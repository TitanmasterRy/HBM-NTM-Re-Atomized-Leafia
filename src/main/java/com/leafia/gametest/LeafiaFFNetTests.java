package com.leafia.gametest;

import java.util.List;

import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.network.FluidDuctStandard.PipeMaterial;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.uninos.UniNodespace;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorTE;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE;
import com.leafia.contents.network.FFNBT;
import com.leafia.contents.network.ff_duct.FFDuctStandard;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.contents.network.ff_duct.uninos.FFNet;
import com.leafia.contents.network.ff_duct.uninos.IFFProvider;
import com.leafia.contents.network.ff_duct.uninos.IFFReceiver;
import com.leafia.dev.LeafiaUtil;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Forge-fluid duct network (D-3012): the transfer helpers, the network's split, the duct's node,
 * connections and shapes, and fluid moving from an ejector to a plug through a run of duct.
 *
 * <p>Most tests call the code directly inside one test body, where no world tick can intervene, so a
 * number written a moment ago is still there. The two that need the world to tick
 * open their window through {@link TickWindow} and assert a relationship rather than a tick count.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFFNetTests {

	private static final String EMPTY16 = "empty16";

	private static boolean ducts() {
		return LeafiaFeatures.ffDuct && LeafiaFF.enabled();
	}

	private static boolean msr() {
		return LeafiaFeatures.lftrReactor() && LeafiaFF.enabled();
	}

	private static FluidStack stack(com.hbm.inventory.fluid.FluidType type, int mB, double heat, String fuel, double amount) {
		FluidStack s = LeafiaFF.toFF(type, mB);
		CompoundTag tag = MSRTEBase.nbtProtocol((CompoundTag) null);
		tag.putDouble("heat", heat);
		if(fuel != null) {
			java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
			m.put(fuel, amount);
			MSRTEBase.writeMixture(m, tag);
		}
		LeafiaFF.setTag(s, tag);
		return s;
	}

	private static FluidTank tank(int capacity, FluidStack contents) {
		FluidTank t = new FluidTank(capacity);
		t.setFluid(contents.copy());
		return t;
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	// ------------------------------------------------------------------ fillFF and the tags

	/**
	 * fillFF writes the receiver directly: an empty one gets a copy of the sender at the asked amount,
	 * tag and all; a full sender pays what was asked. Asking for more than the sender holds creates the
	 * difference - upstream's, pinned (D-021).
	 */
	@GameTest(template = EMPTY16)
	public static void fillFFCopiesTheStackAndPaysTheAskedAmount(GameTestHelper helper) {
		if(!LeafiaFF.enabled()) { helper.succeed(); return; }
		FluidTank s = tank(1000, stack(Fluids.DIESEL, 5, 40, null, 0));
		FluidTank r = new FluidTank(10);
		helper.assertTrue(LeafiaUtil.fillFF(s, r, 3) == 3, "the empty receiver takes the asked 3 mB");
		helper.assertTrue(r.getFluidAmount() == 3 && s.getFluidAmount() == 2, "3 moved: " + r.getFluidAmount() + "/" + s.getFluidAmount());
		helper.assertTrue(LeafiaFF.getTag(r.getFluid()).equals(LeafiaFF.getTag(s.getFluid())), "the copy carries the sender's tag");
		// Now ask for 3 when the sender holds 2: the receiver gains 3, the sender can only lose 2.
		helper.assertTrue(LeafiaUtil.fillFF(s, r, 3) == 3, "the receiver had room for 3");
		helper.assertTrue(r.getFluidAmount() == 6 && s.getFluidAmount() == 0,
				"upstream's overfill: 5 mB became 6 - got " + r.getFluidAmount() + " and " + s.getFluidAmount());
		helper.assertTrue(LeafiaUtil.fillFF(s, r, 3) == 0, "an empty sender moves nothing");
		helper.succeed();
	}

	/** An empty or untagged receiver takes anything; equal tags meet; different tags meet only on a coolant salt. */
	@GameTest(template = EMPTY16)
	public static void tagsAreCompatibleByUpstreamsFourRules(GameTestHelper helper) {
		if(!LeafiaFF.enabled()) { helper.succeed(); return; }
		FluidStack hot = stack(Fluids.DIESEL, 100, 50, null, 0);
		helper.assertTrue(FFNBT.areTagsCompatible(hot, new FluidTank(1000)), "an empty receiver takes anything");
		helper.assertTrue(FFNBT.areTagsCompatible(hot, tank(1000, LeafiaFF.toFF(Fluids.DIESEL, 10))), "an untagged receiver takes anything");
		helper.assertTrue(FFNBT.areTagsCompatible(hot, tank(1000, stack(Fluids.DIESEL, 10, 50, null, 0))), "equal tags meet");
		helper.assertTrue(!FFNBT.areTagsCompatible(hot, tank(1000, stack(Fluids.DIESEL, 10, 60, null, 0))), "different tags on diesel do not meet");
		if(LeafiaFluids.FLUORIDE != null && LeafiaFF.isMapped(LeafiaFluids.FLUORIDE)) {
			helper.assertTrue(FFNBT.areTagsCompatible(stack(LeafiaFluids.FLUORIDE, 100, 50, null, 0),
					tank(1000, stack(LeafiaFluids.FLUORIDE, 10, 60, "th232", 1))), "any two salts meet");
		}
		helper.succeed();
	}

	/**
	 * Salt into salt: a tagged receiver mixes and both end level; an untagged one is left holding the
	 * sender's original tag while the sender has lost half its heat and half its fuel share - upstream's
	 * reference juggling, pinned (D-021, D-3012).
	 */
	@GameTest(template = EMPTY16)
	public static void transferTagsMixesSaltAndHalvesASendersHeatIntoAnUntaggedReceiver(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		FluidTank s = tank(1000, stack(LeafiaFluids.FLUORIDE, 300, 100, "th232", 1.0));
		FluidTank r = tank(1000, stack(LeafiaFluids.FLUORIDE, 100, 0, "u233", 2.0));
		FFNBT.transferTags(s, r);
		CompoundTag a = LeafiaFF.getTag(s.getFluid()), b = LeafiaFF.getTag(r.getFluid());
		near(helper, a.getDouble("heat"), 50, "tagged: sender heat");
		near(helper, b.getDouble("heat"), 50, "tagged: receiver heat");
		near(helper, MSRTEBase.readMixture(b).get("th232"), 0.75, "tagged: th232 weighted 300/400");
		near(helper, MSRTEBase.readMixture(b).get("u233"), 0.5, "tagged: u233 weighted 100/400");

		FluidTank s2 = tank(1000, stack(LeafiaFluids.FLUORIDE, 300, 100, "th232", 1.0));
		FluidTank r2 = tank(1000, LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 100));
		FFNBT.transferTags(s2, r2);
		CompoundTag a2 = LeafiaFF.getTag(s2.getFluid()), b2 = LeafiaFF.getTag(r2.getFluid());
		near(helper, b2.getDouble("heat"), 100, "untagged: the receiver gets the sender's original heat");
		near(helper, MSRTEBase.readMixture(b2).get("th232"), 1.0, "untagged: and its original mixture");
		near(helper, a2.getDouble("heat"), 50, "untagged: the sender has lost half its heat");
		near(helper, MSRTEBase.readMixture(a2).get("th232"), 0.75, "untagged: and a quarter of its fuel share");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the network's split

	/** A provider and receiver at once, holding one NeoForge tank, as a machine on a duct would. */
	static final class Endpoint implements IFFProvider, IFFReceiver {
		final FluidTank tank;
		Endpoint(int capacity) { this.tank = new FluidTank(capacity); }
		@Override public FluidTank getSendingTank(FluidStack stack) { return tank; }
		@Override public FluidTank getCorrespondingTank(FluidStack stack) { return tank; }
		@Override public int getTanks() { return 1; }
		@Override public FluidStack getFluidInTank(int i) { return tank.getFluid(); }
		@Override public int getTankCapacity(int i) { return tank.getCapacity(); }
		@Override public boolean isFluidValid(int i, FluidStack s) { return true; }
		@Override public int fill(FluidStack s, FluidAction a) { return tank.fill(s, a); }
		@Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
		@Override public FluidStack drain(int m, FluidAction a) { return tank.drain(m, a); }
	}

	/**
	 * Shares are {@code min(room, ceil(available x room / total room))}: 100 mB into rooms of 10 and 30
	 * is 10 and 30; 5 mB into two rooms of 10 is 3 and 3, one millibucket out of nothing (D-021). A
	 * receiver of another fluid is passed over, and the tank registry is cleared after the transfer.
	 */
	@GameTest(template = EMPTY16)
	public static void theNetworkSplitsByRoomAndRoundsUp(GameTestHelper helper) {
		if(!LeafiaFF.enabled()) { helper.succeed(); return; }
		FFNet net = new FFNet();
		try {
			Endpoint prov = new Endpoint(1000);
			prov.tank.setFluid(LeafiaFF.toFF(Fluids.DIESEL, 100));
			Endpoint small = new Endpoint(10), big = new Endpoint(30), other = new Endpoint(1000);
			other.tank.setFluid(LeafiaFF.toFF(Fluids.KEROSENE, 1));
			net.addProvider(prov);
			net.addTank(prov, prov.tank);
			net.addReceiver(small);
			net.addReceiver(big);
			net.addReceiver(other);
			net.transfer();
			helper.assertTrue(small.tank.getFluidAmount() == 10 && big.tank.getFluidAmount() == 30,
					"rooms 10 and 30 take 10 and 30, got " + small.tank.getFluidAmount() + " and " + big.tank.getFluidAmount());
			helper.assertTrue(prov.tank.getFluidAmount() == 60, "the provider paid 40, has " + prov.tank.getFluidAmount());
			helper.assertTrue(other.tank.getFluidAmount() == 1, "a receiver holding kerosene takes no diesel");
			net.transfer();
			helper.assertTrue(small.tank.getFluidAmount() == 10 && prov.tank.getFluidAmount() == 60,
					"no tank was registered again, so nothing moved");

			Endpoint five = new Endpoint(1000), a = new Endpoint(10), b = new Endpoint(10);
			five.tank.setFluid(LeafiaFF.toFF(Fluids.DIESEL, 5));
			FFNet net2 = new FFNet();
			try {
				net2.addProvider(five);
				net2.addTank(five, five.tank);
				net2.addReceiver(a);
				net2.addReceiver(b);
				net2.transfer();
				helper.assertTrue(a.tank.getFluidAmount() + b.tank.getFluidAmount() == 6 && five.tank.getFluidAmount() == 0,
						"5 mB split 3 + 3 (ceil of 2.5 each): got " + a.tank.getFluidAmount() + " + " + b.tank.getFluidAmount());
			} finally {
				net2.destroy();
			}
		} finally {
			net.destroy();
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the duct

	/** A duct joins the FF network only while its fluid has a Forge fluid, and its node goes with the block. */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void aDuctMakesItsNodeOnlyForAForgeFluidAndLosesItWithTheBlock(GameTestHelper helper) {
		if(!ducts()) { helper.succeed(); return; }
		BlockPos none = new BlockPos(3, 2, 3), diesel = new BlockPos(6, 2, 3);
		helper.setBlock(none, LeafiaBlocks.ff_duct.get());
		helper.setBlock(diesel, LeafiaBlocks.ff_duct.get());
		((FFDuctTE) helper.getBlockEntity(diesel)).setType(Fluids.DIESEL);
		TickWindow.runAfterDelay(helper, 3, () -> {
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), helper.absolutePos(none), FFNet.PROVIDER) == null,
					"a NONE duct carries nothing and makes no node");
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), helper.absolutePos(diesel), FFNet.PROVIDER) != null,
					"a diesel duct is on the FF network");
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), helper.absolutePos(diesel), Fluids.DIESEL.getNetworkProvider()) == null,
					"and not on the base mod's diesel network");
			helper.setBlock(diesel, Blocks.AIR);
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), helper.absolutePos(diesel), FFNet.PROVIDER) == null,
					"breaking it takes the node");
			helper.succeed();
		});
	}

	/**
	 * Ducts of one fluid join; another fluid's do not; a straight run's end flags say which neighbours are
	 * same-fluid ducts; and retyping one duct re-reads its neighbours' flags. The fuzzy identifier's flood
	 * fill retypes a whole run.
	 */
	@GameTest(template = EMPTY16)
	public static void ductsJoinTheirOwnFluidAndKnowTheirEnds(GameTestHelper helper) {
		if(!ducts()) { helper.succeed(); return; }
		BlockPos a = new BlockPos(3, 2, 5), b = a.east(), c = b.east(), d = c.east();
		for(BlockPos p : List.of(a, b, c, d)) helper.setBlock(p, LeafiaBlocks.ff_duct.get());
		FuzzyIdentifierItem.spreadTypeFF(helper.getLevel(), helper.absolutePos(a), Fluids.DIESEL, Fluids.NONE, FuzzyIdentifierItem.SPREAD_RANGE);
		for(BlockPos p : List.of(a, b, c, d))
			helper.assertTrue(((FFDuctTE) helper.getBlockEntity(p)).getFluidType() == Fluids.DIESEL, "the flood fill retyped " + p);
		((FFDuctTE) helper.getBlockEntity(d)).setType(Fluids.KEROSENE);
		BlockState sb = helper.getBlockState(b), sc = helper.getBlockState(c), sa = helper.getBlockState(a);
		helper.assertTrue(sb.getValue(FFDuctStandard.EAST) && sb.getValue(FFDuctStandard.WEST), "the middle duct joins both sides");
		helper.assertTrue(!sb.getValue(FFDuctStandard.END_POS) && !sb.getValue(FFDuctStandard.END_NEG), "between two diesel ducts, no ends");
		helper.assertTrue(!sc.getValue(FFDuctStandard.EAST), "a diesel duct does not join a kerosene one");
		helper.assertTrue(sc.getValue(FFDuctStandard.END_POS) && !sc.getValue(FFDuctStandard.END_NEG),
				"the kerosene neighbour is an end, the diesel one is not: " + sc);
		helper.assertTrue(sa.getValue(FFDuctStandard.END_NEG) && !sa.getValue(FFDuctStandard.END_POS), "the run's west end");
		helper.assertTrue(FFDuctStandard.mask(helper.getBlockState(d)) == 0, "the lone kerosene duct joins nothing");
		helper.succeed();
	}

	/** Upstream's outline (one box) and collision (the arms) per mask, and the finishes are the base duct's three. */
	@GameTest(template = EMPTY16)
	public static void ductShapesAreUpstreams(GameTestHelper helper) {
		helper.assertTrue(FFDuctStandard.outlineFor(0).bounds().equals(new AABB(0, 0, 0, 1, 1, 1)), "isolated: a full cube outline");
		helper.assertTrue(FFDuctStandard.outlineFor(0b110000).bounds().equals(new AABB(0, 0.3125, 0.3125, 1, 0.6875, 0.6875)), "straight x");
		helper.assertTrue(FFDuctStandard.outlineFor(0b100010).bounds().equals(new AABB(0.3125, 0.3125, 0.3125, 1, 0.6875, 1)), "east and south");
		helper.assertTrue(Shapes.joinIsNotEmpty(com.hbm.blocks.network.FluidDuctStandard.shapeFor(0), Shapes.box(0.32, 0.32, 0.32, 0.68, 0.68, 0.68),
				net.minecraft.world.phys.shapes.BooleanOp.AND) == false, "isolated collision has no core, as upstream's six arms");
		helper.assertTrue(PipeMaterial.values().length == 3, "three finishes");
		if(ducts()) {
			for(PipeMaterial m : PipeMaterial.values())
				helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl("ff_duct_" + m.getSerializedName())), "ff_duct_" + m.getSerializedName());
			helper.assertTrue(LeafiaBlocks.ff_duct.get().defaultDestroyTime() == 5F && LeafiaBlocks.ff_duct.get().getExplosionResistance() == 6F,
					"ff_duct is 5 / 10 x 0.6");
			helper.assertTrue(LeafiaBlocks.ff_duct_solid_shielded.get().defaultDestroyTime() == 15F
					&& LeafiaBlocks.ff_duct_solid_shielded.get().getExplosionResistance() == 240F, "the shielded duct is 15 / COMPOUND_MESH 240");
		}
		helper.succeed();
	}

	/** The shielded duct shields, is a duct to the network, and keeps its fluid through a save. */
	@GameTest(template = EMPTY16)
	public static void theShieldedDuctShieldsAndCarries(GameTestHelper helper) {
		if(!ducts()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(4, 2, 4);
		helper.setBlock(p, LeafiaBlocks.ff_duct_solid_shielded.get());
		helper.assertTrue(IRadResistantBlock.blocksRadiation(helper.getBlockState(p)), "it blocks radiation");
		FFDuctTE duct = (FFDuctTE) helper.getBlockEntity(p);
		duct.setType(Fluids.DIESEL);
		helper.assertTrue(duct.canConnect(LeafiaFF.toFF(Fluids.DIESEL, 1), Direction.UP), "it takes its own fluid");
		helper.assertTrue(!duct.canConnect(LeafiaFF.toFF(Fluids.KEROSENE, 1), Direction.UP), "and no other");
		helper.assertTrue(!duct.canConnect(LeafiaFF.toFF(Fluids.DIESEL, 1), null), "and nothing on an unknown side");
		CompoundTag saved = duct.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.getInt("fluid") == Fluids.DIESEL.getID(), "saved as the NTM id under \"fluid\"");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the endpoints

	/**
	 * The plug's handler is on its front and bottom, the ejector's on its front only, both also unsided;
	 * a duct beside a plug joins it only on those faces.
	 */
	@GameTest(template = EMPTY16)
	public static void endpointsExposeUpstreamsSides(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos plug = new BlockPos(5, 3, 5), ej = new BlockPos(10, 3, 5);
		helper.setBlock(plug, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.EAST));
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.UP));
		for(Direction d : Direction.values()) {
			boolean plugHas = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(plug), d) != null;
			boolean ejHas = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(ej), d) != null;
			helper.assertTrue(plugHas == (d == Direction.EAST || d == Direction.DOWN), "plug side " + d + ": " + plugHas);
			helper.assertTrue(ejHas == (d == Direction.UP), "ejector side " + d + ": " + ejHas);
		}
		helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(plug), null) != null, "plug unsided");
		helper.setBlock(plug.east(), LeafiaBlocks.ff_duct.get());
		helper.setBlock(plug.north(), LeafiaBlocks.ff_duct.get());
		FFDuctTE front = (FFDuctTE) helper.getBlockEntity(plug.east()), side = (FFDuctTE) helper.getBlockEntity(plug.north());
		front.setType(LeafiaFluids.FLUORIDE);
		side.setType(LeafiaFluids.FLUORIDE);
		helper.assertTrue(helper.getBlockState(plug.east()).getValue(FFDuctStandard.WEST), "a duct on the plug's front joins it");
		helper.assertTrue(!helper.getBlockState(plug.north()).getValue(FFDuctStandard.SOUTH), "a duct on its side does not");
		helper.succeed();
	}

	/**
	 * Straight across: an ejector under a plug hands its salt up in one call, as much as the plug has
	 * room for, tag and all - and the ejector cannot be filled and the plug cannot be drained.
	 */
	@GameTest(template = EMPTY16)
	public static void anEjectorFillsAPlugDirectly(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(5, 2, 5), plug = ej.above();
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.UP));
		helper.setBlock(plug, LeafiaBlocks.msr_plug.get());
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		MSRPlugTE p = (MSRPlugTE) helper.getBlockEntity(plug);
		e.tank.setFluid(stack(LeafiaFluids.FLUORIDE, 800, 20, "th232", 1));
		e.tryProvide(e.tank, helper.getLevel(), helper.absolutePos(plug), Direction.UP);
		helper.assertTrue(p.tank.getFluidAmount() == 800 && e.tank.getFluidAmount() == 0, "800 mB went up: " + p.tank.getFluidAmount());
		near(helper, LeafiaFF.getTag(p.tank.getFluid()).getDouble("heat"), 20, "the salt kept its heat");
		helper.assertTrue(e.fill(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 10), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0,
				"the ejector takes nothing");
		helper.assertTrue(p.drain(10, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "the plug gives nothing");
		helper.succeed();
	}

	/** The whole path: an ejector, three ducts of FLiBe and a plug facing them; the salt arrives. */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void saltTravelsFromAnEjectorToAPlugThroughDucts(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(3, 2, 8);
		BlockPos plug = ej.east(4);
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.EAST));
		for(int i = 1; i <= 3; i++) helper.setBlock(ej.east(i), LeafiaBlocks.ff_duct.get());
		helper.setBlock(plug, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.WEST));
		FuzzyIdentifierItem.spreadTypeFF(helper.getLevel(), helper.absolutePos(ej.east()), LeafiaFluids.FLUORIDE, Fluids.NONE, 8);
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		e.tank.setFluid(stack(LeafiaFluids.FLUORIDE, 1000, 0, null, 0));
		MSRPlugTE p = (MSRPlugTE) helper.getBlockEntity(plug);
		TickWindow.sequence(helper).thenWaitUntil(() -> {
			helper.assertTrue(p.tank.getFluidAmount() == 1000, "the plug has " + p.tank.getFluidAmount() + " mB");
			helper.assertTrue(e.tank.getFluidAmount() == 0, "the ejector still has " + e.tank.getFluidAmount() + " mB");
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ the switches

	/** What registers is what the switches say. */
	@GameTest(template = EMPTY16)
	public static void theSwitchesDecideWhatRegisters(GameTestHelper helper) {
		boolean d = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("ff_duct"));
		boolean s = BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl("ff_duct_solid_shielded"));
		helper.assertTrue(d == LeafiaFeatures.ffDuct && s == LeafiaFeatures.ffDuct, "ff ducts registered " + d + "/" + s + ", switch " + LeafiaFeatures.ffDuct);
		for(String name : List.of("msr_element", "msr_plug", "msr_control", "msr_control_extension", "msr_arbitrary", "msr_ejector")) {
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name)) == LeafiaFeatures.lftrReactor(),
					name + " registered " + BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name)) + ", gate " + LeafiaFeatures.lftrReactor());
		}
		helper.assertTrue(LeafiaFeatures.ffBridge() == (LeafiaFeatures.ffDuct || LeafiaFeatures.lftr || LeafiaFeatures.lftrReactor()),
				"the bridge follows the duct and the reactor");
		helper.succeed();
	}

	@SuppressWarnings("unused")
	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos);
	}
}
