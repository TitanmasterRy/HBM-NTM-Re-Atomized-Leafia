package com.leafia.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.machines.misc.modular_turbine.MTPacketId;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreBlock;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.AssemblyErrorReason;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.AssemblyReturnCode.ReturnCodeError;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The modular turbine. T1's packet keys, and T2's core: the steam table,
 * the work each steam is worth, the buffers, the governor, the solver, and the core block itself.
 *
 * <p>No test here builds a whole turbine — blades, separators and ports are T3 and T4 — so the
 * physics is pinned through the public static functions the tick is made of, and the block through
 * what a lone core does. Every number below is derived in its comment from upstream's constants
 * and the base mod's {@code FT_Coolable} values, which match NTM-CE's exactly (D-1058).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaTurbineTests {

	private static final String EMPTY = "empty";

	/**
	 * {@link MTPacketId}'s ids repeat, and the wire depends on it.
	 *
	 * <p>A {@code LeafiaPacket} key is scoped to the block entity it is sent from (D-1025), so the
	 * port's keys and the core's keys are two separate numberings sharing one enum. Both start at
	 * zero. Renumbering them into a single sequence — which is exactly what the file looks like it
	 * wants — would desync every turbine, and the compiler would not say a word.
	 *
	 * <p>So this asserts the two tables <em>and</em> that {@code id} has come loose from
	 * {@code ordinal()}, because reading the ordinal is the other way to make the same mistake.
	 */
	@GameTest(template = EMPTY)
	public static void packetIdsAreTwoTablesAndNotOrdinals(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();

		if(MTPacketId.PORT_IDENTIFIER.id != 0) problems.add("PORT_IDENTIFIER is " + MTPacketId.PORT_IDENTIFIER.id);
		if(MTPacketId.PORT_DECOMPRESSION.id != 1) problems.add("PORT_DECOMPRESSION is " + MTPacketId.PORT_DECOMPRESSION.id);

		MTPacketId[] core = {
				MTPacketId.CORE_ASSEMBLY_SYNC, MTPacketId.CORE_STEAM_SYNC,
				MTPacketId.CORE_TURBULENCE_REASONS, MTPacketId.CORE_WEIGHT,
				MTPacketId.CORE_TURBULENCE, MTPacketId.CORE_GENERATION,
				MTPacketId.CORE_GLOBAL_GEAR, MTPacketId.CORE_RPS, MTPacketId.CORE_OVERDRIVE,
		};

		for(int i = 0; i < core.length; i++) {
			if(core[i].id != i) problems.add(core[i] + " is " + core[i].id + ", wanted " + i);
		}

		// The two tables must overlap. If they ever stop, somebody has merged them.
		if(MTPacketId.PORT_IDENTIFIER.id != MTPacketId.CORE_ASSEMBLY_SYNC.id)
			problems.add("the port and core numberings no longer overlap; they are one table now");

		// And the ordinal must have come apart from the id, or reading either would work by
		// accident and the next person would read the wrong one.
		if(MTPacketId.CORE_ASSEMBLY_SYNC.id == MTPacketId.CORE_ASSEMBLY_SYNC.ordinal())
			problems.add("CORE_ASSEMBLY_SYNC's id and ordinal agree; ordinal() would read correct here "
					+ "and wrong everywhere else");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s) in the turbine's packet keys: " + String.join("; ", problems));
		helper.succeed();
	}

	/** A core block entity on its own, with no level - enough for anything that is pure arithmetic. */
	private static MTCoreTE looseCore() {
		return new MTCoreTE(LeafiaBlockEntities.MODULAR_TURBINE_CORE.get(), BlockPos.ZERO,
				LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
						.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE));
	}

	private static MTCoreTE placeCore(GameTestHelper helper, BlockPos pos, Direction dir) {
		helper.setBlock(pos, LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, dir));
		return (MTCoreTE) helper.getBlockEntity(pos);
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(b));
	}

	private static void expect(List<String> problems, String what, double actual, double wanted) {
		if(!close(actual, wanted)) problems.add(what + " is " + actual + ", wanted " + wanted);
	}

	/** The core, its item and its block entity exist exactly when both of its switches are on. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.modularTurbine();

		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("modular_turbine_core")) == on,
				"leafia:modular_turbine_core should exist exactly when turbines and addonFluids are both on");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("modular_turbine_core")) == on,
				"and so should its item");
		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("modular_turbine_core")) == on,
				"and its block entity type");
		helper.assertTrue(MTCoreTE.steamTypes.isEmpty() != on,
				"the steam table is filled exactly when the turbine registers, and empty otherwise");
		helper.succeed();
	}

	/**
	 * Upstream's six steams, most compressed first.
	 *
	 * <p>The order is balance, not presentation: {@code calculateBufferSize} raises 2.5 to a
	 * steam's index here and {@code getNextSteam} decompresses one step down the list. Upstream
	 * fills it from {@code AddonFluidTraits}, which here would sit behind {@code fluidTraits} — off
	 * by default — and leave the table empty on a default install (D-1058).
	 */
	@GameTest(template = EMPTY)
	public static void steamTableIsUpstreamsSixInOrder(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		List<FluidType> wanted = List.of(LeafiaFluids.DEATHSTEAM, Fluids.ULTRAHOTSTEAM, Fluids.SUPERHOTSTEAM,
				Fluids.HOTSTEAM, Fluids.STEAM, Fluids.SPENTSTEAM);

		helper.assertTrue(MTCoreTE.steamTypes.equals(wanted),
				"steam table is " + MTCoreTE.steamTypes.stream().map(FluidType::getName).toList());

		helper.assertTrue(MTCoreTE.getNextSteam(Fluids.HOTSTEAM, true) == Fluids.STEAM, "hot steam decompresses to steam");
		helper.assertTrue(MTCoreTE.getNextSteam(Fluids.HOTSTEAM, false) == Fluids.HOTSTEAM, "and without decompression stays itself");
		helper.assertTrue(MTCoreTE.getNextSteam(Fluids.SPENTSTEAM, true) == null, "spent steam is the end of the line");
		helper.assertTrue(MTCoreTE.getNextSteam(Fluids.WATER, true) == null, "and water is not on it at all");
		helper.succeed();
	}

	/**
	 * What each steam is worth, read off the base mod's {@code FT_Coolable} chain:
	 *
	 * <pre>
	 *   STEAM         -> SPENTSTEAM     100 : 1   heat 200
	 *   HOTSTEAM      -> STEAM            1 : 10  heat 2
	 *   SUPERHOTSTEAM -> HOTSTEAM         1 : 10  heat 18
	 *   ULTRAHOTSTEAM -> SUPERHOTSTEAM    1 : 10  heat 120     (turbine efficiency 1.0 on all four)
	 * </pre>
	 *
	 * Mass equivalent (spent steam = 1): 0.01, 0.1, 1, 10. Step work {@code heat / req}: 2, 2, 18,
	 * 120. Remaining work, step plus the next steam's times {@code prod / req}: 2, 22, 238, 2500.
	 * Per unit mass: 200, 220, 238, 250. A decompressing stage extracts the difference to the next
	 * steam down — 200, 20, 18, 12 — and a non-decompressing one 0.2 of the whole.
	 */
	@GameTest(template = EMPTY)
	public static void steamWorkFollowsTheCoolableChain(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		List<String> problems = new ArrayList<>();
		FluidType[] steams = { Fluids.STEAM, Fluids.HOTSTEAM, Fluids.SUPERHOTSTEAM, Fluids.ULTRAHOTSTEAM };
		double[] mass = { 0.01, 0.1, 1, 10 };
		double[] step = { 2, 2, 18, 120 };
		double[] remaining = { 2, 22, 238, 2500 };
		double[] specific = { 200, 220, 238, 250 };
		double[] decompressed = { 200, 20, 18, 12 };

		expect(problems, "SPENTSTEAM mass", MTCoreTE.getSteamMassEquivalent(Fluids.SPENTSTEAM), 1);
		for(int i = 0; i < steams.length; i++) {
			String n = steams[i].getName();
			expect(problems, n + " mass", MTCoreTE.getSteamMassEquivalent(steams[i]), mass[i]);
			expect(problems, n + " step work", MTCoreTE.getTurbineStepWork(steams[i]), step[i]);
			expect(problems, n + " remaining work", MTCoreTE.getSteamRemainingWork(steams[i]), remaining[i]);
			expect(problems, n + " specific work", MTCoreTE.getSteamSpecificRemainingWork(steams[i]), specific[i]);
			expect(problems, n + " decompressing stage", MTCoreTE.getStageSpecificWork(steams[i], true), decompressed[i]);
			expect(problems, n + " partial stage", MTCoreTE.getStageSpecificWork(steams[i], false), specific[i] * 0.2);
			expect(problems, n + " stage multiplier", MTCoreTE.getStageWorkMultiplier(steams[i]), 125);
		}

		helper.assertTrue(problems.isEmpty(), problems.size() + " wrong: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * {@code 2.5^index × blades × 8^((size-1)/2) × (4500 / 900) × 12}, spent steam over 1000.
	 *
	 * <p>900 is deathsteam's temperature — the first entry — because upstream overwrites the
	 * {@code mostCompression} argument with 0 ("ah forget it"). So the argument passed is ignored,
	 * which the {@code 99} below checks.
	 *
	 * <pre>
	 *   STEAM (4), 4 blades, 3x3:   39.0625 × 4 × 8 × 5 × 12            = 75,000
	 *   SPENTSTEAM (5), 16, 3x3:    97.65625 × 16 × 8 × 5 × 12 / 1000   = 750
	 *   HOTSTEAM (3), 1, 9x9:       15.625 × 1 × 4096 × 5 × 12          = 3,840,000
	 * </pre>
	 */
	@GameTest(template = EMPTY)
	public static void bufferSizesAreUpstreamsFormula(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = looseCore();
		List<String> problems = new ArrayList<>();
		expect(problems, "STEAM 4 blades 3x3", core.calculateBufferSize(Fluids.STEAM, 99, 4, 3), 75_000);
		expect(problems, "SPENTSTEAM 16 blades 3x3", core.calculateBufferSize(Fluids.SPENTSTEAM, 99, 16, 3), 750);
		expect(problems, "HOTSTEAM 1 blade 9x9", core.calculateBufferSize(Fluids.HOTSTEAM, 0, 1, 9), 3_840_000);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Radius {@code max(size, 2) × 0.35}, so a 2x2 and a "1x1" share one; inlet whirl
	 * {@code sqrt(2w) × 32}, so 200 of specific work is {@code 20 × 32 = 640}; and the governed
	 * speed is the config's 60 plus 10, 25 or 40 per overdrive tier.
	 */
	@GameTest(template = EMPTY)
	public static void radiusWhirlAndGovernedSpeed(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		List<String> problems = new ArrayList<>();
		expect(problems, "radius of 1", MTCoreTE.getStageRadius(1), 0.7);
		expect(problems, "radius of 2", MTCoreTE.getStageRadius(2), 0.7);
		expect(problems, "radius of 9", MTCoreTE.getStageRadius(9), 3.15);
		expect(problems, "whirl of 200", MTCoreTE.getStageInletWhirl(200, MTCoreTE.INLET_WHIRL_COEFFICIENT), 640);
		expect(problems, "whirl of nothing", MTCoreTE.getStageInletWhirl(0, MTCoreTE.INLET_WHIRL_COEFFICIENT), 0);

		int[] boosts = { 0, 10, 25, 40, 0 };
		for(int tier = 0; tier < boosts.length; tier++)
			expect(problems, "overdrive " + tier, MTCoreTE.getRPSBoost(tier), boosts[tier]);

		MTCoreTE core = looseCore();
		core.overdrive = 2;
		expect(problems, "governed rps at overdrive 2", core.getGovernedRPS(), LeafiaConfig.governedRPS + 25);
		expect(problems, "the config default", LeafiaConfig.governedRPS, 60);

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The governor wants full admission from a standstill but may only open it 0.02 a tick, so ten
	 * ticks are exactly 0.2. Its integral does not wind up meanwhile: the proportional term alone
	 * (60 × 0.04 = 2.4) already saturates it, which is the anti-windup branch.
	 */
	@GameTest(template = EMPTY)
	public static void governorOpensTwoPercentPerTick(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = looseCore();
		double admission = 0;
		for(int i = 0; i < 10; i++) admission = core.stepGovernorForTest();

		helper.assertTrue(close(admission, 0.2), "admission after ten ticks is " + admission + ", wanted 0.2");
		helper.succeed();
	}

	/**
	 * The equilibrium solver against the torque law typed out independently: for a flat 100 of
	 * drive, the speed it returns must leave {@code 100 - 1e-6·ω - (0.7·tanh(r/0.25) + 0.05r) -
	 * 0.01·r|r|} at zero. And with no drive it answers zero rather than searching.
	 */
	@GameTest(template = EMPTY)
	public static void equilibriumSolverZeroesTheTorqueLaw(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		helper.assertTrue(MTCoreTE.solveBaseEquilibriumRPS(0, 0) == 0, "no drive, no speed");

		double r = MTCoreTE.solveBaseEquilibriumRPS(100, 0);
		double omega = r * Math.PI * 2;
		double net = 100 - 0.000001 * omega - (0.7 * Math.tanh(r / 0.25) + 0.05 * r) - 0.01 * r * Math.abs(r);

		helper.assertTrue(r > 90 && r < 100, "a flat 100 of drive settles near 97 rps, got " + r);
		helper.assertTrue(Math.abs(net) < 1e-6, "net torque at " + r + " rps is " + net + ", wanted 0");
		helper.succeed();
	}

	/**
	 * A core with nothing in front of it is an open end — the walk's first step finds no turbine
	 * part, and nothing before it was a separator. It stays disassembled.
	 */
	@GameTest(template = EMPTY)
	public static void aLoneCoreReportsAnOpenEnd(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper, new BlockPos(2, 2, 2), Direction.NORTH);
		AssemblyReturnCode code = core.reassemble();

		helper.assertTrue(code instanceof ReturnCodeError error && error.reason == AssemblyErrorReason.OPEN_END,
				"a lone core should fail with OPEN_END");
		helper.assertTrue(core.components.isEmpty() && core.length == 0 && core.weight == 0,
				"and stay disassembled");
		helper.succeed();
	}

	/**
	 * Every assembly error a player can be told about has a chat line. The key is built from the
	 * enum's name at runtime, so a new reason without a line would print a raw key and nothing
	 * would say so. Read from the jar's own lang file, because a dedicated server cannot translate.
	 */
	@GameTest(template = EMPTY)
	public static void everyAssemblyErrorHasAChatLine(GameTestHelper helper) {

		JsonObject lang;
		try(InputStream in = LeafiaTurbineTests.class.getResourceAsStream("/assets/leafia/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json is not on the classpath");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}

		List<String> missing = new ArrayList<>();
		for(AssemblyErrorReason reason : AssemblyErrorReason.values()) {
			if(!lang.has(MTCoreBlock.errorKey(reason))) missing.add(MTCoreBlock.errorKey(reason));
		}
		helper.assertTrue(missing.isEmpty(), "no chat line for " + missing);
		helper.succeed();
	}

	/**
	 * Speed, turbulence and the upgrade survive a save, and the assembly goes with them to be
	 * re-walked on the next tick rather than at load — upstream's {@code queueFunction}.
	 */
	@GameTest(template = EMPTY)
	public static void coreSurvivesASave(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE core = placeCore(helper, new BlockPos(2, 2, 2), Direction.EAST);
		core.rps = 42.5;
		core.turbulence = 13.25;
		core.overdrive = 3;

		var registries = helper.getLevel().registryAccess();
		CompoundTag saved = core.saveWithoutMetadata(registries);

		MTCoreTE loaded = looseCore();
		loaded.loadWithComponents(saved, registries);

		helper.assertTrue(loaded.rps == 42.5 && loaded.turbulence == 13.25 && loaded.overdrive == 3,
				"rps, turbulence and overdrive did not round-trip");
		helper.assertTrue(saved.contains("assembly") && saved.getCompound("assembly").getInt("length") == 0,
				"the (empty) assembly is saved too");
		helper.succeed();
	}

	/**
	 * Breaking a turbine part disassembles the turbine; a chunk unloading does not. 1.21.1 calls
	 * {@code setRemoved} for both, and a literal port of upstream's {@code invalidate} would take a
	 * turbine apart every time a player walked away from half of it.
	 */
	@GameTest(template = EMPTY)
	public static void unloadingIsNotBreaking(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		MTCoreTE broken = looseCore();
		broken.length = 7;
		broken.weight = 3;
		broken.setRemoved();
		helper.assertTrue(broken.length == 0 && broken.weight == 0, "a broken core must disassemble");

		MTCoreTE unloaded = looseCore();
		unloaded.length = 7;
		unloaded.weight = 3;
		unloaded.onChunkUnloaded();
		unloaded.setRemoved();
		helper.assertTrue(unloaded.length == 7 && unloaded.weight == 3, "an unloading core must not");
		helper.succeed();
	}

	/**
	 * An overdrive upgrade goes in once: the first is taken and sets the tier, a second while one
	 * is fitted is refused. Breaking the core gives it back.
	 */
	@GameTest(template = EMPTY)
	public static void overdriveGoesInOnceAndComesBackOnBreak(GameTestHelper helper) {

		if(!LeafiaFeatures.modularTurbine()) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(2, 2, 2);
		MTCoreTE core = placeCore(helper, pos, Direction.SOUTH);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

		ItemStack first = new ItemStack(ModItems.upgrade_overdrive_2.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, first);
		helper.getLevel().getBlockState(abs).useItemOn(first, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(core.overdrive == 2 && first.isEmpty(), "the first upgrade should go in and be used up");

		ItemStack second = new ItemStack(ModItems.upgrade_overdrive_3.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, second);
		helper.getLevel().getBlockState(abs).useItemOn(second, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(core.overdrive == 2 && second.getCount() == 1, "a second must be refused while one is fitted");

		helper.getLevel().destroyBlock(abs, false);
		boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2),
				e -> e.getItem().is(ModItems.upgrade_overdrive_2.get())).isEmpty();
		helper.assertTrue(dropped, "breaking the core should drop the fitted upgrade");
		helper.succeed();
	}
}
