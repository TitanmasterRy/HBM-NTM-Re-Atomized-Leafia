package com.leafia.contents.building.light;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.blocks.BlockDummyable;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fluorescent light's core: draws a trickle of HE and, while it has any, drops a column of
 * invisible {@link LightEmitter}s beneath itself so the light reaches the floor.
 *
 * <p>Upstream: {@code com/leafia/contents/building/light/LightTE.java}.
 *
 * <h2>The numbers, and why they read strangely</h2>
 *
 * {@link #CONSUMPTION} is 1 HE and {@link #MAX_POWER} is 2, drained once every
 * {@link #INTERVAL} + 1 ticks. So a light holds about eleven ticks of charge and costs roughly
 * one HE every six — a rounding error next to anything else on the network, which is the
 * point: this is lighting, not a machine. They are upstream's and pinned, because "obviously
 * these should be bigger" is exactly the sort of tidy-up that silently makes every lamp in a
 * base a real load.
 *
 * <h2>Which way the fixture's arms point</h2>
 *
 * Upstream reads its block metadata and branches on {@code meta <= 13}, which in 1.7.10's
 * numbering is the two <em>vertical</em> facings — a fixture on a ceiling or a floor. Those get
 * east/west arms; anything else gets north/south. This port's {@link BlockDummyable} carries
 * the facing as a {@code DIR} property instead of packed metadata (D-028), so the same branch
 * is {@code DIR.getAxis().isVertical()} and the arithmetic disappears.
 */
public class LightTE extends BlockEntityLoadedBase implements IEnergyReceiverMK2 {

	/** Upstream's, and deliberately tiny — see the class note. */
	public static final long CONSUMPTION = 1;
	public static final long INTERVAL = 5;
	public static final long MAX_POWER = CONSUMPTION * 2;

	private long power = 0;
	private int timer = 0;

	public LightTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * Lays down or clears a column of emitters beneath {@code from}.
	 *
	 * <p>Upstream's loop, and its stopping rule matters: it descends only through <em>air</em>
	 * and stops at the first thing that is not, so a light over a table lights the table and not
	 * the floor under it. An emitter counts as air, which is why {@link LightEmitter} extends
	 * {@code AirBlock}.
	 */
	public void updateLightEmitters(BlockPos from, boolean emit) {

		if(level == null) return;

		Block emitter = LightEmitter.get();
		if(emitter == null) return;

		BlockPos pos = from;

		for(int i = 0; i < LightEmitter.LIGHT_RANGE; i++) {

			pos = pos.below();
			BlockState state = level.getBlockState(pos);

			if(!state.isAir()) break;

			if(emit) {
				if(!LightEmitter.isEmitter(state)) level.setBlockAndUpdate(pos, emitter.defaultBlockState());
			} else if(LightEmitter.isEmitter(state)) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Upstream's {@code update}. Server only; a light has nothing to do on the client. */
	public void updateEntity() {

		if(level == null || level.isClientSide) return;

		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof LightBlock)) return;

		Direction dir = state.getValue(BlockDummyable.DIR);
		boolean acrossX = dir.getAxis().isVertical();

		BlockPos arm1 = acrossX ? worldPosition.east() : worldPosition.north();
		BlockPos arm2 = acrossX ? worldPosition.west() : worldPosition.south();

		trySubscribe(level, worldPosition.above(), Direction.UP);
		trySubscribe(level, arm1.above(), Direction.UP);
		trySubscribe(level, arm2.above(), Direction.UP);

		timer++;
		if(timer > INTERVAL) {
			timer = 0;
			power = Math.max(power - CONSUMPTION, 0);
		}

		boolean isLit = state.getValue(LightBlock.LIT);
		boolean shouldBeLit = power > 0;

		if(shouldBeLit != isLit) {

			/*
			 * Upstream swaps the whole block here and re-attaches this block entity by hand.
			 * One property, so the block entity is simply not disturbed - see LightBlock.
			 */
			level.setBlock(worldPosition, state.setValue(LightBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);

			updateLightEmitters(worldPosition, shouldBeLit);
			updateLightEmitters(arm1, shouldBeLit);
			updateLightEmitters(arm2, shouldBeLit);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.power = nbt.getLong("power");
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putLong("power", this.power);
	}

	@Override public long getPower() { return power; }
	@Override public void setPower(long power) { this.power = power; }
	@Override public long getMaxPower() { return MAX_POWER; }

	public static void tick(Level level, BlockPos pos, BlockState state, LightTE light) {
		light.updateEntity();
	}
}
