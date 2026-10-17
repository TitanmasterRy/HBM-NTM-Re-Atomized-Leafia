package com.leafia.contents.effects;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws nothing, not even a shadow or fire - what {@code NukeLCA} looks like.
 *
 * <p>Upstream: {@code com.leafia.contents.effects.RenderNothing}, a {@code Render<Entity>} whose
 * {@code doRender} and {@code doRenderShadowAndFire} are empty and whose texture is null. 1.21.1 ships the
 * same thing as vanilla's {@link NoopRenderer} (no model, no shadow - {@code shadowRadius} stays 0 - and
 * {@code displayFireAnimation} is the entity's own, false for an entity that is not on fire, which a blast
 * never is), so this is that, under upstream's name and with upstream's {@code FACTORY}.
 */
@OnlyIn(Dist.CLIENT)
public class RenderNothing<T extends Entity> extends NoopRenderer<T> {

	public RenderNothing(EntityRendererProvider.Context context) {
		super(context);
	}

	/** Upstream's {@code FACTORY}. */
	public static <T extends Entity> EntityRendererProvider<T> factory() {
		return RenderNothing::new;
	}
}
