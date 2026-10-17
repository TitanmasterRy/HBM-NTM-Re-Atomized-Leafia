package com.leafia.overwrite_contents.interfaces;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.RBMKColumn;

/**
 * The console's "this rod is shut off" mark on one automatic control rod's cell.
 *
 * <p>Upstream: {@code com/leafia/overwrite_contents/interfaces/IMixinRBMKColumn$ControlColumn.java},
 * implemented by {@code MixinRBMKColumn$ControlColumn} as a field written into NTM-CE's column packet. The
 * base mod's console scan has no per-type column class; a column is a type and a tag, and anything the
 * {@code addConsoleData} hook puts in the tag reaches the screen (D-2197). So the mark is a key in that tag,
 * present only while true, and the accessors are statics (D-3018). The class name keeps upstream's
 * {@code $}, which is a legal Java identifier character.
 */
public interface IMixinRBMKColumn$ControlColumn {

	String KEY_EXCLAMATION = "leafia_exclamation";

	static boolean leafia$getExclamation(RBMKColumn column) {
		return column.data != null && column.data.getBoolean(KEY_EXCLAMATION);
	}

	static void leafia$setExclamation(RBMKColumn column, boolean exclamation) {
		if(exclamation) column.data.putBoolean(KEY_EXCLAMATION, true);
		else column.data.remove(KEY_EXCLAMATION);
	}
}
