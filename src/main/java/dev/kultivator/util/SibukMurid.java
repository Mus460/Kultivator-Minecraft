package dev.kultivator.util;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * SibukMurid, the "is this disciple busy?" door of DESIGN 39 (39.10.1, contract C39.A3), the disciple twin of
 * {@link Sibuk}. The planner and every module of 39 (and new modules after it) ask {@link #why} before they hand a
 * disciple an activity, a journey, a Hu Fa duty or a feud scene; the answer is a translation key
 * ({@code kultivator.sibuk_murid.<id>} for the built-in list) or null when free.
 * <p>
 * The built-in list is registered lazily on the first question, from public APIs only. 37 and 38 may register their own
 * ({@link #register}, {@link #addProvider}: 37's sword patrol, 38's prison...). The DESIGN 36 modules keep their own free
 * tests unchanged; this door is only used by 39 and later modules. An activity's executor asking about its own disciple
 * uses {@link #whyExcept} with its own entry ({@code "kebun"} for the gardener bridge, {@code "deviasi"} for light
 * activities that a deviating disciple may still do, {@code "aktivitas"} while switching between two activities of its
 * own). Server side only; nothing here touches a block or a client class.
 */
public final class SibukMurid {

	/** Translation key prefix of the built-in reasons. */
	public static final String KEY_PREFIX = "kultivator.sibuk_murid.";

	private record Entry(String id, Predicate<DiscipleEntity> test) {
	}

	private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
	private static final List<Function<DiscipleEntity, String>> PROVIDERS = new CopyOnWriteArrayList<>();
	private static volatile boolean defaults;

	/** Registers (or replaces) the busy check {@code id}; its reason key is {@code kultivator.sibuk_murid.<id>}. */
	public static void register(String id, Predicate<DiscipleEntity> sibuk) {
		if (id == null || sibuk == null) {
			return;
		}
		ENTRIES.removeIf(old -> old.id().equals(id));
		ENTRIES.add(new Entry(id, sibuk));
	}

	/** Adds a provider that names its own reason key, or returns null (modules keeping their keys in their own fragments). */
	public static void addProvider(Function<DiscipleEntity, String> provider) {
		if (provider != null) {
			PROVIDERS.add(provider);
		}
	}

	/** Why the disciple cannot be handed something new now (a translation key), or null when free. */
	public static @Nullable String why(DiscipleEntity entity) {
		return whyExcept(entity, null);
	}

	/** Like {@link #why}, ignoring the entry {@code self} (a system asking while its own state is already set). */
	public static @Nullable String whyExcept(DiscipleEntity entity, @Nullable String self) {
		if (entity == null) {
			return KEY_PREFIX + "peran";
		}
		ensureDefaults();
		for (Entry entry : ENTRIES) {
			if (entry.id().equals(self)) {
				continue;
			}
			boolean busy;
			try {
				busy = entry.test().test(entity);
			} catch (RuntimeException exception) {
				// A module that is off or not ready never holds the disciple.
				busy = false;
			}
			if (busy) {
				return KEY_PREFIX + entry.id();
			}
		}
		for (Function<DiscipleEntity, String> provider : PROVIDERS) {
			String key;
			try {
				key = provider.apply(entity);
			} catch (RuntimeException exception) {
				key = null;
			}
			if (key != null) {
				return key;
			}
		}
		return null;
	}

	/** True when anything holds the disciple. */
	public static boolean busy(DiscipleEntity entity) {
		return why(entity) != null;
	}

	/** The reason as a component (a fallback keeps a missing key readable). */
	public static MutableComponent text(String key) {
		return Component.translatableWithFallback(key, "Busy");
	}

	// ================================================================== the built-in list (39.10.1)

	private static void ensureDefaults() {
		if (defaults) {
			return;
		}
		synchronized (SibukMurid.class) {
			if (defaults) {
				return;
			}
			defaults = true;
			List<Entry> early = List.copyOf(ENTRIES);
			ENTRIES.clear();
			ENTRIES.add(new Entry("peran", e -> e.role() != DiscipleEntity.Role.DISCIPLE || e.isReleasing() || !e.isAlive()));
			ENTRIES.add(new Entry("terobosan", e -> e.data().breakingThrough()));
			ENTRIES.add(new Entry("duel", e -> DuelRules.inDuel(e) || DuelSessions.byEntity(e) != null));
			ENTRIES.add(new Entry("tempur", e -> e.getTarget() != null));
			ENTRIES.add(new Entry("perintah", SibukMurid::underOrder));
			ENTRIES.add(new Entry("kantor", e -> e.activity() == SekteIds.ACT_OFFICE));
			ENTRIES.add(new Entry("aktivitas", SibukMurid::foreignActivity));
			ENTRIES.add(new Entry("membangkang", e -> SekteApi.hati().defiant(e.data())));
			ENTRIES.add(new Entry("turnamen", e -> pair(e, (owner, soul) -> SekteApi.turnamen().busy(owner, soul))));
			ENTRIES.add(new Entry("kebun", e -> pair(e, (owner, soul) -> SekteApi.kebun().working(owner, soul))));
			ENTRIES.add(new Entry("misi", e -> pair(e, (owner, soul) -> SekteApi.misi().away(owner, soul))));
			ENTRIES.add(new Entry("paviliun", e -> pair(e, (owner, soul) -> SekteApi.paviliun().studying(owner, soul) != null)));
			ENTRIES.add(new Entry("luka_berat", e -> {
				JenisLuka luka = MandiriApi.luka(e.data(), e.level().getGameTime());
				return luka == JenisLuka.BERAT || luka == JenisLuka.TERTAWAN;
			}));
			ENTRIES.add(new Entry("tutup_diri", e -> MandiriApi.tutupDiri(e.data(), e.level().getGameTime())));
			ENTRIES.add(new Entry("perjalanan", e -> {
				Kegiatan kegiatan = MandiriApi.kegiatan(e);
				return (kegiatan != null && kegiatan.perjalanan()) || pair(e, MandiriApi::sedangPergi);
			}));
			ENTRIES.add(new Entry("deviasi", e -> e.data().deviating()));
			for (Entry entry : early) {
				register(entry.id(), entry.test());
			}
		}
	}

	/**
	 * The master's order holds it: the order pause of 39.7.2 has not ended, or it follows / assists the master, or it
	 * guards the hall by order (a gate watch the planner itself gave, {@code Kegiatan.JAGA_GERBANG}, does not count).
	 */
	private static boolean underOrder(DiscipleEntity e) {
		DiscipleData data = e.data();
		if (MandiriApi.bebasSampai(data) > e.level().getGameTime()) {
			return true;
		}
		DiscipleEntity.Mode mode = e.mode();
		if (mode.follows()) {
			return true;
		}
		return mode == DiscipleEntity.Mode.GUARD_HALL && MandiriApi.kegiatan(e) != Kegiatan.JAGA_GERBANG;
	}

	/** An activity that DESIGN 39 did not set (duties, keepers, builders, 36 and 37 activities) holds it. */
	private static boolean foreignActivity(DiscipleEntity e) {
		int act = e.activity();
		if (act == 0 || SekteIds.mandiriActivity(act)) {
			return false;
		}
		Kegiatan kegiatan = MandiriApi.kegiatan(e);
		// A borrowed id (13 study, 15/16 mine/garden, 18 tea, 11 keeper) set by a 39 bridge is our own activity.
		return kegiatan == null || kegiatan.act() != act;
	}

	@FunctionalInterface
	private interface PairTest {
		boolean test(UUID owner, UUID soul);
	}

	private static boolean pair(DiscipleEntity e, PairTest test) {
		UUID owner = e.ownerId();
		UUID soul = e.soulId();
		return owner != null && soul != null && test.test(owner, soul);
	}

	private SibukMurid() {
	}
}
