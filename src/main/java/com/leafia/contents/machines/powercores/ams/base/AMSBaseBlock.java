package com.leafia.contents.machines.powercores.ams.base;

import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.util.ItemStackUtil;
import com.leafia.dev.machine.MachineTooltip;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `ams_base`, the AMC base: a 3x3 pedestal two blocks tall whose core stands in the middle, with
 * a proxy on each of the four blocks beside it.
 *
 * <p>Upstream: {@code .../powercores/ams/base/AMSBaseBlock.java}, on {@code AddonBlockDummyable}
 * (registration bookkeeping over {@link BlockDummyable}, D-1028). {@code {1, 0, 1, 1, 1, 1}} with
 * an offset of one is the 3x3 whose core is the middle of the bottom layer and which is one block
 * tall above it; {@link #fillSpace} then upgrades the four blocks beside the core to
 * {@code EXTRA}, each with the proxy upstream builds as {@code TileEntityProxyCombo(true, true,
 * true)}: inventory, power and fluid. That is how a cable or a pipe beside the pedestal reaches
 * the machine - the machine itself talks to the blocks two out, which the extras let it find.
 *
 * <p>Upstream's {@code fillSpace} subtracts the facing from the placement position to get the
 * core, which is {@code pos.relative(dir, o)} with this port's {@code o = -1}.
 *
 * <p>The tooltip is upstream's: multiblock, core, generator, and two lines of apology (D-3002).
 */
public class AMSBaseBlock extends BlockDummyable {

	public static final MapCodec<AMSBaseBlock> CODEC = simpleCodec(AMSBaseBlock::new);

	public AMSBaseBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AMSBaseBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		MachineTooltip.addCore(tooltip);
		MachineTooltip.addGenerator(tooltip);
		tooltip.add(Component.literal("Yeah I DO know spectrometers don't generate power."));
		tooltip.add(Component.literal("Just shut up."));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 1, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new AMSBaseTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.AMS_BASE_DUMMY.get(), pos, state).inventory().power().fluid();
			case DUMMY -> null;
		};
	}

	/** Ticks on both sides: the server runs the machine and the client works out its own warning icon. */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.AMS_BASE.get(), AMSBaseTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos core = pos.relative(dir, o);

		makeExtra(world, core);
		makeExtra(world, core.east());
		makeExtra(world, core.west());
		makeExtra(world, core.south());
		makeExtra(world, core.north());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide || player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;

		if(level.getBlockEntity(core) instanceof AMSBaseTE base && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(base, core);

		return InteractionResult.SUCCESS;
	}

	/** Breaking the core scatters every item inside, as every machine's does upstream. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {

		if(!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.CORE)
			ItemStackUtil.spillItems(level, pos, state.getBlock(), level.getRandom());

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
