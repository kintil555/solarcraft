package com.kintil555.solarsystem.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.world.phys.Vec3;

/** Deterministic solar system generated from the world seed (shared by client and server). */
public final class SolarSystem {
	public static final double SUN_Y = 120.0;
	public static final float SUN_RADIUS = 30.0f;
	public static final double EARTH_PERIOD_TICKS = 12000.0;
	public static final Vec3 SUN_CENTER = new Vec3(0.0, SUN_Y, 0.0);

	public enum Kind { ROCKY, GAS, ICE, EARTH }

	/**
	 * @param style palette index chosen by the generator (interpreted by the client painter)
	 */
	public record Planet(int index, Kind kind, float radius, double orbit, double period, double phase,
						 double inclination, double node, double spinPeriod, float tilt, int style, long seed) {

		public double angleAt(double ticks) {
			return phase + 2.0 * Math.PI * ticks / period;
		}

		public Vec3 position(double ticks) {
			return positionAtAngle(angleAt(ticks));
		}

		/** Point on the (inclined) orbit circle for orbital angle {@code a}. */
		public Vec3 positionAtAngle(double a) {
			double x = orbit * Math.cos(a);
			double z = orbit * Math.sin(a);
			// tilt around X axis by inclination
			double y2 = -z * Math.sin(inclination);
			double z2 = z * Math.cos(inclination);
			// rotate around Y axis by the longitude of the ascending node
			double x3 = x * Math.cos(node) + z2 * Math.sin(node);
			double z3 = -x * Math.sin(node) + z2 * Math.cos(node);
			return new Vec3(SUN_CENTER.x + x3, SUN_CENTER.y + y2, SUN_CENTER.z + z3);
		}

		public double spinAngleAt(double ticks) {
			return 2.0 * Math.PI * ticks / spinPeriod;
		}
	}

	private final List<Planet> planets;
	private final int earthIndex;
	private final long seed;

	private SolarSystem(long seed, List<Planet> planets, int earthIndex) {
		this.seed = seed;
		this.planets = List.copyOf(planets);
		this.earthIndex = earthIndex;
	}

	/** Sun's gravitational parameter: makes Earth's circular orbit last exactly {@link #EARTH_PERIOD_TICKS}. */
	public double gmSun() {
		double w = 2.0 * Math.PI / EARTH_PERIOD_TICKS;
		double a = earth().orbit();
		return w * w * a * a * a;
	}

	public long seed() {
		return seed;
	}

	public List<Planet> planets() {
		return planets;
	}

	public Planet earth() {
		return planets.get(earthIndex);
	}

	public static SolarSystem generate(long worldSeed) {
		Random r = new Random(worldSeed * 0x9E3779B97F4A7C15L + 0x5011A2L);
		int count = 6 + r.nextInt(4); // 6..9 planets
		int earth = 1 + r.nextInt(2); // Earth is the 2nd or 3rd planet

		double[] orbits = new double[count];
		double orbit = 95.0 + r.nextDouble() * 20.0;
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				orbit += 38.0 + r.nextDouble() * 32.0;
			}
			orbits[i] = orbit;
		}

		List<Planet> list = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Kind kind = i == earth ? Kind.EARTH : i <= earth + 1 ? Kind.ROCKY : i >= count - 2 ? Kind.ICE : Kind.GAS;
			float radius = switch (kind) {
				case EARTH -> 7.0f;
				case ROCKY -> 3.5f + r.nextFloat() * 4.5f;
				case GAS -> 11.0f + r.nextFloat() * 7.0f;
				case ICE -> 8.0f + r.nextFloat() * 3.5f;
			};
			double period = EARTH_PERIOD_TICKS * Math.pow(orbits[i] / orbits[earth], 1.5);
			double phase = r.nextDouble() * Math.PI * 2.0;
			double inclination = kind == Kind.EARTH ? 0.0 : (r.nextDouble() - 0.5) * 0.24;
			double node = r.nextDouble() * Math.PI * 2.0;
			double spin = kind == Kind.EARTH ? 2400.0 : (700.0 + r.nextDouble() * 2800.0) * (r.nextInt(8) == 0 ? -1.0 : 1.0);
			float tilt = kind == Kind.EARTH ? (float) Math.toRadians(23.44) : r.nextFloat() * 0.6f;
			int style = r.nextInt(4);
			list.add(new Planet(i, kind, radius, orbits[i], period, phase, inclination, node, spin, tilt, style, r.nextLong()));
		}
		return new SolarSystem(worldSeed, list, earth);
	}

	/** Where a new player starts: just outside Earth, on the side facing away from the Sun. */
	public Vec3 startPosition(double ticks) {
		Planet e = earth();
		Vec3 ep = e.position(ticks);
		Vec3 outward = ep.subtract(SUN_CENTER).normalize();
		return ep.add(outward.scale(e.radius() * 4.5 + 14.0)).add(0.0, e.radius() * 0.8, 0.0);
	}

	/** @return {yaw, pitch} in Minecraft degrees for looking from {@code from} to {@code to}. */
	public static float[] lookAt(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, horizontal));
		return new float[] {yaw, pitch};
	}
}
