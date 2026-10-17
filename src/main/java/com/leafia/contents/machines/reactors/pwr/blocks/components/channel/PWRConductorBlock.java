package com.leafia.contents.machines.reactors.pwr.blocks.components.channel;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.dev.machine.MachineTooltip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR integrated boiler, {@code reactor_conductor} (named "lwr_conductor" upstream): a coolant
 * channel that also boils water with the hot coolant. Counts as a channel, and each one adds 25,600 mB
 * of water and 12,800 mB of steam to the boiler tanks. Only works with plain coolant.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/channel/PWRConductorBlock.java}.
 * Upstream registers it as {@code reactor_conductor} and translates it as {@code lwr_conductor}; this
 * port keeps the registry name (what a world and {@code ReactorTiers} key on) and gives the registry
 * name upstream's English name.
 */
public class PWRConductorBlock extends Block implements ITooltipProvider, PWRComponentBlock {

	public PWRConductorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide && !oldState.is(state.getBlock()))
			PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public boolean shouldRenderOnGUI() {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		MachineTooltip.addBoiler(tooltip);
		addStandardInfo("tile.lwr_conductor.desc", tooltip);
		MachineTooltip.addUpdate(tooltip, "block.hbm.reactor_conductor", "block.hbm.reactor_computer");
		BlastResistanceTooltip.add(this, tooltip);
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return false;
	}
}
