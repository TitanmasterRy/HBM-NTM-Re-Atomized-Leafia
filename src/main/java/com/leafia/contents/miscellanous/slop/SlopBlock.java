package com.leafia.contents.miscellanous.slop;

import java.util.List;

import com.hbm.blocks.ITooltipProvider;
import com.hbm.blocks.machine.BlockMachineBase;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
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
 * `slop_reactor`: the reactor's controller, a rotatable block whose front is the texture with the
 * red button and whose back is where the 5x5x5 goes.
 *
 * <p>Upstream: {@code .../slop/SlopBlock.java}, on {@code BlockMachineBase} - which the base mod
 * has, so unlike the addon's other machine blocks nothing dissolves here. What upstream's
 * {@code onBlockActivated} adds is the only difference from the base's: the menu opens <em>only</em>
 * when the cube is built, and a click on a half-built one makes the wrong blocks glow
 * ({@link SlopTE#checkFormed}). It does not look at sneaking, which the base's does.
 *
 * <p>Upstream's {@code setHardness}/{@code setResistance} are absent, so the block has none:
 * it breaks at a touch, the same as the casing and the glass. Which is how a player takes a
 * reactor apart.
 */
public class SlopBlock extends BlockMachineBase implements ITooltipProvider {

	public static final MapCodec<SlopBlock> CODEC = simpleCodec(SlopBlock::new);

	public SlopBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends SlopBlock> codec() {
		return CODEC;
	}

	@Override
	protected boolean isRotatable() {
		return true;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SlopTE(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(level.isClientSide) return null;
		return createTickerHelper(type, LeafiaBlockEntities.SLOP.get(), SlopTE::tick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return InteractionResult.SUCCESS;

		if(level.getBlockEntity(pos) instanceof SlopTE slop && slop.checkFormed(true)
				&& player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(slop, pos);

		return InteractionResult.SUCCESS;
	}

	/** Upstream's {@code addStandardInfo(tooltip)}, under its own {@code tile.slop_reactor.desc}. */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.slop_reactor.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
