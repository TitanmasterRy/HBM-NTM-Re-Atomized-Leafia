package com.leafia.transformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.leafia.client.LeafiaEasterEgg;

import net.minecraft.ChatFormatting;

/**
 * Odds and ends of the client (D-3026): the title screen's wacky splashes.
 *
 * <p>Upstream: {@code com/leafia/transformer/LeafiaGeneralLocal.java}. Three members, one ported:
 *
 * <ul>
 *   <li>{@link #injectWackySplashes}, called from {@code MixinSplashManager} (upstream's {@code MixinGuiMainMenu_WackySplashes});</li>
 *   <li><b>not ported: {@code injectDebugInfoLeft}</b>, two lines for the F3 screen with the addon's packet bytes a second, a minute and
 *       a tick. Upstream's only call site is commented out ({@code //LeafiaGeneralLocal.injectDebugInfoLeft(debug.getLeft());} in
 *       {@code LeafiaClientListener}), the counters it reads are rolled by the same unported listener, and it formats with a library class
 *       ({@code SIPfx}) the addon does not have;</li>
 *   <li><b>not ported: {@code acidRain} and {@code acidRainParticles}</b>, the acid rain's texture and the cloud puffs it spawns for a
 *       {@code HasAcidicRain} biome. They belong to Sellacity, the addon's acid-rain biomes, which are decided not to be ported, and
 *       their only caller is another mixin ({@code MixinEntityRenderer_AcidRain}) in the same bucket.</li>
 * </ul>
 */
public class LeafiaGeneralLocal {

	static Map<String, String> splash = new HashMap<>();
	static {
		//https://www.ibm.com/docs/en/wamt?topic=binaries-jvm-command-line-properties-options
		splash.put("en", "I know where you live, %s.");
		splash.put("es", "Sé dónde vives, %s.");
		splash.put("ja", "お前の住処は知っている、%s。");
		splash.put("ru", "Я знаю, где ты живёшь, %s.");
		splash.put("zh", "我知道你的住处，%s。");
	}

	/** The splash that names the player, in the language the JVM reports; English when upstream has no line for it. */
	public static String knowsWhereYouLive(String language, String user) {
		String spl = splash.get(language);
		if(spl == null) spl = splash.get("en");
		return ChatFormatting.RED + spl.replace("%s", user);
	}

	public static void injectWackySplashes(List<String> splashes) {
		splashes.add("Floppenheimer!");
		splashes.add("i should dip my balls in sulfuric acid");
		splashes.add("All answers are popbob!");
		splashes.add("None may enter The Orb!");
		splashes.add("Wacarb was here");
		splashes.add("SpongeBoy me Bob I am overdosing on keramine agagagagaga");
		splashes.add(knowsWhereYouLive(System.getProperty("user.language"), System.getProperty("user.name")));
		splashes.add("Nice toes, now hand them over.");
		splashes.add("I smell burnt toast!");
		splashes.add("There are bugs under your skin!");
		splashes.add("Fentanyl!");
		splashes.add("Don't do drugs!"); // for legal reasons :)
		splashes.add("Imagine being scared by splash texts!");
		splashes.add("Redditors aren't people!");
		splashes.add("Can someone tell me what corrosive fumes the people on Reddit are huffing so I can avoid those more effectively?");
		// VV lce shit
		//splashes.add("Extra information on F3 debug screen!");
		splashes.add("This addon sends string characters in just 5 bits!");
		splashes.add("Core game community nowadays is really toxic!");
		splashes.add("OWO MEOW, MEOWMEOWMEOW, OWOOWOOWOOWO, MEOWMEOWMEOW, MEOWMEOWMEOW, MEOW MEOW MEOW MEOWMEOWMEOWM");
		if(LeafiaEasterEgg.actuallySeenEasterEgg)
			splashes.clear();
		splashes.add("Remove 1.7.10");
	}
}
