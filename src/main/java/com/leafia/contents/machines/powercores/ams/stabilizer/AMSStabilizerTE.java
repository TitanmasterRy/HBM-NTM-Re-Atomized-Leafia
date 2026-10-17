package com.leafia.contents.machines.powercores.ams.stabilizer;

import com.hbm.blockentity.BlockEntityMachineBase;
import com.hbm.blocks.BlockDummyable;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.sound.NtmSounds;
import com.hbm.util.DirPos;
import com.hbm.util.ParticleUtil;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.powercores.ams.stabilizer.container.AMSStabilizerMenu;
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
 * The AMC stabilizer: one of four restriction-field generators six blocks from the base, each a
 * five-wide wall that throws a beam at the ball and is worth up to a quarter of its field.
 *
 * <p>Upstream: {@code .../powercores/ams/stabilizer/AMSStabilizerTE.java}, registered as
 * {@code ams_limiter}. It is the emitter's twin - four slots, a 100,000 HE a tick buffer of
 * 10,000,000, a heat meter of 2,500, the same bell for efficiency and the same three coolants -
 * and differs in what it does with them (D-3002):
 *
 * <ul>
 * <li>The tank holds 8,000 mB, and the coolant is cheaper: cryogel 5 mB a tick, coolant 5, water
 *     15. The identifier retypes it to water, coolant or cryogel.</li>
 * <li>Slot 2 is a <b>focus</b>, not a muzzle. A limiting focus sets {@link #mode} to 1 and a
 *     boosting focus to 2, which is what the base reads (a booster adds one to its count and
 *     multiplies its power and heat by 1.25); anything else, or nothing, forces the efficiency to
 *     zero with warning 2.</li>
 * <li>Locked, it burns: a flame rises from one end of the wall, picked by which way the block
 *     faces, and the explosion is a shockwave and a burst of 24 rather than five bursts.</li>
 * </ul>
 *
 * <h2>The four directions</h2>
 *
 * Upstream reads the facing out of the block's metadata and rotates it a quarter turn with
 * {@code rotateShitfuck} (the name is upstream's): {@code EnumFacing.byIndex(meta - 10)
 * .rotateY().getIndex() + 10}. Here the facing is the block's {@code DIR}, and
 * {@link #rotateShitfuck(Direction)} is the same sum - rotate clockwise, take the index - so the
 * base's four checks ({@code == 4} at +x and so on) read the same numbers. A stabilizer faces the
 * base when {@code rotateShitfuck - 10} is the index of the side the base is on.
 */
public class AMSStabilizerTE extends BlockEntityMachineBase implements LeafiaPacketReceiver,
		IFluidStandardReceiverMK2, IEnergyReceiverMK2 {

	/**
	 * Upstream's {@code rotateShitfuck(meta)}, which took the block metadata: the clockwise
	 * neighbour of the facing, by index (down 0, up 1, north 2, south 3, west 4, east 5), plus ten.
	 */
	public static int rotateShitfuck(Direction facing) {
		return facing.getClockWise().get3DDataValue() + 10;
	}

	public static final long maxPower = 10000000;
	public static final int maxEfficiency = 100;
	public static final int maxHeat = 2500;

	/** Upstream's {@code new ItemStackHandler(4)}. */
	public static final int SLOTS = 4;
	public static final int SLOT_FLUID_IN = 0;
	public static final int SLOT_FLUID_OUT = 1;
	public static final int SLOT_FOCUS = 2;
	public static final int SLOT_BATTERY = 3;

	public long power = 0;
	public int efficiency = 0;
	public int heat = 0;
	public int age = 0;
	public int warning = 0;
	public int mode = 0;
	public boolean locked = false;
	public final FluidTank tank;

	public AMSStabilizerTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, SLOTS);
		tank = new FluidTank(Fluids.COOLANT, 8000);
	}

	public AMSStabilizerTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.AMS_STABILIZER.get(), pos, state);
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
		return "tile.ams_limiter.name";
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new AMSStabilizerMenu(containerId, playerInventory, this);
	}

	/** The block's facing, or north for a block entity that is somehow not on its own block. */
	public Direction facing() {
		BlockState state = getBlockState();
		return state.hasProperty(BlockDummyable.DIR) ? state.getValue(BlockDummyable.DIR) : Direction.NORTH;
	}

	// ---- ports -----------------------------------------------------------------------------

	/**
	 * Eight: the front and back of the core, the front and back of each end of the wall, and one
	 * beyond each end.
	 */
	public DirPos[] getConPos() {
		Direction face = facing();
		Direction side = face.getClockWise();
		BlockPos pos = worldPosition;
		return new DirPos[] {
				new DirPos(pos.relative(face), face),
				new DirPos(pos.relative(face.getOpposite()), face.getOpposite()),

				new DirPos(pos.relative(side, 2).relative(face), face),
				new DirPos(pos.relative(side, 2).relative(face.getOpposite()), face.getOpposite()),
				new DirPos(pos.relative(side, 3), side),

				new DirPos(pos.relative(side, -2).relative(face), face),
				new DirPos(pos.relative(side, -2).relative(face.getOpposite()), face.getOpposite()),
				new DirPos(pos.relative(side, -3), side.getOpposite())
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

	public boolean isValidFluid(FluidType fluid) {
		return fluid != null && (fluid == Fluids.WATER || fluid == Fluids.COOLANT || fluid == Fluids.CRYOGEL);
	}

	// ---- the tick --------------------------------------------------------------------------

	@Override
	public void updateEntity() {

		if(level.isClientSide) return;

		if(!locked) {

			LeafiaUtil.setTypeOnly(tank, SLOT_FLUID_IN, SLOT_FLUID_OUT, slots, Fluids.WATER, Fluids.COOLANT, Fluids.CRYOGEL);

			for(DirPos con : getConPos()) {
				trySubscribe(level, con);
				trySubscribe(tank.getTankType(), level, con);
			}

			if(power > 0) {
				// " - (maxHeat / 2)" offsets the centre to 50% instead of 0%
				efficiency = Math.round(calcEffect(power, heat - (maxHeat / 2)) * 100);
				power -= Math.ceil(power * 0.025);
				warning = 0;
			} else {
				efficiency = 0;
				warning = 1;
			}

			if(tank.getTankType() == Fluids.CRYOGEL) {

				if(tank.getFill() >= 5) {
					if(heat > 0) {
						drain(5);
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

			} else if(tank.getTankType() == Fluids.COOLANT) {

				if(tank.getFill() >= 5) {
					if(heat > 0) {
						drain(5);
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

			} else if(tank.getTankType() == Fluids.WATER) {

				if(tank.getFill() >= 15) {
					if(heat > 0) {
						drain(15);
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

			mode = 0;
			if(!slots[SLOT_FOCUS].isEmpty()) {
				if(slots[SLOT_FOCUS].getItem() == LeafiaItems.ams_focus_limiter.get())
					mode = 1;
				else if(slots[SLOT_FOCUS].getItem() == LeafiaItems.ams_focus_booster.get())
					mode = 2;
				else {
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

				ExplosionLarge.spawnShock(level, x, y, z, 24, 3);
				ExplosionLarge.spawnBurst(level, x, y, z, 24, 3);

				level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
						NtmSounds.ENTITY_OLD_EXPLOSION.get(), SoundSource.BLOCKS, 10.0F, 1);
				level.playSound(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
						LeafiaSounds.machineDestroyed.get(), SoundSource.BLOCKS, 10.0F, 1.0F);
			}

			power = Library.chargeTEFromItems(slots, SLOT_BATTERY, power, maxPower);

		} else {
			// fire particles n stuff
			int meta = rotateShitfuck(facing()) - 10;

			double pos2 = level.random.nextDouble() * 2.5;
			double off = 0.25;

			if(meta == 2)
				ParticleUtil.spawnGasFlame(level, worldPosition.getX() + 0.5 + off, worldPosition.getY() + 5.5, worldPosition.getZ() + 0.5 - pos2, 0.0, 0.0, 0.0);
			if(meta == 3)
				ParticleUtil.spawnGasFlame(level, worldPosition.getX() + 0.5 - off, worldPosition.getY() + 5.5, worldPosition.getZ() + 0.5 + pos2, 0.0, 0.0, 0.0);
			if(meta == 4)
				ParticleUtil.spawnGasFlame(level, worldPosition.getX() + 0.5 - pos2, worldPosition.getY() + 5.5, worldPosition.getZ() + 0.5 - off, 0.0, 0.0, 0.0);
			if(meta == 5)
				ParticleUtil.spawnGasFlame(level, worldPosition.getX() + 0.5 + pos2, worldPosition.getY() + 5.5, worldPosition.getZ() + 0.5 + off, 0.0, 0.0, 0.0);

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

	public static float gauss(float a, float x) {

		// Greater values -> less difference of temperate impact
		double amplifier = 0.10;

		return (float) ((1 / Math.sqrt(a * Math.PI)) * Math.pow(Math.E, -1 * Math.pow(x, 2) / amplifier));
	}

	/** As the emitter's, over this buffer: upstream repeats it with {@code maxPower} 10,000,000. */
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
		return "AMS_LMTR";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case 0 -> power = (long) value;
			case 1 -> locked = (boolean) value;
			case 2 -> efficiency = (int) value;
			case 3 -> heat = (int) value;
			case 4 -> {
				FluidType type = Fluids.fromID((int) value);
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

	/** The wall, and the beam that leaves it toward the ball. */
	public AABB getRenderBoundingBox() {
		return new AABB(worldPosition).inflate(8D, 0D, 8D).expandTowards(0D, 8D, 0D);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, AMSStabilizerTE stabilizer) {
		stabilizer.updateEntity();
	}
}
