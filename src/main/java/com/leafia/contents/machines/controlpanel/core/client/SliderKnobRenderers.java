package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.controls.KnobControl;
import com.leafia.contents.machines.controlpanel.core.controls.SliderVertical;
import com.mojang.math.Axis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The slider's and the knob's draw halves (D-3065). NTM-CE: {@code SliderVertical.render} and {@code KnobControl.render}.
 *
 * <p>The slider reads {@code |value| mod 6} as its position, 0..5: the cap (dark grey, {@code 51/255}) sits
 * {@code 0.3125 x position} back from the base, and lights {@code 0..position} are lit green and emissive. The knob turns
 * {@code value x 360/11} degrees about Y and is labelled 0..10 around its rim; <b>eleven positions are drawn whatever the
 * {@code positions} setting says</b>, as CE drew them (see D-3064).
 */
@OnlyIn(Dist.CLIENT)
final class SliderKnobRenderers {

	private SliderKnobRenderers() {}

	static void register() {
		ControlRenderers.register(SliderVertical.class, (c, d) -> {
			int position = (int) Math.abs(c.getVar("value").getNumber()) % 6;
			d.part("slider_vertical", "base");

			d.pose.pushPose();
			d.pose.translate(0, 0, -(0.3125F * position));
			d.part("slider_vertical", "slider", 51 / 255F, 51 / 255F, 51 / 255F);
			d.pose.popPose();

			for(int i = 0; i <= position; i++)
				d.part("slider_vertical", "light" + i, 0F, 1F, 0F, true);
		});

		ControlRenderers.register(KnobControl.class, (c, d) -> {
			int value = (int) c.getVar("value").getNumber();
			int positions = 11;
			d.part("knob_control", "base");

			d.pose.pushPose();
			d.pose.translate(0, -0.04F, 0);
			d.pose.mulPose(Axis.YP.rotationDegrees((float) -(value * (360F / 11F))));
			d.part("knob_control", "knob");
			d.pose.popPose();

			d.pose.pushPose();
			d.pose.translate(0, 0.07F, 0);
			d.flatText(0.028F, false);
			for(int i = 0; i < positions; i++) {
				double angle = (Math.PI * 2) / positions * i;
				float r = 28.0F;
				double x = r * Math.cos(angle - Math.PI / 2);
				double y = r * Math.sin(angle - Math.PI / 2);
				float xOffset = (i == 10 ? -6.5F : -2.5F);
				d.text(Integer.toString(i), (float) (xOffset + x), (float) (-3.0F + y), 0x282828, false);
			}
			d.pose.popPose();
		});
	}
}
