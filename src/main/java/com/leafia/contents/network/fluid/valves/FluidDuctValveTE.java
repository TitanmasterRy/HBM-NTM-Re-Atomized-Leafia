package com.leafia.contents.network.fluid.valves;

import com.hbm.uninos.UniNodespace;
import com.leafia.contents.network.fluid.FluidDuctEquipmentTE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A valve in a run of the base mod's fluid pipe: shut it and the run is cut in two.
 *
 * <p>Upstream: {@code com/leafia/contents/network/fluid/valves/FluidDuctValveTE.java}.
 *
 * <h2>How closing actually cuts the pipe</h2>
 *
 * It does not stop fluid at this block — it removes this block from the graph entirely.
 * {@link #shouldCreateNode()} returns {@code open}, so a closed valve makes no node, and
 * {@link #close()} destroys the one it had. The two halves of the run then have nothing joining
 * them and the nodespace splits them into separate networks on its next pass.
 *
 * <p>That hook is the base mod's own, and its javadoc already names this exact use: <em>"The
 * fluid valve and the fluid switch turn this off to cut a run in two."</em> Which is worth
 * saying out loud — the addon's valve needed no base-mod change at all, because the base mod
 * had already left the hole for its own.
 *
 * <p>Unlike the antimatter pipe, this is an <b>ordinary</b> fluid duct: the node goes on
 * {@code getFluidType().getNetworkProvider()}, so it joins the base mod's network and connects
 * to base-mod pipes exactly as one of them would.
 *
 * <p>{@link #localAngle} is the handwheel's position, ticked on the client only and read by
 * {@code FluidDuctEquipmentRender}, which interpolates between it and {@link #localAngleLast}
 * by the frame's partial tick. Ten steps of nine degrees is the quarter turn from shut to open.
 */
public class FluidDuctValveTE extends FluidDuctEquipmentTE {

	private boolean open = false;
	private boolean wasOpen = false;

	/** The handwheel, 0 (shut) to 10 (open). Client-side only; see the class note. */
	public int localAngle = 0;
	public int localAngleLast = 0;

	/** Upstream's: ten steps of nine degrees, so shut to open is a quarter turn. */
	public static final int WHEEL_STEPS = 10;
	public static final float WHEEL_DEGREES_PER_STEP = 9F;

	/**
	 * Where the handwheel is pointing this frame, in degrees about the body's axis.
	 *
	 * <p>Upstream computes this inline in its renderer. It lives here because it is arithmetic
	 * over this block entity's own state and because a renderer is client-only, so a number left
	 * in one cannot be pinned by a test — and this project's rule is that anything with a number
	 * in it gets one.
	 *
	 * <p>The subtraction is upstream's and is the part worth reading twice: a <em>shut</em> valve
	 * is the one with the wheel turned ninety degrees, and an open one sits at zero.
	 */
	public float wheelAngle(float partialTick) {
		float step = localAngleLast + (localAngle - localAngleLast) * partialTick;
		return (WHEEL_STEPS - step) * WHEEL_DEGREES_PER_STEP;
	}

	public FluidDuctValveTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public boolean isOpen() {
		return this.open;
	}

	public void setOpen(boolean open) {
		this.open = open;
		this.setChanged();
	}

	@Override
	public boolean shouldCreateNode() {
		return this.open;
	}

	/** Drops this valve out of the fluid network, which is what splits the run. */
	public void close() {

		if(level != null && this.node != null) {
			UniNodespace.destroyNode(level, worldPosition, getFluidType().getNetworkProvider());
			this.node = null;
		}
	}

	@Override
	public void updateEntity() {

		super.updateEntity();

		if(level == null) return;

		if(!level.isClientSide) {

			if(wasOpen && !open) close();
			wasOpen = open;

			networkPackNT(SYNC_RANGE);

		} else {

			localAngleLast = localAngle;
			if(open && localAngle < WHEEL_STEPS) localAngle++;
			if(!open && localAngle > 0) localAngle--;
		}
	}

	@Override
	protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.open = nbt.getBoolean("open");
		this.wasOpen = this.open;
	}

	@Override
	protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putBoolean("open", this.open);
	}

	@Override
	public void serialize(RegistryFriendlyByteBuf buf) {
		super.serialize(buf);
		buf.writeBoolean(this.open);
	}

	@Override
	public void deserialize(RegistryFriendlyByteBuf buf) {
		super.deserialize(buf);
		this.open = buf.readBoolean();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, FluidDuctValveTE valve) {
		valve.updateEntity();
	}
}
