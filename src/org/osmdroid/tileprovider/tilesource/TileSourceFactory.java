package org.osmdroid.tileprovider.tilesource;

import java.util.ArrayList;

import org.osmdroid.ResourceProxy;

public class TileSourceFactory {

	/**
	 * Get the tile source with the specified name.
	 *
	 * @param aName
	 *            the tile source name
	 * @return the tile source
	 * @throws IllegalArgumentException
	 *             if tile source not found
	 */
	public static ITileSource getTileSource(final String aName) throws IllegalArgumentException {
		for (final ITileSource tileSource : mTileSources) {
			// TODO perhaps we should ignore case and white space
			if (tileSource.name().equals(aName)) {
				return tileSource;
			}
		}
		throw new IllegalArgumentException("No such tile source: " + aName);
	}

	/**
	 * Get the tile source at the specified position.
	 *
	 * @param aOrdinal
	 * @return the tile source
	 * @throws IllegalArgumentException
	 *             if tile source not found
	 */
	public static ITileSource getTileSource(final int aOrdinal) throws IllegalArgumentException {
		for (final ITileSource tileSource : mTileSources) {
			if (tileSource.ordinal() == aOrdinal) {
				return tileSource;
			}
		}
		throw new IllegalArgumentException("No tile source at position: " + aOrdinal);
	}

	public static ArrayList<ITileSource> getTileSources() {
		return mTileSources;
	}

	public static void addTileSource(ITileSource mTileSource) {
		mTileSources.add(mTileSource);
	}

	public static final OnlineTileSourceBase OSMARENDER = new XYTileSource("Osmarender",
			ResourceProxy.string.osmarender, 0, 17, 256, ".png",
			"http://tah.openstreetmap.org/Tiles/tile/");

	public static final OnlineTileSourceBase MAPNIK = new XYTileSource("Mapnik",
			ResourceProxy.string.mapnik, 0, 18, 256, ".png", "http://tile.openstreetmap.org/");

	public static final OnlineTileSourceBase CYCLEMAP = new XYTileSource("CycleMap",
			ResourceProxy.string.cyclemap, 0, 17, 256, ".png",
			"http://a.andy.sandbox.cloudmade.com/tiles/cycle/",
			"http://b.andy.sandbox.cloudmade.com/tiles/cycle/",
			"http://c.andy.sandbox.cloudmade.com/tiles/cycle/");

	public static final OnlineTileSourceBase PUBLIC_TRANSPORT = new XYTileSource(
			"OSMPublicTransport", ResourceProxy.string.public_transport, 0, 17, 256, ".png",
			"http://tile.xn--pnvkarte-m4a.de/tilegen/");

	public static final OnlineTileSourceBase BASE = new XYTileSource("Base",
			ResourceProxy.string.base, 4, 17, 256, ".png", "http://topo.openstreetmap.de/base/");

	public static final OnlineTileSourceBase TOPO = new XYTileSource("Topo",
			ResourceProxy.string.topo, 4, 17, 256, ".png", "http://topo.openstreetmap.de/topo/");

	public static final OnlineTileSourceBase HILLS = new XYTileSource("Hills",
			ResourceProxy.string.hills, 8, 17, 256, ".png", "http://topo.geofabrik.de/hills/");


	public static final OnlineTileSourceBase DEFAULT_TILE_SOURCE = MAPNIK;

	// The following tile sources are overlays, not standalone map views.
	// They are therefore not in mTileSources.

	public static final OnlineTileSourceBase FIETS_OVERLAY_NL = new XYTileSource("Fiets",
			ResourceProxy.string.fiets_nl, 3, 18, 256, ".png",
			"http://overlay.openstreetmap.nl/openfietskaart-overlay/");

	public static final OnlineTileSourceBase BASE_OVERLAY_NL = new XYTileSource("BaseNL",
			ResourceProxy.string.base_nl, 0, 18, 256, ".png",
			"http://overlay.openstreetmap.nl/basemap/");

	public static final OnlineTileSourceBase ROADS_OVERLAY_NL = new XYTileSource("RoadsNL",
			ResourceProxy.string.roads_nl, 0, 18, 256, ".png",
			"http://overlay.openstreetmap.nl/roads/");

	private static ArrayList<ITileSource> mTileSources;
	static {
		mTileSources = new ArrayList<ITileSource>();
		mTileSources.add(OSMARENDER);
		mTileSources.add(MAPNIK);
		mTileSources.add(CYCLEMAP);
		mTileSources.add(PUBLIC_TRANSPORT);
		mTileSources.add(BASE);
		mTileSources.add(TOPO);
		mTileSources.add(HILLS);
	}
}
