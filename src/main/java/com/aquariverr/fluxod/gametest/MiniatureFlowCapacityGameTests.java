package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.*;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.math.BigInteger;
import java.util.Map;
import static com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureFlowCapacityGameTests {
    @GameTest(template = "empty")
    public static void branchCommandsRotateAndPersistAllRatiosThroughNetwork(GameTestHelper h) {
        var p = new GridPosition(1, 1);
        var fixture = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withConfiguration(p, new FactoryCell.Arrow()));
        long commandId = 1;
        for (var action : new Object[][]{{SET_ARROW_WEIGHT, UP, 3}, {SET_ARROW_WEIGHT, RIGHT, 1}, {ROTATE_DIRECTION, RIGHT, 1},
                {SET_ARROW_WEIGHT, DOWN, 0}}) {
            var command = new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                    fixture.menu().revision(), commandId++, (MiniatureFactoryCommandPayload.Action) action[0],
                    1, 1, BigInteger.valueOf((int) action[2]), (GridDirection) action[1]);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            try {
                MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer, command);
                var decoded = MiniatureFactoryCommandPayload.STREAM_CODEC.decode(buffer);
                h.assertTrue(command.equals(decoded), "Weight command changed in transit");
                var response = fixture.menu().handleCommand(fixture.player(), decoded);
                h.assertTrue(response.result() == MiniatureFactorySnapshotPayload.Result.SUCCESS, "Valid weight command rejected");
                var runtime = new RuntimePreview(true, RedstoneMode.IGNORE, FactoryRunner.Status.RUNNING, 2, 1, 299,
                        FactoryRules.energyPerTick(1), 400);
                response = new MiniatureFactorySnapshotPayload(response.containerId(), response.sessionId(), response.targetPos(),
                        response.revision(), response.acknowledgedCommandId(), response.acknowledgedRevision(), response.result(),
                        response.snapshot().withRuntime(runtime), response.errorContext(), response.snapshotId());
                buffer.clear();
                MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, response);
                h.assertTrue(response.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)), "Snapshot lost weights or progress above 255");
            } finally { buffer.release(); }
        }
        var storage = fixture.tile().factoryState().storage().orElseThrow();
        h.assertTrue(((FactoryCell.Arrow) storage.layout().cellAt(p)).weights().equals(Map.of(RIGHT, 3)), "Rotation or zero disabling changed ratios");
        var restored = FactoryStateCodec.load(FactoryStateCodec.save(storage, h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(restored.layout().equals(storage.layout()), "Ratios did not survive persistence");
        var invalid = new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), commandId, SET_ARROW_WEIGHT, 1, 1, BigInteger.ZERO, RIGHT);
        var before = fixture.tile().factoryState();
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), invalid).result() == MiniatureFactorySnapshotPayload.Result.INVALID_QUANTITY
                && before == fixture.tile().factoryState(), "Disabling the last branch changed state");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedBatchPausesReloadsAndCommitsOnlyAfterAllRounds(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        var source = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), registries).storage()
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.valueOf(8)), RIGHT))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow())
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(8)))).withControls(true, RedstoneMode.IGNORE);
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        var compiled = catalog.compile(source.layout(), h.getLevel()).factory();
        h.assertTrue(compiled != null && compiled.requiredTicks(1, 2) == 80, "Eight operations on one furnace should take four rounds at P=2");
        var environment = new FactoryRules.Environment(10, 1);
        var state = source;
        BigInteger charge = BigInteger.ZERO;
        for (int paid = 1; paid <= 80; paid++) {
            var step = FactoryRunner.prepare(state, compiled, catalog.generation(), environment, paid);
            state = step.storage(); charge = charge.add(step.charge());
            h.assertTrue(state.inputs().amounts().isEmpty() && state.outputs().amount(ingot).equals(paid == 80 ? BigInteger.valueOf(8) : BigInteger.ZERO),
                    "Multi-round batch lost inputs or committed before its final payment");
            var repeated = FactoryRunner.prepare(state, compiled, catalog.generation(), environment, paid);
            h.assertTrue(repeated.storage() == state && repeated.charge().signum() == 0, "Repeated timestamp charged twice");
            if (paid == 27) {
                var paused = state.withControls(false, RedstoneMode.IGNORE);
                h.assertTrue(FactoryRunner.prepare(paused, compiled, catalog.generation(), environment, 1000).storage() == paused, "Pause advanced progress");
                state = FactoryStateCodec.load(FactoryStateCodec.save(paused, registries), registries).withControls(true, RedstoneMode.IGNORE);
                h.assertTrue(state.batch().paidTicks() == 27 && state.batch().requiredTicks() == 80, "Reload lost the multi-round duration");
            }
        }
        h.assertTrue(state.batch() == null && charge.equals(FactoryRules.energyPerTick(1).multiply(BigInteger.valueOf(80))), "Wrong completion or energy");
        var highCapacity = source.withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.ONE), RIGHT))
                .insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, 63), registries).storage()
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(128))));
        var highCompiled = catalog.compile(highCapacity.layout(), h.getLevel()).factory();
        var highBatch = FactoryRunner.prepare(highCapacity, highCompiled, catalog.generation(), environment, 1000).storage().batch();
        h.assertTrue(highBatch != null && highBatch.parallelism() == 128 && highBatch.requiredTicks() == 20
                        && highBatch.outputs().amount(ingot).equals(BigInteger.valueOf(128))
                        && highBatch.energyPerTick().equals(FactoryRules.energyPerTick(64)),
                "Installed machines did not permit batch copies above the environment multiplier");
        var nearlyFull = highCapacity.withOutputs(new ResourceInventory(Map.of(ingot, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.TWO))));
        h.assertTrue(FactoryRunner.prepare(nearlyFull, highCompiled, catalog.generation(), environment, 1001).storage().batch().parallelism() == 2,
                "Output capacity did not limit actual parallelism");
        h.succeed();
    }
}
