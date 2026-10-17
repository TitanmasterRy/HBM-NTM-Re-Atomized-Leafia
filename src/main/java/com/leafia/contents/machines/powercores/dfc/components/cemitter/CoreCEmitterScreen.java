package com.leafia.contents.machines.powercores.dfc.components.cemitter;

import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.BobMathUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;
import com.llib.math.SIPfx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The creative booster's screen: four beams typed with SI prefixes ({@code 20M}, {@code 1G}), a button
 * beside each to choose it, the on switch, and the base emitter's bars.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/cemitter/CoreCEmitterGUI.java}, on
 * {@code leafia:textures/gui/dfc/gui_cemitter.png}, 176 by 170, titled "Creative DFC Emitter (Debug)" as
 * upstream hard-codes it. Every rectangle is upstream's: the fields at (12, 20 + 14n), 29 by 7, five
 * characters each, highlighted from (194, 0); the choosers at (61, 17 + 14n), 10 by 13, the chosen one
 * marked from (194, 9); the switch at (76, 56), 18 square, lit from (176, 0); the setting bar at (81, 52),
 * 4 wide and 35 tall; the power column at (49, 73), 16 by 52; the glow at (151, 33) while on with more than
 * 500,000 HE; the tank at (13, 101). A field's value is sent when it loses focus, as upstream does.
 *
 * <p>The switch is the base emitter's {@code toggle} control (upstream's {@code AuxButtonPacket(pos, 0, 1)},
 * deprecated upstream and not ported, D-968). Enter leaves a field: upstream tests key code 13, which in
 * LWJGL 2 is the equals key, so upstream's own "Enter" never worked; GLFW's Enter is what was meant.
 */
@OnlyIn(Dist.CLIENT)
public class CoreCEmitterScreen extends LCEGuiInfoContainer<CoreCEmitterMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/dfc/gui_cemitter.png");
	private final CoreCEmitterTE emitter;
	private final EditBox[] fields = new EditBox[4];
	int focused = -1;

	public CoreCEmitterScreen(CoreCEmitterMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.emitter = menu.emitter;
		this.imageWidth = 176;
		this.imageHeight = 170;
	}

	@Override
	protected void init() {
		super.init();
		for(int z = 0; z < 4; z++) {
			fields[z] = new EditBox(this.font, leftPos + 12, topPos + 20 + 14 * z, 29, 7, Component.empty());
			fields[z].setTextColor(0x5BBC00);
			fields[z].setTextColorUneditable(0x499500);
			fields[z].setBordered(false);
			fields[z].setMaxLength(5);
			addRenderableWidget(fields[z]);
		}
		syncTextField();
	}

	public void syncTextField() {
		for(int z = 0; z < 4; z++)
			if(!fields[z].isFocused())
				fields[z].setValue(SIPfx.custom((x, s) -> x.longValue() + s.substring(1), emitter.joulesT[z], false));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		if(emitter.changed) {
			emitter.changed = false;
			syncTextField();
		}
		drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 81, topPos + 14, 8, 39, mouseX, mouseY,
				"Output: " + BobMathUtil.getShortNumber(emitter.prev) + "SPK");
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int i) {
		boolean handled = super.mouseClicked(x, y, i);
		for(int z = 0; z < 4; z++) {
			if(leftPos + 61 <= x && leftPos + 61 + 10 >= x && topPos + 17 + 14 * z <= y && topPos + 17 + 13 + 14 * z >= y) {
				playClick(1);
				LeafiaPacket._start(emitter).__write(0, z).__sendToServer();
			}
		}
		this.clickSendFlag(emitter.getBlockPos(), x, y, 151 - 75, 56, 18, 18, "toggle");
		return handled;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && focused != -1) fields[focused].setFocused(false);
		for(EditBox field : fields)
			if(field.isFocused() && field.keyPressed(keyCode, scanCode, modifiers)) return true;
		if(focused != -1 && fields[focused].isFocused() && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		String name = "Creative DFC Emitter (Debug)";
		graphics.drawString(font, name, imageWidth / 2 - font.width(name) / 2, 6, 4210752, false);
		String inventory = playerInventoryTitle.getString();
		graphics.drawString(font, inventory, imageWidth - 8 - font.width(inventory), imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		int lastFocused = focused;
		focused = -1;
		for(int z = 0; z < 4; z++) {
			if(fields[z].isFocused()) {
				graphics.blit(TEXTURE, leftPos + 11, topPos + 19 + 14 * z, 194, 0, 31, 9);
				focused = z;
			}
		}
		try {
			if(lastFocused != -1 && focused != lastFocused)
				LeafiaPacket._start(emitter).__write(lastFocused + 1, (long) SIPfx.parse(fields[lastFocused].getValue())).__sendToServer();
		} catch(NumberFormatException ignored) {
		}
		graphics.blit(TEXTURE, leftPos + 61, topPos + 17 + 14 * emitter.selecting, 194, 9, 10, 13);

		if(emitter.isOn) {
			graphics.blit(TEXTURE, leftPos + 151 - 76, topPos + 56, 192 - 16, 0, 18, 18);
		}

		int emitterWatts = emitter.getWattsScaled(35);
		graphics.blit(TEXTURE, leftPos + 81, topPos + 52 - emitterWatts, 176, 87 - emitterWatts, 4, emitterWatts);

		int i = (int) emitter.getPowerScaled(52);
		graphics.blit(TEXTURE, leftPos + 49, topPos + 73 - i, 176, 52 - i, 16, i);

		if(emitter.isOn && emitter.power > 500000) graphics.blit(TEXTURE, leftPos + 149 + 2, topPos + 33, 176, 53, 80, 3);

		GUIElements.drawFluidTank(graphics, emitter.tank, leftPos + 13, topPos + 101, 16, 52);
	}
}
