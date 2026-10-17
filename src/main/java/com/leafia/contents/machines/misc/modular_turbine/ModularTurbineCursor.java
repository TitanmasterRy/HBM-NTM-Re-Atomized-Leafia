package com.leafia.contents.machines.misc.modular_turbine;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ILookOverlay;
import com.hbm.config.ClientConfig;
import com.hbm.main.ModEventHandlerClient;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase.TurbineComponentType;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * With turbine blades in sight, the crosshair becomes an arrow pointing the way the blades face —
 * so a player can tell which way round to place the next stage.
 *
 * <p>Upstream: the tail of {@code ModularTurbineBlockBase.printHook}, which cancels the 1.12.2
 * overlay event and draws {@code cursor.png} rotated by the blades' facing against the player's
 * yaw, flipped when the player looks up.
 *
 * <h2>Why it is an event handler here</h2>
 *
 * This port's {@link ILookOverlay#printHook} is called from the base mod's
 * {@code RenderGuiLayerEvent.Post} on the crosshair layer and draws into a {@link GuiGraphics}; it
 * cannot stop the crosshair, which has already been drawn. Hiding it means cancelling the layer's
 * {@code Pre} — and NeoForge's {@code GuiLayerManager} then skips the layer's {@code Post} too,
 * read in its source, so cancelling would also lose the turbine's own info panel. So this cancels,
 * draws the arrow, and then posts the base mod's own dispatcher the {@code Post} it would have had,
 * so the panel is drawn by exactly the code that draws it everywhere else. No base-mod file is
 * touched (D-1001).
 *
 * <p>It acts exactly where upstream's did: only when the base mod would have called the blades'
 * {@code printHook} at all — the overlay switch on, the held item not an overlay of its own, the
 * block a blade stage with a core.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ModularTurbineCursor {

	private ModularTurbineCursor() {}

	public static final ResourceLocation CURSOR =
			ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/gui/modular_turbine_cursor.png");

	@SubscribeEvent
	public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {

		if(!LeafiaFeatures.modularTurbine()) return;
		if(!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;
		if(!ClientConfig.DODD_RBMK_DIAGNOSTIC.get()) return;

		Minecraft mc = Minecraft.getInstance();
		Level world = mc.level;
		HitResult hit = mc.hitResult;
		if(world == null || mc.player == null || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;
		if(mc.player.getMainHandItem().getItem() instanceof ILookOverlay) return;

		BlockPos pos = blockHit.getBlockPos();
		if(!(world.getBlockState(pos).getBlock() instanceof ModularTurbineBlockBase turbine)) return;
		if(turbine.componentType() != TurbineComponentType.BLADES) return;
		BlockPos core = turbine.findCore(world, pos);
		if(core == null) return;

		event.setCanceled(true);

		// The panel the base mod would have drawn on this layer's Post, which cancelling skips.
		ModEventHandlerClient.onRenderGuiLayer(new RenderGuiLayerEvent.Post(event.getGuiGraphics(),
				event.getPartialTick(), event.getName(), event.getLayer()));

		Direction facing = world.getBlockState(core).getValue(BlockDummyable.DIR).getOpposite();
		drawCursor(event.getGuiGraphics(), facing, mc.player.getYRot(), mc.player.getXRot());
	}

	/**
	 * The arrow's angle on screen: the blades' compass angle minus the player's yaw, mirrored and
	 * turned half round when the player looks up. Upstream's formula; 1.12.2's
	 * {@code getHorizontalAngle} is {@link Direction#toYRot}.
	 */
	public static float cursorDegrees(Direction facing, float yaw, float pitch) {
		boolean inverse = pitch < 0;
		return (facing.toYRot() - yaw) * (inverse ? -1 : 1) + (inverse ? 180 : 0);
	}

	static void drawCursor(GuiGraphics gui, Direction facing, float yaw, float pitch) {
		RenderSystem.enableBlend();
		// The crosshair's own inverting blend, which is what upstream set.
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
				GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		gui.pose().pushPose();
		gui.pose().translate(gui.guiWidth() / 2D, gui.guiHeight() / 2D, 0D);
		gui.pose().mulPose(Axis.ZP.rotationDegrees(cursorDegrees(facing, yaw, pitch)));
		gui.blit(CURSOR, -6, -6, 0, 0, 12, 12, 256, 256);
		gui.pose().popPose();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}
}
