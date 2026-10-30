package io.github.ditsjohann.outbreak.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.block.OutbreakBlocks;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * When the power goes, the city goes dark: the electric lights Arnis builds (the street lamps, the
 * ceiling lights in buildings, the lights in tunnels) go out. Chunks that are loaded go dark a few at
 * a time; the others as they load. Each chunk only once (it remembers), and only electric lights:
 * lanterns, torches and candles keep burning.
 */
public final class PowerGrid {
	/** Set on a chunk once its lights are out. */
	private static final AttachmentType<Boolean> DARK = AttachmentRegistry.createPersistent(Outbreak.id("dark"), Codec.BOOL);
	private static final int CHUNKS_PER_TICK = 4;

	private static final Set<ChunkPos> LOADED = new HashSet<>();
	private static final Deque<ChunkPos> QUEUE = new ArrayDeque<>();

	private PowerGrid() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level == level.getServer().overworld()) {
				LOADED.add(chunk.getPos());
				if (!Timeline.isPowerOn() && !chunk.hasAttached(DARK)) {
					QUEUE.add(chunk.getPos());
				}
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			if (level == level.getServer().overworld()) {
				LOADED.remove(chunk.getPos());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PowerGrid::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			QUEUE.clear();
		});
	}

	/** The power has just gone: every loaded chunk goes dark (a few per tick). */
	static void goDark(MinecraftServer server) {
		QUEUE.addAll(LOADED);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
			ChunkPos pos = QUEUE.poll();
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk != null && !chunk.hasAttached(DARK)) {
				darken(level, chunk);
			}
		}
	}

	/** Puts out every electric light in a chunk. Returns how many. */
	public static int darken(ServerLevel level, LevelChunk chunk) {
		int count = 0;
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];
			if (section.hasOnlyAir() || !section.maybeHas(PowerGrid::isElectric)) {
				continue;
			}

			int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (isElectric(state)) {
							level.setBlock(pos.set(baseX + x, baseY + y, baseZ + z), off(state), Block.UPDATE_CLIENTS);
							count++;
						}
					}
				}
			}
		}

		chunk.setAttached(DARK, true);
		return count;
	}

	/** The lights that run on the grid. */
	public static boolean isElectric(BlockState state) {
		return state.is(Blocks.GLOWSTONE) || state.is(Blocks.SEA_LANTERN)
			|| state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
	}

	private static BlockState off(BlockState state) {
		if (state.is(Blocks.REDSTONE_LAMP)) {
			return state.setValue(RedstoneLampBlock.LIT, false);
		}

		return OutbreakBlocks.DEAD_LIGHT.defaultBlockState();
	}
}
