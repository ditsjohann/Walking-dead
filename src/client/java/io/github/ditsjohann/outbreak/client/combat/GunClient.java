package io.github.ditsjohann.outbreak.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.combat.GunItem;
import io.github.ditsjohann.outbreak.combat.GunType;
import io.github.ditsjohann.outbreak.combat.Guns;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Guns on the client: left-click fires (right-click held aims: tighter, zoomed, the rifle through
 * its scope), the barrel kicks up, R reloads. The shot itself is the server's (see {@link Guns}).
 */
public final class GunClient {
	private static final int KICK_TICKS = 5;
	private static final Identifier SCOPE = Outbreak.id("textures/gui/rifle_scope.png");

	private static long lastShot = -100;
	private static long readyAt;
	private static float aim;
	private static float aimO;

	private GunClient() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(GunClient::tick);
		// Times are the world's game time: a new world starts its clock again.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			lastShot = -100;
			readyAt = 0;
			aim = 0.0F;
			aimO = 0.0F;
		});
	}

	public static GunType held(LocalPlayer player) {
		return player.getMainHandItem().getItem() instanceof GunItem gun ? gun.type : null;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		aimO = aim;
		GunType type = player == null ? null : held(player);
		boolean aiming = type != null && player.isUsingItem();
		aim = Mth.approach(aim, aiming ? 1.0F : 0.0F, 0.25F);
		if (type == null || minecraft.gui.screen() != null || player.getVehicle() != null) {
			return;
		}

		boolean fire = false;
		while (minecraft.options.keyAttack.consumeClick()) {
			fire = true;
		}

		long now = player.level().getGameTime();
		if (!fire || now < readyAt) {
			return;
		}

		ItemStack stack = player.getMainHandItem();
		readyAt = now + (GunItem.rounds(stack) > 0 ? type.cooldown : 8);
		ClientPlayNetworking.send(new Guns.GunPayload(aiming ? Guns.GunPayload.FIRE_AIMED : Guns.GunPayload.FIRE));
		if (GunItem.rounds(stack) > 0) {
			lastShot = now;
			player.setXRot(Mth.clamp(player.getXRot() - Guns.recoil(type, aiming), -90.0F, 90.0F));
			player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * Guns.recoil(type, aiming) * 0.4F);
		}
	}

	/** R while holding a gun (and not in a vehicle). */
	public static boolean reload(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || player.getVehicle() != null || held(player) == null) {
			return false;
		}

		ClientPlayNetworking.send(new Guns.GunPayload(Guns.GunPayload.RELOAD));
		return true;
	}

	/** 0 (at the hip) to 1 (aimed), smoothed. */
	public static float aim(float partialTicks) {
		return Mth.lerp(partialTicks, aimO, aim);
	}

	/** How zoomed in you are: a bit when aiming, a lot through the rifle's scope. */
	public static float fov(LocalPlayer player, float partialTicks) {
		GunType type = held(player);
		if (type == null) {
			return 1.0F;
		}

		float zoom = type == GunType.RIFLE ? 0.45F : 0.88F;
		return Mth.lerp(aim(partialTicks), 1.0F, zoom);
	}

	/**
	 * First person: aimed, the gun comes up to the middle of the screen, sights at your eye; each shot
	 * it kicks back and up.
	 */
	public static void transform(PoseStack poseStack, int invert, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || held(minecraft.player) == null) {
			return;
		}

		float a = aim(partialTicks);
		// Down the sights: the gun comes to the middle, its top just under your eye line.
		poseStack.translate(-invert * 0.56F * a, 0.05F * a, 0.16F * a);
		float since = minecraft.player.level().getGameTime() - lastShot + partialTicks;
		if (since >= 0.0F && since < KICK_TICKS) {
			float kick = 1.0F - since / KICK_TICKS;
			poseStack.translate(0.0F, 0.02F * kick, 0.14F * kick);
			poseStack.rotateDegrees(Axis.XP, 9.0F * kick);
		}
	}

	/** Looking through the rifle's scope (aimed all the way). */
	public static boolean scoped(LocalPlayer player) {
		return held(player) == GunType.RIFLE && aim(1.0F) > 0.95F;
	}

	/** The rifle's scope: black all round, a fine crosshair. */
	public static void extractScope(GuiGraphicsExtractor graphics, LocalPlayer player) {
		if (!scoped(player)) {
			return;
		}

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		int size = Math.min(w, h) - 10;
		int left = (w - size) / 2;
		int top = (h - size) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE, left, top, 0.0F, 0.0F, size, size, size, size);
		graphics.fill(0, 0, left, h, 0xFF000000);
		graphics.fill(left + size, 0, w, h, 0xFF000000);
		graphics.fill(left, 0, left + size, top, 0xFF000000);
		graphics.fill(left, top + size, left + size, h, 0xFF000000);
	}

	/** The rounds in the gun you hold, and how many more you carry, bottom right. */
	public static void extract(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
		GunType type = held(player);
		if (type == null) {
			return;
		}

		int spare = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(type.ammo())) {
				spare += stack.getCount();
			}
		}

		int rounds = GunItem.rounds(player.getMainHandItem());
		Component text = Component.translatable("outbreak.gun.hud", rounds, type.capacity, spare);
		int x = graphics.guiWidth() - 8 - font.width(text);
		int y = graphics.guiHeight() - 20;
		graphics.fill(x - 4, y - 3, graphics.guiWidth() - 4, y + 11, 0xB0201C18);
		graphics.text(font, text, x, y, rounds == 0 ? 0xFFE04030 : 0xFFF2E8D0, false);
	}
}
