package io.github.ditsjohann.outbreak.client.world;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.world.Broadcasts;
import io.github.ditsjohann.outbreak.world.OutbreakSounds;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * NL-Alerts and the radio, on the client. An alert takes over your phone: the tone, and the message
 * at the top of the screen for a while. A line on the radio goes in the chat, as the radio's.
 */
public final class BroadcastClient {
	private static final int ALERT_TICKS = 20 * 14;
	private static final int WIDTH = 230;

	private static @Nullable Component alert;
	private static int alertTicks;

	private BroadcastClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Broadcasts.BroadcastPayload.TYPE, (payload, context) -> {
			Minecraft minecraft = context.client();
			if (payload.kind() == Broadcasts.BroadcastPayload.ALERT) {
				alert = Component.translatable(payload.key());
				alertTicks = ALERT_TICKS;
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(OutbreakSounds.NL_ALERT.value(), 1.0F, 1.0F));
			} else {
				Component line = payload.key().isEmpty() ? Component.translatable("outbreak.radio.static")
					: Component.translatable(payload.key());
				minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("outbreak.radio.prefix").withStyle(ChatFormatting.DARK_GREEN)
					.append(line.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			}
		});
	}

	public static void tick() {
		if (alertTicks > 0 && --alertTicks == 0) {
			alert = null;
		}
	}

	/** The alert, if one came in: a phone notification at the top of the screen. */
	public static void extract(GuiGraphicsExtractor graphics, Font font) {
		Component message = alert;
		if (message == null) {
			return;
		}

		List<FormattedCharSequence> lines = font.split(message, WIDTH - 16);
		int height = 24 + lines.size() * 10;
		int left = (graphics.guiWidth() - WIDTH) / 2;
		// Slides down when it arrives.
		int shown = ALERT_TICKS - alertTicks;
		int top = 6 - Math.max(0, 10 - shown) * (height / 10);
		graphics.fill(left, top, left + WIDTH, top + height, 0xEE1B1E24);
		graphics.fill(left, top, left + WIDTH, top + 2, 0xFFE03A2C);
		graphics.text(font, Component.translatable("outbreak.alert.title").withStyle(ChatFormatting.BOLD), left + 8, top + 7, 0xFFFFFFFF, false);
		graphics.text(font, Component.translatable("outbreak.alert.now"), left + WIDTH - 8 - font.width(Component.translatable("outbreak.alert.now")),
			top + 7, 0xFF9AA0A8, false);
		int y = top + 20;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, left + 8, y, 0xFFE6E8EC, false);
			y += 10;
		}
	}
}
