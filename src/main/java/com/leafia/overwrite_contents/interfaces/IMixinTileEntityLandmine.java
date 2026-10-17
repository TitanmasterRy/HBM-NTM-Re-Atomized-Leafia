package com.leafia.overwrite_contents.interfaces;

import com.leafia.init.LeafiaAttachments;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Whether a landmine was placed by a player (D-3022). Upstream's interface, which {@code MixinTileEntityLandmine}
 * implemented on the base mod's mine with a field saved as {@code byPlayer}; here the flag is the
 * {@link LeafiaAttachments#LANDMINE_PLAYER_PLACED} data attachment on the base mod's {@code BlockEntityLandmine},
 * which saves with it (D-2197), and these are statics over it. Set by {@code LeafiaStrikeListener} when a living
 * thing places a mine; read by the landmine detonation hook.
 */
public interface IMixinTileEntityLandmine {

	static void setPlayerPlaced(BlockEntity mine) {
		mine.setData(LeafiaAttachments.LANDMINE_PLAYER_PLACED, Boolean.TRUE);
		mine.setChanged();
	}

	static boolean getPlayerPlaced(BlockEntity mine) {
		return mine.hasData(LeafiaAttachments.LANDMINE_PLAYER_PLACED) && mine.getData(LeafiaAttachments.LANDMINE_PLAYER_PLACED);
	}
}
