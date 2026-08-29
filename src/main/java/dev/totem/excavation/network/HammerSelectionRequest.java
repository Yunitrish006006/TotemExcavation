package dev.totem.excavation.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A compact bounded client intent. The server never accepts selection or item state from it. */
public record HammerSelectionRequest(
        long sequence,
        int selectedSlot,
        BlockPos target,
        Direction face
) implements CustomPacketPayload {
    public static final int MIN_HOTBAR_SLOT = 0;
    public static final int MAX_HOTBAR_SLOT = 8;
    public static final Type<HammerSelectionRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totem", "excavation/hammer_selection")
    );
    public static final StreamCodec<FriendlyByteBuf, HammerSelectionRequest> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.sequence);
                buffer.writeByte(payload.selectedSlot);
                BlockPos.STREAM_CODEC.encode(buffer, payload.target);
                Direction.STREAM_CODEC.encode(buffer, payload.face);
            },
            buffer -> new HammerSelectionRequest(
                    buffer.readVarLong(),
                    buffer.readUnsignedByte(),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    Direction.STREAM_CODEC.decode(buffer)
            )
    );

    public HammerSelectionRequest {
        target = target.immutable();
    }

    public boolean hasValidEnvelope() {
        return sequence > 0L
                && selectedSlot >= MIN_HOTBAR_SLOT
                && selectedSlot <= MAX_HOTBAR_SLOT;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
