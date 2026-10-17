package com.leafia.contents.machines.powercores.ams.emitter;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.sound.NtmSounds;
import com.hbm.util.DirPos;
import com.hbm.lib.Library;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.powercores.ams.emitter.container.AMSEmitterMenu;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The AMC emitter: the top of the machine, a laser nine blocks above the base that gives the
 * core its efficiency, and runs hotter the harder it is driven.
 *
 * <p>Upstream: {@code .../powercores/ams/emitter/AMSEmitterTE.java}. Four slots (a fluid
 * identifier in and out, the muzzle, a battery), a 16,000 mB tank of coolant, and a buffer of
 * 100,000,000 HE it burns 2.5% of per tick. Every number is upstream's (D-3002).
 *
 * <h2>How it works</h2>
 *
 * <ul>
 * <li><b>Efficiency</b> is {@code round(100 x bell(power, heat - 1250))}: a Gaussian in heat
 *     centred on half the {@code 2,500} limit, whose height falls with the square root of the
 *     buffer, normalised so that a full buffer at the centre of the bell gives 100%. Below the
 *     peak the emitter is cold and weak; at the top it is overheating and weak, and between the
 *     two it is the best.</li>
 * <li><b>Heat</b> rises by the efficiency every tick it is running, and the coolant takes it off.
 *     Cryogel (15 mB a tick, {@code 10} off a tick above {@code 1250}), coolant ({@code 15} mB,
 *     5 off above 625) and water (45 mB, 2 off above 2,125); with less than that in the tank, or a
 *     fluid that is none of the three, there is no cooling and the heat simply adds. The
 *     coolant is only drunk while there is heat to remove.</li>
 * <li><b>Without a muzzle</b> in slot 2 the efficiency is forced to zero and the warning is
 *     {@code 2}; the heat it has just gained stays.</li>
 * <li><b>Over 2,500 it breaks</b>: heat is clamped, the emitter locks (zero efficiency, zero
 *     power, a trail of fire from its foot, warning 3) and five bursts and two sounds go off.
 *     It never recovers.</li>
 * </ul>
 *
 * <h2>Upstream's quirks, kept</h2>
 *
 * <ol>
 * <li>The warning is a plain overwrite in a fixed order, so a later test wins: an unpowered
 *     emitter that also has no muzzle ends on {@code 2}, and one that is out of coolant ends on 2
 *     whatever else is true.</li>
 * <li>The heat added when the tank cannot afford the coolant is added <em>in the same line</em> as
 *     when the tank holds none of the three fluids, so the two cannot be told apart from outside
 *     except by the warning, which only the second one sets.</li>
 * </ol>
 */
public class AMSEmitterTE extends BlockEntityMachineBase implements LeafiaPacketReceiver,
		IFluidStandardReceiverMK2, IEnergyReceiverMK2 {

	public static final long maxPower = 100000000;
	public static final int maxEfficiency = 100;
	public static final int maxHeat = 2500;

	/** Upstream's {@code new ItemStackHandler(4)}. */
	public static final int SLOTS = 4;
	public static final int SLOT_FLUID_IN = 0;
	public static final int SLOT_FLUID_OUT = 1;
	public static final int SLOT_MUZZLE = 2;
	public static final int SLOT_BATTERY = 3;

	public long power = 0;
	public int efficiency = 0;
	public int heat = 0;
	public int age = 0;
	public int warning = 0;
	public boolean locked = false;
	public final FluidTank tank;

	public AMSEmitterTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
		tank = new FluidTank(Fluids.COOLANT, 16000);
	}

	public AMSEmitterTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.AMS_EMITTER.get(), pos, state);
	}

	// ---- inventory -------------------------------------------------------------------------

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return true;
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return new int[0];
	}

	@Override
	public boolean stillValid(Player player) {
		if(level == null || level.getBlockEntity(worldPosition) != this) return false;
		return player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 128D;
	}

	@Override
	public String getName() {
		return "tile.ams_emitter.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new AMSEmitterMenu(containerId, playerInventory, this);
	}

	// ---- ports -----------------------------------------------------------------------------

	/** The block above the top of the machine, and the four around it. */
	public DirPos[] getConPos() {
		BlockPos top = worldPosition.above(6);
		return new DirPos[] {
				new DirPos(top, Direction.UP),
				new DirPos(top.relative(Direction.NORTH), Direction.UP),
				new DirPos(top.relative(Direction.SOUTH), Direction.UP),
				new DirPos(top.relative(Direction.EAST), Direction.UP),
				new DirPos(top.relative(Direction.WEST), Direction.UP)
		};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
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

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		if(!locked) {

			for(DirPos con : getConPos()) {
				trySubscribe(level, con);
				trySubscribe(tank.getTankType(), level, con);
			}

			LeafiaUtil.setTypeOnly(tank, SLOT_FLUID_IN, SLOT_FLUID_OUT, slots, Fluids.CRYOGEL, Fluids.COOLANT, Fluids.WATER);

			if(power > 0) {
				// " - (maxHeat / 2)" offsets the centre to 50% instead of 0%
				efficiency = Math.round(calcEffect(power, heat - (maxHeat / 2)) * 100);
				power -= Math.ceil(power * 0.025);
				warning = 0;
			} else {
				efficiency = 0;
				warning = 1;
			}

			if(tank.getTankType().equals(Fluids.CRYOGEL)) {

				if(tank.getFill() >= 15) {
					if(heat > 0) {
						drain(15);
					}

					if(heat <= maxHeat / 2) {
						if(efficiency > 0) heat += efficiency;
						else for(int i = 0; i < 10; i++) if(heat > 0) heat--;
					}

					for(int i = 0; i < 10; i++)
						if(heat > maxHeat / 2) heat--;
				} else {
					heat += efficiency;
				}

			} else if(tank.getTankType().equals(Fluids.COOLANT)) {

				if(tank.getFill() >= 15) {
					if(heat > 0) {
						drain(15);
					}

					if(heat <= maxHeat / 4) {
						if(efficiency > 0) heat += efficiency;
						else for(int i = 0; i < 5; i++) if(heat > 0) heat--;
					}

					for(int i = 0; i < 5; i++)
						if(heat > maxHeat / 4) heat--;
				} else {
					heat += efficiency;
				}

			} else if(tank.getTankType().equals(Fluids.WATER)) {

				if(tank.getFill() >= 45) {
					if(heat > 0) {
						drain(45);
					}

					if(heat <= maxHeat * 0.85) {
						if(efficiency > 0) heat += efficiency;
						else for(int i = 0; i < 2; i++) if(heat > 0) heat--;
					}

					for(int i = 0; i < 2; i++)
						if(heat > maxHeat * 0.85) heat--;
				} else {
					heat += efficiency;
				}

			} else {
				heat += efficiency;
				warning = 2;
			}

			if(!slots[SLOT_MUZZLE].isEmpty()) {
				if(slots[SLOT_MUZZLE].getItem() != LeafiaItems.ams_muzzle.get()) {
					this.efficiency = 0;
					this.warning = 2;
				}
			} else {
				this.efficiency = 0;
				this.warning = 2;
			}

			if(tank.getFill() <= 5 || heat > maxHeat * 0.9)
				warning = 2;

			if(heat > maxHeat) {
				heat = maxHeat;
				locked = true;

				double x = worldPosition.getX() + 0.5;
				double y = worldPosition.getY();
				double z = worldPosition.getZ() + 0.5;

				ExplosionLarge.spawnBurst(level, x, y, z, 36, 3);
				ExplosionLarge.spawnBurst(level, x, y, z, 36, 2.5);
				ExplosionLarge.spawnBurst(level, x, y, z, 36, 2);
				ExplosionLarge.spawnBurst(level, x, y, z, 36, 1.5);
				ExplosionLarge.spawnBurst(level, x, y, z, 36, 1);

				level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
						NtmSounds.ENTITY_OLD_EXPLOSION.get(), SoundSource.BLOCKS, 10.0F, 1);
				level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
						LeafiaSounds.machineDestroyed.get(), SoundSource.BLOCKS, 10.0F, 1.0F);
			}

			power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);

		} else {
			// fire particles n stuff
			ExplosionLarge.spawnBurst(level, worldPosition.getX() + 0.5, worldPosition.getY() - 0.5, worldPosition.getZ() + 0.5,
					level.random.nextInt(10), 1);

			efficiency = 0;
			power = 0;
			warning = 3;
		}

		LeafiaPacket._start(this)
				.__write(0, power)
				.__write(1, locked)
				.__write(2, efficiency)
				.__write(3, heat)
				.__write(4, tank.getTankType().getID())
				.__write(5, tank.getFill())
				.__sendToClients(250);
	}

	private void drain(int amount) {
		tank.setFill(tank.getFill() - Math.min(amount, tank.getFill()));
	}

	/** Upstream's bell, shared by the emitter and the stabilizer. */
	public static float gauss(float a, float x) {

		// Greater values -> less difference of temperate impact
		double amplifier = 0.10;

		return (float) ((1 / Math.sqrt(a * Math.PI)) * Math.pow(Math.E, -1 * Math.pow(x, 2) / amplifier));
	}

	/** {@code bell(1 / power, x / maxHeat) x sqrt(2 pi) / (sqrt 2 x sqrt maxPower)}, in upstream's types. */
	public static float calcEffect(float a, float x) {
		return (float) (gauss(1 / a, x / maxHeat) * Math.sqrt(Math.PI * 2) / (Math.sqrt(2) * Math.sqrt(maxPower)));
	}

	public long getPowerScaled(long i) {
		return (power * i) / maxPower;
	}

	public int getEfficiencyScaled(int i) {
		return (efficiency * i) / maxEfficiency;
	}

	public int getHeatScaled(int i) {
		return (heat * i) / maxHeat;
	}

	// ---- persistence and sync --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);

		nbt.putLong("power", power);
		tank.writeToNBT(nbt, "coolant");
		nbt.putInt("efficiency", efficiency);
		nbt.putInt("heat", heat);
		nbt.putBoolean("locked", locked);
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);

		power = nbt.getLong("power");
		if(nbt.contains("coolant")) tank.readFromNBT(nbt, "coolant");
		efficiency = nbt.getInt("efficiency");
		heat = nbt.getInt("heat");
		locked = nbt.getBoolean("locked");
	}

	@Override
	public String getPacketIdentifier() {
		return "AMS_EMTR";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> power = (long) value;
			case 1 -> locked = (boolean) value;
			case 2 -> efficiency = (int) value;
			case 3 -> heat = (int) value;
			case 4 -> {
				FluidType type = com.hbm.inventory.fluid.Fluids.fromID((int) value);
				if(!tank.getTankType().equals(type)) {
					int fill = tank.getFill();
					tank.setTankType(type);
					tank.setFill(fill);
				}
			}
			case 5 -> tank.setFill((int) value);
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	/** The machine, its six top blocks, and the beam that runs seven blocks down from its foot. */
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(3D, 0D, 3D).expandTowards(0D, 7D, 0D).expandTowards(0D, -9D, 0D);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, AMSEmitterTE emitter) {
		emitter.updateEntity();
	}
}
