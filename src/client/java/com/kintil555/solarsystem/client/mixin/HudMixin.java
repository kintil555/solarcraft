package com.kintil555.solarsystem.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kintil555.solarsystem.client.FreeCamera;

/** Hides the whole HUD (hotbar, health, crosshair...) while the Free camera is active. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow
	private boolean isHidden;

	@Unique
	private boolean solarsystem$forced;
	@Unique
	private boolean solarsystem$saved;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void solarsystem$hideHead(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (FreeCamera.active()) {
			solarsystem$saved = isHidden;
			solarsystem$forced = true;
			isHidden = true;
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void solarsystem$hideReturn(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (solarsystem$forced) {
			isHidden = solarsystem$saved;
			solarsystem$forced = false;
		}
	}
}
