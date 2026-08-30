package dev.totem.excavation.client;

import dev.totem.excavation.component.AreaSelection;
import dev.totem.excavation.component.ExcavationDataComponents;
import dev.totem.excavation.registry.ExcavationItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Survival regression for one request per press and full held-attack crack suppression. */
@SuppressWarnings("UnstableApiUsage")
public final class HammerSelectionHoldClientGameTest implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(0, 81, -3);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            singleplayer.getServer().runCommand("gamemode survival @a");
            singleplayer.getServer().runCommand("fill -2 79 -2 2 79 2 minecraft:stone");
            singleplayer.getServer().runCommand("tp @a 0 80 0");
            singleplayer.getServer().runCommand("setblock 0 81 -3 minecraft:oak_planks");
            singleplayer.getServer().runCommand(
                    "item replace entity @a weapon.mainhand with totem:excavation/wooden_hammer"
            );

            context.waitFor(client -> client.player != null
                    && client.player.getMainHandItem().is(ExcavationItems.WOODEN_HAMMER));
            context.getInput().lookAt(TARGET);
            context.waitTicks(3);
            context.getInput().holdShift();
            context.waitTicks(3);
            context.waitFor(client -> client.player != null && client.player.isShiftKeyDown());

            int initialDamage = context.computeOnClient(
                    client -> client.player.getMainHandItem().getDamageValue()
            );
            context.getInput().holdMouse(0);
            try {
                for (int tick = 0; tick < 20; tick++) {
                    context.waitTick();
                    int observedTick = tick;
                    context.runOnClient(client -> {
                        require(client.gameMode != null && !client.gameMode.isDestroying(),
                                "Held selection entered vanilla destroy progress on tick " + observedTick);
                        require(client.level.getBlockState(TARGET).is(Blocks.OAK_PLANKS),
                                "Held selection destroyed the target on tick " + observedTick);
                        require(client.player.getMainHandItem().getDamageValue() == initialDamage,
                                "Held selection damaged the hammer on tick " + observedTick);
                    });
                }
            } finally {
                context.getInput().releaseMouse(0);
            }

            context.waitFor(client -> {
                AreaSelection selection = client.player.getMainHandItem().get(
                        ExcavationDataComponents.AREA_SELECTION
                );
                return selection != null
                        && !selection.isComplete()
                        && selection.firstCorner().equals(TARGET);
            });

            captureDepthTestedOutline(context, singleplayer);

            // A second physical press on the same block is one new request and clears A.
            context.getInput().pressMouse(0);
            context.waitFor(client -> client.player.getMainHandItem().get(
                    ExcavationDataComponents.AREA_SELECTION
            ) == null);
            context.runOnClient(client -> {
                ItemStack stack = client.player.getMainHandItem();
                require(client.gameMode != null && !client.gameMode.isDestroying(),
                        "Second selection press left destroy progress active");
                require(client.level.getBlockState(TARGET).is(Blocks.OAK_PLANKS),
                        "Second selection press destroyed the target");
                require(stack.getDamageValue() == initialDamage,
                        "Second selection press damaged the hammer");
            });
            context.getInput().releaseShift();
        } finally {
            // Input cleanup is safe even when setup/assertions fail halfway through.
            context.getInput().releaseMouse(0);
            context.getInput().releaseShift();
        }
    }

    private static void captureDepthTestedOutline(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer) {
        context.getInput().releaseMouse(0);
        context.getInput().releaseShift();
        context.getInput().lookAt(TARGET);
        context.waitTicks(2);
        context.getInput().holdShift();

        singleplayer.getServer().runCommand("fill -1 80 -2 1 83 -2 minecraft:stone_bricks");
        context.waitFor(client -> client.level.getBlockState(new BlockPos(0, 81, -2)).is(Blocks.STONE_BRICKS));

        AABB partlyOccludedSelection = AABB.encapsulatingFullBlocks(
                new BlockPos(-2, 80, -3),
                new BlockPos(2, 82, -3)
        ).inflate(0.002D);
        // Fabric's screenshot helper bypasses the outer frame collector used by
        // BEFORE_GIZMOS, so submit the production outline into that same collector.
        context.runOnClient(client -> {
            try (var ignored = client.levelRenderer.collectPerFrameRenderThreadGizmos()) {
                ExcavationOutlineRenderer.addSelectionOutline(partlyOccludedSelection);
            }
        });
        context.takeScreenshot("totem-excavation-selection-depth-tested");

        singleplayer.getServer().runCommand("fill -1 80 -2 1 83 -2 minecraft:air");
        context.waitFor(client -> client.level.getBlockState(new BlockPos(0, 81, -2)).isAir());
        context.getInput().lookAt(TARGET);
        context.waitTicks(2);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
