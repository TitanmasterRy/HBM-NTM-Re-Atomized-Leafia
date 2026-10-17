package com.leafia.contents.machines.reactors.rbmk.columns.realersim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKAbsorber;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControl;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.blockentity.machine.rbmk.IRBMKFluxReceiver;
import com.hbm.blockentity.machine.rbmk.RBMKDials;
import com.hbm.handler.neutron.NeutronNode;
import com.hbm.handler.neutron.NeutronNodeWorld;
import com.hbm.handler.neutron.NeutronNodeWorld.StreamWorld;
import com.hbm.handler.neutron.RBMKNeutronHandler;
import com.hbm.handler.neutron.RBMKNeutronHandler.RBMKNeutronNode;
import com.hbm.handler.neutron.RBMKNeutronHandler.RBMKNeutronStream;
import com.hbm.handler.neutron.RBMKNeutronHandler.RBMKType;
import com.hbm.handler.radiation.ChunkRadiationManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A RealerSim neutron stream: one that travels twice as far as an ordinary one, bends toward
 * absorbers and control rods, and mostly ignores what it passes.
 *
 * <p>Upstream: {@code com.hbm.handler.neutron.RealerSimNeutronStream} — an addon file in a
 * base-mod package, which a second jar cannot do here (a split package), so it lives beside the
 * column that throws it. It extends the base mod's {@link RBMKNeutronStream} and overrides two
 * methods.
 *
 * <h2>{@link #getBlocks}: the bend</h2>
 *
 * <p>Walks {@code 2 x fluxRange} steps. At each step it looks at the 3x3 square
 * ({@link #absorptionRadius} 1) around the next cell, and the first absorber or control column
 * it finds there that it has not looked at before turns the stream: the vector is re-aimed from
 * the current point at that column's centre, and the walk restarts one step out from there.
 * Every cell it has inspected is remembered for the rest of the walk, so a stream is pulled by
 * each such column at most once.
 *
 * <h2>{@link #runStreamInteraction}: the base method, with the addon's changes</h2>
 *
 * <p>Upstream's body is a copy of NTM-CE's {@code RBMKNeutronStream.runStreamInteraction}, with
 * the addon's changes marked {@code ADDED} and a note that they are marked so the copy can follow
 * the base method as it changes. So the intent is <em>the base method plus these changes</em>,
 * and under D-1000 the base method is this port's, from {@code hbm-src}. The addon's changes:
 * <ol>
 * <li>a moderator — or moderated column — moderates only one time in four
 *     ({@code nextInt(4) == 0}), and only then counts as moderation;</li>
 * <li>a fuel channel takes the stream only if it has been moderated on the way, or the stream
 *     was entirely slow to begin with ({@code fluxRatio == 0}); otherwise it passes through;</li>
 * <li>a reflector returns flux under the same condition, and otherwise ends the stream;</li>
 * <li>a stream that ends with no last column irradiates nothing — upstream commented the
 *     irradiation out ("SCREW THIS").</li>
 * </ol>
 *
 * <p>Two places upstream's copy differs from this port's base for CE's reasons rather than the
 * addon's, and both follow this port's base (D-1074): an absorber <b>heats</b> by
 * {@code dialAbsorberHeatConversion x flux}, which CE's arm does not; and the cell past a control
 * rod is found by {@code (int)} truncation, where CE's {@code BlockPos.add(double…)} floors.
 *
 * <p>The outgasser arm is absent as it is in the base mod's copy, because the outgasser column is
 * not ported. Upstream's {@code LeafiaDebug.debugPos} calls are developer markers drawn in the
 * world and do nothing to the reactor; they are not carried.
 */
public class RealerSimNeutronStream extends RBMKNeutronStream {

	private int i;
	private int j;
	private final List<BlockPos> blockedPositions = new ArrayList<>();
	private BlockPos basePos;

	/** Upstream's: how far either side of the path an absorber or control rod is felt. */
	public static int absorptionRadius = 1;

	/** A dummy stream, walked but never queued. */
	public RealerSimNeutronStream(NeutronNode origin, Vec3 vector) {
		super(origin, vector);
	}

	public RealerSimNeutronStream(NeutronNode origin, Vec3 vector, double flux, double ratio) {
		super(origin, vector, flux, ratio);
	}

	/**
	 * Upstream's {@code (RBMKType) node.data.get("type")}. The base mod keeps {@code data}
	 * protected and answers through {@link RBMKNeutronNode#getRBMKType}; a node of any other
	 * kind has no type entry upstream either, and reads null, which the walk passes by.
	 */
	private static RBMKType typeOf(NeutronNode node) {
		return node instanceof RBMKNeutronNode rbmk ? rbmk.getRBMKType() : null;
	}

	private static BlockEntity blockPosToTE(Level worldObj, BlockPos pos) {
		return worldObj.getBlockEntity(pos);
	}

	/** The horizontal unit vector from one point toward another; zero if they coincide. */
	public static Vec3 lookAt(double x0, double z0, double x1, double z1) {
		Vec3 vec = new Vec3(x1 - x0, 0, z1 - z0);
		if(vec.length() > 0)
			vec = vec.normalize();
		return vec;
	}

	@Override
	public Iterator<BlockPos> getBlocks(int range) {

		i = 1;
		j = 0;
		blockedPositions.clear();
		basePos = origin.getTile().getBlockPos();

		return new Iterator<>() {
			@Override
			public boolean hasNext() {
				return i <= range * 2;
			}

			@Override
			public BlockPos next() {
				double xd0 = vector.x * j;
				double zd0 = vector.z * j;
				int x0 = (int) Math.floor(0.5 + xd0);
				int z0 = (int) Math.floor(0.5 + zd0);
				j++;
				int x1 = (int) Math.floor(0.5 + vector.x * j);
				int z1 = (int) Math.floor(0.5 + vector.z * j);
				i++;

				BlockPos p1 = new BlockPos(basePos.getX() + x1, basePos.getY(), basePos.getZ() + z1);
				for(int xo = -absorptionRadius; xo <= absorptionRadius; xo++) {
					for(int zo = -absorptionRadius; zo <= absorptionRadius; zo++) {
						BlockPos pa = p1.offset(xo, 0, zo);
						if(!blockedPositions.contains(pa)) {
							blockedPositions.add(pa);
							BlockEntity te = blockPosToTE(origin.getTile().getLevel(), pa);
							if(te instanceof BlockEntityRBMKAbsorber || te instanceof BlockEntityRBMKControl) {
								vector = lookAt(
										basePos.getX() + 0.5 + xd0,
										basePos.getZ() + 0.5 + zd0,
										pa.getX() + 0.5,
										pa.getZ() + 0.5);
								basePos = new BlockPos(basePos.getX() + x0, basePos.getY(), basePos.getZ() + z0);
								j = 1;
								x1 = (int) Math.floor(0.5 + vector.x * j);
								z1 = (int) Math.floor(0.5 + vector.z * j);
								p1 = new BlockPos(basePos.getX() + x1, basePos.getY(), basePos.getZ() + z1);
								return p1;
							}
						}
					}
				}
				return p1;
			}
		};
	}

	@Override
	public void runStreamInteraction(Level worldObj, StreamWorld streamWorld) {

		// do nothing if there's nothing to do lmao
		if(fluxQuantity == 0D)
			return;

		int columnHeight = RBMKNeutronHandler.getCachedColumnHeight();

		BlockPos pos = origin.getTile().getBlockPos();

		BlockEntityRBMKBase originTE;

		NeutronNode node = streamWorld.getNode(pos);
		if(node != null) {
			originTE = (BlockEntityRBMKBase) node.getTile();
		} else {
			BlockEntity tile = blockPosToTE(worldObj, pos);
			if(!(tile instanceof BlockEntityRBMKBase)) return; // Doesn't exist anymore!
			originTE = (BlockEntityRBMKBase) tile;

			streamWorld.addNode(new RBMKNeutronNode(originTE, originTE.getRBMKType(), originTE.hasLid()));
		}

		int moderatedCount = 0;

		Iterator<BlockPos> iterator = getBlocks(RBMKNeutronHandler.getCachedFluxRange());

		while(iterator.hasNext()) {

			BlockPos targetPos = iterator.next();

			if(fluxQuantity == 0D) // Whoops, used it all up!
				return;

			NeutronNode targetNode = streamWorld.getNode(targetPos);
			if(targetNode == null) {
				BlockEntity te = blockPosToTE(worldObj, targetPos); // ok, maybe it didn't get added to the list somehow??
				if(te instanceof BlockEntityRBMKBase rbmk) {
					targetNode = RBMKNeutronHandler.makeNode(streamWorld, rbmk);
					streamWorld.addNode(targetNode); // whoops!
				} else {
					int hits = getHits(targetPos); // Get the amount of hits on blocks.
					if(hits == columnHeight) // If stream is fully blocked.
						return;
					else if(hits > 0) { // If stream is partially blocked.
						irradiateFromFlux(pos, hits);
						fluxQuantity *= 1 - ((double) hits / columnHeight); // Inverse to get partial blocking by blocks.
						continue;
					} else { // Nothing hit!
						irradiateFromFlux(pos, 0);
						continue;
					}
				}
			}

			RBMKType type = typeOf(targetNode);

			if(type == RBMKType.OTHER || type == null) // pass right on by!
				continue;

			BlockEntityRBMKBase nodeTE = (BlockEntityRBMKBase) targetNode.getTile();

			if(!((RBMKNeutronNode) targetNode).hasLid())
				ChunkRadiationManager.incrementRad(worldObj, targetPos.getX(), targetPos.getY(), targetPos.getZ(), (float) (this.fluxQuantity * 0.05F));

			if(type == RBMKType.MODERATOR || nodeTE.isModerated()) {
				if(worldObj.random.nextInt(4) == 0) { // ADDED
					moderatedCount++;
					moderateStream();
				}
			}

			if(nodeTE instanceof IRBMKFluxReceiver column) {
				if(moderatedCount > 0 || this.fluxRatio == 0) { // ADDED
					if(type == RBMKType.ROD) {
						BlockEntityRBMKRod rod = (BlockEntityRBMKRod) column;

						if(rod.hasRod) {
							rod.receiveFlux(this);
							return;
						}
					}
					// Upstream's OUTGASSER arm goes here, as in the base mod's copy.
				}
			} else if(type == RBMKType.CONTROL_ROD) {
				BlockEntityRBMKControl rod = (BlockEntityRBMKControl) nodeTE;

				if(rod.rodLevel > 0.0D) {
					this.fluxQuantity *= rod.getMult();
					continue;
				}
				return;
			} else if(type == RBMKType.REFLECTOR) {

				if(((BlockEntityRBMKBase) this.origin.getTile()).isModerated())
					moderatedCount++;

				if(this.fluxRatio > 0 && moderatedCount > 0)
					for(int k = 0; k < moderatedCount; k++)
						moderateStream();

				if(moderatedCount > 0 || this.fluxRatio == 0) { // ADDED
					if(RBMKNeutronHandler.getCachedReflectorEfficiency() != 1.0D) {
						this.fluxQuantity *= RBMKNeutronHandler.getCachedReflectorEfficiency();
						continue;
					}

					((BlockEntityRBMKRod) originTE).receiveFlux(this);
				}
				return;
			} else if(type == RBMKType.ABSORBER) {
				// This port's base arm, not CE's, which has no heat term: see the class note.
				nodeTE.heat += RBMKDials.getAbsorberHeatConversion(worldObj) * this.fluxQuantity;

				if(RBMKNeutronHandler.getCachedAbsorberEfficiency() == 1)
					return;

				this.fluxQuantity *= RBMKNeutronHandler.getCachedAbsorberEfficiency();
			}
		}

		NeutronNode[] nodes = getNodes(streamWorld, true);

		NeutronNode lastNode = nodes[(nodes.length - 1)];

		if(lastNode == null) { // This implies that there was *no* last node, meaning either way it was never caught.
			// Upstream: "SCREW THIS" - the irradiation at the origin is commented out.
			return;
		}

		RBMKType lastNodeType = typeOf(lastNode);

		// The base mod's copy flags the guard below: it tests the origin's `pos`, which always has
		// a node, so the body almost never runs. Upstream's, and carried.
		if(lastNodeType == RBMKType.CONTROL_ROD) {
			BlockEntityRBMKControl rod = (BlockEntityRBMKControl) lastNode.getTile();
			if(rod.getMult() > 0.0D) {
				this.fluxQuantity *= rod.getMult();
				BlockPos lastPos = lastNode.getTile().getBlockPos();
				BlockPos posAfter = new BlockPos(
						lastPos.getX() + (int) this.vector.x,
						lastPos.getY(),
						lastPos.getZ() + (int) this.vector.z);

				if(NeutronNodeWorld.getNode(worldObj, pos) == null) {
					BlockEntity te = blockPosToTE(worldObj, posAfter);
					if(te instanceof BlockEntityRBMKBase after) {
						StreamWorld world = NeutronNodeWorld.getOrAddWorld(worldObj);
						world.addNode(RBMKNeutronHandler.makeNode(world, after));
					} else {
						irradiateFromFlux(posAfter); // I'm so mad about this...
					}
				}
			}
		}
	}
}
