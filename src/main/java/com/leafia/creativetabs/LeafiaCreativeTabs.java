package com.leafia.creativetabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.leafia.lib.LeafiaRef;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's creative tabs, mirroring {@code com.hbm.creativetabs.ModCreativeTabs}.
 *
 * <p>The addon gets its own tabs rather than appending to the base mod's, for the same reason
 * it gets its own jar: with the addon uninstalled nothing about the base mod should look
 * different, and an entry appearing in an {@code hbm} tab is exactly that kind of difference.
 *
 * <p>Contents are collected as content registers — {@link #add} is called from the registry
 * holders — and handed over when the game builds the tab. Anything gated off by
 * {@link com.leafia.config.LeafiaFeatures} never calls {@link #add} at all, because it never
 * registers (D-1002).
 */
public final class LeafiaCreativeTabs {

	private LeafiaCreativeTabs() {}

	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LeafiaRef.MODID);

	/** Tab id, doubling as the registry path and the key into {@link #CONTENTS}. */
	public static final String MAIN = "main";

	private static final Map<String, List<Supplier<? extends ItemLike>>> CONTENTS = new HashMap<>();

	/** Called from the content registries as each block or item registers. */
	public static void add(String tab, Supplier<? extends ItemLike> content) {
		if(tab == null) return; // deliberately hidden content, as upstream does with null
		CONTENTS.computeIfAbsent(tab, k -> new ArrayList<>()).add(content);
	}

	private static final Map<String, List<Supplier<List<ItemStack>>>> STACKS = new HashMap<>();

	/**
	 * For an item that appears once per variant held on the stack rather than per registration -
	 * the fluid bottle, one per fluid. The list is built when the tab is, so it sees every fluid.
	 */
	public static void addStacks(String tab, Supplier<List<ItemStack>> stacks) {
		if(tab == null) return;
		STACKS.computeIfAbsent(tab, k -> new ArrayList<>()).add(stacks);
	}

	/**
	 * What {@link #add} has filed under {@code tab} so far, for tests.
	 *
	 * <p>A test seam, and it exists because the obvious alternative does not work: a tab's
	 * {@code getDisplayItems()} is empty on a dedicated server, since contents are built with
	 * client-side display parameters. A GameTest asking the tab directly gets an empty list and
	 * passes whatever it was checking — which is exactly what happened to the first draft of
	 * {@code noStyleIsReachableFromTheCreativeTab}, and only a guard assertion caught it.
	 *
	 * <p>This is also the more honest thing to assert against: the question a test wants answered
	 * is "did this content register with a null tab", and this map is where that decision lands.
	 */
	public static List<Supplier<? extends ItemLike>> contentsOf(String tab) {
		return List.copyOf(CONTENTS.getOrDefault(tab, List.of()));
	}

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register(MAIN,
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.leafia.main"))
					.icon(() -> new ItemStack(Items.NETHER_STAR))
					.displayItems((params, output) -> {
						CONTENTS.getOrDefault(MAIN, List.of()).forEach(supplier -> output.accept(supplier.get()));
						STACKS.getOrDefault(MAIN, List.of()).forEach(supplier -> output.acceptAll(supplier.get()));
					})
					.build());

	public static void register(IEventBus modBus) {
		TABS.register(modBus);
		modBus.addListener(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent.class, LeafiaCreativeTabs::hideBasePwr);
	}

	/**
	 * The base mod's PWR blocks, by registry path. Upstream's {@code AddonBlocks.modifyBlockParams} names NTM-CE's
	 * {@code pwr_fuelrod}, which is {@code hbm-src}'s (and this port's) block {@code pwr_fuel}; {@code pwr_block}, the
	 * formed reactor's block, has no item to hide.
	 */
	public static final java.util.Set<String> BASE_PWR_BLOCKS = java.util.Set.of("pwr_casing", "pwr_channel", "pwr_control",
			"pwr_fuel", "pwr_controller", "pwr_heatex", "pwr_heatsink", "pwr_neutron_source", "pwr_port", "pwr_reflector");

	/**
	 * Whether {@code item} is one of the base mod's PWR pieces that the addon's PWR hides from the creative tabs: the blocks of
	 * {@link #BASE_PWR_BLOCKS} and the fuel items, which upstream's {@code AddonItems.modifyItemParams} names as NTM-CE's
	 * {@code pwr_fuel}, {@code pwr_fuel_hot} and {@code pwr_fuel_depleted} (one item each with a fuel per metadata) and this
	 * port registers one per fuel, {@code pwr_fuel_<fuel>}, {@code pwr_fuel_hot_<fuel>} and {@code pwr_fuel_depleted_<fuel>}.
	 *
	 * <p>Upstream hides them whenever its own PWR is on ({@code !disableAddonPWR}). Here that is the overhaul,
	 * {@code overhauls.pwr} (off), and only while the addon's PWR exists ({@code pwrReactor()}), so the switch can never hide
	 * both reactors at once (D-3040).
	 */
	public static boolean hidesBasePwr(net.minecraft.world.item.Item item) {
		if(!com.leafia.config.LeafiaFeatures.pwr || !com.leafia.config.LeafiaFeatures.pwrReactor()) return false;
		net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
		if(!"hbm".equals(id.getNamespace())) return false;
		return BASE_PWR_BLOCKS.contains(id.getPath()) || id.getPath().startsWith("pwr_fuel_");
	}

	/** Takes {@link #hidesBasePwr} items out of every tab the base mod builds (D-3040). */
	private static void hideBasePwr(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
		if(!com.leafia.config.LeafiaFeatures.pwr) return;
		for(ItemStack stack : List.copyOf(event.getParentEntries()))
			if(hidesBasePwr(stack.getItem())) event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
		for(ItemStack stack : List.copyOf(event.getSearchEntries()))
			if(hidesBasePwr(stack.getItem())) event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
	}
}
