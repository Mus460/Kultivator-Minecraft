package dev.kultivator.sect.event;

import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utus Perekrut, the envoy (DESIGN 20.3.10): one Outer+ disciple at Qi Refining Middle or higher travels for a day,
 * once per 5 in-game days per hall (the wait can be cut for 300 hall PK), and brings back 1-3 youths as Sect Hall
 * candidates through {@code Halls.addCandidates}.
 * <p>
 * Sumber: I Shall Seal the Heavens ch. 19 (each April, Outer disciples above the fifth level go out and each brings back
 * two or three talented youths).
 */
final class Envoys {

	public static final long AWAY_TICKS = 24000L;
	public static final int COOLDOWN_DAYS = 5;
	public static final int RESET_PRICE = 300;
	public static final int ENVOY_PK = 40;
	public static final double ENVOY_SHARE = 0.10D;
	/** Youths brought: 1 (30%), 2 (55%), 3 (15%). */
	public static final double ONE = 0.30D;
	public static final double TWO = 0.55D;
	public static final double GRADE_UP = 0.30D;
	public static final double SHARE_ELEMENT = 0.50D;
	public static final double TALENT_CHANCE = 0.50D;

	/**
	 * The hall's {@code core} section, where DESIGN 20.4.6 keeps {@code envoy_day} (module A's Paviliun item "reset the
	 * envoy cooldown" clears it there).
	 */
	private static CompoundTag core(UUID owner) {
		return Halls.section(owner, "core");
	}

	/** The day index from which the next envoy may leave. */
	static long readyDay(UUID owner) {
		CompoundTag core = core(owner);
		return core.contains("envoy_day") ? core.getLongOr("envoy_day", 0L) + COOLDOWN_DAYS : Long.MIN_VALUE;
	}

	static @Nullable String blocker(DiscipleEntity disciple) {
		if (Expeditions.refuses(disciple)) {
			return Expeditions.REFUSES;
		}
		DiscipleData data = disciple.data();
		if (SectRanks.rank(data) < 1) {
			return "kultivator.sekte_sambung.bakat.perlu_luar";
		}
		boolean middle = data.realm().ordinal() > Realm.QI_REFINING.ordinal() || data.realm() == Realm.QI_REFINING && data.stage() >= 1;
		return middle ? null : "kultivator.sekte_sambung.bakat.perlu_menengah";
	}

	static boolean send(ServerPlayer player, DiscipleEntity disciple) {
		UUID owner = player.getUUID();
		if (EventUtil.day() < readyDay(owner)) {
			EventUtil.chat(player, Component.translatable("kultivator.sekte_sambung.bakat.tunggu", readyDay(owner)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		String why = blocker(disciple);
		if (why != null) {
			EventUtil.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Component name = disciple.getDisplayName();
		if (AwayTeams.depart(player, AwayTeams.KIND_ENVOY, List.of(disciple), AWAY_TICKS, new CompoundTag()) == null) {
			return false;
		}
		core(owner).putLong("envoy_day", EventUtil.day());
		EventUtil.dirty();
		EventUtil.chat(player, Component.translatable("kultivator.sekte_sambung.bakat.berangkat", name).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Pays 300 PK from the hall pool to send the next envoy at once (the Paviliun's "reset" price). */
	static boolean reset(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (EventUtil.day() >= readyDay(owner)) {
			return false;
		}
		if (!Halls.spendPool(owner, RESET_PRICE)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.envoy.reset_poor", RESET_PRICE).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		resetCooldown(owner);
		EventUtil.chat(player, Component.translatable("kultivator.sekte_sambung.bakat.percepat_siap").withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Clears the envoy wait (also for module A's Paviliun item). */
	static void resetCooldown(UUID owner) {
		core(owner).remove("envoy_day");
		EventUtil.dirty();
	}

	static void resolve(UUID owner, CompoundTag team, @Nullable ServerPlayer player, RandomSource random) {
		for (Tag tag : team.getListOrEmpty("members")) {
			if (!(tag instanceof CompoundTag member)) {
				continue;
			}
			DiscipleData envoy = AwayTeams.data(member);
			SectRanks.addPk(envoy, ENVOY_PK, "kultivator.sectevent.pk.envoy");
			DisciplePaths.grantBarShare(envoy, ENVOY_SHARE);
			AwayTeams.setData(member, envoy);
			double roll = random.nextDouble();
			int count = roll < ONE ? 1 : roll < ONE + TWO ? 2 : 3;
			List<Element> elements = Element.fromMask(envoy.rootElements());
			Element main = elements.isEmpty() ? null : elements.get(0);
			List<Halls.CandidateSpec> specs = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				DiscipleData youth = DiscipleData.fresh(random);
				if (random.nextDouble() < GRADE_UP) {
					youth.setRootGrade(youth.rootGrade().up());
				}
				if (main != null && random.nextDouble() < SHARE_ELEMENT) {
					// The youth's main element becomes the envoy's (the element count stays).
					List<Element> own = Element.fromMask(youth.rootElements());
					int mask = youth.rootElements();
					if (!own.isEmpty()) {
						mask &= ~own.get(0).bit();
					}
					youth.setRootElements(mask | main.bit());
				}
				specs.add(new Halls.CandidateSpec(youth, -1, TALENT_CHANCE, -1.0D, false, "envoy"));
			}
			Halls.addCandidates(owner, specs);
			EventUtil.chat(player, Component.translatable("kultivator.sekte_sambung.bakat.pulang", AwayTeams.name(member), count).withStyle(ChatFormatting.GOLD));
			Halls.log(owner, Component.translatable("kultivator.sectevent.log.envoy", AwayTeams.name(member), count));
		}
	}

	private Envoys() {
	}
}
