package dev.kultivator.client.epic.sky;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.epic.EpicColors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sky grades of the epic moments (DESIGN 23.9). One probe mixin with a whitelist: {@code SKY_COLOR}, {@code FOG_COLOR},
 * {@code CLOUD_COLOR}, {@code CLOUD_HEIGHT} (protagonist and viewers within 48 b, Overworld), {@code SUNRISE_SUNSET_COLOR},
 * {@code STAR_BRIGHTNESS}, {@code SKY_LIGHT_FACTOR} (never below half of vanilla) together with {@code SKY_LIGHT_COLOR} and
 * {@code AMBIENT_LIGHT_COLOR} (BadOptimizations only updates the lightmap when they move), and the music attributes of
 * {@link MusicCue}. Fog distances, sky light level, day time, rain and thunder are never touched; no server code reads
 * this probe. One grade at a time (the higher scene priority wins), blends take 1 s (2 s under "Kurangi gerak"), "Langit
 * berubah" gates it all, and with no grade active the mixin returns the vanilla value at once.
 * <p>
 * Shader-pack profile (23.9.4): the HUD sky layer is the primary channel ({@link #hudTop}, {@link #hudTint}); in the
 * vanilla profile at Normal+ only a top gradient (≤ 0.35) is added for dark grades at D ≥ 0.5; Minimal always uses it.
 */
public final class SkyDirector {

	/** A grade: sky / fog / cloud colour, stars, light factor (1 = vanilla), whether it tints the HUD, whether it has a sky. */
	public enum Grade {
		SK_TRIB(EpicColors.TRIB_SKY, EpicColors.TRIB_FOG, EpicColors.TRIB_CLOUD, 0.15F, -1.0F, true, true),
		SK_NETHER(EpicColors.NETHER_FOG, EpicColors.NETHER_FOG, EpicColors.NETHER_FOG, 0.0F, 1.0F, false, false),
		SK_VOID(EpicColors.VOID_FOG, EpicColors.VOID_FOG, EpicColors.VOID_FOG, 0.0F, 1.0F, false, false),
		SK_GOLD(EpicColors.GOLD_SKY, EpicColors.GOLD_FOG, EpicColors.GOLD_CLOUD, 0.0F, 1.0F, true, true),
		SK_WHITE(EpicColors.WHITE_SKY, EpicColors.WHITE_FOG, EpicColors.WHITE_FOG, 0.0F, 1.0F, false, true),
		SK_RED(EpicColors.RED_SKY, EpicColors.RED_FOG, EpicColors.RED_CLOUD, 0.2F, 0.6F, true, true),
		SK_ASH(EpicColors.ASH_SKY, EpicColors.ASH_FOG, EpicColors.ASH_FOG, 0.0F, 0.7F, true, true),
		SK_ASH_RED(EpicColors.ASH_RED_SKY, EpicColors.ASH_FOG, EpicColors.ASH_FOG, 0.0F, 0.7F, true, true),
		SK_STARS(EpicColors.STARS_SKY, EpicColors.STARS_FOG, EpicColors.STARS_FOG, 1.0F, 0.55F, true, true),
		SK_JADE(EpicColors.JADE_TINT, EpicColors.JADE_TINT, EpicColors.JADE_CLOUD, 0.0F, 1.0F, false, true),
		SK_DAWN(0xFFC89A, 0xFFC89A, 0xFFE6CC, 0.0F, 1.0F, false, true),
		SK_DAO(0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0.0F, 1.0F, false, true),
		SK_ICE(EpicColors.ICE_TINT, EpicColors.ICE_TINT, 0xF0FAFF, 0.0F, 1.0F, false, true);

		final int sky;
		final int fog;
		final int cloud;
		final float stars;
		final float light;
		final boolean tinted;
		final boolean hasSky;

		Grade(int sky, int fog, int cloud, float stars, float light, boolean tinted, boolean hasSky) {
			this.sky = sky;
			this.fog = fog;
			this.cloud = cloud;
			this.stars = stars;
			this.light = light;
			this.tinted = tinted;
			this.hasSky = hasSky;
		}

		/** The highest blend weight of the tint-only grades (23.9.2). */
		float cap() {
			return switch (this) {
				case SK_JADE -> 0.3F;
				case SK_DAO -> 0.35F;
				case SK_ICE -> 0.4F;
				default -> 1.0F;
			};
		}
	}

	private static final float NEAR = 48.0F;
	private static final float RANGE = 192.0F;

	private static boolean any;
	private static Grade grade;
	private static int priority = -1;
	private static int custom = 0xFFFFFF;
	private static Vec3 center = Vec3.ZERO;
	private static boolean own;
	private static float othersScale = 1.0F;
	private static double subjectY;
	// eased state
	private static float w;
	private static float wTarget;
	private static float d;
	private static float dTarget;
	private static float rate = 0.05F;
	private static final float[] SKY = {1.0F, 1.0F, 1.0F};
	private static final float[] FOG = {1.0F, 1.0F, 1.0F};
	private static final float[] CLOUD = {1.0F, 1.0F, 1.0F};
	private static float stars;
	private static float light = 1.0F;
	private static float falloff = 1.0F;
	private static boolean near;
	private static int holdTicks = -1;
	private static float releaseRate;
	private static long ticks;
	/** The scene whose tick set the grade (the director's context), so its end can release a grade nobody released. */
	private static Object owner;
	private static Object context;
	private static int sinceGrade;
	/** A grade with no new grade call for this long fades out (5 min; a safety net, scenes release their own). */
	private static final int WATCHDOG = 6000;
	// per-call outputs (no allocation per call)
	private static final Vector3f OUT_SKY = new Vector3f();
	private static final Vector3f OUT_FOG = new Vector3f();
	private static final Vector4f OUT_CLOUD = new Vector4f();
	private static final Vector4f OUT_SUN = new Vector4f();
	private static final Vector3f OUT_SKY_LIGHT = new Vector3f();
	private static final Vector3f OUT_AMBIENT = new Vector3f();

	/**
	 * Moves to {@code target} at intensity {@code intensity} with darkness {@code darkness} over {@code blendTicks} (≥ 1 s,
	 * 2 s under "Kurangi gerak"). A lower priority than the grade alive is dropped. {@code own} = the protagonist's scene;
	 * {@code subjectY} for the cloud base.
	 */
	public static void grade(Grade target, float intensity, float darkness, int blendTicks, int prio, Vec3 at, boolean ownScene, float others,
							 double subject) {
		if (!EpicSettings.sky()) {
			return;
		}
		if (any && prio < priority && w > 0.02F) {
			return;
		}
		int blend = Math.max(20, blendTicks);
		if (EffectQuality.reduceMotion()) {
			blend = Math.max(40, blend);
		}
		if (!any) {
			// Start from the vanilla look: colours already at the target, weight from 0.
			copy(SKY, target.sky);
			copy(FOG, target.fog);
			copy(CLOUD, target.cloud);
			stars = 0.0F;
			light = 1.0F;
			w = 0.0F;
			d = 0.0F;
		}
		grade = target;
		priority = prio;
		center = at;
		own = ownScene;
		othersScale = others;
		subjectY = subject;
		wTarget = Mth.clamp(intensity, 0.0F, target.cap());
		dTarget = Mth.clamp(darkness, 0.0F, 1.0F);
		rate = 1.0F / blend;
		holdTicks = -1;
		releaseRate = 0.0F;
		owner = context;
		sinceGrade = 0;
		any = true;
	}

	/** Set by the director around each scene tick: grades started inside it belong to that scene. */
	public static void context(Object scene) {
		context = scene;
	}

	/**
	 * A scene ended: when it set the grade alive and nothing scheduled its release, fade the grade out (an END beat that
	 * never came, a stop, a dropped cue). A release already running keeps its own hold and fade.
	 */
	public static void sceneEnded(Object scene) {
		if (any && owner != null && owner == scene && holdTicks < 0) {
			holdTicks = 0;
			releaseRate = 1.0F / 40.0F;
		}
	}

	/** Fades out a grade of the protagonist's own scenes at once (death); others' grades keep running. */
	public static void releaseOwn(int ticks) {
		if (any && own) {
			holdTicks = 0;
			releaseRate = 1.0F / Math.max(1, ticks);
		}
	}

	/** The Dao grade with its colour. */
	public static void dao(int rgb, float intensity, int blendTicks, int prio, Vec3 at, boolean ownScene, float others) {
		custom = rgb & 0xFFFFFF;
		grade(Grade.SK_DAO, intensity, 0.0F, blendTicks, prio, at, ownScene, others, at.y);
	}

	/** Keeps the grade {@code hold} ticks, then fades it over {@code release} ticks. */
	public static void holdThenRelease(int hold, int release, int prio) {
		if (!any || prio < priority) {
			return;
		}
		holdTicks = Math.max(0, hold);
		releaseRate = 1.0F / Math.max(20, release);
	}

	/** Fades the grade out over {@code ticks} when {@code prio} owns it. */
	public static void release(int ticks, int prio) {
		holdThenRelease(0, ticks, prio);
	}

	/** Ends every grade at once (disconnect, level change, stop). */
	public static void clear() {
		any = false;
		grade = null;
		priority = -1;
		owner = null;
		holdTicks = -1;
		releaseRate = 0.0F;
		w = 0.0F;
		wTarget = 0.0F;
	}

	public static boolean active() {
		return any;
	}

	public static Grade current() {
		return grade;
	}

	/** Once per client tick. */
	public static void tick() {
		if (!any) {
			return;
		}
		ticks++;
		if (!EpicSettings.sky()) {
			clear();
			return;
		}
		if (++sinceGrade > WATCHDOG && holdTicks < 0) {
			holdTicks = 0;
			releaseRate = 1.0F / 40.0F;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		double distance = Math.sqrt(camera.distanceToSqr(center.x, camera.y, center.z));
		falloff = own ? 1.0F : distance <= NEAR ? 1.0F : (float) Math.max(0.0D, 1.0D - (distance - NEAR) / (RANGE - NEAR));
		near = own || distance <= NEAR;
		if (holdTicks == 0) {
			wTarget = Math.max(0.0F, wTarget - releaseRate);
			if (wTarget <= 0.0F && w <= 0.01F) {
				clear();
				return;
			}
		} else if (holdTicks > 0) {
			holdTicks--;
		}
		float step = rate;
		w += Mth.clamp(wTarget - w, -Math.max(step, releaseRate), step);
		d += Mth.clamp(dTarget - d, -step, step);
		int skyRgb = grade == Grade.SK_DAO ? custom : grade.sky;
		int fogRgb = grade == Grade.SK_DAO ? custom : grade.fog;
		int cloudRgb = grade == Grade.SK_DAO ? custom : grade.cloud;
		if (grade == Grade.SK_WHITE && !EffectQuality.reduceMotion()) {
			// ±10° hue drift: a slow sway between a warm and a cool white.
			float sway = Mth.sin(ticks * 0.03F);
			skyRgb = sway > 0 ? mix(0xFFFFFF, 0xFFF0E0, sway) : mix(0xFFFFFF, 0xE8F2FF, -sway);
		}
		ease(SKY, skyRgb, step * 2.0F);
		ease(FOG, fogRgb, step * 2.0F);
		ease(CLOUD, cloudRgb, step * 2.0F);
		stars += Mth.clamp(grade.stars - stars, -step, step);
		float targetLight = grade.light < 0.0F ? 1.0F - 0.6F * d : grade.light;
		light += Mth.clamp(Math.max(0.5F, targetLight) - light, -step, step);
	}

	/** The effective blend weight for this viewer (× falloff × others' scale). */
	public static float weight() {
		return any ? w * falloff * (own ? 1.0F : othersScale) : 0.0F;
	}

	public static float darkness() {
		return any ? d : 0.0F;
	}

	/** True while a sky grade replaces the Kehancuran red sky ({@code RedSkyHud} then draws nothing). */
	public static boolean coversRedSky() {
		return any && (grade == Grade.SK_RED) && weight() > 0.05F && EpicSettings.sky();
	}

	/**
	 * Called at the RETURN of {@code EnvironmentAttributeProbe.getValue}; may replace the value through {@code cir}.
	 * Returns at once on one boolean while no grade or music cue is alive.
	 */
	public static void override(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> cir) {
		if (!any && !MusicCue.active()) {
			return;
		}
		if (attribute == EnvironmentAttributes.BACKGROUND_MUSIC) {
			BackgroundMusic music = MusicCue.background();
			if (music != null) {
				cir.setReturnValue(music);
			}
			return;
		}
		if (attribute == EnvironmentAttributes.MUSIC_VOLUME) {
			if (MusicCue.active() && cir.getReturnValue() instanceof Float volume) {
				cir.setReturnValue(volume * MusicCue.volume());
			}
			return;
		}
		if (!any) {
			return;
		}
		float weight = weight();
		if (weight <= 0.001F) {
			return;
		}
		Object value = cir.getReturnValue();
		if (attribute == EnvironmentAttributes.SKY_COLOR) {
			if (grade.hasSky && value instanceof Vector3fc vanilla) {
				float dark = 1.0F - 0.5F * d;
				cir.setReturnValue(lerp(OUT_SKY, vanilla, SKY, dark, weight));
			}
		} else if (attribute == EnvironmentAttributes.FOG_COLOR) {
			if (value instanceof Vector3fc vanilla) {
				float dark = 1.0F - 0.5F * d;
				cir.setReturnValue(lerp(OUT_FOG, vanilla, FOG, dark, weight));
			}
		} else if (attribute == EnvironmentAttributes.CLOUD_COLOR) {
			if (value instanceof Vector4fc vanilla) {
				float dark = 1.0F - 0.4F * d;
				OUT_CLOUD.set(Mth.lerp(weight, vanilla.x(), CLOUD[0] * dark), Mth.lerp(weight, vanilla.y(), CLOUD[1] * dark),
						Mth.lerp(weight, vanilla.z(), CLOUD[2] * dark), vanilla.w());
				cir.setReturnValue(OUT_CLOUD);
			}
		} else if (attribute == EnvironmentAttributes.CLOUD_HEIGHT) {
			if (near && grade == Grade.SK_TRIB && value instanceof Float height) {
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD) {
					double cameraY = minecraft.gameRenderer.mainCamera().position().y;
					float target = (float) Math.max(subjectY + 38.0D, cameraY + 24.0D);
					if (target < height) {
						float lowered = Mth.lerp(weight, height, target);
						cir.setReturnValue(Math.round(lowered * 2.0F) / 2.0F);
					}
				}
			}
		} else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
			if (value instanceof Vector4fc vanilla) {
				float boost = grade == Grade.SK_DAWN ? 1.0F + 0.4F * weight : 1.0F - d * weight;
				OUT_SUN.set(vanilla.x(), vanilla.y(), vanilla.z(), Mth.clamp(vanilla.w() * boost, 0.0F, 1.0F));
				cir.setReturnValue(OUT_SUN);
			}
		} else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
			if (value instanceof Float vanilla) {
				cir.setReturnValue(Math.max(vanilla, Mth.clamp(stars * weight, 0.0F, 1.0F)));
			}
		} else if (attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR) {
			if (near && value instanceof Float vanilla && light < 0.999F) {
				float factor = Mth.lerp(weight, 1.0F, Math.max(0.5F, light));
				cir.setReturnValue(vanilla * factor);
			}
		} else if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR) {
			if (near && value instanceof Vector3fc vanilla && light < 0.999F) {
				cir.setReturnValue(lerp(OUT_SKY_LIGHT, vanilla, SKY, 1.0F, weight * 0.3F));
			}
		} else if (attribute == EnvironmentAttributes.AMBIENT_LIGHT_COLOR) {
			if (near && value instanceof Vector3fc vanilla && light < 0.999F) {
				cir.setReturnValue(lerp(OUT_AMBIENT, vanilla, SKY, 1.0F, weight * 0.2F));
			}
		}
	}

	// ================================================================ HUD sky layer (23.9.4)

	/** The colour of the HUD sky layer (RGB). */
	public static int hudColor() {
		if (!any) {
			return 0;
		}
		return grade == Grade.SK_DAO ? custom : grade.sky;
	}

	/** Alpha (0..1) of the HUD top gradient now. */
	public static float hudTop() {
		if (!any || !EpicSettings.sky()) {
			return 0.0F;
		}
		float weight = weight();
		boolean dark = grade == Grade.SK_TRIB || grade == Grade.SK_STARS || grade == Grade.SK_RED || grade == Grade.SK_ASH
				|| grade == Grade.SK_ASH_RED || grade == Grade.SK_NETHER || grade == Grade.SK_VOID;
		if (!dark) {
			return 0.0F;
		}
		if (ShaderCompat.packOn() || EpicBudget.tier() <= 0) {
			return 0.55F * d * weight;
		}
		return d >= 0.5F ? Math.min(0.35F, 0.55F * d * weight) : 0.0F;
	}

	/** Alpha (0..1) of the full-screen HUD tint now (tinted grades; the pack profile and Minimal). */
	public static float hudTint() {
		if (!any || !EpicSettings.sky() || !grade.tinted) {
			return 0.0F;
		}
		if (!ShaderCompat.packOn() && EpicBudget.tier() > 0) {
			return 0.0F;
		}
		return Math.min(0.18F, 0.18F * weight());
	}

	private static Vector3f lerp(Vector3f out, Vector3fc vanilla, float[] grade, float scale, float t) {
		return out.set(Mth.lerp(t, vanilla.x(), grade[0] * scale), Mth.lerp(t, vanilla.y(), grade[1] * scale), Mth.lerp(t, vanilla.z(), grade[2] * scale));
	}

	private static void copy(float[] out, int rgb) {
		out[0] = (rgb >> 16 & 255) / 255.0F;
		out[1] = (rgb >> 8 & 255) / 255.0F;
		out[2] = (rgb & 255) / 255.0F;
	}

	private static void ease(float[] out, int rgb, float step) {
		out[0] += Mth.clamp((rgb >> 16 & 255) / 255.0F - out[0], -step, step);
		out[1] += Mth.clamp((rgb >> 8 & 255) / 255.0F - out[1], -step, step);
		out[2] += Mth.clamp((rgb & 255) / 255.0F - out[2], -step, step);
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
		int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
		int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
		return r << 16 | g << 8 | bl;
	}

	private SkyDirector() {
	}
}
