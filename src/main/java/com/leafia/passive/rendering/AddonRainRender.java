package com.leafia.passive.rendering;

import java.util.Random;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.worldgen.biomes.artificial.DigammaCrater;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.savedata.FalloutSavedData;
import com.leafia.savedata.FalloutSavedData.FalloutData;
import com.leafia.unsorted.ParticleRainBlood;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Two kinds of rain vanilla does not have: <b>fallout rain</b>, falling over a finished nuke's radius
 * while its {@link FalloutSavedData} record lives, as dense as that record is at the viewer; and <b>blood
 * rain</b>, falling in the digamma crater, with drops spattering on the ground around the viewer and a
 * rotten-rain sound. Upstream: {@code com/leafia/passive/rendering/AddonRainRender.java}, a copy of
 * 1.12.2's {@code EntityRenderer.renderRainSnow} and {@code addRainParticles} drawn from
 * {@code RenderWorldLastEvent} and ticked from {@code LeafiaPassiveLocal}.
 *
 * <p>Here it draws from {@link RenderLevelStageEvent} at {@code AFTER_WEATHER}, NeoForge's hook beside
 * vanilla's own rain (no mixin into a vanilla class), and ticks from {@link ClientTickEvent.Pre} when the
 * game is not paused (or is not single-player), as upstream's {@code priorTick}. Each column's quad, its
 * texture scroll and its fade from the viewer are upstream's statement for statement, including the
 * integer seeds; the textures are {@code environment/fallout_dense.png} and {@code environment/bloodrain.png}
 * (upstream's {@code fallout_dense.png} and {@code bloodrain.png}; the 32 x 8 particle strip of the same
 * name is a different picture).
 *
 * <h2>Translations</h2>
 * <ul>
 *   <li>Vertices are relative to the camera, where upstream's were relative to the viewer's interpolated
 *       feet under a modelview already at the eye: the same in first person.</li>
 *   <li>Upstream read each column's biome at {@code y = 50}, which in 1.12.2 was the column's only biome;
 *       1.21.1's biomes are three-dimensional, and the crater biome is painted from top to bottom of the
 *       world, so the surface ({@code MOTION_BLOCKING}) is read. Same for the drops.</li>
 *   <li>Drawn with the position-texture-colour shader, unlit, as upstream's (which disabled lighting and
 *       did not write a brightness), and without depth writes, as vanilla's own rain.</li>
 *   <li>A drop's surface is the block's outline shape, upstream's {@code getBoundingBox}; lava and magma
 *       give smoke instead, as upstream's material test.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class AddonRainRender {

	public static final AddonRainRender INSTANCE = new AddonRainRender();

	public static final ResourceLocation TEX_DENSE = LeafiaRef.rl("textures/environment/fallout_dense.png");
	public static final ResourceLocation TEX_BLOOD = LeafiaRef.rl("textures/environment/bloodrain.png");

	private final Random random = new Random();
	final float[] rainXCoords = new float[1024];
	final float[] rainYCoords = new float[1024];
	public int rendererUpdateCount;
	private int rainSoundCounter;

	public AddonRainRender() {
		for(int i = 0; i < 32; ++i) {
			for(int j = 0; j < 32; ++j) {
				float f = (float) (j - 16);
				float f1 = (float) (i - 16);
				float f2 = Mth.sqrt(f * f + f1 * f1);
				this.rainXCoords[i << 5 | j] = -f1 / f2;
				this.rainYCoords[i << 5 | j] = f / f2;
			}
		}
	}

	// ------------------------------------------------------------------------------- hooks

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || mc.getCameraEntity() == null) return;
		if(!mc.isPaused() || !mc.isSingleplayer()) {
			INSTANCE.update();
			if(LeafiaFeatures.falloutRain) FalloutSavedData.forClient(mc.level).tick();
		}
	}

	@SubscribeEvent
	public static void onRenderStage(RenderLevelStageEvent event) {
		if(event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.level == null || mc.getCameraEntity() == null) return;
		if(!LeafiaFeatures.falloutRain && !LeafiaFeatures.digammaCrater) return;
		INSTANCE.render(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
	}

	// ------------------------------------------------------------------------------- upstream

	public void render(Camera camera, float partialTicks) {
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		try {
			if(LeafiaFeatures.falloutRain) renderFalloutRain(camera, partialTicks);
			if(LeafiaFeatures.digammaCrater) renderBloodRain(camera, partialTicks);
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.disableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableCull();
		}
	}

	public void update() {
		this.rendererUpdateCount++;
		if(LeafiaFeatures.digammaCrater) addRainParticles();
	}

	private boolean fancy() {
		return Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST;
	}

	private void addRainParticles() {
		Minecraft mc = Minecraft.getInstance();
		float f = 1;
		if(!fancy()) f /= 2.0F;

		this.random.setSeed((long) this.rendererUpdateCount * 312987231L);
		Entity entity = mc.getCameraEntity();
		ClientLevel world = mc.level;
		BlockPos blockpos = entity.blockPosition();
		double d0 = 0.0D;
		double d1 = 0.0D;
		double d2 = 0.0D;
		int j = 0;
		int k = (int) (100.0F * f * f);

		ParticleStatus particles = mc.options.particles().get();
		if(particles == ParticleStatus.DECREASED) k >>= 1;
		else if(particles == ParticleStatus.MINIMAL) k = 0;

		for(int l = 0; l < k; ++l) {
			BlockPos column = blockpos.offset(this.random.nextInt(10) - this.random.nextInt(10), 0, this.random.nextInt(10) - this.random.nextInt(10));
			BlockPos blockpos1 = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
			BlockPos blockpos2 = blockpos1.below();
			BlockState iblockstate = world.getBlockState(blockpos2);

			if(blockpos1.getY() <= blockpos.getY() + 10 && blockpos1.getY() >= blockpos.getY() - 10 && DigammaCrater.isDigammaBiome(world.getBiome(blockpos1))) {
				double d3 = this.random.nextDouble();
				double d4 = this.random.nextDouble();
				VoxelShape shape = iblockstate.getShape(world, blockpos2);
				double maxY = shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y);
				double minY = shape.isEmpty() ? 0 : shape.min(net.minecraft.core.Direction.Axis.Y);

				if(!iblockstate.getFluidState().is(FluidTags.LAVA) && !iblockstate.is(Blocks.MAGMA_BLOCK)) {
					if(!iblockstate.isAir()) {
						++j;
						if(this.random.nextInt(j) == 0) {
							d0 = (double) blockpos2.getX() + d3;
							d1 = (double) ((float) blockpos2.getY() + 0.1F) + maxY - 1.0D;
							d2 = (double) blockpos2.getZ() + d4;
						}
						mc.particleEngine.add(new ParticleRainBlood(world, (double) blockpos2.getX() + d3,
								(double) ((float) blockpos2.getY() + 0.1F) + maxY, (double) blockpos2.getZ() + d4));
					}
				} else {
					world.addParticle(ParticleTypes.SMOKE, (double) blockpos1.getX() + d3,
							(double) ((float) blockpos1.getY() + 0.1F) - minY, (double) blockpos1.getZ() + d4, 0.0D, 0.0D, 0.0D);
				}
			}
		}

		if(j > 0 && this.random.nextInt(3) < this.rainSoundCounter++) {
			this.rainSoundCounter = 0;
			if(d1 > (double) (blockpos.getY() + 1) && world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos).getY() > Mth.floor((float) blockpos.getY()))
				world.playLocalSound(d0, d1, d2, LeafiaSounds.sbesrottenrain_above.get(), SoundSource.WEATHER, 0.3F, 1F, false);
			else
				world.playLocalSound(d0, d1, d2, LeafiaSounds.sbesrottenrain.get(), SoundSource.WEATHER, 0.3F, 1.0F, false);
		}
	}

	/** The upstream seed of one rain column: int arithmetic, {@code +} before {@code ^}. */
	static long columnSeed(int i1, int l) {
		return i1 * i1 * 3121 + i1 * 45238971 ^ l * l * 418711 + l * 13761;
	}

	protected void renderBloodRain(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel worldclient = mc.level;
		Entity entity = mc.getCameraEntity();
		Vec3 cam = camera.getPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		int k2 = Mth.floor(entity.getX());
		int l2 = Mth.floor(entity.getY());
		int i3 = Mth.floor(entity.getZ());
		double d1 = Mth.lerp(partialTick, entity.yo, entity.getY());
		int k = Mth.floor(d1);
		int b0 = fancy() ? 10 : 5;

		BufferBuilder buf = null;
		for(int l = i3 - b0; l <= i3 + b0; ++l) {
			for(int i1 = k2 - b0; i1 <= k2 + b0; ++i1) {
				int j1 = (l - i3 + 16) * 32 + i1 - k2 + 16;
				float f6 = this.rainXCoords[j1] * 0.5F;
				float f7 = this.rainYCoords[j1] * 0.5F;
				int k1 = worldclient.getHeight(Heightmap.Types.MOTION_BLOCKING, i1, l);
				pos.set(i1, k1, l);

				if(DigammaCrater.isDigammaBiome(worldclient.getBiome(pos))) {
					int l1 = l2 - b0;
					int i2 = l2 + b0;
					if(l1 < k1) l1 = k1;
					if(i2 < k1) i2 = k1;

					if(l1 != i2) {
						this.random.setSeed(columnSeed(i1, l));
						if(buf == null) {
							RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
							RenderSystem.setShaderTexture(0, TEX_BLOOD);
							buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
						}

						double f11 = -((double) (this.rendererUpdateCount + l1 * l1 * 3121 + l1 * 45238971 + k1 * k1 * 418711 + k1 * 13761 & 31)
								+ (double) partialTick) / 32.0D * (3.0D + this.random.nextDouble());

						double d4 = i1 + 0.5F - entity.getX();
						double d5 = l + 0.5F - entity.getZ();
						float f14 = (float) Math.sqrt(d4 * d4 + d5 * d5) / b0;
						float f15 = 1;
						float a = ((1.0F - f14 * f14) * 0.3F + 0.5F) * f15;

						quad(buf, cam, i1 - f6 + 0.5D, l1, l - f7 + 0.5D, 0.0F, (float) (l1 / 4.0F - f11), a);
						quad(buf, cam, i1 + f6 + 0.5D, l1, l + f7 + 0.5D, 1.0F, (float) (l1 / 4.0F - f11), a);
						quad(buf, cam, i1 + f6 + 0.5D, i2, l + f7 + 0.5D, 1.0F, (float) (i2 / 4.0F - f11), a);
						quad(buf, cam, i1 - f6 + 0.5D, i2, l - f7 + 0.5D, 0.0F, (float) (i2 / 4.0F - f11), a);
					}
				}
			}
		}
		draw(buf);
	}

	protected void renderFalloutRain(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel worldclient = mc.level;
		Entity entity = mc.getCameraEntity();
		FalloutSavedData saved = FalloutSavedData.forClient(worldclient);
		float rawStrength = 0;
		FalloutData data = saved.getDensest(new Vec3(entity.getX(), 0, entity.getZ()));
		if(data != null) rawStrength = (float) data.getDensity();

		float f1 = (float) Math.min(Math.pow(rawStrength * 2, 0.5), 1); // strength
		if(f1 <= 0.0F) return;

		Vec3 cam = camera.getPosition();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int k2 = Mth.floor(entity.getX());
		int l2 = Mth.floor(entity.getY());
		int i3 = Mth.floor(entity.getZ());
		double d1 = Mth.lerp(partialTick, entity.yo, entity.getY());
		int k = Mth.floor(d1);
		int b0 = fancy() ? 10 : 5;
		float f5 = this.rendererUpdateCount + partialTick;

		BufferBuilder buf = null;
		for(int l = i3 - b0; l <= i3 + b0; ++l) {
			for(int i1 = k2 - b0; i1 <= k2 + b0; ++i1) {
				int j1 = (l - i3 + 16) * 32 + i1 - k2 + 16;
				float f6 = this.rainXCoords[j1] * 0.5F;
				float f7 = this.rainYCoords[j1] * 0.5F;
				double mul = data.getDistanceMultiplier(new Vec3(i1 + 0.5, 0, l + 0.5));
				float alpha = (float) (f1 * mul);

				int k1 = worldclient.getHeight(Heightmap.Types.MOTION_BLOCKING, i1, l);
				pos.set(i1, k1, l);
				int l1 = l2 - b0;
				int i2 = l2 + b0;
				if(l1 < k1) l1 = k1;
				if(i2 < k1) i2 = k1;

				if(l1 != i2) {
					this.random.setSeed(columnSeed(i1, l));
					if(buf == null) {
						RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
						RenderSystem.setShaderTexture(0, TEX_DENSE);
						buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
					}

					float f10 = ((this.rendererUpdateCount & 511) + partialTick) / 512.0F;
					float f16 = this.random.nextFloat() + f5 * 0.01F * (float) this.random.nextGaussian();
					float f11 = this.random.nextFloat() + f5 * (float) this.random.nextGaussian() * 0.001F;
					double d4 = i1 + 0.5F - entity.getX();
					double d5 = l + 0.5F - entity.getZ();
					float f14 = (float) Math.sqrt(d4 * d4 + d5 * d5) / b0;
					float a = ((1.0F - f14 * f14) * 0.3F + 0.5F) * alpha;
					if(!(a > 0)) continue; // NaN past the record's radius: upstream wrote it as a zero alpha

					quad(buf, cam, i1 - f6 + 0.5D, l1, l - f7 + 0.5D, 0.0F + f16, l1 / 4.0F + f10 + f11, a);
					quad(buf, cam, i1 + f6 + 0.5D, l1, l + f7 + 0.5D, 1.0F + f16, l1 / 4.0F + f10 + f11, a);
					quad(buf, cam, i1 + f6 + 0.5D, i2, l + f7 + 0.5D, 1.0F + f16, i2 / 4.0F + f10 + f11, a);
					quad(buf, cam, i1 - f6 + 0.5D, i2, l - f7 + 0.5D, 0.0F + f16, i2 / 4.0F + f10 + f11, a);
				}
			}
		}
		draw(buf);
	}

	private static void quad(BufferBuilder buf, Vec3 cam, double x, double y, double z, float u, float v, float alpha) {
		buf.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setUv(u, v).setColor(1F, 1F, 1F, Math.min(alpha, 1F));
	}

	private static void draw(BufferBuilder buf) {
		if(buf == null) return;
		MeshData mesh = buf.build();
		if(mesh != null) BufferUploader.drawWithShader(mesh);
	}
}
