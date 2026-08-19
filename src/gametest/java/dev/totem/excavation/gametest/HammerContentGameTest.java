package dev.totem.excavation.gametest;

import dev.totem.excavation.ExcavationTags;
import dev.totem.excavation.HammerTier;
import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.item.HammerItem;
import dev.totem.excavation.registry.ExcavationItems;
import dev.totem.excavation.selection.HammerSelectionService;
import dev.totem.excavation.session.ExcavationSessions;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HammerContentGameTest {
    private static boolean protectionHookRegistered;
    private static volatile boolean emeraldOreBreakWasDenied;

    @GameTest(maxTicks = 40)
    public void sevenCanonicalItemsAreRegistered(GameTestHelper helper) {
        for (HammerTier tier : HammerTier.values()) {
            Item canonical = item("totem:excavation/" + tier.path() + "_hammer");
            require(helper, canonical instanceof HammerItem, "Canonical hammer missing for " + tier.path());
            require(helper, ((HammerItem) canonical).tier() == tier, "Canonical tier mismatch for " + tier.path());
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void recipesAndTagsUseCanonicalExcavationOwnership(GameTestHelper helper) {
        require(helper, Blocks.OAK_PLANKS.defaultBlockState().is(ExcavationTags.HAMMER_EFFICIENCY),
                "Planks are absent from hammer_efficiency");
        require(helper, Blocks.OAK_PLANKS.defaultBlockState().is(ExcavationTags.HAMMER_MINEABLE),
                "Planks are absent from hammer_mineable");
        require(helper, Blocks.STONE.defaultBlockState().is(ExcavationTags.HAMMER_MINEABLE),
                "Pickaxe blocks are absent from hammer_mineable");

        assertRecipe(helper, HammerTier.WOODEN, Items.OAK_PLANKS);
        assertRecipe(helper, HammerTier.STONE, Items.COBBLESTONE);
        assertRecipe(helper, HammerTier.COPPER, Items.COPPER_INGOT);
        assertRecipe(helper, HammerTier.IRON, Items.IRON_INGOT);
        assertRecipe(helper, HammerTier.GOLDEN, Items.GOLD_INGOT);
        assertRecipe(helper, HammerTier.DIAMOND, Items.DIAMOND);
        assertRecipe(helper, HammerTier.NETHERITE, Items.NETHERITE_INGOT);
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void transferredHammerHasExpectedToolPredicate(GameTestHelper helper) {
        for (HammerTier tier : HammerTier.values()) {
            ItemStack stack = new ItemStack(item("totem:excavation/" + tier.path() + "_hammer"));
            HammerItem hammer = (HammerItem) stack.getItem();
            require(helper, hammer.getDestroySpeed(stack, Blocks.OAK_PLANKS.defaultBlockState()) > 1.0F,
                    tier.path() + " hammer did not receive the transferred efficiency speed");
            require(helper, hammer.isCorrectToolForDrops(stack, Blocks.OAK_PLANKS.defaultBlockState()),
                    tier.path() + " hammer rejected a tagged wooden block");
            require(helper, hammer.getDestroySpeed(stack, Blocks.GLASS.defaultBlockState())
                            < hammer.getDestroySpeed(stack, Blocks.OAK_PLANKS.defaultBlockState()),
                    tier.path() + " hammer did not reduce speed for a non-tagged block");
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void selectionIsServerOwnedAndIsolatedToTheChosenStack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack selected = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        ItemStack other = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 2, 2);
        BlockPos first = helper.absolutePos(firstRelative);
        BlockPos second = helper.absolutePos(secondRelative);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);
        helper.setBlock(secondRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, selected);
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, selected, ExcavationItems.WOODEN_HAMMER,
                    level, first, true
            );
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, selected, ExcavationItems.WOODEN_HAMMER,
                    level, second, false
            );
            AreaSelection selection = selected.get(ExcavationDataComponents.AREA_SELECTION);
            require(helper, selection != null && selection.isComplete(),
                    "The server did not retain both selection corners on the held stack");
            require(helper, other.get(ExcavationDataComponents.AREA_SELECTION) == null,
                    "Selection leaked to a distinct hammer stack");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void selectingNewFirstCornerClearsThePreviousSecondCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 2, 2);
        BlockPos replacementFirstRelative = new BlockPos(3, 2, 3);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);
        helper.setBlock(secondRelative, Blocks.OAK_PLANKS);
        helper.setBlock(replacementFirstRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.WOODEN_HAMMER, firstRelative, secondRelative);
            HammerSelectionService.select(
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    ExcavationItems.WOODEN_HAMMER,
                    level,
                    helper.absolutePos(replacementFirstRelative),
                    true
            );

            AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
            require(helper, selection != null && !selection.isComplete(),
                    "Selecting a new first corner retained the stale second corner");
            require(helper, selection != null && selection.firstCorner().equals(helper.absolutePos(replacementFirstRelative)),
                    "Selecting a new first corner did not replace the previous first corner");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void twoPlayersKeepTheirHammerSelectionsIsolated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer firstPlayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer secondPlayer = helper.makeMockServerPlayerInLevel();
        ItemStack firstStack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        ItemStack secondStack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstPlayerCorner = new BlockPos(1, 2, 1);
        BlockPos secondPlayerCorner = new BlockPos(4, 2, 4);
        helper.setBlock(firstPlayerCorner, Blocks.OAK_PLANKS);
        helper.setBlock(secondPlayerCorner, Blocks.OAK_PLANKS);

        try {
            firstPlayer.setItemInHand(InteractionHand.MAIN_HAND, firstStack);
            secondPlayer.setItemInHand(InteractionHand.MAIN_HAND, secondStack);
            HammerSelectionService.select(
                    firstPlayer, InteractionHand.MAIN_HAND, firstStack, ExcavationItems.WOODEN_HAMMER,
                    level, helper.absolutePos(firstPlayerCorner), true
            );
            HammerSelectionService.select(
                    secondPlayer, InteractionHand.MAIN_HAND, secondStack, ExcavationItems.WOODEN_HAMMER,
                    level, helper.absolutePos(secondPlayerCorner), true
            );

            AreaSelection firstSelection = firstStack.get(ExcavationDataComponents.AREA_SELECTION);
            AreaSelection secondSelection = secondStack.get(ExcavationDataComponents.AREA_SELECTION);
            require(helper, firstSelection != null && secondSelection != null,
                    "A player selection was not stored on its held hammer");
            require(helper, !firstSelection.equals(secondSelection),
                    "Two players received the same selection state");
            require(helper, firstSelection.firstCorner().equals(helper.absolutePos(firstPlayerCorner)),
                    "The first player's selection was overwritten");
            require(helper, secondSelection.firstCorner().equals(helper.absolutePos(secondPlayerCorner)),
                    "The second player's selection was overwritten");
            helper.succeed();
        } finally {
            firstPlayer.discard();
            secondPlayer.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void crossDimensionSecondCornerLeavesTheExistingSelectionUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstCorner = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos attemptedSecondCorner = helper.absolutePos(new BlockPos(2, 2, 2));
        AreaSelection netherSelection = AreaSelection.firstCorner(Level.NETHER, firstCorner);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.set(ExcavationDataComponents.AREA_SELECTION, netherSelection);
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, stack, ExcavationItems.WOODEN_HAMMER,
                    level, attemptedSecondCorner, false
            );

            require(helper, netherSelection.equals(stack.get(ExcavationDataComponents.AREA_SELECTION)),
                    "A cross-dimension second corner changed the selection");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void selectionComponentIsRetainedWhenTheInventoryStackIsCopied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, stack, ExcavationItems.WOODEN_HAMMER,
                    level, helper.absolutePos(firstRelative), true
            );

            ItemStack inventoryCopy = stack.copy();
            require(helper,
                    stack.get(ExcavationDataComponents.AREA_SELECTION)
                            .equals(inventoryCopy.get(ExcavationDataComponents.AREA_SELECTION)),
                    "The ItemStack copy lost the synchronised area selection component");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void oversizedSecondCornerLeavesTheFirstCornerUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos oversizedRelative = new BlockPos(3, 2, 1);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);
        helper.setBlock(oversizedRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, stack, ExcavationItems.WOODEN_HAMMER,
                    level, helper.absolutePos(firstRelative), true
            );
            HammerSelectionService.select(
                    player, InteractionHand.MAIN_HAND, stack, ExcavationItems.WOODEN_HAMMER,
                    level, helper.absolutePos(oversizedRelative), false
            );

            AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
            require(helper, selection != null && !selection.isComplete(),
                    "An oversized second corner completed the selection");
            require(helper, selection != null && selection.firstCorner().equals(helper.absolutePos(firstRelative)),
                    "An oversized second corner changed the first corner");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void efficiencyEnchantmentExtendsTheValidatedSelectionRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.WOODEN_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos extendedRangeRelative = new BlockPos(3, 2, 1);
        helper.setBlock(firstRelative, Blocks.OAK_PLANKS);
        helper.setBlock(extendedRangeRelative, Blocks.OAK_PLANKS);

        try {
            stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.EFFICIENCY), 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.WOODEN_HAMMER, firstRelative, extendedRangeRelative);

            AreaSelection selection = stack.get(ExcavationDataComponents.AREA_SELECTION);
            require(helper, selection != null && selection.isComplete(),
                    "Efficiency did not extend the wooden hammer selection range");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 80)
    public void completedNetheriteSelectionMinesEveryEligibleLoadedTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 3, 2);
        fillPlanks(helper, firstRelative, secondRelative);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, firstRelative, secondRelative);
            helper.setBlock(firstRelative, Blocks.AIR); // The trigger was already manually mined.
            ExcavationSessions.startAfterManualBreak(
                    player, level, stack, helper.absolutePos(firstRelative)
            );

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, allAir(helper, firstRelative, secondRelative),
                            "A fully-complete netherite session left an eligible loaded target behind");
                    require(helper, stack.get(ExcavationDataComponents.AREA_SELECTION) == null,
                            "A fully exhausted selection did not clear both corners");
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

    @GameTest(maxTicks = 80)
    public void changingHeldStackCancelsAnExcavationSession(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(2, 3, 2);
        fillPlanks(helper, firstRelative, secondRelative);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, firstRelative, secondRelative);
            helper.setBlock(firstRelative, Blocks.AIR);
            ExcavationSessions.startAfterManualBreak(
                    player, level, stack, helper.absolutePos(firstRelative)
            );
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, helper.getLevel().getBlockState(helper.absolutePos(secondRelative)).is(Blocks.OAK_PLANKS),
                            "Changing the held stack did not cancel excavation before mining");
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

    @GameTest(maxTicks = 80)
    public void sessionSkipsIneligibleBlocksInsideTheSelectedBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos triggerRelative = new BlockPos(1, 2, 1);
        BlockPos ineligibleRelative = new BlockPos(2, 2, 1);
        BlockPos eligibleRelative = new BlockPos(3, 2, 1);
        helper.setBlock(triggerRelative, Blocks.OAK_PLANKS);
        helper.setBlock(ineligibleRelative, Blocks.OAK_LEAVES);
        helper.setBlock(eligibleRelative, Blocks.OAK_PLANKS);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, triggerRelative, eligibleRelative);
            helper.setBlock(triggerRelative, Blocks.AIR); // The triggering block was mined manually.
            ExcavationSessions.startAfterManualBreak(player, level, stack, helper.absolutePos(triggerRelative));

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, helper.getLevel().getBlockState(helper.absolutePos(ineligibleRelative)).is(Blocks.OAK_LEAVES),
                            "An ineligible block inside the selected box was mined");
                    require(helper, helper.getLevel().getBlockState(helper.absolutePos(eligibleRelative)).isAir(),
                            "An eligible block inside the selected box was not mined");
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

    @GameTest(maxTicks = 80)
    public void sessionDoesNotForceLoadAnUnloadedSelectedChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos triggerRelative = new BlockPos(1, 2, 1);
        BlockPos trigger = helper.absolutePos(triggerRelative);
        BlockPos unloadedCorner = trigger.offset(512, 0, 0);
        helper.setBlock(triggerRelative, Blocks.AIR);

        try {
            require(helper, !level.hasChunkAt(unloadedCorner),
                    "The GameTest setup unexpectedly loaded the remote selected chunk");
            stack.set(ExcavationDataComponents.AREA_SELECTION, new AreaSelection(
                    AreaSelection.CURRENT_DATA_VERSION,
                    level.dimension(),
                    trigger,
                    Optional.of(unloadedCorner)
            ));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ExcavationSessions.startAfterManualBreak(player, level, stack, trigger);

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, !level.hasChunkAt(unloadedCorner),
                            "Scanning an unloaded selected chunk forced it to load");
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

    @GameTest(maxTicks = 80)
    public void largeSessionDefersWorkPastTheSingleTickHarvestBudget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos secondRelative = new BlockPos(4, 4, 3);
        fillPlanks(helper, firstRelative, secondRelative);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, firstRelative, secondRelative);
            helper.setBlock(firstRelative, Blocks.AIR); // The manually-triggered block has already been harvested.
            ExcavationSessions.startAfterManualBreak(player, level, stack, helper.absolutePos(firstRelative));

            helper.runAtTickTime(1, () -> require(helper, !allAir(helper, firstRelative, secondRelative),
                    "A session mined more than the configured single-tick harvest budget"));
            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, allAir(helper, firstRelative, secondRelative),
                            "A bounded session did not continue its remaining work on later ticks");
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

    @GameTest(maxTicks = 80)
    public void protectionCancellationLeavesTargetAndSelectionIntact(GameTestHelper helper) {
        registerEmeraldOreProtectionHook();
        emeraldOreBreakWasDenied = false;

        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos firstRelative = new BlockPos(1, 2, 1);
        BlockPos protectedRelative = new BlockPos(2, 2, 1);
        helper.setBlock(firstRelative, Blocks.EMERALD_ORE);
        helper.setBlock(protectedRelative, Blocks.EMERALD_ORE);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, firstRelative, protectedRelative);
            helper.setBlock(firstRelative, Blocks.AIR); // The manually-triggered block has already been harvested.
            ExcavationSessions.startAfterManualBreak(player, level, stack, helper.absolutePos(firstRelative));

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, emeraldOreBreakWasDenied,
                            "The normal player break protection event was not invoked for excavation");
                    require(helper, helper.getLevel().getBlockState(helper.absolutePos(protectedRelative)).is(Blocks.EMERALD_ORE),
                            "A protection-cancelled target was still mined");
                    AreaSelection remaining = stack.get(ExcavationDataComponents.AREA_SELECTION);
                    require(helper, remaining != null && remaining.isComplete(),
                            "A protection-cancelled target incorrectly exhausted the selection");
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

    @GameTest(maxTicks = 80)
    public void normalExcavationHarvestUsesDiamondLootAndExperienceBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(ExcavationItems.NETHERITE_HAMMER);
        BlockPos triggerRelative = new BlockPos(1, 2, 1);
        BlockPos diamondRelative = new BlockPos(2, 2, 1);
        helper.setBlock(triggerRelative, Blocks.DIAMOND_ORE);
        helper.setBlock(diamondRelative, Blocks.DIAMOND_ORE);

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            selectArea(helper, player, stack, ExcavationItems.NETHERITE_HAMMER, triggerRelative, diamondRelative);
            BlockPos diamond = helper.absolutePos(diamondRelative);
            require(helper, level.getBlockState(diamond).getBlock() instanceof DropExperienceBlock,
                    "The diamond ore target did not use Minecraft's experience-bearing block path");
            require(helper, Block.getDrops(
                    level.getBlockState(diamond), level, diamond, null, player, player.getMainHandItem()
            ).stream().anyMatch(drop -> drop.is(Items.DIAMOND)),
                    "A correct hammer did not receive the normal diamond ore loot result");
            helper.setBlock(triggerRelative, Blocks.AIR); // The triggering block was mined manually.
            ExcavationSessions.startAfterManualBreak(player, level, stack, helper.absolutePos(triggerRelative));

            helper.runAtTickTime(20, () -> {
                try {
                    require(helper, helper.getLevel().getBlockState(diamond).isAir(),
                            "The eligible diamond ore target was not mined");
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

    private static synchronized void registerEmeraldOreProtectionHook() {
        if (protectionHookRegistered) {
            return;
        }
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (state.is(Blocks.EMERALD_ORE)) {
                emeraldOreBreakWasDenied = true;
                return false;
            }
            return true;
        });
        protectionHookRegistered = true;
    }

    private static void assertRecipe(GameTestHelper helper, HammerTier tier, Item material) {
        CraftingInput input = hammerInput(material);
        Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        require(helper, recipe.isPresent(), "No recipe matched " + tier.path() + " hammer inputs");
        ItemStack result = recipe.orElseThrow().value().assemble(input);
        Item canonical = item("totem:excavation/" + tier.path() + "_hammer");
        require(helper, result.is(canonical) && result.getCount() == 1,
                "Recipe did not produce canonical " + tier.path() + " hammer");
    }

    private static CraftingInput hammerInput(Item material) {
        List<ItemStack> slots = new ArrayList<>(List.of(
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        ));
        slots.set(0, new ItemStack(material));
        slots.set(1, new ItemStack(material));
        slots.set(2, new ItemStack(material));
        slots.set(3, new ItemStack(material));
        slots.set(4, new ItemStack(Items.STICK));
        slots.set(5, new ItemStack(material));
        slots.set(7, new ItemStack(Items.STICK));
        return CraftingInput.of(3, 3, slots);
    }

    private static void selectArea(
            GameTestHelper helper,
            ServerPlayer player,
            ItemStack stack,
            HammerItem hammer,
            BlockPos firstRelative,
            BlockPos secondRelative
    ) {
        ServerLevel level = helper.getLevel();
        HammerSelectionService.select(
                player, InteractionHand.MAIN_HAND, stack, hammer,
                level, helper.absolutePos(firstRelative), true
        );
        HammerSelectionService.select(
                player, InteractionHand.MAIN_HAND, stack, hammer,
                level, helper.absolutePos(secondRelative), false
        );
    }

    private static void fillPlanks(GameTestHelper helper, BlockPos first, BlockPos second) {
        for (BlockPos position : BlockPos.betweenClosed(first, second)) {
            helper.setBlock(position, Blocks.OAK_PLANKS);
        }
    }

    private static boolean allAir(GameTestHelper helper, BlockPos first, BlockPos second) {
        for (BlockPos position : BlockPos.betweenClosed(first, second)) {
            if (!helper.getLevel().getBlockState(helper.absolutePos(position)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static Item item(String id) {
        Item value = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (value == null) {
            throw new IllegalStateException("Missing registered item " + id);
        }
        return value;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(message);
        }
    }
}
