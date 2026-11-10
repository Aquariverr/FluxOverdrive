package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.recipe.MachineRecipeAdapters;

import java.util.Set;

/** Mekanism recipe classes live behind this optional integration boundary. */
public final class MekanismRecipeIntegration {
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_enriching", machines("enrichment_chamber", "enriching"),
                MekanismResourceRecipeAdapter.Kind.ENRICHING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_crushing", machines("crusher", "crushing"),
                MekanismResourceRecipeAdapter.Kind.CRUSHING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_item_chemical", machines("purification_chamber", "purifying"),
                MekanismResourceRecipeAdapter.Kind.PURIFYING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_injecting", machines("chemical_injection_chamber", "injecting"),
                MekanismResourceRecipeAdapter.Kind.INJECTING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_centrifuge", Set.of("mekanism:isotopic_centrifuge"),
                MekanismResourceRecipeAdapter.Kind.CENTRIFUGING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_infuser", Set.of("mekanism:chemical_infuser"),
                MekanismResourceRecipeAdapter.Kind.INFUSING));
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter(
                "flux_overdrive:mekanism_electrolysis", Set.of("mekanism:electrolytic_separator"),
                MekanismResourceRecipeAdapter.Kind.ELECTROLYSIS));
        register("smelting", "energized_smelter", "smelting", MekanismResourceRecipeAdapter.Kind.SMELTING);
        register("compressing", "osmium_compressor", "compressing", MekanismResourceRecipeAdapter.Kind.COMPRESSING);
        register("metallurgic_infusing", "metallurgic_infuser", "infusing", MekanismResourceRecipeAdapter.Kind.METALLURGIC_INFUSING);
        register("combining", "combiner", "combining", MekanismResourceRecipeAdapter.Kind.COMBINING);
        register("sawing", "precision_sawmill", "sawing", MekanismResourceRecipeAdapter.Kind.SAWING);
        register("oxidizing", "chemical_oxidizer", null, MekanismResourceRecipeAdapter.Kind.OXIDIZING);
        register("washing", "chemical_washer", null, MekanismResourceRecipeAdapter.Kind.WASHING);
        register("crystallizing", "chemical_crystallizer", null, MekanismResourceRecipeAdapter.Kind.CRYSTALLIZING);
        register("dissolving", "chemical_dissolution_chamber", null, MekanismResourceRecipeAdapter.Kind.DISSOLVING);
        register("reaction", "pressurized_reaction_chamber", null, MekanismResourceRecipeAdapter.Kind.REACTION);
        register("rotary", "rotary_condensentrator", null, MekanismResourceRecipeAdapter.Kind.ROTARY);
        register("evaporating", "thermal_evaporation_controller", null, MekanismResourceRecipeAdapter.Kind.EVAPORATING);
        register("activating", "solar_neutron_activator", null, MekanismResourceRecipeAdapter.Kind.ACTIVATING);
        register("nucleosynthesizing", "antiprotonic_nucleosynthesizer", null, MekanismResourceRecipeAdapter.Kind.NUCLEOSYNTHESIZING);
        register("pigment_extracting", "pigment_extractor", null, MekanismResourceRecipeAdapter.Kind.PIGMENT_EXTRACTING);
        register("pigment_mixing", "pigment_mixer", null, MekanismResourceRecipeAdapter.Kind.PIGMENT_MIXING);
        register("painting", "painting_machine", null, MekanismResourceRecipeAdapter.Kind.PAINTING);
    }

    private static void register(String id, String machine, String factory, MekanismResourceRecipeAdapter.Kind kind) {
        MachineRecipeAdapters.register(new MekanismResourceRecipeAdapter("flux_overdrive:mekanism_" + id, machines(machine, factory), kind));
    }

    private static Set<String> machines(String machine, String factory) {
        var result = new java.util.HashSet<String>();
        result.add("mekanism:" + machine);
        if (factory != null) for (String tier : new String[]{"basic", "advanced", "elite", "ultimate"}) {
            result.add("mekanism:" + tier + "_" + factory + "_factory");
        }
        return Set.copyOf(result);
    }

    private MekanismRecipeIntegration() {}
}
