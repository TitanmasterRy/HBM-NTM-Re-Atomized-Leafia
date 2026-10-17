package com.leafia.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryTE;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlBlock;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlTE;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRByproduct;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.Reaction;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The molten-salt reactor's components (D-3012): the fuel table, the salt's NBT, the flow between
 * blocks, the reaction and its geometry, decay, the control rod, the plug's melt and spill, and the
 * arbitrary block.
 *
 * <p>Every test sets a block entity's state and calls its code in the same body, so no tick runs in
 * between; none probes the one random roll, the sellafield chance, only its
 * threshold (D-3004).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaMSRTests {

	private static final String EMPTY16 = "empty16";
	private static final double EPS = 1e-9;

	private static boolean on() {
		return LeafiaFeatures.lftrReactor() && LeafiaFF.enabled() && LeafiaFluids.FLUORIDE != null;
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= EPS * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	/** FLiBe of {@code mB}, at {@code heat}, with one fuel in it (or none). */
	static FluidStack salt(int mB, double heat, String fuel, double amount) {
		FluidStack s = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, mB);
		CompoundTag tag = MSRTEBase.nbtProtocol((CompoundTag) null);
		tag.putDouble("heat", heat);
		if(fuel != null) {
			Map<String, Double> m = new LinkedHashMap<>();
			m.put(fuel, amount);
			MSRTEBase.writeMixture(m, tag);
		}
		LeafiaFF.setTag(s, tag);
		return s;
	}

	private static double heat(MSRTEBase te) {
		return MSRTEBase.nbtProtocol(te.tank.getFluid()).getDouble("heat");
	}

	private static Map<String, Double> mixture(MSRTEBase te) {
		return MSRTEBase.readMixture(MSRTEBase.nbtProtocol(te.tank.getFluid()));
	}

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

	// ------------------------------------------------------------------ the table

	/** Every number in upstream's MSRFuel and MSRByproduct. */
	@GameTest(template = EMPTY16)
	public static void theFuelTableIsUpstreams(GameTestHelper helper) {
		double x = 1234.5;
		near(helper, MSRFuel.th232.function.apply(x), Math.pow(x, 0.3), "th232 x^0.3");
		near(helper, MSRFuel.pa233.function.apply(x), Math.pow(x, 0.3), "pa233 x^0.3");
		near(helper, MSRFuel.u233.function.apply(x), Math.pow(x, 0.85), "u233 x^0.85");
		near(helper, MSRFuel.u235.function.apply(x), Math.pow(x, 0.75), "u235 x^0.75");
		near(helper, MSRFuel.pa234.function.apply(x), Math.pow(x, 0.15), "pa234 x^0.15");
		helper.assertTrue(MSRFuel.th232.funcString.equals("x^0.3/B") && MSRFuel.u233.funcString.equals("x^0.85/B")
				&& MSRFuel.u235.funcString.equals("x^0.75/B") && MSRFuel.pa234.funcString.equals("x^0.15/B")
				&& MSRFuel.pa233.funcString.equals("x^0.3/B"), "the heat-function strings");
		near(helper, MSRFuel.th232.life, 30000000d, "th232 life");
		near(helper, MSRFuel.pa233.life, 10000d, "pa233 life");
		near(helper, MSRFuel.u233.life, 600000000d, "u233 life");
		near(helper, MSRFuel.u235.life, 0, "u235 has no byproduct");
		near(helper, MSRFuel.pa233.decayRate, 0.03, "pa233 decays at 0.03");
		for(MSRFuel f : MSRFuel.values())
			if(f != MSRFuel.pa233) near(helper, f.decayRate, 0, f + " does not decay");
		near(helper, MSRFuel.th232.heatPerFlux, 0.3, "th232 heatPerFlux (scrapped)");
		helper.assertTrue(MSRFuel.th232.byproduct == MSRByproduct.th232 && MSRFuel.pa233.byproduct == MSRByproduct.pa233
				&& MSRFuel.u233.byproduct == MSRByproduct.u233 && MSRFuel.u235.byproduct == null && MSRFuel.pa234.byproduct == null,
				"the byproduct links");
		helper.assertTrue(MSRFuel.pa233.decayProduct == MSRByproduct.pa233_decay, "pa233 decays through pa233_decay");
		String[] chain = { "pa233", "pa234", "u233", "u235" };
		MSRByproduct[] bys = MSRByproduct.values();
		for(int i = 0; i < bys.length; i++) {
			helper.assertTrue(bys[i].byproducts.length == 1 && bys[i].byproducts[0].getKey().equals(chain[i])
					&& bys[i].byproducts[0].getValue() == 1d && bys[i].division == 1, bys[i] + " -> " + chain[i]);
		}
		helper.assertTrue(MSRFuel.th232.dicts[0].equals(com.hbm.inventory.OreDictManager.TH232.nugget())
				&& MSRFuel.u233.dicts[0].equals(com.hbm.inventory.OreDictManager.U233.nugget())
				&& MSRFuel.u235.dicts[0].equals(com.hbm.inventory.OreDictManager.U235.nugget())
				&& MSRFuel.pa233.dicts.length == 0 && MSRFuel.pa234.dicts.length == 0, "the nugget dictionary names");
		near(helper, MSRControlTE.speed, 0.005, "the rod moves 0.1 m/s");
		near(helper, MSRControlBlock.STEP, 0.25, "a screwdriver click is a quarter metre");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the salt's NBT

	/** The protocol adds both keys and keeps what is there; a mixture survives a write and a read. */
	@GameTest(template = EMPTY16)
	public static void theSaltProtocolAndItsMixture(GameTestHelper helper) {
		CompoundTag fresh = MSRTEBase.nbtProtocol((CompoundTag) null);
		helper.assertTrue(fresh.contains("itemMixture") && fresh.getDouble("heat") == 0, "a fresh tag has both keys");
		CompoundTag kept = new CompoundTag();
		kept.putDouble("heat", 7);
		helper.assertTrue(MSRTEBase.nbtProtocol(kept).getDouble("heat") == 7, "an existing heat is kept");
		Map<String, Double> m = new LinkedHashMap<>();
		m.put("th232", 0.5);
		m.put("u233", 0.25);
		Map<String, Double> back = MSRTEBase.readMixture(MSRTEBase.writeMixture(m, new CompoundTag()));
		helper.assertTrue(back.equals(m) && new ArrayList<>(back.keySet()).equals(List.of("th232", "u233")), "round trip in order: " + back);
		helper.succeed();
	}

	/** Mixing two stacks: isotopes weighted by amount, heat moving by difference / (2 x divisor). */
	@GameTest(template = EMPTY16)
	public static void transferStatsWeighsByAmountAndMovesHeat(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		FluidStack a = salt(300, 100, "th232", 1.0), b = salt(100, 0, "u233", 2.0);
		MSRTEBase.transferStats(a, b, 2);
		CompoundTag ta = LeafiaFF.getTag(a), tb = LeafiaFF.getTag(b);
		near(helper, ta.getDouble("heat"), 75, "the hotter loses 100 / 4");
		near(helper, tb.getDouble("heat"), 25, "the colder gains it");
		helper.assertTrue(MSRTEBase.readMixture(ta).equals(MSRTEBase.readMixture(tb)), "both end with one mixture");
		near(helper, MSRTEBase.readMixture(tb).get("th232"), 0.75, "th232 1 x 300/400");
		near(helper, MSRTEBase.readMixture(tb).get("u233"), 0.5, "u233 2 x 100/400");
		FluidStack before = salt(10, 5, null, 0);
		MSRTEBase.transferStats(before, FluidStack.EMPTY, 1);
		helper.assertTrue(LeafiaFF.getTag(before).getDouble("heat") == 5, "nothing happens against an empty stack");
		helper.succeed();
	}

	// ------------------------------------------------------------------ flow and cooling

	/**
	 * A tick's cooling is x0.99; and the flow to an emptier neighbour is half the difference, divided
	 * again by the number of such neighbours (upstream's double division, D-021). A plug that has not
	 * melted takes nothing.
	 */
	@GameTest(template = EMPTY16)
	public static void saltCoolsAndFlowsByUpstreamsDoubleDivision(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos c = new BlockPos(8, 3, 8);
		helper.setBlock(c, LeafiaBlocks.msr_arbitrary.get());
		MSRArbitraryTE centre = (MSRArbitraryTE) helper.getBlockEntity(c);
		centre.tank.setFluid(salt(1000, 1000, null, 0));
		centre.update();
		near(helper, heat(centre), 990, "passive cooling x0.99");
		near(helper, MSRTEBase.meltdownHeat(centre.tank.getFluid()), 6000 - LeafiaFluids.FLUORIDE.temperature, "the sellafield threshold");
		near(helper, MSRPlugTE.meltingHeat(centre.tank.getFluid()), 4000 - LeafiaFluids.FLUORIDE.temperature, "the plug's melting point");

		helper.setBlock(c.east(), LeafiaBlocks.msr_arbitrary.get());
		MSRArbitraryTE one = (MSRArbitraryTE) helper.getBlockEntity(c.east());
		centre.tank.setFluid(salt(1000, 0, null, 0));
		centre.sendFluids();
		helper.assertTrue(centre.tank.getFluidAmount() == 500 && one.tank.getFluidAmount() == 500,
				"one neighbour: half the difference, " + centre.tank.getFluidAmount() + "/" + one.tank.getFluidAmount());

		helper.setBlock(c.west(), LeafiaBlocks.msr_arbitrary.get());
		helper.setBlock(c.north(), LeafiaBlocks.msr_plug.get());
		MSRArbitraryTE two = (MSRArbitraryTE) helper.getBlockEntity(c.west());
		MSRPlugTE plug = (MSRPlugTE) helper.getBlockEntity(c.north());
		centre.tank.setFluid(salt(1000, 0, null, 0));
		one.tank.setFluid(FluidStack.EMPTY);
		centre.sendFluids();
		helper.assertTrue(one.tank.getFluidAmount() == 250 && two.tank.getFluidAmount() == 250 && centre.tank.getFluidAmount() == 500,
				"two neighbours: 500 each, then halved again: " + one.tank.getFluidAmount() + "/" + two.tank.getFluidAmount() + "/" + centre.tank.getFluidAmount());
		helper.assertTrue(plug.tank.getFluidAmount() == 0, "the plug that has not melted takes nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the reaction

	/** One reaction against upstream's formula, with and without a control rod's restriction. */
	@GameTest(template = EMPTY16)
	public static void aReactionFollowsUpstreamsFormula(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos pa = new BlockPos(3, 3, 3), pb = new BlockPos(12, 3, 12);
		helper.setBlock(pa, LeafiaBlocks.msr_element.get());
		helper.setBlock(pb, LeafiaBlocks.msr_element.get());
		MSRElementTE a = (MSRElementTE) helper.getBlockEntity(pa), b = (MSRElementTE) helper.getBlockEntity(pb);
		double base = LeafiaFluids.FLUORIDE.temperature;
		for(double restriction : new double[] { 0, 0.5 }) {
			a.tank.setFluid(salt(1000, 10, "th232", 1.0));
			b.tank.setFluid(salt(500, 200, null, 0));
			a.restriction = restriction;
			a.react(b, 2, 0.5);
			double B = 500 / 2000d + 1000 / 2000d;
			double mult = 0.5 * (1 - restriction) / (2 / 2d);
			double y = Math.pow(200 + base, 0.3) * 1.0 * B * mult;
			double expectHeat = 10 + Math.pow(Math.abs(y - 10), 0.2) * Math.signum(y - 10);
			near(helper, heat(a), expectHeat, "heat after one reaction at restriction " + restriction);
			near(helper, mixture(a).get("pa233"), y / 30000000d, "th232 burns into pa233 at y/life");
			near(helper, mixture(a).get("th232"), 1.0 - y / 30000000d, "and loses as much");
			near(helper, heat(b), 200, "the target's salt is only read");
		}
		helper.succeed();
	}

	/** Protactinium-233 decays into uranium-233 at 0.03 x amount x buckets x 0.05 a tick. */
	@GameTest(template = EMPTY16)
	public static void protactiniumDecaysIntoUranium(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(5, 3, 5);
		helper.setBlock(p, LeafiaBlocks.msr_element.get());
		MSRElementTE e = (MSRElementTE) helper.getBlockEntity(p);
		e.tank.setFluid(salt(800, 0, "pa233", 2.0));
		e.decay();
		double add = 0.03 * 2.0 * 0.8 * 0.05;
		near(helper, mixture(e).get("u233"), add, "u233 gained");
		near(helper, mixture(e).get("pa233"), 2.0 - add, "pa233 lost");
		helper.succeed();
	}

	/**
	 * Which chambers react: lines to four blocks (a moderator anywhere before the target doubles it, a
	 * shielding block stops the line), the twelve edge positions at distance 2, a PWR reflector standing
	 * for the chamber itself, an arbitrary block counting as what it holds - and upstream's corner quirk,
	 * where only the first face neighbour is asked about shielding.
	 */
	@GameTest(template = EMPTY16)
	public static void theReactionGeometryIsUpstreams(GameTestHelper helper) {
		if(!on() || !LeafiaFeatures.ffDuct) { helper.succeed(); return; }
		BlockPos c = new BlockPos(8, 6, 8);
		helper.setBlock(c, LeafiaBlocks.msr_element.get());
		BlockPos eastLine = c.east(3);                       // line, distance 3, plain
		BlockPos northLine = c.north(2);                     // line through graphite at north 1
		BlockPos southLine = c.south(2);                     // line through an arbitrary block holding graphite
		BlockPos westLine = c.west(2);                       // blocked by a shielded duct at west 1
		BlockPos corner = c.east().above();                  // corner east-up: the shield is the SECOND neighbour (up)
		BlockPos blocked = c.west().above();                 // corner west-up: the shield is the FIRST neighbour (west)
		for(BlockPos p : List.of(eastLine, northLine, southLine, westLine, corner, blocked))
			helper.setBlock(p, LeafiaBlocks.msr_element.get());
		helper.setBlock(c.north(), ModBlocks.block_graphite.get());
		helper.setBlock(c.south(), LeafiaBlocks.msr_arbitrary.get());
		((MSRArbitraryTE) helper.getBlockEntity(c.south())).inventory.setStackInSlot(0, new ItemStack(ModBlocks.block_graphite.get()));
		helper.setBlock(c.west(), LeafiaBlocks.ff_duct_solid_shielded.get());
		helper.setBlock(c.above(), LeafiaBlocks.ff_duct_solid_shielded.get());
		boolean reflector = LeafiaBlocks.lwr_reflector != null;
		if(reflector) helper.setBlock(c.below(3), LeafiaBlocks.lwr_reflector.get());

		MSRElementTE e = (MSRElementTE) helper.getBlockEntity(c);
		List<String> got = new ArrayList<>();
		for(Reaction r : e.reactionTargets())
			got.add(rel(helper, c, r.target()) + " d" + r.distance() + " x" + r.multiplier());
		List<String> want = new ArrayList<>(List.of(
				"(1,1,0) d2.0 x0.5",       // the corner past the shield on its second neighbour: upstream's quirk
				"(3,0,0) d3.0 x0.5",
				"(0,0,-2) d2.0 x2.0",
				"(0,0,2) d2.0 x2.0"));
		if(reflector) want.add("(0,0,0) d3.0 x0.5");  // the reflector three below: the chamber reacts with itself
		Collections.sort(got);
		Collections.sort(want);
		helper.assertTrue(got.equals(want), "reactions " + got + ", expected " + want);
		helper.assertTrue(MSRElementTE.isModerator(ModBlocks.block_graphite.get()) && !MSRElementTE.isModerator(Blocks.STONE),
				"graphite moderates, stone does not");
		helper.succeed();
	}

	private static String rel(GameTestHelper helper, BlockPos c, MSRElementTE te) {
		BlockPos d = te.getBlockPos().subtract(helper.absolutePos(c));
		return "(" + d.getX() + "," + d.getY() + "," + d.getZ() + ")";
	}

	// ------------------------------------------------------------------ the control rod

	/**
	 * The rod's travel is the tubes behind it on its own axis; it moves 0.005 a tick toward its target;
	 * the chamber i blocks out is restricted by clamp(insertion - i + 1, 0, 1); a screwdriver moves the
	 * target a quarter metre, capped at the travel, and a click that changes nothing passes.
	 */
	@GameTest(template = EMPTY16)
	public static void theControlRodRestrictsByInsertion(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos rod = new BlockPos(6, 3, 8);
		helper.setBlock(rod, LeafiaBlocks.msr_control.get().defaultBlockState().setValue(MSRControlBlock.FACING, Direction.EAST));
		helper.setBlock(rod.west(), LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		helper.setBlock(rod.west(2), LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		helper.setBlock(rod.west(3), LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
		helper.setBlock(rod.east(), LeafiaBlocks.msr_element.get());
		helper.setBlock(rod.east(2), LeafiaBlocks.msr_element.get());
		MSRControlTE control = (MSRControlTE) helper.getBlockEntity(rod);
		helper.assertTrue(control.getExtensionLength(Direction.EAST) == 2, "two tubes on the axis; the upright third does not count");

		ServerPlayer player = mockPlayer(helper);
		MSRControlBlock block = (MSRControlBlock) LeafiaBlocks.msr_control.get();
		BlockPos abs = helper.absolutePos(rod);
		control.length = 2;
		player.setShiftKeyDown(true);
		for(int i = 0; i < 8; i++) block.onScrew(helper.getLevel(), player, abs, Direction.UP, Vec3.ZERO, IToolable.ToolType.SCREWDRIVER);
		near(helper, control.targetInsertion, 2, "sneaking inserts by 0.25, capped at the travel");
		helper.assertTrue(!block.onScrew(helper.getLevel(), player, abs, Direction.UP, Vec3.ZERO, IToolable.ToolType.SCREWDRIVER),
				"a click at the cap changes nothing and passes");
		player.setShiftKeyDown(false);
		for(int i = 0; i < 2; i++) block.onScrew(helper.getLevel(), player, abs, Direction.UP, Vec3.ZERO, IToolable.ToolType.SCREWDRIVER);
		near(helper, control.targetInsertion, 1.5, "standing withdraws by 0.25");
		helper.assertTrue(!block.onScrew(helper.getLevel(), player, abs, Direction.UP, Vec3.ZERO, IToolable.ToolType.DEFUSER),
				"another tool does nothing");

		control.update();
		near(helper, control.insertion, 0.005, "one tick moves 0.005");
		for(int i = 0; i < 400; i++) control.update();
		near(helper, control.insertion, 1.5, "it arrives at the target");
		MSRElementTE first = (MSRElementTE) helper.getBlockEntity(rod.east()), second = (MSRElementTE) helper.getBlockEntity(rod.east(2));
		near(helper, first.restriction, 1, "the first chamber is fully restricted");
		near(helper, second.restriction, 0.5, "the second half");
		helper.assertTrue(abs.equals(first.control) && abs.equals(second.control), "both are claimed by the rod");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the plug

	/**
	 * The plug takes a coolant salt, mixing tags, and nothing else; it melts above 4000 - 500 and then
	 * fills the replaceable block below with fluoride and drains 1,000 mB a tick while fluoride is there;
	 * it never pours into a liquid.
	 */
	@GameTest(template = EMPTY16)
	public static void thePlugTakesSaltMeltsAndSpills(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(8, 4, 8);
		helper.setBlock(p, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.NORTH));
		MSRPlugTE plug = (MSRPlugTE) helper.getBlockEntity(p);
		helper.assertTrue(plug.fill(LeafiaFF.toFF(com.hbm.inventory.fluid.Fluids.DIESEL, 100), FluidAction.EXECUTE) == 0, "diesel is refused");
		helper.assertTrue(plug.fill(salt(400, 100, null, 0), FluidAction.SIMULATE) == 400 && plug.tank.getFluidAmount() == 0, "simulate fills nothing");
		helper.assertTrue(plug.fill(salt(400, 100, null, 0), FluidAction.EXECUTE) == 400, "salt goes in");
		helper.assertTrue(plug.fill(salt(400, 0, null, 0), FluidAction.EXECUTE) == 400, "more salt goes in, mixed");
		near(helper, heat(plug), 50, "the two halves of salt mixed their heat");
		helper.assertTrue(plug.tank.getCapacity() == 10000, "a 10,000 mB tank");

		BlockPos below = p.below();
		helper.setBlock(below, Blocks.WATER);
		plug.tank.setFluid(salt(5000, 4000 - LeafiaFluids.FLUORIDE.temperature, null, 0));
		plug.update();
		helper.assertTrue(!plug.molten, "exactly at the melting point it holds (upstream's test is strictly greater)");
		plug.tank.setFluid(salt(5000, 4000 - LeafiaFluids.FLUORIDE.temperature + 1, null, 0));
		plug.update();
		helper.assertTrue(plug.molten, "above it, it melts");
		helper.assertTrue(helper.getBlockState(below).is(Blocks.WATER) && plug.tank.getFluidAmount() == 5000, "it never pours into a liquid");

		if(MSRPlugTE.fluorideBlock() != null) {
			helper.setBlock(below, Blocks.AIR);
			plug.update();
			helper.assertTrue(helper.getBlockState(below).getBlock() == MSRPlugTE.fluorideBlock().getBlock(), "fluoride below");
			helper.assertTrue(plug.tank.getFluidAmount() == 4000, "and 1,000 mB paid the same tick: " + plug.tank.getFluidAmount());
			plug.update();
			helper.assertTrue(plug.tank.getFluidAmount() == 3000, "and 1,000 more each tick it is there: " + plug.tank.getFluidAmount());
			helper.setBlock(below, Blocks.STONE); // stop the spill spreading through the test world
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the arbitrary block

	/** It takes one block from a stack, gives it back to a screwdriver, and saves it. */
	@GameTest(template = EMPTY16)
	public static void theArbitraryBlockHoldsOneBlock(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(6, 3, 6);
		helper.setBlock(p, LeafiaBlocks.msr_arbitrary.get());
		MSRArbitraryTE te = (MSRArbitraryTE) helper.getBlockEntity(p);
		ServerPlayer player = mockPlayer(helper);
		player.getAbilities().instabuild = false; // so the screwdriver wears
		BlockPos abs = helper.absolutePos(p);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		ItemStack graphite = new ItemStack(ModBlocks.block_graphite.get(), 5);
		player.setItemInHand(InteractionHand.MAIN_HAND, graphite);
		helper.getBlockState(p).useItemOn(graphite, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(te.inventory.getStackInSlot(0).getCount() == 1 && graphite.getCount() == 4, "one graphite went in");
		CompoundTag saved = te.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.contains("inventory") && saved.contains("tank"), "the slot and the tank are saved");
		ItemStack again = new ItemStack(ModBlocks.block_graphite.get(), 5);
		helper.getBlockState(p).useItemOn(again, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(again.getCount() == 5, "a full block takes no more");
		ItemStack driver = new ItemStack(ModItems.screwdriver.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, driver);
		helper.getBlockState(p).useItemOn(driver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(te.inventory.getStackInSlot(0).isEmpty(), "the screwdriver takes it out");
		helper.assertTrue(driver.getDamageValue() == 1, "and wears by one");
		for(net.minecraft.world.entity.item.ItemEntity item : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(3)))
			item.discard(); // dropped at the mock player, who stands at the origin for every test
		helper.succeed();
	}
}
