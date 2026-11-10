package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureLifecycleGameTests {
    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 1200)
    public static void mekanismStructuresFinishChunkWorkBeforeShutdown(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer() instanceof GameTestServer,
                "Lifecycle ticket cleanup is only allowed in the standalone GameTestServer");
        reportSuccessWithoutWorldMutation(helper);
        int[] stableTicks = {0};
        // Release the finished fixtures while ordinary ticks can still run generation callbacks.
        // GameTestRunner otherwise releases forced tickets only after this final test succeeds,
        // immediately before stopServer drains its unload queue with an unlimited time budget.
        helper.startSequence().thenIdle(60)
                .thenWaitUntil(() -> helper.assertTrue(otherTestsFinished(helper), "Waiting for all other tests to finish"))
                .thenExecute(() -> {
                    for (var level : helper.getLevel().getServer().getAllLevels()) {
                        for (long chunk : level.getForcedChunks().toLongArray()) {
                            level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
                        }
                        level.getChunkSource().removeTicketsOnClosing();
                    }
                })
                .thenWaitUntil(() -> {
                    // Keep no report-area ticket alive: the success listener below never touches
                    // blocks, so the complete arena can unload while ordinary ticks still run.
                    stableTicks[0] = chunksDrainedForShutdown(helper) ? stableTicks[0] + 1 : 0;
                    helper.assertTrue(stableTicks[0] >= 20,
                            "Waiting for released test chunks to finish ordinary saving and unloading");
                })
                .thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static void reportSuccessWithoutWorldMutation(GameTestHelper helper) {
        helper.assertTrue(!helper.testInfo.isFlaky() && !helper.testInfo.retryOptions().hasRetries(),
                "Terminal lifecycle reporting requires a single-attempt test");
        try {
            var field = GameTestInfo.class.getDeclaredField("listeners");
            field.setAccessible(true);
            var listeners = (Collection<GameTestListener>) field.get(helper.testInfo);
            var replacements = new ArrayList<GameTestListener>();
            int replaced = 0;
            for (GameTestListener listener : listeners) {
                if (!listener.getClass().getName().equals("net.minecraft.gametest.framework.ReportGameListener")) {
                    replacements.add(listener);
                    continue;
                }
                replaced++;
                replacements.add(new GameTestListener() {
                    @Override public void testStructureLoaded(GameTestInfo test) { listener.testStructureLoaded(test); }
                    @Override public void testPassed(GameTestInfo test, GameTestRunner runner) {
                        // Vanilla changes the beacon glass AFTER the sequence's final readiness check.
                        // GameTestServer halts in the same tick, before that new lighting/chunk work
                        // can drain. Preserve the real reporter and all tracker/batch listeners; only
                        // this standalone cleanup fixture omits its cosmetic success block update.
                        GlobalTestReporter.onTestSuccess(test);
                    }
                    @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { listener.testFailed(test, runner); }
                    @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                        listener.testAddedForRerun(oldTest, newTest, runner);
                    }
                });
            }
            helper.assertTrue(replaced == 1, "Expected one vanilla terminal test reporter");
            listeners.clear();
            listeners.addAll(replacements);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot isolate standalone lifecycle success reporting", exception);
        }
    }

    private static boolean otherTestsFinished(GameTestHelper helper) {
        try {
            var field = GameTestTicker.class.getDeclaredField("testInfos");
            field.setAccessible(true);
            for (Object value : (Collection<?>) field.get(GameTestTicker.SINGLETON)) {
                GameTestInfo test = (GameTestInfo) value;
                if (test != helper.testInfo && !test.isDone()) return false;
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect remaining GameTests", exception);
        }
    }

    private static boolean chunksDrainedForShutdown(GameTestHelper helper) {
        if (!chunksReadyForShutdown(helper)) return false;
        for (var level : helper.getLevel().getServer().getAllLevels()) {
            // Includes every live/pending chunk, unload/generation/lighting/POI task, and ticket.
            // Matching the real stopServer loop prevents a partial-readiness false positive.
            if (level.getChunkSource().chunkMap.hasWork()) return false;
        }
        return true;
    }

    static boolean chunksReadyForShutdown(GameTestHelper helper) {
        // GameTestServer can stop faster than world generation workers drain. Inspect only readiness;
        // ordinary server ticks and shutdown remain responsible for completing and saving the work.
        try {
            for (var level : helper.getLevel().getServer().getAllLevels()) {
                var chunkMap = level.getChunkSource().chunkMap;
                for (String name : List.of("updatingChunkMap", "pendingUnloads")) {
                    var field = ChunkMap.class.getDeclaredField(name);
                    field.setAccessible(true);
                    var holders = (Map<?, ?>) field.get(chunkMap);
                    for (Object holder : holders.values()) {
                        if (!((ChunkHolder) holder).isReadyForSaving()) return false;
                    }
                }
                if (level.getChunkSource().getPendingTasksCount() != 0) return false;
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect GameTest chunk readiness", exception);
        }
    }

    private MiniatureLifecycleGameTests() {}
}
