package dev.kultivator.sekte.hubungan;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.HubunganService;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.MisiHasil;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.sekte.SekteSuara;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Module R, Hubungan antar-murid (DESIGN 36.2.2, contracts C36.R1-R4): the friendships, sworn kinship, love, rivalries
 * and grudges between the disciples of one sect. {@link #init()} (one line in {@code SekteContent}) sets the
 * {@link HubunganService}, listens to the bus, adds the bond effects ({@link EfekIkatan}), the tea turns
 * ({@link GiliranSosial}), the ceremonies ({@link Upacara}), the honour duel ({@link DuelKehormatan}), the commands and
 * the disciple-scroll info {@code sekte_hubungan}.
 * <p>
 * Every change of a pair goes through {@link #change}: the trait factors of module P, the caps (+-15 per call, +-25 per
 * pair per day), the history, the memories ({@code sekte.Ingatan}), the kind and, when the kind changes,
 * {@code SekteBus.RELATION_CHANGED}, the hall log, the master's chat line, the bubbles and the advancements.
 * <p>
 * Sumber: the Peach Garden Oath of Liu Bei, Guan Yu and Zhang Fei (<i>Romance of the Three Kingdoms</i> ch. 1); Wang Lin
 * and his Dao companion Li Muwan (<i>Renegade Immortal</i>); the rivalries of the seniors and juniors of <i>A Will
 * Eternal</i>; the wedding cups "he jin" of the <i>Liji</i> (Hunyi) for the Dao companions' rite.
 */
public final class Hubungan {

	/** {@link #change} flags. */
	static final int CAP = 1;
	static final int TRAITS = 2;
	static final int MEMORY = 4;
	static final int MEETING = 8;
	static final int QUIET = 16;
	static final int EVENT = CAP | TRAITS | MEMORY;

	/** Info key of the disciple scroll (DESIGN 36.3.8). */
	public static final String INFO_KEY = "sekte_hubungan";

	/** The reasons whose memory and history have their own text ({@code ...ingat.<suffix>}, {@code ...sebab.<suffix>}). */
	private static final Set<String> KNOWN = Set.of("teh", "selamat", "turnamen_babak", "turnamen_upset", "misi_berhasil", "misi_sebagian",
			"misi_gagal", "misi_selamat", "paviliun_duka", "paviliun_qingming", "tetua_jabatan", "tetua_bangga", "sumpah", "restu", "larang",
			"duel", "uji", "pergi", "mandiri_teh", "mandiri_tanding");

	private static final int COLOR_GOOD = 0x8FE3B0;
	private static final int COLOR_LOVE = 0xFF9CC4;
	private static final int COLOR_GOLD = 0xF2C66A;
	private static final int COLOR_BAD = 0xE59A6A;
	private static final int COLOR_GRUDGE = 0xE0584A;

	private static @Nullable MinecraftServer server;
	private static boolean initialised;
	private static final RandomSource RANDOM = RandomSource.create();
	/** Bodies already checked this load (name noted, parked pairs restored). */
	private static final Map<DiscipleEntity, Boolean> SEEN = new WeakHashMap<>();

	/** Payload types (K1): always, from {@code SekteContent.init}, whatever the switches say. */
	public static void registerContent() {
		HubunganPayloads.init();
	}

	public static void init() {
		registerContent();
		if (initialised || !SekteContent.on(SekteIds.R)) {
			return;
		}
		initialised = true;
		HubunganConfig.load();
		HubunganPayloads.initServer();
		SekteApi.setHubungan(new Service());
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			GiliranSosial.clear();
			DuelKehormatan.clear();
			EfekIkatan.clear();
			Upacara.clear();
		});
		SekteBus.TOURNAMENT_BOUT.register(Hubungan::onBout);
		SekteBus.MISSION_RETURNED.register(Hubungan::onMission);
		SekteBus.DISCIPLE_BURIED.register(Hubungan::onBuried);
		SekteBus.DISCIPLE_LEFT.register(Hubungan::onLeft);
		SekteBus.ELDER_APPOINTED.register(Hubungan::onElder);
		SekteBus.ANCESTOR_RITE.register(Hubungan::onRite);
		DiscipleHooks.addInfo(Hubungan::info);
		DiscipleHooks.addTick20(Hubungan::tick20);
		EfekIkatan.init();
		GiliranSosial.init();
		DuelKehormatan.init();
		HubunganCommands.init();
	}

	static boolean active() {
		return initialised && HubunganConfig.get().aktif && SekteContent.on(SekteIds.R);
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	static long today() {
		return Ingatan.today(server);
	}

	static RandomSource random() {
		return RANDOM;
	}

	// ================================================================== the service (C36.R1)

	private static final class Service implements HubunganService {
		@Override
		public int affinity(UUID owner, UUID a, UUID b) {
			TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
			return pair == null ? 0 : pair.affinity();
		}

		@Override
		public Ikatan kind(UUID owner, UUID a, UUID b) {
			TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
			return pair == null ? Ikatan.NONE : pair.kind();
		}

		@Override
		public void adjust(UUID owner, UUID a, UUID b, int delta, String reason) {
			change(owner, a, b, delta, reason == null || reason.isEmpty() ? "lain" : reason, EVENT);
		}

		@Override
		public List<UUID> with(UUID owner, UUID soul, Ikatan kind) {
			if (owner == null || soul == null || kind == null) {
				return List.of();
			}
			List<TabelPasangan.Pair> found = new ArrayList<>();
			for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
				if (pair.kind() == kind) {
					found.add(pair);
				}
			}
			found.sort(Comparator.comparingInt((TabelPasangan.Pair p) -> -Math.abs(p.affinity())));
			List<UUID> out = new ArrayList<>(found.size());
			for (TabelPasangan.Pair pair : found) {
				out.add(pair.other(soul));
			}
			return out;
		}

		@Override
		public int socialBalance(UUID owner, UUID soul) {
			return balance(owner, soul);
		}
	}

	static int balance(@Nullable UUID owner, @Nullable UUID soul) {
		if (owner == null || soul == null || !active()) {
			return 0;
		}
		DiscipleEntity body = loaded(owner).get(soul);
		boolean proud = body != null && SekteApi.hati().has(body.data(), Sifat.SOMBONG);
		int sum = 0;
		for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
			sum += Kecocokan.balanceOf(pair.kind(), pair.affinity(), proud);
		}
		return Math.max(-100, Math.min(100, sum));
	}

	// ================================================================== the one funnel

	/** The master's loaded disciples by soul. */
	static Map<UUID, DiscipleEntity> loaded(UUID owner) {
		Map<UUID, DiscipleEntity> map = new HashMap<>();
		if (owner != null) {
			for (DiscipleEntity disciple : Disciples.loaded(owner)) {
				if (disciple.role() == DiscipleEntity.Role.DISCIPLE) {
					map.put(disciple.soulId(), disciple);
				}
			}
		}
		return map;
	}

	/**
	 * Changes the pair (a, b) of {@code owner}'s hall by {@code delta} for {@code reason} ({@code <module>.<what>}) with
	 * {@code flags} ({@link #CAP}, {@link #TRAITS}, {@link #MEMORY}, {@link #MEETING}, {@link #QUIET}); returns the change
	 * actually applied. A zero change still re-reads the kind (tournament bouts alone can make rivals).
	 */
	static int change(@Nullable UUID owner, @Nullable UUID a, @Nullable UUID b, int delta, String reason, int flags) {
		if (!active() || owner == null || a == null || b == null || a.equals(b)) {
			return 0;
		}
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(owner, a, b);
		if (pair == null) {
			return 0;
		}
		long today = today();
		Map<UUID, DiscipleEntity> bodies = loaded(owner);
		DiscipleEntity ea = bodies.get(a);
		DiscipleEntity eb = bodies.get(b);
		DiscipleData da = ea == null ? null : ea.data();
		DiscipleData db = eb == null ? null : eb.data();
		int d = delta;
		if ((flags & TRAITS) != 0) {
			d = Kecocokan.traitScaled(d, da, db);
		}
		if ((flags & CAP) != 0) {
			d = Math.max(-15, Math.min(15, d));
			int used = pair.changedToday(today);
			if (d > 0) {
				d = Math.min(d, Math.max(0, 25 - used));
			} else if (d < 0) {
				d = Math.max(d, Math.min(0, -25 - used));
			}
		}
		int before = pair.affinity();
		pair.setAffinity(before + d);
		int applied = pair.affinity() - before;
		pair.addChangedToday(today, applied);
		if (applied <= Kecocokan.WOUND) {
			pair.setWounded(true);
		} else if (pair.affinity() > 0) {
			pair.setWounded(false);
		}
		if (applied != 0) {
			pair.pushHistory(reason, applied, today);
		}
		if ((flags & MEETING) != 0) {
			pair.addMeeting(today);
		}
		if (ea != null) {
			TabelPasangan.rememberName(owner, a, ea.getName());
		}
		if (eb != null) {
			TabelPasangan.rememberName(owner, b, eb.getName());
		}
		if ((flags & MEMORY) != 0 && Math.abs(applied) >= 3) {
			int weight = Math.max(-8, Math.min(8, (int) Math.round(applied / 2.0D)));
			if (weight != 0) {
				remember(ea, b, reason, weight, TabelPasangan.name(owner, b));
				remember(eb, a, reason, weight, TabelPasangan.name(owner, a));
			}
		}
		Ikatan was = pair.kind();
		HubunganConfig config = HubunganConfig.get();
		Ikatan now = Kecocokan.derive(was, pair.affinity(), pair.tournamentBouts(), pair.wounded(), pair.known(), config.dendamAktif);
		if ((flags & MEETING) != 0 && now == Ikatan.SAHABAT && config.cintaAktif && !pair.banned() && pair.affinity() >= Kecocokan.LOVE
				&& Kecocokan.loveRealm(da, db) && !hasLove(owner, a) && !hasLove(owner, b) && RANDOM.nextDouble() < config.peluangCinta) {
			now = Ikatan.KEKASIH;
		}
		if (now != was) {
			setKind(owner, pair, was, now, ea, eb, (flags & QUIET) != 0);
		}
		Upacara.maybePropose(owner, pair, da, db);
		Halls.setDirty();
		EfekIkatan.invalidate(a);
		EfekIkatan.invalidate(b);
		return applied;
	}

	/** True when {@code soul} already has a lover or Dao companion. */
	static boolean hasLove(UUID owner, UUID soul) {
		for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
			if (pair.kind() == Ikatan.KEKASIH || pair.kind() == Ikatan.PASANGAN_DAO) {
				return true;
			}
		}
		return false;
	}

	/** The memory kind and text suffix of a reason: {@code hubungan.teh} -> {@code teh}, {@code turnamen.babak} -> {@code turnamen_babak}. */
	static String suffix(String reason) {
		String r = reason == null ? "" : reason;
		String s = r.startsWith(SekteIds.R + ".") ? r.substring(SekteIds.R.length() + 1) : r.replace('.', '_');
		return s.isEmpty() ? "lain" : s;
	}

	static void remember(@Nullable DiscipleEntity body, UUID other, String reason, int weight, Component otherName) {
		if (body == null) {
			return;
		}
		String s = suffix(reason);
		String text = "kultivator.sekte.hubungan.ingat." + (KNOWN.contains(s) ? s : "lain");
		Ingatan.remember(server, body.data(), SekteIds.R + "." + s, other, weight, text, otherName.getString());
	}

	/** Sets a new kind and tells everyone (bus, log, master, bubbles, memories, advancements). */
	static void setKind(UUID owner, TabelPasangan.Pair pair, Ikatan was, Ikatan now, @Nullable DiscipleEntity ea, @Nullable DiscipleEntity eb,
			boolean quiet) {
		pair.setKind(now);
		Halls.setDirty();
		if (was == Ikatan.PASANGAN_DAO && now != Ikatan.PASANGAN_DAO) {
			markDao(ea, null);
			markDao(eb, null);
		}
		EfekIkatan.invalidate(pair.a);
		EfekIkatan.invalidate(pair.b);
		if (server != null) {
			SekteBus.RELATION_CHANGED.invoker().onRelationChanged(server, owner, pair.a, pair.b, was, now);
		}
		if (quiet || now == Ikatan.KENALAN && (was == Ikatan.NONE || was == Ikatan.KENALAN) || now == Ikatan.NONE && was == Ikatan.KENALAN) {
			return;
		}
		Component na = TabelPasangan.name(owner, pair.a);
		Component nb = TabelPasangan.name(owner, pair.b);
		String line;
		int color;
		int icon;
		int weight;
		if (rank(now) > rank(was) && now.positive()) {
			line = "jadi." + now.id();
			color = now == Ikatan.KEKASIH || now == Ikatan.PASANGAN_DAO ? COLOR_LOVE : now == Ikatan.SAUDARA_ANGKAT ? COLOR_GOLD : COLOR_GOOD;
			icon = now == Ikatan.PASANGAN_DAO ? HubunganPayloads.ICON_TERATAI : now == Ikatan.SAUDARA_ANGKAT ? HubunganPayloads.ICON_SIMPUL
					: HubunganPayloads.ICON_HATI;
			weight = switch (now) {
				case SAHABAT -> 4;
				case KEKASIH -> 6;
				case SAUDARA_ANGKAT -> 8;
				case PASANGAN_DAO -> 9;
				default -> 2;
			};
		} else if (now.negative() && (!was.negative() || now == Ikatan.DENDAM)) {
			line = "jadi." + now.id();
			color = now == Ikatan.DENDAM ? COLOR_GRUDGE : COLOR_BAD;
			icon = HubunganPayloads.ICON_PETIR;
			weight = now == Ikatan.DENDAM ? -6 : -3;
		} else if (was == Ikatan.DENDAM && now == Ikatan.SAINGAN) {
			line = "reda.dendam";
			color = COLOR_GOLD;
			icon = HubunganPayloads.ICON_BINTANG;
			weight = 3;
		} else if (was.negative() && !now.negative()) {
			line = "reda." + was.id();
			color = COLOR_GOOD;
			icon = HubunganPayloads.ICON_BINTANG;
			weight = 2;
		} else if (was.positive()) {
			line = "putus." + was.id();
			color = COLOR_BAD;
			icon = HubunganPayloads.ICON_PATAH;
			weight = was == Ikatan.PASANGAN_DAO || was == Ikatan.SAUDARA_ANGKAT ? -7 : -4;
		} else {
			return;
		}
		Component text = Component.translatable("kultivator.sekte.hubungan." + line, na, nb);
		SekteLog.log(server, owner, SekteIds.R, text);
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, text.copy().withColor(color));
			if (now == Ikatan.SAHABAT || now == Ikatan.SAUDARA_ANGKAT || now == Ikatan.PASANGAN_DAO) {
				Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "hubungan_sahabat");
			}
			if (now.negative()) {
				Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "hubungan_saingan");
			}
		}
		HubunganPayloads.bubble(ea, eb, icon, 100);
		if (now == Ikatan.DENDAM && was != Ikatan.DENDAM) {
			SekteSuara.line(ea != null ? ea : eb, "grudge", "careful", 1200);
		} else if (now == Ikatan.KEKASIH && was != Ikatan.KEKASIH) {
			SekteSuara.line(ea != null ? ea : eb, "beloved", "thanks", 1200);
		}
		String memory = line.replace('.', '_');
		if (server != null) {
			if (ea != null) {
				Ingatan.remember(server, ea.data(), SekteIds.R + "." + memory, pair.b, weight, "kultivator.sekte.hubungan.ingat." + memory, nb.getString());
			}
			if (eb != null) {
				Ingatan.remember(server, eb.data(), SekteIds.R + "." + memory, pair.a, weight, "kultivator.sekte.hubungan.ingat." + memory, na.getString());
			}
		}
	}

	/** How close a kind is (for "grew closer" against "fell apart"). */
	private static int rank(Ikatan kind) {
		return switch (kind) {
			case NONE -> 0;
			case KENALAN -> 1;
			case SAHABAT -> 2;
			case KEKASIH, SAUDARA_ANGKAT -> 3;
			case PASANGAN_DAO -> 4;
			default -> 0;
		};
	}

	/** Marks a body's Dao companion in {@code extra.ikatan_murid} (null clears). */
	static void markDao(@Nullable DiscipleEntity body, @Nullable UUID partner) {
		if (body == null) {
			return;
		}
		if (partner == null) {
			body.data().extra().remove(SekteIds.EXTRA_R);
		} else {
			body.data().extraSection(SekteIds.EXTRA_R).putString("pasangan", partner.toString());
		}
	}

	// ================================================================== the bus (C36.R2)

	private static void onBout(MinecraftServer s, UUID owner, @Nullable UUID winner, @Nullable UUID loser, boolean upset) {
		if (!active() || winner == null || loser == null || winner.equals(loser)) {
			return;
		}
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(owner, winner, loser);
		if (pair == null) {
			return;
		}
		pair.addTournamentBout();
		DiscipleEntity body = loaded(owner).get(loser);
		boolean gentle = body != null && SekteApi.hati().has(body.data(), Sifat.PENYAYANG);
		int delta = gentle ? 0 : upset ? -6 : -4;
		change(owner, winner, loser, delta, upset ? "turnamen.upset" : "turnamen.babak", EVENT);
	}

	private static void onMission(MinecraftServer s, UUID owner, MisiHasil result) {
		if (!active() || result == null) {
			return;
		}
		List<UUID> team = result.team();
		int delta = result.success() >= 2 ? 6 : result.success() == 1 ? 2 : -2;
		String reason = result.success() >= 2 ? "misi.berhasil" : result.success() == 1 ? "misi.sebagian" : "misi.gagal";
		for (int i = 0; i < team.size(); i++) {
			for (int j = i + 1; j < team.size(); j++) {
				UUID a = team.get(i);
				UUID b = team.get(j);
				change(owner, a, b, delta, reason, EVENT);
				if (result.success() >= 1 && result.injured().contains(a) != result.injured().contains(b)) {
					// the unhurt one brought the hurt one home
					change(owner, a, b, 8, "misi.selamat", EVENT);
				}
			}
		}
	}

	private static void onBuried(MinecraftServer s, UUID owner, UUID soul, Component name) {
		if (!active() || soul == null) {
			return;
		}
		Map<UUID, DiscipleEntity> bodies = loaded(owner);
		List<UUID> mourners = new ArrayList<>();
		for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
			Ikatan kind = pair.kind();
			if (!kind.positive()) {
				continue;
			}
			UUID other = pair.other(soul);
			mourners.add(other);
			DiscipleEntity body = bodies.get(other);
			if (body != null) {
				int weight = kind == Ikatan.PASANGAN_DAO ? -10 : kind == Ikatan.SAUDARA_ANGKAT ? -8 : kind == Ikatan.KEKASIH ? -9 : -6;
				Ingatan.remember(s, body.data(), SekteIds.R + ".duka", soul, weight, "kultivator.sekte.hubungan.ingat.duka", name.getString());
				if (kind == Ikatan.PASANGAN_DAO) {
					markDao(body, null);
				}
			}
		}
		TabelPasangan.drop(owner, soul);
		for (int i = 0; i < mourners.size(); i++) {
			for (int j = i + 1; j < mourners.size(); j++) {
				change(owner, mourners.get(i), mourners.get(j), 3, "paviliun.duka", CAP | MEMORY);
			}
		}
		EfekIkatan.invalidate(soul);
	}

	private static void onLeft(MinecraftServer s, UUID owner, UUID soul, Component name, Kepergian how, @Nullable String toSect,
			@Nullable UUID companion) {
		if (!active() || soul == null) {
			return;
		}
		leave(s, owner, soul, name);
		if (companion != null) {
			leave(s, owner, companion, TabelPasangan.name(owner, companion));
		}
	}

	private static void leave(MinecraftServer s, UUID owner, UUID soul, Component name) {
		Map<UUID, DiscipleEntity> bodies = loaded(owner);
		for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
			if (!pair.kind().positive()) {
				continue;
			}
			DiscipleEntity body = bodies.get(pair.other(soul));
			if (body != null) {
				int weight = pair.kind() == Ikatan.PASANGAN_DAO || pair.kind() == Ikatan.SAUDARA_ANGKAT ? -6 : -3;
				Ingatan.remember(s, body.data(), SekteIds.R + ".pergi", soul, weight, "kultivator.sekte.hubungan.ingat.pergi", name.getString());
				if (pair.kind() == Ikatan.PASANGAN_DAO || pair.kind() == Ikatan.SAUDARA_ANGKAT) {
					SekteLog.log(s, owner, SekteIds.R, Component.translatable("kultivator.sekte.hubungan.log.ditinggal", body.getName(), name,
							Component.translatable(pair.kind().key())));
				}
			}
		}
		TabelPasangan.park(owner, soul);
		EfekIkatan.invalidate(soul);
	}

	private static void onElder(MinecraftServer s, UUID owner, UUID soul, @Nullable Jabatan office) {
		if (!active() || office == null || soul == null) {
			return;
		}
		Map<UUID, DiscipleEntity> bodies = loaded(owner);
		DiscipleEntity appointee = bodies.get(soul);
		if (appointee == null) {
			return;
		}
		int rank = SectRanks.rank(appointee.data());
		int step = appointee.data().rank().step();
		for (DiscipleEntity other : bodies.values()) {
			if (other == appointee) {
				continue;
			}
			TabelPasangan.Pair pair = TabelPasangan.find(owner, soul, other.soulId());
			if (pair != null && pair.kind().positive()) {
				change(owner, soul, other.soulId(), 2, "tetua.bangga", EVENT);
			} else if (SectRanks.rank(other.data()) > rank || other.data().rank().step() > step) {
				// a junior passed over a senior
				change(owner, soul, other.soulId(), -5, "tetua.jabatan", EVENT);
			}
		}
	}

	private static void onRite(MinecraftServer s, UUID owner, int kind, List<UUID> attendees) {
		if (!active() || attendees == null || kind < 1) {
			return;
		}
		int n = Math.min(12, attendees.size());
		int delta = kind == 2 ? 3 : 1;
		String reason = kind == 2 ? "paviliun.duka" : "paviliun.qingming";
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				change(owner, attendees.get(i), attendees.get(j), delta, reason, CAP);
			}
		}
	}

	// ================================================================== disciple tick (every 20 ticks per body)

	private static void tick20(DiscipleEntity entity) {
		if (!active() || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		if (entity.activity() == SekteIds.ACT_SOCIAL && !GiliranSosial.inSession(entity)) {
			// a tea or rite that a restart cut off: the saved activity would hold the spot for ever
			entity.setActivity(0, null);
		}
		if (SEEN.containsKey(entity)) {
			return;
		}
		SEEN.put(entity, Boolean.TRUE);
		UUID owner = entity.ownerId();
		if (owner == null || !TabelPasangan.available(owner)) {
			return;
		}
		TabelPasangan.rememberName(owner, entity.soulId(), entity.getName());
		if (TabelPasangan.isParked(owner, entity.soulId()) && TabelPasangan.restore(owner, entity.soulId())) {
			SekteLog.log(server, owner, SekteIds.R, Component.translatable("kultivator.sekte.hubungan.log.kembali", entity.getName()));
			EfekIkatan.invalidate(entity.soulId());
		}
	}

	// ================================================================== the daily round (GiliranSosial, once per day per master)

	/** Once per in-game day for an online master: expires requests, parks the fallen, asks for honour duels. */
	static void daily(ServerPlayer master) {
		UUID owner = master.getUUID();
		long today = today();
		TabelPasangan.expireRequests(owner, today, 2);
		// souls no longer living in the master's list (released, fallen) are parked; a revived soul brings them back
		Set<UUID> living = new java.util.HashSet<>();
		Set<UUID> listed = new java.util.HashSet<>();
		ListTag list = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list");
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry) {
				try {
					UUID soul = UUID.fromString(entry.getStringOr("id", ""));
					listed.add(soul);
					if (!entry.getBooleanOr("dead", false)) {
						living.add(soul);
					}
				} catch (IllegalArgumentException ignored) {
					// not a soul entry
				}
			}
		}
		if (!listed.isEmpty()) {
			Set<UUID> gone = new java.util.HashSet<>();
			for (TabelPasangan.Pair pair : TabelPasangan.all(owner)) {
				if (!living.contains(pair.a)) {
					gone.add(pair.a);
				}
				if (!living.contains(pair.b)) {
					gone.add(pair.b);
				}
			}
			for (UUID soul : gone) {
				TabelPasangan.park(owner, soul);
				EfekIkatan.invalidate(soul);
			}
		}
		DuelKehormatan.dailyRoll(master, today);
	}

	// ================================================================== the disciple scroll (C36.R3)

	private static void info(DiscipleEntity entity, ServerPlayer master, CompoundTag tag) {
		CompoundTag out = new CompoundTag();
		UUID owner = entity.ownerId();
		boolean hall = owner != null && TabelPasangan.available(owner);
		out.putBoolean("on", active());
		out.putBoolean("hall", hall);
		out.putBoolean("own", master.getUUID().equals(owner));
		if (active() && hall) {
			UUID soul = entity.soulId();
			out.putString("self", soul.toString());
			out.putInt("bal", balance(owner, soul));
			Map<UUID, DiscipleEntity> bodies = loaded(owner);
			List<TabelPasangan.Pair> pairs = new ArrayList<>(TabelPasangan.of(owner, soul));
			pairs.sort(Comparator.comparingInt((TabelPasangan.Pair p) -> -Kecocokan.effectRank(p.kind()) * 1000 - Math.abs(p.affinity())));
			ListTag ties = new ListTag();
			for (TabelPasangan.Pair pair : pairs) {
				if (ties.size() >= 16) {
					break;
				}
				if (!pair.known()) {
					continue;
				}
				UUID other = pair.other(soul);
				CompoundTag tie = new CompoundTag();
				tie.putString("s", other.toString());
				tie.store("n", ComponentSerialization.CODEC, TabelPasangan.name(owner, other));
				tie.putString("k", pair.kind().id());
				tie.putInt("a", pair.affinity());
				tie.putInt("m", pair.meetings());
				tie.putInt("t", pair.tournamentBouts());
				tie.putBoolean("ban", pair.banned());
				DiscipleEntity body = bodies.get(other);
				tie.putBoolean("near", body != null && body.level() == entity.level() && body.distanceTo(entity) < 48.0F);
				ListTag history = new ListTag();
				for (CompoundTag event : pair.history()) {
					history.add(event.copy());
				}
				tie.put("h", history);
				tie.putInt("act", Upacara.actions(owner, pair, entity, body));
				ties.add(tie);
			}
			out.put("ties", ties);
		}
		tag.put(INFO_KEY, out);
	}

	// ================================================================== client actions (HubunganPayloads.Aksi)

	static void onAction(ServerPlayer player, HubunganPayloads.Aksi aksi) {
		if (player == null || aksi == null || !active()) {
			return;
		}
		UUID owner = player.getUUID();
		String op = aksi.op();
		if ("jaring".equals(op)) {
			sendJaring(player);
			return;
		}
		if ("terima".equals(op) || "tolak".equals(op)) {
			Upacara.answer(player, aksi.n(), "terima".equals(op));
			return;
		}
		UUID a = soul(aksi.a());
		UUID b = soul(aksi.b());
		if (a == null || b == null || a.equals(b)) {
			return;
		}
		switch (op) {
			case "sumpah" -> Upacara.oathFromScroll(player, a, b);
			case "restui" -> Upacara.bless(player, a, b);
			case "larang" -> Upacara.forbid(player, a, b);
			case "duel" -> DuelKehormatan.fromScroll(player, a, b);
			default -> {
				return;
			}
		}
		DiscipleEntity body = loaded(owner).get(a);
		if (body != null && body.level() == player.level() && body.distanceTo(player) <= Disciples.VIEW_RANGE) {
			Disciples.sendInfo(player, body);
		}
	}

	static @Nullable UUID soul(@Nullable String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** The Jaring Hubungan of the player's own hall: nodes (souls) and edges (known pairs). */
	static void sendJaring(ServerPlayer player) {
		HubunganPayloads.send(player, new HubunganPayloads.Jaring(jaring(player.getUUID())));
	}

	static CompoundTag jaring(UUID owner) {
		CompoundTag out = new CompoundTag();
		out.putBoolean("hall", TabelPasangan.available(owner));
		out.putBoolean("on", active());
		if (!TabelPasangan.available(owner)) {
			return out;
		}
		Map<UUID, DiscipleEntity> bodies = loaded(owner);
		List<UUID> souls = new ArrayList<>(bodies.keySet());
		List<TabelPasangan.Pair> pairs = TabelPasangan.all(owner);
		for (TabelPasangan.Pair pair : pairs) {
			if (pair.known()) {
				if (!souls.contains(pair.a)) {
					souls.add(pair.a);
				}
				if (!souls.contains(pair.b)) {
					souls.add(pair.b);
				}
			}
		}
		if (souls.size() > 16) {
			souls = new ArrayList<>(souls.subList(0, 16));
		}
		souls.sort(Comparator.comparing(UUID::toString));
		ListTag nodes = new ListTag();
		for (UUID soul : souls) {
			CompoundTag node = new CompoundTag();
			node.putString("s", soul.toString());
			node.store("n", ComponentSerialization.CODEC, TabelPasangan.name(owner, soul));
			DiscipleEntity body = bodies.get(soul);
			node.putBoolean("here", body != null);
			if (body != null) {
				DiscipleData data = body.data();
				node.putInt("r", data.realm().ordinal());
				node.putInt("d", data.daoIndex());
				node.putInt("c", data.rootColor());
				node.putInt("rank", SectRanks.rank(data));
			}
			node.putInt("bal", balance(owner, soul));
			nodes.add(node);
		}
		ListTag edges = new ListTag();
		for (TabelPasangan.Pair pair : pairs) {
			int i = souls.indexOf(pair.a);
			int j = souls.indexOf(pair.b);
			if (i < 0 || j < 0 || !pair.known()) {
				continue;
			}
			CompoundTag edge = new CompoundTag();
			edge.putInt("i", i);
			edge.putInt("j", j);
			edge.putString("k", pair.kind().id());
			edge.putInt("a", pair.affinity());
			edge.putInt("m", pair.meetings());
			edges.add(edge);
		}
		out.put("nodes", nodes);
		out.put("edges", edges);
		return out;
	}

	private Hubungan() {
	}
}
