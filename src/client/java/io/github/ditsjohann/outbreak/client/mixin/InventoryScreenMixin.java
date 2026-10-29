package io.github.ditsjohann.outbreak.client.mixin;

import java.util.Locale;

import io.github.ditsjohann.outbreak.client.PaperStyle;
import io.github.ditsjohann.outbreak.inventory.CarryWeight;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The inventory is a page of survivor's notes (the texture replaces vanilla's). On it: your pockets,
 * what you're carrying, and where the rest of vanilla's inventory used to be, a reminder to find a bag.
 * Vanilla's effect list next to it is gone: the moodles already say how you feel.
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/EffectsInInventory;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
	private void outbreak$noEffects(EffectsInInventory effects, GuiGraphicsExtractor graphics, int mouseX, int mouseY, Operation<Void> original) {
	}

	@Inject(method = "extractLabels", at = @At("TAIL"))
	private void outbreak$notes(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		Font font = minecraft.font;
		float weight = CarryWeight.total(minecraft.player);
		// Under the pockets row: the armour slots leave no room above it.
		String carrying = String.format(Locale.ROOT, "%.1f / %.0f kg", weight, CarryWeight.CAPACITY);
		graphics.text(font, Component.translatable("outbreak.screen.pockets"), 8, 106, PaperStyle.INK, false);
		graphics.text(font, carrying, 168 - font.width(carrying), 106,
			weight > CarryWeight.CAPACITY ? PaperStyle.MARKER_RED : PaperStyle.PENCIL, false);
		graphics.text(font, Component.translatable("outbreak.screen.find_a_bag"), 8, 120, PaperStyle.PENCIL, false);
	}
}
