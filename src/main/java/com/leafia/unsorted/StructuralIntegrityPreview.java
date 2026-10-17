package com.leafia.unsorted;

import java.util.ArrayList;
import java.util.List;

/**
 * The structural integrity preview's geometry: the wire box drawn where a held block would go, filled from the bottom by how
 * well it would be held up (D-3040).
 *
 * <p>Upstream: {@code LeafiaClientListener.HandlerClient.renderWorld} and its {@code renderBox}, the "client preview of a block
 * about to be placed" {@link StructuralIntegrityHandler}'s note left out. With structural integrity on, holding a block and
 * looking at a face shows a unit box at the position the block would take, from
 * {@link StructuralIntegrityHandler#handleBlockSimulate}'s {@code maxRatio} - the load on the worst column over its glue:
 *
 * <ul>
 * <li>its vertical edges are white from the bottom up to {@code 1 - ratio} and red above that, so a box that is all white
 *     would carry no load and a box that is all red would fall; the bottom square is white unless the box is all red, the
 *     top square red unless it is all white;</li>
 * <li>a block that would stand ({@code ratio < 1}) is drawn at three quarters size, and a fading copy grows from it to 0.9
 *     over three quarters of every second;</li>
 * <li>a block that would fall is drawn alone, swelling from 0.75 to 0.85 and back over the same three quarters.</li>
 * </ul>
 *
 * <p>The geometry is here so a test can read it; {@code StructuralIntegrityPreviewClient} draws it, while the switch is on
 * or while a meteor impact's seismic level is up ({@code ImpactSeismic.seismic}, sent to clients by
 * {@code ImpactSeismic.SeismicSyncPacket}), as upstream's did.
 */
public final class StructuralIntegrityPreview {

	private StructuralIntegrityPreview() {}

	/** One edge, from {@code (x0, y0, z0)} to {@code (x1, y1, z1)}, in a unit box centred on the origin; {@code red} or white. */
	public record Edge(double x0, double y0, double z0, double x1, double y1, double z1, boolean red) {}

	/** One copy of the box: its scale and opacity. */
	public record Box(double scale, float alpha) {}

	/**
	 * The copies to draw for a block whose worst ratio is {@code maxRatio}, {@code offset} seconds into the current second
	 * (upstream's {@code System.currentTimeMillis() % 1000 / 1000}).
	 */
	public static List<Box> boxes(double maxRatio, double offset) {
		List<Box> boxes = new ArrayList<>();
		if(maxRatio < 1) {
			boxes.add(new Box(0.75, 0.75F));
			double pulsate = offset / 0.75;
			if(pulsate <= 1) boxes.add(new Box(0.75 + pulsate * 0.15, (float) Math.pow(0.5 - pulsate / 2, 1.5)));
		} else {
			double psin = Math.max(Math.sin(offset * Math.PI / 0.75), 0);
			boxes.add(new Box(0.75 + 0.1 * psin, 0.75F));
		}
		return boxes;
	}

	/** Upstream's {@code renderBox}: the edges of a unit box filled to {@code ratio} (= {@code 1 - maxRatio}). */
	public static List<Edge> edges(double ratio) {
		List<Edge> edges = new ArrayList<>();
		boolean bottomRed = ratio <= 0;
		edges.add(new Edge(-0.5, -0.5, -0.5, 0.5, -0.5, -0.5, bottomRed));
		edges.add(new Edge(0.5, -0.5, -0.5, 0.5, -0.5, 0.5, bottomRed));
		edges.add(new Edge(0.5, -0.5, 0.5, -0.5, -0.5, 0.5, bottomRed));
		edges.add(new Edge(-0.5, -0.5, 0.5, -0.5, -0.5, -0.5, bottomRed));
		// upstream's loop: each of its four passes draws all four corners, so every vertical edge is drawn four times over,
		// and with blending that is what makes them more opaque than the squares - kept
		for(int xo = -1; xo <= 1; xo += 2) {
			for(int zo = -1; zo <= 1; zo += 2) {
				double[][] corners = {{-xo / 2D, -zo / 2D}, {xo / 2D, -zo / 2D}, {-xo / 2D, zo / 2D}, {xo / 2D, zo / 2D}};
				if(ratio > 0) for(double[] c : corners) edges.add(new Edge(c[0], -0.5, c[1], c[0], -0.5 + ratio, c[1], false));
				if(ratio < 1) for(double[] c : corners) edges.add(new Edge(c[0], -0.5 + ratio, c[1], c[0], 0.5, c[1], true));
			}
		}
		boolean topRed = ratio < 1;
		edges.add(new Edge(-0.5, 0.5, -0.5, 0.5, 0.5, -0.5, topRed));
		edges.add(new Edge(0.5, 0.5, -0.5, 0.5, 0.5, 0.5, topRed));
		edges.add(new Edge(0.5, 0.5, 0.5, -0.5, 0.5, 0.5, topRed));
		edges.add(new Edge(-0.5, 0.5, 0.5, -0.5, 0.5, -0.5, topRed));
		return edges;
	}
}
