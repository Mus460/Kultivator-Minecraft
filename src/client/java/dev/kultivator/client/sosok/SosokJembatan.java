package dev.kultivator.client.sosok;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.sosok.busana.BusanaLayer;
import dev.kultivator.client.sosok.busana.SosokGear;
import dev.kultivator.client.sosok.busana.SosokLapisan;
import dev.kultivator.client.sosok.busana.WajahLayer;
import dev.kultivator.client.sosok.gerak.GerakApplier;
import dev.kultivator.client.sosok.gerak.GerakKatalog;
import dev.kultivator.client.sosok.gerak.GerakKlien;
import dev.kultivator.client.sosok.gerak.GerakPengendali;
import dev.kultivator.client.sosok.gerak.SosokPropLayer;
import dev.kultivator.client.sosok.kulit.KulitCache;
import dev.kultivator.client.sosok.kulit.KulitCadangan;
import dev.kultivator.client.sosok.kulit.KulitPenyusun;
import dev.kultivator.client.sosok.kulit.RupaResolver;
import dev.kultivator.client.sosok.kulit.SosokRupa;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.Sosok;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The one place where 40R calls into the skin and busana module (40K, {@code client/sosok/kulit/**},
 * {@code client/sosok/busana/**}) and the animation module (40G, {@code client/sosok/gerak/**}). Every renderer goes
 * through {@link SosokRender}; nothing else of 40R names a 40K or 40G class, so a contract that landed with another
 * signature is fixed here in one line (the contracts are C40.K1-K3, C40.G1-G2 of DESIGN 40.7.3).
 * <p>
 * Each call is guarded: an exception inside 40K or 40G logs one line and turns that part off for the session (the
 * bodies fall back to the class fallback atlas, or to the old player skin when even that fails), so a broken atlas or
 * clip never takes the render loop down.
 */
public final class SosokJembatan {

	private static boolean kulitRusak;
	private static boolean gerakRusak;
	private static boolean lapisanRusak;

	// ================================================================ init (C40.K3)

	/** 40K's model layers ({@code kultivator:sosok_tubuh(_ramping)}, busana, face) before the entity models are baked. */
	static void daftarLapisanModel() {
		SosokLapisan.register();
	}

	/** 40G's catalogue, clip registry and its 200-tick cleaner. */
	static void initGerak() {
		GerakKlien.init();
	}

	// ================================================================ models (40.3.1)

	/** The Sosok body mesh (player mesh on a 128 × 128 texture) with wide or slim arms. */
	public static ModelPart tubuh(EntityRendererProvider.Context context, boolean ramping) {
		return context.bakeLayer(ramping ? SosokLapisan.TUBUH_RAMPING : SosokLapisan.TUBUH);
	}

	/**
	 * The Sosok layers of a human renderer, each behind {@link Gerbang} (LOD, shadow pass, see-through): busana and hair
	 * cubes (40K), the animated face (40K), the clip props (40G).
	 */
	public static List<RenderLayer<DiscipleRenderState, PlayerModel>> lapisan(RenderLayerParent<DiscipleRenderState, PlayerModel> parent,
			EntityRendererProvider.Context context) {
		return List.of(
				new Gerbang(parent, new BusanaLayer(parent, context), Gerbang.BUSANA),
				new Gerbang(parent, new WajahLayer(parent, context), Gerbang.WAJAH),
				new Gerbang(parent, new SosokPropLayer(parent), Gerbang.PROP));
	}

	// ================================================================ skins (C40.K1)

	/** The look of a body (indexes of every part and its 16 slot colours), or null when 40K failed. */
	static @Nullable Object rupa(Sosok sosok, Gaya gaya, SosokRender.RupaMasukan masukan) {
		if (kulitRusak) {
			return null;
		}
		try {
			return RupaResolver.dari(sosok, gaya, masukan);
		} catch (RuntimeException e) {
			kulitRusak = rusak("rupa", e);
			return null;
		}
	}

	/** The composed atlas of a look (null = not composed yet; asking may compose it or queue it, 40K's choice). */
	static @Nullable Identifier atlas(@Nullable Object rupa) {
		if (kulitRusak || !(rupa instanceof SosokRupa r)) {
			return null;
		}
		try {
			return KulitCache.atlas(r);
		} catch (RuntimeException e) {
			kulitRusak = rusak("atlas", e);
			return null;
		}
	}

	/** True when the atlas of a look is in the cache now (no composing). */
	static boolean atlasAda(@Nullable Object rupa) {
		return !kulitRusak && rupa instanceof SosokRupa r && KulitCache.ada(r);
	}

	/** One of the 24 ready fallback atlases {@code textures/sosok/cadangan/<kelamin>_<usia>_<kelas>.png}. */
	static @Nullable Identifier cadangan(int kelamin, int usia, String kelas) {
		try {
			return KulitCadangan.untuk(kelamin, usia, kelas);
		} catch (RuntimeException e) {
			rusak("cadangan", e);
			return null;
		}
	}

	/** Releases every atlas texture (world closed, {@code /sosok muat_ulang}). */
	static void lepasSemua() {
		try {
			KulitCache.lepasSemua();
		} catch (RuntimeException e) {
			rusak("lepasSemua", e);
		}
		kulitRusak = false;
	}

	/** Atlases alive in the cache (for {@code /sosok info}). */
	static int jumlahAtlas() {
		try {
			return KulitCache.jumlah();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Composes a look into a fresh 128 × 128 image (the caller closes it); {@code /sosok dump benih}. */
	static @Nullable NativeImage susun(@Nullable Object rupa) {
		if (!(rupa instanceof SosokRupa r)) {
			return null;
		}
		try {
			return KulitPenyusun.susun(r);
		} catch (RuntimeException e) {
			rusak("susun", e);
			return null;
		}
	}

	// ================================================================ old gear (C40.K2, 40.4.5)

	/** The KotaGear parts a Sosok body hides for gear {@code kind} (its hair and beard come from Sosok); never null. */
	public static String[] sembunyi(int kind) {
		try {
			String[] names = SosokGear.SEMBUNYI.get(kind);
			return names == null ? new String[0] : names;
		} catch (RuntimeException e) {
			rusak("sembunyi", e);
			return new String[0];
		}
	}

	/**
	 * True when a Dimension D {@code Headwear} piece is the Sosok body's own (40.4.5: the ghost hair and glowing eyes are
	 * painted by the {@code d.arwah} look), so the old piece is not drawn on a Sosok skin.
	 */
	public static boolean headwearMilikSosok(int kind) {
		if (kind <= 0) {
			return false;
		}
		try {
			String[] names = SosokGear.sembunyiHeadwear(kind);
			return names.length == 1 && "*".equals(names[0]);
		} catch (RuntimeException e) {
			rusak("sembunyiHeadwear", e);
			return false;
		}
	}

	// ================================================================ animation (C40.G1, C40.G2, 40.5.1)

	/**
	 * 40G's per-frame evaluation (layers 4-9, walk profile, springs, face, gaze, props) after the cast/duel extract.
	 * {@code kunciRupa} is the look key of a client-made body (D people, rogues, the envoy, spectators); null lets 40G take
	 * it from the entity (disciples and town NPCs, whose stance keys 40G derives itself).
	 */
	static void evaluasi(LivingEntity entity, DiscipleRenderState state, float age, @Nullable String kunciRupa) {
		if (gerakRusak) {
			return;
		}
		try {
			GerakPengendali.evaluasi(entity, state, age, kunciRupa);
		} catch (RuntimeException e) {
			gerakRusak = rusak("evaluasi", e);
		}
	}

	/** BOB and SPIN of the Gerak data in {@code setupRotations} (does nothing for a state without Gerak data). */
	static void angkat(LivingEntityRenderState state, PoseStack poseStack) {
		if (gerakRusak || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		try {
			GerakApplier.angkat(avatar, poseStack);
		} catch (RuntimeException e) {
			gerakRusak = rusak("angkat", e);
		}
	}

	/** Clip 13 {@code tidur}: 40G's 90-degree turn after {@code super.setupRotations}. */
	static void putarTidur(LivingEntityRenderState state, PoseStack poseStack) {
		if (gerakRusak || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		try {
			GerakApplier.putarTidur(avatar, poseStack);
		} catch (RuntimeException e) {
			gerakRusak = rusak("putarTidur", e);
		}
	}

	/** {@code /sosok muat_ulang}: 40G forgets every body's animation state and its prop caches. */
	static void muatUlangGerak() {
		try {
			GerakKlien.muatUlang();
		} catch (RuntimeException e) {
			rusak("muatUlang", e);
		}
		gerakRusak = false;
		lapisanRusak = false;
	}

	/** Bodies with a live 40G state (for {@code /sosok info}). */
	static int jumlahKeadaanGerak() {
		try {
			return GerakPengendali.jumlahKeadaan();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	/** Clips in 40G's catalogue once it is built (for {@code /sosok info}). */
	static int jumlahKlip() {
		try {
			return GerakKatalog.jumlah();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	static boolean gerakRusak() {
		return gerakRusak || lapisanRusak;
	}

	static boolean kulitRusak() {
		return kulitRusak;
	}

	private static boolean rusak(String where, RuntimeException e) {
		Kultivator.LOGGER.error("[Sosok] {} failed, part turned off for this session", where, e);
		return true;
	}

	/**
	 * A 40K/40G layer behind the 40.6.1 rules: busana at PENUH and KURANG on Sosok skins (in the shadow pass only above
	 * Rendah), the face at PENUH outside the shadow pass, props on any Gerak body at PENUH and KURANG (in the shadow pass
	 * only on Tinggi). Nothing for an invisible body. An exception inside the layer turns all three off for the session.
	 */
	static final class Gerbang extends RenderLayer<DiscipleRenderState, PlayerModel> {

		static final int BUSANA = 0;
		static final int WAJAH = 1;
		static final int PROP = 2;

		private final RenderLayer<DiscipleRenderState, PlayerModel> inner;
		private final int jenis;

		Gerbang(RenderLayerParent<DiscipleRenderState, PlayerModel> parent, RenderLayer<DiscipleRenderState, PlayerModel> inner, int jenis) {
			super(parent);
			this.inner = inner;
			this.jenis = jenis;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
			if (lapisanRusak || state.isInvisible) {
				return;
			}
			SosokRender.Keadaan k = SosokRender.keadaan(state);
			if (k == null || !boleh(k)) {
				return;
			}
			try {
				inner.submit(poseStack, collector, lightCoords, state, yRot, xRot);
			} catch (RuntimeException e) {
				lapisanRusak = rusak("layer " + jenis, e);
			}
		}

		private boolean boleh(SosokRender.Keadaan k) {
			boolean shadow = ShaderCompat.shadowPass();
			SosokDetail.Tingkat t = SosokDetail.tingkat();
			return switch (jenis) {
				case BUSANA -> k.kulit && (k.lod == SosokDetail.Lod.PENUH || k.lod == SosokDetail.Lod.KURANG)
						&& (!shadow || t != SosokDetail.Tingkat.RENDAH);
				case WAJAH -> k.kulit && k.lod == SosokDetail.Lod.PENUH && !shadow;
				default -> k.gerak && (k.lod == SosokDetail.Lod.PENUH || k.lod == SosokDetail.Lod.KURANG)
						&& (!shadow || t == SosokDetail.Tingkat.TINGGI);
			};
		}
	}

	private SosokJembatan() {
	}
}
