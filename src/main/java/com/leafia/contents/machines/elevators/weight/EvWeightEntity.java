package com.leafia.contents.machines.elevators.weight;

import com.hbm.blocks.BlockDummyable;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.EvPulleyTE;
import com.leafia.contents.machines.elevators.EvShaftNeo;
import com.leafia.dev.math.FiaMatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The counterweight: the other end of the cabin's rope, riding its own shaft rail.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/elevators/weight/EvWeightEntity.java}.
 *
 * <h2>It does not really fall</h2>
 *
 * Only until it finds a pulley. Once {@link EvPulleyTE} has it, the pulley places it by
 * arithmetic every tick — mirroring the cabin's offset — and this entity's job narrows to holding
 * position 1.40625 blocks out from the pulley's centre, square to the pulley's facing. The free
 * fall is the *unattached* case, and an unattached weight that lands on anything destroys itself
 * back into its spawn item.
 *
 * <p>It also destroys itself the moment it is not inside an {@link EvShaftNeo}, which is what
 * stops a counterweight surviving someone mining the rail out from under it.
 *
 * <h2>{@code kms()} keeps its name</h2>
 *
 * Upstream's, and renaming it would make this file stop matching the original for no gain
 * (D-002). It means "drop the spawn item and die".
 */
public class EvWeightEntity extends Entity {

	public static final EntityDataAccessor<Integer> PULLEY_X =
			SynchedEntityData.defineId(EvWeightEntity.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Integer> PULLEY_Y =
			SynchedEntityData.defineId(EvWeightEntity.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Integer> PULLEY_Z =
			SynchedEntityData.defineId(EvWeightEntity.class, EntityDataSerializers.INT);

	public EvPulleyTE pulley = null;

	public EvWeightEntity(EntityType<? extends EvWeightEntity> type, Level level) {
		super(type, level);
	}

	/**
	 * Walks up the column from here to the world's old 255 ceiling looking for a pulley.
	 *
	 * <p>Upstream does <b>not</b> break out when it finds one — it keeps going and takes the
	 * highest. Kept: a shaft with two pulleys stacked hands the weight to the top one, and
	 * changing that would change which one drives it.
	 */
	public void findPulley(BlockPos basePos) {
		for(int i = (int) getY(); i < 255; i++) {
			BlockEntity te = level().getBlockEntity(new BlockPos(basePos.getX(), i, basePos.getZ()));
			if(te instanceof EvPulleyTE found) {
				pulley = found;
				BlockPos pos = pulley.getBlockPos();
				entityData.set(PULLEY_X, pos.getX());
				entityData.set(PULLEY_Y, pos.getY());
				entityData.set(PULLEY_Z, pos.getZ());
			}
		}
	}

	// Upstream declares its own setPos(Vec3) forwarding to setPos(x, y, z). 1.21.1's Entity
	// already has exactly that method, and it is final, so the override is dropped rather than
	// renamed: the one call site below resolves to vanilla's, whose body is identical. D-1039.

	/** Drop the spawn item and die. Server only, and only once. */
	public void kms() {
		if(!isAlive()) return;
		if(level().isClientSide) return;
		if(LeafiaItems.weight_spawn != null)
			spawnAtLocation(new ItemStack(LeafiaItems.weight_spawn.get()));
		discard();
	}

	/** Tall enough to reach its pulley, so the rope above it is never culled. */
	@Override
	public AABB getBoundingBoxForCulling() {
		double renderMaxHeight = getY() + 3.0625;
		if(pulley != null) {
			double pulleyHeight = pulley.getBlockPos().getY();
			if(renderMaxHeight < pulleyHeight)
				renderMaxHeight = pulleyHeight;
		}
		return new AABB(getX() - 0.75, getY(), getZ() - 0.75,
				getX() + 0.75, renderMaxHeight, getZ() + 0.75);
	}

	@Override
	public void tick() {
		super.tick();

		if(!(level().getBlockState(blockPosition()).getBlock() instanceof EvShaftNeo)) {
			kms();
			return;
		}

		Vec3 pos = position();
		FiaMatrix mat = new FiaMatrix(pos).rotateY(-getYRot());
		BlockPos bp = BlockPos.containing(mat.translate(-1.5, 0, 0).position);

		if(pulley != null && pulley.isRemoved()) pulley = null;

		if(pulley == null) {
			findPulley(bp);
			if(pulley == null)
				setDeltaMovement(getDeltaMovement().add(0, -9.8 / 400, 0));
		}

		move(MoverType.SELF, getDeltaMovement());

		if(pulley == null) {
			if(onGround())
				kms();
		} else {
			Direction dir = pulley.getLevel().getBlockState(pulley.getBlockPos())
					.getValue(BlockDummyable.DIR).getOpposite();
			FiaMatrix mat2 = new FiaMatrix(new Vec3(
					pulley.getBlockPos().getX() + 0.5, getY(), pulley.getBlockPos().getZ() + 0.5))
					.rotateY(-dir.toYRot());
			setPos(mat2.translate(1.40625, 0, 0).position);
			setYRot(dir.toYRot());
			if(pulley.setupDistWeight < 0)
				pulley.setupDistWeight = pulley.getBlockPos().getY() - getY();
			pulley.counterweight = this;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PULLEY_X, 1);
		builder.define(PULLEY_Y, 1);
		builder.define(PULLEY_Z, 1);
	}

	/*
	 * Upstream's readEntityFromNBT and writeEntityToNBT are both empty: a counterweight carries no
	 * saved state, because everything about it is re-derived from the pulley it finds. Kept empty
	 * rather than dropped, so the two files still line up.
	 */

	@Override
	protected void readAdditionalSaveData(CompoundTag compound) { }

	@Override
	protected void addAdditionalSaveData(CompoundTag compound) { }
}
