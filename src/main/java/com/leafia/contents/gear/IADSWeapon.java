package com.leafia.contents.gear;

/**
 * An item the holder can aim down the sights of: while sneaking with it in one hand and nothing in the other, the
 * view narrows to {@link #getADS()} of its width.
 *
 * <p>Upstream: {@code com.leafia.contents.gear.IADSWeapon} ("ancient crap that added before 1.12.2 got the gun
 * rework"), read by {@code LeafiaClientListener.HandlerClient.getViewADS} and its {@code FOVUpdateEvent}. Its one
 * implementor upstream is the laser detonator, through {@code MixinItemLaserDetonator}; that is a base-mod item here,
 * which cannot implement an addon interface, so {@code LeafiaLaserDetonator} answers for it (D-3027). The
 * {@code @SideOnly(CLIENT)} is dropped (D-003): the method is only called on the client.
 */
public interface IADSWeapon {

	/** The field-of-view multiplier while aiming; 1 is "no sights". */
	default float getADS() {
		return 1;
	}
}
