package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.math.BigInteger;
import java.util.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.*;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureBalancingGameTests {
    @GameTest(template = "empty")
    public static void autoBalanceIsAtomicReplaySafeAndSharedByAllViewers(GameTestHelper h) {
        var storage = split(h).insertInput(new ItemStack(Items.IRON_ORE, 17), h.getLevel().registryAccess()).storage();
        var fixture = MiniatureResourceEditingGameTests.fixture(h, storage);
        var viewer = MiniatureResourceEditingGameTests.playerFixture(h, fixture.tile());
        var stale = command(viewer, 1);
        fixture.menu().setCarried(new ItemStack(Items.DIAMOND, 3));
        var request = command(fixture, 1);
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            MiniatureFactoryCommandPayload.STREAM_CODEC.encode(wire, request);
            h.assertTrue(request.equals(MiniatureFactoryCommandPayload.STREAM_CODEC.decode(wire)), "Balance command changed in transit");
        } finally { wire.release(); }
        var response = fixture.menu().handleCommand(fixture.player(), request);
        var after = fixture.tile().factoryState();
        var balanced = after.storage().orElseThrow();
        h.assertTrue(response.result() == SUCCESS && response.snapshot().compilation().status() == CompilationPreview.Status.READY
                        && balanced.layout().revision() == storage.layout().revision() + 1
                        && ((FactoryCell.Marker) balanced.layout().cellAt(pos(0, 1))).resource().amount().equals(BigInteger.valueOf(4))
                        && balanced.layout().cellAt(pos(2, 1)).equals(storage.layout().cellAt(pos(2, 1)))
                        && balanced.machines().entries().equals(storage.machines().entries()) && balanced.inputs().equals(storage.inputs())
                        && balanced.outputs().equals(storage.outputs()) && fixture.menu().getCarried().is(Items.DIAMOND)
                        && fixture.menu().getCarried().getCount() == 3,
                "Automatic balance changed assets, ratios, revision semantics, or failed to publish compilation");
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), request).result() == SUCCESS && fixture.tile().factoryState() == after,
                "Replaying balance applied it twice");
        h.assertTrue(viewer.menu().handleCommand(viewer.player(), stale).result() == STALE_REVISION && fixture.tile().factoryState() == after,
                "Old viewer overwrote the balanced layout");
        viewer.menu().broadcastChanges();
        h.assertTrue(viewer.menu().snapshot().compilation().equals(response.snapshot().compilation()), "Other viewer did not receive compiled balance");
        var repeated = fixture.menu().handleCommand(fixture.player(), command(fixture, 2));
        h.assertTrue(repeated.result() == SUCCESS && fixture.tile().factoryState() == after, "Already balanced layout was rewritten");
        var restored = FactoryState.load(after.save(), h.getLevel().registryAccess());
        h.assertTrue(restored.storage().orElseThrow().layout().equals(balanced.layout())
                && MiniatureRecipeCatalog.get(h.getLevel().getServer()).compile(restored.storage().orElseThrow().layout(), h.getLevel()).successful(),
                "Saved inferred recipe or marker amounts did not recompile");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedBalancePublishesDiagnosticWithoutEditingAnyState(GameTestHelper h) {
        var fixture = MiniatureResourceEditingGameTests.fixture(h, split(h).withConfiguration(pos(2, 0), FactoryCell.Empty.INSTANCE));
        var before = fixture.tile().factoryState();
        var response = fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
        h.assertTrue(response.result() == SUCCESS && fixture.tile().factoryState() == before
                        && response.snapshot().compilation().status() == CompilationPreview.Status.FAILED
                        && response.snapshot().compilation().diagnostic().code() == FactoryDiagnostic.Code.ROUTE_DEAD_END,
                "Failed automatic balance changed configuration or lost its route diagnostic");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void balanceRejectsStaleRecipeViewsAndLockedBatches(GameTestHelper h) {
        var storage = split(h);
        var fixture = MiniatureResourceEditingGameTests.fixture(h, storage);
        var old = command(fixture, 1); var before = fixture.tile().factoryState();
        MiniatureRecipeCatalog.get(h.getLevel().getServer()).invalidate();
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), old).result() == STALE_REVISION && before == fixture.tile().factoryState(),
                "Balance used a stale recipe generation");
        var batch = new RunningBatch(UUID.randomUUID(), "a".repeat(64), new FactoryRules.Environment(10, 1), 1,
                new ResourceInventory(Map.of(key(h, Items.IRON_ORE), BigInteger.ONE)),
                new ResourceInventory(Map.of(key(h, Items.IRON_INGOT), BigInteger.ONE)), FactoryRules.energyPerTick(2), 1);
        var locked = FactoryState.of(storage.withBatch(batch, 100), h.getLevel().registryAccess());
        h.assertTrue(fixture.tile().applyPreparedState(before, locked) == false, "Generic edit unexpectedly replaced runtime reservation");
        // Restore through ordinary block entity NBT loading, as after a server restart.
        var wrapper = new net.minecraft.nbt.CompoundTag();
        wrapper.put(com.aquariverr.fluxod.block.entity.TileMiniatureUniverse.STATE_TAG, locked.save());
        fixture.tile().loadAdditional(wrapper, h.getLevel().registryAccess());
        fixture.menu().broadcastChanges(); before = fixture.tile().factoryState();
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 2)).result() == BATCH_LOCKED
                && before == fixture.tile().factoryState(), "Balance edited an active batch");
        h.succeed();
    }

    private static FactoryStorage split(GameTestHelper h) {
        return FactoryStorage.empty().insertMachine(pos(1, 1), new ItemStack(Items.FURNACE, 2), h.getLevel().registryAccess()).storage()
                .withConfiguration(pos(0, 1), new FactoryCell.Marker(new ResourceAmount(key(h, Items.IRON_ORE), BigInteger.valueOf(7)), RIGHT))
                .withConfiguration(pos(2, 1), new FactoryCell.Arrow(Map.of(RIGHT, 1, UP, 3)))
                .withConfiguration(pos(2, 0), new FactoryCell.Arrow());
    }
    private static GridPosition pos(int x, int y) { return new GridPosition(x, y); }
    private static ResourceKey key(GameTestHelper h, Item item) { return FactoryItemResources.keyOf(new ItemStack(item), h.getLevel().registryAccess()); }
    private static MiniatureFactoryCommandPayload command(MiniatureResourceEditingGameTests.Fixture f, long id) {
        return new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(), f.menu().revision(), id,
                MiniatureFactoryCommandPayload.Action.AUTO_BALANCE, 0, 0, BigInteger.ONE, RIGHT, f.menu().snapshotId(), -1);
    }
}
