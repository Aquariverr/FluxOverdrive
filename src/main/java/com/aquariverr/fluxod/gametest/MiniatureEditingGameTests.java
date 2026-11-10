package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureEditingGameTests {

    private static final BlockPos TEST_POS = new BlockPos(2, 2, 2);
    private static final GridPosition CELL = new GridPosition(0, 0);

    @GameTest(template = "empty")
    public static void machineRotationPreservesRecipeAssetsAndSurvivesReload(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var registries = helper.getLevel().registryAccess();
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 5));
        assertResult(helper, execute(fixture, 0, 1, Action.INSERT_MACHINE, 2), Result.SUCCESS);
        var before = fixture.tile().factoryState();
        var recipe = new com.aquariverr.fluxod.miniature.model.RecipeReference("flux_overdrive:smelting",
                "minecraft:iron_ingot_from_smelting_iron_ore", 1, 1, List.of(
                new com.aquariverr.fluxod.miniature.model.RecipeReference.Binding(0,
                        FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries))));
        helper.assertTrue(fixture.tile().applyPreparedState(before,
                FactoryState.of(before.storage().orElseThrow().withRecipe(CELL, recipe), registries)), "Could not select fixture recipe");
        long id = 2;
        for (GridDirection direction : List.of(GridDirection.DOWN, GridDirection.LEFT, GridDirection.UP, GridDirection.RIGHT)) {
            long revision = fixture.tile().factoryState().storage().orElseThrow().layout().revision();
            var command = command(fixture, revision, id++, Action.ROTATE_DIRECTION, 1);
            var response = fixture.menu().handleCommand(fixture.player(), command);
            assertResult(helper, response, Result.SUCCESS);
            var machine = (FactoryCell.Machine) fixture.tile().factoryState().storage().orElseThrow().layout().cellAt(CELL);
            helper.assertTrue(machine.direction() == direction && machine.recipe().equals(recipe) && machine.stack().count() == 2
                    && fixture.menu().getCarried().getCount() == 3 && response.snapshot().cells().getFirst().direction() == direction,
                    "Rotation lost direction, recipe, count, cursor, or snapshot state");
            var rotated = fixture.tile().factoryState().save();
            assertResult(helper, fixture.menu().handleCommand(fixture.player(), command), Result.SUCCESS);
            helper.assertTrue(rotated.equals(fixture.tile().factoryState().save()), "Duplicate rotation rotated twice");
        }
        long revision = fixture.tile().factoryState().storage().orElseThrow().layout().revision();
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(), revision, id++,
                Action.SET_DIRECTION, 0, 0, BigInteger.ONE, GridDirection.UP)), Result.SUCCESS);
        assertResult(helper, execute(fixture, revision + 1, id++, Action.INSERT_MACHINE, 2), Result.SUCCESS);
        assertResult(helper, execute(fixture, revision + 2, id, Action.EXTRACT_MACHINE, 1), Result.SUCCESS);
        var saved = fixture.tile().factoryState().save();
        var restored = FactoryStateCodec.load(saved, registries);
        var machine = (FactoryCell.Machine) restored.layout().cellAt(CELL);
        helper.assertTrue(machine.direction() == GridDirection.UP && machine.recipe().equals(recipe) && machine.stack().count() == 3
                && fixture.menu().getCarried().getCount() == 2, "Stack edits or reload reset machine direction or assets");
        var legacy = saved.copy();
        legacy.getList("cells", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("direction");
        helper.assertTrue(((FactoryCell.Machine) FactoryStateCodec.load(legacy, registries).layout().cellAt(CELL)).direction()
                == GridDirection.RIGHT, "Legacy machines must default to right");
        legacy.getList("cells", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putString("direction", "INVALID");
        helper.assertTrue(FactoryState.load(legacy, registries).storage().isEmpty(), "Invalid machine direction was silently accepted");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicateCommandsReturnTheirAcknowledgementWithoutTransferringAgain(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 5));
        MiniatureFactoryCommandPayload command = command(fixture, 0, 1, Action.INSERT_MACHINE, 2);
        MiniatureFactorySnapshotPayload first = fixture.menu().handleCommand(fixture.player(), command);
        assertResult(helper, first, Result.SUCCESS);
        Assets after = assets(fixture);
        MiniatureFactorySnapshotPayload repeated = fixture.menu().handleCommand(fixture.player(), command);
        helper.assertTrue(repeated.result() == first.result()
                        && repeated.acknowledgedCommandId() == first.acknowledgedCommandId()
                        && repeated.acknowledgedRevision() == first.acknowledgedRevision(),
                "Duplicate command did not return its existing acknowledgement");
        assertAssets(helper, fixture, after, "Duplicate command transferred assets again");
        helper.assertTrue(machineCount(fixture) == 2 && fixture.menu().getCarried().getCount() == 3,
                "Initial insertion did not transfer exactly two real machines");
        assertResult(helper, fixture.menu().handleCommand(fixture.player(),
                command(fixture, 0, 1, Action.INSERT_MACHINE, 3)), Result.COMMAND_ID_REUSED);
        assertAssets(helper, fixture, after, "Changed content reused a command ID to transfer assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleRevisionCannotConsumeTheCursor(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 4));
        assertResult(helper, execute(fixture, 0, 1, Action.INSERT_MACHINE, 1), Result.SUCCESS);
        Assets before = assets(fixture);
        MiniatureFactorySnapshotPayload rejected = execute(fixture, 0, 2, Action.INSERT_MACHINE, 1);
        assertResult(helper, rejected, Result.STALE_REVISION);
        helper.assertTrue(rejected.revision() == 1, "Stale revision response did not expose the authoritative revision");
        helper.assertTrue(rejected.errorContext().x() == 0 && rejected.errorContext().y() == 0
                        && BigInteger.ZERO.equals(rejected.errorContext().expected())
                        && BigInteger.ONE.equals(rejected.errorContext().actual()),
                "Stale revision error omitted its target and expected/actual revisions");
        assertAssets(helper, fixture, before, "Stale revision changed the factory or cursor");
        assertResult(helper, execute(fixture, 1, 3, Action.INSERT_MACHINE, 1), Result.SUCCESS);
        Assets updated = assets(fixture);
        MiniatureFactorySnapshotPayload repeated = execute(fixture, 0, 2, Action.INSERT_MACHINE, 1);
        assertResult(helper, repeated, Result.STALE_REVISION);
        helper.assertTrue(repeated.revision() == 2 && repeated.acknowledgedRevision() == 1
                        && repeated.errorContext().equals(rejected.errorContext()),
                "Duplicate rejection replaced its original error with the current factory revision");
        assertAssets(helper, fixture, updated, "Repeated stale command changed assets after a later accepted command");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void evictedCommandReceiptsStillRejectOldTransfers(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 2));
        MiniatureFactoryCommandPayload first = command(fixture, 0, 1, Action.INSERT_MACHINE, 1);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), first), Result.SUCCESS);
        MiniatureFactoryCommandPayload arrow = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                1, 2, Action.CREATE_ARROW, 1, 0, BigInteger.ONE, GridDirection.RIGHT);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), arrow), Result.SUCCESS);
        for (int index = 0; index < MiniatureUniverseMenu.RECEIPT_LIMIT; index++) {
            MiniatureFactoryCommandPayload rotate = new MiniatureFactoryCommandPayload(
                    fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                    index + 2, index + 3, Action.ROTATE_ARROW, 1, 0, BigInteger.ONE, GridDirection.RIGHT);
            assertResult(helper, fixture.menu().handleCommand(fixture.player(), rotate), Result.SUCCESS);
        }
        Assets before = assets(fixture);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), first), Result.COMMAND_EXPIRED);
        assertAssets(helper, fixture, before, "Receipt eviction permitted an old asset transfer to run again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void twoPlayersCompeteForOneAuthoritativeRevision(GameTestHelper helper) {
        Fixture first = fixture(helper);
        Fixture second = playerFixture(helper, first.tile());
        first.menu().setCarried(new ItemStack(Items.FURNACE));
        second.menu().setCarried(new ItemStack(Items.FURNACE));
        MiniatureFactoryCommandPayload firstCommand = command(first, 0, 1, Action.INSERT_MACHINE, 1);
        MiniatureFactoryCommandPayload secondCommand = command(second, 0, 1, Action.INSERT_MACHINE, 1);
        assertResult(helper, first.menu().handleCommand(first.player(), firstCommand), Result.SUCCESS);
        assertResult(helper, second.menu().handleCommand(second.player(), secondCommand), Result.STALE_REVISION);
        helper.assertTrue(machineCount(first) == 1 && first.menu().getCarried().isEmpty()
                        && second.menu().getCarried().getCount() == 1,
                "Concurrent old-revision commands did not preserve exactly two machines");
        helper.assertTrue(first.tile().factoryState().storage().orElseThrow().layout().revision() == 1,
                "Two competing old-revision commands both changed the layout");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spectatorAndDistantPlayersCannotEdit(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 2));
        fixture.player().setGameMode(GameType.SPECTATOR);
        Assets before = assets(fixture);
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 1, Action.INSERT_MACHINE, 1), before);
        fixture.player().setGameMode(GameType.SURVIVAL);
        fixture.player().setPos(fixture.tile().getBlockPos().getX() + 9.0,
                fixture.tile().getBlockPos().getY() + 0.5, fixture.tile().getBlockPos().getZ() + 0.5);
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 2, Action.INSERT_MACHINE, 1), before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedAndReplacedEntitiesInvalidateExistingMenus(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE));
        Assets before = assets(fixture);
        fixture.tile().setRemoved();
        helper.assertTrue(!fixture.menu().stillValid(fixture.player()), "Removed entity retained a valid editing menu");
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 1, Action.INSERT_MACHINE, 1), before);
        fixture.tile().clearRemoved();
        BlockPos position = fixture.tile().getBlockPos();
        helper.getLevel().removeBlock(position, false);
        helper.getLevel().setBlock(position, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
        TileMiniatureUniverse replacement = (TileMiniatureUniverse) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(replacement != null && replacement != fixture.tile(), "Replacement did not create a distinct entity");
        CompoundTag replacementBefore = replacement.factoryState().save();
        helper.assertTrue(!fixture.menu().stillValid(fixture.player()), "Menu accepted a replacement entity at the same coordinates");
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 2, Action.INSERT_MACHINE, 1), before);
        helper.assertTrue(replacementBefore.equals(replacement.factoryState().save()), "Stale menu mutated the replacement entity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forgedMenuIdentityAndPlayerCannotEdit(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        Assets before = assets(fixture);
        MiniatureFactoryCommandPayload wrongContainer = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId + 1, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                0, 1, Action.INSERT_MACHINE, 0, 0, BigInteger.ONE, GridDirection.RIGHT);
        assertRejectedWithoutMutation(helper, fixture, wrongContainer, before);
        MiniatureFactoryCommandPayload wrongSession = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, UUID.randomUUID(), fixture.tile().getBlockPos(),
                0, 2, Action.INSERT_MACHINE, 0, 0, BigInteger.ONE, GridDirection.RIGHT);
        assertRejectedWithoutMutation(helper, fixture, wrongSession, before);
        MiniatureFactoryCommandPayload wrongTarget = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos().above(),
                0, 3, Action.INSERT_MACHINE, 0, 0, BigInteger.ONE, GridDirection.RIGHT);
        assertRejectedWithoutMutation(helper, fixture, wrongTarget, before);
        Fixture other = playerFixture(helper, fixture.tile());
        other.menu().setCarried(new ItemStack(Items.FURNACE, 2));
        Assets otherBefore = assets(other);
        helper.assertTrue(fixture.menu().handleCommand(other.player(),
                command(fixture, 0, 4, Action.INSERT_MACHINE, 1)).result() != Result.SUCCESS,
                "A different player invoked another player's editing menu");
        assertAssets(helper, fixture, before, "Forged player changed the owner cursor or factory");
        assertAssets(helper, other, otherBefore, "Forged player changed the attacking player's assets");
        fixture.player().containerMenu = fixture.player().inventoryMenu;
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 5, Action.INSERT_MACHINE, 1), before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidCoordinatesAndMachineQuantitiesPreserveAssets(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        Assets before = assets(fixture);
        long id = 1;
        for (int[] coordinates : List.of(new int[]{3, 0}, new int[]{0, 3}, new int[]{10, 10})) {
            MiniatureFactoryCommandPayload command = new MiniatureFactoryCommandPayload(
                    fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                    0, id++, Action.INSERT_MACHINE, coordinates[0], coordinates[1], BigInteger.ONE, GridDirection.RIGHT);
            MiniatureFactorySnapshotPayload rejected = fixture.menu().handleCommand(fixture.player(), command);
            assertResult(helper, rejected, Result.INVALID_POSITION);
            helper.assertTrue(rejected.errorContext().x() == coordinates[0]
                            && rejected.errorContext().y() == coordinates[1],
                    "Invalid position response omitted the rejected target coordinates");
            assertAssets(helper, fixture, before, "Invalid position command changed assets");
        }
        for (int amount : List.of(65, Integer.MAX_VALUE)) {
            MiniatureFactoryCommandPayload command = command(fixture, 0, id++, Action.INSERT_MACHINE, amount);
            MiniatureFactorySnapshotPayload rejected = fixture.menu().handleCommand(fixture.player(), command);
            assertResult(helper, rejected, Result.INVALID_QUANTITY);
            helper.assertTrue(rejected.errorContext().x() == 0 && rejected.errorContext().y() == 0
                            && BigInteger.valueOf(64).equals(rejected.errorContext().expected())
                            && BigInteger.valueOf(amount).equals(rejected.errorContext().actual()),
                    "Invalid quantity response omitted the target, machine limit or requested amount");
            MiniatureFactorySnapshotPayload repeated = fixture.menu().handleCommand(fixture.player(), command);
            helper.assertTrue(repeated.result() == rejected.result()
                            && repeated.errorContext().equals(rejected.errorContext()),
                    "Duplicate invalid quantity command changed its original error details");
            assertAssets(helper, fixture, before, "Invalid quantity or repeated command changed assets");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void oversizedMachineComponentsCannotConsumeTheCursor(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemStack offered = new ItemStack(Items.FURNACE);
        offered.set(DataComponents.CUSTOM_NAME, Component.literal("x".repeat(70_000)));
        fixture.menu().setCarried(offered);
        Assets before = assets(fixture);
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, 0, 1, Action.INSERT_MACHINE, 1), before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unavailableSavedStatesRejectCommandsWithoutExposingRawData(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        CompoundTag valid = fixture.tile().factoryState().save();
        CompoundTag unknown = valid.copy();
        unknown.putString("format", "flux_overdrive:future_miniature_factory");
        unknown.putString("privateDiagnosticData", "S03 unknown raw asset content");
        CompoundTag invalid = valid.copy();
        invalid.putInt("gridSize", 4);
        invalid.putString("privateDiagnosticData", "S03 invalid raw asset content");
        long commandId = 1;
        for (CompoundTag raw : List.of(unknown, invalid)) {
            loadState(helper, fixture.tile(), raw);
            helper.assertTrue(fixture.tile().factoryState().storage().isEmpty(), "Unavailable fixture was accepted as editable state");
            Assets before = assets(fixture);
            MiniatureFactorySnapshotPayload response = execute(fixture, 0, commandId++, Action.INSERT_MACHINE, 1);
            assertResult(helper, response, Result.STATE_UNAVAILABLE);
            helper.assertTrue(response.snapshot().size() == 0 && response.snapshot().cells().isEmpty()
                            && response.snapshot().inputs().isEmpty() && response.snapshot().outputs().isEmpty(),
                    "Unavailable snapshot exposed a partial or raw asset representation");
            assertAssets(helper, fixture, before, "Unavailable state command changed preserved raw data or the cursor");
            helper.assertTrue(raw.equals(fixture.tile().factoryState().save()), "Unavailable raw asset data was discarded");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void combinedStateBudgetFailurePreservesBothAssetOwners(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Map<ResourceKey, BigInteger> inputs = new LinkedHashMap<>();
        for (int index = 0; index < 8; index++) {
            CompoundTag content = new CompoundTag();
            content.putByteArray("payload", new byte[60 * 1024]);
            content.putInt("variant", index);
            ItemStack resource = new ItemStack(Items.STONE);
            resource.set(DataComponents.CUSTOM_DATA, CustomData.of(content));
            inputs.put(FactoryItemResources.keyOf(resource, helper.getLevel().registryAccess()), BigInteger.ONE);
        }
        FactoryStorage storage = FactoryStorage.empty().withInputs(new ResourceInventory(inputs));
        loadState(helper, fixture.tile(), FactoryStateCodec.save(storage, helper.getLevel().registryAccess()));
        helper.assertTrue(fixture.tile().factoryState().storage().isPresent(), "The below-limit fixture was not a valid S02 state");
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.CUSTOM_NAME, Component.literal("m".repeat(40_000)));
        FactoryItemResources.keyOf(machine, helper.getLevel().registryAccess());
        fixture.menu().setCarried(machine);
        Assets before = assets(fixture);
        assertResult(helper, execute(fixture, 0, 1, Action.INSERT_MACHINE, 1), Result.STATE_TOO_LARGE);
        assertAssets(helper, fixture, before, "Full-state encoding failure consumed the cursor or changed stored assets");
        assertResult(helper, execute(fixture, 0, 2, Action.SET_MARKER_FROM_CARRIED, 1), Result.STATE_TOO_LARGE);
        assertAssets(helper, fixture, before, "Full-state marker encoding failure changed configuration or assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistedResourceOutsideSnapshotBudgetProducesOnlyUnavailablePreview(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "flux_overdrive:" + "a".repeat(40_000));
        GridPosition oversizedMarker = new GridPosition(1, 0);
        FactoryStorage storage = FactoryStorage.empty().withConfiguration(oversizedMarker, new FactoryCell.Marker(resource));
        loadState(helper, fixture.tile(), FactoryStateCodec.save(storage, helper.getLevel().registryAccess()));
        helper.assertTrue(fixture.tile().factoryState().storage().isPresent(), "Long registry ID fixture was not valid persisted state");
        fixture.menu().setCarried(new ItemStack(Items.FURNACE));
        Assets before = assets(fixture);
        fixture.packets().clear();
        fixture.menu().broadcastChanges();
        List<MiniatureFactorySnapshotPayload> snapshots = fixture.packets().stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(payload -> payload instanceof MiniatureFactorySnapshotPayload)
                .map(payload -> (MiniatureFactorySnapshotPayload) payload).toList();
        helper.assertTrue(snapshots.size() == 1, "Unsynchronizable persisted state did not produce one bounded response");
        MiniatureFactorySnapshotPayload response = snapshots.getFirst();
        assertResult(helper, response, Result.STATE_TOO_LARGE);
        helper.assertTrue(response.snapshot().status() == FactoryState.Status.INVALID_STATE
                        && response.snapshot().size() == 0 && response.snapshot().inputs().isEmpty(),
                "Unsynchronizable state leaked a partial resource preview");
        assertAssets(helper, fixture, before, "Snapshot fallback changed preserved server assets");
        assertResult(helper, execute(fixture, 1, 1, Action.INSERT_MACHINE, 1), Result.STATE_TOO_LARGE);
        assertAssets(helper, fixture, before, "Snapshot encoding failure committed the cursor or machine transfer");
        MiniatureFactoryCommandPayload clear = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                1, 2, Action.CLEAR_CONFIGURATION, oversizedMarker.x(), oversizedMarker.y(),
                BigInteger.ONE, GridDirection.RIGHT);
        MiniatureFactorySnapshotPayload recovered = fixture.menu().handleCommand(fixture.player(), clear);
        assertResult(helper, recovered, Result.SUCCESS);
        helper.assertTrue(recovered.revision() == 2 && recovered.snapshot().status() == FactoryState.Status.READY
                        && recovered.snapshot().size() == 3 && recovered.snapshot().cells().size() == 9
                        && fixture.tile().factoryState().storage().orElseThrow().layout().cellAt(oversizedMarker) == FactoryCell.Empty.INSTANCE
                        && ItemStack.matches(fixture.menu().getCarried(), before.cursor()),
                "Clearing the oversized virtual marker did not restore an authoritative usable preview");
        fixture.packets().clear();
        fixture.menu().broadcastChanges();
        MiniatureFactorySnapshotPayload synchronizedRecovery = fixture.packets().stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof MiniatureFactorySnapshotPayload)
                .map(packet -> (MiniatureFactorySnapshotPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .findFirst().orElseThrow();
        assertResult(helper, synchronizedRecovery, Result.SUCCESS);
        helper.assertTrue(synchronizedRecovery.snapshot().equals(recovered.snapshot()),
                "Snapshot fallback remained active after the oversized marker was removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturallyUnstackableMachinesReturnOneAndKeepTheRemainder(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        long revision = 0;
        for (int index = 0; index < 3; index++) {
            ItemStack machine = new ItemStack(Items.FURNACE);
            machine.set(DataComponents.MAX_STACK_SIZE, 1);
            machine.set(DataComponents.CUSTOM_NAME, Component.literal("S03 singular furnace"));
            fixture.menu().setCarried(machine);
            assertResult(helper, execute(fixture, revision++, index + 1, Action.INSERT_MACHINE, 1), Result.SUCCESS);
        }
        assertResult(helper, execute(fixture, revision, 4, Action.EXTRACT_MACHINE, 64), Result.SUCCESS);
        helper.assertTrue(machineCount(fixture) == 2 && fixture.menu().getCarried().getCount() == 1
                        && fixture.menu().getCarried().getMaxStackSize() == 1,
                "Extraction ignored the natural stack limit or deleted remaining machines");
        Assets before = assets(fixture);
        assertRejectedWithoutMutation(helper, fixture,
                command(fixture, revision + 1, 5, Action.EXTRACT_MACHINE, 64), before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineTransferRespectsCellAndCursorCapacity(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 60));
        assertResult(helper, execute(fixture, 0, 1, Action.INSERT_MACHINE, 64), Result.SUCCESS);
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 10));
        assertResult(helper, execute(fixture, 1, 2, Action.INSERT_MACHINE, 64), Result.SUCCESS);
        helper.assertTrue(machineCount(fixture) == 64 && fixture.menu().getCarried().getCount() == 6,
                "Cell capacity failed to preserve insertion remainder");
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 62));
        assertResult(helper, execute(fixture, 2, 3, Action.EXTRACT_MACHINE, 64), Result.SUCCESS);
        helper.assertTrue(machineCount(fixture) == 62 && fixture.menu().getCarried().getCount() == 64,
                "Extraction exceeded the cursor's available capacity");
        Assets full = assets(fixture);
        assertRejectedWithoutMutation(helper, fixture, command(fixture, 3, 4, Action.EXTRACT_MACHINE, 64), full);
        fixture.menu().setCarried(new ItemStack(Items.STONE));
        Assets different = assets(fixture);
        assertRejectedWithoutMutation(helper, fixture, command(fixture, 3, 5, Action.EXTRACT_MACHINE, 64), different);
        assertRejectedWithoutMutation(helper, fixture, command(fixture, 3, 6, Action.INSERT_MACHINE, 1), different);
        assertRejectedWithoutMutation(helper, fixture, command(fixture, 3, 7, Action.CLEAR_CONFIGURATION, 1), different);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markerEditingDoesNotConsumeOrReturnResources(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemStack ore = new ItemStack(Items.IRON_ORE, 17);
        ore.set(DataComponents.CUSTOM_NAME, Component.literal("S03 marked ore"));
        fixture.menu().setCarried(ore);
        assertResult(helper, execute(fixture, 0, 1, Action.SET_MARKER_FROM_CARRIED, 1), Result.SUCCESS);
        helper.assertTrue(ItemStack.matches(fixture.menu().getCarried(), ore), "Creating a marker consumed its source item");
        FactoryCell cell = fixture.tile().factoryState().storage().orElseThrow().layout().cellAt(CELL);
        helper.assertTrue(cell instanceof FactoryCell.Marker marker
                        && marker.resource().resource().equals(FactoryItemResources.keyOf(ore, helper.getLevel().registryAccess()))
                        && marker.resource().amount().equals(BigInteger.ONE),
                "Marker did not preserve the exact item components and default quantity");
        assertResult(helper, execute(fixture, 1, 2, Action.SET_MARKER_QUANTITY, 29), Result.SUCCESS);
        MiniatureFactoryCommandPayload direction = new MiniatureFactoryCommandPayload(
                fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                2, 3, Action.SET_DIRECTION, 0, 0, BigInteger.ONE, GridDirection.DOWN);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), direction), Result.SUCCESS);
        assertResult(helper, execute(fixture, 3, 4, Action.ROTATE_DIRECTION, 1), Result.SUCCESS);
        helper.assertTrue(fixture.tile().factoryState().storage().orElseThrow().layout().cellAt(CELL)
                        .equals(new FactoryCell.Marker(new com.aquariverr.fluxod.miniature.model.ResourceAmount(
                                FactoryItemResources.keyOf(ore, helper.getLevel().registryAccess()), BigInteger.valueOf(29)), GridDirection.LEFT)),
                "Rotating a marker changed its resource, components, or quantity");
        assertResult(helper, execute(fixture, 4, 5, Action.ROTATE_DIRECTION, 1), Result.SUCCESS);
        assertResult(helper, execute(fixture, 5, 6, Action.CLEAR_CONFIGURATION, 1), Result.SUCCESS);
        helper.assertTrue(ItemStack.matches(fixture.menu().getCarried(), ore)
                        && fixture.tile().factoryState().storage().orElseThrow().machines().entries().isEmpty()
                        && fixture.tile().factoryState().storage().orElseThrow().inputs().amounts().isEmpty()
                        && fixture.tile().factoryState().storage().orElseThrow().outputs().amounts().isEmpty(),
                "Configuration editing created or consumed real assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeInventoryClickAndMenuClosePreserveCursorOwnership(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.player().getInventory().setItem(9, new ItemStack(Items.FURNACE, 7));
        int inventorySlot = -1;
        for (int index = 0; index < fixture.menu().slots.size(); index++) {
            var slot = fixture.menu().slots.get(index);
            if (slot.container == fixture.player().getInventory() && slot.getContainerSlot() == 9) inventorySlot = index;
        }
        helper.assertTrue(inventorySlot >= 0, "Local menu does not expose the player's main inventory");
        fixture.menu().clicked(inventorySlot, 0, ClickType.PICKUP, fixture.player());
        helper.assertTrue(fixture.menu().getCarried().getCount() == 7
                        && fixture.player().getInventory().getItem(9).isEmpty(),
                "Native inventory click did not transfer source machines to the actual menu cursor");
        assertResult(helper, execute(fixture, 0, 1, Action.INSERT_MACHINE, 3), Result.SUCCESS);
        MiniatureFactoryCommandPayload delayed = command(fixture, 1, 2, Action.INSERT_MACHINE, 1);
        fixture.player().closeContainer();
        helper.assertTrue(fixture.menu().getCarried().isEmpty() && inventoryCount(fixture.player(), Items.FURNACE) == 4
                        && machineCount(fixture) == 3 && fixture.player().containerMenu == fixture.player().inventoryMenu,
                "Closing the local menu failed to return the cursor without duplicating machines");
        Assets closedAssets = assets(fixture);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), delayed), Result.INVALID_ACCESS);
        // Even an accidental reattachment of the old object cannot reopen a server-side closed session.
        fixture.player().containerMenu = fixture.menu();
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), delayed), Result.INVALID_ACCESS);
        fixture.player().containerMenu = fixture.player().inventoryMenu;
        fixture.tile().openMenu(fixture.player());
        var reopened = (MiniatureUniverseMenu) fixture.player().containerMenu;
        helper.assertTrue(!reopened.sessionId().equals(fixture.menu().sessionId()), "Reopening reused a closed session identity");
        assertResult(helper, reopened.handleCommand(fixture.player(), delayed), Result.INVALID_SESSION);
        assertAssets(helper, fixture, closedAssets, "Late closed-session commands changed returned assets or factory state");
        fixture.player().closeContainer();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void realServerPlayerOpensTheRegisteredMenuWithBoundedOpeningData(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.player().closeContainer();
        fixture.packets().clear();
        fixture.tile().openMenu(fixture.player());
        helper.assertTrue(fixture.player().containerMenu instanceof MiniatureUniverseMenu,
                "ServerPlayer did not open the registered miniature menu");
        MiniatureUniverseMenu opened = (MiniatureUniverseMenu) fixture.player().containerMenu;
        helper.assertTrue(opened.stillValid(fixture.player()) && opened.slots.size() == 36,
                "Opened menu does not provide a valid local player inventory");
        List<AdvancedOpenScreenPayload> openings = fixture.packets().stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(payload -> payload instanceof AdvancedOpenScreenPayload)
                .map(payload -> (AdvancedOpenScreenPayload) payload).toList();
        helper.assertTrue(openings.size() == 1 && openings.getFirst().windowId() == opened.containerId
                        && openings.getFirst().menuType() == opened.getType()
                        && openings.getFirst().additionalData().length == Long.BYTES * 3,
                "Opening did not send exactly one registered menu with position and session data");
        fixture.player().closeContainer();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unchangedMenusDoNotResendCompleteSnapshotsEveryTick(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.packets().clear();
        fixture.menu().broadcastChanges();
        helper.assertTrue(snapshotCount(fixture) == 1, "Initial menu state was not synchronized exactly once");
        for (int tick = 0; tick < 20; tick++) fixture.menu().broadcastChanges();
        helper.assertTrue(snapshotCount(fixture) == 1, "Unchanged menu repeatedly synchronized complete factory state");
        assertResult(helper, execute(fixture, 0, 1, Action.CREATE_ARROW, 1), Result.SUCCESS);
        fixture.menu().broadcastChanges();
        helper.assertTrue(snapshotCount(fixture) == 2, "Changed layout was not synchronized to the open menu");
        fixture.menu().broadcastChanges();
        helper.assertTrue(snapshotCount(fixture) == 2, "New revision was synchronized more than once without changes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closingWithFullInventoryDropsTheCursorExactlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (int slot = 0; slot < 36; slot++) fixture.player().getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        fixture.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        AABB area = new AABB(fixture.tile().getBlockPos()).inflate(3.0);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "Full inventory close test started with unrelated drops");
        fixture.player().closeContainer();
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.FURNACE)
                        && drops.getFirst().getItem().getCount() == 3 && fixture.menu().getCarried().isEmpty(),
                "Closing with a full inventory lost or duplicated the carried machines");
        fixture.menu().removed(fixture.player());
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size() == 1,
                "Repeated menu removal dropped the same cursor twice");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        helper.setBlock(TEST_POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState());
        TileMiniatureUniverse tile = (TileMiniatureUniverse) helper.getLevel().getBlockEntity(helper.absolutePos(TEST_POS));
        helper.assertTrue(tile != null, "Could not create the miniature editing fixture");
        return playerFixture(helper, tile);
    }

    private static void loadState(GameTestHelper helper, TileMiniatureUniverse tile, CompoundTag state) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, state.copy());
        tile.loadAdditional(wrapper, helper.getLevel().registryAccess());
    }

    private static Fixture playerFixture(GameTestHelper helper, TileMiniatureUniverse tile) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "s03-editor");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, ClientInformation.createDefault()) {
            @Override
            public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
                // Menu tests do not need the constructor's random respawn search or its chunk generation.
                return pos;
            }
        };
        List<Packet<?>> sent = new ArrayList<>();
        // Keep real ServerPlayer menu/inventory behavior while terminating outbound packets in the fixture.
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
                sent.add(packet);
            }

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                sent.add(packet);
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        BlockPos position = tile.getBlockPos();
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        MiniatureUniverseMenu menu = new MiniatureUniverseMenu(1, player.getInventory(), tile);
        player.containerMenu = menu;
        return new Fixture(tile, player, menu, sent);
    }

    private static MiniatureFactoryCommandPayload command(Fixture fixture, long revision, long id, Action action, int quantity) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(),
                fixture.tile().getBlockPos(), revision, id, action, 0, 0, BigInteger.valueOf(quantity), GridDirection.RIGHT);
    }

    private static MiniatureFactorySnapshotPayload execute(Fixture fixture, long revision, long id, Action action, int quantity) {
        return fixture.menu().handleCommand(fixture.player(), command(fixture, revision, id, action, quantity));
    }

    private static int machineCount(Fixture fixture) {
        return fixture.tile().factoryState().storage().orElseThrow().machines().entry(CELL).map(entry -> entry.count()).orElse(0);
    }

    private static int inventoryCount(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static long snapshotCount(Fixture fixture) {
        return fixture.packets().stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof MiniatureFactorySnapshotPayload).count();
    }

    private static Assets assets(Fixture fixture) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int slot = 0; slot < fixture.player().getInventory().getContainerSize(); slot++) {
            inventory.add(fixture.player().getInventory().getItem(slot).copy());
        }
        return new Assets(fixture.tile().factoryState().save(), fixture.menu().getCarried().copy(), inventory);
    }

    private static void assertAssets(GameTestHelper helper, Fixture fixture, Assets expected, String message) {
        helper.assertTrue(expected.state().equals(fixture.tile().factoryState().save())
                        && ItemStack.matches(expected.cursor(), fixture.menu().getCarried()), message);
        for (int slot = 0; slot < expected.inventory().size(); slot++) {
            helper.assertTrue(ItemStack.matches(expected.inventory().get(slot), fixture.player().getInventory().getItem(slot)), message);
        }
    }

    private static void assertRejectedWithoutMutation(GameTestHelper helper, Fixture fixture,
                                                      MiniatureFactoryCommandPayload command, Assets before) {
        MiniatureFactorySnapshotPayload result = fixture.menu().handleCommand(fixture.player(), command);
        helper.assertTrue(result.result() != Result.SUCCESS, "Invalid command was accepted: " + command.action());
        assertAssets(helper, fixture, before, "Rejected command changed machine, resource, cursor or player inventory assets");
    }

    private static void assertResult(GameTestHelper helper, MiniatureFactorySnapshotPayload response, Result expected) {
        helper.assertTrue(response.result() == expected, "Expected " + expected + " but received " + response.result());
    }

    private record Fixture(TileMiniatureUniverse tile, ServerPlayer player, MiniatureUniverseMenu menu, List<Packet<?>> packets) {}

    private record Assets(CompoundTag state, ItemStack cursor, List<ItemStack> inventory) {}

    private MiniatureEditingGameTests() {}
}
