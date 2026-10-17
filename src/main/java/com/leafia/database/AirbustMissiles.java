package com.leafia.database;

import java.util.ArrayList;
import java.util.List;

import com.hbm.items.ModItems;

import net.minecraft.world.item.Item;

/**
 * The base-mod missiles that burst in the air over their target (D-3022), read by
 * {@code IMixinEntityMissileBaseNT.shouldDetonateInAir} when {@code overhauls.missileOverhaul} is on.
 *
 * <p>Upstream's list, in upstream's order, less one: <b>{@code missile_n2} is not here.</b> NTM-CE has an N2
 * missile; the 1.7.10 base mod - the specification this port's base follows - has none, and the base mod here
 * has no such item, entity or recipe. Registering one in the addon's namespace (D-1097 #3's rule for CE-only
 * <em>items</em>) would mean porting CE's whole missile - entity, warhead, model and pad support - none of which
 * is an addon file; the addon's own file only names it in this list. So it is dropped: nothing could ever match
 * the entry. If the base mod gains {@code missile_n2}, it belongs first in this list.
 *
 * <p>Filled in common setup, after the base mod's items exist. Upstream's {@code init()}.
 */
public final class AirbustMissiles {

	private AirbustMissiles() {}

	public static final List<Item> defaultAirDetonationMissiles = new ArrayList<>();

	public static void init() {
		defaultAirDetonationMissiles.clear();
		defaultAirDetonationMissiles.add(ModItems.missile_micro.get());
		defaultAirDetonationMissiles.add(ModItems.missile_nuclear.get());
		defaultAirDetonationMissiles.add(ModItems.missile_nuclear_cluster.get());
		defaultAirDetonationMissiles.add(ModItems.missile_doomsday.get());
		defaultAirDetonationMissiles.add(ModItems.missile_emp.get());
		defaultAirDetonationMissiles.add(ModItems.missile_emp_strong.get());
	}
}
