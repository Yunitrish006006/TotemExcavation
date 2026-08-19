package dev.totem.excavation.gametest;

import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.item.HammerItem;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.selection.HammerSelectionService;
import dev.totem.excavation.session.ExcavationSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Verifies that automatic area harvesting follows normal tool durability semantics. */
public final class HammerDurabilityGameTest {
    @GameTest(maxTicks = 80)
    public void automaticHarvestDamagesTheHeldHammer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack hammerStack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        HammerItem hammer = ExcavationItems.NETHERITE_HAMMER;
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 2, 1);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);
        helper.setBlock(secondRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, hammerStack);
            HammerSelectionService.select(player, InteractionHand.MAIN_HAND, hammerStack, hammer,
                    level, helper.absolutePos(firstRelative), true);
            HammerSelectionService.select(player, InteractionHand.MAIN_HAND, hammerStack, hammer,
                    level, helper.absolutePos(secondRelative), false);
            require(helper, hammerStack.get(ExcavationDataComponents.AREA_SELECTION) != null,
                    "Hammer selection was not stored before harvesting");

            int initialDamage = hammerStack.getDamageValue();
            helper.setBlock(firstRelative, Blocks.AIR);
            ExcavationSessions.startAfterManualBreak(player, level, hammerStack, helper.absolutePos(firstRelative));

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, level.getBlockState(helper.absolutePos(secondRelative)).isAir(),
                            "Automatic excavation did not harvest the eligible target");
                    require(helper, hammerStack.getDamageValue() > initialDamage,
                            "Automatic excavation bypassed normal hammer durability damage");
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
