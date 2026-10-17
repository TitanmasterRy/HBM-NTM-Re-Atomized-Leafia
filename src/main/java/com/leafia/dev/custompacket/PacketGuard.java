package com.leafia.dev.custompacket;

import javax.annotation.Nullable;

import com.leafia.LeafiaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;

/**
 * The checks every serverbound addon packet makes before it acts (D-1094, the owner's answer to
 * D-1093 question 4).
 *
 * <p>Upstream trusts its clients completely: a packet names a block or an entity and the server does
 * what it says, from any distance, whether or not the sender has the machine's screen open, with
 * whatever index or string the client typed. 1.12.2 let that stand because a modified client was the
 * only thing that could send such a packet; here it is the same, and the addon's own code is where the
 * fix belongs. The rules, applied by every handler that touches the world:
 *
 * <ol>
 *   <li><b>Reach</b> - the sender is within vanilla's own interaction range of the block or entity,
 *       with the same 4-block buffer {@code ServerGamePacketListenerImpl} allows
 *       ({@link #BUFFER}); that is about 8.5 blocks, and it is what a menu's {@code stillValid}
 *       means in vanilla.</li>
 *   <li><b>Type</b> - the block entity or entity at the named place is the class the packet is
 *       for.</li>
 *   <li><b>Menu</b> - where the action is a GUI action, the sender's open container is that
 *       machine's menu.</li>
 *   <li><b>Values</b> - every index, enum ordinal, number and string is range-checked before it is
 *       used.</li>
 * </ol>
 *
 * A packet that fails any of them is dropped without a word to the sender and logged at debug.
 */
public final class PacketGuard {

	private PacketGuard() {}

	/** Added to the player's interaction range: vanilla's own slack for latency, 4 blocks. */
	public static final double BUFFER = 4.0D;

	/** Whether the player could plausibly be using the block at {@code pos}. */
	public static boolean near(@Nullable Player player, @Nullable BlockPos pos) {
		return player != null && pos != null && player.canInteractWithBlock(pos, BUFFER);
	}

	/** Whether the player could plausibly be using the entity. */
	public static boolean near(@Nullable Player player, @Nullable Entity entity) {
		return player != null && entity != null && player.canInteractWithEntity(entity, BUFFER);
	}

	/**
	 * Whether the player is within {@code range} blocks of the middle of {@code pos}, or within
	 * reach, whichever is larger. For a packet that only asks the server for state (the empty
	 * "validate" packet), where the receiver names how far its listeners can be.
	 */
	public static boolean within(@Nullable Player player, @Nullable BlockPos pos, double range) {
		if(player == null || pos == null) return false;
		double limit = Math.max(range, player.blockInteractionRange() + BUFFER);
		return player.distanceToSqr(Vec3.atCenterOf(pos)) <= limit * limit;
	}

	/** Whether the player has exactly this menu open. */
	public static boolean hasOpen(@Nullable Player player, @Nullable AbstractContainerMenu menu) {
		return player != null && menu != null && player.containerMenu == menu;
	}

	/** Logs a dropped packet at debug level. Never tells the sender. */
	public static void reject(String packet, @Nullable Player player, String why) {
		if(LeafiaMod.LOGGER.isDebugEnabled()) {
			LeafiaMod.LOGGER.debug("Dropped {} from {}: {}", packet,
					player == null ? "nobody" : player.getName().getString(), why);
		}
	}
}
