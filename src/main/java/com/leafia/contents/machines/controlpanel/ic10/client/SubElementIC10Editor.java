package com.leafia.contents.machines.controlpanel.ic10.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.leafia.config.LeafiaConfig;
import com.leafia.contents.machines.controlpanel.core.client.ControlEditScreen;
import com.leafia.contents.machines.controlpanel.core.client.GuiDraw;
import com.leafia.contents.machines.controlpanel.core.client.SubElement;
import com.leafia.contents.machines.controlpanel.ic10.IC10;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Argument;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Instruction;
import com.leafia.contents.machines.controlpanel.ic10.IC10.IC10Type;
import com.leafia.contents.machines.controlpanel.ic10.IC10Limits;
import com.leafia.contents.machines.controlpanel.ic10.IC10Program;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The IC10 code editor (CCP-IC10, D-3073): a text editor over a node's program, drawn at half scale with one colour per kind of
 * word, a cursor and a selection, undo and redo, a clipboard, completion hints, and the signature of the instruction under the
 * mouse. Adapted from Leafia's {@code SubElementIC10Editor} onto this port's {@link SubElement} pages.
 *
 * <h2>What Minecraft changed</h2>
 * Leafia drew with GL clip planes, a hand-made scroll bar and LWJGL's keyboard; this draws with a scissor and
 * {@link GuiGraphics}, takes a key and the character it typed as two events ({@link #keyPressed}, {@link #charTyped}), and reads
 * the mouse from the screen. The layout, the colours, the keys (arrows, Enter, Backspace, Tab, Ctrl+A/C/X/V/Z/Y) and the
 * editing rules are Leafia's. The editor edits {@link #instructions}, which {@code IC10Client} points at the node's own list.
 *
 * <h2>Limits</h2>
 * The program is bounded as the server will bound it ({@link IC10Limits}): at most {@value IC10Limits#MAX_LINES} lines of
 * {@value IC10Limits#MAX_LINE_CHARS} characters, printable ASCII only (the save format cannot hold more). An edit that would pass
 * a limit is refused, and a paste is cut to fit. The server refuses a program past them whatever this page does.
 *
 * <h2>Fixed from Leafia</h2>
 * A comment containing a run of spaces typed on its own threw a class cast in {@code formatInstruction}.
 */
@OnlyIn(Dist.CLIENT)
public class SubElementIC10Editor extends SubElement {

	@SafeVarargs
	static <T> ArrayList<T> asArrayList(T... a) {
		return new ArrayList<>(Arrays.asList(a));
	}

	public ArrayList<ArrayList<Object>> instructions = asArrayList(new ArrayList<>());

	ArrayList<UndoData> undoHistory = new ArrayList<>();
	int undoCurrent = -1;
	UndoData lastUndo = null;

	public static class UndoData {}

	public static class UndoDataAddRemove extends UndoData {
		public final int line;
		public int pos;
		public String str;
		public final boolean isAdd;

		public UndoDataAddRemove(int line, int pos, String str, boolean isAdd) {
			this.line = line;
			this.pos = pos;
			this.str = str;
			this.isAdd = isAdd;
		}
	}

	public static class UndoDataReplace extends UndoData {
		public final int lst;
		public final ArrayList<ArrayList<Object>> before;
		public final ArrayList<ArrayList<Object>> after;
		public final int[] cursorBefore;
		public final int[] cursorAfter;

		public UndoDataReplace(int lst, ArrayList<ArrayList<Object>> before, ArrayList<ArrayList<Object>> after, int[] cursorBefore, int[] cursorAfter) {
			this.lst = lst;
			this.before = before;
			this.after = after;
			this.cursorBefore = cursorBefore;
			this.cursorAfter = cursorAfter;
		}
	}

	public static final ResourceLocation texture = GuiDraw.texture("gui_ic10_editor");

	/** The colours of the words (Leafia's {@code EditorColors}), as ARGB. */
	public static final class EditorColors {
		private EditorColors() {}

		public static final int plain = argb(0xFF, 0xFF, 0xFF, 255);
		public static final int label = argb(0xFF, 0x88, 0xFF, 255);
		public static final int comment = argb(0xAA, 0xAA, 0xAA, 127);
		public static final int func = argb(0xFF, 0xAA, 0x30, 255);
		public static final int number = argb(0x00, 0xAA, 0xAA, 255);
		public static final int invalid = argb(0xFF, 0x00, 0x00, 255);
		public static final int suggestion = argb(0xFF, 0x88, 0x88, 127);
		public static final int register = argb(0x88, 0x88, 0xFF, 255);
		public static final int string = GuiDraw.argb(0.435F, 0.784F, 0.135F, 1);

		private static int argb(int r, int g, int b, int a) {
			return (a << 24) | (r << 16) | (g << 8) | b;
		}
	}

	public static final int textSpacing = 6;
	public static final int lineHeight = 12;
	public static final int heightOffset = 3;
	public static final float globalScale = 0.5F;
	public static final int scrollAreaX = 178;
	public static final int scrollAreaY = 188;

	/** The window of code inside the page, from the page's corner. */
	private static final int VIEW_X = 65, VIEW_Y = 48;

	public Button btn_back;
	public Button btn_funcs;
	public Button btn_examples;

	Map<Integer, String> labels = new HashMap<>();
	Map<Integer, String> aliases = new HashMap<>();
	boolean LMBdown = false;
	int[] cursor = null;
	int[] selStart = null;
	int preservePosition = 0;
	boolean ignoreCTRLflag = false;

	public double scrollRatioX = 0;
	public double scrollRatioY = 0;
	public boolean scrollingX = false;
	public boolean scrollingY = false;
	public int scrollXCount = 1;
	public int scrollYCount = 1;
	public int scrollXThing = 1;
	public int scrollYThing = 1;

	double cursorFlick = 0;
	long lastTimeMillis = System.currentTimeMillis();
	TypingData typing = null;

	public static class TypingData {
		List<String> words = new ArrayList<>();
		int index = -1;
	}

	public SubElementIC10Editor(ControlEditScreen gui) {
		super(gui);
	}

	// ------------------------------------------------------------------ the page

	@Override
	public void init() {
		btn_back = button(gui.getGuiLeft() + 7, gui.getGuiTop() + 13, 30, 20, "Back");
		btn_funcs = button(gui.getGuiLeft() + 54, gui.getGuiTop() + 13, 58, 20, "Functions");
		btn_examples = button(gui.getGuiLeft() + 117, gui.getGuiTop() + 13, 58, 20, "Examples");
		super.init();
	}

	/** Points the editor at a program (a node's own list: the edits are made in it) and puts the view and the history back. */
	public void open(ArrayList<ArrayList<Object>> program) {
		instructions = program;
		if(instructions.isEmpty())
			instructions.add(new ArrayList<>());
		undoHistory.clear();
		undoCurrent = -1;
		lastUndo = null;
		cursor = null;
		selStart = null;
		typing = null;
		scrollRatioX = 0;
		scrollRatioY = 0;
	}

	/** Replaces the whole program (the Examples page's "Proceed"). */
	public void replaceProgram(List<ArrayList<Object>> program) {
		instructions.clear();
		for(ArrayList<Object> line : program) {
			if(instructions.size() >= IC10Limits.MAX_LINES)
				break;
			instructions.add(new ArrayList<>(line));
		}
		if(instructions.isEmpty())
			instructions.add(new ArrayList<>());
		undoHistory.clear();
		undoCurrent = -1;
		lastUndo = null;
		cursor = null;
		selStart = null;
	}

	@Override
	public void actionPerformed(Button button) {
		if(button == btn_back)
			gui.popElement();
		else if(button == btn_funcs && gui.ic10Funcs != null)
			gui.pushElement(gui.ic10Funcs);
		else if(button == btn_examples && gui.ic10Examples != null)
			gui.pushElement(gui.ic10Examples);
	}

	@Override
	public void enableButtons(boolean enable) {
		GuiDraw.show(btn_back, enable);
		GuiDraw.show(btn_funcs, enable);
		GuiDraw.show(btn_examples, enable);
		if(enable)
			gui.setSlotsEnabled(false);
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		g.blit(texture, gui.getGuiLeft(), gui.getGuiTop(), 0, 0, gui.getXSize() - 5, gui.getYSize() - 5);
	}

	@Override
	public void onElementClose() {
		typing = null;
		LMBdown = false;
		scrollingX = false;
		scrollingY = false;
	}

	// ------------------------------------------------------------------ text of a line

	String substringSafely(String s, int start) {
		return s.substring(Math.min(Math.max(start, 0), s.length()));
	}

	String substringSafely(String s, int start, int end) {
		int from = Math.min(Math.max(start, 0), s.length());
		int to = Math.min(end, s.length());
		return to < from ? "" : s.substring(from, to);
	}

	String toString(ArrayList<Object> insn) {
		StringBuilder builder = new StringBuilder();
		for(Object object : insn) {
			if(object instanceof Number n) {
				for(int i = 0; i < n.intValue(); i++)
					builder.append(' ');
			} else if(object instanceof String s)
				builder.append(s);
		}
		return builder.toString();
	}

	int lenString(ArrayList<Object> insn) {
		int len = 0;
		for(Object object : insn) {
			if(object instanceof Number n)
				len += n.intValue();
			else if(object instanceof String s)
				len += s.length();
		}
		return len;
	}

	/** A line of text as the editor stores it, cut to the width a line may have. */
	ArrayList<Object> lineOf(String text) {
		if(text.length() > IC10Limits.MAX_LINE_CHARS)
			text = text.substring(0, IC10Limits.MAX_LINE_CHARS);
		return formatInstruction(asArrayList(IC10Program.ascii(text)));
	}

	void addString(int z, int pos, String str) {
		String s = toString(instructions.get(z));
		s = substringSafely(s, 0, pos - 1) + str + substringSafely(s, pos - 1);
		instructions.set(z, lineOf(s));
	}

	void removeString(int z, int pos, int length) {
		int posEnd = pos - length;
		String s = toString(instructions.get(z));
		s = substringSafely(s, 0, posEnd) + substringSafely(s, pos);
		instructions.set(z, lineOf(s));
	}

	/** Splits a line into words, spaces and a comment (Leafia's {@code formatInstruction}). */
	ArrayList<Object> formatInstruction(ArrayList<Object> insn) {
		boolean isComment = false;
		int needle = 0;
		while(needle < insn.size()) {
			Object obj = insn.get(needle);
			if(isComment) {
				if(obj instanceof Number n) {
					insn.set(needle, " ".repeat(Math.max(0, n.intValue())));
					obj = insn.get(needle);
				}
				if(!insn.get(needle - 1).equals("#")) {
					insn.set(needle - 1, (String) insn.get(needle - 1) + (String) obj);
					insn.remove(needle);
				} else
					needle++;
			} else if(obj.toString().startsWith("#")) {
				isComment = true;
				if(insn.get(needle).toString().length() > 1) {
					insn.add(needle + 1, insn.get(needle).toString().substring(1));
					insn.set(needle, "#");
				}
				needle++;
			} else {
				if(obj instanceof String s) {
					int spaceStart = s.indexOf(' ');
					if(spaceStart != -1) {
						int spaceEnd = spaceStart;
						while(spaceEnd + 1 < s.length()) {
							if(s.charAt(spaceEnd + 1) == ' ')
								spaceEnd++;
							else
								break;
						}
						int spaces = 1 + spaceEnd - spaceStart;
						String subA = s.substring(0, spaceStart);
						String subB = s.substring(spaceEnd + 1);
						insn.set(needle, subA);
						insn.add(needle + 1, spaces);
						insn.add(needle + 2, subB);
						obj = insn.get(needle);
					}
				}
				if(equals(obj, "") || equals(obj, 0))
					insn.remove(needle);
				else {
					if(needle == 0)
						needle++;
					else {
						Object prev = insn.get(needle - 1);
						if(prev instanceof Number n0 && obj instanceof Number n1) {
							insn.set(needle - 1, n0.intValue() + n1.intValue());
							insn.remove(needle);
						} else if(prev instanceof String s0 && obj instanceof String s1) {
							insn.set(needle - 1, s0 + s1);
							insn.remove(needle);
						} else
							needle++;
					}
				}
			}
		}
		return insn;
	}

	boolean equals(Object a, Object b) {
		if(a == null)
			return b == null;
		return a.equals(b);
	}

	@Nullable
	Integer tonumber(String s) {
		try {
			return Integer.parseInt(s);
		} catch(NumberFormatException ignored) {}
		return null;
	}

	@Nullable
	Double tonumberd(String s) {
		try {
			return Double.parseDouble(s);
		} catch(NumberFormatException ignored) {}
		return null;
	}

	// ------------------------------------------------------------------ definitions and colours

	void updateDefsForLine(int ln) {
		labels.remove(ln);
		aliases.remove(ln);
		ArrayList<Object> insn = instructions.get(ln);
		int arg = 0;
		int mode = -1;
		for(Object v0 : insn) {
			if(v0 instanceof String v) {
				arg++;
				if(v.equals("#")) return;
				if(arg == 1) {
					if(v.equals("alias"))
						mode = 0;
					else if(v.equals("define"))
						mode = 1;
					else if(v.length() >= 2 && v.endsWith(":")) {
						labels.put(ln, v.substring(0, v.length() - 1));
						return;
					} else
						return;
				} else {
					if(mode == 0) {
						if(arg == 2)
							aliases.put(ln, v);
						else if(arg == 3) { // verify value
							if(v.equals("ra") || v.equals("sp"))
								return;
							else if(v.startsWith("r")) {
								Integer idx = tonumber(v.substring(1));
								if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters)
									return;
								else
									break; // invalidate
							} else
								break; // invalidate
						}
					} else if(mode == 1) {
						if(arg == 2)
							labels.put(ln, v);
						else if(arg == 3) { // verify value
							if(tonumberd(v) != null)
								return;
							else
								break; // invalidate
						}
					}
				}
			}
		}
		labels.remove(ln);
		aliases.remove(ln);
	}

	void renderInstruction(GuiGraphics g, int ln) {
		ArrayList<Object> insn = instructions.get(ln);
		int offset = 0;
		int arg = 0;
		Object insnType = null;
		for(Object v0 : insn) {
			if(v0 instanceof Number num)
				offset += num.intValue();
			else {
				String v = (String) v0;
				int color = equals(insnType, "invalid") ? EditorColors.invalid : EditorColors.plain;
				arg++;
				if(arg == 1) {
					insnType = "invalid";
					color = EditorColors.invalid;
					if(v.endsWith(":")) {
						insnType = "label";
						color = EditorColors.label;
					}
					if(IC10.instructions.containsKey(v)) {
						insnType = IC10.instructions.get(v);
						color = EditorColors.func;
					}
					if(v.equals("define")) {
						insnType = IC10.define;
						color = EditorColors.label;
					}
				}
				if(v.equals("#")) insnType = "comment";
				if(equals(insnType, "comment")) color = EditorColors.comment;
				if(insnType instanceof IC10Instruction insnI && arg > 1) { // argument type coloring
					if(arg - 1 <= insnI.args.size()) {
						IC10Argument data = insnI.args.get(arg - 1 - 1);
						IC10Type ex_t = data.type;
						boolean ok = false;
						boolean forceNope = false;
						if(ex_t == IC10Type.NUMBER || ex_t == IC10Type.INTEGER || ex_t == IC10Type.STRING || ex_t == IC10Type.ANY) {
							if(insnI != IC10.define) { // label or registry checks (not usable in constant definition ofc)
								for(String l : labels.values()) { // constant check (pink)
									if(l.equals(v)) {
										ok = true;
										color = EditorColors.label;
										break;
									}
								}
								if(!ok) { // alias check (uncolored)
									for(Entry<Integer, String> entry : aliases.entrySet()) {
										if(entry.getValue().equals(v) && ln >= entry.getKey()) {
											ok = true;
											break;
										}
									}
								}
								if(!ok) { // direct register check (blue)
									if(v.equals("ra") || v.equals("sp")) {
										ok = true;
										color = EditorColors.register;
									} else if(v.startsWith("r")) {
										Integer idx = tonumber(v.substring(1));
										if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters) {
											ok = true;
											color = EditorColors.register;
										} else
											forceNope = true;
									}
								}
							}
							if(!ok && !forceNope) { // number check (turquoise)
								if(ex_t == IC10Type.INTEGER || ex_t == IC10Type.NUMBER || ex_t == IC10Type.ANY) {
									Double d = tonumberd(v);
									if(d != null) {
										ok = true;
										color = EditorColors.number;
										if(ex_t == IC10Type.INTEGER && (int) (double) d != d)
											ok = false;
									}
								}
							}
							if(!ok && !forceNope) {
								if(ex_t == IC10Type.STRING || ex_t == IC10Type.ANY) {
									if(v.startsWith("\"") && v.endsWith("\"")) {
										ok = true;
										color = EditorColors.string;
									}
								}
							}
							if(!ok)
								color = EditorColors.invalid;
						} else if(ex_t == IC10Type.REGISTER) {
							for(Entry<Integer, String> entry : aliases.entrySet()) {
								if(entry.getValue().equals(v) && ln >= entry.getKey()) {
									ok = true;
									break;
								}
							}
							if(!ok) { // direct register check (blue)
								if(v.equals("ra") || v.equals("sp")) {
									ok = true;
									color = EditorColors.register;
								} else if(v.startsWith("r")) {
									Integer idx = tonumber(v.substring(1));
									if(idx != null && idx >= 0 && idx <= LeafiaConfig.ic10MaxRegisters) {
										ok = true;
										color = EditorColors.register;
									}
								}
							}
							if(!ok)
								color = EditorColors.invalid;
						}
					} else // if this argument is out of bounds
						color = EditorColors.invalid;
				}
				setLineText(g, ln, v, offset, color);
				offset += v.length();
			}
		}
		if(insnType instanceof IC10Instruction insnI) {
			if(arg <= insnI.args.size()) {
				for(int sarg = arg; sarg <= insnI.args.size(); sarg++) {
					IC10Argument data = insnI.args.get(sarg - 1);
					String ghost = data.name + "<" + data.type.name().toLowerCase() + ">";
					setLineText(g, ln, ghost, offset, EditorColors.suggestion);
					offset = offset + ghost.length() + 1;
				}
			}
		}
	}

	/** One glyph at a time on a fixed grid, as Leafia drew it, in the scaled space; only what is in the window is drawn. */
	void setLineText(GuiGraphics g, int line, String text, int start, int argb) {
		Font font = gui.font();
		double xOffset = codeOffsetX();
		int firstCol = (int) Math.floor(xOffset / textSpacing) - 1;
		int lastCol = firstCol + (int) (scrollAreaX / globalScale / textSpacing) + 3;
		for(int i = 0; i < text.length(); i++) {
			int col = start + i;
			if(col < firstCol || col > lastCol)
				continue;
			String s = String.valueOf(text.charAt(i));
			int width = font.width(s);
			g.drawString(font, s, (col * textSpacing + (int) (textSpacing / 2f - width / 2f) + 2), (line * lineHeight + heightOffset), argb, false);
		}
	}

	// ------------------------------------------------------------------ the view

	/** How far the code is shifted left, in the scaled space. */
	double codeOffsetX() {
		return scrollXThing * 2 * textSpacing * scrollRatioX;
	}

	double codeOffsetY() {
		return scrollYThing * lineHeight * scrollRatioY;
	}

	float relMouseX() {
		return gui.mouseX - gui.getGuiLeft();
	}

	float relMouseY() {
		return gui.mouseY - gui.getGuiTop();
	}

	static int scrollBarPos(int size, int area, double ratio) {
		return (int) (ratio * (area - size));
	}

	static double scrollRatio(int size, int area, double relativePosition) {
		return Mth.clamp((relativePosition - size / 2d) / (area - size), 0, 1);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Font font = gui.font();
		int left = gui.getGuiLeft();
		int top = gui.getGuiTop();
		String hint = "Needs to be connected to output nodes!";
		g.drawString(font, hint, left + gui.getXSize() / 2F - font.width(hint) / 2F + 15, top + 17 + 20, 0xFF777777, false);

		double dt = (System.currentTimeMillis() - lastTimeMillis) / 1000d;
		lastTimeMillis = System.currentTimeMillis();

		// the scroll bars
		scrollXCount = 1;
		for(ArrayList<Object> insn : instructions)
			scrollXCount = Math.max(scrollXCount, lenString(insn) / 2 + 1);
		scrollYCount = instructions.size();
		double scrollXDiv = Math.max(1, scrollXCount) / 30d;
		double scrollYDiv = Math.max(1, scrollYCount) / 31d;
		int scrollBarXSize = (int) Mth.clamp(scrollAreaX / scrollXDiv, 20, scrollAreaX);
		int scrollBarYSize = (int) Mth.clamp(scrollAreaY / scrollYDiv, 20, scrollAreaY);
		scrollXThing = Math.max(0, scrollXCount - 30);
		scrollYThing = Math.max(0, scrollYCount - 31);
		if(scrollingX)
			scrollRatioX = scrollRatio(scrollBarXSize, scrollAreaX, relMouseX() - VIEW_X);
		if(scrollingY)
			scrollRatioY = scrollRatio(scrollBarYSize, scrollAreaY, relMouseY() - VIEW_Y);
		{
			int pos = left + VIEW_X + scrollBarPos(scrollBarXSize, scrollAreaX, scrollRatioX);
			g.blit(texture, pos, top + 237, 73, 251, 2, 5);
			g.blit(texture, pos + scrollBarXSize - 2, top + 237, 73 + scrollAreaX - 2, 251, 2, 5);
			stretch(g, pos + 2, top + 237, 73 + 2, 251, 1, 5, scrollBarXSize - 4, 5);
		}
		{
			int pos = top + VIEW_Y + scrollBarPos(scrollBarYSize, scrollAreaY, scrollRatioY);
			g.blit(texture, left + 244, pos, 251, 63, 5, 2);
			g.blit(texture, left + 244, pos + scrollBarYSize - 2, 251, 63 + scrollAreaY - 2, 5, 2);
			stretch(g, left + 244, pos + 2, 251, 63 + 2, 5, 1, 5, scrollBarYSize - 4);
		}

		// the line numbers, clipped to the window's height only
		for(int i = 0; i < instructions.size(); i++)
			updateDefsForLine(i);
		double yOff = codeOffsetY();
		int firstLine = Math.max(0, (int) Math.floor(yOff / lineHeight) - 1);
		int lastLine = Math.min(instructions.size() - 1, firstLine + (int) (scrollAreaY / globalScale / lineHeight) + 3);

		g.enableScissor(left + VIEW_X - 14, top + VIEW_Y, left + VIEW_X + scrollAreaX, top + VIEW_Y + scrollAreaY);
		g.pose().pushPose();
		g.pose().translate(left + VIEW_X, top + VIEW_Y, 0);
		g.pose().translate(0, -yOff * globalScale, 0);
		g.pose().scale(globalScale, globalScale, 1);
		for(int i = firstLine; i <= lastLine; i++) {
			String n = Integer.toString(i);
			g.drawString(font, n, -2 - font.width(n), i * lineHeight + heightOffset, 0xFFAAAAAA, false);
		}
		g.pose().popPose();
		g.disableScissor();

		// the code
		g.enableScissor(left + VIEW_X, top + VIEW_Y, left + VIEW_X + scrollAreaX, top + VIEW_Y + scrollAreaY);
		g.pose().pushPose();
		g.pose().translate(left + VIEW_X, top + VIEW_Y, 0);
		g.pose().translate(-codeOffsetX() * globalScale, -yOff * globalScale, 0);
		g.pose().scale(globalScale, globalScale, 1);
		for(int i = firstLine; i <= lastLine; i++)
			renderInstruction(g, i);
		if(cursor != null) {
			if(selStart != null && !posEquals(cursor, selStart)) {
				int[][] sorted = getSelectionSorted();
				int[] sst = sorted[0];
				int[] sen = sorted[1];
				for(int ln = sst[0]; ln <= sen[0] && ln < instructions.size(); ln++) {
					int[] range = getSelectionRangeForLine(ln, sst, sen);
					int rs = range[0];
					int re = range[1];
					int start = rs * textSpacing + 2;
					if(rs == 0)
						start = 0;
					GuiDraw.rect(g, start, ln * lineHeight, re * textSpacing + 2, (ln + 1) * lineHeight, GuiDraw.argb(28 * 2 / 255F, 79 * 2 / 255F, 111 * 2 / 255F, 64 / 255F));
				}
			}
			cursorFlick = (cursorFlick + dt) % 0.75;
			double flk = Math.floor(cursorFlick / 0.375) + 1;
			float x = cursor[1] * textSpacing + 2;
			float y = cursor[0] * lineHeight;
			GuiDraw.rect(g, x - 0.5F, y, x + 0.5F, y + lineHeight, GuiDraw.argb((float) (28 * flk / 255), (float) (79 * flk / 255), (float) (111 * flk / 255), 1));
		}
		g.pose().popPose();
		g.disableScissor();

		// completion hints, or the signature under the mouse
		if(cursor != null && typing != null) {
			float x = (float) ((cursor[1] * textSpacing + 2 - codeOffsetX()) * globalScale);
			float y = (float) (((cursor[0] + 1) * lineHeight - yOff) * globalScale);
			List<Component> suggestions = new ArrayList<>();
			List<String> plain = new ArrayList<>();
			if(typing.index == 0) {
				for(String insn : filterByPrefix(IC10.instructions.keySet(), typing.words.get(typing.index))) {
					IC10Instruction instruction = IC10.instructions.get(insn);
					suggestions.add(Component.literal(insn).append(Component.literal(argsString(instruction)).withStyle(ChatFormatting.DARK_GRAY)));
				}
			} else if(IC10.instructions.containsKey(typing.words.get(0))) {
				IC10Instruction instruction = IC10.instructions.get(typing.words.get(0));
				int argindex = typing.index - 1;
				if(argindex < instruction.args.size()) {
					IC10Argument arg = instruction.args.get(argindex);
					if(arg.type != IC10Type.NAME && arg.type != IC10Type.REGISTER) {
						plain.addAll(filterByPrefix(aliases.values(), typing.words.get(typing.index)));
						plain.addAll(filterByPrefix(labels.values(), typing.words.get(typing.index)));
					} else if(arg.type == IC10Type.REGISTER)
						plain.addAll(filterByPrefix(aliases.values(), typing.words.get(typing.index)));
				}
			}
			Collections.sort(plain);
			for(String s : plain)
				suggestions.add(Component.literal(s));
			if(!suggestions.isEmpty())
				g.renderComponentTooltip(font, suggestions, (int) (left + VIEW_X + x - 10), (int) (top + VIEW_Y + y));
		} else {
			int[] hovering = getPositionFromMouse();
			if(hovering != null && hovering[0] >= 0 && hovering[0] < instructions.size()) {
				ArrayList<Object> insn = instructions.get(hovering[0]);
				float x = (float) ((hovering[1] * textSpacing + 2 - codeOffsetX()) * globalScale);
				float y = (float) (((hovering[0] + 1) * lineHeight - yOff) * globalScale);
				TypingData data = getTypingData(insn, hovering[1]);
				if(data != null && data.index == 0 && IC10.instructions.containsKey(data.words.get(0))) {
					IC10Instruction instruction = IC10.instructions.get(data.words.get(0));
					List<Component> list = new ArrayList<>();
					String args = argsString(instruction);
					if(!args.isEmpty())
						list.add(Component.literal(args.substring(1)).withStyle(ChatFormatting.DARK_GRAY));
					for(String d : instruction.desc)
						list.add(Component.literal(d));
					g.renderComponentTooltip(font, list, (int) (left + VIEW_X + x - 10), (int) (top + VIEW_Y + y));
				}
			}
		}
	}

	/** Draws a part of the texture stretched over a box (the scroll bars' middles are one pixel wide in the sheet). */
	private void stretch(GuiGraphics g, int x, int y, int u, int v, int uw, int vh, int w, int h) {
		if(w <= 0 || h <= 0)
			return;
		g.blit(texture, x, y, w, h, u, v, uw, vh, 256, 256);
	}

	static String argsString(IC10Instruction instruction) {
		StringBuilder args = new StringBuilder();
		for(IC10Argument arg : instruction.args)
			args.append(" ").append(arg.name).append("<").append(arg.type.name().toLowerCase()).append(">");
		return args.toString();
	}

	public static List<String> filterByPrefix(Collection<String> col, String pfx) {
		List<String> list = new ArrayList<>();
		for(String s : col) {
			if(s.toLowerCase().startsWith(pfx.toLowerCase()))
				list.add(s);
		}
		return list;
	}

	@Nullable
	TypingData getTypingData(ArrayList<Object> insn, int pos) {
		int current = 0;
		TypingData data = new TypingData();
		for(Object object : insn) {
			if(object instanceof String s)
				data.words.add(s);
		}
		for(Object object : insn) {
			if(object instanceof Number n)
				current += n.intValue();
			else if(object instanceof String s) {
				current += s.length();
				data.index++;
			}
			if(current >= pos) {
				if(data.index >= 0)
					return data;
				else
					return null;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the selection

	boolean posEquals(int[] p1, int[] p2) {
		if(p1 == null) return p2 == null;
		if(p2 == null) return false;
		return p1[0] == p2[0] && p1[1] == p2[1];
	}

	boolean isCursorBeforeSelectionStart() {
		if(cursor[0] < selStart[0]) return true;
		return cursor[0] == selStart[0] && cursor[1] < selStart[1];
	}

	/** {start, end} of the selection in reading order. */
	int[][] getSelectionSorted() {
		boolean opposite = isCursorBeforeSelectionStart();
		return new int[][]{opposite ? cursor : selStart, opposite ? selStart : cursor};
	}

	int[] getSelectionRangeForLine(int ln, int[] sst, int[] sen) {
		if(sst == null) {
			int[][] sorted = getSelectionSorted();
			sst = sorted[0];
			sen = sorted[1];
		}
		int pen = sen[0] == ln ? sen[1] : lenString(instructions.get(ln));
		int pst = sst[0] == ln ? sst[1] : 0;
		return new int[]{pst, pen};
	}

	void deleteSelection(int[] sst, int[] sen) {
		if(sst == null) {
			int[][] sorted = getSelectionSorted();
			sst = sorted[0];
			sen = sorted[1];
		}
		cursor = new int[]{sst[0], sst[1]};
		String startInsnStr = toString(instructions.get(sst[0]));
		preservePosition = cursor[1];
		String endInsnStr = sen[0] == sst[0] ? startInsnStr : toString(instructions.get(sen[0]));
		instructions.set(sst[0], lineOf(substringSafely(startInsnStr, 0, sst[1]) + substringSafely(endInsnStr, sen[1])));
		int moveUp = sen[0] - sst[0];
		for(int i = 0; i < moveUp; i++)
			instructions.remove(sst[0] + 1);
	}

	// ------------------------------------------------------------------ the mouse

	@Nullable
	int[] getPositionFromMouse() {
		float mx = relMouseX();
		float my = relMouseY();
		if(mx >= VIEW_X && my >= VIEW_Y && mx <= VIEW_X + scrollAreaX && my <= VIEW_Y + scrollAreaY) {
			double codeX = (mx - VIEW_X) / globalScale + codeOffsetX();
			double codeY = (my - VIEW_Y) / globalScale + codeOffsetY();
			int line = (int) codeY / lineHeight;
			if(line >= 0 && line < instructions.size())
				return new int[]{line, (int) Math.max(0, Math.min(Math.floor(codeX / textSpacing + 0.25), lenString(instructions.get(line))))};
		}
		return null;
	}

	private boolean inRect(double mx, double my, int x, int y, int w, int h) {
		double rx = mx - gui.getGuiLeft();
		double ry = my - gui.getGuiTop();
		return rx >= x && ry >= y && rx < x + w && ry < y + h;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if(button != 0)
			return false;
		boolean used = false;
		typing = null;
		if(inRect(mouseX, mouseY, VIEW_X, VIEW_Y, scrollAreaX, scrollAreaY)) {
			LMBdown = true;
			cursor = getPositionFromMouse();
			if(cursor != null)
				preservePosition = cursor[1];
			selStart = new int[]{cursor[0], cursor[1]};
			used = true;
		}
		if(inRect(mouseX, mouseY, VIEW_X, 237, scrollAreaX, 5)) {
			scrollingX = true;
			used = true;
		}
		if(inRect(mouseX, mouseY, 244, VIEW_Y, 5, scrollAreaY)) {
			scrollingY = true;
			used = true;
		}
		return used;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if(button == 0) {
			LMBdown = false;
			scrollingX = false;
			scrollingY = false;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		double delta = -Mth.clamp(scrollY, -1, 1);
		if(delta == 0)
			return false;
		if(Screen.hasShiftDown())
			scrollRatioX = Mth.clamp(scrollRatioX + delta / Math.max(scrollXThing, 1), 0, 1);
		else
			scrollRatioY = Mth.clamp(scrollRatioY + delta / Math.max(scrollYThing, 1), 0, 1);
		return true;
	}

	@Override
	public void update() {
		if(LMBdown && selStart != null) {
			int[] at = getPositionFromMouse();
			if(at != null) {
				cursor = at;
				preservePosition = cursor[1];
			}
		}
	}

	// ------------------------------------------------------------------ undo

	void addUndo(UndoData data) {
		if(undoCurrent + 1 < undoHistory.size())
			undoHistory.subList(undoCurrent + 1, undoHistory.size()).clear();
		undoHistory.add(data);
		undoCurrent = undoHistory.size() - 1;
		lastUndo = data;
	}

	void undo(boolean isRedo) {
		int index = isRedo ? undoCurrent + 1 : undoCurrent;
		if(index >= 0 && index < undoHistory.size()) {
			UndoData change = undoHistory.get(index);
			undoCurrent = isRedo ? undoCurrent + 1 : undoCurrent - 1;
			if(change instanceof UndoDataAddRemove addrmv) {
				int ln = addrmv.line;
				int pos = addrmv.pos;
				if(ln < 0 || ln >= instructions.size())
					return;
				boolean doRemove = addrmv.isAdd; // undoing an addition is removing it
				if(isRedo) doRemove = !doRemove;
				if(doRemove) {
					removeString(ln, pos + addrmv.str.length() - 1, addrmv.str.length());
					cursor = new int[]{ln, Math.max(0, pos - 1)};
				} else {
					addString(ln, pos, addrmv.str);
					cursor = new int[]{ln, pos + addrmv.str.length() - 1};
				}
				selStart = new int[]{cursor[0], cursor[1]};
			} else if(change instanceof UndoDataReplace replace) {
				int lst = replace.lst; // line start
				ArrayList<ArrayList<Object>> newData = isRedo ? replace.after : replace.before;
				ArrayList<ArrayList<Object>> curData = isRedo ? replace.before : replace.after;
				int move = newData.size() - curData.size();
				if(lst < 0 || lst + curData.size() > instructions.size())
					return;
				for(int ln = lst; ln <= lst - 1 + Math.min(newData.size(), curData.size()); ln++)
					instructions.set(ln, newData.get(ln - lst));
				if(move > 0) {
					ArrayList<ArrayList<Object>> addLines = new ArrayList<>();
					for(int lno = 1; lno <= move; lno++)
						addLines.add(newData.get(curData.size() + lno - 1));
					instructions.addAll(lst + curData.size(), addLines);
				} else {
					for(int i = 0; i < -move; i++)
						instructions.remove(lst + newData.size());
				}
				int[] targetCursorPos = isRedo ? replace.cursorAfter : replace.cursorBefore;
				if(targetCursorPos != null) {
					cursor = new int[]{targetCursorPos[0], targetCursorPos[1]};
					selStart = new int[]{cursor[0], cursor[1]};
				}
			}
			clampCursor();
		}
	}

	void clampCursor() {
		if(cursor == null)
			return;
		cursor[0] = Mth.clamp(cursor[0], 0, instructions.size() - 1);
		cursor[1] = Mth.clamp(cursor[1], 0, lenString(instructions.get(cursor[0])));
		if(selStart != null) {
			selStart[0] = Mth.clamp(selStart[0], 0, instructions.size() - 1);
			selStart[1] = Mth.clamp(selStart[1], 0, lenString(instructions.get(selStart[0])));
		}
	}

	void copySelection(int[] sst, int[] sen) {
		if(sst == null) {
			int[][] sorted = getSelectionSorted();
			sst = sorted[0];
			sen = sorted[1];
		}
		StringBuilder copyLines = new StringBuilder();
		for(int ln = sst[0]; ln <= sen[0]; ln++) {
			int[] range = getSelectionRangeForLine(ln, sst, sen);
			String s = substringSafely(toString(instructions.get(ln)), range[0], range[1]);
			if(ln == sst[0])
				copyLines = new StringBuilder(s);
			else
				copyLines.append("\n").append(s);
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(copyLines.toString());
	}

	// ------------------------------------------------------------------ the keyboard

	/** Appends a typed character at the cursor, with the undo that joins a run of typing into one step. */
	private void insertChar(String text) {
		if(lenString(instructions.get(cursor[0])) + text.length() > IC10Limits.MAX_LINE_CHARS)
			return;
		cursor[1] = cursor[1] + text.length();
		addString(cursor[0], cursor[1] - text.length() + 1, text);
		boolean undoAppended = false;
		if(lastUndo instanceof UndoDataAddRemove addrmv && addrmv.isAdd && undoCurrent == undoHistory.size() - 1) {
			if(addrmv.line == cursor[0] && addrmv.pos == cursor[1] - text.length() - addrmv.str.length() + 1) {
				addrmv.str = addrmv.str + text;
				undoAppended = true;
			}
		}
		if(!undoAppended)
			addUndo(new UndoDataAddRemove(cursor[0], cursor[1] - text.length() + 1, text, true));
		typing = getTypingData(instructions.get(cursor[0]), cursor[1]);
		preservePosition = cursor[1];
		selStart = new int[]{cursor[0], cursor[1]};
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if(cursor == null || Screen.hasControlDown())
			return false;
		if(chr < 32 || chr > 126)
			return true;
		if(cursor[0] < 0 || cursor[0] >= instructions.size())
			return true;
		if(!posEquals(selStart, cursor) && selStart != null) {
			// typing over a selection replaces it, as every editor does
			int[][] sorted = getSelectionSorted();
			ArrayList<ArrayList<Object>> backup = new ArrayList<>();
			for(int ln = sorted[0][0]; ln <= sorted[1][0]; ln++)
				backup.add(instructions.get(ln));
			int[] cursorBkp = cursor;
			deleteSelection(sorted[0], sorted[1]);
			addUndo(new UndoDataReplace(sorted[0][0], backup, asArrayList(instructions.get(sorted[0][0])), cursorBkp, cursor));
		}
		cursorFlick = 0.375;
		insertChar(String.valueOf(chr));
		return true;
	}

	@Override
	public boolean keyPressed(int code, int scanCode, int modifiers) {
		boolean shiftDown = Screen.hasShiftDown();
		boolean ctrlDown = Screen.hasControlDown();
		if(ignoreCTRLflag) ctrlDown = false;
		if(code != GLFW.GLFW_KEY_LEFT_SHIFT && code != GLFW.GLFW_KEY_RIGHT_SHIFT && code != GLFW.GLFW_KEY_LEFT_CONTROL && code != GLFW.GLFW_KEY_RIGHT_CONTROL)
			typing = null;
		if(ctrlDown && code == GLFW.GLFW_KEY_Z) {
			undo(false);
			return true;
		}
		if(ctrlDown && code == GLFW.GLFW_KEY_Y) {
			undo(true);
			return true;
		}
		if(cursor == null)
			return false;
		cursorFlick = 0.375;
		if(cursor[0] < 0 || cursor[0] >= instructions.size())
			return true;
		boolean isArrowKey = false;
		if(code == GLFW.GLFW_KEY_LEFT) {
			if(cursor[1] <= 0 && cursor[0] > 0)
				cursor = new int[]{cursor[0] - 1, lenString(instructions.get(cursor[0] - 1))};
			else
				cursor[1] = Math.max(cursor[1] - 1, 0);
			preservePosition = cursor[1];
			isArrowKey = true;
		} else if(code == GLFW.GLFW_KEY_RIGHT) {
			if(cursor[1] >= lenString(instructions.get(cursor[0])) && cursor[0] + 1 < instructions.size())
				cursor = new int[]{cursor[0] + 1, 0};
			else
				cursor[1] = Math.min(cursor[1] + 1, lenString(instructions.get(cursor[0])));
			preservePosition = cursor[1];
			isArrowKey = true;
		} else if(code == GLFW.GLFW_KEY_UP) {
			int index = Math.max(cursor[0] - 1, 0);
			cursor = new int[]{index, Math.min(lenString(instructions.get(index)), preservePosition)};
			isArrowKey = true;
		} else if(code == GLFW.GLFW_KEY_DOWN) {
			int index = Math.min(cursor[0] + 1, instructions.size() - 1);
			cursor = new int[]{index, Math.min(lenString(instructions.get(index)), preservePosition)};
			isArrowKey = true;
		} else if(ctrlDown) {
			if(code == GLFW.GLFW_KEY_C)
				copySelection(null, null);
			else if(code == GLFW.GLFW_KEY_X) {
				copySelection(null, null);
				ignoreCTRLflag = true;
				keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0); // press backspace
				ignoreCTRLflag = false;
			} else if(code == GLFW.GLFW_KEY_V)
				paste();
			else if(code == GLFW.GLFW_KEY_A) {
				int index = instructions.size() - 1;
				selStart = new int[]{0, 0};
				cursor = new int[]{index, lenString(instructions.get(index))};
			}
			return true;
		} else if(code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {
			if(instructions.size() >= IC10Limits.MAX_LINES)
				return true;
			ArrayList<Object> curInsn = instructions.get(cursor[0]);
			String strInsn = toString(curInsn);
			int indent = 0;
			if(!curInsn.isEmpty() && curInsn.get(0) instanceof Number n)
				indent = Math.min(n.intValue(), cursor[1]);
			int at = Math.min(cursor[1], strInsn.length());
			String rest = strInsn.substring(at);
			instructions.set(cursor[0], lineOf(strInsn.substring(0, at)));
			instructions.add(cursor[0] + 1, lineOf(" ".repeat(indent) + rest));
			cursor = new int[]{cursor[0] + 1, indent};
			preservePosition = cursor[1];
		} else if(code == GLFW.GLFW_KEY_TAB) {
			if(lenString(instructions.get(cursor[0])) + 2 <= IC10Limits.MAX_LINE_CHARS)
				insertChar("  ");
		} else if(code == GLFW.GLFW_KEY_BACKSPACE) {
			backspace();
		} else if(code == GLFW.GLFW_KEY_DELETE) {
			if(cursor[1] < lenString(instructions.get(cursor[0]))) {
				cursor[1]++;
				backspace();
			} else if(cursor[0] + 1 < instructions.size()) {
				cursor = new int[]{cursor[0] + 1, 0};
				backspace();
			}
		} else {
			// printable keys arrive again as characters; the editor takes them all so nothing else reacts
			return true;
		}
		if(!shiftDown || !isArrowKey)
			selStart = new int[]{cursor[0], cursor[1]};
		return true;
	}

	private void backspace() {
		if(!posEquals(selStart, cursor)) {
			int[][] sorted = getSelectionSorted();
			int[] sst = sorted[0];
			int[] sen = sorted[1];
			ArrayList<ArrayList<Object>> backup = new ArrayList<>();
			for(int ln = sst[0]; ln <= sen[0]; ln++)
				backup.add(instructions.get(ln));
			int[] cursorBkp = cursor;
			deleteSelection(sst, sen);
			addUndo(new UndoDataReplace(sst[0], backup, asArrayList(instructions.get(sst[0])), cursorBkp, cursor));
		} else if(cursor[1] > 0) {
			String chr0 = substringSafely(toString(instructions.get(cursor[0])), cursor[1] - 1, cursor[1]);
			removeString(cursor[0], cursor[1], 1);
			boolean undoAppended = false;
			if(lastUndo instanceof UndoDataAddRemove addrmv && !addrmv.isAdd && undoCurrent == undoHistory.size() - 1) {
				if(addrmv.line == cursor[0] && addrmv.pos == cursor[1] + 1) {
					addrmv.str = chr0 + addrmv.str;
					addrmv.pos -= 1;
					undoAppended = true;
				}
			}
			if(!undoAppended)
				addUndo(new UndoDataAddRemove(cursor[0], cursor[1], chr0, false));
			cursor[1] = Math.max(cursor[1] - 1, 0);
			preservePosition = cursor[1];
		} else if(cursor[0] > 0) {
			int[] newcursor = {cursor[0] - 1, lenString(instructions.get(cursor[0] - 1))};
			String joined = toString(instructions.get(cursor[0] - 1)) + toString(instructions.get(cursor[0]));
			instructions.set(cursor[0] - 1, lineOf(joined));
			instructions.remove(cursor[0]);
			cursor = newcursor;
			preservePosition = cursor[1];
		}
	}

	/** Ctrl+V: the clipboard goes in at the cursor, over the selection, and is cut to the limits. */
	private void paste() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		if(text == null || text.isEmpty())
			return;
		text = text.replace("\r", "").replace("\t", "  ");
		String[] clipboard = text.split("\n", -1);
		ArrayList<ArrayList<Object>> backup = new ArrayList<>();
		if(!posEquals(selStart, cursor)) {
			int[][] sorted = getSelectionSorted();
			int[] sst = sorted[0];
			int[] sen = sorted[1];
			for(int ln = sst[0]; ln <= sen[0]; ln++)
				backup.add(instructions.get(ln));
			deleteSelection(sst, sen);
			cursor = new int[]{sst[0], sst[1]};
		} else
			backup.add(instructions.get(cursor[0]));
		int newLines = Math.max(1, Math.min(clipboard.length, IC10Limits.MAX_LINES - instructions.size() + 1));
		int startline = cursor[0];
		ArrayList<ArrayList<Object>> newinsns = new ArrayList<>();
		String strInsn = toString(instructions.get(cursor[0]));
		String pre = substringSafely(strInsn, 0, cursor[1]);
		String post = substringSafely(strInsn, cursor[1]);
		int sidePos = 0;
		for(int lno = 0; lno < newLines; lno++) {
			String s = clipboard[lno];
			sidePos = s.length();
			if(lno == newLines - 1)
				s = s + post;
			if(lno == 0) {
				instructions.set(cursor[0], lineOf(pre + s));
				sidePos += pre.length();
			} else
				instructions.add(cursor[0] + lno, lineOf(s));
			newinsns.add(instructions.get(cursor[0] + lno));
		}
		int[] cursorBefore = cursor;
		cursor = new int[]{cursor[0] + newLines - 1, Math.min(sidePos, IC10Limits.MAX_LINE_CHARS)};
		selStart = new int[]{cursor[0], cursor[1]};
		preservePosition = cursor[1];
		clampCursor();
		addUndo(new UndoDataReplace(startline, backup, newinsns, cursorBefore, new int[]{cursor[0], cursor[1]}));
	}
}
