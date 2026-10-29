package io.github.ditsjohann.outbreak.mixin;

import java.util.stream.Stream;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.ditsjohann.outbreak.vanilla.VanillaRules;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;

/** Leaves removed vanilla recipes out of the recipe map, so they can't be crafted or shown in the recipe book. */
@Mixin(RecipeMap.class)
abstract class RecipeMapMixin {
	@WrapOperation(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;"))
	private static Stream<Holder.Reference<Recipe<?>>> outbreak$filterRecipes(HolderLookup<Recipe<?>> recipes, Operation<Stream<Holder.Reference<Recipe<?>>>> original) {
		return original.call(recipes).filter(recipe -> !VanillaRules.isRecipeRemoved(recipe.key()));
	}
}
