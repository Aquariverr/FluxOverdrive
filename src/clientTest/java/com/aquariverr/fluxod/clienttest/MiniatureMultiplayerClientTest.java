package com.aquariverr.fluxod.clienttest;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Two independent Minecraft clients, loopback TCP, actual Screen clicks and authoritative receipts. */
final class MiniatureMultiplayerClientTest {
    private static final BlockPos POS = new BlockPos(0, 90, 0);
    private final Minecraft mc = Minecraft.getInstance();
    private final String role = System.getProperty("flux_overdrive.clientTestPeerRole");
    private final boolean host = role.equals("host");
    private final Path root = Path.of(System.getProperty("flux_overdrive.clientTestPeerDirectory"));
    private final Path output = Path.of(System.getProperty("flux_overdrive.clientTestOutput"));
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> passed = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();
    private Step current;
    private long began;
    private boolean initialized, finished;
    private String capture;
    private int frames;
    private Result firstResult;
    private CompletableFuture<Void> barrier;

    MiniatureMultiplayerClientTest() {
        if (!host && !role.equals("guest")) throw new IllegalArgumentException("Unknown peer role");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::render);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        try {
            // Login/tutorial and recipe unlock toasts obscure the command receipt in captures.
            // Suppress only this isolated harness's transient notifications, not editor feedback.
            mc.getToasts().clear();
            if (!initialized) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                initialized = true;
                initialize();
            }
            if (current == null) {
                current = steps.pollFirst();
                if (current == null) { finish(null); return; }
                began = System.nanoTime(); current.start().run();
            }
            if (current.ready().getAsBoolean()) { passed.add(current.name()); current = null; }
            else if (System.nanoTime() - began > 180_000_000_000L) throw new IllegalStateException("Timed out " + current.name() + "; screen=" + mc.screen);
        } catch (Throwable failure) { finish(failure); }
    }

    private void initialize() throws Exception {
        Files.createDirectories(root); Files.createDirectories(output);
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
        mc.options.framerateLimit().set(60); mc.options.guiScale().set(3); mc.resizeDisplay();
        mc.options.onboardingAccessibilityFinished(); mc.getTutorial().setStep(TutorialSteps.NONE); mc.getToasts().clear();
        if (host) {
            steps.add(new Step("host_create_fresh_world", () -> mc.createWorldOpenFlows().createFreshLevel("s12-dual-" + System.currentTimeMillis(),
                    new LevelSettings("S12 Two Client Verification", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen),
                    () -> mc.player != null && mc.getSingleplayerServer() != null));
            serverStep("listen_on_loopback_only", server -> {
                try {
                    int port;
                    InetAddress loopback = InetAddress.getByName("127.0.0.1");
                    try (var socket = new ServerSocket(0, 1, loopback)) { port = socket.getLocalPort(); }
                    server.setUsesAuthentication(false);
                    server.getConnection().startTcpServerListener(loopback, port);
                    write("port", Integer.toString(port));
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            waitFor("both_real_players_connected", () -> mc.getSingleplayerServer().getPlayerCount() == 2);
            serverStep("open_independent_menus_and_inventories", server -> {
                var level = server.overworld();
                level.setBlock(POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
                level.setBlock(POS.offset(2, -1, 2), Blocks.STONE.defaultBlockState(), 3);
                var tile = (TileMiniatureUniverse) level.getBlockEntity(POS);
                var players = server.getPlayerList().getPlayers();
                require(players.size() == 2 && !players.get(0).getUUID().equals(players.get(1).getUUID()), "Clients did not have independent identities");
                for (var player : players) {
                    player.setGameMode(GameType.SURVIVAL);
                    player.teleportTo(2.5, 90, 2.5);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(9, new ItemStack(Items.FURNACE));
                    tile.openMenu(player);
                }
            });
        } else {
            waitFor("wait_for_loopback_server", () -> exists("port"));
            steps.add(new Step("connect_second_real_client", () -> {
                String address = "127.0.0.1:" + read("port");
                ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(address),
                        new ServerData("S12 isolated loopback test", address, ServerData.Type.OTHER), false, null);
            }, () -> {
                require(!(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen), "Loopback connection failed");
                return mc.player != null && mc.level != null;
            }));
        }
        waitFor("receive_original_authoritative_grid", () -> mc.screen instanceof MiniatureUniverseScreen screen
                && screen.getMenu().snapshotReady() && screen.getMenu().revision() == 0 && mc.player.getInventory().getItem(9).is(Items.FURNACE));
        capture("initial-independent-menu");
        steps.add(new Step("pick_up_real_machine_via_inventory_click", () -> {
            var slot = screen().getMenu().slots.stream().filter(s -> s.container == mc.player.getInventory() && s.getContainerSlot() == 9).findFirst().orElseThrow();
            click(slot.x + 8, slot.y + 8);
        }, () -> screen().getMenu().getCarried().is(Items.FURNACE)));
        action("mark_original_revision_ready", () -> write(role + "-ready", "0"));
        if (host) {
            waitFor("both_clients_hold_same_revision", () -> exists("guest-ready"));
            action("bound_network_scheduling_barrier", () -> barrier = mc.getSingleplayerServer().submit(() -> {
                // Keep both real requests based on the same displayed revision. Only this isolated
                // server tick waits, at most five seconds; the normal packet handlers execute afterward.
                write("go", "ready");
                long end = System.nanoTime() + 5_000_000_000L;
                while (!exists("host-clicked") || !exists("guest-clicked")) {
                    if (System.nanoTime() > end) throw new IllegalStateException("Peer did not click within the bounded barrier");
                    try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
            }));
        }
        waitFor("both_clients_dispatch_grid_clicks", () -> exists("go"));
        action("click_same_empty_cell_through_screen", () -> { click(53.5, 64.5); write(role + "-clicked", "sent"); });
        waitFor("receive_real_server_command_receipt", () -> screen().getMenu().lastAcknowledgedCommandId() >= 1);
        action("record_concurrent_result", () -> {
            firstResult = screen().getMenu().lastResult();
            require(firstResult == Result.SUCCESS || firstResult == Result.STALE_REVISION, "Unexpected concurrent result " + firstResult);
            write(role + "-result", firstResult.name());
        });
        capture("concurrent-command-result");
        if (host) {
            waitFor("both_network_receipts_recorded", () -> exists("guest-result"));
            action("verify_exactly_one_stale_request", () -> {
                barrier.join();
                require(!read("host-result").equals(read("guest-result")), "Both old-revision requests had the same outcome");
                write("checked", "one success and one stale revision");
            });
        }
        waitFor("concurrency_verified", () -> exists("checked"));
        steps.add(new Step("retry_losing_machine_in_another_cell", () -> {
            if (firstResult == Result.STALE_REVISION) click(80.5, 64.5);
        }, () -> screen().getMenu().getCarried().isEmpty() && screen().getMenu().snapshot().cells().stream().mapToInt(c -> c.machineCount()).sum() == 2));
        capture("both_clients_converge");
        action("mark_authoritative_convergence", () -> write(role + "-done", "two machines and empty cursor"));
        if (host) {
            waitFor("both_clients_finished_editing", () -> exists("guest-done"));
            serverStep("verify_both_real_inventories_and_factory_ledger", server -> {
                var tile = (TileMiniatureUniverse) server.overworld().getBlockEntity(POS);
                require(tile.factoryState().storage().orElseThrow().layout().machineCount() == 2 && server.getPlayerCount() == 2, "Wrong final machine or player count");
                for (var player : server.getPlayerList().getPlayers()) require(player.containerMenu.getCarried().isEmpty()
                        && player.getInventory().items.stream().noneMatch(s -> s.is(Items.FURNACE)), "Client retained a duplicate machine");
                write("verified", "all assets conserved");
            });
            waitFor("guest_report_saved", () -> exists("guest-finished"));
        } else waitFor("server_verified_both_clients", () -> exists("verified"));
    }

    private void capture(String name) { steps.add(new Step("capture_" + name, () -> { capture = name; frames = 0; }, () -> capture == null)); }
    private void render(RenderFrameEvent.Post event) {
        if (finished || capture == null || ++frames < 5) return;
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path path = output.resolve(role + "-" + capture + ".png"); image.writeToFile(path);
            screenshots.add(path.toAbsolutePath().toString()); capture = null;
        } catch (Throwable error) { finish(error); }
    }
    private void click(double x, double y) {
        var screen = screen(); x += screen.getGuiLeft(); y += screen.getGuiTop();
        screen.mouseMoved(x, y); screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
    }
    private MiniatureUniverseScreen screen() { require(mc.screen instanceof MiniatureUniverseScreen, "Editor is not open"); return (MiniatureUniverseScreen) mc.screen; }
    private void action(String name, Runnable action) { steps.add(new Step(name, action, () -> true)); }
    private void waitFor(String name, BooleanSupplier ready) { steps.add(new Step(name, () -> {}, ready)); }
    private void serverStep(String name, Consumer<MinecraftServer> action) {
        @SuppressWarnings("unchecked") CompletableFuture<Void>[] task = new CompletableFuture[1];
        steps.add(new Step(name, () -> task[0] = mc.getSingleplayerServer().submit(() -> action.accept(mc.getSingleplayerServer())), () -> {
            if (!task[0].isDone()) return false; task[0].join(); return true;
        }));
    }
    private boolean exists(String name) { return Files.isRegularFile(root.resolve(name + ".txt")); }
    private String read(String name) { try { return Files.readString(root.resolve(name + ".txt")); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void write(String name, String value) {
        try {
            Path temp = root.resolve(name + ".tmp"); Files.writeString(temp, value);
            Files.move(temp, root.resolve(name + ".txt"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void finish(Throwable failure) {
        if (finished) return; finished = true;
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failure == null ? "passed" : "failed"); report.put("role", role);
        report.put("scope", "two-independent-clients-loopback-tcp-screen-concurrency");
        report.put("steps", passed); report.put("screenshots", screenshots); report.put("firstResult", firstResult);
        report.put("completedAt", Instant.now().toString()); report.put("activeStep", current == null ? null : current.name());
        report.put("scheduling", "A bounded server-thread barrier queues both actual Screen-generated requests before normal packet processing; not human input.");
        if (failure != null) report.put("failure", failure.toString());
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if (!host) write("guest-finished", failure == null ? "passed" : "failed");
        } catch (Exception e) { com.aquariverr.fluxod.FluxOverdrive.LOGGER.error("Peer report failed", e); }
        if (failure != null) com.aquariverr.fluxod.FluxOverdrive.LOGGER.error("Peer acceptance failed", failure);
        mc.stop();
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Step(String name, Runnable start, BooleanSupplier ready) {}
}
