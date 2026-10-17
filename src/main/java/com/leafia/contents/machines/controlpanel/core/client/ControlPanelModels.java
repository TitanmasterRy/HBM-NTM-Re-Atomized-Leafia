package com.leafia.contents.machines.controlpanel.core.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.ControlPanelParts;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * The control panel's baked parts, by name, and their registration for baking (D-3065).
 *
 * <p>A model is only baked if something references it, and these are drawn by the block entity renderer rather than chosen
 * by a blockstate, so nothing does. {@link ModelEvent.RegisterAdditional} is how the mod says "bake this anyway"; a part that
 * is drawn and not registered bakes to the missing-model cube, which looks like a broken renderer. The registration walks
 * {@link ControlPanelParts}, the same table the generator's output is tested against, so adding an instrument's parts there
 * registers them here.
 *
 * <p>NTM-CE: {@code ResourceManager}'s thirteen {@code WaveFrontObjectVAO} fields and {@code renderPart(name)}.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ControlPanelModels {

	private ControlPanelModels() {}

	private static final Map<String, ModelResourceLocation> CACHE = new ConcurrentHashMap<>();

	/** The upright front's slab, drawn whole. */
	public static final ModelResourceLocation PANEL_FRONT = standalone(ControlPanelParts.PANEL_FRONT);

	/** The baked model of one part of one control model, e.g. {@code part("button_push", "btn_top")}. */
	public static ModelResourceLocation part(String model, String part) {
		return CACHE.computeIfAbsent(model + "/" + part, k -> standalone(ControlPanelParts.modelName(model, part)));
	}

	private static ModelResourceLocation standalone(String name) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/" + name));
	}

	@SubscribeEvent
	public static void registerExtraModels(ModelEvent.RegisterAdditional event) {
		if(!LeafiaFeatures.controlPanel)
			return;
		event.register(PANEL_FRONT);
		for(Map.Entry<String, List<String>> e : ControlPanelParts.all().entrySet())
			for(String part : e.getValue())
				event.register(part(e.getKey(), part));
	}
}
