package com.leafia.contents.machines.misc.heatex;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.util.DirPos;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;
import com.leafia.contents.machines.misc.heatex.container.CoolantHeatexMenu;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.pwr.PWRTicks;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The coolant heat exchanger: it takes the heat out of a hot fluid and boils a second coolant with it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/heatex/CoolantHeatexTE.java}.
 *
 * <h2>What it does</h2>
 *
 * Two circuits share one heat counter, {@link #heatEnergy}.
 *
 * <ul>
 * <li><b>The hot side (A)</b> takes an input fluid, cools it into the fluid its trait names and adds the heat it
 *     gave up. The fluid is set by the identifier in slot 0 (any fluid with a Forge counterpart that is either
 *     {@code FT_Coolable} for the heat exchanger, or an {@code FT_LFTRCoolant} - the molten salt) and the output is
 *     what it cools to; the salt leaves as itself, with its tag's heat taken out and counted as
 *     {@code 2 x heat} of the heat counter. Each cycle converts up to {@link #amountToCool} operations, every
 *     {@link #tickDelay} ticks, for {@link #powerConsumption} HE, and only while the counter is not already past
 *     {@code getHeatRequired() x capacity}.</li>
 * <li><b>The cold side (B)</b> is a coolant of three kinds - air, water or the base mod's coolant - set by
 *     the button, boiled by the heat counter into its hot form on every tick the heat is there: warm air,
 *     warm water or hot coolant. Air and water are not {@code FT_Heatable} for the heat exchanger, so two virtual traits
 *     stand in for them: <b>2 air and 50 TU make 3 warm air, 1 water and 70 TU make 1 warm water</b>.</li>
 * </ul>
 *
 * <p>Side A has two modes, switched by the other button: <b>FF</b>, the default, keeps the hot side in two Forge
 * tanks ({@link #ffInputA}, {@link #ffOutputA}) and talks to the Forge-fluid duct network, which is the only way
 * the salt (with its heat and fuel in the stack's tag) can arrive; <b>NTMF</b> keeps it in two ordinary
 * tanks and talks to the base mod's ducts. Side B is always ordinary tanks. The four tanks' capacity is
 * 2,400,000 mB each.
 *
 * <h2>Ports</h2>
 *
 * Six, one block up: at four blocks out on the machine's left and right, and at two blocks forward and back
 * on both of its two columns. Power and the cold side subscribe at every one of them each tick; side A
 * subscribes (FF or NTMF as the mode says) at every one, and everything it holds is offered at every one.
 *
 * <h2>Upstream's quirks, kept (D-021)</h2>
 *
 * <ol>
 * <li>(Fixed, D-3076) <b>A partly cooled tank of salt kept its heat only on paper</b>: upstream zeroed the input stack's own
 *     tag, so every millibucket left behind lost its heat; only the salt that leaves is cooled now. And the
 *     {@code int} product {@code heat x ops} wrapped; the counter stops at the int ceiling instead.</li>
 * <li><b>The heat counter is not saved</b> ({@code writeToNBT} has no line for it), so a reloaded exchanger
 *     starts cold: the heat a cycle gathered and the cold side had not yet used is lost with the chunk.</li>
 * <li><b>Draining is refused.</b> The Forge handler this block entity is takes input fluid (FF mode only, the set
 *     fluid only) and gives nothing back; only the duct network moves anything out.</li>
 * <li>The cold side's "heat required", which gates the hot side, divides the first step's heat by what it produces and
 *     multiplies by what it needs, so for air it is {@code 50 / 3 x 2}, not 50.</li>
 * </ol>
 */
public class CoolantHeatexTE extends BlockEntityMachineBase implements LeafiaPacketReceiver, IEnergyReceiverMK2,
		IFluidStandardReceiverMK2, IFluidStandardSenderMK2, IFFReceiver, IFFProvider, IFluidHandler {

	/** Upstream's {@code super(2, ...)}: the fluid identifier and a battery. */
	public static final int SLOTS = 2;
	public static final int SLOT_IDENTIFIER = 0;
	public static final int SLOT_BATTERY = 1;

	public long power;
	public static long powerConsumption = 5;
	public static long maxPower = 10_000;
	public float spin;
	public float lastSpin;
	public boolean ntmfMode = false;
	public int amountToCool = 24_000;
	public int tickDelay = 1;
	public int heatEnergy = 0;
	public int coolantMode = 2;

	public static final int capacity = 2_400_000;

	/** The cold side's coolant for a mode: air, water, the base mod's coolant. */
	public static FluidType coolant(int mode) {
		return switch(mode) {
			case 0 -> Fluids.AIR;
			case 1 -> Fluids.WATER;
			default -> Fluids.COOLANT;
		};
	}

	/** What the cold side boils to for a mode: warm air, warm water, hot coolant. */
	public static FluidType hotCoolant(int mode) {
		return switch(mode) {
			case 0 -> LeafiaFluids.HOT_AIR;
			case 1 -> LeafiaFluids.HOT_WATER;
			default -> Fluids.COOLANT_HOT;
		};
	}

	public FluidType inputFilter = LeafiaFluids.FLUORIDE;
	public FluidType outputFilter = LeafiaFluids.FLUORIDE;

	public net.neoforged.neoforge.fluids.capability.templates.FluidTank ffInputA = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(capacity);
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank ffOutputA = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(capacity);
	public FluidTank ntmfInputA = new FluidTank(inputFilter, capacity);
	public FluidTank ntmfOutputA = new FluidTank(outputFilter, capacity);
	public FluidTank ntmfInputB = new FluidTank(Fluids.COOLANT, capacity);
	public FluidTank ntmfOutputB = new FluidTank(Fluids.COOLANT_HOT, capacity);

	public CoolantHeatexTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
	}

	public CoolantHeatexTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.COOLANT_HEATEX.get(), pos, state);
	}

	// ---- inventory -------------------------------------------------------------------------

	/** Upstream's slots: the identifier takes anything by hand, the battery only a battery. Automation reaches none. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot == SLOT_IDENTIFIER || stack.getItem() instanceof api.hbm.energymk2.IBatteryItem;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	@Override
	public String getName() {
		return "container.coolant_heatex";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new CoolantHeatexMenu(containerId, playerInventory, this);
	}

	// ---- fluid types -----------------------------------------------------------------------

	/** Retypes the cold side's two tanks for the coolant mode. A tank already of the type is kept, with its contents. */
	public void updateCoolantMode() {
		FluidType input = coolant(coolantMode);
		FluidType output = hotCoolant(coolantMode);
		ntmfInputB = changeFluidType(ntmfInputB, input);
		ntmfOutputB = changeFluidType(ntmfOutputB, output);
	}

	public boolean isValidFluid(FluidType type) {
		FT_Coolable coolable = getCoolable(type);
		return (coolable != null) || type.hasTrait(FT_LFTRCoolant.class);
	}

	/**
	 * Retypes the hot side to {@link #inputFilter}, if it is not already: the output becomes what the input cools
	 * to (a salt cools to itself), and the tanks of the other type are replaced (ordinary) or emptied (Forge).
	 * A fluid that is neither a salt nor coolable for the heat exchanger is refused and nothing changes.
	 */
	public void updateResource() {
		if(!ntmfInputA.getTankType().equals(inputFilter)) {
			FT_Coolable coolable = getCoolable(inputFilter);
			if(inputFilter.hasTrait(FT_LFTRCoolant.class))
				outputFilter = inputFilter;
			else if(coolable != null) {
				if(coolable.getEfficiency(CoolingType.HEATEXCHANGER) > 0)
					outputFilter = coolable.coolsTo;
				else
					return;
			} else
				return;
			ntmfInputA = changeFluidType(ntmfInputA, inputFilter);
			ntmfOutputA = changeFluidType(ntmfOutputA, outputFilter);
			changeFluidType(ffInputA, inputFilter);
			changeFluidType(ffOutputA, outputFilter);
		}
	}

	/** Empties a Forge tank holding anything but {@code type}. */
	public void changeFluidType(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank, FluidType type) {
		if(tank.getFluid().isEmpty()) return;
		FluidType currentType = LeafiaFF.fromFF(tank.getFluid());
		if(!currentType.equals(type))
			tank.drain(capacity * 2, IFluidHandler.FluidAction.EXECUTE);
	}

	public FluidTank changeFluidType(FluidTank tank, FluidType type) {
		if(!tank.getTankType().equals(type))
			return new FluidTank(type, tank.getMaxFill());
		return tank;
	}

	// ---- ports -----------------------------------------------------------------------------

	/**
	 * Upstream's six, one block up: four blocks out left and right, and two out forward and back on both columns.
	 * {@code ForgeDirection.getRotation(UP)} is {@link Direction#getClockWise}.
	 */
	public DirPos[] getConPos() {

		Direction dir = getBlockState().getValue(BlockDummyable.DIR);
		Direction rot = dir.getClockWise();
		BlockPos pos = worldPosition;

		return new DirPos[] {
				new DirPos(pos.getX() + rot.getStepX() * 4, pos.getY() + 1, pos.getZ() + rot.getStepZ() * 4, rot),
				new DirPos(pos.getX() - rot.getStepX() * 4, pos.getY() + 1, pos.getZ() - rot.getStepZ() * 4, rot.getOpposite()),
				new DirPos(pos.getX() + dir.getStepX() * 2 - rot.getStepX(), pos.getY() + 1, pos.getZ() + dir.getStepZ() * 2 - rot.getStepZ(), dir),
				new DirPos(pos.getX() + dir.getStepX() * 2 + rot.getStepX(), pos.getY() + 1, pos.getZ() + dir.getStepZ() * 2 + rot.getStepZ(), dir),
				new DirPos(pos.getX() - dir.getStepX() * 2 - rot.getStepX(), pos.getY() + 1, pos.getZ() - dir.getStepZ() * 2 - rot.getStepZ(), dir.getOpposite()),
				new DirPos(pos.getX() - dir.getStepX() * 2 + rot.getStepX(), pos.getY() + 1, pos.getZ() - dir.getStepZ() * 2 + rot.getStepZ(), dir.getOpposite())
		};
	}

	/** What a subscription asks a duct with when the Forge input tank is empty: the set fluid, one millibucket (see {@link IFFReceiver}). */
	FluidStack askStack() {
		var ff = LeafiaFF.getFF(inputFilter);
		return ff == null ? FluidStack.EMPTY : new FluidStack(ff, 1);
	}

	public void subscribeToAllAround() {
		for(DirPos dirPos : getConPos()) {
			trySubscribe(level, dirPos);
			trySubscribe(coolant(coolantMode), level, dirPos);
			if(ntmfMode)
				trySubscribe(inputFilter, level, dirPos);
			else
				trySubscribe(ffInputA, askStack(), level, dirPos, dirPos.getDir());
		}
	}

	public void sendFluidToAll() {
		for(DirPos dirPos : getConPos()) {
			tryProvide(ntmfOutputB, level, dirPos);
			if(ntmfMode)
				tryProvide(ntmfOutputA, level, dirPos);
			else
				tryProvide(ffOutputA, level, dirPos, dirPos.getDir());
		}
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {

		if(level == null) return;

		if(!level.isClientSide) {
			this.power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);

			ItemStack filterStack = slots[SLOT_IDENTIFIER];
			if(!filterStack.isEmpty() && filterStack.getItem() instanceof IItemFluidIdentifier id) {
				FluidType type = id.getType(level, worldPosition, filterStack);
				if(isValidFluid(type) && LeafiaFF.getFF(type) != null) {
					inputFilter = type;
					updateResource();
				}
			}

			subscribeToAllAround();

			if(heatEnergy <= getHeatRequired() * capacity) {
				if(power >= powerConsumption) {
					power -= powerConsumption;
					if(ntmfMode)
						tryConvert();
					else
						tryConvertFF();
				}
			}

			tryConvertB();
			sendFluidToAll();

			CompoundTag fluidTags = new CompoundTag();
			ntmfInputB.writeToNBT(fluidTags, "ntmf_inputB");
			ntmfOutputB.writeToNBT(fluidTags, "ntmf_outputB");
			if(ntmfMode) {
				ntmfInputA.writeToNBT(fluidTags, "ntmf_inputA");
				ntmfOutputA.writeToNBT(fluidTags, "ntmf_outputA");
			} else {
				fluidTags.put("ff_inputA", ffInputA.writeToNBT(level.registryAccess(), new CompoundTag()));
				fluidTags.put("ff_outputA", ffOutputA.writeToNBT(level.registryAccess(), new CompoundTag()));
			}

			LeafiaPacket._start(this)
					.__write(0, power)
					.__write(1, ntmfMode)
					.__write(2, coolantMode)
					.__write(3, fluidTags)
					.__write(4, inputFilter.getID())
					.__write(5, outputFilter.getID())
					.__write(6, amountToCool)
					.__write(7, tickDelay)
					.__sendToAffectedClients();
			setChanged();
		} else {
			this.lastSpin = this.spin;
			if(power >= powerConsumption) {
				this.spin += 15F;
				if(this.spin >= 360F) {
					this.spin -= 360F;
					this.lastSpin -= 360F;
				}
			}
		}
	}

	/** The screen's number boxes send this: upstream's {@code Math.max(NumberUtils.toInt(text), 1)}, so anything that is not a number is a one. */
	public static int parseCount(String text) {
		int n;
		try {
			n = Integer.parseInt(text.trim());
		} catch(NumberFormatException e) {
			n = 0;
		}
		return Math.max(n, 1);
	}

	// ---- the conversions -------------------------------------------------------------------

	/** Built on first use: their product is the addon's warm fluid, which a static initialiser could read before it exists. */
	private static FT_Heatable virtualAir;
	private static FT_Heatable virtualWater;
	private static FT_Heatable virtualCoolant;
	private static FT_Heatable virtualCoolantOf;
	static final FT_Coolable VIRTUAL_LPS = new FT_Coolable(Fluids.WATER, 1, 1, 20).setEff(CoolingType.HEATEXCHANGER, 1.0D);

	/**
	 * Air and water as the cold side boils them (the addon's warm air and warm water), which the base mod
	 * gives no heating step for; everything else asks the fluid's own trait.
	 *
	 */
	public static synchronized FT_Heatable getHeatable(FluidType fluid) {
		if(fluid.equals(Fluids.AIR)) {
			if(virtualAir == null) virtualAir = new FT_Heatable().addStep(50, 2, LeafiaFluids.HOT_AIR, 3).setEff(HeatingType.BOILER, 1.0D);
			return virtualAir;
		}
		if(fluid.equals(Fluids.WATER)) {
			if(virtualWater == null) virtualWater = new FT_Heatable().addStep(70, 1, LeafiaFluids.HOT_WATER, 1).setEff(HeatingType.BOILER, 1.0D);
			return virtualWater;
		}
		if(fluid.equals(Fluids.COOLANT)) {
			// D-3076/D-3077: the base table now gives the default coolant a boiler efficiency itself (D-2291), so this only fills in for a
			// table that lacks one (a pack that rewrote the traits): it boils on its own first step (300 heat, 1 mB to 1 mB of hot coolant).
			FT_Heatable own = fluid.getTrait(FT_Heatable.class);
			if(own != null && own.getEfficiency(HeatingType.BOILER) <= 0 && own.getFirstStep() != null) {
				if(virtualCoolant == null || virtualCoolantOf != own) {
					HeatingStep step = own.getFirstStep();
					virtualCoolant = new FT_Heatable().addStep(step.heatReq, step.amountReq, step.typeProduced, step.amountProduced).setEff(HeatingType.BOILER, 1.0D);
					virtualCoolantOf = own;
				}
				return virtualCoolant;
			}
			return own;
		}
		return fluid.getTrait(FT_Heatable.class);
	}

	/** Spent steam stands in as a coolable fluid (1 mB to 1 mB of water, 20 heat); otherwise the fluid's own trait, if it is for the heat exchanger. */
	public static FT_Coolable getCoolable(FluidType fluid) {
		if(fluid.equals(Fluids.SPENTSTEAM))
			return VIRTUAL_LPS;
		if(fluid.hasTrait(FT_Coolable.class)) {
			FT_Coolable trait = fluid.getTrait(FT_Coolable.class);
			if(trait.getEfficiency(CoolingType.HEATEXCHANGER) > 0)
				return trait;
		}
		return null;
	}

	/** {@code heatReq / amountProduced x amountReq} of the cold side's first step. */
	public double getHeatRequired() {
		FT_Heatable heatable = getHeatable(coolant(coolantMode));
		return heatable.getFirstStep().heatReq / (double) heatable.getFirstStep().amountProduced * heatable.getFirstStep().amountReq;
	}

	/** Side B: boil as much coolant as the input, the output room and the heat counter allow. */
	public void tryConvertB() {
		FT_Heatable trait = getHeatable(coolant(coolantMode));
		if(trait.getEfficiency(HeatingType.BOILER) > 0) {

			HeatingStep entry = trait.getFirstStep();
			int inputOps = ntmfInputB.getFill() / entry.amountReq;
			int outputOps = (ntmfOutputB.getMaxFill() - ntmfOutputB.getFill()) / entry.amountProduced;
			int heatOps = this.heatEnergy / entry.heatReq;

			int ops = Math.min(inputOps, Math.min(outputOps, heatOps));

			ntmfInputB.setFill(ntmfInputB.getFill() - entry.amountReq * ops);
			ntmfOutputB.setFill(ntmfOutputB.getFill() + entry.amountProduced * ops);
			heatEnergy -= entry.heatReq * ops;
		}
	}

	/** The heat counter is an int: a cycle that would carry it past that stops at the ceiling (D-3076; upstream multiplied two ints and wrapped to a small or negative number). */
	private void addHeat(double gained) {
		heatEnergy = (int) Math.max(Math.min((double) heatEnergy + gained, Integer.MAX_VALUE), Integer.MIN_VALUE);
	}

	/** Side A in NTMF mode: ordinary tanks. */
	public void tryConvert() {
		FT_Coolable trait = getCoolable(inputFilter);
		if(trait == null) return;
		if(tickDelay < 1) tickDelay = 1;
		if(level.getGameTime() % tickDelay != 0) return;

		int inputOps = ntmfInputA.getFill() / trait.amountReq;
		int outputOps = (ntmfOutputA.getMaxFill() - ntmfOutputA.getFill()) / trait.amountProduced;
		int opCap = this.amountToCool;

		int ops = Math.min(inputOps, Math.min(outputOps, opCap));
		ntmfInputA.setFill(ntmfInputA.getFill() - trait.amountReq * ops);
		ntmfOutputA.setFill(ntmfOutputA.getFill() + trait.amountProduced * ops);
		addHeat((long) trait.heatEnergy * ops * trait.getEfficiency(CoolingType.HEATEXCHANGER));
		this.setChanged();
	}

	/** Side A in FF mode: Forge tanks, and the salt's own heat. */
	public void tryConvertFF() {
		if(tickDelay < 1) tickDelay = 1;
		if(level.getGameTime() % tickDelay != 0) return;

		int amountReq = 0;
		int amountProduced = 0;
		int heat = 0;
		double efficiency = 1;
		var outFF = LeafiaFF.getFF(outputFilter);
		FluidStack outStack = !ffOutputA.getFluid().isEmpty() ? ffOutputA.getFluid()
				: outFF == null ? FluidStack.EMPTY : new FluidStack(outFF, 1);

		FT_Coolable trait = getCoolable(inputFilter);
		if(trait != null) {
			amountReq = trait.amountReq;
			amountProduced = trait.amountProduced;
			heat = trait.heatEnergy;
			efficiency = trait.getEfficiency(CoolingType.HEATEXCHANGER);
		} else if(inputFilter.hasTrait(FT_LFTRCoolant.class)) {
			amountProduced = 1;
			amountReq = 1;
			FluidStack stack = ffInputA.getFluid();
			if(!stack.isEmpty()) {
				// D-3076: the heat is taken out of the salt that leaves, not out of the tank (upstream's nbtProtocol handed
				// back the input stack's own tag and zeroed it, so the millibuckets left behind lost their heat as well).
				CompoundTag compound = MSRTEBase.nbtProtocol(stack);
				double h = compound.getDouble("heat");
				compound.putDouble("heat", 0);
				heat = (int) (h * 2);
				outStack = stack.copy();
				LeafiaFF.setTag(outStack, compound);
			} else
				return;
		}

		if(amountReq <= 0 || amountProduced <= 0) return;

		int inputOps = ffInputA.getFluidAmount() / amountReq;
		int outputOps = (ffOutputA.getCapacity() - ffOutputA.getFluidAmount()) / amountProduced;
		int opCap = this.amountToCool;

		int ops = Math.min(inputOps, Math.min(outputOps, opCap));
		ffInputA.drain(amountReq * ops, IFluidHandler.FluidAction.EXECUTE);

		FluidStack produced = outStack.isEmpty() ? FluidStack.EMPTY : outStack.copyWithAmount(amountProduced * ops);
		if(!produced.isEmpty()) {
			var holder = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(produced.getAmount());
			holder.setFluid(produced);
			LeafiaUtil.fillFF(holder, ffOutputA, amountProduced * ops);
		}
		addHeat((long) heat * ops * efficiency);
		this.setChanged();
	}

	// ---- the Forge handler -----------------------------------------------------------------

	@Override
	public int getTanks() {
		return 2;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return tank == 0 ? ffInputA.getFluid() : ffOutputA.getFluid();
	}

	@Override
	public int getTankCapacity(int tank) {
		return capacity;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return tank == 0 && !ntmfMode && LeafiaFF.fromFF(stack).equals(inputFilter);
	}

	/** Only the set input fluid, only in FF mode. */
	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if(resource.isEmpty()) return 0;
		if(!ntmfMode) {
			FluidType ntmf = LeafiaFF.fromFF(resource);
			if(ntmf.equals(inputFilter))
				return ffInputA.fill(resource, action);
		}
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

	@Override
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank getCorrespondingTank(FluidStack stack) {
		FluidType type = LeafiaFF.fromFF(stack);
		if(inputFilter.equals(type))
			return ffInputA;
		return null;
	}

	@Override
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank getSendingTank(FluidStack stack) {
		return ffOutputA;
	}

	// ---- the base mod's interfaces ---------------------------------------------------------

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

	@Override
	public FluidTank[] getReceivingTanks() {
		if(ntmfMode)
			return new FluidTank[] { ntmfInputA, ntmfInputB };
		return new FluidTank[] { ntmfInputB };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		if(ntmfMode)
			return new FluidTank[] { ntmfOutputA, ntmfOutputB };
		return new FluidTank[] { ntmfOutputB };
	}

	@Override
	public FluidTank[] getAllTanks() {
		if(ntmfMode)
			return new FluidTank[] { ntmfInputA, ntmfInputB, ntmfOutputA, ntmfOutputB };
		return new FluidTank[] { ntmfInputB, ntmfOutputB };
	}

	// ---- persistence and sync --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putLong("power", power);
		nbt.putBoolean("ntmfMode", ntmfMode);
		nbt.putInt("coolantMode", coolantMode);
		ntmfInputA.writeToNBT(nbt, "ntmf_inputA");
		ntmfOutputA.writeToNBT(nbt, "ntmf_outputA");
		ntmfInputB.writeToNBT(nbt, "ntmf_inputB");
		ntmfOutputB.writeToNBT(nbt, "ntmf_outputB");
		nbt.put("ff_inputA", ffInputA.writeToNBT(registries, new CompoundTag()));
		nbt.put("ff_outputA", ffOutputA.writeToNBT(registries, new CompoundTag()));
		nbt.putString("inputFilter", inputFilter.getName());
		nbt.putInt("cycle", amountToCool);
		nbt.putInt("delay", tickDelay);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		if(nbt.contains("power")) power = nbt.getLong("power");
		if(nbt.contains("ntmfMode")) ntmfMode = nbt.getBoolean("ntmfMode");
		if(nbt.contains("coolantMode")) coolantMode = nbt.getInt("coolantMode");
		if(nbt.contains("inputFilter")) {
			FluidType f = Fluids.fromName(nbt.getString("inputFilter"));
			if(LeafiaFF.getFF(f) != null)
				inputFilter = f;
		}
		updateResource();
		updateCoolantMode();
		if(nbt.contains("ntmf_inputB")) {
			ntmfInputB.readFromNBT(nbt, "ntmf_inputB");
			ntmfOutputB.readFromNBT(nbt, "ntmf_outputB");
		}
		if(nbt.contains("ntmf_inputA")) {
			ntmfInputA.readFromNBT(nbt, "ntmf_inputA");
			ntmfOutputA.readFromNBT(nbt, "ntmf_outputA");
		}
		if(nbt.contains("ff_inputA")) {
			ffInputA.readFromNBT(registries, nbt.getCompound("ff_inputA"));
			ffOutputA.readFromNBT(registries, nbt.getCompound("ff_outputA"));
		}
		if(nbt.contains("cycle")) amountToCool = nbt.getInt("cycle");
		if(nbt.contains("delay")) tickDelay = nbt.getInt("delay");
	}

	@Override
	public String getPacketIdentifier() {
		return "C_HEATEX";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> power = (long) value;
			case 1 -> ntmfMode = (boolean) value;
			case 2 -> coolantMode = (int) value;
			case 3 -> {
				CompoundTag tag = (CompoundTag) value;
				ntmfInputB.readFromNBT(tag, "ntmf_inputB");
				ntmfOutputB.readFromNBT(tag, "ntmf_outputB");
				if(tag.contains("ntmf_inputA")) {
					ntmfInputA.readFromNBT(tag, "ntmf_inputA");
					ntmfOutputA.readFromNBT(tag, "ntmf_outputA");
				}
				if(tag.contains("ff_inputA")) {
					var access = level.registryAccess();
					ffInputA.setFluid(FluidStack.EMPTY);
					ffOutputA.setFluid(FluidStack.EMPTY);
					ffInputA.readFromNBT(access, tag.getCompound("ff_inputA"));
					ffOutputA.readFromNBT(access, tag.getCompound("ff_outputA"));
				}
			}
			case 4 -> inputFilter = Fluids.fromID((int) value);
			case 5 -> outputFilter = Fluids.fromID((int) value);
			case 6 -> amountToCool = (int) value;
			case 7 -> tickDelay = (int) value;
			default -> { }
		}
	}

	/**
	 * The screen's three controls: key 0 flips the FF/NTMF switch ({@code false}) or steps the coolant ({@code true},
	 * air, water, coolant, applied at the start of the next server tick as upstream queued it), and keys 1 and 2
	 * set the cycle size and the delay.
	 *
	 * <p>D-1094: the sender must have this exchanger's screen open and be in reach, and a number must be an
	 * {@code int} of at least one - the screen clamps to one, and a cycle of zero or less would run the conversion
	 * backwards (negative operations take fluid from the output and give it to the input).
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(!(plr != null && plr.containerMenu instanceof CoolantHeatexMenu menu && menu.heatex == this)) {
			PacketGuard.reject("CoolantHeatexTE", plr, "this exchanger's screen is not open");
			return;
		}
		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("CoolantHeatexTE", plr, "out of reach");
			return;
		}
		switch(key) {
			case 0 -> {
				if(!(value instanceof Boolean coolantButton)) {
					PacketGuard.reject("CoolantHeatexTE", plr, "key 0 is a " + (value == null ? "null" : value.getClass().getSimpleName()));
					return;
				}
				if(coolantButton) {
					PWRTicks.queueFunction(() -> {
						coolantMode = Math.floorMod(coolantMode + 1, 3);
						updateCoolantMode();
					});
				} else
					ntmfMode = !ntmfMode;
			}
			case 1, 2 -> {
				if(!(value instanceof Integer n) || n < 1) {
					PacketGuard.reject("CoolantHeatexTE", plr, "key " + key + " is " + value);
					return;
				}
				if(key == 1) amountToCool = n;
				else tickDelay = n;
			}
			default -> PacketGuard.reject("CoolantHeatexTE", plr, "key " + key);
		}
	}

	@Override
	public void onPlayerValidate(Player plr) { }

	// ---- rendering -------------------------------------------------------------------------

	private AABB bb = null;

	/** Upstream's box: three blocks either way around the core and three up, not turned for the facing. */
	public AABB getRenderBoundingBox() {
		if(bb == null) {
			bb = new AABB(worldPosition.getX() - 3, worldPosition.getY(), worldPosition.getZ() - 3,
					worldPosition.getX() + 4, worldPosition.getY() + 3, worldPosition.getZ() + 4);
		}
		return bb;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CoolantHeatexTE heatex) {
		heatex.updateEntity();
	}
}
