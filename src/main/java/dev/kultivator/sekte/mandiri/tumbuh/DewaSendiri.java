package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tawaran Dewa dipilih sendiri (DESIGN 39.5.5): at each new 9-star title the old code keeps a list of Dewa arts the
 * master may pick from ({@code DiscipleCultivation.dewaPending}, offered in chat and on the Teknik tab). When the master
 * has not chosen within {@code tungguDaoHari} days (Seimbang 3, Penuh at once; Patuh never), the disciple picks one itself
 * by its path ({@link #skor}) through {@code DiscipleCultivation.chooseDewaSelf} (every gate again, the old FX), writes the
 * day in its diary and keeps a memory of it. The master is told once when the offer opens (the old click line only
 * reaches a master who was online at that minute).
 * <p>
 * The path's leaning by art type: Pedang strikes and finishers, Penjaga guards and domains, Alkemis healing, Pertapa
 * passives, senses and transformations, Pemburu strikes and binds, Petualang movement, Formasi domains and binds,
 * Cendekia senses and passives, Pedagang movement and healing, Iblis strikes and areas; +1 for an art of its own Dao over a
 * universal one.
 */
public final class DewaSendiri {

	// ================================================================== the waiting

	/** Once a minute per loaded disciple. */
	static void periksa(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		List<String> pending = DiscipleCultivation.dewaPending(data);
		long since = DataTumbuh.tungguHari(data, DataTumbuh.T_DEWA);
		if (pending.isEmpty()) {
			if (since >= 0L) {
				DataTumbuh.setTunggu(data, DataTumbuh.T_DEWA, -1L);
			}
			return;
		}
		if (!MandiriApi.aktif() || entity.ownerId() == null || data.breakingThrough()) {
			return;
		}
		Kebijakan policy = MandiriApi.kebijakan(entity);
		if (policy.mode() == ModeMandiri.PATUH) {
			return;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		if (since < 0L) {
			DataTumbuh.setTunggu(data, DataTumbuh.T_DEWA, today);
			TechniqueSpec lean = pilih(entity, pending);
			KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEKNIK, KabarTumbuh.baris("dewa.tawaran", 0xFFE9A8, KabarTumbuh.nama(entity),
					pending.size(), policy.tungguDaoHari(), lean == null ? net.minecraft.network.chat.Component.empty()
							: lean.displayName().copy().withColor(0xFFD54A)));
			if (policy.tungguDaoHari() > 0) {
				return;
			}
		}
		if (today < Math.max(0L, since) + policy.tungguDaoHari()) {
			return;
		}
		pilihSendiri(entity, pending);
	}

	/** The disciple takes the art its path likes most. True when learned. */
	static boolean pilihSendiri(DiscipleEntity entity, List<String> pending) {
		TechniqueSpec spec = pilih(entity, pending);
		if (spec == null || !DiscipleCultivation.chooseDewaSelf(entity, spec.id())) {
			return false;
		}
		DiscipleData data = entity.data();
		DataTumbuh.setTunggu(data, DataTumbuh.T_DEWA, -1L);
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_TEKNIK, 1);
		Ingatan.remember(entity.level().getServer(), data, "mandiri.dewa_sendiri", null, 7, KabarTumbuh.P + "ingat.dewa_sendiri",
				spec.displayName().getString());
		KabarTumbuh.catat(entity, "belajar", "dewa.pilih", EntriHarian.Suasana.BANGGA, true, "t:" + spec.id());
		SekteSuara.line(entity, "dewa_sendiri", "technique", 0);
		if (entity.owner() == null) {
			KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEKNIK, KabarTumbuh.baris("dewa.pilih_jauh", 0xFFD54A, KabarTumbuh.nama(entity),
					spec.displayName().copy().withColor(0xFFE9A8)).withStyle(ChatFormatting.BOLD));
		}
		return true;
	}

	/** The pending art its path likes most, or null. */
	static @Nullable TechniqueSpec pilih(DiscipleEntity entity, List<String> pending) {
		JalanHidup path = MandiriApi.jalan(entity.data());
		TechniqueSpec best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (String id : pending) {
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null) {
				continue;
			}
			double score = skor(path, spec) + (spec.dao() != null ? 1.0D : 0.0D) + spec.dewaStars() * 0.01D;
			if (score > bestScore) {
				bestScore = score;
				best = spec;
			}
		}
		return best;
	}

	/** The path's leaning for an art type. */
	static double skor(@Nullable JalanHidup path, TechniqueSpec spec) {
		TechniqueType type = spec.type();
		if (path == null || type == null) {
			return 0.0D;
		}
		return switch (path) {
			case PEDANG -> type == TechniqueType.SERANG || type == TechniqueType.PAMUNGKAS ? 2.0D : 0.0D;
			case PENJAGA -> type == TechniqueType.LINDUNG || type == TechniqueType.WILAYAH ? 2.0D : 0.0D;
			case ALKEMIS -> type == TechniqueType.PULIH ? 2.0D : 0.0D;
			case PERTAPA -> type == TechniqueType.PASIF || type == TechniqueType.INDRA || type == TechniqueType.UBAH ? 1.5D : 0.0D;
			case PEMBURU -> type == TechniqueType.SERANG || type == TechniqueType.KENDALI ? 1.5D : 0.0D;
			case PETUALANG -> type == TechniqueType.GERAK ? 2.0D : 0.0D;
			case FORMASI -> type == TechniqueType.WILAYAH || type == TechniqueType.KENDALI ? 2.0D : 0.0D;
			case CENDEKIA -> type == TechniqueType.INDRA || type == TechniqueType.PASIF ? 1.5D : 0.0D;
			case PEDAGANG -> type == TechniqueType.GERAK || type == TechniqueType.PULIH ? 1.0D : 0.0D;
			case IBLIS -> type == TechniqueType.SERANG || type == TechniqueType.AREA ? 1.5D : 0.0D;
		};
	}

	/** {@code uji ... tumbuh dewa <murid>}: the waiting skipped. */
	static boolean paksa(DiscipleEntity entity) {
		List<String> pending = DiscipleCultivation.dewaPending(entity.data());
		return !pending.isEmpty() && pilihSendiri(entity, pending);
	}

	private DewaSendiri() {
	}
}
