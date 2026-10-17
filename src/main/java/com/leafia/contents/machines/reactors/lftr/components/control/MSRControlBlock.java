package com.leafia.contents.machines.reactors.lftr.components.control;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ILookOverlay;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ITooltipProvider;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.sound.NtmSounds;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.block.IToolable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * {@code msr_control}, the LFTR Control Rod.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/lftr/components/control/MSRControlBlock.java}.
 * Six-way, facing <em>away</em> from the placer (the opposite of the ejector's rule, so the rod points
 * into the reactor and its tubes trail back toward the player), radiation shielding, and a screwdriver
 * that moves the target a quarter metre: sneaking inserts up to the travel the tubes allow, otherwise it
 * withdraws to 0. A click that changes nothing passes; one that does plays the lock sound. The rod
 * itself is {@link MSRControlTE}.
 */
public class MSRControlBlock extends Block implements EntityBlock, ILookOverlay, IRadResistantBlock, ITooltipProvider, IToolable {

	public static final DirectionProperty FACING = DirectionalBlock.FACING;

	/** A quarter metre per screwdriver click. */
	public static final double STEP = 0.25;

	public MSRControlBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING,
				MSREjectorBlock.directionFromEntityLiving(context.getClickedPos(), context.getPlayer()).getOpposite());
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
		addStandardInfo("tile.msr_control.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal(ChatFormatting.DARK_GREEN + "[" + I18nUtil.resolveKey("trait.radshield") + "]"));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MSRControlTE(LeafiaBlockEntities.MSR_CONTROL.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide || type != LeafiaBlockEntities.MSR_CONTROL.get() ? null
				: (l, p, s, be) -> MSRControlTE.tick(l, p, s, (MSRControlTE) be);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void printHook(GuiGraphics gui, Level world, BlockPos pos) {
		if(world.getBlockEntity(pos) instanceof MSRControlTE control) {
			List<String> texts = new ArrayList<>();
			texts.add(I18nUtil.resolveKey("tile.msr_control.insertion", String.format("%01.2f", control.insertion)));
			ILookOverlay.printGeneric(gui, I18nUtil.resolveKey(getDescriptionId()), 0xFF55FF, 0x3F153F, texts);
		}
	}

	@Override
	public boolean onScrew(Level world, Player player, BlockPos pos, Direction side, Vec3 hit, ToolType tool) {
		if(!(world.getBlockEntity(pos) instanceof MSRControlTE control))
			return true;
		if(tool.equals(ToolType.SCREWDRIVER)) {
			if(!world.isClientSide) {
				double ogPos = control.targetInsertion;
				if(player.isShiftKeyDown())
					control.targetInsertion = Math.min(control.targetInsertion + STEP, control.length);
				else
					control.targetInsertion = Math.max(control.targetInsertion - STEP, 0);
				if(control.targetInsertion == ogPos)
					return false;
				control.setChanged();
				world.playSound(null, pos, NtmSounds.BLOCK_LOCK_OPEN.get(), SoundSource.BLOCKS, 0.5F, 1F);
			}
			return true;
		}
		return false;
	}
}
