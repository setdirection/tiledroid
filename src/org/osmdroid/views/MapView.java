// Created by plusminus on 17:45:56 - 25.09.2008
package org.osmdroid.views;

import java.util.List;

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
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler;
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
import android.widget.ZoomButtonsController;
import android.widget.ZoomButtonsController.OnZoomListener;

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
	private final OverlayManager mOverlayManager;

	private Projection mProjection;

	private final GestureDetector mGestureDetector;

	/** Handles map scrolling */
	private final Scroller mScroller;
	private boolean mFlinging = false;

	private final ScaleAnimation mZoomInAnimation;
	private final ScaleAnimation mZoomOutAnimation;
	private final MyAnimationListener mAnimationListener = new MyAnimationListener();

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
	private final ZoomCoord mPoint = new ZoomCoord();

	private final WorldCoord mCenter = new WorldCoord();

	// ===========================================================
	// Constructors
	// ===========================================================

	private MapView(final Context context,
			final ResourceProxy resourceProxy, MapTileProviderBase tileProvider,
			final Handler tileRequestCompleteHandler, final AttributeSet attrs) {
		super(context, attrs);
		mResourceProxy = resourceProxy;
		this.mScroller = new Scroller(context);

		if (tileProvider == null) {
			final ITileSource tileSource = TileSourceFactory.DEFAULT_TILE_SOURCE;
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

		setZoomLevel(0);
	}

	/**
	 * Constructor used by XML layout resource (uses default tile source).
	 */
	public MapView(final Context context, final AttributeSet attrs) {
		this(context, new DefaultResourceProxyImpl(context), null, null, attrs);
	}

	/**
	 * Standard Constructor.
	 */
	public MapView(final Context context) {
		this(context, new DefaultResourceProxyImpl(context));
	}

	public MapView(final Context context,
			final ResourceProxy resourceProxy) {
		this(context, resourceProxy, null);
	}

	public MapView(final Context context,
			final ResourceProxy resourceProxy, final MapTileProviderBase aTileProvider) {
		this(context, resourceProxy, aTileProvider, null);
	}

	public MapView(final Context context,
			final ResourceProxy resourceProxy, final MapTileProviderBase aTileProvider,
			final Handler tileRequestCompleteHandler) {
		this(context, resourceProxy, aTileProvider, tileRequestCompleteHandler,
				null);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

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

	/**
	 * This class is only meant to be used during on call of onDraw(). Otherwise it may produce
	 * strange results.
	 *
	 * @return
	 */
	public Projection getProjection() {
		return mProjection;
	}

	public void setMapCenter(final WorldCoord worldCenter, final boolean jump) {
		if (getAnimation() == null || getAnimation().hasEnded()) {
			mCenter.set(worldCenter.x, worldCenter.y);

			mFlinging = false;

			logger.debug("StartScroll");
			final ViewportCoord viewCord = mProjection.toViewport(worldCenter, null);
			if (jump) {
				if (!mScroller.isFinished()) {
					mScroller.forceFinished(true);
				}

				scrollTo(viewCord.x, viewCord.y);
			} else {
				mScroller.startScroll(getScrollX(), getScrollY(),
						viewCord.x - getScrollX(), viewCord.y - getScrollY(),
						500);
			}
			postInvalidate();
		}
	}

	public void setTileSource(final ITileSource aTileSource) {
		mTileProvider.setTileSource(aTileSource);
		this.checkZoomButtons();
		this.setZoomLevel(getZoomLevel(false)); // revalidate zoom level
		postInvalidate();
	}

	/**
	 * @param aZoomLevel
	 *            the zoom level bound by the tile source
	 */
	private void setZoomLevel(final int aZoomLevel) {
		final int minZoomLevel = getMinZoomLevel();
		final int maxZoomLevel = getMaxZoomLevel();

		final int newZoomLevel = Math.max(minZoomLevel, Math.min(maxZoomLevel, aZoomLevel));
		final int curZoomLevel = this.getZoomLevel(false);

		final WorldCoord center = getMapCenter();

		// snap for all snappables
		if (curZoomLevel != newZoomLevel) {
			// XXX why do we need a new projection here?
			mProjection = new Projection(newZoomLevel);

			// Update the center location for the new zoom
			if (curZoomLevel >= 0) {
				ViewportCoord viewCenter = mProjection.toViewport(center, null);
				scrollTo(viewCenter.x, viewCenter.y);
			}
		}

		this.checkZoomButtons();

		final WorldCoord snapPoint = new WorldCoord();
		if (mOverlayManager.onSnapToItem(getScrollX(), getScrollY(), snapPoint, this)) {
			scrollTo(snapPoint.x, snapPoint.y);
		}

		// do callback on listener
		if (newZoomLevel != curZoomLevel && mListener != null) {
			final ZoomEvent event = new ZoomEvent(this, newZoomLevel);
			mListener.onZoom(event);
		}
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
			return mProjection != null ? mProjection.getZoomLevel() : -1;
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
		if (getZoomLevel() >= maxZoomLevel) {
			return false;
		}
		if (mAnimationListener.animating && mAnimationListener.targetZoomLevel >= maxZoomLevel) {
			return false;
		}
		return true;
	}

	public boolean canZoomOut() {
		final int minZoomLevel = getMinZoomLevel();
		if (getZoomLevel() <= minZoomLevel) {
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
	boolean zoomIn(int count) {

		if (canZoomIn()) {
			if (mAnimationListener.animating) {
				// TODO extend zoom (and return true)
				return false;
			} else {
				mAnimationListener.targetZoomLevel = getZoomLevel() + count;
				startAnimation(mZoomInAnimation);
				return true;
			}
		} else {
			return false;
		}
	}

	public boolean zoomInFixing(final WorldCoord point) {
		setMapCenter(point, false); // TODO should fix on point, not center on it
		return zoomIn(1);
	}

	public boolean zoomFixing(final WorldCoord point, final int count) {
		setMapCenter(point, false); // TODO should fix on point, not center on it
		if (count < 0) {
			return zoomOut(-1*count);
		} else {
			return zoomIn(count);
		}
	}

	/**
	 * Zoom out by one zoom level.
	 */
	boolean zoomOut(int count) {

		if (canZoomOut()) {
			if (mAnimationListener.animating) {
				// TODO extend zoom (and return true)
				return false;
			} else {
				mAnimationListener.targetZoomLevel = getZoomLevel() - count;
				startAnimation(mZoomOutAnimation);
				return true;
			}
		} else {
			return false;
		}
	}

	boolean zoomOutFixing(final WorldCoord point) {
		setMapCenter(point, false); // TODO should fix on point, not center on it
		return zoomOut(1);
	}

	@Override
	public void startAnimation(Animation animation) {
		mAnimationListener.animating = true;
		mFlinging = false;

		super.startAnimation(animation);
	}

	public WorldCoord getMapCenter() {
		return mCenter;
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
				setZoomLevel(getZoomLevel(false));
				mFlinging = false;
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
		if (mOverlayManager.isWrapMap()) {
			final int renderOffX = mProjection.getZoomSizeX_2();
			final int renderOffY = mProjection.getZoomSizeY_2();

			x = Math.max(-renderOffX, Math.min(x, renderOffX));
			y = Math.max(-renderOffY, Math.min(y, renderOffY));
		} else {
			x = x % (mProjection.getZoomSizeX_2() << 1);
			y = y % (mProjection.getZoomSizeY_2() << 1);
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

		c.translate(-mProjection.getZoomSizeX_2(), -mProjection.getZoomSizeY_2());

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
			setZoomLevel(getZoomLevel() + scaleDiffInt);
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

		float scaleDelta = multiTouchScale/mMultiTouchScale;
		mMultiTouchScale = multiTouchScale;

		final float focusOffsetX  = ((float)getWidth() / 2 - aTouchPoint.getX())/scaleDelta;
		final float focusOffsetY = ((float)getHeight() / 2 - aTouchPoint.getY())/scaleDelta;

		ViewportCoord viewCoord = new ViewportCoord((int)(aTouchPoint.getX() + focusOffsetX), (int)(aTouchPoint.getY() + focusOffsetY));
		setMapCenter(getProjection().fromViewport(viewCoord, mCenter), true);

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

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	/**
	 * This class may return valid results until the zoom level changes
	 *
	 * @author Nicolas Gramlich
	 * @author Manuel Stahl
	 */
	public class Projection {
		private final int mZoomLevel;
		private final int mTileXCount;
		private final int mTileYCount;
		private final int mZoomSizeX_2;
		private final int mZoomSizeY_2;
		private final int mZoomDelta;

		private Projection(int zoomLevel) {
			mZoomLevel = zoomLevel;
			mTileXCount = mOverlayManager.getTilesOverlay().getTileXCount(mZoomLevel);
			mTileYCount = mOverlayManager.getTilesOverlay().getTileYCount(mZoomLevel);
			mZoomSizeX_2 = mOverlayManager.getTilesOverlay().getZoomWidth(mZoomLevel) >> 1;
			mZoomSizeY_2 = mOverlayManager.getTilesOverlay().getZoomHeight(mZoomLevel) >> 1;
			mZoomDelta = getMaxZoomLevel() - mZoomLevel;
		}

		public int getZoomLevel() {
			return mZoomLevel;
		}

		public int getTileXCount() {
			return mTileXCount;
		}
		public int getTileYCount() {
			return mTileYCount;
		}

		public int getZoomSizeX_2() {
			return mZoomSizeX_2;
		}
		public int getZoomSizeY_2() {
			return mZoomSizeY_2;
		}

		public ZoomCoord toCurrentZoom(final WorldCoord worldCoords, final ZoomCoord reuse) {
			final ZoomCoord out = reuse != null ? reuse : new ZoomCoord();

			out.set((worldCoords.x >> mZoomDelta), (worldCoords.y >> mZoomDelta));
			return out;
		}

		public Rect fromCurrentZoom(final Rect curCoords, final Rect reuse) {
			final Rect out = reuse != null ? reuse : new Rect();

			out.set(
					(curCoords.left << mZoomDelta), (curCoords.top << mZoomDelta),
					(curCoords.right << mZoomDelta), (curCoords.bottom << mZoomDelta));
			return out;
		}
		public WorldCoord fromCurrentZoom(final ZoomCoord curCoords, final WorldCoord reuse) {
			return fromCurrentZoom(curCoords.x, curCoords.y, reuse);
		}
		private WorldCoord fromCurrentZoom(final int curX, final int curY, final WorldCoord reuse) {
			final WorldCoord out = reuse != null ? reuse : new WorldCoord();

			out.set(curX << mZoomDelta, curY << mZoomDelta);
			return out;
		}

		public ViewportCoord toViewport(final WorldCoord worldCoord, final ViewportCoord reuse) {
			final ViewportCoord out = reuse != null ? reuse : new ViewportCoord();
			final ZoomCoord zoomCoord = toCurrentZoom(worldCoord, null);

			// In inverse of the -getX()/2 is done when rendering rather than here. Makes it fun that way.
			out.set(
					zoomCoord.x - mZoomSizeX_2,
					zoomCoord.y - mZoomSizeY_2);
			return out;
		}
		public WorldCoord fromViewport(final ViewportCoord viewportCord, final WorldCoord reuse) {
			return getProjection().fromCurrentZoom(
					viewportCord.x + getScrollX() + mZoomSizeX_2 - getWidth()/2,
					viewportCord.y + getScrollY() + mZoomSizeY_2 - getHeight()/2,
					reuse);
		}
	}

	private class MapViewGestureDetectorListener implements OnGestureListener {

		@Override
		public boolean onDown(final MotionEvent e) {
			if (mFlinging && !mScroller.isFinished()) {
				mScroller.forceFinished(true);
				mFlinging = false;
			}

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

			final int zoomSizeX = mProjection.getZoomSizeX_2();
			final int zoomSizeY = mProjection.getZoomSizeY_2();
			final double scaledVelocityX = -0.6*velocityX;
			final double scaledVelocityY = -0.6*velocityY;
			mScroller.fling(getScrollX(), getScrollY(), (int) scaledVelocityX, (int) scaledVelocityY,
					-zoomSizeX, zoomSizeX, -zoomSizeY, zoomSizeY);
			mFlinging = true;

			// Ensure that we are actually running the scroll animation
			postInvalidate();

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

			final WorldCoord center = getProjection().fromViewport(new ViewportCoord((int)e.getX(), (int)e.getY()), null);
			return zoomInFixing(center);
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
				zoomIn(1);
			} else {
				zoomOut(1);
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
		public WorldCoord geoPoint;

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
		public LayoutParams(int width, int height, WorldCoord geoPoint, int alignment) {
			super(width, height);
			if (geoPoint != null)
				this.geoPoint = geoPoint;
			else
				this.geoPoint = new WorldCoord(0, 0);
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
			this.geoPoint = new WorldCoord(0, 0);
			this.alignment = BOTTOM_CENTER;
		}

		/**
		 * {@inheritDoc}
		 */
		public LayoutParams(ViewGroup.LayoutParams source) {
			super(source);
		}
	}

	public static class WorldCoord extends Point {
		public WorldCoord() { super(); }
		public WorldCoord(int x, int y) { super(x, y); }
	}
	public static class ZoomCoord extends Point {
		public ZoomCoord() { super(); }
		public ZoomCoord(int x, int y) { super(x, y); }
	}
	public static class ViewportCoord extends Point {
		public ViewportCoord() { super(); }
		public ViewportCoord(int x, int y) { super(x, y); }
	}
}
