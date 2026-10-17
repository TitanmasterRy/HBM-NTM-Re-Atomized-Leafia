package com.leafia.contents.machines.heat.hpboiler;

import java.util.List;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ITooltipProvider;
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
 * `hp_boiler`: the high-pressure boiler's block — a 3×3×4 machine with a fluid port on each side.
 *
 * <p>Upstream: {@code .../hpboiler/HPBoilerBlock.java}.
 *
 * <h2>The fourth addon base class to dissolve</h2>
 *
 * Upstream extends {@code AddonBlockDummyable}, which D-1028 already read and found to be
 * 1.12.2 registration bookkeeping over {@link BlockDummyable}. Its <em>own</em> base,
 * {@code LCETileEntityMachineBase}, dissolves the same way on the block-entity side: every line
 * of it is a 1.12.2 {@code ItemStackHandler} capability wrapper, and 1.21.1 answers all of it
 * with {@code WorldlyContainer} plus a {@code RegisterCapabilitiesEvent} entry. After
 * {@code AddonBlockBase} (D-1015), {@code AddonBlockBaked} (D-1026) and
 * {@code AddonBlockDummyable} (D-1028), that is four.
 *
 * <h2>The two side ports are proxies, and they need a type of their own</h2>
 *
 * {@link #fillSpace} plants an {@code EXTRA} one block up on each side of the machine's
 * left-right axis, and upstream gives each a {@code TileEntityProxyCombo(false, false, true)} —
 * fluid only, no inventory, no power. 1.21.1 needs a registered {@link BlockEntityType} for
 * that, and it cannot be the base mod's {@code MULTIBLOCK_DUMMY}: a type's validity is a fixed
 * list of blocks, and this block is not on the base mod's list and must never be added to it
 * (rule 11). So the addon registers {@link LeafiaBlockEntities#HP_BOILER_DUMMY} over
 * {@link BlockEntityProxyCombo}'s own constructor, exactly as {@code Slice3} does for the base
 * mod's twenty-nine.
 */
public class HPBoilerBlock extends BlockDummyable implements ITooltipProvider {

	public static final MapCodec<HPBoilerBlock> CODEC = simpleCodec(HPBoilerBlock::new);

	public HPBoilerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends HPBoilerBlock> codec() {
		return CODEC;
	}

	/** Upstream's {@code {3, 0, 1, 1, 1, 1}}: three up, nothing down, one out on all four sides. */
	@Override
	public int[] getDimensions() {
		return new int[] { 3, 0, 1, 1, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 1;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return switch(state.getValue(PART)) {
			case CORE -> new HPBoilerTE(LeafiaBlockEntities.HP_BOILER.get(), pos, state);
			// Upstream's TileEntityProxyCombo(false, false, true): fluid only.
			case EXTRA -> new BlockEntityProxyCombo(LeafiaBlockEntities.HP_BOILER_DUMMY.get(), pos, state).fluid();
			case DUMMY -> null;
		};
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if(state.getValue(PART) != Part.CORE) return null;
		return createTickerHelper(type, LeafiaBlockEntities.HP_BOILER.get(), HPBoilerTE::tick);
	}

	/**
	 * The multiblock, plus a port one block up on each side of the left-right axis.
	 *
	 * <p>Upstream re-derives the core's position from the placement position and the offset
	 * before planting them, which is why the two {@code makeExtra} calls are relative to
	 * {@code pos.relative(dir, o)} rather than to {@code pos}.
	 */
	@Override
	protected void fillSpace(Level world, BlockPos pos, Direction dir, int o) {

		super.fillSpace(world, pos, dir, o);

		BlockPos core = pos.relative(dir, o);
		Direction rot = dir.getClockWise();

		this.makeExtra(world, core.above().relative(rot));
		this.makeExtra(world, core.above().relative(rot.getOpposite()));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {

		if(level.isClientSide) return InteractionResult.SUCCESS;

		BlockPos core = findCore(level, pos);
		if(core == null) return InteractionResult.FAIL;

		if(level.getBlockEntity(core) instanceof HPBoilerTE boiler && player instanceof ServerPlayer serverPlayer)
			serverPlayer.openMenu(boiler, core);

		return InteractionResult.SUCCESS;
	}

	/** Drawn entirely by {@link HPBoilerRender}; upstream's block has no model of its own. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	/**
	 * Upstream's {@code addStandardInfo(tooltip)}, with upstream's own key.
	 *
	 * <p>The key is {@code tile.hp_boiler.desc} rather than this block's 1.21.1 description id:
	 * that is what upstream's {@code en_us.lang} calls it and what its six lines are written
	 * under, so pointing at a renamed key would have shipped the tooltip blank. No
	 * {@code MachineTooltip} badge — upstream's {@code addInformation} adds none, and the boiler
	 * is not a generator.
	 */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		addStandardInfo("tile.hp_boiler.desc", tooltip);
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
