package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * The built-in story conditions of DESIGN 38.S.1, registered into {@link Syarat} by {@code Cerita.init}. "The place" is
 * the town of the NPC being talked to, else the 38 place the player stands in, else the nearest capital with an arc
 * ({@link TempatDialog#situs}).
 * <pre>
 * nama_baik N            Nama Baik at the place >= N           pangkat N        capital Nama Baik rank >= N (38KS)
 * ranah R                realm ordinal (or id) >= R           pita N           realm band >= N
 * bendera ID             story flag set                         punya ITEM [N]   carries N (default 1)
 * nb N / nb_pita K       carries N / K x (band+1) Nilai Batu   hari D           world day >= D
 * quest ID K|aktif|selesai|gagal|baru|tahap STAGE              jam A B          hour of day in [A, B) (wraps)
 * sikap_sekte S N        standing with sect S (id, terdekat, lurus, iblis) >= N
 * aliran_terdekat A      alignment of the nearest NPC sect (0 lurus, 1 netral, 2 iblis)
 * takut N                fear meter of the place >= N (38.R.5)  buronan          wanted at the place (38KS)
 * acak P                 P percent chance                        watak W          the speaker's temperament (Tokoh.WATAK)
 * situs_dikenal KIND     knows a site whose kind starts with KIND
 * alur BAB               capital chapter >= BAB                  alur_dukung X    chapter-4 choice (ruyi|gu|netral)
 * alur_bab5 X            chapter-5 outcome (a|b|c)               alur_penguasa X  crowned ruler (ruyi|gu)
 * alur_dou               Dou was helped in chapter 2              bukti N          player's arc proofs >= N
 * makam DAO TIER         the remnant soul being talked to guards a tomb of that Dao and tier (1-3)
 * </pre>
 */
final class SyaratDasar {

	static void daftar() {
		Syarat.daftar("nama_baik", (p, a) -> {
			long town = TempatDialog.situs(p);
			return town != 0L && KotaData.get(p.level().getServer()).reputation(town, p.getUUID()) >= num(a, 0, 0);
		});
		Syarat.daftar("pangkat", (p, a) -> KotaBesarApi.pangkatNamaBaik(p, TempatDialog.situs(p)) >= num(a, 0, 0));
		Syarat.daftar("ranah", (p, a) -> Cultivators.get(p).realm().ordinal() >= realm(a));
		Syarat.daftar("pita", (p, a) -> Isian.pita(p) >= num(a, 0, 0));
		Syarat.daftar("bendera", (p, a) -> !a.isEmpty() && CeritaData.get(p.level().getServer()).flag(p.getUUID(), a.get(0)));
		Syarat.daftar("punya", (p, a) -> !a.isEmpty() && BarangCerita.hitung(p, a.get(0)) >= num(a, 1, 1));
		Syarat.daftar("nb", (p, a) -> NilaiBatu.total(p) >= num(a, 0, 0));
		Syarat.daftar("nb_pita", (p, a) -> NilaiBatu.total(p) >= num(a, 0, 0) * (Isian.pita(p) + 1L));
		Syarat.daftar("hari", (p, a) -> Kota.day(p.level().getServer()) >= num(a, 0, 0));
		Syarat.daftar("quest", SyaratDasar::quest);
		Syarat.daftar("jam", (p, a) -> {
			int hour = (int) ((Kota.timeOfDay(p.level().getServer()) / 1000L + 6L) % 24L);
			int from = (int) num(a, 0, 0);
			int to = (int) num(a, 1, 24);
			return from <= to ? hour >= from && hour < to : hour >= from || hour < to;
		});
		Syarat.daftar("sikap_sekte", (p, a) -> {
			String sect = sekte(p, a.isEmpty() ? "terdekat" : a.get(0));
			return sect != null && SekteApi.diplomasi().standing(p.getUUID(), sect) >= num(a, 1, 0);
		});
		Syarat.daftar("aliran_terdekat", (p, a) -> {
			String sect = SekteApi.diplomasi().nearest(p.blockPosition(), -1);
			return sect != null && SekteApi.diplomasi().aliran(sect) == num(a, 0, -9);
		});
		Syarat.daftar("takut", (p, a) -> PerbaikanApi.takut(TempatDialog.situs(p)) >= num(a, 0, 0));
		Syarat.daftar("buronan", (p, a) -> KotaBesarApi.buronan(p, TempatDialog.situs(p)));
		Syarat.daftar("acak", (p, a) -> p.getRandom().nextInt(100) < num(a, 0, 50));
		Syarat.daftar("watak", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			return npc != null && !a.isEmpty() && Tokoh.WATAK.get(Tokoh.watak(npc)).equals(a.get(0));
		});
		Syarat.daftar("situs_dikenal", (p, a) -> {
			String kind = a.isEmpty() ? "" : a.get(0);
			for (DuniaSites.Site site : DuniaSites.known(p)) {
				if (site.kind().startsWith(kind)) {
					return true;
				}
			}
			return false;
		});
		Syarat.daftar("alur", (p, a) -> AlurTakhta.bab(p.level().getServer(), TempatDialog.situsAlur(p)) >= num(a, 0, 1));
		Syarat.daftar("alur_dukung", (p, a) -> !a.isEmpty() && a.get(0).equals(AlurTakhta.nilai(p.level().getServer(), TempatDialog.situsAlur(p), "dukung")));
		Syarat.daftar("alur_bab5", (p, a) -> !a.isEmpty() && a.get(0).equals(AlurTakhta.nilai(p.level().getServer(), TempatDialog.situsAlur(p), "bab5")));
		Syarat.daftar("alur_penguasa", (p, a) -> !a.isEmpty() && a.get(0).equals(AlurTakhta.penguasa(p.level().getServer(), TempatDialog.situsAlur(p))));
		Syarat.daftar("alur_dou", (p, a) -> AlurTakhta.dou(p.level().getServer(), TempatDialog.situsAlur(p)));
		Syarat.daftar("bukti", (p, a) -> AlurTakhta.bukti(p, TempatDialog.situsAlur(p)) >= num(a, 0, 1));
		Syarat.daftar("makam", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			return npc != null && a.size() >= 2 && a.get(0).equals(makamDao(npc)) && num(a, 1, 0) == makamTier(npc);
		});
	}

	/**
	 * The Dao of the inheritance tomb a remnant soul guards: {@code memory "dao"} written by 38GU when it spawns the body
	 * (C38.GU: {@code npc.memory().putString("dao", dao.id()); putInt("tier", 1..3)}), else a stable pick from its look.
	 */
	static String makamDao(KultivatorNpc npc) {
		String dao = npc.memory().getStringOr("dao", "");
		if (Dao.byId(dao) != null) {
			return dao;
		}
		Dao[] all = Dao.values();
		return all[Math.floorMod(npc.look() * 0x9E3779B1, all.length)].id();
	}

	/** The tomb tier 1-3 of a remnant soul ({@code memory "tier"}, else from its look). */
	static int makamTier(KultivatorNpc npc) {
		int tier = npc.memory().getIntOr("tier", 0);
		return tier >= 1 && tier <= 3 ? tier : 1 + Math.floorMod(npc.look() >>> 3, 3);
	}

	private static boolean quest(ServerPlayer p, List<String> a) {
		if (a.isEmpty()) {
			return false;
		}
		int stage = QuestTracker.tahap(p, a.get(0));
		String what = a.size() > 1 ? a.get(1).toLowerCase(Locale.ROOT) : "aktif";
		return switch (what) {
			case "aktif" -> stage > 0 && stage < TempatEvents.QUEST_SELESAI;
			case "selesai" -> stage == TempatEvents.QUEST_SELESAI;
			case "gagal" -> stage == TempatEvents.QUEST_GAGAL;
			case "baru" -> stage == 0;
			case "tahap" -> a.size() > 2 && a.get(2).equals(QuestTracker.simpul(p, a.get(0)));
			case "hasil" -> a.size() > 2 && a.get(2).equals(QuestTracker.hasil(p, a.get(0)));
			default -> stage >= num(a, 1, 1) && stage != TempatEvents.QUEST_GAGAL;
		};
	}

	/** A sect id from a word: an id, {@code terdekat}, {@code lurus}, {@code netral}, {@code iblis}. */
	static String sekte(ServerPlayer p, String word) {
		var service = SekteApi.diplomasi();
		return switch (word) {
			case "terdekat" -> service.nearest(p.blockPosition(), -1);
			case "lurus" -> service.nearest(p.blockPosition(), 0);
			case "netral" -> service.nearest(p.blockPosition(), 1);
			case "iblis" -> service.nearest(p.blockPosition(), 2);
			default -> service.sects().contains(word) ? word : null;
		};
	}

	static long num(List<String> a, int index, long fallback) {
		if (index >= a.size()) {
			return fallback;
		}
		try {
			return Long.parseLong(a.get(index));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int realm(List<String> a) {
		if (a.isEmpty()) {
			return 0;
		}
		String word = a.get(0).toLowerCase(Locale.ROOT);
		for (Realm r : Realm.values()) {
			if (r.name().toLowerCase(Locale.ROOT).equals(word)) {
				return r.ordinal();
			}
		}
		return (int) num(a, 0, 0);
	}

	private SyaratDasar() {
	}
}
