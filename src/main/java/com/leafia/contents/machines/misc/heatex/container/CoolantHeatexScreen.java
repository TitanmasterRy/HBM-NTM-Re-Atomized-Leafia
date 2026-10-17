package com.leafia.contents.machines.misc.heatex.container;

import java.util.Arrays;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.machines.misc.heatex.CoolantHeatexTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The coolant heat exchanger's screen: the hot side's two tanks on the left, the cold side's two on the right,
 * the two switches between them and the two numbers that set the cycle.
 *
 * <p>Upstream: {@code .../heatex/container/CoolantHeatexGUI.java}, on {@code LCEGuiInfoContainer}, 176 by 204
 * on {@code gui_heatex.png}. Every coordinate is upstream's.
 *
 * <ul>
 * <li><b>Tanks.</b> The hot side's input at (17, 18) and output at (41, 18), both 16 by 52 - Forge tanks in
 *     FF mode, ordinary tanks in NTMF mode; the cold side's input at (107, 18) and its small output at
 *     (124, 79), 10 by 16. The power bar is at (152, 18), 16 by 52, with the lamp at (156, 4) lit while there is
 *     enough power to run.</li>
 * <li><b>The FF / NTMF switch</b> at (50, 72), 51 by 16: its knob sits at x = 60 (FF) or 71 (NTMF). Its tooltip is
 *     four lines in light purple, the last naming the mode the exchanger is in.</li>
 * <li><b>The coolant switch</b> at (107, 78), 14 by 18: the icon is cut from the sheet at x = 202 + 14 per
 *     mode (air, water, coolant), and the tooltip names the coolant.</li>
 * <li><b>Two numbers</b>, in green text with no box at (67, 31) and (67, 49): the amount per cycle and the delay in
 *     ticks. Each change sends the value, never less than one - and so do the server's rules for it.</li>
 * </ul>
 *
 * <p>The fuzzy identifier's click on a tank (D-1053) is on the Forge tanks' tooltips, as it is on upstream's.
 */
@OnlyIn(Dist.CLIENT)
public class CoolantHeatexScreen extends LCEGuiInfoContainer<CoolantHeatexMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/coolant_heatex.png");

	private final CoolantHeatexTE heatex;

	private FiaUIRect ntmfSwitch;
	private FiaUIRect coolantMode;
	private EditBox fieldCycles;
	private EditBox fieldDelay;

	public CoolantHeatexScreen(CoolantHeatexMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.heatex = menu.heatex;
		this.imageWidth = 176;
		this.imageHeight = 204;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	protected void init() {

		super.init();

		ntmfSwitch = new FiaUIRect(this, 50, 72, 51, 16);
		coolantMode = new FiaUIRect(this, 107, 78, 14, 18);

		fieldCycles = field(leftPos + 73 - 6, topPos + 31, heatex == null ? 24_000 : heatex.amountToCool, 1);
		fieldDelay = field(leftPos + 73 - 6, topPos + 49, heatex == null ? 1 : heatex.tickDelay, 2);
	}

	/** Upstream's {@code initText}: green, no background, five characters; the responder is set after the value, so opening sends nothing. */
	private EditBox field(int x, int y, int value, int key) {
		EditBox box = new EditBox(this.font, x, y, 30, 10, Component.empty());
		box.setTextColor(0x00ff00);
		box.setTextColorUneditable(0x00ff00);
		box.setBordered(false);
		box.setMaxLength(5);
		box.setValue(String.valueOf(value));
		box.setResponder(text -> {
			if(heatex != null) LeafiaPacket._start(heatex).__write(key, CoolantHeatexTE.parseCount(text)).__sendToServer();
		});
		addRenderableWidget(box);
		return box;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(heatex != null) {
			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 152, topPos + 18, 16, 52, heatex.power, CoolantHeatexTE.maxPower);

			if(ntmfSwitch.isMouseIn(mouseX, mouseY)) {
				String light = ChatFormatting.LIGHT_PURPLE.toString();
				drawFluidInfo(graphics, Arrays.asList(
						light + I18nUtil.resolveKey("gui.coolant_heatex.ntmfswitch.title"),
						light + " " + I18nUtil.resolveKey("gui.coolant_heatex.ntmfswitch.desc.ff"),
						light + " " + I18nUtil.resolveKey("gui.coolant_heatex.ntmfswitch.desc.ntmf"),
						" " + I18nUtil.resolveKey("gui.coolant_heatex.ntmfswitch", heatex.ntmfMode ? "NTMF" : "FF")),
						mouseX, mouseY);
			}

			if(coolantMode.isMouseIn(mouseX, mouseY)) {
				drawFluidInfo(graphics, Arrays.asList(I18nUtil.resolveKey("gui.coolant_heatex.coolant",
						CoolantHeatexTE.coolant(heatex.coolantMode).getLocalizedName()).split("\\$")), mouseX, mouseY);
			}

			if(leftPos + 70 - 6 <= mouseX && leftPos + 70 - 6 + 36 > mouseX && topPos + 26 < mouseY && topPos + 26 + 18 >= mouseY)
				drawFluidInfo(graphics, Arrays.asList(I18nUtil.resolveKey("gui.heatex.amount")), mouseX, mouseY);
			if(leftPos + 70 - 6 <= mouseX && leftPos + 70 - 6 + 36 > mouseX && topPos + 44 < mouseY && topPos + 44 + 18 >= mouseY)
				drawFluidInfo(graphics, Arrays.asList(I18nUtil.resolveKey("gui.heatex.cycle")), mouseX, mouseY);

			tankInfo(graphics, mouseX, mouseY, heatex.ntmfInputB, 107, 18, 16, 52);
			tankInfo(graphics, mouseX, mouseY, heatex.ntmfOutputB, 124, 79, 10, 16);
			if(heatex.ntmfMode) {
				tankInfo(graphics, mouseX, mouseY, heatex.ntmfInputA, 17, 18, 16, 52);
				tankInfo(graphics, mouseX, mouseY, heatex.ntmfOutputA, 41, 18, 16, 52);
			} else {
				LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 17, topPos + 18, 16, 52,
						heatex.ffInputA.getFluid(), heatex.ffInputA.getCapacity(), fallback(heatex.inputFilter));
				LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + 41, topPos + 18, 16, 52,
						heatex.ffOutputA.getFluid(), heatex.ffOutputA.getCapacity(), fallback(heatex.outputFilter));
			}
		}

		renderTooltip(graphics, mouseX, mouseY);
	}

	private static net.neoforged.neoforge.fluids.FluidStack fallback(com.hbm.inventory.fluid.FluidType type) {
		var ff = LeafiaFF.getFF(type);
		return ff == null ? net.neoforged.neoforge.fluids.FluidStack.EMPTY : new net.neoforged.neoforge.fluids.FluidStack(ff, 1);
	}

	private void tankInfo(GuiGraphics graphics, int mouseX, int mouseY, FluidTank tank, int x, int y, int w, int h) {
		LeafiaFFClient.renderTankInfo(this, graphics, mouseX, mouseY, leftPos + x, topPos + y, w, h, tank, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if(button == 0 && heatex != null) {
			if(ntmfSwitch.isMouseIn((int) mouseX, (int) mouseY)) {
				playClick(1);
				LeafiaPacket._start(heatex).__write(0, false).__sendToServer();
				return true;
			}
			if(coolantMode.isMouseIn((int) mouseX, (int) mouseY)) {
				playClick(1);
				LeafiaPacket._start(heatex).__write(0, true).__sendToServer();
				return true;
			}
		}
		return handled;
	}

	/** A focused number box takes the keystroke before the inventory key or a hotbar key can act on it. */
	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if(key != 256) {
			for(EditBox box : new EditBox[] { fieldCycles, fieldDelay }) {
				if(box != null && box.isFocused() && box.canConsumeInput()) {
					box.keyPressed(key, scanCode, modifiers);
					return true;
				}
			}
		}
		return super.keyPressed(key, scanCode, modifiers);
	}

	/** Upstream's name at x = 94 in {@code 0xC7C1A3}, and the inventory label in the usual grey. */
	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, 94 - font.width(title) / 2, 6, 0xC7C1A3, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(heatex == null) return;

		if(heatex.power >= CoolantHeatexTE.powerConsumption)
			graphics.blit(TEXTURE, leftPos + 156, topPos + 4, 176, 52, 9, 12);

		int j = (int) (heatex.power * 52 / CoolantHeatexTE.maxPower);
		graphics.blit(TEXTURE, leftPos + 152, topPos + 70 - j, 176, 52 - j, 16, j);

		// ff/ntmf switch
		graphics.blit(TEXTURE, leftPos + (heatex.ntmfMode ? 71 : 60), topPos + 72, 192, 0, 10, 16);

		// coolant switch
		graphics.blit(TEXTURE, leftPos + 107, topPos + 78, 202 + 14 * heatex.coolantMode, 0, 14, 18);

		GUIElements.drawFluidTank(graphics, heatex.ntmfInputB, leftPos + 107, topPos + 18 + 52, 16, 52);
		GUIElements.drawFluidTank(graphics, heatex.ntmfOutputB, leftPos + 124, topPos + 79 + 16, 10, 16);
		if(heatex.ntmfMode) {
			GUIElements.drawFluidTank(graphics, heatex.ntmfInputA, leftPos + 17, topPos + 18 + 52, 16, 52);
			GUIElements.drawFluidTank(graphics, heatex.ntmfOutputA, leftPos + 41, topPos + 18 + 52, 16, 52);
		} else {
			LeafiaFFClient.drawTank(graphics, heatex.ffInputA, leftPos + 17, topPos + 18 + 52, 16, 52);
			LeafiaFFClient.drawTank(graphics, heatex.ffOutputA, leftPos + 41, topPos + 18 + 52, 16, 52);
		}
	}
}
