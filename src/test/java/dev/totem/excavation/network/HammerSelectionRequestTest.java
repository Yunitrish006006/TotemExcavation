package dev.totem.excavation.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HammerSelectionRequestTest {
    @Test
    void fixedEnvelopeRoundTripsWithinACompactBound() {
        HammerSelectionRequest original = new HammerSelectionRequest(
                42L,
                7,
                new BlockPos(-30_000_000, 319, 30_000_000),
                Direction.WEST
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HammerSelectionRequest.CODEC.encode(buffer, original);
            assertTrue(buffer.readableBytes() <= 24, "Selection intent grew beyond its fixed compact bound");
            assertEquals(original, HammerSelectionRequest.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void envelopeRejectsInvalidSequencesAndHotbarSlots() {
        BlockPos target = new BlockPos(1, 2, 3);
        assertTrue(new HammerSelectionRequest(1L, 0, target, Direction.UP).hasValidEnvelope());
        assertTrue(new HammerSelectionRequest(Long.MAX_VALUE, 8, target, Direction.DOWN).hasValidEnvelope());
        assertFalse(new HammerSelectionRequest(0L, 0, target, Direction.UP).hasValidEnvelope());
        assertFalse(new HammerSelectionRequest(-1L, 0, target, Direction.UP).hasValidEnvelope());
        assertFalse(new HammerSelectionRequest(1L, -1, target, Direction.UP).hasValidEnvelope());
        assertFalse(new HammerSelectionRequest(1L, 9, target, Direction.UP).hasValidEnvelope());
    }
}
