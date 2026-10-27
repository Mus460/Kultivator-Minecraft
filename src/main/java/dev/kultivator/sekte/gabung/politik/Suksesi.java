package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The succession of DESIGN 41.8.5 (contract C41.D1, owner 41D): "Jubah dan Mangkuk", the robe and bowl the Fifth
 * Patriarch Hongren handed to Huineng (<i>Platform Sutra</i>). A three-chapter chain that starts once an Heir or Vice
 * Master has the realm {@code ranahKetua(t)} and {@code 15000 x kf} PK:
 * <ol>
 *   <li>{@link #BAB_UJIAN} "Ujian Terakhir Ketua": the NPC master's Duel Pedang at {@code kompleks/puncak} (stop at 10 %,
 *   at most 3 min); a loss may be retried every 7 days.</li>
 *   <li>{@link #BAB_SUARA} "Suara Puncak": every elder votes in the hall (Politik screen); NPCs vote by attitude, another
 *   player heir may object and duel.</li>
 *   <li>{@link #BAB_PENYERAHAN} "Penyerahan": a 90 s ceremony, the old master hands over the bowl (prop 153) and the robe
 *   flashes gold; the old master retires as "Leluhur Hidup".</li>
 * </ol>
 * Sibuk state {@code gabung.suksesi}. The rights of a player master (41.8.5 item 4) live here too: the weekly share
 * ({@code KasSekte}), the presence duty (at the compound once per {@link #HADIR_HARI} days or the sect wavers, -2
 * resources a day), the stand-in of the Vice Master after {@link #LURING_HARI} days away, and the abdication
 * ({@link #turunTakhta}).
 * <p>
 * State: the candidate's {@code Keanggotaan.bagian("politik")} ({@code bab}, {@code duelHari}, {@code ulangHari}) and the
 * sect's {@code politik} compound ({@code suksesi}: candidate, votes, end day; {@code leluhur}, {@code hadirHari}).
 */
public final class Suksesi {

	/** Not started (or not eligible). */
	public static final int BAB_BELUM = 0;
	public static final int BAB_UJIAN = 1;
	public static final int BAB_SUARA = 2;
	public static final int BAB_PENYERAHAN = 3;
	/** The chain is over: the player is the sect master. */
	public static final int BAB_SELESAI = 4;

	/** Days between two attempts at the master's duel. */
	public static final int JEDA_DUEL_HARI = 7;
	/** Seconds of the handing-over ceremony. */
	public static final int UPACARA_DETIK = 90;
	/** Days the player elders have to vote in chapter 2. */
	public static final int SUARA_HARI = 1;
	/** The master must visit the compound at least once per this many days. */
	public static final int HADIR_HARI = 10;
	/** Away this long, the Vice Master leads in his stead (the master is never deposed for it). */
	public static final int LURING_HARI = 30;
	/** Distance to the hall/peak spot that counts as "at the compound" (blocks). */
	public static final double JARAK_TEMPAT = 128.0D;

	private static final String KEY = "kultivator.gabung.politik.suksesi.";
	private static final int BEAT = 10;

	private record Upacara(UUID pemain, String sekte, long mulai, @Nullable DiscipleEntity ketua) {
	}

	private static final Map<UUID, Upacara> UPACARA = new HashMap<>();

	// ================================================================== chapter

	/** The chapter of the succession {@code player} is in ({@link #BAB_BELUM} .. {@link #BAB_SELESAI}). */
	public static int bab(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (k.status.anggota() && k.pangkat == Pangkat.KETUA) {
			return BAB_SELESAI;
		}
		return k.status.anggota() ? Math.max(BAB_BELUM, Math.min(BAB_PENYERAHAN, k.bagian("politik").getIntOr("bab", BAB_BELUM))) : BAB_BELUM;
	}

	private static void setBab(ServerPlayer player, int bab) {
		Keanggotaan k = GabungApi.anggota(player);
		k.bagian("politik").putInt("bab", bab);
		GabungData.get(player.level().getServer()).setDirty();
	}

	/** Why {@code player} may not walk the succession now, or null (41.8.5: Heir/Vice Master, master's realm, PK). */
	public static @Nullable Component syarat(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !Gabung.aktif()) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		if (!GabungConfig.get().bolehSuksesi) {
			return Component.translatable(KEY + "mati");
		}
		if (k.pangkat != Pangkat.PEWARIS && k.pangkat != Pangkat.WAKIL) {
			return Component.translatable(KEY + "pangkat", Pangkat.PEWARIS.nama(), Pangkat.WAKIL.nama());
		}
		GabungData.DataSekteGabung d = GabungData.get(server).sekte(sekte);
		if (d.ketuaPemain != null) {
			return Component.translatable(KEY + "ada_ketua", Kursi.namaPemegang(server, d.ketuaPemain.toString()));
		}
		String calon = d.bagian(DiplomasiGabung.BAGIAN).getCompoundOrEmpty("suksesi").getStringOr("calon", "");
		if (!calon.isEmpty() && !calon.equals(player.getUUID().toString()) && bab(server, calon) >= BAB_SUARA) {
			return Component.translatable(KEY + "ada_calon", Kursi.namaPemegang(server, calon));
		}
		Realm perlu = KelasSekte.ranahKetua(GabungApi.tier(sekte));
		if (Cultivators.get(player).realm().ordinal() < perlu.ordinal()) {
			return Component.translatable(KEY + "ranah", perlu.displayName());
		}
		int pk = biaya(sekte);
		if (k.pk < pk) {
			return Component.translatable(KEY + "pk", pk, k.pk);
		}
		return null;
	}

	private static int bab(MinecraftServer server, String uuid) {
		UUID id = Kursi.uuid(uuid);
		Keanggotaan k = id == null ? null : GabungData.get(server).peek(id);
		return k == null ? BAB_BELUM : k.bagian("politik").getIntOr("bab", BAB_BELUM);
	}

	/** The PK the robe and bowl cost: {@code Pangkat.KETUA.biaya() x kf x skalaBiaya} (15000 x kf). */
	public static int biaya(String sekte) {
		return GabungApi.biaya(sekte, Pangkat.KETUA.biaya());
	}

	/**
	 * The next step of {@code player}'s succession ({@code /kultivasi gabung suksesi}, the Politik screen): starts chapter 1,
	 * challenges the master, shows the vote, or begins the ceremony. Null when something happened, else why not.
	 */
	public static @Nullable Component lanjut(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		int bab = bab(player);
		if (bab == BAB_SELESAI) {
			return Component.translatable(KEY + "sudah_ketua");
		}
		Component why = syarat(player);
		if (why != null) {
			return why;
		}
		String sekte = GabungApi.sekte(player.getUUID());
		long hari = GabungApi.hari(server);
		CompoundTag mine = GabungApi.anggota(player).bagian("politik");
		switch (bab) {
			case BAB_BELUM -> {
				setBab(player, BAB_UJIAN);
				player.sendSystemMessage(Component.translatable(KEY + "bab1", SekteApi.diplomasi().master(sekte)).withStyle(ChatFormatting.GOLD));
				return null;
			}
			case BAB_UJIAN -> {
				long lalu = mine.getLongOr("duelHari", -100L);
				if (hari < lalu + JEDA_DUEL_HARI) {
					return Component.translatable(KEY + "jeda", lalu + JEDA_DUEL_HARI - hari);
				}
				BlockPos puncak = GabungApi.titik(sekte, "puncak");
				if (puncak != null && puncak.distSqr(player.blockPosition()) > 48.0D * 48.0D) {
					return Component.translatable(KEY + "ke_puncak", puncak.getX(), puncak.getY(), puncak.getZ());
				}
				String ketua = TokohSekte.idKetua(sekte);
				Component nama = SekteApi.diplomasi().master(sekte);
				why = Kursi.tandingTokoh(player, sekte, ketua, nama == null ? Component.literal("?") : nama,
						DiplomasiGabung.langkahKetua(server, sekte), 20 * 180, (p, menang, alasan, lawan) -> hasilDuel(p, sekte, menang));
				if (why == null) {
					mine.putLong("duelHari", hari);
					GabungData.get(server).setDirty();
					GabungApi.sibuk(player, Gabung.SIBUK_SUKSESI, 20 * 200);
				}
				return why;
			}
			case BAB_SUARA -> {
				CompoundTag s = sidang(server, sekte);
				if (!player.getUUID().toString().equals(s.getStringOr("calon", ""))) {
					long ulang = mine.getLongOr("ulangHari", -100L);
					if (hari < ulang) {
						return Component.translatable(KEY + "jeda", ulang - hari);
					}
					bukaSidang(server, player, sekte);
					return null;
				}
				return Component.translatable(KEY + "sidang_jalan", Math.max(0L, s.getLongOr("sampai", hari) - hari), hitung(server, sekte)[0],
						hitung(server, sekte)[1]);
			}
			case BAB_PENYERAHAN -> {
				return mulaiUpacara(player, sekte);
			}
			default -> {
				return null;
			}
		}
	}

	private static void hasilDuel(ServerPlayer player, String sekte, boolean menang) {
		GabungApi.lepasSibuk(player, Gabung.SIBUK_SUKSESI);
		if (!menang) {
			player.sendSystemMessage(Component.translatable(KEY + "duel_kalah", JEDA_DUEL_HARI).withStyle(ChatFormatting.RED));
			return;
		}
		setBab(player, BAB_SUARA);
		player.sendSystemMessage(Component.translatable(KEY + "duel_menang").withStyle(ChatFormatting.GOLD));
		bukaSidang(player.level().getServer(), player, sekte);
	}

	// ================================================================== chapter 2: the vote of the peak

	private static CompoundTag sidang(MinecraftServer server, String sekte) {
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		CompoundTag s = b.getCompoundOrEmpty("suksesi");
		if (!b.contains("suksesi")) {
			b.put("suksesi", s);
		}
		return s;
	}

	/** Opens the vote: NPC elders vote at once by their attitude toward the candidate (at least 20 = yes). */
	private static void bukaSidang(MinecraftServer server, ServerPlayer player, String sekte) {
		CompoundTag s = sidang(server, sekte);
		s.putString("calon", player.getUUID().toString());
		s.putLong("sampai", GabungApi.hari(server) + SUARA_HARI);
		s.remove("penentang");
		CompoundTag suara = new CompoundTag();
		for (TokohSekte.Tokoh e : TokohSekte.tetua(server, sekte)) {
			suara.putBoolean(e.id(), TokohSekte.sikap(server, e.id(), player.getUUID()) >= 20);
		}
		s.put("suara", suara);
		GabungData.get(server).setDirty();
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "sidang", player.getName()), ChatFormatting.GOLD);
	}

	/** Yes and no votes of the running vote. */
	public static int[] hitung(MinecraftServer server, String sekte) {
		CompoundTag suara = sidang(server, sekte).getCompoundOrEmpty("suara");
		int ya = 0;
		int tidak = 0;
		for (String key : suara.keySet()) {
			if (suara.getBooleanOr(key, false)) {
				ya++;
			} else {
				tidak++;
			}
		}
		return new int[]{ya, tidak};
	}

	/** Voters and their choice (true = yes); secret on the screen until the result. */
	public static Map<String, Boolean> suara(MinecraftServer server, String sekte) {
		Map<String, Boolean> out = new java.util.LinkedHashMap<>();
		CompoundTag suara = sidang(server, sekte).getCompoundOrEmpty("suara");
		for (String key : suara.keySet()) {
			out.put(key, suara.getBooleanOr(key, false));
		}
		return out;
	}

	/** A player elder votes yes/no on the running succession; null when counted. */
	public static @Nullable Component pilih(ServerPlayer player, boolean ya) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag s = sekte == null ? null : sidang(server, sekte);
		if (s == null || s.getStringOr("calon", "").isEmpty()) {
			return Component.translatable(KEY + "tak_ada_sidang");
		}
		if (!k.pangkat.bersuara()) {
			return Component.translatable(KEY + "tak_bersuara");
		}
		if (s.getStringOr("calon", "").equals(player.getUUID().toString())) {
			return Component.translatable(KEY + "diri_sendiri");
		}
		CompoundTag suara = s.getCompoundOrEmpty("suara");
		suara.putBoolean(player.getUUID().toString(), ya);
		s.put("suara", suara);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + (ya ? "pilih_ya" : "pilih_tidak")).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/**
	 * Another player Heir or Vice Master objects to the candidate: a deciding Sword Duel. The objector winning ends this
	 * vote (the candidate may try again in 7 days); losing changes nothing. Null when the duel invite went out.
	 */
	public static @Nullable Component tentang(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag s = sekte == null ? null : sidang(server, sekte);
		if (s == null || s.getStringOr("calon", "").isEmpty()) {
			return Component.translatable(KEY + "tak_ada_sidang");
		}
		if (k.pangkat != Pangkat.PEWARIS && k.pangkat != Pangkat.WAKIL) {
			return Component.translatable(KEY + "tentang_pangkat");
		}
		UUID calon = Kursi.uuid(s.getStringOr("calon", ""));
		ServerPlayer lawan = calon == null ? null : server.getPlayerList().getPlayer(calon);
		if (lawan == null || lawan == player) {
			return Component.translatable(KEY + "calon_luring");
		}
		s.putString("penentang", player.getUUID().toString());
		GabungData.get(server).setDirty();
		return Kursi.tandingPemain(player, lawan, (p, menang, alasan, other) -> {
			if (menang) {
				gagalSidang(server, sekte, lawan, "tentang");
			}
		});
	}

	private static void tutupSidang(MinecraftServer server, String sekte) {
		CompoundTag s = sidang(server, sekte);
		UUID calon = Kursi.uuid(s.getStringOr("calon", ""));
		ServerPlayer p = calon == null ? null : server.getPlayerList().getPlayer(calon);
		int[] h = hitung(server, sekte);
		boolean lulus = h[0] * 2 >= h[0] + h[1];
		if (p == null) {
			// the candidate is away: the vote waits for him (counted when he is back)
			return;
		}
		if (lulus) {
			s.putString("calon", "");
			setBab(p, BAB_PENYERAHAN);
			Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "sidang_lulus", p.getName(), h[0], h[1]), ChatFormatting.GOLD);
		} else {
			gagalSidang(server, sekte, p, "suara");
		}
		GabungData.get(server).setDirty();
	}

	private static void gagalSidang(MinecraftServer server, String sekte, ServerPlayer calon, String sebab) {
		CompoundTag s = sidang(server, sekte);
		int[] h = hitung(server, sekte);
		s.putString("calon", "");
		CompoundTag mine = GabungApi.anggota(calon).bagian("politik");
		mine.putLong("ulangHari", GabungApi.hari(server) + JEDA_DUEL_HARI);
		GabungData.get(server).setDirty();
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "sidang_gagal." + sebab, calon.getName(), h[0], h[1]), ChatFormatting.RED);
	}

	// ================================================================== chapter 3: the handing over

	private static @Nullable Component mulaiUpacara(ServerPlayer player, String sekte) {
		if (UPACARA.containsKey(player.getUUID())) {
			return Component.translatable(KEY + "upacara_jalan");
		}
		BlockPos aula = GabungApi.titik(sekte, "aula");
		if (aula != null && aula.distSqr(player.blockPosition()) > 32.0D * 32.0D) {
			return Component.translatable(KEY + "ke_aula", aula.getX(), aula.getY(), aula.getZ());
		}
		String busy = Sibuk.why(player);
		if (busy != null) {
			return Sibuk.text(busy);
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		Vec3 depan = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(3.0D);
		BlockPos spot = DiplomasiGabung.tanah(level, BlockPos.containing(player.getX() + depan.x, player.getY(), player.getZ() + depan.z));
		Component nama = SekteApi.diplomasi().master(sekte);
		DiscipleEntity ketua = DiplomasiGabung.tubuh(level, Vec3.atBottomCenterOf(spot), sekte, DiplomasiGabung.langkahKetua(server, sekte), 98,
				nama == null ? Component.literal("?") : nama, false);
		if (ketua != null) {
			ketua.getLookControl().setLookAt(player);
			// presides standing, hands behind the back (the bowl gesture of beat 6 plays over it)
			SosokApi.gerak(ketua, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0);
		}
		UPACARA.put(player.getUUID(), new Upacara(player.getUUID(), sekte, level.getGameTime(), ketua));
		GabungApi.sibuk(player, Gabung.SIBUK_SUKSESI, 20 * (UPACARA_DETIK + 10));
		return null;
	}

	/** Once a second: the ceremony beats (every 10 s a line, the bowl at 60 s, the robe at 80 s, the end at 90 s). */
	static void detik(MinecraftServer server) {
		if (UPACARA.isEmpty()) {
			return;
		}
		for (Upacara u : List.copyOf(UPACARA.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(u.pemain());
			if (player == null || !player.isAlive()) {
				batalUpacara(u);
				continue;
			}
			long detik = (player.level().getGameTime() - u.mulai()) / 20L;
			if (detik % BEAT != 0) {
				continue;
			}
			int beat = (int) (detik / BEAT);
			ServerLevel level = player.level();
			Component line = Component.translatable(KEY + "upacara." + Math.min(9, beat), player.getName(),
					DiplomasiGabung.nama(server, u.sekte()));
			for (ServerPlayer near : level.players()) {
				if (near.distanceToSqr(player) < 48.0D * 48.0D) {
					near.sendSystemMessage(line.copy().withStyle(beat >= 9 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
				}
			}
			level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, beat >= 9 ? 0.7F : 1.0F);
			switch (beat) {
				case 0 -> GabungPayloads.kirimKlip(player, GabungGerakId.SUMPAH_BERLUTUT, 0);
				case 1 -> kowtow(level, player);
				case 3 -> GabungPayloads.kirimKlip(player, GabungGerakId.DUPA_TIGA, GabungGerakId.PROP_DUPA_SUMPAH);
				case 6 -> {
					if (u.ketua() != null) {
						SosokApi.gerak(u.ketua(), GabungGerakId.SERAH_JUBAH_MANGKUK, 0, 20 * 6, GabungGerakId.PROP_MANGKUK_KETUA, 0, 0);
					}
					GabungPayloads.kirimKlip(player, GabungGerakId.TERIMA_JUBAH_MANGKUK, GabungGerakId.PROP_MANGKUK_KETUA);
				}
				case 8 -> {
					level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 60, 0.6D, 1.0D, 0.6D, 0.25D);
					level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2D, player.getZ(), 24, 0.4D, 0.8D, 0.4D, 0.02D);
					kowtow(level, player);
				}
				case 9 -> {
					UPACARA.remove(u.pemain());
					GabungPayloads.kirimKlip(player, 0, 0);
					GabungApi.lepasSibuk(player, Gabung.SIBUK_SUKSESI);
					if (u.ketua() != null) {
						DiplomasiGabung.lenyap(u.ketua());
					}
					jadikanKetua(player, u.sekte(), true);
				}
				default -> {
				}
			}
		}
	}

	/** The sect's people nearby bow (clip 302 on every sect body within 24 blocks). */
	private static void kowtow(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(24.0D);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, box, n -> sekteNpc(n.role()))) {
			SosokApi.gerak(npc, GabungGerakId.KOWTOW, 0, 20 * 4);
			npc.getLookControl().setLookAt(player);
		}
	}

	private static boolean sekteNpc(NpcRole role) {
		return role == NpcRole.MURID_SEKTE_LUAR || role == NpcRole.MURID_SEKTE_DALAM || role == NpcRole.PELAYAN_SEKTE
				|| role == NpcRole.TETUA_SEKTE || role == NpcRole.JUARA_SEKTE;
	}

	private static void batalUpacara(Upacara u) {
		UPACARA.remove(u.pemain());
		if (u.ketua() != null) {
			DiplomasiGabung.lenyap(u.ketua());
		}
	}

	/**
	 * The player becomes the sect master (end of chapter 3, or {@code uji gabung ketua}): the robe and bowl's PK when
	 * {@code bayar}, the seat, the old master retires as "Leluhur Hidup", the branch pact for his hall,
	 * {@code GabungBus.KETUA_BARU}, the rumour.
	 */
	public static void jadikanKetua(ServerPlayer player, String sekte, boolean bayar) {
		MinecraftServer server = player.level().getServer();
		if (bayar) {
			GabungApi.bayarPk(player, Math.min(GabungApi.anggota(player).pk, biaya(sekte)), "gabung.jubah_mangkuk");
		}
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		CompoundTag b = d.bagian(DiplomasiGabung.BAGIAN);
		if (d.ketuaPemain == null) {
			b.putString("leluhur", TokohSekte.idKetua(sekte));
			b.putLong("leluhurHari", GabungApi.hari(server));
		}
		d.ketuaPemain = player.getUUID();
		b.putLong("hadirHari", GabungApi.hari(server));
		setBab(player, BAB_SELESAI);
		data.setDirty();
		GabungApi.setPangkat(player, Pangkat.KETUA, "gabung.suksesi");
		if (Halls.hallOf(player.getUUID()) != null) {
			DiplomasiGabung.paktaCabang(player.getUUID(), sekte);
		}
		Politik.kabar(server, "gabung.ketua_baru", player.getName(), DiplomasiGabung.nama(server, sekte));
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "ketua_baru", player.getName(), DiplomasiGabung.nama(server, sekte)),
				ChatFormatting.GOLD);
		GabungBus.KETUA_BARU.invoker().onKetuaBaru(server, sekte, player.getUUID(), "");
	}

	/** The former NPC master now "Leluhur Hidup" (meditation x1.05 at the compound, 41C reads it), or null. */
	public static @Nullable String leluhurHidup(MinecraftServer server, String sekte) {
		String id = server == null ? "" : DiplomasiGabung.bagian(server, sekte).getStringOr("leluhur", "");
		return id.isEmpty() ? null : id;
	}

	// ================================================================== abdication and the master's duty

	/**
	 * The player master steps down (41.8.5 "Turun takhta"): {@code pengganti} a member player (online) takes the seat with
	 * a short handing-over, else the Grand Elder (an NPC) leads again. The former master stays an Elder. An absorbed sect is
	 * released. Null when done, else why not.
	 */
	public static @Nullable Component turunTakhta(ServerPlayer player, @Nullable ServerPlayer pengganti) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || k.pangkat != Pangkat.KETUA) {
			return Component.translatable(KEY + "bukan_ketua");
		}
		if (pengganti != null && (!GabungApi.anggota(pengganti.getUUID(), sekte) || pengganti == player)) {
			return Component.translatable(KEY + "pengganti_salah");
		}
		lepasTakhta(server, player.getUUID(), sekte);
		GabungApi.setPangkat(player, Pangkat.TETUA, "gabung.turun_takhta");
		if (pengganti != null) {
			GabungData.get(server).sekte(sekte).ketuaPemain = null;
			jadikanKetua(pengganti, sekte, false);
		}
		return null;
	}

	/** Clears the player master of {@code sekte} (abdication, leaving): the NPC side leads again, absorb released. */
	private static void lepasTakhta(MinecraftServer server, UUID uuid, String sekte) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		if (!uuid.equals(d.ketuaPemain)) {
			return;
		}
		d.ketuaPemain = null;
		data.setDirty();
		if (d.lebur) {
			Penyatuan.pisah(server, sekte, uuid);
		}
		String npc = TokohSekte.idKetua(sekte);
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "turun", Kursi.namaPemegang(server, uuid.toString())), ChatFormatting.YELLOW);
		GabungBus.KETUA_BARU.invoker().onKetuaBaru(server, sekte, null, npc);
	}

	/** True while the player master has been away over {@link #LURING_HARI} days (the Vice Master leads meanwhile). */
	public static boolean wakilMemimpin(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d == null || d.ketuaPemain == null) {
			return false;
		}
		long hadir = d.bagian(DiplomasiGabung.BAGIAN).getLongOr("hadirHari", GabungApi.hari(server));
		return GabungApi.hari(server) - hadir > LURING_HARI;
	}

	/** Once a minute per online master: at the compound counts as present today. */
	static void periksaHadir(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Keanggotaan k = GabungData.get(server).peek(p.getUUID());
			if (k == null || !k.status.anggota() || k.pangkat != Pangkat.KETUA) {
				continue;
			}
			BlockPos aula = GabungApi.titik(k.sekte, "aula");
			if (aula != null && p.level() == server.overworld() && aula.distSqr(p.blockPosition()) < JARAK_TEMPAT * JARAK_TEMPAT) {
				DiplomasiGabung.bagian(server, k.sekte).putLong("hadirHari", GabungApi.hari(server));
				GabungData.get(server).setDirty();
			}
		}
	}

	/** Daily: the vote's end, the master's presence duty (a wavering sect loses 2 resources a day). */
	static void harian(MinecraftServer server, String sekte, long hari) {
		CompoundTag s = sidang(server, sekte);
		if (!s.getStringOr("calon", "").isEmpty() && hari >= s.getLongOr("sampai", hari)) {
			tutupSidang(server, sekte);
		}
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d != null && d.ketuaPemain != null) {
			long hadir = d.bagian(DiplomasiGabung.BAGIAN).getLongOr("hadirHari", hari);
			if (hari - hadir > HADIR_HARI) {
				DiplomasiGabung.ubahSumber(server, sekte, -2, DiplomasiGabung.SEBAB_GOYAH);
			}
		}
	}

	/** Registers the listeners once (from {@code Politik.init}). */
	static void init() {
		GabungBus.KELUAR.register((player, sekte, status) -> {
			MinecraftServer server = player.level().getServer();
			if (server != null) {
				lepasTakhta(server, player.getUUID(), sekte);
				GabungApi.anggota(player).bagian("politik").putInt("bab", BAB_BELUM);
				CompoundTag s = sidang(server, sekte);
				if (player.getUUID().toString().equals(s.getStringOr("calon", ""))) {
					s.putString("calon", "");
				}
				Upacara u = UPACARA.get(player.getUUID());
				if (u != null) {
					batalUpacara(u);
				}
			}
		});
	}

	/** Forces chapter {@code bab} for the tester ({@code uji gabung suksesi bab <n>}). */
	static void paksaBab(ServerPlayer player, int bab) {
		setBab(player, Math.max(BAB_BELUM, Math.min(BAB_PENYERAHAN, bab)));
		if (bab == BAB_SUARA) {
			bukaSidang(player.level().getServer(), player, GabungApi.sekte(player.getUUID()));
		}
	}

	/** Ends a running vote now ({@code uji gabung suksesi sidang}). */
	static void tutup(MinecraftServer server, String sekte) {
		tutupSidang(server, sekte);
	}

	static void clear() {
		for (Upacara u : UPACARA.values()) {
			if (u.ketua() != null) {
				u.ketua().discard();
			}
		}
		UPACARA.clear();
	}

	private Suksesi() {
	}
}
