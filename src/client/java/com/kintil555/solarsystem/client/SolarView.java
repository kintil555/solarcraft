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
import com.kintil555.solarsystem.system.StellarState;

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
		static final int TRAIL_LEN = 80;
		int heat;
		final double[] trail = new double[TRAIL_LEN * 3];
		int trailCount;
		int sampleTick;
		final int color;
		final float size;
		final float spin;
		final float ax;
		final float ay;
		final float az;

		Asteroid(int color, float size, float spin, float ax, float ay, float az) {
			this.color = color;
			this.size = size;
			this.spin = spin;
			this.ax = ax;
			this.ay = ay;
			this.az = az;
		}

		/** Remembers the Sun-relative position every tick (newest first). */
		void recordTrail() {
			System.arraycopy(trail, 0, trail, 3, (TRAIL_LEN - 1) * 3);
			trail[0] = p[0];
			trail[1] = p[1];
			trail[2] = p[2];
			trailCount = Math.min(TRAIL_LEN, trailCount + 1);
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

	/** A short-lived explosion flash: two expanding translucent shells at a world position. */
	static final class Blast {
		final double x;
		final double y;
		final double z;
		final float r0;
		final float r1;
		final int color;
		final int maxTicks;
		int ticksLeft;

		Blast(double x, double y, double z, float r0, float r1, int color, int ticks) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.r0 = r0;
			this.r1 = r1;
			this.color = color;
			this.maxTicks = ticks;
			this.ticksLeft = ticks;
		}
	}

	static Mode mode = Mode.EXPLORE;
	static SolarStyle style = SolarStyle.ENLARGED;
	static Tool tool = Tool.NONE;
	static Tab tab = Tab.CAMERA;
	static boolean expanded;
	/** Left button held with the laser tool: fires every tick. */
	static boolean laserHeld;

	static final Set<Integer> userRings = new HashSet<>();
	static final List<Moon> moons = new ArrayList<>();
	static final List<Asteroid> asteroids = new ArrayList<>();
	static final List<Beam> beams = new ArrayList<>();
	static final List<Blast> blasts = new ArrayList<>();

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

	/**
	 * Displayed radius of the (possibly grown) Sun, 0 once it is gone. The growth is run through the radial map so
	 * the surface reaches a planet's displayed position at the moment the planet is swallowed.
	 */
	static float sunVisualRadius(RadialMap map, StellarState st) {
		if (st.sunGone()) {
			return 0.0f;
		}
		double base = SolarSystem.SUN_RADIUS;
		double grow = map.map(st.sunRadius() * 1.1) - map.map(base * 1.1);
		return (float) (base + Math.max(0.0, grow));
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
		laserHeld = false;
		FreeLaunch.clear();
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
		blasts.clear();
		cachedMap = null;
	}
}
