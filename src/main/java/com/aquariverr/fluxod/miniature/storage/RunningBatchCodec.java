package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.runtime.BatchEnergySchedule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireFields;
import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireType;

final class RunningBatchCodec {
    private RunningBatchCodec() {}

    static CompoundTag write(RunningBatch batch) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", batch.id());
        tag.putString("fingerprint", batch.fingerprint());
        tag.putInt("n", batch.environment().stabilityBand());
        tag.putInt("m", batch.environment().temperatureBand());
        tag.putInt("parallelism", batch.parallelism());
        tag.put("inputs", ResourceNbtCodec.writeInventory(batch.inputs()));
        tag.put("outputs", ResourceNbtCodec.writeInventory(batch.outputs()));
        if (!batch.catalysts().amounts().isEmpty()) tag.put("catalysts", ResourceNbtCodec.writeInventory(batch.catalysts()));
        tag.putByteArray("energyPerTick", QuantityCodec.encode(batch.energyPerTick()));
        tag.putInt("paidTicks", batch.paidTicks());
        tag.putInt("requiredTicks", batch.requiredTicks());
        if (batch.energySchedule().stages().size() > 1) {
            ListTag stages = new ListTag();
            for (var stage : batch.energySchedule().stages()) {
                CompoundTag value = new CompoundTag();
                value.putInt("round", stage.round()); value.putInt("machines", stage.machines());
                stages.add(value);
            }
            tag.put("energySchedule", stages);
        }
        return tag;
    }

    static RunningBatch read(CompoundTag tag) {
        var fields = new java.util.HashSet<>(Set.of("id", "fingerprint", "n", "m", "parallelism", "inputs", "outputs", "energyPerTick", "paidTicks"));
        if (tag.contains("catalysts")) fields.add("catalysts");
        if (tag.contains("requiredTicks")) { fields.add("requiredTicks"); requireType(tag, "requiredTicks", Tag.TAG_INT); }
        if (tag.contains("energySchedule")) { fields.add("energySchedule"); requireType(tag, "energySchedule", Tag.TAG_LIST); }
        requireFields(tag, fields);
        requireType(tag, "id", Tag.TAG_INT_ARRAY);
        requireType(tag, "fingerprint", Tag.TAG_STRING);
        for (String field : Set.of("n", "m", "parallelism", "paidTicks")) requireType(tag, field, Tag.TAG_INT);
        requireType(tag, "inputs", Tag.TAG_LIST);
        requireType(tag, "outputs", Tag.TAG_LIST);
        requireType(tag, "energyPerTick", Tag.TAG_BYTE_ARRAY);
        if (tag.contains("catalysts")) requireType(tag, "catalysts", Tag.TAG_LIST);
        var fee = QuantityCodec.decode(tag.getByteArray("energyPerTick"));
        var schedule = BatchEnergySchedule.fixed(fee);
        if (tag.contains("energySchedule")) {
            ListTag encoded = (ListTag) tag.get("energySchedule");
            if (encoded.isEmpty() || encoded.size() > BatchEnergySchedule.MAX_STAGES || encoded.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Invalid energy schedule list");
            var stages = new java.util.ArrayList<BatchEnergySchedule.Stage>();
            for (Tag element : encoded) {
                CompoundTag stage = (CompoundTag) element;
                requireFields(stage, Set.of("round", "machines"));
                requireType(stage, "round", Tag.TAG_INT); requireType(stage, "machines", Tag.TAG_INT);
                stages.add(new BatchEnergySchedule.Stage(stage.getInt("round"), stage.getInt("machines")));
            }
            schedule = new BatchEnergySchedule(stages);
        }
        return new RunningBatch(tag.getUUID("id"), tag.getString("fingerprint"),
                new FactoryRules.Environment(tag.getInt("n"), tag.getInt("m")), tag.getInt("parallelism"),
                ResourceNbtCodec.readInventory((ListTag) tag.get("inputs")), ResourceNbtCodec.readInventory((ListTag) tag.get("outputs")),
                tag.contains("catalysts") ? ResourceNbtCodec.readInventory((ListTag) tag.get("catalysts")) : ResourceInventory.empty(),
                fee, tag.getInt("paidTicks"),
                tag.contains("requiredTicks") ? tag.getInt("requiredTicks") : FactoryRules.BATCH_TICKS, schedule);
    }
}
