package dev.kultivator.formasi.spec;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The 13 formation families (DESIGN 24.3). The first six are catalogue A (P7c-inti), the rest catalogue B
 * (P7c-katalog-ui). Saved by name; never reorder. Labels: {@code kultivator.formasi.family.<name>} (module A's text).
 */
public enum Family {
	PELINDUNG(0x4FC9A0),
	PENGUMPUL(0xB07CFF),
	PEMULIHAN(0x9FE8C8),
	PENEMPAAN(0xFF8A3D),
	PENANGKAL(0xFFD54A),
	PENGUAT(0xC8A64A),
	PERINGATAN(0xD08A50),
	PENYEMBUNYI(0x8FA890),
	ILUSI(0xE8C0FF),
	KENDALI(0xC0603A),
	PEMBUNUH(0xE04040),
	PERPINDAHAN(0xD8C8FF),
	GABUNGAN(0xFFF0C0);

	private final int color;

	Family(int color) {
		this.color = color;
	}

	/** True for the families of catalogue A. */
	public boolean moduleA() {
		return ordinal() <= PENGUAT.ordinal();
	}

	/** The support families (gathering, healing, refining): power ≤ P_SUPPORT_CAP and amplifiers ignored (24.2.9). */
	public boolean support() {
		return this == PENGUMPUL || this == PEMULIHAN || this == PENEMPAAN;
	}

	/** The family frame colour (24.8). */
	public int color() {
		return color;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component label() {
		return Component.translatable("kultivator.formasi.family." + id());
	}
}
