package dev.kultivator.sekte.mandiri.api;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.MandiriData;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The one door of Murid Mandiri for every other module (contract C39.A1; 39.F.9 adds {@link #addPertemuanProvider}).
 * Static, server side, safe defaults: with the module off or a part not landed yet every read answers "nothing" and every
 * write is refused, so callers never need to know which module landed.
 * <p>
 * <b>Schema (C39.A4).</b> The per-disciple data lives in {@code DiscipleData.extra().mandiri} (keys in {@link MandiriIds});
 * only 39A changes the schema. Other modules read through the getters here and write only through the writers here
 * ({@link #ubahDompet}, {@link #masukTas}, {@link #ambilTas}, {@link #onPerintah}, {@link #catat}), never by touching
 * the compound.
 * <p>
 * <b>Parts installed by their owners</b> (each a single setter, called once from that module's init): 39A
 * {@link #setSumberKebijakan}, 39C {@link #setPergi}, {@link #setPasar} default, {@link #setGilda} default, 39E
 * {@link #setPencatat}, 39C/39D {@link #setKas}, {@link #setRelawan}, {@link #setDagangan}. 38EK may replace the market
 * and the guild (C39.K1, C39.K4); 37/38 add destinations (C39 {@link TujuanProvider}); 39F adds encounters (C39.C3).
 */
public final class MandiriApi {

	// ================================================================== installed parts

	/** Writes a diary entry (39E {@code Cerita.catat}). */
	@FunctionalInterface
	public interface Pencatat {
		void catat(UUID owner, UUID soul, EntriHarian entri);
	}

	/** The hall policy and a disciple's overlay (39A {@code KebijakanAula}). */
	public interface SumberKebijakan {
		Kebijakan aula(UUID owner);

		/** The overlay compound of an unloaded disciple (from the hall record), or null. */
		default @Nullable CompoundTag tumpang(UUID owner, UUID soul) {
			return null;
		}
	}

	/** Records a purse change in the hall's money book ({@code MandiriData.kas}, 39C {@code Dompet}). */
	@FunctionalInterface
	public interface Kas {
		void catat(DiscipleData data, long delta, long saldo, String sebab);
	}

	private static volatile @Nullable Pencatat pencatat;
	private static volatile @Nullable SumberKebijakan sumberKebijakan;
	private static volatile @Nullable Kas kas;
	private static volatile BiPredicate<UUID, UUID> pergi = (owner, soul) -> false;
	private static volatile BiFunction<UUID, String, List<UUID>> relawan = (owner, jenis) -> List.of();
	private static volatile BiFunction<UUID, UUID, List<ItemStack>> dagangan = (owner, soul) -> List.of();
	private static volatile PasarMurid pasar = PasarMurid.NONE;
	private static volatile GildaMurid gilda = GildaMurid.NONE;
	private static final List<TujuanProvider> TUJUAN = new CopyOnWriteArrayList<>();
	private static final List<PertemuanProvider> PERTEMUAN = new CopyOnWriteArrayList<>();
	private static final List<Consumer<DiscipleEntity>> PERINTAH = new CopyOnWriteArrayList<>();
	/** Set while a DESIGN 39 module itself changes a disciple's mode, so the change is not taken as the master's order. */
	private static final ThreadLocal<Boolean> MODE_SENDIRI = ThreadLocal.withInitial(() -> Boolean.FALSE);

	public static void setPencatat(@Nullable Pencatat value) {
		pencatat = value;
	}

	public static void setSumberKebijakan(@Nullable SumberKebijakan value) {
		sumberKebijakan = value;
	}

	public static void setKas(@Nullable Kas value) {
		kas = value;
	}

	/** 39C: whether a soul is away on a journey of DESIGN 39 ({@code AwayStore} reason {@link MandiriIds#AWAY_JALAN}). */
	public static void setPergi(@Nullable BiPredicate<UUID, UUID> value) {
		pergi = value == null ? (owner, soul) -> false : value;
	}

	/** C39.K5: who volunteers for a stall or caravan job (39C/39D). */
	public static void setRelawan(@Nullable BiFunction<UUID, String, List<UUID>> value) {
		relawan = value == null ? (owner, jenis) -> List.of() : value;
	}

	/** C39.K5: the disciple's own work it may sell (39D). */
	public static void setDagangan(@Nullable BiFunction<UUID, UUID, List<ItemStack>> value) {
		dagangan = value == null ? (owner, soul) -> List.of() : value;
	}

	/** C39.K1: the market (39C sets its default, 38EK may replace it). */
	public static void setPasar(@Nullable PasarMurid value) {
		pasar = value == null ? PasarMurid.NONE : value;
	}

	public static PasarMurid pasar() {
		return pasar;
	}

	/** C39.K4: the guild board (39C sets its default, 38EK may replace it). */
	public static void setGilda(@Nullable GildaMurid value) {
		gilda = value == null ? GildaMurid.NONE : value;
	}

	public static GildaMurid gilda() {
		return gilda;
	}

	public static void addTujuanProvider(TujuanProvider provider) {
		if (provider != null) {
			TUJUAN.add(provider);
		}
	}

	public static List<TujuanProvider> tujuanProviders() {
		return List.copyOf(TUJUAN);
	}

	/** C39.C3: an encounter source asked when a journey stage falls due (39F). */
	public static void addPertemuanProvider(PertemuanProvider provider) {
		if (provider != null) {
			PERTEMUAN.add(provider);
		}
	}

	public static List<PertemuanProvider> pertemuanProviders() {
		return List.copyOf(PERTEMUAN);
	}

	/** Called after {@link #onPerintah} paused a disciple (39A's planner cancels its running activity there). */
	public static void addPerintahListener(Consumer<DiscipleEntity> listener) {
		if (listener != null) {
			PERINTAH.add(listener);
		}
	}

	/** C39.A5: the executor of {@code kegiatan} (see {@link MandiriKegiatan#register}). */
	public static void register(Kegiatan kegiatan, Pelaksana pelaksana) {
		MandiriKegiatan.register(kegiatan, pelaksana);
	}

	// ================================================================== module state

	/** True when the phase, the module switch {@code umum.mandiri} and {@code mandiri.aktif} are on. */
	public static boolean aktif() {
		return SekteContent.on(MandiriIds.MODUL) && MandiriConfig.get().aktif;
	}

	// ================================================================== per-disciple reads

	/** The disciple's {@code extra.mandiri} for reading (an empty detached tag when it has none; never write into it). */
	public static CompoundTag bagian(DiscipleData data) {
		return data == null ? new CompoundTag() : data.extra().getCompoundOrEmpty(MandiriIds.EXTRA);
	}

	/** The live {@code extra.mandiri} of 39A's own code (created when missing). Other modules use the writers below. */
	public static CompoundTag bagianTulis(DiscipleData data) {
		CompoundTag section = data.extraSection(MandiriIds.EXTRA);
		if (!section.contains(MandiriIds.K_VERSI)) {
			section.putInt(MandiriIds.K_VERSI, MandiriIds.VERSI);
		}
		return section;
	}

	/** The life path, or null before the first choice. */
	public static @Nullable JalanHidup jalan(DiscipleData data) {
		return JalanHidup.byId(bagian(data).getStringOr(MandiriIds.K_JALAN, ""));
	}

	/** The running planner activity of a loaded disciple, or null. */
	public static @Nullable Kegiatan kegiatan(DiscipleEntity entity) {
		if (entity == null) {
			return null;
		}
		Kegiatan kegiatan = Kegiatan.byId(bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_KEGIATAN).getStringOr("id", ""));
		return kegiatan == Kegiatan.KOSONG ? null : kegiatan;
	}

	/** Temptation 0-100. */
	public static int iblis(DiscipleData data) {
		return Math.max(0, Math.min(100, bagian(data).getIntOr(MandiriIds.K_IBLIS, 0)));
	}

	/** The current wound kind ({@link JenisLuka#TIDAK} once healed: {@code sampai} passed). */
	public static JenisLuka luka(DiscipleData data, long now) {
		CompoundTag luka = bagian(data).getCompoundOrEmpty(MandiriIds.K_LUKA);
		JenisLuka jenis = JenisLuka.byId(luka.getStringOr("jenis", ""));
		long sampai = luka.getLongOr("sampai", 0L);
		return jenis != JenisLuka.TIDAK && (sampai <= 0L || now < sampai) ? jenis : JenisLuka.TIDAK;
	}

	/**
	 * Writes a wound ({@code extra.mandiri.luka}) until game time {@code sampai}; {@code sebab} is a short id (jalan,
	 * duel, seteru...). A lighter wound never replaces a heavier one that is still open (only its end may grow).
	 * {@link JenisLuka#TIDAK} heals. Never a death path (39.0, {@code bolehGugur} false).
	 */
	public static void lukai(DiscipleData data, JenisLuka jenis, long now, long sampai, String sebab) {
		if (data == null || jenis == null) {
			return;
		}
		if (jenis == JenisLuka.TIDAK) {
			bagianTulis(data).remove(MandiriIds.K_LUKA);
			return;
		}
		JenisLuka open = luka(data, now);
		CompoundTag wound = bagianTulis(data).getCompoundOrEmpty(MandiriIds.K_LUKA).copy();
		if (open.ordinal() > jenis.ordinal()) {
			wound.putLong("sampai", Math.max(wound.getLongOr("sampai", 0L), sampai));
		} else {
			wound.putString("jenis", jenis.id());
			wound.putLong("sampai", open == jenis ? Math.max(wound.getLongOr("sampai", 0L), sampai) : sampai);
			wound.putString("sebab", sebab == null ? "" : sebab);
		}
		bagianTulis(data).put(MandiriIds.K_LUKA, wound);
	}

	/** True while the disciple is in seclusion. */
	public static boolean tutupDiri(DiscipleData data, long now) {
		return now < bagian(data).getCompoundOrEmpty(MandiriIds.K_TUTUP).getLongOr("sampai", 0L);
	}

	/** Game time until which the master's last order holds the disciple (0 none, {@link Long#MAX_VALUE} forever). */
	public static long bebasSampai(DiscipleData data) {
		return bagian(data).getLongOr(MandiriIds.K_BEBAS, 0L);
	}

	/** True while a soul is away on a journey of DESIGN 39. */
	public static boolean sedangPergi(UUID owner, UUID soul) {
		try {
			return owner != null && soul != null && pergi.test(owner, soul);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	// ================================================================== policy

	/** The hall policy (the new-world default while 39A's store has not landed). */
	public static Kebijakan kebijakanAula(UUID owner) {
		SumberKebijakan source = sumberKebijakan;
		if (source != null && owner != null) {
			try {
				Kebijakan value = source.aula(owner);
				if (value != null) {
					return value;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri policy source failed", exception);
			}
		}
		MandiriConfig config = MandiriConfig.get();
		return Kebijakan.bawaan(config.modeAwal(), config.tungguGuruHari, config.tungguDaoHari);
	}

	/** The effective policy of a soul (hall policy plus its overlay); {@code soul} null gives the hall policy. */
	public static Kebijakan kebijakan(UUID owner, @Nullable UUID soul) {
		Kebijakan hall = kebijakanAula(owner);
		SumberKebijakan source = sumberKebijakan;
		if (soul == null || source == null) {
			return hall;
		}
		try {
			CompoundTag over = source.tumpang(owner, soul);
			return over == null ? hall : Kebijakan.fromTag(over, hall);
		} catch (RuntimeException exception) {
			return hall;
		}
	}

	/** The effective policy of a loaded disciple (its own overlay read from its data). */
	public static Kebijakan kebijakan(DiscipleEntity entity) {
		UUID owner = entity == null ? null : entity.ownerId();
		if (owner == null) {
			MandiriConfig config = MandiriConfig.get();
			return Kebijakan.bawaan(ModeMandiri.PATUH, config.tungguGuruHari, config.tungguDaoHari);
		}
		return Kebijakan.fromTag(bagian(entity.data()).getCompoundOrEmpty(MandiriIds.K_KEBIJAKAN), kebijakanAula(owner));
	}

	// ================================================================== the master's orders win (39.7.2)

	/**
	 * The master gave {@code entity} an explicit order (a new mode, a mission, a post): the planner leaves it alone for
	 * {@code jedaPerintah} ticks ("Selamanya" stays forever) and the listeners cancel what it was doing. Wired to
	 * {@code DiscipleEntity.addModeListener} by {@code Mandiri.init}; old modules that call {@code setActivity} with an id
	 * outside 40-60 win anyway because the planner only judges disciples that are idle or in its own activities.
	 */
	public static void onPerintah(DiscipleEntity entity) {
		if (entity == null || MODE_SENDIRI.get() || !aktif() || entity.role() != DiscipleEntity.Role.DISCIPLE
				|| entity.level().isClientSide()) {
			return;
		}
		CompoundTag section = bagianTulis(entity.data());
		long now = entity.level().getGameTime();
		if (section.getLongOr(MandiriIds.K_BEBAS, 0L) != Long.MAX_VALUE) {
			section.putLong(MandiriIds.K_BEBAS, now + MandiriConfig.get().jedaPerintah);
		}
		for (Consumer<DiscipleEntity> listener : PERINTAH) {
			try {
				listener.accept(entity);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Mandiri order listener failed", exception);
			}
		}
	}

	/** Changes the mode for DESIGN 39 itself (gate guard, "Bebaskan"): not counted as the master's order. */
	public static void setModeSendiri(DiscipleEntity entity, DiscipleEntity.Mode mode) {
		boolean before = MODE_SENDIRI.get();
		MODE_SENDIRI.set(Boolean.TRUE);
		try {
			entity.setMode(mode);
		} finally {
			MODE_SENDIRI.set(before);
		}
	}

	/** Sets the order pause: 0 = "Bebaskan ke Jalan Hidup" (free now), {@link Long#MAX_VALUE} = "Selamanya", else a game time. */
	public static void setBebas(DiscipleData data, long sampai) {
		bagianTulis(data).putLong(MandiriIds.K_BEBAS, Math.max(0L, sampai));
	}

	// ================================================================== diary

	/** C39.E1: writes a diary entry (nothing happens until 39E installs its writer). */
	public static void catat(UUID owner, UUID soul, EntriHarian entri) {
		Pencatat writer = pencatat;
		if (writer == null || owner == null || soul == null || entri == null) {
			return;
		}
		try {
			writer.catat(owner, soul, entri);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri diary writer failed", exception);
		}
	}

	// ================================================================== purse (39.6.4, 39.6.11)

	/** NB in the purse. */
	public static long dompet(DiscipleData data) {
		return Math.max(0L, bagian(data).getLongOr(MandiriIds.K_DOMPET, 0L));
	}

	/** Purse limit: {@code dompetMaksPerRanah x (realm + 1)} NB. */
	public static long dompetMaks(DiscipleData data) {
		return (long) MandiriConfig.get().dompetMaksPerRanah * (data.realm().ordinal() + 1L);
	}

	/**
	 * The only purse writer (39.6.11 rule 1): adds {@code delta} NB, clamped to {@code [0, dompetMaks]}, records it in the
	 * money book with {@code sebab} and returns the change actually applied (a caller routes the remainder, e.g. an
	 * overflow to the hall pool). Refused (0) while the module is off.
	 */
	public static long ubahDompet(DiscipleData data, long delta, String sebab) {
		if (data == null || delta == 0L || !aktif()) {
			return 0L;
		}
		long before = dompet(data);
		long after = Math.max(0L, Math.min(dompetMaks(data), before + delta));
		long applied = after - before;
		if (applied == 0L) {
			return 0L;
		}
		bagianTulis(data).putLong(MandiriIds.K_DOMPET, after);
		Kas book = kas;
		if (book != null) {
			try {
				book.catat(data, applied, after, sebab == null ? "" : sebab);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri money book failed", exception);
			}
		}
		return applied;
	}

	// ================================================================== bag (39.6.7)

	/** Copies of the bag's stacks (at most {@link MandiriIds#TAS_SLOT}); created on demand, never cached. */
	public static List<ItemStack> tas(DiscipleData data, HolderLookup.Provider registries) {
		ListTag list = bagian(data).getListOrEmpty(MandiriIds.K_TAS);
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		List<ItemStack> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size() && out.size() < MandiriIds.TAS_SLOT; i++) {
			ItemStack stack = list.getCompoundOrEmpty(i).read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		return out;
	}

	/**
	 * Puts {@code stack} into the bag (merging with equal stacks first); false, and nothing changed, when it does not fit
	 * completely. The caller keeps ownership of {@code stack} (a copy is stored).
	 */
	public static boolean masukTas(DiscipleData data, HolderLookup.Provider registries, ItemStack stack) {
		if (data == null || stack == null || stack.isEmpty() || !aktif()) {
			return false;
		}
		List<ItemStack> bag = tas(data, registries);
		ItemStack rest = stack.copy();
		for (ItemStack each : bag) {
			if (rest.isEmpty()) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(each, rest)) {
				int room = each.getMaxStackSize() - each.getCount();
				int moved = Math.min(room, rest.getCount());
				if (moved > 0) {
					each.grow(moved);
					rest.shrink(moved);
				}
			}
		}
		while (!rest.isEmpty() && bag.size() < MandiriIds.TAS_SLOT) {
			bag.add(rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
		}
		if (!rest.isEmpty()) {
			return false;
		}
		simpanTas(data, registries, bag);
		return true;
	}

	/** Takes up to {@code n} items matching {@code filter} out of the bag and returns them (empty when none). */
	public static ItemStack ambilTas(DiscipleData data, HolderLookup.Provider registries, Predicate<ItemStack> filter, int n) {
		if (data == null || filter == null || n <= 0 || !aktif()) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> bag = tas(data, registries);
		ItemStack taken = ItemStack.EMPTY;
		for (ItemStack each : bag) {
			if (each.isEmpty() || !filter.test(each)) {
				continue;
			}
			if (!taken.isEmpty() && !ItemStack.isSameItemSameComponents(taken, each)) {
				continue;
			}
			int want = n - taken.getCount();
			if (want <= 0) {
				break;
			}
			ItemStack part = each.split(Math.min(want, each.getCount()));
			if (taken.isEmpty()) {
				taken = part;
			} else {
				taken.grow(part.getCount());
			}
		}
		if (!taken.isEmpty()) {
			bag.removeIf(ItemStack::isEmpty);
			simpanTas(data, registries, bag);
		}
		return taken;
	}

	private static void simpanTas(DiscipleData data, HolderLookup.Provider registries, List<ItemStack> bag) {
		RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
		ListTag list = new ListTag();
		for (ItemStack each : bag) {
			if (!each.isEmpty() && list.size() < MandiriIds.TAS_SLOT) {
				CompoundTag one = new CompoundTag();
				one.store("item", ItemStack.CODEC, ops, each);
				list.add(one);
			}
		}
		bagianTulis(data).put(MandiriIds.K_TAS, list);
	}

	// ================================================================== statistics (extra.mandiri.statistik)

	/** A statistic of {@code extra.mandiri.statistik} (keys {@code MandiriIds.S_*}), 0 when missing. */
	public static long statistik(DiscipleData data, String key) {
		return bagian(data).getCompoundOrEmpty(MandiriIds.K_STATISTIK).getLongOr(key, 0L);
	}

	/** Adds {@code delta} to a statistic (counters: journeys, NB earned, hours...). */
	public static void tambahStatistik(DiscipleData data, String key, long delta) {
		if (data == null || key == null || delta == 0L) {
			return;
		}
		CompoundTag stats = child(bagianTulis(data), MandiriIds.K_STATISTIK);
		stats.putLong(key, Math.max(0L, stats.getLongOr(key, 0L) + delta));
	}

	/** Raises a statistic to {@code value} when higher (records: beast tier, realm tier, craft grade). */
	public static void maksStatistik(DiscipleData data, String key, long value) {
		if (data == null || key == null) {
			return;
		}
		CompoundTag stats = child(bagianTulis(data), MandiriIds.K_STATISTIK);
		if (value > stats.getLongOr(key, 0L)) {
			stats.putLong(key, value);
		}
	}

	// ================================================================== XP of activities that are not seated meditation

	/**
	 * Gives the "breathing while working" XP of an activity block (39.5.1): {@code amount} is the raw XP before the disciple
	 * multipliers, already {@code 50 x meditationBaseTanpaMode x K x skalaXp x hours}. 39B installs
	 * {@code DiscipleCultivation.addXpKegiatan} (multipliers without the formation/site factor, distribute, stage-up FX).
	 */
	@FunctionalInterface
	public interface Laju {
		void beri(DiscipleEntity entity, double amount);
	}

	private static volatile @Nullable Laju laju;

	/** 39B: installs the XP path of 39.5.1 ({@code DiscipleCultivation.addXpKegiatan}). */
	public static void setLaju(@Nullable Laju value) {
		laju = value;
	}

	/**
	 * Applies activity XP: 39B's path when installed, else the same rule with old public pieces (root, rank and hook
	 * multipliers with no body, so no formation or site factor; {@code distribute}; the synced values refreshed).
	 */
	public static void beriXpKegiatan(DiscipleEntity entity, double amount) {
		if (entity == null || !(amount > 0.0D) || !Double.isFinite(amount) || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		Laju path = laju;
		if (path != null) {
			try {
				path.beri(entity, amount);
				return;
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri activity XP path failed, using the built-in rule", exception);
			}
		}
		DiscipleData data = entity.data();
		double gain = amount * dev.kultivator.disciple.DiscipleCultivation.xpMultiplier(null, data, dev.kultivator.data.XpSource.MEDITATION);
		if (!(gain > 0.0D) || !Double.isFinite(gain)) {
			return;
		}
		data.addTotalXp(gain);
		if (dev.kultivator.disciple.DiscipleCultivation.distribute(data, gain) > 0) {
			entity.refreshSynced();
		}
	}

	// ================================================================== notices to the master (C39.E2)

	/** 39E's {@code Pemberitahuan.kirim}: chat with [Lihat], the toast, Sibuk delay, the offline summary. */
	@FunctionalInterface
	public interface Pemberita {
		void kirim(MinecraftServer server, UUID owner, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting);
	}

	private static volatile @Nullable Pemberita pemberita;

	public static void setPemberita(@Nullable Pemberita value) {
		pemberita = value;
	}

	/**
	 * Tells the master about {@code jenis} when the effective policy wants it: 39E's notices when installed, else a plain
	 * chat line while the master is online and not busy ({@code Sibuk}); always a line in the hall news and the elder's
	 * morning report ({@code SekteLog}, module {@code mandiri}).
	 */
	public static void beritahu(MinecraftServer server, UUID owner, @Nullable UUID soul, Kebijakan.Pemberitahuan jenis,
			Component pesan, boolean penting) {
		if (server == null || owner == null || pesan == null || !aktif()) {
			return;
		}
		dev.kultivator.sekte.SekteLog.log(server, owner, MandiriIds.MODUL, pesan);
		if (!kebijakan(owner, soul).diberitahu(jenis)) {
			return;
		}
		Pemberita installed = pemberita;
		if (installed != null) {
			try {
				installed.kirim(server, owner, jenis, pesan, penting);
				return;
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Mandiri notice writer failed", exception);
			}
		}
		net.minecraft.server.level.ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null && dev.kultivator.util.Sibuk.why(master) == null) {
			dev.kultivator.util.Msg.chat(master, pesan.copy().withStyle(penting ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
	}

	// ================================================================== the hall record (MandiriData, DESIGN 39.2.2)

	/**
	 * The live sub-compound {@code key} ({@code MandiriIds.A_*}) of the hall record of {@code owner}, created when missing.
	 * Only the module named at the key writes it; call {@link #tandai} after writing.
	 */
	public static CompoundTag aula(MinecraftServer server, UUID owner, String key) {
		return MandiriData.get(server).bagian(owner, key);
	}

	/** Marks the hall records dirty (saved with the world). */
	public static void tandai(MinecraftServer server) {
		MandiriData.get(server).setDirty();
	}

	/** Appends a diary entry to the soul's ring (40 kept, ordinary entries dropped first); 39E's writer calls it. */
	public static void simpanHarian(MinecraftServer server, UUID owner, UUID soul, EntriHarian entri) {
		if (server != null && owner != null && soul != null && entri != null) {
			MandiriData.get(server).tambahHarian(owner, soul, entri);
		}
	}

	/** The soul's diary, oldest first (empty when none). */
	public static List<EntriHarian> harian(MinecraftServer server, UUID owner, UUID soul) {
		return server == null || owner == null || soul == null ? List.of() : MandiriData.get(server).harian(owner, soul);
	}

	/**
	 * Adds a news line to the hall feed (30 kept): a lang key and its plain arguments. An argument that is itself a lang key
	 * (starts with {@code kultivator.}, {@code item.}, {@code block.} or {@code entity.}) is translated by {@link #teks}, so
	 * names of paths, goals, modes and items follow the reader's language. Readers build the line with {@link #teksKabar}.
	 */
	public static void kabar(MinecraftServer server, UUID owner, @Nullable UUID soul, String kunci, boolean penting, String... arg) {
		if (server != null && owner != null && kunci != null) {
			MandiriData.get(server).tambahKabar(server, owner, soul, kunci, penting, arg);
		}
	}

	/** The text of one stored line ({@code kunci} + {@code arg} list of strings), lang-key arguments translated. */
	public static Component teks(String kunci, List<String> arg) {
		Object[] parts = new Object[arg == null ? 0 : arg.size()];
		for (int i = 0; i < parts.length; i++) {
			String value = arg.get(i) == null ? "" : arg.get(i);
			parts[i] = adalahKunci(value) ? Component.translatable(value) : value;
		}
		return Component.translatable(kunci == null ? "" : kunci, parts);
	}

	/** {@link #teks} for one tag of the news feed ({@code MandiriData.kabar}: {@code kunci}, {@code arg}). */
	public static Component teksKabar(CompoundTag line) {
		ListTag tags = line.getListOrEmpty("arg");
		List<String> arg = new ArrayList<>(tags.size());
		for (int i = 0; i < tags.size(); i++) {
			arg.add(tags.getStringOr(i, ""));
		}
		return teks(line.getStringOr("kunci", ""), arg);
	}

	private static boolean adalahKunci(String value) {
		return value.indexOf(' ') < 0 && value.indexOf('.') > 0 && (value.startsWith("kultivator.") || value.startsWith("item.")
				|| value.startsWith("block.") || value.startsWith("entity."));
	}

	// ================================================================== the master's hand (tab and board, 39.7.3)

	/** [Tegur]: temptation -6, once a day; false when already scolded or praised today. */
	public static boolean tegur(DiscipleEntity entity, net.minecraft.server.level.ServerPlayer master) {
		return dev.kultivator.sekte.mandiri.JalanIblis.tegur(entity, master);
	}

	/** [Puji]: honoured +2, once a day; false when already scolded or praised today. */
	public static boolean puji(DiscipleEntity entity, net.minecraft.server.level.ServerPlayer master) {
		return dev.kultivator.sekte.mandiri.JalanIblis.puji(entity, master);
	}

	/** [Tebus]: starts the 60 s redemption ritual (temptation 60+, master within 6 blocks, a Heart Calming Pill). */
	public static @Nullable Component tebus(DiscipleEntity entity, net.minecraft.server.level.ServerPlayer master) {
		return dev.kultivator.sekte.mandiri.JalanIblis.mulaiTebus(entity, master);
	}

	/** [Dorong ke jalan ...]: the master pushes a path (+0.8 in the next path check, 39.3.2). */
	public static void dorongJalan(DiscipleEntity entity, JalanHidup jalan) {
		dev.kultivator.sekte.mandiri.PilihJalan.dorong(entity, jalan);
	}

	/** [Bebaskan ke Jalan Hidup]: ends the order pause now and leaves the disciple on STAY at its spot (39.7.2). */
	public static void bebaskan(DiscipleEntity entity) {
		dev.kultivator.sekte.mandiri.KebijakanAula.bebaskan(entity);
	}

	// ================================================================== goals, paths, activities for the other modules

	/** C39 events: adds (or lifts) a goal; false when it makes no sense now (no Dao, no enemy, the module off). */
	public static boolean tambahCita(DiscipleEntity entity, JenisCita jenis, @Nullable String target, int prio, String asal) {
		return entity != null && jenis != null && aktif() && dev.kultivator.sekte.mandiri.CitaCita.tambah(entity, jenis, target, prio, asal);
	}

	/** Ends an active goal as reached (a rival beaten on the road, a feud foe defeated); false when none is active. */
	public static boolean selesaikanCita(DiscipleEntity entity, JenisCita jenis, @Nullable String target) {
		return entity != null && jenis != null && aktif() && dev.kultivator.sekte.mandiri.CitaCita.selesaikan(entity, jenis, target);
	}

	/** The disciple lost to {@code target} (a soul, a character id, a feud id): a DENDAM goal now or at its next load. */
	public static void dendam(MinecraftServer server, UUID owner, UUID soul, String target) {
		if (aktif()) {
			dev.kultivator.sekte.mandiri.CitaCita.dendam(server, owner, soul, target);
		}
	}

	/** An event bonus to a path's next checks (39.3.5: rescued by the master +0.5 penjaga, a lost final +0.5 pedang). */
	public static void bonusJalan(DiscipleData data, JalanHidup jalan, double delta) {
		if (data != null && jalan != null && aktif()) {
			dev.kultivator.sekte.mandiri.PilihJalan.bonus(data, jalan, delta);
		}
	}

	/** 39C: the disciple bought in a black market today (temptation +4 at the next daily step). */
	public static void pasarGelap(DiscipleData data, long hari) {
		if (data != null && aktif()) {
			bagianTulis(data).putLong(dev.kultivator.sekte.mandiri.JalanIblis.K_PASAR_GELAP, hari);
		}
	}

	/** Temptation changes from events (39B's heart trial +15, a Heart Calming Pill -10): thresholds and all. */
	public static void ubahIblis(DiscipleEntity entity, int delta, String sebab) {
		if (entity != null && aktif()) {
			dev.kultivator.sekte.mandiri.JalanIblis.ubah(entity, delta, sebab);
		}
	}

	/**
	 * Starts {@code kegiatan} on a loaded disciple now, outside the hourly choice (39B's breakthrough and its Hu Fa
	 * friends, 39C's packing and departure, 39F's scenes): the running activity ends first (interrupted), then the
	 * registered executor starts with this place and companion for {@code lamaTick} ticks (-1 = until the executor ends
	 * it). False when the planner may not take this disciple (an order, a foreign activity, the module off).
	 */
	public static boolean mulaiKegiatan(DiscipleEntity entity, Kegiatan kegiatan, int lamaTick, @Nullable net.minecraft.core.BlockPos tempat,
			@Nullable UUID teman) {
		return entity != null && kegiatan != null && aktif()
				&& dev.kultivator.sekte.mandiri.Perencana.paksa(entity, kegiatan, lamaTick, tempat, teman);
	}

	/** Ends the running planner activity of a loaded disciple ({@code batal}: interrupted). */
	public static void akhiriKegiatan(DiscipleEntity entity, boolean batal) {
		if (entity != null) {
			dev.kultivator.sekte.mandiri.Perencana.batal(entity, batal);
		}
	}

	/**
	 * One small off-screen event of the catch-up (39.4.7 step 4) offered by a module: learn a technique from the rack
	 * (39B), brew pills from allowed Lumbung herbs (39D), a harvest... Called with the past day it happened on; returns
	 * true when it did something (and wrote its own diary entry, dated {@code hari}).
	 */
	@FunctionalInterface
	public interface PeristiwaKejar {
		boolean coba(DiscipleEntity entity, long hari, net.minecraft.util.RandomSource random);
	}

	private static final List<PeristiwaKejar> KEJAR = new CopyOnWriteArrayList<>();

	public static void addPeristiwaKejar(PeristiwaKejar provider) {
		if (provider != null) {
			KEJAR.add(provider);
		}
	}

	public static List<PeristiwaKejar> peristiwaKejar() {
		return List.copyOf(KEJAR);
	}

	private static CompoundTag child(CompoundTag parent, String key) {
		CompoundTag tag = parent.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(key, tag);
		}
		return tag;
	}

	// ================================================================== C39.K5 (38EK asks)

	/** Disciples of {@code owner} willing to do {@code jenis} ({@code lapak}, {@code kafilah}), most eager first. */
	public static List<UUID> relawan(UUID owner, String jenis) {
		try {
			List<UUID> list = relawan.apply(owner, jenis);
			return list == null ? List.of() : List.copyOf(list);
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	/** The disciple's own work ({@code kultivator:buatan_murid}) it agrees to sell; new stacks every call. */
	public static List<ItemStack> dagangan(UUID owner, UUID soul) {
		try {
			List<ItemStack> list = dagangan.apply(owner, soul);
			return list == null ? List.of() : List.copyOf(list);
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	private MandiriApi() {
	}
}
