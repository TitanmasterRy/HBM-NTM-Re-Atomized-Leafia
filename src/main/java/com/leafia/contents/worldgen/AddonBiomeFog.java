package com.leafia.contents.worldgen;

import java.util.Map;

import com.leafia.lib.LeafiaRef;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The addon biomes' fog. Not an upstream file: upstream's {@code LeafiaClientListener.HandlerClient}
 * {@code overrideFog} and {@code setFogColor}, the two handlers that read {@link AddonBiome}'s fog
 * methods, lifted out of that 1,200-line listener (which is not ported) with the class they serve.
 *
 * <p>Both blend over {@link AddonBiome#getBiomeRatios} around the camera's entity, so walking into a
 * crater closes the fog in over about fifteen blocks rather than at a line. The distances are
 * {@link ViewportEvent.RenderFog}'s near and far planes, which is 1.21.1's linear fog (1.12.2's fog start
 * and end); a density has nothing to act on and no addon biome changes it. Upstream's handlers also folded
 * in the meteor-impact dust ({@code ImpactWorldHandler}), which belongs to the impact system, not ported;
 * that term is not here.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AddonBiomeFog {

	private AddonBiomeFog() {}

	@SubscribeEvent
	public static void onRenderFog(ViewportEvent.RenderFog event) {
		Entity entity = event.getCamera().getEntity();
		if(entity == null || AddonBiomes.ALL_BIOMES.isEmpty()) return;
		Map<AddonBiome, Float> ratios = AddonBiome.getBiomeRatios(entity.level(), entity.blockPosition());
		if(ratios.isEmpty()) return;
		float near = event.getNearPlaneDistance();
		float far = event.getFarPlaneDistance();
		float newNear = AddonBiome.blendFog(ratios, near, 1);
		float newFar = AddonBiome.blendFog(ratios, far, 2);
		if(newNear == near && newFar == far) return;
		event.setNearPlaneDistance(newNear);
		event.setFarPlaneDistance(newFar);
		event.setCanceled(true); // RenderFog only applies its new planes when cancelled
	}

	@SubscribeEvent
	public static void onFogColor(ViewportEvent.ComputeFogColor event) {
		Entity entity = event.getCamera().getEntity();
		if(entity == null || AddonBiomes.ALL_BIOMES.isEmpty()) return;
		Map<AddonBiome, Float> ratios = AddonBiome.getBiomeRatios(entity.level(), entity.blockPosition());
		if(ratios.isEmpty()) return;
		double[] col = AddonBiome.blendFogColor(ratios, event.getRed(), event.getGreen(), event.getBlue());
		event.setRed((float) Mth.clamp(col[0], 0, 1));
		event.setGreen((float) Mth.clamp(col[1], 0, 1));
		event.setBlue((float) Mth.clamp(col[2], 0, 1));
	}
}
