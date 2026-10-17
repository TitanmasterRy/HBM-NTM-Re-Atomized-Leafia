package com.leafia.unsorted;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineChungus;
import com.hbm.blocks.machine.MachineCoker;
import com.hbm.blocks.machine.MachineElectrolyser;
import com.hbm.blocks.machine.MachineExcavator;
import com.hbm.blocks.machine.MachineFrackingTower;
import com.hbm.blocks.machine.MachineICF;
import com.hbm.blocks.machine.MachineMiningLaser;
import com.hbm.blocks.machine.MachineOilWell;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.elevators.floors.EvFloor;
import com.leafia.dev.LeafiaUtil;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structural integrity: a building that is not held up falls down. Upstream's
 * {@code com.leafia.unsorted.StructuralIntegrityHandler}, behind the {@code structuralIntegrity}
 * switch, which is off - upstream's own config comment on it is "VERY LAGGY &amp; CHALLENGING".
 *
 * <h2>The algorithm, in plain words</h2>
 *
 * Every solid block has a <b>mass</b> (how much it weighs on what holds it) and a <b>glue</b>
 * (how much it can hold). When a block changes, the handler asks whether the block - and the
 * vertical column it is part of - is held up:
 *
 * <ol>
 *   <li>The root's mass, plus the surplus mass of the unbroken solid column above and below it
 *       (each block there contributes {@code max(mass - glue, 0)}), is the load.
 *   <li>If the column runs all the way down to the bottom of the world, it is grounded and holds
 *       anything: its glue is infinite and the search ends.
 *   <li>Otherwise every block of the column looks at its four horizontal neighbours. A solid
 *       neighbour that has not been visited is searched the same way, one level deeper, and
 *       inherits the load so far. If what comes back is itself held up (its load no more than its
 *       glue), its whole column is marked <b>supported</b>.
 *   <li>A supported neighbour adds its glue to the root's. As soon as glue reaches load the
 *       column is safe and the search stops.
 *   <li>If the root ends with more load than glue, the <b>bottom block of its column</b> collapses:
 *       a full, solid, drawn cube becomes a falling block, anything else breaks and drops. The
 *       fall changes the world, which notifies the neighbours, which queues the next check - a
 *       collapse spreads one wave per tick, never recursively.
 * </ol>
 *
 * <h2>The bounds, which are the whole of what keeps this playable</h2>
 *
 * <ul>
 *   <li><b>{@value #MAX_DEPTH} levels</b> of horizontal search from the root. A column at the
 *       limit is counted but does not look further.
 *   <li><b>{@value #BUDGET} calculations per server tick</b>, counted across every level, and
 *       <b>one calculation per position per tick</b> ({@link #blockedPoses}). The rest wait for
 *       the next tick (D-3076: the listener carries them over; upstream dropped them, which left
 *       part of a large edit's building unchecked until something next touched it).
 *   <li>A search stops, and the check is abandoned, the moment it would read a chunk that is not
 *       loaded - it never loads or generates one.
 *   <li>Nether and End are never checked.
 * </ul>
 *
 * Worst case per calculation: the search visits each column at most once (a shared visited set),
 * so it is bounded by the diamond of columns within {@value #MAX_DEPTH} steps of the root, 5,101
 * columns, each read top to bottom plus four neighbours per block. Times the budget, that is the
 * ceiling per tick; it is reached only by a very large ungrounded structure, since every column
 * that touches the bottom of the world ends its branch on the spot. See D-1086.
 *
 * <h2>What changed in the port</h2>
 *
 * <ul>
 *   <li><b>No {@code Material}.</b> Upstream's three special materials and its "rock" are
 *       recovered from a generated table for base-mod blocks and from sound and tags for the
 *       rest - see {@link Kind}.
 *   <li>{@code META >= 12} (the core of a multiblock) is {@code PART == CORE}.
 *   <li>The glue table names ten machine classes through a wildcard import; four exist here. The
 *       other six, and the silo hatch pair that no longer exists under that name, are listed on
 *       {@link #getGlue}.
 *   <li>A chunk is "generated" if it is <em>loaded</em>: nothing here may ask a chunk to load.
 *   <li>Nether and End are keyed by dimension rather than by upstream's integer id.
 *   <li>Upstream's {@code checkPoses} - a set every supported neighbour was added to and nothing
 *       ever read, since the loop that consumed it is commented out - is not kept.
 * </ul>
 *
 * Not here: the client preview of a block about to be placed, {@code ImpactSeismic},
 * {@code DigammaCrater} and {@code BlockControlPanel}, which are other files' callers. The
 * simulate mode they use is ported, because it is the same computation.
 */
public class StructuralIntegrityHandler {

	public static final StructuralIntegrityHandler SERVER = new StructuralIntegrityHandler();
	public static final StructuralIntegrityHandler LOCAL = new StructuralIntegrityHandler();

	/** Upstream's {@code AUTOMATIC}: read through, so the switch is one value and never a copy. */
	public static boolean automatic() {
		return LeafiaFeatures.structuralIntegrity;
	}

	static final int MAX_DEPTH = 50;
	/** Calculations per tick, across every level. Upstream's literal {@code 200}, tested with {@code >}. */
	static final int BUDGET = 200;

	public static int calculations = 0;

	/** Whether this tick's budget is spent (upstream's test is {@code calculations > BUDGET}, so 201 are made). */
	public static boolean overBudget() {
		return calculations > BUDGET;
	}
	/**
	 * Positions already calculated this tick. <b>Keyed by position alone</b>, so two dimensions
	 * share it; the Nether and End are excluded, which leaves modded dimensions. Upstream's.
	 */
	public static LongOpenHashSet blockedPoses = new LongOpenHashSet();

	public static final Set<ResourceKey<Level>> blacklistedDimensions = new HashSet<>();
	static {
		blacklistedDimensions.add(Level.NETHER);
		blacklistedDimensions.add(Level.END);
	}

	private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	public static void collapse(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return;
		if (LeafiaUtil.isSolidVisibleCube(state, level, pos)) {
			// D-3076: a block with an inventory keeps it: the block entity is saved, taken off the cell quietly (so the block's own
			// removal drops nothing) and handed to the falling block, which writes it back where it lands (upstream's fell without it)
			net.minecraft.nbt.CompoundTag carried = null;
			net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(pos);
			if (entity != null && !level.isClientSide && !(state.getBlock() instanceof com.hbm.blocks.BlockDummyable)) {
				carried = entity.saveWithoutMetadata(level.registryAccess());
				level.removeBlockEntity(pos);
			}
			// fall() puts the fluid state back in the cell, which is what setBlockToAir was.
			FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, pos, state);
			fallingBlock.time = 1;
			if (carried != null) fallingBlock.blockData = carried;
		} else
			level.destroyBlock(pos, true);
	}

	/**
	 * What upstream's {@code Material} decided, as far as it decided anything here: the glue and
	 * mass of the four groups it names. There is no {@code Material} in 1.21.1, so a block's group
	 * is recovered in two steps:
	 *
	 * <ol>
	 *   <li><b>A base-mod block is looked up</b> in {@link GeneratedBlockMaterials}, which
	 *       a generator script writes from the 1.7.10 sources. The sound is no
	 *       use for these: most NTM iron never set a step sound, so {@code steel_scaffold}, which
	 *       is {@code Material.iron}, sounds like stone and would read as rock.
	 *   <li><b>Everything else is read from its sound and tags</b>, which is how a vanilla block
	 *       says what it is made of. See {@link #ofSoundAndTag}.
	 * </ol>
	 *
	 * <ul>
	 *   <li>{@link #IRON}: upstream's {@code IRON} and {@code ANVIL}.
	 *   <li>{@link #WOOD}: the wood family of sounds.
	 *   <li>{@link #ROCK}: upstream's {@code ROCK}, whose table entry is commented out and which is
	 *       special-cased afterwards.
	 *   <li>{@link #OTHER}: everything else - dirt, sand, leaves, glass, wool.
	 * </ul>
	 *
	 * The vanilla half is an approximation, and the one place a block can be classified
	 * differently from upstream; the tests pin one block per group.
	 */
	enum Kind {
		IRON(40, 5), WOOD(6, 1), ROCK(25, 0), OTHER(12, 1),
		/** Never returned: the cache's mark for a block the table does not name. */
		UNLISTED(0, 0);

		final int glue, mass;

		Kind(int glue, int mass) {
			this.glue = glue;
			this.mass = mass;
		}

		/** Sounds only stone makes: bedrock is in no mining tag, and was rock. */
		private static final Set<SoundType> STONE_SOUNDS = Set.of(SoundType.STONE, SoundType.DEEPSLATE, SoundType.NETHERRACK,
				SoundType.BASALT, SoundType.NETHER_BRICKS, SoundType.TUFF, SoundType.CALCITE, SoundType.DEEPSLATE_BRICKS);

		private static final Map<Block, Kind> LISTED = new ConcurrentHashMap<>();

		static Kind of(BlockState state) {
			Block block = state.getBlock();
			Kind listed = LISTED.get(block);
			if (listed == null) {
				ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
				String group = key.getNamespace().equals("hbm") ? GeneratedBlockMaterials.GROUP.get(key.getPath()) : null;
				listed = group == null ? UNLISTED : valueOf(group);
				LISTED.put(block, listed);
			}
			return listed != UNLISTED ? listed : ofSoundAndTag(state);
		}

		/**
		 * Metal-sounding blocks and anything tagged {@code anvil} are {@link #IRON} (copper,
		 * netherite, chains and lanterns did not exist in 1.12.2 and are metal here); the wood
		 * sounds are {@link #WOOD}; whatever a pickaxe mines, or that sounds like stone, and does not
		 * sound like glass is {@link #ROCK} (ice is glass-sounding, and was never {@code ROCK});
		 * the rest {@link #OTHER}.
		 */
		static Kind ofSoundAndTag(BlockState state) {
			SoundType sound = state.getSoundType();
			if (state.is(BlockTags.ANVIL) || sound == SoundType.ANVIL || sound == SoundType.METAL
					|| sound == SoundType.NETHERITE_BLOCK || sound == SoundType.COPPER
					|| sound == SoundType.CHAIN || sound == SoundType.LANTERN)
				return IRON;
			if (sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD
					|| sound == SoundType.CHERRY_WOOD || sound == SoundType.BAMBOO_WOOD)
				return WOOD;
			if (sound != SoundType.GLASS && (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || STONE_SOUNDS.contains(sound)))
				return ROCK;
			return OTHER;
		}
	}

	/**
	 * How much a block holds. Zero for the core of any multiblock, so the glue of a machine is
	 * its shell and not its heart.
	 *
	 * <p><b>The machine table</b> - upstream's, through {@code com.hbm.blocks.machine.*}, which
	 * a dependency checker cannot see:
	 *
	 * <pre>
	 *   RBMKBase             20   ported
	 *   MachineChungus       22   ported
	 *   MachineOilWell       22   ported
	 *   MachineCoker         32   ported
	 *   MachineElectrolyser  22   ported (base mod, merged in 2026-10-07)
	 *   MachineExcavator     42   ported (base mod)
	 *   MachineFrackingTower 62   ported (base mod)
	 *   BlockSiloHatch       22   not a class in the 1.7.10 spec; the hatches are BlockDoorGeneric
	 *   DummyBlockSiloHatch  22   multiblocks here, so matched by block: silo_hatch, silo_hatch_large
	 *   MachineICF           22   ported (base mod, the allen consolidation, D-2203)
	 *   MachineMiningLaser   22   ported (base mod, the allen consolidation, D-2203)
	 * </pre>
	 *
	 * Each unported class is a plain block until it lands, so it has the glue of its material and
	 * <em>not</em> its table value. A test fails the day any of them becomes a multiblock, which
	 * is the day its line belongs here.
	 */
	public static int getGlue(BlockState state, @Nullable Level level, @Nullable BlockPos pos) {
		// D-3075: an elevator landing is tested before BlockDummyable (it is one), so its own lines are reached
		if (state.getBlock() instanceof EvFloor)
			return 1;
		if (state.getBlock() instanceof BlockDummyable) {
			if (state.hasProperty(BlockDummyable.PART) && state.getValue(BlockDummyable.PART) == BlockDummyable.Part.CORE)
				return 0;
			if (state.getBlock() instanceof MachineElectrolyser)
				return 22;
			if (state.getBlock() instanceof RBMKBase)
				return 20;
			if (state.getBlock() instanceof MachineFrackingTower)
				return 62;
			if (state.getBlock() instanceof MachineCoker)
				return 32;
			if (state.getBlock() instanceof MachineOilWell)
				return 22;
			if (state.getBlock() instanceof MachineExcavator)
				return 42;
			if (state.is(ModBlocks.silo_hatch.get()) || state.is(ModBlocks.silo_hatch_large.get()))
				return 22;
			if (state.getBlock() instanceof MachineChungus)
				return 22;
			if (state.getBlock() instanceof MachineICF)
				return 22;
			if (state.getBlock() instanceof MachineMiningLaser)
				return 22;
			return 0;
		}
		if (state.is(ModBlocks.dfc_core.get()))
			return 0;
		Kind kind = Kind.of(state);
		int glue = kind.glue;
		// D-3075: rock no longer returns 25 before the multipliers, so concrete_rebar's x8 is live (25 -> 200)
		if (state.is(ModBlocks.concrete_rebar.get()))
			glue *= 8;
		if (state.is(ModBlocks.steel_scaffold.get()))
			glue *= 4;
		return glue;
	}

	public static int getMass(BlockState state, @Nullable Level level, @Nullable BlockPos pos) {
		if (state.getBlock() instanceof EvFloor)
			return 0;
		if (state.getBlock() instanceof BlockDummyable) {
			if (state.getBlock() instanceof RBMKBase)
				return 1;
			return 10;
		}
		if (state.is(ModBlocks.dfc_core.get()))
			return 0;
		Kind kind = Kind.of(state);
		if (kind == Kind.ROCK)
			return 1 + Math.min((int) Math.pow(state.getBlock().getExplosionResistance(), 0.45) / 2, 15);
		int mass = kind.mass;
		BlockGetter getter = level != null && pos != null ? level : EmptyBlockGetter.INSTANCE;
		if (!state.isCollisionShapeFullBlock(getter, level != null && pos != null ? pos : BlockPos.ZERO)) mass /= 3;
		return Math.max(mass, 1);
	}

	public static class SimulationData {
		public double maxRatio = 0;
		public double maxMass = 0;
		public double maxGlue = 0;
	}

	public static class CalStack {
		public int mass;
		public int glue;
		public int count;
		public boolean terminate;
		public boolean bottomReached;
		public final MutableBlockPos scratch = new MutableBlockPos();
		LongOpenHashSet ignore;
		LongOpenHashSet supporteds;
		final LongOpenHashSet newIgnore = new LongOpenHashSet();
		CalStack parent;
		SimulationData simulation = null;

		public CalStack(int mass) {
			this.mass = mass;
		}

		void reset(int mass, int count, LongOpenHashSet ignore, LongOpenHashSet supporteds, CalStack parent) {
			this.mass = mass;
			this.glue = 0;
			this.count = count;
			this.terminate = false;
			this.bottomReached = false;
			this.ignore = ignore;
			this.supporteds = supporteds;
			this.parent = parent;
			this.newIgnore.clear();
			this.simulation = parent.simulation;
		}
	}

	private final CalStack[] FRAME_POOL = new CalStack[MAX_DEPTH + 2];
	private final LongOpenHashSet ROOT_IGNORE = new LongOpenHashSet();
	private final LongOpenHashSet ROOT_SUPPORTEDS = new LongOpenHashSet();

	/** The deepest {@code count} any calculation on this instance has reached. A probe for tests. */
	public int deepest = 0;

	private CalStack frame(int depth) {
		CalStack f = FRAME_POOL[depth];
		if (f == null) FRAME_POOL[depth] = f = new CalStack(0);
		return f;
	}

	static boolean needsSupport(BlockState state) {
		return state.isSolid();
	}

	void calculate(CalStack stack, Level level, BlockPos pos) {
		MutableBlockPos mutable = stack.scratch;
		int baseX = pos.getX(), baseY = pos.getY(), baseZ = pos.getZ();
		mutable.set(baseX, baseY, baseZ);
		int maxRelativeY = 0, minRelativeY = 0;
		if (stack.count > deepest) deepest = stack.count;
		// fill vertical column & pillar check
		LongOpenHashSet newIgnore = stack.newIgnore;
		LongOpenHashSet ignore = stack.ignore;
		LongOpenHashSet supporteds = stack.supporteds;

		stack.mass += getMass(level.getBlockState(pos), level, pos);
		newIgnore.add(mutable.asLong());

		int extraMass = 0;

		while (true) {
			mutable.setY(mutable.getY() + 1);
			if (!level.isInWorldBounds(mutable)) break;
			long key = mutable.asLong();
			if (ignore.contains(key)) break;
			BlockState above = level.getBlockState(mutable);
			if (!needsSupport(above)) break;
			newIgnore.add(key);
			maxRelativeY++;
			extraMass += Math.max(getMass(above, level, mutable) - getGlue(above, level, mutable), 0);
		}
		mutable.setY(baseY);
		while (true) {
			mutable.setY(mutable.getY() - 1);
			if (!level.isInWorldBounds(mutable)) {
				stack.bottomReached = true;
				ignore.addAll(newIgnore);
				stack.glue = Integer.MAX_VALUE;
				return;
			}
			long key = mutable.asLong();
			if (ignore.contains(key)) break;
			BlockState below = level.getBlockState(mutable);
			if (!needsSupport(below)) break;
			newIgnore.add(key);
			minRelativeY--;
			extraMass += Math.max(getMass(below, level, mutable) - getGlue(below, level, mutable), 0);
		}
		ignore.addAll(newIgnore);
		stack.mass += extraMass;

		if (stack.count > MAX_DEPTH) return;

		int cachedCx = Integer.MIN_VALUE, cachedCz = Integer.MIN_VALUE;
		boolean cachedLoaded = false;

		int mass = stack.mass;
		int glue = stack.glue;

		outer:
		for (int yo = minRelativeY; yo <= maxRelativeY; yo++) {
			for (Direction face : HORIZONTALS) {
				int nx = baseX + face.getStepX();
				int ny = baseY + yo;
				int nz = baseZ + face.getStepZ();
				mutable.set(nx, ny, nz);

				int cx = nx >> 4, cz = nz >> 4;
				if (cx != cachedCx || cz != cachedCz) {
					cachedCx = cx;
					cachedCz = cz;
					cachedLoaded = level.hasChunk(cx, cz);
				}
				if (!cachedLoaded) {
					stack.terminate = true;
					return;
				}

				BlockState nState = level.getBlockState(mutable);
				if (!needsSupport(nState)) continue;
				int glueAdd = getGlue(nState, level, mutable);
				long neighborLong = mutable.asLong();

				if (!ignore.contains(neighborLong)) {
					CalStack next = frame(stack.count + 1);
					stack.mass = mass;
					stack.glue = glue;
					next.reset(stack.mass, stack.count + 1, ignore, supporteds, stack);
					calculate(next, level, mutable);
					if (next.terminate) {
						stack.terminate = true;
						return;
					}
					if (next.mass <= next.glue)
						supporteds.addAll(next.newIgnore);
				}
				if (supporteds.contains(neighborLong)) {
					stack.glue += glueAdd;
					supporteds.addAll(newIgnore);
					if (stack.glue >= stack.mass) break outer;
				}
			}
		}

		if (stack.parent == null) {
			if (stack.mass > stack.glue) {
				if (stack.simulation == null)
					collapse(level, pos.below(-minRelativeY));
				else
					stack.simulation.maxRatio = 1;
			}
		} else {
			stack.parent.mass = Math.max(stack.mass, stack.parent.mass);
		}
		if (stack.simulation != null) {
			if (stack.parent == null && stack.mass > stack.glue)
				stack.simulation.maxRatio = 1;
			stack.simulation.maxMass = Math.max(stack.simulation.maxMass, stack.mass);
			stack.simulation.maxGlue = Math.max(stack.simulation.maxGlue, stack.glue);
		}
	}

	static boolean isChunkLoaded(Level level, BlockPos pos) {
		return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
	}

	public static void handleBlock(Level level, BlockPos pos) {
		SERVER.handleBlock(level, pos, false, false, null);
	}

	public static SimulationData handleBlockSimulate(Level level, BlockPos pos, @Nullable BlockState forcedBlockState) {
		return LOCAL.handleBlock(level, pos, true, false, forcedBlockState);
	}

	public SimulationData handleBlock(Level level, BlockPos pos, boolean simulate, boolean verifyMode, @Nullable BlockState forcedBlockState) {
		if (level.isClientSide && !simulate) return null;
		if (blacklistedDimensions.contains(level.dimension())) return null;
		if (!verifyMode) {
			if (calculations > BUDGET && !simulate) return null;
		}
		if (!isChunkLoaded(level, pos)) return null;
		BlockState state = forcedBlockState != null ? forcedBlockState : level.getBlockState(pos);
		if (!needsSupport(state)) return null;
		if (blockedPoses.contains(pos.asLong())) return null;
		if (!simulate)
			blockedPoses.add(pos.asLong());
		if (!simulate && !verifyMode)
			calculations++;
		ROOT_IGNORE.clear();
		ROOT_SUPPORTEDS.clear();
		CalStack stacc = new CalStack(0);
		stacc.ignore = ROOT_IGNORE;
		stacc.supporteds = ROOT_SUPPORTEDS;
		if (simulate) {
			SimulationData sim = new SimulationData();
			stacc.simulation = sim;
		}
		calculate(stacc, level, pos);
		if (stacc.simulation != null) {
			if (stacc.simulation.maxRatio <= 0) {
				if (stacc.simulation.maxGlue == 0)
					stacc.simulation.maxRatio = stacc.simulation.maxMass <= 0 ? 0 : 1;
				else
					stacc.simulation.maxRatio = Math.min(stacc.simulation.maxMass / stacc.simulation.maxGlue, 0.99);
			}
		}
		return stacc.simulation;
	}
}
