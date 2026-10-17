package com.leafia.contents.machines.reactors.lftr.components.ejector;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementBlock;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code msr_ejector}, the LFTR Fuel Ejector.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/ejector/MSREjectorBlock.java}.
 * Six-way, facing the placer by 1.12.2's {@code EnumFacing.getDirectionFromEntityLiving}
 * ({@link #directionFromEntityLiving}, reproduced: 1.21.1's look-vector rule answers differently when
 * the player is near and above or below), radiation shielding, and the MSR overlay.
 */
public class MSREjectorBlock extends Block implements EntityBlock, ITooltipProvider, IRadResistantBlock, ILookOverlay {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	public MSREjectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, directionFromEntityLiving(context.getClickedPos(), context.getPlayer()));
	}

	/**
	 * 1.12.2's {@code EnumFacing.getDirectionFromEntityLiving}: within two blocks horizontally, up if
	 * the eyes are more than two above the block, down if the block is above the eyes; otherwise the
	 * opposite of the placer's horizontal facing. Placed without a placer, north.
	 */
	public static Direction directionFromEntityLiving(BlockPos pos, @Nullable LivingEntity placer) {
		if(placer == null) return Direction.NORTH;
		if(Math.abs(placer.getX() - (pos.getX() + 0.5)) < 2.0 && Math.abs(placer.getZ() - (pos.getZ() + 0.5)) < 2.0) {
			double eyes = placer.getY() + placer.getEyeHeight();
			if(eyes - pos.getY() > 2.0) return Direction.UP;
			if(pos.getY() - eyes > 0.0) return Direction.DOWN;
		}
		return placer.getDirection().getOpposite();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public int getResistance() {
		return 1;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if(!oldState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onPlace(state, level, pos, oldState, movedByPiston);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if(!newState.is(state.getBlock())) ChunkRadiationManager.markSectionForRebuild(level, pos);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addModular(tooltip);
		addStandardInfo("tile.msr_ejector.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MSREjectorTE(LeafiaBlockEntities.MSR_EJECTOR.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return MSRElementBlock.serverTicker(level, type, LeafiaBlockEntities.MSR_EJECTOR.get());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		List<String> texts = new ArrayList<>();
		MSRTEBase.appendPrintHook(texts, world, pos);
		MSRElementBlock.printScaled(gui, I18nUtil.resolveKey(getDescriptionId()), texts);
	}
}
