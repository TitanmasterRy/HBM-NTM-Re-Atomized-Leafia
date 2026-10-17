package com.leafia.contents.fluids.traits;

import java.util.Arrays;
import java.util.List;

import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

/**
 * Free text on a fluid's tooltip, split on NTM's line-break character ({@code $}).
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_Description.java}. The string is
 * passed to the translator, which returns the key unchanged when it is not a translation key —
 * and upstream uses it both ways, so both are kept working. The two descriptions the addon
 * actually ships are raw English sentences rather than keys.
 *
 * <p>{@code hidden} decides <em>which</em> half of the tooltip it lands in: the always-visible
 * one or the shift-to-see one. It is not a switch that turns the text off.
 *
 * <p>No JSON serialisation, matching upstream — and therefore no no-arg constructor either. It
 * is registered in the trait name map for consistency with the other three, but a fluid JSON
 * naming {@code desc} could not be read back. That mirrors upstream exactly; the base mod does
 * not read fluid traits out of JSON yet, so nothing exercises the difference.
 */
public class FT_Description extends FluidTrait {

	final String description;
	final boolean hidden;

	public FT_Description(String translationKey, boolean hidden) {
		this.description = translationKey;
		this.hidden = hidden;
	}

	@Override
	public void addInfo(List<String> info) {
		if(!hidden)
			info.addAll(Arrays.asList(I18nUtil.resolveKey(description).split("\\$")));
	}

	@Override
	public void addInfoHidden(List<String> info) {
		if(hidden)
			info.addAll(Arrays.asList(I18nUtil.resolveKey(description).split("\\$")));
	}
}
