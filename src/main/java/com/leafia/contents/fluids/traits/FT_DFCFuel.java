package com.leafia.contents.fluids.traits;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

/**
 * How well a fluid burns in the addon's Dark Fusion Core: a multiplier on its power output.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_DFCFuel.java}. The modifier is
 * stored raw and the tooltip shows it as a delta from 1.0, so deuterium's 1.2 reads
 * "+20.00%" and hydrogen's 1.0 reads "0.00%" — hydrogen is the baseline, not a penalty.
 *
 * <h2>The percentage formatting, which is the one thing here that is not a straight port</h2>
 *
 * Upstream calls {@code com.hbm.lib.Library.getPercentage(double)}. That method exists in
 * NTM-CE 1.12.2 and <b>does not exist in 1.7.10 {@code hbm-src}</b>, which is this port's
 * specification (standing rule 1) — so there is nothing in {@code com.hbm} to call and, per
 * D-1001, the addon may not add one. It is a display helper rather than a balance value, so it
 * is reproduced here instead of handed off. See D-1012.
 *
 * <p>{@link #getPercentage} is CE's implementation read out of its bytecode rather than
 * guessed at: {@code numberformat.format(roundFloat(d * 100, 2))} with
 * {@code numberformat = new DecimalFormat("0.00")} and
 * {@code roundFloat(d, i) = Math.round(d * 10^i) / (float) 10^i}. The double rounding is
 * upstream's and is kept — it changes nothing for the 22 values the addon actually ships, all
 * of which land on whole percents, but inventing a tidier formatter would silently move any
 * future one.
 *
 * <p>Like CE's, the formatter is built with the default locale, so a player running a
 * comma-decimal locale sees "20,00". That is upstream's behaviour and not worth diverging
 * from; it is the reason {@code LeafiaFluidTraitTests} normalises the separator before
 * comparing rather than asserting a literal.
 */
public class FT_DFCFuel extends FluidTrait {

	float modifier;

	public FT_DFCFuel() { }

	public FT_DFCFuel(float modifier) {
		this.modifier = modifier;
	}

	public float getModifier() {
		return modifier;
	}

	@Override
	public void addInfo(List<String> info) {
		info.add("§5[" + I18nUtil.resolveKey("trait._hazardfluid.dfcFuel") + "]");
		float dfcEff = (modifier - 1F);
		info.add(I18nUtil.resolveKey("trait._hazardfluid.dfcFuel.desc",
				dfcEff >= 0 ? "+" + getPercentage(dfcEff) : getPercentage(dfcEff)));
	}

	/**
	 * NTM-CE's {@code Library.getPercentage}, reproduced. A new {@link DecimalFormat} per call
	 * rather than CE's shared static field, because {@code DecimalFormat} is not thread-safe
	 * and a tooltip is not hot enough for that to matter; the output is identical.
	 */
	public static String getPercentage(double value) {
		return new DecimalFormat("0.00").format(roundFloat(value * 100D, 2));
	}

	/** NTM-CE's {@code Library.roundFloat(double, int)}, for the two-digit round above. */
	private static float roundFloat(double value, int digits) {
		int pow = POWERS_OF_TEN[digits];
		return Math.round(value * pow) / (float) pow;
	}

	private static final int[] POWERS_OF_TEN = { 1, 10, 100, 1000, 10000, 100000, 1000000,
			10000000, 100000000, 1000000000 };

	@Override
	public void serializeJSON(JsonWriter writer) throws IOException {
		writer.name("dfc_modifier").value(modifier);
	}

	@Override
	public void deserializeJSON(JsonObject obj) {
		modifier = obj.get("dfc_modifier").getAsFloat();
	}
}
