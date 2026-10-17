package com.leafia.contents.gear.combat.guns.am_rifle;

import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.hbm.items.weapon.sedna.BulletConfig;
import com.hbm.items.weapon.sedna.GunConfig;
import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import com.hbm.items.weapon.sedna.Receiver;
import com.hbm.items.weapon.sedna.factory.GunAnimSupport;
import com.hbm.items.weapon.sedna.mags.IMagazine;
import com.hbm.items.weapon.sedna.mods.XWeaponModManager;
import com.hbm.render.anim.AnimationEnums.GunAnimation;
import com.hbm.render.anim.BusAnimation;
import com.hbm.render.anim.BusAnimationKeyframe.IType;
import com.hbm.render.anim.BusAnimationSequence;
import com.leafia.contents.gear.ILockonWeapon;
import com.leafia.contents.gear.ILockonWeapon.GetLockonPacket;
import com.leafia.contents.gear.combat.guns.GunInit;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacketEncoder;
import com.leafia.init.LeafiaSounds;
import com.leafia.passive.EntityAttachedSounds.AttachedSoundPacket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The anti-mass rifle, "Cazahna" (D-3028): a sedna gun that charges for eleven ticks, then throws one rainbow beam that deletes
 * whatever it passes through, then needs ten seconds while its fan spins down before it will charge again. With the refractor fitted
 * the beam aims itself at whatever the player's client had locked on.
 *
 * <p>Ported from {@code com.leafia.contents.gear.combat.guns.am_rifle.AMRifle}. Upstream's private state lives in the stack's tag
 * under its own keys, and keeps them: {@code leafia_state} (0 ready, 1 charging, 2 fired), {@code leafia_timer} (ticks since the
 * shot, to 200), {@code leafia_fan} (0 to {@link #fanDuration}), {@code leafia_rot} and {@code leafia_prev_rot} (the fan's angle, for
 * the renderer) and {@code leafia_soundId} (which looping sound the fan is). Here that tag is the stack's custom data, where the base
 * gun keeps its own keys.
 *
 * <p>The cycle: a primary press while idle and ready plays {@code CYCLE}, puts the gun on an eleven-tick cooldown, marks it charging
 * and asks the shooter's client for its lock ({@link GetLockonPacket}). The orchestra plays the charge sound on the cycle's first tick
 * and on its eleventh plays the fire sound and calls the receiver's fire, which is {@link #fire}: one {@link AMRifleBeam} of 200
 * (100 with the refractor), aimed at the reported lock if there is one, and one point of wear. Firing sets the state to 2, and the
 * fan spins up over 50 ticks while the state counts 200 ticks back to ready, then spins down; its sound starts above a tenth of full
 * speed and is stopped once as it passes that tenth on the way down.
 *
 * <p>What moved: {@code onUpdate} is {@code inventoryTick}; packets go only to players who have the addon's channel (a test's mock
 * player has none, and a send to it throws out of the tick, D-3000); upstream's {@code Lego.spawnBullet(world, runnable)} (NTM-CE's
 * deferral helper, not in this base) is the plain spawn.
 */
public class AMRifle extends ItemGunBaseNT implements ILockonWeapon {

	public static final String KEY_STATE = "leafia_state";
	public static final String KEY_TIMER = "leafia_timer";
	public static final String KEY_FAN = "leafia_fan";
	public static final String KEY_ROT = "leafia_rot";
	public static final String KEY_PREV_ROT = "leafia_prev_rot";
	public static final String KEY_SOUND_ID = "leafia_soundId";

	/** How many ticks the cycle charges before the beam: upstream's {@code setTimer(stack, index, 11)} and its orchestra's {@code timer == 11}. */
	public static final int CHARGE_TICKS = 11;
	/** How long the fired state lasts: {@code timestamp > 200}. */
	public static final int COOLDOWN_TICKS = 200;

	public AMRifle(Properties properties, WeaponQuality quality, GunConfig... cfg) {
		super(properties, quality, cfg);
	}

	@SuppressWarnings("incomplete-switch") public static BiFunction<ItemStack, GunAnimation, BusAnimation> LAMBDA_AMRIFLE = (stack, type) -> {
		int amount = GunAnimSupport.ammo(stack);
		return switch(type) {
			case EQUIP -> new BusAnimation()
					.addBus("EQUIP", new BusAnimationSequence().addPos(60, 0, 0, 0).addPos(0, 0, 0, 500, IType.SIN_DOWN));
			case CYCLE -> new BusAnimation()
					.addBus("RECOIL", new BusAnimationSequence().addPos(0, 0, 0, 550).addPos(0, 0, -0.5, 50, IType.SIN_DOWN).addPos(0, 0, 0, 150, IType.SIN_FULL))
					.addBus("CYCLE", new BusAnimationSequence().addPos(0, 0, 0, 550).addPos(0, 0, 0, 150).addPos(0, 0, 22.5, 350))
					.addBus("COUNT", new BusAnimationSequence().addPos(0, 0, 0, 550).addPos(amount, 0, 0, 0))
					.addBus("CHARGE", new BusAnimationSequence().addPos(0, 0, -0.5, 550, IType.SIN_UP).addPos(0, 0, 0, 30, IType.SIN_DOWN));
			default -> null;
		};
	};

	public static void fire(ItemStack stack, LambdaContext ctx) {
		LivingEntity entity = ctx.entity;
		Player player = ctx.getPlayer();
		int index = ctx.configIndex;
		Receiver primary = ctx.config.getReceivers(stack)[0];
		@SuppressWarnings({ "rawtypes", "unused" }) IMagazine mag = primary.getMagazine(stack);
		BulletConfig config = GunInit.am_beam;

		Vec3 offset = ItemGunBaseNT.getIsAiming(stack) ? primary.getProjectileOffsetScoped(stack) : primary.getProjectileOffset(stack);
		double forwardOffset = offset.x;
		double heightOffset = offset.y;
		double sideOffset = offset.z;
		Level world = entity.level();
		setByte(stack, KEY_STATE, (byte) 2);
		setInt(stack, KEY_TIMER, 0);

		float dmg = GunInit.AM_DAMAGE;
		if(XWeaponModManager.hasUpgrade(stack, 0, XWeaponModManager.ID_LAS_SHOTGUN))
			dmg /= 2;
		AMRifleBeam mk4 = new AMRifleBeam(entity, ILockonWeapon.getLoadedLockon(player), config, dmg, sideOffset, heightOffset, forwardOffset);
		world.addFreshEntity(mk4);

		ItemGunBaseNT.setWear(stack, index, Math.min(ItemGunBaseNT.getWear(stack, index) + config.wear, ctx.config.getDurability(stack)));
	}

	public static int fanDuration = 50;
	public static int fanDiv = 5;
	public static double fanRPS = 360;

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean isHeld) {
		super.inventoryTick(stack, world, entity, slot, isHeld);
		if(!world.isClientSide) tickFan(stack, (ServerLevel) world, entity);
	}

	/** Upstream's server half of {@code onUpdate}: the fired state's countdown, the fan and its sound. */
	public static void tickFan(ItemStack stack, ServerLevel world, Entity entity) {
		CompoundTag tag = tag(stack);
		if(!tag.contains(KEY_SOUND_ID))
			setLong(stack, KEY_SOUND_ID, world.random.nextLong());
		long soundId = tag(stack).getLong(KEY_SOUND_ID);
		if(getByte(stack, KEY_STATE) == 2) {
			if(getInt(stack, KEY_FAN) < fanDuration)
				setInt(stack, KEY_FAN, getInt(stack, KEY_FAN) + 1);
			int timestamp = getInt(stack, KEY_TIMER);
			if(timestamp > COOLDOWN_TICKS) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(KEY_TIMER));
				setByte(stack, KEY_STATE, (byte) 0);
			} else
				setInt(stack, KEY_TIMER, timestamp + 1);
		} else {
			if(getInt(stack, KEY_FAN) > 0) {
				setInt(stack, KEY_FAN, getInt(stack, KEY_FAN) - 1);
				if(getInt(stack, KEY_FAN) == fanDuration / fanDiv)
					sendToDimension(world, new AttachedSoundPacket(entity, soundId));
			}
		}
		int fan = getInt(stack, KEY_FAN);
		if(fan > 0) {
			double rot = getDouble(stack, KEY_ROT) + fanRPS / 20 * (fan / (double) fanDuration);
			double subt = 0;
			if(rot >= 360)
				subt = 360;
			setDouble(stack, KEY_PREV_ROT, getDouble(stack, KEY_ROT) - subt);
			setDouble(stack, KEY_ROT, rot - subt);
			int offset = fanDuration / fanDiv;
			float pitch = (fan - offset) / (float) (fanDuration - offset) * 1.5f + 0.5f;
			if(fan > offset) {
				sendAround(world, entity.position(), 128,
						new AttachedSoundPacket(entity, soundId, LeafiaSounds.fan.get(), 0.35f * pitch, pitch));
			}
		}
	}

	public static void primary(ItemStack stack, LambdaContext ctx) {
		Player player = ctx.getPlayer();
		int index = ctx.configIndex;
		GunState state = ItemGunBaseNT.getState(stack, index);
		if(state == GunState.IDLE && getByte(stack, KEY_STATE) == 0) {
			ItemGunBaseNT.playAnimation(player, stack, GunAnimation.CYCLE, ctx.configIndex);
			ItemGunBaseNT.setState(stack, index, GunState.COOLDOWN);
			ItemGunBaseNT.setTimer(stack, index, CHARGE_TICKS);
			setByte(stack, KEY_STATE, (byte) 1);
			if(player instanceof ServerPlayer server) sendTo(server, new GetLockonPacket());
		}
	}

	public static void orchestra(ItemStack stack, LambdaContext ctx) {
		LivingEntity entity = ctx.entity;
		if(entity.level().isClientSide) return;
		GunAnimation type = ItemGunBaseNT.getLastAnim(stack, ctx.configIndex);
		int timer = ItemGunBaseNT.getAnimTimer(stack, ctx.configIndex);
		if(type == GunAnimation.EQUIP) {
			if(getByte(stack, KEY_STATE) == 1)
				setByte(stack, KEY_STATE, (byte) 0); // anti-softlock
		}
		if(type == GunAnimation.CYCLE) {
			if(timer == 0) {
				entity.level().playSound(null, entity.blockPosition(), LeafiaSounds.am_rifle_charge.get(), SoundSource.PLAYERS, 10, 1);
			}
			if(timer == CHARGE_TICKS) {
				entity.level().playSound(null, entity.blockPosition(), LeafiaSounds.am_rifle_fire.get(), SoundSource.PLAYERS, 10, 1);
				Receiver rec = ctx.config.getReceivers(stack)[0];
				rec.getOnFire(stack).accept(stack, ctx);
			}
		}
	}

	@Override
	public boolean canLockon(ItemStack stack) {
		return XWeaponModManager.hasUpgrade(stack, 0, XWeaponModManager.ID_LAS_SHOTGUN) && getByte(stack, KEY_STATE) != 2;
	}

	/** Whether the local player is fully aimed: written by the renderer every frame, read by the lock-on cone. Client only. */
	public static boolean isADS = false;

	@Override
	public double maxInclination(ItemStack stack) {
		return isADS ? 0.1 : 0.45;
	}

	@Override
	public boolean freezeTick(ItemStack stack) {
		return getByte(stack, KEY_STATE) == 1;
	}

	// --------------------------------------------------------------------------------------
	// the stack's custom data, under upstream's keys
	// --------------------------------------------------------------------------------------

	/** The custom data as a read-only tag (an empty one when there is none). Never written to. */
	public static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.getUnsafe();
	}

	public static byte getByte(ItemStack stack, String key) { return tag(stack).getByte(key); }
	public static int getInt(ItemStack stack, String key) { return tag(stack).getInt(key); }
	public static double getDouble(ItemStack stack, String key) { return tag(stack).getDouble(key); }

	public static void setByte(ItemStack stack, String key, byte value) { CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putByte(key, value)); }
	public static void setInt(ItemStack stack, String key, int value) { CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(key, value)); }
	public static void setLong(ItemStack stack, String key, long value) { CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putLong(key, value)); }
	public static void setDouble(ItemStack stack, String key, double value) { CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putDouble(key, value)); }

	// --------------------------------------------------------------------------------------
	// sends that skip a player without the channel
	// --------------------------------------------------------------------------------------

	private static boolean reachable(ServerPlayer player) {
		return player.connection != null && player.connection.hasChannel(LeafiaCustomPacket.TYPE);
	}

	public static void sendTo(@Nullable ServerPlayer player, LeafiaCustomPacketEncoder packet) {
		if(player != null && reachable(player)) LeafiaCustomPacket.__start(packet).__sendToClient(player);
	}

	public static void sendAround(ServerLevel level, Vec3 pos, double range, LeafiaCustomPacketEncoder packet) {
		LeafiaCustomPacket built = null;
		for(ServerPlayer player : level.players()) {
			if(!reachable(player) || player.distanceToSqr(pos) > range * range) continue;
			if(built == null) built = LeafiaCustomPacket.__start(packet);
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, built);
		}
	}

	public static void sendToDimension(ServerLevel level, LeafiaCustomPacketEncoder packet) {
		LeafiaCustomPacket built = null;
		for(ServerPlayer player : level.players()) {
			if(!reachable(player)) continue;
			if(built == null) built = LeafiaCustomPacket.__start(packet);
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, built);
		}
	}
}
