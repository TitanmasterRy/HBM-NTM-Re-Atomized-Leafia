package com.leafia.gametest;

import java.util.HashSet;
import java.util.Set;

import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem;
import com.leafia.contents.machines.elevators.car.styles.EvStyleItem.StyleType;
import com.leafia.creativetabs.LeafiaCreativeTabs;
import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The six cabin style items — the addon's first items, and the first use of {@link LeafiaItems}.
 *
 * <p>The interesting property here is a <em>negative</em> one: these must be registered and must
 * <b>not</b> be reachable from the creative tab, because none of them has a flat texture and the
 * renderer that draws them is deferred (D-1035, D-1036). A regression in either direction is
 * invisible — putting them in the tab shows six missing-texture entries, and dropping the
 * registration breaks the car's style slots — so both are pinned.
 */
@GameTestHolder(LeafiaRef.MODID)
@PrefixGameTestTemplate(false)
public class LeafiaElevatorStyleTests {

	private static final String EMPTY = "empty";

	/** Upstream's six ids, upstream's six types. */
	@GameTest(template = EMPTY)
	public static void theSixStylesAreUpstreamsSixStyles(GameTestHelper helper) {

		helper.assertTrue(LeafiaItems.style_items.size() == 6,
				"a cabin has six styles, got " + LeafiaItems.style_items.size());

		assertStyle(helper, "s6floor", StyleType.FLOOR);
		assertStyle(helper, "s6ceiling", StyleType.CEILING);
		assertStyle(helper, "s6door", StyleType.WALL);
		assertStyle(helper, "s6wall", StyleType.WALL);
		assertStyle(helper, "s6window", StyleType.WALL);
		assertStyle(helper, "skyliftdoor", StyleType.WALL);

		helper.succeed();
	}

	private static void assertStyle(GameTestHelper helper, String id, StyleType type) {

		var holder = LeafiaItems.styles_by_id.get(id);
		helper.assertTrue(holder != null, "no style registered for id " + id);

		EvStyleItem item = holder.get();
		helper.assertTrue(item.getStyleId().equals(id),
				id + ": the style id is derived from the registry name, got " + item.getStyleId());
		helper.assertTrue(item.type == type,
				id + ": should be a " + type + ", is a " + item.type);
	}

	/**
	 * The id is {@code ev_}-chopped from the registry name, which is how upstream does it and how
	 * a player's saved cabin already reads.
	 *
	 * <p>Passing the id in as a second argument would let the two drift — a cabin holding
	 * {@code s6wall} would stop matching an item registered as {@code ev_s6wall}, and the panel
	 * would silently fail to load rather than erroring.
	 */
	@GameTest(template = EMPTY)
	public static void theStyleIdIsTheRegistryNameWithoutTheEvPrefix(GameTestHelper helper) {

		for(var holder : LeafiaItems.style_items) {

			ResourceLocation key = BuiltInRegistries.ITEM.getKey(holder.get());
			String path = key.getPath();

			helper.assertTrue(key.getNamespace().equals(LeafiaRef.MODID),
					"a style must register under the addon, got " + key);
			helper.assertTrue(path.startsWith("ev_"),
					"upstream's names all start with ev_, got " + path);
			helper.assertTrue(holder.get().getStyleId().equals(path.substring(3)),
					path + ": the id must be the name without ev_, got " + holder.get().getStyleId());
		}

		helper.succeed();
	}

	/**
	 * Registered, reachable from creative, and each with a model to draw.
	 *
	 * <p><b>This test was the inverse of itself until D-1043</b>, and said so: it asserted that
	 * no style was reachable, because all six are drawn out of the cabin OBJ by a renderer that
	 * was deferred, and it carried the line <em>"when the renderer lands this test is what tells
	 * whoever writes it to change the tab argument"</em>. The renderer landed — as eight
	 * generated item models rather than a class — so the argument changed and so did this.
	 *
	 * <p>Keeping it pointed the other way is the point: the rule it enforces is
	 * <em>registered content is reachable once it can be drawn</em>, and the item-model check is
	 * what stops that becoming six missing-texture entries. D-1042 found nine of those left by
	 * three batches; asserting the model here as well as the tab is what makes the pair a rule
	 * rather than two separate habits.
	 *
	 * <p>It asks {@link LeafiaCreativeTabs#contentsOf} rather than the tab itself, because a tab's
	 * {@code getDisplayItems()} is empty on a dedicated server — the first draft of this test asked
	 * the tab, got nothing back, and would have passed for the wrong reason if the guard assertion
	 * below had not been there first.
	 */
	@GameTest(template = EMPTY)
	public static void everyStyleIsReachableAndHasAModel(GameTestHelper helper) {

		Set<Item> listed = new HashSet<>();
		for(var supplier : LeafiaCreativeTabs.contentsOf(LeafiaCreativeTabs.MAIN)) {
			listed.add(supplier.get().asItem());
		}

		helper.assertTrue(!listed.isEmpty(),
				"the tab should have the addon's blocks in it, or this test proves nothing");

		for(var holder : LeafiaItems.style_items) {
			helper.assertTrue(listed.contains(holder.get()),
					holder.getId() + " is drawn now (D-1043) and should be in the creative tab");
			helper.assertTrue(LeafiaElevatorStyleTests.class.getResourceAsStream(
							"/assets/leafia/models/item/" + holder.getId().getPath() + ".json") != null,
					holder.getId() + " is in the tab with no item model, which is a purple cube");
		}

		helper.succeed();
	}

	/** A cabin takes one of each panel, so upstream stacks them to one. */
	@GameTest(template = EMPTY)
	public static void aStyleDoesNotStack(GameTestHelper helper) {

		for(var holder : LeafiaItems.style_items) {
			helper.assertTrue(holder.get().getDefaultMaxStackSize() == 1,
					holder.getId() + " should not stack, its max is "
							+ holder.get().getDefaultMaxStackSize());
		}

		helper.succeed();
	}
}
