package com.leafia.contents.network.spk_cable;

import com.hbm.blockentity.BlockEntityLoadedBase;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.Compat;
import com.leafia.contents.network.BlockCable;
import com.leafia.contents.network.spk_cable.uninos.ISPKConductor;
import com.leafia.contents.network.spk_cable.uninos.ISPKReceiver;
import com.leafia.contents.network.spk_cable.uninos.SPKNet;
import com.leafia.contents.network.spk_cable.uninos.SPKNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A length of SPK cable: puts a node into the nodespace and, on the client, works out which
 * pairs of corners to draw a spark between.
 *
 * <p>Upstream: {@code com/leafia/contents/network/spk_cable/SPKCableTE.java}, "copy-pasted
 * TileEntityCableBaseNT". The server half is the base mod's cable block entity with the network
 * swapped for {@link SPKNet}: re-fetch a node that survived a reload rather than splitting the
 * network, create one if there is none, destroy it with the block. Nothing else - the cable
 * holds no energy, and receivers and providers talk to its net, not to it.
 *
 * <h2>The client half: sparks between corners</h2>
 *
 * A straight run of cable is drawn as one stretched model. At each <em>corner</em> (a cable that
 * is not a plain straight piece: a bend, a junction, an end, a lone block) upstream draws a
 * crackling arc along the run to the next corner or to the receiver it ends at, so a long wire
 * reads as a line of sparks between its bends. Each corner therefore keeps six {@link EffectLink}s,
 * one per direction, and every client tick re-scans along each direction until it hits another
 * corner cable, a receiver, or anything else, and links the pair. Exactly one end of a pair has
 * {@code emit} set, so the arc is drawn once: whichever corner found the other first.
 *
 * <p>Upstream's renderer wrote the six connection flags and {@code isCorner} into this block
 * entity every frame, from the same neighbour check that chose the model parts, and this tick
 * read them. Here the model comes from the blockstate, so {@link #refreshShape} derives all
 * seven from the state instead - the same facts from the same source as the model, with no
 * dependency on a renderer having run.
 *
 * <p>Upstream compares link targets with {@code ==} on {@code BlockPos} objects, which worked
 * because a 1.12.2 tile entity hands out the same {@code pos} instance each time; here they are
 * compared with {@code equals}.
 */
public class SPKCableTE extends BlockEntityLoadedBase implements ISPKConductor {

	protected SPKNode node;

	public boolean pX = false;
	public boolean pY = false;
	public boolean pZ = false;
	public boolean nX = false;
	public boolean nY = false;
	public boolean nZ = false;
	public boolean isCorner = false;

	public final EffectLink[] links = new EffectLink[Direction.values().length];

	public SPKCableTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		for(int i = 0; i < links.length; i++) links[i] = new EffectLink(this, i);
	}

	/**
	 * Upstream's {@code update}. Named the same so the file stays diffable against its original;
	 * {@link #tick} is what the block's ticker calls.
	 */
	public void updateEntity() {

		if(level == null) return;

		if(!level.isClientSide) {

			if(this.node == null || this.node.expired) {

				if(this.shouldCreateNode()) {
					this.node = (SPKNode) UniNodespace.getNode(level, worldPosition, SPKNet.THE_NETWORK_PROVIDER);

					if(this.node == null || this.node.expired) {
						this.node = this.createNode();
						UniNodespace.createNode(level, node);
					}
				}
			}

		} else {
			refreshShape();

			if(isCorner) updateLinks();
		}
	}

	public boolean shouldCreateNode() {
		return true;
	}

	/**
	 * Upstream's {@code invalidate}: the node goes with the block, or the network keeps a conductor
	 * at a position where there is no longer a cable.
	 */
	@Override
	public void setRemoved() {
		super.setRemoved();

		if(level != null && !level.isClientSide && this.node != null) {
			UniNodespace.destroyNode(level, worldPosition, SPKNet.THE_NETWORK_PROVIDER);
			this.node = null;
		}
	}

	/** Exposed for the GameTests, which assert that a placed cable actually reaches a network. */
	public SPKNode getNode() {
		return this.node;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SPKCableTE cable) {
		cable.updateEntity();
	}

	/// SHAPE ///

	/**
	 * Whether a cable with exactly these connections is a straight piece: two opposite sides and
	 * nothing else. Anything that is not is a corner, which is where sparks are drawn from.
	 */
	public static boolean isStraight(boolean pX, boolean pY, boolean pZ, boolean nX, boolean nY, boolean nZ) {
		return (pX && nX && !pY && !nY && !pZ && !nZ)
				|| (!pX && !nX && pY && nY && !pZ && !nZ)
				|| (!pX && !nX && !pY && !nY && pZ && nZ);
	}

	/**
	 * Copies the six connections off the blockstate and decides {@link #isCorner}.
	 *
	 * <p>Read from the level, not from {@link #getBlockState()}: the block's {@code onPlace} may
	 * rewrite its own state (it recomputes the arms), and {@code LevelChunk.setBlockState} then
	 * finishes the outer placement by handing the block entity the <em>stale</em> state it started
	 * with. On a client the packet's state arrives after and is right; on a server the cached one
	 * stays wrong until the next block update. The world's is always the truth.
	 */
	public void refreshShape() {

		if(level == null) return;
		BlockState state = level.getBlockState(worldPosition);
		if(!(state.getBlock() instanceof BlockCable)) return;

		pX = state.getValue(BlockCable.EAST);
		nX = state.getValue(BlockCable.WEST);
		pY = state.getValue(BlockCable.UP);
		nY = state.getValue(BlockCable.DOWN);
		pZ = state.getValue(BlockCable.SOUTH);
		nZ = state.getValue(BlockCable.NORTH);
		isCorner = !isStraight(pX, pY, pZ, nX, nY, nZ);
	}

	protected boolean getFromFacing(Direction facing) {
		return switch(facing) {
			case EAST -> pX;
			case UP -> pY;
			case SOUTH -> pZ;
			case WEST -> nX;
			case DOWN -> nY;
			case NORTH -> nZ;
		};
	}

	/// SPARK LINKS ///

	/**
	 * One client tick's worth of link maintenance for a corner. Public so a GameTest, which has no
	 * client level, can drive it on the server's; it reads only blocks and block entities.
	 */
	public void updateLinks() {

		for(EffectLink link : links) {

			boolean allow = getFromFacing(link.direction);

			if(link.link != null) {
				if(!allow) link.reset();
				else {
					BlockEntity te = Compat.getTileStandard(level, link.link);
					if(te != null && !te.isRemoved()) {
						if(te instanceof SPKCableTE cab && !link.nonCable) {
							if(!worldPosition.equals(cab.links[link.direction.getOpposite().ordinal()].link))
								link.reset();
						} else if(!link.nonCable)
							link.reset();
					} else
						link.reset();
				}
			}

			for(int d = 1; true; d++) {
				BlockEntity te = Compat.getTileStandard(level, worldPosition.relative(link.direction, d));
				if(te instanceof SPKCableTE cab && !te.isRemoved()) {
					if(!cab.isCorner) continue;
					if(cab.getBlockPos().equals(link.link) && !link.nonCable) break;
					if(cab.getFromFacing(link.direction.getOpposite())) {
						link.setLink(cab);
						break;
					}
				} else if(te instanceof ISPKReceiver && !te.isRemoved()) {
					if(te.getBlockPos().equals(link.link) && link.nonCable) break;
					link.setLinkNonCable(te.getBlockPos());
					break;
				} else break;
			}
		}
	}

	/** One direction out of a corner: where the spark arc goes, and whether this end is the one that draws it. */
	public static class EffectLink {

		public BlockPos link = null;
		public boolean emit = false;
		public boolean nonCable = false;
		public final Direction direction;
		final SPKCableTE self;

		EffectLink(SPKCableTE self, int i) {
			this.self = self;
			direction = Direction.values()[i];
		}

		public void reset() {
			link = null;
			emit = false;
			nonCable = false;
		}

		public void setLink(SPKCableTE other) {
			reset();
			link = other.getBlockPos();
			EffectLink lnk = other.links[direction.getOpposite().ordinal()];
			if(lnk.link == null || !lnk.link.equals(self.getBlockPos())) {
				lnk.reset();
				lnk.link = self.getBlockPos();
				emit = true;
			}
		}

		public void setLinkNonCable(BlockPos pos) {
			reset();
			link = pos;
			nonCable = true;
			emit = true;
		}
	}
}
