package com.kintil555.solarsystem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.kintil555.solarsystem.system.StellarState;

/** Server -> client: laser heat, destroyed planets and the supernova state (see {@link StellarState}). */
public record StellarPayload(float sunHeat, float shock, boolean sunGone, float[] heat, boolean[] alive) implements CustomPacketPayload {
	public static final Type<StellarPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SolarSystemMod.MOD_ID, "stellar"));
	public static final StreamCodec<FriendlyByteBuf, StellarPayload> CODEC = StreamCodec.ofMember(StellarPayload::write, StellarPayload::read);

	public static StellarPayload of(StellarState s) {
		return new StellarPayload(s.sunHeat(), s.shock(), s.sunGone(), s.heatCopy(), s.aliveCopy());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeFloat(sunHeat);
		buf.writeFloat(shock);
		buf.writeBoolean(sunGone);
		buf.writeVarInt(heat.length);
		for (int i = 0; i < heat.length; i++) {
			buf.writeFloat(heat[i]);
			buf.writeBoolean(alive[i]);
		}
	}

	private static StellarPayload read(FriendlyByteBuf buf) {
		float sunHeat = buf.readFloat();
		float shock = buf.readFloat();
		boolean gone = buf.readBoolean();
		int len = Math.min(buf.readVarInt(), 64);
		float[] heat = new float[len];
		boolean[] alive = new boolean[len];
		for (int i = 0; i < len; i++) {
			heat[i] = buf.readFloat();
			alive[i] = buf.readBoolean();
		}
		return new StellarPayload(sunHeat, shock, gone, heat, alive);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
