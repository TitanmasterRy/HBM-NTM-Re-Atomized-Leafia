package com.leafia.contents.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * One of the addon's biomes. Upstream: {@code com/leafia/contents/worldgen/AddonBiome.java}, an abstract
 * {@code Biome} subclass.
 *
 * <h2>What a biome is here</h2>
 *
 * 1.21.1 has no Java biome hierarchy: a {@link Biome} is a codec-backed record read from datapack JSON
 * ({@code data/leafia/worldgen/biome/<name>.json}) and never subclassed (the base mod's D-623). So the
 * numbers upstream's subclasses pass to {@code BiomeProperties} and return from their overrides - sky,
 * water, grass and foliage colour, rain, temperature, spawn lists - are in that JSON, and this class
 * keeps only what JSON cannot say: <b>the fog</b>. A 1.12.2 biome had no fog distance at all and its fog
 * colour was the sky's; upstream's {@code getFogColor} / {@code getFogDensity} / {@code getFogStart} /
 * {@code getFogEnd} were read by its own client listener, and are read here by {@link AddonBiomeFog}.
 *
 * <p>An instance is a <b>descriptor keyed by the biome's registry key</b>. Constructing one registers it in
 * {@link AddonBiomes#ALL_BIOMES}, as upstream's constructor did.
 *
 * <h2>Not carried across</h2>
 * <ul>
 *   <li>{@code overrideGenLayers}: 1.12.2's {@code GenLayer} stack does not exist in 1.21.1 (a biome is
 *       placed by a {@code BiomeSource}'s climate parameters). It is overridden only by {@code Barrens},
 *       {@code Ruins} and {@code Desolation}, whose own ports decide how they are placed.</li>
 *   <li>{@code postInit} is kept as a hook, but upstream's one use - {@code BiomeDictionary.addTypes} - is
 *       a biome tag here ({@code data/c/tags/worldgen/biome/*.json}).</li>
 * </ul>
 */
public abstract class AddonBiome {

	public final ResourceKey<Biome> key;
	public Runnable postInit = () -> {};

	protected AddonBiome(String resource) {
		this.key = ResourceKey.create(Registries.BIOME, LeafiaRef.rl(resource));
		AddonBiomes.ALL_BIOMES.add(this);
	}

	/** Whether {@code biome} is this one. */
	public boolean is(Holder<Biome> biome) {
		return biome.is(this.key);
	}

	/**
	 * Fog colour in upstream's <b>inverse-ARGB</b>: an alpha of {@code 00} is opaque and {@code FF}
	 * invisible, so the default, {@code 0xFF_000000}, changes nothing.
	 */
	public int getFogColor() { return 0xFF_000000; }
	public float getFogDensity(float original) { return original; }
	public float getFogStart(float original) { return original; }
	public float getFogEnd(float original) { return original; }

	/** Upstream's sampling: a 7 x 7 grid five blocks apart, centred on the viewer, at the viewer's height. */
	public static final int RATIO_HALF_SPAN = 3;
	public static final int RATIO_STEP = 5;

	/**
	 * How much of the area around {@code pos} each addon biome covers: upstream's
	 * {@code LeafiaClientListener.getBiomeRatios}, lifted out of that listener with the fog it feeds. Of the
	 * 49 samples, each that lands in an addon biome counts for that biome, and with
	 * {@link DigammaCrater#schizoMode} on, every other sample counts for the digamma crater. The value is the
	 * count over 49. The map is in first-seen order, which is the order the fog blends in.
	 */
	public static Map<AddonBiome, Float> getBiomeRatios(Level level, BlockPos pos) {
		int samples = 0;
		Map<AddonBiome, Integer> counts = new LinkedHashMap<>();
		for(int ox = -RATIO_HALF_SPAN; ox <= RATIO_HALF_SPAN; ox++) {
			for(int oz = -RATIO_HALF_SPAN; oz <= RATIO_HALF_SPAN; oz++) {
				Holder<Biome> biome = level.getBiome(pos.offset(ox * RATIO_STEP, 0, oz * RATIO_STEP));
				AddonBiome addon = AddonBiomes.of(biome);
				if(addon != null)
					counts.merge(addon, 1, Integer::sum);
				else if(DigammaCrater.schizoMode && AddonBiomes.digamma != null)
					counts.merge(AddonBiomes.digamma, 1, Integer::sum);
				samples++;
			}
		}
		Map<AddonBiome, Float> ratios = new LinkedHashMap<>();
		for(Entry<AddonBiome, Integer> entry : counts.entrySet())
			ratios.put(entry.getKey(), entry.getValue().floatValue() / samples);
		return ratios;
	}

	/**
	 * Upstream's fog-distance blend: for each biome, move the value a fraction {@code ratio} of the way to
	 * what that biome makes of the <em>original</em>. {@code which} is 0 for density, 1 for start, 2 for end.
	 */
	public static float blendFog(Map<AddonBiome, Float> ratios, float original, int which) {
		float value = original;
		for(Entry<AddonBiome, Float> entry : ratios.entrySet()) {
			AddonBiome biome = entry.getKey();
			float target = switch(which) {
				case 0 -> biome.getFogDensity(original);
				case 1 -> biome.getFogStart(original);
				default -> biome.getFogEnd(original);
			};
			value += (target - value) * entry.getValue();
		}
		return value;
	}

	/**
	 * Upstream's fog-colour blend, in doubles as upstream's {@code Vec3d}: each biome pulls the colour
	 * toward its own by {@code (1 - alpha/255) x ratio}, where alpha is the top byte of
	 * {@link #getFogColor()}. Returns {@code {r, g, b}}, unclamped.
	 */
	public static double[] blendFogColor(Map<AddonBiome, Float> ratios, double r, double g, double b) {
		double[] col = {r, g, b};
		for(Entry<AddonBiome, Float> entry : ratios.entrySet()) {
			int code = entry.getKey().getFogColor();
			double alpha = (1 - (code >> 24 & 0xFF) / 255d) * entry.getValue();
			col[0] += ((code >>> 16 & 0xFF) / 255d - col[0]) * alpha;
			col[1] += ((code >>> 8 & 0xFF) / 255d - col[1]) * alpha;
			col[2] += ((code & 0xFF) / 255d - col[2]) * alpha;
		}
		return col;
	}
}
