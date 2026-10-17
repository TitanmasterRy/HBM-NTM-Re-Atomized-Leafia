package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.controls.ButtonEmergencyPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonEncasedPush;
import com.leafia.contents.machines.controlpanel.core.controls.ButtonPush;
import com.leafia.contents.machines.controlpanel.core.client.ControlDraw.Layer;
import com.mojang.math.Axis;

import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The three push buttons' draw halves (D-3065). NTM-CE: {@code ButtonPush.render}, {@code ButtonEmergencyPush.render} and
 * {@code ButtonEncasedPush.render}; every constant below is theirs.
 *
 * <ul>
 * <li><b>Push button</b>: the base and the button's well untinted; the cap in two pieces, tinted by its dye, at 0.6 of the
 * colour when dark and full colour and emissive when lit, and 0.1 lower while pushed.</li>
 * <li><b>Emergency button</b>: base, then the mushroom cap 0.125 lower while pushed.</li>
 * <li><b>Encased button</b>: base, a red cap (0.6 dark, full and emissive lit, 0.1 lower pushed), and a translucent cover hinged
 * at {@code (0, 0.625, -0.75)} that swings back 75 degrees when open.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class ButtonRenderers {

	private ButtonRenderers() {}

	static void register() {
		ControlRenderers.register(ButtonPush.class, (c, d) -> {
			boolean isPushed = c.getVar("isPushed").getBoolean();
			boolean isLit = c.getVar("isLit").getBoolean();
			float[] color = ControlRenderers.dyeRgb(c.getVar("color"));
			d.part("button_push", "base");
			d.part("button_push", "btn_base");
			float cMul = isLit ? 1F : 0.6F;
			d.pose.pushPose();
			d.pose.translate(0, isPushed ? -0.1F : 0F, 0);
			d.part("button_push", "btn_top", color, cMul, isLit);
			d.part("button_push", "btn_top_top", color, cMul, isLit);
			d.pose.popPose();
		});

		ControlRenderers.register(ButtonEmergencyPush.class, (c, d) -> {
			boolean isPushed = c.getVar("isPushed").getBoolean();
			d.part("button_emergency_push", "base");
			d.pose.pushPose();
			d.pose.translate(0, isPushed ? -0.125F : 0F, 0);
			d.part("button_emergency_push", "top");
			d.pose.popPose();
		});

		ControlRenderers.register(ButtonEncasedPush.class, (c, d) -> {
			boolean isPushed = c.getVar("isPushed").getBoolean();
			boolean isLit = c.getVar("isLit").getBoolean();
			boolean isCoverOpen = c.getVar("isCoverOpen").getBoolean();
			d.part("button_encased_push", "base");

			float[] red = ControlRenderers.dyeRgb(DyeColor.RED);
			float cMul = isLit ? 1F : 0.6F;
			d.pose.pushPose();
			d.pose.translate(0, isPushed ? -0.1F : 0F, 0);
			d.part("button_encased_push", "btn_top", red, cMul, isLit);
			d.part("button_encased_push", "btn_top_top", red, cMul, isLit);
			d.pose.popPose();

			// Drawn last and blended, as CE did: the cover is the one translucent part.
			d.pose.pushPose();
			d.pose.translate(0, 0.625F, -0.75F);
			if(isCoverOpen)
				d.pose.mulPose(Axis.XP.rotationDegrees(-75F));
			d.part(Layer.TRANSLUCENT, "button_encased_push", "cover", 1, 1, 1, false);
			d.part(Layer.TRANSLUCENT, "button_encased_push", "cover2", 1, 1, 1, false);
			d.pose.popPose();
		});
	}
}
