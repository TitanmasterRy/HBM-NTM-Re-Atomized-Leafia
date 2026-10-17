package com.leafia.contents.gear.utility.fuzzy;

import com.hbm.inventory.fluid.FluidType;

/**
 * A block entity that can tell the fuzzy identifier what it puts out.
 *
 * <p>Upstream: {@code com/leafia/contents/gear/utility/fuzzy/IFuzzyCompatible.java}, seven lines
 * and one method. {@link FuzzyIdentifierItem#onItemUseFirst} checks for it before it falls back
 * to reading a machine's sending tanks, so a machine that implements this gets to name the one
 * fluid a player actually wants rather than making them cycle through every tank it has.
 *
 * <h2>The four base-mod machines are read through a table, not by implementing this</h2>
 *
 * Upstream's four implementors are all <b>mixins into {@code com.hbm}</b> -
 * {@code MixinTileEntityTurbineBase}, {@code MixinTileEntityCondenser},
 * {@code MixinTileEntityHeatBoiler} and {@code MixinTileEntityHeatBoilerIndustrial} - each of
 * which is one method bolted onto a base-mod machine. D-1001 forbids that, and D-1053 left the
 * interface without an implementor for that reason. The answer is {@link FuzzyAdapters}: a table
 * from a machine's class to a function reading its output fluid, which needs nothing from the
 * base mod (D-3025). The item asks {@link FuzzyAdapters#outputOf}, which asks this interface first,
 * so an addon machine may still implement it directly.
 */
public interface IFuzzyCompatible {

	/** The fluid this machine wants an identifier set to. */
	FluidType getOutputType();
}
