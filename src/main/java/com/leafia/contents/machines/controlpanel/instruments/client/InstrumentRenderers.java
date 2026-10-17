package com.leafia.contents.machines.controlpanel.instruments.client;

import com.leafia.contents.machines.controlpanel.core.client.ControlDraw;
import com.leafia.contents.machines.controlpanel.core.client.ControlDraw.Layer;
import com.leafia.contents.machines.controlpanel.core.client.ControlRenderers;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.drawing.Drawing;
import com.leafia.contents.machines.controlpanel.instruments.types.graph.Graph;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverBase;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.lever.LeverVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.horizontal.MeterHorizontal;
import com.leafia.contents.machines.controlpanel.instruments.types.meters.vertical.MeterVertical;
import com.leafia.contents.machines.controlpanel.instruments.types.nixie.NixieDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.rbmk.RBMKDisplay;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeButton;
import com.leafia.contents.machines.controlpanel.instruments.types.starbound.LargeSwitch;
import com.leafia.contents.machines.controlpanel.instruments.types.textindicator.TextIndicator;
import com.mojang.math.Axis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The draw halves of Leafia's instruments (CCP-INSTRUMENTS-1, D-3071), registered with {@link ControlRenderers} (which calls
 * {@link #register} when it loads). Leafia's {@code render()} and {@code renderControl()} sat on the control classes, which put GL
 * in classes a dedicated server loads (section 2.2); here they are renderers keyed by class, as the stock fourteen's are.
 *
 * <ul>
 * <li><b>The large switch and button</b> are their baked parts drawn at three times the model's size, the palette texture
 * on every part. The switch's handle tips about {@code (0, 0.125, 0)} by {@code -65 * (1 - curLevel / 50)} degrees; the button's
 * lens sinks 0.125 when pushed, is dark (0.5 grey) when not lit and emissive when lit.</li>
 * <li><b>The levers</b> are three parts in three greys (0.306, 0.765 and 0.533) on the white texture, at half size, the horizontal
 * one turned a quarter about the panel's normal; the handle tips about {@code (0, 0.1, 0)} by {@code -50 * (curLevel / 50 - 1)}.</li>
 * <li><b>The boxed label</b> is a flat quad of its colour with its text centred on it.</li>
 * <li><b>The graph</b> is {@link GraphDraw}.</li>
 * <li><b>The text indicator</b> (CCP-INSTRUMENTS-2, D-3072) is its baked body at two thirds of the model's size in 0.29 grey, the lens
 * on its own texture in a dark tint of the colour, an additive copy of the lens whose alpha is {@link TextIndicator#glow}, and the line
 * of text 0.25 above the body in 0x4A4A4A.</li>
 * <li><b>The two meters</b> are {@link MeterDraw}, <b>the nixie tube</b> {@link NixieDraw} (and an inset: drawn before the slab) and
 * <b>the RBMK display</b> {@link RBMKDisplayDraw}.</li>
 * </ul>
 *
 * <p>The editor draws the same renderers in its preview ({@code ControlPreview.real}), so Leafia's separate 2D previews
 * ({@code Graph.drawPreview}, {@code Drawing.renderControl}) have no counterpart: the pane shows what the world shows.
 */
@OnlyIn(Dist.CLIENT)
public final class InstrumentRenderers {

	private InstrumentRenderers() {}

	private static final String SWITCH = AddonInstrumentModels.LARGE_SWITCH;
	private static final String BUTTON = AddonInstrumentModels.LARGE_BUTTON;
	private static final String LEVER = AddonInstrumentModels.LEVER;

	public static void register() {
		ControlRenderers.register(LargeSwitch.class, (c, d) -> {
			d.pose.pushPose();
			d.pose.scale(3, 3, 3); // Leafia: "that was tiny as shit"
			for(String part : new String[]{"Plane", "Plane.001", "Plane.002", "Cube"})
				d.part(SWITCH, part);
			d.pose.translate(0, 0.125F, 0);
			d.pose.mulPose(Axis.XP.rotationDegrees(-65 * (1 - c.getCurLevel() / 50)));
			d.pose.translate(0, -0.125F, 0);
			d.part(SWITCH, "Switch");
			d.pose.popPose();
		});

		ControlRenderers.register(LargeButton.class, (c, d) -> {
			boolean lit = c.getVar("isLit").getBoolean();
			boolean pushed = c.getVar("isPushed").getBoolean();
			d.pose.pushPose();
			d.pose.scale(3, 3, 3);
			for(String part : new String[]{"Plane", "Plane.001", "Plane.002", "Circle"})
				d.part(BUTTON, part);
			if(pushed)
				d.pose.translate(0, -0.125F, 0);
			float tint = lit ? 1F : 0.5F;
			d.part(BUTTON, "Circle.001", tint, tint, tint, lit);
			d.pose.popPose();
		});

		ControlRenderers.register(LeverHorizontal.class, (c, d) -> {
			d.pose.pushPose();
			d.pose.mulPose(Axis.YP.rotationDegrees(-90));
			lever(c, d);
			d.pose.popPose();
		});
		ControlRenderers.register(LeverVertical.class, InstrumentRenderers::lever);

		ControlRenderers.register(Drawing.class, (c, d) -> {
			float w = c.getWidth(), h = c.getHeight();
			float[] color = c.getColor();
			d.pose.pushPose();
			d.pose.translate(-0.5F, 0, -0.5F); // Leafia: translate(posX - 1/2, 0, posY - 1/2)
			d.flatQuad(0, 0, w, h, 0.02F, color[0], color[1], color[2]);
			String text = c.getText();
			if(!text.isBlank()) {
				d.pose.translate(w / 2, 0.04F, h / 2);
				d.flatText(c.getScale() / 500F, false);
				d.text(text, -d.font.width(text) / 2F, -3, c.isWhiteText() ? 0xFFFFFF : 0, false);
			}
			d.pose.popPose();
		});

		ControlRenderers.register(Graph.class, GraphDraw::draw);

		ControlRenderers.register(MeterHorizontal.class, MeterDraw::horizontal);
		ControlRenderers.register(MeterVertical.class, MeterDraw::vertical);
		ControlRenderers.register(NixieDisplay.class, NixieDraw::draw);
		ControlRenderers.registerInset(NixieDisplay.class); // D-3065: Leafia's MixinRrenderControlPanel drew the tube before the slab
		ControlRenderers.register(RBMKDisplay.class, RBMKDisplayDraw::draw);
		ControlRenderers.register(TextIndicator.class, InstrumentRenderers::textIndicator);
	}

	private static void textIndicator(TextIndicator c, ControlDraw d) {
		String ti = AddonInstrumentModels.TEXT_INDICATOR;
		float red = c.getRed(), green = c.getGreen(), blue = c.getBlue();
		d.pose.pushPose();
		d.pose.scale(1 / 1.5F, 1 / 1.5F, 1 / 1.5F); // Leafia: "now it's too big"
		d.part(ti, "Plane", 0.29F, 0.29F, 0.29F);
		float offset = 20;
		d.part(ti, "Plane.001", red / (255 + offset) * 0.25F + offset / 255, green / (255 + offset) * 0.25F + offset / 255,
				blue / (255 + offset) * 0.25F + offset / 255);
		offset = 80;
		d.pose.translate(0, 0.01F, 0);
		d.part(Layer.GLOW, ti, "Plane.001", red / (255 + offset) + offset / 255, green / (255 + offset) + offset / 255,
				blue / (255 + offset) + offset / 255, c.glow(), true);
		d.pose.translate(0, 0.376F, 0);
		d.flatText(0.1F, false);
		String text = c.getText();
		d.text(text, -(d.font.width(text) / 2), -7 / 2, 0x4A4A4A, false);
		d.pose.popPose();
	}

	private static void lever(LeverBase c, ControlDraw d) {
		d.pose.pushPose();
		d.pose.scale(0.5F, 0.5F, 0.5F);
		d.part(LEVER, "Base", 0.306F, 0.306F, 0.306F);
		d.part(LEVER, "Base.001", 0.765F, 0.765F, 0.765F);
		d.pose.translate(0, 0.1F, 0);
		d.pose.mulPose(Axis.XP.rotationDegrees(-50 * (c.getCurLevel() / 50 - 1F)));
		d.pose.translate(0, -0.1F, 0);
		d.part(LEVER, "Lever", 0.533F, 0.533F, 0.533F);
		d.pose.popPose();
	}
}
