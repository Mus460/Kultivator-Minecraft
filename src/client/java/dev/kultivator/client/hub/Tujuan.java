package dev.kultivator.client.hub;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Gulungan Utama should show (DESIGN 43.4.3): a section ({@code HubNav.DIRI} ... {@code HubNav.DI_SINI}), a page id
 * of {@link HubPages} ({@code ""} = the section's last page this session), a sub id ({@code ""} = the page's last sub) and
 * an optional focus tag handed to {@code Page.fokus} (for example {@code {"spec": netId}}, {@code {"murid": entityId}},
 * {@code {"sekte": id}}). Opened with {@link HubNav#buka}.
 */
public record Tujuan(int bagian, String halaman, String sub, @Nullable CompoundTag fokus) {

	public Tujuan {
		halaman = halaman == null ? "" : halaman;
		sub = sub == null ? "" : sub;
	}

	/** Section {@code bagian}, page {@code halaman}, its last sub. */
	public static Tujuan of(int bagian, String halaman) {
		return new Tujuan(bagian, halaman, "", null);
	}

	/** Section {@code bagian} on its last page. */
	public static Tujuan bagian(int bagian) {
		return new Tujuan(bagian, "", "", null);
	}

	/** The same place on sub {@code sub}. */
	public Tujuan sub(String sub) {
		return new Tujuan(bagian, halaman, sub, fokus);
	}

	/** The same place with focus tag {@code f}. */
	public Tujuan fokus(@Nullable CompoundTag f) {
		return new Tujuan(bagian, halaman, sub, f);
	}
}
