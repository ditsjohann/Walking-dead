package io.github.ditsjohann.outbreak.item;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.world.Broadcasts;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/**
 * A portable radio: right-click to switch it on or off. While it's on (anywhere in your inventory) you
 * hear the emergency broadcasts, for as long as the batteries last (the durability bar). Flat
 * batteries are swapped for new ones from your inventory when you switch it on.
 */
public class RadioItem extends Item {
	/** In steps of {@link #DRAIN_TICKS}: an hour of listening on one set of batteries. */
	public static final int BATTERY_LIFE = 720;
	private static final int DRAIN_TICKS = 100;

	public RadioItem(Properties properties) {
		super(properties);
	}

	public static boolean isOn(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data != null && !data.flags().isEmpty() && data.flags().getFirst();
	}

	private static void setOn(ItemStack stack, boolean on) {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(on), List.of(), List.of()));
	}

	private static boolean isFlat(ItemStack stack) {
		return stack.getDamageValue() >= stack.getMaxDamage() - 1;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		if (isOn(stack)) {
			setOn(stack, false);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.5F, 1.4F);
			return InteractionResult.SUCCESS;
		}

		if (isFlat(stack)) {
			ItemStack batteries = find(player);
			if (batteries == null) {
				serverPlayer.sendOverlayMessage(Component.translatable("outbreak.radio.flat"));
				return InteractionResult.FAIL;
			}

			batteries.shrink(1);
			stack.setDamageValue(0);
			serverPlayer.sendOverlayMessage(Component.translatable("outbreak.radio.new_batteries"));
		}

		setOn(stack, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5F, 1.4F);
		Broadcasts.tuneIn(serverPlayer);
		return InteractionResult.SUCCESS;
	}

	private static @Nullable ItemStack find(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(OutbreakItems.BATTERIES)) {
				return stack;
			}
		}

		return null;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!isOn(stack) || level.getGameTime() % DRAIN_TICKS != 0) {
			return;
		}

		stack.setDamageValue(stack.getDamageValue() + 1);
		if (isFlat(stack)) {
			setOn(stack, false);
			if (owner instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.translatable("outbreak.radio.died"));
			}
		}
	}

	/** Whether this player hears the broadcasts: a radio on, or sitting in a car with the engine running. */
	public static boolean isListening(ServerPlayer player) {
		if (player.getVehicle() instanceof Vehicle vehicle && vehicle.isRunning() && vehicle.vehicleType().isCar()) {
			return true;
		}

		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(OutbreakItems.PORTABLE_RADIO) && isOn(stack)) {
				return true;
			}
		}

		return false;
	}
}
