package com.aquariverr.fluxod.multiblock;

import java.util.IdentityHashMap;
import java.util.Map;

final class AssemblyMaterialBudget<T> {

    private final Map<T, Integer> available = new IdentityHashMap<>();

    AssemblyMaterialBudget(Map<T, Integer> available) {
        this.available.putAll(available);
    }

    boolean reserve(T material) {
        int count = available.getOrDefault(material, 0);
        if (count <= 0) return false;
        available.put(material, count - 1);
        return true;
    }
}
