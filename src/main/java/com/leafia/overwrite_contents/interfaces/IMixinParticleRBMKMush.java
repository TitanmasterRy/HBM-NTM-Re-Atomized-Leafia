package com.leafia.overwrite_contents.interfaces;

import com.hbm.particle.ParticleRBMKMush;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The pink RBMK mushroom cloud. Client only.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinParticleRBMKMush.java}, implemented by
 * {@code MixinParticleRBMKMush}, whose {@code @Overwrite} of {@code renderParticle} drew the strip with
 * {@code glColor(1, isPink ? 0 : 1, 1, 1)}. The base mod's cloud has a tint seam,
 * {@link ParticleRBMKMush#setTint} (D-2197), which draws white unless told otherwise, so pink is the tint
 * {@code (1, 0, 1)} and the interface becomes two statics over it (D-3018).
 */
@OnlyIn(Dist.CLIENT)
public interface IMixinParticleRBMKMush {

	static boolean isPink(ParticleRBMKMush mush) {
		float[] tint = mush.getTint();
		return tint[0] == 1F && tint[1] == 0F && tint[2] == 1F;
	}

	static void setPink(ParticleRBMKMush mush, boolean value) {
		mush.setTint(1F, value ? 0F : 1F, 1F);
	}
}
