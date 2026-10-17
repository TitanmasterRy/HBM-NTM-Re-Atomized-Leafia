package com.leafia.contents.machines.controlpanel.core.reach;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBase;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKBoiler;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControl;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlManual;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKControlManual.RBMKColor;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKCooler;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKHeater;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKOutgasser;
import com.hbm.blockentity.machine.rbmk.BlockEntityRBMKRod;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.items.machine.ItemRBMKRod;
import com.leafia.contents.machines.controlpanel.core.ControlEvent;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Every RBMK column NTM-CE made controllable ({@code TileEntityRBMKBase} and its subclasses), as adapters (batch 8, D-3068).
 *
 * <h2>Keys</h2>
 * Every column: {@code heat}, {@code RSIM_feed}, {@code RSIM_steam}. Then, per type, in CE's order:
 * <table>
 * <caption>query keys by column</caption>
 * <tr><td>fuel rod, ReaSim rod</td><td>{@code enrichment}, {@code xenon}, {@code c_heat}, {@code c_coreHeat}, {@code c_maxHeat}
 * (all zero with no rod in the slot)</td></tr>
 * <tr><td>control rod, auto control rod</td><td>{@code level}, {@code targetLevel}, each times 100</td></tr>
 * <tr><td>manual control rod</td><td>the same, and {@code color} while it has one (the {@code RBMKColor} ordinal)</td></tr>
 * <tr><td>boiler</td><td>{@code feed}, {@code steam} (the fills), {@code steamType} (the fluid's name)</td></tr>
 * <tr><td>cooler, heater</td><td>{@code t0_fluidType}, {@code t0_fluidAmount}, {@code t1_fluidType}, {@code t1_fluidAmount}</td></tr>
 * <tr><td>outgasser</td><td>{@code gas}, {@code progress}, {@code maxProgress}</td></tr>
 * <tr><td>moderator, reflector, absorber, blank, storage</td><td>the common three only</td></tr>
 * </table>
 *
 * <p>The plan's table for the crane console and these columns listed extra keys that NTM-CE does not have; CE's source is the
 * specification (D-3060), so the keys above are CE's, and the crane's are in {@link CraneConsoleReach}.
 *
 * <h2>In-events (the manual control rod only)</h2>
 * <ul>
 * <li>{@code rbmk_ctrl_set_level}: {@code level} is a percentage, {@code /100} and clamped to {@code 0..1} like the rod's own
 * screen packet; a non-finite value is ignored. The base's {@code setTarget} records the starting level for the insertion surge,
 * which is what CE did by hand before calling it.</li>
 * <li>{@code rbmk_ctrl_set_color}: {@code color} is one-based, 1 to 5 for red, yellow, green, blue, purple. CE indexed
 * {@code VALUES[n % 5 - 1]}, which throws for 0 and every multiple of five, so purple could never be set; here {@code 5} is purple,
 * and anything below 1 is ignored. Unlike the screen's button, setting the colour a rod already has does not clear it.</li>
 * </ul>
 */
public final class RBMKReach {

	private RBMKReach() {}

	public static final String SET_LEVEL = "rbmk_ctrl_set_level";
	public static final String SET_COLOR = "rbmk_ctrl_set_color";

	/** {@code heat}, {@code RSIM_feed}, {@code RSIM_steam}, which every column answers. */
	public static class Column<T extends BlockEntityRBMKBase> extends ReachAdapter<T> {

		public Column(T be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = map();
			data.put("heat", num((float) be.heat));
			data.put("RSIM_feed", num(be.reasimWater));
			data.put("RSIM_steam", num(be.reasimSteam));
			return data;
		}
	}

	public static final class Rod extends Column<BlockEntityRBMKRod> {

		public Rod(BlockEntityRBMKRod be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			ItemStack stack = be.slots[0];

			data.put("enrichment", num(0));
			data.put("xenon", num(0));
			data.put("c_heat", num(0));
			data.put("c_coreHeat", num(0));
			data.put("c_maxHeat", num(0));

			if(stack.getItem() instanceof ItemRBMKRod rod) {
				data.put("enrichment", num((float) ItemRBMKRod.getEnrichment(stack)));
				data.put("xenon", num((float) ItemRBMKRod.getPoison(stack)));
				data.put("c_heat", num((float) ItemRBMKRod.getHullHeat(stack)));
				data.put("c_coreHeat", num((float) ItemRBMKRod.getCoreHeat(stack)));
				data.put("c_maxHeat", num((float) rod.meltingPoint));
			}
			return data;
		}
	}

	/** The automatic control rod answers the levels and takes no event, as CE's. */
	public static class Control<T extends BlockEntityRBMKControl> extends Column<T> {

		public Control(T be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			data.put("level", num((float) be.rodLevel * 100));
			data.put("targetLevel", num((float) be.targetLevel * 100));
			return data;
		}
	}

	public static final class ControlManual extends Control<BlockEntityRBMKControlManual> {

		public ControlManual(BlockEntityRBMKControlManual be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			if(be.color != null)
				data.put("color", num(be.color.ordinal()));
			return data;
		}

		@Override
		public List<String> getInEvents() {
			return Arrays.asList(SET_LEVEL, SET_COLOR);
		}

		@Override
		public void receiveEvent(BlockPos from, ControlEvent e) {
			if(e.name.equals(SET_LEVEL)) {
				float percent = number(e, "level", 0F);
				if(!Float.isFinite(percent))
					return;
				be.setTarget(Mth.clamp(percent / 100, 0F, 1F));
				be.setChanged();
			}
			if(e.name.equals(SET_COLOR)) {
				int n = whole(e, "color");
				if(n < 1)
					return;
				RBMKColor[] colors = RBMKColor.values();
				be.color = colors[(n - 1) % colors.length];
				be.setChanged();
			}
		}
	}

	public static final class Boiler extends Column<BlockEntityRBMKBoiler> {

		public Boiler(BlockEntityRBMKBoiler be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			data.put("feed", num(be.feed.getFill()));
			data.put("steam", num(be.steam.getFill()));
			data.put("steamType", str(be.steam.getTankType().getName()));
			return data;
		}
	}

	/** Cooler and heater: two tanks under the same four keys. */
	private static void twoTanks(Map<String, DataValue> data, FluidTank t0, FluidTank t1) {
		data.put("t0_fluidType", ReachAdapter.str(t0.getTankType().getName()));
		data.put("t0_fluidAmount", ReachAdapter.num(t0.getFill()));
		data.put("t1_fluidType", ReachAdapter.str(t1.getTankType().getName()));
		data.put("t1_fluidAmount", ReachAdapter.num(t1.getFill()));
	}

	public static final class Cooler extends Column<BlockEntityRBMKCooler> {

		public Cooler(BlockEntityRBMKCooler be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			FluidTank[] tanks = be.getAllTanks();
			twoTanks(data, tanks[0], tanks[1]);
			return data;
		}
	}

	public static final class Heater extends Column<BlockEntityRBMKHeater> {

		public Heater(BlockEntityRBMKHeater be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			twoTanks(data, be.feed, be.steam);
			return data;
		}
	}

	public static final class Outgasser extends Column<BlockEntityRBMKOutgasser> {

		public Outgasser(BlockEntityRBMKOutgasser be) {
			super(be);
		}

		@Override
		public Map<String, DataValue> getQueryData() {
			Map<String, DataValue> data = super.getQueryData();
			data.put("gas", num(be.gas.getFill()));
			data.put("progress", num((float) be.progress));
			data.put("maxProgress", num((float) BlockEntityRBMKOutgasser.duration));
			return data;
		}
	}
}
