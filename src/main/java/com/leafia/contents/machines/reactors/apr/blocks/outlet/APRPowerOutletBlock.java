package com.leafia.contents.machines.reactors.apr.blocks.outlet;

import java.util.List;

import com.hbm.blocks.BlockContainerBase;
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
 * {@code apr_outlet}: the APR's power port, the block under the core in the middle of the disc's top. A column
 * model, the outlet on its ends and dark plating round its sides (upstream's {@code AddonBlockBaked} with two
 * textures, a {@code cube_column} here).
 *
 * <p>Upstream: {@code .../apr/blocks/outlet/APRPowerOutletBlock.java}.
 */
public class APRPowerOutletBlock extends BlockContainerBase {

	public APRPowerOutletBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new APRPowerOutletTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.APR_OUTLET.get(), (lvl, pos, st, be) -> be.update());
	}
}
