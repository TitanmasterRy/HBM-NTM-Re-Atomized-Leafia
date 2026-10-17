package com.llib.exceptions;

/**
 * Ported from `com.llib.exceptions.LeafiaDevFlaw`.
 *
 * <p>The addon's "this is a bug in my own code" exception, named by 59 upstream files — more
 * than any other class in {@code com.llib}. The leaf emoji prefix is upstream's own signature
 * on the message, kept so a crash report from this port is greppable against theirs.
 *
 * <p>{@link #appendStackTrace} concatenates the wrapped throwable's frames onto this one's
 * rather than using {@code initCause}. That loses the "Caused by:" section a reader expects,
 * and it is deliberate upstream: the point is one flat trace running from the failure down
 * through the wrapper. Kept as-is — changing it would change what every one of those 59 call
 * sites prints.
 */
public class LeafiaDevFlaw extends RuntimeException {

	public LeafiaDevFlaw(String s) {
		super("\uD83C\uDF3F" + s); // super("\uE05E\u8AFA"+s);
	}

	public LeafiaDevFlaw(String s, Throwable e) {
		this(s + ": " + e.getMessage());
		appendStackTrace(e);
	}

	public LeafiaDevFlaw(Throwable e) {
		this(e.toString());
		appendStackTrace(e);
	}

	public LeafiaDevFlaw appendStackTrace(Throwable e) {
		StackTraceElement[] stack = new StackTraceElement[e.getStackTrace().length + this.getStackTrace().length];
		System.arraycopy(this.getStackTrace(), 0, stack, 0, this.getStackTrace().length);
		System.arraycopy(e.getStackTrace(), 0, stack, this.getStackTrace().length, e.getStackTrace().length);
		this.setStackTrace(stack);
		return this;
	}
}
