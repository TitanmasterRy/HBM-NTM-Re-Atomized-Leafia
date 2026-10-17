package com.leafia.init;

import java.util.function.Supplier;

import com.leafia.lib.LeafiaRef;
import com.mojang.serialization.Codec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The addon's NeoForge data attachments: state the addon keeps on something the base mod owns, where upstream
 * kept it in a field a mixin added (D-1001 forbids the mixin; D-2197 names attachments as the place).
 *
 * <p>Registered whatever the feature switches say. An attachment type is not content a player can see, and
 * recording a fact while its feature is off means switching the feature on later reads the truth: a mine placed
 * with {@code overhauls.landmineOverhaul} off is still known to have been placed by a player.
 */
public final class LeafiaAttachments {

	private LeafiaAttachments() {}

	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LeafiaRef.MODID);

	/**
	 * Whether a landmine was placed by a living thing rather than by worldgen (upstream's
	 * {@code MixinTileEntityLandmine.leafia$playerPlaced}, saved as {@code byPlayer}). On the base mod's
	 * {@code BlockEntityLandmine}, saved with it.
	 */
	public static final Supplier<AttachmentType<Boolean>> LANDMINE_PLAYER_PLACED = ATTACHMENT_TYPES.register(
			"landmine_player_placed", () -> AttachmentType.builder(() -> Boolean.FALSE).serialize(Codec.BOOL).build());

	/**
	 * Who dropped an item entity, and whether anyone has picked it up (upstream's {@code MixinEntityItem.leafia$droppedBy} and
	 * {@code leafia$wasPickedUp}, plain fields a mixin added to {@code EntityItem}, D-3026). <b>Not saved</b>, as upstream's were not:
	 * an item entity reloaded from a chunk has forgotten who threw it.
	 */
	public static final Supplier<AttachmentType<com.leafia.overwrite_contents.LeafiaItemDrops.Track>> ITEM_DROP = ATTACHMENT_TYPES.register(
			"item_drop", () -> AttachmentType.builder(com.leafia.overwrite_contents.LeafiaItemDrops.Track::new).build());

	public static void register(IEventBus modBus) {
		ATTACHMENT_TYPES.register(modBus);
	}
}
