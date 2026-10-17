package com.leafia.contents.machines.reactors.pwr;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.Tuple.Triplet;
import com.leafia.LeafiaMod;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRChannelBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRConductorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.leafia.init.LeafiaSounds;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.group.LeafiaSet;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * One addon PWR's state and its per-tick simulation: eight tanks, the coolant loop, the integrated
 * boiler, the offline core cooling system, the control-rod demands and the meltdown.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/PWRData.java} (1,275 lines). It is not a
 * block entity: it lives inside whichever component block entity the diagnosis booked as the core
 * ({@link PWRComponentEntity#getCore()}), is saved under that entity's {@code data} tag, and is ticked
 * from it.
 *
 * <h2>The tanks</h2>
 *
 * Upstream's are already NTM {@code FluidTankNTM}s (no Forge tank anywhere in this file, so no fluid
 * bridge, D-3003), and each is the base {@link FluidTank} here. CE's tank has three Forge-style
 * conveniences the base one lacks and that this file calls; they are private helpers with CE's exact
 * semantics, read from the CE jar: {@link #fill} (accepts into an empty-typed tank, refuses a
 * different type), {@link #drainable} (CE's {@code drain(int, false)} returns null on an empty tank,
 * which is what gates the boiler) and {@code loadTank} on an {@code IItemHandler}, which is
 * {@link #loadWater}.
 *
 * <table>
 * <tr><th>#</th><th>holds</th><th>capacity</th></tr>
 * <tr><td>0</td><td>coolant</td><td>3,200 x channels</td></tr>
 * <tr><td>1</td><td>hot coolant</td><td>3,200 x ceil(channels / (1.5 if gaseous) x multiplier)</td></tr>
 * <tr><td>2</td><td>malfunctioning coolant (the emergency buffer)</td><td>ceil(toughness / (10 if gaseous) x multiplier)</td></tr>
 * <tr><td>3, 4</td><td>water, steam (the integrated boiler)</td><td>25,600 and 12,800 x conductors</td></tr>
 * <tr><td>5, 6, 7</td><td>OCCS water in, water out, spent steam out</td><td>750 x fuel columns</td></tr>
 * </table>
 *
 * <h2>What is not here, and where it went</h2>
 *
 * <ul>
 * <li><b>The level-3 meltdown is {@link PWRMeltdown}.</b> {@link PWRExplosion#explodeLv3()} turns the vessel
 * into {@code PWRMeshedWreck}s and throws {@code PWRDebrisEntity}s; it is one call here, {@link #LV3_MELTDOWN},
 * set at class initialisation to {@link PWRMeltdown#explodeLv3} (D-3010). D-3006's stopgap - a level-3
 * meltdown running level 2 with a warning - is gone.</li>
 * <li>The debug wand's chat lines, {@code LeafiaDebug.debugLog} and upstream's profiling
 * {@code Tracker} calls are developer tooling and are not carried.</li>
 * <li>{@code PacketThreading} is not needed (D-062); {@code ChunkRadiationManager.proxy.incrementRad(w,
 * pos, amount, max)} is CE's capped form, which the base mod has since D-2199 as
 * {@code ChunkRadiationManager.incrementRad(level, pos, amount, max)}, as the rods use it (D-3001, D-3023).</li>
 * <li>The {@code radMark} that marks players for the elemental spawn is the base mod's precedent: its
 * reader is unported and upstream's write is a no-op (see {@code BlockEntityReactorResearch}).</li>
 * </ul>
 */
public class PWRData implements LeafiaPacketReceiver {

	public BlockPos corePos;
	public final FluidTank[] tanks;
	public final FluidType[] tankTypes;
	public int coolantId = Fluids.COOLANT.getID();
	public int compression = 0;
	public int coriums = 0;
	public double masterControl = 0.25;
	public final Map<String, Double> controlDemand = new HashMap<>();
	public int toughness = 16_000;

	PWRComponentBlock getPWRBlock(Level world, BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();
		if(block instanceof PWRComponentBlock pwr) return pwr;
		return null;
	}

	PWRComponentBlock getPWRComponent(Level world, BlockPos pos) {
		PWRComponentBlock block = getPWRBlock(world, pos);
		if(block == null) return null;
		return block.tileEntityShouldCreate(world, pos) ? block : null;
	}

	PWRComponentEntity getPWREntity(Level world, BlockPos pos) {
		PWRComponentBlock pwr = getPWRBlock(world, pos);
		if(pwr != null) return pwr.getPWR(world, pos);
		return null;
	}

	public Set<BlockPos> members = new HashSet<>();
	public Set<BlockPos> controls = new HashSet<>();
	public Set<BlockPos> fuels = new HashSet<>();
	public LeafiaSet<BlockPos> projection = new LeafiaSet<>();

	public void onDiagnosis(Level world) {
		if(!world.isClientSide) {
			int slots = 0;
			for(BlockPos pos : projection) {
				PWRComponentBlock block = getPWRComponent(world, pos);
				if(block instanceof PWRElementBlock) slots++;
			}
			if(remoteSize != slots) {
				remoteContainer = new ItemStackHandler(slots);
				remoteSize = slots;
			}
			tanks[5] = resizeTank(tanks[5], fuels.size() * 150 * 5);
			tanks[6] = resizeTank(tanks[6], fuels.size() * 150 * 5);
			tanks[7] = resizeTank(tanks[7], fuels.size() * 150 * 5);
			for(FluidTank tank : tanks) {
				if(tank.getFill() > tank.getMaxFill()) // whoops
					tank.setFill(tank.getMaxFill());
			}
			addDataToPacket(LeafiaPacket._start(companion), this).__sendToAffectedClients();
		}
	}

	public Pair<LeafiaSet<BlockPos>, LeafiaSet<BlockPos>> getProjectionFuelAndControlPositions() {
		Pair<LeafiaSet<BlockPos>, LeafiaSet<BlockPos>> output = new Pair<>(new LeafiaSet<>(), new LeafiaSet<>());
		for(BlockPos pos : projection) {
			Block block = getWorld().getBlockState(pos).getBlock();
			if(block instanceof PWRElementBlock) output.getKey().add(pos);
			else if(block instanceof PWRControlBlock) output.getValue().add(pos);
		}
		return output;
	}

	/** The terminal's grid to world, for its screen. Upstream's, with {@code BlockHorizontal.FACING}. */
	public BlockPos terminal_toGlobal(BlockState terminal, BlockPos terminalPos, BlockPos pos) {
		Direction face = terminal.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
		Vec3 lookVector = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
		Vec3 rightVector = lookVector.cross(new Vec3(0, 1, 0));
		return BlockPos.containing(Vec3.atLowerCornerOf(terminalPos).add(0.5, 0.5 + pos.getY(), 0.5)
				.add(lookVector.scale(-pos.getZ())).add(rightVector.scale(pos.getX())));
	}

	public BlockPos terminal_toLocal(BlockState terminal, BlockPos terminalPos, BlockPos pos) {
		return terminalToLocal(terminal, terminalPos, pos);
	}

	/** {@link #terminal_toLocal}, which reads nothing of the reactor: the terminal's menu builds its grid with it. */
	public static BlockPos terminalToLocal(BlockState terminal, BlockPos terminalPos, BlockPos pos) {
		Direction face = terminal.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
		BlockPos relative = pos.subtract(terminalPos);
		switch(face) {
			case NORTH: return relative;
			case SOUTH: return new BlockPos(-relative.getX(), relative.getY(), -relative.getZ());
			case WEST: return new BlockPos(-relative.getZ(), relative.getY(), relative.getX());
			case EAST: return new BlockPos(relative.getZ(), relative.getY(), -relative.getX());
			default: throw new LeafiaDevFlaw("PWR Terminals should only face sideways, got " + face.getName());
		}
	}

	public BlockEntity companion;

	/**
	 * Slot 0 is a fluid identifier that picks the coolant; slots 1 and 2 load water into the boiler
	 * tank from a canister. Upstream's {@code ItemStackHandler}, kept so the terminal's menu can put
	 * {@code SlotItemHandler}s on it.
	 */
	public ItemStackHandler resourceContainer = new ItemStackHandler(3) {
		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			companion.setChanged();
			if(slot == 0) updateCoolantTypeFromItem(getStackInSlot(slot));
		}
	};

	void updateCoolantTypeFromItem(ItemStack stack) {
		if(stack.getItem() instanceof IItemFluidIdentifier identifier && getWorld() != null) {
			FluidType type = identifier.getType(getWorld(), corePos, stack);
			if(updateCoolantType(type.getID())) coolantId = type.getID();
		}
	}

	public double multiplier = 1;

	/**
	 * Retypes the three coolant tanks for a new coolant. <b>Upstream's bug, kept:</b> each of the three
	 * mismatch checks empties {@code tanks[2]}, where the second and third were evidently meant to empty
	 * tanks 1 and 2. The base tank's {@code setTankType} empties a tank whose type changes anyway, so the
	 * only effect is that the emergency buffer is emptied when the hot coolant's type changes.
	 */
	public boolean updateCoolantType(int coolantId) {
		FluidType coolant = Fluids.fromID(coolantId);
		if(!coolant.equals(Fluids.NONE)) {
			this.coolantId = coolantId;
			Triplet<FluidType, FluidType, Double> hots = getBoiledCoolant(coolant);
			if(hots != null) {
				if(tankTypes[0] != coolant) tanks[2].setFill(0);
				if(tankTypes[1] != hots.getX()) tanks[2].setFill(0);
				if(tankTypes[2] != hots.getX()) tanks[2].setFill(0);
				tankTypes[0] = coolant;
				tankTypes[1] = hots.getX();
				tankTypes[2] = hots.getY();
				for(int i = 0; i < 3; i++) tanks[i].setTankType(tankTypes[i]);
				multiplier = hots.getZ();
				resizeTanks(lastChannels, lastConductors);
				return true;
			}
		}
		return false;
	}

	/** Upstream's {@code ItemStackHandler} the terminal mirrors the fuel grid into; resized on diagnosis, which closes an open terminal (its menu checks it). */
	public ItemStackHandler remoteContainer = new ItemStackHandler(0);
	int remoteSize = 0;

	/**
	 * Upstream's {@code invalidate}: when the core's block entity goes, if what replaced it is corium
	 * the reactor melted, so the rest of it goes up (level 1). Deferred to the next server tick as
	 * upstream's {@code addScheduledTask} was (see {@link PWRTicks#queueFunction}).
	 */
	public void invalidate(Level world) {
		if(world == null || world.isClientSide) return;
		BlockPos checkPos = companion.getBlockPos();
		PWRTicks.queueFunction(() -> {
			if(world.getBlockState(checkPos).getBlock() instanceof CoriumFinite)
				this.explode(world, null, null, 1);
		});
	}

	public PWRData(BlockEntity entity) {
		tanks = new FluidTank[] {
				new FluidTank(Fluids.COOLANT, 128_000),
				new FluidTank(Fluids.COOLANT_HOT, 128_000),
				new FluidTank(LeafiaFluids.COOLANT_MAL, 16_000),

				// Integrated Boilers
				new FluidTank(Fluids.WATER, 512_000),
				new FluidTank(Fluids.STEAM, 256_000),

				// OCCS
				new FluidTank(Fluids.WATER, 0),
				new FluidTank(Fluids.WATER, 0),
				new FluidTank(Fluids.SPENTSTEAM, 0)
		};
		tankTypes = new FluidType[] {
				Fluids.COOLANT,
				Fluids.COOLANT_HOT,
				LeafiaFluids.COOLANT_MAL,

				Fluids.WATER,
				Fluids.STEAM
		};
		this.companion = entity;
		this.corePos = companion.getBlockPos();
	}

	public Level getWorld() {
		return this.companion.getLevel();
	}

	public void onUpdateCompression() {
		if(compression == 0) {
			tanks[4].setTankType(Fluids.STEAM);
			tankTypes[4] = Fluids.STEAM;
		} else if(compression == 1) {
			tanks[4].setTankType(Fluids.HOTSTEAM);
			tankTypes[4] = Fluids.HOTSTEAM;
		} else if(compression == 2) {
			tanks[4].setTankType(Fluids.SUPERHOTSTEAM);
			tankTypes[4] = Fluids.SUPERHOTSTEAM;
		}
	}

	protected FluidTank resizeTank(FluidTank tank, int newCapacity) {
		if(newCapacity == tank.getMaxFill()) return tank;
		FluidTank resized = new FluidTank(tank.getTankType(), newCapacity);
		resized.setFill(Math.min(newCapacity, tank.getFill()));
		return resized;
	}

	public int lastChannels = 0;
	public int lastConductors = 0;

	public void resizeTanks(int channels, int conductors) {
		tanks[0] = resizeTank(tanks[0], 3200 * channels); // coolant
		tanks[1] = resizeTank(tanks[1], 3200 * (int) Math.ceil(channels / (tankTypes[1].hasTrait(com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous.class) ? 1.5 : 1) * multiplier)); // hot coolant
		tanks[2] = resizeTank(tanks[2], (int) Math.ceil(toughness / (float) (tankTypes[1].hasTrait(com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous.class) ? 10 : 1) * multiplier)); // emergency buffer
		tanks[3] = resizeTank(tanks[3], 25600 * conductors); // water
		tanks[4] = resizeTank(tanks[4], 12800 * conductors); // steam
	}

	FluidType getBoilFluid(FluidType f) {
		if(f.hasTrait(FT_Heatable.class)) {
			HeatingStep step = f.getTrait(FT_Heatable.class).getFirstStep();
			return step == null ? null : step.typeProduced;
		}
		return null;
	}

	double getBoilRatio(FluidType f) {
		if(f.hasTrait(FT_Heatable.class)) {
			HeatingStep step = f.getTrait(FT_Heatable.class).getFirstStep();
			return step.amountProduced / (double) step.amountReq;
		}
		return 0;
	}

	/**
	 * Follows the coolant's heating chain to its end: (the second-hottest, the hottest, the product of
	 * every ratio but the last). Upstream's; {@code secondhot} starts as the first product, so a
	 * one-step coolant answers its product twice.
	 */
	Triplet<FluidType, FluidType, Double> getBoiledCoolant(FluidType f) {
		FluidType hot = getBoilFluid(f);
		double mul = 1;
		if(hot == null) return null;
		FluidType secondhot = getBoilFluid(f);
		mul *= getBoilRatio(f);
		double mul2 = mul;
		while(true) {
			FluidType hotter = getBoilFluid(hot);
			if(hotter != null) {
				mul = mul2;
				mul2 *= getBoilRatio(hot);
				secondhot = hot;
				hot = hotter;
			} else break;
		}
		return new Triplet<>(secondhot, hot, mul);
	}

	// ------------------------------------------------------------------------------------ NBT

	public PWRData readFromNBT(CompoundTag nbt, HolderLookup.Provider registries) {
		nbt = nbt.getCompound("data");
		if(nbt.contains("compression")) compression = nbt.getInt("compression");
		onUpdateCompression();
		tankTypes[0] = Fluids.COOLANT;
		tankTypes[1] = Fluids.COOLANT_HOT;
		tankTypes[2] = LeafiaFluids.COOLANT_MAL;
		if(nbt.contains("coolantId")) {
			coolantId = nbt.getInt("coolantId");
			updateCoolantType(coolantId);
		}
		for(int i = 0; i <= 7; i++) {
			if(nbt.contains("tank" + i)) tanks[i].readFromNBT(nbt, "tank" + i);
		}
		if(nbt.contains("remoteContainerSize")) remoteSize = nbt.getInt("remoteContainerSize");
		if(nbt.contains("resourceContainer") && registries != null)
			resourceContainer.deserializeNBT(registries, nbt.getCompound("resourceContainer"));
		if(nbt.contains("projectionMap")) {
			projection.clear();
			ListTag nbtjection = nbt.getList("projectionMap", Tag.TAG_INT_ARRAY);
			for(Tag item : nbtjection) {
				int[] coords = ((IntArrayTag) item).getAsIntArray();
				projection.add(new BlockPos(coords[0], coords[1], coords[2]));
			}
		}
		if(nbt.contains("controlMaster")) masterControl = nbt.getDouble("controlMaster");
		if(nbt.contains("controlDemand")) readControlPositions(nbt.getCompound("controlDemand"));
		return this;
	}

	public CompoundTag writeToNBT(CompoundTag mainCompound, @Nullable HolderLookup.Provider registries) {
		CompoundTag nbt = new CompoundTag();
		if(Fluids.COOLANT.getID() != coolantId) nbt.putInt("coolantId", coolantId);
		nbt.putInt("compression", compression);
		for(int i = 0; i <= 7; i++) tanks[i].writeToNBT(nbt, "tank" + i);
		nbt.putInt("remoteContainerSize", remoteSize);
		if(registries != null) nbt.put("resourceContainer", resourceContainer.serializeNBT(registries));
		ListTag nbtjection = new ListTag();
		for(BlockPos pos : projection)
			nbtjection.add(new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()}));
		nbt.put("projectionMap", nbtjection);

		nbt.putDouble("controlMaster", masterControl);
		nbt.put("controlDemand", writeControlPositions());

		mainCompound.put("data", nbt);
		return mainCompound;
	}

	public void readControlPositions(CompoundTag nbt) {
		controlDemand.clear();
		for(String key : nbt.getAllKeys()) controlDemand.put(key, nbt.getDouble(key));
	}

	public void resyncControls() {
		LeafiaPacket._start(companion).__write(27, writeFullControls()).__sendToAffectedClients();
	}

	public CompoundTag writeFullControls() {
		ListTag nbt = new ListTag();
		for(BlockPos pos : controls) {
			if(getWorld().getBlockEntity(pos) instanceof PWRControlTE control)
				nbt.add(control.writeControlDateToNBT());
		}
		CompoundTag result = new CompoundTag();
		result.put("controls", nbt);
		return result;
	}

	public CompoundTag writeControlPositions() {
		Set<String> existingNames = new LeafiaSet<>();
		Level world = getWorld();
		if(world != null) {
			for(BlockPos pos : controls) {
				if(world.getBlockEntity(pos) instanceof PWRControlTE control) existingNames.add(control.name);
			}
		}
		CompoundTag nbt = new CompoundTag();
		for(Entry<String, Double> entry : controlDemand.entrySet()) {
			if(existingNames.contains(entry.getKey())) nbt.putDouble(entry.getKey(), entry.getValue());
		}
		return nbt;
	}

	boolean valid = false;
	float timeToDrainMalcoolant = 15;
	public int warnTicks = 0;
	public double stressTimer = 300;

	public double spendAccum = 0;

	// ---------------------------------------------------------------------- CE's tank conveniences

	/** CE {@code FluidTankNTM.fill(FluidType, int, boolean)}: an untyped tank takes the type; a different type is refused. */
	static int fill(FluidTank tank, @Nullable FluidType incomingType, int amount, boolean doFill) {
		if(incomingType == null || incomingType == Fluids.NONE || amount <= 0) return 0;
		if(tank.getTankType() == Fluids.NONE) {
			int toTransfer = Math.min(tank.getMaxFill(), amount);
			if(doFill) {
				tank.setTankType(incomingType);
				tank.setFill(toTransfer);
			}
			return toTransfer;
		}
		if(!tank.getTankType().equals(incomingType)) return 0;
		int toTransfer = Math.min(tank.getMaxFill() - tank.getFill(), amount);
		if(doFill && toTransfer > 0) tank.setFill(tank.getFill() + toTransfer);
		return toTransfer;
	}

	/**
	 * CE {@code FluidTankNTM.drain(int, boolean)}, as the amount it would return, or -1 where CE returns
	 * null: an untyped or empty tank. A full tank asked for 0 answers 0, not -1.
	 */
	static int drainable(FluidTank tank, int maxDrain) {
		if(tank.getTankType() == Fluids.NONE || tank.getFill() == 0) return -1;
		return Math.min(maxDrain, tank.getFill());
	}

	/** {@link #drainable}, and takes it. */
	static int drain(FluidTank tank, int maxDrain) {
		int amount = drainable(tank, maxDrain);
		if(amount > 0) tank.setFill(tank.getFill() - amount);
		return amount;
	}

	/** Upstream's {@code tanks[3].loadTank(1, 2, resourceContainer)} over the base tank's slot array. */
	void loadWater() {
		ItemStack[] slots = {
				resourceContainer.getStackInSlot(0), resourceContainer.getStackInSlot(1), resourceContainer.getStackInSlot(2)};
		ItemStack in = slots[1];
		ItemStack out = slots[2];
		tanks[3].loadTank(1, 2, slots, getWorld().registryAccess());
		if(slots[1] != in) resourceContainer.setStackInSlot(1, slots[1]);
		if(slots[2] != out) resourceContainer.setStackInSlot(2, slots[2]);
	}

	// ---------------------------------------------------------------------------------- the tick

	public void update() {
		if(!valid) {
			if(!companion.isRemoved()) {
				valid = true;
				Block block = companion.getBlockState().getBlock();
				if(block instanceof PWRComponentBlock pwr) {
					this.corePos = companion.getBlockPos();
					pwr.beginDiagnosis(getWorld(), companion.getBlockPos(), companion.getBlockPos());
				} else
					getWorld().removeBlockEntity(companion.getBlockPos()); // you're coming with me
			}
			return;
		}
		Level world = getWorld();
		if(world.isClientSide) {
			warnTicks = Math.floorMod(warnTicks + 1, 8);
		} else {
			loadWater();
			occs();
			if(coriums > 0) spendCoolant(Math.pow(coriums * 2727, 0.414), null);
			drainMalcoolant();
			boil();
			LeafiaPacket._start(companion).__write(30, new int[] {
					compression,
					tanks[0].getMaxFill(), tanks[1].getMaxFill(), tanks[2].getMaxFill(), tanks[3].getMaxFill(),
					tanks[4].getMaxFill(), tanks[5].getMaxFill(), tanks[6].getMaxFill(), tanks[7].getMaxFill(),
					tanks[0].getFill(), tanks[1].getFill(), tanks[2].getFill(), tanks[3].getFill(),
					tanks[4].getFill(), tanks[5].getFill(), tanks[6].getFill(), tanks[7].getFill()
			}).__sendToAffectedClients();
		}
	}

	/**
	 * The offline core cooling system: 150 mB of OCCS water per fuel column per tick takes half a percent off the
	 * rod's heat above 20; a rod it cooled by more than 0.05 degrees sends its water out as spent steam, any other
	 * as water. Part of {@link #update}, in upstream's order.
	 */
	public void occs() {
		Level world = getWorld();
		for(BlockPos fuelPos : fuels) {
			if(tanks[5].getFill() >= 150 && tanks[6].getFill() <= tanks[6].getMaxFill() - 150 && tanks[7].getFill() <= tanks[7].getMaxFill() - 150) {
				if(world.getBlockEntity(fuelPos) instanceof PWRElementTE element) {
					tanks[5].setFill(tanks[5].getFill() - 150);
					ItemStack stack = element.inventory.getStackInSlot(0);
					boolean cooled = false;
					if(stack.getItem() instanceof LeafiaRodItem) {
						CompoundTag nbt = LeafiaRodItem.data(stack);
						if(nbt != null && nbt.contains("heat")) {
							double heat = nbt.getDouble("heat");
							double heatOg = heat;
							heat = (heat - 20) * 0.995 + 20;
							nbt.putDouble("heat", heat);
							LeafiaRodItem.putData(stack, nbt);
							cooled = heatOg - heat > 0.05;
						}
					}
					if(cooled) tanks[7].setFill(tanks[7].getFill() + 150);
					else tanks[6].setFill(tanks[6].getFill() + 150);
				}
			}
		}
	}

	/**
	 * The emergency buffer drains back into the hot coolant tank, a 300th of its capacity a tick at four hot
	 * coolant per malfunctioning coolant, and stresses the vessel while it holds anything. Part of {@link #update}.
	 */
	public void drainMalcoolant() {
		Level world = getWorld();
		if(tanks[2].getFill() > 0) {
			int decr = Math.min(tanks[2].getFill(), tanks[2].getMaxFill() / Math.round(timeToDrainMalcoolant * 20));
			if(tanks[1].getMaxFill() - tanks[1].getFill() >= decr * 4) {
				tanks[1].setFill(Math.min(tanks[1].getFill() + decr * 4, tanks[1].getMaxFill()));
				tanks[2].setFill(Math.max(tanks[2].getFill() - decr, 0));
			}
			// directly copied from zirnox port
			double stress = tanks[2].getFill() / (double) tanks[2].getMaxFill();
			stressTimer -= Math.pow(stress, 0.9) * 64;
			// D-3075: the timer resets after the sound (upstream's never did, so it played every tick once spent).
			if(stressTimer <= 0) {
				stressTimer = 300;
				BlockPos pos = (BlockPos) members.toArray()[world.random.nextInt(members.size())];
				world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						LeafiaSounds.stressSounds[world.random.nextInt(7)].get(), SoundSource.BLOCKS,
						(float) Mth.clampedLerp(0.25, 14, Math.pow(stress, 4)), 1.0F);
			}
		}
	}

	/**
	 * The integrated boiler: plain coolant only. It takes {@code round((hot / waterCapacity x 4)^0.4 x 80 x 16)} mB of
	 * water a tick, sixteen water per hot coolant, makes {@code 100 / 10^compression} steam per water (limited by the
	 * steam tank), and turns the hot coolant it used back into coolant. Part of {@link #update}.
	 */
	public void boil() {
		if(tanks[3].getMaxFill() > 0 && coolantId == Fluids.COOLANT.getID()) {
			int conversionRate = 16;
			int consumption = (int) Math.round(Math.pow(tanks[1].getFill() / (double) Math.max(tanks[3].getMaxFill(), 1) * 4, 0.4) * 80 * conversionRate);
			int stack = drainable(tanks[1], consumption / conversionRate);
			int stack2 = drainable(tanks[3], consumption);
			if(stack >= 0 && stack2 >= 0) {
				int boilAmt = Math.min(stack * conversionRate, stack2);
				int division = (int) Math.pow(10, compression);
				int filled = fill(tanks[4], tankTypes[4], boilAmt * 100 / division, true);
				int drained = (Math.min(boilAmt, filled * division / 100) / conversionRate);
				tanks[1].setFill(Math.max(tanks[1].getFill() - drained, 0));
				fill(tanks[0], tankTypes[0], (int) (drained / multiplier), true);
				tanks[3].setFill(Math.max(tanks[3].getFill() - (Math.min(boilAmt, filled * division / 100)), 0));
			}
		}
	}

	int boilingAccum = 0;

	/** The 96:1 boil-off remainder ({@link #spendCoolant}), for tests. */
	public int boilingAccum() {
		return boilingAccum;
	}

	public static final double transferMultiplier = 12;
	public static final int boilingDivision = 96;

	/**
	 * A rod's {@code cooled} heat turned into coolant: {@code cooled x 12 / multiplier} mB of coolant
	 * becomes hot coolant, and what will not fit in the hot tank boils into the emergency buffer at
	 * 96:1. With the buffer full as well, the vessel blows ({@link #explode}, level by toughness).
	 *
	 * <p><b>Upstream's bug, kept:</b> the 96:1 remainder accumulator subtracts the whole units it hands
	 * out rather than 96 times them ({@code boilingAccum -= fillAdd}), so it creeps upward and pays out
	 * about one extra millibucket per 96 it was owed. With coolant missing entirely nothing is drained
	 * and nothing heats: CE's {@code drain} answers null on an empty tank.
	 */
	public void spendCoolant(double cooled, @Nullable ItemStack stack) {
		double drainD = cooled * transferMultiplier / multiplier;
		int drain = (int) Math.floor(drainD);

		double accum = drainD - drain;
		spendAccum += accum;
		int add = (int) Math.floor(spendAccum);
		spendAccum -= add;
		drain += add;

		drain = (int) ((int) (drain * multiplier) / multiplier);

		int fs = drain(tanks[0], drain);
		if(fs >= 0) {
			int drained = (int) (fs * multiplier);
			for(int tank = 1; drained > 0; tank++) {
				switch(tank) {
					case 1: {
						int filled = fill(tanks[tank], tankTypes[tank], drained, true);
						drained -= filled;
					}
					break;
					case 2: {
						int fill = drained / boilingDivision;
						boilingAccum += Math.floorMod(drained, boilingDivision);
						int fillAdd = boilingAccum / boilingDivision;
						boilingAccum -= fillAdd;
						int filled = fill(tanks[tank], tankTypes[tank], fill + fillAdd, true);
						drained -= filled * boilingDivision;
					}
					break;
					default:
						if(tanks[2].getFill() >= tanks[2].getMaxFill())
							explode(getWorld(), stack, null, 0); // Blowout
						return;
				}
			}
		}
	}

	// ------------------------------------------------------------------------------- the meltdown

	boolean exploded = false;
	Level explodeWorld = null;

	/** Grows {@link #members} by one block on every face, and answers the ring it added. */
	public Set<BlockPos> growMembers(Set<BlockPos> set) {
		Set<BlockPos> offsets = new HashSet<>();
		for(BlockPos member : set) {
			for(Direction facing : Direction.values())
				offsets.add(member.relative(facing));
		}
		members.addAll(offsets);
		return offsets;
	}

	public static double signedPow(double x, double y) {
		return Math.pow(Math.abs(x), y) * Math.signum(x);
	}

	/**
	 * The level-3 meltdown: the vessel becomes {@code PWRMeshedWreck}s and {@code PWRDebrisEntity}s. Upstream's
	 * {@code explodeLv3} body, ported as {@link PWRMeltdown} against the public state of {@link PWRExplosion}
	 * and this class ({@link #members}, {@link #growMembers}, {@link #coriumTypes}, {@link #toughness}).
	 * Swappable, so a test can watch a level-3 meltdown be chosen without running one.
	 */
	@FunctionalInterface
	public interface Lv3Meltdown {
		void explode(PWRData data, PWRExplosion explosion);
	}

	/** Set at initialisation (D-3010). */
	public static Lv3Meltdown LV3_MELTDOWN = PWRMeltdown::explodeLv3;

	public class PWRExplosion {
		public final Level world;
		public final Vec3 centerPoint;
		public final int minX;
		public final int minY;
		public final int minZ;
		public final int maxX;
		public final int maxY;
		public final int maxZ;
		/** Which level actually ran: 1, 2, or 3. For tests. */
		public int ran = 0;

		public PWRExplosion(Level world, Vec3 centerPoint, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.world = world;
			this.centerPoint = centerPoint;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		/** Channels and conductors become dense radon, a fifth of the components beside them radon, and an 8-strength blast. */
		public void explodeLv1() {
			ran = 1;
			for(BlockPos member : members) {
				Block block = world.getBlockState(member).getBlock();
				if(block instanceof PWRChannelBlock || block instanceof PWRConductorBlock) {
					world.setBlockAndUpdate(member, ModBlocks.gas_radon_dense.get().defaultBlockState());
					for(Direction face : Direction.values()) {
						BlockPos offs = member.relative(face);
						if(world.isInWorldBounds(offs) && world.getBlockState(offs).getBlock() instanceof PWRComponentBlock) {
							if(world.random.nextInt(5) == 0)
								world.setBlockAndUpdate(offs, ModBlocks.gas_radon.get().defaultBlockState());
						}
					}
				}
			}
			// createExplosion(null, x, y, z, 8, true): smoking (breaks blocks), not flaming.
			world.explode(null, centerPoint.x + 0.5, centerPoint.y + 0.5, centerPoint.z + 0.5, 8.0F, false, Level.ExplosionInteraction.BLOCK);
		}

		/** Every component becomes dense radon, and a flaming 24-strength blast. */
		public void explodeLv2() {
			ran = 2;
			for(BlockPos member : members) {
				if(world.getBlockState(member).getBlock() instanceof PWRComponentBlock)
					world.setBlockAndUpdate(member, ModBlocks.gas_radon_dense.get().defaultBlockState());
			}
			world.explode(null, centerPoint.x + 0.5, centerPoint.y + 0.5, centerPoint.z + 0.5, 24.0F, true, Level.ExplosionInteraction.BLOCK);
		}

		/** The vessel blows apart into wreck and debris: {@link PWRMeltdown}. */
		public void explodeLv3() {
			ran = 3;
			LV3_MELTDOWN.explode(PWRData.this, this);
		}
	}

	/** Each fuel column's position to the corium its rod melts into, filled by {@link #explode}. Read by level 3. */
	public final Long2ObjectOpenHashMap<Block> coriumTypes = new Long2ObjectOpenHashMap<>();

	/** The last explosion, for tests. */
	@Nullable
	public PWRExplosion lastExplosion = null;

	/**
	 * The meltdown, once per reactor: {@link #gatherMeltdown}, then the level {@link #meltdownLevel} picks, then
	 * a radius-35 wasteland, 3,000 RAD of chunk radiation (capped at 4,000) and, for a rod at {@code nuke},
	 * its nuke.
	 */
	public void explode(Level world, @Nullable ItemStack prevStack, @Nullable LeafiaRodItem doNuke, int forceLevel) {
		if(exploded) return;
		exploded = true;
		explodeWorld = world;
		if(members.size() <= 0) return;

		PWRExplosion boom = gatherMeltdown(world, prevStack);
		lastExplosion = boom;
		switch(meltdownLevel(toughness, forceLevel)) {
			case 1: boom.explodeLv1(); break;
			case 2: boom.explodeLv2(); break;
			case 3: boom.explodeLv3(); break;
		}

		Vec3 centerPoint = boom.centerPoint;
		BlockPos center = BlockPos.containing(centerPoint);
		if(doNuke != null) doNuke.nuke(world, center);
		ExplosionNukeGeneric.waste(world, (int) centerPoint.x, (int) centerPoint.y, (int) centerPoint.z, 35);
		ChunkRadiationManager.incrementRad(world, center, 3000F, 4000F);
		// Upstream marks every player within 100 blocks with "radMark" when MobConfig.enableElementals;
		// the base mod's precedent (BlockEntityReactorResearch) records why that write is a no-op.
	}

	/**
	 * Which meltdown {@link #explode} runs: {@code forceLevel} 1 to 3 as given (anything else none), and 0 by
	 * toughness - 15,000 and up level 3, 10,000 and up level 2, below that level 1.
	 */
	public static int meltdownLevel(int toughness, int forceLevel) {
		if(forceLevel == 0) {
			if(toughness >= 15_000) return 3;
			else if(toughness >= 10_000) return 2;
			else return 1;
		}
		return forceLevel >= 1 && forceLevel <= 3 ? forceLevel : 0;
	}

	/**
	 * The first half of {@link #explode}, upstream's member loop: the vessel's bounds and centre, and every fuel
	 * column emptied, its rod's corium recorded down the column's height in {@link #coriumTypes}. Nothing is
	 * blown up. Public so the meltdown can be checked without a blast.
	 */
	public PWRExplosion gatherMeltdown(Level world, @Nullable ItemStack prevStack) {
		Vec3 centerPoint = Vec3.ZERO;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for(BlockPos member : members) {
			minX = Math.min(minX, member.getX());
			minY = Math.min(minY, member.getY());
			minZ = Math.min(minZ, member.getZ());
			maxX = Math.max(maxX, member.getX());
			maxY = Math.max(maxY, member.getY());
			maxZ = Math.max(maxZ, member.getZ());
			centerPoint = centerPoint.add(new Vec3(member.getX(), member.getY(), member.getZ()).scale(1d / members.size()));
			Block block = world.getBlockState(member).getBlock();
			if(block instanceof PWRElementBlock element && element.tileEntityShouldCreate(world, member)) {
				if(world.getBlockEntity(member) instanceof PWRElementTE te && te.inventory != null) {
					ItemStack stack = te.inventory.getStackInSlot(0);
					if(!stack.isEmpty() && stack.getItem() instanceof LeafiaRodItem rod) {
						int height = te.getHeight();
						for(int i = 0; i < height; i++)
							coriumTypes.put(member.below(i).asLong(), rod.corium());
					}
					prevStack = LeafiaRodItem.comparePriority(te.inventory.getStackInSlot(0), prevStack);
					te.inventory.setStackInSlot(0, ItemStack.EMPTY);
				}
			}
		}
		return new PWRExplosion(world, centerPoint, minX, minY, minZ, maxX, maxY, maxZ);
	}

	// ----------------------------------------------------------------------------------- packets

	public static LeafiaPacket.Builder addDataToPacket(LeafiaPacket.Builder packet, @Nullable PWRData self) {
		return packet.__write(31, (self != null) ? self.writeToNBT(new CompoundTag(), registries(self.getWorld())) : false);
	}

	@Nullable
	private static HolderLookup.Provider registries(@Nullable Level level) {
		return level == null ? null : level.registryAccess();
	}

	@Nullable
	public static PWRData tryLoadFromPacket(BlockEntity entity, Object value) {
		if(value == null || value.equals(false)) return null;
		else if(value instanceof CompoundTag tag)
			return new PWRData(entity).readFromNBT(tag, registries(entity.getLevel()));
		else return null;
	}

	public void sendControlPositions() {
		LeafiaPacket._start(companion)
				.__write(29, writeControlPositions())
				.__write(28, masterControl)
				.__sendToAffectedClients();
	}

	@Override
	public String getPacketIdentifier() {
		throw new LeafiaDevFlaw("Method PWRData.getPacketIdentifier() is not supposed to be used! Spaghetti coding moment.");
	}

	/** Client side. Upstream warns the player in chat on a malformed packet; that is a log line here. */
	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == 30) { // Tank packets
			if(value == null || !value.getClass().isArray()) {
				LeafiaMod.LOGGER.warn("Malformed PWR tank packet! (Given value wasn't Array)");
				return;
			}
			if(Array.getLength(value) != 17) {
				LeafiaMod.LOGGER.warn("Malformed PWR tank packet! (Array length must be 17, got {})", Array.getLength(value));
				return;
			}
			int readIndex = 0;
			int prevCompression = compression;
			compression = (int) Array.get(value, readIndex++);
			if(prevCompression != compression) onUpdateCompression();
			for(int i = 0; i <= 7; i++)
				tanks[i] = resizeTank(tanks[i], (int) Array.get(value, readIndex++));
			for(int i = 0; i <= 7; i++)
				tanks[i].setFill((int) Array.get(value, readIndex++));
		} else if(key == 29) { // Rod sync packets
			if(value instanceof CompoundTag tag) readControlPositions(tag);
		} else if(key == 31) {
			new PWRDiagnosis(companion.getLevel(), companion.getBlockPos()).addPosition(companion.getBlockPos());
		} else if(key == 28) { // Master rod sync
			masterControl = (double) value;
		} else if(key == 27) { // Server Rod sync
			if(value instanceof CompoundTag tag) {
				ListTag list = tag.getList("controls", Tag.TAG_COMPOUND);
				for(int i = 0; i < list.size(); i++) {
					CompoundTag nbt = list.getCompound(i);
					if(nbt.contains("x") && nbt.contains("y") && nbt.contains("z")) {
						BlockPos pos = new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
						if(controls.contains(pos) && getWorld().getBlockEntity(pos) instanceof PWRControlTE control)
							control.readControlDataFromNBT(nbt);
					}
				}
			} else {
				LeafiaMod.LOGGER.warn("Malformed PWR control rod packet! (Given value wasn't NBTTagList)");
			}
		}
	}

	/**
	 * From the terminal's screen: key 30 sets a rod group's demand (or the master), key 29 the boiler's
	 * compression. D-1094: only from a player with a terminal's menu open over this reactor (the terminal
	 * forwards them, {@code PWRTerminalTE}), a level must be a number from 0 to 1 - what the screen's
	 * slider can send - and a group must be one of this reactor's rods' names. Upstream checks none of it.
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(!(plr.containerMenu instanceof PWRTerminalMenu menu) || menu.core != this) {
			PacketGuard.reject("PWRData", plr, "no terminal of this reactor is open");
			return;
		}
		if(key == 30) { // control rods request
			if(value instanceof CompoundTag nbt) {
				double level = nbt.getDouble("level");
				if(!(level >= 0 && level <= 1)) {
					PacketGuard.reject("PWRData", plr, "rod level " + level);
					return;
				}
				if(nbt.contains("name") && !rodNames().contains(nbt.getString("name"))) {
					PacketGuard.reject("PWRData", plr, "no rod group of that name");
					return;
				}
				if(nbt.contains("name")) {
					controlDemand.put(nbt.getString("name"), nbt.getDouble("level"));
					manipulateRod(nbt.getString("name"));
				} else {
					masterControl = nbt.getDouble("level");
					manipulateRod(null);
				}
				sendControlPositions();
			}
		} else if(key == 29) {
			if(value instanceof Integer i) {
				compression = Math.floorMod(i, 3);
				onUpdateCompression();
			}
		}
	}

	@Override
	public void onPlayerValidate(Player plr) {
		addDataToPacket(LeafiaPacket._start(companion), this).__sendToClient(plr);
	}

	/** The names this reactor's control rods carry: what a terminal's rod group request may name. */
	public Set<String> rodNames() {
		Set<String> names = new HashSet<>();
		Level world = getWorld();
		if(world == null) return names;
		for(BlockPos pos : controls)
			if(world.getBlockEntity(pos) instanceof PWRControlTE control) names.add(control.name);
		return names;
	}

	/** Drives every rod of group {@code name} (or every rod, with null) to its demand times the master. */
	public void manipulateRod(@Nullable String name) {
		for(BlockPos pos : controls) {
			if(getWorld().getBlockEntity(pos) instanceof PWRControlTE control) {
				double newTarget = 0;
				if(controlDemand.containsKey(control.name))
					newTarget = controlDemand.get(control.name) * masterControl;
				if(name != null && !control.name.equals(name)) continue;
				control.targetPosition = newTarget;
			}
		}
	}

	/** The positions as a list, for callers that want an index. Upstream's {@code motherfucker} list. */
	public List<BlockPos> memberList() {
		return new ArrayList<>(members);
	}
}
