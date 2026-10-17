package com.leafia.contents.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * The addon's biomes. Upstream: {@code com/leafia/contents/worldgen/AddonBiomes.java}, four
 * {@code AddonBiome} constants and an {@code init()} that registers each with Forge and runs its
 * {@code postInit}.
 *
 * <p>Registration is not Java here: each biome is a datapack JSON (see {@link AddonBiome}), loaded only
 * while its feature switch is on through a {@code leafia:feature} condition, so {@code init()} has
 * nothing to register and only runs the {@code postInit} hooks. What stays is the list of descriptors,
 * which the fog reads.
 *
 * <p><b>Append-only, as upstream's own list was built:</b> {@code barrens}, {@code outer_barrens} and
 * {@code iturnedmcto7dtd} (upstream's {@code Barrens}, {@code Desolation} and {@code Ruins}) are three
 * further worldgen files with their own batch; each adds its constant here when it lands. The digamma
 * crater is the only biome upstream paints rather than generates, and the only one ported (D-3009).
 */
public final class AddonBiomes {

	private AddonBiomes() {}

	public static final List<AddonBiome> ALL_BIOMES = new ArrayList<>();

	/** The digamma crater, or null with {@code features.digammaCrater} off. */
	public static final AddonBiome digamma = LeafiaFeatures.digammaCrater ? new DigammaCrater("digamma_crater") : null;

	public static void init() {
		for(AddonBiome biome : ALL_BIOMES) biome.postInit.run();
	}

	/** The addon biome {@code biome} is, or null for any other. */
	public static AddonBiome of(Holder<Biome> biome) {
		for(AddonBiome addon : ALL_BIOMES) {
			if(addon.is(biome)) return addon;
		}
		return null;
	}
}
