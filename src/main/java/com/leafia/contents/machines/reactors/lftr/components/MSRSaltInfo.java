package com.leafia.contents.machines.reactors.lftr.components;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What an LFTR salt's NBT adds to a Forge fluid's tooltip and look overlay.
 *
 * <p>Not an upstream file: it is the {@code FT_LFTRCoolant} half of upstream's
 * {@code LeafiaClientUtil.addFluidInfo}, hung on the bridge's {@link LeafiaFFClient#INFO_HOOKS} so the
 * bridge does not name a reactor (D-3003 left the hook for this batch). Two contributions, as upstream:
 * the salt's {@code heat} raises the shown temperature (cast to whole degrees, upstream's
 * {@code (int)(temp + heat)}, which is the same for a heat that cannot go below zero), and after the
 * traits comes the mixture - each isotope's amount per bucket to three places, its heat function and
 * its decay rate where it has one. Upstream's {@code wand_v} tag dump is a developer tool and is not
 * here. Registered from {@code LeafiaClient} when the MSR blocks are.
 */
public class MSRSaltInfo implements LeafiaFFClient.InfoHook {

	@Override
	public int heat(FluidStack stack, FluidType ntm) {
		if(!ntm.hasTrait(FT_LFTRCoolant.class)) return 0;
		return (int) MSRTEBase.nbtProtocol(stack).getDouble("heat");
	}

	@Override
	public void addInfo(FluidStack stack, FluidType ntm, List<String> texts, String prefix) {
		if(!ntm.hasTrait(FT_LFTRCoolant.class)) return;
		CompoundTag tag = MSRTEBase.nbtProtocol(stack);
		Map<String, Double> mixture = MSRTEBase.readMixture(tag);
		if(mixture.isEmpty()) return;
		texts.add(prefix + ChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("tile.msr.mixture"));
		for(Entry<String, Double> entry : mixture.entrySet()) {
			texts.add(prefix + " " + ChatFormatting.LIGHT_PURPLE + I18nUtil.resolveKey("tile.msr.fuel." + entry.getKey()) + " "
					+ String.format("%01.3f", entry.getValue()) + "/B ");
			try {
				MSRFuel fuel = MSRFuel.valueOf(entry.getKey());
				if(!fuel.funcString.equals("0"))
					texts.add(prefix + ChatFormatting.LIGHT_PURPLE + "  Heat Function: " + fuel.funcString);
				if(fuel.decayRate > 0)
					texts.add(prefix + ChatFormatting.DARK_PURPLE + "  Decay Function: ΔT×" + fuel.decayRate);
			} catch(IllegalArgumentException ignored) {}
		}
	}
}
