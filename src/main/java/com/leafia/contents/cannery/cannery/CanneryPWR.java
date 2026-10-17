package com.leafia.contents.cannery.cannery;

import com.hbm.blocks.ModBlocks;
import com.hbm.util.i18n.I18nUtil;
import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.JarScript;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actions.ActionCreateActor;
import com.hbm.wiaj.actions.ActionRemoveActor;
import com.hbm.wiaj.actions.ActionRotateBy;
import com.hbm.wiaj.actions.ActionSetBlock;
import com.hbm.wiaj.actions.ActionSetZoom;
import com.hbm.wiaj.actions.ActionWait;
import com.hbm.wiaj.actors.ActorFancyPanel.Orientation;
import com.hbm.wiaj.actors.ActorTileEntity;
import com.hbm.wiaj.actors.ITileActorRenderer;
import com.hbm.wiaj.cannery.CanneryBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.cannery.CanneryActorRenders;
import com.leafia.contents.cannery.actors.ActorVisText;
import com.leafia.contents.control.fuel.nuclearfuel.LeafiaRods;
import com.leafia.contents.machines.reactors.pwr.blocks.components.terminal.PWRTerminalBlock;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The PWR guide topic: a short reactor built round a fuel column, the three skins of the fuel rod, how a rod is loaded, where flux
 * goes (a half to each corner, and a halving to the power of the distance in the four straight lines), the control rods, the coolant
 * channel that moderates, graphite as the other moderator, and stacking.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/cannery/CanneryPWR.java}, scene for scene and wait for wait; every text is the addon's
 * {@code cannery.leafia.pwr.*} key. Differences of kind, none of them in the script:
 *
 * <ul>
 * <li>The text panels take their font from {@link #metrics()}, as every topic does, where upstream took it from the Minecraft singleton.</li>
 * <li>A block with metadata is a block state: the terminal's {@code 2} is north, the wool and concrete of a colour are that colour's
 * block. The fuel rods stay at {@code stacked = false}, which is what metadata {@code 0} was and what a jar never updates.</li>
 * <li>The control rods are actors with no block behind them, as upstream's: {@link ActorControlRods} draws a segment of the column from
 * the eight baked part models the control rod's own renderer uses (D-3029).</li>
 * <li>The rod in the third scene's panel is the addon's medium-enriched uranium rod. When the rods are switched off it is not
 * registered and the panel shows nothing there.</li>
 * </ul>
 */
public class CanneryPWR extends CanneryBase {

	@Override
	public ItemStack getIcon() {
		return LeafiaBlocks.lwr_element == null ? ItemStack.EMPTY : new ItemStack(LeafiaBlocks.lwr_element.get());
	}

	@Override
	public String getName() {
		return "cannery.leafia.pwr";
	}

	@Override
	public JarScript createScript() {

		WorldInAJar world = new WorldInAJar(9, 5, 9);
		JarScript script = new JarScript(world);
		int center = 4;
		final int FLUX_LABELS = center / 2;

		var concrete = ModBlocks.concrete_smooth.get();
		var port = LeafiaBlocks.lwr_port.get();
		var reflector = LeafiaBlocks.lwr_reflector.get();
		var hull = LeafiaBlocks.lwr_casing.get();
		var channel = LeafiaBlocks.lwr_channel.get();
		var element = LeafiaBlocks.lwr_element.get();
		var elementOld = LeafiaBlocks.reactor_element.get();
		var elementOldBlank = LeafiaBlocks.reactor_element_blank.get();
		var graphite = ModBlocks.block_graphite.get();

		{
			// INTRO
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetZoom(2, 0));
			for(int x = world.sizeX - 1; x >= 0; x--) {
				for(int z = 0; z < world.sizeZ; z++)
					scene.add(new ActionSetBlock(x, 0, z, concrete));
				scene.add(new ActionWait(2));
			}
			{
				// build example PWR
				for(int xr = -2; xr <= 2; xr += 4) {
					for(int zr = -1; zr <= 1; zr++) {
						int x = xr + center;
						int z = zr + center;
						scene.add(new ActionSetBlock(x, 1, z, zr == 0 ? port : reflector));
						scene.add(new ActionSetBlock(x, 2, z, hull));
					}
				}
				for(int xr = -1; xr <= 1; xr++) {
					for(int zr = -2; zr <= 2; zr += 2) {
						int x = xr + center;
						int z = zr + center;
						scene.add(new ActionSetBlock(x, 1, z, xr == 0 ? hull : reflector));
						scene.add(new ActionSetBlock(x, 2, z, hull));
					}
				}
				for(int xr = -1; xr <= 1; xr++) {
					for(int zr = -1; zr <= 1; zr++) {
						int x = xr + center;
						int z = zr + center;
						scene.add(new ActionSetBlock(x, 0, z, hull));
						scene.add(new ActionSetBlock(x, 3, z, hull));
						scene.add(new ActionSetBlock(x, 2, z, channel));
					}
				}
				scene.add(new ActionSetBlock(center, 1, center, Blocks.AIR)); // :lazy:

				addControl(scene, center, 1, center, 0);
				addControl(scene, center + 1, 1, center + 1, 0);
				addControl(scene, center + 1, 1, center - 1, 0);
				addControl(scene, center - 1, 1, center + 1, 0);
				addControl(scene, center - 1, 1, center - 1, 0);

				scene.add(new ActionSetBlock(center + 1, 1, center, element));
				scene.add(new ActionSetBlock(center - 1, 1, center, element));
				scene.add(new ActionSetBlock(center, 1, center + 1, element));
				scene.add(new ActionSetBlock(center, 1, center - 1, element));

				// metadata 2
				scene.add(new ActionSetBlock(center, 2, center - 2,
						LeafiaBlocks.lwr_terminal.get().defaultBlockState().setValue(PWRTerminalBlock.FACING, Direction.NORTH)));
			}
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.0"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.1"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			script.addScene(scene);
		}
		{
			// SKINS
			JarScene scene = new JarScene(script);
			for(int y = 3; y >= 0; y--) {
				scene.add(new ActionWait(3));
				for(int x = center - 2; x <= center + 2; x++) {
					for(int z = center - 2; z <= center + 2; z++) {
						scene.add(new ActionSetBlock(x, y, z, y == 0 ? concrete : Blocks.AIR));
						removeControl(scene, x, y, z);
					}
				}
			}
			scene.add(new ActionWait(10));
			scene.add(new ActionSetBlock(center, 1, center, element));
			scene.add(new ActionSetBlock(center + 2, 1, center, elementOld));
			scene.add(new ActionSetBlock(center - 2, 1, center, elementOldBlank));
			scene.add(new ActionWait(10));
			showText(scene, 0, 5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.2"));
			scene.add(new ActionWait(20 * 3));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, 5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.3"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			scene.add(new ActionWait(10));
			scene.add(new ActionSetBlock(center + 2, 1, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center - 2, 1, center, Blocks.AIR));
			scene.add(new ActionWait(10));
			script.addScene(scene);
		}
		{
			// LOADING FUEL
			JarScene scene = new JarScene(script);
			showText(scene, 0, 5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.4"));
			scene.add(new ActionWait(20 * 3));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, 5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.5"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			Item rod = LeafiaRods.get("leafRodMEU235");
			showText(scene, 14, 18, Orientation.LEFT, rod == null ? ItemStack.EMPTY : new ItemStack(rod));
			scene.add(new ActionWait(30));
			scene.add(new ActionRemoveActor(0));

			showText(scene, 0, 5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.6"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));

			scene.add(new ActionRotateBy(45, -60, 20));

			scene.add(new ActionWait(10));

			script.addScene(scene);
		}
		{
			// FLOOR PAINT
			JarScene scene = new JarScene(script);
			scene.add(new ActionSetBlock(center - 1, 0, center - 1, Blocks.MAGENTA_WOOL));
			scene.add(new ActionSetBlock(center - 1, 0, center + 1, Blocks.MAGENTA_WOOL));
			scene.add(new ActionSetBlock(center + 1, 0, center - 1, Blocks.MAGENTA_WOOL));
			scene.add(new ActionSetBlock(center + 1, 0, center + 1, Blocks.MAGENTA_WOOL));
			scene.add(new ActionWait(10));

			showText(scene, 0, -25, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.7"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(10));
			for(int i = 1; i <= 4; i++) {
				var lime = i % 2 == 0 ? Blocks.LIME_WOOL : Blocks.LIME_CONCRETE;
				scene.add(new ActionSetBlock(center + i, 0, center, lime));
				scene.add(new ActionSetBlock(center - i, 0, center, lime));
				scene.add(new ActionSetBlock(center, 0, center + i, lime));
				scene.add(new ActionSetBlock(center, 0, center - i, lime));
			}
			scene.add(new ActionWait(10));
			showText(scene, 0, -25, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.8"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(10));

			script.addScene(scene);
		}
		{
			// CORNER FLUX
			JarScene scene = new JarScene(script);
			scene.add(new ActionCreateActor(1, new ActorVisText(script, center - 1, 1, center - 1, 0xFFFFFF, 1.5f, false, "1/2")));
			scene.add(new ActionCreateActor(2, new ActorVisText(script, center + 1, 1, center - 1, 0xFFFFFF, 1.5f, false, "1/2")));
			scene.add(new ActionCreateActor(3, new ActorVisText(script, center - 1, 1, center + 1, 0xFFFFFF, 1.5f, false, "1/2")));
			scene.add(new ActionCreateActor(4, new ActorVisText(script, center + 1, 1, center + 1, 0xFFFFFF, 1.5f, false, "1/2")));
			scene.add(new ActionWait(20 * 2));
			scene.add(new ActionRemoveActor(1));
			scene.add(new ActionRemoveActor(2));
			scene.add(new ActionRemoveActor(3));
			scene.add(new ActionRemoveActor(4));
			scene.add(new ActionWait(10));
			script.addScene(scene);
		}
		{
			// LINEAR FLUX: a number every two blocks, as far as the 9x9 floor reaches (D-3077; the labels used to run on to 20 blocks, in the air)
			JarScene scene = new JarScene(script);
			for(int i = 1; i <= FLUX_LABELS; i++) {
				scene.add(new ActionCreateActor(i, new ActorVisText(script, center + i * 2, 1, center, 0xFFFFFF, 1.5f, false, "1/" + (int) Math.pow(2, i))));
				scene.add(new ActionCreateActor(i + 11, new ActorVisText(script, center - i * 2, 1, center, 0xFFFFFF, 1.5f, false, "1/" + (int) Math.pow(2, i))));
				scene.add(new ActionCreateActor(i + 22, new ActorVisText(script, center, 1, center + i * 2, 0xFFFFFF, 1.5f, false, "1/" + (int) Math.pow(2, i))));
				scene.add(new ActionCreateActor(i + 33, new ActorVisText(script, center, 1, center - i * 2, 0xFFFFFF, 1.5f, false, "1/" + (int) Math.pow(2, i))));
			}
			scene.add(new ActionWait(20 * 3));
			for(int i = 1; i <= FLUX_LABELS; i++) {
				scene.add(new ActionRemoveActor(i));
				scene.add(new ActionRemoveActor(i + 11));
				scene.add(new ActionRemoveActor(i + 22));
				scene.add(new ActionRemoveActor(i + 33));
			}
			scene.add(new ActionWait(10));
			scene.add(new ActionRotateBy(-45, 60, 20));
			scene.add(new ActionWait(10));
			addControl(scene, center + 2, 1, center, 0);
			addControl(scene, center - 2, 1, center, 0);
			addControl(scene, center, 1, center + 2, 0);
			addControl(scene, center, 1, center - 2, 0);
			scene.add(new ActionWait(10));
			script.addScene(scene);
		}
		{
			// CONTROL RODS
			JarScene scene = new JarScene(script);
			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.9"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			removeControl(scene, center + 2, 1, center);
			removeControl(scene, center - 2, 1, center);
			removeControl(scene, center, 1, center + 2);
			removeControl(scene, center, 1, center - 2);
			scene.add(new ActionWait(20));
			addControl(scene, center + 2, 1, center, 1);
			addControl(scene, center - 2, 1, center, 1);
			addControl(scene, center, 1, center + 2, 1);
			addControl(scene, center, 1, center - 2, 1);
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.a"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			removeControl(scene, center + 2, 1, center);
			removeControl(scene, center - 2, 1, center);
			removeControl(scene, center, 1, center + 2);
			removeControl(scene, center, 1, center - 2);
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center + 2, 1, center, channel));
			scene.add(new ActionSetBlock(center - 2, 1, center, channel));
			scene.add(new ActionSetBlock(center, 1, center + 2, channel));
			scene.add(new ActionSetBlock(center, 1, center - 2, channel));
			scene.add(new ActionWait(10));

			script.addScene(scene);
		}
		{
			// MODERATING
			JarScene scene = new JarScene(script);

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.b"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.c"));
			scene.add(new ActionWait(20 * 5));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center + 2, 1, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center - 2, 1, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 1, center + 2, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 1, center - 2, Blocks.AIR));
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center + 2, 1, center, graphite));
			scene.add(new ActionSetBlock(center - 2, 1, center, graphite));
			scene.add(new ActionSetBlock(center, 1, center + 2, graphite));
			scene.add(new ActionSetBlock(center, 1, center - 2, graphite));
			scene.add(new ActionWait(10));

			showText(scene, 0, -5, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.d"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));

			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center + 2, 1, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center - 2, 1, center, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 1, center + 2, Blocks.AIR));
			scene.add(new ActionSetBlock(center, 1, center - 2, Blocks.AIR));
			scene.add(new ActionWait(20));
			for(int x = 0; x < world.sizeX; x++) {
				for(int z = 0; z <= world.sizeZ; z++)
					scene.add(new ActionSetBlock(x, 0, z, concrete));
			}
			scene.add(new ActionWait(20));
			scene.add(new ActionSetBlock(center, 2, center, element));
			scene.add(new ActionWait(3));
			scene.add(new ActionSetBlock(center, 3, center, element));
			scene.add(new ActionWait(3));

			script.addScene(scene);
		}
		{
			// STACKING
			JarScene scene = new JarScene(script);
			showText(scene, 0, -30, Orientation.BOTTOM, I18nUtil.resolveKey("cannery.leafia.pwr.e"));
			scene.add(new ActionWait(20 * 4));
			scene.add(new ActionRemoveActor(0));
			script.addScene(scene);
		}

		return script;
	}

	/** Upstream's {@code showText}: the copper panel, 200 wide, as actor 0. */
	public void showText(JarScene scene, int x, int y, Orientation orient, Object text) {
		scene.add(new ActionCreateActor(0, copper(metrics(), x, y, orient, 200, text)));
	}

	/** Upstream's id for the actor at a cell: the cell's index plus 300, clear of the text's small ids. */
	public int getHash(JarScene scene, int x, int y, int z) {
		int sizeX = scene.script.world.sizeX;
		int sizeY = scene.script.world.sizeY;
		int sizeZ = scene.script.world.sizeZ;
		return x + y * sizeX + z * sizeX * sizeY + 300;
	}

	public void addControl(JarScene scene, int x, int y, int z, int variant) {
		int hash = getHash(scene, x, y, z);
		CompoundTag data = new CompoundTag();
		data.putDouble("x", x);
		data.putDouble("y", y);
		data.putDouble("z", z);
		data.putInt("variant", variant);
		scene.add(new ActionCreateActor(hash, new ActorTileEntity(new ActorControlRods(), data)));
	}

	public void removeControl(JarScene scene, int x, int y, int z) {
		int hash = getHash(scene, x, y, z);
		scene.add(new ActionRemoveActor(hash));
	}

	/** A PWR control rod column in the jar: an actor, with no block behind it. The tag's {@code variant} 1 is the classic skin. */
	public static class ActorControlRods implements ITileActorRenderer {

		@Override
		public void renderActor(WorldInAJar world, int ticks, float interp, CompoundTag data, PoseStack pose, MultiBufferSource buffers) {
			CanneryActorRenders.controlRods(world, data, pose, buffers);
		}

		@Override
		public void updateActor(int ticks, CompoundTag data) { }
	}
}
