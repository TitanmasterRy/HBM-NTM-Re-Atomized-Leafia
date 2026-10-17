package com.leafia.contents.machines.powercores.ams.stabilizer;

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
 * `ams_limiter`, the AMC stabilizer: a wall five wide and six tall, one block thick, whose core is
 * the middle of its foot, with a proxy on each end of the foot.
 *
 * <p>Upstream: {@code .../powercores/ams/stabilizer/AMSStabilizerBlock.java}. {@code {5, 0, 0, 0,
 * 2, 2}} with no offset runs the wall along the east-west axis when the block faces north or
 * south, and the block rotates it. {@link #fillSpace} turns the middle and the two ends of the
 * bottom row into extras, which {@link AMSStabilizerTE#getConPos} plugs into: a block either side
 * of each, and one beyond each end. Upstream's first call is on the core itself, which
 * {@code makeExtra} leaves alone (it only upgrades a plain dummy), so the foot has two extras, not
 * three: the same as the base's own call on its core.
 */
public class AMSStabilizerBlock extends BlockDummyable {

	public static final MapCodec<AMSStabilizerBlock> CODEC = simpleCodec(AMSStabilizerBlock::new);

	public AMSStabilizerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AMSStabilizerBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 5, 0, 0, 0, 2, 2 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new AMSStabilizerTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.AMS_STABILIZER_DUMMY.get(), pos, state).inventory().power().fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.AMS_STABILIZER.get(), AMSStabilizerTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos core = pos.relative(dir, o);
		Direction side = dir.getClockWise();

		makeExtra(world, core);
		makeExtra(world, core.relative(side, 2));
		makeExtra(world, core.relative(side, -2));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide || player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;

		if(level.getBlockEntity(core) instanceof AMSStabilizerTE stabilizer && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(stabilizer, core);

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {

		if(!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.CORE)
			ItemStackUtil.spillItems(level, pos, state.getBlock(), level.getRandom());

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
