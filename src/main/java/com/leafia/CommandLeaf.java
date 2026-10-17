package com.leafia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.custom_hbm.contents.torex.LCETorex;
import com.leafia.config.LeafiaFeatures;
import com.leafia.lib.LeafiaRef;
import com.leafia.passive.effects.LeafiaShakecam;
import com.llib.technical.LeafiaEase;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /hbmleaf}: the addon's debug command. Upstream: {@code com/leafia/CommandLeaf.java}, 860
 * lines, a 1.12.2 {@code CommandBase} that parses {@code String[] args} by hand. <b>Partial</b>: only
 * the three subcommands the torex and the camera shake need are here, as Brigadier.
 *
 * <pre>
 *   /hbmleaf torex statFac|statFacBale|statFacEndo|statFacDigamma &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;scale&gt; [sound]
 *   /hbmleaf shake ?
 *   /hbmleaf shake &lt;players&gt; [&lt;x&gt; &lt;y&gt; &lt;z&gt;] [params...]
 *   /hbmleaf eases
 * </pre>
 *
 * <p>Not here, and why: {@code visualizer} (the packet-trace debugger, {@code LeafiaDebug}),
 * {@code wand} ({@code ItemWandV} and the structural-integrity highlight packets), {@code field}
 * (a reflection-based field editor, dev environments only) and the class-keyed debug targets. None
 * is the torex's or the shake's, and each names a system that is not ported.
 *
 * <p>Two additions to upstream's text. {@code statFacDigamma} is a fourth palette
 * ({@code LCETorex.statFacDigamma} exists upstream and nothing in the command reached it), and
 * {@code eases} is upstream's own subcommand, kept because the shake's help text sends the player to
 * it. Upstream's {@code summon} variant is commented out there and is not carried. Permission level
 * 2 as upstream ({@code getRequiredPermissionLevel}).
 *
 * <p>The position is a block position, as upstream's {@code parseBlockPos(..., false)}: integers,
 * {@code ~} relative, and the torex is placed at the block's corner, not its centre.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CommandLeaf {

	private CommandLeaf() {}

	public static final String TOREX_USAGE = "/hbmleaf torex statFac|statFacBale|statFacEndo|statFacDigamma <x> <y> <z> <scale> [sound]";
	public static final String SHAKE_USAGE = "/hbmleaf shake ?\nOR /hbmleaf shake <player> [<x> <y> <z>] [params...]";

	private static final Style HEADER = Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE);

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		if(!LeafiaFeatures.torex) return;
		register(event.getDispatcher());
	}

	/** Builds the tree onto {@code dispatcher}. Public so a test can build it on a private dispatcher. */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

		LiteralArgumentBuilder<CommandSourceStack> torex = Commands.literal("torex");
		for(String name : new String[] {"statFac", "statFacBale", "statFacEndo", "statFacDigamma"}) {
			torex.then(Commands.literal(name)
					.then(Commands.argument("pos", BlockPosArgument.blockPos())
							.then(Commands.argument("scale", DoubleArgumentType.doubleArg())
									.executes(ctx -> torex(ctx, name, true))
									.then(Commands.argument("sound", BoolArgumentType.bool())
											.executes(ctx -> torex(ctx, name, BoolArgumentType.getBool(ctx, "sound")))))));
		}

		// the shake: <players> then either <pos> [params] or just [params]
		RequiredArgumentBuilder<CommandSourceStack, ?> params = Commands.argument("params", StringArgumentType.greedyString())
				.suggests(PARAM_SUGGESTIONS);
		RequiredArgumentBuilder<CommandSourceStack, ?> paramsAfterPos = Commands.argument("params", StringArgumentType.greedyString())
				.suggests(PARAM_SUGGESTIONS);
		LiteralArgumentBuilder<CommandSourceStack> shake = Commands.literal("shake")
				.then(Commands.literal("?").executes(CommandLeaf::shakeHelp))
				.then(Commands.argument("players", EntityArgument.players())
						.executes(ctx -> shake(ctx, false, false))
						.then(Commands.argument("pos", BlockPosArgument.blockPos())
								.executes(ctx -> shake(ctx, true, false))
								.then(paramsAfterPos.executes(ctx -> shake(ctx, true, true))))
						.then(params.executes(ctx -> shake(ctx, false, true))));

		dispatcher.register(Commands.literal("hbmleaf")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("eases").executes(CommandLeaf::eases))
				.then(torex)
				.then(shake));
	}

	// ----- torex ------------------------------------------------------------------------------

	private static int torex(CommandContext<CommandSourceStack> ctx, String which, boolean sound) throws CommandSyntaxException {
		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
		float scale = (float) DoubleArgumentType.getDouble(ctx, "scale");
		LCETorex torex = switch(which) {
		case "statFac" -> LCETorex.statFac(level, pos.getX(), pos.getY(), pos.getZ(), scale, sound);
		case "statFacBale" -> LCETorex.statFacBale(level, pos.getX(), pos.getY(), pos.getZ(), scale, sound);
		case "statFacEndo" -> LCETorex.statFacEndo(level, pos.getX(), pos.getY(), pos.getZ(), scale, sound);
		default -> LCETorex.statFacDigamma(level, pos.getX(), pos.getY(), pos.getZ(), scale, sound);
		};
		if(torex == null) {
			ctx.getSource().sendFailure(Component.literal("The torex is switched off (features.torex)"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Summoned a torex (" + which + ", scale " + torex.getScale() + ")"), true);
		return 1;
	}

	// ----- shake ------------------------------------------------------------------------------

	/** Upstream splits its argument array on spaces; so does this, and empty gives no parameters. */
	public static String[] splitParams(String raw) {
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
	}

	private static int shake(CommandContext<CommandSourceStack> ctx, boolean withPos, boolean withParams) throws CommandSyntaxException {
		Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
		BlockPos pos = withPos ? BlockPosArgument.getBlockPos(ctx, "pos") : null;
		String[] params = withParams ? splitParams(StringArgumentType.getString(ctx, "params")) : new String[0];
		for(ServerPlayer player : players) LeafiaShakecam.send(player, pos, params);
		return players.size();
	}

	private static int shakeHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean dark = false;
		source.sendSystemMessage(Component.literal("Many examples:").setStyle(HEADER));
		for(String example : new String[] {
				"/hbmleaf shake @a",
				"/hbmleaf shake @a ~ ~ ~",
				"/hbmleaf shake @a ~ ~ ~ duration*2",
				"/hbmleaf shake @a intensity=9 range=30 duration=15",
				"/hbmleaf shake @a type=smooth",
				"/hbmleaf shake @a type=smooth preset=RUPTURE",
				"/hbmleaf shake @a type=smooth preset=RUPTURE intensity*2",
				"/hbmleaf shake @a ~ ~ ~ type=smooth curve=60 intensity=20 duration=30"}) {
			source.sendSystemMessage(suggestion(example, dark));
			dark = !dark;
		}
		source.sendSystemMessage(Component.literal(""));
		source.sendSystemMessage(Component.literal("Available types:").setStyle(HEADER));
		source.sendSystemMessage(Component.literal("  simple | smooth"));
		source.sendSystemMessage(Component.literal(""));
		source.sendSystemMessage(Component.literal("Available presets:").setStyle(HEADER));
		boolean isDarkRow = false;
		for(LeafiaShakecam.Preset preset : LeafiaShakecam.Preset.values()) {
			source.sendSystemMessage(Component.literal("  " + preset.name()).setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true))
					.append(Component.literal(" (" + preset.paramString + ")").setStyle(Style.EMPTY.withColor(isDarkRow ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY))));
			isDarkRow = !isDarkRow;
		}
		source.sendSystemMessage(Component.literal(""));
		source.sendSystemMessage(Component.literal("Default parameters:").setStyle(HEADER));
		source.sendSystemMessage(Component.literal("  range=25 intensity=4 curve=2 speed=4 duration=5 ease=expoOut"));
		source.sendSystemMessage(Component.literal("  blurExponent=2 blurDulling=16 bloomExponent=6 bloomDulling=16"));
		source.sendSystemMessage(Component.literal(""));
		source.sendSystemMessage(Component.literal("Use /hbmleaf eases to see all available eases.").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
		return 1;
	}

	private static MutableComponent suggestion(String command, boolean dark) {
		return Component.literal("  " + command).setStyle(Style.EMPTY
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to try out")))
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
				.withColor(dark ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
	}

	private static int eases(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSystemMessage(Component.literal("Available eases:").setStyle(HEADER));
		for(String s : LeafiaEase.listEasesForCommands()) ctx.getSource().sendSystemMessage(Component.literal("  " + s));
		return 1;
	}

	/** Upstream's tab list for the shake's parameters: the types, the presets, every numeric with each operator, the eases. */
	public static List<String> paramSuggestions() {
		List<String> list = new ArrayList<>();
		list.add("type=simple");
		list.add("type=smooth");
		for(LeafiaShakecam.Preset preset : LeafiaShakecam.Preset.values()) list.add("preset=" + preset.name());
		for(String s : LeafiaShakecam.NUMERICS) {
			list.add(s + "=");
			list.add(s + "+");
			list.add(s + "-");
			list.add(s + "*");
			list.add(s + "/");
		}
		for(String s : LeafiaEase.listEasesForCommands()) list.add("ease=" + s);
		list.add("ease=none");
		return list;
	}

	private static final SuggestionProvider<CommandSourceStack> PARAM_SUGGESTIONS = (ctx, builder) -> suggestParams(builder);

	private static java.util.concurrent.CompletableFuture<Suggestions> suggestParams(SuggestionsBuilder builder) {
		// suggest for the last space-separated word, as a vanilla greedy string cannot
		String remaining = builder.getRemaining();
		int lastSpace = remaining.lastIndexOf(' ');
		SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastSpace + 1);
		String typed = word.getRemaining();
		for(String s : paramSuggestions()) if(s.startsWith(typed)) word.suggest(s);
		return word.buildFuture();
	}
}
