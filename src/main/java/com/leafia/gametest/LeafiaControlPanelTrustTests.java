package com.leafia.gametest;

import java.util.List;

import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeFunction;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeMath;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Custom Control Panel's trust boundary (CCP-PANEL, D-3063): every way a hostile client's
 * whole-panel edit is refused, each sent as a real tag and each checked for <i>why</i> it was refused as well as that it was, and
 * for the panel being left exactly as it was. NTM-CE applied the edit for any sender; these are the cases that would have worked.
 *
 * <p>The positive case comes first, because a gate that refuses everything proves nothing: an edit from a player with the editor
 * open, in reach, linking only what a held linker presented, is applied. Then every refusal is a one-field mutation of that case.
 *
 * <p>Nothing here evaluates a hostile graph: the point of the cycle and size checks is that the server never runs one. The tests
 * that read a cyclic panel back would overflow the stack if it did.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelTrustTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	/** The panel is untouched and the edit was refused for a reason containing {@code expect}. */
	private static void refused(GameTestHelper helper, ControlPanelTE te, ServerPlayer player, CompoundTag edit, String expect) {
		CompoundTag before = te.panel.writeToNBT(new CompoundTag());
		List<BlockPos> linksBefore = ControlPanelGuard.linksOf(te.panel);
		ControlPanel panelBefore = te.panel;
		String why = te.applyFullSet(player, edit);
		helper.assertTrue(why != null, "the edit should have been refused (" + expect + ")");
		helper.assertTrue(why.contains(expect), "refused, but for '" + why + "' where '" + expect + "' was expected");
		helper.assertTrue(te.panel == panelBefore && te.panel.writeToNBT(new CompoundTag()).equals(before), "a refused edit leaves the panel exactly as it was");
		helper.assertTrue(ControlPanelGuard.linksOf(te.panel).equals(linksBefore), "and its links are the same");
	}

	private static CompoundTag goodEdit(GameTestHelper helper, ControlPanelTE te, BlockPos link) {
		return LeafiaControlPanelKit.edit(te, panel -> {
			TestControl c = LeafiaControlPanelKit.control(panel, 2, 3);
			c.taggedLinks.put("door", link);
			c.vars.put("label", new DataValueString("open"));
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "ctrl_press");
			sys.addNode(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("door_toggle"))));
			panel.globalVars.put("mode", new DataValueFloat(1));
		});
	}

	private static BlockPos near(GameTestHelper helper) {
		return helper.absolutePos(AT.east(4));
	}

	// ------------------------------------------------------------------ the case that is allowed

	@GameTest(template = EMPTY)
	public static void anEditFromAPlayerWithTheEditorOpenInReachIsApplied(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			ControlEventSystem system = ControlEventSystem.get(helper.getLevel());

			CompoundTag edit = goodEdit(helper, te, door);
			String why = te.applyFullSet(player, edit);
			helper.assertTrue(why == null, "a good edit is applied, got: " + why);
			helper.assertTrue(te.panel.controls.size() == 1 && te.panel.controls.get(0) instanceof TestControl, "the control is on the panel");
			Control c = te.panel.controls.get(0);
			helper.assertTrue(c.posX == 2 && c.posY == 3 && c.getVar("label").toString().equals("open") && c.taggedLinks.get("door").equals(door), "with its place, variable and link");
			helper.assertTrue(c.receiveNodeMap.containsKey("ctrl_press") && te.panel.getVar("mode").getNumber() == 1F, "its graph and the global");
			helper.assertTrue(te.panel.parent == te && te.panel.transform != null, "the new panel belongs to this block entity and has its transform");
			helper.assertTrue(system.isSubscribed(te, door), "the panel is subscribed to the link it now has");

			// An edit that drops the link unsubscribes it; a geometry change inside the editor's rules is taken.
			CompoundTag drop = LeafiaControlPanelKit.edit(te, panel -> {
				LeafiaControlPanelKit.control(panel, 0, 0);
				panel.height = 0.5F;
				panel.angle = (float) Math.toRadians(-30);
				panel.a_off = 0.1F;
				panel.c_off = 0.2F;
			});
			helper.assertTrue(te.applyFullSet(player, drop) == null, "an edit with no links and a new slab is applied");
			helper.assertTrue(!system.isSubscribed(te, door), "the link it dropped is no longer subscribed");
			helper.assertTrue(te.panel.height == 0.5F && te.panel.a_off == 0.1F, "and the slab changed");

			// A link the panel already had needs no linker (the editor re-sends every link it keeps).
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			helper.assertTrue(te.applyFullSet(player, goodEdit(helper, te, door)) == null, "re-adding it with the linker held is fine");
			LeafiaControlPanelKit.hold(player, ItemStack.EMPTY);
			helper.assertTrue(te.applyFullSet(player, goodEdit(helper, te, door)) == null, "and an edit that keeps a link already there needs no linker in hand");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ who may edit

	@GameTest(template = EMPTY)
	public static void anEditNeedsTheEditorOpenForThisPanel(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ControlPanelTE other = LeafiaControlPanelKit.place(helper, AT.east(6));
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			CompoundTag edit = goodEdit(helper, te, door);

			player.containerMenu = player.inventoryMenu;
			refused(helper, te, player, edit, "no editor open");

			player.containerMenu = new LeafiaControlPanelKit.EditMenu(other);
			refused(helper, te, player, edit, "no editor open");

			player.containerMenu = new LeafiaControlPanelKit.EditMenu(te);
			helper.assertTrue(te.applyFullSet(player, edit) == null, "with the right editor open the same edit is applied");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void anEditNeedsReach(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			CompoundTag edit = goodEdit(helper, te, door);

			LeafiaControlPanelKit.stand(player, te, 60);
			refused(helper, te, player, edit, "reach");
			LeafiaControlPanelKit.stand(player, te, 4);
			helper.assertTrue(te.applyFullSet(player, edit) == null, "four blocks away is within reach, and the edit goes through");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aHostilePacketThroughTheRealHandlerIsRefused(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos far = helper.absolutePos(AT).offset(40_000, 0, 0);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(far));
			CompoundTag evil = goodEdit(helper, te, far);

			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT), evil), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel.controls.isEmpty(), "the base mod's packet handler delivered it, and the panel refused a link forty thousand blocks away");

			// A position with no panel, and one that is not loaded, are not looked at.
			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT.east(5)), evil), LeafiaControlPanelKit.context(player));
			NBTControlPacket.handle(new NBTControlPacket(new BlockPos(3_000_000, 70, 3_000_000), evil), LeafiaControlPanelKit.context(player));

			// Neither key present: nothing happens.
			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT), new CompoundTag()), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel.controls.isEmpty(), "a packet with neither full_set nor click_control does nothing");

			// The same edit with a link that is fine is applied by the same handler.
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT), goodEdit(helper, te, door)), LeafiaControlPanelKit.context(player));
			helper.assertTrue(te.panel.controls.size() == 1, "an honest edit through the same handler is applied");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ where a link may point

	@GameTest(template = EMPTY)
	public static void aLinkMustBeInRangeAndPresentedByAHeldLinker(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);

			// In range, but no linker anywhere.
			refused(helper, te, player, goodEdit(helper, te, door), "not presented");

			// A linker that names some other position.
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door.above(), door.north()));
			refused(helper, te, player, goodEdit(helper, te, door), "not presented");

			// An item that carries the same arrays but is not a linker.
			ItemStack stick = new ItemStack(Items.STICK);
			CustomData.update(DataComponents.CUSTOM_DATA, stick, tag -> {
				tag.putIntArray("xValues", new int[]{door.getX()});
				tag.putIntArray("yValues", new int[]{door.getY()});
				tag.putIntArray("zValues", new int[]{door.getZ()});
			});
			LeafiaControlPanelKit.hold(player, stick);
			refused(helper, te, player, goodEdit(helper, te, door), "not presented");

			// Presented, but farther than the radius.
			BlockPos far = helper.absolutePos(AT).offset(LeafiaConfig.controlPanelLinkRadius + 1, 0, 0);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(far));
			refused(helper, te, player, goodEdit(helper, te, far), "farther than");

			// A linker with ragged arrays is read as far as it goes and never throws.
			ItemStack ragged = LeafiaControlPanelKit.linker(door);
			CustomData.update(DataComponents.CUSTOM_DATA, ragged, tag -> tag.putIntArray("zValues", new int[0]));
			LeafiaControlPanelKit.hold(player, ragged);
			refused(helper, te, player, goodEdit(helper, te, door), "not presented");

			// Held in the main hand, the off hand, or the linker slot: any of the three presents.
			LeafiaControlPanelKit.hold(player, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, LeafiaControlPanelKit.linker(door));
			helper.assertTrue(te.applyFullSet(player, goodEdit(helper, te, door)) == null, "the off hand presents");
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			te.panel.controls.clear();
			te.inventory.setStackInSlot(0, LeafiaControlPanelKit.linker(door));
			helper.assertTrue(te.applyFullSet(player, goodEdit(helper, te, door)) == null, "and so does the one in the linker slot");
			te.inventory.setStackInSlot(0, ItemStack.EMPTY);
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRadiusAppliesToLinksThePanelAlreadyHas(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));
			helper.assertTrue(te.applyFullSet(player, goodEdit(helper, te, door)) == null, "linked at the default radius");

			int before = LeafiaConfig.controlPanelLinkRadius;
			try {
				LeafiaConfig.controlPanelLinkRadius = 2;
				LeafiaControlPanelKit.hold(player, ItemStack.EMPTY);
				refused(helper, te, player, goodEdit(helper, te, door), "farther than");
			} finally {
				LeafiaConfig.controlPanelLinkRadius = before;
			}
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ enums

	private static CompoundTag enumTag(String key, String id, int ordinal) {
		CompoundTag t = new CompoundTag();
		t.putString(key, id);
		t.putInt("ordinal", ordinal);
		return t;
	}

	@GameTest(template = EMPTY)
	public static void anEnumMustComeFromTheClosedRegistry(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));

			// The tripwire: a class that sets a flag the moment it is initialised. Naming it must never initialise it.
			LeafiaControlPanelKit.tripped = false;
			String tripwire = LeafiaControlPanelKit.Tripwire.class.getName();

			for(String key : new String[]{"enum", "clazz"}) {
				CompoundTag edit = goodEdit(helper, te, door);
				edit.getCompound("controls").getCompound("control0").getCompound("vars").put("hostile", enumTag(key, tripwire, 0));
				refused(helper, te, player, edit, "not in the registry");
			}

			CompoundTag edit = goodEdit(helper, te, door);
			edit.getCompound("controls").getCompound("control0").getCompound("vars").put("hostile", enumTag("enum", "direction", 99));
			refused(helper, te, player, edit, "out of range");
			edit.getCompound("controls").getCompound("control0").getCompound("vars").put("hostile", enumTag("enum", "direction", -1));
			refused(helper, te, player, edit, "out of range");

			// In a global variable.
			edit = goodEdit(helper, te, door);
			edit.getCompound("globalvars").put("hostile", enumTag("enum", tripwire, 0));
			refused(helper, te, player, edit, "not in the registry");

			// Deep in a graph: a socket's default value, where the node loader would have swallowed it as a number.
			edit = goodEdit(helper, te, door);
			CompoundTag node = edit.getCompound("controls").getCompound("control0").getCompound("RN").getCompound("ctrl_press").getCompound("N").getCompound("n0");
			node.getCompound("in").getCompound("con0").put("D", enumTag("enum", tripwire, 0));
			refused(helper, te, player, edit, "not in the registry");

			// In a config of a control.
			edit = goodEdit(helper, te, door);
			edit.getCompound("controls").getCompound("control0").getCompound("configs").put("hostile", enumTag("enum", tripwire, 0));
			refused(helper, te, player, edit, "not in the registry");

			helper.assertTrue(!LeafiaControlPanelKit.tripped, "no class named by a client was ever initialised");

			// A registered enum with a good ordinal is fine.
			edit = goodEdit(helper, te, door);
			edit.getCompound("controls").getCompound("control0").getCompound("vars").put("ok", enumTag("enum", "dye_color", 3));
			helper.assertTrue(te.applyFullSet(player, edit) == null, "a registered enum with an ordinal in range is accepted");
			helper.assertTrue(te.panel.controls.get(0).getVar("ok").getNumber() == 3F, "and read as that ordinal");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ size

	@GameTest(template = EMPTY)
	public static void anOversizeGraphIsRefused(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));

			// Exactly at the node ceiling is fine; one past it is not.
			CompoundTag atCeiling = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				for(int i = 0; i < ControlPanelGuard.MAX_NODES; i++) sys.addNode(new NodeMath(0, 0));
			});
			helper.assertTrue(ControlPanelGuard.scan(atCeiling) == null, "2048 nodes pass the scan");
			CompoundTag past = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				for(int i = 0; i <= ControlPanelGuard.MAX_NODES; i++) sys.addNode(new NodeMath(0, 0));
			});
			refused(helper, te, player, past, "nodes");

			// The ceiling is for the whole panel, summed over controls and events.
			CompoundTag spread = LeafiaControlPanelKit.edit(te, panel -> {
				for(int k = 0; k < 9; k++) {
					TestControl c = LeafiaControlPanelKit.control(panel, k, 0);
					NodeSystem a = LeafiaControlPanelKit.receiveSystem(c, "tick");
					NodeSystem b = LeafiaControlPanelKit.sendSystem(c, "lever_toggle");
					for(int i = 0; i < 120; i++) {
						a.addNode(new NodeMath(0, 0));
						b.addNode(new NodeMath(0, 0));
					}
				}
			});
			refused(helper, te, player, spread, "nodes");

			// Too many controls.
			CompoundTag crowd = LeafiaControlPanelKit.edit(te, panel -> {
				for(int i = 0; i <= ControlPanelGuard.MAX_CONTROLS; i++) LeafiaControlPanelKit.control(panel, i % 10, i / 10);
			});
			refused(helper, te, player, crowd, "controls");

			// Too many links on one control, and on the panel.
			CompoundTag links = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				for(int i = 0; i <= ControlPanelGuard.MAX_LINKS_PER_CONTROL; i++) c.taggedLinks.put("l" + i, door.above(i));
			});
			refused(helper, te, player, links, "links on a control");

			// Too many event graphs on one control.
			CompoundTag events = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				for(int i = 0; i <= ControlPanelGuard.MAX_SYSTEMS_PER_CONTROL; i++) LeafiaControlPanelKit.receiveSystem(c, "e" + i);
			});
			refused(helper, te, player, events, "event graphs");

			// Function nodes inside function nodes, nine deep.
			CompoundTag nested = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				for(int depth = 0; depth <= ControlPanelGuard.MAX_SYSTEM_DEPTH; depth++) {
					NodeFunction fn = new NodeFunction(0, 0);
					sys.addNode(fn);
					sys = sys.subSystems.get(fn);
				}
			});
			refused(helper, te, player, nested, "nested deeper");

			// A tag nested past the depth ceiling in a place the semantic walk does not look.
			CompoundTag deep = goodEdit(helper, te, door);
			CompoundTag cursor = deep.getCompound("globalvars");
			for(int i = 0; i < ControlPanelGuard.MAX_TAG_DEPTH + 5; i++) {
				CompoundTag next = new CompoundTag();
				cursor.put("x", next);
				cursor = next;
			}
			refused(helper, te, player, deep, "nested deeper");

			// A chain of nodes longer than evaluation may recurse through: acyclic, and still refused.
			CompoundTag chain = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				Node previous = null;
				for(int i = 0; i < ControlPanelGuard.MAX_CHAIN + 5; i++) {
					NodeMath m = new NodeMath(0, 0);
					sys.addNode(m);
					if(previous != null) m.inputs.get(0).setData(previous, 0, true);
					previous = m;
				}
			});
			refused(helper, te, player, chain, "chain");

			// Right at the limits, a big but sane panel is accepted.
			CompoundTag ok = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				Node previous = null;
				for(int i = 0; i < ControlPanelGuard.MAX_CHAIN; i++) {
					NodeMath m = new NodeMath(0, 0);
					sys.addNode(m);
					if(previous != null) m.inputs.get(0).setData(previous, 0, true);
					previous = m;
				}
			});
			helper.assertTrue(te.applyFullSet(player, ok) == null, "a chain of exactly 128 is accepted: " + ControlPanelGuard.scan(ok));
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ cycles

	@GameTest(template = EMPTY)
	public static void aCyclicGraphIsRefusedAndNeverEvaluated(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(door));

			// Two nodes feeding each other.
			CompoundTag pair = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(0, 0);
				sys.addNode(a);
				sys.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
				b.inputs.get(0).setData(a, 0, true);
			});
			refused(helper, te, player, pair, "cycle");

			// A node feeding itself.
			CompoundTag self = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath a = new NodeMath(0, 0);
				sys.addNode(a);
				a.inputs.get(1).setData(a, 0, true);
			});
			refused(helper, te, player, self, "cycle");

			// A longer ring, in a send graph.
			CompoundTag ring = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.sendSystem(c, "lever_toggle");
				NodeMath[] n = new NodeMath[5];
				for(int i = 0; i < n.length; i++) {
					n[i] = new NodeMath(0, 0);
					sys.addNode(n[i]);
				}
				for(int i = 0; i < n.length; i++) n[i].inputs.get(0).setData(n[(i + 1) % n.length], 0, true);
			});
			refused(helper, te, player, ring, "cycle");

			// A cycle inside a function node's body.
			CompoundTag body = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeFunction fn = new NodeFunction(0, 0);
				sys.addNode(fn);
				NodeSystem inner = sys.subSystems.get(fn);
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(0, 0);
				inner.addNode(a);
				inner.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
				b.inputs.get(1).setData(a, 0, true);
			});
			refused(helper, te, player, body, "cycle");

			// A connection to a socket the node does not have.
			CompoundTag stray = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(0, 0);
				sys.addNode(a);
				sys.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
			});
			stray.getCompound("controls").getCompound("control0").getCompound("RN").getCompound("tick").getCompound("N").getCompound("n0")
					.getCompound("in").getCompound("con0").putInt("Ci", 40);
			refused(helper, te, player, stray, "socket");

			// And a connection to a node index that is not in the system.
			CompoundTag missing = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(0, 0);
				sys.addNode(a);
				sys.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
			});
			missing.getCompound("controls").getCompound("control0").getCompound("RN").getCompound("tick").getCompound("N").getCompound("n0")
					.getCompound("in").getCompound("con0").putInt("Ni", 9000);
			refused(helper, te, player, missing, "unsafe or unreadable");

			// An acyclic diamond (a node read twice) is not a cycle and is accepted.
			CompoundTag diamond = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath top = new NodeMath(0, 0);
				NodeMath left = new NodeMath(0, 0);
				NodeMath right = new NodeMath(0, 0);
				NodeMath bottom = new NodeMath(0, 0);
				sys.addNode(top);
				sys.addNode(left);
				sys.addNode(right);
				sys.addNode(bottom);
				left.inputs.get(0).setData(top, 0, true);
				right.inputs.get(0).setData(top, 0, true);
				bottom.inputs.get(0).setData(left, 0, true);
				bottom.inputs.get(1).setData(right, 0, true);
			});
			helper.assertTrue(te.applyFullSet(player, diamond) == null, "a diamond is not a cycle");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aPanelThatReadsACycleOffDiskDoesNotRunIt(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			CompoundTag pair = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				NodeMath a = new NodeMath(0, 0);
				NodeMath b = new NodeMath(0, 0);
				sys.addNode(a);
				sys.addNode(b);
				a.inputs.get(0).setData(b, 0, true);
				b.inputs.get(0).setData(a, 0, true);
				// Feed the loop into an output so a naive evaluation would recurse.
				sys.addNode(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("lever_toggle"))));
				((NodeEventBroadcast) sys.nodes.get(2)).inputs.get(0).setData(a, 0, true);
			});
			helper.assertTrue(te.panel.readFromNBT(pair), "the tree itself is readable");
			helper.assertTrue(te.panel.controls.size() == 1 && te.panel.controls.get(0) instanceof UnknownControl, "but the control is inert");
			ControlEventSystem.get(helper.getLevel()).broadcastEvent(te.getBlockPos(), ControlEvent.newEvent("tick"), te.getBlockPos());
			te.panel.receiveEvent(te.getBlockPos(), ControlEvent.newEvent("tick")); // would be a StackOverflowError
			helper.assertTrue(te.panel.controls.get(0).writeToNBT(new CompoundTag()).getCompound("RN").equals(pair.getCompound("controls").getCompound("control0").getCompound("RN")),
					"and it still holds the graph it was given");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ loops through events

	@GameTest(template = EMPTY)
	public static void anEventBroadcastIntoItselfIsRefused(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);
			BlockPos own = helper.absolutePos(AT);
			BlockPos door = near(helper);
			LeafiaControlPanelKit.hold(player, LeafiaControlPanelKit.linker(own, door));
			ControlEvent lever = ControlEvent.getRegisteredEvent("lever_toggle");
			ControlEvent doorToggle = ControlEvent.getRegisteredEvent("door_toggle");

			// A control that receives lever_toggle and broadcasts lever_toggle to a link that is this very panel.
			CompoundTag self = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("me", own);
				LeafiaControlPanelKit.receiveSystem(c, "lever_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
			});
			refused(helper, te, player, self, "its own event");

			// A send graph for an event that broadcasts that event.
			CompoundTag send = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("door", door);
				LeafiaControlPanelKit.sendSystem(c, "lever_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
			});
			refused(helper, te, player, send, "leads back");

			// Two send graphs that broadcast each other's events.
			CompoundTag mutual = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("door", door);
				LeafiaControlPanelKit.sendSystem(c, "lever_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(doorToggle)));
				LeafiaControlPanelKit.sendSystem(c, "door_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
			});
			refused(helper, te, player, mutual, "leads back");

			// The same, with the broadcast inside a function body.
			CompoundTag hidden = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("door", door);
				NodeSystem sys = LeafiaControlPanelKit.sendSystem(c, "lever_toggle");
				NodeFunction fn = new NodeFunction(0, 0);
				sys.addNode(fn);
				sys.subSystems.get(fn).addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
			});
			refused(helper, te, player, hidden, "leads back");

			// What is fine: receive one event, broadcast a different one, to this panel or to another.
			CompoundTag fine = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("me", own);
				c.taggedLinks.put("door", door);
				LeafiaControlPanelKit.receiveSystem(c, "lever_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(doorToggle)));
				LeafiaControlPanelKit.sendSystem(c, "door_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
				// door_toggle's send graph broadcasts lever_toggle, which has no send graph: a chain, not a ring.
			});
			helper.assertTrue(te.applyFullSet(player, fine) == null, "a different event, and a send chain that ends, are accepted");

			// And the same self-event to another panel's position is the run-time guard's business, not the edit's.
			CompoundTag elsewhere = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				c.taggedLinks.put("door", door);
				LeafiaControlPanelKit.receiveSystem(c, "lever_toggle").addNode(new NodeEventBroadcast(0, 0, List.of(lever)));
			});
			helper.assertTrue(te.applyFullSet(player, elsewhere) == null, "re-sending the same event to a different position is left to the delivery depth");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ geometry and unknown controls

	@GameTest(template = EMPTY)
	public static void theSlabMustBeOneTheEditorCouldMake(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

			float nan = Float.NaN;
			float inf = Float.POSITIVE_INFINITY;
			Object[][] bad = {
					{"height", nan}, {"height", inf}, {"height", -0.5F}, {"height", 1.5F},
					{"angle", nan}, {"angle", (float) Math.toRadians(60)}, {"angle", (float) -Math.toRadians(60)},
					{"a_offset", nan}, {"a_offset", -0.1F}, {"a_offset", 1.5F},
					{"b_offset", inf}, {"b_offset", 2F}, {"c_offset", -inf()}, {"c_offset", 7F}, {"d_offset", nan}, {"d_offset", -3F},
					// each inset fine alone, but the two on a side meeting or crossing
					{"a_offset", 0.9F}, {"d_offset", 1F},
			};
			for(Object[] row : bad) {
				CompoundTag edit = LeafiaControlPanelKit.edit(te, p -> {});
				edit.putFloat((String) row[0], (Float) row[1]);
				refused(helper, te, player, edit, "geometry");
			}
			// A slab whose far edge would stand more than a block tall.
			CompoundTag tall = LeafiaControlPanelKit.edit(te, p -> {
				p.height = 1F;
				p.angle = (float) Math.toRadians(40);
			});
			refused(helper, te, player, tall, "geometry");

			// A missing key.
			CompoundTag missing = LeafiaControlPanelKit.edit(te, p -> {});
			missing.remove("angle");
			refused(helper, te, player, missing, "missing 'angle'");

			// The editor's own extremes are fine.
			CompoundTag extreme = LeafiaControlPanelKit.edit(te, p -> {
				p.height = 0.5F;
				p.angle = (float) Math.toRadians(45);
				p.a_off = 0.1F;
				p.b_off = 0.5F;
				p.c_off = 0.3F;
				p.d_off = 0.4F;
			});
			helper.assertTrue(ControlPanel.geometryValid(0.5F, (float) Math.toRadians(45), 0.1F, 0.5F, 0.3F, 0.4F), "the slab is one the rule allows");
			helper.assertTrue(te.applyFullSet(player, extreme) == null, "and the edit is taken");
		});
		helper.succeed();
	}

	private static float inf() {
		return Float.POSITIVE_INFINITY;
	}

	@GameTest(template = EMPTY)
	public static void anUnknownControlCanOnlyBeCarriedThrough(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

			// A control from an addon that is no longer installed is on the panel already.
			CompoundTag raw = new CompoundTag();
			raw.putString("name", "gone_addon_dial");
			raw.putString("myName", "Dial");
			raw.putFloat("X", 1);
			raw.putFloat("Y", 2);
			CompoundTag links = new CompoundTag();
			raw.put("taglnk", links);
			te.panel.controls.add(new UnknownControl(te.panel, raw));

			// The editor sends it back as it is, moved: accepted, and still the same control.
			CompoundTag moved = LeafiaControlPanelKit.edit(te, p -> p.controls.add(new UnknownControl(p, raw)));
			moved.getCompound("controls").getCompound("control0").putFloat("X", 7);
			moved.getCompound("controls").getCompound("control0").putString("myName", "Renamed");
			helper.assertTrue(te.applyFullSet(player, moved) == null, "an unknown control carried through (moved, renamed) is accepted: " + te.applyFullSet(player, moved));
			helper.assertTrue(te.panel.controls.get(0) instanceof UnknownControl u && u.posX == 7 && u.name.equals("Renamed"), "and it is the same placeholder");

			// One that was never there.
			CompoundTag rawNew = raw.copy();
			rawNew.putString("name", "invented_control");
			CompoundTag invented = LeafiaControlPanelKit.edit(te, p -> p.controls.add(new UnknownControl(p, rawNew)));
			refused(helper, te, player, invented, "was not on the panel");

			// The same name with its contents changed: a link smuggled in to be woken later by the addon coming back.
			CompoundTag smuggled = raw.copy();
			CompoundTag taglnk = new CompoundTag();
			taglnk.putIntArray("far", new int[]{999_999, 70, 999_999});
			smuggled.put("taglnk", taglnk);
			CompoundTag swapped = LeafiaControlPanelKit.edit(te, p -> p.controls.add(new UnknownControl(p, smuggled)));
			refused(helper, te, player, swapped, "was not on the panel");

			// A registered control that will not load is not an "unknown control": it is refused.
			CompoundTag broken = LeafiaControlPanelKit.edit(te, p -> LeafiaControlPanelKit.control(p, 0, 0));
			broken.getCompound("controls").getCompound("control0").getCompound("vars").putByteArray("junk", new byte[]{1});
			// (a var that is not a value reads as null and makes the control unloadable)
			refused(helper, te, player, broken, "unsafe or unreadable");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void noRefusalCostsAnythingOnTheServer(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			// The scan is a few walks over the tag and builds nothing: a hundred refusals of a maximal bad tag are quick.
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			CompoundTag past = LeafiaControlPanelKit.edit(te, panel -> {
				TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
				NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
				for(int i = 0; i <= ControlPanelGuard.MAX_NODES; i++) sys.addNode(new NodeMath(0, 0));
			});
			long start = System.nanoTime();
			for(int i = 0; i < 100; i++)
				helper.assertTrue(ControlPanelGuard.scan(past) != null, "refused");
			long ms = (System.nanoTime() - start) / 1_000_000;
			helper.assertTrue(ms < 5000, "a hundred scans of a 2049-node tag took " + ms + " ms");
			helper.assertTrue(ControlPanelGuard.scan(null) != null && ControlPanelGuard.scan(new CompoundTag()) == null, "a null tag is refused and an empty one passes");
			helper.assertTrue(Tag.TAG_COMPOUND == 10, "the constants the scan relies on");
		});
		helper.succeed();
	}
}
