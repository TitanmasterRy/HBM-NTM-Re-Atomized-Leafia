package com.leafia.contents.machines.reactors.pwr.blocks.components.port;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR fluid port, {@code lwr_port}: coolant and water in, hot coolant and steam out. A casing block.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/port/PWRPortBlock.java},
 * a CE {@code BlockRadResistant} ({@link AddonRadResistantBlockBaked} here).
 */
public class PWRPortBlock extends AddonRadResistantBlockBaked implements ITooltipProvider, EntityBlock, PWRComponentBlock {

	public PWRPortBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_port.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide && !oldState.is(state.getBlock()))
			PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return true;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PWRPortTE(LeafiaBlockEntities.PWR_PORT.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_PORT.get() ? (l, p, s, be) -> PWRAssignableEntity.tick(l, p, s, (PWRAssignableEntity) be) : null;
	}
}
