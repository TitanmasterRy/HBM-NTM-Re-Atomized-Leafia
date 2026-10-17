package com.custom_hbm;

import java.util.Random;

import com.leafia.init.LeafiaSounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The screen the title screen shows one time in 512 (D-3026): bedrock, a red grid of "remove1.7.10", a countdown in the middle that
 * shakes, and a looped track. It will not take a key press, not even escape, and closes itself when the countdown ends.
 *
 * <p>Upstream: {@code com/custom_hbm/GuiEasterEgg.java}. The sound was a 1.12.2 {@code PositionedSoundRecord} flagged repeating with
 * no attenuation; the tile background was {@code drawBackground} binding {@code textures/blocks/bedrock.png} and filling the screen with
 * UVs running 0 to size/32 in a (64, 64, 64) tint, which is what 1.21.1's own menu background does with its dirt (a 32-pixel repeat of
 * a 16-pixel texture). The countdown was a pushed matrix scaled 2x and jittered with a gaussian each frame.
 */
@OnlyIn(Dist.CLIENT)
public class GuiEasterEgg extends Screen {

	/** How long it stays up. */
	public static final long DURATION_MS = 22000;
	public static final String MESSAGE = "remove1.7.10";
	public static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/block/bedrock.png");

	public SoundInstance music;
	private final long startTimestamp;
	private final Random rand = new Random();

	public GuiEasterEgg() {
		super(Component.empty());
		startTimestamp = System.currentTimeMillis();
	}

	/** Upstream's `loopedMusic`: the whole track, at full volume, everywhere, repeating. */
	public static SoundInstance loopedMusic(SoundEvent sound) {
		return new SimpleSoundInstance(sound.getLocation(), SoundSource.MASTER, 1.0F, 1.0F, RandomSource.create(), true, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
	}

	/** Whole seconds left, as upstream prints them: the ceiling. */
	public static int secondsLeft(long timeRemainingMs) {
		return (int) Math.ceil(timeRemainingMs / 1000D);
	}

	@Override
	protected void init() {
		if(music == null) {
			music = loopedMusic(LeafiaSounds.bgm013_50.get());
			minecraft.getSoundManager().play(music);
		}
	}

	@Override
	public void removed() {
		if(music != null) minecraft.getSoundManager().stop(music);
		super.removed();
	}

	/** Upstream's `keyTyped` was empty ("nope!"): nothing closes it. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.setColor(64 / 255F, 64 / 255F, 64 / 255F, 1F);
		graphics.blit(BACKGROUND, 0, 0, 0, 0F, 0F, width, height, 32, 32);
		graphics.setColor(1F, 1F, 1F, 1F);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);

		int leng = font.width(MESSAGE);
		for(int i = 0; i <= width / leng; i++) {
			for(int j = 0; j <= height / 10; j++) {
				int brightness = 100 + rand.nextInt(156);
				graphics.drawString(font, MESSAGE, i * leng, j * 10, 0xFF000000 | (brightness << 16), false);
			}
		}

		long timeRemaining = (startTimestamp + DURATION_MS) - System.currentTimeMillis();
		String countdown = Integer.toString(secondsLeft(timeRemaining));
		graphics.pose().pushPose();
		graphics.pose().translate(width / 2D, height / 2D, 0);
		graphics.pose().scale(2F, 2F, 1F);
		graphics.pose().translate(-font.width(countdown) / 2D, -4, 0);
		graphics.pose().translate(rand.nextGaussian(), rand.nextGaussian(), 0);
		graphics.drawString(font, countdown, 0, 0, 0xFFFF0000, true);
		graphics.pose().popPose();
		if(timeRemaining <= 0) minecraft.setScreen(null);
	}
}
