package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.MisiGilda;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Palace of the City Warden (DESIGN 38.B.4.2): the audience and the Palace Orders ("Perintah Istana", never "Titah").
 * <ul>
 * <li><b>Audience</b> with the Prince Warden ({@link NpcRole#PANGERAN_KOTA} in the throne hall) for a Hero of the City
 * (Nama Baik 80+) between 10000 and 12000: the title "Tamu Kehormatan Istana" and the right to the orders.</li>
 * <li><b>Palace Orders</b> from the Palace Minister ({@link NpcRole#MENTERI} of the {@code perintah_istana} lot), one set
 * per 30-day term: (1) clear the nearest known demon lair ({@code gua/sarang_iblis}: three of its demonic cultivators or
 * its master), (2) escort a palace envoy along the roads to a town at least 500 blocks away (the envoy walks with the
 * player as a companion), (3) slay the master beast of a beast lair ({@code gua/sarang_binatang}). Each pays an A-rank
 * guild bounty for the player's band x 1.5 (38.M.3) when done, and the whole set once a term a rare spirit herb.</li>
 * </ul>
 * The palace screens reuse the {@code acara} sheet (title, the three orders as schedule rows, a question with three
 * answers); this class is registered in the calendar as a never-scheduled entry {@link #ID}.
 */
final class Istana implements KotaBesarKalender.Acara {

	static final String ID = "istana";
	static final int NB_AUDIENSI = KotaBesarApi.NB_PAHLAWAN;
	static final long AUDIENSI_MULAI = 10000L;
	static final long AUDIENSI_AKHIR = 12000L;
	static final int MASA = 30;
	static final String[] TUGAS = {"sarang_iblis", "kawal", "sarang_binatang"};
	static final int KAWAL_JARAK = 500;
	static final int IBLIS_PERLU = 3;

	Istana() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Istana::mati);
		CeritaApi.daftarJasa(Istana::jasa);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return -1;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return false;
	}

	@Override
	public long mulaiJam() {
		return AUDIENSI_MULAI;
	}

	@Override
	public long akhirJam() {
		return AUDIENSI_AKHIR;
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
	}

	// ================================================================== orders state

	private static CompoundTag perintah(MinecraftServer server, UUID player) {
		return KotaBesarData.get(server).pemain(player).getCompoundOrEmpty("perintah");
	}

	private static void simpan(MinecraftServer server, UUID player, CompoundTag tag) {
		KotaBesarData data = KotaBesarData.get(server);
		data.pemain(player).put("perintah", tag);
		data.setDirty();
	}

	static long masa(MinecraftServer server) {
		return Kota.day(server) / MASA;
	}

	/** The A-rank guild bounty of the band (midpoint of the board's range) x 1.5. */
	static long imbalan(int band) {
		long low;
		long high;
		if (band <= 1) {
			low = 6L;
			high = 12L;
		} else if (band == 2) {
			low = 15L;
			high = 30L;
		} else {
			low = 40L;
			high = 80L;
			for (int b = 4; b <= Math.min(band, 6); b++) {
				low *= 2L;
				high *= 2L;
			}
		}
		return Math.round((low + high) / 2.0D * MisiGilda.PENGALI[JenisMisi.A] * 1.5D);
	}

	/** Draws the term's three orders for {@code player} from the places he knows. */
	private static CompoundTag terbitkan(ServerPlayer p, long town) {
		MinecraftServer server = p.level().getServer();
		CompoundTag tag = new CompoundTag();
		tag.putLong("masa", masa(server));
		tag.putLong("town", town);
		ListTag list = new ListTag();
		for (String jenis : TUGAS) {
			CompoundTag t = new CompoundTag();
			t.putString("jenis", jenis);
			DuniaSites.Site target = switch (jenis) {
				case "sarang_iblis" -> terdekat(p, "gua/sarang_iblis", 0);
				case "sarang_binatang" -> terdekat(p, "gua/sarang_binatang", 0);
				default -> kotaTujuan(p);
			};
			if (target != null) {
				t.putLong("situs", target.id());
				t.putInt("x", target.pos().getX());
				t.putInt("z", target.pos().getZ());
			}
			t.putLong("imbalan", imbalan(Barang.pita(p)));
			list.add(t);
		}
		tag.put("tugas", list);
		return tag;
	}

	private static @Nullable DuniaSites.Site terdekat(ServerPlayer p, String kind, int minJarak) {
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site s : DuniaSites.known(p)) {
			if (!s.kind().equals(kind) || s.dim() != p.level().dimension()) {
				continue;
			}
			double d = s.pos().distSqr(p.blockPosition());
			if (d >= (double) minJarak * minJarak && d < bestSq) {
				bestSq = d;
				best = s;
			}
		}
		return best;
	}

	private static @Nullable DuniaSites.Site kotaTujuan(ServerPlayer p) {
		DuniaSites.Site best = terdekat(p, "kota/kota", KAWAL_JARAK);
		if (best == null) {
			best = terdekat(p, "kampung/desa_besar", KAWAL_JARAK);
		}
		return best;
	}

	private static boolean semuaSelesai(CompoundTag tag) {
		ListTag list = tag.getListOrEmpty("tugas");
		for (int i = 0; i < list.size(); i++) {
			if (!list.getCompoundOrEmpty(i).getBooleanOr("selesai", false)) {
				return false;
			}
		}
		return !list.isEmpty();
	}

	/** Marks order {@code jenis} done, pays it, and the term's rare herb when the set is complete. */
	private static void tuntas(ServerPlayer p, String jenis) {
		MinecraftServer server = p.level().getServer();
		CompoundTag tag = perintah(server, p.getUUID());
		if (tag.getLongOr("masa", -1L) != masa(server)) {
			return;
		}
		ListTag list = tag.getListOrEmpty("tugas");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			if (!jenis.equals(t.getStringOr("jenis", "")) || t.getBooleanOr("selesai", false)) {
				continue;
			}
			t.putBoolean("selesai", true);
			long pay = t.getLongOr("imbalan", 0L);
			EkonomiApi.beri(p, pay, "kotabesar.perintah");
			KotaData.get(server).addReputation(tag.getLongOr("town", 0L), p.getUUID(), 2);
			Msg.title(p, Component.translatable("kultivator.kota_besar.istana.tuntas").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.kota_besar.istana.tugas." + jenis), 10, 60, 20);
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.istana.dibayar", Barang.nb(pay)).withStyle(ChatFormatting.GOLD));
		}
		tag.put("tugas", list);
		if (semuaSelesai(tag) && !tag.getBooleanOr("langka", false)) {
			tag.putBoolean("langka", true);
			Barang.beri(p, SpiritHerbs.randomFor(null, p.getRandom(), 300, 600));
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.istana.langka").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		simpan(server, p.getUUID(), tag);
	}

	// ================================================================== detection

	private static void mati(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer p) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag tag = perintah(server, p.getUUID());
		if (tag.isEmpty() || tag.getLongOr("masa", -1L) != masa(server)) {
			return;
		}
		boolean boss = victim.entityTags().contains(TechniquePower.TAG_BOSS);
		boolean elite = EliteMonsters.isElite(victim);
		ListTag list = tag.getListOrEmpty("tugas");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			String jenis = t.getStringOr("jenis", "");
			if (t.getBooleanOr("selesai", false) || jenis.equals("kawal")) {
				continue;
			}
			String kind = "gua/" + jenis;
			if (!diSarang(level, victim.blockPosition(), t, kind)) {
				continue;
			}
			if (jenis.equals("sarang_iblis")) {
				boolean iblis = victim instanceof KultivatorNpc n && n.role().iblis();
				if (!iblis && !boss && !elite) {
					continue;
				}
				int kills = t.getIntOr("bunuh", 0) + 1;
				t.putInt("bunuh", kills);
				tag.put("tugas", list);
				simpan(server, p.getUUID(), tag);
				if (kills >= IBLIS_PERLU || boss) {
					tuntas(p, jenis);
				} else {
					Msg.actionBar(p, Component.translatable("kultivator.kota_besar.istana.iblis_maju", kills, IBLIS_PERLU).withStyle(ChatFormatting.GOLD));
				}
			} else if (boss || elite && EliteMonsters.tier(victim) >= 2) {
				tuntas(p, jenis);
			}
			return;
		}
	}

	private static boolean diSarang(ServerLevel level, BlockPos at, CompoundTag t, String kind) {
		if (t.contains("situs")) {
			return Math.hypot(at.getX() - t.getIntOr("x", 0), at.getZ() - t.getIntOr("z", 0)) <= 80.0D;
		}
		return !DuniaSites.near(level, at, 80, kind).isEmpty();
	}

	/** Every 20 ticks: envoys that reached their town. */
	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			CompoundTag tag = perintah(server, p.getUUID());
			String id = tag.getStringOr("utusan", "");
			if (id.isEmpty()) {
				continue;
			}
			Entity e;
			try {
				e = p.level().getEntity(UUID.fromString(id));
			} catch (IllegalArgumentException ex) {
				e = null;
			}
			if (!(e instanceof KultivatorNpc envoy) || !envoy.isAlive()) {
				if (p.level().getGameTime() - tag.getLongOr("utusan_t", 0L) > 200L) {
					tag.remove("utusan");
					simpan(server, p.getUUID(), tag);
					Msg.chat(p, Component.translatable("kultivator.kota_besar.istana.utusan_hilang").withStyle(ChatFormatting.RED));
				}
				continue;
			}
			tag.putLong("utusan_t", p.level().getGameTime());
			ListTag list = tag.getListOrEmpty("tugas");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				if (!"kawal".equals(t.getStringOr("jenis", "")) || !t.contains("situs")) {
					continue;
				}
				BlockPos goal = Towns.centre(server, t.getLongOr("situs", 0L));
				if (goal == null) {
					goal = new BlockPos(t.getIntOr("x", 0), envoy.getBlockY(), t.getIntOr("z", 0));
				}
				if (Math.hypot(envoy.getX() - goal.getX(), envoy.getZ() - goal.getZ()) <= 40.0D) {
					envoy.say(Component.translatable("kultivator.kota_besar.istana.utusan_tiba"), 80);
					HidupApi.klip(envoy, GerakId.HORMAT_DALAM, 0, 60);
					envoy.setCompanion(null, 0L);
					Voice.later(80, envoy::vanish);
					tag.remove("utusan");
					simpan(server, p.getUUID(), tag);
					tuntas(p, "kawal");
				}
			}
		}
	}

	private static void bawaUtusan(ServerPlayer p, CompoundTag tag) {
		ServerLevel level = p.level();
		long town = tag.getLongOr("town", 0L);
		KultivatorNpc envoy = HidupApi.tubuh(level, town, NpcRole.MENTERI, p.blockPosition().offset(1, 0, 1), p.getYRot());
		if (envoy == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_besar.istana.utusan_gagal").withStyle(ChatFormatting.RED));
			return;
		}
		envoy.setPlate(Component.translatable("kultivator.kota_besar.istana.utusan_nama", KotaNames.person(envoy.look(), envoy.role())));
		envoy.memory().remove("hold");
		envoy.setCompanion(p.getUUID(), level.getGameTime() + 48000L);
		envoy.setLonely(160.0D, 12000);
		tag.putString("utusan", envoy.getUUID().toString());
		tag.putLong("utusan_t", level.getGameTime());
		simpan(level.getServer(), p.getUUID(), tag);
		envoy.say(Component.translatable("kultivator.kota_besar.istana.utusan_ikut"), 80);
	}

	// ================================================================== sheets

	private static boolean audiensiBuka(MinecraftServer server) {
		long t = Kota.timeOfDay(server);
		return t >= AUDIENSI_MULAI && t < AUDIENSI_AKHIR;
	}

	@Override
	public void lembar(ServerPlayer p, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		MinecraftServer server = p.level().getServer();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(npc != null && npc.role() == NpcRole.PANGERAN_KOTA
				? "kultivator.kota_besar.istana.audiensi" : "kultivator.kota_besar.istana.perintah"));
		tag.remove(LembarKunci.STATUS);
		tag.remove(LembarKunci.SISA_TICK);
		tag.remove(LembarKunci.Acara.JADWAL);
		Kandidat k = KotaBesarKota.dariSitus(server.overworld(), town);
		if (k != null) {
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, KotaBesarNames.pangeran(server.overworld().getSeed(), k.index()));
		}
		boolean tamu = KotaBesarData.get(server).punyaGelar(p.getUUID(), "tamu_istana");
		CompoundTag q = new CompoundTag();
		ListTag answers = new ListTag();
		if (npc != null && npc.role() == NpcRole.PANGERAN_KOTA) {
			int nb = KotaData.get(server).reputation(town, p.getUUID());
			if (nb < NB_AUDIENSI) {
				KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.istana.tolak_nb"),
						Component.translatable("kultivator.kota_besar.istana.syarat_nb", NB_AUDIENSI, nb));
				return;
			}
			if (!audiensiBuka(server)) {
				KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.istana.tutup"),
						Component.translatable("kultivator.kota_besar.istana.jam", KotaBesarKalender.jam(AUDIENSI_MULAI), KotaBesarKalender.jam(AUDIENSI_AKHIR)));
				return;
			}
			q.putString(LembarKunci.ID, "audiensi");
			LembarKunci.teks(q, LembarKunci.Acara.SOAL, Component.translatable(tamu ? "kultivator.kota_besar.istana.sapa_tamu" : "kultivator.kota_besar.istana.sapa"));
			for (String a : List.of("hormat", "tanya", "pamit")) {
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.TEKS, Component.translatable("kultivator.kota_besar.istana.jawab." + a));
				answers.add(row);
			}
			q.put(LembarKunci.Acara.JAWABAN, answers);
			q.putInt(LembarKunci.Acara.SISA, 1);
			tag.put(LembarKunci.Acara.TEKA, q);
			return;
		}
		if (!tamu) {
			KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.istana.belum_tamu"),
					Component.translatable("kultivator.kota_besar.istana.syarat_tamu"));
			return;
		}
		CompoundTag orders = perintah(server, p.getUUID());
		boolean current = orders.getLongOr("masa", -1L) == masa(server);
		ListTag rows = new ListTag();
		if (current) {
			ListTag list = orders.getListOrEmpty("tugas");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				String jenis = t.getStringOr("jenis", "");
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.istana.tugas." + jenis));
				row.putInt(LembarKunci.HARI, (int) ((masa(server) + 1) * MASA));
				Component state = t.getBooleanOr("selesai", false) ? Component.translatable("kultivator.kota_besar.istana.status.selesai")
						: t.contains("situs") ? Component.translatable("kultivator.kota_besar.istana.status.arah", t.getIntOr("x", 0), t.getIntOr("z", 0),
						Barang.nb(t.getLongOr("imbalan", 0L))) : Component.translatable("kultivator.kota_besar.istana.status.cari", Barang.nb(t.getLongOr("imbalan", 0L)));
				LembarKunci.teks(row, LembarKunci.CATATAN, state);
				rows.add(row);
			}
		}
		tag.put(LembarKunci.Acara.JADWAL, rows);
		q.putString(LembarKunci.ID, "perintah");
		LembarKunci.teks(q, LembarKunci.Acara.SOAL, Component.translatable(current ? "kultivator.kota_besar.istana.menteri_ada"
				: "kultivator.kota_besar.istana.menteri_baru", Barang.nb(imbalan(Barang.pita(p)))));
		for (String a : current ? List.of("utusan", "segarkan", "pamit") : List.of("terima", "segarkan", "pamit")) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.TEKS, Component.translatable("kultivator.kota_besar.istana.jawab." + a));
			answers.add(row);
		}
		q.put(LembarKunci.Acara.JAWABAN, answers);
		q.putInt(LembarKunci.Acara.SISA, 1);
		tag.put(LembarKunci.Acara.TEKA, q);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.istana.catatan", MASA));
	}

	@Override
	public void aksi(ServerPlayer p, long town, @Nullable KultivatorNpc npc, String[] op) {
		if (!LembarKunci.Acara.OP_JAWAB.equals(op[0]) || npc == null) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		String what = LembarKunci.arg(op, 1);
		int pick = LembarKunci.argInt(op, 2, -1);
		KotaBesarData data = KotaBesarData.get(server);
		if ("audiensi".equals(what) && npc.role() == NpcRole.PANGERAN_KOTA) {
			if (KotaData.get(server).reputation(town, p.getUUID()) < NB_AUDIENSI || !audiensiBuka(server)) {
				return;
			}
			switch (pick) {
				case 0 -> {
					boolean fresh = data.beriGelar(p.getUUID(), "tamu_istana");
					HidupApi.tatap(npc, p);
					Voice.say(npc, "kotabesar.palace_audience", null);
					npc.say(Component.translatable("kultivator.kota_besar.istana.pangeran_kata"), 120);
					if (fresh) {
						Msg.title(p, Component.translatable("kultivator.kota_besar.gelar.tamu_istana").withStyle(ChatFormatting.GOLD),
								Component.translatable("kultivator.kota_besar.istana.gelar_sub"), 10, 70, 20);
						CeritaApi.kabar(p.level(), town, "kota_besar.tamu_istana", p.getDisplayName());
					}
					KotaBesarKalender.segarkan(p, Component.translatable(fresh ? "kultivator.kota_besar.istana.diterima" : "kultivator.kota_besar.istana.diterima_lagi"), true);
				}
				case 1 -> {
					long day = Kota.day(server);
					npc.say(Component.translatable("kultivator.kota_besar.istana.cerita." + Math.floorMod(day, 12L)), 160);
					KotaBesarKalender.segarkan(p, Component.translatable("kultivator.kota_besar.istana.cerita." + Math.floorMod(day, 12L)), true);
				}
				default -> HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
			}
			return;
		}
		if (!"perintah".equals(what) || !data.punyaGelar(p.getUUID(), "tamu_istana")) {
			return;
		}
		CompoundTag orders = perintah(server, p.getUUID());
		boolean current = orders.getLongOr("masa", -1L) == masa(server);
		switch (pick) {
			case 0 -> {
				if (!current) {
					CompoundTag fresh = terbitkan(p, town);
					simpan(server, p.getUUID(), fresh);
					HidupApi.klip(npc, GerakId.MENULIS, 0, 60);
					KotaBesarKalender.segarkan(p, Component.translatable("kultivator.kota_besar.istana.terbit"), true);
				} else if (!orders.getStringOr("utusan", "").isEmpty()) {
					KotaBesarKalender.segarkan(p, Component.translatable("kultivator.kota_besar.istana.utusan_sudah"), false);
				} else if (kawalSelesai(orders)) {
					KotaBesarKalender.segarkan(p, Component.translatable("kultivator.kota_besar.istana.kawal_sudah"), false);
				} else {
					bawaUtusan(p, orders);
					KotaBesarKalender.segarkan(p, Component.translatable("kultivator.kota_besar.istana.utusan_bawa"), true);
				}
			}
			case 1 -> {
				if (current) {
					// a lair found since: point the empty orders at it
					ListTag list = orders.getListOrEmpty("tugas");
					for (int i = 0; i < list.size(); i++) {
						CompoundTag t = list.getCompoundOrEmpty(i);
						if (t.contains("situs") || t.getBooleanOr("selesai", false)) {
							continue;
						}
						String jenis = t.getStringOr("jenis", "");
						DuniaSites.Site target = jenis.equals("kawal") ? kotaTujuan(p) : terdekat(p, "gua/" + jenis, 0);
						if (target != null) {
							t.putLong("situs", target.id());
							t.putInt("x", target.pos().getX());
							t.putInt("z", target.pos().getZ());
						}
					}
					orders.put("tugas", list);
					simpan(server, p.getUUID(), orders);
				}
				KotaBesarKalender.segarkan(p);
			}
			default -> HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
		}
	}

	private static boolean kawalSelesai(CompoundTag orders) {
		ListTag list = orders.getListOrEmpty("tugas");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			if ("kawal".equals(t.getStringOr("jenis", ""))) {
				return t.getBooleanOr("selesai", false) || !t.contains("situs");
			}
		}
		return true;
	}

	/** The palace entries of the dialog's services. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer p, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (!Hukum.ibuKota(p.level().getServer(), npc.town())) {
			return out;
		}
		String lot = npc.memory().getStringOr("kotabesar_layanan", "");
		if (npc.role() == NpcRole.PANGERAN_KOTA) {
			out.add(new TempatDialog.JasaEntri(LembarKunci.Acara.LAYAR, Component.translatable("kultivator.kota_besar.jasa.audiensi"),
					LembarKunci.Ikon.ISTANA, Component.translatable("kultivator.kota_besar.istana.jam", KotaBesarKalender.jam(AUDIENSI_MULAI),
					KotaBesarKalender.jam(AUDIENSI_AKHIR)), false, null));
		} else if (npc.role() == NpcRole.MENTERI && "perintah_istana".equals(lot)) {
			boolean tamu = KotaBesarData.get(p.level().getServer()).punyaGelar(p.getUUID(), "tamu_istana");
			out.add(new TempatDialog.JasaEntri(LembarKunci.Acara.LAYAR, Component.translatable("kultivator.kota_besar.jasa.perintah"),
					LembarKunci.Ikon.ISTANA, Component.translatable("kultivator.kota_besar.jasa.perintah.catatan"), !tamu,
					Component.translatable("kultivator.kota_besar.istana.syarat_tamu")));
		}
		return out;
	}
}
