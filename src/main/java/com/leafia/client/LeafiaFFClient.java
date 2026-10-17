package com.leafia.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.util.BobMathUtil;
import com.leafia.contents.fluids.ff.LeafiaFF;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem;
import com.leafia.contents.gear.utility.fuzzy.FuzzyIdentifierItem.FuzzyIdentifierPacket;
import com.leafia.dev.custompacket.LeafiaCustomPacket;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.leafia.lib.LeafiaRef;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;

/**
 * The client half of the Forge-fluid bridge: how an {@code ff/} fluid looks, and the tank drawing
 * and tooltips the addon's Forge-tank screens use.
 *
 * <p>Upstream: the FF half of {@code com/leafia/dev/LeafiaClientUtil.java} ({@code drawLiquid},
 * {@code drawLogLiquid}, {@code renderTankInfo}, {@code addFluidInfo}, {@code getTextureFromFluid})
 * and its NTM-tank {@code renderTankInfo}, which is where the line {@code BullshitMixinFluidTankNTM}
 * rewrote and the click {@code MixinFluidTankNTM} injected reach the addon's own screens.
 * {@code LeafiaClientUtil} is an accumulator (its other half is unrelated), so these live here and
 * that port can delegate to them.
 *
 * <h2>Looks</h2>
 *
 * NTM-CE drew every Forge fluid it registered with one of three grey textures of its own
 * ({@code fluid_default_still}, {@code fluid_viscous_default_still}, {@code gas_default}) tinted
 * with the NTM colour. Here every {@code ff/} fluid uses vanilla's water textures, which are grey
 * for the same reason (the biome tints them), tinted with the same colour - no art shipped, and a
 * visual difference only. Seeded fluids keep their world fluid's own extension.
 *
 * <h2>Coordinates</h2>
 *
 * {@link #drawTank} takes the tank's <b>bottom</b>-left corner, as the base mod's
 * {@code GUIElements.drawFluidTank} does, so an addon screen draws both kinds the same way.
 * Upstream's "PUT 28 ON Y OFFSET" arithmetic does not survive. The tooltip rectangle is upstream's
 * half-open test: left edge in, right out, top out, bottom in.
 */
@EventBusSubscriber(modid = LeafiaRef.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LeafiaFFClient {

	private LeafiaFFClient() {}

	private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
	private static final ResourceLocation FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

	@SubscribeEvent
	public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
		for(LeafiaFF.Entry e : LeafiaFF.entries()) {
			if(e.seeded || e.type == null) continue;
			int tint = colour(e.ntm);
			event.registerFluidType(new IClientFluidTypeExtensions() {
				@Override
				public ResourceLocation getStillTexture() {
					return STILL;
				}

				@Override
				public ResourceLocation getFlowingTexture() {
					return FLOWING;
				}

				@Override
				public int getTintColor() {
					return tint;
				}
			}, e.type.get());
		}
	}

	/** An NTM fluid's colour as opaque ARGB, white if it has none - {@code ItemNTMFBottle}'s rule. */
	public static int colour(FluidType ntm) {
		int color = ntm.getColor();
		return 0xFF000000 | (color < 0 ? 0xFFFFFF : color);
	}

	/** The tint a stack is drawn with: its fluid type's client extension. */
	public static int colour(FluidStack stack) {
		return stack.isEmpty() ? 0xFFFFFFFF : IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
	}

	@Nullable
	public static TextureAtlasSprite sprite(FluidStack stack) {
		if(stack.isEmpty()) return null;
		ResourceLocation still = IClientFluidTypeExtensions.of(stack.getFluid()).getStillTexture(stack);
		if(still == null) return null;
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Upstream's {@code drawLiquid}/{@code drawLogLiquid}: the fluid's still texture tiled across the
	 * filled part of a rectangle and tinted, over an opaque backing of the tint (the texture alone is 70%
	 * opaque; see the comment in the body).
	 *
	 * @param x           left edge
	 * @param y           <b>bottom</b> edge, as {@code GUIElements.drawFluidTank}
	 * @param orientation 0 fills upward, 1 fills rightward
	 * @param log         upstream's log scale: {@code size * ln(amount) / ln(capacity)}
	 */
	public static void drawTank(GuiGraphics graphics, FluidStack stack, int capacity, int x, int y, int width, int height,
			int orientation, boolean log) {
		if(stack.isEmpty() || capacity <= 0) return;
		TextureAtlasSprite sprite = sprite(stack);
		if(sprite == null) return;

		int size = orientation == 0 ? height : width;
		int level = fillLevel(stack.getAmount(), capacity, size, log);
		if(level <= 0) return;

		int top = y - height;
		int drawW = width, drawH = height, left = x;
		if(orientation == 0) {
			top += height - level;
			drawH = level;
		} else {
			drawW = level;
		}

		int c = colour(stack);
		float a = (c >>> 24 & 0xFF) / 255F, r = (c >> 16 & 0xFF) / 255F, g = (c >> 8 & 0xFF) / 255F, b = (c & 0xFF) / 255F;

		graphics.enableScissor(left, top, left + drawW, top + drawH);
		// Vanilla's water texture is 70% opaque (alpha 180), so on its own the screen's gauge marks show through
		// the fluid, which no NTM tank's (opaque) texture does. An opaque backing in the fluid's own tint, under
		// the sprite, makes it read as a fluid of that colour (D-3015; first seen on the salt separator's screen).
		graphics.fill(left, top, left + drawW, top + drawH, 0xFF000000 | (c & 0xFFFFFF));
		for(int px = 0; px < drawW; px += 16)
			for(int py = drawH; py > 0; py -= 16)
				graphics.blit(left + px, top + py - 16, 0, 16, 16, sprite, r, g, b, a == 0 ? 1F : a);
		graphics.disableScissor();
	}

	public static void drawTank(GuiGraphics graphics, IFluidTank tank, int x, int y, int width, int height) {
		drawTank(graphics, tank.getFluid(), tank.getCapacity(), x, y, width, height, 0, false);
	}

	/** How many pixels of {@code size} a tank fills: upstream's two formulae, truncated as upstream. */
	public static int fillLevel(int amount, int capacity, int size, boolean log) {
		if(amount <= 0 || capacity <= 0) return 0;
		if(log) return (int) (size * (Math.log(amount) / Math.log(capacity)));
		return (int) ((double) amount / (double) capacity * size);
	}

	// ------------------------------------------------------------------ tooltips

	/**
	 * A hook for what a fluid's NBT adds to its tooltip. The LFTR's salt is the one upstream case:
	 * its {@code heat} raises the shown temperature and its mixture adds a block of lines
	 * ({@code MSRTEBase.nbtProtocol}/{@code readMixture}). That batch adds a hook here rather than
	 * this class naming a reactor that is not ported.
	 */
	public interface InfoHook {
		/** Degrees to add to the shown temperature. */
		default int heat(FluidStack stack, FluidType ntm) {
			return 0;
		}

		/** Lines to add after the traits. */
		default void addInfo(FluidStack stack, FluidType ntm, List<String> lines, String prefix) {}
	}

	public static final List<InfoHook> INFO_HOOKS = new ArrayList<>();

	/**
	 * Upstream's FF {@code addFluidInfo}: the temperature line coloured by band (hidden at 27 °C,
	 * a Forge fluid's default 300 K), then the NTM fluid's traits if it maps to one, then any hook's
	 * lines, then the hold-shift hint. Upstream's {@code wand_v} tag dump is a developer tool and is
	 * not here.
	 */
	public static void addFluidInfo(FluidStack stack, List<String> lines, String prefix, boolean showHidden) {
		if(stack.isEmpty()) return;
		FluidType ntm = LeafiaFF.fromFF(stack);
		boolean mapped = ntm != Fluids.NONE;

		int temp = stack.getFluidType().getTemperature(stack) - 273;
		if(mapped) for(InfoHook hook : INFO_HOOKS) temp += hook.heat(stack, ntm);
		if(temp != 27) lines.add(prefix + temperatureColour(temp) + temp + "°C");

		boolean hasHidden = false;
		if(mapped) {
			List<String> hidden = new ArrayList<>();
			for(Class<? extends FluidTrait> clazz : FluidTrait.traitList) {
				FluidTrait trait = ntm.getTrait(clazz);
				if(trait != null) {
					trait.addInfo(lines);
					if(showHidden) trait.addInfoHidden(lines);
					trait.addInfoHidden(hidden);
				}
			}
			hasHidden = !hidden.isEmpty();
			for(InfoHook hook : INFO_HOOKS) hook.addInfo(stack, ntm, lines, prefix);
		}
		if(hasHidden && !showHidden) {
			lines.add(ChatFormatting.DARK_GRAY + "" + ChatFormatting.ITALIC + "Hold <" + ChatFormatting.YELLOW + ""
					+ ChatFormatting.ITALIC + "LSHIFT" + ChatFormatting.DARK_GRAY + "" + ChatFormatting.ITALIC + "> to display more info");
		}
	}

	/** Upstream's eight temperature bands. */
	public static String temperatureColour(int celsius) {
		if(celsius < -130) return "§3";
		if(celsius < 0) return "§b";
		if(celsius < 100) return "§e";
		if(celsius < 300) return "§6";
		if(celsius < 1000) return "§c";
		if(celsius < 3000) return "§4";
		if(celsius < 20000) return "§5";
		return "§d";
	}

	private static boolean over(int mouseX, int mouseY, int x, int y, int width, int height) {
		return x <= mouseX && x + width > mouseX && y < mouseY && y + height >= mouseY;
	}

	private static boolean lastClicked = false;

	/**
	 * Upstream's FF {@code renderTankInfo}: name, amount and fluid info for a NeoForge stack while the
	 * mouse is over the tank, and the fuzzy identifier's click. {@code fallback} is the fluid an empty
	 * tank is labelled with (upstream's {@code Fluid} argument); null labels it "None".
	 */
	public static void renderTankInfo(LCEGuiInfoContainer<?> gui, GuiGraphics graphics, int mouseX, int mouseY,
			int x, int y, int width, int height, FluidStack stack, int capacity, @Nullable FluidStack fallback) {
		if(!over(mouseX, mouseY, x, y, width, height)) return;
		List<String> lines = new ArrayList<>();
		FluidStack shown = !stack.isEmpty() ? stack : fallback != null && !fallback.isEmpty() ? fallback.copyWithAmount(1) : FluidStack.EMPTY;
		if(!shown.isEmpty()) {
			lines.add(shown.getHoverName().getString());
			lines.add(LeafiaFF.amountLine(stack.getAmount(), capacity, false));
			addFluidInfo(shown, lines, "", Screen.hasShiftDown());
			click(gui, LeafiaFF.fromFF(shown), shown.getHoverName());
		} else {
			// Upstream's "desc.none" is defined in neither upstream's lang nor the base mod's; NONE's own name is.
			lines.add(Fluids.NONE.getLocalizedName());
			lines.add(LeafiaFF.amountLine(0, capacity, false));
		}
		gui.drawFluidInfo(graphics, lines, mouseX, mouseY);
		lastClicked = gui.clickDown;
	}

	public static void renderTankInfo(LCEGuiInfoContainer<?> gui, GuiGraphics graphics, int mouseX, int mouseY,
			int x, int y, int width, int height, IFluidTank tank) {
		renderTankInfo(gui, graphics, mouseX, mouseY, x, y, width, height, tank.getFluid(), tank.getCapacity(), null);
	}

	/**
	 * Upstream's NTM {@code renderTankInfo}, for a base-mod tank on an addon screen: the base mod's
	 * tooltip with the amount line from {@link LeafiaFF#amountLine} (the line
	 * {@code BullshitMixinFluidTankNTM} rewrote) and the fuzzy identifier's click
	 * ({@code MixinFluidTankNTM}'s injection). The base mod's own screens are reached by
	 * {@code LeafiaFuzzyScreenClient}, through the base's recorded tank rectangles (D-3041).
	 */
	public static void renderTankInfo(LCEGuiInfoContainer<?> gui, GuiGraphics graphics, int mouseX, int mouseY,
			int x, int y, int width, int height, com.hbm.inventory.fluid.tank.FluidTank tank, boolean sludgeUnits) {
		if(!over(mouseX, mouseY, x, y, width, height)) return;
		List<String> lines = new ArrayList<>();
		lines.add(tank.getTankType().getLocalizedName());
		lines.add(LeafiaFF.amountLine(tank.getFill(), tank.getMaxFill(), sludgeUnits));
		if(tank.getPressure() != 0) {
			lines.add(ChatFormatting.RED + "Pressure: " + tank.getPressure() + " PU");
			lines.add((BobMathUtil.getBlink() ? ChatFormatting.RED : ChatFormatting.DARK_RED) + "Pressurized, use compressor!");
		}
		click(gui, tank.getTankType(), Component.literal(tank.getTankType().getLocalizedName()));
		tank.getTankType().addInfo(lines, Screen.hasShiftDown());
		gui.drawFluidInfo(graphics, lines, mouseX, mouseY);
		lastClicked = gui.clickDown;
	}

	/** A fresh click on a tank with the fuzzy identifier on the cursor sets it to that tank's fluid. */
	private static void click(LCEGuiInfoContainer<?> gui, FluidType type, Component name) {
		if(lastClicked || !gui.clickDown || type == Fluids.NONE) return;
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) return;
		ItemStack carried = mc.player.containerMenu.getCarried();
		if(carried.isEmpty() || !(carried.getItem() instanceof FuzzyIdentifierItem)) return;
		sendFuzzyClick(type, name);
	}

	/**
	 * Tells the server to set the carried identifier to {@code type}, and says so in chat. The one place the click is sent from: the
	 * addon's own screens' tank tooltips and the listener for the base mod's screens ({@code LeafiaFuzzyScreenClient}, D-3041).
	 */
	public static void sendFuzzyClick(FluidType type, Component name) {
		Minecraft mc = Minecraft.getInstance();
		if(mc.player == null) return;
		FuzzyIdentifierPacket packet = new FuzzyIdentifierPacket();
		packet.fluidRsc = type.getName();
		LeafiaCustomPacket.__start(packet).__sendToServer();
		mc.player.displayClientMessage(Component.translatable("item.fuzzy_identifier.message", name)
				.withStyle(ChatFormatting.YELLOW), false);
	}
}
