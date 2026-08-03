package dev.totem.excavation.component;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaSelectionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void persistentCodecRoundTripsCompletedSelection() {
        AreaSelection original = new AreaSelection(
                AreaSelection.CURRENT_DATA_VERSION,
                Level.OVERWORLD,
                new BlockPos(-12, 64, 8),
                Optional.of(new BlockPos(24, 70, -3))
        );

        var encoded = AreaSelection.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        AreaSelection decoded = AreaSelection.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
        assertTrue(decoded.isComplete());
    }

    @Test
    void firstCornerSelectionIsIncompleteUntilSecondCornerIsSet() {
        AreaSelection first = AreaSelection.firstCorner(Level.OVERWORLD, new BlockPos(3, 80, 3));

        assertEquals(AreaSelection.CURRENT_DATA_VERSION, first.dataVersion());
        assertFalse(first.isComplete());
        assertEquals(first, first.withSecondCorner(new BlockPos(3, 80, 3)).withoutSecondCorner());
    }
}
