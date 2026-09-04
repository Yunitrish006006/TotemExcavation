package dev.totem.excavation.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("removal")
public final class HoeHarvestGameTest {
    private static final BlockPos CROP = new BlockPos(2, 2, 2);
    private static final BlockPos SECOND_CROP = new BlockPos(4, 2, 2);
    private static final AtomicReference<DeniedTarget> DENIED_TARGET = new AtomicReference<>();
    private static boolean protectionHookRegistered;

    @GameTest(maxTicks = 40)
    public void matureWheatUsesItsLootTableAndIsReplanted(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            InteractionResult result = use(helper, player, CROP);
            require(helper, result.consumesAction(), "A mature wheat crop was not harvested");
            require(helper, helper.getBlockState(CROP).is(Blocks.WHEAT)
                            && helper.getBlockState(CROP).getValue(CropBlock.AGE) == 0,
                    "Harvested wheat was not replanted at age zero");
            require(helper, hoe.getDamageValue() == 1,
                    "Harvesting did not consume exactly one point of hoe durability");

            helper.runAfterDelay(2, () -> {
                try {
                    require(helper, countNearby(helper, CROP, Items.WHEAT) == 1,
                            "Harvesting mature wheat did not produce its crop loot");
                    int seeds = countNearby(helper, CROP, Items.WHEAT_SEEDS);
                    require(helper, seeds >= 0 && seeds <= 3,
                            "The replanted seed was not reserved from the wheat loot");
                    helper.succeed();
                } finally {
                    player.discard();
                }
            });
        } catch (RuntimeException | Error failure) {
            player.discard();
            throw failure;
        }
    }

    @GameTest(maxTicks = 40)
    public void immatureCropsAndNonHoesKeepTheVanillaInteractionPath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 6));

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            require(helper, use(helper, player, CROP) == InteractionResult.PASS,
                    "An immature crop consumed the hoe right-click");
            require(helper, helper.getBlockState(CROP).getValue(CropBlock.AGE) == 6,
                    "An immature crop was changed");
            require(helper, hoe.getDamageValue() == 0,
                    "An immature crop damaged the hoe");

            helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            require(helper, use(helper, player, CROP) == InteractionResult.PASS,
                    "A non-hoe consumed the mature crop interaction");
            require(helper, helper.getBlockState(CROP).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "A non-hoe harvested the mature crop");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void hoeAlsoHarvestsNetherWartAndCocoa(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
        helper.setBlock(CROP.below(), Blocks.SOUL_SAND);
        helper.setBlock(CROP, Blocks.NETHER_WART.defaultBlockState()
                .setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(SECOND_CROP.relative(direction), Blocks.JUNGLE_LOG);
        }
        helper.setBlock(SECOND_CROP, Blocks.COCOA.defaultBlockState()
                .setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            require(helper, use(helper, player, CROP).consumesAction(),
                    "Mature nether wart was not harvested");
            require(helper, use(helper, player, SECOND_CROP).consumesAction(),
                    "Mature cocoa was not harvested");
            require(helper, helper.getBlockState(CROP).getValue(NetherWartBlock.AGE) == 0,
                    "Nether wart was not replanted");
            require(helper, helper.getBlockState(SECOND_CROP).getValue(CocoaBlock.AGE) == 0,
                    "Cocoa was not replanted");
            require(helper, hoe.getDamageValue() == 2,
                    "Two harvests did not consume two points of hoe durability");

            helper.runAfterDelay(2, () -> {
                try {
                    int wart = countNearby(helper, CROP, Items.NETHER_WART);
                    require(helper, wart >= 1 && wart <= 3,
                            "Nether wart loot did not reserve one item for replanting");
                    require(helper, countNearby(helper, SECOND_CROP, Items.COCOA_BEANS) == 2,
                            "Cocoa loot did not reserve one bean for replanting");
                    helper.succeed();
                } finally {
                    player.discard();
                }
            });
        } catch (RuntimeException | Error failure) {
            player.discard();
            throw failure;
        }
    }

    @GameTest(maxTicks = 40)
    public void deniedUseCallbackPreventsHarvestAndDurabilityLoss(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
        registerProtectionHook();
        DENIED_TARGET.set(new DeniedTarget(helper.getLevel(), helper.absolutePos(CROP)));

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            require(helper, use(helper, player, CROP) == InteractionResult.FAIL,
                    "A denied crop interaction did not stop before hoe harvesting");
            require(helper, helper.getBlockState(CROP).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "A denied crop interaction still changed the crop");
            require(helper, hoe.getDamageValue() == 0,
                    "A denied crop interaction still damaged the hoe");
            helper.succeed();
        } finally {
            DENIED_TARGET.set(null);
            player.discard();
        }
    }

    private static synchronized void registerProtectionHook() {
        if (protectionHookRegistered) return;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            DeniedTarget denied = DENIED_TARGET.get();
            return denied != null
                    && denied.level() == level
                    && denied.pos().equals(hit.getBlockPos())
                    ? InteractionResult.FAIL
                    : InteractionResult.PASS;
        });
        protectionHookRegistered = true;
    }

    private static InteractionResult use(
            GameTestHelper helper,
            ServerPlayer player,
            BlockPos relativePos
    ) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(absolutePos),
                Direction.UP,
                absolutePos,
                false
        );
        return player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                hit
        );
    }

    private static int countNearby(GameTestHelper helper, BlockPos relativePos, Item item) {
        BlockPos absolutePos = helper.absolutePos(relativePos);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(absolutePos).inflate(2.0D),
                ItemEntity::isAlive
        );
        return drops.stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    private record DeniedTarget(net.minecraft.world.level.Level level, BlockPos pos) {
    }
}
