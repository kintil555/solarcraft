package com.kintil555.solarsystem.client;

import java.util.function.Supplier;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.system.SolarSystem;

/** Orbit camera used by the Free mode: scroll = zoom, left drag = orbit, right drag = pan. */
public final class FreeCamera {
	private static final double MIN_DISTANCE = 4.0;
	private static final double MAX_DISTANCE = 2500.0;
	private static final float ORBIT_SPEED = 0.4f;
	private static final double PAN_SPEED = 0.0035;
	private static final double ZOOM_STEP = 0.12;

	private static boolean active;
	private static double tx;
	private static double ty;
	private static double tz;
	private static double distance = 260.0;
	private static float yaw;
	private static float pitch = 25.0f;
	private static Supplier<Vec3> follow;

	private FreeCamera() {}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** Camera position for this frame (also advances a followed target). */
	public static Vec3 position() {
		if (follow != null) {
			Vec3 t = follow.get();
			if (t != null) {
				tx = t.x;
				ty = t.y;
				tz = t.z;
			}
		}
		Vec3 f = forward(yaw, pitch);
		return new Vec3(tx - f.x * distance, ty - f.y * distance, tz - f.z * distance);
	}

	static Vec3 forward(float yawDeg, float pitchDeg) {
		double y = Math.toRadians(yawDeg);
		double p = Math.toRadians(pitchDeg);
		return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
	}

	static Vec3 right(float yawDeg, float pitchDeg) {
		Vec3 r = forward(yawDeg, pitchDeg).cross(new Vec3(0.0, 1.0, 0.0));
		return r.lengthSqr() < 1e-9 ? new Vec3(-1.0, 0.0, 0.0) : r.normalize();
	}

	static Vec3 up(float yawDeg, float pitchDeg) {
		return right(yawDeg, pitchDeg).cross(forward(yawDeg, pitchDeg)).normalize();
	}

	/** Starts at {@code eye}, looking at {@code target}. */
	static void enter(Vec3 eye, Vec3 target) {
		follow = null;
		tx = target.x;
		ty = target.y;
		tz = target.z;
		distance = Mth.clamp(eye.distanceTo(target), 30.0, MAX_DISTANCE);
		float[] look = SolarSystem.lookAt(eye, target);
		yaw = look[0];
		pitch = Mth.clamp(look[1], -85.0f, 85.0f);
		active = true;
	}

	static void exit() {
		active = false;
		follow = null;
	}

	static void focus(Supplier<Vec3> target) {
		follow = target;
	}

	static void orbit(double dx, double dy) {
		yaw -= (float) dx * ORBIT_SPEED;
		pitch = Mth.clamp(pitch + (float) dy * ORBIT_SPEED, -89.0f, 89.0f);
	}

	static void pan(double dx, double dy) {
		follow = null;
		Vec3 r = right(yaw, pitch);
		Vec3 u = up(yaw, pitch);
		double k = distance * PAN_SPEED;
		tx += (-r.x * dx + u.x * dy) * k;
		ty += (-r.y * dx + u.y * dy) * k;
		tz += (-r.z * dx + u.z * dy) * k;
	}

	static void zoom(double scroll) {
		distance = Mth.clamp(distance * (1.0 - scroll * ZOOM_STEP), MIN_DISTANCE, MAX_DISTANCE);
	}
}
