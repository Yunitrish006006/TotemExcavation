package dev.totem.excavation.gametest;

import dev.totem.core.api.v1.manual.TotemManualAssembler;
import dev.totem.core.api.v1.manual.TotemManualOnboarding;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Exercises the published UseBlockCallback path instead of only inspecting registration. */
public final class ExcavationManualGameTest {
    private static final Identifier SECTION_ID =
            Identifier.fromNamespaceAndPath("totem", "excavation/manual");
    private static final Identifier ADVANCEMENT_ID =
            Identifier.fromNamespaceAndPath("deadrecall", "excavation_manual");
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void realCraftingTableInteractionCreatesAndRefreshesSharedExcavationManual(
            GameTestHelper helper
    ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 2));
            InteractionResult first = useTable(helper, player, InteractionHand.MAIN_HAND);
            require(helper, first.consumesAction(),
                    "A real plain-book crafting-table interaction was not handled");
            require(helper, player.getMainHandItem().is(Items.BOOK)
                            && player.getMainHandItem().getCount() == 1,
                    "Creating the Excavation guide did not consume exactly one plain book");
            ItemStack target = takeGuide(player, TotemManualOnboarding.SECTION_ID, SECTION_ID);
            require(helper, target != null && exactSections(
                            target, TotemManualOnboarding.SECTION_ID, SECTION_ID
                    ),
                    "The interaction did not create a shared manual with Core and Excavation chapters");
            require(helper, advancementDone(player),
                    "Successful guide acquisition did not award deadrecall:excavation_manual");

            player.setItemInHand(InteractionHand.OFF_HAND, target);
            int before = countGuides(player, TotemManualOnboarding.SECTION_ID, SECTION_ID);
            require(helper, before == 1,
                    "Moving the target guide to the offhand unexpectedly duplicated it");
            InteractionResult second = useTable(helper, player, InteractionHand.MAIN_HAND);
            require(helper, second.consumesAction(),
                    "Refreshing an offhand target guide was not handled");
            require(helper, player.getMainHandItem().is(Items.BOOK)
                            && player.getMainHandItem().getCount() == 1,
                    "Refreshing an existing target guide consumed the remaining plain book");
            require(helper, countGuides(
                            player, TotemManualOnboarding.SECTION_ID, SECTION_ID
                    ) == 1,
                    "Refreshing an existing shared manual created a duplicate");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void basicGuideIsUpdatedInPlaceWhenTheInventoryIsFull(
            GameTestHelper helper
    ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        BlockPos tablePos = helper.absolutePos(TABLE);
        player.snapTo(
                tablePos.getX() + 0.5D,
                tablePos.getY() + 1.0D,
                tablePos.getZ() + 0.5D,
                0.0F,
                0.0F
        );
        ItemStack basic = TotemManualAssembler.create(List.of(TotemManualOnboarding.SECTION));
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, basic);

        try {
            InteractionResult result = useTable(helper, player, InteractionHand.MAIN_HAND);
            require(helper, result.consumesAction(),
                    "A canonical basic-guide crafting-table interaction was not handled");
            require(helper, player.getMainHandItem() == basic
                            && exactSections(
                            basic, TotemManualOnboarding.SECTION_ID, SECTION_ID
                    ),
                    "The basic guide was not updated in place with the Excavation chapter");
            require(helper, advancementDone(player),
                    "Updating the shared guide did not award the module advancement");

            // ServerPlayer.drop queues the entity during this tick. Wait until
            // the subsequent entity tick before proving that no split guide was created.
            helper.runAfterDelay(2, () -> {
                try {
                    require(helper, nearbyDrops(player).isEmpty(),
                            "Updating the shared manual dropped an obsolete split guide");
                    useTable(helper, player, InteractionHand.MAIN_HAND);

                    helper.runAfterDelay(2, () -> {
                        try {
                            require(helper, nearbyDrops(player).isEmpty(),
                                    "Refreshing the shared manual dropped a duplicate");
                            require(helper, player.getMainHandItem() == basic
                                            && exactSections(
                                            basic, TotemManualOnboarding.SECTION_ID, SECTION_ID
                                    ),
                                    "Refreshing changed the shared manual's chapter scope or stack identity");
                            helper.succeed();
                        } finally {
                            player.discard();
                        }
                    });
                } catch (RuntimeException | Error failure) {
                    player.discard();
                    throw failure;
                }
            });
        } catch (RuntimeException | Error failure) {
            player.discard();
            throw failure;
        }
    }

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void unrelatedItemStillOpensTheVanillaCraftingMenu(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            InteractionResult result = useTable(helper, player, InteractionHand.MAIN_HAND);
            require(helper, result.consumesAction() && player.containerMenu instanceof CraftingMenu,
                    "An unrelated item no longer followed the normal Crafting Table interaction");
            require(helper, !advancementDone(player),
                    "An unrelated item incorrectly awarded the Excavation guide advancement");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    private static InteractionResult useTable(
            GameTestHelper helper,
            ServerPlayer player,
            InteractionHand hand
    ) {
        BlockPos absolute = helper.absolutePos(TABLE);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absolute),
                net.minecraft.core.Direction.UP,
                absolute,
                false
        );
        return player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                player.getItemInHand(hand),
                hand,
                hit
        );
    }

    private static ItemStack takeGuide(ServerPlayer player, Identifier... sectionIds) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (exactSections(stack, sectionIds)) {
                return player.getInventory().removeItemNoUpdate(slot);
            }
        }
        return null;
    }

    private static int countGuides(ServerPlayer player, Identifier... sectionIds) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (exactSections(player.getInventory().getItem(slot), sectionIds)) total++;
        }
        return total;
    }

    private static boolean exactSections(ItemStack stack, Identifier... sectionIds) {
        return TotemManualAssembler.isCanonical(stack)
                && TotemManualAssembler.sections(stack).stream()
                .map(section -> section.id()).toList().equals(List.of(sectionIds));
    }

    private static boolean advancementDone(ServerPlayer player) {
        var advancement = player.level().getServer().getAdvancements().get(ADVANCEMENT_ID);
        return advancement != null
                && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static List<ItemEntity> nearbyDrops(ServerPlayer player) {
        return player.level().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(player.blockPosition()).inflate(4.0D),
                ItemEntity::isAlive
        );
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
