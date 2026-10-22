package dev.kultivator.sect.event;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tanah Terlarang Berwarna Darah, the Blood-Colored Forbidden Ground (DESIGN 20.3.11): every
 * {@code forbiddenGroundIntervalDays} at hall level 2+ the wind seal weakens for one day (crimson wind around the stele);
 * up to 3 Qi Refining disciples go in for 6,000 ticks. Survivors bring Herba Fondasi; the others come back badly hurt
 * (never dead; under the master's Hardcore their Soul Tablet comes back to the storehouse instead).
 * <p>
 * Sumber: A Record of a Mortal's Journey to Immortality ch. 155-160 (the wind restriction that weakens for five days
 * every five years, the second restriction for Qi Condensation disciples only, Foundation herbs, fewer than a third
 * return).
 */
final class ForbiddenGround {

	public static final int MIN_HALL_LEVEL = 2;
	public static final int MAX_ENTRANTS = 3;
	public static final long AWAY_TICKS = 6000L;
	public static final long WINDOW_TICKS = 24000L;
	/** Chance = 0.30 + 0.08 x stage + 0.05 x root grade + 0.05 x min(3, attack arts) + 0.10 in company, at most 0.85. */
	public static final double BASE = 0.30D;
	public static final double PER_STAGE = 0.08D;
	public static final double PER_GRADE = 0.05D;
	public static final double PER_ART = 0.05D;
	public static final double COMPANY = 0.10D;
	public static final double MAX = 0.85D;
	public static final double SHARE = 0.25D;
	public static final int PK = 40;
	public static final int RENOWN = 30;
	public static final int FAIL_DEVIATION = 12000;
	public static final double FAIL_BAR_KEEP = 0.70D;

	/** Techniques that strike (the attack arts counted for the chance). */
	private static final Set<Technique> ATTACKS = EnumSet.of(Technique.QI_PALM, Technique.BUDDHA_PALM, Technique.SWORD_QI,
			Technique.THOUSAND_SWORDS, Technique.FLAME_PALM, Technique.PHOENIX_FLAME, Technique.FROST_LOTUS, Technique.GLACIER_PRISON,
			Technique.THUNDER_PALM, Technique.HEAVENLY_THUNDER, Technique.MOUNTAIN_STOMP, Technique.LIFE_DRAIN, Technique.SHADOW_STEP,
			Technique.SPACE_PRISON);

	static boolean open(UUID owner) {
		return EventUtil.section(owner).getCompoundOrEmpty("events").getLongOr("forbidden_until", 0L) > EventUtil.gameTime();
	}

	/** Opens the window now (schedule or test command): title, chat line and the crimson wind. */
	static void openNow(UUID owner, Halls.HallView hall) {
		CompoundTag events = EventUtil.sub(EventUtil.section(owner), "events");
		events.putLong("forbidden_until", EventUtil.gameTime() + WINDOW_TICKS);
		events.putInt("forbidden_sent", 0);
		EventUtil.dirty();
		ServerPlayer player = EventUtil.player(owner);
		if (player != null) {
			Msg.title(player, Component.translatable("kultivator.sectevent.forbidden.title").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.sectevent.forbidden.subtitle").withStyle(ChatFormatting.RED), 10, 60, 20);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.forbidden.open", MAX_ENTRANTS).withStyle(ChatFormatting.RED));
		}
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level != null) {
			level.playSound(null, hall.pos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.25F, 1.6F);
		}
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.forbidden_open"));
	}

	/** Entrants still allowed in this window. */
	static int slotsLeft(UUID owner) {
		return MAX_ENTRANTS - EventUtil.section(owner).getCompoundOrEmpty("events").getIntOr("forbidden_sent", 0);
	}

	static @Nullable String blocker(DiscipleEntity disciple) {
		if (Expeditions.refuses(disciple)) {
			return Expeditions.REFUSES;
		}
		return disciple.data().realm() == Realm.QI_REFINING ? null : "kultivator.sectevent.forbidden.second_seal";
	}

	static double chance(DiscipleData data, boolean company) {
		int arts = 0;
		for (Technique technique : data.learnedTechniques()) {
			if (ATTACKS.contains(technique)) {
				arts++;
			}
		}
		double chance = BASE + PER_STAGE * data.stage() + PER_GRADE * data.rootGrade().ordinal() + PER_ART * Math.min(3, arts)
				+ (company ? COMPANY : 0.0D);
		return Math.min(MAX, chance);
	}

	static boolean send(ServerPlayer player, List<DiscipleEntity> members) {
		UUID owner = player.getUUID();
		if (!open(owner)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.forbidden.closed").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (members.isEmpty() || members.size() > slotsLeft(owner)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.forbidden.too_many", slotsLeft(owner)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		for (DiscipleEntity member : members) {
			String why = blocker(member);
			if (why != null) {
				EventUtil.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
				return false;
			}
		}
		CompoundTag record = new CompoundTag();
		record.putInt("count", members.size());
		if (AwayTeams.depart(player, AwayTeams.KIND_FORBIDDEN, members, AWAY_TICKS, record) == null) {
			return false;
		}
		CompoundTag events = EventUtil.sub(EventUtil.section(owner), "events");
		events.putInt("forbidden_sent", events.getIntOr("forbidden_sent", 0) + members.size());
		EventUtil.dirty();
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.forbidden.sent", members.size()).withStyle(ChatFormatting.RED));
		return true;
	}

	static void resolve(UUID owner, CompoundTag team, @Nullable ServerPlayer player, RandomSource random) {
		ListTag members = team.getListOrEmpty("members");
		boolean company = members.size() > 1;
		boolean hardcore = KultivatorConfig.get().hardcoreForced || player != null && Cultivators.get(player).hardcore();
		ListTag report = new ListTag();
		int survivors = 0;
		for (Tag tag : members) {
			if (!(tag instanceof CompoundTag member)) {
				continue;
			}
			DiscipleData data = AwayTeams.data(member);
			boolean ok = random.nextDouble() < chance(data, company);
			CompoundTag line = new CompoundTag();
			line.store("name", ComponentSerialization.CODEC, AwayTeams.name(member));
			line.putBoolean("ok", ok);
			if (ok) {
				survivors++;
				int herbs = 2 + random.nextInt(4);
				line.putInt("herbs", herbs);
				// S12: a full Lumbung spills into the Peti Galian (online or not), never onto the floor of nowhere.
				ItemStack herb = new ItemStack(SectEventItems.FOUNDATION_HERB, herbs);
				Component herbName = herb.getHoverName();
				dev.kultivator.sect.build.flow.LumbungOverflow.tell(player, owner, dev.kultivator.sect.build.flow.LumbungOverflow.store(owner, herb),
						herbName);
				DisciplePaths.grantBarShare(data, SHARE);
				SectRanks.addPk(data, PK, "kultivator.sectevent.pk.forbidden");
				Halls.addRenown(owner, RENOWN, "kultivator.sectevent.renown.forbidden");
			} else {
				data.setDeviationTicks(Math.max(data.deviationTicks(), FAIL_DEVIATION));
				data.setXp(data.xp() * FAIL_BAR_KEEP);
				member.putFloat("arrive_hp", 0.30F);
				if (hardcore) {
					member.putBoolean("as_tablet", true);
					line.putBoolean("tablet", true);
				}
			}
			AwayTeams.setData(member, data);
			report.add(line);
		}
		CompoundTag section = EventUtil.section(owner);
		section.put("report", report);
		section.putLong("report_time", EventUtil.gameTime());
		EventUtil.dirty();
		if (player != null) {
			player.sendSystemMessage(Component.translatable("kultivator.sectevent.forbidden.report").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			for (Tag tag : report) {
				if (tag instanceof CompoundTag line) {
					Component name = line.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
					String key = line.getBooleanOr("ok", false) ? "kultivator.sectevent.forbidden.line_ok"
							: line.getBooleanOr("tablet", false) ? "kultivator.sectevent.forbidden.line_tablet" : "kultivator.sectevent.forbidden.line_hurt";
					player.sendSystemMessage(Component.translatable(key, name, line.getIntOr("herbs", 0))
							.withStyle(line.getBooleanOr("ok", false) ? ChatFormatting.GREEN : ChatFormatting.RED));
				}
			}
			if (survivors > 0) {
				Advancements.grant(player, "forbidden_ground_survivor");
			}
		}
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.forbidden", survivors, members.size()));
	}

	private ForbiddenGround() {
	}
}
