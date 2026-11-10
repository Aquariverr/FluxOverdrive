package com.aquariverr.fluxod.miniature.upgrade;

import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import java.util.List;
import java.util.Objects;

public record UpgradePreview(Status status, List<ResourceAmount> materials) {
    public static final UpgradePreview NONE = new UpgradePreview(Status.UNAVAILABLE, List.of());
    public UpgradePreview {
        Objects.requireNonNull(status);
        materials = List.copyOf(materials);
        if (status == Status.AVAILABLE ? materials.isEmpty() || materials.size() > MiniatureUpgradeRecipe.MAX_MATERIALS : !materials.isEmpty()) {
            throw new IllegalArgumentException("Invalid upgrade preview");
        }
        if (materials.stream().anyMatch(cost -> cost.resource().kind() != ResourceKind.ITEM
                || cost.amount().signum() <= 0 || cost.amount().compareTo(java.math.BigInteger.valueOf(MiniatureUpgradeRecipe.MAX_COUNT)) > 0)
                || materials.stream().map(ResourceAmount::resource).distinct().count() != materials.size()) {
            throw new IllegalArgumentException("Invalid upgrade materials");
        }
    }
    public enum Status { UNAVAILABLE, AVAILABLE, MAXIMUM, NO_RECIPE, AMBIGUOUS }
}
