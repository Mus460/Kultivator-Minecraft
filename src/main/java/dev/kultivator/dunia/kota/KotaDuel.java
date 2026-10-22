package dev.kultivator.dunia.kota;

import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Module K's side of the sword duel (DESIGN 35.7.6, docs/rancangan/35-duel-pedang.md): the town's people who duel answer
 * the duel module through {@link dev.kultivator.duel.api.Duelist} on {@link KultivatorNpc}. This is the only class of
 * the module that calls the duel session API ({@code DuelChallenges}); everything else asks it.
 * <ul>
 * <li>Wanderers (34.5.2): their style follows Dao and temper (the Sword Dao from Golden Core is a {@code SANTO_PEDANG},
 * the Shadow Dao and the cunning trick, the proud press, the friendly wait); the pressing and the sword saints offer a
 * duel to a player within 8 blocks who holds a blade, at most once per 20 minutes per player. A wanderer duels a player
 * once (shared with the old fist-and-qi Tanding): beaten, it pays the Tanding reward of 34.5.2.</li>
 * <li>Rivals (34.5.3): the group's way (cunning and poison trick, the proud and the assassins press, the body
 * cultivators wait with axes); a leader from Nascent Soul is a sword saint. They accept when not racing or ambushing.</li>
 * <li>Guards: patient spearmen; a friendly bout for a guest of good name (Nama Baik 20+), never during a raid.</li>
 * <li>Native immortals of the Immortal City: sword saints, for immortals only.</li>
 * </ul>
 * Rank from realm and stage, Honour seeded around 1000 (+25 per realm), stones by realm band. Server thread only.
 */
final class KotaDuel {

	/** A player gets an NPC's duel offer at most this often (35.7.6: once per 20 minutes). */
	static final long OFFER_COOLDOWN = 20L * 60L * 20L;
	static final double OFFER_RANGE = 8.0D;
	/** Guards duel guests of at least this Nama Baik. */
	static final int GUARD_REPUTATION = 20;

	// ================================================================== the profile

	static @Nullable DuelistProfile profile(KultivatorNpc npc) {
		NpcRole role = npc.role();
		if (role != NpcRole.PENGEMBARA && role != NpcRole.SAINGAN && role != NpcRole.PENJAGA && role != NpcRole.ABADI) {
			return null;
		}
		DuelStyle style = style(npc);
		Realm realm = npc.realmValue();
		int stage = Mth.clamp(npc.stage(), 0, Math.max(0, realm.stages() - 1));
		int step = new Rank(realm, 0, stage).step();
		WeaponClass weapon = NpcGear.duelWeapon(npc);
		String kiai = npc.female() ? "grunt.female.attack" : "grunt.male.attack";
		boolean leader = npc.memory().getBooleanOr("leader", false);
		int honour = switch (role) {
			case ABADI -> 1400;
			case PENJAGA -> 1050;
			default -> 1000 + 25 * npc.realm() + Math.floorMod(npc.look() >>> 17, 121) - 60 + (leader ? 150 : 0);
		};
		float surrender = style.surrender;
		if (role == NpcRole.PENGEMBARA && npc.temperament() == Pengembara.LICIK) {
			surrender = Math.max(surrender, 0.30F);
		} else if (role == NpcRole.PENGEMBARA && npc.temperament() == Pengembara.ANGKUH) {
			surrender = Math.min(surrender, 0.10F);
		}
		int grade = stoneGrade(realm);
		int count = switch (role) {
			case PENGEMBARA -> Math.min(8, 2 + npc.realm() / 2);
			case SAINGAN -> Math.min(12, (leader ? 6 : 3) + npc.realm() / 2);
			case ABADI -> 2;
			default -> 0;
		};
		boolean offers = role == NpcRole.PENGEMBARA && (style == DuelStyle.SANTO_PEDANG || style == DuelStyle.AGRESIF)
				&& npc.companionOf() == null && !npc.hasFlag(KultivatorNpc.FLAG_SITTING);
		return new DuelistProfile(style, step, weapon, NpcSkills.color(npc), kiai, honour, surrender, grade, count, null, offers);
	}

	static DuelStyle style(KultivatorNpc npc) {
		Realm realm = npc.realmValue();
		switch (npc.role()) {
			case PENJAGA -> {
				return DuelStyle.BERTAHAN;
			}
			case ABADI -> {
				return DuelStyle.SANTO_PEDANG;
			}
			case SAINGAN -> {
				boolean leader = npc.memory().getBooleanOr("leader", false);
				if (leader && realm.atLeast(Realm.NASCENT_SOUL)) {
					return DuelStyle.SANTO_PEDANG;
				}
				String way = groupStyle(npc);
				return switch (way) {
					case "licik", "racun" -> DuelStyle.PENIPU;
					case "angkuh", "pembunuh" -> DuelStyle.AGRESIF;
					default -> DuelStyle.BERTAHAN;
				};
			}
			default -> {
				Dao dao = npc.dao();
				if (dao == Dao.SWORD) {
					return realm.atLeast(Realm.GOLDEN_CORE) ? DuelStyle.SANTO_PEDANG : DuelStyle.AGRESIF;
				}
				if (dao == Dao.SHADOW) {
					return DuelStyle.PENIPU;
				}
				return switch (npc.temperament()) {
					case Pengembara.ANGKUH -> DuelStyle.AGRESIF;
					case Pengembara.LICIK -> DuelStyle.PENIPU;
					default -> dao == Dao.FIRE || dao == Dao.LIGHTNING ? DuelStyle.AGRESIF : DuelStyle.BERTAHAN;
				};
			}
		}
	}

	private static String groupStyle(KultivatorNpc npc) {
		if (npc.group().isEmpty() || !(npc.level() instanceof ServerLevel level)) {
			return "netral";
		}
		SainganData.Group group = SainganData.get(level.getServer()).byId(npc.group());
		return group == null ? "netral" : SainganData.STYLES[Math.floorMod(group.style, SainganData.STYLES.length)];
	}

	/** Spirit stone grade 0-3 (low, mid, high, supreme) by realm band. */
	static int stoneGrade(Realm realm) {
		if (realm.atLeast(Realm.TRUE_IMMORTAL)) {
			return 3;
		}
		if (realm.atLeast(Realm.DEITY_TRANSFORMATION)) {
			return 2;
		}
		return realm.atLeast(Realm.GOLDEN_CORE) ? 1 : 0;
	}

	// ================================================================== accepting and ending

	static boolean accepts(KultivatorNpc npc, ServerPlayer challenger) {
		if (!npc.isAlive() || npc.isRemoved() || npc.getTarget() != null || DuelRules.inDuel(npc) || npc.isHostileTo(challenger.getUUID())
				|| !Kota.enabled()) {
			return false;
		}
		String refuse = refusal(npc, challenger);
		if (refuse != null) {
			NpcLife.speak(npc, "kultivator.kota.duel.tolak." + refuse, 80);
			Voice.say(npc, npc.voiceKey("careful"), "");
			return false;
		}
		npc.memory().putBoolean("duel", true);
		npc.memory().putString("duel_with", challenger.getUUID().toString());
		npc.memory().putFloat("duel_low", 1.0F);
		npc.memory().putLong("idle", npc.level().getGameTime());
		npc.setFlag(KultivatorNpc.FLAG_SITTING, false);
		npc.memory().putBoolean("hold", false);
		npc.getNavigation().stop();
		npc.arm(true);
		npc.bow(30);
		NpcLife.speak(npc, "kultivator.kota.duel.terima." + npc.role().id() + "." + npc.getRandom().nextInt(3), 80);
		return true;
	}

	/** Why it refuses {@code challenger} ("sudah", "rendah", "nama", "razia", "sibuk", "fana"), or null. */
	private static @Nullable String refusal(KultivatorNpc npc, ServerPlayer challenger) {
		CultivationData data = Cultivators.get(challenger);
		switch (npc.role()) {
			case PENGEMBARA -> {
				if (npc.memory().getBooleanOr("sparred", false)) {
					return "sudah";
				}
				if (npc.temperament() == Pengembara.ANGKUH && data.realm().ordinal() < npc.realm()) {
					return "rendah";
				}
				return npc.companionOf() != null && !npc.companionOf().equals(challenger.getUUID()) ? "sibuk" : null;
			}
			case SAINGAN -> {
				return npc.memory().getBooleanOr("racing", false) || npc.memory().getBooleanOr("ambush", false) ? "sibuk" : null;
			}
			case PENJAGA -> {
				if (PasarGelap.raided(challenger, npc.town())) {
					return "razia";
				}
				int rep = KotaData.get(challenger.level().getServer()).reputation(npc.town(), challenger.getUUID());
				return rep < GUARD_REPUTATION ? "nama" : null;
			}
			case ABADI -> {
				return data.realm().atLeast(Realm.IMMORTAL_ASCENSION) ? null : "fana";
			}
			default -> {
				return "sibuk";
			}
		}
	}

	/** True while the NPC is in a formal duel of the duel module (from the challenge's acceptance to its end). */
	static boolean inSession(KultivatorNpc npc) {
		DuelSession session = DuelSessions.byEntity(npc);
		return session != null && !session.ended();
	}

	/**
	 * Every 20 ticks: while it duels (a session of the duel module, or a plain DuelRules bout), it remembers its partner
	 * and the lowest health share it reached; a beaten NPC is told only "not won" ({@link #ended}). This also covers a
	 * duel the NPC offered itself, which never asked {@link #accepts}.
	 */
	static void sample(KultivatorNpc npc) {
		DuelSession session = DuelSessions.byEntity(npc);
		if (session != null && session.ended()) {
			session = null;
		}
		LivingEntity partner = session != null ? session.partnerOf(npc) : DuelRules.partnerOf(npc);
		long now = npc.level().getGameTime();
		if (session == null && partner == null) {
			// a duel that ended without a word (the module unloaded, a crash) is forgotten after 10 s
			if (npc.memory().getBooleanOr("duel", false) && now - npc.memory().getLongOr("duel_seen", now) > 200L) {
				npc.memory().remove("duel");
				npc.memory().remove("duel_with");
				npc.memory().remove("duel_low");
			}
			return;
		}
		npc.memory().putLong("duel_seen", now);
		if (!npc.memory().getBooleanOr("duel", false)) {
			npc.memory().putBoolean("duel", true);
			npc.memory().putFloat("duel_low", 1.0F);
			npc.setFlag(KultivatorNpc.FLAG_SITTING, false);
			npc.memory().putBoolean("hold", false);
		}
		if (partner instanceof ServerPlayer player) {
			npc.memory().putString("duel_with", player.getUUID().toString());
		}
		float share = npc.getMaxHealth() <= 0.0F ? 1.0F : npc.getHealth() / npc.getMaxHealth();
		if (share < npc.memory().getFloatOr("duel_low", 1.0F)) {
			npc.memory().putFloat("duel_low", share);
		}
		npc.fought();
	}

	static void ended(KultivatorNpc npc, boolean won) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return;
		}
		sample(npc);
		if (!npc.memory().getBooleanOr("duel", false)) {
			return;
		}
		float low = npc.memory().getFloatOr("duel_low", 1.0F);
		UUID with = null;
		try {
			with = UUID.fromString(npc.memory().getStringOr("duel_with", ""));
		} catch (IllegalArgumentException ignored) {
			// no partner remembered
		}
		npc.memory().remove("duel");
		npc.memory().remove("duel_with");
		npc.memory().remove("duel_low");
		npc.memory().putLong("idle", level.getGameTime());
		npc.setTarget(null);
		if (!NpcGear.alwaysArmed(npc.role())) {
			npc.fought();
		}
		ServerPlayer player = with == null ? null : level.getServer().getPlayerList().getPlayer(with);
		DuelistProfile profile = profile(npc);
		float yieldAt = profile == null ? 0.1F : Math.max(0.1F, profile.surrenderAt());
		// "not won" is a loss, a draw or a cancel: only a real beating (health down to its yield line) pays
		boolean beaten = !won && low <= yieldAt + 0.08F;
		if (won) {
			Voice.say(npc, npc.voiceKey("victory"), null);
			NpcLife.speak(npc, "kultivator.kota.duel.menang." + npc.getRandom().nextInt(4), 100);
		} else if (beaten) {
			Voice.say(npc, npc.voiceKey("tired"), null);
			npc.bow(40);
			NpcLife.speak(npc, "kultivator.kota.duel.kalah." + npc.role().id() + "." + npc.getRandom().nextInt(2), 100);
		}
		if (npc.role() == NpcRole.PENGEMBARA) {
			npc.memory().putBoolean("sparred", true);
		}
		if (player == null || player.level() != level || !beaten) {
			return;
		}
		reward(level, npc, player);
	}

	/** The beaten NPC's due to the winner: the 34.5.2 Tanding reward for wanderers, a guard's respect, a rival's grudge. */
	private static void reward(ServerLevel level, KultivatorNpc npc, ServerPlayer player) {
		KotaData data = KotaData.get(level.getServer());
		switch (npc.role()) {
			case PENGEMBARA -> {
				CultivationData cultivation = Cultivators.get(player);
				Progression.grantXp(player, cultivation.rank().xpRequired() * Pengembara.SPAR_XP, XpSource.COMBAT);
				ItemStack badge = new ItemStack(KotaItems.LENCANA_PENGEMBARA);
				if (!player.getInventory().add(badge)) {
					Kota.drop(player, badge);
				}
				long town = Towns.near(level, player.blockPosition(), 3000);
				if (town != 0L) {
					data.addReputation(town, player.getUUID(), 3);
				}
				data.putInt(player, "lencana", data.getInt(player, "lencana", 0) + 1);
				data.putInt(player, "duel_pengembara", data.getInt(player, "duel_pengembara", 0) + 1);
				Advancements.grant(player, "dunia/wanderer_duel");
				Msg.chat(player, Component.translatable("kultivator.kota.duel.hadiah.pengembara", npc.plate()).withStyle(ChatFormatting.GREEN));
			}
			case PENJAGA -> {
				if (npc.town() != 0L) {
					int value = data.addReputation(npc.town(), player.getUUID(), 2);
					Msg.chat(player, Component.translatable("kultivator.kota.duel.hadiah.penjaga", npc.plate(), value).withStyle(ChatFormatting.GREEN));
				}
			}
			case SAINGAN -> {
				if (!npc.group().isEmpty()) {
					SainganData.get(level.getServer()).addGrudge(npc.group(), player.getUUID(), 10);
				}
				Msg.chat(player, Component.translatable("kultivator.kota.duel.hadiah.saingan", npc.plate()).withStyle(ChatFormatting.GOLD));
			}
			case ABADI -> {
				Advancements.grant(player, "dunia/immortal_duel");
				Msg.chat(player, Component.translatable("kultivator.kota.duel.hadiah.abadi", npc.plate()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			default -> {
			}
		}
	}

	// ================================================================== challenges and offers

	/** The meeting's "Duel Pedang" button (34.5.2 with 35): the player challenges the wanderer with the default rules. */
	static void challenge(ServerPlayer player, KultivatorNpc npc) {
		if (npc.memory().getBooleanOr("sparred", false)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.duel.tolak.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		int gap = Cultivators.get(player).realm().ordinal() - npc.realm();
		Component reason = DuelChallenges.challenge(player, npc, null, DuelRuleSet.suggested(gap));
		if (reason != null) {
			Msg.actionBar(player, reason.copy().withStyle(ChatFormatting.RED));
		} else {
			Msg.chat(player, Component.translatable("kultivator.kota.duel.tantang", npc.plate()).withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * A wanderer that offers duels (the pressing and the sword saints) offers one to a player within 8 blocks holding a
	 * blade, at most once per 20 minutes per player (35.7.6), through the duel module's own offer.
	 */
	static void maybeOffer(ServerLevel level, KultivatorNpc npc, long now) {
		if (npc.role() != NpcRole.PENGEMBARA || npc.memory().getBooleanOr("sparred", false) || npc.getTarget() != null || DuelRules.inDuel(npc)
				|| npc.memory().getBooleanOr("duel", false) || now % 100L != 0L) {
			return;
		}
		DuelistProfile profile = profile(npc);
		if (profile == null || !profile.offersChallenges()) {
			return;
		}
		KotaData data = KotaData.get(level.getServer());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || player.distanceToSqr(npc) > OFFER_RANGE * OFFER_RANGE
					|| !WeaponClass.of(player).blade() || DuelRules.inDuel(player) || npc.isHostileTo(player.getUUID())) {
				continue;
			}
			long last = data.getLong(player, "duel_offer", -OFFER_COOLDOWN);
			if (level.getGameTime() - last < OFFER_COOLDOWN && level.getGameTime() >= last) {
				continue;
			}
			// the duel module keeps its own once-per-20-minutes rule (and scans by itself); the bow and the words go with an
			// offer that was really made
			data.putLong(player, "duel_offer", level.getGameTime());
			if (DuelChallenges.npcOffer(npc, player)) {
				NpcLife.face(npc, player.position());
				npc.bow(30);
				NpcLife.speak(npc, "kultivator.kota.duel.tawar." + npc.getRandom().nextInt(4), 100);
			}
			return;
		}
	}

	/** {@code uji pengembara duel}: the wanderer offers its duel to the player now (no cooldown, no blade needed). */
	static void testOffer(ServerPlayer player, KultivatorNpc npc) {
		npc.memory().remove("sparred");
		NpcLife.face(npc, player.position());
		npc.bow(30);
		NpcLife.speak(npc, "kultivator.kota.duel.tawar." + npc.getRandom().nextInt(4), 100);
		DuelChallenges.npcOffer(npc, player);
	}

	private KotaDuel() {
	}
}
