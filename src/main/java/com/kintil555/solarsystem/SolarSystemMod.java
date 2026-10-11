package com.kintil555.solarsystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.WeakHashMap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import com.kintil555.solarsystem.system.SolarPhysics;
import com.kintil555.solarsystem.system.SolarSystem;

public final class SolarSystemMod implements ModInitializer {
	public static final String MOD_ID = "solarsystem";
	/** Dimension type used by the (overridden) default world preset. */
	public static final Identifier SPACE_TYPE = Identifier.fromNamespaceAndPath(MOD_ID, "space");

	private static final String INIT_TAG = MOD_ID + ".initialized";
	private static final String STATE_FILE = "solarsystem_state.txt";
	private static final String STELLAR_FILE = "solarsystem_stellar.txt";
	/** Minimum game ticks between two accepted laser pulses of one player (the client fires every 5). */
	private static final int LASER_MIN_INTERVAL = 4;
	private static final float FLY_SPEED = 0.4f;
	private static final int SAVE_INTERVAL_TICKS = 6000;

	private static SolarSystem system;
	private static SolarPhysics physics;
	private static long currentTick;
	private static final WeakHashMap<ServerPlayer, Long> LAST_LASER = new WeakHashMap<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(SeedPayload.TYPE, SeedPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StellarPayload.TYPE, StellarPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LaunchPayload.TYPE, LaunchPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LaserPayload.TYPE, LaserPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(SolarSystemMod::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			system = null;
			physics = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(SolarSystemMod::onServerTick);

		ServerPlayNetworking.registerGlobalReceiver(LaunchPayload.TYPE, (payload, context) -> {
			SolarPhysics sim = physics;
			if (sim == null || payload.planet() < 0 || payload.planet() >= sim.count()) {
				return;
			}
			double len = Math.sqrt(payload.dx() * payload.dx() + payload.dy() * payload.dy() + payload.dz() * payload.dz());
			if (!(len > 1e-4) || !Float.isFinite(payload.power())) {
				return;
			}
			double power = Math.max(0.0, Math.min(1.0, payload.power())) * SolarPhysics.MAX_DV / len;
			sim.impulse(payload.planet(), payload.dx() * power, payload.dy() * power, payload.dz() * power);
		});

		ServerPlayNetworking.registerGlobalReceiver(LaserPayload.TYPE, (payload, context) -> {
			SolarPhysics sim = physics;
			if (sim == null || payload.target() < -1 || payload.target() >= sim.count()) {
				return;
			}
			ServerPlayer shooter = context.player();
			Long last = LAST_LASER.get(shooter);
			if (last != null && currentTick - last < LASER_MIN_INTERVAL) {
				return;
			}
			LAST_LASER.put(shooter, currentTick);
			if (payload.target() < 0) {
				sim.laserSun();
			} else {
				sim.laserPlanet(payload.target());
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			ServerLevel level = player.level();
			if (!level.dimensionTypeRegistration().is(SPACE_TYPE)) {
				return;
			}
			ServerPlayNetworking.send(player, new SeedPayload(level.getSeed()));
			if (physics != null) {
				ServerPlayNetworking.send(player, StellarPayload.of(physics.stellar()));
			}
			preparePlayer(player, level);
		});
	}

	private static void onServerStarted(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!level.dimensionTypeRegistration().is(SPACE_TYPE)) {
			return;
		}
		system = SolarSystem.generate(level.getSeed());
		physics = SolarPhysics.initial(system, level.getGameTime());
		Path file = server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE);
		try {
			if (Files.exists(file)) {
				physics.deserialize(Files.readString(file), level.getSeed());
			}
			Path stellarFile = server.getWorldPath(LevelResource.ROOT).resolve(STELLAR_FILE);
			if (Files.exists(stellarFile)) {
				physics.stellar().deserialize(Files.readString(stellarFile), level.getSeed());
			}
		} catch (IOException ignored) {
			// keep the freshly generated circular orbits
		}
	}

	private static void onServerTick(MinecraftServer server) {
		SolarPhysics sim = physics;
		if (sim == null) {
			return;
		}
		ServerLevel level = server.overworld();
		long time = level.getGameTime();
		currentTick = time;
		sim.tick(time);
		if (time % SAVE_INTERVAL_TICKS == 0) {
			save(server);
		}
		StatePayload payload = new StatePayload(sim.dump());
		StellarPayload stellar = sim.stellar().consumeChanged() ? StellarPayload.of(sim.stellar()) : null;
		for (ServerPlayer player : level.players()) {
			ServerPlayNetworking.send(player, payload);
			if (stellar != null) {
				ServerPlayNetworking.send(player, stellar);
			}
		}
	}

	private static void save(MinecraftServer server) {
		if (physics == null || system == null) {
			return;
		}
		try {
			Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE), physics.serialize(system.seed()));
			Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve(STELLAR_FILE), physics.stellar().serialize(system.seed()));
		} catch (IOException ignored) {
			// not fatal: the system restarts on circular orbits
		}
	}

	private static void preparePlayer(ServerPlayer player, ServerLevel level) {
		if (!player.isCreative() && !player.isSpectator()) {
			player.setGameMode(GameType.CREATIVE);
		}
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.setFlyingSpeed(FLY_SPEED);
		player.onUpdateAbilities();

		// addTag returns true only the first time -> teleport to the start point once.
		if (player.addTag(INIT_TAG) && system != null && physics != null) {
			int e = system.earth().index();
			Vec3 earth = new Vec3(SolarSystem.SUN_CENTER.x + physics.px(e), SolarSystem.SUN_CENTER.y + physics.py(e),
					SolarSystem.SUN_CENTER.z + physics.pz(e));
			Vec3 outward = earth.subtract(SolarSystem.SUN_CENTER).normalize();
			Vec3 start = earth.add(outward.scale(system.earth().radius() * 4.5 + 14.0)).add(0.0, system.earth().radius() * 0.8, 0.0);
			float[] look = SolarSystem.lookAt(start, earth);
			level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, BlockPos.containing(start), look[0], look[1]));
			player.teleportTo(level, start.x, start.y, start.z, Set.<Relative>of(), look[0], look[1], true);
		}
	}
}
