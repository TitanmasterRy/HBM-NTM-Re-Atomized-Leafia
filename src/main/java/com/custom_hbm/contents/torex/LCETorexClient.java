package com.custom_hbm.contents.torex;

import com.custom_hbm.contents.torex.LCETorex.TorexPacket;
import com.hbm.items.ModItems;
import com.leafia.config.LeafiaFeatures;
import com.leafia.dev.LeafiaUtil;
import com.leafia.init.LeafiaSounds;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam;
import com.leafia.passive.effects.LeafiaShakecam.shakeSimple;
import com.leafia.passive.effects.LeafiaShakecam.shakeSmooth;
import com.llib.technical.LeafiaEase.Direction;
import com.llib.technical.LeafiaEase.Ease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What a client does about a torex besides drawing it: take the packet, and be hit by the blast.
 *
 * <p>Upstream does the second inside {@code LCETorexRender.doRender} - {@code doScreenShake} is a
 * method of the <em>renderer</em>, called every frame, and plays sounds, adds camera shakes and
 * pushes the player from there. It is moved to the client tick (<b>D-1089</b>), which is the only
 * place a sound, a velocity and a hurt timer should be set from, and it changes one visible thing:
 * the shockwave's thunder, which upstream plays <em>every frame</em> the player is inside the
 * shockwave's ten-block band (so it plays between three and twelve times in the one or two ticks
 * the wave takes to cross it, by frame rate), plays once or twice here.
 *
 * <p>Upstream's {@code Vic4Games} branch is not carried: it compares an {@code ITextComponent} to a
 * {@code String}, which is never equal, so it never ran (D-1089).
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LCETorexClient {

	private LCETorexClient() {}

	/** A {@link TorexPacket} has arrived: if it is in range of the player, a cloud begins. */
	static void receive(TorexPacket message) {
		if(!LeafiaFeatures.torex) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if(player == null || level == null) return;
		LCETorex.fromPacket(level, message, player.getX(), player.getZ());
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if(!event.getLevel().isClientSide() || !LeafiaFeatures.torex) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if(player == null) return;
		for(int i = 0; i < LCETorex.CLIENT.size(); i++) {
			LCETorex torex = LCETorex.CLIENT.get(i);
			if(torex.level != event.getLevel() || torex.isDead || !torex.isValid()) continue;
			doScreenShake(torex, player, (float) (torex.getScale() * 100));
			debug(torex, player);
		}
	}

	/** Upstream's {@code doScreenShake}, once per tick. */
	static void doScreenShake(LCETorex cloud, LocalPlayer player, float amplitude) {
		if(cloud.ticksExisted > 300) return;

		Vec3 origin = new Vec3(cloud.posX, cloud.posY, cloud.posZ);
		double dist = player.position().distanceTo(origin);
		double shockwaveDistance = TorexMath.shockwaveDistance(dist, cloud.ticksExisted, cloud.getScale());
		if(shockwaveDistance > 10) return;

		ClientLevel level = (ClientLevel) cloud.level;
		if(!cloud.reachedPlayer && cloud.sound) {
			cloud.reachedPlayer = true;
			if(amplitude > 48) {
				SoundEvent evt = switch(TorexMath.blastSound(amplitude, dist)) {
				case 1 -> LeafiaSounds.nuke_smol.get();
				case 2 -> LeafiaSounds.nuke_near.get();
				case 3 -> LeafiaSounds.nuke_far.get();
				default -> LeafiaSounds.nuke.get();
				};
				level.playLocalSound(cloud.initPosX, cloud.initPosY, cloud.initPosZ, evt, SoundSource.AMBIENT,
						amplitude * 15F, 0.8F + level.random.nextFloat() * 0.2F, false);
				LeafiaShakecam._addShake(cloud.getInitialPosition(), new shakeSimple(8f * (amplitude / 100), Ease.BACK, Direction.I).configure(amplitude * 12F, 24f, 0.5f, null));
				LeafiaShakecam._addShake(cloud.getInitialPosition(), new shakeSmooth(15f * (amplitude / 100), Ease.QUAD, Direction.I).configure(amplitude * 5F, 12f, 1.8f, 8f));
				LeafiaShakecam._addShake(cloud.getInitialPosition(), new shakeSmooth(30f * (amplitude / 100), null, null).configure(amplitude * 4F, 2f, 1.5f, 3.5f));
				LeafiaShakecam._addShake(cloud.getInitialPosition(), new shakeSmooth(60f * (amplitude / 100), null, null).configure(amplitude * 4F, 0.5f, 0.5f, 2f));
			} else {
				var pos = cloud.getInitialPosition();
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, LeafiaSounds.mukeExplosion.get(), SoundSource.BLOCKS, 15, 1, false);
			}
			Vec3 force = LeafiaUtil.getKnockback(player.position().add(0, player.getEyeHeight(), 0), origin, amplitude);
			player.setDeltaMovement(player.getDeltaMovement().add(force));
		}
		if(shockwaveDistance < 0) return;

		int duration = TorexMath.hurtDuration(amplitude, dist);
		int swingTimer = duration << 1;
		level.playLocalSound(cloud.initPosX, cloud.initPosY, cloud.initPosZ, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT,
				amplitude * 10F, 0.8F + level.random.nextFloat() * 0.2F, false);

		player.hurtTime = swingTimer;
		player.hurtDuration = duration;
	}

	/** Upstream's {@code localSendMessage}: with the debug wand in hand, the cloud's numbers in chat every tick. */
	private static void debug(LCETorex torex, LocalPlayer player) {
		if(!player.getMainHandItem().is(ModItems.wand_d.get())) return;
		player.sendSystemMessage(Component.literal("spawn: " + torex.lastDoSpawn));
		player.sendSystemMessage(Component.literal("height: " + torex.coreHeight));
		player.sendSystemMessage(Component.literal("width: " + torex.torusWidth));
		player.sendSystemMessage(Component.literal("scale: " + torex.scaleCurrent));
		player.sendSystemMessage(Component.literal("sim: " + torex.lastSimSpeed));
	}

	/** Leaving the world drops every cloud; a new world's clouds come from its own packets. */
	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		LCETorex.CLIENT.clear();
		LCETorex.waitingTorexes.clear();
	}
}
