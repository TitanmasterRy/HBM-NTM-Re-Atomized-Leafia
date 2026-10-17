package com.leafia.overwrite_contents;

import java.util.UUID;

import javax.annotation.Nullable;

import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.advisor.AdvisorItem;
import com.leafia.init.LeafiaAdvancements;
import com.leafia.init.LeafiaAttachments;
import com.leafia.lib.LeafiaRef;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Who threw an item on the ground, and what became of it (D-3026).
 *
 * <p>Upstream does this with four things that are not events: {@code MixinEntityPlayer}, an injection at the return of
 * {@code EntityPlayer.dropItem} that calls {@code WorldServerLeafia.onItemDroppedByPlayer}; {@code MixinEntityItem}, which gives
 * every {@code EntityItem} two fields (the dropper, and whether it was picked up) through {@code IMixinEntityItem};
 * {@code LeafiaServerListener.onEntityItemPickup}, which sets the second; and {@code MixinWorld}, an injection at the head of
 * {@code World.onEntityRemoved} that grants the advancement {@code loseadvisor} for a High-Tech Advisor that goes without being
 * picked up. NeoForge publishes all four as events and a data attachment, so none of it is a mixin:
 *
 * <ul>
 *   <li>{@link ItemTossEvent} (the player threw it) writes the dropper;</li>
 *   <li>{@link ItemEntityPickupEvent.Pre} (a player touched it, as upstream's {@code EntityItemPickupEvent}) writes the flag, before
 *       the pickup, so a full inventory that leaves it on the ground still counts as picked up, as upstream's did;</li>
 *   <li>{@link EntityLeaveLevelEvent} (an item entity left the world) asks. 1.12.2's {@code onEntityRemoved} also fired when a chunk
 *       unloaded; the removal reason here says whether it was <em>destroyed</em> (killed, burnt, despawned, merged away) or only put
 *       to sleep, and a chunk unloading is not losing the advisor.</li>
 * </ul>
 *
 * <p>Both fields are an attachment ({@link LeafiaAttachments#ITEM_DROP}) that is not saved, as upstream's were not.
 *
 * <p><b>The other half of {@code MixinEntityItem}</b> is an injection after {@code attackEntityFrom} kills the entity, which asks every
 * hazard of the stack for a {@code HazardTypeContainmentBase} and tells it where the item was destroyed. Vanilla's
 * {@code Item.onDestroyed(ItemEntity, DamageSource)} is that point, so it needs no mixin: the APR batch's {@code AbsorptionParticleItem}
 * overrides it (D-3027). A first draft of this batch had a mixin and a hook list for it; the APR batch's override made both dead and they
 * were removed.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaItemDrops {

	private LeafiaItemDrops() {}

	/** The two fields upstream's mixin added to {@code EntityItem}. */
	public static final class Track {
		/** {@code leafia$droppedBy}: the thrower's UUID (upstream kept the player itself). */
		@Nullable public UUID droppedBy;
		/** {@code leafia$wasPickedUp}. */
		public boolean wasPickedUp;
	}

	public static Track track(ItemEntity item) {
		return item.getData(LeafiaAttachments.ITEM_DROP);
	}

	/** {@code IMixinEntityItem.leafia$setDroppedBy}. */
	public static void setDroppedBy(ItemEntity item, Player player) {
		track(item).droppedBy = player.getUUID();
	}

	/** {@code IMixinEntityItem.leafia$getDroppedBy}: the thrower if they are still on the server. */
	@Nullable
	public static ServerPlayer droppedBy(ItemEntity item) {
		UUID id = track(item).droppedBy;
		if(id == null || item.level().getServer() == null) return null;
		return item.level().getServer().getPlayerList().getPlayer(id);
	}

	/** {@code IMixinEntityItem.leafia$setWasPickedUp}. */
	public static void setWasPickedUp(ItemEntity item, boolean pickedUp) {
		track(item).wasPickedUp = pickedUp;
	}

	/** {@code IMixinEntityItem.leafia$getWasPickedUp}. */
	public static boolean wasPickedUp(ItemEntity item) {
		return track(item).wasPickedUp;
	}

	// ------------------------------------------------------------------ the events

	/** {@code MixinEntityPlayer}: a player dropped it. */
	@SubscribeEvent
	public static void onToss(ItemTossEvent event) {
		setDroppedBy(event.getEntity(), event.getPlayer());
	}

	/** {@code LeafiaServerListener.onEntityItemPickup}. */
	@SubscribeEvent
	public static void onPickup(ItemEntityPickupEvent.Pre event) {
		setWasPickedUp(event.getItemEntity(), true);
	}

	/** {@code MixinWorld.leafia$onEntityRemoved} into {@code WorldServerLeafia.onEntityRemoved}. */
	@SubscribeEvent
	public static void onLeave(EntityLeaveLevelEvent event) {
		Entity entity = event.getEntity();
		if(entity.level().isClientSide || !(entity instanceof ItemEntity item)) return;
		com.leafia.transformer.WorldServerLeafia.onEntityRemoved(entity.level(), item);
	}

	/** Whether {@code item}'s removal is the item being lost: destroyed rather than unloaded, and not merged into another stack or taken whole. */
	public static boolean wasLost(ItemEntity item) {
		Entity.RemovalReason reason = item.getRemovalReason();
		return reason != null && reason.shouldDestroy() && !item.getItem().isEmpty() && !wasPickedUp(item);
	}

	/** The advancement an item gone missing earns its thrower, if any: upstream's only tracked item is the advisor. */
	public static void lose(ItemEntity item) {
		if(!LeafiaFeatures.advisor || !wasLost(item)) return;
		if(!(item.getItem().getItem() instanceof AdvisorItem)) return;
		ServerPlayer player = droppedBy(item);
		if(player != null) LeafiaAdvancements.grant(player, LeafiaAdvancements.LOSE_ADVISOR);
	}
}
