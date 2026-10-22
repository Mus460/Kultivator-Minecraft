package dev.kultivator.sekte.api;

import dev.kultivator.disciple.DiscipleData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Module E, Tetua &amp; Pewaris (DESIGN 36.2.6, contract C36.E1): the offices of elder-rank disciples and the second
 * generation Sect Master. The heir itself stays the existing {@code disciple.path.Heir}; E adds the succession on top and
 * answers {@link #heir} from it. The default has no offices (every factor 1).
 */
public interface TetuaService {

	TetuaService NONE = new TetuaService() {
	};

	/** The office of the disciple, or null. */
	default @Nullable Jabatan office(DiscipleData data) {
		return null;
	}

	/** Every filled office of the owner's hall and its holder's soul. */
	default Map<Jabatan, UUID> offices(UUID owner) {
		return Map.of();
	}

	/** The holder of {@code office}, or null when vacant. */
	default @Nullable UUID holder(UUID owner, Jabatan office) {
		return offices(owner).get(office);
	}

	/**
	 * The bonus a filled office gives its domain module: 1.0 vacant, up to 1.25 with a Nascent Soul+ holder (E's
	 * formula). P multiplies its contentment recovery by the HUKUM factor, A study by AJAR, W output by PUSAKA/OBAT, X the
	 * mission roll by LUAR.
	 */
	default double officeFactor(UUID owner, Jabatan office) {
		return 1.0D;
	}

	/** The named heir's soul (the existing Heir's), or null. */
	default @Nullable UUID heir(UUID owner) {
		return null;
	}
}
