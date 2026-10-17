package com.leafia.init;

import com.leafia.config.LeafiaFeatures;

import net.minecraft.world.level.GameRules;

/**
 * The addon's gamerules.
 *
 * <p>One so far: {@code dialReasimCount}, how many streams a RealerSim channel throws per tick.
 * It is an NTM-CE RBMK dial — {@code RBMKDials.RBMKKeys.KEY_REASIM_COUNT("dialReasimCount", 6)},
 * read from the vendored 1.5.1.8 jar — that neither {@code hbm-src} nor this port's base mod has,
 * and the RealerSim channel is its only reader upstream (CE's own ReaSim channel reads it too,
 * but the base mod ports {@code hbm-src}'s ReaSim, which throws a fixed eight). So the addon
 * registers it, under CE's name, so {@code /gamerule dialReasimCount 12} works as it did.
 *
 * <p>An {@code IntegerValue}, which {@code GameRules.register} takes without the access
 * transformer the base mod needs for its double dials. Two differences from upstream's
 * string gamerule, both at the edges: an unparseable value in {@code level.dat} reads 0 rather
 * than the default (vanilla's {@code IntegerValue}), which the 1..24 clamp then makes 1; and the
 * rule appears on the world-creation screen, which the base mod's double dials do not.
 *
 * <p>Registered from the mod constructor, before any save builds its {@code GameRules}, and only
 * when RealerSim is on — a disabled feature registers nothing (D-1002). The key is null then, and
 * its only reader is the RealerSim block entity, which does not exist then either.
 */
public final class LeafiaGameRules {

	private LeafiaGameRules() {}

	/** NTM-CE's default. */
	public static final int REASIM_COUNT_DEFAULT = 6;

	public static GameRules.Key<GameRules.IntegerValue> REASIM_COUNT;

	public static void register() {
		if(LeafiaFeatures.realerSim && REASIM_COUNT == null)
			REASIM_COUNT = GameRules.register("dialReasimCount", GameRules.Category.MISC,
					GameRules.IntegerValue.create(REASIM_COUNT_DEFAULT));
	}
}
