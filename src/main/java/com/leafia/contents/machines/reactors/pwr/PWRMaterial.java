package com.leafia.contents.machines.reactors.pwr;

import com.hbm.util.LegacyMaterial;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Upstream's {@code state.getMaterial() == Material.IRON}, which the level-3 meltdown and the debris both
 * ask. Not an upstream file: 1.21.1 has no {@code Material}.
 *
 * <p>Two halves, because the answer lives in two places. The addon's own blocks were registered with
 * upstream's {@code Material.IRON} encoded as a metal map colour plus a pickaxe to drop them (D-3006's
 * reading, D-1074's rule) - which the PWR's hull carries although it <em>sounds</em> like stone, and which
 * is the whole point: a stone-sounding iron wall becomes stone wreck, a metal-sounding one metal wreck.
 * Everything else is the base mod's {@link LegacyMaterial}, exact for the base mod's own blocks and a
 * documented classification for vanilla's (D-815).
 */
public final class PWRMaterial {

	private PWRMaterial() {}

	public static boolean isIron(BlockState state) {
		if(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(LeafiaRef.MODID))
			return state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.METAL && state.requiresCorrectToolForDrops();
		return LegacyMaterial.of(state) == LegacyMaterial.IRON;
	}
}
