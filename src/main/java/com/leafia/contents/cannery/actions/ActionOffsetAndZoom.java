package com.leafia.contents.cannery.actions;

import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actions.IJarAction;

/**
 * Moves the camera and zooms it in the same action: the base mod's {@code ActionOffsetBy} and {@code ActionSetZoom} at once.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/actions/ActionOffsetAndZoom.java}, unchanged in logic. The arithmetic is the two
 * actions' own, step for step: each axis and the zoom are divided by {@code time + 1} and <i>added</i> to the camera on every tick of
 * the action, so a timed change moves the camera by the amount given over its duration and an instant one ({@code time == 0}) sets the
 * zoom to it, as {@code ActionSetZoom} does, and puts the offset's last-tick copy level with the offset, as {@code ActionOffsetBy} does.
 *
 * <p>The base mod's own {@code ActionOffsetBy} is ported now (D-2207) and the LFTR topic uses it where upstream does (D-3041); with a
 * zoom of 0 this action is that one for any duration above 0 (the zoom step is 0 and is added), which {@code LeafiaCanneryTests} pins
 * against the base's. This one stays for the places that zoom.
 *
 * @author ntmleafia
 */
public class ActionOffsetAndZoom implements IJarAction {

	int time;
	double motionX;
	double motionY;
	double motionZ;
	double zoom;

	public ActionOffsetAndZoom(double x, double y, double z, double zoom, int time) {
		this.motionX = x / (time + 1);
		this.motionY = y / (time + 1);
		this.motionZ = z / (time + 1);
		this.zoom = zoom / (time + 1);
		this.time = time;
	}

	@Override
	public int getDuration() {
		return this.time;
	}

	@Override
	public void act(WorldInAJar world, JarScene scene) {
		scene.script.offsetX += this.motionX;
		scene.script.offsetY += this.motionY;
		scene.script.offsetZ += this.motionZ;

		if(this.time == 0) {
			scene.script.lastOffsetX = scene.script.offsetX;
			scene.script.lastOffsetY = scene.script.offsetY;
			scene.script.lastOffsetZ = scene.script.offsetZ;
		}

		if(this.getDuration() == 0) {
			scene.script.lastZoom = scene.script.zoom = this.zoom;
		} else {
			scene.script.zoom += this.zoom;
		}
	}
}
