package com.leafia.gametest;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.blockentity.IRepairable.EnumExtinguishType;
import com.hbm.blockentity.machine.BlockEntityChungus;
import com.hbm.blockentity.machine.BlockEntityCondenser;
import com.hbm.blockentity.machine.BlockEntityHeatBoiler;
import com.hbm.blockentity.machine.BlockEntityHeatBoilerIndustrial;
import com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant;
import com.hbm.blockentity.machine.BlockEntityMachineIndustrialTurbine;
import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blockentity.machine.storage.BlockEntityMachineBigAssTank;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.DirPos;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.gear.utility.fuzzy.FuzzyAdapters;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.gear.utility.fuzzy.IFuzzyCompatible;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.network.pipe_amat.uninos.AmatNet;
import com.leafia.contents.network.pipe_amat.uninos.AmatNode;
import com.leafia.dev.firestorm.IFirestormBlock;
import com.leafia.dev.firestorm.IFirestormTE;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaFirestorm;
import com.leafia.overwrite_contents.LeafiaFluidSeams;
import com.leafia.unsorted.fluids.FluidIconOverlays;

import api.hbm.fluidmk2.FluidNetMK2;
import api.hbm.fluidmk2.IFluidReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fluid-network, barrel, fluid-icon, boiler, fuzzy-identifier and firestorm hooks (FLUID-FIRESTORM-FUZZY, D-3025):
 * the addon's consumers of the base mod's M5/M9 seams, each beside the unhooked default it must leave alone.
 *
 * <p>Every switch written is restored in a {@code finally}, and every hook is driven by calling the tick by hand in
 * one synchronous body, so no other test in the batch ever meets a flipped switch (D-952). The firestorm dice are
 * rolled with seeded or counting {@link RandomSource}s, or in loops that end at the first success with a bound at which
 * failure is not a possibility worth the name - never inside a random band (D-3004).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaFluidFirestormTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";
	private static final BlockPos CORE = new BlockPos(8, 1, 8);

	// ------------------------------------------------------------------ helpers

	/** A provider and a receiver standing in for a machine: it asks for 1,000 mB and takes what it is sent. */
	private static class FakeUser implements IFluidReceiverMK2, IFluidStandardSenderMK2 {
		long got = 0;
		final FluidTank tank = new FluidTank(Fluids.WATER, 1000);
		FakeUser() { tank.setFill(1000); }
		@Override public long transferFluid(FluidType type, int pressure, long amount) { got += amount; return 0; }
		@Override public long getDemand(FluidType type, int pressure) { return 1000 - got; }
		@Override public FluidTank[] getSendingTanks() { return new FluidTank[] { tank }; }
		@Override public FluidTank[] getAllTanks() { return new FluidTank[] { tank }; }
		@Override public boolean isLoaded() { return true; }
	}

	/** An amat node is a {@code GenNode<AmatNet>}, not the {@code FluidNode} the net's type parameters name; the nodespace joins it raw. */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void join(AmatNet net, AmatNode node) {
		((com.hbm.uninos.NodeNet) net).joinLink(node);
	}

	/** A fresh plant core (the old one, flags and all, is cleared first); no body round it. */
	private static BlockEntityMachineChemicalPlant plant(GameTestHelper helper) {
		helper.setBlock(CORE, Blocks.AIR.defaultBlockState());
		helper.setBlock(CORE, ModBlocks.machine_chemical_plant.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		return (BlockEntityMachineChemicalPlant) helper.getBlockEntity(CORE);
	}

	/** A plant with its 3x3x3 body filled in, so that fire next to any part of it finds the core. */
	private static BlockEntityMachineChemicalPlant bodiedPlant(GameTestHelper helper) {
		BlockEntityMachineChemicalPlant plant = plant(helper);
		var block = ModBlocks.machine_chemical_plant.get();
		MultiblockHandlerXR.fillSpace(helper.getLevel(), helper.absolutePos(CORE), block.getDimensions(), block, Direction.NORTH);
		return plant;
	}

	private static BlockEntityBarrel barrel(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, ModBlocks.barrel_plastic.get().defaultBlockState());
		return (BlockEntityBarrel) helper.getBlockEntity(pos);
	}

	private static Player player(GameTestHelper helper) {
		return helper.makeMockPlayer(GameType.SURVIVAL);
	}

	/** Always answers the bound minus one, and remembers the bounds it was asked for: a die that never rolls a zero. */
	private static class Counting extends net.minecraft.world.level.levelgen.LegacyRandomSource {
		final List<Integer> bounds = new ArrayList<>();
		Counting() { super(1L); }
		@Override public int nextInt(int bound) {
			bounds.add(bound);
			return bound - 1;
		}
	}

	/** Every explosion started while it is open: the base's event, which is posted before any block is touched. */
	private static final class Blasts {
		final List<Explosion> seen = new ArrayList<>();
		private final Consumer<ExplosionEvent.Start> listener = event -> seen.add(event.getExplosion());

		Blasts() {
			NeoForge.EVENT_BUS.addListener(ExplosionEvent.Start.class, listener);
		}

		void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
	}

	/** Whether the explosion sets fires: a private field of vanilla's {@code Explosion}. */
	private static boolean isFire(Explosion explosion) {
		try {
			var field = Explosion.class.getDeclaredField("fire");
			field.setAccessible(true);
			return field.getBoolean(explosion);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("Explosion.fire is not where the test expects it", e);
		}
	}

	private static Method reflect(String name, Class<?>... args) {
		try {
			Method method = FireBlock.class.getDeclaredMethod(name, args);
			method.setAccessible(true);
			return method;
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException("FireBlock." + name + " is not where the mixin expects it", e);
		}
	}

	private static JsonObject json(String path) {
		try(InputStream in = LeafiaFluidFirestormTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null) throw new IllegalStateException(path + " is missing from the jar");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch(java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean exists(String path) {
		try(InputStream in = LeafiaFluidFirestormTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			return in != null;
		} catch(java.io.IOException e) {
			return false;
		}
	}

	// ------------------------------------------------------------------ the switches

	/** The five new switches: four overhauls default off, upstream's side-port setting defaults on. */
	@GameTest(template = EMPTY)
	public static void theSwitchesDefaultAsDecided(GameTestHelper helper) {
		helper.assertTrue(!LeafiaFeatures.antimatterContainment, "antimatter containment is an overhaul: off (D-1002)");
		helper.assertTrue(!LeafiaFeatures.barrelPorts, "barrel ports are an overhaul: off (D-1002)");
		helper.assertTrue(LeafiaFeatures.barrelSidePorts, "upstream's enableBarrelSidePorts defaults on");
		helper.assertTrue(!LeafiaFeatures.industrialBoilerTanks, "the x5 boiler tanks are a rebalance: off (D-1000)");
		helper.assertTrue(!LeafiaFeatures.firestorm, "firestorm is upstream's development-only switch: off");
		helper.succeed();
	}

	/** Upstream's three numbers that no behaviour test can read back: the blast, the tank factor and the fire radius. */
	@GameTest(template = EMPTY)
	public static void upstreamsThreeConstants(GameTestHelper helper) {
		helper.assertTrue(LeafiaFluidSeams.ANTIMATTER_POWER == 5F, "the antimatter blast is power 5, got " + LeafiaFluidSeams.ANTIMATTER_POWER);
		helper.assertTrue(LeafiaFluidSeams.BOILER_TANK_FACTOR == 5, "the boiler's tanks are x5, got " + LeafiaFluidSeams.BOILER_TANK_FACTOR);
		helper.assertTrue(LeafiaFirestorm.FIRE_RADIUS == 4, "a wreck spreads fire in a radius of 4, got " + LeafiaFirestorm.FIRE_RADIUS);
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinFluidNetMK2

	/**
	 * A provider of antimatter on a plain network, with another receiver on it, blows up where it stands and is
	 * dropped; a lone transceiver is not, nor is any other fluid; off, nothing happens.
	 */
	@GameTest(template = BIG)
	public static void anAntimatterProviderOnAPlainNetExplodesWhenAnythingElseListens(GameTestHelper helper) {

		boolean was = LeafiaFeatures.antimatterContainment;
		BlockPos spot = new BlockPos(4, 2, 4);
		Blasts blasts = new Blasts();
		try {
			// off: the net keeps its provider and nothing blows up
			LeafiaFeatures.antimatterContainment = false;
			BlockEntityBarrel quiet = barrel(helper, spot);
			FluidNetMK2 off = new FluidNetMK2(Fluids.AMAT);
			off.addProvider(quiet);
			off.addReceiver(new FakeUser());
			off.update();
			helper.assertTrue(off.isProvider(quiet) && blasts.seen.isEmpty(), "switched off, the provider stays and nothing explodes");

			LeafiaFeatures.antimatterContainment = true;

			// another fluid on the same net is not antimatter
			FluidNetMK2 water = new FluidNetMK2(Fluids.WATER);
			water.addProvider(quiet);
			water.addReceiver(new FakeUser());
			water.update();
			helper.assertTrue(water.isProvider(quiet) && blasts.seen.isEmpty(), "water is not antimatter");

			// the provider is also the only receiver: connected to nothing but itself
			FluidNetMK2 lone = new FluidNetMK2(Fluids.AMAT);
			lone.addProvider(quiet);
			lone.addReceiver(quiet);
			lone.update();
			helper.assertTrue(lone.isProvider(quiet) && blasts.seen.isEmpty(), "a lone transceiver is connected to nothing else, so it is safe");

			// connected to a second receiver: the explosion
			FluidNetMK2 net = new FluidNetMK2(Fluids.AMAT);
			FakeUser to = new FakeUser();
			net.addProvider(quiet);
			net.addReceiver(to);
			net.update();
			helper.assertTrue(!net.isProvider(quiet), "the provider was dropped from the net");
			helper.assertTrue(to.got == 0, "and nothing moved, moved " + to.got);
			helper.assertTrue(blasts.seen.size() == 1, "and one blast, got " + blasts.seen.size());
			Explosion blast = blasts.seen.get(0);
			BlockPos abs = helper.absolutePos(spot);
			helper.assertTrue(blast.radius() == 5F, "of power 5, got " + blast.radius());
			helper.assertTrue(blast.center().equals(new Vec3(abs.getX(), abs.getY(), abs.getZ())), "at the provider's position, got " + blast.center());
			helper.assertTrue(blast.getBlockInteraction() != Explosion.BlockInteraction.KEEP, "that breaks blocks");
			helper.assertTrue(isFire(blast), "and sets fires");
		} finally {
			blasts.close();
			LeafiaFeatures.antimatterContainment = was;
		}
		helper.succeed();
	}

	/**
	 * The antimatter pipe's own network never asks the plain network's check: its provider is dropped by its own rule
	 * (D-1022, upstream's explosion is commented out) and nothing blows up.
	 */
	@GameTest(template = BIG)
	public static void theAntimatterPipesOwnNetworkDoesNotExplode(GameTestHelper helper) {

		boolean was = LeafiaFeatures.antimatterContainment;
		BlockPos spot = new BlockPos(4, 2, 4);
		Blasts blasts = new Blasts();
		try {
			LeafiaFeatures.antimatterContainment = true;
			BlockEntityBarrel provider = barrel(helper, spot);
			AmatNet net = new AmatNet(Fluids.AMAT);
			net.addProvider(provider);
			net.addReceiver(new FakeUser());
			net.update();
			helper.assertTrue(!net.isProvider(provider), "the amat net dropped the unmagnetised antimatter provider by its own rule");
			helper.assertTrue(blasts.seen.isEmpty(), "without the plain network's explosion, got " + blasts.seen.size());
		} finally {
			blasts.close();
			LeafiaFeatures.antimatterContainment = was;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinIFluidReceiverMK2, MixinIFluidStandardSenderMK2

	/**
	 * A machine subscribing to a neighbour that holds a live antimatter-network node joins that network too, for any
	 * fluid, and a sender provides to it; with the antimatter pipes off it does not.
	 */
	@GameTest(template = EMPTY)
	public static void aMachineNextToAnAmatNodeJoinsTheAmatNetworkForAnyFluid(GameTestHelper helper) {

		boolean was = LeafiaFeatures.amatPipes;
		BlockPos where = new BlockPos(1, 1, 1);
		helper.setBlock(where, ModBlocks.barrel_plastic.get().defaultBlockState()); // a connector to subscribe at
		BlockPos abs = helper.absolutePos(where);

		AmatNode node = new AmatNode(AmatNet.getProvider(Fluids.WATER), abs);
		UniNodespace.createNode(helper.getLevel(), node);
		AmatNet net = new AmatNet(Fluids.WATER);
		join(net, node);
		try {
			FakeUser receiver = new FakeUser(), sender = new FakeUser(), oil = new FakeUser();
			LeafiaFeatures.amatPipes = false;
			receiver.trySubscribe(Fluids.WATER, helper.getLevel(), abs, Direction.UP);
			helper.assertTrue(!net.isSubscribed(receiver), "with the pipes off the amat net is not joined");

			LeafiaFeatures.amatPipes = true;
			receiver.trySubscribe(Fluids.WATER, helper.getLevel(), abs, Direction.UP);
			sender.tryProvide(Fluids.WATER, 0, helper.getLevel(), abs, Direction.UP);
			oil.trySubscribe(Fluids.OIL, helper.getLevel(), abs, Direction.UP);
			helper.assertTrue(net.isSubscribed(receiver), "a receiver joins the amat net of its fluid");
			helper.assertTrue(net.isProvider(sender), "and a sender provides to it");
			helper.assertTrue(!net.isSubscribed(oil), "but a node of another fluid's amat net is not the water's");
		} finally {
			LeafiaFeatures.amatPipes = was;
			UniNodespace.destroyNode(helper.getLevel(), node);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinTileEntityBarrel, MixinTileEntityMachineFluidTank

	/**
	 * A barrel in buffer mode is a node of its fluid's antimatter network, on it as provider and receiver; in any
	 * other mode the node is gone and the barrel is on a neighbour's amat net as a receiver only while receiving
	 * and a provider only while providing; removing the barrel takes the node away.
	 */
	@GameTest(template = BIG)
	public static void aBarrelInBufferModeIsANodeOfTheAmatNetwork(GameTestHelper helper) {

		boolean was = LeafiaFeatures.amatPipes;
		BlockPos spot = new BlockPos(4, 2, 4), next = new BlockPos(4, 3, 4);
		BlockEntityBarrel barrel = barrel(helper, spot);
		barrel(helper, next); // a connector for the base's own subscribe and provide to find
		BlockPos abs = helper.absolutePos(spot);
		var provider = AmatNet.getProvider(Fluids.AMAT);
		AmatNode neighbour = new AmatNode(provider, helper.absolutePos(next));
		try {
			LeafiaFeatures.amatPipes = false;
			barrel.tank.setTankType(Fluids.AMAT);
			barrel.mode = 1;
			barrel.updateEntity();
			helper.assertTrue(LeafiaFluidSeams.bufferNode(barrel) == null && UniNodespace.getNode(helper.getLevel(), abs, provider) == null,
					"with the pipes off a buffer-mode barrel makes no amat node");

			LeafiaFeatures.amatPipes = true;
			barrel.updateEntity();
			AmatNode node = LeafiaFluidSeams.bufferNode(barrel);
			helper.assertTrue(node != null && UniNodespace.getNode(helper.getLevel(), abs, provider) == node,
					"in buffer mode the barrel is an amat node on its own position");
			helper.assertTrue(node.connections.length == 6, "with the barrel's six ports as its connections, got " + node.connections.length);

			AmatNet net = new AmatNet(Fluids.AMAT);
			join(net, node);
			barrel.updateEntity();
			helper.assertTrue(net.isProvider(barrel) && net.isSubscribed(barrel), "and on its net as provider and receiver");

			// leaving buffer mode: the node goes, and a neighbour's net is told what the barrel now is
			UniNodespace.createNode(helper.getLevel(), neighbour);
			AmatNet other = new AmatNet(Fluids.AMAT);
			join(other, neighbour);
			other.addProvider(barrel);
			barrel.mode = 0;
			barrel.updateEntity();
			helper.assertTrue(LeafiaFluidSeams.bufferNode(barrel) == null && UniNodespace.getNode(helper.getLevel(), abs, provider) == null,
					"in any other mode the barrel's own amat node is destroyed");
			helper.assertTrue(other.isSubscribed(barrel) && !other.isProvider(barrel), "receiving: a receiver of the neighbour's net, not a provider");
			barrel.mode = 3;
			barrel.updateEntity();
			helper.assertTrue(!other.isSubscribed(barrel) && !other.isProvider(barrel), "off: neither");
			barrel.mode = 2;
			barrel.updateEntity();
			helper.assertTrue(other.isProvider(barrel) && !other.isSubscribed(barrel), "providing: a provider of it (by the base's own call through the extra network), not a receiver");

			// removal
			barrel.mode = 1;
			barrel.updateEntity();
			helper.assertTrue(LeafiaFluidSeams.bufferNode(barrel) != null, "back in buffer mode it has a node again");
			helper.setBlock(spot, Blocks.AIR.defaultBlockState());
			helper.assertTrue(UniNodespace.getNode(helper.getLevel(), abs, provider) == null, "and breaking the barrel takes its node away");
		} finally {
			LeafiaFeatures.amatPipes = was;
			UniNodespace.destroyNode(helper.getLevel(), neighbour);
			UniNodespace.destroyNode(helper.getLevel(), abs, provider);
		}
		helper.succeed();
	}

	/** The fluid tank's mode hook is the barrel's: buffer mode is an amat node, another mode destroys it. */
	@GameTest(template = BIG)
	public static void aFluidTankInBufferModeIsANodeOfTheAmatNetwork(GameTestHelper helper) {

		boolean was = LeafiaFeatures.amatPipes;
		BlockPos spot = new BlockPos(8, 1, 8);
		helper.setBlock(spot, ModBlocks.machine_fluidtank.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
		BlockEntityMachineFluidTank tank = (BlockEntityMachineFluidTank) helper.getBlockEntity(spot);
		BlockPos abs = helper.absolutePos(spot);
		var provider = AmatNet.getProvider(Fluids.AMAT);
		try {
			LeafiaFeatures.amatPipes = true;
			tank.tank.setTankType(Fluids.AMAT);
			tank.mode = 1;
			tank.updateEntity();
			AmatNode node = LeafiaFluidSeams.bufferNode(tank);
			helper.assertTrue(node != null && UniNodespace.getNode(helper.getLevel(), abs, provider) == node, "a tank in buffer mode is an amat node");
			helper.assertTrue(node.connections.length == tank.getPorts().length, "on the tank's own ports, got " + node.connections.length);

			AmatNet net = new AmatNet(Fluids.AMAT);
			join(net, node);
			tank.updateEntity();
			helper.assertTrue(net.isProvider(tank) && net.isSubscribed(tank), "as provider and receiver");

			tank.mode = 3;
			tank.updateEntity();
			helper.assertTrue(LeafiaFluidSeams.bufferNode(tank) == null && UniNodespace.getNode(helper.getLevel(), abs, provider) == null,
					"in another mode the node is destroyed");
		} finally {
			LeafiaFeatures.amatPipes = was;
			UniNodespace.destroyNode(helper.getLevel(), abs, provider);
		}
		helper.succeed();
	}

	/**
	 * The port filter: off, a barrel is the base mod's (six ports, every side). On with side ports, the six ports in
	 * upstream's order but connectable only from above and below; on without them, only the top and bottom ports and
	 * the base's {@code canConnect}. The big tank is not a barrel and is never touched.
	 */
	@GameTest(template = BIG)
	public static void theBarrelPortsAreUpstreamsWhenTheOverhaulIsOn(GameTestHelper helper) {

		boolean ports = LeafiaFeatures.barrelPorts, sides = LeafiaFeatures.barrelSidePorts;
		BlockEntityBarrel barrel = barrel(helper, new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(8, 1, 8), ModBlocks.machine_bigasstank.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
		BlockEntityMachineBigAssTank big = (BlockEntityMachineBigAssTank) helper.getBlockEntity(new BlockPos(8, 1, 8));
		try {
			LeafiaFeatures.barrelPorts = false;
			int bigPorts = big.getPorts().length;
			helper.assertTrue(barrel.getPorts().length == 6 && barrel.canConnect(Fluids.WATER, Direction.NORTH), "off: the base barrel, six ports and every side");

			LeafiaFeatures.barrelPorts = true;
			LeafiaFeatures.barrelSidePorts = true;
			DirPos[] six = barrel.getPorts();
			Direction[] order = { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH };
			helper.assertTrue(six.length == 6, "side ports on: six ports");
			for(int i = 0; i < 6; i++) helper.assertTrue(six[i].getDir() == order[i], "in upstream's order +X -X +Y -Y +Z -Z, port " + i + " faces " + six[i].getDir());
			helper.assertTrue(!barrel.canConnect(Fluids.WATER, Direction.NORTH) && !barrel.canConnect(Fluids.WATER, Direction.EAST),
					"but a neighbour may not connect from the sides");
			helper.assertTrue(barrel.canConnect(Fluids.WATER, Direction.UP) && barrel.canConnect(Fluids.WATER, Direction.DOWN), "only from above and below");

			LeafiaFeatures.barrelSidePorts = false;
			DirPos[] two = barrel.getPorts();
			helper.assertTrue(two.length == 2 && two[0].getDir() == Direction.UP && two[1].getDir() == Direction.DOWN, "side ports off: top and bottom only");
			helper.assertTrue(barrel.canConnect(Fluids.WATER, Direction.NORTH), "and the base's canConnect answers");

			helper.assertTrue(big.getPorts().length == bigPorts, "the big tank keeps its own ports");
		} finally {
			LeafiaFeatures.barrelPorts = ports;
			LeafiaFeatures.barrelSidePorts = sides;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinTileEntityHeatBoilerIndustrial

	/** A new industrial boiler has upstream's x5 tanks only with the rebalance on; a saved one keeps its size. */
	@GameTest(template = EMPTY)
	public static void theIndustrialBoilersTanksAreFiveTimesLargerOnlyWhenTheRebalanceIsOn(GameTestHelper helper) {

		boolean was = LeafiaFeatures.industrialBoilerTanks;
		BlockState state = ModBlocks.machine_industrial_boiler.get().defaultBlockState();
		try {
			LeafiaFeatures.industrialBoilerTanks = false;
			BlockEntityHeatBoilerIndustrial plain = new BlockEntityHeatBoilerIndustrial(BlockPos.ZERO, state);
			helper.assertTrue(plain.tanks[0].getMaxFill() == 64_000 && plain.tanks[1].getMaxFill() == 6_400_000,
					"off: the base mod's tanks, got " + plain.tanks[0].getMaxFill() + "/" + plain.tanks[1].getMaxFill());

			LeafiaFeatures.industrialBoilerTanks = true;
			BlockEntityHeatBoilerIndustrial big = new BlockEntityHeatBoilerIndustrial(BlockPos.ZERO, state);
			helper.assertTrue(big.tanks[0].getMaxFill() == 64_000 * 5 && big.tanks[1].getMaxFill() == 64_000 * 5 * 100,
					"on: 64,000*5 water and 64,000*5*100 steam, got " + big.tanks[0].getMaxFill() + "/" + big.tanks[1].getMaxFill());
			helper.assertTrue(big.tanks[0].getTankType() == Fluids.WATER && big.tanks[1].getTankType() == Fluids.STEAM, "still water in and steam out");
			helper.assertTrue(plain.tanks[0].getMaxFill() == 64_000, "and the boiler built before is unchanged");
		} finally {
			LeafiaFeatures.industrialBoilerTanks = was;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinItemFluidIcon

	/** The two enriched UF6s wear overlays; nothing else does; the models and sprites are in the jar. */
	@GameTest(template = EMPTY)
	public static void theFluidIconOverlaysAreUpstreamsTwo(GameTestHelper helper) {

		helper.assertTrue(FluidIconOverlays.overlays.equals(List.of("isotope_lime", "isotope_orange")), "the two overlays in upstream's order, got " + FluidIconOverlays.overlays);
		helper.assertTrue("isotope_orange".equals(FluidIconOverlays.getOverlay(LeafiaFluids.UF6_233)), "U-233 hexafluoride is orange");
		helper.assertTrue("isotope_lime".equals(FluidIconOverlays.getOverlay(LeafiaFluids.UF6_235)), "U-235 hexafluoride is lime");
		helper.assertTrue(FluidIconOverlays.getOverlay(Fluids.UF6) == null && FluidIconOverlays.getOverlay(Fluids.WATER) == null, "the base UF6 and water are plain");

		ResourceLocation orange = ItemFluidIcon.overlayFor(LeafiaFluids.UF6_233), lime = ItemFluidIcon.overlayFor(LeafiaFluids.UF6_235);
		helper.assertTrue(LeafiaRef.rl("item/fluid_icon_isotope_orange").equals(orange), "the base icon asks for U-233's model, got " + orange);
		helper.assertTrue(LeafiaRef.rl("item/fluid_icon_isotope_lime").equals(lime), "and U-235's, got " + lime);
		helper.assertTrue(ItemFluidIcon.overlayFor(Fluids.WATER) == null, "water's icon is the plain one");

		for(String overlay : FluidIconOverlays.overlays) {
			JsonObject model = json("models/item/fluid_icon_" + overlay + ".json");
			helper.assertTrue("minecraft:item/generated".equals(model.get("parent").getAsString()), overlay + " is a flat item model");
			JsonObject textures = model.getAsJsonObject("textures");
			helper.assertTrue("hbm:item/fluid_icon".equals(textures.get("layer0").getAsString()), overlay + " layer 0 is the base icon, which the fluid colour tints");
			String layer1 = textures.get("layer1").getAsString();
			helper.assertTrue(layer1.equals("leafia:" + FluidIconOverlays.prefix + overlay), overlay + " layer 1 is the sprite upstream names, got " + layer1);
			helper.assertTrue(exists("textures/" + FluidIconOverlays.prefix + overlay + ".png"), overlay + "'s sprite is in the jar");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the four fuzzy mixins

	/** The identifier reads upstream's fluid off each of the four machines, and nothing off a machine it was not given. */
	@GameTest(template = EMPTY)
	public static void theFuzzyIdentifierReadsTheFourBaseMachines(GameTestHelper helper) {

		BlockEntityCondenser condenser = new BlockEntityCondenser(BlockPos.ZERO, ModBlocks.machine_condenser.get().defaultBlockState());
		helper.assertTrue(FuzzyAdapters.outputOf(condenser) == Fluids.WATER, "a condenser makes water");

		BlockEntityHeatBoiler boiler = new BlockEntityHeatBoiler(BlockPos.ZERO, ModBlocks.machine_boiler.get().defaultBlockState());
		boiler.tanks[1].setTankType(Fluids.HOTSTEAM);
		helper.assertTrue(FuzzyAdapters.outputOf(boiler) == Fluids.HOTSTEAM, "a boiler puts out whatever its second tank holds");
		boiler.tanks[1].setTankType(Fluids.STEAM);
		helper.assertTrue(FuzzyAdapters.outputOf(boiler) == Fluids.STEAM, "and follows it");

		BlockEntityHeatBoilerIndustrial industrial = new BlockEntityHeatBoilerIndustrial(BlockPos.ZERO, ModBlocks.machine_industrial_boiler.get().defaultBlockState());
		industrial.tanks[1].setTankType(Fluids.SUPERHOTSTEAM);
		helper.assertTrue(FuzzyAdapters.outputOf(industrial) == Fluids.SUPERHOTSTEAM, "so does the industrial boiler");

		BlockEntityMachineIndustrialTurbine turbine = new BlockEntityMachineIndustrialTurbine(BlockPos.ZERO, ModBlocks.machine_industrial_turbine.get().defaultBlockState());
		turbine.tanks[1].setTankType(Fluids.OIL);
		helper.assertTrue(FuzzyAdapters.outputOf(turbine) == Fluids.OIL, "a turbine puts out its second tank's fluid");
		BlockEntityChungus chungus = new BlockEntityChungus(BlockPos.ZERO, ModBlocks.machine_chungus.get().defaultBlockState());
		chungus.tanks[1].setTankType(Fluids.LAVA);
		helper.assertTrue(FuzzyAdapters.outputOf(chungus) == Fluids.LAVA, "and so does the Chungus, on the same base");

		BlockEntity barrel = barrel(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(FuzzyAdapters.outputOf(barrel) == null && FuzzyAdapters.outputOf(null) == null, "a barrel and nothing are not read this way");

		class Own extends BlockEntityCondenser implements IFuzzyCompatible {
			Own() { super(BlockPos.ZERO, ModBlocks.machine_condenser.get().defaultBlockState()); }
			@Override public FluidType getOutputType() { return Fluids.LAVA; }
		}
		helper.assertTrue(FuzzyAdapters.outputOf(new Own()) == Fluids.LAVA, "a machine that implements the interface itself answers for itself");
		helper.succeed();
	}

	/** Through the item: sneak-clicking a boiler sets the tool to the fluid of its second tank. */
	@GameTest(template = EMPTY)
	public static void sneakClickingABoilerSetsTheToolToItsSteam(GameTestHelper helper) {

		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos, ModBlocks.machine_boiler.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
		BlockEntityHeatBoiler boiler = (BlockEntityHeatBoiler) helper.getBlockEntity(pos);
		boiler.tanks[1].setTankType(Fluids.HOTSTEAM);

		ItemStack stack = new ItemStack(LeafiaItems.fuzzy_identifier.get());
		Player player = player(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.setShiftKeyDown(true);
		BlockPos abs = helper.absolutePos(pos);
		stack.getItem().onItemUseFirst(stack, new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
				new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));

		helper.assertTrue(FuzzyIdentifierItem.getType(stack) == Fluids.HOTSTEAM, "the tool took the boiler's output, got " + FuzzyIdentifierItem.getType(stack).getName());
		helper.succeed();
	}

	// ------------------------------------------------------------------ firestorm: the plant

	/** Only a plant's block is a firestorm block, and the switch decides whether fire reaches it at all. */
	@GameTest(template = EMPTY)
	public static void onlyAChemicalPlantIsAFirestormBlock(GameTestHelper helper) {

		helper.assertTrue(IFirestormBlock.of(ModBlocks.machine_chemical_plant.get()) != null, "a chemical plant's block is");
		helper.assertTrue(IFirestormBlock.of(Blocks.STONE) == null && IFirestormBlock.of(ModBlocks.barrel_plastic.get()) == null, "stone and a barrel are not");
		BlockEntityMachineChemicalPlant plant = plant(helper);
		helper.assertTrue(IFirestormTE.of(plant) != null && IFirestormTE.of(barrel(helper, new BlockPos(1, 1, 1))) == null, "the plant has a firestorm face, a barrel has not");
		helper.assertTrue(!IFirestormTE.of(plant).isDamaged() && IFirestormTE.of(plant).getRepairMaterials().isEmpty(), "and it is never damaged: nothing repairs a burnt-out plant");
		helper.succeed();
	}

	/**
	 * The base mod's {@code IRepairable.of} finds a plant through the addon's resolver while the firestorm switch is on, and only then:
	 * that is how the extinguisher's rounds reach it (D-2207, D-3041). A barrel is never one, and a plant's face puts its fire out and
	 * is not damaged.
	 */
	@GameTest(template = EMPTY)
	public static void theBaseModsRepairableLookupFindsAPlantOnlyWhileTheSwitchIsOn(GameTestHelper helper) {

		BlockEntityMachineChemicalPlant plant = plant(helper);
		boolean was = LeafiaFeatures.firestorm;
		try {
			LeafiaFeatures.firestorm = false;
			helper.assertTrue(com.hbm.blockentity.IRepairable.of(plant) == null, "with the switch off a plant is not repairable, as in the base mod");
			LeafiaFeatures.firestorm = true;
			com.hbm.blockentity.IRepairable found = com.hbm.blockentity.IRepairable.of(plant);
			helper.assertTrue(found instanceof LeafiaFirestorm.ChemicalPlant face && face.plant() == plant, "with it on the lookup answers the plant's adapter");
			helper.assertTrue(com.hbm.blockentity.IRepairable.of(barrel(helper, new BlockPos(1, 1, 1))) == null && com.hbm.blockentity.IRepairable.of(null) == null,
					"a barrel and nothing are not");
			plant.getExtraData().putBoolean(LeafiaFirestorm.ON_FIRE, true);
			found.tryExtinguish(helper.getLevel(), plant.getBlockPos(), com.hbm.blockentity.IRepairable.EnumExtinguishType.FOAM);
			helper.assertTrue(!plant.getExtraData().getBoolean(LeafiaFirestorm.ON_FIRE), "the adapter's tryExtinguish puts the plant out");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/**
	 * Each tank holding something flammable or combustible rolls once, one in 40 for a liquid and one in 3 for a gas
	 * (upstream's 200 and 15, divided by five); tanks with nothing flammable, or empty ones, never roll.
	 */
	@GameTest(template = BIG)
	public static void aPlantRollsOneInFortyForALiquidAndOneInThreeForAGas(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		try {
			LeafiaFeatures.firestorm = true;
			BlockEntityMachineChemicalPlant plant = plant(helper);
			var face = new LeafiaFirestorm.ChemicalPlant(plant);

			plant.inputTanks[0].setTankType(Fluids.WATER);
			plant.inputTanks[0].setFill(1000);
			plant.inputTanks[1].setTankType(Fluids.DIESEL); // flammable and combustible, but empty
			Counting dice = new Counting();
			face.catchFire(dice);
			helper.assertTrue(dice.bounds.isEmpty() && !face.isDestroyed(), "water is not flammable and an empty tank does not count, rolled " + dice.bounds);

			plant.inputTanks[1].setFill(1000);
			face.catchFire(dice);
			helper.assertTrue(dice.bounds.equals(List.of(40)), "a liquid rolls one in 200/5 = 40 once, got " + dice.bounds);

			dice.bounds.clear();
			plant.inputTanks[2].setTankType(Fluids.GAS);
			plant.inputTanks[2].setFill(1000);
			face.catchFire(dice);
			helper.assertTrue(dice.bounds.equals(List.of(40, 3)), "a gas rolls one in 15/5 = 3, after the liquid in tank order, got " + dice.bounds);
			helper.assertTrue(!face.isDestroyed(), "and none of those rolls was a zero");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/**
	 * The roll that comes up zero burns the plant out: every tank is emptied, a liquid sets it on fire and a gas does
	 * not, and it explodes without breaking blocks.
	 */
	@GameTest(template = BIG)
	public static void aPlantThatCatchesFireIsBurntOutAndItsTanksEmptied(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		Blasts blasts = new Blasts();
		try {
			LeafiaFeatures.firestorm = true;

			for(FluidType fuel : new FluidType[] { Fluids.DIESEL, Fluids.GAS }) {
				BlockEntityMachineChemicalPlant plant = plant(helper);
				var face = new LeafiaFirestorm.ChemicalPlant(plant);
				plant.inputTanks[0].setTankType(fuel);
				plant.inputTanks[0].setFill(1000);
				plant.outputTanks[2].setTankType(Fluids.WATER);
				plant.outputTanks[2].setFill(500);

				RandomSource dice = RandomSource.create(7L);
				int rolls = 0;
				while(!face.isDestroyed() && rolls < 20_000) {
					face.catchFire(dice);
					rolls++;
				}
				helper.assertTrue(face.isDestroyed(), fuel.getName() + " never burnt the plant out in " + rolls + " rolls");
				helper.assertTrue(plant.inputTanks[0].getFill() == 0 && plant.outputTanks[2].getFill() == 0, "every tank was emptied, even the water one");
				helper.assertTrue(plant.inputTanks[0].getTankType() == fuel, "the types are left");
				helper.assertTrue(face.isOnFire() == (fuel == Fluids.DIESEL), fuel.getName() + ": a liquid burns, a gas only explodes");
				helper.assertTrue(!face.canCatchFire(), "and it cannot burn out twice");
				helper.assertTrue(blasts.seen.size() == 1, fuel.getName() + ": exactly one blast, got " + blasts.seen.size());
				Explosion blast = blasts.seen.get(0);
				BlockPos abs = helper.absolutePos(CORE);
				helper.assertTrue(blast.radius() == 5F, "of power 5, got " + blast.radius());
				helper.assertTrue(blast.center().equals(new Vec3(abs.getX() + 0.5D, abs.getY() + 0.5D, abs.getZ() + 0.5D)), "at the middle of the core, got " + blast.center());
				helper.assertTrue(blast.getBlockInteraction() == Explosion.BlockInteraction.KEEP, "that breaks no blocks");
				helper.assertTrue(!isFire(blast), "and sets no fires of its own");
				blasts.seen.clear();
			}
		} finally {
			blasts.close();
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/** Off, a plant is the base mod's: not destroyed whatever its tag says, and its screen is not blocked. */
	@GameTest(template = BIG)
	public static void withTheSwitchOffTheBurntOutFlagIsIgnored(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		try {
			BlockEntityMachineChemicalPlant plant = plant(helper);
			plant.getExtraData().putBoolean(LeafiaFirestorm.DESTROYED, true);
			plant.getExtraData().putBoolean(LeafiaFirestorm.ON_FIRE, true);
			var face = new LeafiaFirestorm.ChemicalPlant(plant);
			Player player = player(helper);

			LeafiaFeatures.firestorm = false;
			helper.assertTrue(!face.isDestroyed() && !face.isOnFire() && !plant.useBlocked(player), "off: not destroyed, not burning, the screen opens");
			face.catchFire(new Counting());
			IFirestormBlock.ignite(helper.getLevel(), helper.absolutePos(CORE));

			LeafiaFeatures.firestorm = true;
			helper.assertTrue(face.isDestroyed() && face.isOnFire() && plant.useBlocked(player), "on: the same plant is a burnt-out wreck with its screen shut");
		} finally {
			LeafiaFeatures.firestorm = was;
			// the flags are part of the saved tag, which this test wrote on purpose; the plant goes with the test
		}
		helper.succeed();
	}

	/** A burnt-out plant does no work: the module's progress and flags are cleared and nothing is consumed. */
	@GameTest(template = BIG)
	public static void aBurntOutPlantDoesNoWork(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		try {
			LeafiaFeatures.firestorm = true;
			BlockEntityMachineChemicalPlant plant = plant(helper);
			plant.setPower(50_000L);
			plant.inputTanks[0].setTankType(Fluids.WATER);
			plant.inputTanks[0].setFill(1_000);
			plant.inputTanks[1].setTankType(Fluids.LAVA);
			plant.inputTanks[1].setFill(25);
			plant.chemplantModule.setRecipe("chem.cobble", false);

			plant.updateEntity();
			helper.assertTrue(plant.didProcess && plant.chemplantModule.progress > 0, "a working plant processes: progress " + plant.chemplantModule.progress);

			plant.getExtraData().putBoolean(LeafiaFirestorm.DESTROYED, true);
			plant.updateEntity();
			helper.assertTrue(!plant.didProcess && !plant.chemplantModule.didProcess, "burnt out: didProcess is cleared");
			helper.assertTrue(plant.chemplantModule.progress == 0 && !plant.chemplantModule.markDirty, "and the module's progress and dirty flag with it");

			LeafiaFeatures.firestorm = false;
			plant.updateEntity();
			helper.assertTrue(plant.didProcess && plant.chemplantModule.progress > 0, "with the switch off the same plant works again");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/** A wreck refuses its screen. */
	@GameTest(template = BIG)
	public static void aBurntOutPlantKeepsItsScreenShut(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		try {
			LeafiaFeatures.firestorm = true;
			BlockEntityMachineChemicalPlant plant = plant(helper);
			Player player = player(helper);
			helper.assertTrue(!plant.useBlocked(player), "a sound plant opens");
			plant.getExtraData().putBoolean(LeafiaFirestorm.DESTROYED, true);
			helper.assertTrue(plant.useBlocked(player), "a burnt-out one does not");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/** Only a burning wreck sets the entities above it alight; and any extinguisher puts the fire out. */
	@GameTest(template = BIG, timeoutTicks = TickWindow.TIMEOUT)
	public static void aBurningWreckSetsEntitiesAboveItAlightUntilItIsPutOut(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		boolean tick = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		BlockEntityMachineChemicalPlant plant = plant(helper);

		TickWindow.sequence(helper).thenExecute(() -> {
			try {
				LeafiaFeatures.firestorm = true;
				// the fire the wreck spreads is not the subject: keep it from lighting anything but the pig
				helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, helper.getLevel().getServer());
				Pig near = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 8));
				Pig far = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
				var face = new LeafiaFirestorm.ChemicalPlant(plant);

				plant.updateEntity();
				helper.assertTrue(near.getRemainingFireTicks() <= 0, "a sound plant sets nothing alight");

				plant.getExtraData().putBoolean(LeafiaFirestorm.DESTROYED, true);
				plant.updateEntity();
				helper.assertTrue(near.getRemainingFireTicks() <= 0, "a burnt-out plant that is not burning sets nothing alight");

				plant.getExtraData().putBoolean(LeafiaFirestorm.ON_FIRE, true);
				plant.updateEntity();
				helper.assertTrue(near.getRemainingFireTicks() >= 100, "a burning wreck sets the animal on it alight for five seconds, got " + near.getRemainingFireTicks());
				helper.assertTrue(far.getRemainingFireTicks() <= 0, "and not one six blocks away");

				face.tryExtinguish(helper.getLevel(), helper.absolutePos(CORE), EnumExtinguishType.FOAM);
				helper.assertTrue(face.isDestroyed() && !face.isOnFire(), "foam puts the fire out and leaves the wreck");
				near.clearFire();
				plant.updateEntity();
				helper.assertTrue(near.getRemainingFireTicks() <= 0, "and it burns nothing afterwards");
				near.discard();
				far.discard();
			} finally {
				LeafiaFeatures.firestorm = was;
				helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(tick, helper.getLevel().getServer());
			}
			helper.succeed();
		});
	}

	/** The wreck's fire spreads to flammable ground in a radius of four, and only while the world ticks fire. */
	@GameTest(template = BIG)
	public static void aWrecksFireSpreadsToFlammableGroundOnlyWhileFireTicks(GameTestHelper helper) {

		boolean tick = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		BlockPos centre = new BlockPos(8, 1, 8);
		for(int x = 4; x <= 12; x++) for(int z = 4; z <= 12; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState());
		BlockPos abs = helper.absolutePos(centre);
		try {
			helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, helper.getLevel().getServer());
			for(int i = 0; i < 400; i++) LeafiaFirestorm.spreadFire(helper.getLevel(), abs, LeafiaFirestorm.FIRE_RADIUS);
			boolean any = false;
			for(int x = 4; x <= 12; x++) for(int z = 4; z <= 12; z++) any |= helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.FIRE);
			helper.assertTrue(!any, "with fire ticks off nothing is lit");

			helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, helper.getLevel().getServer());
			int tries = 0;
			while(!any && tries < 20_000) {
				LeafiaFirestorm.spreadFire(helper.getLevel(), abs, LeafiaFirestorm.FIRE_RADIUS);
				tries++;
				for(int x = 4; x <= 12 && !any; x++) for(int z = 4; z <= 12 && !any; z++) any = helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.FIRE);
			}
			helper.assertTrue(any, "with fire ticks on a fire was lit on the planks in " + tries + " tries");

			// and never outside the radius: the fires are all within four of the centre
			for(int x = 4; x <= 12; x++) for(int z = 4; z <= 12; z++) {
				if(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.FIRE))
					helper.assertTrue(Math.abs(x - 8) <= 4 && Math.abs(z - 8) <= 4, "a fire at " + x + "," + z + " is outside the radius");
			}
		} finally {
			helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(tick, helper.getLevel().getServer());
		}
		helper.succeed();
	}

	/** The wreck's flags are saved in the plant's tag, sent to the client and read back. */
	@GameTest(template = BIG)
	public static void theWrecksFlagsAreSavedAndSynced(GameTestHelper helper) {

		BlockEntityMachineChemicalPlant plant = plant(helper);
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(!plant.saveWithoutMetadata(registries).contains("extraData"), "a sound plant saves nothing extra");

		plant.getExtraData().putBoolean(LeafiaFirestorm.DESTROYED, true);
		plant.getExtraData().putBoolean(LeafiaFirestorm.ON_FIRE, true);
		CompoundTag saved = plant.saveWithoutMetadata(registries);
		CompoundTag extra = saved.getCompound("extraData");
		helper.assertTrue(extra.getBoolean("firestorm_destroyed") && extra.getBoolean("firestorm_onFire"), "upstream's two keys are saved");

		BlockEntityMachineChemicalPlant loaded = new BlockEntityMachineChemicalPlant(plant.getBlockPos(), plant.getBlockState());
		loaded.loadWithComponents(saved, registries);
		boolean was = LeafiaFeatures.firestorm;
		try {
			LeafiaFeatures.firestorm = true;
			var face = new LeafiaFirestorm.ChemicalPlant(loaded);
			helper.assertTrue(face.isDestroyed() && face.isOnFire(), "and read back by a plant loaded from the save");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ MixinBlockFire

	/**
	 * Vanilla's fire block asks about the block next to it - {@code checkBurnOut} for each of its six neighbours -
	 * and the injection hands the position to {@code IFirestormBlock.ignite}, which rolls for the plant that owns it.
	 */
	@GameTest(template = BIG)
	public static void fireAskingAboutANeighbourRollsForThePlantThere(GameTestHelper helper) {

		Method checkBurnOut = reflect("checkBurnOut", Level.class, BlockPos.class, int.class, RandomSource.class, int.class, Direction.class);
		boolean was = LeafiaFeatures.firestorm;
		try {
			BlockEntityMachineChemicalPlant plant = bodiedPlant(helper);
			plant.inputTanks[0].setTankType(Fluids.GAS);
			plant.inputTanks[0].setFill(1000);
			BlockPos part = helper.absolutePos(CORE.east()); // a part of the plant, not its core
			var face = new LeafiaFirestorm.ChemicalPlant(plant);

			LeafiaFeatures.firestorm = false;
			for(int i = 0; i < 200; i++) invoke(checkBurnOut, Blocks.FIRE, helper.getLevel(), part, 300, helper.getLevel().getRandom(), 0, Direction.WEST);
			LeafiaFeatures.firestorm = true;
			helper.assertTrue(!face.isDestroyed(), "switched off, 200 questions burn nothing");

			int asked = 0;
			while(!face.isDestroyed() && asked < 400) {
				invoke(checkBurnOut, Blocks.FIRE, helper.getLevel(), part, 300, helper.getLevel().getRandom(), 0, Direction.WEST);
				asked++;
			}
			helper.assertTrue(face.isDestroyed(), "switched on, asking about a part of the plant burnt it out within " + asked + " questions");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/** And {@code getIgniteOdds}, which vanilla asks about every position in the box around a fire, solid or not. */
	@GameTest(template = BIG)
	public static void fireAskingItsIgniteOddsRollsForThePlantThere(GameTestHelper helper) {

		Method odds = reflect("getIgniteOdds", LevelReader.class, BlockPos.class);
		boolean was = LeafiaFeatures.firestorm;
		try {
			BlockEntityMachineChemicalPlant plant = bodiedPlant(helper);
			plant.inputTanks[0].setTankType(Fluids.GAS);
			plant.inputTanks[0].setFill(1000);
			BlockPos part = helper.absolutePos(CORE.north());
			var face = new LeafiaFirestorm.ChemicalPlant(plant);

			LeafiaFeatures.firestorm = false;
			for(int i = 0; i < 200; i++) invoke(odds, Blocks.FIRE, helper.getLevel(), part);
			LeafiaFeatures.firestorm = true;
			helper.assertTrue(!face.isDestroyed(), "switched off, 200 questions burn nothing");

			int asked = 0;
			while(!face.isDestroyed() && asked < 400) {
				invoke(odds, Blocks.FIRE, helper.getLevel(), part);
				asked++;
			}
			helper.assertTrue(face.isDestroyed(), "switched on, the odds question burnt the plant out within " + asked + " questions");
		} finally {
			LeafiaFeatures.firestorm = was;
		}
		helper.succeed();
	}

	/** End to end: a fire block ticking next to the plant, through vanilla's own tick, burns it out. */
	@GameTest(template = BIG)
	public static void aFireTickingNextToAPlantBurnsItOut(GameTestHelper helper) {

		boolean was = LeafiaFeatures.firestorm;
		boolean tick = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		try {
			LeafiaFeatures.firestorm = true;
			helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, helper.getLevel().getServer());
			BlockEntityMachineChemicalPlant plant = bodiedPlant(helper);
			plant.inputTanks[0].setTankType(Fluids.GAS);
			plant.inputTanks[0].setFill(1000);
			var face = new LeafiaFirestorm.ChemicalPlant(plant);

			BlockPos fire = CORE.offset(2, 0, 2); // diagonal to the ring: only the odds box reaches the plant
			helper.setBlock(fire.below(), Blocks.NETHERRACK.defaultBlockState()); // infiniburn: vanilla's fire ticks fully without a flammable neighbour
			int ticks = 0;
			while(!face.isDestroyed() && ticks < 300) {
				helper.setBlock(fire, Blocks.FIRE.defaultBlockState());
				BlockPos abs = helper.absolutePos(fire);
				helper.getLevel().getBlockState(abs).tick(helper.getLevel(), abs, helper.getLevel().getRandom());
				ticks++;
			}
			helper.assertTrue(face.isDestroyed(), "a ticking fire burnt the plant out within " + ticks + " ticks");
		} finally {
			LeafiaFeatures.firestorm = was;
			helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(tick, helper.getLevel().getServer());
		}
		helper.succeed();
	}

	private static Object invoke(Method method, Object target, Object... args) {
		try {
			return method.invoke(target, args);
		} catch(ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ the burnt-out model

	/** The wreck's model is upstream's mesh with a material (NeoForge bakes none without), on the plant's own texture. */
	@GameTest(template = EMPTY)
	public static void theWrecksModelIsInTheJar(GameTestHelper helper) {

		JsonObject model = json("models/block/chem_destroyed.json");
		helper.assertTrue("neoforge:obj".equals(model.get("loader").getAsString()), "an OBJ model");
		helper.assertTrue("leafia:models/blocks/chem_destroyed.obj".equals(model.get("model").getAsString()), "of the mesh upstream ships");
		helper.assertTrue("hbm:models/machines/chemical_plant".equals(model.getAsJsonObject("textures").get("0").getAsString()), "on the plant's own texture, as upstream binds it");
		helper.assertTrue(model.getAsJsonObject("visibility").size() == 0, "every group visible: upstream's renderAll");

		try(InputStream in = LeafiaFluidFirestormTests.class.getResourceAsStream("/assets/leafia/models/blocks/chem_destroyed.obj")) {
			helper.assertTrue(in != null, "the mesh is in the jar");
			String obj = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			helper.assertTrue(obj.contains("\nusemtl "), "with a material, or it would bake zero quads (D-1062)");
			for(String group : new String[] { "o Base", "o Slider", "o Spinner" }) helper.assertTrue(obj.contains(group), "and upstream's " + group);
		} catch(java.io.IOException e) {
			helper.fail(e.toString());
			return;
		}
		helper.succeed();
	}
}
