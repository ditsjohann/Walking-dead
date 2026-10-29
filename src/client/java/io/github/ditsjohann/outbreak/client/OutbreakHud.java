package io.github.ditsjohann.outbreak.client;

import java.util.Locale;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.inventory.CarryWeight;
import io.github.ditsjohann.outbreak.survivor.Moodle;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The outbreak's own HUD, replacing vanilla's entirely: a slim hotbar, a crosshair that shows when
 * you're lined up for a headshot, a status panel (body, day and time, health, noise, weight),
 * moodles, and a breath bar underwater.
 */
final class OutbreakHud {
	private static final int SLOT = 20;
	private static final int MOODLE_SIZE = 20;
	private static final int[] MOODLE_COLORS = {0, 0xB0B89A3A, 0xB0D0702A, 0xB0C03028, 0xD08B0000};
	private static final int ITEM_NAME_TICKS = 50;

	private static int lastSelectedSlot = -1;
	private static ItemStack lastSelectedStack = ItemStack.EMPTY;
	private static long itemNameShownAt;

	private OutbreakHud() {
	}

	static void register() {
		for (Identifier vanilla : new Identifier[] {
			VanillaHudElements.HOTBAR, VanillaHudElements.CROSSHAIR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
			VanillaHudElements.ARMOR_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
			VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS}) {
			HudElementRegistry.removeElement(vanilla);
		}

		HudElementRegistry.addFirst(Outbreak.id("crosshair"), OutbreakHud::extractCrosshair);
		HudElementRegistry.addFirst(Outbreak.id("hud"), OutbreakHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}

		extractHotbar(graphics, minecraft, player);
		if (player.isCreative()) {
			return;
		}

		SurvivorState state = Survivors.get(player);
		extractStatus(graphics, minecraft, player, state);
		drawMoodles(graphics, player, state, graphics.guiWidth() - MOODLE_SIZE - 6, 6);
		extractBreath(graphics, player);
	}

	// --- Hotbar ---

	private static void extractHotbar(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int width = 9 * SLOT;
		int left = graphics.guiWidth() / 2 - width / 2;
		int top = graphics.guiHeight() - SLOT - 4;
		HudStyle.panel(graphics, left - 2, top - 2, width + 4, SLOT + 4);

		int selected = inventory.getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			int x = left + i * SLOT;
			graphics.fill(x + 1, top + 1, x + SLOT - 1, top + SLOT - 1, HudStyle.SLOT);
			if (i == selected) {
				graphics.outline(x, top, SLOT, SLOT, HudStyle.ACCENT);
			}

			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				graphics.item(player, stack, x + 2, top + 2, i + 1);
				graphics.itemDecorations(minecraft.font, stack, x + 2, top + 2);
			}
		}

		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			int x = left + width + 8;
			HudStyle.panel(graphics, x - 2, top - 2, SLOT + 4, SLOT + 4);
			graphics.item(player, offhand, x + 2, top + 2, 10);
			graphics.itemDecorations(minecraft.font, offhand, x + 2, top + 2);
		}

		extractItemName(graphics, minecraft.font, player, inventory, top);
	}

	/** The selected item's name (and how worn it is) fades in above the hotbar when you switch. */
	private static void extractItemName(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, Inventory inventory, int hotbarTop) {
		ItemStack stack = inventory.getSelectedItem();
		long now = player.level().getGameTime();
		if (inventory.getSelectedSlot() != lastSelectedSlot || !ItemStack.isSameItem(stack, lastSelectedStack)) {
			lastSelectedSlot = inventory.getSelectedSlot();
			lastSelectedStack = stack.copy();
			itemNameShownAt = now;
		}

		long age = now - itemNameShownAt;
		if (stack.isEmpty() || age > ITEM_NAME_TICKS) {
			return;
		}

		float alpha = age < ITEM_NAME_TICKS - 10 ? 1.0F : (ITEM_NAME_TICKS - age) / 10.0F;
		String text = stack.getHoverName().getString();
		if (stack.isDamageableItem()) {
			int condition = Math.round(100.0F * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
			text += String.format(Locale.ROOT, "  %d%%", condition);
		}

		int width = font.width(text);
		int x = graphics.guiWidth() / 2 - width / 2;
		int y = hotbarTop - 16;
		graphics.fill(x - 4, y - 3, x + width + 4, y + 11, HudStyle.fade(HudStyle.PANEL, alpha));
		graphics.text(font, text, x, y, HudStyle.fade(HudStyle.TEXT, alpha), false);
	}

	// --- Status panel ---

	private static void extractStatus(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, SurvivorState state) {
		Font font = minecraft.font;
		int height = BodyDoll.HEIGHT * 2 + 8;
		int left = 4;
		int top = graphics.guiHeight() - height - 4;
		HudStyle.panel(graphics, left, top, 118, height);
		BodyDoll.draw(graphics, state, left + 4, top + 4, 2);

		int x = left + 44;
		int y = top + 6;
		long time = player.level().getOverworldClockTime();
		long day = time / 24000L + 1;
		int hour = (int) ((time / 1000L + 6) % 24);
		int minute = (int) (time % 1000L * 60 / 1000);
		graphics.text(font, Component.translatable("outbreak.hud.day", day), x, y, HudStyle.TEXT, false);
		graphics.text(font, String.format(Locale.ROOT, "%02d:%02d", hour, minute), x + 44, y, HudStyle.DIM, false);
		y += 13;

		int health = Math.round(player.getHealth() / player.getMaxHealth() * 100);
		graphics.text(font, Component.translatable("outbreak.hud.health", health), x, y, health <= 40 ? HudStyle.DANGER : HudStyle.TEXT, false);
		y += 13;

		int noise = noiseLevel(player);
		graphics.text(font, Component.translatable("outbreak.hud.noise." + noise), x, y, noise >= 3 ? HudStyle.DANGER : HudStyle.TEXT, false);
		for (int i = 0; i < 3; i++) {
			int barX = x + 52 + i * 5;
			int barHeight = 3 + i * 3;
			graphics.fill(barX, y + 8 - barHeight, barX + 3, y + 8, i < noise ? (noise >= 3 ? HudStyle.DANGER : HudStyle.ACCENT) : 0x40FFFFFF);
		}

		y += 13;
		float weight = CarryWeight.total(player);
		graphics.text(font, String.format(Locale.ROOT, "%.1f / %.0f kg", weight, CarryWeight.CAPACITY), x, y,
			weight > CarryWeight.CAPACITY ? HudStyle.DANGER : HudStyle.DIM, false);
	}

	/** How much noise your movement makes right now: 0 still, 1 sneaking, 2 walking, 3 running. */
	static int noiseLevel(LocalPlayer player) {
		double speed = player.getDeltaMovement().horizontalDistanceSqr();
		if (speed < 1.0E-4 && player.onGround()) {
			return 0;
		}

		if (player.isCrouching()) {
			return 1;
		}

		return player.isSprinting() ? 3 : 2;
	}

	// --- Moodles ---

	static int drawMoodles(GuiGraphicsExtractor graphics, LocalPlayer player, SurvivorState state, int x, int y) {
		int drawn = 0;
		for (Moodle moodle : Moodle.values()) {
			int level = moodle.level(player, state);
			if (level <= 0) {
				continue;
			}

			int top = y + drawn * (MOODLE_SIZE + 2);
			int color = MOODLE_COLORS[level];
			if (level == 4 && (Util.getMillis() / 500) % 2 == 0) {
				color = 0xF0D01010;
			}

			graphics.fill(x, top, x + MOODLE_SIZE, top + MOODLE_SIZE, color);
			graphics.outline(x, top, MOODLE_SIZE, MOODLE_SIZE, HudStyle.BORDER);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, moodleSprite(moodle), x + 2, top + 2, 16, 16);
			drawn++;
		}

		return drawn;
	}

	static Identifier moodleSprite(Moodle moodle) {
		return Outbreak.id("moodle/" + moodle.id());
	}

	// --- Breath ---

	private static void extractBreath(GuiGraphicsExtractor graphics, LocalPlayer player) {
		int air = player.getAirSupply();
		int max = player.getMaxAirSupply();
		if (air >= max) {
			return;
		}

		int width = 9 * SLOT;
		int left = graphics.guiWidth() / 2 - width / 2;
		int y = graphics.guiHeight() - SLOT - 11;
		graphics.fill(left - 2, y, left + width + 2, y + 3, HudStyle.PANEL);
		graphics.fill(left - 2, y, left - 2 + Math.round((width + 4) * Math.max(0, air) / (float) max), y + 3, HudStyle.WATER);
	}

	// --- Crosshair ---

	/** A small dot; red when a walker's head is in reach. A thin bar under it while your swing recovers. */
	private static void extractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}

		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		boolean headshot = aimingAtHead(minecraft.hitResult);
		int color = headshot ? HudStyle.DANGER : 0xE0FFFFFF;
		graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x90000000);
		graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
		if (headshot) {
			// Brackets around the dot: line it up and swing.
			graphics.fill(cx - 6, cy - 6, cx - 3, cy - 5, color);
			graphics.fill(cx - 6, cy - 6, cx - 5, cy - 3, color);
			graphics.fill(cx + 3, cy - 6, cx + 6, cy - 5, color);
			graphics.fill(cx + 5, cy - 6, cx + 6, cy - 3, color);
			graphics.fill(cx - 6, cy + 5, cx - 3, cy + 6, color);
			graphics.fill(cx - 6, cy + 3, cx - 5, cy + 6, color);
			graphics.fill(cx + 3, cy + 5, cx + 6, cy + 6, color);
			graphics.fill(cx + 5, cy + 3, cx + 6, cy + 6, color);
		}

		float strength = player.getAttackStrengthScale(0.0F);
		if (strength < 1.0F) {
			graphics.fill(cx - 8, cy + 9, cx + 8, cy + 11, 0x90000000);
			graphics.fill(cx - 8, cy + 9, cx - 8 + Math.round(16 * strength), cy + 11, 0xC0FFFFFF);
		}
	}

	private static boolean aimingAtHead(HitResult hit) {
		if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Walker walker)) {
			return false;
		}

		return entityHit.getLocation().y >= walker.getY() + walker.getBbHeight() * Walker.HEAD_START;
	}
}
