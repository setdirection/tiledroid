package org.osmdroid.tileprovider.tilesource;


public class TileSourceFactory {
	public static final OnlineTileSourceBase MAPNIK = new XYTileSource("Mapnik", 0, 18, 256, ".png", "http://tile.openstreetmap.org/") {
		@Override
		public int getWorldHeight() {
			return 67108864;
		}
		@Override
		public int getWorldWidth() {
			return 67108864;
		}
	};
	public static final OnlineTileSourceBase DEFAULT_TILE_SOURCE = MAPNIK;
}
