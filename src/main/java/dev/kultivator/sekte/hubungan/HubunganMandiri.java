package dev.kultivator.sekte.hubungan;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The bridge of Murid Mandiri (DESIGN 39D, 39.4.2 row SOSIAL/TEH) to module R's package-private tea turns: a disciple
 * that chooses tea itself invites a free hall-mate and the two sit down through {@link GiliranSosial#forceTea}, with the
 * old seats, cups, bubbles, voice line and the old affinity change at the end ({@code Hubungan.change} with traits,
 * memories and the meeting count). Sworn enemies ({@code DENDAM}) never sit down together, as in R's own turn. A new file
 * in the old package (rule 39.0.7); nothing old changes.
 */
public final class HubunganMandiri {

	/** True while module R runs. */
	public static boolean aktif() {
		return Hubungan.active();
	}

	/**
	 * True when {@code d} could sit down for tea now apart from what it is doing (the caller ends its own planner activity
	 * first): a real disciple, not fighting, hurt, below half health, breaking through, deviating, duelling, defiant,
	 * releasing, riding or already at a tea or a rite.
	 */
	public static boolean bebas(DiscipleEntity d) {
		return d != null && d.isAlive() && !d.isRemoved() && d.role() == DiscipleEntity.Role.DISCIPLE && d.getTarget() == null
				&& !d.isReleasing() && !d.isPassenger() && d.hurtTime <= 0 && d.getHealth() >= d.getMaxHealth() * 0.5F
				&& !d.data().breakingThrough() && !d.data().deviating() && DuelSessions.byEntity(d) == null && !DuelRules.inDuel(d)
				&& !SekteApi.hati().defiant(d.data()) && !GiliranSosial.inSession(d);
	}

	/** True when the two would sit together: same level, within R's meeting reach x2, no grudge, met under twice today. */
	public static boolean cocok(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		if (a == null || b == null || a == b || a.level() != b.level()) {
			return false;
		}
		double reach = HubunganConfig.get().jarakTemu * 2.0D;
		if (a.distanceToSqr(b) > reach * reach) {
			return false;
		}
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a.soulId(), b.soulId());
		return pair == null || pair.kind() != Ikatan.DENDAM && pair.meetingsToday(Hubungan.today()) < 2;
	}

	/** Starts the tea of the two now (R's own session); null when it began, else why not. */
	public static @Nullable Component minum(UUID owner, DiscipleEntity a, DiscipleEntity b) {
		if (!aktif()) {
			return Component.translatable("kultivator.sekte.hubungan.alasan.tidak_ada");
		}
		return GiliranSosial.forceTea(owner, a, b);
	}

	/** True while {@code d} sits at (or walks to) a tea or a rite of R. */
	public static boolean diMeja(DiscipleEntity d) {
		return d != null && GiliranSosial.inSession(d);
	}

	/** The tea length of R's config in ticks (the time seated, after the walk to the seats). */
	public static int lamaTick() {
		return HubunganConfig.get().lamaTehDetik * 20;
	}

	/** R's seat check: a disciple this close to its seat counts as seated (blocks). */
	public static double jarakDuduk() {
		return 1.6D;
	}

	private HubunganMandiri() {
	}
}
