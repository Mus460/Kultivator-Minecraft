package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Sepuluh Pengadilan, the Ten Courts of Diyu (Yuli Baochao; Journey to the West ch. 10-11, where Taizong's soul stands
 * before the Kings with Judge Cui). Each court's judge sets one trial that reflects the soul's deeds; courts are taken
 * in order, progress is saved in {@code extra.bawah.court} (the highest passed, 0-10). Meng Po must have been visited
 * first ("refusing the soup is the way to the courts").
 * <ul>
 * <li>Court 1, Qinguang: <b>Cermin Karma</b> (Niejing Tai), the mirror of retribution showing the soul's real deeds
 * (monsters and players slain, deaths, disciples, sect, breakthroughs, tribulations, heart demons, days lived).</li>
 * <li>Courts 2, 5, 8: a guardian duel (Ox-Head or Horse-Face) through {@link DuelRules}: ends at 10 % health, never a
 * death; a lost duel may be retried.</li>
 * <li>Courts 3, 6, 9: a judgment riddle; a wrong answer waits 60 s.</li>
 * <li>Courts 4, 7, 10: escort a lost soul from the court to the Naihe bridge (10 minutes).</li>
 * </ul>
 * Every passed court gives soul jade (two on 5 and 10). After {@code courtsRequired} courts Raja Yama opens the Book.
 */
public final class Courts {

	public static final int MIRROR = 0;
	public static final int DUEL = 1;
	public static final int RIDDLE = 2;
	public static final int ESCORT = 3;
	public static final int RIDDLES = 12;
	/** The right answer (0-2) of each riddle {@code kultivator.dimensi.teka.<i>.q / .a0-.a2}. */
	private static final int[] ANSWER = {1, 0, 2, 1, 2, 0, 0, 1, 2, 1, 0, 2};
	public static final long RIDDLE_WAIT = 20L * 60L;
	public static final long ESCORT_TICKS = 20L * 60L * 10L;
	public static final int DUEL_TICKS = 20 * 120;

	/** The trial of court {@code n} (1-10). */
	public static int trialOf(int n) {
		if (n == 1) {
			return MIRROR;
		}
		return switch (n % 3) {
			case 2 -> DUEL;
			case 0 -> RIDDLE;
			default -> ESCORT;
		};
	}

	/** The highest court passed (0-10). */
	public static int passed(ServerPlayer player) {
		return Math.max(0, Math.min(10, SoulJourney.data(player).getIntOr("court", 0)));
	}

	public static void setPassed(ServerPlayer player, int n) {
		SoulJourney.data(player).putInt("court", Math.max(0, Math.min(10, n)));
		SoulJourney.data(player).remove("trial");
		DimensiData.dirty(player);
	}

	/** The riddle this player meets at court {@code n}. */
	static int riddleOf(ServerPlayer player, int n) {
		return Math.floorMod(n * 7 + player.getUUID().hashCode(), RIDDLES);
	}

	// ================================================================== the judge

	static void openCourt(ServerPlayer player, ArwahEntity judge) {
		int n = Math.max(1, Math.min(10, judge.variant()));
		CompoundTag bawah = SoulJourney.data(player);
		if (!bawah.getBooleanOr("mengpo", false)) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.mengpo_dulu").withColor(0xD0A0A0));
			Voice.say(judge, "boss.taunt", "kultivator.dimensi.voice.yama_judgement");
			return;
		}
		int passed = passed(player);
		if (n <= passed) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.sudah", Component.translatable("kultivator.dimensi.raja." + n))
					.withColor(0xC9B8FF));
			return;
		}
		if (n > passed + 1) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.urutan", passed + 1).withColor(0xD0A0A0));
			return;
		}
		Voice.say(judge, "boss.awaken", "kultivator.dimensi.voice.yama_judgement");
		DimensiNet.open(player, DimensiNet.SCREEN_COURT, judge.getId(), courtTag(player, n));
	}

	public static CompoundTag courtTag(ServerPlayer player, int n) {
		CompoundTag tag = new CompoundTag();
		int trial = trialOf(n);
		tag.putInt("court", n);
		tag.putInt("trial", trial);
		tag.putInt("required", DimensiConfig.get().courtsRequired);
		CompoundTag active = SoulJourney.data(player).getCompoundOrEmpty("trial");
		tag.putBoolean("active", active.getIntOr("court", 0) == n);
		long now = DimensiData.now(player.level().getServer());
		tag.putLong("wait", Math.max(0L, SoulJourney.data(player).getLongOr("riddle_wait", 0L) - now));
		if (trial == MIRROR) {
			tag.put("karma", karma(player));
		} else if (trial == RIDDLE) {
			tag.putInt("riddle", riddleOf(player, n));
		}
		return tag;
	}

	/** The deeds the Mirror of Retribution shows: all real numbers of this player. */
	static CompoundTag karma(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = new CompoundTag();
		tag.putInt("mobs", player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)));
		tag.putInt("players", player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)));
		tag.putInt("deaths", player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
		tag.putInt("bred", player.getStats().getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED)));
		tag.putInt("days", player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 24000);
		tag.putInt("disciples", Disciples.aliveCount(data));
		Sect sect = Sects.sectOf(player);
		tag.putString("sect", sect == null ? "" : sect.name());
		tag.putInt("realm", data.realm().ordinal());
		tag.putInt("tribulations", data.tribulationsSurvived());
		tag.putInt("demons", data.heartDemonsDefeated());
		tag.putInt("failed", data.failedBreakthroughs());
		tag.putInt("enlightenments", data.enlightenments());
		tag.putInt("pills", data.pillsRefined());
		return tag;
	}

	/** {@code court:<accept|duel|answer:i|escort>} from the court scroll. */
	public static void action(ServerPlayer player, @Nullable ArwahEntity judge, String what) {
		int passed = passed(player);
		int n = passed + 1;
		if (judge != null && judge.variant() != n) {
			return;
		}
		if (n > 10) {
			return;
		}
		int trial = trialOf(n);
		long now = DimensiData.now(player.level().getServer());
		CompoundTag bawah = SoulJourney.data(player);
		if (what.equals("accept") && trial == MIRROR) {
			pass(player, n, judge);
		} else if (what.equals("duel") && trial == DUEL && judge != null) {
			startDuel(player, judge, n);
		} else if (what.startsWith("answer:") && trial == RIDDLE) {
			if (bawah.getLongOr("riddle_wait", 0L) > now) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.court.tunggu").withStyle(ChatFormatting.GRAY));
				return;
			}
			int chosen;
			try {
				chosen = Integer.parseInt(what.substring("answer:".length()));
			} catch (NumberFormatException exception) {
				return;
			}
			if (chosen == ANSWER[riddleOf(player, n)]) {
				pass(player, n, judge);
			} else {
				bawah.putLong("riddle_wait", now + RIDDLE_WAIT);
				DimensiData.dirty(player);
				Msg.chat(player, Component.translatable("kultivator.dimensi.court.salah").withColor(0xD08080));
				if (judge != null) {
					Voice.say(judge, "boss.taunt", "");
				}
			}
		} else if (what.equals("escort") && trial == ESCORT && judge != null) {
			startEscort(player, judge, n);
		}
	}

	private static void startDuel(ServerPlayer player, ArwahEntity judge, int n) {
		if (DuelRules.inDuel(player)) {
			return;
		}
		ServerLevel level = player.level();
		int role = n == 5 ? ArwahEntity.MUKA_KUDA : ArwahEntity.KEPALA_LEMBU;
		ArwahEntity guard = nearestGuard(level, judge.position(), role, n);
		if (guard == null) {
			Vec3 at = judge.position().add(judge.getLookAngle().scale(3.0D));
			guard = BawahPeople.spawn(level, at, judge.getYRot() + 180.0F, role, n, BawahPeople.realmNear(level, at), null, false);
		}
		if (guard == null) {
			return;
		}
		guard.setup(role, guard.look(), n, Cultivators.get(player).realm().ordinal());
		ArwahEntity partner = guard;
		boolean started = DuelRules.begin(player, partner, DUEL_TICKS, (winner, loser, reason) -> {
			partner.setFlag(DimensiNpc.FLAG_FIGHT, false);
			partner.setTarget(null);
			partner.setHealth(partner.getMaxHealth());
			if (winner == player && passed(player) == n - 1) {
				pass(player, n, judge);
			} else if (player.isAlive()) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.court.kalah").withColor(0xD08080));
			}
		});
		if (started) {
			partner.setFlag(DimensiNpc.FLAG_FIGHT, true);
			partner.setTarget(player);
			Voice.say(partner, "boss.awaken", "kultivator.dimensi.voice.ox_head");
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.duel", partner.getName()).withColor(0xE0B070));
		}
	}

	private static @Nullable ArwahEntity nearestGuard(ServerLevel level, Vec3 at, int role, int court) {
		List<ArwahEntity> found = level.getEntitiesOfClass(ArwahEntity.class, new AABB(at, at).inflate(16.0D),
				npc -> npc.isAlive() && npc.role() == role && npc.variant() == court && !DuelRules.inDuel(npc));
		return found.isEmpty() ? null : found.get(0);
	}

	private static void startEscort(ServerPlayer player, ArwahEntity judge, int n) {
		CompoundTag bawah = SoulJourney.data(player);
		CompoundTag active = bawah.getCompoundOrEmpty("trial");
		ServerLevel level = player.level();
		if (active.getIntOr("court", 0) == n) {
			UUID ghost = SoulJourney.parseUuid(active.getStringOr("ghost", ""));
			Entity alive = ghost == null ? null : level.getEntity(ghost);
			if (alive != null && alive.isAlive()) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.court.antar_jalan").withColor(0xC9B8FF));
				return;
			}
		}
		Vec3 at = judge.position().add(judge.getLookAngle().scale(2.0D));
		ArwahEntity soul = BawahPeople.spawn(level, at, judge.getYRot(), ArwahEntity.ARWAH, n, 4, null, false);
		if (soul == null) {
			return;
		}
		soul.setFollow(player.getUUID());
		soul.setNote("court:" + n);
		soul.setFlag(DimensiNpc.FLAG_QUEST, true);
		CompoundTag trial = new CompoundTag();
		trial.putInt("court", n);
		trial.putString("ghost", soul.getUUID().toString());
		trial.putLong("until", DimensiData.now(level.getServer()) + ESCORT_TICKS);
		bawah.put("trial", trial);
		DimensiData.dirty(player);
		Voice.say(soul, "disciple.f2.careful", "kultivator.dimensi.voice.ghost_plea");
		Msg.chat(player, Component.translatable("kultivator.dimensi.court.antar").withColor(0xC9B8FF));
	}

	/** The Naihe anchor's pass: an escorted soul that reached the bridge passes its court; a court ghost leaves. */
	static void naihePass(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		Vec3 bridge = anchor.position();
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(bridge) < 7.0D * 7.0D && SoulJourney.isSoul(player)) {
				SoulJourney.crossedNaihe(player);
			}
		}
		List<ArwahEntity> souls = level.getEntitiesOfClass(ArwahEntity.class, new AABB(bridge, bridge).inflate(7.0D),
				npc -> npc.isAlive() && npc.role() == ArwahEntity.ARWAH && npc.following() != null);
		for (ArwahEntity soul : souls) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(soul.following());
			String note = soul.note();
			soul.setFollow(null);
			DimensiNet.fx(level, DimensiNet.FX_JUDGED, soul.position(), 2, 48.0D);
			soul.discard();
			if (owner == null) {
				continue;
			}
			if (note.startsWith("court:")) {
				int n;
				try {
					n = Integer.parseInt(note.substring("court:".length()));
				} catch (NumberFormatException exception) {
					continue;
				}
				if (passed(owner) == n - 1) {
					pass(owner, n, null);
				}
			} else {
				GhostQuests.lostSoulHome(owner);
			}
		}
	}

	/** Checks the running escort timers (every second, from the module tick). */
	static void tick(ServerPlayer player) {
		CompoundTag trial = SoulJourney.data(player).getCompoundOrEmpty("trial");
		if (trial.isEmpty()) {
			return;
		}
		if (trial.getLongOr("until", 0L) < DimensiData.now(player.level().getServer())) {
			UUID ghost = SoulJourney.parseUuid(trial.getStringOr("ghost", ""));
			Entity soul = ghost == null ? null : player.level().getEntity(ghost);
			if (soul != null) {
				soul.discard();
			}
			SoulJourney.data(player).remove("trial");
			DimensiData.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.antar_gagal").withColor(0xD08080));
		}
	}

	/** Court {@code n} is passed. */
	public static void pass(ServerPlayer player, int n, @Nullable LivingEntity judge) {
		setPassed(player, n);
		DimensiData.give(player, new ItemStack(DimensiItems.GIOK_ARWAH, n == 5 || n == 10 ? 2 : 1));
		ServerLevel level = player.level();
		DimensiNet.fx(level, DimensiNet.FX_JUDGED, judge != null ? judge.position() : player.position(), n, 48.0D);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		Msg.chat(player, Component.translatable("kultivator.dimensi.court.lulus", n, Component.translatable("kultivator.dimensi.raja." + n))
				.withColor(0xE8D9A8));
		if (n >= DimensiConfig.get().courtsRequired) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.court.yama").withColor(0xFFE9A8));
		}
		if (judge instanceof ArwahEntity speaker) {
			Voice.say(speaker, "boss.defeat", "kultivator.dimensi.voice.yama_judgement");
		}
	}

	/** The judge's anchor pass: a guard duel or a court ghost needs nothing else; kept for symmetry. */
	static @Nullable DuniaAnchorEntity courtAnchor(ServerLevel level, Vec3 near, int n) {
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, "bawah/pengadilan")) {
			if (anchor.data().getIntOr("court", 0) == n && anchor.position().distanceToSqr(near) < 64.0D * 64.0D) {
				return anchor;
			}
		}
		return null;
	}

	private Courts() {
	}
}
