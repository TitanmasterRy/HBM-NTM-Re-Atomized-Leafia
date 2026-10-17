package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.controls.DialLarge;
import com.leafia.contents.machines.controlpanel.core.controls.DialSquare;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two dials' draw halves (D-3065). NTM-CE: {@code DialSquare.render} and {@code DialLarge.render}.
 *
 * <p>Both read {@code (int) value} as a percentage and sweep the needle from 0: the square dial through 90 degrees about
 * {@code (0.77, 0, 0.77)} with a scale of eleven numbers (even ones printed, odd ones a dot) on a 68-unit arc, the large one
 * through 180 degrees about {@code (0, 0, 0.77)} with 0 to 100 in tens on a 1.1-pi arc. The label is wrapped to 50 and 70 units
 * under the scale. Text is the font laid flat on the panel at {@code 0.023} a pixel and 0.07 above it.
 */
@OnlyIn(Dist.CLIENT)
final class DialRenderers {

	private DialRenderers() {}

	static void register() {
		ControlRenderers.register(DialSquare.class, (c, d) -> {
			int value = (int) c.getVar("value").getNumber();
			d.part("dial_square", "base");

			d.pose.pushPose();
			d.pose.translate(0.77F, 0, 0.77F);
			d.pose.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(value * (90F / 100F), 0F, 90F)));
			d.part("dial_square", "dial");
			d.pose.popPose();

			d.pose.pushPose();
			d.pose.translate(0, 0.07F, 0);
			d.flatText(0.023F, false);
			for(int i = 0; i < 11; i++) {
				double angle = (Math.PI / 1.8) / 11F * i;
				float r = 68F;
				double x = r * Math.cos(angle - Math.PI);
				double y = r * Math.sin(angle - Math.PI);
				String txt = (i % 2 != 0) ? "·" : Integer.toString(i);
				d.text(txt, (float) (28 + x), (float) (29.5F + y), 0x303030, false);
			}
			d.wrapped(c.getLabel(), -8, -5, 50, 0x303030);
			d.pose.popPose();
		});

		ControlRenderers.register(DialLarge.class, (c, d) -> {
			int value = (int) c.getVar("value").getNumber();
			d.part("dial_large", "base");

			d.pose.pushPose();
			d.pose.translate(0, 0, 0.77F);
			d.pose.mulPose(Axis.YP.rotationDegrees(-Mth.clamp(value * (180F / 100F), 0F, 180F)));
			d.part("dial_large", "dial");
			d.pose.popPose();

			d.pose.pushPose();
			d.pose.translate(0, 0.07F, 0);
			d.flatText(0.023F, false);
			for(int i = 0; i < 11; i++) {
				double angle = (Math.PI * 1.1) / 11F * i;
				float r = 68F;
				double x = r * Math.cos(angle - Math.PI) + i;
				double y = r * Math.sin(angle - Math.PI);
				String labelText = Integer.toString(i * 10);
				float textOffset = (i == 10) ? 14F : 10F;
				d.text(labelText, (float) (x - textOffset), (float) (29.5F + y), 0x303030, false);
			}
			d.wrapped(c.getLabel(), -31, -10, 70, 0x303030);
			d.pose.popPose();
		});
	}
}
