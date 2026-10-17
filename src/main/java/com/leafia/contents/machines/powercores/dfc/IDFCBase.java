package com.leafia.contents.machines.powercores.dfc;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCore;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.llib.LeafiaLib;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What every part of the addon's Dark Fusion Core has: a target it points at, and a way to find the
 * core along that line.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/IDFCBase.java}. Implemented upstream
 * by the four component mixins (DFC-3), the creative emitter, the exchanger and the pulse detonator;
 * the detonator is this batch's.
 *
 * <h2>The target, not the facing</h2>
 *
 * Upstream's components aim at a <em>position</em>, set when the block is placed (one block along the
 * piston-rule facing) and changed afterwards with a designator. The direction to it is a unit vector,
 * so a component can point diagonally - the base mod's components can only point along an axis.
 * Packet key 31 carries it: the server pushes it every tick and a client that has just read the NBT
 * asks for it, both upstream's.
 *
 * <h2>Finding the core</h2>
 *
 * {@link #leafia$getCore} walks the addon's own block raytrace from the block's centre out to
 * {@code range} along the direction. It skips its own cell and air, skips a block whose shape the
 * short segment through the cell misses, returns the first core it meets (and registers itself in
 * that core's component list, which the renderer reads), and <b>stops at anything else</b> - so a
 * component needs a clear line of sight, as the base mod's do (D-968).
 *
 * <p>Two translations. 1.12.2's {@code canCollideCheck(state, true)} is false for air only and for a
 * flowing liquid; a liquid source collides, and its 1.12.2 bounding box is a full cube although its
 * 1.21.1 shape is empty, so a still liquid stops the walk as a full cube here. And
 * {@code collisionRayTrace} tested the block's selection box, which is {@code getShape} now.
 */
public interface IDFCBase extends LeafiaPacketReceiver {

	/** Packet key for the target position, upstream's 31. */
	int KEY_TARGET = 31;

	default void leafia$writeTargetPos(CompoundTag compound) {
		BlockPos targetPos = leafia$getTargetPosition();
		compound.putInt("x1", targetPos.getX());
		compound.putInt("y1", targetPos.getY());
		compound.putInt("z1", targetPos.getZ());
	}

	default void leafia$readTargetPos(CompoundTag compound) {
		leafia$targetPosition(new BlockPos(compound.getInt("x1"), compound.getInt("y1"), compound.getInt("z1")));
		BlockEntity thisTE = (BlockEntity) this;
		Level world = thisTE.getLevel();
		if(world != null && world.isClientSide)
			LeafiaPacket._start(thisTE).__write(KEY_TARGET, true).__sendToServer();
	}

	@Override
	default void onReceivePacketLocal(byte key, Object value) {
		if(key == KEY_TARGET) leafia$targetPosition((BlockPos) value);
	}

	@Override
	default void onReceivePacketServer(byte key, Object value, Player plr) {
		if(key == KEY_TARGET) {
			LeafiaPacket.Builder packet = LeafiaPacket._start((BlockEntity) this);
			syncClients(packet);
			packet.__sendToClient(plr);
		}
	}

	default LeafiaPacket.Builder syncClients(LeafiaPacket.Builder packet) {
		packet.__write(KEY_TARGET, leafia$getTargetPosition());
		return packet;
	}

	@Override
	default void onPlayerValidate(Player plr) {
		LeafiaPacket._start((BlockEntity) this).__write(KEY_TARGET, leafia$getTargetPosition()).__sendToClient(plr);
	}

	default void leafia$setTargetPosition(BlockPos pos) {
		BlockEntity thisTE = (BlockEntity) this;
		leafia$targetPosition(pos);
		if(thisTE.getLevel() != null && !thisTE.getLevel().isClientSide)
			LeafiaPacket._start(thisTE).__write(KEY_TARGET, pos).__sendToAffectedClients();
	}

	/** The unit vector from this block to its target; zero when they coincide, as 1.12.2's normalize. */
	default Vec3 leafia$getDirection() {
		BlockEntity thisTE = (BlockEntity) this;
		return Vec3.atLowerCornerOf(leafia$getTargetPosition().subtract(thisTE.getBlockPos())).normalize();
	}

	/**
	 * The axis the target lies most along. Upstream's order of tests, ties included: east, west,
	 * south, north, up, then down for everything else.
	 */
	default Direction leafia$getFront() {
		BlockEntity thisTE = (BlockEntity) this;
		Vec3i relative = leafia$getTargetPosition().subtract(thisTE.getBlockPos());
		int max = Math.max(Math.abs(relative.getX()), Math.max(Math.abs(relative.getY()), Math.abs(relative.getZ())));
		if(max == relative.getX()) return Direction.EAST;
		else if(max == -relative.getX()) return Direction.WEST;
		else if(max == relative.getZ()) return Direction.SOUTH;
		else if(max == -relative.getZ()) return Direction.NORTH;
		else if(max == relative.getY()) return Direction.UP;
		else return Direction.DOWN;
	}

	/** The core this part points at within {@code range} blocks, or null. Remembered as {@link #leafia$lastGetCore()}. */
	@Nullable
	default BlockEntityCore leafia$getCore(int range) {
		BlockEntity thisTE = (BlockEntity) this;
		Level world = thisTE.getLevel();
		BlockPos pos = thisTE.getBlockPos();
		if(world == null) return null;
		Vec3 start = Vec3.atCenterOf(pos);
		leafia$lastGetCore(LeafiaLib.<BlockEntityCore>leafiaRayTraceBlocksCustom(world, start, start.add(leafia$getDirection().scale(range)), (process, config, current) -> {
			if(current.posSnapped.equals(pos)) return process.CONTINUE();
			BlockState state = current.state;
			if(state.isAir()) return process.CONTINUE();
			FluidState fluid = state.getFluidState();
			if(!fluid.isEmpty() && !fluid.isSource() && state.getShape(world, current.posSnapped).isEmpty())
				return process.CONTINUE(); // a flowing liquid: canCollideCheck(state, true) is false
			VoxelShape shape = state.getShape(world, current.posSnapped);
			if(shape.isEmpty() && !fluid.isEmpty()) shape = Shapes.block(); // a still liquid's 1.12.2 box
			BlockHitResult result = shape.clip(current.posIntended.subtract(config.unitDir.scale(2)),
					current.posIntended.add(config.unitDir.scale(2)), current.posSnapped);
			if(result == null) return process.CONTINUE();
			BlockEntity te = world.getBlockEntity(current.posSnapped);
			if(te instanceof BlockEntityCore core) {
				if(te instanceof IMixinTileEntityCore mixin) mixin.getDFCComponentPositions().add(pos);
				return process.RETURN(core);
			}
			return process.BREAK();
		}));
		return leafia$lastGetCore();
	}

	/// ---------------------------- BOILERPLATE ZONE ---------------------------- ///

	@Nullable
	BlockEntityCore leafia$lastGetCore();

	void leafia$lastGetCore(@Nullable BlockEntityCore core);

	BlockPos leafia$getTargetPosition();

	/**
	 * @deprecated internal setter
	 */
	@Deprecated
	void leafia$targetPosition(BlockPos pos);
}
