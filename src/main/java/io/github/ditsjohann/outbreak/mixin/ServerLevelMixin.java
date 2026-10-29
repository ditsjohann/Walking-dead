package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.noise.NoiseSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
	@Inject(method = "gameEvent", at = @At("HEAD"))
	private void outbreak$hearGameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo ci) {
		NoiseSystem.onGameEvent((ServerLevel) (Object) this, gameEvent, position, context);
	}
}
