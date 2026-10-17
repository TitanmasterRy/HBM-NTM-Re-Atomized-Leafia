package com.leafia.contents.nonmachines.storage.items;

import com.hbm.blockentity.machine.storage.BlockEntityCrateTungsten;
import com.hbm.blocks.BlockEntityOverrides;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.recipes.DFCRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tungsten crate as the addon makes it: it takes SPK from the DFC's cable, and a beam's smelt reads the DFC
 * recipe table.
 *
 * <p>Upstream: {@code MixinTileEntityCrateTungsten}, which makes the base crate an {@code ISPKReceiver}, injects a
 * six-sided subscription into its {@code update}, and <b>overwrites {@code addEnergy}</b> ("i preferred Extended
 * algorithm"). Here the crate is the base mod's {@link BlockEntityCrateTungsten} subclassed under a type of the
 * addon's, put into the base block by {@link BlockEntityOverrides} while {@code overhauls.tungstenCrate} is on,
 * and the smelt is a {@link BlockEntityCrateTungsten.Hook}:
 *
 * <ul>
 * <li><b>The subscription</b> is the hook's {@code tick}: every tick, to the cable beside each of the six faces
 *     (upstream's {@code ForgeDirection.VALID_DIRECTIONS} loop, in that order).</li>
 * <li><b>The smelt</b> is the hook's {@code smeltResult}, run for each stack on top of the furnace's answer and
 *     the base crate's polonium rule (the base stays whole; upstream's overwrite dropped both of those for NTM-CE's
 *     own ways of making them, which this base has no table for). For a stack with a row in {@link DFCRecipes}
 *     whose energy is {@code 0.9 x} the row's or less than the beam's, the row's output replaces the furnace's
 *     <b>unless a roll fails</b>: {@code 0.001 > count x random x (needed / beam)}, so the bigger the stack and the
 *     closer the beam is to the need, the likelier the stack is left alone. A beam {@code 1000 x} the need makes a
 *     stack of 64 sure.</li>
 * <li><b>The last beam is the crate's SPK.</b> Upstream sets {@code joules = energy} at the end of {@code addEnergy}
 *     and answers it as the crate's stored SPK; the crate's capacity is the whole range of a {@code long}.</li>
 * </ul>
 *
 * <p>Upstream also charges a crucible by one when the beam is over 10,000,000; the crucible is a plain item in
 * this base (no charges), so that line has no target. A hand-off.
 *
 * <p>A crate already in a world keeps the type it was saved with, as the DFC's parts do (D-3011): turning the
 * switch on changes the crates placed afterwards. The hook's smelt, though, applies to every tungsten crate while
 * the switch is on.
 */
public class TungstenCrateSpk extends BlockEntityCrateTungsten implements ISPKReceiver {

	/** The last beam's energy: upstream's {@code joules}, and what {@link #getSPK()} answers. */
	public long joules;

	public TungstenCrateSpk(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public long transferSPK(long power, boolean simulate) {
		if(!simulate) {
			addEnergy(null, null, power, null);
			joules = power;
		}
		return 0L;
	}

	@Override
	public long getSPK() {
		return joules;
	}

	@Override
	public void setSPK(long power) {
		joules = power;
	}

	@Override
	public long getMaxSPK() {
		return Long.MAX_VALUE;
	}

	/// THE HOOK ///

	public static final BlockEntityCrateTungsten.Hook HOOK = new BlockEntityCrateTungsten.Hook() {

		@Override
		public void tick(BlockEntityCrateTungsten crate) {
			if(!LeafiaFeatures.tungstenCrate) return;
			if(!(crate instanceof TungstenCrateSpk receiver) || crate.getLevel() == null) return;
			for(Direction dir : DIRECTIONS)
				receiver.trySubscribeSPK(crate.getLevel(), crate.getBlockPos().relative(dir), dir);
		}

		@Override
		public boolean addEnergy(BlockEntityCrateTungsten crate, long energy) {
			if(LeafiaFeatures.tungstenCrate && crate instanceof TungstenCrateSpk receiver) receiver.joules = energy;
			return false;
		}

		@Override
		public ItemStack smeltResult(BlockEntityCrateTungsten crate, ItemStack stack, long energy, ItemStack result) {
			if(!LeafiaFeatures.tungstenCrate || crate.getLevel() == null) return result;
			return smelt(crate.getLevel().random, stack, energy, result);
		}
	};

	/** Upstream's {@code ForgeDirection.VALID_DIRECTIONS}: down, up, north, south, west, east. */
	private static final Direction[] DIRECTIONS = { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

	/**
	 * Upstream's "Extended algorithm" for one stack: {@code result} is what the furnace made of it, and the
	 * DFC table may replace that.
	 */
	public static ItemStack smelt(RandomSource random, ItemStack stack, long energy, ItemStack result) {

		long requiredEnergy = DFCRecipes.getRequiredFlux(stack);
		int count = stack.getCount();
		requiredEnergy *= 0.9D;
		if(requiredEnergy > -1 && energy > requiredEnergy) {
			if(0.001D > count * random.nextDouble() * ((double) requiredEnergy / (double) energy)) {
				ItemStack check = DFCRecipes.getOutput(stack);
				if(check != null && !check.isEmpty()) result = check;
			}
		}
		return result;
	}

	private static boolean overrideRegistered;

	/** Common setup: the hook, and the override while the content exists. Idempotent. */
	public static void commonSetup() {
		if(!LeafiaFeatures.tungstenCrateContent() || LeafiaBlockEntities.CRATE_TUNGSTEN == null) return;
		BlockEntityCrateTungsten.removeHook(HOOK);
		BlockEntityCrateTungsten.addHook(HOOK);
		if(overrideRegistered) return;
		overrideRegistered = true;
		BlockEntityOverrides.register(ModBlocks.crate_tungsten.get(), LeafiaBlockEntities.CRATE_TUNGSTEN,
				(pos, state) -> LeafiaFeatures.tungstenCrate ? new TungstenCrateSpk(LeafiaBlockEntities.CRATE_TUNGSTEN.get(), pos, state) : null);
	}
}
