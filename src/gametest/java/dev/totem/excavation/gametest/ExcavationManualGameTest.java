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
    public void realCraftingTableInteractionCreatesAndRefreshesOnlyExcavationGuide(
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
            ItemStack target = takeGuide(player, SECTION_ID);
            require(helper, target != null && exactSections(target, SECTION_ID),
                    "The actual block interaction did not insert a target-only Excavation guide");
            require(helper, advancementDone(player),
                    "Successful guide acquisition did not award deadrecall:excavation_manual");

            player.setItemInHand(InteractionHand.OFF_HAND, target);
            int before = countGuides(player, SECTION_ID);
            require(helper, before == 1,
                    "Moving the target guide to the offhand unexpectedly duplicated it");
            InteractionResult second = useTable(helper, player, InteractionHand.MAIN_HAND);
            require(helper, second.consumesAction(),
                    "Refreshing an offhand target guide was not handled");
            require(helper, player.getMainHandItem().is(Items.BOOK)
                            && player.getMainHandItem().getCount() == 1,
                    "Refreshing an existing target guide consumed the remaining plain book");
            require(helper, countGuides(player, SECTION_ID) == 1,
                    "Refreshing an existing offhand target guide created a duplicate");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @SuppressWarnings("removal")
    @GameTest(maxTicks = 40)
    public void basicGuideIsKeptAndFullInventoryDropsOneRecoverableTargetGuide(
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
                            && exactSections(basic, TotemManualOnboarding.SECTION_ID),
                    "The basic guide was consumed, replaced, or merged with Excavation");
            require(helper, countGuides(player, SECTION_ID) == 0,
                    "The supposedly full inventory accepted the separate Excavation guide");

            // ServerPlayer.drop queues the entity during this tick. Wait until
            // the subsequent entity tick has made it visible to world queries.
            helper.runAfterDelay(2, () -> {
                try {
                    List<ItemEntity> drops = nearbyDrops(player);
                    require(helper, drops.size() == 1
                                    && exactSections(drops.getFirst().getItem(), SECTION_ID),
                            "A full inventory did not produce exactly one recoverable target-only guide");
                    require(helper, advancementDone(player),
                            "Safely dropped guide acquisition did not award the module advancement");

                    ItemStack target = drops.getFirst().getItem().copy();
                    drops.getFirst().discard();
                    player.setItemInHand(InteractionHand.OFF_HAND, target);
                    useTable(helper, player, InteractionHand.MAIN_HAND);

                    // A duplicate drop would pass through the same deferred path.
                    helper.runAfterDelay(2, () -> {
                        try {
                            require(helper, nearbyDrops(player).isEmpty(),
                                    "Refreshing the offhand target guide dropped a duplicate into the world");
                            require(helper,
                                    exactSections(player.getOffhandItem(), SECTION_ID)
                                            && exactSections(player.getMainHandItem(),
                                            TotemManualOnboarding.SECTION_ID),
                                    "Refreshing through a basic reference changed either guide's chapter scope");
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

    private static ItemStack takeGuide(ServerPlayer player, Identifier sectionId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (exactSections(stack, sectionId)) {
                return player.getInventory().removeItemNoUpdate(slot);
            }
        }
        return null;
    }

    private static int countGuides(ServerPlayer player, Identifier sectionId) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (exactSections(player.getInventory().getItem(slot), sectionId)) total++;
        }
        return total;
    }

    private static boolean exactSections(ItemStack stack, Identifier sectionId) {
        return TotemManualAssembler.isCanonical(stack)
                && TotemManualAssembler.sections(stack).stream()
                .map(section -> section.id()).toList().equals(List.of(sectionId));
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
