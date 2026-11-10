package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class FactoryFingerprint {
    static String of(FactoryLayout layout, Map<GridPosition, ResolvedRecipe> recipes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            boolean routed = layout.cells().stream().anyMatch(cell -> cell instanceof FactoryCell.Machine machine && !machine.outputDirections().isEmpty());
            out.writeInt(routed ? 3 : 2); // Preserve historical fingerprints for layouts without product routing.
            out.writeInt(layout.size());
            for (int index = 0; index < layout.cells().size(); index++) {
                FactoryCell cell = layout.cells().get(index);
                switch (cell) {
                    case FactoryCell.Empty ignored -> out.writeByte(0);
                    case FactoryCell.Arrow arrow -> {
                        out.writeByte(1);
                        for (GridDirection direction : GridDirection.values()) out.writeInt(arrow.weight(direction));
                    }
                    case FactoryCell.Marker marker -> {
                        out.writeByte(2); out.writeByte(marker.direction().ordinal()); amounts(out, List.of(marker.resource()));
                    }
                    case FactoryCell.Machine machine -> {
                        out.writeByte(3); resource(out, machine.stack().variant()); out.writeInt(machine.stack().count());
                        out.writeByte(machine.direction().ordinal());
                        ResolvedRecipe recipe = recipes.get(new GridPosition(index % layout.size(), index / layout.size()));
                        out.writeUTF(recipe.reference().adapterId()); out.writeUTF(recipe.reference().recipeId());
                        out.writeUTF(recipe.reference().variantId());
                        amounts(out, recipe.inputs()); amounts(out, recipe.outputs()); amounts(out, recipe.catalysts());
                        if (routed) for (var output : recipe.outputs()) out.writeByte(machine.outputDirection(output.resource()).ordinal());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint factory", exception);
        }
    }

    private static void amounts(DataOutputStream out, List<ResourceAmount> amounts) throws IOException {
        out.writeInt(amounts.size());
        for (ResourceAmount amount : amounts) {
            resource(out, amount.resource());
            byte[] quantity = QuantityCodec.encode(amount.amount());
            out.writeInt(quantity.length); out.write(quantity);
        }
    }

    private static void resource(DataOutputStream out, ResourceKey key) throws IOException {
        out.writeByte(key.kind().ordinal()); out.writeUTF(key.registryId());
        RecipeReference.writeCanonical(key.components(), out);
    }
}
