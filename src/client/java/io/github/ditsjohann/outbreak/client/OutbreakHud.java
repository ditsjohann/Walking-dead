package io.github.ditsjohann.outbreak.client;

import java.util.Locale;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.vehicle.Dashboard;
import io.github.ditsjohann.outbreak.client.vehicle.Ignition;
import io.github.ditsjohann.outbreak.client.world.BroadcastClient;
import io.github.ditsjohann.outbreak.entity.Walker;
import io.github.ditsjohann.outbreak.inventory.CarryWeight;
import io.github.ditsjohann.outbreak.survivor.Moodle;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;

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
 * The outbreak's own HUD, replacing vanilla's entirely, drawn as a survivor's notes on old paper: a hotbar, a crosshair that shows when
 * you're lined up for a headshot, a status panel (body, day and time, health, noise, weight),
 * moodles, and a breath bar underwater.
 */
final class OutbreakHud {
	private static final int SLOT = 20;
	private static final int MOODLE_SIZE = 20;
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
		Dashboard.extract(graphics, minecraft.font, player);
		Ignition.extract(graphics, minecraft.font, player);
		BroadcastClient.extract(graphics, minecraft.font);
		if (player.isCreative()) {
			return;
		}

		SurvivorState state = Survivors.get(player);
		extractStatus(graphics, minecraft, player, state);
		drawMoodles(graphics, player, state, graphics.guiWidth() - MOODLE_SIZE - 6, 6);
		extractBreath(graphics, player);
	}

	// --- Hotbar ---

	private static final Identifier HOTBAR = Outbreak.id("hud/hotbar");
	private static final Identifier HOTBAR_SELECTION = Outbreak.id("hud/hotbar_selection");
	private static final Identifier OFFHAND = Outbreak.id("hud/offhand");
	private static final Identifier STATUS = Outbreak.id("hud/status");
	private static final Identifier TAG = Outbreak.id("hud/tag");
	private static final int HOTBAR_WIDTH = 188;
	private static final int HOTBAR_HEIGHT = 28;

	private static void extractHotbar(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int left = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2;
		int top = graphics.guiHeight() - HOTBAR_HEIGHT - 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, left, top, HOTBAR_WIDTH, HOTBAR_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION, left + 4 + inventory.getSelectedSlot() * SLOT, top + 4, SLOT, SLOT);

		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				int x = left + 6 + i * SLOT;
				graphics.item(player, stack, x, top + 6, i + 1);
				graphics.itemDecorations(minecraft.font, stack, x, top + 6);
			}
		}

		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			int x = left + HOTBAR_WIDTH + 4;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND, x, top, HOTBAR_HEIGHT, HOTBAR_HEIGHT);
			graphics.item(player, offhand, x + 6, top + 6, 10);
			graphics.itemDecorations(minecraft.font, offhand, x + 6, top + 6);
		}

		extractItemName(graphics, minecraft.font, player, inventory, top);
	}

	/** The selected item's name (and how worn it is) on a scrap of paper above the hotbar when you switch. */
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

		String text = stack.getHoverName().getString();
		if (stack.isDamageableItem()) {
			int condition = Math.round(100.0F * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
			text += String.format(Locale.ROOT, "  %d%%", condition);
		}

		int width = font.width(text) + 12;
		int x = graphics.guiWidth() / 2 - width / 2;
		int y = hotbarTop - 17;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAG, x, y, width, 15);
		graphics.text(font, text, x + 6, y + 4, PaperStyle.INK, false);
	}

	// --- Status panel ---

	private static void extractStatus(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, SurvivorState state) {
		Font font = minecraft.font;
		int left = 3;
		int top = graphics.guiHeight() - 76 - 3;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STATUS, left, top, 124, 76);
		BodyDoll.draw(graphics, state, left + 6, top + 6, 2);

		int x = left + 44;
		int y = top + 9;
		long time = player.level().getOverworldClockTime();
		long day = time / 24000L + 1;
		int hour = (int) ((time / 1000L + 6) % 24);
		int minute = (int) (time % 1000L * 60 / 1000);
		graphics.text(font, Component.translatable("outbreak.hud.day", day), x, y, PaperStyle.INK, false);
		graphics.text(font, String.format(Locale.ROOT, "%02d:%02d", hour, minute), x + 44, y, PaperStyle.PENCIL, false);
		y += 13;

		int health = Math.round(player.getHealth() / player.getMaxHealth() * 100);
		graphics.text(font, Component.translatable("outbreak.hud.health", health), x, y, health <= 40 ? PaperStyle.MARKER_RED : PaperStyle.INK, false);
		y += 13;

		int noise = noiseLevel(player);
		graphics.text(font, Component.translatable("outbreak.hud.noise." + noise), x, y, noise >= 3 ? PaperStyle.MARKER_RED : PaperStyle.INK, false);
		for (int i = 0; i < 3; i++) {
			int barX = x + 52 + i * 5;
			int barHeight = 3 + i * 3;
			graphics.fill(barX, y + 8 - barHeight, barX + 3, y + 8, i < noise ? (noise >= 3 ? PaperStyle.MARKER_RED : PaperStyle.INK) : 0x40261A10);
		}

		y += 13;
		float weight = CarryWeight.total(player);
		graphics.text(font, String.format(Locale.ROOT, "%.1f / %s kg", weight, CarryWeight.capacityLabel(player)), x, y,
			weight > CarryWeight.capacity(player) ? PaperStyle.MARKER_RED : PaperStyle.PENCIL, false);
	}

	/**
	 * How much noise your movement makes right now: 0 still, 1 sneaking, 2 walking, 3 running. In a
	 * vehicle it's the vehicle: a running engine is loud, a bicycle is quiet.
	 */
	static int noiseLevel(LocalPlayer player) {
		if (player.getVehicle() instanceof Vehicle vehicle) {
			boolean moving = Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo) > 0.02;
			if (vehicle.vehicleType().hasEngine) {
				return vehicle.isRunning() ? 3 : moving ? 1 : 0;
			}

			return moving ? 1 : 0;
		}

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

	/** Paper tags down the right edge; the marker colour says how bad it is. Level 4 flickers. */
	static int drawMoodles(GuiGraphicsExtractor graphics, LocalPlayer player, SurvivorState state, int x, int y) {
		int drawn = 0;
		for (Moodle moodle : Moodle.values()) {
			int level = moodle.level(player, state);
			if (level <= 0) {
				continue;
			}

			int top = y + drawn * (MOODLE_SIZE + 2);
			int tile = level == 4 && (Util.getMillis() / 500) % 2 == 0 ? 3 : level;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Outbreak.id("hud/moodle_" + tile), x, top, MOODLE_SIZE, MOODLE_SIZE);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, moodleSprite(moodle), x + 2, top + 1, 16, 16);
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

		int left = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2 + 4;
		int width = HOTBAR_WIDTH - 8;
		int y = graphics.guiHeight() - HOTBAR_HEIGHT - 7;
		graphics.fill(left - 1, y - 1, left + width + 1, y + 3, PaperStyle.INK);
		graphics.fill(left, y, left + width, y + 2, 0xFF5A4A36);
		graphics.fill(left, y, left + Math.round(width * Math.max(0, air) / (float) max), y + 2, 0xFF4A8FD8);
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
