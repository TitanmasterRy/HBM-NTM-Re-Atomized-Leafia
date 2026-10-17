package com.leafia.contents.machines.processing.mixingvat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.leafia.lib.LeafiaRef;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * The older mixing vat model: the one thing in the addon that wears it is the guide scene for the LFTR (D-3029).
 *
 * <p>Upstream: {@code .../mixingvat/MixingVatRender.java}, a {@code TileEntitySpecialRenderer} and an item renderer that upstream registers
 * for <b>nothing</b>: the vat the player places is drawn by {@link MixingVatRenderNeo} (D-3016), and this class's {@code mdl},
 * {@code tex} and {@code rsc} are read only by the Cannery's LFTR scene, whose {@code ActorMixingVat} draws the machine inside a jar.
 * So what is ported is what that scene reads, and not the two renderers, which nothing could construct:
 *
 * <ul>
 * <li>{@code mdl} is the OBJ, six objects ({@link #PARTS}), copied by a generator script with a material
 * library of its own and one baked model per object, since {@code renderPart} has no successor;</li>
 * <li>{@code tex} and {@code rsc} are the two textures, as block-atlas sprites: the vat's and the fluid's.</li>
 * </ul>
 *
 * <p>The drawing, which upstream wrote out twice (here and in the scene's actor), is the actor's, in {@code CanneryActorRenders}.
 */
public class MixingVatRender {

	/** Upstream's {@code mdl}: the OBJ every part model is a view of. */
	public static final ResourceLocation mdl = LeafiaRef.rl("models/blocks/mixingvat_old.obj");
	/** Upstream's {@code tex}: the vat, as a block-atlas sprite. */
	public static final ResourceLocation tex = LeafiaRef.rl("block/mixingvat_old");
	/** Upstream's {@code rsc}: the fluid, as a block-atlas sprite. */
	public static final ResourceLocation rsc = LeafiaRef.rl("block/mixingvat_old_fluid");

	/** The OBJ's six objects, in the order the scene's actor draws them (upstream's {@code renderPart} order). */
	public static final String[] PARTS = {"Base", "MixingBlade", "VatGlass", "Vat", "Tanks", "VatLiquid"};

	/** The model path of one part ({@code block/mixingvat_old_base} ...). */
	public static String partModel(String part) {
		return "block/mixingvat_old_" + part.toLowerCase(Locale.ROOT);
	}

	/** One part's standalone model. */
	public static ModelResourceLocation part(String part) {
		return ModelResourceLocation.standalone(LeafiaRef.rl(partModel(part)));
	}

	/** Every part's model, for {@code ModelEvent.RegisterAdditional}. */
	public static List<ModelResourceLocation> all() {
		List<ModelResourceLocation> all = new ArrayList<>();
		for(String part : PARTS) all.add(part(part));
		return all;
	}
}
