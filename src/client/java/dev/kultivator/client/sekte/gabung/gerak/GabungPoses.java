package dev.kultivator.client.sekte.gabung.gerak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sosok.gerak.Easing;
import dev.kultivator.client.sosok.gerak.GerakKatalog;
import dev.kultivator.client.sosok.gerak.Klip;
import dev.kultivator.client.sosok.gerak.Osilator;
import dev.kultivator.client.sosok.gerak.PropKatalog;
import dev.kultivator.client.sosok.gerak.Sampler;
import dev.kultivator.client.sosok.gerak.Wajah;
import dev.kultivator.fx.QiParticleOptions;
import dev.kultivator.fx.QiParticles;
import dev.kultivator.sekte.gabung.GabungPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static dev.kultivator.client.sosok.gerak.Klip.BAHU;
import static dev.kultivator.client.sosok.gerak.Klip.BICARA;
import static dev.kultivator.client.sosok.gerak.Klip.BZ;
import static dev.kultivator.client.sosok.gerak.Klip.DUDUK;
import static dev.kultivator.client.sosok.gerak.Klip.HX;
import static dev.kultivator.client.sosok.gerak.Klip.HY;
import static dev.kultivator.client.sosok.gerak.Klip.HZ;
import static dev.kultivator.client.sosok.gerak.Klip.JALAN_OK;
import static dev.kultivator.client.sosok.gerak.Klip.KAKI_PAKSA;
import static dev.kultivator.client.sosok.gerak.Klip.KERJA;
import static dev.kultivator.client.sosok.gerak.Klip.LAX;
import static dev.kultivator.client.sosok.gerak.Klip.LAY;
import static dev.kultivator.client.sosok.gerak.Klip.LAZ;
import static dev.kultivator.client.sosok.gerak.Klip.LEAN;
import static dev.kultivator.client.sosok.gerak.Klip.LIFT;
import static dev.kultivator.client.sosok.gerak.Klip.LIRIK_BAWAH;
import static dev.kultivator.client.sosok.gerak.Klip.LLX;
import static dev.kultivator.client.sosok.gerak.Klip.LLY;
import static dev.kultivator.client.sosok.gerak.Klip.LLZ;
import static dev.kultivator.client.sosok.gerak.Klip.MATA_TUTUP;
import static dev.kultivator.client.sosok.gerak.Klip.MOD_GEMETAR;
import static dev.kultivator.client.sosok.gerak.Klip.NAPAS;
import static dev.kultivator.client.sosok.gerak.Klip.PROP_SLOT_KANAN;
import static dev.kultivator.client.sosok.gerak.Klip.RAX;
import static dev.kultivator.client.sosok.gerak.Klip.RAY;
import static dev.kultivator.client.sosok.gerak.Klip.RAZ;
import static dev.kultivator.client.sosok.gerak.Klip.RLX;
import static dev.kultivator.client.sosok.gerak.Klip.RLY;
import static dev.kultivator.client.sosok.gerak.Klip.RLZ;
import static dev.kultivator.client.sosok.gerak.Klip.SY_P;
import static dev.kultivator.client.sosok.gerak.Klip.SY_TETUA;
import static dev.kultivator.client.sosok.gerak.Klip.TWIST;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.BIKSU_KUDA_KUDA;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.DENGAR_CERAMAH;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.DIUSIR_TUNDUK;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.DUPA_TIGA;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.KIPAS_TUNGKU;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.KOWTOW;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.LELAH_BERLUTUT;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PAMIT_BUNGKUK;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PENJAGA_MEMANGGIL;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PIKUL_AIR;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_DUPA_SUMPAH;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_EMBER_PIKUL;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_FIRST;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_GULUNGAN_ATURAN;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_KIPAS_BAMBU;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_LAST;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_LENCANA_ANGGOTA;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_MANGKUK_KETUA;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.PROP_PEDANG_KAYU;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.RENUNGAN;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.SALAM_SEKTE;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.SERAH_JUBAH_MANGKUK;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.SORAK_MURID;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.SUMPAH_BERLUTUT;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.TEBASAN_UJIAN;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.TERIMA_JUBAH_MANGKUK;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.TERIMA_LENCANA;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.TETUA_MENILAI;
import static dev.kultivator.sekte.gabung.api.GabungGerakId.TETUA_MENUNJUK_ATURAN;

/**
 * The body language of Gabung Sekte on the shared Gerak system (DESIGN 41.11.6, contract C41.V1): the hand props 150-156
 * and the clips 300-319 of {@code sekte.gabung.api.GabungGerakId}, registered through {@link PropKatalog#daftar} /
 * {@link GerakKatalog#daftar}; the client effect hooks their {@code gabung:*} KAIT events fire (incense threads, jade
 * glints, furnace embers, water drips, a sword arc, breath and sweat); and the player path of the oath, leaving, expulsion,
 * reflection and succession:
 * <ul>
 * <li>{@link #pemain}, called last by the {@code PlayerModelMixin} for every PlayerModel of a player (skin, armour, robe,
 * cape), lays the clip that {@code GabungClient.klip(uuid)} names over the model with {@code Sampler.terapkan}: a blend in
 * from the vanilla pose, a cross-fade from the previous 41 clip (the oath runs 300 -> 301 -> 302 -> 303 on a kneeling
 * body), a blend out when the server stops it, the end fade of once clips, and the whole-body {@code LIFT} as a shift of
 * the model root (kneeling and sitting reach the floor; {@code resetPose} clears it every frame). It yields to the
 * meditation seat and to cast and duel poses. On Minimal effect quality the clip holds one static pose.</li>
 * <li>{@link PropPemain}, a render layer on every avatar renderer, draws the clip's props on a posed player (the same
 * attachment geometry as {@code SosokPropLayer}, props change with the clip's PROP events).</li>
 * <li>A client tick fires the clip's sound and {@code gabung:*} hook events for posed players within 32 blocks (NPC bodies
 * get theirs from the Gerak controller).</li>
 * </ul>
 * Clip times of players are server game ticks since {@code GabungPayloads.Klip.mulai}, so every viewer sees the same
 * moment. Nothing here changes the world; effects scale with {@code EffectQuality}, none at Minimal or in a shader
 * shadow pass. Client thread only; called once from {@code GabungClient.init}.
 */
public final class GabungPoses {

	/** Ticks of the cross-fade between 41 clips and of the blend out. */
	private static final float BLEND = 6.0F;
	/** Player model scale (the renderer's 0.9375): LIFT blocks to model pixels. */
	private static final float PIKSEL_PER_BLOK = 16.0F / 0.9375F;
	/** Players farther than this from the camera get no clip events (sounds within 16). */
	private static final double JARAK_PERISTIWA_SQ = 32.0D * 32.0D;
	private static final double JARAK_PROP_SQ = 48.0D * 48.0D;

	// ================================================================== effect hook ids (KAIT)

	static final String K_DUPA = "gabung:dupa";
	static final String K_KILAU_GIOK = "gabung:kilau_giok";
	static final String K_BARA = "gabung:bara";
	static final String K_PERCIK = "gabung:percik";
	static final String K_TEBAS = "gabung:tebas";
	static final String K_NAPAS_BIKSU = "gabung:napas_biksu";
	static final String K_KERINGAT = "gabung:keringat";
	static final String K_NAPAS = "gabung:napas";
	static final String K_DESAH = "gabung:desah";
	private static final String[] KAIT = {K_DUPA, K_KILAU_GIOK, K_BARA, K_PERCIK, K_TEBAS, K_NAPAS_BIKSU, K_KERINGAT, K_NAPAS, K_DESAH};

	// ================================================================== shared poses (degrees, blocks)

	/** All channels the oath clips start from (the vanilla stance). */
	private static final float[] NOL = {RLX, 0, RLZ, 0, LLX, 0, LLZ, 0, LIFT, 0, LEAN, 0, BZ, 0, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0,
			LAZ, 0, HX, 0};
	/** On one knee: the right thigh forward, the left shin on the floor. */
	private static final float[] BERLUTUT_SATU = {RLX, -84, RLZ, 4, LLX, 68, LLZ, -4, LIFT, -0.42F, BZ, 0};
	/** On both knees, thighs angled back (the kowtow seat). */
	private static final float[] BERLUTUT_DUA = {RLX, 60, RLZ, 6, LLX, 62, LLZ, -6, LIFT, -0.4F};
	/** Fist in palm before the chest (baoquan, the numbers of 40's HORMAT). */
	private static final float[] BAOQUAN = {RAX, -74.5F, RAY, -35.5F, RAZ, 0, LAX, -71.6F, LAY, 35.5F, LAZ, 0};
	/**
	 * Cross-legged on the floor (40's ASAH_PEDANG numbers): keyed legs and LIFT rather than the BERSILA meditation seat, so
	 * an NPC and a player sit the same way and nothing sinks while the seat blends in.
	 */
	private static final float[] BERSILA_LANTAI = {RLX, -90, LLX, -90, RLY, 35, LLY, -35, RLZ, 20, LLZ, -20, LIFT, -0.65F};
	/** Fists chambered at the hips (the bronze monk). */
	private static final float[] KEPALAN_PINGGANG = {RAX, 22, RAY, 0, RAZ, 8, LAX, 22, LAY, 0, LAZ, -8};

	private static final Map<UUID, Jejak> JEJAK = new HashMap<>();
	private static final Int2ObjectOpenHashMap<Jejak> PER_ID = new Int2ObjectOpenHashMap<>();
	private static final Map<String, SoundEvent> SUARA = new HashMap<>();
	private static final float[] SCRATCH = new float[Klip.KANAL];
	private static boolean initialised;

	/** What one player is posed with, as the client saw it change (render clock = the player's tick count). */
	private static final class Jejak {
		int klip;
		int prop;
		long mulai;
		/** Tick count when this clip was first seen. */
		float awal;
		/** The clip before (cross-fade source), its frozen clip time and when the switch happened; 0 = none. */
		int klipLama;
		float tLama;
		float ganti;
		/** Tick count when the server stopped the clip (-1 = playing) and the clip time it froze at. */
		float henti = -1.0F;
		float tHenti;
		/** Event cursor: cycle and clip tick last processed (-1 = not yet). */
		int siklus;
		int dalam = -1;
		boolean dilihat;
	}

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		props();
		klipSumpah();
		klipHidup();
		klipTokoh();
		for (String id : KAIT) {
			GerakKatalog.daftarKait(id, (e, st, t) -> efek(id, e, st.bodyRot));
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new PropPemain(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GabungPoses::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			JEJAK.clear();
			PER_ID.clear();
		});
	}

	// ================================================================== props 150-156

	private static void props() {
		for (int id = PROP_FIRST; id <= PROP_LAST; id++) {
			String model = dev.kultivator.sekte.gabung.api.GabungGerakId.propModel(id);
			if (model != null) {
				PropKatalog.daftar(id, Kultivator.id(model), pegangan(id));
			}
		}
	}

	/**
	 * Hand grips (model conventions as 40's props, anchor (8, 8, 8)): the incense sticks stand upright above the fist
	 * whatever the arm does, the token hangs from its cord, the bowl stays level, the pole lies like 38's carrying pole.
	 */
	private static PropKatalog.Pegangan pegangan(int id) {
		return switch (id) {
			case PROP_DUPA_SUMPAH -> new PropKatalog.Pegangan(0.0F, -2.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, PropKatalog.TEGAK);
			case PROP_LENCANA_ANGGOTA -> new PropKatalog.Pegangan(0.0F, 1.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.9F, PropKatalog.TEGAK);
			case PROP_EMBER_PIKUL -> new PropKatalog.Pegangan(0.0F, 0.0F, 0.0F, 90.0F, 0.0F, 0.0F, 1.0F, PropKatalog.BESAR);
			case PROP_MANGKUK_KETUA -> new PropKatalog.Pegangan(0.0F, -0.5F, -1.5F, 0.0F, 0.0F, 0.0F, 0.9F, PropKatalog.TEGAK | PropKatalog.BESAR);
			case PROP_KIPAS_BAMBU -> new PropKatalog.Pegangan(0.0F, 0.0F, -2.0F, 90.0F, 0.0F, 0.0F, 1.0F, 0);
			case PROP_GULUNGAN_ATURAN -> new PropKatalog.Pegangan(0.0F, 0.0F, -1.0F, 90.0F, 0.0F, 0.0F, 1.0F, 0);
			case PROP_PEDANG_KAYU -> new PropKatalog.Pegangan(0.0F, 0.0F, -1.0F, 90.0F, 0.0F, 0.0F, 0.9F, 0);
			default -> null;
		};
	}

	// ================================================================== clips 300-303, 308, 309, 315, 317, 318: the player's

	private static Klip.Pembangun b(int id, int durasi) {
		return Klip.baru(id, "gabung_" + dev.kultivator.sekte.gabung.api.GabungGerakId.klipNama(id), durasi);
	}

	/** Concatenates channel/value pairs. */
	private static float[] p(float[]... bagian) {
		int n = 0;
		for (float[] a : bagian) {
			n += a.length;
		}
		float[] out = new float[n];
		int i = 0;
		for (float[] a : bagian) {
			System.arraycopy(a, 0, out, i, a.length);
			i += a.length;
		}
		return out;
	}

	private static float[] v(float... pasangan) {
		return pasangan;
	}

	/** The oath ceremony (41.5.6): kneel, incense, three prostrations, the token. */
	private static void klipSumpah() {
		// 300: a step forward, down onto the right knee, the hands meet in a baoquan, the head sinks
		GerakKatalog.daftar(b(SUMPAH_BERLUTUT, 60).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_DIAM)
				.kunci(0, p(NOL, v(NAPAS, 1)))
				.kunci(6, RLX, -45, LLX, 12, LIFT, -0.08F, LEAN, 10, BZ, -3, HX, 8, RAX, -30, RAY, -15, LAX, -28, LAY, 15)
				.kunciE(14, Easing.CUBIC_OUT, p(BERLUTUT_SATU, v(RAX, -60, RAY, -30, LAX, -58, LAY, 30, HX, 12, LEAN, 8)))
				.kunciE(18, Easing.BACK_OUT, RLX, -86, LEAN, 5)
				.kunci(26, p(BAOQUAN, v(HX, 14)))
				.kunci(40, HX, 24, LEAN, 12, NAPAS, 1.3F)
				.kunci(60, HX, 20, LEAN, 10, RAX, -76, LAX, -73, NAPAS, 1.3F)
				.suara(4, "item.armor.equip_leather", 0.2F, 1.3F).suara(14, "block.stone.step", 0.3F, 0.75F)
				.wajahPada(40, Wajah.MATA_TUTUP_TENANG, -1).bangun());

		// 301: kneeling, three lit sticks raised to the brow, a bow into the smoke, leaning forward to plant them
		GerakKatalog.daftar(b(DUPA_TIGA, 60).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(BERLUTUT_SATU, v(RAX, -62, RAY, -32, LAX, -60, LAY, 33, HX, 15, LEAN, 6)))
				.kunciE(12, Easing.CUBIC_OUT, RAX, -122, RAY, -24, LAX, -119, LAY, 26, HX, 26, LEAN, 10)
				.kunci(18, LEAN, 20, HX, 32)
				.kunci(26, LEAN, 8, HX, 14, RAX, -112, LAX, -109)
				.kunci(36, LEAN, 30, HX, 30, RAX, -66, RAY, -8, LAX, -50, LAY, 20)
				.kunciE(40, Easing.BACK_OUT, RAX, -54, RLX, -88, LIFT, -0.44F)
				.kunci(44, RAX, -60)
				.kunci(52, p(BAOQUAN, v(LEAN, 6, HX, 18, LIFT, -0.42F, RLX, -84)))
				.kunci(60, p(BAOQUAN, v(LEAN, 6, HX, 18)))
				.kait(0, K_DUPA).kait(8, K_DUPA).kait(16, K_DUPA).kait(24, K_DUPA).kait(32, K_DUPA)
				.suara(40, "block.bamboo.place", 0.35F, 1.4F)
				.propPada(41, PROP_SLOT_KANAN, 0)
				.wajahPada(14, Wajah.MATA_TUTUP_TENANG, -1).wajahPada(26, Wajah.MATA_SETENGAH, -1)
				.prop(PROP_DUPA_SUMPAH, 0, 0).bangun());

		// 302: onto both knees, then three full prostrations, hands joined at the brow between them
		Klip.Pembangun kowtow = b(KOWTOW, 60).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, p(BERLUTUT_SATU, BAOQUAN, v(LEAN, 6, HX, 18)))
				.kunciE(6, Easing.CUBIC_OUT, p(BERLUTUT_DUA, v(LEAN, 4, HX, 10, RAX, -118, RAY, -20, LAX, -116, LAY, 22)));
		for (int i = 0; i < 3; i++) {
			int s = 6 + 16 * i;
			// palms to the floor before the knees, the brow down to the stone
			kowtow.kunciE(s + 6, Easing.CUBIC_IN, LEAN, 78, HX, 72, RAX, -45, RAY, -14, LAX, -45, LAY, 14)
					.kunci(s + 9, LEAN, 80, HX, 76)
					.kunciE(s + 15, Easing.CUBIC_OUT, LEAN, 4, HX, 10, RAX, -118, RAY, -20, LAX, -116, LAY, 22)
					.suara(s + 8, "block.stone.hit", 0.18F, 0.7F)
					.wajahPada(s + 5, Wajah.MATA_TUTUP, -1).wajahPada(s + 13, Wajah.MATA_SETENGAH, -1);
		}
		GerakKatalog.daftar(kowtow.kunci(60, p(BAOQUAN, v(LEAN, 4, HX, 14))).bangun());

		// 303: still on both knees: both hands forward, the token laid in them, drawn to the chest, a deep bow of thanks
		GerakKatalog.daftar(b(TERIMA_LENCANA, 50).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_DIAM)
				.kunci(0, p(BERLUTUT_DUA, BAOQUAN, v(LEAN, 4, HX, 14)))
				.kunciE(8, Easing.CUBIC_OUT, RAX, -72, RAY, -12, RAZ, -4, LAX, -70, LAY, 12, LAZ, 4, LEAN, 14, HX, 22)
				.kunci(14, RAX, -76, LAX, -74)
				.kunci(24, RAX, -58, RAY, -38, RAZ, 0, LAX, -56, LAY, 40, LAZ, 0, LEAN, 8, HX, 16)
				.kunciE(32, Easing.CUBIC_OUT, LEAN, 32, HX, 30)
				.kunci(40, LEAN, 30, HX, 28)
				.kunci(50, LEAN, 6, HX, 12, RAX, -60, LAX, -58)
				.propPada(16, PROP_SLOT_KANAN, PROP_LENCANA_ANGGOTA)
				.suara(16, "block.amethyst_block.chime", 0.35F, 1.5F).kait(16, K_KILAU_GIOK)
				.wajahPada(16, Wajah.MATA_SENANG, Wajah.MULUT_SENYUM).wajahPada(30, Wajah.MATA_TUTUP_TENANG, -1)
				.wajahPada(44, Wajah.MATA_SENANG, -1).bangun());
	}

	/** Member life (41.6): the salute, the lecture, the chores, leaving, expulsion, reflection, the exhausted stair. */
	private static void klipHidup() {
		// 304: fist snapped into the palm at chest height, a measured bow; an elder returns it with a shallow nod
		GerakKatalog.daftar(b(SALAM_SEKTE, 30).sekali()
				.kunci(0, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.kunciE(5, Easing.CUBIC_OUT, p(BAOQUAN, v(RAZ, 2)))
				.kunciE(8, Easing.BACK_OUT, RAX, -78, LAX, -75)
				.kunci(16, LEAN, 20, HX, 18)
				.kunci(22, p(BAOQUAN, v(LEAN, 20, HX, 18)))
				.kunci(30, RAX, 0, RAY, 0, RAZ, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
				.suara(7, "item.armor.equip_leather", 0.15F, 1.6F)
				.varian(SY_TETUA, v -> v.wajah(Wajah.MATA_SETENGAH, -1)
						.kunci(0, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0)
						.kunci(6, RAX, -60, RAY, -30, LAX, -58, LAY, 30)
						.kunci(14, LEAN, 6, HX, 14).kunci(22, LEAN, 6, HX, 14, RAX, -60, LAX, -58)
						.kunci(30, RAX, 0, RAY, 0, LAX, 0, LAY, 0, LEAN, 0, HX, 0))
				.bangun());

		// 305: cross-legged in the lecture hall, palms on the knees, nodding along, following the speaker
		GerakKatalog.daftar(b(DENGAR_CERAMAH, 60).loop().bendera(DUDUK | KAKI_PAKSA).wajah(-1, Wajah.MULUT_DIAM)
				.kunci(0, p(BERSILA_LANTAI, v(RAX, -48, RAY, -20, RAZ, 22, LAX, -48, LAY, 20, LAZ, -22, HX, 6, LEAN, 4, NAPAS, 0.8F)))
				.kunci(18, HX, 6).kunciE(23, Easing.CUBIC_OUT, HX, 17).kunci(28, HX, 7)
				.kunci(46, HX, 7).kunciE(50, Easing.CUBIC_OUT, HX, 13).kunci(55, HX, 6)
				.osc(HY, 6.0F, 60.0F).osc(HZ, 2.0F, 60.0F, 1.2F, Osilator.Bentuk.SIN)
				.wajahPada(22, Wajah.MATA_SETENGAH, -1).wajahPada(30, Wajah.MATA_BUKA, -1).bangun());

		// 306: the carrying pole with two full buckets on the right shoulder, the right hand steadying it, a swaying walk
		GerakKatalog.daftar(b(PIKUL_AIR, 40).loop().bendera(JALAN_OK).wajah(-1, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -158, RAY, -6, RAZ, -10, LAX, -12, LAZ, -8, LEAN, 6, HZ, -3, BAHU, -0.3F)
				.osc(BZ, 2.5F, 20.0F).osc(LIFT, 0.03F, 20.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF)
				.osc(RAX, 3.0F, 20.0F, 0.6F, Osilator.Bentuk.SIN).osc(LAX, -5.0F, 20.0F).osc(HZ, 1.5F, 40.0F)
				.kait(10, K_PERCIK).kait(30, K_PERCIK)
				.suara(0, "block.bamboo.step", 0.12F, 0.8F).suara(20, "block.bamboo.step", 0.12F, 0.7F)
				// 38's carrying-pole place on the back: pole across the right shoulder, front to back
				.pasang(PROP_EMBER_PIKUL, Klip.Titik.PUNGGUNG, -4.5F, -5.2F, -2.7F, 0, 0, 0, 1.0F).bangun());

		// 307: squatting at the furnace mouth, the bamboo fan flicking fast, the free hand on the knee, blowing on the coals
		GerakKatalog.daftar(b(KIPAS_TUNGKU, 24).loop().bendera(KAKI_PAKSA | KERJA | LIRIK_BAWAH).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, LIFT, -0.36F, LEAN, 30, RLX, -72, LLX, -68, RLZ, 8, LLZ, -8, RAX, -72, RAY, -10, LAX, -38, LAZ, -12, HX, 18)
				.osc(RAZ, 22.0F, 8.0F).osc(RAY, 6.0F, 8.0F, 1.57F, Osilator.Bentuk.SIN).osc(LEAN, 1.5F, 24.0F)
				.kait(0, K_BARA).kait(12, K_BARA).suara(0, "block.campfire.crackle", 0.15F, 1.1F)
				.wajahPada(12, -1, Wajah.MULUT_MENIUP).wajahPada(18, -1, Wajah.MULUT_DIAM)
				.prop(PROP_KIPAS_BAMBU, 0, 0).bangun());

		// 308: honourable leave: baoquan, a deep held bow, then three steps backwards, head still lowered
		GerakKatalog.daftar(b(PAMIT_BUNGKUK, 60).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_DIAM)
				.kunci(0, NOL)
				.kunciE(6, Easing.CUBIC_OUT, BAOQUAN)
				.kunci(14, p(BAOQUAN, v(LEAN, 55, HX, 25)))
				.kunci(24, LEAN, 55, HX, 25)
				.kunciE(30, Easing.CUBIC_OUT, LEAN, 28, HX, 22, RAX, -40, RAY, -20, LAX, -38, LAY, 20, RLX, 0, LLX, 0, LIFT, 0, BZ, 0)
				.kunci(34, RLX, 22, LLX, -10, BZ, 2, LIFT, -0.03F).kunci(38, RLX, 0, LLX, 0, BZ, 0, LIFT, 0)
				.kunci(42, RLX, -10, LLX, 22, BZ, -2, LIFT, -0.03F).kunci(46, RLX, 0, LLX, 0, BZ, 0, LIFT, 0)
				.kunci(50, RLX, 22, LLX, -10, BZ, 2, LIFT, -0.03F).kunci(54, RLX, 0, LLX, 0, BZ, 0, LIFT, 0)
				.kunci(60, LEAN, 10, HX, 14, RAX, 0, RAY, 0, LAX, 0, LAY, 0)
				.suara(36, "block.stone.step", 0.12F, 0.9F).suara(44, "block.stone.step", 0.12F, 0.9F)
				.suara(52, "block.stone.step", 0.12F, 0.9F).bangun());

		// 309: expelled: head hung, shoulders dropped, arms dead at the sides, a slow walk with a sigh now and then
		GerakKatalog.daftar(b(DIUSIR_TUNDUK, 40).loop().bendera(JALAN_OK).wajah(Wajah.MATA_SEDIH, Wajah.MULUT_SEDIH)
				.kunci(0, HX, 38, LEAN, 12, BAHU, -0.8F, RAX, -4, RAZ, 3, LAX, -4, LAZ, -3)
				.osc(HZ, 3.0F, 40.0F).osc(BZ, 1.5F, 20.0F).osc(HX, 3.0F, 40.0F, 1.0F, Osilator.Bentuk.SIN)
				.osc(RAX, 4.0F, 20.0F).osc(LAX, -4.0F, 20.0F)
				.kait(28, K_DESAH).wajahPada(28, Wajah.MATA_TUTUP, -1).wajahPada(34, Wajah.MATA_SEDIH, -1).bangun());

		// 315: facing the wall of the Gua Renungan, hands folded in the lap, a slump of remorse, then sitting straight again
		GerakKatalog.daftar(b(RENUNGAN, 60).loop().bendera(DUDUK | KAKI_PAKSA | MATA_TUTUP).wajah(Wajah.MATA_TUTUP, Wajah.MULUT_DIAM)
				.kunci(0, p(BERSILA_LANTAI, v(RAX, -40, RAY, -38, LAX, -40, LAY, 38, HX, 22, LEAN, 6, BAHU, 0, NAPAS, 0.9F)))
				.kunci(30, HX, 26, BAHU, -0.4F, LEAN, 9)
				.kunci(48, HX, 21, BAHU, 0, LEAN, 5)
				.osc(HZ, 1.2F, 60.0F)
				.wajahPada(30, -1, Wajah.MULUT_MENGGIGIT_BIBIR).wajahPada(48, -1, Wajah.MULUT_DIAM).bangun());

		// 318: collapsed on the Mountain Pressure stair: one knee up on the next step, a hand on the stone, panting, trembling
		Klip.Pembangun lelah = b(LELAH_BERLUTUT, 40).loop().bendera(KAKI_PAKSA).mods(MOD_GEMETAR, 0.7F)
				.wajah(Wajah.MATA_SAKIT, Wajah.MULUT_A)
				.kunci(0, RLX, -72, LLX, 56, RLZ, 4, LLZ, -4, LIFT, -0.38F, LEAN, 34, RAX, -42, RAY, -8, RAZ, -4, LAX, -52, LAY, 10,
						LAZ, -10, HX, 12, NAPAS, 2.2F)
				.kunci(20, HX, 12, LEAN, 34).kunciE(26, Easing.CUBIC_OUT, HX, -12, LEAN, 26).kunci(34, HX, 10, LEAN, 34)
				.osc(LEAN, 4.0F, 10.0F).osc(HX, 4.0F, 10.0F, 0.5F, Osilator.Bentuk.SIN).osc(BAHU, 0.4F, 10.0F, 0.0F, Osilator.Bentuk.SIN_POSITIF)
				.kait(0, K_KERINGAT).kait(20, K_KERINGAT)
				.suara(0, "entity.player.breath", 0.2F, 1.1F).suara(20, "entity.player.breath", 0.2F, 1.0F);
		for (int t = 5; t < 40; t += 10) {
			lelah.kait(t, K_NAPAS).wajahPada(t, -1, Wajah.MULUT_O).wajahPada(t + 5, -1, Wajah.MULUT_A);
		}
		GerakKatalog.daftar(lelah.bangun());
	}

	/** The sect's people (41.5, 41.6.12, 41.8.5): elders, guards, instructors, the bronze monk, succession, spectators. */
	private static void klipTokoh() {
		// 310: an elder weighing a candidate: stroking the beard, the other hand behind the back, head tilting this way and that
		GerakKatalog.daftar(b(TETUA_MENILAI, 60).loop().wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -98, RAY, -42, RAZ, 0, LAX, 28.6F, LAZ, 12.6F, HX, -4, HZ, 4, LEAN, -3, BZ, 0)
				.kunci(12, RAX, -84, RAY, -38).kunci(20, RAX, -80).kunciE(26, Easing.CUBIC_OUT, RAX, -98, RAY, -42)
				.kunci(30, HZ, 4).kunciE(36, Easing.CUBIC_OUT, HZ, -3, HX, 2).kunci(38, RAX, -86).kunci(40, BZ, 2.5F)
				.kunci(46, RAX, -82).kunci(52, HZ, 4, HX, -4).kunci(54, RAX, -98)
				.osc(HY, 10.0F, 60.0F)
				.wajahPada(36, Wajah.MATA_LIRIK_KIRI, -1).wajahPada(48, Wajah.MATA_SETENGAH, -1).bangun());

		// 311: the Tetua Hukum reading the rules: the open scroll in the left hand, two taps on a line, then the raised finger
		GerakKatalog.daftar(b(TETUA_MENUNJUK_ATURAN, 60).loop().bendera(BICARA).wajah(Wajah.MATA_SETENGAH, -1)
				.kunci(0, LAX, -62, LAY, 30, LAZ, 0, RAX, -68, RAY, -22, RAZ, 0, HX, 12, LEAN, 4)
				.kunci(12, RAX, -68).kunciE(15, Easing.BACK_OUT, RAX, -60).kunci(18, RAX, -68).kunciE(21, Easing.BACK_OUT, RAX, -60)
				.kunci(24, RAX, -68, HX, 12)
				.kunciE(34, Easing.CUBIC_OUT, RAX, -118, RAY, -8, RAZ, 8, HX, -6, LEAN, -2)
				.kunci(46, RAX, -112, HX, -6)
				.kunci(56, RAX, -68, RAY, -22, RAZ, 0, HX, 12, LEAN, 4)
				.osc(HY, 6.0F, 30.0F)
				.suara(0, "item.book.page_turn", 0.2F, 0.9F)
				.wajahPada(34, Wajah.MATA_MARAH, -1).wajahPada(52, Wajah.MATA_SETENGAH, -1)
				.prop(0, PROP_GULUNGAN_ATURAN, 0).bangun());

		// 312: the gate guard calling the candidates: hand cupped at the mouth, chest out, the other hand on the hip
		GerakKatalog.daftar(b(PENJAGA_MEMANGGIL, 40).loop().bendera(BICARA).wajah(Wajah.MATA_BUKA, Wajah.MULUT_DIAM)
				.kunci(0, RAX, -20, RAY, 0, RAZ, 0, LAX, -10, LAZ, -25, LEAN, 0, HX, 0, NAPAS, 1)
				.kunciE(5, Easing.CUBIC_OUT, RAX, -122, RAY, -58, RAZ, 6, LEAN, -6, HX, -12, NAPAS, 1.5F)
				.kunci(8, LEAN, -8)
				.kunci(20, RAX, -118, LEAN, -4, HX, -10)
				.kunci(28, RAX, -24, RAY, -4, RAZ, 0, LEAN, 0, HX, 0, NAPAS, 1)
				.wajahPada(6, -1, Wajah.MULUT_O).wajahPada(12, -1, Wajah.MULUT_A).wajahPada(20, -1, Wajah.MULUT_DIAM).bangun());

		// 313: the instructor's demonstration with the wooden sword: guard, rising cut down to the right, backhand cut back
		float[] kuda = {RLX, -14, LLX, 12, RLZ, 6, LLZ, -6, LIFT, -0.06F};
		float[] jaga = p(kuda, v(RAX, -42, RAY, -12, RAZ, 0, LAX, -30, LAZ, 10, TWIST, 0, LEAN, 6, HX, 0));
		GerakKatalog.daftar(b(TEBASAN_UJIAN, 30).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_MARAH, Wajah.MULUT_DIAM)
				.kunci(0, jaga)
				.kunciE(6, Easing.CUBIC_OUT, RAX, -152, RAZ, -12, TWIST, -18, LEAN, -4, HX, -4)
				.kunciE(10, Easing.BACK_OUT, RAX, -38, RAZ, 24, TWIST, 22, LEAN, 14, HX, 8, RLX, -20, LLX, 18)
				.kunci(15, RAX, -78, RAY, -62, RAZ, 4, TWIST, -12, LEAN, 8)
				.kunciE(20, Easing.CUBIC_OUT, RAY, 48, TWIST, 30, LEAN, 12, HX, 4)
				.kunci(30, jaga)
				.kait(10, K_TEBAS).kait(20, K_TEBAS)
				.suara(9, "entity.player.attack.sweep", 0.22F, 1.35F).suara(19, "entity.player.attack.sweep", 0.22F, 1.15F)
				.wajahPada(10, Wajah.MATA_KILAT, Wajah.MULUT_MARAH).wajahPada(24, Wajah.MATA_MARAH, Wajah.MULUT_DIAM)
				.prop(PROP_PEDANG_KAYU, 0, 0).bangun());

		// 314: the bronze monk's horse stance: fists at the hips, a deep breath in, palms pushed out on a golden breath
		float[] kudaKuda = {LIFT, -0.26F, RLX, -32, LLX, -32, RLZ, 30, LLZ, -30, RLY, 10, LLY, -10, LEAN, 4};
		GerakKatalog.daftar(b(BIKSU_KUDA_KUDA, 60).loop().bendera(KAKI_PAKSA).wajah(Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_DIAM)
				.kunci(0, p(kudaKuda, KEPALAN_PINGGANG, v(HX, 0, BAHU, 0, NAPAS, 1.6F)))
				.kunci(16, BAHU, 0.5F, HX, -4)
				.kunciE(26, Easing.CUBIC_OUT, RAX, -88, RAY, -8, RAZ, 0, LAX, -88, LAY, 8, LAZ, 0, BAHU, 0, HX, 2)
				.kunci(40, RAX, -86, LAX, -86)
				.kunci(52, p(KEPALAN_PINGGANG, v(HX, 0)))
				.osc(LIFT, 0.015F, 60.0F)
				.kait(26, K_NAPAS_BIKSU).suara(26, "entity.player.breath", 0.25F, 0.6F)
				.wajahPada(26, Wajah.MATA_BUKA, Wajah.MULUT_O).wajahPada(36, Wajah.MATA_TUTUP_TENANG, Wajah.MULUT_DIAM).bangun());

		// 316: the old master hands over robe and bowl: a step forward, both hands offering, release, baoquan, a bow
		GerakKatalog.daftar(b(SERAH_JUBAH_MANGKUK, 60).tahan().bendera(KAKI_PAKSA).mods(MOD_GEMETAR, 0.35F)
				.wajah(Wajah.MATA_SETENGAH, Wajah.MULUT_SENYUM)
				.kunci(0, RAX, -62, RAY, -24, RAZ, 0, LAX, -60, LAY, 28, LAZ, 0, LEAN, 2, HX, 10, RLX, 0, LLX, 0)
				.kunci(10, RLX, -14, LLX, 6, LEAN, 10, HX, 14)
				.kunciE(18, Easing.CUBIC_OUT, RAX, -80, RAY, -12, LAX, -78, LAY, 14, LEAN, 16, HX, 18, RLX, -8, LLX, 4)
				.kunci(28, LEAN, 18, HX, 20)
				.kunciE(38, Easing.CUBIC_OUT, p(BAOQUAN, v(LEAN, 6, HX, 10, RLX, 0, LLX, 0)))
				.kunci(46, LEAN, 22, HX, 20)
				.kunci(60, p(BAOQUAN, v(LEAN, 4, HX, 8)))
				.propPada(30, PROP_SLOT_KANAN, 0).suara(30, "block.amethyst_block.chime", 0.3F, 0.9F)
				.wajahPada(46, Wajah.MATA_TUTUP_TENANG, -1)
				.prop(PROP_MANGKUK_KETUA, 0, 0).bangun());

		// 317: the heir receives: a step, down on one knee, hands raised, the bowl laid in them, lifted above the head
		GerakKatalog.daftar(b(TERIMA_JUBAH_MANGKUK, 60).tahan().bendera(KAKI_PAKSA).wajah(Wajah.MATA_LIRIK_BAWAH, Wajah.MULUT_DIAM)
				.kunci(0, NOL)
				.kunci(6, RLX, -45, LLX, 12, LIFT, -0.08F, LEAN, 10, BZ, -3)
				.kunciE(12, Easing.CUBIC_OUT, p(BERLUTUT_SATU, v(LEAN, 8, HX, 14, RAX, -40, LAX, -40)))
				.kunciE(20, Easing.CUBIC_OUT, RAX, -80, RAY, -14, LAX, -78, LAY, 16, LEAN, 12, HX, 22)
				.kunci(28, RAX, -82, LAX, -80)
				.kunci(38, RAX, -70, RAY, -20, LAX, -68, LAY, 22, LEAN, 6, HX, 16)
				.kunciE(48, Easing.CUBIC_OUT, RAX, -132, RAY, -16, LAX, -130, LAY, 18, HX, 26, LEAN, 4)
				.kunci(60, RAX, -98, LAX, -96, HX, 22, LEAN, 6)
				.propPada(30, PROP_SLOT_KANAN, PROP_MANGKUK_KETUA)
				.suara(30, "block.amethyst_block.chime", 0.3F, 0.9F).kait(30, K_KILAU_GIOK)
				.wajahPada(30, Wajah.MATA_SENANG, -1).wajahPada(48, Wajah.MATA_TUTUP_TENANG, -1).bangun());

		// 319: tournament spectators: a pumped fist, the other clenched at the chest; variant 1 both fists in turn;
		// women applaud before the chest
		GerakKatalog.daftar(b(SORAK_MURID, 20).loop().wajah(Wajah.MATA_SENANG, Wajah.MULUT_TERTAWA)
				.kunci(0, RAX, -165, RAZ, 10, LAX, -50, LAY, 25, HX, -12, LIFT, 0)
				.kunciE(10, Easing.BACK_OUT, RAX, -140, RAZ, 14, LIFT, 0.04F, HX, -8)
				.kunci(14, LIFT, 0)
				.varian(1, v -> v.kunci(0, RAX, -165, RAZ, 8, LAX, -150, LAZ, -8, HX, -14, LIFT, 0)
						.kunciE(10, Easing.BACK_OUT, RAX, -140, LAX, -165, LIFT, 0.05F).kunci(14, LIFT, 0))
				.varian(SY_P, v -> v.wajah(Wajah.MATA_SENANG, Wajah.MULUT_SENYUM)
						.kunci(0, RAX, -70, RAY, -40, LAX, -70, LAY, 40, HX, -6).kunci(5, RAY, -18, LAY, 18)
						.kunci(10, RAY, -40, LAY, 40).kunci(15, RAY, -18, LAY, 18))
				.bangun());
	}

	// ================================================================== effect hooks (NPC bodies and posed players)

	/**
	 * One {@code gabung:*} effect at body {@code e} facing {@code bodyRot} (degrees): a few particles placed for the pose
	 * of the clip that fires it. Never at Minimal quality or in a shader shadow pass; never changes the world.
	 */
	static void efek(String id, LivingEntity e, float bodyRot) {
		if (EffectQuality.minimal() || ShaderCompat.shadowPass() || !(e.level() instanceof ClientLevel level)) {
			return;
		}
		RandomSource r = level.getRandom();
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		// the body's right-hand side
		double kx = -Mth.cos(yaw);
		double kz = -Mth.sin(yaw);
		double x = e.getX();
		double y = e.getY();
		double z = e.getZ();
		double h = e.getBbHeight();
		switch (id) {
			case K_DUPA -> {
				// three thin threads from the glowing tips above the joined hands (kneeling)
				int n = Math.min(3, EffectQuality.scale(3));
				for (int i = 0; i < n; i++) {
					double s = (i - 1) * 0.035D;
					double px = x + fx * 0.34D + kx * (0.05D + s);
					double pz = z + fz * 0.34D + kz * (0.05D + s);
					double py = y + h * 0.68D + r.nextDouble() * 0.04D;
					level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xEEEAE2, 0xC9C4BA, 0x8E8A82, 0.035F, 0.16F,
							44 + r.nextInt(14), 0.0F, 0.018F, 0.0F, (byte) (QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)),
							px, py, pz, sebar(r, 0.004D), 0.018D, sebar(r, 0.004D));
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFE8B0, 0xFF8A30, 0xA03010, 0.03F, 0.01F,
							6, 0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY)), px, py - 0.02D, pz,
							0.0D, 0.0D, 0.0D);
				}
			}
			case K_KILAU_GIOK -> {
				// jade glints around the cupped hands
				int n = EffectQuality.scale(5);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(0xFFFFFF, 0x8FF0C8, 0x2FA078, 0.1F, 0.0F, 12 + r.nextInt(8), 0.0F, 0.01F, 0.0F,
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.STAR)),
							x + fx * 0.4D + sebar(r, 0.18D), y + h * 0.5D + sebar(r, 0.15D), z + fz * 0.4D + sebar(r, 0.18D), 0.0D, 0.0D, 0.0D);
				}
			}
			case K_BARA -> {
				// embers leaping from the furnace mouth ahead, low, and a curl of darker smoke
				int n = EffectQuality.scale(3);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFF0B0, 0xFF9030, 0xB03808, 0.05F, 0.015F,
							10 + r.nextInt(8), 0.0F, 0.0F, 0.0F, QiParticleOptions.GLOW), x + fx * 0.75D + sebar(r, 0.12D), y + 0.25D,
							z + fz * 0.75D + sebar(r, 0.12D), fx * 0.03D + sebar(r, 0.04D), 0.1D + r.nextDouble() * 0.08D,
							fz * 0.03D + sebar(r, 0.04D));
				}
				level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0x9A948A, 0x6A655E, 0x3C3934, 0.12F, 0.32F,
						34 + r.nextInt(14), 0.0F, 0.03F, 0.0F, (byte) 0), x + fx * 0.8D, y + 0.4D, z + fz * 0.8D, 0.0D, 0.03D, 0.0D);
			}
			case K_PERCIK -> {
				// water slopping over the rims of the front and back buckets
				for (int sisi = -1; sisi <= 1; sisi += 2) {
					if (r.nextFloat() < 0.4F && !EffectQuality.maximal()) {
						continue;
					}
					level.addParticle(ParticleTypes.FALLING_WATER, x + kx * 0.28D + fx * 0.55D * sisi + sebar(r, 0.08D), y + 0.45D,
							z + kz * 0.28D + fz * 0.55D * sisi + sebar(r, 0.08D), 0.0D, 0.0D, 0.0D);
				}
			}
			case K_TEBAS -> {
				// a pale arc where the wooden blade passed: upper right to lower left, before the chest
				int n = Math.max(3, EffectQuality.scale(6));
				for (int i = 0; i < n; i++) {
					double a = (i / (double) (n - 1) - 0.5D) * 2.2D;
					double sx = Math.sin(a) * 0.55D;
					double sy = Math.cos(a) * 0.45D;
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFFFFF, 0xDCEBFF, 0x8CA8D8, 0.07F, 0.0F, 6 + r.nextInt(3),
							0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY)),
							x + fx * 0.85D + kx * sx, y + h * 0.55D + sy * 0.6D, z + fz * 0.85D + kz * sx, 0.0D, 0.0D, 0.0D);
				}
			}
			case K_NAPAS_BIKSU -> {
				// a golden breath rolling forward from the monk's mouth
				int n = EffectQuality.scale(4);
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xFFF4C8, 0xFFD870, 0xC89430, 0.06F, 0.28F,
							20 + r.nextInt(8), 0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY)),
							x + fx * 0.35D, y + h * 0.74D, z + fz * 0.35D, fx * (0.06D + i * 0.015D) + sebar(r, 0.01D), sebar(r, 0.01D),
							fz * (0.06D + i * 0.015D) + sebar(r, 0.01D));
				}
			}
			case K_KERINGAT -> {
				// sweat flicked from the brow (kneeling)
				int n = EffectQuality.scale(2);
				for (int i = 0; i < n; i++) {
					level.addParticle(ParticleTypes.SPLASH, x + fx * 0.25D + sebar(r, 0.15D), y + h * 0.62D, z + fz * 0.25D + sebar(r, 0.15D),
							sebar(r, 0.05D), 0.05D, sebar(r, 0.05D));
				}
			}
			case K_NAPAS -> {
				// a faint pant of mist
				level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xFFFFFF, 0xEDEFF2, 0xC8CCD2, 0.04F, 0.14F, 14 + r.nextInt(6),
						0.0F, 0.0F, 0.0F, (byte) (QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)), x + fx * 0.32D, y + h * 0.56D,
						z + fz * 0.32D, fx * 0.04D, 0.005D, fz * 0.04D);
			}
			case K_DESAH -> {
				// one long sigh sinking from the lowered head
				level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xF4F4F4, 0xDCDCDC, 0xB4B4B4, 0.06F, 0.2F, 26,
						0.0F, -0.004F, 0.0F, (byte) (QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)), x + fx * 0.3D, y + h * 0.82D,
						z + fz * 0.3D, fx * 0.02D, -0.004D, fz * 0.02D);
			}
			default -> {
			}
		}
	}

	private static double sebar(RandomSource r, double d) {
		return (r.nextDouble() - 0.5D) * 2.0D * d;
	}

	// ================================================================== player path: tracking (client tick)

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		PER_ID.clear();
		if (level == null) {
			JEJAK.clear();
			return;
		}
		long now = level.getGameTime();
		for (Jejak j : JEJAK.values()) {
			j.dilihat = false;
		}
		for (AbstractClientPlayer p : level.players()) {
			UUID u = p.getUUID();
			GabungPayloads.Klip k = GabungClient.klip(u);
			Jejak j = JEJAK.get(u);
			float umur = p.tickCount;
			if (k != null && GerakKatalog.klip(k.klip()) != null) {
				if (j == null) {
					j = new Jejak();
					JEJAK.put(u, j);
					mulai(j, k, umur);
				} else if (j.klip != k.klip() || j.mulai != k.mulai()) {
					// a new clip: cross-fade from where the old one is now (or froze, if it is still fading out)
					boolean masihTerlihat = j.henti < 0.0F || umur - j.henti < BLEND;
					j.klipLama = masihTerlihat ? j.klip : 0;
					j.tLama = j.henti < 0.0F ? waktu(j, now) : j.tHenti;
					j.ganti = umur;
					mulai(j, k, umur);
				}
				j.prop = k.prop();
			} else if (j != null) {
				if (j.henti < 0.0F) {
					j.henti = umur;
					j.tHenti = waktu(j, now);
				} else if (umur - j.henti > BLEND || umur < j.henti) {
					JEJAK.remove(u);
					continue;
				}
			}
			if (j == null) {
				continue;
			}
			if (j.klipLama != 0 && (umur - j.ganti > BLEND || umur < j.ganti)) {
				j.klipLama = 0;
			}
			j.dilihat = true;
			PER_ID.put(p.getId(), j);
			if (j.henti < 0.0F && mc.player != null && mc.player.distanceToSqr(p) < JARAK_PERISTIWA_SQ) {
				peristiwa(level, p, j, now, mc.player.distanceToSqr(p));
			}
		}
		// players who left the level
		Iterator<Jejak> it = JEJAK.values().iterator();
		while (it.hasNext()) {
			if (!it.next().dilihat) {
				it.remove();
			}
		}
	}

	private static void mulai(Jejak j, GabungPayloads.Klip k, float umur) {
		j.klip = k.klip();
		j.prop = k.prop();
		j.mulai = k.mulai();
		j.awal = umur;
		j.henti = -1.0F;
		j.siklus = 0;
		j.dalam = -1;
	}

	private static float waktu(Jejak j, long now) {
		return Math.max(0.0F, now - j.mulai);
	}

	/** Fires the clip's sound and {@code gabung:*} events between the last processed clip tick and now (players only). */
	private static void peristiwa(ClientLevel level, AbstractClientPlayer p, Jejak j, long now, double jarakSq) {
		Klip k = GerakKatalog.klip(j.klip);
		if (k == null || k.peristiwa().length == 0) {
			return;
		}
		long tl = now - j.mulai;
		if (tl < 0) {
			return;
		}
		int tick = (int) Math.min(tl, Integer.MAX_VALUE);
		int dur = Math.max(1, k.durasi());
		int siklus = k.loop() ? Math.floorDiv(tick, dur) : 0;
		int dalam = k.loop() ? Math.floorMod(tick, dur) : Math.min(tick, dur);
		if (siklus == j.siklus && dalam == j.dalam) {
			return;
		}
		boolean awal = j.dalam < 0;
		int dari = awal ? dalam - 1 : j.dalam;
		boolean bungkus = !awal && siklus != j.siklus;
		for (Klip.Peristiwa ev : k.peristiwa()) {
			int et = ev.t();
			boolean kena = bungkus ? et > dari || et <= dalam : et > dari && et <= dalam;
			if (awal && et == 0 && dalam == 0) {
				kena = true;
			}
			if (!kena) {
				continue;
			}
			if (ev.jenis() == Klip.Jenis.SUARA && jarakSq < 256.0D) {
				SoundEvent s = suara(ev.id());
				if (s != null && ev.a() > 0.0F) {
					level.playLocalSound(p.getX(), p.getY() + p.getBbHeight() * 0.5D, p.getZ(), s, SoundSource.PLAYERS, ev.a(),
							Mth.clamp(ev.b() < 0.0F ? 1.0F : ev.b(), 0.5F, 2.0F), false);
				}
			} else if (ev.jenis() == Klip.Jenis.KAIT && ev.id().startsWith("gabung:")) {
				efek(ev.id(), p, p.yBodyRot);
			}
		}
		j.siklus = siklus;
		j.dalam = dalam;
	}

	private static @Nullable SoundEvent suara(String id) {
		if (SUARA.containsKey(id)) {
			return SUARA.get(id);
		}
		SoundEvent s = null;
		try {
			s = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id));
		} catch (RuntimeException ignored) {
			// a malformed id stays silent
		}
		SUARA.put(id, s);
		return s;
	}

	// ================================================================== player path: the pose (PlayerModelMixin)

	/**
	 * From the {@code PlayerModelMixin} (after the vanilla animation, the cast pose, Gerak's channels and the player walk):
	 * lays a posed player's 41 clip over {@code model}. Nothing for other avatars, a player without a 41 clip, while the
	 * meditation seat or a cast or duel pose holds the body.
	 */
	public static void pemain(PlayerModel model, AvatarRenderState state) {
		if (PER_ID.isEmpty()) {
			return;
		}
		Jejak j = PER_ID.get(state.id);
		if (j == null || MeditationPose.strength(state) > 0.5F || PoseRenderData.pose(state) != null) {
			return;
		}
		Klip cur = GerakKatalog.klip(j.klip);
		ClientLevel level = Minecraft.getInstance().level;
		if (cur == null || level == null) {
			return;
		}
		float umur = state.ageInTicks;
		boolean statis = EffectQuality.minimal();
		float tCur = waktuSekarang(j, level, umur);
		float w = bobot(j, cur, tCur, umur);
		if (statis) {
			tCur = statis(cur);
		}
		float lift = 0.0F;
		if (j.klipLama != 0 && j.henti < 0.0F && umur - j.ganti < BLEND) {
			Klip lama = GerakKatalog.klip(j.klipLama);
			if (lama != null) {
				float tl = statis ? statis(lama) : j.tLama;
				Sampler.terapkan(model, state, j.klipLama, 0, 1.0F, tl);
				lift = lift(lama, tl, state.id);
			}
		}
		if (w > 0.0F) {
			Sampler.terapkan(model, state, j.klip, 0, w, tCur);
			lift = Mth.lerp(w, lift, lift(cur, tCur, state.id));
		}
		if (lift != 0.0F) {
			// the whole body down (kneeling, sitting) or up (a hop): the root carries every part and the layers' hand frames
			model.root().y -= Mth.clamp(lift, -0.8F, 1.2F) * PIKSEL_PER_BLOK;
		}
	}

	/** The clip time this frame: frozen after a stop, else server ticks since the start plus the frame's fraction. */
	private static float waktuSekarang(Jejak j, ClientLevel level, float umur) {
		if (j.henti >= 0.0F) {
			return j.tHenti;
		}
		float t = (level.getGameTime() - j.mulai) + (umur - Mth.floor(umur));
		return Math.max(0.0F, t);
	}

	/** Weight of the current clip: blend in (or cross-fade), blend out after a stop, the end fade of a once clip. */
	private static float bobot(Jejak j, Klip cur, float t, float umur) {
		float w;
		if (j.henti >= 0.0F) {
			w = 1.0F - (umur - j.henti) / BLEND;
		} else if (j.klipLama != 0) {
			w = (umur - j.ganti) / BLEND;
		} else {
			// a looping clip eases in from the vanilla stance; once clips start from it in their own keys
			w = (umur - j.awal) / (cur.loop() ? BLEND : 3.0F);
		}
		w = halus(w);
		if (cur.mode() == Klip.Mode.SEKALI) {
			w *= halus((cur.durasi() - t) / BLEND);
		}
		return w;
	}

	private static float halus(float x) {
		float v = Mth.clamp(x, 0.0F, 1.0F);
		return v * v * (3.0F - 2.0F * v);
	}

	/** The static pose of Minimal quality: a held clip's last frame, a loop's first. */
	private static float statis(Klip k) {
		return k.loop() ? 0.0F : k.durasi();
	}

	/** The clip's LIFT at {@code t} (blocks; 0 when it does not drive it). */
	private static float lift(Klip k, float t, int benih) {
		if (!k.menggerakkan(LIFT)) {
			return 0.0F;
		}
		Sampler.sampel(k, t, SCRATCH, 1.0F, benih);
		float v = SCRATCH[LIFT];
		return Float.isFinite(v) ? v : 0.0F;
	}

	// ================================================================== player path: props

	/**
	 * The props of a posed player's 41 clip (DESIGN 41.11.6 props 150-156): the clip's hand, back and attached props, as
	 * its PROP events change them at this clip time; the payload's prop when the clip names none. A hand that holds a real
	 * item keeps it. Same attachment geometry as {@code SosokPropLayer} (fist frame, two-hand frame, body frame); nothing
	 * beyond 48 blocks, for invisible players or while the meditation seat or a cast pose holds the body.
	 */
	public static final class PropPemain extends RenderLayer<AvatarRenderState, PlayerModel> {

		private static final float PX = 1.0F / 16.0F;
		private static final ItemStackRenderState[] STATES = new ItemStackRenderState[256];

		private final PoseStack ukur = new PoseStack();
		private final Vector3f kanan = new Vector3f();
		private final Vector3f kiri = new Vector3f();
		private final Vector3f sumbuX = new Vector3f();
		private final Vector3f sumbuY = new Vector3f();
		private final Vector3f sumbuZ = new Vector3f();
		private final Matrix3f basis = new Matrix3f();
		private final Quaternionf putaran = new Quaternionf();
		private final Quaternionf lepas = new Quaternionf();

		public PropPemain(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (PER_ID.isEmpty() || state.isInvisible || state.distanceToCameraSq > JARAK_PROP_SQ) {
				return;
			}
			Jejak j = PER_ID.get(state.id);
			ClientLevel level = Minecraft.getInstance().level;
			if (j == null || level == null || MeditationPose.strength(state) > 0.5F || PoseRenderData.pose(state) != null) {
				return;
			}
			float umur = state.ageInTicks;
			int id = j.klip;
			float t = waktuSekarang(j, level, umur);
			if (j.henti >= 0.0F && umur - j.henti > BLEND * 0.5F) {
				return;
			}
			if (j.klipLama != 0 && j.henti < 0.0F && umur - j.ganti < BLEND * 0.5F) {
				// the first half of a cross-fade still shows the old clip's props
				id = j.klipLama;
				t = j.tLama;
			}
			Klip k = GerakKatalog.klip(id);
			if (k == null) {
				return;
			}
			if (EffectQuality.minimal()) {
				t = statis(k);
			}
			int pKanan = k.propKanan();
			int pKiri = k.propKiri();
			int pPunggung = k.propPunggung();
			boolean pasang = k.pasang().length > 0;
			boolean adaPeristiwa = false;
			int dur = Math.max(1, k.durasi());
			float dalam = k.loop() ? t % dur : Math.min(t, dur);
			for (Klip.Peristiwa ev : k.peristiwa()) {
				if (ev.jenis() != Klip.Jenis.PROP) {
					continue;
				}
				adaPeristiwa = true;
				if (ev.t() > dalam) {
					continue;
				}
				int slot = (int) ev.a();
				int prop = (int) ev.b();
				switch (slot) {
					case Klip.PROP_SLOT_KANAN -> pKanan = prop;
					case Klip.PROP_SLOT_KIRI -> pKiri = prop;
					case Klip.PROP_SLOT_PUNGGUNG -> pPunggung = prop;
					case Klip.PROP_SLOT_PASANG -> pasang = pasang && prop != 0;
					default -> {
					}
				}
			}
			if (pKanan == 0 && pKiri == 0 && pPunggung == 0 && !pasang && !adaPeristiwa && j.prop != 0) {
				pKanan = j.prop;
			}
			if (pKanan != 0 && state.rightHandItemState.isEmpty()) {
				tangan(poseStack, collector, lightCoords, state, HumanoidArm.RIGHT, pKanan, null);
			}
			if (pKiri != 0 && state.leftHandItemState.isEmpty()) {
				tangan(poseStack, collector, lightCoords, state, HumanoidArm.LEFT, pKiri, null);
			}
			if (pPunggung != 0) {
				badan(poseStack, collector, lightCoords, state, pPunggung, PropKatalog.punggung(pPunggung), null);
			}
			if (pasang) {
				for (Klip.PropPasang pp : k.pasang()) {
					pasang(poseStack, collector, lightCoords, state, pp);
				}
			}
		}

		private void pasang(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, Klip.PropPasang p) {
			int id = p.prop();
			if (id == 0) {
				return;
			}
			PlayerModel model = this.getParentModel();
			switch (p.titik()) {
				case KANAN -> {
					if (state.rightHandItemState.isEmpty()) {
						tangan(ps, c, light, state, HumanoidArm.RIGHT, id, p);
					}
				}
				case KIRI -> {
					if (state.leftHandItemState.isEmpty()) {
						tangan(ps, c, light, state, HumanoidArm.LEFT, id, p);
					}
				}
				case DUA_TANGAN -> {
					if (PropKatalog.tangan(id).panjang()) {
						if (state.rightHandItemState.isEmpty()) {
							tangan(ps, c, light, state, HumanoidArm.RIGHT, id, p);
						}
					} else if (state.rightHandItemState.isEmpty() && state.leftHandItemState.isEmpty()) {
						duaTangan(ps, c, light, state, id, p);
					}
				}
				case PUNGGUNG -> badan(ps, c, light, state, id, PropKatalog.punggung(id), p);
				case PINGGANG_KIRI -> titikBadan(ps, c, light, state, id, p, 2.6F, 10.5F, -2.6F);
				case PINGGANG_KANAN -> titikBadan(ps, c, light, state, id, p, -2.6F, 10.5F, -2.6F);
				case BAHU_KIRI -> titikBadan(ps, c, light, state, id, p, 4.2F, 0.6F, -1.6F);
				case KEPALA -> {
					ps.pushPose();
					model.root().translateAndRotate(ps);
					model.head.translateAndRotate(ps);
					ps.translate(0.0F, -8.6F * PX, 0.5F * PX);
					geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
					gambar(ps, c, light, state, id);
					ps.popPose();
				}
				// seated laps and ground pieces are NPC-only in 41's clips (their floor offsets need the seat)
				default -> {
				}
			}
		}

		/** In one fist: the grip (mirrored for the left hand; upright props undo the arm's turn), then the clip offset. */
		private void tangan(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, HumanoidArm arm, int id,
				Klip.PropPasang p) {
			float m = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
			PlayerModel model = this.getParentModel();
			ps.pushPose();
			model.translateToHand(state, arm, ps);
			ps.translate(-m * PX, 10.5F * PX, -0.5F * PX);
			PropKatalog.Pegangan g = PropKatalog.tangan(id);
			if (g.tegak()) {
				var a = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
				ps.rotate(lepas.rotationZYX(a.zRot, a.yRot, a.xRot).conjugate());
			}
			geser(ps, g.dx() * m, g.dy(), g.dz(), g.rx(), g.ry() * m, g.rz() * m, g.skala());
			if (p != null) {
				geser(ps, p.dx() * m, p.dy(), p.dz(), p.rx(), p.ry() * m, p.rz() * m, p.skala());
			}
			gambar(ps, c, light, state, id);
			ps.popPose();
		}

		/** Between the two fists, its x along the right fist to the left one, upright otherwise. */
		private void duaTangan(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, int id, Klip.PropPasang p) {
			PlayerModel model = this.getParentModel();
			kepalan(model, state, HumanoidArm.RIGHT, kanan);
			kepalan(model, state, HumanoidArm.LEFT, kiri);
			sumbuX.set(kiri).sub(kanan);
			if (sumbuX.lengthSquared() < 1.0E-6F) {
				sumbuX.set(1.0F, 0.0F, 0.0F);
			} else {
				sumbuX.normalize();
			}
			sumbuY.set(sumbuX).mul(-sumbuX.y).add(0.0F, 1.0F, 0.0F);
			if (sumbuY.lengthSquared() < 1.0E-6F) {
				sumbuY.set(0.0F, 1.0F, 0.0F);
			} else {
				sumbuY.normalize();
			}
			sumbuX.cross(sumbuY, sumbuZ);
			basis.set(sumbuX, sumbuY, sumbuZ);
			putaran.setFromNormalized(basis);
			ps.pushPose();
			ps.translate((kanan.x + kiri.x) * 0.5F, (kanan.y + kiri.y) * 0.5F, (kanan.z + kiri.z) * 0.5F);
			ps.rotate(putaran);
			PropKatalog.Pegangan g = PropKatalog.duaTangan(id);
			geser(ps, g.dx(), g.dy(), g.dz(), g.rx(), g.ry(), g.rz(), g.skala());
			geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
			gambar(ps, c, light, state, id);
			ps.popPose();
		}

		private void kepalan(PlayerModel model, AvatarRenderState state, HumanoidArm arm, Vector3f out) {
			ukur.pushPose();
			ukur.setIdentity();
			model.translateToHand(state, arm, ukur);
			ukur.translate((arm == HumanoidArm.LEFT ? 1.0F : -1.0F) * PX, 10.5F * PX, -0.5F * PX);
			ukur.last().pose().transformPosition(0.0F, 0.0F, 0.0F, out);
			ukur.popPose();
		}

		private void badan(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, int id, PropKatalog.Pegangan g,
				Klip.PropPasang p) {
			PlayerModel model = this.getParentModel();
			ps.pushPose();
			model.root().translateAndRotate(ps);
			model.body.translateAndRotate(ps);
			geser(ps, g.dx(), g.dy(), g.dz(), g.rx(), g.ry(), g.rz(), g.skala());
			if (p != null) {
				geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
			}
			gambar(ps, c, light, state, id);
			ps.popPose();
		}

		private void titikBadan(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, int id, Klip.PropPasang p, float x,
				float y, float z) {
			PlayerModel model = this.getParentModel();
			ps.pushPose();
			model.root().translateAndRotate(ps);
			model.body.translateAndRotate(ps);
			ps.translate(x * PX, y * PX, z * PX);
			geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
			gambar(ps, c, light, state, id);
			ps.popPose();
		}

		private static void geser(PoseStack ps, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
			if (dx != 0.0F || dy != 0.0F || dz != 0.0F) {
				ps.translate(dx * PX, dy * PX, dz * PX);
			}
			if (rx != 0.0F) {
				ps.rotateDegrees(Axis.XP, rx);
			}
			if (ry != 0.0F) {
				ps.rotateDegrees(Axis.YP, ry);
			}
			if (rz != 0.0F) {
				ps.rotateDegrees(Axis.ZP, rz);
			}
			if (skala != 1.0F && skala > 0.0F) {
				ps.scale(skala, skala, skala);
			}
		}

		/** Draws prop {@code id} with the piece convention of {@code FormModels} (its +y up, its (8, 8, 8) at the origin). */
		private static void gambar(PoseStack ps, SubmitNodeCollector c, int light, AvatarRenderState state, int id) {
			ItemStack stack = PropKatalog.stack(id);
			if (stack == null || id < 0 || id >= STATES.length) {
				return;
			}
			ItemStackRenderState item = STATES[id];
			if (item == null) {
				item = new ItemStackRenderState();
				STATES[id] = item;
			}
			Minecraft mc = Minecraft.getInstance();
			mc.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
			if (item.isEmpty()) {
				return;
			}
			ps.pushPose();
			ps.rotateDegrees(Axis.ZP, 180.0F);
			item.submit(ps, c, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
			ps.popPose();
		}
	}

	private GabungPoses() {
	}
}
