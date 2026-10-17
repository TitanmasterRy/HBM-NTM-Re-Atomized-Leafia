package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.client.ControlDraw.Layer;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLamp;
import com.leafia.contents.machines.controlpanel.core.controls.IndicatorLampRGB;
import com.leafia.contents.machines.controlpanel.core.controls.Label;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two lamps' and the label's draw halves (D-3065). NTM-CE: {@code IndicatorLamp.render}, {@code IndicatorLampRGB.render}
 * and {@code Label.render}.
 *
 * <p><b>Lamp.</b> A dark grey ({@code 0.3}) untextured base and a lens wearing the push button's texture, tinted by its dye at
 * 1.5 and emissive when lit and at 0.5 when not (above 1 clamps; CE's byte colours did too).
 *
 * <p><b>RGB lamp.</b> The same base and a lens at {@code 0.25} grey, with the colour of the {@code red}, {@code green} and
 * {@code blue} variables (0..255) laid over it additively, emissive, and 1% larger so it does not z-fight the lens.
 *
 * <p><b>Label.</b> Text only, in the configured colour, emissive when {@code isLit}. As for {@link DisplayRenderers} the client
 * reports the measured string back with {@link Label#setMeasured}.
 */
@OnlyIn(Dist.CLIENT)
final class LampLabelRenderers {

	private LampLabelRenderers() {}

	static void register() {
		ControlRenderers.register(IndicatorLamp.class, (c, d) -> {
			boolean isLit = c.getVar("isLit").getBoolean();
			float[] color = ControlRenderers.dyeRgb(c.getVar("color"));
			d.part("indicator_lamp", "base", 0.3F, 0.3F, 0.3F);
			d.part("indicator_lamp", "lamp", color, isLit ? 1.5F : 0.5F, isLit);
		});

		ControlRenderers.register(IndicatorLampRGB.class, (c, d) -> {
			float red = c.getVar("red").getNumber() / 255F;
			float green = c.getVar("green").getNumber() / 255F;
			float blue = c.getVar("blue").getNumber() / 255F;
			d.part("indicator_lamp", "base", 0.3F, 0.3F, 0.3F);
			d.part("indicator_lamp", "lamp", 0.25F, 0.25F, 0.25F);

			d.pose.pushPose();
			d.pose.scale(1.01F, 1.01F, 1.01F);
			d.part(Layer.GLOW, "indicator_lamp", "lamp", red, green, blue, true);
			d.pose.popPose();
		});

		ControlRenderers.register(Label.class, (c, d) -> {
			String text = c.getText();
			boolean isLit = c.getVar("isLit").getBoolean();
			float[] color = c.getColor();
			int rgb = ((int) (color[0] * 255) << 16) | ((int) (color[1] * 255) << 8) | (int) (color[2] * 255);

			c.setMeasured(d.font.width(text), d.font.lineHeight);
			float s = c.getScale() / 500F;

			d.pose.pushPose();
			d.pose.translate(0, .03F, 0);
			d.flatText(s, true);
			d.text(text, 0, 0, rgb, isLit);
			d.pose.popPose();
		});
	}
}
