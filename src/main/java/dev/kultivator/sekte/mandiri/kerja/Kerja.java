package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.power.NatalMandiri;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module 39D, Kerja, Kerajinan &amp; Pergaulan (DESIGN 39.4.2, 39.6.7, 39.6.8, 39.12): the entry of package
 * {@code sekte.mandiri.kerja}, called by {@code sekte.mandiri.Mandiri} (the one line per module).
 * <ul>
 *   <li>Executors (C39.D1): LATIH_JURUS {@link LatihJurus}, TANDING {@link Tanding}, MERAMU {@link AlkimiaMurid}, JIMAT
 *   {@link JimatMurid}, KEBUN / TAMBANG / POS_TUGAS / JAGA_FORMASI {@link KerjaSekte} (through the new bridges
 *   {@code KebunMandiri} and the additive {@code Duties.assignSelf}, {@code Keepers.assignSelf}), SOSIAL {@link TehMandiri}
 *   (through {@code HubunganMandiri}).</li>
 *   <li>Item marks (C39.D2): {@code kultivator:buatan_murid}, {@code kultivator:beli_murid} ({@link KerjaKomponen}).</li>
 *   <li>The natal treasure the disciple works on by itself ({@link NatalSendiri}, bridge {@code NatalMandiri}).</li>
 *   <li>Catch-up events of 39.4.7 step 4: a brew, a practice, a tea with a sworn friend.</li>
 *   <li>C39.K5 {@code MandiriApi.setDagangan}: the disciple's own work it agrees to sell.</li>
 *   <li>The info compound {@code sekte_mandiri_kerja} for the Jalan Hidup tab (39E) and the test commands
 *   {@code uji perguruan mandiri kerja ...} ({@link KerjaCommands}).</li>
 * </ul>
 * {@link #registerContent} runs always (K1: the data components never follow a switch); {@link #init} only while
 * {@code umum.mandiri} and {@code mandiri.aktif} are on. Both are safe to call twice. No block ever changes here.
 */
public final class Kerja {

	/** The {@code DiscipleHooks.addInfo} key of 39D (skills and records, at most a few hundred bytes). */
	public static final String INFO = "sekte_mandiri_kerja";

	private static boolean content;
	private static boolean initialised;

	/** Registry content of 39D (always, both physical sides): the two item marks and their tooltips. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		KerjaKomponen.register();
	}

	/** Runtime behaviour of 39D (server listeners, ticks, executors, commands); only while the module is on. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		MandiriApi.register(Kegiatan.LATIH_JURUS, new LatihJurus());
		MandiriApi.register(Kegiatan.TANDING, new Tanding());
		MandiriApi.register(Kegiatan.MERAMU, new AlkimiaMurid());
		MandiriApi.register(Kegiatan.JIMAT, new JimatMurid());
		MandiriApi.register(Kegiatan.SOSIAL, new TehMandiri());
		KerjaSekte.daftar();
		DiscipleHooks.addTick20(NatalSendiri::tick20);
		ServerTickEvents.END_SERVER_TICK.register(TehMandiri::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Tanding.kosongkan();
			TehMandiri.kosongkan();
		});
		MandiriApi.addPeristiwaKejar(AlkimiaMurid::kejar);
		MandiriApi.addPeristiwaKejar(LatihJurus::kejar);
		MandiriApi.addPeristiwaKejar(TehMandiri::kejar);
		MandiriApi.setDagangan(Kerja::dagangan);
		DiscipleHooks.addInfo((entity, master, tag) -> {
			if (entity.role() == DiscipleEntity.Role.DISCIPLE && MandiriApi.aktif()) {
				tag.put(INFO, info(entity));
			}
		});
		KerjaCommands.init();
	}

	/** Skills and records of the disciple for the tab: alchemy, talismans, sparring, natal treasure. */
	static CompoundTag info(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		CompoundTag own = DataKerja.baca(data);
		CompoundTag tag = new CompoundTag();
		tag.putInt("alkimia", DataKerja.levelAlkimia(data));
		tag.putLong("jamMeramu", MandiriApi.statistik(data, MandiriIds.S_JAM_MERAMU));
		tag.putInt("jimat", DataKerja.levelJimat(data));
		tag.putInt("jimatXp", DataKerja.jimatXp(data));
		tag.putInt("jimatBuat", own.getIntOr(DataKerja.JIMAT_BUAT, 0));
		tag.putInt("elo", DataKerja.elo(data));
		tag.putInt("menang", own.getIntOr(DataKerja.MENANG, 0));
		tag.putInt("kalah", own.getIntOr(DataKerja.KALAH, 0));
		tag.putInt("seri", own.getIntOr(DataKerja.SERI, 0));
		tag.putInt("karya", (int) MandiriApi.statistik(data, MandiriIds.S_KARYA));
		tag.putInt("natalForm", NatalMandiri.form(data));
		tag.putInt("natalLipat", NatalMandiri.folds(data));
		return tag;
	}

	/**
	 * C39.K5: the disciple's own work in its bag ({@code kultivator:buatan_murid} by this soul, never something it bought)
	 * that it agrees to sell; it keeps two healing pills and one talisman of each kind for itself. New stacks every call;
	 * an unloaded disciple offers nothing.
	 */
	static List<ItemStack> dagangan(UUID owner, UUID soul) {
		List<ItemStack> out = new ArrayList<>();
		if (owner == null || soul == null) {
			return out;
		}
		DiscipleEntity body = null;
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			if (soul.equals(e.soulId())) {
				body = e;
				break;
			}
		}
		if (body == null) {
			return out;
		}
		List<net.minecraft.world.item.Item> kept = new ArrayList<>();
		int healingKept = 0;
		for (ItemStack stack : Bahan.isiTas(body, null)) {
			KerjaKomponen.Karya karya = KerjaKomponen.buatan(stack);
			if (karya == null || !soul.equals(karya.soul()) || KerjaKomponen.dibeli(stack)) {
				continue;
			}
			ItemStack offer = stack.copy();
			if (offer.is(dev.kultivator.alchemy.AlchemyItems.HEALING_PILL) && healingKept < 2) {
				int keep = Math.min(2 - healingKept, offer.getCount());
				healingKept += keep;
				offer.shrink(keep);
			} else if (offer.has(dev.kultivator.talisman.Talismans.TALISMAN)
					&& !kept.contains(offer.getItem())) {
				kept.add(offer.getItem());
				offer.shrink(1);
			}
			if (!offer.isEmpty()) {
				out.add(offer);
			}
		}
		return out;
	}

	private Kerja() {
	}
}
