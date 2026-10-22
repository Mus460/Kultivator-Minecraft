package dev.kultivator.client.sekte.seragam;

import dev.kultivator.sekte.seragam.SeragamData;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module S on the client (DESIGN 36.2.8): every design the server sent ({@code SeragamPayloads.LambangSync}), by key
 * ({@code u:<owner>}, {@code npc:<sect>}). An entry keeps its design for the robes even after {@code LambangForget}
 * (only its banners and decals go); at most {@link #MAX_ENTRIES} entries live, the least recently used one goes first.
 * Client thread only.
 */
public final class LambangCache {

	public static final int MAX_ENTRIES = 12;

	/** One hall's design and where it shows. */
	public static final class Entry {
		public final String key;
		public int version;
		public String dim = "";
		public SeragamData data = SeragamData.DEFAULT;
		public List<SeragamPayloads.Banner> banners = List.of();
		public List<SeragamPayloads.Decal> decals = List.of();
		public int maxBanners = 12;
		public int animRange = 48;
		/** Client tick after which it drops by itself (0 = never). */
		public long expires;
		/** Bumped on every change (the atlas rebuilds when it differs from what it baked). */
		public int stamp;
		long used;

		Entry(String key) {
			this.key = key;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static int stamps;
	private static long clock;

	static void onSync(SeragamPayloads.LambangSync sync, long tick) {
		Entry entry = ENTRIES.get(sync.key());
		if (entry == null) {
			trim();
			entry = new Entry(sync.key());
			ENTRIES.put(sync.key(), entry);
		}
		boolean look = entry.version != sync.version() || !entry.data.equals(sync.data());
		entry.version = sync.version();
		entry.dim = sync.dim();
		entry.data = sync.data();
		entry.banners = List.copyOf(sync.banners());
		entry.decals = List.copyOf(sync.decals());
		entry.maxBanners = sync.maxBanners();
		entry.animRange = Math.max(16, sync.animRange());
		entry.expires = sync.ttl() > 0 ? tick + sync.ttl() : 0L;
		entry.used = ++clock;
		if (look) {
			entry.stamp = ++stamps;
		}
	}

	static void onForget(String key) {
		Entry entry = ENTRIES.get(key);
		if (entry != null) {
			entry.banners = List.of();
			entry.decals = List.of();
		}
	}

	static void tick(long tick) {
		for (Entry entry : ENTRIES.values()) {
			if (entry.expires > 0L && tick > entry.expires) {
				entry.banners = List.of();
				entry.decals = List.of();
				entry.expires = 0L;
			}
		}
	}

	private static void trim() {
		while (ENTRIES.size() >= MAX_ENTRIES) {
			Entry oldest = null;
			for (Entry entry : ENTRIES.values()) {
				if (oldest == null || entry.used < oldest.used) {
					oldest = entry;
				}
			}
			if (oldest == null) {
				return;
			}
			ENTRIES.remove(oldest.key);
			LambangAtlas.release(oldest.key);
		}
	}

	public static @Nullable Entry get(String key) {
		Entry entry = ENTRIES.get(key);
		if (entry != null) {
			entry.used = ++clock;
		}
		return entry;
	}

	public static @Nullable Entry forOwner(@Nullable UUID owner) {
		return owner == null ? null : get("u:" + owner);
	}

	/** Every entry (a copy; the renderers iterate it). */
	public static List<Entry> all() {
		return new ArrayList<>(ENTRIES.values());
	}

	static Collection<Entry> live() {
		return ENTRIES.values();
	}

	static boolean anyGeometry() {
		for (Entry entry : ENTRIES.values()) {
			if (!entry.banners.isEmpty() || !entry.decals.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	static void clear() {
		ENTRIES.clear();
	}

	private LambangCache() {
	}
}
