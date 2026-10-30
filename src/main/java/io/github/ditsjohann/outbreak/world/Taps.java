package io.github.ditsjohann.outbreak.world;

import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.survivor.Hydration;
import io.github.ditsjohann.outbreak.survivor.Survivors;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The sinks and taps in Arnis's houses (empty cauldrons): while the water still runs, fill a bottle
 * (clean tap water) or a bucket, or drink straight from the tap. Once it's off they run dry, and it's
 * rain in the cauldrons and boiled canal water from then on.
 */
public final class Taps {
	private static final float GULP = 0.15F;

	private Taps() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || !level.getBlockState(hit.getBlockPos()).is(Blocks.CAULDRON)) {
				return InteractionResult.PASS;
			}

			ItemStack held = player.getMainHandItem();
			boolean bottle = held.is(Items.GLASS_BOTTLE);
			boolean bucket = held.is(Items.BUCKET);
			if (!bottle && !bucket && !held.isEmpty()) {
				return InteractionResult.PASS;
			}

			if (!Timeline.isWaterOn()) {
				serverPlayer.sendOverlayMessage(Component.translatable("outbreak.tap.dry"));
				level.playSound(null, hit.getBlockPos(), SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.6F, 0.8F);
				return InteractionResult.SUCCESS;
			}

			if (bottle || bucket) {
				ItemStack filled = new ItemStack(bottle ? OutbreakItems.TAP_WATER : Items.WATER_BUCKET);
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
				level.playSound(null, hit.getBlockPos(), bottle ? SoundEvents.BOTTLE_FILL : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			} else if (Survivors.get(serverPlayer).thirst > 0.01F) {
				Hydration.drink(serverPlayer, GULP);
				level.playSound(null, hit.getBlockPos(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
			} else {
				serverPlayer.sendOverlayMessage(Component.translatable("outbreak.tap.not_thirsty"));
			}

			return InteractionResult.SUCCESS;
		});
	}
}
