package dev.totem.excavation.session;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.item.HammerItem;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.selection.HammerSelectionService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, server-thread-only area scans and harvesting sessions. */
public final class ExcavationSessions {
    private static final int SCAN_POSITIONS_PER_TICK = 256;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> HARVESTING = new HashSet<>();
    private static boolean registered;

    private ExcavationSessions() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(ExcavationSessions::tickServer);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                cancel(player);
            }
        });
        ServerPlayerEvents.LEAVE.register(ExcavationSessions::cancel);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        registered = true;
    }

    public static boolean isHarvesting(ServerPlayer player) {
        return HARVESTING.contains(player.getUUID());
    }

    static int harvestBudget(int remainingTargets) {
        return Math.min(Math.max(remainingTargets, 0), ExcavationConfig.blocksPerTick());
    }

    private static void cancel(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        HARVESTING.remove(player.getUUID());
    }

    private static void clear() {
        SESSIONS.clear();
        HARVESTING.clear();
    }

    public static void startAfterManualBreak(
            ServerPlayer player,
            ServerLevel level,
            ItemStack heldStack,
            BlockPos triggerPos
    ) {
        HammerItem hammer = ExcavationItems.hammer(heldStack);
        AreaSelection selection = heldStack.get(ExcavationDataComponents.AREA_SELECTION);
        if (hammer == null || selection == null || !selection.isComplete()
                || !selection.dimension().equals(level.dimension())) {
            return;
        }
        if (!withinSelection(selection, triggerPos)) {
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(player.getUUID(), selection, hammer, heldStack, level));
    }

    private static void tickServer(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Session> entry : List.copyOf(SESSIONS.entrySet())) {
            if (entry.getValue().tick(server)) {
                finished.add(entry.getKey());
            }
        }
        finished.forEach(SESSIONS::remove);
    }

    private static boolean withinSelection(AreaSelection selection, BlockPos pos) {
        BlockPos second = selection.secondCorner().orElse(null);
        if (second == null) {
            return false;
        }
        BlockPos min = BlockPos.min(selection.firstCorner(), second);
        BlockPos max = BlockPos.max(selection.firstCorner(), second);
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static final class Session {
        private final UUID playerId;
        private final AreaSelection selection;
        private final HammerItem hammer;
        private final ItemStack selectedStack;
        private final ServerLevel level;
        private final BlockPos min;
        private final BlockPos max;
        private final List<BlockPos> candidates = new ArrayList<>();
        private List<BlockPos> targets = List.of();
        private int scanX;
        private int scanY;
        private int scanZ;
        private boolean scanComplete;
        private int targetIndex;
        private int harvested;

        private Session(
                UUID playerId,
                AreaSelection selection,
                HammerItem hammer,
                ItemStack selectedStack,
                ServerLevel level
        ) {
            this.playerId = playerId;
            this.selection = selection;
            this.hammer = hammer;
            this.selectedStack = selectedStack;
            this.level = level;
            BlockPos second = selection.secondCorner().orElseThrow();
            this.min = BlockPos.min(selection.firstCorner(), second);
            this.max = BlockPos.max(selection.firstCorner(), second);
            this.scanX = min.getX();
            this.scanY = min.getY();
            this.scanZ = min.getZ();
        }

        private boolean tick(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (!validPlayerState(server, player)) {
                return true;
            }
            ItemStack heldStack = player.getMainHandItem();
            if (!scanComplete) {
                scan(player, heldStack);
                return false;
            }
            return harvest(player, heldStack);
        }

        private boolean validPlayerState(MinecraftServer server, ServerPlayer player) {
            if (player == null || !player.isAlive() || player.level() != level
                    || server.getLevel(level.dimension()) != level) {
                return false;
            }
            ItemStack stack = player.getMainHandItem();
            return stack == selectedStack
                    && ExcavationItems.hammer(stack) == hammer
                    && selection.equals(stack.get(ExcavationDataComponents.AREA_SELECTION));
        }

        private void scan(ServerPlayer player, ItemStack stack) {
            int scanned = 0;
            while (scanned++ < SCAN_POSITIONS_PER_TICK && !scanComplete) {
                BlockPos pos = new BlockPos(scanX, scanY, scanZ);
                advanceScanPosition();
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (isEligible(stack, state)) {
                    candidates.add(pos.immutable());
                }
            }
            if (!scanComplete) {
                return;
            }
            if (candidates.isEmpty()) {
                HammerSelectionService.clearSelection(stack);
                player.sendOverlayMessage(Component.translatable("message.totem.excavation.session.empty"));
                return;
            }
            candidates.sort(Comparator
                    .comparing((BlockPos pos) -> !level.getBlockState(pos).is(ExcavationTags.HAMMER_EFFICIENCY))
                    .thenComparingDouble(pos -> pos.distSqr(player.blockPosition())));
            int targetCount = Math.min(
                    candidates.size(),
                    (int) Math.floor(candidates.size() * hammer.tier().completionFraction(level, stack))
            );
            targets = targetCount == 0 ? List.of() : List.copyOf(candidates.subList(0, targetCount));
        }

        private void advanceScanPosition() {
            scanZ++;
            if (scanZ <= max.getZ()) {
                return;
            }
            scanZ = min.getZ();
            scanX++;
            if (scanX <= max.getX()) {
                return;
            }
            scanX = min.getX();
            scanY++;
            if (scanY <= max.getY()) {
                return;
            }
            scanComplete = true;
        }

        private boolean harvest(ServerPlayer player, ItemStack heldStack) {
            if (targets.isEmpty()) {
                return true;
            }
            int budget = harvestBudget(targets.size() - targetIndex);
            while (budget-- > 0 && targetIndex < targets.size()) {
                BlockPos pos = targets.get(targetIndex++);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!isEligible(heldStack, state)) {
                    continue;
                }
                HARVESTING.add(playerId);
                boolean destroyed;
                try {
                    destroyed = player.gameMode.destroyBlock(pos);
                } finally {
                    HARVESTING.remove(playerId);
                }
                if (destroyed) {
                    harvested++;
                }
                if (!validPlayerState(level.getServer(), player)) {
                    return true;
                }
                heldStack = player.getMainHandItem();
            }
            if (targetIndex < targets.size()) {
                return false;
            }
            if (targets.size() == candidates.size() && harvested == targets.size()) {
                HammerSelectionService.clearSelection(player.getMainHandItem());
            }
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.excavation.session.complete", harvested
            ));
            return true;
        }

        private boolean isEligible(ItemStack stack, BlockState state) {
            return !state.isAir()
                    && state.is(ExcavationTags.HAMMER_MINEABLE)
                    && hammer.isCorrectToolForDrops(stack, state);
        }
    }
}
