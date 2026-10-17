package com.leafia.contents.machines.powercores.ams.emitter;

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
 * `ams_emitter`, the AMC emitter: a 5x5 tower six blocks tall with its core in the middle of the
 * bottom layer, and a proxy on the five blocks of the top layer's cross.
 *
 * <p>Upstream: {@code .../powercores/ams/emitter/AMSEmitterBlock.java}. {@code {5, 0, 2, 2, 2, 2}}
 * with no offset; {@link #fillSpace} upgrades the centre of the top layer and its four neighbours
 * to {@code EXTRA}, which is where {@link AMSEmitterTE#getConPos} connects.
 */
public class AMSEmitterBlock extends BlockDummyable {

	public static final MapCodec<AMSEmitterBlock> CODEC = simpleCodec(AMSEmitterBlock::new);

	public AMSEmitterBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends AMSEmitterBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MachineTooltip.addMultiblock(tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 5, 0, 2, 2, 2, 2 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new AMSEmitterTE(pos, state);
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.AMS_EMITTER_DUMMY.get(), pos, state).inventory().power().fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.AMS_EMITTER.get(), AMSEmitterTE::tick);
	}

	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos top = pos.relative(dir, o).above(5);

		makeExtra(world, top);
		makeExtra(world, top.east());
		makeExtra(world, top.west());
		makeExtra(world, top.south());
		makeExtra(world, top.north());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide || player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;

		if(level.getBlockEntity(core) instanceof AMSEmitterTE emitter && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(emitter, core);

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {

		if(!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.CORE)
			ItemStackUtil.spillItems(level, pos, state.getBlock(), level.getRandom());

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
