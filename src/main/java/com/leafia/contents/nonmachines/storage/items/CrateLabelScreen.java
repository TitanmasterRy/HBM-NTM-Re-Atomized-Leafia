package com.leafia.contents.nonmachines.storage.items;

import java.util.List;

import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The labelling screen: the crate's face as it will read, four text boxes over it, and the icon slot.
 *
 * <p>Upstream: {@code .../nonmachines/storage/items/CrateLabelGUI.java}, 176 by 185 on
 * {@code crate_labelling.png}. The four boxes are <b>invisible</b> upstream - {@code drawTextBox} is never
 * called - and the screen draws their text itself at half size, centred on each box, over a picture of the
 * crate's own face. Here the same: the {@link LabelBox} paints nothing but the half-size text (the first, the
 * vertical line, in the dark grey of a label; the other three white), a 35% white highlight under the
 * mouse, and - not upstream's - a one-pixel caret on the focused box, because a box with no caret is hard to
 * type in.
 *
 * <p>The first box edits the vertical line for the face that was clicked: upstream's index
 * {@code (1 - horizontalIndex) mod 4}. The middle box holds two characters and the others thirty-two (a
 * text box's default), and every box is trimmed to the width of "handle with care" after each key.
 *
 * <p>Upstream sends the whole label state after every key a box took; here the text is sent on every change.
 * A box in focus takes the keyboard first, so typing {@code e} does not close the screen.
 */
@OnlyIn(Dist.CLIENT)
public class CrateLabelScreen extends AbstractContainerScreen<CrateLabelMenu> {

	private static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/crate_labelling.png");
	/** The widest a line may be, in font pixels - upstream's {@code getStringWidth("handle with care")}. */
	private static final String WIDEST = "handle with care";

	private final LabelBox[] fields = new LabelBox[4];
	private boolean initialising;

	public CrateLabelScreen(CrateLabelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 185;
		this.titleLabelX = 0;
		this.inventoryLabelY = imageHeight - 96 + 2;
	}

	@Override
	protected void init() {
		super.init();
		initialising = true;
		CrateLabels labels = menu.crate() == null ? null : CrateLabels.of(menu.crate());

		fields[0] = box(61, 26 - 5, 54, 5, 32, labels == null ? "" : labels.vertical(CrateLabels.verticalIndex(menu.side())));
		fields[1] = box(61, 16 + (70 - 16) / 2 - 9 - 2, 54, 5, 32, labels == null ? "" : labels.upper());
		fields[3] = box(61, 16 + (70 - 16) / 2 + 9 - 2, 54, 5, 32, labels == null ? "" : labels.lower());
		fields[2] = box(61 + 18, 16 + (70 - 16) / 2 - 2, 54 - 18, 5, CrateLabels.MIDDLE_LENGTH, labels == null ? "" : labels.middle());
		for(LabelBox field : fields) addRenderableWidget(field);
		initialising = false;
	}

	private LabelBox box(int x, int y, int w, int h, int maxLength, String text) {
		LabelBox box = new LabelBox(font, leftPos + x, topPos + y, w, h);
		box.setBordered(false);
		box.setMaxLength(maxLength);
		box.setValue(text);
		box.setResponder(value -> changed(box));
		return box;
	}

	/** Trims a line to the width of "handle with care", then sends all four. */
	private void changed(LabelBox box) {
		if(initialising) return;
		int limit = font.width(WIDEST);
		String value = box.getValue();
		if(font.width(value) > limit) {
			while(font.width(value) > limit) value = value.substring(0, value.length() - 1);
			box.setValue(value); // re-enters, and sends
			return;
		}
		send();
	}

	private void send() {
		if(menu.crate() == null) return;
		// Only the face's own vertical line is edited here; the other three are the crate's as read at open.
		String[] vertical = CrateLabels.of(menu.crate()).verticals();
		vertical[CrateLabels.verticalIndex(menu.side())] = fields[0].getValue();
		LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new CrateLabelPacket(menu.crate().getBlockPos(), vertical,
				fields[1].getValue(), fields[2].getValue(), fields[3].getValue()));
		if(packet != null) packet.__sendToServer();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if(keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			for(LabelBox field : fields)
				if(field.isFocused() && field.keyPressed(keyCode, scanCode, modifiers)) return true;
			for(LabelBox field : fields)
				if(field.isFocused()) return true; // a focused box swallows the inventory key
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 4210752, false);
		graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 4210752, false);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

		// The crate's own face, upstream's quad at (71, 26) 34 by 34.
		if(menu.crate() != null) {
			BlockState state = menu.crate().getBlockState();
			BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
			List<BakedQuad> quads = model.getQuads(state, Direction.NORTH, RandomSource.create(), ModelData.EMPTY, null);
			TextureAtlasSprite sprite = quads.isEmpty() ? model.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
			graphics.blit(leftPos + 71, topPos + 26, 0, 34, 34, sprite);
		}
	}

	/** A text box that draws only its text, half size and centred, as upstream's screen does for its invisible ones. */
	private final class LabelBox extends EditBox {

		LabelBox(net.minecraft.client.gui.Font font, int x, int y, int w, int h) {
			super(font, x, y, w, h, Component.empty());
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

			if(mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= getY() && mouseY < getY() + getHeight())
				graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x59FFFFFF);

			String text = getValue();
			int color = this == fields[0] ? 0x404040 : 0xFFFFFF;
			PoseStack pose = graphics.pose();
			pose.pushPose();
			pose.translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0);
			pose.scale(0.5f, 0.5f, 1f);
			int width = font.width(text);
			graphics.drawString(font, text, -width / 2, -4, color, false);
			if(isFocused() && (System.currentTimeMillis() / 500 & 1) == 0)
				graphics.fill(-width / 2 + font.width(text.substring(0, Math.min(getCursorPosition(), text.length()))), -4,
						-width / 2 + font.width(text.substring(0, Math.min(getCursorPosition(), text.length()))) + 1, 5, color | 0xFF000000);
			pose.popPose();
		}
	}
}
