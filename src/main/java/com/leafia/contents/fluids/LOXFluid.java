package com.leafia.contents.fluids;

import com.leafia.contents.fluids.LeafiaLiquids.Liquid;
import com.leafia.contents.potion.LeafiaPotion;
import com.leafia.init.LeafiaDamageSources;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Liquid oxygen, placed by a bucket and fatal to stand in.
 *
 * <p>Upstream: {@code com.leafia.contents.fluids.LOXFluid}, a Forge {@code Fluid} named
 * {@code oxygen} whose block is a {@code BlockFluidClassic} of {@code Material.WATER}. Both
 * halves are one class here, because 1.21.1's fluid and its type are two registry entries and the
 * block is a third; {@link LeafiaLiquids} registers them together.
 *
 * <h2>The numbers, and what each became</h2>
 *
 * <table>
 * <tr><td>{@code setTemperature(-100+273)}</td><td>{@link #TEMPERATURE} 173 K - the same unit</td></tr>
 * <tr><td>density, viscosity</td><td>untouched upstream, so Forge's 1000 and 1000 - NeoForge's defaults</td></tr>
 * <tr><td>{@code quantaPerBlock} 8 (the default)</td><td>source 8, seven blocks of flow:
 *   {@code levelDecreasePerBlock} 1, which is {@code BaseFlowingFluid}'s default</td></tr>
 * <tr><td>{@code calculateFlowCost}'s depth limit of 4</td><td>{@code slopeFindDistance} 4, also the default</td></tr>
 * <tr><td>{@code tickRate} 30</td><td>{@link #TICK_RATE}, the fluid's tick delay. Water's is 5</td></tr>
 * <tr><td>{@code Material.WATER}</td><td>swims, drowns, puts fire out, floats a boat, hydrates farmland and
 *   resets fall distance: the six {@code FluidType} properties that are water's, and the vanilla
 *   path types</td></tr>
 * <tr><td>{@code getFogColor}</td><td>the client extension's fog colour</td></tr>
 * </table>
 *
 * <p>Material.WATER was also what put a player under the blue overlay and the underwater fog, so
 * the client extension turns on both.
 *
 * <p><b>Contact.</b> Upstream's {@code onEntityCollision} gives any living thing in the block
 * {@code frigid} for ten seconds at amplifier 1 and one point of {@code cryo} damage, every tick it
 * touches the block. The effect is applied on the server only: 1.12.2 ran it on both sides, and
 * a client-side effect is overwritten by the server's the next sync.
 */
public final class LOXFluid {

	private LOXFluid() {}

	/** {@code Fluid.setTemperature(-100+273)}. */
	public static final int TEMPERATURE = -100 + 273;
	/** {@code LOXFluidBlock.tickRate}. */
	public static final int TICK_RATE = 30;
	/** {@code new PotionEffect(LeafiaPotion.frigid, 10*20, 1)}. */
	public static final int FRIGID_TICKS = 10 * 20;
	public static final int FRIGID_AMPLIFIER = 1;
	/** {@code attackEntityFrom(LeafiaDamageSource.cryo, 1)}. */
	public static final float CRYO_DAMAGE = 1.0F;
	/** {@code LOXFluidBlock.getFogColor}: {@code new Vec3d(0.502, 0.859, 1)}. */
	public static final float[] FOG = {0.502F, 0.859F, 1.0F};

	static Liquid define() {
		return LeafiaLiquids.define("oxygen", "oxygen_fluid", LOXFluid::type,
				ClassicFluid.Source::new, ClassicFluid.Flowing::new, LOXFluidBlock::new,
				LeafiaLiquids.liquidProps(MapColor.WATER),
				p -> p.tickRate(TICK_RATE));
	}

	static FluidType type() {
		return new FluidType(FluidType.Properties.create()
				.descriptionId("hbmfluid.oxygen")
				.temperature(TEMPERATURE)
				// Material.WATER, as NeoForge's own water type says it.
				.fallDistanceModifier(0F)
				.canExtinguish(true)
				.supportsBoating(true)
				.canHydrate(true)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
				.sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH));
	}

	/** Upstream's {@code LOXFluidBlock}. */
	public static class LOXFluidBlock extends LiquidBlock {

		public LOXFluidBlock(FlowingFluid fluid, Properties properties) {
			super(fluid, properties);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
			if(level.isClientSide || !(entity instanceof LivingEntity living)) return;
			living.addEffect(new MobEffectInstance(LeafiaPotion.frigid, FRIGID_TICKS, FRIGID_AMPLIFIER));
			living.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.cryo), CRYO_DAMAGE);
		}
	}
}
