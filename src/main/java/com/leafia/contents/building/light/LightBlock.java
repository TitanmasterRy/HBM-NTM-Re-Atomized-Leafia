package com.leafia.contents.building.light;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.leafia.init.LeafiaBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;

/**
 * `light_unlit`, the fluorescent light: a three-wide ceiling fixture that lights up while it
 * has power.
 *
 * <p>Upstream: {@code com/leafia/contents/building/light/LightBlock.java}.
 *
 * <h2>Two blocks upstream, one block and a property here</h2>
 *
 * Upstream registers {@code light_unlit} and {@code light_lit} as separate blocks and swaps one
 * for the other whenever the power state changes, because 1.12.2's {@code setLightLevel} was a
 * property of the <em>Block</em>. 1.21.1 takes {@code lightLevel} as a function of the
 * <em>state</em>, so this is one block with a {@link #LIT} boolean.
 *
 * <p>That is not a tidy-up, it removes a bug the swap forces upstream to work around: replacing
 * a block destroys its block entity, so upstream has to re-attach the same instance by hand
 * ({@code this.validate(); world.setTileEntity(pos, this)}). Doing that literally here would
 * lose the fixture's stored power on every toggle, and since the fixture lights up <em>because</em>
 * it has power, it would immediately unlight and flicker forever. A blockstate property keeps
 * the block entity, which is the whole point.
 *
 * <p>The registered id stays {@code light_unlit} — that is the id of the <em>item</em> upstream
 * gives the player, and of its pick-block and its drop, so a player's keys still match.
 * {@code light_lit} is not registered at all, because upstream never puts one in a hand.
 * See D-1017.
 *
 * <p><b>It has no baked model</b>, which is {@link BlockDummyable}'s own choice for every
 * multiblock in this port and upstream's {@code getRenderType() { return -1; }} — the fixture
 * is drawn by {@code LightRender}, a block entity renderer, and that is not ported. It lands
 * invisible, as the base mod's own ammo press does.
 */
public class LightBlock extends BlockDummyable {

	public static final MapCodec<LightBlock> CODEC = simpleCodec(LightBlock::new);

	/** Upstream's second block, as the property it always wanted to be. */
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	public LightBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any()
				.setValue(PART, Part.DUMMY)
				.setValue(DIR, net.minecraft.core.Direction.NORTH)
				.setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends LightBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}

	/** One block either side of the core: `{up, down, north, south, west, east}`. Upstream's. */
	@Override
	public int[] getDimensions() {
		return new int[] { 0, 0, 0, 0, 1, 1 };
	}

	@Override
	public int getOffset() {
		return 0;
	}

	/**
	 * The core gets the fixture's block entity; the two arms get a proxy, so a cable touching an
	 * arm still reaches the core. Upstream branches on {@code meta >= 12}, which is its way of
	 * saying "this is the core".
	 */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {

		if(state.getValue(PART) == Part.CORE)
			return new LightTE(LeafiaBlockEntities.LIGHT.get(), pos, state);

		// Upstream's `new TileEntityProxyCombo(false, true, false)`: power only.
		return new BlockEntityProxyCombo(LeafiaBlockEntities.LIGHT_PROXY.get(), pos, state).power();
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, LeafiaBlockEntities.LIGHT.get(), LightTE::tick);
	}
}
