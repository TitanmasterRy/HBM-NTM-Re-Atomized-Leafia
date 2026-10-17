package com.leafia.contents.bomb.missile;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon's missile models (D-3022): one, the neon micro missile the custom nuke missile flies as and is drawn
 * as in the inventory.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/AddonMissileItemRender.java}, which held the texture
 * ({@code textures/models/leafia/missileneonc.png}) and the VAO ({@code models/leafia/missileneon_mini_upscaled.obj})
 * and put a {@code generateStandard} drawer for {@code missile_customnuke} into NTM-CE's
 * {@code ItemRenderMissileGeneric.renderers} map. The base mod's missile item renderer has no such map - it is built
 * from its own table - so the model is a baked {@code neoforge:obj} model of its own, {@link #NEONC}, over the copied
 * OBJ and texture, registered as an additional model by
 * {@code LeafiaClient} and drawn by {@code CustomNukeMissileItemRender} and {@code CustomNukeMissileEntityRender}.
 */
@OnlyIn(Dist.CLIENT)
public final class AddonMissileItemRender {

	private AddonMissileItemRender() {}

	/** Upstream's {@code neonc_mdl} under {@code neonc_tex}. */
	public static final ModelResourceLocation NEONC =
			ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "missile/customnuke"));
}
