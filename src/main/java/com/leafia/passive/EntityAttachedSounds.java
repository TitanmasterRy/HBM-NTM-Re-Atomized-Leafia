package com.leafia.passive;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.llib.technical.FifthString;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Looping sounds that follow an entity: the anti-mass rifle's fan is the one producer (D-3028). The server says "this sound, at
 * this volume and pitch, on this entity, under this id" every tick it should play, and "stop" once; a client starts the loop the
 * first time it hears an id, moves it with the entity every client tick, and drops it when told to stop, when the entity leaves the
 * client's level, or when two seconds pass without a word.
 *
 * <p>Ported from {@code com.leafia.passive.EntityAttachedSounds}. The packet is upstream's on the wire (the entity's UUID, the id,
 * a play flag, then the sound's registry name, volume and pitch); what moved:
 * <ul>
 * <li>a decoder reads every value <b>eagerly</b> and hands back what to do (the {@code LeafiaCustomPacketEncoder} contract), where
 *     upstream's {@code run} read the rest of the buffer inside a scheduled task;</li>
 * <li>the loop is {@link LCEAudioProxy}'s, which is upstream's {@code AddonBase.proxy.getLoopedSoundStartStop};</li>
 * <li>the bookkeeping is common code over a level and its entities, so a test drives it on a server level, and only the
 *     {@link ClientOnly} wrapper names the client's level;</li>
 * <li>"another dimension" is another {@link Level} object, which is what upstream's dimension id compared.</li>
 * </ul>
 */
public class EntityAttachedSounds {

	/** How long a sound lives without a packet, in milliseconds: upstream's {@code lastTimestamp + 2000}. */
	public static final long STALE_AFTER = 2000L;

	public static long serverId = 0;
	public static final List<EntityAttachedSound> sounds = new ArrayList<>();

	/** One client tick: drop every stale sound (stopping it) and move the rest with their entities. */
	public static void update(Level level) {
		int i = 0;
		while(i < sounds.size()) {
			EntityAttachedSound snd = sounds.get(i);
			if(snd.isStale(level)) {
				if(snd.wrapper != null)
					snd.wrapper.stopSound();
				sounds.remove(snd);
				continue;
			}
			snd.update();
			i++;
		}
	}

	public static class EntityAttachedSound {
		@Nullable public LCEAudioWrapper wrapper;
		public long id = 0;
		public long lastTimestamp = 0;
		public Entity target;

		public EntityAttachedSound(Entity target, long id) {
			this.target = target;
			this.id = id;
			lastTimestamp = System.currentTimeMillis();
		}

		public boolean isStale(Level level) {
			return target.level() != level || System.currentTimeMillis() > lastTimestamp + STALE_AFTER;
		}

		public void update() {
			if(wrapper != null)
				wrapper.updatePosition((float) target.getX(), (float) target.getY(), (float) target.getZ());
		}
	}

	/** Play (or keep playing, at this volume and pitch) or stop one entity's sound. Server to client. */
	public static class AttachedSoundPacket extends AttachedSoundPacketBase {
		public boolean play = false;
		@Nullable public SoundEvent evt;
		public float volume;
		public float pitch;

		public AttachedSoundPacket() {
		}

		/** The stop form. */
		public AttachedSoundPacket(Entity target, long id) {
			super(target, id);
		}

		public AttachedSoundPacket(Entity target, long id, SoundEvent evt, float volume, float pitch) {
			super(target, id);
			play = true;
			this.evt = evt;
			this.volume = volume;
			this.pitch = pitch;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			super.encode(buf);
			buf.writeBoolean(play);
			if(play) {
				buf.writeFifthString(new FifthString(evt.getLocation().toString()));
				buf.writeFloat(volume);
				buf.writeFloat(pitch);
			}
		}

		@Override
		public Consumer<EntityAttachedSound> readBody(LeafiaBuf buf) {
			boolean play = buf.readBoolean();
			if(play) {
				ResourceLocation name = ResourceLocation.tryParse(buf.readFifthString().toString());
				SoundEvent evt = name == null ? null : BuiltInRegistries.SOUND_EVENT.get(name);
				float volume = buf.readFloat();
				float pitch = buf.readFloat();
				return snd -> {
					if(evt == null) return;
					if(snd.wrapper == null) {
						snd.wrapper = LCEAudioProxy.getLoopedSoundStartStop(snd.target.level(), evt, null, null, SoundSource.PLAYERS,
								(float) snd.target.getX(), (float) snd.target.getY(), (float) snd.target.getZ(), volume, pitch);
						snd.wrapper.startSound();
					}
					if(snd.wrapper.getVolume() != volume)
						snd.wrapper.updateVolume(volume);
					if(snd.wrapper.getPitch() != pitch)
						snd.wrapper.updatePitch(pitch);
				};
			}
			return snd -> {
				if(snd.wrapper != null)
					snd.wrapper.stopSound();
				sounds.remove(snd);
			};
		}
	}

	public abstract static class AttachedSoundPacketBase implements LeafiaCustomPacketEncoder {
		public long id = 0;
		@Nullable public Entity target;

		public AttachedSoundPacketBase() { }

		public AttachedSoundPacketBase(Entity target, long id) {
			this.target = target;
			this.id = id;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			UUID uuid = target.getUUID();
			buf.writeLong(uuid.getMostSignificantBits());
			buf.writeLong(uuid.getLeastSignificantBits());
			buf.writeLong(id);
		}

		/** Reads what follows the entity and the id, and returns what to do to that entity's sound. */
		public abstract Consumer<EntityAttachedSound> readBody(LeafiaBuf buf);

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			UUID uuid = new UUID(buf.readLong(), buf.readLong());
			long id = buf.readLong();
			Consumer<EntityAttachedSound> body = readBody(buf);
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND && FMLEnvironment.dist.isClient()) ClientOnly.receive(uuid, id, body);
			};
		}

		/**
		 * Upstream's {@code doRun}: find the entity among {@code entities}, find or start the sound with this id, stamp it, act.
		 * Nothing happens when the entity is not there, as upstream.
		 */
		public static void apply(Iterable<Entity> entities, UUID uuid, long id, Consumer<EntityAttachedSound> body) {
			for(Entity entity : entities) {
				if(entity.getUUID().equals(uuid)) {
					EntityAttachedSound snd = null;
					for(EntityAttachedSound sound : sounds) {
						if(sound.id == id) {
							snd = sound;
							break;
						}
					}
					if(snd == null) {
						snd = new EntityAttachedSound(entity, id);
						sounds.add(snd);
					}
					snd.lastTimestamp = System.currentTimeMillis();
					body.accept(snd);
					break;
				}
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientOnly {
		static void receive(UUID uuid, long id, Consumer<EntityAttachedSound> body) {
			net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
			if(level != null) AttachedSoundPacketBase.apply(level.entitiesForRendering(), uuid, id, body);
		}
	}
}
