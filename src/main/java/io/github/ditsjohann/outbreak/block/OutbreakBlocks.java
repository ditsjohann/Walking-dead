package io.github.ditsjohann.outbreak.block;

import java.util.function.Function;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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

	private OutbreakBlocks() {
	}

	public static void register() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Outbreak.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
