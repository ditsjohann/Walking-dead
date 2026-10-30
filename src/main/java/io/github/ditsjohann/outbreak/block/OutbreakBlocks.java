package io.github.ditsjohann.outbreak.block;

import java.util.function.Function;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.gore.BloodBlock;
import io.github.ditsjohann.outbreak.inventory.ContainerKind;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class OutbreakBlocks {
	/** No item form: barricades are nailed up with a hammer, planks and nails. */
	public static final Block BARRICADE = register("barricade", BarricadeBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.WOOD)
		.instrument(NoteBlockInstrument.BASS)
		.strength(2.0F, 3.0F)
		.sound(SoundType.WOOD)
		.noOcclusion()
		.pushReaction(PushReaction.IMMOVEABLE)
		.ignitedByLava());

	/** Blood on the ground (no item form either): left by hits, the dead, the dismembered and the bleeding. */
	/** What the city's electric lights look like once the power is gone (see PowerGrid). */
	public static final Block DEAD_LIGHT = register("dead_light", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_GRAY)
		.strength(0.3F)
		.sound(SoundType.GLASS));
	public static final Block BLOOD = register("blood", BloodBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_RED)
		.noCollision()
		.instabreak()
		.sound(SoundType.HONEY_BLOCK)
		.replaceable()
		.noLootTable()
		.noOcclusion()
		.pushReaction(PushReaction.POPPED));

	// Containers with their own screens. They fill with loot on first open, like chests.
	public static final Block FRIDGE = register("fridge", p -> new StorageBlock(ContainerKind.FRIDGE, p), metal(MapColor.SNOW));
	public static final Block KITCHEN_CUPBOARD = register("kitchen_cupboard", p -> new StorageBlock(ContainerKind.KITCHEN_CUPBOARD, p), wood());
	public static final Block TOOLBOX = register("toolbox", p -> new StorageBlock(ContainerKind.TOOLBOX, p), metal(MapColor.COLOR_RED).noOcclusion());
	public static final Block METAL_CABINET = register("metal_cabinet", p -> new StorageBlock(ContainerKind.METAL_CABINET, p), metal(MapColor.METAL));

	public static final BlockEntityType<StorageBlockEntity> STORAGE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Outbreak.id("storage"),
		FabricBlockEntityTypeBuilder.create(StorageBlockEntity::new, FRIDGE, KITCHEN_CUPBOARD, TOOLBOX, METAL_CABINET).build());

	private OutbreakBlocks() {
	}

	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties wood() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).ignitedByLava();
	}

	public static void register() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Outbreak.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
