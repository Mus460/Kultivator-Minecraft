package dev.kultivator.client.dunia.tempat.cerita;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The short story scenes of DESIGN 38.S.3 on the client ({@link AdeganPayload}). Each scene is a recipe: one camera move
 * of at most 4 s through {@link DuniaScenes} (so {@code CinematicDirector.shoot} applies ShotGuard, Kamera sinematik
 * Penuh / Singkat / Mati and the skip keys), a title card with a seal glyph (full with Penuh, a stamp otherwise; "Kartu
 * judul" still decides), the scene's sound beats and a few vanilla particles scaled by {@link EffectQuality} (none on
 * Minimal). With {@link AdeganPayload#KARTU_SAJA} only the card and the sound play. No post chain, no block, no world
 * change: Sodium and Iris safe.
 */
public final class AdeganCerita {

	/** Camera move of a recipe. */
	private enum Gerak {
		ORBIT, PULLBACK, NONE
	}

	/**
	 * One scene: card title key ({@code %s} = the scene number), subtitle key (nullable), seal glyph and colour, the move,
	 * the sound beats (sound, delay ticks, pitch) and the particle of its sparkle (nullable).
	 */
	private record Resep(String judul, @Nullable String sub, String segel, int warna, Gerak gerak, float detik, Bunyi[] bunyi,
			@Nullable ParticleOptions partikel) {
	}

	private record Bunyi(SoundEvent suara, int tunda, float nada) {
	}

	private static final Map<String, Resep> RESEP = Map.ofEntries(
			Map.entry("bab", new Resep("kultivator.cerita.adegan.bab", "kultivator.cerita.adegan.bab.sub", "章", 0xE8C46A, Gerak.ORBIT, 3.5F,
					new Bunyi[] {new Bunyi(SoundEvents.BELL_BLOCK, 0, 0.6F), new Bunyi(SoundEvents.BELL_BLOCK, 24, 0.5F)}, ParticleTypes.END_ROD)),
			Map.entry("ruyi_pingsan", new Resep("kultivator.cerita.adegan.ruyi_pingsan", "kultivator.cerita.adegan.ruyi_pingsan.sub", "毒", 0x9A5CC8,
					Gerak.PULLBACK, 3.0F, new Bunyi[] {new Bunyi(SoundEvents.WITCH_DRINK, 0, 0.7F), new Bunyi(SoundEvents.BELL_BLOCK, 20, 0.45F)},
					ParticleTypes.WITCH)),
			Map.entry("sidang", new Resep("kultivator.cerita.adegan.sidang", "kultivator.cerita.adegan.sidang.sub", "判", 0xC8302A, Gerak.ORBIT, 4.0F,
					new Bunyi[] {new Bunyi(SoundEvents.WOOD_HIT, 6, 0.7F), new Bunyi(SoundEvents.WOOD_HIT, 14, 0.7F), new Bunyi(SoundEvents.WOOD_HIT, 30, 0.55F)},
					null)),
			Map.entry("topeng", new Resep("kultivator.cerita.adegan.topeng", "kultivator.cerita.adegan.topeng.sub", "魔", 0x8A1020, Gerak.PULLBACK, 3.0F,
					new Bunyi[] {new Bunyi(SoundEvents.GLASS_BREAK, 4, 0.6F), new Bunyi(SoundEvents.WITCH_DRINK, 18, 0.5F)}, ParticleTypes.LARGE_SMOKE)),
			Map.entry("penobatan", new Resep("kultivator.cerita.adegan.penobatan", "kultivator.cerita.adegan.penobatan.sub", "冠", 0xFFD27A, Gerak.ORBIT, 4.0F,
					new Bunyi[] {new Bunyi(SoundEvents.BELL_BLOCK, 0, 0.8F), new Bunyi(SoundEvents.FIREWORK_ROCKET_BLAST, 20, 1.0F),
							new Bunyi(SoundEvents.FIREWORK_ROCKET_BLAST, 34, 1.2F)}, ParticleTypes.FIREWORK)),
			Map.entry("kakek_lumpur", new Resep("kultivator.cerita.adegan.kakek_lumpur", "kultivator.cerita.adegan.kakek_lumpur.sub", "隱", 0x7FD8B0,
					Gerak.ORBIT, 3.0F, new Bunyi[] {new Bunyi(SoundEvents.BEACON_ACTIVATE, 0, 1.4F)}, ParticleTypes.ENCHANT)),
			Map.entry("pewaris_selamat", new Resep("kultivator.cerita.adegan.pewaris_selamat", null, "愈", 0x8FE0B0, Gerak.NONE, 0.0F,
					new Bunyi[] {new Bunyi(SoundEvents.AMETHYST_BLOCK_CHIME, 0, 1.2F)}, ParticleTypes.HAPPY_VILLAGER)),
			Map.entry("pedang_lahir", new Resep("kultivator.cerita.adegan.pedang_lahir", "kultivator.cerita.adegan.pedang_lahir.sub", "劍", 0xBFE6FF,
					Gerak.ORBIT, 3.0F, new Bunyi[] {new Bunyi(SoundEvents.ANVIL_USE, 0, 0.8F), new Bunyi(SoundEvents.AMETHYST_BLOCK_CHIME, 16, 0.9F)},
					ParticleTypes.END_ROD)),
			// harbour-theme scenes sent by 38TK-P (LintasLaut, HariDewiLaut, SerbuanBajakLaut, PelabuhanCerita); titles are theirs
			Map.entry("tema_pelabuhan_lintas", new Resep("kultivator.cerita.adegan.tema_pelabuhan_lintas",
					"kultivator.cerita.adegan.tema_pelabuhan_lintas.sub", "航", 0x7FB8D8, Gerak.PULLBACK, 4.0F,
					new Bunyi[] {new Bunyi(SoundEvents.BELL_BLOCK, 0, 0.5F), new Bunyi(SoundEvents.BOAT_PADDLE_WATER, 10, 0.8F),
							new Bunyi(SoundEvents.BOAT_PADDLE_WATER, 26, 0.7F), new Bunyi(SoundEvents.BELL_BLOCK, 50, 0.45F)}, ParticleTypes.CLOUD)),
			Map.entry("tema_pelabuhan_dewi", new Resep("kultivator.cerita.adegan.tema_pelabuhan_dewi",
					"kultivator.cerita.adegan.tema_pelabuhan_dewi.sub", "媽", 0xF0C860, Gerak.ORBIT, 4.0F,
					new Bunyi[] {new Bunyi(SoundEvents.BELL_BLOCK, 0, 0.9F), new Bunyi(SoundEvents.CONDUIT_ACTIVATE, 14, 1.2F),
							new Bunyi(SoundEvents.FIREWORK_ROCKET_BLAST, 36, 1.1F)}, ParticleTypes.END_ROD)),
			Map.entry("tema_pelabuhan_bajak", new Resep("kultivator.cerita.adegan.tema_pelabuhan_bajak",
					"kultivator.cerita.adegan.tema_pelabuhan_bajak.sub", "賊", 0xC03028, Gerak.PULLBACK, 3.0F,
					new Bunyi[] {new Bunyi(SoundEvents.WOOD_HIT, 0, 0.5F), new Bunyi(SoundEvents.WOOD_HIT, 8, 0.5F), new Bunyi(SoundEvents.WOOD_HIT, 16, 0.5F),
							new Bunyi(SoundEvents.GENERIC_SPLASH, 30, 0.6F)}, ParticleTypes.SPLASH)),
			Map.entry("tema_pelabuhan_heisha", new Resep("kultivator.cerita.adegan.tema_pelabuhan_heisha",
					"kultivator.cerita.adegan.tema_pelabuhan_heisha.sub", "鯊", 0x3A4A5C, Gerak.ORBIT, 3.0F,
					new Bunyi[] {new Bunyi(SoundEvents.ELDER_GUARDIAN_CURSE, 0, 1.4F), new Bunyi(SoundEvents.GENERIC_SPLASH, 18, 0.7F)},
					ParticleTypes.LARGE_SMOKE)),
			Map.entry("tema_pelabuhan_pelita", new Resep("kultivator.cerita.adegan.tema_pelabuhan_pelita",
					"kultivator.cerita.adegan.tema_pelabuhan_pelita.sub", "燈", 0xFFE08A, Gerak.ORBIT, 3.5F,
					new Bunyi[] {new Bunyi(SoundEvents.FIRECHARGE_USE, 0, 0.8F), new Bunyi(SoundEvents.BEACON_ACTIVATE, 12, 1.3F),
							new Bunyi(SoundEvents.BELL_BLOCK, 30, 0.7F)}, ParticleTypes.FLAME)));

	static void terima(AdeganPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		Resep resep = RESEP.get(payload.adegan());
		Component title = resep == null ? Component.translatable("kultivator.cerita.adegan." + payload.adegan())
				: Component.translatable(resep.judul, payload.a());
		Component sub = resep == null || resep.sub == null ? null : Component.translatable(resep.sub, payload.a());
		String seal = resep == null ? "話" : resep.segel;
		int rgb = resep == null ? 0xE8C46A : resep.warna;
		Entity subject = payload.subjek() >= 0 ? level.getEntity(payload.subjek()) : null;
		Vec3 at = subject != null ? subject.position() : payload.pos();
		boolean cardOnly = (payload.flags() & AdeganPayload.KARTU_SAJA) != 0 || resep != null && resep.gerak == Gerak.NONE;
		Scene scene = cardOnly ? null : DuniaScenes.world("cerita/" + payload.adegan(), at, payload.adegan().hashCode() ^ payload.a(), 90, true);
		if (scene == null) {
			TitleCard.show(TitleCard.stamp(title, seal, rgb));
			if (resep != null) {
				for (Bunyi b : resep.bunyi) {
					if (b.tunda == 0) {
						level.playLocalSound(at.x, at.y, at.z, b.suara, SoundSource.AMBIENT, 0.8F, b.nada, false);
					}
				}
				percik(level, at, resep.partikel);
			}
			return;
		}
		Resep r = resep;
		if (r != null && r.gerak == Gerak.PULLBACK) {
			DuniaScenes.pullback(scene, 4, 2.5F, 7.0F, 1.6F, 3.0F, r.detik);
		} else {
			DuniaScenes.orbit(scene, 4, 5.5F, 1.8F, 70.0F, r == null ? 3.0F : r.detik);
		}
		DuniaScenes.card(scene, 10, title, sub, seal, rgb);
		if (r != null) {
			for (Bunyi b : r.bunyi) {
				scene.at(b.tunda, () -> level.playLocalSound(at.x, at.y, at.z, b.suara, SoundSource.AMBIENT, 0.8F, b.nada, false));
			}
			scene.at(12, () -> percik(level, at, r.partikel));
		}
	}

	/** A small sparkle around the scene, scaled by EffectQuality (Minimal: none). */
	private static void percik(ClientLevel level, Vec3 at, @Nullable ParticleOptions particle) {
		if (particle == null || EffectQuality.level() == EffectQuality.Level.MINIMAL) {
			return;
		}
		int count = EffectQuality.level() == EffectQuality.Level.NORMAL ? 14 : 28;
		RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 1.0D + random.nextDouble() * 2.5D;
			level.addParticle(particle, at.x + Math.cos(angle) * r, at.y + 0.6D + random.nextDouble() * 1.8D, at.z + Math.sin(angle) * r,
					0.0D, 0.03D + random.nextDouble() * 0.04D, 0.0D);
		}
	}

	private AdeganCerita() {
	}
}
