package com.leafia.client;

import com.hbm.inventory.fluid.FluidType;
import com.leafia.config.LeafiaFeatures;
import com.leafia.contents.gear.utility.fuzzy.FuzzyScreenClicks;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The fuzzy identifier's click on a tank of one of the base mod's own screens (D-3041): a press with the identifier on the cursor,
 * over a tank the screen drew, sets the identifier to that tank's fluid - what {@code MixinFluidTankNTM} did from inside
 * {@code renderTankInfo}, found here through the base mod's recorded tank rectangles ({@link FuzzyScreenClicks}).
 *
 * <p>The addon's own screens ({@code LCEGuiInfoContainer}) already click through {@link LeafiaFFClient#renderTankInfo}, so a press on
 * one is left to it and nothing is sent twice. Only the press is acted on, so it sends once per click rather than once per frame
 * held. The press is not cancelled: the base mod's tank has no click of its own, and the carried item's pickup or drop proceeds as
 * for any click on the screen, as upstream's did.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class LeafiaFuzzyScreenClient {

	private LeafiaFuzzyScreenClient() {}

	@SubscribeEvent
	public static void onPress(ScreenEvent.MouseButtonPressed.Pre event) {
		if(!LeafiaFeatures.fuzzyIdentifier) return;
		if(event.getScreen() instanceof LCEGuiInfoContainer<?>) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) return;
		ItemStack carried = mc.player.containerMenu.getCarried();
		FluidType type = FuzzyScreenClicks.target(event.getScreen(), event.getMouseX(), event.getMouseY(), carried);
		if(type != null) LeafiaFFClient.sendFuzzyClick(type, Component.literal(type.getLocalizedName()));
	}
}
