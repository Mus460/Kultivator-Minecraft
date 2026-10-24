package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pencerahan, enlightenment (DESIGN 39.5.5, the fifth way of learning alone): at the end of a contemplation or a seclusion,
 * once a day, a chance of {@code 0.02 + comprehension / 5000} (at most 0.08) to understand one art of the disciple's
 * <b>own</b> Dao by itself: teachable, not a Dewa art, not a legacy art with module hooks, not a disciple-only art (those
 * come by themselves), a grade the realm allows (Foundation Kuning, Golden Core Misterius, Nascent Soul Bumi, Deity
 * Transformation Langit, Abadi only from True Immortal as for slips), the old gates ({@code teachBlockerSelf}) and a free
 * technique slot of its sect rank. The draw leans to the low grades (weight {@code (cap - grade + 1)^2}). At most one in
 * ten days. Seen: the Dao's learning stamp and a pillar; heard: "Aku memahaminya sendiri!".
 * <p>
 * Sumber: the sweeping monk of the Shaolin Sutra Library (<i>Demi-Gods and Semi-Devils</i>, Jin Yong), cited in
 * {@code sekte/paviliun/RakKitab}: understanding that comes from long quiet study.
 */
public final class Pencerahan {

	public static final double DASAR = 0.02D;
	public static final double PER_PEMAHAMAN = 1.0D / 5000.0D;
	public static final double MAKS = 0.08D;
	public static final int JEDA_HARI = 10;
	private static final String KEY = "pencerahan";

	/** The chance today for {@code data} (pure). */
	public static double peluang(DiscipleData data) {
		return Math.min(MAKS, DASAR + data.comprehension() * PER_PEMAHAMAN);
	}

	/** The highest grade tier enlightenment reaches at the disciple's realm, or -1 below Foundation. */
	static int batasTier(Realm realm) {
		return switch (realm) {
			case MORTAL, QI_REFINING -> -1;
			case FOUNDATION -> 0;
			case GOLDEN_CORE -> 1;
			case NASCENT_SOUL -> 2;
			case DEITY_TRANSFORMATION, DAO_UNION, IMMORTAL_ASCENSION -> 3;
			case TRUE_IMMORTAL -> TechniqueGrade.ABADI.tier();
		};
	}

	/** The arts enlightenment could bring now. */
	static List<TechniqueSpec> calon(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		Dao dao = data.dao();
		int cap = batasTier(data.realm());
		List<TechniqueSpec> list = new ArrayList<>();
		if (dao == null || cap < 0 || SectRanks.slotsFull(data)) {
			return list;
		}
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.dao() != dao || spec.dewa() || spec.grade() == TechniqueGrade.DEWA || spec.legacy() != null
					|| spec.has(TechniqueSpec.Flag.DISCIPLE_ONLY) || spec.grade().tier() > cap
					|| DiscipleCultivation.teachBlockerSelf(entity, spec) != null) {
				continue;
			}
			list.add(spec);
		}
		return list;
	}

	/**
	 * Today's roll at the end of a contemplation or seclusion ({@code paksa}: the test command skips the day rules and the
	 * chance). Returns the learned art, or null.
	 */
	static @Nullable TechniqueSpec coba(DiscipleEntity entity, boolean paksa) {
		DiscipleData data = entity.data();
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		CompoundTag record = DataTumbuh.sub(data, KEY);
		if (!paksa) {
			if (record.getLongOr("hari", -1L) == today || today < record.getLongOr("berhasil", -100L) + JEDA_HARI) {
				return null;
			}
			record.putLong("hari", today);
			if (entity.getRandom().nextDouble() >= peluang(data)) {
				return null;
			}
		}
		List<TechniqueSpec> list = calon(entity);
		if (list.isEmpty()) {
			return null;
		}
		int cap = Math.max(0, batasTier(data.realm()));
		double total = 0.0D;
		double[] weights = new double[list.size()];
		for (int i = 0; i < list.size(); i++) {
			int w = cap - list.get(i).grade().tier() + 1;
			weights[i] = Math.max(1, w * w);
			total += weights[i];
		}
		double roll = entity.getRandom().nextDouble() * total;
		TechniqueSpec spec = list.get(list.size() - 1);
		for (int i = 0; i < list.size(); i++) {
			roll -= weights[i];
			if (roll <= 0.0D) {
				spec = list.get(i);
				break;
			}
		}
		if (!DiscipleCultivation.learnSelf(entity, spec, 1)) {
			return null;
		}
		record.putLong("berhasil", today);
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_TEKNIK, 1);
		Ingatan.remember(server, data, "mandiri.pencerahan", null, 4, KabarTumbuh.P + "ingat.pencerahan", spec.displayName().getString());
		if (entity.level() instanceof ServerLevel level) {
			int color = spec.dao() == null ? TechniqueBooks.UNIVERSAL_COLOR : spec.dao().color();
			Fx.send(level, entity.position(), FxType.DAO_CHOSEN, spec.dao() == null ? -1 : spec.dao().ordinal(), TechniqueBooks.FX_LEARNED, 0.0F);
			Fx.pillar(level, entity.position(), color, 3.0F);
			level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.5F);
		}
		SekteSuara.line(entity, "belajar_sendiri", "cheer", 0);
		KabarTumbuh.catat(entity, "belajar", "belajar.pencerahan", EntriHarian.Suasana.BANGGA, true, "t:" + spec.id());
		KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEKNIK, KabarTumbuh.baris("belajar.pencerahan", 0x9FF5DC, KabarTumbuh.nama(entity),
				spec.displayName().copy().withColor(spec.grade().ribbon())));
		return spec;
	}

	private Pencerahan() {
	}
}
