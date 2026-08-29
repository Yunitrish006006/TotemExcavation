package dev.totem.excavation.selection;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.item.HammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Applies the two-click selection state machine to one server-owned hammer stack. */
public final class HammerSelectionService {
    private HammerSelectionService() {
    }

    public static SelectionAction select(
            ServerPlayer player,
            ItemStack stack,
            HammerItem hammer,
            ServerLevel level,
            BlockPos target
    ) {
        if (!level.getBlockState(target).is(ExcavationTags.HAMMER_MINEABLE)) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.ineligible"));
            return SelectionAction.REJECTED;
        }

        AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
        if (selection == null) {
            stack.set(ExcavationDataComponents.AREA_SELECTION, AreaSelection.firstCorner(level.dimension(), target));
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.excavation.selection.first", target.getX(), target.getY(), target.getZ()
            ));
            return SelectionAction.FIRST_SET;
        }

        if (!selection.dimension().equals(level.dimension())) {
            return restart(player, stack, level, target);
        }

        BlockPos second = selection.secondCorner().orElse(null);
        if (target.equals(selection.firstCorner()) || target.equals(second)) {
            clearSelection(stack);
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.cleared"));
            return SelectionAction.CLEARED;
        }

        if (second != null) {
            return restart(player, stack, level, target);
        }

        int range = hammer.tier().maxRange(level, stack);
        if (!withinRange(selection.firstCorner(), target, range)) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.range", range));
            return SelectionAction.REJECTED;
        }

        stack.set(ExcavationDataComponents.AREA_SELECTION, selection.withSecondCorner(target));
        player.sendOverlayMessage(Component.translatable(
                "message.totem.excavation.selection.second", target.getX(), target.getY(), target.getZ()
        ));
        return SelectionAction.SECOND_SET;
    }

    private static SelectionAction restart(
            ServerPlayer player,
            ItemStack stack,
            ServerLevel level,
            BlockPos target
    ) {
        stack.set(ExcavationDataComponents.AREA_SELECTION, AreaSelection.firstCorner(level.dimension(), target));
        player.sendOverlayMessage(Component.translatable(
                "message.totem.excavation.selection.restarted", target.getX(), target.getY(), target.getZ()
        ));
        return SelectionAction.RESTARTED;
    }

    public static void clearSecondCorner(ItemStack stack) {
        AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
        if (selection != null && selection.isComplete()) {
            stack.set(ExcavationDataComponents.AREA_SELECTION, selection.withoutSecondCorner());
        }
    }

    /** Clears both corners after a selection has been fully consumed by a completed excavation session. */
    public static void clearSelection(ItemStack stack) {
        stack.remove(ExcavationDataComponents.AREA_SELECTION);
    }

    static boolean withinRange(BlockPos first, BlockPos second, int range) {
        return Math.abs(first.getX() - second.getX()) + 1 <= range
                && Math.abs(first.getY() - second.getY()) + 1 <= range
                && Math.abs(first.getZ() - second.getZ()) + 1 <= range;
    }

    public enum SelectionAction {
        FIRST_SET,
        SECOND_SET,
        CLEARED,
        RESTARTED,
        REJECTED
    }
}
