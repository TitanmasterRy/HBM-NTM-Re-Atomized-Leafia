package com.leafia.contents.machines.reactors.zirnox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityReactorZirnox;
import com.hbm.entity.projectile.EntityZirnoxDebris;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemZirnoxRod;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.sound.NtmSounds;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ZIRNOX overhaul: the base reactor's block entity with upstream's hull, pressure, control rods,
 * steam compression and vent, burning the addon's fuel rods.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinTileEntityReactorZIRNOX.java} (745
 * lines), a mixin that overwrites the base ZIRNOX's {@code update}, NBT and GUI, and its interface
 * {@code IMixinTileEntityReactorZIRNOX}, which dissolves: its packet keys are {@link PacketKeys} and its
 * thirteen getters are this class's public fields. Here it is a subclass the base core makes through
 * {@code BlockEntityOverrides} while {@code overhauls.zirnoxOverhaul} is on ({@link LeafiaZirnox}).
 *
 * <h2>The machine, in one paragraph</h2>
 *
 * The base reactor's {@code heat} (from base ZIRNOX rods, which still burn here, only with the control rods
 * fully out) is moved into a <b>hull temperature</b> each tick, in degrees: {@code heat x 0.00001 x 780 x
 * coolant / 3.5}. Addon rods fission off their neighbours' flux, scaled by the control rods, and pull the
 * hull toward their average heat by a fourth-root step, plus what they shed into the CO2. The hull boils
 * feedwater into steam at a rate set by how far it is above the steam's boiling point, and the
 * <b>compression</b> switch picks the steam grade (1x, 10x, 100x, 1000x) and the water cost. CO2 is the
 * pressure: {@code (2 x fill + (hull - 20) x 125 x fill%) / 3333} atmospheres. The vent bleeds CO2 faster
 * the higher the pressure. It fails at a hull of 1,300 degrees, or after five seconds at 30 atmospheres
 * or more without a drop of 0.2 - with an addon rod in it, as that rod's visual blast and no fallout;
 * without, as fallout out to 35 and 3,000 RAD.
 *
 * <h2>Translations</h2>
 *
 * <ul>
 * <li>Upstream's tick runs on both sides; the server half is {@link #updateEntity()} and the client half -
 *     the vent wheel's turn and the pressure dial's jitter, which only the screen draws - is
 *     {@link #clientTick}, which {@link ZirnoxScreen} runs while it is open (the base core has no client
 *     ticker).</li>
 * <li>{@code updateConnections} every 20 ticks plus {@code tryProvide} every tick is the base reactor's
 *     {@code autoPort} every tick, after the steam is made - a fluid net moves the steam itself, so this is
 *     the subscription half D-969 allows to collapse.</li>
 * <li>CE's {@code loadTank(24, 25)}/{@code (26, 27)} are the base tank's {@code loadTank} on the same slots:
 *     CO2 canisters in 24 out 25, water in 26 out 27 (the base reactor uses 24/26 and 25/27).</li>
 * <li>{@code LeafiaPassiveServer.queueFunction(rod.nuke)} - a melted rod's blast on the next tick - runs at
 *     once, after the meltdown; spawning entities from a block-entity tick is safe here.</li>
 * <li>{@code MainRegistry.proxy.effectNT} is the client half of the mushroom packet and dissolves; the ZIRNOX
 *     advancement and the elementals' {@code radMark} are the base reactor's two deferred lines, and are
 *     deferred here for the same reasons.</li>
 * <li>The OpenComputers callbacks are plain methods as the base reactor's are ({@link #getTemp},
 *     {@link #getPressure}, {@link #getControl}, {@link #setControl}, {@link #getInfo}); the Custom Control
 *     Panel half ({@code IControllable}) belongs to a panel this port does not have.</li>
 * <li>Every serverbound packet is checked (D-1094): this reactor's screen open, in reach, values in range.</li>
 * </ul>
 *
 * <p>Upstream oddities are kept, each pinned by {@code LeafiaZirnoxTests} and listed in D-3020.
 */
public class LeafiaZirnoxTE extends BlockEntityReactorZirnox implements LeafiaPacketReceiver, IControllable {

	/** Upstream's {@code IMixinTileEntityReactorZIRNOX.packetKeys}. */
	public enum PacketKeys {
		HULL_TEMP(0),
		PRESSURE(1),
		CONTROL_RODS(2),
		COMPRESSION(3),
		CONTROL_RODS_TARGET(4),
		OPENVALVE(5),
		TANKS(6);

		public final byte key;

		PacketKeys(int key) {
			this.key = (byte) key;
		}
	}

	/** The hull's melting point: where the screen's temperature bar is full. Not a failure. */
	public static final double MELTING_POINT = 800;
	/** The hull temperature that fails the reactor at once. */
	public static final double HULL_FAILURE = 1300;
	/** Atmospheres at which the generosity timer starts. */
	public static final double MAX_PRESSURE = 30;
	/** Ticks the pressure may stay at {@link #MAX_PRESSURE}. Upstream's {@code 5*20}. */
	public static final int GENEROSITY = 5 * 20;
	/** A pressure that falls by more than this in a tick resets the timer. */
	public static final double PRESSURE_RELIEF = 0.2;
	/** Upstream's stress timer, reset after each groan: "the audios were lot longer than i thought". */
	public static final double STRESS_RESET = 300;
	/** The steam tank, which upstream builds as 8,000 mB of plain steam. */
	public static final int STEAM_CAPACITY = 8000;
	/** Blocks around the core the state packet reaches. */
	public static final int PACKET_RANGE = 15;
	/** The rebuilt meltdown's flaming explosion, two and a half blocks up. */
	public static final float MELTDOWN_EXPLOSION = 24.0F;
	/** Fallout radius when no addon rod chose the blast. */
	public static final int MELTDOWN_WASTE = 35;
	public static final float MELTDOWN_RAD = 3000F;
	public static final float MELTDOWN_RAD_MAX = 4000F;
	/** The visual-only blast an addon rod is asked for. */
	public static final float ROD_DETONATE_RADIUS = 18;
	/** Upstream's {@code spillage > 200}. */
	public static final int SPILLAGE_LIMIT = 200;

	// SERVER
	public byte rods = 0;
	public byte rodsTarget = 0;
	public double hulltemp = 20;
	public double meltingPoint = MELTING_POINT;
	public double pressureLCE = 0;
	public double lastPressureLCE = 0;
	boolean kill = false;
	public double maxPressureLCE = MAX_PRESSURE;
	public byte compression = 0;
	public int generosityTimer = GENEROSITY;
	public double stressTimer = STRESS_RESET;
	public boolean valveOpen = false;
	public double avgHeat = 0;
	// CLIENT
	public int dialX = 0;
	public int dialY = 0;
	public int valveLevel = 0;
	int movedelay = 0;

	public LeafiaZirnoxTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		// upstream's onInit: the other two tanks stay the base reactor's
		steam = new FluidTank(Fluids.STEAM, STEAM_CAPACITY);
	}

	/** Upstream's injected head: an addon rod goes into any of the 24 channels; everything else as the base reactor. */
	@Override
	public boolean canPlaceItem(int i, ItemStack stack) {
		if(i < CHANNELS && stack.getItem() instanceof LeafiaRodItem) return true;
		return super.canPlaceItem(i, stack);
	}

	// ------------------------------------------------------------------------------- the arithmetic

	/** Upstream's pressure, in atmospheres. */
	public static double pressureOf(int co2Fill, int co2Max, double hulltemp) {
		return ((co2Fill * 2) + (hulltemp - 20) * 125 * ((float) co2Fill / (float) co2Max)) / 3333;
	}

	/** mB of CO2 the open vent lets out in a tick at {@code pressure}. */
	public static int ventDrain(double pressure) {
		return (int) Math.ceil(Math.pow(Math.max(pressure - 5, 0), 3) / 30 + 10);
	}

	/** How far the stress timer runs down in a tick: nothing below 22 atmospheres. */
	public static double stressStep(double pressure) {
		return Math.pow(Math.max(pressure - 22, 0) / 6, 0.9) * 64;
	}

	/** The groan's volume. */
	public static float stressVolume(double pressure) {
		return (float) Mth.clampedLerp(0.25, 4, Math.pow((pressure - 16) / 14, 4));
	}

	/** Upstream's boil, already divided by three: the hull's loss and a 24th of the steam made. */
	public static double boilBase(double hulltemp, double steamtemp, double feedwatr) {
		double boilBase = Math.pow(Math.max(hulltemp - (steamtemp), 0), 0.25) * Math.pow(steamtemp / 100, 0.75) * feedwatr;
		boilBase /= 3;
		return boilBase;
	}

	/** Water a tick costs at each compression: a hundredth, a tenth, one and ten times the steam (int division). */
	public static int waterCost(int boiling, int compression) {
		return switch(compression) {
			case 0 -> boiling / 100;
			case 1 -> boiling / 10;
			case 2 -> boiling;
			case 3 -> boiling * 10;
			default -> 0;
		};
	}

	/** Upstream's {@code getCompressionLevel}: how many plain-steam units one unit of the grade stands for. */
	public static short compressionLevel(FluidType fluid) {
		if(fluid == Fluids.STEAM) return 1;
		else if(fluid == Fluids.HOTSTEAM) return 10;
		else if(fluid == Fluids.SUPERHOTSTEAM) return 100;
		else if(fluid == Fluids.ULTRAHOTSTEAM) return 1000;
		return 0;
	}

	/** The steam grade a compression setting means. */
	public static FluidType compressionFluid(int compression) {
		return switch(compression) {
			case 1 -> Fluids.HOTSTEAM;
			case 2 -> Fluids.SUPERHOTSTEAM;
			case 3 -> Fluids.ULTRAHOTSTEAM;
			default -> Fluids.STEAM;
		};
	}

	/** One neighbour a fuel channel reads flux from, and what that flux is divided by. */
	@FunctionalInterface
	public interface NeighbourVisitor {
		void visit(int slot, double divisor);
	}

	/** The first and last slot of each of the lattice's seven rows (3, 4, 3, 4, 3, 4, 3 channels). */
	private static final int[] ROW_START = { 0, 3, 7, 10, 14, 17, 21 };
	private static final int[] ROW_END = { 2, 6, 9, 13, 16, 20, 23 };

	/**
	 * Upstream's neighbour walk, in upstream's order: straight up and down the 7-wide layout halving with
	 * each step, along the row halving with each step, and the diagonals at a half. D-3076 fixes its two
	 * slips: the row bounds were the absolute 0-2 and 3-7, so every row below the second read back along
	 * the wrong slots (slot 10 read 9, 8, 7 ... 3 and met 3 and 7 twice); they are the slot's own row now.
	 * And the diagonal guards were {@code > 0}, so slot 0 was never reached diagonally.
	 */
	public static void forEachNeighbour(int slot, NeighbourVisitor visitor) {
		for(int offset = 1; slot - offset * 7 >= 0; offset += 1) {
			visitor.visit(slot - offset * 7, Math.pow(2, offset - 1));
		}
		for(int offset = 1; slot + offset * 7 < 24; offset += 1) {
			visitor.visit(slot + offset * 7, Math.pow(2, offset - 1));
		}
		int row = 0;
		while(row < 6 && slot > ROW_END[row]) row++;
		int rowStart = ROW_START[row];
		int rowEnd = ROW_END[row];
		int smod = Math.floorMod(slot, 7);
		for(int offset = 1; slot - offset >= rowStart; offset += 1) {
			visitor.visit(slot - offset, Math.pow(2, offset - 1));
		}
		for(int offset = 1; slot + offset <= rowEnd; offset += 1) {
			visitor.visit(slot + offset, Math.pow(2, offset - 1));
		}
		if(smod <= 2) {
			if(slot - 4 >= 0)
				visitor.visit(slot - 4, 2);
			if(slot - 3 >= 0)
				visitor.visit(slot - 3, 2);
			if(slot + 4 < 24)
				visitor.visit(slot + 4, 2);
			if(slot + 3 < 24)
				visitor.visit(slot + 3, 2);
		} else {
			if(smod != 3) {
				if(slot - 4 >= 0)
					visitor.visit(slot - 4, 2);
				if(slot + 3 < 24)
					visitor.visit(slot + 3, 2);
			}
			if(smod != 6) {
				if(slot - 3 >= 0)
					visitor.visit(slot - 3, 2);
				if(slot + 4 < 24)
					visitor.visit(slot + 4, 2);
			}
		}
	}

	/**
	 * The cells the meltdown sets to air before it builds the wreck: the 5x5 above the reactor from two to five
	 * up, as the base ZIRNOX does. Upstream's overhaul wrote {@code for(oy = 2; ox <= 5; ox++)} and so cleared
	 * eight cells in a line, three of them outside the reactor; fixed in D-3075 (was D-3020 #1).
	 */
	public static List<BlockPos> upstreamClearedCells(BlockPos pos) {
		List<BlockPos> cells = new ArrayList<>();
		for(int ox = -2; ox <= 2; ox++) {
			for(int oz = -2; oz <= 2; oz++) {
				for(int oy = 2; oy <= 5; oy++) {
					cells.add(pos.offset(ox, oy, oz));
				}
			}
		}
		return cells;
	}

	// ------------------------------------------------------------------------------------- the tick

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		this.checkTilt(TiltType.CONFIG, true);
		movedelay = (movedelay + 1) % 2;
		if(movedelay == 0) {
			if(rods > rodsTarget)
				rods--;
			else if(rods < rodsTarget)
				rods++;
		}

		loadTanks();

		if(rods >= 100) {
			for(int i = 0; i < CHANNELS; i++) {
				if(!slots[i].isEmpty()) {
					if(slots[i].getItem() instanceof ItemZirnoxRod)
						decay(i);
					else if(slots[i].getItem() == ModItems.meteorite_sword_bred.get())
						slots[i] = new ItemStack(ModItems.meteorite_sword_irradiated.get());
				}
			}
		}

		lastPressureLCE = pressureLCE;
		this.pressureLCE = pressureOf(carbonDioxide.getFill(), carbonDioxide.getMaxFill(), hulltemp);
		if(valveOpen)
			drainTank(carbonDioxide, ventDrain(pressureLCE));
		stressTimer -= stressStep(pressureLCE);
		if(stressTimer <= 0) {
			stressTimer = STRESS_RESET;
			SoundEvent groan = stressSound(level.random.nextInt(7));
			level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5,
					groan, SoundSource.BLOCKS, stressVolume(pressureLCE), 1.0F);
		}
		if(pressureFailing()) {
			explode();
			return;
		}
		if(hulltemp >= HULL_FAILURE) {
			explode();
			return;
		}
		double coolin = (float) Math.pow(carbonDioxide.getFill() / 16000f, 0.4);

		hulltemp += (heat * 0.00001 * 780) * coolin / 3.5; // haha
		heat = 0;
		double feedwatr = (float) Math.pow(water.getFill() / 32000f, 0.4);
		double cooledSum = 0;
		avgHeat = 20;
		for(int i = 0; i < CHANNELS; i++) {
			if(kill) return;
			if(slots[i].getItem() instanceof LeafiaRodItem)
				cooledSum += handleLeafiaFuel(i, coolin * 1.5);
		}
		if(kill) return;
		double difference = Math.abs(avgHeat - this.hulltemp);
		byte sign = 1;
		if(this.hulltemp > avgHeat) sign = -1;
		this.hulltemp += Math.pow(difference, 0.25) * sign + cooledSum / 24;
		double steamtemp = steam.getTankType().temperature;
		double boilBase = boilBase(hulltemp, steamtemp, feedwatr);
		int boiling = (int) (boilBase * 24);
		this.hulltemp = Math.max(this.hulltemp - boilBase, 20);
		drainTank(water, waterCost(boiling, compression));
		fillTank(steam, boiling);

		if(!tilted) this.autoPort(getConPos());

		sendState();
	}

	/**
	 * The pressure half of the failure test, one tick of it: at or over {@link #MAX_PRESSURE} without
	 * falling by more than {@link #PRESSURE_RELIEF} since the last tick runs the timer down, and anything
	 * else resets it. True when it has run out.
	 */
	public boolean pressureFailing() {
		if((this.pressureLCE >= this.maxPressureLCE) && (pressureLCE >= lastPressureLCE - PRESSURE_RELIEF)) {
			generosityTimer--;
			return generosityTimer <= 0;
		}
		generosityTimer = GENEROSITY;
		return false;
	}

	private static SoundEvent stressSound(int i) {
		return switch(i) {
			case 0 -> LeafiaSounds.stressSounds_0.get();
			case 1 -> LeafiaSounds.stressSounds_1.get();
			case 2 -> LeafiaSounds.stressSounds_2.get();
			case 3 -> LeafiaSounds.stressSounds_3.get();
			case 4 -> LeafiaSounds.stressSounds_4.get();
			case 5 -> LeafiaSounds.stressSounds_5.get();
			default -> LeafiaSounds.stressSounds_6.get();
		};
	}

	/** CE's {@code loadTank(24, 25)} and {@code (26, 27)} - see the class note. */
	@Override
	protected void loadTanks() {
		carbonDioxide.loadTank(24, 25, slots, level.registryAccess());
		water.loadTank(26, 27, slots, level.registryAccess());
	}

	/** Upstream's {@code drain}: takes up to {@code amount}, never below empty. */
	public static int drainTank(FluidTank tank, int amount) {
		amount = Math.min(tank.getFill(), amount);
		tank.setFill(tank.getFill() - amount);
		return amount;
	}

	/** CE's {@code FluidTankNTM.fill(type, amount, true)} into the tank's own type: nothing for a non-positive amount, else up to full. */
	public static int fillTank(FluidTank tank, int amount) {
		if(amount <= 0) return 0;
		int added = Math.min(tank.getMaxFill() - tank.getFill(), amount);
		tank.setFill(tank.getFill() + added);
		return added;
	}

	double getHeatInSlot(int slot, LeafiaRodItem rod) {
		return rod.getFlux(slots[slot], true);
	}

	/** What a channel's rod reads off its neighbours this tick, summed in upstream's order. */
	public double detectedHeat(int slot, LeafiaRodItem rod) {
		double[] sum = {0};
		forEachNeighbour(slot, (s, divisor) -> sum[0] += getHeatInSlot(s, rod) / divisor);
		return sum[0];
	}

	double handleLeafiaFuel(int slot, double cool) {
		ItemStack stack = slots[slot];
		LeafiaRodItem rod = (LeafiaRodItem) stack.getItem();
		double detectedHeat = detectedHeat(slot, rod);
		rod.HeatFunction(stack, true, detectedHeat * (rods / 100f), cool, 20, Math.pow(pressureLCE / 30, 2) * 1000);
		ItemStack product = rod.getDecayProduct(stack);
		if(product != null) slots[slot] = product;
		CompoundTag data = LeafiaRodItem.data(stack);
		if(data != null) {
			avgHeat += (data.getDouble("heat") - 20) / 24;
			if(data.getBoolean("nuke")) {
				this.kill = true;
				explode();
				// upstream queues this for the next tick (LeafiaPassiveServer.queueFunction)
				rod.nuke(level, worldPosition.offset(0, 3, 0));
			}
			if(data.getInt("spillage") > SPILLAGE_LIMIT) {
				this.kill = true;
				explode();
			}
			return data.getDouble("cooled");
		}
		return 0; // failsafe
	}

	// ------------------------------------------------------------------------------ compression

	/** Upstream's {@code setCompression}: retypes the steam and rescales what is in it. */
	public void setCompression(FluidType newType) {
		short curCompression = compressionLevel(steam.getTankType());
		short newCompression = compressionLevel(newType);
		int amount = this.steam.getFill();
		steam.setTankType(newType);
		steam.setFill(amount * curCompression / newCompression);
	}

	/** Upstream's {@code updateCompression}. */
	public void updateCompression() {
		setCompression(compressionFluid(compression));
	}

	// -------------------------------------------------------------------------------- the meltdown

	/** Upstream's debris: 2 exchangers, 50 concrete and 50 blank, 30 shrapnel, 10 elements and 10 graphite. */
	@Override
	public void zirnoxDebris() {
		for(EntityZirnoxDebris.DebrisType type : debrisPlan()) spawnDebris(type);
	}

	/** The 152 pieces in upstream's throwing order. The base reactor's 1.7.10 meltdown throws 72. */
	public static List<EntityZirnoxDebris.DebrisType> debrisPlan() {
		List<EntityZirnoxDebris.DebrisType> plan = new ArrayList<>();
		for(int i = 0; i < 2; i++) {
			plan.add(EntityZirnoxDebris.DebrisType.EXCHANGER);
		}
		for(int i = 0; i < 50; i++) {
			plan.add(EntityZirnoxDebris.DebrisType.CONCRETE);
			plan.add(EntityZirnoxDebris.DebrisType.BLANK);
		}
		for(int i = 0; i < 30; i++) {
			plan.add(EntityZirnoxDebris.DebrisType.SHRAPNEL);
		}
		for(int i = 0; i < 10; i++) {
			plan.add(EntityZirnoxDebris.DebrisType.ELEMENT);
			plan.add(EntityZirnoxDebris.DebrisType.GRAPHITE);
		}
		return plan;
	}

	/** Upstream's {@code explode}, in upstream's order; see the class note and D-3020. */
	public void explode() {
		ItemStack prevStack = null;
		for(int i = 0; i < slots.length; i++) {
			prevStack = LeafiaRodItem.comparePriority(slots[i], prevStack);
			slots[i] = ItemStack.EMPTY;
		}

		double x = worldPosition.getX() + 0.5;
		double y = worldPosition.getY() + 0.5;
		double z = worldPosition.getZ() + 0.5;
		CompoundTag data = new CompoundTag();
		data.putString("type", "rbmkmush");
		data.putFloat("scale", 4);
		if(level instanceof ServerLevel server)
			PacketDispatcher.sendToPlayersNear(server, x, y, z, 250, new AuxParticlePacketNT(data, x, y, z));

		for(BlockPos cell : upstreamClearedCells(worldPosition))
			level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
		level.playSound(null, x, worldPosition.getY() + 2.5, z, NtmSounds.BLOCK_RBMK_EXPLOSION.get(), SoundSource.BLOCKS, 50.0F, 1.0F);

		if(prevStack != null && prevStack.getItem() instanceof LeafiaRodItem rod) {
			rod.resetDetonate();
			rod.detonateRadius = ROD_DETONATE_RADIUS;
			rod.detonateVisualsOnly = true;
			rod.detonate(level, worldPosition);
		}
		// D-3076: the fallout and the radiation come with every meltdown (upstream skipped them when an addon rod was inside, whose blast is visuals only)
		ExplosionNukeGeneric.waste(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), MELTDOWN_WASTE);
		ChunkRadiationManager.incrementRad(level, worldPosition, MELTDOWN_RAD, MELTDOWN_RAD_MAX);

		replaceWithWreck();

		level.explode(null, x, worldPosition.getY() + 2.5, z, MELTDOWN_EXPLOSION, true, Level.ExplosionInteraction.BLOCK);
		zirnoxDebris();
	}

	// -------------------------------------------------------------------------------- persistence

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		hulltemp = nbt.getDouble("hulltemp");
		pressureLCE = nbt.getDouble("pressure");
		rods = nbt.getByte("rods");
		rodsTarget = nbt.getByte("rodsD");
		if(nbt.contains("compression"))
			compression = nbt.getByte("compression");
	}

	/** Upstream's keys; its {@code "pressure"} double replaces the base reactor's int under the same key. */
	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putDouble("hulltemp", hulltemp);
		nbt.putDouble("pressure", pressureLCE);
		nbt.putByte("rods", rods);
		nbt.putByte("rodsD", rodsTarget);
		nbt.putByte("compression", compression);
	}

	// ------------------------------------------------------------------------------------- the GUI

	@Override
	@Nullable
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new ZirnoxMenu(id, playerInventory, this);
	}

	/** Upstream's state packet: every tick, to clients within {@link #PACKET_RANGE} blocks. */
	protected void sendState() {
		CompoundTag compound = new CompoundTag();
		water.writeToNBT(compound, "water");
		carbonDioxide.writeToNBT(compound, "carbondioxide");
		steam.writeToNBT(compound, "steam");
		LeafiaPacket._start(this)
				.__write(PacketKeys.HULL_TEMP.key, hulltemp)
				.__write(PacketKeys.PRESSURE.key, pressureLCE)
				.__write(PacketKeys.CONTROL_RODS.key, rods)
				.__write(PacketKeys.COMPRESSION.key, compression)
				.__write(PacketKeys.CONTROL_RODS_TARGET.key, rodsTarget)
				.__write(PacketKeys.OPENVALVE.key, valveOpen)
				.__write(PacketKeys.TANKS.key, compound)
				.__sendToClients(PACKET_RANGE);
	}

	/** The client half of upstream's tick: the vent wheel turns toward open or shut, and the dial jitters. */
	public void clientTick(RandomSource rand) {
		if(valveOpen && (valveLevel < 6))
			valveLevel++;
		if(!valveOpen && (valveLevel > 0))
			valveLevel--;
		if(rand.nextInt(5) == 0)
			dialX = 1;
		else
			dialX = rand.nextInt(3);
		if(rand.nextInt(3) == 0)
			dialY = rand.nextInt(2);
		else
			dialY = 1;
	}

	@Override
	public String getPacketIdentifier() {
		return "zirnox";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		if(key == PacketKeys.HULL_TEMP.key)
			hulltemp = (double) value;
		if(key == PacketKeys.PRESSURE.key)
			pressureLCE = (double) value;
		if(key == PacketKeys.CONTROL_RODS.key)
			rods = (byte) value;
		if(key == PacketKeys.CONTROL_RODS_TARGET.key)
			rodsTarget = (byte) value;
		if(key == PacketKeys.TANKS.key) {
			CompoundTag tag = (CompoundTag) value;
			water.readFromNBT(tag, "water");
			carbonDioxide.readFromNBT(tag, "carbondioxide");
			steam.readFromNBT(tag, "steam");
			if(steam.getTankType().equals(Fluids.STEAM))
				compression = 0;
			else if(steam.getTankType().equals(Fluids.HOTSTEAM))
				compression = 1;
			else if(steam.getTankType().equals(Fluids.SUPERHOTSTEAM))
				compression = 2;
			else if(steam.getTankType().equals(Fluids.ULTRAHOTSTEAM))
				compression = 3;
		}
		if(key == PacketKeys.OPENVALVE.key)
			valveOpen = (boolean) value;
	}

	/**
	 * The screen's three controls. Upstream applies them unchecked; here the sender must have this
	 * reactor's screen open and be in reach, the control rods must be 0 to 100 and the compression 0 to 3
	 * (D-1094). The screen never sends anything else.
	 */
	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) {
		if(!(plr != null && plr.containerMenu instanceof ZirnoxMenu menu && menu.reactor == this)) {
			PacketGuard.reject("LeafiaZirnoxTE", plr, "this reactor's screen is not open");
			return;
		}
		if(!PacketGuard.near(plr, worldPosition)) {
			PacketGuard.reject("LeafiaZirnoxTE", plr, "out of reach");
			return;
		}
		if(key == PacketKeys.CONTROL_RODS.key) {
			if(value instanceof Byte b && b >= 0 && b <= 100) rodsTarget = b;
			else PacketGuard.reject("LeafiaZirnoxTE control rods", plr, "value " + value);
		}
		if(key == PacketKeys.COMPRESSION.key) {
			if(value instanceof Byte b && b >= 0 && b <= 3) {
				compression = b;
				updateCompression();
			} else PacketGuard.reject("LeafiaZirnoxTE compression", plr, "value " + value);
		}
		if(key == PacketKeys.OPENVALVE.key) {
			if(value instanceof Boolean open) {
				level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
						NtmSounds.BLOCK_RBMK_AZ_5_COVER.get(), SoundSource.BLOCKS, 0.5F, 0.5F);
				valveOpen = open;
			} else PacketGuard.reject("LeafiaZirnoxTE valve", plr, "value " + value);
		}
	}

	@Override
	public void onPlayerValidate(Player plr) { }

	// ------------------------------------------------------------------- OpenComputers, as plain methods

	/** Upstream's {@code getTemp}: the hull, in degrees. */
	@Override
	public long getTemp() {
		return Math.round(hulltemp);
	}

	/** Upstream's {@code getPressure}: the CO2 pressure, in atmospheres. */
	@Override
	public long getPressure() {
		return Math.round(pressureLCE);
	}

	public byte getControl() {
		return rods;
	}

	/** Upstream's {@code setControl}: the control rods' target, clamped to 0-100. */
	public byte setControl(int level) {
		rodsTarget = (byte) Mth.clamp(level, 0, 100);
		return rodsTarget;
	}

	public Object[] getInfo() {
		return new Object[] {hulltemp, pressureLCE, water.getFill(), steam.getFill(), carbonDioxide.getFill(), rods};
	}

	@Override
	public String[] methods() {
		return new String[] {
				"getTemp",
				"getPressure",
				"getWater",
				"getSteam",
				"getCarbonDioxide",
				"getControl",
				"getInfo",
				"setControl"
		};
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	/** Upstream's six query keys; {@code compression} is the multiplier, ten to the setting. */
	@Override
	public Map<String, DataValue> getQueryData() {
		return ControllableLifecycle.values("temperature", (float) hulltemp, "pressure", (float) pressureLCE, "water", water.getFill(),
				"steam", steam.getFill(), "compression", (float) Math.pow(10, compression), "co2", carbonDioxide.getFill());
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("zirnox_ctrl_set_level");
	}

	/** {@code level} is the rods' target in percent, held to 0..100 as upstream held it; a NaN changes nothing. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("zirnox_ctrl_set_level")) {
			int target = ControllableLifecycle.clampedInt(e, "level", 0, 100);
			if(target != Integer.MIN_VALUE)
				rodsTarget = (byte) target;
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		super.onChunkUnloaded();
	}
}
