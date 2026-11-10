package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;

import java.math.BigInteger;
import java.util.*;

/** Bounded display data only; it contains no executable recipe or client-supplied compilation identity. */
public record CompilationPreview(Status status, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                                 List<ResourceAmount> catalysts, Map<Integer, List<ResourceAmount>> outputRows,
                                 int machineCount, FactoryDiagnostic diagnostic, int requiredTicks,
                                 BigInteger energyPerTick, BigInteger energyPerBatch) {
    public CompilationPreview(Status status, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                              List<ResourceAmount> catalysts, Map<Integer, List<ResourceAmount>> outputRows,
                              int machineCount, FactoryDiagnostic diagnostic, int requiredTicks) {
        this(status, inputs, outputs, catalysts, outputRows, machineCount, diagnostic, requiredTicks,
                FactoryRules.energyPerTick(machineCount), FactoryRules.energyPerTick(machineCount).multiply(BigInteger.valueOf(requiredTicks)));
    }
    public CompilationPreview(Status status, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                              List<ResourceAmount> catalysts, Map<Integer, List<ResourceAmount>> outputRows,
                              int machineCount, FactoryDiagnostic diagnostic) {
        this(status, inputs, outputs, catalysts, outputRows, machineCount, diagnostic, FactoryRules.BATCH_TICKS);
    }
    public static final CompilationPreview NONE = new CompilationPreview(Status.NOT_COMPILED, List.of(), List.of(), List.of(), Map.of(), 0, null);

    public CompilationPreview {
        Objects.requireNonNull(status);
        if (requiredTicks < FactoryRules.BATCH_TICKS || requiredTicks > FactoryLimits.MAX_BATCH_TICKS
                || requiredTicks % FactoryRules.BATCH_TICKS != 0) throw new IllegalArgumentException("Invalid batch duration");
        Objects.requireNonNull(energyPerTick); Objects.requireNonNull(energyPerBatch);
        if (energyPerTick.signum() < 0 || energyPerTick.compareTo(FactoryRules.energyPerTick(machineCount)) > 0
                || energyPerTick.remainder(FactoryRules.ENERGY_PER_MACHINE_TICK).signum() != 0
                || energyPerBatch.compareTo(energyPerTick.multiply(BigInteger.valueOf(FactoryRules.BATCH_TICKS))) < 0
                || energyPerBatch.compareTo(energyPerTick.multiply(BigInteger.valueOf(requiredTicks))) > 0
                || energyPerBatch.remainder(FactoryRules.energyPerBatch(1)).signum() != 0
                || status == Status.READY && energyPerTick.signum() == 0) throw new IllegalArgumentException("Invalid compilation energy");
        inputs = checked(inputs); outputs = checked(outputs); catalysts = checked(catalysts);
        Map<Integer, List<ResourceAmount>> rows = new LinkedHashMap<>();
        outputRows.forEach((row, amounts) -> {
            if (row < 0 || row >= FactoryLimits.MAX_GRID_SIZE || amounts.isEmpty()) throw new IllegalArgumentException("Invalid output row");
            rows.put(row, checked(amounts));
        });
        outputRows = Collections.unmodifiableMap(rows);
        if (status == Status.READY) {
            if (machineCount < 1 || machineCount > FactoryLimits.MAX_TOTAL_MACHINES || inputs.isEmpty() || outputs.isEmpty()
                    || diagnostic != null || !FactoryCompiler.map(outputs).equals(FactoryCompiler.map(rows.values().stream().flatMap(List::stream).toList()))) {
                throw new IllegalArgumentException("Invalid compiled preview");
            }
            Set<ResourceKey> boundary = new HashSet<>();
            for (List<ResourceAmount> list : List.of(inputs, outputs, catalysts)) list.forEach(amount -> boundary.add(amount.resource()));
            if (boundary.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new IllegalArgumentException("Too many boundary resources");
        } else if (machineCount != 0 || !inputs.isEmpty() || !outputs.isEmpty() || !catalysts.isEmpty() || !rows.isEmpty()
                || (status == Status.FAILED) != (diagnostic != null)) throw new IllegalArgumentException("Failed preview exposes partial recipe");
        if (diagnostic != null) {
            diagnostic.expected().ifPresent(FactoryLimits::requireQuantity);
            diagnostic.actual().ifPresent(FactoryLimits::requireQuantity);
        }
    }

    public static CompilationPreview from(CompilationResult result) {
        if (result == null) return NONE;
        if (!result.successful()) return new CompilationPreview(Status.FAILED, List.of(), List.of(), List.of(), Map.of(), 0, result.diagnostic());
        CompiledFactory factory = result.factory();
        return new CompilationPreview(Status.READY, factory.inputs(), factory.outputs(), factory.catalysts(), factory.outputRows(), factory.machineCount(), null,
                factory.requiredTicks(1, 1), factory.energyPerTick(), factory.energyPerBatch());
    }

    private static List<ResourceAmount> checked(List<ResourceAmount> resources) {
        resources = List.copyOf(resources);
        if (resources.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new IllegalArgumentException("Too many compilation resources");
        Set<ResourceKey> unique = new HashSet<>();
        for (ResourceAmount amount : resources) {
            FactoryLimits.requireInventoryQuantity(amount.amount());
            if (!unique.add(amount.resource())) throw new IllegalArgumentException("Duplicate compilation resource");
        }
        return resources;
    }

    public enum Status { NOT_COMPILED, READY, FAILED }
}
