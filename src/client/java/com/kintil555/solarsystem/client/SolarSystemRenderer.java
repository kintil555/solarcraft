package com.kintil555.solarsystem.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.kintil555.solarsystem.SolarSystemMod;
import com.kintil555.solarsystem.system.SolarPhysics;
import com.kintil555.solarsystem.system.SolarSystem;
import com.kintil555.solarsystem.system.SolarSystem.Kind;
import com.kintil555.solarsystem.system.SolarSystem.Planet;

/**
 * Draws the solar system as flat-coloured quads with the unfogged debug-quad render type, so bodies stay visible
 * at any distance (no render-distance fog) and are depth-tested against terrain. Every body is a cube.
 */
public final class SolarSystemRenderer {
	private static final String EARTH_ATLAS = "/assets/solarsystem/textures/planet/earth_faces.png";

	private static final int ORBIT_SEGMENTS = 180;
	private static final int STAR_COUNT = 1400;
	private static final float STAR_RADIUS = 480.0f;
	private static final int ORBIT_COLOR = 0x60C8C8DC;
	private static final int EARTH_ORBIT_COLOR = 0xA064A0FF;
	private static final int SELECTED_ORBIT_COLOR = 0xB0FFE070;
	private static final double MAX_ORBIT_DISTANCE = 1500.0;

	private static final int MODE_SHADED = 0;
	private static final int MODE_LIMB = 1;
	private static final int MODE_FLAT = 2;

	private final SolarSystem system;
	private final BodyMesh sunMesh = BodyMesh.cube(20);
	private final int[] sunColors = new int[sunMesh.cells];
	private final BodyMesh haloMesh = BodyMesh.cube(6);
	private final int[] halo1 = new int[haloMesh.cells];
	private final int[] halo2 = new int[haloMesh.cells];
	private final BodyMesh[] planetMeshes;
	private final float[] starData = new float[STAR_COUNT * 9];
	private final int[] starColors = new int[STAR_COUNT];
	private final float[] orbitBuffer = new float[(ORBIT_SEGMENTS + 1) * 3];

	private final Vector3f tmp = new Vector3f();
	private final Quaternionf rot = new Quaternionf();
	private final Quaternionf identity = new Quaternionf();

	private SolarSystemRenderer(SolarSystem system) {
		this.system = system;
		java.util.Arrays.fill(halo1, 0x26FFAA28);
		java.util.Arrays.fill(halo2, 0x14FF7814);

		planetMeshes = new BodyMesh[system.planets().size()];
		for (Planet p : system.planets()) {
			BodyMesh mesh;
			if (p.kind() == Kind.EARTH) {
				mesh = buildEarth();
			} else {
				mesh = BodyMesh.cube(8);
				Painter.paintPlanet(p, mesh);
			}
			planetMeshes[p.index()] = mesh;
		}
		buildStars(system.seed());
	}

	static SolarSystemRenderer create(SolarSystem system) {
		return new SolarSystemRenderer(system);
	}

	// ------------------------------------------------------------------ setup

	private static BodyMesh buildEarth() {
		int w = 27;
		int h = 18;
		int[] atlas = new int[w * h];
		java.util.Arrays.fill(atlas, 0xFF1D51B9);
		try (InputStream in = SolarSystemRenderer.class.getResourceAsStream(EARTH_ATLAS)) {
			if (in != null) {
				BufferedImage img = ImageIO.read(in);
				if (img != null && img.getWidth() == w && img.getHeight() == h) {
					atlas = img.getRGB(0, 0, w, h, null, 0, w);
				}
			}
		} catch (Exception ignored) {
			// fall back to an all-ocean planet
		}
		return BodyMesh.earthCube(9, atlas, w);
	}

	private void buildStars(long seed) {
		Random r = new Random(seed ^ 0xA57A25L);
		for (int i = 0; i < STAR_COUNT; i++) {
			double x = r.nextGaussian(), y = r.nextGaussian(), z = r.nextGaussian();
			double len = Math.sqrt(x * x + y * y + z * z);
			if (len < 1e-6) {
				x = 0;
				y = 1;
				z = 0;
				len = 1;
			}
			float dx = (float) (x / len), dy = (float) (y / len), dz = (float) (z / len);
			float hx = Math.abs(dy) < 0.9f ? 0 : 1, hy = Math.abs(dy) < 0.9f ? 1 : 0;
			float ux = -dz * hy, uy = dz * hx, uz = dx * hy - dy * hx;
			float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			ux /= ul;
			uy /= ul;
			uz /= ul;
			float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
			float s = 0.45f + (float) Math.pow(r.nextFloat(), 3) * 0.9f;
			int o = i * 9;
			starData[o] = dx;
			starData[o + 1] = dy;
			starData[o + 2] = dz;
			starData[o + 3] = ux * s;
			starData[o + 4] = uy * s;
			starData[o + 5] = uz * s;
			starData[o + 6] = vx * s;
			starData[o + 7] = vy * s;
			starData[o + 8] = vz * s;

			float b = 0.45f + 0.55f * r.nextFloat() * r.nextFloat();
			int tint = r.nextInt(3);
			float cr = tint == 1 ? 190 : 255, cg = tint == 1 ? 210 : tint == 2 ? 235 : 255, cb = tint == 2 ? 190 : 255;
			starColors[i] = Painter.rgb(cr * b, cg * b, cb * b, 235);
		}
	}

	// ------------------------------------------------------------------ rendering

	static void onCollectSubmits(LevelRenderContext context) {
		SolarSystemRenderer renderer = SolarSystemClient.renderer();
		if (renderer == null || SolarSystemClient.physics() == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !level.dimensionTypeRegistration().is(SolarSystemMod.SPACE_TYPE)) {
			return;
		}
		renderer.submit(context, level.getGameTime());
	}

	private void submit(LevelRenderContext context, long gameTime) {
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double ticks = gameTime + partial;
		SolarPhysics phys = SolarSystemClient.physics();
		double age = SolarSystemClient.syncAge(partial);
		SolarSystemClient.Preview preview = SolarSystemClient.preview();

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
				(pose, buffer) -> draw(buffer, pose, (float) cam.x, (float) cam.y, (float) cam.z, ticks, phys, age, preview));
		poseStack.popPose();
	}

	private void draw(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, double ticks,
					  SolarPhysics phys, double age, SolarSystemClient.Preview preview) {
		int n = system.planets().size();
		double[][] pos = new double[n][3];
		double[] dist = new double[n];
		for (int i = 0; i < n; i++) {
			pos[i][0] = SolarSystem.SUN_CENTER.x + phys.px(i) + phys.vx(i) * age;
			pos[i][1] = SolarSystem.SUN_CENTER.y + phys.py(i) + phys.vy(i) * age;
			pos[i][2] = SolarSystem.SUN_CENTER.z + phys.pz(i) + phys.vz(i) * age;
			dist[i] = Math.sqrt(sq(pos[i][0] - cx) + sq(pos[i][1] - cy) + sq(pos[i][2] - cz));
		}

		drawStars(vc, pose, cx, cy, cz);

		for (int i = 0; i < n; i++) {
			int count = orbit(phys, i, age);
			if (count > 1) {
				int color = preview != null && preview.planet() == i ? SELECTED_ORBIT_COLOR
						: system.planets().get(i).kind() == Kind.EARTH ? EARTH_ORBIT_COLOR : ORBIT_COLOR;
				drawPolyline(vc, pose, orbitBuffer, count, color, cx, cy, cz, 0.0016f, 0.08f, false, false);
			}
		}

		// Bodies far -> near (no depth writes, so draw order matters between bodies).
		List<Planet> order = new ArrayList<>(system.planets());
		order.sort((a, b) -> Double.compare(dist[b.index()], dist[a.index()]));
		float sunX = (float) SolarSystem.SUN_CENTER.x, sunY = (float) SolarSystem.SUN_CENTER.y, sunZ = (float) SolarSystem.SUN_CENTER.z;
		double sunDist = Math.sqrt(sq(sunX - cx) + sq(sunY - cy) + sq(sunZ - cz));
		boolean sunDrawn = false;
		for (Planet p : order) {
			if (!sunDrawn && sunDist >= dist[p.index()]) {
				drawSun(vc, pose, cx, cy, cz, ticks, sunX, sunY, sunZ);
				sunDrawn = true;
			}
			drawPlanet(vc, pose, p, pos[p.index()], ticks, cx, cy, cz, sunX, sunY, sunZ);
		}
		if (!sunDrawn) {
			drawSun(vc, pose, cx, cy, cz, ticks, sunX, sunY, sunZ);
		}

		if (preview != null) {
			drawPreview(vc, pose, preview, cx, cy, cz);
		}
	}

	private static double sq(double v) {
		return v * v;
	}

	/**
	 * Osculating orbit of planet {@code i} around the Sun (the path it would follow if only the Sun pulled on
	 * it), written to {@link #orbitBuffer} in world coordinates. Returns the number of points.
	 */
	private int orbit(SolarPhysics phys, int i, double age) {
		double mu = phys.gmSun();
		double rx = phys.px(i) + phys.vx(i) * age, ry = phys.py(i) + phys.vy(i) * age, rz = phys.pz(i) + phys.vz(i) * age;
		double vx = phys.vx(i), vy = phys.vy(i), vz = phys.vz(i);
		double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
		double v2 = vx * vx + vy * vy + vz * vz;
		double hx = ry * vz - rz * vy, hy = rz * vx - rx * vz, hz = rx * vy - ry * vx;
		double h2 = hx * hx + hy * hy + hz * hz;
		if (r < 1e-6 || h2 < 1e-12) {
			return 0;
		}
		double rv = rx * vx + ry * vy + rz * vz;
		double k = (v2 - mu / r) / mu;
		double ex = k * rx - rv / mu * vx, ey = k * ry - rv / mu * vy, ez = k * rz - rv / mu * vz;
		double e = Math.sqrt(ex * ex + ey * ey + ez * ez);
		double px, py, pz;
		if (e < 1e-6) {
			px = rx / r;
			py = ry / r;
			pz = rz / r;
		} else {
			px = ex / e;
			py = ey / e;
			pz = ez / e;
		}
		double hl = Math.sqrt(h2);
		double wx = hx / hl, wy = hy / hl, wz = hz / hl;
		double qx = wy * pz - wz * py, qy = wz * px - wx * pz, qz = wx * py - wy * px;
		double p = h2 / mu;

		double nuMin, nuMax;
		if (v2 / 2.0 - mu / r < 0.0 && e < 1.0) {
			nuMin = 0.0;
			nuMax = 2.0 * Math.PI;
		} else {
			double ee = Math.max(e, 1.0001);
			double lim = Math.acos(-1.0 / ee) * 0.985;
			double c = (p / MAX_ORBIT_DISTANCE - 1.0) / ee;
			if (c > -1.0 / ee && c < 1.0) {
				lim = Math.min(lim, Math.acos(c));
			}
			nuMin = -lim;
			nuMax = lim;
			e = ee;
		}
		int count = ORBIT_SEGMENTS + 1;
		for (int s = 0; s < count; s++) {
			double nu = nuMin + (nuMax - nuMin) * s / ORBIT_SEGMENTS;
			double rr = p / (1.0 + e * Math.cos(nu));
			double c = Math.cos(nu) * rr, sn = Math.sin(nu) * rr;
			orbitBuffer[s * 3] = (float) (SolarSystem.SUN_CENTER.x + c * px + sn * qx);
			orbitBuffer[s * 3 + 1] = (float) (SolarSystem.SUN_CENTER.y + c * py + sn * qy);
			orbitBuffer[s * 3 + 2] = (float) (SolarSystem.SUN_CENTER.z + c * pz + sn * qz);
		}
		return count;
	}

	private void drawSun(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz, double ticks,
						 float sx, float sy, float sz) {
		Painter.paintSun(sunMesh, sunColors, (float) ticks);
		rot.identity().rotateY((float) (ticks * 0.0009)).rotateX(0.25f);
		emitBody(vc, pose, sunMesh, sunColors, rot, sx, sy, sz, SolarSystem.SUN_RADIUS, cx, cy, cz, MODE_LIMB, 1.0f, 0, 0, 0);
		rot.identity().rotateY((float) (ticks * 0.0009)).rotateX(0.25f);
		emitBody(vc, pose, haloMesh, halo1, rot, sx, sy, sz, SolarSystem.SUN_RADIUS * 1.12f, cx, cy, cz, MODE_FLAT, 1.0f, 0, 0, 0);
		emitBody(vc, pose, haloMesh, halo2, rot, sx, sy, sz, SolarSystem.SUN_RADIUS * 1.32f, cx, cy, cz, MODE_FLAT, 1.0f, 0, 0, 0);
	}

	private void drawPlanet(VertexConsumer vc, PoseStack.Pose pose, Planet p, double[] pos, double ticks,
							float cx, float cy, float cz, float sx, float sy, float sz) {
		float spin = (float) (p.spinAngleAt(ticks) % (2.0 * Math.PI));
		rot.identity().rotateZ(p.tilt()).rotateY(spin);
		float ambient = p.kind() == Kind.EARTH ? 0.10f : 0.08f;
		BodyMesh mesh = planetMeshes[p.index()];
		emitBody(vc, pose, mesh, mesh.colors, rot, (float) pos[0], (float) pos[1], (float) pos[2], p.radius(),
				cx, cy, cz, MODE_SHADED, ambient, sx, sy, sz);
	}

	private void emitBody(VertexConsumer vc, PoseStack.Pose pose, BodyMesh m, int[] colors, Quaternionf q,
						  float px, float py, float pz, float radius, float camX, float camY, float camZ,
						  int mode, float ambient, float sunX, float sunY, float sunZ) {
		float cullThreshold = m.cube ? -0.02f : -0.15f;
		for (int c = 0; c < m.cells; c++) {
			tmp.set(m.shade[c * 3], m.shade[c * 3 + 1], m.shade[c * 3 + 2]);
			q.transform(tmp);
			float nx = tmp.x, ny = tmp.y, nz = tmp.z;
			tmp.set(m.centers[c * 3], m.centers[c * 3 + 1], m.centers[c * 3 + 2]);
			q.transform(tmp);
			float wx = px + tmp.x * radius, wy = py + tmp.y * radius, wz = pz + tmp.z * radius;

			float tx = camX - wx, ty = camY - wy, tz = camZ - wz;
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			if (tl < 1e-3f) {
				continue;
			}
			float facing = (nx * tx + ny * ty + nz * tz) / tl;
			if (facing < cullThreshold) {
				continue; // faces away from the camera
			}

			float k = 1.0f;
			if (mode == MODE_SHADED) {
				float lx = sunX - wx, ly = sunY - wy, lz = sunZ - wz;
				float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
				float d = (nx * lx + ny * ly + nz * lz) / ll;
				float lit = Math.min(1.0f, Math.max(0.0f, (d + 0.08f) / 0.35f));
				lit = lit * lit * (3.0f - 2.0f * lit);
				k = ambient + (1.0f - ambient) * lit;
			} else if (mode == MODE_LIMB) {
				k = 0.6f + 0.4f * Math.max(0.0f, facing);
			}

			int base = colors[c];
			int color = Painter.rgb(((base >> 16) & 0xFF) * k, ((base >> 8) & 0xFF) * k, (base & 0xFF) * k, base >>> 24);
			for (int i = 0; i < 4; i++) {
				int o = c * 12 + i * 3;
				tmp.set(m.corners[o], m.corners[o + 1], m.corners[o + 2]);
				q.transform(tmp);
				vc.addVertex(pose, px + tmp.x * radius, py + tmp.y * radius, pz + tmp.z * radius).setColor(color);
			}
		}
	}

	private void drawPreview(VertexConsumer vc, PoseStack.Pose pose, SolarSystemClient.Preview preview, float cx, float cy, float cz) {
		SolarPhysics.Prediction pred = preview.prediction();
		float power = preview.power();
		int r = (int) (80 + 175 * power), g = (int) (255 - 175 * power);
		int color = (0xE6 << 24) | (r << 16) | (g << 8) | 0x5A;
		drawPolyline(vc, pose, pred.points(), pred.count(), color, cx, cy, cz, 0.0035f, 0.2f, true, true);

		// aim arrow from the planet along the launch direction
		float radius = system.planets().get(preview.planet()).radius();
		float len = radius * (2.5f + 9.0f * power);
		float ax = (float) (SolarSystem.SUN_CENTER.x + preview.from()[0]);
		float ay = (float) (SolarSystem.SUN_CENTER.y + preview.from()[1]);
		float az = (float) (SolarSystem.SUN_CENTER.z + preview.from()[2]);
		float[] arrow = {ax, ay, az, ax + (float) preview.dir().x * len, ay + (float) preview.dir().y * len, az + (float) preview.dir().z * len};
		drawPolyline(vc, pose, arrow, 2, color | 0xFF000000, cx, cy, cz, 0.0f, radius * 0.18f, false, false);
	}

	/** Camera-facing ribbon through {@code pts}; optionally dotted and fading towards the end. */
	private void drawPolyline(VertexConsumer vc, PoseStack.Pose pose, float[] pts, int count, int color,
							  float cx, float cy, float cz, float widthPerDistance, float minWidth,
							  boolean dotted, boolean fade) {
		int a0 = color >>> 24;
		int segments = count - 1;
		for (int k = 0; k < segments; k++) {
			if (dotted && (k & 1) == 1) {
				continue;
			}
			float ax = pts[k * 3], ay = pts[k * 3 + 1], az = pts[k * 3 + 2];
			float bx = pts[k * 3 + 3], by = pts[k * 3 + 4], bz = pts[k * 3 + 5];
			float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
			float tx = bx - ax, ty = by - ay, tz = bz - az;
			float vx = cx - mx, vy = cy - my, vz = cz - mz;
			// side = tangent x toCamera -> ribbon always faces the camera
			float sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
			float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (sl < 1e-5f) {
				continue;
			}
			float distance = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
			float w = Math.min(4.0f, Math.max(minWidth, distance * widthPerDistance)) / sl;
			sx *= w;
			sy *= w;
			sz *= w;
			int c = color;
			if (fade) {
				int a = (int) (a0 * (1.0f - 0.65f * k / Math.max(1, segments)));
				c = (a << 24) | (color & 0xFFFFFF);
			}
			vc.addVertex(pose, ax - sx, ay - sy, az - sz).setColor(c);
			vc.addVertex(pose, ax + sx, ay + sy, az + sz).setColor(c);
			vc.addVertex(pose, bx + sx, by + sy, bz + sz).setColor(c);
			vc.addVertex(pose, bx - sx, by - sy, bz - sz).setColor(c);
		}
	}

	private void drawStars(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz) {
		for (int i = 0; i < STAR_COUNT; i++) {
			int o = i * 9;
			float x = cx + starData[o] * STAR_RADIUS, y = cy + starData[o + 1] * STAR_RADIUS, z = cz + starData[o + 2] * STAR_RADIUS;
			float ux = starData[o + 3], uy = starData[o + 4], uz = starData[o + 5];
			float vx = starData[o + 6], vy = starData[o + 7], vz = starData[o + 8];
			int color = starColors[i];
			vc.addVertex(pose, x - ux - vx, y - uy - vy, z - uz - vz).setColor(color);
			vc.addVertex(pose, x + ux - vx, y + uy - vy, z + uz - vz).setColor(color);
			vc.addVertex(pose, x + ux + vx, y + uy + vy, z + uz + vz).setColor(color);
			vc.addVertex(pose, x - ux + vx, y - uy + vy, z - uz + vz).setColor(color);
		}
	}
}
