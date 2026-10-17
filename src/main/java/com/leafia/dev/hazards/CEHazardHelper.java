package com.leafia.dev.hazards;

import com.hbm.config.GeneralConfig;
import com.hbm.items.ModItems;
import com.hbm.util.BobMathUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What the addon's radiation types take from NTM-CE's hazard helper and base hazard type, read
 * out of {@code reference/ntm-cursed/libs/NTM-CE-1.12.2-1.5.1.8.jar} with Vineflower (D-1078).
 *
 * <p>Upstream's six radiation types each open with the same seven lines - is the target holding
 * the reacher, scale the level by the stack, and bend it - and each calls
 * {@code HazardHelper.isHoldingReacher}, which exists in CE and not in this port's base mod. This
 * is that helper plus the shared bend, so the six classes read the same as upstream's without
 * six copies of it.
 *
 * <p><b>The reacher is a different test here than in the base mod's own radiation type.</b>
 * {@code hbm-src}'s {@code HazardTypeRadiation} asks whether the player carries a reacher
 * anywhere in the inventory; CE's helper asks whether it is in a <em>hand</em>
 * ({@code Library.checkForHeld}: main hand or off hand - this port's own {@code checkForHeld}
 * is the 1.7.10 one and asks the main hand only, so the test is written out here), and answers false whenever
 * {@code enable528} is on - which makes the 528 branch below dead code upstream, because it is
 * only taken when the helper says true. Both are carried across as they were, and neither is
 * "fixed": the addon was balanced against CE's.
 */
public final class CEHazardHelper {

	private CEHazardHelper() {}

	/**
	 * Ticks between applications of a hazard, as CE's {@code RadiationConfig.hazardRate} and the
	 * {@code hazardRate} constant on its {@code IHazardType}. <b>One here, five there.</b>
	 *
	 * <p>CE applies hazards every fifth tick and multiplies its own types by this to make up the
	 * difference; this port's {@code HazardTickHandler} applies them every tick, so the factor is
	 * one and a type that multiplies by it (only {@code Radon} does) gives the same rate. The
	 * addon's other types do <em>not</em> multiply, so against CE 1.5.1.8's cadence they would
	 * land at a fifth of the rate they have here. Whether the author meant that is not
	 * answerable from the jar: upstream builds against CE 2.6.1.0, which is not vendored. See
	 * D-1078.
	 */
	public static final int HAZARD_RATE = 1;

	/**
	 * CE's default {@code RadiationConfig.hazardRate}: how many ticks apart its applications are.
	 *
	 * <p>The owner's answer to D-1078's open question (D-1093 #2, applied in D-1094) is that an
	 * addon type's constant means what it meant on CE, so it is <b>divided by this</b>: Gamma's
	 * {@code 0.05} per application, once in five ticks on CE, is {@code 0.01} here, in every tick.
	 * Applying every fifth tick instead would give the same mean dose but bursts, and a result
	 * that depends on the world clock; the division is smooth, deterministic, and the same on
	 * every caller. {@code Radon} is the exception that proves it: CE multiplies it by the rate and
	 * so it is already the same here at a rate of one. See {@link #perTick} and {@link #rollOdds}.
	 */
	public static final int CE_HAZARD_RATE = 5;

	/** A per-application dose or chance as CE would have had it per tick: divided by {@link #CE_HAZARD_RATE}. */
	public static double perTick(double perApplication) {
		return perApplication / CE_HAZARD_RATE;
	}

	/**
	 * The bound of a {@code nextInt} roll made every tick that comes up with the same chance, per
	 * five ticks, as CE's roll did once per application: five times the odds' denominator.
	 */
	public static int rollOdds(int perApplication) {
		return perApplication * CE_HAZARD_RATE;
	}

	/** CE's {@code HazardHelper.isHoldingReacher}. */
	public static boolean isHoldingReacher(LivingEntity target) {
		return target instanceof Player player && !GeneralConfig.enable528
				&& (player.getMainHandItem().is(ModItems.reacher.get()) || player.getOffhandItem().is(ModItems.reacher.get()));
	}

	/**
	 * The shared preamble: multiply by the stack, then bend the level when the reacher is held.
	 *
	 * <pre>
	 * enable528 and reacher: level x 20 / 49      (unreachable: the helper is false under 528)
	 * reacher:               sqrt(level x 20) / 20   with sqrt = x -> sqrt(x + 1/(x+2)^2) - 1/(x+2)
	 * </pre>
	 *
	 * CE's {@code BobMathUtil.sqrt} is this port's {@code BobMathUtil.squirt}: the same
	 * expression, renamed by the base mod.
	 */
	public static double stackAndReacher(LivingEntity target, double level, int count) {
		boolean reacher = isHoldingReacher(target);
		level *= count;
		if(GeneralConfig.enable528 && reacher) {
			level = level * 20 / 49D;
		} else if(reacher) {
			level = BobMathUtil.squirt(level * 20) / 20;
		}
		return level;
	}
}
