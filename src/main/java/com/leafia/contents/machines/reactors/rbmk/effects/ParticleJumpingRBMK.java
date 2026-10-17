package com.leafia.contents.machines.reactors.rbmk.effects;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.RBMKDials;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.hbm.render.blockentity.RenderRBMKLid;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityRBMKBase;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A damaged RBMK column's lid in the air: the column's top drawn again, lifted by the jump height the
 * server syncs, with its lid and contents on it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/rbmk/effects/ParticleJumpingRBMK.java}, a particle
 * on 1.12.2's custom layer whose {@code renderParticle} drew the column's core block model and its top block
 * model with a GL translate of the jump, and then called the column's TESR with a destroy stage of
 * {@code -14}, which {@code MixinRenderRBMKControlRod} read as "draw me even though I am jumping". 1.21.1's
 * particle engine hands a particle one vertex buffer of one render type, which cannot hold a block model and a
 * block-entity renderer's several types, so the particle keeps upstream's life and does its drawing at
 * {@link RenderLevelStageEvent.Stage#AFTER_BLOCK_ENTITIES}, after the real column: it is a
 * {@link ParticleRenderType#NO_RENDER} particle the engine ticks, and the live ones are kept here by position.
 * The {@code -14} becomes {@link RenderRBMKLid#renderColumn}, which no render hook is asked about, and the
 * skip on the base renderer is {@code LeafiaRBMKClient}'s render hook (D-3018).
 *
 * <p>Its life is upstream's: it reads the column's jump height every tick, expires after three seconds if
 * the column never jumps, and once it has seen a jump expires the first tick the height reads zero twice. It
 * expires at once if the block at its position stops being an RBMK column.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ParticleJumpingRBMK extends Particle {

	/** The live ones, by column. Client thread only. */
	private static final Map<BlockPos, ParticleJumpingRBMK> LIVE = new HashMap<>();

	final BlockState state;
	final BlockState topState;
	final BlockPos pos;
	final BlockPos topPos;
	final BlockPos abovePos;
	BlockEntityRBMKBase rbmk;
	boolean jumped = false;
	double lastJump = 0;
	double newJump = 0;

	public ParticleJumpingRBMK(ClientLevel level, BlockPos rbmkPos) {
		super(level, rbmkPos.getX() + 0.5, rbmkPos.getY() + RBMKDials.getColumnHeight(level) - 1, rbmkPos.getZ() + 0.5);
		this.pos = rbmkPos;
		this.state = level.getBlockState(rbmkPos);
		BlockPos top = this.pos;
		while(level.getBlockState(top.above()).getBlock() == this.state.getBlock()) top = top.above();
		this.topPos = top;
		this.topState = level.getBlockState(this.topPos);
		this.abovePos = this.topPos.above();
		if(level.getBlockEntity(rbmkPos) instanceof BlockEntityRBMKBase te) this.rbmk = te;
		else this.remove();
		this.lifetime = 20 * 3; // upstream's "test"
		this.hasPhysics = false;
	}

	/**
	 * Upstream's {@code JumpingRBMKParticle.emit}, and what the render hook calls for a column it hides: a
	 * particle for this column unless one is alive already.
	 */
	public static void spawn(ClientLevel level, BlockPos pos) {
		ParticleJumpingRBMK live = LIVE.get(pos);
		if(live != null && live.isAlive() && live.level == level) return;
		ParticleJumpingRBMK particle = new ParticleJumpingRBMK(level, pos.immutable());
		if(!particle.isAlive()) return;
		LIVE.put(particle.pos, particle);
		Minecraft.getInstance().particleEngine.add(particle);
	}

	@Override
	public void tick() {
		this.lastJump = this.newJump;
		this.newJump = this.rbmk == null || this.rbmk.isRemoved() ? 0 : IMixinTileEntityRBMKBase.leafia$jumpHeight(this.rbmk);
		if(!this.jumped) {
			this.age++;
			if(this.age > this.lifetime) this.remove(); // too long without jumping
		} else {
			if(this.lastJump <= 0 && this.newJump <= 0) this.remove();
		}
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTicks) { }

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.NO_RENDER;
	}

	/** Upstream's {@code renderParticle}: the core and top block models and the column's renderer, lifted. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void renderJump(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTicks) {

		if(!(this.level.getBlockState(this.pos).getBlock() instanceof RBMKBase)) this.remove();
		if(!this.isAlive()) return;

		double jump = this.lastJump + (this.newJump - this.lastJump) * partialTicks;
		if(jump > 0) this.jumped = true;

		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = camera.getPosition();
		int height = this.topPos.getY() - this.pos.getY();
		int light = LevelRenderer.getLightColor(this.level, this.abovePos);

		pose.pushPose();
		pose.translate(this.pos.getX() - cam.x, this.pos.getY() - cam.y + jump, this.pos.getZ() - cam.z);

		mc.getBlockRenderer().renderSingleBlock(this.state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.pushPose();
		pose.translate(0, height, 0);
		mc.getBlockRenderer().renderSingleBlock(this.topState, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();

		if(this.rbmk != null && !this.rbmk.isRemoved()) {
			BlockEntityRenderer<BlockEntityRBMKBase> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(this.rbmk);
			if(renderer instanceof RenderRBMKLid lid) {
				lid.renderColumn(this.rbmk, partialTicks, pose, buffers, LevelRenderer.getLightColor(this.level, this.pos), OverlayTexture.NO_OVERLAY);
			}
		}

		pose.popPose();
	}

	/** How many columns are in the air on this client. For the snapshot scene. */
	public static int liveCount() {
		return LIVE.size();
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
		if(LIVE.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		boolean drew = false;

		Iterator<ParticleJumpingRBMK> it = LIVE.values().iterator();
		while(it.hasNext()) {
			ParticleJumpingRBMK particle = it.next();
			if(!particle.isAlive() || particle.level != mc.level) {
				it.remove();
				continue;
			}
			particle.renderJump(event.getPoseStack(), buffers, event.getCamera(), partialTicks);
			drew = true;
		}

		if(drew) buffers.endBatch();
	}
}
