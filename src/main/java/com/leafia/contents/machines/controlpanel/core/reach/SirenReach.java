package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Collections;
import java.util.List;

import com.hbm.blockentity.machine.BlockEntityMachineSiren;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;

/**
 * The base mod's siren, driven by a panel (batch 9, D-3069; hand-off H-CCP-2, which master took as D-2275). In {@code siren_set_state},
 * whose {@code isOn} sets the siren's public {@code ctrlActive}; the siren ORs it with its redstone signal in {@code isActive()}, so a
 * panel can sound it and cannot silence one that a lever is holding on, exactly as CE's. No query keys and no out-events, as CE's.
 *
 * <p>The addon's overhauled siren ({@code LeafiaSirenTE}) implements {@code IControllable} itself and is found before this adapter.
 */
public final class SirenReach extends ReachAdapter<BlockEntityMachineSiren> {

	public static final String IN = "siren_set_state";

	public SirenReach(BlockEntityMachineSiren be) {
		super(be);
	}

	@Override
	public List<String> getInEvents() {
		return Collections.singletonList(IN);
	}

	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals(IN)) {
			DataValue on = e.vars.get("isOn");
			if(on != null)
				be.ctrlActive = on.getBoolean();
		}
	}
}
