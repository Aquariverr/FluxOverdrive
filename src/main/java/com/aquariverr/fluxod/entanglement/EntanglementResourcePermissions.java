package com.aquariverr.fluxod.entanglement;

public final class EntanglementResourcePermissions {

    public static boolean supports(int inputResources, int outputResources,
                                   EntanglementResourceKind kind,
                                   EntanglementResourceDirection direction) {
        int resources = direction == EntanglementResourceDirection.INPUT
                ? inputResources
                : outputResources;
        return kind.isPresent(resources);
    }

    private EntanglementResourcePermissions() {}
}
