package com.leafia.overwrite_contents.interfaces;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlAuto;

/**
 * Whether an automatic control rod has been shut off by an AZ-5 and is waiting to be acknowledged.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinTileEntityRBMKControlAuto.java},
 * implemented by {@code MixinTileEntityRBMKControlAuto} as a field it saved as {@code leafia_scrammed} and
 * synced. Here it is that key in the rod's {@code getExtraData()} tag (D-2197), which the base mod saves
 * and syncs, so the accessors are statics (D-3018). Present only while true.
 */
public interface IMixinTileEntityRBMKControlAuto {

	String KEY_SCRAMMED = "leafia_scrammed";

	static boolean leafia$getScrammed(BlockEntityRBMKControlAuto rod) {
		return rod.getExtraData().getBoolean(KEY_SCRAMMED);
	}

	static void leafia$setScrammed(BlockEntityRBMKControlAuto rod, boolean scrammed) {
		if(scrammed) rod.getExtraData().putBoolean(KEY_SCRAMMED, true);
		else rod.getExtraData().remove(KEY_SCRAMMED);
	}
}
