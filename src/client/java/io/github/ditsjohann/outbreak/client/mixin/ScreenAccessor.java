package io.github.ditsjohann.outbreak.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

/** Lets our title screen (a TitleScreen, for everything that checks for one) draw its widgets without Minecraft's logo. */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("renderables")
	List<Renderable> outbreak$renderables();
}
