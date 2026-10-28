package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The patrol gang (DESIGN 39.F.6). From T4, when the sect is hostile (or at war), one patrol per hall of two or three
 * roster members "guards" a stretch of road between the hall and the nearest town (a straight line, 200-600 blocks out).
 * It is pure data: bodies (with the sect banner on their backs) show only while a player is within 96 blocks of the
 * stretch, at most three, walking it back and forth. Journeys of our disciples that pass it get an ambush (25 %,
 * settled like T4) or, when careful ({@code AMAN}), go round (+20 % time). The master who walks past is mocked and
 * challenged in voice; they never strike first unless the sects are at war. Beating their leader in a duel drives the
 * patrol off for five days (heat -15, standing -3). The patrol ends when the feud falls below T3 or makes peace.
 */
final class Patroli {

	static final double JARAK_TAMPIL = 96.0D;
	static final int USIR_HARI = 5;

	/** A patrol on the stretch {@code a-b} at once ({@code uji ... patroli}: the road nearest the player). */
	static void buatDi(MinecraftServer server, Perseteruan f, BlockPos a, BlockPos b) {
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(f.owner);
		CompoundTag p = new CompoundTag();
		p.putString("seteru", f.id);
		p.putString("sekte", f.sekte);
		p.putLong("a", a.asLong());
		p.putLong("b", b.asLong());
		p.putLong("town", 0L);
		ListTag who = new ListTag();
		for (TokohSekte t : Roster.aktif(server, f.sekte)) {
			if (who.size() >= 3) {
				break;
			}
			if (t.peran < TokohSekte.PERAN_TETUA) {
				who.add(StringTag.valueOf(t.id));
			}
		}
		p.put("tokoh", who);
		p.putLong("usir", 0L);
		aula.patroli = p;
		f.sergapTertunda = true;
		f.catat(Ingatan.today(server), "riwayat.patroli", Teks.argSekte(f.sekte));
		data.setDirty();
	}

	/** T4: the gang takes the road when the sect is hostile; the ambush of the next journey waits either way. */
	static void buat(MinecraftServer server, Perseteruan f) {
		f.sergapTertunda = true;
		SeteruConfig config = SeteruConfig.get();
		Sikap sikap = SekteApi.diplomasi().stance(f.owner, f.sekte);
		if (!config.patroli || sikap != Sikap.BERMUSUHAN && sikap != Sikap.PERANG) {
			return;
		}
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(f.owner);
		if (!aula.patroli.isEmpty()) {
			return;
		}
		var hall = Halls.hallOf(f.owner);
		if (hall == null) {
			return;
		}
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null) {
			return;
		}
		RandomSource rnd = Tangga.acak(f, 404L);
		BlockPos home = hall.pos();
		long town = Towns.near(level, home, 1400);
		BlockPos target = town == 0L ? null : Towns.centre(server, town);
		double angle;
		double length;
		if (target != null && home.distSqr(target) > 220.0D * 220.0D) {
			angle = Math.atan2(target.getZ() - home.getZ(), target.getX() - home.getX());
			length = Math.sqrt(home.distSqr(target));
		} else {
			angle = rnd.nextDouble() * Math.PI * 2.0D;
			length = 420.0D;
		}
		double from = Math.max(200.0D, Math.min(520.0D, length * 0.45D));
		double to = Math.min(600.0D, from + 70.0D);
		BlockPos a = new BlockPos((int) (home.getX() + Math.cos(angle) * from), home.getY(), (int) (home.getZ() + Math.sin(angle) * from));
		BlockPos b = new BlockPos((int) (home.getX() + Math.cos(angle) * to), home.getY(), (int) (home.getZ() + Math.sin(angle) * to));
		CompoundTag p = new CompoundTag();
		p.putString("seteru", f.id);
		p.putString("sekte", f.sekte);
		p.putLong("a", a.asLong());
		p.putLong("b", b.asLong());
		p.putLong("town", town);
		ListTag who = new ListTag();
		for (String id : f.tokoh) {
			who.add(StringTag.valueOf(id));
		}
		List<TokohSekte> more = Roster.aktif(server, f.sekte);
		for (TokohSekte t : more) {
			if (who.size() >= 2 + rnd.nextInt(2)) {
				break;
			}
			if (!f.tokoh.contains(t.id) && t.peran < TokohSekte.PERAN_TETUA) {
				who.add(StringTag.valueOf(t.id));
			}
		}
		p.put("tokoh", who);
		p.putLong("usir", 0L);
		aula.patroli = p;
		f.catat(Ingatan.today(server), "riwayat.patroli", Teks.argSekte(f.sekte));
		data.setDirty();
		Kabar.sebar(server, f, SeteruIds.KABAR_PATROLI);
		Tangga.beritahu(server, f, Teks.t("pesan.patroli", Teks.sekte(f.sekte), Teks.kota(town)), true, true);
	}

	static void hapusUntuk(MinecraftServer server, Perseteruan f) {
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.peek(f.owner);
		if (aula != null && f.id.equals(aula.patroli.getStringOr("seteru", ""))) {
			aula.patroli = new CompoundTag();
			data.setDirty();
		}
		TubuhSeteru.hilangSeteru(f.id);
		f.sergapTertunda = false;
	}

	/** Daily: the patrol ends below T3, at peace, or when its feud is gone. */
	static void hariBaru(MinecraftServer server, UUID owner, SeteruData.Aula aula, long today) {
		if (aula.patroli.isEmpty()) {
			return;
		}
		Perseteruan f = aula.byId(aula.patroli.getStringOr("seteru", ""));
		if (f == null || !f.aktif() || f.panas < SeteruIds.AMBANG[SeteruIds.T_TAWURAN]) {
			if (f != null) {
				f.catat(today, "riwayat.patroli_pergi", Teks.argSekte(f.sekte));
				TubuhSeteru.hilangSeteru(f.id);
			}
			aula.patroli = new CompoundTag();
		}
	}

	/** True while the patrol is driven off. */
	static boolean diusir(CompoundTag p, long today) {
		return today < p.getLongOr("usir", 0L);
	}

	/** Every 40 ticks: shows the bodies of patrols a player is near (data otherwise). */
	static void tampilkan(MinecraftServer server) {
		SeteruData data = SeteruData.get(server);
		long today = Ingatan.today(server);
		for (Map.Entry<UUID, SeteruData.Aula> e : data.aula.entrySet()) {
			CompoundTag p = e.getValue().patroli;
			if (p.isEmpty() || diusir(p, today)) {
				continue;
			}
			var hall = Halls.hallOf(e.getKey());
			ServerLevel level = hall == null ? null : server.getLevel(hall.dim());
			if (level == null) {
				continue;
			}
			BlockPos a = BlockPos.of(p.getLongOr("a", 0L));
			BlockPos b = BlockPos.of(p.getLongOr("b", 0L));
			BlockPos mid = new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
			if (!level.isLoaded(mid) || level.getNearestPlayer(mid.getX() + 0.5D, mid.getY(), mid.getZ() + 0.5D, JARAK_TAMPIL, false) == null) {
				continue;
			}
			String seteru = p.getStringOr("seteru", "");
			ListTag who = p.getListOrEmpty("tokoh");
			int shown = 0;
			for (int i = 0; i < who.size() && shown < 3; i++) {
				TokohSekte t = data.tokoh(who.getStringOr(i, ""));
				if (t == null || !t.aktif() || t.luka(today)) {
					continue;
				}
				BlockPos start = i % 2 == 0 ? a.offset(i, 0, -i) : b.offset(-i, 0, i);
				if (TubuhSeteru.dari(t.id) == null) {
					TubuhSeteru.patroli(level, start, i % 2 == 0 ? b : a, t, e.getKey(), seteru);
				}
				shown++;
			}
		}
	}

	/** The patrol of {@code owner} whose road passes within 160 blocks of {@code pos}, or null. */
	static @Nullable CompoundTag diJalan(MinecraftServer server, UUID owner, BlockPos pos) {
		SeteruData.Aula aula = SeteruData.get(server).peek(owner);
		if (aula == null || aula.patroli.isEmpty() || diusir(aula.patroli, Ingatan.today(server))) {
			return null;
		}
		BlockPos a = BlockPos.of(aula.patroli.getLongOr("a", 0L));
		BlockPos b = BlockPos.of(aula.patroli.getLongOr("b", 0L));
		return jarakGaris(pos, a, b) <= 160.0D ? aula.patroli : null;
	}

	/** Distance in the x-z plane from {@code p} to the segment {@code a-b}. */
	static double jarakGaris(BlockPos p, BlockPos a, BlockPos b) {
		double ax = a.getX();
		double az = a.getZ();
		double bx = b.getX();
		double bz = b.getZ();
		double dx = bx - ax;
		double dz = bz - az;
		double len = dx * dx + dz * dz;
		double t = len <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((p.getX() - ax) * dx + (p.getZ() - az) * dz) / len));
		double cx = ax + t * dx;
		double cz = az + t * dz;
		return Math.sqrt((p.getX() - cx) * (p.getX() - cx) + (p.getZ() - cz) * (p.getZ() - cz));
	}

	// ================================================================== the master passes by

	/** The patrol mocks the master and challenges it (a duel button); at war it attacks through a duel at once. */
	static void ejekGuru(MinecraftServer server, TubuhSeteru.Tubuh t, ServerPlayer master) {
		if (t.seteru == null || Sibuk.why(master) != null) {
			return;
		}
		Perseteruan f = SeteruData.get(server).aula(master.getUUID()).byId(t.seteru);
		if (f == null || !f.aktif()) {
			return;
		}
		Adegan.klip(t.body, SeteruIds.KLIP_TANCAP_PANJI, 60, 0, 0, SeteruIds.panji(Math.max(0, SekteApi.diplomasi().aliran(f.sekte))));
		Naskah.ucap(t.body, SeteruIds.V_SERGAP, Naskah.baris(f, "sergap"));
		if (SekteApi.diplomasi().stance(f.owner, f.sekte) == Sikap.PERANG && Sibuk.enemyRefusal(master) == null) {
			Sibuk.enemyTaken(master, SeteruIds.SIBUK);
			usir(server, f, master);
			return;
		}
		MutableComponent line = Teks.t("pesan.patroli_ejek", t.body.getName(), Teks.sekte(f.sekte));
		line.append(" ").append(Tangga.tombol("tombol.usir", "usir " + f.id, 0xFF8A6A));
		Msg.chat(master, line);
	}

	private static final class Usir {
		final String seteru;
		final UUID owner;
		final UUID master;
		final DiscipleEntity pemimpin;
		final long mulai;
		/** The master won the duel (set by the end listener; a cancelled, drawn or missing result stays false). */
		boolean menang;

		Usir(String seteru, UUID owner, UUID master, DiscipleEntity pemimpin, long mulai) {
			this.seteru = seteru;
			this.owner = owner;
			this.master = master;
			this.pemimpin = pemimpin;
			this.mulai = mulai;
		}
	}

	/** Registered once from {@code Seteru.init}: reads who won the master's duel with the patrol leader. */
	static void daftar() {
		DuelEndListeners.add(Patroli::duelUsai);
	}

	private static void duelUsai(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Usir u : USIR) {
			if (session.involves(u.pemimpin)) {
				u.menang = winner != null && winner.getUUID().equals(u.master);
			}
		}
	}

	private static final List<Usir> USIR = new ArrayList<>();

	/** The master duels the patrol's leader (39.F.6): won, the patrol leaves for five days. */
	static @Nullable Component usir(MinecraftServer server, Perseteruan f, ServerPlayer master) {
		DiscipleEntity leader = null;
		double best = Double.MAX_VALUE;
		for (TubuhSeteru.Tubuh t : TubuhSeteru.semua()) {
			if (t.peran == TubuhSeteru.Peran.PATROLI && f.id.equals(t.seteru) && t.body.level() == master.level()) {
				double d = t.body.distanceToSqr(master);
				if (d < best) {
					best = d;
					leader = t.body;
				}
			}
		}
		if (leader == null || best > 48.0D * 48.0D) {
			return Teks.t("alasan.patroli_jauh");
		}
		if (!DuelSessions.room()) {
			return Component.translatable("kultivator.duel.reason.full");
		}
		DuelRuleSet rules = DuelRuleSet.suggested(0).withoutStake().withCrowd(false).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN);
		Component why = DuelChallenges.startDirect(master, master, leader, DuelIds.PAIR_NPC, rules);
		if (why != null) {
			return why;
		}
		Naskah.ucap(leader, SeteruIds.V_TANTANG, Naskah.baris(f, "patroli_guru"));
		USIR.add(new Usir(f.id, f.owner, master.getUUID(), leader, server.getTickCount()));
		return null;
	}

	static void tick(MinecraftServer server) {
		if (USIR.isEmpty() || server.getTickCount() % 20 != 17) {
			return;
		}
		for (Usir u : List.copyOf(USIR)) {
			DuelSession s = DuelSessions.byEntity(u.pemimpin);
			if (s != null && !s.ended() && server.getTickCount() - u.mulai < 20 * 60 * 4) {
				continue;
			}
			if (s == null && server.getTickCount() - u.mulai < 200) {
				continue;
			}
			USIR.remove(u);
			ServerPlayer master = server.getPlayerList().getPlayer(u.master);
			SeteruData data = SeteruData.get(server);
			SeteruData.Aula aula = data.aula(u.owner);
			Perseteruan f = aula.byId(u.seteru);
			if (f == null) {
				continue;
			}
			long today = Ingatan.today(server);
			if (u.menang) {
				if (f.id.equals(aula.patroli.getStringOr("seteru", ""))) {
					aula.patroli.putLong("usir", today + USIR_HARI);
				}
				f.catat(today, "riwayat.patroli_usir", Teks.argSekte(f.sekte));
				f.diurus = true;
				Tangga.panas(server, f, -15, "riwayat.panas_usir");
				DiplomasiMandiri.insiden(server, f.owner, f.sekte, -3, "patroli");
				Adegan.klip(u.pemimpin, SeteruIds.KLIP_KALAH, 80, 0, 0, 0);
				Naskah.ucap(u.pemimpin, SeteruIds.V_KALAH, Naskah.baris(f, "duel.kalah"));
				Adegan.nanti(server, 100, () -> TubuhSeteru.hilangSeteru(f.id));
				if (master != null) {
					Msg.chat(master, Teks.t("pesan.patroli_usir", Teks.sekte(f.sekte), USIR_HARI));
				}
			} else {
				f.catat(today, "riwayat.patroli_menang", Teks.argSekte(f.sekte));
				Tangga.panas(server, f, 5, "riwayat.panas_patroli");
				Adegan.klip(u.pemimpin, SeteruIds.KLIP_MENANG, 60, 0, 0, 0);
				Naskah.ucap(u.pemimpin, SeteruIds.V_MENANG, Naskah.baris(f, "duel.menang"));
			}
			data.setDirty();
		}
	}

	static void bersihkan() {
		USIR.clear();
	}

	private Patroli() {
	}
}
