package com.kintil555.solarsystem.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kintil555.solarsystem.client.FreeCamera;

/** Lets {@link FreeCamera} take over the view after vanilla has aligned the camera with the player. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void solarsystem$applyFreeCamera(float partialTicks, CallbackInfo ci) {
		if (FreeCamera.active()) {
			setRotation(FreeCamera.yaw(), FreeCamera.pitch());
			setPosition(FreeCamera.position());
		}
	}
}
