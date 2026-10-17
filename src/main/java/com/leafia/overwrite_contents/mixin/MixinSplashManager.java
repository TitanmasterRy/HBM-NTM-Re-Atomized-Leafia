package com.leafia.overwrite_contents.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.leafia.config.LeafiaFeatures;
import com.leafia.transformer.LeafiaGeneralLocal;

import net.minecraft.client.resources.SplashManager;

/**
 * The title screen's wacky splashes (D-3026). Upstream: {@code MixinGuiMainMenu_WackySplashes} redirected the
 * {@code splashes.isEmpty()} test in {@code GuiMainMenu}'s constructor so that {@code LeafiaGeneralLocal.injectWackySplashes} could
 * add to the list the menu had just read. 1.21.1 reads the list in {@code SplashManager} when resources reload, so this is the tail of
 * its {@code apply}, and the first mixin the addon has on the client. It reads the switch when it runs.
 */
@Mixin(SplashManager.class)
public abstract class MixinSplashManager {

	@Shadow @Final
	private List<String> splashes;

	@Inject(method = "apply(Ljava/util/List;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void leafia$injectWackySplashes(List<String> object, net.minecraft.server.packs.resources.ResourceManager manager,
			net.minecraft.util.profiling.ProfilerFiller profiler, CallbackInfo ci) {
		if(LeafiaFeatures.cosmeticOverhaul) LeafiaGeneralLocal.injectWackySplashes(splashes);
	}
}
