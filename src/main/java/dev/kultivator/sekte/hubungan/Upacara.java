package dev.kultivator.sekte.hubungan;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * The master's part in the ties of his disciples (DESIGN 36.2.2): the requests R puts before him and the two rites.
 * <ul>
 * <li><b>Sumpah Saudara Angkat.</b> Friends with affinity 75+ ask to swear brotherhood (once a week at most). Approved
 * (chat chip, or the Hubungan tab), the two take seats before the hall stele, raise their cups and swear; they become
 * Saudara Angkat (scene beat 110).</li>
 * <li><b>Restu Pasangan Dao.</b> Lovers with affinity 90+, both Golden Core or higher, ask for the master's blessing.
 * Blessed, they drink the joined wedding cups before the stele and become Dao companions (scene beat 111); forbidden
 * ({@code Melarang}), both are punished ({@code Perlakuan.DIHUKUM}) and their love is set back to friendship for good.</li>
 * </ul>
 * Sumber: the oath in the peach garden, "not born on the same day, we only wish to die on the same day" (<i>Romance of
 * the Three Kingdoms</i> ch. 1); the bride and groom drink from the two halves of one gourd, "he jin" (<i>Liji</i>,
 * Hunyi); Wang Lin and Li Muwan (<i>Renegade Immortal</i>).
 */
final class Upacara {

	static final String OATH = "sumpah";
	static final String BLESS = "restu";
	static final String DUEL = "duel";

	/** Bits of the scroll's {@code act}: what the master can do for a tie now. */
	static final int ACT_OATH = 1;
	static final int ACT_BLESS = 2;
	static final int ACT_FORBID = 4;
	static final int ACT_DUEL = 8;
	static final int ACT_PENDING = 16;
	static final int ACT_AWAY = 32;

	private static final int PROPOSE_EVERY_DAYS = 7;

	static final int BEAT_OATH = EpicIds.EXTRA_SEKTE_FIRST + 10;
	static final int BEAT_WEDDING = EpicIds.EXTRA_SEKTE_FIRST + 11;
	static final int BEAT_SETTLED = EpicIds.EXTRA_SEKTE_FIRST + 12;

	// ================================================================== requests

	/** After every change: friends close enough ask for the oath, lovers close enough ask for the blessing. */
	static void maybePropose(UUID owner, TabelPasangan.Pair pair, @Nullable DiscipleData da, @Nullable DiscipleData db) {
		long today = Hubungan.today();
		if (today - pair.proposedDay() < PROPOSE_EVERY_DAYS || pending(owner, pair) != null) {
			return;
		}
		String type = null;
		if (pair.kind() == Ikatan.SAHABAT && pair.affinity() >= Kecocokan.OATH && da != null && db != null) {
			type = OATH;
		} else if (pair.kind() == Ikatan.KEKASIH && pair.affinity() >= Kecocokan.DAO && !pair.banned() && Kecocokan.daoRealm(da, db)) {
			type = BLESS;
		}
		if (type == null) {
			return;
		}
		pair.setProposedDay(today);
		int id = TabelPasangan.addRequest(owner, type, pair.a, pair.b, today);
		announce(owner, id, type, pair.a, pair.b);
	}

	static @Nullable TabelPasangan.Request pending(UUID owner, TabelPasangan.Pair pair) {
		for (TabelPasangan.Request request : TabelPasangan.requests(owner)) {
			if (TabelPasangan.key(request.a(), request.b()).equals(pair.key)) {
				return request;
			}
		}
		return null;
	}

	/** Tells the master (if online) about a request, with "yes" and "no" chips. */
	static void announce(UUID owner, int id, String type, UUID a, UUID b) {
		MinecraftServer server = Hubungan.server();
		ServerPlayer master = server == null || id < 0 ? null : server.getPlayerList().getPlayer(owner);
		if (master == null) {
			return;
		}
		Component na = TabelPasangan.name(owner, a);
		Component nb = TabelPasangan.name(owner, b);
		String base = "kultivator.sekte.hubungan.minta." + type;
		MutableComponent yes = Component.translatable(base + ".ya").withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi perguruan hubungan jawab " + id + " ya"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(base + ".ya.tip"))));
		MutableComponent no = Component.translatable(base + ".tidak").withStyle(style -> style.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi perguruan hubungan jawab " + id + " tidak"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(base + ".tidak.tip"))));
		int color = OATH.equals(type) ? 0xF2C66A : BLESS.equals(type) ? 0xFF9CC4 : 0xE0584A;
		Msg.chat(master, Component.translatable(base, na, nb, yes, no).withColor(color));
	}

	/** What the master can do for the tie ({@code ACT_*} bits) as the scroll of {@code entity} shows it. */
	static int actions(UUID owner, TabelPasangan.Pair pair, DiscipleEntity entity, @Nullable DiscipleEntity other) {
		int bits = 0;
		Ikatan kind = pair.kind();
		if (kind == Ikatan.SAHABAT && pair.affinity() >= Kecocokan.OATH) {
			bits |= ACT_OATH;
		}
		if (kind == Ikatan.KEKASIH) {
			bits |= ACT_FORBID;
			if (pair.affinity() >= Kecocokan.DAO && !pair.banned() && Kecocokan.daoRealm(entity.data(), other == null ? null : other.data())) {
				bits |= ACT_BLESS;
			}
		}
		if (kind == Ikatan.DENDAM && HubunganConfig.get().dendamAktif) {
			bits |= ACT_DUEL;
		}
		if (pending(owner, pair) != null) {
			bits |= ACT_PENDING;
		}
		if (other == null || other.level() != entity.level() || other.distanceTo(entity) > 64.0F) {
			bits |= ACT_AWAY;
		}
		return bits;
	}

	/** A chat chip ({@code /kultivasi perguruan hubungan jawab <id> ya|tidak}). */
	static void answer(ServerPlayer master, int id, boolean yes) {
		UUID owner = master.getUUID();
		TabelPasangan.Request request = TabelPasangan.request(owner, id);
		if (request == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.minta.kadaluarsa").withColor(0xA0A0A0));
			return;
		}
		TabelPasangan.removeRequest(owner, id);
		switch (request.type()) {
			case OATH -> {
				if (yes) {
					startOath(master, request.a(), request.b());
				} else {
					declined(master, request, "kultivator.sekte.hubungan.minta.sumpah.ditolak");
				}
			}
			case BLESS -> {
				if (yes) {
					bless(master, request.a(), request.b());
				} else {
					forbid(master, request.a(), request.b());
				}
			}
			case DUEL -> DuelKehormatan.answer(master, request, yes);
			default -> {
			}
		}
	}

	private static void declined(ServerPlayer master, TabelPasangan.Request request, String key) {
		UUID owner = master.getUUID();
		Msg.chat(master, Component.translatable(key, TabelPasangan.name(owner, request.a()), TabelPasangan.name(owner, request.b()))
				.withColor(0xA0A0A0));
	}

	// ================================================================== the oath

	static void oathFromScroll(ServerPlayer master, UUID a, UUID b) {
		TabelPasangan.Request open = null;
		TabelPasangan.Pair pair = TabelPasangan.find(master.getUUID(), a, b);
		if (pair != null) {
			open = pending(master.getUUID(), pair);
		}
		if (open != null) {
			TabelPasangan.removeRequest(master.getUUID(), open.id());
		}
		startOath(master, a, b);
	}

	static void startOath(ServerPlayer master, UUID a, UUID b) {
		UUID owner = master.getUUID();
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
		if (pair == null || pair.kind() != Ikatan.SAHABAT || pair.affinity() < Kecocokan.OATH) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.belum_sumpah").withColor(0xE0A060));
			return;
		}
		begin(master, GiliranSosial.OATH, a, b, "kultivator.sekte.hubungan.upacara.sumpah.mulai");
	}

	static void completeOath(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(owner, a.soulId(), b.soulId());
		if (pair == null || pair.kind() != Ikatan.SAHABAT && pair.kind() != Ikatan.SAUDARA_ANGKAT) {
			return;
		}
		pair.setAffinity(pair.affinity() + 5);
		pair.pushHistory(SekteIds.R + ".sumpah", 5, Hubungan.today());
		if (pair.kind() != Ikatan.SAUDARA_ANGKAT) {
			Hubungan.setKind(owner, pair, pair.kind(), Ikatan.SAUDARA_ANGKAT, a, b, false);
		}
		VoiceDisciples.line(a, "loyal", 600);
		VoiceDisciples.line(b, "loyal", 600);
		scene(owner, a, b, BEAT_OATH);
	}

	// ================================================================== the blessing

	static void bless(ServerPlayer master, UUID a, UUID b) {
		UUID owner = master.getUUID();
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
		if (pair == null || pair.kind() != Ikatan.KEKASIH || pair.affinity() < Kecocokan.DAO || pair.banned()) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.belum_restu").withColor(0xE0A060));
			return;
		}
		Map<UUID, DiscipleEntity> bodies = Hubungan.loaded(owner);
		DiscipleEntity ea = bodies.get(a);
		DiscipleEntity eb = bodies.get(b);
		if (ea != null && eb != null && !Kecocokan.daoRealm(ea.data(), eb.data())) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.ranah_dao").withColor(0xE0A060));
			return;
		}
		begin(master, GiliranSosial.WEDDING, a, b, "kultivator.sekte.hubungan.upacara.restu.mulai");
	}

	static void completeWedding(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(owner, a.soulId(), b.soulId());
		if (pair == null || pair.kind() != Ikatan.KEKASIH && pair.kind() != Ikatan.PASANGAN_DAO) {
			return;
		}
		pair.setAffinity(pair.affinity() + 5);
		pair.pushHistory(SekteIds.R + ".restu", 5, Hubungan.today());
		if (pair.kind() != Ikatan.PASANGAN_DAO) {
			Hubungan.setKind(owner, pair, pair.kind(), Ikatan.PASANGAN_DAO, a, b, false);
		}
		Hubungan.markDao(a, b.soulId());
		Hubungan.markDao(b, a.soulId());
		MinecraftServer server = Hubungan.server();
		SekteBus.honored(server, owner, a.soulId(), SekteIds.R + ".restu", 4);
		SekteBus.honored(server, owner, b.soulId(), SekteIds.R + ".restu", 4);
		VoiceDisciples.line(a, "thanks", 600);
		VoiceDisciples.line(b, "thanks", 600);
		scene(owner, a, b, BEAT_WEDDING);
	}

	/** Melarang: the love ends for good (back to friendship) and both are punished. */
	static void forbid(ServerPlayer master, UUID a, UUID b) {
		UUID owner = master.getUUID();
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a, b);
		if (pair == null || pair.kind() != Ikatan.KEKASIH) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.bukan_kekasih").withColor(0xE0A060));
			return;
		}
		pair.setBanned(true);
		Hubungan.change(owner, a, b, -10, SekteIds.R + ".larang", Hubungan.MEMORY | Hubungan.QUIET);
		Map<UUID, DiscipleEntity> bodies = Hubungan.loaded(owner);
		DiscipleEntity ea = bodies.get(a);
		DiscipleEntity eb = bodies.get(b);
		if (pair.kind() == Ikatan.KEKASIH) {
			Hubungan.setKind(owner, pair, Ikatan.KEKASIH, pair.affinity() >= Kecocokan.FRIEND_KEEP ? Ikatan.SAHABAT : Ikatan.KENALAN, ea, eb,
					true);
		}
		MinecraftServer server = Hubungan.server();
		SekteBus.mistreated(server, owner, a, Perlakuan.DIHUKUM, SekteIds.R + ".larang");
		SekteBus.mistreated(server, owner, b, Perlakuan.DIHUKUM, SekteIds.R + ".larang");
		HubunganPayloads.bubble(ea, eb, HubunganPayloads.ICON_PATAH, 120);
		Component line = Component.translatable("kultivator.sekte.hubungan.larang.selesai", TabelPasangan.name(owner, a), TabelPasangan.name(owner, b));
		Msg.chat(master, line.copy().withColor(0xC08080));
		SekteLog.log(server, owner, SekteIds.R, line);
	}

	// ================================================================== shared

	/** Starts a rite with both bodies (they must be loaded near each other). */
	private static void begin(ServerPlayer master, int type, UUID a, UUID b, String startedKey) {
		UUID owner = master.getUUID();
		Map<UUID, DiscipleEntity> bodies = Hubungan.loaded(owner);
		DiscipleEntity ea = bodies.get(a);
		DiscipleEntity eb = bodies.get(b);
		if (ea == null || eb == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.tidak_dimuat").withColor(0xE0A060));
			return;
		}
		Component why = GiliranSosial.startRite(owner, type, ea, eb);
		if (why != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.alasan.gagal", why).withColor(0xE0A060));
			return;
		}
		Msg.chat(master, Component.translatable(startedKey, ea.getName(), eb.getName()).withColor(0xF2D58A));
	}

	/** The rite's scene for the master when near and safe (7b rules: short, skippable, never in danger). */
	static void scene(UUID owner, DiscipleEntity a, DiscipleEntity b, int beat) {
		MinecraftServer server = Hubungan.server();
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (master == null || !(a.level() instanceof ServerLevel level) || master.level() != level || master.distanceTo(a) > 48.0F
				|| !EpicMoments.safe(master, null)) {
			return;
		}
		Vec3 mid = a.position().add(b.position()).scale(0.5D);
		EpicMoments.send(level, EpicIds.EXTRA, beat, a, master, mid, level.getGameTime(), 0, 0, 0.0F, 3, 0);
	}

	private Upacara() {
	}
}
