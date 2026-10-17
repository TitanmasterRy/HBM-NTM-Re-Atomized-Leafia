package com.leafia.dev.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import com.hbm.util.i18n.I18nUtil;

/**
 * The one-line badges the addon puts at the top of a machine's tooltip: "Power Generator",
 * "Multiblock", "Unfinished / Extremely buggy".
 *
 * <p>Upstream: {@code com/leafia/dev/machine/MachineTooltip.java}.
 *
 * <h2>The dispatch switch badges the base mod's machines</h2>
 *
 * Two thirds of upstream's file is one {@code switch} over an item's registry path — seventy-odd
 * cases, each naming a machine and calling the badges it wants. D-1028 read it as a table of the
 * addon's own blocks and left it out; it is not. Every name in it is a <b>base-mod</b> machine
 * (condensers, turbines, boilers, the fusion reactor, the RBMK columns, the Watz, the ICF, the
 * particle accelerator), which the addon has no other way to label — its own blocks call the badge
 * they want from {@code appendHoverText}, as {@code WindTurbineMediumBlock} does upstream. So the
 * table is ported as {@link #BADGES} (D-3040). It changes how base items read, so it is behind
 * {@code overhauls.cosmeticOverhaul}, off.
 *
 * <p>{@code addInfoASM}, the reactor-tier line, is here (D-3001): it reads
 * {@link com.leafia.database.ReactorTiers} and asks {@code Screen.hasShiftDown} where upstream polls
 * LWJGL2's {@code Keyboard}. Upstream calls it from a bytecode hook at the head of every tooltip;
 * here a client {@code ItemTooltipEvent} listener does ({@link ReactorTierTooltips}).
 *
 * <p>Upstream's list is {@code List<String>} with {@code TextFormatting} prefixes; 1.21.1's is
 * {@link Component}, so {@link #append} — which upstream implements by string-concatenating onto
 * the last entry — rebuilds the last component instead.
 */
public final class MachineTooltip {

	private MachineTooltip() {}

	/**
	 * Upstream's: glue this badge onto the end of the previous line rather than starting a new
	 * one. Used by the badges that qualify another — "Multiblock -Core Component".
	 *
	 * <p>Does nothing on an empty tooltip, where upstream throws: its callers always follow an
	 * {@code add}, so the guard changes no reachable behaviour and turns a crash into a missing
	 * word if one ever does not.
	 */
	public static void append(List<Component> tooltip, Component s) {
		if(tooltip.isEmpty()) return;
		int last = tooltip.size() - 1;
		tooltip.set(last, Component.empty().append(tooltip.get(last)).append(s));
	}

	/**
	 * The reactor-tier line, which upstream calls "before addInformation()": "RBMK (Tier 3 / Fission
	 * Reactor)" in dark grey italics, then either a hint to hold shift or, with shift held, the tiers
	 * either side. Adds nothing for an item that is not in the table.
	 *
	 * @param shift whether shift is held; passed in so a test can ask both ways
	 */
	public static void addInfoASM(Item item, List<Component> tooltip, boolean shift) {
		addTier(item, tooltip, shift);
	}

	/**
	 * Upstream's {@code switch(item.getRegistryName().getPath())}, as a table from the path to the badges in the order the
	 * case calls them: {@code G} power generator, {@code B} steam, {@code C} condenser, {@code M} multiblock, and the three that
	 * qualify the line before them, {@code N} nuclear, {@code D} modular and {@code K} core component. Keyed by path alone, as
	 * upstream's switch is.
	 */
	public static final java.util.Map<String, String> BADGES = badgeTable();

	private static java.util.Map<String, String> badgeTable() {
		java.util.Map<String, String> map = new java.util.HashMap<>();
		java.util.function.BiConsumer<String, String[]> put = (code, names) -> { for(String name : names) map.put(name, code); };
		put.accept("C", new String[] {"machine_condenser", "machine_condenser_powered", "machine_tower_small", "machine_tower_large"});
		put.accept("G", new String[] {"machine_turbine", "machine_industrial_turbine", "machine_chungus", "machine_generator",
				"machine_diesel", "machine_rtg_grey", "machine_flare", "machine_turbofan", "machine_radgen", "machine_amgen", "machine_geo",
				"machine_minirtg", "rtg_polonium", "machine_spp_bottom", "machine_spp_top"});
		put.accept("B", new String[] {"machine_solar_boiler", "heat_boiler", "machine_industrial_boiler", "machine_hephaestus"});
		put.accept("BN", new String[] {"machine_zirnox"});
		put.accept("MDB", new String[] {"fusion_boiler", "rbmk_boiler", "rbmk_heater"});
		put.accept("MDN", new String[] {"fusion_torus", "rbmk_rod", "rbmk_rod_mod", "rbmk_rod_reasim", "rbmk_rod_reasim_mod"});
		put.accept("MD", new String[] {"fusion_klystron", "fusion_breeder", "fusion_collector", "fusion_coupler", "icf_laser_component",
				"rbmk_blank", "rbmk_moderator", "rbmk_control", "rbmk_control_mod", "rbmk_control_auto", "rbmk_reflector", "rbmk_absorber",
				"rbmk_outgasser", "rbmk_cooler", "rbmk_storage", "pa_beamline", "pa_rfc", "pa_quadrupole", "pa_dipole"});
		put.accept("MDG", new String[] {"fusion_mhdt"});
		put.accept("MK", new String[] {"struct_torus_core", "struct_launcher_core", "struct_launcher_core_large", "struct_soyuz_core", "watz_pump"});
		put.accept("M", new String[] {"fusion_component", "struct_launcher", "struct_scaffold", "watz_element", "watz_cooler", "watz_casing"});
		put.accept("MDK", new String[] {"icf_controller", "pa_source", "pa_detector"});
		put.accept("MBN", new String[] {"watz"});
		put.accept("MKBN", new String[] {"struct_watz_core"});
		put.accept("MKN", new String[] {"struct_icf_core"});
		put.accept("MN", new String[] {"icf_component"});
		return java.util.Map.copyOf(map);
	}

	/**
	 * The badges {@link #BADGES} gives {@code item}, if any: the second half of upstream's {@code addInfoASM}, which the
	 * tooltip listener calls on its own switch ({@link ReactorTierTooltips}), so the tier half keeps its own.
	 */
	public static void addBadges(Item item, List<Component> tooltip) {
		String code = BADGES.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
		if(code == null) return;
		for(char c : code.toCharArray()) {
			switch(c) {
				case 'G' -> addGenerator(tooltip);
				case 'B' -> addBoiler(tooltip);
				case 'C' -> addCondenser(tooltip);
				case 'M' -> addMultiblock(tooltip);
				case 'N' -> addNuclear(tooltip);
				case 'D' -> addModular(tooltip);
				case 'K' -> addCore(tooltip);
				default -> throw new IllegalStateException("no badge " + c);
			}
		}
	}

	/** The reactor-tier half of {@link #addInfoASM}. */
	public static void addTier(Item item, List<Component> tooltip, boolean shift) {
		Integer boxed = com.leafia.database.ReactorTiers.redirection.get(item);
		if(boxed == null) return;
		int tier = boxed;
		java.util.List<String> names = com.leafia.database.ReactorTiers.names;
		String preformat = I18nUtil.resolveKey("desc.leafia._reactortier.desc.title");
		String format = preformat.replace("{tier}", Integer.toString(tier + 1))
				.replace("{name}", I18nUtil.resolveKey("desc.leafia._reactortier.name." + names.get(tier)))
				.replace("{class}", I18nUtil.resolveKey("desc.leafia._reactortier.class." + com.leafia.database.ReactorTiers.classes.get(tier)));
		String prefix = ChatFormatting.DARK_GRAY + "" + ChatFormatting.ITALIC;
		tooltip.add(Component.literal(prefix + format));
		prefix += "  ";
		if(shift) {
			if(tier < names.size() - 1)
				tooltip.add(Component.literal(prefix + I18nUtil.resolveKey("desc.leafia._reactortier.desc.next",
						I18nUtil.resolveKey("desc.leafia._reactortier.name." + names.get(tier + 1)))));
			if(tier > 0)
				tooltip.add(Component.literal(prefix + I18nUtil.resolveKey("desc.leafia._reactortier.desc.previous",
						I18nUtil.resolveKey("desc.leafia._reactortier.name." + names.get(tier - 1)))));
		} else
			tooltip.add(Component.literal(prefix + I18nUtil.resolveKey("desc.leafia._reactortier.desc.shift")));
	}

	public static void addGenerator(List<Component> tooltip) {
		tooltip.add(bracket("trait._machine.power", ChatFormatting.AQUA));
	}

	public static void addBoiler(List<Component> tooltip) {
		tooltip.add(bracket("trait._machine.steam", ChatFormatting.GOLD));
	}

	public static void addCondenser(List<Component> tooltip) {
		tooltip.add(bracket("trait._machine.condenser", ChatFormatting.LIGHT_PURPLE));
	}

	public static void addNuclear(List<Component> tooltip) {
		append(tooltip, dash("trait._machine.nuclear", ChatFormatting.DARK_GREEN));
	}

	public static void addMultiblock(List<Component> tooltip) {
		tooltip.add(bracket("trait._machine.multiblock", ChatFormatting.GRAY));
	}

	public static void addModular(List<Component> tooltip) {
		append(tooltip, dash("trait._machine.modular", ChatFormatting.GREEN));
	}

	public static void addCore(List<Component> tooltip) {
		append(tooltip, dash("trait._machine.multiblock.core", ChatFormatting.RED));
	}

	/** Upstream's wording, unedited. */
	public static void addShit(List<Component> tooltip) {
		tooltip.add(Component.literal("< Obsolete >").withStyle(ChatFormatting.DARK_RED));
	}

	/** Upstream's wording, unedited. */
	public static void addWIP(List<Component> tooltip) {
		tooltip.add(Component.literal("< Unfinished / Extremely buggy >").withStyle(ChatFormatting.DARK_RED));
	}

	/** Upstream's wording, unedited. */
	public static void addBeta(List<Component> tooltip) {
		tooltip.add(Component.literal("< Beta Feature >").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** "(Formerly A, B and C)" — for a block that replaced older ones. */
	public static void addUpdate(List<Component> tooltip, String... oldKeys) {

		StringBuilder buffer = new StringBuilder(Component.translatable(oldKeys[0]).getString());

		for(int i = 1; i < oldKeys.length; i++) {
			buffer.append(i >= oldKeys.length - 1 ? " and " : ", ");
			buffer.append(Component.translatable(oldKeys[i]).getString());
		}

		tooltip.add(Component.literal("(Formerly " + buffer + ")").withStyle(ChatFormatting.DARK_PURPLE));
	}

	private static Component bracket(String key, ChatFormatting colour) {
		return Component.literal("< ").append(Component.translatable(key)).append(" >").withStyle(colour);
	}

	private static Component dash(String key, ChatFormatting colour) {
		return Component.literal(" -").append(Component.translatable(key)).withStyle(colour);
	}
}
