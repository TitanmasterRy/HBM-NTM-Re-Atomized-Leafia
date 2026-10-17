package com.leafia.contents.machines.reactors.lftr.processing.separator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.lib.Library;
import com.hbm.util.BobMathUtil;
import com.hbm.util.DirPos;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.machines.reactors.lftr.processing.separator.container.SaltSeparatorMenu;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipe;
import com.leafia.contents.machines.reactors.lftr.processing.separator.recipes.SaltSeparatorRecipes;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The salt fuel separation plant's brain: a LFTR fluorination machine that takes the uranium out of a
 * molten salt and sends the rest on.
 *
 * <p>Upstream: {@code .../lftr/processing/separator/SaltSeparatorTE.java}.
 *
 * <h2>The machine in one paragraph</h2>
 *
 * Spent salt arrives from Forge ducts into {@link #bufferIn} and is passed straight on to
 * {@link #bufferOut} every tick (the machine never holds it). Fresh FLiBe arrives from ordinary NTM
 * pipes into {@link #concIn} and tops the {@link #saltTank} up to the recipe's {@code saltAmount}
 * (1,000 mB for both rows, so the tank is resized to that). Once the salt tank is <b>full</b>, each
 * tick {@link #lastResortFuckingIdea} moves the recipe's isotope out of the passing spent salt and
 * into the salt tank's mixture, up to the recipe's amount per bucket. When the salt tank holds the
 * recipe's mixture <em>exactly</em> the module runs: 500 mB of fluorine in, the salt out of the tank,
 * 1,000 mB of FLiBe and 300 mB of the isotope's UF6 into the NTM output tanks.
 *
 * <h2>Upstream's quirks, kept (D-021) and pinned in {@code LeafiaSaltSeparatorTests}</h2>
 *
 * <ul>
 * <li><b>The upgrade slots do nothing.</b> The menu has two, and the tick has the arithmetic for speed,
 *     power and overdrive, but {@code UpgradeManagerNT.checkSlots} is never called and the machine
 *     is not an {@code IUpgradeInfoProvider}, so every level is zero: speed 1 and power 1 always.</li>
 * <li><b>The pass-through destroys what does not fit.</b> {@code fillFF(bufferIn, bufferOut, all of
 *     bufferIn)} drains the sender by the full amount and fills the receiver by at most its room, so a
 *     jammed output buffer eats the difference. Duct traffic cannot reach it (the network never asks for
 *     more than a room), but this call does.</li>
 * <li><b>The mixture has to land exactly.</b> The extraction subtracts and adds doubles, and the module
 *     compares with {@code Double.equals}.</li>
 * <li><b>The concentrate-out tank is never filled.</b> {@link #concOut} is offered to the middle ports
 *     every tick and nothing ever puts anything in it.</li>
 * </ul>
 *
 * <h2>What does not port</h2>
 *
 * Three methods upstream has no caller for - {@code getExtractionAmount}, {@code transferToTank} and
 * {@code idRatherStartOverThisShitSoHereWeAre}, the author's earlier attempts, whose calls are
 * commented out in {@code update} - are not here. {@code getTankProperties} was an empty array
 * upstream; {@link #getTanks} answers 0 likewise, so another mod sees fill and drain and no tanks.
 * The Forge item capability is the base mod's WorldlyContainer, as for every machine.
 */
public class SaltSeparatorTE extends BlockEntityMachineBase implements IEnergyReceiverMK2, IControlReceiver,
		LeafiaPacketReceiver, IFluidStandardReceiverMK2, IFluidStandardSenderMK2, IFFReceiver, IFFProvider {

	/** Upstream's {@code super(14)}: battery, blueprint, two upgrades, then the five tank slot pairs. */
	public static final int SLOTS = 14;

	public SaltSeparatorModule module;
	public long maxPower = 100_000;
	public long power = 0;
	public boolean didProcess = false;
	public FluidType saltType = Fluids.NONE;
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank saltTank = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(12000);
	public FluidTank[] inputTanks = new FluidTank[] {
			new FluidTank(Fluids.NONE, 12000),
			new FluidTank(Fluids.NONE, 12000)
	};
	public FluidTank[] outputTanks = new FluidTank[] {
			new FluidTank(Fluids.NONE, 12000),
			new FluidTank(Fluids.NONE, 12000),
			new FluidTank(Fluids.NONE, 12000)
	};
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank bufferIn = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(12000);
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank bufferOut = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(12000);
	public FluidTank concIn = new FluidTank(Fluids.NONE, 12000);
	public FluidTank concOut = new FluidTank(Fluids.NONE, 12000);
	public UpgradeManagerNT upgradeManager = new UpgradeManagerNT(this);

	public SaltSeparatorTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
		module = new SaltSeparatorModule(0, this, slots)
				.fluidInput(inputTanks[0], inputTanks[1])
				.fluidOutput(outputTanks[0], outputTanks[1], outputTanks[2])
				.saltTank(saltTank);
	}

	/**
	 * Takes the recipe's isotope out of the passing salt and into the salt tank's mixture.
	 *
	 * <p>Upstream's name for it is kept. The salt tank is full when this runs; {@code ratio} is how much
	 * more salt is passing than the tank holds, and for each isotope the recipe wants and the passing salt
	 * has, the tank takes {@code min(the passing salt's amount, shortfall / ratio) x ratio} of it and the
	 * passing salt loses {@code min(...)}. Both tags are rewritten whether or not anything moved, so the
	 * passing salt gains the protocol's keys here.
	 */
	public void lastResortFuckingIdea(Map<MSRFuel, Double> recipe) {
		FluidStack inFluid = bufferIn.getFluid();
		FluidStack saltFluid = saltTank.getFluid();
		if(inFluid.isEmpty() || saltFluid.isEmpty()) return;
		if(inFluid.getAmount() <= 0) return;
		CompoundTag inMixtureNBT = MSRTEBase.nbtProtocol(inFluid);
		Map<String, Double> inMixture = MSRTEBase.readMixture(inMixtureNBT);
		CompoundTag saltMixtureNBT = MSRTEBase.nbtProtocol(saltFluid);
		Map<String, Double> saltMixture = MSRTEBase.readMixture(saltMixtureNBT);
		Map<String, Double> desiredMixture = new HashMap<>();
		for(Entry<MSRFuel, Double> entry : recipe.entrySet())
			desiredMixture.put(entry.getKey().name(), entry.getValue());
		double ratio = inFluid.getAmount() / (double) saltFluid.getAmount();
		// Upstream edits inMixture while walking it (its LeafiaMap allows that); walk a copy.
		for(Entry<String, Double> entry : new ArrayList<>(new LinkedHashMap<>(inMixture).entrySet())) {
			if(desiredMixture.containsKey(entry.getKey())) {
				double demand = desiredMixture.getOrDefault(entry.getKey(), 0d) - saltMixture.getOrDefault(entry.getKey(), 0d);
				if(demand > 0) {
					double transfer = Math.min(entry.getValue(), demand / ratio);
					inMixture.put(entry.getKey(), entry.getValue() - transfer);
					saltMixture.put(entry.getKey(), saltMixture.getOrDefault(entry.getKey(), 0d) + transfer * ratio);
				}
			}
		}
		LeafiaFF.setTag(inFluid, MSRTEBase.writeMixture(inMixture, inMixtureNBT));
		LeafiaFF.setTag(saltFluid, MSRTEBase.writeMixture(saltMixture, saltMixtureNBT));
	}

	@Override
	public void updateEntity() {
		if(level == null || level.isClientSide) return;

		SaltSeparatorRecipe recipe = SaltSeparatorRecipes.INSTANCE.recipeNameMap.get(module.getRecipeName());
		if(recipe != null)
			this.maxPower = recipe.power * 100;
		this.maxPower = BobMathUtil.max(this.power, this.maxPower, 100_000);
		this.power = Library.chargeTEFromItems(slots, 0, power, maxPower);
		HolderLookup.Provider registries = level.registryAccess();
		if(recipe != null && recipe.inputFluid != null) {
			for(int i = 0; i < Math.min(2, recipe.inputFluid.length); i++)
				inputTanks[i].loadTank(4 + i, 6 + i, slots, registries);
		}
		outputTanks[0].unloadTank(8, 11, slots, registries);
		outputTanks[1].unloadTank(9, 12, slots, registries);
		outputTanks[2].unloadTank(10, 13, slots, registries);

		Fluid ffSalt = LeafiaFF.getFF(saltType);

		for(DirPos con : getConPos()) {
			trySubscribe(level, con);
			for(FluidTank tank : inputTanks) {
				if(tank.getTankType() != Fluids.NONE)
					trySubscribe(tank.getTankType(), level, con);
			}
			for(FluidTank tank : outputTanks) {
				if(tank.getFill() > 0)
					tryProvide(tank, level, con);
			}
			if(ffSalt != null) {
				// A stack of 0 mB is empty and answers no fluid, so the stand-in is 1 (IFFReceiver).
				trySubscribe(bufferIn, new FluidStack(ffSalt, 1), level, con, con.getDir());
				tryProvide(bufferOut, level, con, con.getDir());
			}
		}

		for(DirPos con : getConPos2()) {
			if(saltType != Fluids.NONE) {
				trySubscribe(saltType, level, con);
				tryProvide(concOut, level, con);
			}
		}

		double speed = 1D;
		double pow = 1D;

		speed += Math.min(upgradeManager.getLevel(ItemMachineUpgrade.UpgradeType.SPEED), 3) / 3D;
		speed += Math.min(upgradeManager.getLevel(ItemMachineUpgrade.UpgradeType.OVERDRIVE), 3);

		pow -= Math.min(upgradeManager.getLevel(ItemMachineUpgrade.UpgradeType.POWER), 3) * 0.25D;
		pow += Math.min(upgradeManager.getLevel(ItemMachineUpgrade.UpgradeType.SPEED), 3) * 1D;
		pow += Math.min(upgradeManager.getLevel(ItemMachineUpgrade.UpgradeType.OVERDRIVE), 3) * 10D / 3D;

		SaltSeparatorRecipe curRecipe = this.module.getRecipe();
		saltType = (curRecipe != null) ? curRecipe.saltType : Fluids.NONE;
		concIn.setTankType(saltType);
		concOut.setTankType(saltType);
		if(curRecipe != null) {
			if(saltTank.getCapacity() != curRecipe.saltAmount) {
				if(saltTank.getFluidAmount() > curRecipe.saltAmount)
					saltTank.drain(saltTank.getFluidAmount() - curRecipe.saltAmount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
				saltTank.setCapacity(curRecipe.saltAmount);
			}
			if(saltTank.getFluidAmount() <= 0)
				saltTank.setFluid(FluidStack.EMPTY);
			int demand = saltTank.getCapacity() - saltTank.getFluidAmount();
			if(demand > 0) {
				int transfer = Math.min(demand, concIn.getFill());
				concIn.setFill(concIn.getFill() - transfer);
				if(!saltTank.getFluid().isEmpty())
					saltTank.getFluid().grow(transfer);
				else
					saltTank.fill(new FluidStack(LeafiaFF.getFF(saltType), transfer), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
			} else
				lastResortFuckingIdea(curRecipe.mixture);
			LeafiaUtil.fillFF(bufferIn, bufferOut, bufferIn.getFluidAmount());
		}

		this.module.update(speed, pow, true, slots[1]);
		this.didProcess = this.module.didProcess;
		if(this.module.markDirty) this.markChanged();

		LeafiaPacket._start(this)
				.__write(0, this.module.getRecipeName())
				.__write(1, tankData(registries))
				.__write(2, saltType.getID())
				.__write(3, module.progress)
				.__write(4, power)
				.__write(5, didProcess)
				.__write(6, concIn.getFill())
				.__write(7, concOut.getFill())
				.__write(8, saltTank.getCapacity())
				.__sendToAffectedClients();
	}

	/** The five NTM tanks and the three Forge ones, as the sync packet carries them (key 1). */
	public CompoundTag tankData(HolderLookup.Provider registries) {
		CompoundTag tankData = new CompoundTag();
		for(int i = 0; i < inputTanks.length; i++)
			inputTanks[i].writeToNBT(tankData, "in" + i);
		for(int i = 0; i < outputTanks.length; i++)
			outputTanks[i].writeToNBT(tankData, "out" + i);
		tankData.put("salt", saltTank.writeToNBT(registries, new CompoundTag()));
		tankData.put("bufIn", bufferIn.writeToNBT(registries, new CompoundTag()));
		tankData.put("bufOut", bufferOut.writeToNBT(registries, new CompoundTag()));
		return tankData;
	}

	/** Upstream's {@code isItemValidForSlot} is the base machine's default, true for every slot. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	/// PERSISTENCE ///

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		for(int i = 0; i < inputTanks.length; i++)
			inputTanks[i].writeToNBT(compound, "in" + i);
		for(int i = 0; i < outputTanks.length; i++)
			outputTanks[i].writeToNBT(compound, "out" + i);
		compound.put("salt", saltTank.writeToNBT(registries, new CompoundTag()));
		compound.put("bufIn", bufferIn.writeToNBT(registries, new CompoundTag()));
		compound.put("bufOut", bufferOut.writeToNBT(registries, new CompoundTag()));
		concIn.writeToNBT(compound, "concIn");
		concOut.writeToNBT(compound, "concOut");
		compound.putLong("power", power);
		compound.putLong("maxPower", maxPower);
		module.writeToNBT(compound);
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		for(int i = 0; i < inputTanks.length; i++)
			inputTanks[i].readFromNBT(compound, "in" + i);
		for(int i = 0; i < outputTanks.length; i++)
			outputTanks[i].readFromNBT(compound, "out" + i);
		saltTank.readFromNBT(registries, compound.getCompound("salt"));
		bufferIn.readFromNBT(registries, compound.getCompound("bufIn"));
		bufferOut.readFromNBT(registries, compound.getCompound("bufOut"));
		concIn.readFromNBT(compound, "concIn");
		concOut.readFromNBT(compound, "concOut");
		power = compound.getLong("power");
		if(compound.getLong("maxPower") > 0) // upstream: "you son of a"
			maxPower = compound.getLong("maxPower");
		module.readFromNBT(compound);
	}

	@Override
	public String getName() {
		return "tile.salt_separator.name";
	}

	/// PORTS ///

	/** The eight ports beside the machine's flanks: power, the NTM fluids and the Forge salt. */
	public DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(worldPosition.getX() + 2, worldPosition.getY(), worldPosition.getZ() + 1, Library.POS_X),
				new DirPos(worldPosition.getX() + 2, worldPosition.getY(), worldPosition.getZ() - 1, Library.POS_X),
				new DirPos(worldPosition.getX() - 2, worldPosition.getY(), worldPosition.getZ() + 1, Library.NEG_X),
				new DirPos(worldPosition.getX() - 2, worldPosition.getY(), worldPosition.getZ() - 1, Library.NEG_X),
				new DirPos(worldPosition.getX() + 1, worldPosition.getY(), worldPosition.getZ() + 2, Library.POS_Z),
				new DirPos(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() + 2, Library.POS_Z),
				new DirPos(worldPosition.getX() + 1, worldPosition.getY(), worldPosition.getZ() - 2, Library.NEG_Z),
				new DirPos(worldPosition.getX() - 1, worldPosition.getY(), worldPosition.getZ() - 2, Library.NEG_Z)
		};
	}

	/** The four middle ports, where the FLiBe concentrate comes in. */
	public DirPos[] getConPos2() {
		return new DirPos[] {
				new DirPos(worldPosition.getX() + 2, worldPosition.getY(), worldPosition.getZ(), Library.POS_X),
				new DirPos(worldPosition.getX() - 2, worldPosition.getY(), worldPosition.getZ(), Library.NEG_X),
				new DirPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ() + 2, Library.POS_Z),
				new DirPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ() - 2, Library.NEG_Z),
		};
	}

	/// ENERGY ///

	@Override public long getPower() { return power; }
	@Override public void setPower(long l) { power = l; }
	@Override public long getMaxPower() { return maxPower; }

	/// CONTROL ///

	@Override
	public boolean hasPermission(Player player) {
		return this.stillValid(player);
	}

	@Override
	public void receiveControl(CompoundTag data) {
		if(data.contains("index") && data.contains("selection")) {
			int index = data.getInt("index");
			String selection = data.getString("selection");
			if(index == 0) {
				this.module.setRecipe(selection, false);
				this.markChanged();
			}
		}
	}

	/// GUI ///

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new SaltSeparatorMenu(containerId, playerInventory, this);
	}

	/// SYNC ///

	@Override
	public String getPacketIdentifier() {
		return "SALT_SEPA";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0:
				module.setRecipe((String) value, false);
				break;
			case 1:
				if(level == null) break;
				HolderLookup.Provider registries = level.registryAccess();
				CompoundTag nbt = (CompoundTag) value;
				for(int i = 0; i < inputTanks.length; i++)
					inputTanks[i].readFromNBT(nbt, "in" + i);
				for(int i = 0; i < outputTanks.length; i++)
					outputTanks[i].readFromNBT(nbt, "out" + i);
				saltTank.readFromNBT(registries, nbt.getCompound("salt"));
				bufferIn.readFromNBT(registries, nbt.getCompound("bufIn"));
				bufferOut.readFromNBT(registries, nbt.getCompound("bufOut"));
				break;
			case 2:
				saltType = Fluids.fromID((int) value);
				concIn.setTankType(saltType);
				concOut.setTankType(saltType);
				break;
			case 3:
				module.progress = (double) value;
				break;
			case 4:
				power = (long) value;
				break;
			case 5:
				didProcess = (boolean) value;
				break;
			case 6:
				concIn.setFill((int) value);
				break;
			case 7:
				concOut.setFill((int) value);
				break;
			case 8:
				int amt = (int) value;
				if(amt != saltTank.getCapacity())
					saltTank.setCapacity(amt);
				break;
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	/// NTM FLUIDS ///

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { inputTanks[0], inputTanks[1], concIn };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return outputTanks;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { inputTanks[0], inputTanks[1], outputTanks[0], outputTanks[1], outputTanks[2] };
	}

	/// FORGE FLUIDS ///

	@Override
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank getSendingTank(FluidStack stack) {
		return bufferOut;
	}

	@Override
	public net.neoforged.neoforge.fluids.capability.templates.FluidTank getCorrespondingTank(FluidStack stack) {
		return bufferIn;
	}

	/** Upstream's {@code getTankProperties} was an empty array: fill and drain work, and no tanks are listed. */
	@Override
	public int getTanks() {
		return 0;
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return FluidStack.EMPTY;
	}

	@Override
	public int getTankCapacity(int tank) {
		return 0;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return false;
	}

	/** Anything that fills this machine fills {@link #bufferIn}. */
	@Override
	public int fill(FluidStack resource, FluidAction action) {
		return bufferIn.fill(resource, action);
	}

	/** Anything that drains this machine drains {@link #bufferOut}. */
	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		return bufferOut.drain(resource, action);
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return bufferOut.drain(maxDrain, action);
	}

	public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, SaltSeparatorTE separator) {
		separator.updateEntity();
	}
}
