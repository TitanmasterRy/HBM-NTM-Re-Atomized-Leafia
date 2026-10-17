package com.leafia.overwrite_contents.difurnace;

import com.hbm.inventory.container.ContainerMachineDiFurnaceRTG;
import com.hbm.inventory.gui.GUIMachineDiFurnaceRTG;
import com.leafia.lib.LeafiaRef;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The RTG blast furnace's screen on the addon's sheet, which has the fuel-rod slot drawn in.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/mixin/mod/hbm/MixinGUIDiFurnaceRTG.java}, a redirect of
 * the {@code ResourceLocation} in the base screen's static initialiser to
 * {@code leafia:textures/gui/processing/gui_rtg_difurnace.png}. Here it is the base screen with
 * {@link #texture()} overridden, which is the seam M7 left for it, registered for the overhaul's own menu.
 */
@OnlyIn(Dist.CLIENT)
public class LeafiaDiFurnaceRTGScreen extends GUIMachineDiFurnaceRTG {

	public static final ResourceLocation TEXTURE = LeafiaRef.rl("textures/gui/processing/gui_rtg_difurnace.png");

	public LeafiaDiFurnaceRTGScreen(ContainerMachineDiFurnaceRTG menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	@Override
	protected ResourceLocation texture() {
		return TEXTURE;
	}
}
