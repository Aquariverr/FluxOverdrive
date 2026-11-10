package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
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
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureResourceEditingGameTests {

    private static final BlockPos TEST_POS = new BlockPos(2, 2, 2);

    @GameTest(template = "empty")
    public static void inputTransfersConsumeOnlyTheRealCursorAndDuplicateExtractionCannotRepeat(GameTestHelper helper) {
        Fixture fixture = fixture(helper, FactoryStorage.empty());
        ResourceKey ore = key(helper, new ItemStack(Items.IRON_ORE));
        fixture.player().getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 19));
        fixture.menu().setCarried(new ItemStack(Items.IRON_ORE, 10));
        UUID initial = fixture.menu().snapshotId();
        assertResult(helper, execute(fixture, 1, Action.INSERT_INPUT, -1, 4), Result.SUCCESS);
        helper.assertTrue(input(fixture, ore).equals(BigInteger.valueOf(4)) && fixture.menu().getCarried().getCount() == 6,
                "Input insertion did not transfer exactly four cursor items");
        helper.assertTrue(fixture.menu().revision() == 0 && !initial.equals(fixture.menu().snapshotId()),
                "Inventory change did not change its snapshot identity independently of the layout revision");
        var extraction = command(fixture, 2, Action.EXTRACT_INPUT, 0, BigInteger.valueOf(4));
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), extraction), Result.SUCCESS);
        Assets completed = assets(fixture);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), extraction), Result.SUCCESS);
        assertUnchanged(helper, fixture, completed);
        helper.assertTrue(input(fixture, ore).signum() == 0 && fixture.menu().getCarried().getCount() == 10
                        && fixture.player().getInventory().getItem(0).is(Items.GOLD_INGOT)
                        && fixture.player().getInventory().getItem(0).getCount() == 19,
                "Resource transfer used the main hand or changed the conserved item count");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outputExtractionMergesExactVariantsOnlyUpToTheirNaturalStackLimit(GameTestHelper helper) {
        ItemStack item = new ItemStack(Items.IRON_INGOT);
        item.set(DataComponents.MAX_STACK_SIZE, 99);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Large output stack"));
        ResourceKey resource = key(helper, item);
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(
                new ResourceInventory(Map.of(resource, BigInteger.valueOf(200)))));
        fixture.menu().setCarried(item.copyWithCount(65));
        assertResult(helper, execute(fixture, 1, Action.EXTRACT_OUTPUT, 0, Integer.MAX_VALUE), Result.SUCCESS);
        helper.assertTrue(fixture.menu().getCarried().getCount() == 99
                        && output(fixture, resource).equals(BigInteger.valueOf(166))
                        && key(helper, fixture.menu().getCarried()).equals(resource),
                "Output extraction did not respect the variant's natural 99-item stack limit");
        Assets full = assets(fixture);
        assertResult(helper, execute(fixture, 2, Action.EXTRACT_OUTPUT, 0, 1), Result.CURSOR_BLOCKED);
        assertUnchanged(helper, fixture, full);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturallyUnstackableOutputLeavesTheRemainingAssetsInStorage(GameTestHelper helper) {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        ResourceKey resource = key(helper, tool);
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(
                new ResourceInventory(Map.of(resource, BigInteger.valueOf(3)))));
        assertResult(helper, execute(fixture, 1, Action.EXTRACT_OUTPUT, 0, 64), Result.SUCCESS);
        helper.assertTrue(fixture.menu().getCarried().getCount() == 1 && fixture.menu().getCarried().is(Items.IRON_PICKAXE)
                        && output(fixture, resource).equals(BigInteger.TWO),
                "Naturally unstackable output was duplicated, overstacked, or fully removed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void distinctComponentsAndDifferentItemsCannotBeMergedIntoTheCursor(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.IRON_INGOT);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("First variant"));
        ItemStack second = first.copy();
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Second variant"));
        ResourceKey resource = key(helper, first);
        Fixture fixture = fixture(helper, FactoryStorage.empty().withInputs(
                new ResourceInventory(Map.of(resource, BigInteger.TEN))));
        int id = 1;
        for (ItemStack blocked : new ItemStack[]{second, new ItemStack(Items.GOLD_INGOT)}) {
            fixture.menu().setCarried(blocked);
            Assets before = assets(fixture);
            assertResult(helper, execute(fixture, id++, Action.EXTRACT_INPUT, 0, 64), Result.CURSOR_BLOCKED);
            assertUnchanged(helper, fixture, before);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void competingInventorySnapshotsCannotExtractTheResourceThatMovedIntoAnOldIndex(GameTestHelper helper) {
        ResourceKey ore = key(helper, new ItemStack(Items.IRON_ORE));
        ResourceKey gold = key(helper, new ItemStack(Items.GOLD_INGOT));
        LinkedHashMap<ResourceKey, BigInteger> inputs = new LinkedHashMap<>();
        inputs.put(ore, BigInteger.ONE);
        inputs.put(gold, BigInteger.valueOf(7));
        Fixture first = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(inputs)));
        Fixture second = playerFixture(helper, first.tile());
        var stale = command(second, 1, Action.EXTRACT_INPUT, 0, BigInteger.valueOf(64));
        assertResult(helper, execute(first, 1, Action.EXTRACT_INPUT, 0, 64), Result.SUCCESS);
        helper.assertTrue(first.menu().revision() == stale.expectedRevision(),
                "Fixture unexpectedly depended on a changed layout revision");
        Assets before = assets(second);
        var rejected = second.menu().handleCommand(second.player(), stale);
        assertResult(helper, rejected, Result.STALE_REVISION);
        assertUnchanged(helper, second, before);
        helper.assertTrue(!rejected.snapshotId().equals(stale.expectedSnapshotId())
                        && rejected.snapshot().inputs().getFirst().resource().equals(gold),
                "Stale inventory command did not receive the authoritative replacement snapshot");
        assertResult(helper, execute(second, 2, Action.EXTRACT_INPUT, 0, 64), Result.SUCCESS);
        helper.assertTrue(second.menu().getCarried().is(Items.GOLD_INGOT) && second.menu().getCarried().getCount() == 7,
                "A newly confirmed inventory index failed to select the current resource");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void broadcastDoesNotMakeAnOldInventorySelectionValidAgain(GameTestHelper helper) {
        ResourceKey ore = key(helper, new ItemStack(Items.IRON_ORE));
        Fixture fixture = fixture(helper, FactoryStorage.empty().withInputs(
                new ResourceInventory(Map.of(ore, BigInteger.ONE))));
        var stale = command(fixture, 1, Action.EXTRACT_INPUT, 0, BigInteger.ONE);
        fixture.tile().extractInput(ore, 1, false);
        fixture.tile().insertInput(new ItemStack(Items.GOLD_INGOT, 4), false);
        fixture.menu().broadcastChanges();
        Assets before = assets(fixture);
        helper.assertTrue(fixture.menu().revision() == 0 && !fixture.menu().snapshotId().equals(stale.expectedSnapshotId()),
                "External inventory change did not replace the menu snapshot identity");
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), stale), Result.STALE_REVISION);
        assertUnchanged(helper, fixture, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inputInsertionAlsoRequiresTheCurrentInventorySnapshot(GameTestHelper helper) {
        Fixture fixture = fixture(helper, FactoryStorage.empty());
        fixture.menu().setCarried(new ItemStack(Items.IRON_ORE, 4));
        var stale = command(fixture, 1, Action.INSERT_INPUT, -1, BigInteger.valueOf(4));
        fixture.tile().insertInput(new ItemStack(Items.GOLD_INGOT), false);
        Assets before = assets(fixture);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), stale), Result.STALE_REVISION);
        assertUnchanged(helper, fixture, before);
        assertResult(helper, execute(fixture, 2, Action.INSERT_INPUT, -1, 4), Result.SUCCESS);
        helper.assertTrue(fixture.menu().getCarried().isEmpty(), "Fresh input insertion did not consume its exact cursor amount");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inputCapacityAcceptsOnlyAvailableSpaceAndRejectsTheRemainderWithoutMutation(GameTestHelper helper) {
        ResourceKey ore = key(helper, new ItemStack(Items.IRON_ORE));
        Fixture fixture = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(
                Map.of(ore, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.ONE)))));
        fixture.menu().setCarried(new ItemStack(Items.IRON_ORE, 7));
        assertResult(helper, execute(fixture, 1, Action.INSERT_INPUT, -1, 7), Result.SUCCESS);
        helper.assertTrue(input(fixture, ore).equals(FactoryLimits.MAX_INVENTORY_AMOUNT)
                        && fixture.menu().getCarried().getCount() == 6,
                "Input insertion did not preserve the remainder beyond its exact capacity");
        Assets full = assets(fixture);
        UUID fullSnapshot = fixture.menu().snapshotId();
        assertResult(helper, execute(fixture, 2, Action.INSERT_INPUT, -1, 6), Result.STATE_TOO_LARGE);
        assertUnchanged(helper, fixture, full);
        helper.assertTrue(fullSnapshot.equals(fixture.menu().snapshotId()), "Failed insertion changed the inventory snapshot identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidInventoryIndicesAndTransferAmountsCannotChangeAssets(GameTestHelper helper) {
        ResourceKey ore = key(helper, new ItemStack(Items.IRON_ORE));
        Fixture fixture = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(Map.of(ore, BigInteger.TEN))));
        Assets before = assets(fixture);
        assertResult(helper, execute(fixture, 1, Action.EXTRACT_INPUT, 255, 1), Result.INVALID_POSITION);
        assertUnchanged(helper, fixture, before);
        var tooLarge = command(fixture, 2, Action.EXTRACT_INPUT, 0, BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE));
        var rejected = fixture.menu().handleCommand(fixture.player(), tooLarge);
        assertResult(helper, rejected, Result.INVALID_QUANTITY);
        helper.assertTrue(rejected.errorContext().expected().equals(BigInteger.valueOf(Integer.MAX_VALUE)),
                "Inventory transfer error reported the machine count limit instead of the transfer bound");
        assertUnchanged(helper, fixture, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemInventoryCommandsCannotMaterializeStoredFluidResources(GameTestHelper helper) {
        ResourceKey water = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(new ResourceInventory(Map.of(water, BigInteger.valueOf(1000)))));
        Assets before = assets(fixture);
        assertResult(helper, execute(fixture, 1, Action.EXTRACT_OUTPUT, 0, 64), Result.INVALID_COMPONENTS);
        assertUnchanged(helper, fixture, before);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resourceCommandsRetainTheMenuSpectatorAndDistanceGuards(GameTestHelper helper) {
        Fixture fixture = fixture(helper, FactoryStorage.empty());
        fixture.menu().setCarried(new ItemStack(Items.IRON_ORE, 5));
        var command = command(fixture, 1, Action.INSERT_INPUT, -1, BigInteger.valueOf(5));
        Assets before = assets(fixture);
        fixture.player().setGameMode(GameType.SPECTATOR);
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), command), Result.INVALID_ACCESS);
        assertUnchanged(helper, fixture, before);
        fixture.player().setGameMode(GameType.SURVIVAL);
        BlockPos position = fixture.tile().getBlockPos();
        fixture.player().setPos(position.getX() + 20, position.getY(), position.getZ());
        assertResult(helper, fixture.menu().handleCommand(fixture.player(), command), Result.INVALID_ACCESS);
        assertUnchanged(helper, fixture, before);
        helper.succeed();
    }

    static Fixture fixture(GameTestHelper helper, FactoryStorage storage) {
        helper.setBlock(TEST_POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState());
        TileMiniatureUniverse tile = (TileMiniatureUniverse) helper.getLevel().getBlockEntity(helper.absolutePos(TEST_POS));
        helper.assertTrue(tile != null, "Missing miniature resource editing fixture");
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, helper.getLevel().registryAccess()).save());
        tile.loadAdditional(wrapper, helper.getLevel().registryAccess());
        return playerFixture(helper, tile);
    }

    static Fixture playerFixture(GameTestHelper helper, TileMiniatureUniverse tile) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "s04-resource-editor");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault()) {
            @Override
            public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
                return pos;
            }
        };
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {}

            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        BlockPos position = tile.getBlockPos();
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        MiniatureUniverseMenu menu = new MiniatureUniverseMenu(1, player.getInventory(), tile);
        player.containerMenu = menu;
        menu.broadcastChanges();
        return new Fixture(tile, player, menu);
    }

    private static MiniatureFactoryCommandPayload command(Fixture fixture, long id, Action action, int index, BigInteger amount) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), id, action, 0, 0, amount, GridDirection.RIGHT, fixture.menu().snapshotId(), index);
    }

    private static MiniatureFactorySnapshotPayload execute(Fixture fixture, long id, Action action, int index, int amount) {
        return fixture.menu().handleCommand(fixture.player(), command(fixture, id, action, index, BigInteger.valueOf(amount)));
    }

    private static ResourceKey key(GameTestHelper helper, ItemStack item) {
        return FactoryItemResources.keyOf(item, helper.getLevel().registryAccess());
    }

    private static BigInteger input(Fixture fixture, ResourceKey resource) {
        return fixture.tile().factoryState().storage().orElseThrow().inputs().amount(resource);
    }

    private static BigInteger output(Fixture fixture, ResourceKey resource) {
        return fixture.tile().factoryState().storage().orElseThrow().outputs().amount(resource);
    }

    private static Assets assets(Fixture fixture) {
        return new Assets(fixture.tile().factoryState().save(), fixture.menu().getCarried().copy());
    }

    private static void assertUnchanged(GameTestHelper helper, Fixture fixture, Assets before) {
        helper.assertTrue(before.state().equals(fixture.tile().factoryState().save())
                        && ItemStack.matches(before.carried(), fixture.menu().getCarried()),
                "Rejected or repeated inventory command changed factory or cursor assets");
    }

    private static void assertResult(GameTestHelper helper, MiniatureFactorySnapshotPayload payload, Result result) {
        helper.assertTrue(payload.result() == result, "Expected " + result + " but received " + payload.result());
    }

    record Fixture(TileMiniatureUniverse tile, ServerPlayer player, MiniatureUniverseMenu menu) {}
    private record Assets(CompoundTag state, ItemStack carried) {}

    private MiniatureResourceEditingGameTests() {}
}
