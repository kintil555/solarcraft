package com.kintil555.solarsystem.client;

/**
 * Monotonic piecewise-linear map from a real orbital distance to a displayed distance (and back). Only the
 * distance from the Sun changes, never the direction, so orbits keep their shape.
 */
final class RadialMap {
	private final double[] real;
	private final double[] shown;
	private final double tail;

	RadialMap(double[] real, double[] shown, double tail) {
		this.real = real;
		this.shown = shown;
		this.tail = tail;
	}

	static RadialMap linear(double factor) {
		return new RadialMap(new double[] {0.0, 1.0}, new double[] {0.0, factor}, factor);
	}

	double map(double r) {
		return interpolate(real, shown, r, tail);
	}

	double unmap(double r) {
		return interpolate(shown, real, r, 1.0 / tail);
	}

	/** Factor that turns a Sun-relative vector of length {@code r} into its displayed vector. */
	double scale(double r) {
		return r < 1e-6 ? shown[1] / real[1] : map(r) / r;
	}

	private static double interpolate(double[] from, double[] to, double v, double tailSlope) {
		int n = from.length;
		if (v >= from[n - 1]) {
			return to[n - 1] + (v - from[n - 1]) * tailSlope;
		}
		for (int i = 1; i < n; i++) {
			if (v <= from[i]) {
				double t = (v - from[i - 1]) / (from[i] - from[i - 1]);
				return to[i - 1] + t * (to[i] - to[i - 1]);
			}
		}
		return to[n - 1];
	}
}
