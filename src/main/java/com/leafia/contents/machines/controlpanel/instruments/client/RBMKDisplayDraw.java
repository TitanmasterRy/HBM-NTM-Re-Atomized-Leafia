package com.leafia.contents.machines.controlpanel.instruments.client;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKConsole.ColumnType;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.leafia.contents.machines.controlpanel.core.block.ControlPanelBlock;
import com.leafia.contents.machines.controlpanel.core.client.ControlDraw;
import com.leafia.contents.machines.controlpanel.instruments.types.AddonInstrumentModels;
import com.leafia.contents.machines.controlpanel.instruments.types.rbmk.RBMKDisplay;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The RBMK display's drawing (CCP-INSTRUMENTS-2, D-3072): a port of the body of Leafia's {@code RBMKDisplay.render}.
 *
 * <p>A {@code width x height} checkerboard of the 2 x 2 {@code rbmk} texture, one pixel to a column, centred on the control (Leafia let the
 * texture repeat, which the block atlas does not, so each cell takes the centre of one pixel: {@code (cell + offset) mod 2}); then, for
 * each cell, the column the control's one link names at that place ({@link RBMKDisplay#columnPos}), read from the client's own world:
 * a square 0.75 of a unit across in {@link RBMKDisplay#columnColor}, and a dot over it ({@link RBMKDisplay#dotColor}), three kites
 * Leafia copied from the console's drawing. With none or several links, two lines of white text instead of the map.
 *
 * <p>Layers sit 0.01 apart above the panel: the checkerboard, the squares, the dots 0.03 and the error text.
 */
@OnlyIn(Dist.CLIENT)
final class RBMKDisplayDraw {

	private RBMKDisplayDraw() {}

	/** Half a column square: Leafia's {@code 0.0625 * 0.75} times the 8 he scaled by. */
	private static final float HALF = 0.0625F * 0.75F * 8;
	private static final float W = 0.03125F * 8;
	private static final float E = 0.022097F * 8;

	static void draw(RBMKDisplay c, ControlDraw d) {
		int w = c.getMapWidth();
		int h = c.getMapHeight();

		TextureAtlasSprite sprite = ControlDraw.sprite("leafia:block/ccp/" + AddonInstrumentModels.RBMK_TEXTURE);
		for(int cy = 0; cy < h; cy++) {
			for(int cx = 0; cx < w; cx++) {
				float u = (((cx + c.getOffsetU()) % 2) + 0.5F) / 2F;
				float v = (((cy + c.getOffsetV()) % 2) + 0.5F) / 2F;
				float x0 = RBMKDisplay.cellX(w, cx) - 0.5F;
				float z0 = RBMKDisplay.cellY(h, cy) - 0.5F;
				d.flatSpriteQuad(sprite, x0, z0, x0 + 1, z0 + 1, 0.01F, u, v, 1, 1, 1);
			}
		}

		String error = c.linkError();
		if(error != null) {
			String[] lines = error.split("\\$");
			for(int i = 0; i < lines.length; i++) {
				float line = -(lines.length - 1) + i;
				d.pose.pushPose();
				d.pose.translate(0, 0.04F, 0);
				d.flatText(0.1F, false);
				d.text(lines[i], -(d.font.width(lines[i]) / 2), (int) (line * 11), 0xFFFFFF, false);
				d.pose.popPose();
			}
			return;
		}

		if(c.panel == null || c.panel.parent == null)
			return;
		Level level = c.panel.parent.getControlWorld();
		if(level == null)
			return;
		BlockState panelState = level.getBlockState(c.panel.parent.getControlPos());
		if(!panelState.hasProperty(ControlPanelBlock.FACING))
			return;
		Direction facing = panelState.getValue(ControlPanelBlock.FACING).getOpposite();
		BlockPos center = c.center();

		for(int cy = 0; cy < h; cy++) {
			for(int cx = 0; cx < w; cx++) {
				BlockPos pos = RBMKDisplay.columnPos(center, facing, w, h, cx, cy);
				if(!(level.getBlockState(pos).getBlock() instanceof RBMKBase rbmk))
					continue;
				BlockPos core = rbmk.findCore(level, pos);
				if(core == null)
					continue;
				BlockEntity be = level.getBlockEntity(core);
				if(be instanceof BlockEntityRBMKBase column)
					column(d, column, RBMKDisplay.cellX(w, cx), RBMKDisplay.cellY(h, cy));
			}
		}
	}

	private static void column(ControlDraw d, BlockEntityRBMKBase column, float x, float z) {
		ColumnType type = column.getConsoleType();
		CompoundTag data = column.getNBTForConsole();
		if(data == null)
			data = new CompoundTag();
		float[] color = RBMKDisplay.columnColor(type, data, column.heat, column.maxHeat());
		d.flatQuad(x - HALF, z - HALF, x + HALF, z + HALF, 0.02F, color[0], color[1], color[2]);

		float[] dot = RBMKDisplay.dotColor(type, data);
		if(dot == null)
			return;
		// Leafia's three quads of the dot, in his (y, z) which is this plane's (z, x): each kite as a fan from its first corner
		poly(d, x, z, dot, new float[]{0, E, W, E}, new float[]{W, E, 0, -E});
		poly(d, x, z, dot, new float[]{-E, 0, -E, -W}, new float[]{E, W, -E, 0});
		poly(d, x, z, dot, new float[]{0, E, 0, -E}, new float[]{W, -E, -W, -E});
	}

	private static void poly(ControlDraw d, float x, float z, float[] color, float[] dx, float[] dz) {
		float[] xs = new float[4];
		float[] zs = new float[4];
		for(int i = 0; i < 4; i++) {
			xs[i] = x + dx[i];
			zs[i] = z + dz[i];
		}
		d.flatPolygon(xs, zs, 4, 0.03F, color[0], color[1], color[2], false);
	}
}
