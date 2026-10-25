package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.dunia.tempat.hidup.TubuhPenduduk;
import dev.kultivator.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The everyday life of the town's people (DESIGN 34.5.1, 34.5.4; detail pass): what an NPC does when nobody fights.
 * Every pass runs from {@link NpcAi#check} (every 20 ticks per NPC, and NPCs exist only while a player is near), costs a
 * handful of comparisons, and changes nothing but the NPC itself.
 * <ul>
 * <li><b>Daily round</b> of the townsfolk: the morning exercise at dawn (slow arm circles), the market and the square by
 * day, a rest on the ground at noon, home at dusk, sitting in meditation at night.</li>
 * <li><b>Chatter</b>: two idle townsfolk (or two immortals) within 5 blocks stop, face each other and talk a little
 * four-line exchange in speech bubbles (topics by time, weather and the sky); no chat spam, no voice.</li>
 * <li><b>Work</b>: merchants count on the abacus, the smith hammers, the tea master pours while nobody is at the counter;
 * a passer-by within 10 blocks is called over once in a while; the auctioneer harangues during a session.</li>
 * <li><b>Guards</b> walk between their post and the square, halt a player the town is hostile to and salute the
 * powerful.</li>
 * <li><b>Respect</b>: a player two realms above an NPC gets a cupped-fist bow and a polite word (once per 5 minutes per
 * NPC); native immortals sneer at those short of True Immortal.</li>
 * <li><b>The sky</b>: a blood eclipse sends the townsfolk home with worried words, a meteor shower or the twin moons
 * make them stop and look up, a newborn treasure's light sets the town talking ({@link DuniaEvents}).</li>
 * <li><b>Blades</b>: wanderers and rivals draw when they fight or duel and sheathe three seconds after.</li>
 * <li><b>Residents of DESIGN 38</b> (bodies of {@code dunia/tempat/hidup}, {@link KultivatorNpc#penduduk}): blades as
 * above, the sky and a newborn treasure with their own clips and lines ({@link TubuhPenduduk#reaksi}), then their own day
 * ({@link TubuhPenduduk#tik}) instead of the old round.</li>
 * </ul>
 */
final class NpcLife {

	/** Speech bubbles last this long by default (3.5 s). */
	static final int LINE_TICKS = 70;
	static final double CHAT_RANGE = 5.0D;
	static final double SEE_RANGE = 20.0D;
	static final double RESPECT_RANGE = 6.0D;
	static final long RESPECT_GAP = 6000L;
	static final long HAWK_GAP = 2400L;
	/** Lines per chatter topic (a four-line exchange: initiator, partner, initiator, partner). */
	static final int CHAT_LINES = 4;
	static final String[] TOWN_TOPICS = {"harga", "lelang", "sekte", "pengembara", "cuaca", "latihan", "gosip", "makanan", "anak", "pil"};
	static final String[] IMMORTAL_TOPICS = {"abadi_dao", "abadi_persik", "abadi_fana", "abadi_kuliah"};

	static void init() {
		DuniaEvents.SKY_EVENT.register(NpcLife::onSky);
		DuniaEvents.TREASURE_BORN.register(NpcLife::onTreasure);
	}

	// ================================================================== saying things

	/** A speech bubble with the lang line {@code key} for {@code ticks} (null: nothing). */
	static void speak(KultivatorNpc npc, @Nullable String key, int ticks) {
		if (key != null && !key.isEmpty()) {
			npc.say(Component.translatable(key), ticks);
		}
	}

	/** The talking gesture without a bubble (a player has its screen open). */
	static void gesture(KultivatorNpc npc, int ticks) {
		npc.say(Component.empty(), ticks);
	}

	/** A right-click answered: it turns to the player, talks with its hands, and bows to a senior. */
	static void greeted(KultivatorNpc npc, ServerPlayer player) {
		face(npc, player.position());
		if (npc.line().getString().isEmpty()) {
			gesture(npc, 60);
		}
		if (Cultivators.get(player).realm().ordinal() >= npc.realm() + 2) {
			npc.bow(40);
		}
	}

	/** What a townsperson says to a player who speaks to them: the sky, a senior, the hour and the weather first. */
	static String townLine(KultivatorNpc npc, ServerPlayer player) {
		ServerLevel level = (ServerLevel) npc.level();
		SkyKind sky = DuniaApi.sky().active(level);
		if (sky != null) {
			return "kultivator.kota.langit." + sky.id() + "." + npc.getRandom().nextInt(4);
		}
		if (Cultivators.get(player).realm().ordinal() >= npc.realm() + 3) {
			return "kultivator.kota.hormat." + npc.getRandom().nextInt(4);
		}
		long time = Kota.timeOfDay(level.getServer());
		if (level.isRaining() && npc.getRandom().nextBoolean()) {
			return "kultivator.kota.warga.hujan." + npc.getRandom().nextInt(3);
		}
		if (npc.getRandom().nextInt(3) == 0) {
			String part = time < 3000L || time >= 23000L ? "pagi" : time < 9000L ? "siang" : time < 13000L ? "sore" : "malam";
			return "kultivator.kota.warga." + part + "." + npc.getRandom().nextInt(3);
		}
		return "kultivator.kota.warga.kata." + npc.getRandom().nextInt(12);
	}

	// ================================================================== the pass (every 20 ticks)

	static void tick(ServerLevel level, KultivatorNpc npc, long now) {
		KotaDuel.sample(npc);
		blades(npc, now);
		reactLater(level, npc, now);
		if (npc.penduduk()) {
			// a 38 resident: schedule, titik, chats, bargaining and reactions of DESIGN 38.N.4
			TubuhPenduduk.tik(level, npc, now);
			return;
		}
		boolean fighting = npc.getTarget() != null || DuelRules.inDuel(npc) || npc.memory().getBooleanOr("duel", false) || KotaDuel.inSession(npc);
		if (fighting) {
			endChat(level, npc);
			npc.work(0);
			return;
		}
		Player near = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), SEE_RANGE, each -> !each.isSpectator());
		if (near == null) {
			return;
		}
		NpcRole role = npc.role();
		switch (role) {
			case WARGA -> {
				round(level, npc, now);
				chatter(level, npc, now, TOWN_TOPICS);
			}
			case ABADI -> chatter(level, npc, now, IMMORTAL_TOPICS);
			case PENJAGA -> guard(level, npc, now);
			case PEDAGANG_PIL, PANDAI_PUSAKA, PEDAGANG_ABADI, PEMILIK_TEH, MAKELAR_GELAP -> shop(level, npc, now);
			case JURU_LELANG -> {
				if (BalaiLelang.sessionRunning(npc.town()) && now % 100L < 20L) {
					speak(npc, "kultivator.kota.lelang.seru." + npc.getRandom().nextInt(6), 90);
				}
			}
			case PENGEMBARA -> KotaDuel.maybeOffer(level, npc, now);
			default -> {
			}
		}
		if (role.townsfolk() && near instanceof ServerPlayer player) {
			respect(npc, player, now);
		}
	}

	// ================================================================== blades

	private static void blades(KultivatorNpc npc, long now) {
		NpcRole role = npc.role();
		if (!NpcGear.armed(role)) {
			return;
		}
		if (NpcGear.alwaysArmed(role)) {
			npc.arm(true);
			return;
		}
		boolean fight = npc.getTarget() != null || DuelRules.inDuel(npc) || npc.memory().getBooleanOr("duel", false) || KotaDuel.inSession(npc);
		if (fight) {
			npc.fought();
			npc.arm(true);
		} else if (now - npc.lastFight() > 60L) {
			npc.arm(false);
		}
	}

	// ================================================================== the townsfolk's day

	private static void round(ServerLevel level, KultivatorNpc npc, long now) {
		if (npc.memory().getBooleanOr("busy", false) || npc.memory().getBooleanOr("escort", false)) {
			return;
		}
		long time = Kota.timeOfDay(level.getServer());
		long day = Kota.day(level.getServer());
		BlockPos home = npc.home();
		boolean atHome = home != null && npc.position().distanceToSqr(Vec3.atBottomCenterOf(home)) < 9.0D;
		boolean night = time >= 13000L && time < 23000L;
		boolean sitting = npc.hasFlag(KultivatorNpc.FLAG_SITTING);
		long sitUntil = npc.memory().getLongOr("sit_until", 0L);
		long hurry = npc.memory().getLongOr("hurry_until", 0L);
		if (night || now < hurry) {
			// home for the night (or out of the eclipse's red light): meditate on the spot once there
			if (atHome && !sitting) {
				sit(npc, true);
			} else if (!atHome && home != null && npc.getNavigation().isDone()) {
				npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, now < hurry ? 0.9D : 0.6D);
			}
			return;
		}
		if (sitting && (sitUntil == 0L || now >= sitUntil)) {
			sit(npc, false);
			npc.memory().remove("sit_until");
			return;
		}
		if (time < 1500L && atHome && npc.memory().getLongOr("ex_day", -1L) != day) {
			// the morning exercise: slow arm circles in the doorway
			npc.memory().putLong("ex_day", day);
			npc.getNavigation().stop();
			npc.work(400 + npc.getRandom().nextInt(200));
			return;
		}
		if (time >= 5500L && time < 6500L && !sitting && npc.getNavigation().isDone() && npc.memory().getLongOr("noon_day", -1L) != day
				&& npc.getRandom().nextInt(6) == 0) {
			// a rest at noon wherever it is
			npc.memory().putLong("noon_day", day);
			npc.memory().putLong("sit_until", now + 600L + npc.getRandom().nextInt(600));
			sit(npc, true);
		}
	}

	private static void sit(KultivatorNpc npc, boolean on) {
		npc.setFlag(KultivatorNpc.FLAG_SITTING, on);
		if (on) {
			npc.getNavigation().stop();
			npc.work(0);
		}
	}

	// ================================================================== chatter

	private static void chatter(ServerLevel level, KultivatorNpc npc, long now, String[] topics) {
		if (npc.memory().contains("chat_with")) {
			continueChat(level, npc, now);
			return;
		}
		if (npc.memory().getBooleanOr("busy", false) || npc.busyTalking() || npc.hasFlag(KultivatorNpc.FLAG_SITTING)
				|| npc.hasFlag(KultivatorNpc.FLAG_WORK) || npc.getRandom().nextInt(40) != 0) {
			return;
		}
		List<KultivatorNpc> around = level.getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(CHAT_RANGE),
				other -> other != npc && other.isAlive() && other.role() == npc.role() && other.getTarget() == null
						&& !other.memory().getBooleanOr("busy", false) && !other.memory().contains("chat_with") && !other.busyTalking()
						&& !other.hasFlag(KultivatorNpc.FLAG_SITTING));
		if (around.isEmpty()) {
			return;
		}
		KultivatorNpc partner = around.get(npc.getRandom().nextInt(around.size()));
		String topic = topic(level, npc, topics);
		npc.memory().putInt("chat_with", partner.getId());
		npc.memory().putString("chat_topic", topic);
		npc.memory().putInt("chat_step", 0);
		npc.memory().putLong("chat_next", now);
		npc.memory().putBoolean("busy", true);
		partner.memory().putBoolean("busy", true);
		npc.getNavigation().stop();
		partner.getNavigation().stop();
		face(npc, partner.position());
		face(partner, npc.position());
		continueChat(level, npc, now);
	}

	/** The topic: the sky first, then the night, else a seeded pick. */
	private static String topic(ServerLevel level, KultivatorNpc npc, String[] topics) {
		SkyKind sky = DuniaApi.sky().active(level);
		if (sky != null && topics == TOWN_TOPICS) {
			return "langit_" + sky.id();
		}
		long time = Kota.timeOfDay(level.getServer());
		if (topics == TOWN_TOPICS && time >= 12000L && time < 13500L && npc.getRandom().nextBoolean()) {
			return "senja";
		}
		return topics[npc.getRandom().nextInt(topics.length)];
	}

	private static void continueChat(ServerLevel level, KultivatorNpc npc, long now) {
		net.minecraft.world.entity.Entity found = level.getEntity(npc.memory().getIntOr("chat_with", -1));
		if (!(found instanceof KultivatorNpc partner) || !partner.isAlive() || partner.distanceToSqr(npc) > (CHAT_RANGE + 3.0D) * (CHAT_RANGE + 3.0D)
				|| partner.getTarget() != null) {
			endChat(level, npc);
			return;
		}
		if (now < npc.memory().getLongOr("chat_next", 0L)) {
			return;
		}
		int step = npc.memory().getIntOr("chat_step", 0);
		if (step >= CHAT_LINES) {
			endChat(level, npc);
			return;
		}
		KultivatorNpc speaker = step % 2 == 0 ? npc : partner;
		KultivatorNpc listener = speaker == npc ? partner : npc;
		face(speaker, listener.position());
		face(listener, speaker.position());
		String chatTopic = npc.memory().getStringOr("chat_topic", "harga");
		// 42K (42.5A.11): a stored warga talks through the conversation engine (bubble); else the 38 line
		if (!dev.kultivator.dunia.kota42.cakap.ObrolanWarga.ujar(level, speaker, listener, chatTopic, step, now)) {
			speak(speaker, "kultivator.kota.obrol." + chatTopic + "." + step, LINE_TICKS);
		}
		npc.memory().putInt("chat_step", step + 1);
		npc.memory().putLong("chat_next", now + LINE_TICKS + 10L);
	}

	private static void endChat(ServerLevel level, KultivatorNpc npc) {
		if (!npc.memory().contains("chat_with")) {
			return;
		}
		net.minecraft.world.entity.Entity found = level.getEntity(npc.memory().getIntOr("chat_with", -1));
		if (found instanceof KultivatorNpc partner) {
			partner.memory().remove("busy");
		}
		npc.memory().remove("chat_with");
		npc.memory().remove("chat_topic");
		npc.memory().remove("chat_step");
		npc.memory().remove("chat_next");
		npc.memory().remove("busy");
	}

	// ================================================================== guards

	private static void guard(ServerLevel level, KultivatorNpc npc, long now) {
		// halt a player the town is hostile to
		for (ServerPlayer player : level.players()) {
			if (npc.isHostileTo(player.getUUID()) && player.distanceToSqr(npc) < 12.0D * 12.0D
					&& now - npc.memory().getLongOr("halt_t", -1000L) >= 600L) {
				npc.memory().putLong("halt_t", now);
				face(npc, player.position());
				KotaVoice.say(npc, KotaVoice.GUARD_HALT, npc.voiceKey("careful"), null);
				speak(npc, "kultivator.kota.penjaga.halt." + npc.getRandom().nextInt(3), LINE_TICKS);
				return;
			}
		}
		if (npc.memory().getBooleanOr("hold", false) || !npc.getNavigation().isDone() || npc.getRandom().nextInt(15) != 0) {
			return;
		}
		// the round: from the post to the square and back
		boolean out = npc.memory().getBooleanOr("patrol_out", false);
		BlockPos goal = out ? npc.home() : npc.memory().contains("square") ? BlockPos.of(npc.memory().getLongOr("square", 0L)) : null;
		if (goal == null) {
			return;
		}
		npc.memory().putBoolean("patrol_out", !out);
		double angle = npc.getRandom().nextDouble() * Math.PI * 2.0D;
		npc.getNavigation().moveTo(goal.getX() + 0.5D + Math.cos(angle) * 3.0D, goal.getY(), goal.getZ() + 0.5D + Math.sin(angle) * 3.0D, 0.55D);
	}

	// ================================================================== shops

	private static void shop(ServerLevel level, KultivatorNpc npc, long now) {
		Player close = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), 10.0D, each -> !each.isSpectator());
		double d2 = close == null ? Double.MAX_VALUE : close.distanceToSqr(npc);
		if (d2 > 25.0D) {
			if (!npc.hasFlag(KultivatorNpc.FLAG_WORK) && !npc.busyTalking()) {
				npc.work(200 + npc.getRandom().nextInt(200));
			}
		} else if (npc.hasFlag(KultivatorNpc.FLAG_WORK)) {
			npc.work(0);
		}
		if (close != null && d2 > 16.0D && now - npc.memory().getLongOr("hawk_t", -HAWK_GAP) >= HAWK_GAP && npc.getRandom().nextInt(4) == 0) {
			// calls a passer-by over
			npc.memory().putLong("hawk_t", now);
			npc.work(0);
			face(npc, close.position());
			speak(npc, "kultivator.kota.tawar." + npc.role().id() + "." + npc.getRandom().nextInt(3), LINE_TICKS + 20);
			if (npc.getRandom().nextBoolean()) {
				Voice.say(npc, npc.voiceKey("greet_alt"), "");
			}
		}
	}

	// ================================================================== respect

	private static void respect(KultivatorNpc npc, ServerPlayer player, long now) {
		if (player.distanceToSqr(npc) > RESPECT_RANGE * RESPECT_RANGE || npc.busyTalking() || npc.memory().contains("chat_with")) {
			return;
		}
		String key = "resp_" + Integer.toHexString(player.getUUID().hashCode());
		if (now - npc.memory().getLongOr(key, -RESPECT_GAP) < RESPECT_GAP) {
			return;
		}
		int theirs = Cultivators.get(player).realm().ordinal();
		NpcRole role = npc.role();
		if (role == NpcRole.ABADI) {
			if (theirs < Realm.TRUE_IMMORTAL.ordinal() && npc.getRandom().nextInt(4) == 0) {
				npc.memory().putLong(key, now);
				face(npc, player.position());
				speak(npc, "kultivator.kota.abadi.cela." + npc.getRandom().nextInt(4), LINE_TICKS);
			}
			return;
		}
		if (theirs < npc.realm() + 2 || npc.isHostileTo(player.getUUID())) {
			return;
		}
		npc.memory().putLong(key, now);
		face(npc, player.position());
		npc.work(0);
		npc.bow(40);
		speak(npc, (role == NpcRole.PENJAGA ? "kultivator.kota.penjaga.hormat." : "kultivator.kota.hormat.") + npc.getRandom().nextInt(4), LINE_TICKS);
	}

	// ================================================================== the sky (DESIGN 34.5.8-34.5.9 events of module L)

	private static void onSky(ServerLevel level, SkyKind kind, boolean started) {
		if (!Kota.enabled()) {
			return;
		}
		long now = level.getGameTime();
		for (KultivatorNpc npc : level.getEntities(EntityTypeTest.forClass(KultivatorNpc.class), each -> each.isAlive() && each.role().townsfolk())) {
			npc.memory().putString("react", started ? kind.id() : kind.id() + "_end");
			npc.memory().putLong("react_at", now + npc.getRandom().nextInt(80));
		}
	}

	private static void onTreasure(ServerLevel level, TreasureBirth birth) {
		if (!Kota.enabled()) {
			return;
		}
		long now = level.getGameTime();
		double range = 600.0D;
		for (KultivatorNpc npc : level.getEntities(EntityTypeTest.forClass(KultivatorNpc.class),
				each -> each.isAlive() && each.role().townsfolk() && each.blockPosition().distSqr(birth.pos()) < range * range)) {
			npc.memory().putString("react", "harta");
			npc.memory().putLong("react_at", now + npc.getRandom().nextInt(60));
			npc.memory().putLong("react_x", birth.pos().asLong());
		}
	}

	/**
	 * A sky or treasure reaction whose (staggered) moment came. A DESIGN 38 resident shows it with its own clips and then
	 * re-reads its schedule (the eclipse sends it indoors, the meteors out to the open squares; {@link TubuhPenduduk#reaksi}).
	 */
	private static void reactLater(ServerLevel level, KultivatorNpc npc, long now) {
		String react = npc.memory().getStringOr("react", "");
		if (react.isEmpty() || now < npc.memory().getLongOr("react_at", 0L)) {
			return;
		}
		npc.memory().remove("react");
		npc.memory().remove("react_at");
		if (npc.getTarget() != null || DuelRules.inDuel(npc)) {
			return;
		}
		if (npc.penduduk()) {
			BlockPos at = react.equals("harta") ? BlockPos.of(npc.memory().getLongOr("react_x", 0L)) : null;
			npc.memory().remove("react_x");
			TubuhPenduduk.reaksi(level, npc, react, at, now);
			return;
		}
		NpcRole role = npc.role();
		boolean guard = role == NpcRole.PENJAGA;
		switch (react) {
			case "blood_eclipse" -> {
				npc.gaze(50);
				speak(npc, (guard ? "kultivator.kota.penjaga.gerhana." : "kultivator.kota.langit.blood_eclipse.") + npc.getRandom().nextInt(4), LINE_TICKS);
				if (role == NpcRole.WARGA) {
					npc.memory().putLong("hurry_until", now + 2400L);
					sit(npc, false);
				}
			}
			case "meteor_shower", "twin_moons" -> {
				npc.gaze(100);
				npc.getNavigation().stop();
				speak(npc, "kultivator.kota.langit." + react + "." + npc.getRandom().nextInt(4), LINE_TICKS + 30);
			}
			case "harta" -> {
				BlockPos at = BlockPos.of(npc.memory().getLongOr("react_x", 0L));
				npc.memory().remove("react_x");
				face(npc, Vec3.atCenterOf(at));
				npc.gaze(60);
				speak(npc, "kultivator.kota.langit.harta." + npc.getRandom().nextInt(4), LINE_TICKS + 20);
			}
			default -> {
				if (react.endsWith("_end") && role == NpcRole.WARGA && npc.getRandom().nextBoolean()) {
					npc.memory().remove("hurry_until");
					speak(npc, "kultivator.kota.langit.reda." + npc.getRandom().nextInt(3), LINE_TICKS);
				}
			}
		}
	}

	// ================================================================== tests (uji kota obrol / langit)

	/** {@code uji kota obrol}: the two nearest townsfolk (made when missing) start a chat now. */
	static boolean testChat(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		List<KultivatorNpc> folk = level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(16.0D),
				each -> each.isAlive() && each.role() == NpcRole.WARGA && each.getTarget() == null);
		while (folk.size() < 2) {
			KultivatorNpc made = KotaCommands.spawnTest(player, NpcRole.WARGA);
			if (made == null) {
				return false;
			}
			folk = new java.util.ArrayList<>(folk);
			folk.add(made);
		}
		KultivatorNpc a = folk.get(0);
		KultivatorNpc b = folk.get(1);
		endChat(level, a);
		endChat(level, b);
		if (a.distanceToSqr(b) > CHAT_RANGE * CHAT_RANGE) {
			Vec3 spot = a.position().add(1.5D, 0.0D, 0.0D);
			b.snapTo(spot.x, spot.y, spot.z, b.getYRot(), 0.0F);
		}
		a.memory().putInt("chat_with", b.getId());
		a.memory().putString("chat_topic", topic(level, a, TOWN_TOPICS));
		a.memory().putInt("chat_step", 0);
		a.memory().putLong("chat_next", level.getGameTime());
		a.memory().putBoolean("busy", true);
		b.memory().putBoolean("busy", true);
		sit(a, false);
		sit(b, false);
		continueChat(level, a, level.getGameTime());
		return true;
	}

	/** {@code uji kota langit <peristiwa>}: the townsfolk within 64 blocks react as if it happened (the sky itself stays). */
	static int testReact(ServerPlayer player, String kind) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		int n = 0;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(64.0D),
				each -> each.isAlive() && each.role().townsfolk())) {
			npc.memory().putString("react", kind);
			npc.memory().putLong("react_at", now + npc.getRandom().nextInt(40));
			if ("harta".equals(kind)) {
				Vec3 ahead = player.position().add(player.getLookAngle().scale(60.0D));
				npc.memory().putLong("react_x", BlockPos.containing(ahead).asLong());
			}
			n++;
		}
		return n;
	}

	// ================================================================== helpers

	/** Turns head and body toward {@code at} (it stands, so the turn holds). */
	static void face(KultivatorNpc npc, Vec3 at) {
		double dx = at.x - npc.getX();
		double dz = at.z - npc.getZ();
		if (dx * dx + dz * dz < 1.0E-4D) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		npc.setYRot(yaw);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		npc.getLookControl().setLookAt(at.x, at.y + 1.4D, at.z);
	}

	private NpcLife() {
	}
}
