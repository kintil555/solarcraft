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
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.LaunchPayload;
import com.kintil555.solarsystem.SeedPayload;
import com.kintil555.solarsystem.SolarSystemMod;
import com.kintil555.solarsystem.StatePayload;
import com.kintil555.solarsystem.system.SolarPhysics;
import com.kintil555.solarsystem.system.SolarSystem;

public final class SolarSystemClient implements ClientModInitializer {
	/** Aim preview shown while the launch key is held. */
	record Preview(int planet, float power, Vec3 dir, SolarPhysics.Prediction prediction, double[] from) {}

	private static final int KEY_R = 82;
	private static final int KEY_G = 71;
	private static final int KEY_V = 86;
	private static final float FULL_CHARGE_TICKS = 60.0f;
	private static final int PREVIEW_HORIZON = 9000;
	private static final int PREVIEW_EVERY = 30;
	private static final int PREVIEW_SUBSTEPS = 2;

	private static volatile SolarSystemRenderer renderer;
	private static volatile SolarSystem system;
	private static volatile SolarPhysics physics;
	private static volatile Preview preview;
	private static long clientTick;
	private static long rxTick;
	private static KeyMapping launchKey;
	private static KeyMapping toolbarKey;
	private static KeyMapping cameraKey;

	private static boolean charging;
	private static int selected = -1;
	private static int chargeTicks;

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

	static KeyMapping launchKey() {
		return launchKey;
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
		launchKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.solarsystem.launch", InputConstants.Type.KEYSYM, KEY_R, KeyMapping.Category.GAMEPLAY));
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
			charging = false;
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
		if (!inSpace || mc.gui.screen() != null) {
			charging = false;
			preview = null;
			return;
		}

		boolean down = launchKey.isDown();
		SolarPhysics now = phys.copy();
		now.advance(syncAge(0.0f));

		if (down) {
			if (!charging) {
				int pick = SolarTools.pick(player.getEyePosition(), player.getViewVector(1.0f).normalize()).planet();
				if (pick >= 0) {
					charging = true;
					selected = pick;
					chargeTicks = 0;
				}
			}
			if (charging) {
				chargeTicks++;
				float power = Math.min(1.0f, chargeTicks / FULL_CHARGE_TICKS);
				Vec3 dir = player.getViewVector(1.0f).normalize();
				double dv = power * SolarPhysics.MAX_DV;
				SolarPhysics.Prediction prediction = now.predict(selected, dir.x * dv, dir.y * dv, dir.z * dv,
						PREVIEW_HORIZON, PREVIEW_EVERY, PREVIEW_SUBSTEPS);
				preview = new Preview(selected, power, dir, prediction,
						new double[] {now.px(selected), now.py(selected), now.pz(selected)});
				String text = "Daya " + Math.round(power * 100) + "%" + (prediction.crashed() ? "  - menabrak Matahari!" : "")
						+ "  (lepas R untuk meluncurkan)";
				player.sendOverlayMessage(Component.literal(text));
			}
		} else if (charging) {
			Preview p = preview;
			if (p != null && p.power() > 0.04f) {
				ClientPlayNetworking.send(new LaunchPayload(p.planet(), (float) p.dir().x, (float) p.dir().y, (float) p.dir().z, p.power()));
			}
			charging = false;
			preview = null;
		}
	}
}
