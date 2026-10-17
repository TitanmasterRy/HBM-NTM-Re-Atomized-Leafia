package com.leafia.contents.control.upgrades;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The one place a client opens {@link UpgradeControlScreen}. The item reaches it only on the logical client,
 * so a dedicated server never loads the screen's class (upstream's {@code @SideOnly} method).
 */
@OnlyIn(Dist.CLIENT)
final class UpgradeControlClient {

	private UpgradeControlClient() {}

	static void open(String freq) {
		Minecraft.getInstance().setScreen(new UpgradeControlScreen(freq));
	}
}
