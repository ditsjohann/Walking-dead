package io.github.ditsjohann.outbreak.world;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.noise.NoiseSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The helicopter (Project Zomboid's helicopter event): an army helicopter comes in low, finds a
 * survivor and circles over them for a minute, as if looking for something, then flies off. It's
 * so loud that every walker for a long way around follows it, and it leaves them all at your door.
 */
public class Helicopter extends Entity {
	private static final double SPEED = 0.7;
	private static final double ALTITUDE = 30.0;
	private static final double CIRCLE_RADIUS = 18.0;
	private static final int CIRCLE_TICKS = 20 * 60;
	private static final int MAX_AGE = 20 * 60 * 5;
	private static final float NOISE = 64.0F;

	private enum Phase {
		APPROACH,
		CIRCLE,
		LEAVE
	}

	private @Nullable UUID target;
	private Phase phase = Phase.APPROACH;
	private int phaseTicks;
	private double angle;
	private Vec3 heading = new Vec3(1.0, 0.0, 0.0);

	public Helicopter(EntityType<? extends Helicopter> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Sends one in from far off, for this player. */
	public static void send(ServerPlayer player, EntityType<Helicopter> type) {
		ServerLevel level = player.level();
		Helicopter helicopter = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (helicopter == null) {
			return;
		}

		// It comes in from as far off as the world is running (it only flies where chunks are ticking).
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double far = edge(level);
		Vec3 from = player.position().add(Math.cos(angle) * far, ALTITUDE + 10.0, Math.sin(angle) * far);
		helicopter.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
		helicopter.target = player.getUUID();
		helicopter.heading = new Vec3(-Math.cos(angle), 0.0, -Math.sin(angle));
		level.addFreshEntity(helicopter);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}

		ServerPlayer player = this.target == null ? null : level.getServer().getPlayerList().getPlayer(this.target);
		if (player != null && player.level() != level) {
			player = null;
		}

		Vec3 goal = null;
		this.phaseTicks++;
		switch (this.phase) {
			case APPROACH -> {
				if (player == null) {
					this.phase = Phase.LEAVE;
				} else {
					goal = above(level, player.position());
					if (horizontal(goal.subtract(this.position())) < CIRCLE_RADIUS) {
						this.phase = Phase.CIRCLE;
						this.phaseTicks = 0;
						this.angle = Math.atan2(this.getZ() - player.getZ(), this.getX() - player.getX());
					}
				}
			}
			case CIRCLE -> {
				if (player == null || this.phaseTicks > CIRCLE_TICKS) {
					this.phase = Phase.LEAVE;
				} else {
					this.angle += SPEED / CIRCLE_RADIUS;
					goal = above(level, player.position().add(Math.cos(this.angle) * CIRCLE_RADIUS, 0.0, Math.sin(this.angle) * CIRCLE_RADIUS));
				}
			}
			case LEAVE -> goal = this.position().add(this.heading.scale(40.0)).add(0.0, 0.3, 0.0);
		}

		Vec3 move = goal == null ? this.heading.scale(SPEED) : goal.subtract(this.position());
		if (move.length() > SPEED) {
			move = move.normalize().scale(SPEED);
		}

		if (move.horizontalDistanceSqr() > 0.0001) {
			this.heading = new Vec3(move.x, 0.0, move.z).normalize();
		}

		this.setDeltaMovement(move);
		this.setPos(this.position().add(move));
		float yaw = (float) (Mth.atan2(this.heading.z, this.heading.x) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.rotLerp(0.2F, this.getYRot(), yaw));

		if (this.tickCount % 20 == 0) {
			// The racket carries: everything for a long way around turns to follow it.
			NoiseSystem.emit(level, ground(level, this.position()), NOISE);
		}

		if (this.tickCount > MAX_AGE || this.phase == Phase.LEAVE && this.farFromEveryone(level)) {
			this.discard();
		}
	}

	private Vec3 above(ServerLevel level, Vec3 pos) {
		return ground(level, pos).add(0.0, ALTITUDE, 0.0);
	}

	private static Vec3 ground(ServerLevel level, Vec3 pos) {
		BlockPos at = BlockPos.containing(pos);
		double y = level.hasChunkAt(at) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) : pos.y - ALTITUDE;
		return new Vec3(pos.x, y, pos.z);
	}

	private static double horizontal(Vec3 v) {
		return Math.sqrt(v.horizontalDistanceSqr());
	}

	/** How far from a player the world is loaded and running: the simulation or view distance, less a chunk. */
	private static double edge(ServerLevel level) {
		var players = level.getServer().getPlayerList();
		return Math.max(32.0, (Math.min(players.getSimulationDistance(), players.getViewDistance()) - 1) * 16.0);
	}

	private boolean farFromEveryone(ServerLevel level) {
		double gone = edge(level);
		for (ServerPlayer player : level.players()) {
			if (horizontal(player.position().subtract(this.position())) < gone) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 400.0 * 400.0;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
