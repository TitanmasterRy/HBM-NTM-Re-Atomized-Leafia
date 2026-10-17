package com.custom_hbm.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Ported from {@code com.custom_hbm.sound.LCEAudioWrapperClientStartStop}: a looping sound with a
 * one-shot clip when it starts and another when it stops.
 *
 * <p>Both clips are optional and played at the wrapper's own position, volume and pitch through
 * {@code Level.playLocalSound} (upstream's {@code world.playSound(x, y, z, ..., false)}: the
 * client's own level, no distance delay). <b>The stop clip only plays if the loop was actually
 * playing</b> - a sound that never started, or whose engine failed to open, ends in silence.
 *
 * <p>Upstream's class is not annotated client-only, which is harmless there because only the
 * client proxy builds it; it extends a client-only class here, so it is annotated.
 */
@OnlyIn(Dist.CLIENT)
public class LCEAudioWrapperClientStartStop extends LCEAudioWrapperClient {

	public SoundEvent start;
	public SoundEvent stop;
	public Level world;

	public LCEAudioWrapperClientStartStop(Level world, SoundEvent source, SoundEvent start, SoundEvent stop,
			float vol, SoundSource cat) {
		super(source, cat);
		updateVolume(vol);
		this.world = world;
		this.start = start;
		this.stop = stop;
	}

	@Override
	public LCEAudioWrapperClientStartStop startSound() {
		super.startSound();
		if(start != null)
			world.playLocalSound(x, y, z, start, category, volume, getPitch(), false);
		return this;
	}

	@Override
	public LCEAudioWrapperClientStartStop stopSound() {
		if(stop != null && sound != null && sound.isPlaying())
			world.playLocalSound(x, y, z, stop, category, volume, getPitch(), false);
		super.stopSound();
		return this;
	}
}
