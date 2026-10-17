package com.leafia.gametest;

import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.uninos.UniNodespace;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.network.fluid.FluidDuctEquipmentBase;
import com.leafia.contents.network.fluid.FluidDuctEquipmentTE;
import com.leafia.contents.network.fluid.valves.FluidDuctValveTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid valves: the claim is that shutting one <em>removes the block from the graph</em>,
 * not that it stops fluid at the block.
 *
 * <p>That distinction is the whole mechanism and it is invisible from outside — a valve that
 * merely refused to pass fluid would look identical from the player's side and would leave the
 * two halves of the run on one network, so anything that walks the network (a gauge, a debug
 * readout, a machine counting its neighbours) would still see through it. So the tests check
 * the node, not the fluid.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaValveTests {

	private static final String EMPTY = "empty";

	/** A closed valve has no node; opening it makes one; closing it takes it away again. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void shuttingAValveRemovesItFromTheNetwork(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.fluid_duct_valve_mdl.get());

		TickWindow.runAfterDelay(helper, 5, () -> {

			BlockPos abs = helper.absolutePos(pos);
			FluidDuctValveTE valve = (FluidDuctValveTE) helper.getBlockEntity(pos);

			helper.assertTrue(!valve.isOpen(), "a valve starts shut, as upstream's does");
			helper.assertTrue(node(helper, abs) == null,
					"a shut valve should have no node at all - that is what cuts the run");

			valve.setOpen(true);

			helper.runAfterDelay(5, () -> {

				helper.assertTrue(node(helper, abs) != null,
						"an open valve should have joined the fluid network");

				valve.setOpen(false);

				helper.runAfterDelay(5, () -> {
					helper.assertTrue(node(helper, abs) == null,
							"shutting it again should destroy the node, not merely stop the flow");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The valve is an ordinary duct: it joins the <em>base mod's</em> network, and base-mod pipes
	 * connect to it.
	 *
	 * <p>Worth asserting separately from the amat pipe's mirror-image test — these two blocks
	 * are built on the same base class and want opposite answers, so a change that fixed one
	 * could silently swap the other.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void anOpenValveJoinsTheBaseModsOwnNetwork(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		BlockPos valvePos = new BlockPos(1, 1, 1);
		helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.fluid_duct_neo.get());
		helper.setBlock(valvePos, LeafiaBlocks.fluid_duct_valve_mdl.get());

		TickWindow.runAfterDelay(helper, 5, () -> {

			((FluidDuctValveTE) helper.getBlockEntity(valvePos)).setOpen(true);

			helper.runAfterDelay(10, () -> {

				helper.assertTrue(node(helper, helper.absolutePos(valvePos)) != null,
						"an open valve belongs on the base mod's fluid network, not a network of its own");

				helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 1))
						.getValue(com.hbm.blocks.network.FluidDuctStandard.WEST),
						"and a base-mod duct beside it should connect to it");

				helper.succeed();
			});
		});
	}

	/** The redstone valve follows the signal rather than a click. */
	@GameTest(template = EMPTY, timeoutTicks = 400)
	public static void theRedstoneValveFollowsItsSignal(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockPos lever = new BlockPos(1, 1, 2);

		helper.setBlock(pos, LeafiaBlocks.fluid_duct_valve_mdl_rs.get());

		TickWindow.runAfterDelay(helper, 5, () -> {

			FluidDuctValveTE valve = (FluidDuctValveTE) helper.getBlockEntity(pos);
			helper.assertTrue(!valve.isOpen(), "unpowered, it is shut");

			helper.setBlock(lever, Blocks.REDSTONE_BLOCK);

			helper.runAfterDelay(5, () -> {

				helper.assertTrue(valve.isOpen(), "powered, it should have opened");
				helper.assertTrue(node(helper, helper.absolutePos(pos)) != null,
						"and joined the network with it");

				helper.setBlock(lever, Blocks.AIR);

				helper.runAfterDelay(5, () -> {
					helper.assertTrue(!valve.isOpen(), "unpowered again, it should have shut");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Upstream's placement axis, <b>bug included</b>.
	 *
	 * <p>{@code FluidDuctEquipmentBase.getAxis} tests {@code face == 0 || face == 2} while
	 * {@code face} is only ever -1, 0 or 1 — so the {@code == 2} arm is dead and a level
	 * placement always yields X, whichever way the pipe actually runs. D-021 keeps it; this
	 * asserts it so a later "obvious" fix has to be a deliberate one. See D-1016.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void placementAxisReproducesUpstreamsBug(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.fluid_duct_valve_mdl.get());

		TickWindow.runAfterDelay(helper, 3, () -> {

			FluidDuctEquipmentTE duct = (FluidDuctEquipmentTE) helper.getBlockEntity(pos);
			BlockPos abs = helper.absolutePos(pos);

			// face 0, not vertical: a level placement. Upstream answers X for every horizontal
			// facing, which is the bug - `direction` is never consulted.
			duct.face = 0;
			duct.vertical = false;
			for(Direction facing : Direction.Plane.HORIZONTAL) {
				duct.direction = facing;
				helper.assertTrue(FluidDuctEquipmentBase.getAxis(helper.getLevel(), abs) == Direction.Axis.X,
						"upstream answers X for a level placement facing " + facing
								+ " - if this now says " + facing.getAxis() + ", the bug was fixed on purpose (D-1016)");
			}

			duct.vertical = true;
			helper.assertTrue(FluidDuctEquipmentBase.getAxis(helper.getLevel(), abs) == Direction.Axis.Y,
					"vertical always wins");

			duct.vertical = false;
			duct.face = 1;
			helper.assertTrue(FluidDuctEquipmentBase.getAxis(helper.getLevel(), abs) == Direction.Axis.Z,
					"a steep placement falls through to Z");

			helper.succeed();
		});
	}

	/**
	 * The handwheel's quarter turn, and which end of it is "shut".
	 *
	 * <p>Upstream computes this inside its renderer, where nothing can reach it — a renderer is
	 * client-only and a GameTest server cannot load one. It lives on the block entity here for
	 * exactly that reason, so the three numbers in it (ten steps, nine degrees, and the
	 * subtraction that puts the turned wheel on the <em>closed</em> valve) are pinned rather
	 * than eyeballed. See D-1021.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 200)
	public static void theHandwheelTurnsAQuarterAndShutIsTheTurnedEnd(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, LeafiaBlocks.fluid_duct_valve_mdl.get());

		TickWindow.runAfterDelay(helper, 3, () -> {

			FluidDuctValveTE valve = (FluidDuctValveTE) helper.getBlockEntity(pos);

			valve.localAngle = valve.localAngleLast = 0;
			helper.assertTrue(valve.wheelAngle(1F) == 90F,
					"a shut valve has its wheel turned a quarter, not left at zero");

			valve.localAngle = valve.localAngleLast = FluidDuctValveTE.WHEEL_STEPS;
			helper.assertTrue(valve.wheelAngle(1F) == 0F, "and an open one sits at zero");

			// Mid-swing, the frame is interpolated between the last tick and this one.
			valve.localAngleLast = 4;
			valve.localAngle = 5;
			helper.assertTrue(valve.wheelAngle(0F) == (10 - 4) * 9F, "partialTick 0 is last tick");
			helper.assertTrue(valve.wheelAngle(1F) == (10 - 5) * 9F, "partialTick 1 is this tick");
			helper.assertTrue(valve.wheelAngle(0.5F) == (10 - 4.5F) * 9F, "and it is linear between");

			helper.succeed();
		});
	}

	/**
	 * Both valves are drawn by {@code FluidDuctEquipmentRender} and by nothing else.
	 *
	 * <p>Upstream's {@code getRenderType} is {@code ENTITYBLOCK_ANIMATED}, so this is the
	 * regression guard for the stand-in that used to be here: a static multipart blockstate model
	 * stood in for the renderer, and if the render shape ever goes back to {@code MODEL} the
	 * valve is drawn twice — once by the chunk mesh and once by the renderer. See D-1021.
	 */
	@GameTest(template = EMPTY)
	public static void bothValvesAreDrawnByTheirRenderer(GameTestHelper helper) {

		if(!LeafiaFeatures.fluidDuctEquipment) { helper.succeed(); return; }

		for(var block : new net.minecraft.world.level.block.Block[] {
				LeafiaBlocks.fluid_duct_valve_mdl.get(), LeafiaBlocks.fluid_duct_valve_mdl_rs.get() }) {

			helper.assertTrue(block.defaultBlockState().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED,
					BuiltInRegistries.BLOCK.getKey(block) + " is drawn by its block entity renderer, not by a"
							+ " blockstate model - a MODEL shape here means it is drawn twice (D-1021)");
		}

		helper.succeed();
	}

	/** Both valves and both block entity types follow the switch. */
	@GameTest(template = EMPTY)
	public static void featureSwitchAgreesWithWhatWasRegistered(GameTestHelper helper) {

		boolean on = LeafiaFeatures.fluidDuctEquipment;

		for(String name : new String[] { "fluid_duct_valve_mdl", "fluid_duct_valve_mdl_rs" }) {
			ResourceLocation id = LeafiaRef.rl(name);
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id) == on,
					"leafia:" + name + " should exist exactly when fluidDuctEquipment is on");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) == on,
					"its BlockItem too");
		}

		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("fluid_duct_valve")) == on,
				"and the block entity types - a disabled feature registers nothing (D-1002)");
		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(LeafiaRef.rl("fluid_duct_valve_rs")) == on,
				"both of them");

		helper.succeed();
	}

	private static Object node(GameTestHelper helper, BlockPos abs) {
		return UniNodespace.getNode(helper.getLevel(), abs, Fluids.NONE.getNetworkProvider());
	}
}
