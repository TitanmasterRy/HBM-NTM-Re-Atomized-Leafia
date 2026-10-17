package com.leafia.overwrite_contents;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.BlockProcessorStandard;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.hbm.util.LegacyMaterial;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.bomb.balefire.BaleoniteBlock;
import com.leafia.contents.fluids.BaleCoriumFluid.BaleCoriumFluidBlock;
import com.leafia.unsorted.explosion_vnt.BlockAllocatorSteamExplosion;
import com.leafia.unsorted.explosion_vnt.BlockMutatorCollapse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Corium, the addon's way: upstream's {@code MixinCoriumFinite}, five changes to the base mod's corium
 * pool, here one {@link CoriumFinite.Behavior} (D-2197) that applies while
 * {@code features.overhauls.explosionVfx} is on. Upstream mixes into {@code CoriumFinite} itself, so it
 * reaches balefire corium too ({@link BaleCoriumFluidBlock} extends it), with a branch for that one.
 *
 * <ol>
 * <li><b>Displacement</b> - one time in three corium refuses to flow at all, before the base rule is
 *     asked. Balefire corium overrides {@code canDisplace} with its own rule, which already has that
 *     refusal, and never reaches this.</li>
 * <li><b>Contact</b> - the base rule (stuck, three seconds alight, two radiation damage, one RAD) becomes
 *     stuck, three seconds alight, <b>three lava damage and 500 RAD</b> through anything; a server player
 *     in creative or spectator is left alone entirely.</li>
 * <li><b>The tick</b> - after the flow: 50 chunk RAD (500 for balefire corium), uncapped; then one time in
 *     ten the pool sets <b>whether or not there is corium under it</b> (upstream's reason: "make it
 *     solidify even if theres corium below") - as corium cobble, or as the corium block, or one time in
 *     three of those as chernobylite ore (zetalite for balefire), with the block twice as likely for
 *     balefire corium. Then, with {@code doFireTick}, upstream's lava fire: {@code nextInt(3)} steps
 *     upward, lighting fire (balefire for balefire corium) in the first air beside something
 *     flammable; or, on a roll of 0, three tries beside it at the same height. Balefire corium also eats
 *     rock it touches there, into baleonite level 2 two times in three and level 1 otherwise (the XOR
 *     roll, {@link BaleoniteBlock#roll}). The pool keeps ticking until it sets.</li>
 * <li><b>Water</b> - whenever a pool is placed or a neighbour changes and any of its six neighbours is
 *     water, it vanishes and explodes: a {@link BlockAllocatorSteamExplosion} of resolution 32 and size
 *     {@code 12 + w / 40}, where {@code w} is the water blocks in the 21-cube around it, with
 *     {@link BlockMutatorCollapse}, the cross-smooth entity pass (0.5, 50, piercing 5 / 0.2), the
 *     standard player pass and the standard sound and smoke; plus an explosion sound at 4 and a fire
 *     hiss at 9.</li>
 * <li><b>Display</b> - one time in five a lava pop (one in twenty of those), one or two lava sparks and up to
 *     two of each smoke; one time in a hundred the lava ambience. Balefire corium draws its own instead
 *     (D-3004).</li>
 * </ol>
 *
 * <h2>Kept exactly, and flagged</h2>
 *
 * <ul>
 * <li>In the upward fire walk, a non-air block that does not block movement ends nothing, and balefire
 *     corium's rock test runs only on such a block - which rock never is. That branch is dead upstream
 *     and dead here. (The sideways one, on a roll of 0, is live.)</li>
 * <li>The sideways rock test does not exclude corium blocks and corium ores (the upward one does): a
 *     balefire pool turns the corium block beside it into baleonite if 1.7.10 called it rock.</li>
 * <li>The contact damage ignores radiation protection ({@code CREATIVE}) and is 500 for both corium kinds;
 *     only the chunk radiation tells them apart.</li>
 * <li>Upstream's re-entry guard is a field of the block, so a pool's own blast can set off a
 *     neighbouring pool of the <em>other</em> kind but not of its own. That is what the guard here
 *     keeps: one flag per block.</li>
 * </ul>
 *
 * <p>Translations: {@code Material.getCanBurn} is {@code ignitedByLava()}, the property 1.21.1's lava asks
 * the same question with; {@code blocksMovement} is {@code blocksMotion()}; {@code Material.ROCK} is
 * {@link LegacyMaterial#ROCK}; a water material is a water block ({@link BlockAllocatorSteamExplosion#isWater});
 * {@code fireFluidPlaceBlockEvent} is NeoForge's {@link EventHooks#fireFluidPlaceBlockEvent}. The fire
 * rolls use a random of the behaviour's own, as upstream's used the block's {@code rand} field (the tick's
 * own random sets the pool); a test can swap it. With {@code features.coriumOres} off there are no ores,
 * and the roll that would have made one makes the corium block.
 */
public final class LeafiaCoriumBehavior implements CoriumFinite.Behavior {

	public static final LeafiaCoriumBehavior INSTANCE = new LeafiaCoriumBehavior();

	/** Chunk radiation a tick: corium. */
	public static final float RAD = 50F;
	/** ... and balefire corium. */
	public static final float RAD_BALEFIRE = 500F;
	/** Contamination on contact, through any protection. */
	public static final float CONTACT_RAD = 500F;
	/** Lava damage on contact. */
	public static final float CONTACT_DAMAGE = 3F;
	public static final int CONTACT_FIRE_SECONDS = 3;
	/** A pool sets one tick in this many. */
	public static final int SET_ONE_IN = 10;
	/** The steam blast: {@code BASE_SIZE + water / WATER_PER_SIZE}, water counted {@code WATER_REACH} out. */
	public static final float BASE_SIZE = 12F;
	public static final float WATER_PER_SIZE = 40F;
	public static final int WATER_REACH = 10;
	public static final int STEAM_RESOLUTION = 32;

	/** Upstream's {@code CoriumFinite.rand}: the fire rolls. */
	private RandomSource rand = RandomSource.create();

	/** For a test: swaps the fire rolls' random and returns the one it had. */
	public RandomSource swapRandom(RandomSource replacement) {
		RandomSource old = this.rand;
		this.rand = replacement;
		return old;
	}

	private LeafiaCoriumBehavior() {}

	public static boolean isBalefire(Block block) {
		return block instanceof BaleCoriumFluidBlock;
	}

	// ------------------------------------------------------------------ displacement

	@Override
	public Boolean canDisplace(CoriumFinite block, Level level, BlockPos pos, BlockState state, RandomSource random) {
		if(!LeafiaFeatures.explosionVfx) return null;
		return rand.nextInt(3) == 0 ? Boolean.FALSE : null;
	}

	// ------------------------------------------------------------------ contact

	@Override
	public boolean entityInside(CoriumFinite block, BlockState state, Level level, BlockPos pos, Entity entity) {
		if(!LeafiaFeatures.explosionVfx) return false;
		if(entity instanceof ServerPlayer player && (player.isSpectator() || player.isCreative())) return true;
		entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05F, 0.25D));
		entity.igniteForSeconds(CONTACT_FIRE_SECONDS);
		if(!level.isClientSide) entity.hurt(level.damageSources().lava(), CONTACT_DAMAGE);
		if(entity instanceof LivingEntity living)
			ContaminationUtil.contaminate(living, HazardType.RADIATION, ContaminationType.CREATIVE, CONTACT_RAD);
		return true;
	}

	// ------------------------------------------------------------------ the tick

	@Override
	public boolean updateTick(CoriumFinite block, Level level, BlockPos pos, RandomSource random) {
		if(!LeafiaFeatures.explosionVfx || level.isClientSide) return false;
		boolean balefire = isBalefire(block);
		ChunkRadiationManager.incrementRad(level, pos.getX(), pos.getY(), pos.getZ(), balefire ? RAD_BALEFIRE : RAD);

		boolean set = false;
		if(random.nextInt(SET_ONE_IN) == 0) {
			set = true;
			if(random.nextInt(3) <= (balefire ? 1 : 0)) {
				if(random.nextInt(3) == 0)
					level.setBlockAndUpdate(pos, ore(balefire));
				else
					level.setBlockAndUpdate(pos, ModBlocks.block_corium.get().defaultBlockState());
			} else {
				level.setBlockAndUpdate(pos, ModBlocks.block_corium_cobble.get().defaultBlockState());
			}
		}

		spreadFire(level, pos, balefire);

		if(!set && level.getBlockState(pos).is(block)) {
			level.scheduleTick(pos, block, block.tickRate);
		}
		return true;
	}

	/** Chernobylite ore, zetalite for balefire corium; the corium block without the ores. */
	static BlockState ore(boolean balefire) {
		var ore = balefire ? LeafiaBlocks.ore_corium_zetalite : LeafiaBlocks.ore_corium_chernobylite;
		if(ore == null) return ModBlocks.block_corium.get().defaultBlockState();
		return ore.get().defaultBlockState();
	}

	/** The fire half of upstream's tick, from {@code if (doFireTick)} on. */
	void spreadFire(Level level, BlockPos pos, boolean balefire) {

		if(!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;

		BlockState fireState = balefire ? ModBlocks.balefire.get().defaultBlockState() : Blocks.FIRE.defaultBlockState();
		int i = rand.nextInt(3);

		if(i > 0) {
			BlockPos blockpos = pos;

			for(int j = 0; j < i; ++j) {
				blockpos = blockpos.offset(rand.nextInt(3) - 1, 1, rand.nextInt(3) - 1);

				if(!level.isOutsideBuildHeight(blockpos) && !level.isLoaded(blockpos)) {
					return;
				}

				BlockState block = level.getBlockState(blockpos);

				if(block.isAir()) {
					if(isSurroundingBlockFlammable(level, blockpos)) {
						level.setBlockAndUpdate(blockpos, EventHooks.fireFluidPlaceBlockEvent(level, blockpos, pos, fireState));
						return;
					}
				} else if(block.blocksMotion()) {
					return;
				} else if(balefire) {
					// dead upstream: a block that does not block movement is never rock
					if(BaleoniteBlock.consumes(block) && !BaleoniteBlock.isCorium(block)) {
						baleonite(level, blockpos);
					}
				}
			}
		} else {
			for(int k = 0; k < 3; ++k) {
				BlockPos blockpos1 = pos.offset(rand.nextInt(3) - 1, 0, rand.nextInt(3) - 1);

				if(!level.isOutsideBuildHeight(blockpos1) && !level.isLoaded(blockpos1)) {
					return;
				}

				if(level.isEmptyBlock(blockpos1.above()) && getCanBlockBurn(level, blockpos1)) {
					level.setBlockAndUpdate(blockpos1.above(), EventHooks.fireFluidPlaceBlockEvent(level, blockpos1.above(), pos, fireState));
				} else if(balefire) {
					if(BaleoniteBlock.consumes(level.getBlockState(blockpos1))) {
						baleonite(level, blockpos1);
					}
				}
			}
		}
	}

	/** {@code (nextInt(3) + 1) ^ 2}, read as {@link BaleoniteBlock#levelForRoll} does. */
	void baleonite(Level level, BlockPos pos) {
		int meta = BaleoniteBlock.levelForRoll(BaleoniteBlock.roll(rand));
		if(meta >= 0 && LeafiaBlocks.baleonite != null)
			level.setBlockAndUpdate(pos, LeafiaBlocks.baleonite.get().withMeta(meta));
	}

	static boolean isSurroundingBlockFlammable(Level level, BlockPos pos) {
		for(Direction direction : Direction.values()) {
			if(getCanBlockBurn(level, pos.relative(direction))) return true;
		}
		return false;
	}

	/** {@code getCanBlockBurn}: false in an unloaded chunk, otherwise whether lava would set it alight. */
	static boolean getCanBlockBurn(Level level, BlockPos pos) {
		if(!level.isOutsideBuildHeight(pos) && !level.isLoaded(pos)) return false;
		return level.getBlockState(pos).ignitedByLava();
	}

	// ------------------------------------------------------------------ water

	/** Upstream's re-entry guard: one per block, as upstream's was a field of the block. */
	private static final Set<Block> BUSY = Collections.newSetFromMap(new IdentityHashMap<>());

	@Override
	public void onNeighborUpdate(CoriumFinite block, Level level, BlockPos pos) {
		if(!LeafiaFeatures.explosionVfx) return;
		checkForMixing(block, level, pos);
	}

	/** {@code leafia$checkForMixing}. */
	public static void checkForMixing(Block block, Level level, BlockPos pos) {
		if(level.isClientSide) return;
		synchronized(BUSY) {
			if(!BUSY.add(block)) return;
		}
		try {
			for(Direction value : Direction.values()) {
				BlockPos offs = pos.relative(value);
				if(BlockAllocatorSteamExplosion.isWater(level.getBlockState(offs))) {
					int count = countWater(level, pos);
					level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
					level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4,
							(1 + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
					level.playSound(null, x, y, z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 9,
							(1 + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
					steamExplosion(level, pos, blastSize(count)).explode();
					break;
				}
			}
		} finally {
			synchronized(BUSY) {
				BUSY.remove(block);
			}
		}
	}

	/** Water blocks in the 21-cube around {@code pos}. */
	public static int countWater(Level level, BlockPos pos) {
		int count = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for(int xo = -WATER_REACH; xo <= WATER_REACH; xo++) {
			for(int yo = -WATER_REACH; yo <= WATER_REACH; yo++) {
				for(int zo = -WATER_REACH; zo <= WATER_REACH; zo++) {
					cursor.setWithOffset(pos, xo, yo, zo);
					if(BlockAllocatorSteamExplosion.isWater(level.getBlockState(cursor))) count++;
				}
			}
		}
		return count;
	}

	public static float blastSize(int water) {
		return water / WATER_PER_SIZE + BASE_SIZE;
	}

	/** Upstream's {@code ExplosionVNT(world, pos, size)} centres on the block. */
	public static ExplosionVNT steamExplosion(Level level, BlockPos pos, float size) {
		ExplosionVNT vnt = new ExplosionVNT(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, size);
		vnt.setBlockAllocator(new BlockAllocatorSteamExplosion(STEAM_RESOLUTION));
		vnt.setBlockProcessor(new BlockProcessorStandard().withBlockEffect(new BlockMutatorCollapse()));
		vnt.setEntityProcessor(new EntityProcessorCrossSmooth(0.5, 50).setupPiercing(5F, 0.2F));
		vnt.setPlayerProcessor(new PlayerProcessorStandard());
		vnt.setSFX(new ExplosionEffectStandard());
		return vnt;
	}

	// ------------------------------------------------------------------ display

	@Override
	public void animateTick(CoriumFinite block, BlockState state, Level level, BlockPos pos, RandomSource rand) {
		if(!LeafiaFeatures.explosionVfx) return;
		double x = pos.getX(), y = pos.getY(), z = pos.getZ();
		if(rand.nextInt(5) == 0) {
			if(rand.nextInt(20) == 0)
				level.playLocalSound(x + .5, y + .5, z + .5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.7F + rand.nextFloat() * 0.25F, false);
			int amt = rand.nextInt(2) + 1;
			for(int i = 0; i < amt; i++)
				level.addParticle(ParticleTypes.LAVA, x + rand.nextFloat(), y + rand.nextFloat(), z + rand.nextFloat(), 0, 0, 0);
			amt = rand.nextInt(3);
			for(int i = 0; i < amt; i++)
				level.addParticle(ParticleTypes.SMOKE, x + rand.nextFloat(), y + rand.nextFloat(), z + rand.nextFloat(),
						rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25);
			amt = rand.nextInt(3);
			for(int i = 0; i < amt; i++)
				level.addParticle(ParticleTypes.LARGE_SMOKE, x + rand.nextFloat(), y + rand.nextFloat(), z + rand.nextFloat(),
						rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25);
		}
		if(rand.nextInt(100) == 0)
			level.playLocalSound(x + .5, y + .5, z + .5, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.7F + rand.nextFloat() * 0.25F, false);
	}
}
