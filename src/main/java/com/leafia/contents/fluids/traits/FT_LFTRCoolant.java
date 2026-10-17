package com.leafia.contents.fluids.traits;

import java.util.List;

import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.ChatFormatting;

/**
 * Marks a fluid usable as coolant in the addon's LFTR, with its neutron flux multiplier.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_LFTRCoolant.java}. A multiplier of
 * 2 or more also makes the coolant a moderator — that threshold is upstream's and is the only
 * number in the class.
 *
 * <p><b>Nothing carries this trait yet.</b> Upstream attaches it to exactly one fluid,
 * {@code AddonFluids.FLUORIDE} (FLiBe), which is an addon fluid and unported — see
 * {@code LeafiaFluidTraits}. Its five readers (the mixing vat, the coolant heat exchanger, the
 * MSR plug and the FF duct) are unported too, so the class lands registered and inert, the way
 * L3's marker interfaces did.
 *
 * <p>The tooltip formats the multiplier with Java's float {@code toString} rather than a
 * pattern, so a multiplier of 1 reads "+0.0%". That is upstream's, kept.
 */
public class FT_LFTRCoolant extends FluidTrait {

	public float multiplier = 1;
	public boolean moderated = false;

	public FT_LFTRCoolant(float multiplier) {
		this.multiplier = multiplier;
		this.moderated = multiplier >= 2;
	}

	@Override
	public void addInfoHidden(List<String> info) {
		float perc = (multiplier - 1) * 100;
		String percs;
		if(perc >= 0)
			percs = "+" + perc + "%";
		else
			percs = perc + "%";
		info.add(ChatFormatting.YELLOW + "[" + I18nUtil.resolveKey("trait.leafia.lftr") + "] "
				+ ChatFormatting.AQUA + I18nUtil.resolveKey("trait.leafia.lftr.multiplier", percs));
	}
}
