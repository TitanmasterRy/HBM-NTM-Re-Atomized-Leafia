package com.leafia.contents.machines.reactors.pwr.blocks;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;
import com.leafia.dev.machine.MachineTooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The PWR neutron reflector, {@code lwr_reflector}: a casing block that also sends neutrons back.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/PWRReflectorBlock.java}, a CE
 * {@code BlockRadResistant} with the metal step sound. A fuel column whose neutron trace meets one counts
 * its own flux again over the reflector's rows ({@code PWRElementTE.linetraceNeutrons}).
 */
public class PWRReflectorBlock extends AddonRadResistantBlockBaked implements ITooltipProvider {

	public PWRReflectorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_reflector.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
