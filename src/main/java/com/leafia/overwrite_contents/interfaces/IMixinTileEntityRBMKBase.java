package com.leafia.overwrite_contents.interfaces;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;

import net.minecraft.nbt.CompoundTag;

/**
 * An RBMK column's damage and lid jump, which upstream mixed into every column as two fields.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityRBMKBase.java}, implemented
 * by {@code MixinTileEntityRBMKBase}. Here the base column keeps no addon field: both values live in the
 * column's {@link BlockEntityRBMKBase#getExtraData() extra tag} (D-2197), which the base mod saves with
 * the column and sends with every sync packet, so the accessors are statics over that tag rather than
 * methods a column implements (D-3018). The two constants are upstream's.
 *
 * <ul>
 * <li>{@link #KEY_DAMAGE} is upstream's own NBT key, {@code leafia_damage}. A key is present only while
 * the value is not zero, so an undamaged reactor carries an empty tag - one byte in its sync packet,
 * as the base mod's.</li>
 * <li>{@link #KEY_JUMP} is how high the lid stands, in blocks. Upstream synced it and did not save it;
 * the extra tag does both, so a lid in the air when the world is saved is still in the air when it
 * loads, and comes down then.</li>
 * </ul>
 */
public interface IMixinTileEntityRBMKBase {

	int dmgIncrement = 2;
	int maxDamage = 30 * 20 * dmgIncrement;

	String KEY_DAMAGE = "leafia_damage";
	String KEY_JUMP = "leafia_jump";

	static int leafia$getDamage(BlockEntityRBMKBase column) {
		return column.getExtraData().getInt(KEY_DAMAGE);
	}

	static void leafia$setDamage(BlockEntityRBMKBase column, int damage) {
		CompoundTag tag = column.getExtraData();
		if(damage == 0) tag.remove(KEY_DAMAGE);
		else tag.putInt(KEY_DAMAGE, damage);
	}

	static double leafia$jumpHeight(BlockEntityRBMKBase column) {
		return column.getExtraData().getDouble(KEY_JUMP);
	}

	static void leafia$setJumpHeight(BlockEntityRBMKBase column, double height) {
		CompoundTag tag = column.getExtraData();
		if(height == 0) tag.remove(KEY_JUMP);
		else tag.putDouble(KEY_JUMP, height);
	}
}
