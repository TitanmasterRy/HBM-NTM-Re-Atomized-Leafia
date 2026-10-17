package com.leafia.dev.blocks.blockbase;

import com.hbm.potion.NtmEffects;
import com.leafia.dev.blocks.legacy.MetaBlock;
import com.leafia.dev.blocks.legacy.MetaLevel;
import com.hbm.handler.radiation.ChunkRadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin layer of radioactive powder: contaminated snow.
 *
 * <p>Ported from {@code com.leafia.dev.blocks.blockbase.AddonBlockPowder}. A single eighth-block
 * slab with seven levels ({@link MetaLevel}) that only differ in colour. It
 * <ul>
 * <li>adds radiation to its chunk every 10 to 39 ticks - 1, never past 100 (the capped
 *     {@code incrementRad(level, pos, amount, max)}, now the base mod's own);
 * <li>puts the radiation effect, level 15, on anything that walks on it for two minutes;
 * <li>can only be placed on a leaf block or a block that is opaque and stops movement, and is
 *     removed again the moment that stops being true - never on ice or packed ice;
 * <li>is replaceable, so a block placed into it simply takes its place;
 * <li>drops nothing ({@code getItemDropped} answers air for anything but {@code ModBlocks.fallout});
 * <li>lets only radiation-immune things spawn on it.
 * </ul>
 *
 * <p>Upstream's {@code canPlaceBlockAt} also lets it stand on another of itself when
 * {@code (meta & 7) == 7}. Its metadata only runs to 6, so that clause can never be true; it is
 * kept in {@link #canSurvive} as written, so the day a block of this class has an eighth level it
 * behaves as upstream's would have.
 *
 * <p>Both the outline and the collision are the eighth-block slab: upstream's
 * {@code getBoundingBox} is the only shape it defines, and 1.12.2 uses it for collision as well.
 */
public class AddonBlockPowder extends Block implements MetaBlock {

	public static final EnumProperty<MetaLevel> META = MetaLevel.LEVELS_7;

	/** {@code new AxisAlignedBB(0, 0, 0, 1, 0.125, 1)}. */
	public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

	/** Radiation effect on contact: {@code 2 * 60 * 20} ticks, amplifier 14. */
	public static final int WALK_TICKS = 2 * 60 * 20;
	public static final int WALK_AMPLIFIER = 14;
	/** {@code incrementRad(world, pos, 1, 100)}. */
	public static final float RAD = 1F;
	public static final float RAD_CAP = 100F;

	public AddonBlockPowder(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(META, MetaLevel.L0));
	}

	@Override
	public EnumProperty<MetaLevel> getVariantProperty() {
		return META;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(META);
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return getVariantStack(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** {@code onBlockAdded}: the first tick is 10 to 39 ticks away. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if(!level.isClientSide) level.scheduleTick(pos, this, 10 + level.random.nextInt(30));
	}

	/** {@code updateTick}. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		ChunkRadiationManager.incrementRad(level, pos, RAD, RAD_CAP);
		level.scheduleTick(pos, this, 10 + level.random.nextInt(30));
	}

	/**
	 * {@code canPlaceBlockAt}: not on ice or packed ice, and then on leaves, on a full stack of
	 * itself (unreachable), or on an opaque block that stops movement.
	 */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos belowPos = pos.below();
		BlockState below = level.getBlockState(belowPos);
		if(below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE)) return false;
		return below.is(BlockTags.LEAVES)
				|| (below.is(this) && (meta(below) & 7) == 7)
				|| (below.isSolidRender(level, belowPos) && below.blocksMotion());
	}

	/** {@code neighborChanged}: gone as soon as it could not have been placed. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
		if(!state.canSurvive(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	/** {@code onEntityWalk}. */
	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		if(!level.isClientSide && entity instanceof LivingEntity living)
			living.addEffect(new MobEffectInstance(NtmEffects.RADIATION, WALK_TICKS, WALK_AMPLIFIER));
	}
}
