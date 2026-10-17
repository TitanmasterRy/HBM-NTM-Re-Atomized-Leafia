package com.custom_hbm.sound;

import java.util.function.BiFunction;

/**
 * Ported from `com.custom_hbm.sound.LCEAudioWrapper`.
 *
 * <p>A handle on a playing sound - move it, change its volume or pitch, stop it. Every method
 * here does nothing and returns {@code this}, and that is deliberate upstream: a sound is a
 * client-side thing, so the server wants a handle it can call into and have ignored. The
 * client subclass overrides them.
 *
 * <p>No Minecraft API at all, so it came across unchanged. The client half is built on
 * 1.12.2's {@code ISound} and is deferred with the rest of the sound layer.
 */
public class LCEAudioWrapper {
	
	public LCEAudioWrapper updatePosition(float x,float y,float z) { return this; }
	
	public LCEAudioWrapper updateVolume(float volume) { return this; }
	
	public LCEAudioWrapper updatePitch(float pitch) { return this; }
	
	public float getVolume() { return 0F; }
	
	public float getPitch() { return 0F; }
	
	public LCEAudioWrapper startSound() { return this; }
	
	public LCEAudioWrapper stopSound() { return this; }

	public LCEAudioWrapper setCustomAttenuation(BiFunction<Float,Double,Double> attentuationFunction) { return this; }

	public LCEAudioWrapper setLooped(boolean looped) { return this; }
}