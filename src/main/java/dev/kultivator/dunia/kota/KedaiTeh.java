package dev.kultivator.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiItems;
import dev.kultivator.dunia.kota42.ekonomi.PasarHantu;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Kedai Teh, Paviliun Angin Bambu (DESIGN 34.5.1): three teas (one in effect at a time, {@link #MINUTES} minutes),
 * Kabar Angin (rumours bought by distance band: 1 / 9 / 80 NB for 1 500 / 3 000 / 6 000 blocks, through
 * {@link DuniaSites#locateFor}; or, while a treasure birth is live within 6 000 blocks, its direction), and the Token
 * Pasar Gelap after three rumours. Tea effects use the public hooks only: Teh Embun Pagi through the XP multiplier
 * provider (while meditating), Teh Awan Ungu as a small comprehension stream while meditating (there is no
 * comprehension-gain hook), Teh Penenang Hati by giving back a fifth of every Heart Demon pressure rise (the public
 * pressure value).
 * <p>
 * DESIGN 42.6.7 (42C-2): the token now also tells tonight's <i>sandi malam</i>, the riddle of where the Ghost Market opens
 * ({@code PasarHantu.sandi}); a token holder asks for it again with verb {@code sandi}; verb {@code topeng} sells the
 * Ghost Market mask (20 NB) to players who were offered the token. Every price carries one {@code HargaKota.faktor}
 * (teas by their item's category, rumours, token and mask as services).
 */
public final class KedaiTeh {

	public static final int EMBUN = 0;
	public static final int AWAN = 1;
	public static final int PENENANG = 2;
	public static final String[] IDS = {"embun", "awan", "penenang"};
	public static final long[] PRICE = {2L, 9L, 20L};
	public static final Realm[] GATE = {Realm.MORTAL, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL};
	/** A tea lasts this many minutes (12 000 ticks). */
	public static final int MINUTES = 10;
	public static final long TEA_TICKS = MINUTES * 1200L;
	/** Kabar Angin bands: price and radius. */
	public static final long[] RUMOUR_PRICE = {1L, 9L, 81L};
	public static final int[] RUMOUR_RADIUS = {1500, 3000, 6000};
	/**
	 * Rumour kinds: the structure tag folders of the modules (each module ships {@code #kultivator:<folder>}). DESIGN 38
	 * (38.D.12, 38W) appends the capital ({@code kota_besar}), the villages ({@code kampung}, 38DS) and the caves of 38
	 * ({@code gua}, 38GU) at the end, so the indexes of the older kinds stay.
	 */
	public static final String[] RUMOUR_KINDS = {"kota", "reruntuhan", "penjara", "menara", "bawah", "laut", "kota_besar", "kampung", "gua"};
	public static final int TOKEN_AFTER = 3;
	/** KotaData player key: the in-game day of the last free rumour of a Lencana Pengembara. */
	public static final String BADGE_DAY = "lencana_hari";
	public static final long TOKEN_PRICE = 60L;
	/** The Ghost Market mask (42.6.7), sold to players offered the token. */
	public static final long TOPENG_PRICE = 20L;
	/** Teh Awan Ungu: comprehension per minute of meditation. */
	public static final double AWAN_PER_MINUTE = 0.03D;

	private static final Map<UUID, Double> PRESSURE = new HashMap<>();

	static void init() {
	}

	// ================================================================== teas

	/** The tea in effect for the player (-1 for none). */
	public static int active(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = KotaData.get(server).player(player.getUUID());
		int kind = tag.getIntOr("tea", -1);
		if (kind < 0 || server.overworld().getGameTime() >= tag.getLongOr("tea_until", 0L)) {
			return -1;
		}
		return kind;
	}

	/** Starts {@code kind} for the player (replacing any tea in effect). */
	public static boolean drink(ServerPlayer player, int kind, boolean fromItem) {
		if (kind < 0 || kind > 2) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		KotaData data = KotaData.get(server);
		CompoundTag tag = data.player(player.getUUID());
		tag.putInt("tea", kind);
		tag.putLong("tea_until", server.overworld().getGameTime() + TEA_TICKS);
		data.touch();
		PRESSURE.put(player.getUUID(), Cultivators.get(player).heartDemonPressure());
		Msg.actionBar(player, Component.translatable("kultivator.kota.teh.minum", Component.translatable("item.kultivator.teh_" + teaItem(kind)), MINUTES)
				.withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.6F);
		return true;
	}

	static String teaItem(int kind) {
		return switch (kind) {
			case AWAN -> "awan_ungu";
			case PENENANG -> "penenang_hati";
			default -> "embun_pagi";
		};
	}

	/** Every 20 ticks: Teh Penenang Hati gives back a fifth of a pressure rise; every minute Teh Awan Ungu's stream. */
	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int tea = active(player);
			if (tea < 0) {
				PRESSURE.remove(player.getUUID());
				continue;
			}
			CultivationData data = Cultivators.get(player);
			if (tea == PENENANG) {
				double now = data.heartDemonPressure();
				Double before = PRESSURE.put(player.getUUID(), now);
				if (before != null && now > before) {
					double lowered = now - (now - before) * 0.2D;
					data.setHeartDemonPressure(lowered);
					PRESSURE.put(player.getUUID(), lowered);
					Cultivators.dirty(player);
				}
			}
			if (tea == AWAN && tick % 1200L == 0L && Meditation.isMeditating(player)) {
				Dao dao = data.dao();
				if (dao != null) {
					Comprehension.grant(player, dao, AWAN_PER_MINUTE * (Kota.config().teaBonus / 0.10D));
				}
			}
		}
	}

	// ================================================================== actions

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.PEMILIK_TEH) {
			return false;
		}
		switch (verb) {
			case "teh", "beliteh" -> buyTea(player, npc, Kota.arg(parts, 1, -1), "beliteh".equals(verb));
			case "kabar" -> rumour(player, npc, Kota.arg(parts, 1, 0), Kota.arg(parts, 2, 0), true);
			case "token" -> buyToken(player, npc);
			case "sandi" -> tellPassword(player, npc, false);
			case "topeng" -> buyMask(player, npc);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void buyTea(ServerPlayer player, KultivatorNpc npc, int kind, boolean asItem) {
		if (kind < 0 || kind > 2) {
			return;
		}
		if (Cultivators.get(player).realm().ordinal() < GATE[kind].ordinal()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.teh.ranah", GATE[kind].displayName()).withStyle(ChatFormatting.RED));
			return;
		}
		long price = teaPrice(player, npc, kind);
		if (!NilaiBatu.pay(player, price)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
			return;
		}
		KotaVoice.say(npc, KotaVoice.TEA_WELCOME, npc.voiceKey("greet_alt"), "kultivator.kota.teh.suara.sambut");
		if (asItem) {
			ItemStack stack = Harga.item("teh_" + teaItem(kind));
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				Kota.drop(player, stack);
			}
			return;
		}
		drink(player, kind, false);
	}

	private static void buyToken(ServerPlayer player, KultivatorNpc npc) {
		KotaData data = KotaData.get(player.level().getServer());
		if (!Kota.config().blackMarket) {
			return;
		}
		if (data.getInt(player, "rumours", 0) < TOKEN_AFTER) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.teh.token_belum", TOKEN_AFTER).withStyle(ChatFormatting.RED));
			return;
		}
		long price = servicePrice(player.level().getServer(), npc.town(), TOKEN_PRICE);
		if (!NilaiBatu.pay(player, price)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack token = new ItemStack(KotaItems.TOKEN_PASAR_GELAP);
		if (!player.getInventory().add(token) && !token.isEmpty()) {
			Kota.drop(player, token);
		}
		KotaVoice.say(npc, KotaVoice.BLACK_WHISPER, npc.voiceKey("careful"), null);
		Msg.chat(player, Component.translatable("kultivator.kota.teh.token_bisik").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		tellPassword(player, npc, true);
	}

	/** The sandi malam of tonight's Ghost Market in this town, for a token holder (free; 42.6.7). */
	private static void tellPassword(ServerPlayer player, KultivatorNpc npc, boolean justBought) {
		if (!justBought && !player.getInventory().contains(stack -> stack.is(KotaItems.TOKEN_PASAR_GELAP))) {
			Msg.actionBar(player, Component.translatable("kultivator.kota42.pasar_hantu.sandi_token").withStyle(ChatFormatting.RED));
			return;
		}
		Component riddle = PasarHantu.sandiUntuk(player, npc.town());
		if (riddle == null) {
			if (!justBought) {
				Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.sandi_tiada").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			return;
		}
		KotaVoice.say(npc, KotaVoice.BLACK_WHISPER, npc.voiceKey("careful"), null);
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.sandi", riddle).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
	}

	/** The Ghost Market mask, for players who were offered the token (three rumours heard). */
	private static void buyMask(ServerPlayer player, KultivatorNpc npc) {
		KotaData data = KotaData.get(player.level().getServer());
		if (data.getInt(player, "rumours", 0) < TOKEN_AFTER) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.teh.token_belum", TOKEN_AFTER).withStyle(ChatFormatting.RED));
			return;
		}
		long price = servicePrice(player.level().getServer(), npc.town(), TOPENG_PRICE);
		if (!NilaiBatu.pay(player, price)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack mask = new ItemStack(KotaEkonomiItems.TOPENG_PASAR_HANTU);
		if (!player.getInventory().add(mask) && !mask.isEmpty()) {
			Kota.drop(player, mask);
		}
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.topeng_beli").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
	}

	/** A tea's price for this player here: base x town price scale x discount x {@code HargaKota.faktor} of the tea item. */
	static long teaPrice(ServerPlayer player, KultivatorNpc npc, int kind) {
		double town = HargaKota.faktor(player.level().getServer(), npc.town(), Kategori.dari(Harga.item("teh_" + teaItem(kind))));
		return Math.max(1L, Math.round(PRICE[kind] * Kota.config().priceScale * Toko.discount(player, npc) * town));
	}

	/** A service price (rumour, token, mask): base x town price scale x {@code HargaKota.faktor(JASA)}. */
	static long servicePrice(MinecraftServer server, long town, long base) {
		return Math.max(1L, Math.round(base * Kota.config().priceScale * HargaKota.faktor(server, town, Kategori.JASA)));
	}

	/**
	 * Kabar Angin of {@code kind} ({@link #RUMOUR_KINDS}) in distance band {@code band}: a live treasure birth first (its
	 * direction), else the nearest structure of that kind. Paid only when something new was told.
	 */
	static boolean rumour(ServerPlayer player, KultivatorNpc npc, int kind, int band, boolean paid) {
		// Lencana Pengembara: one rumour a day on the house (integration audit S22, new reward approved by the user)
		boolean badge = paid && badgeFree(player);
		if (badge) {
			paid = false;
		}
		int b = Math.max(0, Math.min(2, band));
		int k = Math.max(0, Math.min(RUMOUR_KINDS.length - 1, kind));
		long price = paid ? servicePrice(player.level().getServer(), npc != null ? npc.town() : 0L, RUMOUR_PRICE[b]) : 0L;
		if (paid && NilaiBatu.total(player) < price) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		// a treasure about to be fought over is the talk of every teahouse
		for (TreasureBirth birth : DuniaApi.treasures().live(level)) {
			double distance = Math.sqrt(birth.pos().distSqr(player.blockPosition()));
			if (distance <= RUMOUR_RADIUS[2] && !player.level().getServer().getPlayerList().getPlayers().isEmpty()
					&& KotaData.get(level.getServer()).getLong(player, "told_birth", 0L) != birth.id()) {
				KotaData.get(level.getServer()).putLong(player, "told_birth", birth.id());
				if (paid && !NilaiBatu.pay(player, price)) {
					return false;
				}
				Msg.chat(player, Component.translatable("kultivator.kota.kabar.harta", direction(player.blockPosition(), birth.pos()), (int) distance)
						.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
				counted(player);
				badgeUsed(player, badge);
				return true;
			}
		}
		String folder = RUMOUR_KINDS[k];
		TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Kultivator.id(folder));
		int chunks = Math.max(1, RUMOUR_RADIUS[b] / 16);
		long cooldown = DuniaSites.locateCooldown(player);
		if (cooldown > 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kabar.tunggu", (cooldown + 19L) / 20L).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		DuniaSites.Site site = DuniaSites.locateFor(player, tag, Math.min(DuniaSites.MAX_LOCATE_CHUNKS, chunks), siteKind(folder), 1);
		if (site == null) {
			Msg.chat(player, Component.translatable("kultivator.kota.kabar.kosong", Component.translatable("kultivator.kota.kabar.jenis." + folder))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		if (DuniaSites.knows(player, site.id())) {
			Msg.chat(player, Component.translatable("kultivator.kota.kabar.sudah", Component.translatable("kultivator.kota.kabar.jenis." + folder))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		if (paid && !NilaiBatu.pay(player, price)) {
			return false;
		}
		DuniaSites.discover(player, site.id());
		int distance = (int) Math.sqrt(site.pos().distSqr(player.blockPosition()));
		Msg.chat(player, Component.translatable("kultivator.kota.kabar.situs", Component.translatable("kultivator.kota.kabar.jenis." + folder),
				direction(player.blockPosition(), site.pos()), distance, site.pos().getX(), site.pos().getZ())
				.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		if (npc != null) {
			KotaVoice.say(npc, KotaVoice.TEA_NEWS, npc.voiceKey("careful"), null);
		}
		counted(player);
		badgeUsed(player, badge);
		return true;
	}

	/** True when the player carries a Lencana Pengembara and has not had today's free rumour yet. */
	static boolean badgeFree(ServerPlayer player) {
		if (!player.getInventory().contains(stack -> stack.is(KotaItems.LENCANA_PENGEMBARA))) {
			return false;
		}
		return KotaData.get(player.level().getServer()).getLong(player, BADGE_DAY, Long.MIN_VALUE) != today(player);
	}

	private static void badgeUsed(ServerPlayer player, boolean badge) {
		if (!badge) {
			return;
		}
		KotaData.get(player.level().getServer()).putLong(player, BADGE_DAY, today(player));
		Msg.chat(player, Component.translatable("kultivator.sambung.lencana.gratis").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private static long today(ServerPlayer player) {
		return Math.floorDiv(player.level().getServer().overworld().getOverworldClockTime(), 24000L);
	}

	/** The site kind a rumour of {@code folder} publishes (its marker). */
	private static String siteKind(String folder) {
		return switch (folder) {
			case "kota" -> "kota/kota";
			case "penjara" -> "penjara/penjara";
			case "menara" -> "menara/menara";
			case "bawah" -> "bawah/kuil";
			case "laut" -> "laut/pusaran";
			case "kota_besar" -> "kota/kota_besar";
			case "kampung" -> "kampung/desa";
			case "gua" -> "gua/pertapa";
			default -> "reruntuhan/gua_kecil";
		};
	}

	private static void counted(ServerPlayer player) {
		KotaData data = KotaData.get(player.level().getServer());
		int count = data.getInt(player, "rumours", 0) + 1;
		data.putInt(player, "rumours", count);
		Advancements.grant(player, "dunia/kota_rumour");
		if (count == TOKEN_AFTER && Kota.config().blackMarket) {
			Msg.chat(player, Component.translatable("kultivator.kota.teh.token_tawar").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		}
	}

	/** "barat laut" (the eight winds, lang {@code kultivator.kota.arah.<0-7>}). */
	public static Component direction(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		// 0 = north, clockwise
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int index = Math.floorMod((int) Math.round(angle / 45.0D), 8);
		return Component.translatable("kultivator.kota.arah." + index);
	}

	/** Catatan Pengembara: a free rumour of a random kind within 3 000 blocks, then a lore line. */
	static boolean readNote(ServerPlayer player) {
		int kind = player.getRandom().nextInt(RUMOUR_KINDS.length);
		Msg.chat(player, Component.translatable("kultivator.kota.catatan.lore." + player.getRandom().nextInt(8)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		if (DuniaSites.locateCooldown(player) > 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kabar.tunggu", (DuniaSites.locateCooldown(player) + 19L) / 20L)
					.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		rumour(player, null, kind, 1, false);
		return true;
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("active", active(player));
		CompoundTag data = KotaData.get(player.level().getServer()).player(player.getUUID());
		long left = data.getLongOr("tea_until", 0L) - player.level().getServer().overworld().getGameTime();
		tag.putLong("left", Math.max(0L, left));
		ListTag teas = new ListTag();
		int realm = Cultivators.get(player).realm().ordinal();
		for (int i = 0; i < 3; i++) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("price", teaPrice(player, npc, i));
			entry.putBoolean("open", realm >= GATE[i].ordinal());
			entry.putInt("gate", GATE[i].ordinal());
			teas.add(entry);
		}
		tag.put("teas", teas);
		ListTag rumours = new ListTag();
		for (int b = 0; b < 3; b++) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("price", servicePrice(player.level().getServer(), npc.town(), RUMOUR_PRICE[b]));
			entry.putInt("radius", RUMOUR_RADIUS[b]);
			rumours.add(entry);
		}
		tag.put("rumours", rumours);
		tag.putInt("heard", data.getIntOr("rumours", 0));
		tag.putBoolean("token", Kota.config().blackMarket && data.getIntOr("rumours", 0) >= TOKEN_AFTER);
		tag.putLong("token_price", servicePrice(player.level().getServer(), npc.town(), TOKEN_PRICE));
		tag.putLong("topeng_price", servicePrice(player.level().getServer(), npc.town(), TOPENG_PRICE));
		tag.putBoolean("sandi", player.getInventory().contains(stack -> stack.is(KotaItems.TOKEN_PASAR_GELAP)));
		tag.putLong("cooldown", DuniaSites.locateCooldown(player));
		// today's rumour is free (Lencana Pengembara); the screen may show it, the server decides on purchase
		tag.putBoolean("lencana", badgeFree(player));
		tag.putInt("wanderers", Pengembara.seatedNear(npc));
		tag.putDouble("bonus", Kota.config().teaBonus);
		return tag;
	}

	private KedaiTeh() {
	}
}
