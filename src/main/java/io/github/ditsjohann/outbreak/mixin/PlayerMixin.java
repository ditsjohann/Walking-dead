package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.vanilla.VanillaRules;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** No punching trees or concrete: blocks that need a tool can't be broken at all without the right one. */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void outbreak$needsTool(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player player = (Player) (Object) this;
		if ((state.requiresCorrectToolForDrops() || state.is(VanillaRules.NEEDS_TOOL)) && !player.getMainHandItem().isCorrectToolForDrops(state)) {
			cir.setReturnValue(0.0F);
		}
	}
}
