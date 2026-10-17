package com.leafia.contents.machines.reactors.pwr.blocks;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The PWR pressure vessel, {@code lwr_casing}: a radiation-shielding casing block.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/PWRHullBlock.java}, a CE
 * {@code BlockRadResistant}, which is {@link AddonRadResistantBlockBaked} here (that class's doc). It is
 * not a PWR component: the diagnosis walks past it, and reads its blast resistance (165) into the
 * vessel's toughness, and the fuel's neutron trace stops at it because it shields.
 */
public class PWRHullBlock extends AddonRadResistantBlockBaked implements ITooltipProvider {

	public PWRHullBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.lwr_casing.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
