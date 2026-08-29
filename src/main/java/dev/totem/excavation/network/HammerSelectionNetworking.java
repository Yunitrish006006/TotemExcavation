package dev.totem.excavation.network;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.item.HammerItem;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.selection.HammerSelectionService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server authority and replay protection for crouch-attack selection intents. */
public final class HammerSelectionNetworking {
    private static final Map<ServerPlayer, Long> LAST_SEQUENCE = new IdentityHashMap<>();
    private static boolean registered;

    private HammerSelectionNetworking() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(
                HammerSelectionRequest.TYPE,
                HammerSelectionRequest.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                HammerSelectionRequest.TYPE,
                (payload, context) -> context.server().execute(
                        () -> handleRequest(context.player(), payload)
                )
        );
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> LAST_SEQUENCE.remove(handler.getPlayer())
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_SEQUENCE.clear());
        registered = true;
    }

    /** Exposed for server GameTests; production calls arrive through Fabric networking. */
    public static boolean handleRequest(ServerPlayer player, HammerSelectionRequest request) {
        if (player == null || request == null || !request.hasValidEnvelope()) {
            return false;
        }
        long previous = LAST_SEQUENCE.getOrDefault(player, 0L);
        if (request.sequence() <= previous
                || player.getInventory().getSelectedSlot() != request.selectedSlot()) {
            return false;
        }

        ServerLevel level = player.level();
        if (!isValidServerTarget(player, level, request.target(), request.face())) {
            return false;
        }

        // Re-read the exact server-owned main-hand object after validation. The payload cannot
        // nominate a stack, item, tier, component, dimension, range, or selection state.
        ItemStack exactStack = player.getMainHandItem();
        HammerItem exactHammer = ExcavationItems.hammer(exactStack);
        if (exactHammer == null) {
            return false;
        }

        LAST_SEQUENCE.put(player, request.sequence());
        HammerSelectionService.select(player, exactStack, exactHammer, level, request.target());
        return true;
    }

    static boolean isValidServerTarget(
            ServerPlayer player,
            ServerLevel level,
            BlockPos target,
            Direction claimedFace
    ) {
        ItemStack stack = player.getMainHandItem();
        if (!player.isAlive()
                || !player.isShiftKeyDown()
                || player.isSpectator()
                || player.containerMenu != player.inventoryMenu
                || player.level() != level
                || ExcavationItems.hammer(stack) == null
                || !level.isInWorldBounds(target)
                || !level.isLoaded(target)
                || !level.getWorldBorder().isWithinBounds(target)
                || !player.mayInteract(level, target)
                || player.blockActionRestricted(level, target, player.gameMode())
                || !level.getBlockState(target).is(ExcavationTags.HAMMER_MINEABLE)) {
            return false;
        }

        double reach = player.blockInteractionRange();
        HitResult hit = player.pick(reach, 1.0F, false);
        return hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(target)
                && blockHit.getDirection() == claimedFace;
    }
}
