package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Balai Lelang of a town (DESIGN 34.5.1): one session at dusk (overworld time 12 000-14 000) each in-game day while a
 * player stands within 20 blocks of the auctioneer. Five lots seeded by (town, day) from the pool of the strongest
 * present realm band; four NPC bidders, members of the rival groups (34.5.3), each with a hidden limit 0.6-1.5 × base,
 * raise 5-15 % every 4 s; players bid +5 / +10 / +25 % from the Lelang tab (the action carries the lot and the price the
 * screen showed, a stale bid is refused, and the bidder must carry the stones); a lot is sold 8 s after the last raise
 * and the stones are taken only then. Consignment: two items per player per session, paid at the session's end at 0.7-1.3
 * × a value of at most half the lots' mean base. These are the rules of Lelang Abadi (26.8) in spirit stones, in a class
 * of its own.
 */
final class BalaiLelang {

	static final long DUSK_START = 12000L;
	static final long DUSK_END = 14000L;
	static final double PRESENCE = 20.0D;
	static final int LOTS = 5;
	static final int PRESENT_TICKS = 100;
	static final int RAISE_TICKS = 80;
	static final int SELL_TICKS = 160;
	static final int UNSOLD_TICKS = 300;
	static final int EMPTY_TICKS = 600;
	static final int CONSIGN_MAX = 2;
	static final int[] STEPS = {5, 10, 25};

	static final class Lot {
		ItemStack stack;
		long base;
		long price;
		/** 0 waiting, 1 open, 2 sold, 3 unsold. */
		int state;
		@Nullable UUID player;
		int bidder = -1;
		long openAt;
		long lastRaise = -1L;

		Lot(ItemStack stack, long base) {
			this.stack = stack;
			this.base = base;
			this.price = base;
		}

		boolean hasBid() {
			return player != null || bidder >= 0;
		}
	}

	static final class Bidder {
		final Component name;
		final double limit;
		final String group;

		Bidder(Component name, double limit, String group) {
			this.name = name;
			this.limit = limit;
			this.group = group;
		}
	}

	static final class Consign {
		final UUID player;
		final ItemStack stack;
		final long value;

		Consign(UUID player, ItemStack stack, long value) {
			this.player = player;
			this.stack = stack;
			this.value = value;
		}
	}

	static final class Session {
		final long town;
		final long day;
		final ServerLevel level;
		final List<Lot> lots = new ArrayList<>();
		final List<Bidder> bidders = new ArrayList<>();
		final List<Consign> consigned = new ArrayList<>();
		final RandomSource random;
		int current;
		long lastPresence;
		/** Started by a test command: runs from the server tick, any hour. */
		boolean test;
		@Nullable KultivatorNpc auctioneer;

		Session(ServerLevel level, long town, long day) {
			this.level = level;
			this.town = town;
			this.day = day;
			this.random = RandomSource.create(town * 977L + day);
		}

		@Nullable Lot lot() {
			return current >= 0 && current < lots.size() ? lots.get(current) : null;
		}

		long meanBase() {
			long sum = 0L;
			for (Lot lot : lots) {
				sum += lot.base;
			}
			return lots.isEmpty() ? 0L : sum / lots.size();
		}
	}

	private static final Map<Long, Session> SESSIONS = new HashMap<>();

	static void clear() {
		SESSIONS.clear();
	}

	static boolean sessionRunning(long town) {
		return SESSIONS.containsKey(town);
	}

	/** Every 20 ticks while a player is near the auctioneer's anchor: starts the day's session, runs the lots. */
	static void nearby(ServerLevel level, DuniaAnchorEntity anchor, long town, List<ServerPlayer> within) {
		if (!Kota.config().auction) {
			return;
		}
		KultivatorNpc auctioneer = KotaAnchors.HANDLER.npcOf(anchor);
		long now = level.getGameTime();
		boolean present = false;
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(anchor.position()) <= PRESENCE * PRESENCE) {
				present = true;
				break;
			}
		}
		Session session = SESSIONS.get(town);
		if (session == null) {
			long time = Kota.timeOfDay(level.getServer());
			long day = Kota.day(level.getServer());
			CompoundTag data = KotaData.get(level.getServer()).town(town);
			if (!present || time < DUSK_START || time >= DUSK_END || data.getLongOr("auction_day", -1L) == day || auctioneer == null) {
				return;
			}
			data.putLong("auction_day", day);
			KotaData.get(level.getServer()).touch();
			session = start(level, town, day, within, now);
			session.auctioneer = auctioneer;
			SESSIONS.put(town, session);
			KotaVoice.say(auctioneer, KotaVoice.AUCTION_OPEN, auctioneer.voiceKey("cheer"), "kultivator.kota.lelang.suara.buka");
			for (ServerPlayer player : within) {
				Msg.chat(player, Component.translatable("kultivator.kota.lelang.mulai", KotaNames.town(town)).withStyle(ChatFormatting.GOLD));
			}
		}
		if (session.test) {
			return;
		}
		session.auctioneer = auctioneer;
		if (present) {
			session.lastPresence = now;
		}
		run(session, now);
		if (auctioneer != null) {
			Kota.refreshAround(auctioneer, PRESENCE + 4.0D);
		}
	}

	/** The server tick: sessions whose auctioneer is gone or nobody stays end (every second). */
	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L || SESSIONS.isEmpty()) {
			return;
		}
		tickTests(server);
		for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
			Session session = it.next();
			long now = session.level.getGameTime();
			long time = Kota.timeOfDay(server);
			boolean late = !session.test && (time >= DUSK_END + 2000L || time < DUSK_START - 1000L);
			boolean over = session.current >= session.lots.size() || now - session.lastPresence > EMPTY_TICKS || late;
			if (over) {
				finish(session);
				it.remove();
			}
		}
	}

	static void onLeave(ServerPlayer player) {
		// a bid in progress stays; the stones are checked when the lot is sold
	}

	// ================================================================== the session

	private static Session start(ServerLevel level, long town, long day, List<ServerPlayer> within, long now) {
		Session session = new Session(level, town, day);
		int band = 0;
		for (ServerPlayer player : within) {
			band = Math.max(band, Cultivators.get(player).realm().ordinal());
		}
		for (int i = 0; i < LOTS; i++) {
			Lot lot = makeLot(session.random, band);
			if (lot != null) {
				session.lots.add(lot);
			}
		}
		int rivals = Kota.config().auctionRivals;
		List<Saingan.Member> members = Saingan.bidders(level.getServer(), session.random, rivals);
		for (Saingan.Member member : members) {
			session.bidders.add(new Bidder(member.name(), 0.6D + session.random.nextDouble() * 0.9D, member.group()));
		}
		session.current = 0;
		session.lastPresence = now;
		if (!session.lots.isEmpty()) {
			session.lots.getFirst().openAt = now + PRESENT_TICKS;
			session.lots.getFirst().state = 1;
		}
		return session;
	}

	/** A lot of the band's pool (34.5.1). */
	private static @Nullable Lot makeLot(RandomSource random, int band) {
		ItemStack stack;
		if (band <= 2) {
			stack = switch (random.nextInt(6)) {
				case 0 -> Harga.item("foundation_pill");
				case 1 -> SpiritHerbs.randomFor(null, random, 100, 300);
				case 2 -> BeastCores.create(Harga.randomElement(random), 1 + random.nextInt(2));
				case 3 -> Harga.item("jade_slip");
				case 4 -> Harga.item("storage_pouch").isEmpty() ? Harga.item("jade_slip") : dev.kultivator.tempa.StorageRingItem.create(0, null);
				default -> Harga.item("marrow_cleansing_pill");
			};
		} else if (band == 3) {
			stack = switch (random.nextInt(6)) {
				case 0 -> Harga.item("golden_core_pill");
				case 1 -> Harga.item("token_darah_api");
				case 2 -> Harga.item("heavenvoid_fragment");
				case 3 -> BeastCores.create(Harga.randomElement(random), 2 + random.nextInt(2));
				default -> ArtifactItem.create(ArtifactItem.FORMS.get(random.nextInt(ArtifactItem.FORMS.size())), 3 + random.nextInt(3), random.nextInt(3), 0);
			};
		} else {
			if (random.nextInt(100) < 3 && Harga.exists("heavenly_flame_seed")) {
				stack = Harga.item("heavenly_flame_seed");
			} else {
				stack = switch (random.nextInt(4)) {
					case 0 -> Harga.item("soul_nurturing_wood");
					case 1 -> Harga.item("soul_seal_jade");
					case 2 -> SpiritHerbs.randomFor(null, random, 1000, 1000);
					default -> BeastCores.create(Harga.randomElement(random), 4);
				};
			}
		}
		if (stack == null || stack.isEmpty()) {
			stack = Harga.item("jade_slip");
		}
		if (stack.isEmpty()) {
			return null;
		}
		long base = Math.max(1L, Math.round(Harga.value(stack) * Kota.config().priceScale));
		return new Lot(stack, base);
	}

	private static void run(Session session, long now) {
		Lot lot = session.lot();
		if (lot == null) {
			return;
		}
		if (lot.state == 0) {
			lot.state = 1;
			lot.openAt = now + PRESENT_TICKS;
		}
		if (now < lot.openAt) {
			return;
		}
		// NPC bidders raise every 4 s while under their limit
		if (!session.bidders.isEmpty() && (lot.lastRaise < 0L ? now - lot.openAt >= 40L : now - lot.lastRaise >= RAISE_TICKS)) {
			List<Integer> willing = new ArrayList<>();
			for (int i = 0; i < session.bidders.size(); i++) {
				Bidder bidder = session.bidders.get(i);
				long next = nextPrice(lot, 5 + session.random.nextInt(11));
				if (i != lot.bidder && next <= lot.base * bidder.limit) {
					willing.add(i);
				}
			}
			if (!willing.isEmpty() && session.random.nextFloat() < 0.75F) {
				int who = willing.get(session.random.nextInt(willing.size()));
				lot.price = nextPrice(lot, 5 + session.random.nextInt(11));
				lot.bidder = who;
				lot.player = null;
				lot.lastRaise = now;
				announceRaise(session, lot, session.bidders.get(who).name);
			}
		}
		if (lot.hasBid() && now - lot.lastRaise >= SELL_TICKS) {
			sell(session, lot, now);
		} else if (!lot.hasBid() && now - lot.openAt >= UNSOLD_TICKS) {
			lot.state = 3;
			advance(session, now);
		}
	}

	/** The next price for a +{@code percent} % raise (the base when nobody bid yet). */
	static long nextPrice(Lot lot, int percent) {
		if (!lot.hasBid()) {
			return lot.base;
		}
		return Math.max(lot.price + 1L, (long) Math.ceil(lot.price * (1.0D + percent / 100.0D)));
	}

	private static void announceRaise(Session session, Lot lot, Component who) {
		for (ServerPlayer player : session.level.players()) {
			if (session.auctioneer != null && player.distanceToSqr(session.auctioneer) <= (PRESENCE + 8.0D) * (PRESENCE + 8.0D)) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.naik", who, NilaiBatu.text(lot.price)).withStyle(ChatFormatting.YELLOW));
			}
		}
		if (session.auctioneer != null) {
			session.level.playSound(null, session.auctioneer.getX(), session.auctioneer.getY(), session.auctioneer.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(),
					SoundSource.NEUTRAL, 0.6F, 1.2F);
		}
	}

	private static void sell(Session session, Lot lot, long now) {
		if (lot.player != null) {
			ServerPlayer winner = session.level.getServer().getPlayerList().getPlayer(lot.player);
			if (winner != null && NilaiBatu.pay(winner, lot.price)) {
				ItemStack prize = lot.stack.copy();
				Component name = prize.getHoverName();
				if (!winner.getInventory().add(prize) && !prize.isEmpty()) {
					Kota.drop(winner, prize);
				}
				lot.state = 2;
				Msg.chat(winner, Component.translatable("kultivator.kota.lelang.menang", name, NilaiBatu.text(lot.price)).withStyle(ChatFormatting.GREEN));
				KotaData.get(session.level.getServer()).addReputation(session.town, winner.getUUID(), 1);
				Advancements.grant(winner, "dunia/kota_auction");
				if (session.auctioneer != null) {
					KotaVoice.say(session.auctioneer, KotaVoice.AUCTION_SOLD, session.auctioneer.voiceKey("victory"), "kultivator.kota.lelang.suara.terjual");
				}
			} else {
				// the winner could not pay (or left): the last NPC bidder takes it, the town remembers
				if (winner != null) {
					KotaData.get(session.level.getServer()).addReputation(session.town, winner.getUUID(), -5);
					Msg.chat(winner, Component.translatable("kultivator.kota.lelang.gagal_bayar").withStyle(ChatFormatting.RED));
				}
				lot.player = null;
				lot.bidder = session.bidders.isEmpty() ? -1 : 0;
				lot.state = lot.bidder >= 0 ? 2 : 3;
			}
		} else {
			lot.state = 2;
			if (session.auctioneer != null) {
				KotaVoice.say(session.auctioneer, KotaVoice.AUCTION_SOLD, session.auctioneer.voiceKey("victory"), "kultivator.kota.lelang.suara.terjual");
			}
		}
		advance(session, now);
	}

	private static void advance(Session session, long now) {
		session.current++;
		Lot next = session.lot();
		if (next != null) {
			next.state = 1;
			next.openAt = now + PRESENT_TICKS;
		}
	}

	/** The end: consigned items are paid (0.7-1.3 × their capped value). */
	private static void finish(Session session) {
		long cap = Math.max(1L, session.meanBase() / 2L);
		for (Consign consign : session.consigned) {
			ServerPlayer player = session.level.getServer().getPlayerList().getPlayer(consign.player);
			long value = Math.min(consign.value, cap);
			long paid = Math.max(1L, Math.round(value * (0.7D + session.random.nextDouble() * 0.6D)));
			if (player != null) {
				NilaiBatu.give(player, paid);
				Msg.chat(player, Component.translatable("kultivator.kota.lelang.titipan_laku", consign.stack.getHoverName(), NilaiBatu.text(paid))
						.withStyle(ChatFormatting.GOLD));
			} else {
				// offline: the stones wait in the player's record and are paid on the next visit
				CompoundTag tag = KotaData.get(session.level.getServer()).player(consign.player);
				tag.putLong("owed", tag.getLongOr("owed", 0L) + paid);
				KotaData.get(session.level.getServer()).touch();
			}
		}
		session.consigned.clear();
		if (session.auctioneer != null && !session.auctioneer.isRemoved()) {
			for (ServerPlayer player : session.level.players()) {
				if (player.distanceToSqr(session.auctioneer) <= 32.0D * 32.0D) {
					Msg.chat(player, Component.translatable("kultivator.kota.lelang.selesai").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	// ================================================================== actions

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.JURU_LELANG) {
			return false;
		}
		Session session = SESSIONS.get(npc.town());
		if (session == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.tidak_ada").withStyle(ChatFormatting.RED));
			return true;
		}
		if ("titip".equals(verb)) {
			return consign(player, session);
		}
		int index = Kota.arg(parts, 1, -1);
		long seen = Kota.arg(parts, 2, -1);
		int step = Kota.arg(parts, 3, 5);
		if (step != 5 && step != 10 && step != 25) {
			return true;
		}
		Lot lot = session.lot();
		long now = session.level.getGameTime();
		if (lot == null || index != session.current || lot.state != 1 || now < lot.openAt) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.usang").withStyle(ChatFormatting.RED));
			return true;
		}
		if (seen != lot.price) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.usang").withStyle(ChatFormatting.RED));
			return true;
		}
		if (player.getUUID().equals(lot.player)) {
			return true;
		}
		long offer = nextPrice(lot, step);
		if (NilaiBatu.total(player) < offer) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(offer)).withStyle(ChatFormatting.RED));
			return true;
		}
		lot.price = offer;
		lot.player = player.getUUID();
		lot.bidder = -1;
		lot.lastRaise = now;
		announceRaise(session, lot, player.getDisplayName());
		return true;
	}

	private static boolean consign(ServerPlayer player, Session session) {
		int mine = 0;
		for (Consign consign : session.consigned) {
			if (consign.player.equals(player.getUUID())) {
				mine++;
			}
		}
		if (mine >= CONSIGN_MAX) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.titip_penuh", CONSIGN_MAX).withStyle(ChatFormatting.RED));
			return true;
		}
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		long value = Harga.value(hand) * Math.max(1, hand.getCount());
		if (hand.isEmpty() || value <= 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.titip_tolak").withStyle(ChatFormatting.RED));
			return true;
		}
		ItemStack taken = hand.copy();
		hand.shrink(hand.getCount());
		session.consigned.add(new Consign(player.getUUID(), taken, value));
		Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.dititip", taken.getHoverName()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Pays stones a consignment earned while the player was away (on their next screen). */
	static void payOwed(ServerPlayer player) {
		KotaData data = KotaData.get(player.level().getServer());
		CompoundTag tag = data.player(player.getUUID());
		long owed = tag.getLongOr("owed", 0L);
		if (owed > 0L) {
			tag.remove("owed");
			data.touch();
			NilaiBatu.give(player, owed);
			Msg.chat(player, Component.translatable("kultivator.kota.lelang.utang_dibayar", NilaiBatu.text(owed)).withStyle(ChatFormatting.GOLD));
		}
	}


	// ================================================================== tests

	/** {@code uji kota lelang mulai}: a session at this auctioneer now, whatever the hour. */
	static void forceStart(ServerPlayer player, KultivatorNpc auctioneer) {
		ServerLevel level = (ServerLevel) player.level();
		long town = auctioneer.town();
		Session old = SESSIONS.remove(town);
		if (old != null) {
			finish(old);
		}
		Session session = start(level, town, Kota.day(level.getServer()), List.of(player), level.getGameTime());
		session.auctioneer = auctioneer;
		session.test = true;
		SESSIONS.put(town, session);
		KotaVoice.say(auctioneer, KotaVoice.AUCTION_OPEN, auctioneer.voiceKey("cheer"), "kultivator.kota.lelang.suara.buka");
	}

	/** {@code uji kota lelang tutup}: ends every session now (consignments are paid). */
	static void forceClose(MinecraftServer server) {
		for (Session session : SESSIONS.values()) {
			finish(session);
		}
		SESSIONS.clear();
	}

	/** {@code uji kota lelang tawar <persen>}: the player bids on the current lot. */
	static void testBid(ServerPlayer player, KultivatorNpc auctioneer, int percent) {
		Session session = SESSIONS.get(auctioneer.town());
		Lot lot = session == null ? null : session.lot();
		if (lot == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.tidak_ada").withStyle(ChatFormatting.RED));
			return;
		}
		lot.openAt = Math.min(lot.openAt, session.level.getGameTime());
		int step = percent >= 25 ? 25 : percent >= 10 ? 10 : 5;
		action(player, auctioneer, "tawar", new String[]{"tawar", Integer.toString(session.current), Long.toString(lot.price), Integer.toString(step)});
	}

	/** Test sessions run every 20 ticks from the server tick even without the anchor (a test auctioneer has none). */
	static void tickTests(MinecraftServer server) {
		for (Session session : SESSIONS.values()) {
			if (session.test && session.auctioneer != null && !session.auctioneer.isRemoved()) {
				long now = session.level.getGameTime();
				for (ServerPlayer player : session.level.players()) {
					if (player.distanceToSqr(session.auctioneer) <= PRESENCE * PRESENCE) {
						session.lastPresence = now;
					}
				}
				run(session, now);
				Kota.refreshAround(session.auctioneer, PRESENCE + 4.0D);
			}
		}
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		Session session = SESSIONS.get(npc.town());
		long now = player.level().getGameTime();
		long time = Kota.timeOfDay(player.level().getServer());
		tag.putBoolean("enabled", Kota.config().auction);
		tag.putBoolean("active", session != null);
		long untilDusk = Math.floorMod(DUSK_START - time, 24000L);
		tag.putLong("next", untilDusk);
		boolean doneToday = KotaData.get(player.level().getServer()).town(npc.town()).getLongOr("auction_day", -1L) == Kota.day(player.level().getServer());
		tag.putBoolean("done_today", doneToday && session == null);
		tag.putInt("consign_max", CONSIGN_MAX);
		if (session == null) {
			return tag;
		}
		tag.putInt("current", session.current);
		ListTag lots = new ListTag();
		for (int i = 0; i < session.lots.size(); i++) {
			Lot lot = session.lots.get(i);
			CompoundTag entry = new CompoundTag();
			entry.putString("item", BuiltInRegistries.ITEM.getKey(lot.stack.getItem()).toString());
			entry.store("name", ComponentSerialization.CODEC, lot.stack.getHoverName());
			entry.putLong("base", lot.base);
			entry.putLong("price", lot.price);
			entry.putInt("state", lot.state);
			entry.putLong("present", Math.max(0L, lot.openAt - now));
			entry.putLong("sell", lot.hasBid() ? Math.max(0L, lot.lastRaise + SELL_TICKS - now) : -1L);
			entry.putBoolean("mine", player.getUUID().equals(lot.player));
			Component leader = lot.player != null ? nameOf(player, lot.player) : lot.bidder >= 0 && lot.bidder < session.bidders.size()
					? session.bidders.get(lot.bidder).name : Component.empty();
			entry.store("leader", ComponentSerialization.CODEC, leader);
			for (int s = 0; s < STEPS.length; s++) {
				entry.putLong("offer" + STEPS[s], nextPrice(lot, STEPS[s]));
			}
			lots.add(entry);
		}
		tag.put("lots", lots);
		ListTag bidders = new ListTag();
		for (Bidder bidder : session.bidders) {
			CompoundTag entry = new CompoundTag();
			entry.store("name", ComponentSerialization.CODEC, bidder.name);
			entry.putInt("color", Saingan.colorOf(bidder.group));
			bidders.add(entry);
		}
		tag.put("bidders", bidders);
		ListTag consign = new ListTag();
		for (Consign each : session.consigned) {
			if (each.player.equals(player.getUUID())) {
				CompoundTag entry = new CompoundTag();
				entry.store("name", ComponentSerialization.CODEC, each.stack.getHoverName());
				entry.putLong("value", Math.min(each.value, Math.max(1L, session.meanBase() / 2L)));
				consign.add(entry);
			}
		}
		tag.put("consign", consign);
		return tag;
	}

	private static Component nameOf(ServerPlayer viewer, UUID id) {
		ServerPlayer player = viewer.level().getServer().getPlayerList().getPlayer(id);
		return player == null ? Component.translatable("kultivator.kota.lelang.seseorang") : player.getDisplayName();
	}

	private BalaiLelang() {
	}
}
