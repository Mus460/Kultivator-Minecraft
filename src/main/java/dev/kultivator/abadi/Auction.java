package dev.kultivator.abadi;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lelang Abadi, the Immortal auction (DESIGN 26.8; Sumber RMJIR ch. 615 "Another Beast Bone": a bidding war priced in
 * Immortal Origin Stones; rival names, lots, prices and timings are game design). One session per 2 in-game days, overworld
 * time 12,000 → 18,000; five lots seeded by (world seed, session index), shown one after another; all players see the same
 * lots. Four NPC rivals each hold a hidden limit of 0.6-1.6 × the base and every 4 s one under its limit may raise 5-15%.
 * Players click +5% / +10% / +25% (the action carries the lot and the price it saw; a stale click is refused; a bid needs that
 * much Batu in the inventory). A lot sells 8 s after the last raise. Batu are taken only on a win; a winner who no longer holds
 * enough loses the lot to the best rival and cannot bid for the rest of the session. Consignment: at most 2 items of
 * {@code #kultivator:auction_sellable} per player and session, paid at the session end at 0.6-1.0 × a value that is always at
 * most half the item's lot base (no buy-sell loop). State is memory only (a restart voids open lots with no charge); the bid
 * tick runs only while a session has open lots and a player is in Alam Abadi.
 */
final class Auction {

	public static final int OPEN_TIME = 12000;
	public static final int CLOSE_TIME = 18000;
	public static final int LOTS = 5;
	public static final int RIVALS = 4;
	public static final int BID_TICK = 80;
	public static final int SELL_AFTER = 160;
	public static final int PRESENT_TICKS = 200;
	public static final int UNSOLD_AFTER = 600;
	public static final double RIVAL_MIN = 0.6D;
	public static final double RIVAL_MAX = 1.6D;
	public static final double RAISE_MIN = 0.05D;
	public static final double RAISE_MAX = 0.15D;
	public static final double RIVAL_ACT = 0.7D;
	public static final double OFFENDED = 0.9D;
	/** Consignment rules shared with the town's Balai Lelang ({@code dunia.kota.Titipan}, audit T14/R28). */
	public static final int CONSIGN_MAX = dev.kultivator.dunia.kota.Titipan.PER_SESSION;
	public static final double CONSIGN_MIN = dev.kultivator.dunia.kota.Titipan.PAY_MIN;
	public static final double CONSIGN_SPREAD = dev.kultivator.dunia.kota.Titipan.PAY_SPREAD;
	public static final long VIEWER_TICKS = 2400L;
	public static final int[] STEPS = {5, 10, 25};
	public static final TagKey<Item> SELLABLE = TagKey.create(Registries.ITEM, Kultivator.id("auction_sellable"));
	public static final TagKey<Item> PRIMORDIAL = TagKey.create(Registries.ITEM, Kultivator.id("primordial_material"));

	private static final int NO_LEADER = -2;
	private static final int PLAYER = -1;

	private enum State { WAITING, OPEN, SOLD, UNSOLD }

	/** A row of the lot table: its key (lang {@code kultivator.abadi.lot.<key>}), base price, weight and maker. */
	private record Row(String key, int base, int weight, Function<RandomSource, ItemStack> make) {
	}

	private static final List<Row> ROWS = List.of(
			new Row("kunpeng_bone_fragment", 40, 3, random -> SectEventItems.fragment(1 + random.nextInt(6))),
			new Row("heavenly_flame_seed", 60, 2, Auction::flameSeed),
			new Row("herb", 25, 4, random -> SpiritHerbs.randomFor(null, random, 1000, 1500)),
			new Row("dewa_slip", 120, 1, Natives::dewaSlip),
			new Row("tomb_essence", 30, 4, random -> byId("tomb_essence", 5)),
			new Row("tomb_sigil", 35, 3, random -> byId("tomb_sigil", 1)),
			new Row("ancient_god_blood", 30, 3, random -> byId("ancient_god_blood", 1)),
			new Row("inheritance_crystal", 40, 3, random -> byId("inheritance_crystal", 1)),
			new Row("primordial_material", 50, 2, Auction::primordial),
			new Row("land_expansion_token", 80, 1, random -> byId("land_expansion_token", 1)),
			new Row("tribulation_warding_pill", 20, 4, random -> byId("tribulation_warding_pill", 2)),
			new Row("soul_nurturing_wood", 25, 3, random -> byId("soul_nurturing_wood", 1)),
			new Row("supreme_stones", 30, 3, random -> SpiritStones.stack(SpiritStones.SUPREME, 2)),
			new Row("embryo", 90, 1, random -> ArtifactItem.create(ArtifactItem.FORMS.get(random.nextInt(ArtifactItem.FORMS.size())), 4, 0, 0)));

	/** Consignment values per item (Batu), always at most half of the same item's lot base per item. */
	private static final Map<String, Integer> VALUES = Map.ofEntries(
			Map.entry("kunpeng_bone_fragment", 20), Map.entry("heavenly_flame_seed", 30), Map.entry("tomb_essence", 3),
			Map.entry("tomb_sigil", 15), Map.entry("ancient_god_blood", 15), Map.entry("inheritance_crystal", 20),
			Map.entry("kun_scale", 25), Map.entry("peng_feather", 25), Map.entry("zhulong_scale", 25), Map.entry("taotie_fang", 25),
			Map.entry("tribulation_warding_pill", 5), Map.entry("soul_nurturing_wood", 12), Map.entry("land_expansion_token", 40));

	private static final class Lot {
		final ItemStack item;
		final int base;
		final double[] limits = new double[RIVALS];
		State state = State.WAITING;
		int price;
		int leader = NO_LEADER;
		@Nullable UUID leaderPlayer;
		String leaderName = "";
		long openedAt;
		long lastRaise;
		Component winner = Component.empty();

		Lot(ItemStack item, int base) {
			this.item = item;
			this.base = base;
			this.price = base;
		}
	}

	private record Win(UUID player, int price, List<Integer> offended) {
	}

	private static final class Session {
		final long index;
		final boolean forced;
		final List<Lot> lots = new ArrayList<>();
		final Set<UUID> banned = new HashSet<>();
		final Set<UUID> bidders = new HashSet<>();
		final List<Win> wins = new ArrayList<>();
		int current;
		boolean done;

		Session(long index, boolean forced) {
			this.index = index;
			this.forced = forced;
		}

		@Nullable Lot lot() {
			return current >= 0 && current < lots.size() ? lots.get(current) : null;
		}
	}

	private static @Nullable Session session;
	private static final Map<UUID, Long> VIEWERS = new LinkedHashMap<>();

	static void init() {
		ServerPlayerEvents.JOIN.register(player -> Abadi.schedule(40, () -> payOwed(player)));
		ServerPlayerEvents.LEAVE.register(player -> VIEWERS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			session = null;
			VIEWERS.clear();
		});
	}

	// ================================================================== schedule and ticking

	/** True inside the session window: even days, 12,000-18,000. */
	private static boolean inWindow(MinecraftServer server) {
		long tod = Abadi.timeOfDay(server);
		return Abadi.day(server) % 2L == 0L && tod >= OPEN_TIME && tod < CLOSE_TIME;
	}

	/** Ticks until the next session opens (0 inside a window). */
	private static long untilNext(MinecraftServer server) {
		long day = Abadi.day(server);
		long tod = Abadi.timeOfDay(server);
		if (day % 2L == 0L) {
			if (tod < OPEN_TIME) {
				return OPEN_TIME - tod;
			}
			if (tod < CLOSE_TIME) {
				return 0L;
			}
			return 2L * 24000L - tod + OPEN_TIME;
		}
		return 24000L - tod + OPEN_TIME;
	}

	static void tick(MinecraftServer server, long ticks) {
		if (!KultivatorConfig.get().immortalAuction) {
			if (session != null) {
				close(server);
			}
			return;
		}
		if (ticks % 20L == 0L) {
			boolean window = inWindow(server);
			long index = Abadi.day(server) / 2L;
			if (session == null && window && Abadi.immortalLevel(server) != null && AlamAbadiData.get(server).settledSession() != index) {
				open(server, index, false);
			} else if (session != null && !session.forced && !window) {
				close(server);
			}
		}
		Session current = session;
		if (current == null || current.done) {
			return;
		}
		long now = Abadi.now(server);
		boolean viewers = hasViewers(server);
		Lot lot = current.lot();
		if (lot == null) {
			finishLots(server, current);
			return;
		}
		if (!viewers) {
			// Nobody is in Alam Abadi: the auction waits (its timers do not run).
			lot.lastRaise++;
			lot.openedAt++;
			return;
		}
		if (lot.state == State.WAITING) {
			lot.state = State.OPEN;
			lot.openedAt = now;
			lot.lastRaise = now;
			push(server);
			return;
		}
		if (ticks % BID_TICK == 0L && now - lot.openedAt >= PRESENT_TICKS) {
			rivalsAct(server, lot, now);
		}
		if (ticks % 20L == 0L) {
			if (lot.leader != NO_LEADER && now - lot.lastRaise >= SELL_AFTER) {
				sell(server, current, lot);
				nextLot(server, current);
			} else if (lot.leader == NO_LEADER && now - lot.openedAt >= UNSOLD_AFTER) {
				lot.state = State.UNSOLD;
				nextLot(server, current);
			}
		}
	}

	private static void nextLot(MinecraftServer server, Session current) {
		current.current++;
		if (current.lot() == null) {
			finishLots(server, current);
		}
		push(server);
	}

	private static void finishLots(MinecraftServer server, Session current) {
		if (current.done) {
			return;
		}
		current.done = true;
		if (current.forced) {
			Abadi.schedule(200, () -> {
				if (session == current) {
					close(server);
				}
			});
		}
	}

	/** True when a player is in Alam Abadi. */
	private static boolean hasViewers(MinecraftServer server) {
		var level = Abadi.immortalLevel(server);
		return level != null && !level.players().isEmpty();
	}

	// ================================================================== opening and closing

	private static void open(MinecraftServer server, long index, boolean forced) {
		Session created = new Session(index, forced);
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ (index * 0x9E3779B97F4A7C15L) ^ (forced ? 0x5DEECE66DL : 0L));
		int total = 0;
		for (Row row : ROWS) {
			total += row.weight();
		}
		Set<String> used = new HashSet<>();
		int attempts = 0;
		while (created.lots.size() < LOTS && attempts++ < 60) {
			int pick = random.nextInt(total);
			Row chosen = ROWS.get(0);
			for (Row row : ROWS) {
				pick -= row.weight();
				if (pick < 0) {
					chosen = row;
					break;
				}
			}
			if (!used.add(chosen.key())) {
				continue;
			}
			ItemStack item;
			try {
				item = chosen.make().apply(random);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: auction lot {} could not be made", chosen.key(), exception);
				item = ItemStack.EMPTY;
			}
			if (item == null || item.isEmpty()) {
				continue;
			}
			Lot lot = new Lot(item, chosen.base());
			for (int r = 0; r < RIVALS; r++) {
				lot.limits[r] = chosen.base() * (RIVAL_MIN + random.nextDouble() * (RIVAL_MAX - RIVAL_MIN));
			}
			created.lots.add(lot);
		}
		session = created;
		var level = Abadi.immortalLevel(server);
		if (level != null) {
			for (ServerPlayer player : level.players()) {
				Msg.chat(player, Component.translatable("kultivator.abadi.lelang.opened", created.lots.size()).withStyle(ChatFormatting.GOLD));
			}
		}
		push(server);
	}

	/** Ends the session: unsold lots stay unsold, consignments are paid, pursuits are rolled; the session index is settled. */
	private static void close(MinecraftServer server) {
		Session ended = session;
		if (ended == null) {
			return;
		}
		session = null;
		if (!ended.forced) {
			AlamAbadiData.get(server).setSettledSession(ended.index);
		}
		long now = Abadi.now(server);
		RandomSource random = server.overworld().getRandom();
		Set<UUID> pursued = new HashSet<>();
		for (Win win : ended.wins) {
			if (pursued.contains(win.player())) {
				continue;
			}
			for (int rival : win.offended()) {
				if (random.nextDouble() < KultivatorConfig.get().auctionPursuitChance) {
					ServerPlayer player = server.getPlayerList().getPlayer(win.player());
					if (player != null) {
						Pursuit.schedule(player, rival, now + 1200L + random.nextInt(2401));
						pursued.add(win.player());
					}
					break;
				}
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			payOwed(player);
		}
		var level = Abadi.immortalLevel(server);
		if (level != null) {
			for (ServerPlayer player : level.players()) {
				Msg.chat(player, Component.translatable("kultivator.abadi.lelang.closed").withStyle(ChatFormatting.GRAY));
			}
		}
		push(server);
	}

	// ================================================================== bidding

	private static void rivalsAct(MinecraftServer server, Lot lot, long now) {
		if (lot.state != State.OPEN) {
			return;
		}
		RandomSource random = server.overworld().getRandom();
		if (random.nextDouble() >= RIVAL_ACT) {
			return;
		}
		List<Integer> willing = new ArrayList<>();
		int[] offers = new int[RIVALS];
		for (int r = 0; r < RIVALS; r++) {
			if (r == lot.leader) {
				continue;
			}
			int offer = lot.leader == NO_LEADER ? lot.base : (int) Math.ceil(lot.price * (1.0D + RAISE_MIN + random.nextDouble() * (RAISE_MAX - RAISE_MIN)));
			if (offer <= lot.limits[r]) {
				willing.add(r);
				offers[r] = offer;
			}
		}
		if (willing.isEmpty()) {
			return;
		}
		int rival = willing.get(random.nextInt(willing.size()));
		lot.price = offers[rival];
		lot.leader = rival;
		lot.leaderPlayer = null;
		lot.leaderName = "";
		lot.lastRaise = now;
		push(server);
	}

	/** A player's bid: {@code step} 5, 10 or 25 (%), {@code expected} the price the player saw. */
	static void bid(ServerPlayer player, int lotIndex, int expected, int step) {
		Session current = session;
		Lot lot = current == null ? null : current.lot();
		if (current == null || lot == null || lot.state != State.OPEN || lotIndex != current.current) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.closed").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!Abadi.isImmortalLevel(player.level()) || !Abadi.ascended(player)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.place").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (current.banned.contains(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.banned").withStyle(ChatFormatting.RED));
			return;
		}
		if (expected != lot.price) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.stale").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (lot.leader == PLAYER && player.getUUID().equals(lot.leaderPlayer)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.leading").withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean known = false;
		for (int each : STEPS) {
			known |= each == step;
		}
		if (!known) {
			return;
		}
		int offer = lot.leader == NO_LEADER ? lot.base : (int) Math.ceil(lot.price * (1.0D + step / 100.0D));
		if (!player.isCreative() && Abadi.stonesIn(player) < offer) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.stones", offer, Abadi.stonesIn(player)).withStyle(ChatFormatting.GRAY));
			return;
		}
		lot.price = offer;
		lot.leader = PLAYER;
		lot.leaderPlayer = player.getUUID();
		lot.leaderName = player.getName().getString();
		lot.lastRaise = Abadi.now(player.level().getServer());
		current.bidders.add(player.getUUID());
		// Lifetime counter read by the BID daily chore (goals/DailyChores).
		CompoundTag record = Abadi.section(player, "auction");
		record.putInt("bids", record.getIntOr("bids", 0) + 1);
		Cultivators.dirty(player);
		Abadi.activity(player, Abadi.ACT_AUCTION_BID);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.8F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.abadi.lelang.bid", offer).withStyle(ChatFormatting.GOLD));
		push(player.level().getServer());
	}

	private static void sell(MinecraftServer server, Session current, Lot lot) {
		lot.state = State.SOLD;
		if (lot.leader == PLAYER && lot.leaderPlayer != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(lot.leaderPlayer);
			if (player != null && Abadi.takeStones(player, lot.price)) {
				lot.winner = Component.literal(lot.leaderName);
				Abadi.give(player, lot.item.copy());
				Advancements.grant(player, "auction_win");
				Abadi.activity(player, Abadi.ACT_AUCTION_WIN);
				EpicHooks.extra(player.level(), EpicIds.EXTRA_AUCTION_WIN, player, player, player.position(), current.current, lot.price);
				Msg.chat(player, Component.translatable("kultivator.abadi.lelang.won", lot.item.getHoverName(), lot.price).withStyle(ChatFormatting.GOLD));
				List<Integer> offended = new ArrayList<>();
				for (int r = 0; r < RIVALS; r++) {
					if (lot.limits[r] >= OFFENDED * lot.price) {
						offended.add(r);
					}
				}
				current.wins.add(new Win(player.getUUID(), lot.price, offended));
				return;
			}
			// The winner no longer holds enough Batu: the best rival takes it, and that player bids no more this session.
			current.banned.add(lot.leaderPlayer);
			if (player != null) {
				Msg.chat(player, Component.translatable("kultivator.abadi.lelang.lost_unpaid").withStyle(ChatFormatting.RED));
			}
			int best = 0;
			for (int r = 1; r < RIVALS; r++) {
				if (lot.limits[r] > lot.limits[best]) {
					best = r;
				}
			}
			lot.leader = best;
			lot.price = (int) Math.min(lot.price, Math.floor(lot.limits[best]));
		}
		if (lot.leader >= 0) {
			lot.winner = Component.translatable("kultivator.abadi.rival." + lot.leader);
		}
	}

	// ================================================================== consignment

	/** Consigns one item of the main-hand stack (at most 2 per session); paid at the session end. */
	static void consign(ServerPlayer player) {
		Session current = session;
		if (current == null || !Abadi.isImmortalLevel(player.level()) || !Abadi.ascended(player)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.refuse.place").withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack hand = player.getMainHandItem();
		String id = hand.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(hand.getItem()).getPath();
		Integer value = VALUES.get(id);
		if (hand.isEmpty() || !hand.is(SELLABLE) || value == null || !BuiltInRegistries.ITEM.getKey(hand.getItem()).getNamespace().equals(Kultivator.MOD_ID)) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.consign.refuse").withStyle(ChatFormatting.GRAY));
			return;
		}
		ListTag list = consignList(player);
		int mine = 0;
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry && entry.getLongOr("session", -1L) == current.index) {
				mine++;
			}
		}
		if (mine >= CONSIGN_MAX) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.consign.full", CONSIGN_MAX).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component name = hand.getHoverName().copy();
		hand.shrink(1);
		CompoundTag entry = new CompoundTag();
		entry.putLong("session", current.index);
		entry.putInt("value", value);
		entry.store("name", ComponentSerialization.CODEC, name);
		list.add(entry);
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.abadi.lelang.consign.done", name, value).withStyle(ChatFormatting.AQUA));
	}

	private static ListTag consignList(ServerPlayer player) {
		CompoundTag auction = Abadi.section(player, "auction");
		ListTag list = auction.getList("consign").orElse(null);
		if (list == null) {
			list = new ListTag();
			auction.put("consign", list);
		}
		return list;
	}

	/** Pays every consignment of a session that is over (at the end, or at the next login). */
	private static void payOwed(ServerPlayer player) {
		if (player.isRemoved()) {
			return;
		}
		CompoundTag auction = Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY).getCompoundOrEmpty("auction");
		ListTag list = auction.getList("consign").orElse(null);
		if (list == null || list.isEmpty()) {
			return;
		}
		Session current = session;
		RandomSource random = player.getRandom();
		int total = 0;
		int sold = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!(list.get(i) instanceof CompoundTag entry)) {
				list.remove(i);
				continue;
			}
			if (current != null && entry.getLongOr("session", -1L) == current.index) {
				continue;
			}
			total += (int) dev.kultivator.dunia.kota.Titipan.paid(entry.getIntOr("value", 1), random);
			sold++;
			list.remove(i);
		}
		if (sold > 0) {
			Abadi.giveStones(player, total);
			Cultivators.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.consign.paid", sold, total).withStyle(ChatFormatting.GOLD));
		}
	}

	// ================================================================== lot makers

	private static ItemStack byId(String id, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id(id));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
	}

	@SuppressWarnings("unchecked")
	private static ItemStack flameSeed(RandomSource random) {
		ItemStack stack = byId("heavenly_flame_seed", 1);
		if (stack.isEmpty()) {
			return stack;
		}
		int kind = random.nextInt(PowerIds.FLAME_IDS.length);
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Kultivator.id("flame_kind"));
		if (type != null) {
			stack.set((DataComponentType<Integer>) type, kind);
			stack.set(DataComponents.ITEM_NAME, Component.translatable("item.kultivator.heavenly_flame_seed." + PowerIds.FLAME_IDS[kind]));
			stack.set(DataComponents.ITEM_MODEL, Kultivator.id("heavenly_flame_seed_" + PowerIds.FLAME_IDS[kind]));
		}
		return stack;
	}

	private static ItemStack primordial(RandomSource random) {
		List<Item> items = new ArrayList<>();
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(PRIMORDIAL)) {
			items.add(holder.value());
		}
		return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())));
	}

	// ================================================================== the Lelang tablet and syncs

	static void noteViewer(ServerPlayer player) {
		VIEWERS.put(player.getUUID(), Abadi.now(player.level().getServer()));
	}

	/** Sends the tablet data to every player who opened the Keabadian screen within the last 2 minutes. */
	private static void push(MinecraftServer server) {
		long now = Abadi.now(server);
		VIEWERS.values().removeIf(at -> now - at > VIEWER_TICKS);
		for (UUID id : List.copyOf(VIEWERS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ABADI, Abadi.syncTag(player));
			}
		}
	}

	static boolean inAuction(ServerPlayer player) {
		Session current = session;
		if (current == null || current.done) {
			return false;
		}
		Lot lot = current.lot();
		return current.bidders.contains(player.getUUID()) || (lot != null && player.getUUID().equals(lot.leaderPlayer));
	}

	/** The Lelang tablet's data. */
	static CompoundTag pageTag(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag page = new CompoundTag();
		Session current = session;
		long now = Abadi.now(server);
		page.putBoolean("enabled", KultivatorConfig.get().immortalAuction);
		page.putBoolean("in_realm", Abadi.isImmortalLevel(player.level()));
		page.putBoolean("active", current != null);
		page.putLong("next", untilNext(server));
		page.putLong("ends", inWindow(server) ? CLOSE_TIME - Abadi.timeOfDay(server) : 0L);
		page.putInt("stones", Abadi.stonesIn(player));
		if (current != null) {
			page.putInt("current", current.current);
			page.putBoolean("banned", current.banned.contains(player.getUUID()));
			page.putBoolean("done", current.done);
			ListTag lots = new ListTag();
			for (Lot lot : current.lots) {
				CompoundTag entry = new CompoundTag();
				Component name = lot.item.getCount() > 1 ? Component.translatable("kultivator.abadi.lelang.count", lot.item.getHoverName(), lot.item.getCount())
						: lot.item.getHoverName();
				entry.store("name", ComponentSerialization.CODEC, name.copy());
				entry.putInt("base", lot.base);
				entry.putInt("price", lot.price);
				entry.putInt("state", lot.state.ordinal());
				Component leader = lot.leader == PLAYER ? Component.literal(lot.leaderName)
						: lot.leader >= 0 ? Component.translatable("kultivator.abadi.rival." + lot.leader) : Component.translatable("kultivator.abadi.lelang.no_bid");
				entry.store("leader", ComponentSerialization.CODEC, lot.state == State.SOLD ? lot.winner : leader);
				entry.putBoolean("mine", lot.leader == PLAYER && player.getUUID().equals(lot.leaderPlayer));
				entry.putLong("sell", lot.leader != NO_LEADER ? Math.max(0L, SELL_AFTER - (now - lot.lastRaise)) : -1L);
				entry.putLong("present", lot.state == State.OPEN ? Math.max(0L, PRESENT_TICKS - (now - lot.openedAt)) : 0L);
				lots.add(entry);
			}
			page.put("lots", lots);
		}
		ListTag consign = new ListTag();
		for (Tag tag : Cultivators.get(player).extra().getCompoundOrEmpty(Abadi.EXTRA_KEY).getCompoundOrEmpty("auction").getListOrEmpty("consign")) {
			if (tag instanceof CompoundTag entry) {
				consign.add(entry.copy());
			}
		}
		page.put("consign", consign);
		page.putInt("consign_max", CONSIGN_MAX);
		return page;
	}

	static int info(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Session current = session;
		if (current == null) {
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.info.next", Msg.number(Math.ceil(untilNext(server) / 1200.0D)))
					.withStyle(ChatFormatting.GOLD));
			return 1;
		}
		Msg.chat(player, Component.translatable("kultivator.abadi.lelang.info.header", current.lots.size()).withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < current.lots.size(); i++) {
			Lot lot = current.lots.get(i);
			Msg.chat(player, Component.translatable("kultivator.abadi.lelang.info.line", i + 1, lot.item.getHoverName(), lot.base, lot.price,
					Component.translatable("kultivator.abadi.lelang.state." + lot.state.name().toLowerCase(java.util.Locale.ROOT))).withStyle(
					i == current.current ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
		return 1;
	}

	static String debugLine() {
		Session current = session;
		return current == null ? "auction=none" : "auction=#" + current.index + " lot " + (current.current + 1) + "/" + current.lots.size()
				+ (current.forced ? " (test)" : "") + ", viewers " + VIEWERS.size();
	}

	// ================================================================== tests

	/** {@code uji lelang sesi}: a session now (outside the schedule). */
	static boolean testOpen(MinecraftServer server) {
		if (session != null) {
			return false;
		}
		open(server, Abadi.day(server) / 2L, true);
		return true;
	}

	/** {@code uji lelang tutup}: ends the session now. */
	static boolean testClose(MinecraftServer server) {
		if (session == null) {
			return false;
		}
		close(server);
		return true;
	}

	/** {@code uji lelang ...}: a value table check for the docs (value ≤ half the lot base). */
	static Map<String, Integer> values() {
		return new HashMap<>(VALUES);
	}

	private Auction() {
	}
}
