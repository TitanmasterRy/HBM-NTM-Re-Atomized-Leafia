package com.leafia.contents.building.catwalk.railing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
 * Draws a railing by choosing, per block position, which of the .obj's named parts to emit.
 *
 * <p>Upstream: {@code .../railing/CatwalkRailingBakedModel.java}. The part-choosing logic in
 * {@link #partsFor} is upstream's {@code getQuads} body, condition for condition; everything
 * around it is different, because the two engines hand a baked model completely different
 * things.
 *
 * <h2>Why this is a custom model and not the addon's usual generated JSON</h2>
 *
 * Everywhere else in this addon a (part, texture) pair is a model and an item is a
 * {@code neoforge:composite} over several — D-1041 wrote a hundred and twelve of them. That
 * works because each of those sets is fixed. A railing's is not: it is chosen from a sixteen-bit
 * mask, four bits of blockstate and twelve of neighbour, so there are thousands of sets and no
 * JSON can enumerate them. The parts are still generated models — all thirty-two of them — and
 * this composes them at meshing time.
 *
 * <h2>Where the mask comes from</h2>
 *
 * {@link #getModelData} — the same door {@code EvFloorBakedModel} uses, and for the same reason
 * (D-1042): there is no block-level {@code getModelData} hook in NeoForge 21.1, and a railing
 * has no block entity. The {@code level} handed in is the mesh thread's snapshot of the
 * section, so reading eight neighbours out of it is safe.
 *
 * <p>Upstream computes the same thing in {@code getExtendedState} and caches it in a static map
 * with a generation counter and a world listener. None of that is here; see the note on
 * {@code CatwalkRailingBase} for why the caching is redundant rather than merely dropped.
 *
 * <h2>What is cached, and what a concurrent map is doing here</h2>
 *
 * The per-mask quad list is, exactly as upstream caches it — that is the expensive half and it
 * depends on nothing but the mask. Upstream guards its map with a monitor and says why:
 * {@code getQuads} runs on the chunk batcher threads. Same here, on a
 * {@link ConcurrentHashMap}, which says the same thing without a lock.
 */
@OnlyIn(Dist.CLIENT)
public class CatwalkRailingBakedModel implements BakedModel {

	/** The sixteen-bit render mask for one position. See {@code CatwalkRailingBase.renderMask}. */
	public static final ModelProperty<Integer> RENDER_MASK = new ModelProperty<>();

	private final String block;
	private final Map<Integer, List<BakedQuad>> quadCache = new ConcurrentHashMap<>();

	public CatwalkRailingBakedModel(String block) {
		this.block = block;
	}

	@Override
	public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
		if(!(state.getBlock() instanceof CatwalkRailingBase)) return modelData;
		return modelData.derive()
				.with(RENDER_MASK, CatwalkRailingBase.renderMask(level, pos, state))
				.build();
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
			RandomSource rand, ModelData data, @Nullable RenderType renderType) {

		// The part models are generated with automatic_culling off, so every quad they carry is
		// on the general pass; asking for a side would return nothing from them anyway.
		if(side != null || state == null) return Collections.emptyList();

		Integer masked = data.get(RENDER_MASK);
		// No model data means this is not a meshing call - an inventory or particle query, say.
		// Upstream falls back to the four arm bits alone, which draws the arms with every end
		// capped, and so does this.
		int mask = masked != null ? masked : CatwalkRailingBase.armMask(state);

		return quadCache.computeIfAbsent(mask, this::bake);
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
		return getQuads(state, side, rand, ModelData.EMPTY, null);
	}

	private List<BakedQuad> bake(int mask) {
		List<BakedQuad> quads = new ArrayList<>();
		for(String part : CatwalkRailingBase.partsFor(mask)) {
			BakedModel model = Minecraft.getInstance().getModelManager().getModel(
					ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
							LeafiaRef.MODID, "block/" + block + "_" + part.toLowerCase())));
			quads.addAll(model.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null));
		}
		return List.copyOf(quads);
	}

	/** Thin metal on the cutout pass, which is what the texture's transparency needs. */
	@Override
	public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
		return ChunkRenderTypeSet.of(RenderType.cutout());
	}

	@Override public boolean useAmbientOcclusion() { return true; }

	@Override public boolean isGui3d() { return true; }

	@Override public boolean usesBlockLight() { return true; }

	@Override public boolean isCustomRenderer() { return false; }

	@Override
	public TextureAtlasSprite getParticleIcon() {
		return Minecraft.getInstance().getModelManager().getModel(
				ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
						LeafiaRef.MODID, "block/" + block + "_post_px"))).getParticleIcon();
	}

	@Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }

	@Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }

	/**
	 * Makes this reachable from a blockstate JSON, as {@code leafia:catwalk_railing}.
	 *
	 * <p>Upstream installs its model by patching the bake result in {@code ModelBakeEvent}. The
	 * same choice as D-1042: the data-driven route instead, so the blockstate is the one place
	 * that says which model a block draws with.
	 *
	 * <p>The loader reads nothing from the JSON but the model's own name, which it needs to
	 * find the thirty-two part models — they are named after it.
	 */
	public static final class Loader implements IGeometryLoader<Loader.Geometry> {

		public static final Loader INSTANCE = new Loader();

		private Loader() {}

		@Override
		public Geometry read(com.google.gson.JsonObject json,
				com.google.gson.JsonDeserializationContext context) {
			return new Geometry();
		}

		public static final class Geometry implements IUnbakedGeometry<Geometry> {

			@Override
			public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
					Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
					ItemOverrides overrides) {
				// The model's own name minus the "block/" folder: the part models are that name
				// with "_<part>" on the end, which is the generator's rule.
				String name = context.getModelName();
				int slash = name.lastIndexOf('/');
				return new CatwalkRailingBakedModel(slash < 0 ? name : name.substring(slash + 1));
			}

			@Override
			public void resolveParents(Function<ResourceLocation,
					net.minecraft.client.resources.model.UnbakedModel> modelGetter, IGeometryBakingContext context) {
				// The part models are standalone and registered through ModelEvent
				// .RegisterAdditional, so they are baked independently and looked up by name at
				// meshing time rather than being this model's parents.
			}
		}
	}
}
