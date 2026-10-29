package io.github.ditsjohann.outbreak.vehicle;

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Holds up to 20 litres of petrol. Right-click a vehicle to pour it in; fill it by siphoning with a hose. */
public class JerrycanItem extends Item {
	public static final float CAPACITY = 20.0F;
	private static final int FUEL_COLOR = 0xD9A030;

	public JerrycanItem(Properties properties) {
		super(properties);
	}

	public static float fuel(ItemStack stack) {
		return stack.getOrDefault(VehicleComponents.FUEL, 0.0F);
	}

	public static void setFuel(ItemStack stack, float litres) {
		stack.set(VehicleComponents.FUEL, Mth.clamp(litres, 0.0F, CAPACITY));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * fuel(stack) / CAPACITY);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return FUEL_COLOR;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.outbreak.jerrycan.fuel",
			String.format(Locale.ROOT, "%.1f", fuel(stack)), String.format(Locale.ROOT, "%.0f", CAPACITY)));
	}
}
