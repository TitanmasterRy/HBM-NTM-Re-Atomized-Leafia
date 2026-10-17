package com.leafia.contents.machines.reactors.pwr.blocks.components.computer;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The PWR control port, {@code lwr_signal}: the reactor's computer interface.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/computer/PWRComputerBlock.java}:
 * an {@code AddonBlockBase} that implements {@code IRadResistantBlock} itself (so no "[Radiation
 * Shielding]" line, unlike the hull), marks its section on place and break, and - unlike every other
 * component - does <b>not</b> start a diagnosis when placed: it joins a reactor when something else does.
 * Kept.
 */
public class PWRComputerBlock extends Block implements ITooltipProvider, PWRComponentBlock, IRadResistantBlock, EntityBlock {

	public PWRComputerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public int getResistance() {
		return 1;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.lwr_signal.desc", tooltip);
		BlastResistanceTooltip.add(this, tooltip);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PWRComputerTE(LeafiaBlockEntities.PWR_COMPUTER.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_COMPUTER.get() ? (l, p, s, be) -> PWRAssignableEntity.tick(l, p, s, (PWRAssignableEntity) be) : null;
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return true;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if(!oldState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onPlace(state, level, pos, oldState, movedByPiston);
		// D-3076: every other component starts a diagnosis when placed; the control port did not, so it joined a reactor only when something else was touched
		if(!level.isClientSide && !oldState.is(state.getBlock()))
			PWRTicks.queueFunction(() -> beginDiagnosis(level, pos, pos));
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!newState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
