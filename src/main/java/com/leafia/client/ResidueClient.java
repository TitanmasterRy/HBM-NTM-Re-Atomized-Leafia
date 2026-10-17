package com.leafia.client;

import com.leafia.unsorted.ParticleBalefire;
import com.leafia.unsorted.ParticleBalefireLava;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The client half of the balefire residue: what thermonuclear ash and a balefire corium pool do to
 * the air around them. Every method is a straight reading of the {@code randomDisplayTick} it came
 * from, and every method is called only from a block's {@code animateTick}, which only the client runs.
 *
 * <p>Kept out of the block classes so that they name no client class: a dedicated server loads
 * {@code AshBalefire} and never this.
 */
public final class ResidueClient {

	private ResidueClient() {}

	/** {@code AshBalefire.randomDisplayTick}, after the falling-dust call of its superclass. */
	public static void ashDisplayTick(Level level, int layers, BlockPos pos, RandomSource rand) {
		if(!(level instanceof ClientLevel client)) return;

		if(rand.nextDouble() < 0.2D)
			client.playLocalSound(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.5F, false);

		for(Direction dir : Direction.values()) {

			if(dir == Direction.DOWN) continue;

			for(int rep = 0; rep < 2; rep += (rand.nextInt(2) + 1)) {
				if(!client.getBlockState(pos.relative(dir)).isAir()) continue;

				double offs = rand.nextDouble();
				if(dir != Direction.UP && offs * 8 > layers) continue;

				double ix = pos.getX() + 0.5F + dir.getStepX() + rand.nextDouble() - 0.5D;
				double iy = pos.getY() + offs;
				double iz = pos.getZ() + 0.5F + dir.getStepZ() + rand.nextDouble() - 0.5D;

				if(dir.getStepX() != 0) ix = pos.getX() + 0.5F + dir.getStepX() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepX();
				if(dir.getStepY() != 0) iy = pos.getY() + layers / 8D + rand.nextDouble() * 0.125;
				if(dir.getStepZ() != 0) iz = pos.getZ() + 0.5F + dir.getStepZ() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepZ();

				Minecraft.getInstance().particleEngine.add(new ParticleBalefire(client, ix, iy, iz));
				if(rand.nextInt(2) == 0)
					Minecraft.getInstance().particleEngine.add(new ParticleBalefireLava(client, ix, iy, iz));
				client.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.0, 0.0);
				client.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.1, 0.0);
			}
		}
	}

	/** {@code BaleCoriumFluidBlock.randomDisplayTick}. */
	public static void baleCoriumDisplayTick(Level level, BlockPos pos, RandomSource rand) {
		if(!(level instanceof ClientLevel client)) return;

		if(rand.nextInt(5) == 0) {
			if(rand.nextInt(20) == 0)
				client.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.7F + rand.nextFloat() * 0.25F, false);

			int lava = rand.nextInt(2) + 1;
			for(int i = 0; i < lava; i++)
				Minecraft.getInstance().particleEngine.add(new ParticleBalefireLava(client, pos.getX() + rand.nextFloat(), pos.getY() + rand.nextFloat(), pos.getZ() + rand.nextFloat()));

			int smoke = rand.nextInt(3);
			for(int i = 0; i < smoke; i++)
				client.addParticle(ParticleTypes.SMOKE,
						pos.getX() + rand.nextFloat(), pos.getY() + rand.nextFloat(), pos.getZ() + rand.nextFloat(),
						rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25);

			int large = rand.nextInt(3);
			for(int i = 0; i < large; i++)
				client.addParticle(ParticleTypes.LARGE_SMOKE,
						pos.getX() + rand.nextFloat(), pos.getY() + rand.nextFloat(), pos.getZ() + rand.nextFloat(),
						rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25, rand.nextDouble() / 2 - 0.25);
		}

		if(rand.nextInt(100) == 0)
			client.playLocalSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.2F + rand.nextFloat() * 0.2F, 0.7F + rand.nextFloat() * 0.25F, false);
	}
}
