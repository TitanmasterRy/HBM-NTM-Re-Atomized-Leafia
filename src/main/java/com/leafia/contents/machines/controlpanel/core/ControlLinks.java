package com.leafia.contents.machines.controlpanel.core;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blockentity.BlockEntityProxyBase;
import com.hbm.blocks.BlockDummyable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What a linker's stored position means to the panel editor (CCP-EDITOR, D-3066). A multi-detonator stores whichever block the
 * player clicked, which for a multiblock is usually a dummy; the panel links to the <b>core</b>. NTM-CE's
 * {@code SubElementLinker} resolved it with {@code BlockDummyable.findCore} and the dummy's {@code target}; this does the same
 * with the base mod's {@link BlockDummyable#findCore} and {@link BlockEntityProxyBase#getCore}, on either side (it only reads
 * loaded blocks).
 *
 * <p>Common code, so the server can apply the same resolution when it decides what a player's linker presents
 * ({@code ControlPanelTE.presentedLinks}): the editor stores the core, so the core has to count as presented.
 */
public final class ControlLinks {

	private ControlLinks() {}

	/** The core of the multiblock at {@code pos}, or {@code pos} itself when it is not part of one. Never loads a chunk. */
	public static BlockPos resolveCore(Level level, BlockPos pos) {
		if(level == null || !level.isLoaded(pos))
			return pos;
		if(level.getBlockState(pos).getBlock() instanceof BlockDummyable dummy) {
			BlockPos core = dummy.findCore(level, pos);
			if(core != null)
				return core;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if(be instanceof BlockEntityProxyBase proxy) {
			BlockEntity core = proxy.getCore();
			if(core != null)
				return core.getBlockPos();
		}
		return pos;
	}

	/** What would answer a panel's event at this position, as far as a client can tell, or null. */
	public static IControllable controllableAt(Level level, BlockPos pos) {
		if(level == null || !level.isLoaded(pos))
			return null;
		BlockEntity be = level.getBlockEntity(pos);
		if(be == null || be.isRemoved())
			return null;
		if(be instanceof IControllable c)
			return c;
		return level.getCapability(ControlEventSystem.CONTROLLABLE, pos);
	}

	/**
	 * The receivable events a new control's default graphs are built from, in the order they index them (D-3064): the control's
	 * own out-events, then its in-events, then every linked machine's out-events, without repeats. CE's
	 * {@code SubElementEventEditor.accumulateEventTypes}, whose first part is what {@code populateDefaultNodes} reads
	 * ({@code receiveEvents.get(0)} is {@code ctrl_press} on a button).
	 */
	public static List<ControlEvent> accumulateReceiveEvents(Control control, List<IControllable> linked) {
		List<ControlEvent> events = new ArrayList<>();
		for(String name : control.getOutEvents())
			addEvent(events, name);
		for(String name : control.getInEvents())
			addEvent(events, name);
		for(IControllable c : linked) {
			for(String name : c.getOutEvents())
				addEvent(events, name);
		}
		return events;
	}

	/**
	 * The events a control may send: every in-event of every linked machine, without repeats, in the machines' order. CE's
	 * {@code SubElementEventEditor.accumulateEventTypes}, second half. An event nobody registered is left out, as above.
	 */
	public static List<ControlEvent> accumulateSendEvents(List<IControllable> linked) {
		List<ControlEvent> events = new ArrayList<>();
		for(IControllable c : linked) {
			for(String name : c.getInEvents())
				addEvent(events, name);
		}
		return events;
	}

	private static void addEvent(List<ControlEvent> events, String name) {
		if(!ControlEvent.isRegistered(name))
			return; // getRegisteredEvent throws for a name nobody registered; a machine may advertise one
		ControlEvent evt = ControlEvent.getRegisteredEvent(name);
		if(!events.contains(evt))
			events.add(evt);
	}
}
