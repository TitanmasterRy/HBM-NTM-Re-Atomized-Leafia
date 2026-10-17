package com.leafia.config;

import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods.RodDefaults;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-rod life, emission and reactivity: upstream's {@code AddonConfig.FuelLives}, which reads a file
 * called {@code generic_fuels2} with one {@code <rod>-life}, {@code <rod>-emission} and
 * {@code <rod>-reactivity} line per rod whose life is above zero (D-3001).
 *
 * <p>Upstream's file ships every line commented out ("The configs will not apply by default! Remove ?
 * on start of each lines to apply") and an uncommented line overrides the rod. The same thing here is
 * a TOML spec whose defaults <em>are</em> the rods' own numbers, so a pack edits only what it wants
 * and the file shows every rod's value.
 *
 * <p>COMMON, like {@link LeafiaConfig}: it only changes numbers a rod reads while it burns, never
 * whether a rod exists, so it may load after registration. It is registered only when
 * {@code features.rods} is on, and the table is the generated {@link LeafiaRods#CONFIG}.
 */
public final class LeafiaRodConfig {

	private LeafiaRodConfig() {}

	public static final ModConfigSpec SPEC;
	private static final ModConfigSpec.DoubleValue[][] VALUES;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		builder.comment(
				"Per-rod overrides: how much heat a rod can generate before it is spent (life), how many",
				"neutrons it emits, and how readily it takes them (reactivity). Every default is the rod's",
				"own value, upstream's.")
				.push("rods");
		RodDefaults[] rows = LeafiaRods.CONFIG;
		VALUES = new ModConfigSpec.DoubleValue[rows.length][];
		for(int i = 0; i < rows.length; i++) {
			RodDefaults row = rows[i];
			builder.push(row.key());
			VALUES[i] = new ModConfigSpec.DoubleValue[] {
					builder.defineInRange("life", row.life(), 0.0, Double.MAX_VALUE),
					builder.defineInRange("emission", row.emission(), 0.0, Double.MAX_VALUE),
					builder.defineInRange("reactivity", row.reactivity(), 0.0, Double.MAX_VALUE),
			};
			builder.pop();
		}
		builder.pop();
		SPEC = builder.build();
	}

	/** Copies the loaded file onto the rods. Rods that are not registered are skipped. */
	public static void refresh() {
		RodDefaults[] rows = LeafiaRods.CONFIG;
		for(int i = 0; i < rows.length; i++) {
			LeafiaRodItem rod = LeafiaRodItem.fromResourceMap.get("leafia_rod_" + rows[i].key());
			if(rod == null) continue;
			rod.life = VALUES[i][0].get();
			rod.emission = VALUES[i][1].get();
			rod.reactivity = VALUES[i][2].get();
		}
	}
}
