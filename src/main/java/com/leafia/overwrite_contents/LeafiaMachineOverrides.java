package com.leafia.overwrite_contents;

import com.hbm.blocks.BlockEntityOverrides;
import com.hbm.blocks.ModBlocks;
import com.leafia.config.LeafiaFeatures;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.lib.LeafiaRef;
import com.leafia.overwrite_contents.fel.LeafiaFELTE;
import com.leafia.overwrite_contents.siren.LeafiaSirenTE;
import com.leafia.overwrite_contents.welding.LeafiaArcWelderTE;
import com.leafia.overwrite_contents.welding.LeafiaSolderingStationTE;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * How the siren, arc welder, soldering station and free-electron laser overhauls go into the base mod: an override on
 * each base block, read each time the block makes its block entity (M8a, D-2201), answering with the addon's subclass
 * while that overhaul's switch is on and declining otherwise - so the base block makes its own. As for the DFC and the
 * ZIRNOX (D-3011, D-3020), a machine already in a world keeps the type it was saved with.
 *
 * <p>The base mod's renderers are keyed by its own types, so the client registers them again for the addon's: the
 * welder, the soldering station and the laser look exactly as they did. The siren has no renderer. The menus and
 * screens are the base mod's, inherited.
 *
 * <p>And upstream's {@code MixinMachineArcWelder}: the welder's item says, in red, to keep it away from water.
 */
public final class LeafiaMachineOverrides {

	private LeafiaMachineOverrides() {}

	/** Common setup: the four overrides, each when its type exists. */
	public static void commonSetup() {
		if(LeafiaBlockEntities.SIREN != null)
			BlockEntityOverrides.register(ModBlocks.machine_siren.get(), LeafiaBlockEntities.SIREN,
					(pos, state) -> LeafiaFeatures.sirenOverhaul ? new LeafiaSirenTE(LeafiaBlockEntities.SIREN.get(), pos, state) : null);
		if(LeafiaBlockEntities.ARC_WELDER != null)
			BlockEntityOverrides.register(ModBlocks.machine_arc_welder.get(), LeafiaBlockEntities.ARC_WELDER,
					(pos, state) -> LeafiaFeatures.arcWelderOverhaul && isCore(state) ? new LeafiaArcWelderTE(LeafiaBlockEntities.ARC_WELDER.get(), pos, state) : null);
		if(LeafiaBlockEntities.SOLDERING_STATION != null)
			BlockEntityOverrides.register(ModBlocks.machine_soldering_station.get(), LeafiaBlockEntities.SOLDERING_STATION,
					(pos, state) -> LeafiaFeatures.solderingOverhaul && isCore(state) ? new LeafiaSolderingStationTE(LeafiaBlockEntities.SOLDERING_STATION.get(), pos, state) : null);
		if(LeafiaBlockEntities.FEL != null)
			BlockEntityOverrides.register(ModBlocks.machine_fel.get(), LeafiaBlockEntities.FEL,
					(pos, state) -> LeafiaFeatures.felOverhaul && isCore(state) ? new LeafiaFELTE(LeafiaBlockEntities.FEL.get(), pos, state) : null);
	}

	/** Only a multiblock's core is the machine; its dummies and extras keep whatever the base block makes them. */
	private static boolean isCore(net.minecraft.world.level.block.state.BlockState state) {
		return !state.hasProperty(com.hbm.blocks.BlockDummyable.PART) || state.getValue(com.hbm.blocks.BlockDummyable.PART) == com.hbm.blocks.BlockDummyable.Part.CORE;
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static final class ClientModEvents {

		private ClientModEvents() {}

		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			if(LeafiaBlockEntities.ARC_WELDER != null)
				event.registerBlockEntityRenderer(LeafiaBlockEntities.ARC_WELDER.get(), com.hbm.render.blockentity.RenderArcWelder::new);
			if(LeafiaBlockEntities.SOLDERING_STATION != null)
				event.registerBlockEntityRenderer(LeafiaBlockEntities.SOLDERING_STATION.get(), com.hbm.render.blockentity.RenderSolderingStation::new);
			if(LeafiaBlockEntities.FEL != null)
				event.registerBlockEntityRenderer(LeafiaBlockEntities.FEL.get(), com.hbm.render.blockentity.RenderFEL::new);
		}
	}

	@OnlyIn(Dist.CLIENT)
	@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
	public static final class ClientGameEvents {

		private ClientGameEvents() {}

		/** Upstream's {@code MixinMachineArcWelder.addInformation}: the warning first, then the base lines. */
		@SubscribeEvent
		public static void onTooltip(ItemTooltipEvent event) {
			if(!LeafiaFeatures.arcWelderOverhaul || event.getItemStack().getItem() != ModBlocks.machine_arc_welder.get().asItem()) return;
			int at = Math.min(1, event.getToolTip().size());
			event.getToolTip().add(at, Component.translatable("tile.machine_arc_welder.warn").withStyle(ChatFormatting.RED));
		}
	}
}
