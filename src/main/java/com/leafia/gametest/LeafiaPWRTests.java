package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blocks.IRadResistantBlock;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.PWRDiagnosis;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRAssignableEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.computer.PWRComputerTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRProxyTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.port.PWRPortTE;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable.ToolType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The addon PWR, core batch (D-3006): a reactor built in a shielding box, diagnosed, and read against
 * numbers worked by hand from upstream's formulas.
 *
 * <p>Every test that runs the reactor does it inside one body, and empties and removes what it built
 * before it succeeds: a block entity ticks on its own between bodies (D-953), and a fuel column left with
 * a rod after its test would go on heating, and a meltdown blasts and wastes a radius-35 sphere of the
 * GameTest world. The meltdown itself is checked through {@link PWRData#gatherMeltdown} and
 * {@link PWRData#meltdownLevel}, and the blowout through an overridden {@code explode}, never by a blast.
 *
 * <p>Rig A, used by most tests, in template coordinates, inside a hull box x 3..9, y 2..7, z 2..8:
 * fuel columns E1 (5,4,5) and E2 (7,4,5) with a coolant channel CH (6,4,5) between them, an integrated
 * boiler B (6,4,4), a heat exchanger X (6,4,6) and a fluid port P (6,5,5). Every non-component block within
 * two of a component is hull (blast resistance 165), so the toughness is {@code (int)(165^0.25 x 4800) =
 * 17203}.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaPWRTests {

	private static final String BIG = "empty16";
	private static final double EPS = 1e-6;

	private static final BlockPos E1 = new BlockPos(5, 4, 5);
	private static final BlockPos CH = new BlockPos(6, 4, 5);
	private static final BlockPos E2 = new BlockPos(7, 4, 5);
	private static final BlockPos B = new BlockPos(6, 4, 4);
	private static final BlockPos X = new BlockPos(6, 4, 6);
	private static final BlockPos P = new BlockPos(6, 5, 5);

	// ---- the rig ---------------------------------------------------------------------------

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

	private static void box(GameTestHelper helper, Block block) {
		for(int x = 3; x <= 9; x++)
			for(int y = 2; y <= 7; y++)
				for(int z = 2; z <= 8; z++)
					helper.setBlock(new BlockPos(x, y, z), block);
	}

	/** Empties every fuel column and clears the box, so nothing is left to tick (class doc). */
	private static void teardown(GameTestHelper helper) {
		for(int x = 3; x <= 9; x++)
			for(int y = 2; y <= 7; y++)
				for(int z = 2; z <= 8; z++)
					if(be(helper, new BlockPos(x, y, z)) instanceof PWRElementTE element)
						element.inventory.setStackInSlot(0, ItemStack.EMPTY);
		box(helper, Blocks.AIR);
		PWRDiagnosis.preventScan.clear();
	}

	/** The block entity at a template position, or null; GameTestHelper.getBlockEntity fails on none. */
	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static void diagnose(GameTestHelper helper, BlockPos from) {
		PWRDiagnosis.preventScan.clear();
		BlockPos abs = helper.absolutePos(from);
		((PWRComponentBlock) helper.getBlockState(from).getBlock()).beginDiagnosis(helper.getLevel(), abs, abs);
	}

	/** The one PWRData among {@code candidates}' block entities; fails if there is not exactly one. */
	private static PWRData core(GameTestHelper helper, BlockPos... candidates) {
		PWRData found = null;
		int count = 0;
		for(BlockPos pos : candidates) {
			if(be(helper, pos) instanceof PWRComponentEntity entity && entity.getCore() != null) {
				found = entity.getCore();
				count++;
			}
		}
		helper.assertTrue(count == 1, "exactly one core, found " + count);
		return found;
	}

	private static PWRData rigA(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_channel.get());
		helper.setBlock(E2, LeafiaBlocks.lwr_element.get());
		helper.setBlock(B, LeafiaBlocks.reactor_conductor.get());
		helper.setBlock(X, LeafiaBlocks.lwr_exchanger.get());
		helper.setBlock(P, LeafiaBlocks.lwr_port.get());
		diagnose(helper, E1);
		return core(helper, E1, E2, P);
	}

	private static PWRElementTE element(GameTestHelper helper, BlockPos pos) {
		return (PWRElementTE) be(helper, pos);
	}

	private static LeafiaRodItem u238(GameTestHelper helper) {
		LeafiaRodItem rod = LeafiaRods.get("leafRodU38");
		helper.assertTrue(rod != null, "the U-238 rod is registered");
		return rod;
	}

	private static ItemStack rodAt(LeafiaRodItem rod, double heat) {
		ItemStack stack = new ItemStack(rod);
		CompoundTag tag = new CompoundTag();
		tag.putDouble("heat", heat);
		LeafiaRodItem.putData(stack, tag);
		return stack;
	}

	private static double heat(ItemStack stack) {
		CompoundTag tag = LeafiaRodItem.data(stack);
		return tag == null ? Double.NaN : tag.getDouble("heat");
	}

	/** A U-238 rod's flux into another U-238 rod: max(heat, 20) x compat x emission x reactivity. */
	private static double flux(LeafiaRodItem rod, double heat, boolean moderated) {
		// U-238 splits into fast neutrons and is split by slow ones, so a straight route is 0.5 and a
		// moderated one 3 (LeafiaRodItem.getFlux).
		return Math.max(heat, 20) * (moderated ? 3 : 0.5) * rod.emission * rod.reactivity;
	}

	// ---- the blocks ------------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void theBlocksAreUpstreamsStrengthAndShielding(GameTestHelper helper) {
		helper.assertTrue(LeafiaFeatures.pwrReactor(), "pwrBlocks and addonFluids are on by default");
		Object[][] table = {
				// block, hardness, resistance (x 0.6 already: GenericBlockResistance x 5/3 x 3/5), sound, shields
				{LeafiaBlocks.lwr_casing.get(), 24F, 165F, SoundType.STONE, true},
				{LeafiaBlocks.lwr_reflector.get(), 24F, 165F, SoundType.METAL, true},
				{LeafiaBlocks.lwr_element.get(), 8F, 27F, null, false},
				{LeafiaBlocks.reactor_element.get(), 5F, 6F, null, false},
				{LeafiaBlocks.reactor_element_blank.get(), 5F, 6F, null, false},
				{LeafiaBlocks.lwr_control.get(), 8F, 27F, SoundType.METAL, false},
				{LeafiaBlocks.reactor_control.get(), 5F, 6F, SoundType.METAL, false},
				{LeafiaBlocks.lwr_channel.get(), 8F, 27F, null, false},
				{LeafiaBlocks.reactor_conductor.get(), 8F, 27F, null, false},
				{LeafiaBlocks.lwr_exchanger.get(), 8F, 27F, SoundType.METAL, false},
				{LeafiaBlocks.lwr_port.get(), 275F, 165F, SoundType.STONE, true},
				{LeafiaBlocks.lwr_signal.get(), 275F, 165F, SoundType.STONE, true},
				{LeafiaBlocks.lwr_occs_in.get(), 275F, 165F, SoundType.STONE, true},
				{LeafiaBlocks.lwr_occs_out.get(), 275F, 165F, SoundType.STONE, true},
		};
		for(Object[] row : table) {
			Block block = (Block) row[0];
			BlockState state = block.defaultBlockState();
			String name = block.getDescriptionId();
			near(helper, state.getDestroySpeed(helper.getLevel(), BlockPos.ZERO), (Float) row[1], name + " hardness");
			near(helper, block.getExplosionResistance(), (Float) row[2], name + " resistance");
			if(row[3] != null) helper.assertTrue(state.getSoundType() == row[3], name + " sound");
			helper.assertTrue(state.requiresCorrectToolForDrops(), name + " needs a pickaxe (Material.IRON)");
			helper.assertTrue(IRadResistantBlock.blocksRadiation(state) == (Boolean) row[4], name + " shields: " + row[4]);
			helper.assertTrue(PWRElementTE.stopsNeutrons(state) == (Boolean) row[4], name + " stops neutrons: " + row[4]);
		}
		// The fuel and the channels share upstream's soundTypePWRTube: pipe placed, metal block broken, half volume.
		SoundType tube = LeafiaBlocks.lwr_channel.get().defaultBlockState().getSoundType();
		near(helper, tube.getVolume(), 0.5F, "the tube sound's volume");
		helper.assertTrue(tube == LeafiaBlocks.lwr_element.get().defaultBlockState().getSoundType(), "the fuel sounds like a tube");
		helper.assertTrue(LeafiaBlocks.lwr_casing.get() instanceof AddonRadResistantBlockBaked, "the hull is a rad-resistant block");
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theDarkConcreteShieldsAndStopsNeutronsByFinish(GameTestHelper helper) {
		if(!LeafiaFeatures.concreteBricks) {
			helper.succeed();
			return;
		}
		Block[] finishes = {LeafiaBlocks.brick_concrete_dark.get(), LeafiaBlocks.brick_concrete_dark_mossy.get(),
				LeafiaBlocks.brick_concrete_dark_cracked.get(), LeafiaBlocks.brick_concrete_dark_broken.get()};
		boolean[] shields = {true, true, false, false};
		for(int i = 0; i < 4; i++) {
			BlockState state = finishes[i].defaultBlockState();
			helper.assertTrue(((IRadResistantBlock) finishes[i]).getResistance() == (shields[i] ? 1 : 0), finishes[i].getDescriptionId() + " resistance flag");
			helper.assertTrue(IRadResistantBlock.blocksRadiation(state) == shields[i], finishes[i].getDescriptionId() + " shields radiation");
			// D-3076: the cracked and broken finishes (which do not shield radiation) no longer stop the PWR's neutrons either
			helper.assertTrue(PWRElementTE.stopsNeutrons(state) == shields[i], finishes[i].getDescriptionId() + " stops the PWR's neutrons only if it shields");
		}
		helper.succeed();
	}

	// ---- the diagnosis ---------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void aReactorFormsInsideItsVesselAndSizesItsTanks(GameTestHelper helper) {
		PWRData data = rigA(helper);
		Set<BlockPos> expected = new HashSet<>();
		for(BlockPos pos : new BlockPos[] {E1, CH, E2, B, X, P}) expected.add(helper.absolutePos(pos));
		helper.assertTrue(new HashSet<>(data.members).equals(expected), "the six components are the members: " + data.members);
		helper.assertTrue(data.fuels.equals(Set.of(helper.absolutePos(E1), helper.absolutePos(E2))), "two fuel columns");
		helper.assertTrue(data.controls.isEmpty(), "no control rods");
		for(BlockPos pos : new BlockPos[] {E1, E2, P}) {
			PWRComponentEntity entity = (PWRComponentEntity) be(helper, pos);
			helper.assertTrue(entity.getCoreByCorePos() == data, pos + " links to the core");
		}

		helper.assertTrue(data.toughness == 17203, "toughness (int)(165^0.25 x 4800), got " + data.toughness);
		helper.assertTrue(data.lastChannels == 2 && data.lastConductors == 1, "the boiler counts as a channel");
		helper.assertTrue(data.tanks[0].getMaxFill() == 6400, "coolant 3200 x 2 channels: " + data.tanks[0].getMaxFill());
		helper.assertTrue(data.tanks[1].getMaxFill() == 6400, "hot coolant (a liquid) 3200 x ceil(2 x 1): " + data.tanks[1].getMaxFill());
		helper.assertTrue(data.tanks[2].getMaxFill() == 17203, "the buffer is the toughness: " + data.tanks[2].getMaxFill());
		helper.assertTrue(data.tanks[3].getMaxFill() == 25600 && data.tanks[4].getMaxFill() == 12800, "one boiler's water and steam");
		for(int i = 5; i <= 7; i++)
			helper.assertTrue(data.tanks[i].getMaxFill() == 1500, "OCCS tank " + i + " is 750 x 2 columns: " + data.tanks[i].getMaxFill());
		helper.assertTrue(data.tanks[2].getTankType() == LeafiaFluids.COOLANT_MAL, "the buffer holds malfunctioning coolant");
		helper.assertTrue(data.projection.size() == 5, "the terminal's grid: two fuel, channel, boiler, exchanger: " + data.projection);
		helper.assertTrue(data.remoteContainer.getSlots() == 2, "a remote slot per fuel column");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aFuelColumnCountsTheChannelsAndExchangersNearIt(GameTestHelper helper) {
		rigA(helper);
		// E1: the channel 1 away, the boiler (a channel) sqrt 2 away, the exchanger sqrt 2 away; range 3, exponent 0.2.
		float ch = 0;
		ch += Math.pow(Math.max(1 - 1 / 3d, 0), 0.2);
		ch += Math.pow(Math.max(1 - Math.sqrt(2) / 3, 0), 0.2);
		float ex = 0;
		ex += Math.pow(Math.max(1 - Math.sqrt(2) / 3, 0), 0.2);
		for(BlockPos pos : new BlockPos[] {E1, E2}) {
			PWRElementTE element = element(helper, pos);
			near(helper, element.channelScale, (float) Math.pow(ch, 0.35), pos + " channelScale");
			near(helper, element.exchangerScale, 1 + ex, pos + " exchangerScale");
		}
		near(helper, element(helper, E1).channelScale, 1.2289878F, "hand-worked (float32) channelScale");
		near(helper, element(helper, E1).exchangerScale, 1.8802879F, "hand-worked (float32) exchangerScale");
		teardown(helper);
		helper.succeed();
	}

	// ---- the neutrons ----------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void aColumnReadsItsNeighbourThroughTheModeratingChannel(GameTestHelper helper) {
		rigA(helper);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 100));
		element(helper, E2).inventory.setStackInSlot(0, rodAt(rod, 100));
		PWRElementTE e1 = element(helper, E1);
		// East: the channel (a moderated row), then E2, then hull, which carves the route shut. The other
		// three sides are hull at once, and every diagonal is hull or a non-fuel block.
		helper.assertTrue(e1.linearFuelMap.size() == 1, "one straight route: " + e1.linearFuelMap.size());
		helper.assertTrue(e1.cornerFuelMap.isEmpty(), "no corner routes: " + e1.cornerFuelMap.size());
		PWRElementTE.HeatRetrival route = e1.linearFuelMap.iterator().next();
		helper.assertTrue(route.fuelPos.equals(helper.absolutePos(E2)), "the route ends at E2");
		near(helper, route.moderation, 1.0, "every row of the route passed the channel");
		near(helper, route.getControlMin(helper.getLevel()), 1.0, "nothing closes it");
		// So E1 receives E2's moderated flux: 100 x 3 x emission x reactivity, at full weight x height 1.
		near(helper, e1.heatDetection(rod, 1), flux(rod, 100, true), "E1's incoming flux");
		near(helper, e1.heatDetection(rod, 1), 300 * rod.emission * rod.reactivity, "hand-worked");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aCornerNeighbourCountsHalfByEachOfItsTwoRoutes(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		BlockPos e3 = new BlockPos(6, 4, 6);
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_channel.get());
		helper.setBlock(new BlockPos(5, 4, 6), LeafiaBlocks.lwr_channel.get());
		helper.setBlock(e3, LeafiaBlocks.lwr_element.get());
		diagnose(helper, E1);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 100));
		element(helper, e3).inventory.setStackInSlot(0, rodAt(rod, 100));
		PWRElementTE e1 = element(helper, E1);
		helper.assertTrue(e1.linearFuelMap.isEmpty(), "no straight route: both channels end in hull");
		helper.assertTrue(e1.cornerFuelMap.size() == 1, "one corner, reached two ways: " + e1.cornerFuelMap.size());
		// Each way is moderated (a channel) and weighs height / 2: 2 x (300 er x 1 x 1/2) = 300 er.
		near(helper, e1.heatDetection(rod, 1), flux(rod, 100, true), "E1's incoming flux from the corner");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aReflectorSendsTheColumnsOwnFluxBack(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_reflector.get());
		diagnose(helper, E1);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 100));
		PWRElementTE e1 = element(helper, E1);
		helper.assertTrue(e1.linearFuelMap.size() == 1, "the reflector is one straight route: " + e1.linearFuelMap.size());
		helper.assertTrue(e1.cornerFuelMap.isEmpty(), "a corner needs both of its ways: " + e1.cornerFuelMap.size());
		PWRElementTE.HeatRetrival route = e1.linearFuelMap.iterator().next();
		helper.assertTrue(route.fuelPos.equals(helper.absolutePos(E1)), "the route is the column's own");
		near(helper, route.moderation, 0, "a reflector does not moderate");
		near(helper, e1.heatDetection(rod, 1), flux(rod, 100, false), "E1 receives its own fast flux back");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aControlRodOpensTheRouteByItsWithdrawnFraction(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_control.get());
		helper.setBlock(E2, LeafiaBlocks.lwr_element.get());
		diagnose(helper, E1);
		PWRData data = core(helper, E1, CH, E2);
		helper.assertTrue(data.controls.equals(Set.of(helper.absolutePos(CH))), "the rod is the reactor's control");
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 100));
		element(helper, E2).inventory.setStackInSlot(0, rodAt(rod, 100));
		PWRElementTE e1 = element(helper, E1);
		PWRControlTE control = (PWRControlTE) be(helper, CH);
		double full = flux(rod, 100, false); // a control rod does not moderate
		double[] positions = {0, 0.25, 0.5, 1};
		for(double position : positions) {
			control.position = position;
			// The rod covers the column's height exactly: its bottom range [0, 1] opens [1 - p, 1].
			near(helper, e1.heatDetection(rod, 1), full * position, "flux with the rod " + position + " withdrawn");
		}
		teardown(helper);
		helper.succeed();
	}

	// ---- the control rods ------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void aRodColumnMovesAtItsMaterialsSpeedOverItsHeight(GameTestHelper helper) {
		Block[] kinds = {LeafiaBlocks.lwr_control.get(), LeafiaBlocks.reactor_control.get()};
		double[] speeds = {0.22 / 20, 0.1 / 20};
		for(int k = 0; k < 2; k++) {
			BlockPos bottom = new BlockPos(3 + k * 4, 2, 3);
			BlockPos top = bottom.above();
			helper.setBlock(bottom, kinds[k]);
			helper.setBlock(top, kinds[k]);
			helper.assertTrue(helper.getBlockState(bottom).getValue(PWRControlBlock.stacked), "the lower block is stacked");
			helper.assertTrue(be(helper, bottom) == null, "a stacked rod block holds no block entity");
			PWRControlTE control = (PWRControlTE) be(helper, top);
			control.updateHeight();
			helper.assertTrue(control.height == 2, "a two-block rod: " + control.height);
			near(helper, PWRControlTE.speedOf(kinds[k]), speeds[k], "speed of " + kinds[k].getDescriptionId());
			control.targetPosition = 1;
			control.tick();
			near(helper, control.position, speeds[k] / 2, "one tick moves speed / height");
			control.position = 1 - speeds[k] / 2 * 0.5;
			control.tick();
			near(helper, control.position, 1, "a step past the target snaps to it");
			helper.setBlock(top, Blocks.AIR);
			helper.assertTrue(!helper.getBlockState(bottom).getValue(PWRControlBlock.stacked), "the rod's last block is the top again");
			helper.assertTrue(be(helper, bottom) instanceof PWRControlTE, "and holds a fresh block entity");
			helper.setBlock(bottom, Blocks.AIR);
		}
		PWRDiagnosis.preventScan.clear();
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aScrewdriverMovesTheRodAQuarterBlockAndANameTagGroupsIt(GameTestHelper helper) {
		BlockPos bottom = new BlockPos(4, 2, 4);
		BlockPos top = bottom.above();
		helper.setBlock(bottom, LeafiaBlocks.lwr_control.get());
		helper.setBlock(top, LeafiaBlocks.lwr_control.get());
		PWRControlTE control = (PWRControlTE) be(helper, top);
		PWRControlBlock block = LeafiaBlocks.lwr_control.get();
		ServerPlayer player = mockPlayer(helper);
		// Used on the lower block: the top's entity answers.
		block.onScrew(helper.getLevel(), player, helper.absolutePos(bottom), Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER);
		near(helper, control.targetPosition, 0.25 / 2, "raised a quarter block of a two-block rod");
		player.setShiftKeyDown(true);
		block.onScrew(helper.getLevel(), player, helper.absolutePos(top), Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER);
		near(helper, control.targetPosition, 0, "sneaking lowers it");
		helper.assertTrue(!block.onScrew(helper.getLevel(), player, helper.absolutePos(top), Direction.UP, Vec3.ZERO, ToolType.SCREWDRIVER),
				"lowering a rod already at the bottom does nothing");
		player.setShiftKeyDown(false);

		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("Bank A"));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(top)), Direction.UP, helper.absolutePos(top), false);
		helper.getBlockState(top).useItemOn(tag, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(control.name.equals("Bank A"), "a named tag names the group: " + control.name);
		ItemStack plain = new ItemStack(Items.NAME_TAG);
		helper.getBlockState(top).useItemOn(plain, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(control.name.equals(PWRControlTE.defaultName), "a plain tag resets it to " + PWRControlTE.defaultName);
		helper.setBlock(top, Blocks.AIR);
		helper.setBlock(bottom, Blocks.AIR);
		PWRDiagnosis.preventScan.clear();
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theCoreSetsEveryRodInAGroupToItsDemandTimesTheMaster(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(E1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(CH, LeafiaBlocks.lwr_control.get());
		helper.setBlock(E2, LeafiaBlocks.lwr_element.get());
		helper.setBlock(P, LeafiaBlocks.lwr_signal.get());
		diagnose(helper, E1);
		PWRData data = core(helper, E1, CH, E2, P);
		PWRComputerTE computer = (PWRComputerTE) be(helper, P);
		PWRControlTE control = (PWRControlTE) be(helper, CH);
		helper.assertTrue(computer.getCoreByCorePos() == data, "the control port joined the reactor");
		near(helper, computer.getMasterControl(), 25, "the master starts at 25%");
		computer.setControl("nope", 50);
		helper.assertTrue(!data.controlDemand.containsKey("nope"), "a group no rod carries is ignored");
		helper.assertTrue(computer.getControl("nope") == null, "and reads null");
		computer.setControl(PWRControlTE.defaultName, 80);
		near(helper, control.targetPosition, 0.8 * 0.25, "the rod goes to demand x master");
		computer.setMasterControl(50);
		near(helper, control.targetPosition, 0.8 * 0.5, "every rod follows the master");
		computer.setControl(PWRControlTE.defaultName, 250);
		near(helper, computer.getControl(PWRControlTE.defaultName), 100, "a demand is clamped to 100%");
		// The query values, and upstream's getTankCapacities, which answers the fills.
		data.tanks[0].setFill(1234);
		var query = computer.queryValues();
		near(helper, query.get("tank_coolant"), 1234, "tank_coolant");
		near(helper, query.get("capacity_coolant"), data.tanks[0].getMaxFill(), "capacity_coolant is the capacity");
		helper.assertTrue(computer.getTankCapacities()[0] == data.tanks[0].getMaxFill() && computer.getTankFills()[0] == 1234, "getTankCapacities answers the capacity, getTankFills the fill (D-3076)");
		teardown(helper);
		helper.succeed();
	}

	// ---- the fuel columns ------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void aRodGoesInByHandRisesWithTheColumnAndComesOutWithAScrewdriver(GameTestHelper helper) {
		BlockPos low = new BlockPos(4, 2, 4);
		BlockPos high = low.above();
		helper.setBlock(low, LeafiaBlocks.lwr_element.get());
		ServerPlayer player = mockPlayer(helper);
		LeafiaRodItem rod = u238(helper);
		ItemStack fuel = rodAt(rod, 20);
		fuel.setCount(3);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a creative hand keeps its stack
		player.setItemInHand(InteractionHand.MAIN_HAND, fuel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(low)), Direction.UP, helper.absolutePos(low), false);
		helper.getBlockState(low).useItemOn(fuel, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(element(helper, low).inventory.getStackInSlot(0).is(rod) && element(helper, low).inventory.getStackInSlot(0).getCount() == 1, "one rod is in the column");
		helper.assertTrue(player.getMainHandItem().getCount() == 2, "and the other two stay in the hand (D-3076; upstream put the whole stack in)");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		helper.setBlock(high, LeafiaBlocks.lwr_element.get());
		helper.assertTrue(helper.getBlockState(low).getValue(PWRElementBlock.stacked), "the lower block is stacked");
		helper.assertTrue(be(helper, low) instanceof PWRProxyTE, "and is a proxy");
		helper.assertTrue(element(helper, high).inventory.getStackInSlot(0).is(rod), "the rod moved to the new top");
		IItemHandler viaProxy = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(low), Direction.NORTH);
		helper.assertTrue(viaProxy != null && viaProxy.getStackInSlot(0).is(rod), "the proxy's item face is the top's slot");
		helper.assertTrue(element(helper, high).getHeight() == 2, "a two-block column");

		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a creative hand does not wear tools
		ItemStack driver = new ItemStack(ModItems.screwdriver.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, driver);
		helper.getBlockState(low).useItemOn(driver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(element(helper, high).inventory.getStackInSlot(0).isEmpty(), "the screwdriver took it out");
		AABB around = new AABB(player.blockPosition()).inflate(2);
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(rod)).isEmpty(), "at the player's feet");
		helper.assertTrue(driver.getDamageValue() == 1, "for one point of wear");

		helper.setBlock(high, Blocks.AIR);
		helper.assertTrue(!helper.getBlockState(low).getValue(PWRElementBlock.stacked), "the lower block is the top again");
		helper.assertTrue(be(helper, low) instanceof PWRElementTE, "with a fuel slot of its own");

		helper.setBlock(high, LeafiaBlocks.reactor_element.get());
		helper.assertTrue(helper.getBlockState(low).isAir(), "a column of one kind under another breaks");
		helper.setBlock(high, Blocks.AIR);
		for(ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(low)).inflate(4))) item.discard();
		PWRDiagnosis.preventScan.clear();
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aTickDrivesTheRodWithItsFluxAndSpendsWhatItCools(GameTestHelper helper) {
		PWRData data = rigA(helper);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 2000));
		element(helper, E2).inventory.setStackInSlot(0, rodAt(rod, 100));
		data.tanks[0].setFill(6400);
		data.tanks[1].setFill(0);
		PWRElementTE e1 = element(helper, E1);
		e1.tick();
		CompoundTag tag = LeafiaRodItem.data(e1.inventory.getStackInSlot(0));
		near(helper, tag.getDouble("incoming"), flux(rod, 100, true), "the rod's incoming is the flux the routes carried");
		double cooled = tag.getDouble("cooled");
		helper.assertTrue(cooled > 0, "a hot rod beside two channels sheds heat: " + cooled);
		int spent = 6400 - data.tanks[0].getFill();
		helper.assertTrue(spent == (int) Math.floor(cooled * 12), "coolant spent is floor(cooled x 12): " + spent + " for " + cooled);
		helper.assertTrue(data.tanks[1].getFill() == spent, "and all of it is hot coolant now");
		teardown(helper);
		helper.succeed();
	}

	// ---- the coolant loop ------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void coolantSpentBecomesHotCoolantAndOverflowsIntoTheBufferAtNinetySixToOne(GameTestHelper helper) {
		PWRData data = rigA(helper);
		data.tanks[0].setFill(1000);
		data.tanks[1].setFill(0);
		data.spendCoolant(10.5, null); // 10.5 x 12 = 126
		helper.assertTrue(data.tanks[0].getFill() == 874 && data.tanks[1].getFill() == 126, "126 mB moved: " + data.tanks[0].getFill() + "/" + data.tanks[1].getFill());
		data.spendAccum = 0;
		data.spendCoolant(0.05, null); // 0.6: nothing yet
		helper.assertTrue(data.tanks[0].getFill() == 874, "a fraction waits");
		data.spendCoolant(0.05, null); // 1.2: one
		helper.assertTrue(data.tanks[0].getFill() == 873 && data.tanks[1].getFill() == 127, "the fractions add to a whole millibucket");
		near(helper, data.spendAccum, 0.2, "and keep the rest");

		data.tanks[0].setFill(1000);
		data.tanks[1].setFill(data.tanks[1].getMaxFill() - 10);
		data.tanks[2].setFill(0);
		data.spendCoolant(10.5, null);
		// 126: 10 fit; 116 boil at 96:1 into one buffer millibucket, 20 into the remainder.
		helper.assertTrue(data.tanks[2].getFill() == 1, "one buffer millibucket: " + data.tanks[2].getFill());
		helper.assertTrue(data.boilingAccum() == 20, "a remainder of 20: " + data.boilingAccum());
		for(int i = 0; i < 3; i++) data.spendCoolant(10.5, null);
		// Remainders 50, 80, 110; at 110 one unit pays out and 96 is taken off (D-3076; upstream took off 1, leaving 109).
		helper.assertTrue(data.tanks[2].getFill() == 5, "1 + 1 + 1 + 2: " + data.tanks[2].getFill());
		helper.assertTrue(data.boilingAccum() == 14, "the remainder keeps 14: " + data.boilingAccum());
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void aFullBufferBlowsTheVesselAtItsToughnessLevel(GameTestHelper helper) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(P, LeafiaBlocks.lwr_port.get());
		BlockEntity port = be(helper, P);
		int[] called = {-1};
		PWRData data = new PWRData(port) {
			@Override
			public void explode(net.minecraft.world.level.Level world, ItemStack prevStack, LeafiaRodItem doNuke, int forceLevel) {
				called[0] = forceLevel;
			}
		};
		data.resizeTanks(1, 0);
		data.tanks[0].setFill(1000);
		data.tanks[1].setFill(data.tanks[1].getMaxFill());
		data.tanks[2].setFill(data.tanks[2].getMaxFill() - 2);
		data.spendCoolant(10.5, null);
		// 126 boil: 96 into one buffer millibucket, 30 left over with the buffer one short of full.
		helper.assertTrue(called[0] == -1 && data.tanks[2].getFill() == data.tanks[2].getMaxFill() - 1, "a buffer with room takes the boil-off");
		data.tanks[2].setFill(data.tanks[2].getMaxFill());
		data.spendCoolant(10.5, null);
		helper.assertTrue(called[0] == 0, "a full buffer blows by toughness (force level 0): " + called[0]);
		helper.assertTrue(PWRData.meltdownLevel(17203, 0) == 3 && PWRData.meltdownLevel(15000, 0) == 3, "15,000 and up is level 3");
		helper.assertTrue(PWRData.meltdownLevel(14999, 0) == 2 && PWRData.meltdownLevel(10000, 0) == 2, "10,000 and up is level 2");
		helper.assertTrue(PWRData.meltdownLevel(9999, 0) == 1 && PWRData.meltdownLevel(0, 0) == 1, "below is level 1");
		helper.assertTrue(PWRData.meltdownLevel(17203, 1) == 1 && PWRData.meltdownLevel(0, 3) == 3 && PWRData.meltdownLevel(0, 4) == 0, "a forced level is taken as given");
		helper.setBlock(P, Blocks.AIR);
		box(helper, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theMeltdownEmptiesTheColumnsAndMarksTheirCorium(GameTestHelper helper) {
		PWRData data = rigA(helper);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 100));
		element(helper, E2).inventory.setStackInSlot(0, rodAt(rod, 100));
		PWRData.PWRExplosion boom = data.gatherMeltdown(helper.getLevel(), null);
		helper.assertTrue(element(helper, E1).inventory.getStackInSlot(0).isEmpty() && element(helper, E2).inventory.getStackInSlot(0).isEmpty(), "the rods are gone");
		helper.assertTrue(data.coriumTypes.get(helper.absolutePos(E1).asLong()) == rod.corium(), "E1 melts into its rod's corium");
		helper.assertTrue(data.coriumTypes.get(helper.absolutePos(E2).asLong()) == rod.corium(), "and E2");
		helper.assertTrue(data.coriumTypes.size() == 2, "one entry per column block: " + data.coriumTypes.size());
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		// The mean of the six members: x (5+6+7+6+6+6)/6 = 6, y (4x5 + 5)/6 = 25/6, z (5+5+5+4+6+5)/6 = 5.
		near(helper, boom.centerPoint.x, o.getX() + 6, "centre x");
		near(helper, boom.centerPoint.y, o.getY() + 25 / 6d, "centre y");
		near(helper, boom.centerPoint.z, o.getZ() + 5, "centre z");
		helper.assertTrue(boom.minX == o.getX() + 5 && boom.maxX == o.getX() + 7 && boom.minY == o.getY() + 4 && boom.maxY == o.getY() + 5, "the bounds");
		// Level 3 is PWR-2's: with a handler registered, it is that handler that runs.
		PWRData.Lv3Meltdown previous = PWRData.LV3_MELTDOWN;
		PWRData[] seen = {null};
		try {
			PWRData.LV3_MELTDOWN = (d, explosion) -> seen[0] = d;
			boom.explodeLv3();
		} finally {
			PWRData.LV3_MELTDOWN = previous;
		}
		helper.assertTrue(seen[0] == data && boom.ran == 3, "the level-3 hook got this reactor");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theBoilerTurnsSixteenWaterPerHotCoolantIntoSteamByCompression(GameTestHelper helper) {
		PWRData data = rigA(helper);
		data.tanks[0].setFill(0);
		data.tanks[1].setFill(3200);
		data.tanks[3].setFill(25600);
		data.tanks[4].setFill(0);
		data.boil();
		// round((3200 / 25600 x 4)^0.4 x 80 x 16) = 970 water; 60 hot coolant; 960 boil; 96,000 steam wanted,
		// 12,800 fit; that is 128 water and 8 hot coolant.
		helper.assertTrue(data.tanks[4].getFill() == 12800, "the steam tank fills: " + data.tanks[4].getFill());
		helper.assertTrue(data.tanks[3].getFill() == 25600 - 128, "128 water boiled: " + data.tanks[3].getFill());
		helper.assertTrue(data.tanks[1].getFill() == 3200 - 8 && data.tanks[0].getFill() == 8, "8 hot coolant back to coolant");

		data.compression = 1;
		data.onUpdateCompression();
		helper.assertTrue(data.tanks[4].getTankType() == Fluids.HOTSTEAM && data.tanks[4].getFill() == 0, "compression 1 is dense steam, and empties the tank");
		data.tanks[0].setFill(0);
		data.tanks[1].setFill(3200);
		data.tanks[3].setFill(25600);
		data.boil();
		// 960 water at 10 per 100: 9,600 dense steam, all of it fits; 60 hot coolant.
		helper.assertTrue(data.tanks[4].getFill() == 9600, "dense steam: " + data.tanks[4].getFill());
		helper.assertTrue(data.tanks[3].getFill() == 25600 - 960 && data.tanks[1].getFill() == 3200 - 60 && data.tanks[0].getFill() == 60, "960 water, 60 coolant");

		// D-3010: the terminal's request now needs a player with the terminal open, so the wrap mod 3 is
		// LeafiaPWR2Tests.theCoreTakesTheScreensRequestsOnlyThroughAnOpenTerminal's; here the result of it.
		data.compression = Math.floorMod(5, 3);
		data.onUpdateCompression();
		helper.assertTrue(data.compression == 2 && data.tanks[4].getTankType() == Fluids.SUPERHOTSTEAM, "compression 2 is superdense steam");
		data.coolantId = Fluids.WATER.getID();
		int water = data.tanks[3].getFill();
		data.boil();
		helper.assertTrue(data.tanks[3].getFill() == water, "the boiler only works with plain coolant");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theOCCSCoolsEveryColumnAndSortsItsWaterBySteam(GameTestHelper helper) {
		PWRData data = rigA(helper);
		LeafiaRodItem rod = u238(helper);
		element(helper, E1).inventory.setStackInSlot(0, rodAt(rod, 1020));
		element(helper, E2).inventory.setStackInSlot(0, rodAt(rod, 20.01));
		data.tanks[5].setFill(300);
		data.occs();
		near(helper, heat(element(helper, E1).inventory.getStackInSlot(0)), 1015, "(1020 - 20) x 0.995 + 20");
		near(helper, heat(element(helper, E2).inventory.getStackInSlot(0)), 20.00995, "a cool rod barely changes");
		helper.assertTrue(data.tanks[5].getFill() == 0, "150 mB per column");
		helper.assertTrue(data.tanks[7].getFill() == 150 && data.tanks[6].getFill() == 150, "one column's water left as spent steam, one's as water");
		helper.assertTrue(data.tanks[7].getTankType() == Fluids.SPENTSTEAM && data.tanks[6].getTankType() == Fluids.WATER, "steam and water");
		data.tanks[5].setFill(300);
		data.tanks[6].setFill(data.tanks[6].getMaxFill() - 149);
		data.occs();
		helper.assertTrue(data.tanks[5].getFill() == 300, "an outlet tank without 150 mB of room stops the OCCS");
		teardown(helper);
		helper.succeed();
	}

	/** D-3075: once the stress timer runs out and the sound plays, it is reset (upstream's stayed spent, so the sound played every tick). */
	@GameTest(template = BIG)
	public static void theStressTimerResetsAfterItsSound(GameTestHelper helper) {
		PWRData data = rigA(helper);
		data.tanks[2] = new FluidTank(LeafiaFluids.COOLANT_MAL, 3000);
		data.tanks[2].setFill(3000);
		data.tanks[1].setFill(0);
		data.stressTimer = 1;
		data.drainMalcoolant();
		helper.assertTrue(data.stressTimer == 300, "the timer was reset to 300, got " + data.stressTimer);
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theBufferDrainsIntoTheHotTankAndStressesTheVessel(GameTestHelper helper) {
		PWRData data = rigA(helper);
		data.tanks[2] = new FluidTank(LeafiaFluids.COOLANT_MAL, 3000);
		data.tanks[2].setFill(1500);
		data.tanks[1].setFill(0);
		data.stressTimer = 300;
		data.drainMalcoolant();
		// decr = min(1500, 3000 / round(15 x 20)) = 10, at four hot coolant each.
		helper.assertTrue(data.tanks[2].getFill() == 1490 && data.tanks[1].getFill() == 40, "10 out, 40 in");
		near(helper, data.stressTimer, 265.9090984654095, "300 - (1490/3000)^0.9 x 64");
		data.tanks[1].setFill(data.tanks[1].getMaxFill() - 39);
		data.drainMalcoolant();
		helper.assertTrue(data.tanks[2].getFill() == 1490, "no room for 40 hot coolant, nothing drains");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void identifyingPlainCoolantKeepsTheMalfunctioningBuffer(GameTestHelper helper) {
		PWRData data = rigA(helper);
		helper.assertTrue(data.tankTypes[0] == Fluids.COOLANT && data.tankTypes[1] == Fluids.COOLANT_HOT && data.tankTypes[2] == LeafiaFluids.COOLANT_MAL, "the defaults");
		near(helper, data.multiplier, 1, "coolant heats 1:1");
		helper.assertTrue(data.updateCoolantType(Fluids.COOLANT.getID()), "coolant is a valid coolant");
		// getBoiledCoolant answers (second-hottest, hottest, ratio); a one-step chain answers its product twice, and the buffer stays malfunctioning coolant (D-3076).
		helper.assertTrue(data.tankTypes[1] == Fluids.COOLANT_HOT && data.tankTypes[2] == LeafiaFluids.COOLANT_MAL, "the buffer stays malfunctioning coolant: " + data.tankTypes[2].getName());
		helper.assertTrue(!data.updateCoolantType(Fluids.WATER.getID()) || data.tankTypes[0] == Fluids.WATER, "water either is not a coolant or becomes one");
		helper.assertTrue(!data.updateCoolantType(Fluids.NONE.getID()), "nothing is not a coolant");
		teardown(helper);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theCoreKeepsItsStateAcrossASave(GameTestHelper helper) {
		rigA(helper);
		helper.setBlock(X, LeafiaBlocks.lwr_control.get()); // a rod, so a rod group's demand is kept
		diagnose(helper, E1);
		PWRData data = core(helper, E1, E2, P, X);
		data.compression = 2;
		data.onUpdateCompression();
		data.tanks[0].setFill(777);
		data.masterControl = 0.6;
		data.controlDemand.put(PWRControlTE.defaultName, 0.3);
		data.controlDemand.put("gone", 0.9);
		CompoundTag saved = data.writeToNBT(new CompoundTag(), helper.getLevel().registryAccess());
		PWRData loaded = new PWRData(data.companion).readFromNBT(saved, helper.getLevel().registryAccess());
		helper.assertTrue(loaded.compression == 2 && loaded.tanks[4].getTankType() == Fluids.SUPERHOTSTEAM, "compression");
		helper.assertTrue(loaded.tanks[0].getFill() == 777 && loaded.tanks[0].getMaxFill() == data.tanks[0].getMaxFill(), "a tank");
		near(helper, loaded.masterControl, 0.6, "the master");
		helper.assertTrue(loaded.controlDemand.size() == 1 && loaded.controlDemand.containsKey(PWRControlTE.defaultName), "only groups a rod still carries are saved: " + loaded.controlDemand);
		helper.assertTrue(loaded.projection.equals(data.projection), "the terminal's grid");
		teardown(helper);
		helper.succeed();
	}

	// ---- the fluid ports -------------------------------------------------------------------

	@GameTest(template = BIG)
	public static void thePortsAnswerForTheCoresTanks(GameTestHelper helper) {
		PWRData data = rigA(helper);
		PWRPortTE port = (PWRPortTE) be(helper, P);
		helper.assertTrue(port.getReceivingTanks().length == 2 && port.getReceivingTanks()[0] == data.tanks[0] && port.getReceivingTanks()[1] == data.tanks[3], "coolant and water in");
		helper.assertTrue(port.getSendingTanks().length == 2 && port.getSendingTanks()[0] == data.tanks[1] && port.getSendingTanks()[1] == data.tanks[4], "hot coolant and steam out");
		helper.setBlock(new BlockPos(5, 5, 5), LeafiaBlocks.lwr_occs_in.get());
		helper.setBlock(new BlockPos(7, 5, 5), LeafiaBlocks.lwr_occs_out.get());
		diagnose(helper, E1);
		data = core(helper, E1, E2, P, new BlockPos(5, 5, 5), new BlockPos(7, 5, 5));
		var in = (com.leafia.contents.machines.reactors.pwr.blocks.components.occs.inlet.PWROCCSInTE) be(helper, new BlockPos(5, 5, 5));
		var out = (com.leafia.contents.machines.reactors.pwr.blocks.components.occs.outlet.PWROCCSOutTE) be(helper, new BlockPos(7, 5, 5));
		helper.assertTrue(in.getReceivingTanks().length == 1 && in.getReceivingTanks()[0] == data.tanks[5] && in.getSendingTanks().length == 0, "the inlet takes OCCS water");
		helper.assertTrue(out.getSendingTanks().length == 2 && out.getSendingTanks()[0] == data.tanks[7] && out.getSendingTanks()[1] == data.tanks[6] && out.getReceivingTanks().length == 0, "the outlet gives spent steam, then water");
		teardown(helper);
		helper.succeed();
	}

	// ---- registration and assets -----------------------------------------------------------

	@GameTest(template = BIG)
	public static void theBlockEntityTypesNameTheirBlocks(GameTestHelper helper) {
		for(Block block : new Block[] {LeafiaBlocks.lwr_element.get(), LeafiaBlocks.reactor_element.get(), LeafiaBlocks.reactor_element_blank.get()}) {
			helper.assertTrue(LeafiaBlockEntities.PWR_ELEMENT.get().isValid(block.defaultBlockState()), "element type on " + block.getDescriptionId());
			helper.assertTrue(LeafiaBlockEntities.PWR_ELEMENT_PROXY.get().isValid(block.defaultBlockState()), "proxy type on " + block.getDescriptionId());
		}
		helper.assertTrue(LeafiaBlockEntities.PWR_CONTROL.get().isValid(LeafiaBlocks.reactor_control.get().defaultBlockState()), "control type on the old rod");
		helper.assertTrue(PWRAssignableEntity.class.isAssignableFrom(PWRPortTE.class) && PWRAssignableEntity.class.isAssignableFrom(PWRComputerTE.class), "the port and the computer can hold the core");
		helper.succeed();
	}

	private static JsonObject json(String path) throws IOException {
		try(InputStream in = LeafiaPWRTests.class.getResourceAsStream(path)) {
			if(in == null) return null;
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@GameTest(template = BIG)
	public static void theControlRodPartsAreOnePartEachOnUpstreamsTextures(GameTestHelper helper) throws IOException {
		List<String> missing = new ArrayList<>();
		for(String mesh : new String[] {"final", "classic"}) {
			for(String part : PWRControlBlock.PARTS) {
				JsonObject model = json("/assets/leafia/models/" + PWRControlBlock.partModel(mesh, part) + ".json");
				if(model == null) {
					missing.add(mesh + "/" + part);
					continue;
				}
				JsonObject vis = model.getAsJsonObject("visibility");
				for(String other : PWRControlBlock.PARTS)
					helper.assertTrue(vis.get(other).getAsBoolean() == other.equals(part), mesh + "/" + part + " shows " + other + "? " + vis.get(other));
				String tex = model.getAsJsonObject("textures").get("0").getAsString();
				boolean side = part.endsWith("Side") || (mesh.equals("classic") && part.equals("RodsEnd"));
				String want = mesh.equals("final") ? (side ? "leafia:block/pwr/pwr_control_side" : "leafia:block/pwr/pwr_control_top")
						: (side ? "leafia:block/reactor_control_side" : "leafia:block/reactor_control_top");
				helper.assertTrue(tex.equals(want), mesh + "/" + part + " draws " + tex + ", upstream binds " + want);
			}
		}
		helper.assertTrue(missing.isEmpty(), "missing parts: " + missing);
		for(String obj : new String[] {"pwr_control_final", "pwr_control_classic"})
			helper.assertTrue(LeafiaPWRTests.class.getResource("/assets/leafia/models/blocks/" + obj + ".obj") != null, obj + ".obj is shipped");
		helper.succeed();
	}
}
