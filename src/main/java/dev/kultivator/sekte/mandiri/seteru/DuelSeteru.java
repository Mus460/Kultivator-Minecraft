package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The honour duel between feuding disciples (DESIGN 39.F.3 T2, 39.F.5). When the ladder reaches T2 a duel is set for the
 * next half day at the feud's place; it is fought live as soon as a player is near (the fist-and-palm salute 58, then
 * the Duel Pedang of DESIGN 35 through {@code DuelChallenges.startDirect}: its own intro and ring, the camera obeying
 * Penuh/Singkat/Mati, stopped at 10 %, no stake; the client adds the 2 s title card of 39F; townsfolk gather through
 * C39.K11 {@code HidupApi.kerumunan}), otherwise it is rolled as data when its time is up:
 * {@code 0.5 + 0.12 (P ours - P theirs)}, +-0.05 for brave or proud. With the master's support the master stands as
 * the challenger ({@code PAIR_TONTON}) and the disciple fights braver. The live result is read every second from
 * {@code DuelSessions.byEntity} (the pattern of 36 R): the side with more health left won. Nobody dies.
 */
final class DuelSeteru {

	static final String JENIS = "duel";
	private static final int TUNDA = 12_000;
	private static final int START_GRACE = 200;
	private static final int MAX_TICKS = 20 * 60 * 5;

	private static final class Tonton {
		final String seteru;
		final UUID owner;
		final DiscipleEntity kita;
		final DiscipleEntity mereka;
		final long mulai;
		final long situsKerumunan;
		final ServerLevel level;
		@Nullable DuelSession session;
		boolean bertarung;

		Tonton(String seteru, UUID owner, DiscipleEntity kita, DiscipleEntity mereka, long mulai, long situs, ServerLevel level) {
			this.seteru = seteru;
			this.owner = owner;
			this.kita = kita;
			this.mereka = mereka;
			this.mulai = mulai;
			this.situsKerumunan = situs;
			this.level = level;
		}
	}

	private static final List<Tonton> TONTON = new ArrayList<>();

	/** T2: the duel is set for the next half day at the feud's place (our real disciple salutes now if it is home). */
	static void jadwalkan(MinecraftServer server, Perseteruan f) {
		CompoundTag a = new CompoundTag();
		a.putString("jenis", JENIS);
		a.putLong("sampai", server.overworld().getGameTime() + TUNDA);
		f.adegan = a;
		UUID soul = f.jiwaUtama();
		DiscipleEntity real = soul == null ? null : Antrean.loaded(f.owner, soul);
		if (real != null) {
			PelaksanaSeteru.mulai(real, Kegiatan.TANTANG, 120, real.blockPosition(), null);
		}
	}

	/** True while this feud's duel is being fought. */
	static boolean berlangsung(String seteru) {
		for (Tonton t : TONTON) {
			if (t.seteru.equals(seteru)) {
				return true;
			}
		}
		return false;
	}

	/** The master's support (39.F.4 "Dukung dalam duel"): the master becomes the challenger, the duel starts now if near. */
	static @Nullable Component dukung(MinecraftServer server, Perseteruan f, ServerPlayer master) {
		if (!JENIS.equals(f.adegan.getStringOr("jenis", ""))) {
			return Teks.t("alasan.tanpa_duel");
		}
		f.adegan.putBoolean("dukung", true);
		Adegan.Pemeran p = Adegan.pemeran(server, f, true, 3600);
		if (p != null && p.kita() != null && p.mereka() != null && master.level() == p.level()
				&& master.blockPosition().distSqr(p.pos()) <= 64.0D * 64.0D) {
			mulai(server, f, p, master);
		}
		return null;
	}

	/** Every 40 ticks for each feud with a duel set: fight it live when seen, roll it when its time is up. */
	static void periksa(MinecraftServer server, Perseteruan f) {
		if (berlangsung(f.id)) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (DuelSessions.room()) {
			Adegan.Pemeran p = Adegan.pemeran(server, f, true, 3600);
			if (p != null && p.kita() != null && p.mereka() != null) {
				ServerPlayer master = server.getPlayerList().getPlayer(f.owner);
				boolean withMaster = f.adegan.getBooleanOr("dukung", false) && master != null && master.level() == p.level()
						&& master.blockPosition().distSqr(p.pos()) <= 64.0D * 64.0D;
				mulai(server, f, p, withMaster ? master : null);
				return;
			}
		}
		if (now >= f.adegan.getLongOr("sampai", now)) {
			undian(server, f);
		}
	}

	/** The duel as data (39.F.5). */
	static void undian(MinecraftServer server, Perseteruan f) {
		boolean dukung = f.adegan.getBooleanOr("dukung", false);
		f.adegan = new CompoundTag();
		long today = Ingatan.today(server);
		UUID soul = f.jiwaUtama();
		MuridInfo murid = soul == null ? null : MuridInfo.dari(f.owner, soul);
		TokohSekte tokoh = SeteruData.get(server).tokoh(f.tokohUtama());
		double ours = murid == null ? 1.5D : murid.kekuatan();
		double theirs = tokoh == null ? 1.5D : tokoh.kekuatan(today, SekteApi.diplomasi().tier(f.sekte));
		double chance = 0.5D + 0.12D * (ours - theirs);
		if (murid != null && (murid.punya(Sifat.BERANI) || murid.punya(Sifat.SOMBONG))) {
			chance += 0.05D;
		}
		if (tokoh != null && (tokoh.punya(Sifat.BERANI) || tokoh.punya(Sifat.SOMBONG))) {
			chance -= 0.05D;
		}
		if (dukung) {
			chance += 0.05D;
		}
		chance = Math.max(0.10D, Math.min(0.90D, chance));
		boolean win = Tangga.acak(f, today * 13L + 2L).nextDouble() < chance;
		Tangga.hasilDuel(server, f, win, false);
	}

	/** The live duel: the salute, the title card, the crowd, then the Duel Pedang. */
	private static void mulai(MinecraftServer server, Perseteruan f, Adegan.Pemeran p, @Nullable ServerPlayer challenger) {
		DiscipleEntity kita = p.kita();
		DiscipleEntity mereka = p.mereka();
		if (kita == null || mereka == null) {
			return;
		}
		f.adegan.putBoolean("hidup", true);
		Adegan.hadap(kita, mereka);
		Adegan.tandaiSibuk(kita, MAX_TICKS);
		if (p.kitaAsli()) {
			PelaksanaSeteru.mulai(kita, Kegiatan.TANTANG, 60, kita.blockPosition(), null);
		} else {
			Adegan.klip(kita, SeteruIds.KLIP_TANTANG, 50, 0, 0, 0);
		}
		Adegan.klip(mereka, SeteruIds.KLIP_TANTANG, 50, SeteruIds.PROP_SURAT_TANTANGAN, 0, 0);
		Naskah.ucap(mereka, SeteruIds.V_TANTANG, Naskah.baris(f, "duel.tantang"));
		Adegan.nanti(server, 30, () -> Naskah.ucap(kita, SeteruIds.V_TERIMA, Naskah.baris(f, "duel.terima")));
		SeteruPayloads.judul(p.level(), p.pos(), f, kita.getName(), mereka.getName());
		long situs = 0L;
		try {
			situs = HidupApi.kerumunan(p.level(), p.pos(), 150);
		} catch (RuntimeException exception) {
			situs = 0L;
		}
		if (situs == 0L) {
			// without 38's townsfolk: the crowd's murmur (and the old residents who are near turn their heads)
			p.level().playSound(null, p.pos(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 0.8F);
			Adegan.wargaMenoleh(p.level(), mereka);
		}
		long crowd = situs;
		Adegan.nanti(server, 45, () -> {
			if (kita.isRemoved() || mereka.isRemoved()) {
				undian(server, f);
				return;
			}
			int gap = kita.data().realm().ordinal() - mereka.data().realm().ordinal();
			DuelRuleSet rules = DuelRuleSet.suggested(gap).withoutStake().withCrowd(true).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN);
			Component why = DuelChallenges.startDirect(challenger, kita, mereka, challenger != null ? DuelIds.PAIR_TONTON : DuelIds.PAIR_NPC, rules);
			if (why != null) {
				if (crowd != 0L) {
					HidupApi.bubarkan(p.level(), crowd);
				}
				undian(server, f);
				return;
			}
			TONTON.add(new Tonton(f.id, f.owner, kita, mereka, server.getTickCount(), crowd, p.level()));
			Kabar.sebar(server, f, SeteruIds.KABAR_DUEL);
		});
	}

	/** Every second: reads the live duels (the 36 R pattern). */
	static void tick(MinecraftServer server) {
		if (TONTON.isEmpty() || server.getTickCount() % 20 != 7) {
			return;
		}
		long now = server.getTickCount();
		for (Tonton t : List.copyOf(TONTON)) {
			DuelSession session = DuelSessions.byEntity(t.kita);
			if (session != null) {
				t.session = session;
				if (session.phase().ordinal() >= DuelSession.Phase.FIGHT.ordinal()) {
					t.bertarung = true;
				}
				if (!session.ended() && now - t.mulai < MAX_TICKS) {
					continue;
				}
			} else if (t.session == null && now - t.mulai < START_GRACE) {
				continue;
			}
			TONTON.remove(t);
			selesai(server, t);
		}
	}

	private static void selesai(MinecraftServer server, Tonton t) {
		if (t.situsKerumunan != 0L) {
			HidupApi.bubarkan(t.level, t.situsKerumunan);
		}
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(t.owner);
		Perseteruan f = aula.byId(t.seteru);
		if (f == null) {
			return;
		}
		f.adegan = new CompoundTag();
		if (!t.bertarung) {
			undian(server, f);
			return;
		}
		boolean win = kesehatan(t.kita) >= kesehatan(t.mereka);
		DiscipleEntity winner = win ? t.kita : t.mereka;
		DiscipleEntity loser = win ? t.mereka : t.kita;
		Adegan.klip(winner, SeteruIds.KLIP_MENANG, 60, 0, 0, 0);
		Adegan.klip(loser, SeteruIds.KLIP_KALAH, 80, 0, 0, 0);
		Naskah.ucap(winner, SeteruIds.V_MENANG, Naskah.baris(f, "duel.menang"));
		Adegan.nanti(server, 40, () -> Naskah.ucap(loser, SeteruIds.V_KALAH, Naskah.baris(f, "duel.kalah")));
		Adegan.nanti(server, 160, () -> TubuhSeteru.hilangSeteru(f.id));
		if (!win && !t.kita.isRemoved()) {
			// 39.A wound writer: a home duel loser rests a day (never a death path, bolehGugur false)
			long now = t.level.getGameTime();
			MandiriApi.lukai(t.kita.data(), JenisLuka.TERLUKA, now, now + 24000L, "seteru");
		}
		Tangga.hasilDuel(server, f, win, true);
	}

	private static float kesehatan(DiscipleEntity body) {
		return body.isRemoved() || !body.isAlive() ? 0.0F : body.getHealth() / Math.max(1.0F, body.getMaxHealth());
	}

	static void bersihkan() {
		TONTON.clear();
	}

	/** The duel between the master and their Young Elder or Eldest Senior (39.F.4 "Tantang tetua mereka"). */
	static @Nullable Component tantangTetua(MinecraftServer server, Perseteruan f, ServerPlayer master) {
		TokohSekte elder = Roster.tetua(server, f.sekte);
		if (elder == null) {
			return Teks.t("alasan.tanpa_tetua");
		}
		if (!DuelSessions.room()) {
			return Component.translatable("kultivator.duel.reason.full");
		}
		ServerLevel level = master.level();
		BlockPos at = master.blockPosition().offset(5, 0, 0);
		DiscipleEntity body = TubuhSeteru.munculkan(level, at, elder, TubuhSeteru.Peran.DUEL, f.owner, f.id, MAX_TICKS + 1200);
		if (body == null) {
			return Teks.t("alasan.tubuh_penuh");
		}
		dev.kultivator.sosok.SosokApi.tatap(body, master);
		Adegan.klip(body, SeteruIds.KLIP_TANTANG, 50, 0, 0, 0);
		Naskah.ucap(body, SeteruIds.V_TANTANG, Naskah.baris(f, "tetua_tantang"));
		DuelRuleSet rules = DuelRuleSet.suggested(0).withoutStake().withCrowd(true).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN)
				.withRadius(Math.max(6, DuelRuleSet.defaults().radius()));
		Component why = DuelChallenges.startDirect(master, master, body, DuelIds.PAIR_NPC, rules);
		if (why != null) {
			TubuhSeteru.hilang(body);
			return why;
		}
		SeteruPayloads.judul(level, master.blockPosition(), f, master.getName(), body.getName());
		TETUA.add(new TantanganGuru(f.id, f.owner, master.getUUID(), body, server.getTickCount()));
		return null;
	}

	private record TantanganGuru(String seteru, UUID owner, UUID master, DiscipleEntity tetua, long mulai) {
	}

	private static final List<TantanganGuru> TETUA = new ArrayList<>();

	/** Every second: the master's duel with their elder (3 minutes at most, D's sect-master rules: 10 %). */
	static void tickTetua(MinecraftServer server) {
		if (TETUA.isEmpty() || server.getTickCount() % 20 != 11) {
			return;
		}
		for (TantanganGuru t : List.copyOf(TETUA)) {
			DuelSession session = DuelSessions.byEntity(t.tetua);
			boolean timeUp = server.getTickCount() - t.mulai > 20 * 60 * 3 + START_GRACE;
			if (session != null && !session.ended() && !timeUp) {
				continue;
			}
			if (session == null && server.getTickCount() - t.mulai < START_GRACE) {
				continue;
			}
			TETUA.remove(t);
			ServerPlayer master = server.getPlayerList().getPlayer(t.master);
			Perseteruan f = SeteruData.get(server).aula(t.owner).byId(t.seteru);
			boolean win = master != null && master.isAlive()
					&& master.getHealth() / Math.max(1.0F, master.getMaxHealth()) >= kesehatan(t.tetua);
			Adegan.nanti(server, 80, () -> TubuhSeteru.hilang(t.tetua));
			if (f != null) {
				CampurTangan.hasilTantangTetua(server, f, win);
			}
		}
	}

	private DuelSeteru() {
	}
}
