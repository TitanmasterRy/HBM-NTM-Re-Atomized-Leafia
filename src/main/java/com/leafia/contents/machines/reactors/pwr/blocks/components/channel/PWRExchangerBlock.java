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
 * The PWR heat exchanger, {@code lwr_exchanger}: within three blocks of a fuel column it raises the rate
 * the column sheds heat at ({@code exchangerScale = 1 + sum / height}).
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/channel/PWRExchangerBlock.java}.
 */
public class PWRExchangerBlock extends Block implements ITooltipProvider, PWRComponentBlock {

	public PWRExchangerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide && !oldState.is(state.getBlock()))
			PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_exchanger.desc", tooltip);
		BlastResistanceTooltip.add(this, tooltip);
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
