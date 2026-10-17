package com.leafia.contents.machines.powercores.dfc.render;

import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.custom_hbm.render.misc.LCEBeamPronter;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumBeamType;
import com.custom_hbm.render.misc.LCEBeamPronter.EnumWaveType;
import com.hbm.blockentity.machine.BlockEntityCore;
import com.hbm.blockentity.machine.BlockEntityCoreEmitter;
import com.hbm.blockentity.machine.BlockEntityCoreInjector;
import com.hbm.blockentity.machine.BlockEntityCoreReceiver;
import com.hbm.blockentity.machine.BlockEntityCoreStabilizer;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;
import com.leafia.contents.machines.powercores.dfc.components.cemitter.CoreCEmitterTE;
import com.leafia.contents.machines.powercores.dfc.components.exchanger.CoreExchangerTE;
import com.leafia.contents.machines.powercores.dfc.components.pulser.CoreDetonatorTE;
import com.leafia.contents.machines.powercores.dfc.components.stabilizer.LeafiaCoreStabilizerTE;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCore;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreEmitter;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreReceiver;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityCoreStabilizer.LensType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws every part of the overhauled DFC but the core: the booster (the base emitter and the creative
 * one), the absorber and its reinforced twin, the injector, the stabilizer, the heat exchanger and the pulse
 * detonator - one renderer, picking its mesh and texture by block entity, as upstream's does.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/render/DFCComponentRender.java}. Every
 * part is a cube of five sides: a {@code Core} that turns to face its target (the yaw and pitch of the
 * vector to it, {@code atan2(-dx, -dz)} and {@code atan2(dy, hypot(dx, dz))}, with the model's front on
 * -Z), and a {@code Frame} on each face that does not face away from the target by more than 0.15 of
 * the direction's length, plus an {@code Arm} on the target-ward face's edges whose neighbour face is also
 * not a front face. Then what each part does:
 *
 * <ul>
 * <li><b>Emitter</b> - while it is firing ({@code leafia$isActive}), three beams along its last ray to where
 *     it stopped: a straight one and two wavering ones, {@code 0x401500} and {@code 0x5B1D00}
 *     ({@code 0x281332} and {@code 0x110165} for the creative booster), as wide as
 *     {@code max(1, log10(joules) - 6) / 8}.</li>
 * <li><b>Injector</b> - a spiral of each tank's fluid colour, 0.09 across and 0.0625 thick, along the line
 *     to the core, turning two degrees a tick, the second half a turn behind the first.</li>
 * <li><b>Absorber</b> - the reinforced one has a {@code Fan}, which turns {@code 720} degrees a second on
 *     top of the block entity's own angle. While the core spills SPK it hurls a bundle of wavering
 *     {@code 0x5B1D00} beams back at it, as many as {@code sqrt(mspk / 200) + 1}, where {@code mspk}
 *     is this absorber's share of what the core expels times a random 1 to 100, capped at 100,000.</li>
 * <li><b>Stabilizer</b> - its lens in the lens's own colours ({@code ONE, SRC_COLOR} over the glass), and
 *     while it is on, a straight beam and two spirals the whole way to the core, in the lens's two colours.
 *     Then the glass, translucent.</li>
 * <li><b>Heat exchanger</b> - three spirals of {@code 0x9b4100} (turning 25, 15 and 5 degrees a tick) and a
 *     straight {@code 0xffd000} line, 0.01 thick, to the core.</li>
 * <li><b>Pulse detonator</b> - a neon that is black, red once the count is running and white for the last
 *     six seconds; energy rings turning 2 and 3 degrees a tick while it is armed; a laser on each arm; and
 *     in the last tick of the countdown a red beam to the core and a flare one block ahead of it.</li>
 * </ul>
 *
 * <h2>Translations</h2>
 *
 * Upstream's lighting, culling, blend and alpha-test switches are render types ({@link DFCRenderTypes});
 * the lightmap it forces to 240 for glows is the full-bright light of the type. {@code renderPart} is
 * {@link DFCDraw#part} over the meshes {@link DFCObj} reads. The lens and the glass are the only textures
 * not read from a standalone file: the lens is drawn on the lens item's own sprite, mapped into the sprite
 * (upstream binds the item's particle texture by name). {@code isGlobalRenderer} is
 * {@link #shouldRenderOffScreen} with an infinite render box (D-3014's hand-off): the beams are as long as
 * the line to the core. The item models (the inventory and held renderers upstream provides) are the
 * DFC-2/-3 generator's JSON models; the base mod's four component items keep the base mod's.
 */
@OnlyIn(Dist.CLIENT)
public class DFCComponentRender implements BlockEntityRenderer<BlockEntity> {

	private static ResourceLocation texture(String name) {
		return LeafiaRef.rl("textures/entity/dfc/" + name + ".png");
	}

	static final ResourceLocation CEMITTER = texture("core_cemitter");
	static final ResourceLocation BOOSTER = texture("core_emitter");
	static final ResourceLocation ABSORBER = texture("core_receiver");
	static final ResourceLocation INJECTOR = texture("core_injector");
	static final ResourceLocation STABILIZER = texture("test_texture64");
	static final ResourceLocation EXCHANGER = texture("core_exchanger");
	static final ResourceLocation PULSER = texture("core_pulser");
	/** Upstream's {@code AddonBase.solid}: one white pixel, tinted by the vertex colour. */
	static final ResourceLocation SOLID = texture("solid");
	/** The flare the detonator ends with: the base mod's own {@code hadron.png}. */
	static final ResourceLocation HADRON = ResourceLocation.fromNamespaceAndPath("hbm", "textures/particle/hadron.png");

	public DFCComponentRender(BlockEntityRendererProvider.Context context) { }

	@Override
	public boolean shouldRenderOffScreen(BlockEntity te) {
		return true;
	}

	@Override
	public AABB getRenderBoundingBox(BlockEntity te) {
		return AABB.INFINITE;
	}

	@Override
	public int getViewDistance() {
		return 512;
	}

	/** The mesh a block entity is drawn from. */
	@Nullable
	static Map<String, Mesh> meshOf(BlockEntity te) {
		if(te instanceof CoreCEmitterTE || te instanceof BlockEntityCoreEmitter) return DFCObj.load(DFCObj.BOOSTER);
		if(te instanceof BlockEntityCoreReceiver)
			return DFCObj.load(isReinforced(te) ? DFCObj.REINFORCED : DFCObj.ABSORBER);
		if(te instanceof BlockEntityCoreInjector) return DFCObj.load(DFCObj.INJECTOR);
		if(te instanceof BlockEntityCoreStabilizer) return DFCObj.load(DFCObj.STABILIZER);
		if(te instanceof CoreExchangerTE) return DFCObj.load(DFCObj.EXCHANGER);
		if(te instanceof CoreDetonatorTE) return DFCObj.load(DFCObj.PULSER);
		return null;
	}

	/** The texture a block entity is drawn on. */
	@Nullable
	static ResourceLocation textureOf(BlockEntity te) {
		if(te instanceof CoreCEmitterTE) return CEMITTER;
		if(te instanceof BlockEntityCoreEmitter) return BOOSTER;
		if(te instanceof BlockEntityCoreReceiver) return ABSORBER;
		if(te instanceof BlockEntityCoreInjector) return INJECTOR;
		if(te instanceof BlockEntityCoreStabilizer) return STABILIZER;
		if(te instanceof CoreExchangerTE) return EXCHANGER;
		if(te instanceof CoreDetonatorTE) return PULSER;
		return null;
	}

	static boolean isReinforced(BlockEntity te) {
		return LeafiaBlocks.dfc_reinforced != null && te.getBlockState().is(LeafiaBlocks.dfc_reinforced.get());
	}

	@Override
	public void render(BlockEntity te, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {

		Level level = te.getLevel();
		if(level == null || !(te instanceof IDFCBase base)) return;
		Map<String, Mesh> mdl = meshOf(te);
		ResourceLocation tex = textureOf(te);
		if(mdl == null || tex == null) return;

		boolean pulser = te instanceof CoreDetonatorTE;
		RenderType body = DFCRenderTypes.body(tex);

		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.pushPose();

		net.minecraft.core.Vec3i relative = base.leafia$getTargetPosition().subtract(te.getBlockPos());
		Vec3 unit = DFCLayout.unit(relative);
		pose.mulPose(Axis.YP.rotationDegrees((float) DFCLayout.yaw(relative)));
		pose.mulPose(Axis.XP.rotationDegrees((float) DFCLayout.pitch(relative)));

		DFCDraw.part(buffers, body, mdl, DFCObj.CORE, pose, light);

		if(te instanceof IMixinTileEntityCoreReceiver absorber && isReinforced(te)) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(-absorber.leafia$fanAngle() - partialTick * 720));
			DFCDraw.part(buffers, body, mdl, DFCObj.FAN, pose, light);
			pose.popPose();
		}

		double range = 0;
		BlockEntityCore lastCore = base.leafia$lastGetCore();
		if(lastCore != null)
			range = Vec3.atCenterOf(te.getBlockPos()).distanceTo(Vec3.atCenterOf(lastCore.getBlockPos()));

		long time = level.getGameTime();

		if(te instanceof BlockEntityCoreStabilizer && te instanceof IMixinTileEntityCoreStabilizer stabilizer) {
			LensType lensType = stabilizer.getLens();
			int outerColor = lensType.outerColor;
			int innerColor = lensType.innerColor;
			if(stabilizer.hasLens()) {
				drawLens(buffers, mdl, pose, light, lensType, level);
				if(range > 0 && te instanceof LeafiaCoreStabilizerTE on && on.isOn) {
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
							outerColor, innerColor, 0, 1, 0F, 2, 0.125F);
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
							outerColor, innerColor, (int) time * -8 % 360, (int) Math.round(range * 3), 0.125F, 2, 0.04F);
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
							outerColor, innerColor, (int) time * -8 % 360 + 180, (int) Math.round(range * 3), 0.125F, 2, 0.04F);
				}
			}
			DFCDraw.part(buffers, DFCRenderTypes.glass(tex), mdl, DFCObj.GLASS, pose, light);
		}

		if(te instanceof BlockEntityCoreEmitter emitter && te instanceof IMixinTileEntityCoreEmitter mixin) {
			HitResult result = mixin.leafia$lastRaycast();
			if(result != null) {
				range = Vec3.atCenterOf(te.getBlockPos()).distanceTo(result.getLocation());
				if(mixin.leafia$isActive()) {
					float width = (float) Math.max(1, Math.log10(emitter.prev) - 6) / 8F;
					int colorA = 0x401500;
					int colorB = 0x5B1D00;
					if(te instanceof CoreCEmitterTE) {
						colorA = 0x281332;
						colorB = 0x110165;
					}
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
							colorA, 0x7F7F7F, 0, 1, 0F, 2, width);
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.RANDOM, EnumBeamType.SOLID,
							colorA, 0x7F7F7F, (int) (time % 1000), (int) (0.3F * range / width), width * 0.75F, 2, width * 0.5F);
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.RANDOM, EnumBeamType.SOLID,
							colorB, 0x7F7F7F, (int) (time % 1000) + 1, (int) (0.3F * range / width), width * 0.75F, 2, width * 0.5F);
				}
			}
		}

		if(te instanceof BlockEntityCoreInjector injector && range > 0) {
			if(injector.tanks[0].getFill() > 0)
				LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
						injector.tanks[0].getTankType().getColor(), 0x7F7F7F, (int) time * -2 % 360, (int) Math.round(range), 0.09F, 3, 0.0625F);
			if(injector.tanks[1].getFill() > 0)
				LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
						injector.tanks[1].getTankType().getColor(), 0x7F7F7F, (int) time * -2 % 360 + 180, (int) Math.round(range), 0.09F, 3, 0.0625F);
		}

		// The client's own core is the one its client tick found (the server's `leafia$getCore` is not set here),
		// and the client core keeps no absorber list (D-3014), so each one's share is the whole of what it
		// expels divided by one - upstream's client list holds every absorber that ticked, which is one in
		// the usual build and the count in a bank of them.
		if(te instanceof BlockEntityCoreReceiver absorber && te instanceof IMixinTileEntityCoreReceiver mixin
				&& lastCore instanceof IMixinTileEntityCore coreMixin) {
			BlockEntityCore core = lastCore;
			double mspk = coreMixin.getDFCExpellingSpk() * 20 / Math.max(1, coreMixin.getDFCAbsorbers().size()) * mixin.leafia$getLevel();
			mspk *= (level.random.nextDouble() * 99 + 1); // What the fuck why is it not (upstream's comment)
			mspk = Math.min(100000, mspk);
			int distance = (int) Math.round(Math.sqrt(absorber.getBlockPos().distSqr(core.getBlockPos())));
			pose.translate(0, 0, -distance);
			if(mspk > 0) {
				for(int i = 0; i < (int) Math.pow(mspk / 200, 0.5) + 1; i++) {
					LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, distance - 0.5), EnumWaveType.RANDOM, EnumBeamType.SOLID,
							0x5B1D00, 0x7F7F7F,
							(int) Math.floorMod(time * 3 + (int) (partialTick / 7) + i + 33, 1500L),
							distance * (i + 1),
							0.2F + (float) (Math.pow(mspk / 1000, 0.25) - 1) * 0.025F,
							3,
							0.1F + 0.0666F * (float) (Math.pow(mspk / 1000, 0.25) - 1));
				}
			}
		}

		if(te instanceof CoreExchangerTE && range > 0) {
			LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
					0x9b4100, 0x9b4100, (int) time * -25 % 360, (int) (range * 3), 0.125F / 1.5f, 1, 0.01f);
			LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
					0x9b4100, 0x9b4100, (int) time * -15 % 360 + 180, (int) (range * 3), 0.125F / 1.5f, 1, 0.01f);
			LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.SPIRAL, EnumBeamType.SOLID,
					0x9b4100, 0x9b4100, (int) time * -5 % 360 + 180, (int) (range * 3), 0.125F / 1.5f, 1, 0.01f);
			LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
					0xffd000, 0xffd000, 0, 1, 0, 1, 0.01f);
		}

		if(te instanceof CoreDetonatorTE det) {
			IMixinTileEntityCore core = det.lastGetCore instanceof IMixinTileEntityCore m ? m : null;
			if(det.isOn && core != null && core.getDetonationTimer() >= 20 * 30 - 1) {
				float width = 0.35f;
				LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.STRAIGHT, EnumBeamType.SOLID,
						0xFF0000, 0x7F7F7F, 0, 1, 0F, 2, width / 2f);
				LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.RANDOM, EnumBeamType.SOLID,
						0xFF0000, 0x7F7F7F, (int) (time % 1000), (int) (0.3F * range / width), width * 0.75F, 2, width * 0.5F);
				LCEBeamPronter.prontBeam(pose, buffers, new Vec3(0, 0, -range), EnumWaveType.RANDOM, EnumBeamType.SOLID,
						0xFF0000, 0x7F7F7F, (int) (time % 1000) + 1, (int) (0.3F * range / width), width * 0.75F, 2, width * 0.5F);
			}

			float nr = 0, ng = 0, nb = 0;
			if(core != null) {
				if(core.getDetonation()) nr = 1;
				if(core.getDetonationTimer() >= 20 * (30 - 6)) nr = ng = nb = 1;
			}
			DFCDraw.part(buffers, DFCRenderTypes.unlit(SOLID), mdl, DFCObj.NEON, pose, LightTexture.FULL_BRIGHT, nr, ng, nb, 1F);

			if(det.isOn) {
				RenderType additive = DFCRenderTypes.additive(tex);
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(Mth.positiveModulo((time + partialTick) * 2, 360)));
				DFCDraw.part(buffers, additive, mdl, DFCObj.RING_OUTER, pose, LightTexture.FULL_BRIGHT);
				pose.mulPose(Axis.ZP.rotationDegrees(Mth.positiveModulo((time + partialTick) * 3, 360)));
				DFCDraw.part(buffers, additive, mdl, DFCObj.RING_INNER, pose, LightTexture.FULL_BRIGHT);
				pose.popPose();
			}
		}

		pose.popPose();

		for(Direction face : Direction.values()) {
			pose.pushPose();
			if(face == Direction.UP) pose.mulPose(Axis.XP.rotationDegrees(90));
			else if(face == Direction.DOWN) pose.mulPose(Axis.XP.rotationDegrees(-90));
			else pose.mulPose(Axis.YP.rotationDegrees(90 * (2 - face.get2DDataValue())));

			if(DFCLayout.hasFrame(face, unit)) DFCDraw.part(buffers, body, mdl, DFCObj.FRAME, pose, light);

			if(DFCLayout.isFront(face, unit)) {
				boolean[] arms = DFCLayout.arms(face, unit);
				for(int i = 0; i < 4; i++) {
					if(arms[i]) {
						DFCDraw.part(buffers, body, mdl, DFCObj.ARM, pose, light);
						if(pulser) DFCDraw.part(buffers, DFCRenderTypes.additive(tex), mdl, DFCObj.ARM_LASER, pose, LightTexture.FULL_BRIGHT);
					}
					pose.mulPose(Axis.ZP.rotationDegrees(90));
				}
			}
			pose.popPose();
		}

		if(te instanceof CoreDetonatorTE det && det.isOn && det.lastGetCore instanceof IMixinTileEntityCore core
				&& core.getDetonationTimer() >= 20 * 30 - 1) {
			pose.pushPose();
			pose.translate(unit.x, unit.y, unit.z);
			flare(buffers, pose, 4);
			pose.popPose();
		}

		pose.popPose();
	}

	/** Upstream's {@code tessellateFlare}: the hadron sprite, camera-facing, additive, full-bright. */
	private static void flare(MultiBufferSource buffers, PoseStack pose, float scale) {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		Vector3f left = camera.getLeftVector();
		Vector3f up = camera.getUpVector();
		// Upstream's corners are -R-U, -R+U, +R+U, +R-U with R pointing right of the view.
		float rx = -left.x * scale, ry = -left.y * scale, rz = -left.z * scale;
		float ux = up.x * scale, uy = up.y * scale, uz = up.z * scale;
		PoseStack.Pose last = pose.last();
		VertexConsumer buffer = buffers.getBuffer(DFCRenderTypes.additive(HADRON));
		buffer.addVertex(last, -rx - ux, -ry - uy, -rz - uz).setColor(1F, 1F, 1F, 1F).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
		buffer.addVertex(last, -rx + ux, -ry + uy, -rz + uz).setColor(1F, 1F, 1F, 1F).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
		buffer.addVertex(last, rx + ux, ry + uy, rz + uz).setColor(1F, 1F, 1F, 1F).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
		buffer.addVertex(last, rx - ux, ry - uy, rz - uz).setColor(1F, 1F, 1F, 1F).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
	}

	/**
	 * The lens, on its own item's sprite. Upstream binds {@code baked.getParticleTexture().getIconName()}
	 * as a texture and draws the lens object over the glass with {@code ONE, SRC_COLOR}; the item's sprite
	 * lives in the atlas here, so the lens's 0..1 coordinates are mapped into the sprite's window.
	 */
	private static void drawLens(MultiBufferSource buffers, Map<String, Mesh> mdl, PoseStack pose, int light, LensType type, Level level) {
		net.minecraft.world.item.Item item = type.item.get();
		if(item == null) return;
		BakedModel baked = Minecraft.getInstance().getItemRenderer().getModel(new ItemStack(item), level, null, 0);
		TextureAtlasSprite sprite = baked.getParticleIcon();
		float[] window = { sprite.getU0(), sprite.getV0(), sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0() };
		DFCDraw.partInWindow(buffers, DFCRenderTypes.lens(sprite.atlasLocation()), mdl, DFCObj.LENS, pose, light, window);
	}

}
