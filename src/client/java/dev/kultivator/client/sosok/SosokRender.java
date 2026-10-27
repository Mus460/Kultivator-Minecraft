package dev.kultivator.client.sosok;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sosok on every human renderer (DESIGN 40.5.1, 40.6; contract C40.R1): the extract step that turns an entity's synced
 * Sosok and Gaya (or, for the bodies the server does not drive, a Sosok made on the client from their old look fields)
 * into this frame's {@link Keadaan}: the level of detail, the atlas (its own composed one, or the class fallback while it
 * waits or when it is far), the slim arms, the sleep pose and the request for 40G's evaluation. Renderers call, in order:
 * <ol>
 *   <li>{@link #ekstrak} after their own state fields (realm, Dao, rank, role...) are written;</li>
 *   <li>{@code PoseRenderData.extract} (cast 27 and duel 35, unchanged);</li>
 *   <li>{@link #selesai}: 40G's {@code GerakPengendali.evaluasi} for bodies that are not JAUH;</li>
 *   <li>in {@code setupRotations}: {@link #angkat} before {@code super}, {@link #baring} after it.</li>
 * </ol>
 * The per-entity {@link Keadaan} object is kept and reused across frames (no allocation per frame); entities not drawn
 * for 10 s are forgotten every 200 ticks, everything on disconnect. Client thread only.
 */
public final class SosokRender {

	/** What a human renderer knows about the look of a body (C40.R1). {@code kunciRupa} is a {@code RupaKunci} text form. */
	public record RupaMasukan(String kunciRupa, int realm, int dao, int sectRank, int physique, @Nullable LambangCache.Entry seragam,
							  int kotaGearKind, int headwearKind, boolean tembus) {
	}

	/** This frame's Sosok data of one body (read by 40K's layers and 40G's controller through {@link #keadaan}). */
	public static final class Keadaan {
		/** Entity id. */
		public int id;
		public Sosok sosok = new Sosok(Sosok.LAKI, Sosok.DEWASA, 3, 1);
		public Gaya gaya = Gaya.NETRAL;
		/** The look key in text form ({@code juru_lelang}, {@code murid.2|sekte=u:...}, {@code d.hakim}...). */
		public String kunciRupa = "";
		/** The body is drawn with a Sosok atlas on the 128 × 128 mesh (false: an iconic own skin or the old skin). */
		public boolean kulit;
		/** Gerak animates the body (also on iconic own skins). */
		public boolean gerak;
		/** See-through body (ghosts, phantoms, clones, energy bodies). */
		public boolean tembus;
		public SosokDetail.Lod lod = SosokDetail.Lod.JAUH;
		/** Springs this frame (PENUH and among the nearest). */
		public boolean pegas;
		/** The texture this frame: its own atlas or the class fallback. */
		public @Nullable Identifier atlas;
		/** True while {@link #atlas} is the class fallback. */
		public boolean atlasCadangan;
		public int kotaGearKind;
		public int headwearKind;
		/** Clip 13 {@code tidur}: the renderer lays the body down (on its side for variant {@code V_TIDUR_MIRING}). */
		public boolean tidur;
		public boolean tidurMiring;
		/**
		 * A clip asked by the client for a body without server clips (Dimension D people: talking 38, bowing 32, their
		 * work), 0 = none. 40G plays it as layer 6 when the body has no {@code DATA_GERAK}.
		 */
		public int klipKlien;
		public int varianKlien;
		/** Extra draw scale for bodies whose {@code Attributes.SCALE} the server does not set (D, rogues, envoys, spectators). */
		public float skalaGambar = 1.0F;

		long rupaHash = Long.MIN_VALUE;
		@Nullable Object rupa;
		boolean atlasSiap;
		@Nullable String kunciKelas;
		String kelas = KELAS[0];
		long dilihat;
		@Nullable String lamaKunci;
		int lamaLook;
		int lamaSlim;
		@Nullable Sosok lamaSosok;
		@Nullable String rupaMentah;
		@Nullable RupaKunci rupaKunci;
		String rupaTeks = "";

		/** True when the body's own atlas (not the fallback) is shown. */
		public boolean atlasSendiri() {
			return kulit && atlas != null && !atlasCadangan;
		}

		/**
		 * The resolved look of this body (40K's {@code SosokRupa}, made by {@code RupaResolver.dari} when the inputs last
		 * changed; null at JAUH before it was ever near, or when 40K failed). 40K's busana and face layers read their part
		 * choices and slot colours from it.
		 */
		public @Nullable Object rupa() {
			return rupa;
		}
	}

	public static final RenderStateDataKey<Keadaan> DATA = RenderStateDataKey.create(() -> "kultivator:sosok");

	/** The fallback atlas classes (40.3.9). */
	public static final String[] KELAS = {"sipil", "kultivator", "penjaga", "rahib", "bangsawan", "iblis"};
	private static final long LUPA_MS = 10_000L;

	private static final Int2ObjectOpenHashMap<Keadaan> KEADAAN = new Int2ObjectOpenHashMap<>();
	/** [kelamin][usia][kelas] fallback ids (null = not asked yet; {@link #TIDAK_ADA} = 40K has none). */
	private static final Identifier[][][] CADANGAN = new Identifier[2][4][KELAS.length];
	private static final Identifier TIDAK_ADA = Identifier.withDefaultNamespace("missingno");
	private static @Nullable Map<String, NpcRole> peranById;

	// ================================================================ extract

	/** Extract for a body the server drives ({@code DiscipleEntity}, {@code KultivatorNpc}: synced Sosok, Gaya, clip). */
	public static void ekstrak(LivingEntity entity, DiscipleRenderState state, float partialTicks, RupaMasukan masukan) {
		ekstrak(entity, state, partialTicks, masukan, SosokApi.sosok(entity), SosokApi.gaya(entity), true, 1.0F);
	}

	/**
	 * Extract with an explicit Sosok and Gaya: the client-made Sosok of D people, rogues and envoys ({@link #dariLama}).
	 * {@code kulit} false keeps the body's own iconic skin and mesh (Gerak still animates it). {@code skalaGambar} is
	 * multiplied into the draw scale (bodies whose scale attribute the server never sets pass {@code sosok.skala()}).
	 */
	public static void ekstrak(LivingEntity entity, DiscipleRenderState state, float partialTicks, RupaMasukan masukan, Sosok sosok, Gaya gaya,
			boolean kulit, float skalaGambar) {
		SosokDetail.mulaiFrame();
		int id = entity.getId();
		Keadaan k = KEADAAN.get(id);
		if (k == null) {
			k = new Keadaan();
			k.id = id;
			KEADAAN.put(id, k);
		}
		k.dilihat = Util.getMillis();
		SosokDetail.Tingkat tingkat = SosokDetail.tingkat();
		SosokDetail.Lod lod = SosokDetail.catat(SosokDetail.hitung(id, state.distanceToCameraSq, tingkat), state.distanceToCameraSq);
		k.lod = lod;
		k.pegas = lod == SosokDetail.Lod.PENUH && SosokDetail.bolehPegas(state.distanceToCameraSq);
		k.sosok = sosok;
		k.gaya = gaya;
		k.kunciRupa = masukan.kunciRupa();
		k.tembus = masukan.tembus();
		k.kotaGearKind = masukan.kotaGearKind();
		k.headwearKind = masukan.headwearKind();
		k.gerak = true;
		k.klipKlien = 0;
		k.varianKlien = 0;
		k.skalaGambar = skalaGambar;
		k.kulit = kulit;
		if (k.kunciKelas == null || !k.kunciKelas.equals(k.kunciRupa)) {
			k.kunciKelas = k.kunciRupa;
			k.kelas = kelasCadangan(k.kunciRupa);
		}
		if (kulit) {
			pilihAtlas(k, masukan, lod);
		}
		// the clip the server plays: clip 13 lays the body down
		SosokApi.Pemegang p = SosokApi.pemegang(entity);
		int gerak = p == null ? 0 : p.gerakData();
		k.tidur = GerakId.klip(gerak) == GerakId.TIDUR;
		k.tidurMiring = k.tidur && GerakId.varian(gerak) == GerakId.V_TIDUR_MIRING;
		if (k.tidur) {
			state.walkAnimationSpeed = 0.0F;
		}
		state.sosok = sosok.pack();
		state.gaya = gaya.pack();
		if (k.kulit) {
			state.texture = k.atlas;
			state.slim = sosok.lenganRamping();
		}
		((FabricRenderState) state).setData(DATA, k);
	}

	/**
	 * The atlas of a Sosok body: the class fallback when JAUH (never asks for a unique one there); otherwise its own atlas
	 * once composed, asked within the per-frame budget (nearest PENUH bodies first), the fallback while it waits. A body
	 * without even a fallback atlas goes back to the old skin and mesh.
	 */
	private static void pilihAtlas(Keadaan k, RupaMasukan m, SosokDetail.Lod lod) {
		Sosok sosok = k.sosok;
		Identifier cadangan = cadangan(sosok.kelamin(), sosok.usia(), k.kelas);
		Identifier atlas = null;
		if (lod != SosokDetail.Lod.JAUH && !SosokJembatan.kulitRusak()) {
			long hash = hashMasukan(sosok, k.gaya, m);
			if (hash != k.rupaHash || k.rupa == null) {
				k.rupaHash = hash;
				k.rupa = SosokJembatan.rupa(sosok, k.gaya, m);
				k.atlasSiap = false;
			}
			if (k.rupa != null) {
				if (k.atlasSiap) {
					atlas = SosokJembatan.atlas(k.rupa);
					k.atlasSiap = atlas != null;
				}
				if (atlas == null) {
					if (SosokDetail.bolehSusun(lod)) {
						long t0 = System.nanoTime();
						atlas = SosokJembatan.atlas(k.rupa);
						SosokDetail.catatSusun(System.nanoTime() - t0);
						k.atlasSiap = atlas != null;
					}
					if (atlas == null) {
						SosokDetail.catatMenunggu(lod);
					}
				}
			}
		}
		k.atlasCadangan = atlas == null;
		k.atlas = atlas != null ? atlas : cadangan;
		if (k.atlas == null) {
			k.kulit = false;
		}
	}

	/** FNV-1a 64 over every input that changes a look (the sect stamp included, so a redesigned uniform recomposes). */
	private static long hashMasukan(Sosok sosok, Gaya gaya, RupaMasukan m) {
		long h = 0xCBF29CE484222325L;
		h = mix(h, sosok.pack());
		h = mix(h, gaya.kunciAtlas());
		h = mix(h, gaya.jalan());
		h = mix(h, gaya.luka());
		h = mix(h, m.kunciRupa().hashCode());
		h = mix(h, m.realm());
		h = mix(h, m.dao());
		h = mix(h, m.sectRank());
		h = mix(h, m.physique());
		h = mix(h, m.kotaGearKind());
		h = mix(h, m.headwearKind());
		h = mix(h, m.tembus() ? 1 : 0);
		LambangCache.Entry seragam = m.seragam();
		if (seragam != null) {
			h = mix(h, seragam.key.hashCode());
			h = mix(h, seragam.stamp);
		}
		return h;
	}

	private static long mix(long h, int v) {
		for (int i = 0; i < 4; i++) {
			h ^= (v >>> (i * 8)) & 0xFF;
			h *= 0x100000001B3L;
		}
		return h;
	}

	/**
	 * The look key the server set for a body ({@code DATA_RUPA}; {@link RupaKunci#KOSONG} when none), parsed once per
	 * change of the synced text instead of every frame (the synced String keeps its instance until it changes).
	 */
	public static RupaKunci rupaDari(LivingEntity entity) {
		SosokApi.Pemegang p = SosokApi.pemegang(entity);
		String raw = p == null ? "" : p.rupaData();
		Keadaan k = KEADAAN.get(entity.getId());
		if (k != null && k.rupaKunci != null && raw == k.rupaMentah) {
			return k.rupaKunci;
		}
		RupaKunci rupa = RupaKunci.parse(raw);
		if (k != null) {
			k.rupaMentah = raw;
			k.rupaKunci = rupa;
			k.rupaTeks = rupa.kosong() ? "" : rupa.format();
		}
		return rupa;
	}

	/** The text form of {@link #rupaDari} (empty when the server set none), cached with it. */
	public static String teksRupaDari(LivingEntity entity, RupaKunci rupa) {
		Keadaan k = KEADAAN.get(entity.getId());
		if (k != null && k.rupaKunci == rupa) {
			return k.rupaTeks;
		}
		return rupa.kosong() ? "" : rupa.format();
	}

	/** 40G's evaluation for a body that is not JAUH (after {@code PoseRenderData.extract}, 40.5.1). */
	public static void selesai(LivingEntity entity, DiscipleRenderState state, float partialTicks) {
		Keadaan k = keadaan(state);
		if (k != null && k.gerak && k.lod != SosokDetail.Lod.JAUH) {
			// a client-made body (no synced Sosok) hands 40G its look key; disciples and town NPCs let 40G read theirs
			String kunci = entity instanceof SosokApi.Pemegang || k.kunciRupa.isEmpty() ? null : k.kunciRupa;
			SosokJembatan.evaluasi(entity, state, entity.tickCount + partialTicks, kunci);
		}
	}

	/** Asks 40G for a client clip on a body without server clips (call between {@link #ekstrak} and {@link #selesai}). */
	public static void mintaKlip(DiscipleRenderState state, int klip, int varian) {
		Keadaan k = keadaan(state);
		if (k != null) {
			k.klipKlien = klip;
			k.varianKlien = varian;
		}
	}

	// ================================================================ reading a state

	public static @Nullable Keadaan keadaan(EntityRenderState state) {
		return ((FabricRenderState) state).getData(DATA);
	}

	/** True when the body in {@code state} wears a Sosok atlas (the old robe layers stand down, C40.R1). */
	public static boolean aktif(EntityRenderState state) {
		Keadaan k = keadaan(state);
		return k != null && k.kulit;
	}

	/** True when Gerak animates the body in {@code state}. */
	public static boolean gerak(EntityRenderState state) {
		Keadaan k = keadaan(state);
		return k != null && k.gerak;
	}

	/** The Sosok of the body in {@code state}, or null when Sosok did not extract it (players, shadows, heart demons). */
	public static @Nullable Sosok sosok(EntityRenderState state) {
		Keadaan k = keadaan(state);
		return k == null ? null : k.sosok;
	}

	/** The extra draw scale of the body in {@code state} (1 for server-scaled bodies and non-Sosok states). */
	public static float skalaGambar(EntityRenderState state) {
		Keadaan k = keadaan(state);
		return k == null ? 1.0F : k.skalaGambar;
	}

	// ================================================================ setupRotations

	/** Before {@code super.setupRotations}: Gerak's BOB and SPIN (40.5.2). */
	public static void angkat(LivingEntityRenderState state, PoseStack poseStack) {
		Keadaan k = keadaan(state);
		if (k != null && k.gerak && k.lod != SosokDetail.Lod.JAUH) {
			SosokJembatan.angkat(state, poseStack);
		}
	}

	/**
	 * After {@code super.setupRotations}: clip 13 {@code tidur} lays the body down (on its back, or its side for
	 * {@code V_TIDUR_MIRING}); the rotation is 40G's {@code GerakApplier.putarTidur}, so the Gerak data and the drawn body
	 * always agree (40.5.10 row 13).
	 */
	public static void baring(LivingEntityRenderState state, PoseStack poseStack) {
		Keadaan k = keadaan(state);
		if (k != null && k.gerak && k.lod != SosokDetail.Lod.JAUH) {
			SosokJembatan.putarTidur(state, poseStack);
		}
	}

	// ================================================================ client-made Sosok (40.2.2 "Orang D, Liar...")

	/**
	 * A Sosok for a body the server does not drive, from its old synced fields: fixed sex/age for the roles with a fixed
	 * policy (Meng Po woman, King Yama, the judges and the Dragon King men...), otherwise the old slim arms decide the sex
	 * when they meant it ({@code slim} 0/1; -1 = not known, then the role's women share of 40.4.1 D decides), and the age
	 * mix of 40.4.1 D. Build and seed come from the UUID and look, so every player sees the same face.
	 */
	public static Sosok dariLama(String kunci, int look, int slim, UUID uuid) {
		long h = Sosok.aduk(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 23) ^ (long) look * 0x9E3779B97F4A7C15L);
		Kebijakan k = kebijakan(kunci);
		int kelamin;
		if (k.kelamin >= 0) {
			kelamin = k.kelamin;
		} else if (slim >= 0) {
			kelamin = slim == 1 ? Sosok.PEREMPUAN : Sosok.LAKI;
		} else {
			kelamin = (h & 127) < k.persenP * 1.28F ? Sosok.PEREMPUAN : Sosok.LAKI;
		}
		int roll = (int) ((h >>> 8) & 127) * 100 / 128;
		int usia = Sosok.DEWASA;
		int acc = 0;
		for (int i = 0; i < 4; i++) {
			acc += k.usia[i];
			if (roll < acc) {
				usia = i;
				break;
			}
		}
		int postur = (int) ((h >>> 16) & 7);
		int benih = (int) (h >>> 24) & Sosok.BENIH_MASK;
		return new Sosok(kelamin, usia, postur, benih);
	}

	/**
	 * {@link #dariLama(String, int, int, UUID)} for a drawn entity, remembered with its inputs so a body costs one
	 * comparison per frame instead of a hash and a new record.
	 */
	public static Sosok dariLama(LivingEntity entity, String kunci, int look, int slim) {
		Keadaan k = KEADAAN.get(entity.getId());
		if (k != null && k.lamaSosok != null && k.lamaLook == look && k.lamaSlim == slim && kunci.equals(k.lamaKunci)) {
			return k.lamaSosok;
		}
		Sosok sosok = dariLama(kunci, look, slim, entity.getUUID());
		if (k != null) {
			k.lamaKunci = kunci;
			k.lamaLook = look;
			k.lamaSlim = slim;
			k.lamaSosok = sosok;
		}
		return sosok;
	}

	/** Sex policy (-1 mixed) with the women's share, and the age weights {anak, muda, dewasa, tua} in percent. */
	private record Kebijakan(int kelamin, int persenP, int[] usia) {
	}

	private static final Kebijakan BAWAAN = new Kebijakan(-1, 50, new int[] {0, 40, 60, 0});
	private static final Kebijakan LIAR = new Kebijakan(-1, 30, new int[] {0, 50, 50, 0});

	private static final Map<String, Kebijakan> KEBIJAKAN = Map.ofEntries(
			Map.entry("d.meng_po", new Kebijakan(Sosok.PEREMPUAN, 100, new int[] {0, 0, 0, 100})),
			Map.entry("d.hakim", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 50, 50})),
			Map.entry("d.raja_yama", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.arwah", new Kebijakan(-1, 50, new int[] {10, 25, 40, 25})),
			Map.entry("d.penjaga_laut", new Kebijakan(-1, 25, new int[] {0, 40, 60, 0})),
			Map.entry("d.pertapa_laut", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 0, 100})),
			Map.entry("d.dewa_penglai", new Kebijakan(-1, 40, new int[] {0, 0, 100, 0})),
			Map.entry("d.nelayan", new Kebijakan(-1, 20, new int[] {0, 0, 60, 40})),
			Map.entry("d.pedagang", new Kebijakan(-1, 40, new int[] {0, 0, 100, 0})),
			Map.entry("d.raja_naga", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.kepala_lembu", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.muka_kuda", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.wuchang_putih", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.wuchang_hitam", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("d.hantu_lapar", new Kebijakan(-1, 50, new int[] {0, 0, 100, 0})),
			Map.entry("utusan_langit", new Kebijakan(Sosok.LAKI, 0, new int[] {0, 0, 100, 0})),
			Map.entry("penonton.utusan", new Kebijakan(-1, 40, new int[] {0, 45, 55, 0})),
			Map.entry("penonton.warga", new Kebijakan(-1, 50, new int[] {15, 0, 60, 25})));

	private static Kebijakan kebijakan(String kunci) {
		Kebijakan k = KEBIJAKAN.get(kunci);
		if (k != null) {
			return k;
		}
		return kunci.startsWith("liar.") ? LIAR : BAWAAN;
	}

	// ================================================================ fallback atlases

	/**
	 * The fallback atlas class of a look key (40.3.9): sipil, kultivator, penjaga, rahib, bangsawan, iblis. Town and 38
	 * roles go by their schedule group, with the demonic and noble roles named; disciples are cultivators, sect masters
	 * nobles; D, rogues, envoys and spectators by their row in 40.4.1 D.
	 */
	public static String kelasCadangan(String kunciRupa) {
		String kunci = kunciRupa;
		int bar = kunci.indexOf('|');
		if (bar >= 0) {
			kunci = kunci.substring(0, bar);
		}
		kunci = dev.kultivator.sosok.RupaKunci.tanpaGaya(kunci.toLowerCase(Locale.ROOT));
		if (kunci.startsWith("liar.") || kunci.contains("iblis") || kunci.equals("makelar_gelap") || kunci.equals("pembunuh_bayaran")) {
			return "iblis";
		}
		if (kunci.equals("ketua_sekte") || kunci.equals("murid.5") || kunci.startsWith("jalan.bangsawan")) {
			return "bangsawan";
		}
		if (kunci.startsWith("murid.") || kunci.startsWith("jalan.") || kunci.equals("utusan_langit") || kunci.equals("penonton.utusan")) {
			return kunci.equals("jalan.penjaga") ? "penjaga" : "kultivator";
		}
		switch (kunci) {
			case "d.hakim", "d.raja_yama", "d.dewa_penglai" -> {
				return "bangsawan";
			}
			case "d.penjaga_laut" -> {
				return "penjaga";
			}
			case "d.pertapa_laut" -> {
				return "rahib";
			}
			case "d.meng_po", "d.arwah", "d.nelayan", "d.pedagang", "penonton.warga" -> {
				return "sipil";
			}
			default -> {
			}
		}
		if (kunci.startsWith("kelompok.")) {
			return kelasKelompok(kunci.substring("kelompok.".length()).toUpperCase(Locale.ROOT));
		}
		NpcRole role = peran(kunci);
		if (role == null) {
			return "sipil";
		}
		if (role.iblis()) {
			return "iblis";
		}
		if (role.immortal() || role == NpcRole.JURU_LELANG) {
			return "bangsawan";
		}
		if (role == NpcRole.PENGEMBARA || role == NpcRole.SAINGAN) {
			return "kultivator";
		}
		return kelasKelompok(role.kelompok.name());
	}

	private static String kelasKelompok(String kelompok) {
		return switch (kelompok) {
			case "PENJAGA" -> "penjaga";
			case "RAHIB" -> "rahib";
			case "PEJABAT" -> "bangsawan";
			case "KULTIVATOR" -> "kultivator";
			default -> "sipil";
		};
	}

	private static @Nullable NpcRole peran(String id) {
		if (peranById == null) {
			Map<String, NpcRole> map = new HashMap<>();
			for (NpcRole role : NpcRole.values()) {
				map.put(role.id(), role);
			}
			peranById = map;
		}
		return peranById.get(id);
	}

	/** The fallback atlas of a sex, age and class (asked from 40K once, then cached); null when 40K has none. */
	public static @Nullable Identifier cadangan(int kelamin, int usia, String kelas) {
		int k = kelamin == Sosok.PEREMPUAN ? 1 : 0;
		int u = Math.max(0, Math.min(3, usia));
		int c = 0;
		for (int i = 0; i < KELAS.length; i++) {
			if (KELAS[i].equals(kelas)) {
				c = i;
				break;
			}
		}
		Identifier id = CADANGAN[k][u][c];
		if (id == null) {
			Identifier asked = SosokJembatan.cadangan(k, u, KELAS[c]);
			id = asked == null ? TIDAK_ADA : asked;
			CADANGAN[k][u][c] = id;
		}
		return id == TIDAK_ADA ? null : id;
	}

	// ================================================================ /sosok lod

	/**
	 * While {@code /sosok lod} is on, the name plate of every Sosok body reads its LOD, atlas kind and look key (cyan:
	 * own atlas, yellow: fallback, grey: no Sosok skin). Call after {@code super.extractRenderState}.
	 */
	public static void labelLod(LivingEntity entity, DiscipleRenderState state, float partialTicks) {
		if (!SosokDetail.labelLod()) {
			return;
		}
		Keadaan k = keadaan(state);
		if (k == null) {
			return;
		}
		String atlas = !k.kulit ? "-" : k.atlasCadangan ? "cadangan/" + k.kelas : "atlas";
		ChatFormatting color = !k.kulit ? ChatFormatting.GRAY : k.atlasCadangan ? ChatFormatting.YELLOW : ChatFormatting.AQUA;
		state.nameTag = Component.literal(k.lod.name() + (k.pegas ? "+pegas" : "") + " · " + atlas + " · " + k.kunciRupa).withStyle(color);
		if (state.nameTagAttachment == null) {
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		}
	}

	// ================================================================ housekeeping

	/** Forgets bodies not drawn for 10 s (every 200 client ticks). */
	static void rapikan() {
		long now = Util.getMillis();
		ObjectIterator<Int2ObjectMap.Entry<Keadaan>> it = KEADAAN.int2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			Int2ObjectMap.Entry<Keadaan> entry = it.next();
			if (now - entry.getValue().dilihat > LUPA_MS) {
				SosokDetail.lupakan(entry.getIntKey());
				it.remove();
			}
		}
	}

	/** World closed or {@code /sosok muat_ulang}: every body asks again, the fallback ids are asked again. */
	static void bersihkan() {
		KEADAAN.clear();
		for (Identifier[][] a : CADANGAN) {
			for (Identifier[] b : a) {
				java.util.Arrays.fill(b, null);
			}
		}
		SosokDetail.bersihkan();
	}

	/** The body with entity id {@code id} as last extracted, or null ({@code /sosok dump}). */
	static @Nullable Keadaan dariId(int id) {
		return KEADAAN.get(id);
	}

	static int jumlahDilacak() {
		return KEADAAN.size();
	}

	// ================================================================ old layers

	/**
	 * An old layer that only draws for bodies without a Sosok atlas (the disciple robe {@code DiscipleRobeLayer}, which
	 * the Sosok busana replaces, 40.7.2): iconic own skins and the old-skin fallback keep it.
	 */
	public static final class TanpaSosok extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private final RenderLayer<DiscipleRenderState, PlayerModel> inner;

		public TanpaSosok(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, RenderLayer<DiscipleRenderState, PlayerModel> inner) {
			super(parent);
			this.inner = inner;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			if (!aktif(state)) {
				inner.submit(poseStack, collector, lightCoords, state, yRot, xRot);
			}
		}
	}

	/** Adds the Sosok layers (busana, face, props behind their LOD gate) and the {@code /sosok bingkai} outline. */
	public static void pasangLapisan(java.util.function.Consumer<RenderLayer<DiscipleRenderState, PlayerModel>> add,
			RenderLayerParent<DiscipleRenderState, PlayerModel> parent, net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
		for (RenderLayer<DiscipleRenderState, PlayerModel> layer : SosokJembatan.lapisan(parent, context)) {
			add.accept(layer);
		}
		add.accept(new Bingkai(parent));
	}

	// ================================================================ /sosok bingkai (body outline)

	/**
	 * While {@code /sosok bingkai} is on: thin outlines of every cube of the posed body mesh (cyan) so the UV regions and
	 * the busana cubes (40K draws its own outlines) can be checked against the body. PENUH Sosok bodies only.
	 */
	public static final class Bingkai extends RenderLayer<DiscipleRenderState, PlayerModel> {

		private static final int WARNA = 0xFF40E0FF;

		public Bingkai(RenderLayerParent<DiscipleRenderState, PlayerModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			if (!SosokDetail.bingkai() || state.isInvisible) {
				return;
			}
			Keadaan k = keadaan(state);
			if (k == null || !k.kulit || k.lod != SosokDetail.Lod.PENUH) {
				return;
			}
			ModelPart root = this.getParentModel().root();
			java.util.List<float[]> boxes = new java.util.ArrayList<>();
			Vector3f v = new Vector3f();
			root.visit(new PoseStack(), (pose, path, index, cube) -> {
				Matrix4f m = pose.pose();
				float[] c = new float[24];
				for (int i = 0; i < 8; i++) {
					float x = ((i & 1) == 0 ? cube.minX : cube.maxX) / 16.0F;
					float y = ((i & 2) == 0 ? cube.minY : cube.maxY) / 16.0F;
					float z = ((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16.0F;
					m.transformPosition(x, y, z, v);
					c[i * 3] = v.x;
					c[i * 3 + 1] = v.y;
					c[i * 3 + 2] = v.z;
				}
				boxes.add(c);
			});
			float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
			collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				for (float[] c : boxes) {
					for (int a = 0; a < 8; a++) {
						for (int bit = 1; bit <= 4; bit <<= 1) {
							if ((a & bit) == 0) {
								garis(buffer, pose, c, a, a | bit, width);
							}
						}
					}
				}
			});
		}

		private static void garis(VertexConsumer buffer, PoseStack.Pose pose, float[] c, int a, int b, float width) {
			float nx = c[b * 3] - c[a * 3];
			float ny = c[b * 3 + 1] - c[a * 3 + 1];
			float nz = c[b * 3 + 2] - c[a * 3 + 2];
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1.0E-5F) {
				return;
			}
			nx /= len;
			ny /= len;
			nz /= len;
			buffer.addVertex(pose, c[a * 3], c[a * 3 + 1], c[a * 3 + 2]).setColor(WARNA).setNormal(pose, nx, ny, nz).setLineWidth(width);
			buffer.addVertex(pose, c[b * 3], c[b * 3 + 1], c[b * 3 + 2]).setColor(WARNA).setNormal(pose, nx, ny, nz).setLineWidth(width);
		}
	}

	private SosokRender() {
	}
}
