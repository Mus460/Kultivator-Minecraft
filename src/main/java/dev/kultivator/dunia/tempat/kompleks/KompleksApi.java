package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * NPC sect compounds public face (contract C38.DS1, owner 38DS). Compounds exist only for entries of the single NPC
 * sect list ({@code GerbangPlan} / {@code DiplomasiService}, 38.0 rule 15); which compound a site or a position belongs
 * to comes from the deterministic plan ({@link RencanaKompleks}), never from stored data, so the answers are the same
 * before and after the compound's chunks were generated.
 */
public final class KompleksApi {

	private static volatile @Nullable MinecraftServer server;

	static void server(@Nullable MinecraftServer current) {
		server = current;
	}

	/** The NPC sect id ({@code s<i>}) of compound site {@code situsId}, or null. */
	public static @Nullable String sekteDi(long situsId) {
		RencanaKompleks.Kompleks k = kompleksSitus(situsId);
		return k == null ? null : k.sekte();
	}

	/**
	 * Strength of compound {@code situsId} as a realm index (38.R.5: the sect's {@code tier} - 1; 0 unknown): a tier 5
	 * sect's compound stands like a Golden Core.
	 */
	public static int kekuatan(long situsId) {
		RencanaKompleks.Kompleks k = kompleksSitus(situsId);
		if (k == null) {
			return 0;
		}
		int tier = SekteApi.diplomasi().tier(k.sekte());
		if (tier <= 0) {
			tier = k.entry().tier();
		}
		return Math.max(0, tier - 1);
	}

	/** The compound of the site with id {@code situsId}: the site's position looked up in the plan. */
	public static RencanaKompleks.@Nullable Kompleks kompleksSitus(long situsId) {
		MinecraftServer s = server;
		if (s == null) {
			return null;
		}
		DuniaSites.Site site = DuniaSites.byId(s, situsId);
		if (site == null || !site.dim().equals(Level.OVERWORLD)) {
			return null;
		}
		return RencanaKompleks.of(s.overworld().getSeed()).di(site.pos().getX(), site.pos().getZ(), RencanaKompleks.MARGIN);
	}

	/** The born compound at {@code pos} (overworld, within the compound box), or null. */
	public static RencanaKompleks.@Nullable Kompleks kompleksDi(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		RencanaKompleks.Kompleks k = RencanaKompleks.of(level.getSeed()).di(pos.getX(), pos.getZ(), 0);
		if (k == null || !RencanaKompleks.akhir(TempatZones.sumber(level), k).layak()) {
			return null;
		}
		return k;
	}

	/**
	 * Named spot {@code titik} of sect {@code sekteId}'s compound (the {@code TempatApi.spot} provider, 38.C.3.3):
	 * {@code puncak} (the master's seat, module D's war duel), {@code arena}, {@code gerbang}, {@code aula},
	 * {@code dalam}, {@code pasar}, {@code dermaga_awan} (37's cloud pier). The anchor's real position once its chunk
	 * loaded; before that the district centre on the planned floor for {@code puncak}/{@code arena}/{@code aula}; null when
	 * the sect has no born compound.
	 */
	public static @Nullable BlockPos spot(String sekteId, String titik) {
		MinecraftServer s = server;
		if (s == null || sekteId == null || titik == null) {
			return null;
		}
		BlockPos known = KompleksData.get(s).spot(sekteId, titik);
		if (known != null) {
			return known;
		}
		ServerLevel level = s.overworld();
		RencanaKompleks.Kompleks k = RencanaKompleks.of(level.getSeed()).kompleks(sekteId);
		if (k == null) {
			return null;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		Putusan p = RencanaKompleks.akhir(sumber, k);
		if (!p.layak()) {
			return null;
		}
		Distrik d = switch (titik) {
			case "puncak", "dermaga_awan" -> k.distrik().getLast();
			case "arena", "aula", "gerbang", "pasar" -> k.distrik().getFirst();
			case "dalam" -> k.distrik().size() > 2 ? k.distrik().get(1) : null;
			default -> null;
		};
		if (d == null) {
			return null;
		}
		// the planned street floor of the district (lot floor y 4 lies on dasarY - 1)
		return new BlockPos(d.pusatX(), p.dasarY() + d.dy(), d.pusatZ());
	}

	private KompleksApi() {
	}
}
