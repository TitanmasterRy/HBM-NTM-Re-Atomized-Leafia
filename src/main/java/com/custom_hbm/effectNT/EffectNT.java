package com.custom_hbm.effectNT;

import com.hbm.particle.helper.ClientParticleDispatch;
import com.leafia.unsorted.ParticleRedstoneLight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The addon's effect dispatch: the two effect types NTM-CE's {@code effectNT} did not have,
 * {@code "rslight"} and the {@code "lava"} mode of {@code "vanillaExt"}.
 *
 * <p>Upstream: {@code com/custom_hbm/effectNT/EffectNT.java}, a 122-line copy of the base mod's
 * {@code ClientProxy.effectNT} cut down to its own two types. Eleven {@code vanillaExt} modes live
 * in it there, each building a vanilla particle and reaching its protected fields through
 * {@code LCEParticleUtility} (a class planted in {@code net.minecraft.client.particle}). Ten of
 * those are already in the base mod, as {@code VanillaExtCreator} (D-916), with the setters
 * 1.21.1 made public, so <b>this class does not repeat them</b>: it answers the two things the base
 * does not and hands everything else to {@link ClientParticleDispatch}. {@code LCEParticleUtility}
 * is not needed at all, which is why it stays out of the port (D-1075).
 *
 * <p>Nothing here is balance or a number worth a test; what is checked is that the right tag reaches
 * the right branch, and a client is the only place that can see that - D-3000 records the snapshot.
 *
 * <h2>Where it lives</h2>
 *
 * Upstream's package is {@code com.custom_hbm}, and the brief for this batch said two jars cannot
 * share a package (D-1074) and so it must move into {@code com.leafia}. D-1074's finding was about
 * {@code com.hbm}, which both jars have. {@code com.custom_hbm} is the addon's alone - the base mod
 * has no such package, and {@code LCETorex}, the audio wrappers and {@code LCETuple} already live in
 * it - so there is nothing to split and the file keeps its upstream path, which is also what the
 * ledger row says.
 *
 * <h2>The two types</h2>
 *
 * <ul>
 * <li>{@code "rslight"}: a {@link ParticleRedstoneLight}, a glowing dust mote. {@code scale} of
 *     zero means one (upstream's guard, so a sender that never set it still draws something),
 *     the {@code m} keys are its motion and {@code red/green/blue} its colour.</li>
 * <li>{@code "vanillaExt"}: the base mod's creator for {@code flame}, {@code smoke},
 *     {@code volcano}, {@code cloud}, {@code reddust}, {@code bluedust}, {@code greendust},
 *     {@code largeexplode}, {@code townaura} and {@code blockdust}, and here {@code lava}. Vanilla's
 *     lava particle ignores the speed it is given in both versions, so the motion is passed through
 *     and does nothing.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class EffectNT {

	public static final String RSLIGHT = "rslight";
	public static final String VANILLA_EXT = "vanillaExt";
	public static final String MODE_LAVA = "lava";

	public static void effectNT(CompoundTag data) {

		ClientLevel world = Minecraft.getInstance().level;
		if(world == null)
			return;

		String type = data.getString("type");
		double x = data.getDouble("posX");
		double y = data.getDouble("posY");
		double z = data.getDouble("posZ");

		if(RSLIGHT.equals(type)) {
			float size = data.getFloat("scale");
			ParticleRedstoneLight fx = new ParticleRedstoneLight(
					world, x, y, z, (size == 0) ? 1 : size,
					data.getDouble("mX"),
					data.getDouble("mY"),
					data.getDouble("mZ"),
					data.getFloat("red"),
					data.getFloat("green"),
					data.getFloat("blue"));
			Minecraft.getInstance().particleEngine.add(fx);
			return;
		}

		if(VANILLA_EXT.equals(type)) {

			if(MODE_LAVA.equals(data.getString("mode"))) {
				world.addParticle(ParticleTypes.LAVA, x, y, z,
						data.getDouble("mX"), data.getDouble("mY"), data.getDouble("mZ"));
				return;
			}

			ClientParticleDispatch.dispatch(data);
		}
	}
}
