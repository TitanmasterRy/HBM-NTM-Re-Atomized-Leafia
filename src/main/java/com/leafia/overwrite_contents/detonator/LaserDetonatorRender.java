package com.leafia.overwrite_contents.detonator;

import java.util.EnumMap;
import java.util.Map;

import org.joml.Matrix4f;

import com.hbm.render.util.ObjUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The laser detonator held as upstream draws it: the base mod's own {@code detonator_laser.obj}, its red lights, a
 * yellow sine wave on its screen and three random six-digit numbers that change every half second.
 *
 * <p>Upstream: {@code MixinItemRenderLaserDetonator}, an {@code @Overwrite} of CE's {@code ItemRendererDetonatorLaser}
 * that places it by the addon's {@code LeafiaGripOffsetHelper} and adds an aim-down-sights pose, on the OBJ the base mod
 * ships ({@code hbm:models/weapons/detonator_laser.obj}, groups {@code Main} and {@code Lights}). The base mod has drawn
 * the detonator itself since D-2203 and NeoForge takes one item extension per item, so this replaces the base's renderer
 * through {@code ItemRenderOverrides.replaceRenderer} (D-2240, registered in
 * {@code LeafiaLaserDetonatorClient.ModEvents.replaceBaseRenderer}, D-3051) while the overhaul is on.
 *
 * <h2>The placement, upstream's {@code LeafiaGripOffsetHelper}</h2>
 * Every context first turns -90 degrees about Y; a left hand applies the right hand's offset before its own; then the
 * context's offset; then {@code NONE}'s (identity). An offset is {@code scale(s)}, {@code translate(-x, y, z)},
 * {@code rotate(-ry, Y)}, {@code rotate(rx, X)}, {@code rotate(-rz, Z)}. The numbers are upstream's table, the
 * unused defaults (0.25, nothing else) included. While aiming down sights with this hand (the sign of
 * {@link LeafiaLaserDetonator#getViewADS} is 1 for the right, -1 for the left), a first-person hand takes the right
 * hand's offset and then the ADS one ({@code 1.01}, {@code (-3.35, 1, -0.9)}, {@code (-14, 1, -5)}).
 *
 * <p>1.12.2's state juggling (lighting off, culling off, blend on, texture off, lightmap 240) is the render types:
 * the lights and the wave are flat colour at full brightness ({@code debugQuads}), the text full-bright.
 */
@OnlyIn(Dist.CLIENT)
public class LaserDetonatorRender extends BlockEntityWithoutLevelRenderer {

	/** One of upstream's {@code LeafiaGripOffset}s. */
	public record Grip(double scale, double px, double py, double pz, double rx, double ry, double rz) {

		void apply(PoseStack pose) {
			pose.scale((float) scale, (float) scale, (float) scale);
			pose.translate(-px, py, pz);
			pose.mulPose(Axis.YP.rotationDegrees((float) -ry));
			pose.mulPose(Axis.XP.rotationDegrees((float) rx));
			pose.mulPose(Axis.ZP.rotationDegrees((float) -rz));
		}
	}

	public static final Map<ItemDisplayContext, Grip> OFFSETS = new EnumMap<>(ItemDisplayContext.class);
	public static final Grip ADS = new Grip(1.01, -3.35, 1, -0.9, -14, 1, -5);

	static {
		// LeafiaGripOffsetHelper's defaults: 0.25, except NONE, the two left hands and FIXED at 1
		for(ItemDisplayContext ctx : ItemDisplayContext.values()) {
			boolean one = ctx == ItemDisplayContext.NONE || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
					|| ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.FIXED;
			OFFSETS.put(ctx, new Grip(one ? 1 : 0.25, 0, 0, 0, 0, 0, 0));
		}
		Grip gui = new Grip(0.25, -2.25, 1.45, -1.25, -40, 0, 0);
		OFFSETS.put(ItemDisplayContext.GUI, gui);
		OFFSETS.put(ItemDisplayContext.FIXED, gui); // .copySettings(GUI)
		OFFSETS.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new Grip(0.25, -1.30, 1.05, -3.25, 14, 91, 5));
		OFFSETS.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, new Grip(0.25, 1.75, 6.25, -12.75, -5, -5, -10));
		OFFSETS.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, new Grip(0.25, -2.10, 1.60, -1.35, 7, 92, -2));
		OFFSETS.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, new Grip(1, 1.25, 0, 0, 0, -9, 0));
		OFFSETS.put(ItemDisplayContext.GROUND, new Grip(0.25, -2, 0.25, -1.75, 0, 0, 0));
	}

	public LaserDetonatorRender() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	/** Upstream's {@code offsets.apply(type)}. */
	public static void apply(PoseStack pose, ItemDisplayContext type) {
		pose.mulPose(Axis.YP.rotationDegrees(-90f));
		if(type == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) OFFSETS.get(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(pose);
		if(type == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) OFFSETS.get(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).apply(pose);
		OFFSETS.get(type).apply(pose);
		OFFSETS.get(ItemDisplayContext.NONE).apply(pose);
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext type, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();

		switch(type) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
				float ads = Minecraft.getInstance().player == null ? 0 : Math.signum(LeafiaLaserDetonator.getViewADS(Minecraft.getInstance().player));
				if(ads == ((type == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) ? 1 : -1)) {
					apply(pose, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
					ADS.apply(pose);
				} else
					apply(pose, type);
			}
			default -> apply(pose, type);
		}

		ObjUtil.renderModel(ObjUtil.getModel(LeafiaLaserDetonatorClient.MAIN), pose, buffers.getBuffer(Sheets.cutoutBlockSheet()), light, overlay);

		VertexConsumer flat = buffers.getBuffer(RenderType.debugQuads());
		flatModel(ObjUtil.getModel(LeafiaLaserDetonatorClient.LIGHTS), pose, flat, 1F, 0F, 0F);

		final float px = 0.0625F;
		pose.pushPose();
		pose.translate(0.5626F, px * 18, -px * 14);
		Matrix4f m = pose.last().pose();
		int sub = 32;
		double width = px * 8;
		double len = width / sub;
		double time = System.currentTimeMillis() / -100D;
		double amplitude = 0.075;
		for(int i = 0; i < sub; i++) {
			double h0 = Math.sin(i * 0.5 + time) * amplitude;
			double h1 = Math.sin((i + 1) * 0.5 + time) * amplitude;
			// both windings: 1.12.2 drew this with culling off
			quad(flat, m, 0, -px * 0.25 + h1, len * (i + 1), 0, px * 0.25 + h1, len * (i + 1), 0, px * 0.25 + h0, len * i, 0, -px * 0.25 + h0, len * i);
			quad(flat, m, 0, -px * 0.25 + h0, len * i, 0, px * 0.25 + h0, len * i, 0, px * 0.25 + h1, len * (i + 1), 0, -px * 0.25 + h1, len * (i + 1));
		}
		pose.popPose();

		pose.pushPose();
		Font font = Minecraft.getInstance().font;
		float f3 = 0.01F;
		pose.translate(0.5625F, 1.3125F, 0.875F);
		pose.scale(f3, -f3, f3);
		pose.mulPose(Axis.YP.rotationDegrees(90F));
		pose.translate(3F, -2F, 0.2F);
		for(String s : LeafiaLaserDetonatorClient.readout(System.currentTimeMillis())) {
			font.drawInBatch(s, 0, 0, 0xff0000, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
			pose.translate(0F, 12.5F, 0F);
		}
		pose.popPose();

		pose.popPose();
	}

	private static void quad(VertexConsumer c, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1,
			double x2, double y2, double z2, double x3, double y3, double z3) {
		c.addVertex(m, (float) x0, (float) y0, (float) z0).setColor(1F, 1F, 0F, 1F);
		c.addVertex(m, (float) x1, (float) y1, (float) z1).setColor(1F, 1F, 0F, 1F);
		c.addVertex(m, (float) x2, (float) y2, (float) z2).setColor(1F, 1F, 0F, 1F);
		c.addVertex(m, (float) x3, (float) y3, (float) z3).setColor(1F, 1F, 0F, 1F);
	}

	/** A baked model's quads in one flat colour, both windings: the lights with texturing and lighting off. */
	private static void flatModel(BakedModel model, PoseStack pose, VertexConsumer c, float r, float g, float b) {
		Matrix4f m = pose.last().pose();
		for(BakedQuad quad : model.getQuads(null, null, RandomSource.create(0))) {
			int[] v = quad.getVertices();
			int stride = v.length / 4;
			float[][] p = new float[4][3];
			for(int i = 0; i < 4; i++) {
				p[i][0] = Float.intBitsToFloat(v[i * stride]);
				p[i][1] = Float.intBitsToFloat(v[i * stride + 1]);
				p[i][2] = Float.intBitsToFloat(v[i * stride + 2]);
			}
			for(int i = 0; i < 4; i++) c.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(r, g, b, 1F);
			for(int i = 3; i >= 0; i--) c.addVertex(m, p[i][0], p[i][1], p[i][2]).setColor(r, g, b, 1F);
		}
	}
}
