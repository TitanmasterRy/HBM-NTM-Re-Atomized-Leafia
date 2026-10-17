package com.leafia.contents.building.storage.rack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.leafia.contents.machines.processing.solblaster.SolBlasterObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;

/**
 * The rack's OBJ, read by hand for the same reason the Neutron Blaster's is (D-1087): its two pallets' texture
 * coordinates run to 2.625 (a spruce plank tiled across the board), which a model baked against the block
 * atlas cannot wrap. {@link RackRender} draws the five objects from their own geometry on standalone textures;
 * the item's baked model (the shelf, without the pallets) is made from the same file.
 *
 * <p>The parser is the Neutron Blaster's, which keeps every coordinate as written; the file is the one
 * upstream draws with, {@code decoration/sellacity/storagerack/rackfinal.obj}, with a material line after each
 * object so that NeoForge bakes the item (the parser ignores them).
 */
public final class RackObj {

	private RackObj() {}

	public static final String RESOURCE = "/assets/leafia/models/blocks/sellacity_rack.obj";

	private static Map<String, Mesh> meshes;

	public static synchronized Map<String, Mesh> meshes() {
		if(meshes == null) {
			try(InputStream in = RackObj.class.getResourceAsStream(RESOURCE)) {
				if(in == null) throw new IllegalStateException("missing " + RESOURCE);
				meshes = SolBlasterObj.parse(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + RESOURCE, e);
			}
		}
		return meshes;
	}
}
