package com.leafia.contents.machines.controlpanel.instruments.client;

import com.leafia.contents.machines.controlpanel.core.client.ControlDraw;
import com.leafia.contents.machines.controlpanel.core.client.ControlDraw.Layer;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.nixie.NixieDisplay;
import com.mojang.math.Axis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The nixie tube display's drawing (CCP-INSTRUMENTS-2, D-3072): a port of the body of Leafia's {@code NixieDisplay.render} and of
 * {@code MixinRrenderControlPanel}'s inset pass.
 *
 * <p><b>An inset.</b> The tubes sit in a recess below the panel's surface. Leafia drew the class before the slab and ended each tube
 * with {@code ClipPlane}, an invisible quad at the surface drawn with the alpha test off, which wrote depth and so kept the slab from
 * being drawn over the opening. Here the class is registered with {@code ControlRenderers.registerInset} and the drawing uses the
 * three ordered layers of {@link Layer} ({@code INSET}, {@code GLASS}, {@code MASK}) that reach the screen in the order they are
 * asked for, then {@link ControlDraw#flush}es the mask so it is never left behind the slab.
 *
 * <p>Per tube, in Leafia's order and colours: the frame (0.1 grey, the left end piece on the first tube and its mirror on the last),
 * the screws (0.5 grey, on the end tubes), the recess's walls (0.02 grey); then the character, 6.4 pixels to the unit, in the tube's
 * orange (0xFFA746) with eight copies a quarter pixel and half a pixel away in a dimmer one (0x724615) for the glow; then the glass
 * ({@code (1, 0.462, 0.076)} at alpha 0.2) over it; and last the mask. Leafia's additive blend made the glow copies add up; here they
 * are drawn first and a little deeper and the bright character on top, which is the same picture where they overlap.
 */
@OnlyIn(Dist.CLIENT)
final class NixieDraw {

	private NixieDraw() {}

	private static final String N = AddonInstrumentModels.NIXIE;

	private static final int MAIN = 0xFFA746;
	private static final int DIM = 0x724615;

	private static final float[][] COPIES = {
			{-0.25F, 0}, {0.25F, 0}, {0, -0.25F}, {0, 0.25F}, {-0.5F, 0}, {0.5F, 0}, {0, -0.5F}, {0, 0.5F}};

	static void draw(NixieDisplay n, ControlDraw d) {
		int length = n.getLength();
		String[] tubes = n.getTubeTexts();

		for(int i = 0; i < length; i++) {
			d.pose.pushPose();
			d.pose.translate(i, 0, 0);
			inset(d, "Border", 0.1F);
			if(i == 0)
				inset(d, "BorderLeft", 0.1F);
			if(i == length - 1)
				mirrored(d, "BorderLeft", 0.1F);
			if(i == 0)
				inset(d, "Screws", 0.5F);
			if(i == length - 1)
				mirrored(d, "Screws", 0.5F);
			inset(d, "Inside", 0.02F);
			if(i == 0)
				inset(d, "InsideLeft", 0.02F);
			if(i == length - 1)
				mirrored(d, "InsideLeft", 0.02F);
			d.pose.popPose();
		}

		for(int i = 0; i < length; i++) {
			String s = tubes[i];
			if(s.isEmpty())
				continue;
			d.pose.pushPose();
			d.pose.translate(i, 0, 0);
			d.pose.mulPose(Axis.XP.rotationDegrees(90));
			d.pose.translate(0, 0, 0.562F); // into the recess
			d.pose.translate(0, -0.1F, 0); // Leafia: "unicode font offset"
			d.pose.scale(1 / 6.4F, 1 / 6.4F, 1 / 6.4F);
			d.pose.translate(-d.font.width(s) / 2F + 0.5F, -7 / 2F, 0);
			for(float[] c : COPIES) {
				d.pose.pushPose();
				d.pose.translate(c[0], c[1], 0.05F);
				d.text(s, 0, 0, DIM, true);
				d.pose.popPose();
			}
			d.text(s, 0, 0, MAIN, true);
			d.pose.popPose();
		}

		for(int i = 0; i < length; i++) {
			d.pose.pushPose();
			d.pose.translate(i, 0, 0);
			d.part(Layer.GLASS, N, "Tube", 1F, 0.462F, 0.076F, 0.2F, false);
			d.pose.popPose();
		}

		for(int i = 0; i < length; i++) {
			d.pose.pushPose();
			d.pose.translate(i, 0, 0);
			d.part(Layer.MASK, N, "ClipPlane", 1F, 1F, 1F, 1F, false);
			d.pose.popPose();
		}
		d.flush(Layer.MASK);
	}

	private static void inset(ControlDraw d, String part, float grey) {
		d.part(Layer.INSET, N, part, grey, grey, grey, 1F, false);
	}

	/** The end piece turned half a turn about the tube's own axis, as Leafia drew the right end. */
	private static void mirrored(ControlDraw d, String part, float grey) {
		d.pose.pushPose();
		d.pose.mulPose(Axis.YP.rotationDegrees(180));
		inset(d, part, grey);
		d.pose.popPose();
	}
}
