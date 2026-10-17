package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Arrays;
import java.util.List;

import com.hbm.blockentity.turret.BlockEntityTurretBaseNT;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;

import net.minecraft.core.BlockPos;

/**
 * NTM-CE's {@code TileEntityTurretBaseNT} panel half, as one adapter for every turret core (batch 8, D-3068).
 *
 * <ul>
 * <li>in {@code turret_set_target}: {@code players}, {@code passive}, {@code hostile}, {@code machines} set the four target
 * switches ({@code targetPlayers}, {@code targetAnimals}, {@code targetMobs}, {@code targetMachines});</li>
 * <li>in {@code turret_switch}: {@code isOn} switches the turret. No query keys and no out-events, as CE.</li>
 * </ul>
 *
 * <h2>The part of {@code turret_switch} that does nothing</h2>
 *
 * CE's event also carries {@code isBlacklistFilter}, which sets {@code isBlacklistMobFilter}, a CE-only mode that turns the turret
 * chip's name list from "never shoot these" into "shoot only these". The base mod ({@code hbm-src}) has no such mode, so the
 * variable is accepted and ignored: the event's other half, {@code isOn}, is applied. A panel saved with the variable loads and
 * runs; it just cannot change a filter the machine does not have.
 *
 * <h2>The siren</h2>
 *
 * CE's {@code siren_set_state} is {@link SirenReach}'s, since master gave the siren {@code ctrlActive} (D-2275, hand-off H-CCP-2,
 * taken in batch 9).
 */
public final class TurretReach extends ReachAdapter<BlockEntityTurretBaseNT> {

	public static final String SET_TARGET = "turret_set_target";
	public static final String SWITCH = "turret_switch";

	public TurretReach(BlockEntityTurretBaseNT be) {
		super(be);
	}

	@Override
	public List<String> getInEvents() {
		return Arrays.asList(SET_TARGET, SWITCH);
	}

	@Override
	public void receiveEvent(BlockPos from, ControlEvent e) {
		if(e.name.equals(SET_TARGET)) {
			be.targetPlayers = flag(e, "players", false);
			be.targetMobs = flag(e, "hostile", true);
			be.targetAnimals = flag(e, "passive", false);
			be.targetMachines = flag(e, "machines", true);
			be.setChanged();
		} else if(e.name.equals(SWITCH)) {
			be.isOn = flag(e, "isOn", true);
			be.setChanged();
		}
	}
}
