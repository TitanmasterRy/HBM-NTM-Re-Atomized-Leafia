package com.leafia.contents.machines.processing.mixingvat;

import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.NtmTags;
import com.hbm.lib.Library;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.processing.mixingvat.container.MixingVatMenu;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.init.LeafiaBlockEntities;
import com.llib.group.LeafiaMap;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The mixing vat: it stirs fuel nuggets into FLiBe salt, a bucket at a time, and hands the salt on with the fuel in its tag.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/processing/mixingvat/MixingVatTE.java}.
 *
 * <h2>How it works</h2>
 *
 * Two 4,000 mB Forge tanks: the input ({@link #tankNc0}) takes salt - from a duct behind the machine, or from anything
 * that can reach its Forge handler - and the output ({@link #tankNc1}) is offered to the two ducts at the front. Six
 * ingredient slots (15 to 20) take fuel: a stack counts <b>once</b> whatever its size, as the nugget of the isotope
 * {@link MSRFuel} names it, and the first fuel in declaration order whose nugget it is wins.
 *
 * <p>A batch needs <b>a fuel in at least one slot, 1,000 mB of salt in the input, more than 50 HE, and room for the
 * mixed bucket in the output</b>. It then runs {@link #maxProgress} (60) ticks at 50 HE a tick, 3,000 HE, and at the end
 * moves 1,000 mB of the salt across with the item mixture added to the salt's own, and uses one of every fuel slot
 * that holds a fuel. Anything missing at any tick sets the progress back to nothing.
 *
 * <p><b>The output must take the stack as it is</b>: a Forge tank fills only with the same fluid and the same tag, so
 * a vat whose output holds a different mixture is stuck, and the clear button on the screen (key 0, 5,000 mB
 * from the output) is how it is unstuck.
 *
 * <p>The slot at 21 holds a fluid identifier: an {@code FT_LFTRCoolant} one is moved to slot 22 when that is empty,
 * and sets the vat's fluid, emptying a tank of anything else. The vat is at 100,000 HE and has a battery at slot 0.
 *
 * <h2>Upstream's quirks, kept (D-021)</h2>
 *
 * <ol>
 * <li><b>The mixed salt has no heat.</b> The stack is rebuilt with a tag holding only {@code itemMixture}, so
 *     whatever heat the input salt carried is gone when it reaches the output.</li>
 * <li><b>Neither power nor the progress is saved.</b> {@code writeToNBT} has a line for the mode, the fluid, the
 *     tanks and the progress, but not for {@code power}: a reloaded vat is empty of charge.</li>
 * <li><b>The identifier is copied whole.</b> Slot 22 gets a copy of the stack and slot 21 loses one, so a stack of
 *     more than one would be duplicated. The fuzzy identifier stacks to one, the base mod's other identifier is not
 *     ported, and nothing reaches it.</li>
 * <li><b>The shift-click does not reach the ingredients</b> (see {@code MixingVatMenu}) and <b>the left tank's
 *     tooltip shows the right tank</b> (see {@code MixingVatScreen}).</li>
 * <li>The Forge handler takes any {@code FT_LFTRCoolant} salt, not just the vat's set fluid, and gives nothing: only the two
 *     proxies at the front ({@link com.leafia.contents.machines.processing.mixingvat.proxy.MixingVatProxy}) drain the output.</li>
 * </ol>
 */
public class MixingVatTE extends BlockEntityMachineBase implements LeafiaPacketReceiver, IEnergyReceiverMK2,
		IFluidHandler, IFFReceiver, IFFProvider {

	/** Upstream's {@code super(3 + 10 + 10)}. */
	public static final int SLOTS = 3 + 10 + 10;
	public static final int SLOT_BATTERY = 0;
	/** The first and last of the six ingredient slots. */
	public static final int FIRST_INGREDIENT = 15;
	public static final int LAST_INGREDIENT = 20;
	public static final int SLOT_IDENTIFIER = 21;
	public static final int SLOT_IDENTIFIER_DONE = 22;

	public static final int maxPower = 100_000;
	/** Ticks a batch takes. */
	public static int maxProgress = 20 * 3;
	/** Power a tick of a batch costs: {@code 1000 / 20}. */
	public static final int POWER_PER_TICK = 1000 / 20;
	/** What a batch moves, in mB. */
	public static final int BATCH = 1000;
	public static final int TANK = 4000;

	public long power;
	public int progress = 0;
	public final FluidTank tankNc0 = new FluidTank(TANK);
	public final FluidTank tankNc1 = new FluidTank(TANK);
	public FluidType inputTypeNc = LeafiaFluids.FLUORIDE;

	public float mixerRot = -1;
	public float prevRot = 0;

	/** Upstream's other mode never shipped: its screen and container have no machine slots. On by default, and never turned off. */
	public boolean nuclearMode = true;

	public MixingVatTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	public MixingVatTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.MIXING_VAT.get(), pos, state);
	}

	public long getProgressScaled(long i) {
		return (progress * i) / maxProgress;
	}

	public long getPowerRemainingScaled(long i) {
		return (power * i) / maxPower;
	}

	// ---- inventory -------------------------------------------------------------------------

	/** Upstream's {@code LCETileEntityMachineBase.isItemValidForSlot}: anything. The two output slots refuse by hand (their {@code SlotTakeOnly}), and automation reaches none. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	@Override
	public String getName() {
		return "tile.mixingvat.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new MixingVatMenu(containerId, playerInventory, this);
	}

	// ---- geometry --------------------------------------------------------------------------

	/**
	 * The way the machine faces: toward its front, the two-block depth with the output ports at the end. The block stores
	 * the opposite (the core's {@code DIR} is the back), and upstream's {@code getRotationMat(...).frontVector}
	 * is the same thing derived through a matrix (D-3016).
	 */
	public Direction getFacingForward() {
		return getBlockState().getValue(BlockDummyable.DIR).getOpposite();
	}

	public Direction getFacing() {
		return getBlockState().getValue(BlockDummyable.DIR);
	}

	/** The block behind the core, and behind the block to its right: where power and salt come in. */
	public BlockPos[] inputPorts() {
		Direction front = getFacingForward();
		return new BlockPos[] {
				worldPosition.relative(front.getOpposite()),
				worldPosition.relative(front.getClockWise()).relative(front.getOpposite())
		};
	}

	/** The block beside the left front corner and beside the right front corner: where the salt goes out. */
	public BlockPos[] outputPorts() {
		Direction front = getFacingForward();
		return new BlockPos[] {
				worldPosition.relative(front, 2).relative(front.getClockWise().getOpposite()),
				worldPosition.relative(front, 2).relative(front.getClockWise(), 2)
		};
	}

	// ---- what the client draws, here so a server can test it -------------------------------

	/** How full the drawn salt is: 1 in the slotless mode, else the greater of the two tanks. */
	public float fillLevel() {
		if(!nuclearMode) return 1F;
		return Math.max(tankNc0.getFluidAmount() / (float) tankNc0.getCapacity(),
				tankNc1.getFluidAmount() / (float) tankNc1.getCapacity());
	}

	/** The tint of the drawn salt as red, green and blue: white in the slotless mode, else the fluid's colour. */
	public float[] tint() {
		if(!nuclearMode) return new float[] { 1F, 1F, 1F };
		int color = inputTypeNc.getColor();
		if(color < 0) color = 0xFFFFFF;
		return new float[] { (color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F };
	}

	/**
	 * The tank whose contents the tooltip over the input ({@code left}) or the output shows. D-3076: upstream's screen
	 * called {@code renderTankInfo} twice with {@code tankNc1}, so hovering the input read the output's contents.
	 */
	public FluidTank tooltipTank(boolean left) {
		return left ? tankNc0 : tankNc1;
	}

	// ---- the fuels -------------------------------------------------------------------------

	/**
	 * The fuel a stack is, or null: first by item (every list is empty), then by the ore names of its tags in
	 * {@link MSRFuel}'s declaration order.
	 */
	@Nullable
	public static MSRFuel getFuelType(ItemStack stack) {
		if(stack.isEmpty()) return null;
		Item item = stack.getItem();
		for(MSRFuel fuel : MSRFuel.values()) {
			for(Item i : fuel.items)
				if(i.equals(item))
					return fuel;
		}
		for(MSRFuel fuel : MSRFuel.values()) {
			for(String dict : fuel.dicts)
				if(stack.is(NtmTags.ore(dict)))
					return fuel;
		}
		return null;
	}

	/** One of each fuel per slot holding it, whatever the stack size. */
	public Map<String, Double> createMixture() {
		Map<String, Double> mixture = new LeafiaMap<>();
		for(int i = FIRST_INGREDIENT; i <= LAST_INGREDIENT; i++) {
			ItemStack stack = slots[i];
			MSRFuel fuel = getFuelType(stack);
			if(fuel != null)
				mixture.put(fuel.name(), mixture.getOrDefault(fuel.name(), 0d) + 1);
		}
		return mixture;
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {

		if(level == null) return;

		if(!level.isClientSide) {

			Direction front = getFacingForward();
			Direction back = front.getOpposite();
			Direction left = front.getClockWise().getOpposite();
			Direction right = front.getClockWise();

			BlockPos[] in = inputPorts();
			BlockPos[] out = outputPorts();
			trySubscribe(level, in[0], back);
			trySubscribe(level, in[1], back);
			trySubscribe(level, out[0], left);
			trySubscribe(level, out[1], right);

			power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);

			mix();

			if(nuclearMode) {
				ItemStack filterStack = slots[SLOT_IDENTIFIER];
				if(!filterStack.isEmpty() && filterStack.getItem() instanceof IItemFluidIdentifier id) {
					FluidType type = id.getType(level, worldPosition, filterStack);
					if(type.hasTrait(FT_LFTRCoolant.class) && slots[SLOT_IDENTIFIER_DONE].isEmpty() && LeafiaFF.getFF(type) != null) {
						slots[SLOT_IDENTIFIER_DONE] = filterStack.copy();
						filterStack.shrink(1);
						if(filterStack.isEmpty()) slots[SLOT_IDENTIFIER] = ItemStack.EMPTY;
						inputTypeNc = type;
						var ff = LeafiaFF.getFF(type);
						if(!tankNc0.getFluid().isEmpty() && !ff.equals(tankNc0.getFluid().getFluid()))
							tankNc0.drain(9999, FluidAction.EXECUTE);
						if(!tankNc1.getFluid().isEmpty() && !ff.equals(tankNc1.getFluid().getFluid()))
							tankNc1.drain(9999, FluidAction.EXECUTE);
					}
				}
				tryProvide(tankNc1, level, out[0], left);
				tryProvide(tankNc1, level, out[1], right);
				trySubscribe(tankNc0, askStack(), level, in[0], back);
				trySubscribe(tankNc0, askStack(), level, in[1], back);
			}

			LeafiaPacket._start(this)
					.__write(0, power)
					.__write(1, nuclearMode)
					.__write(2, progress)
					.__write(6, tankNc0.writeToNBT(level.registryAccess(), new CompoundTag()))
					.__write(7, tankNc1.writeToNBT(level.registryAccess(), new CompoundTag()))
					.__write(8, inputTypeNc.getID())
					.__sendToAffectedClients();
			setChanged();
		} else {
			if(mixerRot == -1)
				mixerRot = level.random.nextFloat() * 360;
			prevRot = mixerRot;
			if(progress > 0) {
				this.mixerRot += 9F;
				if(this.mixerRot >= 360F) {
					this.mixerRot -= 360F;
					this.prevRot -= 360F;
				}
			}
		}
	}

	/** What a subscription asks a duct with when the input tank is empty: the set fluid, one millibucket (see {@link IFFReceiver}). */
	FluidStack askStack() {
		var ff = LeafiaFF.getFF(inputTypeNc);
		return ff == null ? FluidStack.EMPTY : new FluidStack(ff, 1);
	}

	/** One tick of a batch (class note). */
	public void mix() {
		FluidStack stack = tankNc0.getFluid();
		Map<String, Double> itemMixture = createMixture();
		if(!stack.isEmpty() && !itemMixture.isEmpty() && stack.getAmount() >= BATCH && power > POWER_PER_TICK) {
			FluidStack mixed = stack.copyWithAmount(BATCH);
			Map<String, Double> mixture = MSRTEBase.readMixture(MSRTEBase.nbtProtocol(mixed));
			for(Entry<String, Double> entry : itemMixture.entrySet())
				mixture.put(entry.getKey(), mixture.getOrDefault(entry.getKey(), 0d) + entry.getValue());
			CompoundTag tag = new CompoundTag();
			tag.put("itemMixture", MSRTEBase.writeMixture(mixture));
			LeafiaFF.setTag(mixed, tag);
			if(tankNc1.fill(mixed, FluidAction.SIMULATE) >= BATCH) {
				power -= POWER_PER_TICK;
				progress++;
				if(progress >= maxProgress) {
					progress = 0;
					tankNc1.fill(mixed, FluidAction.EXECUTE);
					tankNc0.drain(BATCH, FluidAction.EXECUTE);
					for(int i = FIRST_INGREDIENT; i <= LAST_INGREDIENT; i++) {
						if(getFuelType(slots[i]) != null) {
							slots[i].shrink(1);
							if(slots[i].isEmpty()) slots[i] = ItemStack.EMPTY;
						}
					}
				}
			} else
				progress = 0;
		} else
			progress = 0;
	}

	// ---- the Forge handler -----------------------------------------------------------------

	@Override
	public int getTanks() {
		return 2;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return tank == 0 ? tankNc0.getFluid() : tankNc1.getFluid();
	}

	@Override
	public int getTankCapacity(int tank) {
		return TANK;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return tank == 0 && nuclearMode && LeafiaFF.fromFF(stack).hasTrait(FT_LFTRCoolant.class);
	}

	/** Any {@code FT_LFTRCoolant} salt into the input, in nuclear mode; nothing else. */
	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if(resource.isEmpty() || !nuclearMode) return 0;
		FluidType ntmf = LeafiaFF.fromFF(resource);
		if(ntmf != Fluids.NONE && ntmf.hasTrait(FT_LFTRCoolant.class))
			return tankNc0.fill(resource, action);
		return 0;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return FluidStack.EMPTY;
	}

	/** What the output proxies give: the output tank, in nuclear mode. */
	public FluidStack drainOut(FluidStack resource, FluidAction action) {
		return nuclearMode ? tankNc1.drain(resource, action) : FluidStack.EMPTY;
	}

	public FluidStack drainOut(int maxDrain, FluidAction action) {
		return nuclearMode ? tankNc1.drain(maxDrain, action) : FluidStack.EMPTY;
	}

	@Override
	public FluidTank getSendingTank(FluidStack stack) {
		return tankNc1;
	}

	@Override
	public FluidTank getCorrespondingTank(FluidStack stack) {
		return tankNc0;
	}

	// ---- energy ----------------------------------------------------------------------------

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	// ---- persistence and sync --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putBoolean("nuclear", nuclearMode);
		nbt.putString("filter", inputTypeNc.getName());
		nbt.put("tankNc0", tankNc0.writeToNBT(registries, new CompoundTag()));
		nbt.put("tankNc1", tankNc1.writeToNBT(registries, new CompoundTag()));
		nbt.putInt("progress", progress);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		nuclearMode = nbt.getBoolean("nuclear");
		FluidType filter = Fluids.fromName(nbt.getString("filter"));
		if(filter != Fluids.NONE) inputTypeNc = filter;
		tankNc0.setFluid(FluidStack.EMPTY);
		tankNc1.setFluid(FluidStack.EMPTY);
		tankNc0.readFromNBT(registries, nbt.getCompound("tankNc0"));
		tankNc1.readFromNBT(registries, nbt.getCompound("tankNc1"));
		progress = nbt.getInt("progress");
	}

	@Override
	public String getPacketIdentifier() {
		return "MIXING_VAT";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> power = (long) value;
			case 1 -> nuclearMode = (boolean) value;
			case 2 -> progress = (int) value;
			case 6 -> {
				tankNc0.setFluid(FluidStack.EMPTY);
				tankNc0.readFromNBT(level.registryAccess(), (CompoundTag) value);
			}
			case 7 -> {
				tankNc1.setFluid(FluidStack.EMPTY);
				tankNc1.readFromNBT(level.registryAccess(), (CompoundTag) value);
			}
			case 8 -> inputTypeNc = Fluids.fromID((int) value);
			default -> { }
		}
	}

	/**
	 * The screen's one control: key 0 voids up to 5,000 mB of the output tank (all of it, which holds 4,000).
	 *
	 * <p>D-1094: upstream drains on any key-0 packet from anyone; here the sender must have this vat's screen open and be in reach.
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(!(plr != null && plr.containerMenu instanceof MixingVatMenu menu && menu.vat == this)) {
			PacketGuard.reject("MixingVatTE void", plr, "this vat's screen is not open");
			return;
		}
		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("MixingVatTE void", plr, "out of reach");
			return;
		}
		if(key == 0)
			tankNc1.drain(5000, FluidAction.EXECUTE);
		else
			PacketGuard.reject("MixingVatTE void", plr, "key " + key);
	}

	@Override
	public void onPlayerValidate(Player plr) { }

	// ---- rendering -------------------------------------------------------------------------

	private AABB bb = null;

	/** Upstream's box: two blocks either way around the core and two up, not turned for the facing. */
	public AABB getRenderBoundingBox() {
		if(bb == null) {
			bb = new AABB(worldPosition.getX() - 2, worldPosition.getY(), worldPosition.getZ() - 2,
					worldPosition.getX() + 3, worldPosition.getY() + 2, worldPosition.getZ() + 3);
		}
		return bb;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MixingVatTE vat) {
		vat.updateEntity();
	}
}
