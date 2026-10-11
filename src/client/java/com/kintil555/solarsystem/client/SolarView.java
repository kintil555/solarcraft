package com.kintil555.solarsystem.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.system.SolarSystem;
import com.kintil555.solarsystem.system.SolarSystem.Planet;

/** Client-side view state: camera mode, solar style, selected tool and the objects the tools created. */
final class SolarView {
	enum Mode { EXPLORE, FREE }

	enum Tool { NONE, LASER, RING, ASTEROID, MOON }

	enum Tab { CAMERA, TOOLS, KEYS, STYLE }

	/** A moon placed by the moon tool; its orbit is relative to the (displayed) planet. */
	record Moon(int planet, float orbit, double period, double phase, float size, float incline) {}

	/** A test particle orbiting the Sun; position/velocity are Sun-relative real (unmapped) coordinates. */
	static final class Asteroid {
		final double[] p = new double[3];
		final double[] v = new double[3];
		final float size;
		final float spin;
		final float ax;
		final float ay;
		final float az;

		Asteroid(float size, float spin, float ax, float ay, float az) {
			this.size = size;
			this.spin = spin;
			this.ax = ax;
			this.ay = ay;
			this.az = az;
		}
	}

	/** A short-lived laser beam in world coordinates. */
	static final class Beam {
		final float[] pts;
		final int maxTicks;
		int ticksLeft;

		Beam(float[] pts, int ticks) {
			this.pts = pts;
			this.maxTicks = ticks;
			this.ticksLeft = ticks;
		}
	}

	static Mode mode = Mode.EXPLORE;
	static SolarStyle style = SolarStyle.ENLARGED;
	static Tool tool = Tool.NONE;
	static Tab tab = Tab.CAMERA;
	static boolean expanded;

	static final Set<Integer> userRings = new HashSet<>();
	static final List<Moon> moons = new ArrayList<>();
	static final List<Asteroid> asteroids = new ArrayList<>();
	static final List<Beam> beams = new ArrayList<>();

	private static SolarStyle cachedStyle;
	private static SolarSystem cachedSystem;
	private static RadialMap cachedMap;

	private SolarView() {}

	static RadialMap map(SolarSystem system) {
		if (cachedMap == null || cachedSystem != system || cachedStyle != style) {
			cachedMap = style.buildMap(system);
			cachedSystem = system;
			cachedStyle = style;
		}
		return cachedMap;
	}

	/** Sun-relative real position -> displayed world position, written to {@code out}. */
	static void toVisual(RadialMap map, double rx, double ry, double rz, double[] out) {
		double s = map.scale(Math.sqrt(rx * rx + ry * ry + rz * rz));
		out[0] = SolarSystem.SUN_CENTER.x + rx * s;
		out[1] = SolarSystem.SUN_CENTER.y + ry * s;
		out[2] = SolarSystem.SUN_CENTER.z + rz * s;
	}

	static float radiusOf(Planet p) {
		return p.radius() * style.radiusScale(p.kind());
	}

	static boolean hasRing(Planet p) {
		return userRings.contains(p.index()) || style.hasNaturalRing(p);
	}

	static int moonCount(int planet) {
		int n = 0;
		for (Moon m : moons) {
			if (m.planet() == planet) {
				n++;
			}
		}
		return n;
	}

	static void setMode(Mode next) {
		if (mode == next) {
			return;
		}
		mode = next;
		if (next == Mode.FREE) {
			LocalPlayer player = Minecraft.getInstance().player;
			Vec3 eye = player != null ? player.getEyePosition() : SolarSystem.SUN_CENTER.add(0.0, 40.0, 260.0);
			FreeCamera.enter(eye, SolarSystem.SUN_CENTER);
		} else {
			FreeCamera.exit();
		}
	}

	static void reset() {
		setMode(Mode.EXPLORE);
		tool = Tool.NONE;
		userRings.clear();
		moons.clear();
		asteroids.clear();
		beams.clear();
		cachedMap = null;
	}
}
