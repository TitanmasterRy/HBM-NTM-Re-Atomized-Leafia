package com.leafia.contents.machines.controlpanel.core;

/**
 * Something in a node that takes typed text: a socket's default value, a text box (CCP-NODE-EDITOR, D-3067). Adapted from
 * NTM-CE's {@code ITypableNode}.
 *
 * <p>CE's {@code keyTyped(char, int)} took an LWJGL key code, which a common class cannot name (§2.2), and 1.21.1 delivers a
 * key and the character it typed as two events anyway. So the editor translates: a printable character is {@link #typeChar},
 * Backspace is {@link #backspace}, and Enter is {@link #stopTyping} (CE's {@code KEY_RETURN} branch). The editor's own
 * {@code NodeSystemEditor} keeps the one box that has the focus and routes to it.
 *
 * <p>What is typed is client-side text until {@link #stopTyping} commits it, and a commit goes through the same save path as
 * every other edit, so the server's trust boundary (D-3063) sees the result and not the keystrokes. The length ceiling here is a
 * kindness to the player (a runaway paste cannot fill the packet), not the server's check.
 */
public interface ITypableNode {

	/** The longest text a box takes. CE had no limit; a socket's default is a number, a word or a format string. */
	int MAX_TYPED = 512;

	/** minX, minY, maxX, maxY of the area that starts typing when clicked, in node space. */
	float[] getValueBox();

	boolean isTyping();

	void startTyping();

	/** Commits what was typed and leaves typing. */
	void stopTyping();

	/** Appends a character to what is being typed. Control characters and anything past {@link #MAX_TYPED} are ignored. */
	void typeChar(char c);

	/** Deletes the last typed character. */
	void backspace();

	/** True for a character a box accepts: anything printable, as 1.21.1's own text fields would. */
	static boolean isTypable(char c) {
		return c >= 32 && c != 127 && !Character.isISOControl(c);
	}
}
