package com.leafia.contents.fluids.ff;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * A NeoForge tank seen as a base-mod {@link FluidTank}: how an addon machine that keeps Forge tanks
 * (the LFTR, the mixing vat, the coolant heat exchanger) feeds the base mod's fluid network, its
 * canister slots and its tank drawing without a second copy of the fluid.
 *
 * <p>Nothing is stored here. Every read goes to the delegate and every write goes back into it, so
 * the NeoForge tank stays the one source of truth and its stack keeps its components - an LFTR
 * salt's mixture and heat survive a trip through the base network's {@code setFill}. The base mod's
 * network and loaders reach a tank only through {@code getTankType}, {@code getFill},
 * {@code getMaxFill}, {@code getPressure}, {@code setFill} and {@code setTankType} (read from
 * {@code IFluidStandardSenderMK2}/{@code ReceiverMK2} and the three loading handlers), and those are
 * what is overridden; the methods that write the base class's own fields ({@code setType} from an
 * identifier, the NBT and buffer halves) run against a copy pulled from the delegate and push the
 * result back.
 *
 * <h2>What a view cannot be</h2>
 *
 * <ul>
 * <li><b>Pressurised.</b> A Forge stack has no pressure; {@link #withPressure} is refused.</li>
 * <li><b>Typed by its contents alone.</b> An empty NeoForge tank has no fluid, but the base network
 *     subscribes by type, so the view carries a <em>declared</em> type for when it is empty (the
 *     constructor's, or the last {@link #setTankType}). Once fluid is in, the fluid's NTM type wins.
 *     Retyping empties the tank, the base mod's rule.</li>
 * <li><b>A holder of foreign fluid.</b> A NeoForge fluid the bridge does not map reads as
 *     {@code NONE}, which no network matches; it is left where it is.</li>
 * </ul>
 */
public class ForgeTankView extends FluidTank {

	private final net.neoforged.neoforge.fluids.capability.templates.FluidTank delegate;

	public ForgeTankView(net.neoforged.neoforge.fluids.capability.templates.FluidTank delegate, FluidType declared) {
		super(declared == null ? Fluids.NONE : declared, delegate.getCapacity());
		this.delegate = delegate;
	}

	public net.neoforged.neoforge.fluids.capability.templates.FluidTank delegate() {
		return delegate;
	}

	/** Fields from the delegate, before a base method that reads them directly. */
	private void pull() {
		this.type = getTankType();
		this.fluid = getFill();
		this.maxFluid = getMaxFill();
		this.pressure = 0;
	}

	/** Fields into the delegate, after a base method that wrote them directly. */
	private void push() {
		delegate.setCapacity(maxFluid);
		if(fluid <= 0 || type == Fluids.NONE || !LeafiaFF.isMapped(type)) {
			delegate.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
			return;
		}
		net.neoforged.neoforge.fluids.FluidStack held = delegate.getFluid();
		if(!held.isEmpty() && LeafiaFF.fromFF(held) == type) {
			held.setAmount(fluid); // keeps the stack's components
			delegate.setFluid(held);
		} else {
			delegate.setFluid(LeafiaFF.toFF(type, fluid));
		}
	}

	@Override
	public FluidType getTankType() {
		net.neoforged.neoforge.fluids.FluidStack held = delegate.getFluid();
		return held.isEmpty() ? type : LeafiaFF.fromFF(held);
	}

	@Override
	public int getFill() {
		return delegate.getFluidAmount();
	}

	@Override
	public int getMaxFill() {
		return delegate.getCapacity();
	}

	@Override
	public int getPressure() {
		return 0;
	}

	@Override
	public void setFill(int i) {
		this.type = getTankType();
		this.maxFluid = getMaxFill();
		this.fluid = i;
		push();
	}

	@Override
	public void setTankType(FluidType type) {
		if(type == null) type = Fluids.NONE;
		if(getTankType() == type) {
			this.type = type;
			return;
		}
		this.type = type;
		delegate.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
	}

	@Override
	public void resetTank() {
		this.type = Fluids.NONE;
		delegate.setFluid(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
	}

	/** A Forge tank cannot hold pressurised fluid; anything but zero is ignored. */
	@Override
	public FluidTank withPressure(int pressure) {
		return this;
	}

	@Override
	public FluidTank conform(FluidStack stack) {
		setTankType(stack.type);
		return this;
	}

	@Override
	public int changeTankSize(int size) {
		pull();
		int spilled = super.changeTankSize(size);
		push();
		return spilled;
	}

	@Override
	public boolean setType(int in, int out, ItemStack[] slots) {
		pull();
		boolean changed = super.setType(in, out, slots);
		if(changed) push();
		return changed;
	}

	@Override
	public void writeToNBT(CompoundTag nbt, String s) {
		pull();
		super.writeToNBT(nbt, s);
	}

	@Override
	public void readFromNBT(CompoundTag nbt, String s) {
		super.readFromNBT(nbt, s);
		this.pressure = 0;
		push();
	}

	@Override
	public void serialize(FriendlyByteBuf buf) {
		pull();
		super.serialize(buf);
	}

	@Override
	public void deserialize(FriendlyByteBuf buf) {
		super.deserialize(buf);
		this.pressure = 0;
		push();
	}
}
