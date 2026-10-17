package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.BlockDummyable.Part;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.effects.nuke.NukeLCA;
import com.leafia.contents.effects.nuke.NukeLCA.ExplosionType;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock;
import com.leafia.contents.machines.reactors.apr.blocks.APRComponentBlock.APRComponentType;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE.APRChamber;
import com.leafia.contents.machines.reactors.apr.blocks.core.container.APRMainContainer;
import com.leafia.contents.machines.reactors.apr.blocks.outlet.APRPowerOutletTE;
import com.leafia.contents.machines.reactors.apr.blocks.port.APRFluidIOTE;
import com.leafia.eventbuses.NotifyEventListeners;
import com.leafia.init.LeafiaEntities;
import com.leafia.init.LeafiaHazards;
import com.leafia.init.hazards.types.containment.HazardTypeContainmentBase;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaFalloutVariants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The absorption particle reactor and its endothermic blast (D-3027): the registry, upstream's structure map and
 * its arithmetic tick by tick, assembly and the two ways it comes apart, the outlet, the port, the void button,
 * {@code NukeLCA}'s numbers and its cold blast end to end, and the capsule whose destruction sets one off.
 *
 * <p>A tick is a call inside one body (D-953). Expected numbers were worked out in Python from upstream's
 * expressions and transcribed. A blast is never allowed to run in the shared test world: every {@code NukeLCA} the
 * code under test spawns there is caught as it joins the level and refused ({@link #catchBlasts}); the one blast that
 * runs, runs at a random far spot. Every switch flipped is restored in the same body.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaAPRTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";
	private static final String HUGE = "empty20";

	// ---- the rig ---------------------------------------------------------------------------

	private static boolean on() {
		return LeafiaFeatures.aprReactor() && LeafiaBlocks.apr_core != null;
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

	private static APRCoreTE core(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, LeafiaBlocks.apr_core.get().defaultBlockState().setValue(BlockDummyable.PART, Part.CORE)
				.setValue(BlockDummyable.DIR, Direction.NORTH));
		return (APRCoreTE) helper.getBlockEntity(at);
	}

	/** A reactor with these chambers, assembled by hand without its structure, as {@code onAssemble} sizes it. */
	private static APRCoreTE assembled(GameTestHelper helper, BlockPos at, int... radii) {
		APRCoreTE te = core(helper, at);
		te.chambers.clear();
		for(int r : radii) te.chambers.add(r);
		te.rebuildMBRequirement();
		te.assembled = true;
		te.onAssemble();
		return te;
	}

	/** Runs {@code body} with every {@code NukeLCA} that joins the level caught and refused. */
	private static List<NukeLCA> catchBlasts(Runnable body) {
		List<NukeLCA> caught = new ArrayList<>();
		Consumer<EntityJoinLevelEvent> listener = e -> {
			if(e.getEntity() instanceof NukeLCA n) {
				caught.add(n);
				e.setCanceled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(listener);
		try {
			body.run();
		} finally {
			NeoForge.EVENT_BUS.unregister(listener);
		}
		return caught;
	}

	private static APRComponentBlock apr() {
		return LeafiaBlocks.apr_component.get();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.abs(b));
	}

	// ---- registry and constants ------------------------------------------------------------

	/**
	 * The four blocks with upstream's strengths (the core 5 / 30 x 0.6, the rest set by hardness alone: 25 x 0.2),
	 * the eight variants in upstream's order with an item each, the entity, the menu, and the reactor's constants.
	 */
	@GameTest(template = EMPTY)
	public static void theReactorIsRegisteredWithUpstreamsNumbers(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockState core = LeafiaBlocks.apr_core.get().defaultBlockState();
		helper.assertTrue(core.getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 5F && LeafiaBlocks.apr_core.get().getExplosionResistance() == 18F,
				"core 5 / 18");
		for(var b : List.of(LeafiaBlocks.apr_port, LeafiaBlocks.apr_outlet, LeafiaBlocks.apr_component))
			helper.assertTrue(b.get().defaultBlockState().getDestroySpeed(helper.getLevel(), BlockPos.ZERO) == 5F && b.get().getExplosionResistance() == 5F,
					b.getId() + " is 5 / 5");
		String[] order = { "support", "hull", "hull_assembled", "plating", "plating_dark", "blades", "mechanical", "coil" };
		APRComponentType[] types = APRComponentType.values();
		helper.assertTrue(types.length == 8, "eight variants");
		for(int i = 0; i < 8; i++) {
			helper.assertTrue(types[i].getSerializedName().equals(order[i]), "variant " + i + " is " + order[i]);
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(LeafiaRef.rl("apr_component_" + order[i]));
			helper.assertTrue(item != net.minecraft.world.item.Items.AIR, "apr_component_" + order[i] + " has an item");
		}
		helper.assertTrue(LeafiaEntities.NUKE_LCA != null, "the blast is registered");
		helper.assertTrue(APRCoreTE.minChamberRadius == 6 && APRCoreTE.maxChamberRadius == 32 && APRCoreTE.getCapacityFromRadius(7) == 350000,
				"radii 6 to 32, 50,000 mB a block of radius");
		helper.assertTrue(APRCoreTE.maxDamage == 600 && APRCoreTE.graphXSegments == 16 && APRCoreTE.graphRefreshRate == 10 && APRCoreTE.controlSpeed == 20,
				"600 damage, a sixteen-sample graph every ten ticks, the funnel at 1 a tick");
		helper.assertTrue(APRCoreTE.blastRadius(60000) == 32 && APRCoreTE.blastRadius(30000) == 22 && APRCoreTE.blastRadius(0) == 12,
				"the blast is 22 + (particles - 30000) / 3000");
		helper.assertTrue(APRCoreTE.leverOffset(10) == 0 && APRCoreTE.leverOffset(100) == 33 && APRCoreTE.leverOffset(55) == 16,
				"the lever runs 33 pixels");
		helper.assertTrue(APRCoreTE.controlFromMouse(136) == 10 && APRCoreTE.controlFromMouse(169) == 100 && APRCoreTE.controlFromMouse(0) == 10
				&& APRCoreTE.controlFromMouse(500) == 100, "dragging maps 33 pixels onto 10..100, clamped");
		helper.assertTrue("TOO_SMALL".equals(APRCoreTE.radiusError("5")) && "TOO_LARGE".equals(APRCoreTE.radiusError("33"))
				&& "MALFORMED".equals(APRCoreTE.radiusError("x")) && APRCoreTE.radiusError("6") == null, "the editor's field rules");
		// the menu: six slots at upstream's coordinates, then the player's 36
		ServerPlayer player = mockPlayer(helper);
		APRCoreTE te = core(helper, new BlockPos(2, 1, 2));
		APRMainContainer menu = new APRMainContainer(0, player.getInventory(), te);
		int[][] xy = { { 117, 96 }, { 135, 96 }, { 84, 25 }, { 84, 61 }, { 8, 25 }, { 8, 61 } };
		helper.assertTrue(menu.slots.size() == 6 + 36, "6 + 36 slots");
		for(int i = 0; i < 6; i++)
			helper.assertTrue(menu.slots.get(i).x == xy[i][0] && menu.slots.get(i).y == xy[i][1], "slot " + i + " at " + xy[i][0] + "," + xy[i][1]);
		helper.assertTrue(menu.slots.get(6).x == 25 && menu.slots.get(6).y == 138 && menu.slots.get(41).y == 196, "the inventory from (25, 138), the hotbar at 196");
		helper.assertTrue(te.createMenu(0, player.getInventory(), player) == null, "no screen before it is assembled");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
		helper.succeed();
	}

	/**
	 * Upstream's map for one chamber of radius 6: the outlet five below the core in the middle of the disc's
	 * bottom, the 3 x 3 x 4 funnel empty under the core (it overwrites the disc's top middle), 48 gold coils round it,
	 * plating off the axes and dark plating on them, mechanical parts between, and the torus: supports where a ring
	 * crosses an axis, blades inside the inner ring. Mirror-symmetric in x and in z.
	 */
	@GameTest(template = EMPTY)
	public static void theStructureMapIsUpstreamsForOneChamber(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = core(helper, at);
		te.setChambers(new int[] { 6 }, true);
		Map<BlockPos, BlockState> req = te.mbRequirement;
		BlockPos c = te.getBlockPos();
		helper.assertTrue(req.get(c.below(5)).is(LeafiaBlocks.apr_outlet.get()), "the outlet five below");
		int air = 0, coil = 0, outlets = 0;
		for(var e : req.entrySet()) {
			if(e.getValue().isAir()) air++;
			else if(e.getValue().is(LeafiaBlocks.apr_outlet.get())) outlets++;
			else if(e.getValue().getValue(APRComponentBlock.VARIANT) == APRComponentType.COIL) coil++;
			BlockPos rel = e.getKey().subtract(c);
			BlockState mirror = req.get(c.offset(-rel.getX(), rel.getY(), rel.getZ()));
			helper.assertTrue(e.getValue().equals(mirror), "not mirrored in x at " + rel);
			mirror = req.get(c.offset(rel.getX(), rel.getY(), -rel.getZ()));
			helper.assertTrue(e.getValue().equals(mirror), "not mirrored in z at " + rel);
		}
		helper.assertTrue(air == 36 && coil == 48 && outlets == 1, "36 air, 48 coils, 1 outlet: " + air + "/" + coil + "/" + outlets);
		helper.assertTrue(req.get(c.below()).isAir() && req.get(c.offset(1, -4, 1)).isAir(), "the funnel is empty to four below");
		helper.assertTrue(req.get(c.offset(0, -1, 2)).getValue(APRComponentBlock.VARIANT) == APRComponentType.COIL, "a coil two out");
		helper.assertTrue(req.get(c.offset(3, -1, 1)).getValue(APRComponentBlock.VARIANT) == APRComponentType.PLATING, "plating off the axes");
		helper.assertTrue(req.get(c.offset(0, -1, 3)).getValue(APRComponentBlock.VARIANT) == APRComponentType.PLATING_DARK, "dark plating on them");
		helper.assertTrue(req.get(c.offset(0, -3, 3)).getValue(APRComponentBlock.VARIANT) == APRComponentType.MECHANICAL, "parts between");
		helper.assertTrue(req.get(c.offset(3, -5, 1)).getValue(APRComponentBlock.VARIANT) == APRComponentType.PLATING, "the bottom is plated");
		helper.assertTrue(req.get(c.offset(0, 0, 6)).getValue(APRComponentBlock.VARIANT) == APRComponentType.SUPPORT
				&& req.get(c.offset(0, -5, -6)).getValue(APRComponentBlock.VARIANT) == APRComponentType.SUPPORT, "the inner ring is held on the axes");
		helper.assertTrue(req.get(c.offset(0, -2, 6)).getValue(APRComponentBlock.VARIANT) == APRComponentType.BLADES, "blades inside it");
		helper.assertTrue(!req.containsKey(c.above()) && !req.containsKey(c.offset(9, -1, 0)), "nothing above the core or past the rings");
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/** Upstream's {@code setChambers}: clamped to 1..32, sorted, and a request with two rings within 3 is refused and (D-3076) the old list stays. */
	@GameTest(template = EMPTY)
	public static void aRejectedChamberListLeavesTheReactorsAlone(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = core(helper, at);
		te.setChambers(new int[] { 20, 8, 40 }, true);
		helper.assertTrue(te.chambers.equals(List.of(8, 20, 32)), "clamped and sorted: " + te.chambers);
		te.setChambers(new int[] { 0, 9 }, false);
		helper.assertTrue(te.chambers.equals(List.of(1, 9)), "clamped up to 1: " + te.chambers);
		te.setChambers(new int[] { 0, 9 }, true);
		helper.assertTrue(te.chambers.equals(List.of(1, 9)), "the spacing counts from the centre, so a first ring under 4 is refused and the list kept: " + te.chambers);
		te.setChambers(new int[] { 9, 6 }, false);
		helper.assertTrue(te.chambers.equals(List.of(6, 9)), "the client's copy takes it as it is: " + te.chambers);
		te.setChambers(new int[] { 9, 6 }, true);
		helper.assertTrue(te.chambers.equals(List.of(6, 9)), "a request three apart is refused and the list kept (D-3076; upstream emptied it): " + te.chambers);
		te.setChambers(new int[] { 10, 6 }, true);
		helper.assertTrue(te.chambers.equals(List.of(6, 10)), "four apart is accepted");
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * A complete radius-6 reactor - built by the creative "Auto-Build" - assembles: tanks of 300,000 mB, liquid oxygen
	 * at 50:1 (6,000), a particle cap of 30,000. Taking out one plate disassembles it through the block-change
	 * listener, and with no particles in it that is quiet: no blast.
	 */
	@GameTest(template = HUGE)
	public static void aCompleteStructureAssemblesAndABrokenPlateTakesItApartQuietly(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(10, 7, 10);
		APRCoreTE te = core(helper, at);
		te.setChambers(new int[] { 6 }, true);
		te.highlight(mockPlayer(helper));
		helper.assertTrue(te.checkAssembled(), "the auto-built structure is complete");
		te.assembled = true;
		te.onAssemble();
		helper.assertTrue(te.oxygen.getTankType() == LeafiaFluids.OXYGEN_GAS && te.oxygen.getMaxFill() == 300000, "gaseous oxygen, 300,000 mB");
		helper.assertTrue(te.lox.getTankType() == Fluids.OXYGEN && te.lox.getMaxFill() == 6000, "liquid oxygen, 6,000 mB");
		helper.assertTrue(te.particleCap == 30000 && te.chamberDatas.size() == 1 && te.chamberDatas.get(0).input.getMaxFill() == 300000,
				"a cap of 30,000 and one chamber of 300,000");
		te.subscribeToBlockNotification();
		helper.assertTrue(NotifyEventListeners.isSubscribed(te), "it listens for block changes");
		BlockPos plate = te.getBlockPos().offset(3, -1, 1);
		helper.assertTrue(helper.getLevel().getBlockState(plate).getValue(APRComponentBlock.VARIANT) == APRComponentType.PLATING, "a plate stands there");
		List<NukeLCA> blasts = catchBlasts(() -> helper.getLevel().setBlockAndUpdate(plate, Blocks.AIR.defaultBlockState()));
		helper.assertTrue(!te.assembled, "the missing plate disassembled it");
		helper.assertTrue(blasts.isEmpty(), "and without particles that is quiet");
		helper.setBlock(at, Blocks.AIR);
		helper.assertTrue(!NotifyEventListeners.isSubscribed(te), "a removed core stops listening");
		for(BlockPos p : te.mbRequirement.keySet()) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
		helper.succeed();
	}

	/**
	 * One tick of a full reactor - 300,000 mB of gas, the particle cap reached, the funnel at 10: 1,500 mB of gas becomes
	 * 30 of liquid oxygen, worth {@code 1500 x 120^0.75 / 1000} = 54.385; the turbine reaches 0.0443768 RPS
	 * (the spin-up bonus below 30 RPS is 1.31875) and the chamber 20.542760 degrees after its 0.998 decay.
	 */
	@GameTest(template = EMPTY)
	public static void oneTickOfCoolantIsUpstreamsArithmetic(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = assembled(helper, at, 6);
		te.oxygen.setFill(300000);
		te.particles = 30000;
		helper.assertTrue(te.control == 10 && te.targetControl == 10, "the funnel starts at 10");
		helper.assertTrue(APRCoreTE.getHeatDifference(LeafiaFluids.OXYGEN_GAS) == 120 && APRCoreTE.getFinalConversionTo(LeafiaFluids.OXYGEN_GAS) == Fluids.OXYGEN,
				"gas at 20 cools to liquid at -100");
		te.tickServer();
		helper.assertTrue(te.oxygen.getFill() == 298500 && te.lox.getFill() == 30, "30 cycles: " + te.oxygen.getFill() + " / " + te.lox.getFill());
		helper.assertTrue(near(te.rps, 0.04437668719363045), "rps " + te.rps);
		helper.assertTrue(near(te.chamberDatas.get(0).blanketTemp, 20.54275987638117), "blanket " + te.chamberDatas.get(0).blanketTemp);
		helper.assertTrue(te.hullTemp == 20 && te.damage == 0, "the hull follows last tick's hottest chamber");
		te.rps = 60;
		helper.assertTrue(te.getPowerOutput() == 600000, "60 RPS on a 30,000 cap is 600,000 HE a tick: " + te.getPowerOutput());
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * Over 110 RPS the damage rises one a tick to 600 and the reactor comes apart - with no particles, quietly - and
	 * below it, with every chamber under 500, falls one a tick.
	 */
	@GameTest(template = EMPTY)
	public static void overspeedDamagesTheReactorUntilItComesApart(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = assembled(helper, at, 6);
		List<NukeLCA> blasts = catchBlasts(() -> {
			for(int i = 0; i < 600; i++) {
				te.rps = 200;
				te.tickServer();
			}
			helper.assertTrue(te.damage == 600 && te.assembled, "600 ticks of overspeed is 600 damage and still standing: " + te.damage);
			te.rps = 200;
			te.tickServer();
			helper.assertTrue(te.damage == 600 && !te.assembled, "the 601st takes it apart, the damage held at 600");
		});
		helper.assertTrue(blasts.isEmpty(), "without particles there is no blast");
		te.assembled = true;
		te.rps = 0;
		te.tickServer();
		helper.assertTrue(te.damage == 599, "cool and slow, the damage falls by one: " + te.damage);
		// D-3076: "hottest" is this tick's. A chamber at 500.5 cools to 499.54 inside the tick, so the damage falls (upstream read 500.5 and held it)
		te.chamberDatas.get(0).blanketTemp = 500.5;
		te.tickServer();
		helper.assertTrue(te.damage == 598, "a chamber that cooled through 500 this tick no longer holds the damage up: " + te.damage);
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/**
	 * Destructive or not, upstream's order: a support or an assembled hull broken, any damage, or a hull over 1,538 -
	 * and never without particles. The blast is an endothermic {@code NukeLCA} of {@code 22 + (p - 30000) / 3000}.
	 */
	@GameTest(template = EMPTY)
	public static void aDestructiveDisassemblyFiresTheEndothermicBlast(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = assembled(helper, at, 6);
		BlockPos c = te.getBlockPos();
		BlockPos support = c.offset(0, 0, 6), plate = c.offset(3, -1, 1);
		te.particles = 60000;
		helper.assertTrue(te.isDestructive(support) && !te.isDestructive(plate), "a support is, a plate is not");
		te.damage = 1;
		helper.assertTrue(te.isDestructive(plate), "any damage is");
		te.damage = 0;
		te.hullTemp = 1539;
		helper.assertTrue(te.isDestructive(plate), "a molten hull is");
		te.particles = 0;
		helper.assertTrue(!te.isDestructive(support), "nothing is without particles");
		te.hullTemp = 20;
		te.particles = 60000;
		te.damage = 1;
		List<NukeLCA> blasts = catchBlasts(() -> te.disassemble(c));
		helper.assertTrue(!te.assembled && blasts.size() == 1, "one blast: " + blasts.size());
		NukeLCA nuke = blasts.get(0);
		helper.assertTrue(nuke.type == ExplosionType.ENDOTHERMIC && nuke.getRadius() == 32 && nuke.getStrength() == 64,
				"endothermic, radius 32, strength 64: " + nuke.type + " " + nuke.getRadius());
		helper.assertTrue(nuke.getX() == c.getX() + 0.5 && nuke.getY() == c.getY() && nuke.getZ() == c.getZ() + 0.5, "at the broken block");
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/** The outlet holds exactly what the core five above it makes this tick, and offers it down. */
	@GameTest(template = BIG)
	public static void theOutletHoldsWhatTheCoreAboveMakes(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos outletAt = new BlockPos(5, 1, 5);
		helper.setBlock(outletAt, LeafiaBlocks.apr_outlet.get().defaultBlockState());
		APRPowerOutletTE outlet = (APRPowerOutletTE) helper.getBlockEntity(outletAt);
		APRCoreTE te = core(helper, outletAt.above(5));
		te.rps = 30;
		te.particleCap = 60000;
		outlet.update();
		helper.assertTrue(outlet.getPower() == 600000 && outlet.getMaxPower() == 600000, "30 RPS on 60,000 is 600,000: " + outlet.getPower());
		helper.setBlock(outletAt.above(5), Blocks.AIR);
		outlet.update();
		helper.assertTrue(outlet.getPower() == 600000, "with no core it keeps what it last held");
		helper.setBlock(outletAt, Blocks.AIR);
		helper.succeed();
	}

	/** A port feeds the chamber whose radius is nearest its distance, and the core's coolant; it sends that chamber's output. */
	@GameTest(template = HUGE)
	public static void aPortFeedsTheChamberNearestItsDistance(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = assembled(helper, at, 6, 12);
		BlockPos portAt = at.offset(11, 0, 2);
		helper.setBlock(portAt, LeafiaBlocks.apr_port.get().defaultBlockState());
		APRFluidIOTE port = (APRFluidIOTE) helper.getBlockEntity(portAt);
		port.targetPos = te.getBlockPos();
		helper.assertTrue(port.validateTarget() && port.getChamberIndex() == 1, "11.2 from the core is the radius-12 chamber");
		APRChamber chamber = te.chamberDatas.get(1);
		helper.assertTrue(port.getReceivingTanks().length == 2 && port.getReceivingTanks()[0] == chamber.input && port.getReceivingTanks()[1] == te.oxygen,
				"it fills the chamber's input and the coolant");
		helper.assertTrue(port.getSendingTanks().length == 1 && port.getSendingTanks()[0] == chamber.output, "it drains the chamber's output");
		BlockPos tie = at.offset(9, 0, 0);
		helper.setBlock(tie, LeafiaBlocks.apr_port.get().defaultBlockState());
		APRFluidIOTE tiePort = (APRFluidIOTE) helper.getBlockEntity(tie);
		tiePort.targetPos = te.getBlockPos();
		helper.assertTrue(tiePort.validateTarget() && tiePort.getChamberIndex() == 0, "halfway between ties to the inner one");
		helper.setBlock(portAt, Blocks.AIR);
		helper.setBlock(tie, Blocks.AIR);
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	/** The void button trades an empty capsule for each thousand particles, then empties the rest; hot chambers refuse. */
	@GameTest(template = EMPTY)
	public static void theVoidButtonTradesEmptyCapsulesForParticles(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		BlockPos at = new BlockPos(2, 1, 2);
		APRCoreTE te = assembled(helper, at, 6);
		ServerPlayer player = mockPlayer(helper);
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(ModItems.particle_empty.get(), 3));
		te.particles = 2900;
		te.chamberDatas.get(0).blanketTemp = 150;
		te.voidParticles(player);
		helper.assertTrue(te.particles == 2900, "a chamber over 100 degrees refuses");
		te.chamberDatas.get(0).blanketTemp = 20;
		te.voidParticles(player);
		int full = 0, empty = 0;
		for(ItemStack s : player.getInventory().items) {
			if(s.getItem() == LeafiaResourceItems.get("particle_absorption").get()) full += s.getCount();
			if(s.getItem() == ModItems.particle_empty.get()) empty += s.getCount();
		}
		helper.assertTrue(te.particles == 0 && full == 2 && empty == 1, "2,900 is two capsules and the other 900 are lost: " + full + " / " + empty);
		helper.setBlock(at, Blocks.AIR);
		helper.succeed();
	}

	// ---- NukeLCA ---------------------------------------------------------------------------

	/** {@code statFac} and the derived numbers: strength 2r (25 for 0), fallout 2.5r, flood {@code lowest + 1 + round(r^0.65 / 2.5)}, ash. */
	@GameTest(template = EMPTY)
	public static void theBlastsNumbersAreUpstreams(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		NukeLCA a = NukeLCA.statFac(helper.getLevel(), 10, 0, 0, 0);
		NukeLCA b = NukeLCA.statFac(helper.getLevel(), 0, 0, 0, 0);
		helper.assertTrue(a.getStrength() == 20 && a.getRadius() == 10 && b.getRadius() == 25 && b.getStrength() == 50, "2r, and 25 for nothing");
		helper.assertTrue(a.falloutScale() == 25 && a.hasFallout() && a.type == null, "fallout 25, a plain blast unless told");
		helper.assertTrue(NukeLCA.floodHeight(60, 10) == 63 && NukeLCA.floodHeight(60, 32) == 65, "the flood is 2 over r 10 (4.47 / 2.5 = 1.79), 4 over 32 (9.51 / 2.5 = 3.81)");
		helper.assertTrue(NukeLCA.floodHeight(Integer.MAX_VALUE, 10) < 0, "a crater that removed nothing floods nowhere (it overflows)");
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		helper.assertTrue(NukeLCA.ashCount(zombie) == 17 && NukeLCA.ashCount(golem) == 50, "17 ashes for a zombie, 50 at most");
		helper.assertTrue(NukeLCA.isExplosionExempt(EntityType.OCELOT.create(helper.getLevel())) && NukeLCA.isExplosionExempt(EntityType.CAT.create(helper.getLevel()))
				&& !NukeLCA.isExplosionExempt(zombie), "ocelots and cats are spared");
		a.discard();
		b.discard();
		helper.succeed();
	}

	/** The cold blast: {@code 250 x (r - d) / r} through line of sight, five seconds of fire and a push; the spared and the far untouched. */
	@GameTest(template = BIG)
	public static void theEndothermicBlastHurtsByDistance(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 8));
		Ocelot ocelot = helper.spawn(EntityType.OCELOT, new BlockPos(12, 2, 8));
		IronGolem far = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(0, 2, 0));
		// at the golem's feet height, so its distance is exactly 8; the line of sight runs to its eyes
		Vec3 centre = new Vec3(golem.getX() + 8, golem.getY(), golem.getZ());
		List<net.minecraft.world.entity.Entity> list = List.of(golem, ocelot, far);
		Vec3 before = golem.getDeltaMovement();
		NukeLCA.dealDamageEndothermic(helper.getLevel(), list, centre.x, centre.y, centre.z, 10, NukeLCA.ENDOTHERMIC_MAX_DAMAGE);
		helper.assertTrue(Math.abs(golem.getHealth() - 50) < 1e-3, "eight out of ten is 50: " + golem.getHealth());
		helper.assertTrue(golem.getRemainingFireTicks() > 0 && golem.getDeltaMovement().x < before.x, "alight and pushed away");
		helper.assertTrue(ocelot.getHealth() == ocelot.getMaxHealth() && far.getHealth() == far.getMaxHealth(), "the ocelot and the golem 12.8 out untouched");
		golem.discard();
		ocelot.discard();
		far.discard();
		helper.succeed();
	}

	private static BlockPos farSpot(GameTestHelper helper) {
		RandomSource r = helper.getLevel().random;
		return new BlockPos(-(70_000 + r.nextInt(4000) * 16) + 8, 0, 70_000 + r.nextInt(4000) * 16 + 8);
	}

	/**
	 * A radius-4 endothermic blast run end to end at a random far spot: it digs on the batched ray, records the lowest
	 * block it took, and leaves an endothermic fallout of 10 whose flood height is that plus 1 + round(4^0.65 / 2.5).
	 * The rain is removed as soon as it is seen.
	 */
	@GameTest(template = EMPTY, timeoutTicks = 3000, batch = "leafia_apr_isolated")
	public static void aSmallColdBlastLeavesFloodingFallout(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		ServerLevel level = helper.getLevel();
		BlockPos far = farSpot(helper);
		NukeLCA[] nuke = new NukeLCA[1];
		EntityFalloutRain[] rain = new EntityFalloutRain[1];
		Consumer<EntityJoinLevelEvent> watch = e -> {
			if(e.getEntity() instanceof EntityFalloutRain r && nuke[0] != null && r.distanceToSqr(nuke[0]) < 1) rain[0] = r;
		};
		NeoForge.EVENT_BUS.addListener(watch);
		helper.onEachTick(() -> {
			if(nuke[0] != null) return;
			level.getChunk(far);
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, far.getX(), far.getZ());
			nuke[0] = NukeLCA.statFac(level, 4, far.getX() + 0.5, y, far.getZ() + 0.5).setExplosionType(ExplosionType.ENDOTHERMIC);
			level.addFreshEntity(nuke[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(nuke[0] != null && nuke[0].isRemoved() && rain[0] != null, "blast still running");
			NeoForge.EVENT_BUS.unregister(watch);
			EntityFalloutRain r = rain[0];
			r.discard();
			helper.assertTrue(LeafiaFalloutVariants.ENDOTHERMIC.equals(r.getVariantId()), "the fallout is endothermic: " + r.getVariantId());
			helper.assertTrue(r.getScale() == 10, "scale 2.5 x 4: " + r.getScale());
			helper.assertTrue(nuke[0].lowestHeight != Integer.MAX_VALUE, "the crater took a block");
			helper.assertTrue(LeafiaFalloutVariants.getFloodHeight(r) == nuke[0].lowestHeight + 1 + 1,
					"flood at lowest + 1 + round(4^0.65 / 2.5): " + LeafiaFalloutVariants.getFloodHeight(r) + " vs " + nuke[0].lowestHeight);
		});
	}

	// ---- the capsule -----------------------------------------------------------------------

	/**
	 * The capsule carries the endothermic containment at 15; destroyed by damage it sets off a radius-15 cold blast at
	 * its eye height, but not when it falls out of the world.
	 */
	@GameTest(template = EMPTY)
	public static void aDestroyedCapsuleSetsOffARadius15ColdBlast(GameTestHelper helper) {
		if(!on()) { helper.succeed(); return; }
		ItemStack capsule = new ItemStack(LeafiaResourceItems.get("particle_absorption").get());
		HazardEntry entry = null;
		for(HazardEntry e : HazardSystem.getHazardsFromStack(capsule)) if(e.getType() == LeafiaHazards.CONTAINER_ENDO) entry = e;
		helper.assertTrue(entry != null && HazardSystem.getHazardLevelFromStack(capsule, LeafiaHazards.CONTAINER_ENDO) == 15F, "the capsule's containment is 15");
		ServerLevel level = helper.getLevel();
		Vec3 p = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
		ItemEntity item = new ItemEntity(level, p.x, p.y, p.z, capsule.copy());
		DamageSource voidDamage = level.damageSources().fellOutOfWorld();
		helper.assertTrue(catchBlasts(() -> HazardTypeContainmentBase.dispatch(item, voidDamage)).isEmpty(), "the void sets nothing off");
		ItemEntity live = new ItemEntity(level, p.x, p.y, p.z, capsule.copy());
		level.addFreshEntity(live);
		List<NukeLCA> blasts = catchBlasts(() -> live.hurt(level.damageSources().lava(), 10));
		helper.assertTrue(live.isRemoved() && blasts.size() == 1, "burnt, it breaks its containment: " + blasts.size());
		NukeLCA nuke = blasts.get(0);
		helper.assertTrue(nuke.getRadius() == 15 && nuke.type == ExplosionType.ENDOTHERMIC, "radius 15, endothermic");
		helper.assertTrue(Math.abs(nuke.getY() - (p.y + live.getEyeHeight())) < 1e-6, "at its eye height");
		helper.succeed();
	}

	/** D-3075: the blast's type and flood height are saved, so a reloaded endothermic blast is still endothermic. */
	@GameTest(template = EMPTY)
	public static void aSavedBlastKeepsItsTypeAndFloodHeight(GameTestHelper helper) {
		NukeLCA blast = new NukeLCA(helper.getLevel());
		blast.setExplosionType(ExplosionType.ENDOTHERMIC);
		blast.lowestHeight = 37;
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		blast.addAdditionalSaveData(tag);
		NukeLCA loaded = new NukeLCA(helper.getLevel());
		loaded.readAdditionalSaveData(tag);
		helper.assertTrue(loaded.type == ExplosionType.ENDOTHERMIC, "the type was lost: " + loaded.type);
		helper.assertTrue(loaded.lowestHeight == 37, "the flood height was lost: " + loaded.lowestHeight);
		helper.succeed();
	}
}
