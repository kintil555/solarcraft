package com.kintil555.solarsystem.client;

import com.kintil555.solarsystem.system.SolarSystem.Kind;
import com.kintil555.solarsystem.system.SolarSystem.Planet;

/** Procedural colouring for the Sun and the non-Earth planets. */
final class Painter {
	private Painter() {}

	private static final int[] ROCKY = {0xA0583C, 0x8C8A84, 0xC2A06E, 0x6E5F58};
	private static final int[][] GAS = {
			{0xD8B58A, 0xB98B5C, 0xF0E2C6},
			{0xC9A66B, 0xE8D08C, 0xA6804A},
			{0x5C8FD6, 0x3A63B0, 0xBFD7F5},
			{0xB05A3C, 0xD98C5F, 0xF2C9A0}};
	private static final int[][] ICE = {
			{0x9FD6E8, 0x7FB5D6},
			{0x6C8FE0, 0x4F6FC4},
			{0xB5E0D6, 0x8CC8BE},
			{0xA8C4E8, 0x86A2D8}};

	static int rgb(float r, float g, float b, int a) {
		int ri = Math.min(255, Math.max(0, (int) (r + 0.5f)));
		int gi = Math.min(255, Math.max(0, (int) (g + 0.5f)));
		int bi = Math.min(255, Math.max(0, (int) (b + 0.5f)));
		return (a << 24) | (ri << 16) | (gi << 8) | bi;
	}

	private static float ch(int c, int shift) {
		return (c >> shift) & 0xFF;
	}

	private static int mix(int c0, int c1, float t, float shade) {
		float r = (ch(c0, 16) + (ch(c1, 16) - ch(c0, 16)) * t) * shade;
		float g = (ch(c0, 8) + (ch(c1, 8) - ch(c0, 8)) * t) * shade;
		float b = (ch(c0, 0) + (ch(c1, 0) - ch(c0, 0)) * t) * shade;
		return rgb(r, g, b, 255);
	}

	/** Static colours of a rocky / gas / ice planet. */
	static void paintPlanet(Planet p, BodyMesh m) {
		int seed = (int) (p.seed() ^ (p.seed() >>> 32));
		float off = (seed & 0xFF) * 0.37f;
		for (int c = 0; c < m.cells; c++) {
			float x = m.normals[c * 3], y = m.normals[c * 3 + 1], z = m.normals[c * 3 + 2];
			int color;
			if (p.kind() == Kind.ROCKY) {
				float n = Noise.fbm(x * 3.5f + off, y * 3.5f, z * 3.5f, 4, seed);
				float d = Noise.value(x * 14f + off, y * 14f, z * 14f, seed + 7);
				float shade = 0.62f + 0.75f * n + 0.18f * (d - 0.5f);
				color = mix(ROCKY[p.style()], ROCKY[(p.style() + 1) & 3], 0.15f, shade);
			} else if (p.kind() == Kind.GAS) {
				int[] pal = GAS[p.style()];
				float warp = Noise.fbm(x * 2.2f + off, y * 2.2f, z * 2.2f, 3, seed);
				float band = 0.5f + 0.5f * (float) Math.sin((y + 0.35f * warp) * (10f + (seed & 7)));
				int base = mix(pal[0], pal[1], band, 1.0f);
				float storm = Noise.smoothstep(0.55f, 0.8f, Noise.fbm(x * 5f + off, y * 9f, z * 5f, 3, seed + 3));
				color = mix(base, pal[2], storm * 0.7f, 1.0f);
			} else {
				int[] pal = ICE[p.style()];
				float n = Noise.fbm(x * 3f + off, y * 3f, z * 3f, 3, seed);
				float d = Noise.value(x * 12f + off, y * 12f, z * 12f, seed + 5);
				color = mix(pal[0], pal[1], n, 0.9f + 0.2f * d);
			}
			m.colors[c] = color;
		}
	}

	/** Animated surface colours of the Sun (granulation, sunspots). */
	static void paintSun(BodyMesh m, int[] out, float ticks) {
		float t = ticks * 0.0016f;
		for (int c = 0; c < m.cells; c++) {
			float x = m.normals[c * 3], y = m.normals[c * 3 + 1], z = m.normals[c * 3 + 2];
			float n = Noise.fbm(x * 3.0f + t, y * 3.0f - t * 0.6f, z * 3.0f + t * 0.8f, 4, 11);
			float g = Noise.fbm(x * 9.0f - t * 2.0f, y * 9.0f, z * 9.0f + t * 2.0f, 2, 29);
			float heat = Math.min(1.0f, Math.max(0.0f, n * 1.25f + (g - 0.5f) * 0.5f - 0.1f));
			float r = 255;
			float gr;
			float b;
			if (heat < 0.5f) {
				float k = heat / 0.5f;
				gr = 110 + 90 * k;
				b = 10 + 30 * k;
			} else {
				float k = (heat - 0.5f) / 0.5f;
				gr = 200 + 45 * k;
				b = 40 + 130 * k;
			}
			float spot = Noise.smoothstep(0.66f, 0.76f, Noise.fbm(x * 2.2f + 31.4f, y * 2.2f, z * 2.2f - t * 0.2f, 3, 53));
			float dark = 1.0f - 0.6f * spot;
			out[c] = rgb(r * dark, gr * dark * (1.0f - 0.25f * spot), b * dark * (1.0f - 0.6f * spot), 255);
		}
	}
}
