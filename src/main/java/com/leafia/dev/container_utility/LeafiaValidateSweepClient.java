package com.leafia.dev.container_utility;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of {@link LeafiaValidateSweep}: at the end of every client tick, the loaded block entities around the
 * player and {@link LeafiaPacket#_validate} as the sender (D-3040).
 *
 * <p>Upstream read {@code world.loadedTileEntityList}. A 1.21.1 client level keeps no such list, so the block entities are
 * read out of every chunk the client has loaded within its render distance (plus one, for the chunks the client keeps at
 * the edge), which is the same set. A receiver's range can be larger than that - the elevator pulley's is 256 - but it
 * could not be validated upstream either before its chunk reached the client.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaValidateSweepClient {

	private LeafiaValidateSweepClient() {}

	/** Upstream's one {@code HandlerClient} instance held this set for the whole game. */
	public static final LeafiaValidateSweep SWEEP = new LeafiaValidateSweep();

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if(level == null || player == null) return;
		// upstream's EntityPlayer.getPosition(): the block half a block above the feet
		BlockPos pos = BlockPos.containing(player.getX(), player.getY() + 0.5, player.getZ());
		SWEEP.step(level, loaded(level, pos, mc.options.getEffectiveRenderDistance() + 1), pos, LeafiaPacket::_validate);
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		SWEEP.clear();
	}

	/** Every block entity in the loaded chunks within {@code radius} chunks of {@code centre}. */
	private static List<BlockEntity> loaded(ClientLevel level, BlockPos centre, int radius) {
		List<BlockEntity> list = new ArrayList<>();
		int cx = centre.getX() >> 4;
		int cz = centre.getZ() >> 4;
		for(int x = cx - radius; x <= cx + radius; x++) {
			for(int z = cz - radius; z <= cz + radius; z++) {
				LevelChunk chunk = level.getChunkSource().getChunk(x, z, false);
				if(chunk != null && !chunk.getBlockEntities().isEmpty()) list.addAll(chunk.getBlockEntities().values());
			}
		}
		return list;
	}
}
