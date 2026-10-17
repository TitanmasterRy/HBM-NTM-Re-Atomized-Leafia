package com.leafia.contents.control.upgrades;

import com.leafia.contents.control.upgrades.AddonUpgradeItem.ControlUpgradeFreqPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The control upgrade's screen: a name and one text box for the channel.
 *
 * <p>Upstream: {@code .../control/upgrades/UpgradeControlGUI.java}, 125 by 42 on {@code upgrade_control.png},
 * its box at (25 + 4, 18 + 4) in the panel, 82 wide and 14 tall, green text, no border, ten characters at most.
 * The channel is sent <b>when the screen closes</b>, whatever closed it - not on every key. The title is
 * centred at y 6 in the dark grey of every panel's.
 */
@OnlyIn(Dist.CLIENT)
public class UpgradeControlScreen extends GuiScreenLeafia {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/upgrade_control.png");

	private static final int TEXT_COLOR = 0x00FF00;

	protected EditBox frequency;
	public final String defaultFreq;

	public UpgradeControlScreen(String defaultFreq) {
		this.xSize = 125;
		this.ySize = 42;
		this.defaultFreq = defaultFreq;
	}

	@Override
	protected void init() {
		super.init();
		int oX = 4;
		int oY = 4;
		this.frequency = new EditBox(this.font, guiLeft + 25 + oX, guiTop + 18 + oY, 90 - oX * 2, 14, Component.empty());
		this.frequency.setTextColor(TEXT_COLOR);
		this.frequency.setTextColorUneditable(TEXT_COLOR);
		this.frequency.setBordered(false);
		this.frequency.setMaxLength(AddonUpgradeItem.MAX_FREQUENCY);
		this.frequency.setValue(defaultFreq);
		addRenderableWidget(this.frequency);
		setInitialFocus(this.frequency);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);
		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		Component name = Component.translatable("item.upgrade_control.name");
		graphics.drawString(this.font, name, this.xSize / 2 - this.font.width(name) / 2, 6, 0x404040, false);
	}

	/** Upstream's {@code onGuiClosed}: every way the screen can end, not only the close key. */
	@Override
	public void removed() {
		super.removed();
		if(minecraft == null || minecraft.getConnection() == null) return;
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new ControlUpgradeFreqPacket(frequency.getValue()));
		if(packet != null) packet.__sendToServer();
	}
}
