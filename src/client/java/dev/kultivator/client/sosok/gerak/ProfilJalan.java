package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.disciple.DiscipleRenderState;
import net.minecraft.util.Mth;

/**
 * Walk profiles (DESIGN 40.5.6): the vanilla walk is changed, not replaced. Before the model reads the render state the
 * rhythm ({@code walkAnimationPos}) and leg amplitude ({@code walkAnimationSpeed}) are scaled; the arm swing gets its own
 * multiplier ({@link GerakData#ayunLengan}, applied by {@code GerakApplier}); and per step (phase
 * {@code phi = walkAnimationPos x 0.6662}, the vanilla formula) the hips sway (BZ), the head counter-turns, the body bobs
 * and leans. Specials: the old man's stick taps in step, children skip every second step, dancers walk on their toes
 * with lifted arms, ghosts hover. Running ({@code walkAnimationSpeed > 0.9}) and the limp override the profile.
 */
public enum ProfilJalan {
	//          irama lengan kaki  bz   hy    bob   lean  hx
	GAGAH(1.00F, 1.00F, 1.00F, 1.5F, 0.0F, 0.25F, 2.0F, -2.0F),
	ANGGUN(1.12F, 0.55F, 0.80F, 3.5F, -1.5F, 0.15F, 0.0F, 0.0F),
	SANTAI(0.92F, 0.85F, 0.90F, 2.0F, 0.0F, 0.20F, 3.0F, 0.0F),
	TERGESA(1.25F, 1.30F, 1.10F, 1.0F, 0.0F, 0.35F, 8.0F, 0.0F),
	MILITER(1.00F, 0.70F, 1.05F, 0.0F, 0.0F, 0.15F, 0.0F, -3.0F),
	BANGSAWAN(0.85F, 0.25F, 0.80F, 1.0F, 0.0F, 0.10F, -2.0F, 0.0F),
	PEDAGANG(1.15F, 0.70F, 0.85F, 1.5F, 0.0F, 0.25F, 6.0F, 0.0F),
	RAHIB(0.85F, 0.20F, 0.75F, 0.5F, 0.0F, 0.10F, 4.0F, 8.0F),
	KULI(0.90F, 0.60F, 1.00F, 1.0F, 0.0F, 0.45F, 10.0F, 0.0F),
	TUA_TONGKAT(0.75F, 0.40F, 0.60F, 1.0F, 0.0F, 0.10F, 18.0F, 0.0F),
	TUA(0.80F, 0.45F, 0.65F, 1.0F, 0.0F, 0.10F, 15.0F, 0.0F),
	ANAK_LOMPAT(1.35F, 1.25F, 1.15F, 2.0F, 0.0F, 0.60F, 4.0F, 0.0F),
	PENARI(1.20F, 0.40F, 0.85F, 4.0F, -2.0F, 0.20F, 0.0F, 0.0F),
	IBLIS(0.95F, 0.50F, 0.95F, 1.0F, 0.0F, 0.15F, 10.0F, 6.0F),
	ARWAH(0.60F, 0.15F, 0.10F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F),
	/** The limp of clip 146 / wound 1 (right leg swing x0.4, a hip drop every step). */
	PINCANG(0.85F, 0.80F, 0.85F, 6.0F, 0.0F, 0.20F, 6.0F, 8.0F);

	final float irama;
	final float lengan;
	final float kaki;
	final float bz;
	final float hy;
	final float bob;
	final float lean;
	final float hx;

	ProfilJalan(float irama, float lengan, float kaki, float bz, float hy, float bob, float lean, float hx) {
		this.irama = irama;
		this.lengan = lengan;
		this.kaki = kaki;
		this.bz = bz;
		this.hy = hy;
		this.bob = bob;
		this.lean = lean;
		this.hx = hx;
	}

	/** The id used by the look tables and the {@code jalan} test command. */
	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The profile of a table id ({@code gagah}, {@code tua_tongkat}...), or null. */
	public static ProfilJalan dariId(String id) {
		for (ProfilJalan p : values()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}

	private static final float D = Mth.DEG_TO_RAD;

	/**
	 * Applies the profile to the render state's walk and writes its channels as the lowest layer (9) into
	 * {@code target}/{@code bobot}; returns true on the step where the old man's stick touches the ground.
	 */
	boolean terapkan(DiscipleRenderState st, GerakKeadaan k, GerakData d, float[] target, float[] bobot, boolean perempuan,
			float age) {
		float speed = st.walkAnimationSpeed;
		boolean lari = speed > 0.9F;
		float irama = this.irama;
		float lengan = this.lengan;
		float kaki = this.kaki;
		float bobAmp = this.bob;
		float leanV = this.lean;
		if (lari && this != ARWAH) {
			irama = 1.0F;
			lengan = perempuan ? 1.08F : 1.2F;
			kaki = 1.0F;
			bobAmp = 0.5F;
			leanV = 12.0F;
		} else if (this == MILITER && perempuan) {
			irama = 1.05F;
		}
		st.walkAnimationPos *= irama;
		st.walkAnimationSpeed = speed * kaki;
		d.ayunLengan = kaki <= 0.01F ? 0.0F : lengan / kaki;
		d.ayunKakiKanan = this == PINCANG ? 0.4F : 1.0F;
		d.profil = ordinal();
		float w = Mth.clamp(speed / 0.3F, 0.0F, 1.0F);
		float phi = st.walkAnimationPos * 0.6662F;
		float s = Mth.sin(phi);
		boolean ketuk = false;
		if (this == ARWAH) {
			// hovering a quarter block, rising and falling slowly; the robe hem sways on its springs
			tulis(target, bobot, Klip.LIFT, 0.25F + 0.04F * Mth.sin(age * Mth.TWO_PI / 60.0F), 1.0F);
			tulis(target, bobot, Klip.BZ, 2.0F * D * Mth.sin(age * 0.05F), 1.0F);
			return false;
		}
		if (w <= 0.0F) {
			k.langkahSinLalu = s;
			return false;
		}
		tulis(target, bobot, Klip.BZ, bz * D * s, w);
		if (hy != 0.0F) {
			tulis(target, bobot, Klip.HY, hy * D * s, w);
		}
		tulis(target, bobot, Klip.BOB, bobAmp * Math.abs(s), w);
		if (leanV != 0.0F) {
			tulis(target, bobot, Klip.LEAN, leanV * D, w);
		}
		if (hx != 0.0F && !lari) {
			tulis(target, bobot, Klip.HX, hx * D, w);
		}
		switch (this) {
			case ANGGUN -> {
				// hands a little in front, feet falling toward the middle line
				tulis(target, bobot, Klip.RAY, -8.0F * D, w);
				tulis(target, bobot, Klip.LAY, 8.0F * D, w);
				tulis(target, bobot, Klip.RLY, 4.0F * D, w);
				tulis(target, bobot, Klip.LLY, -4.0F * D, w);
			}
			case TUA_TONGKAT -> {
				// the stick in the right hand swings with the step and taps when the phase rises through zero
				tulis(target, bobot, Klip.RAX, (-35.0F + 12.0F * s) * D, w);
				tulis(target, bobot, Klip.RAZ, 6.0F * D, w);
				ketuk = k.langkahSinLalu < 0.0F && s >= 0.0F;
			}
			case TUA -> {
				tulis(target, bobot, Klip.RAX, 28.0F * D, w);
				tulis(target, bobot, Klip.RAZ, -10.0F * D, w);
				tulis(target, bobot, Klip.LAX, 28.0F * D, w);
				tulis(target, bobot, Klip.LAZ, 10.0F * D, w);
			}
			case BANGSAWAN -> {
				// hands clasped behind the back
				tulis(target, bobot, Klip.RAX, 28.6F * D, w * 0.9F);
				tulis(target, bobot, Klip.RAZ, -12.6F * D, w * 0.9F);
				tulis(target, bobot, Klip.LAX, 28.6F * D, w * 0.9F);
				tulis(target, bobot, Klip.LAZ, 12.6F * D, w * 0.9F);
			}
			case RAHIB -> {
				// palms together before the chest (the prayer stance walks)
				tulis(target, bobot, Klip.RAX, -70.0F * D, w);
				tulis(target, bobot, Klip.RAY, -38.0F * D, w);
				tulis(target, bobot, Klip.LAX, -70.0F * D, w);
				tulis(target, bobot, Klip.LAY, 38.0F * D, w);
			}
			case ANAK_LOMPAT -> {
				// a small skip on every second step
				float half = Mth.cos(phi * 0.5F);
				if (half > 0.0F) {
					tulis(target, bobot, Klip.LIFT, 0.06F * Math.max(0.0F, s), w);
				}
			}
			case PENARI -> {
				// arms lifted softly, walking on the toes
				tulis(target, bobot, Klip.RAX, -30.0F * D, w);
				tulis(target, bobot, Klip.RAZ, 10.0F * D, w);
				tulis(target, bobot, Klip.LAX, -30.0F * D, w);
				tulis(target, bobot, Klip.LAZ, -10.0F * D, w);
			}
			default -> {
			}
		}
		k.langkahSinLalu = s;
		return ketuk;
	}

	private static void tulis(float[] target, float[] bobot, int c, float v, float w) {
		target[c] = v;
		bobot[c] = w;
	}
}
