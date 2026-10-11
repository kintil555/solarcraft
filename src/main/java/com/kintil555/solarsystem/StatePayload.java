package com.kintil555.solarsystem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client, every tick: {@code [px,py,pz,vx,vy,vz]} per planet (relative to the Sun). */
public record StatePayload(float[] data) implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SolarSystemMod.MOD_ID, "state"));
	public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = StreamCodec.ofMember(StatePayload::write, StatePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(data.length);
		for (float f : data) {
			buf.writeFloat(f);
		}
	}

	private static StatePayload read(FriendlyByteBuf buf) {
		int len = Math.min(buf.readVarInt(), 256);
		float[] d = new float[len];
		for (int i = 0; i < len; i++) {
			d[i] = buf.readFloat();
		}
		return new StatePayload(d);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
