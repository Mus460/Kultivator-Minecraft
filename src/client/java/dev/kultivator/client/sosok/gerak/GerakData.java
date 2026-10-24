package dev.kultivator.client.sosok.gerak;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

/**
 * What the Gerak controller decided for one body this frame (DESIGN 40.5.1, contract C40.G2), carried in the render
 * state under {@link #KUNCI} ({@code kultivator:gerak}) from the extraction to the model setup and the layers. Read by
 * {@code GerakApplier} (extra channels, additive offsets, the seated clip's arms), {@code SosokPropLayer} (props), 40K's
 * {@code SosokBusanaModel}/{@code WajahModel} (springs, skirt panels, face strips) and 40R's renderers
 * ({@code setupRotations}: bob, spin, sleep). A render state without it (players, mirror shadows, heart demons) is never
 * touched: <b>players are never animated by Gerak</b>.
 * <p>
 * One instance per body, reused every frame (no allocation on the hot path); fields are plain and public for speed.
 * Units: angles in radians, {@link #bob} and {@link #bahu} in model pixels, {@link #spin} in degrees.
 */
public final class GerakData {

	/** The render-state data key (C40.G2). */
	public static final RenderStateDataKey<GerakData> KUNCI = RenderStateDataKey.create(() -> "kultivator:gerak");

	/** The data of a render state, or null when Gerak does not drive it. */
	public static @Nullable GerakData dari(EntityRenderState state) {
		return ((FabricRenderState) state).getData(KUNCI);
	}

	// ------------------------------------------------------------------ extra channels (applied by GerakApplier)

	/** Head roll (HZ), body roll about the hips (BZ), radians. */
	public float hz, bz;
	/** Whole-body bob in model pixels (+ up), applied in setupRotations. */
	public float bob;
	/** Extra yaw in degrees (the spinning dance 87), accumulated; applied in setupRotations. */
	public float spin;
	/** Shoulder raise in pixels. */
	public float bahu;
	/** Breath amplitude multiplier used this frame (1 = normal). */
	public float napas = 1.0F;
	/** Additive offsets on channels 0-17 (head look, breath, glances, idle gestures), radians / blocks. */
	public final float[] tambah = new float[Klip.KANAL_POSE];
	/**
	 * The arms and head of a seated clip while the meditation seat holds the body (PoseApplier skips the snapshot
	 * then): values and weights of channels 0-17.
	 */
	public final float[] duduk = new float[Klip.KANAL_POSE];
	public final float[] dudukW = new float[Klip.KANAL_POSE];

	// ------------------------------------------------------------------ walk

	/** Arm swing multiplier relative to the leg swing (the profile's arm x / leg x). */
	public float ayunLengan = 1.0F;
	/** Right leg swing multiplier (the limp, 0.4). */
	public float ayunKakiKanan = 1.0F;
	/** The walk profile used ({@link ProfilJalan} ordinal). */
	public int profil;
	/** True when the arms are driven by a pose this frame (no swing rescale then). */
	public boolean lenganDipose;
	/** The shared locomotion polish's state (PENUH and KURANG bodies), or null. */
	public Langkah.Keadaan langkah;

	// ------------------------------------------------------------------ face (40K WajahModel)

	/** Eye strip 0-15 ({@code Wajah.MATA_*}). */
	public int mata;
	/** Mouth strip 0-15 ({@code Wajah.MULUT_*}). */
	public int mulut;
	/** Glance -1..1 (x: + to the wearer's left, y: + up). */
	public float lirikX, lirikY;

	// ------------------------------------------------------------------ springs and skirt (40K SosokBusanaModel)

	/** Spring angles per {@link Pegas.Rantai} ordinal, radians (x pitch, z roll). */
	public final float[] pegasX = new float[Pegas.RANTAI];
	public final float[] pegasZ = new float[Pegas.RANTAI];
	/** False beyond the spring budget or LOD: the values are the rest targets. */
	public boolean pegasAktif;
	/** Skirt panels: front/back pitch, right/left roll (radians), front/back height scale. */
	public float rokDepanX, rokBelakangX, rokKananZ, rokKiriZ;
	public float rokDepanSkalaY = 1.0F, rokBelakangSkalaY = 1.0F;

	// ------------------------------------------------------------------ props (SosokPropLayer)

	public int propKanan, propKiri, propPunggung;
	/** Props at other points (from the clip); null for none. */
	public Klip.PropPasang[] pasang;
	/** Props always worn by this body (life path: trophy, abacus, scroll, flags, the Qiankun pouch); null for none. */
	public Klip.PropPasang[] pasangTetap;
	/** Props are hidden (fighting, casting, far LOD, seated hands on knees...). */
	public boolean propSembunyi;
	/** The legacy duty prop is drawn by DiscipleHeldItemLayer (do not draw ours). */
	public boolean propLama;

	// ------------------------------------------------------------------ state

	/** The clip that drives the body most this frame (0 none) and its variant and clip time. */
	public int klip, varian;
	public float tKlip;
	/** Clip 13 (or a BERBARING clip) at more than half weight: the renderer lays the body down. */
	public boolean tidur;
	/** 0..1 how far the body lies down. */
	public float tidurBobot;
	/** Floating (immortals 0.02, ghosts): extra lift in blocks. */
	public float melayang;
	/** {@code SosokDetail.Lod} ordinal (0 PENUH, 1 KURANG, 2 SILUET, 3 JAUH). */
	public int lod;
	/** Trait condition bits ({@code Klip.SY_*}) and trait set bits (1 << Sifat ordinal) of the body. */
	public int ciri, sifatBit;
	/** Age variants: amplitude and time scale of work clips. */
	public float skalaAmp = 1.0F, skalaWaktu = 1.0F;
	/** True while the disciple's activity pose (layer 5, DutyPoses) owns the body. */
	public boolean aktivitas;

	void kosongkan() {
		hz = 0.0F;
		bz = 0.0F;
		bob = 0.0F;
		bahu = 0.0F;
		napas = 1.0F;
		java.util.Arrays.fill(tambah, 0.0F);
		java.util.Arrays.fill(dudukW, 0.0F);
		ayunLengan = 1.0F;
		ayunKakiKanan = 1.0F;
		lenganDipose = false;
		langkah = null;
		lirikX = 0.0F;
		lirikY = 0.0F;
		pegasAktif = false;
		pasang = null;
		pasangTetap = null;
		propSembunyi = false;
		propLama = false;
		propKanan = 0;
		propKiri = 0;
		propPunggung = 0;
		klip = 0;
		varian = 0;
		tKlip = 0.0F;
		tidur = false;
		tidurBobot = 0.0F;
		melayang = 0.0F;
		aktivitas = false;
	}
}
