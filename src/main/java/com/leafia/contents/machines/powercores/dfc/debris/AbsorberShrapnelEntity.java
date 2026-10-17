package com.leafia.contents.machines.powercores.dfc.debris;

import com.hbm.entity.projectile.EntityDebrisBase;
import com.leafia.contents.LeafiaResourceItems;
import com.leafia.init.LeafiaEntities;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of a DFC absorber that was blown apart: the cable, the core, a corner, the front plate or a
 * beam of its frame. It flies, tumbles, lands, and lies there for a day until someone picks it up as the
 * matching {@code dfcsh_*} item.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/powercores/dfc/debris/AbsorberShrapnelEntity.java}, on
 * NTM-CE's {@code EntityDebrisBase} - the base mod's {@link EntityDebrisBase} here (D-2195). Upstream's
 * numbers: a hitbox of 0.45 by 0.3, a life of {@code 24 * 3600 * 20} ticks, five kinds in upstream's order.
 *
 * <h2>The two interface files this dissolves</h2>
 *
 * Upstream's {@code IMixinDebrisBase.leafia$destroysBlocks()} is the addon's hook into CE's base, answered
 * {@code false} here so a shrapnel never smashes what it flies into. The base mod's
 * {@link EntityDebrisBase#destroysBlocks()} is that hook (M2), and its default is already false, so the
 * interface has nothing left to say (D-3014).
 *
 * <h2>NTM-CE's movement</h2>
 *
 * Upstream runs CE's {@code onUpdate}, not hbm-src's: a piece that touches a floor or ceiling does not
 * move sideways that tick and its sideways velocity reverses at 0.75. That is the PWR debris's
 * {@code moveDebris}, read out of the CE jar for D-3010, repeated here because the two are siblings on
 * the same CE base.
 */
public class AbsorberShrapnelEntity extends EntityDebrisBase {

	/** Upstream's {@code getLifetime}: a day. */
	public static final int LIFETIME = 24 * 3600 * 20;

	public AbsorberShrapnelEntity(EntityType<? extends AbsorberShrapnelEntity> type, Level level) {
		super(type, level);
	}

	public AbsorberShrapnelEntity(Level world, double x, double y, double z, DebrisType type) {
		this(LeafiaEntities.DFC_SHRAPNEL.get(), world);
		this.setPos(x, y, z);
		this.setType(type);
	}

	/** The item this kind becomes when picked up, or empty when the resource items are switched off. */
	public static ItemStack pickupOf(DebrisType type) {
		String name = switch(type) {
			case CABLE -> "dfcsh_cable";
			case CORE -> "dfcsh_core";
			case CORNER -> "dfcsh_corner";
			case FRONT -> "dfcsh_front";
			case BEAM -> "dfcsh_beam";
		};
		var holder = LeafiaResourceItems.<Item>get(name);
		return holder == null ? ItemStack.EMPTY : new ItemStack(holder.get());
	}

	@Override
	public ItemStack getPickup() {
		return pickupOf(getDebrisType());
	}

	/** Upstream's {@code processInitialInteract}: into the inventory if it fits, and never "handled". */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if(!level().isClientSide && !isRemoved()) {
			ItemStack stack = getPickup();
			if(!stack.isEmpty() && player.getInventory().add(stack)) this.discard();
			player.inventoryMenu.broadcastChanges();
		}
		return InteractionResult.PASS;
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.scalable(0.45F, 0.3F);
	}

	@Override
	public int getLifetime() {
		return LIFETIME;
	}

	/** NTM-CE's move (class note), as {@code PWRDebrisEntity.moveDebris}. */
	@Override
	protected void moveDebris() {
		Vec3 motion = getDeltaMovement();
		double mx = motion.x;
		double my = motion.y - GRAVITY;
		double mz = motion.z;
		this.stuckSpeedMultiplier = Vec3.ZERO;

		Vec3 vertical = new Vec3(0, my, 0);
		double allowedY = collideBoundingBox(this, vertical, getBoundingBox(), level(),
				level().getEntityCollisions(this, getBoundingBox().expandTowards(vertical))).y;
		if(allowedY != my) {
			move(MoverType.SELF, vertical);
			mx *= WALL_BOUNCE;
			mz *= WALL_BOUNCE;
			my = 0;
		} else {
			Vec3 wanted = new Vec3(mx, my, mz);
			Vec3 allowed = collideBoundingBox(this, wanted, getBoundingBox(), level(),
					level().getEntityCollisions(this, getBoundingBox().expandTowards(wanted)));
			move(MoverType.SELF, wanted);
			if(wanted.x != allowed.x) mx *= WALL_BOUNCE;
			if(wanted.z != allowed.z) mz *= WALL_BOUNCE;
		}

		this.lastRot = this.rot;
		if(onGround()) {
			mx *= GROUND_FRICTION;
			mz *= GROUND_FRICTION;
			my *= GROUND_BOUNCE;
		} else {
			this.rot += SPIN;
			if(this.rot >= 360F) {
				this.rot -= 360F;
				this.lastRot -= 360F;
			}
		}
		setDeltaMovement(mx, my, mz);
	}

	public void setType(DebrisType type) {
		setTypeId(type.ordinal());
	}

	/** Upstream's {@code getType}, renamed: {@code Entity.getType()} exists (D-2195). */
	public DebrisType getDebrisType() {
		return DebrisType.values()[Math.abs(getTypeId()) % DebrisType.values().length];
	}

	public enum DebrisType { CABLE, CORE, CORNER, FRONT, BEAM }
}
