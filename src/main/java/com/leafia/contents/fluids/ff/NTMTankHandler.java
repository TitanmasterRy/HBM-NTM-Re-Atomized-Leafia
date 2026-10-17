package com.leafia.contents.fluids.ff;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Base-mod tanks seen as a NeoForge {@link IFluidHandler}: how a machine that keeps the base mod's
 * {@link FluidTank}s is reached by FF ducts, buckets, other mods' pipes and {@link LeafiaFF#pour}.
 *
 * <p>Upstream had no such class: in NTM-CE a machine that wanted to be a Forge fluid handler
 * implemented {@code IFluidHandler} itself, over Forge tanks, and the FF converter
 * ({@code FFConverterTE}) moved fluid between the two worlds by hand -
 * {@code ff.fill(new FluidStack(ntmf.getTankType().getFF(), ntmf.getFill()))}. This is that
 * conversion made reusable, so a converter, a pump or a block capability is one constructor.
 *
 * <h2>The rules, each a base-mod rule kept</h2>
 *
 * <ul>
 * <li><b>A tank's type is not set by filling it.</b> In the base mod a tank is typed by a fluid
 *     identifier and only then accepts that fluid; a {@code NONE} tank accepts nothing here either.</li>
 * <li><b>A pressurised tank is invisible.</b> A Forge stack has no pressure, so a tank holding
 *     pressurised fluid neither gives nor takes - giving would depressurise the fluid silently.</li>
 * <li><b>Capacity is the tank's {@code maxFill}</b>, and a fill takes what fits and reports it.</li>
 * <li>Filling spreads across matching tanks in order; draining by amount takes from the first tank
 *     that has a mapped fluid, as {@code FluidTank} does with one tank. Nothing is moved on
 *     {@code SIMULATE}.</li>
 * </ul>
 *
 * The stacks it hands out are new each call; writing to one changes no tank.
 */
public class NTMTankHandler implements IFluidHandler {

	private final FluidTank[] tanks;
	private final boolean canFill;
	private final boolean canDrain;

	/** All the tanks, both ways. */
	public NTMTankHandler(FluidTank... tanks) {
		this(true, true, tanks);
	}

	/** For a machine that only takes in, or only gives out, through this face. */
	public NTMTankHandler(boolean canFill, boolean canDrain, FluidTank... tanks) {
		this.tanks = tanks;
		this.canFill = canFill;
		this.canDrain = canDrain;
	}

	private static boolean usable(FluidTank tank) {
		return tank.getPressure() == 0 && tank.getTankType() != Fluids.NONE && LeafiaFF.isMapped(tank.getTankType());
	}

	@Override
	public int getTanks() {
		return tanks.length;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		FluidTank t = tanks[tank];
		if(t.getPressure() != 0) return FluidStack.EMPTY;
		return LeafiaFF.toFF(t.getTankType(), t.getFill());
	}

	@Override
	public int getTankCapacity(int tank) {
		return tanks[tank].getMaxFill();
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		FluidTank t = tanks[tank];
		return canFill && usable(t) && LeafiaFF.fromFF(stack) == t.getTankType();
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if(!canFill || resource.isEmpty()) return 0;
		FluidType type = LeafiaFF.fromFF(resource);
		if(type == Fluids.NONE) return 0;

		int left = resource.getAmount();
		for(FluidTank t : tanks) {
			if(left <= 0) break;
			if(t.getTankType() != type || !usable(t)) continue;
			int fits = Math.max(0, t.getMaxFill() - t.getFill());
			int take = Math.min(fits, left);
			if(take <= 0) continue;
			if(action.execute()) t.setFill(t.getFill() + take);
			left -= take;
		}
		return resource.getAmount() - left;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if(!canDrain || resource.isEmpty()) return FluidStack.EMPTY;
		FluidType type = LeafiaFF.fromFF(resource);
		if(type == Fluids.NONE) return FluidStack.EMPTY;
		return drainType(type, resource.getAmount(), action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		if(!canDrain || maxDrain <= 0) return FluidStack.EMPTY;
		for(FluidTank t : tanks) {
			if(usable(t) && t.getFill() > 0) return drainType(t.getTankType(), maxDrain, action);
		}
		return FluidStack.EMPTY;
	}

	private FluidStack drainType(FluidType type, int max, FluidAction action) {
		int left = max;
		for(FluidTank t : tanks) {
			if(left <= 0) break;
			if(t.getTankType() != type || !usable(t)) continue;
			int take = Math.min(t.getFill(), left);
			if(take <= 0) continue;
			if(action.execute()) t.setFill(t.getFill() - take);
			left -= take;
		}
		return LeafiaFF.toFF(type, max - left);
	}
}
