package dev.kultivator.data;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Technique types (DESIGN 22.7): the codex filter, the disciple AI role and the default teachability. The ordinal is
 * not saved; ids are stable. PASIF is reserved (only the legacy Cloud Step uses it).
 */
public enum TechniqueType {
	SERANG("serang", true),
	AREA("area", true),
	KENDALI("kendali", true),
	GERAK("gerak", true),
	LINDUNG("lindung", true),
	PULIH("pulih", true),
	/** Teachable, but summons and counts at half (rounded up). */
	PANGGIL("panggil", true),
	WILAYAH("wilayah", false),
	UBAH("ubah", false),
	PASIF("pasif", false),
	PAMUNGKAS("pamungkas", true),
	INDRA("indra", false);

	private final String id;
	private final boolean teachableByDefault;

	TechniqueType(String id, boolean teachableByDefault) {
		this.id = id;
		this.teachableByDefault = teachableByDefault;
	}

	public String id() {
		return id;
	}

	/** Teachable to disciples unless the spec's TEACHABLE / NO_TEACH flag says otherwise. */
	public boolean teachableByDefault() {
		return teachableByDefault;
	}

	/** ECHO (the default level-4 bonus) is allowed only for instant SERANG, AREA, KENDALI and PAMUNGKAS (22.3.4). */
	public boolean echoType() {
		return this == SERANG || this == AREA || this == KENDALI || this == PAMUNGKAS;
	}

	/** Translation key {@code kultivator.teknik.type.<id>}. */
	public Component displayName() {
		return Component.translatable("kultivator.teknik.type." + id);
	}

	public static @Nullable TechniqueType byId(String id) {
		for (TechniqueType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
