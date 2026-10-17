package com.leafia.contents.machines.powercores.dfc.components.exchanger;

import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The DFC heat exchanger's screen: input and output tanks, the amount boiled per cycle, the cycle's delay
 * in ticks, and the chain's compression with a save button.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/exchanger/CoreExchangerGUI.java}, on
 * {@code leafia:textures/gui/dfc/gui_exchanger.png}, 176 by 184. Every rectangle is upstream's: the tanks at
 * (24, 30) and (136, 30), 16 by 52; the amount field at (74, 28) with its hover area (70, 22), 36 by 18;
 * the delay field at (74, 46) under (70, 40); the compression field at (90, 72), two characters, saved
 * with the button at (115, 66), 18 square, which refuses anything under 1. The amount and the delay are
 * sent on every key typed into them, upstream's.
 */
@OnlyIn(Dist.CLIENT)
public class CoreExchangerScreen extends LCEGuiInfoContainer<CoreExchangerMenu> {

	static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/dfc/gui_exchanger.png");
	final CoreExchangerTE te;
	final EditBox[] fields = new EditBox[3];
	FiaUIRect amountHover;
	FiaUIRect cycleHover;
	FiaUIRect saveBtn;
	int amount = 0;
	int delay = 0;
	int compression = 0;
	int saveButtonCoolDown = 0;

	public CoreExchangerScreen(CoreExchangerMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		imageWidth = 176;
		imageHeight = 184;
		this.te = menu.te;
	}

	void updateTextBoxes() {
		if(te.amountToHeat != amount) {
			amount = te.amountToHeat;
			fields[0].setValue(Integer.toString(amount));
		}
		if(te.tickDelay != delay) {
			delay = te.tickDelay;
			fields[1].setValue(Integer.toString(delay));
		}
		if(te.compression != compression) {
			compression = te.compression;
			fields[2].setValue(Integer.toString(compression));
		}
	}

	@Override
	protected void init() {
		super.init();
		saveBtn = new FiaUIRect(this, 115, 66, 18, 18);
		amountHover = new FiaUIRect(this, 70, 22, 36, 18);
		cycleHover = new FiaUIRect(this, 70, 40, 36, 18);
		fields[0] = new EditBox(this.font, leftPos + 72 + 2, topPos + 24 + 4, 30, 10, Component.empty());
		fields[1] = new EditBox(this.font, leftPos + 72 + 2, topPos + 42 + 4, 24, 10, Component.empty());
		fields[2] = new EditBox(this.font, leftPos + 81 + 9, topPos + 68 + 4, 24, 10, Component.empty());
		for(EditBox field : fields) {
			field.setTextColor(0x5BBC00);
			field.setTextColorUneditable(0x499500);
			field.setBordered(false);
			field.setValue("0");
			addRenderableWidget(field);
		}
		fields[0].setMaxLength(5);
		fields[1].setMaxLength(4);
		fields[2].setMaxLength(2);
		amount = delay = compression = 0;
		updateTextBoxes();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		renderBackground(graphics, mouseX, mouseY, partialTicks);
		super.render(graphics, mouseX, mouseY, partialTicks);
		renderTooltip(graphics, mouseX, mouseY);
		GUIElements.drawFluidTankInfo(graphics, this, te.input, mouseX, mouseY, leftPos + 24, topPos + 30, 16, 52, hasShiftDown());
		GUIElements.drawFluidTankInfo(graphics, this, te.output, mouseX, mouseY, leftPos + 136, topPos + 30, 16, 52, hasShiftDown());
		if(amountHover.isMouseIn(mouseX, mouseY))
			drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey("gui.heatex.amount")}, mouseX, mouseY);
		if(cycleHover.isMouseIn(mouseX, mouseY))
			drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey("gui.heatex.cycle")}, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		boolean handled = super.mouseClicked(mouseX, mouseY, mouseButton);
		if(mouseButton == 0 && saveBtn.isMouseIn((int) mouseX, (int) mouseY) && saveButtonCoolDown == 0) {
			try {
				int value = Integer.parseInt(fields[2].getValue());
				if(value <= 0) {
					playDenied();
					return true;
				}
				LeafiaPacket._start(te).__write(2, value).__sendToServer();
				playClick(1);
				saveButtonCoolDown = 20;
			} catch(NumberFormatException ignored) {
				playDenied();
			}
			return true;
		}
		return handled;
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		boolean typed = super.charTyped(codePoint, modifiers);
		sendTyped();
		return typed;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		for(EditBox field : fields) {
			if(field.isFocused() && field.keyPressed(keyCode, scanCode, modifiers)) {
				sendTyped();
				return true;
			}
		}
		for(EditBox field : fields)
			if(field.isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Upstream's {@code keyTyped} tail: the focused amount or delay is sent as it is typed. */
	private void sendTyped() {
		if(fields[0].isFocused()) {
			try {
				LeafiaPacket._start(te).__write(0, Integer.parseInt(fields[0].getValue())).__sendToServer();
			} catch(NumberFormatException ignored) {}
		}
		if(fields[1].isFocused()) {
			try {
				LeafiaPacket._start(te).__write(1, Integer.parseInt(fields[1].getValue())).__sendToServer();
			} catch(NumberFormatException ignored) {}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if(saveButtonCoolDown > 0) {
			saveButtonCoolDown--;
			graphics.blit(TEXTURE, leftPos + 115, topPos + 66, 176, 14, 18, 18);
		}
		if(fields[0].isFocused())
			graphics.blit(TEXTURE, leftPos + 72, topPos + 24, 176, 0, 32, 14);
		if(fields[1].isFocused())
			graphics.blit(TEXTURE, leftPos + 72, topPos + 42, 176, 0, 32, 14);
		if(fields[2].isFocused())
			graphics.blit(TEXTURE, leftPos + 81, topPos + 68, 176, 0, 32, 14);
		GUIElements.drawFluidTank(graphics, te.input, leftPos + 24, topPos + 31 + 52, 16, 52);
		GUIElements.drawFluidTank(graphics, te.output, leftPos + 136, topPos + 31 + 52, 16, 52);
		updateTextBoxes();
	}
}
