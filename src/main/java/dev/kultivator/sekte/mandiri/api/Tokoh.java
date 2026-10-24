package dev.kultivator.sekte.mandiri.api;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A named character met by a hall's disciples (DESIGN 39.8.3; 39.F.1 adds {@link Peran#SAHABAT_LUAR}). Kept in
 * {@code MandiriData.tokoh}, at most 32 per hall (the longest unseen is forgotten). Names come from the existing
 * generators ({@code DiscipleNames} for cultivators, {@code KotaNames} for townsfolk) and are stored as text so a
 * character keeps its name. Immutable; modules replace the record. Pure data, safe on both sides.
 *
 * @param id     stable id ({@code <role>#<n>} per hall, or 39F's {@code <sectId>#<n>})
 * @param nama   display name as generated
 * @param peran  role and story arc
 * @param situs  the site/town id where it lives or was last met ({@code ""} when roaming)
 * @param watak  temperament key ({@code Sifat} name or a town trait)
 * @param sikap  attitude per soul, -100..100
 * @param jumpa  meetings so far
 * @param hari   world day of the last meeting
 */
public record Tokoh(String id, String nama, Peran peran, String situs, String watak, Map<UUID, Integer> sikap, int jumpa,
					long hari) {

	/** Roles and their arcs (39.8.3). */
	public enum Peran {
		/** A regular stall keeper: 5 % off, keeps one rare item a week. */
		PEDAGANG,
		/** A wanderer: comprehension, news of new places. */
		PENGEMBARA,
		/** A rival: 3-5 meetings, then the deciding duel. */
		SAINGAN,
		/** A sweetheart in town: a festival promise, maybe a candidate. */
		KENALAN_HATI,
		/** An old master: mastery +1, rarely a Dao hint. */
		AHLI_TUA,
		/** The pickpocket who got away. */
		PENCURI,
		/** A village head with recurring requests. */
		KEPALA_DESA,
		/** 39.F: a friend from another sect (helps on the road, refuses brawls against us). */
		SAHABAT_LUAR;

		public static Peran byName(@Nullable String name, Peran fallback) {
			if (name != null) {
				for (Peran value : values()) {
					if (value.name().equalsIgnoreCase(name)) {
						return value;
					}
				}
			}
			return fallback;
		}

		/** {@code kultivator.sekte.mandiri.inti.tokoh.<peran lower>}. */
		public String kunciNama() {
			return "kultivator.sekte.mandiri.inti.tokoh." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public Tokoh {
		id = id == null ? "" : id;
		nama = nama == null ? "" : nama;
		peran = peran == null ? Peran.PENGEMBARA : peran;
		situs = situs == null ? "" : situs;
		watak = watak == null ? "" : watak;
		sikap = sikap == null ? Map.of() : Map.copyOf(sikap);
		jumpa = Math.max(0, jumpa);
	}

	/** Attitude toward {@code soul} (0 when never met). */
	public int sikapKe(@Nullable UUID soul) {
		Integer value = soul == null ? null : sikap.get(soul);
		return value == null ? 0 : value;
	}

	/** A copy with the attitude toward {@code soul} moved by {@code delta} (clamped to -100..100). */
	public Tokoh ubahSikap(UUID soul, int delta) {
		Map<UUID, Integer> next = new HashMap<>(sikap);
		next.put(soul, Math.max(-100, Math.min(100, sikapKe(soul) + delta)));
		return new Tokoh(id, nama, peran, situs, watak, next, jumpa, hari);
	}

	/** A copy after one more meeting on {@code day} at {@code site}. */
	public Tokoh dijumpai(long day, @Nullable String site) {
		return new Tokoh(id, nama, peran, site == null || site.isEmpty() ? situs : site, watak, sikap, jumpa + 1, day);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putString("nama", nama);
		tag.putString("peran", peran.name());
		tag.putString("situs", situs);
		tag.putString("watak", watak);
		CompoundTag att = new CompoundTag();
		for (Map.Entry<UUID, Integer> entry : sikap.entrySet()) {
			att.putInt(entry.getKey().toString(), entry.getValue());
		}
		tag.put("sikap", att);
		tag.putInt("jumpa", jumpa);
		tag.putLong("hari", hari);
		return tag;
	}

	public static Tokoh fromTag(CompoundTag tag) {
		Map<UUID, Integer> att = new HashMap<>();
		CompoundTag raw = tag.getCompoundOrEmpty("sikap");
		for (String key : raw.keySet()) {
			try {
				att.put(UUID.fromString(key), raw.getIntOr(key, 0));
			} catch (IllegalArgumentException ignored) {
				// not a soul id: skipped
			}
		}
		return new Tokoh(tag.getStringOr("id", ""), tag.getStringOr("nama", ""),
				Peran.byName(tag.getStringOr("peran", ""), Peran.PENGEMBARA), tag.getStringOr("situs", ""),
				tag.getStringOr("watak", ""), att, tag.getIntOr("jumpa", 0), tag.getLongOr("hari", 0L));
	}
}
