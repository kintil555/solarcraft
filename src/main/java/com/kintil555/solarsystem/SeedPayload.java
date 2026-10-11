package com.kintil555.solarsystem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: the world seed that defines this world's solar system. */
public record SeedPayload(long seed) implements CustomPacketPayload {
	public static final Type<SeedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SolarSystemMod.MOD_ID, "seed"));
	public static final StreamCodec<FriendlyByteBuf, SeedPayload> CODEC =
			ByteBufCodecs.LONG.map(SeedPayload::new, SeedPayload::seed).cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
