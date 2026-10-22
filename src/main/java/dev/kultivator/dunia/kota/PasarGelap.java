package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.MusuhFxIds;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import dev.kultivator.voice.Voice;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Pasar Gelap under the coffin shop (DESIGN 34.5.1). The hidden door (an entity, {@link KotaPintuEntity}) slides
 * open for a player holding a Token Pasar Gelap. The broker sells, with no realm gate, realm keys, heavenvoid fragments
 * and maps, King cores and other modules' wares (Selendang Pengikat Naga, Dupa Arwah, Mutiara Penunjuk Laut when their
 * module is on) at 1.5 × base, and buys loot pouches and banners at 0.6 × value. Every purchase rolls
 * {@code kota.raidChance} (halved at Reputasi Gelap 50+) for a Razia: the town's guard sect bars the door for 60 s and
 * three guards attack that player only; a bribe of 20 NB × realm band ends it. Reputasi Gelap (−100..100) rises with
 * trade and falls with guard kills; below −50 the broker turns the player away.
 */
final class PasarGelap {

	static final double MARKUP = 1.5D;
	static final double BUYBACK = 0.6D;
	static final int RAID_TICKS = 1200;
	static final long BRIBE_PER_BAND = 20L;
	static final int TRADE_REP = 2;

	record Raid(long town, long until, List<KultivatorNpc> guards) {
	}

	private static final Map<UUID, Raid> RAIDS = new HashMap<>();

	static void clear() {
		RAIDS.clear();
	}

	static boolean raided(ServerPlayer player, long town) {
		Raid raid = RAIDS.get(player.getUUID());
		return raid != null && raid.town() == town && player.level().getGameTime() < raid.until();
	}

	static int shadowReputation(ServerPlayer player) {
		return Mth.clamp(KotaData.get(player.level().getServer()).getInt(player, "gelap_rep", 0), -100, 100);
	}

	static void addShadowReputation(ServerPlayer player, int delta) {
		if (delta == 0) {
			return;
		}
		KotaData.get(player.level().getServer()).putInt(player, "gelap_rep", Mth.clamp(shadowReputation(player) + delta, -100, 100));
	}

	// ================================================================== the door

	static void doorUsed(ServerLevel level, ServerPlayer player, KotaPintuEntity door) {
		if (!Kota.enabled() || !Kota.config().blackMarket) {
			return;
		}
		if (door.barred()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.gelap.pintu_dijaga").withStyle(ChatFormatting.RED));
			return;
		}
		if (door.open()) {
			return;
		}
		boolean token = player.getMainHandItem().is(KotaItems.TOKEN_PASAR_GELAP) || player.getOffhandItem().is(KotaItems.TOKEN_PASAR_GELAP);
		if (!token) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.gelap.pintu_diam").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			level.playSound(null, door.getX(), door.getY() + 1.0D, door.getZ(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.3F, 0.5F);
			return;
		}
		if (door.openFor(level)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.gelap.pintu_buka").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	// ================================================================== the broker

	record Offer(int index, Harga.Ware ware, ItemStack stack, long price) {
	}

	static List<Offer> offers(ServerPlayer player, KultivatorNpc npc) {
		long day = Kota.day(player.level().getServer());
		List<Harga.Ware> pool = new ArrayList<>();
		for (Harga.Ware ware : Harga.SHADOW) {
			if ("king_core".equals(ware.id()) || Harga.exists(ware.id())) {
				pool.add(ware);
			}
		}
		RandomSource random = RandomSource.create(npc.anchorSite() * 131L + day * 7L);
		int count = Math.min(pool.size(), 5 + random.nextInt(3));
		List<Offer> offers = new ArrayList<>();
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			Harga.Ware ware = pool.remove(random.nextInt(pool.size()));
			ItemStack stack = ware.make().make(RandomSource.create(npc.anchorSite() ^ day * 31L ^ i), 8);
			if (stack.isEmpty()) {
				continue;
			}
			// a Gerhana Darah makes the smugglers lie low: x1.25 while it lasts (34.4.3, K listens to SKY_EVENT through the sky service)
			double eclipse = KabarDunia.shadowFactor(player.level() instanceof net.minecraft.server.level.ServerLevel level ? level : null);
			long price = Math.max(1L, Math.round(Harga.priceOf(ware, stack) * MARKUP * eclipse * Kota.config().priceScale));
			offers.add(new Offer(i, ware, stack, price));
		}
		return offers;
	}

	static boolean buys(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return "loot_pouch".equals(id) || "demonic_loot_pouch".equals(id) || "demonic_banner".equals(id) || "kantong_saingan".equals(id);
	}

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.MAKELAR_GELAP) {
			return false;
		}
		if ("suap".equals(verb)) {
			bribe(player, npc.town());
			return true;
		}
		if (shadowReputation(player) <= -50) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.gelap.ditolak").withStyle(ChatFormatting.RED));
			return true;
		}
		if ("gjual".equals(verb)) {
			sell(player, npc);
			return true;
		}
		int index = Kota.arg(parts, 1, -1);
		long seen = Kota.arg(parts, 2, -1);
		for (Offer offer : offers(player, npc)) {
			if (offer.index() != index) {
				continue;
			}
			if (seen >= 0 && seen != offer.price()) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.toko.harga_berubah").withStyle(ChatFormatting.RED));
				return true;
			}
			if (!NilaiBatu.pay(player, offer.price())) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(offer.price())).withStyle(ChatFormatting.RED));
				return true;
			}
			ItemStack stack = offer.stack().copy();
			Component name = stack.getHoverName();
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				Kota.drop(player, stack);
			}
			addShadowReputation(player, TRADE_REP);
			KotaVoice.say(npc, KotaVoice.BLACK_WHISPER, npc.voiceKey("careful"), "kultivator.kota.gelap.suara.bisik");
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.dibeli", name, NilaiBatu.text(offer.price())).withStyle(ChatFormatting.DARK_PURPLE));
			double chance = Kota.config().raidChance * (shadowReputation(player) >= 50 ? 0.5D : 1.0D);
			if (player.getRandom().nextDouble() < chance) {
				raid(player, npc);
			}
			return true;
		}
		return true;
	}

	private static void sell(ServerPlayer player, KultivatorNpc npc) {
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!buys(hand)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.toko.tidak_beli").withStyle(ChatFormatting.RED));
			return;
		}
		long each = Math.max(1L, (long) Math.floor(Harga.value(hand) * BUYBACK * Kota.config().priceScale));
		int count = hand.getCount();
		Component name = hand.getHoverName();
		hand.shrink(count);
		NilaiBatu.give(player, each * count);
		addShadowReputation(player, TRADE_REP);
		Msg.actionBar(player, Component.translatable("kultivator.kota.toko.dijual", count, name, NilaiBatu.text(each * count)).withStyle(ChatFormatting.DARK_PURPLE));
	}

	// ================================================================== Razia

	/** The guard sect raids the market: the door is barred for 60 s and three guards come for {@code player}. */
	static void raid(ServerPlayer player, @Nullable KultivatorNpc broker) {
		ServerLevel level = (ServerLevel) player.level();
		long town = broker != null ? broker.town() : Towns.near(level, player.blockPosition(), 96);
		long until = level.getGameTime() + RAID_TICKS;
		for (KotaPintuEntity door : doors(level, town)) {
			door.setBarred(true);
		}
		List<KultivatorNpc> guards = new ArrayList<>();
		int band = Mth.clamp(Cultivators.get(player).realm().ordinal(), Realm.FOUNDATION.ordinal(), Realm.GOLDEN_CORE.ordinal());
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI * 2.0D * i / 3.0D + player.getRandom().nextDouble();
			Vec3 at = player.position().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
			KultivatorNpc guard = spawnGuard(level, at, town, band, player);
			if (guard != null) {
				guards.add(guard);
			}
		}
		RAIDS.put(player.getUUID(), new Raid(town, until, guards));
		if (!guards.isEmpty()) {
			KotaVoice.say(guards.getFirst(), KotaVoice.GUARD_HALT, guards.getFirst().voiceKey("attack"), "kultivator.kota.gelap.suara.razia");
		}
		Fx.send(level, player.position().add(0.0D, 1.0D, 0.0D), FxType.MUSUH, MusuhFxIds.AMBUSH_ALERT, 0x6A7AB8, 3.0F);
		Msg.chat(player, Component.translatable("kultivator.kota.gelap.razia", BRIBE_PER_BAND * Math.max(1, Cultivators.get(player).realm().ordinal()))
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	private static @Nullable KultivatorNpc spawnGuard(ServerLevel level, Vec3 at, long town, int realm, ServerPlayer target) {
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC)) {
			return null;
		}
		KultivatorNpc guard = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (guard == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, guard, at);
		if (spot == null) {
			spot = target.position();
		}
		int look = level.getRandom().nextInt();
		guard.snapTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		guard.setup(NpcRole.PENJAGA, look, realm, level.getRandom().nextInt(4), Dao.values()[level.getRandom().nextInt(Dao.COUNT)], 0, 0,
				Component.translatable("kultivator.kota.gelap.penjaga_razia"));
		guard.setTown(town);
		guard.setLonely(64.0D, 600);
		guard.setFightsMonsters(true);
		guard.memory().putBoolean("razia", true);
		if (!level.addFreshEntity(guard)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, guard);
		guard.markHostile(target);
		Fx.send(level, guard.position(), FxType.MUSUH, MusuhFxIds.ARRIVE, 0x6A7AB8, 2.0F);
		return guard;
	}

	/** A guard was right-clicked during the player's raid: the bribe offer. */
	static void offerBribe(ServerPlayer player, KultivatorNpc guard) {
		long price = BRIBE_PER_BAND * Math.max(1, Cultivators.get(player).realm().ordinal());
		Msg.chat(player, Component.translatable("kultivator.kota.gelap.suap_tawar", NilaiBatu.text(price))
				.withStyle(style -> style.withColor(ChatFormatting.GOLD)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/kultivasi suap"))));
		Voice.say(guard, guard.voiceKey("careful"), null);
	}

	/** Pays the bribe: the raid ends, the guards leave, the door opens again. */
	static boolean bribe(ServerPlayer player, long town) {
		Raid raid = RAIDS.get(player.getUUID());
		if (raid == null || player.level().getGameTime() >= raid.until()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.gelap.tidak_razia").withStyle(ChatFormatting.GRAY));
			return false;
		}
		long price = BRIBE_PER_BAND * Math.max(1, Cultivators.get(player).realm().ordinal());
		if (!NilaiBatu.pay(player, price)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price)).withStyle(ChatFormatting.RED));
			return false;
		}
		end(player.level().getServer(), player.getUUID(), raid);
		RAIDS.remove(player.getUUID());
		Msg.chat(player, Component.translatable("kultivator.kota.gelap.suap_terima").withStyle(ChatFormatting.GOLD));
		return true;
	}

	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L || RAIDS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Raid>> it = RAIDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Raid> entry = it.next();
			Raid raid = entry.getValue();
			boolean alive = false;
			for (KultivatorNpc guard : raid.guards()) {
				if (!guard.isRemoved() && guard.isAlive()) {
					alive = true;
				}
			}
			if (server.overworld().getGameTime() >= raid.until() || !alive) {
				end(server, entry.getKey(), raid);
				it.remove();
			}
		}
	}

	private static void end(MinecraftServer server, UUID player, Raid raid) {
		for (KultivatorNpc guard : raid.guards()) {
			if (!guard.isRemoved()) {
				guard.forgive(player);
				guard.vanish();
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (KotaPintuEntity door : doors(level, raid.town())) {
				door.setBarred(false);
			}
		}
	}

	private static List<KotaPintuEntity> doors(ServerLevel level, long town) {
		List<KotaPintuEntity> list = new ArrayList<>();
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, "kota/gerbang_gelap")) {
			KotaPintuEntity door = KotaAnchors.HANDLER.doorOf(anchor);
			if (door != null && door.town() == town) {
				list.add(door);
			}
		}
		return list;
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Offer offer : offers(player, npc)) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("i", offer.index());
			entry.putString("item", BuiltInRegistries.ITEM.getKey(offer.stack().getItem()).toString());
			entry.store("name", ComponentSerialization.CODEC, offer.stack().getHoverName());
			entry.putLong("price", offer.price());
			list.add(entry);
		}
		tag.put("offers", list);
		ItemStack hand = player.getMainHandItem();
		if (buys(hand)) {
			tag.store("hand", ComponentSerialization.CODEC, hand.getHoverName());
			tag.putLong("hand_price", Math.max(1L, (long) Math.floor(Harga.value(hand) * BUYBACK * Kota.config().priceScale)) * hand.getCount());
		}
		tag.putInt("rep", shadowReputation(player));
		tag.putDouble("raid", Kota.config().raidChance * (shadowReputation(player) >= 50 ? 0.5D : 1.0D));
		if (player.level() instanceof net.minecraft.server.level.ServerLevel here && KabarDunia.eclipse(here)) {
			// the Gerhana Darah surcharge (x1.25 in every price above) gets its own notice in the tab
			tag.putInt("eclipse", (int) Math.round((KabarDunia.shadowFactor(here) - 1.0D) * 100.0D));
		}
		Raid raid = RAIDS.get(player.getUUID());
		if (raid != null) {
			tag.putLong("raid_left", Math.max(0L, raid.until() - player.level().getGameTime()));
			tag.putLong("bribe", BRIBE_PER_BAND * Math.max(1, Cultivators.get(player).realm().ordinal()));
		}
		return tag;
	}

	private PasarGelap() {
	}
}
