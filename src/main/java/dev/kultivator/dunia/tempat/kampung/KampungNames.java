package dev.kultivator.dunia.tempat.kampung;

import net.minecraft.network.chat.Component;

/**
 * Stable names of the mortal villages and clan villages (DESIGN 38.C.2, owner 38DS; the posts' names are 38W's
 * {@code TempatNames}). A village name is a hash of its site id into the word list {@code kultivator.kampung.nama.kata.<0-23>}
 * ("Bambu Hijau", "Batu Lumut", ...) put into the size pattern {@code kultivator.kampung.nama.<ukuran>} ("Dusun %s",
 * "Desa %s", "Kota Kecamatan %s"); a clan village carries its clan's surname ({@code kultivator.kampung.marga.<0-15>},
 * proper nouns) in {@code kultivator.kampung.nama.klan} ("Desa Klan %s di %s"). The patriarch and the young master are
 * named from the same surname ({@link #marga}).
 */
public final class KampungNames {

	public static final int KATA = 24;
	public static final int MARGA = 16;

	/** The name of village {@code situsId} of size {@code ukuran} ({@code dusun}, {@code desa}, {@code desa_besar}, {@code klan}). */
	public static Component nama(String ukuran, long situsId) {
		long h = mix(situsId);
		Component kata = Component.translatable("kultivator.kampung.nama.kata." + Math.floorMod(h, KATA));
		if (ukuran.equals("klan")) {
			return Component.translatable("kultivator.kampung.nama.klan", marga(situsId), kata);
		}
		return Component.translatable("kultivator.kampung.nama." + ukuran, kata);
	}

	/** The lang key of the village's word ("Bambu Hijau"; a hall candidate's origin line reads "dari Desa <word>"). */
	public static String kataKunci(long situsId) {
		return "kultivator.kampung.nama.kata." + Math.floorMod(mix(situsId), KATA);
	}

	/** The clan surname of clan village {@code situsId} ("Xiao", "Lin", ...; the same in every language). */
	public static Component marga(long situsId) {
		return Component.translatable("kultivator.kampung.marga." + margaIndeks(situsId));
	}

	/** The surname index 0-15 of clan village {@code situsId}. */
	public static int margaIndeks(long situsId) {
		return Math.floorMod(mix(situsId ^ 0x6B6C616EL) >>> 7, MARGA);
	}

	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private KampungNames() {
	}
}
