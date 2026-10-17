package com.leafia.contents.machines.misc.modular_turbine.flywheel;

import com.hbm.particle.helper.IParticleCreator;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineBlockBase;
import com.leafia.contents.machines.misc.modular_turbine.ModularTurbineComponentTE;
import com.leafia.dev.math.FiaMatrix;
import com.leafia.init.LeafiaDamageSources;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A flywheel's block entity: it carries anything standing in the disc round with the shaft, and
 * past 8 rps it grinds it.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/misc/modular_turbine/flywheel/MTFlywheelTE.java}.
 *
 * <h2>What it does each tick</h2>
 *
 * While the shaft turns at all ({@code rps >= 0.005}) every entity whose position lies inside the
 * disc — measured in the disc's plane, with a quarter-block and half the entity's width of grace —
 * is rotated about the shaft by the angle the shaft turned this tick, the same angle the renderer
 * uses. It is <b>ground</b> instead if that would put it inside a solid block, or put a living
 * thing's head inside a suffocating one, or if the shaft is past 8 rps whatever is in the way.
 * Grinding a living thing deals {@code sqrt(weight) / 2 × rps} of the {@code flywheel} damage
 * type, which bypasses armour, resistance and enchantments; one that dies is removed on the spot
 * with a wooden-door crunch. Players in creative or spectator are left alone.
 *
 * <p>The client runs the same code for its <em>own</em> player only, because a client owns its
 * player's movement and a server-side nudge alone would be rubber-banded straight back. That half
 * lives in {@link MTFlywheelClient} so this class never links a client-only type.
 *
 * <h2>What waits</h2>
 *
 * <ul>
 * <li><b>Shredding items.</b> Upstream turns a dropped item into its {@code ShredderRecipes} dust
 *     (if the hit is at least 5 and the result is not plain dust). {@code ShredderRecipes} is not
 *     ported in the base mod — the deferral D-1056 recorded — so items are carried round and
 *     otherwise left alone.</li>
 * <li><b>Giblets.</b> The {@code "giblets"} burst is sent as upstream sends it, and the base mod's
 *     client does not draw that type yet (Slice6 explains why), so the death is silent apart
 *     from the sound.</li>
 * </ul>
 */
public class MTFlywheelTE extends ModularTurbineComponentTE {

	/** Past this, everything in the disc is ground, whether or not anything is in its way. */
	public static final double GRIND_RPS = 8;

	public MTFlywheelTE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Upstream's damage for a turbine of this weight at this speed. */
	public static double damageFor(double weight, double rps) {
		return Math.max(0, Math.pow(weight, 0.5) / 2 * (rps - 0/*.5*/));
	}

	public void grindEntity(Entity entity, double damage) {
		if(entity instanceof Player player) {
			if(player.isCreative() || player.isSpectator())
				return;
		}
		if(getBlockState().getBlock() instanceof ModularTurbineBlockBase base) {
			Direction facing = getAssemblyFacing();
			BlockPos shaft = worldPosition.above(base.shaftHeight());
			Vec3 s = Vec3.atCenterOf(shaft);
			FiaMatrix mat = new FiaMatrix(s, s.add(Vec3.atLowerCornerOf(facing.getNormal())));
			FiaMatrix offset = mat.toObjectSpace(new FiaMatrix(entity.position()));
			double mul = -1;
			if(facing.equals(Direction.SOUTH) || facing.equals(Direction.WEST))
				mul *= -1;
			mat = mat.rotateZ(core.getVisualDeltaAngle() * mul);
			FiaMatrix newPos = mat.travel(offset);

			BlockPos bp = BlockPos.containing(newPos.position);
			BlockPos headPos = BlockPos.containing(newPos.position.add(0, entity.getEyeHeight(), 0));
			boolean grind = false;
			BlockState at = level.getBlockState(bp);
			if(!(at.getBlock() instanceof ModularTurbineBlockBase)) {
				if(at.isSolid())
					grind = true;
			} else if(entity instanceof LivingEntity && level.getBlockState(headPos).isSuffocating(level, headPos))
				grind = true;

			if(!grind)
				entity.setPos(newPos.getX(), newPos.getY(), newPos.getZ());

			if(core.rps > GRIND_RPS)
				grind = true;
			if(!grind) return;

			if(!level.isClientSide && damage > 0) {
				if(entity instanceof LivingEntity living) {
					living.hurt(LeafiaDamageSources.of(level, LeafiaDamageSources.flywheel), (float) damage);
					if(!living.isAlive()) {
						// stolen code below
						CompoundTag vdat = new CompoundTag();
						vdat.putString("type", "giblets");
						vdat.putInt("ent", living.getId());
						vdat.putInt("cDiv", 5);
						IParticleCreator.sendPacket(level, living.getX(), living.getY() + living.getBbHeight() * 0.5,
								living.getZ(), 150, vdat);
						level.playSound(null, living.getX(), living.getY(), living.getZ(),
								SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 2.0F,
								0.95F + level.random.nextFloat() * 0.2F);
						living.discard();
					}
				} else if(entity instanceof ItemEntity && damage >= 5) {
					// Upstream shreds the item through ShredderRecipes here. Not ported in the
					// base mod - see the class doc.
				}
			}
		}
	}

	/** Upstream's {@code update()}. Runs on both sides. */
	public void updateEntity() {
		if(getBlockState().getBlock() instanceof ModularTurbineBlockBase base) {
			int hurtDiameter = base.size() / 2 * 2 + 1;
			if(core != null && core.rps >= 0.005) {
				double damage = damageFor(core.weight, core.rps);
				Direction facing = getAssemblyFacing();
				Direction side = facing.getClockWise();
				int dx = Math.abs(side.getStepX());
				int dz = Math.abs(side.getStepZ());
				int dfx = Math.abs(facing.getStepX());
				int dfz = Math.abs(facing.getStepZ());
				BlockPos shaft = worldPosition.above(base.shaftHeight());
				double hurtRadius = hurtDiameter / 2d + 1;
				AABB aabb = new AABB(
						shaft.getX() + 0.5 - hurtRadius * dx - dfx / 2d,
						shaft.getY() + 0.5 - hurtRadius,
						shaft.getZ() + 0.5 - hurtRadius * dz - dfz / 2d,
						shaft.getX() + 0.5 + hurtRadius * dx + dfx / 2d,
						shaft.getY() + 0.5 + hurtRadius,
						shaft.getZ() + 0.5 + hurtRadius * dz + dfz / 2d
				);
				for(Entity entity : level.getEntities((Entity) null, aabb)) {
					Vec3 p = entity.position();
					Vec3 s = Vec3.atCenterOf(shaft);
					if(facing.getAxis() == Axis.X)
						p = new Vec3(s.x, p.y, p.z);
					else if(facing.getAxis() == Axis.Z)
						p = new Vec3(p.x, p.y, s.z);
					double threshold = hurtRadius - 1 + 0.25 + entity.getBbWidth() / 2;
					if(s.distanceTo(p) < threshold || s.distanceTo(p.add(0, entity.getBbHeight(), 0)) < threshold) {
						if(level.isClientSide)
							MTFlywheelClient.grindIfLocalPlayer(this, entity, damage);
						else
							grindEntity(entity, damage);
					}
				}
			}
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MTFlywheelTE flywheel) {
		flywheel.updateEntity();
	}
}
