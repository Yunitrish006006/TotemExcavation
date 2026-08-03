package dev.totem.excavation.client;

import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.registry.ExcavationItems;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Client-only hook for the local held hammer preview. The render callback is
 * intentionally separate from all common/server classes.
 */
public final class ExcavationOutlineRenderer {
    private static boolean registered;

    private ExcavationOutlineRenderer() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderLocalSelection());
        registered = true;
    }

    private static void renderLocalSelection() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!ExcavationItems.isHammer(held)) {
            return;
        }
        AreaSelection selection = held.get(ExcavationDataComponents.AREA_SELECTION);
        if (selection == null || !selection.dimension().equals(client.level.dimension())) {
            return;
        }
        var second = selection.secondCorner().orElse(selection.firstCorner());
        AABB box = AABB.encapsulatingFullBlocks(selection.firstCorner(), second).inflate(0.002D);
        Gizmos.cuboid(box, GizmoStyle.stroke(0xFF4FC3F7, 1.5F)).setAlwaysOnTop();
    }
}
