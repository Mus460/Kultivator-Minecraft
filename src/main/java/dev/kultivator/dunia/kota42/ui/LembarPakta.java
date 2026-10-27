package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.sekte.KantorRekrut;
import dev.kultivator.dunia.kota42.sekte.KotaSekte;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.sekte.SerbuanKota;
import dev.kultivator.dunia.kota42.sekte.TugasKotaMurid;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Pakta.*;

/**
 * {@code pakta} (DESIGN 42.11.12, keys {@link KotaLembar.Pakta}) from 42E's {@link PaktaLindung}, {@link SerbuanKota} and
 * {@link TugasKotaMurid}: the protection pact of the player's sect over a town. Tab Pakta (since when, the week's tribute in
 * NB and surplus goods, renown, the guard disciples and their rotation, failed defence calls x/3; without a pact the NPC
 * protector and the ways to take the town, plus the checklist), tab Bela (the defence call: state, enemy, strength of both
 * sides, "Datang" / "Kirim murid", the last defence), tab Murid (every disciple of the master: idle ones get the three
 * duties, working ones can be called home) and tab Cabang (a jump to {@code cabang}). Drawn as the town mode of the 41
 * sect-politics screen ({@code LayarPolitik}).
 * <p>
 * When the town has no NPC protector the {@code cara} list holds one row {@code buat} ("Buat pakta"): {@code rebut:buat}
 * ends in {@link PaktaLindung#buat} (which {@link PaktaLindung#rebut} calls itself when nobody holds the town).
 * Ops: {@code ambil_upeti}, {@code rebut:CR}, {@code bela:BD}, {@code tugaskan:SOUL:TK}, {@code tarik_murid:SOUL},
 * {@code putus} (asks to confirm) then {@code putus_ya}, {@code cabang}.
 */
public final class LembarPakta {

	private static final String K = LembarKotaDasar.K + "pakta.";
	/** Pseudo way of {@code cara}: no NPC protector holds the town, the pact is simply made. */
	private static final String CR_BUAT = "buat";

	static void init() {
		String layar = LAYAR;
		LembarKotaDasar.daftar(layar, LembarPakta::bangun);
		LembarKotaDasar.bawaan(layar, OP_AMBIL_UPETI, (p, npc, situs, op) -> jawab(p, PaktaLindung.ambilUpeti(p, situs)));
		LembarKotaDasar.bawaan(layar, OP_REBUT, (p, npc, situs, op) -> jawab(p, PaktaLindung.rebut(p, situs, LembarKunci.arg(op, 1))));
		LembarKotaDasar.bawaan(layar, OP_BELA, (p, npc, situs, op) -> jawab(p, SerbuanKota.jawab(p, situs, LembarKunci.arg(op, 1))));
		LembarKotaDasar.bawaan(layar, OP_TUGASKAN, (p, npc, situs, op) -> {
			UUID soul = soul(LembarKunci.arg(op, 1));
			jawab(p, TugasKotaMurid.tugaskan(p, situs, soul, LembarKunci.arg(op, 2)));
		});
		LembarKotaDasar.bawaan(layar, OP_TARIK_MURID, (p, npc, situs, op) -> {
			UUID soul = soul(LembarKunci.arg(op, 1));
			if (soul == null) {
				LembarKotaDasar.pesan(p, Component.translatable(K + "tanpa_murid"), LembarKunci.PESAN_PERINGATAN);
			} else {
				jawab(p, TugasKotaMurid.tarik(p, soul));
			}
		});
		LembarKotaDasar.bawaan(layar, OP_PUTUS, (p, npc, situs, op) -> LembarKotaDasar.pesan(p, Component.translatable(K + "putus_tanya"), LembarKunci.PESAN_PERINGATAN));
		LembarKotaDasar.bawaan(layar, OP_PUTUS_YA, (p, npc, situs, op) -> jawab(p, PaktaLindung.putus(p, situs)));
		LembarKotaDasar.bawaan(layar, OP_CABANG, (p, npc, situs, op) -> LembarKotaDasar.buka(p, KotaLembar.Cabang.LAYAR, npc));
	}

	private static @Nullable UUID soul(String s) {
		try {
			return s == null || s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void jawab(ServerPlayer p, KotaSekte.Hasil h) {
		LembarKotaDasar.pesan(p, h.pesan(), h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN);
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		UUID id = player.getUUID();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_GIOK);
		LembarKotaDasar.kepala(tag, player, situs);
		if (!KotaSekte.aktif()) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "mati"));
		}
		if (Halls.hallOf(id) == null) {
			return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_aula"));
		}
		if (situs == 0L) {
			// a remote session of a master with pacts shows the first of them
			List<Long> milik = PaktaLindung.milik(server, id);
			if (milik.isEmpty()) {
				return LembarKotaDasar.kunci(tag, Component.translatable(K + "tanpa_tempat"));
			}
			situs = milik.get(0);
			LembarKotaDasar.kepala(tag, player, situs);
		}
		UUID pelindung = PaktaLindung.pelindung(situs);
		boolean milikku = id.equals(pelindung);

		CompoundTag p = new CompoundTag();
		p.putBoolean(ADA, milikku);
		if (milikku) {
			pakta(tag, p, player, server, situs);
		} else {
			tag.put(CALON, calon(player, situs, pelindung));
		}
		tag.put(PAKTA, p);
		tag.put(MURID, LembarKotaDasar.daftar(murid(server, player, situs)));

		List<CompoundTag> daftar = new ArrayList<>();
		for (long s : PaktaLindung.milik(server, id)) {
			CompoundTag d = new CompoundTag();
			d.putLong(LembarKunci.SITUS, s);
			LembarKunci.teks(d, LembarKunci.TEMPAT, PajakKota.nama(server, s));
			d.putBoolean(UPETI_SIAP, PaktaLindung.upetiSiap(server, s) > 0);
			d.putString(BELA, SerbuanKota.bela(server, s).keadaan());
			daftar.add(d);
		}
		tag.put(DAFTAR_PAKTA, LembarKotaDasar.daftar(daftar));
		return tag;
	}

	/** The pact card: since, tribute, goods, renown, guards, failures; and the defence block of tab Bela. */
	private static void pakta(CompoundTag tag, CompoundTag p, ServerPlayer player, MinecraftServer server, long situs) {
		UUID id = player.getUUID();
		p.putLong(SEJAK, PaktaLindung.sejak(server, situs));
		p.putLong(UPETI, PaktaLindung.upetiNb(server, situs, Barang.pita(player)));
		p.putBoolean(UPETI_SIAP, PaktaLindung.upetiSiap(server, situs) > 0);
		p.putInt(RENOWN, 5);
		p.putInt(ROTASI, TugasKotaMurid.ROTASI_JAGA);
		p.putInt(GAGAL, PaktaLindung.gagal(server, situs));
		p.putInt(GAGAL_MAKS, PaktaLindung.GAGAL_MAKS);
		List<CompoundTag> barang = new ArrayList<>();
		for (PaktaLindung.BarangUpeti b : PaktaLindung.barang(server, situs)) {
			Item item = item(b.kategori());
			if (item == null) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			LembarKunci.teks(t, LembarKunci.NAMA, b.kategori().nama());
			LembarKunci.barang(t, new ItemStack(item), server.registryAccess());
			t.putInt(LembarKunci.JUMLAH, b.jumlah());
			barang.add(t);
		}
		p.put(BARANG_UPETI, LembarKotaDasar.daftar(barang));
		long hari = KotaSekte.hari(server);
		List<CompoundTag> penjaga = new ArrayList<>();
		for (TugasKotaMurid.Tugas t : TugasKotaMurid.penjagaKota(server, situs, id)) {
			CompoundTag g = new CompoundTag();
			LembarKunci.teks(g, LembarKunci.NAMA, Component.literal(t.nama()));
			g.putString(KotaLembar.SOUL, t.soul().toString());
			g.putInt(KotaLembar.RANAH, t.ranah());
			g.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, t.sampai() - hari));
			penjaga.add(g);
		}
		p.put(PENJAGA, LembarKotaDasar.daftar(penjaga));

		SerbuanKota.Bela b = SerbuanKota.bela(server, situs);
		CompoundTag bela = new CompoundTag();
		bela.putString(LembarKunci.KEADAAN, b.keadaan());
		bela.putString(LembarKunci.JENIS, b.jenis());
		bela.putLong(LembarKunci.SISA_TICK, b.sisaTick());
		bela.putInt(KEKUATAN, b.kekuatan());
		bela.putInt(KEKUATAN_KITA, SerbuanKota.kekuatanKita(server, situs, id, player));
		if (b.terakhirHari() >= 0L) {
			CompoundTag t = new CompoundTag();
			t.putLong(LembarKunci.HARI, b.terakhirHari());
			t.putBoolean(MENANG, b.terakhirMenang());
			bela.put(TERAKHIR, t);
		}
		BlockPos at = b.depan() != null ? b.depan() : pusat(server, situs, player);
		bela.putInt(LembarKunci.X, at.getX());
		bela.putInt(LembarKunci.Z, at.getZ());
		tag.put(BELA, bela);
	}

	/** No pact of the player: the protector now, the ways to take the town, the checklist. */
	private static CompoundTag calon(ServerPlayer player, long situs, @Nullable UUID lain) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = new CompoundTag();
		List<CompoundTag> cara = new ArrayList<>();
		List<CompoundTag> syarat = new ArrayList<>();
		Component pertama = null;
		for (KantorRekrut.Syarat s : PaktaLindung.syarat(player, situs)) {
			syarat.add(LembarKotaDasar.syarat(s.ok(), s.teks(), s.punya()));
			if (!s.ok() && pertama == null) {
				pertama = s.teks();
			}
		}
		c.put(LembarKunci.SYARAT, LembarKotaDasar.daftar(syarat));
		if (lain != null) {
			// another player's sect holds the town: nothing to take
			LembarKunci.teks(c, KotaLembar.PELINDUNG, KotaSekte.namaSekte(lain));
		} else {
			String lama = PaktaLindung.sekteLama(server, situs, pusat(server, situs, player));
			if (lama == null) {
				cara.add(LembarKotaDasar.tombol(CR_BUAT, Component.translatable(K + "cara.buat"), pertama));
			} else {
				Component nama = SekteApi.diplomasi().name(lama);
				LembarKunci.teks(c, KotaLembar.PELINDUNG, nama == null ? Component.literal(lama) : nama);
				for (PaktaLindung.Cara r : PaktaLindung.cara(player, situs, lama)) {
					Component alasan = pertama != null ? pertama : r.bisa() ? null : r.alasan();
					cara.add(LembarKotaDasar.tombol(r.id(), Component.translatable(K + "cara." + r.id()), alasan));
				}
			}
		}
		c.put(CARA, LembarKotaDasar.daftar(cara));
		return c;
	}

	/** Every disciple of the master: a working one with its post, an idle loaded one with whether it may go. */
	private static List<CompoundTag> murid(MinecraftServer server, ServerPlayer player, long situs) {
		UUID id = player.getUUID();
		List<CompoundTag> out = new ArrayList<>();
		for (TugasKotaMurid.Tugas t : TugasKotaMurid.milik(server, id)) {
			CompoundTag m = new CompoundTag();
			m.putString(KotaLembar.SOUL, t.soul().toString());
			LembarKunci.teks(m, LembarKunci.NAMA, Component.literal(t.nama()));
			m.putInt(KotaLembar.RANAH, t.ranah());
			m.putString(TUGAS, t.tugas());
			LembarKunci.teks(m, LembarKunci.TEMPAT, PajakKota.nama(server, t.situs()));
			m.putBoolean(LembarKunci.BISA, true);
			out.add(m);
		}
		long now = player.level().getGameTime();
		for (DiscipleEntity d : Disciples.loaded(id)) {
			if (d.role() != DiscipleEntity.Role.DISCIPLE || TugasKotaMurid.tugas(d.soulId()) != null) {
				continue;
			}
			CompoundTag m = new CompoundTag();
			m.putString(KotaLembar.SOUL, d.soulId().toString());
			LembarKunci.teks(m, LembarKunci.NAMA, d.getDisplayName());
			m.putInt(KotaLembar.RANAH, d.data().realm().ordinal());
			boolean siap = !d.data().breakingThrough() && MandiriApi.luka(d.data(), now) == JenisLuka.TIDAK;
			m.putBoolean(LembarKunci.BISA, siap);
			if (!siap) {
				LembarKunci.teks(m, LembarKunci.ALASAN, Component.translatable(K + "murid.tidak_siap"));
			}
			out.add(m);
		}
		return out;
	}

	private static BlockPos pusat(MinecraftServer server, long situs, ServerPlayer player) {
		TempatInfo info = PajakKota.tempat(player);
		if (info != null && info.situsId() == situs) {
			return info.kotak().getCenter();
		}
		var site = dev.kultivator.dunia.DuniaSites.byId(server, situs);
		return site != null ? site.pos() : player.blockPosition();
	}

	/** The vanilla good standing for a category's surplus (the same table as {@code PaktaLindung.item}). */
	private static @Nullable Item item(Kategori k) {
		return switch (k) {
			case PANGAN -> Items.BREAD;
			case OBAT -> Items.HONEY_BOTTLE;
			case HERBA -> Items.SWEET_BERRIES;
			case SENJATA -> Items.IRON_INGOT;
			case ZIRAH -> Items.LEATHER;
			case JIMAT -> Items.PAPER;
			case BAHAN -> Items.OAK_PLANKS;
			case KAIN -> Items.STRING;
			case MEWAH -> Items.AMETHYST_SHARD;
			default -> null;
		};
	}

	private LembarPakta() {
	}
}
