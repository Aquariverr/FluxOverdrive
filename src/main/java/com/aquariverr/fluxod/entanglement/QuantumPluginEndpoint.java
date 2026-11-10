package com.aquariverr.fluxod.entanglement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record QuantumPluginEndpoint(QuantumPluginEndpointKey key, BlockPos absolutePos, int ringTier,
                                    QuantumPluginLocation location, ResourceLocation iconItemId,
                                    String displayTranslationKey, int inputResources,
                                    int outputResources, boolean remoteMenu) {

    public static final java.util.Comparator<QuantumPluginEndpoint> DISPLAY_ORDER = java.util.Comparator
            .comparing(QuantumPluginEndpoint::displayOrderKey);

    public QuantumPluginEndpoint {
        absolutePos = absolutePos.immutable();
    }

    public boolean supports(EntanglementResourceKind kind, EntanglementResourceDirection direction) {
        return EntanglementResourcePermissions.supports(inputResources, outputResources, kind, direction);
    }

    public QuantumPluginDisplayKey displayOrderKey() {
        return new QuantumPluginDisplayKey(ringTier, location, key.localEndpointId());
    }
}
