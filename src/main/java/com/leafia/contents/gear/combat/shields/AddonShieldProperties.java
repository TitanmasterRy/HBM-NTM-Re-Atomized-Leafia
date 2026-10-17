package com.leafia.contents.gear.combat.shields;

import com.leafia.contents.LeafiaItems;
import com.leafia.contents.gear.combat.shields.AddonShieldItem.Kind;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The two item properties the shields' item models switch on (D-3031).
 *
 * <ul>
 * <li><b>{@code minecraft:blocking}</b> on all six. Every shield model carries a {@code blocking} override with vanilla's
 *     raised pose, but 1.21.1 registers that property for {@code Items.SHIELD} alone, so until this the addon's shields
 *     never raised in the hand. 1.12.2 registered it in {@code ItemShield}'s own constructor, so upstream's subclasses had
 *     it; the function is vanilla's, word for word.</li>
 * <li><b>{@code leafia:decorated}</b> on fissite and mysticite: 1 while the stack wears a banner ({@code BASE_COLOR}).
 *     Their plain model is a baked OBJ, and the override for this sends a decorated one to {@code AddonShieldRender},
 *     which lays the banner over the mesh.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class AddonShieldProperties {

	public static final ResourceLocation BLOCKING = ResourceLocation.withDefaultNamespace("blocking");
	public static final ResourceLocation DECORATED = LeafiaRef.rl("decorated");

	private AddonShieldProperties() {}

	public static void register() {
		for(Kind kind : Kind.values()) {
			Item item = LeafiaItems.shields.get(kind).get();
			ItemProperties.register(item, BLOCKING,
					(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
			if(kind.charges())
				ItemProperties.register(item, DECORATED, (stack, level, entity, seed) -> stack.has(DataComponents.BASE_COLOR) ? 1.0F : 0.0F);
		}
	}
}
