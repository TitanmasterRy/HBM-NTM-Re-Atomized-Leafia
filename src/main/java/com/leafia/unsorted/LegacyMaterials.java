package com.leafia.unsorted;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Read-only access to the 1.7.10 material group of a base-mod block.
 *
 * <p>Not an upstream file. {@link GeneratedBlockMaterials} is package-private on purpose (it is
 * a generated table and structural integrity is its one reader, D-1086), and the Sol Blaster is
 * the second thing that asks the same question - upstream deletes any block item whose
 * {@code Material} is wood, and 1.21.1 has no {@code Material}. This is the narrowest window onto
 * the table that answers it, so the table itself stays closed.
 *
 * <p>Only four groups are written - {@code IRON}, {@code ROCK}, {@code WOOD} and {@code OTHER} -
 * so only wood can be asked about; a base-mod block whose material was plants, a cactus or a web
 * reads as {@code OTHER} and the caller has to decide what it does about those.
 */
public final class LegacyMaterials {

	private LegacyMaterials() {}

	/**
	 * The group the table gives a block of the base mod, or null for a block of any other
	 * namespace and for a base-mod block the generator could not resolve.
	 */
	@Nullable
	public static String groupOf(Block block) {
		ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
		if(!key.getNamespace().equals("hbm")) return null;
		return GeneratedBlockMaterials.GROUP.get(key.getPath());
	}

	/** Whether the base mod's own block was {@code Material.wood} upstream. */
	public static boolean isWood(Block block) {
		return "WOOD".equals(groupOf(block));
	}
}
