package org.osmdroid.tileprovider.modules;

import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileCache;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class MapTileInterpolator extends Drawable {
	private final MapTile      tile;
	private final Drawable     above;
	private final Drawable[]   below;

	/**
	 * Creates a map tile interpolator object if we currently have the proper cached data
	 * to construct one.
	 */
	public static MapTileInterpolator create(MapTile mapTile, MapTileCache cache) {
		// Attempt to pull data from the level below us first as this has more
		// data for us to play with
		int belowZoom = mapTile.getZoomLevel()+1;
		int belowX = mapTile.getX()<<1;
		int belowY = mapTile.getY()<<1;
		MapTile belowNW = new MapTile(belowZoom, belowX,   belowY);
		MapTile belowNE = new MapTile(belowZoom, belowX+1, belowY);
		MapTile belowSW = new MapTile(belowZoom, belowX,   belowY+1);
		MapTile belowSE = new MapTile(belowZoom, belowX+1, belowY+1);
		if (cache.containsTile(belowNW) && cache.containsTile(belowNE)
				&& cache.containsTile(belowSW) && cache.containsTile(belowSE)) {
			Drawable belowNWTile = cache.getMapTile(belowNW);
			Drawable belowNETile = cache.getMapTile(belowNE);
			Drawable belowSWTile = cache.getMapTile(belowSW);
			Drawable belowSETile = cache.getMapTile(belowSE);

			if (!(belowNWTile instanceof MapTileInterpolator) && !(belowNETile instanceof MapTileInterpolator)
					&& !(belowSWTile instanceof MapTileInterpolator) && !(belowSETile instanceof MapTileInterpolator)) {
				return new MapTileInterpolator(mapTile, null, new Drawable[] { belowNWTile, belowNETile, belowSWTile, belowSETile});
			}
		}

		// Attempt to pull data from the level above us
		MapTile above = new MapTile(mapTile.getZoomLevel()-1, mapTile.getX()>>1, mapTile.getY()>>1);
		if (cache.containsTile(above)) {
			Drawable aboveTile = cache.getMapTile(above);
			if (!(aboveTile instanceof MapTileInterpolator)) {
				return new MapTileInterpolator(mapTile, aboveTile, null);
			}
		}

		// Couldn't load anything useful from the current cache data
		return null;
	}

	private MapTileInterpolator(MapTile tile, Drawable above, Drawable[] below) {
		this.tile = tile;
		this.above = above;
		this.below = below;
	}

	@Override
	public void draw(Canvas canvas) {
		final Rect bounds = getBounds();
		final int tileSize = bounds.width();

		if (above != null) {
			// Upscale and clip the image
			final int xOff = tile.getX() % 2;
			final int yOff = tile.getY() % 2;

			above.setBounds(
					bounds.left - (xOff == 0 ? 0 : tileSize),
					bounds.top - (yOff == 0 ? 0 : tileSize),
					bounds.right + (xOff == 0 ? tileSize : 0),
					bounds.bottom + (yOff == 0 ? tileSize : 0));

			canvas.save();
			canvas.clipRect(bounds);
			above.draw(canvas);
			canvas.restore();
		}
		if (below != null) {
			// Downscale the component images and draw
			final int midX = bounds.left + tileSize/2;
			final int midY = bounds.top + tileSize/2;

			below[0].setBounds(bounds.left, bounds.top, midX, midY);
			below[0].draw(canvas);

			below[1].setBounds(midX, bounds.top, bounds.right, midY);
			below[1].draw(canvas);

			below[2].setBounds(bounds.left, midY, midX, bounds.bottom);
			below[2].draw(canvas);

			below[3].setBounds(midX, midY, bounds.right, bounds.bottom);
			below[3].draw(canvas);
		}
	}

	@Override
	public void setAlpha(int alpha) {
		if (above != null) {
			above.setAlpha(alpha);
		}
		if (below != null) {
			for (Drawable entry : below) {
				entry.setAlpha(alpha);
			}
		}
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		if (above != null) {
			above.setColorFilter(cf);
		}
		if (below != null) {
			for (Drawable entry : below) {
				entry.setColorFilter(cf);
			}
		}
	}

	@Override
	public int getOpacity() {
		if (above != null) {
			return above.getOpacity();
		}
		if (below != null) {
			int opacity = PixelFormat.OPAQUE;
			for (Drawable entry : below) {
				opacity = resolveOpacity(opacity, entry.getOpacity());
			}
			return opacity;
		}
		return PixelFormat.UNKNOWN;
	}

}
