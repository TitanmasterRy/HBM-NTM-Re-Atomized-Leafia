package com.leafia.contents.machines.controlpanel.core.client;

import com.hbm.packet.toserver.NBTControlPacket;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.ControlPanelClick;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelTE;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Custom Control Panel's client wiring (D-3065): the block entity renderer, the click, and the outline of the control under
 * the crosshair.
 *
 * <h2>The click</h2>
 * {@code ControlPanelBlock.useWithoutItem} runs on the client first and calls {@link ControlPanelBlock#clientClick}, which this
 * class sets. It casts the player's eye ray into the panel with {@link ControlPanelClick#pickIndex} (CE's
 * {@code getSelectedControl}, on the client with the client's matrices), and sends the index as the base mod's
 * {@code NBTControlPacket} carrying a {@code ctrl_press} event and the sneak state. <b>Nothing the client works out is trusted</b>:
 * the server re-checks reach, that the index exists and that the event is a press ({@code ControlPanelTE.applyClick}, D-3063).
 *
 * <h2>The outline</h2>
 * CE's {@code ICustomSelectionBox}: when the crosshair is on a control that can be clicked, the vanilla block outline gives way to
 * a box round that control, 0.01 below the panel so its bottom lines are buried. A lamp, dial, display or label has no box
 * ({@code getBoundingBox() == null}), so over one the whole panel's outline stays.
 */
@OnlyIn(Dist.CLIENT)
public final class ControlPanelClient {

	private ControlPanelClient() {}

	/** MOD-bus wiring. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class Mod {

		private Mod() {}

		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			if(LeafiaFeatures.controlPanel)
				event.registerBlockEntityRenderer(LeafiaBlockEntities.CONTROL_PANEL.get(), ControlPanelRender::new);
		}

		@SubscribeEvent
		public static void clientSetup(FMLClientSetupEvent event) {
			if(LeafiaFeatures.controlPanel)
				event.enqueueWork(() -> ControlPanelBlock.clientClick = ControlPanelClient::click);
		}
	}

	/** GAME-bus wiring. */
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT)
	public static final class Game {

		private Game() {}

		@SubscribeEvent
		public static void highlight(RenderHighlightEvent.Block event) {
			if(!LeafiaFeatures.controlPanel)
				return;
			Minecraft mc = Minecraft.getInstance();
			if(mc.level == null || mc.player == null)
				return;
			BlockPos pos = event.getTarget().getBlockPos();
			if(!(mc.level.getBlockEntity(pos) instanceof ControlPanelTE te) || te.panel == null || te.panel.transform == null)
				return;
			Control hit = te.panel.pick(eyeInBlock(mc.player, pos), mc.player.getViewVector(1F));
			if(hit == null || hit.getBoundingBox() == null)
				return;

			Vec3 cam = event.getCamera().getPosition();
			PoseStack pose = event.getPoseStack();
			pose.pushPose();
			pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
			pose.mulPose(te.panel.transform);
			VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
			AABB box = hit.getBoundingBox().move(0, -.01, 0);
			LevelRenderer.renderLineBox(pose, lines, box, 0F, 0F, 0F, 0.4F);
			pose.popPose();
			event.setCanceled(true);
		}
	}

	private static Vec3 eyeInBlock(Player player, BlockPos pos) {
		Vec3 eye = player.getEyePosition(1F);
		return new Vec3(eye.x - pos.getX(), eye.y - pos.getY(), eye.z - pos.getZ());
	}

	/**
	 * The press: finds the control under the crosshair and sends it. Returns whether a control was hit; the block consumes the
	 * click either way, as CE's did.
	 */
	static boolean click(Level level, BlockPos pos, Player player) {
		BlockEntity be = level.getBlockEntity(pos);
		if(!(be instanceof ControlPanelTE te) || te.panel == null)
			return false;
		int index = ControlPanelClick.pickIndex(te.panel, pos, player.getEyePosition(1F), player.getViewVector(1F));
		if(index < 0)
			return false;
		PacketDistributor.sendToServer(new NBTControlPacket(pos, ControlPanelClick.press(index, player.isShiftKeyDown())));
		return true;
	}
}
