package com.llib.exceptions.messages;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ported from `com.llib.exceptions.messages.TextWarningLeafia`.
 *
 * <p>The addon's red {@code ERROR >>} chat prefix, used wherever it tells a player they did
 * something the mod cannot do.
 *
 * <p><b>Shape changed: this is a factory, not a component subclass.</b> Upstream extends
 * {@code TextComponentString}, which 1.21.1 has no equivalent of — {@code MutableComponent} is
 * built from a {@code ComponentContents} through a package-private constructor and cannot be
 * subclassed from outside the {@code net.minecraft.network.chat} package. So the ten upstream
 * call sites change from {@code new TextWarningLeafia(msg)} to {@link #of(String)}, and pass
 * the result to {@code sendSystemMessage} rather than 1.12.2's {@code sendMessage}.
 *
 * <p>The rendered text is unchanged, chevron for chevron: {@code ERROR } in the inherited
 * colour, a red {@code >}, a dark-red {@code >}, then the message in red.
 */
public final class TextWarningLeafia {

	private TextWarningLeafia() {}

	/** The warning line, ready to hand to {@code Player#sendSystemMessage}. */
	public static MutableComponent of(String message) {
		return Component.literal("ERROR ")
				.append(Component.literal(">").withStyle(ChatFormatting.RED))
				.append(Component.literal(">").withStyle(ChatFormatting.DARK_RED))
				.append(Component.literal(" " + message).withStyle(ChatFormatting.RED));
	}

	/**
	 * The same line as a raw string with formatting codes in it, which is upstream's
	 * {@code toString}. Kept because a log line is not a chat component and upstream uses both.
	 */
	public static String asFormattedString(String message) {
		return "ERROR " + ChatFormatting.RED + ">" + ChatFormatting.DARK_RED + ">"
				+ ChatFormatting.RED + " " + message;
	}
}
