package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.character.Skill;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Maintenance: a survivor who looks after their weapon wears it out more slowly (and learns by using it). */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "postHurtEnemy", at = @At("HEAD"), cancellable = true)
	private void outbreak$maintenance(LivingEntity mob, LivingEntity attacker, CallbackInfo ci) {
		ItemStack stack = (ItemStack) (Object) this;
		if (attacker instanceof ServerPlayer player && stack.isDamageableItem() && Characters.hasCharacter(player)) {
			Characters.addXp(player, Skill.MAINTENANCE, 1.0F);
			if (player.getRandom().nextFloat() < Perks.sparedWear(player)) {
				ci.cancel();
			}
		}
	}
}
