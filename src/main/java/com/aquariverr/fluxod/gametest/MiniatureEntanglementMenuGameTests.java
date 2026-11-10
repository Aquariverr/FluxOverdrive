package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetworkData;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureEntanglementMenuGameTests {
    private static final GridPosition CELL = new GridPosition(0, 0);

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void localAndRemoteEditorsShareOneRevisionAndRejectConflictingTransfers(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session remote = remote(helper, source);
        Session secondPlayer = player(helper.getLevel(), source.tile(), source.tile().getBlockPos());
        var networks = FluxNetworkData.getInstance();
        var network = networks.createNetwork(remote.player(), "S10 concurrent editors", 0x44CCEE, SecurityLevel.PUBLIC, "");
        helper.assertTrue(network != null, "Could not create the real shared Flux network");
        try {
            helper.assertTrue(source.battery().connect(network) && network.canPlayerAccess(secondPlayer.player())
                            && source.battery().canPlayerManage(remote.player())
                            && source.battery().canPlayerManage(secondPlayer.player()),
                    "The shared Flux network did not authorize both independent editors");
            MiniatureUniverseMenu localMenu = new MiniatureUniverseMenu(2, secondPlayer.player().getInventory(), source.tile());
            secondPlayer.player().containerMenu = localMenu;
            Session local = new Session(source.tile(), secondPlayer.player(), localMenu, secondPlayer.packets());
            localMenu.broadcastChanges();
            helper.assertTrue(remote.player() != local.player() && !remote.player().getUUID().equals(local.player().getUUID())
                            && remote.player().getInventory() != local.player().getInventory()
                            && remote.player().containerMenu == remote.menu() && local.player().containerMenu == local.menu()
                            && remote.menu().stillValid(remote.player()) && local.menu().stillValid(local.player()),
                    "Concurrent fixture did not retain two independently authorized players and open menus");
            remote.menu().setCarried(new ItemStack(Items.FURNACE, 2));
            local.menu().setCarried(new ItemStack(Items.FURNACE));
            remote.player().getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
            local.player().getInventory().setItem(9, new ItemStack(Items.GOLD_INGOT, 11));
            var stale = command(remote, 1, Action.INSERT_MACHINE, 1, -1);
            helper.assertTrue(remote.player().distanceToSqr(source.tile().getBlockPos().getCenter()) > 64.0,
                    "Remote fixture accidentally remained within local editing distance");

            assertResult(helper, execute(local, 1, Action.INSERT_MACHINE, 1, -1), Result.SUCCESS);
            Assets before = assets(remote);
            Assets localBeforeConflict = assets(local);
            var rejected = remote.menu().handleCommand(remote.player(), stale);
            assertResult(helper, rejected, Result.STALE_REVISION);
            assertAssets(helper, remote, before);
            assertAssets(helper, local, localBeforeConflict);
            helper.assertTrue(rejected.revision() == 1 && rejected.snapshot().cells().getFirst().machineCount() == 1,
                    "Remote conflict did not return the locally committed machine and revision");
            assertResult(helper, execute(remote, 2, Action.INSERT_MACHINE, 1, -1), Result.SUCCESS);
            local.menu().broadcastChanges();
            helper.assertTrue(machineCount(source.tile()) == 2 && local.menu().revision() == 2
                            && local.menu().snapshot().cells().getFirst().machineCount() == 2
                            && local.menu().getCarried().isEmpty() && remote.menu().getCarried().getCount() == 1
                            && remote.player().getInventory().getItem(9).getCount() == 7
                            && local.player().getInventory().getItem(9).getCount() == 11
                            && remote.menu().stillValid(remote.player()) && local.menu().stillValid(local.player()),
                    "Concurrent local and remote menus did not conserve their independent assets and shared machine inventory");
        } finally {
            source.battery().disconnect();
            networks.deleteNetwork(network);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void remoteTransfersConserveMachineInputAndExactOutputVariants(GameTestHelper helper) {
        ItemStack product = new ItemStack(Items.IRON_INGOT);
        product.set(DataComponents.CUSTOM_NAME, Component.literal("S10 remote product"));
        ResourceKey output = FactoryItemResources.keyOf(product, helper.getLevel().registryAccess());
        var source = source(helper, FactoryStorage.empty().withOutputs(
                new ResourceInventory(Map.of(output, BigInteger.valueOf(70)))));
        Session remote = remote(helper, source);
        remote.player().getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 19));
        remote.menu().setCarried(new ItemStack(Items.FURNACE, 5));
        assertResult(helper, execute(remote, 1, Action.INSERT_MACHINE, 2, -1), Result.SUCCESS);
        helper.assertTrue(machineCount(source.tile()) == 2 && remote.menu().getCarried().getCount() == 3,
                "Remote machine insertion did not conserve the five real furnaces");
        assertResult(helper, execute(remote, 2, Action.EXTRACT_MACHINE, 2, -1), Result.SUCCESS);
        helper.assertTrue(machineCount(source.tile()) == 0 && remote.menu().getCarried().getCount() == 5,
                "Remote machine extraction lost or duplicated real furnaces");

        remote.player().getInventory().setItem(1, remote.menu().getCarried().copy());
        remote.menu().setCarried(new ItemStack(Items.IRON_ORE, 10));
        assertResult(helper, execute(remote, 3, Action.INSERT_INPUT, 4, -1), Result.SUCCESS);
        helper.assertTrue(storage(source.tile()).inputs().amount(key(helper, Items.IRON_ORE)).equals(BigInteger.valueOf(4))
                        && remote.menu().getCarried().getCount() == 6,
                "Remote input insertion did not conserve the ten real ore items");
        var extraction = command(remote, 4, Action.EXTRACT_INPUT, 4, 0);
        assertResult(helper, remote.menu().handleCommand(remote.player(), extraction), Result.SUCCESS);
        Assets returned = assets(remote);
        assertResult(helper, remote.menu().handleCommand(remote.player(), extraction), Result.SUCCESS);
        assertAssets(helper, remote, returned);
        helper.assertTrue(storage(source.tile()).inputs().amounts().isEmpty() && remote.menu().getCarried().getCount() == 10,
                "Remote input replay materialized a second extraction");

        remote.player().getInventory().setItem(2, remote.menu().getCarried().copy());
        remote.menu().setCarried(ItemStack.EMPTY);
        assertResult(helper, execute(remote, 5, Action.EXTRACT_OUTPUT, 64, 0), Result.SUCCESS);
        helper.assertTrue(storage(source.tile()).outputs().amount(output).equals(BigInteger.valueOf(6))
                        && remote.menu().getCarried().getCount() == 64
                        && FactoryItemResources.keyOf(remote.menu().getCarried(), helper.getLevel().registryAccess()).equals(output)
                        && remote.player().getInventory().getItem(0).getCount() == 19
                        && remote.player().getInventory().getItem(1).getCount() == 5
                        && remote.player().getInventory().getItem(2).getCount() == 10,
                "Remote output extraction lost exact components, exceeded 70 products, or changed player assets");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void reopenedRemoteSessionsRejectOldCommandsEvenWhenTheContainerIdIsReused(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session first = remote(helper, source);
        first.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        var accepted = command(first, 1, Action.INSERT_MACHINE, 1, -1);
        var acknowledgement = first.menu().handleCommand(first.player(), accepted);
        assertResult(helper, acknowledgement, Result.SUCCESS);
        Assets transferred = assets(first);
        var duplicate = first.menu().handleCommand(first.player(), accepted);
        helper.assertTrue(duplicate.acknowledgedRevision() == acknowledgement.acknowledgedRevision()
                        && duplicate.acknowledgedCommandId() == acknowledgement.acknowledgedCommandId(),
                "Remote replay did not preserve its original acknowledgement");
        assertAssets(helper, first, transferred);
        assertResult(helper, execute(first, 1, Action.INSERT_MACHINE, 2, -1), Result.COMMAND_ID_REUSED);
        assertAssets(helper, first, transferred);

        first.player().closeContainer();
        MiniatureUniverseMenu reopened = new MiniatureUniverseMenu(first.menu().containerId,
                first.player().getInventory(), source.tile(), source.access(), source.endpoint().key());
        first.player().containerMenu = reopened;
        reopened.broadcastChanges();
        Session second = new Session(source.tile(), first.player(), reopened, first.packets());
        Assets before = assets(second);
        helper.assertTrue(!reopened.sessionId().equals(first.menu().sessionId()), "A new remote menu reused the previous session ID");
        assertResult(helper, reopened.handleCommand(first.player(), accepted), Result.INVALID_SESSION);
        assertAssets(helper, second, before);
        assertResult(helper, first.menu().handleCommand(first.player(), accepted), Result.INVALID_ACCESS);
        helper.assertTrue(machineCount(source.tile()) == 1 && inventoryCount(first.player(), Items.FURNACE) == 2,
                "Remote close or old-session replay lost or duplicated the remaining real machines");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void unbindingAndSelectingAnotherEndpointInvalidateAnOpenRemoteMenu(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session remote = remote(helper, source);
        remote.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        remote.player().getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
        source.access().unbindBattery(remote.player());
        assertInvalidAccess(helper, remote);

        helper.assertTrue(source.access().bindBattery(remote.player(), GlobalPos.of(
                        helper.getLevel().dimension(), source.battery().getBlockPos())), "Could not restore the test binding");
        helper.assertTrue(source.access().selectEndpoint(remote.player(), source.endpoint().key(),
                        source.battery().getPluginDirectoryRevision()), "Could not restore the selected miniature endpoint");
        assertInvalidAccess(helper, remote);
        remote = reopen(helper, source, remote);
        remote.menu().clicked(27, 0, ClickType.PICKUP, remote.player());
        QuantumPluginEndpoint other = source.battery().getEntanglementEndpoints().stream()
                .filter(endpoint -> !endpoint.key().equals(source.endpoint().key())).findFirst().orElseThrow();
        helper.assertTrue(source.access().selectEndpoint(remote.player(), other.key(),
                        source.battery().getPluginDirectoryRevision()), "Could not select the other battery endpoint");
        assertInvalidAccess(helper, remote);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void sameTickRebindingAndReselectionCannotReviveOldRemoteSessions(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session current = remote(helper, source);
        current.menu().setCarried(new ItemStack(Items.FURNACE, 4));
        current.player().getInventory().setItem(9, new ItemStack(Items.DIAMOND, 7));
        QuantumPluginEndpoint other = source.battery().getEntanglementEndpoints().stream()
                .filter(endpoint -> !endpoint.key().equals(source.endpoint().key())).findFirst().orElseThrow();

        for (boolean rebind : List.of(true, false)) {
            long tick = helper.getLevel().getGameTime();
            Assets before = assets(current);
            var pending = command(current, 2, Action.INSERT_MACHINE, 1, -1);
            // No menu validity check observes the intermediate unbound or differently selected state.
            // Both changes complete in one tick before the delayed command is delivered.
            if (rebind) {
                source.access().unbindBattery(current.player());
                helper.assertTrue(source.access().bindBattery(current.player(), GlobalPos.of(
                                helper.getLevel().dimension(), source.battery().getBlockPos())),
                        "Could not rebind the same source battery during the authorization cycle");
            } else {
                helper.assertTrue(source.access().selectEndpoint(current.player(), other.key(),
                                source.battery().getPluginDirectoryRevision()),
                        "Could not select another endpoint during the authorization cycle");
            }
            helper.assertTrue(source.access().selectEndpoint(current.player(), source.endpoint().key(),
                            source.battery().getPluginDirectoryRevision()),
                    "Could not restore the original selected endpoint during the authorization cycle");
            helper.assertTrue(helper.getLevel().getGameTime() == tick, "Authorization cycle crossed a server tick");
            assertResult(helper, current.menu().handleCommand(current.player(), pending), Result.INVALID_ACCESS);
            assertAssets(helper, current, before);

            Session renewed = reopen(helper, source, current);
            renewed.menu().clicked(27, 0, ClickType.PICKUP, renewed.player());
            assertResult(helper, execute(renewed, 1, Action.INSERT_MACHINE, 1, -1), Result.SUCCESS);
            helper.assertTrue(machineCount(source.tile()) + renewed.menu().getCarried().getCount()
                            + inventoryCount(renewed.player(), Items.FURNACE) == 4
                            && renewed.player().getInventory().getItem(9).getCount() == 7,
                    "Authorization renewal lost machines, duplicated the old command, or changed unrelated inventory");
            current = renewed;
        }
        helper.assertTrue(machineCount(source.tile()) == 2, "Only the two renewed sessions should have inserted a machine");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void accessDistanceAndSpectatorChangesRejectTransfersWithoutTouchingTheCursor(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session remote = remote(helper, source);
        remote.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        BlockPos access = source.access().getBlockPos();
        remote.player().setPos(access.getX() + 9.0, access.getY() + 0.5, access.getZ() + 0.5);
        assertInvalidAccess(helper, remote);
        position(remote.player(), access);
        helper.assertTrue(remote.menu().stillValid(remote.player()), "Returning to the access controller did not restore valid distance");
        remote.player().setGameMode(GameType.SPECTATOR);
        assertInvalidAccess(helper, remote);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void batteryAndAccessOwnershipAreRecheckedForEveryRemoteCommand(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        Session remote = remote(helper, source);
        remote.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        source.battery().setOwnerUUID(UUID.randomUUID());
        assertInvalidAccess(helper, remote);
        source.battery().setOwnerUUID(remote.player().getUUID());
        helper.assertTrue(remote.menu().stillValid(remote.player()), "Restored source permission did not resolve the live menu");
        source.access().setOwner(UUID.randomUUID());
        assertInvalidAccess(helper, remote);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void removedAndReplacedSourceEntitiesRejectRetainedRemoteMenus(GameTestHelper helper) {
        assertEntityReplacement(helper, true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void removedAndReplacedAccessEntitiesRejectRetainedRemoteMenus(GameTestHelper helper) {
        assertEntityReplacement(helper, false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement", timeoutTicks = 100)
    public static void crossDimensionOpeningAndCompilationKeepTheSourceFactoryIdentity(GameTestHelper helper) {
        FactoryStorage line = FactoryStorage.empty()
                .insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), helper.getLevel().registryAccess()).storage()
                .withConfiguration(CELL, new FactoryCell.Marker(key(helper, Items.IRON_ORE)))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
        var source = source(helper, line);
        ServerLevel accessLevel = helper.getLevel().getServer().getLevel(
                helper.getLevel().dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        helper.assertTrue(accessLevel != null && accessLevel != helper.getLevel(), "Cross-dimension test level is unavailable");
        Map<BlockPos, SavedBlock> previous = new LinkedHashMap<>();
        Session remote = null;
        try {
            BlockPos testOrigin = helper.absolutePos(new BlockPos(5, 0, 5));
            // GameTests can start below y=0, which is outside the Nether's build height.
            // Retain each test's isolated horizontal coordinates with a valid destination height.
            BlockPos accessPos = new BlockPos(testOrigin.getX(), 96, testOrigin.getZ());
            TileQuantumEntanglementController access = placeAccessInLevel(helper, accessLevel, accessPos, previous);
            Session player = player(accessLevel, source.tile(), accessPos);
            source.battery().setOwnerUUID(player.player().getUUID());
            helper.assertTrue(access.bindBattery(player.player(), GlobalPos.of(helper.getLevel().dimension(), source.battery().getBlockPos()))
                            && access.selectEndpoint(player.player(), source.endpoint().key(), source.battery().getPluginDirectoryRevision()),
                    "Real cross-dimension binding did not select the miniature endpoint");
            helper.assertTrue(access.openSelectedPluginMenu(player.player()), "The selected miniature endpoint did not open remotely");
            helper.assertTrue(player.player().containerMenu instanceof MiniatureUniverseMenu,
                    "Cross-dimension opening produced the wrong registered menu");
            var opened = (MiniatureUniverseMenu) player.player().containerMenu;
            remote = new Session(source.tile(), player.player(), opened, player.packets());
            helper.assertTrue(opened.stillValid(player.player()) && opened.getPluginPos().equals(source.tile().getBlockPos())
                            && opened.slots.size() == 36 && player.player().level() != source.tile().getLevel(),
                    "Cross-dimension menu lost the source position, access validity, or player inventory");
            List<AdvancedOpenScreenPayload> openings = player.packets().stream()
                    .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                    .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                    .filter(AdvancedOpenScreenPayload.class::isInstance).map(AdvancedOpenScreenPayload.class::cast).toList();
            helper.assertTrue(openings.size() == 1 && openings.getFirst().windowId() == opened.containerId
                            && openings.getFirst().menuType() == opened.getType()
                            && openings.getFirst().additionalData().length < 1024,
                    "Remote opening did not send one bounded registered-menu payload");
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(openings.getFirst().additionalData()),
                    player.player().registryAccess(), ConnectionType.NEOFORGE);
            try {
                var decoded = new MiniatureUniverseMenu(opened.containerId, player.player().getInventory(), buffer);
                helper.assertTrue(!buffer.isReadable() && decoded.sessionId().equals(opened.sessionId())
                                && decoded.getPluginPos().equals(opened.getPluginPos()) && decoded.isRemoteAccess()
                                && decoded.getPluginDimension().equals(helper.getLevel().dimension()),
                        "Remote opening decoder did not consume and preserve the source and session identity");
            } finally {
                buffer.release();
            }
            opened.broadcastChanges();
            Assets before = assets(remote);
            assertResult(helper, execute(remote, 1, Action.COMPILE, 1, -1), Result.SUCCESS);
            helper.assertTrue(source.tile().compilationFor(source.tile().factoryState()).successful()
                            && opened.snapshot().compilation().status() == CompilationPreview.Status.READY
                            && opened.snapshot().compilation().outputs().getFirst().resource().equals(key(helper, Items.IRON_INGOT)),
                    "Remote compilation was not committed and synchronized for the source factory");
            assertAssets(helper, remote, before);
        } finally {
            if (remote != null) remote.player().closeContainer();
            for (var saved : previous.entrySet()) {
                accessLevel.getChunkAt(saved.getKey());
                accessLevel.removeBlockEntity(saved.getKey());
                accessLevel.setBlock(saved.getKey(), saved.getValue().state(), 3);
                if (saved.getValue().entity() != null) {
                    BlockEntity restored = BlockEntity.loadStatic(saved.getKey(), saved.getValue().state(),
                            saved.getValue().entity(), accessLevel.registryAccess());
                    if (restored != null) accessLevel.setBlockEntity(restored);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void remoteUpgradePaysOnlyTheAccessingPlayerAndRevocationRejectsFurtherPayment(GameTestHelper helper) {
        var source = source(helper, FactoryStorage.empty());
        var remote = remote(helper, source);
        remote.player().getInventory().setItem(9, new ItemStack(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get(), 32));
        remote.player().getInventory().setItem(10, new ItemStack(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get(), 16));
        remote.player().getInventory().setItem(11, new ItemStack(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(), 4));
        remote.menu().setCarried(new ItemStack(Items.DIAMOND, 7));
        var request = command(remote, 1, Action.UPGRADE, 1, -1);
        assertResult(helper, remote.menu().handleCommand(remote.player(), request), Result.SUCCESS);
        assertResult(helper, remote.menu().handleCommand(remote.player(), request), Result.SUCCESS);
        helper.assertTrue(storage(source.tile()).layout().size() == 5
                && remote.player().getInventory().getItem(9).getCount() == 24
                && remote.player().getInventory().getItem(10).getCount() == 12
                && remote.player().getInventory().getItem(11).getCount() == 3 && remote.menu().getCarried().getCount() == 7,
                "Remote upgrade used the wrong inventory, duplicated payment or changed the cursor");
        source.access().unbindBattery(remote.player());
        var before = source.tile().factoryState();
        assertResult(helper, execute(remote, 2, Action.UPGRADE, 1, -1), Result.INVALID_ACCESS);
        helper.assertTrue(source.tile().factoryState() == before && remote.player().getInventory().getItem(9).getCount() == 24,
                "Revoked remote upgrade consumed materials");
        helper.succeed();
    }

    private static MiniatureEntanglementResourceGameTests.Fixture source(GameTestHelper helper, FactoryStorage storage) {
        return MiniatureEntanglementResourceGameTests.fixture(helper, storage, BigInteger.ZERO);
    }

    private static Session remote(GameTestHelper helper, MiniatureEntanglementResourceGameTests.Fixture source) {
        Session player = player(helper.getLevel(), source.tile(), source.access().getBlockPos());
        source.battery().setOwnerUUID(player.player().getUUID());
        MiniatureUniverseMenu menu = new MiniatureUniverseMenu(1, player.player().getInventory(),
                source.tile(), source.access(), source.endpoint().key());
        player.player().containerMenu = menu;
        helper.assertTrue(menu.stillValid(player.player()), "Real assembled source and access controller did not authorize the remote menu");
        menu.broadcastChanges();
        return new Session(source.tile(), player.player(), menu, player.packets());
    }

    private static Session reopen(GameTestHelper helper, MiniatureEntanglementResourceGameTests.Fixture source, Session previous) {
        helper.assertTrue(source.access().openSelectedPluginMenu(previous.player()), "Restored authorization did not open a new remote session");
        helper.assertTrue(previous.player().containerMenu instanceof MiniatureUniverseMenu,
                "Restored authorization opened the wrong menu type");
        var menu = (MiniatureUniverseMenu) previous.player().containerMenu;
        helper.assertTrue(menu.stillValid(previous.player()) && !menu.sessionId().equals(previous.menu().sessionId()),
                "Restored authorization reused the old remote session");
        menu.broadcastChanges();
        return new Session(source.tile(), previous.player(), menu, previous.packets());
    }

    private static Session player(ServerLevel level, TileMiniatureUniverse tile, BlockPos position) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "s10-remote-editor");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override
            public BlockPos adjustSpawnLocation(ServerLevel target, BlockPos pos) { return pos; }
        };
        List<Packet<?>> sent = new ArrayList<>();
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) { sent.add(packet); }
            @Override
            public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
        };
        player.setGameMode(GameType.SURVIVAL);
        position(player, position);
        return new Session(tile, player, null, sent);
    }

    private static void position(ServerPlayer player, BlockPos position) {
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
    }

    private static void assertEntityReplacement(GameTestHelper helper, boolean replaceSource) {
        var source = source(helper, FactoryStorage.empty());
        Session remote = remote(helper, source);
        remote.menu().setCarried(new ItemStack(Items.FURNACE, 3));
        BlockEntity original = replaceSource ? source.tile() : source.access();
        CompoundTag saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
        original.setRemoved();
        assertInvalidAccess(helper, remote);
        original.clearRemoved();
        BlockEntity replacement = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved,
                helper.getLevel().registryAccess());
        helper.assertTrue(replacement != null && replacement != original, "Fixture did not create a distinct replacement block entity");
        helper.getLevel().removeBlockEntity(original.getBlockPos());
        helper.getLevel().setBlockEntity(replacement);
        if (replacement instanceof TileQuantumEntanglementController controller) controller.validateStructure();
        CompoundTag replacementBefore = replacement.saveWithFullMetadata(helper.getLevel().registryAccess());
        assertInvalidAccess(helper, remote);
        helper.assertTrue(replacementBefore.equals(replacement.saveWithFullMetadata(helper.getLevel().registryAccess())),
                "A retained remote menu mutated the replacement entity at the same coordinates");
    }

    private static void assertInvalidAccess(GameTestHelper helper, Session session) {
        Assets before = assets(session);
        helper.assertTrue(!session.menu().stillValid(session.player()), "Revoked remote access retained a valid menu");
        var response = session.menu().handleCommand(session.player(), command(session, 1, Action.INSERT_MACHINE, 1, -1));
        assertResult(helper, response, Result.INVALID_ACCESS);
        helper.assertTrue(response.snapshot().cells().isEmpty() && response.snapshot().inputs().isEmpty()
                        && response.snapshot().outputs().isEmpty(), "Revoked remote access exposed retained source assets");
        session.menu().clicked(0, 0, ClickType.PICKUP, session.player());
        session.menu().quickMoveStack(session.player(), 0);
        assertAssets(helper, session, before);
    }

    private static TileQuantumEntanglementController placeAccessInLevel(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                                                       Map<BlockPos, SavedBlock> previous) {
        BlockState controller = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get().defaultBlockState()
                .setValue(QuantumEntanglementControllerBlock.FACING, Direction.NORTH);
        replaceBlock(helper, level, origin, controller, previous);
        for (var cell : QuantumEntanglementTemplate.cells()) {
            replaceBlock(helper, level, QuantumEntanglementStructure.worldPos(origin, Direction.NORTH, cell.offset()),
                    QuantumEntanglementAssembler.expectedState(cell, controller), previous);
        }
        BlockEntity entity = level.getBlockEntity(origin);
        helper.assertTrue(entity instanceof TileQuantumEntanglementController,
                "Cross-dimension access controller was not created at " + origin);
        var access = (TileQuantumEntanglementController) entity;
        access.validateStructure();
        helper.assertTrue(access.isFormed(), "Cross-dimension access controller did not form");
        return access;
    }

    private static void replaceBlock(GameTestHelper helper, ServerLevel level, BlockPos pos, BlockState state,
                                     Map<BlockPos, SavedBlock> previous) {
        helper.assertTrue(level.isInWorldBounds(pos), "Cross-dimension fixture position is outside world bounds: " + pos);
        // Load before taking the restoration snapshot; an unloaded chunk must not appear to have no block entity.
        level.getChunkAt(pos);
        helper.assertTrue(level.isLoaded(pos), "Cross-dimension fixture chunk was not loaded: " + pos);
        BlockEntity entity = level.getBlockEntity(pos);
        previous.put(pos.immutable(), new SavedBlock(level.getBlockState(pos),
                entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
        level.setBlock(pos, state, 3);
        helper.assertTrue(level.getBlockState(pos).equals(state), "Could not place cross-dimension fixture block at " + pos);
    }

    private static MiniatureFactoryCommandPayload command(Session session, long id, Action action, int quantity, int index) {
        return new MiniatureFactoryCommandPayload(session.menu().containerId, session.menu().sessionId(),
                session.tile().getBlockPos(), session.menu().revision(), id, action, 0, 0, BigInteger.valueOf(quantity),
                GridDirection.RIGHT, action.requiresSnapshot() || action.isControl() ? session.menu().snapshotId()
                : MiniatureFactoryCommandPayload.NO_SNAPSHOT, index);
    }

    private static MiniatureFactorySnapshotPayload execute(Session session, long id, Action action, int quantity, int index) {
        return session.menu().handleCommand(session.player(), command(session, id, action, quantity, index));
    }

    private static FactoryStorage storage(TileMiniatureUniverse tile) { return tile.factoryState().storage().orElseThrow(); }
    private static int machineCount(TileMiniatureUniverse tile) { return storage(tile).machines().entry(CELL).map(entry -> entry.count()).orElse(0); }
    private static ResourceKey key(GameTestHelper helper, Item item) {
        return FactoryItemResources.keyOf(new ItemStack(item), helper.getLevel().registryAccess());
    }

    private static int inventoryCount(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) count += player.getInventory().getItem(slot).getCount();
        }
        return count;
    }

    private static Assets assets(Session session) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int slot = 0; slot < session.player().getInventory().getContainerSize(); slot++) {
            inventory.add(session.player().getInventory().getItem(slot).copy());
        }
        return new Assets(session.tile().factoryState().save(), session.menu().getCarried().copy(), inventory);
    }

    private static void assertAssets(GameTestHelper helper, Session session, Assets before) {
        helper.assertTrue(before.state().equals(session.tile().factoryState().save())
                        && ItemStack.matches(before.cursor(), session.menu().getCarried()),
                "Rejected, repeated, or compile-only remote command changed factory or cursor assets");
        for (int slot = 0; slot < before.inventory().size(); slot++) {
            helper.assertTrue(ItemStack.matches(before.inventory().get(slot), session.player().getInventory().getItem(slot)),
                    "Remote command changed player inventory outside the carried stack");
        }
    }

    private static void assertResult(GameTestHelper helper, MiniatureFactorySnapshotPayload response, Result expected) {
        helper.assertTrue(response.result() == expected, "Expected " + expected + " but received " + response.result());
    }

    private record Session(TileMiniatureUniverse tile, ServerPlayer player, MiniatureUniverseMenu menu, List<Packet<?>> packets) {}
    private record Assets(CompoundTag state, ItemStack cursor, List<ItemStack> inventory) {}
    private record SavedBlock(BlockState state, CompoundTag entity) {}

    private MiniatureEntanglementMenuGameTests() {}
}
