package com.leafia.overwrite_contents;

import com.custom_hbm.contents.torex.LCETorex;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.entity.effect.EntityNukeTorex;
import com.leafia.config.LeafiaFeatures;
import com.leafia.savedata.FalloutSavedData;

import net.minecraft.world.level.Level;

/**
 * Where the addon plugs into the base mod's nuke seams (D-2196), all at once, from the mod constructor.
 * Not an upstream file: upstream did each of these with a mixin into {@code com.hbm}, and D-1001 makes
 * every one of them a seam consumer instead.
 *
 * <ul>
 *   <li>the digamma and endothermic fallout variants and the MK5 hook ({@link LeafiaFalloutVariants},
 *       for {@code MixinEntityFalloutRain} and {@code MixinEntityNukeExplosionMK5});</li>
 *   <li>the fallout finish listener ({@link FalloutSavedData#FINISH_LISTENER}, for
 *       {@code MixinEntityFalloutRain}'s {@code setDead} injection);</li>
 *   <li>the torex spawn delegate ({@link #TOREX_DELEGATE}, for {@code MixinEntityNukeTorex}).</li>
 * </ul>
 *
 * Each is registered whatever its switch says and reads the switch when it runs, except the variants,
 * which are not registered with {@code features.digammaCrater} off (an unregistered variant id falls back
 * to the base mod's rain, which is what "off" should mean for a blast that asks for one). The listener
 * and the delegate read switches the server's config holds; a variant is looked up by id on every column,
 * so whether it exists is the switch.
 */
public final class LeafiaNukeSeams {

	private LeafiaNukeSeams() {}

	/** The base mod's {@code statFac} cloud types upstream's mixin redirected: {@code statFac} and {@code statFacBale}. */
	public static final int TYPE_NUKE = 0;
	public static final int TYPE_BALEFIRE = 1;

	/**
	 * {@code MixinEntityNukeTorex}: with {@code useLeafiaTorex} (here {@code features.overhauls.leafiaTorex},
	 * and {@code features.torex} for the cloud to exist at all), {@code statFac} and {@code statFacBale}
	 * raise an {@code LCETorex} of the nuke or balefire palette with the caller's scale, and the base cloud
	 * is not made. Upstream cancels the base call even on a client level, where {@code LCETorex.statFac}
	 * makes nothing; so does this. Any other cloud type is left to the base mod.
	 */
	public static final EntityNukeTorex.SpawnDelegate TOREX_DELEGATE = LeafiaNukeSeams::spawnTorex;

	public static boolean spawnTorex(Level level, double x, double y, double z, float scale, int type) {
		if(!LeafiaFeatures.leafiaTorex || !LeafiaFeatures.torex) return false;
		if(type == TYPE_NUKE) {
			LCETorex.statFac(level, x, y, z, scale);
			return true;
		}
		if(type == TYPE_BALEFIRE) {
			LCETorex.statFacBale(level, x, y, z, scale);
			return true;
		}
		return false;
	}

	private static boolean installed = false;

	public static void install() {
		if(installed) return;
		installed = true;
		LeafiaFalloutVariants.register();
		EntityFalloutRain.addFinishListener(FalloutSavedData.FINISH_LISTENER);
		EntityNukeTorex.addSpawnDelegate(TOREX_DELEGATE);
	}
}
