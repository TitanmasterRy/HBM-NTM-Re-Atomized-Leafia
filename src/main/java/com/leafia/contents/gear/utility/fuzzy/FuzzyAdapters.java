package com.leafia.contents.gear.utility.fuzzy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.hbm.blockentity.machine.BlockEntityCondenser;
import com.hbm.blockentity.machine.BlockEntityHeatBoiler;
import com.hbm.blockentity.machine.BlockEntityHeatBoilerIndustrial;
import com.hbm.blockentity.machine.BlockEntityTurbineBase;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What the fuzzy identifier reads off the base mod's machines that upstream made {@link IFuzzyCompatible}.
 *
 * <p>Upstream: four mixins into {@code com.hbm} - {@code MixinTileEntityCondenser}, {@code MixinTileEntityHeatBoiler},
 * {@code MixinTileEntityHeatBoilerIndustrial} and {@code MixinTileEntityTurbineBase} - each bolting one method onto a
 * machine. D-1001 forbids that, and D-1053 left the interface with no implementor. A table from the machine's class
 * to a function that reads its output fluid needs nothing from the base mod, which is the "pure A" that
 * the completion survey called these four: {@link #outputOf} asks the interface first, then this table, in
 * order, and {@link FuzzyIdentifierItem} calls it where it called {@code instanceof IFuzzyCompatible}.
 *
 * <p>The answers are upstream's: the condenser makes water; a boiler (either kind) and a turbine put out whatever their
 * second tank holds - so a boiler asked while it is on steam gives steam, and on superheated steam, that.
 * Another mod's machine can add its own row with {@link #register}.
 */
public final class FuzzyAdapters {

	private FuzzyAdapters() {}

	private record Adapter<T extends BlockEntity>(Class<T> type, Function<T, FluidType> output) {
		@Nullable
		FluidType read(BlockEntity be) {
			return type.isInstance(be) ? output.apply(type.cast(be)) : null;
		}
	}

	private static final List<Adapter<?>> ADAPTERS = new ArrayList<>();

	static {
		register(BlockEntityCondenser.class, condenser -> Fluids.WATER);
		register(BlockEntityHeatBoiler.class, boiler -> boiler.tanks[1].getTankType());
		register(BlockEntityHeatBoilerIndustrial.class, boiler -> boiler.tanks[1].getTankType());
		register(BlockEntityTurbineBase.class, turbine -> turbine.tanks[1].getTankType());
	}

	/** Adds a machine the fuzzy identifier can read a fluid off. */
	public static synchronized <T extends BlockEntity> void register(Class<T> type, Function<T, FluidType> output) {
		ADAPTERS.add(new Adapter<>(type, output));
	}

	/** The fluid a machine wants an identifier set to, or null if it is not one the identifier reads this way. */
	@Nullable
	public static synchronized FluidType outputOf(@Nullable BlockEntity be) {
		if(be == null) return null;
		if(be instanceof IFuzzyCompatible own) return own.getOutputType();
		for(Adapter<?> adapter : ADAPTERS) {
			FluidType type = adapter.read(be);
			if(type != null) return type;
		}
		return null;
	}
}
