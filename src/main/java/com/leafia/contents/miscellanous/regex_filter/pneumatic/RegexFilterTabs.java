package com.leafia.contents.miscellanous.regex_filter.pneumatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.LeafiaMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Which creative tabs an item sits in - the answer to upstream's
 * {@code stack.getItem().getCreativeTab().tabLabel}, which has no 1.21.1 successor.
 *
 * <h2>Why this file exists</h2>
 *
 * In 1.12.2 an item <em>had</em> a tab: one field, readable anywhere. In 1.21.1 a tab has a
 * <em>list of items</em>, produced by a generator that runs when somebody asks for the tab's
 * contents, and an item may be in any number of them or none. The filter's third mode,
 * {@code CREATIVE_TAB}, needs the old direction, item to tab, on the <b>server</b>.
 *
 * <p>That is not available for free. {@code CreativeModeTab.getDisplayItems()} is empty on a
 * dedicated server because nothing there has ever built the tabs - the addon's own
 * {@code LeafiaCreativeTabs.contentsOf} documents the same trap for its tests (D-1049) - so the
 * obvious implementation passes whatever it is asked and matches nothing, silently. This builds
 * them once, <b>only if nobody has</b>, and inverts them.
 *
 * <h2>Building them touches vanilla's global state, and why that is acceptable here</h2>
 *
 * {@link CreativeModeTabs#tryRebuildTabContents} fills the tabs everybody shares. It is called
 * only when <em>every</em> category tab is empty, which means no client has opened the creative
 * menu in this JVM, so there is nothing to overwrite; on a dedicated server nothing else reads
 * them. On an integrated server the client has built them already and this does not run. If it
 * did run first, the creative screen asks for its own parameters when it opens and vanilla
 * rebuilds on any mismatch.
 *
 * <h2>The string a player types</h2>
 *
 * The tab's registry id - {@code hbm:machine}, {@code minecraft:building_blocks},
 * {@code leafia:main}. Upstream matched {@code tabLabel}, which was a free-form string
 * ({@code "buildingBlocks"}) and is gone; the screen has always called the mode "Creative Tab
 * ID", which is what this is. An item in several tabs matches if <b>any</b> of them does, and a
 * blacklist entry therefore blocks it if any does.
 */
public final class RegexFilterTabs {

	private RegexFilterTabs() {}

	private static volatile Map<Item, List<String>> index = null;
	private static volatile boolean failed = false;

	/** Every tab id {@code item} is listed in, or an empty list. */
	public static List<String> tabsOf(Level level, Item item) {
		Map<Item, List<String>> map = index;
		if(map == null) map = build(level);
		return map.getOrDefault(item, List.of());
	}

	/** Forgets the index, so the next lookup rebuilds it. A test seam. */
	public static void reset() {
		index = null;
		failed = false;
	}

	private static synchronized Map<Item, List<String>> build(Level level) {

		if(index != null) return index;
		if(failed || level == null) return Map.of();

		try {
			if(allCategoryTabsEmpty()) {
				FeatureFlagSet features = level.enabledFeatures();
				HolderLookup.Provider registries = level.registryAccess();
				CreativeModeTabs.tryRebuildTabContents(features, false, registries);
			}

			Map<Item, List<String>> built = new HashMap<>();
			for(CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
				if(tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
				ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
				if(id == null) continue;
				Collection<ItemStack> stacks = tab.getDisplayItems();
				for(ItemStack stack : stacks) {
					List<String> tabs = built.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
					if(!tabs.contains(id.toString())) tabs.add(id.toString());
				}
			}

			if(built.isEmpty()) {
				// Nothing came out: do not cache it, or one early call would blind the filter for good.
				return Map.of();
			}

			index = built;
			return built;

		} catch(RuntimeException | LinkageError exc) {
			// A tab generator from some other mod that cannot run here. Say so once and match
			// nothing rather than retry on every tick of every filter.
			failed = true;
			LeafiaMod.LOGGER.warn("The regex filter could not index the creative tabs; "
					+ "CREATIVE_TAB filters will match nothing: {}", exc.toString());
			return Map.of();
		}
	}

	private static boolean allCategoryTabsEmpty() {
		for(CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
			if(tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.getDisplayItems().isEmpty()) return false;
		}
		return true;
	}
}
