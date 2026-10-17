package com.leafia.database;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hbm.hazard.HazardRegistry;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.material.MaterialShapes;

/**
 * Ported from {@code com.leafia.database.AddonOreDictHazards}.
 *
 * <p>The tables the addon's hazard registration reads: for every ore-dictionary frame, every name the
 * frame's materials go by ({@code ingotUranium}, {@code blockUranium}, {@code plateTripleUranium}...)
 * and the multiplier its shape gives a hazard there (a block of uranium is ten times an ingot's dose).
 *
 * <h2>How upstream fills it, and how this does</h2>
 *
 * Upstream's {@code MixinDictFrame} fires on every {@code DictFrame.makeObject} and records
 * {@code prefix + mat -> hazMult} for the shape being registered, with {@code hazMult} the field the
 * shape's setter (<code>ingot()</code>, <code>dust()</code>, <code>block()</code>...) set a moment before.
 * That field is package-private in the base mod and the addon cannot mix in (D-1001), but the setters'
 * constants are the base mod's own public {@link HazardRegistry} floats, and the setters are fixed
 * code, so {@link #SHAPE_MULTIPLIERS} is the same table read off {@code DictFrame}'s thirteen setters:
 *
 * <pre>
 * nugget -> nugget (prefixes nugget, tiny)   tiny -> nugget         ingot -> ingot
 * dustSmall -> powder_tiny (dustTiny)        dust -> powder          gem -> gem
 * crystal -> gem (<b>not</b> the crystal constant)                    plate -> plate
 * plateCast -> plateCast (plateTriple)       billet -> billet        block -> block
 * ore -> ore (ore, oreNether)                oreNether -> ore
 * </pre>
 *
 * Upstream's top-level loop in {@code AddonHazards.register} then gives every material every prefix
 * seen anywhere ({@code putIfAbsent}) and adds {@code plateSextuple} at twice {@code plateTriple}'s
 * multiplier; here that is just what {@link #table} does for every frame, so the table is complete
 * from the start. <b>What is not carried:</b> a shape registered by a direct {@code makeObject} call
 * rather than a setter (bolts, wires, shells, pipes, barrels, receivers, fragments) took whatever
 * {@code hazMult} the previous call left; there is nothing to read that from, and the addon's hazard
 * lines only ever select ingots, dusts, blocks, plates, billets and crystals (or everything, for the
 * osmiridium and xanaxium digamma, which then misses those shapes). Frames are built lazily, the
 * first time something asks for one - upstream's table has no entry for a frame that never
 * registered an item, and asking for it printed a warning and did nothing; here it is a table of
 * names that may have no items behind them, which is harmless: a hazard on a tag nothing is in.
 */
public class AddonOreDictHazards {

	/** frame -> (ore name -> the shape's hazard multiplier). Filled by {@link #table}. */
	public static Map<Object,Map<String,Float>> dictMap = new HashMap<>();
	/** shape prefix -> multiplier, first writer wins, as upstream's. Filled by {@link #init}. */
	public static Map<String,Float> prefixToHazMultMap = new LinkedHashMap<>();
	/** material name -> its frame. */
	public static Map<String,Object> matList = new HashMap<>();

	/** The thirteen setters of {@code DictFrame}, in the order they are declared, with the constant each assigns. */
	public static final Map<MaterialShapes,Float> SHAPE_MULTIPLIERS = new LinkedHashMap<>();
	static {
		SHAPE_MULTIPLIERS.put(MaterialShapes.NUGGET, HazardRegistry.nugget);
		SHAPE_MULTIPLIERS.put(MaterialShapes.TINY, HazardRegistry.nugget);
		SHAPE_MULTIPLIERS.put(MaterialShapes.INGOT, HazardRegistry.ingot);
		SHAPE_MULTIPLIERS.put(MaterialShapes.DUSTTINY, HazardRegistry.powder_tiny);
		SHAPE_MULTIPLIERS.put(MaterialShapes.DUST, HazardRegistry.powder);
		SHAPE_MULTIPLIERS.put(MaterialShapes.GEM, HazardRegistry.gem);
		SHAPE_MULTIPLIERS.put(MaterialShapes.CRYSTAL, HazardRegistry.gem);
		SHAPE_MULTIPLIERS.put(MaterialShapes.PLATE, HazardRegistry.plate);
		SHAPE_MULTIPLIERS.put(MaterialShapes.CASTPLATE, HazardRegistry.plateCast);
		SHAPE_MULTIPLIERS.put(MaterialShapes.BILLET, HazardRegistry.billet);
		SHAPE_MULTIPLIERS.put(MaterialShapes.BLOCK, HazardRegistry.block);
		SHAPE_MULTIPLIERS.put(MaterialShapes.ORE, HazardRegistry.ore);
		SHAPE_MULTIPLIERS.put(MaterialShapes.ORENETHER, HazardRegistry.ore);
		init();
	}

	/** Fills {@link #prefixToHazMultMap} from {@link #SHAPE_MULTIPLIERS}. Idempotent. */
	public static void init() {
		for(Map.Entry<MaterialShapes,Float> shape : SHAPE_MULTIPLIERS.entrySet())
			for(String prefix : shape.getKey().prefixes)
				prefixToHazMultMap.putIfAbsent(prefix, shape.getValue());
	}

	/** Every name {@code frame} goes by, with its multiplier: the frame's entry in {@link #dictMap}, built on first use. */
	public static Map<String,Float> table(DictFrame frame) {
		return dictMap.computeIfAbsent(frame, f -> build((DictFrame) f));
	}

	private static Map<String,Float> build(DictFrame frame) {
		Map<String,Float> map = new LinkedHashMap<>();
		for(String mat : frame.mats) {
			for(Map.Entry<String,Float> prefix : prefixToHazMultMap.entrySet()) {
				map.putIfAbsent(prefix.getKey() + mat, prefix.getValue());
				// upstream's "seriously" line: the welded plate is a triple plate twice over
				if(prefix.getKey().equals("plateTriple"))
					map.putIfAbsent("plateSextuple" + mat, prefix.getValue() * 2);
			}
			matList.put(mat, frame);
		}
		return map;
	}
}
