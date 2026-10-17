package com.leafia.contents.building.doors.renderers;

import com.hbm.blockentity.generic.BlockEntityDoorGeneric;
import com.hbm.render.blockentity.door.IRenderDoors;
import com.hbm.render.util.ObjUtil;
import com.leafia.lib.LeafiaRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * What the two crimson doors' renderers share: a frame that never moves, two leaves that slide apart
 * along the door's width and are cut off at the frame, and an indicator light that changes colour.
 *
 * <p>Upstream has two classes whose bodies differ only in four numbers, and they are kept as two
 * classes ({@link CrimsonDoorSmallRender}, {@link CrimsonDoorLargeRender}) so the ledger and the
 * names match; the body is here once.
 *
 * <ul>
 * <li><b>The slide</b> is the door's {@code DOOR} animation channel, {@code [1]} of it: 0 shut, 1 open,
 *     taken from the running animation if there is one and from the state if there is not.</li>
 * <li><b>The cut</b> is upstream's pair of clip planes at plus and minus the frame's half width less
 *     a millimetre ({@link DoorClip}).</li>
 * <li><b>The light</b> is the solid sheet, tinted - green open, red shut, amber in between - and drawn
 *     full-bright, which is upstream's {@code setLightmapTextureCoords(240, 240)} with lighting off.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
abstract class CrimsonDoorRenderBase implements IRenderDoors {

	protected final ModelResourceLocation left;
	protected final ModelResourceLocation right;
	protected final ModelResourceLocation frame;
	protected final ModelResourceLocation light;

	/** How far each leaf slides, at full open, in blocks. */
	private final double slideLeft;
	private final double slideRight;
	/** Upstream's clip distance: the plane sits this far either side of the door's centre. */
	private final double clip;

	CrimsonDoorRenderBase(String name, double slideLeft, double slideRight, double clip) {
		this.left = standalone(name + "_left");
		this.right = standalone(name + "_right");
		this.frame = standalone(name + "_frame");
		this.light = standalone(name + "_light");
		this.slideLeft = slideLeft;
		this.slideRight = slideRight;
		this.clip = clip;
	}

	static ModelResourceLocation standalone(String path) {
		return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(LeafiaRef.MODID, "block/" + path));
	}

	/** The four models, for {@code LeafiaClient} to register: nothing references them, so nothing else bakes them. */
	public ModelResourceLocation[] models() {
		return new ModelResourceLocation[] {left, right, frame, light};
	}

	@Override
	public void render(BlockEntityDoorGeneric door, PoseStack matrix, VertexConsumer buffer, int packedLight, int overlay) {

		double ratio = 0;
		if(door.state == BlockEntityDoorGeneric.STATE_OPEN)
			ratio = 1;
		if(door.currentAnimation != null)
			ratio = IRenderDoors.getRelevantTransformation("DOOR", door.currentAnimation)[1];

		DoorClip.render(ObjUtil.getModel(left), matrix, buffer, packedLight, overlay, slideLeft * ratio, -clip, clip);
		DoorClip.render(ObjUtil.getModel(right), matrix, buffer, packedLight, overlay, -slideRight * ratio, -clip, clip);

		ObjUtil.renderModel(ObjUtil.getModel(frame), matrix, buffer, packedLight, overlay);

		float r = 1F, g = 0.9F, b = 0F;
		if(door.state == BlockEntityDoorGeneric.STATE_OPEN) { r = 0F; g = 1F; b = 0F; }
		else if(door.state == BlockEntityDoorGeneric.STATE_CLOSED) { r = 1F; g = 0F; b = 0F; }

		ObjUtil.renderModel(ObjUtil.getModel(light), matrix, buffer, LightTexture.FULL_BRIGHT, overlay, r, g, b);
	}
}
