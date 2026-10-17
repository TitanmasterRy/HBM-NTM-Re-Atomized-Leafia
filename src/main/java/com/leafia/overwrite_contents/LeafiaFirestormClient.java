package com.leafia.overwrite_contents;

import com.hbm.render.blockentity.RenderChemicalPlant;
import com.hbm.render.util.ObjUtil;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaFirestorm.ChemicalPlant;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The burnt-out chemical plant, drawn (D-3025): upstream's {@code MixinRenderChemicalPlant}, which rewrote the whole
 * of {@code RenderChemicalPlant.render} to branch on {@code isDestroyed}, as the base mod's render hook.
 *
 * <p>The wreck is upstream's {@code chem_destroyed.obj} - the plant's base, slider and spinner and the rubble round
 * them, one mesh drawn whole - on the plant's own texture, in the frame the hook is given (the plant's facing,
 * which is where upstream drew it). Upstream turned face culling off for it, and so does the render type here.
 * When the plant is not burnt out the hook answers false and the base mod draws its five parts as it always does,
 * and everything the base would draw afterwards - the fluid scroll - is skipped with them, as it was upstream
 * (the destroyed branch had no fluid pass).
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaFirestormClient {

	private LeafiaFirestormClient() {}

	/** {@code leafia:block/chem_destroyed}, registered with {@code ModelEvent.RegisterAdditional}. */
	public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(LeafiaRef.rl("block/chem_destroyed"));

	private static boolean installed = false;

	public static synchronized void install() {
		if(installed) return;
		installed = true;
		RenderChemicalPlant.addRenderHook(RENDER);
	}

	private static final RenderChemicalPlant.RenderHook RENDER = (plant, partialTick, matrix, buffers, light, overlay) -> {
		if(!new ChemicalPlant(plant).isDestroyed()) return false;
		ObjUtil.renderModel(ObjUtil.getModel(MODEL), matrix, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
				light, OverlayTexture.NO_OVERLAY);
		return true;
	};
}
