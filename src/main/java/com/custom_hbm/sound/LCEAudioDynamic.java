package com.custom_hbm.sound;

import java.util.function.BiFunction;

import com.leafia.LeafiaMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Ported from {@code com.custom_hbm.sound.LCEAudioDynamic}: one looping sound that sits at a point
 * and whose volume is whatever a function of the listener's distance says.
 *
 * <p>Upstream extends 1.12.2's {@code MovingSound}; {@link AbstractTickableSoundInstance} is its
 * successor, as it was for the base mod's {@code AudioDynamic} (which this is the addon's twin of,
 * and which stays unmodified - the addon never edits {@code com.hbm}, D-1001).
 *
 * <ul>
 * <li>{@code update()} is {@link #tick()}; {@code repeat} is {@code looping}; {@code attenuationType}
 *     is {@code attenuation}; {@code xPosF/yPosF/zPosF} are {@code x/y/z}, now doubles. Positions
 *     arrive as floats and are stored from them, so the precision loss upstream has is kept.
 * <li><b>{@code stop()} is {@link #stopSound()}.</b> Upstream's public {@code stop()} cannot exist
 *     here - {@code AbstractTickableSoundInstance.stop()} is {@code protected final} - so the
 *     public entry point has the wrapper's own name and calls both halves: vanilla's, which sets
 *     the stopped flag and clears {@code looping} so the engine does not restart it, and the
 *     sound manager's. <b>A stopped instance is finished for good</b>, which is why the wrapper
 *     rebuilds a fresh one after every stop, exactly as upstream's does.
 * <li>{@code intendedVolume} defaults to upstream's 10, which nothing reads: the wrapper always
 *     sets one before the sound plays.
 * <li>{@link #currentVolume()} and {@link #currentPitch()} read the instance's own fields. Vanilla's
 *     {@code getVolume()} multiplies by the resolved sound's volume and throws before the
 *     instance has been resolved, and the wrapper asks before it ever plays.
 * <li>A {@link SoundSource} is a constructor argument, as upstream's {@code SoundCategory} was.
 *     The cabin's music is {@code RECORDS} and not the {@code MUSIC} the sounds file files it
 *     under, because upstream's proxy passes {@code RECORDS} and a sound instance's own category
 *     is what the mixer slider uses.
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class LCEAudioDynamic extends AbstractTickableSoundInstance {

	public float intendedVolume = 10.0F;
	public BiFunction<Float, Double, Double> attenuationFunction = null;

	protected LCEAudioDynamic(SoundEvent loc, SoundSource cat) {
		super(loc, cat, RandomSource.create());
		this.looping = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	public void setPosition(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public void setAttenuation(SoundInstance.Attenuation type) {
		this.attenuation = type;
	}

	public void setCustomAttenuation(BiFunction<Float, Double, Double> attenuationFunction) {
		this.attenuationFunction = attenuationFunction;
	}

	public void setLooped(boolean repeat) {
		this.looping = repeat;
	}

	/** Upstream's {@code update()}: with a custom curve the volume follows the listener, otherwise it is the intended one. */
	@Override
	public void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		if(player != null && attenuationFunction != null) {
			double distance = Math.sqrt(Math.pow(x - player.getX(), 2) + Math.pow(y - player.getY(), 2)
					+ Math.pow(z - player.getZ(), 2));
			volume = attenuationFunction.apply(intendedVolume, distance).floatValue();
		} else {
			volume = intendedVolume;
		}
	}

	/** Upstream catches the {@code IllegalArgumentException} the 1.12.2 sound handler throws for an unknown event. */
	public void start() {
		try {
			Minecraft.getInstance().getSoundManager().play(this);
		} catch(IllegalArgumentException ex) {
			LeafiaMod.LOGGER.warn("Could not start {}: {}", getLocation(), ex.toString());
		}
	}

	public boolean isPlaying() {
		return Minecraft.getInstance().getSoundManager().isActive(this);
	}

	/** Upstream's {@code stop()}; renamed, see the class note. */
	public void stopSound() {
		this.stop();
		Minecraft.getInstance().getSoundManager().stop(this);
	}

	public void setVolume(float volume) {
		this.intendedVolume = volume;
		this.volume = volume;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
	}

	public float currentVolume() {
		return volume;
	}

	public float currentPitch() {
		return pitch;
	}
}
