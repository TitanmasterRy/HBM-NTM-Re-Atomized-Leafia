package com.leafia.database;

import net.minecraft.core.BlockPos;

/**
 * Ported from `com.leafia.database.FolkvangrJammers`.
 *
 * <p>Where the last Folkvangr jammer was seen. One mutable static, which is how upstream lets
 * the bomb and its renderer agree on a position without a packet.
 */
public class FolkvangrJammers {
	public static BlockPos lastDetectedJammer = new BlockPos(0, 0, 0);
}
