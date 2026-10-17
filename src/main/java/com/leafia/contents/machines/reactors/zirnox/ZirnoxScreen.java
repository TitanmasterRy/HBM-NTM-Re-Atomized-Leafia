package com.leafia.contents.machines.reactors.zirnox;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.reactors.zirnox.LeafiaZirnoxTE.PacketKeys;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;
import com.leafia.unsorted.BullshitInDavenportIowa;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The overhauled ZIRNOX's screen: the fuel lattice, a hull-temperature bar, a pressure dial, a CO2 gauge,
 * a draggable control-rod bar, the compression switch and the vent wheel.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/zirnox/ZirnoxGUI.java}, 215 x 243 over two
 * sheets, every rectangle and texture coordinate its own. Controls, as upstream:
 *
 * <ul>
 * <li>the control-rod bar (x 180-189, y 29-116) is grabbed by a click and dragged; the height from the top,
 *     over 88 pixels, is how far the rods are <em>in</em>, so the value sent is {@code 100 - that};</li>
 * <li>the compression switch (x 162-175, y 128-145) steps 1x, 10x, 100x, 1000x and round again;</li>
 * <li>the vent wheel is held: pressing it opens the vent, releasing the mouse or closing the screen shuts
 *     it.</li>
 * </ul>
 *
 * <p>The dial jitters when the pressure is at or past the 30-atmosphere limit, and the wheel turns, from the
 * block entity's {@link LeafiaZirnoxTE#clientTick} - run here, every client tick the screen is open, because
 * nothing else on the client draws them. {@code bullshitUnits} swaps degrees for grilles and atmospheres for
 * kirks, as everywhere in the addon. Upstream's {@code tile.water.name} is vanilla's water block name.
 */
@OnlyIn(Dist.CLIENT)
public class ZirnoxScreen extends LCEGuiInfoContainer<ZirnoxMenu> {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/reactors/leafia_zirnox.png");
	public static final ResourceLocation TEXTURE_02 = LeafiaRef.rl("textures/gui/reactors/leafia_zirnox_02.png");

	private final LeafiaZirnoxTE entity;
	private boolean barGrabbed = false;
	private boolean valveOpen = false;

	public ZirnoxScreen(ZirnoxMenu menu, Inventory invPlayer, Component title) {
		super(menu, invPlayer, title);
		this.entity = menu.reactor;
		this.imageWidth = 215;
		this.imageHeight = 243;
	}

	/** Upstream's {@code ui} rectangles. */
	enum UI {
		TANK_FEEDWATER(193, 27, 16, 52),
		TANK_STEAM(179, 128, 10, 16),
		BAR_HULL_TEMP(24, 25, 4, 88),
		BAR_CONTROL_ROD(181, 28, 8, 90),
		VALVE(11, 123, 26, 26),
		GAUGE_PRESSURE(5, 26, 18, 18),
		GAUGE_COOLANT(5, 65, 18, 12);

		final int x, y, width, height;

		UI(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	boolean isHovering(int mouseX, int mouseY, UI element) {
		return ((mouseX >= element.x + leftPos) && (mouseX <= element.x + element.width + leftPos)
				&& (mouseY >= element.y + topPos) && (mouseY <= element.y + element.height + topPos));
	}

	/** Upstream's {@code LeafiaClientUtil.statusDecimals}: the value rounded half-up to {@code decimals} places, into the key. */
	static String[] statusDecimals(String template, double value, int decimals) {
		double mul = Math.pow(10, decimals);
		return I18nUtil.resolveKeyArray(template, String.format("%01." + decimals + "f", Math.floor(value * mul + 0.5) / mul));
	}

	private void drawTankInfo(GuiGraphics graphics, int mouseX, int mouseY, UI element, FluidTank tank) {
		GUIElements.drawFluidTankInfo(graphics, this, tank, mouseX, mouseY,
				leftPos + element.x, topPos + element.y, element.width, element.height, hasShiftDown());
	}

	private void drawInfo(GuiGraphics graphics, int mouseX, int mouseY, UI element, String[] desc) {
		this.drawCustomInfo(graphics, mouseX, mouseY, leftPos + element.x, topPos + element.y - 1, element.width, element.height, desc);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if(entity != null && minecraft != null && minecraft.level != null) entity.clientTick(minecraft.level.random);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(entity != null) {
			drawTankInfo(graphics, mouseX, mouseY, UI.TANK_FEEDWATER, entity.water);
			drawTankInfo(graphics, mouseX, mouseY, UI.TANK_STEAM, entity.steam);
			if(!LeafiaConfig.bullshitUnits)
				drawInfo(graphics, mouseX, mouseY, UI.BAR_HULL_TEMP, statusDecimals("desc.leafia._repeated.reactors.temp.hull", entity.hulltemp, 1));
			else
				drawInfo(graphics, mouseX, mouseY, UI.BAR_HULL_TEMP, statusDecimals("desc.leafia._repeated.reactors.temp.hull.bs", BullshitInDavenportIowa.CelsiusToG(entity.hulltemp), 1));
			this.drawCustomInfo(graphics, mouseX, mouseY, leftPos + UI.BAR_CONTROL_ROD.x, topPos + UI.BAR_CONTROL_ROD.y - 1,
					UI.BAR_CONTROL_ROD.width + 4, UI.BAR_CONTROL_ROD.height,
					statusDecimals("desc.leafia._repeated.reactors.controlrods", entity.rods, 0));
			drawInfo(graphics, mouseX, mouseY, UI.VALVE, I18nUtil.resolveKeyArray("desc.leafia.zirnox.vent"));
			if(!LeafiaConfig.bullshitUnits)
				drawInfo(graphics, mouseX, mouseY, UI.GAUGE_PRESSURE, statusDecimals("desc.leafia.zirnox.pressure", entity.pressureLCE, 1));
			else
				drawInfo(graphics, mouseX, mouseY, UI.GAUGE_PRESSURE, statusDecimals("desc.leafia.zirnox.pressure.bs", entity.pressureLCE * 1770.49585941, 1));
			drawTankInfo(graphics, mouseX, mouseY, UI.GAUGE_COOLANT, entity.carbonDioxide);

			String[] text = I18nUtil.resolveKeyArray("desc.leafia.zirnox.tips.coolant");
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos - 16, topPos + 36, 16, 16, leftPos - 8, topPos + 36 + 16, text);

			String[] text1 = I18nUtil.resolveKeyArray("desc.leafia.zirnox.tips.pressure");
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos - 16, topPos + 36 + 16, 16, 16, leftPos - 8, topPos + 36 + 16, text1);

			String[] text0 = I18nUtil.resolveKeyArray("desc.leafia._repeated." + ((entity.rods != 0) ? "reactors.on" : "reactors.off"));
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 176, topPos + 6 - 1, 18, 19, mouseX, mouseY, text0);

			if(entity.water.getFill() <= 0) {
				String[] text2 = I18nUtil.resolveKeyArray("desc.leafia._repeated.reactors.require.properly",
						I18nUtil.resolveKey("block.minecraft.water"));
				this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos - 16, topPos + 36 + 32, 16, 16, leftPos - 8, topPos + 36 + 32 + 16, text2);
			}

			if(entity.carbonDioxide.getFill() < 4000) {
				String[] text3 = I18nUtil.resolveKeyArray("desc.leafia._repeated.reactors.require.properly",
						I18nUtil.resolveKey("desc.leafia._short.fluid.co2"));
				this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos - 16, topPos + 36 + 32 + 16, 16, 16, leftPos - 8, topPos + 36 + 32 + 16, text3);
			}

			String[] text4 = statusDecimals("desc.leafia._repeated.reactors.compression", (short) Math.pow(10, entity.compression), 0);
			this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 162, topPos + 128 - 1, 16, 20, mouseX, mouseY, text4);
		}

		this.renderTooltip(graphics, mouseX, mouseY);
	}

	private void send(PacketKeys key, Object value) {
		LeafiaPacket._start(entity).__write(key.key, value).__sendToServer();
	}

	/** The rods value a click or drag at screen height {@code y} asks for. */
	private int rodsAt(int y) {
		int rods = Mth.clamp((y - (topPos + 28 - 2)) * 100 / 88, 0, 100);
		return 100 - rods;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		boolean handled = super.mouseClicked(mx, my, button);
		if(entity == null || barGrabbed || valveOpen) return handled;
		int x = (int) Math.floor(mx);
		int y = (int) Math.floor(my);

		if(leftPos + 180 <= x && leftPos + 180 + 10 > x && topPos + 28 < y && topPos + 28 + 88 >= y) {
			barGrabbed = true;
			playClick(1.0F);
			send(PacketKeys.CONTROL_RODS, (byte) rodsAt(y));
			return true;
		}

		if(leftPos + 162 <= x && leftPos + 162 + 14 > x && topPos + 127 < y && topPos + 127 + 18 >= y) {
			playClick(1.0F);
			send(PacketKeys.COMPRESSION, (byte) ((entity.compression + 1) % 4));
			return true;
		}

		if(isHovering(x, y, UI.VALVE)) {
			valveOpen = true;
			send(PacketKeys.OPENVALVE, true);
			return true;
		}
		return handled;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
		if(barGrabbed && entity != null) {
			send(PacketKeys.CONTROL_RODS, (byte) rodsAt((int) Math.floor(my)));
		}
		return super.mouseDragged(mx, my, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		barGrabbed = false;
		if(valveOpen && entity != null) send(PacketKeys.OPENVALVE, false);
		valveOpen = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public void removed() {
		if(valveOpen && entity != null) send(PacketKeys.OPENVALVE, false);
		valveOpen = false;
		super.removed();
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, 107 - this.font.width(this.title) / 2, 4, 15066597, false);
		graphics.drawString(this.font, this.playerInventoryTitle, 26, this.imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 143);
		graphics.blit(TEXTURE, leftPos + 19, topPos + 143, 19, 143, 176, imageHeight - 143);

		if(entity == null) return;

		// "Hull Temperature" bar
		int pix = (int) Math.floor(Math.min((entity.hulltemp - 20) / (entity.meltingPoint - 20), 1) * 88);
		if(pix > 0) graphics.blit(TEXTURE, leftPos + 25, topPos + 115 - pix, 246, 172 - pix, 4, pix);

		// Control Rod
		float col = isHovering(mouseX, mouseY, UI.BAR_CONTROL_ROD) ? 0.85F : 1F;
		pix = (int) Math.floor((1 - entity.rods / 100f) * 88);
		graphics.setColor(col, col, col, 1.0F);
		if(pix > 0) graphics.blit(TEXTURE, leftPos + 181, topPos + 28, 238, 172 - pix, 8, pix);
		pix = (int) Math.floor((1 - entity.rodsTarget / 100f) * 88);
		RenderSystem.enableBlend();
		graphics.setColor(col, col, col, 0.25F);
		if(pix > 0) graphics.blit(TEXTURE, leftPos + 181, topPos + 28, 238, 172 - pix, 8, pix);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		if(entity.rods > 0) // Indicator Light
			graphics.blit(TEXTURE, leftPos + 175, topPos + 10, 233, 239, 23, 17);

		// CO2 Gauge
		graphics.blit(TEXTURE, leftPos + 5, topPos + 65, 238,
				(int) Math.floor(entity.carbonDioxide.getFill() / (float) entity.carbonDioxide.getMaxFill() * 6f + 0.5f) * 12, 18, 12);

		// Pressure Dial
		if(entity.pressureLCE < entity.maxPressureLCE)
			graphics.blit(TEXTURE, leftPos + 5, topPos + 26, 220, 18 * (int) Math.floor(entity.pressureLCE / entity.maxPressureLCE * 12 + 0.5), 18, 18);
		else
			graphics.blit(TEXTURE, leftPos + 5, topPos + 26, 202 + 18 * entity.dialX, 198 + 18 * entity.dialY, 18, 18);

		// PAGE 2
		// Valve
		col = isHovering(mouseX, mouseY, UI.VALVE) ? 1F : 0.8F;
		graphics.setColor(col, col, col, 1.0F);
		graphics.blit(TEXTURE_02, leftPos + 11, topPos + 123, 0, 38 + 26 * entity.valveLevel, 26, 26);
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

		// Compression
		graphics.blit(TEXTURE_02, leftPos + 162, topPos + 127, entity.compression % 2 * 14, entity.compression / 2 * 19, 14, 19);

		// Control Rods
		int level = (int) Math.min(Math.floor(entity.rods / 20f), 4) - 1;
		if(level >= 0)
			graphics.blit(TEXTURE_02, leftPos + 55, topPos + 24, 28 + level % 2 * 104, level / 2 * 104, 104, 104);

		this.drawInfoPanel(graphics, leftPos - 16, topPos + 36, 2);
		this.drawInfoPanel(graphics, leftPos - 16, topPos + 36 + 16, 3);

		if(entity.water.getFill() <= 0)
			this.drawInfoPanel(graphics, leftPos - 16, topPos + 36 + 32, 6);

		if(entity.carbonDioxide.getFill() < 4000)
			this.drawInfoPanel(graphics, leftPos - 16, topPos + 36 + 32 + 16, 7);

		GUIElements.drawFluidTank(graphics, entity.water, leftPos + 193, topPos + 79, 16, 52);
		GUIElements.drawFluidTank(graphics, entity.steam, leftPos + 179, topPos + 145, 10, 16);
	}
}
