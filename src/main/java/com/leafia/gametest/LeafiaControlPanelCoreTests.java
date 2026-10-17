package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownNode;
import com.leafia.contents.machines.controlpanel.core.controls.ControlType;
import com.leafia.contents.machines.controlpanel.core.modular.INodeLoader;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.nodes.IFunctionNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IInputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.IOutputNode;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.leafia.contents.machines.controlpanel.core.types.DataValueComposite;
import com.leafia.contents.machines.controlpanel.core.types.DataValueEnum;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.gametest.LeafiaControlPanelFixtures.AdaptedHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.DirectHost;
import com.leafia.gametest.LeafiaControlPanelFixtures.Received;
import com.leafia.gametest.LeafiaControlPanelFixtures.Recorder;
import com.leafia.lib.LeafiaRef;
import com.hbm.gametest.TickWindow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Custom Control Panel's event post office and its object model (CCP-DATA, D-3061): dispatch to a controllable at a position,
 * the refusal of an unloaded one, subscriptions that outlive their target, a control's NBT and geometry, and a node graph run
 * and saved end to end.
 *
 * <p>Every body here is synchronous on the server thread, so the tests cannot interleave with one another; the ones that touch
 * the process-wide node-loader list put it back in a {@code finally} (D-952). The one test that waits on the
 * level tick opens its window through {@link TickWindow}, and every controllable a test registers is removed before it ends,
 * because the level's system is shared and a stale tick target would go on being called (D-963).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelCoreTests {

	private static final String EMPTY = "empty";

	/** A position well outside anything a test loads. */
	private static final BlockPos FAR = new BlockPos(2_000_000, 64, 2_000_000);

	private static ControlEventSystem system(GameTestHelper helper) {
		return ControlEventSystem.get(helper.getLevel());
	}

	private static BlockPos at(GameTestHelper helper, int x, int z) {
		return helper.absolutePos(new BlockPos(x, 1, z));
	}

	// ------------------------------------------------------------------ the capability

	@GameTest(template = EMPTY)
	public static void theCapabilityIsLeafiaControllable(GameTestHelper helper) {
		helper.assertTrue(ControlEventSystem.CONTROLLABLE.name().toString().equals("leafia:controllable"), "its id is leafia:controllable");
		helper.assertTrue(ControlEventSystem.CONTROLLABLE.typeClass() == IControllable.class && ControlEventSystem.CONTROLLABLE.contextClass() == void.class,
				"it carries an IControllable and takes no context (NeoForge spells that void.class)");
		helper.succeed();
	}

	// ------------------------------------------------------------------ dispatch

	@GameTest(template = EMPTY)
	public static void aBlockEntityThatIsControllableReceivesAtItsPosition(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		ControlEventSystem sys = system(helper);
		BlockPos pos = at(helper, 1, 1);
		helper.setBlock(new BlockPos(1, 1, 1), LeafiaControlPanelFixtures.direct);
		DirectHost host = (DirectHost) helper.getBlockEntity(new BlockPos(1, 1, 1));
		host.rec.in = new ArrayList<>(List.of("lever_toggle"));

		BlockPos from = at(helper, 3, 3);
		ControlEvent evt = ControlEvent.newEvent("lever_toggle").setVar("isOn", true);
		sys.broadcastEvent(from, evt, pos);

		helper.assertTrue(host.rec.received.size() == 1, "one event arrived, got " + host.rec.received.size());
		Received r = host.rec.received.get(0);
		helper.assertTrue(r.from().equals(from) && r.event() == evt, "with the sender's position and the event itself");

		sys.broadcastEvent(from, ControlEvent.newEvent("door_toggle"), pos);
		helper.assertTrue(host.rec.received.size() == 1, "an event the target does not list is not delivered");

		host.rec.in = new ArrayList<>();
		sys.broadcastEvent(from, ControlEvent.newEvent("lever_toggle"), pos);
		helper.assertTrue(host.rec.received.size() == 1, "a target that lists nothing receives nothing");

		helper.assertTrue(sys.resolve(pos) == host, "resolve finds the block entity itself");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBlockEntityThatIsNotControllableIsReachedThroughTheCapability(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		ControlEventSystem sys = system(helper);
		BlockPos rel = new BlockPos(2, 1, 2);
		BlockPos pos = helper.absolutePos(rel);
		helper.setBlock(rel, LeafiaControlPanelFixtures.adapted);
		AdaptedHost host = (AdaptedHost) helper.getBlockEntity(rel);
		helper.assertTrue(!(((Object) host) instanceof IControllable), "the fixture is not itself controllable, or this proves nothing");
		host.adapter.in = new ArrayList<>(List.of("door_toggle"));

		helper.assertTrue(sys.resolve(pos) == host.adapter, "resolve returns what the provider returned");
		sys.broadcastEvent(at(helper, 4, 4), ControlEvent.newEvent("door_toggle").setVar("passcode", 1234), pos);
		helper.assertTrue(host.adapter.received.size() == 1, "the adapter received the event");
		helper.assertTrue(host.adapter.received.get(0).event().vars.get("passcode").getNumber() == 1234F, "with its payload");
		helper.assertTrue(host.adapter.getControlPos().equals(pos) && host.adapter.getControlWorld() == helper.getLevel(), "and it reports the block's position and level");

		sys.broadcastEvent(at(helper, 4, 4), ControlEvent.newEvent("lever_toggle"), pos);
		helper.assertTrue(host.adapter.received.size() == 1, "an event it does not list is refused here too");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void nothingThereReceivesNothing(GameTestHelper helper) {
		ControlEventSystem sys = system(helper);
		BlockPos pos = at(helper, 1, 3);
		helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
		helper.assertTrue(sys.resolve(pos) == null, "a stone block resolves to nothing");
		sys.broadcastEvent(at(helper, 2, 2), ControlEvent.newEvent("initialize"), pos);
		sys.broadcastEvent(at(helper, 2, 2), ControlEvent.newEvent("initialize"), (java.util.Collection<BlockPos>) null);
		helper.assertTrue(sys.resolve(at(helper, 3, 1)) == null, "and so does air");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aTargetThatIsNotABlockEntityRegistersItself(GameTestHelper helper) {
		ControlEventSystem sys = system(helper);
		BlockPos pos = at(helper, 3, 2);
		Recorder r = new Recorder(pos, helper.getLevel(), "initialize");
		try {
			helper.assertTrue(!sys.isValid(r), "not valid before it registers");
			sys.addControllable(r);
			helper.assertTrue(sys.isValid(r) && sys.resolve(pos) == r, "registered, it is valid and found at its position");
			sys.addControllable(r);
			sys.broadcastEvent(at(helper, 1, 1), ControlEvent.newEvent("initialize"), pos);
			helper.assertTrue(r.received.size() == 1, "registering twice does not deliver twice");
			sys.removeControllable(r);
			helper.assertTrue(!sys.isValid(r) && sys.resolve(pos) == null, "removed, it is forgotten");
			sys.broadcastEvent(at(helper, 1, 1), ControlEvent.newEvent("initialize"), pos);
			helper.assertTrue(r.received.size() == 1, "and receives nothing more");
		} finally {
			sys.removeControllable(r);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aCollectionDeliversToEachPositionOnce(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		ControlEventSystem sys = system(helper);
		helper.setBlock(new BlockPos(1, 1, 1), LeafiaControlPanelFixtures.direct);
		helper.setBlock(new BlockPos(3, 1, 1), LeafiaControlPanelFixtures.direct);
		DirectHost a = (DirectHost) helper.getBlockEntity(new BlockPos(1, 1, 1));
		DirectHost b = (DirectHost) helper.getBlockEntity(new BlockPos(3, 1, 1));
		a.rec.in = new ArrayList<>(List.of("siren_set_state"));
		b.rec.in = new ArrayList<>(List.of("siren_set_state"));

		List<BlockPos> targets = List.of(at(helper, 1, 1), at(helper, 2, 2), at(helper, 3, 1));
		sys.broadcastEvent(at(helper, 4, 4), ControlEvent.newEvent("siren_set_state").setVar("isOn", true), targets);
		helper.assertTrue(a.rec.received.size() == 1 && b.rec.received.size() == 1, "both loaded controllable targets heard it once, the empty position nobody");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anUnloadedPositionIsRefusedAndNeverLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ControlEventSystem sys = system(helper);
		helper.assertTrue(!level.isLoaded(FAR), "the far position starts unloaded, or this test proves nothing");
		Recorder r = new Recorder(FAR, level, "initialize");
		try {
			sys.addControllable(r);
			helper.assertTrue(sys.resolve(FAR) == null, "an unloaded position resolves to nothing, even for a registered target");
			sys.broadcastEvent(at(helper, 1, 1), ControlEvent.newEvent("initialize"), FAR);
			sys.broadcastEvent(at(helper, 1, 1), ControlEvent.newEvent("initialize"), List.of(FAR));
			helper.assertTrue(r.received.isEmpty(), "nothing was delivered");
			helper.assertTrue(!level.isLoaded(FAR), "and asking did not load the chunk");
		} finally {
			sys.removeControllable(r);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aRemovedBlockEntityStopsReceiving(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		ControlEventSystem sys = system(helper);
		BlockPos rel = new BlockPos(2, 1, 3);
		BlockPos pos = helper.absolutePos(rel);
		helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
		DirectHost host = (DirectHost) helper.getBlockEntity(rel);
		host.rec.in = new ArrayList<>(List.of("initialize"));
		helper.setBlock(rel, Blocks.AIR);
		sys.broadcastEvent(at(helper, 1, 1), ControlEvent.newEvent("initialize"), pos);
		helper.assertTrue(host.rec.received.isEmpty() && sys.resolve(pos) == null, "a broken block entity is not found, so it is not called");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aReceiverThatThrowsDoesNotStopTheOthers(GameTestHelper helper) {
		ControlEventSystem sys = system(helper);
		BlockPos p1 = at(helper, 1, 2);
		BlockPos p2 = at(helper, 2, 2);
		Recorder bad = new Recorder(p1, helper.getLevel(), "initialize");
		bad.failWith = new IllegalStateException("boom, from a test");
		Recorder good = new Recorder(p2, helper.getLevel(), "initialize");
		try {
			sys.addControllable(bad);
			sys.addControllable(good);
			sys.broadcastEvent(at(helper, 4, 4), ControlEvent.newEvent("initialize"), List.of(p1, p2));
			helper.assertTrue(bad.received.size() == 1, "the failing receiver was called");
			helper.assertTrue(good.received.size() == 1, "and the next one still was");
		} finally {
			sys.removeControllable(bad);
			sys.removeControllable(good);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ subscriptions

	@GameTest(template = EMPTY)
	public static void aSubscriptionSurvivesItsTargetUnloading(GameTestHelper helper) {
		ControlEventSystem sys = system(helper);
		BlockPos subPos = at(helper, 1, 1);
		BlockPos targetPos = at(helper, 3, 3);
		Recorder sub = new Recorder(subPos, helper.getLevel(), "lever_toggle");
		Recorder target = new Recorder(targetPos, helper.getLevel(), "lever_toggle");
		try {
			sys.addControllable(sub);
			sys.addControllable(target);
			sys.subscribeTo(sub, target);
			helper.assertTrue(sys.isSubscribed(sub, targetPos), "subscribed to the target's position");

			ControlEvent evt = ControlEvent.newEvent("lever_toggle").setVar("isOn", true);
			sys.broadcastToSubscribed(target, evt);
			helper.assertTrue(sub.received.size() == 1 && sub.received.get(0).from().equals(targetPos) && sub.received.get(0).event() == evt,
					"a subscriber hears from the target's position");

			sys.removeControllable(target);
			helper.assertTrue(sys.isSubscribed(sub, targetPos), "the target unloading does not drop the subscription (CE keeps it on purpose)");
			sys.addControllable(target);
			sys.broadcastToSubscribed(target, ControlEvent.newEvent("lever_toggle"));
			helper.assertTrue(sub.received.size() == 2, "and when it is back the subscriber hears again");

			sys.unsubscribeFrom(sub, targetPos);
			helper.assertTrue(!sys.isSubscribed(sub, targetPos), "unsubscribing ends it");
			sys.broadcastToSubscribed(target, ControlEvent.newEvent("lever_toggle"));
			helper.assertTrue(sub.received.size() == 2, "after which nothing arrives");

			sys.subscribeTo(sub, targetPos);
			sys.removeControllable(sub);
			helper.assertTrue(!sys.isSubscribed(sub, targetPos), "but the subscriber leaving drops what it held");
			sys.broadcastToSubscribed(target, ControlEvent.newEvent("lever_toggle"));
			helper.assertTrue(sub.received.size() == 2, "so a departed subscriber is never called");
		} finally {
			sys.removeControllable(sub);
			sys.removeControllable(target);
			sys.unsubscribeFrom(sub, targetPos);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aSubscriptionSurvivesARealBlockEntityBeingRemovedAndReplaced(GameTestHelper helper) {
		helper.assertTrue(LeafiaGameTestFixtures.enabled(), "the fixtures are registered only when leafia.gametest.fixtures is set");
		ControlEventSystem sys = system(helper);
		BlockPos rel = new BlockPos(3, 1, 3);
		BlockPos targetPos = helper.absolutePos(rel);
		Recorder sub = new Recorder(at(helper, 1, 1), helper.getLevel(), "lever_toggle");
		try {
			sys.addControllable(sub);
			sys.subscribeTo(sub, targetPos);
			helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
			DirectHost first = (DirectHost) helper.getBlockEntity(rel);
			sys.addControllable(first);
			sys.removeControllable(first);
			helper.setBlock(rel, Blocks.AIR);
			helper.setBlock(rel, LeafiaControlPanelFixtures.direct);
			DirectHost second = (DirectHost) helper.getBlockEntity(rel);
			helper.assertTrue(second != first, "a fresh block entity stands there");
			sys.broadcastToSubscribed(second, ControlEvent.newEvent("lever_toggle"));
			helper.assertTrue(sub.received.size() == 1, "the subscription made before it existed reaches the new one's broadcasts");
		} finally {
			sys.removeControllable(sub);
			sys.unsubscribeFrom(sub, targetPos);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ tick

	@GameTest(template = EMPTY)
	public static void tickGoesToLoadedTickTargetsOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ControlEventSystem sys = system(helper);
		BlockPos p = at(helper, 2, 2);
		Recorder ticker = new Recorder(p, level, "tick");
		Recorder quiet = new Recorder(at(helper, 3, 3), level, "initialize");
		Recorder far = new Recorder(FAR, level, "tick");
		try {
			sys.addControllable(ticker);
			sys.addControllable(quiet);
			sys.addControllable(far);
			helper.assertTrue(sys.tickTargets().contains(ticker) && !sys.tickTargets().contains(quiet), "only a target listing tick is a tick target");
			sys.tick();
			helper.assertTrue(ticker.count("tick") == 1, "a loaded tick target got one tick");
			Received r = ticker.received.get(0);
			helper.assertTrue(r.from().equals(p), "from its own position, as CE sends it");
			helper.assertTrue(r.event().vars.get("time").getNumber() == (float) level.getGameTime(), "with the level's game time");
			helper.assertTrue(quiet.received.isEmpty(), "a target that does not list tick got none");
			helper.assertTrue(far.received.isEmpty(), "and an unloaded one got none (Leafia's overwrite of the loop)");
		} finally {
			sys.removeControllable(ticker);
			sys.removeControllable(quiet);
			sys.removeControllable(far);
		}
		helper.succeed();
	}

	/** The real path: {@code LevelTickEvent.Pre} walks the level's system once per server tick. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void theLevelTickDrivesTheTickEvent(GameTestHelper helper) {
		ControlEventSystem sys = system(helper);
		Recorder ticker = new Recorder(at(helper, 2, 1), helper.getLevel(), "tick");
		TickWindow.sequence(helper)
				.thenExecute(() -> sys.addControllable(ticker))
				.thenIdle(5)
				.thenExecute(() -> {
					try {
						long n = ticker.count("tick");
						helper.assertTrue(n >= 4 && n <= 6, "about one tick event per level tick over five, got " + n);
						float prev = -1;
						for(Received r : ticker.received) {
							float t = r.event().vars.get("time").getNumber();
							helper.assertTrue(t > prev, "game time only moves forward: " + prev + " then " + t);
							prev = t;
						}
					} finally {
						sys.removeControllable(ticker);
					}
				})
				.thenSucceed();
	}

	// ------------------------------------------------------------------ Control

	/** A control with nothing on it but its size. */
	static final class TestControl extends Control {
		final float[] size;

		TestControl(ControlPanel panel, float w, float l, float h) {
			super("Test", "gt_test", panel);
			this.size = new float[]{w, l, h};
		}

		@Override public ControlType getControlType() { return ControlType.BUTTON; }
		@Override public float[] getSize() { return size; }
		@Override public Control newControl(ControlPanel p) { return new TestControl(p, size[0], size[1], size[2]); }
		@Override public void populateDefaultNodes(List<ControlEvent> receiveEvents) {}
	}

	private static ControlPanel panel(GameTestHelper helper) {
		return new ControlPanel(new Recorder(at(helper, 1, 1), helper.getLevel(), "tick"));
	}

	@GameTest(template = EMPTY)
	public static void aControlsBoundsFollowCEsArithmetic(GameTestHelper helper) {
		ControlPanel p = panel(helper);
		TestControl one = new TestControl(p, 1, 1, 1);
		helper.assertTrue(one.getBoundingBox().equals(new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5)), "1x1x1 at the origin is centred on it, got " + one.getBoundingBox());
		one.posX = 2;
		one.posY = 4;
		helper.assertTrue(one.getBoundingBox().equals(new AABB(1.5, 0, 3.5, 2.5, 1, 4.5)), "moved by its position, got " + one.getBoundingBox());
		float[] box = one.getBox();
		helper.assertTrue(box[0] == 2 && box[1] == 4 && box[2] == 3 && box[3] == 5, "the 2D box is position to position plus size");

		TestControl big = new TestControl(p, 3, 2, 1.5F);
		big.posX = 2;
		big.posY = 4;
		AABB bb = big.getBoundingBox();
		helper.assertTrue(bb.equals(new AABB(1.5, 0, 3.5, 4.5, 1.5, 5.5)),
				"a control wider than one cell is shifted by |1-w|/2 (1 across, 0.5 along), got " + bb);

		TestControl small = new TestControl(p, 0.5F, 0.5F, 2);
		AABB sb = small.getBoundingBox();
		helper.assertTrue(sb.equals(new AABB(-0.5, 0, -0.5, 0.0, 2, 0.0)), "a control narrower than one cell is shifted by (w-1)/2 = -0.25, got " + sb);
		float[] fill = new float[4];
		big.fillBox(fill);
		helper.assertTrue(fill[0] == 2 && fill[1] == 4 && fill[2] == 5 && fill[3] == 6, "fillBox agrees with getBox");

		helper.assertTrue(one.getInEvents().equals(List.of("tick", "initialize", "redstone_input")), "the default in-events are CE's three");
		helper.assertTrue(one.getOutEvents().isEmpty(), "and there are no default out-events");
		helper.assertTrue(one.getVar("nothing").getNumber() == 0F && one.getVar("nothing") instanceof DataValueFloat, "an unset variable reads as the number 0");
		helper.assertTrue(one.taggedLinks.get("missing").equals(BlockPos.ZERO), "an unset link tag reads as the origin");
		p.globalVars.put("g", new DataValueString("x"));
		helper.assertTrue(one.getGlobalVar("g").toString().equals("x") && one.getGlobalVar("h").getNumber() == 0F, "and a global reads through the panel");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aControlRoundTripsThroughNbt(GameTestHelper helper) {
		ControlPanel p = panel(helper);
		TestControl c = new TestControl(p, 2, 1, 1);
		c.name = "Lever 1";
		c.posX = 2.5F;
		c.posY = -1F;
		c.vars.put("n", new DataValueFloat(7));
		c.vars.put("s", new DataValueString("hello"));
		c.vars.put("e", new DataValueEnum<>(DyeColor.RED));
		DataValueComposite pack = new DataValueComposite();
		pack.setValueOf("k", "v");
		c.vars.put("c", pack);
		c.customVarNames.add("n");
		c.customVarNames.add("s");
		c.taggedLinks.put("north", new BlockPos(1, 2, 3));
		c.taggedLinks.put("x", new BlockPos(-5, 64, 9));
		c.configMap.put("size", new DataValueFloat(3));
		c.configMap.put("color", new DataValueEnum<>(Direction.UP));

		CompoundTag tag = c.writeToNBT(new CompoundTag());
		helper.assertTrue(tag.getAllKeys().equals(Set.of("name", "myName", "vars", "SN", "RN", "customvars", "taglnk", "X", "Y", "configs", "ntmPlaceholderMeta")),
				"the keys are CE's, got " + tag.getAllKeys());
		helper.assertTrue(tag.getString("name").equals("gt_test") && tag.getString("myName").equals("Lever 1"), "the registry name and the given name");
		helper.assertTrue(tag.getCompound("taglnk").getIntArray("north").length == 3 && tag.getCompound("taglnk").getIntArray("x")[0] == -5, "links are int triples");
		CompoundTag meta = tag.getCompound("ntmPlaceholderMeta");
		helper.assertTrue(meta.getFloat("boxMinX") == 2.5F && meta.getFloat("boxMinY") == -1F && meta.getFloat("boxMaxX") == 4.5F && meta.getFloat("boxMaxY") == 0F,
				"the placeholder box is the 2D box");
		helper.assertTrue(meta.getFloat("sizeHeight") == 1F && meta.getBoolean("hasBoundingBox"), "with its height and the fact it has bounds");
		helper.assertTrue(meta.contains("boundsMaxZ", Tag.TAG_DOUBLE) && meta.getDouble("boundsMaxY") == 1.0, "and the bounds as doubles");
		helper.assertTrue(new TestControl(p, 1, 1, 0).writeToNBT(new CompoundTag()).getCompound("ntmPlaceholderMeta").getFloat("sizeHeight") == 0.1F,
				"a flat control still records a height of at least 0.1");

		TestControl back = new TestControl(p, 2, 1, 1);
		back.readFromNBT(tag);
		helper.assertTrue(back.name.equals("Lever 1") && back.posX == 2.5F && back.posY == -1F, "name and position read back");
		helper.assertTrue(back.vars.equals(c.vars) && back.configMap.equals(c.configMap), "variables and configs read back equal");
		helper.assertTrue(back.customVarNames.equals(c.customVarNames), "custom variable names read back");
		helper.assertTrue(back.taggedLinks.equals(c.taggedLinks) && back.taggedLinks.get("north").equals(new BlockPos(1, 2, 3)), "links read back");
		CompoundTag again = back.writeToNBT(new CompoundTag());
		CompoundTag first = tag.copy();
		// A set's iteration order is not part of the format, so the custom-variable list is compared as a set.
		helper.assertTrue(again.getCompound("customvars").getAllKeys().size() == 2, "both custom variable names are written again");
		again.remove("customvars");
		first.remove("customvars");
		helper.assertTrue(again.equals(first), "and writing it again gives the same tree");

		CompoundTag legacy = new CompoundTag();
		CompoundTag conset = new CompoundTag();
		conset.putInt("px0", 4);
		conset.putInt("py0", 5);
		conset.putInt("pz0", 6);
		legacy.put("conset", conset);
		TestControl old = new TestControl(p, 1, 1, 1);
		old.readFromNBT(legacy);
		helper.assertTrue(old.taggedLinks.size() == 1 && old.taggedLinks.get("4, 5, 6").equals(new BlockPos(4, 5, 6)), "CE's old 'conset' form still reads, as a link tagged 'x, y, z'");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aControlWithAnUnreadableValueRefusesToLoad(GameTestHelper helper) {
		ControlPanel p = panel(helper);
		CompoundTag badVar = new CompoundTag();
		CompoundTag vars = new CompoundTag();
		vars.put("v", IntTag.valueOf(1));
		badVar.put("vars", vars);
		boolean threw = false;
		try {
			new TestControl(p, 1, 1, 1).readFromNBT(badVar);
		} catch(IllegalStateException e) {
			threw = e.getMessage().contains("'v'");
		}
		helper.assertTrue(threw, "a variable that is not a value throws, naming it (the panel then keeps the raw tag as a placeholder)");

		CompoundTag badEnum = new CompoundTag();
		CompoundTag cfg = new CompoundTag();
		CompoundTag e = new CompoundTag();
		e.putString("enum", "no_such_enum");
		e.putInt("ordinal", 0);
		cfg.put("color", e);
		badEnum.put("configs", cfg);
		threw = false;
		try {
			new TestControl(p, 1, 1, 1).readFromNBT(badEnum);
		} catch(IllegalStateException ex) {
			threw = ex.getMessage().contains("'color'");
		}
		helper.assertTrue(threw, "so does a config naming an enum nobody registered");
		helper.succeed();
	}

	// ------------------------------------------------------------------ nodes

	/** A constant: one number output. */
	static final class ConstNode extends Node {
		ConstNode(float x, float y, float v) {
			super(x, y);
			outputs.add(new NodeConnection("out", this, 0, false, DataType.NUMBER, new DataValueFloat(v)));
			recalcSize();
		}

		@Override public DataValue evaluate(int idx) { return outputs.get(idx).defaultValue; }
		@Override public float[] getColor() { return new float[]{0, 1, 0}; }
		@Override public String getDisplayName() { return "const"; }

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_const");
			return super.writeToNBT(tag, sys);
		}
	}

	/** a + b, cached until reset. */
	static final class AddNode extends Node {
		int evaluations = 0;

		AddNode(float x, float y) {
			super(x, y);
			inputs.add(new NodeConnection("a", this, 0, true, DataType.NUMBER, new DataValueFloat(0)));
			inputs.add(new NodeConnection("b", this, 1, true, DataType.NUMBER, new DataValueFloat(0)));
			outputs.add(new NodeConnection("sum", this, 0, false, DataType.NUMBER, new DataValueFloat(0)));
			evalCache = new DataValue[1];
			recalcSize();
		}

		@Override
		public DataValue evaluate(int idx) {
			if(cacheValid) return evalCache[0];
			cacheValid = true;
			evaluations++;
			return evalCache[0] = new DataValueFloat(inputs.get(0).evaluate().getNumber() + inputs.get(1).evaluate().getNumber());
		}

		@Override public float[] getColor() { return new float[]{1, 1, 0}; }
		@Override public String getDisplayName() { return "add"; }

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_add");
			return super.writeToNBT(tag, sys);
		}
	}

	/** Takes the event's variables as its outputs. */
	static final class InNode extends Node implements IInputNode {
		InNode(float x, float y) {
			super(x, y);
		}

		InNode with(String name, DataValue v) {
			outputs.add(new NodeConnection(name, this, outputs.size(), false, v.getType(), v.copy()));
			recalcSize();
			return this;
		}

		@Override
		public Node setOutputFromVars(Map<String, DataValue> vars) {
			for(NodeConnection c : outputs) {
				DataValue v = vars.get(c.name);
				if(v != null) c.setDefault(v);
			}
			return this;
		}

		@Override public DataValue evaluate(int idx) { return outputs.get(idx).defaultValue; }
		@Override public float[] getColor() { return new float[]{0, 1, 0}; }
		@Override public String getDisplayName() { return "in"; }

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_in");
			return super.writeToNBT(tag, sys);
		}
	}

	/** One input; records what it saw and what it was handed. */
	static final class OutNode extends Node implements IOutputNode {
		final List<Float> seen = new ArrayList<>();
		IControllable lastFrom;
		Map<String, NodeSystem> lastSend;
		Map<String, BlockPos> lastLinks;
		boolean returns = true;

		OutNode(float x, float y) {
			super(x, y);
			inputs.add(new NodeConnection("v", this, 0, true, DataType.NUMBER, new DataValueFloat(0)));
			recalcSize();
		}

		@Override public DataValue evaluate(int idx) { return null; }
		@Override public float[] getColor() { return new float[]{1, 0, 0}; }
		@Override public String getDisplayName() { return "out"; }

		@Override
		public boolean doOutput(IControllable from, Map<String, NodeSystem> sendNodeMap, Map<String, BlockPos> links) {
			seen.add(inputs.get(0).evaluate().getNumber());
			lastFrom = from;
			lastSend = sendNodeMap;
			lastLinks = links;
			return returns;
		}

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_out");
			return super.writeToNBT(tag, sys);
		}
	}

	/** Gates a sub-graph on its Enable socket; {@code unset} makes the enable evaluate to nothing. */
	static final class FnNode extends Node implements IFunctionNode {
		boolean unset = false;

		FnNode(float x, float y) {
			super(x, y);
			inputs.add(new NodeConnection("Enable", this, 0, true, DataType.NUMBER, new DataValueFloat(0)));
			evalCache = new DataValue[1];
			recalcSize();
		}

		@Override
		public DataValue evaluate(int idx) {
			if(unset) return null;
			return new DataValueFloat(inputs.get(0).evaluate().getBoolean());
		}

		@Override public float[] getColor() { return new float[]{0.4F, 0.4F, 1}; }
		@Override public String getDisplayName() { return "fn"; }

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_fn");
			return super.writeToNBT(tag, sys);
		}
	}

	private static final INodeLoader TEST_LOADER = (tag, sys) -> switch(tag.getString("nodeType")) {
		case "gt_const" -> new ConstNode(0, 0, 0);
		case "gt_add" -> new AddNode(0, 0);
		case "gt_in" -> new InNode(0, 0);
		case "gt_out" -> new OutNode(0, 0);
		case "gt_fn" -> new FnNode(0, 0);
		default -> null;
	};

	private interface Body {
		void run() throws Exception;
	}

	/** Runs with the test loader registered, and removes it whatever happens. */
	private static void withLoader(Body body) {
		NTMControlPanelRegistry.nbtNodeLoaders.add(TEST_LOADER);
		try {
			body.run();
		} catch(RuntimeException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		} finally {
			NTMControlPanelRegistry.nbtNodeLoaders.remove(TEST_LOADER);
		}
	}

	private static void wire(Node to, int input, Node from, int output) {
		to.inputs.get(input).setData(from, output, true);
	}

	@GameTest(template = EMPTY)
	public static void anEventRunsThroughTheGraph(GameTestHelper helper) {
		ControlPanel p = panel(helper);
		TestControl c = new TestControl(p, 1, 1, 1);
		NodeSystem sys = new NodeSystem(c);
		InNode in = new InNode(0, 0).with("isOn", new DataValueFloat(0));
		ConstNode ten = new ConstNode(0, 20, 10);
		AddNode add = new AddNode(50, 0);
		OutNode out = new OutNode(100, 0);
		for(Node n : List.of(in, ten, add, out)) sys.addNode(n);
		wire(add, 0, in, 0);
		wire(add, 1, ten, 0);
		wire(out, 0, add, 0);
		c.receiveNodeMap.put("lever_toggle", sys);

		helper.assertTrue(sys.outputNodes.size() == 1 && sys.outputNodes.get(0) == out, "only the output node is an output node");
		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 5));
		helper.assertTrue(out.seen.equals(List.of(15F)), "5 plus 10 reached the output, got " + out.seen);
		helper.assertTrue(out.lastFrom == p.parent, "the output is given the panel's owner");
		helper.assertTrue(out.lastSend == c.sendNodeMap && out.lastLinks == c.taggedLinks, "and the control's own send graphs and links, not copies");

		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 7));
		helper.assertTrue(out.seen.equals(List.of(15F, 17F)), "a second event is computed afresh, caches reset, got " + out.seen);
		helper.assertTrue(add.evaluations == 2, "and the add node was evaluated once per event, got " + add.evaluations);

		c.receiveEvent(ControlEvent.newEvent("initialize"));
		helper.assertTrue(out.seen.size() == 2, "an event with no graph does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFunctionNodeRunsItsSubGraphOnlyWhenEnabled(GameTestHelper helper) {
		ControlPanel p = panel(helper);
		TestControl c = new TestControl(p, 1, 1, 1);
		NodeSystem sys = new NodeSystem(c);
		InNode in = new InNode(0, 0).with("isOn", new DataValueFloat(0));
		FnNode fn = new FnNode(50, 0);
		sys.addNode(in);
		sys.addNode(fn);
		wire(fn, 0, in, 0);
		helper.assertTrue(sys.subSystems.get(fn) != null, "adding a function node gives it a sub-graph");
		NodeSystem sub = sys.subSystems.get(fn);
		InNode subIn = new InNode(0, 0).with("isOn", new DataValueFloat(0));
		OutNode subOut = new OutNode(40, 0);
		sub.addNode(subIn);
		sub.addNode(subOut);
		wire(subOut, 0, subIn, 0);
		c.receiveNodeMap.put("lever_toggle", sys);

		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 0));
		helper.assertTrue(subOut.seen.isEmpty(), "disabled, the sub-graph does not run");
		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 3));
		helper.assertTrue(subOut.seen.equals(List.of(3F)), "enabled, it runs with the same event, got " + subOut.seen);

		fn.unset = true;
		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 3));
		helper.assertTrue(subOut.seen.size() == 1, "an enable that evaluates to nothing is disabled, not a crash");

		fn.unset = false;
		sys.subSystems.remove(fn);
		c.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 3));
		helper.assertTrue(subOut.seen.size() == 1, "a function node with no sub-graph is skipped, not a crash");

		sys.removeNode(fn);
		helper.assertTrue(!sys.nodes.contains(fn) && !sys.subSystems.containsKey(fn), "removeNode takes the node and its sub-graph");
		sys.removeNode(in);
		helper.assertTrue(sys.nodes.isEmpty(), "and a plain node");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aNodeSystemRoundTripsThroughNbt(GameTestHelper helper) {
		withLoader(() -> {
			ControlPanel p = panel(helper);
			TestControl c = new TestControl(p, 1, 1, 1);
			NodeSystem sys = new NodeSystem(c);
			InNode in = new InNode(3, 4).with("isOn", new DataValueFloat(0)).with("facing", new DataValueEnum<>(Direction.NORTH));
			ConstNode k = new ConstNode(5, 6, 2.5F);
			AddNode add = new AddNode(50, 0);
			OutNode out = new OutNode(100, 0);
			FnNode fn = new FnNode(7, 8);
			for(Node n : List.of(in, k, add, out, fn)) sys.addNode(n);
			wire(add, 0, in, 0);
			wire(add, 1, k, 0);
			wire(out, 0, add, 0);
			wire(fn, 0, in, 0);
			OutNode inner = new OutNode(1, 1);
			sys.subSystems.get(fn).addNode(inner);
			sys.setVar("local", new DataValueString("kept"));

			CompoundTag tag = sys.writeToNBT(new CompoundTag());
			helper.assertTrue(tag.getAllKeys().equals(Set.of("N", "V")), "a graph is N for nodes and V for variables");
			helper.assertTrue(tag.getCompound("N").getAllKeys().equals(Set.of("n0", "n1", "n2", "n3", "n4")), "nodes are n0..n4 in order");
			helper.assertTrue(tag.getCompound("N").getCompound("n4").contains("SS"), "a function node carries its sub-graph as SS");
			helper.assertTrue(!tag.getCompound("N").getCompound("n0").contains("SS"), "and a plain node does not");
			helper.assertTrue(tag.getCompound("N").getCompound("n2").getString("nodeType").equals("gt_add"), "each node names its type");

			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(tag);
			helper.assertTrue(back.nodes.size() == 5, "five nodes read back");
			helper.assertTrue(back.nodes.get(0) instanceof InNode && back.nodes.get(1) instanceof ConstNode && back.nodes.get(2) instanceof AddNode
					&& back.nodes.get(3) instanceof OutNode && back.nodes.get(4) instanceof FnNode, "of the right classes, in order");
			helper.assertTrue(back.outputNodes.size() == 1 && back.outputNodes.get(0) == back.nodes.get(3), "the output node is tracked");
			helper.assertTrue(back.getVar("local").toString().equals("kept") && back.getVar("absent").getNumber() == 0F, "variables read back, an absent one is 0");
			Node rin = back.nodes.get(0);
			helper.assertTrue(rin.posX == 3 && rin.posY == 4 && rin.size == in.size, "position and size read back");
			helper.assertTrue(rin.outputs.size() == 2 && rin.outputs.get(1).type == DataType.ENUM && rin.outputs.get(1).defaultValue.equals(new DataValueEnum<>(Direction.NORTH)),
					"sockets read back, enum default included");
			AddNode radd = (AddNode) back.nodes.get(2);
			helper.assertTrue(radd.inputs.get(0).connection == rin && radd.inputs.get(0).connectionIndex == 0 && radd.inputs.get(0).drawsLine,
					"a connection points at the node object, by index");
			helper.assertTrue(radd.inputs.get(1).connection == back.nodes.get(1), "both of them do");
			helper.assertTrue(back.subSystems.get(back.nodes.get(4)) != null && back.subSystems.get(back.nodes.get(4)).nodes.size() == 1
					&& back.subSystems.get(back.nodes.get(4)).nodes.get(0) instanceof OutNode, "the sub-graph read back");
			helper.assertTrue(back.writeToNBT(new CompoundTag()).equals(tag), "and writing it again gives the same tree");

			// The loaded graph computes the same answer.
			TestControl c2 = new TestControl(p, 1, 1, 1);
			back.parent = c2;
			c2.receiveNodeMap.put("lever_toggle", back);
			c2.receiveEvent(ControlEvent.newEvent("lever_toggle").setVar("isOn", 4));
			helper.assertTrue(((OutNode) back.nodes.get(3)).seen.equals(List.of(6.5F)), "4 + 2.5 after a reload, got " + ((OutNode) back.nodes.get(3)).seen);
		});
		helper.succeed();
	}

	/** One output of a given type, for the placeholder test. */
	static final class TypedNode extends Node {
		TypedNode(DataType type) {
			super(0, 0);
			outputs.add(new NodeConnection("o", this, 0, false, type, defaultFor(type)));
			recalcSize();
		}

		@Override public DataValue evaluate(int idx) { return outputs.get(idx).defaultValue; }
		@Override public float[] getColor() { return new float[]{0, 0, 0}; }
		@Override public String getDisplayName() { return "typed"; }

		@Override
		public CompoundTag writeToNBT(CompoundTag tag, NodeSystem sys) {
			tag.putString("nodeType", "gt_typed");
			return super.writeToNBT(tag, sys);
		}
	}

	@GameTest(template = EMPTY)
	public static void aNodeNobodyRecognisesSurvivesAsAPlaceholder(GameTestHelper helper) {
		final ControlPanel p = panel(helper);
		final TestControl c = new TestControl(p, 1, 1, 1);
		final CompoundTag[] holder = new CompoundTag[1];
		withLoader(() -> {
			NodeSystem built = new NodeSystem(c);
			InNode in = new InNode(1, 2).with("isOn", new DataValueFloat(0));
			ConstNode k = new ConstNode(3, 4, 9F);
			AddNode add = new AddNode(5, 6);
			built.addNode(in);
			built.addNode(k);
			built.addNode(add);
			wire(add, 0, in, 0);
			wire(add, 1, k, 0);
			holder[0] = built.writeToNBT(new CompoundTag());
		});
		CompoundTag tag = holder[0];

		// No loader registered now: the way a panel saved with an addon node loads without the addon.
		NodeSystem sys = new NodeSystem(c);
		sys.readFromNBT(tag);
		helper.assertTrue(sys.nodes.size() == 3, "all three nodes load");
		for(Node n : sys.nodes)
			helper.assertTrue(n instanceof UnknownNode, "each is a placeholder: " + n.getClass().getSimpleName());
		UnknownNode un = (UnknownNode) sys.nodes.get(2);
		helper.assertTrue(un.getDisplayName().equals("Unknown") && java.util.Arrays.equals(un.getColor(), new float[]{1, 1, 1}), "it says Unknown, in white");
		helper.assertTrue(un.inputs.size() == 2 && un.outputs.size() == 1, "with its sockets");
		helper.assertTrue(un.inputs.get(0).connection == sys.nodes.get(0), "and its connections");
		helper.assertTrue(Float.isNaN(un.evaluate(0).getNumber()), "a number output evaluates to NaN");
		helper.assertTrue(sys.writeToNBT(new CompoundTag()).equals(tag), "and writing the graph back gives the original tree, node types and all");

		// The other output types.
		NodeSystem typedSrc = new NodeSystem(c);
		for(DataType type : DataType.VALUES) typedSrc.addNode(new TypedNode(type));
		NodeSystem typed = new NodeSystem(c);
		typed.readFromNBT(typedSrc.writeToNBT(new CompoundTag()));
		helper.assertTrue(typed.nodes.size() == 5, "five typed placeholders");
		helper.assertTrue(typed.nodes.get(DataType.GENERIC.ordinal()).evaluate(0).toString().equals("ERROR"), "a generic output is the text ERROR");
		helper.assertTrue(typed.nodes.get(DataType.STRING.ordinal()).evaluate(0).toString().equals("ERROR"), "so is a string");
		helper.assertTrue(typed.nodes.get(DataType.COMPOSITE.ordinal()).evaluate(0) instanceof DataValueComposite, "a composite is an empty pack");
		boolean threw = false;
		try {
			typed.nodes.get(DataType.ENUM.ordinal()).evaluate(0);
		} catch(UnsupportedOperationException e) {
			threw = true;
		}
		helper.assertTrue(threw, "an enum output has nothing to invent and throws");
		helper.succeed();
	}

	private static DataValue defaultFor(DataType t) {
		return switch(t) {
			case GENERIC, STRING -> new DataValueString("");
			case NUMBER -> new DataValueFloat(0);
			case ENUM -> new DataValueEnum<>(Direction.UP);
			case COMPOSITE -> new DataValueComposite();
		};
	}

	@GameTest(template = EMPTY)
	public static void aSocketReadsItsDefaultOrItsConnection(GameTestHelper helper) {
		AddNode add = new AddNode(0, 0);
		ConstNode k = new ConstNode(0, 0, 4F);
		helper.assertTrue(add.inputs.get(0).evaluate().getNumber() == 0F, "an unconnected input answers its default");
		add.inputs.get(0).defaultValue = new DataValueFloat(3);
		helper.assertTrue(add.inputs.get(0).evaluate().getNumber() == 3F, "which can be set");
		wire(add, 0, k, 0);
		helper.assertTrue(add.inputs.get(0).evaluate().getNumber() == 4F, "connected, it answers the other node");
		NodeConnection removed = add.inputs.get(0).removeConnection();
		helper.assertTrue(removed == k.outputs.get(0) && add.inputs.get(0).connection == null && add.inputs.get(0).connectionIndex == -1 && !add.inputs.get(0).drawsLine,
				"removing a connection returns the output it was on and clears the input");
		helper.assertTrue(add.inputs.get(0).removeConnection() == null, "removing an absent one returns null");
		helper.assertTrue(add.outputs.get(0).connection == null && add.outputs.get(0).connectionIndex == -1, "an output never holds a connection");

		// Sizes and boxes.
		helper.assertTrue(add.size == (0 + 2 + 1) * 8 + 2, "a node is 8 per row plus 2: 3 rows is 26, got " + add.size);
		helper.assertTrue(java.util.Arrays.equals(add.getBoundingBox(), new float[]{0, 0, 40, 32}), "its box is 40 wide and 6 plus its size tall");
		helper.assertTrue(java.util.Arrays.equals(add.getExtendedBoundingBox(), new float[]{-2, 0, 42, 32}), "the extended box is 2 either side");
		add.setPosition(10, 20);
		NodeConnection in0 = add.inputs.get(0);
		NodeConnection in1 = add.inputs.get(1);
		NodeConnection out0 = add.outputs.get(0);
		helper.assertTrue(out0.offsetX == 10 && out0.offsetY == 20 + 0 * 8, "the first output sits at the node's origin row, got " + out0.offsetY);
		helper.assertTrue(in0.offsetY == 20 + 8 && in1.offsetY == 20 + 16, "inputs sit below the outputs, 8 apart, got " + in0.offsetY + " and " + in1.offsetY);
		helper.assertTrue(java.util.Arrays.equals(in0.getPortBox(), new float[]{8, 36, 12, 40}), "an input's port is centred on its left edge, got " + java.util.Arrays.toString(in0.getPortBox()));
		helper.assertTrue(java.util.Arrays.equals(out0.getPortBox(), new float[]{48, 28, 52, 32}), "an output's on its right edge, 40 across");
		helper.assertTrue(java.util.Arrays.equals(in0.getValueBox(), new float[]{13, 35, 47, 41}), "and the value box is a 34 by 6 strip, got " + java.util.Arrays.toString(in0.getValueBox()));
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void typingIntoASocketReadsItAsItsType(GameTestHelper helper) {
		NodeConnection num = new NodeConnection("n", new ConstNode(0, 0, 0), 0, true, DataType.NUMBER, new DataValueFloat(1.5F));
		num.startTyping();
		helper.assertTrue(num.isTyping() && num.builder.toString().equals("1.5"), "typing starts with the current value");
		num.builder.setLength(0);
		num.builder.append("42");
		num.stopTyping();
		helper.assertTrue(!num.isTyping() && num.builder == null && num.defaultValue.getNumber() == 42F && num.defaultValue instanceof DataValueFloat,
				"a number socket reads the text as a number");

		NodeConnection str = new NodeConnection("s", new ConstNode(0, 0, 0), 0, true, DataType.STRING, new DataValueString("a"));
		str.startTyping();
		str.builder.append("bc");
		str.stopTyping();
		helper.assertTrue(str.defaultValue.toString().equals("abc") && str.defaultValue instanceof DataValueString, "a string socket keeps the text");

		NodeConnection en = new NodeConnection("e", new ConstNode(0, 0, 0), 0, true, DataType.ENUM, new DataValueEnum<>(Direction.NORTH));
		helper.assertTrue(java.util.Arrays.equals(en.enumNames(), new String[]{"DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"}), "an enum socket lists its constants by name");
		en.startTyping();
		en.builder.setLength(0);
		en.builder.append("east");
		en.stopTyping();
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.EAST, "typed text matching a constant, ignoring case, selects it");
		en.startTyping();
		en.builder.setLength(0);
		en.builder.append("9");
		en.stopTyping();
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.SOUTH, "typed text that is a number selects by ordinal, mod the count: 9 mod 6 is 3");
		en.startTyping();
		en.builder.setLength(0);
		en.builder.append("-2");
		en.stopTyping();
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.NORTH, "and a negative one by its absolute value: 2");
		en.startTyping();
		en.builder.setLength(0);
		en.builder.append("-2147483648");
		en.stopTyping();
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.NORTH, "the most negative int does not overflow into a crash: |-2^31| mod 6 is 2");
		en.selectEnum("WEST");
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.WEST, "selectEnum is the selector's callback");
		en.selectEnum("sideways");
		helper.assertTrue(en.defaultValue.getEnum(Direction.class) == Direction.WEST, "an unknown name changes nothing");
		helper.assertTrue(num.enumNames().length == 0, "a number socket has no constants");
		helper.assertTrue(en.enumSelector == null, "and with no client factory there is no selector widget on a server");
		helper.succeed();
	}
}
