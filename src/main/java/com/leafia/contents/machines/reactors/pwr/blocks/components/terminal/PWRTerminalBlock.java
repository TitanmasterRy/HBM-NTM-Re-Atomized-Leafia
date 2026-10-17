package com.leafia.contents.machines.reactors.pwr.blocks.components.terminal;

import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blocks.ITooltipProvider;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The PWR control terminal, {@code lwr_terminal}, and the two older skins upstream keeps on the same class,
 * {@code reactor_hatch} and {@code reactor_hatch_alt}: a casing block that opens the reactor's screen.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/blocks/components/terminal/PWRTerminalBlock.java},
 * a {@code BlockMachineBase} that implements {@code IRadResistantBlock}: it shields, it faces the way it was
 * placed from ({@link #FACING}, which the screen reads to lay the reactor out as seen from the front), and it
 * starts a diagnosis when placed. A right click on a terminal linked to a reactor opens the screen; one
 * that is not linked does nothing (upstream's {@code return false}). All three skins use the translation
 * key {@code lwr_terminal}, as upstream sets on all three.
 *
 * <p>Right-clicking one that is linked grants {@code openpwr} first, as upstream does before it opens the screen
 * ({@code LeafiaAdvancements.OPEN_PWR}, D-3040).
 */
public class PWRTerminalBlock extends AddonRadResistantBlockBaked implements ITooltipProvider, EntityBlock, PWRComponentBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public PWRTerminalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** BlockMachineBase's rotatable placement: the front faces the player who placed it. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
		addStandardInfo("tile.lwr_terminal.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
		MachineTooltip.addUpdate(tooltip, "block.hbm.reactor_hatch");
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		PWRComponentEntity entity = getPWR(level, pos);
		if(entity instanceof PWRTerminalTE terminal && terminal.getCoreByCorePos() != null) {
			if(!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
				com.leafia.init.LeafiaAdvancements.grant(serverPlayer, com.leafia.init.LeafiaAdvancements.OPEN_PWR);
				PWRData core = terminal.getLinkedCoreDiagnosis();
				if(core != null)
					serverPlayer.openMenu(terminal, buf -> com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu.writeOpening(buf, terminal, core));
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean tileEntityShouldCreate(Level world, BlockPos pos) {
		return true;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PWRTerminalTE(LeafiaBlockEntities.PWR_TERMINAL.get(), pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == LeafiaBlockEntities.PWR_TERMINAL.get() ? (l, p, s, be) -> PWRAssignableEntity.tick(l, p, s, (PWRAssignableEntity) be) : null;
	}
}
