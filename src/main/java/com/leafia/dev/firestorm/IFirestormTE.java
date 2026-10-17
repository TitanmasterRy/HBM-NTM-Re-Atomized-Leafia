package com.leafia.dev.firestorm;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.blockentity.IRepairable;
import com.hbm.blockentity.machine.BlockEntityMachineChemicalPlant;
import com.hbm.inventory.RecipesCommon.AStack;
import com.leafia.overwrite_contents.LeafiaFirestorm;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that fire can burn out for good.
 *
 * <p>Upstream: {@code com/leafia/dev/firestorm/IFirestormTE.java}, which {@code MixinTileEntityMachineChemicalPlant}
 * mixed into the base mod's chemical plant. Nothing in the base mod can be made to implement an addon
 * interface from outside (D-1001), so the plant's side of this is {@link LeafiaFirestorm.ChemicalPlant}, a
 * stateless adapter over the plant that keeps its flags in the plant's own extra tag; {@link #of} finds it. A
 * block entity of the addon's own may implement this directly and {@link #of} answers it as itself.
 *
 * <p>Like upstream's it is an {@link IRepairable} that cannot be repaired: nothing is damaged, there is
 * nothing to bring back, and a burnt-out machine stays burnt out.
 */
public interface IFirestormTE extends IRepairable {

	/** Fire is next to this machine: roll for it to burn out. */
	void catchFire();

	boolean isDestroyed();

	default boolean canCatchFire() {
		return !isDestroyed();
	}

	@Override
	default boolean isDamaged() {
		return false;
	}

	@Override
	default List<AStack> getRepairMaterials() {
		return Collections.emptyList();
	}

	@Override
	default void repair(Player player) {}

	/** The firestorm face of a block entity, or null if fire cannot burn it out. */
	@Nullable
	static IFirestormTE of(@Nullable BlockEntity be) {
		if(be instanceof IFirestormTE own) return own;
		if(be instanceof BlockEntityMachineChemicalPlant plant) return new LeafiaFirestorm.ChemicalPlant(plant);
		return null;
	}
}
