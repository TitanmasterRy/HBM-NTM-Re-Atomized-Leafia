package com.leafia.contents.network.pipe_amat;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.network.FluidDuctBase;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.DirPos;
import com.leafia.contents.network.pipe_amat.uninos.AmatNet;
import com.leafia.contents.network.pipe_amat.uninos.AmatNode;
import com.leafia.contents.network.pipe_amat.uninos.IAmatConductor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A length of antimatter pipe.
 *
 * <p>Upstream: {@code com/leafia/contents/network/pipe_amat/AmatDuctTE.java}. It is the base
 * mod's {@link BlockEntityPipeBaseNT} with the network swapped: same fluid type, same six-sided
 * connections, same nodespace — but the node is created against {@link AmatNet#getProvider},
 * so a run of amat pipe forms its own graph and never merges with the plain duct carrying the
 * same fluid. {@link #canConnect} enforces the other half of that: a plain duct trying to
 * connect to this one is refused outright.
 *
 * <h2>The node field is deliberately a second one</h2>
 *
 * {@link BlockEntityPipeBaseNT} already has a {@code node}, typed {@code FluidNode}, and an
 * {@link AmatNode} is not one — both descend from {@code GenNode} but neither from the other.
 * Upstream shadows the parent's field with its own; here it is named {@link #amatNode} instead,
 * because a shadowed field is the kind of thing that silently half-works. The parent's stays
 * null for the life of this block entity, which is what keeps its {@code setRemoved} from
 * trying to destroy a node on the wrong network.
 *
 * <h2>What is not ported yet</h2>
 *
 * Upstream syncs the network's remaining field strength to the client so the look overlay can
 * show it, through a {@code LeafiaPacket} request/response the client fires on demand. Neither
 * half is here: {@code LeafiaPacket} is unported, and the thing that <em>refills</em> the field
 * — {@code AmatDuctChargerTE} — is the next batch. Rather than invent a sync scheme for a value
 * nothing can change yet, the readout waits for the charger. The overlay shows the fluid, as
 * the base mod's duct does.
 */
public class AmatDuctTE extends BlockEntityPipeBaseNT implements IAmatConductor {

	/** See the class note: this is not the parent's {@code node}, and the parent's stays null. */
	protected AmatNode amatNode;

	public AmatDuctTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/**
	 * An amat pipe refuses any base-mod duct.
	 *
	 * <p>Upstream tests the neighbour for {@code TileEntityPipeBaseNT} and rejects it unless it
	 * is also an {@code AmatDuctTE}. Same test here, and it is what makes the two networks
	 * genuinely separate rather than merely differently-keyed: without it a plain duct would
	 * still form a blockstate connection and look joined.
	 */
	@Override
	public boolean canConnect(FluidType type, Direction dir) {

		if(dir == null) return false;

		/*
		 * `dir` is this block entity's own connecting side - Library.canConnectFluid passes the
		 * opposite of the direction it travelled - so whoever is asking is at relative(dir).
		 */
		if(level != null && isPlainDuct(level, worldPosition.relative(dir))) return false;

		return super.canConnect(type, dir);
	}

	/**
	 * Whether the block at {@code pos} is one of the base mod's ducts rather than an amat one.
	 *
	 * <p>Static and public because the connection has to be refused from <em>both</em> ends:
	 * this block entity can turn down a plain duct that asks it, but when the amat duct is the
	 * one asking, the plain duct answers for itself and would say yes. {@link AmatDuctStandard}
	 * uses this for that half.
	 */
	public static boolean isPlainDuct(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof BlockEntityPipeBaseNT other && !(other instanceof AmatDuctTE);
	}

	/**
	 * Upstream's {@code update}, minus the client half.
	 *
	 * <p>The parent ticks on both sides because a duct redraws when its fluid changes; that
	 * still happens, because this calls {@code super} for the client branch and only replaces
	 * the server one. The replacement is the whole point: the node is fetched and created
	 * against the <em>amat</em> provider.
	 */
	@Override
	public void updateEntity() {

		if(level == null) return;

		if(level.isClientSide) {
			super.updateEntity();
			return;
		}

		if(this.amatNode == null || this.amatNode.expired) {

			if(this.shouldCreateNode()) {
				this.amatNode = (AmatNode) UniNodespace.getNode(level, worldPosition, AmatNet.getProvider(getFluidType()));

				if(this.amatNode == null || this.amatNode.expired) {
					this.amatNode = this.createNodeAmat(getFluidType());
					UniNodespace.createNode(level, this.amatNode);
				}
			}
		}
	}

	/** Six connections, one per face — upstream writes the offsets out by hand; {@link DirPos} does it. */
	public AmatNode createNodeAmat(FluidType type) {

		DirPos[] sides = new DirPos[6];
		int i = 0;
		for(Direction dir : Direction.values()) sides[i++] = new DirPos(worldPosition.relative(dir), dir);

		return new AmatNode(AmatNet.getProvider(type), worldPosition).setConnections(sides);
	}

	/**
	 * Changes what this pipe carries, tearing the node down against the network it is actually
	 * in — the <em>previous</em> fluid's amat provider, not the new one.
	 *
	 * <p>Not delegated to {@code super}: the parent would destroy the node against
	 * {@code prev.getNetworkProvider()}, which is the plain fluid network this pipe was never
	 * on. Everything else about the sequence is the parent's, including not rebuilding here —
	 * {@link #updateEntity} makes the new node next tick, so neighbours leave the old network
	 * before anything joins the new one.
	 */
	@Override
	public void setType(FluidType type) {

		FluidType prev = this.type;
		this.type = type;
		this.setChanged();

		if(level != null && !level.isClientSide) {
			BlockState state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);

			if(state.getBlock() instanceof FluidDuctBase duct) duct.updateConnections(level, worldPosition);
		}

		if(level != null) UniNodespace.destroyNode(level, worldPosition, AmatNet.getProvider(prev));

		this.amatNode = null;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();

		if(level != null && !level.isClientSide && this.amatNode != null) {
			UniNodespace.destroyNode(level, worldPosition, AmatNet.getProvider(getFluidType()));
			this.amatNode = null;
		}
	}

	/** Exposed for the GameTests, which check the node is created on the amat net and destroyed with the block. */
	public AmatNode getAmatNode() {
		return this.amatNode;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, AmatDuctTE pipe) {
		pipe.updateEntity();
	}
}
