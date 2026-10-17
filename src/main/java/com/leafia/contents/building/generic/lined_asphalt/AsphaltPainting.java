package com.leafia.contents.building.generic.lined_asphalt;

import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lets the <em>base mod's</em> plain asphalt be painted, without touching the base mod.
 *
 * <h2>What this replaces, and why it is not a port of it</h2>
 *
 * Upstream reaches plain asphalt with {@code MixinBlockSpeedy} — a mixin that injects into
 * {@code com.hbm.blocks.generic.BlockSpeedy.onBlockActivated} and calls
 * {@code AsphaltBlock.paintBlock}. <b>D-1001 forbids mixing into {@code com.hbm}</b>, and that
 * rule is not negotiable: the whole point of the addon being a second jar is that the dependency
 * runs one way and nothing on master has to know it exists.
 *
 * <p>So the mixin is not ported and this is not a port of it. It is the same behaviour reached
 * a different way: a {@link PlayerInteractEvent.RightClickBlock} subscription on this jar's own
 * event bus, which sees every right click before the block does and can cancel it. Nothing in
 * {@code com.hbm} is patched, subclassed or referenced except by name.
 *
 * <p><b>This is worth generalising.</b> A mixin into the base mod is forbidden; <em>listening to
 * a vanilla event</em> is not, and a surprising number of the addon's mixins exist only to reach
 * a hook that NeoForge now publishes as an event. Before writing off an
 * {@code overwrite_contents/mixin/mod/hbm} file as unportable, check whether the thing it
 * injects into has an event in front of it. See D-1047.
 *
 * <h2>Why it only fires for the base mod's asphalt</h2>
 *
 * This addon's own painted blocks handle the click themselves, in
 * {@code LinedAsphaltBlock.useItemOn}, because they can. The event covers the one state that is
 * not this addon's to override — the unpainted block a road starts as. Guarding on the exact
 * block rather than on "is it asphalt-like" keeps the two paths from both firing.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID)
public final class AsphaltPainting {

	private AsphaltPainting() {}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {

		if(!LeafiaFeatures.linedAsphalt) return;

		BlockState state = event.getLevel().getBlockState(event.getPos());
		if(state.getBlock() != ModBlocks.asphalt.get()) return;

		double hitX = event.getHitVec().getLocation().x - event.getPos().getX();
		double hitZ = event.getHitVec().getLocation().z - event.getPos().getZ();

		if(!AsphaltBlock.paintBlock(event.getLevel(), event.getPos(), event.getEntity(),
				event.getHand(), hitX, hitZ))
			return;

		// Upstream's onBlockActivated returns true, which stops the click reaching the held
		// item. Cancelling is how a listener says the same thing.
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
	}
}
