package com.leafia.dev.container_utility;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The client's validate sweep: what makes {@link LeafiaPacketReceiver#onPlayerValidate} reachable (D-3040).
 *
 * <p>Upstream: the middle of {@code LeafiaClientListener.HandlerClient.clientTick}, at the end of every client tick. It walks
 * the world's loaded block entities, and each {@link LeafiaPacketReceiver} the player has come within
 * {@link LeafiaPacketReceiver#affectionRange} of is sent {@link LeafiaPacket#_validate} once - an empty packet the server reads
 * as "bring this player up to date", answered by {@code onPlayerValidate}. A receiver stays validated until the player is more
 * than {@link #DROP_FACTOR} times its range away (or it is gone), and is asked again when the player comes back. The 25%
 * margin is upstream's, so standing on the edge of a range does not send a packet every tick.
 *
 * <p>The sweep was the one part of that 1,200-line listener every receiver depended on: until it landed every
 * {@code onPlayerValidate} in the addon was dead code, and the letter signs, the duct utilities, the APR and the elevator
 * landings each grew a vanilla update-tag workaround for state they could otherwise only push every tick. Those workarounds
 * stay - they are cheaper - and the validate packets now arrive as well.
 *
 * <p>This class is the decision and nothing else, so it can be tested without a client: {@code LeafiaValidateSweepClient}
 * hands it the client level's loaded block entities and {@link LeafiaPacket#_validate} as the sender. Distances are upstream's,
 * {@code BlockPos.getDistance} between the block entity's position and the player's block position, both integer corners.
 */
public final class LeafiaValidateSweep {

	/** A validated receiver is forgotten when the player is further than this times its range. */
	public static final double DROP_FACTOR = 1.25;

	/** Upstream's {@code validatedTEs}. Block entities compare by identity, as 1.12.2's tile entities did. */
	private final Set<BlockEntity> validated = Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * One client tick.
	 *
	 * @param level the player's level; a remembered receiver in any other level is forgotten
	 * @param loaded the level's loaded block entities, in any order
	 * @param player the player's block position (upstream's {@code getPosition()})
	 * @param validate called once for each receiver that has just come into range
	 * @return how many were validated this tick
	 */
	public int step(Level level, Iterable<? extends BlockEntity> loaded, BlockPos player, Consumer<BlockEntity> validate) {
		// first forget the ones that went away, or that the player walked away from
		validated.removeIf(entity -> entity.isRemoved()
				|| entity.getLevel() != level
				|| !(entity instanceof LeafiaPacketReceiver receiver)
				|| distance(entity, player) > receiver.affectionRange() * DROP_FACTOR);

		// then ask for the ones that have just come into range
		int sent = 0;
		for(BlockEntity entity : loaded) {
			if(entity.isRemoved() || !(entity instanceof LeafiaPacketReceiver receiver) || validated.contains(entity)) continue;
			if(distance(entity, player) <= receiver.affectionRange()) {
				validated.add(entity);
				validate.accept(entity);
				sent++;
			}
		}
		return sent;
	}

	/** Whether {@code entity} is remembered as validated. */
	public boolean isValidated(BlockEntity entity) {
		return validated.contains(entity);
	}

	/** How many receivers are remembered. */
	public int size() {
		return validated.size();
	}

	/** Forgets everything: the player left the world. */
	public void clear() {
		validated.clear();
	}

	/** Upstream's {@code getPos().getDistance(x, y, z)}: the straight line between two block corners. */
	public static double distance(BlockEntity entity, BlockPos player) {
		return Math.sqrt(entity.getBlockPos().distSqr(player));
	}
}
