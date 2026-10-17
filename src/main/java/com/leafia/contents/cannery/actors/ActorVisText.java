package com.leafia.contents.cannery.actors;

import com.hbm.wiaj.JarScript;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Lines of text in a cell that always face the viewer: the flux fractions of the PWR topic, and the test topic's label.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/actors/ActorVisText.java}. The constructors are upstream's: one that leaves the font
 * as it is, and one that forces it to the unicode font ({@code true}) or the ordinary one ({@code false}); the PWR topic forces the
 * ordinary one. The drawing is {@link VisActorRenders#text}.
 */
public class ActorVisText extends ActorVisBase {

	final String[] texts;
	final float scale;
	/** {@code -1} leaves the font alone, {@code 1} forces the unicode font and {@code 0} the ordinary one: upstream's three values. */
	final int forceUnicode;

	public ActorVisText(JarScript script, int x, int y, int z, int color, float scale, String... texts) {
		super(script, x, y, z, color);
		this.forceUnicode = -1;
		this.texts = texts;
		this.scale = scale;
	}

	public ActorVisText(JarScript script, int x, int y, int z, int color, float scale, boolean setUnicode, String... texts) {
		super(script, x, y, z, color);
		this.texts = texts;
		this.forceUnicode = setUnicode ? 1 : 0;
		this.scale = scale;
	}

	public String[] texts() { return texts.clone(); }

	public float scale() { return scale; }

	/** {@code -1} leaves the font alone, {@code 1} forces the unicode font and {@code 0} the ordinary one. */
	public int forceUnicode() { return forceUnicode; }

	@Override
	public void renderContents(PoseStack pose, MultiBufferSource buffers) {
		VisActorRenders.text(pose, buffers, script, color, scale, forceUnicode, texts);
	}
}
