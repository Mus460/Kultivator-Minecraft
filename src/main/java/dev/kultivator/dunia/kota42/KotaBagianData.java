package dev.kultivator.dunia.kota42;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared shape of the split-module SavedData of DESIGN 42.13.1 ({@code KotaEkonomiData}, {@code KotaAcaraData},
 * {@code KotaSekteData}, {@code KotaTumbuhData}, {@code RagamData}), pattern {@code KotaTemaData}: the owning module and its
 * sub-agents never edit the data class; each keeps its state in {@link #bagian(String) bagian(name)} (one
 * {@link CompoundTag} per sub-module, e.g. {@code rumah}, {@code toko}, {@code pasar_hantu} of 42C / 42C-2) and calls
 * {@link #ubah()} after a change. Per-place state goes under {@link #situs(String, long) situs(bagian, situsId)}.
 * New worlds only: no migration, no version field.
 */
public abstract class KotaBagianData extends SavedData {

	private static final String SITUS = "kota_";

	private final Map<String, CompoundTag> bagian = new HashMap<>();

	/**
	 * The live state compound of one sub-module, created empty on first use. Change it in place, then call {@link #ubah()}.
	 * {@code null} or "" means {@code "umum"}.
	 */
	public CompoundTag bagian(String nama) {
		String key = nama == null || nama.isEmpty() ? "umum" : nama;
		return bagian.computeIfAbsent(key, k -> new CompoundTag());
	}

	/** The live compound of one place inside a sub-module: {@code bagian(nama)["kota_<situsId>"]}, created on first use. */
	public CompoundTag situs(String nama, long situsId) {
		CompoundTag part = bagian(nama);
		String key = SITUS + situsId;
		CompoundTag town = part.getCompoundOrEmpty(key);
		if (!part.contains(key)) {
			part.put(key, town);
		}
		return town;
	}

	/** Names of the sub-modules that hold state. */
	public List<String> namaBagian() {
		return List.copyOf(bagian.keySet());
	}

	/** Forgets one place in every sub-module ({@code uji kota_hidup reset <bagian> <situs>}). */
	public void hapusSitus(long situsId) {
		String key = SITUS + situsId;
		for (CompoundTag part : bagian.values()) {
			part.remove(key);
		}
		setDirty();
	}

	/** Forgets everything ({@code uji kota_hidup reset <bagian>}). */
	public void hapusSemua() {
		bagian.clear();
		setDirty();
	}

	/** Marks the data dirty after a change to any compound returned above. */
	public void ubah() {
		setDirty();
	}

	/** Rough size in bytes ({@code uji kota_hidup anggaran}). */
	public int ukuran() {
		int n = 0;
		for (CompoundTag part : bagian.values()) {
			n += part.sizeInBytes();
		}
		return n;
	}

	// ------------------------------------------------------------------ codec helpers for the subclasses

	protected static <T extends KotaBagianData> T baca(T data, CompoundTag tag) {
		CompoundTag parts = tag.getCompoundOrEmpty("bagian");
		Map<String, CompoundTag> map = ((KotaBagianData) data).bagian;
		for (String key : parts.keySet()) {
			map.put(key, parts.getCompoundOrEmpty(key).copy());
		}
		return data;
	}

	protected CompoundTag tulis() {
		CompoundTag tag = new CompoundTag();
		CompoundTag parts = new CompoundTag();
		for (Map.Entry<String, CompoundTag> entry : bagian.entrySet()) {
			parts.put(entry.getKey(), entry.getValue().copy());
		}
		tag.put("bagian", parts);
		return tag;
	}
}
