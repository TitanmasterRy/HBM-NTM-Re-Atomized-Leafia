package com.leafia.contents.machines.misc.modular_turbine.generator;

/**
 * A turbine component that turns shaft power into HE.
 *
 * <p>Upstream: {@code .../modular_turbine/generator/IMTGeneratorBlock.java}, two lines.
 *
 * <p>What the number does, read from {@code MTGeneratorBase} and {@code MTCoreTE} once both were
 * ported (T5) — T1's version of this comment guessed, and guessed wrong: {@link #generatorPower()}
 * is <b>added to the shaft's generator load coefficient</b>, the slope of the electrical
 * counter-torque against shaft speed. It is not a cap. Without a generator that slope is upstream's
 * {@code 0.000001}, so the shaft spins almost unloaded and the HE it makes truncates to nothing;
 * with the 3x3 generator it is {@code 5.000001}, and the power is that counter-torque times the
 * shaft's angular speed times {@code POWER_SCALE}. More generators load the shaft harder: more power
 * per rps, and a lower speed for the same steam.
 *
 * <p>Upstream ships exactly one implementor, {@code MTGenerator3x3}.
 */
public interface IMTGeneratorBlock {

	/** Added to the shaft's generator load coefficient. 5 for the 3x3 generator. */
	double generatorPower();
}
