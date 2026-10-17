package com.leafia.contents.machines.reactors.pwr.blocks.components.element;

import com.leafia.unsorted.ParticleBalefire;
import com.leafia.unsorted.ParticleBalefireLava;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The client half of {@code PWRElementBlock.randomDisplayTick}: a column holding a blazing balefire rod
 * throws balefire, balefire lava and smoke off all six faces ("definitely not copypasted from
 * AshBalefire.java"). Behind its own class so the block never names a client type; {@code animateTick}
 * only runs on a client.
 */
public final class PWRElementClient {

	private PWRElementClient() {}

	public static void balefire(Level world, BlockPos pos, RandomSource rand) {
		if(!(world instanceof ClientLevel client)) return;
		for(Direction dir : Direction.values()) {
			for(int rep = 0; rep < 2; rep += (rand.nextInt(2) + 1)) {
				double ix = pos.getX() + 0.5F + dir.getStepX() + rand.nextDouble() - 0.5D;
				double iy = pos.getY() + 0.5F + dir.getStepY() + rand.nextDouble() - 0.5D;
				double iz = pos.getZ() + 0.5F + dir.getStepZ() + rand.nextDouble() - 0.5D;

				if(dir.getStepX() != 0) ix = pos.getX() + 0.5F + dir.getStepX() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepX();
				if(dir.getStepY() != 0) iy = pos.getY() + 0.5F + dir.getStepY() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepY();
				if(dir.getStepZ() != 0) iz = pos.getZ() + 0.5F + dir.getStepZ() * 0.5 + rand.nextDouble() * 0.125 * dir.getStepZ();

				Minecraft.getInstance().particleEngine.add(new ParticleBalefire(client, ix, iy, iz));
				if(rand.nextInt(2) == 0) Minecraft.getInstance().particleEngine.add(new ParticleBalefireLava(client, ix, iy, iz));
				world.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.0, 0.0);
				world.addParticle(ParticleTypes.SMOKE, ix, iy, iz, 0.0, 0.1, 0.0);
			}
		}
	}
}
