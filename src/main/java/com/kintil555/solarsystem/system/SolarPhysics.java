package com.kintil555.solarsystem.system;

import java.util.List;

import com.kintil555.solarsystem.system.SolarSystem.Planet;

/**
 * N-body simulation: a fixed Sun plus all planets attracting each other (3D, leapfrog integrator).
 * Positions are relative to {@link SolarSystem#SUN_CENTER}; units are blocks and ticks.
 */
public final class SolarPhysics {
	/** Max launch speed change (blocks/tick). Earth's orbital speed is ~0.09. */
	public static final double MAX_DV = 0.15;
	public static final int SUBSTEPS = 4;
	private static final double SOFT2 = 4.0;
	private static final double PLANET_MASS_FACTOR = 4.0e-4;
	private static final double ESCAPE_DISTANCE = 4000.0;

	public record Prediction(float[] points, int count, boolean crashed) {}

	private final SolarSystem system;
	private final int n;
	private final double gmSun;
	private final double[] gm;
	private final double[] radius;
	private final double[] p;
	private final double[] v;
	private final double[] acc;
	private final StellarState stellar;

	private SolarPhysics(SolarSystem system) {
		this.system = system;
		List<Planet> planets = system.planets();
		this.n = planets.size();
		this.gmSun = system.gmSun();
		this.gm = new double[n];
		this.radius = new double[n];
		this.p = new double[n * 3];
		this.v = new double[n * 3];
		this.acc = new double[n * 3];
		this.stellar = new StellarState(n);
		for (Planet pl : planets) {
			double k = pl.radius() / 7.0;
			gm[pl.index()] = gmSun * PLANET_MASS_FACTOR * k * k * k;
			radius[pl.index()] = pl.radius();
		}
	}

	/** Every planet on its ideal circular orbit at time {@code ticks}. */
	public static SolarPhysics initial(SolarSystem system, double ticks) {
		SolarPhysics s = new SolarPhysics(system);
		for (int i = 0; i < s.n; i++) {
			s.circular(i, ticks);
		}
		return s;
	}

	public SolarPhysics copy() {
		SolarPhysics c = new SolarPhysics(system);
		System.arraycopy(p, 0, c.p, 0, p.length);
		System.arraycopy(v, 0, c.v, 0, v.length);
		c.stellar.copyFrom(stellar);
		return c;
	}

	public int count() {
		return n;
	}

	/** Sun's gravity right now: zero once the Sun has gone supernova. */
	public double gmSun() {
		return stellar.sunGone() ? 0.0 : gmSun;
	}

	public StellarState stellar() {
		return stellar;
	}

	/** One laser pulse on the Sun. */
	public void laserSun() {
		stellar.hitSun();
	}

	/** One laser pulse on planet {@code i}; an exploded planet is removed for good. */
	public void laserPlanet(int i) {
		if (stellar.hitPlanet(i)) {
			freeze(i);
		}
	}

	private void freeze(int i) {
		v[i * 3] = 0.0;
		v[i * 3 + 1] = 0.0;
		v[i * 3 + 2] = 0.0;
	}

	public double px(int i) { return p[i * 3]; }
	public double py(int i) { return p[i * 3 + 1]; }
	public double pz(int i) { return p[i * 3 + 2]; }
	public double vx(int i) { return v[i * 3]; }
	public double vy(int i) { return v[i * 3 + 1]; }
	public double vz(int i) { return v[i * 3 + 2]; }

	public void circular(int i, double ticks) {
		Planet pl = system.planets().get(i);
		double a = pl.angleAt(ticks);
		double w = 2.0 * Math.PI / pl.period();
		double d = 1e-4;
		var p0 = pl.positionAtAngle(a);
		var p1 = pl.positionAtAngle(a + d);
		p[i * 3] = p0.x - SolarSystem.SUN_CENTER.x;
		p[i * 3 + 1] = p0.y - SolarSystem.SUN_CENTER.y;
		p[i * 3 + 2] = p0.z - SolarSystem.SUN_CENTER.z;
		double tx = (p1.x - p0.x) / d, ty = (p1.y - p0.y) / d, tz = (p1.z - p0.z) / d; // d(pos)/d(angle)
		v[i * 3] = tx * w;
		v[i * 3 + 1] = ty * w;
		v[i * 3 + 2] = tz * w;
	}

	/** Instantly change the velocity of planet {@code i}. */
	public void impulse(int i, double dx, double dy, double dz) {
		if (!stellar.alive(i)) {
			return;
		}
		v[i * 3] += dx;
		v[i * 3 + 1] += dy;
		v[i * 3 + 2] += dz;
	}

	public void advance(double dt) {
		for (int k = 0; k < p.length; k++) {
			p[k] += v[k] * dt;
		}
	}

	/** Loads {@code [px,py,pz,vx,vy,vz] * count} (floats, as sent over the network). */
	public void load(float[] data) {
		if (data.length != n * 6) {
			return;
		}
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 3; c++) {
				p[i * 3 + c] = data[i * 6 + c];
				v[i * 3 + c] = data[i * 6 + 3 + c];
			}
		}
	}

	public float[] dump() {
		float[] out = new float[n * 6];
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 3; c++) {
				out[i * 6 + c] = (float) p[i * 3 + c];
				out[i * 6 + 3 + c] = (float) v[i * 3 + c];
			}
		}
		return out;
	}

	/**
	 * Advance one game tick. A planet that hits (or is swallowed by) the Sun is destroyed for good, one reached by
	 * the supernova shockwave too; a planet that merely escapes is put back on its circular orbit.
	 */
	public void tick(double ticks) {
		stellar.tick();
		double dt = 1.0 / SUBSTEPS;
		accel();
		for (int s = 0; s < SUBSTEPS; s++) {
			substep(dt);
		}
		for (int i = 0; i < n; i++) {
			if (!stellar.alive(i)) {
				freeze(i);
				continue;
			}
			if (stellar.sunGone()) {
				if (distance(i) - radius[i] <= stellar.shock()) {
					stellar.destroy(i);
					freeze(i);
				}
			} else if (crashed(i)) {
				stellar.destroy(i);
				freeze(i);
			} else if (distance(i) > ESCAPE_DISTANCE) {
				circular(i, ticks);
			}
		}
	}

	public boolean crashed(int i) {
		double sunRadius = stellar.sunRadius();
		return sunRadius > 0.0 && distance(i) < sunRadius * 1.1 + radius[i];
	}

	private double distance(int i) {
		double x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
		return Math.sqrt(x * x + y * y + z * z);
	}

	private void substep(double dt) {
		for (int i = 0; i < n; i++) {
			if (!stellar.alive(i)) {
				continue;
			}
			for (int k = i * 3; k < i * 3 + 3; k++) {
				v[k] += acc[k] * dt * 0.5;
				p[k] += v[k] * dt;
			}
		}
		accel();
		for (int i = 0; i < n; i++) {
			if (!stellar.alive(i)) {
				continue;
			}
			for (int k = i * 3; k < i * 3 + 3; k++) {
				v[k] += acc[k] * dt * 0.5;
			}
		}
	}

	private void accel() {
		java.util.Arrays.fill(acc, 0.0);
		double sun = gmSun();
		for (int i = 0; i < n; i++) {
			if (!stellar.alive(i)) {
				continue;
			}
			double xi = p[i * 3], yi = p[i * 3 + 1], zi = p[i * 3 + 2];
			double r2 = xi * xi + yi * yi + zi * zi + SOFT2;
			double f = -sun / (r2 * Math.sqrt(r2));
			acc[i * 3] += xi * f;
			acc[i * 3 + 1] += yi * f;
			acc[i * 3 + 2] += zi * f;
			for (int j = i + 1; j < n; j++) {
				if (!stellar.alive(j)) {
					continue;
				}
				double dx = p[j * 3] - xi, dy = p[j * 3 + 1] - yi, dz = p[j * 3 + 2] - zi;
				double d2 = dx * dx + dy * dy + dz * dz + SOFT2;
				double inv = 1.0 / (d2 * Math.sqrt(d2));
				double fi = gm[j] * inv;
				double fj = gm[i] * inv;
				acc[i * 3] += dx * fi;
				acc[i * 3 + 1] += dy * fi;
				acc[i * 3 + 2] += dz * fi;
				acc[j * 3] -= dx * fj;
				acc[j * 3 + 1] -= dy * fj;
				acc[j * 3 + 2] -= dz * fj;
			}
		}
	}

	/**
	 * Predicts the path of {@code planet} after launching it with velocity change (dx,dy,dz): returns world
	 * coordinates (Sun offset included) sampled every {@code every} ticks for up to {@code horizon} ticks.
	 */
	public Prediction predict(int planet, double dx, double dy, double dz, int horizon, int every, int substeps) {
		SolarPhysics s = copy();
		s.impulse(planet, dx, dy, dz);
		int max = horizon / every + 1;
		float[] pts = new float[max * 3];
		int count = 0;
		boolean crashed = false;
		double dt = 1.0 / substeps;
		s.accel();
		for (int t = 0; t <= horizon && count < max; t++) {
			if (t % every == 0) {
				pts[count * 3] = (float) (s.p[planet * 3] + SolarSystem.SUN_CENTER.x);
				pts[count * 3 + 1] = (float) (s.p[planet * 3 + 1] + SolarSystem.SUN_CENTER.y);
				pts[count * 3 + 2] = (float) (s.p[planet * 3 + 2] + SolarSystem.SUN_CENTER.z);
				count++;
			}
			if (s.crashed(planet)) {
				crashed = true;
				break;
			}
			for (int k = 0; k < substeps; k++) {
				s.substep(dt);
			}
		}
		return new Prediction(pts, count, crashed);
	}

	// ------------------------------------------------------------------ persistence

	public String serialize(long seed) {
		StringBuilder sb = new StringBuilder();
		sb.append(seed).append(' ').append(n).append('\n');
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 3; c++) {
				sb.append(p[i * 3 + c]).append(' ');
			}
			for (int c = 0; c < 3; c++) {
				sb.append(v[i * 3 + c]).append(c < 2 ? " " : "\n");
			}
		}
		return sb.toString();
	}

	/** @return true if the text matched this system (same seed and planet count) and was loaded. */
	public boolean deserialize(String text, long seed) {
		try {
			String[] lines = text.strip().split("\n");
			String[] head = lines[0].trim().split(" ");
			if (Long.parseLong(head[0]) != seed || Integer.parseInt(head[1]) != n || lines.length < n + 1) {
				return false;
			}
			double[] np = new double[n * 3];
			double[] nv = new double[n * 3];
			for (int i = 0; i < n; i++) {
				String[] f = lines[i + 1].trim().split(" ");
				for (int c = 0; c < 3; c++) {
					np[i * 3 + c] = Double.parseDouble(f[c]);
					nv[i * 3 + c] = Double.parseDouble(f[3 + c]);
					if (!Double.isFinite(np[i * 3 + c]) || !Double.isFinite(nv[i * 3 + c])) {
						return false;
					}
				}
			}
			System.arraycopy(np, 0, p, 0, np.length);
			System.arraycopy(nv, 0, v, 0, nv.length);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
