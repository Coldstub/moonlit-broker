package dev.xqanzd.moonlitbroker.trade.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C mastery progress update.
 */
public record MasteryProgressS2CPacket(
        String katanaId,
        int progress
) implements CustomPayload {
    public static final Identifier PACKET_ID =
            Identifier.of("xqanzd_moonlit_broker", "mastery_progress");

    public static final Id<MasteryProgressS2CPacket> ID =
            new Id<>(PACKET_ID);

    public static final PacketCodec<RegistryByteBuf, MasteryProgressS2CPacket> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING,
                    MasteryProgressS2CPacket::katanaId,
                    PacketCodecs.VAR_INT,
                    MasteryProgressS2CPacket::progress,
                    MasteryProgressS2CPacket::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
