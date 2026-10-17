package com.leafia.contents.machines.elevators.car;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Every baked model and standalone texture the elevator renderers draw with.
 *
 * <p>Upstream this is two nested classes inside {@code ElevatorRender}, {@code S6} and
 * {@code Skylift}, each a wall of {@code ResourceLocation} constants over one loaded OBJ.
 * <b>Hoisted</b>, which D-1035 predicted would happen when the renderer landed and which is the
 * third time this campaign after {@code HitSrf} (D-1034) and {@code EvRope} (D-1040):
 * {@code EvFloorRender} imports {@code ElevatorRender.S6} and {@code .Skylift} and nothing else
 * from that file, so leaving them there makes the landing's renderer depend on the cabin's.
 *
 * <h2>Why these are model locations and not texture locations</h2>
 *
 * Upstream binds a texture and draws one named object out of the OBJ. This port has no
 * {@code renderPart}, so a (part, texture) pair <em>is</em> a model — see
 * a generator script, which writes all 101 of them. So where upstream
 * has one model and a dozen textures, this has a dozen models; the names below follow the
 * generator's rule exactly, and {@link #part} is that rule written once so the two cannot drift.
 *
 * <p>The handful of genuine {@code ResourceLocation}s that remain are for quads the renderer
 * builds by hand rather than baking — the buttons and their labels, drawn through
 * {@link com.leafia.dev.render.LeafiaBrush}, which needs a texture because there is no model.
 *
 * <h2>{@link #ALL} is what gets registered, and it has to be</h2>
 *
 * A baked model nothing references from a blockstate is never baked at all — it resolves to the
 * missing-model cube. These are all referenced from Java only, so every one has to be handed to
 * {@code ModelEvent.RegisterAdditional}. Collecting them here rather than listing them again in
 * {@code LeafiaClient} is the only way 101 entries stay in step with 101 files.
 */
@OnlyIn(Dist.CLIENT)
public final class ElevatorModels {

	private ElevatorModels() {}

	/** Every model this class names, for {@code ModelEvent.RegisterAdditional}. */
	public static final List<ModelResourceLocation> ALL = new ArrayList<>();

	/**
	 * Upstream's {@code ElevatorEntity.ALLOWED_DIGITS} plus the blank.
	 *
	 * <p>Upstream's {@code indicator(path)} maps <b>both</b> {@code ""} and {@code " "} to the
	 * off texture and each allowed digit to its own, and the renderer looks up a one-character
	 * substring — so a floor display of {@code " 3"} asks for {@code " "} and {@code "3"}. Kept,
	 * including the two keys for one texture, because the lookup falls back to {@code " "} and a
	 * missing {@code ""} would make that fallback unreachable.
	 */
	private static final String BLANK = "off";

	private static ModelResourceLocation model(String path) {
		ModelResourceLocation mrl = ModelResourceLocation.standalone(
				ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
		ALL.add(mrl);
		return mrl;
	}

	/** The generator's naming rule: {@code <model>_<part>} lowercased. */
	private static ModelResourceLocation part(String obj, String name) {
		return model("block/" + obj + "_" + name.toLowerCase());
	}

	/** The generator's naming rule for a part that takes more than one texture. */
	private static ModelResourceLocation part(String obj, String name, String suffix) {
		return model("block/" + obj + "_" + name.toLowerCase() + "_" + suffix.toLowerCase());
	}

	private static ResourceLocation texture(String path) {
		return ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/block/" + path + ".png");
	}

	/**
	 * The sixteen indicator models for one digit wheel, keyed as upstream keys them.
	 *
	 * <p>The generator renames {@code -} to {@code dash} and lowercases {@code L}/{@code R}/
	 * {@code B}/{@code E}, because 1.12.2 lowercased every resource path silently and 1.21.1
	 * rejects an uppercase one outright (D-1038). The <em>keys</em> stay upstream's, because they
	 * are what a floor display string is looked up by.
	 */
	private static Map<String, ModelResourceLocation> indicator(String obj, String wheel, String style) {
		Map<String, ModelResourceLocation> map = new LinkedHashMap<>();
		ModelResourceLocation off = part(obj, wheel, BLANK);
		map.put("", off);
		map.put(" ", off);
		for(String digit : ElevatorEntity.ALLOWED_DIGITS) {
			String file = switch(digit) {
				case "-" -> "dash";
				default -> digit.toLowerCase();
			};
			map.put(digit, part(obj, wheel, file));
		}
		return map;
	}

	/** One cabin style: which model each of its parts uses, and its button textures. */
	public static final class Style {

		public final ModelResourceLocation doorFrame, doorL, doorR, doorFloor, logo;
		public final ModelResourceLocation arrowUpOn, arrowUpOff, arrowDnOn, arrowDnOff;
		public final Map<String, ModelResourceLocation> digit0, digit10;
		public final ResourceLocation buttonOn, buttonOff, buttonLabel;

		/** Upstream's button quad, as half-extents in sixteenths from the button's own origin. */
		public final double buttonLow, buttonHigh;

		/** Upstream's label quad: left edge, right edge and top, in sixteenths. */
		public final double labelLeft, labelRight, labelTop;

		private Style(String obj, String style,
				double buttonLow, double buttonHigh,
				double labelLeft, double labelRight, double labelTop) {
			this.doorFrame = part(obj, "DoorFrame");
			this.doorL = part(obj, "DoorL");
			this.doorR = part(obj, "DoorR");
			this.doorFloor = part(obj, "DoorFloor");
			this.logo = part(obj, "Logo");
			this.arrowUpOn = part(obj, "ArrowUp", "on");
			this.arrowUpOff = part(obj, "ArrowUp", "off");
			this.arrowDnOn = part(obj, "ArrowDn", "on");
			this.arrowDnOff = part(obj, "ArrowDn", "off");
			this.digit0 = indicator(obj, "Digit0", style);
			this.digit10 = indicator(obj, "Digit10", style);
			this.buttonOn = texture("elevator_" + style + "_button_on");
			this.buttonOff = texture("elevator_" + style + "_button_off");
			this.buttonLabel = texture("elevator_" + style + "_button_label");
			this.buttonLow = buttonLow;
			this.buttonHigh = buttonHigh;
			this.labelLeft = labelLeft;
			this.labelRight = labelRight;
			this.labelTop = labelTop;
		}
	}

	/**
	 * The S6, which is the only style with a full cabin: floor, ceiling, walls, windows and door.
	 */
	public static final class S6 {
		private S6() {}

		public static final String OBJ = "elevator_s6";

		public static final ModelResourceLocation FRAMES = part(OBJ, "Frames");
		public static final ModelResourceLocation FLOOR = part(OBJ, "Floor");
		public static final ModelResourceLocation FLOOR_SIDE = part(OBJ, "FloorSide");
		public static final ModelResourceLocation CEILING = part(OBJ, "Ceiling");
		public static final ModelResourceLocation CEILING_SIDE = part(OBJ, "CeilingSide");
		public static final ModelResourceLocation WALL_FRAMES = part(OBJ, "WallFrames");
		/**
		 * The outside of a wall panel, in the <b>frame</b> texture.
		 *
		 * <p>Named after the texture rather than the style, because the same part takes three and
		 * two of them are used by different things: the cabin's wall style shows the frame here,
		 * and the wall <em>item</em> shows the wall texture on both faces. Naming these after
		 * styles hid that they differ (D-1043).
		 */
		public static final ModelResourceLocation WALL_OUT_FRAME = part(OBJ, "WallOut", "frame");
		public static final ModelResourceLocation WALL_IN_WALL = part(OBJ, "WallIn", "wall");
		public static final ModelResourceLocation WALL_OUT_WINDOW = part(OBJ, "WallOut", "window");
		public static final ModelResourceLocation WALL_IN_WINDOW = part(OBJ, "WallIn", "window");

		/**
		 * The fire button's unlit texture.
		 *
		 * <p><b>Both cabin styles draw the S6's</b>, and the lit one is never bound at all —
		 * upstream's `skyliftdoor` case says {@code bindTexture(S6.buttonFireOff)} verbatim, and
		 * nothing anywhere reads {@code buttonFireOn}. Kept, because a Skylift showing an S6 fire
		 * button is what a player sees today and changing it is a visual change (D-021). The lit
		 * texture is copied in so the pair is complete and the oddity is visible.
		 */
		public static final ResourceLocation BUTTON_FIRE_OFF = texture("elevator_s6_button_fire_off");
		public static final ResourceLocation BUTTON_FIRE_ON = texture("elevator_s6_button_fire_on");

		public static final Style STYLE =
				new Style(OBJ, "s6", 0.15, 0.85, -1, 0, 1);
	}

	/** The Skylift, which upstream only ever draws as a door. */
	public static final class Skylift {
		private Skylift() {}

		public static final String OBJ = "elevator_skylift";

		public static final ModelResourceLocation NAME = part(OBJ, "Name");
		public static final ModelResourceLocation SKYLIFT = part(OBJ, "Skylift");

		public static final Style STYLE =
				new Style(OBJ, "skylift", -0.25, 1.25, -1, -0.25, 1.25);
	}

	/** The landing, shared by both floor blocks and textured per block. */
	public static final class Landing {
		private Landing() {}

		public static final String OBJ = "elevator_landing";

		public static final ModelResourceLocation FRAMES_S6 = part(OBJ, "Frames", "s6");
		public static final ModelResourceLocation FRAMES_SKYLIFT = part(OBJ, "Frames", "skylift");
		public static final ModelResourceLocation DOOR_L_S6 = part(OBJ, "DoorL", "s6");
		public static final ModelResourceLocation DOOR_R_S6 = part(OBJ, "DoorR", "s6");
		public static final ModelResourceLocation DOOR_L_SKYLIFT = part(OBJ, "DoorL", "skylift");
		public static final ModelResourceLocation DOOR_R_SKYLIFT = part(OBJ, "DoorR", "skylift");
		public static final ModelResourceLocation WALL = part(OBJ, "Wall");
	}

	/** The three shared button labels: open, close and bell. */
	public static final ResourceLocation LABEL_OPEN = texture("elevator_label_open");
	public static final ResourceLocation LABEL_CLOSE = texture("elevator_label_close");
	public static final ResourceLocation LABEL_BELL = texture("elevator_label_bell");

	/**
	 * Forces every nested holder's static initialiser to run, so {@link #ALL} is complete.
	 *
	 * <p>Without this, a nested class nothing has touched yet contributes nothing to {@code ALL}
	 * and its models are never registered — and the symptom is a cabin drawn as missing-model
	 * cubes only for the style a player happened not to load first. Called from
	 * {@code LeafiaClient} before it registers.
	 */
	public static List<ModelResourceLocation> all() {
		// Reading one field from each is enough to class-initialise it, which is the point of
		// this method - an `assert` would not do, because assertions are off by default.
		if(S6.STYLE == null || Skylift.STYLE == null || Landing.WALL == null)
			throw new IllegalStateException("an elevator model holder failed to initialise");
		return List.copyOf(ALL);
	}
}
