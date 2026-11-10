package com.aquariverr.fluxod.clienttest;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.CellPreview;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Mod(value = MiniatureClientTest.MOD_ID, dist = Dist.CLIENT)
public final class MiniatureClientTest {

    public static final String MOD_ID = "flux_overdrive_client_test";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos FACTORY_POS = new BlockPos(0, 90, 0);
    private static final double GRID_LEFT = 41.5;
    private static final double GRID_TOP = 52.5;
    private static final int CELL_PITCH = 27;
    private static final int CELL_CENTER = 12;
    private static final long STEP_TIMEOUT_NANOS = 90_000_000_000L;
    private final Path output = Path.of(System.getProperty("flux_overdrive.clientTestOutput"));
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> passed = new ArrayList<>();
    private final List<Map<String, Object>> screenshots = new ArrayList<>();
    private final String worldName = "miniature-client-" + System.currentTimeMillis();
    private Step current;
    private long currentStarted;
    private boolean initialized;
    private boolean finished;
    private int renderedFrames;
    private int recipeReadyFrames;
    private long lastClickAt;
    private Capture capture;
    private String recipeBrowserCapture;
    private int captureEarliestFrame;
    private UUID oldSession;
    private CompoundTag expectedState;
    private CompoundTag expectedVisualState;
    private String fixture = "3x3-player-interactions";
    private List<OverlayFlags> originalOverlayFlags;
    private Hover activeHover;
    private boolean cursorCallbackDriver;
    private List<RecipeHolder<?>> originalRecipes;
    private RecipeReference clientRecipeSelection;
    private ItemStack markerContainer = ItemStack.EMPTY;
    private final List<Map<String, Object>> jeiEvidence = new ArrayList<>();
    private FactoryStorage jeiAssetStorage;
    private List<ItemStack> jeiPlayerAssets;
    private ItemStack jeiCursor = ItemStack.EMPTY;
    private ItemStack jeiGhostItem = ItemStack.EMPTY;
    private FluidStack jeiGhostFluid = FluidStack.EMPTY;
    private String jeiSmeltingRecipeId;
    private long jeiPreviousAcknowledgement;
    private MiniatureUniverseScreen jeiRetainedEditor;
    private List<RecipeReference.Binding> jeiExpectedBindings;
    private String jeiInfusingRecipeId;
    private boolean jeiChemicalGhostSkipped;
    private String jeiChemicalGhostCoverage = "not-exercised";
    private String jeiRecipeTransferCoverage = "not-exercised";
    private Map<String, Object> jeiServerRecipeEvidence;

    public MiniatureClientTest() {
        if (!System.getProperty("flux_overdrive.clientTestPeerRole", "").isEmpty()) {
            new MiniatureMultiplayerClientTest();
            return;
        }
        NeoForge.EVENT_BUS.addListener(this::onTick);
        NeoForge.EVENT_BUS.addListener(this::onRender);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::beforeScreenRender);
    }

    private void onTick(ClientTickEvent.Post event) {
        if (finished) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (!initialized) {
                if (minecraft.getOverlay() != null || minecraft.screen == null) return;
                initialized = true;
                initialize(minecraft);
            }
            if (current == null) {
                current = steps.pollFirst();
                if (current == null) {
                    finish(null);
                    return;
                }
                currentStarted = System.nanoTime();
                LOGGER.info("Miniature client verification: {}", current.name());
                current.start().run();
            }
            if (current.ready().getAsBoolean()) {
                passed.add(current.name());
                current = null;
            } else if (System.nanoTime() - currentStarted > STEP_TIMEOUT_NANOS) {
                throw new IllegalStateException("Timed out: " + current.name() + "; screen=" + minecraft.screen);
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void initialize(Minecraft minecraft) throws Exception {
        Files.createDirectories(output);
        // Match the published EMI default in this process without saving a player configuration.
        if (emiInstalled()) MiniatureEmiClientTestSupport.configure();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(2);
        minecraft.options.simulationDistance().set(5);
        minecraft.options.framerateLimit().set(60);
        minecraft.options.onboardingAccessibilityFinished();
        // This verification runs in its isolated game directory. Keep onboarding toasts out of layout screenshots.
        minecraft.getTutorial().setStep(TutorialSteps.NONE);
        minecraft.getToasts().clear();
        if (Boolean.getBoolean("flux_overdrive.miniatureWithoutGtceu")) {
            action("optional_gtceu_mod_and_classes_are_absent", () -> require(!ModList.get().isLoaded("gtceu")
                    && MiniatureClientTest.class.getClassLoader().getResource("com/gregtechceu/gtceu/GTCEu.class") == null,
                    "The requested no-GTCEu client retained GTCEu runtime classes"));
        }
        steps.add(new Step("create_isolated_flat_world", () -> {
            LevelSettings settings = new LevelSettings("Miniature Client Verification", GameType.CREATIVE,
                    false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(0, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), minecraft.screen);
        }, () -> minecraft.player != null && minecraft.level != null && minecraft.getSingleplayerServer() != null));
        serverStep("place_factory_and_open_real_menu", this::prepareWorld);
        waitFor("receive_registered_screen_and_authoritative_snapshot", () -> minecraft.screen instanceof MiniatureUniverseScreen screen
                && screen.getXSize() == 310 && screen.getMenu().snapshotReady() && screen.getMenu().snapshot().cells().size() == 9
                && screen.getMenu().snapshot().cells().get(6).machineCount() == 3
                && resourceAmount(true, "minecraft:iron_ingot", 7));
        if (Boolean.getBoolean("flux_overdrive.clientTestS12Only")) {
            addEntanglementScenario();
            addReleaseScenario();
            action("close_verified_release_scenario", () -> screen().onClose());
            return;
        }
        if (Boolean.getBoolean("flux_overdrive.clientTestS09Only")) {
            addJeiScenario();
            return;
        }
        if (Boolean.getBoolean("flux_overdrive.clientTestDirectionsOnly")) {
            addInteractionScenario();
            addMachineDirectionScenario();
            addWeightedSplitScenario();
            addProductRoutingScenario();
            addJeiScenario();
            return;
        }
        if (Boolean.getBoolean("flux_overdrive.clientTestS10Only")) {
            addEntanglementScenario();
            action("close_verified_remote_scenario", () -> screen().onClose());
            return;
        }
        if (Boolean.getBoolean("flux_overdrive.clientTestS11Only")) {
            addUpgradeScenario();
            action("close_verified_upgrade_scenario", () -> screen().onClose());
            return;
        }
        addInteractionScenario();
        addViewportScenario();
        serverStep("prepare_independent_populated_resource_display_fixture", this::prepareDisplayResources);
        waitFor("receive_populated_resource_display_fixture", () -> screen().getMenu().snapshot().inputs().size() == 6
                && screen().getMenu().snapshot().outputs().size() == 5);
        addLanguage("en_us");
        waitFor("wait_for_emi_jei_reload_and_search_completion", this::recipeIndexesReady);
        for (int[] size : List.of(new int[]{1280, 720}, new int[]{1920, 1080})) {
            for (int requestedScale : List.of(2, 3, 4)) addCapture(size[0], size[1], requestedScale);
        }
        clickAt("show_populated_output_inventory_for_capture", 245, 143, 0, () -> true);
        addCapture("outputs-en_us", 1280, 720, 3);
        clickAt("scroll_populated_outputs_down", 295, 216, 0, () -> true);
        addCapture("outputs-scrolled-en_us", 1280, 720, 3);
        clickAt("restore_populated_input_inventory_for_capture", 201, 143, 0, () -> true);
        addLanguage("zh_cn");
        addCapture("zh_cn", 1280, 720, 3);
        addCapture("zh_cn-resource-tooltip", 1280, 720, 3, new Hover("input_resource_quantity", 230, 162));
        addCapture("zh_cn-tool-tooltip", 1920, 1080, 4, new Hover("fit_tool", 170, 41));
        serverStep("display_fixture_unchanged_by_rendering_language_and_tabs", player ->
                require(expectedVisualState.equals(factory(player).factoryState().save()), "Display controls changed factory assets"));
        addLargeGridScenario();
        serverStep("large_fixture_unchanged_by_final_rendering", player ->
                require(expectedVisualState.equals(factory(player).factoryState().save()), "Rendering changed large-grid fixture"));
        addRecipeScenario();
        addCompilationScenario();
        addMarkerSourceScenario();
        addJeiScenario();
        addEntanglementScenario();
        addUpgradeScenario();
        addReleaseScenario();
        action("close_verified_menu", () -> screen().onClose());
        action("open_ordinary_inventory_after_editor", () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)));
        int[] inventoryOpenedFrame = new int[1];
        steps.add(new Step("ordinary_inventory_restores_emi_widget_visibility_and_activity",
                () -> inventoryOpenedFrame[0] = renderedFrames, () -> {
            if (renderedFrames < inventoryOpenedFrame[0] + 10) return false;
            require(Minecraft.getInstance().screen instanceof InventoryScreen, "Ordinary inventory did not open");
            if (emiInstalled()) require(originalOverlayFlags != null && originalOverlayFlags.equals(overlayFlags()),
                    "EMI widget visibility/activity was not restored");
            return true;
        }));
        action("close_ordinary_inventory", () -> Minecraft.getInstance().screen.onClose());
    }

    private void addReleaseScenario() {
        addLanguage("zh_cn");
        for (boolean remote : new boolean[]{false, true}) {
            String prefix = remote ? "s12-remote" : "s12-local";
            serverStep(prefix + "-prepare-native-line-with-empty-inventories", player -> {
                player.closeContainer();
                player.getInventory().clearContent();
                var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess());
                var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, 2), player.registryAccess()).storage()
                        .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.TWO), GridDirection.RIGHT))
                        .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
                installRecipeFixture(player, storage);
                player.getInventory().setItem(9, new ItemStack(Items.IRON_ORE, 4));
                releaseEnergy(player, 5);
                if (remote) {
                    var access = MiniatureEntanglementClientTestSupport.ACCESS_POS;
                    player.teleportTo(access.getX() + 0.5, access.getY(), access.getZ() - 2.5);
                    MiniatureEntanglementClientTestSupport.bind(player, FACTORY_POS);
                    require(MiniatureEntanglementClientTestSupport.access(player).openSelectedPluginMenu(player), "Could not open release remote source");
                } else {
                    player.teleportTo(2.5, 90, 2.5);
                    factory(player).openMenu(player);
                }
                fixture = prefix + "-actual-twenty-payment-production";
            });
            waitFor(prefix + "-receive-empty-authoritative-factory", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen current
                    && current.getMenu().snapshotReady() && current.getMenu().isRemoteAccess() == remote
                    && cell(1, 0).machineCount() == 2 && current.getMenu().snapshot().inputs().isEmpty()
                    && current.getMenu().snapshot().outputs().isEmpty() && !current.getMenu().snapshot().runtime().enabled()
                    && Minecraft.getInstance().player.getInventory().getItem(9).getCount() == 4);
            clickInventory(prefix + "-pick-up-real-ore", 9, () -> cursor(Items.IRON_ORE, 4));
            clickAt(prefix + "-deposit-real-ore", 295, 143, 0, () -> cursorEmpty() && resourceAmount(false, "minecraft:iron_ore", 4));
            clickAt(prefix + "-compile-native-furnaces", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY);
            clickAt(prefix + "-start-from-real-run-button", 152, 16, 0, () -> screen().getMenu().snapshot().runtime().enabled());
            waitFor(prefix + "-fifth-payment-pauses-without-output", () -> screen().getMenu().snapshot().runtime().paidTicks() == 5
                    && screen().getMenu().snapshot().runtime().status() == com.aquariverr.fluxod.miniature.runtime.FactoryRunner.Status.NO_ENERGY
                    && screen().getMenu().snapshot().outputs().isEmpty());
            serverStep(prefix + "-verify-incomplete-batch-accounting", player -> {
                var storage = factory(player).factoryState().storage().orElseThrow();
                require(storage.inputs().amounts().isEmpty() && storage.batch().paidTicks() == 5 && storage.batch().parallelism() == 2
                        && releaseBattery(player).getStoredEnergy().signum() == 0, "Incomplete production ledger changed");
                expectedState = factory(player).factoryState().save();
            });
            addCapture(prefix + "-unpowered-paid5", 1280, 720, 3);
            clickAt(prefix + "-pause-manually", 152, 16, 0, () -> !screen().getMenu().snapshot().runtime().enabled());
            serverStep(prefix + "-fund-only-fifteen-remaining-payments", player -> releaseEnergy(player, 15));
            addCapture(prefix + "-manual-pause-paid5", 1920, 1080, 4);
            serverStep(prefix + "-manual-pause-preserves-paid-state", player -> require(factory(player).factoryState().storage().orElseThrow().batch().paidTicks() == 5
                    && releaseBattery(player).getStoredEnergy().equals(BigInteger.valueOf(600_000_000)), "Disabled batch spent restored energy"));
            clickAt(prefix + "-resume-from-real-run-button", 152, 16, 0, () -> screen().getMenu().snapshot().runtime().enabled());
            waitFor(prefix + "-twentieth-payment-commits-four-products", () -> !screen().getMenu().snapshot().runtime().locked()
                    && resourceAmount(true, "minecraft:iron_ingot", 4));
            clickAt(prefix + "-open-real-output-inventory", 245, 143, 0, () -> true);
            addCapture(prefix + "-completed-output", 1280, 720, 3);
            clickAt(prefix + "-extract-paid-products", 230, 162, 0, () -> cursor(Items.IRON_INGOT, 4));
            clickInventory(prefix + "-store-products-in-player-inventory", 12, this::cursorEmpty);
            clickAt(prefix + "-disable-after-completion", 152, 16, 0, () -> !screen().getMenu().snapshot().runtime().enabled());
            serverStep(prefix + "-verify-complete-ledger", player -> {
                var storage = factory(player).factoryState().storage().orElseThrow();
                require(storage.batch() == null && storage.inputs().amounts().isEmpty() && storage.outputs().amounts().isEmpty()
                        && storage.layout().machineCount() == 2 && player.getInventory().getItem(12).is(Items.IRON_INGOT)
                        && player.getInventory().getItem(12).getCount() == 4 && releaseBattery(player).getStoredEnergy().signum() == 0,
                        "Complete production failed input/output/energy conservation");
            });
        }
    }

    private static com.aquariverr.fluxod.block.entity.TileQuantumBatteryController releaseBattery(ServerPlayer player) {
        return (com.aquariverr.fluxod.block.entity.TileQuantumBatteryController) player.serverLevel().getBlockEntity(
                FACTORY_POS.offset(com.aquariverr.fluxod.multiblock.QuantumBatteryStructure.stabilizerSlotRadius(3), 0, 0));
    }
    private static void releaseEnergy(ServerPlayer player, int payments) {
        var battery = releaseBattery(player);
        CompoundTag tag = new CompoundTag();
        battery.writeCustomTag(tag, sonar.fluxnetworks.api.FluxConstants.NBT_SAVE_ALL);
        tag.putByteArray("QuantumEnergy", BigInteger.valueOf(40_000_000L * payments).toByteArray());
        battery.readCustomTag(tag, sonar.fluxnetworks.api.FluxConstants.NBT_SAVE_ALL);
        battery.validateStructure();
        require(battery.isOperational(), "Release fixture battery did not form");
    }

    private void addUpgradeScenario() {
        addLanguage("en_us");
        serverStep("s11_prepare_selected_native_line_without_upgrade_materials", player -> {
            var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(
                            FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess()), BigInteger.TWO), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
            var compiled = MiniatureRecipeCatalog.get(player.server).compile(storage.layout(), player.serverLevel());
            require(compiled.successful(), "Upgrade fixture recipe did not compile");
            storage = storage.withRecipe(new GridPosition(1, 0), compiled.factory().recipes().get(new GridPosition(1, 0)).reference());
            installRecipeFixture(player, storage);
            player.getInventory().clearContent();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
            fixture = "s11-real-release-upgrades";
        });
        waitFor("s11_receive_release_upgrade_materials", () -> screen().getMenu().snapshot().size() == 3
                && screen().getMenu().snapshot().upgrade().materials().size() == 3 && cursorEmpty());
        clickAt("s11_open_upgrade_preview", 28, 41, 0, () -> true);
        addCapture("s11-missing-materials", 1280, 720, 3);
        serverStep("s11_supply_plain_quantum_materials_in_player_inventory", player -> {
            player.getInventory().setItem(9, new ItemStack(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get(), 64));
            player.getInventory().setItem(10, new ItemStack(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get(), 64));
            player.getInventory().setItem(11, new ItemStack(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get(), 64));
            player.getInventory().setItem(12, new ItemStack(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(), 16));
            player.containerMenu.broadcastChanges();
        });
        waitFor("s11_receive_player_upgrade_materials", () -> Minecraft.getInstance().player.getInventory().getItem(12).getCount() == 16);
        addCapture("s11-material-cost-tooltip", 1280, 720, 3, new Hover("upgrade_materials", 225, 65));
        for (int size : List.of(5, 7, 9, 11)) {
            clickAt("s11_pay_upgrade_to_" + size, 240, 120, 0, () -> screen().getMenu().snapshot().size() == size
                    && screen().getMenu().lastResult() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS);
            clickAt("s11_recompile_preserved_line_at_" + size, 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                    && compilation().outputRows().get(0).getFirst().amount().equals(BigInteger.TWO));
            serverStep("s11_verify_server_assets_and_bridges_at_" + size, player -> {
                var current = factory(player).factoryState().storage().orElseThrow();
                require(current.layout().size() == size && current.layout().machineCount() == 2
                        && ((FactoryCell.Machine) current.layout().cellAt(new GridPosition(1, 0))).recipe() != null, "Upgrade lost native selection or machines");
                for (int x = 2; x < size; x++) require(current.layout().cellAt(new GridPosition(x, 0)).equals(new FactoryCell.Arrow()), "Missing output bridge");
            });
            if (size == 5) {
                for (int[] dimensions : List.of(new int[]{1280, 720}, new int[]{1920, 1080}))
                    for (int scale : List.of(2, 3, 4)) addCapture("s11-paid-5x5", dimensions[0], dimensions[1], scale);
                addLanguage("zh_cn");
                addCapture("s11-materials-zh", 1280, 720, 3, new Hover("upgrade_materials", 225, 65));
            }
        }
        serverStep("s11_verify_exact_total_upgrade_charge", player -> {
            require(player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).getCount() == 8
                    && player.getInventory().getItem(11).getCount() == 4 && player.getInventory().getItem(12).getCount() == 1,
                    "Four upgrades did not consume exactly 120 casings, 60 glass and 15 interfaces");
            expectedVisualState = factory(player).factoryState().save();
        });
        addCapture("s11-maximum-11x11-zh", 1280, 720, 3);
        action("s11_close_upgraded_menu", () -> screen().onClose());
        serverStep("s11_reopen_upgraded_factory", player -> factory(player).openMenu(player));
        waitFor("s11_authoritative_11x11_after_reopening", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && screen().getMenu().snapshot().size() == 11);
        for (int[] dimensions : List.of(new int[]{1280, 720}, new int[]{1920, 1080}))
            for (int scale : List.of(2, 3, 4)) addCapture("s11-11x11-status-zh", dimensions[0], dimensions[1], scale);
        addCapture("s11-runtime-tooltip", 1280, 720, 3, new Hover("runtime_status", 240, 18));
        serverStep("s11_views_preserve_upgraded_assets", player -> require(expectedVisualState.equals(factory(player).factoryState().save()), "Viewing resized factory changed assets"));
        action("s11_close_before_real_drop_presentation", () -> screen().onClose());
        serverStep("s11_break_factory_into_its_single_saved_item", player -> {
            var level = player.serverLevel();
            var area = new net.minecraft.world.phys.AABB(FACTORY_POS).inflate(1);
            require(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty(), "Unexpected drops near presentation fixture");
            require(level.destroyBlock(FACTORY_POS, true), "Could not break upgraded factory");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
            require(drops.size() == 1, "Upgraded factory did not drop one complete item");
            ItemStack item = drops.getFirst().getItem().copy(); drops.getFirst().discard();
            require(item.getCount() == 1 && expectedVisualState.equals(item.get(com.aquariverr.fluxod.register.FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()), "Dropped presentation item lost factory state");
            player.getInventory().setItem(9, item);
            // An empty menu stand exposes the actual dropped item through vanilla inventory rendering.
            level.setBlock(FACTORY_POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
            factory(player).openMenu(player);
        });
        waitFor("s11_receive_actual_saved_item_and_empty_menu_stand", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && screen().getMenu().snapshot().size() == 3
                && Minecraft.getInstance().player.getInventory().getItem(9).has(com.aquariverr.fluxod.register.FluxOdDataComponents.MINIATURE_UNIVERSE_STATE));
        addCapture("s11-saved-item-model-tooltip", 1280, 720, 3, new Hover("saved_factory", 16, 159));
        action("s11_close_presentation_stand", () -> screen().onClose());
        serverStep("s11_place_same_item_and_restore_upgraded_factory", player -> {
            player.serverLevel().removeBlock(FACTORY_POS, false);
            ItemStack item = player.getInventory().getItem(9);
            var placed = ((net.minecraft.world.item.BlockItem) item.getItem()).place(new net.minecraft.world.item.context.DirectionalPlaceContext(
                    player.serverLevel(), FACTORY_POS, net.minecraft.core.Direction.DOWN, item, net.minecraft.core.Direction.UP));
            require(placed.consumesAction() && item.isEmpty() && expectedVisualState.equals(factory(player).factoryState().save()), "Restoring the displayed item changed upgraded assets");
            player.getInventory().setChanged();
            factory(player).openMenu(player);
        });
        waitFor("s11_placed_item_restores_authoritative_11x11", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && screen().getMenu().snapshot().size() == 11);
    }

    private void addEntanglementScenario() {
        addLanguage("zh_cn");
        serverStep("s10_build_real_battery_and_bind_selected_factory", player -> {
            player.closeContainer();
            player.getInventory().clearContent();
            var registries = player.registryAccess();
            var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
            var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
            var storage = FactoryStorage.empty()
                    .insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), registries).storage()
                    .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.ONE), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow())
                    .insertInput(new ItemStack(Items.IRON_ORE, 4), registries).storage()
                    .withOutputs(new ResourceInventory(Map.of(ingot, BigInteger.valueOf(7))));
            installRecipeFixture(player, storage);
            MiniatureEntanglementClientTestSupport.assemble(player, FACTORY_POS);
            fixture = "s10-real-tier3-bound-remote-editor";
        });
        waitFor("s10_receive_actual_entanglement_menu", () -> Minecraft.getInstance().player.containerMenu
                instanceof com.aquariverr.fluxod.menu.QuantumEntanglementMenu);
        action("s10_open_selected_plugin_via_native_menu_button_packet", () -> {
            var minecraft = Minecraft.getInstance();
            minecraft.gameMode.handleInventoryButtonClick(minecraft.player.containerMenu.containerId,
                    com.aquariverr.fluxod.menu.QuantumEntanglementMenu.BUTTON_OPEN_PLUGIN);
        });
        waitFor("s10_receive_remote_screen_and_authoritative_source_snapshot", () ->
                Minecraft.getInstance().screen instanceof MiniatureUniverseScreen remote && remote.getMenu().snapshotReady()
                        && remote.getMenu().isRemoteAccess() && cell(1, 0).machineCount() == 1);
        action("s10_client_validates_entry_instead_of_source_distance", () -> {
            require(Minecraft.getInstance().player.distanceToSqr(FACTORY_POS.getCenter()) > 64
                            && screen().getMenu().stillValid(Minecraft.getInstance().player),
                    "Remote menu retained the local factory distance restriction");
            oldSession = screen().getMenu().sessionId();
        });
        addCapture("s10-remote-initial", 1280, 720, 3);
        removeMachine("s10_remote_take_real_machine", 1, 0, () -> cursor(Items.FURNACE, 1) && cell(1, 0).type() == CellPreview.Type.EMPTY);
        clickCell("s10_remote_restore_same_machine", 1, 0, 0, () -> cursorEmpty() && cell(1, 0).machineCount() == 1);
        clickAt("s10_remote_recover_real_inputs", 230, 162, 0, () -> cursor(Items.IRON_ORE, 4));
        clickInventory("s10_remote_store_inputs_in_player_inventory", 9, this::cursorEmpty);
        clickInventory("s10_remote_pick_inputs_for_reinsertion", 9, () -> cursor(Items.IRON_ORE, 4));
        clickAt("s10_remote_reinsert_into_same_source_inventory", 295, 143, 0,
                () -> cursorEmpty() && resourceAmount(false, "minecraft:iron_ore", 4));
        clickAt("s10_remote_select_outputs", 245, 143, 0, () -> true);
        clickAt("s10_remote_extract_committed_products", 230, 162, 0, () -> cursor(Items.IRON_INGOT, 7));
        clickInventory("s10_remote_products_reach_real_player_inventory", 10, this::cursorEmpty);
        clickAt("s10_remote_compile_uses_original_factory", 116, 41, 0,
                () -> compilation().status() == CompilationPreview.Status.READY);
        serverStep("s10_remote_assets_and_compilation_have_one_owner", player -> {
            var storage = factory(player).factoryState().storage().orElseThrow();
            require(storage.machines().entry(new GridPosition(1, 0)).orElseThrow().count() == 1
                            && storage.inputs().amount(FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess())).equals(BigInteger.valueOf(4))
                            && storage.outputs().amounts().isEmpty() && player.getInventory().getItem(10).is(Items.IRON_INGOT)
                            && player.getInventory().getItem(10).getCount() == 7 && storage.batch() == null,
                    "Remote operations changed asset totals or created a batch");
            expectedState = factory(player).factoryState().save();
        });
        addCapture("s10-remote-compiled", 1920, 1080, 4);
        serverStep("s10_unbind_active_remote_session", player -> MiniatureEntanglementClientTestSupport.access(player).unbindBattery(player));
        waitFor("s10_unbind_closes_client_container", () -> !(Minecraft.getInstance().screen instanceof MiniatureUniverseScreen)
                && Minecraft.getInstance().player.containerMenu == Minecraft.getInstance().player.inventoryMenu);
        serverStep("s10_rebind_and_open_fresh_session", player -> {
            MiniatureEntanglementClientTestSupport.bind(player, FACTORY_POS);
            require(MiniatureEntanglementClientTestSupport.access(player).openSelectedPluginMenu(player), "Rebound menu failed to open");
        });
        waitFor("s10_rebound_menu_has_new_session", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen remote
                && remote.getMenu().snapshotReady() && remote.getMenu().isRemoteAccess() && !remote.getMenu().sessionId().equals(oldSession));
        serverStep("s10_leave_access_controller_range", player -> player.teleportTo(2.5, 90, 2.5));
        waitFor("s10_distance_revocation_closes_remote_container", () -> Minecraft.getInstance().player.containerMenu
                == Minecraft.getInstance().player.inventoryMenu);
        serverStep("s10_reopen_original_factory_locally", player -> {
            require(expectedState.equals(factory(player).factoryState().save()), "Revocation changed source assets");
            require(player.serverLevel().noCollision(player, player.getBoundingBox()), "Local return station intersects the battery structure");
            factory(player).openMenu(player);
        });
        waitFor("s10_local_reopen_observes_same_state", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen local
                && local.getMenu().snapshotReady() && !local.getMenu().isRemoteAccess()
                && resourceAmount(false, "minecraft:iron_ore", 4) && cell(1, 0).machineCount() == 1);
        addCapture("s10-local-after-remote", 1280, 720, 3);
    }

    private void prepareWorld(ServerPlayer player) {
        var level = player.serverLevel();
        level.setDefaultSpawnPos(FACTORY_POS.offset(0, 0, 2), 0);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, player.server);
        level.setDayTime(6000);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) level.setBlock(FACTORY_POS.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(FACTORY_POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
        player.teleportTo(0.5, 90, 2.5);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.FURNACE, 5));
        player.getInventory().setItem(10, new ItemStack(Items.IRON_ORE, 17));
        player.getInventory().setItem(11, new ItemStack(Items.WATER_BUCKET));
        TileMiniatureUniverse tile = factory(player);
        for (int count = 0; count < 3; count++) {
            ItemStack singular = new ItemStack(Items.FURNACE);
            singular.set(DataComponents.MAX_STACK_SIZE, 1);
            singular.set(DataComponents.CUSTOM_NAME, Component.literal("Singular furnace"));
            require(tile.insertMachine(new GridPosition(0, 2), singular, false).isEmpty(), "Could not prepare singular machines");
        }
        FactoryState before = tile.factoryState();
        ResourceInventory outputs = new ResourceInventory(Map.of(
                FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), level.registryAccess()), BigInteger.valueOf(7)));
        require(tile.applyPreparedState(before, FactoryState.of(before.storage().orElseThrow().withOutputs(outputs),
                level.registryAccess())), "Could not prepare output extraction fixture");
        tile.openMenu(player);
    }

    private void addRecipeScenario() {
        addLanguage("en_us");
        serverStep("prepare_server_only_recipe_candidates", player -> {
            originalRecipes = List.copyOf(player.server.getRecipeManager().getRecipes());
            List<RecipeHolder<?>> recipes = new ArrayList<>();
            for (String outputItem : List.of("iron_ingot", "iron_nugget")) {
                Recipe<?> recipe = Recipe.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                        {"type":"minecraft:smelting","ingredient":{"item":"minecraft:iron_ore"},
                        "result":{"id":"minecraft:%s"},"experience":0.0,"cookingtime":200}
                        """.formatted(outputItem))).getOrThrow();
                recipes.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("flux_overdrive", "s05_client_" + outputItem), recipe));
            }
            player.server.getRecipeManager().replaceRecipes(recipes);
            MiniatureRecipeCatalog.get(player.server).invalidate();
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess())));
            installRecipeFixture(player, storage);
            fixture = "s05-server-recipe-selection";
        });
        waitFor("receive_two_authoritative_recipe_candidates", () -> screen().getMenu().snapshot().size() == 3
                && recipePreview() != null && recipePreview().candidateCount() == 2 && recipePreview().status() == RecipePreview.Status.AUTOMATIC);
        clickMachineRecipe("right_click_cycles_recipe_with_empty_cursor", 1, 1, () -> recipePreview().selectedIndex() == 1
                && recipePreview().status() == RecipePreview.Status.READY && cursorEmpty());
        action("remember_selected_recipe", () -> clientRecipeSelection = recipePreview().reference());
        for (int[] size : List.of(new int[]{1280, 720}, new int[]{1920, 1080})) {
            for (int scale : List.of(2, 3, 4)) addCapture("s05-recipe-" + size[0] + "-" + scale, size[0], size[1], scale);
        }
        action("close_selected_recipe_editor", () -> screen().onClose());
        serverStep("reopen_recipe_selection", player -> factory(player).openMenu(player));
        waitFor("reopened_recipe_binding_restored", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && recipePreview() != null && clientRecipeSelection.equals(recipePreview().reference()));
        clickMachineRecipe("recipe_cycle_wraps_to_first_candidate", 1, 1, () -> recipePreview().selectedIndex() == 0);
        serverStep("remove_selected_native_recipe_without_replacing_reference", player -> {
            var machine = (FactoryCell.Machine) factory(player).factoryState().storage().orElseThrow().layout().cellAt(new GridPosition(1, 1));
            player.server.getRecipeManager().replaceRecipes(player.server.getRecipeManager().getRecipes().stream()
                    .filter(recipe -> !recipe.id().toString().equals(machine.recipe().recipeId())).toList());
            MiniatureRecipeCatalog.get(player.server).invalidate();
        });
        waitFor("invalid_recipe_keeps_reference_and_hides_outputs", () -> recipePreview().status() == RecipePreview.Status.INVALID_SELECTION
                && recipePreview().reference() != null && recipePreview().outputs().isEmpty());
        addLanguage("zh_cn");
        addCapture("s05-invalid-zh_cn", 1280, 720, 3);
        addCapture("s05-invalid-tooltip-zh_cn", 1280, 720, 3, new Hover("invalid_recipe", 207, 85));
        serverStep("prepare_native_shaped_cake_with_bucket_returns", player -> {
            player.server.getRecipeManager().replaceRecipes(originalRecipes);
            MiniatureRecipeCatalog.get(player.server).invalidate();
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.CRAFTING_TABLE, 2), player.registryAccess()).storage();
            List<Item> inputs = List.of(Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT);
            for (int index = 0; index < inputs.size(); index++) storage = storage.withConfiguration(new GridPosition(index % 3, index / 3),
                    new FactoryCell.Marker(FactoryItemResources.keyOf(new ItemStack(inputs.get(index)), player.registryAccess())));
            var search = MiniatureRecipeCatalog.get(player.server).search(FactoryItemResources.keyOf(new ItemStack(Items.CRAFTING_TABLE), player.registryAccess()),
                    MiniatureRecipeCatalog.markerInputs(storage.layout()), player.serverLevel());
            var cake = search.candidates().stream().filter(recipe -> recipe.reference().recipeId().equals("minecraft:cake")).findFirst().orElseThrow();
            storage = storage.withRecipe(new GridPosition(1, 1), cake.reference());
            installRecipeFixture(player, storage);
            expectedVisualState = factory(player).factoryState().save();
            fixture = "s05-native-cake-and-returns";
        });
        waitFor("receive_native_shaped_slots_and_scaled_bucket_returns", () -> recipePreview().reference() != null
                && recipePreview().reference().recipeId().equals("minecraft:cake") && recipePreview().reference().bindings().size() == 9
                && recipePreview().outputs().stream().anyMatch(amount -> amount.resource().registryId().equals("minecraft:bucket") && amount.amount().equals(BigInteger.valueOf(6))));
        addCapture("s05-cake-zh_cn", 1280, 720, 3);
        addCapture("s05-cake-return-tooltip-zh_cn", 1920, 1080, 4, new Hover("bucket_return", 286, 84));
        serverStep("recipe_previews_do_not_produce_or_consume_real_assets", player -> {
            require(expectedVisualState.equals(factory(player).factoryState().save()), "Recipe rendering changed assets");
            var storage = factory(player).factoryState().storage().orElseThrow();
            require(storage.inputs().amounts().isEmpty() && storage.outputs().amounts().isEmpty(), "Recipe previews created real resources");
        });
    }

    private RecipePreview recipePreview() { return screen().getMenu().snapshot().recipes().get(4); }

    private void addMarkerSourceScenario() {
        addLanguage("en_us");
        serverStep("s08_prepare_independent_container_marker_fixture", player -> {
            require(player.containerMenu.getCarried().isEmpty(), "Previous fixture left a carried asset");
            installRecipeFixture(player, FactoryStorage.empty());
            markerContainer = new ItemStack(Items.WATER_BUCKET);
            markerContainer.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this bucket and its water"));
            player.getInventory().setItem(12, markerContainer.copy());
            player.getInventory().setItem(13, new ItemStack(Items.LAVA_BUCKET));
            fixture = "s08-explicit-container-marker-sources";
        });
        waitFor("s08_receive_empty_grid_and_named_water_bucket", () -> cell(0, 0).type() == CellPreview.Type.EMPTY
                && ItemStack.matches(markerContainer, Minecraft.getInstance().player.getInventory().getItem(12)));
        clickInventory("s08_pick_up_named_water_bucket", 12,
                () -> ItemStack.matches(markerContainer, screen().getMenu().getCarried()) && markerSourceIs(ResourceKind.ITEM));
        clickCell("s08_default_source_marks_exact_container_item", 0, 0, 1,
                () -> exactContainerMarker(0, 0) && ItemStack.matches(markerContainer, screen().getMenu().getCarried()));
        serverStep("s08_record_state_before_source_button", player -> expectedVisualState = factory(player).factoryState().save());
        clickAt("s08_choose_explicit_water_tank", 245, 41, 0, () -> markerSourceIs(ResourceKind.FLUID));
        serverStep("s08_source_button_does_not_edit_or_drain_assets", player -> {
            require(expectedVisualState.equals(factory(player).factoryState().save()), "Choosing a marker source changed factory state");
            require(ItemStack.matches(markerContainer, player.containerMenu.getCarried()), "Choosing water changed the container");
        });
        replaceMarker("s08_selected_water_creates_1000_unit_fluid_marker", 0, 0, 1,
                () -> marker(0, 0, "minecraft:water", 1000, GridDirection.RIGHT)
                        && cell(0, 0).resource().kind() == ResourceKind.FLUID
                        && ItemStack.matches(markerContainer, screen().getMenu().getCarried()));
        addCapture("s08-water-source-en_us", 1280, 720, 3, new Hover("marker_source", 245, 41));
        clickAt("s08_cycle_back_to_container_item", 245, 41, 0, () -> markerSourceIs(ResourceKind.ITEM));
        replaceMarker("s08_container_item_remains_selectable_after_water", 0, 0, 1, () -> exactContainerMarker(0, 0));
        clickAt("s08_select_water_before_switching_containers", 245, 41, 0, () -> markerSourceIs(ResourceKind.FLUID));
        clickInventory("s08_return_named_bucket_intact", 12, this::cursorEmpty);
        waitFor("s08_empty_cursor_hides_source_button", () -> !markerSourceButton().visible);
        clickInventory("s08_different_container_resets_source_to_item", 13,
                () -> cursor(Items.LAVA_BUCKET, 1) && markerSourceIs(ResourceKind.ITEM));
        clickCell("s08_new_lava_bucket_defaults_to_item", 1, 0, 1,
                () -> marker(1, 0, "minecraft:lava_bucket", 1, GridDirection.RIGHT) && cell(1, 0).resource().kind() == ResourceKind.ITEM);
        clickAt("s08_choose_current_lava_contents", 245, 41, 0, () -> markerSourceIs(ResourceKind.FLUID));
        replaceMarker("s08_current_lava_contents_replace_item_marker", 1, 0, 1,
                () -> marker(1, 0, "minecraft:lava", 1000, GridDirection.RIGHT) && cell(1, 0).resource().kind() == ResourceKind.FLUID);
        addLanguage("zh_cn");
        addCapture("s08-lava-source-zh_cn", 1920, 1080, 4, new Hover("marker_source", 245, 41));
        clickInventory("s08_return_lava_bucket_intact", 13, this::cursorEmpty);
        if (ModList.get().isLoaded("mekanism")) addChemicalMarkerSourceScenario();
        serverStep("s08_marker_choices_preserve_all_real_container_assets", player -> {
            require(player.getInventory().getItem(12).is(Items.WATER_BUCKET)
                    && player.getInventory().getItem(12).getHoverName().getString().equals("Keep this bucket and its water"),
                    "Named bucket was consumed, emptied or lost its components");
            require(ItemStack.matches(new ItemStack(Items.LAVA_BUCKET), player.getInventory().getItem(13)), "Lava bucket was changed");
            var storage = factory(player).factoryState().storage().orElseThrow();
            require(storage.machines().entries().isEmpty() && storage.inputs().amounts().isEmpty() && storage.outputs().amounts().isEmpty(),
                    "Virtual resource marking created or consumed real factory assets");
        });
    }

    private void addChemicalMarkerSourceScenario() {
        serverStep("s08_prepare_native_chemical_tank_item", player -> {
            markerContainer = ChemicalMarkerFixture.filledTank();
            player.getInventory().setItem(14, markerContainer.copy());
        });
        waitFor("s08_receive_filled_chemical_tank", () -> ItemStack.matches(markerContainer, Minecraft.getInstance().player.getInventory().getItem(14)));
        clickInventory("s08_pick_up_chemical_tank_with_item_default", 14,
                () -> ItemStack.matches(markerContainer, screen().getMenu().getCarried()) && markerSourceIs(ResourceKind.ITEM));
        clickCell("s08_mark_exact_chemical_container_item", 2, 0, 1, () -> exactContainerMarker(2, 0));
        clickAt("s08_choose_native_hydrogen_tank", 245, 41, 0, () -> markerSourceIs(ResourceKind.CHEMICAL));
        replaceMarker("s08_hydrogen_marker_uses_default_quantity_without_draining", 2, 0, 1,
                () -> marker(2, 0, "mekanism:hydrogen", 1000, GridDirection.RIGHT)
                        && cell(2, 0).resource().kind() == ResourceKind.CHEMICAL
                        && ItemStack.matches(markerContainer, screen().getMenu().getCarried()));
        addCapture("s08-chemical-source-zh_cn", 1280, 720, 3, new Hover("marker_source", 245, 41));
        clickAt("s08_chemical_source_cycles_back_to_item", 245, 41, 0, () -> markerSourceIs(ResourceKind.ITEM));
        replaceMarker("s08_restore_exact_chemical_container_marker", 2, 0, 1, () -> exactContainerMarker(2, 0));
        clickInventory("s08_return_native_chemical_tank_intact", 14, this::cursorEmpty);
        serverStep("s08_native_chemical_amount_and_components_remain_exact", player ->
                require(ItemStack.matches(markerContainer, player.getInventory().getItem(14)), "Chemical selection changed the real tank"));
    }

    private static final class ChemicalMarkerFixture {
        static Object ghostIngredient() {
            return new mekanism.api.chemical.ChemicalStack(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY
                    .getHolder(ResourceLocation.parse("mekanism:hydrogen")).orElseThrow(), 731);
        }

        static ItemStack filledTank() {
            ItemStack tank = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("mekanism:basic_chemical_tank")));
            var capability = net.neoforged.neoforge.capabilities.ItemCapability.createVoid(
                    ResourceLocation.parse("mekanism:chemical_handler"), mekanism.api.chemical.IChemicalHandler.class);
            var handler = capability.getCapability(tank, null);
            require(handler != null && handler.getChemicalTanks() == 1, "Native chemical tank capability is missing");
            handler.setChemicalInTank(0, new mekanism.api.chemical.ChemicalStack(
                    mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceLocation.parse("mekanism:hydrogen")).orElseThrow(), 700));
            return tank;
        }
    }

    private AbstractWidget markerSourceButton() {
        return screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getX() == screen().getGuiLeft() + 188 && widget.getY() == screen().getGuiTop() + 33)
                .findFirst().orElseThrow();
    }

    private boolean markerSourceIs(ResourceKind kind) {
        var button = markerSourceButton();
        Component label = Component.translatable("screen.flux_overdrive.miniature_universe.resource_kind."
                + kind.name().toLowerCase(java.util.Locale.ROOT));
        if (kind != ResourceKind.ITEM) label = Component.translatable("screen.flux_overdrive.miniature_universe.marker_tank", label, 1);
        return button.visible && button.active && button.getMessage().getString().startsWith(
                Component.translatable("screen.flux_overdrive.miniature_universe.marker_source_value", label, "").getString());
    }

    private boolean exactContainerMarker(int x, int y) {
        var cell = cell(x, y);
        return cell.type() == CellPreview.Type.MARKER && cell.quantity().equals(BigInteger.ONE)
                && cell.resource().equals(FactoryItemResources.keyOf(markerContainer, Minecraft.getInstance().level.registryAccess()));
    }

    private void addJeiScenario() {
        if (ModList.get().isLoaded("mekanism")) addPerTickRecipeScenario();
        if (emiInstalled()) { addEmiScenario(); return; }
        if (!ModList.get().isLoaded("jei")) {
            action("s09_optional_jei_and_emi_classes_are_absent", () -> {
                var loader = MiniatureClientTest.class.getClassLoader();
                require(!ModList.get().isLoaded("jei") && !emiInstalled(), "The no-JEI run retained an overlay mod");
                require(loader.getResource("mezz/jei/api/IModPlugin.class") == null
                        && loader.getResource("dev/emi/emi/api/EmiPlugin.class") == null,
                        "Optional overlay classes are still on the no-JEI runtime classpath");
                jeiEvidence.add(Map.of("entry", "optional_dependency_absence", "jeiClassAbsent", true,
                        "emiClassAbsent", true, "baseEditorScenariosPassed", !Boolean.getBoolean("flux_overdrive.clientTestS09Only")));
            });
            addCapture("s09-no-jei-base-editor", 1280, 720, 3);
            return;
        }
        waitFor("s09_real_jei_runtime_registered", MiniatureJeiClientTestSupport::ready);
        addLanguage("en_us");
        serverStep("s09_prepare_populated_assets_and_named_carried_bucket", player -> {
            FactoryStorage storage = FactoryStorage.empty()
                    .insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE), player.registryAccess()).storage()
                    .withInputs(new ResourceInventory(Map.of(FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess()), BigInteger.valueOf(19))))
                    .withOutputs(new ResourceInventory(Map.of(FactoryItemResources.keyOf(new ItemStack(Items.GOLD_INGOT), player.registryAccess()), BigInteger.valueOf(7))));
            installRecipeFixture(player, storage);
            jeiCursor = new ItemStack(Items.WATER_BUCKET);
            jeiCursor.set(DataComponents.CUSTOM_NAME, Component.literal("JEI must preserve this real bucket"));
            player.containerMenu.setCarried(jeiCursor.copy());
            rememberJeiAssets(player);
            fixture = "s09-registered-jei-ghost-assets";
        });
        waitFor("s09_receive_ghost_fixture_and_exact_real_cursor", () -> screen().getMenu().snapshot().size() == 3
                && cell(1, 1).type() == CellPreview.Type.MACHINE && cell(0, 0).type() == CellPreview.Type.EMPTY
                && ItemStack.matches(jeiCursor, screen().getMenu().getCarried()));
        action("s09_prepare_typed_item_and_component_fluid_ingredients", () -> {
            jeiGhostItem = new ItemStack(Items.IRON_ORE, 41);
            jeiGhostItem.set(DataComponents.CUSTOM_NAME, Component.literal("Exact JEI ore variant"));
            CompoundTag data = new CompoundTag();
            data.putString("s09", "preserve-fluid-components");
            jeiGhostFluid = new FluidStack(Fluids.WATER, 4321);
            jeiGhostFluid.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            verifyJeiTargets(jeiGhostItem, 8, fittedPoint(3, 0, 0), fittedPoint(3, 1, 1));
        });
        addJeiDrop("s09_registered_item_ghost_preserves_components_and_default_one", () -> jeiGhostItem,
                () -> fittedPoint(3, 0, 0), () -> exactGhostMarker(0, 0,
                        FactoryItemResources.keyOf(jeiGhostItem, Minecraft.getInstance().level.registryAccess()), 1));
        addJeiDrop("s09_registered_fluid_ghost_preserves_components_and_default_1000", () -> jeiGhostFluid,
                () -> fittedPoint(3, 1, 0), () -> exactGhostMarker(1, 0,
                        FactoryFluidResources.keyOf(jeiGhostFluid, Minecraft.getInstance().level.registryAccess()), 1000));
        if (ModList.get().isLoaded("mekanism")) {
            addJeiChemicalGhostScenario();
        } else {
            action("s09_optional_mekanism_classes_are_absent", () -> require(MiniatureClientTest.class.getClassLoader()
                    .getResource("mekanism/api/chemical/ChemicalStack.class") == null, "Absent Mekanism leaked chemical classes"));
        }
        addGhostRotationChecks("jei");
        serverStep("s09_server_ghost_changes_preserve_all_real_assets", this::assertJeiAssets);
        addCapture("s09-jei-typed-ghosts", 1280, 720, 3);
        addJeiViewportScenario();
        serverStep("s09_viewport_ghosts_preserve_all_real_assets", this::assertJeiAssets);
        clickInventory("s09_return_named_real_bucket_before_recipe_selection", 15, this::cursorEmpty);
        addJeiRecipeAvailabilityProbe();
    }

    private void addEmiScenario() {
        waitFor("compat_native_emi_index_ready", MiniatureEmiClientTestSupport::indexesReady);
        serverStep("compat_prepare_emi_assets", player -> {
            FactoryStorage storage = FactoryStorage.empty()
                    .insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE), player.registryAccess()).storage();
            installRecipeFixture(player, storage);
            jeiCursor = new ItemStack(Items.WATER_BUCKET);
            jeiCursor.set(DataComponents.CUSTOM_NAME, Component.literal("EMI must preserve this real bucket"));
            player.containerMenu.setCarried(jeiCursor.copy());
            rememberJeiAssets(player);
            fixture = "compat-native-emi-drag-drop";
        });
        waitFor("compat_emi_fixture_received", () -> cell(1, 1).type() == CellPreview.Type.MACHINE
                && cell(0, 0).type() == CellPreview.Type.EMPTY && ItemStack.matches(jeiCursor, screen().getMenu().getCarried()));
        action("compat_emi_component_item_drop", () -> {
            jeiGhostItem = new ItemStack(Items.IRON_ORE, 41);
            jeiGhostItem.set(DataComponents.CUSTOM_NAME, Component.literal("Exact EMI variant"));
            var point = fittedPoint(3, 0, 0);
            MiniatureEmiClientTestSupport.drop(screen(), jeiGhostItem, screen().getGuiLeft() + point[0], screen().getGuiTop() + point[1]);
        });
        waitFor("compat_emi_item_ack", () -> exactGhostMarker(0, 0,
                FactoryItemResources.keyOf(jeiGhostItem, Minecraft.getInstance().level.registryAccess()), 1));
        action("compat_emi_component_fluid_drop", () -> {
            jeiGhostFluid = new FluidStack(Fluids.WATER, 4321);
            CompoundTag data = new CompoundTag();
            data.putString("compat", "emi-fluid-components");
            jeiGhostFluid.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var point = fittedPoint(3, 1, 0);
            MiniatureEmiClientTestSupport.drop(screen(), jeiGhostFluid, screen().getGuiLeft() + point[0], screen().getGuiTop() + point[1]);
        });
        waitFor("compat_emi_fluid_ack", () -> exactGhostMarker(1, 0,
                FactoryFluidResources.keyOf(jeiGhostFluid, Minecraft.getInstance().level.registryAccess()), 1000));
        if (ModList.get().isLoaded("mekanism")) {
            action("compat_emi_native_chemical_drop", () -> {
                var point = fittedPoint(3, 2, 0);
                MiniatureEmiClientTestSupport.drop(screen(), "mekanism:hydrogen", screen().getGuiLeft() + point[0], screen().getGuiTop() + point[1]);
            });
            waitFor("compat_emi_chemical_ack", () -> exactGhostMarker(2, 0, new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"), 1000));
        }
        addGhostRotationChecks("emi");
        serverStep("compat_emi_all_real_assets_unchanged", this::assertJeiAssets);
        addCapture("compat-native-emi-item-fluid-chemical", 1280, 720, 3);
        clickInventory("compat_return_real_bucket", 15, this::cursorEmpty);
        action("compat_emi_record_native_coverage", () -> {
            jeiChemicalGhostCoverage = ModList.get().isLoaded("mekanism") ? "native-emi-chemical-handler-and-server-ack" : "mekanism-absent";
            jeiEvidence.add(Map.of("entry", "native_emi_drag_drop", "fluidComponentsPreserved", true,
                    "realAssetsUnchanged", true, "registeredHandler", true));
        });
    }

    private void addGhostRotationChecks(String browser) {
        CellPreview[] originals = new CellPreview[3];
        action(browser + "_remember_ghost_markers_before_rotation", () -> {
            for (int x = 0; x < 3; x++) originals[x] = cell(x, 0);
        });
        int count = ModList.get().isLoaded("mekanism") ? 3 : 2;
        for (int x = 0; x < count; x++) {
            int column = x;
            for (GridDirection direction : List.of(GridDirection.DOWN, GridDirection.LEFT, GridDirection.UP, GridDirection.RIGHT)) {
                addClickStep(browser + "_rotate_typed_marker_" + column + "_" + direction, () -> {
                    var point = fittedPoint(3, column, 0);
                    click(point[0], point[1], 1);
                }, () -> cell(column, 0).direction() == direction && cell(column, 0).type() == CellPreview.Type.MARKER
                        && cell(column, 0).resource().equals(originals[column].resource())
                        && cell(column, 0).quantity().equals(originals[column].quantity())
                        && ItemStack.matches(jeiCursor, screen().getMenu().getCarried()));
            }
        }
    }

    private void addJeiRecipeAvailabilityProbe() {
        serverStep("s09_record_authoritative_native_recipes_before_jei_lookup", player -> {
            var recipes = player.server.getRecipeManager().getRecipes();
            var ids = recipes.stream().map(recipe -> recipe.id().toString()).collect(java.util.stream.Collectors.toSet());
            jeiServerRecipeEvidence = Map.of("entry", "authoritative_native_recipe_presence",
                    "serverSmeltingRecipeCount", recipes.stream().filter(recipe -> recipe.value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe).count(),
                    "serverCraftingRecipeCount", recipes.stream().filter(recipe -> recipe.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe).count(),
                    "ironOreSmeltingHolderPresent", ids.contains("minecraft:iron_ingot_from_smelting_iron_ore"),
                    "craftingTableHolderPresent", ids.contains("minecraft:crafting_table"), "chestHolderPresent", ids.contains("minecraft:chest"));
        });
        action("s09_probe_real_jei_native_recipe_collections_before_transfer", () -> {
            jeiEvidence.add(jeiServerRecipeEvidence);
            var availability = MiniatureJeiClientTestSupport.nativeRecipeAvailability();
            jeiEvidence.add(availability.evidence());
            if (availability.bothCollectionsEmpty()) {
                require(emiInstalled(), "Pure JEI must register native smelting and crafting recipes: " + availability.evidence());
                require(((Number) jeiServerRecipeEvidence.get("serverSmeltingRecipeCount")).longValue() > 0
                                && ((Number) jeiServerRecipeEvidence.get("serverCraftingRecipeCount")).longValue() > 0,
                        "Cannot attribute empty JEI native collections to EMI while authoritative native recipes are also absent");
                jeiRecipeTransferCoverage = "not-exercised-native-jei-recipe-collections-empty-with-emi";
                jeiEvidence.add(Map.of("entry", "recipe_transfer_not_exercised", "emiInstalled", true, "reason",
                        "EMI/JEMI PluginCallerMixin suppresses jei:minecraft recipe registration. This runtime's SMELTING and CRAFTING collections are empty, including hidden recipes; native JEI recipe transfer is verified in the pure-JEI configuration"));
                return;
            }
            // Add only the scenarios that will actually run, ahead of the already queued menu-close steps.
            var following = new ArrayDeque<>(steps);
            steps.clear();
            addJeiRecipeScenario();
            action("s09_native_jei_recipe_transfer_scenarios_completed", () -> jeiRecipeTransferCoverage = "native-jei-recipe-layouts-and-server-acknowledgements-exercised");
            steps.addAll(following);
        });
    }

    private void addJeiChemicalGhostScenario() {
        action("s09_probe_and_dispatch_native_chemical_ghost_when_registered", () -> {
            Object chemical = ChemicalMarkerFixture.ghostIngredient();
            var availability = MiniatureJeiClientTestSupport.ingredientAvailability(chemical);
            jeiEvidence.add(availability.evidence());
            if (!availability.recognized()) {
                require(emiInstalled() && !availability.registered(),
                        "Native ChemicalStack must be accepted; only an absent type in the combined EMI runtime may be skipped: " + availability.evidence());
                // JEMI's PluginCallerMixin skips JEI plugins for mods that provide their own native EMI plugin.
                jeiChemicalGhostSkipped = true;
                jeiChemicalGhostCoverage = "skipped-native-chemical-type-unavailable-with-emi";
                jeiEvidence.add(Map.of("entry", "chemical_ghost_not_exercised", "emiInstalled", true,
                        "nativeJeiTypeRegistered", false, "reason",
                        "EMI/JEMI PluginCallerMixin filters Mekanism's JEI plugin because Mekanism has a native EMI plugin; ChemicalStack is absent from this JEI runtime. Pure-JEI runs require native chemical ghost support"));
                return;
            }
            var point = fittedPoint(3, 2, 0);
            double x = screen().getGuiLeft() + point[0];
            double y = screen().getGuiTop() + point[1];
            moveActualCursor(x, y);
            jeiEvidence.add(MiniatureJeiClientTestSupport.drop(screen(), chemical, x, y));
        });
        waitFor("s09_native_chemical_ghost_ack_or_explicit_unavailable_type_record", () -> {
            if (jeiChemicalGhostSkipped) return cell(2, 0).type() == CellPreview.Type.EMPTY
                    && ItemStack.matches(jeiCursor, screen().getMenu().getCarried());
            boolean accepted = exactGhostMarker(2, 0, new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"), 1000)
                    && ItemStack.matches(jeiCursor, screen().getMenu().getCarried());
            if (accepted) jeiChemicalGhostCoverage = "native-jei-handler-acknowledged-default-1000";
            return accepted;
        });
    }

    private void addJeiViewportScenario() {
        serverStep("s09_prepare_existing_11x11_layout_for_registered_targets", player -> {
            var old = factory(player).factoryState().storage().orElseThrow();
            FactoryLayout layout = FactoryLayout.empty(11);
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                GridPosition position = new GridPosition(x, y);
                layout = layout.withCell(position, old.layout().cellAt(position));
            }
            installRecipeFixture(player, FactoryStorage.restore(layout, old.machines(), old.inputs(), old.outputs(), player.registryAccess()));
            fixture = "s09-jei-11x11-transformed-targets";
        });
        waitFor("s09_receive_11x11_before_target_geometry_checks", () -> screen().getMenu().snapshot().size() == 11);
        action("s09_fitted_targets_exclude_output_column", () -> verifyJeiTargets(jeiGhostItem, 120,
                fittedPoint(11, 10, 10), fittedPoint(11, 11, 10)));
        action("s09_zoom_grid_using_existing_mouse_wheel_entry", () -> scrollGrid(94, 91.5, Math.log(294.0 / 81.0) / Math.log(1.2)));
        action("s09_pan_grid_first_mouse_drag", () -> panGrid(170, 120, -70, -60));
        action("s09_pan_grid_second_mouse_drag", () -> panGrid(170, 120, -70, -60));
        action("s09_transformed_targets_are_clipped_to_current_viewport", () -> verifyJeiTargets(jeiGhostItem, -1,
                new double[]{75.5, 106.5}, new double[]{181, 106.5}));
        addJeiDrop("s09_jei_drop_after_zoom_pan_edits_exact_far_corner", () -> new ItemStack(Items.DIAMOND, 37),
                () -> new double[]{75.5, 106.5}, () -> marker(10, 10, "minecraft:diamond", 1, GridDirection.RIGHT));
        addCapture("s09-jei-zoom-pan-corner", 1280, 720, 3);
        clickAt("s09_fit_after_transformed_drop", 170, 41, 0, () -> true);
        addCapture("s09-jei-11x11-resized", 1920, 1080, 4);
        action("s09_requery_registered_targets_after_window_resize", () -> verifyJeiTargets(jeiGhostFluid, 120,
                fittedPoint(11, 10, 9), fittedPoint(11, 11, 9)));
        addJeiDrop("s09_jei_drop_after_resize_edits_exact_cell", () -> jeiGhostFluid,
                () -> fittedPoint(11, 10, 9), () -> exactGhostMarker(10, 9,
                        FactoryFluidResources.keyOf(jeiGhostFluid, Minecraft.getInstance().level.registryAccess()), 1000));
        addCapture("s09-jei-11x11-fluid-result", 1920, 1080, 4);
    }

    private void addJeiRecipeScenario() {
        serverStep("s09_prepare_native_furnace_and_preserved_inventory", player -> {
            var old = factory(player).factoryState().storage().orElseThrow();
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE), player.registryAccess()).storage()
                    .withInputs(old.inputs()).withOutputs(old.outputs());
            installRecipeFixture(player, storage);
            rememberJeiAssets(player);
            fixture = "s09-registered-jei-recipe-selection";
        });
        waitFor("s09_receive_furnace_without_input_markers", () -> screen().getMenu().snapshot().size() == 3
                && cell(1, 1).type() == CellPreview.Type.MACHINE && cell(0, 0).type() == CellPreview.Type.EMPTY);
        clickMachineRecipe("s09_select_furnace_for_registered_recipe_transfer", 1, 1, this::jeiMachineSelected);
        action("s09_get_recipe_from_jei_native_smelting_category", () -> jeiSmeltingRecipeId = MiniatureJeiClientTestSupport.findSmeltingRecipe());
        serverStep("s09_snapshot_before_transfer_simulation", player -> expectedVisualState = factory(player).factoryState().save());
        action("s09_real_jei_recipe_layout_simulation_does_not_send_selection", () -> {
            jeiPreviousAcknowledgement = screen().getMenu().lastAcknowledgedCommandId();
            jeiEvidence.add(MiniatureJeiClientTestSupport.transferSmelting(screen(), false, false));
            require(screen().getMenu().lastAcknowledgedCommandId() == jeiPreviousAcknowledgement, "JEI transfer simulation submitted a command");
        });
        serverStep("s09_transfer_simulation_preserves_authoritative_state", player ->
                require(expectedVisualState.equals(factory(player).factoryState().save()), "JEI simulation changed server state"));
        if (!emiInstalled()) {
            action("s09_open_native_smelting_recipes_gui_over_editor", () -> {
                jeiRetainedEditor = screen();
                jeiEvidence.add(MiniatureJeiClientTestSupport.showSmelting(jeiRetainedEditor));
            });
            addRecipeBrowserCapture("s09-jei-native-smelting-browser");
        } else {
            action("s09_combined_overlay_uses_registered_transfer_without_native_browser_claim", () ->
                    jeiEvidence.add(Map.of("entry", "combined_overlay_scope", "nativeJeiBrowserExercised", false,
                            "reason", "EMI can replace JEI's recipe browser; native RecipesGui is covered in the pure-JEI run")));
        }
        action("s09_execute_registered_recipe_selection", () -> {
            var editor = emiInstalled() ? screen() : jeiRetainedEditor;
            jeiPreviousAcknowledgement = editor.getMenu().lastAcknowledgedCommandId();
            if (!emiInstalled()) MiniatureJeiClientTestSupport.verifyNativeBrowser(editor);
            jeiEvidence.add(MiniatureJeiClientTestSupport.transferSmelting(editor, true, false));
        });
        if (!emiInstalled()) action("s09_close_native_smelting_browser_back_to_same_editor", () ->
                MiniatureJeiClientTestSupport.closeBrowser(jeiRetainedEditor));
        waitFor("s09_server_accepts_native_recipe_id_and_exact_displayed_binding", () -> recipePreview() != null
                && screen().getMenu().lastAcknowledgedCommandId() > jeiPreviousAcknowledgement
                && screen().getMenu().lastResult() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                && recipePreview().status() == RecipePreview.Status.READY && recipePreview().reference().recipeId().equals(jeiSmeltingRecipeId)
                && recipePreview().reference().bindings().size() == 1
                && recipePreview().reference().bindings().getFirst().resource().registryId().equals("minecraft:iron_ore"));
        serverStep("s09_recipe_selection_changes_no_assets", this::assertJeiAssets);
        addCapture("s09-jei-native-recipe-selected", 1280, 720, 3);
        serverStep("s09_remove_recipe_only_from_authoritative_server", player -> {
            originalRecipes = List.copyOf(player.server.getRecipeManager().getRecipes());
            player.server.getRecipeManager().replaceRecipes(originalRecipes.stream()
                    .filter(recipe -> !recipe.id().toString().equals(jeiSmeltingRecipeId)).toList());
            MiniatureRecipeCatalog.get(player.server).invalidate();
        });
        waitFor("s09_client_receives_missing_recipe_state", () -> recipePreview().status() == RecipePreview.Status.INVALID_SELECTION);
        action("s09_jei_stale_display_requests_server_validation", () -> {
            jeiPreviousAcknowledgement = screen().getMenu().lastAcknowledgedCommandId();
            jeiEvidence.add(MiniatureJeiClientTestSupport.transferSmelting(screen(), true, false));
        });
        waitFor("s09_server_rejects_recipe_removed_after_jei_indexing", () -> screen().getMenu().lastAcknowledgedCommandId() > jeiPreviousAcknowledgement
                && screen().getMenu().lastResult() != com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                && recipePreview().reference().recipeId().equals(jeiSmeltingRecipeId));
        serverStep("s09_rejected_recipe_selection_preserves_all_real_assets", this::assertJeiAssets);
        addCapture("s09-jei-server-rejected-stale-recipe", 1280, 720, 3);
        serverStep("s09_restore_native_server_recipe_manager", player -> {
            player.server.getRecipeManager().replaceRecipes(originalRecipes);
            MiniatureRecipeCatalog.get(player.server).invalidate();
        });
        waitFor("s09_restored_native_recipe_resolves_again", () -> recipePreview().status() == RecipePreview.Status.READY);
        addJeiCraftingScenario();
        if (ModList.get().isLoaded("mekanism") && !emiInstalled()) {
            addJeiInfusingScenario();
            addJeiDissolvingScenario();
        }
    }

    private void addPerTickRecipeScenario() {
        addLanguage("zh_cn");
        serverStep("per_tick_prepare_two_native_dissolution_machines", player -> {
            var items = player.registryAccess().registryOrThrow(Registries.ITEM);
            var gem = FactoryItemResources.keyOf(new ItemStack(items.get(ResourceLocation.parse("mekanism:fluorite_gem"))), player.registryAccess());
            var acid = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:sulfuric_acid");
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1),
                            new ItemStack(items.get(ResourceLocation.parse("mekanism:chemical_dissolution_chamber")), 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(gem, BigInteger.TWO), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(1, 0), new FactoryCell.Marker(new ResourceAmount(acid, BigInteger.valueOf(20)), GridDirection.DOWN))
                    .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow());
            installRecipeFixture(player, storage);
            player.containerMenu.setCarried(ItemStack.EMPTY);
            rememberJeiAssets(player);
            fixture = "per-tick-dissolution-full-batch-preview";
        });
        waitFor("per_tick_receive_two_machine_fixture", () -> cell(1, 1).type() == CellPreview.Type.MACHINE
                && cell(1, 1).resource().registryId().equals("mekanism:chemical_dissolution_chamber")
                && cell(1, 1).machineCount() == 2 && cursorEmpty());
        clickMachineRecipe("per_tick_select_native_recipe", 1, 1, () -> recipePreview() != null && recipePreview().status() == RecipePreview.Status.READY);
        action("per_tick_preview_uses_standard_single_machine_consumption", this::assertDissolutionPreview);
        clickAt("per_tick_compile_uses_actual_routed_batch", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY);
        action("per_tick_compiled_preview_uses_routed_consumption", this::assertDissolutionPreview);
        serverStep("per_tick_preview_preserves_real_assets", this::assertJeiAssets);
        addCapture("per-tick-dissolution-chemical-budget", 1280, 720, 3, new Hover("per_tick_chemical_budget", 213, 83));
    }

    private void assertDissolutionPreview() {
        var preview = recipePreview();
        int batches = preview.status() == RecipePreview.Status.COMPILED ? 2 : 1;
        require(preview.reference().recipeId().equals("mekanism:processing/uranium/hydrofluoric_acid")
                        && preview.inputs().size() == 2 && preview.outputs().size() == 1
                        && preview.inputs().stream().anyMatch(input -> input.resource().registryId().equals("mekanism:fluorite_gem")
                        && input.amount().equals(BigInteger.valueOf(batches)))
                        && preview.inputs().stream().anyMatch(input -> input.resource().registryId().equals("mekanism:sulfuric_acid")
                        && input.amount().equals(BigInteger.valueOf(10L * batches)))
                        && preview.outputs().getFirst().amount().equals(BigInteger.valueOf(1000L * batches)),
                "Dissolution preview does not distinguish standard recipe from the compiled routed batch");
    }

    private void addJeiDissolvingScenario() {
        serverStep("per_tick_jei_prepare_machine_without_markers", player -> {
            var items = player.registryAccess().registryOrThrow(Registries.ITEM);
            installRecipeFixture(player, FactoryStorage.empty().insertMachine(new GridPosition(1, 1),
                    new ItemStack(items.get(ResourceLocation.parse("mekanism:chemical_dissolution_chamber")), 2), player.registryAccess()).storage());
            rememberJeiAssets(player);
        });
        waitFor("per_tick_jei_receive_dissolution_machine", () -> cell(1, 1).resource().registryId().equals("mekanism:chemical_dissolution_chamber")
                && cell(1, 1).machineCount() == 2 && cell(0, 1).type() == CellPreview.Type.EMPTY);
        clickMachineRecipe("per_tick_jei_select_dissolution_machine", 1, 1, this::jeiMachineSelected);
        action("per_tick_jei_open_real_dissolution_category", () -> {
            MiniatureJeiClientTestSupport.findDissolvingRecipe();
            jeiRetainedEditor = screen();
            jeiEvidence.add(MiniatureJeiClientTestSupport.showDissolving(jeiRetainedEditor));
        });
        action("per_tick_jei_transfer_real_item_and_chemical_inputs", () -> {
            jeiPreviousAcknowledgement = jeiRetainedEditor.getMenu().lastAcknowledgedCommandId();
            jeiEvidence.add(MiniatureJeiClientTestSupport.transferDissolving(jeiRetainedEditor));
        });
        action("per_tick_jei_close_native_browser", () -> MiniatureJeiClientTestSupport.closeBrowser(jeiRetainedEditor));
        waitFor("per_tick_jei_server_accepts_selection", () -> screen().getMenu().lastAcknowledgedCommandId() > jeiPreviousAcknowledgement
                && screen().getMenu().lastResult() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                && recipePreview() != null && recipePreview().status() == RecipePreview.Status.READY);
        action("per_tick_jei_preview_recomputes_full_native_batch", this::assertDissolutionPreview);
        serverStep("per_tick_jei_selection_preserves_assets", this::assertJeiAssets);
    }

    private void addJeiCraftingScenario() {
        serverStep("s09_prepare_native_crafting_machine_without_markers", player -> {
            var old = factory(player).factoryState().storage().orElseThrow();
            installRecipeFixture(player, FactoryStorage.empty().insertMachine(new GridPosition(1, 1),
                    new ItemStack(Items.CRAFTING_TABLE), player.registryAccess()).storage()
                    .withInputs(old.inputs()).withOutputs(old.outputs()));
            rememberJeiAssets(player);
            fixture = "s09-native-jei-shaped-crafting-layouts";
        });
        waitFor("s09_receive_native_crafting_machine", () -> cell(1, 1).type() == CellPreview.Type.MACHINE
                && cell(1, 1).resource().registryId().equals("minecraft:crafting_table"));
        clickMachineRecipe("s09_select_native_crafting_machine", 1, 1, this::jeiMachineSelected);
        addJeiCraftingTransfer("crafting_table", 2, 2, List.of(0, 1, 3, 4), List.of(0, 1, 2, 3), true);
        addJeiCraftingTransfer("chest", 3, 3, List.of(0, 1, 2, 3, 5, 6, 7, 8), List.of(0, 1, 2, 3, 5, 6, 7, 8), false);
        serverStep("s09_both_native_crafting_transfers_preserve_all_real_assets", this::assertJeiAssets);
        addCapture("s09-jei-native-crafting-hole-selected", 1280, 720, 3);
    }

    private void addJeiCraftingTransfer(String name, int width, int height, List<Integer> guiSlots,
                                        List<Integer> patternSlots, boolean captureBrowser) {
        action("s09_find_native_jei_" + name + "_recipe", () ->
                MiniatureJeiClientTestSupport.findCraftingRecipe("minecraft:" + name));
        if (!emiInstalled()) {
            action("s09_open_native_" + name + "_recipes_gui", () -> {
                jeiRetainedEditor = screen();
                jeiEvidence.add(MiniatureJeiClientTestSupport.showCrafting(jeiRetainedEditor));
            });
            if (captureBrowser) addRecipeBrowserCapture("s09-jei-native-2x2-crafting-browser");
        }
        action("s09_transfer_" + name + "_using_actual_jei_input_slots", () -> {
            var editor = emiInstalled() ? screen() : jeiRetainedEditor;
            if (!emiInstalled()) MiniatureJeiClientTestSupport.verifyNativeBrowser(editor);
            jeiPreviousAcknowledgement = editor.getMenu().lastAcknowledgedCommandId();
            var observed = MiniatureJeiClientTestSupport.transferCrafting(editor, guiSlots, patternSlots);
            jeiExpectedBindings = observed.bindings();
            jeiEvidence.add(observed.evidence());
        });
        if (!emiInstalled()) action("s09_close_native_" + name + "_browser", () ->
                MiniatureJeiClientTestSupport.closeBrowser(jeiRetainedEditor));
        waitFor("s09_server_accepts_" + name + "_exact_bindings_and_pattern_dimensions", () ->
                screen().getMenu().lastAcknowledgedCommandId() > jeiPreviousAcknowledgement
                        && screen().getMenu().lastResult() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                        && recipePreview() != null && recipePreview().status() == RecipePreview.Status.READY
                        && recipePreview().reference().recipeId().equals("minecraft:" + name)
                        && recipePreview().reference().width() == width && recipePreview().reference().height() == height
                        && recipePreview().reference().bindings().equals(jeiExpectedBindings));
    }

    private void addJeiInfusingScenario() {
        serverStep("s09_prepare_native_mekanism_infuser_without_markers", player -> {
            var old = factory(player).factoryState().storage().orElseThrow();
            Item machine = player.registryAccess().registryOrThrow(Registries.ITEM)
                    .get(ResourceLocation.parse("mekanism:chemical_infuser"));
            require(machine != null && machine != Items.AIR, "Mekanism infuser item is not registered");
            installRecipeFixture(player, FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(machine),
                    player.registryAccess()).storage().withInputs(old.inputs()).withOutputs(old.outputs()));
            rememberJeiAssets(player);
            fixture = "s09-native-mekanism-jei-infuser-input-order";
        });
        waitFor("s09_receive_native_mekanism_infuser", () -> cell(1, 1).type() == CellPreview.Type.MACHINE
                && cell(1, 1).resource().registryId().equals("mekanism:chemical_infuser"));
        clickMachineRecipe("s09_select_native_mekanism_infuser", 1, 1, this::jeiMachineSelected);
        action("s09_open_native_mekanism_infuser_recipe_browser", () -> {
            jeiInfusingRecipeId = MiniatureJeiClientTestSupport.findInfusingRecipe();
            jeiRetainedEditor = screen();
            jeiEvidence.add(MiniatureJeiClientTestSupport.showInfusing(jeiRetainedEditor));
        });
        addRecipeBrowserCapture("s09-jei-native-mekanism-infuser-browser");
        action("s09_transfer_native_infuser_left_then_right_chemical_excluding_output", () -> {
            MiniatureJeiClientTestSupport.verifyNativeBrowser(jeiRetainedEditor);
            jeiPreviousAcknowledgement = jeiRetainedEditor.getMenu().lastAcknowledgedCommandId();
            var observed = MiniatureJeiClientTestSupport.transferInfusing(jeiRetainedEditor);
            jeiExpectedBindings = observed.bindings();
            jeiEvidence.add(observed.evidence());
        });
        action("s09_close_native_infuser_browser_back_to_editor", () -> MiniatureJeiClientTestSupport.closeBrowser(jeiRetainedEditor));
        waitFor("s09_server_accepts_native_infuser_exact_input_order", () ->
                screen().getMenu().lastAcknowledgedCommandId() > jeiPreviousAcknowledgement
                        && screen().getMenu().lastResult() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                        && recipePreview() != null && recipePreview().status() == RecipePreview.Status.READY
                        && recipePreview().reference().recipeId().equals(jeiInfusingRecipeId)
                        && recipePreview().reference().bindings().equals(jeiExpectedBindings));
        serverStep("s09_native_infuser_selection_preserves_all_real_assets", this::assertJeiAssets);
        addCapture("s09-jei-native-infuser-selected", 1280, 720, 3);
    }

    private void addJeiDrop(String name, java.util.function.Supplier<Object> ingredient,
                            java.util.function.Supplier<double[]> point, BooleanSupplier accepted) {
        action(name + "_dispatch_via_registered_jei_handler", () -> {
            double[] relative = point.get();
            double x = screen().getGuiLeft() + relative[0];
            double y = screen().getGuiTop() + relative[1];
            moveActualCursor(x, y);
            jeiEvidence.add(MiniatureJeiClientTestSupport.drop(screen(), ingredient.get(), x, y));
        });
        waitFor(name + "_authoritative_acknowledgement", () -> accepted.getAsBoolean()
                && ItemStack.matches(jeiCursor, screen().getMenu().getCarried()));
    }

    private boolean jeiMachineSelected() {
        return cursorEmpty() && cell(1, 1).type() == CellPreview.Type.MACHINE && screen().recipeTransferProblem() == null;
    }

    private void verifyJeiTargets(Object ingredient, int count, double[] allowed, double[] excluded) {
        jeiEvidence.add(MiniatureJeiClientTestSupport.verifyTargets(screen(), ingredient, count,
                screen().getGuiLeft() + allowed[0], screen().getGuiTop() + allowed[1],
                screen().getGuiLeft() + excluded[0], screen().getGuiTop() + excluded[1]));
    }

    private static double[] fittedPoint(int size, int x, int y) {
        double width = (size + 1) * 24 + size * 3;
        double height = size * 24 + (size - 1) * 3;
        double scale = Math.min(172 / width, 81 / height);
        return new double[]{8 + (172 - width * scale) / 2 + (x * 27 + 12) * scale,
                51 + (81 - height * scale) / 2 + (y * 27 + 12) * scale};
    }

    private boolean exactGhostMarker(int x, int y, ResourceKey resource, int amount) {
        return cell(x, y).type() == CellPreview.Type.MARKER && cell(x, y).resource().equals(resource)
                && cell(x, y).quantity().equals(BigInteger.valueOf(amount));
    }

    private void rememberJeiAssets(ServerPlayer player) {
        jeiAssetStorage = factory(player).factoryState().storage().orElseThrow();
        jeiCursor = player.containerMenu.getCarried().copy();
        jeiPlayerAssets = java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                .mapToObj(index -> player.getInventory().getItem(index).copy()).toList();
    }

    private void assertJeiAssets(ServerPlayer player) {
        var actual = factory(player).factoryState().storage().orElseThrow();
        require(actual.inputs().equals(jeiAssetStorage.inputs()) && actual.outputs().equals(jeiAssetStorage.outputs())
                && actual.machines().entries().equals(jeiAssetStorage.machines().entries()), "JEI interaction changed real factory assets");
        require(ItemStack.matches(jeiCursor, player.containerMenu.getCarried()), "JEI interaction changed the server cursor");
        for (int index = 0; index < jeiPlayerAssets.size(); index++) {
            require(ItemStack.matches(jeiPlayerAssets.get(index), player.getInventory().getItem(index)), "JEI interaction changed player inventory slot " + index);
        }
    }

    private void addProductRoutingScenario() {
        if (!ModList.get().isLoaded("mekanism")) return;
        serverStep("products_prepare_native_separator", player -> {
            var item = player.registryAccess().registryOrThrow(Registries.ITEM).get(ResourceLocation.parse("mekanism:electrolytic_separator"));
            var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(item, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(new ResourceKey(ResourceKind.FLUID, "minecraft:water"), BigInteger.valueOf(999)), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow())
                    .withConfiguration(new GridPosition(1, 0), new FactoryCell.Arrow()).withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
            installRecipeFixture(player, storage); rememberJeiAssets(player);
            fixture = "individual-hydrogen-oxygen-output-directions";
        });
        waitFor("products_receive_native_separator", () -> cell(1, 1).resource() != null && cell(1, 1).resource().registryId().equals("mekanism:electrolytic_separator"));
        clickMachineRecipe("products_select_separator", 1, 1, () -> recipePreview() != null && recipePreview().outputs().getFirst().resource().registryId().equals("mekanism:hydrogen"));
        for (var direction : List.of(GridDirection.DOWN, GridDirection.LEFT, GridDirection.UP)) {
            clickAt("products_hydrogen_" + direction, 268, 84, 1, () -> cell(1, 1).outputDirections().get(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen")) == direction
                    && cell(1, 1).outputDirection(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen")) == GridDirection.RIGHT);
        }
        clickAt("products_balance_separate_hydrogen_and_oxygen", 98, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                && cell(0, 1).quantity().equals(BigInteger.TWO)
                && compilation().outputRows().get(0).equals(List.of(new ResourceAmount(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"), BigInteger.TWO)))
                && compilation().outputRows().get(1).equals(List.of(new ResourceAmount(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen"), BigInteger.ONE))));
        addCapture("individual-hydrogen-up-oxygen-right", 1280, 720, 3, new Hover("product_direction", 268, 84));
        for (var direction : List.of(GridDirection.DOWN, GridDirection.LEFT, GridDirection.UP, GridDirection.RIGHT)) {
            clickCell("products_rotate_all_ports_" + direction, 1, 1, 1, () -> cell(1, 1).direction() == direction
                    && cell(1, 1).outputDirection(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen")) == direction);
        }
        serverStep("products_preserve_real_assets", this::assertJeiAssets);
        action("products_close", () -> screen().onClose());
        serverStep("products_reopen", player -> factory(player).openMenu(player));
        waitFor("products_reopen_preserves_ports", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && cell(1, 1).outputDirection(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen")) == GridDirection.UP);

        serverStep("oxide_prepare_enrichment_without_yellow_cake_marker", player -> {
            var registry = player.registryAccess().registryOrThrow(Registries.ITEM);
            var uranium = FactoryItemResources.keyOf(new ItemStack(registry.get(ResourceLocation.parse("mekanism:ingot_uranium"))), player.registryAccess());
            var sulfur = FactoryItemResources.keyOf(new ItemStack(registry.get(ResourceLocation.parse("mekanism:dust_sulfur"))), player.registryAccess());
            var storage = FactoryStorage.empty()
                    .insertMachine(new GridPosition(1, 0), new ItemStack(registry.get(ResourceLocation.parse("mekanism:ultimate_enriching_factory")), 64), player.registryAccess()).storage()
                    .insertMachine(new GridPosition(2, 0), new ItemStack(registry.get(ResourceLocation.parse("mekanism:chemical_oxidizer")), 32), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(uranium, BigInteger.valueOf(180)), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(0, 2), new FactoryCell.Marker(new ResourceAmount(sulfur, BigInteger.valueOf(9)), GridDirection.RIGHT));
            var oxidizer = (FactoryCell.Machine) storage.layout().cellAt(new GridPosition(2, 0));
            var wrong = MiniatureRecipeCatalog.get(player.server).search(oxidizer.stack().variant(), List.of(sulfur), player.serverLevel()).candidates().getFirst().reference();
            installRecipeFixture(player, storage.withRecipe(new GridPosition(2, 0), wrong));
            fixture = "uranium-oxide-from-upstream-yellow-cake";
        });
        waitFor("oxide_receive_wrong_sulfur_selection", () -> cell(2, 0).type() == CellPreview.Type.MACHINE && cell(2, 0).machineCount() == 32);
        clickMachineRecipe("oxide_cycle_to_upstream_uranium_recipe", 2, 0, () -> screen().getMenu().snapshot().recipes().get(2) != null
                && screen().getMenu().snapshot().recipes().get(2).reference().recipeId().equals("mekanism:processing/uranium/uranium_oxide"));
        addCapture("uranium-oxide-upstream-recipe-selected", 1280, 720, 3, new Hover("oxide_recipe", 230, 67));
    }

    private void addWeightedSplitScenario() {
        serverStep("split_prepare_eight_ores_one_furnace", player -> {
            var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess());
            var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.valueOf(8)), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow())
                    .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
            installRecipeFixture(player, storage);
            fixture = "weighted-split-one-to-three";
        });
        waitFor("split_receive_one_machine_fixture", () -> cell(1, 1).machineCount() == 1 && cell(2, 0).type() == CellPreview.Type.ARROW);
        clickCell("split_select_arrow", 2, 1, 0, () -> cell(2, 1).weights().equals(Map.of(GridDirection.RIGHT, 1)));
        clickAt("split_choose_up_branch", 284, 117, 0, () -> quantityField().getValue().equals("0"));
        action("split_type_up_weight_three", () -> typeQuantity("3"));
        clickAt("split_apply_up_weight", 289, 87, 0, () -> cell(2, 1).weights().equals(Map.of(GridDirection.RIGHT, 1, GridDirection.UP, 3)));
        clickAt("split_compile_one_to_three", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                && compilation().outputRows().get(0).getFirst().amount().equals(BigInteger.valueOf(6))
                && compilation().outputRows().get(1).getFirst().amount().equals(BigInteger.TWO)
                && compilation().requiredTicks() == 160);
        addLanguage("zh_cn");
        addCapture("weighted-split-ratio-editor", 1280, 720, 3, new Hover("split_ratio", 213, 87));
        clickCell("balance_select_input_marker", 0, 1, 0, () -> marker(0, 1, "minecraft:iron_ore", 8, GridDirection.RIGHT));
        action("balance_prepare_nonintegral_input", () -> typeQuantity("7"));
        action("balance_draft_disables_button", () -> {
            var widget = screen().children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget button
                    && button.getX() == screen().getGuiLeft() + 90 && button.getY() == screen().getGuiTop() + 33)
                    .map(child -> (net.minecraft.client.gui.components.AbstractWidget) child).findFirst().orElseThrow();
            require(!widget.active && quantityField().getValue().equals("7"), "Automatic balance ignored an unsubmitted quantity draft");
        });
        clickAt("balance_apply_invalid_seven", 289, 87, 0, () -> marker(0, 1, "minecraft:iron_ore", 7, GridDirection.RIGHT));
        clickAt("balance_manual_compile_reports_split_fraction", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.FAILED
                && compilation().diagnostic().code() == com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.NON_INTEGRAL_SPLIT);
        clickAt("balance_button_solves_and_compiles", 98, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                && marker(0, 1, "minecraft:iron_ore", 4, GridDirection.RIGHT)
                && compilation().outputRows().get(0).getFirst().amount().equals(BigInteger.valueOf(3))
                && compilation().outputRows().get(1).getFirst().amount().equals(BigInteger.ONE)
                && cell(1, 1).machineCount() == 1 && cell(2, 1).weights().equals(Map.of(GridDirection.RIGHT, 1, GridDirection.UP, 3)));
        addCapture("automatic-balance-four-inputs-three-and-one", 1280, 720, 3, new Hover("auto_balance", 98, 41));
        clickCell("split_rotate_all_branches", 2, 1, 1, () -> cell(2, 1).weights().equals(Map.of(GridDirection.DOWN, 1, GridDirection.RIGHT, 3)));
        clickAt("split_choose_down_branch", 228, 117, 0, () -> quantityField().getValue().equals("1"));
        action("split_disable_down_with_zero", () -> typeQuantity("0"));
        clickAt("split_apply_zero", 289, 87, 0, () -> cell(2, 1).weights().equals(Map.of(GridDirection.RIGHT, 3)));
        action("split_close_editor", () -> screen().onClose());
        serverStep("split_reopen_editor", player -> factory(player).openMenu(player));
        waitFor("split_reopen_preserves_weight", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && cell(2, 1).weights().equals(Map.of(GridDirection.RIGHT, 3)));
    }

    private void addMachineDirectionScenario() {
        serverStep("direction_prepare_downward_output_route", player -> {
            FactoryStorage storage = FactoryStorage.empty()
                    .insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(
                            FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess()), BigInteger.TWO), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(1, 2), new FactoryCell.Arrow())
                    .withConfiguration(new GridPosition(2, 2), new FactoryCell.Arrow());
            installRecipeFixture(player, storage);
            fixture = "direction-machine-products-down";
        });
        waitFor("direction_receive_fixture", () -> cell(1, 1).machineCount() == 2 && cell(1, 2).type() == CellPreview.Type.ARROW
                && cell(2, 1).type() == CellPreview.Type.EMPTY);
        clickAt("direction_wrong_output_fails_compilation", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.FAILED);
        clickCell("direction_right_click_turns_machine_down", 1, 1, 1, () -> cell(1, 1).direction() == GridDirection.DOWN
                && cell(1, 1).machineCount() == 2 && cursorEmpty());
        clickAt("direction_recipe_summary_selects_without_rotating", 230, 67, 0, () -> recipePreview().status() == RecipePreview.Status.READY
                && cell(1, 1).direction() == GridDirection.DOWN);
        action("direction_remember_recipe", () -> clientRecipeSelection = recipePreview().reference());
        for (GridDirection direction : List.of(GridDirection.LEFT, GridDirection.UP, GridDirection.RIGHT, GridDirection.DOWN)) {
            clickCell("direction_machine_rotation_" + direction, 1, 1, 1, () -> cell(1, 1).direction() == direction
                    && clientRecipeSelection.equals(recipePreview().reference()) && cell(1, 1).machineCount() == 2);
        }
        clickAt("direction_products_reach_lower_output_row", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                && compilation().outputRows().size() == 1 && compilation().outputRows().containsKey(2)
                && compilation().outputs().getFirst().amount().equals(BigInteger.TWO));
        addLanguage("zh_cn");
        addCapture("direction-machine-output-down", 1280, 720, 3,
                new Hover("machine_direction", GRID_LEFT + CELL_PITCH + CELL_CENTER, GRID_TOP + CELL_PITCH + CELL_CENTER));
        action("direction_close_editor", () -> screen().onClose());
        serverStep("direction_reopen_editor", player -> factory(player).openMenu(player));
        waitFor("direction_reopened_machine_preserves_direction_and_recipe", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && cell(1, 1).direction() == GridDirection.DOWN
                && clientRecipeSelection.equals(recipePreview().reference()));
    }

    private void addCompilationScenario() {
        addLanguage("en_us");
        serverStep("s06_prepare_connected_two_machine_line", player -> {
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(
                            FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess()), BigInteger.TWO), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow());
            installRecipeFixture(player, storage);
            expectedVisualState = factory(player).factoryState().save(); fixture = "s06-compiled-smelting-line";
        });
        waitFor("s06_receive_uncompiled_line", () -> marker(0, 1, "minecraft:iron_ore", 2, GridDirection.RIGHT)
                && compilation().status() == CompilationPreview.Status.NOT_COMPILED);
        clickAt("s06_compile_from_real_button", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY
                && compilation().outputs().getFirst().amount().equals(BigInteger.TWO));
        clickCell("s06_select_compiled_output_row", 3, 1, 0, () -> compilation().outputRows().get(1).size() == 1);
        for (int[] size : List.of(new int[]{1280, 720}, new int[]{1920, 1080})) {
            for (int scale : List.of(2, 3, 4)) addCapture("s06-output-" + size[0] + "-" + scale, size[0], size[1], scale);
        }
        addCapture("s06-output-quantity-tooltip", 1280, 720, 3, new Hover("compiled_output", 233, 60));
        serverStep("s06_compilation_and_output_view_never_change_assets", player -> require(expectedVisualState.equals(factory(player).factoryState().save()), "Compiled preview changed real assets"));
        clickCell("s06_select_input_to_unbalance", 0, 1, 0, () -> true);
        action("s06_type_unbalanced_marker", () -> typeQuantity("1"));
        clickAt("s06_apply_marker_invalidates_compilation", 289, 87, 0, () -> marker(0, 1, "minecraft:iron_ore", 1, GridDirection.RIGHT)
                && compilation().status() == CompilationPreview.Status.NOT_COMPILED);
        clickAt("s06_compile_reports_machine_shortage", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.FAILED
                && compilation().diagnostic().code() == FactoryDiagnostic.Code.MISSING_INPUT
                && compilation().diagnostic().expected().orElseThrow().equals(BigInteger.TWO));
        addLanguage("zh_cn");
        addCapture("s06-error-zh_cn", 1280, 720, 3);
        addCapture("s06-error-context-zh_cn", 1920, 1080, 4, new Hover("compile_error", 240, 17));
        clickCell("s06_reselect_input_to_repair", 0, 1, 0, () -> true);
        action("s06_type_balanced_marker", () -> typeQuantity("2"));
        clickAt("s06_apply_repaired_marker", 289, 87, 0, () -> marker(0, 1, "minecraft:iron_ore", 2, GridDirection.RIGHT));
        clickAt("s06_recompile_after_repair", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY);
        action("s06_close_compiled_editor", () -> screen().onClose());
        serverStep("s06_reopen_same_compiled_factory", player -> factory(player).openMenu(player));
        waitFor("s06_compiled_result_survives_menu_reopen", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && compilation().status() == CompilationPreview.Status.READY);
        serverStep("s06_invalidate_catalog_generation", player -> MiniatureRecipeCatalog.get(player.server).invalidate());
        waitFor("s06_catalog_change_clears_visible_outputs", () -> compilation().status() == CompilationPreview.Status.NOT_COMPILED && compilation().outputRows().isEmpty());
        clickAt("s06_compile_current_catalog", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY);
        serverStep("s06_prepare_native_multioutput_recipe", player -> {
            Recipe<?> recipe = Recipe.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                    {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:milk_bucket"}],"result":{"id":"minecraft:cake"}}
                    """)).getOrThrow();
            player.server.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("flux_overdrive", "s06_client_returns"), recipe)));
            MiniatureRecipeCatalog.get(player.server).invalidate();
            FactoryStorage storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.CRAFTING_TABLE, 2), player.registryAccess()).storage()
                    .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(new ResourceAmount(
                            FactoryItemResources.keyOf(new ItemStack(Items.MILK_BUCKET), player.registryAccess()), BigInteger.TWO), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow());
            installRecipeFixture(player, storage); expectedVisualState = factory(player).factoryState().save();
            fixture = "s06-native-multiple-outputs";
        });
        waitFor("s06_receive_multioutput_configuration", () -> marker(0, 1, "minecraft:milk_bucket", 2, GridDirection.RIGHT));
        clickAt("s06_compile_multiple_outputs", 116, 41, 0, () -> compilation().status() == CompilationPreview.Status.READY && compilation().outputRows().get(1).size() == 2);
        clickCell("s06_select_multiple_outputs", 3, 1, 0, () -> true);
        addCapture("s06-multiple-outputs-zh_cn", 1280, 720, 3);
        addCapture("s06-returned-bucket-tooltip", 1920, 1080, 4, new Hover("compiled_bucket", 233, 78));
        clickMachineRecipe("s06_select_machine_recipe_without_extracting", 1, 1, () -> recipePreview().status() == RecipePreview.Status.READY && cursorEmpty());
        serverStep("s06_capture_state_after_explicit_recipe_selection", player -> expectedVisualState = factory(player).factoryState().save());
        clickAt("s06_compile_preserves_machine_selection", 116, 41, 0, () -> recipePreview().status() == RecipePreview.Status.COMPILED && cursorEmpty());
        addCapture("s06-compiled-machine-zh_cn", 1280, 720, 3);
        serverStep("s06_restore_recipes_and_check_no_production", player -> {
            require(expectedVisualState.equals(factory(player).factoryState().save()), "Multioutput preview created assets");
            player.server.getRecipeManager().replaceRecipes(originalRecipes); MiniatureRecipeCatalog.get(player.server).invalidate();
        });
    }

    private CompilationPreview compilation() { return screen().getMenu().snapshot().compilation(); }

    private void installRecipeFixture(ServerPlayer player, FactoryStorage storage) {
        var before = factory(player).factoryState();
        require(before.storage().orElseThrow().batch() == null, "Cannot replace an unfinished fixture batch");
        long revision = before.storage().orElseThrow().layout().revision() + 1;
        FactoryStorage rebased = FactoryStorage.restore(new FactoryLayout(storage.layout().size(), storage.layout().cells(), revision),
                storage.machines(), storage.inputs(), storage.outputs(), player.registryAccess())
                .withBatch(null, before.storage().orElseThrow().lastPaidGameTime());
        require(factory(player).applyPreparedState(before, FactoryState.of(rebased, player.registryAccess())), "Recipe fixture not installed");
    }

    private void addInteractionScenario() {
        addInputCapacityScenario();
        clickInventory("pick_up_five_machines", 9, () -> cursor(Items.FURNACE, 5));
        clickCell("place_real_machines", 0, 0, 0,
                () -> cell(0, 0).type() == CellPreview.Type.MACHINE && cell(0, 0).machineCount() == 5 && cursorEmpty());
        clickCell("left_click_keeps_real_machine_stack", 0, 0, 0,
                () -> cell(0, 0).machineCount() == 5 && cursorEmpty());
        addCapture("machine-left-select-delete-hint", 1280, 720, 3, new Hover("machine_controls", 54, 65));
        clickInventory("hold_incompatible_item_before_delete", 10, () -> cursor(Items.IRON_ORE, 17));
        long[] deleteAcknowledgement = new long[1];
        action("delete_machine_with_incompatible_cursor", () -> {
            deleteAcknowledgement[0] = screen().getMenu().lastAcknowledgedCommandId();
            screen().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
        });
        waitFor("delete_rejection_preserves_machine_and_cursor", () -> screen().getMenu().lastAcknowledgedCommandId() > deleteAcknowledgement[0]
                && screen().getMenu().lastResult() != com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                && cell(0, 0).machineCount() == 5 && cursor(Items.IRON_ORE, 17));
        clickInventory("return_incompatible_cursor_after_delete", 10, this::cursorEmpty);
        removeMachine("extract_real_machines", 0, 0,
                () -> cell(0, 0).type() == CellPreview.Type.EMPTY && cursor(Items.FURNACE, 5));
        clickInventory("return_machines_to_inventory", 9, this::cursorEmpty);
        clickInventory("pick_up_marker_source", 10, () -> cursor(Items.IRON_ORE, 17));
        clickCell("mark_without_consuming_source", 1, 0, 1,
                () -> marker(1, 0, "minecraft:iron_ore", 1, GridDirection.RIGHT) && cursor(Items.IRON_ORE, 17));
        clickInventory("return_marker_source", 10, this::cursorEmpty);
        clickCell("select_marker_sidebar", 1, 0, 0, () -> true);
        addInvalidQuantity("empty", "");
        addInvalidQuantity("zero", "0");
        addInvalidQuantity("uint256_overflow", BigInteger.ONE.shiftLeft(256).toString());
        action("type_exact_marker_quantity", () -> typeQuantity("29"));
        clickAt("apply_exact_marker_quantity", 289, 87, 0,
                () -> marker(1, 0, "minecraft:iron_ore", 29, GridDirection.RIGHT));
        clickAt("change_marker_direction", 228, 117, 0,
                () -> marker(1, 0, "minecraft:iron_ore", 29, GridDirection.DOWN));
        clickCell("right_click_rotates_marker_and_keeps_quantity", 1, 0, 1,
                () -> marker(1, 0, "minecraft:iron_ore", 29, GridDirection.LEFT));
        clickInventory("pick_up_source_while_rotating_existing_marker", 10, () -> cursor(Items.IRON_ORE, 17));
        clickCell("held_item_does_not_replace_existing_marker", 1, 0, 1,
                () -> marker(1, 0, "minecraft:iron_ore", 29, GridDirection.UP) && cursor(Items.IRON_ORE, 17));
        clickInventory("return_source_after_marker_rotation", 10, this::cursorEmpty);
        action("delete_selected_marker", () -> screen().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0));
        waitFor("delete_only_clears_virtual_marker", () -> cell(1, 0).type() == CellPreview.Type.EMPTY);
        clickCell("create_arrow_in_cleared_marker_cell", 1, 0, 1, () -> arrow(1, 0, GridDirection.RIGHT));
        clickCell("right_click_rotates_arrow", 1, 0, 1, () -> arrow(1, 0, GridDirection.DOWN));
        clickCell("left_click_selects_arrow_ratios", 1, 0, 0, () -> arrow(1, 0, GridDirection.DOWN));
        action("delete_selected_arrow", () -> screen().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0));
        waitFor("delete_clears_virtual_arrow", () -> cell(1, 0).type() == CellPreview.Type.EMPTY);
        clickCell("empty_right_click_creates_right_arrow", 2, 0, 1, () -> arrow(2, 0, GridDirection.RIGHT));
        for (GridDirection direction : List.of(GridDirection.DOWN, GridDirection.LEFT, GridDirection.UP, GridDirection.RIGHT)) {
            clickCell("arrow_cycle_" + direction.name().toLowerCase(java.util.Locale.ROOT), 2, 0, 1,
                    () -> arrow(2, 0, direction));
        }
        clickInventory("pick_up_full_container", 11, () -> cursor(Items.WATER_BUCKET, 1));
        clickCell("container_item_marker_keeps_contents", 1, 1, 1,
                () -> marker(1, 1, "minecraft:water_bucket", 1, GridDirection.RIGHT) && cursor(Items.WATER_BUCKET, 1));
        clickInventory("return_full_container", 11, this::cursorEmpty);
        clickInventory("pick_up_machines_for_persistent_layout", 9, () -> cursor(Items.FURNACE, 5));
        clickCell("restore_machine_layout", 0, 0, 0,
                () -> cell(0, 0).type() == CellPreview.Type.MACHINE && cell(0, 0).machineCount() == 5 && cursorEmpty());
        removeMachine("extract_natural_limit_keep_remaining_machines", 0, 2,
                () -> cell(0, 2).type() == CellPreview.Type.MACHINE && cell(0, 2).machineCount() == 2
                        && cursor(Items.FURNACE, 1) && screen().getMenu().getCarried().getMaxStackSize() == 1);
        clickInventory("return_unstackable_machine", 13, this::cursorEmpty);
        clickAt("select_input_inventory_tab", 201, 143, 0, () -> true);
        clickInventory("pick_up_real_input_ore", 10, () -> cursor(Items.IRON_ORE, 17));
        clickAt("deposit_cursor_into_real_inputs", 295, 143, 0,
                () -> cursorEmpty() && resourceAmount(false, "minecraft:iron_ore", 17));
        addPendingInventoryDraftRegression();
        clickAt("right_click_configures_without_extracting_input", 230, 162, 1,
                () -> cursorEmpty() && quantityField().visible && resourceAmount(false, "minecraft:iron_ore", 17));
        clickAt("left_click_extracts_real_inputs", 230, 162, 0,
                () -> cursor(Items.IRON_ORE, 17) && screen().getMenu().snapshot().inputs().isEmpty());
        clickInventory("merge_remaining_inputs_in_inventory", 10, this::cursorEmpty);
        clickAt("select_output_inventory_tab", 245, 143, 0, () -> true);
        clickAt("right_click_extracts_one_real_output", 230, 162, 1,
                () -> cursor(Items.IRON_INGOT, 1) && resourceAmount(true, "minecraft:iron_ingot", 6));
        clickInventory("return_single_output_to_inventory", 14, this::cursorEmpty);
        clickAt("left_click_extracts_remaining_outputs", 230, 162, 0,
                () -> cursor(Items.IRON_INGOT, 6) && screen().getMenu().snapshot().outputs().isEmpty());
        clickInventory("merge_remaining_outputs_in_inventory", 14, this::cursorEmpty);
        clickAt("restore_input_inventory_tab", 201, 143, 0, () -> true);
        serverStep("assert_real_assets_and_marker_configuration", player -> {
            var storage = factory(player).factoryState().storage().orElseThrow();
            require(storage.machines().entry(new GridPosition(0, 0)).orElseThrow().count() == 5, "Wrong machine count");
            require(storage.machines().entry(new GridPosition(0, 2)).orElseThrow().count() == 2, "Remaining machines were cleared");
            require(player.getInventory().getItem(10).is(Items.IRON_ORE)
                    && player.getInventory().getItem(10).getCount() == 17, "Marker consumed source ore");
            require(player.getInventory().getItem(11).is(Items.WATER_BUCKET), "Marking drained the water bucket");
            require(player.getInventory().getItem(13).is(Items.FURNACE)
                    && player.getInventory().getItem(13).getMaxStackSize() == 1, "Singular machine was not returned intact");
            require(player.getInventory().getItem(14).is(Items.IRON_INGOT)
                    && player.getInventory().getItem(14).getCount() == 7, "Output transfer changed total ingot assets");
            require(storage.inputs().amounts().isEmpty() && storage.outputs().amounts().isEmpty(), "Resource transfers left unexpected assets");
            expectedState = factory(player).factoryState().save();
        });
        action("close_editor", () -> {
            oldSession = screen().getMenu().sessionId();
            screen().onClose();
        });
        waitFor("editor_closed_on_client", () -> !(Minecraft.getInstance().screen instanceof MiniatureUniverseScreen));
        serverStep("reopen_same_server_factory", player -> {
            require(expectedState.equals(factory(player).factoryState().save()), "Closing changed factory state");
            factory(player).openMenu(player);
        });
        waitFor("reopened_editor_restores_authoritative_layout", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && !screen().getMenu().sessionId().equals(oldSession) && cell(0, 0).machineCount() == 5
                && cell(0, 2).machineCount() == 2 && marker(1, 1, "minecraft:water_bucket", 1, GridDirection.RIGHT));
        clickCell("select_marker_for_screenshot", 1, 1, 0, () -> true);
    }

    private void addInputCapacityScenario() {
        clickInventory("pick_up_capacity_test_inputs", 10, () -> cursor(Items.IRON_ORE, 17));
        clickAt("deposit_capacity_test_inputs", 295, 143, 0, () -> cursorEmpty() && resourceAmount(false, "minecraft:iron_ore", 17));
        clickAt("right_click_opens_ingredient_capacity", 230, 162, 1, () -> quantityField().visible && cursorEmpty());
        for (String invalid : List.of("", "0", FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE).toString())) {
            action("capacity_reject_invalid_" + invalid, () -> {
                typeQuantity(invalid);
            });
            action("capacity_invalid_commit_disabled_" + invalid, () -> {
                var apply = screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getX() == screen().getGuiLeft() + 278 && widget.getY() == screen().getGuiTop() + 78)
                        .findFirst().orElseThrow();
                require(!apply.active, "Invalid cache limit left apply enabled");
            });
        }
        action("escape_discards_invalid_capacity_draft", () -> {
            screen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            require(quantityField().getValue().equals(FactoryLimits.MAX_INVENTORY_AMOUNT.toString()), "Escape did not restore saved capacity");
        });
        action("type_local_capacity_1000", () -> typeQuantity("1000"));
        clickAt("apply_local_capacity_1000", 289, 87, 0,
                () -> inputCapacity(Items.IRON_ORE).equals(BigInteger.valueOf(1000)));
        serverStep("verify_local_capacity_saved", player -> require(factory(player).factoryState().storage().orElseThrow()
                .configuredInputCapacity(FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), player.registryAccess()))
                .equals(BigInteger.valueOf(1000)), "Capacity was not saved on server"));
        clickInventory("pick_up_second_capacity_ingredient", 11, () -> cursor(Items.WATER_BUCKET, 1));
        clickAt("right_click_with_carried_stack_does_not_deposit", 230, 162, 1,
                () -> cursor(Items.WATER_BUCKET, 1) && resourceAmount(false, "minecraft:iron_ore", 17)
                        && quantityField().getValue().equals("1000"));
        clickAt("deposit_second_capacity_ingredient", 295, 143, 0,
                () -> cursorEmpty() && screen().getMenu().snapshot().inputs().size() == 2);
        clickAt("configure_second_ingredient", 230, 180, 1,
                () -> quantityField().getValue().equals(FactoryLimits.MAX_INVENTORY_AMOUNT.toString()));
        action("type_second_ingredient_capacity", () -> typeQuantity("2000"));
        clickAt("apply_independent_second_capacity", 289, 87, 0,
                () -> inputCapacity(Items.WATER_BUCKET).equals(BigInteger.valueOf(2000))
                        && inputCapacity(Items.IRON_ORE).equals(BigInteger.valueOf(1000)));
        clickAt("return_to_first_ingredient_capacity", 230, 162, 1,
                () -> quantityField().getValue().equals("1000"));
        addLanguage("zh_cn");
        addCapture("input-capacity-zh_cn", 1280, 720, 3);
        addCapture("input-capacity-tooltip-zh_cn", 1280, 720, 3, new Hover("input_capacity", 241, 57));
        action("capacity_enter_applies_limit_below_existing_stock", () -> {
            typeQuantity("1");
            screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        });
        waitFor("capacity_lowering_keeps_stock", () -> inputCapacity(Items.IRON_ORE).equals(BigInteger.ONE)
                && screen().getMenu().snapshot().inputs().stream().anyMatch(value -> value.resource().registryId().equals("minecraft:iron_ore")
                        && value.amount().equals(BigInteger.valueOf(17))) && inputCapacity(Items.WATER_BUCKET).equals(BigInteger.valueOf(2000)));
        action("close_capacity_menu", () -> screen().onClose());
        serverStep("reopen_capacity_menu", player -> factory(player).openMenu(player));
        waitFor("capacity_survives_reopen", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen local
                && local.getMenu().snapshotReady() && inputCapacity(Items.IRON_ORE).equals(BigInteger.ONE));
        clickAt("reopen_capacity_settings", 230, 162, 1, () -> quantityField().visible && quantityField().getValue().equals("1"));
        addLanguage("en_us");
        addCapture("input-capacity-en_us", 1280, 720, 3);
        clickAt("withdraw_capacity_test_inputs", 230, 162, 0, () -> cursor(Items.IRON_ORE, 17));
        clickInventory("return_capacity_test_inputs", 10, this::cursorEmpty);
        clickAt("depleted_ingredient_remains_configurable", 230, 180, 1,
                () -> quantityField().visible && quantityField().getValue().equals("1")
                        && screen().getMenu().snapshot().inputResources().size() == 2);
        clickAt("restore_only_depleted_ingredient_default", 240, 119, 0,
                () -> inputCapacity(Items.IRON_ORE).equals(FactoryLimits.MAX_INVENTORY_AMOUNT)
                        && inputCapacity(Items.WATER_BUCKET).equals(BigInteger.valueOf(2000))
                        && screen().getMenu().snapshot().inputResources().size() == 1);
        clickAt("reopen_second_ingredient_capacity", 230, 162, 1, () -> quantityField().getValue().equals("2000"));
        clickAt("restore_second_ingredient_default", 240, 119, 0,
                () -> screen().getMenu().snapshot().inputCapacities().isEmpty());
        clickAt("withdraw_second_capacity_ingredient", 230, 162, 0, () -> cursor(Items.WATER_BUCKET, 1));
        clickInventory("return_second_capacity_ingredient", 11, this::cursorEmpty);
        clickCell("close_capacity_settings", 1, 0, 0, () -> !quantityField().visible);
    }

    private BigInteger inputCapacity(Item item) {
        return screen().getMenu().snapshot().configuredInputCapacity(
                FactoryItemResources.keyOf(new ItemStack(item), Minecraft.getInstance().player.registryAccess()));
    }

    private void addInvalidQuantity(String name, String value) {
        long[] acknowledged = new long[1];
        action("type_invalid_quantity_" + name, () -> {
            acknowledged[0] = screen().getMenu().lastAcknowledgedCommandId();
            typeQuantity(value);
        });
        addClickStep("invalid_quantity_disables_commit_" + name, () -> {
            AbstractWidget apply = screen().children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(widget -> widget.getX() == screen().getGuiLeft() + 278
                            && widget.getY() == screen().getGuiTop() + 78).findFirst().orElseThrow();
            require(apply.visible && !apply.active, "Invalid quantity left the commit button active");
            click(289, 87, 0);
        }, () -> marker(1, 0, "minecraft:iron_ore", 1, GridDirection.RIGHT)
                && screen().getMenu().lastAcknowledgedCommandId() == acknowledged[0]);
    }

    private void typeQuantity(String value) {
        click(201, 87, 0);
        screen().keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        for (int index = 0; index < 78; index++) screen().keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        for (char character : value.toCharArray()) screen().charTyped(character, 0);
    }

    private void addPendingInventoryDraftRegression() {
        long[] beforeInventoryCommand = new long[1];
        clickCell("select_marker_for_pending_inventory_draft_regression", 1, 1, 0, () -> true);
        action("leave_unsubmitted_quantity_draft_before_inventory_command", () -> {
            typeQuantity("37");
            beforeInventoryCommand[0] = screen().getMenu().lastAcknowledgedCommandId();
            require(quantityField().isFocused(), "Quantity draft field is not focused");
        });
        addClickStep("inventory_click_then_enter_before_ack_preserves_quantity_draft", () -> {
            click(230, 162, 0);
            require(quantityField().isFocused(), "Resource-row click did not preserve quantity focus");
            require(screen().getMenu().lastAcknowledgedCommandId() == beforeInventoryCommand[0],
                    "Inventory command was acknowledged before same-action Enter");
            screen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, () -> {
            if (screen().getMenu().lastAcknowledgedCommandId() <= beforeInventoryCommand[0]
                    || !cursor(Items.IRON_ORE, 17) || !screen().getMenu().snapshot().inputs().isEmpty()) return false;
            // Mouse input refreshes the view after the ACK so a stale draft cannot hide an overwrite.
            click(201, 87, 0);
            require(screen().getMenu().lastAcknowledgedCommandId() == beforeInventoryCommand[0] + 1,
                    "Enter sent a second command while the inventory command was pending");
            require(quantityField().getValue().equals("37"), "Inventory acknowledgement overwrote unsubmitted quantity draft");
            require(marker(1, 1, "minecraft:water_bucket", 1, GridDirection.RIGHT), "Pending Enter changed marker quantity");
            return true;
        });
        serverStep("pending_enter_keeps_authoritative_marker_quantity_unchanged", player -> {
            var stored = factory(player).factoryState().storage().orElseThrow();
            FactoryCell cell = stored.layout().cellAt(new GridPosition(1, 1));
            require(cell instanceof FactoryCell.Marker marker && marker.resource().amount().equals(BigInteger.ONE),
                    "Pending Enter changed authoritative marker quantity");
        });
        clickAt("explicitly_submit_preserved_quantity_draft_after_ack", 289, 87, 0,
                () -> marker(1, 1, "minecraft:water_bucket", 37, GridDirection.RIGHT));
        action("restore_original_quantity_after_pending_command_regression", () -> typeQuantity("1"));
        clickAt("submit_original_quantity_after_pending_command_regression", 289, 87, 0,
                () -> marker(1, 1, "minecraft:water_bucket", 1, GridDirection.RIGHT));
        clickAt("restore_input_assets_after_pending_command_regression", 230, 162, 0,
                () -> cursorEmpty() && resourceAmount(false, "minecraft:iron_ore", 17));
    }

    private EditBox quantityField() {
        return screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(widget -> widget.getX() == screen().getGuiLeft() + 188
                        && widget.getY() == screen().getGuiTop() + 78).findFirst().orElseThrow();
    }

    private void addViewportScenario() {
        long[] revision = new long[1];
        long[] acknowledgement = new long[1];
        action("record_output_column_read_only_state", () -> {
            revision[0] = screen().getMenu().revision();
            acknowledgement[0] = screen().getMenu().lastAcknowledgedCommandId();
        });
        clickCell("left_click_output_column_selects_without_editing", 3, 1, 0, () -> cursorEmpty());
        clickCell("right_click_output_column_preserves_state", 3, 1, 1, () -> cursorEmpty());
        serverStep("output_column_preserves_authoritative_assets_and_revision", player ->
                require(expectedState.equals(factory(player).factoryState().save()), "Output-column selection edited factory assets"));
        action("output_column_sends_no_edit_command", () -> require(screen().getMenu().revision() == revision[0]
                && screen().getMenu().lastAcknowledgedCommandId() == acknowledgement[0], "Output-column selection sent an edit command"));
        action("mouse_wheel_zooms_about_grid_point", () -> scrollGrid(94, 91.5, 1));
        action("middle_drag_pans_zoomed_grid", () -> panGrid(94, 91.5, -9, 12));
        // These expected coordinates are calculated independently from the screen's viewport instance.
        clickAt("zoomed_panned_hit_clears_expected_arrow", 101.2, 71.1, 0,
                () -> cell(2, 0).type() == CellPreview.Type.EMPTY);
        clickAt("zoomed_panned_hit_recreates_expected_arrow", 101.2, 71.1, 1,
                () -> arrow(2, 0, GridDirection.RIGHT));
        clickAt("fit_grid_after_zoom_and_pan", 170, 41, 0, () -> true);
        clickFittedCell("select_fitted_marker_for_capture", 3, 1, 1, 0, () -> true);
    }

    private void prepareDisplayResources(ServerPlayer player) {
        TileMiniatureUniverse tile = factory(player);
        FactoryState before = tile.factoryState();
        FactoryStorage display = before.storage().orElseThrow()
                .withInputs(resourceFixture(player, new Item[]{Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE,
                        Items.COAL, Items.COBBLESTONE, Items.RAW_IRON}, false))
                .withOutputs(resourceFixture(player, new Item[]{Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                        Items.STONE, Items.GLASS}, true));
        require(tile.applyPreparedState(before, FactoryState.of(display, player.registryAccess())), "Could not install display fixture");
        expectedVisualState = tile.factoryState().save();
        fixture = "3x3-independent-populated-display-fixture";
    }

    private ResourceInventory resourceFixture(ServerPlayer player, Item[] items, boolean outputs) {
        Map<ResourceKey, BigInteger> amounts = new LinkedHashMap<>();
        for (int index = 0; index < items.length; index++) {
            BigInteger amount = index == 0 ? BigInteger.TEN.pow(29).add(BigInteger.valueOf(123456))
                    : BigInteger.valueOf((outputs ? 96 : 256) + index * 37L);
            amounts.put(FactoryItemResources.keyOf(new ItemStack(items[index]), player.registryAccess()), amount);
        }
        return new ResourceInventory(amounts);
    }

    private void addLargeGridScenario() {
        action("close_before_independent_11x11_fixture", () -> screen().onClose());
        serverStep("load_existing_11x11_state_through_codec_not_upgrade", player -> {
            TileMiniatureUniverse tile = factory(player);
            FactoryState before = tile.factoryState();
            FactoryStorage stored = before.storage().orElseThrow();
            FactoryLayout layout = FactoryLayout.empty(11);
            for (int y = 0; y < stored.layout().size(); y++) {
                for (int x = 0; x < stored.layout().size(); x++) {
                    GridPosition position = new GridPosition(x, y);
                    layout = layout.withCell(position, stored.layout().cellAt(position));
                }
            }
            layout = layout.withCell(new GridPosition(10, 10), new FactoryCell.Arrow(GridDirection.RIGHT));
            layout = layout.withCell(new GridPosition(9, 10), new FactoryCell.Marker(
                    FactoryItemResources.keyOf(new ItemStack(Items.DIAMOND), player.registryAccess())));
            FactoryStorage restored = FactoryStorage.restore(layout, stored.machines(), stored.inputs(), stored.outputs(), player.registryAccess());
            FactoryState encoded = FactoryState.of(restored, player.registryAccess());
            FactoryState decoded = FactoryState.load(encoded.save(), player.registryAccess());
            require(decoded.storage().isPresent() && tile.applyPreparedState(before, decoded), "11x11 codec fixture was rejected");
            fixture = "11x11-existing-state-codec-fixture-not-upgrade";
            tile.openMenu(player);
        });
        waitFor("receive_11x11_authoritative_fixture", () -> Minecraft.getInstance().screen instanceof MiniatureUniverseScreen
                && screen().getMenu().snapshotReady() && screen().getMenu().snapshot().size() == 11
                && arrow(10, 10, GridDirection.RIGHT));
        action("zoom_large_fitted_grid_to_natural_cell_size", () ->
                scrollGrid(94, 91.5, Math.log(294.0 / 81.0) / Math.log(1.2)));
        action("pan_large_grid_toward_far_corner_first_drag", () -> panGrid(170, 120, -70, -60));
        action("pan_large_grid_toward_far_corner_second_drag", () -> panGrid(170, 120, -70, -60));
        clickAt("large_grid_edge_hit_clears_correct_cell", 75.5, 106.5, 0,
                () -> cell(10, 10).type() == CellPreview.Type.EMPTY);
        clickAt("large_grid_edge_hit_recreates_correct_cell", 75.5, 106.5, 1,
                () -> arrow(10, 10, GridDirection.RIGHT));
        clickAt("fit_all_11x11_cells_and_output_column", 170, 41, 0, () -> true);
        clickFittedCell("select_11x11_corner_marker", 11, 9, 10, 0, () -> true);
        serverStep("record_large_fixture_after_edge_hit_checks", player -> expectedVisualState = factory(player).factoryState().save());
        addCapture("11x11-zh_cn", 1280, 720, 3);
        addCapture("11x11-zh_cn", 1920, 1080, 4);
    }

    private void scrollGrid(double x, double y, double amount) {
        MiniatureUniverseScreen screen = screen();
        require(screen.mouseScrolled(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0, amount), "Grid wheel input was ignored");
    }

    private void panGrid(double x, double y, double deltaX, double deltaY) {
        MiniatureUniverseScreen screen = screen();
        double absoluteX = screen.getGuiLeft() + x;
        double absoluteY = screen.getGuiTop() + y;
        require(screen.mouseClicked(absoluteX, absoluteY, 2), "Grid pan did not start");
        require(screen.mouseDragged(absoluteX + deltaX, absoluteY + deltaY, 2, deltaX, deltaY), "Grid pan did not move");
        screen.mouseReleased(absoluteX + deltaX, absoluteY + deltaY, 2);
    }

    private void clickFittedCell(String name, int size, int x, int y, int button, BooleanSupplier ready) {
        double contentWidth = (size + 1) * 24 + size * 3;
        double contentHeight = size * 24 + (size - 1) * 3;
        double scale = Math.min(172 / contentWidth, 81 / contentHeight);
        clickAt(name, 8 + (172 - contentWidth * scale) / 2 + (x * 27 + 12) * scale,
                51 + (81 - contentHeight * scale) / 2 + (y * 27 + 12) * scale, button, ready);
    }

    private void addLanguage(String language) {
        @SuppressWarnings("unchecked") CompletableFuture<Void>[] reloaded = new CompletableFuture[1];
        steps.add(new Step("load_language_" + language, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getLanguageManager().getSelected().equals(language)) {
                reloaded[0] = CompletableFuture.completedFuture(null);
                return;
            }
            minecraft.getLanguageManager().setSelected(language);
            minecraft.options.languageCode = language;
            reloaded[0] = minecraft.reloadResourcePacks();
        }, () -> {
            if (!reloaded[0].isDone() || Minecraft.getInstance().getOverlay() != null) return false;
            reloaded[0].join();
            return recipeIndexesReady();
        }));
    }

    private boolean recipeIndexesReady() {
        return (!emiInstalled() || MiniatureEmiClientTestSupport.indexesReady())
                && (!ModList.get().isLoaded("jei") || MiniatureJeiClientTestSupport.ready())
                && Minecraft.getInstance().getOverlay() == null;
    }

    private void beforeScreenRender(ScreenEvent.Render.Pre event) {
        if (originalOverlayFlags == null && event.getScreen() instanceof MiniatureUniverseScreen) {
            originalOverlayFlags = overlayFlags();
        }
    }

    private List<AbstractWidget> overlayWidgets() {
        return emiInstalled() ? MiniatureEmiClientTestSupport.widgets() : List.of();
    }

    private static boolean emiInstalled() { return ModList.get().isLoaded("emi"); }

    private static Map<String, Object> optionalOverlayStatus() {
        if (emiInstalled()) return MiniatureEmiClientTestSupport.status();
        return Map.of("emiInstalled", false, "emiEnabled", false);
    }

    private List<OverlayFlags> overlayFlags() {
        return overlayWidgets().stream().map(widget -> new OverlayFlags(widget.visible, widget.active)).toList();
    }

    private List<Map<String, Object>> verifyOverlayLayout() {
        List<Map<String, Object>> states = new ArrayList<>();
        for (AbstractWidget widget : overlayWidgets()) {
            boolean overlaps = widget.getX() - 1 < screen().getGuiLeft() + screen().getXSize()
                    && widget.getRight() + 1 > screen().getGuiLeft()
                    && widget.getY() - 1 < screen().getGuiTop() + screen().getYSize()
                    && widget.getBottom() + 1 > screen().getGuiTop();
            require(!widget.visible || !overlaps, "Visible EMI widget overlaps miniature editor: " + widget.getClass().getSimpleName());
            states.add(Map.of("widget", widget.getClass().getSimpleName(), "visible", widget.visible, "active", widget.active,
                    "x", widget.getX(), "y", widget.getY(), "width", widget.getWidth(), "height", widget.getHeight()));
        }
        return states;
    }

    private void addCapture(int width, int height, int requestedScale) {
        addCapture("en_us", width, height, requestedScale);
    }

    private void addRecipeBrowserCapture(String name) {
        int[] firstFrame = new int[1];
        steps.add(new Step("render_" + name, () -> {
            MiniatureJeiClientTestSupport.verifyNativeBrowser(jeiRetainedEditor);
            moveActualCursor(2, 2);
            firstFrame[0] = renderedFrames;
        }, () -> renderedFrames >= firstFrame[0] + 10 && recipeIndexesReady()));
        steps.add(new Step("capture_" + name, () -> {
            prepareScreenshotFrame();
            recipeBrowserCapture = name;
        }, () -> recipeBrowserCapture == null));
    }

    private void prepareScreenshotFrame() {
        // One cleanup per requested image; two new frames let the framebuffer reflect the cleared toast layer.
        Minecraft.getInstance().getToasts().clear();
        captureEarliestFrame = renderedFrames + 2;
    }

    private void captureRecipeBrowser(Minecraft minecraft) throws java.io.IOException {
        Map<String, Object> result = new LinkedHashMap<>(MiniatureJeiClientTestSupport.verifyNativeBrowser(jeiRetainedEditor));
        Path imagePath = output.resolve(recipeBrowserCapture + ".png");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            HashSet<Integer> colors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y += 7) for (int x = 0; x < image.getWidth(); x += 7) {
                colors.add(image.getPixelRGBA(x, y));
            }
            require(colors.size() > 32, "Native JEI recipe browser screenshot is blank");
            image.writeToFile(imagePath);
            result.put("file", imagePath.toAbsolutePath().toString());
            result.put("width", image.getWidth());
            result.put("height", image.getHeight());
            result.put("sampledColors", colors.size());
        }
        result.put("requestedGuiScale", minecraft.options.guiScale().get());
        result.put("effectiveGuiScale", minecraft.getWindow().getGuiScale());
        result.put("logicalWidth", minecraft.screen.width);
        result.put("logicalHeight", minecraft.screen.height);
        result.put("language", minecraft.getLanguageManager().getSelected());
        result.put("fixture", fixture);
        result.put("gridSize", jeiRetainedEditor.getMenu().snapshot().size());
        result.putAll(optionalOverlayStatus());
        result.put("jeiInstalled", ModList.get().isLoaded("jei"));
        result.put("gtceuInstalled", ModList.get().isLoaded("gtceu"));
        result.put("toastCleanupRenderFrames", 2);
        screenshots.add(result);
        recipeBrowserCapture = null;
    }

    private void addCapture(String suffix, int width, int height, int requestedScale) {
        addCapture(suffix, width, height, requestedScale, null);
    }

    private void addCapture(String suffix, int width, int height, int requestedScale, Hover hover) {
        String name = width + "x" + height + "-scale-" + requestedScale + "-" + suffix;
        steps.add(new Step("resize_" + name, () -> Minecraft.getInstance().getWindow().setWindowed(width, height),
                () -> Minecraft.getInstance().getWindow().getWidth() == width && Minecraft.getInstance().getWindow().getHeight() == height));
        int[] startingFrame = new int[1];
        steps.add(new Step("configure_scale_" + name, () -> {
            Minecraft.getInstance().options.guiScale().set(requestedScale);
            Minecraft.getInstance().resizeDisplay();
            startingFrame[0] = renderedFrames;
            recipeReadyFrames = 0;
            moveActualCursor(2, 2);
        }, () -> renderedFrames >= startingFrame[0] + 10 && recipeReadyFrames >= 10 && recipeIndexesReady()));
        if (hover != null) {
            long[] hoverStarted = new long[1];
            steps.add(new Step("hover_real_cursor_" + name, () -> {
                require(cursorEmpty() || hover.target().equals("marker_source"), "Tooltip fixture has a carried item");
                if (hover.target().equals("input_resource_quantity")) {
                    require(screen().getMenu().snapshot().inputs().getFirst().amount()
                            .equals(BigInteger.TEN.pow(29).add(BigInteger.valueOf(123456))), "Full-quantity tooltip fixture is missing");
                }
                activeHover = hover;
                moveActualCursor(screen().getGuiLeft() + hover.x(), screen().getGuiTop() + hover.y());
                LOGGER.info("Miniature tooltip cursor requested: {}", cursorDiagnostics());
            }, () -> {
                double expectedX = screen().getGuiLeft() + hover.x();
                double expectedY = screen().getGuiTop() + hover.y();
                if (!actualCursorAt(expectedX, expectedY) || !Minecraft.getInstance().isWindowActive()) {
                    hoverStarted[0] = 0;
                    moveActualCursor(expectedX, expectedY);
                    return false;
                }
                if (hoverStarted[0] == 0) hoverStarted[0] = System.nanoTime();
                return System.nanoTime() - hoverStarted[0] >= 1_200_000_000L && recipeReadyFrames >= 10 && recipeIndexesReady();
            }));
        }
        steps.add(new Step("capture_" + name, () -> {
            require(screen().getGuiLeft() >= 0 && screen().getGuiTop() >= 0
                    && screen().getGuiLeft() + screen().getXSize() <= screen().width
                    && screen().getGuiTop() + screen().getYSize() <= screen().height, "Editor frame exceeds logical viewport");
            for (var listener : screen().children()) {
                if (listener instanceof AbstractWidget widget && widget.visible) {
                    require(widget.getX() >= 0 && widget.getY() >= 0
                            && widget.getX() + widget.getWidth() <= screen().width
                            && widget.getY() + widget.getHeight() <= screen().height, "Visible widget exceeds logical viewport");
                }
            }
            prepareScreenshotFrame();
            capture = new Capture(name, width, height, requestedScale, hover);
        }, () -> capture == null));
        if (hover != null) action("move_cursor_off_tooltip_after_" + name, () -> {
            activeHover = null;
            moveActualCursor(2, 2);
        });
    }

    private void moveActualCursor(double logicalX, double logicalY) {
        var window = Minecraft.getInstance().getWindow();
        if (GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE) {
            GLFW.glfwFocusWindow(window.getWindow());
        }
        double windowX = logicalX * window.getScreenWidth() / window.getGuiScaledWidth();
        double windowY = logicalY * window.getScreenHeight() / window.getGuiScaledHeight();
        if (!cursorCallbackDriver) {
            GLFW.glfwSetCursorPos(window.getWindow(), windowX, windowY);
            if (actualCursorAt(logicalX, logicalY)) return;
            cursorCallbackDriver = true;
            LOGGER.info("Native cursor positioning did not update Minecraft; using its registered GLFW cursor callback: {}", cursorDiagnostics());
        }
        // Remote or clipped desktops may reject OS pointer moves; drive the existing input callback instead.
        GLFWCursorPosCallback registered = GLFW.glfwSetCursorPosCallback(window.getWindow(), null);
        require(registered != null, "Minecraft has no registered GLFW cursor callback");
        GLFW.glfwSetCursorPosCallback(window.getWindow(), registered);
        registered.invoke(window.getWindow(), windowX, windowY);
    }

    private boolean actualCursorAt(double logicalX, double logicalY) {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        return Math.abs(minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth() - logicalX) < 1
                && Math.abs(minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight() - logicalY) < 1;
    }

    private Map<String, Object> cursorDiagnostics() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double[] nativeX = new double[1];
        double[] nativeY = new double[1];
        GLFW.glfwGetCursorPos(window.getWindow(), nativeX, nativeY);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("cursorInputDriver", cursorInputDriver());
        diagnostics.put("glfwFocused", GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_FALSE);
        diagnostics.put("minecraftWindowActive", minecraft.isWindowActive());
        diagnostics.put("glfwCursorX", nativeX[0]);
        diagnostics.put("glfwCursorY", nativeY[0]);
        diagnostics.put("minecraftCursorX", minecraft.mouseHandler.xpos());
        diagnostics.put("minecraftCursorY", minecraft.mouseHandler.ypos());
        diagnostics.put("actualLogicalX", minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        diagnostics.put("actualLogicalY", minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        diagnostics.put("windowWidth", window.getScreenWidth());
        diagnostics.put("windowHeight", window.getScreenHeight());
        diagnostics.put("framebufferWidth", window.getWidth());
        diagnostics.put("framebufferHeight", window.getHeight());
        diagnostics.put("guiWidth", window.getGuiScaledWidth());
        diagnostics.put("guiHeight", window.getGuiScaledHeight());
        if (activeHover != null && minecraft.screen instanceof MiniatureUniverseScreen editor) {
            diagnostics.put("expectedLogicalX", editor.getGuiLeft() + activeHover.x());
            diagnostics.put("expectedLogicalY", editor.getGuiTop() + activeHover.y());
            diagnostics.put("hoverTarget", activeHover.target());
        }
        return diagnostics;
    }

    private String cursorInputDriver() {
        return cursorCallbackDriver ? "registered_glfw_cursor_callback" : "native_glfw_cursor_position";
    }

    private void onRender(RenderFrameEvent.Post event) {
        renderedFrames++;
        if (finished) return;
        try {
            recipeReadyFrames = recipeIndexesReady() ? recipeReadyFrames + 1 : 0;
            if (recipeBrowserCapture != null && recipeReadyFrames >= 10 && renderedFrames >= captureEarliestFrame) {
                captureRecipeBrowser(Minecraft.getInstance());
                return;
            }
            if (capture == null || recipeReadyFrames < 10 || renderedFrames < captureEarliestFrame) return;
            Minecraft minecraft = Minecraft.getInstance();
            Capture pending = capture;
            if (pending.hover() != null) {
                require(actualCursorAt(screen().getGuiLeft() + pending.hover().x(), screen().getGuiTop() + pending.hover().y()),
                        "Actual cursor left the tooltip target before framebuffer capture");
                if (pending.hover().target().equals("fit_tool")) {
                    require(screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                            .anyMatch(widget -> widget.getX() == screen().getGuiLeft() + 162
                                    && widget.getY() == screen().getGuiTop() + 33 && widget.isHovered() && widget.getTooltip() != null),
                            "Fit tool did not receive the actual rendered hover state");
                }
                if (pending.hover().target().equals("marker_source")) {
                    require(markerSourceButton().visible && markerSourceButton().isHovered() && markerSourceButton().getTooltip() != null,
                            "Marker source button did not receive the actual rendered hover state");
                }
            }
            Path imagePath = output.resolve(pending.name() + ".png");
            List<Map<String, Object>> overlayLayout = verifyOverlayLayout();
            try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                require(image.getWidth() == pending.width() && image.getHeight() == pending.height(), "Unexpected framebuffer dimensions");
                HashSet<Integer> colors = new HashSet<>();
                for (int y = 0; y < image.getHeight(); y += 7) {
                    for (int x = 0; x < image.getWidth(); x += 7) colors.add(image.getPixelRGBA(x, y));
                }
                require(colors.size() > 32, "Rendered screenshot is blank or lacks content");
                image.writeToFile(imagePath);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("file", imagePath.toAbsolutePath().toString());
                result.put("width", image.getWidth());
                result.put("height", image.getHeight());
                result.put("requestedGuiScale", pending.requestedScale());
                result.put("effectiveGuiScale", minecraft.getWindow().getGuiScale());
                result.put("logicalWidth", screen().width);
                result.put("logicalHeight", screen().height);
                result.put("sampledColors", colors.size());
                result.put("language", minecraft.getLanguageManager().getSelected());
                result.put("gridSize", screen().getMenu().snapshot().size());
                result.put("fixture", fixture);
                result.put("inputResourceRows", screen().getMenu().snapshot().inputs().size());
                result.put("outputResourceRows", screen().getMenu().snapshot().outputs().size());
                result.putAll(optionalOverlayStatus());
                result.put("jeiInstalled", ModList.get().isLoaded("jei"));
                result.put("gtceuInstalled", ModList.get().isLoaded("gtceu"));
                result.put("toastCleanupRenderFrames", 2);
                result.put("cursorInputDriver", cursorInputDriver());
                result.put("recipeIndexesStableFrames", recipeReadyFrames);
                result.put("emiWidgetLayout", overlayLayout);
                if (pending.hover() != null) {
                    result.put("hover", pending.hover());
                    result.put("cursorDiagnostics", cursorDiagnostics());
                    result.put("tooltipExpectedText", pending.hover().target().equals("input_resource_quantity")
                            ? screen().getMenu().snapshot().inputs().getFirst().amount().toString()
                            : pending.hover().target().equals("per_tick_chemical_budget")
                            ? Component.translatable("screen.flux_overdrive.miniature_universe.recipe_input",
                                    Component.translatable("screen.flux_overdrive.miniature_universe.resource_amount.chemical", 20)).getString()
                            : pending.hover().target().equals("marker_source") ? markerSourceButton().getMessage().getString()
                            : pending.hover().target().equals("upgrade_materials")
                            ? Component.translatable("screen.flux_overdrive.miniature_universe.upgrade_hint").getString()
                            : pending.hover().target().equals("runtime_status")
                            ? Component.translatable("screen.flux_overdrive.miniature_universe.progress", screen().getMenu().snapshot().runtime().paidTicks(), screen().getMenu().snapshot().runtime().requiredTicks()).getString()
                            : pending.hover().target().equals("saved_factory")
                            ? Component.translatable("tooltip.flux_overdrive.miniature_universe.layout", 11, 11, 2).getString()
                            : Component.translatable("screen.flux_overdrive.miniature_universe.fit").getString());
                    result.put("actualCursorLogicalX", minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                            / minecraft.getWindow().getScreenWidth());
                    result.put("actualCursorLogicalY", minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                            / minecraft.getWindow().getScreenHeight());
                }
                screenshots.add(result);
            }
            capture = null;
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private void clickInventory(String name, int inventoryIndex, BooleanSupplier ready) {
        addClickStep(name, () -> {
            var slot = screen().getMenu().slots.stream()
                    .filter(candidate -> candidate.container == Minecraft.getInstance().player.getInventory()
                            && candidate.getContainerSlot() == inventoryIndex).findFirst().orElseThrow();
            click(slot.x + 8, slot.y + 8, 0);
        }, ready);
    }

    private void clickCell(String name, int x, int y, int button, BooleanSupplier ready) {
        clickAt(name, GRID_LEFT + x * CELL_PITCH + CELL_CENTER, GRID_TOP + y * CELL_PITCH + CELL_CENTER, button, ready);
    }

    private void clickMachineRecipe(String name, int x, int y, BooleanSupplier ready) {
        clickCell(name + "_select", x, y, 0, this::cursorEmpty);
        clickAt(name, 230, 67, 0, ready);
    }

    private void removeMachine(String name, int x, int y, BooleanSupplier ready) {
        clickCell(name + "_select_without_extracting", x, y, 0,
                () -> cursorEmpty() && cell(x, y).type() == CellPreview.Type.MACHINE);
        action(name + "_delete", () -> screen().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0));
        waitFor(name, ready);
    }

    private void replaceMarker(String name, int x, int y, int button, BooleanSupplier ready) {
        clickCell(name + "_select", x, y, 0, () -> true);
        action(name + "_delete", () -> screen().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0));
        waitFor(name + "_cleared", () -> cell(x, y).type() == CellPreview.Type.EMPTY);
        clickCell(name, x, y, button, ready);
    }

    private void clickAt(String name, double x, double y, int button, BooleanSupplier ready) {
        addClickStep(name, () -> click(x, y, button), ready);
    }

    private void addClickStep(String name, Runnable click, BooleanSupplier ready) {
        boolean[] clicked = new boolean[1];
        steps.add(new Step(name, () -> {}, () -> {
            // These are individual clicks; vanilla interprets same-slot clicks under 250 ms as double clicks.
            if (!clicked[0]) {
                if (System.nanoTime() - lastClickAt < 300_000_000L) return false;
                click.run();
                clicked[0] = true;
            }
            return ready.getAsBoolean();
        }));
    }

    private void click(double relativeX, double relativeY, int button) {
        MiniatureUniverseScreen screen = screen();
        double x = screen.getGuiLeft() + relativeX;
        double y = screen.getGuiTop() + relativeY;
        screen.mouseMoved(x, y);
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
        lastClickAt = System.nanoTime();
    }

    private void action(String name, Runnable action) {
        steps.add(new Step(name, action, () -> true));
    }

    private void waitFor(String name, BooleanSupplier condition) {
        steps.add(new Step(name, () -> {}, condition));
    }

    private void serverStep(String name, Consumer<ServerPlayer> action) {
        @SuppressWarnings("unchecked") CompletableFuture<Void>[] future = new CompletableFuture[1];
        steps.add(new Step(name, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            var server = minecraft.getSingleplayerServer();
            UUID playerId = minecraft.player.getUUID();
            future[0] = server.submit(() -> action.accept(server.getPlayerList().getPlayer(playerId)));
        }, () -> {
            if (!future[0].isDone()) return false;
            future[0].join();
            return true;
        }));
    }

    private MiniatureUniverseScreen screen() {
        require(Minecraft.getInstance().screen instanceof MiniatureUniverseScreen, "Miniature editor is not open");
        return (MiniatureUniverseScreen) Minecraft.getInstance().screen;
    }

    private CellPreview cell(int x, int y) {
        return screen().getMenu().snapshot().cells().get(y * screen().getMenu().snapshot().size() + x);
    }

    private boolean marker(int x, int y, String registryId, int quantity, GridDirection direction) {
        CellPreview cell = cell(x, y);
        return cell.type() == CellPreview.Type.MARKER && cell.resource().registryId().equals(registryId)
                && cell.quantity().equals(BigInteger.valueOf(quantity)) && cell.direction() == direction;
    }

    private boolean arrow(int x, int y, GridDirection direction) {
        return cell(x, y).type() == CellPreview.Type.ARROW && cell(x, y).direction() == direction;
    }

    private boolean cursor(Item item, int count) {
        ItemStack carried = screen().getMenu().getCarried();
        return carried.is(item) && carried.getCount() == count;
    }

    private boolean cursorEmpty() {
        return screen().getMenu().getCarried().isEmpty();
    }

    private boolean resourceAmount(boolean outputInventory, String registryId, int amount) {
        var resources = outputInventory ? screen().getMenu().snapshot().outputs() : screen().getMenu().snapshot().inputs();
        return resources.size() == 1 && resources.getFirst().resource().registryId().equals(registryId)
                && resources.getFirst().amount().equals(BigInteger.valueOf(amount));
    }

    private static TileMiniatureUniverse factory(ServerPlayer player) {
        return (TileMiniatureUniverse) player.serverLevel().getBlockEntity(FACTORY_POS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", failure == null ? "passed" : "failed");
        report.put("scope", Boolean.getBoolean("flux_overdrive.clientTestS12Only") ? "s12-production-acceptance"
                : Boolean.getBoolean("flux_overdrive.clientTestS11Only") ? "s11-focused-acceptance"
                : Boolean.getBoolean("flux_overdrive.clientTestS10Only") ? "s10-focused-acceptance"
                : Boolean.getBoolean("flux_overdrive.clientTestDirectionsOnly") ? "direction-editor-and-recipe-browser"
                : Boolean.getBoolean("flux_overdrive.clientTestS09Only") ? "s09-focused-debug" : "full-client-acceptance");
        report.put("completedAt", Instant.now().toString());
        report.put("world", worldName);
        report.put("steps", passed);
        report.put("screenshots", screenshots);
        report.put("activeStep", current == null ? null : current.name());
        report.put("originalEmiWidgetFlags", originalOverlayFlags);
        report.putAll(optionalOverlayStatus());
        report.put("jeiInstalled", ModList.get().isLoaded("jei"));
        report.put("gtceuInstalled", ModList.get().isLoaded("gtceu"));
        report.put("requestedWithoutGtceu", Boolean.getBoolean("flux_overdrive.miniatureWithoutGtceu"));
        report.put("tutorialSuppressedForLayoutQa", Minecraft.getInstance().options.tutorialStep == TutorialSteps.NONE);
        report.put("toastCleanup", "Cleared at isolated initialization and once before each screenshot; two rendered frames elapse before capture");
        report.put("jeiIntegrationEvidence", jeiEvidence);
        report.put("jeiChemicalGhostCoverage", jeiChemicalGhostCoverage);
        report.put("jeiRecipeTransferCoverage", jeiRecipeTransferCoverage);
        report.put("jeiInputMethod", "JEI runtime-registered handlers; native recipe layout coverage is reported separately in jeiRecipeTransferCoverage; no physical overlay drag claim");
        report.put("cursorInputDriver", cursorInputDriver());
        report.put("fixtureNote", "Earlier populated inventories and the existing 11x11 layout are isolated display fixtures. S11 starts at 3x3 and pays all four server-owned release upgrades using the player's inventory; those are real upgrade transactions.");
        if (failure != null) {
            LOGGER.error("Miniature client verification failed", failure);
            report.put("failure", failure.toString());
            report.put("cursorDiagnostics", cursorDiagnostics());
        }
        try {
            Files.createDirectories(output);
            if (failure != null) {
                try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    Path imagePath = output.resolve("failure.png");
                    image.writeToFile(imagePath);
                    report.put("failureScreenshot", imagePath.toAbsolutePath().toString());
                } catch (Exception screenshotFailure) {
                    report.put("failureScreenshotError", screenshotFailure.toString());
                }
            }
            Files.writeString(output.resolve("report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception writeFailure) {
            LOGGER.error("Could not write miniature client verification report", writeFailure);
        }
        Minecraft.getInstance().stop();
    }

    private record Step(String name, Runnable start, BooleanSupplier ready) {}
    private record Capture(String name, int width, int height, int requestedScale, Hover hover) {}
    private record Hover(String target, double x, double y) {}
    private record OverlayFlags(boolean visible, boolean active) {}
}
