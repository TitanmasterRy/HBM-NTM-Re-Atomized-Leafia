package com.leafia.gametest;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.IPersistentNBT;
import com.hbm.gametest.TickWindow;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlEventSystem;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelGuard;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSetup;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.IRedstonePanel;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.UnknownControl;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelFrontBlock;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelType;
import com.leafia.contents.machines.controlpanel.core.modular.NTMControlPanelRegistry;
import com.leafia.contents.machines.controlpanel.core.modular.StockNodeLoader;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeEventBroadcast;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneInput;
import com.leafia.contents.machines.controlpanel.core.nodes.NodeRedstoneOutput;
import com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket;
import com.leafia.contents.machines.controlpanel.core.packet.ControlPanelUpdatePacket.VarUpdate;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;
import com.leafia.contents.machines.controlpanel.core.types.DataValueFloat;
import com.leafia.contents.machines.controlpanel.core.types.DataValueString;
import com.leafia.gametest.LeafiaControlPanelFixtures.DirectHost;
import com.leafia.gametest.LeafiaControlPanelKit.TestControl;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;

import api.hbm.block.IToolable.ToolType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Custom Control Panel block (CCP-PANEL, D-3063): that it exists with upstream's numbers, its shape, the redstone it reads
 * and writes, the graph that joins them, a click that reaches a linked machine, the panel riding the dropped item, the skin,
 * the update packet, and the lifecycle with the event system. The refusals a hostile client meets are in
 * {@link LeafiaControlPanelTrustTests}.
 *
 * <p>Every body is synchronous on the server thread except the two that wait on the level tick, which open their window through
 * {@link TickWindow} (D-1262). A test that registers a control, a tool, an editor opener or changes a config static puts it back in
 * a {@code finally} (D-952).
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelBlockTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;

	// ------------------------------------------------------------------ the block

	@GameTest(template = EMPTY)
	public static void theBlocksExistWithTheirUpstreamNumbers(GameTestHelper helper) {
		Block custom = LeafiaBlocks.control_panel_custom.get();
		Block front = LeafiaBlocks.control_panel_front.get();

		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(custom).toString().equals("leafia:control_panel_custom"), "the desk block's id");
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(front).toString().equals("leafia:control_panel_front"), "the front's id");
		helper.assertTrue(custom instanceof ControlPanelBlock && !(custom instanceof ControlPanelFrontBlock) && front instanceof ControlPanelFrontBlock,
				"the front is the desk block's subclass");

		// NTM-CE: setHardness(0.25F).setResistance(2.0F), and 1.12.2 divides the stored 3x by 5; the front's commented-out ctor: 3F and 30F.
		helper.assertTrue(custom.defaultDestroyTime() == 0.25F, "the desk block's hardness is 0.25, got " + custom.defaultDestroyTime());
		helper.assertTrue(Math.abs(custom.getExplosionResistance() - 1.2F) < 1E-5F, "and its resistance 1.2, got " + custom.getExplosionResistance());
		helper.assertTrue(front.defaultDestroyTime() == 3.0F && front.getExplosionResistance() == 18.0F,
				"the front's hardness 3 and resistance 18, got " + front.defaultDestroyTime() + " and " + front.getExplosionResistance());

		helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(LeafiaBlockEntities.CONTROL_PANEL.get()).toString().equals("leafia:control_panel"),
				"one block entity type serves both");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("leafia:control_panel_custom"))
				&& BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("leafia:control_panel_front")), "each has an item");
		helper.assertTrue(custom.defaultBlockState().isSignalSource(), "a panel is a signal source");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void eachPanelTypeStartsWithItsOwnSlab(GameTestHelper helper) {
		ControlPanelTE desk = LeafiaControlPanelKit.place(helper, AT);
		ControlPanelTE front = LeafiaControlPanelKit.placeFront(helper, AT.east(3));

		helper.assertTrue(desk.panelType == ControlPanelType.CUSTOM_PANEL && front.panelType == ControlPanelType.FRONT_PANEL, "the type follows the block");
		ControlPanel d = desk.panel;
		helper.assertTrue(d.height == 0.25F && d.angle == (float) Math.toRadians(20) && d.a_off == 0 && d.b_off == 0 && d.c_off == 0.25F && d.d_off == 0,
				"the desk slab: height 0.25, 20 degrees, insets 0, 0, 0.25, 0");
		ControlPanel f = front.panel;
		float frontHeight = (float) (1 - Math.tan(Math.toRadians(11.31)) / 2);
		helper.assertTrue(f.height == frontHeight && f.angle == (float) Math.toRadians(11.31) && f.a_off == 0 && f.b_off == 0 && f.c_off == 0.08F && f.d_off == 0,
				"the front slab: height 1 - tan(11.31 deg) / 2, 11.31 degrees, insets 0, 0, 0.08, 0");
		helper.assertTrue(d.geometryValid() && f.geometryValid(), "both starting slabs are ones the editor would accept");
		helper.assertTrue(desk.panel.parent == desk && desk.panel.controls.isEmpty() && desk.getInEvents().equals(List.of("setGlobalVar", "tick")),
				"it owns its panel, which is empty, and takes setGlobalVar (Leafia's) and tick");
		helper.succeed();
	}

	private static void assertBox(GameTestHelper helper, AABB box, double x0, double y0, double z0, double x1, double y1, double z1, String what) {
		double e = 1E-4;
		boolean ok = Math.abs(box.minX - x0) < e && Math.abs(box.minY - y0) < e && Math.abs(box.minZ - z0) < e
				&& Math.abs(box.maxX - x1) < e && Math.abs(box.maxY - y1) < e && Math.abs(box.maxZ - z1) < e;
		helper.assertTrue(ok, what + ": expected (" + x0 + ", " + y0 + ", " + z0 + ")-(" + x1 + ", " + y1 + ", " + z1 + "), got " + box);
	}

	@GameTest(template = EMPTY)
	public static void theShapeFollowsTheSlabAndTheFacing(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		double h0 = 0.25 + 0.5 * Math.tan(Math.toRadians(20)); // the low edge's height: height - (1/2) tan(-angle)
		double h1 = h0 - Math.tan(Math.toRadians(20)) * 0.75;
		double top = Math.max(h0, h1);

		assertBox(helper, te.getBoundingBox(true, false, Direction.SOUTH), 0, 0, 0, 1, top, 0.75, "up, south (as designed)");
		assertBox(helper, te.getBoundingBox(true, false, Direction.NORTH), 0, 0, 0.25, 1, top, 1, "up, north (turned half way round)");
		assertBox(helper, te.getBoundingBox(true, false, Direction.EAST), 0, 0, 0, 0.75, top, 1, "up, east");
		assertBox(helper, te.getBoundingBox(true, false, Direction.WEST), 0.25, 0, 0, 1, top, 1, "up, west");
		assertBox(helper, te.getBoundingBox(false, true, Direction.SOUTH), 0, 1 - top, 0, 1, 1, 0.75, "down hangs from the ceiling");
		assertBox(helper, te.getBoundingBox(false, false, Direction.SOUTH), 0, 0.25, 0, 1, 1, top, "on a wall it stands on its low edge");

		// The block asks the block entity for the same thing.
		BlockState up = LeafiaBlocks.control_panel_custom.get().defaultBlockState().setValue(ControlPanelBlock.UP, true).setValue(ControlPanelBlock.FACING, Direction.NORTH);
		helper.setBlock(AT, up);
		AABB shape = helper.getLevel().getBlockState(helper.absolutePos(AT)).getShape(helper.getLevel(), helper.absolutePos(AT)).bounds();
		assertBox(helper, shape, 0, 0, 0.25, 1, top, 1, "the block's own shape, up and facing north");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void thePanelToBlockTransformSitsOnTheRightSurface(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		helper.assertTrue(te.panel.transform != null && te.panel.invTransform != null, "the transform is built when the panel loads");

		// Panel local (0, 0, 0) -> block space: for an upward-clicked, south-facing... the origin of the grid is the slab's centre line.
		BlockState up = LeafiaBlocks.control_panel_custom.get().defaultBlockState().setValue(ControlPanelBlock.UP, true).setValue(ControlPanelBlock.FACING, Direction.NORTH);
		helper.setBlock(AT, up);
		ControlPanelTE desk = (ControlPanelTE) helper.getBlockEntity(AT);
		var origin = new org.joml.Vector4f(0, 0, 0, 1).mul(desk.panel.transform);
		var far = new org.joml.Vector4f(0, 0, 10, 1).mul(desk.panel.transform);
		helper.assertTrue(origin.x >= -0.01F && origin.x <= 1.01F && origin.z >= -0.01F && origin.z <= 1.01F, "the grid's origin is inside the block's footprint, got " + origin);
		float span = (float) Math.sqrt(far.distance(origin) * far.distance(origin));
		helper.assertTrue(Math.abs(far.distance(origin) - 1F) < 1E-3F, "ten grid units are one block long (scale 0.1), got " + span);

		// The inverse really is the inverse, and a ray down onto a control picks it.
		TestControl c = new TestControl(desk.panel);
		c.posX = 0;
		c.posY = 0;
		desk.panel.controls.add(c);
		Vec3 target = new Vec3(origin.x, origin.y + 0.02, origin.z);
		Control picked = desk.panel.pick(target.add(0, 2, 0), new Vec3(0, -1, 0));
		helper.assertTrue(picked == c, "a ray from above onto the control's top picks it");
		helper.assertTrue(desk.panel.pick(new Vec3(50, 50, 50), new Vec3(0, -1, 0)) == null, "and one that misses picks nothing");
		helper.succeed();
	}

	// ------------------------------------------------------------------ redstone

	/** A parent that is a redstone panel and nothing else, so the redstone nodes can be read without a block. */
	private static final class FakeRedstone implements IControllable, IRedstonePanel {
		final int[] power = new int[6];
		final int[] weak = new int[6];
		final int[] strong = new int[6];
		final int[] outWeak = new int[6];
		final int[] outStrong = new int[6];

		@Override public int getRedstoneInputPower(Direction f) { return power[f.get3DDataValue()]; }
		@Override public int getRedstoneInputWeak(Direction f) { return weak[f.get3DDataValue()]; }
		@Override public int getRedstoneInputStrong(Direction f) { return strong[f.get3DDataValue()]; }
		@Override public void setWeakRedstoneOutput(Direction f, int s) { outWeak[f.get3DDataValue()] = s; }
		@Override public void setStrongRedstoneOutput(Direction f, int s) { outStrong[f.get3DDataValue()] = s; }
		@Override public void beginRedstoneOutputCollection() {}
		@Override public void finishRedstoneOutputCollection() {}
		@Override public BlockPos getControlPos() { return BlockPos.ZERO; }
		@Override public net.minecraft.world.level.Level getControlWorld() { return null; }
	}

	private static void pick(NodeRedstoneInput node, int dropdown, String choice) {
		((NodeDropdown) node.otherElements.get(dropdown)).choose(choice);
	}

	@GameTest(template = EMPTY)
	public static void theRedstoneInputNodeReadsEachModeOffTheOwner(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			FakeRedstone owner = new FakeRedstone();
			ControlPanel panel = new ControlPanel(owner);
			TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
			owner.power[Direction.EAST.get3DDataValue()] = 9;
			owner.weak[Direction.EAST.get3DDataValue()] = 7;
			owner.strong[Direction.EAST.get3DDataValue()] = 0;
			owner.power[Direction.WEST.get3DDataValue()] = 4;

			String[][] cases = {
					{"Power", "9"}, {"Weak Power", "7"}, {"Strong Power", "0"}, {"Is Powered", "1"}, {"Is Weak", "1"}, {"Is Strong", "0"}};
			for(String[] row : cases) {
				NodeRedstoneInput node = new NodeRedstoneInput(0, 0, c);
				pick(node, 0, "east");
				pick(node, 1, row[0]);
				float got = node.evaluate(0).getNumber();
				helper.assertTrue(got == Float.parseFloat(row[1]), row[0] + " on east reads " + row[1] + ", got " + got);
			}
			NodeRedstoneInput node = new NodeRedstoneInput(0, 0, c);
			helper.assertTrue(node.facing() == Direction.NORTH && node.modeLabel().equals("Power"), "it starts on north, reading power");
			pick(node, 0, "WEST");
			helper.assertTrue(node.facing() == Direction.WEST, "the face matches ignoring case");
			helper.assertTrue(node.evaluate(0).getNumber() == 4F, "and reads that face");
			owner.power[Direction.WEST.get3DDataValue()] = 12;
			helper.assertTrue(node.evaluate(0).getNumber() == 4F, "a value is cached until the graph is reset");
			node.cacheValid = false;
			helper.assertTrue(node.evaluate(0).getNumber() == 12F, "and read again after");

			// An owner that is not a redstone panel reads 0, not a crash.
			ControlPanel plain = new ControlPanel(new LeafiaControlPanelFixtures.Recorder(BlockPos.ZERO, null));
			TestControl pc = LeafiaControlPanelKit.control(plain, 0, 0);
			helper.assertTrue(new NodeRedstoneInput(0, 0, pc).evaluate(0).getNumber() == 0F, "a panel whose owner is no redstone panel reads 0");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRedstoneOutputNodeWritesWeakAndStrong(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			FakeRedstone owner = new FakeRedstone();
			ControlPanel panel = new ControlPanel(owner);
			TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");

			NodeRedstoneOutput weak = new NodeRedstoneOutput(0, 0);
			((NodeDropdown) weak.otherElements.get(0)).choose("up");
			weak.inputs.get(0).defaultValue = new DataValueFloat(2.5F);
			NodeRedstoneOutput strong = new NodeRedstoneOutput(0, 0);
			((NodeDropdown) strong.otherElements.get(0)).choose("south");
			((NodeDropdown) strong.otherElements.get(1)).choose("Strong");
			strong.inputs.get(0).defaultValue = new DataValueFloat(40F);
			sys.addNode(weak);
			sys.addNode(strong);

			helper.assertTrue(weak.doOutput(owner, c.sendNodeMap, c.taggedLinks) && strong.doOutput(owner, c.sendNodeMap, c.taggedLinks), "an output never cancels");
			helper.assertTrue(owner.outWeak[Direction.UP.get3DDataValue()] == 3, "2.5 rounds to 3 on the weak output of up, got " + owner.outWeak[1]);
			helper.assertTrue(owner.outStrong[Direction.SOUTH.get3DDataValue()] == 40, "the node hands the strength on; the panel clamps it");
			helper.assertTrue(weak.facing() == Direction.UP && weak.modeLabel().equals("Weak") && strong.modeLabel().equals("Strong"), "the face and mode took");

			// A caller that is not a redstone panel is ignored.
			weak.doOutput(new LeafiaControlPanelFixtures.Recorder(BlockPos.ZERO, null), c.sendNodeMap, c.taggedLinks);
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theRedstoneNodesSaveAndReadHostileIndices(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelSetup.commonSetup();
			ControlPanel panel = new ControlPanel(new FakeRedstone());
			TestControl c = LeafiaControlPanelKit.control(panel, 0, 0);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");

			NodeRedstoneInput in = new NodeRedstoneInput(1, 2, c);
			pick(in, 0, "west");
			pick(in, 1, "Is Strong");
			NodeRedstoneOutput out = new NodeRedstoneOutput(3, 4);
			((NodeDropdown) out.otherElements.get(0)).choose("down");
			((NodeDropdown) out.otherElements.get(1)).choose("Strong");
			sys.addNode(in);
			sys.addNode(out);
			out.inputs.get(0).setData(in, 0, true);

			CompoundTag tag = sys.writeToNBT(new CompoundTag());
			CompoundTag inTag = tag.getCompound("N").getCompound("n0");
			helper.assertTrue(inTag.getString("nodeType").equals("redstoneInput") && inTag.getInt("facing") == Direction.WEST.get3DDataValue()
					&& inTag.getInt("mode") == 5, "the input saves its type, the 1.12.2 face index and the mode's ordinal");
			CompoundTag outTag = tag.getCompound("N").getCompound("n1");
			helper.assertTrue(outTag.getString("nodeType").equals("redstoneOutput") && outTag.getInt("facing") == Direction.DOWN.get3DDataValue() && outTag.getInt("mode") == 1,
					"and the output its");

			NodeSystem back = new NodeSystem(c);
			back.readFromNBT(tag);
			NodeRedstoneInput in2 = (NodeRedstoneInput) back.nodes.get(0);
			NodeRedstoneOutput out2 = (NodeRedstoneOutput) back.nodes.get(1);
			helper.assertTrue(in2.facing() == Direction.WEST && in2.modeLabel().equals("Is Strong") && out2.facing() == Direction.DOWN && out2.modeLabel().equals("Strong"),
					"both come back as they were saved");
			helper.assertTrue(out2.inputs.get(0).connection == in2, "with the wire between them");

			// A hostile index is reduced, not thrown on.
			for(int bad : new int[]{-3, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
				CompoundTag t = sys.writeToNBT(new CompoundTag());
				t.getCompound("N").getCompound("n0").putInt("mode", bad);
				t.getCompound("N").getCompound("n0").putInt("facing", bad);
				t.getCompound("N").getCompound("n1").putInt("mode", bad);
				t.getCompound("N").getCompound("n1").putInt("facing", bad);
				NodeSystem hostile = new NodeSystem(c);
				hostile.readFromNBT(t);
				helper.assertTrue(hostile.nodes.get(0) instanceof NodeRedstoneInput && hostile.nodes.get(1) instanceof NodeRedstoneOutput, "index " + bad + " still loads both nodes");
			}

			boolean loaderKnows = false;
			for(var l : NTMControlPanelRegistry.nbtNodeLoaders) loaderKnows |= l instanceof StockNodeLoader;
			helper.assertTrue(loaderKnows, "the setup registered the stock loader, which knows these two");
		});
		helper.succeed();
	}

	/** Redstone in, through a graph, to redstone out - on a real panel, with the real tick. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void redstoneInGoesThroughTheGraphAndOut(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			TestControl c = LeafiaControlPanelKit.control(te.panel, 0, 0);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "redstone_input");
			NodeRedstoneInput in = new NodeRedstoneInput(0, 0, c);
			pick(in, 0, "south");
			NodeRedstoneOutput out = new NodeRedstoneOutput(0, 0);
			((NodeDropdown) out.otherElements.get(0)).choose("up");
			sys.addNode(in);
			sys.addNode(out);
			out.inputs.get(0).setData(in, 0, true);

			BlockPos abs = helper.absolutePos(AT);
			TickWindow.sequence(helper)
					.thenExecute(() -> helper.setBlock(AT.south(), Blocks.REDSTONE_BLOCK))
					.thenIdle(6)
					.thenExecute(() -> {
						helper.assertTrue(te.getWeakRedstoneOutput(Direction.UP) == 15, "the block to the south gave 15, so up is 15, got " + te.getWeakRedstoneOutput(Direction.UP));
						helper.assertTrue(te.getRedstoneInputPower(Direction.SOUTH) == 15, "and the panel reads 15 on its south face");
						BlockState state = helper.getLevel().getBlockState(abs);
						helper.assertTrue(state.getSignal(helper.getLevel(), abs, Direction.DOWN) == 15, "the block answers the neighbour above (asking along down) with the weak output of its up face (D-3075)");
						helper.assertTrue(state.getDirectSignal(helper.getLevel(), abs, Direction.DOWN) == 0, "and nothing strong");
						helper.setBlock(AT.south(), Blocks.AIR);
					})
					.thenIdle(6)
					.thenExecute(() -> {
						helper.assertTrue(te.getWeakRedstoneOutput(Direction.UP) == 0, "the source gone, the output follows it to 0, got " + te.getWeakRedstoneOutput(Direction.UP));
						helper.assertTrue(te.getRedstoneInputPower(Direction.SOUTH) == 0, "and the input reads 0");
					})
					.thenSucceed();
		});
	}

	@GameTest(template = EMPTY)
	public static void theOutputIsClampedPersistedAndGivenToNeighbours(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		BlockPos abs = helper.absolutePos(AT);

		te.setWeakRedstoneOutput(Direction.EAST, 99);
		te.setStrongRedstoneOutput(Direction.WEST, -5);
		te.setStrongRedstoneOutput(Direction.NORTH, 6);
		helper.assertTrue(te.getWeakRedstoneOutput(Direction.EAST) == 15 && te.getStrongRedstoneOutput(Direction.WEST) == 0 && te.getStrongRedstoneOutput(Direction.NORTH) == 6,
				"15 at most, 0 at least");
		BlockState state = helper.getLevel().getBlockState(abs);
		helper.assertTrue(state.getSignal(helper.getLevel(), abs, Direction.WEST) == 15 && state.getDirectSignal(helper.getLevel(), abs, Direction.SOUTH) == 6,
				"the block reports them");

		// Outputs set during one event take effect together, at the end of the outermost one.
		te.beginRedstoneOutputCollection();
		te.beginRedstoneOutputCollection();
		te.setWeakRedstoneOutput(Direction.DOWN, 5);
		te.finishRedstoneOutputCollection();
		helper.assertTrue(te.getWeakRedstoneOutput(Direction.DOWN) == 0, "an inner finish applies nothing");
		te.finishRedstoneOutputCollection();
		helper.assertTrue(te.getWeakRedstoneOutput(Direction.DOWN) == 5, "the outer one applies it");
		te.finishRedstoneOutputCollection(); // unbalanced: ignored, as CE ignores it
		helper.assertTrue(te.getWeakRedstoneOutput(Direction.DOWN) == 5, "an unbalanced finish changes nothing");

		CompoundTag saved = te.saveWithoutMetadata(helper.getLevel().registryAccess());
		helper.setBlock(AT.east(3), LeafiaBlocks.control_panel_custom.get().defaultBlockState());
		ControlPanelTE other = (ControlPanelTE) helper.getBlockEntity(AT.east(3));
		other.loadWithComponents(saved, helper.getLevel().registryAccess());
		helper.assertTrue(other.getWeakRedstoneOutput(Direction.EAST) == 15 && other.getWeakRedstoneOutput(Direction.DOWN) == 5 && other.getStrongRedstoneOutput(Direction.NORTH) == 6,
				"the outputs survive a save and a load");

		CompoundTag hostile = saved.copy();
		hostile.getCompound("redstoneWeakOut").putInt("east", 4000);
		hostile.getCompound("redstoneStrongOut").putInt("north", -4000);
		other.loadWithComponents(hostile, helper.getLevel().registryAccess());
		helper.assertTrue(other.getWeakRedstoneOutput(Direction.EAST) == 15 && other.getStrongRedstoneOutput(Direction.NORTH) == 0, "a saved strength is clamped on the way in");
		helper.succeed();
	}

	// ------------------------------------------------------------------ a panel event reaches a machine

	/** Builds a button-like control whose {@code ctrl_press} sends {@code lever_toggle} to its one link. */
	private static TestControl pressSendsLeverToggle(ControlPanelTE te, BlockPos link) {
		TestControl c = LeafiaControlPanelKit.control(te.panel, 0, 0);
		c.taggedLinks.put("lever", link);
		NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "ctrl_press");
		sys.addNode(new NodeEventBroadcast(0, 0, List.of(ControlEvent.getRegisteredEvent("lever_toggle"))));
		return c;
	}

	private static CompoundTag click(int index, String event, boolean sneaking) {
		CompoundTag tag = ControlEvent.newEvent(event.isEmpty() ? "ctrl_press" : event).setVar("isSneaking", sneaking).writeToNBT(new CompoundTag());
		tag.putInt("click_control", index);
		return tag;
	}

	@GameTest(template = EMPTY)
	public static void aPanelEventDrivesALinkedControllable(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			helper.setBlock(AT.east(3), LeafiaControlPanelFixtures.direct);
			DirectHost host = (DirectHost) helper.getBlockEntity(AT.east(3));
			host.rec.in = new ArrayList<>(List.of("lever_toggle"));

			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			pressSendsLeverToggle(te, helper.absolutePos(AT.east(3)));
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

			// Through the base mod's own control packet, as a client's click arrives.
			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT), click(0, "ctrl_press", false)), LeafiaControlPanelKit.context(player));

			helper.assertTrue(host.rec.count("lever_toggle") == 1, "the linked machine was sent one lever_toggle, got " + host.rec.count("lever_toggle"));
			var received = host.rec.received.get(0);
			helper.assertTrue(received.from().equals(helper.absolutePos(AT)), "from the panel's position");
			helper.assertTrue(received.event().vars.containsKey("isOn"), "carrying the event's variables");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aClickIsOnlyACtrlPressOnAControlInReach(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			helper.setBlock(AT.east(3), LeafiaControlPanelFixtures.direct);
			DirectHost host = (DirectHost) helper.getBlockEntity(AT.east(3));
			host.rec.in = new ArrayList<>(List.of("lever_toggle", "tick"));

			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			pressSendsLeverToggle(te, helper.absolutePos(AT.east(3)));
			ServerPlayer player = LeafiaControlPanelKit.editor(helper, te);

			helper.assertTrue(te.applyClick(player, click(0, "ctrl_press", true)), "a press on control 0 is taken");
			helper.assertTrue(host.rec.count("lever_toggle") == 1, "and delivered");

			helper.assertTrue(!te.applyClick(player, click(1, "ctrl_press", false)), "an index past the last control is refused");
			helper.assertTrue(!te.applyClick(player, click(-1, "ctrl_press", false)), "and a negative one");
			helper.assertTrue(!te.applyClick(player, click(Integer.MIN_VALUE, "ctrl_press", false)), "and the smallest int");
			helper.assertTrue(!te.applyClick(player, click(0, "tick", false)), "a click may not be a tick event (CE built any registered event from the client's bytes)");
			helper.assertTrue(!te.applyClick(player, click(0, "lever_toggle", false)), "nor any other event name");
			CompoundTag noName = click(0, "ctrl_press", false);
			noName.remove("name");
			helper.assertTrue(!te.applyClick(player, noName), "nor a click with no name");
			helper.assertTrue(host.rec.count("lever_toggle") == 1, "none of those reached the machine");

			LeafiaControlPanelKit.stand(player, te, 40);
			helper.assertTrue(!te.hasPermission(player) && !te.applyClick(player, click(0, "ctrl_press", false)), "forty blocks away is out of reach");
			helper.assertTrue(host.rec.count("lever_toggle") == 1, "and nothing was sent");

			// The real packet handler refuses it too, before the block entity is asked.
			NBTControlPacket.handle(new NBTControlPacket(helper.absolutePos(AT), click(0, "ctrl_press", false)), LeafiaControlPanelKit.context(player));
			helper.assertTrue(host.rec.count("lever_toggle") == 1, "the packet handler stopped at hasPermission");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ the item keeps the panel, and the skin

	private static ControlPanelTE dressedPanel(GameTestHelper helper, BlockPos at) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, at);
		TestControl c = LeafiaControlPanelKit.control(te.panel, 3, 4);
		c.name = "Big Red";
		c.vars.put("label", new DataValueString("scram"));
		c.taggedLinks.put("tank", helper.absolutePos(at.east(5)));
		LeafiaControlPanelKit.receiveSystem(c, "ctrl_press");
		te.panel.globalVars.put("mode", new DataValueFloat(2));
		te.panel.height = 0.4F;
		te.panel.angle = (float) Math.toRadians(30);
		te.panel.c_off = 0.1F;
		te.setSkin(Blocks.BRICKS);
		return te;
	}

	@GameTest(template = EMPTY)
	public static void theItemKeepsThePanel(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = dressedPanel(helper, AT);
			BlockPos abs = helper.absolutePos(AT);

			// What vanilla hands the block when it is mined: the drops, asked of the block with its block entity.
			List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(abs), helper.getLevel(), abs, te);
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(LeafiaBlocks.control_panel_custom.get().asItem()), "one panel item drops, and no plain one");
			ItemStack item = drops.get(0);
			helper.assertTrue(item.get(DataComponents.CUSTOM_DATA) != null, "carrying the panel");

			BlockPos second = AT.east(4);
			helper.setBlock(second, LeafiaBlocks.control_panel_custom.get().defaultBlockState());
			IPersistentNBT.restoreData(helper.getLevel(), helper.absolutePos(second), item);
			ControlPanelTE back = (ControlPanelTE) helper.getBlockEntity(second);

			helper.assertTrue(back.panel.controls.size() == 1 && back.panel.controls.get(0) instanceof TestControl, "the control came back");
			Control bc = back.panel.controls.get(0);
			helper.assertTrue(bc.name.equals("Big Red") && bc.posX == 3 && bc.posY == 4, "with its name and place");
			helper.assertTrue(bc.getVar("label").toString().equals("scram"), "and its variable");
			helper.assertTrue(bc.taggedLinks.get("tank").equals(helper.absolutePos(AT.east(5))), "and its link");
			helper.assertTrue(bc.receiveNodeMap.containsKey("ctrl_press"), "and its graph");
			helper.assertTrue(back.panel.getVar("mode").getNumber() == 2F, "the panel's global too");
			helper.assertTrue(back.panel.height == 0.4F && Math.abs(back.panel.angle - Math.toRadians(30)) < 1E-6 && back.panel.c_off == 0.1F, "and its slab");
			helper.assertTrue(back.getSkin() == Blocks.BRICKS, "and its skin");
			helper.assertTrue(back.panel.transform != null, "its transform was rebuilt for the new slab");

			// A new panel that never had anything restored has nothing.
			helper.setBlock(AT.east(8), LeafiaBlocks.control_panel_custom.get().defaultBlockState());
			ControlPanelTE fresh = (ControlPanelTE) helper.getBlockEntity(AT.east(8));
			helper.assertTrue(fresh.panel.controls.isEmpty() && fresh.getSkin() == null, "a panel placed from an item with no data is empty and plain");

			// The front drops as the front.
			ControlPanelTE front = LeafiaControlPanelKit.placeFront(helper, AT.east(12));
			TestControl fc = LeafiaControlPanelKit.control(front.panel, 1, 1);
			BlockPos fabs = helper.absolutePos(AT.east(12));
			List<ItemStack> fdrops = Block.getDrops(helper.getLevel().getBlockState(fabs), helper.getLevel(), fabs, front);
			helper.assertTrue(fdrops.size() == 1 && fdrops.get(0).is(LeafiaBlocks.control_panel_front.get().asItem()), "the front drops the front item");
			helper.assertTrue(fc != null, "ok");

			// Leafia's flag: a creative break is recorded (vanilla already drops nothing for it).
			helper.assertTrue(!te.isDestroyedByCreativePlayer(), "not flagged by default");
			te.setDestroyedByCreativePlayer();
			helper.assertTrue(te.isDestroyedByCreativePlayer(), "and flagged when set");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aBlockEntitySavesAndLoadsItsWholeState(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = dressedPanel(helper, AT);
			te.setWeakRedstoneOutput(Direction.SOUTH, 7);
			CompoundTag saved = te.saveWithoutMetadata(helper.getLevel().registryAccess());
			helper.assertTrue(saved.getString("skin").equals("minecraft:bricks"), "the skin is saved as its registry id");
			helper.assertTrue(saved.contains("panel") && saved.contains("redstoneWeakOut") && saved.contains("redstoneStrongOut"), "and the panel and both redstone tables");

			ControlPanelTE other = LeafiaControlPanelKit.place(helper, AT.east(4));
			other.loadWithComponents(saved, helper.getLevel().registryAccess());
			helper.assertTrue(other.panel.writeToNBT(new CompoundTag()).equals(te.panel.writeToNBT(new CompoundTag())), "the panel is the same tree");
			helper.assertTrue(other.getSkin() == Blocks.BRICKS && other.getWeakRedstoneOutput(Direction.SOUTH) == 7, "with the skin and the outputs");

			CompoundTag noSkin = saved.copy();
			noSkin.remove("skin");
			other.loadWithComponents(noSkin, helper.getLevel().registryAccess());
			helper.assertTrue(other.getSkin() == null, "a save with no skin loads plain");
			CompoundTag junk = saved.copy();
			junk.putString("skin", "no such:block");
			other.loadWithComponents(junk, helper.getLevel().registryAccess());
			helper.assertTrue(other.getSkin() == null, "a skin that names no block loads plain");
			junk.putString("skin", "minecraft:air");
			other.loadWithComponents(junk, helper.getLevel().registryAccess());
			helper.assertTrue(other.getSkin() == null, "and so does air");
			junk.putString("skin", "not a resource location!!");
			other.loadWithComponents(junk, helper.getLevel().registryAccess());
			helper.assertTrue(other.getSkin() == null, "and a malformed id");

			// The chunk-sync tag is the same data.
			helper.assertTrue(te.getUpdateTag(helper.getLevel().registryAccess()).equals(saved), "the update tag is the saved state");
			helper.assertTrue(te.getUpdatePacket() != null, "and there is a packet for it");
		});
		helper.succeed();
	}

	private static BlockHitResult hit(BlockPos abs) {
		return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
	}

	@GameTest(template = EMPTY)
	public static void aScrewdriverWithABlockInTheOtherHandSkinsThePanel(GameTestHelper helper) {
		ItemStack screwdriver = new ItemStack(Items.STICK);
		ToolType.SCREWDRIVER.register(screwdriver);
		ControlPanelTE.EditorOpener before = ControlPanelTE.editorOpener;
		try {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			BlockPos abs = helper.absolutePos(AT);
			ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
			BlockState state = helper.getLevel().getBlockState(abs);
			List<ControlPanelTE> opened = new ArrayList<>();
			ControlPanelTE.editorOpener = (p, panel) -> opened.add(panel);

			helper.assertTrue(ControlPanelBlock.isScrewdriver(screwdriver) && !ControlPanelBlock.isScrewdriver(new ItemStack(Items.STONE)), "a screwdriver is anything the base mod registers as one");

			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
			ItemInteractionResult r = state.useItemOn(screwdriver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			helper.assertTrue(r.consumesAction() && te.getSkin() == Blocks.STONE && opened.isEmpty(), "stone in the other hand dresses the panel as stone and opens no editor");

			// A block that is not drawn as a plain model is refused, as upstream's getRenderType test did.
			helper.assertTrue(Blocks.CHEST.defaultBlockState().getRenderShape() != RenderShape.MODEL, "a chest is not a plain model");
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CHEST));
			state.useItemOn(screwdriver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			helper.assertTrue(te.getSkin() == Blocks.STONE && opened.size() == 1, "a chest is not taken as a skin; the click goes on to the editor, which is called with the panel");
			helper.assertTrue(opened.get(0) == te, "and with this panel");

			// An empty other hand, or a non-block item there, goes to the editor.
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			state.useItemOn(screwdriver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
			state.useItemOn(screwdriver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			helper.assertTrue(opened.size() == 3 && te.getSkin() == Blocks.STONE, "the editor opens for each, the skin stays");

			// With no opener set (batch 6 has not landed) it does nothing, rather than opening a menu with no screen.
			ControlPanelTE.editorOpener = null;
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			ItemInteractionResult none = state.useItemOn(screwdriver, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			helper.assertTrue(none.consumesAction() && opened.size() == 3, "with no opener the click is consumed and nothing opens");

			// Not a screwdriver: the block defers, and a plain click is consumed without effect on the server.
			ItemInteractionResult passes = state.useItemOn(new ItemStack(Items.APPLE), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
			helper.assertTrue(passes == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, "anything else passes to the plain click");
			helper.assertTrue(state.useWithoutItem(helper.getLevel(), player, hit(abs)).consumesAction(), "and a plain click is consumed, as CE's was");

			// The skin sync: a client sets it from the packet's slot 0, and null clears it.
			te.onReceivePacketLocal((byte) 0, "minecraft:glass");
			helper.assertTrue(te.getSkin() == Blocks.GLASS, "slot 0 sets the skin on a client");
			te.onReceivePacketLocal((byte) 0, null);
			helper.assertTrue(te.getSkin() == null, "and null clears it");
			te.onReceivePacketLocal((byte) 0, "no such:block");
			helper.assertTrue(te.getSkin() == null, "an unknown id leaves it plain");
			helper.assertTrue(te.getPacketIdentifier().equals("CTRL_PNL") && te.affectionRange() == 256, "the identifier and range are upstream's mixin's");
			te.setSkin(Blocks.OAK_PLANKS);
			te.sendSkinPackets();
			var slots = te.writeSkin(com.leafia.dev.container_utility.LeafiaPacket._start(te)).build().decode(helper.getLevel().registryAccess());
			helper.assertTrue(slots.size() == 1 && slots.values().stream().anyMatch(p -> "minecraft:oak_planks".equals(p.value)), "the packet's one slot carries the skin's id");
			te.setSkin(null);
			slots = te.writeSkin(com.leafia.dev.container_utility.LeafiaPacket._start(te)).build().decode(helper.getLevel().registryAccess());
			helper.assertTrue(te.getSkin() == null && slots.size() == 1 && slots.values().stream().noneMatch(p -> p.value instanceof String), "a plain panel writes its one slot as null, not a name");
		} finally {
			ControlPanelTE.editorOpener = before;
			ToolType.SCREWDRIVER.unregister(screwdriver);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ the update packet

	private static RegistryFriendlyByteBuf buf(GameTestHelper helper) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
	}

	@GameTest(template = EMPTY)
	public static void theUpdatePacketRoundTrips(GameTestHelper helper) {
		BlockPos pos = new BlockPos(-5, 70, 123456);
		List<VarUpdate> updates = List.of(
				new VarUpdate(-1, "mode", new DataValueFloat(3)),
				new VarUpdate(0, "label", new DataValueString("scram")),
				new VarUpdate(7, "gone", null));
		RegistryFriendlyByteBuf b = buf(helper);
		ControlPanelUpdatePacket.STREAM_CODEC.encode(b, new ControlPanelUpdatePacket(pos, updates));
		ControlPanelUpdatePacket back = ControlPanelUpdatePacket.STREAM_CODEC.decode(b);
		helper.assertTrue(back.pos().equals(pos) && back.full() == null && back.updates().size() == 3, "a delta packet comes back with its position and three updates");
		helper.assertTrue(back.updates().get(0).varListIdx() == -1 && back.updates().get(0).varName().equals("mode") && back.updates().get(0).data().getNumber() == 3F, "the global");
		helper.assertTrue(back.updates().get(1).varListIdx() == 0 && back.updates().get(1).data().toString().equals("scram"), "the control's string");
		helper.assertTrue(back.updates().get(2).varListIdx() == 7 && back.updates().get(2).data() == null, "and a removal stays a removal");

		CompoundTag full = new CompoundTag();
		full.putFloat("height", 0.5F);
		RegistryFriendlyByteBuf f = buf(helper);
		ControlPanelUpdatePacket.STREAM_CODEC.encode(f, new ControlPanelUpdatePacket(pos, full));
		ControlPanelUpdatePacket fb = ControlPanelUpdatePacket.STREAM_CODEC.decode(f);
		helper.assertTrue(fb.full() != null && fb.full().getFloat("height") == 0.5F && fb.updates().isEmpty(), "a full set comes back as its tag");

		helper.assertTrue(ControlPanelUpdatePacket.TYPE.id().toString().equals("leafia:control_panel_update"), "the payload's id");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theUpdatePacketBoundsWhatItReads(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 2, 3);
		// A count past the ceiling is refused before anything is allocated.
		RegistryFriendlyByteBuf b = buf(helper);
		net.minecraft.core.BlockPos.STREAM_CODEC.encode(b, pos);
		b.writeBoolean(false);
		b.writeVarInt(ControlPanelUpdatePacket.MAX_UPDATES + 1);
		boolean threw = false;
		try {
			ControlPanelUpdatePacket.STREAM_CODEC.decode(b);
		} catch(RuntimeException e) {
			threw = true;
		}
		helper.assertTrue(threw, "more updates than the ceiling is refused");

		// A name longer than the ceiling is refused.
		RegistryFriendlyByteBuf n = buf(helper);
		net.minecraft.core.BlockPos.STREAM_CODEC.encode(n, pos);
		n.writeBoolean(false);
		n.writeVarInt(1);
		n.writeVarInt(0);
		n.writeUtf("x".repeat(ControlPanelUpdatePacket.MAX_NAME * 3), 32767);
		n.writeBoolean(false);
		threw = false;
		try {
			ControlPanelUpdatePacket.STREAM_CODEC.decode(n);
		} catch(RuntimeException e) {
			threw = true;
		}
		helper.assertTrue(threw, "a name past the ceiling is refused");

		// A value that does not parse is dropped, not read as a removal.
		RegistryFriendlyByteBuf v = buf(helper);
		net.minecraft.core.BlockPos.STREAM_CODEC.encode(v, pos);
		v.writeBoolean(false);
		v.writeVarInt(2);
		v.writeVarInt(0);
		v.writeUtf("bad", ControlPanelUpdatePacket.MAX_NAME);
		v.writeBoolean(true);
		net.minecraft.network.codec.ByteBufCodecs.TAG.encode(v, net.minecraft.nbt.IntTag.valueOf(5));
		v.writeVarInt(0);
		v.writeUtf("good", ControlPanelUpdatePacket.MAX_NAME);
		v.writeBoolean(true);
		net.minecraft.network.codec.ByteBufCodecs.TAG.encode(v, new DataValueFloat(1).writeToNBT());
		ControlPanelUpdatePacket read = ControlPanelUpdatePacket.STREAM_CODEC.decode(v);
		helper.assertTrue(read.updates().size() == 1 && read.updates().get(0).varName().equals("good"), "an unparsable value is dropped and the next one kept");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void variableChangesAreDiffedAndAppliedOnTheOtherSide(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			TestControl c = LeafiaControlPanelKit.control(te.panel, 0, 0);
			te.panel.update();
			te.panel.changedVars.clear();

			c.vars.put("lit", new DataValueFloat(1));
			te.panel.globalVars.put("mode", new DataValueFloat(2));
			te.panel.update();
			helper.assertTrue(te.panel.changedVars.size() == 2, "a new control variable and a new global are two updates, got " + te.panel.changedVars.size());
			List<VarUpdate> first = new ArrayList<>(te.panel.changedVars);

			te.panel.update();
			helper.assertTrue(te.panel.changedVars.isEmpty(), "an unchanged panel reports nothing the next tick");

			c.vars.put("lit", new DataValueFloat(0));
			te.panel.globalVars.remove("mode");
			te.panel.update();
			helper.assertTrue(te.panel.changedVars.size() == 2, "a change and a removal are two updates");
			boolean sawRemoval = false;
			for(VarUpdate u : te.panel.changedVars) sawRemoval |= u.varListIdx() == -1 && u.varName().equals("mode") && u.data() == null;
			helper.assertTrue(sawRemoval, "the removal carries no value");

			// Applied as a client would, on a second panel with the same control.
			ControlPanelTE client = LeafiaControlPanelKit.place(helper, AT.east(4));
			TestControl cc = LeafiaControlPanelKit.control(client.panel, 0, 0);
			client.applyServerUpdate(new ControlPanelUpdatePacket(client.getBlockPos(), first));
			helper.assertTrue(cc.getVar("lit").getNumber() == 1F && client.panel.getVar("mode").getNumber() == 2F, "the deltas land on the control and on the globals");
			client.applyServerUpdate(new ControlPanelUpdatePacket(client.getBlockPos(), new ArrayList<>(te.panel.changedVars)));
			helper.assertTrue(cc.getVar("lit").getNumber() == 0F && !client.panel.globalVars.containsKey("mode"), "and a removal removes");
			client.applyServerUpdate(new ControlPanelUpdatePacket(client.getBlockPos(), List.of(new VarUpdate(99, "x", new DataValueFloat(1)), new VarUpdate(-7, "y", new DataValueFloat(1)))));
			helper.assertTrue(client.panel.controls.size() == 1 && !client.panel.globalVars.containsKey("y"), "an index that is not a control is ignored, whatever its sign");

			CompoundTag whole = te.panel.writeToNBT(new CompoundTag());
			client.applyServerUpdate(new ControlPanelUpdatePacket(client.getBlockPos(), whole));
			helper.assertTrue(client.panel.writeToNBT(new CompoundTag()).equals(whole), "a full set replaces the panel");
		});
		helper.succeed();
	}

	// ------------------------------------------------------------------ the event system

	/** A block entity's {@code onLoad} runs on the level's next tick, so the registration is waited for, not assumed. */
	@GameTest(template = EMPTY, timeoutTicks = TickWindow.TIMEOUT)
	public static void aPanelJoinsAndLeavesItsLevelsEventSystem(GameTestHelper helper) {
		ControlEventSystem system = ControlEventSystem.get(helper.getLevel());
		ControlPanelTE[] second = new ControlPanelTE[1];
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		TickWindow.sequence(helper)
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(system.isValid(te) && system.tickTargets().contains(te), "a placed panel is registered, and takes the per-tick event");
					helper.assertTrue(system.resolve(helper.absolutePos(AT)) == te, "and is what its position resolves to");
					helper.setBlock(AT, Blocks.AIR);
				})
				.thenExecute(() -> {
					helper.assertTrue(!system.isValid(te) && !system.tickTargets().contains(te), "removed, it is gone from both");
					second[0] = LeafiaControlPanelKit.place(helper, AT);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(system.isValid(second[0]), "a new one registers");
					second[0].onChunkUnloaded();
					helper.assertTrue(!system.isValid(second[0]), "and an unloaded one is dropped (Leafia's mixin patched exactly this leak)");
					helper.setBlock(AT, Blocks.AIR);
				})
				.thenSucceed();
	}

	@GameTest(template = EMPTY)
	public static void aPanelSubscribesToItsLinksWhenItLoads(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			ControlEventSystem system = ControlEventSystem.get(helper.getLevel());
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			BlockPos target = helper.absolutePos(AT.east(4));
			TestControl c = LeafiaControlPanelKit.control(te.panel, 0, 0);
			c.taggedLinks.put("t", target);

			helper.assertTrue(!system.isSubscribed(te, target), "nothing is subscribed until the panel loads");
			te.onLoad();
			helper.assertTrue(system.isSubscribed(te, target), "loading subscribes to each link");

			system.removeControllable(te);
			helper.assertTrue(!system.isSubscribed(te, target), "leaving drops the subscription, as it does for any subscriber");
			helper.setBlock(AT, Blocks.AIR);
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aGlobalVariableCanBeSetByEvent(GameTestHelper helper) {
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		te.panel.globalVars.put("count", new DataValueFloat(1));
		te.panel.globalVars.put("name", new DataValueString("a"));

		te.receiveEvent(BlockPos.ZERO, ControlEvent.newEvent("setGlobalVar").setVar("name", "count").setVar("value", "5"));
		helper.assertTrue(te.panel.getVar("count") instanceof DataValueFloat && te.panel.getVar("count").getNumber() == 5F, "a number global takes the event's value as a number");
		te.receiveEvent(BlockPos.ZERO, ControlEvent.newEvent("setGlobalVar").setVar("name", "name").setVar("value", "bee"));
		helper.assertTrue(te.panel.getVar("name").toString().equals("bee"), "a string global takes it as it came");
		te.receiveEvent(BlockPos.ZERO, ControlEvent.newEvent("setGlobalVar").setVar("name", "nothing").setVar("value", "9"));
		helper.assertTrue(!te.panel.globalVars.containsKey("nothing"), "only an existing global can be set: the event does not make variables");

		// A hostile or malformed event, built by hand, is ignored where the mixin threw.
		ControlEvent bare = new ControlEvent("setGlobalVar");
		te.receiveEvent(BlockPos.ZERO, bare);
		ControlEvent noValue = new ControlEvent("setGlobalVar");
		noValue.setVar("name", "count");
		te.receiveEvent(BlockPos.ZERO, noValue);
		helper.assertTrue(te.panel.getVar("count").getNumber() == 5F, "an event missing its name or its value changes nothing and does not throw");
		helper.succeed();
	}

	/** A controllable that answers every event by sending it on to another position. */
	private static final class Echo implements IControllable {
		final BlockPos pos;
		final net.minecraft.world.level.Level level;
		BlockPos to;
		int received = 0;

		Echo(BlockPos pos, net.minecraft.world.level.Level level) {
			this.pos = pos;
			this.level = level;
		}

		@Override public List<String> getInEvents() { return List.of("lever_toggle"); }
		@Override public BlockPos getControlPos() { return pos; }
		@Override public net.minecraft.world.level.Level getControlWorld() { return level; }

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			received++;
			ControlEventSystem.get(level).broadcastEvent(pos, ControlEvent.newEvent("lever_toggle"), to);
		}
	}

	@GameTest(template = EMPTY)
	public static void twoMachinesEchoingAnEventAreStoppedByTheDeliveryDepth(GameTestHelper helper) {
		ControlEventSystem system = ControlEventSystem.get(helper.getLevel());
		Echo a = new Echo(helper.absolutePos(AT), helper.getLevel());
		Echo b = new Echo(helper.absolutePos(AT.east(2)), helper.getLevel());
		a.to = b.pos;
		b.to = a.pos;
		system.addControllable(a);
		system.addControllable(b);
		try {
			system.broadcastEvent(a.pos, ControlEvent.newEvent("lever_toggle"), b.pos);
			helper.assertTrue(a.received + b.received == ControlEventSystem.MAX_DELIVERY_DEPTH,
					"the echo ran to the depth ceiling and stopped: " + (a.received + b.received) + " deliveries");
			helper.assertTrue(ControlEventSystem.deliveryDepth() == 0, "and the depth is back to 0, so the next event is not starved");

			a.received = 0;
			b.received = 0;
			b.to = helper.absolutePos(AT.east(6)); // break the loop
			system.broadcastEvent(a.pos, ControlEvent.newEvent("lever_toggle"), b.pos);
			helper.assertTrue(b.received == 1 && a.received == 0, "with the loop broken, one event is one delivery");
		} finally {
			system.removeControllable(a);
			system.removeControllable(b);
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ setup, config, load-time hardening

	@GameTest(template = EMPTY)
	public static void theSetupIsIdempotentAndTheRadiusIsConfigured(GameTestHelper helper) {
		ControlPanelSetup.commonSetup();
		int loaders = NTMControlPanelRegistry.nbtNodeLoaders.size();
		ControlPanelSetup.commonSetup();
		helper.assertTrue(NTMControlPanelRegistry.nbtNodeLoaders.size() == loaders, "running the setup twice registers the loader once");

		helper.assertTrue(LeafiaConfig.controlPanelLinkRadius == 128 && ControlPanelGuard.linkRadius() == 128, "the link radius defaults to 128 blocks, which is this port's figure (the multi-detonator has none)");
		int before = LeafiaConfig.controlPanelLinkRadius;
		try {
			LeafiaConfig.controlPanelLinkRadius = 10;
			BlockPos panel = new BlockPos(0, 0, 0);
			helper.assertTrue(ControlPanelGuard.linkProblem(panel, new BlockPos(10, 0, 0), List.of(), List.of(new BlockPos(10, 0, 0))) == null, "a link exactly at the radius is in");
			helper.assertTrue(ControlPanelGuard.linkProblem(panel, new BlockPos(11, 0, 0), List.of(), List.of(new BlockPos(11, 0, 0))) != null, "one block past it is out");
			helper.assertTrue(ControlPanelGuard.linkProblem(panel, new BlockPos(7, 7, 0), List.of(), List.of(new BlockPos(7, 7, 0))) == null, "the radius is a sphere: 7 and 7 is 9.9 blocks out, which is in");
			helper.assertTrue(ControlPanelGuard.linkProblem(panel, new BlockPos(8, 7, 0), List.of(), List.of(new BlockPos(8, 7, 0))) != null, "and 8 and 7 is 10.6, which is out");
		} finally {
			LeafiaConfig.controlPanelLinkRadius = before;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aPanelReadsFromDiskOrItemWithoutTrustingIt(GameTestHelper helper) throws Exception {
		LeafiaControlPanelKit.withTestControl(() -> {
			// A control whose saved graph is a cycle loads as an inert placeholder with its tag intact.
			ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
			TestControl c = LeafiaControlPanelKit.control(te.panel, 1, 1);
			NodeSystem sys = LeafiaControlPanelKit.receiveSystem(c, "tick");
			var a = new com.leafia.contents.machines.controlpanel.core.nodes.NodeMath(0, 0);
			var b = new com.leafia.contents.machines.controlpanel.core.nodes.NodeMath(0, 0);
			sys.addNode(a);
			sys.addNode(b);
			a.inputs.get(0).setData(b, 0, true);
			b.inputs.get(0).setData(a, 0, true);
			CompoundTag saved = te.saveWithoutMetadata(helper.getLevel().registryAccess());

			ControlPanelTE other = LeafiaControlPanelKit.place(helper, AT.east(4));
			other.loadWithComponents(saved, helper.getLevel().registryAccess());
			helper.assertTrue(other.panel.controls.size() == 1 && other.panel.controls.get(0) instanceof UnknownControl, "the cyclic control is a placeholder on load");
			UnknownControl u = (UnknownControl) other.panel.controls.get(0);
			helper.assertTrue(u.problem != null && u.problem.contains("cycle"), "that says why: " + u.problem);
			helper.assertTrue(u.rawTag().getCompound("RN").equals(saved.getCompound("panel").getCompound("controls").getCompound("control0").getCompound("RN")), "and keeps the graph byte for byte");
			other.panel.receiveEvent(other.getBlockPos(), ControlEvent.newEvent("tick")); // would overflow the stack if the cycle ran
			other.panel.receiveEvent(other.getBlockPos(), ControlEvent.newEvent("tick"));
			helper.assertTrue(other.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("panel").getCompound("controls").getCompound("control0").getCompound("RN")
					.equals(u.rawTag().getCompound("RN")), "a placeholder saves back what it was given");

			// A panel with more controls than the ceiling is refused whole, and loads empty.
			CompoundTag huge = saved.copy();
			CompoundTag controls = huge.getCompound("panel").getCompound("controls");
			for(int i = 0; i < ControlPanelGuard.MAX_CONTROLS + 5; i++) {
				controls.put("control" + i, controls.getCompound("control0").copy());
			}
			other.loadWithComponents(huge, helper.getLevel().registryAccess());
			helper.assertTrue(other.panel.controls.isEmpty(), "a panel past the control ceiling loads empty, not truncated");

			// A tag with a negative-nesting-depth bomb: deeper than the ceiling.
			CompoundTag deep = new CompoundTag();
			CompoundTag cursor = deep;
			for(int i = 0; i < ControlPanelGuard.MAX_TAG_DEPTH + 10; i++) {
				CompoundTag next = new CompoundTag();
				cursor.put("x", next);
				cursor = next;
			}
			CompoundTag bomb = new CompoundTag();
			bomb.put("globalvars", deep);
			helper.assertTrue(ControlPanelGuard.scan(bomb) != null, "a tag nested past the ceiling is refused by the scan");
			helper.assertTrue(!new ControlPanel(other).readFromNBT(bomb) && other.panel.controls.isEmpty(), "and the panel read says so");
		});
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFrontPanelAlwaysStandsUpright(GameTestHelper helper) {
		BlockState front = LeafiaBlocks.control_panel_front.get().defaultBlockState();
		helper.assertTrue(!front.getValue(ControlPanelBlock.UP) && !front.getValue(ControlPanelBlock.DOWN), "the front has neither the up nor the down state");
		ControlPanelTE te = LeafiaControlPanelKit.placeFront(helper, AT);
		helper.assertTrue(te.panel.transform != null, "its transform is built");
		AABB box = te.getBoundingBox(false, false, Direction.SOUTH);
		helper.assertTrue(box.maxY > 0.9 && box.minY > 0.07, "an upright slab, nearly a block tall, got " + box);
		helper.succeed();
	}
}
