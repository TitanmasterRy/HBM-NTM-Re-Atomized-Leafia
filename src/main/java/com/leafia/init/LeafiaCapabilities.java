package com.leafia.init;

import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * The addon's block capabilities.
 *
 * <h2>Why this file exists, and the bug that produced it</h2>
 *
 * In 1.12.2 a block entity answers {@code getCapability}/{@code hasCapability} itself, so a
 * capability is a pair of overrides on the class that provides it. In 1.21.1 it is a
 * <b>registration</b>: {@code RegisterCapabilitiesEvent} maps a block entity type to a
 * provider, and a block entity that merely holds an {@code ItemStackHandler} exposes nothing.
 *
 * <p>The broof was ported with its handler, its NBT and its menu, and <b>without this</b>: a
 * storage block no hopper, pipe or comparator could reach. It compiled, it saved, its screen
 * worked, and nothing anywhere complained. A GameTest that asked the level for the capability
 * from all six sides is what found it — see {@code LeafiaBroofTests.everySideSeesTheWholeInventory}
 * and D-1049.
 *
 * <p>So: <b>porting a {@code getCapability} override means adding a line here.</b> There is no
 * compiler error and no log line for forgetting.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaCapabilities {

	private LeafiaCapabilities() {}

	/**
	 * Upstream's {@code BroofTE.getCapability} exposes the handler on every side.
	 *
	 * <p>Its {@code getAccessibleSlotsFromSide} builds an array of all fifty-four slots for
	 * every face, so the wrapper it goes through is an identity and the handler is returned
	 * directly. The {@code (be, dir) -> be.inventory} below ignores {@code dir} for that
	 * reason rather than by omission.
	 */
	@SubscribeEvent
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {

		/*
		 * The Custom Control Panel's adapters over the base mod's machines (CCP-BASE-REACH, D-3068): a CONTROLLABLE provider per
		 * base block entity type the panel can link to, so the base mod never names the panel.
		 */
		if(LeafiaFeatures.controlPanel)
			com.leafia.contents.machines.controlpanel.core.reach.BaseReach.register(event);

		if(LeafiaFeatures.broof)
			event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LeafiaBlockEntities.BROOF.get(),
					(be, dir) -> be.inventory);

		/*
		 * The regex filter's handler is on its open back only: upstream answers when the side is
		 * null or is the opposite of its facing, and the facing is where it pushes to.
		 */
		if(LeafiaFeatures.regexFilter)
			event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LeafiaBlockEntities.REGEX_FILTER.get(),
					(be, dir) -> dir == null || dir == be.getFacing().getOpposite() ? be.inventory : null);

		/*
		 * The PWR fuel column: CE's TileEntityInventoryBase answers its one slot on every face, and its
		 * stacked blocks' TileEntityProxyInventory forwards to the column's top (D-3006).
		 */
		/*
		 * The duct utilities' handlers (D-3019): upstream's hasCapability answers on the facing's axis, or unsided, and
		 * the pump, the converter and the filter's old tank design all return themselves - decoys that hold no tank and
		 * take or give nothing, there so a duct will join them. The filter the block makes (FFFilterTE2) is no handler at
		 * all, so no duct connects to it.
		 */
		/*
		 * The debug Forge-fluid source and tank (D-3031): upstream's hasCapability answers on every side, and both return
		 * themselves.
		 */
		if(LeafiaFeatures.debugFFBlocks()) {
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.FF_SOURCE.get(), (be, dir) -> be);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.FF_TANK.get(), (be, dir) -> be);
		}

		if(LeafiaFeatures.ffDuct) {
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.FF_PUMP.get(),
					(be, dir) -> dir == null || dir.getAxis() == be.getFacing().getAxis() ? be : null);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.FF_CONVERTER.get(),
					(be, dir) -> dir == null || dir.getAxis() == be.getFacing().getAxis() ? be : null);
		}

		/*
		 * The MSR plug and ejector are NeoForge fluid handlers, on upstream's sides (D-3012): the plug on its
		 * front and its bottom, the ejector on its front, both also to an unsided query. No other MSR block
		 * exposes anything, as upstream's override none.
		 */
		if(LeafiaFeatures.lftrReactor()) {
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.MSR_PLUG.get(),
					(be, dir) -> dir == null || dir == be.getDirection() || dir == net.minecraft.core.Direction.DOWN ? be : null);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.MSR_EJECTOR.get(),
					(be, dir) -> dir == null || dir == be.getDirection() ? be : null);
			/*
			 * The salt separator (D-3015): upstream answers the fluid handler on every side, and its
			 * TileEntityProxyCombo(true, true, true) forwards the core's answer, so a Forge duct beside one of its
			 * proxy cells sees a machine end. The core is the handler either way.
			 */
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.SALT_SEPARATOR.get(),
					(be, dir) -> be);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.SALT_SEPARATOR_DUMMY.get(),
					(be, dir) -> be.getTile() instanceof com.leafia.contents.machines.reactors.lftr.processing.separator.SaltSeparatorTE separator ? separator : null);
		}

		/*
		 * The mixing vat (D-3016): the core answers every side, as upstream's "getting lazier and lazier"; each of its
		 * three port blocks answers only its own side or an unsided query, and the proxy decides what it gives.
		 */
		if(LeafiaFeatures.mixingVatMachine()) {
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.MIXING_VAT.get(), (be, dir) -> be);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.MIXING_VAT_DUMMY.get(),
					(be, dir) -> dir == null || dir == be.facing() ? be : null);
		}

		/*
		 * The coolant heat exchanger (D-3016): the core is the Forge handler (it takes the set fluid in FF mode), and each of
		 * its six port blocks shows the core's, which is what lets a Special Pipe join a port - the duct asks the port
		 * block for a handler and the core for an IFFHandler (LeafiaUtil.canConnectFF).
		 */
		if(LeafiaFeatures.coolantHeatexMachine()) {
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.COOLANT_HEATEX.get(), (be, dir) -> be);
			com.leafia.contents.fluids.ff.LeafiaFF.registerBlock(event, LeafiaBlockEntities.COOLANT_HEATEX_DUMMY.get(),
					(be, dir) -> be.getCore() instanceof com.leafia.contents.machines.misc.heatex.CoolantHeatexTE core ? core : null);
		}

		if(LeafiaFeatures.pwrReactor()) {
			event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LeafiaBlockEntities.PWR_ELEMENT.get(),
					(be, dir) -> be.inventory);
			event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LeafiaBlockEntities.PWR_ELEMENT_PROXY.get(),
					(be, dir) -> be.getTE() instanceof com.leafia.contents.machines.reactors.pwr.blocks.components.element.PWRElementTE top ? top.inventory : null);
		}
	}
}
