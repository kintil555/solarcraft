package com.kintil555.solarsystem.client;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.LaunchPayload;
import com.kintil555.solarsystem.system.SolarPhysics;
import com.kintil555.solarsystem.system.SolarSystem;
import com.kintil555.solarsystem.system.SolarSystem.Planet;

/** Picking and the four toolbar tools: laser, planet ring, asteroid and moon. */
final class SolarTools {
	record Ray(Vec3 origin, Vec3 dir, Vec3 up) {}

	/** {@code planet} is -1 when nothing was hit. */
	record Hit(int planet, double t) {}

	private static final Random RNG = new Random();
	private static final float LASER_POWER = 0.3f;
	private static final int LASER_COOLDOWN_TICKS = 5;
	private static final int BEAM_TICKS = 8;
	private static final double BEAM_RANGE = 700.0;
	private static final int MAX_ASTEROIDS = 40;
	private static final int MAX_MOONS_PER_PLANET = 4;
	private static final double ASTEROID_SOFT2 = 4.0;
	private static final double ASTEROID_ESCAPE = 4000.0;
	private static final int SUBSTEPS = 4;

	private static long lastLaser = Long.MIN_VALUE;

	private SolarTools() {}

	// ------------------------------------------------------------------ picking

	/** Where planet {@code i} is drawn right now, or null before the system exists. */
	static Vec3 planetVisual(int i) {
		SolarPhysics phys = SolarSystemClient.physics();
		SolarSystem sys = SolarSystemClient.system();
		if (phys == null || sys == null || i < 0 || i >= phys.count()) {
			return null;
		}
		double age = SolarSystemClient.syncAge(0.0f);
		double[] out = new double[3];
		SolarView.toVisual(SolarView.map(sys), phys.px(i) + phys.vx(i) * age, phys.py(i) + phys.vy(i) * age,
				phys.pz(i) + phys.vz(i) * age, out);
		return new Vec3(out[0], out[1], out[2]);
	}

	/** Ray through the mouse cursor (GUI-scaled coordinates) from the camera that is currently in use. */
	static Ray cursorRay(Minecraft mc, double mouseX, double mouseY) {
		Vec3 origin;
		float yaw;
		float pitch;
		if (FreeCamera.active()) {
			origin = FreeCamera.position();
			yaw = FreeCamera.yaw();
			pitch = FreeCamera.pitch();
		} else {
			LocalPlayer player = mc.player;
			origin = player.getEyePosition();
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		Vec3 forward = FreeCamera.forward(yaw, pitch);
		Vec3 right = FreeCamera.right(yaw, pitch);
		Vec3 up = FreeCamera.up(yaw, pitch);
		double ndcX = mouseX / mc.getWindow().getGuiScaledWidth() * 2.0 - 1.0;
		double ndcY = 1.0 - mouseY / mc.getWindow().getGuiScaledHeight() * 2.0;
		double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) * 0.5);
		double aspect = (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
		Vec3 dir = forward.add(right.scale(ndcX * tanHalf * aspect)).add(up.scale(ndcY * tanHalf)).normalize();
		return new Ray(origin, dir, up);
	}

	/** Nearest planet near the ray; the aim cone widens with distance so far planets stay easy to hit. */
	static Hit pick(Vec3 o, Vec3 d) {
		SolarSystem sys = SolarSystemClient.system();
		if (sys == null) {
			return new Hit(-1, 0.0);
		}
		int best = -1;
		double bestT = Double.MAX_VALUE;
		for (Planet p : sys.planets()) {
			Vec3 c = planetVisual(p.index());
			if (c == null) {
				continue;
			}
			double rx = c.x - o.x, ry = c.y - o.y, rz = c.z - o.z;
			double t = rx * d.x + ry * d.y + rz * d.z;
			if (t <= 0.0) {
				continue;
			}
			double perp2 = rx * rx + ry * ry + rz * rz - t * t;
			double limit = SolarView.radiusOf(p) * 1.7 + t * 0.05;
			if (perp2 < limit * limit && t < bestT) {
				best = p.index();
				bestT = t;
			}
		}
		return new Hit(best, bestT);
	}

	/** Distance along the ray to the sphere surface, or -1 on a miss. */
	private static double sphereHit(Vec3 o, Vec3 d, Vec3 c, double radius) {
		double rx = c.x - o.x, ry = c.y - o.y, rz = c.z - o.z;
		double t = rx * d.x + ry * d.y + rz * d.z;
		double perp2 = rx * rx + ry * ry + rz * rz - t * t;
		if (t <= 0.0 || perp2 > radius * radius) {
			return -1.0;
		}
		return t - Math.sqrt(radius * radius - perp2);
	}

	// ------------------------------------------------------------------ tools

	static void use(Minecraft mc, double mouseX, double mouseY) {
		if (mc.player == null || mc.level == null || SolarSystemClient.system() == null) {
			return;
		}
		Ray ray = cursorRay(mc, mouseX, mouseY);
		switch (SolarView.tool) {
			case LASER -> laser(mc, ray);
			case RING -> ring(mc, ray);
			case MOON -> moon(mc, ray);
			case ASTEROID -> asteroid(mc, ray);
			case NONE -> {
			}
		}
	}

	private static void laser(Minecraft mc, Ray ray) {
		long now = mc.level.getGameTime();
		if (now - lastLaser < LASER_COOLDOWN_TICKS) {
			return;
		}
		lastLaser = now;
		SolarSystem sys = SolarSystemClient.system();
		Hit hit = pick(ray.origin(), ray.dir());
		double end = BEAM_RANGE;
		boolean planetHit = false;
		if (hit.planet() >= 0) {
			double t = sphereHit(ray.origin(), ray.dir(), planetVisual(hit.planet()), SolarView.radiusOf(sys.planets().get(hit.planet())));
			end = t > 0.0 ? t : hit.t();
			planetHit = true;
		}
		double sunT = sphereHit(ray.origin(), ray.dir(), SolarSystem.SUN_CENTER, SolarSystem.SUN_RADIUS);
		if (sunT > 0.0 && sunT < end) {
			end = sunT;
			planetHit = false;
		}
		if (planetHit) {
			ClientPlayNetworking.send(new LaunchPayload(hit.planet(), (float) ray.dir().x, (float) ray.dir().y, (float) ray.dir().z, LASER_POWER));
		}
		Vec3 from = ray.origin().add(ray.dir().scale(3.0)).subtract(ray.up().scale(2.2));
		Vec3 to = ray.origin().add(ray.dir().scale(end));
		SolarView.beams.add(new SolarView.Beam(new float[] {(float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z}, BEAM_TICKS));
	}

	private static void ring(Minecraft mc, Ray ray) {
		Hit hit = pick(ray.origin(), ray.dir());
		if (hit.planet() < 0) {
			mc.player.sendOverlayMessage(Component.literal("Arahkan ke planet untuk memberi / melepas cincin"));
			return;
		}
		if (!SolarView.userRings.remove(hit.planet())) {
			SolarView.userRings.add(hit.planet());
		}
	}

	private static void moon(Minecraft mc, Ray ray) {
		Hit hit = pick(ray.origin(), ray.dir());
		if (hit.planet() < 0) {
			mc.player.sendOverlayMessage(Component.literal("Arahkan ke planet untuk menambah bulan"));
			return;
		}
		int count = SolarView.moonCount(hit.planet());
		if (count >= MAX_MOONS_PER_PLANET) {
			mc.player.sendOverlayMessage(Component.literal("Planet ini sudah punya " + MAX_MOONS_PER_PLANET + " bulan"));
			return;
		}
		Planet p = SolarSystemClient.system().planets().get(hit.planet());
		float radius = SolarView.radiusOf(p);
		float orbit = radius * (2.3f + 0.9f * count);
		float size = Math.max(0.9f, radius * (0.2f + 0.04f * RNG.nextFloat()));
		SolarView.moons.add(new SolarView.Moon(hit.planet(), orbit, 520.0 + 260.0 * count + RNG.nextInt(120),
				RNG.nextDouble() * Math.PI * 2.0, size, (RNG.nextFloat() - 0.5f) * 0.5f));
	}

	private static void asteroid(Minecraft mc, Ray ray) {
		SolarSystem sys = SolarSystemClient.system();
		SolarPhysics phys = SolarSystemClient.physics();
		Vec3 o = ray.origin();
		Vec3 d = ray.dir();
		// prefer the orbital plane under the cursor; otherwise a point a short way along the ray
		Vec3 spot = o.add(d.scale(FreeCamera.active() ? 120.0 : 40.0));
		if (Math.abs(d.y) > 1e-3) {
			double t = (SolarSystem.SUN_Y - o.y) / d.y;
			if (t > 0.0 && t < 3000.0) {
				spot = o.add(d.scale(t));
			}
		}
		RadialMap map = SolarView.map(sys);
		double vx = spot.x - SolarSystem.SUN_CENTER.x, vy = spot.y - SolarSystem.SUN_CENTER.y, vz = spot.z - SolarSystem.SUN_CENTER.z;
		double shown = Math.sqrt(vx * vx + vy * vy + vz * vz);
		double real = map.unmap(shown);
		if (shown < 1e-6 || real < SolarSystem.SUN_RADIUS * 1.6) {
			mc.player.sendOverlayMessage(Component.literal("Terlalu dekat dengan Matahari"));
			return;
		}
		double k = real / shown;
		double rx = vx * k, ry = vy * k, rz = vz * k;
		// tangent in the same sense as the planets orbit (r x up)
		double tx = -rz, tz = rx;
		double tl = Math.sqrt(tx * tx + tz * tz);
		if (tl < 1e-6) {
			tx = 1.0;
			tz = 0.0;
			tl = 1.0;
		}
		double speed = Math.sqrt(phys.gmSun() / real) * (0.85 + 0.3 * RNG.nextDouble());
		float ax = RNG.nextFloat() - 0.5f, ay = RNG.nextFloat() - 0.5f, az = RNG.nextFloat() - 0.5f;
		float al = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
		SolarView.Asteroid a = new SolarView.Asteroid(1.1f + RNG.nextFloat() * 1.3f, 0.02f + RNG.nextFloat() * 0.05f, ax / al, ay / al, az / al);
		a.p[0] = rx;
		a.p[1] = ry;
		a.p[2] = rz;
		a.v[0] = tx / tl * speed + (RNG.nextDouble() - 0.5) * 0.004;
		a.v[1] = (RNG.nextDouble() - 0.5) * 0.004;
		a.v[2] = tz / tl * speed + (RNG.nextDouble() - 0.5) * 0.004;
		if (SolarView.asteroids.size() >= MAX_ASTEROIDS) {
			SolarView.asteroids.remove(0);
		}
		SolarView.asteroids.add(a);
	}

	// ------------------------------------------------------------------ simulation

	/** Advances beams and asteroids by one game tick. */
	static void tick(Minecraft mc, SolarPhysics phys, SolarSystem sys) {
		SolarView.beams.removeIf(b -> --b.ticksLeft <= 0);

		List<SolarView.Asteroid> list = SolarView.asteroids;
		if (list.isEmpty()) {
			return;
		}
		double age = SolarSystemClient.syncAge(0.0f);
		double dt = 1.0 / SUBSTEPS;
		double gm = phys.gmSun();
		Iterator<SolarView.Asteroid> it = list.iterator();
		while (it.hasNext()) {
			SolarView.Asteroid a = it.next();
			for (int s = 0; s < SUBSTEPS; s++) {
				step(a, gm, dt);
			}
			double r = Math.sqrt(a.p[0] * a.p[0] + a.p[1] * a.p[1] + a.p[2] * a.p[2]);
			if (r < SolarSystem.SUN_RADIUS * 1.05 || r > ASTEROID_ESCAPE || !Double.isFinite(r)) {
				it.remove();
				continue;
			}
			for (Planet p : sys.planets()) {
				int i = p.index();
				double dx = a.p[0] - (phys.px(i) + phys.vx(i) * age);
				double dy = a.p[1] - (phys.py(i) + phys.vy(i) * age);
				double dz = a.p[2] - (phys.pz(i) + phys.vz(i) * age);
				if (dx * dx + dy * dy + dz * dz < p.radius() * p.radius()) {
					it.remove();
					mc.player.sendOverlayMessage(Component.literal("Asteroid menabrak planet " + (i + 1)));
					break;
				}
			}
		}
	}

	private static void step(SolarView.Asteroid a, double gm, double dt) {
		accelerate(a, gm, dt * 0.5);
		for (int c = 0; c < 3; c++) {
			a.p[c] += a.v[c] * dt;
		}
		accelerate(a, gm, dt * 0.5);
	}

	private static void accelerate(SolarView.Asteroid a, double gm, double dt) {
		double r2 = a.p[0] * a.p[0] + a.p[1] * a.p[1] + a.p[2] * a.p[2] + ASTEROID_SOFT2;
		double f = -gm / (r2 * Math.sqrt(r2));
		for (int c = 0; c < 3; c++) {
			a.v[c] += a.p[c] * f * dt;
		}
	}
}
