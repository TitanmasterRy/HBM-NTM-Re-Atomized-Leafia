package com.leafia.contents.fluids.ff;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A NeoForge {@link Fluid} that only ever lives in a tank: no block, no bucket, no flowing half.
 *
 * <p>This is what NTM-CE's {@code Fluids.initForgeFluidCompat} gave every {@code FluidType} that
 * had no Forge fluid already: a {@code FluidNTM} carrying a name, two textures, a colour and a
 * temperature, and nothing that could be poured. 1.12.2's {@code Fluid} was a description, and a
 * block was something a mod opted into with {@code BlockFluidBase}; a 1.21.1 {@code Fluid} is a
 * world object by construction, so "description only" has to be spelled out - which is everything
 * this class does. It is {@code EmptyFluid} with one difference that matters: {@link #isEmpty()}
 * is false, because a {@code FluidStack} of an empty fluid is itself empty, and every stack of
 * this one would silently read as nothing.
 *
 * <p>Non-placeable follows from {@link #createLegacyBlock} answering air: NeoForge's
 * {@code FluidType.canBePlacedInLevel} is exactly "the block for this state is not air", so
 * {@code FluidUtil.tryPlaceFluid} refuses it and {@link LeafiaFF#pour} reports false rather than
 * deleting fluid. {@link #getBucket()} is air for the same reason: a vanilla bucket's capability
 * fills only with a fluid that names its bucket, so an {@code ff/} fluid cannot be bucketed,
 * which is upstream's too ({@code addBucketForFluid} was only called for the five {@code AddonFF}
 * world fluids).
 */
public class FFTankFluid extends Fluid {

	private final Supplier<FluidType> type;

	public FFTankFluid(Supplier<FluidType> type) {
		this.type = type;
	}

	@Override
	public FluidType getFluidType() {
		return type.get();
	}

	@Override
	public Item getBucket() {
		return Items.AIR;
	}

	@Override
	protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
		return true;
	}

	@Override
	protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
		return Vec3.ZERO;
	}

	@Override
	public int getTickDelay(LevelReader level) {
		return 0;
	}

	@Override
	protected float getExplosionResistance() {
		return 0.0F;
	}

	@Override
	public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
		return 0.0F;
	}

	@Override
	public float getOwnHeight(FluidState state) {
		return 0.0F;
	}

	@Override
	protected BlockState createLegacyBlock(FluidState state) {
		return Blocks.AIR.defaultBlockState();
	}

	/** A tank's contents are always "source": there is no flowing half to be. */
	@Override
	public boolean isSource(FluidState state) {
		return true;
	}

	@Override
	public int getAmount(FluidState state) {
		return 8;
	}

	@Override
	public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}
}
