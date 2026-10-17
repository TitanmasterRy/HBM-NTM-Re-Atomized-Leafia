package com.leafia.gametest;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.blockentity.machine.BlockEntityBlastDoor;
import com.hbm.blockentity.machine.BlockEntityHeaterElectric;
import com.hbm.blockentity.machine.BlockEntityHeaterFirebox;
import com.hbm.blockentity.machine.BlockEntityHeaterHeatex;
import com.hbm.blockentity.machine.BlockEntityHeaterOilburner;
import com.hbm.blockentity.machine.BlockEntityHeaterOven;
import com.hbm.blockentity.machine.BlockEntityMachineSiren;
import com.hbm.blockentity.machine.rbmk.BlockEntityCraneConsole;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBoiler;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlAuto;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlManual;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlManual.RBMKColor;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKCooler;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKHeater;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKOutgasser;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.blockentity.machine.storage.BlockEntityBarrel;
import com.hbm.blockentity.machine.storage.BlockEntityBatterySocket;
import com.hbm.blockentity.machine.storage.BlockEntityMachineFluidTank;
import com.hbm.blockentity.network.BlockEntityCableGauge;
import com.hbm.blockentity.network.BlockEntityFluidPump;
import com.hbm.blockentity.network.BlockEntityPipeGauge;
import com.hbm.blockentity.turret.BlockEntityTurretBaseNT;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.gametest.TickWindow;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemRBMKRod;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.reach.BaseReach;
import com.leafia.contents.machines.controlpanel.core.reach.BaseReachPoller;
import com.leafia.contents.machines.controlpanel.core.reach.CraneConsoleReach;
import com.leafia.contents.machines.controlpanel.core.reach.ReachAdapter;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Custom Control Panel's reach into the base mod (CCP-BASE-REACH, D-3068): one adapter per base machine NTM-CE and Leafia made
 * controllable, each pinned against a live base block entity, plus every in-event and the out-event poller.
 *
 * <p>What a test of an adapter must hold fixed is the <b>base machine's meaning of a field</b>, not the adapter's code: the adapter
 * compiles against public fields, so a rename already fails the build, and the quiet failure is master changing what a field
 * <i>means</i>. So every query test reads the key back through the capability from a block entity the test filled itself, and
 * every in-event test asserts the field the base reads, not a field of the adapter.
 *
 * <p>Out-events are polled at the end of a tick, so the tests that depend on the level tick open their window through
 * {@link TickWindow} (D-1262); the others step {@link BaseReachPoller#poll} by hand, which is exactly what the tick runs. Every
 * subscription a test makes is removed before it ends, because the level's system is shared (D-963).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelReachTests {

	private static final String EMPTY = "empty";

	private static final BlockPos AT = new BlockPos(2, 1, 2);

	// ------------------------------------------------------------------ plumbing

	private static BlockPos abs(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	/** Places a block's core (or the block itself) the way the base tests do, facing {@code dir}, and returns its block entity. */
	private static BlockEntity place(GameTestHelper helper, Block block, BlockPos rel, Direction dir) {
		helper.setBlock(rel, Blocks.AIR);
		BlockState state = block.defaultBlockState();
		if(state.hasProperty(BlockDummyable.PART)) state = state.setValue(BlockDummyable.PART, BlockDummyable.Part.CORE);
		if(state.hasProperty(BlockDummyable.DIR)) state = state.setValue(BlockDummyable.DIR, dir);
		helper.setBlock(rel, state);
		return helper.getBlockEntity(rel);
	}

	private static BlockEntity place(GameTestHelper helper, Block block) {
		return place(helper, block, AT, Direction.NORTH);
	}

	private static IControllable adapter(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getCapability(ControlEventSystem.CONTROLLABLE, abs(helper, rel));
	}

	private static IControllable adapter(GameTestHelper helper) {
		IControllable c = adapter(helper, AT);
		helper.assertTrue(c != null, "the capability answers at the machine");
		helper.assertTrue(c.getControlPos().equals(abs(helper, AT)) && c.getControlWorld() == helper.getLevel(), "and reports its block's position and level");
		return c;
	}

	private static ControlEventSystem sys(GameTestHelper helper) {
		return ControlEventSystem.get(helper.getLevel());
	}

	private static void send(GameTestHelper helper, ControlEvent evt) {
		sys(helper).broadcastEvent(abs(helper, new BlockPos(5, 1, 5)), evt, abs(helper, AT));
	}

	private static Set<String> keys(IControllable c) {
		return new TreeSet<>(c.getQueryData().keySet());
	}

	private static Set<String> set(String... s) {
		return new TreeSet<>(List.of(s));
	}

	private static float val(IControllable c, String key) {
		DataValue v = c.getQueryData().get(key);
		if(v == null) throw new AssertionError("no value for " + key + " in " + keys(c));
		return v.getNumber();
	}

	private static String text(IControllable c, String key) {
		DataValue v = c.getQueryData().get(key);
		if(v == null) throw new AssertionError("no value for " + key + " in " + keys(c));
		return v.toString();
	}

	private static void assertKeys(GameTestHelper helper, IControllable c, String what, String... expected) {
		helper.assertTrue(keys(c).equals(set(expected)), what + " keys are " + set(expected) + ", got " + keys(c));
	}

	private static void assertEvents(GameTestHelper helper, IControllable c, String what, List<String> in, List<String> out) {
		helper.assertTrue(c.getInEvents().equals(in), what + " in-events are " + in + ", got " + c.getInEvents());
		helper.assertTrue(c.getOutEvents().equals(out), what + " out-events are " + out + ", got " + c.getOutEvents());
	}

	private static void near(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < 1.0e-4, what + ": expected " + expected + ", was " + actual);
	}

	// ------------------------------------------------------------------ the registration

	/** 2 doors + 3 storage + 3 network + 5 heaters + 4 turbines + 13 turrets + 13 RBMK columns + the crane console. */
	private static final int TYPES = 2 + 3 + 3 + 5 + 4 + 13 + 13 + 1 + 1;

	@GameTest(template = EMPTY)
	public static void everyAdapterTypeIsRegisteredOnceAndSwitchedByTheFeature(GameTestHelper helper) {
		var types = BaseReach.registeredTypes();
		helper.assertTrue(types.size() == TYPES, TYPES + " base block entity types carry an adapter, got " + types.size());
		helper.assertTrue(new java.util.HashSet<>(types).size() == types.size(), "no type is registered twice");
		helper.assertTrue(com.leafia.config.LeafiaFeatures.controlPanel, "the feature is on, or nothing above would be registered");
		helper.succeed();
	}

	/** H-CCP-2 was taken by master (D-2275): the base siren has a public {@code ctrlActive}, and the adapter drives it (CCP-RETROFIT, D-3069). */
	@GameTest(template = EMPTY)
	public static void theBaseSirenIsDrivenByItsEventThroughCtrlActive(GameTestHelper helper) {
		BlockEntity be = place(helper, ModBlocks.machine_siren.get());
		helper.assertTrue(be != null && be.getClass() == BlockEntityMachineSiren.class,
				"the base siren, not the addon's overhaul (which implements IControllable itself), got " + be);
		BlockEntityMachineSiren siren = (BlockEntityMachineSiren) be;
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the base siren", List.of("siren_set_state"), List.of());
		assertKeys(helper, c, "the base siren");
		helper.assertTrue(!siren.ctrlActive && !siren.isActive(), "quiet to begin with");
		send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", true));
		helper.assertTrue(siren.ctrlActive && siren.isActive(), "siren_set_state on sets ctrlActive and the siren is active with no redstone");
		send(helper, ControlEvent.newEvent("siren_set_state").setVar("isOn", false));
		helper.assertTrue(!siren.ctrlActive && !siren.isActive(), "and off clears it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ doors

	@GameTest(template = EMPTY)
	public static void theGenericDoorsKeysAndEvents(GameTestHelper helper) {
		BlockEntityDoorGeneric door = (BlockEntityDoorGeneric) place(helper, ModBlocks.vault_door.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the door", List.of("door_toggle"), List.of("door_open_state"));
		assertKeys(helper, c, "the door (CE has no query data on a door)");
		helper.succeed();
	}

	/** The in-event: open and close on an unlocked door, and a locked one only for its pin. */
	@GameTest(template = EMPTY)
	public static void doorToggleDrivesTheGenericDoorAndRespectsItsLock(GameTestHelper helper) {
		BlockEntityDoorGeneric door = (BlockEntityDoorGeneric) place(helper, ModBlocks.vault_door.get());
		IControllable c = adapter(helper);

		send(helper, ControlEvent.newEvent("door_toggle"));
		helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_OPENING, "an unlocked door starts opening, state " + door.state);
		door.state = BlockEntityDoorGeneric.STATE_OPEN;
		send(helper, ControlEvent.newEvent("door_toggle"));
		helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_CLOSING, "and an open one starts closing, state " + door.state);
		door.state = BlockEntityDoorGeneric.STATE_CLOSED;

		door.setPins(1234);
		door.lock();
		helper.assertTrue(door.isLocked(), "a pin and the lock switch make it locked");
		send(helper, ControlEvent.newEvent("door_toggle").setVar("passcode", 1));
		send(helper, ControlEvent.newEvent("door_toggle"));
		helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_CLOSED, "a locked door ignores the wrong pin and the default 0, state " + door.state);
		send(helper, ControlEvent.newEvent("door_toggle").setVar("passcode", 1234));
		helper.assertTrue(door.state == BlockEntityDoorGeneric.STATE_OPENING, "and opens for its pin, state " + door.state);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBlastDoorsKeysEventsAndStateMapping(GameTestHelper helper) {
		BlockEntityBlastDoor door = (BlockEntityBlastDoor) place(helper, ModBlocks.blast_door.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the blast door", List.of("door_toggle"), List.of("door_open_state"));
		assertKeys(helper, c, "the blast door");

		var reach = (com.leafia.contents.machines.controlpanel.core.reach.DoorReach.Blast) c;
		helper.assertTrue(reach.ordinal() == 0, "closed is 0");
		send(helper, ControlEvent.newEvent("door_toggle"));
		helper.assertTrue(door.state == BlockEntityBlastDoor.STATE_TRANSITIONING && door.isOpening, "the event starts it opening");
		helper.assertTrue(((com.leafia.contents.machines.controlpanel.core.reach.DoorReach.Blast) adapter(helper)).ordinal() == 3, "opening is CE's 3");
		door.finishOpen();
		helper.assertTrue(((com.leafia.contents.machines.controlpanel.core.reach.DoorReach.Blast) adapter(helper)).ordinal() == 1, "open is CE's 1, not the base's 2");
		send(helper, ControlEvent.newEvent("door_toggle"));
		helper.assertTrue(!door.isOpening && door.state == BlockEntityBlastDoor.STATE_TRANSITIONING, "the event starts it closing");
		helper.assertTrue(((com.leafia.contents.machines.controlpanel.core.reach.DoorReach.Blast) adapter(helper)).ordinal() == 2, "closing is CE's 2");
		door.finishClose();
		helper.assertTrue(((com.leafia.contents.machines.controlpanel.core.reach.DoorReach.Blast) adapter(helper)).ordinal() == 0, "and closed again is 0");
		helper.succeed();
	}

	/** The blast door's lock: CE ignored it, the redstone path does not, so neither does the adapter (a recorded divergence). */
	@GameTest(template = EMPTY)
	public static void aLockedBlastDoorObeysOnlyItsPin(GameTestHelper helper) {
		BlockEntityBlastDoor door = (BlockEntityBlastDoor) place(helper, ModBlocks.blast_door.get());
		door.setPins(4321);
		door.lock();
		send(helper, ControlEvent.newEvent("door_toggle"));
		send(helper, ControlEvent.newEvent("door_toggle").setVar("passcode", 4320));
		helper.assertTrue(door.state == BlockEntityBlastDoor.STATE_CLOSED, "the wrong pin and the default 0 do nothing");
		send(helper, ControlEvent.newEvent("door_toggle").setVar("passcode", 4321));
		helper.assertTrue(door.state == BlockEntityBlastDoor.STATE_TRANSITIONING, "its pin opens it");
		helper.succeed();
	}

	/** Out-events by the real level tick: subscribe, let the poller take its baseline, change the door, hear the new state. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theDoorsStateChangeIsPolledAndAnnounced(GameTestHelper helper) {
		BlockEntityDoorGeneric door = (BlockEntityDoorGeneric) place(helper, ModBlocks.vault_door.get());
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "door_open_state");
		sys.subscribeTo(panel, pos);
		TickWindow.sequence(helper)
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(panel.count("door_open_state") == 0, "the baseline is taken silently, got " + panel.count("door_open_state"));
					helper.assertTrue(BaseReachPoller.watched(helper.getLevel()) >= 1, "the subscribed door is being watched");
					door.state = BlockEntityDoorGeneric.STATE_OPENING;
				})
				.thenIdle(2)
				.thenExecute(() -> {
					try {
						helper.assertTrue(panel.count("door_open_state") >= 1, "the change was announced at the end of a tick");
						float state = panel.received.get(0).event().vars.get("state").getNumber();
						helper.assertTrue(state == 3F, "carrying CE's ordinal for opening, got " + state);
						helper.assertTrue(panel.received.get(0).from().equals(pos), "from the door's position");
					} finally {
						sys.unsubscribeFrom(panel, pos);
					}
				})
				.thenSucceed();
	}

	/** The poller by hand: baseline, no change, change, and nothing watched once nobody is subscribed. */
	@GameTest(template = EMPTY)
	public static void thePollerSendsOnlyAChangeAndOnlyToSubscribers(GameTestHelper helper) {
		BlockEntityDoorGeneric door = (BlockEntityDoorGeneric) place(helper, ModBlocks.vault_door.get());
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "door_open_state");
		try {
			BaseReachPoller.poll(helper.getLevel());
			door.state = BlockEntityDoorGeneric.STATE_OPEN;
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.received.isEmpty(), "nothing is polled while nobody is subscribed");

			sys.subscribeTo(panel, pos);
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.received.isEmpty(), "the first poll is a baseline");
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.received.isEmpty(), "and an unchanged door says nothing");
			door.state = BlockEntityDoorGeneric.STATE_CLOSING;
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.received.size() == 1, "a change is one event, got " + panel.received.size());
			helper.assertTrue(panel.received.get(0).event().vars.get("state").getNumber() == 2F, "closing is 2");
			door.state = BlockEntityDoorGeneric.STATE_OPENING;
			door.state = BlockEntityDoorGeneric.STATE_CLOSING;
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.received.size() == 1, "two changes inside a tick that end where they began merge into none");
		} finally {
			sys.unsubscribeFrom(panel, pos);
		}
		BaseReachPoller.poll(helper.getLevel());
		helper.assertTrue(BaseReachPoller.watched(helper.getLevel()) == 0, "with nobody subscribed the baseline is dropped");
		helper.succeed();
	}

	// ------------------------------------------------------------------ storage

	@GameTest(template = EMPTY)
	public static void theFluidTanksKeysEventsAndModeBounds(GameTestHelper helper) {
		BlockEntityMachineFluidTank tank = (BlockEntityMachineFluidTank) place(helper, ModBlocks.machine_fluidtank.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the tank", List.of("tank_set_mode"), List.of("tank_set_mode"));
		assertKeys(helper, c, "an empty tank (no fluid type while it has none)", "t0_fluidAmount", "mode");

		tank.tank.setTankType(Fluids.WATER);
		tank.tank.setFill(12_345);
		tank.mode = 2;
		c = adapter(helper);
		assertKeys(helper, c, "a filled tank", "t0_fluidAmount", "t0_fluidType", "mode");
		helper.assertTrue(val(c, "t0_fluidAmount") == 12_345F && val(c, "mode") == 2F, "amount and mode are the tank's own");
		helper.assertTrue(text(c, "t0_fluidType").equals(Fluids.WATER.getConditionalName()), "the type is the fluid's translation key (the server cannot localise)");

		send(helper, ControlEvent.newEvent("tank_set_mode").setVar("mode", 3));
		helper.assertTrue(tank.mode == 3, "mode 3 is set");
		send(helper, ControlEvent.newEvent("tank_set_mode").setVar("mode", 6));
		helper.assertTrue(tank.mode == 2, "a mode past the count wraps into it, as CE's remainder did, got " + tank.mode);
		send(helper, ControlEvent.newEvent("tank_set_mode").setVar("mode", -1));
		helper.assertTrue(tank.mode == 3, "a negative one lands in range (CE's left it negative), got " + tank.mode);
		send(helper, ControlEvent.newEvent("tank_set_mode").setVar("mode", Float.NaN));
		helper.assertTrue(tank.mode == 0, "NaN is 0, got " + tank.mode);
		helper.succeed();
	}

	/** The echo goes out at once, once; a GUI change is found by the poller. */
	@GameTest(template = EMPTY)
	public static void theTankEchoesItsModeOnceAndThePollerFindsAGuiChange(GameTestHelper helper) {
		BlockEntityMachineFluidTank tank = (BlockEntityMachineFluidTank) place(helper, ModBlocks.machine_fluidtank.get());
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "tank_set_mode");
		sys.subscribeTo(panel, pos);
		try {
			BaseReachPoller.poll(helper.getLevel());
			send(helper, ControlEvent.newEvent("tank_set_mode").setVar("mode", 1));
			helper.assertTrue(panel.count("tank_set_mode") == 1, "the echo is immediate, as CE's, got " + panel.count("tank_set_mode"));
			helper.assertTrue(panel.received.get(0).event().vars.get("mode").getNumber() == 1F, "carrying the new mode");
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("tank_set_mode") == 1, "the poll at the end of the tick does not repeat it");

			CompoundTag button = new CompoundTag();
			button.putBoolean("mode", true);
			tank.receiveControl(button);
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("tank_set_mode") == 2, "the screen's mode button is found by the poll");
			helper.assertTrue(panel.received.get(1).event().vars.get("mode").getNumber() == 2F, "carrying mode 2");
		} finally {
			sys.unsubscribeFrom(panel, pos);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBarrelsKeysAndEvent(GameTestHelper helper) {
		BlockEntityBarrel barrel = (BlockEntityBarrel) place(helper, ModBlocks.barrel_steel.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the barrel", List.of("setIOMode"), List.of());
		assertKeys(helper, c, "the barrel", "fill", "maxFill", "ioMode");
		barrel.tank.setTankType(Fluids.WATER);
		barrel.tank.setFill(4_000);
		barrel.mode = 1;
		c = adapter(helper);
		helper.assertTrue(val(c, "fill") == 4_000F && val(c, "maxFill") == (float) barrel.tank.getMaxFill() && val(c, "ioMode") == 1F, "the barrel's own tank and mode");

		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", 2));
		helper.assertTrue(barrel.mode == 2, "mode 2");
		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", 99));
		helper.assertTrue(barrel.mode == 3, "clamped to 3, got " + barrel.mode);
		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", -5));
		helper.assertTrue(barrel.mode == 0, "clamped to 0, got " + barrel.mode);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBatterySocketsKeysAndEvent(GameTestHelper helper) {
		BlockEntityBatterySocket socket = (BlockEntityBatterySocket) place(helper, ModBlocks.machine_battery_socket.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the socket", List.of("setIOMode"), List.of());
		assertKeys(helper, c, "the socket", "charge", "maxCharge", "ioMode");
		helper.assertTrue(val(c, "charge") == (float) socket.getPower() && val(c, "maxCharge") == (float) socket.getMaxPower(), "charge and capacity come from the socket");

		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", 3));
		helper.assertTrue(socket.redLow == 3, "sets the mode a socket has without redstone, got " + socket.redLow);
		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", 42));
		helper.assertTrue(socket.redLow == 3, "clamped to 3, got " + socket.redLow);
		send(helper, ControlEvent.newEvent("setIOMode").setVar("mode", -1));
		helper.assertTrue(socket.redLow == 0, "clamped to 0, got " + socket.redLow);
		helper.assertTrue(val(adapter(helper), "ioMode") == (float) socket.getRelevantMode(true), "ioMode is the relevant mode");
		helper.succeed();
	}

	// ------------------------------------------------------------------ network

	@GameTest(template = EMPTY)
	public static void theGaugesKeys(GameTestHelper helper) {
		place(helper, ModBlocks.red_cable_gauge.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the cable gauge", List.of(), List.of());
		assertKeys(helper, c, "the cable gauge", "perSecond", "perTick");
		BlockEntityCableGauge cable = (BlockEntityCableGauge) helper.getBlockEntity(AT);
		helper.assertTrue(val(c, "perTick") == (float) cable.getDeltaTick() && val(c, "perSecond") == (float) cable.getDeltaLastSecond(), "read through the base's getters");

		place(helper, ModBlocks.fluid_duct_gauge.get());
		c = adapter(helper);
		assertKeys(helper, c, "the duct gauge", "perSecond", "perTick");
		BlockEntityPipeGauge pipe = (BlockEntityPipeGauge) helper.getBlockEntity(AT);
		helper.assertTrue(val(c, "perTick") == (float) pipe.getDeltaTick() && val(c, "perSecond") == (float) pipe.getDeltaLastSecond(), "read through the base's getters");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theFluidPumpsKeysAndEventBounds(GameTestHelper helper) {
		BlockEntityFluidPump pump = (BlockEntityFluidPump) place(helper, ModBlocks.fluid_pump.get());
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the pump", List.of("set_pump_buffer", "set_pump_pressure", "set_pump_priority"), List.of());
		assertKeys(helper, c, "the pump", "fill");
		pump.tank[0].setTankType(Fluids.WATER);
		pump.tank[0].setFill(77);
		helper.assertTrue(val(adapter(helper), "fill") == 77F, "fill is the tank's");

		send(helper, ControlEvent.newEvent("set_pump_buffer").setVar("size", 5_000));
		helper.assertTrue(pump.bufferSize == 5_000, "buffer 5000");
		send(helper, ControlEvent.newEvent("set_pump_buffer").setVar("size", 1_000_000));
		helper.assertTrue(pump.bufferSize == 10_000, "buffer clamped to the packet path's 10,000, got " + pump.bufferSize);
		send(helper, ControlEvent.newEvent("set_pump_buffer").setVar("size", -4));
		helper.assertTrue(pump.bufferSize == 0, "and to 0");

		send(helper, ControlEvent.newEvent("set_pump_pressure").setVar("pu", 3));
		helper.assertTrue(pump.tank[0].getPressure() == 3, "pressure 3");
		send(helper, ControlEvent.newEvent("set_pump_pressure").setVar("pu", 40));
		helper.assertTrue(pump.tank[0].getPressure() == 5, "pressure clamped to 5, got " + pump.tank[0].getPressure());
		send(helper, ControlEvent.newEvent("set_pump_pressure").setVar("pu", -2));
		helper.assertTrue(pump.tank[0].getPressure() == 0, "and to 0");

		api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority[] all = api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority.values();
		send(helper, ControlEvent.newEvent("set_pump_priority").setVar("priority", 4));
		helper.assertTrue(pump.priority == all[4], "priority by ordinal");
		send(helper, ControlEvent.newEvent("set_pump_priority").setVar("priority", 400));
		helper.assertTrue(pump.priority == all[all.length - 1], "clamped to the last");
		send(helper, ControlEvent.newEvent("set_pump_priority").setVar("priority", -1));
		helper.assertTrue(pump.priority == all[0], "and to the first");
		helper.succeed();
	}

	// ------------------------------------------------------------------ heaters

	@GameTest(template = EMPTY)
	public static void theElectricHeaterKeysAndHeaterSet(GameTestHelper helper) {
		BlockEntityHeaterElectric heater = (BlockEntityHeaterElectric) place(helper, ModBlocks.heater_electric.get());
		heater.heatEnergy = 4_321;
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the electric heater", List.of("heater_set"), List.of());
		assertKeys(helper, c, "the electric heater", "heat", "maxHeat");
		helper.assertTrue(val(c, "heat") == 4_321F && val(c, "maxHeat") == 2147483647F, "heat is the buffer, maxHeat Leafia's 2147483647");

		send(helper, ControlEvent.newEvent("heater_set").setVar("heatPerTick", 250));
		helper.assertTrue(heater.setting == 3, "250 per tick is ceil(2.5) = 3 steps, got " + heater.setting);
		send(helper, ControlEvent.newEvent("heater_set").setVar("heatPerTick", 300));
		helper.assertTrue(heater.setting == 3, "300 is exactly 3");
		send(helper, ControlEvent.newEvent("heater_set").setVar("heatPerTick", 99_999));
		helper.assertTrue(heater.setting == 10, "clamped to the dial's 10, got " + heater.setting);
		send(helper, ControlEvent.newEvent("heater_set").setVar("heatPerTick", -50));
		helper.assertTrue(heater.setting == 0, "and to 0, got " + heater.setting);
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOtherHeatersKeys(GameTestHelper helper) {
		BlockEntityHeaterHeatex heatex = (BlockEntityHeaterHeatex) place(helper, ModBlocks.heater_heatex.get());
		heatex.heatEnergy = 99;
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the heat exchanger", List.of(), List.of());
		assertKeys(helper, c, "the heat exchanger", "heat", "maxHeat");
		helper.assertTrue(val(c, "heat") == 99F && val(c, "maxHeat") == 2147483647F, "heat and Leafia's 2147483647");

		BlockEntityHeaterFirebox firebox = (BlockEntityHeaterFirebox) place(helper, ModBlocks.heater_firebox.get());
		firebox.heatEnergy = 1_000;
		c = adapter(helper);
		assertEvents(helper, c, "the firebox", List.of(), List.of());
		assertKeys(helper, c, "the firebox", "heat", "maxHeat");
		helper.assertTrue(val(c, "heat") == 1_000F && val(c, "maxHeat") == (float) BlockEntityHeaterFirebox.maxHeatEnergy, "the firebox's own cap");

		BlockEntityHeaterOven oven = (BlockEntityHeaterOven) place(helper, ModBlocks.heater_oven.get());
		oven.heatEnergy = 2_000;
		c = adapter(helper);
		assertKeys(helper, c, "the oven", "heat", "maxHeat");
		helper.assertTrue(val(c, "heat") == 2_000F && val(c, "maxHeat") == (float) BlockEntityHeaterOven.maxHeatEnergy, "the oven's own cap");
		helper.assertTrue(BlockEntityHeaterOven.maxHeatEnergy != BlockEntityHeaterFirebox.maxHeatEnergy, "and the two caps differ, so this reads the right class");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theOilBurnerKeysAndBurnerSet(GameTestHelper helper) {
		BlockEntityHeaterOilburner burner = (BlockEntityHeaterOilburner) place(helper, ModBlocks.heater_oilburner.get());
		burner.heatEnergy = 5_000;
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the oil burner", List.of("burner_set"), List.of());
		assertKeys(helper, c, "the oil burner", "heat", "maxHeat");
		helper.assertTrue(val(c, "heat") == 5_000F && val(c, "maxHeat") == 100_000F, "heat and the 100,000 cap");

		send(helper, ControlEvent.newEvent("burner_set").setVar("isOn", true));
		helper.assertTrue(burner.isOn, "switched on");
		send(helper, ControlEvent.newEvent("burner_set").setVar("isOn", false));
		helper.assertTrue(!burner.isOn, "and off");
		helper.succeed();
	}

	// ------------------------------------------------------------------ turbines

	/** The four turbines are controllable targets; the industrial turbine and the Chungus have no generation seam and so no key, above all no reassuring 0. */
	@GameTest(template = EMPTY)
	public static void theTurbinesWithoutASeamOmitGenerated(GameTestHelper helper) {
		Block[] turbines = { ModBlocks.machine_industrial_turbine.get(), ModBlocks.machine_chungus.get() };
		for(Block b : turbines) {
			place(helper, b);
			IControllable c = adapter(helper);
			assertKeys(helper, c, b + " (no generated key: it would read 0 and look healthy)");
			assertEvents(helper, c, b.toString(), List.of(), List.of());
			helper.assertTrue(c instanceof ReachAdapter<?>, "it is an adapter");
		}
		helper.succeed();
	}

	/**
	 * H-CCP-1 was taken by master (D-2275) and the mean is Leafia's (owner decision 2026-10-17, D-3072): {@code generated} is the mean of
	 * the last 20 ticks' {@code lastTickGeneration}, sampled once a tick, zero when idle, and forgotten with the block entity.
	 *
	 * <p>The assertions are relationships that do not depend on whether the level's Post tick or the test body runs first within a game
	 * tick (rule 10, D-953): the steam is topped up every step so the generation g is constant, then after k running ticks the mean is
	 * between (k-1)g/20 and kg/20, after twenty it is g itself (never g earlier), and after the steam is cut it falls back to
	 * between the same bounds and is exactly zero twenty-one ticks on.
	 */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theSmallTurbineGeneratedIsTheTwentyTickMean(GameTestHelper helper) {
		com.hbm.blockentity.machine.BlockEntityMachineTurbine turbine =
				(com.hbm.blockentity.machine.BlockEntityMachineTurbine) place(helper, ModBlocks.machine_turbine.get());
		turbine.tanks[0].setFill(1_000);
		turbine.power = 0;
		IControllable c = adapter(helper);
		assertKeys(helper, c, "the turbine", "generated");
		assertEvents(helper, c, "the turbine", List.of(), List.of());
		helper.assertTrue(val(c, "generated") == 0F, "0 before it has run, got " + val(c, "generated"));
		meanSequence(helper, c, () -> turbine.lastTickGeneration, () -> { turbine.tanks[0].setFill(1_000); turbine.power = 0; },
				() -> turbine.tanks[0].setFill(0));
	}

	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theLargeTurbineGeneratedIsTheTwentyTickMean(GameTestHelper helper) {
		com.hbm.blockentity.machine.BlockEntityMachineLargeTurbine turbine =
				(com.hbm.blockentity.machine.BlockEntityMachineLargeTurbine) place(helper, ModBlocks.machine_large_turbine.get());
		turbine.tanks[0].setFill(10_000);
		turbine.power = 0;
		IControllable c = adapter(helper);
		assertKeys(helper, c, "the large turbine", "generated");
		assertEvents(helper, c, "the large turbine", List.of(), List.of());
		meanSequence(helper, c, () -> turbine.lastTickGeneration, () -> { turbine.tanks[0].setFill(10_000); turbine.power = 0; },
				() -> turbine.tanks[0].setFill(0));
	}

	private static void meanSequence(GameTestHelper helper, IControllable c, java.util.function.LongSupplier seam, Runnable topUp, Runnable stop) {
		float[] g = { 0F };
		net.minecraft.gametest.framework.GameTestSequence seq = TickWindow.sequence(helper);
		for(int i = 1; i <= 30; i++) {
			final int k = i;
			seq = seq.thenIdle(1).thenExecute(() -> {
				topUp.run();
				float mean = val(c, "generated");
				if(k == 5) {
					g[0] = seam.getAsLong();
					helper.assertTrue(g[0] > 0F, "the turbine generates, got " + g[0]);
				}
				if(k > 5) {
					helper.assertTrue(seam.getAsLong() == (long) g[0], "the generation is constant, " + g[0] + " then " + seam.getAsLong());
					float lo = Math.min(k - 1, 20) * g[0] / 20F - 0.5F, hi = Math.min(k, 20) * g[0] / 20F + 0.5F;
					helper.assertTrue(mean >= lo && mean <= hi, "after " + k + " running ticks the mean is within [" + lo + ", " + hi + "], got " + mean);
				}
				if(k < 20 && k > 5)
					helper.assertTrue(mean < g[0] - 0.5F, "and it is not the last tick's value while the window fills, got " + mean + " of " + g[0]);
				if(k >= 22)
					helper.assertTrue(Math.abs(mean - g[0]) < 0.5F, "a full window is the generation itself, got " + mean + " of " + g[0]);
			});
		}
		seq = seq.thenExecute(stop);
		for(int i = 1; i <= 23; i++) {
			final int k = i;
			seq = seq.thenIdle(1).thenExecute(() -> {
				float mean = val(c, "generated");
				if(k >= 2) helper.assertTrue(seam.getAsLong() == 0L, "the machine makes nothing, got " + seam.getAsLong());
				float lo = Math.max(0, 20 - k - 1) * g[0] / 20F - 0.5F, hi = Math.max(0, 20 - k + 1) * g[0] / 20F + 0.5F;
				helper.assertTrue(mean >= lo && mean <= hi, k + " ticks after stopping the mean is within [" + lo + ", " + hi + "], got " + mean);
				if(k >= 21)
					helper.assertTrue(mean == 0F, "twenty-one ticks on the window is empty, got " + mean);
			});
		}
		seq.thenSucceed();
	}

	/** The ring is dropped with the block entity: a broken turbine leaves nothing behind, and a new one at the same place starts empty. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theTurbineRingIsClearedWhenTheBlockEntityIsRemoved(GameTestHelper helper) {
		com.hbm.blockentity.machine.BlockEntityMachineTurbine turbine =
				(com.hbm.blockentity.machine.BlockEntityMachineTurbine) place(helper, ModBlocks.machine_turbine.get());
		turbine.tanks[0].setFill(1_000);
		turbine.power = 0;
		adapter(helper);
		net.minecraft.world.level.Level level = helper.getLevel();
		int before = com.leafia.contents.machines.controlpanel.core.reach.TurbineGeneration.tracked(level);
		helper.assertTrue(before >= 1, "tracked on lookup, got " + before);
		TickWindow.sequence(helper).thenIdle(4).thenExecute(() -> {
			helper.assertTrue(com.leafia.contents.machines.controlpanel.core.reach.TurbineGeneration.mean(turbine) > 0F, "the mean has risen");
			helper.setBlock(AT, Blocks.AIR);
		}).thenIdle(2).thenExecute(() -> {
			helper.assertTrue(turbine.isRemoved(), "removed");
			helper.assertTrue(com.leafia.contents.machines.controlpanel.core.reach.TurbineGeneration.tracked(level) < before,
					"the removed turbine is forgotten, tracked " + com.leafia.contents.machines.controlpanel.core.reach.TurbineGeneration.tracked(level));
			BlockEntity fresh = place(helper, ModBlocks.machine_turbine.get());
			adapter(helper);
			helper.assertTrue(com.leafia.contents.machines.controlpanel.core.reach.TurbineGeneration.mean(fresh) == 0F, "a new turbine starts with an empty window");
			helper.setBlock(AT, Blocks.AIR);
		}).thenSucceed();
	}

	// ------------------------------------------------------------------ turrets

	@GameTest(template = EMPTY)
	public static void everyTurretCoreIsReachableAndTakesTheTwoEvents(GameTestHelper helper) {
		Block[] cores = { ModBlocks.turret_jeremy.get(), ModBlocks.turret_maxwell.get(), ModBlocks.turret_arty.get(), ModBlocks.turret_himars.get(),
				ModBlocks.turret_howard.get(), ModBlocks.turret_howard_damaged.get(), ModBlocks.turret_chekhov.get(), ModBlocks.turret_friendly.get(),
				ModBlocks.turret_fritz.get(), ModBlocks.turret_richard.get(), ModBlocks.turret_tauon.get(), ModBlocks.turret_sentry.get(),
				ModBlocks.turret_sentry_damaged.get() };
		helper.assertTrue(cores.length == 13, "thirteen turret cores");
		for(Block b : cores) {
			BlockEntity be = place(helper, b);
			helper.assertTrue(be instanceof BlockEntityTurretBaseNT, b + " has a turret core, got " + be);
			IControllable c = adapter(helper);
			assertEvents(helper, c, b.toString(), List.of("turret_set_target", "turret_switch"), List.of());
			assertKeys(helper, c, b.toString());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void turretSetTargetAndSwitchDriveTheTurretsFlags(GameTestHelper helper) {
		BlockEntityTurretBaseNT turret = (BlockEntityTurretBaseNT) place(helper, ModBlocks.turret_jeremy.get());
		turret.isOn = false;

		send(helper, ControlEvent.newEvent("turret_set_target").setVar("players", true).setVar("passive", true).setVar("hostile", false).setVar("machines", false));
		helper.assertTrue(turret.targetPlayers && turret.targetAnimals && !turret.targetMobs && !turret.targetMachines, "the four switches, players/passive/hostile/machines");
		send(helper, ControlEvent.newEvent("turret_set_target"));
		helper.assertTrue(!turret.targetPlayers && !turret.targetAnimals && turret.targetMobs && turret.targetMachines,
				"an event with no values set is CE's registered defaults: players off, passive off, hostile on, machines on");

		send(helper, ControlEvent.newEvent("turret_switch").setVar("isOn", true));
		helper.assertTrue(turret.isOn, "switched on");
		send(helper, ControlEvent.newEvent("turret_switch").setVar("isOn", false).setVar("isBlacklistFilter", false));
		helper.assertTrue(!turret.isOn, "and off; the blacklist-filter variable is accepted and ignored (the base has no such mode)");
		helper.succeed();
	}

	// ------------------------------------------------------------------ RBMK columns

	private static final String[] COMMON = { "RSIM_feed", "RSIM_steam", "heat" };

	private static Set<String> plus(String... more) {
		Set<String> s = set(COMMON);
		s.addAll(List.of(more));
		return s;
	}

	@GameTest(template = EMPTY)
	public static void everyRbmkColumnAnswersTheCommonThree(GameTestHelper helper) {
		Block[] columns = { ModBlocks.rbmk_moderator.get(), ModBlocks.rbmk_reflector.get(), ModBlocks.rbmk_absorber.get(), ModBlocks.rbmk_blank.get(),
				ModBlocks.rbmk_storage.get() };
		for(Block b : columns) {
			BlockEntity be = place(helper, b);
			helper.assertTrue(be != null, b + " has a block entity");
			IControllable c = adapter(helper);
			helper.assertTrue(keys(c).equals(plus()), b + " answers heat, RSIM_feed and RSIM_steam and no more, got " + keys(c));
			assertEvents(helper, c, b.toString(), List.of(), List.of());
		}
		BlockEntity blank = place(helper, ModBlocks.rbmk_blank.get());
		com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase base = (com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase) blank;
		base.heat = 321.5;
		base.reasimWater = 77;
		base.reasimSteam = 88;
		IControllable c = adapter(helper);
		helper.assertTrue(val(c, "heat") == 321.5F && val(c, "RSIM_feed") == 77F && val(c, "RSIM_steam") == 88F, "the column's heat and ReaSim buffers");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theFuelRodsKeysFollowItsLoadedRod(GameTestHelper helper) {
		for(Block b : new Block[] { ModBlocks.rbmk_rod.get(), ModBlocks.rbmk_rod_reasim.get() }) {
			BlockEntityRBMKRod rod = (BlockEntityRBMKRod) place(helper, b);
			IControllable c = adapter(helper);
			helper.assertTrue(keys(c).equals(plus("enrichment", "xenon", "c_heat", "c_coreHeat", "c_maxHeat")), b + " keys, got " + keys(c));
			helper.assertTrue(val(c, "enrichment") == 0F && val(c, "xenon") == 0F && val(c, "c_heat") == 0F && val(c, "c_coreHeat") == 0F && val(c, "c_maxHeat") == 0F,
					"with no rod loaded all five are 0");

			ItemStack stack = new ItemStack(ModItems.rbmk_fuel_ueu.get());
			ItemRBMKRod.setPoison(stack, 12.5);
			ItemRBMKRod.setHullHeat(stack, 150);
			ItemRBMKRod.setCoreHeat(stack, 400);
			rod.slots[0] = stack;
			c = adapter(helper);
			near(helper, ItemRBMKRod.getEnrichment(stack), val(c, "enrichment"), "enrichment");
			near(helper, 12.5, val(c, "xenon"), "xenon");
			near(helper, 150, val(c, "c_heat"), "hull heat");
			near(helper, 400, val(c, "c_coreHeat"), "core heat");
			near(helper, 2865, val(c, "c_maxHeat"), "the rod's melting point");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theControlRodsLevelsAreTimesOneHundred(GameTestHelper helper) {
		BlockEntityRBMKControlManual manual = (BlockEntityRBMKControlManual) place(helper, ModBlocks.rbmk_control.get());
		manual.rodLevel = 0.25;
		manual.targetLevel = 0.75;
		IControllable c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("level", "targetLevel")), "no colour while it has none, got " + keys(c));
		near(helper, 25, val(c, "level"), "level");
		near(helper, 75, val(c, "targetLevel"), "target");
		assertEvents(helper, c, "the manual rod", List.of("rbmk_ctrl_set_level", "rbmk_ctrl_set_color"), List.of());

		manual.color = RBMKColor.GREEN;
		helper.assertTrue(keys(adapter(helper)).equals(plus("level", "targetLevel", "color")) && val(adapter(helper), "color") == (float) RBMKColor.GREEN.ordinal(),
				"with a colour it adds the ordinal");

		BlockEntityRBMKControlAuto auto = (BlockEntityRBMKControlAuto) place(helper, ModBlocks.rbmk_control_auto.get());
		auto.rodLevel = 0.5;
		c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("level", "targetLevel")), "the automatic rod answers the levels, got " + keys(c));
		near(helper, 50, val(c, "level"), "auto level");
		assertEvents(helper, c, "the automatic rod", List.of(), List.of());
		helper.succeed();
	}

	/** The in-event stays within the rod's own screen bounds: 0 to 100 percent, never a multiplier past 1. */
	@GameTest(template = EMPTY)
	public static void rbmkCtrlSetLevelIsBoundedLikeTheScreen(GameTestHelper helper) {
		BlockEntityRBMKControlManual rod = (BlockEntityRBMKControlManual) place(helper, ModBlocks.rbmk_control.get());
		rod.rodLevel = 0.6;

		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", 40));
		near(helper, 0.4, rod.targetLevel, "40 percent is 0.4");
		near(helper, 0.6, rod.startingLevel, "the insertion surge starts from where the rod was");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", 5_000));
		near(helper, 1, rod.targetLevel, "5000 percent is clamped to 1: a rod is a multiplier on flux");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", -30));
		near(helper, 0, rod.targetLevel, "a negative level is clamped to 0");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", 55));
		near(helper, 0.55, rod.targetLevel, "55 percent");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", Float.NaN));
		near(helper, 0.55, rod.targetLevel, "NaN changes nothing");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_level").setVar("level", Float.POSITIVE_INFINITY));
		near(helper, 0.55, rod.targetLevel, "neither does infinity");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void rbmkCtrlSetColorIsOneBasedAndCannotThrow(GameTestHelper helper) {
		BlockEntityRBMKControlManual rod = (BlockEntityRBMKControlManual) place(helper, ModBlocks.rbmk_control.get());
		RBMKColor[] colors = RBMKColor.values();
		for(int n = 1; n <= colors.length; n++) {
			send(helper, ControlEvent.newEvent("rbmk_ctrl_set_color").setVar("color", n));
			helper.assertTrue(rod.color == colors[n - 1], "colour " + n + " is " + colors[n - 1] + ", got " + rod.color);
		}
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_color").setVar("color", 0));
		helper.assertTrue(rod.color == colors[colors.length - 1], "0 is ignored (CE threw on it), still " + rod.color);
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_color").setVar("color", -7));
		helper.assertTrue(rod.color == colors[colors.length - 1], "so is a negative");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_color").setVar("color", 6));
		helper.assertTrue(rod.color == colors[0], "6 wraps to the first");
		send(helper, ControlEvent.newEvent("rbmk_ctrl_set_color").setVar("color", Float.NaN));
		helper.assertTrue(rod.color == colors[0], "NaN is ignored");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theFluidColumnsKeys(GameTestHelper helper) {
		BlockEntityRBMKBoiler boiler = (BlockEntityRBMKBoiler) place(helper, ModBlocks.rbmk_boiler.get());
		boiler.feed.setFill(1_111);
		boiler.steam.setFill(222);
		IControllable c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("feed", "steam", "steamType")), "boiler keys, got " + keys(c));
		helper.assertTrue(val(c, "feed") == 1_111F && val(c, "steam") == 222F, "the two fills");
		helper.assertTrue(text(c, "steamType").equals(boiler.steam.getTankType().getName()), "steamType is the fluid's name");

		BlockEntityRBMKCooler cooler = (BlockEntityRBMKCooler) place(helper, ModBlocks.rbmk_cooler.get());
		cooler.getAllTanks()[0].setFill(300);
		cooler.getAllTanks()[1].setFill(40);
		c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("t0_fluidType", "t0_fluidAmount", "t1_fluidType", "t1_fluidAmount")), "cooler keys, got " + keys(c));
		helper.assertTrue(val(c, "t0_fluidAmount") == 300F && val(c, "t1_fluidAmount") == 40F, "the two tanks");
		helper.assertTrue(text(c, "t0_fluidType").equals(cooler.getAllTanks()[0].getTankType().getName()), "named by the fluid's name");

		BlockEntityRBMKHeater heater = (BlockEntityRBMKHeater) place(helper, ModBlocks.rbmk_heater.get());
		heater.feed.setFill(10);
		heater.steam.setFill(20);
		c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("t0_fluidType", "t0_fluidAmount", "t1_fluidType", "t1_fluidAmount")), "heater keys, got " + keys(c));
		helper.assertTrue(val(c, "t0_fluidAmount") == 10F && val(c, "t1_fluidAmount") == 20F, "feed is tank 0, steam tank 1");

		BlockEntityRBMKOutgasser outgasser = (BlockEntityRBMKOutgasser) place(helper, ModBlocks.rbmk_outgasser.get());
		outgasser.gas.setFill(500);
		outgasser.progress = 2_500;
		c = adapter(helper);
		helper.assertTrue(keys(c).equals(plus("gas", "progress", "maxProgress")), "outgasser keys, got " + keys(c));
		helper.assertTrue(val(c, "gas") == 500F && val(c, "progress") == 2_500F && val(c, "maxProgress") == 10_000F, "gas, progress and the 10,000 duration");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the crane console

	private static BlockEntityCraneConsole crane(GameTestHelper helper) {
		BlockEntityCraneConsole console = (BlockEntityCraneConsole) place(helper, ModBlocks.rbmk_crane_console.get());
		BlockPos column = abs(helper, new BlockPos(4, 1, 4));
		console.centerX = column.getX();
		console.centerY = column.getY() + 1;
		console.centerZ = column.getZ();
		console.spanF = console.spanB = console.spanL = console.spanR = 4;
		console.height = 7;
		return console;
	}

	@GameTest(template = EMPTY)
	public static void theCraneConsolesKeysAndEvents(GameTestHelper helper) {
		BlockEntityCraneConsole console = crane(helper);
		IControllable c = adapter(helper);
		assertEvents(helper, c, "the crane", List.of("rbmk_crane_move", "rbmk_crane_load"), List.of("rbmk_crane_move", "rbmk_crane_load"));
		assertKeys(helper, c, "a console with no crane set up has no position");

		console.setUpCrane = true;
		console.posFront = 1.5;
		console.posLeft = -2.25;
		c = adapter(helper);
		assertKeys(helper, c, "a set-up crane", "posX", "posY");
		near(helper, 2.25, val(c, "posX"), "posX is the left offset, negated");
		near(helper, 1.5, val(c, "posY"), "posY is the front offset");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void rbmkCraneMoveMovesOneBlockWithinTheSpanAndEchoes(GameTestHelper helper) {
		BlockEntityCraneConsole console = crane(helper);
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "rbmk_crane_move");

		send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("up", true));
		near(helper, 0, console.posFront, "a crane that is not set up does not move");

		console.setUpCrane = true;
		sys.subscribeTo(panel, pos);
		try {
			send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("up", true));
			near(helper, 1.0, console.posFront, "up is one block in front");
			helper.assertTrue(panel.count("rbmk_crane_move") == 1, "and is echoed once, as CE's processInput did");
			var echoed = panel.received.get(0).event().vars;
			helper.assertTrue(echoed.get("up").getBoolean() && !echoed.get("down").getBoolean() && !echoed.get("left").getBoolean() && !echoed.get("right").getBoolean(), "with the flags that were applied");

			send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("left", true).setVar("down", true));
			near(helper, 0.0, console.posFront, "down undoes it");
			near(helper, 1.0, console.posLeft, "and left is one block to the left");

			send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("up", true).setVar("down", true).setVar("left", true).setVar("right", true));
			near(helper, 0.0, console.posFront, "opposite flags cancel");
			near(helper, 1.0, console.posLeft, "both axes");
			helper.assertTrue(panel.count("rbmk_crane_move") == 2, "and a cancelled move is not echoed");

			for(int i = 0; i < 10; i++) send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("up", true));
			near(helper, 4.0, console.posFront, "the crane never leaves its span, as the console clamps every tick");
			for(int i = 0; i < 12; i++) send(helper, ControlEvent.newEvent("rbmk_crane_move").setVar("right", true));
			near(helper, -4.0, console.posLeft, "on either side");

			BaseReachPoller.poll(helper.getLevel());
			long before = panel.count("rbmk_crane_move");
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("rbmk_crane_move") == before, "the poller does not repeat what the echo sent");
		} finally {
			sys.unsubscribeFrom(panel, pos);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void rbmkCraneLoadSendsTheHookDownAndTheEndOfItIsPolled(GameTestHelper helper) {
		BlockEntityCraneConsole console = crane(helper);
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "rbmk_crane_load", "rbmk_crane_move");

		send(helper, ControlEvent.newEvent("rbmk_crane_load"));
		helper.assertTrue(!console.isGoingDown(), "a crane that is not set up does nothing");
		console.setUpCrane = true;

		sys.subscribeTo(panel, pos);
		try {
			BaseReachPoller.poll(helper.getLevel());
			send(helper, ControlEvent.newEvent("rbmk_crane_load"));
			helper.assertTrue(console.isGoingDown(), "the hook goes down");
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("rbmk_crane_load") == 0, "nothing is announced while it is going down");
			console.progress = 0.5;
			send(helper, ControlEvent.newEvent("rbmk_crane_load"));
			helper.assertTrue(console.isGoingDown(), "a hook already loading ignores a second request");

			// the end of the descent, as the console's own tick does it: goesDown falls and progress is 0
			for(int i = 0; i < 40 && console.isGoingDown(); i++) console.updateEntity();
			helper.assertTrue(!console.isGoingDown(), "the console finished its descent");
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("rbmk_crane_load") == 1, "the end of the descent is announced once, got " + panel.count("rbmk_crane_load"));
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("rbmk_crane_load") == 1, "and not again");
		} finally {
			sys.unsubscribeFrom(panel, pos);
		}
		helper.succeed();
	}

	/** A move made at the desk (not by a panel) is found by the poller, in the direction it went. */
	@GameTest(template = EMPTY)
	public static void aMoveMadeAtTheDeskIsPolledWithItsDirection(GameTestHelper helper) {
		BlockEntityCraneConsole console = crane(helper);
		console.setUpCrane = true;
		ControlEventSystem sys = sys(helper);
		BlockPos pos = abs(helper, AT);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "rbmk_crane_move");
		sys.subscribeTo(panel, pos);
		try {
			BaseReachPoller.poll(helper.getLevel());
			console.posFront += 0.05;
			console.posLeft -= 0.05;
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(panel.count("rbmk_crane_move") == 1, "one event for the tick");
			var v = panel.received.get(0).event().vars;
			helper.assertTrue(v.get("up").getBoolean() && !v.get("down").getBoolean() && !v.get("left").getBoolean() && v.get("right").getBoolean(),
					"up and right: the front offset grew and the left offset shrank");
		} finally {
			sys.unsubscribeFrom(panel, pos);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ a stale target

	/** A position in a chunk nobody has loaded is never polled and never loads one. */
	@GameTest(template = EMPTY)
	public static void aSubscribedPositionThatIsNotLoadedIsSkipped(GameTestHelper helper) {
		ControlEventSystem sys = sys(helper);
		BlockPos far = new BlockPos(2_000_000, 64, 2_000_000);
		Recorder panel = new Recorder(abs(helper, new BlockPos(5, 1, 5)), helper.getLevel(), "door_open_state");
		sys.subscribeTo(panel, far);
		try {
			BaseReachPoller.poll(helper.getLevel());
			helper.assertTrue(!helper.getLevel().isLoaded(far), "the poll did not load the chunk");
			helper.assertTrue(BaseReachPoller.watched(helper.getLevel()) == 0, "and keeps no baseline for it");
		} finally {
			sys.unsubscribeFrom(panel, far);
		}
		helper.succeed();
	}
}
