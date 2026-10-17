package com.leafia.contents.machines.reactors.pwr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.fluid.CoriumFinite;
import com.hbm.util.Tuple.Pair;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRChannelBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRConductorBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.channel.PWRExchangerBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.llib.group.LeafiaMap;
import com.llib.group.LeafiaSet;
import com.llib.math.MathLeafia;
import com.llib.math.range.RangeInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flood fill that turns a pile of PWR components into a reactor: it finds every component
 * connected to the one that changed, books one of them as the core, sizes the tanks, measures the
 * vessel's toughness from the blocks around it, and tells every fuel column how much coolant channel
 * and heat exchanger it has nearby.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/PWRDiagnosis.java}, line for line. Four
 * translations:
 *
 * <ul>
 * <li><b>Toughness</b> averages {@code block.getExplosionResistance(null)} over the non-component blocks
 * within two of every member. In 1.12.2 that is {@code setResistance(r) x 3 / 5}, which is exactly what
 * this port stores (D-012), so {@link Block#getExplosionResistance()} is the same number. 1.12.2's
 * {@code Material.isSolid()} is {@link BlockState#isSolid()}, its successor for the same purpose.</li>
 * <li>{@code world.isValid(pos)} is {@link Level#isInWorldBounds}.</li>
 * <li>{@code ModBlocks.block_corium}/{@code block_corium_cobble} and {@code CoriumFinite} are the base
 * mod's own.</li>
 * <li>The debug wand's stack-overflow chat line and {@code LeafiaDebug.debugLog} are not carried.</li>
 * <li><b>One correction 1.21.1 requires</b>: the terminal grid's "no block projected here yet" height is
 * {@code Integer.MIN_VALUE}, not upstream's -1. Below Y 0 - a 1.21.1 world goes to -64 - nothing but fuel
 * was ever projected, so a reactor built in the deepslate showed only its fuel on the terminal. Identical
 * at any Y upstream could reach (D-3006).</li>
 * </ul>
 *
 * <p>Two upstream behaviours to know, both kept. A diagnosis runs on the client too (when the server
 * sends a core's data, key 31): there it only links members and does not book a core. And
 * {@link #ongoing} and {@link #preventScan} are static, shared by both sides in single player, as
 * upstream's are.
 */
public class PWRDiagnosis {
	// We have to judge by the listener's position instead of changed neighbors position
	//   because that way the separated assemblies triggered by the same block would not update in a corner situation
	// This set is cleared every server tick
	public static final Set<BlockPos> preventScan = new HashSet<>();

	public BlockPos forcedCorePos = null;

	public final boolean isMeltdown;
	public static final Set<PWRDiagnosis> ongoing = new HashSet<>();
	private static boolean cleanupInProgress = false;

	public static void cleanup() {
		if(cleanupInProgress) return;
		cleanupInProgress = true;
		Set<PWRDiagnosis> removalQueue = new HashSet<>();
		for(PWRDiagnosis task : ongoing) {
			if(task.checkTimeElapsed() >= 10_000 || task.closure) removalQueue.add(task);
		}
		for(PWRDiagnosis diagnosis : removalQueue) diagnosis.destroy();
		cleanupInProgress = false;
	}

	public double cr;
	public double cg;
	public double cb;

	public int lastConfirmed;
	public final Set<BlockPos> activePos = new HashSet<>();
	public final Set<BlockPos> blockPos = new HashSet<>();
	public final Set<BlockPos> corePos = new HashSet<>();
	public final Set<BlockPos> potentialPos = new HashSet<>();
	public final Set<BlockPos> fuelPositions = new HashSet<>();
	public final Set<BlockPos> coriums = new HashSet<>();
	public final Set<BlockPos> controlPositions = new HashSet<>();
	public final LeafiaMap<Pair<Integer, Integer>, Pair<Integer, Boolean>> projected = new LeafiaMap<>();
	RangeInt rangeX = new RangeInt(Integer.MAX_VALUE, Integer.MIN_VALUE);
	RangeInt rangeZ = new RangeInt(Integer.MAX_VALUE, Integer.MIN_VALUE);
	float maxHardness = 0;
	float sumHardness = 0;
	float divide = 0;

	void gridFill() {
		for(Entry<Pair<Integer, Integer>, Pair<Integer, Boolean>> entry : projected.entrySet()) {
			int fromHeight = entry.getValue().getKey();
			for(Direction face : Direction.Plane.HORIZONTAL) {
				List<Pair<Integer, Integer>> buffer = new ArrayList<>();
				for(int i = 1; true; i++) {
					Pair<Integer, Integer> offset = new Pair<>(entry.getKey().getKey() + face.getStepX() * i, entry.getKey().getValue() + face.getStepZ() * i);
					if(!rangeX.isInRange(offset.getKey()) || !rangeZ.isInRange(offset.getValue()))
						break;
					if(projected.containsKey(offset)) {
						if(buffer.size() > 0) {
							int toHeight = projected.get(offset).getKey();
							int finalHeight = Math.min(fromHeight, toHeight); // TODO: min or max?
							for(Pair<Integer, Integer> pair : buffer)
								projected.put(pair, new Pair<>(finalHeight, false));
						}
						break;
					} else
						buffer.add(offset);
				}
			}
		}
	}

	void addProjection(BlockPos pos, boolean isFuel) {
		Pair<Integer, Integer> pos2d = new Pair<>(pos.getX(), pos.getZ());
		// Upstream's "nothing here yet" is -1, below any 1.12.2 block. 1.21.1 builds down to -64, where every
		// block is below -1 and only fuel would ever be projected; MIN_VALUE is the same sentinel for any Y.
		int height = Integer.MIN_VALUE;
		boolean hasToBeFuel = false;
		if(projected.containsKey(pos2d)) {
			height = projected.get(pos2d).getKey();
			hasToBeFuel = projected.get(pos2d).getValue();
		}
		if((pos.getY() > height || (isFuel && !hasToBeFuel)) && (isFuel || !hasToBeFuel)) {
			projected.put(pos2d, new Pair<>(pos.getY(), isFuel));
			rangeX.min = Math.min(rangeX.min, pos.getX());
			rangeX.max = Math.max(rangeX.max, pos.getX());
			rangeZ.min = Math.min(rangeZ.min, pos.getZ());
			rangeZ.max = Math.max(rangeZ.max, pos.getZ());
		}
	}

	boolean closure = false;
	Level world = null;
	final BlockPos triggerPos;

	/** Creates PWRDiagnosis instance, and automatically adds to ongoing Set */
	public PWRDiagnosis(Level world, BlockPos trigger) {
		confirmLife();
		this.world = world;
		ongoing.add(this);
		cr = world.random.nextDouble();
		cg = world.random.nextDouble();
		cb = world.random.nextDouble();
		triggerPos = trigger;
		isMeltdown = world.getBlockState(trigger).getBlock() instanceof CoriumFinite;
	}

	PWRComponentBlock getPWRBlock(BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();
		if(block instanceof PWRComponentBlock pwr) return pwr;
		return null;
	}

	PWRComponentEntity getPWREntity(BlockPos pos) {
		PWRComponentBlock pwr = getPWRBlock(pos);
		if(pwr != null) return pwr.getPWR(world, pos);
		return null;
	}

	/**
	 * Visits the six neighbours and the six blocks two away. A component adjacent is added (and so
	 * explored); anything that is not a component, at either distance, counts toward the toughness.
	 *
	 * <p><b>Upstream's bug, kept:</b> a {@code CoriumFinite} neighbour adds and re-explores the
	 * <em>current</em> position, not the corium's, so {@link #coriums} counts components touching
	 * corium rather than corium blocks.
	 */
	public void explorePosition(BlockPos pos) {
		if(closure) return;
		activePos.add(pos);
		for(Direction facing : Direction.values()) {
			for(int i = 0; i <= 1; i++) {
				BlockPos neighbor = pos.relative(facing, i + 1);
				BlockState state = world.getBlockState(neighbor);
				Block block = state.getBlock();
				if(i == 0) {
					if(block instanceof PWRComponentBlock) {
						addPosition(neighbor);
					} else if(block instanceof CoriumFinite) {
						if(!coriums.contains(pos)) {
							coriums.add(pos);
							explorePosition(pos);
						}
					} else if(block == ModBlocks.block_corium.get())
						addPosition(neighbor);
					else if(block == ModBlocks.block_corium_cobble.get())
						addPosition(neighbor);
				}
				if(!world.isInWorldBounds(neighbor)) break;
				if(block instanceof PWRComponentBlock) continue;
				divide++;
				if(state.isSolid()) {
					float strength = block.getExplosionResistance();
					sumHardness += strength;
					maxHardness = Math.max(strength, maxHardness);
				}
			}
		}
		activePos.remove(pos);
		if(activePos.size() <= 0) close();
	}

	public void addPosition(BlockPos pos) {
		if(closure) return;
		if(!blockPos.contains(pos)) {
			blockPos.add(pos);
			if(!world.isClientSide) preventScan.add(pos);
			confirmLife();
			PWRComponentBlock pwr = getPWRBlock(pos);
			boolean isFuel = false;
			if(pwr != null) {
				// if this block isn't under control of topping blocks,
				if(pwr.tileEntityShouldCreate(world, pos)) {
					// then allow it to be assigned as core
					BlockEntity entity = world.getBlockEntity(pos);
					// only assign entities explicitly stated as assignable
					if(entity instanceof PWRComponentEntity component && component.canAssignCore()) {
						boolean doNotAdd = forcedCorePos != null && !pos.equals(forcedCorePos);
						if(!doNotAdd) potentialPos.add(pos);
					}
					if(pwr instanceof PWRElementBlock) {
						fuelPositions.add(pos);
						isFuel = true;
					}
					if(pwr instanceof PWRControlBlock) controlPositions.add(pos);
				}
				if(pwr.shouldRenderOnGUI()) addProjection(pos, isFuel);
			}
			PWRComponentEntity entity = getPWREntity(pos);
			if(entity != null) {
				// if this block is the core of the affected assembly..
				if(entity.getCore() != null) {
					// check if it's in right place
					if(potentialPos.contains(pos)) corePos.add(pos); // mark it so one of valid cores are chosen when joining assemblies
					else entity.assignCore(null); // destroy it if it's on the wrong place
				}
			}
			try {
				explorePosition(pos);
			} catch(StackOverflowError error) {
				closure = true; // "STACK OVERFLOW! PWR too gigantic!"
			}
		}
	}

	void close() {
		if(closure) return;
		closure = true;
		List<BlockPos> members = new ArrayList<>(blockPos);

		BlockPos outCorePos = null;
		if(!world.isClientSide) {
			if(corePos.size() >= 2) { // if theres multiple cores
				// pick random...
				Object[] array = corePos.toArray();
				outCorePos = (BlockPos) (array[world.random.nextInt(array.length)]);
				// and then remove the rest
				for(BlockPos pos : corePos) {
					if(!pos.equals(outCorePos)) {
						PWRComponentEntity entity = getPWREntity(pos);
						if(entity != null) entity.assignCore(null);
					}
				}
			} else if(corePos.size() <= 0) { // if theres not a single available core
				// pick random member...
				Object[] array = potentialPos.toArray();
				if(array.length > 0) outCorePos = (BlockPos) (array[world.random.nextInt(array.length)]); // and book it as the new core
			} else // if theres exactly one, keep it
				outCorePos = (BlockPos) corePos.toArray()[0];
		} else {
			outCorePos = triggerPos;
		}

		// iterate over all members (blocks)
		Set<BlockPos> channels = new LeafiaSet<>();
		Set<BlockPos> exchangers = new LeafiaSet<>();
		Set<PWRElementTE> teElements = new LeafiaSet<>();
		int conductors = 0;
		for(BlockPos pos : members) {
			boolean shouldHaveCoreCoords = false;
			PWRComponentBlock pwr = getPWRBlock(pos);
			// check if it should have a tile entity
			if(pwr != null) {
				shouldHaveCoreCoords = pwr.tileEntityShouldCreate(world, pos);
				if(pwr instanceof PWRChannelBlock) channels.add(pos);
				if(pwr instanceof PWRConductorBlock) {
					channels.add(pos);
					conductors++;
				}
				if(pwr instanceof PWRExchangerBlock) exchangers.add(pos);
			}
			PWRComponentEntity entity = getPWREntity(pos);
			if(entity != null) { // give coordinates of the core for each valid blocks
				if(!world.isClientSide) {
					PWRData link = entity.getCoreByCorePos();
					if(link != null && !members.contains(link.corePos) && isMeltdown) {
						link.explode(world, null, null, 1);
						return;
					}
					if(entity instanceof PWRElementTE element) teElements.add(element);
				}
				entity.setCoreLink(shouldHaveCoreCoords ? outCorePos : null);
			}
		}
		// if position for core is booked,
		if(outCorePos != null) {
			PWRComponentEntity entity = getPWREntity(outCorePos);
			if(entity != null) {
				if(entity.getCore() == null && !world.isClientSide) // and if the target block doesn't have a core yet
					entity.assignCore(new PWRData((BlockEntity) entity)); // assign it
				PWRData core = entity.getCore();
				if(core != null) {
					core.members = blockPos;
					core.controls = controlPositions;
					core.fuels = fuelPositions;
					if(!world.isClientSide) {
						core.coriums = this.coriums.size();
						float avg = sumHardness / Math.max(divide, 1);
						float hardness = avg * 0.8f + maxHardness * 0.2f;
						core.toughness = (int) (Math.pow(hardness, 0.25) * 4800);
						core.lastChannels = channels.size();
						core.lastConductors = conductors;
						core.resizeTanks(channels.size(), conductors);
						gridFill();
						LeafiaSet<BlockPos> projection = new LeafiaSet<>();
						for(Entry<Pair<Integer, Integer>, Pair<Integer, Boolean>> entry : projected.entrySet())
							projection.add(new BlockPos(entry.getKey().getKey(), entry.getValue().getKey(), entry.getKey().getValue()));
						core.projection = projection;
						core.onDiagnosis(world);
					}
				}
			}
		}
		if(!world.isClientSide) {
			for(BlockPos pos : members) {
				PWRComponentEntity entity = getPWREntity(pos);
				if(entity != null) entity.onDiagnosis();
			}
		}
		float channelRange = 3f;
		float channelExponent = 0.2f;
		for(PWRElementTE elm : teElements) {
			if(!elm.isRemoved()) {
				BlockPos pos = elm.getBlockPos();
				int height = elm.getHeight();
				float ch = 0;
				float ex = 0;
				for(BlockPos channel : channels) {
					BlockPos local = channel.subtract(pos);
					local = local.above(Mth.clamp(-local.getY(), 0, height));
					ch += Math.pow(Math.max(1 - distance(local) / channelRange, 0), channelExponent);
				}
				for(BlockPos exchanger : exchangers) {
					BlockPos local = exchanger.subtract(pos);
					local = local.above(Mth.clamp(-local.getY(), 0, height));
					ex += Math.pow(Math.max(1 - distance(local) / channelRange, 0), channelExponent);
				}
				elm.channelScale = (float) (Math.pow(ch, 0.35) / Math.pow(height, 0.35));
				elm.exchangerScale = 1 + ex / height;
			}
		}
	}

	/** 1.12.2's {@code Vec3i.getDistance(0, 0, 0)}: the Euclidean length, in double. */
	static double distance(BlockPos local) {
		return Math.sqrt((double) local.getX() * local.getX() + (double) local.getY() * local.getY() + (double) local.getZ() * local.getZ());
	}

	public void confirmLife() {
		lastConfirmed = MathLeafia.getTime32s();
	}

	public int checkTimeElapsed() {
		return MathLeafia.getTimeDifference32s(MathLeafia.getTime32s(), lastConfirmed);
	}

	public void destroy() {
		ongoing.remove(this);
	}
}
