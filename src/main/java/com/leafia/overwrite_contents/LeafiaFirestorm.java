package com.leafia.overwrite_contents;

import java.util.List;

import com.hbm.blockentity.IRepairable.EnumExtinguishType;
import com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Liquid;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.module.machine.ModuleMachineChemplant;
import com.hbm.util.ParticleUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.firestorm.IFirestormBlock;
import com.leafia.dev.firestorm.IFirestormTE;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Firestorm (D-3025): fire next to a chemical plant holding something flammable can burn it out for good.
 * Not an upstream file: it is where {@code MixinMachineChemicalPlant}, {@code MixinTileEntityMachineChemicalPlant}
 * and the fire half of {@code LeafiaUtil} meet the base mod's chemical plant seams (D-2198).
 *
 * <table>
 * <tr><th>upstream</th><th>here</th></tr>
 * <tr><td>{@code MixinTileEntityMachineChemicalPlant}: the {@code onFire}/{@code destroyed} fields, their NBT and
 * packet</td><td>two booleans in the plant's extra tag ({@link BlockEntityMachineChemicalPlant#getExtraData()}), which
 * the base saves and syncs</td></tr>
 * <tr><td>its {@code @Redirect} of the module's {@code update}</td><td>{@link #PLANT}'s {@code replaceProcessing}, which
 * also clears the module's {@code didProcess}, {@code progress} and {@code markDirty} itself, as upstream's did</td></tr>
 * <tr><td>its {@code catchFire}, {@code isDestroyed}, {@code tryExtinguish}</td><td>{@link ChemicalPlant}, an adapter
 * {@link IFirestormTE#of} finds</td></tr>
 * <tr><td>{@code MixinMachineChemicalPlant.onBlockActivated}</td><td>{@code blockUse}</td></tr>
 * <tr><td>{@code MixinRenderChemicalPlant}</td><td>{@code LeafiaFirestormClient}'s render hook</td></tr>
 * <tr><td>{@code LeafiaUtil.spreadFire}</td><td>{@link #spreadFire}</td></tr>
 * </table>
 *
 * <p>Every part reads {@link LeafiaFeatures#firestorm} when it runs, so with it off (the default) every plant is the
 * base mod's, including one saved burnt out: its flags stay in its tag and are ignored.
 */
public final class LeafiaFirestorm {

	private LeafiaFirestorm() {}

	/** Upstream's NBT keys, kept for the extra tag. */
	public static final String DESTROYED = "firestorm_destroyed";
	public static final String ON_FIRE = "firestorm_onFire";

	/** How far a burning plant spreads fire. */
	public static final int FIRE_RADIUS = 4;

	private static boolean installed = false;

	public static synchronized void install() {
		if(installed) return;
		installed = true;
		BlockEntityMachineChemicalPlant.addHook(PLANT);
		com.hbm.blockentity.IRepairable.addResolver(REPAIRABLE);
	}

	/**
	 * Upstream's mixin made the plant an {@code IRepairable} (through {@code IFirestormTE}), which is what let the extinguisher's
	 * water, foam and sand rounds find it and put its fire out. The base mod's {@code IRepairable.of} asks a resolver for a block
	 * entity that does not implement the interface (D-2207), and this is the plant's: the {@link ChemicalPlant} adapter, with the
	 * switch read each time, so a plant is the base mod's - not extinguishable, not repairable - while {@code features.firestorm}
	 * is off (D-3041).
	 */
	public static final java.util.function.Function<net.minecraft.world.level.block.entity.BlockEntity, com.hbm.blockentity.IRepairable> REPAIRABLE =
			tile -> LeafiaFeatures.firestorm && tile instanceof BlockEntityMachineChemicalPlant plant ? new ChemicalPlant(plant) : null;

	/** The plant's firestorm face: no state of its own, everything is in the plant's extra tag. */
	public static final class ChemicalPlant implements IFirestormTE {

		private final BlockEntityMachineChemicalPlant plant;

		public ChemicalPlant(BlockEntityMachineChemicalPlant plant) {
			this.plant = plant;
		}

		public BlockEntityMachineChemicalPlant plant() {
			return plant;
		}

		public boolean isOnFire() {
			return LeafiaFeatures.firestorm && plant.getExtraData().getBoolean(ON_FIRE);
		}

		@Override
		public boolean isDestroyed() {
			return LeafiaFeatures.firestorm && plant.getExtraData().getBoolean(DESTROYED);
		}

		@Override
		public void catchFire() {
			catchFire(plant.getLevel().getRandom());
		}

		/**
		 * Upstream's {@code catchFire}, with the dice passed in. Each tank holding a flammable or combustible fluid
		 * rolls in turn, one chance in 40 for a liquid or solid and one in 3 for a gas (upstream writes 200 and 15
		 * and divides by five); the first success burns the plant out, empties all six tanks, sets it on fire if the
		 * fluid that did it is a liquid, and explodes at power 5 without breaking blocks.
		 */
		public void catchFire(RandomSource random) {
			Level level = plant.getLevel();
			for(FluidTank tank : plant.getAllTanks()) {
				if(!tank.getTankType().hasTrait(FT_Flammable.class) && !tank.getTankType().hasTrait(FT_Combustible.class)) continue;
				if(tank.getFill() <= 0) continue;

				int chance = 200;
				if(tank.getTankType().hasTrait(FT_Gaseous.class)) chance = 15;
				boolean willSetOnFire = tank.getTankType().hasTrait(FT_Liquid.class);

				if(random.nextInt(chance / 5) == 0) {
					CompoundTag extra = plant.getExtraData();
					extra.putBoolean(DESTROYED, true);
					for(FluidTank each : plant.getAllTanks()) each.setFill(0);
					extra.putBoolean(ON_FIRE, willSetOnFire);
					plant.markChanged();
					BlockPos pos = plant.getBlockPos();
					level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5F, false, Level.ExplosionInteraction.NONE);
					break;
				}
			}
		}

		/** Upstream clears {@code onFire} for any kind of extinguisher, burnt out or not. */
		@Override
		public void tryExtinguish(Level level, BlockPos pos, EnumExtinguishType type) {
			plant.getExtraData().putBoolean(ON_FIRE, false);
			plant.markChanged();
		}
	}

	/** The plant's two hooks. */
	private static final BlockEntityMachineChemicalPlant.Hook PLANT = new BlockEntityMachineChemicalPlant.Hook() {

		@Override
		public boolean replaceProcessing(BlockEntityMachineChemicalPlant plant) {
			ChemicalPlant face = new ChemicalPlant(plant);
			if(!face.isDestroyed()) return false;

			ModuleMachineChemplant module = plant.chemplantModule;
			module.markDirty = false;
			module.didProcess = false;
			module.progress = 0;

			if(face.isOnFire()) burn(plant);
			return true;
		}

		@Override
		public boolean blockUse(BlockEntityMachineChemicalPlant plant, Player player) {
			return new ChemicalPlant(plant).isDestroyed();
		}
	};

	/** One tick of a burning plant: a flame, everything in the box above it set alight for five seconds, and a third of the time a fire. */
	static void burn(BlockEntityMachineChemicalPlant plant) {
		Level level = plant.getLevel();
		BlockPos pos = plant.getBlockPos();
		RandomSource rand = level.getRandom();

		ParticleUtil.spawnGasFlame(level, pos.getX() + rand.nextDouble(), pos.getY() + 0.5D + rand.nextDouble(), pos.getZ() + rand.nextDouble(),
				rand.nextGaussian() * 0.2D, 0.1D, rand.nextGaussian() * 0.2D);

		List<Entity> affected = level.getEntitiesOfClass(Entity.class,
				new AABB(pos.getX() - 1.5D, pos.getY(), pos.getZ() - 1.5D, pos.getX() + 2.5D, pos.getY() + 5D, pos.getZ() + 2.5D));
		for(Entity entity : affected) entity.igniteForSeconds(5);

		if(rand.nextInt(3) == 0) spreadFire(level, pos, FIRE_RADIUS);
	}

	/**
	 * Upstream's {@code LeafiaUtil.spreadFire}: vanilla's fire spreading from a lava block, with a radius, that also
	 * tries {@link IFirestormBlock#ignite} on every block it looks at.
	 */
	public static void spreadFire(Level level, BlockPos pos, int radius) {
		if(!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
		RandomSource rand = level.getRandom();

		int i = rand.nextInt(3);

		if(i > 0) {
			BlockPos blockpos = pos;

			for(int j = 0; j < i; ++j) {
				blockpos = blockpos.offset(rand.nextInt(radius * 2 + 1) - radius, 1, rand.nextInt(radius * 2 + 1) - radius);

				if(!level.isOutsideBuildHeight(blockpos) && !level.isLoaded(blockpos)) return;

				BlockState block = level.getBlockState(blockpos);

				if(block.isAir()) {
					if(LeafiaCoriumBehavior.isSurroundingBlockFlammable(level, blockpos)) {
						level.setBlockAndUpdate(blockpos, Blocks.FIRE.defaultBlockState());
						return;
					}
				} else if(block.blocksMotion()) {
					return;
				}
				IFirestormBlock.ignite(level, blockpos);
			}
		} else {
			for(int k = 0; k < 3; ++k) {
				BlockPos blockpos1 = pos.offset(rand.nextInt(radius * 2 + 1) - radius, 0, rand.nextInt(radius * 2 + 1) - radius);

				if(!level.isOutsideBuildHeight(blockpos1) && !level.isLoaded(blockpos1)) return;

				if(level.isEmptyBlock(blockpos1.above()) && LeafiaCoriumBehavior.getCanBlockBurn(level, blockpos1)) {
					level.setBlockAndUpdate(blockpos1.above(), Blocks.FIRE.defaultBlockState());
				}
				IFirestormBlock.ignite(level, blockpos1);
			}
		}
	}
}
