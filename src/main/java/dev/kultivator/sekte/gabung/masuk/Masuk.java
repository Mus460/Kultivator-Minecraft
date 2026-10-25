package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Module 41B "Masuk &amp; Ujian" (DESIGN 41.5): finding a sect, the five doors, the application, the three-stage entrance
 * test, the verdict, the oath ceremony and the Shifu choice. Entry of the package {@code sekte.gabung.masuk}.
 * <p>
 * {@link #registerContent()} runs always on both physical sides (rule K1: the four test entity types of
 * {@link GabungEntities}, the six items and six components of {@link GabungItems}); {@code Gabung} calls it before
 * {@code ModItems.init()} whatever {@code gabungSekte.aktif} says. {@link #init()} is the runtime part, called by
 * {@code Gabung.init()} while 41 is on; it calls {@link #registerContent()} first so a caller that only knows
 * {@code init()} still registers everything. The runtime parts check for a server themselves.
 * <p>
 * Parts: {@link Direktori} (which sects the player knows), {@link Perekrut} (recruiters' "Jasa" tablet),
 * {@link Lamaran} (the application, CALON), {@link HariUjian} (the test day and its stages), {@link TanggaUjian} (stage 1),
 * the corak test of 41B-C (stage 2, through {@link HariUjian#daftarTahap2}), {@link UjianHati} (stage 3, the servant's
 * talk, the guest trial), {@link Putusan} (verdict), {@link UpacaraSumpah} (oath), {@link PilihShifu} (teacher),
 * {@link TokenNaik} (tokens and letters), {@link MasukCerita} (story conditions and effects), {@link MasukPerintah}
 * (commands). Public entry points of other modules: {@link Lamaran#buka}, {@link HariUjian#mulai},
 * {@link PilihShifu#mulai} (contract C41.B1).
 */
public final class Masuk {

	/** NPC memory: the sect a body serves on a test day ({@code s<i>}). */
	public static final String MEM_SEKTE = "gabung_sekte";
	/** NPC memory: a body spawned for one test day only (sent home when the day ends). */
	public static final String MEM_CALON = "gabung_calon";
	/** Lang prefix of this module's lines ({@code lang-src/sekte_gabung_masuk.*}). */
	public static final String LANG = "kultivator.gabung.masuk.";

	private static boolean content;
	private static boolean initialised;

	/** K1: entity types, items and components of 41, always registered; safe to call twice. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		GabungEntities.register();
		GabungItems.register();
	}

	/** Runtime wiring of module 41B (server parts only touch a running server). Safe to call twice. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		// 41.12.3: every entity of 41 on a server counts toward one cap of 24 (tests, war squads, hunters).
		DuniaBudget.setCap(GabungEntities.ANGGARAN, Math.max(8, Math.min(48, GabungConfig.get().tubuhMaks)));
		// F41-1: the day before a sect's Test Day the towns talk about it (41W rumour gabung_hari_ujian)
		dev.kultivator.sekte.gabung.GabungHarian.addTugas((server, sekte, d, hari) -> {
			dev.kultivator.sekte.diplomasi.GerbangPlan.Entry e = dev.kultivator.sekte.gabung.api.GabungApi.entry(server, sekte);
			if (e != null && HariUjian.hariUjian(e.index(), hari + 1L, GabungConfig.get().hariUjian)) {
				dev.kultivator.sekte.gabung.dunia.KabarGabung.hariUjian(server, sekte);
			}
		});
		Direktori.init();
		Lamaran.init();
		Perekrut.init();
		HariUjian.init();
		// stage 2 = the corak test of 41B-C (UjianCorak.init runs from GabungEntities.register)
		HariUjian.daftarTahap2(new HariUjian.Tahap2() {
			@Override
			public @org.jetbrains.annotations.Nullable Component mulai(ServerPlayer player, HariUjian.Sesi sesi) {
				return UjianCorak.mulai(player, sesi.sekte, sesi.corak,
						(p, hasil) -> HariUjian.tahap2Selesai(p, hasil.skor(), hasil.gagal(), hasil.alasan()));
			}

			@Override
			public void batal(ServerPlayer player) {
				UjianCorak.batal(player);
			}
		});
		TanggaUjian.init();
		UjianHati.init();
		Putusan.init();
		UpacaraSumpah.init();
		PilihShifu.init();
		TokenNaik.init();
		MasukCerita.init();
		MasukPerintah.init();
	}

	// ================================================================== small shared helpers

	/** {@code kultivator.gabung.masuk.<key>} with arguments. */
	public static MutableComponent t(String key, Object... args) {
		return Component.translatable(LANG + key, args);
	}

	/** The sect's display name (its id when unknown). */
	public static Component nama(String sekte) {
		Component name = sekte == null || sekte.isEmpty() ? null : SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte == null || sekte.isEmpty() ? "?" : sekte) : name;
	}

	/**
	 * {@code npc} says a line: the Japanese voice {@code voice} when its sound exists, else {@code cadangan} (the older line
	 * of 41.11.7), and always the speech bubble {@code teks} (captions stay readable while the voice files are not made).
	 */
	public static void ucap(KultivatorNpc npc, String voice, String cadangan, Component teks) {
		if (npc == null || !npc.isAlive()) {
			return;
		}
		npc.say(teks, 100);
		if (VoiceEvents.exists(voice)) {
			Voice.say(npc, voice, "");
		} else if (cadangan != null && VoiceEvents.exists(cadangan)) {
			Voice.say(npc, cadangan, "");
		}
	}

	/** The 38 place ({@code situsId}) at {@code pos}, or 0 outside every place. */
	public static long situs(ServerLevel level, BlockPos pos) {
		TempatInfo info = TempatApi.tempatDi(level, pos);
		return info == null ? 0L : info.situsId();
	}

	/** Horizontal distance (a planned gate column has y 0 until it is generated). */
	public static double datar(Vec3 a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5D);
		double dz = a.z - (b.getZ() + 0.5D);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Dresses a sect body in the uniform of {@code sekte} ({@code murid.<n>|sekte=npc:<s>}, 40.7.4). */
	public static void rupa(KultivatorNpc npc, String kunci, String sekte) {
		if (npc == null || sekte == null || sekte.isEmpty()) {
			return;
		}
		String key = kunci.startsWith("murid.") ? kunci : kunci + "|sekte=npc:" + sekte;
		if (kunci.startsWith("murid.")) {
			int rank = switch (kunci.substring(6)) {
				case "pelayan" -> 0;
				case "luar" -> 1;
				case "dalam" -> 2;
				case "inti" -> 3;
				default -> 1;
			};
			key = RupaKunci.muridSekteNpc(rank, sekte).format();
		}
		SosokApi.setRupa(npc, key);
	}

	/** Into the player's inventory, the rest at their feet. */
	public static void beri(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ServerLevel level = player.level();
			ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setNoPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private Masuk() {
	}
}
