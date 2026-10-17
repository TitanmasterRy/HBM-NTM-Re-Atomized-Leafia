package com.leafia.contents.machines.processing.solblaster.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import com.hbm.blocks.ModBlocks;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeRadiation;
import com.hbm.inventory.OreDictManager;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.lib.NtmTags;
import com.leafia.contents.LeafiaItems;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRodItem;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.init.LeafiaMats;
import com.leafia.init.LeafiaOreDict;
import com.leafia.init.hazards.types.LCERad;
import com.leafia.unsorted.LegacyMaterials;
import com.llib.exceptions.LeafiaDevFlaw;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DeadBushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.SoundType;

/**
 * What the Neutron Blaster turns each of its fifteen input slots into.
 *
 * <p>Upstream: {@code .../solblaster/recipes/SolBlasterRecipes.java}. Three layers, tried in this
 * order for every stack, and the first that has an answer wins:
 *
 * <ol>
 * <li>{@link #RECIPES} - an item (or an ore-dictionary name, here an item tag) and what it becomes.
 *     <b>Only these are an "input" to the comparator and to a hopper</b>: {@link #isValidInput}
 *     reads this table and no other, which is why upstream registers the balefire eggs and the
 *     fissite plate as <em>fallbacks</em> and not as recipes - a hopper never feeds them in, and
 *     the comparator does not count them.</li>
 * <li>{@link #FALLBACK} - the same shape, consulted only when no recipe matched. A fallback may map
 *     to nothing at all: the balefire egg and its shard simply cease to exist.</li>
 * <li>{@link #EVALUATORS} - four rules that look at the stack rather than the item: a fissite shield
 *     becomes a mysticite one, an RTG pellet or an addon fuel rod becomes what it decays into
 *     (D-3050), food and organic blocks are deleted, and anything radioactive is
 *     slaked into {@code sellafield_slaked}. Evaluated in this order, upstream's
 *     {@code fallbackEvaluators.add(0, ...)} being a prepend each time.</li>
 * </ol>
 *
 * <h2>What the 1.21.1 port changes</h2>
 *
 * <ul>
 * <li><b>No metadata, so no meta-keyed inner map.</b> Upstream keys {@code (item, meta)} and an
 *     ore-dictionary entry is always meta 0. Every item here is its own registration (D-213), so a
 *     key is an {@link Input}: an item, or an item tag from {@link NtmTags#ore} - which is the
 *     ore-dictionary name, written exactly as upstream writes it.</li>
 * <li><b>The table is built on first use</b>, not at {@code postInit}. Every output is a registered
 *     item and every key a tag, so there is nothing to wait for but registration, and a lazy build
 *     needs no hook in {@code LeafiaMod}. {@link #register()} rebuilds it and is what a test calls.</li>
 * <li><b>A feature that is switched off removes its rows</b> (D-1002): a recipe whose output does
 *     not exist is not registered at all, instead of registering an input with no result.</li>
 * <li><b>Materials are approximated</b> - see {@link #isOrganic}.</li>
 * </ul>
 *
 * <h2>What is not here, and why</h2>
 *
 * <ul>
 * <li>The {@code ntmContagion} tag, which upstream strips from every stack before anything else:
 *     the contagion system it belongs to is not ported.</li>
 * </ul>
 */
public final class SolBlasterRecipes {

	private SolBlasterRecipes() {}

	/** An item, or an item tag - upstream's {@code ItemOrOreDict}. */
	public record Input(@Nullable Item item, @Nullable TagKey<Item> tag) {

		public static Input of(Item item) {
			return new Input(item, null);
		}

		public static Input of(TagKey<Item> tag) {
			return new Input(null, tag);
		}

		public boolean isTag() {
			return tag != null;
		}

		@Override
		public String toString() {
			return tag != null ? "#" + tag.location() : String.valueOf(item);
		}
	}

	private static final Map<Input, Item> RECIPE_TABLE = new LinkedHashMap<>();
	private static final Map<Input, Item> FALLBACK_TABLE = new LinkedHashMap<>();

	/** Recipes, in registration order. A value is never null. */
	public static final Map<Input, Item> RECIPES = Collections.unmodifiableMap(RECIPE_TABLE);

	/** Fallbacks, in registration order. A null value means "ceases to exist". */
	public static final Map<Input, Item> FALLBACK = Collections.unmodifiableMap(FALLBACK_TABLE);

	/**
	 * The stack rules, in the order they are tried. Upstream builds the list by prepending, so the
	 * last one it adds is the first one asked: shield, nuclear fuels, organics, radioactivity.
	 */
	public static final List<UnaryOperator<ItemStack>> EVALUATORS = new ArrayList<>();

	private static boolean built = false;

	/** Builds the tables if nothing has yet. Every public reader calls this. */
	private static synchronized void ensure() {
		if(!built) register();
	}

	/**
	 * Upstream's {@code register()}, line for line and in upstream's order. Rebuilds from nothing,
	 * so it can be run again after a switch changed.
	 */
	public static synchronized void register() {

		RECIPE_TABLE.clear();
		FALLBACK_TABLE.clear();
		EVALUATORS.clear();

		addRecipe(NtmTags.ore(OreDictManager.SA326.ingot()), ModItems.nugget_euphemium.get());
		addRecipe(NtmTags.ore(OreDictManager.SA327.ingot()), ModItems.nugget_euphemium.get());
		addRecipe(NtmTags.ore(OreDictManager.SA326.block()), ModItems.ingot_euphemium.get());
		addRecipe(NtmTags.ore(OreDictManager.SA327.block()), ModItems.ingot_euphemium.get());

		// AddonOreDict.FS and FSALLOY, the fissium and fissite frames: ingotFissium and so on.
		addRecipe(NtmTags.ore("ingotFissium"), resource("ingot_chydalium"));
		addRecipe(NtmTags.ore("billetFissium"), resource("billet_chydalium"));
		addRecipe(NtmTags.ore("ingotFissite"), resource("ingot_mysticite"));
		addFallback(NtmTags.ore("plateFissite"), resource("plate_mysticite"));
		// Upstream: part_receiver_light at meta MAT_MYSTICITE.id. One item per material here (D-3026).
		addFallback(NtmTags.ore(LeafiaOreDict.FSALLOY.lightReceiver()), LeafiaMats.item("part_receiver_light_mysticite"));
		// Upstream's AddonItems.am_rifle_cell_fissite and _mysticite, which this port names
		// cells_fissite and cells_mysticite (D-1085).
		addFallback(resource("cells_fissite"), resource("cells_mysticite"));

		addRecipe(resource("blanket_fissite"), resource("blanket_mysticite"));
		addRecipe(resource("blanket_fissite_bundle"), resource("blanket_mysticite_bundle"));

		addFallbackGone(ModItems.egg_balefire_shard.get());
		addFallbackGone(ModItems.egg_balefire.get());

		// Pushed in the order they are asked; upstream builds the same list with add(0, ...).
		EVALUATORS.add(SolBlasterRecipes::fissiteShield);
		EVALUATORS.add(SolBlasterRecipes::nuclearFuels);
		EVALUATORS.add(SolBlasterRecipes::organics);
		EVALUATORS.add(SolBlasterRecipes::radioactive);

		built = true;
	}

	/** A resource item, or null when the switch that registers it is off. */
	@Nullable
	private static Item resource(String name) {
		var item = LeafiaResourceItems.get(name);
		return item == null ? null : item.get();
	}

	// ---- registration -------------------------------------------------------------------------

	/**
	 * Upstream's {@code addRecipe}, which throws on a second recipe for the same input. A row
	 * whose output is absent (its feature is off) is dropped, not half-registered.
	 */
	private static void addRecipe(TagKey<Item> in, @Nullable Item out) {
		if(out == null) return;
		put(RECIPE_TABLE, Input.of(in), out, true);
	}

	private static void addRecipe(@Nullable Item in, @Nullable Item out) {
		if(in == null || out == null) return;
		put(RECIPE_TABLE, Input.of(in), out, true);
	}

	private static void addFallback(TagKey<Item> in, @Nullable Item out) {
		if(out == null) return;
		put(FALLBACK_TABLE, Input.of(in), out, false);
	}

	private static void addFallback(@Nullable Item in, @Nullable Item out) {
		if(in == null || out == null) return;
		put(FALLBACK_TABLE, Input.of(in), out, false);
	}

	/** Upstream's one-argument {@code addFallback}: the input maps to nothing. */
	private static void addFallbackGone(Item in) {
		put(FALLBACK_TABLE, Input.of(in), null, false);
	}

	private static void put(Map<Input, Item> table, Input in, @Nullable Item out, boolean conflictIsAnError) {
		if(conflictIsAnError && table.containsKey(in))
			throw new LeafiaDevFlaw("SOL Blaster recipe conflict! (" + in + ")");
		table.put(in, out);
	}

	// ---- lookup -------------------------------------------------------------------------------

	/**
	 * Upstream's {@code getValidIOD}: the stack's own item first, then the first of its tags that
	 * the table names. Null when the table has no row for it.
	 *
	 * <p>A tag's order is not defined, so a stack in two named tags is answered by whichever the
	 * game lists first. No pair of rows here can be in conflict, so it never decides anything.
	 */
	@Nullable
	private static Input find(Map<Input, Item> table, ItemStack stack) {
		if(stack.isEmpty()) return null;
		Input byItem = Input.of(stack.getItem());
		if(table.containsKey(byItem)) return byItem;
		for(TagKey<Item> tag : (Iterable<TagKey<Item>>) stack.getTags()::iterator) {
			Input byTag = Input.of(tag);
			if(table.containsKey(byTag)) return byTag;
		}
		return null;
	}

	/** {@link #RECIPES}, built if nothing has yet: what the JEI page lists (D-3032). */
	public static Map<Input, Item> recipes() {
		ensure();
		return RECIPES;
	}

	/** Whether a hopper may feed this in and the comparator counts it: a <em>recipe</em> matches. */
	public static boolean isValidInput(ItemStack stack) {
		ensure();
		return find(RECIPE_TABLE, stack) != null;
	}

	/**
	 * Upstream's {@code getOutput}: null for "no row", {@link ItemStack#EMPTY} for a row that
	 * maps to nothing, otherwise one of the output.
	 */
	@Nullable
	public static ItemStack getOutput(Map<Input, Item> table, ItemStack stack) {
		Input in = find(table, stack);
		if(in == null) return null;
		Item out = table.get(in);
		return out == null ? ItemStack.EMPTY : new ItemStack(out);
	}

	/**
	 * What a stack in an input slot becomes when the blaster fires.
	 *
	 * <p>Upstream's {@code processItem}. The result is always <em>one</em> item: a recipe's output is
	 * {@code new ItemStack(item, 1, meta)}. That does not lose anything in play, because the
	 * blaster's slots hold one item each - {@code getNewInventory(20, 1)} - and it is why the
	 * rest of this does not need a count.
	 */
	public static ItemStack processItem(ItemStack stack) {

		ensure();

		if(stack.isEmpty()) return stack;

		// Upstream strips the "ntmContagion" tag here. Not ported: see the class doc.

		ItemStack out = getOutput(RECIPE_TABLE, stack);
		if(out != null) return out;

		out = getOutput(FALLBACK_TABLE, stack);
		if(out != null) return out;

		for(UnaryOperator<ItemStack> rule : EVALUATORS) {
			ItemStack ret = rule.apply(stack);
			if(ret != stack && ret != null) return ret;
		}

		return stack;
	}

	// ---- the rules that look at the stack ------------------------------------------------------

	/** A fissite shield is transmuted to a mysticite one, new. */
	private static ItemStack fissiteShield(ItemStack stack) {
		var fissite = LeafiaItems.shields.get(Kind.FISSITE);
		var mysticite = LeafiaItems.shields.get(Kind.MYSTICITE);
		if(fissite != null && mysticite != null && stack.is(fissite.get()))
			return new ItemStack(mysticite.get());
		return stack;
	}

	/**
	 * Nuclear fuels: an RTG pellet becomes its spent pellet, and an addon fuel rod with a decay
	 * product becomes that rod, carrying its data over with {@code depletion} reset - at once,
	 * however much of the rod was burnt. A pellet that does not decay, and a rod with no product,
	 * fall through to the later rules (upstream's {@code getDecayItem()} is null there; the base
	 * mod's answers empty, which is read the same way here).
	 */
	private static ItemStack nuclearFuels(ItemStack stack) {
		if(stack.getItem() instanceof ItemRTGPellet rtg) {
			ItemStack decayed = rtg.getDecayItem();
			return decayed.isEmpty() ? stack : decayed;
		}
		if(stack.getItem() instanceof LeafiaRodItem rod) {
			LeafiaRodItem newFuel = rod.newFuel();
			if(newFuel != null) {
				CompoundTag data = LeafiaRodItem.data(stack);
				if(data == null) data = new CompoundTag();
				data.putDouble("depletion", 0);
				ItemStack out = new ItemStack(newFuel);
				LeafiaRodItem.putData(out, data);
				return out;
			}
		}
		return stack;
	}

	/** Food and organic blocks vanish. */
	private static ItemStack organics(ItemStack stack) {
		if(stack.has(DataComponents.FOOD)) return ItemStack.EMPTY;
		// 1.12.2's ItemBlock excludes ItemBlockSpecial and ItemSeeds, which 1.21.1 builds as
		// ItemNameBlockItem: seeds, nether wart and the like survive, as upstream's do.
		if(stack.getItem() instanceof BlockItem item && !(item instanceof ItemNameBlockItem)
				&& isOrganic(item.getBlock()))
			return ItemStack.EMPTY;
		return stack;
	}

	/** Anything with a radiation hazard becomes a block of slaked sellafield. */
	private static ItemStack radioactive(ItemStack stack) {
		for(HazardEntry hazard : HazardSystem.getHazardsFromStack(stack)) {
			if(hazard.getType() instanceof HazardTypeRadiation || hazard.getType() instanceof LCERad)
				return new ItemStack(ModBlocks.sellafield_slaked.get());
		}
		return stack;
	}

	/**
	 * Upstream's {@code mat == WOOD || PLANTS || CACTUS || DRAGON_EGG || WEB}.
	 *
	 * <p>1.21.1 has no {@code Material}, so this is an approximation, and the one place the
	 * port can classify a block differently from upstream:
	 *
	 * <ul>
	 * <li><b>Wood</b> is a base-mod block the 1.7.10 sources made wood ({@link LegacyMaterials}),
	 *     or anything with a wood step sound, bar the four kinds that sound like wood and were
	 *     {@code Material.CIRCUITS} or cloth upstream: torches, buttons, levers and beds.</li>
	 * <li><b>Plants</b> are bushes - flowers, saplings, crops, stems, mushrooms, lily pads, nether
	 *     wart - and sugar cane, <em>except</em> the three kinds 1.12.2 made {@code Material.VINE}:
	 *     short grass and ferns, the double plants, and dead bushes.</li>
	 * <li>The cactus, the dragon egg and the cobweb are their own blocks.</li>
	 * </ul>
	 */
	public static boolean isOrganic(Block block) {

		if(block == Blocks.DRAGON_EGG || block == Blocks.COBWEB || block instanceof CactusBlock) return true;

		if(block instanceof SugarCaneBlock) return true;
		if(block instanceof BushBlock) {
			return !(block instanceof TallGrassBlock || block instanceof DoublePlantBlock || block instanceof DeadBushBlock);
		}

		if(LegacyMaterials.isWood(block)) return true;

		SoundType sound = block.defaultBlockState().getSoundType();
		boolean woodSound = sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD
				|| sound == SoundType.CHERRY_WOOD || sound == SoundType.BAMBOO_WOOD;

		return woodSound && !(block instanceof TorchBlock || block instanceof ButtonBlock
				|| block instanceof LeverBlock || block instanceof BedBlock);
	}
}
