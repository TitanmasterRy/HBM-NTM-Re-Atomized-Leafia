package com.leafia.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.hbm.blockentity.machine.BlockEntityMachineSiren;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.CoreComponent;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCassette;
import com.hbm.items.machine.ItemCassette.TrackType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.LeafiaFluids;
import com.leafia.contents.building.doors.AddonDoorDecl;
import com.leafia.contents.building.doors.special.reactor_door.ReactorDoorTE;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.heat.rtheater.HeaterRTGTE;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;
import com.leafia.contents.machines.powercores.dfc.components.absorber.LeafiaCoreReceiverTE;
import com.leafia.contents.machines.powercores.dfc.components.emitter.LeafiaCoreEmitterTE;
import com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE;
import com.leafia.contents.machines.powercores.dfc.components.injector.LeafiaCoreInjectorTE;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE;
import com.leafia.contents.machines.reactors.lftr.components.MSRTEBase;
import com.leafia.contents.machines.reactors.lftr.components.arbitrary.MSRArbitraryTE;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlBlock;
import com.leafia.contents.machines.reactors.lftr.components.control.MSRControlTE;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.PWRDiagnosis;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentBlock;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.computer.PWRComputerTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE;
import com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE;
import com.leafia.contents.network.fluid.gauges.FluidDuctGaugeTE;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.siren.LeafiaSirenTE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The seventeen addon machines that get {@code IControllable} back (CCP-RETROFIT, D-3069): for each, the query keys are exactly
 * upstream's (NTM-CE's, or Leafia's where a mixin or addon file added them), one in-event drives the machine, and the in-events are
 * held to the bounds the machine's own screen or packet path enforces.
 *
 * <p>Events travel through {@link ControlEventSystem#broadcastEvent}, so what is exercised is the real dispatch (the block entity
 * answers for itself), not a direct call. Machines that tick on their own are removed before a body ends (D-953), the DFC and
 * reactor switches are restored around the one {@code setBlock} that needs them, and a number written into a
 * live machine is read back in the same tick, never a tick later (D-953).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelRetrofitTests {

	private static final String EMPTY = "empty";
	private static final String BIG = "empty16";
	private static final BlockPos AT = new BlockPos(2, 1, 2);

	// ------------------------------------------------------------------ plumbing

	private static ControlEventSystem sys(GameTestHelper helper) {
		return ControlEventSystem.get(helper.getLevel());
	}

	private static void send(GameTestHelper helper, BlockPos rel, ControlEvent evt) {
		sys(helper).broadcastEvent(helper.absolutePos(new BlockPos(5, 1, 5)), evt, helper.absolutePos(rel));
	}

	private static void send(GameTestHelper helper, ControlEvent evt) {
		send(helper, AT, evt);
	}

	private static ControlEvent evt(String name, String var, float value) {
		return ControlEvent.newEvent(name).setVar(var, value);
	}

	private static Set<String> keys(IControllable c) {
		return new TreeSet<>(c.getQueryData().keySet());
	}

	private static void assertKeys(GameTestHelper helper, IControllable c, String what, String... expected) {
		Set<String> want = new TreeSet<>(List.of(expected));
		helper.assertTrue(keys(c).equals(want), what + " keys are " + want + ", got " + keys(c));
	}

	private static void assertIn(GameTestHelper helper, IControllable c, String what, String... in) {
		helper.assertTrue(c.getInEvents().equals(List.of(in)), what + " in-events are " + List.of(in) + ", got " + c.getInEvents());
	}

	private static float val(IControllable c, String key) {
		DataValue v = c.getQueryData().get(key);
		if(v == null)
			throw new AssertionError("no value for " + key + " in " + keys(c));
		return v.getNumber();
	}

	private static void same(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-4, what + ": expected " + expected + ", was " + actual);
	}

	private static IControllable controllable(GameTestHelper helper, BlockEntity be) {
		helper.assertTrue(be instanceof IControllable, "the block entity is itself controllable, got " + be);
		IControllable c = (IControllable) be;
		helper.assertTrue(c.getControlPos().equals(be.getBlockPos()) && c.getControlWorld() == helper.getLevel(), "and reports its position and level");
		return c;
	}

	private static BlockEntity dfc(GameTestHelper helper, BlockPos rel, Block block) {
		boolean was = LeafiaFeatures.dfcOverhaul;
		LeafiaFeatures.dfcOverhaul = true;
		try {
			helper.setBlock(rel, block);
		} finally {
			LeafiaFeatures.dfcOverhaul = was;
		}
		return helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	// ------------------------------------------------------------------ the radioisotope heater

	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theRadioisotopeHeaterAnswersHeatAndMaxHeatAndJoinsTheSystem(GameTestHelper helper) {
		helper.setBlock(AT, LeafiaBlocks.heater_rt.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
		HeaterRTGTE heater = (HeaterRTGTE) helper.getBlockEntity(AT);
		IControllable c = controllable(helper, heater);
		assertKeys(helper, c, "the radioisotope heater", "heat", "maxHeat");
		assertIn(helper, c, "the radioisotope heater");
		heater.heatEnergy = 1234;
		same(helper, 1234, val(c, "heat"), "heat");
		same(helper, HeaterRTGTE.MAX_HEAT, val(c, "maxHeat"), "maxHeat");
		TickWindow.sequence(helper).thenIdle(2).thenExecute(() -> {
			helper.assertTrue(sys(helper).isValid(heater), "it joined its level's system when it loaded");
			helper.setBlock(AT, Blocks.AIR);
			helper.assertTrue(!sys(helper).isValid(heater), "and left it when it was removed");
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ the DFC

	@GameTest(template = EMPTY)
	public static void theExchangerAnswersInputAndOutput(GameTestHelper helper) {
		helper.setBlock(AT, LeafiaBlocks.dfc_exchanger.get());
		CoreExchangerTE x = (CoreExchangerTE) helper.getBlockEntity(AT);
		IControllable c = controllable(helper, x);
		assertKeys(helper, c, "the exchanger", "input", "output");
		assertIn(helper, c, "the exchanger");
		x.input.setFill(321);
		x.output.setFill(654);
		same(helper, 321, val(c, "input"), "input");
		same(helper, 654, val(c, "output"), "output");
		x.input.setFill(0);
		x.output.setFill(0);
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theInjectorAnswersBothTanksEachFromItsOwn(GameTestHelper helper) {
		BlockEntity be = dfc(helper, AT, ModBlocks.dfc_injector.get());
		helper.assertTrue(be instanceof LeafiaCoreInjectorTE, "the addon injector, got " + be);
		LeafiaCoreInjectorTE injector = (LeafiaCoreInjectorTE) be;
		IControllable c = controllable(helper, injector);
		assertKeys(helper, c, "the injector", "tankA", "tankB");
		assertIn(helper, c, "the injector");
		injector.tanks[0].setFill(777);
		same(helper, 777, val(c, "tankA"), "tankA");
		injector.tanks[1].setFill(333);
		same(helper, 777, val(c, "tankA"), "tankA still reads the first tank");
		same(helper, 333, val(c, "tankB"), "tankB reads the second tank (owner-approved fix of Leafia's tanks[0], D-3072)");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theEmitterTakesLevelAndActiveWithinItsDial(GameTestHelper helper) {
		BlockEntity be = dfc(helper, AT, ModBlocks.dfc_emitter.get());
		helper.assertTrue(be instanceof LeafiaCoreEmitterTE, "the addon emitter, got " + be);
		LeafiaCoreEmitterTE e = (LeafiaCoreEmitterTE) be;
		IControllable c = controllable(helper, e);
		assertKeys(helper, c, "the emitter", "active", "level", "emitted");
		assertIn(helper, c, "the emitter", "set_booster_level", "set_booster_active");
		send(helper, evt("set_booster_level", "level", 40F));
		helper.assertTrue(e.watts == 40, "level 40 sets the dial to 40, is " + e.watts);
		same(helper, 40, val(c, "level"), "level reads back");
		send(helper, evt("set_booster_level", "level", 500F));
		helper.assertTrue(e.watts == 100, "above the dial is held to 100, is " + e.watts);
		send(helper, evt("set_booster_level", "level", -9F));
		helper.assertTrue(e.watts == 1, "below the dial is held to 1, is " + e.watts);
		send(helper, evt("set_booster_level", "level", 55F));
		send(helper, evt("set_booster_level", "level", Float.NaN));
		helper.assertTrue(e.watts == 55, "NaN changes nothing, is " + e.watts);
		send(helper, evt("set_booster_active", "active", 1F));
		helper.assertTrue(e.isOn, "active 1 turns it on");
		same(helper, 1, val(c, "active"), "active reads 1");
		send(helper, evt("set_booster_active", "active", 0F));
		helper.assertTrue(!e.isOn, "and 0 off");
		e.prev = 99;
		same(helper, 99, val(c, "emitted"), "emitted is the last shot");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theReceiverTakesAnAbsorberLevelInPercentWithinZeroToOne(GameTestHelper helper) {
		BlockEntity be = dfc(helper, AT, ModBlocks.dfc_receiver.get());
		helper.assertTrue(be instanceof LeafiaCoreReceiverTE, "the addon receiver, got " + be);
		LeafiaCoreReceiverTE r = (LeafiaCoreReceiverTE) be;
		IControllable c = controllable(helper, r);
		assertKeys(helper, c, "the receiver", "level", "stress", "received", "power");
		assertIn(helper, c, "the receiver", "set_absorber_level");
		send(helper, evt("set_absorber_level", "level", 40F));
		same(helper, 0.4, r.absorbLevel, "level 40 percent is 0.4");
		same(helper, 40, val(c, "level"), "level reads back in percent");
		send(helper, evt("set_absorber_level", "level", 250F));
		same(helper, 1, r.absorbLevel, "above 100 percent is held to 1");
		send(helper, evt("set_absorber_level", "level", -5F));
		same(helper, 0, r.absorbLevel, "below zero is held to 0");
		send(helper, evt("set_absorber_level", "level", 30F));
		send(helper, evt("set_absorber_level", "level", Float.NaN));
		same(helper, 0.3, r.absorbLevel, "NaN changes nothing");
		r.destructionLevel = 150;
		r.syncJoules = 4242;
		same(helper, 50, val(c, "stress"), "stress is a percent of 300");
		same(helper, 4242, val(c, "received"), "received is the joules the tick paid");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theStabilizerTakesALevelWithinItsDial(GameTestHelper helper) {
		BlockEntity be = dfc(helper, AT, ModBlocks.dfc_stabilizer.get());
		helper.assertTrue(be instanceof LeafiaCoreStabilizerTE, "the addon stabilizer, got " + be);
		LeafiaCoreStabilizerTE s = (LeafiaCoreStabilizerTE) be;
		IControllable c = controllable(helper, s);
		assertKeys(helper, c, "the stabilizer", "active", "level", "lens_health", "core_temp", "core_stabilization", "core_energy", "core_expel",
				"core_potent", "core_collapse");
		assertIn(helper, c, "the stabilizer", "set_stabilizer_level");
		send(helper, evt("set_stabilizer_level", "level", 33F));
		helper.assertTrue(s.watts == 33, "level 33 sets the dial, is " + s.watts);
		same(helper, 33, val(c, "level"), "level reads back");
		send(helper, evt("set_stabilizer_level", "level", 900F));
		helper.assertTrue(s.watts == 100, "above the dial is held to 100, is " + s.watts);
		send(helper, evt("set_stabilizer_level", "level", 0F));
		helper.assertTrue(s.watts == 1, "below the dial is held to 1, is " + s.watts);
		send(helper, evt("set_stabilizer_level", "level", Float.NaN));
		helper.assertTrue(s.watts == 1, "NaN changes nothing, is " + s.watts);
		same(helper, 0, val(c, "lens_health"), "no lens reads 0");
		same(helper, 0, val(c, "core_temp"), "no core in reach reads 0");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void thePulserTakesActiveAndDetonationOnlyBehindItsCode(GameTestHelper helper) {
		helper.setBlock(AT, LeafiaBlocks.dfc_pulser.get().defaultBlockState().setValue(CoreComponent.FACING, Direction.EAST));
		CoreDetonatorTE p = (CoreDetonatorTE) helper.getBlockEntity(AT);
		IControllable c = controllable(helper, p);
		assertKeys(helper, c, "the pulser", "active", "charging", "timeRemaining");
		assertIn(helper, c, "the pulser", "set_pulser_active", "set_pulser_detonation");
		helper.assertTrue(p.tryCode("1234"), "the first code typed is kept");
		same(helper, 30, val(c, "timeRemaining"), "no core: the full thirty seconds");
		same(helper, 1, val(c, "active"), "a fresh pulser is on");
		send(helper, ControlEvent.newEvent("set_pulser_active").setVar("active", 0F).setVar("code", "wrong"));
		helper.assertTrue(p.isOn, "a wrong code changes nothing");
		send(helper, ControlEvent.newEvent("set_pulser_active").setVar("active", 0F).setVar("code", "1234"));
		helper.assertTrue(!p.isOn, "the right code switches it off");
		same(helper, 0, val(c, "active"), "active reads 0");
		send(helper, ControlEvent.newEvent("set_pulser_active").setVar("active", 1F).setVar("code", "1234"));
		helper.assertTrue(p.isOn, "and on");
		send(helper, ControlEvent.newEvent("set_pulser_detonation").setVar("charge", 1F).setVar("code", "1234"));
		helper.assertTrue(p.isOn, "a detonation request with no core in reach is harmless");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the LFTR

	@GameTest(template = EMPTY)
	public static void everyMsrBlockAnswersAmountCapacityAndTemperature(GameTestHelper helper) {
		if(!LeafiaFeatures.lftrReactor() || !LeafiaFF.enabled() || LeafiaFluids.FLUORIDE == null) {
			helper.succeed();
			return;
		}
		helper.setBlock(AT, LeafiaBlocks.msr_arbitrary.get());
		MSRArbitraryTE msr = (MSRArbitraryTE) helper.getBlockEntity(AT);
		IControllable c = controllable(helper, msr);
		assertKeys(helper, c, "an MSR block", "amount", "capacity", "temperature");
		assertIn(helper, c, "an MSR block");
		same(helper, 0, val(c, "amount"), "empty");
		same(helper, 1000, val(c, "capacity"), "the tank holds 1000");
		same(helper, 20, val(c, "temperature"), "an empty tank reads 20");
		FluidStack salt = LeafiaFF.toFF(LeafiaFluids.FLUORIDE, 600);
		var tag = MSRTEBase.nbtProtocol((net.minecraft.nbt.CompoundTag) null);
		tag.putDouble("heat", 150);
		LeafiaFF.setTag(salt, tag);
		msr.tank.setFluid(salt);
		same(helper, 600, val(c, "amount"), "amount");
		same(helper, LeafiaFluids.FLUORIDE.temperature + 150, val(c, "temperature"), "the fluid's base temperature plus the heat it carries");
		msr.tank.setFluid(FluidStack.EMPTY);
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	@GameTest(template = BIG)
	public static void theMsrControlRodTakesALevelInPercentOfItsLength(GameTestHelper helper) {
		if(!LeafiaFeatures.lftrReactor() || !LeafiaFF.enabled() || LeafiaFluids.FLUORIDE == null) {
			helper.succeed();
			return;
		}
		BlockPos rod = new BlockPos(6, 3, 8);
		helper.setBlock(rod, LeafiaBlocks.msr_control.get().defaultBlockState().setValue(MSRControlBlock.FACING, Direction.EAST));
		helper.setBlock(rod.west(), LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		helper.setBlock(rod.west(2), LeafiaBlocks.msr_control_extension.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		MSRControlTE control = (MSRControlTE) helper.getBlockEntity(rod);
		IControllable c = controllable(helper, control);
		assertKeys(helper, c, "the MSR control rod", "level");
		assertIn(helper, c, "the MSR control rod", "lftr_ctrl_set_level");
		control.length = 2;
		control.insertion = 2;
		same(helper, 0, val(c, "level"), "fully inserted reads 0 percent");
		send(helper, rod, evt("lftr_ctrl_set_level", "level", 50F));
		same(helper, 1, control.targetInsertion, "50 percent out of a two-block rod targets insertion 1");
		send(helper, rod, evt("lftr_ctrl_set_level", "level", 100F));
		same(helper, 0, control.targetInsertion, "100 percent is fully out");
		send(helper, rod, evt("lftr_ctrl_set_level", "level", 4000F));
		same(helper, 0, control.targetInsertion, "above 100 is held to fully out");
		send(helper, rod, evt("lftr_ctrl_set_level", "level", -40F));
		same(helper, 2, control.targetInsertion, "below 0 is held to fully in");
		send(helper, rod, evt("lftr_ctrl_set_level", "level", 25F));
		send(helper, rod, evt("lftr_ctrl_set_level", "level", Float.NaN));
		same(helper, 1.5, control.targetInsertion, "NaN changes nothing");
		control.insertion = 0.5;
		same(helper, 75, val(c, "level"), "level is how far out it is");
		helper.setBlock(rod.west(2), Blocks.AIR);
		helper.setBlock(rod.west(), Blocks.AIR);
		helper.setBlock(rod, Blocks.AIR);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the PWR

	private static void box(GameTestHelper helper, Block block) {
		for(int x = 3; x <= 9; x++)
			for(int y = 2; y <= 7; y++)
				for(int z = 2; z <= 8; z++)
					helper.setBlock(new BlockPos(x, y, z), block);
	}

	private static BlockEntity be(GameTestHelper helper, BlockPos pos) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	private static void teardown(GameTestHelper helper) {
		for(int x = 3; x <= 9; x++)
			for(int y = 2; y <= 7; y++)
				for(int z = 2; z <= 8; z++)
					if(be(helper, new BlockPos(x, y, z)) instanceof PWRElementTE element)
						element.inventory.setStackInSlot(0, ItemStack.EMPTY);
		box(helper, Blocks.AIR);
		PWRDiagnosis.preventScan.clear();
	}

	private static PWRData pwrRig(GameTestHelper helper, BlockPos e1, BlockPos mid, BlockPos e2, BlockPos port) {
		box(helper, LeafiaBlocks.lwr_casing.get());
		helper.setBlock(e1, LeafiaBlocks.lwr_element.get());
		helper.setBlock(mid, LeafiaBlocks.lwr_control.get());
		helper.setBlock(e2, LeafiaBlocks.lwr_element.get());
		helper.setBlock(port, LeafiaBlocks.lwr_signal.get());
		PWRDiagnosis.preventScan.clear();
		BlockPos abs = helper.absolutePos(e1);
		((PWRComponentBlock) helper.getBlockState(e1).getBlock()).beginDiagnosis(helper.getLevel(), abs, abs);
		PWRData found = null;
		for(BlockPos pos : new BlockPos[] {e1, mid, e2, port})
			if(be(helper, pos) instanceof PWRComponentEntity entity && entity.getCore() != null)
				found = entity.getCore();
		helper.assertTrue(found != null, "the rig formed a reactor");
		return found;
	}

	@GameTest(template = BIG)
	public static void thePwrControlRodTakesALevelAndTheComputerTakesMasterAndGroupLevels(GameTestHelper helper) {
		BlockPos e1 = new BlockPos(5, 4, 5), mid = new BlockPos(6, 4, 5), e2 = new BlockPos(7, 4, 5), port = new BlockPos(6, 5, 5);
		PWRData data = pwrRig(helper, e1, mid, e2, port);
		PWRControlTE rod = (PWRControlTE) be(helper, mid);
		PWRComputerTE computer = (PWRComputerTE) be(helper, port);
		PWRElementTE element = (PWRElementTE) be(helper, e1);

		IControllable rc = controllable(helper, rod);
		assertKeys(helper, rc, "the PWR control rod", "level");
		assertIn(helper, rc, "the PWR control rod", "pwr_ctrl_set_level");
		rod.position = 0.25;
		same(helper, 25, val(rc, "level"), "the rod's position in percent");
		send(helper, mid, evt("pwr_ctrl_set_level", "level", 60F));
		same(helper, 0.6, rod.targetPosition, "60 percent");
		send(helper, mid, evt("pwr_ctrl_set_level", "level", 700F));
		same(helper, 1, rod.targetPosition, "above 100 is held to 1");
		send(helper, mid, evt("pwr_ctrl_set_level", "level", -3F));
		same(helper, 0, rod.targetPosition, "below 0 is held to 0");
		send(helper, mid, evt("pwr_ctrl_set_level", "level", 20F));
		send(helper, mid, evt("pwr_ctrl_set_level", "level", Float.NaN));
		same(helper, 0.2, rod.targetPosition, "NaN changes nothing");

		IControllable cc = controllable(helper, computer);
		assertKeys(helper, cc, "the PWR control port", "master_level", "temp_lowest", "temp_highest", "temp_average", "tank_coolant",
				"tank_hotcoolant", "tank_e_buffer", "capacity_coolant", "capacity_hotcoolant", "capacity_e_buffer");
		assertIn(helper, cc, "the PWR control port", "pwr_computer_set_master", "pwr_computer_set_level");
		send(helper, port, evt("pwr_computer_set_master", "level", 50F));
		same(helper, 50, computer.getMasterControl(), "master 50");
		same(helper, 50, val(cc, "master_level"), "master_level reads back");
		send(helper, port, evt("pwr_computer_set_master", "level", 900F));
		same(helper, 100, computer.getMasterControl(), "above 100 is held to 100");
		send(helper, port, evt("pwr_computer_set_master", "level", Float.NaN));
		same(helper, 100, computer.getMasterControl(), "NaN changes nothing");
		send(helper, port, ControlEvent.newEvent("pwr_computer_set_level").setVar("name", "no such group").setVar("level", 50F));
		helper.assertTrue(!data.controlDemand.containsKey("no such group"), "a group no rod carries is ignored");
		send(helper, port, ControlEvent.newEvent("pwr_computer_set_level").setVar("name", PWRControlTE.defaultName).setVar("level", 80F));
		same(helper, 80, computer.getControl(PWRControlTE.defaultName), "a named group takes its level");
		send(helper, port, ControlEvent.newEvent("pwr_computer_set_level").setVar("name", PWRControlTE.defaultName).setVar("level", 800F));
		same(helper, 100, computer.getControl(PWRControlTE.defaultName), "and is held to 100");
		data.tanks[0].setFill(1234);
		same(helper, 1234, val(cc, "tank_coolant"), "tank_coolant");
		same(helper, data.tanks[0].getMaxFill(), val(cc, "capacity_coolant"), "capacity_coolant");

		IControllable ec = controllable(helper, element);
		assertKeys(helper, ec, "a PWR fuel column", "temperature", "meltingPoint", "depletion");
		assertIn(helper, ec, "a PWR fuel column");
		same(helper, 20, val(ec, "temperature"), "no rod reads 20");
		same(helper, 0, val(ec, "meltingPoint"), "and no melting point");
		same(helper, 100, val(ec, "depletion"), "and full");
		teardown(helper);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the modular turbine

	@GameTest(template = BIG)
	public static void theModularTurbineCoreAnswersRpsTurbulenceAndGenerated(GameTestHelper helper) {
		BlockPos core = new BlockPos(4, 1, 4);
		helper.setBlock(core, LeafiaBlocks.modular_turbine_core.get().defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.SOUTH));
		MTCoreTE te = (MTCoreTE) helper.getBlockEntity(core);
		IControllable c = controllable(helper, te);
		assertKeys(helper, c, "the turbine core", "rps", "turbulence", "generated");
		assertIn(helper, c, "the turbine core");
		te.rps = 12.5;
		te.turbulence = 7.5;
		te.generatedPerSec = 98_765L;
		same(helper, 12.5, val(c, "rps"), "rps");
		same(helper, 7.5, val(c, "turbulence"), "turbulence");
		same(helper, 98_765, val(c, "generated"), "generated is the sum over twenty ticks, upstream's generatedPerSec");
		helper.setBlock(core, Blocks.AIR);
		helper.succeed();
	}

	// ------------------------------------------------------------------ ZIRNOX

	@GameTest(template = EMPTY)
	public static void theZirnoxTakesARodTargetWithinZeroToOneHundred(GameTestHelper helper) {
		boolean was = LeafiaFeatures.zirnoxOverhaul;
		LeafiaFeatures.zirnoxOverhaul = true;
		try {
			helper.setBlock(AT, ModBlocks.reactor_zirnox.get().defaultBlockState()
					.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH));
		} finally {
			LeafiaFeatures.zirnoxOverhaul = was;
		}
		BlockEntity be = helper.getBlockEntity(AT);
		helper.assertTrue(be instanceof LeafiaZirnoxTE, "the addon reactor, got " + be);
		LeafiaZirnoxTE z = (LeafiaZirnoxTE) be;
		IControllable c = controllable(helper, z);
		assertKeys(helper, c, "the ZIRNOX", "temperature", "pressure", "water", "steam", "compression", "co2");
		assertIn(helper, c, "the ZIRNOX", "zirnox_ctrl_set_level");
		send(helper, evt("zirnox_ctrl_set_level", "level", 40F));
		helper.assertTrue(z.rodsTarget == 40, "40 sets the target, is " + z.rodsTarget);
		send(helper, evt("zirnox_ctrl_set_level", "level", 1000F));
		helper.assertTrue(z.rodsTarget == 100, "above 100 is held to 100, is " + z.rodsTarget);
		send(helper, evt("zirnox_ctrl_set_level", "level", -50F));
		helper.assertTrue(z.rodsTarget == 0, "below 0 is held to 0, is " + z.rodsTarget);
		send(helper, evt("zirnox_ctrl_set_level", "level", 30F));
		send(helper, evt("zirnox_ctrl_set_level", "level", Float.NaN));
		helper.assertTrue(z.rodsTarget == 30, "NaN changes nothing, is " + z.rodsTarget);
		z.hulltemp = 321.5;
		z.compression = 2;
		same(helper, 321.5, val(c, "temperature"), "temperature is the hull's");
		same(helper, 100, val(c, "compression"), "compression is ten to the setting");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the addon duct gauge

	@GameTest(template = EMPTY)
	public static void theAddonDuctGaugeAnswersPerSecSmooth(GameTestHelper helper) {
		helper.setBlock(AT, LeafiaBlocks.fluid_duct_gauge_mdl.get());
		FluidDuctGaugeTE gauge = (FluidDuctGaugeTE) helper.getBlockEntity(AT);
		IControllable c = controllable(helper, gauge);
		assertKeys(helper, c, "the duct gauge", "perSecSmooth");
		assertIn(helper, c, "the duct gauge");
		gauge.fillPerSec = 4321;
		same(helper, 4321, val(c, "perSecSmooth"), "perSecSmooth is the twenty-tick sum");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the sirens

	@GameTest(template = EMPTY)
	public static void theOverhauledSirenTakesSirenSetState(GameTestHelper helper) {
		boolean was = LeafiaFeatures.sirenOverhaul;
		LeafiaFeatures.sirenOverhaul = true;
		try {
			helper.setBlock(AT, ModBlocks.machine_siren.get());
		} finally {
			LeafiaFeatures.sirenOverhaul = was;
		}
		BlockEntity be = helper.getBlockEntity(AT);
		helper.assertTrue(be instanceof LeafiaSirenTE, "the addon siren, got " + be);
		LeafiaSirenTE siren = (LeafiaSirenTE) be;
		IControllable c = controllable(helper, siren);
		assertKeys(helper, c, "the overhauled siren");
		assertIn(helper, c, "the overhauled siren", "siren_set_state");
		helper.assertTrue(!siren.ctrlActive && !siren.isActive(), "quiet to begin with");
		send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", true));
		helper.assertTrue(siren.ctrlActive && siren.isActive(), "siren_set_state on drives ctrlActive");
		send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", false));
		helper.assertTrue(!siren.ctrlActive && !siren.isActive(), "and off");
		helper.setBlock(AT, Blocks.AIR);
		helper.succeed();
	}

	private static boolean lastActive(BlockEntityMachineSiren siren) {
		try {
			Field f = BlockEntityMachineSiren.class.getDeclaredField("lastActive");
			f.setAccessible(true);
			return f.getBoolean(siren);
		} catch(ReflectiveOperationException e) {
			throw new AssertionError("the siren no longer keeps lastActive, so the test cannot see what it announced", e);
		}
	}

	/** H-CCP-2: the base siren sounds when a panel event arrives, with no redstone, through the live ticker. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theBaseSirenSoundsViaAPanelEvent(GameTestHelper helper) {
		helper.setBlock(AT, ModBlocks.machine_siren.get());
		BlockEntity be = helper.getBlockEntity(AT);
		helper.assertTrue(be != null && be.getClass() == BlockEntityMachineSiren.class, "the base siren, got " + be);
		BlockEntityMachineSiren siren = (BlockEntityMachineSiren) be;
		ItemStack tape = new ItemStack(ModItems.siren_track.get());
		ItemCassette.setTrack(tape, TrackType.HATCH);
		siren.slots[0] = tape;
		TickWindow.sequence(helper).thenIdle(2).thenExecute(() -> {
			helper.assertTrue(!lastActive(siren), "silent with a cassette and no signal");
			send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", true));
			helper.assertTrue(siren.ctrlActive, "the event set ctrlActive");
		}).thenIdle(2).thenExecute(() -> {
			helper.assertTrue(lastActive(siren), "and the next tick the siren announced itself playing, with no redstone anywhere");
			send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", false));
		}).thenIdle(2).thenExecute(() -> {
			helper.assertTrue(!lastActive(siren), "and silence once the panel says off");
			helper.setBlock(AT, Blocks.AIR);
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ the reactor door

	@GameTest(template = BIG, timeoutTicks = TickWindow.TIMEOUT)
	public static void theReactorDoorTakesDoorToggleAndAnnouncesWhenItFinishes(GameTestHelper helper) {
		if(!LeafiaFeatures.doors) {
			helper.succeed();
			return;
		}
		var block = LeafiaBlocks.reactor_door.get();
		BlockPos core = helper.absolutePos(new BlockPos(8, 1, 8));
		helper.getLevel().setBlock(core, block.defaultBlockState()
				.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE).setValue(BlockDummyable.DIR, Direction.NORTH), 3);
		com.hbm.handler.MultiblockHandlerXR.fillSpace(helper.getLevel(), core, AddonDoorDecl.REACTOR_DOOR.getDimensions(), block, Direction.NORTH);
		ReactorDoorTE door = (ReactorDoorTE) helper.getLevel().getBlockEntity(core);
		IControllable c = controllable(helper, door);
		helper.assertTrue(c.getInEvents().equals(List.of("door_toggle")) && c.getOutEvents().equals(List.of("door_open_state")),
				"in door_toggle, out door_open_state, got " + c.getInEvents() + " / " + c.getOutEvents());
		assertKeys(helper, c, "the reactor door");

		Recorder listener = new Recorder(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel(), "door_open_state");
		sys(helper).addControllable(listener);
		sys(helper).subscribeTo(listener, core);
		try {
			ReactorDoorTE.tick(helper.getLevel(), core, helper.getLevel().getBlockState(core), door);
			helper.assertTrue(door.state == ReactorDoorTE.STATE_CLOSED, "closed to begin with");
			sys(helper).broadcastEvent(helper.absolutePos(new BlockPos(1, 1, 1)), ControlEvent.newEvent("door_toggle").setVar("passcode", 0F), core);
			helper.assertTrue(door.state == ReactorDoorTE.STATE_OPENING, "door_toggle starts it opening, state is " + door.state);
			helper.assertTrue(listener.received.isEmpty(), "nothing is announced until it has finished");
			int guard = 0;
			while(door.state != ReactorDoorTE.STATE_OPEN && guard++ < 600)
				ReactorDoorTE.tick(helper.getLevel(), core, helper.getLevel().getBlockState(core), door);
			helper.assertTrue(door.state == ReactorDoorTE.STATE_OPEN, "it finished opening");
			helper.assertTrue(listener.received.size() == 1 && listener.received.get(0).event().name.equals("door_open_state")
					&& listener.received.get(0).event().vars.get("state").getNumber() == 1F,
					"it announced open (1) once, got " + listener.received.size());
			sys(helper).broadcastEvent(helper.absolutePos(new BlockPos(1, 1, 1)), ControlEvent.newEvent("door_toggle").setVar("passcode", 0F), core);
			guard = 0;
			while(door.state != ReactorDoorTE.STATE_CLOSED && guard++ < 600)
				ReactorDoorTE.tick(helper.getLevel(), core, helper.getLevel().getBlockState(core), door);
			helper.assertTrue(listener.received.size() == 2 && listener.received.get(1).event().vars.get("state").getNumber() == 0F,
					"and closed (0), got " + listener.received.size());
		} finally {
			sys(helper).removeControllable(listener);
		}
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.AIR);
		helper.succeed();
	}
}
