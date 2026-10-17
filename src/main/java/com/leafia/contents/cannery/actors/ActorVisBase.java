package com.leafia.contents.cannery.actors;

import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.JarScript;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actors.IDrawableActor;
import com.hbm.wiaj.actors.ISpecialActor;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;

/**
 * An actor drawn inside the jar at one cell, in one colour: the base of the flux frames and the flux numbers of the PWR and LFTR topics.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/actors/ActorVisBase.java}. The base mod splits an actor into {@link ISpecialActor} (the
 * tick, which a script and its tests can run without a client) and {@link IDrawableActor} (the drawing, which the screen asks for), so
 * this implements both; the foreground half draws nothing, as upstream's. The background half is upstream's: move to the cell and let
 * {@link #renderContents} draw there. What upstream did to the GL state around it (reset the effects, enable depth, blend with
 * {@code SRC_ALPHA, ONE}) is the draw's own business here, because a render type carries its state: the frame is drawn on a
 * render type with that blend ({@link VisActorRenders}), and the text, whose glyphs vanilla's font pipeline blends, is not (D-3029).
 */
public abstract class ActorVisBase implements ISpecialActor, IDrawableActor {

	final JarScript script;
	final int x;
	final int y;
	final int z;
	final int color;

	public ActorVisBase(JarScript script, int x, int y, int z, int color) {
		this.script = script;
		this.x = x;
		this.y = y;
		this.z = z;
		this.color = color;
	}

	@Override
	public void drawForegroundComponent(GuiGraphics graphics, int w, int h, int ticks, float interp) { }

	@Override
	public void drawBackgroundComponent(WorldInAJar world, int ticks, float interp, PoseStack pose, MultiBufferSource buffers) {
		pose.pushPose();
		pose.translate(x, y, z);
		renderContents(pose, buffers);
		pose.popPose();
	}

	/** The cell the actor stands in, and its colour as 0xRRGGBB: upstream's constructor arguments, for the tests. */
	public int cellX() { return x; }
	public int cellY() { return y; }
	public int cellZ() { return z; }
	public int color() { return color; }

	/** Draws the actor with the pose already at its cell's corner. */
	public abstract void renderContents(PoseStack pose, MultiBufferSource buffers);

	@Override
	public void updateActor(JarScene scene) { }

	@Override
	public void setActorData(CompoundTag data) { }

	@Override
	public void setDataPoint(String tag, Object o) { }
}
