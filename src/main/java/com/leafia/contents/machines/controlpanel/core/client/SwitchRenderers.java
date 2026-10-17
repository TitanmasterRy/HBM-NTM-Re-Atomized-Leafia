package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.controls.SwitchRotaryToggle;
import com.leafia.contents.machines.controlpanel.core.controls.SwitchToggle;
import com.mojang.math.Axis;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two switches' draw halves (D-3065). NTM-CE: {@code SwitchToggle.render} and {@code SwitchRotaryToggle.render}.
 *
 * <p>The toggle has two lamps, a green one that CE tinted {@code (0.031, 0.17, 0.024)} and a red one
 * {@code (0.25, 0.04, 0.04)}, each at 3.0 of its colour and emissive when its side is the active one and 0.4 and dark when it
 * is not; the lever tips 60 degrees about X when on. The rotary switch's lever turns 90 degrees about Y when on, and reads the
 * same {@code isOn} variable.
 */
@OnlyIn(Dist.CLIENT)
final class SwitchRenderers {

	private SwitchRenderers() {}

	static void register() {
		ControlRenderers.register(SwitchToggle.class, (c, d) -> {
			boolean isOn = c.getVar("isOn").getBoolean();
			d.part("switch_toggle", "base");

			float onCMul = isOn ? 3F : .4F;
			float offCMul = isOn ? .4F : 3F;
			d.part("switch_toggle", "lamp_on", 0.031F * onCMul, 0.17F * onCMul, 0.024F * onCMul, isOn);
			d.part("switch_toggle", "lamp_off", 0.25F * offCMul, 0.04F * offCMul, 0.04F * offCMul, !isOn);

			d.pose.pushPose();
			if(isOn)
				d.pose.mulPose(Axis.XP.rotationDegrees(-60F));
			d.part("switch_toggle", "lever");
			d.pose.popPose();
		});

		ControlRenderers.register(SwitchRotaryToggle.class, (c, d) -> {
			boolean isFlipped = c.getVar("isOn").getBoolean();
			d.part("switch_rotary_toggle", "base");
			d.pose.pushPose();
			if(isFlipped)
				d.pose.mulPose(Axis.YP.rotationDegrees(-90F));
			d.part("switch_rotary_toggle", "lever");
			d.pose.popPose();
		});
	}
}
