package com.leafia.contents.machines.misc.modular_turbine.generator;

import java.util.List;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.MachineUpgradeSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Every generator: it loads the shaft by its {@link #generatorPower()}, and says so in its tooltip.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/generator/MTGeneratorBase.java}.
 * The one {@code IMTMachineUpgradeContributor} in the turbine that is not a no-op: the core asks
 * every component's block for machine offsets when it compiles, and this adds the generator's power
 * to the load coefficient. See {@link IMTGeneratorBlock} for what that does to the shaft.
 */
public abstract class MTGeneratorBase extends ModularTurbineBlockBase implements IMTGeneratorBlock {

	public MTGeneratorBase(Properties properties) {
		super(properties);
	}

	@Override
	public void contributeMachineUpgradeOffsets(MachineUpgradeSummary summary, MTCoreTE core, ModularTurbineComponentTE component) {
		summary.addGeneratorLoadCoefficientOffset(generatorPower());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal(ChatFormatting.GOLD + I18nUtil.resolveKey("info.turbine._tooltip.power", generatorPower())));
	}
}
