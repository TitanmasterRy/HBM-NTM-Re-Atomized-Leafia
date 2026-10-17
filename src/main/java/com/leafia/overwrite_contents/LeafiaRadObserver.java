package com.leafia.overwrite_contents;

import java.util.function.Consumer;

import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.util.ArmorUtil;
import com.leafia.contents.LeafiaItems;
import com.leafia.overwrite_contents.packets.RadHighlightPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The rad goggles' locating effect: whenever radiation is added to the world, every player wearing them within 256 blocks of the
 * spot is shown a box at the block with the amount.
 *
 * <p>Upstream: {@code MixinRadiationSystemNT}, an injection at the head of the CE-only {@code RadiationSystemNT.incrementRad}. The
 * base mod's seam is {@code ChunkRadiationManager.addIncrementObserver} (D-2207), told of each increment on the server before the
 * handler stores it, with the amount actually added (the fork's mixin saw the amount asked for, even when the capped increment
 * added nothing; seeing the applied one is the better signal for "this just got more radioactive", and is said in D-2207). The
 * seam tells every observer about every increment, so the 256-block range is filtered here, upstream's way: the distance from the
 * wearer's eye to the centre of the block, strictly less than 256.
 *
 * <p>The goggles exist behind {@code features.radGlasses}; with it off {@code LeafiaItems.radglasses} is null and nobody can wear
 * them, so the observer is not even registered.
 */
public final class LeafiaRadObserver {

	private LeafiaRadObserver() {}

	/** Upstream's {@code < 256}. */
	public static final double RANGE = 256;
	/** The helmet slot, in {@code ArmorUtil.checkArmorPiece}'s numbering. */
	private static final int HEAD = 3;

	private static boolean installed = false;

	/** Where a box is sent. A test swaps it for a recorder; the default is the clientbound packet. */
	private static Consumer<Sent> sink = sent -> RadHighlightPacket.send(sent.player(), sent.pos(), sent.amount());

	public record Sent(ServerPlayer player, BlockPos pos, float amount) {}

	/** From the mod constructor. */
	public static void install() {
		if(installed || LeafiaItems.radglasses == null) return;
		installed = true;
		ChunkRadiationManager.addIncrementObserver(LeafiaRadObserver::onIncrement);
	}

	/** For tests: where boxes go, or the packet again with null. Returns the previous sink so a test can put it back. */
	public static Consumer<Sent> setSink(Consumer<Sent> replacement) {
		Consumer<Sent> old = sink;
		sink = replacement != null ? replacement : sent -> RadHighlightPacket.send(sent.player(), sent.pos(), sent.amount());
		return old;
	}

	static void onIncrement(Level world, int x, int y, int z, float amount) {
		if(!(world instanceof ServerLevel level) || LeafiaItems.radglasses == null) return;
		for(ServerPlayer player : level.players()) {
			if(!ArmorUtil.checkArmorPiece(player, LeafiaItems.radglasses.get(), HEAD)) continue;
			if(new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ()).distanceTo(new Vec3(x + 0.5, y + 0.5, z + 0.5)) < RANGE)
				sink.accept(new Sent(player, new BlockPos(x, y, z), amount));
		}
	}
}
