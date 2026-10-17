package com.leafia.contents.machines.misc.modular_turbine;

import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.MachineUpgradeSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;

/**
 * Contributes offsets to the shaft-wide machine model.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/IMTMachineUpgradeContributor.java},
 * verbatim. Both the component's block <em>and</em> its block entity are asked, so a part can
 * contribute from either; {@link MTCoreTE} casts every component's block to this, which is why
 * {@link ModularTurbineBlockBase} implements it with an empty default.
 */
public interface IMTMachineUpgradeContributor {
	default void contributeMachineUpgradeOffsets(MachineUpgradeSummary summary, MTCoreTE core, ModularTurbineComponentTE component) { }
}
