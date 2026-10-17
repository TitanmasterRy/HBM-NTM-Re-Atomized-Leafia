package com.leafia.contents.gear;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.custom_hbm.util.LCETuple.Pair;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.dev.custompacket.PacketGuard;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.dev.optimization.bitbyte.LeafiaBuf;
import com.leafia.init.LeafiaSounds;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A weapon that locks on: the client picks a target in a cone in front of the player and beeps as the lock firms up, and the
 * server asks for that target when the weapon fires. The only implementor is the anti-mass rifle (D-3028).
 *
 * <p>Ported from {@code com.leafia.contents.gear.ILockonWeapon}. The protocol is upstream's: when the rifle starts its charge the
 * server sends an empty {@link GetLockonPacket}, the client answers with its target if the lock has run for 32 ticks (16 beeps) and
 * with nothing otherwise, and the server keeps the answer for a minute against the player, where {@link #getLoadedLockon} finds
 * it when the beam is spawned eleven ticks later.
 *
 * <p>What is this port's: the answer is <b>validated</b> (D-1094) - the sender must hold a lock-on weapon in the main hand and
 * the target must be a living entity in the sender's level, other than the sender, within the beam's 250-block reach - where
 * upstream stored any UUID any client named. A refused answer clears the stored lock, as an empty one does. The stale-entry sweep
 * that upstream ran every server tick is done when a lock is read instead (an entry older than {@link #LOCK_LIFE} is dropped), and
 * once a second from {@link com.leafia.contents.gear.combat.guns.GunInit}'s server tick listener.
 */
public interface ILockonWeapon {

	/** How long a lock the client reported is kept, in milliseconds: upstream's sweep, {@code getB() + 60_000}. */
	long LOCK_LIFE = 60_000L;

	/** How far a reported target may be from the shooter: the beam's own reach ({@code EntityBulletBeamBase.RANGE}, 250). */
	double MAX_LOCK_RANGE = 250D;

	boolean canLockon(ItemStack stack);

	/** Half the side of the square, at unit depth, that a target must sit in to be picked: upstream's tangent bound. */
	double maxInclination(ItemStack stack);

	/** Whether the client keeps its lock as it is this tick (the rifle while it charges). */
	default boolean freezeTick(ItemStack stack) {
		return false;
	}

	/** The target the player's client last reported, or null; an entry past its minute is dropped. */
	@Nullable
	static Entity getLoadedLockon(@Nullable Player player) {
		if(player == null) return null;
		Pair<Entity, Long> pair = GetLockonPacket.lockons.get(player);
		if(pair == null) return null;
		if(System.currentTimeMillis() > pair.getB() + LOCK_LIFE) {
			GetLockonPacket.lockons.remove(player);
			return null;
		}
		return pair.getA();
	}

	/** Upstream's server-tick sweep: every lock older than {@link #LOCK_LIFE} is dropped. */
	static void sweepLockons() {
		long now = System.currentTimeMillis();
		GetLockonPacket.lockons.entrySet().removeIf(e -> now > e.getValue().getB() + LOCK_LIFE);
	}

	/**
	 * Both directions of the lock-on question. Server to client it carries nothing (a false flag); client to server it carries the
	 * target's UUID, or a false flag for none.
	 */
	class GetLockonPacket implements LeafiaCustomPacketEncoder {

		/** Each player's last reported target and when it was reported. Server side. */
		public static final Map<Player, Pair<Entity, Long>> lockons = new HashMap<>();

		@Nullable Entity target;

		public GetLockonPacket() { }

		public GetLockonPacket(@Nullable Entity target) {
			this.target = target;
		}

		@Override
		public void encode(LeafiaBuf buf) {
			// doesnt matter for server->client packet as it would always have no target set
			if(target != null) {
				buf.writeBoolean(true);
				UUID uuid = target.getUUID();
				buf.writeLong(uuid.getMostSignificantBits());
				buf.writeLong(uuid.getLeastSignificantBits());
			} else
				buf.writeBoolean(false);
		}

		@Override
		public boolean acceptsServerbound() {
			return true;
		}

		@Nullable
		@Override
		public Consumer<IPayloadContext> decode(LeafiaBuf buf) {
			boolean has = buf.readBoolean();
			UUID uuid = has ? new UUID(buf.readLong(), buf.readLong()) : null;
			return ctx -> {
				if(ctx.flow() == PacketFlow.CLIENTBOUND) {
					Entity answer = LockonHandler.timer >= 32 ? LockonHandler.target : null;
					LeafiaCustomPacket.__start(new GetLockonPacket(answer)).__sendToServer();
					return;
				}
				if(ctx.player() instanceof ServerPlayer player) receive(player, uuid);
			};
		}

		/** The server's half: store the reported target if it passes the checks, otherwise clear the player's lock. */
		public static void receive(ServerPlayer player, @Nullable UUID uuid) {
			if(uuid == null) {
				lockons.remove(player);
				return;
			}
			Entity entity = player.serverLevel().getEntity(uuid);
			String why = whyRefused(player, entity);
			if(why != null) {
				PacketGuard.reject("GetLockonPacket", player, why);
				lockons.remove(player);
				return;
			}
			lockons.put(player, new Pair<>(entity, System.currentTimeMillis()));
		}

		/** Null when a lock on {@code entity} is believable for {@code player}; otherwise why not. */
		@Nullable
		public static String whyRefused(Player player, @Nullable Entity entity) {
			if(!(player.getMainHandItem().getItem() instanceof ILockonWeapon)) return "no lock-on weapon in hand";
			if(entity == null || !entity.isAlive()) return "no such entity";
			if(entity == player) return "a lock on oneself";
			if(entity.level() != player.level()) return "another level";
			if(entity.distanceToSqr(player) > MAX_LOCK_RANGE * MAX_LOCK_RANGE) return "beyond the beam's reach";
			return null;
		}
	}

	/**
	 * The client's lock: the target, whether it is held, and how many ticks it has been held. Ticked once a client tick by
	 * {@code LeafiaSednaClient}. Upstream's body with the 1.21.1 names; the cone is a square at unit depth, the pick is the entity
	 * nearest to where the current target sits projected to the candidate's depth (so the lock sticks), and an entity behind a
	 * block is never picked.
	 */
	class LockonHandler {
		@Nullable public static Entity target = null;
		public static boolean locked = false;
		public static int timer = 0;

		public static void handleLockon(Player player, Level world, Iterable<Entity> entities) {
			ItemStack stack = player.getMainHandItem();
			if(stack.getItem() instanceof ILockonWeapon weapon2 && weapon2.freezeTick(stack)) return;
			if(stack.getItem() instanceof ILockonWeapon weapon && weapon.canLockon(stack)) {
				Vec3 plrVec = new Vec3(player.getX(), player.getEyeY(), player.getZ());
				FiaMatrix face = new FiaMatrix(plrVec, plrVec.add(player.getLookAngle()));

				Entity closest = null;
				Entity pivot = player;
				if(target != null) pivot = target;
				Vec3 pivotVec = new Vec3(pivot.getX(), pivot.getEyeY(), pivot.getZ());
				FiaMatrix pivotRelative = face.toObjectSpace(new FiaMatrix(pivotVec));
				double closestDist = Double.MAX_VALUE;

				boolean currentValid = false;

				for(Entity entity : entities) {
					if(entity == player) continue;
					Vec3 tgtVec = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
					FiaMatrix relative = face.toObjectSpace(new FiaMatrix(tgtVec));
					if(relative.getZ() < 0) {
						double depth = -relative.getZ();
						double x = relative.getX() / depth;
						double y = relative.getY() / depth;
						double maxInclination = weapon.maxInclination(stack);
						if(Math.abs(x) <= maxInclination && Math.abs(y) <= maxInclination) {
							HitResult res = world.clip(new ClipContext(plrVec, tgtVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
							if(res != null && res.getType() != HitResult.Type.MISS) continue;

							if(entity == target) currentValid = true;

							double dist = tgtVec.distanceTo(face.translate(pivotRelative.getX(), pivotRelative.getY(), -depth).position);
							if(dist < closestDist) {
								closestDist = dist;
								closest = entity;
							}
						}
					}
				}
				if(!currentValid) {
					target = closest;
					timer = 0;
				}

				if(target != null) {
					if(timer % 2 == 0) {
						int t = timer / 2;
						if(t < 8) {
							if(t % 2 == 0)
								beep(player, world, LeafiaSounds.lockon0.get());
						} else if(t < 16)
							beep(player, world, LeafiaSounds.lockon1.get());
						else if(t == 16)
							beep(player, world, LeafiaSounds.lockon2.get());
					}
					timer++;
				}
			} else
				target = null;
		}

		public static void beep(Player player, Level world, SoundEvent evt) {
			world.playSound(player, (float) player.getX(), (float) player.getY(), (float) player.getZ(), evt, SoundSource.MASTER, 0.5f, 1);
		}
	}
}
