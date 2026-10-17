package com.leafia.contents.fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.render.util.EnumSymbol;
import com.leafia.contents.LeafiaFluids;
import com.leafia.lib.LeafiaRef;

import net.minecraft.resources.ResourceLocation;

/**
 * A fluid the addon adds, rather than one of the base mod's.
 *
 * <p>Upstream: {@code com/leafia/contents/fluids/AddonFluidType.java}. Three things it does that
 * a plain {@link FluidType} does not: it takes an id far away from the base mod's, it can be
 * declared as a <em>copy</em> of an existing fluid, and it puts itself in
 * {@link Fluids#metaOrder}.
 *
 * <h2>The ids start at 1121, and that number is load-bearing</h2>
 *
 * The base mod's fluids are ids 0 upward and each one is pinned — {@code FluidType}'s
 * {@code forcedId} constructor exists to halt the game rather than let an insertion shift them,
 * because a shifted id jumbles every tank in every world. Upstream sidesteps that entirely by
 * starting its own fluids at <b>1121</b> (its comment is just "eevee"), so the two ranges cannot
 * meet however many fluids either side adds. Kept exactly.
 *
 * <h2>Where the texture comes from, and the one deviation</h2>
 *
 * Upstream ships its fluid textures into {@code assets/hbm/} — the base mod's namespace — because
 * {@code FluidType}'s ordinary constructor hardcodes {@code RefStrings.MODID} into the texture
 * path and there was no other way in. This port has one: the {@code setupForeign} constructor
 * takes an explicit {@link ResourceLocation}, so the textures live in {@code assets/leafia/}
 * where they belong and nothing is written into the base mod's asset namespace.
 *
 * <p>That constructor also adds to {@code Fluids.foreignFluids}, and that is <b>a no-op here</b>
 * rather than a side effect being tolerated: the list is read exactly once, inside
 * {@code Fluids.init()}, which has already run by the time this jar constructs anything. Checked
 * rather than assumed — see D-1023.
 *
 * <p>A copy-constructed fluid reuses its base's texture object outright, so {@code UF6_233} is
 * drawn with the base mod's UF6 texture and the addon ships nothing for it. Upstream achieves the
 * same by passing the base's <em>name</em> as the texture name.
 *
 * <h2>Copied traits are deferred, not copied here</h2>
 *
 * The copy constructors record themselves in {@link LeafiaFluids#copyTraits} instead of
 * copying traits immediately, and upstream's commented-out {@code copyTraits(...)} calls show it
 * used to do the eager thing. The reason is ordering: a copy has to happen *after* the traits
 * pass has finished adding to the base fluid, or a fluid copied early gets a half-filled trait
 * map. {@link #addTraits} records what the copy declared for itself so the later pass does not
 * overwrite it — that is what {@link #overrideTraits} is for.
 */
public class AddonFluidType extends FluidType {

	/**
	 * Upstream's, and the reason the ranges never meet. Public and mutable because upstream
	 * increments it by hand when it adopts a base-mod fluid instead of creating one, to keep
	 * every later id where it was.
	 */
	public static final int ID_BASE = 1121;
	public static int id = 0;

	/** Traits this fluid declared itself; the copy pass leaves these alone. */
	public List<Class<? extends FluidTrait>> overrideTraits = new ArrayList<>();

	/** Which of the base's traits to take, or null for a fluid that is not a copy. */
	public Function<FluidTrait, Boolean> copyFunction = null;

	public AddonFluidType(String name, int color, int p, int f, int r, EnumSymbol symbol) {
		this(name, color, p, f, r, symbol, texture(name));
	}

	public AddonFluidType(String name, int color, int p, int f, int r, EnumSymbol symbol,
			ResourceLocation texture) {

		super(name, ID_BASE + (id++), color, p, f, r, symbol, texture);
		Fluids.metaOrder.add(this);
	}

	/** A fluid that is another one with a different name — every trait carried over. */
	public AddonFluidType(String name, FluidType base) {
		this(name, base, trait -> true);
	}

	/** A fluid that is another one, keeping only the traits {@code copyFunction} accepts. */
	public AddonFluidType(String name, FluidType base, Function<FluidTrait, Boolean> copyFunction) {

		this(name, base.getColor(), base.poison, base.flammability, base.reactivity, base.symbol,
				base.getTexture());

		this.temperature = base.temperature;
		this.copyFunction = copyFunction;
		LeafiaFluids.copyTraits.put(this, base);
	}

	private static ResourceLocation texture(String name) {
		return ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID,
				"textures/gui/fluids/" + name.toLowerCase(Locale.US) + ".png");
	}

	/** Upstream's, including that a declared trait beats an inherited one of the same class. */
	public void copyTraits(FluidType other, Function<FluidTrait, Boolean> copyFunction) {

		for(Map.Entry<Class<? extends FluidTrait>, FluidTrait> entry : other.traits.entrySet()) {

			if(overrideTraits.contains(entry.getKey())) continue;
			if(copyFunction.apply(entry.getValue())) this.traits.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public FluidType addTraits(FluidTrait... traits) {

		for(FluidTrait trait : traits) overrideTraits.add(trait.getClass());
		return super.addTraits(traits);
	}
}
