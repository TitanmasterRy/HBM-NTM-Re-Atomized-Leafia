package com.leafia.contents.machines.reactors.apr.blocks.core.container;

import java.util.Arrays;
import java.util.List;

import org.joml.Matrix4f;

import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.BobMathUtil;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE;
import com.leafia.contents.machines.reactors.apr.blocks.core.APRCoreTE.APRChamber;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The APR's screen: coolant and liquid oxygen, the selected chamber's input and output, the particle tank, the
 * funnel lever, a seven-segment RPS readout, the hull and damage bars and a quarter-scale temperature graph of every
 * chamber.
 *
 * <p>Upstream: {@code .../apr/blocks/core/container/APRMainUI.java}, every coordinate and source rectangle
 * upstream's on {@code gui_main.png} (211 x 220). Dragging the lever sends {@code 10 + 90 x (mouse - 136) / 33}
 * clamped to 10..100; the page arrows step through the chambers; the void button (199, 124) asks the core to empty
 * its particles into capsules. The power line reads {@code output x 20} HE/s, right-aligned at 202.
 *
 * <p>The graph is upstream's: a quarter-scale grid of nine lines, 0 to 8,192, in 50 by 25 pixels clipped to its
 * box, each chamber a green polyline of its last sixteen samples, the selected one opaque and the rest at 0.4.
 * 1.12.2's four GL clip planes are a scissor here, and its GL lines are 1.21.1's debug lines, which are one pixel
 * wide at any scale as GL lines were.
 */
@OnlyIn(Dist.CLIENT)
public class APRMainUI extends LCEGuiInfoContainer<APRMainContainer> {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/apr/gui_main.png");

	public final APRCoreTE te;

	public APRMainUI(APRMainContainer menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.te = menu.te;
		this.imageWidth = 211;
		this.imageHeight = 220;
	}

	public FiaUIRect control;
	public FiaUIRect tank;
	public FiaUIRect pageLeft;
	public FiaUIRect pageRight;
	public FiaUIRect empty;
	public FiaUIRect hull;
	public FiaUIRect graph;
	public FiaUIRect damage;

	@Override
	protected void init() {
		super.init();
		control = new FiaUIRect(this, 138, 59, 37, 19);
		tank = new FiaUIRect(this, 153, 96, 52, 16);
		pageLeft = new FiaUIRect(this, 65, 95, 18, 18);
		pageRight = new FiaUIRect(this, 83, 95, 18, 18);
		empty = new FiaUIRect(this, 199, 124, 8, 8);
		hull = new FiaUIRect(this, 77, 25, 4, 52);
		graph = new FiaUIRect(this, 9, 84, 50, 25);
		damage = new FiaUIRect(this, 77, 80, 126, 2);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		String name = title.getString();
		g.drawString(font, name, imageWidth / 2 - font.width(name) / 2, 4, 4210752, false);
		g.drawString(font, playerInventoryTitle, 17 + 8, imageHeight - 96 + 2, 4210752, false);
		g.drawString(font, I18nUtil.resolveKey("tile.apr_core.gui.output"), 138, 24, 4210752, false);
		g.drawString(font, I18nUtil.resolveKey("tile.apr_core.gui.control"), 138, 51, 4210752, false);
		g.drawString(font, I18nUtil.resolveKey("tile.apr_core.gui.rps"), 176, 51, 4210752, false);
		if(te == null) return;
		String power = BobMathUtil.getShortNumber(te.getPowerOutput() * 20) + "HE/s";
		g.drawString(font, power, 181 + 29 - 10 + 2 - font.width(power), 37, 0xD84EDB, false);
	}

	public boolean draggingControl = false;

	@Override
	public boolean mouseClicked(double mx, double my, int mouseButton) {
		boolean handled = super.mouseClicked(mx, my, mouseButton);
		if(te == null || mouseButton != 0) return handled;
		int mouseX = (int) mx, mouseY = (int) my;
		if(control.isMouseIn(mouseX, mouseY)) {
			draggingControl = true;
			playClick(1);
		}
		// upstream: getPageSafely() so a page hacked out of range comes back into it
		if(pageLeft.isMouseIn(mouseX, mouseY)) {
			if(te.page > 0) {
				LeafiaPacket._start(te).__write(APRCoreTE.idPage, getPageSafely(-1)).__sendToServer();
				playClick(1);
			} else
				playDenied();
		}
		if(pageRight.isMouseIn(mouseX, mouseY)) {
			if(te.page < te.chambers.size() - 1) {
				LeafiaPacket._start(te).__write(APRCoreTE.idPage, getPageSafely(1)).__sendToServer();
				playClick(1);
			} else
				playDenied();
		}
		if(empty.isMouseIn(mouseX, mouseY)) {
			if(tooHot() || !te.getItem(0).isEmpty())
				playDenied();
			else
				playClick(1);
			LeafiaPacket._start(te).__write(APRCoreTE.idVoid, true).__sendToServer();
		}
		return handled;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int state) {
		if(state == 0)
			draggingControl = false;
		return super.mouseReleased(mouseX, mouseY, state);
	}

	private boolean tooHot() {
		for(APRChamber data1 : te.chamberDatas) {
			if(data1.blanketTemp > 100)
				return true;
		}
		return false;
	}

	private void tooltip(GuiGraphics g, List<String> lines, int mouseX, int mouseY) {
		drawHoveringText(g, lines, mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		renderBackground(g, mouseX, mouseY, partialTicks);
		super.render(g, mouseX, mouseY, partialTicks);
		renderTooltip(g, mouseX, mouseY);
		if(te == null) return;
		if(control.isMouseIn(mouseX, mouseY))
			tooltip(g, List.of(I18nUtil.resolveKey("tile.apr_core.gui.control_value", (int) Math.ceil(te.targetControl))), mouseX, mouseY);
		if(pageLeft.isMouseIn(mouseX, mouseY) || pageRight.isMouseIn(mouseX, mouseY))
			tooltip(g, List.of(I18nUtil.resolveKey("tile.apr_core.gui.page", (te.page + 1) + "/" + te.chambers.size())), mouseX, mouseY);
		if(tank.isMouseIn(mouseX, mouseY)) {
			GUIElements.drawHoveringText(g, Arrays.asList(I18nUtil.resolveKey("tile.apr_core.gui.ap"), (int) Math.ceil(te.particles) + "/" + te.particleCap + "mB"),
					mouseX, mouseY, font, width, height, 6, GUIElements.STANDARD_LINE_DIST,
					GUIElements.STANDARD_COLOR_BACKGROUND, GUIElements.STANDARD_COLOR_BACKGROUND, 0xffee35ff, 0xff59f7ff);
		}
		boolean shift = hasShiftDown();
		GUIElements.drawFluidTankInfo(g, this, te.oxygen, mouseX, mouseY, leftPos + 102, topPos + 25, 16, 52, shift);
		GUIElements.drawFluidTankInfo(g, this, te.lox, mouseX, mouseY, leftPos + 120, topPos + 25, 16, 52, shift);
		if(te.chamberDatas.isEmpty()) return;
		APRChamber data = te.chamberDatas.get(getPageSafely());
		if(data != null) {
			GUIElements.drawFluidTankInfo(g, this, data.input, mouseX, mouseY, leftPos + 26, topPos + 25, 16, 52, shift);
			GUIElements.drawFluidTankInfo(g, this, data.output, mouseX, mouseY, leftPos + 44, topPos + 25, 16, 52, shift);
		}
		drawCustomInfoStat(g, mouseX, mouseY, leftPos - 16, topPos + 21, 16, 16, leftPos, topPos + 21, I18nUtil.resolveKeyArray("tile.apr_core.gui.tips.hullTemp"));
		drawCustomInfoStat(g, mouseX, mouseY, leftPos - 16, topPos + 21 + 16, 16, 16, leftPos, topPos + 21 + 16, I18nUtil.resolveKeyArray("tile.apr_core.gui.tips.blanketTemp"));
		drawCustomInfoStat(g, mouseX, mouseY, leftPos - 16, topPos + 21 + 32, 16, 16, leftPos, topPos + 21 + 32, I18nUtil.resolveKeyArray("tile.apr_core.gui.tips.rps"));
		if(empty.isMouseIn(mouseX, mouseY)) {
			if(tooHot())
				tooltip(g, Arrays.asList(I18nUtil.resolveKey("tile.apr_core.gui.void"), ChatFormatting.RED + I18nUtil.resolveKey("tile.apr_core.gui.void.hot")), mouseX, mouseY);
			else if(!te.getItem(0).isEmpty())
				tooltip(g, Arrays.asList(I18nUtil.resolveKey("tile.apr_core.gui.void"), ChatFormatting.RED + I18nUtil.resolveKey("tile.apr_core.gui.void.slot")), mouseX, mouseY);
			else
				tooltip(g, Arrays.asList(I18nUtil.resolveKey("tile.apr_core.gui.void"), ChatFormatting.GOLD + I18nUtil.resolveKey("tile.apr_core.gui.void.capsule", (int) (te.particles / 1000))), mouseX, mouseY);
		}
		if(graph.isMouseIn(mouseX, mouseY) && data != null) {
			int hottestChamber = 0;
			double hottestTemp = 0;
			int i = 0;
			for(APRChamber data1 : te.chamberDatas) {
				if(data1.blanketTemp > hottestTemp) {
					hottestTemp = data1.blanketTemp;
					hottestChamber = i;
				}
				i++;
			}
			tooltip(g, Arrays.asList(I18nUtil.resolveKey("tile.apr_core.gui.heat.blanket", String.format("%1.1f°C", data.graph[APRCoreTE.graphXSegments - 1])),
					ChatFormatting.GOLD + I18nUtil.resolveKey("tile.apr_core.gui.heat.blanket.hottest", "#" + (hottestChamber + 1))), mouseX, mouseY);
		}
		if(hull.isMouseIn(mouseX, mouseY))
			tooltip(g, List.of(I18nUtil.resolveKey("tile.apr_core.gui.heat.hull", String.format("%1.1f°C", te.hullTemp))), mouseX, mouseY);
		if(damage.isMouseIn(mouseX, mouseY))
			tooltip(g, List.of(I18nUtil.resolveKey("tile.apr_core.gui.damage", (int) Math.ceil(te.damage * 100d / APRCoreTE.maxDamage) + "%")), mouseX, mouseY);
	}

	public static int getLeverOffset(double control) {
		return APRCoreTE.leverOffset(control);
	}

	public static double controlFromMouse(int relX) {
		return APRCoreTE.controlFromMouse(relX);
	}

	public int getPageSafely(int offset) {
		return Math.max(Math.min(te.page + offset, te.chamberDatas.size() - 1), 0);
	}

	public int getPageSafely() {
		return getPageSafely(0);
	}

	private void blitRect(GuiGraphics g, FiaUIRect rect, int u, int v) {
		g.blit(TEXTURE, rect.absX(), rect.absY(), u, v, rect.w, rect.h);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
		g.setColor(1, 1, 1, 1);
		g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		if(te == null) return;
		if(te.particleCap > 0)
			g.blit(TEXTURE, leftPos + 153, topPos + 96, 0, 231, (int) (52 * te.particles / te.particleCap), 16);
		drawGraph(g);
		if(draggingControl)
			LeafiaPacket._start(te).__write(APRCoreTE.idFunnelDesired, controlFromMouse(mouseX - leftPos)).__sendToServer();
		RenderSystem.enableBlend();
		g.setColor(1, 1, 1, 1);
		g.blit(TEXTURE, leftPos + 138 + getLeverOffset(te.targetControl), topPos + 59, 211, 0, 4, 19);
		g.setColor(1, 1, 1, 0.4f);
		g.blit(TEXTURE, leftPos + 138 + getLeverOffset(te.control), topPos + 59, 211, 0, 4, 19);
		g.setColor(1, 1, 1, 1);
		if(pageLeft.isMouseIn(mouseX, mouseY))
			blitRect(g, pageLeft, 219, 0);
		if(pageRight.isMouseIn(mouseX, mouseY))
			blitRect(g, pageRight, 237, 0);
		g.setColor(0.75f, 0.75f, 0.75f, 1);
		if(te.page <= 0)
			blitRect(g, pageLeft, 65, 95);
		if(te.page >= te.chambers.size() - 1)
			blitRect(g, pageRight, 83, 95);
		g.setColor(1, 1, 1, 1);
		{
			int barLength = (int) Math.ceil((te.hullTemp - 20) * hull.h / (1538 - 20));
			g.blit(TEXTURE, hull.absX(), hull.absY() + hull.h - barLength, 215, 52 - barLength, hull.w, barLength);
		}
		{
			int barLength = te.damage * damage.w / APRCoreTE.maxDamage;
			g.blit(TEXTURE, damage.absX(), damage.absY(), 0, 247, barLength, damage.h);
		}
		{
			int rps = (int) Math.round(te.rps);
			int x = leftPos + 194;
			boolean firstDigit = true;
			while(firstDigit || rps > 0) {
				g.blit(TEXTURE, x, topPos + 63, 6 * (rps % 10), 220, 6, 11);
				x -= 7;
				firstDigit = false;
				rps = rps / 10;
			}
		}
		GUIElements.drawFluidTank(g, te.oxygen, leftPos + 102, topPos + 25 + 52, 16, 52);
		GUIElements.drawFluidTank(g, te.lox, leftPos + 120, topPos + 25 + 52, 16, 52);
		if(!te.chamberDatas.isEmpty()) {
			APRChamber data = te.chamberDatas.get(getPageSafely());
			if(data != null) {
				GUIElements.drawFluidTank(g, data.input, leftPos + 26, topPos + 25 + 52, 16, 52);
				GUIElements.drawFluidTank(g, data.output, leftPos + 44, topPos + 25 + 52, 16, 52);
			}
		}
		drawInfoPanel(g, leftPos - 16, topPos + 21, 16, 16, 2);
		drawInfoPanel(g, leftPos - 16, topPos + 21 + 16, 16, 16, 2);
		drawInfoPanel(g, leftPos - 16, topPos + 21 + 32, 16, 16, 3);
	}

	private void drawInfoPanel(GuiGraphics g, int x, int y, int w, int h, int type) {
		drawInfoPanel(g, x, y, type);
	}

	/** Upstream's {@code drawGraph}: a quarter-scale grid and each chamber's last sixteen samples. */
	public void drawGraph(GuiGraphics g) {
		float scale = 0.25f;
		g.enableScissor(leftPos + 9, topPos + 84, leftPos + 9 + 50, topPos + 84 + 25);
		g.pose().pushPose();
		g.pose().translate(leftPos + 9, topPos + 84, 0);
		g.pose().scale(scale, scale, 1);
		float border = 4;
		float width = 50 / scale;
		float height = 25 / scale - border * 2;
		int divisions = 8;
		float brightness = 0.126f;
		for(int i = 0; i <= divisions; i++) {
			float ratio = i / (float) divisions;
			float y = height * (1 - ratio) + border;
			String s = Integer.toString(8192 * i / divisions);
			line(g.pose().last().pose(), font.width(s) + 1, y, width, y, brightness, brightness, brightness, 1);
			g.pose().pushPose();
			g.pose().translate(0, y - 3.5, 0);
			g.drawString(font, s, 0, 0, 0x3f3f3f, false);
			g.pose().popPose();
		}
		for(int j = 0; j < te.chamberDatas.size(); j++) {
			double lastY = 0;
			APRChamber data = te.chamberDatas.get(j);
			float alpha = j == te.page ? 1 : 0.4f;
			for(int i = 0; i < APRCoreTE.graphXSegments; i++) {
				double xRatio0 = (i - 1) / (APRCoreTE.graphXSegments - 1d);
				double xRatio1 = i / (APRCoreTE.graphXSegments - 1d);
				double yRatio = (1 - data.graph[i] / 8192d) * height + border;
				if(i > 0)
					line(g.pose().last().pose(), (float) (xRatio0 * width), (float) lastY, (float) (xRatio1 * width), (float) yRatio, 0, 1, 0, alpha);
				lastY = yRatio;
			}
		}
		g.pose().popPose();
		g.disableScissor();
	}

	private static void line(Matrix4f matrix, float x0, float y0, float x1, float y1, float r, float gr, float b, float a) {
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
		buffer.addVertex(matrix, x0, y0, 0).setColor(r, gr, b, a);
		buffer.addVertex(matrix, x1, y1, 0).setColor(r, gr, b, a);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}
}
