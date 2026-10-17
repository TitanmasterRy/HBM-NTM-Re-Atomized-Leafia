package com.leafia.contents.machines.elevators.floors.model;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.leafia.contents.machines.elevators.floors.EvFloorTE;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * The landing's paint disguise: a block model that draws whatever block the landing was painted
 * with.
 *
 * <p>Upstream: {@code .../floors/model/EvFloorBakedModel.java}. Right-clicking a landing with any
 * block item paints it, and its surround then looks like a wall of that block with a doorway cut
 * into it. D-1039 ported the paint's <em>state</em> — it sets, saves and synchronises — and left
 * its appearance for this, because the mechanism upstream uses has no counterpart here and the
 * successor had never been used in this port.
 *
 * <h2>Where the paint comes from, and why it is not where upstream put it</h2>
 *
 * Upstream hangs the disguise on an {@code IExtendedBlockState}: {@code EvFloor} declares an
 * unlisted {@code PAINT} property, {@code getExtendedState} fills it from the core's tile entity,
 * and this model reads it back. 1.21.1 has no extended block states; the successor is
 * {@link ModelData}, a per-position map keyed by {@link ModelProperty}.
 *
 * <p><b>But a landing's painted blocks have no block entity.</b> Only the core does — the
 * surround is {@code BlockDummyable} dummies — and {@link ModelData} reaches a block through
 * exactly two doors in NeoForge 21.1: the <em>block entity's</em> {@code getModelData()}, and
 * the <em>baked model's</em>. There is no block-level hook. So the walk to the core happens
 * here, in {@link #getModelData}, rather than on the block: the model is handed the level, the
 * position and the state at meshing time and looks the paint up itself.
 *
 * <p>That is a better fit than upstream's, and it is worth saying why rather than only that it
 * differs: upstream's arrangement needs every dummy to carry a property that is really the
 * core's, and this one asks the core directly at the moment the answer is needed.
 *
 * <h2>Only the front face, and always</h2>
 *
 * Upstream overrides {@code shouldSideBeRendered} to {@code state.getValue(FACE).equals(side)} —
 * so a painted landing draws <b>one</b> face, the one it faces, and draws it whatever is next to
 * it. Reproduced by emitting the disguise's quads for that direction in the <em>general</em>
 * pass rather than the per-side one: the per-side pass is what vanilla culls against neighbours,
 * and upstream's override is unconditional. A landing set into a wall therefore reads as a flat
 * painted panel, which is what upstream draws.
 *
 * <h2>The default is steel, and it is upstream's</h2>
 *
 * An unpainted landing draws {@code hbm:block_steel} rather than nothing, so a landing is never
 * an invisible hole. That is upstream's {@code ModBlocks.block_steel.getDefaultState()}.
 *
 * <h2>What is port-invented, and flagged as such</h2>
 *
 * Upstream installs this model by patching the bake result — {@code ModelBakeEvent} plus
 * {@code getModelRegistry().putObject(...)}. NeoForge has that escape hatch too
 * ({@code ModelEvent.ModifyBakingResult}), and this uses the data-driven route instead: a
 * {@link Loader} registered under {@code leafia:ev_floor_paint} and named by the landings'
 * blockstate JSON, like every other model in this addon. One source of truth — the blockstate
 * says which model a block uses — rather than a blockstate that says one thing and a patch that
 * says another.
 */
@OnlyIn(Dist.CLIENT)
public class EvFloorBakedModel implements BakedModel {

	/** The block a landing has been painted to look like. Empty until {@link #getModelData} runs. */
	public static final ModelProperty<BlockState> PAINT = new ModelProperty<>();

	/**
	 * Reads the core's paint at meshing time.
	 *
	 * <p>{@code level} here is the mesh thread's snapshot of the section, so walking to the core
	 * and reading its block entity out of it is safe — which is the concurrency warning
	 * NeoForge's own {@code IBlockExtension} javadoc gives, satisfied by the snapshot rather
	 * than by copying anything.
	 */
	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {

		if(!(state.getBlock() instanceof BlockDummyable dummyable)) return modelData;

		BlockPos core = dummyable.findCore(level, pos);
		if(core == null) return modelData;
		if(!(level.getBlockEntity(core) instanceof EvFloorTE landing)) return modelData;
		if(landing.paintBlock == null) return modelData;

		return modelData.derive().with(PAINT, landing.paintBlock.defaultBlockState()).build();
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
			RandomSource rand, ModelData data, @Nullable RenderType renderType) {

		// Upstream's shouldSideBeRendered draws one face unconditionally, so the quads go in the
		// general pass; the per-side pass is the one vanilla culls against neighbours.
		if(side != null || state == null) return Collections.emptyList();

		BlockState disguise = disguise(data);
		BakedModel model = modelFor(disguise);
		Direction facing = state.hasProperty(BlockDummyable.DIR)
				? state.getValue(BlockDummyable.DIR) : Direction.NORTH;

		return model.getQuads(disguise, facing, rand, ModelData.EMPTY, renderType);
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
		return getQuads(state, side, rand, ModelData.EMPTY, null);
	}

	/**
	 * The render layers the disguise wants, not this model's.
	 *
	 * <p>Not upstream's — 1.12.2 puts the render layer on the {@code Block}, so upstream's
	 * disguise silently inherits the landing's. Here a glass or leaves disguise has to be drawn
	 * on its own layer or it renders opaque black, so the question has to be asked of the
	 * disguise. A deviation only in the sense that upstream had no way to get it wrong.
	 */
	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		BlockState disguise = disguise(data);
		return modelFor(disguise).getRenderTypes(disguise, rand, ModelData.EMPTY);
	}

	/** Upstream's default: an unpainted landing looks like steel rather than like nothing. */
	private static BlockState disguise(ModelData data) {
		BlockState painted = data.get(PAINT);
		return painted != null ? painted : ModBlocks.block_steel.get().defaultBlockState();
	}

	private static BakedModel modelFor(BlockState state) {
		return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
	}

	@Override public boolean useAmbientOcclusion() { return true; }

	@Override public boolean isGui3d() { return true; }

	@Override public boolean usesBlockLight() { return true; }

	@Override public boolean isCustomRenderer() { return false; }

	/** The break-particle sprite, taken from the disguise so a painted landing breaks in its colour. */
	@Override
	public TextureAtlasSprite getParticleIcon(ModelData data) {
		return modelFor(disguise(data)).getParticleIcon(ModelData.EMPTY);
	}

	@Override
	public TextureAtlasSprite getParticleIcon() {
		return modelFor(ModBlocks.block_steel.get().defaultBlockState()).getParticleIcon();
	}

	@Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }

	@Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }

	/**
	 * Makes this model reachable from a blockstate JSON.
	 *
	 * <p>Port-invented — see the class javadoc. The geometry carries no data of its own, because
	 * everything it draws is decided per-position at meshing time, so the loader has nothing to
	 * read out of the JSON.
	 */
	public static final class Loader implements IGeometryLoader<Loader.Geometry> {

		public static final Loader INSTANCE = new Loader();

		private Loader() {}

		@Override
		public Geometry read(com.google.gson.JsonObject json,
				com.google.gson.JsonDeserializationContext context) {
			return Geometry.INSTANCE;
		}

		public static final class Geometry implements IUnbakedGeometry<Geometry> {

			static final Geometry INSTANCE = new Geometry();

			private Geometry() {}

			@Override
			public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
					Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
					ItemOverrides overrides) {
				return new EvFloorBakedModel();
			}

			@Override
			public void resolveParents(Function<net.minecraft.resources.ResourceLocation,
					net.minecraft.client.resources.model.UnbakedModel> modelGetter, IGeometryBakingContext context) {
				// Nothing to resolve: the model this draws is chosen at meshing time, so it has
				// no parents at bake time. Declared rather than left to the default so that the
				// absence is visibly deliberate.
			}
		}
	}
}
