package com.custom_hbm.sound;

import java.util.function.BiFunction;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Ported from {@code com.custom_hbm.sound.LCEAudioWrapperClient}: the handle a block entity or an
 * entity holds, forwarding to a real {@link LCEAudioDynamic}.
 *
 * <p>This is the class that makes {@link LCEAudioWrapper}'s null-object pattern work - the common
 * side calls unguarded and the client side is the only place anything is audible - and the same
 * shape as the base mod's {@code AudioWrapperClient}. It differs from it in the one way that
 * matters to a long-lived sound: <b>it remembers every setting and rebuilds the instance after a
 * stop</b>, because a 1.21.1 sound instance that has been stopped can never play again.
 *
 * <p>Upstream's {@code setLooped} and {@code setCustomAttenuation} dereference {@code sound}
 * without a null test, so a wrapper built with a null event throws from either. Nothing in the
 * addon builds one, and both are guarded here; with a real event nothing differs.
 *
 * <p>The default attenuation is {@code LINEAR} (vanilla's: audible out to sixteen blocks times the
 * volume), and a custom curve switches the instance to {@code NONE} so the two do not stack.
 */
@OnlyIn(Dist.CLIENT)
public class LCEAudioWrapperClient extends LCEAudioWrapper {

	private final SoundEvent source;
	protected final SoundSource category;
	protected float x;
	protected float y;
	protected float z;
	protected float volume = 1;
	protected float pitch = 1;
	private boolean looped = true;
	private SoundInstance.Attenuation attenuationType = SoundInstance.Attenuation.LINEAR;
	private BiFunction<Float, Double, Double> attenuationFunction = null;
	public LCEAudioDynamic sound;

	public LCEAudioWrapperClient(SoundEvent source, SoundSource cat) {
		this.source = source;
		this.category = cat;
		rebuild();
	}

	protected void rebuild() {
		if(source == null) {
			sound = null;
			return;
		}
		sound = new LCEAudioDynamic(source, category);
		sound.setPosition(x, y, z);
		sound.setVolume(volume);
		sound.setPitch(pitch);
		sound.setLooped(looped);
		sound.setAttenuation(attenuationFunction == null ? attenuationType : SoundInstance.Attenuation.NONE);
		if(attenuationFunction != null)
			sound.setCustomAttenuation(attenuationFunction);
	}

	public void setAttenuation(SoundInstance.Attenuation attenuationType) {
		this.attenuationType = attenuationType;
		if(sound != null && attenuationFunction == null)
			sound.setAttenuation(attenuationType);
	}

	@Override
	public LCEAudioWrapperClient updatePosition(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
		if(sound != null)
			sound.setPosition(x, y, z);
		return this;
	}

	@Override
	public LCEAudioWrapperClient updateVolume(float volume) {
		this.volume = volume;
		if(sound != null)
			sound.setVolume(volume);
		return this;
	}

	@Override
	public LCEAudioWrapperClient updatePitch(float pitch) {
		this.pitch = pitch;
		if(sound != null)
			sound.setPitch(pitch);
		return this;
	}

	@Override
	public float getVolume() {
		if(sound != null)
			return sound.currentVolume();
		else
			return 1;
	}

	@Override
	public float getPitch() {
		if(sound != null)
			return sound.currentPitch();
		else
			return 1;
	}

	@Override
	public LCEAudioWrapperClient startSound() {
		if(sound != null && !sound.isPlaying())
			sound.start();
		return this;
	}

	@Override
	public LCEAudioWrapperClient stopSound() {
		if(sound != null) {
			sound.stopSound();
			rebuild();
		}
		return this;
	}

	@Override
	public LCEAudioWrapperClient setLooped(boolean looped) {
		this.looped = looped;
		if(sound != null)
			sound.setLooped(looped);
		return this;
	}

	@Override
	public LCEAudioWrapperClient setCustomAttenuation(BiFunction<Float, Double, Double> attentuationFunction) {
		this.attenuationFunction = attentuationFunction;
		if(sound != null) {
			sound.setCustomAttenuation(attentuationFunction);
			sound.setAttenuation(attentuationFunction == null ? attenuationType : SoundInstance.Attenuation.NONE);
		}
		return this;
	}
}
