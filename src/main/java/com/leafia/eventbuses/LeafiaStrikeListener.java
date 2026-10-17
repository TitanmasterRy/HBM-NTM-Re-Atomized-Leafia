package com.leafia.eventbuses;

import com.hbm.blocks.bomb.Landmine;
import com.hbm.saveddata.TomSaveData;
import com.leafia.lib.LeafiaRef;
import com.leafia.config.LeafiaFeatures;
import com.leafia.overwrite_contents.interfaces.IMixinTileEntityLandmine;
import com.leafia.overwrite_contents.interfaces.IMixinTomSaveData;
import com.leafia.savedata.PlayerDeathsSavedData;
import com.leafia.unsorted.StructuralIntegrityHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The three hooks of upstream's {@code LeafiaServerListener} that EXPL-C's features need (D-3022), pulled out into a
 * class of their own as {@link StructuralIntegrityListener} was, because that 300-line listener is an accumulator
 * ticked only when its last consumer lands.
 *
 * <ul>
 * <li><b>{@code Tom.worldTick}</b>: every server tick, each dimension's seismic level is 1 while its sky burns and
 *     otherwise settles by 1 / 9,600,000 a tick, "400 days to end shaking". Behind
 *     {@code overhauls.impactSeismic}, so a world without the overhaul has nothing written into the base mod's impact
 *     data.</li>
 * <li><b>{@code onBlockPlaced}</b>: while the seismic level is above 0, every placed block is handed to the
 *     structural-integrity handler on the next server tick (upstream's {@code addScheduledTask}). Behind the same
 *     switch. And, from {@code MixinLandmine.onBlockPlacedBy}: a landmine a living thing placed is marked player-placed
 *     ({@link IMixinTileEntityLandmine}), whatever the switches say.</li>
 * <li><b>{@code onEntityDied}</b>: a player's death is recorded, by name, in the dimension's
 *     {@link PlayerDeathsSavedData}, which the Meteor Protection Beacon reads. Always, as upstream.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaStrikeListener {

	private LeafiaStrikeListener() {}

	/** {@code 1 / 9,600,000}: 400 days of 24,000 ticks. */
	public static final float SETTLE = 1F / 9600000F;

	@SubscribeEvent
	public static void worldTick(LevelTickEvent.Pre event) {
		if(!(event.getLevel() instanceof ServerLevel level)) return;
		if(!LeafiaFeatures.impactSeismic) return;
		TomSaveData data = TomSaveData.forLevel(level);
		tickSeismic(data);
		// upstream's LeafiaPassiveServer.priorTickWorld tail: the level to everyone in the dimension (D-3040)
		com.leafia.dev.custompacket.LeafiaCustomPacket.__start(new com.leafia.database.ImpactSeismic.SeismicSyncPacket(IMixinTomSaveData.getSeismic(data)))
				.__sendToAllInDimension(level);
	}

	/** Upstream's {@code Tom.worldTick} body. */
	public static void tickSeismic(TomSaveData data) {
		if(data.fire > 0) {
			IMixinTomSaveData.setSeismic(data, 1);
		} else {
			IMixinTomSaveData.setSeismic(data, Math.max(0, IMixinTomSaveData.getSeismic(data) - SETTLE));
		}
		data.setDirty();
	}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
		if(!(event.getLevel() instanceof ServerLevel level)) return;
		BlockPos pos = event.getPos();

		if(event.getPlacedBlock().getBlock() instanceof Landmine && event.getEntity() instanceof LivingEntity) {
			BlockEntity mine = level.getBlockEntity(pos);
			if(mine != null) IMixinTileEntityLandmine.setPlayerPlaced(mine);
		}

		if(LeafiaFeatures.impactSeismic && IMixinTomSaveData.getSeismic(TomSaveData.forLevel(level)) > 0) {
			BlockPos at = pos.immutable();
			level.getServer().execute(() -> StructuralIntegrityHandler.handleBlock(level, at));
		}
	}

	@SubscribeEvent
	public static void onEntityDied(LivingDeathEvent event) {
		if(event.getEntity() instanceof Player plr && plr.level() instanceof ServerLevel level) {
			PlayerDeathsSavedData data = PlayerDeathsSavedData.forLevel(level);
			data.timestamps.put(plr.getGameProfile().getName(), level.getGameTime());
			data.setDirty();
		}
	}
}
