package io.github.ditsjohann.outbreak.character;

import java.util.List;
import java.util.Locale;

import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where a new survivor wakes up. On the Arnis map of Groningen, a real place (worked out from its
 * latitude and longitude the way Arnis lays the map out); anywhere else, the world spawn.
 */
public enum StartPoint {
	GROTE_MARKT(53.2193, 6.5681),
	HOOFDSTATION(53.2108, 6.5646),
	UMCG(53.2215, 6.5762),
	ZERNIKE(53.2405, 6.5366),
	EUROBORG(53.2070, 6.5925),
	PATERSWOLDSEMEER(53.1940, 6.5640),
	WORLD_SPAWN(Double.NaN, Double.NaN),
	RANDOM(Double.NaN, Double.NaN);

	private static final double EARTH_RADIUS = 6371000.0;
	/** How far around the spot to look for open ground (not a roof, not water). */
	private static final int SEARCH_RADIUS = 14;

	public final double lat;
	public final double lng;

	StartPoint(double lat, double lng) {
		this.lat = lat;
		this.lng = lng;
	}

	public boolean isPlace() {
		return !Double.isNaN(this.lat);
	}

	/** The start points on offer with these sandbox settings. */
	public static List<StartPoint> available(SandboxSettings settings) {
		if (settings.map() == SandboxSettings.MapKind.GRONINGEN && parseBbox(settings.bbox()) != null) {
			return List.of(values());
		}

		return List.of(WORLD_SPAWN);
	}

	public String translationKey() {
		return "outbreak.start." + this.name().toLowerCase(Locale.ROOT);
	}

	/** Where to put a survivor starting here: on open ground near the place. */
	public BlockPos resolve(ServerLevel level, SandboxSettings settings, RandomSource random) {
		StartPoint point = this;
		if (point == RANDOM) {
			List<StartPoint> places = available(settings).stream().filter(StartPoint::isPlace).toList();
			point = places.isEmpty() ? WORLD_SPAWN : places.get(random.nextInt(places.size()));
		}

		double[] bbox = parseBbox(settings.bbox());
		BlockPos target;
		if (!point.isPlace() || bbox == null || settings.map() != SandboxSettings.MapKind.GRONINGEN) {
			target = level.getRespawnData().globalPos().pos();
		} else {
			target = project(bbox, point.lat, point.lng);
		}

		return openGround(level, target);
	}

	/** Block x/z of a lat/lng on an Arnis map: x grows east from the bbox's west edge, z grows south from its north edge, 1 block = 1 m. */
	public static BlockPos project(double[] bbox, double lat, double lng) {
		double minLat = bbox[0];
		double minLng = bbox[1];
		double maxLat = bbox[2];
		double maxLng = bbox[3];
		double width = distance(minLat, minLng, minLat, maxLng);
		double height = distance(minLat, minLng, maxLat, minLng);
		int x = (int) Math.floor((lng - minLng) / (maxLng - minLng) * width);
		int z = (int) Math.floor((maxLat - lat) / (maxLat - minLat) * height);
		return new BlockPos(x, 0, z);
	}

	/** {@code min_lat,min_lng,max_lat,max_lng}, as Arnis takes it; null if it doesn't parse. */
	public static double @Nullable [] parseBbox(String bbox) {
		String[] parts = bbox.split(",");
		if (parts.length != 4) {
			return null;
		}

		try {
			double[] values = new double[4];
			for (int i = 0; i < 4; i++) {
				values[i] = Double.parseDouble(parts[i].trim());
			}

			return values[0] < values[2] && values[1] < values[3] ? values : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static double distance(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	/**
	 * The nearest spot that looks like street level: the lowest dry ground around (so not on a roof
	 * or in a canal), closest to the target.
	 */
	private static BlockPos openGround(ServerLevel level, BlockPos target) {
		BlockPos best = null;
		int bestY = Integer.MAX_VALUE;
		int bestDist = Integer.MAX_VALUE;
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx += 2) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz += 2) {
				int x = target.getX() + dx;
				int z = target.getZ() + dz;
				// Far from anyone, the chunk may not be there yet: load (or generate) it first.
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos ground = new BlockPos(x, y - 1, z);
				if (y <= level.getMinY() || !level.getFluidState(ground).isEmpty() || level.getBlockState(ground).is(Blocks.LAVA)) {
					continue;
				}

				int dist = dx * dx + dz * dz;
				// Lower wins (street rather than roof); among the same height, nearer wins.
				if (y < bestY - 1 || (y <= bestY + 1 && dist < bestDist)) {
					best = new BlockPos(x, y, z);
					bestY = Math.min(bestY, y);
					bestDist = dist;
				}
			}
		}

		if (best == null) {
			return new BlockPos(target.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ()), target.getZ());
		}

		return best;
	}
}
