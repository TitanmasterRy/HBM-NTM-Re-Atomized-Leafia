package com.leafia.init.hazards.types;

/**
 * Marks the addon's radiation types - {@code Alpha}, {@code Beta}, {@code Gamma},
 * {@code Neutrons}, {@code Radon} and {@code XRay}.
 *
 * <p>Upstream's registration helper scales an entry's level by the material multiplier for
 * exactly the types that are radiation, and asks {@code instanceof HazardTypeRadiation ||
 * instanceof LCERad} to find them; its tooltip mixin does the same to total them up. Nothing in
 * this port reads the marker yet - both readers wait on the ore dictionary and the tooltip hook
 * (D-1078) - but it is the contract, and the tests pin that all six carry it.
 */
public interface LCERad { }
