package com.leafia.contents.control.upgrades;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.blockentity.network.RTTYSystem;
import com.hbm.blockentity.network.RTTYSystem.RTTYChannel;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.LeafiaItems;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What a machine with a control upgrade in it does to its other upgrades: caps every one at the number heard
 * on the radio channel the upgrade names.
 *
 * <p>Upstream: {@code MixinUpgradeManagerNT}, two injections. At the head of {@code checkSlotsInternal} it
 * remembers the machine's world and builds a list of the channels of every control upgrade <b>in the slot
 * array it was handed</b>; at the head of {@code getLevel} it answers instead of the original whenever that
 * list is not empty. Here that is a {@link UpgradeManagerNT.LevelProvider} (the base mod's M5 seam, D-2198),
 * registered once by {@link #commonSetup} while {@code content.upgradeControl} is on.
 *
 * <h2>The rule, upstream's exactly</h2>
 *
 * <ul>
 * <li>Only the <b>first</b> control upgrade counts: upstream's loop returns from its first pass.</li>
 * <li>The channel is heard with {@code RTTYSystem.listen}. A channel nobody has broadcast on - and the empty
 * channel of an upgrade nobody has named - gives <b>zero</b>, so a control upgrade with no listener stops every
 * upgrade in the machine, its own type's included.</li>
 * <li>A signal that is not an integer is zero. Otherwise the level is {@code min(signal, the level the
 * machine's upgrades would have had)}: a signal can only lower a level. <b>A negative signal gives a
 * negative level</b> (the minimum is taken as is), kept.</li>
 * </ul>
 *
 * <h2>Which slots</h2>
 *
 * Upstream scans {@code slots}, the machine's <em>whole</em> inventory array (its own copy of just the upgrade
 * slots, {@code upgradeSlots}, is built on the line above and never read), so a control upgrade lying in an
 * ingredient slot counts as much as one in an upgrade slot. The seam hands the provider only the upgrade slots;
 * the machine itself is the manager's {@code owner}, a {@link Container}, and that is scanned instead, to keep
 * upstream's reach. A machine that has never called {@code checkSlots} has no channels, as upstream's list starts
 * empty.
 */
public final class ControlUpgrade {

	private ControlUpgrade() {}

	/** The provider this registers, so a test can take it out again. */
	public static final UpgradeManagerNT.LevelProvider PROVIDER = ControlUpgrade::level;

	/** Common setup: joins the manager's level chain while the switch is on. Idempotent. */
	public static void commonSetup() {
		UpgradeManagerNT.removeLevelProvider(PROVIDER);
		if(LeafiaFeatures.upgradeControl) UpgradeManagerNT.addLevelProvider(PROVIDER);
	}

	/** The channel of every control upgrade in the machine's inventory, in slot order; empty if there is none. */
	public static List<String> channels(UpgradeManagerNT manager) {
		List<String> channels = new ArrayList<>();
		if(manager.getUpgradeSlots() == null) return channels;
		if(!(manager.owner instanceof Container inventory)) return channels;
		for(int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if(!stack.isEmpty() && stack.getItem() instanceof AddonUpgradeItem item && item.isControl())
				channels.add(AddonUpgradeItem.frequency(stack));
		}
		return channels;
	}

	/** Upstream's {@code leafia$onGetLevel}. */
	public static int level(UpgradeManagerNT manager, UpgradeType type, int level) {

		if(LeafiaItems.upgrade_control == null || manager.owner == null) return level;
		Level world = manager.owner.getLevel();
		if(world == null) return level;

		List<String> channels = channels(manager);
		if(channels.isEmpty()) return level;

		RTTYChannel chan = RTTYSystem.listen(world, channels.get(0));
		if(chan == null) return 0;

		int signal = 0;
		try {
			signal = Integer.parseInt("" + chan.signal);
		} catch(Exception ignored) {}
		return Math.min(signal, manager.getLevelDefault(type));
	}
}
