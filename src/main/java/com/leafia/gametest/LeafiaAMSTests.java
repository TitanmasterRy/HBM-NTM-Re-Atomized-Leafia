package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ISatChip;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemLens;
import com.hbm.saveddata.SatelliteSavedData;
import com.hbm.saveddata.satellites.SatelliteResonator;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseBlock;
import com.leafia.contents.machines.powercores.ams.base.AMSBaseTE;
import com.leafia.contents.machines.powercores.ams.base.container.AMSBaseMenu;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterBlock;
import com.leafia.contents.machines.powercores.ams.emitter.AMSEmitterTE;
import com.leafia.contents.machines.powercores.ams.emitter.container.AMSEmitterMenu;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerBlock;
import com.leafia.contents.machines.powercores.ams.stabilizer.AMSStabilizerTE;
import com.leafia.contents.machines.powercores.ams.stabilizer.container.AMSStabilizerMenu;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The antimatter reaction chamber (D-3002): its three machines' shapes, every number in the base,
 * the emitter and the stabilizer, the whole assembly run for a tick, what it does to a cow, and the
 * assets its renderers and screens draw from.
 *
 * <p>Every test that runs a machine does it inside one body. A block entity ticks on its own every
 * game tick, so a test that spread a sequence across ticks would be racing the machine it is
 * testing; inside one body the world does not advance, and a call to {@code updateEntity()} is a
 * tick (D-953). The numbers pinned from the arithmetic that is easy to get wrong - the float
 * additions, the integer-divided bell - were worked out in Python with {@code float32} and
 * transcribed, not copied out of a run.
 *
 * <p>The assembly is 17 blocks across, so it uses the 20-block {@code leafia:empty20} template.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaAMSTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";
	private static final String HUGE = "empty20";

	// ---- the rig ---------------------------------------------------------------------------

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((java.util.List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	private static float yawFor(Direction looking) {
		return switch(looking) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			case EAST -> 270F;
			default -> throw new IllegalArgumentException(looking.toString());
		};
	}

	/**
	 * Places a machine the way a player does: the block goes in the clicked cell and
	 * {@code setPlacedBy} rebuilds it around a core {@code offset} cells back along {@code dir}.
	 * Returns whether a core stands where it should.
	 */
	private static boolean put(GameTestHelper helper, ServerPlayer player, Block block, BlockPos core, Direction dir, int offset) {

		BlockPos at = core.relative(dir, offset);

		player.setYRot(yawFor(dir.getOpposite()));
		player.setXRot(0F);

		helper.setBlock(at, block.defaultBlockState());
		block.setPlacedBy(helper.getLevel(), helper.absolutePos(at), block.defaultBlockState(), player, new ItemStack(block));

		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(core));
		return state.getBlock() == block && state.getValue(BlockDummyable.PART) == Part.CORE && state.getValue(BlockDummyable.DIR) == dir;
	}

	private static AMSBaseTE base(GameTestHelper helper, BlockPos core) {
		boolean ok = put(helper, mockPlayer(helper), LeafiaBlocks.ams_base.get(), core, Direction.NORTH, 1);
		helper.assertTrue(ok, "the base was not placed at " + core);
		return (AMSBaseTE) helper.getLevel().getBlockEntity(helper.absolutePos(core));
	}

	private static AMSEmitterTE emitter(GameTestHelper helper, BlockPos core) {
		boolean ok = put(helper, mockPlayer(helper), LeafiaBlocks.ams_emitter.get(), core, Direction.NORTH, 0);
		helper.assertTrue(ok, "the emitter was not placed at " + core);
		return (AMSEmitterTE) helper.getLevel().getBlockEntity(helper.absolutePos(core));
	}

	private static AMSStabilizerTE stabilizer(GameTestHelper helper, BlockPos core, Direction dir) {
		boolean ok = put(helper, mockPlayer(helper), LeafiaBlocks.ams_limiter.get(), core, dir, 0);
		helper.assertTrue(ok, "the stabilizer was not placed at " + core + " facing " + dir);
		return (AMSStabilizerTE) helper.getLevel().getBlockEntity(helper.absolutePos(core));
	}

	/** A lone emitter in the 16-block arena, core at (8, 0, 8): the tower is six blocks tall and the small arena five. */
	private static AMSEmitterTE loneEmitter(GameTestHelper helper) {
		return emitter(helper, new BlockPos(8, 0, 8));
	}

	/** A lone stabilizer in the 16-block arena, facing south. */
	private static AMSStabilizerTE loneStabilizer(GameTestHelper helper) {
		return stabilizer(helper, new BlockPos(8, 0, 8), Direction.SOUTH);
	}

	/** The core at (10, 0, 10) of the 20-block arena with an emitter nine up and a working stabilizer on each axis. */
	private static final class Assembly {

		final GameTestHelper helper;
		final BlockPos core = new BlockPos(10, 0, 10);
		final AMSBaseTE base;
		final AMSEmitterTE emitter;
		final AMSStabilizerTE east, west, south, north;
		final List<Integer> frequencies = new ArrayList<>();

		Assembly(GameTestHelper helper) {
			this.helper = helper;
			base = base(helper, core);
			emitter = emitter(helper, core.above(9));
			// The directions that make each stabilizer read as upstream's check wants: +x is rotateShitfuck 4,
			// -x is 5, +z is 2, -z is 3, and rotateShitfuck is the clockwise neighbour.
			east = stabilizer(helper, core.east(6), Direction.SOUTH);
			west = stabilizer(helper, core.west(6), Direction.NORTH);
			south = stabilizer(helper, core.south(6), Direction.WEST);
			north = stabilizer(helper, core.north(6), Direction.EAST);
		}

		/** Four catalysts, the vibrant singularity, three resonator chips on three frequencies, full tanks. */
		void load(int firstFrequency) {
			ServerLevel level = helper.getLevel();
			SatelliteSavedData data = SatelliteSavedData.getData(level);

			for(int i = 8; i <= 11; i++) base.slots[i] = new ItemStack(ModItems.ams_catalyst_iron.get());
			base.slots[12] = new ItemStack(ModItems.ams_core_sing.get());

			for(int i = 0; i < 3; i++) {
				int freq = firstFrequency + i;
				data.sats.put(freq, new SatelliteResonator());
				frequencies.add(freq);
				ItemStack chip = new ItemStack(ModItems.sat_chip.get());
				ISatChip.setFreqS(chip, freq);
				base.slots[13 + i] = chip;
			}

			for(FluidTank tank : base.tanks) tank.setFill(8000);
		}

		void workingStabilizers(int efficiency) {
			for(AMSStabilizerTE s : new AMSStabilizerTE[] { east, west, south, north }) s.efficiency = efficiency;
			emitter.efficiency = 100;
		}

		void cleanup() {
			SatelliteSavedData data = SatelliteSavedData.getData(helper.getLevel());
			for(int freq : frequencies) data.sats.remove(freq);
		}

		/** Discards every nuke the machine left in the arena, which would otherwise tick on. */
		void clearNukes() {
			for(EntityNukeExplosionMK5 nuke : nukes()) nuke.discard();
		}

		List<EntityNukeExplosionMK5> nukes() {
			BlockPos at = helper.absolutePos(core);
			return helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class, new AABB(at).inflate(3D));
		}
	}

	private static ItemStack identifier(FluidType type) {
		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(stack, type);
		return stack;
	}

	// ---- registration ----------------------------------------------------------------------

	/** Three blocks in the tab, six block entity types, three menu types, three items, on a switch that defaults on. */
	@GameTest(template = EMPTY)
	public static void theBlocksTheirTypesAndTheirMenusAreRegistered(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.ams, "ams defaults on");

		record Entry(String id, Class<? extends Block> type) {}

		for(Entry e : new Entry[] { new Entry("ams_base", AMSBaseBlock.class), new Entry("ams_emitter", AMSEmitterBlock.class),
				new Entry("ams_limiter", AMSStabilizerBlock.class) }) {

			var block = BuiltInRegistries.BLOCK.getOptional(LeafiaRef.rl(e.id));
			helper.assertTrue(block.isPresent() && e.type.isInstance(block.get()), e.id + " is not registered as upstream's block");
			helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
					.anyMatch(s -> s.get().asItem() == block.get().asItem()), e.id + " is in no creative tab");
			helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntityType(e.id, false)).equals(LeafiaRef.rl(e.id)),
					e.id + "'s core type is misnamed");
			helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntityType(e.id, true)).equals(LeafiaRef.rl(e.id + "_dummy")),
					e.id + "'s proxy type is misnamed");
			helper.assertTrue(BuiltInRegistries.MENU.getKey(menuType(e.id)).equals(LeafiaRef.rl(e.id)), e.id + "'s menu is misnamed");
		}

		for(String item : new String[] { "ams_muzzle", "ams_focus_limiter", "ams_focus_booster" }) {
			var registered = BuiltInRegistries.ITEM.getOptional(LeafiaRef.rl(item));
			helper.assertTrue(registered.isPresent(), item + " is not registered");
			helper.assertTrue(registered.get().getDefaultMaxStackSize() == 1, item + " stacks");
			helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
					.anyMatch(s -> s.get().asItem() == registered.get()), item + " is in no creative tab");
		}
		helper.succeed();
	}

	private static net.minecraft.world.level.block.entity.BlockEntityType<?> blockEntityType(String id, boolean proxy) {
		return switch(id) {
			case "ams_base" -> proxy ? LeafiaBlockEntities.AMS_BASE_DUMMY.get() : LeafiaBlockEntities.AMS_BASE.get();
			case "ams_emitter" -> proxy ? LeafiaBlockEntities.AMS_EMITTER_DUMMY.get() : LeafiaBlockEntities.AMS_EMITTER.get();
			default -> proxy ? LeafiaBlockEntities.AMS_STABILIZER_DUMMY.get() : LeafiaBlockEntities.AMS_STABILIZER.get();
		};
	}

	private static net.minecraft.world.inventory.MenuType<?> menuType(String id) {
		return switch(id) {
			case "ams_base" -> LeafiaMenuTypes.AMS_BASE.get();
			case "ams_emitter" -> LeafiaMenuTypes.AMS_EMITTER.get();
			default -> LeafiaMenuTypes.AMS_STABILIZER.get();
		};
	}

	/** Hardness 5 and resistance 100 through D-012's x0.6, no tool, no spawns, and each proxy type only for its own block. */
	@GameTest(template = EMPTY)
	public static void theBlocksAreUpstreamsStrengthAndTheProxyTypesAreTheirOwn(GameTestHelper helper) {

		Block[] blocks = { LeafiaBlocks.ams_base.get(), LeafiaBlocks.ams_emitter.get(), LeafiaBlocks.ams_limiter.get() };

		for(int i = 0; i < 3; i++) {
			Block block = blocks[i];
			BlockState state = block.defaultBlockState();
			String id = BuiltInRegistries.BLOCK.getKey(block).getPath();

			helper.assertTrue(Math.abs(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) - 5F) < 1e-4,
					id + " hardness is " + state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO));
			helper.assertTrue(Math.abs(block.getExplosionResistance() - 60F) < 1e-3, id + " blast resistance is " + block.getExplosionResistance());
			helper.assertTrue(!state.requiresCorrectToolForDrops(), id + " asks for a tool");
			helper.assertTrue(!state.isValidSpawn(helper.getLevel(), BlockPos.ZERO, EntityType.ZOMBIE), "a mob may spawn on " + id);

			for(int j = 0; j < 3; j++) {
				boolean own = i == j;
				helper.assertTrue(blockEntityType(id, true).isValid(blocks[j].defaultBlockState()) == own,
						id + "'s proxy type is " + (own ? "not valid for its own block" : "valid for another machine"));
				helper.assertTrue(blockEntityType(id, false).isValid(blocks[j].defaultBlockState()) == own,
						id + "'s core type is " + (own ? "not valid for its own block" : "valid for another machine"));
			}
		}
		helper.succeed();
	}

	/** The lenses wear out at upstream's billions, and the muzzle is one to a stack. */
	@GameTest(template = EMPTY)
	public static void theLensesWearAtUpstreamsNumbers(GameTestHelper helper) {

		helper.assertTrue(((ItemLens) LeafiaItems.ams_focus_limiter.get()).maxDamage == 2_500_000_000L, "the limiting focus wears at 2.5 billion");
		helper.assertTrue(((ItemLens) LeafiaItems.ams_focus_booster.get()).maxDamage == 100_000_000L, "the boosting focus wears at 100 million");
		helper.assertTrue(LeafiaItems.ams_muzzle.get().getDefaultMaxStackSize() == 1, "the muzzle stacks");
		helper.succeed();
	}

	// ---- the shapes ------------------------------------------------------------------------

	/** What each cell of a placed machine is: the count of blocks, parts and core-finding, in every facing. */
	private static void checkShape(GameTestHelper helper, Block block, int offset, int cells,
			java.util.function.Function<Direction, List<BlockPos>> expectedExtras, String name) {

		List<String> problems = new ArrayList<>();

		for(Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {

			// Each one is placed, checked and cleared in turn at the same spot.
			BlockPos core = new BlockPos(10, 1, 10);
			ServerPlayer player = mockPlayer(helper);

			if(!put(helper, player, block, core, dir, offset)) {
				problems.add(dir + ": not placed");
				continue;
			}

			BlockPos abs = helper.absolutePos(core);
			BlockDummyable dummyable = (BlockDummyable) block;
			int blocks = 0, cores = 0, extra = 0, orphans = 0;
			List<BlockPos> foundExtras = new ArrayList<>();

			for(int x = -10; x <= 9; x++)
				for(int y = -1; y <= 18; y++)
					for(int z = -10; z <= 9; z++) {
						BlockPos here = abs.offset(x, y, z);
						BlockState state = helper.getLevel().getBlockState(here);
						if(state.getBlock() != block) continue;
						blocks++;
						Part part = state.getValue(BlockDummyable.PART);
						if(part == Part.CORE) cores++;
						if(part == Part.EXTRA) {
							extra++;
							foundExtras.add(here.subtract(abs));
						}
						if(!abs.equals(dummyable.findCore(helper.getLevel(), here))) orphans++;
						BlockEntity be = helper.getLevel().getBlockEntity(here);
						boolean wants = part != Part.DUMMY;
						if(wants != (be != null)) problems.add(dir + ": " + part + " at " + here.subtract(abs) + " has " + be);
					}

			if(blocks != cells) problems.add(dir + ": " + blocks + " blocks, expected " + cells);
			if(cores != 1) problems.add(dir + ": " + cores + " cores");
			if(orphans != 0) problems.add(dir + ": " + orphans + " cells cannot find the core");

			List<BlockPos> expected = expectedExtras.apply(dir);
			if(extra != expected.size() || !foundExtras.containsAll(expected))
				problems.add(dir + ": extras " + foundExtras + ", expected " + expected);

			// Take it all away again for the next facing.
			helper.getLevel().setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
			for(int x = -10; x <= 9; x++)
				for(int y = -1; y <= 18; y++)
					for(int z = -10; z <= 9; z++) {
						BlockPos here = abs.offset(x, y, z);
						if(helper.getLevel().getBlockState(here).getBlock() == block) helper.getLevel().setBlock(here, Blocks.AIR.defaultBlockState(), 3);
					}
		}

		helper.assertTrue(problems.isEmpty(), name + ": " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A 3x3 pedestal two blocks tall - 18 blocks - with its core in the middle of the bottom layer and
	 * an extra on each of the four blocks beside it, in every facing.
	 */
	@GameTest(template = HUGE)
	public static void theBaseIsAThreeByThreePedestalWithFourExtras(GameTestHelper helper) {
		checkShape(helper, LeafiaBlocks.ams_base.get(), 1, 18,
				dir -> List.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)),
				"the base");
	}

	/** A 5x5 tower six blocks tall - 150 blocks - whose extras are the cross in the middle of its top layer. */
	@GameTest(template = HUGE)
	public static void theEmitterIsAFiveByFiveTowerWithAnExtraCrossOnTop(GameTestHelper helper) {
		checkShape(helper, LeafiaBlocks.ams_emitter.get(), 0, 150,
				dir -> List.of(new BlockPos(0, 5, 0), new BlockPos(1, 5, 0), new BlockPos(-1, 5, 0), new BlockPos(0, 5, 1), new BlockPos(0, 5, -1)),
				"the emitter");
	}

	/**
	 * A wall five wide, one thick and six tall - 30 blocks - running along the facing's clockwise axis, with an
	 * extra at each end of its foot. (Upstream also calls {@code makeExtra} on the middle, which is the core and
	 * is left alone.)
	 */
	@GameTest(template = HUGE)
	public static void theStabilizerIsAFiveWideWallWithTwoExtras(GameTestHelper helper) {
		checkShape(helper, LeafiaBlocks.ams_limiter.get(), 0, 30, dir -> {
			Direction side = dir.getClockWise();
			return List.of(BlockPos.ZERO.relative(side, 2), BlockPos.ZERO.relative(side, -2));
		}, "the stabilizer");
	}

	/** Every extra is a proxy that forwards inventory, power and fluid to the core, which is what a cable beside it needs. */
	@GameTest(template = HUGE)
	public static void everyExtraIsAProxyOfItsOwnType(GameTestHelper helper) {

		BlockPos core = new BlockPos(10, 1, 10);
		AMSBaseTE base = base(helper, core);

		BlockPos extra = helper.absolutePos(core.east());
		BlockEntity be = helper.getLevel().getBlockEntity(extra);
		helper.assertTrue(be instanceof BlockEntityProxyCombo, "the extra is " + be);
		helper.assertTrue(be.getType() == LeafiaBlockEntities.AMS_BASE_DUMMY.get(), "the extra's type is " + be.getType());
		helper.assertTrue(((BlockEntityProxyCombo) be).getTile() == base, "the proxy does not resolve to the core");
		helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(core.east().above())) == null, "a plain dummy has a block entity");
		helper.succeed();
	}

	// ---- the numbers, pure -----------------------------------------------------------------

	/** The field is {@code sum x 25 sum / 40000}: 6, 25, 56, 100 for one to four stabilizers at full, and zero for none. */
	@GameTest(template = EMPTY)
	public static void theRestrictionFieldIsQuadraticInTheStabilizers(GameTestHelper helper) {

		int[][] cases = {
				{ 0, 0, 0, 0, 0 }, { 100, 0, 0, 0, 6 }, { 100, 100, 0, 0, 25 }, { 100, 100, 100, 0, 56 }, { 100, 100, 100, 100, 100 },
				{ 50, 50, 50, 50, 25 }, { 90, 90, 90, 90, 81 }, { 100, 0, 100, 0, 25 }, { 30, 70, 0, 100, 25 } };

		for(int[] c : cases) {
			int field = Math.round(AMSBaseTE.calcField(c[0], c[1], c[2], c[3]));
			helper.assertTrue(field == c[4], "stabilizers " + c[0] + "/" + c[1] + "/" + c[2] + "/" + c[3] + " give a field of " + field + ", expected " + c[4]);
		}

		// The float is not rounded until the machine does it: 100 at one stabilizer is 6.25.
		helper.assertTrue(AMSBaseTE.calcField(100, 0, 0, 0) == 6.25F, "one full stabilizer is 6.25 before rounding");
		helper.succeed();
	}

	/**
	 * Deuterium 50, tritium 75 and nothing else - unless the addon's fluid traits are switched on, which they are not
	 * by default, in which case every DFC fuel burns at {@code (int) (modifier x 49.6794871795)}.
	 */
	@GameTest(template = EMPTY)
	public static void theFuelPowersAreUpstreams(GameTestHelper helper) {

		Object[][] always = { { Fluids.DEUTERIUM, 50 }, { Fluids.TRITIUM, 75 }, { Fluids.WATER, 0 }, { Fluids.OIL, 0 },
				{ Fluids.COOLANT, 0 }, { Fluids.NONE, 0 }, { Fluids.LAVA, 0 } };
		for(Object[] c : always) {
			int power = AMSBaseTE.getFuelPower((FluidType) c[0]);
			helper.assertTrue(power == (int) c[1], ((FluidType) c[0]).getName() + " burns at " + power + ", expected " + c[1]);
			helper.assertTrue(AMSBaseTE.isValidFuel((FluidType) c[0]) == ((int) c[1] > 0), ((FluidType) c[0]).getName() + " is " + (power > 0 ? "" : "not ") + "a fuel");
		}
		helper.assertTrue(AMSBaseTE.getFuelPower(null) == 0, "no fluid is no fuel");

		// The addon's table, which the base reads only when the trait is on the fluid.
		Object[][] traited = { { Fluids.HYDROGEN, 49 }, { Fluids.OXYGEN, 54 }, { Fluids.UF6, 64 }, { Fluids.NITAN, 79 },
				{ Fluids.AMAT, 109 }, { Fluids.BALEFIRE, 119 }, { Fluids.ASCHRAB, 124 }, { Fluids.STELLAR_FLUX, 131 } };
		for(Object[] c : traited) {
			int power = AMSBaseTE.getFuelPower((FluidType) c[0]);
			int expected = LeafiaFeatures.fluidTraits ? (int) c[1] : 0;
			helper.assertTrue(power == expected, ((FluidType) c[0]).getName() + " burns at " + power + ", expected " + expected
					+ " with the fluid traits " + (LeafiaFeatures.fluidTraits ? "on" : "off"));
		}

		// The formula itself, worked out in double: modifier (a float) x 49.6794871795, truncated.
		float[] modifiers = { 1.0F, 1.1F, 1.3F, 1.4F, 1.6F, 2.2F, 2.4F, 2.5F, 2.65F };
		int[] expected = { 49, 54, 64, 69, 79, 109, 119, 124, 131 };
		for(int i = 0; i < modifiers.length; i++)
			helper.assertTrue(AMSBaseTE.dfcFuelPower(modifiers[i]) == expected[i], modifiers[i] + " burns at " + AMSBaseTE.dfcFuelPower(modifiers[i]) + ", expected " + expected[i]);

		// Deuterium and tritium carry the trait too, at 1.2 and 1.3, and are answered before it: 59 and 64 are not what they burn at.
		helper.assertTrue(AMSBaseTE.dfcFuelPower(1.2F) == 59, "deuterium's trait would have said 59");
		helper.succeed();
	}

	/** Water, oil, coolant and cryogel are coolants; the strengths are 5, 15, heat/250 and 25 (5 when there is no heat). */
	@GameTest(template = BIG)
	public static void theCoolantsAndTheirStrengthsAreUpstreams(GameTestHelper helper) {

		helper.assertTrue(AMSBaseTE.isValidCoolant(Fluids.WATER) && AMSBaseTE.isValidCoolant(Fluids.OIL)
				&& AMSBaseTE.isValidCoolant(Fluids.COOLANT) && AMSBaseTE.isValidCoolant(Fluids.CRYOGEL), "the four coolants");
		helper.assertTrue(!AMSBaseTE.isValidCoolant(Fluids.LAVA) && !AMSBaseTE.isValidCoolant(Fluids.HYDROGEN) && !AMSBaseTE.isValidCoolant(Fluids.NONE),
				"nothing else is");

		AMSBaseTE te = base(helper, new BlockPos(8, 1, 8));
		te.heat = 3000;
		helper.assertTrue(te.getCoolingStrength(Fluids.WATER) == 5, "water");
		helper.assertTrue(te.getCoolingStrength(Fluids.OIL) == 15, "oil");
		helper.assertTrue(te.getCoolingStrength(Fluids.COOLANT) == 12, "coolant is heat / 250 = 12 at 3000");
		helper.assertTrue(te.getCoolingStrength(Fluids.CRYOGEL) == 25, "cryogel");
		helper.assertTrue(te.getCoolingStrength(Fluids.LAVA) == 0 && te.getCoolingStrength(null) == 0, "anything else is zero");
		te.heat = 249;
		helper.assertTrue(te.getCoolingStrength(Fluids.COOLANT) == 0, "coolant is integer division");
		te.heat = 0;
		helper.assertTrue(te.getCoolingStrength(Fluids.CRYOGEL) == 5, "cryogel's test is heat > heat / 2, false at zero");
		te.heat = 1;
		helper.assertTrue(te.getCoolingStrength(Fluids.CRYOGEL) == 25, "and true at one");
		helper.succeed();
	}

	/** The core's colour is each channel averaged with integer halving, pairwise. */
	@GameTest(template = EMPTY)
	public static void theCoreColourIsAnIntegerAverage(GameTestHelper helper) {

		helper.assertTrue(AMSBaseTE.calcAvgHex(0xFF0000, 0x0000FF) == 0x7F007F, "red and blue");
		helper.assertTrue(AMSBaseTE.calcAvgHex(0x010101, 0x020202) == 0x010101, "1 and 2 average to 1");
		helper.assertTrue(AMSBaseTE.calcAvgHex(0xFFFFFF, 0xFFFFFF) == 0xFFFFFF, "white");
		helper.assertTrue(AMSBaseTE.calcAvgHex(0x000000, 0xFFFFFF) == 0x7F7F7F, "black and white");
		// Four iron catalysts are all 0xFF7E22, so the average of identical colours is itself.
		int iron = 0xFF7E22;
		helper.assertTrue(AMSBaseTE.calcAvgHex(AMSBaseTE.calcAvgHex(iron, iron), AMSBaseTE.calcAvgHex(iron, iron)) == iron, "four identical catalysts");
		helper.succeed();
	}

	/**
	 * The two lines that make power and heat, in float: a 100,000,000 buffer gains 2,115.7 and lands on the
	 * nearest multiple of 8; a zero field divides by zero and sets the heat to the most an int holds.
	 */
	@GameTest(template = EMPTY)
	public static void thePowerAndHeatAdditionsAreUpstreamsFloatArithmetic(GameTestHelper helper) {

		// 1000 x 1 x 0.5641896 / 1000 x 50 x 75 = 2115.71, and the sum is a float.
		helper.assertTrue(AMSBaseTE.generate(0, 1000L, 1F, 0, 50, 75) == 2115L, "0 + 2115.71 is 2115, was " + AMSBaseTE.generate(0, 1000L, 1F, 0, 50, 75));
		long big = AMSBaseTE.generate(100_000_000L, 1000L, 1F, 0, 50, 75);
		helper.assertTrue(big == 100_002_112L, "float spacing at 1e8 is 8, so 100000000 + 2115.71 is 100002112, was " + big);
		// Heat does nothing: the bell is fed an int division that is 0 anywhere from 0 to 7499.
		helper.assertTrue(AMSBaseTE.generate(0, 1000L, 1F, 5000, 50, 75) == 2115L, "heat 5000 makes the same power");
		helper.assertTrue(AMSBaseTE.generate(0, 1000L, 1F, 7499, 50, 75) == 2115L, "heat 7499 too");
		helper.assertTrue(AMSBaseTE.generate(0, 1000L, 1F, 7500, 50, 75) < 2115L, "and at 7500 the integer quotient is 1 and the bell finally falls");
		helper.assertTrue(AMSBaseTE.gauss(1, 0) > 0.56418F && AMSBaseTE.gauss(1, 0) < 0.56420F, "the peak is 1 / sqrt(pi)");

		// field 25: 3111 x 1 / 0.25 = 12444.
		helper.assertTrue(AMSBaseTE.heatAfter(0, 3111, 1F, 25) == 12444, "3111 / 0.25");
		helper.assertTrue(AMSBaseTE.heatAfter(100, 3111, 1F, 100) == 3211, "a field of 100 divides by one");
		helper.assertTrue(AMSBaseTE.heatAfter(0, 0, 1F, 100) == 0, "no base, no heat");
		helper.assertTrue(AMSBaseTE.heatAfter(0, 3111, 1F, 0) == Integer.MAX_VALUE, "no field: infinity, cast to int");
		helper.assertTrue(AMSBaseTE.heatAfter(0, 0, 1F, 0) == 0, "no field and no base: NaN, cast to int, is zero");
		helper.succeed();
	}

	/** The bases for each core: power x 2,000,000 x efficiency, sqrt(heat) x 220, fuel x 100/15, each shaved by every booster. */
	@GameTest(template = EMPTY)
	public static void theBurnBasesAreUpstreams(GameTestHelper helper) {

		ItemStack sing = new ItemStack(ModItems.ams_core_sing.get());
		ItemStack wormhole = new ItemStack(ModItems.ams_core_wormhole.get());
		ItemStack eye = new ItemStack(ModItems.ams_core_eyeofharmony.get());
		ItemStack thingy = new ItemStack(ModItems.ams_core_thingy.get());

		AMSBaseTE.Burn b = AMSBaseTE.burnFor(sing, 100, 0, 100);
		helper.assertTrue(b.powerBase() == 200_000_000_000_000_000L, "sing power " + b.powerBase());
		helper.assertTrue(b.heatBase() == 0, "a full field leaves no heat: " + b.heatBase());
		helper.assertTrue(b.fuelBase() == 66, "sing fuel 10 x 6.667 = 66.67, truncated: " + b.fuelBase());

		b = AMSBaseTE.burnFor(sing, 1, 0, 0);
		helper.assertTrue(b.heatBase() == 311_100, "sqrt(200) x 220 = 3111, x 100 = " + b.heatBase());

		helper.assertTrue(AMSBaseTE.burnFor(wormhole, 1, 0, 75).heatBase() == 3111 * 25, "wormhole heats like sing, 3111");
		helper.assertTrue(AMSBaseTE.burnFor(eye, 1, 0, 50).heatBase() == 3810 * 50, "eye of harmony sqrt(300) x 220 = 3810");
		helper.assertTrue(AMSBaseTE.burnFor(thingy, 1, 0, 0).heatBase() == 347_800, "thingy sqrt(250) x 220 = 3478");
		helper.assertTrue(AMSBaseTE.burnFor(wormhole, 1, 0, 0).fuelBase() == 100, "wormhole 15 x 6.667 = 100");
		helper.assertTrue(AMSBaseTE.burnFor(thingy, 1, 0, 0).fuelBase() == 33, "thingy 5 x 6.667 = 33");
		helper.assertTrue(AMSBaseTE.burnFor(thingy, 1, 0, 0).powerBase() == 10_000_000_000_000_000L, "thingy 5e9 x 2e6");

		// One booster: power x 1.25 exactly, heat 3111 x 1.25 = 3888.75 truncated to 3888 and then x 100.
		helper.assertTrue(AMSBaseTE.burnFor(eye, 4, 1, 100).powerBase() == 25_000_000_000_000_000L,
				"one booster is x 1.25 on 2.5e9 x 2e6 x 4 = 2e16: " + AMSBaseTE.burnFor(eye, 4, 1, 100).powerBase());
		helper.assertTrue(AMSBaseTE.burnFor(sing, 1, 1, 0).heatBase() == 388_800, "3111 x 1.25 = 3888, x 100");
		helper.assertTrue(AMSBaseTE.burnFor(sing, 1, 2, 0).heatBase() == 486_000, "3111 x 1.5625 = 4860.9, truncated, x 100");
		helper.assertTrue(AMSBaseTE.burnFor(sing, 1, 4, 0).heatBase() == 759_500, "3111 x 2.44140625 = 7595.2, truncated, x 100");
		helper.assertTrue(AMSBaseTE.burnFor(null, 100, 0, 100).powerBase() == 0, "no core is no power");
		helper.succeed();
	}

	/**
	 * Upstream's bell: 100% at half heat and a full buffer, falling symmetrically to 90 at 250 off, 41 at
	 * 750 and 8 at the ends; the emitter's buffer is 100,000,000 and the stabilizer's is 10,000,000, and
	 * the curve is normalised to each, so a full buffer reads the same in both. Worked out in float32.
	 */
	@GameTest(template = EMPTY)
	public static void theEfficiencyBellIsUpstreams(GameTestHelper helper) {

		int[][] curve = { { 1250, 100 }, { 1000, 90 }, { 1500, 90 }, { 500, 41 }, { 2000, 41 }, { 100, 12 }, { 2400, 12 }, { 0, 8 }, { 2500, 8 } };
		for(int[] c : curve) {
			int emitter = Math.round(AMSEmitterTE.calcEffect(AMSEmitterTE.maxPower, c[0] - (AMSEmitterTE.maxHeat / 2)) * 100);
			int stabilizer = Math.round(AMSStabilizerTE.calcEffect(AMSStabilizerTE.maxPower, c[0] - (AMSStabilizerTE.maxHeat / 2)) * 100);
			helper.assertTrue(emitter == c[1], "the emitter at heat " + c[0] + " is " + emitter + "%, expected " + c[1]);
			helper.assertTrue(stabilizer == c[1], "the stabilizer at heat " + c[0] + " is " + stabilizer + "%, expected " + c[1]);
		}

		// A half-empty buffer is weaker: sqrt(buffer) is in the denominator of the amplitude.
		helper.assertTrue(Math.round(AMSEmitterTE.calcEffect(50_000_000F, 0) * 100) == 71, "half a buffer is 71%");
		helper.assertTrue(Math.round(AMSEmitterTE.calcEffect(1F, 0) * 100) == 0, "one HE is nothing");
		helper.assertTrue(Math.round(AMSStabilizerTE.calcEffect(5_000_000F, 0) * 100) == 71, "the stabilizer's half buffer too");
		helper.succeed();
	}

	// ---- the emitter -----------------------------------------------------------------------

	private static void fuelEmitter(AMSEmitterTE te, FluidType type, int fill, int heat, long power, boolean muzzle) {
		te.tank.setTankType(type);
		te.tank.setFill(fill);
		te.heat = heat;
		te.power = power;
		te.slots[AMSEmitterTE.SLOT_MUZZLE] = muzzle ? new ItemStack(LeafiaItems.ams_muzzle.get()) : ItemStack.EMPTY;
	}

	/**
	 * A full buffer at heat 1000 with coolant: 90% efficiency, 2.5% of the buffer gone, 15 mB drunk, and the
	 * heat knocked down five by the coolant's cooling loop (it is above a quarter of the limit).
	 */
	@GameTest(template = BIG)
	public static void anEmitterOnCoolantRunsAtUpstreamsNumbers(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);
		fuelEmitter(te, Fluids.COOLANT, 16000, 1000, AMSEmitterTE.maxPower, true);
		te.updateEntity();

		helper.assertTrue(te.efficiency == 90, "efficiency " + te.efficiency);
		helper.assertTrue(te.power == 97_500_000L, "power " + te.power + " (a quarter of one tenth, rounded up)");
		helper.assertTrue(te.tank.getFill() == 15985, "tank " + te.tank.getFill());
		helper.assertTrue(te.heat == 995, "heat " + te.heat);
		helper.assertTrue(te.warning == 0 && !te.locked, "warning " + te.warning);
		helper.succeed();
	}

	/** Each coolant's draw and its heat loop: cryogel 15 mB and ten off above 1250, coolant 15 and five above 625, water 45 and two above 2125. */
	@GameTest(template = BIG)
	public static void eachEmitterCoolantCostsAndCoolsAtItsOwnRate(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);

		// Cryogel at 1500: efficiency 90, no warming (above half), ten cooling steps.
		fuelEmitter(te, Fluids.CRYOGEL, 1000, 1500, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 985 && te.heat == 1490, "cryogel: tank " + te.tank.getFill() + ", heat " + te.heat);

		// Cryogel at 1000: below half and generating, so it gains the efficiency (90) and loses nothing to the loop.
		fuelEmitter(te, Fluids.CRYOGEL, 1000, 1000, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 985 && te.heat == 1090, "cryogel below half: tank " + te.tank.getFill() + ", heat " + te.heat);

		// Water at 1000: 45 mB, efficiency 90 added, then no loop (below 2125).
		fuelEmitter(te, Fluids.WATER, 1000, 1000, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 955 && te.heat == 1090, "water: tank " + te.tank.getFill() + ", heat " + te.heat);

		// Water at 2400: 45 mB; efficiency 12 is not added because 2400 is above 2125, and the loop removes two.
		fuelEmitter(te, Fluids.WATER, 1000, 2400, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 955 && te.heat == 2398, "water above 2125: tank " + te.tank.getFill() + ", heat " + te.heat);

		// Coolant with no power: the unpowered branch cools by five a tick from above 625, and warning 1.
		fuelEmitter(te, Fluids.COOLANT, 1000, 1000, 0, true);
		te.updateEntity();
		helper.assertTrue(te.efficiency == 0 && te.warning == 1 && te.heat == 995, "unpowered: efficiency " + te.efficiency + ", warning " + te.warning + ", heat " + te.heat);

		// And below the threshold it sheds heat by itself: five steps of one.
		fuelEmitter(te, Fluids.COOLANT, 1000, 600, 0, true);
		te.updateEntity();
		helper.assertTrue(te.heat == 595, "unpowered and below a quarter: " + te.heat);

		// With no heat there is nothing to drink: the tank is untouched.
		fuelEmitter(te, Fluids.COOLANT, 1000, 0, 0, true);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 1000 && te.heat == 0, "no heat, no draw: " + te.tank.getFill());
		helper.succeed();
	}

	/**
	 * Without a muzzle the efficiency is forced to zero and the warning is 2 - but the heat has already
	 * gained the efficiency, which upstream does not take back.
	 */
	@GameTest(template = BIG)
	public static void anEmitterWithoutAMuzzleHeatsAndThenZeroesItsEfficiency(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);
		fuelEmitter(te, Fluids.COOLANT, 16000, 100, AMSEmitterTE.maxPower, false);
		te.updateEntity();
		helper.assertTrue(te.efficiency == 0, "efficiency " + te.efficiency);
		helper.assertTrue(te.warning == 2, "warning " + te.warning);
		helper.assertTrue(te.heat == 100 + 12, "the 12% of heat 100 is added before the muzzle is checked: " + te.heat);

		// Another item in the slot is as good as none.
		fuelEmitter(te, Fluids.COOLANT, 16000, 100, AMSEmitterTE.maxPower, false);
		te.slots[AMSEmitterTE.SLOT_MUZZLE] = new ItemStack(Items.STICK);
		te.updateEntity();
		helper.assertTrue(te.efficiency == 0 && te.warning == 2, "a stick is not a muzzle");

		// The focus of the stabilizer is not one either.
		fuelEmitter(te, Fluids.COOLANT, 16000, 100, AMSEmitterTE.maxPower, false);
		te.slots[AMSEmitterTE.SLOT_MUZZLE] = new ItemStack(LeafiaItems.ams_focus_limiter.get());
		te.updateEntity();
		helper.assertTrue(te.efficiency == 0 && te.warning == 2, "a focus is not a muzzle");
		helper.succeed();
	}

	/** Warning 2 for an empty tank, a tank of something else, or heat above 90%. */
	@GameTest(template = BIG)
	public static void anEmittersWarningsOverwriteInUpstreamsOrder(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);

		fuelEmitter(te, Fluids.COOLANT, 5, 1250, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.warning == 2, "a tank at 5 mB or less warns: " + te.warning);

		fuelEmitter(te, Fluids.COOLANT, 16000, 2300, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.warning == 2, "heat over 90% (2250) warns: " + te.warning);

		fuelEmitter(te, Fluids.OIL, 16000, 1250, AMSEmitterTE.maxPower, true);
		te.updateEntity();
		helper.assertTrue(te.warning == 2 && te.heat == 1250 + 100, "oil is no coolant: it warns and the heat adds: warning " + te.warning + ", heat " + te.heat);
		helper.assertTrue(te.tank.getFill() == 16000, "and nothing is drunk");

		// One HE of power is still "powered": the efficiency rounds to zero, the buffer is emptied by the
		// ceiling of 2.5%, and the warning is 0, not 1.
		fuelEmitter(te, Fluids.COOLANT, 16000, 1250, 1, true);
		te.updateEntity();
		helper.assertTrue(te.efficiency == 0 && te.power == 0 && te.warning == 0,
				"one HE: efficiency " + te.efficiency + ", power " + te.power + ", warning " + te.warning);
		helper.succeed();
	}

	/** Over 2,500 the emitter locks: heat clamped, then every tick after it zeroes power and efficiency and warns 3. */
	@GameTest(template = BIG)
	public static void anEmitterOverTwoThousandFiveHundredBreaksForGood(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);
		fuelEmitter(te, Fluids.COOLANT, 0, 2500, AMSEmitterTE.maxPower, true);
		te.updateEntity();

		helper.assertTrue(te.locked, "the emitter did not lock at heat " + te.heat);
		helper.assertTrue(te.heat == 2500, "heat is clamped to " + te.heat);

		te.power = 5000;
		te.updateEntity();
		helper.assertTrue(te.locked && te.efficiency == 0 && te.power == 0 && te.warning == 3,
				"locked: efficiency " + te.efficiency + ", power " + te.power + ", warning " + te.warning);

		// Refilling it does not help.
		te.tank.setFill(16000);
		te.heat = 0;
		te.power = 1_000_000;
		te.updateEntity();
		helper.assertTrue(te.locked && te.power == 0, "a locked emitter is locked for good");
		helper.succeed();
	}

	/** A battery in slot 3 charges the buffer; a creative one fills it. */
	@GameTest(template = BIG)
	public static void anEmitterChargesFromABattery(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);
		fuelEmitter(te, Fluids.COOLANT, 16000, 1250, 0, true);
		te.slots[AMSEmitterTE.SLOT_BATTERY] = new ItemStack(ModItems.battery_creative.get());
		te.updateEntity();
		helper.assertTrue(te.power == AMSEmitterTE.maxPower, "a creative battery fills the buffer: " + te.power);
		helper.succeed();
	}

	/** An identifier in the fluid slot retypes the tank to cryogel, coolant or water, moves to the out slot, and empties the tank; oil is refused. */
	@GameTest(template = BIG)
	public static void theEmittersIdentifierSlotOnlyTakesTheThreeCoolants(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);

		fuelEmitter(te, Fluids.COOLANT, 5000, 0, 0, true);
		te.slots[0] = identifier(Fluids.WATER);
		te.updateEntity();
		helper.assertTrue(te.tank.getTankType() == Fluids.WATER, "the tank is " + te.tank.getTankType().getName());
		helper.assertTrue(te.slots[1].getItem() == LeafiaItems.fuzzy_identifier.get() && te.slots[0].isEmpty(), "the identifier moved to the out slot");
		helper.assertTrue(te.tank.getFill() == 0, "a retyped tank is empty");

		fuelEmitter(te, Fluids.COOLANT, 5000, 0, 0, true);
		te.slots[0] = identifier(Fluids.OIL);
		te.slots[1] = ItemStack.EMPTY;
		te.updateEntity();
		helper.assertTrue(te.tank.getTankType() == Fluids.COOLANT && !te.slots[0].isEmpty() && te.tank.getFill() >= 4900,
				"oil is refused and the identifier stays");

		// The same type again: neither consumed nor moved.
		fuelEmitter(te, Fluids.CRYOGEL, 5000, 0, 0, true);
		te.slots[0] = identifier(Fluids.CRYOGEL);
		te.slots[1] = ItemStack.EMPTY;
		te.updateEntity();
		helper.assertTrue(!te.slots[0].isEmpty() && te.slots[1].isEmpty(), "an identifier for the type it already has stays put");

		// A full out slot blocks it.
		fuelEmitter(te, Fluids.COOLANT, 5000, 0, 0, true);
		te.slots[0] = identifier(Fluids.WATER);
		te.slots[1] = new ItemStack(Items.STICK);
		te.updateEntity();
		helper.assertTrue(te.tank.getTankType() == Fluids.COOLANT, "a taken out slot blocks the retype");
		helper.succeed();
	}

	/** The emitter's ports: five, on top, all facing up; its tank is the one receiving tank. */
	@GameTest(template = BIG)
	public static void theEmittersPortsAreTheCrossOnTop(GameTestHelper helper) {

		AMSEmitterTE te = loneEmitter(helper);
		DirPos[] ports = te.getConPos();
		BlockPos top = te.getBlockPos().above(6);

		helper.assertTrue(ports.length == 5, "five ports");
		List<BlockPos> want = List.of(top, top.north(), top.south(), top.east(), top.west());
		for(DirPos port : ports) {
			helper.assertTrue(port.getDir() == Direction.UP, "a port faces " + port.getDir());
			helper.assertTrue(want.contains(new BlockPos(port.getX(), port.getY(), port.getZ())), "an unexpected port at " + port);
		}
		helper.assertTrue(te.getReceivingTanks().length == 1 && te.getReceivingTanks()[0] == te.tank, "the tank receives");
		helper.assertTrue(te.getDemand(Fluids.COOLANT, 0) == 16000 && te.getDemand(Fluids.LAVA, 0) == 0, "it demands what it holds, of its own fluid");
		helper.assertTrue(te.getMaxPower() == 100_000_000L, "a buffer of 100,000,000");
		helper.succeed();
	}

	// ---- the stabilizer --------------------------------------------------------------------

	private static void fuelStabilizer(AMSStabilizerTE te, FluidType type, int fill, int heat, long power, ItemStack focus) {
		te.tank.setTankType(type);
		te.tank.setFill(fill);
		te.heat = heat;
		te.power = power;
		te.slots[AMSStabilizerTE.SLOT_FOCUS] = focus;
	}

	/** Cryogel and coolant cost 5 mB a tick here, water 15, and the same loops as the emitter's. */
	@GameTest(template = BIG)
	public static void eachStabilizerCoolantCostsLessThanTheEmitters(GameTestHelper helper) {

		AMSStabilizerTE te = loneStabilizer(helper);
		ItemStack limiter = new ItemStack(LeafiaItems.ams_focus_limiter.get());

		fuelStabilizer(te, Fluids.CRYOGEL, 1000, 1500, AMSStabilizerTE.maxPower, limiter);
		te.updateEntity();
		helper.assertTrue(te.efficiency == 90 && te.tank.getFill() == 995 && te.heat == 1490,
				"cryogel: efficiency " + te.efficiency + ", tank " + te.tank.getFill() + ", heat " + te.heat);
		helper.assertTrue(te.power == 9_750_000L, "a tenth of 2.5% of 10,000,000 is gone: " + te.power);

		fuelStabilizer(te, Fluids.COOLANT, 1000, 1000, AMSStabilizerTE.maxPower, limiter);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 995 && te.heat == 995, "coolant: tank " + te.tank.getFill() + ", heat " + te.heat);

		fuelStabilizer(te, Fluids.WATER, 1000, 1000, AMSStabilizerTE.maxPower, limiter);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 985 && te.heat == 1090, "water: tank " + te.tank.getFill() + ", heat " + te.heat);

		// Water below 15 mB is not drunk and the heat adds.
		fuelStabilizer(te, Fluids.WATER, 14, 1000, AMSStabilizerTE.maxPower, limiter);
		te.updateEntity();
		helper.assertTrue(te.tank.getFill() == 14 && te.heat == 1090 && te.warning == 0, "short of water: tank " + te.tank.getFill() + ", heat " + te.heat + ", warning " + te.warning);
		helper.succeed();
	}

	/** A limiting focus is mode 1, a boosting focus mode 2; anything else, or nothing, zeroes the efficiency and warns 2. */
	@GameTest(template = BIG)
	public static void aStabilizersFocusDecidesItsMode(GameTestHelper helper) {

		AMSStabilizerTE te = loneStabilizer(helper);

		fuelStabilizer(te, Fluids.COOLANT, 8000, 1250, AMSStabilizerTE.maxPower, new ItemStack(LeafiaItems.ams_focus_limiter.get()));
		te.updateEntity();
		helper.assertTrue(te.mode == 1 && te.efficiency == 100 && te.warning == 0, "limiter: mode " + te.mode + ", efficiency " + te.efficiency + ", warning " + te.warning);

		fuelStabilizer(te, Fluids.COOLANT, 8000, 1250, AMSStabilizerTE.maxPower, new ItemStack(LeafiaItems.ams_focus_booster.get()));
		te.updateEntity();
		helper.assertTrue(te.mode == 2 && te.efficiency == 100, "booster: mode " + te.mode);

		fuelStabilizer(te, Fluids.COOLANT, 8000, 1250, AMSStabilizerTE.maxPower, new ItemStack(Items.STICK));
		te.updateEntity();
		helper.assertTrue(te.mode == 0 && te.efficiency == 0 && te.warning == 2, "a stick: mode " + te.mode + ", efficiency " + te.efficiency + ", warning " + te.warning);

		fuelStabilizer(te, Fluids.COOLANT, 8000, 1250, AMSStabilizerTE.maxPower, ItemStack.EMPTY);
		te.updateEntity();
		helper.assertTrue(te.mode == 0 && te.efficiency == 0 && te.warning == 2, "empty: mode " + te.mode);

		// The emitter's muzzle is not a focus.
		fuelStabilizer(te, Fluids.COOLANT, 8000, 1250, AMSStabilizerTE.maxPower, new ItemStack(LeafiaItems.ams_muzzle.get()));
		te.updateEntity();
		helper.assertTrue(te.mode == 0 && te.efficiency == 0, "a muzzle is not a focus");
		helper.succeed();
	}

	/** A stabilizer locks the same way, and is then dead to the base. */
	@GameTest(template = BIG)
	public static void aStabilizerOverTwoThousandFiveHundredLocks(GameTestHelper helper) {

		AMSStabilizerTE te = loneStabilizer(helper);
		fuelStabilizer(te, Fluids.COOLANT, 0, 2500, AMSStabilizerTE.maxPower, new ItemStack(LeafiaItems.ams_focus_limiter.get()));
		te.updateEntity();
		helper.assertTrue(te.locked && te.heat == 2500, "locked at " + te.heat);

		te.updateEntity();
		helper.assertTrue(te.efficiency == 0 && te.power == 0 && te.warning == 3, "then dead: warning " + te.warning);
		helper.succeed();
	}

	/** The identifier slot takes water, coolant and cryogel only. */
	@GameTest(template = BIG)
	public static void theStabilizersIdentifierSlotOnlyTakesTheThreeCoolants(GameTestHelper helper) {

		AMSStabilizerTE te = loneStabilizer(helper);

		fuelStabilizer(te, Fluids.COOLANT, 100, 0, 0, ItemStack.EMPTY);
		te.slots[0] = identifier(Fluids.CRYOGEL);
		te.updateEntity();
		helper.assertTrue(te.tank.getTankType() == Fluids.CRYOGEL && te.slots[0].isEmpty() && !te.slots[1].isEmpty(), "cryogel is taken");

		fuelStabilizer(te, Fluids.COOLANT, 100, 0, 0, ItemStack.EMPTY);
		te.slots[0] = identifier(Fluids.OIL);
		te.slots[1] = ItemStack.EMPTY;
		te.updateEntity();
		helper.assertTrue(te.tank.getTankType() == Fluids.COOLANT, "oil is refused");
		helper.assertTrue(te.isValidFluid(Fluids.WATER) && !te.isValidFluid(Fluids.OIL) && !te.isValidFluid(null), "isValidFluid");
		helper.succeed();
	}

	/**
	 * Upstream's quarter-turn: {@code rotateShitfuck - 10} is the clockwise neighbour's index. North reads 5,
	 * east 3, south 4, west 2, and nothing else is reachable.
	 */
	@GameTest(template = EMPTY)
	public static void rotateShitfuckIsTheClockwiseIndex(GameTestHelper helper) {

		helper.assertTrue(AMSStabilizerTE.rotateShitfuck(Direction.NORTH) - 10 == 5, "north");
		helper.assertTrue(AMSStabilizerTE.rotateShitfuck(Direction.EAST) - 10 == 3, "east");
		helper.assertTrue(AMSStabilizerTE.rotateShitfuck(Direction.SOUTH) - 10 == 4, "south");
		helper.assertTrue(AMSStabilizerTE.rotateShitfuck(Direction.WEST) - 10 == 2, "west");
		helper.succeed();
	}

	/** Eight ports: front and back of the core, front and back of each end of the wall, and one beyond each end. */
	@GameTest(template = BIG)
	public static void theStabilizersPortsAreEightAroundTheWall(GameTestHelper helper) {

		for(Direction dir : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {

			AMSStabilizerTE te = stabilizer(helper, new BlockPos(8, 0, 8), dir);
			Direction side = dir.getClockWise();
			BlockPos pos = te.getBlockPos();
			DirPos[] ports = te.getConPos();
			helper.assertTrue(ports.length == 8, "eight ports");

			List<String> got = new ArrayList<>();
			for(DirPos p : ports) got.add(new BlockPos(p.getX(), p.getY(), p.getZ()).subtract(pos) + ":" + p.getDir());

			List<String> want = new ArrayList<>();
			want.add(BlockPos.ZERO.relative(dir) + ":" + dir);
			want.add(BlockPos.ZERO.relative(dir.getOpposite()) + ":" + dir.getOpposite());
			want.add(BlockPos.ZERO.relative(side, 2).relative(dir) + ":" + dir);
			want.add(BlockPos.ZERO.relative(side, 2).relative(dir.getOpposite()) + ":" + dir.getOpposite());
			want.add(BlockPos.ZERO.relative(side, 3) + ":" + side);
			want.add(BlockPos.ZERO.relative(side, -2).relative(dir) + ":" + dir);
			want.add(BlockPos.ZERO.relative(side, -2).relative(dir.getOpposite()) + ":" + dir.getOpposite());
			want.add(BlockPos.ZERO.relative(side, -3) + ":" + side.getOpposite());

			helper.assertTrue(got.equals(want), dir + ": ports " + got + ", expected " + want);

			// Clear it for the next facing.
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(8, 0, 8)), Blocks.AIR.defaultBlockState(), 3);
			for(int x = -3; x <= 3; x++) for(int y = 0; y <= 5; y++) for(int z = -3; z <= 3; z++) {
				BlockPos here = helper.absolutePos(new BlockPos(8 + x, y, 8 + z));
				if(helper.getLevel().getBlockState(here).getBlock() == LeafiaBlocks.ams_limiter.get()) helper.getLevel().setBlock(here, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		helper.succeed();
	}

	// ---- the base --------------------------------------------------------------------------

	/** Five ports: below the core, and two out on each side. */
	@GameTest(template = BIG)
	public static void theBasesPortsAreBelowAndTwoOutOnEachSide(GameTestHelper helper) {

		AMSBaseTE te = base(helper, new BlockPos(8, 1, 8));
		BlockPos pos = te.getBlockPos();
		DirPos[] ports = te.getConPos();

		helper.assertTrue(ports.length == 5, "five ports");
		helper.assertTrue(new BlockPos(ports[0].getX(), ports[0].getY(), ports[0].getZ()).equals(pos.below()) && ports[0].getDir() == Direction.DOWN, "below");
		Direction[] sides = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
		for(int i = 0; i < 4; i++) {
			BlockPos p = new BlockPos(ports[i + 1].getX(), ports[i + 1].getY(), ports[i + 1].getZ());
			helper.assertTrue(p.equals(pos.relative(sides[i], 2)) && ports[i + 1].getDir() == sides[i], "port " + sides[i]);
		}
		helper.assertTrue(te.getReceivingTanks().length == 4 && te.getAllTanks().length == 4, "four tanks receive");
		helper.assertTrue(te.getMaxPower() == 1_000_000_000_000_000L, "a petaHE buffer");
		helper.succeed();
	}

	/** Four 8,000 mB tanks: coolant, cryogel, deuterium, tritium. */
	@GameTest(template = BIG)
	public static void theBaseStartsWithUpstreamsFourTanks(GameTestHelper helper) {

		AMSBaseTE te = base(helper, new BlockPos(8, 1, 8));
		FluidType[] types = { Fluids.COOLANT, Fluids.CRYOGEL, Fluids.DEUTERIUM, Fluids.TRITIUM };
		for(int i = 0; i < 4; i++) {
			helper.assertTrue(te.tanks[i].getTankType() == types[i], "tank " + i + " is " + te.tanks[i].getTankType().getName());
			helper.assertTrue(te.tanks[i].getMaxFill() == 8000 && te.tanks[i].getFill() == 0, "tank " + i + " holds " + te.tanks[i].getFill() + "/" + te.tanks[i].getMaxFill());
		}
		helper.assertTrue(te.slots.length == 16, "sixteen slots");
		helper.succeed();
	}

	/**
	 * Identifiers retype the base's tanks: a fuel into a fuel slot (and not a coolant), a coolant into a
	 * coolant slot (and not the one the other tank already is), each moving to the paired out slot, and
	 * neither tank of a pair becoming what the other already is. Deuterium and tritium are the only fuels
	 * with the fluid traits off, so they are swapped between the two fuel tanks.
	 */
	@GameTest(template = BIG)
	public static void theBasesIdentifierSlotsRetypeTheirOwnTanks(GameTestHelper helper) {

		AMSBaseTE te = base(helper, new BlockPos(8, 1, 8));

		// Fuel A is deuterium and fuel B tritium. Tritium cannot go into A while B is tritium.
		te.slots[4] = identifier(Fluids.TRITIUM);
		te.updateEntity();
		helper.assertTrue(te.tanks[2].getTankType() == Fluids.DEUTERIUM && !te.slots[4].isEmpty(), "fuel A refuses what fuel B already is");

		// Free B, and A takes it, the identifier moving to the out slot and the tank emptying.
		te.tanks[3].setTankType(Fluids.NONE);
		te.tanks[2].setFill(500);
		te.updateEntity();
		helper.assertTrue(te.tanks[2].getTankType() == Fluids.TRITIUM && te.slots[4].isEmpty() && !te.slots[5].isEmpty(), "tritium into fuel A");
		helper.assertTrue(te.tanks[2].getFill() == 0, "a retyped tank is empty");

		// B takes deuterium now, but not tritium.
		te.slots[6] = identifier(Fluids.TRITIUM);
		te.updateEntity();
		helper.assertTrue(te.tanks[3].getTankType() == Fluids.NONE && !te.slots[6].isEmpty(), "fuel B refuses what fuel A is now");
		te.slots[6] = identifier(Fluids.DEUTERIUM);
		te.updateEntity();
		helper.assertTrue(te.tanks[3].getTankType() == Fluids.DEUTERIUM && te.slots[6].isEmpty() && !te.slots[7].isEmpty(), "deuterium into fuel B");

		// A coolant is no fuel, and a fuel is no coolant.
		te.slots[4] = identifier(Fluids.WATER);
		te.slots[5] = ItemStack.EMPTY;
		te.slots[2] = identifier(Fluids.DEUTERIUM);
		te.updateEntity();
		helper.assertTrue(te.tanks[2].getTankType() == Fluids.TRITIUM, "water is refused by a fuel tank");
		helper.assertTrue(te.tanks[1].getTankType() == Fluids.CRYOGEL && !te.slots[2].isEmpty(), "deuterium is refused by a coolant tank");

		// Water into coolant A; then coolant B may not become water, and A may not become cryogel.
		te.slots[0] = identifier(Fluids.WATER);
		te.updateEntity();
		helper.assertTrue(te.tanks[0].getTankType() == Fluids.WATER && te.slots[0].isEmpty() && !te.slots[1].isEmpty(), "water into coolant A");
		te.slots[2] = identifier(Fluids.WATER);
		te.slots[3] = ItemStack.EMPTY;
		te.updateEntity();
		helper.assertTrue(te.tanks[1].getTankType() == Fluids.CRYOGEL, "coolant B refuses what coolant A is");
		te.slots[0] = identifier(Fluids.CRYOGEL);
		te.slots[1] = ItemStack.EMPTY;
		te.updateEntity();
		helper.assertTrue(te.tanks[0].getTankType() == Fluids.WATER, "coolant A refuses what coolant B is");

		// Lava is a coolant to nobody; oil is.
		te.slots[0] = identifier(Fluids.LAVA);
		te.updateEntity();
		helper.assertTrue(te.tanks[0].getTankType() == Fluids.WATER, "lava is refused");
		te.slots[0] = identifier(Fluids.OIL);
		te.updateEntity();
		helper.assertTrue(te.tanks[0].getTankType() == Fluids.OIL, "oil is a coolant");
		helper.succeed();
	}

	/** Anything goes in any slot by hand; nothing goes in or out by hopper. */
	@GameTest(template = BIG)
	public static void theBaseTakesAnythingByHandAndNothingByHopper(GameTestHelper helper) {

		AMSBaseTE te = base(helper, new BlockPos(8, 1, 8));
		for(int i = 0; i < 16; i++) helper.assertTrue(te.canPlaceItem(i, new ItemStack(Items.DIRT)), "slot " + i + " refuses dirt");
		for(Direction side : Direction.values()) helper.assertTrue(te.getSlotsForFace(side).length == 0, "a hopper reaches " + side);
		helper.succeed();
	}

	/**
	 * With four stabilizers at full the field is 100, the mode 1, and one tick of generation fills the
	 * buffer, drinks 80 mB from each fuel tank, adds no heat and sets the colour.
	 */
	@GameTest(template = HUGE)
	public static void aWorkingMachineGeneratesAndDrinksAtUpstreamsRates(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		try {
			a.load(98_000);
			a.workingStabilizers(100);
			a.base.updateEntity();

			helper.assertTrue(a.base.field == 100, "field " + a.base.field);
			helper.assertTrue(a.base.mode == 1, "mode " + a.base.mode);
			helper.assertTrue(a.base.efficiency == 100, "efficiency is the emitter's: " + a.base.efficiency);
			helper.assertTrue(a.base.power == AMSBaseTE.maxPower, "one tick is more than the buffer holds: " + a.base.power);
			helper.assertTrue(a.base.heat == 0, "a full field adds no heat: " + a.base.heat);
			helper.assertTrue(a.base.tanks[2].getFill() == 7920 && a.base.tanks[3].getFill() == 7920,
					"four iron catalysts: 66 x 1.2155 = 80 each: " + a.base.tanks[2].getFill() + "/" + a.base.tanks[3].getFill());
			helper.assertTrue(a.base.color == 0xFF7E22, "four iron catalysts average to iron: " + Integer.toHexString(a.base.color));
			helper.assertTrue(a.base.getBlockState().getBlock() == LeafiaBlocks.ams_base.get(), "the core stands");
			helper.assertTrue(a.nukes().isEmpty(), "nothing went off");
		} finally {
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/** Without the chips, a core, four catalysts or an emitter's efficiency, nothing is made and the colour is -1. */
	@GameTest(template = HUGE)
	public static void aMachineMissingAnythingMakesNothing(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		try {
			a.load(98_010);
			a.workingStabilizers(100);

			// Take away each requirement in turn.
			ItemStack[] saved = a.base.slots.clone();
			String[] names = { "a catalyst", "the core", "a chip", "the emitter's efficiency" };
			for(int i = 0; i < 4; i++) {
				a.base.slots = saved.clone();
				a.emitter.efficiency = 100;
				for(FluidTank t : a.base.tanks) t.setFill(8000);
				a.base.power = 0;
				switch(i) {
					case 0 -> a.base.slots[9] = ItemStack.EMPTY;
					case 1 -> a.base.slots[12] = ItemStack.EMPTY;
					case 2 -> a.base.slots[14] = ItemStack.EMPTY;
					default -> a.emitter.efficiency = 0;
				}
				a.base.updateEntity();
				helper.assertTrue(a.base.power == 0 && a.base.color == -1, "without " + names[i] + ": power " + a.base.power + ", colour " + a.base.color);
				helper.assertTrue(a.base.tanks[2].getFill() == 8000, "without " + names[i] + " the fuel is untouched");
			}

			// And the chips must be on three different resonators.
			a.base.slots = saved.clone();
			a.emitter.efficiency = 100;
			ISatChip.setFreqS(a.base.slots[14], a.frequencies.get(0));
			helper.assertTrue(!a.base.hasResonators(), "two chips on one frequency");
			ISatChip.setFreqS(a.base.slots[14], 98_999);
			helper.assertTrue(!a.base.hasResonators(), "a chip on a frequency with no satellite");
			SatelliteSavedData.getData(helper.getLevel()).sats.put(98_999, new com.hbm.saveddata.satellites.SatelliteRadar());
			a.frequencies.add(98_999);
			helper.assertTrue(!a.base.hasResonators(), "a chip on a satellite that is not a resonator");
		} finally {
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/** A tank with no fuel, or fuel that burns for nothing, stops it: nothing is made and nothing explodes. */
	@GameTest(template = HUGE)
	public static void aMachineWithoutFuelIdlesQuietly(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		try {
			a.load(98_020);
			a.workingStabilizers(100);

			a.base.tanks[2].setFill(0);
			a.base.updateEntity();
			helper.assertTrue(a.base.power == 0 && a.nukes().isEmpty(), "an empty fuel tank");

			a.base.tanks[2].setTankType(Fluids.WATER);
			a.base.tanks[2].setFill(100);
			a.base.updateEntity();
			helper.assertTrue(a.base.power == 0 && a.base.tanks[2].getFill() == 100 && a.nukes().isEmpty(), "water is no fuel");
			helper.assertTrue(a.base.color != -1, "the ball is still lit: the colour is set before the fuel is checked");
		} finally {
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/** Only a stabilizer facing the right way, not locked, counts: a wrong one adds nothing, and a boosting one is mode 2. */
	@GameTest(template = HUGE)
	public static void onlyAStabilizerFacingTheBaseCounts(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		try {
			a.load(98_030);
			a.workingStabilizers(100);

			a.base.tanks[2].setFill(0); // do not generate; only read the field
			a.base.updateEntity();
			helper.assertTrue(a.base.field == 100 && a.base.mode == 1, "four working: field " + a.base.field + ", mode " + a.base.mode);

			a.east.locked = true;
			a.base.updateEntity();
			helper.assertTrue(a.base.field == 56 && a.base.mode == 1, "a locked one is out: field " + a.base.field);

			a.east.locked = false;
			a.west.mode = 2;
			a.base.updateEntity();
			helper.assertTrue(a.base.field == 100 && a.base.mode == 2, "a boosting one makes mode 2: " + a.base.mode);

			a.west.mode = 0;
			a.west.efficiency = 0;
			a.south.efficiency = 0;
			a.north.efficiency = 0;
			a.base.updateEntity();
			helper.assertTrue(a.base.field == 6 && a.base.mode == 1, "one working: field " + a.base.field + ", mode " + a.base.mode);

			a.west.efficiency = 100;
			a.west.mode = 2;
			a.base.updateEntity();
			helper.assertTrue(a.base.mode == 2 && a.base.field == 25, "two working, one boosting: mode " + a.base.mode + ", field " + a.base.field);

			// Turn the east stabilizer's wall to face the wrong way and it stops counting: only the west one is left.
			BlockPos eastCore = helper.absolutePos(a.core.east(6));
			a.west.mode = 0;
			helper.getLevel().setBlock(eastCore, helper.getLevel().getBlockState(eastCore).setValue(BlockDummyable.DIR, Direction.NORTH), 3);
			helper.assertTrue(helper.getLevel().getBlockEntity(eastCore) == a.east, "turning it kept the block entity");
			a.base.updateEntity();
			helper.assertTrue(a.base.field == 6, "a stabilizer facing the wrong way adds nothing: " + a.base.field);
		} finally {
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/** The emitter's efficiency is copied only while an emitter stands nine up: with none, the last one stays. */
	@GameTest(template = HUGE)
	public static void aMissingEmitterLeavesTheLastEfficiencyStanding(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		try {
			a.load(98_040);
			a.workingStabilizers(100);
			a.base.tanks[2].setFill(0);
			a.base.updateEntity();
			helper.assertTrue(a.base.efficiency == 100, "copied");

			a.emitter.efficiency = 40;
			a.base.updateEntity();
			helper.assertTrue(a.base.efficiency == 40, "follows the emitter");

			helper.getLevel().setBlock(helper.absolutePos(a.core.above(9)), Blocks.AIR.defaultBlockState(), 3);
			a.base.updateEntity();
			helper.assertTrue(a.base.efficiency == 40, "the emitter is gone and the efficiency is still " + a.base.efficiency);
		} finally {
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/**
	 * With no stabilizers the field is zero, the heat addition divides by zero, and the core explodes
	 * <em>twice</em> in the one tick: the heat check and then the field check. Two nukes at one spot, of radius
	 * 50 + (fuel / 16000) x 150, and the core is gone.
	 */
	@GameTest(template = HUGE)
	public static void aMachineWithNoStabilizersExplodesTwiceInOneTick(GameTestHelper helper) {

		BlockPos core = new BlockPos(10, 0, 10);
		AMSBaseTE base = base(helper, core);
		AMSEmitterTE emitter = emitter(helper, core.above(9));
		SatelliteSavedData data = SatelliteSavedData.getData(helper.getLevel());
		List<Integer> freqs = new ArrayList<>();

		try {
			for(int i = 8; i <= 11; i++) base.slots[i] = new ItemStack(ModItems.ams_catalyst_iron.get());
			base.slots[12] = new ItemStack(ModItems.ams_core_sing.get());
			for(int i = 0; i < 3; i++) {
				data.sats.put(98_050 + i, new SatelliteResonator());
				freqs.add(98_050 + i);
				ItemStack chip = new ItemStack(ModItems.sat_chip.get());
				ISatChip.setFreqS(chip, 98_050 + i);
				base.slots[13 + i] = chip;
			}
			base.tanks[2].setFill(8000);
			base.tanks[3].setFill(4000);
			emitter.efficiency = 100;

			base.updateEntity();

			BlockPos abs = helper.absolutePos(core);
			List<EntityNukeExplosionMK5> nukes = helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class, new AABB(abs).inflate(3D));
			helper.assertTrue(nukes.size() == 2, "two nukes, found " + nukes.size());
			helper.assertTrue(base.heat == AMSBaseTE.maxHeat, "the heat is clamped after the first: " + base.heat);

			// 50 + (8000 - 80 + 4000 - 80) / 16000 x 150 = 50 + 35.9 = 85, read before the tanks drained is not what it is read with.
			int radius = (int) (50 + (double) (7920 + 3920) / 16000D * 150);
			helper.assertTrue(radius == 161, "radius arithmetic: " + radius);
			for(EntityNukeExplosionMK5 nuke : nukes) {
				helper.assertTrue(nuke.length == 161, "the nuke's radius is " + nuke.length);
				nuke.discard();
			}
			helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "the core is gone");
		} finally {
			for(int f : freqs) data.sats.remove(f);
			for(EntityNukeExplosionMK5 nuke : helper.getLevel().getEntitiesOfClass(EntityNukeExplosionMK5.class,
					new AABB(helper.absolutePos(core)).inflate(3D))) nuke.discard();
		}
		helper.succeed();
	}

	/** {@code explode()} alone: ten rainbow clouds and a nuke, 50 empty, 200 full, and the core removed. */
	@GameTest(template = HUGE)
	public static void anExplosionIsTenCloudsAndAnUpstreamRadiusNuke(GameTestHelper helper) {

		BlockPos core = new BlockPos(10, 0, 10);
		AMSBaseTE base = base(helper, core);

		List<Entity> spawned = base.explode();
		try {
			helper.assertTrue(spawned.size() == 11, "eleven things: " + spawned.size());
			long clouds = spawned.stream().filter(e -> e instanceof com.hbm.entity.effect.EntityCloudFleijaRainbow).count();
			helper.assertTrue(clouds == 10, clouds + " clouds");
			helper.assertTrue(spawned.get(10) instanceof EntityNukeExplosionMK5 nuke && nuke.length == 50, "an empty core is radius 50");
			for(Entity e : spawned) {
				BlockPos at = e.blockPosition();
				if(e instanceof com.hbm.entity.effect.EntityCloudFleijaRainbow) {
					helper.assertTrue(Math.abs(at.getX() - helper.absolutePos(core).getX()) <= 100
							&& Math.abs(at.getY() - helper.absolutePos(core).getY()) <= 100
							&& Math.abs(at.getZ() - helper.absolutePos(core).getZ()) <= 100, "a cloud landed " + at);
				}
			}
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(core)).isAir(), "the core is gone");
		} finally {
			for(Entity e : spawned) e.discard();
		}

		AMSBaseTE second = base(helper, new BlockPos(4, 1, 4));
		second.tanks[2].setFill(8000);
		second.tanks[3].setFill(8000);
		helper.assertTrue(second.explosionRadius() == 200, "two full tanks make 200: " + second.explosionRadius());
		second.tanks[3].setFill(0);
		helper.assertTrue(second.explosionRadius() == 125, "one full tank makes 125: " + second.explosionRadius());
		helper.succeed();
	}

	/**
	 * Anything within ten blocks of the ball that it can see is set on fire and killed; what is behind a wall,
	 * or farther than ten blocks, is not.
	 */
	@GameTest(template = HUGE)
	public static void aRunningMachineKillsWhatItCanSee(GameTestHelper helper) {

		Assembly a = new Assembly(helper);
		Cow near = null, hidden = null, far = null;
		try {
			a.load(98_060);
			a.workingStabilizers(100);

			BlockPos abs = helper.absolutePos(a.core);
			ServerLevel level = helper.getLevel();

			near = EntityType.COW.create(level);
			near.setPos(abs.getX() + 3.5, abs.getY() + 6.0, abs.getZ() + 0.5);
			level.addFreshEntity(near);

			hidden = EntityType.COW.create(level);
			hidden.setPos(abs.getX() + 8.5, abs.getY() + 12.0, abs.getZ() + 0.5);
			level.addFreshEntity(hidden);
			// Stone on the ray from the ball's centre, one block short of it.
			level.setBlock(abs.offset(6, 12, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(abs.offset(5, 12, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(abs.offset(5, 13, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(abs.offset(6, 13, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(abs.offset(7, 12, 0), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(abs.offset(7, 13, 0), Blocks.STONE.defaultBlockState(), 3);

			far = EntityType.COW.create(level);
			far.setPos(abs.getX() + 0.5, abs.getY() + 6.0, abs.getZ() + 18.5);
			level.addFreshEntity(far);

			a.base.updateEntity();

			helper.assertTrue(near.isDeadOrDying(), "a cow in the open, three blocks off, lives");
			helper.assertTrue(near.getRemainingFireTicks() > 0, "and burns");
			helper.assertTrue(!far.isDeadOrDying() && far.getRemainingFireTicks() <= 0, "a cow eighteen blocks away is left alone");
			helper.assertTrue(!hidden.isDeadOrDying(), "a cow behind stone is left alone");
		} finally {
			for(Entity e : new Entity[] { near, hidden, far }) if(e != null) e.discard();
			a.clearNukes();
			a.cleanup();
		}
		helper.succeed();
	}

	/** Coolant and cryogel remove {@code heat/250 x 25} and drink 10 mB each; water and oil remove 75. */
	@GameTest(template = HUGE)
	public static void theBaseCoolsByTheProductOfItsTwoCoolants(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(10, 1, 10));

		base.heat = 3000;
		base.tanks[0].setFill(100);
		base.tanks[1].setFill(100);
		base.updateEntity();
		helper.assertTrue(base.heat == 3000 - 12 * 25, "coolant x cryogel: 12 x 25 = 300 off: " + base.heat);
		helper.assertTrue(base.tanks[0].getFill() == 90 && base.tanks[1].getFill() == 90, "10 mB each: " + base.tanks[0].getFill() + "/" + base.tanks[1].getFill());

		base.tanks[0].setTankType(Fluids.WATER);
		base.tanks[1].setTankType(Fluids.OIL);
		base.tanks[0].setFill(100);
		base.tanks[1].setFill(100);
		base.heat = 3000;
		base.updateEntity();
		helper.assertTrue(base.heat == 3000 - 75, "water x oil: 5 x 15 = 75 off: " + base.heat);

		// An empty tank stops it.
		base.tanks[1].setFill(0);
		base.heat = 3000;
		base.updateEntity();
		helper.assertTrue(base.heat == 3000, "one empty coolant tank and nothing is cooled");

		// And it never goes below zero.
		base.tanks[1].setFill(100);
		base.heat = 10;
		base.updateEntity();
		helper.assertTrue(base.heat == 0, "heat floors at zero: " + base.heat);
		helper.succeed();
	}

	/** Locked, the base keeps nothing: zero field, efficiency and power, and warning 33. */
	@GameTest(template = BIG)
	public static void aLockedBaseIsDead(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		base.locked = true;
		base.power = 5000;
		base.field = 50;
		base.efficiency = 50;
		base.updateEntity();
		helper.assertTrue(base.field == 0 && base.efficiency == 0 && base.power == 0 && base.warning == 33,
				"field " + base.field + ", efficiency " + base.efficiency + ", power " + base.power + ", warning " + base.warning);
		helper.succeed();
	}

	/** The base takes fluid into its four tanks by type, and reports and takes its power as a provider. */
	@GameTest(template = BIG)
	public static void theBaseTakesFluidInAndReportsItsPower(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));

		// Fluids: the demand is each tank's room, by type.
		helper.assertTrue(base.getDemand(Fluids.DEUTERIUM, 0) == 8000 && base.getDemand(Fluids.TRITIUM, 0) == 8000
				&& base.getDemand(Fluids.COOLANT, 0) == 8000 && base.getDemand(Fluids.WATER, 0) == 0, "demand by type");
		long left = base.transferFluid(Fluids.DEUTERIUM, 0, 9000);
		helper.assertTrue(left == 1000 && base.tanks[2].getFill() == 8000, "deuterium fills to 8000 and returns the rest: " + left);

		// Power: a provider with power gives to a receiver on its side.
		base.power = 1_000_000L;
		helper.assertTrue(base.getPower() == 1_000_000L && base.getProviderSpeed() == base.getMaxPower(), "provider");
		base.setPower(5);
		helper.assertTrue(base.power == 5, "setPower");
		helper.succeed();
	}

	// ---- persistence and sync --------------------------------------------------------------

	/** Every saved field comes back: power, field, efficiency, heat, locked, the tanks and the items. */
	@GameTest(template = BIG)
	public static void theBaseKeepsEverythingAcrossASave(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		base.power = 123_456_789_012L;
		base.field = 77;
		base.efficiency = 55;
		base.heat = 4321;
		base.locked = true;
		base.tanks[0].setTankType(Fluids.OIL);
		base.tanks[0].setFill(1234);
		base.tanks[3].setTankType(Fluids.BALEFIRE);
		base.tanks[3].setFill(7);
		base.slots[12] = new ItemStack(ModItems.ams_core_thingy.get());

		CompoundTag tag = base.saveWithoutMetadata(helper.getLevel().registryAccess());
		AMSBaseTE copy = new AMSBaseTE(base.getBlockPos(), base.getBlockState());
		copy.loadWithComponents(tag, helper.getLevel().registryAccess());

		helper.assertTrue(copy.power == 123_456_789_012L && copy.field == 77 && copy.efficiency == 55 && copy.heat == 4321 && copy.locked, "the numbers");
		helper.assertTrue(copy.tanks[0].getTankType() == Fluids.OIL && copy.tanks[0].getFill() == 1234, "coolant A");
		helper.assertTrue(copy.tanks[3].getTankType() == Fluids.BALEFIRE && copy.tanks[3].getFill() == 7, "fuel B");
		helper.assertTrue(copy.slots[12].getItem() == ModItems.ams_core_thingy.get(), "the core");
		helper.succeed();
	}

	/** The emitter and the stabilizer too. */
	@GameTest(template = BIG)
	public static void theEmitterAndTheStabilizerKeepTheirStateAcrossASave(GameTestHelper helper) {

		AMSEmitterTE emitter = emitter(helper, new BlockPos(3, 0, 3));
		emitter.power = 99_999L;
		emitter.efficiency = 12;
		emitter.heat = 2000;
		emitter.locked = true;
		emitter.tank.setTankType(Fluids.CRYOGEL);
		emitter.tank.setFill(4000);
		emitter.slots[2] = new ItemStack(LeafiaItems.ams_muzzle.get());

		AMSEmitterTE e2 = new AMSEmitterTE(emitter.getBlockPos(), emitter.getBlockState());
		e2.loadWithComponents(emitter.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
		helper.assertTrue(e2.power == 99_999L && e2.efficiency == 12 && e2.heat == 2000 && e2.locked, "emitter numbers");
		helper.assertTrue(e2.tank.getTankType() == Fluids.CRYOGEL && e2.tank.getFill() == 4000, "emitter tank");
		helper.assertTrue(e2.slots[2].getItem() == LeafiaItems.ams_muzzle.get(), "the muzzle");

		AMSStabilizerTE stabilizer = stabilizer(helper, new BlockPos(10, 0, 10), Direction.SOUTH);
		stabilizer.power = 777L;
		stabilizer.heat = 321;
		stabilizer.tank.setTankType(Fluids.WATER);
		stabilizer.tank.setFill(55);
		stabilizer.slots[2] = new ItemStack(LeafiaItems.ams_focus_booster.get());

		AMSStabilizerTE s2 = new AMSStabilizerTE(stabilizer.getBlockPos(), stabilizer.getBlockState());
		s2.loadWithComponents(stabilizer.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
		helper.assertTrue(s2.power == 777L && s2.heat == 321 && s2.tank.getTankType() == Fluids.WATER && s2.tank.getFill() == 55, "stabilizer");
		helper.assertTrue(s2.slots[2].getItem() == LeafiaItems.ams_focus_booster.get(), "the focus");
		helper.succeed();
	}

	/** What each machine's packet slots do on the client: the keys are upstream's. */
	@GameTest(template = BIG)
	public static void thePacketKeysAreUpstreams(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		CompoundTag tanks = new CompoundTag();
		FluidTank tank = new FluidTank(Fluids.WATER, 8000);
		tank.setFill(321);
		tank.writeToNBT(tanks, "tank1");

		base.onReceivePacketLocal((byte) 0, 42L);
		base.onReceivePacketLocal((byte) 1, true);
		base.onReceivePacketLocal((byte) 2, 7);
		base.onReceivePacketLocal((byte) 3, 8);
		base.onReceivePacketLocal((byte) 4, tanks);
		base.onReceivePacketLocal((byte) 5, true);
		base.onReceivePacketLocal((byte) 6, 0x123456);
		base.onReceivePacketLocal((byte) 7, 9);
		helper.assertTrue(base.power == 42L && base.locked && base.efficiency == 7 && base.heat == 8 && base.syncResonators && base.color == 0x123456 && base.field == 9,
				"the base's eight keys");
		helper.assertTrue(base.tanks[1].getTankType() == Fluids.WATER && base.tanks[1].getFill() == 321, "key 4 is the four tanks");
		helper.assertTrue("AMS_BASE".equals(base.getPacketIdentifier()), "identifier");

		AMSEmitterTE emitter = emitter(helper, new BlockPos(3, 0, 3));
		emitter.onReceivePacketLocal((byte) 4, Fluids.CRYOGEL.getID());
		emitter.onReceivePacketLocal((byte) 5, 1500);
		emitter.onReceivePacketLocal((byte) 0, 5L);
		helper.assertTrue(emitter.tank.getTankType() == Fluids.CRYOGEL && emitter.tank.getFill() == 1500 && emitter.power == 5L, "the emitter's keys");
		// A type change keeps the fill the packet already carried.
		emitter.tank.setFill(900);
		emitter.onReceivePacketLocal((byte) 4, Fluids.WATER.getID());
		helper.assertTrue(emitter.tank.getFill() == 900 && emitter.tank.getTankType() == Fluids.WATER, "retyping on the client keeps the fill");
		helper.assertTrue("AMS_EMTR".equals(emitter.getPacketIdentifier()), "emitter identifier");

		AMSStabilizerTE stabilizer = stabilizer(helper, new BlockPos(10, 0, 10), Direction.SOUTH);
		helper.assertTrue("AMS_LMTR".equals(stabilizer.getPacketIdentifier()), "stabilizer identifier");
		helper.succeed();
	}

	// ---- the menus -------------------------------------------------------------------------

	/** The base's sixteen slots at upstream's pixels, then the inventory at 140 and the hotbar at 198. */
	@GameTest(template = BIG)
	public static void theBaseMenuIsUpstreamsLayout(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		ServerPlayer player = mockPlayer(helper);
		AMSBaseMenu menu = new AMSBaseMenu(0, player.getInventory(), base);

		int[][] at = { { 8, 18 }, { 8, 54 }, { 152, 18 }, { 152, 54 }, { 8, 72 }, { 8, 108 }, { 152, 72 }, { 152, 108 },
				{ 80, 45 }, { 62, 63 }, { 98, 63 }, { 80, 81 }, { 80, 63 }, { 62, 108 }, { 80, 108 }, { 98, 108 } };

		helper.assertTrue(menu.slots.size() == 16 + 36, "slots " + menu.slots.size());
		for(int i = 0; i < 16; i++)
			helper.assertTrue(menu.slots.get(i).x == at[i][0] && menu.slots.get(i).y == at[i][1], "slot " + i + " at " + menu.slots.get(i).x + ", " + menu.slots.get(i).y);
		helper.assertTrue(menu.slots.get(16).x == 8 && menu.slots.get(16).y == 140, "inventory at 140");
		helper.assertTrue(menu.slots.get(43).x == 8 && menu.slots.get(43).y == 198, "hotbar at 198");
		helper.assertTrue(menu.slots.get(12).mayPlace(new ItemStack(Items.DIRT)), "dirt in the core's slot, by hand");
		helper.succeed();
	}

	/** The emitter's and stabilizer's four slots. */
	@GameTest(template = BIG)
	public static void theEmitterAndStabilizerMenusAreUpstreamsLayout(GameTestHelper helper) {

		ServerPlayer player = mockPlayer(helper);
		AMSEmitterMenu em = new AMSEmitterMenu(0, player.getInventory(), emitter(helper, new BlockPos(3, 0, 3)));
		AMSStabilizerMenu sm = new AMSStabilizerMenu(0, player.getInventory(), stabilizer(helper, new BlockPos(10, 0, 10), Direction.SOUTH));

		int[][] at = { { 44, 17 }, { 44, 53 }, { 80, 53 }, { 116, 53 } };
		for(var menu : new net.minecraft.world.inventory.AbstractContainerMenu[] { em, sm }) {
			helper.assertTrue(menu.slots.size() == 4 + 36, "slots " + menu.slots.size());
			for(int i = 0; i < 4; i++)
				helper.assertTrue(menu.slots.get(i).x == at[i][0] && menu.slots.get(i).y == at[i][1], "slot " + i);
			helper.assertTrue(menu.slots.get(4).y == 84 && menu.slots.get(31).y == 142, "inventory at 84, hotbar at 142");
		}
		helper.succeed();
	}

	/**
	 * Shift-click only ever takes things <em>out</em> of slots 0 to 3, toward the end of the list - the
	 * hotbar first - and does nothing for any other slot, including the base's own slots 4 to 15 and the
	 * player's inventory.
	 */
	@GameTest(template = BIG)
	public static void shiftClickOnlyLeavesTheFirstFourSlots(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		ServerPlayer player = mockPlayer(helper);
		AMSBaseMenu menu = new AMSBaseMenu(0, player.getInventory(), base);

		base.slots[0] = new ItemStack(Items.DIRT, 5);
		ItemStack moved = menu.quickMoveStack(player, 0);
		helper.assertTrue(moved.getItem() == Items.DIRT && moved.getCount() == 5, "the stack is returned: " + moved);
		helper.assertTrue(base.slots[0].isEmpty(), "slot 0 is empty");
		helper.assertTrue(player.getInventory().getItem(8).is(Items.DIRT) && player.getInventory().getItem(8).getCount() == 5,
				"it lands in the last hotbar slot, which the reverse merge fills first");

		// Slot 3 is the last that moves; slot 4, the first fuel slot, is the first that does not.
		base.slots[3] = new ItemStack(Items.DIRT, 2);
		helper.assertTrue(menu.quickMoveStack(player, 3).getCount() == 2 && base.slots[3].isEmpty(), "slot 3 moves");
		base.slots[4] = new ItemStack(Items.DIRT, 5);
		helper.assertTrue(menu.quickMoveStack(player, 4).isEmpty() && base.slots[4].getCount() == 5, "slot 4 does not move");
		base.slots[5] = new ItemStack(Items.DIRT, 5);
		helper.assertTrue(menu.quickMoveStack(player, 5).isEmpty() && base.slots[5].getCount() == 5, "slot 5 does not move");

		player.getInventory().setItem(0, new ItemStack(Items.STICK));
		helper.assertTrue(menu.quickMoveStack(player, 16 + 27).isEmpty() && base.slots[0].isEmpty(), "nothing is shift-clicked into the machine");
		helper.assertTrue(menu.quickMoveStack(player, 1).isEmpty(), "an empty slot is nothing");
		helper.succeed();
	}

	/** The screen stays open within the square root of 128 and shuts beyond it. */
	@GameTest(template = BIG)
	public static void theMenusStayValidWithinTheSquareRootOfOneTwentyEight(GameTestHelper helper) {

		AMSBaseTE base = base(helper, new BlockPos(8, 1, 8));
		ServerPlayer player = mockPlayer(helper);
		BlockPos p = base.getBlockPos();

		player.setPos(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5 + 11.0);
		helper.assertTrue(base.stillValid(player), "11 blocks away is within 128 (121)");
		player.setPos(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5 + 11.4);
		helper.assertTrue(!base.stillValid(player), "11.4 blocks away is past it (129.96)");
		helper.succeed();
	}

	// ---- the assets ------------------------------------------------------------------------

	private static byte[] resource(GameTestHelper helper, String path) {
		try(InputStream in = LeafiaAMSTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, path + " is missing from the jar");
			return in.readAllBytes();
		} catch(IOException e) {
			helper.fail("could not read " + path + ": " + e);
			return new byte[0];
		}
	}

	/**
	 * Everything the renderers and screens name is in the jar: five OBJs with a material and texture
	 * coordinates inside 0..1 (so they bake against the atlas), the eight textures, three 256-square GUI
	 * sheets, the model JSONs that point at them, and the blockstates and item models.
	 */
	@GameTest(template = EMPTY)
	public static void everyAssetTheRenderersAndScreensDrawFromIsThere(GameTestHelper helper) {

		String base = "/assets/leafia/";

		for(String obj : new String[] { "ams_base", "ams_emitter", "ams_emitter_destroyed", "ams_limiter", "ams_limiter_destroyed" }) {
			String text = new String(resource(helper, base + "models/blocks/" + obj + ".obj"), StandardCharsets.UTF_8);
			helper.assertTrue(text.contains("usemtl"), obj + " has no material, so NeoForge would bake nothing");
			double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			int faces = 0;
			for(String line : text.split("\n")) {
				if(line.startsWith("vt ")) {
					String[] p = line.trim().split("\\s+");
					for(int i = 1; i <= 2; i++) {
						double v = Double.parseDouble(p[i]);
						lo = Math.min(lo, v);
						hi = Math.max(hi, v);
					}
				}
				if(line.startsWith("f ")) faces++;
			}
			helper.assertTrue(lo >= 0 && hi <= 1, obj + " leaves 0..1 (" + lo + " to " + hi + "): it cannot bake against the atlas");
			helper.assertTrue(faces > 50, obj + " has " + faces + " faces");
		}

		for(String model : new String[] { "ams_base", "ams_base_balefire", "ams_emitter", "ams_emitter_balefire", "ams_emitter_destroyed",
				"ams_limiter", "ams_limiter_balefire", "ams_limiter_destroyed" }) {
			String json = new String(resource(helper, base + "models/block/" + model + ".json"), StandardCharsets.UTF_8);
			helper.assertTrue(json.contains("neoforge:obj") && json.contains("\"flip_v\": true"), model + " is not a flipped OBJ model");
		}

		for(String texture : new String[] { "ams_base", "ams_base_balefire", "ams_emitter", "ams_emitter_balefire", "ams_limiter",
				"ams_limiter_balefire", "ams_destroyed" }) {
			resource(helper, base + "textures/block/" + texture + ".png");
		}
		for(String blockstate : new String[] { "ams_base", "ams_emitter", "ams_limiter" }) resource(helper, base + "blockstates/" + blockstate + ".json");
		for(String item : new String[] { "ams_base", "ams_emitter", "ams_limiter", "ams_muzzle", "ams_focus_limiter", "ams_focus_booster" }) {
			resource(helper, base + "models/item/" + item + ".json");
		}
		for(String item : new String[] { "ams_muzzle", "ams_focus_limiter", "ams_focus_booster" }) resource(helper, base + "textures/item/" + item + ".png");

		for(String gui : new String[] { "ams_base", "ams_emitter", "ams_stabilizer" }) {
			try {
				BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(resource(helper, base + "textures/gui/" + gui + ".png")));
				helper.assertTrue(image.getWidth() == 256 && image.getHeight() == 256, gui + " is " + image.getWidth() + "x" + image.getHeight() + ", and the screens blit from a 256 sheet");
			} catch(IOException e) {
				helper.fail("could not read " + gui + ": " + e);
			}
		}
		helper.succeed();
	}

	/** The English names: upstream's three machines, and the muzzle and focuses. */
	@GameTest(template = EMPTY)
	public static void theNamesAreUpstreams(GameTestHelper helper) {

		com.google.gson.JsonObject lang = com.google.gson.JsonParser.parseString(
				new String(resource(helper, "/assets/leafia/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();

		String[][] names = { { "block.leafia.ams_base", "AMC Base" }, { "block.leafia.ams_emitter", "AMC Emitter" }, { "block.leafia.ams_limiter", "AMC Stabilizer" },
				{ "tile.ams_base.name", "AMC Base" }, { "tile.ams_emitter.name", "AMC Emitter" }, { "tile.ams_limiter.name", "AMC Stabilizer" },
				{ "item.leafia.ams_muzzle", "Ray-Emission Muzzle" }, { "item.leafia.ams_focus_limiter", "Limiting Stabilizer Lens" },
				{ "item.leafia.ams_focus_booster", "Boosting Stabilizer Lens" } };
		for(String[] n : names)
			helper.assertTrue(lang.has(n[0]) && n[1].equals(lang.get(n[0]).getAsString()), n[0] + " is " + lang.get(n[0]));

		AMSBaseTE te = base(helper, new BlockPos(2, 0, 2));
		helper.assertTrue("tile.ams_base.name".equals(te.getName()), "the base's menu title key");
		helper.succeed();
	}
}
