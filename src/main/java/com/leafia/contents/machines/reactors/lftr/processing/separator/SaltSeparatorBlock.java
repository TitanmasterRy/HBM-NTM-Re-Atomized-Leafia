package com.leafia.contents.machines.reactors.lftr.processing.separator;

import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * `salt_separator`, the Salt Fuel Separation Plant: a 3x3x6 multiblock with its core in the bottom middle.
 *
 * <p>Upstream: {@code .../lftr/processing/separator/SaltSeparatorBlock.java}, on the base mod's
 * {@code BlockDummyable} with the addon's registration bookkeeping (D-1028).
 *
 * <h2>The shape</h2>
 *
 * {@code {5, 0, 1, 1, 1, 1}} with an offset of one: five up, one out on every side, so the 3x3 footprint's
 * core is its middle cell at the bottom. Upstream then upgrades the eight cells around the core to proxies,
 * written as {@code x - dir.offsetX +/- 1}: with the placement offset of -1 that is the core's own cell, so
 * these are all the machine's ground-level flank cells. They sit beside the eight ports the block entity
 * declares (two at each corner, one at each edge's middle), so a duct or a hopper beside the machine always
 * meets a proxy.
 *
 *  * <h2>The proxies</h2>
 *
 * Upstream's {@code TileEntityProxyCombo(true, true, true)}: inventory, power and fluid. The type is the
 * addon's own ({@code LeafiaBlockEntities.SALT_SEPARATOR_DUMMY}) for the reason the HP boiler's is; it
 * forwards a Forge fluid handler to the core as well (the core's capability is registered for it in
 * {@code LeafiaCapabilities}).
 *
 * <h2>The tooltip</h2>
 *
 * Two lines of the author's, and the second is as rude as upstream wrote it. Kept verbatim: it is
 * content, not a constant, and a softer line is one string.
 */
public class SaltSeparatorBlock extends BlockDummyable {

	public static final MapCodec<SaltSeparatorBlock> CODEC = simpleCodec(SaltSeparatorBlock::new);

	public SaltSeparatorBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends SaltSeparatorBlock> codec() {
		return CODEC;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("Yes, yes, I know this is just a sloppy recolor."));
		tooltip.add(Component.literal("I can't fucking make the texture for a new model ok?"));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	@Override
	public int[] getDimensions() {
		return new int[] { 5, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new SaltSeparatorTE(LeafiaBlockEntities.SALT_SEPARATOR.get(), pos, state);
			// Upstream's TileEntityProxyCombo(true, true, true): inventory, power and fluid.
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.SALT_SEPARATOR_DUMMY.get(), pos, state).inventory().power().fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.SALT_SEPARATOR.get(), SaltSeparatorTE::tick);
	}

	/** The multiblock, then upstream's eight "extras": the ring of ground-level cells around the core. */
	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		// Upstream's x - dir.offsetX is the core, because the placement offset o is -1.
		BlockPos core = pos.relative(dir, o);

		this.makeExtra(world, core.offset(1, 0, 1));
		this.makeExtra(world, core.offset(1, 0, -1));
		this.makeExtra(world, core.offset(-1, 0, 1));
		this.makeExtra(world, core.offset(-1, 0, -1));
		this.makeExtra(world, core.offset(-1, 0, 0));
		this.makeExtra(world, core.offset(1, 0, 0));
		this.makeExtra(world, core.offset(0, 0, -1));
		this.makeExtra(world, core.offset(0, 0, 1));
	}

	/** Upstream's {@code standardOpenBehavior}: a click from any block of it opens the screen, unless sneaking. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide || player.isShiftKeyDown()) return InteractionResult.sidedSuccess(level.isClientSide);

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.PASS;

		if(level.getBlockEntity(core) instanceof SaltSeparatorTE separator && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(separator, core);

		return InteractionResult.SUCCESS;
	}

	/** Drawn entirely by {@link SaltSeparatorRender}. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}
}
