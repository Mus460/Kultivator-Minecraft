package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.langit.SkyEvents;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.BarangCerita;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Lantern Festival (DESIGN 38.B.6.1) on every full-moon night (world day {@code d % 8 == 0}), 13000-18000, called off
 * under a Blood Eclipse. Live it lights the capital: sky lanterns rising over the walls and the lantern street points
 * ({@code jalan_lentera}) burning on clients (quads under {@code EffectQuality} and {@code EpicBudget}, never block light),
 * quad fireworks every 20 s between 15000 and 16000 over the Great Square (never {@code FireworkRocketEntity}), and six
 * Riddle Keepers ({@link NpcRole#PENJAGA_TEKA}) in a ring around the square's stage.
 * <p>
 * <b>Lantern riddles</b>: a keeper's {@code acara} screen poses one of 38ST's forty riddles
 * ({@code kultivator.cerita.teka.<n>.*}) with its three answers shuffled per player; a right answer gives a Lantern Charm
 * ({@code cerita:jimat_lentera}) and Nama Baik +1 (at most 3 per festival), a wrong one makes the keeper laugh. Each player
 * is asked at most {@link #TANYA_MAKS} riddles per festival. The townsfolk go to the market, gate and centre districts with
 * hand lanterns (38NL schedule override {@code festival_lentera}).
 */
final class FestivalLentera implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_FESTIVAL;
	static final int PENJAGA = 6;
	static final int NB_MAKS = 3;
	static final int TANYA_MAKS = 6;
	static final long KEMBANG_MULAI = 15000L;
	static final long KEMBANG_AKHIR = 16000L;
	static final long KEMBANG_JEDA = 400L;

	private final Map<Long, List<UUID>> penjaga = new HashMap<>();
	private final Map<Long, Long> kembangBerikut = new HashMap<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 1;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return Math.floorMod(day, 8L) == 0L;
	}

	@Override
	public long mulaiJam() {
		return 13000L;
	}

	@Override
	public long akhirJam() {
		return 18000L;
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		if (SkyEvents.is(level, SkyKind.BLOOD_ECLIPSE)) {
			KotaBesarData.get(server).putString(town, "ringkasan." + ID, "kultivator.kota_besar.acara.ringkasan.festival.batal");
			KotaBesarKalender.paksaAkhir(level, k, ID);
			return;
		}
		if (!hidup) {
			CeritaApi.kabar(level, town, "kota_besar.festival", KotaBesarKota.nama(server, k));
			KotaBesarData.get(server).putString(town, "ringkasan." + ID, "kultivator.kota_besar.acara.ringkasan.festival.data");
			return;
		}
		KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "festival", data(level, k, town, true));
		kembangBerikut.put(town, 0L);
		bawaPenjaga(level, k, town);
		Component nama = KotaBesarKota.nama(server, k);
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP + 64) {
				Msg.title(p, Component.translatable("kultivator.kota_besar.festival.judul").withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.kota_besar.festival.sub", nama), 10, 70, 20);
			}
		}
		List<UUID> ring = penjaga.get(town);
		if (ring != null && !ring.isEmpty() && level.getEntity(ring.get(0)) instanceof KultivatorNpc first) {
			Voice.say(first, "kotabesar.lantern_festival", null);
		}
		KotaBesarData.get(server).putString(town, "ringkasan." + ID, "kultivator.kota_besar.acara.ringkasan.festival.hidup");
	}

	/** The festival picture: city centre, the end time and the lantern street points (at most 64). */
	static CompoundTag data(ServerLevel level, Kandidat k, long town, boolean aktif) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("aktif", aktif);
		tag.putLong("town", town);
		tag.putDouble("x", k.pusatX());
		tag.putDouble("z", k.pusatZ());
		tag.putInt("y", KotaBesarKota.dasarY(level, k));
		long t = Kota.timeOfDay(level.getServer());
		tag.putLong("sampai", level.getGameTime() + Math.max(0L, 18000L - t));
		if (aktif) {
			List<Integer> pts = new ArrayList<>();
			for (Titik p : TempatApi.titik(level, town, "jalan_lentera")) {
				if (pts.size() >= 64 * 3) {
					break;
				}
				pts.add(p.pos().getX());
				pts.add(p.pos().getY());
				pts.add(p.pos().getZ());
			}
			tag.putIntArray("lentera", pts.stream().mapToInt(Integer::intValue).toArray());
		}
		return tag;
	}

	private void bawaPenjaga(ServerLevel level, Kandidat k, long town) {
		List<UUID> ring = penjaga.computeIfAbsent(town, t -> new ArrayList<>());
		ring.removeIf(id -> !(level.getEntity(id) instanceof KultivatorNpc n) || !n.isAlive());
		if (ring.size() >= PENJAGA) {
			return;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity stage = kota == null ? null : kota.panggung("festival");
		BlockPos centre = stage != null ? stage.blockPosition() : KotaBesarKota.titik(level, k, 0, 0, 1);
		for (int i = ring.size(); i < PENJAGA; i++) {
			double ang = Math.PI * 2.0D * i / PENJAGA;
			BlockPos at = TempatApi.groundSpot(level, centre.getX() + (int) Math.round(Math.cos(ang) * 8.0D),
					centre.getZ() + (int) Math.round(Math.sin(ang) * 8.0D), centre.getY());
			if (at == null) {
				continue;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(centre.getX() - at.getX(), at.getZ() - centre.getZ()));
			KultivatorNpc npc = HidupApi.tubuh(level, town, NpcRole.PENJAGA_TEKA, at, yaw);
			if (npc != null) {
				npc.memory().putString("kb_acara", ID);
				npc.memory().putBoolean("hold", true);
				npc.setLonely(96.0D, 2400);
				ring.add(npc.getUUID());
			}
		}
	}

	@Override
	public void tick(ServerLevel level, Kandidat k, long town, long now) {
		long t = Kota.timeOfDay(level.getServer());
		if (now % 1200L < 20L) {
			bawaPenjaga(level, k, town);
		}
		if (t >= KEMBANG_MULAI && t < KEMBANG_AKHIR && now >= kembangBerikut.getOrDefault(town, 0L)) {
			kembangBerikut.put(town, now + KEMBANG_JEDA);
			RandomSource r = level.getRandom();
			int bursts = 3 + r.nextInt(3);
			KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
			DuniaAnchorEntity stage = kota == null ? null : kota.panggung("festival");
			BlockPos c = stage != null ? stage.blockPosition() : KotaBesarKota.titik(level, k, 0, 0, 1);
			for (int i = 0; i < bursts; i++) {
				CompoundTag b = new CompoundTag();
				b.putDouble("x", c.getX() + (r.nextDouble() - 0.5D) * 48.0D);
				b.putDouble("y", c.getY() + 30.0D + r.nextDouble() * 18.0D);
				b.putDouble("z", c.getZ() + (r.nextDouble() - 0.5D) * 48.0D);
				b.putInt("warna", r.nextInt(6));
				b.putBoolean("besar", r.nextInt(4) == 0);
				b.putInt("tunda", i * 6);
				b.putLong("town", town);
				KotaBesarPayload.sekitar(level, Vec3.atCenterOf(c), "kembang", b);
			}
		}
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		List<UUID> ring = penjaga.remove(town);
		if (ring != null) {
			for (UUID id : ring) {
				Entity e = level.getEntity(id);
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					npc.vanish();
				}
			}
		}
		kembangBerikut.remove(town);
		if (hidup) {
			KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "festival", data(level, k, town, false));
		}
	}

	// ================================================================== riddles

	/** The player's riddle state of this festival (a fresh one on a new festival day). */
	private static CompoundTag teka(ServerPlayer p, long day) {
		KotaBesarData data = KotaBesarData.get(p.level().getServer());
		CompoundTag all = data.pemain(p.getUUID());
		CompoundTag t = all.getCompoundOrEmpty("teka");
		if (t.getLongOr("hari", Long.MIN_VALUE) != day) {
			t = new CompoundTag();
			t.putLong("hari", day);
			t.putInt("n", -1);
			t.putInt("ditanya", 0);
			t.putInt("benar", 0);
			t.putIntArray("sudah", new int[0]);
		}
		return t;
	}

	private static void simpan(ServerPlayer p, CompoundTag t) {
		KotaBesarData data = KotaBesarData.get(p.level().getServer());
		data.pemain(p.getUUID()).put("teka", t);
		data.setDirty();
	}

	/** A riddle the player has not had this festival (deterministic per player, day and count). */
	private static int pilihSoal(ServerPlayer p, CompoundTag t) {
		int[] sudah = t.getIntArray("sudah").orElse(new int[0]);
		RandomSource r = RandomSource.create(p.getUUID().getLeastSignificantBits() ^ t.getLongOr("hari", 0L) * 31L + t.getIntOr("ditanya", 0));
		for (int tries = 0; tries < 80; tries++) {
			int n = r.nextInt(CeritaApi.TEKA_TEKI);
			boolean dup = false;
			for (int s : sudah) {
				if (s == n) {
					dup = true;
					break;
				}
			}
			if (!dup) {
				return n;
			}
		}
		return Math.floorMod((int) r.nextLong(), CeritaApi.TEKA_TEKI);
	}

	/** The order of the three answers on screen: values 0 (right), 1, 2 (wrong) shuffled per player and riddle. */
	private static int[] urutan(ServerPlayer p, int n, long day) {
		int[] o = {0, 1, 2};
		RandomSource r = RandomSource.create(p.getUUID().getMostSignificantBits() ^ n * 977L ^ day);
		for (int i = 2; i > 0; i--) {
			int j = r.nextInt(i + 1);
			int tmp = o[i];
			o[i] = o[j];
			o[j] = tmp;
		}
		return o;
	}

	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.festival.catatan", NB_MAKS));
		if (!KotaBesarKalender.berjalan(server, town, ID) || npc == null || npc.role() != NpcRole.PENJAGA_TEKA) {
			return;
		}
		CompoundTag t = teka(player, day);
		int ditanya = t.getIntOr("ditanya", 0);
		CompoundTag c = new CompoundTag();
		c.putInt(LembarKunci.Acara.SISA, Math.max(0, TANYA_MAKS - ditanya));
		if (ditanya >= TANYA_MAKS) {
			LembarKunci.teks(c, LembarKunci.Acara.SOAL, Component.translatable("kultivator.kota_besar.festival.habis"));
			tag.put(LembarKunci.Acara.TEKA, c);
			return;
		}
		int n = t.getIntOr("n", -1);
		if (n < 0) {
			n = pilihSoal(player, t);
			t.putInt("n", n);
			simpan(player, t);
		}
		c.putString(LembarKunci.ID, Integer.toString(n));
		LembarKunci.teks(c, LembarKunci.Acara.SOAL, Component.translatable("kultivator.cerita.teka." + n + ".soal"));
		ListTag answers = new ListTag();
		for (int v : urutan(player, n, day)) {
			CompoundTag a = new CompoundTag();
			LembarKunci.teks(a, LembarKunci.TEKS, Component.translatable("kultivator.cerita.teka." + n + (v == 0 ? ".benar" : ".salah" + v)));
			answers.add(a);
		}
		c.put(LembarKunci.Acara.JAWABAN, answers);
		tag.put(LembarKunci.Acara.TEKA, c);
		tag.putInt(LembarKunci.SKOR, t.getIntOr("benar", 0));
	}

	@Override
	public void aksi(ServerPlayer player, long town, @Nullable KultivatorNpc npc, String[] op) {
		if (!LembarKunci.Acara.OP_JAWAB.equals(op[0]) || npc == null || npc.role() != NpcRole.PENJAGA_TEKA) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		long day = Kota.day(server);
		CompoundTag t = teka(player, day);
		int n = t.getIntOr("n", -1);
		if (n < 0 || LembarKunci.argInt(op, 1, -2) != n || t.getIntOr("ditanya", 0) >= TANYA_MAKS) {
			return;
		}
		int pick = LembarKunci.argInt(op, 2, -1);
		if (pick < 0 || pick > 2) {
			return;
		}
		boolean right = urutan(player, n, day)[pick] == 0;
		int[] sudah = t.getIntArray("sudah").orElse(new int[0]);
		int[] grown = java.util.Arrays.copyOf(sudah, sudah.length + 1);
		grown[sudah.length] = n;
		t.putIntArray("sudah", grown);
		t.putInt("ditanya", t.getIntOr("ditanya", 0) + 1);
		t.putInt("n", -1);
		Component pesan;
		if (right) {
			int benar = t.getIntOr("benar", 0) + 1;
			t.putInt("benar", benar);
			Barang.beri(player, BarangCerita.buat(player, "jimat_lentera", 1));
			if (benar <= NB_MAKS) {
				KotaData.get(server).addReputation(town, player.getUUID(), 1);
			}
			Voice.say(npc, "kotabesar.riddle_right", null);
			HidupApi.klip(npc, GerakId.TEPUK_TANGAN, 0, 40);
			pesan = Component.translatable(benar <= NB_MAKS ? "kultivator.kota_besar.festival.benar" : "kultivator.kota_besar.festival.benar_tanpa_nb");
		} else {
			HidupApi.klip(npc, GerakId.TERTAWA, 0, 50);
			npc.say(Component.translatable("kultivator.kota_besar.festival.tawa." + player.getRandom().nextInt(6)), 60);
			pesan = Component.translatable("kultivator.kota_besar.festival.salah",
					Component.translatable("kultivator.cerita.teka." + n + ".benar"));
		}
		simpan(player, t);
		KotaBesarKalender.segarkan(player, pesan, right);
	}
}
