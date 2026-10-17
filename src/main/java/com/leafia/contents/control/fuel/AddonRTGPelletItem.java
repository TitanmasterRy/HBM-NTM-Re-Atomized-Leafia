package com.leafia.contents.control.fuel;

import java.util.function.Supplier;

import javax.annotation.CheckForNull;

import com.hbm.items.machine.ItemRTGPelletDepleted.DepletedRTGMaterial;
import com.hbm.items.machine.ItemRTGPellet;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An RTG pellet that decays into one of the addon's items instead of one of the base mod's six depleted
 * pellets.
 *
 * <p>Upstream: {@code com/leafia/contents/control/fuel/AddonRTGPelletItem.java}. Its one instance is
 * {@code pellet_rtg_nc279} - heat 120, decaying after 2,000,000 x 4 ticks into {@code pellet_rtg_fissium}.
 *
 * <h2>No reflection</h2>
 *
 * Upstream calls NTM-CE's {@code setDecays(MERCURY, halflife, halflifes)} for its bookkeeping and then
 * overwrites the private {@code decayItem} field through reflection, and puts the pair into CE's
 * {@code pelletMap} by hand. The base mod's {@link ItemRTGPellet} keeps the material and answers
 * {@link #getDecayItem()} through a public method, so overriding that method is the whole job:
 * {@code handleDecay}, the furnace and {@code getRecipeMap} all ask it. The mercury material upstream
 * passes is kept as the recorded material - it is never what comes out.
 *
 * <h2>The life, from CE</h2>
 *
 * The base mod's {@code setDecays} takes one lifespan; CE's takes a half-life and a count and stores
 * their product ({@code lifespan = halflife * halflifes}, read out of the CE jar's bytecode), which is
 * what {@link #setDecays(Supplier, long, int)} passes down. {@code ModItems.ALL_ITEMS}/{@code AddonItems.ALL_ITEMS}
 * are 1.12.2's model-registration lists and dissolve into the registry.
 */
public class AddonRTGPelletItem extends ItemRTGPellet {

	private Supplier<? extends Item> decayProduct = null;

	public AddonRTGPelletItem(Properties properties, int heatIn) {
		super(properties, heatIn);
	}

	/** Upstream's {@code setDecays(item, halflife, halflifes)}: the product, and a life of their product. */
	public AddonRTGPelletItem setDecays(Supplier<? extends Item> item, long halflife, int halflifes) {
		super.setDecays(DepletedRTGMaterial.MERCURY, halflife * halflifes);
		this.decayProduct = item;
		return this;
	}

	@Override
	@CheckForNull
	public ItemStack getDecayItem() {
		if(decayProduct == null) return super.getDecayItem();
		return new ItemStack(decayProduct.get());
	}
}
