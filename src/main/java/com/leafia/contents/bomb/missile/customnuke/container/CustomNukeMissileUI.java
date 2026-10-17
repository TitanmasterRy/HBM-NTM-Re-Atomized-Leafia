package com.leafia.contents.bomb.missile.customnuke.container;

import com.hbm.inventory.gui.GuiInfoContainer;
import com.leafia.lib.LeafiaRef;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem;
import com.leafia.contents.bomb.missile.customnuke.CustomNukeMissileItem.CustomNukeMissileInventory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The custom nuke missile's screen (D-3022): the base custom nuke's, as NTM-CE drew it - eight stage boxes along
 * y = 88 (TNT, nuclear, thermonuclear, balefire, then schrabidium at 88, solinium at 106 and "ice cream" at 142),
 * each with a hover readout, and the salted stage as a bar across the thermonuclear box.
 *
 * <p>Upstream: {@code com/leafia/contents/bomb/missile/customnuke/container/CustomNukeMissileUI.java}, on NTM-CE's
 * {@code gunBombSchematic.png} - whose stage icons and salted bar sit where the 1.7.10 sheet the base mod uses has
 * none, so the sheet is NTM-CE's, read out of the vendored jar as D-3002 read the emitter's. The <em>strongest</em>
 * stage is lit in the brighter column (u = 194) and every other non-zero stage in the dim one (u = 176); the salted
 * bar is bright while thermonuclear or nuclear is the strongest stage. The readouts are upstream's English literals,
 * which it never localised, with Java's own float to-string; the title is the stack's name.
 */
@OnlyIn(Dist.CLIENT)
public class CustomNukeMissileUI extends GuiInfoContainer<CustomNukeMissileContainer> {

	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "textures/gui/bomb/custom_nuke_missile.png");

	public CustomNukeMissileUI(CustomNukeMissileContainer menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 222;
		this.inventoryLabelY = this.imageHeight - 96 + 2;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = this.imageWidth / 2 - this.font.width(this.title) / 2;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		CustomNukeMissileInventory inventory = this.menu.nukeBoy;
		String[] text;

		text = new String[] { ChatFormatting.YELLOW + "Conventional Explosives (Radius " + inventory.tnt + "/" + CustomNukeMissileItem.MAX_TNT + ")",
				"Caps at " + CustomNukeMissileItem.MAX_TNT,
				"N²-like above level 75",
				ChatFormatting.ITALIC + "\"Goes boom\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 16, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Nuclear (Radius " + inventory.nuke + "(" + inventory.getNukeAdj() + ")/" + CustomNukeMissileItem.MAX_NUKE + ")",
				"Requires TNT level 16",
				"Caps at " + CustomNukeMissileItem.MAX_NUKE,
				"Has fallout",
				ChatFormatting.ITALIC + "\"Now I am become death, destroyer of worlds.\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 34, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Thermonuclear (Radius " + inventory.hydro + "(" + inventory.getHydroAdj() + ")/" + CustomNukeMissileItem.MAX_HYDRO + ")",
				"Requires nuclear level 100",
				"Caps at " + CustomNukeMissileItem.MAX_HYDRO,
				"Reduces added fallout by salted stage by 75%",
				ChatFormatting.ITALIC + "\"And for my next trick, I'll make",
				ChatFormatting.ITALIC + "the island of Elugelab disappear!\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 52, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Balefire (Radius " + inventory.bale + "/(" + inventory.getBaleAdj() + ")/" + CustomNukeMissileItem.MAX_BALE + ")",
				"Requires nuclear level 50",
				"Caps at " + CustomNukeMissileItem.MAX_BALE,
				ChatFormatting.ITALIC + "\"Antimatter, Balefire, whatever.\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 70, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Salted (Radius " + inventory.dirty + "/" + CustomNukeMissileItem.MAX_DIRTY + ")",
				"Extends fallout of nuclear and",
				"thermonuclear stages",
				"Caps at " + CustomNukeMissileItem.MAX_DIRTY,
				ChatFormatting.ITALIC + "\"Not to be confused with tablesalt.\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 53, topPos + 83, 25, 5, mouseX, mouseY, text);
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 53, topPos + 106, 25, 5, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Schrabidium (Radius " + inventory.schrab + "(" + inventory.getSchrabAdj() + ")/" + CustomNukeMissileItem.MAX_SCHRAB + ")",
				"Requires nuclear level 50",
				"Caps at " + CustomNukeMissileItem.MAX_SCHRAB,
				ChatFormatting.ITALIC + "\"For the hundredth time,",
				ChatFormatting.ITALIC + "you can't bypass these caps!\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 88, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Solinium (Radius " + inventory.sol + "(" + inventory.getSolAdj() + ")/" + CustomNukeMissileItem.MAX_SOL + ")",
				"Requires nuclear level 25",
				"Caps at " + CustomNukeMissileItem.MAX_SOL,
				ChatFormatting.ITALIC + "\"For the hundredth time,",
				ChatFormatting.ITALIC + "you can't bypass these caps!\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 106, topPos + 88, 18, 18, mouseX, mouseY, text);

		text = new String[] { ChatFormatting.YELLOW + "Ice cream (Level " + inventory.euph + "/" + CustomNukeMissileItem.MAX_EUPH + ")",
				"Requires schrabidium and solinium level 1",
				"Caps at " + CustomNukeMissileItem.MAX_EUPH,
				ChatFormatting.ITALIC + "\"Probably not ice cream but the label came off.\"" };
		this.drawCustomInfoStat(graphics, mouseX, mouseY, leftPos + 142, topPos + 88, 18, 18, mouseX, mouseY, text);

		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		CustomNukeMissileInventory inventory = this.menu.nukeBoy;

		byte best = 10;
		if(inventory.euph > 0) {
			graphics.blit(TEXTURE, leftPos + 142, topPos + 89, 176, 108, 18, 18);
			best = 9;
		}
		best = stage(graphics, inventory.sol, best, 106, 90, 8);
		best = stage(graphics, inventory.schrab, best, 88, 72, 7);
		best = stage(graphics, inventory.bale, best, 70, 54, 6);
		best = stage(graphics, inventory.hydro, best, 52, 36, 5);
		best = stage(graphics, inventory.nuke, best, 34, 18, 4);
		best = stage(graphics, inventory.tnt, best, 16, 0, 3);

		if(inventory.dirty > 0) {
			if(best < 6 && best > 3) graphics.blit(TEXTURE, leftPos + 53, topPos + 83, 201, 125, 25, 29);
			else graphics.blit(TEXTURE, leftPos + 53, topPos + 83, 176, 125, 25, 29);
		}
	}

	/** One stage box: lit bright (u 194) if it is the strongest non-zero stage so far, dim (u 176) otherwise. */
	private byte stage(GuiGraphics graphics, float level, byte best, int x, int v, int rank) {
		if(level <= 0) return best;
		if(best == 10) {
			graphics.blit(TEXTURE, leftPos + x, topPos + 89, 194, v, 18, 18);
			return (byte) rank;
		}
		graphics.blit(TEXTURE, leftPos + x, topPos + 89, 176, v, 18, 18);
		return best;
	}
}
