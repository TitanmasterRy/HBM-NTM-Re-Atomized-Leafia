package com.leafia.client;

import com.hbm.particle.ParticleCoolingTower;
import com.hbm.particle.ParticleGasFlame;
import com.hbm.particle.helper.HbmParticleRenderTypes;
import com.hbm.render.util.DiamondPronter;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.control.battery.AddonBatteryPackItem;
import com.leafia.contents.control.battery.AddonBatteryPackItemRender;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.LeafiaCosmeticSeams;
import com.leafia.passive.Wind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The client half of the addon's cosmetic overhauls (COSMETIC, D-3023): what upstream did to the base mod's
 * renderers and particles with mixins, as seam consumers, plus the battery packs' item renderer and the hot
 * sellafield's resource pack.
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code MixinDiamondPronter}</td><td>{@link #NUMBERS}, a rating renderer</td><td>cosmeticOverhaul</td></tr>
 * <tr><td>{@code MixinParticleCoolingTower}</td><td>{@link #WIND}, a wind handler</td><td>cosmeticOverhaul</td></tr>
 * <tr><td>{@code MixinParticleGasFlame}</td><td>{@link #GLOW}, a render type provider</td><td>cosmeticOverhaul</td></tr>
 * <tr><td>{@code MixinBlockSellafield}</td><td>{@code resourcepacks/hot_sellafield}, a pack on top</td><td>cosmeticOverhaul, read when the pack repository is built</td></tr>
 * <tr><td>{@code AddonBatteryPackItemRender}</td><td>{@link AddonBatteryPackItemRender}</td><td>batteryPacks</td></tr>
 * </table>
 *
 * Each handler is registered whatever its switch says and reads the switch when it runs. The pack is the
 * exception: a resource pack is chosen when the repository is assembled, so it is added only while the
 * switch is on at launch (the features file is a STARTUP config, read before any pack is found).
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaCosmeticClient {

	private LeafiaCosmeticClient() {}

	/** The pack's location under the jar's resources. */
	public static final ResourceLocation HOT_SELLAFIELD = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "resourcepacks/hot_sellafield");

	@SubscribeEvent
	public static void clientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(LeafiaCosmeticClient::install);
	}

	private static boolean installed = false;

	/** Registers the three handlers, once. */
	public static void install() {
		if(installed) return;
		installed = true;
		DiamondPronter.addRatingRenderer(NUMBERS);
		ParticleCoolingTower.addWindHandler(WIND);
		ParticleGasFlame.addRenderTypeProvider(GLOW);
	}

	@SubscribeEvent
	public static void addPacks(AddPackFindersEvent event) {
		if(!LeafiaFeatures.cosmeticOverhaul) return;
		event.addPackFinders(HOT_SELLAFIELD, PackType.CLIENT_RESOURCES, Component.literal("Leafia's hot sellafield"),
				PackSource.BUILT_IN, true, Pack.Position.TOP);
	}

	@SubscribeEvent
	public static void registerItemExtensions(RegisterClientExtensionsEvent event) {
		if(!LeafiaFeatures.batteryPacks) return;
		event.registerItem(new IClientItemExtensions() {

			private AddonBatteryPackItemRender renderer;

			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				if(renderer == null) renderer = new AddonBatteryPackItemRender();
				return renderer;
			}
		}, LeafiaItems.battery_packs.values().stream().map(holder -> (Item) holder.get()).toArray(Item[]::new));
	}

	// ------------------------------------------------------------------ MixinDiamondPronter

	public static final ResourceLocation NUMBERS_TEXTURE = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/numbers.png");

	private static final float P = 1F / 64F;
	private static final float S = 1F / 139F;
	/** Half a digit's width and height, in the diamond's units. */
	private static final float DIGIT_WIDTH = 11.2F * S;
	private static final float DIGIT_HEIGHT = 14F * S;

	/**
	 * The hazard diamond's ratings in black digits of the addon's own sheet, any number of them (the
	 * base sheet has 0 to 5): upstream's {@code drawNumber}, a digit per ten-power, least significant on
	 * the right, a digit's width apart. Negative ratings are not drawn (upstream: {@code poison >= 0}).
	 */
	public static final DiamondPronter.RatingRenderer NUMBERS = (matrix, buffers, light, oY, oZ, value) -> {
		if(!LeafiaFeatures.cosmeticOverhaul || value < 0) return false;
		drawNumber(matrix, buffers, light, oZ, oY, value);
		return true;
	};

	/** The digits of {@code value}, most significant first as laid out left to right in the diamond's Z. */
	public static void drawNumber(PoseStack matrix, MultiBufferSource buffers, int light, float oX, float oY, int value) {
		VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(NUMBERS_TEXTURE));
		PoseStack.Pose pose = matrix.last();

		int length = digits(value);
		float startOffset = DIGIT_WIDTH * (length - 1);
		for(int i = 0; i < length; i++) {
			int v = value / (int) Math.pow(10, i) % 10;
			int x = v * 5;
			int y = 0;
			float local = -startOffset + i * DIGIT_WIDTH * 2;
			vertex(buffer, pose, light, 0.002F, DIGIT_HEIGHT + oY, -DIGIT_WIDTH + oX + local, (x + 4) * P, y * P);
			vertex(buffer, pose, light, 0.002F, DIGIT_HEIGHT + oY, DIGIT_WIDTH + oX + local, x * P, y * P);
			vertex(buffer, pose, light, 0.002F, -DIGIT_HEIGHT + oY, DIGIT_WIDTH + oX + local, x * P, (y + 5) * P);
			vertex(buffer, pose, light, 0.002F, -DIGIT_HEIGHT + oY, -DIGIT_WIDTH + oX + local, (x + 4) * P, (y + 5) * P);
		}
	}

	public static int digits(int value) {
		return LeafiaCosmeticSeams.digits(value);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z)
				.setColor(0F, 0F, 0F, 1F)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 1F, 0F, 0F);
	}

	// ------------------------------------------------------------------ MixinParticleCoolingTower

	/**
	 * The cooling tower's plume blows downwind: in a dimension with a wind multiplier, every tick of every
	 * puff - including one sent with {@code noWind}, since upstream replaced the field the flag was read
	 * from - is pushed {@code 0.0025 x (power / 100)^0.65 x multiplier} along the wind's compass angle
	 * (+Z at 0 degrees, -X at 90). In a dimension with none the puff gets no wind at all, where the base
	 * mod's drifts a little east and north.
	 */
	public static final ParticleCoolingTower.WindHandler WIND = (particle, ageScale) -> {
		if(!LeafiaFeatures.cosmeticOverhaul) return false;
		double multiplier = Wind.dimensionWindMultiplier.getOrDefault(particle.getLevel().dimension(), 0D);
		double[] push = LeafiaCosmeticSeams.windPush(Wind.power, Wind.angle, multiplier);
		particle.push(push[0], push[1], push[2]);
		return true;
	};

	// ------------------------------------------------------------------ MixinParticleGasFlame

	private static ParticleRenderType glow;

	/** The gas flame under additive blending ({@code SRC_ALPHA, ONE}, no depth write) instead of alpha: one shared instance, so the engine batches them. */
	public static final ParticleGasFlame.RenderTypeProvider GLOW = particle -> {
		if(!LeafiaFeatures.cosmeticOverhaul) return null;
		if(glow == null) glow = HbmParticleRenderTypes.additive(HbmParticleRenderTypes.WHITE);
		return glow;
	};
}
