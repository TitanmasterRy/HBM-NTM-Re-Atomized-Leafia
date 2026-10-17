package com.leafia.init;

import com.leafia.lib.LeafiaRef;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Granting the addon's advancements (D-3026, D-3040).
 *
 * <p>Upstream: {@code com/leafia/init/AddonAdvancements.java} looks seven advancements up by name when a server starts
 * and its callers hand one to the base mod's {@code AdvancementManager.grantAchievement(player, advancement)}. A 1.21.1
 * advancement is a datapack entry, found by id on the running server and awarded criterion by criterion, so the table of
 * fields is not needed: {@link #grant} takes the id, and the seven are JSON under {@code data/leafia/advancement}, each
 * carrying the feature switches its content needs. {@code loseadvisor} landed with its caller (D-3026); the other six and
 * {@code zetalite}, which is {@code nukeapr}'s parent, landed with the close-out batch (D-3040), when their callers were all
 * ported. {@code genericfuel} and {@code zetalite} are earned by their own inventory criterion; the other five are
 * {@code minecraft:impossible} and only {@link #grant} completes them.
 */
public final class LeafiaAdvancements {

	private LeafiaAdvancements() {}

	/** {@code AddonAdvancements.loseadvisor}: "Lose a High-Tech Advisor". */
	public static final String LOSE_ADVISOR = "loseadvisor";
	/** {@code openpwr}: right-clicking a PWR terminal linked to a reactor. */
	public static final String OPEN_PWR = "openpwr";
	/** {@code nukepwr}: within 100 blocks of a level-3 PWR meltdown on a liquid coolant. */
	public static final String NUKE_PWR = "nukepwr";
	/** {@code nukebwr}: the same, with the reactor's hot coolant ultra-hot steam. */
	public static final String NUKE_BWR = "nukebwr";
	/** {@code genericfuel}: holding an MEU fuel rod - an inventory criterion, never granted by code. */
	public static final String GENERIC_FUEL = "genericfuel";
	/** {@code nukeapr}: within 200 blocks of an APR's endothermic blast. */
	public static final String NUKE_APR = "nukeapr";
	/** {@code dgomega}: a DG-OMEGA rod's blast raising the player's digamma. */
	public static final String DG_OMEGA = "dgomega";

	/**
	 * Upstream's {@code grantAchievement}: awards every criterion still missing, so the advancement completes.
	 *
	 * @return whether this call completed it (false when it is unknown on this server, or was already done)
	 */
	public static boolean grant(ServerPlayer player, String id) {
		AdvancementHolder holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, id));
		if(holder == null) return false;
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if(progress.isDone()) return false;
		for(String criterion : progress.getRemainingCriteria()) player.getAdvancements().award(holder, criterion);
		return true;
	}

	/**
	 * Upstream's {@code world.getEntitiesWithinAABB(EntityPlayer.class, box)} followed by a grant to each: every player whose
	 * bounding box meets {@code box}. Nothing on a client.
	 *
	 * @return how many players this completed it for
	 */
	public static int grantWithin(Level level, AABB box, String id) {
		if(level.isClientSide) return 0;
		int granted = 0;
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, box);
		for(ServerPlayer player : players) if(grant(player, id)) granted++;
		return granted;
	}
}
