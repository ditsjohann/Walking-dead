package io.github.ditsjohann.outbreak.client.menu;

import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** The menus' side of the network (the world's rules, the character screen, the sandbox editor) and the title screen swap. */
public final class MenuClient {
	private MenuClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Sandbox.SyncPayload.TYPE, (payload, context) -> Sandbox.setFromServer(payload.settings()));
		ClientPlayNetworking.registerGlobalReceiver(Sandbox.OpenEditorPayload.TYPE, (payload, context) ->
			context.client().gui.setScreen(new SandboxScreen(null, payload.settings(), settings -> ClientPlayNetworking.send(new Sandbox.UpdatePayload(settings)))));
		ClientPlayNetworking.registerGlobalReceiver(Characters.OpenCreationPayload.TYPE, (payload, context) ->
			context.client().gui.setScreen(new CharacterCreationScreen(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Sandbox.setFromServer(SandboxSettings.DEFAULT));
	}

	/** Minecraft's title screen, wherever it's opened from, becomes ours. */
	public static Screen replaceTitle(Screen screen) {
		if (screen instanceof TitleScreen && !(screen instanceof OutbreakTitleScreen)) {
			return new OutbreakTitleScreen();
		}

		return screen;
	}
}
