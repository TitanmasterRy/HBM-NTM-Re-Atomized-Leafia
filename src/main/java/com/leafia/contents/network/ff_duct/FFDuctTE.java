package com.leafia.contents.network.ff_duct;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.uninos.GenNode;
import com.hbm.uninos.UniNodespace;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.network.ff_duct.uninos.FFNet;
import com.leafia.contents.network.ff_duct.uninos.FFNode;
import com.leafia.contents.network.ff_duct.uninos.IFFConductor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A length of Forge-fluid duct: what it carries, and its node.
 *
 * <p>Upstream: {@code com/leafia/contents/network/ff_duct/FFDuctTE.java}. Both {@code ff_duct} and
 * {@code ff_duct_solid_shielded} use it.
 *
 * <h2>The node</h2>
 *
 * Made on the server tick when the duct's fluid has a Forge fluid behind it (a {@code NONE} duct
 * has none, so carries nothing and joins nothing), against the one {@link FFNet#PROVIDER}, and
 * destroyed with the block. It holds no fluid: a duct is only the graph.
 *
 * <h2>Retyping, and an upstream oddity kept</h2>
 *
 * {@link #setType} tears the node down against {@code prev.getNetworkProvider()} - the <em>base
 * mod's</em> fluid network for the old fluid, which this duct was never on - and then forgets its
 * own reference. So the FF node is not destroyed: the next tick finds it still in the nodespace and
 * picks it up again, which is harmless because there is one FF provider for every fluid and the
 * duct's own {@link #canConnect} decides which stacks it passes. The call is kept as written; it
 * destroys nothing.
 *
 * <h2>{@code getType} is {@link #getFluidType}</h2>
 *
 * Upstream's accessor is {@code getType()}, which a 1.21.1 block entity already has (its
 * {@code BlockEntityType}); it is named as the base mod's pipe names its own.
 *
 * <h2>Sync</h2>
 *
 * Upstream sends the fluid id on its own packet channel, both when it changes and when a client
 * first sees the duct ({@code onPlayerValidate}). Here that is vanilla's block entity update tag,
 * which reaches a client on both occasions without asking; the colour handler reads the result.
 */
public class FFDuctTE extends BlockEntity implements IFFConductor {

	public FluidType type = Fluids.NONE;
	protected FFNode node;

	public FFDuctTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, FFDuctTE duct) {
		duct.update();
	}

	public void update() {
		if(level == null || level.isClientSide) return;
		if(LeafiaFF.getFF(type) != null) {
			if(this.node == null || this.node.expired) {
				if(this.shouldCreateNode()) {
					GenNode existing = UniNodespace.getNode(level, worldPosition, FFNet.PROVIDER);
					this.node = existing instanceof FFNode ff ? ff : null;
					if(this.node == null || this.node.expired) {
						this.node = this.createNode();
						UniNodespace.createNode(level, node);
					}
				}
			}
		}
	}

	public boolean shouldCreateNode() {
		return true;
	}

	/** For the GameTests: the node this duct holds, if any. */
	@Nullable
	public FFNode getNode() {
		return node;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if(level != null && !level.isClientSide) {
			if(node != null)
				UniNodespace.destroyNode(level, worldPosition, FFNet.PROVIDER);
		}
	}

	public FluidType getFluidType() {
		return this.type;
	}

	public void setType(FluidType type) {
		FluidType prev = this.type;
		this.type = type;
		this.setChanged();

		if(level != null && !level.isClientSide) {
			BlockState state = level.getBlockState(worldPosition);
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
			if(state.getBlock() instanceof FFDuctBase duct) duct.updateConnections(level, worldPosition);
		}

		// Server only: the nodespace is keyed by dimension, so a client asking would reach the
		// integrated server's map. Upstream's call ran on both sides and found nothing on either.
		if(level != null && !level.isClientSide) UniNodespace.destroyNode(level, worldPosition, prev.getNetworkProvider());

		if(this.node != null) {
			this.node = null;
		}
	}

	@Override
	public boolean canConnect(@Nullable FluidStack stack, @Nullable Direction dir) {
		if(dir == null) return false;
		if(stack == null) return true;
		net.minecraft.world.level.material.Fluid ff = LeafiaFF.getFF(type);
		return ff == null ? ff == stack.getFluid() : ff.equals(stack.getFluid());
	}

	@Override
	protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		super.loadAdditional(compound, registries);
		FluidType loaded = Fluids.fromID(compound.getInt("fluid"));
		if(level == null) this.type = loaded; // a chunk loading: no world to tell yet
		else setType(loaded);
	}

	@Override
	protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
		compound.putInt("fluid", type.getID());
		super.saveAdditional(compound, registries);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Upstream's {@code onReceivePacketLocal} key 31: take the type and redraw, so the tint follows. */
	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		FluidType before = this.type;
		super.onDataPacket(net, pkt, registries);
		redrawIfChanged(before);
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		FluidType before = this.type;
		super.handleUpdateTag(tag, registries);
		redrawIfChanged(before);
	}

	private void redrawIfChanged(FluidType before) {
		if(level != null && level.isClientSide && before != this.type) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}
}
