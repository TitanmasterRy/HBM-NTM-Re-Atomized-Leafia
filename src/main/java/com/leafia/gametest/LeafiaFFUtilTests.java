package com.leafia.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorBlock;
import com.leafia.contents.machines.reactors.lftr.components.ejector.MSREjectorTE;
import com.leafia.contents.machines.reactors.lftr.components.element.MSRElementTE.MSRFuel;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugBlock;
import com.leafia.contents.machines.reactors.lftr.components.plug.MSRPlugTE;
import com.leafia.contents.network.ff_duct.FFDuctTE;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityBase;
import com.leafia.contents.network.ff_duct.utility.FFDuctUtilityTEBase;
import com.leafia.contents.network.ff_duct.utility.converter.FFConverterTE;
import com.leafia.contents.network.ff_duct.utility.filter.FFFilterItem;
import com.leafia.contents.network.ff_duct.utility.filter.FFFilterTE;
import com.leafia.contents.network.ff_duct.utility.filter.FFFilterTE2;
import com.leafia.contents.network.ff_duct.utility.pump.FFPumpTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Forge-fluid duct's in-line utilities (D-3019): the pump, the converter and the filter.
 *
 * <p>Almost every test places the blocks, drives a block entity's {@code update()} in the test body, and asserts
 * what it did - no world tick can intervene in a body, so a number written a moment ago is still there. The two that need the world to tick (fluid through a pump in a line, and from the base mod's network
 * through a converter into the Forge network) open their window through {@link TickWindow} and assert where the
 * fluid ended up, not on which tick it got there.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFFUtilTests {

	private static final String EMPTY16 = "empty16";

	private static boolean on() {
		return LeafiaFeatures.ffDuct && LeafiaFF.enabled();
	}

	/** The salt-side tests also need an ejector and a plug. */
	private static boolean msr() {
		return on() && LeafiaFeatures.lftrReactor() && LeafiaFluids.FLUORIDE != null;
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		@SuppressWarnings("deprecation")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			field.setAccessible(true);
			((List<?>) field.get(helper.getLevel().getServer().getPlayerList())).remove(player);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("could not detach the mock player", e);
		}
		return player;
	}

	/** Anything the mock player dropped, who stands at the origin for every test. */
	private static void discardDrops(GameTestHelper helper, ServerPlayer player) {
		for(net.minecraft.world.entity.item.ItemEntity item : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new AABB(player.blockPosition()).inflate(3)))
			item.discard();
	}

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	private static BlockState facing(net.minecraft.world.level.block.Block block, Direction dir) {
		return block.defaultBlockState().setValue(FFDuctUtilityBase.FACING, dir);
	}

	/** FLiBe of {@code mB} at {@code heat} carrying {@code mixture} (fuel per bucket). */
	private static FluidStack saltMix(int mB, double heat, Map<String, Double> mixture) {
		FluidStack s = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, mB);
		CompoundTag tag = MSRTEBase.nbtProtocol((CompoundTag) null);
		tag.putDouble("heat", heat);
		MSRTEBase.writeMixture(mixture, tag);
		LeafiaFF.setTag(s, tag);
		return s;
	}

	private static Map<String, Double> mix(Object... kv) {
		Map<String, Double> m = new LinkedHashMap<>();
		for(int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
		return m;
	}

	private static Map<String, Double> mixtureOf(FluidStack s) {
		return MSRTEBase.readMixture(MSRTEBase.nbtProtocol(s));
	}

	// ------------------------------------------------------------------ the blocks

	/** Upstream's 5 / 10 x 0.6, the outline along the axis, six facings, and what registers. */
	@GameTest(template = EMPTY16)
	public static void theUtilityBlocksAreUpstreams(GameTestHelper helper) {
		for(String name : List.of("ff_pump", "ff_converter", "ff_filter")) {
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(LeafiaRef.rl(name)) == LeafiaFeatures.ffDuct, name + " registered iff ffDuct");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LeafiaRef.rl(name)) == LeafiaFeatures.ffDuct, name + " item registered iff ffDuct");
		}
		if(!LeafiaFeatures.ffDuct) {
			helper.assertTrue(LeafiaBlocks.ff_pump == null && LeafiaBlocks.ff_converter == null && LeafiaBlocks.ff_filter == null
					&& LeafiaBlockEntities.FF_PUMP == null, "nothing is held while the switch is off");
			helper.succeed();
			return;
		}
		for(var block : List.of(LeafiaBlocks.ff_pump.get(), LeafiaBlocks.ff_converter.get(), LeafiaBlocks.ff_filter.get())) {
			helper.assertTrue(block.defaultDestroyTime() == 5F && block.getExplosionResistance() == 6F, block + " is 5 / 10 x 0.6");
			helper.assertTrue(block.getStateDefinition().getPossibleStates().size() == 6, block + " has six facings");
			helper.assertTrue(!block.defaultBlockState().canOcclude(), block + " is not a full cube");
		}
		helper.assertTrue(FFDuctUtilityBase.shapeFor(Direction.Axis.X).bounds().equals(new AABB(0, 0.3125, 0.3125, 1, 0.6875, 0.6875)), "along x");
		helper.assertTrue(FFDuctUtilityBase.shapeFor(Direction.Axis.Y).bounds().equals(new AABB(0.3125, 0, 0.3125, 0.6875, 1, 0.6875)), "along y");
		helper.assertTrue(FFDuctUtilityBase.shapeFor(Direction.Axis.Z).bounds().equals(new AABB(0.3125, 0.3125, 0, 0.6875, 0.6875, 1)), "along z");
		BlockPos p = new BlockPos(4, 2, 4);
		helper.setBlock(p, facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		BlockPos abs = helper.absolutePos(p);
		helper.assertTrue(helper.getBlockState(p).getShape(helper.getLevel(), abs).bounds().equals(new AABB(0, 0.3125, 0.3125, 1, 0.6875, 0.6875)),
				"an east-facing pump is the x box");
		helper.assertTrue(BuiltInRegistries.ITEM.get(LeafiaRef.rl("ff_filter")) instanceof FFFilterItem, "the filter has its own item");
		helper.assertTrue(helper.getBlockEntity(p) instanceof FFPumpTE, "the pump makes a pump");
		helper.setBlock(p, facing(LeafiaBlocks.ff_converter.get(), Direction.UP));
		helper.assertTrue(helper.getBlockEntity(p) instanceof FFConverterTE, "the converter makes a converter");
		helper.setBlock(p, facing(LeafiaBlocks.ff_filter.get(), Direction.DOWN));
		helper.assertTrue(helper.getBlockEntity(p) instanceof FFFilterTE2 && !(helper.getBlockEntity(p) instanceof FFFilterTE),
				"the filter makes the second design, not the dormant one");
		helper.succeed();
	}

	/** Into the clicked face; out of it while sneaking; the off hand reverses the sneak. */
	@GameTest(template = EMPTY16)
	public static void placementFacesIntoTheClickedFaceUnlessSneakingAndTheOffHandReversesIt(GameTestHelper helper) {
		helper.assertTrue(FFDuctUtilityBase.facingFor(Direction.NORTH, false, false) == Direction.SOUTH, "plain: into the face");
		helper.assertTrue(FFDuctUtilityBase.facingFor(Direction.NORTH, true, false) == Direction.NORTH, "sneaking: out of the face");
		helper.assertTrue(FFDuctUtilityBase.facingFor(Direction.NORTH, false, true) == Direction.NORTH, "off hand: reversed");
		helper.assertTrue(FFDuctUtilityBase.facingFor(Direction.NORTH, true, true) == Direction.SOUTH, "off hand sneaking: reversed back");
		helper.assertTrue(FFDuctUtilityBase.facingFor(Direction.UP, false, false) == Direction.DOWN, "up and down too");
		if(!on()) { helper.succeed(); return; }

		ServerPlayer player = mockPlayer(helper);
		BlockPos abs = helper.absolutePos(new BlockPos(5, 2, 5));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.WEST, abs, false);
		ItemStack stack = new ItemStack(LeafiaBlocks.ff_pump.get());
		BlockState plain = LeafiaBlocks.ff_pump.get().getStateForPlacement(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
		helper.assertTrue(plain.getValue(FFDuctUtilityBase.FACING) == Direction.EAST, "clicked on a west face, it points east: " + plain);
		player.setShiftKeyDown(true);
		BlockState sneak = LeafiaBlocks.ff_pump.get().getStateForPlacement(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
		helper.assertTrue(sneak.getValue(FFDuctUtilityBase.FACING) == Direction.WEST, "sneaking, it points west: " + sneak);
		BlockState off = LeafiaBlocks.ff_pump.get().getStateForPlacement(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.OFF_HAND, stack, hit));
		helper.assertTrue(off.getValue(FFDuctUtilityBase.FACING) == Direction.EAST, "the off hand reverses it: " + off);
		discardDrops(helper, player);
		helper.succeed();
	}

	/** The tints the renderer set: pipes 240/255 of the fluid's colour, the box a grey by the colour's average. */
	@GameTest(template = EMPTY16)
	public static void theTintsAreTheRenderersColours(GameTestHelper helper) {
		helper.assertTrue(FFDuctUtilityBase.tint(0xFFFFFF, 1) == 0xF0F0F0, "white fluid, pipes " + Integer.toHexString(FFDuctUtilityBase.tint(0xFFFFFF, 1)));
		helper.assertTrue(FFDuctUtilityBase.tint(0xFF8000, 1) == (0xF0 << 16 | 0x78 << 8), "orange: 255, 128, 0 times 240/255 is 240, 120.5 -> 120.. got "
				+ Integer.toHexString(FFDuctUtilityBase.tint(0xFF8000, 1)));
		helper.assertTrue(FFDuctUtilityBase.tint(0x000000, 1) == 0, "black stays black");
		helper.assertTrue(FFDuctUtilityBase.tint(0xFFFFFF, 2) == 0x666666, "a bright fluid: the dark box (0.4)");
		helper.assertTrue(FFDuctUtilityBase.tint(0x000000, 2) == 0xA6A6A6, "a dark fluid: the light box (0.65)");
		helper.assertTrue(FFDuctUtilityBase.tint(0x808080, 2) == 0x666666, "128/255 averages over a half: dark");
		helper.assertTrue(FFDuctUtilityBase.tint(0x7F7F7F, 2) == 0xA6A6A6, "127/255 does not: light");
		helper.assertTrue(FFDuctUtilityBase.tint(0x123456, 0) == 0xFFFFFF && FFDuctUtilityBase.tint(0x123456, 3) == 0xFFFFFF, "any other index is white");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the identifier

	/** A fluid identifier sets the utility's fluid on the server, empties its tanks, and a click with anything else passes. */
	@GameTest(template = EMPTY16)
	public static void anIdentifierSetsTheFluidAndEmptiesTheTanks(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		ServerPlayer player = mockPlayer(helper);
		BlockPos p = new BlockPos(6, 2, 6);
		BlockPos abs = helper.absolutePos(p);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
		ItemStack id = new ItemStack(com.leafia.contents.LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(id, Fluids.KEROSENE);

		helper.setBlock(p, facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(p);
		pump.setType(Fluids.DIESEL);
		pump.tank.setFluid(LeafiaFF.toFF(Fluids.DIESEL, 4000));
		ItemInteractionResult r = helper.getBlockState(p).useItemOn(id, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(r.consumesAction(), "the click is consumed");
		helper.assertTrue(pump.getFluidType() == Fluids.KEROSENE, "the pump took the identifier's fluid");
		helper.assertTrue(pump.tank.isEmpty(), "and emptied its tank: " + pump.tank.getFluidAmount());

		helper.setBlock(p, facing(LeafiaBlocks.ff_converter.get(), Direction.EAST));
		FFConverterTE conv = (FFConverterTE) helper.getBlockEntity(p);
		conv.setType(Fluids.DIESEL);
		conv.ff.setFluid(LeafiaFF.toFF(Fluids.DIESEL, 3000));
		conv.ntmf.setFill(2500);
		helper.getBlockState(p).useItemOn(id, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(conv.getFluidType() == Fluids.KEROSENE && conv.ntmf.getTankType() == Fluids.KEROSENE, "the converter retyped both tanks");
		helper.assertTrue(conv.ff.isEmpty() && conv.ntmf.getFill() == 0, "and emptied both: " + conv.ff.getFluidAmount() + " / " + conv.ntmf.getFill());

		helper.setBlock(p, facing(LeafiaBlocks.ff_filter.get(), Direction.EAST));
		FFFilterTE2 filter = (FFFilterTE2) helper.getBlockEntity(p);
		helper.getBlockState(p).useItemOn(id, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(filter.getFluidType() == Fluids.KEROSENE, "the filter took it");

		ItemStack stick = new ItemStack(net.minecraft.world.item.Items.STICK);
		helper.assertTrue(helper.getBlockState(p).useItemOn(stick, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit)
				== ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "anything else passes");
		discardDrops(helper, player);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the pump

	/**
	 * Pull from behind: a handler on the pump's back that gives its fluid is drained into the tank, up to the
	 * room; one that holds the fluid under a tag is not asked for the right stack and gives nothing.
	 */
	@GameTest(template = EMPTY16)
	public static void thePumpDrawsFromAHandlerBehindIt(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(3, 2, 3), pp = ej.east();
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.EAST));
		helper.setBlock(pp, facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(pp);
		pump.setType(Fluids.DIESEL);
		e.tank.setFluid(LeafiaFF.toFF(Fluids.DIESEL, 700)); // the ejector's tank holds 1,000
		pump.update();
		helper.assertTrue(pump.tank.getFluidAmount() == 700 && e.tank.isEmpty(), "700 mB moved back to front: " + pump.tank.getFluidAmount() + " / " + e.tank.getFluidAmount());
		helper.assertTrue(LeafiaFF.fromFF(pump.tank.getFluid()) == Fluids.DIESEL, "and it is diesel");

		// A tagged salt is not drawn: the pump asks for a plain stack, and a tank matches components as well as the fluid.
		pump.setType(LeafiaFluids.FLUORIDE);
		e.tank.setFluid(saltMix(1000, 20, mix("th232", 1.0)));
		pump.update();
		helper.assertTrue(pump.tank.isEmpty() && e.tank.getFluidAmount() == 1000, "the tagged salt stayed: " + pump.tank.getFluidAmount() + " / " + e.tank.getFluidAmount());
		// An untagged salt is.
		e.tank.setFluid(LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 1000));
		pump.update();
		helper.assertTrue(pump.tank.getFluidAmount() == 1000 && e.tank.isEmpty(), "an untagged one is drawn");
		helper.succeed();
	}

	/**
	 * Push ahead: the whole tank is offered to a handler in front, what it refuses stays, and a pump whose front is
	 * a handler never reaches the duct case.
	 */
	@GameTest(template = EMPTY16)
	public static void thePumpFillsAHandlerAheadAndKeepsWhatItRefuses(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos pp = new BlockPos(3, 2, 5), pl = pp.east();
		helper.setBlock(pp, facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		helper.setBlock(pl, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.WEST));
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(pp);
		MSRPlugTE plug = (MSRPlugTE) helper.getBlockEntity(pl);
		pump.setType(LeafiaFluids.FLUORIDE);
		pump.tank.setFluid(saltMix(3000, 10, mix("u235", 1.0)));
		pump.update();
		helper.assertTrue(plug.tank.getFluidAmount() == 3000 && pump.tank.isEmpty(), "all 3000 mB went in: " + plug.tank.getFluidAmount() + " / " + pump.tank.getFluidAmount());
		helper.assertTrue(mixtureOf(plug.tank.getFluid()).getOrDefault("u235", 0d) == 1.0, "tag and all");

		plug.tank.setFluid(saltMix(9000, 10, mix("u235", 1.0)));
		pump.tank.setFluid(saltMix(3000, 10, mix("u235", 1.0)));
		pump.update();
		helper.assertTrue(plug.tank.getFluidAmount() == 10000 && pump.tank.getFluidAmount() == 2000,
				"the plug had room for 1000: " + plug.tank.getFluidAmount() + " / " + pump.tank.getFluidAmount());
		helper.succeed();
	}

	/** The decoy handler: on the facing's axis or unsided, no tanks, and nothing in or out. */
	@GameTest(template = EMPTY16)
	public static void theUtilitiesExposeADecoyHandlerOnTheirAxis(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(5, 3, 5);
		BlockPos abs = helper.absolutePos(p);
		for(var block : List.of(LeafiaBlocks.ff_pump.get(), LeafiaBlocks.ff_converter.get())) {
			helper.setBlock(p, facing(block, Direction.NORTH));
			for(Direction d : Direction.values()) {
				var handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs, d);
				helper.assertTrue((handler != null) == (d.getAxis() == Direction.Axis.Z), block + " side " + d + ": " + handler);
			}
			var unsided = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs, null);
			helper.assertTrue(unsided != null && unsided.getTanks() == 0, block + " unsided, no tanks");
			helper.assertTrue(unsided.fill(LeafiaFF.toFF(Fluids.DIESEL, 100), FluidAction.EXECUTE) == 0
					&& unsided.drain(100, FluidAction.EXECUTE).isEmpty()
					&& unsided.drain(LeafiaFF.toFF(Fluids.DIESEL, 100), FluidAction.EXECUTE).isEmpty(), block + " takes and gives nothing");
		}
		helper.setBlock(p, facing(LeafiaBlocks.ff_filter.get(), Direction.NORTH));
		helper.assertTrue(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs, Direction.NORTH) == null
				&& helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs, null) == null, "the filter is no handler at all");
		helper.succeed();
	}

	/**
	 * The pump as a buffer in a line: an ejector, two ducts, the pump, two ducts and a plug, all of FLiBe. The salt
	 * crosses the pump, tag and all, and none is lost.
	 */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void saltCrossesAPumpBetweenTwoDuctRuns(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(2, 2, 9);
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.EAST));
		for(int i : new int[] { 1, 2, 4, 5 }) {
			helper.setBlock(ej.east(i), LeafiaBlocks.ff_duct.get());
			((FFDuctTE) helper.getBlockEntity(ej.east(i))).setType(LeafiaFluids.FLUORIDE);
		}
		helper.setBlock(ej.east(3), facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(ej.east(3));
		pump.setType(LeafiaFluids.FLUORIDE);
		BlockPos pl = ej.east(6);
		helper.setBlock(pl, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.WEST));
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		MSRPlugTE plug = (MSRPlugTE) helper.getBlockEntity(pl);
		e.tank.setFluid(saltMix(1000, 0, mix("u235", 1.0)));
		TickWindow.sequence(helper).thenWaitUntil(() -> {
			helper.assertTrue(plug.tank.getFluidAmount() == 1000, "the plug has " + plug.tank.getFluidAmount() + " mB");
			helper.assertTrue(e.tank.isEmpty() && pump.tank.isEmpty(), "and nothing is left behind: " + e.tank.getFluidAmount() + " / " + pump.tank.getFluidAmount());
			helper.assertTrue(mixtureOf(plug.tank.getFluid()).getOrDefault("u235", 0d) == 1.0, "carrying its mixture");
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ the converter

	/** One millibucket for one, up to the Forge tank's room, nothing else changed. */
	@GameTest(template = EMPTY16)
	public static void theConverterMovesNtmFluidIntoTheForgeTankOneForOne(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos p = new BlockPos(5, 2, 5);
		helper.setBlock(p, facing(LeafiaBlocks.ff_converter.get(), Direction.EAST));
		FFConverterTE conv = (FFConverterTE) helper.getBlockEntity(p);
		conv.update();
		helper.assertTrue(conv.ff.isEmpty() && conv.ntmf.getFill() == 0, "with no fluid set it does nothing");

		conv.setType(Fluids.DIESEL);
		conv.ntmf.setFill(4000);
		conv.update();
		helper.assertTrue(conv.ff.getFluidAmount() == 4000 && conv.ntmf.getFill() == 0, "4000 across: " + conv.ff.getFluidAmount() + " / " + conv.ntmf.getFill());
		helper.assertTrue(LeafiaFF.fromFF(conv.ff.getFluid()) == Fluids.DIESEL && !LeafiaFF.hasTag(conv.ff.getFluid()), "it is plain diesel, no tag");

		conv.ntmf.setFill(8000);
		conv.update();
		helper.assertTrue(conv.ff.getFluidAmount() == 10000 && conv.ntmf.getFill() == 2000, "only the room: " + conv.ff.getFluidAmount() + " / " + conv.ntmf.getFill());
		conv.update();
		helper.assertTrue(conv.ff.getFluidAmount() == 10000 && conv.ntmf.getFill() == 2000, "and nothing more while it is full");
		helper.assertTrue(conv.getReceivingTanks()[0] == conv.ntmf && conv.getSendingTanks()[0] == conv.ntmf && conv.getAllTanks()[0] == conv.ntmf,
				"the base network sees the NTM tank in all three roles");
		helper.assertTrue(conv.getSendingTank(null) == conv.ff, "and the Forge network the other");
		helper.succeed();
	}

	/**
	 * The bridge the converter exists for: fluid the base mod's network delivers to the back becomes Forge fluid
	 * in front, crosses a duct and arrives in a pump - the base network, the converter and the Forge network end to end.
	 */
	@GameTest(template = EMPTY16, timeoutTicks = TickWindow.TIMEOUT)
	public static void fluidCrossesFromTheBaseNetworkIntoTheForgeNetwork(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos duct = new BlockPos(2, 2, 12);
		helper.setBlock(duct, ModBlocks.fluid_duct_neo.get().defaultBlockState());
		((BlockEntityPipeBaseNT) helper.getBlockEntity(duct)).setType(Fluids.DIESEL);
		BlockPos cv = duct.east();
		helper.setBlock(cv, facing(LeafiaBlocks.ff_converter.get(), Direction.EAST));
		FFConverterTE conv = (FFConverterTE) helper.getBlockEntity(cv);
		conv.setType(Fluids.DIESEL);
		helper.setBlock(cv.east(), LeafiaBlocks.ff_duct.get());
		((FFDuctTE) helper.getBlockEntity(cv.east())).setType(Fluids.DIESEL);
		helper.setBlock(cv.east(2), facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(cv.east(2));
		pump.setType(Fluids.DIESEL);
		helper.setBlock(cv.east(3), LeafiaBlocks.ff_duct.get());
		((FFDuctTE) helper.getBlockEntity(cv.east(3))).setType(Fluids.DIESEL);

		FluidTank source = new FluidTank(Fluids.DIESEL, 5000);
		source.setFill(2000);
		IFluidStandardSenderMK2 sender = new IFluidStandardSenderMK2() {
			@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { source }; }
			@Override public FluidTank[] getAllTanks() { return new FluidTank[] { source }; }
			@Override public boolean isLoaded() { return true; }
		};
		BlockPos ductAbs = helper.absolutePos(duct);
		TickWindow.sequence(helper).thenExecuteFor(30, () -> sender.tryProvide(Fluids.DIESEL, 0, helper.getLevel(), ductAbs, Direction.WEST))
				.thenWaitUntil(() -> {
					helper.assertTrue(pump.tank.getFluidAmount() == 2000, "the pump has " + pump.tank.getFluidAmount() + " mB");
					helper.assertTrue(source.getFill() == 0 && conv.ntmf.getFill() == 0 && conv.ff.isEmpty(),
							"and nothing is left behind: " + source.getFill() + " / " + conv.ntmf.getFill() + " / " + conv.ff.getFluidAmount());
				}).thenSucceed();
	}

	// ------------------------------------------------------------------ the filter

	/**
	 * The filter between a provider and a receiver: all of one fuel's concentration moves from the sender's mixture
	 * to the receiver's, scaled by the ratio of the amounts; the rest, the heat and the amounts stay.
	 */
	@GameTest(template = EMPTY16)
	public static void theFilterMovesOneFuelsShareIntoTheReceiversMixture(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(3, 2, 7), fp = ej.east(), pl = fp.east();
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.EAST));
		helper.setBlock(fp, facing(LeafiaBlocks.ff_filter.get(), Direction.EAST));
		helper.setBlock(pl, LeafiaBlocks.msr_plug.get().defaultBlockState().setValue(MSRPlugBlock.FACING, Direction.WEST));
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		MSRPlugTE plug = (MSRPlugTE) helper.getBlockEntity(pl);
		FFFilterTE2 filter = (FFFilterTE2) helper.getBlockEntity(fp);
		helper.assertTrue(filter.getFilter() == MSRFuel.u235, "a new filter is set to u235");

		filter.update();
		helper.assertTrue(!filter.valid, "with no fluid set it is not valid");
		filter.setType(LeafiaFluids.FLUORIDE);
		filter.update();
		helper.assertTrue(filter.valid, "both machines are there, so valid, even with empty tanks");

		e.tank.setFluid(saltMix(600, 50, mix("u235", 2.0, "th232", 1.0)));
		plug.tank.setFluid(saltMix(200, 7, mix("u233", 0.5)));
		filter.update();
		Map<String, Double> sent = mixtureOf(e.tank.getFluid()), got = mixtureOf(plug.tank.getFluid());
		near(helper, got.getOrDefault("u235", -1d), 6.0, "2.0 per bucket x 600/200");
		near(helper, got.getOrDefault("u233", -1d), 0.5, "the receiver's own fuel is untouched");
		helper.assertTrue(!sent.containsKey("u235"), "the sender lost the fuel's entry: " + sent);
		near(helper, sent.getOrDefault("th232", -1d), 1.0, "and kept the other");
		helper.assertTrue(e.tank.getFluidAmount() == 600 && plug.tank.getFluidAmount() == 200, "no fluid moved");
		near(helper, MSRTEBase.nbtProtocol(e.tank.getFluid()).getDouble("heat"), 50, "the sender's heat");
		near(helper, MSRTEBase.nbtProtocol(plug.tank.getFluid()).getDouble("heat"), 7, "the receiver's heat");

		filter.update();
		near(helper, mixtureOf(plug.tank.getFluid()).getOrDefault("u235", -1d), 6.0, "a second pass finds nothing more to move");

		filter.setFilter(MSRFuel.th232);
		filter.update();
		near(helper, mixtureOf(plug.tank.getFluid()).getOrDefault("th232", -1d), 3.0, "another fuel moves the same way");
		helper.assertTrue(!mixtureOf(e.tank.getFluid()).containsKey("th232"), "and leaves the sender");

		filter.setFilter(MSRFuel.pa234);
		filter.update();
		helper.assertTrue(!mixtureOf(plug.tank.getFluid()).containsKey("pa234"), "a fuel the sender has not is not invented");

		helper.setBlock(pl, Blocks.AIR);
		filter.update();
		helper.assertTrue(!filter.valid, "with nothing in front it is not valid");
		helper.succeed();
	}

	/** The filter's item steps through the five fuels and round, and the block takes the fuel off the stack. */
	@GameTest(template = EMPTY16)
	public static void theFilterItemCyclesFuelsAndTheBlockTakesTheOneOnTheStack(GameTestHelper helper) {
		helper.assertTrue(FFFilterItem.next(new ItemStack(net.minecraft.world.item.Items.STICK)) == MSRFuel.th232, "an unset item starts at the first fuel");
		if(!on()) { helper.succeed(); return; }
		ServerPlayer player = mockPlayer(helper);
		ItemStack stack = new ItemStack(LeafiaBlocks.ff_filter.get());
		helper.assertTrue(FFFilterItem.getFilter(stack) == null && FFFilterItem.rawFilter(stack) == null, "a fresh item names no fuel");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		MSRFuel[] seen = new MSRFuel[7];
		for(int i = 0; i < seen.length; i++) {
			stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			seen[i] = FFFilterItem.getFilter(stack);
		}
		helper.assertTrue(List.of(seen).equals(List.of(MSRFuel.th232, MSRFuel.pa233, MSRFuel.u233, MSRFuel.u235, MSRFuel.pa234, MSRFuel.th232, MSRFuel.pa233)),
				"five fuels and round: " + List.of(seen));

		// A name that is no fuel: the key is there, the fuel is not, and the next click starts again.
		CompoundTag bogus = new CompoundTag();
		bogus.putString("filter", "bogus");
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(bogus));
		helper.assertTrue(FFFilterItem.rawFilter(stack).equals("bogus") && FFFilterItem.getFilter(stack) == null, "an unreadable filter");
		helper.assertTrue(FFFilterItem.next(stack) == MSRFuel.th232, "starts again at the first");

		// Using it on a block: unset it passes (so the click steps it), set it places.
		BlockPos p = new BlockPos(5, 2, 5);
		BlockPos abs = helper.absolutePos(p);
		helper.setBlock(p.below(), Blocks.STONE);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP, abs.below(), false);
		ItemStack unset = new ItemStack(LeafiaBlocks.ff_filter.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, unset);
		helper.assertTrue(unset.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, unset, hit)) == InteractionResult.PASS,
				"an unset filter passes on a block");
		helper.assertTrue(helper.getBlockState(p).isAir(), "and places nothing");

		ItemStack set = new ItemStack(LeafiaBlocks.ff_filter.get());
		FFFilterItem.setFilter(set, MSRFuel.u233);
		player.setItemInHand(InteractionHand.MAIN_HAND, set);
		InteractionResult placed = set.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, set, hit));
		helper.assertTrue(placed.consumesAction(), "a set filter places: " + placed);
		helper.assertTrue(helper.getBlockState(p).is(LeafiaBlocks.ff_filter.get()), "the block is there");
		helper.assertTrue(((FFFilterTE2) helper.getBlockEntity(p)).getFilter() == MSRFuel.u233, "carrying the stack's fuel");
		helper.assertTrue(helper.getBlockState(p).getValue(FFDuctUtilityBase.FACING) == Direction.DOWN, "pointing into the face it was placed on");

		// A placement with no fuel (a dispenser, a command) breaks again at once.
		BlockPos q = new BlockPos(8, 2, 5);
		helper.setBlock(q, facing(LeafiaBlocks.ff_filter.get(), Direction.EAST));
		LeafiaBlocks.ff_filter.get().setPlacedBy(helper.getLevel(), helper.absolutePos(q), helper.getBlockState(q), null, new ItemStack(LeafiaBlocks.ff_filter.get()));
		helper.assertTrue(helper.getBlockState(q).isAir(), "an unset stack destroys the block it placed");
		LeafiaBlocks.ff_filter.get().setPlacedBy(helper.getLevel(), helper.absolutePos(p), helper.getBlockState(p), null, set);
		helper.assertTrue(helper.getBlockState(p).is(LeafiaBlocks.ff_filter.get()), "a set one leaves it");
		discardDrops(helper, player);
		for(net.minecraft.world.entity.item.ItemEntity item : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(helper.absolutePos(q)).inflate(4)))
			item.discard();
		helper.succeed();
	}

	/**
	 * The older filter nothing creates, built by hand: it draws a fuel's share out of the provider behind it, and
	 * (upstream's) writes the sender back with a tag that has no heat.
	 */
	@GameTest(template = EMPTY16)
	public static void theDormantFilterDrawsAFuelsShareOutOfAProvider(GameTestHelper helper) {
		if(!msr()) { helper.succeed(); return; }
		BlockPos ej = new BlockPos(3, 2, 10), fp = ej.east();
		helper.setBlock(ej, LeafiaBlocks.msr_ejector.get().defaultBlockState().setValue(MSREjectorBlock.FACING, Direction.EAST));
		helper.setBlock(fp, facing(LeafiaBlocks.ff_filter.get(), Direction.EAST));
		MSREjectorTE e = (MSREjectorTE) helper.getBlockEntity(ej);
		FFFilterTE old = new FFFilterTE(LeafiaBlockEntities.FF_FILTER.get(), helper.absolutePos(fp), helper.getBlockState(fp));
		old.setLevel(helper.getLevel());
		old.setType(LeafiaFluids.FLUORIDE);
		old.filter = MSRFuel.u235;
		e.tank.setFluid(saltMix(800, 33, mix("u235", 3.0, "th232", 1.0)));
		old.update();
		helper.assertTrue(old.tank.getFluidAmount() == 600, "(int)(800 x 3/4) = 600 drawn: " + old.tank.getFluidAmount());
		near(helper, mixtureOf(old.tank.getFluid()).getOrDefault("u235", -1d), 3.0, "its concentration, 3 x 600/(800 x 3/4)");
		helper.assertTrue(e.tank.getFluidAmount() == 200, "the sender keeps 200: " + e.tank.getFluidAmount());
		Map<String, Double> left = mixtureOf(e.tank.getFluid());
		near(helper, left.getOrDefault("u235", -1d), 0.0, "its entry for the fuel is zero");
		near(helper, left.getOrDefault("th232", -1d), 1.0, "and the other is as it was");
		helper.assertTrue(!LeafiaFF.getTag(e.tank.getFluid()).contains("heat"), "upstream's rewrite drops the sender's heat: " + LeafiaFF.getTag(e.tank.getFluid()));
		old.update();
		helper.assertTrue(old.tank.getFluidAmount() == 600, "with its own tank full of something it draws no more");
		helper.succeed();
	}

	// ------------------------------------------------------------------ saving and syncing

	/** The fluid, the tanks and the fuel survive a save; the update tag carries the fluid; the packet handlers rebuild the tanks. */
	@GameTest(template = EMPTY16)
	public static void theUtilitiesKeepTheirStateThroughASaveAndTheClientPackets(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		var access = helper.getLevel().registryAccess();
		BlockPos p = new BlockPos(5, 2, 5);

		helper.setBlock(p, facing(LeafiaBlocks.ff_pump.get(), Direction.EAST));
		FFPumpTE pump = (FFPumpTE) helper.getBlockEntity(p);
		pump.setType(Fluids.DIESEL);
		FluidStack tagged = LeafiaFF.toFF(Fluids.DIESEL, 1234);
		CompoundTag tag = new CompoundTag();
		tag.putDouble("heat", 5);
		LeafiaFF.setTag(tagged, tag);
		pump.tank.setFluid(tagged);
		CompoundTag saved = pump.saveWithoutMetadata(access);
		helper.assertTrue(saved.getInt("fluid") == Fluids.DIESEL.getID() && saved.contains("buffer"), "saved as upstream's \"fluid\" and \"buffer\": " + saved);
		FFPumpTE loaded = new FFPumpTE(LeafiaBlockEntities.FF_PUMP.get(), helper.absolutePos(p), helper.getBlockState(p));
		loaded.loadWithComponents(saved, access);
		helper.assertTrue(loaded.getFluidType() == Fluids.DIESEL && loaded.tank.getFluidAmount() == 1234
				&& LeafiaFF.getTag(loaded.tank.getFluid()).getDouble("heat") == 5, "the pump came back whole");
		helper.assertTrue(pump.getUpdateTag(access).getInt("fluid") == Fluids.DIESEL.getID(), "a client is told the fluid by the update tag");

		// What a client builds from the per-tick packet (keys 0 and 1, and 31 for the type).
		FFPumpTE client = new FFPumpTE(LeafiaBlockEntities.FF_PUMP.get(), helper.absolutePos(p), helper.getBlockState(p));
		client.onReceivePacketLocal((byte) 31, Fluids.DIESEL.getID());
		client.onReceivePacketLocal((byte) 0, 777);
		client.onReceivePacketLocal((byte) 1, tag);
		helper.assertTrue(client.getFluidType() == Fluids.DIESEL && client.tank.getFluidAmount() == 777
				&& LeafiaFF.getTag(client.tank.getFluid()).getDouble("heat") == 5, "the client's pump has 777 mB with the tag");
		client.onReceivePacketLocal((byte) 31, Fluids.NONE.getID());
		client.onReceivePacketLocal((byte) 0, 5);
		helper.assertTrue(client.tank.isEmpty(), "a pump with no Forge fluid shows an empty tank");

		helper.setBlock(p, facing(LeafiaBlocks.ff_converter.get(), Direction.EAST));
		FFConverterTE conv = (FFConverterTE) helper.getBlockEntity(p);
		conv.setType(Fluids.KEROSENE);
		conv.ff.setFluid(LeafiaFF.toFF(Fluids.KEROSENE, 3000));
		conv.ntmf.setFill(4500);
		CompoundTag cs = conv.saveWithoutMetadata(access);
		FFConverterTE cl = new FFConverterTE(LeafiaBlockEntities.FF_CONVERTER.get(), helper.absolutePos(p), helper.getBlockState(p));
		cl.loadWithComponents(cs, access);
		helper.assertTrue(cl.getFluidType() == Fluids.KEROSENE && cl.ff.getFluidAmount() == 3000 && cl.ntmf.getFill() == 4500
				&& cl.ntmf.getTankType() == Fluids.KEROSENE, "the converter came back whole: " + cs);
		FFConverterTE client2 = new FFConverterTE(LeafiaBlockEntities.FF_CONVERTER.get(), helper.absolutePos(p), helper.getBlockState(p));
		client2.onReceivePacketLocal((byte) 31, Fluids.KEROSENE.getID());
		client2.onReceivePacketLocal((byte) 0, 100);
		client2.onReceivePacketLocal((byte) 2, 200);
		helper.assertTrue(client2.ff.getFluidAmount() == 100 && client2.ntmf.getFill() == 200, "keys 0 and 2 are the two tanks");

		helper.setBlock(p, facing(LeafiaBlocks.ff_filter.get(), Direction.EAST));
		FFFilterTE2 filter = (FFFilterTE2) helper.getBlockEntity(p);
		filter.setType(LeafiaFluids.FLUORIDE == null ? Fluids.DIESEL : LeafiaFluids.FLUORIDE);
		filter.setFilter(MSRFuel.pa233);
		CompoundTag fs = filter.saveWithoutMetadata(access);
		helper.assertTrue("pa233".equals(fs.getString("filter")), "the fuel is saved by name: " + fs);
		FFFilterTE2 fl = new FFFilterTE2(LeafiaBlockEntities.FF_FILTER.get(), helper.absolutePos(p), helper.getBlockState(p));
		fl.loadWithComponents(fs, access);
		helper.assertTrue(fl.getFilter() == MSRFuel.pa233, "and read back");
		fs.putString("filter", "bogus");
		FFFilterTE2 bad = new FFFilterTE2(LeafiaBlockEntities.FF_FILTER.get(), helper.absolutePos(p), helper.getBlockState(p));
		bad.loadWithComponents(fs, access);
		helper.assertTrue(bad.getFilter() == MSRFuel.u235, "an unreadable one leaves the default");
		FFFilterTE2 clientFilter = new FFFilterTE2(LeafiaBlockEntities.FF_FILTER.get(), helper.absolutePos(p), helper.getBlockState(p));
		clientFilter.onReceivePacketLocal((byte) 0, true);
		clientFilter.onReceivePacketLocal((byte) 2, MSRFuel.u233.ordinal());
		helper.assertTrue(clientFilter.valid && clientFilter.getFilter() == MSRFuel.u233, "keys 0 and 2 are the flag and the fuel");

		helper.assertTrue(FFDuctUtilityTEBase.class.isAssignableFrom(FFPumpTE.class), "they share upstream's base");
		helper.succeed();
	}
}
