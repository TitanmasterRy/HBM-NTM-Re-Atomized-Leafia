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
 * The PWR coolant channel, {@code lwr_channel}: it carries the coolant past the fuel, and each one adds
 * 3,200 mB to the coolant and hot coolant tanks. A channel within three blocks of a fuel column makes the
 * column shed heat ({@code channelScale}), and one in a column's neutron path moderates it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/channel/PWRChannelBlock.java}.
 * {@code onBlockAdded}'s queued diagnosis is {@link #onPlace} on {@link PWRTicks#queueFunction}.
 */
public class PWRChannelBlock extends Block implements ITooltipProvider, PWRComponentBlock {

	public PWRChannelBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		MachineTooltip.addBoiler(tooltip);
		addStandardInfo("tile.lwr_channel.desc", tooltip);
		BlastResistanceTooltip.add(this, tooltip);
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
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return false;
	}
}
