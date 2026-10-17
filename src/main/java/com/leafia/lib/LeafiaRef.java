package com.leafia.lib;

import net.minecraft.resources.ResourceLocation;

/**
 * The addon's equivalent of {@code com.hbm.lib.RefStrings}.
 *
 * <p>The mod id is upstream's own, so a player's config keys and world data match theirs and
 * an upstream diff stays readable.
 */
public class LeafiaRef {

	public static final String MODID = "leafia";
	public static final String NAME = "Leafia's Cursed Addon";
	public static final String VERSION = "0.1.0";

	/**
	 * The upstream release this port targets parity with, and the base mod that release was
	 * built against.
	 *
	 * <p>The second half matters more than it looks. Upstream targets NTM Community Edition
	 * 1.12.2, which is a different fork of NTM from the 1.7.10 {@code hbm-src} that is this
	 * port's specification — so a base-mod constant read out of an addon file is a CE value,
	 * not ours, and carrying one across silently rebalances a base machine. Ours wins. See
	 * D-1000.
	 */
	public static final String UPSTREAM_VERSION = "v54_feature";
	public static final String UPSTREAM_BASE = "NTM-CE 2.6.0.0 (Minecraft 1.12.2)";

	/** Shorthand for a ResourceLocation in the addon's namespace. */
	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}
}
