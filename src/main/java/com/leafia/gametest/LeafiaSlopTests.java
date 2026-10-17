package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.machine.storage.BlockEntityMachineBattery;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.BlockMachineBase;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.miscellanous.slop.SlopTE;
import com.leafia.contents.miscellanous.slop.container.SlopMenu;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaMenuTypes;
import com.leafia.lib.LeafiaRef;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The slop reactor: what makes it a reactor, what it costs to keep one, and what happens when you
 * do not (D-1082).
 *
 * <p>Every test is one body. A block entity ticks on its own every game tick, so a test that spread
 * a sequence across ticks would be racing the reactor it is testing; inside one body the world
 * does not advance, and a count of {@code updateEntity()} calls is a count of ticks. That is the
 * rule D-953 arrived at, and it is why numbers here are asserted as <em>differences</em> from a
 * reading taken a line earlier rather than as literals.
 *
 * <p>The two meltdown tests are in batches of their own. A meltdown clears a 17-block cube, and
 * tests in one batch run side by side, so a neighbour inside that cube would lose its blocks
 * under its own feet.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaSlopTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";

	// ---- the rig ---------------------------------------------------------------------------

	/**
	 * A reactor built from the rule rather than from {@code SlopTE}'s own code: a cell with two or
	 * more coordinates at +-2 from the centre is casing, one is glass, none is the core. The
	 * controller replaces the middle of the bottom edge on the side it faces.
	 */
	private record Rig(GameTestHelper helper, Direction facing, BlockPos centre, BlockPos pos, SlopTE slop) {

		Direction forward() { return facing.getOpposite(); }

		/** A cell of the core or the room beyond it, in controller-relative terms. */
		BlockPos cell(int ahead, int right, int up) {
			return pos.relative(forward(), ahead).relative(forward().getClockWise(), right).above(up);
		}

		/** Fills one 3x3 layer of the core, {@code up} blocks above the controller. */
		void layer(int up, BlockState state) {
			for(int right = -1; right <= 1; right++)
				for(int ahead = 1; ahead <= 3; ahead++)
					helper.setBlock(cell(ahead, right, up), state);
		}

		int count(int up, net.minecraft.world.level.block.Block block) {
			int n = 0;
			for(int right = -1; right <= 1; right++)
				for(int ahead = 1; ahead <= 3; ahead++)
					if(helper.getBlockState(cell(ahead, right, up)).is(block)) n++;
			return n;
		}
	}

	private static Rig build(GameTestHelper helper, Direction facing) {
		// Centre of the 5x5x5 at the middle of the template, which puts the controller at the
		// middle of whichever edge it faces.
		return build(helper, facing, new BlockPos(2, 2, 2));
	}

	private static Rig build(GameTestHelper helper, Direction facing, BlockPos centre) {

		Direction forward = facing.getOpposite();
		BlockPos pos = centre.relative(forward, -2).below(2);

		for(int x = -2; x <= 2; x++)
			for(int y = -2; y <= 2; y++)
				for(int z = -2; z <= 2; z++) {
					int edges = (Math.abs(x) == 2 ? 1 : 0) + (Math.abs(y) == 2 ? 1 : 0) + (Math.abs(z) == 2 ? 1 : 0);
					BlockPos at = centre.offset(x, y, z);
					if(at.equals(pos)) continue;
					if(edges >= 2) helper.setBlock(at, LeafiaBlocks.slop_reactor_casing.get());
					else if(edges == 1) helper.setBlock(at, LeafiaBlocks.slop_reactor_glass.get());
					else helper.setBlock(at, Blocks.AIR);
				}

		helper.setBlock(pos, LeafiaBlocks.slop_reactor.get().defaultBlockState().setValue(BlockMachineBase.FACING, facing));
		SlopTE slop = (SlopTE) helper.getBlockEntity(pos);
		helper.assertTrue(slop != null, "the controller has no block entity");
		return new Rig(helper, facing, centre, pos, slop);
	}

	/**
	 * Runs a call and reports whether it got as far as sending the screen payload to a mock
	 * connection, which refuses every payload it did not negotiate (the same wall
	 * {@code LeafiaEvCabinGuiTests} reads as "the opener opened").
	 */
	private static boolean reachedSending(Runnable call) {
		try {
			call.run();
			return false;
		} catch(UnsupportedOperationException e) {
			return e.getMessage() != null && e.getMessage().contains("neoforge:advanced_open_screen");
		}
	}

	private static ItemStack uranium(int n) {
		return new ItemStack(ModItems.ingot_uranium.get(), n);
	}

	private static BlockState toxic() {
		return ModBlocks.toxic_block.get().defaultBlockState();
	}

	/** A player the server does not broadcast to - see {@code LeafiaEvCabinGuiTests.mockPlayer}. */
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

	// ---- registration ----------------------------------------------------------------------

	/** Three blocks, one block entity type, one menu type, all in the one tab. */
	@GameTest(template = EMPTY)
	public static void theThreeBlocksAreRegisteredAndInTheTab(GameTestHelper helper) {

		helper.assertTrue(LeafiaFeatures.slopReactor, "slopReactor defaults on");

		for(String id : List.of("slop_reactor", "slop_reactor_casing", "slop_reactor_glass")) {
			var block = BuiltInRegistries.BLOCK.getOptional(LeafiaRef.rl(id));
			helper.assertTrue(block.isPresent(), id + " is not registered");
			helper.assertTrue(LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN).stream()
					.anyMatch(s -> s.get().asItem() == block.get().asItem()), id + " is in no creative tab");
		}

		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(LeafiaBlockEntities.SLOP.get())
				.equals(LeafiaRef.rl("slop_te")), "the block entity is upstream's slop_te");
		helper.assertTrue(LeafiaBlocks.slop_reactor.get().defaultBlockState().hasProperty(BlockMachineBase.FACING),
				"the controller has no facing");
		helper.assertTrue(!LeafiaBlocks.slop_reactor_casing.get().defaultBlockState().hasProperty(BlockMachineBase.FACING),
				"the casing is rotatable");
		helper.succeed();
	}

	/**
	 * What {@code BlockNTMGlass} gave the glass and {@code TransparentBlock} must: nothing hides
	 * behind it, light passes it, and it drops itself - for a bare hand as well.
	 */
	@GameTest(template = EMPTY)
	public static void theGlassIsClearAndDropsItself(GameTestHelper helper) {

		helper.setBlock(new BlockPos(1, 1, 1), LeafiaBlocks.slop_reactor_glass.get());
		BlockState glass = helper.getBlockState(new BlockPos(1, 1, 1));
		BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));

		helper.assertTrue(!glass.canOcclude(), "the glass occludes");
		helper.assertTrue(glass.propagatesSkylightDown(helper.getLevel(), abs), "skylight does not pass the glass");
		helper.assertTrue(glass.getLightBlock(helper.getLevel(), abs) == 0, "the glass dims light by " + glass.getLightBlock(helper.getLevel(), abs));
		helper.assertTrue(!glass.isRedstoneConductor(helper.getLevel(), abs), "the glass conducts redstone");

		LootParams.Builder params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(abs))
				.withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
		List<ItemStack> drops = glass.getDrops(params);
		helper.assertTrue(drops.size() == 1 && drops.get(0).is(LeafiaBlocks.slop_reactor_glass.get().asItem()),
				"the glass dropped " + drops);

		helper.succeed();
	}

	/** Only the casing needs a pickaxe, because only {@code AddonBlockBase} asked for one. */
	@GameTest(template = EMPTY)
	public static void onlyTheCasingNeedsAPickaxe(GameTestHelper helper) {

		helper.assertTrue(LeafiaBlocks.slop_reactor_casing.get().defaultBlockState().requiresCorrectToolForDrops(),
				"the casing drops to a bare hand");
		helper.assertTrue(!LeafiaBlocks.slop_reactor.get().defaultBlockState().requiresCorrectToolForDrops(),
				"the controller needs a tool");
		helper.assertTrue(!LeafiaBlocks.slop_reactor_glass.get().defaultBlockState().requiresCorrectToolForDrops(),
				"the glass needs a tool");
		helper.succeed();
	}

	// ---- the structure ---------------------------------------------------------------------

	/** The cube is behind the controller whichever way it faces, and only then. */
	@GameTest(template = EMPTY)
	public static void aBuiltCubeIsFormedInEveryFacing(GameTestHelper helper) {

		List<String> problems = new ArrayList<>();

		for(Direction facing : Direction.Plane.HORIZONTAL) {
			Rig rig = build(helper, facing);
			if(!rig.slop.checkFormed(false)) problems.add(facing + ": not formed, wrong cells " + rig.slop.invalidBlocks());
			if(!rig.centre.equals(rig.slop.center().subtract(helper.absolutePos(BlockPos.ZERO)))) problems.add(facing + ": centre is " + rig.slop.center());

			// Turned round, the cube is behind it and nothing is.
			helper.setBlock(rig.pos, LeafiaBlocks.slop_reactor.get().defaultBlockState()
					.setValue(BlockMachineBase.FACING, facing.getOpposite()));
			SlopTE turned = (SlopTE) helper.getBlockEntity(rig.pos);
			if(turned.checkFormed(false)) problems.add(facing + ": formed with its back to the cube");
		}

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every one of the 97 shell cells besides the controller's is checked, and for its own kind: swapping any casing for
	 * glass or glass for casing, or any of them for stone, reports that cell and no other.
	 */
	@GameTest(template = EMPTY)
	public static void everyShellCellMustBeTheRightKind(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		List<String> problems = new ArrayList<>();
		int shell = 0;

		for(int x = -2; x <= 2; x++)
			for(int y = -2; y <= 2; y++)
				for(int z = -2; z <= 2; z++) {
					BlockPos at = rig.centre.offset(x, y, z);
					if(at.equals(rig.pos) || (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1)) continue;
					shell++;

					BlockState right = helper.getBlockState(at);
					boolean wasCasing = right.is(LeafiaBlocks.slop_reactor_casing.get());
					BlockState swap = (wasCasing ? LeafiaBlocks.slop_reactor_glass : LeafiaBlocks.slop_reactor_casing).get().defaultBlockState();

					for(BlockState wrong : List.of(swap, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState())) {
						helper.setBlock(at, wrong);
						List<BlockPos> bad = rig.slop.invalidBlocks();
						if(bad.size() != 1 || !bad.get(0).equals(helper.absolutePos(at)))
							problems.add(at + " as " + wrong.getBlock() + " read " + bad);
					}
					helper.setBlock(at, right);
				}

		helper.assertTrue(shell == 97, "the shell has 98 cells with the controller's, 97 without, counted " + shell);
		helper.assertTrue(problems.isEmpty(), problems.size() + " cell(s) misread: " + problems.subList(0, Math.min(5, problems.size())));
		helper.assertTrue(rig.slop.checkFormed(false), "restoring every cell did not restore the reactor");
		helper.succeed();
	}

	/** The core holds air and toxic block; anything else in any of its 27 cells is wrong. */
	@GameTest(template = EMPTY)
	public static void theCoreMayHoldOnlyAirAndToxicBlock(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		List<String> problems = new ArrayList<>();

		for(int x = -1; x <= 1; x++)
			for(int y = -1; y <= 1; y++)
				for(int z = -1; z <= 1; z++) {
					BlockPos at = rig.centre.offset(x, y, z);

					helper.setBlock(at, toxic());
					if(!rig.slop.invalidBlocks().isEmpty()) problems.add(at + " refused a toxic block");
					helper.setBlock(at, Blocks.STONE);
					if(rig.slop.invalidBlocks().size() != 1) problems.add(at + " accepted stone");
					helper.setBlock(at, Blocks.WATER);
					if(rig.slop.invalidBlocks().size() != 1) problems.add(at + " accepted water");
					helper.setBlock(at, Blocks.AIR);
				}

		helper.assertTrue(problems.isEmpty(), problems.size() + " core cell(s) misread: " + problems);
		helper.succeed();
	}

	/** Clicking an unfinished reactor says so and does not open the menu; a finished one does. */
	@GameTest(template = EMPTY)
	public static void theMenuOpensOnlyOnABuiltReactor(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		try {
			ServerPlayer player = mockPlayer(helper);
			BlockPos abs = helper.absolutePos(rig.pos);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
			BlockState state = helper.getBlockState(rig.pos);

			BlockPos corner = rig.cell(0, 2, 0); // a corner of the shell, beside the controller
			helper.setBlock(corner, Blocks.AIR);
			InteractionResult[] unfinished = new InteractionResult[1];
			boolean opened = reachedSending(() -> unfinished[0] = state.useWithoutItem(helper.getLevel(), player, hit));
			helper.assertTrue(unfinished[0] != null && unfinished[0].consumesAction(), "a click on a reactor is always consumed, got " + unfinished[0]);
			helper.assertTrue(!opened && rig.slop.listeners.isEmpty(), "an unfinished reactor opened its menu");

			helper.setBlock(corner, LeafiaBlocks.slop_reactor_casing.get());
			helper.assertTrue(rig.slop.checkFormed(false), "the rebuilt reactor is not formed: " + rig.slop.invalidBlocks());

			opened = reachedSending(() -> state.useWithoutItem(helper.getLevel(), player, hit));
			helper.assertTrue(opened, "a built reactor did not try to open its menu");
			helper.assertTrue(rig.slop.listeners.contains(player), "the menu it built did not put the player on the listener list");

			// The mock connection refused the payload, so the menu was built and never installed. Take it
			// off the list, or the reactor's next real tick sends the mock a packet it refuses too.
			rig.slop.listeners.clear();
			helper.succeed();
		} finally {
			rig.slop.listeners.clear();
		}
	}

	// ---- the fuel --------------------------------------------------------------------------

	/** Nine ingots become the bottom layer, nine more the next, and a full core takes no more. */
	@GameTest(template = EMPTY)
	public static void uraniumIngotsBuildTheCoreOneLayerAtATime(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		List<String> problems = new ArrayList<>();

		slop.setItem(SlopTE.SLOT_FUEL, uranium(64));

		for(int layer = 1; layer <= 3; layer++) {
			slop.updateEntity();
			if(rig.count(layer, ModBlocks.toxic_block.get()) != 9) problems.add("layer " + layer + " has " + rig.count(layer, ModBlocks.toxic_block.get()));
			if(slop.getItem(SlopTE.SLOT_FUEL).getCount() != 64 - 9 * layer)
				problems.add("after layer " + layer + " there are " + slop.getItem(SlopTE.SLOT_FUEL).getCount() + " ingots");
			// updateFuel runs before the fill, so the count lags by the layer just built.
			if(slop.fuel != 9 * (layer - 1)) problems.add("fuel was " + slop.fuel + " after layer " + layer);
		}

		slop.updateEntity();
		helper.assertTrue(slop.fuel == 27, "a full core is 27, got " + slop.fuel);
		helper.assertTrue(slop.getItem(SlopTE.SLOT_FUEL).getCount() == 64 - 27, "a full core ate more: " + slop.getItem(SlopTE.SLOT_FUEL).getCount());
		helper.assertTrue(rig.count(4, ModBlocks.toxic_block.get()) == 0, "a fourth layer was built");

		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Eight ingots are not a layer, and nothing but uranium is. */
	@GameTest(template = EMPTY)
	public static void aLayerNeedsNineUraniumIngots(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;

		slop.setItem(SlopTE.SLOT_FUEL, uranium(8));
		slop.updateEntity();
		helper.assertTrue(rig.count(1, ModBlocks.toxic_block.get()) == 0 && slop.getItem(SlopTE.SLOT_FUEL).getCount() == 8,
				"eight ingots built a layer");

		slop.setItem(SlopTE.SLOT_FUEL, new ItemStack(Items.IRON_INGOT, 64));
		slop.updateEntity();
		helper.assertTrue(rig.count(1, ModBlocks.toxic_block.get()) == 0 && slop.getItem(SlopTE.SLOT_FUEL).getCount() == 64,
				"iron ingots built a layer");

		slop.setItem(SlopTE.SLOT_FUEL, uranium(9));
		slop.updateEntity();
		helper.assertTrue(rig.count(1, ModBlocks.toxic_block.get()) == 9 && slop.getItem(SlopTE.SLOT_FUEL).isEmpty(),
				"nine ingots did not build a layer");
		helper.succeed();
	}

	/**
	 * Any toxic source block in the core is fuel - a hand-placed one too. Upstream asks for
	 * {@code level == 0}, and the base mod's {@code toxic_block} now has a level, so flowing fluid
	 * does not count.
	 */
	@GameTest(template = EMPTY)
	public static void aToxicBlockPlacedByHandIsFuelToo(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		// The base mod made toxic_block a real liquid (merged 2026-10-07), so D-1082's hand-off is
		// done: a source counts, a flowing block does not, as upstream's level-0 check says.
		helper.assertTrue(ModBlocks.toxic_block.get().defaultBlockState().hasProperty(net.minecraft.world.level.block.LiquidBlock.LEVEL),
				"toxic_block is a liquid with a level");

		for(int i = 0; i < 5; i++) helper.setBlock(rig.cell(1 + i % 3, i / 3, 1 + i / 3), toxic());
		rig.slop.updateEntity();
		helper.assertTrue(rig.slop.fuel == 5, "five toxic blocks counted as " + rig.slop.fuel);

		BlockPos flowing = rig.cell(2, 2, 3);
		helper.setBlock(flowing, toxic().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3));
		helper.assertTrue(!rig.slop.isValidFuel(helper.absolutePos(flowing)), "flowing toxic fluid is fuel");

		helper.setBlock(rig.cell(1, 0, 2), Blocks.STONE);
		helper.assertTrue(!rig.slop.isValidFuel(helper.absolutePos(rig.cell(1, 0, 2))), "stone is fuel");
		helper.succeed();
	}

	// ---- the burn --------------------------------------------------------------------------

	/** 2,500 HE a tick, exactly, while it is on and has fuel - and nothing while it is off. */
	@GameTest(template = EMPTY)
	public static void aFuelledReactorMakesTwoAndAHalfThousandHEPerTick(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		rig.layer(1, toxic());

		slop.on = false;
		slop.updateEntity();
		long idle = slop.power;
		slop.updateEntity();
		helper.assertTrue(slop.power == idle, "an off reactor made power");

		slop.on = true;
		long before = slop.power;
		for(int i = 0; i < 7; i++) slop.updateEntity();
		helper.assertTrue(slop.power - before == 7 * 2500L, "seven ticks made " + (slop.power - before) + ", expected 17500");
		helper.assertTrue(SlopTE.generation == 2500L, "generation is " + SlopTE.generation);
		helper.assertTrue(SlopTE.maxPower == 1_500_000L, "maxPower is " + SlopTE.maxPower);
		helper.succeed();
	}

	/** The buffer holds thirty seconds of output and no more. */
	@GameTest(template = EMPTY)
	public static void thePowerBufferStopsAtThirtySecondsOfOutput(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		rig.layer(1, toxic());
		slop.on = true;

		// Thirty seconds of 2,500 a tick, as a literal, so that changing either constant is seen.
		slop.power = 1_499_000L;
		slop.updateEntity();
		helper.assertTrue(slop.power == 1_500_000L, "the buffer ended at " + slop.power);
		slop.updateEntity();
		helper.assertTrue(slop.power == 1_500_000L, "the buffer overflowed to " + slop.power);
		helper.succeed();
	}

	/** On with no fuel: no power, but - upstream's quirk - the clock to meltdown runs all the same. */
	@GameTest(template = EMPTY)
	public static void anEmptyReactorMakesNothingButStillOverheats(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		slop.on = true;

		long before = slop.power;
		int damage = slop.damage;
		for(int i = 0; i < 10; i++) slop.updateEntity();

		helper.assertTrue(slop.power == before, "an empty reactor made " + (slop.power - before));
		helper.assertTrue(slop.damage - damage == 10, "ten uncooled ticks did " + (slop.damage - damage) + " damage");
		helper.succeed();
	}

	/** A layer's life is {@code 3000 * (fuel mod 9, or 9) / 9} ticks: the layer count does not matter, only the partial one. */
	@GameTest(template = EMPTY)
	public static void aLayersLifetimeDependsOnTheLastPartialLayer(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		List<String> problems = new ArrayList<>();

		int[][] cases = { { 0, 0 }, { 1, 333 }, { 4, 1333 }, { 8, 2666 }, { 9, 3000 }, { 10, 333 }, { 18, 3000 }, { 27, 3000 } };
		for(int[] c : cases) {
			// Clear the room, then put exactly c[0] blocks into it, bottom layer first.
			for(int up = 1; up <= 3; up++) rig.layer(up, Blocks.AIR.defaultBlockState());
			for(int i = 0; i < c[0]; i++) helper.setBlock(rig.cell(1 + i % 3, (i / 3) % 3 - 1, 1 + i / 9), toxic());
			slop.updateEntity();
			if(slop.fuel != c[0]) problems.add(c[0] + " blocks counted as " + slop.fuel);
			if(slop.dynaMaxPartialConsumption != c[1]) problems.add(c[0] + " blocks last " + slop.dynaMaxPartialConsumption + ", expected " + c[1]);
		}

		helper.assertTrue(SlopTE.maxPartialConsumption == 3000, "a full layer lasts " + SlopTE.maxPartialConsumption);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The top layer burns first, and burning one resets the count. */
	@GameTest(template = EMPTY)
	public static void theTopLayerBurnsFirstAfterItsLifetime(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		rig.layer(1, toxic());
		rig.layer(2, toxic());
		slop.on = true;
		slop.updateEntity();
		helper.assertTrue(slop.fuel == 18 && slop.dynaMaxPartialConsumption == 3000, "two layers read " + slop.fuel + "/" + slop.dynaMaxPartialConsumption);

		// One tick short of the lifetime: nothing burns.
		slop.partialConsumption = 2998;
		slop.updateEntity();
		helper.assertTrue(slop.partialConsumption == 2999, "partial is " + slop.partialConsumption);
		helper.assertTrue(rig.count(2, ModBlocks.toxic_block.get()) == 9, "the layer burned a tick early");

		slop.updateEntity();
		helper.assertTrue(slop.partialConsumption == 3000 && rig.count(2, ModBlocks.toxic_block.get()) == 9,
				"the layer burned at exactly its lifetime; upstream burns on the tick after");

		slop.updateEntity();
		helper.assertTrue(slop.partialConsumption == 0, "burning did not reset the count: " + slop.partialConsumption);
		helper.assertTrue(rig.count(2, ModBlocks.toxic_block.get()) == 0, "the top layer did not burn");
		helper.assertTrue(rig.count(1, ModBlocks.toxic_block.get()) == 9, "the bottom layer burned instead");
		helper.succeed();
	}

	// ---- the cooling -----------------------------------------------------------------------

	/** A snowball is eaten, cools for exactly a hundred ticks, and resets the damage. */
	@GameTest(template = EMPTY)
	public static void aSnowballCoolsForExactlyAHundredTicks(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		rig.layer(1, toxic());
		slop.on = true;
		slop.damage = 400;
		slop.setItem(SlopTE.SLOT_COOLANT, new ItemStack(Items.SNOWBALL, 3));

		slop.updateEntity();
		helper.assertTrue(slop.getItem(SlopTE.SLOT_COOLANT).getCount() == 2, "the snowball was not eaten: " + slop.getItem(SlopTE.SLOT_COOLANT));
		helper.assertTrue(slop.cool == 100 && SlopTE.maxCool == 100, "cool is " + slop.cool);
		helper.assertTrue(slop.damage == 0, "a snowball did not clear the damage: " + slop.damage);

		for(int i = 2; i <= 100; i++) slop.updateEntity();
		helper.assertTrue(slop.damage == 0, "damage rose within the hundred ticks: " + slop.damage);
		helper.assertTrue(slop.getItem(SlopTE.SLOT_COOLANT).getCount() == 2, "a second snowball was eaten early");

		slop.updateEntity(); // tick 101: the cooling runs out and the clock starts
		helper.assertTrue(slop.cool == 0 && slop.damage == 1, "tick 101 read cool " + slop.cool + ", damage " + slop.damage);

		slop.updateEntity(); // tick 102: the next snowball
		helper.assertTrue(slop.getItem(SlopTE.SLOT_COOLANT).getCount() == 1 && slop.damage == 0,
				"the next snowball was not eaten, or did not clear the damage: " + slop.getItem(SlopTE.SLOT_COOLANT) + ", " + slop.damage);
		helper.succeed();
	}

	/** Only a snowball cools. */
	@GameTest(template = EMPTY)
	public static void onlyASnowballCools(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		slop.on = true;
		slop.setItem(SlopTE.SLOT_COOLANT, new ItemStack(Items.ICE, 16));

		for(int i = 0; i < 5; i++) slop.updateEntity();
		helper.assertTrue(slop.getItem(SlopTE.SLOT_COOLANT).getCount() == 16 && slop.cool == 0 && slop.damage == 5,
				"ice cooled: " + slop.getItem(SlopTE.SLOT_COOLANT) + ", cool " + slop.cool + ", damage " + slop.damage);
		helper.succeed();
	}

	/** An off reactor neither overheats nor spends snowballs, and a broken one switches itself off. */
	@GameTest(template = EMPTY)
	public static void anOffReactorIsLeftAloneAndABrokenOneSwitchesOff(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		slop.setItem(SlopTE.SLOT_COOLANT, new ItemStack(Items.SNOWBALL, 4));

		for(int i = 0; i < 20; i++) slop.updateEntity();
		helper.assertTrue(slop.damage == 0 && slop.cool == 0 && slop.getItem(SlopTE.SLOT_COOLANT).getCount() == 4, "an off reactor changed");
		helper.assertTrue(slop.formed, "the reactor never read as formed");

		slop.on = true;
		helper.setBlock(rig.centre.offset(2, 0, 2), Blocks.AIR); // a frame cell
		slop.updateEntity();
		helper.assertTrue(!slop.formed && !slop.on, "a broken reactor stayed on: formed " + slop.formed + ", on " + slop.on);
		helper.succeed();
	}

	// ---- the meltdown ----------------------------------------------------------------------

	/** The timer's edge: damage may reach 600 and the reactor stands; 601 and it is gone. */
	@GameTest(template = EMPTY, batch = "slopMeltdownTimer")
	public static void itMeltsOnTheTickDamageExceedsSixHundred(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		slop.on = true;

		helper.assertTrue(SlopTE.maxDamage == 600, "thirty seconds is 600 ticks, got " + SlopTE.maxDamage);

		slop.damage = SlopTE.maxDamage - 1;
		slop.updateEntity();
		helper.assertTrue(slop.damage == SlopTE.maxDamage, "damage is " + slop.damage);
		helper.assertTrue(helper.getBlockState(rig.pos).is(LeafiaBlocks.slop_reactor.get()), "it melted at exactly 600");

		slop.updateEntity();
		helper.assertTrue(helper.getBlockState(rig.pos).isAir(), "it did not melt at 601");
		helper.assertTrue(helper.getBlockState(rig.centre).isAir(), "the core survived");
		helper.assertTrue(helper.getBlockState(rig.centre.offset(2, 2, 2)).isAir(), "a corner of the shell survived");
		helper.succeed();
	}

	/**
	 * The cube is 17 wide - eight either side of the core's middle - and it takes everything:
	 * stone, bedrock, a living pig, a dropped item. One block further out is left alone, on every
	 * axis, and so is a pig standing outside it.
	 */
	@GameTest(template = BIG, batch = "slopMeltdownExtent", timeoutTicks = 100)
	public static void theMeltdownClearsExactlySeventeenBlocksSquare(GameTestHelper helper) {

		BlockPos centre = new BlockPos(8, 8, 8);
		Rig rig = build(helper, Direction.NORTH, centre);

		List<BlockPos> cleared = new ArrayList<>();
		List<BlockPos> kept = new ArrayList<>();
		for(int s : new int[] { -1, 1 }) {
			for(int t : new int[] { -1, 1 }) {
				for(int u : new int[] { -1, 1 }) cleared.add(centre.offset(s * 8, t * 8, u * 8));
				cleared.add(centre.offset(s * 8, t * 8, 0));
				kept.add(centre.offset(s * 9, t * 8, 0));
				kept.add(centre.offset(s * 8, t * 9, 0));
			}
			cleared.add(centre.offset(s * 8, 0, 0));
			cleared.add(centre.offset(0, s * 8, 0));
			cleared.add(centre.offset(0, 0, s * 8));
			kept.add(centre.offset(s * 9, 0, 0));
			kept.add(centre.offset(0, s * 9, 0));
			kept.add(centre.offset(0, 0, s * 9));
		}

		for(BlockPos p : cleared) helper.setBlock(p, Blocks.OBSIDIAN);
		for(BlockPos p : kept) helper.setBlock(p, Blocks.OBSIDIAN);
		helper.setBlock(centre.offset(8, 4, 5), Blocks.BEDROCK);

		Pig inside = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(centre.getX() + 7, centre.getY() + 1, centre.getZ() + 1));
		ItemEntity item = helper.spawnItem(Items.COAL, centre.getX() + 0.5F, centre.getY() + 6F, centre.getZ() + 0.5F);
		Pig outside = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(centre.getX() + 11, centre.getY() + 1, centre.getZ() + 1));

		rig.slop.on = true;
		rig.slop.damage = SlopTE.maxDamage;
		rig.slop.updateEntity();

		List<String> problems = new ArrayList<>();
		for(BlockPos p : cleared) if(!helper.getBlockState(p).isAir()) problems.add("survived: " + p.subtract(centre));
		for(BlockPos p : kept) if(!helper.getBlockState(p).is(Blocks.OBSIDIAN)) problems.add("lost: " + p.subtract(centre));
		if(!helper.getBlockState(centre.offset(8, 4, 5)).isAir()) problems.add("the bedrock survived");

		helper.assertTrue(problems.isEmpty(), problems.size() + " block(s) wrong: " + problems);
		helper.assertTrue(inside.getHealth() <= 0F, "the pig inside has " + inside.getHealth() + " health");
		helper.assertTrue(item.isRemoved(), "the item inside was not removed");
		helper.assertTrue(outside.getHealth() > 0F && !outside.isRemoved(), "the pig outside was hurt");
		helper.succeed();
	}

	/** A part of a multipart mob kills the mob; a non-living entity is removed; the second pass finds nothing left. */
	@GameTest(template = EMPTY)
	public static void tryKillReachesThroughPartsAndRemovesWhatIsNotAlive(GameTestHelper helper) {

		EnderDragon dragon = new EnderDragon(EntityType.ENDER_DRAGON, helper.getLevel());
		SlopTE.tryKill(dragon.head);
		helper.assertTrue(dragon.getHealth() <= 0F, "killing the head left the dragon on " + dragon.getHealth());

		Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
		ItemEntity coal = helper.spawnItem(Items.COAL, 2.5F, 1F, 2.5F);
		SlopTE.tryKill(pig);
		SlopTE.tryKill(coal);
		helper.assertTrue(pig.getHealth() == 0F && pig.isDeadOrDying(), "the pig has " + pig.getHealth());
		helper.assertTrue(coal.isRemoved(), "the coal was not removed");

		SlopTE.Delayed.run(); // upstream's DelayedTick; nothing left alive for it to find
		helper.assertTrue(coal.isRemoved(), "the second pass revived something");
		helper.succeed();
	}

	/** An entity that survives setHealth(0) is removed by the end-of-tick pass. */
	@GameTest(template = EMPTY)
	public static void theSecondPassRemovesWhateverSurvived(GameTestHelper helper) {

		Entity survivor = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
		// Not touched by the first pass at all, so it is alive when the second one looks - which is
		// what a mob that ignores setHealth(0) would be.
		SlopTE.Delayed.queue(survivor);
		SlopTE.Delayed.run();
		helper.assertTrue(survivor.isRemoved(), "a living survivor was not removed at the end of the tick");
		helper.succeed();
	}

	// ---- energy and automation -------------------------------------------------------------

	/**
	 * Output goes to a receiver in front of the controller, and nothing is made or lost on the way.
	 * Two tests rather than one with two batteries: the front is offered first and a battery takes
	 * 5,000 a tick, so it would take every 2,500 and the one below would see none - which is
	 * upstream's order, and not what a test of "below works" wants.
	 */
	@GameTest(template = EMPTY)
	public static void itProvidesToABatteryInFront(GameTestHelper helper) {
		provideTo(helper, new BlockPos(2, 0, -1));
	}

	/** And to one beneath it, which the same call reaches with the direction DOWN. */
	@GameTest(template = EMPTY)
	public static void itProvidesToABatteryBelow(GameTestHelper helper) {
		provideTo(helper, new BlockPos(2, -1, 0));
	}

	private static void provideTo(GameTestHelper helper, BlockPos where) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		rig.layer(1, toxic());
		slop.on = true;

		helper.setBlock(where, ModBlocks.machine_battery.get());
		BlockEntityMachineBattery battery = (BlockEntityMachineBattery) helper.getBlockEntity(where);
		long start = slop.power + battery.power;

		for(int i = 0; i < 5; i++) slop.updateEntity();

		helper.assertTrue(battery.power > 0, "the battery at " + where + " received nothing; the reactor holds " + slop.power);
		helper.assertTrue(slop.power + battery.power - start == 5 * 2500L,
				"five ticks should add 12500 to the total, added " + (slop.power + battery.power - start));
		helper.succeed();
	}

	/** Slot 0 is the bottom's, slot 1 every other side's, anything goes in and nothing comes out. */
	@GameTest(template = EMPTY)
	public static void theSlotsAreReachedFromUpstreamsSides(GameTestHelper helper) {

		SlopTE slop = build(helper, Direction.NORTH).slop;
		List<String> problems = new ArrayList<>();

		for(Direction side : Direction.values()) {
			int[] slots = slop.getSlotsForFace(side);
			int[] want = side == Direction.DOWN ? new int[] { 0 } : new int[] { 1 };
			if(!java.util.Arrays.equals(slots, want)) problems.add(side + " reaches " + java.util.Arrays.toString(slots));
			for(int slot = 0; slot < 2; slot++) {
				if(!slop.canPlaceItemThroughFace(slot, new ItemStack(Items.DIRT), side)) problems.add(side + " refused dirt in " + slot);
				if(slop.canTakeItemThroughFace(slot, new ItemStack(Items.DIRT), side)) problems.add(side + " let dirt out of " + slot);
			}
		}

		helper.assertTrue(slop.getContainerSize() == 2, "two slots, got " + slop.getContainerSize());
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Five numbers and the inventory survive a save, and the structure and fuel are re-derived, not saved. */
	@GameTest(template = EMPTY)
	public static void itSavesFiveNumbersAndItsInventory(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		SlopTE slop = rig.slop;
		slop.damage = 123;
		slop.partialConsumption = 456;
		slop.power = 789_012L;
		slop.on = true;
		slop.cool = 34;
		slop.fuel = 18;
		slop.formed = true;
		slop.setItem(SlopTE.SLOT_FUEL, uranium(17));
		slop.setItem(SlopTE.SLOT_COOLANT, new ItemStack(Items.SNOWBALL, 5));

		CompoundTag tag = slop.saveWithoutMetadata(helper.getLevel().registryAccess());
		SlopTE other = new SlopTE(helper.absolutePos(rig.pos), slop.getBlockState());
		other.loadWithComponents(tag, helper.getLevel().registryAccess());

		helper.assertTrue(other.damage == 123 && other.partialConsumption == 456 && other.power == 789_012L
				&& other.on && other.cool == 34, "the five numbers did not survive");
		helper.assertTrue(other.getItem(0).getCount() == 17 && other.getItem(0).is(ModItems.ingot_uranium.get()), "slot 0 did not survive");
		helper.assertTrue(other.getItem(1).getCount() == 5 && other.getItem(1).is(Items.SNOWBALL), "slot 1 did not survive");
		helper.assertTrue(other.fuel == 0 && !other.formed, "fuel or the structure was saved");
		helper.succeed();
	}

	// ---- the screen's link to it -----------------------------------------------------------

	/** The switch is the one thing the client can send, and the six numbers are the ones it can receive. */
	@GameTest(template = EMPTY)
	public static void thePacketsAreUpstreams(GameTestHelper helper) {

		SlopTE slop = build(helper, Direction.NORTH).slop;

		helper.assertTrue("SLOP_NUKE".equals(slop.getPacketIdentifier()), "the identifier is " + slop.getPacketIdentifier());
		helper.assertTrue(slop.affectionRange() == 0, "affection range is " + slop.affectionRange());

		// D-1094: the switch is heard from a player with this reactor's screen open and in reach
		ServerPlayer player = mockPlayer(helper);
		Vec3 at = Vec3.atCenterOf(slop.getBlockPos());
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = new SlopMenu(1, player.getInventory(), slop);
		slop.listeners.clear();

		slop.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(slop.on, "the toggle did not switch it on");
		slop.onReceivePacketServer((byte) 0, false, player);
		helper.assertTrue(!slop.on, "the toggle did not switch it off");
		slop.onReceivePacketServer((byte) 1, true, player);
		helper.assertTrue(!slop.on, "a key other than 0 switched it");

		// and nothing else is heard (D-1094): not a non-boolean, not a player far away, not one with
		// another screen open, not nobody
		slop.onReceivePacketServer((byte) 0, 1, player);
		slop.onReceivePacketServer((byte) 0, "true", player);
		slop.onReceivePacketServer((byte) 0, null, player);
		slop.onReceivePacketServer((byte) 0, true, null);
		helper.assertTrue(!slop.on, "a non-boolean or a missing sender switched it on");
		player.setPos(at.x + 100, at.y, at.z);
		slop.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(!slop.on, "a player a hundred blocks away switched it on");
		player.setPos(at.x, at.y, at.z);
		player.containerMenu = player.inventoryMenu;
		slop.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(!slop.on, "a player with no reactor screen open switched it on");
		player.containerMenu = new SlopMenu(2, player.getInventory(), (SlopTE) null);
		slop.onReceivePacketServer((byte) 0, true, player);
		helper.assertTrue(!slop.on, "a menu that found no reactor switched this one on");

		slop.onReceivePacketLocal((byte) 0, 5);
		slop.onReceivePacketLocal((byte) 1, 6);
		slop.onReceivePacketLocal((byte) 2, 7);
		slop.onReceivePacketLocal((byte) 3, 8);
		slop.onReceivePacketLocal((byte) 4, true);
		slop.onReceivePacketLocal((byte) 5, 9L);
		helper.assertTrue(slop.fuel == 5 && slop.damage == 6 && slop.partialConsumption == 7
				&& slop.dynaMaxPartialConsumption == 8 && slop.on && slop.power == 9L, "a client-side slot landed in the wrong field");
		helper.succeed();
	}

	/** Two slots at upstream's coordinates, then the usual inventory; the client finds the reactor by its position. */
	@GameTest(template = EMPTY)
	public static void theMenuLaysOutUpstreamsSlots(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		try {
			ServerPlayer player = mockPlayer(helper);
			SlopMenu menu = new SlopMenu(1, player.getInventory(), rig.slop);

			helper.assertTrue(menu.getType() == LeafiaMenuTypes.SLOP.get(), "the menu has the wrong type");
			helper.assertTrue(menu.slots.size() == 2 + 36, "38 slots, got " + menu.slots.size());
			helper.assertTrue(menu.slots.get(0).x == 8 && menu.slots.get(0).y == 17, "slot 0 is at " + menu.slots.get(0).x + "," + menu.slots.get(0).y);
			helper.assertTrue(menu.slots.get(1).x == 80 && menu.slots.get(1).y == 53, "slot 1 is at " + menu.slots.get(1).x + "," + menu.slots.get(1).y);
			helper.assertTrue(menu.slots.get(2).x == 8 && menu.slots.get(2).y == 84, "the inventory starts at " + menu.slots.get(2).x + "," + menu.slots.get(2).y);
			helper.assertTrue(menu.slots.get(29).x == 8 && menu.slots.get(29).y == 142, "the hotbar starts at " + menu.slots.get(29).x + "," + menu.slots.get(29).y);

			helper.assertTrue(rig.slop.listeners.contains(player), "the server's menu did not register its player");
			menu.removed(player);
			helper.assertTrue(rig.slop.listeners.isEmpty(), "removing the menu left a listener");

			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			buf.writeBlockPos(helper.absolutePos(rig.pos));
			SlopMenu found = new SlopMenu(2, player.getInventory(), buf);
			helper.assertTrue(found.slop == rig.slop, "the position did not find the reactor");
			found.removed(player);

			RegistryFriendlyByteBuf wrong = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			wrong.writeBlockPos(helper.absolutePos(new BlockPos(0, 4, 0)));
			SlopMenu none = new SlopMenu(3, player.getInventory(), wrong);
			helper.assertTrue(none.slop == null && none.slots.size() == 38, "an orphaned menu is not the empty one");
			helper.succeed();
		} finally {
			rig.slop.listeners.clear();
		}
	}

	/** A menu is only valid for a built reactor, and a player in reach. */
	@GameTest(template = EMPTY)
	public static void theMenuIsValidOnlyForABuiltReactorInReach(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		try {
			ServerPlayer player = mockPlayer(helper);
			SlopMenu menu = new SlopMenu(1, player.getInventory(), rig.slop);
			rig.slop.listeners.clear(); // the mock refuses the reactor's packets, and this test ticks it
			Vec3 at = Vec3.atCenterOf(helper.absolutePos(rig.pos));
			player.setPos(at.x, at.y, at.z);

			helper.assertTrue(!menu.stillValid(player), "a reactor that has not ticked is valid");
			rig.slop.updateEntity();
			helper.assertTrue(menu.stillValid(player), "a built reactor in reach is not valid");

			player.setPos(at.x + 100, at.y, at.z);
			helper.assertTrue(!menu.stillValid(player), "a player a hundred blocks off is in reach");

			player.setPos(at.x, at.y, at.z);
			helper.setBlock(rig.centre.offset(2, 2, 2), Blocks.AIR);
			rig.slop.updateEntity();
			helper.assertTrue(!menu.stillValid(player), "a broken reactor is still valid");
			menu.removed(player);
			helper.succeed();
		} finally {
			rig.slop.listeners.clear();
		}
	}

	/** Shift-click moves a stack into the machine's first slot that takes it, and out again. */
	@GameTest(template = EMPTY)
	public static void shiftClickMovesBothWays(GameTestHelper helper) {

		Rig rig = build(helper, Direction.NORTH);
		try {
			ServerPlayer player = mockPlayer(helper);
			SlopMenu menu = new SlopMenu(1, player.getInventory(), rig.slop);

			menu.slots.get(2 + 27).set(uranium(20)); // first hotbar slot
			menu.quickMoveStack(player, 2 + 27);
			helper.assertTrue(rig.slop.getItem(0).getCount() == 20, "the ingots did not go into slot 0: " + rig.slop.getItem(0));

			menu.quickMoveStack(player, 0);
			helper.assertTrue(rig.slop.getItem(0).isEmpty(), "the ingots stayed in the machine");
			int held = 0;
			for(ItemStack s : player.getInventory().items) if(s.is(ModItems.ingot_uranium.get())) held += s.getCount();
			helper.assertTrue(held == 20, "the player holds " + held + " ingots after the round trip");
			menu.removed(player);
			helper.succeed();
		} finally {
			rig.slop.listeners.clear();
		}
	}

	// ---- the assets ------------------------------------------------------------------------

	private static String text(String path) {
		try(InputStream in = LeafiaSlopTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static JsonObject json(String path) {
		return JsonParser.parseString(text(path)).getAsJsonObject();
	}

	private static BufferedImage png(String path) {
		try(InputStream in = LeafiaSlopTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException("missing resource " + path);
			return ImageIO.read(in);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Upstream's blockstate turns one model by 0, 90, 180 and 270 for north, east, south and west
	 * - clockwise from above in both versions - and the model is {@code orientable} with the red
	 * button as the front.
	 */
	@GameTest(template = EMPTY)
	public static void theControllerIsOneModelTurnedByFacing(GameTestHelper helper) {

		JsonObject variants = json("blockstates/slop_reactor.json").getAsJsonObject("variants");
		int[][] turns = { { 0 }, { 90 }, { 180 }, { 270 } };
		String[] facings = { "north", "east", "south", "west" };
		for(int i = 0; i < 4; i++) {
			JsonObject v = variants.getAsJsonObject("facing=" + facings[i]);
			helper.assertTrue(v != null, "no variant for " + facings[i]);
			helper.assertTrue("leafia:block/slop_reactor".equals(v.get("model").getAsString()), facings[i] + " uses " + v.get("model"));
			int y = v.has("y") ? v.get("y").getAsInt() : 0;
			helper.assertTrue(y == turns[i][0], facings[i] + " turns " + y + ", expected " + turns[i][0]);
		}

		JsonObject model = json("models/block/slop_reactor.json");
		helper.assertTrue("minecraft:block/orientable".equals(model.get("parent").getAsString()), "parent is " + model.get("parent"));
		JsonObject tex = model.getAsJsonObject("textures");
		helper.assertTrue("leafia:block/slop_reactor".equals(tex.get("front").getAsString()), "the front is " + tex.get("front"));
		helper.assertTrue("leafia:block/slop_reactor_casing".equals(tex.get("side").getAsString())
				&& "leafia:block/slop_reactor_casing".equals(tex.get("top").getAsString()), "the sides and top are casing");
		helper.succeed();
	}

	/** The glass is the only cutout; the casing and the glass are plain cubes of their own texture. */
	@GameTest(template = EMPTY)
	public static void theGlassIsCutoutAndTheCasingIsACube(GameTestHelper helper) {

		JsonObject glass = json("models/block/slop_reactor_glass.json");
		helper.assertTrue(glass.has("render_type") && "minecraft:cutout".equals(glass.get("render_type").getAsString()),
				"the glass is not cutout, so its transparent pixels draw black");
		JsonObject casing = json("models/block/slop_reactor_casing.json");
		helper.assertTrue(!casing.has("render_type"), "the casing is not opaque");

		for(String id : List.of("slop_reactor_casing", "slop_reactor_glass")) {
			JsonObject m = json("models/block/" + id + ".json");
			helper.assertTrue("minecraft:block/cube_all".equals(m.get("parent").getAsString()), id + " is not a cube_all");
			helper.assertTrue(("leafia:block/" + id).equals(m.getAsJsonObject("textures").get("all").getAsString()), id + " wears the wrong texture");
			helper.assertTrue(("leafia:block/" + id).equals(json("models/item/" + id + ".json").get("parent").getAsString()), id + "'s item is not its block");
		}
		helper.assertTrue("leafia:block/slop_reactor".equals(json("models/item/slop_reactor.json").get("parent").getAsString()),
				"the controller's item is not its block");

		for(String t : List.of("slop_reactor", "slop_reactor_casing", "slop_reactor_glass")) {
			BufferedImage img = png("textures/block/" + t + ".png");
			helper.assertTrue(img.getWidth() == 16 && img.getHeight() == 16, t + " is " + img.getWidth() + "x" + img.getHeight());
		}
		helper.succeed();
	}

	/** The screen draws from fixed pixel offsets in a 256-square sheet, so the sheet must be one. */
	@GameTest(template = EMPTY)
	public static void theScreenSheetIsTwoFiftySixSquare(GameTestHelper helper) {

		BufferedImage gui = png("textures/gui/reactors/slop.png");
		helper.assertTrue(gui.getWidth() == 256 && gui.getHeight() == 256, "slop.png is " + gui.getWidth() + "x" + gui.getHeight());
		// The power bar's fill, at (176,0) 16 wide, is opaque at its bottom edge, where a full bar lands.
		helper.assertTrue((gui.getRGB(180, 51) >>> 24) == 255, "the power bar's fill is missing at (180, 51)");
		// The switch's lit overlay at (208, 0), 18 square.
		helper.assertTrue((gui.getRGB(216, 8) >>> 24) == 255, "the switch's overlay is missing at (216, 8)");
		helper.succeed();
	}

	/** Upstream's English, verbatim, because the tooltip splits on its dollar signs. */
	@GameTest(template = EMPTY)
	public static void theNamesAndTheDescriptionAreUpstreams(GameTestHelper helper) {

		JsonObject lang = json("lang/en_us.json");
		String name = "Average \"Nuclear Reactor\" in Modern Mods";
		helper.assertTrue(name.equals(lang.get("block.leafia.slop_reactor").getAsString()), "name: " + lang.get("block.leafia.slop_reactor"));
		helper.assertTrue(name.equals(lang.get("tile.slop_reactor.name").getAsString()), "the menu title is not the block's name");
		helper.assertTrue((name + " Casing").equals(lang.get("block.leafia.slop_reactor_casing").getAsString()), "casing name");
		helper.assertTrue((name + " Glass").equals(lang.get("block.leafia.slop_reactor_glass").getAsString()), "glass name");
		helper.assertTrue(lang.get("tile.slop_reactor.desc").getAsString().startsWith("Create a 5x5x5 cube made of reactor glass$")
				&& lang.get("tile.slop_reactor.desc").getAsString().endsWith("$$Made in 4 hours"), "description: " + lang.get("tile.slop_reactor.desc"));
		helper.assertTrue("Radiation Shielding".equals(lang.get("trait.radshield").getAsString()), "the glass's tooltip line");
		helper.succeed();
	}
}
