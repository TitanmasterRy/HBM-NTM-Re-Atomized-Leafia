package com.leafia.contents.machines.controlpanel.core.client;

import com.leafia.contents.machines.controlpanel.core.controls.DisplaySevenSeg;
import com.leafia.contents.machines.controlpanel.core.controls.DisplayText;

import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two displays' draw halves (D-3065). NTM-CE: {@code DisplaySevenSeg.render} and {@code DisplayText.render}.
 *
 * <p><b>Seven segments.</b> One digit per {@code digitCount}, least significant on the right, each {@code size.x - 0.125}
 * apart after the first (the digits overlap their frames by an eighth). The value is clamped at zero (CE's own TODO: a negative
 * number has no sign), in base 10 or base 16. A frame is the textured base and a dark border ({@code 0.31} grey); each of the
 * seven segments is the display's colour, emissive, at full strength when its bit of {@link DisplaySevenSeg#SEGMENTS} is set
 * and at a tenth when not. Segment {@code j} of the byte is the model's {@code seg_(6 - j)}.
 *
 * <p><b>Text.</b> A black plate with a grey border {@code 0.05} wider, and the text on it in the dye's map colour, emissive
 * when {@code isLit}. <b>The client measures the string and reports it back</b> ({@link DisplayText#setMeasured}), because the
 * selection box the editor draws depends on the font and a server has none (D-3064): the width in pixels and the font height.
 */
@OnlyIn(Dist.CLIENT)
final class DisplayRenderers {

	private DisplayRenderers() {}

	static void register() {
		ControlRenderers.register(DisplaySevenSeg.class, (c, d) -> {
			int value = Math.max(0, (int) c.getVar("value").getNumber());
			int base = c.isDecimal() ? 10 : 16;
			float[] color = c.getColor();
			int digitCount = c.getDigitCount();

			for(int i = 0; i < digitCount; i++) {
				int character = DisplaySevenSeg.SEGMENTS[value % base];
				value /= base;

				float tOff = i * c.getSize()[0] - i * (i > 0 ? 0.125F : 0);

				d.pose.pushPose();
				d.pose.translate(-tOff, 0, 0);
				d.part("display_seven_seg", "base");
				d.part("display_seven_seg", "border", 0.31F, 0.31F, 0.31F);
				for(int j = 0; j < 7; j++) {
					boolean enabled = (character & (1 << j)) != 0;
					float cMul = enabled ? 1.0F : 0.1F;
					d.part("display_seven_seg", "seg_" + (6 - j), color[0] * cMul, color[1] * cMul, color[2] * cMul, true);
				}
				d.pose.popPose();
			}
		});

		ControlRenderers.register(DisplayText.class, (c, d) -> {
			String text = c.getVar("text").toString();
			boolean isLit = c.getVar("isLit").getBoolean();
			int color = c.getVar("color").getEnum(DyeColor.class).getMapColor().col;

			c.setMeasured(d.font.width(text), d.font.lineHeight);
			float s = c.getScale() / 500F;

			d.pose.pushPose();
			d.pose.translate(0, .03F, 0);
			d.flatText(s, true);
			d.text(text, 0, 0, color, isLit);
			d.pose.popPose();

			// The plate: CE drew it in the plane rotated 90 degrees about X and pushed back 0.01, the black plate in front of
			// the grey border. The selection box is in the panel's own coordinates, so the control's position comes off it.
			float[] box = c.getBox();
			float x0 = box[0] - c.posX, z0 = box[1] - c.posY, x1 = box[2] - c.posX, z1 = box[3] - c.posY;
			float border = .05F;
			d.flatQuad(x0 - border, z0 - border, x1 + border, z1 + border, .01F, .3F, .3F, .3F);
			d.flatQuad(x0, z0, x1, z1, .02F, 0F, 0F, 0F);
		});
	}
}
