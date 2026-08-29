package dev.totem.excavation.client;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.network.HammerSelectionRequest;
import dev.totem.excavation.registry.ExcavationItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Cancels every held attack tick while sending one bounded intent per physical press. */
public final class HammerSelectionClientInput {
    private static long nextSequence;
    private static boolean registered;

    private HammerSelectionClientInput() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ClientPreAttackCallback.EVENT.register(HammerSelectionClientInput::onPreAttack);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetSequence());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetSequence());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> resetSequence());
        registered = true;
    }

    private static boolean onPreAttack(Minecraft client, LocalPlayer player, int clickCount) {
        HammerSelectionRequest request = requestFor(client, player, clickCount);
        if (request == null) {
            return false;
        }
        if (shouldSendRequest(clickCount)) {
            ClientPlayNetworking.send(request);
        }
        // Fabric's ClientPreAttack mixin also calls stopDestroyBlock while held, preventing
        // survival continueDestroyBlock crack progress after the first physical click.
        return true;
    }

    private static HammerSelectionRequest requestFor(
            Minecraft client,
            LocalPlayer player,
            int clickCount
    ) {
        if (client.gui.screen() != null
                || player == null
                || !player.isShiftKeyDown()
                || player.isSpectator()
                || player.containerMenu != player.inventoryMenu
                || ExcavationItems.hammer(player.getMainHandItem()) == null
                || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !player.level().getBlockState(hit.getBlockPos()).is(ExcavationTags.HAMMER_MINEABLE)
                || !ClientPlayNetworking.canSend(HammerSelectionRequest.TYPE)) {
            return null;
        }
        long sequence = shouldSendRequest(clickCount) ? nextSequence() : currentSequence();
        return new HammerSelectionRequest(
                sequence,
                player.getInventory().getSelectedSlot(),
                hit.getBlockPos(),
                hit.getDirection()
        );
    }

    static boolean shouldSendRequest(int clickCount) {
        return clickCount != 0;
    }

    private static long nextSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            nextSequence = 1L;
        } else {
            nextSequence++;
        }
        return nextSequence;
    }

    private static long currentSequence() {
        return Math.max(1L, nextSequence);
    }

    private static void resetSequence() {
        nextSequence = 0L;
    }
}
