package com.leafia.client;

import org.joml.Vector3f;

import com.leafia.contents.fluids.ConcreteFluid;
import com.leafia.contents.fluids.FluorideFluid;
import com.leafia.contents.fluids.LOXFluid;
import com.leafia.contents.fluids.LeafiaLiquids;
import com.leafia.contents.fluids.LeafiaLiquids.Liquid;
import com.leafia.contents.fluids.OsmiridiumFluid;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * How the addon's four world fluids look: their textures, the colour of the fog when the camera is
 * inside one, and how far you can see.
 *
 * <p>Upstream drew these with {@code forge:fluid} blockstates naming a fluid, plus two overrides on
 * the block: {@code getFogColor} (every one but concrete and the core component) and
 * {@code IFluidFog} (those two), which {@code FluidFogHandler} applied as an exponential fog. In
 * 1.21.1 both are on the fluid's client extension.
 *
 * <p><b>Fog distance.</b> 1.12.2 was exponential fog by density, 1.21.1 is linear between a start
 * and an end, and the two cannot be matched exactly. Concrete and the core component had density
 * 2.0, which is what 1.12.2 used for <em>lava</em>, so they take lava's distance: fully fogged
 * from one block (starting at a quarter of one). Fluoride is {@code Material.LAVA} and so had
 * that density without asking for it. Oxygen is {@code Material.WATER}, whose fog was a density
 * of 0.1 - roughly vanilla's underwater distance with no water vision yet, 24 blocks.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaLiquidClient {

	private LeafiaLiquidClient() {}

	/** Vanilla lava's linear fog, which is what density 2.0 meant. */
	private static final float LAVA_NEAR = 0.25F, LAVA_FAR = 1.0F;
	/** Vanilla's underwater fog with no water vision: -8 to 96 x 0.25. */
	private static final float WATER_NEAR = -8.0F, WATER_FAR = 24.0F;

	private static final ResourceLocation UNDERWATER = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

	private static ResourceLocation tex(String name) {
		return LeafiaRef.rl("block/forgefluid/" + name);
	}

	@SubscribeEvent
	public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
		register(event, LeafiaLiquids.lox, new Extension(tex("lox_still"), tex("lox_flow"), UNDERWATER,
				new Vector3f(LOXFluid.FOG[0], LOXFluid.FOG[1], LOXFluid.FOG[2]), WATER_NEAR, WATER_FAR));
		register(event, LeafiaLiquids.fluoride, new Extension(tex("fluoride_still"), tex("fluoride_flow"), null,
				new Vector3f(FluorideFluid.FOG[0], FluorideFluid.FOG[1], FluorideFluid.FOG[2]), LAVA_NEAR, LAVA_FAR));
		// Upstream's still texture for concrete is concrete_static, not concrete_still.
		register(event, LeafiaLiquids.concrete, new Extension(tex("concrete_static"), tex("concrete_flow"), null,
				rgb(ConcreteFluid.FOG_COLOR), LAVA_NEAR, LAVA_FAR));
		register(event, LeafiaLiquids.osmiridium, new Extension(tex("osmiridium_still"), tex("osmiridium_flow"), null,
				rgb(OsmiridiumFluid.FOG_COLOR), LAVA_NEAR, LAVA_FAR));
		// Balefire corium is a CoriumFinite, a block with no fluid since D-2173, so it has no extension:
		// its blockstate draws it, and with no fluid type there is no camera fog (D-3052).
	}

	private static void register(RegisterClientExtensionsEvent event, Liquid liquid, IClientFluidTypeExtensions ext) {
		if(liquid != null) event.registerFluidType(ext, liquid.type.get());
	}

	/** {@code IFluidFog.getFogColor}'s packed int, as the 0-1 triple the fog wants. */
	public static Vector3f rgb(int color) {
		return new Vector3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
	}

	private static final class Extension implements IClientFluidTypeExtensions {

		private final ResourceLocation still, flowing, overlay;
		private final Vector3f fog;
		private final float near, far;

		Extension(ResourceLocation still, ResourceLocation flowing, ResourceLocation overlay, Vector3f fog, float near, float far) {
			this.still = still;
			this.flowing = flowing;
			this.overlay = overlay;
			this.fog = fog;
			this.near = near;
			this.far = far;
		}

		@Override
		public ResourceLocation getStillTexture() {
			return still;
		}

		@Override
		public ResourceLocation getFlowingTexture() {
			return flowing;
		}

		@Override
		public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
			return overlay;
		}

		@Override
		public Vector3f modifyFogColor(Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level,
				int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
			return new Vector3f(fog);
		}

		@Override
		public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick,
				float nearDistance, float farDistance, com.mojang.blaze3d.shaders.FogShape shape) {
			if(mode != FogRenderer.FogMode.FOG_TERRAIN) return;
			RenderSystem.setShaderFogStart(near);
			RenderSystem.setShaderFogEnd(Math.min(far, renderDistance));
		}
	}
}
