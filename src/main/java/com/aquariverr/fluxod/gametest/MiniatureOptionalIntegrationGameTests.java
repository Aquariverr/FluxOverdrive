package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Keep this entry point free of optional API types so the absence profile tests real class isolation. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureOptionalIntegrationGameTests {
    @GameTest(template = "empty")
    public static void optionalLibrariesMatchRequestedRuntime(GameTestHelper helper) {
        verify(helper, "jei", "flux_overdrive.miniatureWithoutJei", "mezz/jei/api/IModPlugin.class");
        verify(helper, "emi", "flux_overdrive.miniatureWithoutEmi", "dev/emi/emi/api/EmiPlugin.class");
        verify(helper, "mekanism", "flux_overdrive.miniatureWithoutMekanism", "mekanism/api/chemical/ChemicalStack.class");
        verify(helper, "gtceu", "flux_overdrive.miniatureWithoutGtceu", "com/gregtechceu/gtceu/GTCEu.class");
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, String modId, String property, String resource) {
        // Standard /test runs need not opt into an exact dependency profile.
        if (System.getProperty(property) == null) return;
        boolean absent = Boolean.getBoolean(property);
        helper.assertTrue(ModList.get().isLoaded(modId) != absent,
                "The launched JVM does not match the requested " + modId + " profile");
        if (absent) {
            helper.assertTrue(MiniatureOptionalIntegrationGameTests.class.getClassLoader().getResource(resource) == null,
                    "Absent " + modId + " API remains on the runtime classpath");
        }
    }

    private MiniatureOptionalIntegrationGameTests() {}
}
