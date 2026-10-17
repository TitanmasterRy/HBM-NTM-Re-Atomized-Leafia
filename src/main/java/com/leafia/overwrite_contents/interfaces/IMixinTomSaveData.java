package com.leafia.overwrite_contents.interfaces;

import com.hbm.saveddata.TomSaveData;

/**
 * The seismic level beside the Tom impact's dust and fire (D-3022). Upstream's interface, which
 * {@code MixinTomSaveData} implemented with a float saved as {@code seismic}; here the float is a key in the base
 * mod's {@link TomSaveData#getExtraData()} tag (D-2197), under upstream's own name, and these are statics over it.
 *
 * <p>Set to 1 every tick the sky burns and then settled by {@code 1 / 9,600,000} a tick - 400 days - by
 * {@code LeafiaStrikeListener}; while it is above 0 every placed block is checked for structural support.
 */
public interface IMixinTomSaveData {

	String KEY = "seismic";

	static void setSeismic(TomSaveData data, float v) {
		data.getExtraData().putFloat(KEY, v);
	}

	static float getSeismic(TomSaveData data) {
		return data.getExtraData().getFloat(KEY);
	}
}
