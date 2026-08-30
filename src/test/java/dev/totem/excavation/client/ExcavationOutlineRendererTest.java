package dev.totem.excavation.client;

import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExcavationOutlineRendererTest {
    @Test
    void selectionOutlineRemainsDepthTested() {
        SimpleGizmoCollector collector = new SimpleGizmoCollector();

        try (var ignored = Gizmos.withCollector(collector)) {
            ExcavationOutlineRenderer.addSelectionOutline(new AABB(0, 0, 0, 1, 1, 1));
        }

        assertEquals(1, collector.getGizmos().size());
        assertFalse(collector.getGizmos().getFirst().isAlwaysOnTop());
    }
}
