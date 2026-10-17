package com.leafia.contents.fluids;

import com.hbm.blocks.ModBlocks;
import com.leafia.contents.fluids.LeafiaLiquids.Liquid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Liquid concrete: a finite, heavy, slow fluid that cobwebs whatever is in it and, a tenth of the
 * time it is looked at, sets into the base mod's smooth concrete.
 *
 * <p>Upstream: {@code com.leafia.contents.fluids.ConcreteFluid}, a Forge {@code Fluid} named
 * {@code leafia_concrete} (unlocalised {@code hbmfluid.concrete}) whose block is a
 * {@code BlockFluidFinite} of the addon's own {@code material_concrete}. The base mod's chemical
 * sprayer places it through a mixin, which is a hand-off (D-1079).
 *
 * <h2>The numbers, and what each became</h2>
 *
 * <table>
 * <tr><td>{@code setDensity(31200)}</td><td>{@link #DENSITY}. Denser than lava's 3000, so it
 *   displaces both lava and water - {@link ClassicFluid#denser}</td></tr>
 * <tr><td>{@code setViscosity(2000)}</td><td>{@link #VISCOSITY}</td></tr>
 * <tr><td>temperature, untouched</td><td>Forge's 295 - {@link #TEMPERATURE}</td></tr>
 * <tr><td>{@code quantaPerBlock} 8</td><td>{@link FiniteFluid#QUANTA}</td></tr>
 * <tr><td>{@code tickRate} 40</td><td>{@link #TICK_RATE}</td></tr>
 * <tr><td>{@code setTickRandomly(true)}</td><td>{@link FiniteFluid#isRandomlyTicking} - every
 *   finite fluid has it</td></tr>
 * <tr><td>{@code 10% -> concrete_smooth}</td><td>{@link #HARDEN_ONE_IN}</td></tr>
 * <tr><td>{@code IFluidFog}: density 2.0, colour {@code 0x797979}</td><td>the client extension: 2.0 is
 *   what 1.12.2 used for lava, so it is lava's fog distance, in the same grey</td></tr>
 * <tr><td>{@code material_concrete}: a liquid that {@code blocksMovement()}</td><td>no swimming, no
 *   drowning, no push from the flow, and mobs path around it as a wall</td></tr>
 * </table>
 *
 * <p><b>Contact.</b> Creative and spectator players are left alone (on the server only, see below);
 * anything else is stuck as in a cobweb (a quarter speed sideways, a twentieth vertically, fall distance
 * reset) and a living entity whose <em>eyes</em> are in the block takes one point of in-wall damage
 * a tick.
 *
 * <p><b>Two upstream quirks, kept and flagged.</b> The creative check is {@code instanceof
 * EntityPlayerMP}, which a client-side player is not, so the local player of a creative-mode
 * client is still slowed by the web; and the hardening runs after the flow step at the block's
 * <em>starting</em> position, so a block that has just drained downward still has a one in ten
 * chance of leaving smooth concrete behind where it was.
 */
public final class ConcreteFluid {

	private ConcreteFluid() {}

	/** {@code setDensity(31200)}. */
	public static final int DENSITY = 31200;
	/** {@code setViscosity(2000)}. */
	public static final int VISCOSITY = 2000;
	/** Forge's {@code Fluid} default; upstream does not set one. */
	public static final int TEMPERATURE = 295;
	/** {@code ConcreteFluidBlock.tickRate}. */
	public static final int TICK_RATE = 40;
	/** {@code random.nextInt(10) == 0}. */
	public static final int HARDEN_ONE_IN = 10;
	/** {@code getFogColor}. */
	public static final int FOG_COLOR = 0x797979;
	/** What a cobweb does, which is what {@code entity.setInWeb()} did. */
	public static final Vec3 WEB = new Vec3(0.25, 0.05, 0.25);

	static Liquid define() {
		return LeafiaLiquids.define("leafia_concrete", "leafia_concrete_fluid", ConcreteFluid::type,
				ConcreteSource::new, ConcreteFlowing::new, ConcreteFluidBlock::new,
				LeafiaLiquids.liquidProps(MapColor.COLOR_GRAY),
				p -> p.tickRate(TICK_RATE));
	}

	static FluidType type() {
		return new FluidType(FluidType.Properties.create()
				.descriptionId("hbmfluid.concrete")
				.density(DENSITY)
				.viscosity(VISCOSITY)
				.temperature(TEMPERATURE)
				.canSwim(false)
				.canDrown(false)
				.canPushEntity(false)
				.pathType(PathType.BLOCKED)
				.adjacentPathType(null)
				.sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
				.sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
	}

	/**
	 * Upstream's tail of {@code updateTick}: one time in ten, whatever is at {@code pos}
	 * - fluid, air or something that has since moved in - becomes smooth concrete.
	 */
	public static void harden(Level level, BlockPos pos, RandomSource random) {
		if(level.isClientSide || random.nextInt(HARDEN_ONE_IN) != 0) return;
		level.setBlock(pos, ModBlocks.concrete_smooth.get().defaultBlockState(), 3);
	}

	public static class ConcreteSource extends FiniteFluid.Source {

		public ConcreteSource(BaseFlowingFluid.Properties properties) {
			super(properties);
		}

		@Override
		public void afterFlow(Level level, BlockPos pos, RandomSource random) {
			harden(level, pos, random);
		}
	}

	public static class ConcreteFlowing extends FiniteFluid.Flowing {

		public ConcreteFlowing(BaseFlowingFluid.Properties properties) {
			super(properties);
		}

		@Override
		public void afterFlow(Level level, BlockPos pos, RandomSource random) {
			harden(level, pos, random);
		}
	}

	/** Upstream's {@code ConcreteFluidBlock}. */
	public static class ConcreteFluidBlock extends LiquidBlock {

		public ConcreteFluidBlock(FlowingFluid fluid, Properties properties) {
			super(fluid, properties);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
			if(entity instanceof ServerPlayer player && (player.isSpectator() || player.isCreative())) return;
			entity.makeStuckInBlock(state, WEB);
			if(entity instanceof LivingEntity
					&& BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()).equals(pos))
				entity.hurt(level.damageSources().inWall(), 1.0F);
		}
	}
}
