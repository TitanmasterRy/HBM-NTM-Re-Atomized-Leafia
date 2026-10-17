package com.leafia.contents.machines.controlpanel.ic10;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * A program as it is saved in a panel (CCP-IC10, D-3073): the byte format of Leafia's {@code NodeIC10.writeToNBT}, gzipped, and
 * the checks that a program read from NBT is within {@link IC10Limits}.
 *
 * <h2>The format</h2>
 * A program is a list of lines and a line is a list of tokens, each a {@code String} (a word, a comment, a label) or an
 * {@code Integer} (that many spaces). It is written as one byte stream: the bytes of a string, {@code -2} between two strings
 * that touch, {@code -n-2} for {@code n} spaces and {@code -1} at the end of a line. Leafia gzips it under the key
 * {@code compressed} and falls back to the raw bytes under {@code instructions}; both are read.
 *
 * <p>Three things in the format cannot hold what the editor could once produce, and are fixed without changing the format:
 * a run of more than 126 spaces overflowed the byte and came back as garbage (it is written as several tokens now, which the
 * editor joins again on the next edit), a zero-space token was written as 126 spaces (it is not written), and a character above
 * U+007F is several negative bytes that the reader took for spaces and separators (the editor takes printable ASCII only, and
 * this writes anything else as {@code ?}).
 *
 * <h2>Hostile input</h2>
 * A saved panel is whatever the client sent, so {@link #problem} names a program that is too long, too wide, or a gzip that
 * inflates past {@link IC10Limits#MAX_PROGRAM_BYTES}; {@link ControlPanelGuard} refuses the panel for it (through the hook
 * {@code AddonNodesRegister} installs). {@link #decode} on its own never builds more than the limits allow.
 */
public final class IC10Program {

	private IC10Program() {}

	/** The most spaces one byte can say. */
	private static final int MAX_RUN = 126;

	// ------------------------------------------------------------------ writing

	public static byte[] encode(List<ArrayList<Object>> instructions) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for(ArrayList<Object> instruction : instructions) {
			boolean lastWasString = false;
			for(Object object : instruction) {
				if(object instanceof String s) {
					if(lastWasString)
						out.write((byte) -2);
					out.writeBytes(ascii(s).getBytes(StandardCharsets.ISO_8859_1));
					lastWasString = true;
				} else if(object instanceof Number n) {
					int spaces = n.intValue();
					while(spaces > 0) {
						int run = Math.min(spaces, MAX_RUN);
						out.write((byte) (-run - 2));
						spaces -= run;
					}
					lastWasString = false;
				}
			}
			out.write((byte) -1);
		}
		return out.toByteArray();
	}

	/** {@code s} with everything but printable ASCII replaced by {@code ?}. */
	public static String ascii(String s) {
		StringBuilder b = null;
		for(int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if(c < 32 || c > 126) {
				if(b == null)
					b = new StringBuilder(s);
				b.setCharAt(i, '?');
			}
		}
		return b == null ? s : b.toString();
	}

	/** Writes the program into a node's tag under {@code compressed}. */
	public static void write(CompoundTag tag, List<ArrayList<Object>> instructions) {
		byte[] data = encode(instructions);
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try(GZIPOutputStream gz = new GZIPOutputStream(bos)) {
				gz.write(data);
			}
			tag.putByteArray("compressed", bos.toByteArray());
		} catch(IOException e) {
			tag.putByteArray("instructions", data);
		}
	}

	// ------------------------------------------------------------------ reading

	/** True if the tag holds a program under either key. */
	public static boolean has(CompoundTag tag) {
		return tag.contains("compressed", Tag.TAG_BYTE_ARRAY) || tag.contains("instructions", Tag.TAG_BYTE_ARRAY);
	}

	/** The decoded bytes of the program in a tag, or null if it has none or it is not a valid gzip within the limit. */
	@Nullable
	public static byte[] bytes(CompoundTag tag) {
		if(tag.contains("compressed", Tag.TAG_BYTE_ARRAY)) {
			byte[] z = tag.getByteArray("compressed");
			return z.length > IC10Limits.MAX_COMPRESSED ? null : gunzip(z);
		}
		if(tag.contains("instructions", Tag.TAG_BYTE_ARRAY)) {
			byte[] raw = tag.getByteArray("instructions");
			return raw.length > IC10Limits.MAX_PROGRAM_BYTES ? null : raw;
		}
		return null;
	}

	/** Inflates a gzip, giving up (null) the moment it passes {@link IC10Limits#MAX_PROGRAM_BYTES}. */
	@Nullable
	public static byte[] gunzip(byte[] data) {
		try(GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int total = 0;
			int n;
			while((n = gis.read(buffer)) >= 0) {
				total += n;
				if(total > IC10Limits.MAX_PROGRAM_BYTES)
					return null;
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		} catch(IOException e) {
			return null;
		}
	}

	/**
	 * The program in a byte stream. Lines beyond {@link IC10Limits#MAX_LINES} are dropped and a line past
	 * {@link IC10Limits#MAX_LINE_CHARS} is cut, so what comes back is always within the limits; {@link #problem} is the strict
	 * check that says whether anything was lost.
	 */
	public static ArrayList<ArrayList<Object>> decode(byte[] data) {
		ArrayList<ArrayList<Object>> lines = new ArrayList<>();
		ArrayList<Object> instruction = new ArrayList<>();
		ByteArrayOutputStream cache = new ByteArrayOutputStream();
		boolean writingString = false;
		int width = 0;
		for(byte b : data) {
			if(b < 0 && writingString) {
				String s = cache.toString(StandardCharsets.ISO_8859_1);
				if(width + s.length() > IC10Limits.MAX_LINE_CHARS)
					s = s.substring(0, Math.max(0, IC10Limits.MAX_LINE_CHARS - width));
				if(!s.isEmpty())
					instruction.add(s);
				width += s.length();
				cache.reset();
				writingString = false;
			}
			if(b == -1) {
				if(lines.size() < IC10Limits.MAX_LINES)
					lines.add(instruction);
				instruction = new ArrayList<>();
				width = 0;
			} else if(b < -2) {
				int spaces = -(b + 2);
				if(width + spaces > IC10Limits.MAX_LINE_CHARS)
					spaces = Math.max(0, IC10Limits.MAX_LINE_CHARS - width);
				if(spaces > 0)
					instruction.add(spaces);
				width += spaces;
			} else if(b >= 0) {
				writingString = true;
				cache.write(b);
			}
		}
		return lines;
	}

	/** Why the program in a stream is beyond the limits, or null if nothing would be lost reading it. */
	@Nullable
	public static String lengthProblem(byte[] data) {
		int lines = 0;
		int width = 0;
		for(byte b : data) {
			if(b == -1) {
				if(++lines > IC10Limits.MAX_LINES)
					return "IC10 program has more than " + IC10Limits.MAX_LINES + " lines";
				width = 0;
			} else if(b < -2) {
				width += -(b + 2);
			} else if(b >= 0) {
				width++;
			}
			if(width > IC10Limits.MAX_LINE_CHARS)
				return "IC10 program has a line longer than " + IC10Limits.MAX_LINE_CHARS + " characters";
		}
		return null;
	}

	/**
	 * Why this IC10 node's tag must not be read, or null. The hook {@code ControlPanelGuard} runs on every {@code leafia_ic10}
	 * node of a panel a client sends (and one loaded from disk): the program's size and shape, and the size of the saved state.
	 */
	@Nullable
	public static String problem(CompoundTag node) {
		if(has(node)) {
			byte[] data = bytes(node);
			if(data == null)
				return "IC10 program is larger than " + IC10Limits.MAX_COMPRESSED + " bytes compressed or " + IC10Limits.MAX_PROGRAM_BYTES + " decoded, or is not a gzip";
			String why = lengthProblem(data);
			if(why != null)
				return why;
		}
		CompoundTag state = node.getCompound("state");
		if(state.getCompound("registry").size() > IC10State.MAX_SAVED_REGISTERS)
			return "IC10 state holds too many registers";
		if(state.getCompound("stack").size() > IC10State.MAX_SAVED_STACK)
			return "IC10 state holds too many stack entries";
		if(state.getCompound("pulseQueue").size() > IC10Limits.MAX_OUTPUT_KEYS)
			return "IC10 state holds too many pulses";
		for(String section : new String[]{"registry", "stack", "pulseQueue"}) {
			CompoundTag t = state.getCompound(section);
			for(String key : t.getAllKeys()) {
				if(key.length() > IC10Limits.MAX_TEXT || (t.contains(key, Tag.TAG_STRING) && t.getString(key).length() > IC10Limits.MAX_TEXT))
					return "IC10 state holds text longer than " + IC10Limits.MAX_TEXT;
			}
		}
		if(state.getString("error").length() > IC10Limits.MAX_PRINT_CHARS)
			return "IC10 state holds a long error";
		return null;
	}

	/** The lines of a program as plain text, one line per row (for tests and for the editor's copy). */
	public static String text(List<ArrayList<Object>> instructions) {
		StringBuilder b = new StringBuilder();
		for(ArrayList<Object> line : instructions) {
			for(Object o : line) {
				if(o instanceof Number n)
					b.append(" ".repeat(Math.max(0, n.intValue())));
				else
					b.append(o);
			}
			b.append('\n');
		}
		return b.toString();
	}
}
