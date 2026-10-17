package com.leafia.contents.machines.powercores.dfc.components.absorber;

import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.BobMathUtil;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The overhauled absorber's screen: its coolant tank, its power, what the core paid it this tick, what
 * the cable relayed and what it is putting out, and the level, typed as a percentage and saved with the
 * button.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/components/absorber/CoreReceiverGUI.java},
 * on {@code leafia:textures/gui/dfc/gui_receiver.png}, 205 by 167. Every rectangle is upstream's: the tank
 * at (10, 5), 16 by 52; the power column at (46, 6) whose fill is this tick's pay over 100 billion SPK; the
 * level bar at (192 - n, 9) for {@code n = round(35 * level)}; the field at (15, 71), three characters; the
 * save button at (50, 65), 18 square, with a twenty-tick highlight. "Output" is the client's rolling
 * twenty-tick average of the pay above what the cable took, times 5,000 HE and twenty ticks.
 */
@OnlyIn(Dist.CLIENT)
public class CoreReceiverScreen extends LCEGuiInfoContainer<CoreReceiverMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/dfc/gui_receiver.png");
	private final LeafiaCoreReceiverTE receiver;
	private EditBox field;
	int saveButtonCooldown = 0;

	public CoreReceiverScreen(CoreReceiverMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.receiver = menu.receiver;
		this.imageWidth = 205;
		this.imageHeight = 167;
	}

	@Override
	protected void init() {
		super.init();
		field = new EditBox(this.font, leftPos + 15, topPos + 71, 24, 10, Component.empty());
		field.setTextColor(0x5BBC00);
		field.setTextColorUneditable(0x499500);
		field.setBordered(false);
		field.setMaxLength(3);
		field.setValue(String.valueOf(receiver.leafia$getLevel() * 100));
		addRenderableWidget(field);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		GUIElements.drawFluidTankInfo(graphics, this, receiver.tank, mouseX, mouseY, leftPos + 17 - 7, topPos + 16 - 11, 16, 52, hasShiftDown());
		drawCustomInfo(graphics, mouseX, mouseY, leftPos + 46, topPos + 6, 16, 52, BobMathUtil.getShortNumber(receiver.power) + "HE");
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		boolean handled = super.mouseClicked(x, y, button);
		if(leftPos + 50 < x && leftPos + 50 + 18 > x && topPos + 65 < y && topPos + 65 + 18 > y && button == 0) {
			if(saveButtonCooldown <= 0) {
				saveButtonCooldown = 20;
				try {
					double level = Mth.clamp(Double.parseDouble(field.getValue()) / 100d, 0, 1);
					field.setValue(String.valueOf(level * 100));
					LeafiaPacket._start(receiver).__write(0, level).__sendToServer();
					playClick(1);
				} catch(NumberFormatException ignored) {}
			}
			return true;
		}
		return handled;
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		String name = title.getString();
		graphics.drawString(font, name, 114 - font.width(name) / 2, 7, 4210752, false);

		graphics.drawString(font, "Input:", 98 - font.width("Input:") / 2 + 5, 21, 4210752, false);
		String sparks = BobMathUtil.getShortNumber(receiver.joules) + "SPK";
		graphics.drawString(font, sparks, 161 + 29 - font.width(sparks), 21, 0x4EB3DB, false);
		graphics.drawString(font, "Relaying:", 98 - font.width("Relaying:") / 2 + 5, 21 + 20, 4210752, false);
		String relaying = BobMathUtil.getShortNumber(receiver.leafia$syncSpk()) + "SPK";
		graphics.drawString(font, relaying, 161 + 29 - font.width(relaying), 21 + 20, 0x4EB3DB, false);
		graphics.drawString(font, "Output:", 98 - font.width("Output:") / 2, 21 + 20 * 2, 4210752, false);
		String power = BobMathUtil.getShortNumber((long) receiver.leafia$joulesPerSec() * 5000 * 20) + "HE/s";
		graphics.drawString(font, power, 161 + 29 - font.width(power), 21 + 20 * 2, 0xD84EDB, false);

		String inventory = playerInventoryTitle.getString();
		graphics.drawString(font, inventory, imageWidth - 8 - font.width(inventory), imageHeight - 96 + 2 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		int px = (int) Math.round(35 * receiver.leafia$getLevel());
		graphics.blit(TEXTURE, leftPos + 192 - px, topPos + 9, 256 - px, 32, px, 4);

		if(field.isFocused())
			graphics.blit(TEXTURE, leftPos + 7, topPos + 67, 221, 0, 32, 14);

		if(saveButtonCooldown > 0) {
			saveButtonCooldown--;
			graphics.blit(TEXTURE, leftPos + 50, topPos + 65, 221, 14, 18, 18);
		}
		int i = LeafiaCoreReceiverTE.joulesColumn(receiver.joules);
		graphics.blit(TEXTURE, leftPos + 46, topPos + 58 - i, 205, 52 - i, 16, i);

		GUIElements.drawFluidTank(graphics, receiver.tank, leftPos + 10, topPos + 69 - 11, 16, 52);
	}
}
