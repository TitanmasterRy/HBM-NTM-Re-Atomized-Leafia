package com.leafia.overwrite_contents.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.leafia.overwrite_contents.LeafiaDigammaRecord;
import com.leafia.transformer.WorldServerLeafia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Two of upstream's vanilla mixins, which both ended up at {@code ServerLevel} (D-3026).
 *
 * <p>{@code MixinServerWorldEventHandler} injected at the head of {@code ServerWorldEventHandler.sendBlockBreakProgress} and called
 * {@code WorldServerLeafia.player_onBreakBlockProgress}: the clink and the sparks of a metal tool on an ore. In 1.21.1 the break
 * progress goes out through {@code ServerLevel.destroyBlockProgress} (the player's game mode calls it whenever the step changes),
 * and there is no event for it.
 *
 * <p>{@code MixinWorld} injected at the head of {@code World.playEvent} and, for a record being played in the digamma crater,
 * cancelled it and played the digamma record's event instead ({@link LeafiaDigammaRecord}). Its other injection, at the head of
 * {@code World.onEntityRemoved}, is an event here ({@code LeafiaItemDrops}). Both of the level's two handlers read their switches
 * when they run, so with them off this does exactly what the server level does.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevel {

	@Inject(method = "destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"))
	private void leafia$onDestroyBlockProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
		WorldServerLeafia.player_onBreakBlockProgress((ServerLevel) (Object) this, breakerId, pos, progress);
	}

	@Inject(method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"), cancellable = true)
	private void leafia$onLevelEvent(@Nullable Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		int replaced = LeafiaDigammaRecord.redirect(level, type, pos, data);
		if(replaced != data) {
			ci.cancel();
			level.levelEvent(player, type, pos, replaced);
		}
	}
}
