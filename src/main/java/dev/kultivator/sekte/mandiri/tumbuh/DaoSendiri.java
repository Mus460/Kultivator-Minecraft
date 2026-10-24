package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Memilih Dao sendiri, the disciple choosing its own Dao (DESIGN 39.5.4). At Golden Core without a Dao it waits for the
 * master {@code tungguDaoHari} days (Seimbang 3, Penuh 0, Patuh forever; {@code extra.mandiri.tunggu.dao}); the master is
 * told once, with the disciple's own leaning and three buttons: {@code [Setujui <Dao>]} (the master's old
 * {@code chooseDao}, the master decided), {@code [Biarkan ia memilih]} (no more waiting) and the old screen stays open
 * for any other choice.
 * <p>
 * The score per Dao ({@link #skor}): root affinity +2; the path (Pedang SWORD +2, Alkemis FIRE/LIFE +1.5, Formasi SPACE
 * +1.5, Pertapa LIFE/SPACE +1, Pemburu BODY +2, Petualang LIGHTNING/SHADOW +1, Penjaga BODY/ICE +1); +0.5 per known art
 * of that Dao; +0.5 the master's Dao (following the master's example); +0.3 the Dao of its closest friend. Argmax.
 * <p>
 * The ceremony: the next night the planner sends it to contemplate one block under the open sky ({@link Merenung});
 * at the end of that contemplation {@link #upacara} runs {@code DiscipleCultivation.chooseDaoSelf} with the old FX
 * (pillar, ring, burst), a ring of the Dao's colour on the ground, a memory, the diary and the master's line.
 * <p>
 * Sumber: <i>Renegade Immortal</i> (Wang Lin comprehending his own Dao of Life and Death in seclusion), <i>I Shall Seal
 * the Heavens</i> (Meng Hao choosing his path at the Core Formation), cited in DESIGN 39.11.
 */
public final class DaoSendiri {

	private static final String KEY = "dao";

	/** One line of the score breakdown (for the test command and the notice). */
	public record Skor(Dao dao, double akar, double jalan, double teknik, double guru, double sahabat) {
		public double total() {
			return akar + jalan + teknik + guru + sahabat;
		}
	}

	// ================================================================== waiting

	/** Golden Core or more, no Dao yet, not breaking through: the choice is open. */
	static boolean terbuka(DiscipleData data) {
		return data.dao() == null && data.realm().atLeast(Realm.GOLDEN_CORE) && !data.breakingThrough();
	}

	/**
	 * The waiting is over and the disciple will choose itself at the next night's contemplation: the choice is open, the
	 * policy is not Patuh, the master was told at least {@code tungguDaoHari} days ago (or answered "let it choose").
	 */
	static boolean menunggu(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!MandiriApi.aktif() || entity.ownerId() == null || !terbuka(data)) {
			return false;
		}
		Kebijakan policy = MandiriApi.kebijakan(entity);
		if (policy.mode() == ModeMandiri.PATUH) {
			return false;
		}
		long since = DataTumbuh.tungguHari(data, DataTumbuh.T_DAO);
		if (since < 0L) {
			return false;
		}
		boolean let = "biar".equals(DataTumbuh.baca(data).getCompoundOrEmpty(KEY).getStringOr("jawaban", ""));
		long today = DataTumbuh.hari(entity.level().getServer());
		return let || today >= since + policy.tungguDaoHari();
	}

	/** The master's click {@code [Biarkan ia memilih]}. */
	static void biarkan(DiscipleEntity entity) {
		DataTumbuh.sub(entity.data(), KEY).putString("jawaban", "biar");
		if (DataTumbuh.tungguHari(entity.data(), DataTumbuh.T_DAO) < 0L) {
			DataTumbuh.setTunggu(entity.data(), DataTumbuh.T_DAO, DataTumbuh.hari(entity.level().getServer()));
		}
	}

	/** The master's click {@code [Setujui <Dao>]}: the old master path with the disciple's own leaning. */
	static boolean setujui(DiscipleEntity entity, ServerPlayer master) {
		if (!terbuka(entity.data()) || !entity.isOwnedBy(master)) {
			return false;
		}
		Dao dao = pilih(entity);
		boolean done = dao != null && DiscipleCultivation.chooseDao(entity, master, dao);
		if (done) {
			selesai(entity.data());
		}
		return done;
	}

	/**
	 * Once a minute per loaded disciple: notes the open choice (and tells the master once), forgets it once a Dao is set,
	 * and on a waiting night sends the disciple to contemplate under the sky once.
	 */
	static void periksa(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		UUID owner = entity.ownerId();
		if (owner == null) {
			return;
		}
		if (!terbuka(data)) {
			if (data.dao() != null && DataTumbuh.tungguHari(data, DataTumbuh.T_DAO) >= 0L) {
				selesai(data);
			}
			return;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		if (DataTumbuh.tungguHari(data, DataTumbuh.T_DAO) < 0L) {
			if (!MandiriApi.aktif() || MandiriApi.kebijakan(entity).mode() == ModeMandiri.PATUH) {
				return;
			}
			DataTumbuh.setTunggu(data, DataTumbuh.T_DAO, today);
			beritahu(entity);
			return;
		}
		if (!menunggu(entity) || !DataTumbuh.malam(entity.level()) || MandiriApi.kegiatan(entity) == Kegiatan.MERENUNG) {
			return;
		}
		CompoundTag record = DataTumbuh.sub(data, KEY);
		if (record.getLongOr("malam", -1L) == today) {
			return;
		}
		record.putLong("malam", today);
		BlockPos spot = entity.level() instanceof ServerLevel level ? TempatTumbuh.terbuka(level, entity, null) : null;
		MandiriApi.mulaiKegiatan(entity, Kegiatan.MERENUNG, Kegiatan.JAM, spot, null);
	}

	/** The one notice: the disciple's leaning and the buttons. */
	private static void beritahu(DiscipleEntity entity) {
		Dao dao = pilih(entity);
		if (dao == null) {
			return;
		}
		DataTumbuh.sub(entity.data(), KEY).putString("usulan", dao.id());
		Kebijakan policy = MandiriApi.kebijakan(entity);
		String soul = entity.soulId().toString();
		MutableComponent line = KabarTumbuh.baris("dao.tanya", 0xCFE7FF, KabarTumbuh.nama(entity), dao.displayName().copy().withColor(dao.color()),
				policy.tungguDaoHari());
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.dao_setujui",
				"/kultivasi perguruan mandiri dao setujui " + soul, KabarTumbuh.P + "tombol.dao_setujui.hover", dao.color()));
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.dao_biarkan",
				"/kultivasi perguruan mandiri dao biarkan " + soul, KabarTumbuh.P + "tombol.dao_biarkan.hover", 0x8FE3C8));
		KabarTumbuh.kirim(entity, null, line);
		KabarTumbuh.catat(entity, "dao", "dao.bimbang", EntriHarian.Suasana.TENANG, false, "d:" + dao.id());
	}

	private static void selesai(DiscipleData data) {
		DataTumbuh.setTunggu(data, DataTumbuh.T_DAO, -1L);
		CompoundTag record = DataTumbuh.sub(data, KEY);
		record.remove("jawaban");
		record.remove("malam");
		record.remove("usulan");
	}

	// ================================================================== the choice

	/** The Dao the disciple leans to now (argmax of {@link #skor}; a stable tie-break by soul). */
	static @Nullable Dao pilih(DiscipleEntity entity) {
		Dao best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		int salt = Math.floorMod(entity.soulId().hashCode(), Dao.values().length);
		for (Skor skor : skor(entity).values()) {
			double total = skor.total() + (skor.dao().ordinal() == salt ? 0.01D : 0.0D);
			if (total > bestScore) {
				bestScore = total;
				best = skor.dao();
			}
		}
		return best;
	}

	/** Every Dao's score with its parts (39.5.4). */
	static Map<Dao, Skor> skor(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		JalanHidup path = MandiriApi.jalan(data);
		Dao masterDao = null;
		ServerPlayer master = entity.owner();
		if (master != null) {
			masterDao = Cultivators.get(master).dao();
		}
		Dao friendDao = sahabatDao(entity);
		Map<Dao, Skor> out = new EnumMap<>(Dao.class);
		for (Dao dao : Dao.values()) {
			double akar = dao.hasAffinity(data.rootElements()) ? 2.0D : 0.0D;
			double jalan = jalanSkor(path, dao);
			int known = 0;
			for (TechniqueSpec spec : data.learnedSpecs()) {
				if (spec.dao() == dao) {
					known++;
				}
			}
			out.put(dao, new Skor(dao, akar, jalan, 0.5D * known, dao == masterDao ? 0.5D : 0.0D, dao == friendDao ? 0.3D : 0.0D));
		}
		return out;
	}

	/** The path's leaning (39.5.4 table). */
	static double jalanSkor(@Nullable JalanHidup path, Dao dao) {
		if (path == null) {
			return 0.0D;
		}
		return switch (path) {
			case PEDANG -> dao == Dao.SWORD ? 2.0D : 0.0D;
			case ALKEMIS -> dao == Dao.FIRE || dao == Dao.LIFE ? 1.5D : 0.0D;
			case FORMASI -> dao == Dao.SPACE ? 1.5D : 0.0D;
			case PERTAPA -> dao == Dao.LIFE || dao == Dao.SPACE ? 1.0D : 0.0D;
			case PEMBURU -> dao == Dao.BODY ? 2.0D : 0.0D;
			case PETUALANG -> dao == Dao.LIGHTNING || dao == Dao.SHADOW ? 1.0D : 0.0D;
			case PENJAGA -> dao == Dao.BODY || dao == Dao.ICE ? 1.0D : 0.0D;
			default -> 0.0D;
		};
	}

	/** The Dao of the loaded friend it is closest to (a close bond or affinity 40+), or null. */
	private static @Nullable Dao sahabatDao(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		if (owner == null) {
			return null;
		}
		Dao best = null;
		int bestAffinity = Integer.MIN_VALUE;
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other == entity || other.data().dao() == null) {
				continue;
			}
			Ikatan bond = SekteApi.hubungan().kind(owner, entity.soulId(), other.soulId());
			int aff = SekteApi.hubungan().affinity(owner, entity.soulId(), other.soulId());
			boolean close = bond == Ikatan.SAHABAT || bond == Ikatan.SAUDARA_ANGKAT || bond == Ikatan.KEKASIH || bond == Ikatan.PASANGAN_DAO;
			if (!close && aff < HuFaSahabat.AFINITAS_MIN) {
				continue;
			}
			int weight = aff + (close ? 100 : 0);
			if (weight > bestAffinity) {
				bestAffinity = weight;
				best = other.data().dao();
			}
		}
		return best;
	}

	// ================================================================== the ceremony

	/** The night contemplation ended: the disciple takes its Dao. True when chosen. */
	static boolean upacara(DiscipleEntity entity) {
		Dao dao = pilih(entity);
		if (dao == null || !DiscipleCultivation.chooseDaoSelf(entity, dao)) {
			return false;
		}
		DiscipleData data = entity.data();
		selesai(data);
		MinecraftServer server = entity.level().getServer();
		if (entity.level() instanceof ServerLevel level) {
			Fx.ring(level, entity.position().add(0.0D, 0.05D, 0.0D), dao.color(), 4.0F);
			level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.2F);
		}
		Ingatan.remember(server, data, "mandiri.dao_sendiri", null, 6, KabarTumbuh.P + "ingat.dao_sendiri", dao.displayName().getString());
		KabarTumbuh.catat(entity, "dao", "dao.upacara", EntriHarian.Suasana.BANGGA, true, "d:" + dao.id());
		SekteSuara.line(entity, "dao_pilih", "cultivate", 0);
		if (entity.owner() == null) {
			KabarTumbuh.kirim(entity, null, KabarTumbuh.baris("dao.upacara", dao.color(), KabarTumbuh.nama(entity),
					dao.displayName().copy().withColor(dao.color())).withStyle(ChatFormatting.BOLD));
		}
		return true;
	}

	/** {@code uji ... tumbuh dao <murid>}: the waiting skipped, the ceremony now. */
	static boolean paksa(DiscipleEntity entity) {
		if (!terbuka(entity.data())) {
			return false;
		}
		DataTumbuh.sub(entity.data(), KEY).putString("jawaban", "biar");
		DataTumbuh.setTunggu(entity.data(), DataTumbuh.T_DAO, DataTumbuh.hari(entity.level().getServer()) - 30L);
		return upacara(entity);
	}

	private DaoSendiri() {
	}
}
