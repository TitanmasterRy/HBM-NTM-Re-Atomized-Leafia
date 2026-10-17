package com.leafia.contents.machines.powercores.dfc.render;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.leafia.contents.machines.processing.solblaster.SolBlasterObj;
import com.leafia.contents.machines.processing.solblaster.SolBlasterObj.Mesh;

/**
 * The overhauled DFC's meshes, read from their OBJs by name.
 *
 * <p>Not an upstream file: upstream's {@code ResourceInit.getVAO} loads each OBJ into a
 * {@code WaveFrontObjectVAO} whose {@code renderPart("Core")} draws one named object under whatever texture
 * and blend the caller bound. A {@code neoforge:obj} model cannot be asked for a part per call, and the
 * instability ring's texture coordinates run past 100 (an atlas sprite does not wrap), so the DFC is drawn
 * from its own geometry like the Neutron Blaster, with the same reader ({@link SolBlasterObj#parse}): one
 * {@link Mesh} per object, every texture coordinate as the file has it, {@code v} flipped.
 *
 * <p>No client class is named, so a GameTest on a dedicated server can read the real files and pin which
 * objects each one has - which is what a renderer that names a part by string most needs pinned.
 */
public final class DFCObj {

	private DFCObj() {}

	public static final String DIR = "/assets/leafia/obj/dfc/";

	/** The seven component meshes, upstream's {@code models/leafia/dfc_rotatable}. */
	public static final String BOOSTER = "booster", ABSORBER = "absorber", REINFORCED = "reinforced",
			INJECTOR = "injector", STABILIZER = "stabilizer", EXCHANGER = "exchanger", PULSER = "pulser";

	/** The ring's file, upstream's {@code ecr_instability_ring}. */
	public static final String RING = "ecr_instability_ring";

	/** The ten deformed spheres, {@code deform0} to {@code deform9}. */
	public static final int DEFORMED = 10;

	/**
	 * The objects the renderers name, as strings the compiler cannot check against a file: a name the mesh
	 * lacks draws nothing and says nothing (upstream's {@code renderPart} is the same), so each is a
	 * constant here and {@link #DRAWN} says which meshes the renderer asks which of.
	 */
	public static final String CORE = "Core", FRAME = "Frame", ARM = "Arm", FAN = "Fan", GLASS = "Glass", LENS = "Lens",
			NEON = "Neon", ARM_LASER = "ArmLaser", RING_OUTER = "EnergyRingOuter", RING_INNER = "EnergyRingInner",
			INNER_BAND = "InstabilityInnerRing", OUTER_BAND = "InstabilityRing";

	/**
	 * Per mesh, the objects {@code DFCComponentRender} asks for. Every object a file has is asked for
	 * (none is forgotten), and the only name asked for that a file lacks is the {@code Arm} of the booster
	 * and of the exchanger, which upstream's shared loop asks of every part and which draws nothing there.
	 */
	public static final Map<String, java.util.Set<String>> DRAWN = Map.of(
			BOOSTER, java.util.Set.of(CORE, FRAME, ARM),
			ABSORBER, java.util.Set.of(CORE, FRAME, ARM),
			REINFORCED, java.util.Set.of(CORE, FRAME, ARM, FAN),
			INJECTOR, java.util.Set.of(CORE, FRAME, ARM),
			STABILIZER, java.util.Set.of(CORE, FRAME, ARM, GLASS, LENS),
			EXCHANGER, java.util.Set.of(CORE, FRAME, ARM),
			PULSER, java.util.Set.of(CORE, FRAME, ARM, ARM_LASER, NEON, RING_OUTER, RING_INNER));

	private static final Map<String, Map<String, Mesh>> CACHE = new ConcurrentHashMap<>();

	/** The objects of {@code obj/dfc/<name>.obj} by name, read once. */
	public static Map<String, Mesh> load(String name) {
		return CACHE.computeIfAbsent(name, n -> {
			String path = DIR + n + ".obj";
			try(InputStream in = DFCObj.class.getResourceAsStream(path)) {
				if(in == null) throw new IllegalStateException("missing " + path);
				return SolBlasterObj.parse(in);
			} catch(IOException e) {
				throw new IllegalStateException("could not read " + path, e);
			}
		});
	}

	/**
	 * A whole OBJ as one mesh: faces that come before any object line (the base mod's spheres have none)
	 * and faces under objects alike. {@link SolBlasterObj#parse} insists on an object first, so a default
	 * one is put in front of the stream.
	 */
	public static Mesh whole(InputStream in) throws IOException {
		InputStream head = new ByteArrayInputStream("o __whole\n".getBytes(StandardCharsets.UTF_8));
		return merge(SolBlasterObj.parse(new SequenceInputStream(head, in)));
	}

	/** Every object of a file as one mesh, in file order: the deformed spheres, which have one object each. */
	public static Mesh merged(String name) {
		return merge(load(name));
	}

	private static Mesh merge(Map<String, Mesh> parts) {
		int floats = 0;
		int faces = 0;
		for(Mesh m : parts.values()) {
			floats += m.data().length;
			faces += m.faces();
		}
		float[] data = new float[floats];
		int at = 0;
		for(Mesh m : parts.values()) {
			System.arraycopy(m.data(), 0, data, at, m.data().length);
			at += m.data().length;
		}
		return new Mesh(data, faces);
	}
}
