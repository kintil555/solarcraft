package com.kintil555.solarsystem.client;

import java.util.List;

import com.kintil555.solarsystem.system.SolarSystem;
import com.kintil555.solarsystem.system.SolarSystem.Kind;
import com.kintil555.solarsystem.system.SolarSystem.Planet;

/** How the solar system is displayed. Purely visual: the physics always runs on the real orbits. */
enum SolarStyle {
	REAL("Real", "Jarak antar planet lebar, ukuran planet berbeda jauh, cincin partikel pada raksasa gas/es."),
	ENLARGED("Diperbesar", "Tampilan bawaan: planet dibesarkan agar mudah dilihat."),
	SIMPLE("Simple", "Jarak rapat dan rata, ukuran planet seragam. Cocok untuk melihat semuanya sekaligus.");

	private static final double REAL_DISTANCE = 2.2;
	private static final double SIMPLE_FIRST = 62.0;
	private static final double SIMPLE_STEP = 34.0;
	private static final double SIMPLE_TAIL = 0.6;

	final String label;
	final String description;

	SolarStyle(String label, String description) {
		this.label = label;
		this.description = description;
	}

	float radiusScale(Kind kind) {
		return switch (this) {
			case ENLARGED -> 1.0f;
			case REAL -> switch (kind) {
				case EARTH -> 0.6f;
				case ROCKY -> 0.5f;
				case GAS -> 1.25f;
				case ICE -> 1.15f;
			};
			case SIMPLE -> switch (kind) {
				case EARTH -> 0.9f;
				case ROCKY -> 0.8f;
				case GAS -> 0.6f;
				case ICE -> 0.7f;
			};
		};
	}

	/** Gas and ice giants carry particle rings in the real style. */
	boolean hasNaturalRing(Planet p) {
		return this == REAL && (p.kind() == Kind.GAS || p.kind() == Kind.ICE && (p.seed() & 1L) == 0L);
	}

	RadialMap buildMap(SolarSystem system) {
		return switch (this) {
			case ENLARGED -> RadialMap.linear(1.0);
			case REAL -> RadialMap.linear(REAL_DISTANCE);
			case SIMPLE -> {
				List<Planet> planets = system.planets();
				double[] real = new double[planets.size() + 1];
				double[] shown = new double[planets.size() + 1];
				for (int i = 0; i < planets.size(); i++) {
					real[i + 1] = planets.get(i).orbit();
					shown[i + 1] = SIMPLE_FIRST + SIMPLE_STEP * i;
				}
				yield new RadialMap(real, shown, SIMPLE_TAIL);
			}
		};
	}
}
