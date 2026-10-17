package com.leafia.contents.machines.misc.modular_turbine;

import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageUpgradeContext;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreData.StageUpgradeSummary;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE;

/**
 * Contributes offsets to one compiled steam stage.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/IMTStageUpgradeContributor.java},
 * verbatim. The {@link StageUpgradeContext} says whether the component sits inside the stage
 * being compiled or just before or after it, so a separator can tune the stages either side of it.
 */
public interface IMTStageUpgradeContributor {
	default void contributeStageUpgradeOffsets(StageUpgradeSummary summary, MTCoreTE core, StageUpgradeContext context, ModularTurbineComponentTE component) { }
}
