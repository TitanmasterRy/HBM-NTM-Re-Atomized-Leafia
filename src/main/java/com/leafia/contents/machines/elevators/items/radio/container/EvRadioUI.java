package com.leafia.contents.machines.elevators.items.radio.container;

import com.leafia.LeafiaMod;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.ElevatorMusic;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioSyncRequestPacket;
import com.leafia.contents.machines.elevators.items.radio.EvRadioItem.EvRadioUIPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The elevator radio's main screen: the name of the track chosen for going up, going down and
 * idling, and a Change button beside each.
 *
 * <p>Upstream: {@code .../radio/container/EvRadioUI.java}, a {@code GuiScreenLeafia} with no
 * container behind it. Opening it asks the server what the radio is set to
 * ({@link EvRadioSyncRequestPacket}); until the answer arrives the panel is dimmed and says
 * "Loading..." and nothing can be clicked. Closing it sends the three choices back
 * ({@link EvRadioUIPacket}), unless it is only being replaced by the list screen.
 *
 * <p>256 by 184 on a 256-square sheet; the three Change buttons are 39 by 12 at (208, 19), (208, 73)
 * and (208, 127) and their hover frame is the strip at (0, 184). Every coordinate, the 0.9 grey of
 * the loading state and the text colour 4210752 are upstream's. Three things are not: a bound
 * texture is an argument to {@code blit}; a shader colour also tints text, so it is put back to
 * white before the first string where 1.12.2's font renderer would not have taken it; and the
 * "Loading..." line sits at a half pixel upstream, which {@code GuiGraphics} reaches through the
 * pose rather than a float argument.
 *
 * <p>Closing the screen before the answer arrives sends nothing (D-3076; upstream sent three -1s, and
 * the server then replaced the radio's tag with an empty one, wiping whatever it held).
 */
@OnlyIn(Dist.CLIENT)
public class EvRadioUI extends GuiScreenLeafia {

	/** Whichever radio screen is open, or null. */
	public static EvRadioUI instance;

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/elevators/radio_main.png");

	public int upOrdinal = -1;
	public int downOrdinal = -1;
	public int idleOrdinal = -1;
	public boolean loading = true;

	private FiaUIRect changeUp;
	private FiaUIRect changeDown;
	private FiaUIRect changeIdle;
	private boolean screenSwitching = false;

	/** Opening from the item: asks the server for the choices. */
	public EvRadioUI() {
		xSize = 256;
		ySize = 184;
		instance = this;
		send(new EvRadioSyncRequestPacket());
	}

	/** Coming back from the list screen: the choices are already in hand. */
	public EvRadioUI(int upOrdinal, int downOrdinal, int idleOrdinal) {
		xSize = 256;
		ySize = 184;
		instance = this;
		this.upOrdinal = upOrdinal;
		this.downOrdinal = downOrdinal;
		this.idleOrdinal = idleOrdinal;
		loading = false;
	}

	/**
	 * Sends a packet, unless there is no connection to send it on.
	 *
	 * <p>Upstream's screens send unconditionally. 1.21.1 throws when asked to send with no
	 * connection, and {@code removed()} runs as a screen is torn down on a disconnect, which is
	 * when there is none - so the screens would turn a dropped connection into a second exception.
	 */
	static void send(LeafiaCustomPacketEncoder packet) {
		if(Minecraft.getInstance().getConnection() == null) return;
		try {
			LeafiaCustomPacket started = LeafiaCustomPacket.__start(packet);
			if(started != null) started.__sendToServer();
		} catch(RuntimeException e) {
			LeafiaMod.LOGGER.warn("Radio packet {} not sent: {}", packet.getClass().getSimpleName(), e.toString());
		}
	}

	/** Sends the choices to the server. Shared with the list screen, which closes without passing back through here. */
	static void sendChoices(int up, int down, int idle) {
		EvRadioUIPacket packet = new EvRadioUIPacket();
		packet.upOrdinal = up;
		packet.downOrdinal = down;
		packet.idleOrdinal = idle;
		send(packet);
	}

	@Override
	protected void init() {
		super.init();
		changeUp = new FiaUIRect(this, 208, 19, 39, 12);
		changeDown = new FiaUIRect(this, 208, 73, 39, 12);
		changeIdle = new FiaUIRect(this, 208, 127, 39, 12);
	}

	private void selector(int target) {
		screenSwitching = true;
		playClick(1);
		Minecraft.getInstance().setScreen(new EvRadioUIList(target, upOrdinal, downOrdinal, idleOrdinal));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if(loading) return handled;
		if(button == 0) {
			if(changeUp.isMouseIn((int) mouseX, (int) mouseY))
				selector(0);
			if(changeDown.isMouseIn((int) mouseX, (int) mouseY))
				selector(1);
			if(changeIdle.isMouseIn((int) mouseX, (int) mouseY))
				selector(2);
		}
		return handled;
	}

	@Override
	public void removed() {
		instance = null;
		if(screenSwitching) return;
		// closed before the answer arrived: the three -1s would wipe the radio's choices (D-3076)
		if(loading) {
			super.removed();
			return;
		}
		sendChoices(upOrdinal, downOrdinal, idleOrdinal);
		super.removed();
	}

	private void drawMusicInfo(GuiGraphics graphics, int y, int ordinal) {
		if(ordinal == -1) return;
		ElevatorMusic music = ElevatorMusic.ofOrdinal(ordinal);
		graphics.drawString(font, music.title, 10, y + 1, 4210752, false);
		graphics.drawString(font, "( " + music.artist + " )", 10, y + 1 + 12, 4210752, false);
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.setColor(1, 1, 1, 1);
		Component name = LeafiaItems.ev_radio.get().getDescription();
		graphics.drawString(font, name, xSize / 2 - font.width(name) / 2, 5, 4210752, false);
		drawMusicInfo(graphics, 31, upOrdinal);
		drawMusicInfo(graphics, 85, downOrdinal);
		drawMusicInfo(graphics, 139, idleOrdinal);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);
		if(loading) graphics.setColor(0.9f, 0.9f, 0.9f, 1);
		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);
		graphics.setColor(1, 1, 1, 1);
		if(loading) {
			String text = "Loading...";
			graphics.pose().pushPose();
			graphics.pose().translate(guiLeft + xSize / 2f - 0.5f - font.width(text) / 2f, guiTop + ySize / 2f - 6.5f, 0);
			graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
			graphics.pose().popPose();
			return;
		}
		if(changeUp.isMouseIn(mouseX, mouseY))
			drawTexturedModalByFiaRect(graphics, TEXTURE, changeUp, 0, 184);
		if(changeDown.isMouseIn(mouseX, mouseY))
			drawTexturedModalByFiaRect(graphics, TEXTURE, changeDown, 0, 184);
		if(changeIdle.isMouseIn(mouseX, mouseY))
			drawTexturedModalByFiaRect(graphics, TEXTURE, changeIdle, 0, 184);
	}
}
