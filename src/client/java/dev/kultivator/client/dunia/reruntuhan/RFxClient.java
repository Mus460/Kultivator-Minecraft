package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.dunia.reruntuhan.gua.BayanganEntity;
import dev.kultivator.dunia.reruntuhan.gua.SisaJiwaEntity;
import dev.kultivator.dunia.reruntuhan.menara.PenjagaLantaiEntity;
import dev.kultivator.dunia.reruntuhan.penjara.IblisKecilEntity;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The one-shot moments of module R ({@code op: "fx"}, the {@link RFx} ids), all client side and budgeted (DESIGN 34.0
 * rules V1-V4): the {@code kultivator:qi} particle scaled by EffectQuality, layered additive quads with glow textures
 * from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} (skipped in a shader pack's shadow pass, × 0.6 under a pack),
 * 7b title cards, the sky grade of {@link SkyDirector}, short skippable shots through {@link DuniaScenes} (Kamera
 * sinematik Penuh / Singkat / Mati and ShotGuard apply; none indoors for a door, only Penuh for a tower milestone).
 * <ul>
 * <li>puzzles: lit plates, lamps and rings, the solved formation circle, telegraphed pulses, the backlash, the opened
 * seal (dust, a flare and the "Segel Terbuka" card);</li>
 * <li>fights: ground telegraph rings and lines that fill as the blow comes, slams, waves, impacts, rising and falling
 * guardians, the ambient motes of the fighters;</li>
 * <li>the prison: the seal break (crack decals, a red pillar, chains flying off, a dark red sky within 128 blocks, the
 * card and a short orbit), the re-seal (gold), torn talismans, re-lit pillars and the red qi leaking from a weak seal
 * (4 / 12 / 24 motes per second);</li>
 * <li>the tower: floor cleared stamps, the milestone shot of every tenth floor, band gates, qi waves.</li>
 * </ul>
 * Quads per frame: 120 / 400 / 800 by quality.
 */
final class RFxClient {

	private static final int[] QUAD_CAP = {120, 400, 800};
	private static final int[] LEAK_PER_SECOND = {4, 12, 24};
	private static final int[] LAMP_PER_SECOND = {2, 6, 12};
	private static final int MAX_FX = 96;
	private static final int SKY_PRIORITY = 20;
	private static final int[] KING_RGB = {0x6AB040, 0xE04020, 0xC89050};

	/** One live effect. */
	private static final class Fx {
		final int kind;
		final Vec3 a;
		final @Nullable Vec3 b;
		final int ia;
		final int ib;
		final float f;
		final int life;
		final int seed;
		int age;

		Fx(int kind, Vec3 a, @Nullable Vec3 b, int ia, int ib, float f, int life, int seed) {
			this.kind = kind;
			this.a = a;
			this.b = b;
			this.ia = ia;
			this.ib = ib;
			this.f = f;
			this.life = Math.max(2, life);
			this.seed = seed;
		}

		float t(float partial) {
			return Mth.clamp((age + partial) / life, 0.0F, 1.0F);
		}
	}

	/** The red qi leaking from a weak seal: emits for a while after each server pulse. */
	private static final class Leak {
		final Vec3 at;
		int level;
		long until;

		Leak(Vec3 at) {
			this.at = at;
		}
	}

	/** The air of a dressed puzzle room (RFx.ROOM_AIR): emits for a while after each server pulse. */
	private static final class Air {
		final Vec3 at;
		int look;
		long until;

		Air(Vec3 at) {
			this.at = at;
		}
	}

	private static final int[] AIR_PER_SECOND = {2, 5, 10};
	private static final List<Fx> LIVE = new ArrayList<>();
	private static final List<Leak> LEAKS = new ArrayList<>();
	private static final List<Air> AIRS = new ArrayList<>();
	private static final RSink RING = new RSink(RTex.RING);
	private static final RSink TELE = new RSink(RTex.TELEGRAPH);
	private static final RSink SIGIL = new RSink(RTex.SIGIL);
	private static final RSink GLOW = new RSink(RTex.GLOW);
	private static final RSink STRIP = new RSink(RTex.STRIP);
	private static final RSink CRACK = new RSink(RTex.CRACK);
	private static final RSink CURTAIN = new RSink(RTex.CURTAIN);
	private static final RSink[] ORDER = {CRACK, TELE, RING, SIGIL, CURTAIN, STRIP, GLOW};
	private static @Nullable ItemStack chainStack;
	private static @Nullable ItemStackRenderState chainState;
	private static long ticks;
	private static boolean skyOn;
	private static long skyUntil;
	private static boolean warned;

	// ================================================================== events

	static void onFx(CompoundTag tag) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		int kind = tag.getIntOr("k", 0);
		Vec3 a = new Vec3(tag.getDoubleOr("x", 0.0D), tag.getDoubleOr("y", 0.0D), tag.getDoubleOr("z", 0.0D));
		Vec3 b = tag.contains("x2") ? new Vec3(tag.getDoubleOr("x2", 0.0D), tag.getDoubleOr("y2", 0.0D), tag.getDoubleOr("z2", 0.0D)) : null;
		int ia = tag.getIntOr("a", 0);
		int ib = tag.getIntOr("b", 0);
		float f = tag.getFloatOr("f", 1.0F);
		double dist = Math.sqrt(minecraft.player.position().distanceToSqr(a));
		boolean near = dist < 24.0D;
		int seed = (int) (a.x * 31.0D + a.z * 17.0D) ^ (int) ticks;
		RandomSource random = level.getRandom();
		switch (kind) {
			case RFx.DOOR_OPEN -> {
				add(kind, a, null, ia, ib, f, 40, seed);
				dust(level, random, a, 1.6F * f, 26, 0xA89880);
				if (ib == 1 && near) {
					Scene scene = DuniaScenes.world("segel/buka", a, seed, 80, true);
					DuniaScenes.card(scene, 6, Component.translatable("kultivator.reruntuhan.card.terbuka"),
							Component.translatable("kultivator.reruntuhan.gua.tier." + Mth.clamp(ia, 1, 3)), Glyphs.SEAL, 0xFFC86A);
					CameraShake.add(0.35F, 14, a, 12.0F);
				}
			}
			case RFx.BACKLASH -> {
				add(kind, a, null, ia, ib, f, 16, seed);
				burst(level, random, a, 0xFF3A2A, 14, 0.22F, true);
				if (dist < 2.0D) {
					ScreenFlash.add(0xC02010, 0.18F, 5);
				}
			}
			case RFx.PLATE_LIT -> {
				int rgb = Glyphs.TRIGRAM_RGB[Math.floorMod(ia, 8)];
				add(kind, a, null, rgb, ib, f, 30, seed);
				rise(level, random, a, rgb, 10, 0.6F);
			}
			case RFx.LAMP_LIT -> {
				add(kind, a, null, ia, ib, f, 22, seed);
				if (Qi.on()) {
					for (int i = 0; i < EffectQuality.scale(12); i++) {
						level.addParticle(Qi.flame(ia, 0.12F, 18), a.x + random.nextGaussian() * 0.15D, a.y + 0.5D, a.z + random.nextGaussian() * 0.15D,
								random.nextGaussian() * 0.03D, 0.06D + random.nextDouble() * 0.05D, random.nextGaussian() * 0.03D);
					}
				}
			}
			case RFx.RING_TURN -> {
				add(kind, a, null, ia, ib, f, 12, seed);
				if (Qi.on()) {
					int n = EffectQuality.scale(10);
					for (int i = 0; i < n; i++) {
						double ang = random.nextDouble() * Math.PI * 2.0D;
						level.addParticle(Qi.spark(0xFFE6A0, 0.06F, 10), a.x + Math.cos(ang) * ib, a.y + 0.3D, a.z + Math.sin(ang) * ib,
								-Math.sin(ang) * 0.08D, 0.02D, Math.cos(ang) * 0.08D);
					}
				}
			}
			case RFx.SOLVED -> {
				add(kind, a, null, ia, ib, f, 70, seed);
				rise(level, random, a, 0xFFD54A, 30, 3.5F);
				if (near) {
					TitleCard.show(TitleCard.stamp(Component.translatable("kultivator.reruntuhan.card.terpecahkan"), solvedSeal(ib), 0xFFD54A));
				}
			}
			case RFx.PULSE_WARN -> add(kind, a, null, ia, ib, f, Math.max(6, ia), seed);
			case RFx.PULSE_HIT -> {
				add(kind, a, null, ia, ib, f, 14, seed);
				burst(level, random, a.add(0.0D, 0.2D, 0.0D), 0xFF6A3A, 10, 0.18F, true);
			}
			case RFx.GUARDIAN_RISE -> {
				add(kind, a, null, ia, ib, f, 40, seed);
				dust(level, random, a, f, 40, ia == 9 ? 0x5A2A20 : 0x9A8A70);
				CameraShake.add(0.5F, 24, a, 18.0F);
			}
			case RFx.SLAM -> {
				add(kind, a, null, ia, ib, f, 18, seed);
				dust(level, random, a, f, 22, 0x9A8A70);
				CameraShake.add(0.45F, 10, a, f * 3.0F);
			}
			case RFx.TELEGRAPH -> add(kind, a, null, ia, ib, f, Math.max(6, ib), seed);
			case RFx.LINE -> add(kind, a, b, ia, ib, f, Math.max(6, ib), seed);
			case RFx.SEAL_BREAK -> sealBreak(level, random, a, ia, f, seed, dist);
			case RFx.SEAL_SEALED -> {
				boolean big = f >= 10.0F;
				add(kind, a, null, ia, ib, f, big ? 100 : 50, seed);
				rise(level, random, a, 0xFFD54A, big ? 60 : 16, big ? 8.0F : 1.5F);
				if (big && dist < f + 40.0D) {
					Scene scene = DuniaScenes.world("segel/pulih", a, seed, 120, dist < f + 10.0D);
					DuniaScenes.card(scene, 10, Component.translatable("kultivator.reruntuhan.card.pulih"),
							Component.translatable("kultivator.reruntuhan.card.pulih.sub"), Glyphs.SEAL, 0xFFD54A);
					SkyDirector.grade(SkyDirector.Grade.SK_GOLD, 0.45F, 0.0F, 30, SKY_PRIORITY, a, true, 1.0F, a.y);
					SkyDirector.holdThenRelease(80, 80, SKY_PRIORITY);
					skyOn = false;
				}
			}
			case RFx.TALISMAN_TORN -> {
				add(kind, a, null, ia, ib, f, 20, seed);
				if (Qi.on()) {
					for (int i = 0; i < EffectQuality.scale(12); i++) {
						level.addParticle(Qi.petal(i % 3 == 0 ? 0xC02A1A : 0xF0D878, 0.1F, 40), a.x, a.y + 1.2D, a.z, random.nextGaussian() * 0.08D,
								0.05D + random.nextDouble() * 0.05D, random.nextGaussian() * 0.08D);
					}
				}
				if (near) {
					ScreenFlash.add(0x800000, 0.12F, 6);
				}
			}
			case RFx.PILLAR_LIT -> {
				add(kind, a, null, ia, ib, f, 50, seed);
				rise(level, random, a.add(0.0D, 1.0D, 0.0D), 0xFFD54A, 20, 1.0F);
			}
			case RFx.KING_SKILL -> {
				add(kind, a, null, ia, ib, f, ib == 9 ? 40 : 24, seed);
				int rgb = KING_RGB[Mth.clamp(ia, 0, 2)];
				burst(level, random, a.add(0.0D, 1.5D, 0.0D), rgb, ib == 9 ? 40 : 14, 0.3F, false);
				if (ib == 9) {
					CameraShake.add(0.6F, 20, a, 24.0F);
				}
			}
			case RFx.TOWER_CLEAR -> {
				add(kind, a, null, ia, ib, f, 50, seed);
				rise(level, random, a, LobbyState.bandRgb(ib + 1), 24, 4.0F);
				TitleCard.show(TitleCard.stamp(Component.translatable("kultivator.reruntuhan.card.lantai", ia), Glyphs.TOWER,
						LobbyState.bandRgb(ib + 1)));
			}
			case RFx.TOWER_MILESTONE -> {
				add(kind, a, null, ia, ib, f, 80, seed);
				Scene scene = DuniaScenes.world("menara/" + ia, a, seed, 100, true);
				DuniaScenes.card(scene, 4, Component.translatable("kultivator.reruntuhan.card.tonggak", ia),
						Component.translatable("kultivator.reruntuhan.card.tonggak.sub." + Mth.clamp(ib, 0, 9)), Glyphs.TOWER, LobbyState.bandRgb(ib + 1));
				if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
					DuniaScenes.orbit(scene, 6, 7.0F, 3.5F, 50.0F, 3.0F);
				}
			}
			case RFx.QI_WAVE -> add(kind, a, null, ia, ib, f, 22, seed);
			case RFx.INHERIT -> {
				add(kind, a, null, ia, ib, f, 90, seed);
				rise(level, random, a, 0xFFE6A0, 50, 2.5F);
				if (near) {
					Scene scene = DuniaScenes.world("segel/warisan", a, seed, 120, true);
					DuniaScenes.card(scene, 10, Component.translatable("kultivator.reruntuhan.card.warisan"),
							Component.translatable("kultivator.reruntuhan.gua.tier." + Mth.clamp(ia, 1, 3)), "祖", 0xFFE6A0);
				}
			}
			case RFx.MIRROR_HEAL -> {
				add(kind, a, null, ia, ib, f, 16, seed);
				burst(level, random, a, 0xB08AFF, 10, 0.15F, false);
			}
			case RFx.BAND_GATE -> {
				add(kind, a, null, ia, ib, f, 30, seed);
				rise(level, random, a, LobbyState.bandRgb(ia), 24, 1.2F);
			}
			case RFx.SOUL_APPEAR -> {
				add(kind, a, null, ia, ib, f, 30, seed);
				if (Qi.on()) {
					for (int i = 0; i < EffectQuality.scale(18); i++) {
						double ang = i * 0.7D;
						level.addParticle(Qi.mist(ia, 0.35F, 30), a.x + Math.cos(ang) * 0.6D, a.y - 0.5D + i * 0.06D, a.z + Math.sin(ang) * 0.6D,
								-Math.sin(ang) * 0.04D, 0.03D, Math.cos(ang) * 0.04D);
					}
				}
			}
			case RFx.GUARDIAN_DOWN -> {
				add(kind, a, null, ia, ib, f, 40, seed);
				dust(level, random, a, f, 30, 0x8A7A60);
				rise(level, random, a, ia, 30, f);
				if (ib == 9 && dist < 64.0D) {
					Scene scene = DuniaScenes.world("segel/tumbang", a, seed, 100, dist < 32.0D);
					DuniaScenes.card(scene, 6, Component.translatable("kultivator.reruntuhan.card.tumbang"),
							Component.translatable("kultivator.reruntuhan.card.tumbang.sub"), Glyphs.PRISON, 0xFF5A3A);
				}
			}
			case RFx.SEAL_LEAK -> leak(a, ia);
			case RFx.ROOM_AIR -> air(a, ia);
			case RFx.SKILL_HIT -> {
				add(kind, a, b, ia, ib, f, 14, seed);
				Vec3 at = b != null ? b : a;
				burst(level, random, at.add(0.0D, 0.3D, 0.0D), ia, 12, 0.25F, true);
			}
			default -> {
			}
		}
	}

	private static String solvedSeal(int kind) {
		return switch (kind) {
			case 0 -> "卦";
			case 1 -> "行";
			case 2 -> "斗";
			case 3 -> "幻";
			case 4 -> "鏡";
			default -> "衡";
		};
	}

	private static void add(int kind, Vec3 a, @Nullable Vec3 b, int ia, int ib, float f, int life, int seed) {
		if (LIVE.size() >= MAX_FX) {
			LIVE.remove(0);
		}
		LIVE.add(new Fx(kind, a, b, ia, ib, f, life, seed));
	}

	private static void leak(Vec3 at, int level) {
		for (Leak leak : LEAKS) {
			if (leak.at.distanceToSqr(at) < 1.0D) {
				leak.level = level;
				leak.until = ticks + 45L;
				return;
			}
		}
		if (LEAKS.size() >= 16) {
			LEAKS.remove(0);
		}
		Leak leak = new Leak(at);
		leak.level = level;
		leak.until = ticks + 45L;
		LEAKS.add(leak);
	}

	private static void air(Vec3 at, int look) {
		for (Air air : AIRS) {
			if (air.at.distanceToSqr(at) < 1.0D) {
				air.look = look;
				air.until = ticks + 60L;
				return;
			}
		}
		if (AIRS.size() >= 8) {
			AIRS.remove(0);
		}
		Air air = new Air(at);
		air.look = look;
		air.until = ticks + 60L;
		AIRS.add(air);
	}

	/**
	 * The air of the dressed rooms near the viewer: 2 / 5 / 10 motes per second per room by quality. Library: gold
	 * dust drifting up in the lamplight; shrine: grey incense rising from the four corner braziers (six blocks out
	 * from the centre); garden grotto: petals and leaves falling from under the coffers.
	 */
	private static void airs(ClientLevel level, Vec3 eye, RandomSource random) {
		int perSecond = AIR_PER_SECOND[EffectQuality.level().ordinal()];
		for (Iterator<Air> it = AIRS.iterator(); it.hasNext(); ) {
			Air air = it.next();
			if (ticks > air.until) {
				it.remove();
				continue;
			}
			if (air.at.distanceToSqr(eye) > 24.0D * 24.0D || random.nextInt(10) >= Math.min(10, perSecond)) {
				continue;
			}
			double x = air.at.x + (random.nextDouble() - 0.5D) * 13.0D;
			double z = air.at.z + (random.nextDouble() - 0.5D) * 13.0D;
			switch (air.look) {
				case 1 -> level.addParticle(Qi.mote(0xFFE6A0, 0.05F + random.nextFloat() * 0.03F, 70), x, air.at.y + 0.5D + random.nextDouble() * 4.0D, z,
						random.nextGaussian() * 0.004D, 0.006D, random.nextGaussian() * 0.004D);
				case 2 -> {
					double cx = air.at.x + (random.nextBoolean() ? 6.0D : -6.0D) + random.nextGaussian() * 0.15D;
					double cz = air.at.z + (random.nextBoolean() ? 6.0D : -6.0D) + random.nextGaussian() * 0.15D;
					level.addParticle(Qi.smoke(0xB8B0A8, 0.18F + random.nextFloat() * 0.1F, 60), cx, air.at.y + 1.4D, cz,
							random.nextGaussian() * 0.006D, 0.03D, random.nextGaussian() * 0.006D);
				}
				case 3 -> level.addParticle(Qi.petal(random.nextInt(3) == 0 ? 0xB8E07A : 0xF4B6C8, 0.07F, 90), x, air.at.y + 5.0D + random.nextDouble(), z,
						0.01D + random.nextGaussian() * 0.01D, -0.025D, random.nextGaussian() * 0.01D);
				default -> {
				}
			}
		}
	}

	/** The seal breaks: decals, the red pillar, chains flying off, a dark red sky, the card and a short orbit. */
	private static void sealBreak(ClientLevel level, RandomSource random, Vec3 at, int king, float arena, int seed, double dist) {
		add(RFx.SEAL_BREAK, at, null, king, 0, arena, 140, seed);
		dust(level, random, at, 4.0F, 50, 0x5A2020);
		burst(level, random, at.add(0.0D, 2.0D, 0.0D), 0xFF2A1A, 60, 0.45F, true);
		if (dist < 128.0D) {
			SkyDirector.grade(SkyDirector.Grade.SK_RED, 0.8F, 0.6F, 40, SKY_PRIORITY, at, true, 1.0F, at.y);
			skyOn = true;
			skyUntil = ticks + 600L;
			CameraShake.add(0.8F, 40, at, 32.0F, true);
			ScreenFlash.add(0xC01010, 0.3F, 8);
		}
		boolean own = dist < arena + 12.0D;
		Scene scene = DuniaScenes.world("segel/pecah", at, seed, 120, own);
		Component name = Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[Mth.clamp(king, 0, 2)]);
		DuniaScenes.card(scene, 20, Component.translatable("kultivator.reruntuhan.card.pecah"), name, Glyphs.PRISON, 0xFF2A1A);
		// 2.2 s: the shot ends before the king rises (50 ticks after the break), so it never runs during the fight
		DuniaScenes.orbit(scene, 4, 9.0F, 5.0F, 50.0F, 2.2F);
	}

	// ================================================================== particles

	private static void dust(ClientLevel level, RandomSource random, Vec3 at, float radius, int count, int rgb) {
		if (!Qi.on()) {
			return;
		}
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double ang = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * radius;
			level.addParticle(Qi.smoke(rgb, 0.35F + random.nextFloat() * 0.3F, 30 + random.nextInt(20)), at.x + Math.cos(ang) * r, at.y + 0.2D,
					at.z + Math.sin(ang) * r, Math.cos(ang) * 0.05D, 0.02D + random.nextDouble() * 0.04D, Math.sin(ang) * 0.05D);
		}
	}

	private static void burst(ClientLevel level, RandomSource random, Vec3 at, int rgb, int count, float speed, boolean sparks) {
		if (!Qi.on()) {
			return;
		}
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double vx = random.nextGaussian() * speed;
			double vy = random.nextGaussian() * speed * 0.6D + speed * 0.3D;
			double vz = random.nextGaussian() * speed;
			level.addParticle(sparks ? Qi.spark(rgb, 0.08F, 14) : Qi.mote(rgb, 0.12F, 20), at.x, at.y, at.z, vx, vy, vz);
		}
	}

	private static void rise(ClientLevel level, RandomSource random, Vec3 at, int rgb, int count, float radius) {
		if (!Qi.on()) {
			return;
		}
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double ang = random.nextDouble() * Math.PI * 2.0D;
			double r = Math.sqrt(random.nextDouble()) * radius;
			level.addParticle(Qi.mote(rgb, 0.08F + random.nextFloat() * 0.06F, 40 + random.nextInt(20)), at.x + Math.cos(ang) * r, at.y + 0.1D,
					at.z + Math.sin(ang) * r, 0.0D, 0.04D + random.nextDouble() * 0.05D, 0.0D);
		}
	}

	// ================================================================== tick

	static void tick(Minecraft minecraft) {
		ticks++;
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Iterator<Fx> it = LIVE.iterator(); it.hasNext(); ) {
			Fx fx = it.next();
			fx.age++;
			if (fx.age >= fx.life) {
				it.remove();
				continue;
			}
			if (fx.kind == RFx.SEAL_BREAK && Qi.on() && fx.age % 4 == 0 && fx.age < 100) {
				// the red pillar sheds embers while it stands
				for (int i = 0; i < EffectQuality.scale(4); i++) {
					level.addParticle(Qi.spark(0xFF3A1A, 0.1F, 30), fx.a.x + random.nextGaussian() * 1.2D, fx.a.y + random.nextDouble() * 12.0D,
							fx.a.z + random.nextGaussian() * 1.2D, 0.0D, 0.08D, 0.0D);
				}
			}
		}
		if (skyOn && ticks > skyUntil) {
			SkyDirector.release(100, SKY_PRIORITY);
			skyOn = false;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		if (Qi.on()) {
			int perSecond = LEAK_PER_SECOND[EffectQuality.level().ordinal()];
			for (Iterator<Leak> it = LEAKS.iterator(); it.hasNext(); ) {
				Leak leak = it.next();
				if (ticks > leak.until) {
					it.remove();
					continue;
				}
				if (leak.at.distanceToSqr(eye) > 64.0D * 64.0D) {
					continue;
				}
				int rate = perSecond * (1 + leak.level);
				if (random.nextInt(20) < Math.min(20, rate)) {
					double r = leak.level >= 2 ? 3.0D : 1.4D;
					level.addParticle(Qi.mist(leak.level >= 2 ? 0xC01818 : 0x8A1A20, 0.3F + leak.level * 0.1F, 40),
							leak.at.x + random.nextGaussian() * r, leak.at.y + 0.2D, leak.at.z + random.nextGaussian() * r, random.nextGaussian() * 0.01D,
							0.04D + leak.level * 0.02D, random.nextGaussian() * 0.01D);
				}
			}
		} else {
			LEAKS.clear();
		}
		if (ticks % 4L == 0L && Qi.on()) {
			ambient(level, eye, random);
		}
		if (ticks % 2L == 0L && Qi.on()) {
			lamps(level, eye, random);
			airs(level, eye, random);
		} else if (!Qi.on()) {
			AIRS.clear();
		}
	}

	/** The flames of lit Lima Unsur lamps: 2 / 6 / 12 motes per second each, in the element's colour (at most 10 lamps). */
	private static void lamps(ClientLevel level, Vec3 eye, RandomSource random) {
		int perSecond = LAMP_PER_SECOND[EffectQuality.level().ordinal()];
		int shown = 0;
		for (TekaPartEntity lamp : level.getEntitiesOfClass(TekaPartEntity.class, new AABB(eye, eye).inflate(24.0D),
				e -> e.role() == TekaPartEntity.LAMP && e.state() == TekaPartEntity.LIT)) {
			if (shown++ >= 10) {
				break;
			}
			// two ticks per call: twice the per-tick chance
			if (random.nextInt(20) < Math.min(20, perSecond * 2)) {
				level.addParticle(Qi.flame(lamp.color(), 0.08F + random.nextFloat() * 0.05F, 14), lamp.getX() + random.nextGaussian() * 0.06D,
						lamp.getY() + 0.55D, lamp.getZ() + random.nextGaussian() * 0.06D, 0.0D, 0.035D + random.nextDouble() * 0.02D, 0.0D);
			}
		}
	}

	/** The motes of the module's beings near the viewer (at most 8 of them, every fourth tick). */
	private static void ambient(ClientLevel level, Vec3 eye, RandomSource random) {
		int shown = 0;
		for (Entity entity : level.getEntities((Entity) null, new AABB(eye, eye).inflate(24.0D),
				e -> e instanceof RCreature || e instanceof SisaJiwaEntity)) {
			if (shown++ >= 8) {
				break;
			}
			if (!EffectQuality.chance(random, 0.7F)) {
				continue;
			}
			double ang = random.nextDouble() * Math.PI * 2.0D;
			double r = entity.getBbWidth() * 0.6D;
			double y = entity.getY() + random.nextDouble() * entity.getBbHeight();
			int rgb;
			if (entity instanceof SisaJiwaEntity soul) {
				rgb = soul.robe();
				level.addParticle(Qi.mote(rgb, 0.07F, 40), entity.getX() + Math.cos(ang) * r, y, entity.getZ() + Math.sin(ang) * r, 0.0D, 0.025D, 0.0D);
				continue;
			}
			RCreature creature = (RCreature) entity;
			if (creature instanceof BayanganEntity) {
				rgb = 0x6A4AA0;
			} else if (creature instanceof RajaIblisEntity) {
				rgb = KING_RGB[Mth.clamp(creature.kind(), 0, 2)];
			} else if (creature instanceof IblisKecilEntity) {
				rgb = 0xA01A14;
			} else if (creature instanceof PenjagaLantaiEntity) {
				rgb = LobbyState.bandRgb(creature.kind() + 1);
			} else {
				rgb = 0x9FE0C0;
			}
			boolean hot = creature.has(RCreature.FLAG_TELEGRAPH) || creature.has(RCreature.FLAG_ENRAGED);
			level.addParticle(hot ? Qi.spark(rgb, 0.08F, 16) : Qi.mote(rgb, 0.07F, 26), entity.getX() + Math.cos(ang) * r, y,
					entity.getZ() + Math.sin(ang) * r, 0.0D, hot ? 0.06D : 0.02D, 0.0D);
		}
	}

	static void clear() {
		LIVE.clear();
		LEAKS.clear();
		AIRS.clear();
		chainState = null;
		if (skyOn) {
			SkyDirector.release(20, SKY_PRIORITY);
			skyOn = false;
		}
	}

	// ================================================================== render

	static void render(LevelRenderContext context) {
		if (LIVE.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			Vec3 cam = camera.pos;
			Vector3f right = new Vector3f();
			Vector3f up = new Vector3f();
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			for (RSink sink : ORDER) {
				sink.reset();
			}
			float pack = ShaderCompat.packOn() ? 0.6F : 1.0F;
			int budget = Math.round(QUAD_CAP[EffectQuality.level().ordinal()] * pack);
			PoseStack pose = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (Fx fx : LIVE) {
				if (quads() >= budget) {
					break;
				}
				if (fx.a.distanceToSqr(cam) > 160.0D * 160.0D) {
					continue;
				}
				draw(fx, cam, right, up, partial, pose, collector);
			}
			for (RSink sink : ORDER) {
				sink.submit(collector, pose);
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: ruin visuals skipped a frame", exception);
			}
		}
	}

	private static int quads() {
		int n = 0;
		for (RSink sink : ORDER) {
			n += sink.quads();
		}
		return n;
	}

	private static void draw(Fx fx, Vec3 cam, Vector3f right, Vector3f up, float partial, PoseStack pose, SubmitNodeCollector collector) {
		float t = fx.t(partial);
		float fade = 1.0F - t;
		float x = (float) (fx.a.x - cam.x);
		float y = (float) (fx.a.y - cam.y);
		float z = (float) (fx.a.z - cam.z);
		float time = ticks + partial;
		switch (fx.kind) {
			case RFx.DOOR_OPEN -> {
				GLOW.billboard(x, y, z, right, up, 1.2F * fx.f * (0.5F + t), RSink.argb(0.7F * fade, 0xFFE6A0));
				RING.flat(x, y - 1.4F, z, 0.8F + 3.0F * t * fx.f, 0.0F, RSink.argb(0.5F * fade, 0xC8B090));
			}
			case RFx.BACKLASH -> {
				SIGIL.flat(x, y - 0.9F, z, 1.4F * (1.0F - t * 0.4F), time * 0.08F, RSink.argb(0.8F * fade, 0xFF3A2A));
				RING.billboard(x, y, z, right, up, 0.5F + 1.5F * t, RSink.argb(0.6F * fade, 0xFF5A3A));
			}
			case RFx.PLATE_LIT, RFx.PILLAR_LIT, RFx.BAND_GATE -> {
				int rgb = fx.kind == RFx.PLATE_LIT ? fx.ia : fx.kind == RFx.PILLAR_LIT ? 0xFFD54A : LobbyState.bandRgb(fx.ia);
				float height = fx.kind == RFx.PILLAR_LIT ? 14.0F : fx.kind == RFx.BAND_GATE ? 7.0F : 4.0F;
				float w = fx.kind == RFx.PILLAR_LIT ? 1.4F : 0.8F;
				STRIP.beam(x, y, z, x, y + height * Math.min(1.0F, t * 3.0F), z, 0.0F, 0.0F, 0.0F, w, RSink.argb(0.8F * fade, rgb), RSink.argb(0.0F, rgb));
				RING.flat(x, y + 0.05F, z, 0.5F + 1.5F * t, 0.0F, RSink.argb(0.6F * fade, rgb));
			}
			case RFx.LAMP_LIT -> GLOW.billboard(x, y + 0.5F, z, right, up, 0.6F + 1.2F * t, RSink.argb(0.7F * fade, fx.ia));
			case RFx.RING_TURN -> RING.flat(x, y + 0.08F, z, fx.ib + 0.4F, time * 0.05F, RSink.argb(0.5F * fade, 0xFFE6A0));
			case RFx.SOLVED, RFx.INHERIT -> {
				int rgb = fx.kind == RFx.SOLVED ? 0xFFD54A : 0xFFE6A0;
				float grow = Mth.clamp(t * 2.0F, 0.0F, 1.0F);
				float r = (fx.kind == RFx.SOLVED ? 6.5F : 4.0F) * (0.3F + 0.7F * grow);
				float a = t < 0.7F ? 0.85F : 0.85F * (1.0F - (t - 0.7F) / 0.3F);
				SIGIL.flat(x, y + 0.06F, z, r, time * 0.01F, RSink.argb(a, rgb));
				SIGIL.flat(x, y + 0.08F, z, r * 0.55F, -time * 0.02F, RSink.argb(a * 0.8F, RDraw.lighten(rgb, 0.4F)));
				STRIP.band(x, y, y + 3.0F * grow, z, r * 0.95F, 16, RSink.argb(0.35F * a, rgb), RSink.argb(0.0F, rgb));
				if (fx.kind == RFx.INHERIT) {
					STRIP.beam(x, y + 12.0F, z, x, y - 1.0F, z, 0.0F, 0.0F, 0.0F, 2.4F, RSink.argb(0.0F, rgb), RSink.argb(0.7F * a, rgb));
				}
			}
			case RFx.PULSE_WARN, RFx.TELEGRAPH -> {
				int rgb = fx.kind == RFx.PULSE_WARN ? 0xFF4A2A : fx.ia;
				float r = fx.f;
				// the outline from the start, the fill growing inward-out until the blow lands
				TELE.flat(x, y + 0.05F, z, r, 0.0F, RSink.argb(0.55F + 0.2F * Mth.sin(time * 0.6F), rgb));
				TELE.flat(x, y + 0.06F, z, r * t, 0.0F, RSink.argb(0.45F, rgb));
			}
			case RFx.PULSE_HIT, RFx.SLAM -> {
				int rgb = fx.kind == RFx.PULSE_HIT ? 0xFF6A3A : fx.ia;
				float r = fx.f * (0.4F + 0.9F * t);
				RING.flat(x, y + 0.1F, z, r, 0.0F, RSink.argb(0.8F * fade, rgb));
				STRIP.band(x, y, y + 1.2F * fade + 0.2F, z, r, 20, RSink.argb(0.5F * fade, rgb), RSink.argb(0.0F, rgb));
				if (fx.kind == RFx.SLAM) {
					CRACK.flat(x, y + 0.04F, z, fx.f * 0.9F, fx.seed, RSink.argb(0.6F * fade, 0x2A1A10));
				}
			}
			case RFx.QI_WAVE -> {
				float r = fx.f * t;
				STRIP.band(x, y, y + 2.2F, z, r, 28, RSink.argb(0.5F * fade, fx.ia), RSink.argb(0.0F, fx.ia));
				RING.flat(x, y + 0.08F, z, r, 0.0F, RSink.argb(0.6F * fade, fx.ia));
			}
			case RFx.LINE -> {
				if (fx.b == null) {
					return;
				}
				float bx = (float) (fx.b.x - cam.x);
				float by = (float) (fx.b.y - cam.y);
				float bz = (float) (fx.b.z - cam.z);
				boolean ground = Math.abs(fx.b.y - fx.a.y) < 0.5D && fx.f >= 0.5F;
				if (ground) {
					// a telegraph strip on the floor: the outline now, filling toward the end as the blow comes
					float dx = bx - x;
					float dz = bz - z;
					float len = Mth.sqrt(dx * dx + dz * dz);
					if (len < 0.01F) {
						return;
					}
					float sx = -dz / len;
					float sz = dx / len;
					TELE.strip(x, y + 0.05F, z, bx, by + 0.05F, bz, sx, 0.0F, sz, fx.f, fx.f, RSink.argb(0.35F, fx.ia), RSink.argb(0.35F, fx.ia));
					TELE.strip(x, y + 0.06F, z, x + dx * t, y + 0.06F, z + dz * t, sx, 0.0F, sz, fx.f * 0.8F, fx.f * 0.8F, RSink.argb(0.5F, fx.ia),
							RSink.argb(0.5F, fx.ia));
				} else {
					STRIP.beam(x, y, z, bx, by, bz, 0.0F, 0.0F, 0.0F, Math.max(0.15F, fx.f), RSink.argb(0.7F, fx.ia), RSink.argb(0.7F, RDraw.lighten(fx.ia, 0.4F)));
				}
			}
			case RFx.SKILL_HIT -> {
				if (fx.b != null) {
					float bx = (float) (fx.b.x - cam.x);
					float by = (float) (fx.b.y - cam.y);
					float bz = (float) (fx.b.z - cam.z);
					STRIP.beam(x, y, z, bx, by, bz, 0.0F, 0.0F, 0.0F, Math.max(0.3F, fx.f) * (1.0F + t), RSink.argb(0.85F * fade, fx.ia),
							RSink.argb(0.6F * fade, RDraw.lighten(fx.ia, 0.5F)));
					GLOW.billboard(bx, by, bz, right, up, 0.8F + fx.f * t, RSink.argb(0.7F * fade, fx.ia));
				} else {
					RING.flat(x, y + 0.08F, z, fx.f * (0.5F + 0.8F * t), 0.0F, RSink.argb(0.8F * fade, fx.ia));
					GLOW.billboard(x, y + 0.6F, z, right, up, fx.f * 0.8F * fade + 0.3F, RSink.argb(0.6F * fade, fx.ia));
				}
			}
			case RFx.GUARDIAN_RISE, RFx.GUARDIAN_DOWN -> {
				int rgb = fx.kind == RFx.GUARDIAN_DOWN ? fx.ia : fx.ia == 9 ? 0xC03020 : 0xC8A060;
				CRACK.flat(x, y + 0.04F, z, fx.f * (0.6F + 0.6F * Math.min(1.0F, t * 3.0F)), fx.seed, RSink.argb(0.7F * fade, rgb));
				RING.flat(x, y + 0.08F, z, fx.f * (0.5F + 1.5F * t), 0.0F, RSink.argb(0.6F * fade, rgb));
				if (fx.kind == RFx.GUARDIAN_DOWN) {
					GLOW.billboard(x, y + 1.2F, z, right, up, fx.f * (0.5F + t), RSink.argb(0.5F * fade, rgb));
				}
			}
			case RFx.SEAL_BREAK -> sealBreakQuads(fx, x, y, z, t, time, pose, collector, cam);
			case RFx.SEAL_SEALED -> {
				float r = Math.min(fx.f, 12.0F) * (0.3F + 0.7F * Math.min(1.0F, t * 2.0F));
				float a = fade;
				SIGIL.flat(x, y + 0.06F, z, r, time * 0.015F, RSink.argb(0.85F * a, 0xFFD54A));
				STRIP.band(x, y, y + 6.0F * a, z, r * 0.5F, 18, RSink.argb(0.5F * a, 0xFFE6A0), RSink.argb(0.0F, 0xFFE6A0));
				// golden runes descending onto the core
				SIGIL.flat(x, y + 8.0F * (1.0F - t), z, r * 0.4F, -time * 0.03F, RSink.argb(0.7F * a, 0xFFF2C0));
			}
			case RFx.TALISMAN_TORN -> GLOW.billboard(x, y + 1.2F, z, right, up, 0.5F + t, RSink.argb(0.7F * fade, 0xFF3A1A));
			case RFx.KING_SKILL -> {
				int rgb = KING_RGB[Mth.clamp(fx.ia, 0, 2)];
				float r = fx.ib == 9 ? fx.f * (0.3F + t) : 2.0F + 2.0F * t;
				RING.flat(x, y + 0.1F, z, r, 0.0F, RSink.argb(0.7F * fade, rgb));
				GLOW.billboard(x, y + 2.0F, z, right, up, r * 0.6F, RSink.argb(0.35F * fade, rgb));
				if (fx.ib == 9) {
					STRIP.band(x, y, y + 4.0F, z, r, 24, RSink.argb(0.45F * fade, rgb), RSink.argb(0.0F, rgb));
				}
			}
			case RFx.TOWER_CLEAR, RFx.TOWER_MILESTONE -> {
				int rgb = LobbyState.bandRgb(fx.ib + 1);
				float r = (fx.kind == RFx.TOWER_MILESTONE ? 14.0F : 8.0F) * Math.min(1.0F, t * 1.6F);
				SIGIL.flat(x, y + 0.06F, z, r, time * 0.01F, RSink.argb(0.8F * fade, rgb));
				STRIP.band(x, y, y + 5.0F * fade, z, r, 24, RSink.argb(0.4F * fade, rgb), RSink.argb(0.0F, rgb));
				if (fx.kind == RFx.TOWER_MILESTONE) {
					STRIP.beam(x, y, z, x, y + 40.0F, z, 0.0F, 0.0F, 0.0F, 3.0F, RSink.argb(0.7F * fade, rgb), RSink.argb(0.0F, rgb));
				}
			}
			case RFx.MIRROR_HEAL -> GLOW.billboard(x, y, z, right, up, 1.0F * fade + 0.2F, RSink.argb(0.6F * fade, 0xB08AFF));
			case RFx.SOUL_APPEAR -> GLOW.billboard(x, y, z, right, up, 1.4F * (0.4F + t) * fx.f, RSink.argb(0.5F * fade, fx.ia));
			default -> {
			}
		}
	}

	/** Crack decals, the red pillar with its rings and the chains flying off. */
	private static void sealBreakQuads(Fx fx, float x, float y, float z, float t, float time, PoseStack pose, SubmitNodeCollector collector, Vec3 cam) {
		float fade = t < 0.8F ? 1.0F : (1.0F - t) / 0.2F;
		float grow = Math.min(1.0F, t * 4.0F);
		int rgb = 0xFF2A1A;
		for (int i = 0; i < 3; i++) {
			CRACK.flat(x + (i - 1) * 2.5F * grow, y + 0.03F + i * 0.002F, z + ((i * 7) % 3 - 1) * 2.0F * grow, 4.0F + 3.0F * grow, i * 2.1F + fx.seed,
					RSink.argb(0.75F * fade, rgb));
		}
		float height = 48.0F * grow;
		STRIP.beam(x, y, z, x, y + height, z, 0.0F, 0.0F, 0.0F, 3.5F, RSink.argb(0.85F * fade, rgb), RSink.argb(0.1F * fade, 0xFF8A3A));
		STRIP.beam(x, y, z, x, y + height * 0.8F, z, 0.0F, 0.0F, 0.0F, 1.4F, RSink.argb(0.9F * fade, 0xFFD0A0), RSink.argb(0.0F, 0xFFD0A0));
		for (int k = 0; k < 3; k++) {
			float ry = y + ((time * 0.12F + k * 5.0F) % 15.0F);
			RING.flat(x, ry, z, 3.0F + k, time * 0.02F, RSink.argb(0.5F * fade, rgb));
		}
		// ten chain pieces flying off the core for the first two seconds
		if (fx.age < 40) {
			ItemStackRenderState chain = chain();
			if (chain != null && !chain.isEmpty()) {
				float tt = Math.min(1.0F, (fx.age) / 40.0F);
				for (int k = 0; k < 10; k++) {
					double ang = k * Math.PI / 5.0D + fx.seed * 0.1D;
					float r = 1.0F + 12.0F * tt;
					float cy = y + 3.0F + 6.0F * tt - 9.0F * tt * tt;
					pose.pushPose();
					pose.translate(x + (float) Math.cos(ang) * r, cy, z + (float) Math.sin(ang) * r);
					pose.rotate(com.mojang.math.Axis.YP.rotationDegrees((float) Math.toDegrees(ang) + time * 20.0F));
					pose.rotate(com.mojang.math.Axis.XP.rotationDegrees(time * 31.0F + k * 40.0F));
					pose.scale(1.6F, 1.6F, 1.6F);
					chain.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
					pose.popPose();
				}
			}
		}
	}

	private static @Nullable ItemStackRenderState chain() {
		if (chainState == null) {
			if (chainStack == null) {
				chainStack = new ItemStack(Items.IRON_CHAIN);
			}
			Minecraft minecraft = Minecraft.getInstance();
			ItemStackRenderState state = new ItemStackRenderState();
			minecraft.getItemModelResolver().updateForTopItem(state, chainStack, ItemDisplayContext.FIXED, minecraft.level, null, 0);
			chainState = state;
		}
		return chainState;
	}

	private RFxClient() {
	}
}
