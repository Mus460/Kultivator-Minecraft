package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The one new cross-module bus of DESIGN 38 (38.E.3; old events stay on {@code DuniaEvents}). Each event is fired only
 * by the owner named in its comment, through its {@code fire*} helper, and anyone may listen. A listener that throws
 * is logged and skipped, so one module never breaks another's moment. Themed cities fire {@link #ACARA} with
 * {@code "tema.<acara>"} and {@link #QUEST} like every other place (38.T.14.4).
 */
public final class TempatEvents {

	/** Fired by 38PR when a damage cluster of a cared-for place is recorded ({@code pelaku} null: monster, raid, unknown). */
	@FunctionalInterface
	public interface TempatRusak {
		void onTempatRusak(ServerLevel level, long situsId, BoundingBox gugus, int blok, @Nullable UUID pelaku);
	}

	/** Fired by 38PR when a damage bill is issued ({@code dibayar} false) or settled ({@code dibayar} true). */
	@FunctionalInterface
	public interface Tagihan {
		void onTagihan(ServerPlayer player, long situsId, long jumlah, boolean dibayar);
	}

	/** Fired by the event owner (38KS capital events, 38DS harvest festival, 38TK themed events) on start and end. */
	@FunctionalInterface
	public interface Acara {
		void onAcara(ServerLevel level, long situsId, String acara, boolean mulai);
	}

	/** Fired by 38KS when a crime is recorded against {@code player} in town {@code town}. */
	@FunctionalInterface
	public interface Kejahatan {
		void onKejahatan(ServerPlayer player, long town, String jenis);
	}

	/** Fired by 38ST when quest {@code id} of {@code player} reaches stage {@code tahap} (-1 failed, 999 finished). */
	@FunctionalInterface
	public interface Quest {
		void onQuest(ServerPlayer player, String id, int tahap);
	}

	/** Quest stage meaning "finished". */
	public static final int QUEST_SELESAI = 999;
	/** Quest stage meaning "failed". */
	public static final int QUEST_GAGAL = -1;

	public static final Event<TempatRusak> TEMPAT_RUSAK = EventFactory.createArrayBacked(TempatRusak.class, listeners ->
			(level, situsId, gugus, blok, pelaku) -> {
				for (TempatRusak listener : listeners) {
					try {
						listener.onTempatRusak(level, situsId, gugus, blok, pelaku);
					} catch (RuntimeException e) {
						failed("TEMPAT_RUSAK", e);
					}
				}
			});

	public static final Event<Tagihan> TAGIHAN = EventFactory.createArrayBacked(Tagihan.class, listeners ->
			(player, situsId, jumlah, dibayar) -> {
				for (Tagihan listener : listeners) {
					try {
						listener.onTagihan(player, situsId, jumlah, dibayar);
					} catch (RuntimeException e) {
						failed("TAGIHAN", e);
					}
				}
			});

	public static final Event<Acara> ACARA = EventFactory.createArrayBacked(Acara.class, listeners ->
			(level, situsId, acara, mulai) -> {
				for (Acara listener : listeners) {
					try {
						listener.onAcara(level, situsId, acara, mulai);
					} catch (RuntimeException e) {
						failed("ACARA", e);
					}
				}
			});

	public static final Event<Kejahatan> KEJAHATAN = EventFactory.createArrayBacked(Kejahatan.class, listeners ->
			(player, town, jenis) -> {
				for (Kejahatan listener : listeners) {
					try {
						listener.onKejahatan(player, town, jenis);
					} catch (RuntimeException e) {
						failed("KEJAHATAN", e);
					}
				}
			});

	public static final Event<Quest> QUEST = EventFactory.createArrayBacked(Quest.class, listeners ->
			(player, id, tahap) -> {
				for (Quest listener : listeners) {
					try {
						listener.onQuest(player, id, tahap);
					} catch (RuntimeException e) {
						failed("QUEST", e);
					}
				}
			});

	public static void fireTempatRusak(ServerLevel level, long situsId, BoundingBox gugus, int blok, @Nullable UUID pelaku) {
		TEMPAT_RUSAK.invoker().onTempatRusak(level, situsId, gugus, blok, pelaku);
	}

	public static void fireTagihan(ServerPlayer player, long situsId, long jumlah, boolean dibayar) {
		TAGIHAN.invoker().onTagihan(player, situsId, jumlah, dibayar);
	}

	public static void fireAcara(ServerLevel level, long situsId, String acara, boolean mulai) {
		ACARA.invoker().onAcara(level, situsId, acara, mulai);
	}

	public static void fireKejahatan(ServerPlayer player, long town, String jenis) {
		KEJAHATAN.invoker().onKejahatan(player, town, jenis);
	}

	public static void fireQuest(ServerPlayer player, String id, int tahap) {
		QUEST.invoker().onQuest(player, id, tahap);
	}

	private static void failed(String event, RuntimeException e) {
		Kultivator.LOGGER.error("A Tempat {} listener failed", event, e);
	}

	private TempatEvents() {
	}
}
