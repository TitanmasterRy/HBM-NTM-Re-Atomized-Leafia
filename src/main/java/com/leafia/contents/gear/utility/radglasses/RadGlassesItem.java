package com.leafia.contents.gear.utility.radglasses;

import java.util.List;

import com.leafia.init.LeafiaArmorMaterials;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The radiation locator goggles (D-3026): iron's helmet with a model of its own.
 *
 * <p>Upstream: {@code new RadGlassesItem(ArmorMaterial.IRON, -1, EntityEquipmentSlot.HEAD, "radglasses").setMaxStackSize(1)}, an
 * {@code ItemArmor} whose {@code getArmorModel} returns {@code RadGlassesModel} (the goggles' OBJ on the head, the {@code goggle_rad}
 * skin) and whose tooltip says "Locates sources of chunk radiations". The model is {@link RadGlassesModel}, a render layer; the
 * material is iron's numbers with no layers ({@link LeafiaArmorMaterials}).
 *
 * <p><b>The locating effect</b> is {@link com.leafia.overwrite_contents.LeafiaRadObserver} (D-3041): upstream's
 * {@code MixinRadiationSystemNT} injects into the CE-only {@code RadiationSystemNT.incrementRad} and, for each player wearing the
 * goggles within 256 blocks of the pocket, shows a box with the dose ({@code +x.xRAD}) for ten seconds. The base mod's seam is an
 * increment observer on {@code ChunkRadiationManager} (D-2207); the box is a {@code TopRender.Highlight}, sent by
 * {@code RadHighlightPacket}.
 */
public class RadGlassesItem extends ArmorItem {

	public RadGlassesItem(Properties properties) {
		super(LeafiaArmorMaterials.RADGLASSES, Type.HELMET, properties);
	}

	/** The properties upstream gives it: one in a stack, and iron's durability. */
	public static Item.Properties properties() {
		return new Item.Properties().stacksTo(1).durability(Type.HELMET.getDurability(LeafiaArmorMaterials.IRON_DURABILITY));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.literal("Locates sources of chunk radiations"));
	}
}
