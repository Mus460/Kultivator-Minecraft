package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.paviliun.PaviliunMandiri;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Module 39B, Tumbuh Sendiri (DESIGN 39.5): the entry of package {@code sekte.mandiri.tumbuh}, called by
 * {@code sekte.mandiri.Mandiri}. It wires:
 * <ul>
 *   <li>C39.B1 the XP hook {@link LajuMandiri} and the activity XP path {@code MandiriApi.setLaju(addXpKegiatan)};</li>
 *   <li>C39.B2 the friends' Hu Fa factors ({@link HuFaSahabat});</li>
 *   <li>C39.B3 the executors TUTUP_DIRI ({@link TutupDiri}), MERENUNG ({@link Merenung}), SERAP_BATU ({@link SerapBatu}),
 *   JAGA_TEROBOSAN ({@link HuFaSahabat}), BELAJAR ({@link BelajarSendiri}), MENGAJAR ({@link AjarSenior}), TEROBOSAN
 *   ({@link TerobosanSendiri} with {@link Persiapan}, {@link UjianHati});</li>
 *   <li>C39.B4 {@link #butuh}: the pills a disciple needs for its coming steps, read by 39C's shopping list;</li>
 *   <li>the own Dao ({@link DaoSendiri}), the Dewa offer ({@link DewaSendiri}), rank requests and ambitions
 *   ({@link JenjangSendiri}), the pill rules ({@link PilSendiri}), enlightenment ({@link Pencerahan}), the projection
 *   ({@link Proyeksi}), the commands ({@link TumbuhCommands}), and one off-screen catch-up event (a rack study).</li>
 * </ul>
 * All per-disciple work runs on the disciple's own tick20 ({@code DiscipleHooks.addTick20}), the heavier checks once a
 * minute spread by soul; nothing scans the world. Advancements {@code sekte/mandiri_tembus}, {@code sekte/mandiri_abadi}
 * are data files. Blocks never change here; nobody dies through 39B.
 * <p>
 * {@link #registerContent} runs always (no registry entries of 39B yet); {@link #init} runs only while {@code umum.mandiri}
 * and {@code mandiri.aktif} are on. Both are safe to call twice.
 */
public final class Tumbuh {

	/** One thing the disciple needs to buy (C39.B4): an item id, how many, priority 1 (most urgent) to 3, and why. */
	public record Kebutuhan(String item, int jumlah, int prio, String sebab) {
	}

	private static boolean content;
	private static boolean initialised;
	private static @Nullable MinecraftServer server;
	private static int tick;

	/** Registry content of 39B (always, both physical sides). */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
	}

	/** Runtime behaviour of 39B (server listeners, ticks, executors, commands); only while the module is on. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		MandiriApi.setLaju(DiscipleCultivation::addXpKegiatan);
		LajuMandiri.init();
		HuFaSahabat.init();
		TerobosanSendiri.init();
		TutupDiri.init();
		Merenung.init();
		SerapBatu.init();
		BelajarSendiri.init();
		AjarSenior.init();
		PilSendiri.init();
		TumbuhCommands.init();
		MandiriApi.addPeristiwaKejar(Tumbuh::kejar);
		DiscipleHooks.addTick20(Tumbuh::tick20);
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> kosongkan());
		ServerTickEvents.END_SERVER_TICK.register(Tumbuh::tickServer);
	}

	private static void kosongkan() {
		TerobosanSendiri.clear();
		HuFaSahabat.clear();
		BelajarSendiri.clear();
		AjarSenior.clear();
		Merenung.clear();
		KabarTumbuh.clear();
		server = null;
		tick = 0;
	}

	private static void tickServer(MinecraftServer srv) {
		KabarTumbuh.tick(srv);
		if (++tick % 1200 == 0) {
			TerobosanSendiri.bersihkan(srv);
		}
	}

	// ================================================================== per disciple, once a second

	private static void tick20(DiscipleEntity entity) {
		if (entity.level().isClientSide() || entity.role() != DiscipleEntity.Role.DISCIPLE || entity.ownerId() == null
				|| !MandiriApi.aktif()) {
			return;
		}
		try {
			langkah(entity);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Tumbuh Sendiri step of {} failed", entity.soulId(), exception);
		}
	}

	private static void langkah(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		long now = entity.level().getGameTime();
		TerobosanSendiri.tick20(entity);
		HuFaSahabat.tick20(entity);
		AjarSenior.tick20(entity);
		BelajarSendiri.tick20(entity);
		int act = entity.activity();
		if (act == MandiriKegiatan.ACT_TUTUP_DIRI || MandiriApi.tutupDiri(data, now)) {
			TutupDiri.tick20(entity);
		}
		if (act == MandiriKegiatan.ACT_MERENUNG) {
			Merenung.tick20(entity);
		}
		if (act == MandiriKegiatan.ACT_SERAP_BATU) {
			SerapBatu.tick20(entity);
		}
		long phase = Math.floorMod(entity.soulId().hashCode(), 1200);
		long slot = Math.floorMod(now - phase, 1200L);
		if (slot < 20L) {
			KabarTumbuh.advancementTertunda(entity);
			TerobosanSendiri.catatBarPenuh(entity);
			DaoSendiri.periksa(entity);
			DewaSendiri.periksa(entity);
			JenjangSendiri.periksa(entity);
		}
		if (Math.floorMod(now - phase, 600L) < 20L) {
			PilSendiri.aturan(entity);
		}
	}

	// ================================================================== catch-up (39.4.7 step 4)

	/** One past day off-screen: a scholar or a diligent disciple may have finished an art at the rack. */
	private static boolean kejar(DiscipleEntity entity, long hari, net.minecraft.util.RandomSource random) {
		DiscipleData data = entity.data();
		if (data.breakingThrough() || MandiriApi.luka(data, entity.level().getGameTime()) == JenisLuka.TERTAWAN) {
			return false;
		}
		JalanHidup path = MandiriApi.jalan(data);
		double hours = path == JalanHidup.CENDEKIA ? 2.5D : path == JalanHidup.PERTAPA ? 1.0D : 1.5D;
		TechniqueSpec spec = PaviliunMandiri.belajarKejar(entity, hours, random);
		if (spec == null) {
			return false;
		}
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_TEKNIK, 1);
		catatKejar(entity, hari, spec);
		return true;
	}

	/** The diary entry of the catch-up study, dated on the past day it happened (10:00, the pavilion's hour). */
	private static void catatKejar(DiscipleEntity entity, long hari, TechniqueSpec spec) {
		if (entity.ownerId() != null) {
			MandiriApi.catat(entity.ownerId(), entity.soulId(), new EntriHarian(hari, 10, "belajar", "belajar.kejar",
					List.of("t:" + spec.id()), EntriHarian.Suasana.SENANG, false));
		}
	}

	// ================================================================== C39.B4: what to buy

	/** {@link #butuh(DiscipleData, HolderLookup.Provider)} of a loaded disciple. */
	public static List<Kebutuhan> butuh(DiscipleEntity entity) {
		return butuh(entity.data(), entity.level().registryAccess());
	}

	/** C39.B4 for data whose body may be unloaded (the bag read with the running server's registries). */
	public static List<Kebutuhan> butuh(DiscipleData data) {
		MinecraftServer srv = server;
		return srv == null ? List.of() : butuh(data, srv.registryAccess());
	}

	/**
	 * What the disciple needs and its bag lacks (39.6.3 reads it): the pills of the coming breakthrough once the bar is at
	 * 70 % of a peak (priority 1 for the Foundation and Golden Core Pills it cannot do without, 2 for the warding and heart
	 * pills), healing for a heavy wound, heart calming at temptation 50+, the Marrow Cleansing Pill for a Low root or the
	 * Red cap, a Qi Gathering Pill for a coming retreat. Never more than one of a kind.
	 */
	public static List<Kebutuhan> butuh(DiscipleData data, HolderLookup.Provider registries) {
		List<Kebutuhan> out = new ArrayList<>(4);
		if (data == null || registries == null || !MandiriApi.aktif()) {
			return out;
		}
		List<ItemStack> bag = MandiriApi.tas(data, registries);
		boolean peak = !data.rank().isMax() && data.rank().isBottleneck() && data.barFraction() >= 0.7D;
		if (peak) {
			boolean gate = UjianHati.gerbang(data.rank());
			for (PillKind kind : Persiapan.pilUntuk(data.rank(), gate)) {
				if (!ada(bag, kind)) {
					tambah(out, kind, Persiapan.pilWajib(kind) ? 1 : 2, "terobosan");
				}
			}
		}
		long now = server == null ? 0L : server.overworld().getGameTime();
		if (MandiriApi.luka(data, now) == JenisLuka.BERAT && !ada(bag, PillKind.HEALING)) {
			tambah(out, PillKind.HEALING, 2, "luka");
		}
		if (MandiriApi.iblis(data) > PilSendiri.IBLIS_MINUM && !ada(bag, PillKind.HEART_CALMING)) {
			tambah(out, PillKind.HEART_CALMING, 2, "iblis");
		}
		if (data.rootGrade().ordinal() <= RootGrade.LOW.ordinal() && !adaSumsum(bag)) {
			boolean capped = data.rank().isBottleneck() && data.barFull();
			out.add(new Kebutuhan(Kultivator.MOD_ID + ":marrow_cleansing_pill", 1, capped ? 1 : 3, "akar"));
		}
		if (!data.realm().isMortal() && data.realm() != Realm.TRUE_IMMORTAL && data.barFraction() >= 0.5D && !ada(bag, PillKind.QI_GATHERING)) {
			tambah(out, PillKind.QI_GATHERING, 3, "tutup_diri");
		}
		out.sort((a, b) -> Integer.compare(a.prio(), b.prio()));
		return out;
	}

	private static void tambah(List<Kebutuhan> out, PillKind kind, int prio, String sebab) {
		String id = Kultivator.MOD_ID + ":" + kind.id();
		for (Kebutuhan each : out) {
			if (each.item().equals(id)) {
				return;
			}
		}
		out.add(new Kebutuhan(id, 1, prio, sebab));
	}

	private static boolean ada(List<ItemStack> bag, PillKind kind) {
		for (ItemStack stack : bag) {
			if (PilSendiri.isKind(stack, kind)) {
				return true;
			}
		}
		return false;
	}

	private static boolean adaSumsum(List<ItemStack> bag) {
		for (ItemStack stack : bag) {
			if (Kultivator.id("marrow_cleansing_pill").equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				return true;
			}
		}
		return false;
	}

	private Tumbuh() {
	}
}
