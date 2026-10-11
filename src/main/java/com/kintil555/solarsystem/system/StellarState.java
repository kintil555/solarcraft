package com.kintil555.solarsystem.system;

/**
 * Laser-driven fate of the Sun and the planets (shared by server and client).
 *
 * <ul>
 *   <li>Sun: heat 0..1 makes it grow and turn blue; at 1 it goes supernova, disappears for good and a shockwave
 *       sweeps outwards, destroying every planet it reaches.</li>
 *   <li>Planet: heat 0..1 turns it red, then yellow; at 1 it explodes. Heat cools down when the laser stops.</li>
 *   <li>A destroyed planet (exploded, swallowed by the Sun, hit by the shockwave) never comes back.</li>
 * </ul>
 */
public final class StellarState {
	/** The Sun's radius grows by this factor on top of its normal size while heating up (1 + GROWTH at the end). */
	public static final double SUN_GROWTH = 2.4;
	public static final float SUN_HIT = 0.004f;
	public static final float PLANET_HIT = 0.0125f;
	public static final float PLANET_COOL = 0.0006f;
	public static final float SHOCK_SPEED = 4.0f;
	public static final float SHOCK_MAX = 2500.0f;

	private final int n;
	private final float[] heat;
	private final boolean[] alive;
	private float sunHeat;
	private boolean sunGone;
	private float shock;
	private boolean changed = true;

	public StellarState(int planets) {
		this.n = planets;
		this.heat = new float[planets];
		this.alive = new boolean[planets];
		java.util.Arrays.fill(alive, true);
	}

	public int count() {
		return n;
	}

	public float sunHeat() {
		return sunHeat;
	}

	public boolean sunGone() {
		return sunGone;
	}

	/** Radius of the supernova shockwave (real blocks from the Sun), 0 before the supernova. */
	public float shock() {
		return shock;
	}

	public boolean shockActive() {
		return sunGone && shock < SHOCK_MAX;
	}

	public float heat(int i) {
		return i >= 0 && i < n ? heat[i] : 0.0f;
	}

	public boolean alive(int i) {
		return i >= 0 && i < n && alive[i];
	}

	/** 1 at normal size up to {@code 1 + SUN_GROWTH}; 0 once the Sun is gone. */
	public double sunScale() {
		return sunGone ? 0.0 : 1.0 + SUN_GROWTH * sunHeat;
	}

	/** Real radius of the Sun right now. */
	public double sunRadius() {
		return SolarSystem.SUN_RADIUS * sunScale();
	}

	/** One laser pulse on the Sun. */
	public void hitSun() {
		if (sunGone) {
			return;
		}
		sunHeat = Math.min(1.0f, sunHeat + SUN_HIT);
		changed = true;
		if (sunHeat >= 1.0f) {
			shock = (float) (SolarSystem.SUN_RADIUS * (1.0 + SUN_GROWTH));
			sunGone = true;
		}
	}

	/** One laser pulse on planet {@code i}. @return true if it exploded. */
	public boolean hitPlanet(int i) {
		if (!alive(i)) {
			return false;
		}
		heat[i] = Math.min(1.0f, heat[i] + PLANET_HIT);
		changed = true;
		if (heat[i] >= 1.0f) {
			destroy(i);
			return true;
		}
		return false;
	}

	public void destroy(int i) {
		if (alive(i)) {
			alive[i] = false;
			heat[i] = 0.0f;
			changed = true;
		}
	}

	/** Advances cooling and the shockwave by one game tick. */
	public void tick() {
		for (int i = 0; i < n; i++) {
			if (alive[i] && heat[i] > 0.0f) {
				heat[i] = Math.max(0.0f, heat[i] - PLANET_COOL);
				changed = true;
			}
		}
		if (shockActive()) {
			shock = Math.min(SHOCK_MAX, shock + SHOCK_SPEED);
			changed = true;
		}
	}

	/** True once after anything changed (used to decide when to sync clients). */
	public boolean consumeChanged() {
		boolean c = changed;
		changed = false;
		return c;
	}

	public void copyFrom(StellarState o) {
		if (o.n != n) {
			return;
		}
		System.arraycopy(o.heat, 0, heat, 0, n);
		System.arraycopy(o.alive, 0, alive, 0, n);
		sunHeat = o.sunHeat;
		sunGone = o.sunGone;
		shock = o.shock;
	}

	/** Overwrites this state with the one received from the server. */
	public void load(float sunHeat, float shock, boolean sunGone, float[] heat, boolean[] alive) {
		if (heat.length != n || alive.length != n) {
			return;
		}
		this.sunHeat = sunHeat;
		this.shock = shock;
		this.sunGone = sunGone;
		System.arraycopy(heat, 0, this.heat, 0, n);
		System.arraycopy(alive, 0, this.alive, 0, n);
	}

	public float[] heatCopy() {
		return heat.clone();
	}

	public boolean[] aliveCopy() {
		return alive.clone();
	}

	// ------------------------------------------------------------------ persistence

	public String serialize(long seed) {
		StringBuilder sb = new StringBuilder();
		sb.append(seed).append(' ').append(n).append(' ').append(sunHeat).append(' ').append(shock).append(' ')
				.append(sunGone ? 1 : 0).append('\n');
		for (int i = 0; i < n; i++) {
			sb.append(heat[i]).append(' ').append(alive[i] ? 1 : 0).append('\n');
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
			float sh = Float.parseFloat(head[2]);
			float sk = Float.parseFloat(head[3]);
			boolean gone = Integer.parseInt(head[4]) != 0;
			float[] h = new float[n];
			boolean[] a = new boolean[n];
			for (int i = 0; i < n; i++) {
				String[] f = lines[i + 1].trim().split(" ");
				h[i] = Float.parseFloat(f[0]);
				a[i] = Integer.parseInt(f[1]) != 0;
				if (!Float.isFinite(h[i])) {
					return false;
				}
			}
			if (!Float.isFinite(sh) || !Float.isFinite(sk)) {
				return false;
			}
			load(Math.max(0.0f, Math.min(1.0f, sh)), Math.max(0.0f, Math.min(SHOCK_MAX, sk)), gone, h, a);
			changed = true;
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
