package dev.totem.excavation.gametest;

import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.session.ExcavationSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Regression coverage for clearing both area-selection corners after a completed player excavation. */
public final class HammerSelectionResetGameTest {
    @GameTest(maxTicks = 80)
    public void completedAreaExcavationClearsBothSelectionCorners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack hammer = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 3, 2);
        BlockPos first = helper.absolutePos(firstRelative);
        BlockPos second = helper.absolutePos(secondRelative);

        for (int x = firstRelative.getX(); x <= secondRelative.getX(); x++) {
            for (int y = firstRelative.getY(); y <= secondRelative.getY(); y++) {
                for (int z = firstRelative.getZ(); z <= secondRelative.getZ(); z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
                }
            }
        }

        try {
            hammer.set(
                    ExcavationDataComponents.AREA_SELECTION,
                    AreaSelection.firstCorner(level.dimension(), first).withSecondCorner(second)
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
            helper.setBlock(firstRelative, Blocks.AIR); // Trigger block was already mined manually.
            ExcavationSessions.startAfterManualBreak(player, level, hammer, first);

            helper.runAtTickTime(20, () -> {
                try {
                    for (int x = firstRelative.getX(); x <= secondRelative.getX(); x++) {
                        for (int y = firstRelative.getY(); y <= secondRelative.getY(); y++) {
                            for (int z = firstRelative.getZ(); z <= secondRelative.getZ(); z++) {
                                require(helper, helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir(),
                                        "Completed excavation left a selected block behind");
                            }
                        }
                    }
                    require(helper, hammer.get(ExcavationDataComponents.AREA_SELECTION) == null,
                            "Completed excavation retained the first corner after clearing the second corner");
                    helper.succeed();
                } finally {
                    player.discard();
                }
            });
        } catch (RuntimeException exception) {
            player.discard();
            throw exception;
        }
    }

    @GameTest(maxTicks = 40)
    public void emptyCompletedSelectionAlsoClearsBothCorners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack hammer = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 2, 2));

        try {
            hammer.set(
                    ExcavationDataComponents.AREA_SELECTION,
                    AreaSelection.firstCorner(level.dimension(), first).withSecondCorner(second)
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
            ExcavationSessions.startAfterManualBreak(player, level, hammer, first);

            helper.runAtTickTime(10, () -> {
                try {
                    require(helper, hammer.get(ExcavationDataComponents.AREA_SELECTION) == null,
                            "Empty completed excavation retained an area-selection corner");
                    helper.succeed();
                } finally {
                    player.discard();
                }
            });
        } catch (RuntimeException exception) {
            player.discard();
            throw exception;
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
