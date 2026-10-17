package com.leafia.passive.rendering;


import com.llib.group.LeafiaSet;
import com.llib.math.MathLeafia;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Things drawn over the top of the world (D-3026): labelled wireframe boxes that other code asks for, and the lock-on reticle.
 *
 * <p>Upstream: {@code com/leafia/passive/rendering/TopRender.java}, called at the end of {@code LeafiaClientListener}'s
 * {@code RenderWorldLastEvent}. Its two users are {@link Highlight}, a box (or a bare ray) with a floating label that lasts a number
 * of seconds or until hidden, and {@link #renderLockon}, the square brackets that close on a locked target. Both ignore the depth buffer
 * and add their colour to the scene ({@code SRC_ALPHA, ONE}) so they read through walls.
 *
 * <p>1.21.1 has no immediate mode, so the ported pieces are:
 * <ul>
 *   <li>the {@code GL_LINES} brush is a {@code LINES} render type with the depth test off and the additive blend; lines need a normal
 *       in 1.21.1's line shader, which here is the line's own direction;</li>
 *   <li>{@code ActiveRenderInfo.getRotationX/Z/YZ/XY/XZ}, the five numbers of the camera's billboard basis, are the camera's left and up
 *       vectors: the four brackets sit at {@code +-left +-up}, scaled;</li>
 *   <li>the label is the one thing drawn differently: upstream billboards the text in the world and 1.21.1's text pipeline drew
 *       nothing at any level stage in the headless client, so the label's anchor is projected to the screen and the text drawn
 *       through {@code GuiGraphics} at the size a world-space pixel of {@code 1/32 textSize} blocks has there - the same picture
 *       (flat to the camera, shrinking with distance, over everything), by the path every other text in the game takes.</li>
 * </ul>
 * It draws at {@code AFTER_PARTICLES}, the last stage the addon already draws in. The geometry is separated from the drawing
 * ({@link Highlight#edges}, {@link #lockonColor}, {@link #lockonGap}, {@link #lockonBrackets}) so a GameTest can pin it, and this class
 * is free of client types so that it loads on a server; the drawing is {@link TopRenderClient}.
 *
 * <p><b>Lock-on state.</b> Upstream keeps it in {@code ILockonWeapon.LockonHandler} (the SEDNA batch's); {@link #lockonTarget} and
 * {@link #lockonTimer} are the two fields this reads, public so that handler can write them when it lands.
 */
public final class TopRender {

	private TopRender() {}

	/** {@code LockonHandler.target}: the entity the reticle closes on, or null. */
	public static Entity lockonTarget = null;
	/** {@code LockonHandler.timer}: ticks since the lock began; the reticle's colour and gap step at 16 and 32 (half of it, in whole numbers, is 8 and 16). */
	public static int lockonTimer = 0;

	public static Vec3 lerp(Vec3 a, Vec3 b, double factor) {
		return a.add(b.subtract(a).scale(factor));
	}

	// ------------------------------------------------------------------ the lock-on reticle

	/** {@code col}: white, then orange from {@code timer/2 >= 8}, then red from 16. */
	public static int lockonColor(int timer) {
		if(timer / 2 >= 16) return 0xFF0000;
		if(timer / 2 >= 8) return 0xFFAA00;
		return 0xFFFFFF;
	}

	/** {@code gap}, before the distance scaling: 0.3, 0.15 from 8, 0 from 16. */
	public static float lockonGap(int timer) {
		if(timer / 2 >= 16) return 0;
		if(timer / 2 >= 8) return 0.15F;
		return 0.3F;
	}

	/**
	 * The reticle's eight segments, as sixteen points (pairs of ends) relative to the target: four corners, each throwing two arms along
	 * the edges toward its neighbours, stopping a {@code gap} short of the middle of the edge.
	 *
	 * @param left the camera's left vector
	 * @param up the camera's up vector
	 * @param distance from the camera to the target; both the arm length (0.4) and the gap scale by one tenth of it
	 */
	public static Vec3[] lockonBrackets(Vec3 left, Vec3 up, double distance, int timer) {
		double sc2 = distance / 10;
		double scale = 0.4F * (float) sc2;
		double gap = lockonGap(timer) * (float) sc2;
		double s = scale + gap;

		Vec3 rd = left.scale(-s).add(up.scale(-s));
		Vec3 ru = left.scale(-s).add(up.scale(s));
		Vec3 lu = left.scale(s).add(up.scale(s));
		Vec3 ld = left.scale(s).add(up.scale(-s));

		double fac = 0.5 - (gap / (gap + scale)) / 2;
		return new Vec3[] {
				rd, lerp(rd, ld, fac), rd, lerp(rd, ru, fac),
				ru, lerp(ru, lu, fac), ru, lerp(ru, rd, fac),
				lu, lerp(lu, ru, fac), lu, lerp(lu, ld, fac),
				ld, lerp(ld, rd, fac), ld, lerp(ld, lu, fac)
		};
	}

	// ------------------------------------------------------------------ highlights

	/**
	 * A box or ray drawn through everything, with a label above it.
	 *
	 * <p>{@code ray} is a line from the centre: with a ray longer than the box's own diagonal, every corner draws a line to the ray's
	 * end (a cone); shorter, one line from the middle. {@code colorTop} colours the top four corners and the ray, {@code color} the
	 * rest, and the lines blend between them. The label's lines are centred on the point 41.4% of the way along the ray.
	 */
	public static class Highlight {
		public static final LeafiaSet<Highlight> instances = new LeafiaSet<>();
		static int t0 = 0;

		/** Counts every live highlight's lifetime down by the time since the last frame; one with a negative lifetime never expires. */
		static void cleanup() {
			int t1 = MathLeafia.getTime32s();
			int dT = MathLeafia.getTimeDifference32s(t0, t1);
			t0 = t1;
			cleanup(dT);
		}

		/** {@link #cleanup()} with the time given, in milliseconds. */
		public static void cleanup(int dT) {
			for(Highlight instance : instances) {
				if(instance.lifetime >= 0) {
					instance.lifetime -= dT / 1000f;
					if(instance.lifetime <= 0) instances.remove(instance);
				}
			}
		}

		public Vec3 center;
		public Vec3 size = new Vec3(1, 1, 1);
		public Vec3 ray = new Vec3(0, 0, 0);
		/** Seconds left; negative is forever. */
		public float lifetime = -1;
		public int color = 0x00_FFFFFF;
		public int colorTop = 0x00_FFFFFF;
		public Object[] label = new Object[0];
		public double textSize = 1;

		public Highlight() { center = new Vec3(0, 0, 0); }
		public Highlight(BlockPos pos) { center = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5); }
		public Highlight(Vec3 vec) { center = vec; }

		public Highlight setBlock(BlockPos pos) {
			center = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5);
			size = new Vec3(1, 1, 1);
			return this;
		}

		public Highlight setArea(Vec3 pointA, Vec3 pointB) {
			Vec3 min = new Vec3(Math.min(pointA.x, pointB.x), Math.min(pointA.y, pointB.y), Math.min(pointA.z, pointB.z));
			Vec3 max = new Vec3(Math.max(pointA.x, pointB.x), Math.max(pointA.y, pointB.y), Math.max(pointA.z, pointB.z));
			size = max.subtract(min);
			center = min.add(size.scale(0.5));
			return this;
		}

		/** Both corners inclusive, as blocks: the box covers whole blocks. */
		public Highlight setArea(BlockPos posA, BlockPos posB) {
			setArea(Vec3.atLowerCornerOf(posA), Vec3.atLowerCornerOf(posB));
			size = size.add(1, 1, 1);
			center = center.add(0.5, 0.5, 0.5);
			return this;
		}

		public Highlight setLabel(Object... newLabel) { label = newLabel; return this; }
		public Highlight setColor(int color) { this.color = color; this.colorTop = color; return this; }
		public Highlight setLifetime(float lifetime) { this.lifetime = lifetime; return this; }
		public Highlight show() { instances.add(this); return this; }
		public Highlight hide() { instances.remove(this); return this; }
		public Highlight setVisibility(boolean visibility) { if(visibility) show(); else hide(); return this; }
		public boolean getVisibility() { return instances.contains(this); }

		/** One line of the drawing: two ends, relative to the viewer, and the colour at each. */
		public record Edge(Vec3 from, int fromColor, Vec3 to, int toColor) {}

		/**
		 * Every line this highlight draws, relative to {@code viewPos}: the twelve edges of the box as three passes of four (along z,
		 * along x, along y), each end in the top colour above the centre and the base colour below; then the ray, or the cone of lines
		 * from the corners to the ray's end if it is longer than the box.
		 */
		public java.util.List<Edge> edges(Vec3 viewPos) {
			java.util.List<Edge> out = new java.util.ArrayList<>();
			drawBox(out, viewPos, new Vec3(0, 0, -1), new Vec3(0, 1, 0));
			drawBox(out, viewPos, new Vec3(1, 0, 0), new Vec3(0, 1, 0));
			drawBox(out, viewPos, new Vec3(0, -1, 0), new Vec3(0, 0, -1));
			if(ray.length() <= size.length()) {
				Vec3 c = center.subtract(viewPos);
				out.add(new Edge(c, colorTop, c.add(ray), colorTop));
			}
			return out;
		}

		private static Vec3 corner(Vec3 right, Vec3 up, Vec3 look, int dx, int dy, int dz) {
			return right.scale(dx).add(up.scale(dy)).add(look.scale(dz));
		}

		/**
		 * One pass of upstream's {@code draw}: the four edges along {@code look}, and, for a ray longer than the box's diagonal, a line
		 * from each of the two corners at their ends to the ray's end. <b>All three passes add the cone</b>, each corner three times,
		 * and the lines are additive, so a cone is three times as bright as a single ray; it is upstream's.
		 */
		private void drawBox(java.util.List<Edge> out, Vec3 viewPos, Vec3 look, Vec3 up) { // front as *negative Z*
			Vec3 right = look.cross(up);
			Vec3 tip = center.subtract(viewPos).add(ray);
			for(int bits = 0b00; bits <= 0b11; bits++) {
				int dx = (bits >> 1) * 2 - 1;
				int dy = (bits & 1) * 2 - 1;
				Vec3[] ends = new Vec3[2];
				int[] cols = new int[2];
				for(int dz = -1, i = 0; dz <= 1; dz += 2, i++) {
					Vec3 v = corner(right, up, look, dx, dy, dz);
					cols[i] = (v.y > 0) ? colorTop : color;
					ends[i] = center.add(v.x * size.x / 2, v.y * size.y / 2, v.z * size.z / 2).subtract(viewPos);
				}
				out.add(new Edge(ends[0], cols[0], ends[1], cols[1]));
				if(ray.length() > size.length()) {
					out.add(new Edge(ends[0], colorTop, tip, colorTop));
					out.add(new Edge(ends[1], colorTop, tip, colorTop));
				}
			}
		}
	}
}
