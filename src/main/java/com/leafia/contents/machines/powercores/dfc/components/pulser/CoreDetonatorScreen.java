package com.leafia.contents.machines.powercores.dfc.components.pulser;

import javax.annotation.Nullable;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.unsorted.BullshitInDavenportIowa;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The detonator's screen: a twelve-key pad, a power gauge, a count gauge, two switches, and four lamps
 * saying how big the blast will be.
 *
 * <p>Upstream: {@code .../components/pulser/CoreDetonatorGUI.java}, on {@code gui_pulser.png} (176 x 166).
 * Every rectangle is upstream's: the keys a 3 by 4 grid of 9-pixel squares from (10, 18) on a 10-pixel
 * pitch (1 to 9, 0, then "enter" and "clear" - upstream numbers them so key 10 is 0, 11 enter, 12
 * clear); the power gauge at (57, 20), 16 by 52, drawn up from y = 73 from x = 176; the count at
 * (83, 17), 4 by 35, drawn up from y = 52 out of (176, 52 .. 87); the arm switch at (7, 61) and the
 * detonate switch at (33, 63); the yield lamps at x = 86, 103, 120 and 137 on y = 59, lit at one, two,
 * five and eight detonators, each lower lamp dimmed to half once a higher one is lit.
 *
 * <p>Until the code is accepted the screen shows the prompt and nothing else, and every other click is
 * a refusal. The status line is drawn white with a shadow at {@code width / 2 + 10}, upstream's.
 *
 * <p>One guard upstream lacks: it reads the core's yield ({@code lastPulserPower}) off a cast that
 * throws when the core has gone while the screen is open and unlocked. Here a missing core reads as no
 * yield, which is what the lamps already show. Not balance; a client crash avoided (D-3011).
 */
@OnlyIn(Dist.CLIENT)
public class CoreDetonatorScreen extends LCEGuiInfoContainer<CoreDetonatorMenu> {

	@Nullable
	public static CoreDetonatorScreen openGUI = null;
	String auth = "";
	String code = "";
	boolean blocked = false;
	boolean unlocked = false;
	@Nullable
	final CoreDetonatorTE te;
	static final ResourceLocation rsc = LeafiaRef.rl("textures/gui/dfc/gui_pulser.png");

	final FiaUIRect[] keypad = new FiaUIRect[12];
	FiaUIRect nukeInfo;
	FiaUIRect switch1;
	FiaUIRect switch2;
	FiaUIRect charge;

	public CoreDetonatorScreen(CoreDetonatorMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		imageWidth = 176;
		imageHeight = 166;
		this.te = menu.te;
		openGUI = this;
	}

	@Override
	public void removed() {
		super.removed();
		openGUI = null;
	}

	/** The server's answer to a typed code. */
	public void answer(boolean accepted) {
		if(accepted) {
			playClick(1);
			auth = code;
			unlocked = true;
		} else {
			playDenied();
			code = "";
		}
		blocked = false;
	}

	@Override
	protected void init() {
		super.init();
		for(int x = 0; x < 3; x++) {
			for(int y = 0; y < 4; y++) {
				int index = x + y * 3;
				keypad[index] = new FiaUIRect(this, 10 + 10 * x, 18 + 10 * y, 9, 9);
			}
		}
		nukeInfo = new FiaUIRect(this, 86, 59, 67, 16);
		switch1 = new FiaUIRect(this, 7, 61, 18, 18);
		switch2 = new FiaUIRect(this, 33, 63, 14, 14);
		charge = new FiaUIRect(this, 83, 17, 4, 35);
	}

	@Nullable
	private IMixinTileEntityCore core() {
		return te != null && te.lastGetCore instanceof IMixinTileEntityCore mixin ? mixin : null;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		renderBackground(graphics, mouseX, mouseY, partialTicks);
		super.render(graphics, mouseX, mouseY, partialTicks);
		if(unlocked && te != null) {
			drawElectricityInfo(graphics, mouseX, mouseY, leftPos + 57, topPos + 20, 16, 52, te.power, te.getMaxPower());
			if(switch1.isMouseIn(mouseX, mouseY)) {
				drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey(te.isOn ? "tile.dfc_pulser.gui.switch1.on" : "tile.dfc_pulser.gui.switch1.off")}, mouseX, mouseY);
			}
			if(switch2.isMouseIn(mouseX, mouseY))
				drawFluidInfo(graphics, new String[] {ChatFormatting.RED + I18nUtil.resolveKey("tile.dfc_pulser.gui.switch2")}, mouseX, mouseY);
			if(charge.isMouseIn(mouseX, mouseY)) {
				float timeLeft = 30;
				IMixinTileEntityCore mixin = core();
				if(mixin != null) {
					timeLeft = 30 - mixin.getDetonationTimer() / 20f;
					if(mixin.getDetonation())
						timeLeft -= partialTicks / 20f;
				}
				String unit = "s";
				if(LeafiaConfig.bullshitUnits) {
					unit = "stevejob";
					timeLeft = (float) BullshitInDavenportIowa.SToSj(timeLeft);
				}
				drawFluidInfo(graphics, new String[] {ChatFormatting.RED + String.format("%02.2f" + unit, timeLeft)}, mouseX, mouseY);
			}
			IMixinTileEntityCore mixin = core();
			if(mixin != null && nukeInfo.isMouseIn(mouseX, mouseY)) {
				int power = mixin.lastPulserPower();
				if(power >= 1) {
					if(power == 1)
						drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey("tile.dfc_pulser.gui.radius", I18nUtil.resolveKey("tile.dfc_pulser.gui.radius.minimal"))}, mouseX, mouseY);
					else {
						int radius = 130 * (power - 1);
						if(power >= 8)
							radius += 130 * (power - 8);
						if(!LeafiaConfig.bullshitUnits)
							drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey("tile.dfc_pulser.gui.radius", radius + "m")}, mouseX, mouseY);
						else
							drawFluidInfo(graphics, new String[] {I18nUtil.resolveKey("tile.dfc_pulser.gui.radius", BullshitInDavenportIowa.MToBn(radius) + "banana")}, mouseX, mouseY);
					}
				}
			}
		}
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		String name = I18nUtil.resolveKey("tile.dfc_pulser.name");
		graphics.drawString(font, name, imageWidth / 2 - font.width(name) / 2, 6, 4210752, false);
		String message = "";
		if(te == null || te.lastGetCore == null)
			message = I18nUtil.resolveKey("tile.dfc_pulser.gui.status.connect");
		else {
			if(!unlocked) {
				if(!te.local$codeSet)
					message = I18nUtil.resolveKey("tile.dfc_pulser.gui.status.new", code);
				else
					message = I18nUtil.resolveKey("tile.dfc_pulser.gui.status.code", code);
			}
		}
		if(!message.isEmpty())
			graphics.drawString(font, message, (int) (imageWidth / 2f + 10 - font.width(message) / 2f), 41, 0xFFFFFF, true);
	}

	public void playKeypad() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(LeafiaSounds.UI_BUTTON_KEYPAD.get(), 1));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int mouseButton) {
		boolean result = super.mouseClicked(mx, my, mouseButton);
		int mouseX = (int) mx, mouseY = (int) my;
		if(mouseButton == 0 && te != null) {
			if(switch1.isMouseIn(mouseX, mouseY)) {
				if(unlocked) {
					playClick(1);
					LeafiaPacket._start(te).__write(0, auth).__write(1, !te.isOn).__sendToServer();
				} else
					playDenied();
			}
			if(switch2.isMouseIn(mouseX, mouseY)) {
				if(unlocked) {
					playClick(1);
					LeafiaPacket._start(te).__write(0, auth).__write(2, false).__sendToServer();
				} else
					playDenied();
			}
			for(int i = 0; i < 12; i++) {
				FiaUIRect rect = keypad[i];
				if(rect.isMouseIn(mouseX, mouseY) && !blocked) {
					if(!unlocked) {
						int n = i + 1;
						if(n == 10)
							n = 0;
						if(n == 11) {
							if(!code.isEmpty()) {
								blocked = true;
								LeafiaPacket._start(te).__write(30, code).__sendToServer();
							} else
								playDenied();
						} else {
							if(n == 12)
								code = "";
							else
								code = code + n;
							playKeypad();
						}
					} else
						playDenied();
				}
			}
		}
		return result;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		graphics.blit(rsc, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		graphics.setColor(0.9f, 0.9f, 0.9f, 1);
		if(!unlocked) {
			for(FiaUIRect rect : keypad) {
				if(rect.isMouseIn(mouseX, mouseY))
					drawTexturedModalByFiaRect(graphics, rsc, rect, rect.x, rect.y);
			}
		}
		graphics.setColor(1, 1, 1, 1);
		if(te == null) return;
		if(te.isOn)
			drawTexturedModalByFiaRect(graphics, rsc, switch1, 192, 0);
		IMixinTileEntityCore mixin = core();
		if(mixin != null) {
			if(mixin.getDetonation())
				drawTexturedModalByFiaRect(graphics, rsc, switch2, 210, 0);
			int power = mixin.lastPulserPower();
			if(power >= 8) {
				graphics.blit(rsc, leftPos + 137, topPos + 59, 240, 18, 16, 16);
				graphics.setColor(0.5f, 0.5f, 0.5f, 1);
			}
			if(power >= 5) {
				graphics.blit(rsc, leftPos + 120, topPos + 59, 224, 18, 16, 16);
				graphics.setColor(0.5f, 0.5f, 0.5f, 1);
			}
			if(power >= 2) {
				graphics.blit(rsc, leftPos + 103, topPos + 59, 208, 18, 16, 16);
				graphics.setColor(0.5f, 0.5f, 0.5f, 1);
			}
			if(power >= 1)
				graphics.blit(rsc, leftPos + 86, topPos + 59, 192, 18, 16, 16);
			graphics.setColor(1, 1, 1, 1);
		}
		int det = te.getDetScaled(35);
		graphics.blit(rsc, leftPos + 83, topPos + 52 - det, 176, 87 - det, 4, det);
		int i = (int) te.getPowerScaled(52);
		graphics.blit(rsc, leftPos + 57, topPos + 73 - i, 176, 52 - i, 16, i);
	}
}
