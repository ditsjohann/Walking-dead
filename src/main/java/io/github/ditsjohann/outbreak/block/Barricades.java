package io.github.ditsjohann.outbreak.block;

import java.util.HashMap;
import java.util.Map;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Walkers battering barricades and smashing windows. */
public final class Barricades {
	/** Walker hits needed to tear off one plank. */
	private static final int HITS_PER_PLANK = 6;
	/** Walker hits needed to smash a window. */
	private static final int HITS_PER_WINDOW = 3;
	private static final Map<Long, Integer> HITS = new HashMap<>();

	private Barricades() {
	}

	/** Glass and glass panes: what walkers smash their way through. */
	public static final TagKey<Block> WINDOWS = TagKey.create(Registries.BLOCK, Outbreak.id("windows"));

	public static boolean isWindow(BlockState state) {
		return state.is(WINDOWS);
	}

	public static boolean isBreakable(BlockState state) {
		return state.is(OutbreakBlocks.BARRICADE) || isWindow(state);
	}

	/** One walker blow against a barricade or window. Returns true when something gave way. */
	public static boolean hit(ServerLevel level, BlockPos pos, Entity attacker) {
		BlockState state = level.getBlockState(pos);
		if (!isBreakable(state)) {
			return false;
		}

		long key = pos.asLong();
		int hits = HITS.merge(key, 1, Integer::sum);
		Vec3 center = Vec3.atCenterOf(pos);
		if (state.is(OutbreakBlocks.BARRICADE)) {
			level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			NoiseSystem.emit(level, center, 16.0F, attacker);
			if (hits < HITS_PER_PLANK) {
				return false;
			}

			HITS.remove(key);
			removePlank(level, pos, state, false);
			return true;
		}

		NoiseSystem.emit(level, center, 12.0F, attacker);
		if (hits < HITS_PER_WINDOW) {
			level.playSound(null, pos, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
			return false;
		}

		HITS.remove(key);
		level.destroyBlock(pos, false, attacker);
		NoiseSystem.emit(level, center, 20.0F, attacker);
		return true;
	}

	/** Take one plank off; the last one removes the barricade. Optionally drops the plank. */
	public static void removePlank(ServerLevel level, BlockPos pos, BlockState state, boolean dropPlank) {
		int planks = state.getValue(BarricadeBlock.PLANKS);
		if (planks <= 1) {
			level.destroyBlock(pos, false);
		} else {
			level.setBlock(pos, state.setValue(BarricadeBlock.PLANKS, planks - 1), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
		}

		if (dropPlank) {
			Block.popResource(level, pos, new ItemStack(Items.OAK_PLANKS));
		}
	}

	public static void clear() {
		HITS.clear();
	}
}
