package com.leafia.overwrite_contents;

import com.hbm.blocks.generic.BlockBobble.BobbleType;
import com.hbm.items.special.ItemPlasticScrap.ScrapType;
import com.leafia.lib.LeafiaRef;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * The addon's bobblehead, appended to the base mod's {@code BlockBobble.BobbleType} by NeoForge's enum extension (D-2207's seam,
 * D-3041) - upstream's {@code BobbleType.LEAFIA}, which the cursed fork's own copy of the base enum carried.
 *
 * <p>The loader requires a constant's name to start with the mod id, so the figure is {@code LEAFIA_LEAFIA}: the serialized name (the
 * blockstate's variant) is {@code leafia_leafia} and the base registers its item as {@code hbm:bobblehead_leafia_leafia} in its
 * blocks tab, found by the centrifuge row ({@code LeafiaBaseRecipes}) as {@code bobbleItem("leafia_leafia")}. It exists whether or
 * not any feature switch is on, because the extension is applied as the enum loads, before any config; it is a base-mod decoration
 * and not content a switch could gate.
 *
 * <p>The constructor's strings are the fork's, which is not among the reference sources (only the recipes that name the figure are):
 * the name, label, contribution, inscription and shredder scrap below are this port's own and are flagged in D-3041. The skin is the
 * Vault Boy's layout recoloured, named for the client in {@link #skin()}.
 */
public final class LeafiaBobbleheads {

	private LeafiaBobbleheads() {}

	public static final EnumProxy<BobbleType> LEAFIA_PROXY = new EnumProxy<>(BobbleType.class,
			"Leafia", "Leafia", "Leafia's Cursed Addon", "Everything is fine. The reactor is fine.", false, ScrapType.CPU_REGISTER);

	/** The figure; touching the enum first, because a proxy is only filled in by the enum's own static initialiser. */
	public static BobbleType leafia() {
		BobbleType.values();
		return LEAFIA_PROXY.getValue();
	}

	/** The figure's skin: the Vault Boy's layout under the addon's colours. */
	public static ResourceLocation skin() {
		return LeafiaRef.rl("textures/models/trinkets/leafia.png");
	}
}
