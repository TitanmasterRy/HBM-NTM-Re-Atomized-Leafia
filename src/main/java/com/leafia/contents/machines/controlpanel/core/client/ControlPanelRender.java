package com.leafia.contents.machines.controlpanel.core.client;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.hbm.render.util.ObjUtil;
import com.leafia.LeafiaMod;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanel;
import com.leafia.contents.machines.controlpanel.core.ControlPanelSlab;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelType;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a Custom Control Panel: the slab and every control on it. Adapted from NTM-CE's {@code RenderControlPanel}
 * (D-3065), with Leafia's two render mixins folded in.
 *
 * <h2>Three passes</h2>
 * Leafia's {@code MixinRrenderControlPanel} {@code @Overwrite}s CE's {@code render} so that a control set <i>into</i> the panel
 * (its {@code NixieDisplay}) is drawn before the slab and the rest after it: the first pass is the controls registered with
 * {@link ControlRenderers#registerInset}, then the slab, then every other control. CE's own order was slab then controls. With no
 * inset control registered (batch 12 registers one) the first pass draws nothing and the result is CE's.
 *
 * <h2>The slab</h2>
 * The desk panel's six quads come from {@link ControlPanelSlab}, in block space; the front panel is its OBJ, turned by the
 * facing. Both wear the panel texture, or - Leafia's {@code MixinRenderControlPanel} - the skin: the sprite of the first quad
 * the skin block's model draws on its north face, else the model's particle sprite. The skin is only the surface: the shape,
 * and the controls on top, are the panel's own.
 *
 * <h2>The controls</h2>
 * The pose is multiplied by the panel's transform ({@code ControlPanel.transform}, which {@code ControlPanelTE} keeps for its
 * facing and placement) and each control is drawn at its position, moved by the offset CE gave a control larger than one unit,
 * by the renderer {@link ControlRenderers} holds for its class. A renderer that throws is logged once per class and skipped,
 * so one bad control cannot take the frame down.
 *
 * <h2>For the editor</h2>
 * {@link #drawControl} draws one control with the same renderers, for a preview. It is public so the editor screen
 * (batch 6) does not need a second copy of the draw code; see D-3065.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ControlPanelRender implements BlockEntityRenderer<ControlPanelTE> {

	private static final String PANEL_TEXTURE = "leafia:block/ccp/panel";

	private static final Set<Class<?>> FAILED = new HashSet<>();
	private static final Map<Block, TextureAtlasSprite> SKIN_SPRITES = new WeakHashMap<>();
	private static TextureAtlasSprite panelSprite;
	private static final Map<ControlPanelTE, CachedSlab> SLABS = new WeakHashMap<>();

	private static final class CachedSlab {
		final int key;
		final List<ControlPanelSlab.Quad> quads;

		CachedSlab(int key, List<ControlPanelSlab.Quad> quads) {
			this.key = key;
			this.quads = quads;
		}
	}

	public ControlPanelRender(BlockEntityRendererProvider.Context context) {}

	@SubscribeEvent
	public static void onBaked(ModelEvent.BakingCompleted event) {
		SKIN_SPRITES.clear();
		panelSprite = null;
		SLABS.clear();
		ControlDraw.reset();
	}

	@Override
	public void render(ControlPanelTE te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		ControlPanel panel = te.panel;
		if(panel == null || panel.transform == null)
			return;
		BlockState state = te.getBlockState();
		if(!state.hasProperty(ControlPanelBlock.FACING))
			return;

		drawControls(panel, pose, buffers, light, true);

		if(te.panelType == ControlPanelType.FRONT_PANEL)
			renderFrontPanel(te, state, pose, buffers, light);
		else
			renderCustomPanel(te, state, pose, buffers, light);

		drawControls(panel, pose, buffers, light, false);
	}

	// ------------------------------------------------------------------ controls

	private static void drawControls(ControlPanel panel, PoseStack pose, MultiBufferSource buffers, int light, boolean insetPass) {
		pose.pushPose();
		pose.mulPose(panel.transform);
		for(Control control : panel.controls) {
			if(ControlRenderers.isInset(control) != insetPass)
				continue;
			drawControl(control, pose, buffers, light, true);
		}
		pose.popPose();
	}

	/**
	 * Draws one control with its renderer, in the caller's pose (panel units, +y out of the panel). {@code atPosition} moves it to
	 * the control's {@code (posX, posY)}; an editor preview that places the control itself passes false. A control with no
	 * renderer draws nothing.
	 */
	public static void drawControl(Control control, PoseStack pose, MultiBufferSource buffers, int light, boolean atPosition) {
		ControlRenderers.ControlRenderer<Control> renderer = ControlRenderers.find(control);
		if(renderer == null)
			return;
		float width = control.getSize()[0];
		float length = control.getSize()[1];
		pose.pushPose();
		// CE: a control larger than one unit is moved toward the middle of its cell, a smaller one is not centred.
		pose.translate(width > 1 ? Math.abs(1 - width) / 2 : (width - 1) / 2, 0, length > 1 ? Math.abs(1 - length) / 2 : (length - 1) / 2);
		if(atPosition)
			pose.translate(control.posX, 0, control.posY);
		try {
			renderer.render(control, new ControlDraw(pose, buffers, light));
		} catch(RuntimeException e) {
			if(FAILED.add(control.getClass()))
				LeafiaMod.LOGGER.error("Control panel renderer for {} threw and will be skipped for it from now on", control.getClass().getName(), e);
		} finally {
			pose.popPose();
		}
	}

	// ------------------------------------------------------------------ the slab

	private static TextureAtlasSprite panelSprite() {
		if(panelSprite == null)
			panelSprite = ControlDraw.sprite(PANEL_TEXTURE);
		return panelSprite;
	}

	/** Leafia's skin: the sprite of the skin block's north face, or its particle sprite. Null for no skin. */
	private static TextureAtlasSprite skinSprite(Block skin) {
		if(skin == null)
			return null;
		return SKIN_SPRITES.computeIfAbsent(skin, b -> {
			try {
				BlockState display = b.defaultBlockState();
				BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(display);
				List<net.minecraft.client.renderer.block.model.BakedQuad> quads = baked.getQuads(display, Direction.NORTH, RandomSource.create());
				if(!quads.isEmpty())
					return quads.get(0).getSprite();
				return baked.getParticleIcon(ModelData.EMPTY);
			} catch(RuntimeException e) {
				return panelSprite();
			}
		});
	}

	private void renderCustomPanel(ControlPanelTE te, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
		ControlPanel panel = te.panel;
		boolean up = state.getValue(ControlPanelBlock.UP);
		boolean down = state.getValue(ControlPanelBlock.DOWN);
		Direction facing = state.getValue(ControlPanelBlock.FACING);

		int key = java.util.Objects.hash(panel.height, panel.angle, panel.a_off, panel.b_off, panel.c_off, panel.d_off, up, down, facing);
		CachedSlab cached = SLABS.get(te);
		if(cached == null || cached.key != key) {
			cached = new CachedSlab(key, ControlPanelSlab.build(panel, up, down, facing));
			SLABS.put(te, cached);
		}
		if(cached.quads.isEmpty())
			return;

		TextureAtlasSprite sprite = skinSprite(te.getSkin());
		if(sprite == null)
			sprite = panelSprite();
		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		PoseStack.Pose last = pose.last();
		for(ControlPanelSlab.Quad q : cached.quads) {
			for(int k = 0; k < 4; k++) {
				float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * q.uv()[k * 2];
				float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * q.uv()[k * 2 + 1];
				consumer.addVertex(last, q.pos()[k * 3], q.pos()[k * 3 + 1], q.pos()[k * 3 + 2])
						.setColor(1F, 1F, 1F, 1F)
						.setUv(u, v)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(last, q.normal()[0], q.normal()[1], q.normal()[2]);
			}
		}
	}

	/**
	 * The upright front: CE turned it by the block's metadata (2: 90, 4: 180, 3: 270, 5: 0). The metadata numbers are the
	 * facing's data values, so north is 90, west 180, south 270 and east 0 - the same table {@code ControlPanelTE.updateTransform}
	 * turns the controls by.
	 */
	private void renderFrontPanel(ControlPanelTE te, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
		float degrees = switch(state.getValue(ControlPanelBlock.FACING)) {
			case NORTH -> 90F;
			case WEST -> 180F;
			case SOUTH -> 270F;
			default -> 0F;
		};
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(degrees));

		BakedModel model = ObjUtil.getModel(ControlPanelModels.PANEL_FRONT);
		VertexConsumer consumer = buffers.getBuffer(Sheets.cutoutBlockSheet());
		TextureAtlasSprite skin = skinSprite(te.getSkin());
		if(skin == null)
			ObjUtil.renderModel(model, pose, consumer, light, OverlayTexture.NO_OVERLAY);
		else
			renderOnSprite(model, pose, consumer, light, ControlDraw.sprite("leafia:block/ccp/panel_front"), skin);
		pose.popPose();
	}

	/**
	 * A baked model's quads re-mapped from one atlas sprite to another: each UV is taken back out of {@code from} to its own 0..1
	 * and put into {@code to}. Leafia's skin on an OBJ slab, where it swapped the bound texture.
	 */
	static void renderOnSprite(BakedModel model, PoseStack pose, VertexConsumer consumer, int light, TextureAtlasSprite from, TextureAtlasSprite to) {
		PoseStack.Pose last = pose.last();
		RandomSource random = RandomSource.create();
		for(var quad : model.getQuads(null, null, random)) {
			int[] data = quad.getVertices();
			int stride = data.length / 4;
			for(int k = 0; k < 4; k++) {
				int o = k * stride;
				float x = Float.intBitsToFloat(data[o]), y = Float.intBitsToFloat(data[o + 1]), z = Float.intBitsToFloat(data[o + 2]);
				float u = (Float.intBitsToFloat(data[o + 4]) - from.getU0()) / (from.getU1() - from.getU0());
				float v = (Float.intBitsToFloat(data[o + 5]) - from.getV0()) / (from.getV1() - from.getV0());
				int normal = data[o + 7];
				consumer.addVertex(last, x, y, z)
						.setColor(1F, 1F, 1F, 1F)
						.setUv(to.getU0() + (to.getU1() - to.getU0()) * u, to.getV0() + (to.getV1() - to.getV0()) * v)
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light)
						.setNormal(last, ((byte) normal) / 127F, ((byte) (normal >> 8)) / 127F, ((byte) (normal >> 16)) / 127F);
			}
		}
	}
}
