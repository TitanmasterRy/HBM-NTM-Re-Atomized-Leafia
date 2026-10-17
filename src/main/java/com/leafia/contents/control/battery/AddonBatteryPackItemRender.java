package com.leafia.contents.control.battery;

import com.hbm.render.item.ItemRenderBase;
import com.hbm.render.item.ItemRenderBatteryPack;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the addon's six battery packs as the cells they are.
 *
 * <p>Upstream: {@code com.leafia.contents.control.battery.AddonBatteryPackItemRender}, which is
 * {@code @Deprecated // suck my butt} and has its whole body commented out: upstream's packs were
 * appended to the base mod's battery pack enum by a mixin, after which the base mod's own
 * {@code ItemRenderBatteryPack} drew them from the enum's {@code texture} field, as the
 * skin the mixin constructed. This class is the live part of that - binding the pack's skin and
 * drawing the battery mesh ({@code "Battery"}, never the capacitor: none of the six is one) - through
 * the base mod's public {@link ItemRenderBatteryPack#renderPack}, which exists for exactly this.
 *
 * <p>The skins are {@code assets/leafia/textures/models/machines/<tex>.png}, the six 82x68 sheets the
 * jar's {@code hbm} assets carry (D-1067 shipped none because the socket renderer was not ported;
 * it is now). The item models are re-parented on {@code builtin/entity} with the base packs' display
 * transforms - a generator script writes both.
 */
@OnlyIn(Dist.CLIENT)
public class AddonBatteryPackItemRender extends ItemRenderBase {

	/** A pack's skin, in the addon's own namespace. */
	public static ResourceLocation skin(AddonEnumBatteryPack variant) {
		return ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/models/machines/" + variant.tex + ".png");
	}

	@Override
	public void renderCommonWithStack(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		if(!(stack.getItem() instanceof AddonBatteryPackItem pack)) return;
		ItemRenderBatteryPack.renderPack(skin(pack.getVariant()), false, pose, buffers, light, overlay);
	}
}
