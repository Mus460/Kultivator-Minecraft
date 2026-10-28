package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.ekonomi.LelangAgung;
import dev.kultivator.dunia.tempat.ekonomi.ProfilLelang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.StorageRingItem;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
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
 * and the stones are taken only then. Consignment ({@link Titipan}, the same rules as Lelang Abadi 26.8): one piece of a
 * town good per consignment, two per player per session, paid at the session's end at 0.6-1.0 × its value capped at half
 * the lots' mean base. Consignments are saved in {@code KotaData} at once, and the server stopping closes every session
 * first ({@link #forceClose}), so a consigned piece is always paid (audit T14).
 * <p>
 * DESIGN 38.M.2.3 makes this the <b>one</b> auction engine of Nilai Batu: {@link #mulai} starts a session with a
 * {@link ProfilLelang} at any auctioneer anchor (the capital's Lelang Agung, 38TK's Lelang Gelap). A profile other than
 * {@code kota} takes its lots from the profile's lot table ({@code LelangAgung.lotRumah}), its bidders from the profile's
 * sources, puts the players' consignments up as real lots after the house lots (kept in SavedData by
 * {@code LelangAgung}, the seller paid the price less the commission), takes the commission of every sale into the town
 * treasury, and runs from the server tick while a player stays near its auctioneer, until its closing hour. The live
 * screen {@code lelang} (38.U.3.3) reads a session through {@link #lembarSesi} and bids through {@link #tawarLembar}.
 */
public final class BalaiLelang {

	static final long DUSK_START = 12000L;
	static final long DUSK_END = 14000L;
	static final double PRESENCE = 20.0D;
	static final int LOTS = 5;
	static final int PRESENT_TICKS = 100;
	static final int RAISE_TICKS = 80;
	static final int SELL_TICKS = 160;
	static final int UNSOLD_TICKS = 300;
	static final int EMPTY_TICKS = 600;
	static final int CONSIGN_MAX = Titipan.PER_SESSION;
	static final int[] STEPS = {5, 10, 25};

	static final class Lot {
		ItemStack stack;
		long base;
		long price;
		/** 0 waiting, 1 open, 2 sold, 3 unsold. */
		int state;
		@Nullable UUID player;
		int bidder = -1;
		/** The last NPC bid (price and bidder, 0 / -1 for none): what a defaulted player bid falls back to. */
		long npcPrice;
		int npcBidder = -1;
		long openAt;
		long lastRaise = -1L;
		/** A player's consigned piece (38.M.2.3): the seller and the consignment id, or null / 0 for a house lot. */
		@Nullable UUID seller;
		Component sellerName = Component.empty();
		long consignment;
		/** The closed box of the Grand Auction (shown as a mystery lot). */
		boolean mystery;
		/** Highest price an NPC bidder offers (a consignment's {@code LelangAgung.plafonTitip}; no cap for house lots). */
		long ceiling = Long.MAX_VALUE;
		/** The bids on this lot, newest last: [name Component, price Long, player UUID or null, game time Long]. */
		final List<Object[]> bids = new ArrayList<>();

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

	static final class Session {
		final long town;
		final long day;
		final ServerLevel level;
		final List<Lot> lots = new ArrayList<>();
		final List<Bidder> bidders = new ArrayList<>();
		final RandomSource random;
		int current;
		long lastPresence;
		/** Started by a test command: runs from the server tick, any hour. */
		boolean test;
		@Nullable KultivatorNpc auctioneer;
		/** The profile (38.M.2.3); {@link ProfilLelang#KOTA} for the town sessions of 34.5.1. */
		ProfilLelang profile = ProfilLelang.KOTA;
		/** Started through {@link #mulai}: runs from the server tick until {@link #deadline} (game time). */
		boolean standalone;
		long deadline = Long.MAX_VALUE;
		/** Game time of the last change a live screen must show. */
		long changed;

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
		if (session.test || session.standalone) {
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
			boolean late = !session.test && (session.standalone ? now >= session.deadline : time >= DUSK_END + 2000L || time < DUSK_START - 1000L);
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
				case 4 -> Harga.item("storage_pouch").isEmpty() ? Harga.item("jade_slip") : StorageRingItem.create(0, null);
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
				if (i != lot.bidder && next <= lot.base * bidder.limit && next <= lot.ceiling) {
					willing.add(i);
				}
			}
			if (!willing.isEmpty() && session.random.nextFloat() < 0.75F) {
				int who = willing.get(session.random.nextInt(willing.size()));
				lot.price = nextPrice(lot, 5 + session.random.nextInt(11));
				lot.bidder = who;
				lot.npcPrice = lot.price;
				lot.npcBidder = who;
				lot.player = null;
				lot.lastRaise = now;
				lot.bids.add(new Object[] {session.bidders.get(who).name, lot.price, null, now});
				session.changed = now;
				announceRaise(session, lot, session.bidders.get(who).name);
			}
		}
		if (lot.hasBid() && now - lot.lastRaise >= SELL_TICKS) {
			sell(session, lot, now);
		} else if (!lot.hasBid() && now - lot.openAt >= UNSOLD_TICKS) {
			lot.state = 3;
			if (lot.consignment != 0L) {
				LelangAgung.kembali(session.level.getServer(), session.town, lot.consignment);
			}
			advance(session, now);
		}
		if (session.changed == now) {
			LelangAgung.segarkan(session.level.getServer(), session.town);
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
		MinecraftServer server = session.level.getServer();
		double commission = session.profile.komisiBerlaku();
		if (lot.player != null) {
			ServerPlayer winner = server.getPlayerList().getPlayer(lot.player);
			if (winner != null && NilaiBatu.pay(winner, lot.price)) {
				ItemStack prize = lot.stack.copy();
				Component name = prize.getHoverName();
				if (!winner.getInventory().add(prize) && !prize.isEmpty()) {
					Kota.drop(winner, prize);
				}
				lot.state = 2;
				Msg.chat(winner, Component.translatable("kultivator.kota.lelang.menang", name, NilaiBatu.text(lot.price)).withStyle(ChatFormatting.GREEN));
				KotaData.get(server).addReputation(session.town, winner.getUUID(), 1);
				Advancements.grant(winner, "dunia/kota_auction");
				if (!session.profile.kota()) {
					Advancements.grant(winner, "tempat/lelang_agung");
					LelangAgung.menang(winner, session.town, name, lot.price);
				}
				if (commission > 0.0D && lot.consignment == 0L) {
					// a house lot: the commission share of the price is the town's (38.M.5 sink)
					KasKota.tambah(server, session.town, Math.round(lot.price * commission));
				}
				if (session.auctioneer != null) {
					KotaVoice.say(session.auctioneer, KotaVoice.AUCTION_SOLD, session.auctioneer.voiceKey("victory"), "kultivator.kota.lelang.suara.terjual");
				}
			} else {
				// the winner could not pay (or left): the last NPC bidder takes it at his own (capped) bid, the town remembers
				if (winner != null) {
					KotaData.get(server).addReputation(session.town, winner.getUUID(), -5);
					Msg.chat(winner, Component.translatable("kultivator.kota.lelang.gagal_bayar").withStyle(ChatFormatting.RED));
				}
				lot.player = null;
				lot.bidder = lot.npcBidder;
				lot.price = lot.npcPrice;
				lot.state = lot.bidder >= 0 ? 2 : 3;
			}
		} else {
			lot.state = 2;
			if (session.auctioneer != null) {
				KotaVoice.say(session.auctioneer, KotaVoice.AUCTION_SOLD, session.auctioneer.voiceKey("victory"), "kultivator.kota.lelang.suara.terjual");
			}
		}
		if (lot.consignment != 0L) {
			if (lot.state == 2) {
				long fee = Math.round(lot.price * commission);
				KasKota.tambah(server, session.town, fee);
				LelangAgung.terjual(server, session.town, lot.consignment, lot.price, fee);
			} else {
				LelangAgung.kembali(server, session.town, lot.consignment);
			}
		}
		session.changed = now;
		advance(session, now);
	}

	private static void advance(Session session, long now) {
		session.current++;
		session.changed = now;
		Lot next = session.lot();
		if (next != null) {
			next.state = 1;
			next.openAt = now + PRESENT_TICKS;
		}
	}

	/** The end: consigned pieces are paid (0.6-1.0 × their capped value; offline players on their next visit). */
	private static void finish(Session session) {
		if (session.profile.kota()) {
			Titipan.settle(session.level.getServer(), session.town, session.day, session.random);
		} else {
			// consigned lots never reached (time ran out, everybody left) go back to their owners
			LelangAgung.selesai(session.level.getServer(), session.town);
		}
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
		if (player.getUUID().equals(lot.player) || lot.seller != null && lot.seller.equals(player.getUUID())) {
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
		lot.bids.add(new Object[] {player.getDisplayName(), offer, player.getUUID(), now});
		session.changed = now;
		announceRaise(session, lot, player.getDisplayName());
		LelangAgung.segarkan(session.level.getServer(), session.town);
		return true;
	}

	/** Consigns one piece of the main-hand stack ({@link Titipan}: whitelist, two per session, saved at once). */
	private static boolean consign(ServerPlayer player, Session session) {
		KotaData data = KotaData.get(player.level().getServer());
		if (Titipan.count(data, player.getUUID(), session.town, session.day) >= CONSIGN_MAX) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.titip_penuh", CONSIGN_MAX).withStyle(ChatFormatting.RED));
			return true;
		}
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (StorageRingItem.unsafeToSell(hand)) {
			// a pouch or ring that holds items or is bound would lose everything inside (audit K2)
			Msg.actionBar(player, Component.translatable("kultivator.ekonomi.cincin_berisi").withStyle(ChatFormatting.RED));
			return true;
		}
		if (!Titipan.townAccepts(hand)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.titip_tolak").withStyle(ChatFormatting.RED));
			return true;
		}
		long value = Math.min(Harga.value(hand), Math.max(1L, session.meanBase() / 2L));
		Component name = hand.getHoverName().copy();
		hand.shrink(1);
		Titipan.add(data, player.getUUID(), session.town, session.day, name, value);
		Msg.actionBar(player, Component.translatable("kultivator.kota.lelang.dititip", name).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/**
	 * Pays stones a consignment earned while the player was away (on their next screen), and first every consigned piece
	 * whose session no longer runs (a stop or crash before its end).
	 */
	static void payOwed(ServerPlayer player) {
		Titipan.payStale(player, (town, day) -> {
			Session running = SESSIONS.get(town);
			return running != null && running.day == day;
		});
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


	// ================================================================== the one engine, public (38.M.2.3)

	/**
	 * Starts a session of {@code profil} at the auctioneer anchor {@code juru} (38.M.2.3: the capital's Lelang Agung,
	 * 38TK's Lelang Gelap). The session is keyed by the anchor's site; its auctioneer is the {@link NpcRole#JURU_LELANG}
	 * standing near the anchor, or a body made for it ({@code HidupApi.tubuh}). It runs from the server tick while a
	 * player stays within 20 blocks of the auctioneer, until its lots are done or its time is up. False when a session of
	 * that site already runs, the anchor has no site, or no auctioneer could stand there.
	 */
	public static boolean mulai(ServerLevel level, DuniaAnchorEntity juru, ProfilLelang profil) {
		long key = juru.site();
		if (key == 0L || SESSIONS.containsKey(key)) {
			return false;
		}
		KultivatorNpc auctioneer = juruDekat(level, juru.blockPosition());
		if (auctioneer == null) {
			auctioneer = HidupApi.tubuhStaf(level, key, NpcRole.JURU_LELANG, juru.blockPosition(), juru.getYRot());
		}
		if (auctioneer == null) {
			return false;
		}
		List<ServerPlayer> within = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(juru) <= (PRESENCE + 12.0D) * (PRESENCE + 12.0D)) {
				within.add(player);
			}
		}
		mulai(level, key, auctioneer, profil, within, false);
		return true;
	}

	/**
	 * {@link #mulai(ServerLevel, DuniaAnchorEntity, ProfilLelang)} at an auctioneer body already standing (its anchor's
	 * site, else its town, is the key); false when a session of that key runs.
	 */
	public static boolean mulai(ServerLevel level, KultivatorNpc juru, ProfilLelang profil) {
		long key = kunci(juru);
		if (key == 0L || SESSIONS.containsKey(key)) {
			return false;
		}
		List<ServerPlayer> within = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(juru) <= (PRESENCE + 12.0D) * (PRESENCE + 12.0D)) {
				within.add(player);
			}
		}
		mulai(level, key, juru, profil, within, false);
		return true;
	}

	/** {@code uji ekonomi lelang}: a session of {@code profil} at this auctioneer now, whatever the hour. */
	public static void mulaiUji(ServerPlayer player, KultivatorNpc auctioneer, ProfilLelang profil) {
		ServerLevel level = (ServerLevel) player.level();
		long key = kunci(auctioneer);
		Session old = SESSIONS.remove(key);
		if (old != null) {
			finish(old);
		}
		mulai(level, key, auctioneer, profil, List.of(player), true);
	}

	/**
	 * The session key of an auctioneer: the town whose dusk session runs right now (34.5.1 sessions are keyed by town),
	 * else its anchor's site (profile sessions of {@link #mulai}), else its town.
	 */
	public static long kunci(KultivatorNpc auctioneer) {
		long town = auctioneer.town();
		if (town != 0L && SESSIONS.containsKey(town)) {
			return town;
		}
		return auctioneer.anchorSite() != 0L ? auctioneer.anchorSite() : town;
	}

	private static void mulai(ServerLevel level, long key, KultivatorNpc auctioneer, ProfilLelang profil, List<ServerPlayer> within, boolean test) {
		long now = level.getGameTime();
		Session session = profil.kota() ? start(level, key, Kota.day(level.getServer()), within, now) : startProfile(level, key, within, now, profil);
		session.auctioneer = auctioneer;
		session.standalone = true;
		session.test = test;
		session.deadline = now + Math.max(6000L, 900L * Math.max(1, session.lots.size()));
		SESSIONS.put(key, session);
		KotaVoice.say(auctioneer, KotaVoice.AUCTION_OPEN, auctioneer.voiceKey("cheer"), "kultivator.kota.lelang.suara.buka");
		for (ServerPlayer player : within) {
			Msg.chat(player, Component.translatable(profil.kota() ? "kultivator.kota.lelang.mulai" : "kultivator.tempat_ekonomi.lelang.mulai",
					KotaNames.town(key)).withStyle(ChatFormatting.GOLD));
		}
		LelangAgung.segarkan(level.getServer(), key);
	}

	/** A session of a profile other than {@code kota}: house lots of its table, then the consigned pieces. */
	private static Session startProfile(ServerLevel level, long key, List<ServerPlayer> within, long now, ProfilLelang profil) {
		Session session = new Session(level, key, Kota.day(level.getServer()));
		session.profile = profil;
		int band = 0;
		for (ServerPlayer player : within) {
			band = Math.max(band, Cultivators.get(player).realm().ordinal());
		}
		for (int i = 0; i < profil.lot(); i++) {
			LelangAgung.LotLelang made = LelangAgung.buatLot(level, profil, profil.kelas(i), band, session.random);
			if (made != null) {
				session.lots.add(lot(made));
			}
		}
		for (LelangAgung.LotLelang consigned : LelangAgung.lotTitipan(level.getServer(), key)) {
			session.lots.add(lot(consigned));
		}
		int wanted = Kota.config().auctionRivals + 2;
		if (profil.sumberPenawar().contains("saingan")) {
			for (Saingan.Member member : Saingan.bidders(level.getServer(), session.random, Math.max(1, wanted / 2))) {
				session.bidders.add(new Bidder(member.name(), 0.6D + session.random.nextDouble() * 0.9D, member.group()));
			}
		}
		for (LelangAgung.Penawar bidder : LelangAgung.penawar(level.getServer(), key, profil, session.random, Math.max(1, wanted - session.bidders.size()))) {
			session.bidders.add(new Bidder(bidder.nama(), 0.6D + session.random.nextDouble() * 0.9D, bidder.kelompok()));
		}
		session.current = 0;
		session.lastPresence = now;
		if (!session.lots.isEmpty()) {
			session.lots.getFirst().openAt = now + PRESENT_TICKS;
			session.lots.getFirst().state = 1;
		}
		return session;
	}

	private static Lot lot(LelangAgung.LotLelang made) {
		Lot lot = new Lot(made.barang(), Math.max(1L, made.dasar()));
		lot.seller = made.penjual();
		lot.sellerName = made.namaPenjual();
		lot.consignment = made.titipId();
		if (lot.consignment != 0L) {
			lot.ceiling = LelangAgung.plafonTitip(lot.stack);
		}
		lot.mystery = made.misteri();
		return lot;
	}

	private static @Nullable KultivatorNpc juruDekat(ServerLevel level, BlockPos at) {
		KultivatorNpc best = null;
		double bestSq = Double.MAX_VALUE;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at).inflate(8.0D, 4.0D, 8.0D),
				npc -> npc.isAlive() && npc.role() == NpcRole.JURU_LELANG)) {
			double d = npc.blockPosition().distSqr(at);
			if (d < bestSq) {
				bestSq = d;
				best = npc;
			}
		}
		return best;
	}

	/** True while a session of key {@code kunci} (a town or a site) runs. */
	public static boolean berjalan(long kunci) {
		return SESSIONS.containsKey(kunci);
	}

	/** The profile of the running session {@code kunci}, or null. */
	public static @Nullable ProfilLelang profil(long kunci) {
		Session session = SESSIONS.get(kunci);
		return session == null ? null : session.profile;
	}

	/** The auctioneer of the running session {@code kunci}, or null. */
	public static @Nullable KultivatorNpc juru(long kunci) {
		Session session = SESSIONS.get(kunci);
		return session == null ? null : session.auctioneer;
	}

	/**
	 * Fills the live part of a {@code lelang} sheet (38.U.3.3) for the running session {@code kunci}: status, the lot on
	 * the stage with its seller, base, current price and leader, the last eight bids, the bid steps and the commission.
	 * Without a session nothing is written (the builder shows the schedule).
	 */
	public static void lembarSesi(ServerPlayer player, long kunci, CompoundTag tag) {
		Session session = SESSIONS.get(kunci);
		if (session == null) {
			return;
		}
		long now = session.level.getGameTime();
		tag.putString(LembarKunci.Lelang.PROFIL, session.profile.kota() ? LembarKunci.Lelang.PROFIL_KOTA : LembarKunci.Lelang.PROFIL_AGUNG);
		tag.putFloat(LembarKunci.Lelang.KOMISI, (float) session.profile.komisiBerlaku());
		tag.putIntArray(LembarKunci.Lelang.LANGKAH, STEPS.clone());
		Lot lot = session.lot();
		if (lot == null) {
			tag.putString(LembarKunci.STATUS, LembarKunci.Lelang.S_SELESAI);
			return;
		}
		boolean open = lot.state == 1 && now >= lot.openAt;
		tag.putString(LembarKunci.STATUS, open ? LembarKunci.Lelang.S_BERJALAN : LembarKunci.Lelang.S_JEDA);
		long left = !open ? lot.openAt - now : lot.hasBid() ? lot.lastRaise + SELL_TICKS - now : lot.openAt + UNSOLD_TICKS - now;
		tag.putLong(LembarKunci.SISA_TICK, Math.max(0L, left));
		CompoundTag card = new CompoundTag();
		card.putInt(LembarKunci.NO, session.current + 1);
		card.putInt(LembarKunci.Lelang.TOTAL, session.lots.size());
		LembarKunci.barang(card, lot.stack, player.registryAccess());
		LembarKunci.teks(card, LembarKunci.Lelang.PENJUAL, lot.seller != null ? lot.sellerName
				: Component.translatable("kultivator.tempat_ekonomi.lelang.penjual_rumah"));
		card.putLong(LembarKunci.Lelang.DASAR, lot.base);
		card.putLong(LembarKunci.Lelang.KINI, lot.price);
		Component leader = lot.player != null ? nameOf(player, lot.player) : lot.bidder >= 0 && lot.bidder < session.bidders.size()
				? session.bidders.get(lot.bidder).name : null;
		if (leader != null) {
			LembarKunci.teks(card, LembarKunci.Lelang.PEMIMPIN, leader);
		}
		card.putBoolean(LembarKunci.ANDA, player.getUUID().equals(lot.player));
		card.putBoolean(LembarKunci.Lelang.MISTERI, lot.mystery);
		card.putBoolean(LembarKunci.Lelang.TERJUAL, lot.state == 2);
		card.putLong(LembarKunci.SISA_TICK, Math.max(0L, left));
		tag.put(LembarKunci.Lelang.LOT, card);
		ListTag bids = new ListTag();
		for (int i = lot.bids.size() - 1; i >= 0 && bids.size() < 8; i--) {
			Object[] bid = lot.bids.get(i);
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, (Component) bid[0]);
			row.putLong(LembarKunci.NB, (Long) bid[1]);
			row.putBoolean(LembarKunci.ANDA, player.getUUID().equals(bid[2]));
			row.putBoolean(LembarKunci.BARU, now - (Long) bid[3] < 30L);
			bids.add(row);
		}
		tag.put(LembarKunci.Lelang.TAWARAN, bids);
		tag.putLong(LembarKunci.Lelang.TAWAR_MIN, nextPrice(lot, STEPS[0]));
	}

	/**
	 * A bid of +{@code persen} % from the live screen on the running lot of session {@code kunci}; returns the refusal, or
	 * null when the bid stands (the stones are taken only when the lot is sold).
	 */
	public static @Nullable Component tawarLembar(ServerPlayer player, long kunci, int persen) {
		Session session = SESSIONS.get(kunci);
		Lot lot = session == null ? null : session.lot();
		if (lot == null) {
			return Component.translatable("kultivator.kota.lelang.tidak_ada");
		}
		int step = persen >= 25 ? 25 : persen >= 10 ? 10 : 5;
		long now = session.level.getGameTime();
		if (lot.state != 1 || now < lot.openAt) {
			return Component.translatable("kultivator.kota.lelang.usang");
		}
		if (player.getUUID().equals(lot.player)) {
			return Component.translatable("kultivator.tempat_ekonomi.lelang.sudah_memimpin");
		}
		if (lot.seller != null && lot.seller.equals(player.getUUID())) {
			return Component.translatable("kultivator.tempat_ekonomi.lelang.barang_sendiri");
		}
		long offer = nextPrice(lot, step);
		if (NilaiBatu.total(player) < offer) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(offer));
		}
		lot.price = offer;
		lot.player = player.getUUID();
		lot.bidder = -1;
		lot.lastRaise = now;
		lot.bids.add(new Object[] {player.getDisplayName(), offer, player.getUUID(), now});
		session.changed = now;
		announceRaise(session, lot, player.getDisplayName());
		LelangAgung.segarkan(session.level.getServer(), kunci);
		return null;
	}

	/** Town profile from the live screen: consigns one piece of the main hand ({@link Titipan}); false without a session. */
	public static boolean titipKota(ServerPlayer player, long kunci) {
		Session session = SESSIONS.get(kunci);
		if (session == null || !session.profile.kota()) {
			return false;
		}
		consign(player, session);
		return true;
	}

	/** Town profile: the player's consignments of the running session as {@code lelang.titip.daftar} rows. */
	public static ListTag titipanKota(ServerPlayer player, long kunci) {
		ListTag out = new ListTag();
		Session session = SESSIONS.get(kunci);
		if (session == null || !session.profile.kota()) {
			return out;
		}
		int i = 0;
		for (net.minecraft.nbt.Tag each : Titipan.view(KotaData.get(player.level().getServer()), player.getUUID(), session.town, session.day)) {
			if (each instanceof CompoundTag entry) {
				CompoundTag row = new CompoundTag();
				row.putString(LembarKunci.ID, "k" + i++);
				LembarKunci.teks(row, LembarKunci.NAMA, entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty()));
				row.putLong(LembarKunci.Lelang.DASAR, entry.getLongOr("value", 1L));
				row.putString(LembarKunci.STATUS, LembarKunci.Lelang.T_ANTRE);
				out.add(row);
			}
		}
		return out;
	}

	/** Town profile: consignments the player may still hand in this session. */
	public static int titipSisaKota(ServerPlayer player, long kunci) {
		Session session = SESSIONS.get(kunci);
		if (session == null || !session.profile.kota()) {
			return 0;
		}
		return Math.max(0, CONSIGN_MAX - Titipan.count(KotaData.get(player.level().getServer()), player.getUUID(), session.town, session.day));
	}

	/** Stones a town consignment owes the player (paid on the next town screen or by {@code ambil_hasil}). */
	public static long utangKota(ServerPlayer player) {
		return KotaData.get(player.level().getServer()).player(player.getUUID()).getLongOr("owed", 0L);
	}

	/** Pays every stone a town consignment owes the player (the {@code ambil_hasil} op of the live screen). */
	public static void bayarUtangKota(ServerPlayer player) {
		payOwed(player);
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
			if ((session.test || session.standalone) && session.auctioneer != null && !session.auctioneer.isRemoved()) {
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
		tag.put("consign", Titipan.view(KotaData.get(player.level().getServer()), player.getUUID(), session.town, session.day));
		return tag;
	}

	private static Component nameOf(ServerPlayer viewer, UUID id) {
		ServerPlayer player = viewer.level().getServer().getPlayerList().getPlayer(id);
		return player == null ? Component.translatable("kultivator.kota.lelang.seseorang") : player.getDisplayName();
	}

	private BalaiLelang() {
	}
}
