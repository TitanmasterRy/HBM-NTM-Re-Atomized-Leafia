package com.leafia.contents.machines.controlpanel.core.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The one render type the panel needs that vanilla's sheets do not give: NTM-CE's {@code glBlendFunc(SRC_ALPHA, ONE)}, the RGB
 * lamp's glow laid over its dark lens (D-3065). The block atlas, additive, colour only (no depth write), so two glows overlap
 * to a brighter one instead of hiding each other.
 */
@OnlyIn(Dist.CLIENT)
public final class ControlPanelRenderTypes {

	private ControlPanelRenderTypes() {}

	/**
	 * The three types the nixie tube's inset needs (CCP-INSTRUMENTS-2, D-3072). NTM-CE's tube is drawn <i>before</i> the panel's slab and
	 * then its {@code ClipPlane} (an invisible quad at the panel's surface, drawn with the alpha test off) writes depth over the tube's
	 * opening, so the slab that follows is not drawn there: a hole shaped like the tube. Block-atlas types that are <b>not</b> in the
	 * buffer source's fixed set, so each is drawn the moment another type is asked for (or by {@link ControlDraw#flush}) and the order
	 * the nixie asks in is the order they reach the screen, ahead of the slab and of every other control, which sit in the sheets'
	 * fixed buffers until the end of the pass: {@link #INSET} for the tube's solid parts, {@link #GLASS} for the tube's alpha-blended
	 * glass, {@link #MASK} for the mask (depth only: it writes no colour).
	 */
	public static final RenderType INSET = RenderType.create("leafia_ccp_inset", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			VertexFormat.Mode.QUADS, 256,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
					.setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	public static final RenderType GLASS = RenderType.create("leafia_ccp_glass", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			VertexFormat.Mode.QUADS, 256,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	public static final RenderType MASK = RenderType.create("leafia_ccp_mask", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			VertexFormat.Mode.QUADS, 256,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
					.setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.setCullState(RenderStateShard.NO_CULL)
					.setWriteMaskState(RenderStateShard.DEPTH_WRITE)
					.createCompositeState(false));

	public static final RenderType GLOW = RenderType.create("leafia_ccp_glow", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
			VertexFormat.Mode.QUADS, 256,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.POSITION_COLOR_TEX_LIGHTMAP_SHADER)
					.setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
					.setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
					.setLightmapState(RenderStateShard.LIGHTMAP)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.createCompositeState(false));
}
