package com.leafia.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Vanilla's eight smoke frames, by 1.12.2's texture index.
 *
 * <p>1.12.2's cloud, redstone and smoke particles picked a frame from the first row of
 * {@code particles.png} with {@code setParticleTextureIndex(7 - age × 8 / life)}; 1.21.1 split that
 * row into {@code particle/generic_0.png} to {@code generic_7.png} on the particle atlas.
 * {@code ParticleMTSteam} (D-1062) established the mapping of index {@code n} to {@code generic_n},
 * and the particles here use it unchanged so every addon puff steps through its frames the same way.
 */
@OnlyIn(Dist.CLIENT)
public final class LeafiaParticleSprites {

	private LeafiaParticleSprites() {}

	public static final int FRAMES = 8;

	/** The frame for a 1.12.2 texture index, clamped into 0..7 as a stale index must not throw. */
	public static TextureAtlasSprite generic(int index) {
		int clamped = Math.max(0, Math.min(FRAMES - 1, index));
		return atlas().getSprite(ResourceLocation.withDefaultNamespace("generic_" + clamped));
	}

	/**
	 * The particle atlas, from the texture manager. {@code ModelManager.getAtlas} only knows the
	 * model atlases (blocks, banners, shields, signs, chests, beds, shulker boxes, armour trims and
	 * decorated pots) and throws a null pointer for this one: the particle engine registers its atlas
	 * with the texture manager and with nothing else.
	 */
	public static TextureAtlas atlas() {
		return (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
	}
}
