// Created by plusminus on 17:45:56 - 25.09.2008
package org.osmdroid.views;

import java.util.List;

import net.wigle.wigleandroid.ZoomButtonsController;
import net.wigle.wigleandroid.ZoomButtonsController.OnZoomListener;

import org.metalev.multitouch.controller.MultiTouchController;
import org.metalev.multitouch.controller.MultiTouchController.MultiTouchObjectCanvas;
import org.metalev.multitouch.controller.MultiTouchController.PointInfo;
import org.metalev.multitouch.controller.MultiTouchController.PositionAndScale;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.IStyledTileSource;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.osmdroid.util.constants.GeoConstants;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.util.constants.MapViewConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.ScaleAnimation;
import android.widget.Scroller;

public class MapView extends ViewGroup implements MapViewConstants,
		MultiTouchObjectCanvas<Object> {

	// ===========================================================
	// Constants
	// ===========================================================

	private static final Logger logger = LoggerFactory.getLogger(MapView.class);

	final static String BUNDLE_TILE_SOURCE = "org.osmdroid.views.MapView.TILE_SOURCE";
	final static String BUNDLE_SCROLL_X = "org.osmdroid.views.MapView.SCROLL_X";
	final static String BUNDLE_SCROLL_Y = "org.osmdroid.views.MapView.SCROLL_Y";
	final static String BUNDLE_ZOOM_LEVEL = "org.osmdroid.views.MapView.ZOOM";

	private static final double ZOOM_SENSITIVITY = 1.3;
	private static final double ZOOM_LOG_BASE_INV = 1.0 / Math.log(2.0 / ZOOM_SENSITIVITY);

	// ===========================================================
	// Fields
	// ===========================================================

	/** Current zoom level for map tiles. */
	private int mZoomLevel = 0;

	private int mTileSizePixels = 0;

	private final OverlayManager mOverlayManager;

	private Projection mProjection;

	private final GestureDetector mGestureDetector;

	/** Handles map scrolling */
	private final Scroller mScroller;

	private final ScaleAnimation mZoomInAnimation;
	private final ScaleAnimation mZoomOutAnimation;
	private final MyAnimationListener mAnimationListener = new MyAnimationListener();

	private final MapController mController;

	// XXX we can use android.widget.ZoomButtonsController if we upgrade the
	// dependency to Android 1.6
	private final ZoomButtonsController mZoomController;
	private boolean mEnableZoomController = false;

	private final ResourceProxy mResourceProxy;

	private MultiTouchController<Object> mMultiTouchController;
	private float mMultiTouchScale = 1.0f;

	protected MapListener mListener;

	// for speed (avoiding allocations)
	private final Matrix mMatrix = new Matrix();
	private final MapTileProviderBase mTileProvider;

	private final Handler mTileRequestCompleteHandler;

	/* a point that will be reused to design added views */
	private final Point mPoint = new Point();

	// ===========================================================
	// Constructors
	// ===========================================================

	private MapView(final Context context, final int tileSizePixels,
			final ResourceProxy resourceProxy, MapTileProviderBase tileProvider,
			final Handler tileRequestCompleteHandler, final AttributeSet attrs) {
		super(context, attrs);
		mResourceProxy = resourceProxy;
		this.mController = new MapController(this);
		this.mScroller = new Scroller(context);
		this.mTileSizePixels = tileSizePixels;
		if (Math.pow(2, getMapTileZoom(this.mTileSizePixels)) != this.mTileSizePixels) {
			throw new IllegalArgumentException("Tile size must be a power of two");
		}

		if (tileProvider == null) {
			final ITileSource tileSource = getTileSourceFromAttributes(attrs);
			tileProvider = new MapTileProviderBasic(context, tileSource);
		}

		mTileRequestCompleteHandler = tileRequestCompleteHandler == null ? new SimpleInvalidationHandler(
				this) : tileRequestCompleteHandler;
		mTileProvider = tileProvider;
		mTileProvider.setTileRequestCompleteHandler(mTileRequestCompleteHandler);

		TilesOverlay mapOverlay = new TilesOverlay(mTileProvider, mResourceProxy);
		mOverlayManager = new OverlayManager(mapOverlay);

		this.mZoomController = new ZoomButtonsController(this);
		this.mZoomController.setOnZoomListener(new MapViewZoomListener());

		mZoomInAnimation = new ScaleAnimation(1, 2, 1, 2, Animation.RELATIVE_TO_SELF, 0.5f,
				Animation.RELATIVE_TO_SELF, 0.5f);
		mZoomOutAnimation = new ScaleAnimation(1, 0.5f, 1, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f,
				Animation.RELATIVE_TO_SELF, 0.5f);
		mZoomInAnimation.setDuration(ANIMATION_DURATION_SHORT);
		mZoomOutAnimation.setDuration(ANIMATION_DURATION_SHORT);
		mZoomInAnimation.setAnimationListener(mAnimationListener);
		mZoomOutAnimation.setAnimationListener(mAnimationListener);

		mGestureDetector = new GestureDetector(context, new MapViewGestureDetectorListener());
		mGestureDetector.setOnDoubleTapListener(new MapViewDoubleClickListener());
	}

	/**
	 * Constructor used by XML layout resource (uses default tile source).
	 */
	public MapView(final Context context, final AttributeSet attrs) {
		this(context, 256, new DefaultResourceProxyImpl(context), null, null, attrs);
	}

	/**
	 * Standard Constructor.
	 */
	public MapView(final Context context, final int tileSizePixels) {
		this(context, tileSizePixels, new DefaultResourceProxyImpl(context));
	}

	public MapView(final Context context, final int tileSizePixels,
			final ResourceProxy resourceProxy) {
		this(context, tileSizePixels, resourceProxy, null);
	}

	public MapView(final Context context, final int tileSizePixels,
			final ResourceProxy resourceProxy, final MapTileProviderBase aTileProvider) {
		this(context, tileSizePixels, resourceProxy, aTileProvider, null);
	}

	public MapView(final Context context, final int tileSizePixels,
			final ResourceProxy resourceProxy, final MapTileProviderBase aTileProvider,
			final Handler tileRequestCompleteHandler) {
		this(context, tileSizePixels, resourceProxy, aTileProvider, tileRequestCompleteHandler,
				null);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================
	public MapController getController() {
		return this.mController;
	}

	/**
	 * You can add/remove/reorder your Overlays using the List of {@link Overlay}. The first (index
	 * 0) Overlay gets drawn first, the one with the highest as the last one.
	 */
	public List<Overlay> getOverlays() {
		return mOverlayManager;
	}

	public OverlayManager getOverlayManager() {
		return mOverlayManager;
	}

	public MapTileProviderBase getTileProvider() {
		return mTileProvider;
	}

	public Scroller getScroller() {
		return mScroller;
	}

	public Handler getTileRequestCompleteHandler() {
		return mTileRequestCompleteHandler;
	}

	public static int getMapTileZoom(final int tileSizePixels) {
		if (tileSizePixels <= 0) {
			return 0;
		}

		int pixels = tileSizePixels;
		int a = 0;
		while (pixels != 0) {
			pixels >>= 1;
			a++;
		}
		return a - 1;
	}

	/**
	 * This class is only meant to be used during on call of onDraw(). Otherwise it may produce
	 * strange results.
	 *
	 * @return
	 */
	public Projection getProjection() {
		if (mProjection == null) {
			mProjection = new Projection();
		}
		return mProjection;
	}

	void setMapCenter(final Point aCenter, final boolean jump) {
		this.setMapCenter(aCenter.x, aCenter.y, jump);
	}

	void setMapCenter(final int x, final int y, final boolean jump) {
		final int worldSize_2 = getWorldSizePx() / 2;
		if (getAnimation() == null || getAnimation().hasEnded()) {
			logger.debug("StartScroll");
			mScroller.startScroll(getScrollX(), getScrollY(),
					x - worldSize_2 - getScrollX(), y - worldSize_2 - getScrollY(),
					jump ? 0 : 500);
			postInvalidate();
		}
	}

	public void setTileSource(final ITileSource aTileSource) {
		if (Math.pow(2, getMapTileZoom(aTileSource.getTileSizePixels())) != aTileSource.getTileSizePixels()) {
			throw new IllegalArgumentException("Tile size must be a power of two");
		}

		mTileProvider.setTileSource(aTileSource);
		mTileSizePixels = aTileSource.getTileSizePixels();
		this.checkZoomButtons();
		this.setZoomLevel(mZoomLevel); // revalidate zoom level
		postInvalidate();
	}

	/**
	 * @param aZoomLevel
	 *            the zoom level bound by the tile source
	 */
	int setZoomLevel(final int aZoomLevel) {
		final int minZoomLevel = getMinZoomLevel();
		final int maxZoomLevel = getMaxZoomLevel();

		final int newZoomLevel = Math.max(minZoomLevel, Math.min(maxZoomLevel, aZoomLevel));
		final int curZoomLevel = this.mZoomLevel;

		this.mZoomLevel = newZoomLevel;
		this.checkZoomButtons();

		if (newZoomLevel > curZoomLevel) {
			scrollTo(getScrollX() << newZoomLevel - curZoomLevel, getScrollY() << newZoomLevel
					- curZoomLevel);
		} else if (newZoomLevel < curZoomLevel) {
			scrollTo(getScrollX() >> curZoomLevel - newZoomLevel, getScrollY() >> curZoomLevel
					- newZoomLevel);
		}

		// snap for all snappables
		final Point snapPoint = new Point();
		// XXX why do we need a new projection here?
		mProjection = new Projection();
		if (mOverlayManager.onSnapToItem(getScrollX(), getScrollY(), snapPoint, this)) {
			scrollTo(snapPoint.x, snapPoint.y);
		}

		// do callback on listener
		if (newZoomLevel != curZoomLevel && mListener != null) {
			final ZoomEvent event = new ZoomEvent(this, newZoomLevel);
			mListener.onZoom(event);
		}
		return this.mZoomLevel;
	}

	/**
	 * Get the current ZoomLevel for the map tiles.
	 *
	 * @return the current ZoomLevel between 0 (equator) and 18/19(closest), depending on the tile
	 *         source chosen.
	 */
	public int getZoomLevel() {
		return getZoomLevel(true);
	}

	/**
	 * Get the current ZoomLevel for the map tiles.
	 *
	 * @param aPending
	 *            if true and we're animating then return the zoom level that we're animating
	 *            towards, otherwise return the current zoom level
	 * @return the zoom level
	 */
	public int getZoomLevel(final boolean aPending) {
		if (aPending && mAnimationListener.animating) {
			return mAnimationListener.targetZoomLevel;
		} else {
			return mZoomLevel;
		}
	}

	/**
	 * Returns the minimum zoom level for the point currently at the center.
	 *
	 * @return The minimum zoom level for the map's current center.
	 */
	public int getMinZoomLevel() {
		return mOverlayManager.getTilesOverlay().getMinimumZoomLevel();
	}

	/**
	 * Returns the maximum zoom level for the point currently at the center.
	 *
	 * @return The maximum zoom level for the map's current center.
	 */
	public int getMaxZoomLevel() {
		return mOverlayManager.getTilesOverlay().getMaximumZoomLevel();
	}

	public boolean canZoomIn() {
		final int maxZoomLevel = getMaxZoomLevel();
		if (mZoomLevel >= maxZoomLevel) {
			return false;
		}
		if (mAnimationListener.animating && mAnimationListener.targetZoomLevel >= maxZoomLevel) {
			return false;
		}
		return true;
	}

	public boolean canZoomOut() {
		final int minZoomLevel = getMinZoomLevel();
		if (mZoomLevel <= minZoomLevel) {
			return false;
		}
		if (mAnimationListener.animating && mAnimationListener.targetZoomLevel <= minZoomLevel) {
			return false;
		}
		return true;
	}

	/**
	 * Zoom in by one zoom level.
	 */
	boolean zoomIn() {

		if (canZoomIn()) {
			if (mAnimationListener.animating) {
				// TODO extend zoom (and return true)
				return false;
			} else {
				mAnimationListener.targetZoomLevel = mZoomLevel + 1;
				mAnimationListener.animating = true;
				startAnimation(mZoomInAnimation);
				return true;
			}
		} else {
			return false;
		}
	}

	boolean zoomInFixing(final Point point) {
		setMapCenter(point, false); // TODO should fix on point, not center on it
		return zoomIn();
	}

	boolean zoomInFixing(final int xPixel, final int yPixel) {
		setMapCenter(xPixel, yPixel, false); // TODO should fix on point, not center on it
		return zoomIn();
	}

	/**
	 * Zoom out by one zoom level.
	 */
	boolean zoomOut() {

		if (canZoomOut()) {
			if (mAnimationListener.animating) {
				// TODO extend zoom (and return true)
				return false;
			} else {
				mAnimationListener.targetZoomLevel = mZoomLevel - 1;
				mAnimationListener.animating = true;
				startAnimation(mZoomOutAnimation);
				return true;
			}
		} else {
			return false;
		}
	}

	boolean zoomOutFixing(final Point point) {
		setMapCenter(point, false); // TODO should fix on point, not center on it
		return zoomOut();
	}

	boolean zoomOutFixing(final int xPixel, final int yPixel) {
		setMapCenter(xPixel, yPixel, false); // TODO should fix on point, not center on it
		return zoomOut();
	}

	public Point getMapCenter() {
		return mProjection.toCurrentZoom(getScrollX(), getScrollY(), null);
	}

	public ResourceProxy getResourceProxy() {
		return mResourceProxy;
	}

	public void onSaveInstanceState(final Bundle state) {
		state.putInt(BUNDLE_SCROLL_X, getScrollX());
		state.putInt(BUNDLE_SCROLL_Y, getScrollY());
		state.putInt(BUNDLE_ZOOM_LEVEL, getZoomLevel());
	}

	public void onRestoreInstanceState(final Bundle state) {

		setZoomLevel(state.getInt(BUNDLE_ZOOM_LEVEL, 1));
		scrollTo(state.getInt(BUNDLE_SCROLL_X, 0), state.getInt(BUNDLE_SCROLL_Y, 0));
	}

	/**
	 * Whether to use the network connection if it's available.
	 */
	public boolean useDataConnection() {
		return mOverlayManager.getTilesOverlay().useDataConnection();
	}

	/**
	 * Set whether to use the network connection if it's available.
	 *
	 * @param aMode
	 *            if true use the network connection if it's available. if false don't use the
	 *            network connection even if it's available.
	 */
	public void setUseDataConnection(final boolean aMode) {
	    mOverlayManager.getTilesOverlay().setUseDataConnection(aMode);
	}

	/**
	 * Check mAnimationListener.animating to determine if view is animating. Useful for overlays to
	 * avoid recalculating during an animation sequence.
	 *
	 * @return boolean indicating whether view is animating.
	 */
	public boolean isAnimating() {
		return mAnimationListener.animating;
	}

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	/**
	 * Returns a set of layout parameters with a width of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}, a height of
	 * {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT} at the {@link Point} (0, 0) align
	 * with {@link LayoutParams#BOTTOM_CENTER}.
	 */
	@Override
	protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, null,
				LayoutParams.BOTTOM_CENTER);
	}

	@Override
	public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}

	// Override to allow type-checking of LayoutParams.
	@Override
	protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams;
	}

	@Override
	protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int count = getChildCount();

		int maxHeight = 0;
		int maxWidth = 0;

		// Find out how big everyone wants to be
		measureChildren(widthMeasureSpec, heightMeasureSpec);

		// Find rightmost and bottom-most child
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			if (child.getVisibility() != GONE) {

				final LayoutParams lp = (LayoutParams) child.getLayoutParams();
				final int childHeight = child.getMeasuredHeight();
				final int childWidth = child.getMeasuredWidth();
				getProjection().toCurrentZoom(lp.geoPoint, mPoint);
				final int x = mPoint.x + getWidth() / 2;
				final int y = mPoint.y + getHeight() / 2;
				int childRight = x;
				int childBottom = y;
				switch (lp.alignment) {
				case LayoutParams.BOTTOM_CENTER:
					childRight = x + childWidth / 2;
					childBottom = y + childHeight;
					break;
				case LayoutParams.BOTTOM_LEFT:
					childRight = x + childWidth;
					childBottom = y + childHeight;
					break;
				case LayoutParams.BOTTOM_RIGHT:
					childRight = x;
					childBottom = y + childHeight;
					break;
				case LayoutParams.TOP_CENTER:
					childRight = x + childWidth / 2;
					childBottom = y;
					break;
				case LayoutParams.TOP_LEFT:
					childRight = x + childWidth;
					childBottom = y;
					break;
				case LayoutParams.TOP_RIGHT:
					childRight = x;
					childBottom = y;
					break;
				}

				maxWidth = Math.max(maxWidth, childRight);
				maxHeight = Math.max(maxHeight, childBottom);
			}
		}

		// Account for padding too
		maxWidth += getPaddingLeft() + getPaddingRight();
		maxHeight += getPaddingTop() + getPaddingBottom();

		// Check against minimum height and width
		maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
		maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

		setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
				resolveSize(maxHeight, heightMeasureSpec));
	}

	@Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int count = getChildCount();

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			if (child.getVisibility() != GONE) {

				final LayoutParams lp = (LayoutParams) child.getLayoutParams();
				final int childHeight = child.getMeasuredHeight();
				final int childWidth = child.getMeasuredWidth();
				getProjection().toCurrentZoom(lp.geoPoint, mPoint);
				final int x = mPoint.x + getWidth() / 2;
				final int y = mPoint.y + getHeight() / 2;
				int childLeft = x;
				int childTop = y;
				switch (lp.alignment) {
				case LayoutParams.BOTTOM_CENTER:
					childLeft = getPaddingLeft() + x - childWidth / 2;
					childTop = getPaddingTop() + y - childHeight;
					break;
				case LayoutParams.BOTTOM_LEFT:
					childLeft = getPaddingLeft() + x;
					childTop = getPaddingTop() + y - childHeight;
					break;
				case LayoutParams.BOTTOM_RIGHT:
					childLeft = getPaddingLeft() + x - childWidth;
					childTop = getPaddingTop() + y - childHeight;
					break;
				case LayoutParams.TOP_CENTER:
					childLeft = getPaddingLeft() + x - childWidth / 2;
					childTop = getPaddingTop() + y;
					break;
				case LayoutParams.TOP_LEFT:
					childLeft = getPaddingLeft() + x;
					childTop = getPaddingTop() + y;
					break;
				case LayoutParams.TOP_RIGHT:
					childLeft = getPaddingLeft() + x - childWidth;
					childTop = getPaddingTop() + y;
					break;
				}
				child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
			}
		}
	}

	public void onDetach() {
		mOverlayManager.onDetach(this);
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		final boolean result = mOverlayManager.onKeyDown(keyCode, event, this);

		return result || super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(final int keyCode, final KeyEvent event) {
		final boolean result = mOverlayManager.onKeyUp(keyCode, event, this);

		return result || super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onTrackballEvent(final MotionEvent event) {

		if (mOverlayManager.onTrackballEvent(event, this)) {
			return true;
		}

		scrollBy((int) (event.getX() * 25), (int) (event.getY() * 25));

		return super.onTrackballEvent(event);
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent event) {

		if (DEBUGMODE) {
			logger.debug("dispatchTouchEvent(" + event + ")");
		}

		if (mOverlayManager.onTouchEvent(event, this)) {
			return true;
		}

		if (mMultiTouchController != null && mMultiTouchController.onTouchEvent(event)) {
			if (DEBUGMODE) {
				logger.debug("mMultiTouchController handled onTouchEvent");
			}
			return true;
		}

		final boolean r = super.dispatchTouchEvent(event);

		if (mGestureDetector.onTouchEvent(event)) {
			if (DEBUGMODE) {
				logger.debug("mGestureDetector handled onTouchEvent");
			}
			return true;
		}

		if (r) {
			if (DEBUGMODE) {
				logger.debug("super handled onTouchEvent");
			}
		} else {
			if (DEBUGMODE) {
				logger.debug("no-one handled onTouchEvent");
			}
		}
		return r;
	}

	@Override
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			if (mScroller.isFinished()) {
				// This will facilitate snapping-to any Snappable points.
				setZoomLevel(mZoomLevel);
			} else {
				scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
			}
			postInvalidate(); // Keep on drawing until the animation has
			// finished.
		}
	}

	@Override
	public void scrollTo(int x, int y) {
		// Adjust the scroll position before it is actually updated.
		// Either wrap or clip the parameters based on the WrapMap flag
		final int worldSize = getWorldSizePx();
		if (mOverlayManager.isWrapMap()) {
			final int renderOffX = Math.abs(getWidth() - worldSize) >> 1;
			final int renderOffY = Math.abs(getHeight() - worldSize) >> 1;

			x = Math.max(-renderOffX, Math.min(x, renderOffX));
			y = Math.max(-renderOffY, Math.min(y, renderOffY));
		} else {
			x = x % worldSize;
			y = y % worldSize;
		}

		super.scrollTo(x, y);

		// do callback on listener
		if (mListener != null) {
			final ScrollEvent event = new ScrollEvent(this, x, y);
			mListener.onScroll(event);
		}
	}

	@Override
	public void setBackgroundColor(final int pColor) {
	    mOverlayManager.getTilesOverlay().setLoadingBackgroundColor(pColor);
		invalidate();
	}

	@Override
	protected void dispatchDraw(final Canvas c) {
		final long startMs = System.currentTimeMillis();

		mProjection = new Projection();

		// Save the current canvas matrix
		c.save();

		if (mMultiTouchScale == 1.0f) {
			c.translate(getWidth() / 2, getHeight() / 2);
		} else {
			c.getMatrix(mMatrix);
			mMatrix.postTranslate(getWidth() / 2, getHeight() / 2);
			mMatrix.preScale(mMultiTouchScale, mMultiTouchScale, getScrollX(), getScrollY());
			c.setMatrix(mMatrix);
		}

		/* Draw background */
		// c.drawColor(mBackgroundColor);

		/* Draw all Overlays. */
		mOverlayManager.onDraw(c, this);

		// Restore the canvas matrix
		c.restore();

		super.dispatchDraw(c);

		final long endMs = System.currentTimeMillis();
		if (DEBUGMODE) {
			logger.debug("Rendering overall: " + (endMs - startMs) + "ms");
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		this.mZoomController.setVisible(false);
		this.onDetach();
		super.onDetachedFromWindow();
	}

	// ===========================================================
	// Implementation of MultiTouchObjectCanvas
	// ===========================================================

	@Override
	public Object getDraggableObjectAtPoint(final PointInfo pt) {
		return this;
	}

	@Override
	public void getPositionAndScale(final Object obj, final PositionAndScale objPosAndScaleOut) {
		objPosAndScaleOut.set(0, 0, true, mMultiTouchScale, false, 0, 0, false, 0);
	}

	@Override
	public void selectObject(final Object obj, final PointInfo pt) {
		// if obj is null it means we released the pointers
		// if scale is not 1 it means we pinched
		if (obj == null && mMultiTouchScale != 1.0f) {
			final float scaleDiffFloat = (float) (Math.log(mMultiTouchScale) * ZOOM_LOG_BASE_INV);
			final int scaleDiffInt = Math.round(scaleDiffFloat);
			setZoomLevel(mZoomLevel + scaleDiffInt);
			// XXX maybe zoom in/out instead of zooming direct to zoom level
			// - probably not a good idea because you'll repeat the animation
		}

		// reset scale
		mMultiTouchScale = 1.0f;
	}

	@Override
	public boolean setPositionAndScale(final Object obj, final PositionAndScale aNewObjPosAndScale,
			final PointInfo aTouchPoint) {
		float multiTouchScale = aNewObjPosAndScale.getScale();
		// If we are at the first or last zoom level, prevent pinching/expanding
		if ((multiTouchScale > 1) && !canZoomIn()) {
			multiTouchScale = 1;
		}
		if ((multiTouchScale < 1) && !canZoomOut()) {
			multiTouchScale = 1;
		}
		mMultiTouchScale = multiTouchScale;
		invalidate(); // redraw
		return true;
	}

	/*
	 * Set the MapListener for this view
	 */
	public void setMapListener(final MapListener ml) {
		mListener = ml;
	}

	// ===========================================================
	// Package Methods
	// ===========================================================

	/**
	 * Get the world size in pixels.
	 */
	int getWorldSizePx() {
		return 1 << getPixelZoomLevel();
	}

	/**
	 * Get the equivalent zoom level on pixel scale
	 */
	int getPixelZoomLevel() {
		return this.mZoomLevel + getMapTileZoom(mTileSizePixels);
	}

	// ===========================================================
	// Methods
	// ===========================================================

	private void checkZoomButtons() {
		this.mZoomController.setZoomInEnabled(canZoomIn());
		this.mZoomController.setZoomOutEnabled(canZoomOut());
	}

	public void setBuiltInZoomControls(final boolean on) {
		this.mEnableZoomController = on;
		this.checkZoomButtons();
	}

	public void setMultiTouchControls(final boolean on) {
		mMultiTouchController = on ? new MultiTouchController<Object>(this, false) : null;
	}

	private ITileSource getTileSourceFromAttributes(final AttributeSet aAttributeSet) {

		ITileSource tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE;

		if (aAttributeSet != null) {
			final String tileSourceAttr = aAttributeSet.getAttributeValue(null, "tilesource");
			if (tileSourceAttr != null) {
				try {
					final ITileSource r = TileSourceFactory.getTileSource(tileSourceAttr);
					logger.info("Using tile source specified in layout attributes: " + r);
					tileSource = r;
				} catch (final IllegalArgumentException e) {
					logger.warn("Invalid tile souce specified in layout attributes: " + tileSource);
				}
			}
		}

		if (aAttributeSet != null && tileSource instanceof IStyledTileSource) {
			String style = aAttributeSet.getAttributeValue(null, "style");
			if (style == null) {
				// historic - old attribute name
				style = aAttributeSet.getAttributeValue(null, "cloudmadeStyle");
			}
			if (style == null) {
				logger.info("Using default style: 1");
			} else {
				logger.info("Using style specified in layout attributes: " + style);
				((IStyledTileSource<?>) tileSource).setStyle(style);
			}
		}

		logger.info("Using tile source: " + tileSource);
		return tileSource;
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	/**
	 * This class may return valid results until the underlying {@link MapView} gets modified in any
	 * way (i.e. new center).
	 *
	 * @author Nicolas Gramlich
	 * @author Manuel Stahl
	 */
	public class Projection implements GeoConstants {
		private final int mZoomLevelProjection;
		private final int mTileSizePixelsProjection;
		private final int mTileMapZoomProjection;

		private Projection() {

			/*
			 * Do some calculations and drag attributes to local variables to save some performance.
			 */
			mZoomLevelProjection = mZoomLevel;
			// TODO Draw to attributes and so make it only 'valid' for a short time.
			mTileSizePixelsProjection = mTileSizePixels;
			mTileMapZoomProjection = getMapTileZoom(getTileSizePixels());
		}

		public int getTileSizePixels() {
			return mTileSizePixelsProjection;
		}

		public int getTileMapZoom() {
			return mTileMapZoomProjection;
		}

		public int getZoomLevel() {
			return mZoomLevelProjection;
		}

		public Point toCurrentZoom(final Point worldCoords, final Point reuse) {
			return toCurrentZoom(worldCoords.x, worldCoords.y, reuse);
		}
		public Point toCurrentZoom(final int worldX, final int worldY, final Point reuse) {
			final Point out = reuse != null ? reuse : new Point();

			out.set(worldX >> mZoomLevel, worldY >> mZoomLevel);
			return out;
		}

		public Rect fromCurrentZoom(final Rect curCoords, final Rect reuse) {
			final Rect out = reuse != null ? reuse : new Rect();

			out.set(
					curCoords.left << mZoomLevel, curCoords.top << mZoomLevel,
					curCoords.right << mZoomLevel, curCoords.bottom << mZoomLevel);
			return out;
		}
		public Point fromCurrentZoom(final Point curCoords, final Point reuse) {
			return fromCurrentZoom(curCoords.x, curCoords.y, reuse);
		}
		public Point fromCurrentZoom(final int curX, final int curY, final Point reuse) {
			final Point out = reuse != null ? reuse : new Point();

			out.set(curX << mZoomLevel, curY << mZoomLevel);
			return out;
		}
	}

	private class MapViewGestureDetectorListener implements OnGestureListener {

		@Override
		public boolean onDown(final MotionEvent e) {
			if (MapView.this.mOverlayManager.onDown(e, MapView.this)) {
				return true;
			}

			mZoomController.setVisible(mEnableZoomController);
			return true;
		}

		@Override
		public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
				final float velocityY) {
			if (MapView.this.mOverlayManager.onFling(e1, e2, velocityX, velocityY, MapView.this)) {
				return true;
			}

			final int worldSize = getWorldSizePx();
			mScroller.fling(getScrollX(), getScrollY(), (int) -velocityX, (int) -velocityY,
					-worldSize, worldSize, -worldSize, worldSize);
			return true;
		}

		@Override
		public void onLongPress(final MotionEvent e) {
			MapView.this.mOverlayManager.onLongPress(e, MapView.this);
		}

		@Override
		public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
				final float distanceY) {
			if (MapView.this.mOverlayManager.onScroll(e1, e2, distanceX, distanceY, MapView.this)) {
				return true;
			}

			scrollBy((int) distanceX, (int) distanceY);
			return true;
		}

		@Override
		public void onShowPress(final MotionEvent e) {
			MapView.this.mOverlayManager.onShowPress(e, MapView.this);
		}

		@Override
		public boolean onSingleTapUp(final MotionEvent e) {
			if (MapView.this.mOverlayManager.onSingleTapUp(e, MapView.this)) {
				return true;
			}

			return false;
		}

	}

	private class MapViewDoubleClickListener implements GestureDetector.OnDoubleTapListener {
		@Override
		public boolean onDoubleTap(final MotionEvent e) {
			if (mOverlayManager.onDoubleTap(e, MapView.this)) {
				return true;
			}

			final Point center = getProjection().fromCurrentZoom((int)e.getX(), (int)e.getY(), null);
			return zoomInFixing(center.x, center.y);
		}

		@Override
		public boolean onDoubleTapEvent(final MotionEvent e) {
			if (mOverlayManager.onDoubleTapEvent(e, MapView.this)) {
				return true;
			}

			return false;
		}

		@Override
		public boolean onSingleTapConfirmed(final MotionEvent e) {
			if (mOverlayManager.onSingleTapConfirmed(e, MapView.this)) {
				return true;
			}

			return false;
		}
	}

	private class MapViewZoomListener implements OnZoomListener {
		@Override
		public void onZoom(final boolean zoomIn) {
			if (zoomIn) {
				getController().zoomIn();
			} else {
				getController().zoomOut();
			}
		}

		@Override
		public void onVisibilityChanged(final boolean visible) {
		}
	}

	private class MyAnimationListener implements AnimationListener {
		private int targetZoomLevel;
		private boolean animating;

		@Override
		public void onAnimationEnd(final Animation aAnimation) {
			animating = false;
			MapView.this.post(new Runnable() {
				@Override
				public void run() {
					// This is necessary because (as of API 1.5) when onAnimationEnd is dispatched
					// there still is some residual scaling going on and this will cause a frame of
					// the new zoom level while the canvas is still being scaled as part of the
					// animation and we don't want that.
					clearAnimation();
					setZoomLevel(targetZoomLevel);
				}
			});
		}

		@Override
		public void onAnimationRepeat(final Animation aAnimation) {
		}

		@Override
		public void onAnimationStart(final Animation aAnimation) {
			animating = true;
		}

	}

	// ===========================================================
	// Public Classes
	// ===========================================================

	/**
	 * Per-child layout information associated with OpenStreetMapView.
	 */
	public static class LayoutParams extends ViewGroup.LayoutParams {

		/**
		 * Special value for the alignment requested by a View. BOTTOM_CENTER means that the
		 * location will be centered at the bottom of the view.
		 */
		public static final int BOTTOM_CENTER = 1;
		/**
		 * Special value for the alignment requested by a View. BOTTOM_LEFT means that the location
		 * will be at the bottom left of the View.
		 */
		public static final int BOTTOM_LEFT = 2;
		/**
		 * Special value for the alignment requested by a View. BOTTOM_RIGHT means that the location
		 * will be at the bottom right of the View.
		 */
		public static final int BOTTOM_RIGHT = 3;
		/**
		 * Special value for the alignment requested by a View. TOP_RIGHT means that the location
		 * will be centered at the top of the View.
		 */
		public static final int TOP_CENTER = 4;
		/**
		 * Special value for the alignment requested by a View. TOP_LEFT means that the location
		 * will at the top left the View.
		 */
		public static final int TOP_LEFT = 5;
		/**
		 * Special value for the alignment requested by a View. TOP_RIGHT means that the location
		 * will at the top right the View.
		 */
		public static final int TOP_RIGHT = 6;
		/**
		 * The location of the child within the map view.
		 */
		public Point geoPoint;

		/**
		 * The alignment the alignment of the view compared to the location.
		 */
		public int alignment;

		/**
		 * Creates a new set of layout parameters with the specified width, height and location.
		 *
		 * @param width
		 *            the width, either {@link #FILL_PARENT}, {@link #WRAP_CONTENT} or a fixed size
		 *            in pixels
		 * @param height
		 *            the height, either {@link #FILL_PARENT}, {@link #WRAP_CONTENT} or a fixed size
		 *            in pixels
		 * @param geoPoint
		 *            the location of the child within the map view
		 * @param alignment
		 *            the alignment of the view compared to the location {@link #BOTTOM_CENTER},
		 *            {@link #BOTTOM_LEFT}, {@link #BOTTOM_RIGHT} {@link #TOP_CENTER},
		 *            {@link #TOP_LEFT}, {@link #TOP_RIGHT}
		 */
		public LayoutParams(int width, int height, Point geoPoint, int alignment) {
			super(width, height);
			if (geoPoint != null)
				this.geoPoint = geoPoint;
			else
				this.geoPoint = new Point(0, 0);
			this.alignment = alignment;
		}

		/**
		 * Since we cannot use XML files in this project this constructor is useless. Creates a new
		 * set of layout parameters. The values are extracted from the supplied attributes set and
		 * context.
		 *
		 * @param c
		 *            the application environment
		 * @param attrs
		 *            the set of attributes fom which to extract the layout parameters values
		 */
		public LayoutParams(Context c, AttributeSet attrs) {
			super(c, attrs);
			this.geoPoint = new Point(0, 0);
			this.alignment = BOTTOM_CENTER;
		}

		/**
		 * {@inheritDoc}
		 */
		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}
	}

}
