package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.menu.MenuClient;
import io.github.ditsjohann.outbreak.client.menu.OutbreakTitleScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Our own title screen, wherever Minecraft opens its own. */
@Mixin(Gui.class)
abstract class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen outbreak$ourTitleScreen(Screen screen) {
		return screen == null ? null : MenuClient.replaceTitle(screen);
	}

	/** Closing every screen with no world open falls back to the title screen: ours. */
	@Redirect(method = "setScreen", at = @At(value = "NEW", target = "()Lnet/minecraft/client/gui/screens/TitleScreen;"))
	private TitleScreen outbreak$ourFallbackTitleScreen() {
		return new OutbreakTitleScreen();
	}
}
