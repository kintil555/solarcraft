package com.kintil555.solarsystem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: one laser pulse hit the Sun ({@code target == -1}) or planet {@code target}. */
public record LaserPayload(int target) implements CustomPacketPayload {
	public static final Type<LaserPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SolarSystemMod.MOD_ID, "laser"));
	public static final StreamCodec<FriendlyByteBuf, LaserPayload> CODEC = StreamCodec.ofMember(LaserPayload::write, LaserPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(target + 1);
	}

	private static LaserPayload read(FriendlyByteBuf buf) {
		return new LaserPayload(buf.readVarInt() - 1);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
