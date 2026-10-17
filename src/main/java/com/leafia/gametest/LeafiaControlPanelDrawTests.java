package com.leafia.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelClick;
import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSetup;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSlab;
import com.leafia.contents.machines.controlpanel.core.ControlRegistry;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector4f;

/**
 * The Custom Control Panel's drawing and clicking (CCP-DRAW, D-3065), as far as a dedicated server can reach it.
 *
 * <h2>What this cannot do, stated first</h2>
 * A GameTest server never builds a resource manager for {@code assets/} and cannot load a {@code @OnlyIn(Dist.CLIENT)} class,
 * so nothing here draws a pixel, and none of it proves a model is the right way up, a lamp is bright, or a text is legible. That
 * is the client check in D-3065. What it <i>can</i> pin, and what each is a failure this batch could have:
 *
 * <ul>
 * <li><b>The slab geometry against the collision box.</b> The slab is built by a rotation chain copied from CE; the only thing
 * that ties its facing table to the world is that its extent must be the block's collision box ({@code getBoundingBox}) for every
 * facing and placement. A swapped case would draw the slab beside the shape a player bumps into.</li>
 * <li><b>The generated assets against the table the registration walks.</b> A part that is drawn but has no model JSON, or whose
 * JSON hides the wrong objects, bakes to the missing-model cube or draws the whole control per part, and nothing logs it. Read as
 * plain resources, which ship in the jar.</li>
 * <li><b>The click's ray cast</b>, which is pure maths on the panel's transform, and the packet it builds, sent through the base
 * mod's own handler as a client's would be, then mutated hostilely.</li>
 * </ul>
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaControlPanelDrawTests {

	private static final String EMPTY = "empty16";
	private static final BlockPos AT = LeafiaControlPanelKit.AT;
	private static final float EPS = 1E-3F;

	// ------------------------------------------------------------------ the slab

	private static ControlPanelTE bareTE(boolean up, boolean down, Direction facing) {
		BlockState state = LeafiaBlocks.control_panel_custom.get().defaultBlockState()
				.setValue(ControlPanelBlock.UP, up).setValue(ControlPanelBlock.DOWN, down).setValue(ControlPanelBlock.FACING, facing);
		return new ControlPanelTE(BlockPos.ZERO, state);
	}

	private static boolean near(AABB a, AABB b) {
		return Math.abs(a.minX - b.minX) < EPS && Math.abs(a.minY - b.minY) < EPS && Math.abs(a.minZ - b.minZ) < EPS
				&& Math.abs(a.maxX - b.maxX) < EPS && Math.abs(a.maxY - b.maxY) < EPS && Math.abs(a.maxZ - b.maxZ) < EPS;
	}

	private static String show(AABB b) {
		return String.format("[%.3f %.3f %.3f -> %.3f %.3f %.3f]", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
	}

	/** The two slabs the editor can make that differ most: the default, and one with every inset and the slope different. */
	private static void shape(ControlPanel panel, int which) {
		if(which == 0)
			return;
		panel.height = 0.4F;
		panel.angle = (float) Math.toRadians(30);
		panel.a_off = 0.1F;
		panel.b_off = 0.2F;
		panel.c_off = 0.15F;
		panel.d_off = 0.05F;
	}

	@GameTest(template = EMPTY)
	public static void theSlabIsTheCollisionBoxForEveryFacingAndPlacement(GameTestHelper helper) {
		int checked = 0;
		for(int which = 0; which < 2; which++) {
			for(Direction facing : Direction.Plane.HORIZONTAL) {
				// wall, floor, ceiling: the block's own reading of UP and DOWN (up wins)
				boolean[][] placements = {{false, false}, {true, false}, {false, true}};
				for(boolean[] p : placements) {
					ControlPanelTE te = bareTE(p[0], p[1], facing);
					shape(te.panel, which);
					AABB slab = ControlPanelSlab.bounds(ControlPanelSlab.build(te.panel, p[0], p[1], facing));
					AABB shape = te.getBoundingBox(p[0], !p[0] && p[1], facing);
					helper.assertTrue(slab != null && near(slab, shape),
							"slab " + (slab == null ? "none" : show(slab)) + " is the collision box " + show(shape) + " for shape " + which
									+ ", " + facing + ", up=" + p[0] + ", down=" + p[1]);
					checked++;
				}
			}
		}
		helper.assertTrue(checked == 24, "all 24 combinations were checked");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theSlabIsAClosedSolidWoundOutward(GameTestHelper helper) {
		for(int which = 0; which < 2; which++) {
			for(Direction facing : Direction.Plane.HORIZONTAL) {
				for(boolean[] p : new boolean[][]{{false, false}, {true, false}, {false, true}}) {
					ControlPanelTE te = bareTE(p[0], p[1], facing);
					shape(te.panel, which);
					List<ControlPanelSlab.Quad> quads = ControlPanelSlab.build(te.panel, p[0], p[1], facing);
					String where = "shape " + which + ", " + facing + ", up=" + p[0] + ", down=" + p[1];
					helper.assertTrue(quads.size() == 6, "six faces, got " + quads.size() + " for " + where);

					// A closed polyhedron's outward area vectors sum to zero; one wound inward does not.
					double sx = 0, sy = 0, sz = 0;
					for(ControlPanelSlab.Quad q : quads) {
						float[] n = q.normal();
						helper.assertTrue(Math.abs(Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) - 1) < EPS, "unit normals, " + where);
						float[] pos = q.pos();
						double ax = pos[6] - pos[0], ay = pos[7] - pos[1], az = pos[8] - pos[2];
						double bx = pos[9] - pos[3], by = pos[10] - pos[4], bz = pos[11] - pos[5];
						double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
						// the normal the renderer lights by is the winding's own
						helper.assertTrue(cx * n[0] + cy * n[1] + cz * n[2] > 0, "the normal agrees with the winding, " + where);
						sx += 0.5 * cx;
						sy += 0.5 * cy;
						sz += 0.5 * cz;
					}
					helper.assertTrue(Math.abs(sx) < 1E-3 && Math.abs(sy) < 1E-3 && Math.abs(sz) < 1E-3,
							"the outward area vectors cancel (" + sx + ", " + sy + ", " + sz + "), " + where);
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void aFlatPanelHasNoSlabAndTheTextureCoordinatesStayInTheSkin(GameTestHelper helper) {
		ControlPanelTE te = bareTE(false, false, Direction.NORTH);
		te.panel.height = 0;
		helper.assertTrue(ControlPanelSlab.build(te.panel, false, false, Direction.NORTH).isEmpty(), "CE drew nothing at height 0");
		te.panel.height = 0.25F;
		for(ControlPanelSlab.Quad q : ControlPanelSlab.build(te.panel, false, false, Direction.NORTH))
			for(float uv : q.uv())
				helper.assertTrue(uv >= 0 && uv <= 1, "uv " + uv + " is inside the sprite");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the generated assets

	private static String resource(String path) throws IOException {
		try(InputStream in = LeafiaControlPanelDrawTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			if(in == null)
				throw new IOException("missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static boolean exists(String path) {
		try(InputStream in = LeafiaControlPanelDrawTests.class.getResourceAsStream("/assets/leafia/" + path)) {
			return in != null;
		} catch(IOException e) {
			return false;
		}
	}

	private static List<String> objectsOf(String obj) throws IOException {
		List<String> out = new ArrayList<>();
		for(String line : resource("models/blocks/" + obj).split("\n"))
			if(line.startsWith("o "))
				out.add(line.substring(2).trim());
		return out;
	}

	/**
	 * Every part the registration walks has a model JSON that shows exactly that object of an OBJ that exists, that bakes (has a
	 * material, D-816) and whose texture ships. The expectation is the table in {@link ControlPanelParts}, which the renderers draw
	 * from; the OBJ's own object list is read, not copied.
	 */
	@GameTest(template = EMPTY)
	public static void everyPartTheRenderersDrawHasAModelThatShowsOnlyIt(GameTestHelper helper) throws Exception {
		Set<String> textures = new HashSet<>();
		int parts = 0;
		for(Map.Entry<String, List<String>> e : ControlPanelParts.all().entrySet()) {
			String model = e.getKey();
			List<String> objects = objectsOf("ccp_" + model + ".obj");
			helper.assertTrue(!objects.isEmpty(), "ccp_" + model + ".obj holds objects");
			helper.assertTrue(new HashSet<>(objects).equals(new HashSet<>(e.getValue())),
					model + ": the table lists " + e.getValue() + " but the OBJ holds " + objects);
			helper.assertTrue(resource("models/blocks/ccp_" + model + ".obj").contains("usemtl"), model + " has a material, or it bakes to nothing");

			for(String part : e.getValue()) {
				String name = ControlPanelParts.modelName(model, part);
				JsonObject json = JsonParser.parseString(resource("models/block/" + name + ".json")).getAsJsonObject();
				helper.assertTrue(json.get("loader").getAsString().equals("neoforge:obj"), name + " is an OBJ model");
				helper.assertTrue(json.get("model").getAsString().equals("leafia:models/blocks/ccp_" + model + ".obj"), name + " points at its OBJ");
				helper.assertTrue(!json.get("automatic_culling").getAsBoolean(), name + " does not cull against neighbours");
				JsonObject hidden = json.getAsJsonObject("visibility");
				Set<String> hiddenNames = hidden == null ? Set.of() : hidden.keySet();
				for(String o : objects) {
					boolean shouldHide = !o.equals(part);
					helper.assertTrue(hiddenNames.contains(o) == shouldHide && (!shouldHide || !hidden.get(o).getAsBoolean()),
							name + ": " + o + (shouldHide ? " is hidden" : " is shown"));
				}
				textures.add(json.getAsJsonObject("textures").get("0").getAsString());
				parts++;
			}
		}
		for(String t : textures) {
			String path = "textures/" + t.substring("leafia:".length()) + ".png";
			helper.assertTrue(exists(path), "the texture " + path + " ships");
		}
		helper.assertTrue(parts >= 40, "forty-odd parts were checked, got " + parts);
		helper.succeed();
	}

	/** The registered list is the table plus the front's slab, and nothing else is registered that has no file. */
	@GameTest(template = EMPTY)
	public static void theModelManifestIsTheTableAndTheFront(GameTestHelper helper) throws Exception {
		Set<String> manifest = new HashSet<>(List.of(resource("control_panel_models.txt").trim().split("\n")));
		Set<String> table = new HashSet<>();
		for(Map.Entry<String, List<String>> e : ControlPanelParts.all().entrySet())
			for(String part : e.getValue())
				table.add(ControlPanelParts.modelName(e.getKey(), part));
		table.add(ControlPanelParts.PANEL_FRONT);
		helper.assertTrue(manifest.equals(table), "the generator wrote " + manifest.size() + " models and the table lists " + table.size());

		JsonObject front = JsonParser.parseString(resource("models/block/" + ControlPanelParts.PANEL_FRONT + ".json")).getAsJsonObject();
		helper.assertTrue(front.get("model").getAsString().equals("leafia:models/blocks/ccp_control_panel_front.obj") && !front.has("visibility"),
				"the front's slab is its whole OBJ");
		helper.assertTrue(resource("models/blocks/ccp_control_panel_front.obj").contains("usemtl"), "and has a material");
		for(String t : List.of("panel", "panel_front", "white"))
			helper.assertTrue(exists("textures/block/ccp/" + t + ".png"), "the " + t + " texture ships");
		helper.succeed();
	}

	/** The desk item's model sits in the middle of its block, where an item model is drawn, and wears the panel texture. */
	@GameTest(template = EMPTY)
	public static void theDeskItemIsAnObjModelCentredInItsBlock(GameTestHelper helper) throws Exception {
		JsonObject item = JsonParser.parseString(resource("models/item/control_panel_custom.json")).getAsJsonObject();
		helper.assertTrue(item.get("loader").getAsString().equals("neoforge:obj"), "the desk item is an OBJ model");
		String objPath = item.get("model").getAsString().substring("leafia:".length());
		String obj = resource(objPath);
		helper.assertTrue(obj.contains("usemtl"), "with a material");
		double[] lo = {1E9, 1E9, 1E9}, hi = {-1E9, -1E9, -1E9};
		Matcher m = Pattern.compile("(?m)^v ([-\\d.eE]+) ([-\\d.eE]+) ([-\\d.eE]+)").matcher(obj);
		int n = 0;
		while(m.find()) {
			n++;
			for(int k = 0; k < 3; k++) {
				double v = Double.parseDouble(m.group(k + 1));
				lo[k] = Math.min(lo[k], v);
				hi[k] = Math.max(hi[k], v);
			}
		}
		helper.assertTrue(n == 10, "CE's desk wedge has ten vertices, got " + n);
		for(int k = 0; k < 3; k++)
			helper.assertTrue(lo[k] >= -1E-4 && hi[k] <= 1 + 1E-4, "axis " + k + " is inside the block: " + lo[k] + " to " + hi[k]);
		helper.assertTrue(Math.abs((lo[0] + hi[0]) / 2 - 0.5) < 1E-3 && Math.abs((lo[2] + hi[2]) / 2 - 0.5) < 1E-3, "centred across the block");
		helper.assertTrue(item.getAsJsonObject("textures").get("0").getAsString().equals("leafia:block/ccp/panel"), "in the panel texture");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theBlockDrawsNothingItself(GameTestHelper helper) {
		BlockState state = LeafiaBlocks.control_panel_custom.get().defaultBlockState();
		helper.assertTrue(state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED, "the chunk mesh draws no cube; the block entity renderer draws the panel");
		helper.assertTrue(!state.canOcclude(), "and it does not occlude, or its renderer would be handed light 0 (D-816)");
		BlockState front = LeafiaBlocks.control_panel_front.get().defaultBlockState();
		helper.assertTrue(front.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED && !front.canOcclude(), "the front likewise");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the click

	private static Control add(ControlPanel panel, String key, float x, float y) {
		ControlPanelSetup.commonSetup();
		Control c = ControlRegistry.getNew(key, panel);
		c.posX = x;
		c.posY = y;
		List<ControlEvent> events = new ArrayList<>();
		for(String name : c.getOutEvents())
			events.add(ControlEvent.getRegisteredEvent(name));
		for(String name : c.getInEvents()) {
			ControlEvent evt = ControlEvent.getRegisteredEvent(name);
			if(!events.contains(evt))
				events.add(evt);
		}
		c.populateDefaultNodes(events);
		panel.controls.add(c);
		return c;
	}

	/** Where a ray must start to look straight down onto a point of the control's box: out along the panel's normal, in world space. */
	private static Vec3[] rayOnto(ControlPanelTE te, BlockPos at, Control c) {
		AABB box = c.getBoundingBox();
		float lx = (float) ((box.minX + box.maxX) / 2), ly = (float) (box.maxY - 0.01), lz = (float) ((box.minZ + box.maxZ) / 2);
		Vector4f target = new Vector4f(lx, ly, lz, 1).mul(te.panel.transform);
		Vector4f normal = new Vector4f(0, 1, 0, 0).mul(te.panel.transform);
		Vec3 n = new Vec3(normal.x, normal.y, normal.z).normalize();
		Vec3 hit = new Vec3((double) at.getX() + target.x, (double) at.getY() + target.y, (double) at.getZ() + target.z); // doubles: an int plus a float loses a block at 10 million
		return new Vec3[]{hit.add(n.scale(1.5)), n.scale(-1)};
	}

	@GameTest(template = EMPTY)
	public static void aRayDownOntoAControlPicksItInEveryPlacement(GameTestHelper helper) {
		BlockPos at = new BlockPos(100, 64, -30);
		int picked = 0;
		for(Direction facing : Direction.Plane.HORIZONTAL) {
			for(boolean[] p : new boolean[][]{{false, false}, {true, false}, {false, true}}) {
				ControlPanelTE te = bareTE(p[0], p[1], facing);
				Control button = add(te.panel, "button_push", 3, 2);
				Control other = add(te.panel, "button_push", -3, -2);
				String where = facing + ", up=" + p[0] + ", down=" + p[1];

				Vec3[] ray = rayOnto(te, at, button);
				helper.assertTrue(ControlPanelClick.pickIndex(te.panel, at, ray[0], ray[1]) == 0, "the ray onto control 0 picks 0, " + where);
				ray = rayOnto(te, at, other);
				helper.assertTrue(ControlPanelClick.pickIndex(te.panel, at, ray[0], ray[1]) == 1, "the ray onto control 1 picks 1, " + where);

				// The same ray turned to look the other way, away from the panel, hits nothing.
				ray = rayOnto(te, at, button);
				helper.assertTrue(ControlPanelClick.pickIndex(te.panel, at, ray[0], ray[1].scale(-1)) == -1, "looking away picks nothing, " + where);
				picked += 2;
			}
		}
		helper.assertTrue(picked == 24, "twenty-four rays were cast");
		helper.succeed();
	}

	@GameTest(template = EMPTY)
	public static void theNearestControlWinsAndAControlWithNoBoxIsNeverPicked(GameTestHelper helper) {
		BlockPos at = new BlockPos(5, 70, 5);
		ControlPanelTE te = bareTE(false, true, Direction.EAST);

		// Two controls on the same spot: the emergency button (1.13 tall) stands above the push button (0.4); from above the
		// taller one is hit first, whichever was added first.
		Control low = add(te.panel, "button_push", 0, 0);
		Control high = add(te.panel, "button_emergency_push", 0, 0);
		Vec3[] ray = rayOnto(te, at, high);
		helper.assertTrue(te.panel.controls.get(ControlPanelClick.pickIndex(te.panel, at, ray[0], ray[1])) == high, "the taller control is nearest from above");
		te.panel.controls.remove(high);
		te.panel.controls.add(0, high);
		helper.assertTrue(te.panel.controls.get(ControlPanelClick.pickIndex(te.panel, at, ray[0], ray[1])) == high, "and the order they were added in does not matter");

		// A display, a lamp, a dial and a label have no box and can never be clicked (CE).
		ControlPanelTE bare = bareTE(false, false, Direction.NORTH);
		for(String key : List.of("display_7seg", "display_text", "indicator_lamp", "indicator_lamp_rgb", "dial_square", "dial_large", "label")) {
			Control c = add(bare.panel, key, 0, 0);
			helper.assertTrue(c.getBoundingBox() == null, key + " has no bounding box");
		}
		Vec3 eye = new Vec3(at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5);
		helper.assertTrue(ControlPanelClick.pickIndex(bare.panel, at, eye, new Vec3(0, -1, 0)) == -1, "a ray down through the boxless controls picks nothing");
		helper.assertTrue(low != null, "the low control was built");
		helper.succeed();
	}

	/**
	 * The honest packet, end to end through the base mod's own handler as a client's click arrives, then the same packet mutated
	 * every way a hostile client could: another event, another index, out of reach. Only the first changes the panel.
	 */
	@GameTest(template = EMPTY)
	public static void aClickPacketPressesOnlyWhatItHonestlyNames(GameTestHelper helper) throws Exception {
		ControlPanelSetup.commonSetup();
		ControlPanelTE te = LeafiaControlPanelKit.place(helper, AT);
		// helper.setBlock turns the state by the test's rotation after the block entity was built from the unturned one, and
		// onLoad (which recomputes the transform) runs a tick later; a click happens long after both.
		te.updateTransform();
		Control button = add(te.panel, "button_push", 0, 0);
		Control second = add(te.panel, "button_push", 4, 4);
		BlockPos abs = helper.absolutePos(AT);

		ServerPlayer player = LeafiaControlPanelKit.mockPlayer(helper);
		LeafiaControlPanelKit.stand(player, te, 2);
		var context = LeafiaControlPanelKit.context(player);

		// An honest ray from where the player stands picks control 0; the tag is what the client sends for it.
		Vec3[] ray = rayOnto(te, abs, button);
		int index = ControlPanelClick.pickIndex(te.panel, abs, ray[0], ray[1]);
		helper.assertTrue(index == 0, "the honest ray picks control 0, got " + index);
		CompoundTag press = ControlPanelClick.press(index, false);
		helper.assertTrue(press.getInt(ControlPanelClick.KEY_INDEX) == 0 && press.getString("name").equals("ctrl_press"), "the tag is a ctrl_press on 0");

		// Hostile first, so a later honest press cannot hide an earlier acceptance.
		CompoundTag tick = ControlPanelClick.press(0, false);
		tick.putString("name", "tick");
		CompoundTag past = ControlPanelClick.press(2, false);
		CompoundTag negative = ControlPanelClick.press(-1, false);
		CompoundTag huge = ControlPanelClick.press(Integer.MAX_VALUE, false);
		for(CompoundTag bad : List.of(tick, past, negative, huge))
			NBTControlPacket.handle(new NBTControlPacket(abs, bad), context);
		helper.assertTrue(button.getVar("isPushed").getNumber() == 0 && second.getVar("isPushed").getNumber() == 0, "no hostile tag pressed anything");

		LeafiaControlPanelKit.stand(player, te, 40);
		NBTControlPacket.handle(new NBTControlPacket(abs, press), context);
		helper.assertTrue(button.getVar("isPushed").getNumber() == 0, "the honest tag from forty blocks away is refused");

		// A position nowhere near a panel is not a panel.
		NBTControlPacket.handle(new NBTControlPacket(abs.above(10), press), context);

		LeafiaControlPanelKit.stand(player, te, 2);
		NBTControlPacket.handle(new NBTControlPacket(abs, press), context);
		helper.assertTrue(button.getVar("isPushed").getNumber() == 1 && button.getVar("isLit").getNumber() == 1, "and the honest tag in reach presses the button");
		helper.assertTrue(second.getVar("isPushed").getNumber() == 0, "and only that button");
		helper.succeed();
	}
}
