package com.leafia.contents.fluids.traits;

import java.util.List;

import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.ChatFormatting;

/**
 * A fluid a magnetic field can hold: the addon's antimatter pipe carries these and only these
 * while it has power.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/traits/FT_Magnetic.java}. Its one reader is
 * {@code AmatNet}, the antimatter pipe's network, which is unported — so today this is a
 * tooltip and nothing else. Four base fluids carry it: oxygen, antimatter, amat-schrabidium
 * and stellar flux (see {@code LeafiaFluidTraits}).
 */
public class FT_Magnetic extends FluidTrait {

	@Override
	public void addInfoHidden(List<String> info) {
		info.add(ChatFormatting.GOLD + "[" + I18nUtil.resolveKey("trait.leafia.magnetic") + "]");
	}
}
