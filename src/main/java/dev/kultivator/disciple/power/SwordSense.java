package dev.kultivator.disciple.power;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Indra Pedang, the Sword Sense talent (DESIGN 20.3.5; Absolute Sword Sense: Soh Woonhwi hears weapons and reads their
 * wielders' styles). Against monsters holding a sword: 30% dodge of their melee and x1.5 comprehension from killing them;
 * after 20 such kills, +10% sword damage (melee, and the techniques of a Sword-Dao disciple).
 */
final class SwordSense {

	public static final double DODGE = 0.3D;
	public static final double KILL_INSIGHT = 1.5D;
	public static final int MASTERY_KILLS = 20;
	public static final double MASTERY_DAMAGE = 1.1D;

	static void register() {
		DiscipleHooks.addOnKill((entity, victim) -> {
			DiscipleData data = entity.data();
			if (!PowerCore.talent(data, PowerIds.TALENT_SWORD_SENSE) || !(victim instanceof Enemy) || !holdsSword(victim)) {
				return;
			}
			int kills = PowerCore.tag(data).getIntOr("sword_kills", 0) + 1;
			PowerCore.edit(data).putInt("sword_kills", kills);
			Dao dao = data.dao();
			if (dao != null) {
				// The phase-5 kill insight once more at half: x1.5 in all.
				data.addComprehension(DiscipleCultivation.comprehensionGain(data, dao, DiscipleCultivation.KILL_COMPREHENSION * (KILL_INSIGHT - 1.0D)));
			}
			if (kills == MASTERY_KILLS) {
				PowerCore.tell(entity, PowerCore.key("sword_sense.mastery", PowerCore.name(entity)).withColor(0xD8E6FF));
			}
		});
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (!PowerCore.powered(entity)) {
				return 1.0D;
			}
			DiscipleData data = entity.data();
			if (!PowerCore.talent(data, PowerIds.TALENT_SWORD_SENSE) || PowerCore.tag(data).getIntOr("sword_kills", 0) < MASTERY_KILLS) {
				return 1.0D;
			}
			return !technique || data.dao() == Dao.SWORD ? MASTERY_DAMAGE : 1.0D;
		});
	}

	static boolean holdsSword(LivingEntity entity) {
		return entity.getMainHandItem().is(ItemTags.SWORDS);
	}

	/** True when the disciple reads the blow of a sword-holding monster and steps aside (30%). */
	static boolean dodges(DiscipleEntity entity, DamageSource source) {
		if (!PowerCore.talent(entity.data(), PowerIds.TALENT_SWORD_SENSE)) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity living) || attacker != source.getDirectEntity() || !(attacker instanceof Enemy)
				|| !holdsSword(living)) {
			return false;
		}
		if (entity.getRandom().nextDouble() >= DODGE) {
			return false;
		}
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_DODGE, 1, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.4F);
		}
		return true;
	}

	private SwordSense() {
	}
}
