package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.kapal.KapalStyle;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Screen {@code tema_galangan} (DESIGN 38.T.3.4, 38.T.13.3; keys {@link TemaLayar.Galangan}) at Mbah Lunas: the two keel
 * classes with price, days, band and the reason a row is locked; the player's order with its build stage (the client
 * shows the {@code kapal_galangan} hull of that stage in the 3D preview) and the days left; the hull liveries of the held
 * keel (60 NB). Built and handled here, logic in {@link Galangan}.
 */
final class GalanganLayar {

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaLayar.daftar(TemaLayar.Galangan.LAYAR, GalanganLayar::bangun, GalanganLayar::aksi);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		if (npc == null || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GONG_SHUMU)) {
			return null;
		}
		long situs = TokoPelabuhan.situs(player, npc);
		CompoundTag tag = PelabuhanLayar.kepala(player, "kultivator.kota_tema.pelabuhan.layar.galangan", LembarKunci.GAYA_KAYU, npc);
		ListTag kelas = new ListTag();
		for (String id : new String[] {"perahu", "sekte"}) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, id);
			LembarKunci.teks(e, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.pelabuhan.galangan.kelas." + id));
			e.putLong(LembarKunci.HARGA, Galangan.harga(id));
			e.putInt(LembarKunci.HARI, Galangan.hari(id));
			e.putInt(TemaLayar.Galangan.PITA_MIN, "sekte".equals(id) ? Galangan.PITA_SEKTE : Galangan.PITA_PERAHU);
			Component why = Galangan.alasan(player, situs, id);
			String state = why != null ? LembarKunci.K_TERKUNCI : NilaiBatu.total(player) < Galangan.harga(id) ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
			e.putString(LembarKunci.KEADAAN, state);
			if (why != null) {
				LembarKunci.teks(e, LembarKunci.ALASAN, why);
			}
			e.putString(LembarKunci.ENTITAS, "kultivator:kapal_galangan");
			kelas.add(e);
		}
		tag.put(TemaLayar.Galangan.KELAS, kelas);
		Galangan.Pesanan order = Galangan.pesanan(player.level().getServer(), situs, player.getUUID());
		if (order != null) {
			long today = PelabuhanKota.hari(player.level());
			CompoundTag c = new CompoundTag();
			c.putString(TemaLayar.Galangan.KELAS, order.kelas());
			c.putInt(LembarKunci.TAHAP, order.tahap(today));
			c.putInt(LembarKunci.TAHAP_TOTAL, Galangan.TAHAP + 1);
			c.putInt(LembarKunci.SISA_HARI, order.sisaHari(today));
			c.putString(LembarKunci.ENTITAS, "kultivator:kapal_galangan");
			c.putBoolean(TemaLayar.Galangan.SIAP, order.siap(today));
			if (!order.nama().isEmpty()) {
				LembarKunci.teks(c, LembarKunci.NAMA, Component.literal(order.nama()));
			}
			tag.put(TemaLayar.Galangan.PESANAN, c);
		}
		ListTag styles = new ListTag();
		for (KapalStyle s : KapalStyle.values()) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, s.id());
			LembarKunci.teks(e, LembarKunci.NAMA, Component.translatable("kultivator.kapal.style." + s.id()));
			e.putLong(LembarKunci.HARGA, Galangan.HARGA_LAYAR);
			if (s.unlock() != KapalStyle.Unlock.NONE) {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.pelabuhan.galangan.gaya_terkunci"));
			} else {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			}
			styles.add(e);
		}
		tag.put(TemaLayar.Galangan.GAYA_LAYAR, styles);
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		if (npc == null || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GONG_SHUMU)) {
			return;
		}
		long situs = TokoPelabuhan.situs(player, npc);
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case TemaLayar.Galangan.OP_PESAN -> {
				String kelas = "sekte".equals(LembarKunci.arg(parts, 1)) ? "sekte" : "perahu";
				Component r = Galangan.pesan(player, situs, kelas);
				PelabuhanLayar.pesan(player, r, Galangan.pesanan(player.level().getServer(), situs, player.getUUID()) != null);
			}
			case TemaLayar.Galangan.OP_AMBIL -> {
				boolean had = Galangan.pesanan(player.level().getServer(), situs, player.getUUID()) != null;
				Component r = Galangan.ambil(player, situs);
				PelabuhanLayar.pesan(player, r, had && Galangan.pesanan(player.level().getServer(), situs, player.getUUID()) == null);
			}
			case TemaLayar.Galangan.OP_LAYAR -> {
				Component r = Galangan.layar(player, LembarKunci.arg(parts, 1));
				PelabuhanLayar.pesan(player, r, r.getString().length() > 0 && !(r.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
						&& (t.getKey().endsWith("gaya_terkunci") || t.getKey().endsWith("pegang_lunas") || t.getKey().endsWith("kurang_uang"))));
			}
			default -> {
			}
		}
	}

	private GalanganLayar() {
	}
}
