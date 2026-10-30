package io.github.ditsjohann.outbreak.gore;

import io.github.ditsjohann.outbreak.block.OutbreakBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Blood, server side: a spray of it flying off a hit (dark red chunks with real gravity, seen by
 * everyone), and puddles where it lands. Puddles only go on bare ground with air above (never over
 * plants, in water, or on top of anything a player built in the way).
 */
public final class Blood {
	private static final BlockParticleOption PARTICLE = new BlockParticleOption(ParticleTypes.BLOCK, OutbreakBlocks.BLOOD.defaultBlockState());

	private Blood() {
	}

	/** A spray of blood from pos, thrown roughly along dir (a unit-ish vector) at up to this speed. */
	public static void spray(ServerLevel level, Vec3 pos, Vec3 dir, int count, float speed) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double s = speed * (0.3 + random.nextDouble() * 0.7);
			double vx = dir.x * s + random.nextGaussian() * 0.06;
			double vy = Math.max(0.0, dir.y) * s + 0.05 + random.nextDouble() * 0.12;
			double vz = dir.z * s + random.nextGaussian() * 0.06;
			// Count 0: the "spread" is the velocity of a single particle.
			level.sendParticles(PARTICLE, pos.x, pos.y, pos.z, 0, vx, vy, vz, 1.0);
		}
	}

	/** Blood on the ground at or just below pos: drops (0), a splatter (1) or a pool (2). A bigger one wins over a smaller. */
	public static void splat(ServerLevel level, Vec3 pos, int size) {
		BlockPos start = BlockPos.containing(pos.x, pos.y + 0.5, pos.z);
		for (int down = 0; down < 4; down++) {
			BlockPos at = start.below(down);
			BlockState state = level.getBlockState(at);
			if (!state.isAir() && !BloodBlock.isBlood(state)) {
				// Something in the way: no blood seeps through it.
				return;
			}

			BlockPos below = at.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				int existing = BloodBlock.isBlood(state) ? state.getValue(BloodBlock.SIZE) : -1;
				Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
				BlockState blood = OutbreakBlocks.BLOOD.defaultBlockState()
					.setValue(BloodBlock.SIZE, Math.max(existing, Mth.clamp(size, 0, 2)))
					.setValue(BloodBlock.AGE, 0)
					.setValue(BloodBlock.FACING, existing >= 0 ? state.getValue(BloodBlock.FACING) : facing);
				level.setBlock(at, blood, Block.UPDATE_ALL);
				return;
			}
		}
	}

	/**
	 * Blood thrown against a wall: if there's one within reach blocks of from, the way dir goes
	 * (level), it gets a splash on the face that was hit (bigger close up).
	 */
	public static void wall(ServerLevel level, Vec3 from, Vec3 dir, double reach) {
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		if (flat.lengthSqr() < 1.0E-6) {
			return;
		}

		RandomSource random = level.getRandom();
		Vec3 aim = flat.normalize().add(random.nextGaussian() * 0.45, (random.nextDouble() - 0.35) * 0.6, random.nextGaussian() * 0.45).normalize();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(aim.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
			return;
		}

		Direction face = hit.getDirection();
		BlockPos wall = hit.getBlockPos();
		BlockPos at = wall.relative(face);
		BlockState there = level.getBlockState(at);
		if (!(there.isAir() || WallBloodBlock.isWallBlood(there)) || !level.getBlockState(wall).isFaceSturdy(level, wall, face)) {
			return;
		}

		int size = hit.getLocation().distanceTo(from) < reach * 0.5 ? 1 : 0;
		if (WallBloodBlock.isWallBlood(there)) {
			size = Math.max(size, there.getValue(WallBloodBlock.SIZE));
		}

		level.setBlock(at, OutbreakBlocks.WALL_BLOOD.defaultBlockState().setValue(WallBloodBlock.FACING, face).setValue(WallBloodBlock.SIZE, size),
			Block.UPDATE_ALL);
	}

	/** A pool spreading out where a body lies: a big puddle in the middle, splatter round it. */
	public static void pool(ServerLevel level, Vec3 centre, int spread) {
		splat(level, centre, 2);
		RandomSource random = level.getRandom();
		for (int i = 0; i < spread; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 0.8 + random.nextDouble() * 0.8;
			splat(level, centre.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance), random.nextInt(2));
		}
	}
}
