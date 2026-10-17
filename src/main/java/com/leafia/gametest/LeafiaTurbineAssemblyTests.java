package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.BlockEntityProxyCombo;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase.TurbineComponentType;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.AssemblyErrorReason;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeError;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeSuccess;
import com.leafia.contents.machines.misc.modular_turbine.flywheel.MTFlywheelTE;
import com.leafia.contents.machines.misc.modular_turbine.ports.MTComponentPortTE;
import com.leafia.contents.machines.misc.modular_turbine.power_port.MTPowerPortBlock;
import com.leafia.contents.machines.misc.modular_turbine.power_port.MTPowerPortTE;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Turbine T3: the parts that give a turbine its shape — shaft, separators, steam ports and the
 * electricity port — placed for real and walked by a real core.
 *
 * <p>Every test builds along one line: the core at {@link #CORE}, facing so its shaft runs
 * north (toward z = 0), and 3x3 parts whose core blocks sit one below the shaft. No blades exist
 * yet (T4), so the furthest a stage can get is "has no blades" — which is itself the proof that
 * the walk got past the identifier check.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaTurbineAssemblyTests {

	private static final String EMPTY = "empty";
	/** 16 cubed, the base mod's {@code empty16} copied into this namespace: a 7x7 part is 9 wide and 8 tall. */
	private static final String ARENA = "empty16";

	/** The turbine core. Its shaft runs from z = 3 toward z = 0 at y = 3. */
	private static final BlockPos CORE = new BlockPos(2, 3, 4);

	/**
	 * Upstream's numbers for every part, read out of each class: size, shaft height, weight, the
	 * multiblock extents {up, down, north, south, west, east}, and the component type.
	 */
	private record Spec(int size, int shaft, double weight, int[] dims, TurbineComponentType type, int[] connect) {}

	private static Map<String, Spec> specs() {
		Map<String, Spec> s = new java.util.LinkedHashMap<>();
		int[] any = {};
		s.put("modular_turbine_shaft", new Spec(1, 0, 0.001, new int[] { 0, 0, 0, 0, 0, 0 }, TurbineComponentType.SHAFT_ONLY, any));
		int[][] dims = { { 2, 0, 0, 0, 1, 1 }, { 4, 0, 0, 0, 2, 2 }, { 5, 0, 0, 0, 3, 3 }, { 7, 0, 0, 0, 4, 4 } };
		int[] sizes = { 3, 5, 7, 9 };
		for(int i = 0; i < 4; i++) {
			int n = sizes[i];
			String p = "modular_turbine_" + n + "x" + n;
			s.put(p + "_separator", new Spec(n, i + 1, 0.1, dims[i], TurbineComponentType.SEPARATOR, any));
			s.put(p + "_port_top", new Spec(n, i + 1, 0.1, dims[i], TurbineComponentType.PORT, any));
			s.put(p + "_port_side", new Spec(n, i + 1, 0.1, dims[i], TurbineComponentType.PORT, any));
			s.put(p + "_port_inline", new Spec(n, i + 1, 0.1, dims[i], TurbineComponentType.INLINE_PORT, any));
		}
		// T4: blades and flywheels share their shapes. The 2x2 flywheel joins 2x2 parts (D-3076; upstream said 3x3).
		int[] rotor = { 2, 3, 5, 7, 9 };
		int[] rotorShaft = { 1, 1, 2, 3, 4 };
		int[][] rotorDims = { { 1, 0, 0, 0, 1, 1 }, { 2, 0, 0, 0, 1, 1 }, { 3, 0, 0, 0, 2, 2 }, { 4, 0, 0, 0, 3, 3 }, { 5, 0, 0, 0, 4, 4 } };
		double[] bladeWeight = { 5, 10, 20, 30, 40 };
		double[] flywheelWeight = { 120, 160, 320, 480, 640 };
		int[] flywheelConnect = { 2, 3, 5, 7, 9 };
		for(int i = 0; i < rotor.length; i++) {
			int n = rotor[i];
			String p = "modular_turbine_" + n + "x" + n;
			for(String look : new String[] { "", "_glass", "_smooth", "_glass_smooth" })
				s.put(p + "_blades" + look, new Spec(n, rotorShaft[i], bladeWeight[i], rotorDims[i], TurbineComponentType.BLADES, new int[] { n }));
			s.put(p + "_flywheel", new Spec(n, rotorShaft[i], flywheelWeight[i], rotorDims[i], TurbineComponentType.FLYWHEEL, new int[] { flywheelConnect[i] }));
		}
		// T5: the one generator, three blocks deep along the shaft with its core in the middle.
		s.put("modular_turbine_3x3_generator", new Spec(3, 1, 0.1, new int[] { 2, 0, 1, 1, 1, 1 }, TurbineComponentType.GENERATOR, any));
		return s;
	}

	private static ModularTurbineBlockBase part(String name) {
		return LeafiaBlocks.modular_turbine_parts.get(name).get();
	}

	/** Places the core directly, facing so the shaft runs north. */
	private static MTCoreTE placeCore(GameTestHelper helper) {
		helper.setBlock(CORE, LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.SOUTH));
		return (MTCoreTE) helper.getBlockEntity(CORE);
	}

	/**
	 * Places a part through its own {@code setPlacedBy}, as a player looking north would — so the
	 * real multiblock fill, the extras and the facing all run. {@code at} is the part's core block.
	 */
	private static void place(GameTestHelper helper, Block block, BlockPos at) {
		var player = helper.makeMockPlayer(GameType.CREATIVE);
		player.setYRot(Direction.NORTH.toYRot());
		BlockPos abs = helper.absolutePos(at);
		helper.getLevel().setBlock(abs, block.defaultBlockState(), Block.UPDATE_ALL);
		block.setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), player, new ItemStack(block));
	}

	/** A 3x3 part on the shaft {@code step} blocks in front of the core. */
	private static BlockPos onShaft(int step) {
		return CORE.north(step).below(1);
	}

	/** Every part, the power port and the four block entity types exist exactly when the turbine does. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.modularTurbine();
		List<String> problems = new ArrayList<>();

		List<String> names = new ArrayList<>(specs().keySet());
		names.add("modular_turbine_port");
		for(String name : names) {
			if(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name)) != on) problems.add("block " + name);
			if(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(name)) != on) problems.add("item " + name);
		}
		for(String type : new String[] { "modular_turbine_component", "modular_turbine_port",
				"modular_turbine_proxy", "modular_turbine_power_port" }) {
			if(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl(type)) != on) problems.add("type " + type);
		}

		helper.assertTrue(problems.isEmpty(), "should exist exactly when the turbine does: " + problems);
		helper.succeed();
	}

	/**
	 * Every part is in the creative tab now the component renderer draws it (T6, D-1062). Until
	 * then they were invisible and kept out; this test asserted the opposite and flipped here.
	 */
	@GameTest(template = EMPTY)
	public static void everyPartIsInTheTab(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		var contents = LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
				.map(s -> s.get().asItem()).toList();
		List<String> missing = new ArrayList<>();
		for(DeferredBlock<? extends ModularTurbineBlockBase> part : LeafiaBlocks.modular_turbine_parts.values()) {
			if(!contents.contains(part.get().asItem())) missing.add(part.getId().getPath());
		}
		if(!contents.contains(LeafiaBlocks.modular_turbine_port.get().asItem())) missing.add("modular_turbine_port");
		if(!contents.contains(LeafiaBlocks.modular_turbine_core.get().asItem())) missing.add("modular_turbine_core");
		helper.assertTrue(missing.isEmpty(), "not in the tab: " + missing);
		helper.succeed();
	}

	/**
	 * Every baked part the renderer asks for exists, for every registered part. The renderer looks
	 * models up by name, and a name the generator never wrote bakes to the missing-model cube with
	 * nothing logged until somebody looks at that one turbine part. Read from the manifest and the
	 * model files themselves, because the renderer's own classes are client-only.
	 *
	 * <p>Also pins upstream's missing {@code Tube3}: a 3x3 blade housing has an inner shell and no
	 * outer one.
	 */
	@GameTest(template = EMPTY)
	public static void everyPartTheRendererDrawsHasAModel(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		List<String> manifest;
		try(var in = LeafiaTurbineAssemblyTests.class.getResourceAsStream("/assets/leafia/modular_turbine_models.txt")) {
			helper.assertTrue(in != null, "the turbine model manifest is missing");
			manifest = java.util.Arrays.stream(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n"))
					.map(String::trim).filter(x -> !x.isEmpty()).toList();
		} catch(java.io.IOException e) {
			throw new RuntimeException(e);
		}

		List<String> wanted = new ArrayList<>(List.of("shaft", "blades"));
		for(var part : LeafiaBlocks.modular_turbine_parts.values()) {
			ModularTurbineBlockBase b = part.get();
			int n = b.size();
			switch(b.componentType()) {
				case BLADES -> wanted.add("tube" + n + "_inner_" + b.variant());
				case FLYWHEEL -> { wanted.add("flywheel" + n); wanted.add("flywheel" + n + "_dark"); }
				case GENERATOR -> wanted.add("generator");
				case SHAFT_ONLY -> { }
				default -> wanted.add(n <= 3 ? "block2_3" : "block" + n);
			}
		}

		List<String> problems = new ArrayList<>();
		for(String name : wanted) {
			String full = "modular_turbine_part_" + name;
			if(!manifest.contains(full)) problems.add(full + " is not in the manifest");
			if(LeafiaTurbineAssemblyTests.class.getResource("/assets/leafia/models/block/" + full + ".json") == null)
				problems.add(full + ".json does not exist");
		}
		if(manifest.contains("modular_turbine_part_tube3")) problems.add("a Tube3 outer shell appeared; upstream has none");
		if(!manifest.contains("modular_turbine_part_tube9")) problems.add("the 9x9 outer shell is gone");

		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + problems);
		helper.succeed();
	}

	/** Each part's size, shaft height, weight, extents and type, against the table above. */
	@GameTest(template = EMPTY)
	public static void partNumbersAreUpstreams(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		List<String> problems = new ArrayList<>();
		for(var entry : specs().entrySet()) {
			ModularTurbineBlockBase b = part(entry.getKey());
			Spec s = entry.getValue();
			String n = entry.getKey();
			if(b.size() != s.size) problems.add(n + " size " + b.size());
			if(b.shaftHeight() != s.shaft) problems.add(n + " shaft " + b.shaftHeight());
			if(b.weight() != s.weight) problems.add(n + " weight " + b.weight());
			if(!java.util.Arrays.equals(b.getDimensions(), s.dims)) problems.add(n + " dims " + java.util.Arrays.toString(b.getDimensions()));
			if(b.componentType() != s.type) problems.add(n + " type " + b.componentType());
			if(!java.util.Arrays.equals(b.canConnectTo(), s.connect)) problems.add(n + " connects to " + java.util.Arrays.toString(b.canConnectTo()));
		}
		helper.assertTrue(problems.size() == 0 && specs().size() == LeafiaBlocks.modular_turbine_parts.size(),
				problems.size() + " wrong, " + LeafiaBlocks.modular_turbine_parts.size() + " registered: " + problems);
		helper.succeed();
	}

	/**
	 * A 7x7 separator is its 7-wide, 6-tall disc plus the 5-wide cap row on top — 47 blocks — and
	 * every one of them finds its way back to the core.
	 */
	@GameTest(template = ARENA)
	public static void aSevenSeparatorIsADiscWithACap(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		ModularTurbineBlockBase sep = part("modular_turbine_7x7_separator");
		BlockPos at = new BlockPos(8, 1, 8);
		place(helper, sep, at);

		BlockPos coreAbs = helper.absolutePos(at);
		int count = 0;
		List<String> orphans = new ArrayList<>();
		for(BlockPos p : BlockPos.betweenClosed(coreAbs.offset(-4, 0, -1), coreAbs.offset(4, 7, 1))) {
			if(helper.getLevel().getBlockState(p).getBlock() != sep) continue;
			count++;
			if(!coreAbs.equals(sep.findCore(helper.getLevel(), p))) orphans.add(p.toShortString());
		}
		helper.assertTrue(count == 47, "a 7x7 separator should be 42 + 5 = 47 blocks, got " + count);
		helper.assertTrue(orphans.isEmpty(), "blocks that do not find the core: " + orphans);
		helper.assertTrue(helper.getLevel().getBlockState(coreAbs.above(6)).getBlock() == sep
				&& helper.getLevel().getBlockState(coreAbs.above(6).east(3)).getBlock() != sep,
				"the cap row is two narrower than the disc");
		helper.succeed();
	}

	/**
	 * A 3x3 side port plants a fluid proxy either side of its bottom row, and each proxy resolves
	 * to the port's block entity — which is how a pipe meeting the side reaches the stage's tank.
	 */
	@GameTest(template = EMPTY)
	public static void aSidePortsExtrasAreProxiesOntoThePort(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		ModularTurbineBlockBase port = part("modular_turbine_3x3_port_side");
		BlockPos at = new BlockPos(2, 1, 2);
		place(helper, port, at);

		helper.assertTrue(helper.getBlockEntity(at) instanceof MTComponentPortTE, "the core should be a port");
		for(BlockPos side : new BlockPos[] { at.east(), at.west() }) {
			var state = helper.getBlockState(side);
			helper.assertTrue(state.getBlock() == port && state.getValue(BlockDummyable.PART) == BlockDummyable.Part.EXTRA,
					side.toShortString() + " should be an extra");
			helper.assertTrue(helper.getBlockEntity(side) instanceof BlockEntityProxyCombo proxy
					&& proxy.getTile() instanceof MTComponentPortTE,
					side.toShortString() + " should proxy onto the port");
		}
		helper.assertTrue(helper.getBlockState(at.above(2)).getValue(BlockDummyable.PART) == BlockDummyable.Part.DUMMY,
				"the top row is plain dummies on a side port");
		helper.succeed();
	}

	/** Two separators and nothing between them is a valid, empty turbine: no stages, weight 0.2. */
	@GameTest(template = EMPTY)
	public static void twoSeparatorsAssembleEmpty(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), onShaft(1));
		place(helper, part("modular_turbine_3x3_separator"), onShaft(2));

		AssemblyReturnCode code = core.reassemble();
		helper.assertTrue(code instanceof ReturnCodeSuccess, "two separators should assemble, got "
				+ (code instanceof ReturnCodeError e ? e.reason : code));
		helper.assertTrue(core.length == 2 && core.components.size() == 2 && core.assemblies.isEmpty(),
				"length " + core.length + ", " + core.components.size() + " parts, " + core.assemblies.size() + " stages");
		helper.assertTrue(Math.abs(core.weight - 0.2) < 1e-12, "weight is " + core.weight + ", wanted 0.1 + 0.1");
		helper.assertTrue(helper.getBlockEntity(onShaft(1)) instanceof ModularTurbineComponentTE c && c.core == core,
				"the separator should know its core");
		helper.succeed();
	}

	/**
	 * A stage between two separators needs an identifier, then blades. A port with no identifier
	 * fails the first; give it hot steam through the fuzzy identifier and it fails the second —
	 * which proves the identifier was read, since the walk checks it first.
	 */
	@GameTest(template = EMPTY)
	public static void aStageNeedsAnIdentifierThenBlades(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine() || !LeafiaFeatures.fuzzyIdentifier) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), onShaft(1));
		place(helper, part("modular_turbine_3x3_port_side"), onShaft(2));
		place(helper, part("modular_turbine_3x3_separator"), onShaft(3));

		AssemblyReturnCode first = core.reassemble();
		helper.assertTrue(first instanceof ReturnCodeError e && e.reason == AssemblyErrorReason.NO_IDENTIFIER,
				"a stage without an identifier should fail NO_IDENTIFIER");

		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack fuzzy = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(fuzzy, Fluids.HOTSTEAM);
		player.setItemInHand(InteractionHand.MAIN_HAND, fuzzy);
		BlockPos face = helper.absolutePos(onShaft(2).above(2));
		helper.getLevel().getBlockState(face).useItemOn(fuzzy, helper.getLevel(), player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(face), Direction.UP, face, false));

		MTComponentPortTE port = (MTComponentPortTE) helper.getBlockEntity(onShaft(2));
		helper.assertTrue(port.identifier == Fluids.HOTSTEAM, "the identifier should set the port to hot steam");

		AssemblyReturnCode second = core.reassemble();
		helper.assertTrue(second instanceof ReturnCodeError e && e.reason == AssemblyErrorReason.NO_BLADES,
				"with an identifier the stage should get as far as NO_BLADES");
		helper.succeed();
	}

	/** A part whose shaft is not at the core's height is NOT_ALIGNED, not silently skipped. */
	@GameTest(template = EMPTY)
	public static void aLowPartIsNotAligned(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper);
		// One block too high: its shaft is at y = 4 while the core's is at 3, and the walk meets
		// its bottom row instead.
		place(helper, part("modular_turbine_3x3_separator"), onShaft(1).above());

		AssemblyReturnCode code = core.reassemble();
		helper.assertTrue(code instanceof ReturnCodeError e && e.reason == AssemblyErrorReason.NOT_ALIGNED,
				"a part off the shaft's height should fail NOT_ALIGNED");
		helper.succeed();
	}

	/**
	 * The electricity port behind a turbine part reports that turbine's core, and its power is
	 * the core's output this tick — read and written through, not copied. It connects only on
	 * its face.
	 *
	 * <p>{@code powerOutput} is written and read inside one tick, before the core next ticks and
	 * recomputes it (D-953).
	 */
	@GameTest(template = EMPTY)
	public static void thePowerPortIsAWindowOntoTheCore(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), onShaft(1));
		place(helper, part("modular_turbine_3x3_separator"), onShaft(2));
		helper.assertTrue(core.reassemble() instanceof ReturnCodeSuccess, "precondition: it assembles");

		// Against the separator's west edge, facing west, away from the turbine.
		BlockPos portPos = onShaft(1).west(2);
		helper.setBlock(portPos, LeafiaBlocks.modular_turbine_port.get().defaultBlockState()
				.setValue(MTPowerPortBlock.FACING, Direction.WEST));
		MTPowerPortTE port = (MTPowerPortTE) helper.getBlockEntity(portPos);

		core.powerOutput = 1234;
		port.updateEntity();
		helper.assertTrue(port.getCore() == core, "the port should find the core through the separator");
		helper.assertTrue(port.getPower() == 1234 && port.getMaxPower() == 1234,
				"the port should report the core's output, got " + port.getPower());
		port.setPower(1000);
		helper.assertTrue(core.powerOutput == 1000, "draining the port drains the core");

		helper.assertTrue(port.canConnect(Direction.WEST) && !port.canConnect(Direction.EAST),
				"it connects on its face only");
		helper.succeed();
	}

	/** Upstream's piston-style placement: near and above is UP, near and below DOWN, else toward the player. */
	@GameTest(template = EMPTY)
	public static void thePowerPortFacesLikeUpstream(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		var player = helper.makeMockPlayer(GameType.CREATIVE);

		player.setPos(pos.getX(), pos.getY() + 1, pos.getZ());
		helper.assertTrue(MTPowerPortBlock.facingFromEntity(pos, player) == Direction.UP,
				"eye 2.62 above and close: UP");

		player.setPos(pos.getX(), pos.getY() - 3, pos.getZ());
		helper.assertTrue(MTPowerPortBlock.facingFromEntity(pos, player) == Direction.DOWN,
				"eye below and close: DOWN");

		player.setPos(pos.getX() + 5, pos.getY() + 1, pos.getZ());
		player.setYRot(Direction.WEST.toYRot());
		helper.assertTrue(MTPowerPortBlock.facingFromEntity(pos, player) == Direction.EAST,
				"far away and looking west: faces east, back at the player");
		helper.succeed();
	}

	// ------------------------------------------------------------------- T4: blades and flywheels

	/** The core for the arena tests: room for a turbine five parts long, running north. */
	private static final BlockPos ARENA_CORE = new BlockPos(8, 4, 12);

	private static MTCoreTE placeArenaCore(GameTestHelper helper) {
		helper.setBlock(ARENA_CORE, LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.SOUTH));
		return (MTCoreTE) helper.getBlockEntity(ARENA_CORE);
	}

	/** Where a part of shaft height {@code shaft} goes, {@code step} blocks along the arena turbine. */
	private static BlockPos arenaShaft(int step, int shaft) {
		return ARENA_CORE.north(step).below(shaft);
	}

	/**
	 * <b>A real stage turns the shaft.</b> Separator, side port on steam, 3x3 blades, separator:
	 * one stage, and one tick of it.
	 *
	 * <pre>
	 *   input tank   STEAM (index 4), 1 blade, 3x3:  2.5^4 × 1 × 8 × 5 × 12          = 18,750 mB
	 *   output tank  SPENTSTEAM (5), 4 blades, 3x3:  2.5^5 × 4 × 8 × 5 × 12 / 1000   = 187 mB
	 *   one op       100 steam -> 1 spent steam      so the full tank is min(187, 187) = 187 ops
	 * </pre>
	 *
	 * The governor opens admission to 0.02 on the first tick, so only {@code floor(187 × 0.02) = 3}
	 * ops drive the shaft — but the tanks move by all 187, upstream's "processed instantaneously,
	 * the only bottleneck is the buffer" (D-1058). So after one tick: 50 mB of steam left, 187 of
	 * spent steam made, and a shaft that has started to turn.
	 */
	@GameTest(template = ARENA)
	public static void aRealStageTurnsTheShaft(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeArenaCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), arenaShaft(1, 1));
		place(helper, part("modular_turbine_3x3_port_side"), arenaShaft(2, 1));
		place(helper, part("modular_turbine_3x3_blades"), arenaShaft(3, 1));
		place(helper, part("modular_turbine_3x3_separator"), arenaShaft(4, 1));
		((MTComponentPortTE) helper.getBlockEntity(arenaShaft(2, 1))).identifier = Fluids.STEAM;

		AssemblyReturnCode code = core.reassemble();
		helper.assertTrue(code instanceof ReturnCodeSuccess, "the stage should assemble, got "
				+ (code instanceof ReturnCodeError e ? e.reason : code));
		helper.assertTrue(core.assemblies.size() == 1, core.assemblies.size() + " stages");
		var stage = core.assemblies.get(0);
		helper.assertTrue(stage.input.getTankType() == Fluids.STEAM && stage.input.getMaxFill() == 18_750,
				"input tank " + stage.input.getTankType().getName() + " " + stage.input.getMaxFill());
		helper.assertTrue(stage.output.getTankType() == Fluids.SPENTSTEAM && stage.output.getMaxFill() == 187,
				"output tank " + stage.output.getTankType().getName() + " " + stage.output.getMaxFill());
		helper.assertTrue(Math.abs(core.weight - 10.3) < 1e-9, "weight is " + core.weight + ", wanted 0.1 + 0.1 + 10 + 0.1");

		stage.input.setFill(18_750);
		core.updateEntity();

		helper.assertTrue(stage.input.getFill() == 50 && stage.output.getFill() == 187,
				"after one tick: " + stage.input.getFill() + " steam, " + stage.output.getFill() + " spent; wanted 50 and 187");
		helper.assertTrue(stage.lastOps == 300, "3 admitted ops of 100 steam, got " + stage.lastOps);
		helper.assertTrue(core.rps > 0, "the shaft should have started to turn, rps " + core.rps);
		helper.succeed();
	}

	/** A 3x3 blade stage cannot run straight into a 5x5 one: blades only join their own size. */
	@GameTest(template = ARENA)
	public static void bladesOnlyJoinTheirOwnSize(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeArenaCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), arenaShaft(1, 1));
		place(helper, part("modular_turbine_3x3_blades"), arenaShaft(2, 1));
		place(helper, part("modular_turbine_5x5_blades"), arenaShaft(3, 2));

		AssemblyReturnCode code = core.reassemble();
		helper.assertTrue(code instanceof ReturnCodeError e && e.reason == AssemblyErrorReason.INCOMPATIBLE_SIZE,
				"3x3 blades then 5x5 blades should fail INCOMPATIBLE_SIZE");
		helper.succeed();
	}

	/**
	 * Past 8 rps a flywheel grinds whatever is in its disc: {@code sqrt(160.2) / 2 × 10 = 63.3} of
	 * damage that ignores armour, well past a pig's 10 health, and a ground animal is removed on
	 * the spot. A creative player in the same place is left alone.
	 *
	 * <p>rps is set and read inside one tick; the core does not tick between (D-953).
	 */
	@GameTest(template = ARENA)
	public static void aFastFlywheelGrinds(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeArenaCore(helper);
		place(helper, part("modular_turbine_3x3_separator"), arenaShaft(1, 1));
		place(helper, part("modular_turbine_3x3_flywheel"), arenaShaft(2, 1));
		place(helper, part("modular_turbine_3x3_separator"), arenaShaft(3, 1));
		helper.assertTrue(core.reassemble() instanceof ReturnCodeSuccess, "precondition: it assembles");
		helper.assertTrue(Math.abs(core.weight - 160.2) < 1e-9, "weight is " + core.weight);

		MTFlywheelTE flywheel = (MTFlywheelTE) helper.getBlockEntity(arenaShaft(2, 1));
		Vec3 inDisc = Vec3.atBottomCenterOf(helper.absolutePos(arenaShaft(2, 1)));
		var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
		pig.moveTo(inDisc.x, inDisc.y, inDisc.z);
		helper.getLevel().addFreshEntity(pig);
		var creative = helper.makeMockPlayer(GameType.CREATIVE);
		creative.moveTo(inDisc.x, inDisc.y, inDisc.z);

		core.rps = 10;
		helper.assertTrue(Math.abs(MTFlywheelTE.damageFor(core.weight, core.rps) - Math.sqrt(160.2) / 2 * 10) < 1e-9,
				"damage formula");
		flywheel.updateEntity();

		helper.assertTrue(pig.isRemoved(), "the pig should have been ground and removed");
		helper.assertTrue(creative.getHealth() == creative.getMaxHealth() && !creative.isRemoved(),
				"a creative player is left alone");
		helper.succeed();
	}

	/**
	 * A 3x3 flywheel collides only within {@code 3 / 2 - 0.333 = 1.167} of the shaft: the edge
	 * blocks (1.0 away) are solid, the corners (1.414 away) can be walked through.
	 */
	@GameTest(template = ARENA)
	public static void aFlywheelIsRoundToWalkInto(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		BlockPos at = new BlockPos(8, 1, 8);
		place(helper, part("modular_turbine_3x3_flywheel"), at);

		var level = helper.getLevel();
		BlockPos edge = helper.absolutePos(at);               // bottom centre: 1.0 below the shaft
		BlockPos corner = helper.absolutePos(at.east());      // bottom corner: 1.414 away
		BlockPos centre = helper.absolutePos(at.above());     // on the shaft itself
		helper.assertTrue(!level.getBlockState(edge).getCollisionShape(level, edge).isEmpty(), "the edge should be solid");
		helper.assertTrue(!level.getBlockState(centre).getCollisionShape(level, centre).isEmpty(), "the hub should be solid");
		helper.assertTrue(level.getBlockState(corner).getCollisionShape(level, corner).isEmpty(), "the corner should be open");
		helper.succeed();
	}

	// -------------------------------------------------------------------------- T5: the generator

	/** A turbine core at column {@code x} of the arena, shaft running north at y = 4 from z = 12. */
	private static MTCoreTE coreAt(GameTestHelper helper, int x) {
		BlockPos at = new BlockPos(x, 4, 12);
		helper.setBlock(at, LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.SOUTH));
		return (MTCoreTE) helper.getBlockEntity(at);
	}

	/** The core block of a 3x3 part {@code step} blocks along the turbine at column {@code x}. */
	private static BlockPos step(int x, int step) {
		return new BlockPos(x, 3, 12 - step);
	}

	/**
	 * Places the generator so its <em>core</em> lands {@code step} along: it is three deep with its
	 * core in the middle ({@code getOffset() = 1}), so the click is one block nearer the turbine core.
	 */
	private static void placeGenerator(GameTestHelper helper, int x, int step) {
		place(helper, part("modular_turbine_3x3_generator"), step(x, step - 1));
	}

	/** Separator, steam port, blades, separator from {@code first}; returns the port. */
	private static MTComponentPortTE stage(GameTestHelper helper, int x, int first) {
		place(helper, part("modular_turbine_3x3_separator"), step(x, first));
		place(helper, part("modular_turbine_3x3_port_side"), step(x, first + 1));
		place(helper, part("modular_turbine_3x3_blades"), step(x, first + 2));
		place(helper, part("modular_turbine_3x3_separator"), step(x, first + 3));
		MTComponentPortTE port = (MTComponentPortTE) helper.getBlockEntity(step(x, first + 1));
		port.identifier = Fluids.STEAM;
		return port;
	}

	/** Forty ticks of a full steam tank. The core's simulation reads no world clock, so it can be stepped. */
	private static void run(MTCoreTE core, int ticks) {
		for(int i = 0; i < ticks; i++) {
			var stage = core.assemblies.get(0);
			stage.input.setFill(stage.input.getMaxFill());
			stage.output.setFill(0);
			core.updateEntity();
		}
	}

	/**
	 * <b>The generator is what makes power.</b> Two identical steam stages side by side, one with
	 * a 3x3 generator on the end. Without it the shaft's load coefficient is upstream's 0.000001 and
	 * the HE it makes truncates to 0; with it the load is 5.000001, the turbine makes power, and the
	 * same steam turns a loaded shaft more slowly. The electricity port on the generator reads it.
	 */
	@GameTest(template = ARENA)
	public static void theGeneratorIsWhatMakesPower(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE bare = coreAt(helper, 4);
		stage(helper, 4, 1);
		MTCoreTE loaded = coreAt(helper, 11);
		stage(helper, 11, 1);
		placeGenerator(helper, 11, 6);
		place(helper, part("modular_turbine_3x3_separator"), step(11, 8));

		AssemblyReturnCode a = bare.reassemble();
		AssemblyReturnCode b = loaded.reassemble();
		helper.assertTrue(a instanceof ReturnCodeSuccess && b instanceof ReturnCodeSuccess, "both should assemble, got "
				+ (a instanceof ReturnCodeError e ? e.reason : "ok") + " / " + (b instanceof ReturnCodeError e ? e.reason : "ok"));
		helper.assertTrue(loaded.length == 8 && loaded.components.size() == 6,
				"the generator is three blocks of shaft and one component: length " + loaded.length + ", " + loaded.components.size() + " parts");
		helper.assertTrue(Math.abs(loaded.weight - 10.5) < 1e-9, "weight is " + loaded.weight + ", wanted 10.3 + 0.1 + 0.1");

		run(bare, 40);
		run(loaded, 40);

		helper.assertTrue(bare.powerOutput == 0, "an unloaded shaft makes nothing, got " + bare.powerOutput);
		helper.assertTrue(loaded.powerOutput > 0, "a generator should make power, got " + loaded.powerOutput);
		helper.assertTrue(loaded.rps < bare.rps, "a loaded shaft turns slower: " + loaded.rps + " vs " + bare.rps);

		// The electricity port against the generator's west face reads the same number.
		BlockPos portPos = step(11, 6).west(2);
		helper.setBlock(portPos, LeafiaBlocks.modular_turbine_port.get().defaultBlockState()
				.setValue(MTPowerPortBlock.FACING, Direction.WEST));
		MTPowerPortTE port = (MTPowerPortTE) helper.getBlockEntity(portPos);
		long made = loaded.powerOutput;
		port.updateEntity();
		helper.assertTrue(port.getPower() == made, "the port should read " + made + ", got " + port.getPower());
		helper.succeed();
	}

	/**
	 * <b>An upstream bug, fixed (D-3075, was D-1061).</b> A steam port records where
	 * steam came in as its index in the core's <em>component list</em>, and the core compares that
	 * with <em>shaft positions</em>. The two agree until something longer than one block sits ahead
	 * of the port — and the generator is three long. Then the inlet is recorded two places early,
	 * the blade just after it is judged to be facing the wrong way, and a correctly built turbine
	 * reports a bad blade (and the turbulence that comes with it).
	 *
	 * <p>Two turbines: the same stage with and without a generator in front of it. Steam goes in
	 * through the port, as it would from a pipe, so the real {@code onFill} records it.
	 */
	@GameTest(template = ARENA)
	public static void aGeneratorAheadOfAPortDoesNotMisplaceTheInlet(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE plain = coreAt(helper, 4);
		MTComponentPortTE plainPort = stage(helper, 4, 1);

		MTCoreTE behind = coreAt(helper, 11);
		place(helper, part("modular_turbine_3x3_separator"), step(11, 1));
		placeGenerator(helper, 11, 3);
		MTComponentPortTE behindPort = stage(helper, 11, 5);

		helper.assertTrue(plain.reassemble() instanceof ReturnCodeSuccess, "precondition: plain assembles");
		helper.assertTrue(behind.reassemble() instanceof ReturnCodeSuccess, "precondition: behind assembles");

		plainPort.transferFluid(Fluids.STEAM, 0, 10_000);
		behindPort.transferFluid(Fluids.STEAM, 0, 10_000);

		int plainShaft = plain.assemblies.get(0).positionsInOrder.get(0);
		int behindShaft = behind.assemblies.get(0).positionsInOrder.get(0);
		helper.assertTrue(plain.assemblies.get(0).receivingPositions.contains(plainShaft),
				"with nothing long ahead, the inlet is recorded at its shaft position " + plainShaft);
		helper.assertTrue(behind.assemblies.get(0).receivingPositions.contains(behindShaft),
				"behind a generator the inlet is still recorded at its shaft position: "
						+ behind.assemblies.get(0).receivingPositions + ", shaft position " + behindShaft);

		plain.updateEntity();
		behind.updateEntity();
		helper.assertTrue(plain.assemblies.get(0).lastWrongBlades == 0, "the plain turbine's blade is right");
		helper.assertTrue(behind.assemblies.get(0).lastWrongBlades == 0,
				"the same blade behind a generator is judged right (D-3075)");
		helper.succeed();
	}

	/**
	 * <b>Every addon OBJ names a material</b>, or NeoForge bakes it to nothing (D-1062).
	 *
	 * <p>{@code ObjModel} only sets a mesh's material from a {@code usemtl} line, and a mesh with
	 * none adds no quads — no error, no missing texture, nothing logged. Eighteen of the addon's
	 * nineteen OBJs were like that and every one of them was invisible in game until T6's
	 * screenshot. A generator script fixes a copied-in OBJ; this catches the
	 * generator that copies one in and forgets.
	 *
	 * <p>Lists the model directory through the mod file's own file system, which is a directory
	 * in a dev run and the jar in a packed one. Not off the class path: there the URL is a
	 * {@code union:} one in some runs and a plain file in others, and the first version of this
	 * test failed on the protocol rather than on a model.
	 */
	@GameTest(template = EMPTY)
	public static void everyObjNamesAMaterial(GameTestHelper helper) {

		var dir = net.neoforged.fml.ModList.get().getModFileById(LeafiaRef.MODID).getFile()
				.findResource("assets", LeafiaRef.MODID, "models", "blocks");

		List<String> empty = new ArrayList<>();
		int count = 0;
		try(var files = java.nio.file.Files.list(dir)) {
			for(var obj : files.filter(f -> f.toString().endsWith(".obj")).toList()) {
				count++;
				boolean hasMaterial = java.nio.file.Files.readAllLines(obj).stream().anyMatch(l -> l.startsWith("usemtl"));
				if(!hasMaterial) empty.add(obj.getFileName().toString());
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		helper.assertTrue(count >= 19, "only " + count + " OBJs found");
		helper.assertTrue(empty.isEmpty(), "these bake to nothing - give each mesh a material: " + empty);
		helper.succeed();
	}
}
