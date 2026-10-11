package com.kintil555.solarsystem.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.SeedPayload;
import com.kintil555.solarsystem.SolarSystemMod;
import com.kintil555.solarsystem.StatePayload;
import com.kintil555.solarsystem.system.SolarPhysics;
import com.kintil555.solarsystem.system.SolarSystem;

public final class SolarSystemClient implements ClientModInitializer {
	/** Aim preview shown while the launch key is held. */
	record Preview(int planet, float power, Vec3 dir, SolarPhysics.Prediction prediction, double[] from) {}

	private static final int KEY_G = 71;
	private static final int KEY_V = 86;

	private static volatile SolarSystemRenderer renderer;
	private static volatile SolarSystem system;
	private static volatile SolarPhysics physics;
	private static volatile Preview preview;
	private static long clientTick;
	private static long rxTick;
	private static KeyMapping toolbarKey;
	private static KeyMapping cameraKey;


	static SolarSystemRenderer renderer() {
		return renderer;
	}

	static SolarSystem system() {
		return system;
	}

	static SolarPhysics physics() {
		return physics;
	}

	static Preview preview() {
		return preview;
	}

	static void setPreview(Preview next) {
		preview = next;
	}

	static KeyMapping toolbarKey() {
		return toolbarKey;
	}

	static KeyMapping cameraKey() {
		return cameraKey;
	}

	/** Ticks since the last server state (for smooth extrapolation). */
	static double syncAge(float partial) {
		return Math.max(0.0, Math.min(3.0, clientTick - rxTick + partial));
	}

	@Override
	public void onInitializeClient() {
		toolbarKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.solarsystem.toolbar", InputConstants.Type.KEYSYM, KEY_G, KeyMapping.Category.GAMEPLAY));
		cameraKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.solarsystem.camera", InputConstants.Type.KEYSYM, KEY_V, KeyMapping.Category.GAMEPLAY));

		ClientPlayNetworking.registerGlobalReceiver(SeedPayload.TYPE, (payload, context) -> {
			SolarView.reset();
			SolarSystem s = SolarSystem.generate(payload.seed());
			long time = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
			system = s;
			physics = SolarPhysics.initial(s, time);
			rxTick = clientTick;
			renderer = SolarSystemRenderer.create(s);
		});
		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> {
			SolarPhysics p = physics;
			if (p != null) {
				p.load(payload.data());
				rxTick = clientTick;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			renderer = null;
			system = null;
			physics = null;
			preview = null;
			SolarView.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(SolarSystemClient::onTick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SolarSystemRenderer::onCollectSubmits);
	}

	private static void onTick(Minecraft mc) {
		clientTick++;
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		SolarPhysics phys = physics;
		SolarSystem sys = system;
		boolean inSpace = player != null && level != null && phys != null && sys != null
				&& level.dimensionTypeRegistration().is(SolarSystemMod.SPACE_TYPE);
		if (inSpace) {
			SolarTools.tick(mc, phys, sys);
			if (SolarView.mode == SolarView.Mode.FREE && !(mc.gui.screen() instanceof ToolbarScreen)) {
				SolarView.setMode(SolarView.Mode.EXPLORE);
			}
			while (toolbarKey.consumeClick()) {
				if (mc.gui.screen() == null) {
					mc.gui.setScreen(new ToolbarScreen());
				}
			}
			while (cameraKey.consumeClick()) {
				if (mc.gui.screen() == null) {
					SolarView.setMode(SolarView.Mode.FREE);
					mc.gui.setScreen(new ToolbarScreen());
				}
			}
		} else if (SolarView.mode == SolarView.Mode.FREE) {
			SolarView.setMode(SolarView.Mode.EXPLORE);
		}
		if (!inSpace) {
			preview = null;
			return;
		}
		if (SolarView.mode == SolarView.Mode.FREE) {
			FreeLaunch.tick(phys);
		}
	}
}
