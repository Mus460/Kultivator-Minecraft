package dev.kultivator.disciple.power;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Seni Sekte yang Hilang in effect (DESIGN 20.3.8; restored by module A, hall-wide for disciples of rank Dalam and up):
 * <ul>
 * <li>Pedang Dua Puluh Empat Bunga Plum (Return of the Mount Hua Sect): every 4th melee or Sword-technique hit sets off
 * a petal storm (r 4, 1.2 U); at rank Inti every 24th hit is "Mekar Penuh" (24 petals, r 6, 2 U). The revealed beggar
 * has it innately.</li>
 * <li>Seni Ilahi Kabut Ungu: qi regeneration x1.3, max qi +20% and purple mist while meditating.</li>
 * <li>Warisan Sekte Langit Utara (Legend of the Northern Blade): +20% damage at night and in snowy biomes.</li>
 * </ul>
 */
final class SectArts {

	public static final int ART_PLUM = 1;
	public static final int ART_PURPLE_MIST = 2;
	public static final int ART_NORTHERN = 4;
	/** Dalam (Inner) and above share the restored arts. */
	public static final int MIN_RANK = 2;
	public static final int INTI = 3;
	public static final int PETAL_EVERY = 4;
	public static final double PETAL_RADIUS = 4.0D;
	public static final double PETAL_U = 1.2D;
	public static final int FULL_BLOOM_EVERY = 24;
	public static final double FULL_BLOOM_RADIUS = 6.0D;
	public static final double FULL_BLOOM_U = 2.0D;
	public static final double MIST_REGEN = 0.3D;
	public static final double MIST_MAX_QI = 1.2D;
	/** {@code extra.power.mist}: the Purple Mist was in effect at the last check (max qi only sees the data). */
	private static final String MIST_KEY = "mist";
	public static final double NORTHERN_DAMAGE = 1.2D;

	static void register() {
		DiscipleHooks.addDamageFactor((entity, target, technique) -> PowerCore.powered(entity)
				&& (arts(entity) & ART_NORTHERN) != 0 && nightOrSnow(entity.level(), entity.blockPosition()) ? NORTHERN_DAMAGE : 1.0D);
		DiscipleHooks.addMaxQiFactor(data -> PowerCore.tag(data).getBooleanOr(MIST_KEY, false) ? MIST_MAX_QI : 1.0D);
	}

	/** The restored arts this disciple may use (rank Dalam+; a revealed beggar always has the Plum Blossom). */
	static int arts(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		int arts = 0;
		UUID owner = entity.ownerId();
		if (owner != null && SectRanks.rank(data) >= MIN_RANK) {
			arts = Halls.arts(owner);
		}
		if (SpecialCandidates.revealedBeggar(data)) {
			arts |= ART_PLUM;
		}
		return arts;
	}

	private static boolean nightOrSnow(Level level, BlockPos pos) {
		return level.isDarkOutside() || level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** After a melee or technique hit: count the Plum Blossom hits and loose the petals. */
	static void onHit(DiscipleEntity entity, LivingEntity target, boolean technique) {
		if (technique && entity.data().dao() != Dao.SWORD || (arts(entity) & ART_PLUM) == 0
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		DisciplePowers.Live live = DisciplePowers.live(entity);
		live.plumHits++;
		boolean fullBloom = SectRanks.rank(entity.data()) >= INTI && live.plumHits % FULL_BLOOM_EVERY == 0;
		if (!fullBloom && live.plumHits % PETAL_EVERY != 0) {
			return;
		}
		ServerPlayer master = entity.owner();
		if (master == null) {
			return;
		}
		double radius = fullBloom ? FULL_BLOOM_RADIUS : PETAL_RADIUS;
		double amount = DiscipleTechniques.power(entity.data()) * (fullBloom ? FULL_BLOOM_U : PETAL_U);
		for (LivingEntity other : PowerCore.hostiles(entity, master, target.position(), radius)) {
			PowerCore.hurt(entity, master, other, amount, PowerCore.HIT_AREA);
		}
		PowerCore.fx(level, target.position().add(0.0D, 1.0D, 0.0D), PowerIds.FX_PETALS, fullBloom ? 24 : 8, (float) radius);
		PowerCore.sound(level, target.position(), SoundEvents.CHERRY_LEAVES_BREAK, 1.0F, fullBloom ? 0.6F : 1.2F);
		PowerCore.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, fullBloom ? 0.7F : 1.3F);
	}

	/** Once a second: remembers whether the Purple Mist is in effect (for max qi) and adds its extra qi regeneration. */
	static void tick(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		boolean mist = (arts(entity) & ART_PURPLE_MIST) != 0;
		if (mist != PowerCore.tag(data).getBooleanOr(MIST_KEY, false)) {
			if (mist) {
				PowerCore.edit(data).putBoolean(MIST_KEY, true);
			} else {
				PowerCore.edit(data).remove(MIST_KEY);
				data.setQi(Math.min(data.qi(), DiscipleCultivation.maxQi(data)));
			}
		}
		if (!mist) {
			return;
		}
		double max = DiscipleCultivation.maxQi(data);
		if (data.qi() < max) {
			data.setQi(Math.min(max, data.qi() + DiscipleCultivation.regenPerSecond(data, entity.isMeditatingNow()) * MIST_REGEN));
		}
	}

	private SectArts() {
	}
}
