package com.leafia.overwrite_contents.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.leafia.dev.firestorm.IFirestormBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;

/**
 * Fire reaches the machine next to it (D-3025).
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/MixinBlockFire.java}, two injections at the head of
 * {@code BlockFire.getNeighborEncouragement} and {@code tryCatchFire} calling {@code IFirestormBlock.ignite} with the
 * position they were asked about. This is the addon's one mixin into <em>vanilla</em> (D-1001 forbids only the base
 * mod's classes): there is no event or seam for a fire block considering a neighbour. The 1.21.1 counterparts are
 * {@code FireBlock.getIgniteOdds(LevelReader, BlockPos)} - asked about every position in the 3x3x6 box around a fire,
 * air or not, once per random tick - and {@code checkBurnOut}, asked about each of its six neighbours. Both are
 * private, so the injections name them and nothing else.
 *
 * <p>With {@code features.overhauls.firestorm} off (the default) {@link IFirestormBlock#ignite} returns at its first
 * line, so the fire block does exactly what vanilla's does.
 */
@Mixin(FireBlock.class)
public abstract class MixinBlockFire {

	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"))
	private void leafia$igniteOnNeighborEncouragement(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if(level instanceof Level real) IFirestormBlock.ignite(real, pos);
	}

	@Inject(method = "checkBurnOut", at = @At("HEAD"))
	private void leafia$igniteOnTryCatchFire(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
		IFirestormBlock.ignite(level, pos);
	}
}
