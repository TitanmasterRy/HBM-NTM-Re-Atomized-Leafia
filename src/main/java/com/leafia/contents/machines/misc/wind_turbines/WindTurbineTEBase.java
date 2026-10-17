package com.leafia.contents.machines.misc.wind_turbines;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.passive.Wind;

import api.hbm.energymk2.IEnergyProviderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What every wind turbine shares: a lubricant tank, a spinning blade angle, and a raytrace
 * upwind that decides whether the thing is sheltered.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/wind_turbines/WindTurbineTEBase.java}.
 * It is <b>the first consumer of {@link Wind}</b> (D-1027), which landed with nothing reading it.
 *
 * <h2>The obstruction check is the interesting part</h2>
 *
 * A turbine raytraces {@code range} blocks <em>upwind</em> — along {@code Wind.angle + 180} — and
 * is obstructed only if <b>both</b> of two rays ten degrees either side of that are blocked. One
 * clear ray is enough, so a single tree or a lamp post does not shut a turbine down while a wall
 * does. The check runs on average once every hundred ticks rather than every tick, plus once on
 * the first tick so a freshly placed turbine does not report clear until it happens to roll.
 *
 * <p>Upstream raytraces with {@code LeafiaLib.leafiaRayTraceBlocks}, 285 lines that reimplement
 * 1.12.2's {@code World.rayTraceBlocks} around a callback hook. Nothing here needs the hook, so
 * this uses vanilla's {@link net.minecraft.world.level.Level#clip}: upstream's
 * {@code stopOnLiquid} is {@link ClipContext.Fluid#ANY} and its
 * {@code ignoreBlockWithoutBoundingBox} is {@link ClipContext.Block#COLLIDER}, which is what
 * those two flags meant.
 *
 * <h2>The tank size is computed from an abstract method</h2>
 *
 * {@code lube}'s capacity is {@code 3600 * 20 / lubeConsumptionPeriod()} — one hour of running at
 * whichever rate the subclass burns it. That is a field initialiser calling an overridable
 * method, which is upstream's and is safe only because every override returns a literal; it is
 * kept because changing it would change the tank size, which is a balance number.
 */
public abstract class WindTurbineTEBase extends BlockEntityLoadedBase
		implements LeafiaPacketReceiver, IEnergyProviderMK2 {

	public float bladeAnglePrev = 0;
	public float bladeAngle = 0;

	/** Ticks per millibucket of lubricant burned, once the turbine is doing real work. */
	public abstract int lubeConsumptionPeriod();

	/** An hour of lubricant at this turbine's burn rate. Upstream's arithmetic, unchanged. */
	public FluidTank lube = new FluidTank(Fluids.LUBRICANT, 3600 * 20 / lubeConsumptionPeriod());

	public boolean obstructed = false;

	public long generated = 0;

	protected WindTurbineTEBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void addBladeAngle(float ang) {

		bladeAnglePrev = bladeAngle;
		bladeAngle += ang;

		if(bladeAngle >= 360) {
			bladeAngle -= 360;
			bladeAnglePrev -= 360;
		}
	}

	/** True when there is a clear line of sight along {@code angle} for {@code range} blocks. */
	public boolean raytrace(double height, double angle, int range) {

		double dx = Math.cos(angle / 180 * Math.PI);
		double dz = -Math.sin(angle / 180 * Math.PI);

		Vec3 start = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + height, worldPosition.getZ() + 0.5)
				.add(dx * (0.707 + 0.01), 0, dz * (0.707 + 0.01));
		Vec3 end = start.add(dx * range, 0.1 * range, dz * range);

		BlockHitResult result = level.clip(new ClipContext(
				start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));

		return result == null || result.getType() == HitResult.Type.MISS;
	}

	/** True when it is obstructed — which needs <em>both</em> rays blocked, not one. */
	public boolean checkForObstruction(double height, int range) {

		double angle = Wind.angle + 180;

		if(!raytrace(height, angle + 10, range)) {
			if(!raytrace(height, angle - 10, range)) return true;
		}

		return false;
	}

	boolean firstTick = true;

	public void updateObstructed(double height, int range) {

		if(level.random.nextInt(100) == 0 || firstTick) {
			obstructed = checkForObstruction(height, range);
			firstTick = false;
		}
	}

	int lubeTimer = 0;

	/** Lubricant only burns while the turbine is actually generating something. */
	public void consumeLube() {

		if(generated > 10) {
			lubeTimer++;
			if(lubeTimer > lubeConsumptionPeriod()) {
				lubeTimer = 0;
				lube.setFill(Math.max(0, lube.getFill() - 1));
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.saveAdditional(compound, registries);
		compound.putBoolean("obstructed", obstructed);
		compound.putInt("lube", lube.getFill());
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		obstructed = compound.getBoolean("obstructed");
		lube.setFill(compound.getInt("lube"));
	}

	/*
	 * BlockEntityLoadedBase is an IBufPacketReceiver, and this machine does not use that channel:
	 * it syncs over LeafiaPacket, which is upstream's and which carries the three fields below by
	 * slot. These two are the empty implementations that requires — the base mod's channel is
	 * simply never asked to send one.
	 */

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) { }

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	/**
	 * The three synced slots, counting <em>down</em> from 31.
	 *
	 * <p>Upstream numbers these from the top of the five-bit key space so subclasses can use 0
	 * upwards without colliding with the base class. Kept, because the numbers are the wire
	 * format (D-1025) and a subclass that later adds slot 0 must not have to renumber these.
	 *
	 * <p>The first branch is an {@code if} and the second an {@code else if}, which is upstream's
	 * shape and is harmless — the keys are distinct — but it is not a typo to be tidied.
	 */
	@Override
	public void onReceivePacketLocal(byte key, Object value) {

		if(key == 31) obstructed = (boolean) value;

		if(key == 30) generated = (long) value;
		else if(key == 29) lube.setFill((int) value);
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	/** Half a kilometre: a turbine is visible from a long way off, so it syncs a long way off. */
	@Override
	public double affectionRange() {
		return 512;
	}

	@Override
	public void setPower(long l) {
		generated = l;
	}

	@Override
	public long getPower() {
		return generated;
	}

	@Override
	public long getMaxPower() {
		return 100000;
	}

	/** Power leaves through the bottom of the mast and nowhere else. */
	@Override
	public boolean canConnect(Direction dir) {
		return dir == Direction.DOWN;
	}
}
