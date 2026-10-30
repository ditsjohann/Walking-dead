package io.github.ditsjohann.outbreak.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.vehicle.VehicleItems;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What you did before (PZ's occupations): a few skill levels to start with, sometimes a trait, the
 * things you had on you, and points to spend on traits. The better the job, the fewer the points.
 */
public enum Profession implements StringRepresentable {
	UNEMPLOYED(8, Map.of(), Set.of()),
	NURSE(2, Map.of(Skill.FIRST_AID, 3, Skill.FITNESS, 1), Set.of()),
	POLICE_OFFICER(-4, Map.of(Skill.FIGHTING, 3, Skill.FITNESS, 1, Skill.STRENGTH, 1), Set.of()),
	CARPENTER(2, Map.of(Skill.CARPENTRY, 3, Skill.MAINTENANCE, 1), Set.of()),
	MECHANIC(2, Map.of(Skill.MECHANICS, 3, Skill.MAINTENANCE, 1), Set.of()),
	STUDENT(4, Map.of(Skill.FITNESS, 1), Set.of(Trait.FAST_LEARNER)),
	FARMER(4, Map.of(Skill.STRENGTH, 1, Skill.CARPENTRY, 1), Set.of()),
	FIREFIGHTER(0, Map.of(Skill.FITNESS, 2, Skill.STRENGTH, 2), Set.of()),
	CONSTRUCTION_WORKER(2, Map.of(Skill.STRENGTH, 2, Skill.CARPENTRY, 1), Set.of()),
	CHEF(4, Map.of(Skill.FIGHTING, 1, Skill.MAINTENANCE, 1), Set.of()),
	BURGLAR(-2, Map.of(Skill.SNEAKING, 2, Skill.MECHANICS, 1), Set.of(Trait.BURGLAR));

	public static final Codec<Profession> CODEC = StringRepresentable.fromEnum(Profession::values);

	/** Points to spend on traits. */
	public final int points;
	public final Map<Skill, Integer> skills;
	/** Traits that come with the job, for free. */
	public final Set<Trait> traits;

	Profession(int points, Map<Skill, Integer> skills, Set<Trait> traits) {
		this.points = points;
		this.skills = skills;
		this.traits = traits;
	}

	/** What you had on you when it started (on top of what everyone has). */
	public List<ItemStack> kit() {
		List<ItemStack> kit = new ArrayList<>();
		switch (this) {
			case UNEMPLOYED -> kit.add(new ItemStack(Items.BREAD, 2));
			case NURSE -> {
				kit.add(new ItemStack(OutbreakItems.BANDAGE, 3));
				kit.add(new ItemStack(OutbreakItems.DISINFECTANT));
			}
			case POLICE_OFFICER -> kit.add(new ItemStack(OutbreakItems.BATON));
			case CARPENTER -> {
				kit.add(new ItemStack(OutbreakItems.HAMMER));
				kit.add(new ItemStack(OutbreakItems.NAILS, 16));
				kit.add(new ItemStack(Items.OAK_PLANKS, 8));
				kit.add(worn(Items.IRON_AXE, 0.5F));
			}
			case MECHANIC -> {
				kit.add(new ItemStack(VehicleItems.WRENCH));
				kit.add(new ItemStack(VehicleItems.SCREWDRIVER));
			}
			case STUDENT -> {
				kit.add(new ItemStack(OutbreakItems.SCHOOL_BAG));
				kit.add(new ItemStack(VehicleItems.BICYCLE));
			}
			case FARMER -> {
				kit.add(worn(Items.IRON_AXE, 0.4F));
				kit.add(worn(Items.IRON_HOE, 0.3F));
				kit.add(new ItemStack(Items.WHEAT_SEEDS, 12));
				kit.add(new ItemStack(Items.POTATO, 4));
			}
			case FIREFIGHTER -> kit.add(worn(Items.IRON_AXE, 0.1F));
			case CONSTRUCTION_WORKER -> {
				kit.add(worn(Items.STONE_PICKAXE, 0.4F));
				kit.add(new ItemStack(OutbreakItems.CROWBAR));
			}
			case CHEF -> {
				kit.add(new ItemStack(OutbreakItems.KITCHEN_KNIFE));
				kit.add(new ItemStack(Items.BREAD, 3));
				kit.add(new ItemStack(Items.COOKED_BEEF, 2));
			}
			case BURGLAR -> {
				kit.add(new ItemStack(OutbreakItems.CROWBAR));
				kit.add(new ItemStack(VehicleItems.SCREWDRIVER));
			}
		}

		return kit;
	}

	/** What every new survivor has: a bottle of clean water, a sandwich, two rags. */
	public static List<ItemStack> basics() {
		return List.of(new ItemStack(OutbreakItems.BOILED_WATER), new ItemStack(Items.BREAD), new ItemStack(OutbreakItems.RAG, 2));
	}

	private static ItemStack worn(Item item, float wear) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue((int) (stack.getMaxDamage() * wear));
		return stack;
	}

	/** The item shown next to the profession on the character screen. */
	public Item icon() {
		return switch (this) {
			case UNEMPLOYED -> Items.BREAD;
			case NURSE -> OutbreakItems.BANDAGE;
			case POLICE_OFFICER -> OutbreakItems.BATON;
			case CARPENTER -> OutbreakItems.HAMMER;
			case MECHANIC -> VehicleItems.WRENCH;
			case STUDENT -> OutbreakItems.SCHOOL_BAG;
			case FARMER -> Items.IRON_HOE;
			case FIREFIGHTER -> Items.IRON_AXE;
			case CONSTRUCTION_WORKER -> Items.STONE_PICKAXE;
			case CHEF -> OutbreakItems.KITCHEN_KNIFE;
			case BURGLAR -> OutbreakItems.CROWBAR;
		};
	}

	@Override
	public String getSerializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "outbreak.profession." + this.getSerializedName();
	}

	public String descriptionKey() {
		return this.translationKey() + ".desc";
	}
}
