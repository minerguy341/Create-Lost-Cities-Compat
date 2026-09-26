package com.minerguy341.createlostcities.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-level bookkeeping:
 * <ul>
 *     <li>{@code pending}: railway chunks that still need tracks (waiting for their neighbours to load);</li>
 *     <li>{@code done}: chunks already stitched, so retrofit and reloads never stitch twice;</li>
 *     <li>{@code names}: station block positions waiting for Create to create their edge point.</li>
 * </ul>
 */
public class SubwaySavedData extends SavedData {

    private static final String NAME = "create_lostcities_subways";

    final LongSet pending = new LongOpenHashSet();
    final LongSet done = new LongOpenHashSet();
    final Long2ObjectMap<String> names = new Long2ObjectOpenHashMap<>();

    public static SubwaySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SubwaySavedData::load, SubwaySavedData::new, NAME);
    }

    public static SubwaySavedData load(CompoundTag tag) {
        SubwaySavedData data = new SubwaySavedData();
        for (long l : tag.getLongArray("Pending")) {
            data.pending.add(l);
        }
        for (long l : tag.getLongArray("Done")) {
            data.done.add(l);
        }
        ListTag list = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.names.put(entry.getLong("Pos"), entry.getString("Name"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Pending", new LongArrayTag(pending.toLongArray()));
        tag.put("Done", new LongArrayTag(done.toLongArray()));
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<String> e : names.long2ObjectEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", e.getLongKey());
            entry.putString("Name", e.getValue());
            list.add(entry);
        }
        tag.put("Names", list);
        return tag;
    }

    public int pendingCount() {
        return pending.size();
    }

    public int doneCount() {
        return done.size();
    }

    public int unnamedCount() {
        return names.size();
    }

    public boolean isDone(long chunk) {
        return done.contains(chunk);
    }

    public boolean isPending(long chunk) {
        return pending.contains(chunk);
    }
}
