package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.fx.Fx;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The town body (DESIGN 39.6.6, contract C39.C2): a {@code DiscipleEntity} of role {@code JELMAAN} that shows a disciple
 * whose real record is in {@code AwayStore} while a player is near: in town (within 128 blocks of the arrival point,
 * walking from stall to stall exactly as the journey's stages say), on the road (within 64 blocks of where the plan puts
 * the party, walking on) and bound at a captive's place. It copies the stored tablet's name, Sosok and style and wears
 * travel clothes ({@code SosokApi.setBusanaKhusus(1)}); it is never saved, never listed, carries nothing, cannot be hurt
 * and fights nobody (39A-0). At most {@code jelmaanPerKota} per town, {@code jelmaanServer} in all, under the NPC budget
 * ({@code DuniaBudget.mayAdd}); it leaves when the player is 160 blocks away or its stage ends.
 * <p>
 * The master's right-click (registered in a phase before the disciple core, which answers every non-DISCIPLE body)
 * opens the meeting: "Guru! Murid sedang membeli ..." with [Lihat belanja], [Tambah uang], [Ikut Guru pulang],
 * [Lanjutkan]; on a captive it frees the party. Other players only read the name and the sect.
 */
public final class Jelmaan {

	/** Where a body is shown. */
	enum Jenis {
		KOTA, JALAN, TAWANAN
	}

	/** One shown body. */
	static final class Tubuh {
		final UUID soul;
		final UUID owner;
		final Jenis jenis;
		final String journey;
		final long town;
		int tahap = -1;
		@Nullable String stop;
		@Nullable BlockPos sasaran;
		int npc = -1;
		int beat;
		long beatT0;
		boolean tiba;
		boolean pulang;
		final DiscipleEntity body;

		Tubuh(UUID soul, UUID owner, Jenis jenis, String journey, long town, DiscipleEntity body) {
			this.soul = soul;
			this.owner = owner;
			this.jenis = jenis;
			this.journey = journey;
			this.town = town;
			this.body = body;
		}
	}

	private static final Map<UUID, Tubuh> PER_JIWA = new HashMap<>();
	private static final Map<Integer, Tubuh> PER_ENTITAS = new HashMap<>();

	static void kosongkan() {
		for (Tubuh t : PER_JIWA.values()) {
			if (!t.body.isRemoved()) {
				t.body.discard();
			}
		}
		PER_JIWA.clear();
		PER_ENTITAS.clear();
	}

	/** True while a body of {@code soul} is shown. */
	static boolean tampil(UUID soul) {
		Tubuh t = PER_JIWA.get(soul);
		return t != null && !t.body.isRemoved();
	}

	/** The scene of a town body, or null for anything else. */
	static @Nullable Tubuh dari(Entity e) {
		return e == null ? null : PER_ENTITAS.get(e.getId());
	}

	/** True when a player stands within {@code r} blocks of {@code pos} in {@code level}. */
	static boolean dekatPemain(ServerLevel level, BlockPos pos, double r) {
		double r2 = r * r;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** The walking target of a town body (read by {@link JelmaanJalanGoal}). */
	static @Nullable BlockPos sasaran(DiscipleEntity e) {
		Tubuh t = dari(e);
		return t == null ? null : t.sasaran;
	}

	// ================================================================== the update (every 40 ticks)

	static void perbarui(MinecraftServer server) {
		MandiriConfig cfg = MandiriConfig.get();
		long now = server.overworld().getGameTime();
		// bodies that should go
		for (Iterator<Map.Entry<UUID, Tubuh>> it = PER_JIWA.entrySet().iterator(); it.hasNext(); ) {
			Tubuh t = it.next().getValue();
			Rencana r = Perjalanan.rencana(server, t.soul);
			boolean gone = t.body.isRemoved() || r == null || !r.id().equals(t.journey) || !(t.body.level() instanceof ServerLevel lv)
					|| !dekatPemain(lv, t.body.blockPosition(), 160.0D) || !masihTampil(r, t, now);
			if (gone) {
				if (!t.body.isRemoved()) {
					Fx.burst((ServerLevel) t.body.level(), t.body.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 10, 0.6F);
					t.body.discard();
				}
				PER_ENTITAS.remove(t.body.getId());
				it.remove();
			}
		}
		int total = PER_JIWA.size();
		if (total >= cfg.jelmaanServer || cfg.jelmaanPerKota <= 0) {
			return;
		}
		JalanData data = JalanData.get(server);
		for (UUID soul : data.jiwaAktif()) {
			if (total >= cfg.jelmaanServer) {
				break;
			}
			if (PER_JIWA.containsKey(soul)) {
				continue;
			}
			Rencana r = Perjalanan.rencana(server, soul);
			if (r == null) {
				continue;
			}
			ServerLevel level = Tahap.level(server, r);
			if (level == null) {
				continue;
			}
			int i = r.tahapPada(now);
			if (i < 0) {
				continue;
			}
			CompoundTag stage = r.tahap(i);
			String kind = stage.getStringOr("jenis", "");
			Jenis jenis = switch (kind) {
				case Tahap.TIBA_KOTA, Tahap.KUNJUNGAN -> Jenis.KOTA;
				case Tahap.JALAN, Tahap.PULANG -> cfg.tampilJalan && !Rencana.PEDANG.equals(r.moda()) && !Rencana.AWAN.equals(r.moda()) ? Jenis.JALAN : null;
				case Tahap.TAWANAN -> Jenis.TAWANAN;
				default -> null;
			};
			if (jenis == null) {
				continue;
			}
			BlockPos at = posisi(level, r, stage, jenis, now);
			double range = jenis == Jenis.JALAN ? 64.0D : 128.0D;
			if (at == null || !level.isLoaded(at) || !dekatPemain(level, at, range)) {
				continue;
			}
			if (jenis == Jenis.KOTA && diKota(r.town()) >= cfg.jelmaanPerKota || jenis == Jenis.JALAN && jalanDekat(level, at) >= 2) {
				continue;
			}
			if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, Vec3.atBottomCenterOf(at))) {
				continue;
			}
			DiscipleEntity body = munculkan(level, r.owner(), soul, at);
			if (body == null) {
				continue;
			}
			Tubuh t = new Tubuh(soul, r.owner(), jenis, r.id(), r.town(), body);
			t.tahap = i;
			PER_JIWA.put(soul, t);
			PER_ENTITAS.put(body.getId(), t);
			total++;
			mulaiTahap(level, r, t, i, stage);
		}
		for (Tubuh t : PER_JIWA.values()) {
			Rencana r = Perjalanan.rencana(server, t.soul);
			if (r == null || !(t.body.level() instanceof ServerLevel level)) {
				continue;
			}
			int i = r.tahapPada(now);
			if (i >= 0 && i != t.tahap) {
				t.tahap = i;
				mulaiTahap(level, r, t, i, r.tahap(i));
			} else if (t.jenis == Jenis.JALAN) {
				t.sasaran = posisi(level, r, r.tahap(Math.max(0, t.tahap)), Jenis.JALAN, now + 400L);
			}
		}
	}

	/** Bodies leave once their stage kind is no longer shown (the town visit over, the captive freed). */
	private static boolean masihTampil(Rencana r, Tubuh t, long now) {
		int i = r.tahapPada(now);
		if (i < 0) {
			return false;
		}
		String kind = r.tahap(i).getStringOr("jenis", "");
		return switch (t.jenis) {
			case KOTA -> kind.equals(Tahap.KUNJUNGAN) || kind.equals(Tahap.TIBA_KOTA);
			case JALAN -> kind.equals(Tahap.JALAN) || kind.equals(Tahap.PULANG);
			case TAWANAN -> kind.equals(Tahap.TAWANAN) && r.tertawan();
		};
	}

	private static int diKota(long town) {
		int n = 0;
		for (Tubuh t : PER_JIWA.values()) {
			if (t.jenis == Jenis.KOTA && t.town == town) {
				n++;
			}
		}
		return n;
	}

	private static int jalanDekat(ServerLevel level, BlockPos at) {
		int n = 0;
		for (Tubuh t : PER_JIWA.values()) {
			if (t.jenis == Jenis.JALAN && t.body.level() == level && t.body.blockPosition().distSqr(at) < 96 * 96) {
				n++;
			}
		}
		return n;
	}

	/** Where the plan puts the party at game time {@code t} on {@code stage}. */
	private static @Nullable BlockPos posisi(ServerLevel level, Rencana r, CompoundTag stage, Jenis jenis, long t) {
		BlockPos end = BlockPos.of(stage.getLongOr("pos", 0L));
		if (jenis == Jenis.TAWANAN) {
			return BlockPos.of(r.tawanan().getLongOr("pos", end.asLong()));
		}
		if (jenis == Jenis.KOTA) {
			return KontrakKota.tiba(level, r.town() != 0L ? r.town() : end.asLong());
		}
		BlockPos from = stage.contains("dari") ? BlockPos.of(stage.getLongOr("dari", 0L)) : end;
		long a = stage.getLongOr("mulai", 0L);
		long b = Math.max(a + 1L, stage.getLongOr("sampai", a + 1L));
		double f = Math.max(0.0D, Math.min(1.0D, (t - a) / (double) (b - a)));
		int x = (int) (from.getX() + (end.getX() - from.getX()) * f);
		int z = (int) (from.getZ() + (end.getZ() - from.getZ()) * f);
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		return KontrakKota.tanah(level, x, z, from.getY());
	}

	/** Spawns the body of {@code soul} from its stored tablet (read only) at {@code at}. */
	private static @Nullable DiscipleEntity munculkan(ServerLevel level, UUID owner, UUID soul, BlockPos at) {
		CompoundTag rec = Tahap.rekamAway(owner, soul);
		if (rec == null) {
			return null;
		}
		CompoundTag stored = rec.getCompoundOrEmpty("stored");
		DiscipleData data = DiscipleData.load(stored.getCompoundOrEmpty("disciple"));
		Component name = stored.read("name", ComponentSerialization.CODEC).orElse(null);
		int skin = stored.getIntOr("skin", 0);
		boolean slim = stored.getBooleanOr("slim", false);
		Sosok sosok = Sosok.unpack(stored.getIntOr("sosok", Sosok.KOSONG));
		int gaya = stored.getIntOr("gaya", 0);
		try {
			DiscipleEntity body = Disciples.spawnUnowned(level, Vec3.atBottomCenterOf(at), data, name, skin, slim, sosok, gaya,
					DiscipleEntity.Role.JELMAAN, at);
			if (body == null) {
				return null;
			}
			if (sosok != null) {
				SosokApi.setSosok(body, sosok);
			}
			SosokApi.setBusanaKhusus(body, 1);
			DuniaBudget.track(DuniaBudget.NPC, body);
			Fx.burst(level, body.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 8, 0.5F);
			return body;
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Mandiri: town body of {} could not be shown", soul, exception);
			return null;
		}
	}

	/** A body entered stage {@code i}: its target and first beat. */
	private static void mulaiTahap(ServerLevel level, Rencana r, Tubuh t, int i, CompoundTag stage) {
		t.beat = 0;
		t.beatT0 = level.getGameTime();
		t.tiba = false;
		t.npc = -1;
		String kind = stage.getStringOr("jenis", "");
		if (t.jenis == Jenis.TAWANAN) {
			t.sasaran = null;
			SosokApi.gerak(t.body, GerakId.BERLUTUT_RANTAI, 0, 0, PropId.RANTAI_TANGAN, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
			return;
		}
		if (t.jenis == Jenis.JALAN) {
			t.sasaran = posisi(level, r, stage, Jenis.JALAN, level.getGameTime() + 400L);
			SosokApi.gerak(t.body, GerakId.JALAN_BAWA, GerakId.V_JALAN_BAWA_PUNGGUNG, 0, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.RANGKA_PUNGGUNG);
			return;
		}
		t.stop = Tahap.KUNJUNGAN.equals(kind) ? stage.getStringOr("stop", "") : KunjunganKota.Stop.GERBANG.id;
		KunjunganKota.Stop stop = KunjunganKota.Stop.byId(t.stop);
		BlockPos centre = KontrakKota.tiba(level, t.town != 0L ? t.town : r.tujuan().pos().asLong());
		t.sasaran = tempatStop(level, t, stop, centre);
		SosokApi.gerak(t.body, GerakId.JALAN_SANTAI, 0, 0);
	}

	/** The stall of a stop: a 38 life point, else the town NPC of the stop's role (found once), else near the square. */
	private static BlockPos tempatStop(ServerLevel level, Tubuh t, @Nullable KunjunganKota.Stop stop, BlockPos centre) {
		if (stop == null || stop == KunjunganKota.Stop.GERBANG) {
			return centre;
		}
		if (!stop.titik.isEmpty()) {
			List<Titik> points = KontrakKota.titik(level, centre, stop.titik);
			if (!points.isEmpty()) {
				Titik p = points.get(Math.floorMod(t.soul.hashCode(), Math.min(3, points.size())));
				KultivatorNpc keeper = penjaga(level, p.pos(), stop, 12.0D);
				if (keeper != null) {
					kenal(t, stop, keeper);
				}
				return p.pos();
			}
		}
		KultivatorNpc keeper = penjaga(level, centre, stop, 80.0D);
		if (keeper != null) {
			kenal(t, stop, keeper);
			Vec3 toward = keeper.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
			Vec3 spot = keeper.position().add(toward.lengthSqr() < 1.0E-3D ? new Vec3(1.5D, 0.0D, 0.0D) : toward.normalize().scale(1.6D));
			return BlockPos.containing(spot);
		}
		int ox = Math.floorMod(t.soul.hashCode() + stop.ordinal() * 7, 17) - 8;
		int oz = Math.floorMod((t.soul.hashCode() >>> 8) + stop.ordinal() * 5, 17) - 8;
		return KontrakKota.tanah(level, centre.getX() + ox, centre.getZ() + oz, centre.getY());
	}

	private static void kenal(Tubuh t, KunjunganKota.Stop stop, KultivatorNpc keeper) {
		t.npc = keeper.getId();
		if (t.body.level() instanceof ServerLevel level && t.town != 0L) {
			JalanData.get(level.getServer()).kenalPedagang(t.town, stop.id, JalanIds.orang(keeper.plate(), keeper.look(), SosokApi.perempuan(keeper)));
		}
	}

	/** The nearest town NPC of the stop's roles within {@code r} of {@code at} (one scan per stop). */
	private static @Nullable KultivatorNpc penjaga(ServerLevel level, BlockPos at, KunjunganKota.Stop stop, double r) {
		KultivatorNpc best = null;
		double bestD = Double.MAX_VALUE;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at).inflate(r), n -> n.isAlive() && stop.peran.contains(n.role()))) {
			double d = npc.distanceToSqr(at.getX(), at.getY(), at.getZ());
			if (d < bestD) {
				bestD = d;
				best = npc;
			}
		}
		return best;
	}

	// ================================================================== beats at a stop (JelmaanPoseGoal)

	/** One beat of a stop's little play. */
	record Ketuk(int klip, int varian, int prop, int ticks, @Nullable String ucap, @Nullable String suara, int klipPenjaga, @Nullable String jawab) {
	}

	/** The beats of a stop (the town body plays them once on arriving at the stall, then idles in the stop's pose). */
	static List<Ketuk> ketuk(@Nullable KunjunganKota.Stop stop) {
		List<Ketuk> out = new ArrayList<>();
		if (stop == null) {
			return out;
		}
		switch (stop) {
			case GERBANG -> out.add(new Ketuk(GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, PropId.TIDAK_ADA, 100, "ucap.gerbang", null, 0, null));
			case LAPAK_PIL, PASAR, PASAR_MALAM -> {
				out.add(new Ketuk(GerakId.LIHAT_BARANG, 0, stop.prop, 160, "ucap.lihat", "lihat_lapak", stop.klipPenjaga, "jawab.lihat"));
				out.add(new Ketuk(GerakId.TAWAR, 0, PropId.KANTONG_UANG, 120, "ucap.tawar", "tawar", GerakId.BERDEBAT, "jawab.tawar"));
				out.add(new Ketuk(GerakId.ANGGUK_DENGAR, 0, PropId.KANTONG_UANG, 60, "ucap.sepakat", "tawar_sepakat", GerakId.HORMAT, "jawab.sepakat"));
			}
			case PANDAI -> {
				out.add(new Ketuk(GerakId.LIHAT_BARANG, 0, PropId.PEDANG_KAYU, 120, "ucap.pandai", "lihat_lapak", GerakId.PALU_LANDASAN, "jawab.pandai"));
				out.add(new Ketuk(GerakId.TEBAS_UDARA, 0, PropId.PEDANG_KAYU, 60, null, null, GerakId.SIKAP_LIPAT_TANGAN, null));
				out.add(new Ketuk(GerakId.TAWAR, 0, PropId.KANTONG_UANG, 120, "ucap.tawar", "tawar", GerakId.BERDEBAT, "jawab.tawar"));
				out.add(new Ketuk(GerakId.ANGGUK_DENGAR, 0, PropId.TIDAK_ADA, 60, "ucap.sepakat", "tawar_sepakat", GerakId.HORMAT, "jawab.sepakat"));
			}
			case KEDAI_TEH -> {
				out.add(new Ketuk(GerakId.DUDUK_KURSI, 0, PropId.TIDAK_ADA, 60, "ucap.teh", null, GerakId.TUANG_TEH, "jawab.teh"));
				out.add(new Ketuk(GerakId.MINUM_TEH, 0, PropId.CANGKIR, 600, null, null, 0, null));
				out.add(new Ketuk(GerakId.ANGGUK_DENGAR, 0, PropId.CANGKIR, 400, "ucap.kabar", null, GerakId.BISIK, "jawab.kabar"));
			}
			case RUMAH_MAKAN -> {
				out.add(new Ketuk(GerakId.DUDUK_KURSI, 0, PropId.TIDAK_ADA, 60, "ucap.pesan_makan", null, GerakId.MASAK, "jawab.makan"));
				out.add(new Ketuk(GerakId.MAKAN, 0, PropId.MANGKUK, 600, "ucap.enak", "makan", 0, null));
			}
			case PAPAN_MISI -> {
				out.add(new Ketuk(GerakId.MEMBACA, 0, PropId.GULUNGAN, 200, "ucap.papan", null, GerakId.BICARA_TANGAN, "jawab.papan"));
				out.add(new Ketuk(GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, PropId.TIDAK_ADA, 40, null, null, GerakId.ANGGUK_DENGAR, null));
			}
			case BALAI_LELANG -> {
				out.add(new Ketuk(GerakId.DUDUK_KURSI, 0, PropId.KIPAS_LIPAT, 400, null, null, GerakId.PALU_HAKIM, "jawab.lelang"));
				out.add(new Ketuk(GerakId.LAMBAI, 0, PropId.KIPAS_LIPAT, 60, "ucap.lelang", null, GerakId.TUNJUK, null));
			}
			case KUIL -> {
				out.add(new Ketuk(GerakId.SUJUD_DUPA, 0, PropId.DUPA, 300, null, null, GerakId.SIKAP_DOA, null));
				out.add(new Ketuk(GerakId.HORMAT_DALAM, 0, PropId.TIDAK_ADA, 60, "ucap.kuil", null, 0, null));
			}
			case TABIB -> {
				out.add(new Ketuk(GerakId.DUDUK_KURSI, 0, PropId.TIDAK_ADA, 400, "ucap.tabib", null, GerakId.PERIKSA_NADI, "jawab.tabib"));
				out.add(new Ketuk(GerakId.HORMAT, 0, PropId.TIDAK_ADA, 40, "ucap.terima_kasih", "thanks", GerakId.TEPUK_BAHU, null));
			}
			case LAPAK_SEKTE -> {
				out.add(new Ketuk(GerakId.SERU_DAGANG, 0, PropId.KANTONG_UANG, 300, "ucap.jual", null, 0, null));
				out.add(new Ketuk(GerakId.HITUNG_SEMPOA, 0, PropId.SEMPOA, 200, null, null, 0, null));
			}
			case PASAR_GELAP -> {
				out.add(new Ketuk(GerakId.SIKAP_TUNDUK, 0, PropId.TIDAK_ADA, 80, null, null, GerakId.HITUNG_KOIN_GELAP, "jawab.gelap"));
				out.add(new Ketuk(GerakId.BISIK, 0, PropId.KANTONG_UANG, 120, "ucap.gelap", null, GerakId.ANCAM, null));
			}
		}
		return out;
	}

	/** Plays beat {@code t.beat} (from the pose goal when the body stands at its stall). */
	static void mainkan(Tubuh t) {
		KunjunganKota.Stop stop = KunjunganKota.Stop.byId(t.stop == null ? "" : t.stop);
		List<Ketuk> beats = ketuk(stop);
		if (!(t.body.level() instanceof ServerLevel level)) {
			return;
		}
		Entity npcEntity = t.npc >= 0 ? level.getEntity(t.npc) : null;
		KultivatorNpc npc = npcEntity instanceof KultivatorNpc k ? k : null;
		if (npc != null) {
			t.body.getLookControl().setLookAt(npc, 30.0F, 30.0F);
		}
		if (t.beat >= beats.size()) {
			// the play is over: the stop's quiet pose until the next stage
			if (stop != null && stop.klip != 0 && level.getGameTime() - t.beatT0 > 200L) {
				SosokApi.gerak(t.body, stop.klip, 0, 200, stop.prop, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
				t.beatT0 = level.getGameTime();
			}
			return;
		}
		Ketuk b = beats.get(t.beat);
		if (level.getGameTime() - t.beatT0 < 0L) {
			return;
		}
		SosokApi.gerak(t.body, b.klip(), b.varian(), b.ticks(), b.prop(), PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		if (b.ucap() != null) {
			PelaksanaJalan.Msg2.dekat(t.body, JalanIds.t(b.ucap()), 16.0D);
			SosokApi.bicara(t.body, 40);
		}
		if (b.suara() != null) {
			SekteSuara.body(t.body, b.suara(), "greet");
		}
		if (npc != null) {
			KontrakKota.tatap(npc, t.body);
			if (b.klipPenjaga() != 0) {
				KontrakKota.klip(npc, b.klipPenjaga(), stop != null ? stop.propPenjaga : 0, Math.min(200, b.ticks()));
			}
			if (b.jawab() != null) {
				npc.say(JalanIds.t(b.jawab()), 60);
			}
		}
		if (b.klip() == GerakId.ANGGUK_DENGAR && b.prop() == PropId.KANTONG_UANG) {
			Fx.burst(level, t.body.position().add(0.0D, 1.1D, 0.0D), 0xFFD54A, 6, 0.3F);
		}
		t.beatT0 = level.getGameTime() + b.ticks();
		t.beat++;
	}

	// ================================================================== the master's meeting

	/** {@code UseEntityCallback} in a phase before the disciple core. */
	static InteractionResult pakai(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(entity instanceof DiscipleEntity body) || body.role() != DiscipleEntity.Role.JELMAAN || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		Tubuh t = dari(body);
		if (t == null || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		MinecraftServer server = sp.level().getServer();
		Rencana r = Perjalanan.rencana(server, t.soul);
		if (r == null) {
			return InteractionResult.SUCCESS;
		}
		if (!sp.getUUID().equals(t.owner)) {
			dev.kultivator.util.Msg.chat(sp, JalanIds.t("temu.orang_lain", body.getName().copy().withColor(JalanIds.WARNA_NAMA))
					.withColor(JalanIds.WARNA_ABU));
			return InteractionResult.SUCCESS;
		}
		if (t.jenis == Jenis.TAWANAN) {
			if (Tawanan.bebaskan(sp, r, true)) {
				SekteSuara.body(body, "diselamatkan", "thanks");
				PelaksanaJalan.Msg2.dekat(body, JalanIds.t("ucap.diselamatkan"), 16.0D);
				Fx.ring(sp.level(), body.position().add(0.0D, 0.1D, 0.0D), 0xFFE9A8, 2.0F);
			}
			return InteractionResult.SUCCESS;
		}
		temu(sp, body, t, r);
		return InteractionResult.SUCCESS;
	}

	/** "Guru! Murid sedang ..." and the four buttons. */
	static void temu(ServerPlayer master, DiscipleEntity body, Tubuh t, Rencana r) {
		body.getLookControl().setLookAt(master, 30.0F, 30.0F);
		SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 40);
		SekteSuara.body(body, "greet", "greet");
		String doing = t.jenis == Jenis.JALAN ? "jalan" : t.stop == null ? "gerbang" : t.stop;
		PelaksanaJalan.Msg2.dekat(body, JalanIds.t("temu.sapa." + doing, master.getName()), 12.0D);
		String soul = t.soul.toString();
		net.minecraft.network.chat.MutableComponent row = Component.literal("  ");
		row.append(Tawanan.tombol(JalanIds.k("tombol.belanja"), "/kultivasi perguruan mandiri jelmaan belanja " + soul,
				JalanIds.k("tombol.belanja.hover"), JalanIds.WARNA_GIOK)).append(" ");
		row.append(Tawanan.tombol(JalanIds.k("tombol.uang"), "/kultivasi perguruan mandiri jelmaan uang " + soul,
				JalanIds.k("tombol.uang.hover"), JalanIds.WARNA_EMAS)).append(" ");
		row.append(Tawanan.tombol(JalanIds.k("tombol.pulang"), "/kultivasi perguruan mandiri jelmaan pulang " + soul,
				JalanIds.k("tombol.pulang.hover"), 0xFFB070)).append(" ");
		row.append(Tawanan.tombol(JalanIds.k("tombol.lanjut"), "/kultivasi perguruan mandiri jelmaan lanjut " + soul,
				JalanIds.k("tombol.lanjut.hover"), JalanIds.WARNA_ABU));
		dev.kultivator.util.Msg.chat(master, row);
	}

	/** The town body of {@code soul}, or null. */
	static @Nullable DiscipleEntity tubuh(UUID soul) {
		Tubuh t = PER_JIWA.get(soul);
		return t == null || t.body.isRemoved() ? null : t.body;
	}

	/** Removes the body of {@code soul} at once (the real one arrives, a test). */
	static void hapus(UUID soul) {
		Tubuh t = PER_JIWA.remove(soul);
		if (t != null) {
			PER_ENTITAS.remove(t.body.getId());
			if (!t.body.isRemoved()) {
				t.body.discard();
			}
		}
	}

	/** Test: shows {@code soul}'s town body beside {@code player} now, whatever stage it is in. */
	static boolean paksa(ServerPlayer player, UUID soul) {
		MinecraftServer server = player.level().getServer();
		Rencana r = Perjalanan.rencana(server, soul);
		if (r == null || !(player.level() instanceof ServerLevel level) || PER_JIWA.containsKey(soul)) {
			return false;
		}
		BlockPos at = BlockPos.containing(player.position().add(player.getLookAngle().multiply(3.0D, 0.0D, 3.0D)));
		DiscipleEntity body = munculkan(level, r.owner(), soul, at);
		if (body == null) {
			return false;
		}
		int i = Math.max(0, r.tahapPada(level.getGameTime()));
		Tubuh t = new Tubuh(soul, r.owner(), Jenis.KOTA, r.id(), r.town(), body);
		t.tahap = i;
		PER_JIWA.put(soul, t);
		PER_ENTITAS.put(body.getId(), t);
		mulaiTahap(level, r, t, i, r.tahap(i));
		return true;
	}

	/** Bodies shown now (for the budget gauge). */
	static int jumlah() {
		return PER_JIWA.size();
	}

	/** Clears the speech of a body not used by {@link MandiriApi} (keeps the import of the API for readers). */
	private Jelmaan() {
	}
}
