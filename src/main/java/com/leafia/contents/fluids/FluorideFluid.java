package com.leafia.contents.fluids;

import com.leafia.contents.fluids.LeafiaLiquids.Liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Liquid fluoride - the fluoride salt of the molten-salt reactor, as a pool.
 *
 * <p>Upstream: {@code com.leafia.contents.fluids.FluorideFluid}, a Forge {@code Fluid} named
 * {@code fluoride} (its unlocalised name is {@code hbmfluid.flibe}) whose block is a
 * {@code BlockFluidClassic} of {@code Material.LAVA}. The reactor's plug is what pours it - it
 * places this block when the salt is molten and drains it again when it is not.
 *
 * <h2>The numbers, and what each became</h2>
 *
 * <table>
 * <tr><td>{@code setDensity(1000)}</td><td>NeoForge's default, and Forge's</td></tr>
 * <tr><td>{@code setTemperature(500+273)}</td><td>{@link #TEMPERATURE} 773 K</td></tr>
 * <tr><td>viscosity, untouched</td><td>Forge's 1000 - NeoForge's default</td></tr>
 * <tr><td>{@code quantaPerBlock} 8</td><td>seven blocks of flow, slope search 4: the defaults</td></tr>
 * <tr><td>{@code tickRate} 30</td><td>{@link #TICK_RATE}</td></tr>
 * <tr><td>{@code Material.LAVA}</td><td>cannot be swum in, cannot drown, mobs avoid it; and burns,
 *   which is {@link LeafiaLiquids#lavaBurn}</td></tr>
 * <tr><td>{@code getFogColor}</td><td>the client extension's fog colour</td></tr>
 * </table>
 *
 * <p><b>Contact.</b> On top of what lava does, upstream's {@code onEntityCollision} calls
 * {@code entity.setFire(3)} - three seconds, which lava's fifteen already outlasts, so it changes
 * nothing for anything that burns and is kept for the things that do not take lava's damage but
 * still catch fire. Every entity, living or not, is treated alike.
 */
public final class FluorideFluid {

	private FluorideFluid() {}

	/** {@code Fluid.setTemperature(500+273)}. */
	public static final int TEMPERATURE = 500 + 273;
	/** {@code FluorideFluidBlock.tickRate}. */
	public static final int TICK_RATE = 30;
	/** {@code entity.setFire(3)}, in seconds. */
	public static final float FIRE_SECONDS = 3F;
	/** {@code FluorideFluidBlock.getFogColor}: {@code new Vec3d(0.851, 0.867, 0.765)}. */
	public static final float[] FOG = {0.851F, 0.867F, 0.765F};

	static Liquid define() {
		return LeafiaLiquids.define("fluoride", "fluoride_fluid", FluorideFluid::type,
				ClassicFluid.Source::new, ClassicFluid.Flowing::new, FluorideFluidBlock::new,
				LeafiaLiquids.liquidProps(MapColor.FIRE),
				p -> p.tickRate(TICK_RATE));
	}

	static FluidType type() {
		return new FluidType(FluidType.Properties.create()
				.descriptionId("hbmfluid.flibe")
				.temperature(TEMPERATURE)
				// Material.LAVA, as NeoForge's own lava type says it.
				.canSwim(false)
				.canDrown(false)
				.pathType(PathType.LAVA)
				.adjacentPathType(null)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
	}

	/** Upstream's {@code FluorideFluidBlock}. */
	public static class FluorideFluidBlock extends LiquidBlock {

		public FluorideFluidBlock(FlowingFluid fluid, Properties properties) {
			super(fluid, properties);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
			LeafiaLiquids.lavaBurn(entity, pos);
			entity.igniteForSeconds(FIRE_SECONDS);
		}
	}
}
