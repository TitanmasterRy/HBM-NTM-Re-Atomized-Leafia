package com.leafia.overwrite_contents.interfaces;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.hbm.items.ModItems;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.machines.powercores.dfc.IDFCBase;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The overhauled DFC stabilizer: a 10-trillion HE buffer, a reach of 50 blocks, and one of six lenses,
 * each with the two colours the renderer draws its beam in.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityCoreStabilizer.java}. The
 * implementor is DFC-3's subclass of the base stabilizer. The lens items are suppliers here because
 * five of the six are the addon's and are registered by a switch ({@code features.ams}); a lens whose
 * item is not registered answers null. {@code IControllable} was imported and never used upstream.
 */
public interface IMixinTileEntityCoreStabilizer extends IDFCBase {
	long maxPower = 10000000000000L;
	int range = 50;

	enum LensType {
		STANDARD(0x0c222c, 0x7F7F7F, () -> ModItems.ams_lens.get()),
		BLANK(0x121212, 0x646464, () -> item(LeafiaItems.ams_focus_blank)),
		LIMITER(0x001733, 0x7F7F7F, () -> item(LeafiaItems.ams_focus_limiter)),
		BOOSTER(0x4f1600, 0x7F7F7F, () -> item(LeafiaItems.ams_focus_booster)),
		OMEGA(0x64001e, 0x9A9A9A, () -> item(LeafiaItems.ams_focus_omega)),
		SAFE(0x166800, 0x73ec51, () -> item(LeafiaItems.ams_focus_safe));

		public final int outerColor;
		public final int innerColor;
		public final Supplier<Item> item;

		LensType(int outerColor, int innerColor, Supplier<Item> item) {
			this.outerColor = outerColor;
			this.innerColor = innerColor;
			this.item = item;
		}

		@Nullable
		private static Item item(@Nullable DeferredItem<? extends Item> holder) {
			return holder == null ? null : holder.get();
		}
	}

	boolean hasLens();

	LensType getLens();
}
