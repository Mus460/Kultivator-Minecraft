package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.data.Element;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleNames;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gate Opening Day on the Reception Square (DESIGN 38.B.6, 38.M.4.3): every tenth day ({@code d % 10 == 4}) or the next free
 * one, 6000-12000. The eight tented stalls fill with the recruiters of the strongest NPC sects (a
 * {@link NpcRole#MURID_SEKTE_DALAM} body per stall calling out, line {@code kotabesar.recruit_call}), and eight to twelve
 * young hopefuls of the capital come to be chosen.
 * <p>
 * A player with a sect hall of level 2 or more rents a recruiting stall for 30 NB ({@code lapak_rekrut} screen) and may
 * invite three hopefuls a day. Each hopeful accepts with {@code clamp(0.3 + renown / 1000 + 0.1 x (hall level - average
 * NPC sect tier) + Nama Baik / 200, 0.1, 0.9)}; an accepted one becomes a candidate of the hall
 * ({@link Halls#addCandidates}, source {@code kotabesar}). Hopefuls are rolled from the city and the day, so the sheet and
 * the invitation always talk about the same youth.
 */
final class HariGerbang implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_HARI_GERBANG;
	static final long SEWA = 30L;
	static final int UNDANG_MAKS = 3;
	static final int LEVEL_AULA = 2;

	private final Map<Long, List<UUID>> perekrut = new HashMap<>();

	HariGerbang() {
		TempatLayar.daftar(LembarKunci.LapakRekrut.LAYAR, this::aksiRekrut, this::bangunRekrut);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 2;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return Math.floorMod(day, 10L) == 4L;
	}

	@Override
	public long mulaiJam() {
		return 6000L;
	}

	@Override
	public long akhirJam() {
		return 12000L;
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		if (!hidup) {
			CeritaApi.kabar(level, town, "kota_besar.hari_gerbang", KotaBesarKota.nama(server, k));
			return;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity stage = kota == null ? null : kota.panggung(ID);
		if (stage == null) {
			return;
		}
		List<BlockPos> stalls = KotaBesarAnchors.titikPanggung(level, stage);
		DiplomasiService d = SekteApi.diplomasi();
		List<String> sects = PertemuanSekte.kursi(town, Kota.day(server));
		List<UUID> bodies = new ArrayList<>();
		for (int i = 0; i < stalls.size() && i < sects.size(); i++) {
			BlockPos at = stalls.get(i);
			float yaw = (float) Math.toDegrees(Math.atan2(stage.getX() - at.getX(), at.getZ() - stage.getZ()));
			KultivatorNpc npc = HidupApi.tubuh(level, town, NpcRole.MURID_SEKTE_DALAM, at, yaw);
			if (npc == null) {
				continue;
			}
			Component name = d.name(sects.get(i));
			npc.setPlate(Component.translatable("kultivator.kota_besar.hari_gerbang.perekrut", name == null ? Component.literal(sects.get(i)) : name));
			npc.memory().putString("kb_acara", ID);
			npc.memory().putBoolean("hold", true);
			npc.setLonely(96.0D, 2400);
			bodies.add(npc.getUUID());
		}
		perekrut.put(town, bodies);
		if (!bodies.isEmpty() && level.getEntity(bodies.get(0)) instanceof KultivatorNpc first) {
			Voice.say(first, "kotabesar.recruit_call", null);
		}
	}

	@Override
	public void tick(ServerLevel level, Kandidat k, long town, long now) {
		if (now % 300L >= 20L) {
			return;
		}
		List<UUID> bodies = perekrut.get(town);
		if (bodies == null || bodies.isEmpty()) {
			return;
		}
		int i = (int) Math.floorMod(now / 300L, bodies.size());
		if (level.getEntity(bodies.get(i)) instanceof KultivatorNpc npc) {
			HidupApi.klip(npc, GerakId.SERU_DAGANG, 0, 60);
			npc.say(Component.translatable("kultivator.kota_besar.hari_gerbang.seru." + Math.floorMod(now / 300L, 6L)), 120);
		}
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		List<UUID> bodies = perekrut.remove(town);
		if (bodies != null) {
			for (UUID id : bodies) {
				Entity e = level.getEntity(id);
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					npc.vanish();
				}
			}
		}
	}

	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.hari_gerbang.catatan", Barang.nb(SEWA), UNDANG_MAKS));
	}

	// ================================================================== hopefuls

	/** One youth of the day: data, name, seed. */
	private record Calon(int no, DiscipleData data, MutableComponent nama, int tekad) {
	}

	private static List<Calon> calon(long town, long day) {
		RandomSource count = RandomSource.create(town * 31L + day);
		int n = 8 + count.nextInt(5);
		List<Calon> out = new ArrayList<>();
		List<Component> taken = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			RandomSource r = RandomSource.create(town ^ (day * 0x9E3779B97F4A7C15L) ^ (i * 0xC2B2AE3D27D4EB4FL));
			DiscipleData data = DiscipleData.fresh(r);
			MutableComponent nama = DiscipleNames.pick(r, taken);
			taken.add(nama);
			out.add(new Calon(i, data, nama, 20 + r.nextInt(81)));
		}
		return out;
	}

	/** The chance a hopeful takes the player's invitation. */
	static double peluang(ServerPlayer p, long town) {
		Halls.HallView hall = Halls.hallOf(p.getUUID());
		long renown = hall == null ? 0L : hall.renown();
		int level = hall == null ? 0 : hall.level();
		DiplomasiService d = SekteApi.diplomasi();
		double avg = 0.0D;
		int n = 0;
		for (String s : d.sects()) {
			avg += d.tier(s);
			n++;
		}
		avg = n == 0 ? 3.0D : avg / n;
		int nb = KotaData.get(p.level().getServer()).reputation(town, p.getUUID());
		return Mth.clamp(0.3D + renown / 1000.0D + 0.1D * (level - avg) + nb / 200.0D, 0.1D, 0.9D);
	}

	private static CompoundTag hari(ServerPlayer p, long day) {
		CompoundTag me = KotaBesarData.get(p.level().getServer()).pemain(p.getUUID()).getCompoundOrEmpty("rekrut");
		if (me.getLongOr("hari", Long.MIN_VALUE) != day) {
			me = new CompoundTag();
			me.putLong("hari", day);
		}
		return me;
	}

	private static long townDari(ServerPlayer p, int npcId) {
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc && Hukum.ibuKota(p.level().getServer(), npc.town())) {
			return npc.town();
		}
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		return k == null ? 0L : KotaBesarKota.situs(k);
	}

	private @Nullable CompoundTag bangunRekrut(ServerPlayer p, int npcId) {
		long town = townDari(p, npcId);
		if (town == 0L) {
			return null;
		}
		MinecraftServer server = p.level().getServer();
		long day = Kota.day(server);
		CompoundTag tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_PUSAT);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.rekrut.judul"));
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc) {
			tag.put(LembarKunci.NPC, KotaBesarTampil.kartu(npc));
		}
		if (Halls.level(p.getUUID()) < LEVEL_AULA) {
			KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.rekrut.kunci"),
					Component.translatable("kultivator.kota_besar.rekrut.kunci.syarat", LEVEL_AULA));
			return tag;
		}
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.acara.belum_buka"), null);
			return tag;
		}
		CompoundTag me = hari(p, day);
		boolean rented = me.getBooleanOr("sewa", false);
		tag.putLong(LembarKunci.LapakRekrut.SEWA, SEWA);
		tag.putBoolean(LembarKunci.LapakRekrut.SUDAH_SEWA, rented);
		int used = me.getIntOr("undang", 0);
		tag.putInt(LembarKunci.LapakRekrut.UNDANG_SISA, Math.max(0, UNDANG_MAKS - used));
		int chance = (int) Math.round(peluang(p, town) * 100.0D);
		CompoundTag done = me.getCompoundOrEmpty("jawab");
		CompoundTag hallTaken = KotaBesarData.get(server).bagian(town, "calon_" + day);
		ListTag list = new ListTag();
		for (Calon c : calon(town, day)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(c.no()));
			LembarKunci.teks(row, LembarKunci.NAMA, c.nama());
			row.putInt(LembarKunci.POTRET, -1);
			LembarKunci.teks(row, LembarKunci.LapakRekrut.AKAR, akar(c.data()));
			LembarKunci.teks(row, LembarKunci.LapakRekrut.BAKAT, Component.translatable("kultivator.kota_besar.rekrut.bakat",
					String.format(java.util.Locale.ROOT, "%.1f", c.data().comprehension()), c.tekad()));
			row.putInt(LembarKunci.PELUANG, chance);
			String key = Integer.toString(c.no());
			if (done.contains(key)) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable(done.getBooleanOr(key, false)
						? "kultivator.kota_besar.rekrut.ikut" : "kultivator.kota_besar.rekrut.tolak"));
			} else if (hallTaken.contains(key)) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_besar.rekrut.diambil"));
			} else if (!rented) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_besar.rekrut.sewa_dulu"));
			} else if (used >= UNDANG_MAKS) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_BATAS);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_besar.rekrut.batas"));
			} else {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			}
			list.add(row);
		}
		tag.put(LembarKunci.LapakRekrut.CALON, list);
		return tag;
	}

	private static Component akar(DiscipleData data) {
		MutableComponent els = Component.empty();
		boolean first = true;
		for (Element e : Element.fromMask(data.rootElements())) {
			if (!first) {
				els.append(Component.literal(" · "));
			}
			els.append(e.displayName());
			first = false;
		}
		return Component.translatable("kultivator.kota_besar.rekrut.akar", data.rootGrade().displayName(), els);
	}

	private void aksiRekrut(ServerPlayer p, int npcId, String op) {
		long town = townDari(p, npcId);
		MinecraftServer server = p.level().getServer();
		if (town == 0L || !KotaBesarKalender.berjalan(server, town, ID) || Halls.level(p.getUUID()) < LEVEL_AULA) {
			return;
		}
		long day = Kota.day(server);
		String[] parts = LembarKunci.urai(op);
		KotaBesarData data = KotaBesarData.get(server);
		CompoundTag me = hari(p, day);
		Component pesan = null;
		boolean ok = true;
		switch (parts[0]) {
			case LembarKunci.LapakRekrut.OP_SEWA -> {
				if (me.getBooleanOr("sewa", false)) {
					return;
				}
				if (!EkonomiApi.bayar(p, SEWA, "kotabesar.rekrut")) {
					pesan = Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(SEWA));
					ok = false;
				} else {
					me.putBoolean("sewa", true);
					pesan = Component.translatable("kultivator.kota_besar.rekrut.disewa");
				}
			}
			case LembarKunci.LapakRekrut.OP_UNDANG -> {
				int no = LembarKunci.argInt(parts, 1, -1);
				List<Calon> all = calon(town, day);
				if (!me.getBooleanOr("sewa", false) || no < 0 || no >= all.size() || me.getIntOr("undang", 0) >= UNDANG_MAKS) {
					return;
				}
				String key = Integer.toString(no);
				CompoundTag done = me.getCompoundOrEmpty("jawab");
				CompoundTag taken = data.bagian(town, "calon_" + day);
				if (done.contains(key) || taken.contains(key)) {
					return;
				}
				me.putInt("undang", me.getIntOr("undang", 0) + 1);
				Calon c = all.get(no);
				boolean yes = p.getRandom().nextDouble() < peluang(p, town);
				done.putBoolean(key, yes);
				me.put("jawab", done);
				if (yes) {
					taken.putBoolean(key, true);
					data.putBagian(town, "calon_" + day, taken);
					Halls.addCandidates(p.getUUID(), List.of(new Halls.CandidateSpec(c.data(), c.tekad(), -1.0D, -1.0D, false, "kotabesar",
							"kultivator.kota_besar.rekrut.asal", 5)));
					pesan = Component.translatable("kultivator.kota_besar.rekrut.terima", c.nama());
				} else {
					pesan = Component.translatable("kultivator.kota_besar.rekrut.menolak", c.nama());
					ok = false;
				}
			}
			default -> {
				return;
			}
		}
		data.pemain(p.getUUID()).put("rekrut", me);
		data.setDirty();
		CompoundTag tag = bangunRekrut(p, npcId);
		if (tag != null) {
			if (pesan != null) {
				KotaBesarTampil.pesan(tag, pesan, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN);
			}
			TempatLayar.segarkan(p, LembarKunci.LapakRekrut.LAYAR, npcId, tag);
		}
	}
}
