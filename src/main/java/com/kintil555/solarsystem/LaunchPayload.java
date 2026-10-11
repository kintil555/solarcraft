package com.kintil555.solarsystem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: launch {@code planet} along unit direction (dx,dy,dz) with {@code power} in [0, 1]. */
public record LaunchPayload(int planet, float dx, float dy, float dz, float power) implements CustomPacketPayload {
	public static final Type<LaunchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SolarSystemMod.MOD_ID, "launch"));
	public static final StreamCodec<FriendlyByteBuf, LaunchPayload> CODEC = StreamCodec.ofMember(LaunchPayload::write, LaunchPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(planet);
		buf.writeFloat(dx);
		buf.writeFloat(dy);
		buf.writeFloat(dz);
		buf.writeFloat(power);
	}

	private static LaunchPayload read(FriendlyByteBuf buf) {
		return new LaunchPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
