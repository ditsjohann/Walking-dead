package io.github.ditsjohann.outbreak.item;

import java.util.function.Function;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.block.OutbreakBlocks;
import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.survivor.Treatment;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;

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

	// Melee weapons. Attack damage shown in game is 1 + the baseline; a headshot does 4x that.
	public static final Item KITCHEN_KNIFE = register("kitchen_knife", Item::new, weapon(120, 3.0F, -1.6F, 7));
	public static final Item BASEBALL_BAT = register("baseball_bat", Item::new, weapon(200, 5.0F, -3.0F, 12));
	public static final Item CROWBAR = register("crowbar", Item::new, weapon(600, 5.0F, -2.8F, 10));
	public static final Item MACHETE = register("machete", Item::new, weapon(250, 6.0F, -2.6F, 10));
	public static final Item HAMMER = register("hammer", HammerItem::new, weapon(300, 3.0F, -2.8F, 10));
	public static final Item BATON = register("baton", Item::new, weapon(400, 4.0F, -2.4F, 9));
	public static final Item NAILS = register("nails", Item::new, new Item.Properties());

	// Bags: rows of storage, and how much lighter their contents are while worn.
	public static final Item SCHOOL_BAG = register("school_bag", p -> new BagItem(ContainerKind.SCHOOL_BAG, 0.3F, p), bag());
	public static final Item HIKING_BAG = register("hiking_bag", p -> new BagItem(ContainerKind.HIKING_BAG, 0.6F, p), bag());

	public static final Item FRIDGE = block("fridge", OutbreakBlocks.FRIDGE);
	public static final Item KITCHEN_CUPBOARD = block("kitchen_cupboard", OutbreakBlocks.KITCHEN_CUPBOARD);
	public static final Item TOOLBOX = block("toolbox", OutbreakBlocks.TOOLBOX);
	public static final Item METAL_CABINET = block("metal_cabinet", OutbreakBlocks.METAL_CABINET);

	private OutbreakItems() {
	}

	private static Item block(String name, Block block) {
		return register(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(KITCHEN_KNIFE);
			output.accept(BASEBALL_BAT);
			output.accept(CROWBAR);
			output.accept(MACHETE);
			output.accept(BATON);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(HAMMER);
			output.accept(NAILS);
			output.accept(SCHOOL_BAG);
			output.accept(HIKING_BAG);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(FRIDGE);
			output.accept(KITCHEN_CUPBOARD);
			output.accept(TOOLBOX);
			output.accept(METAL_CABINET);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(RAG);
			output.accept(BANDAGE);
			output.accept(DISINFECTANT);
			output.accept(BOILED_WATER);
		});
	}

	private static Item.Properties bag() {
		return new Item.Properties()
			.stacksTo(1)
			.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build());
	}

	/**
	 * A melee weapon that wears out. Material only sets durability; damage and speed are per weapon,
	 * and so is how long a swing takes to play out (vanilla's is 6 ticks; a bat is a big swing).
	 */
	public static Item.Properties weapon(int durability, float attackDamage, float attackSpeed, int swingTicks) {
		ToolMaterial material = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, durability, 6.0F, 0.0F, 1, ItemTags.IRON_TOOL_MATERIALS);
		return new Item.Properties().sword(material, attackDamage, attackSpeed)
			.component(DataComponents.ATTACK_ANIMATION, new SwingAnimation(SwingAnimationType.WHACK, swingTicks));
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
