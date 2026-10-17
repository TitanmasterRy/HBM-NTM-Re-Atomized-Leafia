package com.leafia.contents;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Poison;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FT_VentRadiation;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.render.util.EnumSymbol;
import com.leafia.LeafiaMod;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.fluids.AddonFluidType;
import com.leafia.contents.fluids.traits.FT_LFTRCoolant;

/**
 * The fourteen fluids the addon adds.
 *
 * <p>Upstream: {@code com/leafia/contents/AddonFluids.java}, and this is a <b>partial</b> port —
 * the upstream file is not counted as ported. What is missing is the whole
 * Forge-fluid bridge: upstream's {@code AddonFF} inner class registers four
 * {@code net.minecraftforge.fluids.Fluid}s and {@code addCompatFluid}/{@code fromFF} map between
 * them and NTM's own {@code FluidType}. This port has no such bridge — {@code FluidType} has
 * neither {@code getFF} nor {@code setFFNameOverride} — so those, and the two
 * {@code setFFNameOverride} calls in {@code init}, are a base-mod hand-off rather than work that
 * can be done here. See D-1023.
 *
 * <h2>Two things upstream needs and this does not</h2>
 *
 * Upstream reaches {@code Fluids.metaOrder} by reflection, catching
 * {@code NoSuchFieldException} and rethrowing it as a dev-flaw exception — because the field is
 * private in NTM-CE. It is <b>public</b> here, so {@code metaOrderPointer}, the reflection and
 * the exception all disappear. That is the same shape as D-019's dissolved renderers, arriving
 * in a place nobody expected it.
 *
 * <p>And the textures live in {@code assets/leafia/} rather than in the base mod's namespace —
 * see {@link AddonFluidType}, which is where that became possible.
 *
 * <h2>One line moved, deliberately</h2>
 *
 * Upstream's {@code init} contains {@code COOLANT_HOT.temperature = 400}, dropping a
 * <em>base-mod</em> fluid's temperature from 600. That is a rebalance of the base mod rather
 * than an addition to it, so it does not belong on this switch, which defaults on. It lives with
 * the heat-step chain in {@link com.leafia.init.LeafiaFluidTraits} instead, behind
 * {@code fluidTraits}, which defaults off. Same numbers, same effect when both are on; the split
 * is so that installing the jar does not quietly cool the base mod's coolant. See D-1023.
 *
 * <h2>These fluids do nothing yet</h2>
 *
 * Most consumers are unported: the LFTR drinks {@code FLUORIDE}, the DFC burns {@code N2O} and
 * {@code PYROGEL}, the APR runs on {@code OXYGEN_GAS}. The modular turbine is the exception — it
 * runs on {@code DEATHSTEAM} (D-1058). So the rest exist, can be held in a tank and are named
 * correctly, and nothing makes or spends one. That is worth saying plainly rather than letting
 * "landed" imply "reachable".
 */
public final class LeafiaFluids {

	private LeafiaFluids() {}

	/** Upstream's own note: this salt wants Lithium-7 — Lithium-6 absorbs a neutron and becomes tritium and helium-4. */
	public static FluidType FLUORIDE;
	public static FluidType UF6_233;
	public static FluidType UF6_235;
	public static FluidType HOT_WATER;
	public static FluidType HOT_AIR;
	public static FluidType RADSPICE_SLOP;
	public static FluidType COOLANT_MAL;
	public static FluidType DEATHSTEAM;
	public static FluidType HF;
	/** Upstream's comment, verbatim: "will you stop begging me". */
	public static FluidType N2O;
	/** Upstream's comment, verbatim: "oh boy fluorine don't exists". */
	public static FluidType FLUORINE;
	public static FluidType PYROGEL;
	public static FluidType CRYOINTER;
	/**
	 * Gaseous oxygen: upstream's fourteenth fluid, added after the other thirteen were ported and
	 * declared after them, so it takes the next id and moves nobody's. What makes it is the
	 * boiler step on the base mod's liquid oxygen, which rides {@code fluidTraits} (D-1068).
	 */
	public static FluidType OXYGEN_GAS;

	private static boolean applied = false;

	/**
	 * Addon fluids declared as copies of a base-mod fluid, and what they copy from.
	 *
	 * <p>Filled by {@link AddonFluidType}'s copy constructors and drained by {@link #applyCopies()}.
	 * The indirection is upstream's and it is about ordering: a fluid copied at construction time
	 * would take a half-filled trait map, because the trait pass is still adding to the fluid it
	 * copies from.
	 */
	public static final Map<AddonFluidType, FluidType> copyTraits = new HashMap<>();

	/** True when the fluids were actually created, so the traits pass knows it may name them. */
	public static boolean present() {
		return applied;
	}

	/**
	 * Creates all fourteen. Called from the mod constructor, before
	 * {@link com.leafia.init.LeafiaFluidTraits#init()}, and that order matters: three of the
	 * traits name fluids declared here.
	 *
	 * <p>Idempotent, and it has to be — a second call would allocate a second set of ids and
	 * leave {@code metaOrder} holding both.
	 */
	public static void init() {

		if(!LeafiaFeatures.addonFluids || applied) return;
		applied = true;

		/*
		 * HOT_WATER and HOT_AIR are water and air that are merely warm, so they must not inherit
		 * the boil/condense chain that would turn them into something else. Upstream's predicate,
		 * unchanged.
		 */
		Function<FluidTrait, Boolean> rejectBoiling = trait ->
				!(trait instanceof FT_Heatable) && !(trait instanceof FT_Coolable);

		FLUORIDE = new AddonFluidType("FLIBE", 0xd3d8b9, 5, 0, 0, EnumSymbol.NONE)
				.setTemp(500)
				.addTraits(Fluids.LIQUID,
						new FT_Polluting()
								.release(PollutionType.POISON, Fluids.POISON_EXTREME / 2)
								.release(PollutionType.HEAVYMETAL, Fluids.LEAD_FUEL),
						new FT_LFTRCoolant(1));

		UF6_233 = new AddonFluidType("UF6_233", Fluids.UF6);
		UF6_235 = new AddonFluidType("UF6_235", Fluids.UF6);
		HOT_WATER = new AddonFluidType("HOT_WATER", Fluids.WATER, rejectBoiling).setTemp(70);
		HOT_AIR = new AddonFluidType("HOT_AIR", Fluids.AIR, rejectBoiling).setTemp(50);

		RADSPICE_SLOP = new AddonFluidType("RADSPICE_SLOP", 0x8baf2d, 9999999, 99999999, 9999999, EnumSymbol.RADIATION)
				.addTraits(Fluids.LIQUID, new FT_VentRadiation(20_000 / 1000F), Fluids.VISCOUS);

		COOLANT_MAL = new AddonFluidType("COOLANT_MAL", 0x880f12, 1, 0, 0, EnumSymbol.NONE)
				.setTemp(1000).addTraits(Fluids.GASEOUS);

		DEATHSTEAM = new AddonFluidType("DEATHSTEAM", 0x7c0000, 4, 0, 0, EnumSymbol.NONE)
				.setTemp(900).addTraits(Fluids.GASEOUS, Fluids.UNSIPHONABLE);

		HF = new AddonFluidType("HF", 0x3ea7ff, 4, 0, 1, EnumSymbol.ACID)
				.addTraits(Fluids.GASEOUS, new FT_Corrosive(40), new FT_Poison(true, 1));

		N2O = new AddonFluidType("N2O", 0x6faf30, 2, 0, 0, EnumSymbol.OXIDIZER)
				.addTraits(Fluids.GASEOUS);

		/*
		 * Upstream adopts the base mod's FLUORINE if it has one, and increments the id counter by
		 * hand so that every fluid after this keeps the id it would have had either way. This
		 * port's base mod has no FLUORINE, so the second branch runs - but the first is kept,
		 * because the day the base mod gains one is the day the ids would otherwise shift.
		 */
		if(Fluids.fromName("FLUORINE") != Fluids.NONE) {
			FLUORINE = Fluids.fromName("FLUORINE");
			AddonFluidType.id++;
		} else {
			FLUORINE = new AddonFluidType("FLUORINE", 0xc5b055, 4, 0, 4, EnumSymbol.NOWATER)
					.addTraits(Fluids.GASEOUS);
		}

		PYROGEL = new AddonFluidType("PYROGEL", 0xffa53b, 5, 0, 0, EnumSymbol.NOWATER)
				.setTemp(2250).addTraits(Fluids.LIQUID, Fluids.VISCOUS);

		CRYOINTER = new AddonFluidType("CRYOINTER", 0x92cabe, 2, 0, 0, EnumSymbol.CROYGENIC)
				.setTemp(-90).addTraits(Fluids.LIQUID);

		OXYGEN_GAS = new AddonFluidType("OXYGEN_GAS", 0x9fd5eb, 0, 0, 0, EnumSymbol.OXIDIZER)
				.addTraits(Fluids.GASEOUS).setTemp(20);

		LeafiaMod.LOGGER.info("Addon fluids created: 14, ids {}-{}.",
				AddonFluidType.ID_BASE, AddonFluidType.ID_BASE + AddonFluidType.id - 1);
	}

	/**
	 * Gives each copied fluid its base's traits. Called after
	 * {@link com.leafia.init.LeafiaFluidTraits#init()} and <b>not gated on it</b>.
	 *
	 * <h2>This ran on the wrong switch, and a test caught it</h2>
	 *
	 * Upstream ends its trait pass with this loop, so the obvious port puts it there too — and
	 * that is what was written first. It is wrong here, because this port splits upstream's one
	 * pass across two switches: with {@code fluidTraits} off, which is its default, the loop never
	 * ran and {@code HOT_WATER} was not a liquid. A copy is a property of <em>being a copy</em>,
	 * not of the trait overhaul, so it belongs on the fluids' own switch.
	 *
	 * <p>It still runs <em>after</em> the trait pass, which is upstream's ordering and matters:
	 * {@code UF6_233} and {@code UF6_235} should inherit the DFC rating the overhaul hangs on
	 * {@code UF6}, when that overhaul is on. See D-1023.
	 */
	public static void applyCopies() {

		if(!applied) return;

		for(Map.Entry<AddonFluidType, FluidType> entry : copyTraits.entrySet()) {
			if(entry.getKey().copyFunction != null) {
				entry.getKey().copyTraits(entry.getValue(), entry.getKey().copyFunction);
			}
		}
	}
}
