package io.github.ditsjohann.outbreak.vehicle;

import java.util.function.Function;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.item.OutbreakItems;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;

/** Vehicle parts, tools for working on them, and fuel. */
public final class VehicleItems {
	/** Condition is durability: 100 = new, 0 = flat. */
	public static final Item TIRE = register("tire", Item::new, new Item.Properties().durability(100));
	public static final Item BIKE_TIRE = register("bike_tire", Item::new, new Item.Properties().durability(100));
	public static final Item CAR_BATTERY = register("car_battery", CarBatteryItem::new,
		new Item.Properties().stacksTo(1).component(VehicleComponents.CHARGE, 0.0F));
	public static final Item ENGINE_PARTS = register("engine_parts", Item::new, new Item.Properties().stacksTo(16));
	public static final Item JERRYCAN = register("jerrycan", JerrycanItem::new,
		new Item.Properties().stacksTo(1).component(VehicleComponents.FUEL, 0.0F));
	public static final Item HOSE = register("hose", Item::new, new Item.Properties().durability(40));
	public static final Item WRENCH = register("wrench", Item::new, OutbreakItems.weapon(300, 3.0F, -2.8F, 10));
	public static final Item SCREWDRIVER = register("screwdriver", Item::new, OutbreakItems.weapon(150, 2.0F, -1.8F, 7));
	public static final Item JACK = register("jack", Item::new, new Item.Properties().stacksTo(1));
	public static final Item CAR_KEY = register("car_key", CarKeyItem::new, new Item.Properties().stacksTo(1));
	public static final Item BICYCLE = register("bicycle", BicycleItem::new,
		new Item.Properties().stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));

	private VehicleItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(WRENCH);
			output.accept(SCREWDRIVER);
			output.accept(JACK);
			output.accept(HOSE);
			output.accept(JERRYCAN);
			ItemStack full = new ItemStack(JERRYCAN);
			JerrycanItem.setFuel(full, JerrycanItem.CAPACITY);
			output.accept(full);
			output.accept(TIRE);
			output.accept(BIKE_TIRE);
			ItemStack battery = new ItemStack(CAR_BATTERY);
			CarBatteryItem.setCharge(battery, 1.0F);
			output.accept(battery);
			output.accept(ENGINE_PARTS);
			output.accept(BICYCLE);
			for (VehicleType type : VehicleType.values()) {
				if (type != VehicleType.BICYCLE) {
					output.accept(Vehicles.spawnItem(type));
				}
			}
		});
	}

	static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Outbreak.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
