package dev.kultivator.dunia.kota42.api;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.Sosok;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A read-only snapshot of one resident for screens, commands and other modules (DESIGN 42.11.2 Kartu Warga, 42.14.3
 * {@code warga info}), made by {@code WargaApi.info(warga)}. Values are copies: holding a WargaInfo never changes data.
 *
 * @param id       global resident id
 * @param situs    place id
 * @param nama     full name (family name + given name; story characters their story name)
 * @param wanita   sex
 * @param umur     age in Fana Years
 * @param tahap    life stage ({@link Tahap})
 * @param peran    work role
 * @param wajah    visible mood now
 * @param akar     spirit root 0 (mortal) or 1-5 ({@code RootGrade} ordinal + 1)
 * @param status   "hidup", "jalan", "sekte" or "mati"
 * @param tokoh    story character id or ""
 */
public record WargaInfo(int id, long situs, Component nama, boolean wanita, int umur, Tahap tahap, NpcRole peran, KodeWajah wajah,
		int akar, String status, String tokoh) {

	/** The life stages of DESIGN 42.4.3 with their age range in Fana Years and the Sosok age they show. */
	public enum Tahap {
		BAYI(0, 2, Sosok.ANAK),
		ANAK(3, 12, Sosok.ANAK),
		REMAJA(13, 15, Sosok.MUDA),
		MUDA(16, 29, Sosok.MUDA),
		DEWASA(30, 54, Sosok.DEWASA),
		TUA(55, 64, Sosok.TUA),
		PENSIUN(65, Integer.MAX_VALUE, Sosok.TUA);

		private static final Tahap[] ALL = values();

		public final int umurMin;
		public final int umurMaks;
		/** {@code Sosok.ANAK/MUDA/DEWASA/TUA} for {@code SosokApi.setUsia}. */
		public final int usiaSosok;

		Tahap(int umurMin, int umurMaks, int usiaSosok) {
			this.umurMin = umurMin;
			this.umurMaks = umurMaks;
			this.usiaSosok = usiaSosok;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** {@code kultivator.kota42.tahap.<id>}. */
		public Component nama() {
			return Component.translatable("kultivator.kota42.tahap." + id());
		}

		public static Tahap dari(int umur) {
			for (Tahap t : ALL) {
				if (umur <= t.umurMaks) {
					return t;
				}
			}
			return PENSIUN;
		}
	}
}
