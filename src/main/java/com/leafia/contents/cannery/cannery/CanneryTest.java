package com.leafia.contents.cannery.cannery;

import com.hbm.blocks.ModBlocks;
import com.hbm.wiaj.JarScene;
import com.hbm.wiaj.JarScript;
import com.hbm.wiaj.WorldInAJar;
import com.hbm.wiaj.actions.ActionCreateActor;
import com.hbm.wiaj.actions.ActionRotateBy;
import com.hbm.wiaj.actions.ActionSetBlock;
import com.hbm.wiaj.actions.ActionWait;
import com.hbm.wiaj.cannery.CanneryBase;
import com.leafia.contents.LeafiaBlocks;
import com.leafia.contents.cannery.actors.ActorVisFrame;
import com.leafia.contents.cannery.actors.ActorVisText;

import net.minecraft.world.item.ItemStack;

/**
 * The test topic: a floor of smooth concrete laid from one side to the other, a green frame and the word "Test" in the middle cell,
 * and a turn of the jar. It exists to try the two debug actors ({@link ActorVisFrame}, {@link ActorVisText}) and is opened from the
 * debug Forge-fluid source, upstream's {@code TestBlocks.ffsource}.
 *
 * <p>Upstream: {@code com/leafia/contents/cannery/cannery/CanneryTest.java}, wait for wait. The icon is the PWR fuel rod's, as
 * upstream's; with the PWR switched off there is none to show. The name is the bare key {@code cannery.test}, which upstream's
 * language file does not define; the addon's does (D-3076).
 */
public class CanneryTest extends CanneryBase {

	@Override
	public ItemStack getIcon() {
		return LeafiaBlocks.lwr_element == null ? ItemStack.EMPTY : new ItemStack(LeafiaBlocks.lwr_element.get());
	}

	@Override
	public String getName() {
		return "cannery.test";
	}

	@Override
	public JarScript createScript() {

		WorldInAJar world = new WorldInAJar(5, 5, 5);
		JarScript script = new JarScript(world);
		{
			JarScene scene = new JarScene(script);
			for(int x = world.sizeX - 1; x >= 0; x--) {
				for(int z = 0; z < world.sizeZ; z++)
					scene.add(new ActionSetBlock(x, 0, z, ModBlocks.concrete_smooth.get()));

				scene.add(new ActionWait(2));
			}
			scene.add(new ActionCreateActor(0, new ActorVisFrame(script, 1, 1, 1, 0x00FF00)));
			scene.add(new ActionCreateActor(1, new ActorVisText(script, 1, 1, 1, 0x00FF00, 1, "Test")));
			scene.add(new ActionWait(20));
			scene.add(new ActionRotateBy(45, -60, 20));

			script.addScene(scene);
		}

		return script;
	}
}
