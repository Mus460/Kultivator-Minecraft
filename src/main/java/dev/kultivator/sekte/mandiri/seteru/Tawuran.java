package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Sibuk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The brawl (DESIGN 39.F.3 T3, 39.F.5): two to four disciples a side, settled as at most two duel sessions fought at the
 * same time by the strongest pairs (one brawl on the server at a time, so never more than two sessions), the rest
 * cheering in the quarrel pose; pairs without bodies are rolled as data and the majority decides. Set for the next half
 * day like the duel; fought live when a player is near, else rolled. Near the master it counts as one of the day's
 * enemy events ({@code Sibuk.enemyTaken(guru, "seteru")}) and waits while the master is busy. Nobody dies.
 */
final class Tawuran {

	static final String JENIS = "tawuran";
	private static final int TUNDA = 12_000;
	private static final int MAX_TICKS = 20 * 60 * 5;

	private record Pasangan(DiscipleEntity kita, DiscipleEntity mereka) {
	}

	private static final class Ramai {
		final String seteru;
		final UUID owner;
		final List<Pasangan> pasangan;
		final int dataMenang;
		final int dataKalah;
		final long mulai;
		final List<Boolean> bertarung = new ArrayList<>();

		Ramai(String seteru, UUID owner, List<Pasangan> pasangan, int dataMenang, int dataKalah, long mulai) {
			this.seteru = seteru;
			this.owner = owner;
			this.pasangan = pasangan;
			this.dataMenang = dataMenang;
			this.dataKalah = dataKalah;
			this.mulai = mulai;
			for (int i = 0; i < pasangan.size(); i++) {
				bertarung.add(Boolean.FALSE);
			}
		}
	}

	private static @Nullable Ramai ramai;

	static void jadwalkan(MinecraftServer server, Perseteruan f) {
		CompoundTag a = new CompoundTag();
		a.putString("jenis", JENIS);
		a.putLong("sampai", server.overworld().getGameTime() + TUNDA);
		f.adegan = a;
		// the members of their sect who join (2-4 a side)
		RandomSource rnd = Tangga.acak(f, 77L);
		List<TokohSekte> them = Roster.aktif(server, f.sekte);
		int want = 2 + rnd.nextInt(3);
		for (TokohSekte t : them) {
			if (f.tokoh.size() >= Math.min(want, Perseteruan.JIWA_MAKS)) {
				break;
			}
			// a friend from that sect refuses to brawl against ours (39.F.3)
			boolean friend = false;
			for (UUID soul : f.jiwa) {
				if (AsmaraLintas.sahabat(server, f.owner, soul, t)) {
					friend = true;
				}
			}
			if (!friend && !t.luka(Ingatan.today(server)) && t.peran < TokohSekte.PERAN_TETUA) {
				f.tambahTokoh(t.id);
			}
		}
	}

	static boolean berlangsung(String seteru) {
		return ramai != null && ramai.seteru.equals(seteru);
	}

	/** Every 40 ticks while set: live when seen and no other brawl runs, else rolled when its time is up. */
	static void periksa(MinecraftServer server, Perseteruan f) {
		if (berlangsung(f.id)) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (ramai == null && DuelSessions.room()) {
			if (hidup(server, f)) {
				return;
			}
		}
		if (now >= f.adegan.getLongOr("sampai", now)) {
			f.adegan = new CompoundTag();
			Tangga.hasilTawuran(server, f, undian(server, f, f.jiwa.size(), f.tokoh.size()));
		}
	}

	/** The majority of {@code kitaN} vs {@code merekaN} fighters rolled from their strength. */
	private static boolean undian(MinecraftServer server, Perseteruan f, int kitaN, int merekaN) {
		long today = Ingatan.today(server);
		double ours = 0.0D;
		for (UUID soul : f.jiwa) {
			ours += MuridInfo.dari(f.owner, soul).kekuatan();
		}
		double theirs = 0.0D;
		int tier = SekteApi.diplomasi().tier(f.sekte);
		SeteruData data = SeteruData.get(server);
		for (String id : f.tokoh) {
			TokohSekte t = data.tokoh(id);
			theirs += t == null ? 1.5D : t.kekuatan(today, tier);
		}
		double avgOurs = ours / Math.max(1, f.jiwa.size());
		double avgTheirs = theirs / Math.max(1, f.tokoh.size());
		double chance = 0.5D + 0.12D * (avgOurs - avgTheirs) + 0.06D * (kitaN - merekaN);
		chance = Math.max(0.10D, Math.min(0.90D, chance));
		return Tangga.acak(f, today * 41L).nextDouble() < chance;
	}

	private static boolean hidup(MinecraftServer server, Perseteruan f) {
		if (Adegan.asliSibuk(f)) {
			return false;
		}
		Adegan.Pemeran p = Adegan.pemeran(server, f, true, MAX_TICKS + 1200);
		if (p == null || p.kita() == null || p.mereka() == null) {
			return false;
		}
		ServerLevel level = p.level();
		SeteruData data = SeteruData.get(server);
		List<Pasangan> pairs = new ArrayList<>();
		pairs.add(new Pasangan(p.kita(), p.mereka()));
		// a second pair: our second disciple's body and their second member's body, when both can be shown
		MuridInfo second = f.jiwa.size() > 1 && f.tokoh.size() > 1 ? MuridInfo.dari(f.owner, f.jiwa.get(1)) : null;
		// a busy real second disciple (breakthrough, the master's order...) is not pulled into the brawl
		if (second != null && (second.tubuh() == null || !dev.kultivator.util.SibukMurid.busy(second.tubuh()))) {
			DiscipleEntity ours = second.tubuh() != null ? second.tubuh() : second.jelmaan(level, p.kita().position());
			if (ours == null) {
				ours = TubuhSeteru.pengganti(level, p.pos().offset(-2, 0, 1), second, f.owner, f.id, MAX_TICKS + 1200);
			}
			TokohSekte t2 = data.tokoh(f.tokoh.get(1));
			DiscipleEntity theirs = t2 == null ? null : TubuhSeteru.munculkan(level, p.pos().offset(4, 0, -2), t2, TubuhSeteru.Peran.DUEL,
					f.owner, f.id, MAX_TICKS + 1200);
			if (ours != null && theirs != null) {
				pairs.add(new Pasangan(ours, theirs));
			}
		}
		ServerPlayer master = server.getPlayerList().getPlayer(f.owner);
		if (master != null && master.level() == level && master.blockPosition().distSqr(p.pos()) <= 64.0D * 64.0D) {
			if (Sibuk.enemyRefusal(master) != null) {
				return false;
			}
			Sibuk.enemyTaken(master, SeteruIds.SIBUK);
		}
		Naskah.ucap(p.mereka(), SeteruIds.V_KEROYOK, Naskah.baris(f, "tawuran"));
		Adegan.marah(p.mereka());
		Adegan.wargaMenoleh(level, p.mereka());
		List<Pasangan> started = new ArrayList<>();
		for (Pasangan pair : pairs) {
			Adegan.hadap(pair.kita, pair.mereka);
			Adegan.tandaiSibuk(pair.kita, MAX_TICKS);
			int gap = pair.kita.data().realm().ordinal() - pair.mereka.data().realm().ordinal();
			DuelRuleSet rules = DuelRuleSet.suggested(gap).withoutStake().withIntro(false).withCrowd(true)
					.withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN);
			Component why = DuelChallenges.startDirect(null, pair.kita, pair.mereka, DuelIds.PAIR_NPC, rules);
			if (why == null) {
				started.add(pair);
			}
		}
		if (started.isEmpty()) {
			return false;
		}
		// those without a session: rolled as data, while the others cheer
		int rest = Math.max(0, Math.min(f.jiwa.size(), f.tokoh.size()) - started.size());
		RandomSource rnd = Tangga.acak(f, server.getTickCount());
		int dataWin = 0;
		int dataLose = 0;
		for (int i = 0; i < rest; i++) {
			if (rnd.nextBoolean()) {
				dataWin++;
			} else {
				dataLose++;
			}
		}
		for (TubuhSeteru.Tubuh t : TubuhSeteru.semua()) {
			if (f.id.equals(t.seteru) && started.stream().noneMatch(pair -> pair.mereka == t.body)) {
				Adegan.klip(t.body, SeteruIds.KLIP_SORAK_KEROYOK, MAX_TICKS, 0, 0, 0);
			}
		}
		f.adegan.putBoolean("hidup", true);
		SeteruPayloads.judul(level, p.pos(), f, p.kita().getName(), p.mereka().getName());
		ramai = new Ramai(f.id, f.owner, started, dataWin, dataLose, server.getTickCount());
		Kabar.sebar(server, f, SeteruIds.KABAR_TAWURAN);
		return true;
	}

	/** Every second while a brawl runs: when both sessions ended, the majority decides. */
	static void tick(MinecraftServer server) {
		Ramai r = ramai;
		if (r == null || server.getTickCount() % 20 != 13) {
			return;
		}
		boolean running = false;
		for (int i = 0; i < r.pasangan.size(); i++) {
			DuelSession s = DuelSessions.byEntity(r.pasangan.get(i).kita);
			if (s != null) {
				if (s.phase().ordinal() >= DuelSession.Phase.FIGHT.ordinal()) {
					r.bertarung.set(i, Boolean.TRUE);
				}
				if (!s.ended()) {
					running = true;
				}
			}
		}
		if (running && server.getTickCount() - r.mulai < MAX_TICKS) {
			return;
		}
		ramai = null;
		Perseteruan f = SeteruData.get(server).aula(r.owner).byId(r.seteru);
		if (f == null) {
			return;
		}
		f.adegan = new CompoundTag();
		int win = r.dataMenang;
		int lose = r.dataKalah;
		for (int i = 0; i < r.pasangan.size(); i++) {
			Pasangan pair = r.pasangan.get(i);
			if (!r.bertarung.get(i)) {
				continue;
			}
			boolean ours = sehat(pair.kita) >= sehat(pair.mereka);
			if (ours) {
				win++;
				Adegan.klip(pair.kita, SeteruIds.KLIP_MENANG, 60, 0, 0, 0);
				Adegan.klip(pair.mereka, SeteruIds.KLIP_KALAH, 80, 0, 0, 0);
			} else {
				lose++;
				Adegan.klip(pair.mereka, SeteruIds.KLIP_MENANG, 60, 0, 0, 0);
				Adegan.klip(pair.kita, SeteruIds.KLIP_KALAH, 80, 0, 0, 0);
			}
		}
		boolean kitaMenang = win == lose ? Tangga.acak(f, server.getTickCount()).nextBoolean() : win > lose;
		Adegan.nanti(server, 160, () -> TubuhSeteru.hilangSeteru(f.id));
		Tangga.hasilTawuran(server, f, kitaMenang);
	}

	private static float sehat(DiscipleEntity body) {
		return body.isRemoved() || !body.isAlive() ? 0.0F : body.getHealth() / Math.max(1.0F, body.getMaxHealth());
	}

	static void bersihkan() {
		ramai = null;
	}

	private Tawuran() {
	}
}
