package com.leafia.contents.machines.powercores.ams.base;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.BlockDummyable;
import com.hbm.entity.effect.EntityCloudFleijaRainbow;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ISatChip;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCatalyst;
import com.hbm.items.special.ItemAMSCore;
import com.hbm.lib.Library;
import com.hbm.lib.ModDamageSource;
import com.hbm.saveddata.SatelliteSavedData;
import com.hbm.saveddata.satellites.SatelliteBase;
import com.hbm.saveddata.satellites.SatelliteResonator;
import com.hbm.util.ArmorUtil;
import com.hbm.util.DirPos;
import com.leafia.contents.fluids.traits.FT_DFCFuel;
import com.leafia.contents.machines.powercores.ams.base.container.AMSBaseMenu;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerTE;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterTE;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The AMC base: the antimatter reaction chamber's core, a sphere of light hung five blocks above
 * a 3x3x2 pedestal, fed fuel and coolant, steadied by four stabilizers and lit by an emitter nine
 * blocks up, and the thing that makes the power.
 *
 * <p>Upstream: {@code .../powercores/ams/base/AMSBaseTE.java}. Every number in it is upstream's
 * and is pinned by {@code LeafiaAMSTests}; where upstream's arithmetic is odd, the oddity is kept
 * and listed below (D-3002).
 *
 * <h2>How it runs</h2>
 *
 * <ul>
 * <li><b>Four tanks of 8,000 mB</b>, in the order upstream builds them: coolant A (default
 *     coolant), coolant B (default cryogel), fuel A (deuterium), fuel B (tritium). A fluid
 *     identifier in the "in" slot of a pair retypes its tank, and the identifier moves to the "out"
 *     slot. Coolants may be water, oil, coolant or cryogel and the two may not match; fuels are any
 *     fluid with a non-zero {@link #getFuelPower}, so deuterium (50), tritium (75) and every fluid
 *     carrying the addon's {@link FT_DFCFuel} trait at {@code (int) (modifier x 49.6794871795)}.
 *     <b>That trait is on {@code features.overhauls.fluidTraits}, which is off by default</b>, so
 *     out of the box deuterium and tritium are the only fuels there are.</li>
 * <li><b>Four stabilizers</b> six blocks out on the four axes, each only counted when it faces the
 *     right way and has not blown up. The restriction field is
 *     {@code round((a + b + c + d) x 25(a + b + c + d) / 40000)}, so one stabilizer at 100% gives
 *     a field of 6, two give 25, three 56 and four 100: it is quadratic in how many are working. A stabilizer
 *     fitted with a boosting focus adds one to the booster count.</li>
 * <li><b>The emitter</b> nine blocks up gives the core its efficiency. Its efficiency is copied
 *     only while an emitter is there, so a destroyed one leaves the last value standing.</li>
 * <li><b>It generates</b> only when four catalysts and a core sit in slots 8 to 12, three satellite
 *     chips tuned to three different resonator satellites sit in slots 13 to 15, the efficiency is
 *     above zero and both fuel tanks hold something. Power per tick is
 *     {@code coreBase x 2,000,000 x efficiency x 1.25^boosters x catalystPowerMod x gauss / 1000 x
 *     fuelA x fuelB}; heat per tick is {@code sqrt(coreHeat) x 220 x 1.25^boosters x (100 - field)
 *     x catalystHeatMod / (field / 100)}; each fuel tank loses {@code coreFuel x 100/15 x
 *     catalystFuelMod}. The catalysts' flat power bonus is read and then thrown away by upstream,
 *     which assigns {@code powerBase} again - kept.</li>
 * <li><b>Cooling</b> takes {@code strengthA x strengthB} off the heat every tick and 10 mB from
 *     each coolant tank: water 5, oil 15, coolant {@code heat / 250}, cryogel 25.</li>
 * <li><b>It explodes</b> when the heat passes 5,000 or the field is zero while generating:
 *     ten rainbow clouds within 100 blocks, a nuke of radius {@code 50 + (fuelA + fuelB) / 16000 x
 *     150}, and the core is removed. See below for how often.</li>
 * <li><b>It kills</b> while generating: 1,000 damage and three seconds of fire to everything
 *     within ten blocks of the ball that it can see (a player in a full hazmat suit is spared), and
 *     10,000 core damage to everything inside the ball, which grows with the fuel in the tanks.</li>
 * </ul>
 *
 * <h2>Upstream's oddities, kept</h2>
 *
 * <ol>
 * <li><b>The temperature term is always the same number.</b> The gauss curve is fed
 *     {@code (heat - maxHeat/2) / maxHeat} in <em>integer</em> division, which is 0 for any heat
 *     from 0 to 7,499, so the curve is its peak, 0.5642, and heat never changes the output. It
 *     was meant to be a bell around 50% heat.</li>
 * <li><b>No stabilizers means the core explodes twice.</b> With a field of zero the heat addition
 *     divides by zero, becomes infinity, and the core is set to {@code Integer.MAX_VALUE}: the
 *     heat check explodes it, then the field check explodes it again in the same tick, because
 *     {@code explode()} does not stop the tick. Twenty clouds and two nukes at one spot.
 *     (Reachable by a player: build the core, fuel it and run it with no stabilizers.)</li>
 * <li><b>The power sum is done in {@code float}.</b> {@code power += (float expression)} is
 *     {@code (long) (power + floatValue)}, a float addition, so a core holding more than about 16
 *     million loses the low bits of every tick's addition. Kept exactly.</li>
 * <li><b>Cryogel always cools by 25</b> when there is heat to remove, because its test is
 *     {@code heat > heat / 2}.</li>
 * <li><b>The window properties for efficiency and field are never sent</b>; the screen reads
 *     them from {@code LeafiaPacket} instead.</li>
 * </ol>
 *
 * <p>Differences, all deliberate: the inventory is the base mod's {@code ItemStack[] slots} and
 * saves under {@code items} rather than {@code inventory}; the satellite store is not marked
 * dirty on every call (upstream's {@code data.markDirty()} wrote the world file for nothing); and
 * the sync packet is the addon's {@code LeafiaPacket}, as upstream's, at 250 blocks.
 */
public class AMSBaseTE extends BlockEntityMachineBase implements LeafiaPacketReceiver,
		IFluidStandardReceiverMK2, IEnergyProviderMK2 {

	public static final long maxPower = 1000000000000000L;
	public static final int maxField = 100;
	public static final int maxEfficiency = 100;
	public static final int maxHeat = 5000;

	/** Upstream's {@code new ItemStackHandler(16)}. */
	public static final int SLOTS = 16;

	// Upstream's slot map, from AMSBaseContainer's comments.
	public static final int SLOT_COOLANT_A_IN = 0;
	public static final int SLOT_COOLANT_A_OUT = 1;
	public static final int SLOT_COOLANT_B_IN = 2;
	public static final int SLOT_COOLANT_B_OUT = 3;
	public static final int SLOT_FUEL_A_IN = 4;
	public static final int SLOT_FUEL_A_OUT = 5;
	public static final int SLOT_FUEL_B_IN = 6;
	public static final int SLOT_FUEL_B_OUT = 7;
	public static final int FIRST_CATALYST = 8;
	public static final int LAST_CATALYST = 11;
	public static final int SLOT_CORE = 12;
	public static final int FIRST_CHIP = 13;
	public static final int LAST_CHIP = 15;

	/** Upstream's tank indices. */
	public static final int TANK_COOLANT_A = 0;
	public static final int TANK_COOLANT_B = 1;
	public static final int TANK_FUEL_A = 2;
	public static final int TANK_FUEL_B = 3;

	public long power = 0;
	public int field = 0;
	public int efficiency = 0;
	public int heat = 0;
	public int age = 0;
	public int warning = 0;
	public int mode = 0;
	public boolean locked = false;
	public final FluidTank[] tanks;
	public int color = -1;

	/** The client's copy of {@link #hasResonators()}, which the server cannot make it work out. */
	public boolean syncResonators = false;

	public AMSBaseTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
		tanks = new FluidTank[4];
		tanks[TANK_COOLANT_A] = new FluidTank(Fluids.COOLANT, 8000);
		tanks[TANK_COOLANT_B] = new FluidTank(Fluids.CRYOGEL, 8000);
		tanks[TANK_FUEL_A] = new FluidTank(Fluids.DEUTERIUM, 8000);
		tanks[TANK_FUEL_B] = new FluidTank(Fluids.TRITIUM, 8000);
	}

	public AMSBaseTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.AMS_BASE.get(), pos, state);
	}

	// ---- inventory -------------------------------------------------------------------------

	/** Upstream's slots take anything by hand ({@code SlotItemHandler} over an unfiltered handler). */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	/** Upstream exposes no capability, so no hopper reaches the slots. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	/** Upstream's {@code isUseableByPlayer}: the same block entity, within the square root of 128. */
	@Override
	public boolean stillValid(Player player) {
		if(level == null || level.getBlockEntity(worldPosition) != this) return false;
		return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 128D;
	}

	@Override
	public String getName() {
		return "tile.ams_base.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new AMSBaseMenu(containerId, playerInventory, this);
	}

	// ---- ports -----------------------------------------------------------------------------

	/** One below the core, and one beyond each of the four extras. */
	public DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(worldPosition.below(), Direction.DOWN),
				new DirPos(worldPosition.relative(Direction.NORTH, 2), Direction.NORTH),
				new DirPos(worldPosition.relative(Direction.SOUTH, 2), Direction.SOUTH),
				new DirPos(worldPosition.relative(Direction.EAST, 2), Direction.EAST),
				new DirPos(worldPosition.relative(Direction.WEST, 2), Direction.WEST)
		};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return tanks;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	// ---- the tick --------------------------------------------------------------------------

	/** Four catalysts and a core: upstream's {@code isSetUp}, which the client reads from the slots it has. */
	public boolean isSetUp() {
		for(int i = FIRST_CATALYST; i <= LAST_CATALYST; i++)
			if(!(slots[i].getItem() instanceof ItemCatalyst)) return false;
		return slots[SLOT_CORE].getItem() instanceof ItemAMSCore;
	}

	/** The stabilizer six blocks out along {@code (dx, dz)} if it is working and faces {@code wanted}. */
	private AMSStabilizerTE stabilizerAt(int dx, int dz, int wanted) {
		if(level.getBlockEntity(worldPosition.offset(dx, 0, dz)) instanceof AMSStabilizerTE te) {
			if(!te.locked && AMSStabilizerTE.rotateShitfuck(te.getBlockState().getValue(BlockDummyable.DIR)) - 10 == wanted)
				return te;
		}
		return null;
	}

	@Override
	public void updateEntity() {

		boolean isSetUp = isSetUp();

		if(level.isClientSide) {
			if(!hasResonators()) warning = 3;
			else if(!isSetUp) warning = 2;
			else if(efficiency <= 0) warning = 1;
			else warning = 0;
			return;
		}

		if(!locked) {

			for(DirPos con : getConPos()) {
				tryProvide(level, con);
				for(int i = 0; i < 4; i++)
					trySubscribe(tanks[i].getTankType(), level, con);
			}

			LeafiaUtil.setTypeBy(tanks[TANK_COOLANT_A], SLOT_COOLANT_A_IN, SLOT_COOLANT_A_OUT, slots, AMSBaseTE::isValidCoolant, tanks[TANK_COOLANT_B].getTankType());
			LeafiaUtil.setTypeBy(tanks[TANK_COOLANT_B], SLOT_COOLANT_B_IN, SLOT_COOLANT_B_OUT, slots, AMSBaseTE::isValidCoolant, tanks[TANK_COOLANT_A].getTankType());
			LeafiaUtil.setTypeBy(tanks[TANK_FUEL_A], SLOT_FUEL_A_IN, SLOT_FUEL_A_OUT, slots, AMSBaseTE::isValidFuel, tanks[TANK_FUEL_B].getTankType());
			LeafiaUtil.setTypeBy(tanks[TANK_FUEL_B], SLOT_FUEL_B_IN, SLOT_FUEL_B_OUT, slots, AMSBaseTE::isValidFuel, tanks[TANK_FUEL_A].getTankType());

			age++;
			if(age >= 20) age = 0;

			int f1 = 0, f2 = 0, f3 = 0, f4 = 0;
			int booster = 0;

			AMSStabilizerTE east = stabilizerAt(6, 0, 4);
			if(east != null) {
				f1 = east.efficiency;
				if(east.mode == 2) booster++;
			}
			AMSStabilizerTE west = stabilizerAt(-6, 0, 5);
			if(west != null) {
				f2 = west.efficiency;
				if(west.mode == 2) booster++;
			}
			AMSStabilizerTE south = stabilizerAt(0, 6, 2);
			if(south != null) {
				f3 = south.efficiency;
				if(south.mode == 2) booster++;
			}
			AMSStabilizerTE north = stabilizerAt(0, -6, 3);
			if(north != null) {
				f4 = north.efficiency;
				if(north.mode == 2) booster++;
			}

			this.field = Math.round(calcField(f1, f2, f3, f4));

			mode = 0;
			if(field > 0) mode = 1;
			if(booster > 0) mode = 2;

			if(level.getBlockEntity(worldPosition.offset(0, 9, 0)) instanceof AMSEmitterTE emitter) {
				this.efficiency = emitter.efficiency;
			}

			this.color = -1;

			float powerMod = 1;
			float heatMod = 1;
			float fuelMod = 1;
			long powerBase = 0;
			int heatBase = 0;
			int fuelBase = 0;

			if(isSetUp && hasResonators() && efficiency > 0) {

				int a = ((ItemCatalyst) slots[8].getItem()).getColor();
				int b = ((ItemCatalyst) slots[9].getItem()).getColor();
				int c = ((ItemCatalyst) slots[10].getItem()).getColor();
				int d = ((ItemCatalyst) slots[11].getItem()).getColor();

				int e = calcAvgHex(a, b);
				int f = calcAvgHex(c, d);

				this.color = calcAvgHex(e, f);

				for(int i = FIRST_CATALYST; i <= LAST_CATALYST; i++) {
					powerBase += ItemCatalyst.getPowerAbs(slots[i]);
					powerMod *= ItemCatalyst.getPowerMod(slots[i]);
					heatMod *= ItemCatalyst.getHeatMod(slots[i]);
					fuelMod *= ItemCatalyst.getFuelMod(slots[i]);
				}

				// Upstream assigns, not adds: the catalysts' flat bonus above is discarded.
				Burn burn = burnFor(slots[SLOT_CORE], this.efficiency, booster, field);
				powerBase = burn.powerBase();
				heatBase = burn.heatBase();
				fuelBase = burn.fuelBase();

				FluidTank fuelA = tanks[TANK_FUEL_A];
				FluidTank fuelB = tanks[TANK_FUEL_B];

				if(getFuelPower(fuelA.getTankType()) > 0 && getFuelPower(fuelB.getTankType()) > 0
						&& fuelA.getFill() > 0 && fuelB.getFill() > 0) {

					power = generate(power, powerBase, powerMod, heat, getFuelPower(fuelA.getTankType()), getFuelPower(fuelB.getTankType()));
					heat = heatAfter(heat, heatBase, heatMod, this.field);
					drain(fuelA, (int) (fuelBase * fuelMod));
					drain(fuelB, (int) (fuelBase * fuelMod));

					radiation();

					if(heat > maxHeat) {
						explode();
						heat = maxHeat;
					}

					if(field <= 0) explode();
				}
			}

			if(power > maxPower) power = maxPower;

			if(heat > 0 && tanks[TANK_COOLANT_A].getFill() > 0 && tanks[TANK_COOLANT_B].getFill() > 0) {

				heat -= (getCoolingStrength(tanks[TANK_COOLANT_A].getTankType()) * getCoolingStrength(tanks[TANK_COOLANT_B].getTankType()));

				drain(tanks[TANK_COOLANT_A], 10);
				drain(tanks[TANK_COOLANT_B], 10);

				if(heat < 0) heat = 0;
			}

		} else {
			field = 0;
			efficiency = 0;
			power = 0;
			warning = 33;
		}

		CompoundTag sendTanks = new CompoundTag();
		for(int i = 0; i < 4; i++)
			tanks[i].writeToNBT(sendTanks, "tank" + i);

		LeafiaPacket._start(this)
				.__write(0, power)
				.__write(1, locked)
				.__write(2, efficiency)
				.__write(3, heat)
				.__write(4, sendTanks)
				.__write(5, hasResonators())
				.__write(6, color)
				.__write(7, field)
				.__sendToClients(250);
	}

	/** What a core is worth to a running machine, before the catalysts' multipliers. */
	public record Burn(long powerBase, int heatBase, int fuelBase) {}

	/**
	 * Upstream's three bases, in upstream's order and types.
	 *
	 * <p>{@code powerBase} is the core's power times 2,000,000, times the efficiency, times
	 * {@code 1.25^boosters}; {@code heatBase} is {@code sqrt(coreHeat) x 220} truncated, times
	 * {@code 1.25^boosters}, times {@code 100 - field}; {@code fuelBase} is the core's fuel times
	 * {@code 100 / 15} truncated. Each compound assignment truncates, so a booster count shaves
	 * the fraction off at every step.
	 */
	public static Burn burnFor(ItemStack core, int efficiency, int booster, int field) {

		long powerBase = ItemAMSCore.getPowerBase(core) * 2000000L;
		int heatBase = (int) (Math.sqrt(ItemAMSCore.getHeatBase(core)) * 220);
		int fuelBase = (int) (ItemAMSCore.getFuelBase(core) * (100 / 15f));

		powerBase *= efficiency;
		powerBase *= Math.pow(1.25F, booster);
		heatBase *= Math.pow(1.25F, booster);
		heatBase *= (100 - field);

		return new Burn(powerBase, heatBase, fuelBase);
	}

	/**
	 * One generating tick's addition to the buffer, upstream's line in upstream's types: the product
	 * is a {@code float} ({@code long x float}), and {@code power += float} is a float addition, so
	 * the buffer is {@code (long) ((float) power + addition)}. The bell is fed
	 * {@code (heat - maxHeat / 2) / maxHeat} in integer division, which is why heat does nothing.
	 */
	public static long generate(long power, long powerBase, float powerMod, int heat, int fuelPowerA, int fuelPowerB) {
		power += (powerBase * powerMod * gauss(1, (heat - (maxHeat / 2)) / maxHeat)) / 1000 * fuelPowerA * fuelPowerB;
		return power;
	}

	/**
	 * One generating tick's heat: {@code heatBase x heatMod / (field / 100)} in float. With no field
	 * the divisor is zero, the quotient is infinity (or NaN, if the numerator is zero too), and the
	 * cast to int is {@code Integer.MAX_VALUE} (or 0).
	 */
	public static int heatAfter(int heat, int heatBase, float heatMod, int field) {
		heat += (heatBase * heatMod) / (float) (field / 100F);
		return heat;
	}

	/** Upstream's {@code drain(amount, true)}: takes up to {@code amount}, never more than is there. */
	private static void drain(FluidTank tank, int amount) {
		int taken = Math.min(Math.max(amount, 0), tank.getFill());
		tank.setFill(tank.getFill() - taken);
	}

	/**
	 * The ball's two kill zones: everything within ten blocks that can see it takes 1,000 and three
	 * seconds of fire, and everything inside it takes 10,000.
	 *
	 * <p>The ball is centred six blocks above the core's cell, and its radius is
	 * {@code 0.6 x (0.5 + (fillA / capA + fillB / capB) x 2.25)}: 0.3 empty, 3.3 full.
	 */
	private void radiation() {

		double maxSize = 5;
		double minSize = 0.5;
		double scale = minSize;
		scale += ((((double) this.tanks[2].getFill()) / ((double) this.tanks[2].getMaxFill()))
				+ (((double) this.tanks[3].getFill()) / ((double) this.tanks[3].getMaxFill()))) * ((maxSize - minSize) / 2);

		scale *= 0.60;

		BlockPos pos = worldPosition;

		List<Entity> list = level.getEntities((Entity) null, new AABB(pos.getX() - 10 + 0.5, pos.getY() - 10 + 0.5 + 6, pos.getZ() - 10 + 0.5,
				pos.getX() + 10 + 0.5, pos.getY() + 10 + 0.5 + 6, pos.getZ() + 10 + 0.5));

		for(Entity e : list) {
			if(!(e instanceof Player player && ArmorUtil.checkForHazmat(player)))
				if(!Library.isObstructed(level, pos.getX() + 0.5, pos.getY() + 0.5 + 6, pos.getZ() + 0.5, e.getX(), e.getY() + e.getEyeHeight(), e.getZ())) {
					e.hurt(level.damageSources().source(ModDamageSource.ams), 1000);
					e.igniteForSeconds(3);
				}
		}

		List<Entity> list2 = level.getEntities((Entity) null, new AABB(pos.getX() - scale + 0.5, pos.getY() - scale + 0.5 + 6, pos.getZ() - scale + 0.5,
				pos.getX() + scale + 0.5, pos.getY() + scale + 0.5 + 6, pos.getZ() + scale + 0.5));

		for(Entity e : list2) {
			if(!(e instanceof Player player && ArmorUtil.checkForHaz2(player)))
				e.hurt(level.damageSources().source(ModDamageSource.amsCore), 10000);
		}
	}

	/**
	 * Ten rainbow clouds scattered within 100 blocks, a nuke, and the core gone.
	 *
	 * <p>Upstream does not stop the tick after this, so a second call in the same tick is possible
	 * (see the class doc). Upstream's is {@code void}; this returns what it spawned - the ten clouds
	 * and then the nuke - so a test can count them, since a cloud may land in a chunk nobody has
	 * loaded.
	 */
	public List<Entity> explode() {

		List<Entity> spawned = new ArrayList<>();

		if(level.isClientSide) return spawned;

		for(int i = 0; i < 10; i++) {

			EntityCloudFleijaRainbow cloud = new EntityCloudFleijaRainbow(level, 100);
			cloud.setPos(worldPosition.getX() + level.random.nextInt(201) - 100,
					worldPosition.getY() + level.random.nextInt(201) - 100,
					worldPosition.getZ() + level.random.nextInt(201) - 100);
			level.addFreshEntity(cloud);
			spawned.add(cloud);
		}

		int radius = explosionRadius();

		EntityNukeExplosionMK5 nuke = EntityNukeExplosionMK5.statFac(level, radius, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
		level.addFreshEntity(nuke);
		spawned.add(nuke);

		level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);

		return spawned;
	}

	/** {@code (int) (50 + (fuelA + fuelB) / 16000 x 150)}: 50 empty, 200 with both tanks full. */
	public int explosionRadius() {
		return (int) (50 + (double) (tanks[2].getFill() + tanks[3].getFill()) / 16000D * 150);
	}

	// ---- the numbers -----------------------------------------------------------------------

	public static boolean isValidCoolant(FluidType type) {
		return type.equals(Fluids.WATER) || type.equals(Fluids.OIL) || type.equals(Fluids.COOLANT) || type.equals(Fluids.CRYOGEL);
	}

	/** Upstream's {@code getCoolingStrength}. Note cryogel's test is {@code heat > heat / 2}. */
	public int getCoolingStrength(FluidType type) {
		if(type == null) return 0;
		else if(type == Fluids.WATER) return 5;
		else if(type == Fluids.OIL) return 15;
		else if(type == Fluids.COOLANT) return this.heat / 250;
		else if(type == Fluids.CRYOGEL) return this.heat > heat / 2 ? 25 : 5;
		else return 0;
	}

	public static boolean isValidFuel(FluidType type) {
		return getFuelPower(type) > 0;
	}

	public static int getFuelPower(FluidType type) {
		if(type == null) return 0;
		else if(type == Fluids.DEUTERIUM) return 50;
		else if(type == Fluids.TRITIUM) return 75;
		else {
			if(type.hasTrait(FT_DFCFuel.class))
				return dfcFuelPower(type.getTrait(FT_DFCFuel.class).getModifier());
			return 0;
		}
	}

	/** A DFC fuel's burn rate: {@code (int) (modifier x 49.6794871795)}, the modifier a float. */
	public static int dfcFuelPower(float modifier) {
		return (int) (modifier * 49.6794871795);
	}

	/** Upstream's bell: {@code (1 / sqrt(a x pi)) x e^(-x^2 / 0.1)}. */
	public static float gauss(float a, float x) {

		// Greater values -> less difference of temperate impact
		double amplifier = 0.10;

		return (float) ((1 / Math.sqrt(a * Math.PI)) * Math.pow(Math.E, -1 * Math.pow(x, 2) / amplifier));
	}

	/** The restriction field before rounding: {@code sum x 25 sum / 40000}, in {@code float}. */
	public static float calcField(int a, int b, int c, int d) {
		return (float) (a + b + c + d) * (a * 25 + b * 25 + c * 25 + d * 25) / 40000;
	}

	/** The channel-wise average of two colours, each channel halved with integer division. */
	public static int calcAvgHex(int h1, int h2) {

		int r1 = ((h1 & 0xFF0000) >> 16);
		int g1 = ((h1 & 0x00FF00) >> 8);
		int b1 = ((h1 & 0x0000FF) >> 0);

		int r2 = ((h2 & 0xFF0000) >> 16);
		int g2 = ((h2 & 0x00FF00) >> 8);
		int b2 = ((h2 & 0x0000FF) >> 0);

		int r = (((r1 + r2) / 2) << 16);
		int g = (((g1 + g2) / 2) << 8);
		int b = (((b1 + b2) / 2) << 0);

		return r | g | b;
	}

	public long getPowerScaled(long i) {
		return (power * i) / maxPower;
	}

	public int getEfficiencyScaled(int i) {
		return (efficiency * i) / maxEfficiency;
	}

	public int getFieldScaled(int i) {
		return (field * i) / maxField;
	}

	public int getHeatScaled(int i) {
		return (heat * i) / maxHeat;
	}

	/**
	 * Three satellite chips, each tuned to a different resonator satellite. The client reads what
	 * the server last told it.
	 */
	public boolean hasResonators() {

		if(level != null && level.isClientSide) return syncResonators;

		for(int i = FIRST_CHIP; i <= LAST_CHIP; i++)
			if(slots[i].isEmpty() || slots[i].getItem() != ModItems.sat_chip.get()) return false;

		if(!(level instanceof ServerLevel server)) return false;

		SatelliteSavedData data = SatelliteSavedData.getData(server);

		int i1 = ISatChip.getFreqS(slots[13]);
		int i2 = ISatChip.getFreqS(slots[14]);
		int i3 = ISatChip.getFreqS(slots[15]);

		SatelliteBase s1 = data.getSatFromFreq(i1);
		SatelliteBase s2 = data.getSatFromFreq(i2);
		SatelliteBase s3 = data.getSatFromFreq(i3);

		return s1 != null && s2 != null && s3 != null
				&& s1 instanceof SatelliteResonator && s2 instanceof SatelliteResonator && s3 instanceof SatelliteResonator
				&& i1 != i2 && i1 != i3 && i2 != i3;
	}

	// ---- persistence and sync --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);

		nbt.putLong("power", power);
		nbt.putInt("field", field);
		nbt.putInt("efficiency", efficiency);
		nbt.putInt("heat", heat);
		nbt.putBoolean("locked", locked);
		tanks[0].writeToNBT(nbt, "coolantA");
		tanks[1].writeToNBT(nbt, "coolantB");
		tanks[2].writeToNBT(nbt, "fuelA");
		tanks[3].writeToNBT(nbt, "fuelB");
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);

		power = nbt.getLong("power");
		field = nbt.getInt("field");
		efficiency = nbt.getInt("efficiency");
		heat = nbt.getInt("heat");
		locked = nbt.getBoolean("locked");
		if(nbt.contains("coolantA")) tanks[0].readFromNBT(nbt, "coolantA");
		if(nbt.contains("coolantB")) tanks[1].readFromNBT(nbt, "coolantB");
		if(nbt.contains("fuelA")) tanks[2].readFromNBT(nbt, "fuelA");
		if(nbt.contains("fuelB")) tanks[3].readFromNBT(nbt, "fuelB");
	}

	@Override
	public String getPacketIdentifier() {
		return "AMS_BASE";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> power = (long) value;
			case 1 -> locked = (boolean) value;
			case 2 -> efficiency = (int) value;
			case 3 -> heat = (int) value;
			case 4 -> {
				CompoundTag tag = (CompoundTag) value;
				for(int i = 0; i < 4; i++)
					tanks[i].readFromNBT(tag, "tank" + i);
			}
			case 5 -> syncResonators = (boolean) value;
			case 6 -> color = (int) value;
			case 7 -> field = (int) value;
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	/** Upstream's infinite extent, as the box that holds the ball, the pedestal and the emitter's beam. */
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(7D, 0D, 7D).expandTowards(0D, 10D, 0D);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, AMSBaseTE base) {
		base.updateEntity();
	}

	/** The block entity at {@code pos}, if it is one. */
	public static AMSBaseTE at(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof AMSBaseTE te ? te : null;
	}
}
