package com.leafia.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.blocks.IRadResistantBlock;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockSellafieldSlaked;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.PWRData.PWRExplosion;
import com.leafia.contents.machines.reactors.pwr.PWRDiagnosis;
import com.leafia.contents.machines.reactors.pwr.PWRMeltdown;
import com.leafia.contents.machines.reactors.pwr.PWRMeltdown.Plan;
import com.leafia.contents.machines.reactors.pwr.PWRMeltdown.Result;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalTE;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Erosion;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreck.Variation;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRMeshedWreckEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRWreckMetal;
import com.leafia.contents.machines.reactors.pwr.blocks.wreckage.PWRWreckStone;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu.RemoteSlot;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisCrafting;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisEntity.DebrisType;
import com.leafia.contents.machines.reactors.pwr.debris.PWRDebrisItem;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon PWR's terminal, its meltdown wreck and its debris (PWR-2, D-3010), read against numbers worked by
 * hand from upstream's code.
 *
 * <p>No test here runs a real meltdown: a real level-3 meltdown blasts a crater of radius 25 or more and
 * wastes a radius-35 sphere. The meltdown is run through {@link PWRMeltdown#run} with a blast that records its
 * arguments and does nothing, and with its rays read through a bounded view - "nothing out there is open" or
 * "everything out there is open" - so the samples never reach a neighbouring test. Every test that builds
 * removes what it built, debris included, before it succeeds. Randomness is pinned with a seeded
 * {@link RandomSource} and a twin of the same seed that predicts it, or the assertion is on a whole support set.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaPWR2Tests {

	private static final String BIG = "empty16";
	private static final String EMPTY = "empty";
	private static final double EPS = 1e-9;

	// rig A of LeafiaPWRTests, with a terminal in the hull behind the exchanger
	private static final BlockPos E1 = new BlockPos(5, 4, 5);
	private static final BlockPos CH = new BlockPos(6, 4, 5);
	private static final BlockPos E2 = new BlockPos(7, 4, 5);
	private static final BlockPos B = new BlockPos(6, 4, 4);
	private static final BlockPos X = new BlockPos(6, 4, 6);
	private static final BlockPos P = new BlockPos(6, 5, 5);
	private static final BlockPos T = new BlockPos(6, 4, 7);

	/** The meltdown scenes' centre, in template coordinates. */
	private static final BlockPos C = new BlockPos(8, 8, 8);

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

	private static void near(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) <= EPS * Math.max(1, Math.abs(expected)), what + ": " + actual + " vs " + expected);
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static void fill(GameTestHelper helper, BlockPos from, BlockPos to, Block block) {
		for(int x = from.getX(); x <= to.getX(); x++)
			for(int y = from.getY(); y <= to.getY(); y++)
				for(int z = from.getZ(); z <= to.getZ(); z++)
					helper.setBlock(new BlockPos(x, y, z), block);
	}

	private static PWRData rigWithTerminal(GameTestHelper helper) {
		fill(helper, new BlockPos(3, 2, 2), new BlockPos(9, 7, 8), LeafiaBlocks.lwr_casing.get());
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_channel.get());
		helper.setBlock(E2, LeafiaBlocks.lwr_element.get());
		helper.setBlock(B, LeafiaBlocks.reactor_conductor.get());
		helper.setBlock(X, LeafiaBlocks.lwr_exchanger.get());
		helper.setBlock(P, LeafiaBlocks.lwr_port.get());
		helper.setBlock(T, LeafiaBlocks.lwr_terminal.get().defaultBlockState().setValue(PWRTerminalBlock.FACING, Direction.NORTH));
		PWRDiagnosis.preventScan.clear();
		BlockPos abs = helper.absolutePos(E1);
		((PWRComponentBlock) helper.getBlockState(E1).getBlock()).beginDiagnosis(helper.getLevel(), abs, abs);
		PWRData found = null;
		for(BlockPos pos : new BlockPos[] {E1, E2, P})
			if(be(helper, pos) instanceof PWRComponentEntity entity && entity.getCore() != null) found = entity.getCore();
		helper.assertTrue(found != null, "the rig forms a reactor");
		return found;
	}

	private static void teardownRig(GameTestHelper helper) {
		for(BlockPos pos : new BlockPos[] {E1, E2})
			if(be(helper, pos) instanceof PWRElementTE element) element.inventory.setStackInSlot(0, ItemStack.EMPTY);
		fill(helper, new BlockPos(3, 2, 2), new BlockPos(9, 7, 8), Blocks.AIR);
		PWRDiagnosis.preventScan.clear();
	}

	private static PWRTerminalTE terminal(GameTestHelper helper) {
		return (PWRTerminalTE) be(helper, T);
	}

	// ---------------------------------------------------------------------------- the blocks

	@GameTest(template = EMPTY)
	public static void theTerminalsAndWrecksAreUpstreamsStrengthAndShielding(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		Object[][] rows = {
				{LeafiaBlocks.lwr_terminal.get(), 275F, 165F},
				{LeafiaBlocks.reactor_hatch.get(), 5F, 96F},
				{LeafiaBlocks.reactor_hatch_alt.get(), 5F, 96F},
				{LeafiaBlocks.lwrwreck_stone.get(), 5F, 5F},
				{LeafiaBlocks.lwrwreck_metal.get(), 5F, 5F}};
		for(Object[] row : rows) {
			Block block = (Block) row[0];
			BlockState state = block.defaultBlockState();
			helper.assertTrue(state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == (float) row[1], block + " hardness " + state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO));
			helper.assertTrue(block.getExplosionResistance() == (float) row[2], block + " resistance " + block.getExplosionResistance());
			helper.assertTrue(state.requiresCorrectToolForDrops(), block + " needs a pickaxe (Material.IRON)");
		}
		for(Block terminal : new Block[] {LeafiaBlocks.lwr_terminal.get(), LeafiaBlocks.reactor_hatch.get(), LeafiaBlocks.reactor_hatch_alt.get()}) {
			helper.assertTrue(terminal instanceof IRadResistantBlock shield && shield.getResistance() == 1, terminal + " shields");
			helper.assertTrue(LeafiaBlockEntities.PWR_TERMINAL.get().isValid(terminal.defaultBlockState()), terminal + " takes the terminal's block entity");
			helper.assertTrue(terminal.getDescriptionId().equals("block.leafia." + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(terminal).getPath()), "id");
		}
		helper.assertTrue(LeafiaBlocks.lwrwreck_stone.get().defaultBlockState().getRenderShape() == RenderShape.INVISIBLE, "a wreck is drawn by its renderer");
		helper.assertTrue(LeafiaBlocks.lwrwreck_stone.get().defaultBlockState().getSoundType() == net.minecraft.world.level.block.SoundType.STONE
				&& LeafiaBlocks.lwrwreck_metal.get().defaultBlockState().getSoundType() == net.minecraft.world.level.block.SoundType.METAL, "wreck sounds");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theTerminalLinksToTheCoreAndIsAFluidPort(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		PWRData core = rigWithTerminal(helper);
		PWRTerminalTE terminal = terminal(helper);
		helper.assertTrue(terminal.getCoreByCorePos() == core, "the terminal links to the core");
		helper.assertTrue(terminal.getCore() == null && !terminal.canAssignCore(), "and never holds it");
		helper.assertTrue(terminal.getLinkedCoreDiagnosis() == core, "a fresh link needs no diagnosis");
		helper.assertTrue(terminal.getReceivingTanks().length == 2 && terminal.getReceivingTanks()[0] == core.tanks[0]
				&& terminal.getReceivingTanks()[1] == core.tanks[3], "coolant and water in");
		helper.assertTrue(terminal.getSendingTanks().length == 2 && terminal.getSendingTanks()[0] == core.tanks[1]
				&& terminal.getSendingTanks()[1] == core.tanks[4], "hot coolant and steam out");
		helper.assertTrue(terminal.getAllTanks() == core.tanks, "all eight");
		teardownRig(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theTerminalMenuPutsASlotOnEveryFuelColumnOfTheGrid(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		PWRData core = rigWithTerminal(helper);
		PWRTerminalTE terminal = terminal(helper);
		ServerPlayer player = mockPlayer(helper);
		PWRTerminalMenu menu = new PWRTerminalMenu(1, player.getInventory(), terminal, core);
		helper.assertTrue(menu.slots.size() == 36 + 3 + 2, "inventory, three resources, two columns: " + menu.slots.size());
		Set<BlockPos> locals = new HashSet<>();
		for(Slot slot : menu.slots) if(slot instanceof RemoteSlot remote) locals.add(remote.localPos);
		// facing north, the terminal looks south into the reactor: (x, y, z) -> (-x, y, -z) relative to it
		helper.assertTrue(locals.equals(Set.of(new BlockPos(1, 0, 2), new BlockPos(-1, 0, 2))), "the grid as the front sees it: " + locals);

		// the opening payload names the same columns, in the same order
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		PWRTerminalMenu.writeOpening(buf, terminal, core);
		PWRTerminalMenu client = new PWRTerminalMenu(2, player.getInventory(), buf);
		helper.assertTrue(client.terminalPos.equals(terminal.getBlockPos()) && client.fuels.equals(menu.fuels)
				&& client.slots.size() == menu.slots.size(), "the client builds the same slots");

		// shift-click: inventory -> the columns (never the resources), and back to the inventory from its far end
		LeafiaRodItem rod = LeafiaRods.get("leafRodU38");
		player.getInventory().setItem(9, new ItemStack(rod));
		menu.quickMoveStack(player, 0);
		helper.assertTrue(menu.slots.get(PWRTerminalMenu.FIRST_REMOTE).getItem().is(rod), "the rod went into the first column");
		helper.assertTrue(core.resourceContainer.getStackInSlot(0).isEmpty(), "not the identifier slot");
		menu.quickMoveStack(player, PWRTerminalMenu.FIRST_REMOTE);
		helper.assertTrue(player.getInventory().getItem(8).is(rod), "and back, to the last hotbar slot first");
		helper.assertTrue(menu.stillValid(player) || !player.canInteractWithBlock(terminal.getBlockPos(), 4.0), "valid within reach");
		player.getInventory().clearContent();
		teardownRig(helper);
		helper.assertTrue(!menu.stillValid(player), "not once the core is gone");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theCoreTakesTheScreensRequestsOnlyThroughAnOpenTerminal(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		PWRData core = rigWithTerminal(helper);
		PWRTerminalTE terminal = terminal(helper);
		ServerPlayer player = mockPlayer(helper);
		CompoundTag half = new CompoundTag();
		half.putDouble("level", 0.5);
		double before = core.masterControl;

		terminal.onReceivePacketServer((byte) 30, half, player);
		core.onReceivePacketServer((byte) 30, half, player);
		helper.assertTrue(core.masterControl == before, "no screen open: refused");

		player.containerMenu = new PWRTerminalMenu(3, player.getInventory(), terminal, core);
		try {
			terminal.onReceivePacketServer((byte) 30, half, player);
			helper.assertTrue(core.masterControl == 0.5, "through the open terminal: the master is 0.5, " + core.masterControl);
			CompoundTag over = new CompoundTag();
			over.putDouble("level", 1.5);
			terminal.onReceivePacketServer((byte) 30, over, player);
			CompoundTag nan = new CompoundTag();
			nan.putDouble("level", Double.NaN);
			terminal.onReceivePacketServer((byte) 30, nan, player);
			helper.assertTrue(core.masterControl == 0.5, "a level the slider cannot send is refused");
			CompoundTag group = new CompoundTag();
			group.putString("name", "nobody");
			group.putDouble("level", 0.25);
			terminal.onReceivePacketServer((byte) 30, group, player);
			helper.assertTrue(!core.controlDemand.containsKey("nobody"), "a group no rod is in is refused");
			terminal.onReceivePacketServer((byte) 29, 4, player);
			helper.assertTrue(core.compression == 1 && core.tanks[4].getTankType() == Fluids.HOTSTEAM, "compression mod 3: 4 is 1, hot steam");
			terminal.onReceivePacketServer((byte) 29, -1, player);
			helper.assertTrue(core.compression == 2 && core.tanks[4].getTankType() == Fluids.SUPERHOTSTEAM, "and -1 is 2");
		} finally {
			player.containerMenu = player.inventoryMenu;
		}
		teardownRig(helper);
		helper.succeed();
	}

	// ----------------------------------------------------------------------------- the wreck

	@GameTest(template = EMPTY)
	public static void aWreckCollidesWithItsVariationTurnedByItsFacing(GameTestHelper helper) {
		Variation wreck = PWRWreckMetal.variations.NORMAL[0]; // blend(-5, -5, 5, 5, 0, 8): the whole floor, 0.8 high
		Object[][] rows = {
				{Direction.UP, new AABB(0, 0, 0, 1, 0.8, 1)},
				{Direction.DOWN, new AABB(0, 0.2, 0, 1, 1, 1)},
				{Direction.NORTH, new AABB(0, 0, 0.2, 1, 1, 1)},
				{Direction.EAST, new AABB(0, 0, 0, 0.8, 1, 1)}};
		for(Object[] row : rows) {
			List<AABB> boxes = PWRMeshedWreck.collisionBoxes(wreck, (Direction) row[0]);
			AABB want = (AABB) row[1];
			helper.assertTrue(boxes.size() == 1, "one box");
			AABB got = boxes.get(0);
			for(double[] pair : new double[][] {{got.minX, want.minX}, {got.minY, want.minY}, {got.minZ, want.minZ},
					{got.maxX, want.maxX}, {got.maxY, want.maxY}, {got.maxZ, want.maxZ}})
				helper.assertTrue(Math.abs(pair[0] - pair[1]) < 1e-9, row[0] + ": " + got + " vs " + want);
		}
		for(Direction face : Direction.values()) {
			AABB intact = PWRMeshedWreck.collisionBoxes(PWRWreckStone.variations.NONE[0], face).get(0);
			helper.assertTrue(Math.abs(intact.getXsize() - 1) < 1e-9 && Math.abs(intact.getYsize() - 1) < 1e-9 && Math.abs(intact.getZsize() - 1) < 1e-9,
					"the intact mesh is the whole block, " + face);
		}
		helper.assertTrue(PWRWreckStone.variations.RUBBLE[0].hitboxes.length == 6 && PWRWreckStone.variations.RUBBLE[3].hitboxes.length == 3,
				"stone_rubble_0_flip has six boxes, stone_rubble_3 three");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theWrecksHaveUpstreamsVariationsAndTheirMeshesAreBaked(GameTestHelper helper) {
		int[] metal = {1, 2, 1, 1};
		int[] stone = {1, 3, 2, 4};
		for(Erosion e : Erosion.values()) {
			helper.assertTrue(PWRWreckMetal.variations.list(e).length == metal[e.ordinal()], "metal " + e);
			helper.assertTrue(PWRWreckStone.variations.list(e).length == stone[e.ordinal()], "stone " + e);
		}
		Variation v = PWRWreckStone.variations.RUBBLE[3];
		helper.assertTrue(v.bits == (3 << 5) + (3 << 3) && PWRWreckStone.variations.fromBits(v.bits) == v, "variation bits: " + v.bits);
		// every mesh a variation names is a baked model, object by object
		for(PWRMeshedWreck.VariationGroup group : new PWRMeshedWreck.VariationGroup[] {PWRWreckMetal.variations, PWRWreckStone.variations})
			for(Erosion e : Erosion.values())
				for(Variation var : group.list(e))
					helper.assertTrue(PWRMeshedWreck.MESH_OBJECTS.containsKey(var.meshName), var.meshName + " is in the renderer's table");
		List<String> models = new ArrayList<>();
		PWRMeshedWreck.MESH_OBJECTS.forEach((mesh, objects) -> {
			for(String o : objects) models.add("pwrwreck_" + mesh + "_" + o.toLowerCase().replace('.', '_'));
		});
		for(String[] pair : PWRDebrisEntity.MESH_OBJECTS)
			models.add("pwrdebris_" + pair[0] + "_" + pair[1].toLowerCase().replace('.', '_'));
		models.add("pwrdebris_graphite");
		for(String model : models) {
			try(InputStream in = LeafiaPWR2Tests.class.getResourceAsStream("/assets/leafia/models/block/" + model + ".json")) {
				helper.assertTrue(in != null, "no model " + model);
			} catch(java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		}
		helper.assertTrue(models.size() == 19 + 11 + 1, "19 wreck parts, 11 debris parts and the graphite chip: " + models.size());
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aWreckDropsThreeToFiveDebrisNamingItsBlock(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockState concrete = ModBlocks.brick_concrete.get().defaultBlockState();
		for(int seed = 1; seed <= 3; seed++) {
			for(int fortune : new int[] {0, 2}) {
				RandomSource twin = RandomSource.create(seed);
				List<ItemStack> drops = LeafiaBlocks.lwrwreck_stone.get().drops(concrete, fortune, RandomSource.create(seed));
				int expected = 5 + fortune - twin.nextInt(3);
				helper.assertTrue(drops.size() == expected, "for(i = rand(3); i < 5 + fortune; i++): " + drops.size() + " vs " + expected);
				for(ItemStack drop : drops) {
					helper.assertTrue(drop.is(LeafiaItems.lwr_piece.get()), "a stone wreck breaks into pieces");
					helper.assertTrue("hbm:brick_concrete".equals(PWRDebrisItem.data(drop).getString("block")), "naming the block");
				}
			}
		}
		helper.assertTrue(LeafiaBlocks.lwrwreck_metal.get().drops(ModBlocks.block_thorium.get().defaultBlockState(), 0, RandomSource.create(4)).get(0)
				.is(LeafiaItems.lwr_shrapnel.get()), "a metal one into shrapnel");
		helper.assertTrue(LeafiaBlocks.lwrwreck_metal.get().drops(Blocks.GLASS.defaultBlockState(), 0, RandomSource.create(4)).get(0)
				.is(LeafiaItems.lwr_shard.get()), "glass into shards, whatever the wreck");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aWreckKeepsWhatItReplacedAcrossASave(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos pos = new BlockPos(1, 1, 1);
		BlockState shown = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z);
		LeafiaBlocks.lwrwreck_stone.get().create(helper.getLevel(), helper.absolutePos(pos), Direction.WEST, shown, Erosion.RUBBLE, 6, RandomSource.create(9));
		PWRMeshedWreckEntity wreck = (PWRMeshedWreckEntity) be(helper, pos);
		int variation = RandomSource.create(9).nextInt(4);
		helper.assertTrue(wreck.display == shown && wreck.erosion == Erosion.RUBBLE && wreck.scorch == 6 && wreck.variation == variation,
				"created: " + wreck.display + " " + wreck.erosion + " " + wreck.scorch + " " + wreck.variation);
		helper.assertTrue(wreck.toBits() == (byte) (6 + (3 << 3) + (variation << 5)), "the detail byte");
		CompoundTag saved = wreck.saveWithFullMetadata(helper.getLevel().registryAccess());
		helper.assertTrue("minecraft:oak_log".equals(saved.getString("block")), "upstream's key holds the name");
		BlockEntity loaded = BlockEntity.loadStatic(wreck.getBlockPos(), wreck.getBlockState(), saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded instanceof PWRMeshedWreckEntity copy && copy.display == shown && copy.erosion == Erosion.RUBBLE
				&& copy.scorch == 6 && copy.variation == variation, "and all of it comes back");
		helper.assertTrue(helper.getBlockState(pos).getValue(PWRMeshedWreck.FACING) == Direction.WEST, "facing the way it broke");
		helper.setBlock(pos, Blocks.AIR);
		helper.succeed();
	}

	// ----------------------------------------------------------------------------- the debris

	@GameTest(template = EMPTY)
	public static void nineDebrisCraftBackIntoTheirBlock(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		PWRDebrisCrafting recipe = new PWRDebrisCrafting(CraftingBookCategory.MISC);
		ItemStack piece = PWRDebrisItem.of(LeafiaItems.lwr_piece.get(), ModBlocks.brick_concrete.get().defaultBlockState());
		List<ItemStack> nine = new ArrayList<>();
		for(int i = 0; i < 9; i++) nine.add(piece.copy());
		helper.assertTrue(recipe.matches(CraftingInput.of(3, 3, nine), helper.getLevel()), "nine pieces match");
		helper.assertTrue(recipe.assemble(CraftingInput.of(3, 3, nine), helper.getLevel().registryAccess()).is(ModBlocks.brick_concrete.get().asItem()),
				"and make the block");
		List<ItemStack> eight = new ArrayList<>(nine);
		eight.set(4, ItemStack.EMPTY);
		helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, eight), helper.getLevel()), "eight do not");
		List<ItemStack> counts = new ArrayList<>(nine);
		counts.set(4, piece.copyWithCount(2));
		helper.assertTrue(recipe.matches(CraftingInput.of(3, 3, counts), helper.getLevel()), "nine of different counts still match (D-3076; upstream's areItemStacksEqual compared them)");
		helper.assertTrue(PWRDebrisItem.containsWord("Block of Steel", "of") && !PWRDebrisItem.containsWord("Offset Stone", "of") && !PWRDebrisItem.containsWord("Proof Glass", "of"),
				"the name's 'of' switch is a word, not a substring (D-3076)");
		List<ItemStack> shards = new ArrayList<>();
		for(int i = 0; i < 9; i++) shards.add(PWRDebrisItem.of(LeafiaItems.lwr_shard.get(), Blocks.GLASS.defaultBlockState()));
		helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, shards), helper.getLevel()), "shards never craft back");
		List<ItemStack> bare = new ArrayList<>();
		for(int i = 0; i < 9; i++) bare.add(new ItemStack(LeafiaItems.lwr_piece.get()));
		helper.assertTrue(!recipe.matches(CraftingInput.of(3, 3, bare), helper.getLevel()), "nor pieces that name no block");
		helper.assertTrue(recipe.canCraftInDimensions(3, 3) && !recipe.canCraftInDimensions(2, 2), "a crafting table's grid");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aDebrisPieceTakesItsKindFromItsBlock(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		var level = helper.getLevel();
		Object[][] rows = {
				{ModBlocks.brick_concrete.get(), Set.of(DebrisType.CONCRETE)},
				{ModBlocks.block_graphite.get(), Set.of(DebrisType.GRAPHITE)},
				{ModBlocks.block_graphite_rod.get(), Set.of(DebrisType.GRAPHITE)},
				{LeafiaBlocks.lwr_casing.get(), Set.of(DebrisType.BLANK, DebrisType.CONCRETE)},
				{LeafiaBlocks.lwr_reflector.get(), Set.of(DebrisType.BLANK, DebrisType.SHRAPNEL)},
				{LeafiaBlocks.lwr_control.get(), Set.of(DebrisType.CONTROL_FRAME, DebrisType.CONTROL_ROD)},
				{LeafiaBlocks.reactor_conductor.get(), Set.of(DebrisType.CHANNEL_1X, DebrisType.CHANNEL_3X)}};
		for(Object[] row : rows) {
			for(int i = 0; i < 40; i++) {
				DebrisType type = PWRDebrisEntity.typeFor(((Block) row[0]).defaultBlockState(), level);
				helper.assertTrue(((Set<?>) row[1]).contains(type), row[0] + " became " + type);
			}
		}
		PWRDebrisEntity planks = new PWRDebrisEntity(level, 0, 0, 0, Blocks.OAK_PLANKS.defaultBlockState());
		helper.assertTrue(planks.getEntityData().get(PWRDebrisEntity.FLAMMABILITY) == 729000 && planks.isOnFire(),
				"planks burn for (300 - 20 - 190)^3: " + planks.getEntityData().get(PWRDebrisEntity.FLAMMABILITY));
		PWRDebrisEntity stone = new PWRDebrisEntity(level, 0, 0, 0, Blocks.STONE.defaultBlockState());
		helper.assertTrue(stone.getEntityData().get(PWRDebrisEntity.FLAMMABILITY) == 0 && !stone.isOnFire(), "stone does not");
		float[] sizes = {0.5F, 0.5F, 0.5F, 0.25F, 1F, 1F, 1F, 1F};
		for(DebrisType type : DebrisType.values()) {
			stone.setType(type);
			helper.assertTrue(stone.getDimensions(Pose.STANDING).width() == sizes[type.ordinal()], type + " hitbox");
		}
		helper.assertTrue(stone.getLifetime() == 12000 && stone.destroysBlocks(), "ten minutes, and NTM-CE's smashing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aDebrisPieceGivesUpstreamsPickup(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		ServerPlayer player = mockPlayer(helper);
		Object[][] rows = {
				{DebrisType.CHANNEL_1X, new ItemStack(ModItems.plate_steel.get())},
				{DebrisType.CHANNEL_3X, new ItemStack(ModItems.pipe_steel.get(), 2)},
				{DebrisType.CONTROL_FRAME, new ItemStack(ModItems.debris_metal.get(), 3)},
				{DebrisType.GRAPHITE, new ItemStack(ModItems.debris_graphite.get())}};
		for(Object[] row : rows) {
			player.getInventory().clearContent();
			PWRDebrisEntity piece = new PWRDebrisEntity(helper.getLevel(), 0, 0, 0, LeafiaBlocks.lwr_channel.get().defaultBlockState());
			piece.setType((DebrisType) row[0]);
			piece.interact(player, InteractionHand.MAIN_HAND);
			ItemStack want = (ItemStack) row[1];
			helper.assertTrue(player.getInventory().countItem(want.getItem()) == want.getCount() && piece.isRemoved(), row[0] + " gives " + want);
		}
		player.getInventory().clearContent();
		PWRDebrisEntity blank = new PWRDebrisEntity(helper.getLevel(), 0, 0, 0, LeafiaBlocks.lwr_reflector.get().defaultBlockState());
		blank.setType(DebrisType.BLANK);
		blank.interact(player, InteractionHand.MAIN_HAND);
		ItemStack got = player.getInventory().getItem(0);
		helper.assertTrue(got.is(LeafiaItems.lwr_shrapnel.get()) && "leafia:lwr_reflector".equals(PWRDebrisItem.data(got).getString("block")),
				"a blank piece of an unshreddable block is shrapnel naming it: " + got);
		player.getInventory().clearContent();
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aDebrisPieceKeepsItsBlockAcrossASave(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
		PWRDebrisEntity piece = new PWRDebrisEntity(helper.getLevel(), 0, 0, 0, log);
		piece.setType(DebrisType.SHRAPNEL);
		piece.getEntityData().set(PWRDebrisEntity.COMBUSTION, 17);
		CompoundTag tag = new CompoundTag();
		piece.saveWithoutId(tag);
		helper.assertTrue("minecraft:oak_log".equals(tag.getString("block_rsc")), "upstream's key");
		PWRDebrisEntity copy = new PWRDebrisEntity(com.leafia.init.LeafiaEntities.PWR_DEBRIS.get(), helper.getLevel());
		copy.load(tag);
		helper.assertTrue(copy.getBlock() == log && copy.getDebrisType() == DebrisType.SHRAPNEL, "the block and the kind");
		int flammability = piece.getEntityData().get(PWRDebrisEntity.FLAMMABILITY);
		helper.assertTrue(flammability == 1157625 && copy.getEntityData().get(PWRDebrisEntity.FLAMMABILITY) == flammability
				&& copy.getEntityData().get(PWRDebrisEntity.COMBUSTION) == 17, "and the fire: a log's (300 - 5 - 190)^3");
		helper.succeed();
	}

	// --------------------------------------------------------------------------- the meltdown

	@GameTest(template = EMPTY)
	public static void theMeltdownsNumbersAreUpstreams(GameTestHelper helper) {
		helper.assertTrue(PWRMeltdown.debrisPerBlock(30) == 5 && PWRMeltdown.debrisPerBlock(30.01) == 4 && PWRMeltdown.debrisPerBlock(40) == 4
				&& PWRMeltdown.debrisPerBlock(40.5) == 3 && PWRMeltdown.debrisPerBlock(50) == 3 && PWRMeltdown.debrisPerBlock(51) == 2
				&& PWRMeltdown.debrisPerBlock(70) == 2 && PWRMeltdown.debrisPerBlock(70.5) == 1, "pieces per block by size");
		helper.assertTrue(PWRMeltdown.blastStrength(15_000) == 24F && Math.abs(PWRMeltdown.blastStrength(16_000) - 25.6F) < 1e-5, "24 x toughness / 15,000");
		near(helper, PWRMeltdown.heatBase(0, 10), 7, "clamped at 7");
		near(helper, PWRMeltdown.heatBase(5, 10), 0, "nothing at half the size");
		near(helper, PWRMeltdown.heatBase(2.5, 10), 5.856342783782502, "0.5^0.45 x 8");
		helper.assertTrue(PWRMeltdown.heat(5.5, 0) == 5 && PWRMeltdown.heat(5.5, 1) == 6 && PWRMeltdown.heat(5.5, 2) == 6
				&& PWRMeltdown.heat(5.2, 1) == 5 && PWRMeltdown.heat(5.2, 2) == 6, "floored, rounded, ceiled");
		helper.assertTrue(PWRMeltdown.outward(new Vec3(3, 1, 1)) == Direction.WEST && PWRMeltdown.outward(new Vec3(-3, 1, 1)) == Direction.EAST
				&& PWRMeltdown.outward(new Vec3(0, 2, 1)) == Direction.DOWN && PWRMeltdown.outward(new Vec3(0, -2, 1)) == Direction.UP
				&& PWRMeltdown.outward(new Vec3(1, 0, -3)) == Direction.SOUTH && PWRMeltdown.outward(new Vec3(1, 0, 3)) == Direction.NORTH
				&& PWRMeltdown.outward(new Vec3(1, 1, 0)) == Direction.UP, "the first face a wreck tries, and UP on a tie");
		helper.assertTrue(PWRMeltdown.erosion(2, false) == Erosion.SLIGHT && PWRMeltdown.erosion(1, false) == Erosion.RUBBLE
				&& PWRMeltdown.erosion(1, true) == Erosion.NORMAL && PWRMeltdown.erosion(0, true) == Erosion.NORMAL, "erosion by support");
		String[][] shake = PWRMeltdown.shakeParams(10, 16_000);
		helper.assertTrue(List.of(shake[0]).equals(List.of("type=smooth", "preset=PWR_NEAR", "bloomDulling*0.9990059290621766",
				"duration*1.2649110640673518", "range=40.0")), "the near shake: " + List.of(shake[0]));
		helper.assertTrue(List.of(shake[1]).equals(List.of("type=smooth", "preset=PWR_FAR", "duration*1.2649110640673518", "range=90.0")),
				"the far shake: " + List.of(shake[1]));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void heatCooksTheGroundIntoWaste(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor() || !LeafiaFeatures.legacyWaste) { helper.succeed(); return; }
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.DIRT.defaultBlockState(), 3) == LeafiaBlocks.waste_dirt.get().withMeta(3), "dirt at 3");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.PODZOL.defaultBlockState(), 9) == LeafiaBlocks.waste_dirt.get().withMeta(6), "podzol is dirt, capped at 6");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.RED_SAND.defaultBlockState(), 2) == LeafiaBlocks.waste_sand_red.get().withMeta(2), "red sand");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.SAND.defaultBlockState(), 2) == LeafiaBlocks.waste_sand.get().withMeta(2), "sand");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.LIME_TERRACOTTA.defaultBlockState(), 4) == LeafiaBlocks.waste_terracotta.get().withMeta(4), "any terracotta");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.SNOW.defaultBlockState(), 1) == LeafiaBlocks.waste_snow.get().withMeta(1), "a snow layer");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.GRASS_BLOCK.defaultBlockState(), 5) == ModBlocks.waste_earth.get().defaultBlockState(), "grass");
		BlockState slaked = ModBlocks.sellafield_slaked.get().defaultBlockState();
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.ANDESITE.defaultBlockState(), 1) == slaked.setValue(BlockSellafieldSlaked.LEVEL, 1), "stone is level 0");
		helper.assertTrue(PWRMeltdown.wasteFor(slaked.setValue(BlockSellafieldSlaked.LEVEL, 3), 2) == null, "sellafield hotter than the heat is left");
		helper.assertTrue(PWRMeltdown.wasteFor(slaked.setValue(BlockSellafieldSlaked.LEVEL, 3), 5) == slaked.setValue(BlockSellafieldSlaked.LEVEL, 5), "cooler is cooked");
		helper.assertTrue(PWRMeltdown.wasteFor(Blocks.RED_SANDSTONE.defaultBlockState(), 3) == null
				&& PWRMeltdown.wasteFor(Blocks.DEEPSLATE.defaultBlockState(), 3) == null, "neither red sandstone nor anything 1.12.2 lacked");
		helper.succeed();
	}

	/** The meltdown scenes: a member at C, a lwr_element whose block entity is the data's companion. */
	private static PWRData scene(GameTestHelper helper) {
		helper.setBlock(C, LeafiaBlocks.lwr_element.get());
		PWRData data = new PWRData(be(helper, C));
		data.members = new HashSet<>(Set.of(helper.absolutePos(C)));
		return data;
	}

	private static void cleanScene(GameTestHelper helper, BlockPos from, BlockPos to) {
		if(be(helper, C) instanceof PWRElementTE element) element.inventory.setStackInSlot(0, ItemStack.EMPTY);
		fill(helper, from, to, Blocks.AIR);
		AABB box = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(from)), Vec3.atLowerCornerOf(helper.absolutePos(to)).add(1, 1, 1)).inflate(32);
		for(PWRDebrisEntity piece : helper.getLevel().getEntitiesOfClass(PWRDebrisEntity.class, box)) piece.discard();
		PWRDiagnosis.preventScan.clear();
	}

	@GameTest(template = BIG)
	public static void theRaysDecideWhatVaporises(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos from = C.offset(-5, -5, -5), to = C.offset(5, 5, 5);
		try {
			fill(helper, from, to, Blocks.STONE);
			for(boolean open : new boolean[] {false, true}) {
				PWRData data = scene(helper);
				PWRExplosion boom = data.gatherMeltdown(helper.getLevel(), null);
				data.growMembers(data.growMembers(data.growMembers(data.growMembers(data.members))));
				helper.assertTrue(data.members.size() == 129, "four layers out on every face: the octahedron of radius 4, " + data.members.size());
				// the threshold rolls are the only random part: shut in, a constant roll of 0 (a threshold of 7) makes the outcome independent of the
				// order the members are taken in, which is a HashSet's and so differs with the test's position
				RandomSource rand = open ? RandomSource.create(42) : new ZeroRandom();
				Plan plan = PWRMeltdown.plan(data, boom, p -> open && !data.members.contains(p), rand);
				if(open) {
					RandomSource twin = RandomSource.create(42);
					for(int i = 0; i < 128; i++) twin.nextInt(3);
					helper.assertTrue(rand.nextLong() == twin.nextLong(), "one threshold roll per ray-casting member, 128");
				}
				helper.assertTrue(plan.elements.keySet().equals(Set.of(helper.absolutePos(C))), "the fuel column melts, it does not cast rays");
				// the octahedron is symmetric about the core's middle (D-3076: upstream measured from the core's corner, so every ray
				// leaned half a block to +x/+y/+z), so the rays sum to nothing on every axis
				if(open) {
					helper.assertTrue(plan.vaporized.size() == 128 && plan.remains.isEmpty(), "open all round: everything goes");
					near(helper, plan.pressure.x, 0, "pressure outward cancels");
				} else {
					// shut in, a member counts only the members its eleven samples land on. The six faces of the core keep all
					// eleven inside; the twelve edges keep exactly seven, the lowest threshold; nothing else reaches seven
					helper.assertTrue(new HashSet<>(plan.vaporized).equals(shutInVaporised(helper)) && plan.remains.size() == 110,
							"shut in: the six faces and twelve edges go, " + plan.vaporized);
					near(helper, plan.pressure.y, 0, "pressure inward cancels too");
				}
				helper.setBlock(C, Blocks.STONE);
			}
		} finally {
			cleanScene(helper, from, to);
		}
		helper.succeed();
	}

	/** A roll that is always 0: a threshold of 7 and a floored heat, whatever order the members come in. */
	private static final class ZeroRandom extends net.minecraft.world.level.levelgen.LegacyRandomSource {
		ZeroRandom() {
			super(0L);
		}

		@Override
		public int nextInt(int bound) {
			return 0;
		}
	}

	/** What a shut-in meltdown at C vaporises (worked out by hand: see theRaysDecideWhatVaporises): the six faces and twelve edges of the core. */
	private static Set<BlockPos> shutInVaporised(GameTestHelper helper) {
		Set<BlockPos> out = new HashSet<>();
		for(int dx = -1; dx <= 1; dx++)
			for(int dy = -1; dy <= 1; dy++)
				for(int dz = -1; dz <= 1; dz++) {
					int nonzero = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
					if(nonzero == 1 || nonzero == 2) out.add(helper.absolutePos(C.offset(dx, dy, dz)));
				}
		return out;
	}

	/**
	 * What a shut-in meltdown at C cooks: the members that stay and touch one that went. (The walls touching the core do not
	 * count: a U-238 rod melts into the base mod's solid {@code block_corium}, which buries them, and every one of the core's
	 * neighbours went in any case.)
	 */
	private static Set<BlockPos> shutInCooked(GameTestHelper helper) {
		Set<BlockPos> went = shutInVaporised(helper);
		Set<BlockPos> out = new HashSet<>();
		for(int dx = -4; dx <= 4; dx++)
			for(int dy = -4; dy <= 4; dy++)
				for(int dz = -4; dz <= 4; dz++) {
					if(Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 4 || (dx == 0 && dy == 0 && dz == 0)) continue;
					BlockPos pos = helper.absolutePos(C.offset(dx, dy, dz));
					if(went.contains(pos)) continue;
					for(Direction d : Direction.values())
						if(went.contains(pos.relative(d))) {
							out.add(pos);
							break;
						}
				}
		return out;
	}

	@GameTest(template = BIG)
	public static void aShutInMeltdownCooksTheCoresShellAndGivesTheWallsBack(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos from = C.offset(-5, -5, -5), to = C.offset(5, 5, 5);
		try {
			fill(helper, from, to, Blocks.STONE);
			PWRData data = scene(helper);
			LeafiaRodItem rod = LeafiaRods.get("leafRodU38");
			((PWRElementTE) be(helper, C)).inventory.setStackInSlot(0, new ItemStack(rod));
			PWRExplosion boom = data.gatherMeltdown(helper.getLevel(), null);
			List<Object[]> blasts = new ArrayList<>();
			Result result = PWRMeltdown.run(data, boom, new ZeroRandom(), p -> false, (w, at, s) -> blasts.add(new Object[] {at, s}));
			helper.assertTrue(result.reactorSize == 7, "one block's reactor is 1 + 2 + 4 across: " + result.reactorSize);
			helper.assertTrue(blasts.size() == 1 && ((Vec3) blasts.get(0)[0]).equals(boom.centerPoint.add(0.5, 0.5, 0.5))
					&& Math.abs((float) blasts.get(0)[1] - 25.6F) < 1e-5, "one blast of 24 x 16,000 / 15,000 at the centre");
			helper.assertTrue(helper.getBlockState(C).getBlock() == rod.corium(), "the column is its rod's corium");
			helper.assertTrue(result.debris.isEmpty(), "nothing thrown: what vaporised touched a wall that stayed");
			Set<BlockPos> went = shutInVaporised(helper);
			helper.assertTrue(new HashSet<>(result.placeWrecks).equals(went), "the eighteen that went touch walls that stay, so they are set aside for wreck rather than thrown, " + result.placeWrecks);
			Set<BlockPos> cooked = shutInCooked(helper);
			Set<BlockPos> wrecked = new HashSet<>(cooked);
			wrecked.addAll(went);
			Set<BlockPos> got = new HashSet<>(result.allDebris);
			helper.assertTrue(got.equals(wrecked), "the walls not buried: those that went and those beside them, " + got.size() + " against " + wrecked.size());
			// stone is not Material.IRON, so a wall that stays is cooked rather than eroded: slaked sellafield at min(heat, 6), heat floored
			// by the constant roll. The ones that went face only solid blocks - the corium included - so no side is open for a wreck to
			// face, and they are given back as they were.
			Vec3 middle = boom.centerPoint.add(0.5, 0.5, 0.5);
			for(BlockPos abs : cooked) {
				BlockPos pos = abs.subtract(helper.absolutePos(BlockPos.ZERO));
				BlockState wall = helper.getBlockState(pos);
				helper.assertTrue(wall.getBlock() == ModBlocks.sellafield_slaked.get(), pos + " is cooked: " + wall);
				int lv = wall.getValue(BlockSellafieldSlaked.LEVEL);
				double length = Vec3.atCenterOf(abs).distanceTo(middle); // the member's middle to the reactor's middle (D-3076)
				int expected = Math.min(6, PWRMeltdown.heat(PWRMeltdown.heatBase(length, 7), 0));
				helper.assertTrue(lv == expected, pos + " at " + length + ": level " + lv + ", expected " + expected);
			}
			for(int x = from.getX(); x <= to.getX(); x++)
				for(int y = from.getY(); y <= to.getY(); y++)
					for(int z = from.getZ(); z <= to.getZ(); z++) {
						BlockPos pos = new BlockPos(x, y, z);
						if(pos.equals(C) || cooked.contains(helper.absolutePos(pos))) continue;
						helper.assertTrue(helper.getBlockState(pos).getBlock() == Blocks.STONE, pos + ": every other wall, and every bedrock stand-in, is given back");
					}
		} finally {
			cleanScene(helper, from, to);
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void anOpenMeltdownVaporisesTheWallsIntoDebrisAndRadon(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos from = C.offset(-5, -5, -5), to = C.offset(5, 5, 5);
		BlockPos[] walls = {C.offset(2, 0, 0), C.offset(0, 2, 0), C.offset(0, 0, -1)};
		try {
			PWRData data = scene(helper);
			for(BlockPos wall : walls) helper.setBlock(wall, Blocks.STONE);
			PWRExplosion boom = data.gatherMeltdown(helper.getLevel(), null);
			RandomSource rand = RandomSource.create(11);
			Result result = PWRMeltdown.run(data, boom, rand, p -> true, (w, at, s) -> {});
			helper.assertTrue(result.plan.vaporized.size() == 3, "the three stone walls vaporise; the air around them only pushes");
			helper.assertTrue(result.debris.size() == 3 * 5, "five pieces each at size 7: " + result.debris.size());
			for(PWRDebrisEntity piece : result.debris)
				helper.assertTrue(piece.getBlock().getBlock() == Blocks.STONE && piece.getDebrisType() == DebrisType.CONCRETE, "stone concrete pieces");
			for(BlockPos wall : walls)
				helper.assertTrue(helper.getBlockState(wall).getBlock() == ModBlocks.gas_radon_dense.get(), wall + " is dense radon");
			helper.assertTrue(helper.getBlockState(C).isAir(), "a column with no rod melts to nothing");
			RandomSource twin = RandomSource.create(11);
			for(int i = 0; i < 3; i++) twin.nextInt(3);
			for(int i = 0; i < 45; i++) twin.nextDouble();
			helper.assertTrue(rand.nextLong() == twin.nextLong(), "three threshold rolls, then three per piece");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(PWRDebrisEntity.class, new AABB(helper.absolutePos(C)).inflate(6)).size() == 15,
					"and they are in the world");
		} finally {
			cleanScene(helper, from, to);
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aBrokenWallFacesTheOpenSideAndErodesByItsSupport(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos m = C;
		Vec3 centre = Vec3.atLowerCornerOf(helper.absolutePos(C.offset(0, -4, 0))); // ray (0.5, 4.5, 0.5): heat 6.085
		BlockPos from = C.offset(-2, -2, -2), to = C.offset(2, 2, 2);
		try {
			// A: a hull block on stone, open above and all round: faces up, RUBBLE (not boxed in)
			// B: the same with stone north and south: two firm sides, SLIGHT
			// C: a reflector (metal sound) as A: metal wreck
			Object[][] cases = {
					{LeafiaBlocks.lwr_casing.get(), false, LeafiaBlocks.lwrwreck_stone.get(), Erosion.RUBBLE},
					{LeafiaBlocks.lwr_casing.get(), true, LeafiaBlocks.lwrwreck_stone.get(), Erosion.SLIGHT},
					{LeafiaBlocks.lwr_reflector.get(), false, LeafiaBlocks.lwrwreck_metal.get(), Erosion.RUBBLE}};
			for(Object[] c : cases) {
				fill(helper, from, to, Blocks.AIR);
				helper.setBlock(m, (Block) c[0]);
				helper.setBlock(m.below(), Blocks.STONE);
				if((boolean) c[1]) {
					helper.setBlock(m.north(), Blocks.STONE);
					helper.setBlock(m.south(), Blocks.STONE);
				}
				BlockState was = helper.getBlockState(m);
				Set<BlockPos> wrecks = new HashSet<>(Set.of(helper.absolutePos(m)));
				Set<BlockPos> anti = new HashSet<>();
				PWRMeltdown.wreckOne(helper.getLevel(), helper.absolutePos(m), centre, 20, new java.util.HashMap<>(), wrecks, anti, RandomSource.create(5));
				RandomSource twin = RandomSource.create(5);
				int heat = PWRMeltdown.heat(PWRMeltdown.heatBase(Math.sqrt(20.75), 20), twin.nextInt(3));
				PWRMeshedWreck wreck = (PWRMeshedWreck) c[2];
				int variation = twin.nextInt(wreck.getVariations().list((Erosion) c[3]).length);
				helper.assertTrue(helper.getBlockState(m).getBlock() == wreck, c[0] + " became " + helper.getBlockState(m));
				helper.assertTrue(helper.getBlockState(m).getValue(PWRMeshedWreck.FACING) == Direction.UP, "facing the open side above");
				PWRMeshedWreckEntity entity = (PWRMeshedWreckEntity) be(helper, m);
				helper.assertTrue(entity.erosion == c[3] && entity.display == was && entity.scorch == heat && entity.variation == variation,
						c[0] + ": " + entity.erosion + " " + entity.scorch + "/" + heat + " " + entity.variation + "/" + variation);
				helper.assertTrue(anti.contains(helper.absolutePos(m)), "and is not put back");
			}
		} finally {
			fill(helper, from, to, Blocks.AIR);
			PWRDiagnosis.preventScan.clear();
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aMeltedControlRodIsElectricalScrapInAnIntactWreck(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		BlockPos m = C;
		Vec3 centre = Vec3.atLowerCornerOf(helper.absolutePos(C)).subtract(1.5, 0.5, 0.5); // the rod's middle is (1.5, 0.5, 0.5) from it: 1.658 at size 7, heat 5 or 6 (D-3076)
		try {
			helper.setBlock(m, LeafiaBlocks.lwr_control.get());
			Set<BlockPos> anti = new HashSet<>();
			PWRMeltdown.wreckOne(helper.getLevel(), helper.absolutePos(m), centre, 7, new java.util.HashMap<>(), new HashSet<>(), anti, RandomSource.create(3));
			helper.assertTrue(helper.getBlockState(m).getBlock() == LeafiaBlocks.lwrwreck_stone.get(), "an intact stone wreck: " + helper.getBlockState(m));
			PWRMeshedWreckEntity entity = (PWRMeshedWreckEntity) be(helper, m);
			helper.assertTrue(entity.erosion == Erosion.NONE && entity.display == ModBlocks.block_electrical_scrap.get().defaultBlockState()
					&& (entity.scorch == 5 || entity.scorch == 6), "of electrical scrap, upstream's way: " + entity.display + " " + entity.scorch);
		} finally {
			helper.setBlock(m, Blocks.AIR);
			PWRDiagnosis.preventScan.clear();
		}
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aLevelThreeMeltdownCallsTheWreckHook(GameTestHelper helper) {
		if(!LeafiaFeatures.pwrReactor()) { helper.succeed(); return; }
		PWRData.Lv3Meltdown real = PWRData.LV3_MELTDOWN;
		helper.assertTrue(real != null, "the hook is set at initialisation");
		List<Object[]> calls = new ArrayList<>();
		try {
			PWRData data = scene(helper);
			PWRExplosion boom = data.gatherMeltdown(helper.getLevel(), null);
			PWRData.LV3_MELTDOWN = (d, b) -> calls.add(new Object[] {d, b});
			boom.explodeLv3();
			helper.assertTrue(calls.size() == 1 && calls.get(0)[0] == data && calls.get(0)[1] == boom && boom.ran == 3,
					"level 3 runs the meltdown, not level 2: ran " + boom.ran);
			helper.assertTrue(PWRData.meltdownLevel(15_000, 0) == 3, "a hull-walled vessel's level");
		} finally {
			PWRData.LV3_MELTDOWN = real;
			helper.setBlock(C, Blocks.AIR);
			PWRDiagnosis.preventScan.clear();
		}
		helper.succeed();
	}
}
