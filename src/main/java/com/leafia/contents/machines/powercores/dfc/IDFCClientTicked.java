package com.leafia.contents.machines.powercores.dfc;

/**
 * A DFC part with work to do on the client every tick: a looping sound, the beam the renderer reads,
 * the receiver's rolling average.
 *
 * <p>Not upstream's: upstream's mixed-in {@code update()} ran on both sides, and here the base mod's
 * {@code CoreComponent} gives no ticker on a client. Each implementor registers itself with
 * {@code LeafiaDFCClient.track} in {@code onLoad} on the client and is called from the end of every
 * unpaused client tick until it is removed - the mechanism the core and the detonator already use
 * (D-3011). A server never calls this.
 */
public interface IDFCClientTicked {

	/** Upstream's {@code world.isRemote} half of {@code update()}. */
	void leafia$clientTick();
}
