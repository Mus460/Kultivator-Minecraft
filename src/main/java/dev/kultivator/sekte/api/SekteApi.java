package dev.kultivator.sekte.api;

import dev.kultivator.Kultivator;
import dev.kultivator.sekte.SekteIds;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The cross-module services of Sekte yang Hidup (DESIGN 36.3.1): one implementation per service, set by its owner module
 * from its init, a no-op {@code NONE} default while the owner is missing or switched off. The ten modules call each other
 * only through these, {@link SekteBus} and the lead's {@code sekte.Ingatan}, so no module imports another's package.
 * Setting a service twice logs and replaces.
 */
public final class SekteApi {

	private static HatiService hati = HatiService.NONE;
	private static HubunganService hubungan = HubunganService.NONE;
	private static TurnamenService turnamen = TurnamenService.NONE;
	private static TanggaService tangga = TanggaService.NONE;
	private static PaviliunService paviliun = PaviliunService.NONE;
	private static TetuaService tetua = TetuaService.NONE;
	private static MisiService misi = MisiService.NONE;
	private static LambangService lambang = LambangService.NONE;
	private static DiplomasiService diplomasi = DiplomasiService.NONE;
	private static KebunService kebun = KebunService.NONE;

	/** Module P. */
	public static HatiService hati() {
		return hati;
	}

	/** Module R. */
	public static HubunganService hubungan() {
		return hubungan;
	}

	/** Module T. */
	public static TurnamenService turnamen() {
		return turnamen;
	}

	/** Module G. */
	public static TanggaService tangga() {
		return tangga;
	}

	/** Module A. */
	public static PaviliunService paviliun() {
		return paviliun;
	}

	/** Module E. */
	public static TetuaService tetua() {
		return tetua;
	}

	/** Module X. */
	public static MisiService misi() {
		return misi;
	}

	/** Module S. */
	public static LambangService lambang() {
		return lambang;
	}

	/** Module D. */
	public static DiplomasiService diplomasi() {
		return diplomasi;
	}

	/** Module W. */
	public static KebunService kebun() {
		return kebun;
	}

	public static void setHati(@Nullable HatiService service) {
		hati = set("hati", hati, service, HatiService.NONE);
	}

	public static void setHubungan(@Nullable HubunganService service) {
		hubungan = set("hubungan", hubungan, service, HubunganService.NONE);
	}

	public static void setTurnamen(@Nullable TurnamenService service) {
		turnamen = set("turnamen", turnamen, service, TurnamenService.NONE);
	}

	public static void setTangga(@Nullable TanggaService service) {
		tangga = set("tangga", tangga, service, TanggaService.NONE);
	}

	public static void setPaviliun(@Nullable PaviliunService service) {
		paviliun = set("paviliun", paviliun, service, PaviliunService.NONE);
	}

	public static void setTetua(@Nullable TetuaService service) {
		tetua = set("tetua", tetua, service, TetuaService.NONE);
	}

	public static void setMisi(@Nullable MisiService service) {
		misi = set("misi", misi, service, MisiService.NONE);
	}

	public static void setLambang(@Nullable LambangService service) {
		lambang = set("lambang", lambang, service, LambangService.NONE);
	}

	public static void setDiplomasi(@Nullable DiplomasiService service) {
		diplomasi = set("diplomasi", diplomasi, service, DiplomasiService.NONE);
	}

	public static void setKebun(@Nullable KebunService service) {
		kebun = set("kebun", kebun, service, KebunService.NONE);
	}

	/** The module ids ({@link SekteIds#MODULES}) whose service is set right now. */
	public static List<String> liveModules() {
		List<String> live = new ArrayList<>();
		if (hati != HatiService.NONE) {
			live.add(SekteIds.P);
		}
		if (hubungan != HubunganService.NONE) {
			live.add(SekteIds.R);
		}
		if (turnamen != TurnamenService.NONE) {
			live.add(SekteIds.T);
		}
		if (tangga != TanggaService.NONE) {
			live.add(SekteIds.G);
		}
		if (paviliun != PaviliunService.NONE) {
			live.add(SekteIds.A);
		}
		if (tetua != TetuaService.NONE) {
			live.add(SekteIds.E);
		}
		if (misi != MisiService.NONE) {
			live.add(SekteIds.X);
		}
		if (lambang != LambangService.NONE) {
			live.add(SekteIds.S);
		}
		if (diplomasi != DiplomasiService.NONE) {
			live.add(SekteIds.D);
		}
		if (kebun != KebunService.NONE) {
			live.add(SekteIds.W);
		}
		return live;
	}

	private static <T> T set(String name, T current, @Nullable T service, T none) {
		if (service == null) {
			return none;
		}
		if (current != none && current != service) {
			Kultivator.LOGGER.warn("Sekte service {} set twice; the newer one wins", name);
		}
		return service;
	}

	private SekteApi() {
	}
}
