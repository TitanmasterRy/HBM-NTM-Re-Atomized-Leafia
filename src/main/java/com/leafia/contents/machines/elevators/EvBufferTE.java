package com.leafia.contents.machines.elevators;

import java.util.List;

import com.leafia.contents.machines.elevators.car.ElevatorEntity;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The shock absorber at the bottom of a shaft — or rather, the <em>renderer's</em> half of it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/EvBufferTE.java}.
 *
 * <h2>This block entity has no server behaviour at all</h2>
 *
 * Worth saying plainly, because the name suggests otherwise. The buffer's actual job — catching a
 * falling cabin instead of letting it explode — is done entirely by {@link ElevatorEntity}, which
 * scans downward for an {@link EvBuffer} block and scales its own {@code motionY} by the distance
 * to it. Nothing in this class is consulted for that.
 *
 * <p>What it does is find the cabin above it, <b>on the client only</b>, so the renderer can draw
 * the buffer's piston compressed by however close the cabin is. {@link #elevator} is written by
 * nothing else and read by nothing else here.
 */
public class EvBufferTE extends BlockEntity {

	/** The cabin somewhere in the 50 blocks above. Client-side, for the renderer. */
	public ElevatorEntity elevator;

	private AABB scanAABB;
	private AABB renderAABB;

	public EvBufferTE(BlockPos pos, BlockState state) {
		super(LeafiaBlockEntities.EV_BUFFER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EvBufferTE te) {
		te.updateEntity();
	}

	// ------------------------------------------------------------------ the spring
	//
	// Upstream computes all of this inline in EvBufferRender. It lives here instead for the
	// reason D-1021 gives for the fluid valve's handwheel angle: a @OnlyIn(Dist.CLIENT) class
	// cannot be loaded by a GameTest server, so arithmetic left in a renderer is arithmetic no
	// test can reach. Nothing about it is client-specific - it is four constants and a clamp.

	/** Upstream's {@code globalScale}: the model is 8.375 units tall in a 6-unit block. */
	public static final double GLOBAL_SCALE = 6 / 8.375;

	/** Upstream's rest length of the spring, in model units. */
	public static final float REST_LENGTH = 4F;

	/**
	 * Upstream's bottoming-out offset: how far past flat the spring is allowed to go.
	 *
	 * <p><b>It is not what makes {@link #squash} 1 at rest</b>, which a first draft of this
	 * comment claimed and a falsification run disproved. {@code (REST_LENGTH + BOTTOM_OUT) /
	 * (REST_LENGTH + BOTTOM_OUT)} is 1 for <em>any</em> value of either, so the rest state is
	 * structural and says nothing about these two literals. What this number actually decides is
	 * the <em>fully compressed</em> squash, {@code BOTTOM_OUT * (1 - GLOBAL_SCALE) /
	 * (REST_LENGTH + BOTTOM_OUT)} — 0.0243 with upstream's values, and 0.0315 if this were 0.5.
	 * That is what {@code LeafiaElevatorRenderTests} pins, and it is the only assertion in that
	 * file's spring tests that an edit here can fail.
	 */
	public static final float BOTTOM_OUT = 0.375F;

	/** How far the model's top sits above the block, in world units. */
	private static final double MODEL_TOP = 8.375;

	/** Upstream's offset from the cabin's origin to the face that touches the spring. */
	private static final double CABIN_FOOT = 0.25;

	/**
	 * How far the cabin has pushed into the spring, in model units. Zero when there is none, or
	 * when it is still above the spring's top.
	 *
	 * @param cabinY the cabin's Y, or {@link Double#NaN} for no cabin
	 */
	public static double shrink(double bufferY, double cabinY) {
		if(Double.isNaN(cabinY)) return 0;
		return Math.max(((bufferY + MODEL_TOP * GLOBAL_SCALE) - (cabinY - CABIN_FOOT)) / GLOBAL_SCALE, 0);
	}

	/** The spring's current length in model units, clamped at how far it can bottom out. */
	public static float length(double bufferY, double cabinY) {
		return (float) Math.max(REST_LENGTH - shrink(bufferY, cabinY), -BOTTOM_OUT * GLOBAL_SCALE);
	}

	/** The vertical squash factor the spring is drawn with. 1 at rest. */
	public static float squash(double bufferY, double cabinY) {
		return (length(bufferY, cabinY) + BOTTOM_OUT) / (REST_LENGTH + BOTTOM_OUT);
	}

	/** How far the sliding piece has travelled from rest, in model units. Negative is down. */
	public static float travel(double bufferY, double cabinY) {
		return length(bufferY, cabinY) - REST_LENGTH;
	}

	public void updateEntity() {

		if(!level.isClientSide) return;

		if(scanAABB == null) {
			// Upstream's box: this block's own column, 50 blocks up.
			scanAABB = new AABB(
					worldPosition.getX(),
					worldPosition.getY(),
					worldPosition.getZ(),
					worldPosition.getX() + 1,
					worldPosition.getY() + 50,
					worldPosition.getZ() + 1);
		}

		for(Entity entity : level.getEntities((Entity) null, scanAABB, e -> e instanceof ElevatorEntity)) {
			elevator = (ElevatorEntity) entity;
			break;
		}
	}

	/**
	 * Upstream's render box: a block out each way and six up, because the buffer's spring is drawn
	 * taller than its block when a cabin is resting on it.
	 *
	 * <p>Not an override. 1.12.2 put {@code getRenderBoundingBox} on the tile entity; NeoForge
	 * 21.1's {@code IBlockEntityExtension} has no such method, and the box is asked of the
	 * {@code BlockEntityRenderer} instead — the same move the base mod made in D-319 for
	 * {@code RenderFileCabinet}. The geometry stays here, where the block that defines it lives,
	 * and the renderer returns it when it lands. D-1039.
	 */
	public AABB renderBounds() {
		if(renderAABB == null) {
			renderAABB = new AABB(
					worldPosition.getX() - 0.5,
					worldPosition.getY(),
					worldPosition.getZ() - 0.5,
					worldPosition.getX() + 1.5,
					worldPosition.getY() + 6,
					worldPosition.getZ() + 1.5);
		}
		return renderAABB;
	}
}
