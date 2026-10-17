package com.leafia.contents.machines.controlpanel.core.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.leafia.contents.machines.controlpanel.core.Control;
import com.leafia.contents.machines.controlpanel.core.types.DataValue;

import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Which renderer draws which control (D-3065). NTM-CE put {@code render()} on every {@code Control} subclass, which put GL
 * calls in classes a dedicated server must load (§2.2); the draw halves live here instead, keyed by control class, in six
 * files by family: {@link ButtonRenderers}, {@link SwitchRenderers}, {@link SliderKnobRenderers}, {@link DialRenderers},
 * {@link DisplayRenderers} and {@link LampLabelRenderers}.
 *
 * <p>Batches 11 and 12 register their instruments here with {@link #register}; a control with no renderer is not drawn
 * (CE's {@code UnknownControl.render} was empty too), so a panel saved with an instrument whose renderer is not in yet shows
 * nothing in its place and keeps the control.
 */
@OnlyIn(Dist.CLIENT)
public final class ControlRenderers {

	private ControlRenderers() {}

	/** Draws one control at its own origin, in panel units (see {@link ControlDraw}). */
	@FunctionalInterface
	public interface ControlRenderer<C extends Control> {
		void render(C control, ControlDraw draw);
	}

	private static final Map<Class<?>, ControlRenderer<?>> RENDERERS = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Boolean> INSETS = new ConcurrentHashMap<>();

	static {
		ButtonRenderers.register();
		SwitchRenderers.register();
		SliderKnobRenderers.register();
		DialRenderers.register();
		DisplayRenderers.register();
		LampLabelRenderers.register();
		// Leafia's instruments (CCP-INSTRUMENTS-1, D-3071).
		com.leafia.contents.machines.controlpanel.instruments.client.InstrumentRenderers.register();
	}

	public static <C extends Control> void register(Class<C> type, ControlRenderer<? super C> renderer) {
		RENDERERS.put(type, renderer);
	}

	/**
	 * Marks a control class as drawn in the first pass, under the slab. Leafia's {@code MixinRrenderControlPanel} drew its
	 * {@code NixieDisplay} (a tube set into the panel) before the slab and everything else after it; batch 12 calls this for it.
	 */
	public static void registerInset(Class<? extends Control> type) {
		INSETS.put(type, Boolean.TRUE);
	}

	@SuppressWarnings("unchecked")
	public static ControlRenderer<Control> find(Control control) {
		for(Class<?> c = control.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			ControlRenderer<?> r = RENDERERS.get(c);
			if(r != null)
				return (ControlRenderer<Control>) r;
		}
		return null;
	}

	public static boolean isInset(Control control) {
		for(Class<?> c = control.getClass(); c != null && c != Object.class; c = c.getSuperclass())
			if(INSETS.containsKey(c))
				return true;
		return false;
	}

	// ------------------------------------------------------------------ colours

	/** CE's {@code EnumDyeColor.getColorComponentValues}: the dye's red, green and blue in 0..1. */
	public static float[] dyeRgb(DyeColor dye) {
		int c = dye.getTextureDiffuseColor();
		return new float[]{((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F};
	}

	/** A variable holding a dye (an enum value), as its colour components. An unreadable one is the first dye. */
	public static float[] dyeRgb(DataValue value) {
		return dyeRgb(value.getEnum(DyeColor.class));
	}
}
