package dev.kultivator.client.fx;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The basic aura of every cultivator from Qi Refining up (polished by P4-visuals): faint motes of the realm's colour
 * rising around the body, more and brighter with realm and stage; from Golden Core three golden motes slowly orbit the
 * waist; from Nascent Soul end-rod sparkles join at Maximal quality; an ascended immortal's motes shift through the
 * rainbow. Shown for players whose synced look has the aura on, within 48 blocks, never for invisible players, and
 * not for yourself in first person. Everything is scaled by Effect Quality (Minimal: a mote every few ticks).
 */
public final class AuraFx {

	private static final double RANGE_SQ = 48.0D * 48.0D;
	private static final int ORBIT_GOLD = 0xFFD54A;

	/** Every client tick: aura particles around every visible player whose VisualState shows the aura. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		long time = level.getGameTime();
		Vec3 eye = minecraft.player.position();
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isRemoved() || player.isInvisible() || player.isSpectator()) {
				continue;
			}
			if (player == minecraft.player && firstPerson) {
				continue;
			}
			if (player.distanceToSqr(eye) > RANGE_SQ) {
				continue;
			}
			VisualState visual = ClientCultivation.visual(player);
			if (!visual.has(VisualState.SHOW_AURA) || visual.realm() < Realm.QI_REFINING.ordinal()) {
				continue;
			}
			aura(player, visual, random, time);
		}
	}

	private static void aura(AbstractClientPlayer player, VisualState visual, RandomSource random, long time) {
		Realm realm = visual.realmEnum();
		int tier = realm.ordinal();
		int stage = visual.stage();
		boolean ascended = visual.has(VisualState.ASCENDED);
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		float height = player.getBbHeight();
		float width = player.getBbWidth();
		// Higher realms and later stages burn brighter: blend towards white a little.
		float brightness = Math.min(0.45F, 0.04F * tier + 0.03F * stage);
		int phase = player.getId() * 37;

		// Rising motes: about 0.25 per tick at Qi Refining Early, over 1.5 at the top (Normal quality).
		float rate = 0.2F + 0.11F * tier + 0.05F * stage + 0.02F * visual.layer();
		int whole = (int) rate;
		int count = whole + (EffectQuality.chance(random, rate - whole) ? 1 : 0);
		if (EffectQuality.minimal()) {
			count = EffectQuality.chance(random, rate) ? 1 : 0;
		} else if (EffectQuality.maximal()) {
			count = Math.round(count * 1.6F);
		}
		float size = 0.45F + 0.07F * tier + 0.04F * stage;
		for (int i = 0; i < count; i++) {
			int rgb = ascended ? rainbow(time + phase + i * 11L, random) : lighten(realm.color(), brightness + random.nextFloat() * 0.15F);
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = width * (0.55D + random.nextDouble() * 0.45D);
			double px = x + Math.cos(angle) * radius;
			double pz = z + Math.sin(angle) * radius;
			double py = y + random.nextDouble() * height * 0.95D;
			CommonFx.add(CommonFx.dust(rgb, size * (0.8F + random.nextFloat() * 0.4F)), new Vec3(px, py, pz),
					new Vec3(0.0D, 0.02D + 0.004D * tier, 0.0D), false);
		}

		// Golden Core and above: three golden motes circling the waist.
		if (tier >= Realm.GOLDEN_CORE.ordinal()) {
			int every = EffectQuality.minimal() ? 6 : EffectQuality.maximal() ? 1 : 2;
			if ((time + phase) % every == 0) {
				double spin = (time + phase) * 0.07D;
				double orbit = width * 1.25D + 0.1D * Math.min(3, tier - Realm.GOLDEN_CORE.ordinal());
				double waist = y + height * 0.5D;
				for (int i = 0; i < 3; i++) {
					double angle = spin + i * Math.PI * 2.0D / 3.0D;
					int rgb = ascended ? rainbow(time + phase + i * 40L, random) : ORBIT_GOLD;
					double bob = Math.sin(spin * 1.7D + i) * 0.08D;
					CommonFx.add(CommonFx.dust(rgb, 0.9F + 0.05F * stage), new Vec3(x + Math.cos(angle) * orbit, waist + bob, z + Math.sin(angle) * orbit),
							Vec3.ZERO, false);
				}
			}
		}

		// Nascent Soul and above at Maximal: end-rod sparkles drifting up.
		if (tier >= Realm.NASCENT_SOUL.ordinal() && EffectQuality.maximal() && random.nextFloat() < 0.08F + 0.02F * (tier - Realm.NASCENT_SOUL.ordinal())) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(x + Math.cos(angle) * width * 0.9D, y + random.nextDouble() * height, z + Math.sin(angle) * width * 0.9D),
					new Vec3(0.0D, 0.015D + random.nextDouble() * 0.02D, 0.0D), false);
		}
	}

	/** A slowly shifting rainbow colour. */
	private static int rainbow(long time, RandomSource random) {
		float hue = ((time % 240L) / 240.0F + random.nextFloat() * 0.08F) % 1.0F;
		return Mth.hsvToRgb(hue, 0.7F, 1.0F);
	}

	private static int lighten(int rgb, float amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	private AuraFx() {
	}
}
