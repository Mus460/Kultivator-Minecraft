package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.fx.QiParticleOptions;
import dev.kultivator.fx.QiParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client init and the event effects of the Gerak system (DESIGN 40.5, contract C40.K3): {@link #init} (called by 40R's
 * {@code SosokClient}) builds the clip catalog, starts the reactions, sweeps the per-body state every 200 ticks and
 * forgets everything on joining or leaving a world. {@link #suara} and {@link #partikel} play the {@code SUARA} and
 * {@code PARTIKEL} events of a clip: client-only sounds and particles at the body (nothing is sent to the server), the
 * particles scaled by {@code EffectQuality} and none at Minimal (40.6.1).
 * <p>
 * Particle ids of the catalog: {@code asap} (kettle, incense, chimney smoke), {@code uap} (steam), {@code debu} (swept
 * dust at the feet), {@code tanah} (dug earth), {@code butir} (thrown feed), {@code percik} (anvil sparks), {@code glif}
 * (talisman runes), {@code kilau} (a glint), {@code darah}/{@code darah_qi} (the blood rite), {@code qi_masuk} (qi drawn
 * into the body), {@code qi_tipis} (a thin qi haze), and any vanilla simple particle by its id
 * ({@code minecraft:splash}, {@code minecraft:note}...). The colour argument picks the qi colour: -1 = the body's Dao.
 */
public final class GerakKlien {

	private static final Map<String, SoundEvent> SUARA = new HashMap<>();
	private static final Map<String, SimpleParticleType> PARTIKEL_VANILLA = new HashMap<>();
	private static final int PEMBERSIH_TICK = 200;
	private static boolean siap;
	private static int tick;
	private static @Nullable BlockParticleOption tanah;
	private static @Nullable DustParticleOptions debu;
	private static @Nullable DustParticleOptions butir;
	private static @Nullable DustParticleOptions darah;

	/** Once, from {@code SosokClient.init()} (40R). */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		GerakKatalog.pastikan();
		GerakReaksi.init();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.isPaused()) {
				return;
			}
			if (++tick % PEMBERSIH_TICK == 0) {
				GerakPengendali.bersihkan(mc.level);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> muatUlang());
		ClientDisconnect.register((handler, mc) -> mc.execute(GerakKlien::muatUlang));
	}

	/** Forgets every body's state and the prop caches (world change, {@code /sosok muat_ulang}). */
	public static void muatUlang() {
		GerakPengendali.kosongkan();
		PropKatalog.lupakan();
		SosokPropLayer.lupakan();
	}

	// ================================================================== SUARA

	/** Plays a sound of the vanilla registry at the body ({@code "block.anvil.use"} or a namespaced id); unknown ids are silent. */
	static void suara(LivingEntity e, String id, float volume, float pitch) {
		if (!(e.level() instanceof ClientLevel level) || volume <= 0.0F) {
			return;
		}
		SoundEvent s = suaraDari(id);
		if (s == null) {
			return;
		}
		level.playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(), s, SoundSource.NEUTRAL, volume,
				Mth.clamp(pitch, 0.5F, 2.0F), false);
	}

	private static @Nullable SoundEvent suaraDari(String id) {
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

	// ================================================================== PARTIKEL

	/** Spawns {@code jumlah} particles of a clip event at the body (scaled by quality, none at Minimal). */
	static void partikel(LivingEntity e, DiscipleRenderState st, String id, int jumlah, int warna) {
		if (!(e.level() instanceof ClientLevel level) || EffectQuality.minimal()) {
			return;
		}
		int n = EffectQuality.scale(Math.max(1, jumlah));
		RandomSource r = level.getRandom();
		float yaw = st.bodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw);
		double fz = Mth.cos(yaw);
		double h = e.getBbHeight();
		double x = e.getX();
		double y = e.getY();
		double z = e.getZ();
		// in front of the hands by default
		double tx = x + fx * 0.45D;
		double ty = y + h * 0.5D;
		double tz = z + fz * 0.45D;
		int dao = warna < 0 ? Wajah.warnaDao(st.dao) : warna;
		switch (id) {
			case "asap" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SMOKE, 0xD8D4CC, 0xA8A49C, 0x6E6A64, 0.22F, 0.45F,
							40 + r.nextInt(20), 0.0F, 0.035F, 0.0F, (byte) 0), tx + sebar(r, 0.15D), ty + 0.2D, tz + sebar(r, 0.15D),
							0.0D, 0.03D, 0.0D);
				}
			}
			case "uap" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(ParticleTypes.WHITE_SMOKE, tx + sebar(r, 0.12D), ty + 0.15D, tz + sebar(r, 0.12D), 0.0D, 0.04D, 0.0D);
				}
			}
			case "debu" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(debu(), x + fx * 0.7D + sebar(r, 0.3D), y + 0.05D, z + fz * 0.7D + sebar(r, 0.3D),
							sebar(r, 0.02D), 0.02D, sebar(r, 0.02D));
				}
			}
			case "tanah" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(tanah(), x + fx * 0.8D + sebar(r, 0.2D), y + 0.1D, z + fz * 0.8D + sebar(r, 0.2D),
							sebar(r, 0.08D), 0.15D, sebar(r, 0.08D));
				}
			}
			case "butir" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(butir(), tx + fx * 0.3D, ty + 0.3D, tz + fz * 0.3D, fx * 0.12D + sebar(r, 0.06D), 0.05D,
							fz * 0.12D + sebar(r, 0.06D));
				}
			}
			case "percik" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(QiParticles.QI_SPARK, 0xFFF4C0, 0xFFB040, 0xC04010, 0.06F, 0.02F,
							8 + r.nextInt(6), 0.0F, 0.0F, 0.0F, QiParticleOptions.GLOW), tx + fx * 0.25D, y + h * 0.42D, tz + fz * 0.25D,
							sebar(r, 0.18D), 0.12D + r.nextDouble() * 0.1D, sebar(r, 0.18D));
				}
			}
			case "glif" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(ParticleTypes.ENCHANT, tx + sebar(r, 0.3D), ty + 0.3D + r.nextDouble() * 0.4D, tz + sebar(r, 0.3D),
							sebar(r, 0.3D), 0.1D, sebar(r, 0.3D));
				}
			}
			case "kilau" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(0xFFFFFF, dao, dao, 0.12F, 0.0F, 14, 0.0F, 0.0F, 0.0F,
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.STAR)), tx + sebar(r, 0.2D),
							ty + 0.2D + sebar(r, 0.2D), tz + sebar(r, 0.2D), 0.0D, 0.0D, 0.0D);
				}
			}
			case "darah", "darah_qi" -> {
				boolean qi = id.equals("darah_qi");
				for (int i = 0; i < n; i++) {
					if (qi) {
						level.addParticle(new QiParticleOptions(0xFF6060, 0xA01020, 0x400008, 0.14F, 0.04F, 26, 0.0F, 0.02F, 0.0F,
								(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY)), x + sebar(r, 0.5D), y + h * (0.2D + r.nextDouble() * 0.6D),
								z + sebar(r, 0.5D), 0.0D, 0.02D, 0.0D);
					} else {
						level.addParticle(darah(), tx + sebar(r, 0.15D), ty, tz + sebar(r, 0.15D), 0.0D, -0.02D, 0.0D);
					}
				}
			}
			case "qi_masuk" -> {
				// motes drawn in from a ring around the body toward the chest
				for (int i = 0; i < n; i++) {
					double a = r.nextDouble() * Mth.TWO_PI;
					double rad = 1.1D + r.nextDouble() * 0.5D;
					double px = x + Math.cos(a) * rad;
					double pz = z + Math.sin(a) * rad;
					double py = y + h * (0.3D + r.nextDouble() * 0.5D);
					float life = 18.0F;
					level.addParticle(new QiParticleOptions(0xFFFFFF, dao, dao, 0.1F, 0.03F, (int) life,
							(float) ((x - px) / life), (float) ((y + h * 0.55D - py) / life), (float) ((z - pz) / life),
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)), px, py, pz, 0.0D, 0.0D, 0.0D);
				}
			}
			case "qi_tipis" -> {
				for (int i = 0; i < n; i++) {
					level.addParticle(new QiParticleOptions(dao, dao, dao, 0.08F, 0.14F, 30 + r.nextInt(16), 0.0F, 0.012F, 0.0F,
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)), x + sebar(r, 0.45D),
							y + h * r.nextDouble(), z + sebar(r, 0.45D), 0.0D, 0.0D, 0.0D);
				}
			}
			default -> {
				SimpleParticleType vanilla = vanilla(id);
				if (vanilla == null) {
					return;
				}
				boolean diAtas = id.endsWith("note");
				for (int i = 0; i < n; i++) {
					double px = diAtas ? x + sebar(r, 0.3D) : tx + sebar(r, 0.15D);
					double py = diAtas ? y + h + 0.2D : ty;
					double pz = diAtas ? z + sebar(r, 0.3D) : tz + sebar(r, 0.15D);
					// a note particle reads its x speed as the colour (0..1)
					double vx = diAtas ? r.nextInt(24) / 24.0D : sebar(r, 0.03D);
					level.addParticle(vanilla, px, py, pz, vx, diAtas ? 0.0D : 0.02D, diAtas ? 0.0D : sebar(r, 0.03D));
				}
			}
		}
	}

	private static double sebar(RandomSource r, double amp) {
		return (r.nextDouble() * 2.0D - 1.0D) * amp;
	}

	private static @Nullable SimpleParticleType vanilla(String id) {
		if (PARTIKEL_VANILLA.containsKey(id)) {
			return PARTIKEL_VANILLA.get(id);
		}
		SimpleParticleType t = null;
		try {
			if (BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse(id)) instanceof SimpleParticleType s) {
				t = s;
			}
		} catch (RuntimeException ignored) {
			// unknown or malformed ids draw nothing
		}
		PARTIKEL_VANILLA.put(id, t);
		return t;
	}

	private static ParticleOptions tanah() {
		if (tanah == null) {
			tanah = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
		}
		return tanah;
	}

	private static ParticleOptions debu() {
		if (debu == null) {
			debu = new DustParticleOptions(0xC8B89A, 0.9F);
		}
		return debu;
	}

	private static ParticleOptions butir() {
		if (butir == null) {
			butir = new DustParticleOptions(0xE0C060, 0.5F);
		}
		return butir;
	}

	private static ParticleOptions darah() {
		if (darah == null) {
			darah = new DustParticleOptions(0x8A0A14, 0.8F);
		}
		return darah;
	}

	private GerakKlien() {
	}
}
