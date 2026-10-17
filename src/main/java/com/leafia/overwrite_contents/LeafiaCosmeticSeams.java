package com.leafia.overwrite_contents;

import java.awt.Color;

import com.hbm.blocks.generic.BlockSellafieldSlaked;
import com.hbm.inventory.gui.GuiInfoText;
import com.hbm.items.tool.ItemDiscord;
import com.hbm.util.BobMathUtil;
import com.leafia.config.LeafiaConfig;
import com.leafia.config.LeafiaFeatures;
import com.leafia.unsorted.BullshitInDavenportIowa;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where the addon plugs into the base mod's cosmetic seams that need no client class (D-2199), all at
 * once, from the mod constructor. Not an upstream file: upstream reached each of these with a mixin into
 * {@code com.hbm}, and D-1001 makes every one a seam consumer instead (D-3023). The client half -
 * the diamond's numbers, the cooling tower's wind, the gas flame's glow - is {@code LeafiaCosmeticClient}.
 *
 * <table>
 * <tr><th>upstream</th><th>here</th><th>switch</th></tr>
 * <tr><td>{@code BullshitMixinGuiInfoContainer}</td><td>{@link #ELECTRICITY}, an electricity formatter</td><td>sludge units ({@code enableSludgeUnits})</td></tr>
 * <tr><td>{@code MixinBlockSellafieldSlaked}</td><td>{@link #SLAKED_TINT}, a tint function</td><td>cosmeticOverhaul</td></tr>
 * <tr><td>{@code MixinItemDiscord}</td><td>{@link #CREATIVE_RAY}, a ray provider</td><td>cosmeticOverhaul</td></tr>
 * </table>
 *
 * Each is registered whatever its switch says and reads the switch when it runs, so an off switch hands the
 * base mod its own behaviour back. (The hot sellafield colours of {@code MixinBlockSellafield} are not a
 * seam at all: they are the recoloured textures of a resource pack, see {@code LeafiaCosmeticClient}.)
 */
public final class LeafiaCosmeticSeams {

	private LeafiaCosmeticSeams() {}

	private static boolean installed = false;

	/** Registers every handler, once. Called from the mod constructor. */
	public static void install() {
		if(installed) return;
		installed = true;
		GuiInfoText.addElectricityFormatter(ELECTRICITY);
		BlockSellafieldSlaked.addTintFunction(SLAKED_TINT);
		ItemDiscord.addRayProvider(CREATIVE_RAY);
	}

	// ------------------------------------------------------------------ BullshitMixinGuiInfoContainer

	/**
	 * The energy bar's tooltip in the addon's sludge units: HE become corn, {@code 15 per HE}
	 * ({@link BullshitInDavenportIowa#HEToCn}), short-formatted, "corn" in place of "HE". Declines
	 * (hands the line back to the base mod) while {@link LeafiaConfig#bullshitUnits} is off.
	 */
	public static final GuiInfoText.ElectricityFormatter ELECTRICITY = (power, maxPower) -> {
		if(!LeafiaConfig.bullshitUnits) return null;
		return electricityText(power, maxPower);
	};

	/** Upstream's line: {@code short(corn(power)) + "/" + short(corn(max)) + "corn"}. */
	public static String electricityText(long power, long maxPower) {
		return BobMathUtil.getShortNumber((long) BullshitInDavenportIowa.HEToCn(power)) + "/"
				+ BobMathUtil.getShortNumber((long) BullshitInDavenportIowa.HEToCn(maxPower)) + "corn";
	}

	// ------------------------------------------------------------------ MixinBlockSellafieldSlaked

	/**
	 * Slaked sellafield, darker: {@code HSBtoRGB(0, 0, 1 - (min(level, 9) / 11)^0.75)}, where the base mod's
	 * is {@code 1 - level / 15}. The brightness stops falling at level 9 (0.46 of white, never black), so
	 * levels 9 to 15 look alike, and the lightest is white as before.
	 */
	public static final BlockSellafieldSlaked.TintFunction SLAKED_TINT = level -> {
		if(!LeafiaFeatures.cosmeticOverhaul) return null;
		return slakedTint(level);
	};

	public static int slakedTint(int level) {
		return Color.HSBtoRGB(0F, 0F, 1F - (float) Math.pow(Math.min(level, 9) / 11F, 0.75));
	}

	// ------------------------------------------------------------------ MixinItemDiscord

	/** Upstream's creative range, 500 blocks, against the rod's usual 100. */
	public static final double CREATIVE_RANGE = 500;

	/**
	 * The rod of discord in creative: a 500-block look instead of 100. Upstream asks the <em>world's</em>
	 * game type and starts the ray at the player's feet; here it asks the player's own mode (1.21.1 has no
	 * world-wide game type a client can read, and in a creative world the two agree) and keeps the feet.
	 * The ray ignores fluids and hits a block's outline, as {@code rayTraceBlocks(..., false, false, true)}
	 * did; a miss is a {@code MISS} result, which the rod passes on, as upstream's.
	 */
	public static final ItemDiscord.RayProvider CREATIVE_RAY = (player, range) -> {
		if(!LeafiaFeatures.cosmeticOverhaul || !player.isCreative()) return null;
		return creativeRay(player);
	};

	public static Vec3 creativeRayEnd(Player player) {
		return player.position().add(player.getLookAngle().scale(CREATIVE_RANGE));
	}

	public static BlockHitResult creativeRay(Player player) {
		return player.level().clip(new ClipContext(player.position(), creativeRayEnd(player), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	// ------------------------------------------------------------------ MixinParticleCoolingTower

	/**
	 * The push the addon's wind gives a cooling-tower puff in one tick: {@code 0.0025 x (power / 100)^0.65 x
	 * multiplier} along the compass angle (degrees; +Z at 0, -X at 90), or nothing where the multiplier is
	 * not positive. Upstream's body of the redirect, with the client particle taken out so it can be pinned.
	 */
	public static double[] windPush(double power, double angleDegrees, double multiplier) {
		if(!(multiplier > 0)) return new double[] {0, 0, 0};
		double acceleration = 0.0025 * Math.pow(power / 100, 0.65) * multiplier;
		return new double[] {-Math.sin(angleDegrees / 180 * Math.PI) * acceleration, 0, Math.cos(angleDegrees / 180 * Math.PI) * acceleration};
	}

	// ------------------------------------------------------------------ MixinDiamondPronter

	/** Upstream's digit count for the hazard diamond's wide numbers: {@code value > 0 ? (int) log10(value) + 1 : 1}. */
	public static int digits(int value) {
		return value > 0 ? (int) Math.log10(value) + 1 : 1;
	}
}
