package com.leafia.unsorted.fluids;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.leafia.contents.LeafiaFluids;
import com.leafia.lib.LeafiaRef;

import net.minecraft.resources.ResourceLocation;

/**
 * The marks the addon draws over a fluid's icon: a lime pip on enriched uranium-235 hexafluoride and an orange one
 * on uranium-233's.
 *
 * <p>Upstream: {@code com/leafia/unsorted/fluids/FluidIconOverlays.java}, the table that {@code MixinItemFluidIcon}
 * read. That mixin redirected the icon's model registration, baked a two-layer {@code item/generated} model per
 * overlay and stitched the overlay sprites by hand. Here the base mod's {@code ItemFluidIcon.addOverlayProvider}
 * (D-2198) does the choosing - {@link #overlayModel} is the provider - and the models are plain JSON the client
 * registers with {@code ModelEvent.RegisterAdditional}: layer 0 is the base icon, tinted with the fluid's colour as
 * the plain one is, and layer 1 is the overlay, which keeps its own colours. The sprites are upstream's.
 */
public class FluidIconOverlays {

	/** Where the overlay sprites live, under {@code textures/item/}. */
	public static String prefix = "item/fluid_icon_overlays/";

	/** The overlays there are, in upstream's order. */
	public static List<String> overlays = new ArrayList<>();

	static {
		overlays.add("isotope_lime");
		overlays.add("isotope_orange");
	}

	/** Which overlay a fluid wears, or null for none. */
	@Nullable
	public static String getOverlay(FluidType fluid) {
		if(fluid.equals(LeafiaFluids.UF6_233)) return "isotope_orange";
		if(fluid.equals(LeafiaFluids.UF6_235)) return "isotope_lime";
		return null;
	}

	/** The standalone model of one overlay: {@code leafia:item/fluid_icon_<overlay>}. */
	public static ResourceLocation model(String overlay) {
		return LeafiaRef.rl("item/fluid_icon_" + overlay);
	}

	/** The base mod's {@code ItemFluidIcon.OverlayProvider}: the model for a fluid with an overlay, otherwise null. */
	@Nullable
	public static ResourceLocation overlayModel(FluidType fluid) {
		String overlay = getOverlay(fluid);
		return overlay == null ? null : model(overlay);
	}
}
