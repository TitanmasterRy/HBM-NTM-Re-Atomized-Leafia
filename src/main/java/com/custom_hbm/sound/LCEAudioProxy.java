package com.custom_hbm.sound;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon's client audio proxy: where common code asks for a looping sound and the client, if
 * there is one, answers with something audible.
 *
 * <p>This stands in for upstream's {@code AddonBase.proxy.getLoopedSoundStartStop}, one method on
 * a client/server proxy pair (D-1037 is the record of why the elevator radio waited on it). A
 * proxy pair is how 1.12.2 kept a dedicated server from loading client classes; the 1.21.1 way
 * is a hook that the client installs and the server never does, and that is all this is.
 *
 * <ul>
 * <li>On a dedicated server, and on a GameTest server, {@link #factory} stays null and every
 *     request answers with {@link LCEAudioWrapper}'s no-op base - which is what upstream's server
 *     proxy meant by returning null, minus the null that every caller would otherwise have had to
 *     test for.
 * <li>The client installs {@link #factory} once, from {@code LeafiaClient.clientSetup}, pointing
 *     at {@link LCEAudioWrapperClientStartStop}. This class names none of the client types, so it
 *     is safe to load anywhere; the factory's body is the only part that is not.
 * <li>A test installs a recording factory for one test and puts the old one back.
 * </ul>
 *
 * <p>The sound is <b>not</b> started by the request. As upstream, the caller starts it:
 * {@code music.startSound()}.
 */
public final class LCEAudioProxy {

	private LCEAudioProxy() {}

	/** Upstream's {@code getLoopedSoundStartStop} parameter list, in its order. */
	@FunctionalInterface
	public interface Factory {
		LCEAudioWrapper create(Level world, SoundEvent sound, @Nullable SoundEvent start, @Nullable SoundEvent stop,
				SoundSource cat, float x, float y, float z, float volume, float pitch);
	}

	/** Null until a client installs one. Only ever written on the client's main thread at start-up, or by a test. */
	@Nullable
	public static volatile Factory factory = null;

	/**
	 * A looping sound with an optional start clip and an optional stop clip, at a position.
	 *
	 * @return the client's wrapper, or a wrapper that does nothing when there is no client
	 */
	public static LCEAudioWrapper getLoopedSoundStartStop(Level world, SoundEvent sound, @Nullable SoundEvent start,
			@Nullable SoundEvent stop, SoundSource cat, float x, float y, float z, float volume, float pitch) {
		Factory current = factory;
		if(current == null) return new LCEAudioWrapper();
		return current.create(world, sound, start, stop, cat, x, y, z, volume, pitch);
	}

	/**
	 * Upstream's {@code LeafiaClientProxy.getLoopedSoundStartStop}, body for body: build the
	 * wrapper, then set its position and pitch. Behind its own class because it is the one place
	 * that names the client types, and so that a server never resolves them.
	 */
	@OnlyIn(Dist.CLIENT)
	public static final class Client {

		private Client() {}

		public static void install() {
			factory = Client::build;
		}

		private static LCEAudioWrapper build(Level world, SoundEvent sound, SoundEvent start, SoundEvent stop,
				SoundSource cat, float x, float y, float z, float volume, float pitch) {
			LCEAudioWrapperClientStartStop audio = new LCEAudioWrapperClientStartStop(world, sound, start, stop, volume, cat);
			audio.updatePosition(x, y, z);
			audio.updatePitch(pitch);
			return audio;
		}
	}
}
