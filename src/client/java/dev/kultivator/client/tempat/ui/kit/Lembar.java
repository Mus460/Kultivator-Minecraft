package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of a sheet ({@code lembar}, DESIGN 38.U.1) or of one compound inside it, with the value types of
 * {@link LembarKunci} (T text, BR item, D list, IA/LA arrays). An absent key reads as "not applicable": empty text,
 * the given fallback, an empty list. Items are decoded lazily once per view (never in a static initialiser).
 */
public final class Lembar {

	private static final Lembar KOSONG = new Lembar(new CompoundTag());

	private final CompoundTag tag;
	private @Nullable ItemStack barang;
	private @Nullable List<Lembar> cacheDaftar;
	private @Nullable String cacheKunci;

	public Lembar(CompoundTag tag) {
		this.tag = tag == null ? new CompoundTag() : tag;
	}

	public static Lembar kosong() {
		return KOSONG;
	}

	public CompoundTag tag() {
		return tag;
	}

	public boolean ada(String kunci) {
		return tag.contains(kunci);
	}

	public boolean kosongSemua() {
		return tag.isEmpty();
	}

	/** Text value (type T); empty when absent. */
	public Component teks(String kunci) {
		return tag.contains(kunci) ? LembarKunci.teks(tag, kunci) : Component.empty();
	}

	/** Text value or {@code null} when absent (to skip a row). */
	public @Nullable Component teksAtau(String kunci) {
		return tag.contains(kunci) ? LembarKunci.teks(tag, kunci) : null;
	}

	public String s(String kunci) {
		return tag.getStringOr(kunci, "");
	}

	public String s(String kunci, String fallback) {
		return tag.getStringOr(kunci, fallback);
	}

	public int i(String kunci, int fallback) {
		return tag.getIntOr(kunci, fallback);
	}

	public long l(String kunci, long fallback) {
		return tag.getLongOr(kunci, fallback);
	}

	public float f(String kunci, float fallback) {
		return tag.getFloatOr(kunci, fallback);
	}

	public boolean b(String kunci) {
		return tag.getBooleanOr(kunci, false);
	}

	public int[] ia(String kunci) {
		return tag.getIntArray(kunci).orElse(new int[0]);
	}

	public long[] la(String kunci) {
		return tag.getLongArray(kunci).orElse(new long[0]);
	}

	/** List of strings (type SL). */
	public List<String> sl(String kunci) {
		ListTag list = tag.getListOrEmpty(kunci);
		if (list.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>(list.size());
		for (int k = 0; k < list.size(); k++) {
			list.getString(k).ifPresent(out::add);
		}
		return out;
	}

	/** Compound value (type C); an empty view when absent. */
	public Lembar sub(String kunci) {
		return tag.contains(kunci) ? new Lembar(tag.getCompoundOrEmpty(kunci)) : KOSONG;
	}

	/** List of compounds (type D), at most {@link LembarKunci#DAFTAR_MAKS} entries; cached for the last key asked. */
	public List<Lembar> daftar(String kunci) {
		if (cacheDaftar != null && kunci.equals(cacheKunci)) {
			return cacheDaftar;
		}
		ListTag list = tag.getListOrEmpty(kunci);
		List<Lembar> out;
		if (list.isEmpty()) {
			out = Collections.emptyList();
		} else {
			out = new ArrayList<>(Math.min(list.size(), LembarKunci.DAFTAR_MAKS));
			for (int k = 0; k < list.size() && out.size() < LembarKunci.DAFTAR_MAKS; k++) {
				Tag entry = list.get(k);
				if (entry instanceof CompoundTag compound) {
					out.add(new Lembar(compound));
				}
			}
		}
		cacheDaftar = out;
		cacheKunci = kunci;
		return out;
	}

	/** The item under {@link LembarKunci#BARANG} (type BR); {@link ItemStack#EMPTY} when absent or no level. */
	public ItemStack barang() {
		if (barang == null) {
			Minecraft minecraft = Minecraft.getInstance();
			barang = minecraft.level == null || !tag.contains(LembarKunci.BARANG) ? ItemStack.EMPTY
					: LembarKunci.barang(tag, minecraft.level.registryAccess());
		}
		return barang;
	}

	/** Common entry state {@link LembarKunci#KEADAAN}, with {@link LembarKunci#TERKUNCI} folded in. */
	public String keadaan() {
		if (tag.getBooleanOr(LembarKunci.TERKUNCI, false)) {
			return LembarKunci.K_TERKUNCI;
		}
		return tag.getStringOr(LembarKunci.KEADAAN, LembarKunci.K_OK);
	}

	/** True when the entry can be acted on (state ok). */
	public boolean bisa() {
		return LembarKunci.K_OK.equals(keadaan());
	}

	public String id() {
		return tag.getStringOr(LembarKunci.ID, "");
	}
}
