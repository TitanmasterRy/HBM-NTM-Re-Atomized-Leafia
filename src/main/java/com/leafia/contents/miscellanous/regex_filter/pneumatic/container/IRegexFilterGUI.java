package com.leafia.contents.miscellanous.regex_filter.pneumatic.container;

/**
 * What the block entity tells whichever of the filter's two screens is open when its list changes.
 *
 * <p>Upstream's, unchanged. It is an interface rather than a field of the screen's own type so
 * that {@code RegexFilterTE} never names a client class: a dedicated server loads the block entity
 * and must not load a {@code Screen}.
 */
public interface IRegexFilterGUI {
	void onSyncFilters();
}
