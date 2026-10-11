package com.kintil555.solarsystem.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.LaunchPayload;
import com.kintil555.solarsystem.system.SolarPhysics;

/**
 * Planet launching, Free mode only: hover = white outline, click = selected (green fill), then pull with the
 * left button like a slingshot; the planet is thrown opposite to the pull.
 */
final class FreeLaunch {
	private static final double FULL_PULL_PX = 140.0;
	private static final float MIN_POWER = 0.04f;
	private static final int PREVIEW_HORIZON = 9000;
	private static final int PREVIEW_EVERY = 30;
	private static final int PREVIEW_SUBSTEPS = 2;

	static int hovered = -1;
	static int selected = -1;

	private static boolean aiming;
	private static boolean dirty;
	private static double pullX;
	private static double pullY;
	private static float power;
	private static Vec3 dir;
	private static boolean crashing;

	private FreeLaunch() {}

	static void clear() {
		hovered = -1;
		selected = -1;
		aiming = false;
		dirty = false;
		power = 0.0f;
		dir = null;
		crashing = false;
		SolarSystemClient.setPreview(null);
	}

	/** Hover only matters while no tool is active. */
	static void updateHover(Minecraft mc, double x, double y, boolean overUi) {
		if (aiming || overUi || SolarView.mode != SolarView.Mode.FREE || SolarView.tool != SolarView.Tool.NONE
				|| SolarSystemClient.system() == null) {
			hovered = -1;
			return;
		}
		SolarTools.Ray ray = SolarTools.cursorRay(mc, x, y);
		hovered = SolarTools.pick(ray.origin(), ray.dir()).planet();
	}

	/** A plain click (no drag): select the planet under the cursor, or drop the selection. */
	static void click(Minecraft mc, double x, double y) {
		if (SolarSystemClient.system() == null) {
			return;
		}
		SolarTools.Ray ray = SolarTools.cursorRay(mc, x, y);
		int hit = SolarTools.pick(ray.origin(), ray.dir()).planet();
		selected = hit == selected ? -1 : hit;
		power = 0.0f;
		dir = null;
		SolarSystemClient.setPreview(null);
	}

	static boolean canAim() {
		return selected >= 0 && SolarView.tool == SolarView.Tool.NONE && SolarView.mode == SolarView.Mode.FREE;
	}

	/** Called while the pull is being dragged; {@code dx/dy} is the offset from the press point in GUI pixels. */
	static void drag(double dx, double dy) {
		aiming = true;
		pullX = dx;
		pullY = dy;
		double len = Math.sqrt(dx * dx + dy * dy);
		power = (float) Math.min(1.0, len / FULL_PULL_PX);
		if (len < 1e-6) {
			dir = null;
			return;
		}
		// pull vector in the camera plane (screen y points down), thrown the opposite way
		Vec3 r = FreeCamera.right(FreeCamera.yaw(), FreeCamera.pitch());
		Vec3 u = FreeCamera.up(FreeCamera.yaw(), FreeCamera.pitch());
		dir = r.scale(-dx).add(u.scale(dy)).normalize();
		dirty = true;
	}

	static void release() {
		if (aiming && selected >= 0 && dir != null && power > MIN_POWER) {
			ClientPlayNetworking.send(new LaunchPayload(selected, (float) dir.x, (float) dir.y, (float) dir.z, power));
		}
		selected = -1;
		aiming = false;
		dirty = false;
		power = 0.0f;
		dir = null;
		crashing = false;
		SolarSystemClient.setPreview(null);
	}

	/** Recomputes the trajectory preview at most once per tick. */
	static void tick(SolarPhysics phys) {
		if (!dirty || !aiming || selected < 0 || dir == null || power <= 0.0f || selected >= phys.count()) {
			return;
		}
		dirty = false;
		SolarPhysics now = phys.copy();
		now.advance(SolarSystemClient.syncAge(0.0f));
		double dv = power * SolarPhysics.MAX_DV;
		SolarPhysics.Prediction prediction = now.predict(selected, dir.x * dv, dir.y * dv, dir.z * dv,
				PREVIEW_HORIZON, PREVIEW_EVERY, PREVIEW_SUBSTEPS);
		crashing = prediction.crashed();
		SolarSystemClient.setPreview(new SolarSystemClient.Preview(selected, power, dir, prediction,
				new double[] {now.px(selected), now.py(selected), now.pz(selected)}));
	}

	/** Text shown next to the cursor while pulling. */
	static String label() {
		if (!aiming || power <= 0.0f) {
			return null;
		}
		return "Daya " + Math.round(power * 100) + "%" + (crashing ? "  - menabrak Matahari!" : "");
	}
}
