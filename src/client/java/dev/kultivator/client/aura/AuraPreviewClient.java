package dev.kultivator.client.aura;

import dev.kultivator.aura.AuraPreviewPayload;
import dev.kultivator.data.Realm;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The client side of {@code /kultivasi uji aura} (DESIGN 31.6, C31.5): per entity id, the previewed realm, layer, stage
 * and flags until a client tick. The server re-sends a live preview every 20 ticks with the ticks left (so late viewers
 * catch up); {@code ticks = 0} ends it. Nothing here changes a rank; the engine draws the aura as if the subject were
 * cultivating, with its real Dao, root and accent.
 */
public final class AuraPreviewClient {

	/** Payload flags. */
	public static final int FORCED_ACCENT = 1;
	public static final int BOTTLENECK = 2;
	public static final int CYCLE_STEP = 4;

	/** One live preview. */
	public record Preview(Realm realm, int layer, int stage, int flags, long until) {
		public boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private static final Map<Integer, Preview> PREVIEWS = new HashMap<>();
	private static long ticks;

	/** A preview payload arrived (client thread). */
	public static void onPayload(AuraPreviewPayload payload) {
		int id = payload.entityId();
		if (payload.ticks() <= 0) {
			PREVIEWS.remove(id);
			return;
		}
		Realm realm = Realm.byOrdinal(Math.max(0, Math.min(Realm.values().length - 1, payload.realm())));
		int layer = realm.layers() > 1 ? Math.max(0, Math.min(realm.layers() - 1, payload.layer())) : 0;
		int stage = Math.max(0, Math.min(realm.stages() - 1, payload.stage()));
		PREVIEWS.put(id, new Preview(realm, layer, stage, payload.flags(), ticks + payload.ticks()));
	}

	/** The live preview of an entity, or null. */
	public static @Nullable Preview get(int entityId) {
		Preview preview = PREVIEWS.get(entityId);
		if (preview != null && preview.until() <= ticks) {
			PREVIEWS.remove(entityId);
			return null;
		}
		return preview;
	}

	/** Entity ids with a preview (live or just expired). */
	public static Set<Integer> ids() {
		return PREVIEWS.keySet();
	}

	public static boolean any() {
		return !PREVIEWS.isEmpty();
	}

	/** Once per unpaused client tick. */
	static void tick() {
		ticks++;
		if (!PREVIEWS.isEmpty()) {
			PREVIEWS.values().removeIf(preview -> preview.until() <= ticks);
		}
	}

	/** World change or disconnect. */
	static void clear() {
		PREVIEWS.clear();
	}

	private AuraPreviewClient() {
	}
}
