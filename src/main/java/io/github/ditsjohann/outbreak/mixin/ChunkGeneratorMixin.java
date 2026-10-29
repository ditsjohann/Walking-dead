package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.vanilla.VanillaRules;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Wherever the game would spawn a monster, it spawns walkers instead: in every biome, structure and
 * world (including Arnis worlds, whatever biomes they use).
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
	@Inject(method = "getMobsAt", at = @At("HEAD"), cancellable = true)
	private void outbreak$walkersOnly(Level level, StructureManager structureManager, MobCategory category, BlockPos pos,
			CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
		if (category == MobCategory.MONSTER) {
			cir.setReturnValue(VanillaRules.WALKER_SPAWNS);
		}
	}
}
