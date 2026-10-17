package com.leafia.contents.control.fuel.nuclearfuel;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityReactorResearch;
import com.hbm.blockentity.machine.BlockEntityWasteDrum;
import com.leafia.config.LeafiaFeatures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The addon's fuel rods in the base mod's research reactor and waste drum.
 *
 * <p>Upstream: {@code MixinTileEntityReactorResearch} and {@code MixinTileEntityWasteDrum}, both of which
 * inject into the base block entity's {@code update}. Here they are the base's two fuel-handler seams (D-2198):
 * {@link BlockEntityReactorResearch#addFuelHandler} and {@link BlockEntityWasteDrum#addFuelHandler}, registered
 * once from common setup while {@code content.rods} is on. A handler claims {@link LeafiaRodItem}s, which makes
 * the reactor accept one by hand or by hopper (slot 0, upstream's guard) and the drum take and hold one.
 *
 * <h2>The research reactor: {@link #RESEARCH}</h2>
 *
 * After the plates' own pass, on every tick the control rods are out, each slot holding a rod is run:
 *
 * <ul>
 * <li>The flux a rod is bathed in is the sum of the {@link LeafiaRodItem#getFlux flux} of the other rods around
 * it - every other slot of its own column of the grid (slots five apart, each halved for every step of
 * distance), the slot beside it in its row at full strength and the diagonals and two-away at half, a table of
 * its own for each of the five columns - times the <b>control level</b>. Upstream's 12 slots are three rows of
 * five minus three, and the table is its own arithmetic, kept as written, not the plates' neighbour map.</li>
 * <li>{@code HeatFunction(stack, true, flux x level, 1, 20, 400)}: full coolant, 20 degrees, a cooling rate of
 * 400. The rod's {@code cooled} (x50) goes into the reactor's heat and its {@code incoming} (/80) into its flux.</li>
 * <li>A rod that has used up its life is swapped for what it decays to, in place.</li>
 * <li>Then the rod in the slot (the decayed one, if it was swapped) is asked two questions: has it gone
 * {@code nuke} (ten million degrees)? Everything is emptied, the reactor is gone, and the rod blows
 * <b>one block above</b> - and the reactor does not explode. Has it spilled for more than 100 ticks? Everything is
 * emptied, the most meltdown-prone rod of all of them detonates at radius 2 where the reactor was, and then the
 * reactor explodes as its own. Either ends the tick for the whole reactor.</li>
 * </ul>
 *
 * <h2>The waste drum: {@link #DRUM}</h2>
 *
 * A rod in a drum with water beside it cools by {@code HeatFunction(stack, true, 0, r / 18000, 20, 100)} and its
 * {@code decay} falls by {@code r / 36,000,000} (to zero) every tick, {@code r} being the drum's interval. Upstream's
 * drum does not roll the one-in-{@code r} for a rod it knows, and takes it out whenever it is asked to.
 */
public final class LeafiaRodFuel {

	private LeafiaRodFuel() {}

	/** The research reactor's: claims every addon rod and runs it. */
	public static final BlockEntityReactorResearch.FuelHandler RESEARCH = new BlockEntityReactorResearch.FuelHandler() {

		@Override
		public boolean handles(ItemStack stack) {
			return stack.getItem() instanceof LeafiaRodItem;
		}

		@Override
		public boolean react(BlockEntityReactorResearch reactor, int slot, ItemStack stack) {
			return LeafiaRodFuel.reactResearch(reactor, slot);
		}
	};

	/** The waste drum's. */
	public static final BlockEntityWasteDrum.FuelHandler DRUM = new BlockEntityWasteDrum.FuelHandler() {

		@Override
		public boolean handles(ItemStack stack) {
			return stack.getItem() instanceof LeafiaRodItem;
		}

		@Override
		public void cool(BlockEntityWasteDrum drum, int slot, ItemStack stack, int interval) {
			LeafiaRodFuel.coolInDrum(stack, interval);
		}
	};

	/** Common setup: joins both seams while the rods exist. Idempotent. */
	public static void commonSetup() {
		BlockEntityReactorResearch.removeFuelHandler(RESEARCH);
		BlockEntityWasteDrum.removeFuelHandler(DRUM);
		if(!LeafiaFeatures.rods) return;
		BlockEntityReactorResearch.addFuelHandler(RESEARCH);
		BlockEntityWasteDrum.addFuelHandler(DRUM);
	}

	/// WASTE DRUM ///

	/** Upstream's {@code MixinTileEntityWasteDrum.onUpdate} for one slot. */
	public static void coolInDrum(ItemStack stack, int r) {
		if(stack.isEmpty() || !(stack.getItem() instanceof LeafiaRodItem rod)) return;
		rod.HeatFunction(stack, true, 0, r / (float) (60 * 60 * 5), 20, 100);
		CompoundTag tag = LeafiaRodItem.data(stack);
		if(tag != null) {
			double d = r / 60d / 60 / 20 / 30;
			double decay = tag.getDouble("decay");
			tag.putDouble("decay", Math.max(decay - d, 0));
			LeafiaRodItem.putData(stack, tag);
		}
	}

	/// RESEARCH REACTOR ///

	/** Upstream's {@code getHeatInSlot}: the flux a slot's rod gives {@code rod}. A slot off the grid gives none. */
	static double heatInSlot(BlockEntityReactorResearch reactor, int slot, LeafiaRodItem rod) {
		if(slot < 0 || slot >= BlockEntityReactorResearch.SLOTS) return 0;
		return rod.getFlux(reactor.getItem(slot));
	}

	/**
	 * The flux the rod in {@code slot} is bathed in, before the control level: upstream's two column sums and its
	 * five-way table on {@code slot mod 5}.
	 */
	public static double detectedHeat(BlockEntityReactorResearch reactor, int slot, LeafiaRodItem rod) {
		double detectedHeat = 0;
		for(int offset = 1; slot - offset * 5 >= 0; offset += 1) {
			detectedHeat += heatInSlot(reactor, slot - offset * 5, rod) / Math.pow(2, offset - 1);
		}
		for(int offset = 1; slot + offset * 5 < 12; offset += 1) {
			detectedHeat += heatInSlot(reactor, slot + offset * 5, rod) / Math.pow(2, offset - 1);
		}
		switch(Math.floorMod(slot, 5)) {
			case 0:
				detectedHeat += heatInSlot(reactor, slot + 1, rod);
				if(slot < 10) {
					detectedHeat += heatInSlot(reactor, slot + 2, rod) / 2;
					detectedHeat += heatInSlot(reactor, slot + 3, rod) / 2;
				}
				if(slot > 4) {
					detectedHeat += heatInSlot(reactor, slot - 2, rod) / 2;
					detectedHeat += heatInSlot(reactor, slot - 3, rod) / 2;
				}
				break;
			case 1:
				detectedHeat += heatInSlot(reactor, slot - 1, rod);
				if(slot < 10) {
					detectedHeat += heatInSlot(reactor, slot + 2, rod) / 2;
					detectedHeat += heatInSlot(reactor, slot + 3, rod) / 2;
				}
				if(slot > 4) {
					detectedHeat += heatInSlot(reactor, slot - 2, rod) / 2;
					detectedHeat += heatInSlot(reactor, slot - 3, rod) / 2;
				}
				break;
			case 2:
				detectedHeat += heatInSlot(reactor, slot + 1, rod) + heatInSlot(reactor, slot + 2, rod) / 2;

				detectedHeat += heatInSlot(reactor, slot - 2, rod) / 2;
				detectedHeat += heatInSlot(reactor, slot + 3, rod) / 2;
				break;
			case 3:
				detectedHeat += heatInSlot(reactor, slot - 1, rod) + heatInSlot(reactor, slot + 1, rod);

				detectedHeat += heatInSlot(reactor, slot - 2, rod) / 2;
				detectedHeat += heatInSlot(reactor, slot + 2, rod) / 2;
				detectedHeat += heatInSlot(reactor, slot - 3, rod) / 2;
				detectedHeat += heatInSlot(reactor, slot + 3, rod) / 2;
				break;
			case 4:
				detectedHeat += heatInSlot(reactor, slot - 1, rod) + heatInSlot(reactor, slot - 2, rod) / 2;

				detectedHeat += heatInSlot(reactor, slot - 3, rod) / 2;
				detectedHeat += heatInSlot(reactor, slot + 2, rod) / 2;
				break;
		}
		return detectedHeat;
	}

	/** Upstream's {@code handleLeafiaFuel}: one rod's fission for one tick. Returns what the rod {@code cooled}. */
	public static double handleLeafiaFuel(BlockEntityReactorResearch reactor, int slot, double cool) {
		ItemStack stack = reactor.getItem(slot);
		LeafiaRodItem rod = (LeafiaRodItem) stack.getItem();
		double detectedHeat = detectedHeat(reactor, slot, rod);
		rod.HeatFunction(stack, true, detectedHeat * reactor.controlLevel, cool, 20, 400);

		ItemStack decayed = rod.getDecayProduct(stack);
		if(decayed != null) reactor.setItem(slot, decayed);

		// upstream reads the tag of the stack it started with, not of the one that replaced it
		CompoundTag data = LeafiaRodItem.data(stack);
		if(data != null) {
			reactor.heat = reactor.heat + (int) (data.getDouble("cooled") * 50);
			reactor.totalFlux += (int) (data.getDouble("incoming") / 80);
			return data.getDouble("cooled");
		}
		return 0; // failsafe
	}

	/** Upstream's loop body for one slot of {@code update}; true when the reactor is gone. */
	public static boolean reactResearch(BlockEntityReactorResearch reactor, int slot) {

		if(!(reactor.getItem(slot).getItem() instanceof LeafiaRodItem rod)) return false;
		Level level = reactor.getLevel();
		if(level == null) return false;

		handleLeafiaFuel(reactor, slot, 1);

		CompoundTag nbt = LeafiaRodItem.data(reactor.getItem(slot));
		if(nbt == null) return false;

		if(nbt.getBoolean("nuke")) {
			for(int j = 0; j < BlockEntityReactorResearch.SLOTS; j++)
				reactor.setItem(j, ItemStack.EMPTY);
			level.removeBlock(reactor.getBlockPos(), false);
			rod.nuke(level, reactor.getBlockPos().above());
			return true;
		}

		if(nbt.getInt("spillage") > 20 * 5) {
			@Nullable ItemStack prevStack = null;
			for(int j = 0; j < BlockEntityReactorResearch.SLOTS; j++) {
				prevStack = LeafiaRodItem.comparePriority(reactor.getItem(j), prevStack);
				reactor.setItem(j, ItemStack.EMPTY);
			}
			level.removeBlock(reactor.getBlockPos(), false);
			LeafiaRodItem detonate = prevStack != null ? (LeafiaRodItem) prevStack.getItem() : rod;
			detonate.resetDetonate();
			detonate.detonateRadius = 2;
			detonate.detonate(level, reactor.getBlockPos());
			reactor.explode();
			return true;
		}

		return false;
	}
}
