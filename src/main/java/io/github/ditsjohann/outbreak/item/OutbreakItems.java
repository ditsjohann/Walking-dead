package io.github.ditsjohann.outbreak.item;

import java.util.function.Function;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.survivor.Treatment;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;

public final class OutbreakItems {
	public static final Item RAG = register("rag", p -> new MedicalItem(Treatment.Kind.RAG, p), medical(1.5F).stacksTo(16));
	public static final Item BANDAGE = register("bandage", p -> new MedicalItem(Treatment.Kind.BANDAGE, p), medical(2.0F).stacksTo(8));
	public static final Item DISINFECTANT = register("disinfectant", p -> new MedicalItem(Treatment.Kind.DISINFECTANT, p), medical(1.0F).stacksTo(4));
	public static final Item BOILED_WATER = register("boiled_water", Item::new, new Item.Properties()
		.stacksTo(1)
		.component(DataComponents.CONSUMABLE, Consumable.builder()
			.consumeSeconds(1.6F)
			.animation(ItemUseAnimation.DRINK)
			.sound(SoundEvents.GENERIC_DRINK)
			.hasConsumeParticles(false)
			.build())
		.usingConvertsTo(Items.GLASS_BOTTLE));

	private OutbreakItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(RAG);
			output.accept(BANDAGE);
			output.accept(DISINFECTANT);
			output.accept(BOILED_WATER);
		});
	}

	/** Medical items use the consumable component only for the hold-to-use timing and animation. */
	private static Item.Properties medical(float seconds) {
		return new Item.Properties().component(DataComponents.CONSUMABLE, Consumable.builder()
			.consumeSeconds(seconds)
			.animation(ItemUseAnimation.BRUSH)
			.sound(SoundEvents.ARMOR_EQUIP_LEATHER)
			.hasConsumeParticles(false)
			.build());
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Outbreak.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}
}
