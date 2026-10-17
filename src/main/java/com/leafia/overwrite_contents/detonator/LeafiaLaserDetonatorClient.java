package com.leafia.overwrite_contents.detonator;

import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.custom_hbm.effectNT.EffectNT;
import com.hbm.items.ModItems;
import com.hbm.render.item.ItemRenderOverrides;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinItemLaserDetonator.RequestLaserPointPacket;
import com.leafia.overwrite_contents.packets.LaserDetonatorPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The laser detonator overhaul's client half: the pointing key, the tooltip, the aim-down-sights view, the laser's
 * motes and the held model. Everything here only while {@code overhauls.laserDetonatorOverhaul} is on.
 *
 * <ul>
 * <li><b>The key</b> ({@code key.categories.leafia.laser}, R) is upstream's {@code LeafiaKeybinds.Local.laserKey}: on
 *     the press (not while held) of a player carrying a detonator anywhere, upstream's item tick sends
 *     {@link RequestLaserPointPacket}.</li>
 * <li><b>The tooltip</b> is upstream's {@code @Overwrite addInformation}: {@code item.detonator_laser.desc} and
 *     {@code .desc2}, in place of the base mod's line.</li>
 * <li><b>The view</b> is {@code LeafiaClientListener}'s {@code FOVUpdateEvent}: multiplied by the sights' 0.3 while
 *     aiming (see {@link LeafiaLaserDetonator#getViewADS}).</li>
 * <li><b>The model</b> is {@code MixinItemRenderLaserDetonator}: upstream's grip offsets and aim-down-sights pose, in
 *     {@link LaserDetonatorRender}, which replaces the base mod's own detonator renderer through
 *     {@link ItemRenderOverrides} (D-3051); the base item's baked model is wrapped to hand it the item with no display
 *     transforms of its own.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaLaserDetonatorClient {

	private LeafiaLaserDetonatorClient() {}

	public static final KeyMapping LASER_KEY = new KeyMapping("key.categories.leafia.laser", InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_R, "key.categories.leafia");

	public static final ModelResourceLocation MAIN = standalone("item/detonator_laser_main");
	public static final ModelResourceLocation LIGHTS = standalone("item/detonator_laser_lights");

	private static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, path));
	}

	private static boolean lastKeyDown = false;

	/** {@code LaserDetonatorPacket}'s body: motes from both ends toward the middle. */
	public static void drawLaser(Vec3 start, Vec3 direction) {
		if(Minecraft.getInstance().level == null) return;
		double length = direction.length();
		Vec3 unit = direction.normalize();
		java.util.Random rand = new java.util.Random();
		for(int i = 0; i <= 1; i++) {
			for(double p : LaserDetonatorPacket.moteSteps(length)) {
				double d = (length - p) * i + p * (1 - i);
				CompoundTag particle = new CompoundTag();
				particle.putString("type", EffectNT.RSLIGHT);
				particle.putDouble("posX", start.x + unit.x * d);
				particle.putDouble("posY", start.y + unit.y * d);
				particle.putDouble("posZ", start.z + unit.z * d);
				particle.putDouble("mX", (rand.nextDouble() - 0.5) * 0.05);
				particle.putDouble("mY", (rand.nextDouble() - 0.5) * 0.05);
				particle.putDouble("mZ", (rand.nextDouble() - 0.5) * 0.05);
				particle.putFloat("red", 1);
				particle.putFloat("green", 0);
				particle.putFloat("blue", 0);
				particle.putFloat("scale", 0.5f);
				EffectNT.effectNT(particle);
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class ModEvents {

		private ModEvents() {}

		@SubscribeEvent
		public static void registerKeys(RegisterKeyMappingsEvent event) {
			if(LeafiaFeatures.laserDetonatorOverhaul) event.register(LASER_KEY);
		}

		@SubscribeEvent
		public static void registerModels(ModelEvent.RegisterAdditional event) {
			if(!LeafiaFeatures.laserDetonatorOverhaul) return;
			event.register(MAIN);
			event.register(LIGHTS);
		}

		/** The base item's model, made to hand its whole drawing to {@link LaserDetonatorRender}. */
		@SubscribeEvent
		public static void wrapModel(ModelEvent.ModifyBakingResult event) {
			// the base's own renderer places itself by this model's display transforms; the overhaul's grip table replaces them
			if(!LeafiaFeatures.laserDetonatorOverhaul) return;
			ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(ModItems.detonator_laser.get()));
			BakedModel original = event.getModels().get(key);
			if(original != null) event.getModels().put(key, new Deferred(original));
		}

		/**
		 * The overhaul's renderer, through the base mod's item renderer seam ({@link ItemRenderOverrides}, D-2240). Since the
		 * allen consolidation (D-2203) the base draws the detonator in 3D itself and NeoForge allows one item extension per
		 * item, so a second registration crashed the client at startup (D-3050); the seam replaces the renderer that
		 * extension builds instead. Made on first draw, and it ignores the base's (the grip table places the whole item).
		 * A hand transform is not needed: upstream's aim-down-sights pose is part of its renderer, not of the hand.
		 */
		@SubscribeEvent
		public static void replaceBaseRenderer(FMLClientSetupEvent event) {
			if(!LeafiaFeatures.laserDetonatorOverhaul) return;
			ItemRenderOverrides.replaceRenderer(BuiltInRegistries.ITEM.getKey(ModItems.detonator_laser.get()), original -> new LaserDetonatorRender());
		}
	}

	/** The base model with no transform of its own and a custom renderer: the BEWLR does the placing (upstream's TEISR). */
	public static final class Deferred extends BakedModelWrapper<BakedModel> {

		public Deferred(BakedModel original) {
			super(original);
		}

		@Override
		public boolean isCustomRenderer() {
			return true;
		}

		@Override
		public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean applyLeftHandTransform) {
			return this;
		}
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
	public static final class GameEvents {

		private GameEvents() {}

		/** Upstream's {@code leafia$handlePointing}, run from the item's inventory tick: one request per press. */
		@SubscribeEvent
		public static void onClientTick(ClientTickEvent.Post event) {
			if(!LeafiaFeatures.laserDetonatorOverhaul) return;
			Player player = Minecraft.getInstance().player;
			if(player == null || !LeafiaLaserDetonator.carries(player)) return;
			if(LASER_KEY.isDown()) {
				if(!lastKeyDown) {
					lastKeyDown = true;
					LeafiaCustomPacket packet = LeafiaCustomPacket.__start(new RequestLaserPointPacket());
					if(packet != null) packet.__sendToServer();
				}
			} else
				lastKeyDown = false;
		}

		@SubscribeEvent
		public static void onTooltip(ItemTooltipEvent event) {
			if(!LeafiaFeatures.laserDetonatorOverhaul || !LeafiaLaserDetonator.isDetonator(event.getItemStack())) return;
			List<Component> tooltip = event.getToolTip();
			tooltip.removeIf(c -> c.getString().equals("Aim & click to detonate!"));
			int at = Math.min(1, tooltip.size());
			tooltip.add(at, Component.literal(I18nUtil.resolveKey("item.detonator_laser.desc")));
			tooltip.add(at + 1, Component.literal(I18nUtil.resolveKey("item.detonator_laser.desc2")));
		}

		@SubscribeEvent
		public static void onFov(ComputeFovModifierEvent event) {
			float viewADS = LeafiaLaserDetonator.getViewADS(event.getPlayer());
			if(viewADS != 0)
				event.setNewFovModifier(event.getNewFovModifier() * Math.abs(viewADS));
		}
	}

	/** For the renderer: three random six-digit numbers, a new set every half second. */
	static String[] readout(long millis) {
		Random rand = new Random(millis / 500);
		String[] out = new String[3];
		for(int i = 0; i < 3; i++) out[i] = Integer.toString(rand.nextInt(900000) + 100000);
		return out;
	}
}
