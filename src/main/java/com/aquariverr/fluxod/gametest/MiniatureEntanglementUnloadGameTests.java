package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import sonar.fluxnetworks.api.FluxConstants;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** The source is outside GameTestRunner's forced arena; only this test's region ticket holds it. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureEntanglementUnloadGameTests {
    private static final TicketType<ChunkPos> SOURCE_TICKET = TicketType.create(
            "flux_overdrive_s10_unload_source", Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 3;

    @GameTest(template = "empty", batch = "miniature_entanglement", timeoutTicks = 600)
    public static void ordinarySourceChunkUnloadRevokesRemoteAccessWithoutReloadingIt(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer() instanceof GameTestServer,
                "The remote unload fixture may only create its isolated source in the standalone GameTestServer");
        ServerLevel sourceLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(sourceLevel != null && sourceLevel != helper.getLevel(),
                "The remote unload fixture needs a Nether source outside the ordinary GameTest arena");
        // Above the Nether roof and far from the test arenas and all mock players. Other tests cannot
        // retain these chunks through the runner's forced arena or a neighboring structure ticket.
        BlockPos sourcePos = new BlockPos(16_384 + helper.absolutePos(BlockPos.ZERO).getX(), 160,
                16_384 + helper.absolutePos(BlockPos.ZERO).getZ());
        ChunkPos ticketCenter = new ChunkPos(sourcePos);
        sourceLevel.getChunkSource().addRegionTicket(SOURCE_TICKET, ticketCenter, TICKET_DISTANCE, ticketCenter);
        Runnable release = () -> sourceLevel.getChunkSource().removeRegionTicket(
                SOURCE_TICKET, ticketCenter, TICKET_DISTANCE, ticketCenter);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { release.run(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { release.run(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
        });

        Source source = buildSource(helper, sourceLevel, sourcePos);
        TileQuantumEntanglementController access = MiniatureEntanglementResourceGameTests.buildAccess(
                helper, source.battery(), source.endpoint());
        ServerPlayer player = player(helper.getLevel(), access.getBlockPos());
        source.battery().setOwnerUUID(player.getUUID());
        helper.assertTrue(access.bindBattery(player, GlobalPos.of(sourceLevel.dimension(), sourcePos))
                        && access.selectEndpoint(player, source.endpoint().key(), source.battery().getPluginDirectoryRevision())
                        && access.openSelectedPluginMenu(player)
                        && player.containerMenu instanceof MiniatureUniverseMenu,
                "Loaded cross-dimension source could not be bound, selected, and opened through the real remote adapter");
        MiniatureUniverseMenu menu = (MiniatureUniverseMenu) player.containerMenu;
        menu.broadcastChanges();
        menu.setCarried(new ItemStack(Items.FURNACE));
        helper.assertTrue(menu.stillValid(player) && menu.isRemoteAccess(), "Pre-unload remote menu was not valid");
        IItemHandler input = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                access.getBlockPos().east(), Direction.EAST);
        IItemHandler output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                access.getBlockPos().west(), Direction.WEST);
        IFluidHandler fluidOutput = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                access.getBlockPos().west(), Direction.WEST);
        helper.assertTrue(input != null && output != null && fluidOutput != null
                        && input.insertItem(0, new ItemStack(Items.IRON_ORE), true).isEmpty()
                        && output.extractItem(FactoryLimits.MAX_BOUNDARY_RESOURCES, 1, true).getCount() == 1
                        && fluidOutput.drain(1, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1,
                "Held proxies did not have a working source before its chunk was released");
        CompoundTag assets = source.tile().factoryState().save();
        BigInteger energy = source.battery().getStoredEnergy();
        ChunkPos batteryChunk = new ChunkPos(source.battery().getBlockPos());
        ChunkPos pluginChunk = new ChunkPos(source.tile().getBlockPos());
        helper.assertTrue(!sourceLevel.getForcedChunks().contains(batteryChunk.toLong())
                        && !sourceLevel.getForcedChunks().contains(pluginChunk.toLong()),
                "GameTest or another fixture has a persistent forced ticket for the remote unload source");
        long[] rejectedCommands = {1};
        long[] lastUnloadLog = {Long.MIN_VALUE};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(readyForRelease(sourceLevel, batteryChunk)
                                && readyForRelease(sourceLevel, pluginChunk),
                        "Waiting for completed source generation before removing its only region ticket: "
                                + unloadDiagnostic(source, batteryChunk, pluginChunk)))
                .thenExecute(() -> {
                    FluxOverdrive.LOGGER.info("S10 source before releasing fixture ticket at test tick {}: {}",
                            helper.getTick(), unloadDiagnostic(source, batteryChunk, pluginChunk));
                    release.run();
                })
                .thenWaitUntil(() -> {
                    // Keep an actual open menu and pipe-style polling active during the transition.
                    // This mock player is not in PlayerList, so the fixture supplies its ordinary
                    // per-tick menu work explicitly. Loaded reads must not renew UNKNOWN tickets
                    // and keep the source alive after its last independent ticket is released.
                    menu.stillValid(player);
                    menu.broadcastChanges();
                    input.getSlots();
                    input.insertItem(0, new ItemStack(Items.IRON_ORE), true);
                    output.extractItem(FactoryLimits.MAX_BOUNDARY_RESOURCES, 1, true);
                    fluidOutput.drain(1, IFluidHandler.FluidAction.SIMULATE);
                    allowOrdinarySourceUnloadWork(sourceLevel);
                    boolean unloaded = source.tile().isRemoved() && source.battery().isRemoved()
                            && chunkAbsent(sourceLevel, batteryChunk) && chunkAbsent(sourceLevel, pluginChunk);
                    String diagnostic = unloadDiagnostic(source, batteryChunk, pluginChunk);
                    if (lastUnloadLog[0] == Long.MIN_VALUE || helper.getTick() - lastUnloadLog[0] >= 100 || unloaded) {
                        lastUnloadLog[0] = helper.getTick();
                        FluxOverdrive.LOGGER.info("S10 actual source unload at test tick {}: {}", helper.getTick(), diagnostic);
                    }
                    helper.assertTrue(unloaded,
                            "Waiting for ordinary server chunk saving/unloading after releasing only the source ticket: " + diagnostic);
                })
                .thenExecuteFor(40, () -> {
                    helper.assertTrue(helper.getLevel().isLoaded(access.getBlockPos())
                                    && helper.getLevel().getBlockEntity(access.getBlockPos()) == access,
                            "The access facility unloaded together with the source and invalidated this test");
                    helper.assertTrue(input.getSlots() == 0 && output.getSlots() == 0
                                    && input.insertItem(0, new ItemStack(Items.IRON_ORE), false).getCount() == 1
                                    && output.extractItem(FactoryLimits.MAX_BOUNDARY_RESOURCES, 1, false).isEmpty()
                                    && fluidOutput.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                            "Held resource proxy transferred assets after actual source chunk unload");
                    helper.assertTrue(!menu.stillValid(player), "Open remote menu survived actual source chunk unload");
                    var command = new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(), source.tile().getBlockPos(),
                            menu.revision(), rejectedCommands[0]++, MiniatureFactoryCommandPayload.Action.INSERT_MACHINE,
                            0, 0, BigInteger.ONE, GridDirection.RIGHT, MiniatureFactoryCommandPayload.NO_SNAPSHOT, -1);
                    helper.assertTrue(menu.handleCommand(player, command).result() == MiniatureFactorySnapshotPayload.Result.INVALID_ACCESS
                                    && menu.getCarried().is(Items.FURNACE) && menu.getCarried().getCount() == 1,
                            "Remote command after chunk unload consumed its cursor or bypassed authorization");
                    menu.broadcastChanges();
                    helper.assertTrue(!access.openSelectedPluginMenu(player)
                                    && assets.equals(source.tile().factoryState().save())
                                    && energy.equals(source.battery().getStoredEnergy())
                                    && chunkAbsent(sourceLevel, batteryChunk) && chunkAbsent(sourceLevel, pluginChunk),
                            "Remote capability, menu validation, reopening, or synchronization reloaded the source or changed its assets");
                })
                .thenExecute(() -> {
                    player.closeContainer();
                    FluxOverdrive.LOGGER.info("S10 source stayed unloaded through 40 ticks of remote access at test tick {}: {}",
                            helper.getTick(), unloadDiagnostic(source, batteryChunk, pluginChunk));
                })
                .thenSucceed();
    }

    private static boolean readyForRelease(ServerLevel level, ChunkPos pos) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.toLong());
        return holder != null && holder.getGenerationRefCount() == 0 && holder.isReadyForSaving();
    }

    static void allowOrdinarySourceUnloadWork(ServerLevel level) {
        // GameTestServer skips real-time waits. A generation-heavy test tick can leave the world's
        // hasTimeLeft supplier false for the entire fixture, starving ChunkMap's small unload queue.
        // Give the normal save/unload scheduler 2 ms, without another chunk-source/world tick:
        // ticking ServerChunkCache here would also purge tickets twice and could hide a remote
        // accessor that accidentally renews UNKNOWN tickets. No ticket is cleared by this helper.
        long deadline = System.nanoTime() + 2_000_000L;
        try {
            var method = ChunkMap.class.getDeclaredMethod("tick", BooleanSupplier.class);
            method.setAccessible(true);
            method.invoke(level.getChunkSource().chunkMap, (BooleanSupplier) () -> System.nanoTime() < deadline);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot provide the standalone source's ordinary unload scheduling budget", exception);
        }
    }

    private static String unloadDiagnostic(Source source, ChunkPos battery, ChunkPos plugin) {
        ServerLevel level = (ServerLevel) source.battery().getLevel();
        return "batteryRemoved=" + source.battery().isRemoved() + ", pluginRemoved=" + source.tile().isRemoved()
                + ", noSave=" + level.noSave() + ", battery=" + chunkDiagnostic(level, battery)
                + ", plugin=" + chunkDiagnostic(level, plugin);
    }

    private static String chunkDiagnostic(ServerLevel level, ChunkPos pos) {
        var chunkMap = level.getChunkSource().chunkMap;
        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(pos.toLong());
        try {
            var field = DistanceManager.class.getDeclaredField("tickets");
            field.setAccessible(true);
            var tickets = (Map<?, ?>) field.get(chunkMap.getDistanceManager());
            StringBuilder nearby = new StringBuilder();
            for (int x = pos.x - TICKET_DISTANCE; x <= pos.x + TICKET_DISTANCE; x++) {
                for (int z = pos.z - TICKET_DISTANCE; z <= pos.z + TICKET_DISTANCE; z++) {
                    Object entries = tickets.get(ChunkPos.asLong(x, z));
                    if (entries != null) nearby.append('[').append(x).append(',').append(z).append(':').append(entries).append(']');
                }
            }
            return pos + "{loaded=" + !chunkAbsent(level, pos) + ", holder="
                    + (holder == null ? "none" : "level=" + holder.getTicketLevel() + "/refs="
                    + holder.getGenerationRefCount() + "/saveReady=" + holder.isReadyForSaving())
                    + ", nearbyTickets=" + nearby + "}";
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the standalone source's unload tickets", exception);
        }
    }

    private static boolean chunkAbsent(ServerLevel level, ChunkPos chunk) {
        // These are non-loading queries. getBlockEntity/getChunk are intentionally never used on
        // the remote dimension after release, including inside the success assertions.
        return !level.getChunkSource().hasChunk(chunk.x, chunk.z)
                && level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null;
    }

    private static Source buildSource(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        for (int x = (pos.getX() - 16) >> 4; x <= (pos.getX() + 16) >> 4; x++) {
            for (int z = (pos.getZ() - 16) >> 4; z <= (pos.getZ() + 16) >> 4; z++) level.getChunk(x, z);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        var battery = (TileQuantumBatteryController) level.getBlockEntity(pos);
        helper.assertTrue(battery != null, "Isolated source battery was not placed");
        CompoundTag core = new CompoundTag();
        battery.writeCustomTag(core, FluxConstants.NBT_SAVE_ALL);
        core.putInt("CoreLevel", 3);
        battery.readCustomTag(core, FluxConstants.NBT_SAVE_ALL);
        int pluginRadius = QuantumBatteryStructure.stabilizerSlotRadius(3);
        BlockPos pluginPos = pos.offset(-pluginRadius, 0, 0);
        for (int tier = 1; tier <= 3; tier++) {
            int radius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                var offset = cell.offset();
                Block block = QuantumBatteryAssembler.expectedBlock(tier, cell.type());
                if (offset.equals(new QuantumBatteryStructure.Offset(radius, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
                } else if (tier == 3 && offset.equals(new QuantumBatteryStructure.Offset(-pluginRadius, 0, 0))) {
                    block = RegistryBlocks.MINIATURE_UNIVERSE.get();
                }
                level.setBlockAndUpdate(pos.offset(offset.x(), offset.y(), offset.z()), block.defaultBlockState());
            }
        }
        var tile = (TileMiniatureUniverse) level.getBlockEntity(pluginPos);
        helper.assertTrue(tile != null, "Isolated source miniature plugin was not placed");
        ResourceKey ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), level.registryAccess());
        ResourceKey ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), level.registryAccess());
        ResourceKey water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), level.registryAccess());
        FactoryStorage storage = FactoryStorage.empty().withOutputs(new ResourceInventory(
                Map.of(ingot, BigInteger.valueOf(20), water, BigInteger.valueOf(2_000))));
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, level.registryAccess()).save());
        tile.loadAdditional(wrapper, level.registryAccess());
        MiniatureEntanglementResourceGameTests.allowInputs(helper, tile, ore);
        battery.validateStructure();
        helper.assertTrue(battery.isOperational() && battery.getStructureTier() == 3
                        && battery.areStructureChunksLoadedForRender(), "Isolated source Tier 3 battery did not form in loaded chunks");
        QuantumPluginEndpoint endpoint = battery.getEntanglementEndpoints().stream()
                .filter(candidate -> candidate.absolutePos().equals(pluginPos)).findFirst()
                .orElseThrow(() -> new AssertionError("Isolated source did not advertise its miniature endpoint"));
        return new Source(battery, tile, endpoint);
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "s10-unload-editor");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override public BlockPos adjustSpawnLocation(ServerLevel target, BlockPos spawn) { return spawn; }
        };
        new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return player;
    }

    private record Source(TileQuantumBatteryController battery, TileMiniatureUniverse tile, QuantumPluginEndpoint endpoint) {}
    private MiniatureEntanglementUnloadGameTests() {}
}
