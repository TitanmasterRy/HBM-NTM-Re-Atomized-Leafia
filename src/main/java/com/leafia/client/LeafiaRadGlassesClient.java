package com.leafia.client;

import com.leafia.contents.gear.utility.radglasses.RadGlassesModel;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Hangs {@link RadGlassesModel} on every renderer that draws a biped (D-3026): the player skins and every living entity type whose
 * model is a {@code HumanoidModel}. The layer draws only for a wearer, so the others cost one slot lookup.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaRadGlassesClient {

	private LeafiaRadGlassesClient() {}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@SubscribeEvent
	public static void addLayers(EntityRenderersEvent.AddLayers event) {
		for(var skin : event.getSkins()) {
			if(event.getSkin(skin) instanceof LivingEntityRenderer renderer && renderer.getModel() instanceof HumanoidModel)
				renderer.addLayer(new RadGlassesModel(renderer));
		}
		for(EntityType<?> type : event.getEntityTypes()) {
			if(event.getRenderer((EntityType) type) instanceof LivingEntityRenderer renderer && renderer.getModel() instanceof HumanoidModel)
				renderer.addLayer(new RadGlassesModel(renderer));
		}
	}
}
