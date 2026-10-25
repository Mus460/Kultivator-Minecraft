package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.hidup.PaviliunJasa;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Paviliun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Paviliun Jasa ({@code gabung_paviliun}, DESIGN 41.6.5 and 41.10.4, keys {@link Paviliun}): the screen of the pavilion
 * of the player's own sect. The sheet itself is written by 41C ({@code PaviliunJasa.lembar}: stock, PK prices after the
 * rank factor and the corak discount, locks with reasons, today's orders, sermons, hidden and locked tabs); this class
 * registers it and answers its ops.
 * <p>
 * Default ops (41C may replace each with {@link GabungLayar#daftarAksi}): {@code tukar:ID:N}, {@code serahkan:ID},
 * {@code ceramah:SOUL} call the matching {@code PaviliunJasa} method (it confirms success itself), {@link #OP_KEDIAMAN}
 * (the Kediaman tablet's button) opens {@code gabung_kediaman}.
 */
public final class LembarPaviliun {

	/** Op of the Kediaman tablet: opens {@code gabung_kediaman} (not in {@link Paviliun}: an extra verb of this screen). */
	public static final String OP_KEDIAMAN = "kediaman";

	static void init() {
		GabungLayar.daftar(Paviliun.LAYAR, LembarPaviliun::bangun);
		GabungLayar.bawaan(Paviliun.LAYAR, Paviliun.OP_TUKAR, (p, npcId, op) -> LembarPapan.hasil(p,
				PaviliunJasa.tukar(p, sekte(p), LembarKunci.arg(op, 1), Math.max(1, LembarKunci.argInt(op, 2, 1))), null));
		GabungLayar.bawaan(Paviliun.LAYAR, Paviliun.OP_SERAHKAN, (p, npcId, op) -> LembarPapan.hasil(p,
				PaviliunJasa.serahkan(p, sekte(p), LembarKunci.arg(op, 1)), null));
		GabungLayar.bawaan(Paviliun.LAYAR, Paviliun.OP_CERAMAH, (p, npcId, op) -> LembarPapan.hasil(p,
				PaviliunJasa.ceramah(p, sekte(p), LembarKunci.arg(op, 1)), null));
		GabungLayar.bawaan(Paviliun.LAYAR, OP_KEDIAMAN, (p, npcId, op) -> GabungLayar.bukaLayar(p, GabungLembar.Kediaman.LAYAR, npcId, null));
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		String sekte = sekte(player);
		if (sekte == null) {
			return LembarPapan.bukanAnggota(GabungLayar.kepala(player, null, Component.translatable("kultivator.gabung.ui2.paviliun.judul")));
		}
		CompoundTag tag = PaviliunJasa.lembar(player, sekte);
		if (tag.isEmpty()) {
			return LembarPapan.bukanAnggota(GabungLayar.kepala(player, sekte, Component.translatable("kultivator.gabung.ui2.paviliun.judul")));
		}
		CompoundTag kosong = new CompoundTag();
		LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable("kultivator.gabung.ui2.paviliun.kosong"));
		tag.put(LembarKunci.KOSONG, kosong);
		return tag;
	}

	private static String sekte(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		return k.sekteAktif();
	}

	private LembarPaviliun() {
	}
}
