package com.leafia.contents.gear.combat.shields;

import java.io.IOException;
import java.io.InputStream;

import com.leafia.contents.machines.powercores.dfc.render.DFCObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the addon's shields: the four that wear vanilla's shield shape (steel, titanium, the SWAT shield and
 * plutonium-238) always, and fissite and mysticite when they wear a banner.
 *
 * <p>Upstream: {@code com.leafia.contents.gear.combat.shields.AddonShieldRender}, a
 * {@code TileEntityItemStackRenderer} that binds the shield's 64x64 skin, flips the model with
 * {@code scale(1, -1, -1)} and calls {@code ModelShield.render()} - or, for fissite and mysticite, draws
 * {@code shield_kms.obj}. 1.21.1's counterpart is a {@link BlockEntityWithoutLevelRenderer} hung on the item through
 * {@code IClientItemExtensions}, and the plain draw is vanilla's own for {@code Items.SHIELD}, in the same frame and with
 * the same flip: the model, a skin, the item's foil.
 *
 * <h2>The banner (D-3031)</h2>
 *
 * Upstream's "design" branch: a stack carrying a banner ({@code BlockEntityTag} there, {@code BASE_COLOR} and
 * {@code BANNER_PATTERNS} here, which {@code AddonShieldCraftingRecipe} copies on) is drawn with a texture composed of
 * the shield's <em>blank</em> skin with the banner's layers laid over it ({@code BannerTextures.Cache}). 1.21.1 draws a
 * banner as layers instead of composing a texture, which comes to the same picture: the blank skin, then the base colour
 * through vanilla's shield {@code base} mask, then each pattern (sixteen at most, as vanilla), each layer an
 * {@code entityNoOutline} pass over the same faces, in the dye's colour, from the shield-pattern atlas - vanilla's
 * {@code BannerRenderer.renderPatterns} with the flag drawn on a plain texture rather than an atlas sprite.
 * <ul>
 * <li>The vanilla-shaped four lay the layers over the plate, as vanilla does, and draw the handle on the blank too.
 *     Upstream's blanks: {@code steel_shield_blank}, {@code titanium_shield_blank}, {@code elec_shield_blank}, and the
 *     skin itself for plutonium-238 ({@link AddonShieldItem.Kind#blank}).</li>
 * <li>Fissite and mysticite draw {@code shield_kms.obj} and lay every layer over all of its faces in their own texture
 *     coordinates - which is exactly where upstream's composed texture put them, since the composition is in texture
 *     space whatever the model. Their blank is their skin. Plain, they stay the baked OBJ item model (D-1083); the
 *     {@code leafia:decorated} item property sends a decorated one here instead.</li>
 * </ul>
 *
 * <p>The skins are standalone textures (under {@code textures/entity/shield/}, the KMS pair's under
 * {@code textures/item/}): this draws them on a plain {@link ResourceLocation}, the way vanilla draws its unpatterned
 * shield, so nothing needs stitching.
 */
@OnlyIn(Dist.CLIENT)
public class AddonShieldRender extends BlockEntityWithoutLevelRenderer {

	/** The KMS shield's mesh, the baked item model's own file (already flipped, so drawn as it is). */
	public static final String KMS_OBJ = "/assets/leafia/models/blocks/shield_kms.obj";

	private ShieldModel model;
	private static Mesh kms;

	public AddonShieldRender() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		EntityModelSet models = Minecraft.getInstance().getEntityModels();
		model = new ShieldModel(models.bakeLayer(ModelLayers.SHIELD));
	}

	private static synchronized Mesh kms() {
		if(kms == null) {
			try(InputStream in = AddonShieldRender.class.getResourceAsStream(KMS_OBJ)) {
				if(in == null) throw new IllegalStateException("missing " + KMS_OBJ);
				kms = DFCObj.whole(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + KMS_OBJ, e);
			}
		}
		return kms;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
			MultiBufferSource buffer, int packedLight, int packedOverlay) {

		if(!(stack.getItem() instanceof AddonShieldItem shield)) return;

		// Built on first use if the reload listener has not run yet.
		if(model == null) onResourceManagerReload(null);

		DyeColor base = stack.get(DataComponents.BASE_COLOR);
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);

		if(shield.kind().charges()) {
			renderKms(shield.kind(), base, patterns, stack.hasFoil(), pose, buffer, packedLight, packedOverlay);
			return;
		}

		pose.pushPose();
		pose.scale(1F, -1F, -1F);
		if(base == null) {
			VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer,
					model.renderType(shield.kind().skin()), true, stack.hasFoil());
			model.renderToBuffer(pose, consumer, packedLight, packedOverlay, -1);
		} else {
			ResourceLocation blank = shield.kind().blank();
			model.handle().render(pose, ItemRenderer.getFoilBufferDirect(buffer, model.renderType(blank), true, stack.hasFoil()), packedLight, packedOverlay);
			ModelPart plate = model.plate();
			plate.render(pose, ItemRenderer.getFoilBufferDirect(buffer, RenderType.entitySolid(blank), true, stack.hasFoil()), packedLight, packedOverlay);
			Faces faces = (p, c, color) -> plate.render(p, c, packedLight, packedOverlay, color);
			layer(pose, buffer, Sheets.SHIELD_BASE, base, faces);
			for(int i = 0; i < 16 && i < patterns.layers().size(); i++) {
				BannerPatternLayers.Layer l = patterns.layers().get(i);
				layer(pose, buffer, Sheets.getShieldMaterial(l.pattern()), l.color(), faces);
			}
		}
		pose.popPose();
	}

	@FunctionalInterface
	private interface Faces {
		void draw(PoseStack pose, VertexConsumer consumer, int color);
	}

	/** One banner layer: vanilla's {@code renderPatternLayer}, over whatever faces it is handed. */
	private static void layer(PoseStack pose, MultiBufferSource buffer, Material material, DyeColor color, Faces faces) {
		faces.draw(pose, material.buffer(buffer, RenderType::entityNoOutline), color.getTextureDiffuseColor());
	}

	/** Fissite or mysticite: the KMS mesh on its skin, then the banner's layers over every face, if it wears one. */
	private static void renderKms(AddonShieldItem.Kind kind, DyeColor base, BannerPatternLayers patterns, boolean foil, PoseStack pose,
			MultiBufferSource buffer, int light, int overlay) {
		Mesh mesh = kms();
		mesh(pose, ItemRenderer.getFoilBufferDirect(buffer, RenderType.entityCutoutNoCull(kind.blank()), true, foil), mesh, light, overlay, -1);
		if(base == null) return;
		Faces faces = (p, c, color) -> mesh(p, c, mesh, light, overlay, color);
		layer(pose, buffer, Sheets.SHIELD_BASE, base, faces);
		for(int i = 0; i < 16 && i < patterns.layers().size(); i++) {
			BannerPatternLayers.Layer l = patterns.layers().get(i);
			layer(pose, buffer, Sheets.getShieldMaterial(l.pattern()), l.color(), faces);
		}
	}

	/** Every face of a mesh in one colour ({@code -1} is white), in the entity vertex format. */
	private static void mesh(PoseStack pose, VertexConsumer consumer, Mesh mesh, int light, int overlay, int argb) {
		PoseStack.Pose last = pose.last();
		float a = (argb >>> 24 & 0xFF) / 255F, r = (argb >> 16 & 0xFF) / 255F, g = (argb >> 8 & 0xFF) / 255F, b = (argb & 0xFF) / 255F;
		float[] d = mesh.data();
		for(int i = 0; i < d.length; i += 8) {
			consumer.addVertex(last, d[i], d[i + 1], d[i + 2])
					.setColor(r, g, b, a)
					.setUv(d[i + 3], d[i + 4])
					.setOverlay(overlay)
					.setLight(light)
					.setNormal(last, d[i + 5], d[i + 6], d[i + 7]);
		}
	}
}
