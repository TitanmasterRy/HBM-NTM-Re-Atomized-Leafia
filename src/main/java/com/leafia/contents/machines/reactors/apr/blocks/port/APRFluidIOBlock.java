package com.leafia.contents.machines.reactors.apr.blocks.port;

import java.util.List;

import com.hbm.blocks.BlockContainerBase;
import com.hbm.blocks.ITooltipProvider;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code apr_port}: a fluid port that stands where a ring wants a support and lets pipes reach the nearest chamber
 * and the core's coolant.
 *
 * <p>Upstream: {@code .../apr/blocks/port/APRFluidIOBlock.java}; the tooltip is upstream's
 * {@code tile.apr_port.desc} behind shift.
 */
public class APRFluidIOBlock extends BlockContainerBase implements ITooltipProvider {

	public APRFluidIOBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
		addStandardInfo("tile.apr_port.desc", tooltip);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new APRFluidIOTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.APR_PORT.get(), (lvl, pos, st, be) -> be.update());
	}
}
