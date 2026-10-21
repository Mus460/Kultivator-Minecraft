package dev.kultivator.dunia.kota;

import dev.kultivator.cultivation.Enlightenment;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kota Abadi in Alam Abadi (DESIGN 34.5.4): the floating district's services for Immortal Ascension and True Immortal.
 * Pasar Abadi in Batu Asal Abadi (immortal herbs, Immortal Pills, essence pearls, rarely a primordial material or a land
 * expansion token), priced so the 16-a-day mining cap of {@code OriginStones} buys a rare item in about a week. Kuliah
 * Dao at noon: sitting (sneaking or meditating) within 6 blocks of the lecturer for 60 s gives +1..3 comprehension of the
 * player's Dao (an enlightenment moment without one), once a day. Persik Abadi every {@code kota.peachDays} days per
 * player: 2 % of the bar at Immortal Ascension, 5 % at True Immortal (lower realms: a little). Titah Utusan: a weekly
 * edict of the envoy pointing at a phase-9 challenge, paid in Batu Asal. The Balai Lelang Abadi's stage opens the
 * existing Lelang Abadi tab ({@link Kota#interact}); this class has no auction of its own.
 */
public final class KotaAbadi {

	public static final long NOON = 6000L;
	public static final long LECTURE_START = 5500L;
	public static final long LECTURE_END = 7500L;
	public static final int LECTURE_SECONDS = 60;
	public static final double LECTURE_RANGE = 6.0D;
	public static final int WEEK = 7;

	/** Titah kinds: offer origin stones, offer essence pearls, slay a primordial (a boss in Alam Abadi), slay enemies there. */
	static final String[] EDICTS = {"batu", "mutiara", "purba", "musuh"};
	static final int[] EDICT_NEED = {16, 2, 1, 12};
	static final int[] EDICT_PAY = {24, 30, 60, 20};

	record Ware(String id, int price, int weight) {
	}

	static final List<Ware> MARKET = List.of(
			new Ware("herb_1000", 8, 30),
			new Ware("immortal_pill", 14, 20),
			new Ware("immortal_essence_pearl", 20, 16),
			new Ware("soul_seal_jade", 18, 10),
			new Ware("kun_scale", 90, 4),
			new Ware("zhulong_scale", 95, 4),
			new Ware("taotie_fang", 90, 4),
			new Ware("peng_feather", 85, 4),
			new Ware("land_expansion_token", 110, 2));

	private static final Map<UUID, Integer> LISTENING = new HashMap<>();
	private static long lastBellDay = -1L;

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level) || !Kota.config().immortalCity || !dev.kultivator.abadi.Abadi.isImmortalLevel(level)) {
				return;
			}
			KillCredit.Credit credit = KillCredit.resolve(victim, source);
			if (credit == null) {
				return;
			}
			ServerPlayer player = credit.owner();
			CompoundTag edict = edict(player);
			if (edict.isEmpty() || edict.getBooleanOr("done", false)) {
				return;
			}
			int kind = edict.getIntOr("kind", 0);
			boolean counts = kind == 2 && victim.entityTags().contains(TechniquePower.TAG_BOSS) || kind == 3 && victim instanceof Enemy;
			if (counts) {
				int progress = edict.getIntOr("n", 0) + 1;
				edict.putInt("n", progress);
				if (progress >= EDICT_NEED[kind]) {
					edict.putBoolean("done", true);
					Msg.chat(player, Component.translatable("kultivator.kota.titah.selesai").withStyle(ChatFormatting.GOLD));
				}
				KotaData.get(level.getServer()).touch();
			}
		});
	}

	// ================================================================== tick: the lecture and the bell

	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L || !Kota.config().immortalCity || !Kota.config().lectures) {
			return;
		}
		long time = Kota.timeOfDay(server);
		if (time < LECTURE_START || time >= LECTURE_END) {
			if (!LISTENING.isEmpty()) {
				LISTENING.clear();
			}
			return;
		}
		ServerLevel level = server.getLevel(dev.kultivator.abadi.Abadi.IMMORTAL_LEVEL);
		if (level == null) {
			return;
		}
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, "kota/abadi_penceramah")) {
			KultivatorNpc lecturer = KotaAnchors.HANDLER.npcOf(anchor);
			if (lecturer == null) {
				continue;
			}
			if (tick % 400L == 0L) {
				Voice.say(lecturer, "elder.advice", "kultivator.kota.kuliah.suara");
			}
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(lecturer) > LECTURE_RANGE * LECTURE_RANGE || !(player.isShiftKeyDown() || Meditation.isMeditating(player))) {
					continue;
				}
				KotaData data = KotaData.get(server);
				long day = Kota.day(server);
				if (data.getLong(player, "kuliah_day", -1L) == day) {
					continue;
				}
				int seconds = LISTENING.merge(player.getUUID(), 1, Integer::sum);
				if (seconds % 15 == 0) {
					Msg.actionBar(player, Component.translatable("kultivator.kota.kuliah.dengar", seconds, LECTURE_SECONDS).withStyle(ChatFormatting.AQUA));
				}
				if (seconds >= LECTURE_SECONDS) {
					LISTENING.remove(player.getUUID());
					data.putLong(player, "kuliah_day", day);
					lectureReward(player);
				}
			}
		}
	}

	static void lectureReward(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao();
		if (dao == null) {
			Enlightenment.trigger(player);
			Msg.chat(player, Component.translatable("kultivator.kota.kuliah.pencerahan").withStyle(ChatFormatting.GOLD));
			return;
		}
		double amount = 1.0D + player.getRandom().nextInt(3);
		double gained = Comprehension.grant(player, dao, amount);
		Msg.chat(player, Component.translatable("kultivator.kota.kuliah.paham", dao.displayName(), String.format(java.util.Locale.ROOT, "%.1f", gained))
				.withStyle(ChatFormatting.GOLD));
	}

	/** The bell tower rings three times when the day crosses noon (vanilla bell sound, no block). */
	static void bell(ServerLevel level, DuniaAnchorEntity anchor, long now) {
		long time = Kota.timeOfDay(level.getServer());
		long day = Kota.day(level.getServer());
		if (time < NOON || time > NOON + 40L || lastBellDay == day) {
			return;
		}
		lastBellDay = day;
		for (int i = 0; i < 3; i++) {
			int delay = i * 30;
			dev.kultivator.voice.Voice.later(delay, () -> level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.BELL_BLOCK,
					SoundSource.BLOCKS, 2.0F, 0.7F));
		}
	}

	// ================================================================== the peach

	/** Eats a Persik Abadi: 2 % of the bar at Immortal Ascension, 5 % at True Immortal, 0.5 % below. */
	public static boolean eatPeach(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Realm realm = data.realm();
		double share = realm == Realm.TRUE_IMMORTAL ? 0.05D : realm == Realm.IMMORTAL_ASCENSION ? 0.02D : 0.005D;
		double xp = data.rank().xpRequired() * share;
		Progression.grantXp(player, xp, XpSource.EXACT);
		player.heal(player.getMaxHealth() * 0.5F);
		Msg.actionBar(player, Component.translatable("kultivator.kota.persik.makan", String.format(java.util.Locale.ROOT, "%.1f", share * 100.0D))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	// ================================================================== actions

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		switch (verb) {
			case "abeli" -> {
				if (npc.role() == NpcRole.PEDAGANG_ABADI) {
					buy(player, npc, Kota.arg(parts, 1, -1), Kota.arg(parts, 2, -1));
				}
			}
			case "persik" -> {
				if (npc.role() == NpcRole.PENJAGA_KEBUN) {
					pick(player, npc);
				}
			}
			case "titah" -> {
				if (npc.role() == NpcRole.UTUSAN) {
					edictAction(player, npc, parts.length > 1 ? parts[1] : "");
				}
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	record Offer(int index, Ware ware, ItemStack stack) {
	}

	static List<Offer> offers(ServerPlayer player, KultivatorNpc npc) {
		long week = Kota.day(player.level().getServer()) / 2L;
		RandomSource random = RandomSource.create(npc.anchorSite() * 37L + week);
		List<Ware> pool = new ArrayList<>(MARKET);
		List<Offer> offers = new ArrayList<>();
		int count = 5;
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			int total = 0;
			for (Ware ware : pool) {
				total += ware.weight();
			}
			int roll = random.nextInt(Math.max(1, total));
			Ware chosen = pool.getFirst();
			for (Ware ware : pool) {
				roll -= ware.weight();
				if (roll < 0) {
					chosen = ware;
					break;
				}
			}
			pool.remove(chosen);
			ItemStack stack = "herb_1000".equals(chosen.id()) ? SpiritHerbs.randomFor(null, RandomSource.create(week ^ i), 1000, 1000) : Harga.item(chosen.id());
			if (!stack.isEmpty()) {
				offers.add(new Offer(i, chosen, stack));
			}
		}
		return offers;
	}

	private static void buy(ServerPlayer player, KultivatorNpc npc, int index, int seen) {
		for (Offer offer : offers(player, npc)) {
			if (offer.index() != index) {
				continue;
			}
			int price = offer.ware().price();
			if (seen >= 0 && seen != price) {
				return;
			}
			if (!NilaiBatu.payOrigin(player, price)) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.abadi.kurang_asal", price).withStyle(ChatFormatting.RED));
				return;
			}
			ItemStack stack = offer.stack().copy();
			Component name = stack.getHoverName();
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				Kota.drop(player, stack);
			}
			Voice.say(npc, npc.voiceKey("thanks"), null);
			Msg.actionBar(player, Component.translatable("kultivator.kota.abadi.dibeli", name, price).withStyle(ChatFormatting.GOLD));
			return;
		}
	}

	private static void pick(ServerPlayer player, KultivatorNpc npc) {
		KotaData data = KotaData.get(player.level().getServer());
		long day = Kota.day(player.level().getServer());
		long last = data.getLong(player, "persik_day", -1000L);
		int every = Kota.config().peachDays;
		if (day - last < every) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.persik.belum", every - (day - last)).withStyle(ChatFormatting.YELLOW));
			return;
		}
		data.putLong(player, "persik_day", day);
		ItemStack peach = new ItemStack(KotaItems.PERSIK_ABADI);
		if (!player.getInventory().add(peach)) {
			Kota.drop(player, peach);
		}
		Voice.say(npc, npc.voiceKey("greet_alt"), null);
		Msg.chat(player, Component.translatable("kultivator.kota.persik.petik").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	static CompoundTag edict(ServerPlayer player) {
		return KotaData.get(player.level().getServer()).player(player.getUUID()).getCompoundOrEmpty("titah");
	}

	private static void edictAction(ServerPlayer player, KultivatorNpc npc, String what) {
		KotaData data = KotaData.get(player.level().getServer());
		long week = Kota.day(player.level().getServer()) / WEEK;
		CompoundTag edict = edict(player);
		switch (what) {
			case "ambil" -> {
				if (!edict.isEmpty() && edict.getLongOr("week", -1L) == week) {
					return;
				}
				CompoundTag next = new CompoundTag();
				next.putLong("week", week);
				next.putInt("kind", (int) Math.floorMod(week * 31L + player.getUUID().hashCode(), EDICTS.length));
				data.player(player.getUUID()).put("titah", next);
				data.touch();
				Voice.say(npc, "elder.welcome", null);
			}
			case "serah" -> {
				if (edict.isEmpty() || edict.getBooleanOr("paid", false)) {
					return;
				}
				int kind = edict.getIntOr("kind", 0);
				boolean done = edict.getBooleanOr("done", false);
				if (!done && kind == 0) {
					done = NilaiBatu.payOrigin(player, EDICT_NEED[0]);
				}
				if (!done && kind == 1) {
					done = takeItems(player, "immortal_essence_pearl", EDICT_NEED[1]);
				}
				if (!done) {
					Msg.actionBar(player, Component.translatable("kultivator.kota.titah.belum").withStyle(ChatFormatting.YELLOW));
					return;
				}
				edict.putBoolean("done", true);
				edict.putBoolean("paid", true);
				data.touch();
				NilaiBatu.giveOrigin(player, EDICT_PAY[kind]);
				Voice.say(npc, "elder.inheritance", null);
				Msg.chat(player, Component.translatable("kultivator.kota.titah.hadiah", EDICT_PAY[kind]).withStyle(ChatFormatting.GOLD));
			}
			default -> {
			}
		}
	}

	private static boolean takeItems(ServerPlayer player, String id, int count) {
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(dev.kultivator.Kultivator.id(id));
		int have = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				have += inventory.getItem(i).getCount();
			}
		}
		if (have < count) {
			return false;
		}
		int left = count;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
		inventory.setChanged();
		return true;
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		MinecraftServer server = player.level().getServer();
		tag.store("town", ComponentSerialization.CODEC, KotaNames.immortalCity(npc.town()));
		tag.putInt("origin", NilaiBatu.origin(player));
		tag.putInt("role", npc.role().ordinal());
		ListTag market = new ListTag();
		for (Offer offer : offers(player, npc)) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("i", offer.index());
			entry.putString("item", BuiltInRegistries.ITEM.getKey(offer.stack().getItem()).toString());
			entry.store("name", ComponentSerialization.CODEC, offer.stack().getHoverName());
			entry.putInt("price", offer.ware().price());
			entry.putBoolean("rare", offer.ware().weight() <= 4);
			market.add(entry);
		}
		tag.put("market", market);
		long time = Kota.timeOfDay(server);
		long day = Kota.day(server);
		KotaData data = KotaData.get(server);
		tag.putBoolean("lecture_now", time >= LECTURE_START && time < LECTURE_END);
		tag.putLong("lecture_in", Math.floorMod(LECTURE_START - time, 24000L));
		tag.putBoolean("lecture_done", data.getLong(player, "kuliah_day", -1L) == day);
		tag.putInt("listened", LISTENING.getOrDefault(player.getUUID(), 0));
		long last = data.getLong(player, "persik_day", -1000L);
		tag.putLong("peach_in", Math.max(0L, Kota.config().peachDays - (day - last)));
		tag.putInt("peach_days", Kota.config().peachDays);
		CompoundTag edict = edict(player);
		long week = day / WEEK;
		tag.putBoolean("edict_open", edict.isEmpty() || edict.getLongOr("week", -1L) != week);
		if (!edict.isEmpty()) {
			int kind = edict.getIntOr("kind", 0);
			tag.putInt("edict", kind);
			tag.putInt("edict_n", edict.getIntOr("n", 0));
			tag.putInt("edict_need", EDICT_NEED[kind]);
			tag.putInt("edict_pay", EDICT_PAY[kind]);
			tag.putBoolean("edict_done", edict.getBooleanOr("done", false));
			tag.putBoolean("edict_paid", edict.getBooleanOr("paid", false));
		}
		Realm realm = Cultivators.get(player).realm();
		tag.putBoolean("immortal", realm.ordinal() >= Realm.IMMORTAL_ASCENSION.ordinal());
		return tag;
	}

	private KotaAbadi() {
	}
}
