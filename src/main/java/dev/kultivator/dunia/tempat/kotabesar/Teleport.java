package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Teleportation Formation Station and the Sky Dock (DESIGN 38.B.4.3, 38.T.14.3).
 * <ul>
 * <li><b>Station</b> ({@code kotabesar/cakram} disc, the Formation Keeper {@link NpcRole#PENJAGA_FORMASI}; a player
 * crouching on any disc opens it too, so road posts and themed cities with a disc are served without code of their own):
 * destinations are the places the player knows ({@link DuniaSites#known}) of the kinds in {@link #JENIS} — towns,
 * capitals, great and clan villages, great posts, NPC sect compounds while the stance is at least Netral, and the five
 * themed cities. Fare {@code ceil(2 + distance / 250)} NB per head, at most 120; following disciples within 8 blocks
 * come along (at most 4) for the same fare each; a Hero of the City rides once a day for free; a wanted player is
 * refused. A three second channel ({@link Sibuk} {@code tempat.teleport}; a blow or a step off the disc breaks it), the
 * Formation Pillar on clients, then the move to {@link TempatApi#arrival} (never the heightmap of a town).</li>
 * <li><b>Sky Dock</b> ({@code kotabesar/dermaga}, the Sky Captain {@link NpcRole#NAKHODA}): in daylight (1000-11000)
 * any known destination at least 600 blocks away and the docks of allied sky palaces (37), fare {@code ceil(6 +
 * distance / 150)} NB, a six second cloud-boat scene (skippable on the client) and the same move.</li>
 * <li>Every capital a player knows is also a destination of the player's own flying ship ({@code DuniaApi.routes()},
 * {@code kota_besar_<index>}), landing at the Sky Dock.</li>
 * </ul>
 */
public final class Teleport {

	public static final List<String> JENIS = List.of("kota/kota", "kota/kota_besar", "kampung/desa_besar", "kampung/klan", "pos/singgah_besar",
			"sekte/kompleks", "kota_tema/pelabuhan", "kota_tema/oasis", "kota_tema/air", "kota_tema/gunung", "kota_tema/iblis");
	public static final int TARIF_MAKS = 120;
	public static final long KANAL = 60L;
	public static final long AWAN = 120L;
	public static final int PENGIKUT_MAKS = 4;
	public static final double PENGIKUT_JARAK = 8.0D;
	public static final int AWAN_JARAK_MIN = 600;
	public static final long DERMAGA_BUKA = 1000L;
	public static final long DERMAGA_TUTUP = 11000L;
	private static final double CAKRAM_JARAK = 3.5D;

	private record Kanal(ServerLevel level, Vec3 dari, long siteId, BlockPos tiba, ResourceKey<Level> dimensi, long selesai, boolean awan,
			List<UUID> murid) {
	}

	private static final Map<UUID, Kanal> KANAL_AKTIF = new HashMap<>();
	private static final Map<UUID, Long> JONGKOK = new HashMap<>();
	private static final Set<String> RUTE = new HashSet<>();

	static void init() {
		Sibuk.register("tempat.teleport", p -> KANAL_AKTIF.containsKey(p.getUUID()));
		TempatLayar.daftar(LembarKunci.Teleport.LAYAR, (p, npcId, op) -> aksi(p, npcId, op, false), (p, npcId) -> bangun(p, npcId, false));
		TempatLayar.daftar(LembarKunci.Dermaga.LAYAR, (p, npcId, op) -> aksi(p, npcId, op, true), (p, npcId) -> bangun(p, npcId, true));
		dev.kultivator.dunia.tempat.cerita.CeritaApi.daftarJasa(Teleport::jasa);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer p) {
				Kanal k = KANAL_AKTIF.remove(p.getUUID());
				if (k != null) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_besar.teleport.batal").withStyle(ChatFormatting.RED));
				}
			}
		});
	}

	static void lupakan() {
		KANAL_AKTIF.clear();
		JONGKOK.clear();
		RUTE.clear();
	}

	static void keluar(UUID id) {
		KANAL_AKTIF.remove(id);
		JONGKOK.remove(id);
	}

	// ================================================================== destinations

	/** One row of the station or dock. */
	private record Tujuan(DuniaSites.Site site, int jarak, long harga, @Nullable Component alasan, boolean langit) {
	}

	static long tarif(int jarak, boolean awan) {
		return awan ? (long) Math.ceil(6.0D + jarak / 150.0D) : Math.min(TARIF_MAKS, (long) Math.ceil(2.0D + jarak / 250.0D));
	}

	private static List<Tujuan> tujuan(ServerPlayer p, boolean awan) {
		List<Tujuan> out = new ArrayList<>();
		DiplomasiService d = SekteApi.diplomasi();
		for (DuniaSites.Site site : DuniaSites.known(p)) {
			boolean langit = awan && LangitNpcPlanSite.adalah(site);
			if (!JENIS.contains(site.kind()) && !langit) {
				continue;
			}
			if (site.dim() != p.level().dimension()) {
				continue;
			}
			int jarak = (int) Math.round(Math.hypot(site.pos().getX() - p.getX(), site.pos().getZ() - p.getZ()));
			if (jarak < 160 || awan && jarak < AWAN_JARAK_MIN) {
				continue;
			}
			Component why = null;
			if ("sekte/kompleks".equals(site.kind())) {
				String sect = KompleksApi.sekteDi(site.id());
				if (sect != null && d.stance(p.getUUID(), sect).ordinal() < Sikap.NETRAL.ordinal()) {
					why = Component.translatable("kultivator.kota_besar.teleport.ditolak_sekte");
				}
			}
			if (langit) {
				String sect = LangitNpcPlanSite.sekte(p.level(), site);
				if (sect == null || d.stance(p.getUUID(), sect).ordinal() < Sikap.RAMAH.ordinal()) {
					continue;
				}
			}
			out.add(new Tujuan(site, jarak, tarif(jarak, awan), why, langit));
		}
		out.sort((a, b) -> Integer.compare(a.jarak(), b.jarak()));
		return out.size() > LembarKunci.DAFTAR_MAKS ? out.subList(0, LembarKunci.DAFTAR_MAKS) : out;
	}

	/** Sky palace docks of 37 ({@code sekte/istana_langit}) and their sect. */
	private static final class LangitNpcPlanSite {
		static boolean adalah(DuniaSites.Site site) {
			return "sekte/istana_langit".equals(site.kind());
		}

		static @Nullable String sekte(ServerLevel level, DuniaSites.Site site) {
			LangitNpcPlan.Istana istana = LangitNpcPlan.near(level.getSeed(), site.pos().getX(), site.pos().getZ(), 256.0D);
			return istana == null ? null : LangitNpcPlan.sectId(istana);
		}

		private LangitNpcPlanSite() {
		}
	}

	private static List<DiscipleEntity> pengikut(ServerPlayer p) {
		List<DiscipleEntity> out = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(p.getUUID())) {
			if (out.size() >= PENGIKUT_MAKS) {
				break;
			}
			if (d.isAlive() && d.level() == p.level() && d.distanceTo(p) <= PENGIKUT_JARAK
					&& (d.mode() == DiscipleEntity.Mode.FOLLOW || d.mode() == DiscipleEntity.Mode.ASSIST)) {
				out.add(d);
			}
		}
		return out;
	}

	/** Free rides left today: one for a Hero of the City of the station's capital. */
	private static int gratis(ServerPlayer p, long town) {
		if (town == 0L || KotaBesarApi.pangkatNamaBaik(p, town) < LembarKunci.PANGKAT_PAHLAWAN) {
			return 0;
		}
		long day = Kota.day(p.level().getServer());
		return KotaBesarData.get(p.level().getServer()).pemain(p.getUUID()).getLongOr("gratis", Long.MIN_VALUE) == day ? 0 : 1;
	}

	private static long townDari(ServerPlayer p, int npcId) {
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc) {
			return npc.town();
		}
		long t = Towns.within(p.level(), p.blockPosition());
		return t;
	}

	// ================================================================== sheets

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId, boolean awan) {
		long town = townDari(p, npcId);
		CompoundTag tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_GIOK, LembarKunci.DISTRIK_AWAN);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(awan ? "kultivator.kota_besar.dermaga.judul" : "kultivator.kota_besar.teleport.judul"));
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc) {
			tag.put(LembarKunci.NPC, KotaBesarTampil.kartu(npc));
		}
		boolean wanted = Hukum.ibuKota(p.level().getServer(), town) && Hukum.buronan(p, town);
		long t = Kota.timeOfDay(p.level().getServer());
		boolean open = !awan || t >= DERMAGA_BUKA && t < DERMAGA_TUTUP;
		ListTag rows = new ListTag();
		List<DiscipleEntity> follow = pengikut(p);
		int free = awan ? 0 : gratis(p, town);
		for (Tujuan dest : tujuan(p, awan)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Long.toString(dest.site().id()));
			LembarKunci.teks(row, LembarKunci.NAMA, namaSitus(p.level().getServer(), dest.site()));
			row.putString(LembarKunci.JENIS, dest.site().kind());
			row.putInt(LembarKunci.IKON, LembarKunci.Ikon.TELEPORT);
			row.putInt(LembarKunci.JARAK, dest.jarak());
			row.putLong(LembarKunci.HARGA, free > 0 ? 0L : dest.harga());
			row.putInt(LembarKunci.X, dest.site().pos().getX());
			row.putInt(LembarKunci.Z, dest.site().pos().getZ());
			if (awan) {
				row.putInt(LembarKunci.Dermaga.DURASI, (int) (AWAN / 20L));
				row.putBoolean(LembarKunci.Dermaga.LANGIT, dest.langit());
			}
			Component why = wanted ? Component.translatable("kultivator.kota_besar.teleport.ditolak_buronan") : !open
					? Component.translatable("kultivator.kota_besar.dermaga.tutup", KotaBesarKalender.jam(DERMAGA_BUKA), KotaBesarKalender.jam(DERMAGA_TUTUP))
					: dest.alasan();
			if (why != null) {
				row.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(row, LembarKunci.ALASAN, why);
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_DITOLAK);
			} else {
				long total = (free > 0 ? 0L : dest.harga()) + dest.harga() * follow.size();
				row.putString(LembarKunci.KEADAAN, dev.kultivator.dunia.kota.NilaiBatu.total(p) < total ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK);
			}
			rows.add(row);
		}
		tag.put(LembarKunci.TUJUAN, rows);
		if (rows.isEmpty()) {
			CompoundTag empty = new CompoundTag();
			LembarKunci.teks(empty, LembarKunci.TEKS, Component.translatable(awan ? "kultivator.kota_besar.dermaga.kosong" : "kultivator.kota_besar.teleport.kosong"));
			tag.put(LembarKunci.KOSONG, empty);
		}
		tag.putInt(LembarKunci.Teleport.PENGIKUT, follow.size());
		if (!awan) {
			tag.putInt(LembarKunci.Teleport.GRATIS_SISA, free);
			tag.putBoolean(LembarKunci.Teleport.BURONAN, wanted);
			tag.putInt(LembarKunci.Teleport.KANAL_DETIK, (int) (KANAL / 20L));
		} else {
			tag.putBoolean(LembarKunci.Dermaga.BUKA, open);
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable(open ? "kultivator.kota_besar.dermaga.jam"
					: "kultivator.kota_besar.dermaga.tutup", KotaBesarKalender.jam(DERMAGA_BUKA), KotaBesarKalender.jam(DERMAGA_TUTUP)));
			tag.putString(LembarKunci.ENTITAS, "kultivator:kapal_roh");
		}
		if (follow.size() > 0) {
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota_besar.teleport.pengikut", follow.size()));
		}
		return tag;
	}

	static Component namaSitus(MinecraftServer server, DuniaSites.Site site) {
		if ("kota/kota_besar".equals(site.kind())) {
			for (Kandidat k : RencanaKotaBesar.of(server.overworld().getSeed()).kandidat()) {
				if (KotaBesarKota.situs(k) == site.id()) {
					return KotaBesarKota.nama(server, k);
				}
			}
		}
		if ("kota/kota".equals(site.kind())) {
			return dev.kultivator.dunia.kota.KotaNames.town(site.id());
		}
		return Component.translatable("kultivator.dunia.marker." + site.kind().replace('/', '.'));
	}

	// ================================================================== departing

	private static void aksi(ServerPlayer p, int npcId, String op, boolean awan) {
		String[] parts = LembarKunci.urai(op);
		if (!LembarKunci.Teleport.OP_BERANGKAT.equals(parts[0])) {
			return;
		}
		long id = LembarKunci.argLong(parts, 1, 0L);
		long town = townDari(p, npcId);
		MinecraftServer server = p.level().getServer();
		String layar = awan ? LembarKunci.Dermaga.LAYAR : LembarKunci.Teleport.LAYAR;
		Tujuan dest = null;
		for (Tujuan t : tujuan(p, awan)) {
			if (t.site().id() == id) {
				dest = t;
			}
		}
		Component fail = null;
		if (dest == null) {
			fail = Component.translatable("kultivator.kota_besar.teleport.tak_dikenal");
		} else if (dest.alasan() != null) {
			fail = dest.alasan();
		} else if (Hukum.ibuKota(server, town) && Hukum.buronan(p, town)) {
			fail = Component.translatable("kultivator.kota_besar.teleport.ditolak_buronan");
		} else if (awan && (Kota.timeOfDay(server) < DERMAGA_BUKA || Kota.timeOfDay(server) >= DERMAGA_TUTUP)) {
			fail = Component.translatable("kultivator.kota_besar.dermaga.tutup", KotaBesarKalender.jam(DERMAGA_BUKA), KotaBesarKalender.jam(DERMAGA_TUTUP));
		} else if (Sibuk.whyExcept(p, "tempat.teleport") != null) {
			fail = Sibuk.text(Sibuk.whyExcept(p, "tempat.teleport"));
		} else if (Hukum.diSel(p)) {
			fail = Component.translatable("kultivator.sibuk.tempat.sel");
		}
		BlockPos arrive = dest == null ? null : tiba(server, dest);
		if (fail == null && arrive == null) {
			fail = Component.translatable("kultivator.kota_besar.teleport.tak_ada_titik");
		}
		if (fail != null) {
			balas(p, npcId, layar, awan, fail, false);
			return;
		}
		List<DiscipleEntity> follow = pengikut(p);
		int free = awan ? 0 : gratis(p, town);
		long total = (free > 0 ? 0L : dest.harga()) + dest.harga() * follow.size();
		if (!EkonomiApi.bayar(p, total, awan ? "kotabesar.dermaga" : "kotabesar.teleport")) {
			balas(p, npcId, layar, awan, Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(total)), false);
			return;
		}
		if (free > 0) {
			KotaBesarData data = KotaBesarData.get(server);
			data.pemain(p.getUUID()).putLong("gratis", Kota.day(server));
			data.setDirty();
		}
		if (Hukum.ibuKota(server, town)) {
			dev.kultivator.dunia.tempat.ekonomi.KasKota.tambah(server, town, Math.max(0L, total / 10L));
		}
		ServerLevel to = server.getLevel(dest.site().dim());
		if (to == null) {
			return;
		}
		DuniaTickets.touch(to, ChunkPos.containing(arrive), 1);
		List<UUID> ids = new ArrayList<>();
		for (DiscipleEntity d : follow) {
			ids.add(d.getUUID());
		}
		long now = p.level().getGameTime();
		KANAL_AKTIF.put(p.getUUID(), new Kanal(p.level(), p.position(), dest.site().id(), arrive, to.dimension(),
				now + (awan ? AWAN : KANAL), awan, ids));
		TempatLayar.tutup(p);
		CompoundTag pillar = new CompoundTag();
		pillar.putDouble("x", p.getX());
		pillar.putDouble("y", p.getY());
		pillar.putDouble("z", p.getZ());
		pillar.putInt("detik", (int) ((awan ? AWAN : KANAL) / 20L));
		pillar.putString("jenis", awan ? "awan" : "cakram");
		pillar.putBoolean("tiba", false);
		pillar.putInt("pemain", p.getId());
		KotaBesarPayload.sekitar(p.level(), p.position(), "teleport", pillar);
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc keeper) {
			Voice.say(keeper, awan ? "kotabesar.dock_depart" : "kotabesar.teleport_ready", null);
		}
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, awan ? 0.7F : 1.4F);
		Msg.actionBar(p, Component.translatable(awan ? "kultivator.kota_besar.dermaga.naik" : "kultivator.kota_besar.teleport.kanal",
				namaSitus(server, dest.site())).withStyle(ChatFormatting.AQUA));
	}

	private static void balas(ServerPlayer p, int npcId, String layar, boolean awan, Component pesan, boolean ok) {
		CompoundTag tag = bangun(p, npcId, awan);
		if (tag != null) {
			KotaBesarTampil.pesan(tag, pesan, ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
			TempatLayar.segarkan(p, layar, npcId, tag);
		}
		Msg.actionBar(p, pesan.copy().withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** Where a traveller lands: the arrival rule of 38W, else a safe column at the site (an ordinary town's square). */
	private static @Nullable BlockPos tiba(MinecraftServer server, Tujuan dest) {
		DuniaSites.Site site = dest.site();
		BlockPos a = TempatApi.arrival(site);
		if (a != null) {
			return a;
		}
		ServerLevel level = server.getLevel(site.dim());
		if (level == null) {
			return null;
		}
		if (dest.langit()) {
			return site.pos().above();
		}
		BlockPos centre = "kota/kota".equals(site.kind()) ? Towns.centre(server, site.id()) : null;
		BlockPos at = centre != null ? centre : site.pos();
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		return new BlockPos(at.getX(), Math.max(y, level.getMinY() + 1), at.getZ());
	}

	// ================================================================== tick (every tick)

	static void tick(MinecraftServer server) {
		if (KANAL_AKTIF.isEmpty()) {
			return;
		}
		for (UUID id : List.copyOf(KANAL_AKTIF.keySet())) {
			Kanal k = KANAL_AKTIF.get(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || k == null) {
				KANAL_AKTIF.remove(id);
				continue;
			}
			if (p.level() != k.level() || p.position().distanceToSqr(k.dari()) > 2.25D) {
				KANAL_AKTIF.remove(id);
				Msg.actionBar(p, Component.translatable("kultivator.kota_besar.teleport.batal").withStyle(ChatFormatting.RED));
				continue;
			}
			if (k.level().getGameTime() < k.selesai()) {
				continue;
			}
			KANAL_AKTIF.remove(id);
			pindah(server, p, k);
		}
	}

	private static void pindah(MinecraftServer server, ServerPlayer p, Kanal k) {
		ServerLevel to = server.getLevel(k.dimensi());
		if (to == null) {
			return;
		}
		BlockPos at = k.tiba();
		JamMalam.abaikan(p, 200L);
		if (to == p.level()) {
			p.teleportTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
		} else {
			p.teleportTo(to, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, Set.of(), p.getYRot(), p.getXRot(), true);
		}
		CompoundTag flash = new CompoundTag();
		flash.putDouble("x", at.getX() + 0.5D);
		flash.putDouble("y", at.getY());
		flash.putDouble("z", at.getZ() + 0.5D);
		flash.putInt("detik", 1);
		flash.putString("jenis", k.awan() ? "awan" : "cakram");
		flash.putBoolean("tiba", true);
		flash.putInt("pemain", p.getId());
		KotaBesarPayload.sekitar(to, Vec3.atCenterOf(at), "teleport", flash);
		to.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.2F);
		Advancements.grant(p, "tempat/teleport");
		List<UUID> murid = k.murid();
		if (!murid.isEmpty()) {
			Voice.later(10, () -> {
				for (UUID mid : murid) {
					Entity e = k.level().getEntity(mid);
					if (e instanceof DiscipleEntity d && d.isAlive() && to == d.level()) {
						d.teleportTo(at.getX() + 0.5D + (d.getRandom().nextDouble() - 0.5D) * 3.0D, at.getY(), at.getZ() + 0.5D
								+ (d.getRandom().nextDouble() - 0.5D) * 3.0D);
					}
				}
			});
		}
	}

	// ================================================================== the disc, the ship routes, the dialog

	/** Every 20 ticks: a player crouching on a loaded disc opens the station (once in 3 s). */
	static void cakram(MinecraftServer server) {
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			if (level == null) {
				continue;
			}
			for (DuniaAnchorEntity disc : kota.cakram) {
				for (ServerPlayer p : level.players()) {
					if (!p.isShiftKeyDown() || p.distanceToSqr(disc) > CAKRAM_JARAK * CAKRAM_JARAK || TempatLayar.layarTerbuka(p) != null) {
						continue;
					}
					long now = level.getGameTime();
					if (now - JONGKOK.getOrDefault(p.getUUID(), -100L) < 60L) {
						continue;
					}
					JONGKOK.put(p.getUUID(), now);
					CompoundTag tag = bangun(p, -1, false);
					if (tag != null) {
						TempatLayar.bukaDi(p, LembarKunci.Teleport.LAYAR, disc.blockPosition(), tag);
					}
				}
			}
		}
	}

	/** Registers capital {@code k} as a destination of the player ships (once per capital). */
	static void daftarRute(MinecraftServer server, Kandidat k) {
		String id = "kota_besar_" + k.index();
		if (!RUTE.add(id)) {
			return;
		}
		long site = KotaBesarKota.situs(k);
		try {
			DuniaApi.routes().registerDestination(id, KotaBesarKota.nama(server, k),
					(captain, level) -> level.dimension() == Level.OVERWORLD && DuniaSites.knows(captain, site),
					(captain, riders) -> tibaKapal(captain, riders, k));
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Kota Besar ship route {} could not be registered", id, e);
		}
	}

	private static void tibaKapal(ServerPlayer captain, List<Entity> riders, Kandidat k) {
		MinecraftServer server = captain.level().getServer();
		ServerLevel level = server.overworld();
		long town = KotaBesarKota.situs(k);
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		BlockPos at = kota != null && !kota.dermaga.isEmpty() ? kota.dermaga.get(0).blockPosition() : KotaBesarKota.titik(level, k, 0, 20, 0);
		DuniaTickets.touch(level, ChunkPos.containing(at), 1);
		JamMalam.abaikan(captain, 200L);
		captain.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, Set.of(), captain.getYRot(), captain.getXRot(), true);
		for (Entity e : riders) {
			if (e instanceof ServerPlayer rp) {
				JamMalam.abaikan(rp, 200L);
				rp.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 1.5D, Set.of(), rp.getYRot(), rp.getXRot(), true);
			} else if (e.level() == level) {
				e.teleportTo(at.getX() + 0.5D, at.getY(), at.getZ() + 1.5D);
			}
		}
	}

	/** The station and dock entries of the dialog's services. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer p, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (npc.role() == NpcRole.PENJAGA_FORMASI) {
			out.add(new TempatDialog.JasaEntri(LembarKunci.Teleport.LAYAR, Component.translatable("kultivator.kota_besar.jasa.teleport"),
					LembarKunci.Ikon.TELEPORT, Component.translatable("kultivator.kota_besar.jasa.teleport.catatan"), false, null));
		}
		if (npc.role() == NpcRole.NAKHODA) {
			long t = Kota.timeOfDay(p.level().getServer());
			boolean open = t >= DERMAGA_BUKA && t < DERMAGA_TUTUP;
			out.add(new TempatDialog.JasaEntri(LembarKunci.Dermaga.LAYAR, Component.translatable("kultivator.kota_besar.jasa.dermaga"),
					LembarKunci.Ikon.DERMAGA, Component.translatable("kultivator.kota_besar.dermaga.jam", KotaBesarKalender.jam(DERMAGA_BUKA),
					KotaBesarKalender.jam(DERMAGA_TUTUP)), false, null));
		}
		return out;
	}

	/** The NB a player would pay to reach {@code site} by station now (command and map helpers). */
	static long tarifKe(ServerPlayer p, DuniaSites.Site site) {
		int jarak = (int) Math.round(Math.hypot(site.pos().getX() - p.getX(), site.pos().getZ() - p.getZ()));
		return tarif(jarak, false);
	}

	/** Moves {@code p} to {@code site} at once (command {@code uji kota_besar teleport}). */
	static boolean langsung(ServerPlayer p, DuniaSites.Site site) {
		Tujuan dest = new Tujuan(site, 0, 0L, null, false);
		BlockPos at = tiba(p.level().getServer(), dest);
		if (at == null) {
			return false;
		}
		ServerLevel to = p.level().getServer().getLevel(site.dim());
		if (to == null) {
			return false;
		}
		pindah(p.level().getServer(), p, new Kanal(p.level(), p.position(), site.id(), at, to.dimension(), 0L, false, List.of()));
		return true;
	}

	private Teleport() {
	}
}
