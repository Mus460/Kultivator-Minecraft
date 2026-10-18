package dev.kultivator.disciple.power;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Pelarian Jiwa Baru Lahir, the Nascent Soul's escape (DESIGN 20.3.17; Fanren: a Nascent Soul flees as a streak of light
 * when its body is destroyed). A Nascent Soul+ disciple that dies while its master's hall is active leaves no tablet: a
 * soul streak flies to the stele, {@code Halls.storeAway(..., "soul_flight")} keeps it for 12,000 ticks, and it re-forms
 * at the stele with -10% bar and full health. Below Nascent Soul, or without an active hall, the Soul Tablet of phase 5
 * applies. Registered last (at server start), so the other modules' one-a-day rescues come first.
 */
final class SoulFlight {

	public static final String REASON = "soul_flight";
	public static final int AWAY_TICKS = 12000;
	public static final double BAR_LOSS = 0.1D;

	private static boolean registered;

	/** Called at server start: the soul's flight is the last death handler. */
	static void registerLate() {
		if (registered) {
			return;
		}
		registered = true;
		DiscipleHooks.addOnDeath(SoulFlight::onDeath);
	}

	static void register() {
		Halls.addReturnHandler(REASON, (level, owner, stored, info) -> {
			// It re-forms with a tenth of a bar less and full health (arrive clamps the health to the maximum).
			CompoundTag soul = stored.getCompoundOrEmpty("disciple");
			if (!soul.isEmpty()) {
				DiscipleData data = DiscipleData.load(soul);
				if (data.rank().isMax()) {
					// True Immortal Peak: the bar is the next star's Stored Qi, so the loss comes from there.
					data.setStoredQi(Math.max(0.0D, data.storedQi() - DisciplePaths.barSize(data) * BAR_LOSS));
				} else {
					data.setXp(Math.max(0.0D, data.xp() - data.xpRequired() * BAR_LOSS));
				}
				stored.put("disciple", data.save());
			}
			stored.putFloat("health", 1.0E6F);
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall != null && hall.active() && hall.dim().equals(level.dimension())) {
				Vec3 at = Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.0D, 0.0D);
				PowerCore.fx(level, at, PowerIds.FX_SOUL_RETURN, 0, 0.0F);
				PowerCore.sound(level, at, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 1.4F);
			}
			return null;
		});
	}

	private static boolean onDeath(DiscipleEntity entity, DamageSource source) {
		DiscipleData data = entity.data();
		UUID owner = entity.ownerId();
		if (owner == null || !data.realm().atLeast(Realm.NASCENT_SOUL) || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active()) {
			return false;
		}
		Vec3 from = entity.position().add(0.0D, 1.0D, 0.0D);
		Vec3 to = hall.dim().equals(level.dimension()) ? Vec3.atBottomCenterOf(hall.pos()).add(0.0D, 1.5D, 0.0D) : from.add(0.0D, 24.0D, 0.0D);
		PowerCore.fx(level, from, to, PowerIds.FX_SOUL_FLIGHT, 0, 0.0F);
		dev.kultivator.epic.EpicHooks.discipleSave(entity, entity.owner(), dev.kultivator.epic.EpicIds.SAVE_SOUL_FLIGHT);
		PowerCore.sound(level, from, SoundEvents.SOUL_ESCAPE.value(), 1.5F, 1.2F);
		PowerCore.sound(level, from, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
		PowerCore.tell(entity, PowerCore.key("soul_flight.fled", PowerCore.name(entity), AWAY_TICKS / 1200).withStyle(ChatFormatting.AQUA));
		PowerCore.tell(entity, PowerCore.key("soul_flight.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		entity.setHealth(1.0F);
		Halls.storeAway(owner, entity, level.getGameTime() + AWAY_TICKS, REASON, new CompoundTag());
		return true;
	}

	private SoulFlight() {
	}
}
