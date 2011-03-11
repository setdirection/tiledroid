package org.osmdroid.tileprovider.tilesource;


public class TileSourceFactory {
	public static final OnlineTileSourceBase MAPNIK = new XYTileSource("Mapnik", 0, 18, 256, ".png", "http://tile.openstreetmap.org/");
	public static final OnlineTileSourceBase DEFAULT_TILE_SOURCE = MAPNIK;
}
