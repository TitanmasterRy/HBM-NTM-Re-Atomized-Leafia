package com.leafia.contents.bomb.missile.customnuke.entity;

import org.jetbrains.annotations.Nullable;

import com.hbm.blocks.bomb.NukeCustom;
import com.hbm.entity.missile.EntityMissileTier0;
import com.hbm.items.ModItems;
import com.leafia.contents.LeafiaItems;
import com.leafia.init.LeafiaEntities;
import com.leafia.overwrite_contents.interfaces.IMixinEntityMissileBaseNT;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The custom nuke missile in flight (D-3022): a tier 0 missile carrying the eight stage strengths it was launched
 * with, which sets off the base mod's custom nuke where it lands.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/entity/CustomNukeMissileEntity.java}. It ends its
 * own way, through {@link IMixinEntityMissileBaseNT} - always, not only with {@code overhauls.missileOverhaul}:
 * <ul>
 * <li>it bursts in the air over its target when it carries a nuclear stage or at least 75 of TNT (N2-like), and
 *     no schrabidium, solinium or euphemium;</li>
 * <li>shot down, it scatters as debris when it carries 75 of TNT or a nuclear stage, and otherwise sets its warhead
 *     off where it was hit.</li>
 * </ul>
 *
 * <p>The base mod's custom nuke has no solinium stage (NTM-CE added one) and calls balefire "antimatter"; so
 * {@code bale} goes off as the base mod's antimatter stage and {@code sol} is carried, saved and read as upstream has
 * it but goes off as nothing - no base-mod item is a solinium entry, so it is always 0 here anyway.
 */
public class CustomNukeMissileEntity extends EntityMissileTier0 implements IMixinEntityMissileBaseNT {

	/** TNT at or above this bursts in the air (and scatters when shot down) without a nuclear stage. */
	public static final float N2_TNT = 75;

	public float tnt;
	public float nuke;
	public float hydro;
	public float bale;
	public float dirty;
	public float schrab;
	public float sol;
	public float euph;

	public CustomNukeMissileEntity(EntityType<? extends CustomNukeMissileEntity> type, Level level) {
		super(type, level);
	}

	/** Upstream's launching constructor: at the pad, aimed, with the bomb's eight numbers. */
	public static CustomNukeMissileEntity launch(Level level, float x, float y, float z, int tx, int tz,
			float tnt, float nuke, float hydro, float bale, float dirty, float schrab, float sol, float euph) {
		CustomNukeMissileEntity missile = new CustomNukeMissileEntity(LeafiaEntities.CUSTOM_NUKE_MISSILE.get(), level);
		missile.setup(x, y, z, tx, tz);
		missile.tnt = tnt;
		missile.nuke = nuke;
		missile.hydro = hydro;
		missile.bale = bale;
		missile.dirty = dirty;
		missile.schrab = schrab;
		missile.sol = sol;
		missile.euph = euph;
		return missile;
	}

	@Override
	public boolean shouldDetonateInAir() {
		return (nuke > 0 || tnt >= N2_TNT) && schrab <= 0 && sol <= 0 && euph <= 0;
	}

	@Override
	public boolean interactABMissile() {
		if(tnt >= N2_TNT || nuke > 0) return true;
		explodeAB();
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		this.tnt = nbt.getFloat("custom_tnt");
		this.nuke = nbt.getFloat("custom_nuke");
		this.hydro = nbt.getFloat("custom_hydro");
		this.bale = nbt.getFloat("custom_bale");
		this.dirty = nbt.getFloat("custom_dirty");
		this.schrab = nbt.getFloat("custom_schrab");
		this.sol = nbt.getFloat("custom_sol");
		this.euph = nbt.getFloat("custom_euph");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putFloat("custom_tnt", this.tnt);
		nbt.putFloat("custom_nuke", this.nuke);
		nbt.putFloat("custom_hydro", this.hydro);
		nbt.putFloat("custom_bale", this.bale);
		nbt.putFloat("custom_dirty", this.dirty);
		nbt.putFloat("custom_schrab", this.schrab);
		nbt.putFloat("custom_sol", this.sol);
		nbt.putFloat("custom_euph", this.euph);
	}

	@Override
	public ItemStack getMissileItemForInfo() {
		return LeafiaItems.missile_customnuke == null ? ItemStack.EMPTY : new ItemStack(LeafiaItems.missile_customnuke.get());
	}

	@Override
	public void onMissileImpact(@Nullable HitResult mop) {
		NukeCustom.explodeCustom(this.level(), this.getX(), this.getY(), this.getZ(),
				this.tnt, this.nuke, this.hydro, this.bale, this.dirty, this.schrab, this.euph);
	}

	@Override
	public ItemStack getDebrisRareDrop() {
		return new ItemStack(ModItems.circuit_controller_advanced.get(), 3);
	}
}
