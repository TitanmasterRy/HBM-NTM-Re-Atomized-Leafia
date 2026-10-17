package com.leafia.contents.machines.reactors.pwr.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.hbm.inventory.gui.element.GUIElements;
import com.hbm.util.Tuple.Pair;
import com.hbm.util.i18n.I18nUtil;
import com.leafia.lib.LeafiaRef;
import com.leafia.client.LeafiaFFClient;
import com.leafia.contents.machines.reactors.pwr.PWRData;
import com.leafia.contents.machines.reactors.pwr.blocks.components.PWRComponentEntity;
import com.leafia.contents.machines.reactors.pwr.blocks.components.control.PWRControlTE;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalTE;
import com.leafia.contents.machines.reactors.pwr.container.PWRTerminalMenu.RemoteSlot;
import com.leafia.dev.container_utility.LeafiaPacket;
import com.leafia.dev.gui.LCEGuiInfoContainer;
import com.llib.exceptions.LeafiaDevFlaw;
import com.llib.group.LeafiaSet;
import com.llib.math.range.RangeInt;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The PWR terminal: the reactor drawn from above as the terminal's front sees it, a slot on every fuel
 * column, a lamp on every control rod, a slider per rod group and a master slider, the coolant tanks, and -
 * with an integrated boiler - the boiler's tanks and its compression switch.
 *
 * <p>Upstream: {@code com/leafia/contents/machines/reactors/pwr/container/PWRTerminalUI.java} (736 lines), the
 * addon's largest screen. Every texture rectangle, offset and colour is upstream's; so are the layout
 * rules, read in {@link #init}: the grid is scaled to fit 176 wide and the space above the 100-high
 * inventory panel (never enlarged), the panel sits {@code round(gridHeight x scale) - 23} below the grid's
 * top, and the whole is centred 45 left of the screen's middle. The grid's outline is the edge-tile walk
 * upstream wrote ({@link #edgeTileMap}), turned to the four sides by {@link EdgeTile}.
 *
 * <h2>The slots, in 1.21.1</h2>
 *
 * Upstream reaches into {@code GuiContainerLeafia}'s slot drawing to put the inventory below a computed
 * offset and the fuel slots on a scaled grid (D-3000). Here slot coordinates are final and fixed when the
 * menu is built, so every slot was given a distinct placeholder there, and the screen maps each to where
 * it really is ({@link #slotRects}): {@link #isHovering(int, int, int, int, double, double)} answers for the
 * real rectangle whenever vanilla asks about a slot's 16 by 16 at its placeholder - which is how vanilla's
 * own hover, click, drag and tooltip find slots - and {@link #renderSlot}/{@link #renderSlotHighlight}
 * move the pose there first (scaled, for a fuel slot).
 *
 * <h2>Kept as upstream wrote it</h2>
 *
 * <ul>
 * <li>A rod group whose rods are not all at one height draws its bar faint. Upstream's test compares the
 *     <em>running sum</em> of positions with the first rod's position, so any group of two or more rods
 *     reads "not all equal" unless the later rods are all at zero. Kept and flagged (D-3010).</li>
 * <li>A projected position outside the grid's range closes the screen; so does the core going away.</li>
 * <li>The glow over a raised rod is drawn twice, once with the background and once over the finished
 *     screen.</li>
 * </ul>
 *
 * <h2>Changed</h2>
 *
 * <ul>
 * <li>The slider and compression requests go to the terminal rather than to the core (D-1094; see
 *     {@code PWRTerminalTE}).</li>
 * <li>The second glow pass is drawn before the tooltips rather than over them.</li>
 * <li>A block on the grid is drawn from its top sprite in the block atlas rather than from a texture file
 *     bound by name: the same centre ten-sixteenths of the face, at 12 by 12.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PWRTerminalScreen extends LCEGuiInfoContainer<PWRTerminalMenu> {

	private static final ResourceLocation texture = LeafiaRef.rl("textures/gui/reactors/leafia_pwr.png");
	private static final RandomSource RANDOM = RandomSource.create();

	@Nullable
	final PWRData core;
	@Nullable
	final BlockEntity entity;

	static final int UI_EDGE = 10;
	static final int UI_CORNER = 28;
	static final int UI_SLOT = 18;

	RangeInt rangeX = new RangeInt(Integer.MAX_VALUE, Integer.MIN_VALUE);
	RangeInt rangeZ = new RangeInt(Integer.MAX_VALUE, Integer.MIN_VALUE);
	static final int centerX = 45 + 176 / 2;
	static final int inventoryStart = 18;
	static final int inventoryHeight = 100;
	static final int preferredWidth = 176;
	int preferredHeight = 0;
	double calculatedScale = 1;

	int pwrWidth = 1;
	int pwrHeight = 1;
	int rawWidth = 0;
	int rawHeight = 0;

	record TileData(int width, int height, int xTex, int yTex, int xOffset, int yOffset) {}

	void drawEdgeTile(GuiGraphics g, int x, int y, TileData LU, TileData RU, TileData LD, TileData RD) {
		g.blit(texture, x + LU.xOffset, y + LU.yOffset, LU.xTex, LU.yTex, LU.width, LU.height);
		g.blit(texture, x + RU.xOffset + 9, y + RU.yOffset, RU.xTex, RU.yTex, RU.width, RU.height);
		g.blit(texture, x + LD.xOffset, y + LD.yOffset + 9, LD.xTex, LD.yTex, LD.width, LD.height);
		g.blit(texture, x + RD.xOffset + 9, y + RD.yOffset + 9, RD.xTex, RD.yTex, RD.width, RD.height);
	}

	/** A corner piece of the grid's outline, its four quarters turned to face {@code vector} ("i'm stupid ok?"). */
	static class EdgeTile {
		final TileData LU;
		final TileData RU;
		final TileData LD;
		final TileData RD;

		EdgeTile(ColumnVector vector, TileData lu, TileData ru, TileData ld, TileData rd) {
			if(vector.y == 1) {
				LU = lu; RU = ru; LD = ld; RD = rd;
			} else if(vector.x == 1) {
				LU = ld; RU = lu; LD = rd; RD = ru;
			} else if(vector.y == -1) {
				LU = rd; RU = ld; LD = ru; RD = lu;
			} else if(vector.x == -1) {
				LU = ru; RU = rd; LD = lu; RD = ld;
			} else
				throw new LeafiaDevFlaw("I'm stupid ok?");
		}

		void draw(PWRTerminalScreen self, GuiGraphics g, int x, int y) {
			self.drawEdgeTile(g, x, y, LU, RU, LD, RD);
		}
	}

	final Map<ColumnPos, EdgeTile> edgeTileMap = new LinkedHashMap<>();

	TileData tileA(ColumnVector vector, int fromCenterX, int fromCenterY) {
		ColumnPos texPos = new ColumnPos(2, 2).add(vector, fromCenterY).add(vector.right(), fromCenterX);
		return tileA(texPos.x, texPos.y);
	}

	TileData tileB(ColumnVector vector, int fromCenterX, int fromCenterY) {
		ColumnPos texPos = new ColumnPos(1, 1).add(vector, fromCenterY).add(vector.right(), fromCenterX);
		return tileB(texPos.x, texPos.y);
	}

	static TileData tileA(int tx, int ty) {
		int width = 10;
		int height = 10;
		int texX = 0;
		int texY = 0;
		int offX = 0;
		int offY = 0;
		switch(tx * 10 + ty) {
			case 21 -> { texX = 140; texY = 130; offX = -1; offY = -1; }
			case 12 -> { texX = 130; texY = 141; offX = -1; }
			case 32 -> { texX = 151; texY = 140; offY = -1; }
			case 23 -> { texX = 141; texY = 151; }
			default -> {
				width = 9;
				height = 9;
				switch(tx) {
					case 0 -> { texX = 120; width = 10; offX = -1; }
					case 4 -> { texX = 161; width = 10; }
					default -> texX = 131 + (tx - 1) * 10;
				}
				switch(ty) {
					case 0 -> { texY = 120; height = 10; offY = -1; }
					case 4 -> { texY = 161; height = 10; }
					default -> texY = 131 + (ty - 1) * 10;
				}
			}
		}
		return new TileData(width, height, texX, texY, offX, offY);
	}

	static TileData tileB(int tx, int ty) {
		return switch(tx * 10 + ty) {
			case 0 -> new TileData(9, 10, 121, 172, 0, -1);
			case 10 -> new TileData(9, 10, 131, 172, 0, -1);
			case 20 -> new TileData(10, 9, 141, 173, 0, 0);

			case 1 -> new TileData(10, 9, 120, 183, -1, 0);
			case 11 -> new TileData(9, 9, 131, 183, 0, 0);
			case 21 -> new TileData(10, 9, 141, 183, 0, 0);

			case 2 -> new TileData(10, 9, 120, 193, -1, 0);
			case 12 -> new TileData(9, 10, 131, 193, 0, 0);
			case 22 -> new TileData(9, 10, 141, 193, 0, 0);
			default -> throw new LeafiaDevFlaw("PWR texture " + tx + "," + ty + " does not exist on Type B Texture");
		};
	}

	/** A column of the grid: x across, y = the world's z. Upstream's, given the hashCode its equals needed. */
	record ColumnPos(int x, int y) {
		ColumnPos(ColumnVector vector) { this(vector.x, -vector.y); }
		ColumnPos(BlockPos pos) { this(pos.getX(), pos.getZ()); }
		ColumnPos add(int x, int y) { return new ColumnPos(this.x + x, this.y + y); }
		ColumnPos add(ColumnVector vector, int distance) { return add(vector.x * distance, -vector.y * distance); }
		ColumnPos subtract(int x, int y) { return new ColumnPos(this.x - x, this.y - y); }
		ColumnPos subtract(ColumnVector vector, int distance) { return subtract(vector.x * distance, -vector.y * distance); }
	}

	record ColumnVector(int x, int y) {
		static ColumnVector of(Direction facing) { return new ColumnVector(facing.getStepX(), -facing.getStepZ()); }
		ColumnVector right() { return new ColumnVector(y, -x); }
		ColumnVector left() { return new ColumnVector(-y, x); }
		ColumnVector back() { return new ColumnVector(-x, -y); }
	}

	/** 1.12.2's {@code EnumFacing.HORIZONTALS}, whose order decides which edge tile a later side overwrites. */
	static final Direction[] HORIZONTALS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

	final BlockState terminalState;
	final BlockPos terminalPos;
	final LeafiaSet<BlockPos> fuels;
	final LeafiaSet<BlockPos> controls;
	final HashMap<String, LeafiaSet<BlockPos>> controlsByName = new HashMap<>();
	final double[] rodPositions;
	final double[] rodPositionsD;
	static final int playerInventoryWidth = 176;

	public PWRTerminalScreen(PWRTerminalMenu menu, Inventory invPlayer, Component title) {
		super(menu, invPlayer, title);
		this.core = menu.core;
		this.entity = core == null ? null : core.companion;
		this.terminalPos = menu.terminalPos;
		this.terminalState = menu.terminalState;
		if(core == null) {
			fuels = new LeafiaSet<>();
			controls = new LeafiaSet<>();
			rodPositions = new double[0];
			rodPositionsD = new double[0];
			return;
		}
		// A core that reached this client in its chunk's block entity data rather than by upstream's key-31 packet
		// has its projection but no members, rods or columns: those come from the client-side diagnosis key 31
		// starts. Run it now, as key 31 would have (D-3010) - or the rod groups have no sliders.
		if(core.controls.isEmpty() || core.members.isEmpty()) core.onReceivePacketLocal((byte) 31, null);
		Pair<LeafiaSet<BlockPos>, LeafiaSet<BlockPos>> keyElements = core.getProjectionFuelAndControlPositions();
		fuels = keyElements.getKey();
		controls = keyElements.getValue();
		for(BlockPos pos : core.controls) {
			if(core.getWorld().getBlockEntity(pos) instanceof PWRControlTE control)
				controlsByName.computeIfAbsent(control.name, k -> new LeafiaSet<>()).add(pos);
		}
		rodPositions = new double[controlsByName.keySet().size()];
		rodPositionsD = new double[rodPositions.length];

		Set<ColumnPos> set2d = new LinkedHashSet<>();
		for(BlockPos pos : core.projection) {
			pos = core.terminal_toLocal(terminalState, terminalPos, pos);
			rangeX.min = Math.min(rangeX.min, pos.getX());
			rangeX.max = Math.max(rangeX.max, pos.getX());
			rangeZ.min = Math.min(rangeZ.min, pos.getZ());
			rangeZ.max = Math.max(rangeZ.max, pos.getZ());
			set2d.add(new ColumnPos(pos.getX(), pos.getZ()));
		}
		for(ColumnPos pos : set2d) {
			for(Direction face : HORIZONTALS) {
				ColumnVector vector = ColumnVector.of(face);
				ColumnPos forward = pos.add(vector, 1);
				if(set2d.contains(forward)) continue;
				boolean spaceRight = !set2d.contains(pos.add(vector.right(), 1));
				boolean spaceLeft = !set2d.contains(pos.add(vector.left(), 1));

				// 90 DEGREES CURVE CHECKS
				boolean spaceRightDn = !set2d.contains(pos.subtract(vector, 1).add(vector.right(), 1));
				boolean spaceLeftDn = !set2d.contains(pos.subtract(vector, 1).add(vector.left(), 1));

				boolean spaceRightUp = !set2d.contains(pos.add(vector, 1).add(vector.right(), 1));
				boolean spaceLeftUp = !set2d.contains(pos.add(vector, 1).add(vector.left(), 1));
				boolean spaceRight2Up = !set2d.contains(pos.add(vector, 1).add(vector.right(), 2));
				boolean spaceLeft2Up = !set2d.contains(pos.add(vector, 1).add(vector.left(), 2));
				if(!spaceLeftUp && spaceRightUp) {
					edgeTileMap.put(forward, new EdgeTile(vector,
							tileA(vector, 0, 0), tileA(vector, 1, 1),
							tileA(vector, 0, 0), tileA(vector, 0, 0)));
					if(spaceRight && !spaceRightDn) {
						edgeTileMap.put(forward.add(vector.right(), 1), new EdgeTile(vector,
								tileA(vector, 2, 2), tileB(vector, 0, 0),
								tileA(vector, 1, 1), tileA(vector, 2, 2)));
					}
				} else if(spaceLeftUp && !spaceRightUp) {
					edgeTileMap.put(forward, new EdgeTile(vector,
							tileA(vector, -1, 1), tileA(vector, 0, 0),
							tileA(vector, 0, 0), tileA(vector, 0, 0)));
					if(spaceLeft && !spaceLeftDn) {
						edgeTileMap.put(forward.add(vector.left(), 1), new EdgeTile(vector,
								tileB(vector, 0, 0), tileA(vector, -2, 2),
								tileA(vector, -2, 2), tileA(vector, -1, 1)));
					}
				} else {
					edgeTileMap.put(forward, new EdgeTile(vector,
							!spaceLeft2Up ? tileA(vector, 2, 2) : tileB(vector, 0, 0),
							!spaceRight2Up ? tileA(vector, -2, 2) : tileB(vector, 0, 0),
							!spaceLeft2Up ? tileB(vector, 0, 1) : tileA(vector, 0, 2),
							!spaceRight2Up ? tileB(vector, -1, 1) : tileA(vector, 0, 2)));
					if(spaceRight) {
						edgeTileMap.put(forward.add(vector.right(), 1), new EdgeTile(vector,
								tileB(vector, 0, 0), tileB(vector, 0, 0),
								tileA(vector, 1, spaceRightDn ? 0 : 2),
								spaceRightDn ? tileB(vector, 0, 0) : tileA(vector, 2, 2)));
					}
					if(spaceLeft) {
						edgeTileMap.put(forward.add(vector.left(), 1), new EdgeTile(vector,
								tileB(vector, 0, 0), tileB(vector, 0, 0),
								spaceLeftDn ? tileB(vector, 0, 0) : tileA(vector, -2, 2),
								tileA(vector, spaceLeftDn ? 0 : -1, spaceLeftDn ? 1 : 2)));
					}
				}
			}
		}

		if(!set2d.isEmpty()) {
			pwrWidth = rangeX.max - rangeX.min + 1;
			pwrHeight = rangeZ.max - rangeZ.min + 1;
		}
		rawWidth = pwrWidth * 18 + UI_EDGE * 2;
		rawHeight = pwrHeight * 18 + UI_EDGE * 2;
	}

	int inventorySpacing = 5;
	int containerOffset = 0;

	/** Where each slot really is, in screen coordinates: x, y and size, by the slot's placeholder. */
	final Map<Long, int[]> slotRects = new HashMap<>();

	static long key(int x, int y) {
		return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
	}

	@Override
	protected void init() {
		preferredHeight = height - inventoryHeight - inventorySpacing;
		this.calculatedScale = Math.min(Math.min(preferredWidth / (double) rawWidth, preferredHeight / (double) rawHeight), 1);
		this.containerOffset = (int) Math.round(rawHeight * calculatedScale) + inventorySpacing - inventoryStart - UI_EDGE;
		this.imageWidth = 215;
		this.imageHeight = containerOffset + inventoryHeight;
		super.init();
		topPos -= UI_EDGE / 2;
		leftPos = width / 2 - 45 - playerInventoryWidth / 2;
		slotRects.clear();
		for(Slot slot : menu.slots) {
			if(slot instanceof RemoteSlot remote) {
				ColumnPos cpos = new ColumnPos(remote.localPos).subtract(rangeX.min, rangeZ.min);
				int xPos = getSlotX(cpos) + 1;
				int yPos = getSlotY(cpos) + 1;
				slotRects.put(key(slot.x, slot.y), new int[] {
						(int) (xPos * calculatedScale), (int) (yPos * calculatedScale), (int) (16 * calculatedScale), xPos, yPos});
			} else {
				slotRects.put(key(slot.x, slot.y), new int[] {leftPos + slot.x, topPos + slot.y + containerOffset, 16, 0, 0});
			}
		}
		if(core == null) onClose();
	}

	/** Vanilla's slot hit test, asked about a slot's placeholder, answers for where the slot really is. */
	@Override
	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		if(width == 16 && height == 16) {
			int[] r = slotRects.get(key(x, y));
			if(r != null) return mouseX >= r[0] - 1 && mouseX < r[0] + r[2] + 1 && mouseY >= r[1] - 1 && mouseY < r[1] + r[2] + 1;
		}
		return super.isHovering(x, y, width, height, mouseX, mouseY);
	}

	@Override
	protected boolean isMouseOverSlot(Slot slot, double x, double y) {
		return this.isHovering(slot.x, slot.y, 16, 16, x, y);
	}

	/** Moves the pose from the slot's placeholder to where it is drawn (the pose is at the GUI's corner here). */
	private void toSlot(GuiGraphics g, Slot slot) {
		int[] r = slotRects.get(key(slot.x, slot.y));
		if(r == null) return;
		if(slot instanceof RemoteSlot) {
			g.pose().translate(-leftPos, -topPos, 0);
			g.pose().scale((float) calculatedScale, (float) calculatedScale, 1);
			g.pose().translate(r[3] - slot.x, r[4] - slot.y, 0);
		} else {
			g.pose().translate(0, containerOffset, 0);
		}
	}

	@Override
	protected void renderSlot(GuiGraphics g, Slot slot) {
		g.pose().pushPose();
		toSlot(g, slot);
		super.renderSlot(g, slot);
		g.pose().popPose();
	}

	@Override
	protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
		g.pose().pushPose();
		toSlot(g, slot);
		super.renderSlotHighlight(g, slot, mouseX, mouseY, partialTick);
		g.pose().popPose();
	}

	String[] tip = null;

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
		if(core == null || entity == null || entity.isRemoved() || !(entity instanceof PWRComponentEntity component) || component.getCore() != core) {
			onClose();
			return;
		}
		super.render(g, mouseX, mouseY, partialTicks);

		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		for(int i = -1; i < rodPositions.length; i++) {
			if(((i < 0) ? core.masterControl : rodPositions[i]) > 0) {
				g.setColor(1, 1, 1, 0.1F);
				g.blit(texture, leftPos + 244 + 19 * i + 9, topPos + containerOffset + 7, 192, 190, 6, 8);
				g.setColor(1, 1, 1, 0.2F);
				g.blit(texture, leftPos + 244 + 19 * i + 9 - 16, topPos + containerOffset + 7 - 15, 153, 190, 38, 38);
			}
		}
		g.setColor(1, 1, 1, 1F);
		RenderSystem.defaultBlendFunc();

		this.renderTooltip(g, mouseX, mouseY);
		if(tip != null) drawHoveringText(g, Arrays.asList(tip), mouseX, mouseY);
		LeafiaFFClient.renderTankInfo(this, g, mouseX, mouseY, leftPos + 5, topPos + containerOffset + 61, 16, 52, core.tanks[0], false);
		LeafiaFFClient.renderTankInfo(this, g, mouseX, mouseY, leftPos + 23, topPos + containerOffset + 61, 16, 52, core.tanks[1], false);
		LeafiaFFClient.renderTankInfo(this, g, mouseX, mouseY, leftPos + 23, topPos + containerOffset + 32, 16, 26, core.tanks[2], false);
		if(core.tanks[3].getMaxFill() > 0) {
			LeafiaFFClient.renderTankInfo(this, g, mouseX, mouseY, leftPos - 22, topPos + containerOffset + 25, 16, 52, core.tanks[3], false);
			LeafiaFFClient.renderTankInfo(this, g, mouseX, mouseY, leftPos - 5, topPos + containerOffset - 3, 10, 16, core.tanks[4], false);
		}
	}

	int getContainerX() {
		return (int) ((leftPos + 45 + playerInventoryWidth / 2) / calculatedScale) - rawWidth / 2;
	}

	public int getSlotX(ColumnPos pos) {
		return getContainerX() + pos.x * 18 + UI_EDGE;
	}

	public int getSlotY(ColumnPos pos) {
		return (int) (topPos / calculatedScale) + pos.y * 18;
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, I18nUtil.resolveKey("container.inventory"), 53, containerOffset + 24, 4210752, false);
	}

	static double avoidNull(@Nullable Double in) {
		return in == null ? 0 : in;
	}

	/** Upstream's {@code LeafiaClientUtil.statusDecimals}: the key's lines with the value rounded to {@code decimals}. */
	static String[] statusDecimals(String template, double value, int decimals) {
		double mul = Math.pow(10, decimals);
		return I18nUtil.resolveKeyArray(template, String.format("%01." + decimals + "f", Math.floor(value * mul + 0.5) / mul));
	}

	Integer rodGrab = null;
	Integer rodHover = null;
	String hoveringRodName = null;
	double rodHoverPosition = 0;
	boolean compressionHover = false;

	/** Where the screen's requests go: the terminal (class note). */
	@Nullable
	private PWRTerminalTE terminal() {
		return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(terminalPos) instanceof PWRTerminalTE t ? t : null;
	}

	void mouse() {
		PWRTerminalTE terminal = terminal();
		if(rodGrab != null && terminal != null) {
			CompoundTag data = new CompoundTag();
			if(hoveringRodName != null) data.putString("name", hoveringRodName);
			data.putDouble("level", rodHoverPosition);
			LeafiaPacket._start(terminal).__write(30, data).__sendToServer();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
		boolean handled = super.mouseClicked(mouseX, mouseY, mouseButton);
		if(rodHover != null) {
			rodGrab = rodHover;
			playClick(1.0F);
		} else if(compressionHover && core != null) {
			PWRTerminalTE terminal = terminal();
			if(terminal != null)
				LeafiaPacket._start(terminal).__write(29, core.compression + ((mouseButton == 0) ? 1 : -1)).__sendToServer();
			playClick(1.0F);
		}
		mouse();
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		boolean handled = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
		mouse();
		return handled;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean handled = super.mouseReleased(mouseX, mouseY, button);
		rodGrab = null;
		return handled;
	}

	static boolean hoverNoOffset(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
		return pointX >= rectX && pointX <= rectX + rectWidth && pointY >= rectY && pointY <= rectY + rectHeight;
	}

	/** Upstream's {@code tryBindQuads}: the first quad's sprite on that side, or the particle sprite. */
	static TextureAtlasSprite sideSprite(BakedModel baked, BlockState display, Direction face) {
		List<BakedQuad> quads = baked.getQuads(display, face, RANDOM, ModelData.EMPTY, null);
		return quads.isEmpty() ? baked.getParticleIcon(ModelData.EMPTY) : quads.get(0).getSprite();
	}

	/** The centre ten-sixteenths of a sprite, at 12 by 12 (upstream's tessellated quad). */
	static void drawBlockFace(GuiGraphics g, TextureAtlasSprite sprite, int slotX, int slotY) {
		float sc = 10 / 16f;
		float offs = 3 / 16f;
		float u0 = sprite.getU(offs), u1 = sprite.getU(offs + sc);
		float v0 = sprite.getV(offs), v1 = sprite.getV(offs + sc);
		RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		Matrix4f m = g.pose().last().pose();
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buf.addVertex(m, slotX + 3, slotY + 3 + 12, 0).setUv(u0, v1);
		buf.addVertex(m, slotX + 3 + 12, slotY + 3 + 12, 0).setUv(u1, v1);
		buf.addVertex(m, slotX + 3 + 12, slotY + 3, 0).setUv(u1, v0);
		buf.addVertex(m, slotX + 3, slotY + 3, 0).setUv(u0, v0);
		BufferUploader.drawWithShader(buf.buildOrThrow());
	}

	private void rotatedLabel(GuiGraphics g, int x, int y, String text, int colour) {
		g.pose().pushPose();
		g.pose().translate(x, y + 106, 0);
		g.pose().mulPose(Axis.ZP.rotationDegrees(-90));
		g.pose().scale(7 / 13f, 7 / 13f, 1);
		g.drawString(font, ChatFormatting.BOLD + text, 0, 3, colour, false);
		g.pose().popPose();
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
		tip = null;
		rodHover = null;
		compressionHover = false;
		if(core == null) return;
		RenderSystem.enableBlend();
		RangeInt rodRange;
		g.blit(texture, leftPos, topPos + containerOffset, 0, 0, 244, 118);
		{
			int x = leftPos + 244 - 19;
			int y = topPos + containerOffset + 7;
			rodRange = new RangeInt(y + 18 + 88, y + 18);

			int pix = (int) Math.floor((1 - core.masterControl) * 88);
			g.blit(texture, x + 8, y + 18, 42, 235 - pix, 8, pix);

			boolean isHovering = hoverNoOffset(x + 6, y + 17, 12, 92, mouseX, mouseY);
			if((rodGrab == null) ? isHovering : (rodGrab == -1)) {
				rodHover = -1;
				rodHoverPosition = Mth.clamp(rodRange.ratio((float) mouseY), 0, 1);
				hoveringRodName = null;
				tip = statusDecimals("desc.leafia._repeated.reactors.controlrods", core.masterControl * 100, 0);
			}
			rotatedLabel(g, x, y, "Master Control", 0xFFFFFF);
			if(core.masterControl > 0) g.blit(texture, x + 9, y, 192, 190, 6, 8);
		}
		List<String> names = new ArrayList<>(controlsByName.keySet());
		names.sort(Comparator.naturalOrder());
		Iterator<String> iter = names.iterator();
		for(int i = 0; iter.hasNext(); i++) {
			String s = iter.next();
			int x = leftPos + 244 + 19 * i;
			int y = topPos + containerOffset + 7;
			g.blit(texture, x, y, iter.hasNext() ? 0 : 19, 120, iter.hasNext() ? 19 : 22, 111);
			rotatedLabel(g, x, y, s, 4210752);
			boolean isAllEqual = true;
			int number = 0;
			double position = 0;
			Double lastPosition = null;
			for(BlockPos pos : controlsByName.get(s)) {
				if(core.getWorld().getBlockEntity(pos) instanceof PWRControlTE control) {
					number++;
					position += control.position;
					if(lastPosition != null) {
						if(position != lastPosition) isAllEqual = false; // upstream compares the running sum (class note)
					} else lastPosition = control.position;
				}
			}
			if(i >= rodPositions.length) break;
			rodPositions[i] = position / number;
			rodPositionsD[i] = avoidNull(core.controlDemand.get(s));

			boolean isHovering = hoverNoOffset(x + 6, y + 17, 12, 92, mouseX, mouseY);
			if((rodGrab == null) ? isHovering : (rodGrab == i)) {
				rodHover = i;
				rodHoverPosition = Mth.clamp(rodRange.ratio((float) mouseY), 0, 1);
				hoveringRodName = s;
				tip = statusDecimals("desc.leafia._repeated.reactors.controlrods", rodPositions[i] * 100, 0);
			}
			float col = isHovering ? 0.85F : 1F;

			int pix = (int) Math.floor((1 - rodPositions[i]) * 88);
			g.setColor(col, col, col, isAllEqual ? 1 : 0.4f);
			g.blit(texture, x + 8, y + 18, 42, 235 - pix, 8, pix);

			pix = (int) Math.floor((1 - rodPositionsD[i]) * 88);
			g.setColor(col, col, col, 0.25f);
			g.blit(texture, x + 8, y + 18, 42, 235 - pix, 8, pix);
			g.setColor(1, 1, 1, 1);
			if(position > 0) g.blit(texture, x + 9, y, 192, 190, 6, 8);
		}
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		for(int i = -1; i < rodPositions.length; i++) {
			if(((i < 0) ? core.masterControl : rodPositions[i]) > 0)
				g.blit(texture, leftPos + 244 + 19 * i + 9 - 16, topPos + containerOffset + 7 - 15, 153, 190, 38, 38);
		}
		RenderSystem.defaultBlendFunc();

		g.pose().pushPose();
		g.pose().scale((float) calculatedScale, (float) calculatedScale, 1);
		for(Map.Entry<ColumnPos, EdgeTile> entry : edgeTileMap.entrySet()) {
			ColumnPos pos = entry.getKey().subtract(rangeX.min, rangeZ.min);
			entry.getValue().draw(this, g, getSlotX(pos), getSlotY(pos));
		}
		for(BlockPos worldPos : core.projection) {
			BlockPos pos = core.terminal_toLocal(terminalState, terminalPos, worldPos);
			if(!rangeX.isInRange(pos.getX()) || !rangeZ.isInRange(pos.getZ())) {
				onClose();
				break;
			}
			ColumnPos cpos = new ColumnPos(pos).subtract(rangeX.min, rangeZ.min);
			BlockState state = core.getWorld().getBlockState(worldPos);
			int slotX = getSlotX(cpos);
			int slotY = getSlotY(cpos);
			if(fuels.contains(worldPos)) {
				g.blit(texture, slotX, slotY, 191, 120, 18, 18); // Slot
			} else if(controls.contains(worldPos)) {
				g.blit(texture, slotX, slotY, 172, 120, 18, 18); // Control
				if(core.getWorld().getBlockEntity(worldPos) instanceof PWRControlTE control) {
					int level = (int) Math.min(Math.floor(control.position * 5), 4);
					g.blit(texture, slotX + 5, slotY + 5, 153 + level * 8, 181, 8, 8);
				}
			} else if(state.getRenderShape() != RenderShape.INVISIBLE) {
				g.blit(texture, slotX, slotY, 210, 120, 18, 18);
				BakedModel baked = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
				drawBlockFace(g, sideSprite(baked, state, Direction.UP), slotX, slotY);
			} else {
				g.blit(texture, slotX, slotY, 172, 120, 18, 18); // None
			}
		}
		g.pose().popPose();

		GUIElements.drawFluidTank(g, core.tanks[0], leftPos + 5, topPos + containerOffset + 61 + 52, 16, 52);
		GUIElements.drawFluidTank(g, core.tanks[1], leftPos + 23, topPos + containerOffset + 61 + 52, 16, 52);
		GUIElements.drawFluidTank(g, core.tanks[2], leftPos + 23, topPos + containerOffset + 32 + 26, 16, 26);

		g.setColor(1, 1, 1, 1); // for some reason without this the entire thing turns red lol
		if(core.tanks[2].getFill() > 0 && core.warnTicks >= 3)
			g.blit(texture, leftPos + 18, topPos + containerOffset, 42, 120, 26, 26);

		if(core.tanks[3].getMaxFill() > 0) {
			g.blit(texture, leftPos - 27, topPos + containerOffset - 9, 69, 120, 38, 127);
			g.blit(texture, leftPos - 22, topPos + containerOffset - 4, 14 * core.compression, 238, 14, 18);
			if(isHovering(-22, containerOffset - 4, 14, 18, mouseX, mouseY)) {
				tip = statusDecimals("desc.leafia._repeated.reactors.compression", Math.pow(10, core.compression), 0);
				compressionHover = true;
			}
			GUIElements.drawFluidTank(g, core.tanks[3], leftPos - 22, topPos + containerOffset + 25 + 52, 16, 52);
			GUIElements.drawFluidTank(g, core.tanks[4], leftPos - 5, topPos + containerOffset - 3 + 16, 10, 16);
		}
	}
}
