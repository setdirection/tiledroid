package org.osmdroid.views.overlay;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.util.MyMath;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * These objects are the principle consumer of map tiles.
 *
 * see {@link MapTile} for an overview of how tiles are acquired by this overlay.
 *
 */

public class TilesOverlay extends Overlay {

	private static final Logger logger = LoggerFactory.getLogger(TilesOverlay.class);

	/** Current tile source */
	protected final MapTileProviderBase mTileProvider;

	/* to avoid allocations during draw */
	protected final Paint mPaint = new Paint();
	private final Rect mTileRect = new Rect();
	private final Rect mViewPort = new Rect();

	private boolean mWrapMap = false;

	/** A drawable loading tile **/
	private BitmapDrawable mLoadingTile = null;
	private int mLoadingBackgroundColor = Color.rgb(216, 208, 208);
	private int mLoadingLineColor = Color.rgb(200, 192, 192);

	public TilesOverlay(final MapTileProviderBase aTileProvider, final Context aContext) {
		this(aTileProvider, new DefaultResourceProxyImpl(aContext));
	}

	public TilesOverlay(final MapTileProviderBase aTileProvider, final ResourceProxy pResourceProxy) {
		super(pResourceProxy);
		if (aTileProvider == null) {
			throw new IllegalArgumentException(
					"You must pass a valid tile provider to the tiles overlay.");
		}
		this.mTileProvider = aTileProvider;
	}

	@Override
	public void onDetach(final MapView pMapView) {
		this.mTileProvider.detach();
	}

	/*
	 * World definition. Defaults to a square with no clipping on the trailing edge tiles.
	 */
	public int getTileSizePixels() {
		return mTileProvider.getTileSource().getTileSizePixels();
	}
	public int getWorldWidth() {
		return mTileProvider.getWorldWidth();
	}
	public int getWorldHeight() {
		return mTileProvider.getWorldHeight();
	}
	public int getZoomWidth(int zoomLevel) {
		return mTileProvider.getZoomWidth(zoomLevel);
	}
	public int getZoomHeight(int zoomLevel) {
		return mTileProvider.getZoomHeight(zoomLevel);
	}
	public int getTileXCount(int zoomLevel) {
		return mTileProvider.getTileXCount(zoomLevel);
	}
	public int getTileYCount(int zoomLevel) {
		return mTileProvider.getTileYCount(zoomLevel);
	}

	public int getMinimumZoomLevel() {
		return mTileProvider.getMinimumZoomLevel();
	}

	public int getMaximumZoomLevel() {
		return mTileProvider.getMaximumZoomLevel();
	}

	/**
	 * Whether to use the network connection if it's available.
	 */
	public boolean useDataConnection() {
		return mTileProvider.useDataConnection();
	}

	/**
	 * Set whether to use the network connection if it's available.
	 *
	 * @param aMode
	 *            if true use the network connection if it's available. if false don't use the
	 *            network connection even if it's available.
	 */
	public void setUseDataConnection(final boolean aMode) {
		mTileProvider.setUseDataConnection(aMode);
	}

	@Override
	protected void draw(final Canvas c, final MapView osmv, final boolean shadow) {
		if (shadow) {
			return;
		}

		// Get the area we are drawing to
		c.getClipBounds(mViewPort);

		// Draw the tiles!
		drawTiles(c, osmv.getProjection(), mViewPort);
	}

	/**
	 * This is meant to be a "pure" tile drawing function that doesn't take into account
	 * platform-specific characteristics (like Android's canvas's having 0,0 as the center rather
	 * than the upper-left corner).
	 */
	public void drawTiles(final Canvas c, final Projection pj, final Rect viewPort) {
		final int tileSizePx = getTileSizePixels();
		final int zoomLevel = pj.getZoomLevel();

		/*
		 * Calculate the amount of tiles needed for each side around the center one.
		 */
		final int tileNeededToLeftOfCenter = (viewPort.left / tileSizePx) - 1;
		final int tileNeededToRightOfCenter = viewPort.right / tileSizePx;
		final int tileNeededToTopOfCenter = (viewPort.top / tileSizePx) - 1;
		final int tileNeededToBottomOfCenter = viewPort.bottom / tileSizePx;

		final int mapTileUpperBoundX = pj.getTileXCount();
		final int mapTileUpperBoundY = pj.getTileYCount();

		// make sure the cache is big enough for all the tiles
		final int numNeeded = (tileNeededToBottomOfCenter - tileNeededToTopOfCenter + 1)
				* (tileNeededToRightOfCenter - tileNeededToLeftOfCenter + 1);
		mTileProvider.ensureCapacity(numNeeded);

		/* Draw all the MapTiles (from the upper left to the lower right). */
		for (int y = tileNeededToTopOfCenter; y <= tileNeededToBottomOfCenter; y++) {
			for (int x = tileNeededToLeftOfCenter; x <= tileNeededToRightOfCenter; x++) {
				// Construct a MapTile to request from the tile provider.
				final int tileY = mWrapMap ? y : MyMath.mod(y, mapTileUpperBoundY);
				final int tileX = mWrapMap ? x : MyMath.mod(x, mapTileUpperBoundX);
				final MapTile tile = new MapTile(zoomLevel, tileX, tileY);

				Drawable currentMapTile = null;

				mTileRect.set(x * tileSizePx, y * tileSizePx, x * tileSizePx + tileSizePx, y * tileSizePx + tileSizePx);

				// Render the background under this, allowing transparent sections to have a background
				// TODO : See if we can optimize this so we aren't double rendering the same data
				currentMapTile = getLoadingTile();
				if (currentMapTile != null) {
					onTileReadyToDraw(c, currentMapTile, mTileRect);
				}

				if (0 <= x && x < mapTileUpperBoundX
						&& 0 <= y && y < mapTileUpperBoundY) {
					currentMapTile = mTileProvider.getMapTile(tile);
					if (currentMapTile != null) {
						onTileReadyToDraw(c, currentMapTile, mTileRect);
					}
				}

				if (DEBUGMODE) {
					c.drawText(tile + " " + mTileRect + " x: " + x + " y: " + y, mTileRect.left + 1,
							mTileRect.top + mPaint.getTextSize(), mPaint);
					c.drawLine(mTileRect.left, mTileRect.top, mTileRect.right, mTileRect.top,
							mPaint);
					c.drawLine(mTileRect.left, mTileRect.top, mTileRect.left, mTileRect.bottom,
							mPaint);
				}
			}
		}

		// draw a cross at center in debug mode
		if (DEBUGMODE) {
			final Point centerPoint = new Point(viewPort.centerX(), viewPort.centerY());
			c.drawLine(centerPoint.x, centerPoint.y - 9, centerPoint.x, centerPoint.y + 9, mPaint);
			c.drawLine(centerPoint.x - 9, centerPoint.y, centerPoint.x + 9, centerPoint.y, mPaint);
		}

	}

	protected void onTileReadyToDraw(final Canvas c, final Drawable currentMapTile,
			final Rect tileRect) {
		currentMapTile.setBounds(tileRect);
		currentMapTile.draw(c);
	}

	public void setWrapMap(final boolean pWrapMap) {
		this.mWrapMap = pWrapMap;
	}

	public boolean isWrapMap() {
		return this.mWrapMap;
	}

	public int getLoadingBackgroundColor() {
		return mLoadingBackgroundColor;
	}

	/**
	 * Set the color to use to draw the background while we're waiting for the tile
	 * to load.
	 * @param pLoadingBackgroundColor the color to use.
	 * If the value is {@link Color.TRANSPARENT} then there will be no loading tile.
	 */
	public void setLoadingBackgroundColor(final int pLoadingBackgroundColor) {
		if (mLoadingBackgroundColor != pLoadingBackgroundColor) {
			mLoadingBackgroundColor = pLoadingBackgroundColor;
			clearLoadingTile();
		}
	}

	public int getLoadingLineColor() {
		return mLoadingLineColor;
	}

	public void setLoadingLineColor(final int pLoadingLineColor) {
		if (mLoadingLineColor != pLoadingLineColor) {
			mLoadingLineColor = pLoadingLineColor;
			clearLoadingTile();
		}
	}

	private Drawable getLoadingTile() {
		if (mLoadingTile == null && mLoadingBackgroundColor != Color.TRANSPARENT) {
			try {
				final int tileSize = mTileProvider.getTileSource() != null ?
						mTileProvider.getTileSource().getTileSizePixels() : 256;
				final Bitmap bitmap = Bitmap.createBitmap(
						tileSize, tileSize, Bitmap.Config.ARGB_8888);
				final Canvas canvas = new Canvas(bitmap);
				final Paint paint = new Paint();
				canvas.drawColor(mLoadingBackgroundColor);
				paint.setColor(mLoadingLineColor);
				paint.setStrokeWidth(0);
				final int lineSize = tileSize / 16;
				for (int a = 0; a < tileSize; a += lineSize) {
					canvas.drawLine(0, a, tileSize, a, paint);
					canvas.drawLine(a, 0, a, tileSize, paint);
				}
				mLoadingTile = new BitmapDrawable(bitmap);
			} catch (final OutOfMemoryError e) {
				logger.error("OutOfMemoryError getting loading tile");
				System.gc();
			}
		}
		return mLoadingTile;
	}

	private void clearLoadingTile() {
		final BitmapDrawable bitmapDrawable = mLoadingTile;
		mLoadingTile = null;
		if (bitmapDrawable != null) {
			bitmapDrawable.getBitmap().recycle();
		}
	}
}
