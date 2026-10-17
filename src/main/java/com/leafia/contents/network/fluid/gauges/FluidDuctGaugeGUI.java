package com.leafia.contents.network.fluid.gauges;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.GuiScreenLeafia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The flow gauge's screen: one text field holding the number a full turn of the dial stands for.
 *
 * <p>Upstream: {@code .../gauges/FluidDuctGaugeGUI.java}, whose author says it was "mostly stolen from
 * GUIPump lmao". A 90 by 20 field at the middle of the screen less 50, a line of grey text above it,
 * and nothing else: no button, no panel. Closing the screen sends whatever is in the field.
 *
 * <ul>
 * <li>A number that parses is sent as a {@code long} under key 0 and plays the click at pitch 1;
 *     one that does not (empty, a letter, too big for a long) plays the refusal and sends nothing.</li>
 * <li>Nothing is trimmed, so a space makes it fail, and a negative or zero is sent as it stands -
 *     {@link FluidDuctGaugeTE#needleAngle} copes with neither, as upstream's does not.</li>
 * <li>The field is not focused when the screen opens: click in it first, or the keys go nowhere.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class FluidDuctGaugeGUI extends GuiScreenLeafia {

	final FluidDuctGaugeTE te;
	EditBox field;

	public FluidDuctGaugeGUI(FluidDuctGaugeTE te) {
		super();
		this.te = te;
	}

	/** The one place a client opens it; {@code FluidDuctGaugeTE} reaches this through a client-only holder. */
	public static void open(FluidDuctGaugeTE te) {
		Minecraft.getInstance().setScreen(new FluidDuctGaugeGUI(te));
	}

	@Override
	protected void init() {

		super.init();

		field = new EditBox(this.font, this.width / 2 - 50, 100, 90, 20, Component.empty());
		field.setMaxLength(32);
		field.setValue(String.valueOf(te.maximum));
		addRenderableWidget(field);
	}

	/** Upstream's {@code onGuiClosed}. */
	@Override
	public void removed() {
		super.removed();
		sendMaximum(field == null ? "" : field.getValue());
	}

	/** What closing does with the field's text. */
	void sendMaximum(String text) {
		try {
			long value = Long.parseLong(text);
			LeafiaPacket._start(te).__write(FluidDuctGaugeTE.ID_FILL, value).__sendToServer();
			playClick(1);
		} catch(NumberFormatException ignored) {
			playDenied();
		}
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);
		graphics.drawString(this.font, I18nUtil.resolveKey("gui.fluid_duct_gauge.max"), this.width / 2 - 50, 80, 0xA0A0A0, false);
	}
}
