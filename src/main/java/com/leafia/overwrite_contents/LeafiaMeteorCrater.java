package com.leafia.overwrite_contents;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockSellafieldSlaked;
import com.hbm.particle.helper.ExplosionCreator;
import com.leafia.dev.LeafiaUtil;
import com.leafia.dev.optimization.LeafiaParticlePacket.SmokeShockwaveParticle;
import com.leafia.passive.effects.LeafiaShakecam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The addon's meteor crater and its blast effect (D-3022): the two statics of upstream's
 * {@code com/leafia/contents/debug/explosion_test/DebugBoomBlock.java} that {@code MixinEntityMeteor} calls. The block
 * itself is a dev-only test bomb (LEAFIA_COMPLETION: D, not ported); these two are what a meteor needs of it, so they
 * live here, with the meteor hook.
 *
 * <h2>The crater</h2>
 *
 * A bowl of radius {@value #RADIUS} around {@code pos}, each column dug from the surface down
 * {@code ceil(8 x (1 - d/13)) - nextInt(2)} blocks: one in five dug blocks that is drawn and not a liquid is thrown
 * outward as debris; anything dug below {@code pos.y - 8 + 2.4} becomes lava and the rest air, with one in eight of the
 * bottom blocks set alight; and the three solid full cubes under the floor are lined - slaked sellafite of level
 * {@code (int)(max(1 - 1.1 d/13, 0)^0.65 x 8)}, or from level 6 tektite (one in fifty tektite osmiridium). The
 * debris lands on top of whatever column it is thrown to, {@code ((1 - |p - pos|/13 x 0.5)^2 x 13 x 0.95 x rand x 0.5
 * + 6.5)} out along the column's own direction from the centre.
 *
 * <p>Kept from upstream, and worth knowing:
 * <ul>
 * <li><b>the centre column's debris is thrown to the world origin column.</b> Its direction from the centre is
 *     {@code 0 / 0}, and 1.12.2's {@code BlockPos.add(NaN, 0, NaN)} floors to x = 0, z = 0, as 1.21.1's
 *     {@code Mth.floor} does. So about one and a half blocks per meteor land on top of column (0, 0). Flagged in D-3022
 *     for a human; the overhaul that calls this is off by default;</li>
 * <li>{@code distanceSq} is between block corners and includes the height difference, so debris from deep in a
 *     column travels less far;</li>
 * <li>"the surface" is 1.12.2's {@code getHeight(x, z)}, which is {@code MOTION_BLOCKING} here (D-843), less one; and
 *     upstream's {@code y <= 0} floor is the level's.</li>
 * </ul>
 */
public final class LeafiaMeteorCrater {

	private LeafiaMeteorCrater() {}

	public static final int RADIUS = 13;
	public static final int MAX_DEPTH = 8;

	/** Upstream's {@code DebugBoomBlock.createMeteorCrater}. */
	public static void createMeteorCrater(Level world, BlockPos pos) {
		int cx = pos.getX();
		int cz = pos.getZ();
		List<BlockPos> debrisAt = new ArrayList<>();
		List<BlockState> debrisState = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int floor = world.getMinBuildHeight();
		for(int ox = -RADIUS; ox <= RADIUS; ox++) {
			for(int oz = -RADIUS; oz <= RADIUS; oz++) {
				double horizontalDistance = Math.sqrt(ox * ox + oz * oz);
				double ratio = horizontalDistance / RADIUS;
				double depthRatio = Math.max(1 - ratio, 0);
				int depth = (int) Math.ceil(MAX_DEPTH * depthRatio) - world.random.nextInt(2);
				if(depth > 0) {
					int my = world.getHeight(Heightmap.Types.MOTION_BLOCKING, cx + ox, cz + oz) - 1;
					for(int y = my; y > my - depth; y--) {
						p.set(cx + ox, y, cz + oz);
						if(y <= floor) break;
						if(world.random.nextInt(5) == 0) {
							BlockState state = world.getBlockState(p);
							if(state.getRenderShape() != RenderShape.INVISIBLE && !state.liquid()) {
								double moveRatio = 1 - Math.sqrt(p.distSqr(pos)) / RADIUS * 0.5;
								if(moveRatio > 0) {
									double moveAmt = Math.pow(moveRatio, 2) * RADIUS * (0.95 * world.random.nextDouble() * 0.5) + RADIUS / 2d;
									BlockPos newPos = BlockPos.containing(p.getX() + ox / horizontalDistance * moveAmt, p.getY(), p.getZ() + oz / horizontalDistance * moveAmt);
									debrisAt.add(newPos);
									debrisState.add(state);
								}
							}
						}
						if(y < pos.getY() - MAX_DEPTH + MAX_DEPTH * 0.3) {
							world.setBlock(p, Blocks.LAVA.defaultBlockState(), 3);
						} else {
							world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
							if(y == my - depth + 1) {
								if(world.random.nextInt(8) == 0) world.setBlock(p, Blocks.FIRE.defaultBlockState(), 3);
							}
						}
					}
					for(int y = my - depth; y > my - depth - 3; y--) {
						p.set(cx + ox, y, cz + oz);
						if(y <= floor) break;
						if(LeafiaUtil.isSolidVisibleCube(world.getBlockState(p), world, p)) {
							int shade = shade(ratio);
							if(shade >= 6) {
								if(world.random.nextInt(50) == 0) world.setBlock(p, ModBlocks.ore_tektite_osmiridium.get().defaultBlockState(), 3);
								else world.setBlock(p, ModBlocks.tektite.get().defaultBlockState(), 3);
							} else {
								world.setBlock(p, ModBlocks.sellafield_slaked.get().defaultBlockState().setValue(BlockSellafieldSlaked.LEVEL, shade), 3);
							}
						}
					}
				}
			}
		}
		for(int i = 0; i < debrisAt.size(); i++)
			world.setBlock(world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, debrisAt.get(i)), debrisState.get(i), 3);
	}

	/** The lining's level at {@code ratio = d / 13}: {@code (int)(max(1 - 1.1 ratio, 0)^0.65 x 8)}. */
	public static int shade(double ratio) {
		return (int) (Math.pow(Math.max(1 - ratio * 1.1, 0), 0.65) * 8);
	}

	/** How deep the column {@code d} out is dug before the {@code nextInt(2)} roll: {@code ceil(8 x max(1 - d/13, 0))}. */
	public static int depth(double horizontalDistance) {
		return (int) Math.ceil(MAX_DEPTH * Math.max(1 - horizontalDistance / RADIUS, 0));
	}

	/**
	 * Upstream's {@code DebugBoomBlock.createMeteorExplosionEffect}: a smoke shockwave seen to 500, three of the base
	 * mod's composed explosion effects, and two camera shakes for everyone within 300.
	 */
	public static void createMeteorExplosionEffect(Level world, double x, double y, double z) {
		if(!(world instanceof ServerLevel level)) return;
		new SmokeShockwaveParticle().emit(level, new Vec3(x, y, z), new Vec3(0, 1, 0), 500);
		ExplosionCreator.composeEffect(world, x, y, z, 10, 2F, 0.5F, 0F, 5, 16, 50, 0.5F, 3F, -2F, 0);
		ExplosionCreator.composeEffect(world, x, y, z, 10, 2F, 0.5F, 0, 12, 8, 50, 1F, 3F, -2F, 0);
		ExplosionCreator.composeEffect(world, x, y, z, 10, 2F, 0.5F, 0, 16, 4, 50, 1.2F, 3F, -2F, 0);
		BlockPos at = BlockPos.containing(x, y, z);
		LeafiaShakecam.sendNear(level, at, 300, "type=smooth", "preset=RUPTURE", "blurDulling*2", "duration/2", "speed/2", "intensity*1.5", "range=250");
		LeafiaShakecam.sendNear(level, at, 300, "type=simple", "preset=RUPTURE", "blurDulling*4", "intensity*1.5", "range=250");
	}
}
