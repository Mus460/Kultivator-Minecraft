package dev.kultivator.disciple.path;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Lampu Jiwa, the soul lamps after Penginjak Langit (DESIGN 20.3.19). Sumber: <i>I Shall Seal the Heavens</i>, ch.
 * ~1276-1362: Soul Lamps, about one per five Immortal meridians; extinguishing a lamp absorbs its clone and greatly raises
 * power. The bonus numbers are game design.
 * <p>
 * One lamp lights per 10 stars (at most 33) once all seven calamities are passed. "Padamkan Lampu" is a trial of the
 * calamity engine with {@code k = 8 + lamps already out}; each lamp put out gives +10% range to ultimates and Jurus
 * Pemusnah and +5% damage.
 */
public final class SoulLamps {

	public static final int STARS_PER_LAMP = 10;
	public static final int MAX_LAMPS = 33;
	/** A lamp trial is a calamity with k = 8 + lamps out. */
	public static final int LAMP_BASE_K = 8;
	public static final double RANGE_PER_LAMP = 0.10D;
	public static final double DAMAGE_PER_LAMP = 0.05D;
	/** The server hears of every ninth lamp put out. */
	public static final int ANNOUNCE_EVERY = 9;

	static void register() {
		DiscipleHooks.addDamageFactor(SoulLamps::damage);
		// Module C's ultimates reach further too (+10% per lamp out).
		dev.kultivator.disciple.power.DisciplePowers.addUltimateRangeFactor(entity -> rangeFactor(entity.data()));
	}

	/** True once all seven calamities are passed (Penginjak Langit). */
	public static boolean unlocked(DiscipleData data) {
		return DisciplePaths.calamitiesPassed(data) >= Calamity.COUNT;
	}

	/** Lamps lit: one per 10 stars, at most 33 (0 before Penginjak Langit). */
	public static int lit(DiscipleData data) {
		return unlocked(data) ? Math.min(MAX_LAMPS, DisciplePaths.stars(data) / STARS_PER_LAMP) : 0;
	}

	/** Lamps already put out. */
	public static int out(DiscipleData data) {
		return Math.max(0, Math.min(MAX_LAMPS, DisciplePaths.tag(data).getIntOr("lamps_out", 0)));
	}

	/** Range factor of ultimates and Jurus Pemusnah: +10% per lamp out (also read by module C). */
	public static double rangeFactor(DiscipleData data) {
		return 1.0D + RANGE_PER_LAMP * out(data);
	}

	/** Why no lamp can be put out now (a translation key), or null. */
	static @Nullable String blocker(DiscipleData data) {
		if (!unlocked(data)) {
			return DisciplePaths.PREFIX + "why.lamp_locked";
		}
		if (lit(data) <= out(data)) {
			return DisciplePaths.PREFIX + "why.lamp_none";
		}
		return null;
	}

	/** The "Padamkan Lampu" talisman. */
	static void start(DiscipleEntity entity, ServerPlayer master) {
		String why = blocker(entity.data());
		if (why != null) {
			Msg.chat(master, Component.translatable(why, DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		Calamity.start(entity, master, false, true);
	}

	/** A lamp trial passed: one more lamp out. */
	static void onPassed(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		int out = out(data) + 1;
		DisciplePaths.section(data).putInt("lamps_out", out);
		Component name = DisciplePaths.name(entity);
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.LAMP, out, 1.0F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.7F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.4F);
			if (out % ANNOUNCE_EVERY == 0) {
				Msg.broadcast(level.getServer(), Component.translatable(DisciplePaths.PREFIX + "lamp.announce", name, out).withStyle(ChatFormatting.GOLD));
			}
		}
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "lamp.pass", name, out, MAX_LAMPS,
					Math.round(RANGE_PER_LAMP * 100.0D * out), Math.round(DAMAGE_PER_LAMP * 100.0D * out)).withColor(DisciplePaths.GOLD));
			Advancements.grant(master, "soul_lamp");
		}
		entity.refreshSynced();
	}

	/** A new lamp lights at every tenth star after Penginjak Langit. */
	static void onStar(DiscipleEntity entity, int star) {
		DiscipleData data = entity.data();
		if (!unlocked(data) || star % STARS_PER_LAMP != 0 || star / STARS_PER_LAMP > MAX_LAMPS) {
			return;
		}
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "lamp.lit", DisciplePaths.name(entity), star / STARS_PER_LAMP)
					.withColor(0x9FF3E6));
		}
	}

	private static double damage(DiscipleEntity entity, LivingEntity target, boolean technique) {
		int out = out(entity.data());
		return out <= 0 ? 1.0D : 1.0D + DAMAGE_PER_LAMP * out;
	}

	private SoulLamps() {
	}
}
