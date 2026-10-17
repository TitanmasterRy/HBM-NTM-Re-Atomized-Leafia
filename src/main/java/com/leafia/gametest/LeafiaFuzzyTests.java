package com.leafia.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.hbm.blockentity.network.BlockEntityPipeBaseNT;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.gear.utility.fuzzy.FuzzyOverlay;
import com.leafia.dev.custompacket.LeafiaPacketTypes;
import com.leafia.lib.LeafiaRef;

import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fuzzy identifier: what it remembers, what it stamps, and the one thing it makes work that
 * nothing else in the project can.
 *
 * <h2>The claim worth testing</h2>
 *
 * Not "the item exists". Five <em>base-mod</em> machines read
 * {@code slots[n].getItem() instanceof IItemFluidIdentifier} to decide what their tank holds,
 * and until this item landed nothing in the game satisfied it — so those slots have been inert
 * since they were written. {@link #theItemAnswersTheBaseModsFluidIdentifierSlot} makes exactly
 * the call those five make, argument for argument, because that is the call that has to keep
 * working: an item that quietly stopped implementing the interface would break five machines
 * and nothing would say so.
 *
 * <h2>The flood fill is where the bugs would be</h2>
 *
 * Four tests cover it and each one can fail on its own: it must convert a run, it must stop at a
 * duct carrying something else, it must respect its step budget, and it must not run at all when
 * the held fluid already matches. Upstream's guard for the last is an {@code if} around the whole
 * branch rather than a check inside the fill, which is a difference that only shows when the
 * sneak modifier is involved.
 *
 * <h2>And one test reads a PNG</h2>
 *
 * {@link #overlayRectanglesLandOnTransparentTexels} is the only way to check the renderer's forty
 * magic numbers without a screen. Every rectangle the overlay draws has to land on a transparent
 * part of the item sprite, because that is the only place it can be seen — mistype one and the
 * readout half-vanishes behind the magnifying glass with nothing logged. The numbers live in
 * {@link FuzzyOverlay} rather than the renderer precisely so this test can reach them: the
 * renderer is client-only and this runs on a dedicated server.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFuzzyTests {

	private static final String EMPTY = "empty";

	/**
	 * The exact call {@code BlockEntityMachineCoker}, {@code ...Diesel}, {@code ...Hydrotreater},
	 * {@code ...VacuumDistill} and {@code BlockEntityHeaterHeatex} make.
	 *
	 * <p>Their line is {@code identifier.getType(null, BlockPos.ZERO, slots[n])} — a null level
	 * and a zero position, because a machine reading a slot has no click to report. An
	 * implementation that reached for either would throw there and nowhere else.
	 */
	@GameTest(template = EMPTY)
	public static void theItemAnswersTheBaseModsFluidIdentifierSlot(GameTestHelper helper) {

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());

		helper.assertTrue(stack.getItem() instanceof IItemFluidIdentifier,
				"the five machines find their identifier with an instanceof; without the "
						+ "interface their fluid-ID slots go back to being inert");

		IItemFluidIdentifier identifier = (IItemFluidIdentifier) stack.getItem();

		helper.assertTrue(identifier.getType(null, BlockPos.ZERO, stack) == Fluids.NONE,
				"an unset identifier must answer NONE, not null - the machines assign the answer "
						+ "straight into setTankType");

		FuzzyIdentifierItem.setType(stack, Fluids.LUBRICANT);

		helper.assertTrue(identifier.getType(null, BlockPos.ZERO, stack) == Fluids.LUBRICANT,
				"a set identifier must answer through the base mod's interface with a null level, "
						+ "got " + identifier.getType(null, BlockPos.ZERO, stack).getName());

		helper.succeed();
	}

	/**
	 * A stack round-trips through its component, and an unknown name reads as NONE.
	 *
	 * <p>The second half pins the upstream bug the item's class doc flags: {@code Fluids.fromName}
	 * answers NONE rather than null, so the red "No fluids set!" tooltip line behind
	 * {@code if(f != null)} can never be shown. The test asserts the <em>bug</em>, so that
	 * anybody who later makes that branch reachable has to come here and say why.
	 */
	@GameTest(template = EMPTY)
	public static void anUnknownFluidNameReadsAsNoneRatherThanNull(GameTestHelper helper) {

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());

		FuzzyIdentifierItem.setType(stack, Fluids.WATER);
		helper.assertTrue(FuzzyIdentifierItem.getType(stack) == Fluids.WATER,
				"the fluid must survive being written to and read from the stack");

		helper.assertTrue(Fluids.fromName("not_a_fluid") == Fluids.NONE,
				"upstream's tooltip guards on f != null; this is why that branch is dead");
		helper.assertTrue(FuzzyIdentifierItem.getType(new ItemStack(LeafiaItems.fuzzy_identifier.get()))
						== Fluids.NONE,
				"a stack with no component at all also reads NONE, so getType never returns null");

		helper.succeed();
	}

	/** Sneak-clicking a duct copies that duct's fluid onto the tool. */
	@GameTest(template = EMPTY)
	public static void sneakClickingADuctCopiesItsFluid(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		BlockEntityPipeBaseNT duct = duct(helper, pos, Fluids.DIESEL);

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(stack, Fluids.WATER);

		use(helper, pos, stack, true);

		helper.assertTrue(FuzzyIdentifierItem.getType(stack) == Fluids.DIESEL,
				"a sneak-click copies the duct's fluid onto the tool, got "
						+ FuzzyIdentifierItem.getType(stack).getName());
		helper.assertTrue(duct.getFluidType() == Fluids.DIESEL,
				"and leaves the duct alone, got " + duct.getFluidType().getName());

		helper.succeed();
	}

	/** A plain click floods the whole connected run with the tool's fluid. */
	@GameTest(template = EMPTY)
	public static void clickingADuctFloodsTheRun(GameTestHelper helper) {

		List<BlockPos> run = new ArrayList<>();
		for(int x = 1; x <= 5; x++) {
			BlockPos pos = new BlockPos(x, 1, 1);
			duct(helper, pos, Fluids.WATER);
			run.add(pos);
		}

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(stack, Fluids.DIESEL);

		use(helper, run.get(2), stack, false);

		List<String> problems = new ArrayList<>();

		for(BlockPos pos : run) {
			FluidType now = ((BlockEntityPipeBaseNT) helper.getBlockEntity(pos)).getFluidType();
			if(now != Fluids.DIESEL) problems.add(pos + " is " + now.getName());
		}

		if(FuzzyIdentifierItem.getType(stack) != Fluids.DIESEL)
			problems.add("the tool changed to " + FuzzyIdentifierItem.getType(stack).getName()
					+ "; a plain click must not re-read it");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " duct(s) not converted: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The fill stops at a duct carrying something else, and does not jump over it.
	 *
	 * <p>This is the half that makes the tool usable rather than destructive: a run that meets a
	 * different fluid is a different run, and the click must not walk through the junction into
	 * somebody's oil line.
	 */
	@GameTest(template = EMPTY)
	public static void theFloodStopsAtADifferentFluid(GameTestHelper helper) {

		BlockPos a = new BlockPos(1, 1, 1);
		BlockPos b = new BlockPos(2, 1, 1);
		BlockPos wall = new BlockPos(3, 1, 1);
		BlockPos beyond = new BlockPos(4, 1, 1);

		duct(helper, a, Fluids.WATER);
		duct(helper, b, Fluids.WATER);
		duct(helper, wall, Fluids.LUBRICANT);
		duct(helper, beyond, Fluids.WATER);

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(stack, Fluids.DIESEL);

		use(helper, a, stack, false);

		List<String> problems = new ArrayList<>();

		if(typeAt(helper, a) != Fluids.DIESEL) problems.add("the clicked duct is " + typeAt(helper, a).getName());
		if(typeAt(helper, b) != Fluids.DIESEL) problems.add("its neighbour is " + typeAt(helper, b).getName());
		if(typeAt(helper, wall) != Fluids.LUBRICANT)
			problems.add("the lubricant duct was overwritten with " + typeAt(helper, wall).getName());
		if(typeAt(helper, beyond) != Fluids.WATER)
			problems.add("the fill jumped the junction; the far duct is " + typeAt(helper, beyond).getName());

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The step budget is a distance, and it is spent on every branch.
	 *
	 * <p>Called directly rather than through a click, because reaching upstream's own 256 would
	 * need 256 ducts. Two steps from the first duct reaches the third and no further.
	 */
	@GameTest(template = EMPTY)
	public static void theFloodIsBoundedByItsStepBudget(GameTestHelper helper) {

		for(int x = 1; x <= 5; x++) duct(helper, new BlockPos(x, 1, 1), Fluids.WATER);

		FuzzyIdentifierItem.spreadType(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
				Fluids.DIESEL, Fluids.WATER, 3);

		List<String> problems = new ArrayList<>();

		for(int x = 1; x <= 5; x++) {
			FluidType now = typeAt(helper, new BlockPos(x, 1, 1));
			// x = 1 spends the budget at 3, x = 2 at 2, x = 3 at 1; x = 4 is called with 0 and
			// returns before reading anything.
			FluidType want = x <= 3 ? Fluids.DIESEL : Fluids.WATER;
			if(now != want) problems.add("x=" + x + " is " + now.getName() + ", wanted " + want.getName());
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Upstream guards the <em>whole</em> branch on the fluids differing, so a click with a tool
	 * already set to the duct's fluid does nothing at all — including the sneak-click, which
	 * would otherwise be a no-op write anyway.
	 */
	@GameTest(template = EMPTY)
	public static void aMatchingFluidDoesNothing(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		duct(helper, pos, Fluids.WATER);

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		FuzzyIdentifierItem.setType(stack, Fluids.WATER);

		use(helper, pos, stack, false);

		helper.assertTrue(typeAt(helper, pos) == Fluids.WATER,
				"the duct must be untouched, got " + typeAt(helper, pos).getName());
		helper.assertTrue(FuzzyIdentifierItem.getType(stack) == Fluids.WATER,
				"and so must the tool");

		helper.succeed();
	}

	/**
	 * Sneak-clicking a machine reads a fluid off its sending tanks.
	 *
	 * <p>The condenser is the cheapest one to stand up — a plain one-block machine with one
	 * sending tank — and the fluid is read off the placed block entity rather than written into
	 * the test, so this pins the wiring rather than the condenser's own recipe.
	 */
	@GameTest(template = EMPTY)
	public static void sneakClickingAMachineReadsItsSendingTank(GameTestHelper helper) {

		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, ModBlocks.machine_condenser.get());

		if(!(helper.getBlockEntity(pos) instanceof IFluidStandardSenderMK2 sender)) {
			helper.fail("the condenser must be a fluid sender for this branch to be reachable", pos);
			return;
		}

		FluidTank[] tanks = sender.getSendingTanks();
		helper.assertTrue(tanks.length > 0, "the condenser must have at least one sending tank");

		FluidType want = tanks[0].getTankType();

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		use(helper, pos, stack, true);

		helper.assertTrue(FuzzyIdentifierItem.getType(stack) == want,
				"the tool must take the machine's sending-tank fluid (" + want.getName() + "), got "
						+ FuzzyIdentifierItem.getType(stack).getName());

		helper.succeed();
	}

	/**
	 * Every rectangle the overlay draws lands on a transparent texel of the item sprite.
	 *
	 * <p>See the class doc: this is the only check on the renderer's coordinates that does not
	 * need a screen, and the failure it catches is silent. A patch is checked over its whole
	 * area, not at a corner, because a rectangle that has crept one pixel into the glass would
	 * still have three clean corners.
	 */
	@GameTest(template = EMPTY)
	public static void overlayRectanglesLandOnTransparentTexels(GameTestHelper helper) {

		BufferedImage sprite;

		try(InputStream in = LeafiaFuzzyTests.class.getResourceAsStream(
				"/assets/leafia/textures/item/fuzzy_identifier.png")) {

			helper.assertTrue(in != null, "the fuzzy identifier's texture is missing from the jar");
			sprite = ImageIO.read(in);

		} catch(Exception e) {
			helper.fail("could not read the fuzzy identifier's texture: " + e);
			return;
		}

		helper.assertTrue(sprite.getWidth() == 16 && sprite.getHeight() == 16,
				"the overlay's coordinates are in texels of a 16x16 sprite; this one is "
						+ sprite.getWidth() + "x" + sprite.getHeight());

		List<String> problems = new ArrayList<>();

		for(int[] p : FuzzyOverlay.PATCHES) {
			opaqueTexels(sprite, p[0], p[1], p[2], p[3], problems, "patch");
		}

		for(int[] p : FuzzyOverlay.PIPS) {
			opaqueTexels(sprite, p[0], p[1], p[0] + 1, p[1] + 1, problems, "pip");
		}

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " overlay rectangle(s) sit behind opaque pixels and would be "
						+ "invisible: " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Both of the item's packets are registered, and at different ids.
	 *
	 * <p>{@code LeafiaPacketTypes} is a list whose index is the wire id, so a duplicate or a
	 * missing registration is a packet decoded as the wrong type rather than an error.
	 */
	@GameTest(template = EMPTY)
	public static void bothPacketsAreRegisteredAndDistinct(GameTestHelper helper) {

		helper.assertTrue(LeafiaPacketTypes.FUZZY_IDENTIFIER != LeafiaPacketTypes.FUZZY_RESPONSE,
				"the two fuzzy packets must not share an id");

		// Appended above the elevators' block, which LeafiaElevatorCarTests pins at 3..9. These
		// two ids are pinned here for the same reason: an insert above them renumbers them, and
		// the total staying right would hide it.
		helper.assertTrue(LeafiaPacketTypes.FUZZY_IDENTIFIER == 10,
				"FUZZY_IDENTIFIER must stay at 10, got " + LeafiaPacketTypes.FUZZY_IDENTIFIER);
		helper.assertTrue(LeafiaPacketTypes.FUZZY_RESPONSE == 11,
				"FUZZY_RESPONSE must stay at 11, got " + LeafiaPacketTypes.FUZZY_RESPONSE);
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.FUZZY_IDENTIFIER)
						instanceof FuzzyIdentifierItem.FuzzyIdentifierPacket,
				"id " + LeafiaPacketTypes.FUZZY_IDENTIFIER + " must resolve to the identifier packet");
		helper.assertTrue(LeafiaPacketTypes.byId(LeafiaPacketTypes.FUZZY_RESPONSE)
						instanceof FuzzyIdentifierItem.FuzzyIdentifierResponsePacket,
				"id " + LeafiaPacketTypes.FUZZY_RESPONSE + " must resolve to the response packet");

		helper.succeed();
	}

	/**
	 * The flicker pulses between half and full, and never blinks out.
	 *
	 * <p>Upstream divides by 200 over a period of 100, so only the first half of the curve is
	 * used. Matching the two numbers would look like a tidy-up and would take the readout to
	 * zero every tenth of a second. Sampled across a whole period rather than at one instant,
	 * because the value is a function of the wall clock.
	 */
	@GameTest(template = EMPTY)
	public static void theFlickerNeverLeavesItsBand(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();
		float lowest = 1F;

		for(long t = 0; t < FuzzyOverlay.FLICKER_PERIOD; t++) {

			// getDisplayAlpha reads the wall clock, so the offset is what is varied to walk the
			// curve: alpha(now - t) covers the same hundred values from wherever "now" is.
			float alpha = FuzzyOverlay.getDisplayAlpha(t);

			if(alpha > 1F || alpha < 0.5F) problems.add("offset " + t + " gives " + alpha);
			lowest = Math.min(lowest, alpha);
		}

		if(lowest > 0.75F)
			problems.add("the curve never dropped below " + lowest + "; it should reach ~0.5");

		helper.assertTrue(problems.isEmpty(),
				problems.size() + " problem(s) in the flicker curve: " + String.join("; ", problems));
		helper.succeed();
	}

	/// HELPERS ///

	/** Places a fluid duct and sets what it carries. */
	private static BlockEntityPipeBaseNT duct(GameTestHelper helper, BlockPos pos, FluidType type) {
		helper.setBlock(pos, ModBlocks.fluid_duct_neo.get());
		BlockEntityPipeBaseNT duct = (BlockEntityPipeBaseNT) helper.getBlockEntity(pos);
		duct.setType(type);
		return duct;
	}

	private static FluidType typeAt(GameTestHelper helper, BlockPos pos) {
		return ((BlockEntityPipeBaseNT) helper.getBlockEntity(pos)).getFluidType();
	}

	/**
	 * Clicks {@code pos} with {@code stack}, through the same hook a real click takes.
	 *
	 * <p>{@code onItemUseFirst} rather than {@code useBlock}: a real click goes through the
	 * NeoForge hook before the block ever sees it, and going in by the block's own path would
	 * test a route this item is not on.
	 */
	private static void use(GameTestHelper helper, BlockPos pos, ItemStack stack, boolean sneak) {

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.setShiftKeyDown(sneak);

		BlockPos abs = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);

		stack.getItem().onItemUseFirst(stack,
				new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
	}

	/**
	 * Records every opaque texel inside a rectangle.
	 *
	 * <p>The model's y runs upward from the bottom and the image's runs down from the top, so a
	 * model row {@code y} is image row {@code 15 - y}. Getting that backwards is the mistake this
	 * would otherwise be written to catch, so it is written once, here.
	 */
	private static void opaqueTexels(BufferedImage sprite, int x0, int y0, int x1, int y1,
			List<String> problems, String what) {

		for(int x = x0; x < x1; x++) {
			for(int y = y0; y < y1; y++) {

				if(x < 0 || x > 15 || y < 0 || y > 15) {
					problems.add(what + " (" + x0 + "," + y0 + ")-(" + x1 + "," + y1
							+ ") runs off the sprite at (" + x + "," + y + ")");
					continue;
				}

				int alpha = (sprite.getRGB(x, 15 - y) >>> 24) & 0xFF;

				if(alpha != 0) {
					problems.add(what + " (" + x0 + "," + y0 + ")-(" + x1 + "," + y1
							+ ") covers opaque texel (" + x + "," + y + "), alpha " + alpha);
				}
			}
		}
	}
}
