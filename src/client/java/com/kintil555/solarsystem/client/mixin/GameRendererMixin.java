package com.kintil555.solarsystem.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kintil555.solarsystem.client.FreeCamera;

/** No first-person hand in the Free camera. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void solarsystem$noHand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
		if (FreeCamera.active()) {
			ci.cancel();
		}
	}
}
