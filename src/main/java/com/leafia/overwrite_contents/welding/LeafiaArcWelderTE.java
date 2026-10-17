package com.leafia.overwrite_contents.welding;

import java.util.List;

import com.custom_hbm.sound.LCEAudioProxy;
import com.custom_hbm.sound.LCEAudioWrapper;
import com.hbm.blockentity.machine.BlockEntityMachineArcWelder;
import com.hbm.lib.ModDamageSource;
import com.leafia.contents.machines.powercores.dfc.IDFCClientTicked;
import com.leafia.dev.LeafiaUtil;
import com.leafia.init.LeafiaBlockEntities;
import com.leafia.init.LeafiaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The arc welder overhaul: the base mod's welder, with the addon's sparks, flash, smoke and sound, and a hazard -
 * a welder that is wet shocks everything round it while it works.
 *
 * <p>Upstream: {@code MixinTileEntityMachineArcWelder} - a {@code @Redirect} that drops the base welder's particle
 * packet and an injection at the tail of {@code update()} - and {@code MixinMachineArcWelder}, a tooltip line.
 * Here it is a subclass the base {@code MachineArcWelder} makes through {@code BlockEntityOverrides} while
 * {@code overhauls.arcWelderOverhaul} is on (D-1002, off), on the protected constructor M8a added (D-2201).
 *
 * <h2>The server</h2>
 * The base mod's own tick, with its particle packet suppressed through the {@code spawnWorkParticles()} seam D-2206
 * added (until then this class carried a copy of the base tick, D-3027 hand-off 1; D-3031 deleted it). Then
 * upstream's tail:
 * <ul>
 * <li>Every 50 ticks of world time the welder is <b>wet</b> if it is raining at the block two above it or any block
 *     in the 5 x 3 x 5 from its own layer up is water.</li>
 * <li>While it works ({@code progress > 0}) and is wet: 4 electricity damage to every entity in the 4-block box
 *     round its centre, items included, every tick; and one tick in fifty, the lava fire-spread round it in a radius
 *     of 2 ({@link LeafiaUtil#spreadFire}).</li>
 * </ul>
 *
 * <h2>The client</h2>
 * While it works, and for one tick after (upstream's "wait 1 tick before stopping sound to make sure the sound
 * loops"): the arc welder loop with its start and stop sounds at 1.4 volume; a spark of 1 to 3 every other tick on
 * average, a flash on even ticks and on one odd tick in three, and one or two smoke puffs every tick, all from the
 * front edge of the bench 1.25 above it.
 */
public class LeafiaArcWelderTE extends BlockEntityMachineArcWelder implements IDFCClientTicked {

	public static final int WET_CHECK_INTERVAL = 50;
	public static final float SHOCK_RANGE = 2;
	public static final float SHOCK_DAMAGE = 4;
	public static final int FIRE_ODDS = 50;

	public boolean isWet = false;
	LCEAudioWrapper clientSfx = null;
	boolean clientLooptimer = false;

	public LeafiaArcWelderTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state, 8);
	}

	public LeafiaArcWelderTE(BlockPos pos, BlockState state) {
		this(LeafiaBlockEntities.ARC_WELDER.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if(level != null && level.isClientSide) LeafiaWeldingClient.track(this);
	}

	/**
	 * The base mod's tick, then upstream's tail. The base tick is not copied: its particle packet goes through
	 * {@link #spawnWorkParticles}, which this class leaves empty (D-2206, D-3031).
	 */
	@Override
	public void updateEntity() {
		super.updateEntity();
		if(!level.isClientSide) tickHazard();
	}

	/**
	 * Upstream's {@code @Redirect}: the base welder's hadron/tau particle packet is not sent. The client draws the
	 * addon's sparks, flash and smoke instead ({@link #leafia$clientTick}).
	 */
	@Override
	protected void spawnWorkParticles() { }

	/** Whether the welder is wet now: rain two above it, or water in the 5 x 3 x 5 from its layer up. */
	public boolean checkWet() {
		if(level.isRainingAt(worldPosition.above(2)))
			return true;
		for(int xo = -2; xo <= 2; xo++) {
			for(int yo = 0; yo <= 2; yo++) {
				for(int zo = -2; zo <= 2; zo++) {
					BlockState state = level.getBlockState(worldPosition.offset(xo, yo, zo));
					// 1.12.2's Material.WATER: a water block, not a waterlogged one
					if(state.liquid() && state.getFluidState().is(FluidTags.WATER))
						return true;
				}
			}
		}
		return false;
	}

	/** Upstream's tail injection, the server half. */
	public void tickHazard() {
		if(level.getGameTime() % WET_CHECK_INTERVAL == 0)
			isWet = checkWet();
		if(progress > 0 && isWet) {
			float range = SHOCK_RANGE;
			AABB box = new AABB(worldPosition.getX() - range + 0.5, worldPosition.getY() - range + 0.5, worldPosition.getZ() - range + 0.5,
					worldPosition.getX() + range + 0.5, worldPosition.getY() + range + 0.5, worldPosition.getZ() + range + 0.5);
			List<Entity> list = level.getEntities((Entity) null, box);
			for(Entity entity : list)
				entity.hurt(ModDamageSource.of(level, ModDamageSource.electricity), SHOCK_DAMAGE);
			if(level.random.nextInt(FIRE_ODDS) == 0)
				LeafiaUtil.spreadFire(level, worldPosition, 2);
		}
	}

	/** The facing the core was placed with (the base's {@code dir()} is private). */
	private net.minecraft.core.Direction facingDir() {
		return getBlockState().getValue(com.hbm.blocks.BlockDummyable.DIR);
	}

	/** The front edge of the bench, 1.25 up: where upstream's sparks come from. */
	public Vec3 sparkPoint() {
		var dir = facingDir();
		return new Vec3(worldPosition.getX() + 0.5 - dir.getStepX() * 0.5, worldPosition.getY() + 1.25, worldPosition.getZ() + 0.5 - dir.getStepZ() * 0.5);
	}

	@Override
	public void leafia$clientTick() {
		if(progress > 0 || clientLooptimer) {
			if(clientSfx == null) {
				clientSfx = LCEAudioProxy.getLoopedSoundStartStop(level, LeafiaSounds.arc_welder.get(), LeafiaSounds.arc_welder_start.get(),
						LeafiaSounds.arc_welder_stop.get(), SoundSource.BLOCKS, worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f,
						worldPosition.getZ() + 0.5f, 1.4f, 1);
				clientSfx.startSound();
			}
			LeafiaWeldingClient.welderParticles(this);
		} else if(clientSfx != null) {
			clientSfx.stopSound();
			clientSfx = null;
		}
		clientLooptimer = progress > 0;
	}

	private void stopSound() {
		if(clientSfx != null) {
			clientSfx.stopSound();
			clientSfx = null;
		}
	}

	@Override
	public void setRemoved() {
		stopSound();
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		stopSound();
		super.onChunkUnloaded();
	}
}
