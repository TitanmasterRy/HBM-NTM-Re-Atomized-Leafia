package com.leafia.gametest;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.uninos.UniNodespace;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.network.pipe_amat.AmatDuctStandard;
import com.leafia.contents.network.pipe_amat.AmatDuctTE;
import com.leafia.contents.network.pipe_amat.charger.AmatDuctChargerTE;
import com.leafia.contents.network.pipe_amat.uninos.AmatNet;
import com.leafia.contents.network.pipe_amat.uninos.AmatNode;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The antimatter pipe: its network, its node, and the rule that keeps it off the base mod's.
 *
 * <p>The claim worth testing here is not "a pipe places". It is that {@code amat_duct} forms a
 * <em>separate</em> network from {@code fluid_duct_neo} carrying the same fluid — which is the
 * whole point of the block and would fail silently if it broke: the pipes would look joined,
 * fluid would flow, and the containment the amat net exists to enforce would simply not apply.
 * Two things have to hold for that, and they are tested separately because either alone looks
 * fine: the nodes must be created against different providers, and the blockstate must not draw
 * a connection.
 *
 * <p>Numbers are pinned the same way {@code LeafiaFluidTraitTests} pins the fluid table:
 * {@link AmatNet#consumption} is written {@code 1000/20} upstream, integer division, and a
 * later "tidy-up" to {@code 1000/20D} would change the field's lifetime without changing
 * anything visible.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaAmatPipeTests {

	private static final String EMPTY = "empty";

	/** Upstream's field numbers, exactly. */
	@GameTest(template = EMPTY)
	public static void fieldConstantsMatchUpstream(GameTestHelper helper) {

		helper.assertTrue(AmatNet.consumption == 50L,
				"consumption is upstream's 1000/20 = 50 (integer division), got " + AmatNet.consumption);
		helper.assertTrue(AmatNet.maxPower == 1000L,
				"maxPower is consumption * 20 = 1000, got " + AmatNet.maxPower);
		helper.assertTrue(new AmatNet(Fluids.AMAT).power == AmatNet.maxPower,
				"a fresh net starts on a full charge");

		helper.succeed();
	}

	/** One provider per fluid, and the same object every time — the nodespace keys its map on it. */
	@GameTest(template = EMPTY)
	public static void oneProviderPerFluidAndNotTheBaseMods(GameTestHelper helper) {

		helper.assertTrue(AmatNet.getProvider(Fluids.AMAT) == AmatNet.getProvider(Fluids.AMAT),
				"the same fluid must resolve to the same provider object, or two nodes at the "
						+ "same position would not find each other");
		helper.assertTrue(AmatNet.getProvider(Fluids.AMAT) != AmatNet.getProvider(Fluids.DEUTERIUM),
				"different fluids must get different providers, as they do on the base fluid net");
		helper.assertTrue(!AmatNet.getProvider(Fluids.AMAT).equals((Object) Fluids.AMAT.getNetworkProvider()),
				"the amat provider must NOT be the fluid's own - that is what keeps an amat pipe "
						+ "off the plain duct network carrying the same fluid");

		helper.succeed();
	}

	/**
	 * A placed pipe makes a node, and it makes it on the amat network.
	 *
	 * <p>The second half is the one that matters: looking the node up under the fluid's own
	 * provider has to come back empty, or the pipe has joined the base mod's network.
	 */
	@GameTest(template = EMPTY)
	public static void placedPipeCreatesItsNodeOnTheAmatNetwork(GameTestHelper helper) {

		if(!LeafiaFeatures.amatPipes) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.amat_duct.get());

		helper.succeedWhen(() -> {

			BlockPos abs = helper.absolutePos(pos);

			helper.assertTrue(helper.getBlockEntity(pos) instanceof AmatDuctTE,
					"the block entity should be an AmatDuctTE");

			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), abs,
					AmatNet.getProvider(Fluids.NONE)) != null,
					"the pipe should have created a node on the amat network");

			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), abs,
					Fluids.NONE.getNetworkProvider()) == null,
					"the pipe must NOT have created a node on the base mod's fluid network");
		});
	}

	/** Breaking the pipe takes its node with it. A leaked node fails silently and forever. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void breakingThePipeDestroysItsNode(GameTestHelper helper) {

		if(!LeafiaFeatures.amatPipes) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.amat_duct.get());

		TickWindow.runAfterDelay(helper, 5, () -> {

			BlockPos abs = helper.absolutePos(pos);
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), abs, AmatNet.getProvider(Fluids.NONE)) != null,
					"precondition: the node exists before the block is broken");

			helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);

			helper.runAfterDelay(5, () -> {
				helper.assertTrue(UniNodespace.getNode(helper.getLevel(), abs, AmatNet.getProvider(Fluids.NONE)) == null,
						"the node should be gone with the block");
				helper.succeed();
			});
		});
	}

	/**
	 * An amat pipe does not connect to a base-mod duct, in either arrangement.
	 *
	 * <p>Both orders are placed because the two halves of the rule live in different classes and
	 * only one of them is exercised by each: {@link AmatDuctTE#canConnect} answers when the
	 * plain duct asks, and {@code AmatDuctStandard.connectsTo} answers when the amat pipe does.
	 * A regression in either would leave this passing if only one arrangement were built.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void amatPipeDoesNotJoinAPlainDuct(GameTestHelper helper) {

		if(!LeafiaFeatures.amatPipes) { helper.succeed(); return; }

		BlockPos amat = new BlockPos(1, 1, 1);
		BlockPos plainEast = new BlockPos(2, 1, 1);
		BlockPos amatWest = new BlockPos(0, 1, 1);

		helper.setBlock(plainEast, ModBlocks.fluid_duct_neo.get());
		helper.setBlock(amat, LeafiaBlocks.amat_duct.get());
		helper.setBlock(amatWest, LeafiaBlocks.amat_duct.get());

		TickWindow.runAfterDelay(helper, 10, () -> {

			BlockState state = helper.getBlockState(amat);

			helper.assertTrue(!state.getValue(AmatDuctStandard.EAST),
					"an amat pipe must not connect east to a plain fluid duct");
			helper.assertTrue(state.getValue(AmatDuctStandard.WEST),
					"an amat pipe must connect west to another amat pipe carrying the same fluid");

			BlockState plain = helper.getBlockState(plainEast);
			helper.assertTrue(!plain.getValue(com.hbm.blocks.network.FluidDuctStandard.WEST),
					"and the plain duct must not connect back to it either");

			helper.succeed();
		});
	}

	/**
	 * The generated blockstate covers every state, and carries upstream's Z swap.
	 *
	 * <p>A generator script writes 64 variants from a rule transcribed out
	 * of upstream's {@code AmatDuctBakedModel.buildWorldQuads} — including its swap, where
	 * {@code if (pZ) parts.add("nZ")} makes a pipe connected north draw its <em>south</em> stub.
	 * Upstream's own comment says it "mirrors original", so D-021 keeps it; this pins it so a
	 * later tidy-up of the generator fails here rather than quietly changing how every pipe in
	 * the world looks.
	 *
	 * <p>Read off the classpath rather than through the resource manager, because a dedicated
	 * server has no resource packs — which is also why nothing here can say the model
	 * <em>renders</em>. That still needs a client.
	 */
	@GameTest(template = EMPTY)
	public static void generatedBlockstateCoversEveryStateAndKeepsTheZSwap(GameTestHelper helper) {

		JsonObject variants;
		try(InputStreamReader in = new InputStreamReader(
				LeafiaAmatPipeTests.class.getResourceAsStream("/assets/leafia/blockstates/amat_duct.json"),
				StandardCharsets.UTF_8)) {
			variants = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("variants");
		} catch(Exception e) {
			helper.fail("could not read the generated blockstate: " + e);
			return;
		}

		helper.assertTrue(variants.size() == 64,
				"a six-boolean pipe has 64 states and every one needs a variant, got " + variants.size());

		// north + up. Not one of the single-axis branches, so the general one applies: pY for up,
		// and north's nZ swapped to pZ. Without the swap this would be amat_duct_pynzcore.
		String key = "down=false,east=false,north=true,south=false,up=true,west=false";
		String model = variants.getAsJsonObject(key).get("model").getAsString();
		helper.assertTrue("leafia:block/amat_duct_pypzcore".equals(model),
				"north+up should draw pY and the SWAPPED pZ (upstream's bug, preserved), got " + model);

		// An isolated pipe shows only its core - upstream's rule, and not the base mod's, which
		// shows all six arms for the same mask.
		String lone = variants.getAsJsonObject(
				"down=false,east=false,north=false,south=false,up=false,west=false").get("model").getAsString();
		helper.assertTrue("leafia:block/amat_duct_core".equals(lone),
				"an unconnected amat pipe shows its core alone, got " + lone);

		helper.succeed();
	}

	/** Upstream's charger buffer, exactly. */
	@GameTest(template = EMPTY)
	public static void chargerBufferMatchesUpstream(GameTestHelper helper) {

		helper.assertTrue(AmatDuctChargerTE.maxPower == 10000L,
				"the charger holds upstream's 10000 HE, got " + AmatDuctChargerTE.maxPower);

		helper.succeed();
	}

	/**
	 * The field runs down on its own, and a charged charger holds it up.
	 *
	 * <p>This is the mechanic, and both halves are asserted because each alone is satisfiable by
	 * a broken implementation: a net that never drains looks the same as one being refilled, and
	 * a charger that transfers nothing looks the same as a net that does not drain. So the empty
	 * charger has to end at zero field and the full one has to end above it.
	 *
	 * <p>Twenty-five ticks, because {@link AmatNet#maxPower} is exactly twenty ticks of
	 * {@link AmatNet#consumption} — a shorter run would not distinguish "drains" from "drains
	 * slowly".
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void anEmptyFieldDrainsAndAChargerHoldsItUp(GameTestHelper helper) {

		if(!LeafiaFeatures.amatPipes) { helper.succeed(); return; }

		BlockPos flat = new BlockPos(1, 1, 1);
		BlockPos charged = new BlockPos(3, 1, 1);

		helper.setBlock(flat, LeafiaBlocks.amat_duct_charger.get());
		helper.setBlock(charged, LeafiaBlocks.amat_duct_charger.get());

		TickWindow.runAfterDelay(helper, 2, () -> {

			if(helper.getBlockEntity(charged) instanceof AmatDuctChargerTE te) te.setPower(AmatDuctChargerTE.maxPower);

			helper.runAfterDelay(25, () -> {

				AmatNet flatNet = netAt(helper, flat);
				AmatNet chargedNet = netAt(helper, charged);

				helper.assertTrue(flatNet != null && chargedNet != null,
						"both chargers should be on an amat network by now");
				helper.assertTrue(flatNet != chargedNet,
						"the two are not adjacent, so they must be on separate networks - otherwise "
								+ "this test would be measuring one net twice");

				helper.assertTrue(flatNet.power == 0,
						"an uncharged charger cannot hold the field: it should have drained to 0, got "
								+ flatNet.power);
				helper.assertTrue(chargedNet.power > 0,
						"a charged charger should be holding the field up, got " + chargedNet.power);
				helper.assertTrue(chargedNet.power <= AmatNet.maxPower,
						"and must never overfill it past " + AmatNet.maxPower + ", got " + chargedNet.power);

				AmatDuctChargerTE te = (AmatDuctChargerTE) helper.getBlockEntity(charged);
				helper.assertTrue(te.getStoredPower() < AmatDuctChargerTE.maxPower,
						"the charger should have spent some of its buffer doing it, still has "
								+ te.getStoredPower());

				helper.succeed();
			});
		});
	}

	/** A pipe connects to a charger, because the charger is a duct as well as a machine. */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void aPipeConnectsToTheCharger(GameTestHelper helper) {

		if(!LeafiaFeatures.amatPipes) { helper.succeed(); return; }

		BlockPos pipe = new BlockPos(1, 1, 1);
		helper.setBlock(new BlockPos(2, 1, 1), LeafiaBlocks.amat_duct_charger.get());
		helper.setBlock(pipe, LeafiaBlocks.amat_duct.get());

		TickWindow.runAfterDelay(helper, 10, () -> {
			helper.assertTrue(helper.getBlockState(pipe).getValue(AmatDuctStandard.EAST),
					"an amat pipe should connect to a charger beside it");
			helper.succeed();
		});
	}

	private static AmatNet netAt(GameTestHelper helper, BlockPos pos) {
		Object node = UniNodespace.getNode(helper.getLevel(), helper.absolutePos(pos),
				AmatNet.getProvider(Fluids.NONE));
		return node instanceof AmatNode amat ? amat.net : null;
	}

	/** The block, its item and its block entity type all exist exactly when the switch is on. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.amatPipes;

		for(String name : new String[] { "amat_duct", "amat_duct_charger" }) {

			ResourceLocation id = LeafiaRef.rl(name);

			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id) == on,
					"leafia:" + name + " should be in the block registry exactly when the switch is on");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) == on,
					"its BlockItem should follow the same switch");
			helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id) == on,
					"and so should its BlockEntityType - a disabled feature registers nothing (D-1002)");
		}

		helper.succeed();
	}
}
