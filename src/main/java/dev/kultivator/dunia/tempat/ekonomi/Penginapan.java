package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Inns of the capital and of the road posts (DESIGN 38.M.2.2): a room is 4 NB a night (2 at a road post); the night is
 * paid at the counter and spent in any bed; waking up afterwards gives Regeneration I for 60 s and "Istirahat Nyenyak"
 * (meditation XP ×1.10 for 20 minutes, see {@link Hidangan}). One paid night at a time; the room stays booked until the
 * next morning is used. The inn's screen also sells the four dishes.
 */
public final class Penginapan {

	public static final long KAMAR = 4L;
	public static final long KAMAR_POS = 2L;
	public static final int REGEN_TICK = 60 * 20;

	private static final String KAMAR_HARI = "kamar_hari";
	private static final String KAMAR_PAKAI = "kamar_pakai";
	private static final Set<NpcRole> PERAN = Set.of(NpcRole.PEMILIK_PENGINAPAN, NpcRole.PELAYAN);

	static void init() {
		TempatLayar.daftar(LembarKunci.Layanan.LAYAR_PENGINAPAN, Penginapan::aksi, Penginapan::bangun);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				bangunTidur(player);
			}
		});
	}

	/** Room price at this inn: road posts are cheaper; the town's service factor applies. */
	static long hargaKamar(ServerPlayer player, @Nullable KultivatorNpc npc) {
		boolean pos = false;
		if (npc != null) {
			DuniaSites.Site site = DuniaSites.byId(player.level().getServer(), npc.anchorSite());
			pos = site != null && site.kind().startsWith("pos/");
		}
		// one HargaKota.faktor (JASA) per price, 42.6.3
		return Barang.harga(pos ? KAMAR_POS : KAMAR, HargaKota.faktor(player, npc, Kategori.JASA));
	}

	/** True when the player already slept on a paid room this night. */
	static boolean sudah(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = EkonomiData.get(server).pemain(player.getUUID());
		return tag.getLongOr(KAMAR_PAKAI, Long.MIN_VALUE) >= Barang.hari(server) - 1L && tag.getLongOr(KAMAR_PAKAI, Long.MIN_VALUE) == tag.getLongOr(KAMAR_HARI, -2L);
	}

	private static boolean dipesan(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = EkonomiData.get(server).pemain(player.getUUID());
		long night = tag.getLongOr(KAMAR_HARI, Long.MIN_VALUE);
		return night >= Barang.hari(server) - 1L && tag.getLongOr(KAMAR_PAKAI, Long.MIN_VALUE) != night;
	}

	private static void bangunTidur(ServerPlayer player) {
		if (!dipesan(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.pemain(player.getUUID());
		tag.putLong(KAMAR_PAKAI, tag.getLongOr(KAMAR_HARI, 0L));
		data.touch();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICK, 0));
		Hidangan.mulai(player, Hidangan.NYENYAK_SAMPAI, Hidangan.NYENYAK_TICK);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.penginapan.bangun"), true);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.penginapan.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_GERBANG, npc);
		tag.putString(LembarKunci.JENIS, LembarKunci.Layanan.JENIS_PENGINAPAN);
		ListTag menu = new ListTag();
		CompoundTag kamar = new CompoundTag();
		kamar.putString(LembarKunci.ID, "kamar");
		LembarKunci.teks(kamar, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.penginapan.kamar"));
		kamar.putInt(LembarKunci.IKON, LembarKunci.Ikon.PENGINAPAN);
		long harga = hargaKamar(player, npc);
		kamar.putLong(LembarKunci.HARGA, harga);
		LembarKunci.teks(kamar, LembarKunci.Layanan.EFEK, Component.translatable("kultivator.tempat_ekonomi.penginapan.kamar.efek"));
		if (dipesan(player)) {
			kamar.putString(LembarKunci.KEADAAN, LembarKunci.K_BATAS);
			LembarKunci.teks(kamar, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.penginapan.sudah_pesan"));
		} else {
			kamar.putString(LembarKunci.KEADAAN, EkonomiConfig.get().toko ? Lembar.keadaan(player, harga, false) : LembarKunci.K_TUTUP);
		}
		menu.add(kamar);
		for (net.minecraft.nbt.Tag row : Hidangan.menu(player)) {
			menu.add(row);
		}
		tag.put(LembarKunci.Layanan.MENU, menu);
		tag.put(LembarKunci.AKTIF, Hidangan.efekAktif(player));
		tag.putBoolean(LembarKunci.Layanan.SUDAH_MENGINAP, sudah(player));
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!LembarKunci.Layanan.OP_PESAN.equals(parts[0])) {
			return;
		}
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		String id = LembarKunci.arg(parts, 1);
		if (!"kamar".equals(id)) {
			Hidangan.beli(player, npc, id);
			return;
		}
		if (!EkonomiConfig.get().toko || dipesan(player)) {
			return;
		}
		long harga = hargaKamar(player, npc);
		if (!NilaiBatu.pay(player, harga)) {
			Lembar.kurangUang(player, harga);
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		data.pemain(player.getUUID()).putLong(KAMAR_HARI, Barang.hari(server));
		data.touch();
		if (npc != null) {
			SosokApi.gerak(npc, GerakId.HORMAT, 0, 30);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.6F, 1.2F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.penginapan.dipesan", NilaiBatu.text(harga)), true);
	}

	private Penginapan() {
	}
}
