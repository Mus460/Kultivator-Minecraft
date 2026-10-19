package dev.kultivator.tempa;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.elite.BattleFatigue;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Roh Senjata, the weapon spirit of the natal treasure (DESIGN 25.5.7). Stage 1 Benih Roh (grade Langit+, a
 * {@code #spirit_seed} fed and 100 years), 2 Roh Dasar (400 years naturally, or at once through a Jiwa Binatang: a Raja
 * core and a seed, Roh Buatan), 3 Roh Lengkap (natural only: 1,000 years, grade Abadi+ and tempered by a tribulation the
 * owner passed while it was bound). Years grow with meditation, technique kills of trial mobs, rogues and bosses (at most
 * 50 a day) and fed cores of its element. Sumber: Martial World ch. 1128, 1132; Martial Peak (Terinspirasi: artificial
 * spirits are lesser). Every number is game design.
 */
public final class WeaponSpirit {

	public static final int SEED_GRADE = 3;
	public static final int COMPLETE_GRADE = 4;
	public static final double SEED_YEARS = 100.0D;
	public static final double ELEMENTARY_YEARS = 400.0D;
	public static final double COMPLETE_YEARS = 1000.0D;
	/** Years per second of meditation (1 per minute). */
	public static final double MEDITATION_YEARS = 1.0D / 60.0D;
	public static final double KILL_YEARS = 0.5D;
	public static final double KILL_DAY_CAP = 50.0D;
	/** Years from a fed core by grade (index = core grade 1 Low … 4 Raja). */
	public static final double[] FEED_YEARS = {0.0D, 5.0D, 15.0D, 50.0D, 150.0D};
	public static final int FEED_COOLDOWN = 200;
	public static final int MAX_YEARS = 1_000_000;

	static void init() {
		Tribulation.addSuccessListener((player, info) -> {
			if (!info.test() && NatalTreasureSelf.bound(player)) {
				NatalTreasureSelf.setTempered(player, true);
				Msg.chat(player, Component.translatable("kultivator.tempa.spirit.tempered").withStyle(ChatFormatting.GOLD));
				CompoundTag art = NatalTreasureSelf.record(player);
				if (art != null) {
					advance(player, art.copy());
				}
			}
		});
	}

	// ================================================================== years

	/** Every 20 ticks: meditation years and natural stage changes. */
	static void tick(ServerPlayer player) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null || !Meditation.isMeditating(player)) {
			return;
		}
		CompoundTag art = live.copy();
		addYears(art, MEDITATION_YEARS);
		NatalTreasureSelf.update(player, art);
		if (player.tickCount % 1200 == 0) {
			advance(player, art);
		}
	}

	/** A technique kill by the player: +0.5 year for a trial mob, rogue or boss, under Battle Fatigue, at most 50 a day. */
	static void onKill(ServerPlayer player, LivingEntity victim) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null || !(TechniquePower.isTrialMob(victim) || TechniquePower.isBoss(victim))) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		CompoundTag dunia = NatalTreasureSelf.dunia(data);
		long day = SpiritHerbs.day(player.level());
		double today = dunia.getLongOr("spirit_day", -1L) == day ? dunia.getDoubleOr("spirit_kills", 0.0D) : 0.0D;
		double gain = Math.min(KILL_DAY_CAP - today, KILL_YEARS * BattleFatigue.factor(player));
		if (gain <= 0.0D) {
			return;
		}
		dunia.putLong("spirit_day", day);
		dunia.putDouble("spirit_kills", today + gain);
		CompoundTag art = live.copy();
		addYears(art, gain);
		NatalTreasureSelf.update(player, art);
		advance(player, art);
	}

	private static void addYears(CompoundTag art, double years) {
		CompoundTag spirit = art.getCompoundOrEmpty("spirit").copy();
		spirit.putDouble("years", Math.min(MAX_YEARS, Math.max(0.0D, spirit.getDoubleOr("years", 0.0D) + years)));
		art.put("spirit", spirit);
	}

	private static void setStage(CompoundTag art, int stage, int kind) {
		CompoundTag spirit = art.getCompoundOrEmpty("spirit").copy();
		spirit.putInt("stage", Math.max(0, Math.min(3, stage)));
		spirit.putInt("kind", kind);
		art.put("spirit", spirit);
	}

	/** Natural stage changes for the record {@code art} (a copy the caller already saved or will save here). */
	static void advance(ServerPlayer player, CompoundTag art) {
		int stage = ArtifactItem.spiritStage(art);
		int kind = ArtifactItem.spiritKind(art);
		double years = ArtifactItem.spiritYears(art);
		int next = stage;
		if (stage == 1 && years >= ELEMENTARY_YEARS) {
			next = 2;
		}
		if (next == 2 && kind == 0 && years >= COMPLETE_YEARS && ArtifactItem.grade(art) >= COMPLETE_GRADE && NatalTreasureSelf.tempered(player)) {
			next = 3;
		}
		if (next != stage) {
			setStage(art, next, kind);
			NatalTreasureSelf.update(player, art);
			awaken(player, art, next);
		}
	}

	private static void awaken(ServerPlayer player, CompoundTag art, int stage) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.8F);
		Fx.send(player.level(), player.position().add(0.0D, 1.0D, 0.0D), FxType.TEMPA, TempaFxIds.SPIRIT_AWAKEN, stage, (float) ArtifactItem.tint(art));
		Msg.chat(player, Component.translatable("kultivator.tempa.spirit.awaken", ArtifactItem.spiritName(stage)).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (stage >= 2) {
			EpicHooks.extra(player.level(), EpicIds.EXTRA_SPIRIT_AWAKEN, player, player, player.position(), stage, ArtifactItem.grade(art));
			Advancements.grant(player, "spirit_elementary");
		}
		if (stage >= 3) {
			Advancements.grant(player, "spirit_complete");
		}
		Tempa.sync(player);
	}

	// ================================================================== forge actions (Roh tab)

	/** Feeds a beast core of the treasure's element: Low 5, Mid 15, High 50, Raja 150 years (10 s cooldown). */
	static @Nullable Component feed(ServerPlayer player, ItemStack core) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return Component.translatable("kultivator.tempa.natal.none");
		}
		if (!BeastCores.isCore(core)) {
			return Component.translatable("kultivator.tempa.spirit.need_core");
		}
		Element want = ArtifactItem.element(live);
		Element have = BeastCores.element(core);
		if (want != null && want != have) {
			return Component.translatable("kultivator.tempa.spirit.wrong_element", want.displayName());
		}
		CompoundTag dunia = NatalTreasureSelf.dunia(Cultivators.get(player));
		long now = player.level().getGameTime();
		if (now < dunia.getLongOr("spirit_feed", 0L)) {
			return Component.translatable("kultivator.tempa.spirit.feed_cd");
		}
		int grade = Math.max(1, Math.min(4, BeastCores.grade(core)));
		core.shrink(1);
		dunia.putLong("spirit_feed", now + FEED_COOLDOWN);
		CompoundTag art = live.copy();
		addYears(art, FEED_YEARS[grade]);
		NatalTreasureSelf.update(player, art);
		Msg.chat(player, Component.translatable("kultivator.tempa.spirit.fed", (int) FEED_YEARS[grade], (int) ArtifactItem.spiritYears(art))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		advance(player, art);
		Tempa.sync(player);
		return null;
	}

	/** Benih Roh: grade Langit+, 100 years, one {@code #spirit_seed} consumed. */
	static @Nullable Component seed(ServerPlayer player, ItemStack seed) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return Component.translatable("kultivator.tempa.natal.none");
		}
		if (ArtifactItem.spiritStage(live) >= 1) {
			return Component.translatable("kultivator.tempa.spirit.has_seed");
		}
		if (ArtifactItem.grade(live) < SEED_GRADE) {
			return Component.translatable("kultivator.tempa.spirit.grade", ArtifactItem.gradeName(SEED_GRADE));
		}
		if (ArtifactItem.spiritYears(live) < SEED_YEARS) {
			return Component.translatable("kultivator.tempa.spirit.years", (int) SEED_YEARS);
		}
		if (seed.isEmpty() || !seed.is(TempaItems.SPIRIT_SEED)) {
			return Component.translatable("kultivator.tempa.spirit.need_seed");
		}
		seed.shrink(1);
		CompoundTag art = live.copy();
		setStage(art, 1, 0);
		NatalTreasureSelf.update(player, art);
		awaken(player, art, 1);
		advance(player, art);
		return null;
	}

	/** Jiwa Binatang: a Raja core and a seed make a Roh Buatan of stage 2 at once (grade Langit+). */
	static @Nullable Component beastSoul(ServerPlayer player, ItemStack core, ItemStack seed) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return Component.translatable("kultivator.tempa.natal.none");
		}
		if (ArtifactItem.spiritStage(live) >= 2) {
			return Component.translatable("kultivator.tempa.spirit.has_spirit");
		}
		if (ArtifactItem.grade(live) < SEED_GRADE) {
			return Component.translatable("kultivator.tempa.spirit.grade", ArtifactItem.gradeName(SEED_GRADE));
		}
		if (core.isEmpty() || !BeastCores.isCore(core) || BeastCores.grade(core) < BeastCores.KING || seed.isEmpty() || !seed.is(TempaItems.SPIRIT_SEED)) {
			return Component.translatable("kultivator.tempa.spirit.need_soul");
		}
		core.shrink(1);
		seed.shrink(1);
		CompoundTag art = live.copy();
		setStage(art, 2, 1);
		NatalTreasureSelf.update(player, art);
		awaken(player, art, 2);
		return null;
	}

	/** Murnikan Roh: removes a Roh Buatan (stage back to 0, years kept). */
	static @Nullable Component purify(ServerPlayer player) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return Component.translatable("kultivator.tempa.natal.none");
		}
		if (ArtifactItem.spiritKind(live) != 1) {
			return Component.translatable("kultivator.tempa.spirit.not_artificial");
		}
		CompoundTag art = live.copy();
		setStage(art, 0, 0);
		NatalTreasureSelf.update(player, art);
		Msg.chat(player, Component.translatable("kultivator.tempa.spirit.purified").withStyle(ChatFormatting.YELLOW));
		Tempa.sync(player);
		return null;
	}

	// ================================================================== tests

	/** {@code uji roh <tahap>}: sets the natal spirit stage (natural). */
	static boolean testStage(ServerPlayer player, int stage) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return false;
		}
		CompoundTag art = live.copy();
		setStage(art, stage, 0);
		NatalTreasureSelf.update(player, art);
		if (stage > 0) {
			awaken(player, art, stage);
		}
		Tempa.sync(player);
		return true;
	}

	/** {@code uji roh umur <tahun>}: sets the natal spirit years. */
	static boolean testYears(ServerPlayer player, int years) {
		CompoundTag live = NatalTreasureSelf.record(player);
		if (live == null) {
			return false;
		}
		CompoundTag art = live.copy();
		CompoundTag spirit = art.getCompoundOrEmpty("spirit").copy();
		spirit.putDouble("years", Math.max(0, Math.min(MAX_YEARS, years)));
		art.put("spirit", spirit);
		NatalTreasureSelf.update(player, art);
		advance(player, art);
		Tempa.sync(player);
		return true;
	}

	private WeaponSpirit() {
	}
}
