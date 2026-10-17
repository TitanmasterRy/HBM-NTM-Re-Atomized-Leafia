package com.leafia.contents.machines.misc.modular_turbine.core;

import com.leafia.contents.machines.misc.modular_turbine.ParticleMTSteam;
import com.leafia.contents.machines.misc.modular_turbine.core.MTCoreTE.TurbineAssembly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * The client half of {@code MTCoreTE.update()}: the steam that runs along each stage.
 *
 * <p>Not an upstream file. Upstream's loop and its {@code @SideOnly(Side.CLIENT)
 * local$spawnParticle} sit on the tile entity; they name {@link Minecraft} and a client particle,
 * which a dedicated server does not have, so they live here and the core calls in only from its
 * client branch — the shape {@code MTFlywheelClient} uses.
 */
public final class MTCoreClient {

	private MTCoreClient() {}

	/**
	 * For every stage that took steam this tick, from each inlet, puffs run forward and back to the
	 * next inlet or the end of the stage. Faster shafts blow faster and more of them; turbulence
	 * slows some, and at random starts some partway along or sends them back the other way.
	 */
	public static void spawnSteam(MTCoreTE core) {
		if(!(core.getLevel() instanceof ClientLevel world)) return;
		Direction dir = core.getAssemblyFacing();
		for(TurbineAssembly assembly : core.assemblies) {
			for(Integer p : assembly.receivingPositions) {
				if(assembly.lastOps <= 0) continue;
				BlockPos center = core.getBlockPos().relative(dir, p + 1);
				int index = assembly.positionsInOrder.indexOf(p);
				int distForward = 0;
				int distBackward = 0;
				for(int i = index + 1; i < assembly.positionsInOrder.size(); i++) {
					if(assembly.receivingPositions.contains(assembly.positionsInOrder.get(i))) break;
					distForward++;
				}
				for(int i = index - 1; i >= 0; i--) {
					if(assembly.receivingPositions.contains(assembly.positionsInOrder.get(i))) break;
					distBackward++;
				}
				double speed = 1 + Math.pow(core.rps, 0.75) / 1.5;
				int amount = (1 + (int) speed / 4) * assembly.size;
				if(distForward > 0)
					spawnParticle(core, world, center.relative(dir), assembly.size, dir, speed / 20, distForward, amount);
				if(distBackward > 0)
					spawnParticle(core, world, center.relative(dir.getOpposite()), assembly.size, dir.getOpposite(), speed / 20, distBackward, amount);
			}
		}
	}

	/** Upstream's {@code local$spawnParticle}, line for line. */
	static void spawnParticle(MTCoreTE core, ClientLevel world, BlockPos pos, double diameter, Direction direction,
			double speed, int travelDistance, int amount) {
		RandomSource rand = world.random;
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		x -= direction.getStepX() * 0.5;
		z -= direction.getStepZ() * 0.5;
		Direction rotate = direction.getClockWise();
		diameter -= 0.75;
		for(int i = 0; i < amount; i++) {
			double angle = rand.nextDouble() * Math.PI * 2;
			double r = diameter / 2 * rand.nextDouble();
			double lx = x;
			double lz = z;
			Direction ldir = direction;
			double lsp = speed;
			int ldst = travelDistance;
			if(core.turbulence > 0) {
				// randomize speed if we have turbulence
				double turb = core.turbulence / 100;
				lsp = speed * (1 - (rand.nextDouble() * 0.75 + 0.25) * turb);
				if(rand.nextInt(100) < core.turbulence) {
					// at random chance (higher chance with higher turbulence) we randomize the position of steam
					int offset = rand.nextInt(travelDistance + 1);
					ldst = travelDistance - offset;
					lx += direction.getStepX() * offset;
					lz += direction.getStepZ() * offset;
					if(rand.nextBoolean()) {
						// at random chance, we reverse the direction
						ldst = travelDistance - ldst;
						ldir = direction.getOpposite();
					}
				}
			}
			Minecraft.getInstance().particleEngine.add(new ParticleMTSteam(
					world,
					lx + Math.cos(angle) * r * rotate.getStepX(),
					y + Math.sin(angle) * r,
					lz + Math.cos(angle) * r * rotate.getStepZ(),
					ldir.getStepX() * lsp,
					0,
					ldir.getStepZ() * lsp,
					ldst
			));
		}
	}
}
