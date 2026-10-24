package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Friendship and love across the sect line (DESIGN 39.F.3). A member who likes one of our disciples ({@code sikap >= 60})
 * and has met it twice becomes a friend from outside (they help each other on the road and the friend refuses to brawl
 * against ours). A friend of the other sex, when our disciple's heart is set on a partner (goal JODOH) and the sects
 * stand cold or worse, becomes a <b>Kekasih Terlarang</b>: secret meetings at night markets and festivals (25 % a visit),
 * each with a 15 % chance of being found out (heat +20 on the feud with that sect, the master is told). It ends in
 * elopement (the member defects and arrives at the hall as a candidate; standing -10), a parting (a bittersweet memory
 * +7, the disciple turns to its cultivation), or, with the master's blessing and a standing of at least -10, the two
 * sects reconciled by them (standing +15, D's pact offered).
 */
final class AsmaraLintas {

	/** After a peace: members who now like our disciples become friends from outside. */
	static void cekSahabat(MinecraftServer server, Perseteruan f) {
		if (!SeteruConfig.get().asmaraLintas) {
			return;
		}
		SeteruData data = SeteruData.get(server);
		for (UUID soul : f.jiwa) {
			for (String id : f.tokoh) {
				TokohSekte t = data.tokoh(id);
				if (t != null && t.sikap(soul) >= 60 && t.jumpa >= 2) {
					jadiSahabat(server, f.owner, soul, t);
				}
			}
		}
	}

	/** A friendly meeting (39.F.2: no incident): attitude +15..+30; twice at 60+ makes a friend. */
	static void ramah(MinecraftServer server, UUID owner, MuridInfo murid, TokohSekte t, RandomSource rnd) {
		t.ubahSikap(murid.soul(), 15 + rnd.nextInt(16));
		if (SeteruConfig.get().asmaraLintas && t.sikap(murid.soul()) >= 60 && t.jumpa >= 2) {
			jadiSahabat(server, owner, murid.soul(), t);
		}
		SeteruData.get(server).setDirty();
	}

	static void jadiSahabat(MinecraftServer server, UUID owner, UUID soul, TokohSekte t) {
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		if (aula.sahabatDari(soul, t.id) != null) {
			return;
		}
		CompoundTag friend = new CompoundTag();
		friend.putString("soul", soul.toString());
		friend.putString("tokoh", t.id);
		friend.putLong("hari", Ingatan.today(server));
		aula.sahabat.add(friend);
		while (aula.sahabat.size() > SeteruData.SAHABAT_MAKS) {
			aula.sahabat.remove(0);
		}
		Antrean.ingat(server, owner, soul, SeteruIds.ING_SAHABAT, null, 5, SeteruIds.k("ingat.sahabat"), t.argNama());
		Tangga.harian(server, owner, soul, "seteru.sahabat", "harian.sahabat", EntriHarian.Suasana.SENANG, true, t.argNama(),
				Teks.argSekte(t.sekte));
		data.setDirty();
	}

	/** True when {@code soul} counts {@code t} as a friend from outside. */
	static boolean sahabat(MinecraftServer server, UUID owner, UUID soul, TokohSekte t) {
		SeteruData.Aula aula = SeteruData.get(server).peek(owner);
		return aula != null && aula.sahabatDari(soul, t.id) != null;
	}

	/** Can a friend become a forbidden love now: the other sex, the goal JODOH, the sects cold or worse, nobody yet. */
	static boolean bisaKekasih(MinecraftServer server, UUID owner, MuridInfo murid, TokohSekte t) {
		if (!SeteruConfig.get().asmaraLintas || murid.perempuan() == t.perempuan() || !murid.cita(JenisCita.JODOH)) {
			return false;
		}
		if (!t.kasih.isEmpty() && !t.kasih.equals(murid.soul().toString())) {
			return false;
		}
		Sikap sikap = SekteApi.diplomasi().stance(owner, t.sekte);
		if (sikap != Sikap.DINGIN && sikap != Sikap.BERMUSUHAN && sikap != Sikap.PERANG) {
			return false;
		}
		SeteruData.Aula aula = SeteruData.get(server).aula(owner);
		return aula.kekasihDari(murid.soul()) == null && aula.milik(murid.soul()) == null;
	}

	/** Starts the forbidden love ({@code uji ... asmara}, or a friend met again). The record shows on the screen. */
	static @Nullable Perseteruan mulai(MinecraftServer server, UUID owner, MuridInfo murid, TokohSekte t) {
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		if (aula.kekasihDari(murid.soul()) != null) {
			return null;
		}
		long today = Ingatan.today(server);
		Perseteruan f = new Perseteruan(data.idBaru(), owner, t.sekte, SeteruIds.SEBAB_ASMARA);
		f.status = SeteruIds.TERLARANG;
		f.tambahJiwa(murid.soul(), murid.nama());
		f.tambahTokoh(t.id);
		f.hariMulai = today;
		f.catat(today, "riwayat.kekasih", Teks.arg(murid.nama()), t.argNama(), Teks.argSekte(t.sekte));
		aula.seteru.add(f);
		CompoundTag love = new CompoundTag();
		love.putString("soul", murid.soul().toString());
		love.putString("tokoh", t.id);
		love.putString("seteru", f.id);
		love.putLong("mulai", today);
		love.putInt("rahasia", 0);
		love.putInt("ketahuan", 0);
		aula.kekasih.add(love);
		t.kasih = murid.soul().toString();
		t.ubahSikap(murid.soul(), 20);
		Antrean.ingat(server, owner, murid.soul(), SeteruIds.ING_KEKASIH, null, 6, SeteruIds.k("ingat.kekasih"), t.argNama());
		Tangga.harian(server, owner, murid.soul(), "seteru.kekasih", "harian.kekasih.mulai", EntriHarian.Suasana.SENANG, true, t.argNama(),
				Teks.argSekte(t.sekte));
		Tangga.prestasi(server, owner, MandiriIds.ADV_SETERU_KEKASIH);
		data.setDirty();
		return f;
	}

	/**
	 * A secret meeting at a night market or festival stage (25 %): a diary page; 15 % found out (heat +20 on that sect's
	 * feud, a new one when there is none, the master told). After four meetings the pair may elope (35 % a meeting).
	 * Returns the story line key of the stage, or null when nothing happened.
	 */
	static @Nullable String rahasia(MinecraftServer server, UUID owner, MuridInfo murid, BlockPos pos, RandomSource rnd) {
		SeteruConfig config = SeteruConfig.get();
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		CompoundTag love = aula.kekasihDari(murid.soul());
		if (love == null || rnd.nextDouble() >= config.kali(config.peluang.rahasia)) {
			return null;
		}
		TokohSekte t = data.tokoh(love.getStringOr("tokoh", ""));
		Perseteruan f = aula.byId(love.getStringOr("seteru", ""));
		if (t == null || f == null || !f.aktif()) {
			return null;
		}
		long today = Ingatan.today(server);
		int meetings = love.getIntOr("rahasia", 0) + 1;
		love.putInt("rahasia", meetings);
		t.dijumpai(today, "");
		f.catat(today, "riwayat.rahasia", Teks.arg(murid.nama()), t.argNama());
		Tangga.harian(server, owner, murid.soul(), "seteru.kekasih", "harian.kekasih.rahasia", EntriHarian.Suasana.SENANG, false,
				t.argNama());
		if (rnd.nextDouble() < config.peluang.ketahuan) {
			love.putInt("ketahuan", love.getIntOr("ketahuan", 0) + 1);
			f.catat(today, "riwayat.ketahuan", t.argNama(), Teks.argSekte(t.sekte));
			Perseteruan feud = aula.dengan(t.sekte);
			if (feud != null) {
				Tangga.panas(server, feud, 20, "riwayat.panas_ketahuan", t.argNama());
			} else {
				TokohSekte jealous = Roster.pilih(server, t.sekte, murid.soul(), today, rnd, true);
				if (jealous != null && jealous != t) {
					Perseteruan started = Tangga.mulai(server, owner, murid, jealous, SeteruIds.SEBAB_ASMARA, SeteruIds.SALAH_KITA, 20,
							0L, pos);
					if (started != null) {
						started.catat(today, "riwayat.ketahuan_mulai", t.argNama());
					}
				}
			}
			Tangga.beritahu(server, f, Teks.t("pesan.ketahuan", murid.nama(), t.namaTampil(), Teks.sekte(t.sekte)), true, true);
			Adegan.rahasia(server, f, pos, true);
			data.setDirty();
			return SeteruIds.k("kisah.ketahuan");
		}
		if (meetings >= 4 && rnd.nextDouble() < 0.35D) {
			kawinLari(server, f);
			return SeteruIds.k("kisah.kawin_lari");
		}
		Adegan.rahasia(server, f, pos, false);
		data.setDirty();
		return SeteruIds.k("kisah.rahasia");
	}

	/** (a) Elopement: the member defects and arrives at the hall as a candidate; the sect is angry (standing -10). */
	static void kawinLari(MinecraftServer server, Perseteruan f) {
		SeteruData data = SeteruData.get(server);
		TokohSekte t = data.tokoh(f.tokohUtama());
		UUID soul = f.jiwaUtama();
		if (t == null || soul == null) {
			return;
		}
		long today = Ingatan.today(server);
		t.status = SeteruIds.TOKOH_MEMBELOT;
		RandomSource rnd = RandomSource.create(t.benih * 31L + today);
		int step = t.langkah(today, SekteApi.diplomasi().tier(t.sekte));
		DiscipleData candidate = DiplomasiMandiri.dataTubuh(rnd, step);
		Halls.addCandidates(f.owner, List.of(new Halls.CandidateSpec(candidate, -1, -1.0D, -1.0D, true, "mandiri_seteru",
				SeteruIds.k("asal.kawin_lari"), 10)));
		DiplomasiMandiri.insiden(server, f.owner, f.sekte, -10, "kawin_lari");
		DiplomasiMandiri.pembelot(server, t.sekte, UUID.nameUUIDFromBytes(t.id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				t.namaTampil());
		Antrean.ingat(server, f.owner, soul, SeteruIds.ING_KAWIN_LARI, null, 9, SeteruIds.k("ingat.kawin_lari"), t.argNama());
		Tangga.harian(server, f.owner, soul, "seteru.kekasih", "harian.kekasih.kawin_lari", EntriHarian.Suasana.BANGGA, true, t.argNama(),
				Teks.argSekte(t.sekte));
		Kabar.sebar(server, f, SeteruIds.KABAR_KAWIN_LARI);
		Tangga.beritahu(server, f, Teks.t("pesan.kawin_lari", f.namaUtama(), t.namaTampil(), Teks.sekte(t.sekte)), true, false);
		selesai(server, f, SeteruIds.KAWIN_LARI, "akhir.kawin_lari");
	}

	/** (b) The master asks them to part: a bittersweet lasting memory, cultivation first. */
	static @Nullable Component pisahkan(MinecraftServer server, Perseteruan f) {
		if (!SeteruIds.TERLARANG.equals(f.status)) {
			return Teks.t("alasan.bukan_kekasih");
		}
		SeteruData data = SeteruData.get(server);
		TokohSekte t = data.tokoh(f.tokohUtama());
		UUID soul = f.jiwaUtama();
		if (soul != null) {
			Antrean.ingat(server, f.owner, soul, SeteruIds.ING_PISAH, null, 7, SeteruIds.k("ingat.pisah"), t == null ? "" : t.argNama());
			Tangga.harian(server, f.owner, soul, "seteru.kekasih", "harian.kekasih.pisah", EntriHarian.Suasana.SEDIH, true,
					t == null ? "" : t.argNama());
			DiscipleEntity body = Antrean.loaded(f.owner, soul);
			if (body != null) {
				MandiriApi.tambahCita(body, JenisCita.RANAH, null, 1, "mandiri.seteru.pisah");
			}
		}
		if (t != null) {
			t.kasih = "";
		}
		selesai(server, f, SeteruIds.BERPISAH, "akhir.pisah");
		return null;
	}

	/** (c) The master's blessing: with standing >= -10 the two sects make peace through them, else they elope. */
	static @Nullable Component restui(MinecraftServer server, ServerPlayer master, Perseteruan f) {
		if (!SeteruIds.TERLARANG.equals(f.status)) {
			return Teks.t("alasan.bukan_kekasih");
		}
		int standing = SekteApi.diplomasi().standing(f.owner, f.sekte);
		if (standing < -10) {
			kawinLari(server, f);
			return null;
		}
		DiplomasiMandiri.insiden(server, f.owner, f.sekte, 15, "restu");
		DiplomasiMandiri.tawarPakta(server, f.owner, f.sekte);
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(f.owner);
		Perseteruan feud = aula.dengan(f.sekte);
		if (feud != null && feud != f && SeteruIds.AKTIF.equals(feud.status)) {
			Tangga.akhiri(server, feud, SeteruIds.DAMAI, "akhir.restu");
		}
		UUID soul = f.jiwaUtama();
		TokohSekte t = data.tokoh(f.tokohUtama());
		if (soul != null) {
			Antrean.ingat(server, f.owner, soul, SeteruIds.ING_KEKASIH, master.getUUID(), 8, SeteruIds.k("ingat.restu"),
					t == null ? "" : t.argNama());
			Tangga.harian(server, f.owner, soul, "seteru.kekasih", "harian.kekasih.restu", EntriHarian.Suasana.BANGGA, true,
					t == null ? "" : t.argNama(), Teks.argSekte(f.sekte));
		}
		Tangga.prestasi(server, f.owner, MandiriIds.ADV_SETERU_DAMAI);
		selesai(server, f, SeteruIds.DAMAI, "akhir.restu");
		return null;
	}

	private static void selesai(MinecraftServer server, Perseteruan f, String status, String kunci) {
		long today = Ingatan.today(server);
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(f.owner);
		f.status = status;
		f.hariAkhir = today;
		f.catat(today, kunci, Teks.argSekte(f.sekte));
		UUID soul = f.jiwaUtama();
		if (soul != null) {
			CompoundTag love = aula.kekasihDari(soul);
			if (love != null) {
				aula.kekasih.remove(love);
			}
		}
		dev.kultivator.sekte.mandiri.api.MandiriBus.seteruAkhir(server, f.owner, f.id, status);
		aula.rapikan();
		data.setDirty();
	}

	private AsmaraLintas() {
	}
}
