package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Complete output-resource routing for an explicitly selected machine recipe. */
public final class ProductRoutingCodec {
    public static ListTag write(Map<ResourceKey, GridDirection> routes) {
        var list = new ListTag();
        routes.forEach((resource, direction) -> {
            var tag = new CompoundTag();
            tag.put("resource", ResourceNbtCodec.writeKey(resource)); tag.putString("direction", direction.name()); list.add(tag);
        });
        return list;
    }

    public static Map<ResourceKey, GridDirection> read(ListTag list) {
        if (list.size() > FactoryLimits.MAX_RECIPE_RESOURCES || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid product routing list");
        var routes = new LinkedHashMap<ResourceKey, GridDirection>();
        for (int i = 0; i < list.size(); i++) {
            var tag = list.getCompound(i);
            ResourceNbtCodec.requireFields(tag, Set.of("resource", "direction"));
            ResourceNbtCodec.requireType(tag, "resource", Tag.TAG_COMPOUND);
            ResourceNbtCodec.requireType(tag, "direction", Tag.TAG_STRING);
            if (routes.put(ResourceNbtCodec.readKey(tag.getCompound("resource")), GridDirection.valueOf(tag.getString("direction"))) != null)
                throw new IllegalArgumentException("Duplicate routed product");
        }
        return routes;
    }
    private ProductRoutingCodec() {}
}
