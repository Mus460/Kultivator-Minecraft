package dev.kultivator.client.anim;

import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.run.BindKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The synced visual binds (DESIGN 27.4, polish plan C2/C3): who is held by vines, rope, chains, a seal, frost, stopped
 * time, a suspension, sleep or a karmic link, by whom and for how long. {@code BindFx} (module M2) draws the chains and
 * glyphs from {@link #all()}; {@code BindVisuals} freezes and tints the bound bodies and {@code ArtModels} grows the
 * vine, ice and rope props this module owns. Times are client ticks ({@link AnimClient#ticks()}).
 */
public final class ClientBinds {

	/** One bind: {@code ticks()} is what is left of it now. */
	public record Bind(int entity, BindKind kind, int caster, int spec, long startTick, int total) {

		/** Ticks left (0 when over). */
		public int ticks() {
			return (int) Math.max(0L, startTick + total - AnimClient.ticks());
		}

		/** Ticks since the bind began. */
		public int age() {
			return (int) Math.max(0L, AnimClient.ticks() - startTick);
		}
	}

	private static final Map<Integer, List<Bind>> BINDS = new HashMap<>();

	/** The live binds on one entity (empty when none). */
	public static List<Bind> of(int entityId) {
		List<Bind> list = BINDS.get(entityId);
		if (list == null) {
			return List.of();
		}
		list.removeIf(b -> b.ticks() <= 0);
		return list;
	}

	/** Every live bind. */
	public static Collection<Bind> all() {
		List<Bind> out = new ArrayList<>();
		for (List<Bind> list : BINDS.values()) {
			for (Bind bind : list) {
				if (bind.ticks() > 0) {
					out.add(bind);
				}
			}
		}
		return out;
	}

	/** The bind of {@code kind} on an entity, or null. */
	public static @Nullable Bind find(int entityId, BindKind kind) {
		for (Bind bind : of(entityId)) {
			if (bind.kind() == kind) {
				return bind;
			}
		}
		return null;
	}

	/** True while the entity is frozen in place (frost, stopped time, a seal, chains). */
	public static boolean frozen(int entityId) {
		for (Bind bind : of(entityId)) {
			if (bind.kind() == BindKind.FREEZE || bind.kind() == BindKind.TIME_STOP || bind.kind() == BindKind.SEAL
					|| bind.kind() == BindKind.CHAINS) {
				return true;
			}
		}
		return false;
	}

	/** BindSync receiver (AnimClient). */
	static void onSync(Payloads.BindSync payload) {
		BindKind kind = BindKind.byOrdinal(payload.kind());
		List<Bind> list = BINDS.computeIfAbsent(payload.entity(), id -> new ArrayList<>(2));
		Bind old = null;
		for (Bind bind : list) {
			if (bind.kind() == kind) {
				old = bind;
			}
		}
		list.removeIf(b -> b.kind() == kind);
		if (payload.ended()) {
			if (list.isEmpty()) {
				BINDS.remove(payload.entity());
			}
			return;
		}
		long now = AnimClient.ticks();
		long start = old != null ? old.startTick() : now;
		Bind bind = new Bind(payload.entity(), kind, payload.caster(), payload.spec(), start, (int) (now - start) + payload.ticks());
		list.add(bind);
		if (old == null) {
			dev.kultivator.client.anim.model.ArtModels.onBind(bind);
		}
	}

	static void prune(@Nullable ClientLevel level) {
		if (level == null) {
			BINDS.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, List<Bind>>> it = BINDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, List<Bind>> entry = it.next();
			entry.getValue().removeIf(b -> b.ticks() <= 0);
			Entity entity = level.getEntity(entry.getKey());
			if (entry.getValue().isEmpty() || entity == null || entity.isRemoved()) {
				it.remove();
			}
		}
	}

	static void clear() {
		BINDS.clear();
	}

	private ClientBinds() {
	}
}
