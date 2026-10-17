package com.leafia.contents.building.pinkdoor;

import com.hbm.blocks.ModBlocks;
import com.hbm.entity.projectile.EntityBoxcar;
import com.hbm.entity.projectile.EntityBulletBaseMK4;
import com.hbm.entity.projectile.EntityFallingNuke;
import com.hbm.entity.projectile.EntityRBMKDebris;
import com.hbm.entity.projectile.EntityRBMKDebris.DebrisType;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.interfaces.IBomb;
import com.hbm.items.weapon.sedna.factory.XFactoryCatapult;
import com.hbm.sound.NtmSounds;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.optimization.LeafiaParticlePacket.PinkRBMK;
import com.leafia.passive.effects.LeafiaShakecam.ShakecamPacket;

import com.leafia.contents.effects.zomg.LeafiaZomg;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The pink door, {@code door_fuckoff} (D-3028): a wooden door - it opens by hand and on redstone, with the wooden door's sounds - that
 * is also a bomb. Any detonator ({@code IBomb}) removes it and lets go of everything upstream's {@code explode} does. "Why. Just why.
 * This is brutal."
 *
 * <p>Ported from {@code com.leafia.contents.building.pinkdoor.BlockPinkDoor}. Upstream extends the base's {@code BlockModDoor} and then
 * overrides every behaviour of it back to 1.12.2's wooden {@code BlockDoor}; here it is a vanilla {@link DoorBlock} on
 * {@link BlockSetType#OAK}, which is that door. One override is kept, because it is not vanilla's: the hand click plays the
 * <b>open</b> sound when the clicked lower half was open (that is, when it closes) and the close sound when it was shut, and always
 * the close sound from the upper half, whose 1.12.2 metadata carried no {@code OPEN}. Kept as upstream, not fixed.
 *
 * <p>{@link #explode}, in upstream's order:
 * <ol>
 * <li>the door is removed and every player within 350 blocks gets the {@code RUPTURE} camera shake, at half duration, four times the
 *     blur dulling, twice the blur exponent and one and a half times the speed, over 300 blocks;</li>
 * <li>a power-15 explosion that breaks blocks, at the door's centre;</li>
 * <li>the pink RBMK mushroom one block down, and the RBMK explosion sound at volume 30;</li>
 * <li>44 RBMK debris: 15 blanks, 10 graphite, 4 lids, 6 fuel and 9 rods, each thrown as a melting RBMK throws it (a lid half as far
 *     sideways and half a block a tick faster upwards);</li>
 * <li>a shrapnel shower of 35 straight up at 0.2 deviation, and 8 loose shrapnels - at {@code (z, y, z)}: upstream passes the
 *     door's z where the x goes, and that is kept (D-3028);</li>
 * <li>25 falling corium blocks scattered over a 201-block square between y 455 and 504;</li>
 * <li>two boxcars dropped from y 350 (iterations 2 and 3 of nineteen) and seventeen catapult nukes ({@code nuke_high}, 100 damage)
 *     at 0.15 a tick straight down from 50 + 15i above the ground, scattered over -10 to +30 blocks on each axis from the door
 *     (upstream's {@code (rand * 2 - 0.5) * 20}) - the ground height read at {@code (x, the door's y)}, upstream's argument swap, kept;</li>
 * <li>a falling bomb with a hydrogen yield of 174 and a dirty yield of 50, 80 blocks above the ground over the door.</li>
 * </ol>
 *
 * <p>{@code ExplosionChaos.zomg(world, ..., 30, null, null)}, NTM-CE's rainbow-beam volley at every living thing within 30 blocks,
 * which neither this base mod nor {@code hbm-src} has, is the addon's own {@link LeafiaZomg} (D-3031), fired after the shrapnel
 * as upstream orders it. The falling corium is {@code FallingBlockEntity.fall}, which centres a
 * block on its column where 1.12.2 put it at the corner, and whose block-placing half does nothing above the build limit.
 */
public class BlockPinkDoor extends DoorBlock implements IBomb {

	public BlockPinkDoor(Properties properties) {
		super(BlockSetType.OAK, properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		BlockPos base = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		BlockState baseState = pos.equals(base) ? state : level.getBlockState(base);
		if(!baseState.is(this)) return InteractionResult.PASS;

		BlockState toggled = baseState.cycle(OPEN);
		level.setBlock(base, toggled, 10);
		boolean clickedOpen = state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(OPEN);
		level.playSound(player, pos, clickedOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS,
				1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	public static final int DEBRIS_BLANK = 15;
	public static final int DEBRIS_GRAPHITE = 10;
	public static final int DEBRIS_LID = 4;
	public static final int DEBRIS_FUEL = 6;
	public static final int DEBRIS_ROD = 9;
	public static final int CORIUM_BLOCKS = 25;
	public static final int VOLLEY = 19;
	public static final float BLAST_POWER = 15F;
	/** {@code ExplosionChaos.zomg}'s reach: every living thing within 30 blocks of the door (D-3031). */
	public static final double ZOMG_REACH = 30;

	protected void spawnDebris(Level world, BlockPos pos, DebrisType type) { // oh boy
		EntityRBMKDebris debris = new EntityRBMKDebris(world, pos.getX() + 0.5D, pos.getY() + 1, pos.getZ() + 0.5D, type);
		double motionX = world.random.nextGaussian() * 0.25D * 2;
		double motionZ = world.random.nextGaussian() * 0.25D * 2;
		double motionY = 0.5D + world.random.nextDouble() * 1.5D;

		if(type == DebrisType.LID) {
			motionX *= 0.5D;
			motionY += 0.5D;
			motionZ *= 0.5D;
		}

		debris.setDeltaMovement(motionX, motionY, motionZ);
		world.addFreshEntity(debris);
	}

	/** One catapult nuke at the given height above the ground, falling. Upstream's {@code fuckyou}. */
	protected void fuckyou(Level world, BlockPos pos, int height) {
		EntityBulletBaseMK4 nuke = new EntityBulletBaseMK4(world, XFactoryCatapult.nuke_high, 100, 0, 0, 90);
		// upstream: world.getHeight(pos.getX(), pos.getY()) - the door's y where the z goes
		nuke.setPos(pos.getX(), world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getY()) + height, pos.getZ());
		nuke.setDeltaMovement(0, -0.15, 0);
		world.addFreshEntity(nuke);
	}

	@Override
	public BombReturnCode explode(Level world, BlockPos pos) {
		if(!(world instanceof ServerLevel level)) return BombReturnCode.UNDEFINED;
		world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		ShakecamPacket shake = new ShakecamPacket(new String[] {
				"type=smooth",
				"preset=RUPTURE",
				"duration/2",
				"blurDulling*4", "blurExponent*2",
				"speed*1.5", "duration/2",
				"range=" + 300
		}, pos);
		LeafiaCustomPacket built = null;
		Vec3 centre = Vec3.atCenterOf(pos);
		for(ServerPlayer player : level.players()) {
			if(player.distanceToSqr(centre) > 350 * 350) continue;
			if(player.connection == null || !player.connection.hasChannel(LeafiaCustomPacket.TYPE)) continue;
			if(built == null) built = LeafiaCustomPacket.__start(shake);
			PacketDistributor.sendToPlayer(player, built);
		}
		world.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, BLAST_POWER, Level.ExplosionInteraction.TNT);
		new PinkRBMK().emit(level, Vec3.atLowerCornerOf(pos).add(.5, -1, .5), Vec3.ZERO);
		world.playSound(null, pos, NtmSounds.BLOCK_RBMK_EXPLOSION.get(), SoundSource.BLOCKS, 30, 1);
		for(int i = 0; i < DEBRIS_BLANK; i++) spawnDebris(world, pos, DebrisType.BLANK);
		for(int i = 0; i < DEBRIS_GRAPHITE; i++) spawnDebris(world, pos, DebrisType.GRAPHITE);
		for(int i = 0; i < DEBRIS_LID; i++) spawnDebris(world, pos, DebrisType.LID);
		for(int i = 0; i < DEBRIS_FUEL; i++) spawnDebris(world, pos, DebrisType.FUEL);
		for(int i = 0; i < DEBRIS_ROD; i++) spawnDebris(world, pos, DebrisType.ROD);
		ExplosionLarge.spawnShrapnelShower(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 1D, 0, 35, 0.2D);
		ExplosionLarge.spawnShrapnels(world, pos.getZ() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8);
		// ExplosionChaos.zomg(world, x, y, z, 30, null, null): NTM-CE's, registered by the addon (D-3030 #4, D-3031)
		LeafiaZomg.zomg(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ZOMG_REACH, null);
		for(int i = 0; i < CORIUM_BLOCKS; i++) {
			FallingBlockEntity.fall(world, new BlockPos(
					pos.getX() + world.random.nextInt(201) - 100,
					world.random.nextInt(50) + 455,
					pos.getZ() + world.random.nextInt(201) - 100),
					ModBlocks.corium_block.get().defaultBlockState());
		}
		for(int i = 0; i < VOLLEY; i++) {
			if(i == 2 || i == 3) {
				EntityBoxcar pippo = new EntityBoxcar(world);
				pippo.setPos(pos.getX() + world.random.nextGaussian() * 25, 350, pos.getZ() + world.random.nextGaussian() * 25);
				world.addFreshEntity(pippo);
				continue;
			}
			fuckyou(world, BlockPos.containing(pos.getX() + (world.random.nextDouble() * 2 - 0.5) * 20, pos.getY(), pos.getZ() + (world.random.nextDouble() * 2 - 0.5) * 20), 50 + i * 15);
		}
		EntityFallingNuke nuke = new EntityFallingNuke(world, 0, 0, 174, 0, 50, 0, 0);
		nuke.setPos(pos.getX(), world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) + 80, pos.getZ());
		world.addFreshEntity(nuke);
		return BombReturnCode.DETONATED;
	}
}
