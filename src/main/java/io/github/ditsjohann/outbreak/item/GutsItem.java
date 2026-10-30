package io.github.ditsjohann.outbreak.item;

import io.github.ditsjohann.outbreak.world.Camouflage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Walker guts: hold right-click to smear them over yourself (see {@link Camouflage}). */
public class GutsItem extends Item {
	public GutsItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (user instanceof ServerPlayer player) {
			Camouflage.apply(player);
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
		}

		return stack;
	}
}
