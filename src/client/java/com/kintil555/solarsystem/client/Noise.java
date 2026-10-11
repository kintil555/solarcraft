package com.kintil555.solarsystem.client;

/** Small seeded 3D value noise used for the procedural textures. */
final class Noise {
	private Noise() {}

	private static float hash(int x, int y, int z, int seed) {
		int h = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	static float value(float x, float y, float z, int seed) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		float tx = smooth(x - xi);
		float ty = smooth(y - yi);
		float tz = smooth(z - zi);
		float c00 = lerp(hash(xi, yi, zi, seed), hash(xi + 1, yi, zi, seed), tx);
		float c10 = lerp(hash(xi, yi + 1, zi, seed), hash(xi + 1, yi + 1, zi, seed), tx);
		float c01 = lerp(hash(xi, yi, zi + 1, seed), hash(xi + 1, yi, zi + 1, seed), tx);
		float c11 = lerp(hash(xi, yi + 1, zi + 1, seed), hash(xi + 1, yi + 1, zi + 1, seed), tx);
		return lerp(lerp(c00, c10, ty), lerp(c01, c11, ty), tz);
	}

	/** Fractal Brownian motion in [0, 1]. */
	static float fbm(float x, float y, float z, int octaves, int seed) {
		float sum = 0.0f;
		float amp = 0.5f;
		float norm = 0.0f;
		for (int i = 0; i < octaves; i++) {
			sum += amp * value(x, y, z, seed + i * 31);
			norm += amp;
			x *= 2.03f;
			y *= 2.03f;
			z *= 2.03f;
			amp *= 0.5f;
		}
		return sum / norm;
	}

	static float smoothstep(float a, float b, float x) {
		float t = Math.min(1.0f, Math.max(0.0f, (x - a) / (b - a)));
		return t * t * (3.0f - 2.0f * t);
	}
}
