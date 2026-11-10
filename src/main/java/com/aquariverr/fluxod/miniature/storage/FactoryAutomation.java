package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.util.LoadedBlockAccess;

/** Shared capability access checks. GUI recovery is intentionally independent of the recipe filter. */
public final class FactoryAutomation {
    public static FactoryStorage storage(TileMiniatureUniverse owner) {
        return LoadedBlockAccess.isCurrent(owner)
                ? owner.factoryState().storage().orElse(null) : null;
    }

    public static boolean accepts(TileMiniatureUniverse owner, ResourceKey resource) {
        FactoryStorage storage = storage(owner);
        if (storage == null) return false;
        if (storage.batch() != null) return storage.batch().inputs().amounts().containsKey(resource)
                || storage.batch().catalysts().amounts().containsKey(resource);
        var result = owner.compilationFor(owner.factoryState());
        return result != null && result.successful()
                && (result.factory().inputs().stream().anyMatch(input -> input.resource().equals(resource))
                || result.factory().catalysts().stream().anyMatch(input -> input.resource().equals(resource)));
    }

    private FactoryAutomation() {}
}
