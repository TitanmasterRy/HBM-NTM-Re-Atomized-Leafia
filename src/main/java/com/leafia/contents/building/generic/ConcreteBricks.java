package com.leafia.contents.building.generic;

import java.util.Locale;

import com.leafia.dev.blocks.BlastResistanceTooltip;
import com.leafia.dev.blocks.blockbase.AddonRadResistantBlockBaked;


/**
 * The dark concrete bricks, one finish of four: plain, mossy, cracked or broken.
 *
 * <p>Upstream: {@code com/leafia/contents/building/generic/ConcreteBricks.java}, which is one block,
 * {@code brick_concrete_dark}, with the finish in its metadata ({@code VARIANT}, a four-value
 * {@code PropertyEnum}) and one item per finish. It is {@code new ConcreteBricks(Material.ROCK,
 * "brick_concrete_dark", "brick_dark_concrete").setHardness(15.0F).setResistance(160.0F)}, so every
 * finish takes the same hardness and blast resistance, 15 and (D-012, {@code × 0.6}) 96.
 *
 * <h2>Four blocks, not one block with a property</h2>
 *
 * D-034 turns packed metadata into an {@code EnumProperty} and one item per finish. This family does
 * not follow it, for the reason the base mod's own brick concretes do not: {@code brick_concrete},
 * {@code _mossy}, {@code _cracked} and {@code _broken} are four registered blocks there
 * ({@code ModBlocks}), and the addon's 231 mixed concretes name them as four blocks. The addon's
 * dark bricks sit beside them in the creative menu and get the same shape. The registry names are
 * {@code brick_concrete_dark} for the plain finish, which is upstream's id and so what a world
 * migration would key on, and {@code brick_concrete_dark_mossy}, {@code _cracked} and {@code _broken}
 * for the other three; upstream's translation keys, {@code tile.brick_concrete_dark.mossy.name} and
 * so on, are the same four names. Nothing carries a blockstate property, so a block entity, a
 * loot table or a recipe never has to ask which finish it is holding.
 *
 * <h2>What is, and is not, carried over</h2>
 *
 * <ul>
 * <li><b>The tooltip, the flag and the section rebuild</b> are
 * {@link AddonRadResistantBlockBaked}'s, which this extends as upstream does (it landed with the PWR,
 * D-3006): {@code [Radiation Shielding]} in dark green for the finishes that shield, then
 * {@code AddonBlockBase}'s blast line ({@link BlastResistanceTooltip}; 96 is over its 50).
 * {@code isRadResistant(meta)} is {@code meta <= MOSSY}: the plain and mossy finishes shield, the
 * cracked and broken ones do not, and {@link #isRadResistant()} says the same to the base mod's
 * {@code IRadResistantBlock} seam (1 or 0). Placing or breaking a shielding finish calls
 * {@code ChunkRadiationManager.markSectionForRebuild}, which has a real effect only once a handler that
 * caches section geometry is ported (D-1082, D-1090, D-2195). The PWR reads the interface, not the
 * flag, so <b>every</b> finish stops its neutrons - upstream's {@code isRadResistant(world, pos)} is the
 * interface default, true, and this class never overrides it.</li>
 * <li><b>The pickaxe.</b> The base chain ends at {@code AddonBlockBase}, whose
 * {@code setHarvestLevel("pickaxe", 0)} is honoured with the flag and a tag row (D-1072).</li>
 * </ul>
 */
public class ConcreteBricks extends AddonRadResistantBlockBaked {

	/**
	 * The four finishes, in upstream's order; the order is what {@code isRadResistant} compares
	 * against, so it is not cosmetic.
	 */
	public enum Type {
		NORMAL, MOSSY, CRACKED, BROKEN;

		/** Upstream's {@code getName()}, {@code name().toLowerCase()}. */
		public String getName() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Upstream's {@code isRadResistant(meta)}: {@code meta <= MOSSY.ordinal()}. */
		public boolean radResistant() {
			return ordinal() <= MOSSY.ordinal();
		}

		/** The registry name: upstream's {@code brick_concrete_dark}, then the finish for all but the first. */
		public String registryName() {
			return this == NORMAL ? ID : ID + "_" + getName();
		}
	}

	/** Upstream's registry name, which is the plain finish's. */
	public static final String ID = "brick_concrete_dark";

	/** {@code setHardness(15.0F)}. */
	public static final float HARDNESS = 15.0F;
	/** {@code setResistance(160.0F)} is 96 here, D-012's {@code × 0.6}. */
	public static final float RESISTANCE = 96.0F;

	private final Type type;

	public ConcreteBricks(Properties properties, Type type) {
		super(properties);
		this.type = type;
	}

	public Type type() {
		return this.type;
	}

	/** Upstream's {@code isRadResistant(meta)} for this finish: the plain and mossy ones. */
	@Override
	public boolean isRadResistant() {
		return this.type.radResistant();
	}
}
