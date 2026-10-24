package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.desa.Desa;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kampung.KampungNames;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaCerita;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kandang Tawanan (DESIGN 38.T.7.6, 38.T.0 butir 5): 4-10 captives, mortals taken from real villages. The only things a
 * player can do are <b>Tebus</b> (pay {@code 25 x (1 + strength / 2)} NB, at most 3 a day) and <b>Tebusan Darah</b> (a
 * bout one tier harder instead of money, {@link ArenaDarah}). A captive is never owned, employed, sold or made a
 * disciple.
 * <ul>
 * <li>The freed captive follows the player as a companion and must be brought out of the gate to its home village (or
 * any village, town or the hunters' camp): there Nama Baik +3, pledged village loyalty +5, Infami -5, standing +2 with
 * the nearest righteous sect, the guide goal {@code tebus}. An escort abandoned for 2 minutes (or 2 days old) walks alone
 * to the hunters' camp: no reward.</li>
 * <li>New captives come 1-2 every 3 world days (a "missing villager" rumour at their village); after a righteous victory
 * the pens stay empty for 5 days; route Lurus closes them for good; route Penebusan makes them the House of Ransom
 * (half price, delivered by an NPC escort at once).</li>
 * <li>Cells: canvas {@code SEL_X} of the generator (cell i door at {@code (x+2.5, 12.5)} of district penjara_tawanan),
 * a {@link SelTawananEntity} per occupied cell, the captive kneeling in chains inside.</li>
 * </ul>
 */
public final class KandangTawanan {

	public static final String DISTRIK = "penjara_tawanan";
	static final int[] SEL_X = {10, 17, 24, 31, 38, 55, 62, 69, 76, 83};
	static final int SEL_Z0 = 7;
	public static final int MAKS = 10;
	public static final int TEBUS_HARIAN = 3;
	public static final int CERITA = 10;
	/** World days per new arrival round. */
	public static final int DATANG_HARI = 3;
	public static final long KAWAL_TICK = 2L * 24000L;
	public static final long TINGGAL_TICK = 2L * 60L * 20L;

	private record Kawal(UUID player, KultivatorNpc npc, long situs, String nama, long mulai, String asalJenis, long asalSitus) {
	}

	private static final List<Kawal> KAWAL = new ArrayList<>();
	/** Town id -> cell bodies (door entity per cell index) and captive bodies. */
	private static final Map<Long, SelTawananEntity[]> SEL = new HashMap<>();
	private static final Map<Long, KultivatorNpc[]> TAWANAN = new HashMap<>();
	private static final Map<UUID, Long> JAUH = new HashMap<>();

	private KandangTawanan() {
	}

	static void init() {
		TemaLayar.daftar(TemaLayar.Tawanan.LAYAR, KandangTawanan::bangun, KandangTawanan::aksi);
	}

	static void clear() {
		KAWAL.clear();
		SEL.clear();
		TAWANAN.clear();
		JAUH.clear();
	}

	// ================================================================== the captives list

	/** The town's captives (A-Fu first while she is held), arrivals applied. */
	static ListTag daftar(ServerLevel level, TemaKota.Kota kota) {
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		ListTag list = IblisData.daftar(town, "tawanan");
		long hari = IblisKota.hari(level);
		if (town.getBooleanOr("kandang_tutup", false)) {
			if (!list.isEmpty()) {
				list.clear();
				IblisData.ubah(server);
			}
			return list;
		}
		if (town.getLongOr("kosong_sampai", -1L) >= hari) {
			return list;
		}
		if (!town.getBooleanOr("tawanan_awal", false)) {
			town.putBoolean("tawanan_awal", true);
			int n = 4 + (int) Math.floorMod(kota.situsId() >>> 7, 7L);
			for (int i = 0; i < n; i++) {
				tambah(level, kota, town, list, hari, i);
			}
			town.putLong("tawanan_hari", hari);
			IblisData.ubah(server);
		} else if (hari - town.getLongOr("tawanan_hari", hari) >= DATANG_HARI) {
			long rounds = (hari - town.getLongOr("tawanan_hari", hari)) / DATANG_HARI;
			for (int r = 0; r < Math.min(3L, rounds); r++) {
				int n = 1 + (int) Math.floorMod((kota.situsId() + hari + r) >>> 3, 2L);
				for (int i = 0; i < n && list.size() < MAKS; i++) {
					CompoundTag c = tambah(level, kota, town, list, hari, 100 + r * 3 + i);
					if (c != null) {
						kabarHilang(level, c);
					}
				}
			}
			town.putLong("tawanan_hari", town.getLongOr("tawanan_hari", hari) + rounds * DATANG_HARI);
			IblisData.ubah(server);
		}
		return list;
	}

	private static @Nullable CompoundTag tambah(ServerLevel level, TemaKota.Kota kota, CompoundTag town, ListTag list, long hari, int salt) {
		if (list.size() >= MAKS) {
			return null;
		}
		int id = town.getIntOr("tawanan_seq", 1) + 1;
		town.putInt("tawanan_seq", id);
		int look = (int) (kota.situsId() * 0x9E3779B9L) ^ id * 0x45D9F3B ^ salt * 131;
		CompoundTag c = new CompoundTag();
		c.putInt("id", id);
		c.putInt("look", look);
		c.putBoolean("perempuan", (look >>> 4 & 1) == 1);
		c.putInt("umur", 7 + Math.floorMod(look >>> 8, 52));
		c.putInt("cerita", Math.floorMod(look >>> 15, CERITA));
		c.putLong("hari", hari);
		DuniaSites.Site home = kampungTerdekat(level, kota.pusat(), look);
		if (home != null) {
			c.putLong("asal", home.id());
			c.putString("asal_jenis", home.kind());
		}
		list.add(c);
		return c;
	}

	/** The village a captive comes from: the nearest published village or hamlet (rotating by the look seed). */
	private static @Nullable DuniaSites.Site kampungTerdekat(ServerLevel level, BlockPos from, int look) {
		List<DuniaSites.Site> villages = new ArrayList<>();
		for (DuniaSites.Site s : DuniaSites.all(level.getServer())) {
			if (s.kind().startsWith("kampung/") || s.kind().startsWith("desa/")) {
				villages.add(s);
			}
		}
		if (villages.isEmpty()) {
			return null;
		}
		villages.sort((a, b) -> Double.compare(a.pos().distSqr(from), b.pos().distSqr(from)));
		int pick = Math.floorMod(look >>> 21, Math.min(3, villages.size()));
		return villages.get(pick);
	}

	/** The home village's name of a captive (generic hamlet name when none is known). */
	static Component asal(CompoundTag c) {
		String jenis = c.getStringOr("asal_jenis", "");
		long site = c.getLongOr("asal", 0L);
		if (jenis.startsWith("kampung/")) {
			return KampungNames.nama(jenis.substring("kampung/".length()), site);
		}
		if (jenis.startsWith("desa/")) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.desa", KotaNames.town(site));
		}
		return KampungNames.nama("dusun", c.getIntOr("look", 0));
	}

	static Component nama(CompoundTag c) {
		if (TokohIblis.A_FU.equals(c.getStringOr("tokoh", ""))) {
			return TokohIblis.nama(TokohIblis.A_FU);
		}
		return KotaNames.person(c.getIntOr("look", 0), c.getBooleanOr("perempuan", false));
	}

	private static void kabarHilang(ServerLevel level, CompoundTag c) {
		long home = c.getLongOr("asal", 0L);
		CeritaApi.kabar(level, home == 0L ? null : home, "tema_iblis_warga_hilang", nama(c), asal(c));
	}

	public static long harga(TemaKota.Kota kota, ServerLevel level) {
		long price = Math.round(25.0D * (1.0D + IblisKota.kekuatan(kota) / 2.0D));
		if (IblisData.kota(level.getServer(), kota.situsId()).getBooleanOr("rumah_tebusan", false)) {
			price = Math.max(1L, price / 2L);
		}
		return price;
	}

	public static int sisaHariIni(ServerPlayer p, TemaKota.Kota kota) {
		return Math.max(0, TEBUS_HARIAN - IblisData.hariIni(p.level().getServer(), kota.situsId(), p.getUUID(), "tebus", IblisKota.hari(p.level())));
	}

	// ================================================================== the pens (anchor kota_tema/tawanan)

	static void nearby(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		ListTag list = daftar(level, kota);
		long situs = kota.situsId();
		SelTawananEntity[] doors = SEL.computeIfAbsent(situs, k -> new SelTawananEntity[MAKS]);
		KultivatorNpc[] bodies = TAWANAN.computeIfAbsent(situs, k -> new KultivatorNpc[MAKS]);
		boolean near = false;
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(anchor) < 40.0D * 40.0D) {
				near = true;
				break;
			}
		}
		for (int i = 0; i < MAKS; i++) {
			CompoundTag c = i < list.size() && list.get(i) instanceof CompoundTag t ? t : null;
			if (c == null || !near) {
				if (bodies[i] != null) {
					bodies[i].vanish();
					bodies[i] = null;
				}
				if (doors[i] != null && (!near || c == null) && doors[i].keadaan() == SelTawananEntity.KEADAAN_TERKUNCI) {
					TemaMekanisme.lepas(doors[i]);
					doors[i] = null;
				}
				continue;
			}
			if (doors[i] == null || doors[i].isRemoved()) {
				Vec3 at = IblisKota.lokal(kota, DISTRIK, SEL_X[i] + 2.5D, IblisKota.LY + 1, SEL_Z0 + 5.5D);
				if (at != null) {
					doors[i] = TemaMekanisme.muncul(level, TemaEntities.SEL_TAWANAN, at, IblisKota.yaw(kota, 0.0F), kota, anchor.blockPosition());
					if (doors[i] != null) {
						doors[i].setKeadaan(SelTawananEntity.KEADAAN_TERKUNCI);
						IblisAnchors.simpan(anchor, doors[i]);
					}
				}
			}
			if (bodies[i] == null || bodies[i].isRemoved() || !bodies[i].isAlive()) {
				BlockPos pos = IblisKota.blok(kota, DISTRIK, SEL_X[i] + 2, IblisKota.LY + 1, SEL_Z0 + 2);
				if (pos != null) {
					boolean afu = TokohIblis.A_FU.equals(c.getStringOr("tokoh", ""));
					// A-Fu is a named character: her body carries the tokoh tag so the chain's dialogs find her
					KultivatorNpc npc = afu ? TokohIblis.panggil(level, situs, TokohIblis.A_FU, pos, IblisKota.yaw(kota, 0.0F))
							: HidupApi.tubuh(level, situs, NpcRole.TAWANAN, pos, IblisKota.yaw(kota, 0.0F), c.getIntOr("look", i));
					if (npc != null) {
						if (afu) {
							npc.memory().putInt("iblis_tawanan", c.getIntOr("id", 0));
							npc.memory().putBoolean("hold", true);
							npc.setDiamDiTitik(true);
						} else {
							tandaiTawanan(npc, c);
						}
						HidupApi.klip(npc, i % 3 == 0 ? GerakId.BERLUTUT_RANTAI : GerakId.DUDUK_LANTAI, 0, 0);
						bodies[i] = IblisAnchors.simpan(anchor, npc);
					}
				}
			}
		}
		// morning feeding: the jailer throws bread at 08:00
		long jam = IblisKota.jam(level);
		if (jam >= 2000L && jam < 2400L) {
			KultivatorNpc jailer = IblisAnchors.milik(anchor, KultivatorNpc.class);
			if (jailer != null) {
				HidupApi.klip(jailer, GerakId.LEMPAR_PAKAN, 0, 100);
			}
		}
	}

	private static void tandaiTawanan(KultivatorNpc npc, CompoundTag c) {
		npc.memory().putInt("iblis_tawanan", c.getIntOr("id", 0));
		npc.memory().putBoolean("hold", true);
		npc.setDiamDiTitik(true);
		Sosok sosok = SosokApi.sosok(npc);
		int umur = c.getIntOr("umur", 30);
		Sosok want = sosok.withKelamin(c.getBooleanOr("perempuan", false) ? Sosok.PEREMPUAN : Sosok.LAKI)
				.withUsia(umur < 13 ? Sosok.ANAK : umur < 25 ? Sosok.MUDA : umur < 50 ? Sosok.DEWASA : Sosok.TUA);
		if (!want.equals(sosok)) {
			SosokApi.setSosok(npc, want);
		}
		npc.setPlate(Component.translatable("kultivator.kota_tema.iblis.tawanan.papan", nama(c), asal(c)));
	}

	// ================================================================== ransom

	/** Op {@code tebus:ID}: pays and frees one captive; returns the refusal or null. */
	static @Nullable Component tebus(ServerPlayer p, TemaKota.Kota kota, int id) {
		ServerLevel level = p.level();
		CompoundTag town = IblisData.kota(level.getServer(), kota.situsId());
		if (town.getBooleanOr("kandang_tutup", false)) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.tutup");
		}
		ListTag list = daftar(level, kota);
		CompoundTag c = IblisData.cari(list, id);
		if (c == null) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.tidak_ada");
		}
		if (sisaHariIni(p, kota) <= 0) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.habis", TEBUS_HARIAN);
		}
		long price = harga(kota, level);
		if (!NilaiBatu.pay(p, price)) {
			return Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(price));
		}
		IblisData.tambahKas(level.getServer(), kota.situsId(), price);
		IblisData.hitungHariIni(level.getServer(), kota.situsId(), p.getUUID(), "tebus", IblisKota.hari(level));
		bebaskan(p, kota, id, false);
		return null;
	}

	/** Op {@code tebusan_darah:ID}: a bout one tier harder for this captive (no money). */
	static @Nullable Component tebusanDarah(ServerPlayer p, TemaKota.Kota kota, int id) {
		ServerLevel level = p.level();
		if (IblisData.cari(daftar(level, kota), id) == null) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.tidak_ada");
		}
		if (sisaHariIni(p, kota) <= 0) {
			return Component.translatable("kultivator.kota_tema.iblis.tawanan.habis", TEBUS_HARIAN);
		}
		Component no = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_TEBUSAN, id);
		if (no == null) {
			IblisData.hitungHariIni(level.getServer(), kota.situsId(), p.getUUID(), "tebus", IblisKota.hari(level));
		}
		return no;
	}

	/** Frees captive {@code id} for {@code p} (paid or won): opens its cell and starts the escort home. */
	static void bebaskan(ServerPlayer p, TemaKota.Kota kota, int id, boolean darah) {
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		ListTag list = IblisData.daftar(town, "tawanan");
		int index = -1;
		CompoundTag c = null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag t && t.getIntOr("id", -1) == id) {
				index = i;
				c = t;
				break;
			}
		}
		if (c == null) {
			return;
		}
		list.remove(index);
		IblisData.ubah(server);
		boolean afu = TokohIblis.A_FU.equals(c.getStringOr("tokoh", ""));
		if (afu) {
			town.putBoolean("a_fu_bebas", true);
		}
		SelTawananEntity[] doors = SEL.get(kota.situsId());
		KultivatorNpc[] bodies = TAWANAN.get(kota.situsId());
		Vec3 from = p.position();
		if (doors != null && index >= 0 && index < MAKS && doors[index] != null && !doors[index].isRemoved()) {
			doors[index].setKeadaan(SelTawananEntity.KEADAAN_TERBUKA);
			from = doors[index].position();
			level.playSound(null, from.x, from.y, from.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		if (bodies != null) {
			// shift the bodies with the list: the simplest is to let the anchor re-make them
			for (int i = 0; i < MAKS; i++) {
				if (bodies[i] != null) {
					bodies[i].vanish();
					bodies[i] = null;
				}
			}
		}
		Component name = nama(c);
		Component home = asal(c);
		p.sendSystemMessage(Component.translatable(darah ? "kultivator.kota_tema.iblis.tawanan.bebas_darah" : "kultivator.kota_tema.iblis.tawanan.bebas", name, home)
				.withStyle(ChatFormatting.GOLD));
		if (town.getBooleanOr("rumah_tebusan", false)) {
			// the House of Ransom: an NPC escort takes the freed home at once
			tiba(p, kota.situsId(), name, c.getStringOr("asal_jenis", "kampung/dusun"), c.getLongOr("asal", 0L), null, true);
			return;
		}
		BlockPos at = BlockPos.containing(from.x, from.y, from.z);
		KultivatorNpc npc = afu ? TokohIblis.panggil(level, kota.situsId(), TokohIblis.A_FU, at, p.getYRot())
				: HidupApi.tubuh(level, kota.situsId(), NpcRole.TAWANAN, at, p.getYRot(), c.getIntOr("look", id));
		if (npc == null) {
			tiba(p, kota.situsId(), name, c.getStringOr("asal_jenis", "kampung/dusun"), c.getLongOr("asal", 0L), null, false);
			return;
		}
		if (!afu) {
			tandaiTawanan(npc, c);
		}
		npc.memory().putBoolean("hold", false);
		npc.setDiamDiTitik(false);
		npc.memory().putString("iblis_kawal", p.getUUID().toString());
		npc.setCompanion(p.getUUID(), level.getGameTime() + KAWAL_TICK);
		Voice.say(npc, "tema.iblis.captive", null);
		KAWAL.add(new Kawal(p.getUUID(), npc, kota.situsId(), name.getString(), level.getGameTime(), c.getStringOr("asal_jenis", ""), c.getLongOr("asal", 0L)));
		if (afu) {
			CeritaApi.peristiwa(p, IblisCerita.TAWANAN_BEBAS, TokohIblis.A_FU);
			CeritaApi.peristiwa(p, IblisCerita.AFU, "bebas");
		}
		CeritaApi.peristiwa(p, IblisCerita.TAWANAN_BEBAS, darah ? "darah" : "tebus");
	}

	/** Every 40 ticks: escorts that reached a safe place, or were abandoned. */
	static void tick(MinecraftServer server) {
		if (KAWAL.isEmpty()) {
			return;
		}
		List<Kawal> done = new ArrayList<>();
		for (Kawal k : KAWAL) {
			ServerPlayer p = server.getPlayerList().getPlayer(k.player());
			KultivatorNpc npc = k.npc();
			if (npc.isRemoved() || !npc.isAlive()) {
				done.add(k);
				continue;
			}
			ServerLevel level = (ServerLevel) npc.level();
			long now = level.getGameTime();
			if (p == null || p.level() != level || p.distanceToSqr(npc) > 96.0D * 96.0D || now - k.mulai() > KAWAL_TICK) {
				long since = JAUH.computeIfAbsent(npc.getUUID(), u -> now);
				if (now - since > TINGGAL_TICK || now - k.mulai() > KAWAL_TICK) {
					if (p != null) {
						p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.tawanan.sendiri", npc.getDisplayName()).withStyle(ChatFormatting.GRAY));
					}
					npc.vanish();
					done.add(k);
				}
				continue;
			}
			JAUH.remove(npc.getUUID());
			if (IblisKota.di(level, npc.blockPosition()) != null) {
				continue;
			}
			TempatInfo here = TempatApi.tempatDi(level, npc.blockPosition());
			String kind = here != null ? here.jenis() : null;
			if (kind == null) {
				List<DuniaSites.Site> near = DuniaSites.near(level, npc.blockPosition(), 24, "");
				kind = near.isEmpty() ? null : near.getFirst().kind();
			}
			if (kind != null && aman(kind)) {
				tiba(p, k.situs(), npc.getDisplayName(), kind, here != null ? here.situsId() : 0L, npc, false);
				done.add(k);
			}
		}
		KAWAL.removeAll(done);
	}

	private static boolean aman(String kind) {
		return kind.startsWith("kampung/") || kind.startsWith("desa/") || kind.startsWith("kota/") || kind.startsWith("kotabesar/")
				|| kind.equals("kota_tema/pos_pemburu") || kind.startsWith("kota_tema/") && !kind.equals("kota_tema/iblis");
	}

	/** The freed captive reached a safe place: the rewards of 38.T.7.6. */
	private static void tiba(ServerPlayer p, long situsIblis, Component name, String kind, long tempat, @Nullable KultivatorNpc npc, boolean rumahTebusan) {
		ServerLevel level = p.level();
		if (tempat != 0L) {
			KotaData.get(level.getServer()).addReputation(tempat, p.getUUID(), 3);
		}
		BlockPos at = npc != null ? npc.blockPosition() : p.blockPosition();
		Desa.loyaltyAt(level, at, 5);
		IblisMeter.tambahInfami(p, -5, "tebus");
		String lurus = SekteApi.diplomasi().nearest(at, 0);
		if (lurus != null) {
			SekteApi.diplomasi().adjust(p.getUUID(), lurus, 2, "38tk.tebus");
		}
		TemaCerita.lapor(p, TemaCerita.TEBUS, kind);
		CeritaApi.peristiwa(p, IblisCerita.TAWANAN_PULANG, kind);
		if (npc != null && TokohIblis.adalah(npc, TokohIblis.A_FU)) {
			CeritaApi.peristiwa(p, IblisCerita.TAWANAN_PULANG, TokohIblis.A_FU);
		}
		CeritaApi.kabar(level, tempat == 0L ? null : tempat, "tema_iblis_tawanan_pulang", name, p.getName().getString());
		p.sendSystemMessage(Component.translatable(rumahTebusan ? "kultivator.kota_tema.iblis.tawanan.diantar" : "kultivator.kota_tema.iblis.tawanan.tiba", name)
				.withStyle(ChatFormatting.GREEN));
		if (npc != null) {
			HidupApi.klip(npc, GerakId.HORMAT_DALAM, 0, 60);
			Voice.say(npc, "tema.iblis.dawn", null);
			npc.setCompanion(null, 0L);
			npc.setLonely(16.0D, 400);
		}
	}

	// ================================================================== the screen

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return null;
		}
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.tawanan", LembarKunci.GAYA_BATU, npc);
		ServerLevel level = p.level();
		CompoundTag town = IblisData.kota(level.getServer(), kota.situsId());
		if (town.getBooleanOr("kandang_tutup", false)) {
			IblisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.iblis.tawanan.tutup"), null);
			return tag;
		}
		if (town.getBooleanOr("rumah_tebusan", false)) {
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota_tema.iblis.tawanan.rumah_tebusan"));
		}
		ListTag list = daftar(level, kota);
		ListTag rows = new ListTag();
		long price = harga(kota, level);
		KultivatorNpc[] bodies = TAWANAN.get(kota.situsId());
		for (int i = 0; i < list.size() && rows.size() < LembarKunci.DAFTAR_MAKS; i++) {
			if (!(list.get(i) instanceof CompoundTag c)) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(c.getIntOr("id", 0)));
			LembarKunci.teks(row, LembarKunci.NAMA, nama(c));
			LembarKunci.teks(row, TemaLayar.Tawanan.ASAL, asal(c));
			row.putInt(TemaLayar.Tawanan.UMUR, c.getIntOr("umur", 30));
			row.putInt(LembarKunci.POTRET, bodies != null && i < MAKS && bodies[i] != null && !bodies[i].isRemoved() ? bodies[i].getId() : -1);
			LembarKunci.teks(row, TemaLayar.Tawanan.CERITA, Component.translatable("kultivator.kota_tema.iblis.tawanan.cerita." + c.getIntOr("cerita", 0)));
			row.putLong(LembarKunci.HARGA, price);
			rows.add(row);
		}
		tag.put(TemaLayar.Tawanan.TAWANAN, rows);
		tag.putInt(TemaLayar.Tawanan.SISA, sisaHariIni(p, kota));
		if (rows.isEmpty()) {
			IblisLayar.kosong(tag, Component.translatable(town.getLongOr("kosong_sampai", -1L) >= IblisKota.hari(level)
					? "kultivator.kota_tema.iblis.tawanan.kosong_serbu" : "kultivator.kota_tema.iblis.tawanan.kosong"));
		}
		return tag;
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		int id = (int) IblisLayar.angka(parts, 1, -1L);
		switch (parts[0]) {
			case TemaLayar.Tawanan.OP_TEBUS -> {
				Component no = tebus(p, kota, id);
				if (no != null) {
					IblisLayar.pesan(p, no, false);
				} else {
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.tawanan.tebus_ok"), true);
				}
			}
			case TemaLayar.Tawanan.OP_TEBUSAN_DARAH -> {
				Component no = tebusanDarah(p, kota, id);
				if (no != null) {
					IblisLayar.pesan(p, no, false);
				} else {
					dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(p);
				}
			}
			default -> {
			}
		}
	}

	/** Adds A-Fu to the front of the town's list (chain chapter 2) while she is held. */
	static void pastikanAfu(ServerLevel level, TemaKota.Kota kota) {
		CompoundTag town = IblisData.kota(level.getServer(), kota.situsId());
		if (town.getBooleanOr("a_fu_bebas", false) || town.getBooleanOr("kandang_tutup", false)) {
			return;
		}
		ListTag list = daftar(level, kota);
		for (Tag t : list) {
			if (t instanceof CompoundTag c && TokohIblis.A_FU.equals(c.getStringOr("tokoh", ""))) {
				return;
			}
		}
		CompoundTag c = new CompoundTag();
		c.putInt("id", 1);
		c.putString("tokoh", TokohIblis.A_FU);
		c.putInt("look", TokohIblis.A_FU.hashCode());
		c.putBoolean("perempuan", true);
		c.putInt("umur", 10);
		c.putInt("cerita", CERITA);
		c.putLong("hari", IblisKota.hari(level));
		DuniaSites.Site home = kampungTerdekat(level, kota.pusat(), 0);
		if (home != null) {
			c.putLong("asal", home.id());
			c.putString("asal_jenis", home.kind());
		}
		list.add(0, c);
		IblisData.ubah(level.getServer());
	}
}
