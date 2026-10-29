package io.github.ditsjohann.outbreak.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;

/** Nether portals never light. */
@Mixin(PortalShape.class)
abstract class PortalShapeMixin {
	@Inject(method = "findEmptyPortalShape", at = @At("HEAD"), cancellable = true)
	private static void outbreak$noPortals(LevelAccessor level, BlockPos pos, Direction.Axis axis, CallbackInfoReturnable<Optional<PortalShape>> cir) {
		cir.setReturnValue(Optional.empty());
	}
}
