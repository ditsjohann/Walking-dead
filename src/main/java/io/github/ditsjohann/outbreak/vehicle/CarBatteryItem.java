package io.github.ditsjohann.outbreak.vehicle;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A car battery. Starting an engine uses some charge; a running engine charges it back up. */
public class CarBatteryItem extends Item {
	public CarBatteryItem(Properties properties) {
		super(properties);
	}

	public static float charge(ItemStack stack) {
		return stack.getOrDefault(VehicleComponents.CHARGE, 0.0F);
	}

	public static void setCharge(ItemStack stack, float charge) {
		stack.set(VehicleComponents.CHARGE, Mth.clamp(charge, 0.0F, 1.0F));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charge(stack));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return Mth.hsvToRgb(charge(stack) / 3.0F, 1.0F, 1.0F);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.outbreak.car_battery.charge", Math.round(charge(stack) * 100)));
	}
}
