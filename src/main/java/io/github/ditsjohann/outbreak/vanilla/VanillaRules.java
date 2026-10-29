package io.github.ditsjohann.outbreak.vanilla;

import java.util.List;
import java.util.regex.Pattern;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;

/**
 * Everything vanilla that doesn't belong in the outbreak: monsters, villagers, XP, and the recipes
 * for tools, weapons, armour, redstone, magic and the other dimensions.
 */
public final class VanillaRules {
	/** Non-monster entities that are also removed (villagers, golems, XP orbs...). All vanilla monsters are removed anyway. */
	public static final TagKey<EntityType<?>> BANNED_ENTITIES = TagKey.create(Registries.ENTITY_TYPE, Outbreak.id("banned"));
	/** Blocks that can't be broken by hand at all, on top of the ones that already need a tool for drops (stone, metal...). */
	public static final TagKey<Block> NEEDS_TOOL = TagKey.create(Registries.BLOCK, Outbreak.id("needs_tool"));

	/** The only natural monster spawn: walkers, in loose groups of 2 to 6 that form a herd. */
	public static final WeightedList<MobSpawnSettings.SpawnerData> WALKER_SPAWNS =
		WeightedList.of(new MobSpawnSettings.SpawnerData(OutbreakEntities.WALKER, UniformInt.of(2, 6)));

	/**
	 * Vanilla recipe ids that are removed. Building blocks (wood, stone, bricks, glass, doors, stairs...),
	 * furniture, food and boats stay; tools and weapons are found, not crafted.
	 */
	private static final List<Pattern> REMOVED_RECIPES = List.of(
		// Tools, weapons, armour and their upgrades
		Pattern.compile("(wooden|stone|copper|iron|golden|diamond|netherite)_(pickaxe|axe|shovel|hoe|sword|spear|helmet|chestplate|leggings|boots|horse_armor|nautilus_armor).*"),
		Pattern.compile("(leather|chainmail|turtle)_.*(helmet|chestplate|leggings|boots|horse_armor).*"),
		Pattern.compile("(bow|crossbow|arrow|spectral_arrow|tipped_arrow|shield|mace|trident|wolf_armor|.*_harness)"),
		Pattern.compile(".*_smithing(_template.*|_trim)?"),
		// Redstone devices and rail transport
		Pattern.compile("(redstone.*|repeater|comparator|observer|piston|sticky_piston|dispenser|dropper|hopper|crafter|daylight_detector|target|note_block|jukebox|tnt|.*minecart|.*rail)"),
		// Magic, the Nether and the End
		Pattern.compile("(enchanting_table|anvil|grindstone|smithing_table|brewing_stand|beacon|conduit|lodestone|respawn_anchor|ender_chest|ender_eye|end_crystal|.*shulker_box)"),
		Pattern.compile("(flint_and_steel|fire_charge|blaze_powder|magma_cream|fermented_spider_eye|glistering_melon_slice|golden_carrot|golden_apple)"),
		// Navigation gadgets (the outbreak gets its own maps later)
		Pattern.compile("(compass|recovery_compass|clock|spyglass|map|cartography_table)"),
		// Ore processing: there's no mining progression
		Pattern.compile(".*_from_(smelting|blasting)_.*(ore|raw_.*|debris)"),
		Pattern.compile("raw_.*_block|.*_from_raw_.*_block|netherite_.*|diamond.*|emerald.*|lapis_.*")
	);

	private VanillaRules() {
	}

	public static void register() {
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loaded) -> !isBanned(entity));
	}

	public static boolean isBanned(Entity entity) {
		EntityType<?> type = entity.getType();
		if (type == OutbreakEntities.WALKER) {
			return false;
		}

		return type.getCategory() == MobCategory.MONSTER || entity.typeHolder().is(BANNED_ENTITIES);
	}

	public static boolean isRecipeRemoved(ResourceKey<Recipe<?>> key) {
		if (!key.identifier().getNamespace().equals("minecraft")) {
			return false;
		}

		String path = key.identifier().getPath();
		for (Pattern pattern : REMOVED_RECIPES) {
			if (pattern.matcher(path).matches()) {
				return true;
			}
		}

		return false;
	}
}
