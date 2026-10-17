package com.leafia.contents.machines.controlpanel.core.client;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.leafia.contents.machines.controlpanel.core.ControlGeometry;
import com.leafia.contents.machines.controlpanel.core.NodeButton;
import com.leafia.contents.machines.controlpanel.core.NodeConnection;
import com.leafia.contents.machines.controlpanel.core.NodeDropdown;
import com.leafia.contents.machines.controlpanel.core.NodeElement;
import com.leafia.contents.machines.controlpanel.core.NodeSystem;
import com.leafia.contents.machines.controlpanel.core.NodeSystemEditor;
import com.leafia.contents.machines.controlpanel.core.NodeTextBox;
import com.leafia.contents.machines.controlpanel.core.nodes.Node;
import com.leafia.contents.machines.controlpanel.core.types.DataValue.DataType;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The picture of a node graph: the client halves of NTM-CE's {@code Node.render}/{@code drawConnections},
 * {@code NodeConnection.render}/{@code drawLine}, {@code NodeButton.render}, {@code NodeDropdown.render},
 * {@code NodeTextBox.render} and {@code NodeSystem.render} (CCP-NODE-EDITOR, D-3067), as functions that take the common object
 *.
 *
 * <p>Everything is drawn in <b>node space</b>, with the pose already scaled and shifted by the page for the canvas' pan and
 * zoom, from one sprite sheet ({@code node.png}, 128 by 128) whose pieces CE addressed by fractions of the texture; the numbers
 * below are CE's. A connection is a straight line between two ports (CE's {@code GL_LINES} at width 3, not a curve - the plan's
 * word "bezier" was wrong), drawn <b>under</b> the nodes. Text is drawn at 0.35 to 0.5 scale about its own anchor, as CE did.
 *
 * <p>An element type that this file does not know (an addon's own row) is drawn by registering a drawer for it with
 * {@link #register}; the lookup walks up the element's superclasses, so a subclass of a known row draws as its parent until it
 * registers its own.
 */
@OnlyIn(Dist.CLIENT)
public final class NodeDraw {

	private NodeDraw() {}

	/** Draws one element of a node, in node space. */
	@FunctionalInterface
	public interface ElementDrawer {
		void draw(GuiGraphics g, NodeElement element, float mX, float mY);
	}

	private static final Map<Class<?>, ElementDrawer> DRAWERS = new IdentityHashMap<>();

	static {
		register(NodeConnection.class, (g, e, mX, mY) -> connection(g, (NodeConnection) e, mX, mY));
		register(NodeDropdown.class, (g, e, mX, mY) -> dropdown(g, (NodeDropdown) e, mX, mY));
		register(NodeButton.class, (g, e, mX, mY) -> button(g, (NodeButton) e));
		register(NodeTextBox.class, (g, e, mX, mY) -> textBox(g, (NodeTextBox) e, mX, mY));
	}

	public static void register(Class<? extends NodeElement> type, ElementDrawer drawer) {
		DRAWERS.put(type, drawer);
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	private static int rgb(float[] c) {
		return GuiDraw.argb(c[0], c[1], c[2], 1);
	}

	private static void tex(GuiGraphics g, float x, float y, float u, float v, float w, float h, float uMax, float vMax, int argb) {
		GuiDraw.texQuad(g, NodeSystem.node_tex, x, y, w, h, u, v, uMax, vMax, argb);
	}

	// ------------------------------------------------------------------ the graph

	/** The wires, then the nodes: CE's {@code NodeSystem.render} without the dragging, which {@code NodeSystemEditor.dragTo} did. */
	public static void system(GuiGraphics g, NodeSystemEditor editor, float mX, float mY, float lineThickness) {
		float nodeMx = mX;
		float nodeMy = mY;
		float[] snap = editor.wireSnapPoint(mX, mY);
		if(snap != null) {
			nodeMx = snap[0];
			nodeMy = snap[1];
		}
		for(Node node : editor.system.nodes)
			connections(g, node, nodeMx, nodeMy, lineThickness);
		for(Node node : editor.system.nodes)
			node(g, node, mX, mY, editor.activeNode == node, editor.selectedNodes.contains(node));
	}

	/** CE's {@code Node.drawConnections}: the lines out of this node's sockets that are marked to draw. */
	public static void connections(GuiGraphics g, Node node, float mX, float mY, float thickness) {
		for(NodeConnection c : node.inputs)
			line(g, c, mX, mY, thickness);
		for(NodeConnection c : node.outputs)
			line(g, c, mX, mY, thickness);
	}

	/** CE's {@code NodeConnection.drawLine}. */
	private static void line(GuiGraphics g, NodeConnection c, float mouseX, float mouseY, float thickness) {
		if(!c.drawsLine)
			return;
		float x0 = c.offsetX + (c.isInput ? 0 : 40);
		float y0 = c.offsetY + 10;
		float x1;
		float y1;
		if(c.connectionIndex == -1 || !c.isInput) {
			x1 = mouseX;
			y1 = mouseY;
		} else {
			if(c.connection == null)
				return;
			List<NodeConnection> list = c.connection.outputs;
			if(c.connectionIndex < 0 || c.connectionIndex >= list.size())
				return;
			NodeConnection pair = list.get(c.connectionIndex);
			x1 = pair.offsetX + (pair.isInput ? 0 : 40);
			y1 = pair.offsetY + 10;
		}
		GuiDraw.line(g, x0, y0, x1, y1, thickness, 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------ a node

	/** CE's {@code Node.render}: the selection edge, the body in three pieces, the colour band, the rows, and the title. */
	public static void node(GuiGraphics g, Node n, float mX, float mY, boolean isActive, boolean isSelected) {
		if(isActive || isSelected) {
			int c = isActive ? GuiDraw.argb(0.7F, 0.7F, 0.7F, 1) : GuiDraw.argb(1F, 0.5F, 0.1F, 1);
			float edge = 0.5F;
			tex(g, n.posX - edge, n.posY - edge, 0, 0.875F, 40 + edge * 2, 6 + edge * 2, 0.625F, 0.96875F, c);
			tex(g, n.posX - edge, n.posY + 6, 0, 0.96875F, 40 + edge * 2, n.size + edge, 0.625F, 0.984375F, c);
			tex(g, n.posX - edge, n.posY + 6 + n.size, 0, 0.984375F, 40 + edge * 2, 1 + edge, 0.625F, 1, c);
		}
		float[] color = n.getColor();
		tex(g, n.posX, n.posY, 0, 0, 40, 6, 0.625F, 0.09375F, 0xFFFFFFFF);
		tex(g, n.posX, n.posY + 6, 0, 0.09375F, 40, n.size, 0.625F, 0.109375F, 0xFFFFFFFF);
		tex(g, n.posX, n.posY + 6 + n.size, 0, 0.109375F, 40, 1, 0.625F, 0.125F, 0xFFFFFFFF);
		tex(g, n.posX, n.posY + 1, 0, 0.125F, 40, 5, 0.625F, 0.203125F, rgb(color));

		for(int i = n.inputs.size() - 1; i >= 0; i--)
			element(g, n.inputs.get(i), mX, mY);
		for(int i = n.outputs.size() - 1; i >= 0; i--)
			element(g, n.outputs.get(i), mX, mY);
		for(int i = n.otherElements.size() - 1; i >= 0; i--)
			element(g, n.otherElements.get(i), mX, mY);

		GuiDraw.scaledText(g, font(), n.getDisplayName(), n.posX, n.posY, 0.5F, n.posX + 4, n.posY + 3, 0xFF2F2F2F);
	}

	/** Draws one element with the drawer for its class, or the nearest superclass that has one; none is drawn if there is no drawer. */
	public static void element(GuiGraphics g, NodeElement e, float mX, float mY) {
		for(Class<?> c = e.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			ElementDrawer d = DRAWERS.get(c);
			if(d != null) {
				d.draw(g, e, mX, mY);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ the elements

	/** CE's {@code NodeConnection.render}: the port, the value field of an unwired input, the name and the default. */
	private static void connection(GuiGraphics g, NodeConnection c, float mX, float mY) {
		float[] color = c.type.getColor();
		float x = c.offsetX + 38 + (c.isInput ? -40 : 0);
		float y = c.offsetY + 8;
		tex(g, x, y, 0.625F, 0, 4, 4, 0.6875F, 0.0625F, rgb(color));
		boolean lit = ControlGeometry.intersects2DBox(mX, mY, c.getValueBox()) && !c.isTyping;
		int field = lit ? 0xFFFFFFFF : GuiDraw.argb(0.6F, 0.6F, 0.6F, 1);
		boolean drawField = c.isInput && c.type != DataType.COMPOSITE;
		if(drawField) {
			if(c.enumSelector != null) {
				element(g, c.enumSelector, mX, mY);
				return;
			}
			if(c.connection == null)
				tex(g, x, y - 1, 0, 0.203125F, 40, 6, 0.625F, 0.296875F, field);
		}

		Font font = font();
		if(c.isTyping && c.builder != null) {
			String s = c.builder.toString();
			GuiDraw.scaledText(g, font, s + cursor(), x, y, 0.4F, x + (c.isInput ? 16 : -font.width(c.name) - 1), y + 1F, 0xFFAFAFAF);
		} else {
			int hex = c.isInput ? 0xFFAFAFAF : 0xFF2F2F2F;
			GuiDraw.scaledText(g, font, c.name, x, y, 0.4F, x + (c.isInput ? 16 : -font.width(c.name) - 1), y + 1F, hex);
			if(drawField && c.connection == null) {
				String s = c.defaultValue.toString();
				if(s.length() > 5)
					s = s.substring(0, 5);
				GuiDraw.scaledText(g, font, s, x, y, 0.4F, x + 94 - font.width(s), y + 1, 0xFFAFAFAF);
			}
		}
	}

	/** The blinking text cursor CE drew while typing: on for the second half of every second. */
	private static String cursor() {
		var level = Minecraft.getInstance().level;
		return level != null && level.getGameTime() % 20 > 10 ? "_" : "";
	}

	/** CE's {@code NodeButton.render}. */
	private static void button(GuiGraphics g, NodeButton b) {
		float x = b.offsetX + 4;
		float y = b.offsetY + 8;
		tex(g, x, y, 0, 0.890625F, 32, 6, 0.609375F, 0.984375F, 0xFFFFFFFF);
		Font font = font();
		GuiDraw.scaledText(g, font, b.name, x, y, 0.35F, x + 43 - font.width(b.name) / 2F, y + 5, 0xFF5F5F5F);
	}

	/** CE's {@code NodeDropdown.render}: the closed row with the chosen name and a caret that turns over when the list is open. */
	private static void dropdown(GuiGraphics g, NodeDropdown d, float mX, float mY) {
		float x = d.offsetX + 4;
		float y = d.offsetY + 8;
		tex(g, x, y, 0, 0.890625F, 32, 6, 0.609375F, 0.984375F, 0xFFFFFFFF);
		ItemListDraw.render(g, d.list, mX, mY);

		Font font = font();
		String s = d.currentName();
		GuiDraw.scaledText(g, font, s, x, y, 0.35F, x + 43 - font.width(s) / 2F, y + 5, 0xFF5F5F5F);

		float width = font.width("^");
		float height = font.lineHeight;
		float cx = x + 27.5F;
		float cy = y + 2.5F;
		g.pose().pushPose();
		g.pose().translate(cx + width * 0.4F, cy + height * 0.2F, 0);
		g.pose().scale(0.5F, 0.5F, 1);
		if(d.list.isClosed)
			g.pose().mulPose(Axis.ZP.rotationDegrees(180));
		g.pose().translate(-cx - width * 0.4F, -cy - height * 0.2F, 0);
		g.drawString(font, "^", cx, cy, 0xFF5F5F5F, false);
		g.flush();
		g.pose().popPose();
	}

	/** CE's {@code NodeTextBox.render}: the field, then either what is being typed or the name and the value. */
	private static void textBox(GuiGraphics g, NodeTextBox t, float mX, float mY) {
		float x = t.offsetX + 38 - 40;
		float y = t.offsetY + 8;
		boolean lit = ControlGeometry.intersects2DBox(mX, mY, t.getValueBox()) && !t.isTyping;
		tex(g, x, y - 1, 0, 0.203125F, 40, 6, 0.625F, 0.296875F, lit ? 0xFFFFFFFF : GuiDraw.argb(0.6F, 0.6F, 0.6F, 1));
		Font font = font();
		if(t.isTyping && t.builder != null) {
			GuiDraw.scaledText(g, font, t.builder.toString() + cursor(), x, y, 0.4F, x + 16, y + 1F, 0xFFAFAFAF);
		} else {
			GuiDraw.scaledText(g, font, t.name, x, y, 0.4F, x + 16, y + 1F, 0xFFAFAFAF);
			String s = t.value;
			if(s.length() > 5)
				s = s.substring(0, 5);
			GuiDraw.scaledText(g, font, s, x, y, 0.4F, x + 94 - font.width(s), y + 1, 0xFFAFAFAF);
		}
	}
}
