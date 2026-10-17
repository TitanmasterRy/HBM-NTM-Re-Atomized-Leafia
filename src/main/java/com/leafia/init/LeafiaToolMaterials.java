package com.leafia.init;

import com.hbm.items.tool.NtmTiers;
import com.leafia.contents.LeafiaItems;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * The addon's three tool materials: mysticite, fissite and plutonium-238.
 *
 * <p>Upstream: {@code com.leafia.init.AddonToolMaterials}, three
 * {@code EnumHelper.addToolMaterial} calls and a second pass that gives two of them a repair item.
 * <b>Only the shields read them</b> - the whole clone has no fissite pickaxe - and a shield reads
 * exactly one number, {@code getMaxUses()}. The other four (harvest level, speed, damage,
 * enchantability) are kept anyway, because they are upstream's data and a future tool built on one
 * of these should not have to look them up again; {@code LeafiaShieldTests} pins all fifteen.
 *
 * <p>1.12.2's {@code ToolMaterial} became {@link Tier}. Harvest level, which a {@code Tier} no
 * longer carries, is what 1.21.1 spells as the tag of blocks the tier is <em>not</em> good enough
 * for; {@link NtmTiers#incorrectFor} does that for 0 to 3, and level 5 (mysticite's) is netherite,
 * whose tag is empty.
 *
 * <h2>Two things that are not upstream's</h2>
 *
 * <ul>
 *   <li><b>Steel and titanium are not here.</b> Upstream's {@code steel_shield} and
 *       {@code titanium_shield} read {@code MaterialRegistry.enumToolMaterialSteel} and
 *       {@code ...Titanium}, which are NTM-CE's: <b>500 and 750</b> uses. The base mod this port
 *       follows has {@code HBM_STEEL} at 750 and {@code HBM_TITANIUM} at 1000, and D-1000 says the
 *       base mod's value wins, so the shields read {@link NtmTiers#STEEL} and
 *       {@link NtmTiers#TITANIUM}. Both numbers are in D-1083.</li>
 *   <li><b>Mysticite has zero uses, which is "never wears out", not "breaks at once".</b>
 *       1.12.2's {@code Item.setMaxDamage(0)} made the item undamageable; 1.21.1 decides
 *       damageability by whether the {@code MAX_DAMAGE} component is present. The shield simply
 *       leaves it off.</li>
 * </ul>
 *
 * <p>Upstream's second pass ({@code initFixMaterials}) gives fissite and plutonium-238 a repair
 * ingot and, in a comment, mysticite none ("you can't though"). The same is true here. The repair
 * ingredient is a supplier, because the items it names do not exist yet when this class loads.
 */
public final class LeafiaToolMaterials {

	private LeafiaToolMaterials() {}

	/** {@code leafia:MYSTICITE}: 5, 0, 15, 5, 20. */
	public static final Tier MYSTICITE = tier(5, 0, 15F, 5F, 20, null);

	/** {@code leafia:FISSITE}: 3, 2500, 8, 3, 10, repaired with a fissite ingot. */
	public static final Tier FISSITE = tier(3, 2500, 8F, 3F, 10,
			() -> Ingredient.of(LeafiaItems.ingot_fissite.get()));

	/** {@code leafia:PU238}: 3, 300, 6, 2, 5, repaired with a plutonium-238 ingot. */
	public static final Tier PU238 = tier(3, 300, 6F, 2F, 5,
			() -> Ingredient.of(com.hbm.items.ModItems.ingot_pu238.get()));

	private static Tier tier(int harvestLevel, int uses, float speed, float damage, int enchantability,
			java.util.function.Supplier<Ingredient> repair) {
		TagKey<Block> incorrect = harvestLevel >= 4 ? BlockTags.INCORRECT_FOR_NETHERITE_TOOL
				: NtmTiers.incorrectFor(harvestLevel);
		return new SimpleTier(incorrect, uses, speed, damage, enchantability,
				repair == null ? () -> Ingredient.EMPTY : repair);
	}
}
