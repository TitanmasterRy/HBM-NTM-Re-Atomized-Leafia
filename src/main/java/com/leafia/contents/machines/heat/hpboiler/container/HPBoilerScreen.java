package com.leafia.contents.machines.heat.hpboiler.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.hbm.inventory.gui.element.GUIElements;
import com.leafia.contents.machines.heat.hpboiler.HPBoilerTE;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The high-pressure boiler's screen: two tanks, a heat bar, and the one number that matters.
 *
 * <p>Upstream: {@code .../hpboiler/container/HPBoilerGUI.java}.
 *
 * <h2>The addon's first screen on the base mod's GUI layer</h2>
 *
 * Upstream extends {@code LCEGuiInfoContainer} from {@code com/leafia/dev/gui}, four files that
 * have sat in the deferred table since D-1011. They are not needed: every one of the three
 * things this screen actually calls into that base for — a tooltip over a rectangle, a tank
 * drawn from the bottom up, and a tank's hover text — is already on the base mod's
 * {@link GuiInfoContainer} and {@link GUIElements}, which the base mod's own machine screens
 * use. So this extends those directly, and {@code dev/gui} stays deferred with <b>one fewer
 * consumer</b> rather than being ported ahead of a need.
 *
 * <p>Two things did have to be written rather than found. {@code FiaUIRect} is a rectangle with
 * an {@code isMouseIn} — three lines, inlined as {@link #overButton}. And upstream's
 * {@code playClick}/{@code playDenied} are {@code LCEGuiInfoContainer} helpers over 1.12.2's
 * sound handler; vanilla's own UI click and the villager "no" are the nearest equivalents and
 * are what a player expects from a 1.21.1 screen.
 *
 * <h2>A latent divide, preserved with its precondition pinned</h2>
 *
 * The heat bar is upstream's {@code entity.heat * 16 / entity.heatReq}, and {@code heatReq} is
 * zero for a fluid with no heating step at all. It cannot be zero in practice — the block
 * entity's constructor boils water, water is heatable, and {@code setFluid} refuses to rebuild
 * for a fluid that does not boil, so the last good value survives. That is a precondition rather
 * than a guarantee, so it is pinned by {@code LeafiaHPBoilerTests.waterGivesTheBoilerARealHeatCost}
 * instead of being papered over with a guard upstream does not have (D-021).
 */
@OnlyIn(Dist.CLIENT)
public class HPBoilerScreen extends GuiInfoContainer<HPBoilerMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/hp_boiler.png");

	/** Upstream's save button, at (88, 52) and 18 by 18. */
	private static final int BUTTON_X = 88;
	private static final int BUTTON_Y = 52;
	private static final int BUTTON_SIZE = 18;

	private final HPBoilerTE boiler;

	private EditBox field;

	/** Ticks left of the button's pressed highlight. Upstream's twenty. */
	private int saveButtonCooldown = 0;

	public HPBoilerScreen(HPBoilerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.boiler = menu.boiler;
		this.imageWidth = 176;
		this.imageHeight = 185;
		// Upstream's ySize is 185 rather than the usual 166, so the inventory label has to move
		// with it or it lands on top of the hotbar.
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	@Override
	protected void init() {

		super.init();

		field = new EditBox(this.font, leftPos + 72 + 9, topPos + 36 + 4, 24, 10, Component.empty());
		field.setTextColor(0x5BBC00);
		field.setTextColorUneditable(0x499500);
		field.setBordered(false);
		field.setMaxLength(2);
		field.setValue(Integer.toString(boiler == null ? 1 : boiler.compression));

		addRenderableWidget(field);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		if(boiler != null) {
			GUIElements.drawFluidTankInfo(graphics, this, boiler.input, mouseX, mouseY,
					leftPos + 44, topPos + 17, 16, 52, hasShiftDown());
			GUIElements.drawFluidTankInfo(graphics, this, boiler.output, mouseX, mouseY,
					leftPos + 116, topPos + 17, 16, 52, hasShiftDown());
			drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 84, topPos + 16, 8, 18,
					mouseX, mouseY, boiler.heat + " TU");
		}

		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {

		boolean handled = super.mouseClicked(x, y, button);

		if(button == 0 && boiler != null && overButton(x, y) && saveButtonCooldown <= 0) {
			try {
				int value = Integer.parseInt(field.getValue());

				if(value <= 0) {
					playDenied();
					return true;
				}

				LeafiaPacket._start(boiler).__write(0, value).__sendToServer();
				playClick();
				saveButtonCooldown = 20;

			} catch(NumberFormatException ignored) {
				playDenied();
			}
			return true;
		}

		return handled;
	}

	/** Upstream's {@code FiaUIRect.isMouseIn}, which is all that class was. */
	private boolean overButton(double x, double y) {
		return x >= leftPos + BUTTON_X && x < leftPos + BUTTON_X + BUTTON_SIZE
				&& y >= topPos + BUTTON_Y && y < topPos + BUTTON_Y + BUTTON_SIZE;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		if(boiler == null) return;

		// See the class doc: heatReq is never zero in practice, and the test says why.
		int heatpx = Math.min(boiler.heat * 16 / boiler.heatReq, 16);
		graphics.blit(TEXTURE, leftPos + 85, topPos + 17 + 16 - heatpx, 194, 14 + 16 - heatpx, 6, heatpx);

		if(saveButtonCooldown > 0) {
			graphics.blit(TEXTURE, leftPos + BUTTON_X, topPos + BUTTON_Y, 176, 14, BUTTON_SIZE, BUTTON_SIZE);
			saveButtonCooldown--;
		}

		if(field.isFocused())
			graphics.blit(TEXTURE, leftPos + 72, topPos + 36, 176, 0, 32, 14);

		GUIElements.drawFluidTank(graphics, boiler.input, leftPos + 44, topPos + 17 + 52, 16, 52);
		GUIElements.drawFluidTank(graphics, boiler.output, leftPos + 116, topPos + 17 + 52, 16, 52);
	}

	private static void playClick() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
	}

	private static void playDenied() {
		Minecraft.getInstance().getSoundManager()
				.play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
	}
}
