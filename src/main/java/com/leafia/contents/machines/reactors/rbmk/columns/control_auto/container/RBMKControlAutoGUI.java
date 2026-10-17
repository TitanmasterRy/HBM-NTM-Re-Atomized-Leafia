package com.leafia.contents.machines.reactors.rbmk.columns.control_auto.container;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlAuto;
import com.hbm.inventory.container.ContainerRBMKControlAuto;
import com.hbm.packet.PacketDispatcher;
import com.leafia.dev.gui.FiaUIRect;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaRBMKSeams;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKControlAuto;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The automatic control rod's screen with the RBMK overhaul's scram lamp and its two acknowledge buttons.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/columns/control_auto/container/RBMKControlAutoGUI.java},
 * which {@code MixinTileEntityRBMKControlAuto.provideGUI} returned in place of NTM-CE's screen. Here the base
 * mod's screen opens and {@code LeafiaRBMKClient} swaps this one in on the same menu while
 * {@code overhauls.rbmkOverhaul} is on (D-3018). It is the base screen's layout moved left - the fields, the
 * function picker and the save button by 13 pixels ({@code offsA}), the level gauge by 16 ({@code offsB}) -
 * on upstream's {@code rbmk_control_auto_az5able.png}, which has the room on the right for:
 *
 * <ul>
 * <li>the lamp at {@code (144, 26)}, 24x25, blinking at 2 Hz (half of every 500 ms) while the rod is shut off,
 * with "Rod is shut off" (gold) or "Rod is active" over {@code (151, 34, 10x11)};</li>
 * <li>"Acknowledge All" at {@code (151, 54, 10x10)}, which always sends {@code acknowledge: true};</li>
 * <li>"Acknowledge" at {@code (151, 70, 10x10)}, which sends {@code acknowledge: false} if the rod is shut off and
 * plays the refusal otherwise.</li>
 * </ul>
 *
 * <p>The power lamp at {@code (136, 21)} is not moved, as upstream's. Upstream's tooltip says the second field
 * "Should be larger than level at min heat", which is the base mod's typo before it was fixed; kept, as the
 * addon's own text. Everything else is the base screen's, including its keyboard fix (D-1250).
 */
@OnlyIn(Dist.CLIENT)
public class RBMKControlAutoGUI extends LCEGuiInfoContainer<ContainerRBMKControlAuto> {

	private static final ResourceLocation texture = LeafiaRef.rl("textures/gui/reactors/rbmk_control_auto_az5able.png");

	static final int offsA = 13;
	static final int offsB = 16;

	private final BlockEntityRBMKControlAuto rod;
	private final EditBox[] fields = new EditBox[4];

	FiaUIRect rectBulb;
	FiaUIRect rectAll;
	FiaUIRect rectOne;

	public RBMKControlAutoGUI(ContainerRBMKControlAuto menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.rod = menu.rod;
		this.imageWidth = 176;
		this.imageHeight = 186;
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	private EditBox field(int x, int y, int maxLength, String initial) {
		EditBox box = new EditBox(this.font, leftPos + x, topPos + y, 26, 6, Component.empty());
		box.setTextColor(0xFFFFFF);
		box.setTextColorUneditable(0xFFFFFF);
		box.setBordered(false);
		box.setMaxLength(maxLength);
		box.setValue(initial);
		this.addRenderableWidget(box);
		return box;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

		rectBulb = new FiaUIRect(this, 151, 34, 10, 11);
		rectAll = new FiaUIRect(this, 151, 54, 10, 10);
		rectOne = new FiaUIRect(this, 151, 70, 10, 10);

		fields[0] = field(30 - offsA, 27, 3, String.valueOf((int) rod.levelUpper));
		fields[1] = field(30 - offsA, 38, 3, String.valueOf((int) rod.levelLower));
		fields[2] = field(30 - offsA, 49, 4, String.valueOf((int) rod.heatUpper));
		fields[3] = field(30 - offsA, 60, 4, String.valueOf((int) rod.heatLower));
	}

	private boolean scrammed() {
		return IMixinTileEntityRBMKControlAuto.leafia$getScrammed(rod);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 124 - offsB, topPos + 29, 16, 56, mouseX, mouseY,
				((int) (rod.rodLevel * 100)) + "%");

		String func = "Function: ";

		switch(rod.function) {
		case LINEAR: func += "Linear"; break;
		case QUAD_UP: func += "Quadratic"; break;
		case QUAD_DOWN: func += "Inverse Quadratic"; break;
		}

		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 58 - offsA, topPos + 26, 28, 19, mouseX, mouseY, func);
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 61 - offsA, topPos + 48, 22, 10, mouseX, mouseY, "Select linear interpolation");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 61 - offsA, topPos + 59, 22, 10, mouseX, mouseY, "Select quadratic interpolation");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 61 - offsA, topPos + 70, 22, 10, mouseX, mouseY, "Select inverse quadratic interpolation");

		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 28 - offsA, topPos + 26, 30, 10, mouseX, mouseY,
				"Level at max heat", "Should be smaller than level at min heat");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 28 - offsA, topPos + 37, 30, 10, mouseX, mouseY,
				"Level at min heat", "Should be larger than level at min heat");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 28 - offsA, topPos + 48, 30, 10, mouseX, mouseY,
				"Max heat", "Must be larger than min heat");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 28 - offsA, topPos + 59, 30, 10, mouseX, mouseY,
				"Min heat", "Must be smaller than max heat");
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 28 - offsA, topPos + 70, 30, 10, mouseX, mouseY,
				"Save parameters");

		if(rectBulb.isMouseIn(mouseX, mouseY))
			this.drawHoveringText(graphics, java.util.List.of(scrammed() ? ChatFormatting.GOLD + "Rod is shut off" : "Rod is active"), mouseX, mouseY);

		if(rectAll.isMouseIn(mouseX, mouseY))
			this.drawHoveringText(graphics, java.util.List.of("Acknowledge All"), mouseX, mouseY);

		if(rectOne.isMouseIn(mouseX, mouseY))
			this.drawHoveringText(graphics, java.util.List.of("Acknowledge"), mouseX, mouseY);

		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {

		if(button == 0) {
			int mx = (int) x, my = (int) y;

			if(rectOne.isMouseIn(mx, my)) {
				if(scrammed()) {
					playClick(1);
					CompoundTag data = new CompoundTag();
					data.putBoolean(LeafiaRBMKSeams.KEY_ACKNOWLEDGE, false);
					PacketDispatcher.sendControlToServer(rod.getBlockPos(), data);
				} else {
					playDenied();
				}
			}

			if(rectAll.isMouseIn(mx, my)) {
				playClick(1);
				CompoundTag data = new CompoundTag();
				data.putBoolean(LeafiaRBMKSeams.KEY_ACKNOWLEDGE, true);
				PacketDispatcher.sendControlToServer(rod.getBlockPos(), data);
			}

			if(checkClick(x, y, 28 - offsA, 70, 30, 10)) {

				click();
				CompoundTag data = new CompoundTag();
				double[] vals = new double[] { 0D, 0D, 0D, 0D };

				for(int k = 0; k < 4; k++) {

					double clamp = k < 2 ? 100 : 9999;

					try {
						int j = (int) Mth.clamp(Double.parseDouble(fields[k].getValue()), 0, clamp);
						fields[k].setValue(String.valueOf(j));
						vals[k] = j;
					} catch(NumberFormatException ex) {
						fields[k].setValue("0");
					}
				}

				data.putDouble("levelUpper", vals[0]);
				data.putDouble("levelLower", vals[1]);
				data.putDouble("heatUpper", vals[2]);
				data.putDouble("heatLower", vals[3]);

				PacketDispatcher.sendControlToServer(rod.getBlockPos(), data);
			}

			for(int k = 0; k < 3; k++) {

				//leafia: fucking lies that's function control
				if(checkClick(x, y, 61 - offsA, 48 + k * 11, 22, 10)) {

					click();
					CompoundTag data = new CompoundTag();
					data.putInt("function", k);
					PacketDispatcher.sendControlToServer(rod.getBlockPos(), data);
				}
			}
		}

		return super.mouseClicked(x, y, button);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(scrammed() && System.currentTimeMillis() % 500L < 250L)
			graphics.blit(texture, leftPos + 144, topPos + 26, 0, 186, 24, 25);

		int height = (int) (56 * (1D - rod.rodLevel));

		if(height > 0)
			graphics.blit(texture, leftPos + 124 - offsB, topPos + 29, 176, 56 - height, 8, height);

		int f = rod.function.ordinal();
		graphics.blit(texture, leftPos + 59 - offsA, topPos + 27, 184, f * 19, 26, 19);

		if(rod.isPowered()) {
			graphics.blit(texture, leftPos + 136, topPos + 21, 210, rod.hasPower ? 16 : 0, 16, 16);
		}
	}

	/** The base screen's fix: typing in a field must not close the screen on the inventory key (D-1250). */
	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if(this.getFocused() instanceof EditBox box && box.canConsumeInput() && key != 256) {
			if(box.keyPressed(key, scan, modifiers)) return true;
			if(key == 258) return super.keyPressed(key, scan, modifiers);
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}
}
