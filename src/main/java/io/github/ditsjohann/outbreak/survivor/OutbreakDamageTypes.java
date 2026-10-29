package io.github.ditsjohann.outbreak.survivor;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;

/** Damage types are data-driven: see data/outbreak/damage_type. */
public final class OutbreakDamageTypes {
	public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
	public static final ResourceKey<DamageType> INFECTION = key("infection");
	public static final ResourceKey<DamageType> DEHYDRATION = key("dehydration");

	private OutbreakDamageTypes() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, Outbreak.id(name));
	}

	public static void hurt(ServerPlayer player, ResourceKey<DamageType> type, float amount) {
		player.hurtServer(player.level(), player.damageSources().source(type), amount);
	}
}
