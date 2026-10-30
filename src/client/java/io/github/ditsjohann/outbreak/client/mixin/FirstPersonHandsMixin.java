package io.github.ditsjohann.outbreak.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ditsjohann.outbreak.client.combat.Punches;
import io.github.ditsjohann.outbreak.client.combat.SwingAnimations;
import io.github.ditsjohann.outbreak.client.vehicle.Ignition;
import io.github.ditsjohann.outbreak.combat.WeaponStyle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * First person: our swings for our weapons (see {@link SwingAnimations}), and bare-handed punches
 * with both fists in turn (vanilla only ever draws the main arm, swiping).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
	@Invoker("renderPlayerHand")
	abstract void outbreak$renderPlayerHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm, PlayerRenderState playerState);

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void outbreak$rememberItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		CallbackInfo ci) {
		SwingAnimations.current = itemStack;
	}

	/**
	 * Vanilla lowers a weapon while its attack cooldown recovers. Ours swing for that long instead,
	 * so they only dip a little (or the swing plays out below the screen).
	 */
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	private void outbreak$lessDip(FirstPersonHandsAndItemsRenderer renderer, PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, Operation<Void> original) {
		boolean ours = WeaponStyle.of(SwingAnimations.current) != WeaponStyle.OTHER && !SwingAnimations.current.isEmpty();
		original.call(renderer, poseStack, arm, ours ? inverseArmHeight * 0.2F : inverseArmHeight);
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void outbreak$swing(float animation, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (SwingAnimations.firstPerson(SwingAnimations.current, animation, poseStack, invert)) {
			ci.cancel();
		}
	}

	/** The main arm punches only on the right-hand turn; then it's a straight punch, not vanilla's swipe. */
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V"))
	private void outbreak$punchMain(FirstPersonHandsAndItemsRenderer renderer, PoseStack poseStack, SubmitNodeCollector collector, int light,
		float inverseArmHeight, float attack, HumanoidArm arm, PlayerRenderState playerState, Operation<Void> original) {
		if (attack > 0.0F && !Punches.isLeft()) {
			SwingAnimations.punchArm(attack, inverseArmHeight, poseStack, arm);
			this.outbreak$renderPlayerHand(poseStack, collector, light, arm, playerState);
			return;
		}

		original.call(renderer, poseStack, collector, light, inverseArmHeight, 0.0F, arm, playerState);
	}

	/** Driving: your hands are on the wheel (drawn with the vehicle), not holding things up in front of you. */
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void outbreak$handsOnWheel(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		if (Ignition.handsOnWheel()) {
			ci.cancel();
		}
	}

	/** The other fist, for the left-hand turn of an empty-handed swing. */
	@Inject(method = "submitHandsWithItems", at = @At("TAIL"))
	private void outbreak$punchOff(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (avatar == null || avatar.isInvisible || !Punches.isLeft() || avatar.swingAnimation <= 0.0F || state.isScoping
			|| !state.mainHandItem.isEmpty() || !state.offHandItem.isEmpty() || state.attackHand != InteractionHand.MAIN_HAND) {
			return;
		}

		HumanoidArm off = avatar.mainArm.getOpposite();
		poseStack.pushPose();
		SwingAnimations.punchArm(avatar.swingAnimation, 0.0F, poseStack, off);
		this.outbreak$renderPlayerHand(poseStack, collector, avatar.lightCoords, off, playerState);
		poseStack.popPose();
	}
}
