package com.leafia.overwrite_contents.siren;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.ControllableLifecycle;
import com.leafia.contents.machines.controlpanel.core.IControllable;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityMachineSiren;
import com.hbm.items.machine.ItemCassette.SoundKind;
import com.hbm.items.machine.ItemCassette.TrackType;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.container_utility.LeafiaPacketReceiver;
import com.leafia.init.LeafiaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The siren overhaul: the base mod's siren, sounding through the addon's own audio and packet.
 *
 * <p>Upstream: {@code MixinTileEntityMachineSiren}, an {@code @Overwrite} of the siren's {@code update} plus a
 * {@code LeafiaPacketReceiver} on the block entity. Here it is a subclass the base {@code MachineSiren} makes
 * through {@code BlockEntityOverrides} while {@code overhauls.sirenOverhaul} is on (D-1002, off), on the protected
 * {@code (type, pos, state)} constructor and the overridable {@code getCurrentType} that M8a added (D-2201).
 *
 * <h2>The server, every tick</h2>
 * <ul>
 * <li>No cassette: the base mod's "off" {@code TESirenPacket}, every tick (upstream keeps that one line).</li>
 * <li>A looping track: the addon packet, to every player within 8,192 blocks, every tick: the track (key 0), looped
 *     (key 1) and whether the block is powered (key 2).</li>
 * <li>Any other track: on the rising edge of the signal, track / not looped / on. The falling edge sends nothing: the base
 *     mod lets a one-shot track play to its end, and so does this (D-3076; upstream's overhaul cut it off).</li>
 * </ul>
 *
 * <h2>The client</h2>
 * One looping sound per siren at its centre, made when the track changes, whose volume falls off in a straight line
 * from {@code min(volume, 1)} at 6 blocks to nothing at {@code 6 + 1.5 x volume} - the track's volume is 50 to 500,
 * so a siren is heard from 81 to 756 blocks; the base mod's is at full volume to half its volume and gone at the
 * volume. Key 1 sets looping, key 2 starts and stops it.
 *
 * <h2>Upstream's behaviour, kept (rule 4)</h2>
 * <ol>
 * <li>(Fixed, D-3076) Taking the cassette out of a looping siren left it sounding, because the "off" that followed was the
 *     base mod's packet; the addon's own "off" is sent once now.</li>
 * <li>The packet is sent every tick to everyone within 8,192 blocks, which is what upstream calls
 *     {@code __sendToClients(8192)}.</li>
 * </ol>
 *
 * <p>Not ported: the Computronics speaker mode ({@code IMixinTileEntitySiren}'s {@code speakerMode} and
 * {@code sounders}, {@code MixinMachineSiren}'s refusal to open), D by the completion map; the control panel's
 * {@code ctrlActive} (D-1093 #8).
 */
public class LeafiaSirenTE extends BlockEntityMachineSiren implements LeafiaPacketReceiver, IControllable {

	/** Upstream's {@code __sendToClients(8192)}. */
	public static final double PACKET_RANGE = 8192;
	/** Upstream's attenuation: full to 6 blocks. */
	public static final double MIN_DIST = 6;

	public static final byte KEY_TRACK = 0;
	public static final byte KEY_LOOPED = 1;
	public static final byte KEY_PLAYING = 2;

	public LCEAudioWrapper audio = null;
	public int playingType = 0;
	public boolean playing = false;
	/** Whether the last packet told the addon's sound to play (D-3076). */
	boolean addonSounding = false;
	/** Client: the last looping flag received, so a one-shot is started again on every rising edge. */
	boolean looped = false;

	public LeafiaSirenTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public LeafiaSirenTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.SIREN.get(), pos, state);
	}

	/** Upstream's attenuation: {@code min(vol, 1) x (1 - clamp((d - 6) / (1.5 vol), 0, 1))}. */
	public static double attenuation(int vol, double dist) {
		double minDist = MIN_DIST;
		double maxDist = minDist + vol * 1.5;
		double maxVolume = Math.min(vol, 1);
		double linear = Mth.clamp((dist - minDist) / (maxDist - minDist), 0, 1);
		return maxVolume * Math.pow(1 - linear, 1);
	}

	@Override
	public double affectionRange() {
		return 0;
	}

	@Override
	public String getPacketIdentifier() {
		return "SIREN";
	}

	@Override
	public void onReceivePacketLocal(byte key, Object value) {
		switch(key) {
			case KEY_TRACK -> {
				if(playingType != (int) value) {
					TrackType type = TrackType.getEnum((int) value);
					if(audio != null)
						audio.stopSound();
					if(type == TrackType.NULL || type.getSound() == null)
						audio = null;
					else {
						int vol = type.getVolume();
						audio = LCEAudioProxy.getLoopedSoundStartStop(level, type.getSound(), null, null, SoundSource.BLOCKS,
								worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f, worldPosition.getZ() + 0.5f, vol, 1)
								.setCustomAttenuation((intended, dist) -> attenuation(vol, dist));
					}
					playing = false;
					playingType = type.ordinal();
				}
			}
			case KEY_LOOPED -> {
				looped = (boolean) value;
				if(audio != null)
					audio.setLooped((boolean) value);
			}
			case KEY_PLAYING -> {
				if(audio != null) {
					if((boolean) value) {
						if(!playing || !looped) // a one-shot is not cut off any more, so each rising edge starts it again (D-3076)
							audio.startSound();
						playing = true;
					} else {
						if(playing)
							audio.stopSound();
						playing = false;
					}
				}
			}
			default -> { }
		}
	}

	@Override
	public void onReceivePacketServer(byte key, Object value, Player plr) { }

	@Override
	public void onPlayerValidate(Player plr) { }

	/** Upstream's {@code @Overwrite update()}, the server half (the client's is the packet above). */
	@Override
	public void updateEntity() {
		if(level.isClientSide) return;

		TrackType currentType = getCurrentType();
		int id = currentType.ordinal();

		if(currentType == TrackType.NULL) {
			sendState(id, false);
			// D-3076: the base's "off" is not heard by the addon's sound, so a looping siren whose cassette was taken out went on sounding
			if(addonSounding) {
				addonSounding = false;
				sendSiren(null, null, false);
			}
			return;
		}

		// ctrlActive (the control panel, D-2275) is OR-ed with the redstone signal by the base's isActive()
		boolean active = isActive();

		if(currentType.getType() == SoundKind.LOOP) {
			addonSounding = true;
			sendSiren(id, true, active);
		} else {
			if(!lock && active) {
				lock = true;
				addonSounding = true;
				sendSiren(id, false, true);
			}
			if(lock && !active) {
				// D-3076: the base mod lets a one-shot track play to its end; upstream's overhaul cut it off here
				lock = false;
			}
		}
	}

	/** One addon packet to every player within {@link #PACKET_RANGE}: the track and looping when given, and whether it plays. */
	protected void sendSiren(Integer track, Boolean looped, boolean playing) {
		LeafiaPacket.Builder packet = LeafiaPacket._start(this);
		if(track != null) packet.__write(KEY_TRACK, track);
		if(looped != null) packet.__write(KEY_LOOPED, looped);
		packet.__write(KEY_PLAYING, playing).__sendToClients(PACKET_RANGE);
	}

	private void stopAudio() {
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void setRemoved() {
		ControllableLifecycle.leave(this);
		stopAudio();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		ControllableLifecycle.leave(this);
		stopAudio();
		super.onChunkUnloaded();
	}

	// ---- the Custom Control Panel (CCP-RETROFIT, D-3069) ----

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList("siren_set_state");
	}

	/** {@code siren_set_state}: sets the base siren's {@code ctrlActive} (D-2275), which {@code isActive()} ORs with redstone. */
	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals("siren_set_state")) {
			DataValue on = e.vars.get("isOn");
			if(on != null)
				ctrlActive = on.getBoolean();
		}
	}

	@Override public BlockPos getControlPos() { return worldPosition; }
	@Override public Level getControlWorld() { return level; }

	@Override
	public void onLoad() {
		super.onLoad();
		ControllableLifecycle.join(this);
	}
}
