package io.github.ditsjohann.outbreak.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.character.StartPoint;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;
import io.github.ditsjohann.outbreak.world.Places;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where walkers may turn up on their own (natural spawns). Vanilla would fill the area around you
 * with its whole monster cap in seconds, in plain view. Instead:
 * <ul>
 * <li>never where a survivor could see it happen, and never closer than {@link #MIN_DISTANCE};</li>
 * <li>a pack at a time: each survivor's surroundings fill up over a couple of minutes;</li>
 * <li>not on rooftops (in buildings, on any floor, is fine);</li>
 * <li>on the Groningen map, thick in the centre and around the UMCG and the Euroborg (where it
 * started and where people fled), thinner out in the suburbs.</li>
 * </ul>
 */
public final class WalkerSpawning {
	private static final double MIN_DISTANCE = 32.0;
	/** Survivors further away than this can't see a spawn anyway (fog, and the world isn't drawn there). */
	private static final double SIGHT = 96.0;
	/** Packs a survivor's surroundings can gain at once, and how fast that comes back (ticks per pack). */
	private static final float BUDGET = 4.0F;
	private static final float REFILL_TICKS = 160.0F;
	private static final int ROOF_HEIGHT = 4;
	private static final double PACK_SPREAD = 16.0;

	/** A survivor's spawn budget: how much is left and when it was last topped up. */
	private static final class Budget {
		/** A new arrival starts with one pack's worth: the rest comes in over the next minutes. */
		float left = 1.0F;
		long at = -1;
		/** The last walker let through, so the rest of its pack can follow it (same tick, close by). */
		long packTick = -1;
		@Nullable BlockPos packPos;
	}

	private static final Map<UUID, Budget> BUDGETS = new HashMap<>();
	private static @Nullable String placesFor;
	private static @Nullable Place[] places;

	/** A place where the dead are thicker: within radius blocks, everything spawns. */
	private record Place(BlockPos pos, double radius) {
	}

	private WalkerSpawning() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BUDGETS.remove(handler.player.getUUID()));
	}

	/** Whether a natural spawn may happen here. */
	public static boolean allow(ServerLevel level, BlockPos pos, RandomSource random) {
		ServerPlayer nearest = null;
		double nearestSqr = Double.MAX_VALUE;
		List<ServerPlayer> players = level.players();
		Vec3 at = Vec3.atBottomCenterOf(pos);
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}

			double d = player.distanceToSqr(at);
			if (d < nearestSqr) {
				nearestSqr = d;
				nearest = player;
			}
		}

		if (nearest == null || nearestSqr < MIN_DISTANCE * MIN_DISTANCE) {
			return false;
		}

		Budget budget = BUDGETS.computeIfAbsent(nearest.getUUID(), id -> new Budget());
		long now = level.getGameTime();
		// The rest of a pack that was let through comes too (vanilla spawns a pack in one go).
		boolean pack = budget.packTick == now && budget.packPos != null && budget.packPos.distSqr(pos) < PACK_SPREAD * PACK_SPREAD;
		if (!pack && random.nextFloat() >= density(pos)) {
			return false;
		}

		if (budget.at >= 0) {
			budget.left = Math.min(BUDGET, budget.left + (now - budget.at) / REFILL_TICKS);
		}

		budget.at = now;
		if (!pack && budget.left < 1.0F || onRoof(level, pos) || seen(level, players, at)) {
			return false;
		}

		if (!pack) {
			budget.left -= 1.0F;
		}

		budget.packTick = now;
		budget.packPos = pos.immutable();
		return true;
	}

	/** Whether any survivor could see a walker standing here. */
	private static boolean seen(ServerLevel level, List<ServerPlayer> players, Vec3 at) {
		for (ServerPlayer player : players) {
			if (player.isSpectator() || player.distanceToSqr(at) > SIGHT * SIGHT) {
				continue;
			}

			Vec3 eye = player.getEyePosition();
			for (double up : new double[] {0.4, 1.6}) {
				Vec3 point = at.add(0.0, up, 0.0);
				if (level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
					return true;
				}
			}
		}

		return false;
	}

	/** Out in the open, well above the ground around it: a roof. */
	private static boolean onRoof(ServerLevel level, BlockPos pos) {
		if (!level.canSeeSky(pos)) {
			return false;
		}

		int lowest = pos.getY();
		for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
			BlockPos side = pos.offset(d[0], 0, d[1]);
			if (level.hasChunkAt(side)) {
				lowest = Math.min(lowest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, side.getX(), side.getZ()));
			}
		}

		return pos.getY() - lowest > ROOF_HEIGHT;
	}

	/**
	 * How likely a walker is to turn up here (0-1). On the Groningen map: everywhere near the UMCG,
	 * the Euroborg and the station, and thinning out from the city centre to a quarter of that in the
	 * suburbs. Any other map: evenly.
	 */
	public static float density(BlockPos pos) {
		Places.Kind building = Places.at(pos);
		if (building != null && building.crowded) {
			// The hospital, the police station, schools, supermarkets, the barracks: full of them.
			return 1.0F;
		}

		SandboxSettings settings = Sandbox.current();
		Place[] known = places(settings);
		if (known == null) {
			return 0.8F;
		}

		double fromCentre = Math.sqrt(pos.distSqr(known[0].pos.atY(pos.getY())));
		for (Place place : known) {
			if (pos.distSqr(place.pos.atY(pos.getY())) < place.radius * place.radius) {
				return 1.0F;
			}
		}

		return (float) Math.max(0.25, 1.0 - fromCentre / 3500.0 * 0.75);
	}

	private static Place @Nullable [] places(SandboxSettings settings) {
		if (settings.map() != SandboxSettings.MapKind.GRONINGEN) {
			return null;
		}

		if (!settings.bbox().equals(placesFor)) {
			placesFor = settings.bbox();
			double[] bbox = StartPoint.parseBbox(settings.bbox());
			places = bbox == null ? null : new Place[] {
				new Place(StartPoint.project(bbox, StartPoint.GROTE_MARKT.lat, StartPoint.GROTE_MARKT.lng), 500.0),
				new Place(StartPoint.project(bbox, StartPoint.UMCG.lat, StartPoint.UMCG.lng), 300.0),
				new Place(StartPoint.project(bbox, StartPoint.EUROBORG.lat, StartPoint.EUROBORG.lng), 250.0),
				new Place(StartPoint.project(bbox, StartPoint.HOOFDSTATION.lat, StartPoint.HOOFDSTATION.lng), 200.0)};
		}

		return places;
	}
}
