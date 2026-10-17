package com.leafia.contents.machines.elevators.floors;

import com.hbm.util.i18n.I18nUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.GuiScreenLeafia;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A landing's floor-number screen: one two-digit field and one button.
 *
 * <p>Upstream: {@code .../elevators/floors/EvFloorGUI.java}. A 66 by 36 panel, a text field at
 * (19, 19) and a save button at (43, 13), every pixel upstream's and every texture coordinate
 * read off {@code floor.png} (the focus highlight at 84,0 and the pressed button at 66,0).
 *
 * <h2>It has no menu, and does not need one</h2>
 *
 * The screen holds no items, so it is a {@link GuiScreenLeafia} - a plain {@code Screen} - and
 * opens from a packet rather than from a menu type: {@link EvFloorTE#openGui} tells the one
 * client to open it, exactly as upstream's {@code idGui} did, and the client reads the floor
 * number off the block entity it was handed. Saving sends {@code idFloor} to the server, whose
 * {@code onReceivePacketServer} stores it and re-broadcasts it.
 *
 * <h2>Three things kept that look like mistakes</h2>
 *
 * <ul>
 * <li>The button's hit box is <b>inclusive on both ends</b> - 43 to 61 and 13 to 31, nineteen
 *     pixels square against a drawn button of eighteen. {@code FiaUIRect.isMouseIn} is
 *     half-open and would be the more careful choice; upstream did not use it here.</li>
 * <li>The 20-tick pressed highlight is counted down <b>per frame</b>, in the background layer,
 *     not per tick, so it lasts twenty frames - a third of a second at sixty and rather longer
 *     on a slow machine. Upstream's arrangement, and cosmetic.</li>
 * <li>The click is refused while that counter is running, so a second save is swallowed until
 *     the highlight has gone. A number that does not parse plays the refusal sound and sends
 *     nothing.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class EvFloorGUI extends GuiScreenLeafia {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/elevators/floor.png");

	/** Panel size. */
	public static final int WIDTH = 66;
	public static final int HEIGHT = 36;

	final EvFloorTE te;
	private EditBox field;
	int click = 0;

	public EvFloorGUI(EvFloorTE te) {
		super();
		this.te = te;
		this.xSize = WIDTH;
		this.ySize = HEIGHT;
	}

	/** The one place a client opens it; {@code EvFloorTE} reaches this through a client-only holder. */
	public static void open(EvFloorTE te) {
		Minecraft.getInstance().setScreen(new EvFloorGUI(te));
	}

	@Override
	protected void init() {

		super.init();

		this.field = new EditBox(this.font, guiLeft + 7 + 12, guiTop + 15 + 4, 24 - 4, 10, Component.empty());
		this.field.setTextColor(0x5BBC00);
		this.field.setTextColorUneditable(0x499500);
		this.field.setBordered(false);
		this.field.setMaxLength(2);
		this.field.setValue(Integer.toString(te.floor));
		addRenderableWidget(this.field);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {

		boolean handled = super.mouseClicked(x, y, button);

		int mouseX = (int) x;
		int mouseY = (int) y;

		if(mouseX - guiLeft >= 43 && mouseY - guiTop >= 13 && mouseX - guiLeft <= 43 + 18 && mouseY - guiTop <= 13 + 18) {
			if(button == 0 && click <= 0) {
				try {
					int floor = Integer.parseInt(field.getValue());
					click = 20;
					playClick(1.0F);
					LeafiaPacket._start(te).__write(EvFloorTE.idFloor, floor).__sendToServer();
					field.setValue(Integer.toString(floor));
				} catch(NumberFormatException ignored) {
					playDenied();
				}
			}
		}

		return handled;
	}

	@Override
	protected void drawGuiScreenForegroundLayer(GuiGraphics graphics, int mouseX, int mouseY) {
		String name = I18nUtil.resolveKey("gui.evfloor", te.floor);
		graphics.drawString(this.font, name, this.xSize / 2 - this.font.width(name) / 2, 4, 4210752, false);
	}

	@Override
	protected void drawGuiScreenBackgroundLayer(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {

		drawDefaultBackground(graphics, mouseX, mouseY, partialTicks);

		graphics.blit(TEXTURE, guiLeft, guiTop, 0, 0, xSize, ySize);

		if(field.isFocused())
			graphics.blit(TEXTURE, guiLeft + 7, guiTop + 15, 84, 0, 32, 14);

		if(click > 0) {
			click--;
			graphics.blit(TEXTURE, guiLeft + 43, guiTop + 13, 66, 0, 18, 18);
		}
	}
}
