package dev.totem.excavation.selection;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.item.HammerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative first/second-corner updates for a single held stack. */
public final class HammerSelectionService {
    private HammerSelectionService() {
    }

    public static InteractionResult select(
            ServerPlayer player,
            InteractionHand hand,
            ItemStack stack,
            HammerItem hammer,
            ServerLevel level,
            BlockPos target,
            boolean selectingFirst
    ) {
        if (!level.getBlockState(target).is(ExcavationTags.HAMMER_MINEABLE)) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.ineligible"));
            return InteractionResult.FAIL;
        }
        if (selectingFirst) {
            stack.set(ExcavationDataComponents.AREA_SELECTION, AreaSelection.firstCorner(level.dimension(), target));
            player.sendOverlayMessage(Component.translatable(
                    "message.totem.excavation.selection.first", target.getX(), target.getY(), target.getZ()
            ));
            return InteractionResult.SUCCESS;
        }

        AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
        if (selection == null) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.need_first"));
            return InteractionResult.FAIL;
        }
        if (!selection.dimension().equals(level.dimension())) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.dimension"));
            return InteractionResult.FAIL;
        }
        int range = hammer.tier().maxRange(level, stack);
        if (!withinRange(selection.firstCorner(), target, range)) {
            player.sendOverlayMessage(Component.translatable("message.totem.excavation.selection.range", range));
            return InteractionResult.FAIL;
        }

        stack.set(ExcavationDataComponents.AREA_SELECTION, selection.withSecondCorner(target));
        player.sendOverlayMessage(Component.translatable(
                "message.totem.excavation.selection.second", target.getX(), target.getY(), target.getZ()
        ));
        return InteractionResult.SUCCESS;
    }

    public static void clearSecondCorner(ItemStack stack) {
        AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
        if (selection != null && selection.isComplete()) {
            stack.set(ExcavationDataComponents.AREA_SELECTION, selection.withoutSecondCorner());
        }
    }

    private static boolean withinRange(BlockPos first, BlockPos second, int range) {
        return Math.abs(first.getX() - second.getX()) + 1 <= range
                && Math.abs(first.getY() - second.getY()) + 1 <= range
                && Math.abs(first.getZ() - second.getZ()) + 1 <= range;
    }
}
