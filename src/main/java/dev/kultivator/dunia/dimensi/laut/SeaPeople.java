package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.dunia.dimensi.TandaEntity;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The people of Laut Tanpa Batas (DESIGN 34.5.14) and their errands.
 * <ul>
 * <li><b>Raja Naga</b> (Journey to the West ch. 3; audience from {@code palaceMinRealm}): trades the Mutiara Naga and
 * gives two quests, {@code extra.laut.q}: 1 = the rebel yaksha chief (Fengshen ch. 12's Li Gen, who served the Dragon
 * King) is out, 2 = slain, report; 3 = the serpent that stole a pearl is out, 4 = slain, report; 5 = all done.</li>
 * <li><b>Penjaga Gerbang</b>: after the first quest, the Mutiara Penghindar Air (once).</li>
 * <li><b>Pertapa</b> of a Pulau Pertapa: once per player, a lesson (comprehension) and aged spirit grass.</li>
 * <li><b>Dewa Penglai</b> (Dao Union+, {@code penglaiMinRealm}): a duel of insight through {@link DuelRules} (ends at
 * 10 % health); the winner receives the Air Mata Air Penglai once, which, drunk, grants a deep insight (+20
 * comprehension of one's Dao) once per player.</li>
 * <li><b>Pedagang Pulau</b>: sells sea goods for spirit stones (fixed prices, NB).</li>
 * <li>Herb beds (Pulau Herba): one harvest per bed per player per day, an aged spirit herb.</li>
 * </ul>
 */
public final class SeaPeople {

	/** The merchant's goods: item id, count, price in NB. */
	private record Offer(String id, int count, int price) {
	}

	private static final Offer[] MERCHANT = {
			new Offer("kultivator:mutiara_penunjuk_laut", 1, 12),
			new Offer("minecraft:prismarine_shard", 16, 2),
			new Offer("minecraft:prismarine_crystals", 16, 3),
			new Offer("minecraft:nautilus_shell", 1, 4),
			new Offer("minecraft:heart_of_the_sea", 1, 40),
			new Offer("minecraft:sea_pickle", 8, 1),
			new Offer("minecraft:cooked_cod", 16, 2),
	};
	public static final int PEARL_PRICE = 80;
	public static final double PENGLAI_INSIGHT = 20.0D;
	public static final double HERMIT_INSIGHT = 3.0D;

	static CompoundTag data(ServerPlayer player) {
		return DimensiData.extra(player, DimensiData.LAUT);
	}

	static boolean interact(ServerPlayer player, LautNpcEntity npc) {
		switch (npc.role()) {
			case LautNpcEntity.RAJA_NAGA -> openKing(player, npc);
			case LautNpcEntity.PENJAGA -> guard(player, npc);
			case LautNpcEntity.PERTAPA -> hermit(player, npc);
			case LautNpcEntity.DEWA_PENGLAI -> openPenglai(player, npc);
			case LautNpcEntity.PEDAGANG -> openMerchant(player, npc);
			default -> {
				return false;
			}
		}
		return true;
	}

	// ================================================================== the Dragon King

	private static void openKing(ServerPlayer player, LautNpcEntity king) {
		Realm min = DimensiConfig.realm(DimensiConfig.get().palaceMinRealm, Realm.DEITY_TRANSFORMATION);
		if (!Cultivators.get(player).realm().atLeast(min)) {
			Voice.say(king, "boss.taunt", "kultivator.dimensi.voice.dragon_king");
			Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.tolak", min.displayName()).withColor(0x7FE0F0));
			return;
		}
		Voice.say(king, "boss.awaken", "kultivator.dimensi.voice.dragon_king");
		Advancements.grant(player, "dunia/dragon_king");
		CompoundTag tag = new CompoundTag();
		tag.putInt("q", data(player).getIntOr("q", 0));
		tag.putInt("price", PEARL_PRICE);
		tag.putLong("nb", DimensiData.nbIn(player));
		DimensiNet.open(player, DimensiNet.SCREEN_KING, king.getId(), tag);
	}

	/** {@code raja:<beli|q1|q2|lapor>}. */
	public static void kingAction(ServerPlayer player, @Nullable LautNpcEntity king, String what) {
		CompoundTag laut = data(player);
		int q = laut.getIntOr("q", 0);
		ServerLevel level = player.level();
		switch (what) {
			case "beli" -> {
				if (DimensiData.pay(player, PEARL_PRICE)) {
					DimensiData.give(player, new ItemStack(DimensiItems.MUTIARA_NAGA));
					Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.beli").withColor(0x7FE0F0));
				} else {
					Msg.chat(player, Component.translatable("kultivator.dimensi.uang_kurang", PEARL_PRICE).withStyle(ChatFormatting.GRAY));
				}
			}
			case "q1" -> {
				if (q != 0) {
					return;
				}
				Vec3 at = questSpot(level, king != null ? king.position() : player.position());
				YakshaEntity chief = DimensiEntities.YAKSHA.create(level, EntitySpawnReason.EVENT);
				if (chief == null) {
					return;
				}
				chief.setup(YakshaEntity.LAUT, tierFor(player), true);
				chief.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
				if (level.addFreshEntity(chief)) {
					laut.putInt("q", 1);
					laut.putString("qt", chief.getUUID().toString());
					DimensiData.dirty(player);
					Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.q1", (int) at.x, (int) at.z).withColor(0x7FE0F0));
				}
			}
			case "q2" -> {
				if (q != 2 && q != 3) {
					return;
				}
				Vec3 at = questSpot(level, king != null ? king.position() : player.position());
				UlarLautEntity serpent = DimensiEntities.ULAR_LAUT.create(level, EntitySpawnReason.EVENT);
				if (serpent == null) {
					return;
				}
				serpent.setup(tierFor(player), true);
				serpent.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
				if (level.addFreshEntity(serpent)) {
					laut.putInt("q", 3);
					laut.putString("qt", serpent.getUUID().toString());
					DimensiData.dirty(player);
					Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.q2", (int) at.x, (int) at.z).withColor(0x7FE0F0));
				}
			}
			case "lapor" -> {
				if (q == 2 && !laut.getBooleanOr("q1_paid", false)) {
					laut.putBoolean("q1_paid", true);
					DimensiData.give(player, new ItemStack(DimensiItems.MUTIARA_NAGA, 2));
					DimensiData.giveNb(player, 80L);
					Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.lapor1").withColor(0xE8D9A8));
				} else if (q == 4) {
					laut.putInt("q", 5);
					DimensiData.give(player, new ItemStack(DimensiItems.MUTIARA_NAGA, 3));
					DimensiData.give(player, new ItemStack(Items.TRIDENT));
					DimensiData.give(player, new ItemStack(Items.HEART_OF_THE_SEA));
					DimensiData.giveNb(player, 160L);
					DuniaApi.beasts().grantItem(player, "mutiara_naga");
					Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.lapor2").withColor(0xE8D9A8));
				} else {
					return;
				}
				DimensiData.dirty(player);
				if (king != null) {
					Voice.say(king, "elder.inheritance", "kultivator.dimensi.voice.dragon_king");
				}
			}
			default -> {
			}
		}
	}

	/** A spot 40-60 b from {@code near} in open water at mid depth. */
	private static Vec3 questSpot(ServerLevel level, Vec3 near) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
		double r = 40.0D + level.getRandom().nextDouble() * 20.0D;
		return new Vec3(near.x + Math.cos(angle) * r, 72.0D, near.z + Math.sin(angle) * r);
	}

	private static int tierFor(ServerPlayer player) {
		return Mth.clamp(Cultivators.get(player).realm().ordinal() - Realm.GOLDEN_CORE.ordinal(), 0, 4);
	}

	/** A quest target was slain by {@code killer}. */
	public static void questKill(ServerPlayer killer, Entity victim, String what) {
		CompoundTag laut = data(killer);
		int q = laut.getIntOr("q", 0);
		String target = laut.getStringOr("qt", "");
		if (!target.equals(victim.getUUID().toString())) {
			return;
		}
		if (q == 1 && "yaksha_chief".equals(what)) {
			laut.putInt("q", 2);
			Msg.chat(killer, Component.translatable("kultivator.dimensi.raja_naga.q1_selesai").withColor(0xE8D9A8));
		} else if (q == 3 && "ular_mutiara".equals(what)) {
			laut.putInt("q", 4);
			Msg.chat(killer, Component.translatable("kultivator.dimensi.raja_naga.q2_selesai").withColor(0xE8D9A8));
		} else {
			return;
		}
		laut.remove("qt");
		DimensiData.dirty(killer);
	}

	// ================================================================== the gate guard

	private static void guard(ServerPlayer player, LautNpcEntity guard) {
		CompoundTag laut = data(player);
		if (laut.getIntOr("q", 0) >= 2 && !laut.getBooleanOr("breath", false)) {
			laut.putBoolean("breath", true);
			DimensiData.dirty(player);
			DimensiData.give(player, new ItemStack(DimensiItems.MUTIARA_PENGHINDAR_AIR));
			Voice.say(guard, "disciple.m1.thanks", "kultivator.dimensi.penjaga_laut.beri");
			Msg.chat(player, Component.translatable("kultivator.dimensi.penjaga_laut.beri").withColor(0x7FE0F0));
			return;
		}
		Voice.say(guard, "disciple.m1.greet", "kultivator.dimensi.penjaga_laut.salam");
		Msg.chat(player, Component.translatable(laut.getBooleanOr("breath", false) ? "kultivator.dimensi.penjaga_laut.salam"
				: "kultivator.dimensi.penjaga_laut.tunggu").withColor(0x7FE0F0));
	}

	// ================================================================== the hermit

	private static void hermit(ServerPlayer player, LautNpcEntity hermit) {
		CompoundTag laut = data(player);
		CultivationData data = Cultivators.get(player);
		if (laut.getBooleanOr("pertapa", false)) {
			Voice.say(hermit, "elder.advice", null);
			Msg.chat(player, Component.translatable("kultivator.dimensi.pertapa.lagi").withColor(0xC8E0B0));
			return;
		}
		laut.putBoolean("pertapa", true);
		DimensiData.dirty(player);
		Dao dao = data.dao();
		if (dao != null) {
			Comprehension.grant(player, dao, HERMIT_INSIGHT);
		}
		ItemStack grass = new ItemStack(HartaItems.herb(HerbKind.SPIRIT_GRASS), 2);
		grass.set(HartaComponents.HERB_AGE, 300);
		DimensiData.give(player, grass);
		Voice.say(hermit, "elder.welcome", null);
		Msg.chat(player, Component.translatable("kultivator.dimensi.pertapa.ajar").withColor(0xC8E0B0));
	}

	// ================================================================== Penglai

	private static void openPenglai(ServerPlayer player, LautNpcEntity immortal) {
		Realm min = DimensiConfig.realm(DimensiConfig.get().penglaiMinRealm, Realm.DAO_UNION);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("worthy", Cultivators.get(player).realm().atLeast(min));
		tag.putString("min", min.id());
		tag.putBoolean("won", data(player).getBooleanOr("penglai_menang", false));
		tag.putBoolean("drunk", data(player).getBooleanOr("penglai_minum", false));
		Voice.say(immortal, "elder.welcome", null);
		DimensiNet.open(player, DimensiNet.SCREEN_PENGLAI, immortal.getId(), tag);
	}

	/** {@code penglai:uji}: the duel of insight. */
	public static void penglaiTrial(ServerPlayer player, LautNpcEntity immortal) {
		Realm min = DimensiConfig.realm(DimensiConfig.get().penglaiMinRealm, Realm.DAO_UNION);
		CompoundTag laut = data(player);
		if (!Cultivators.get(player).realm().atLeast(min) || laut.getBooleanOr("penglai_menang", false) || DuelRules.inDuel(player)) {
			return;
		}
		immortal.setup(LautNpcEntity.DEWA_PENGLAI, immortal.look(), Cultivators.get(player).realm().ordinal());
		boolean started = DuelRules.begin(player, immortal, 20 * 150, (winner, loser, reason) -> {
			immortal.setFlag(DimensiNpc.FLAG_FIGHT, false);
			immortal.setTarget(null);
			immortal.setHealth(immortal.getMaxHealth());
			if (winner == player && !laut.getBooleanOr("penglai_menang", false)) {
				laut.putBoolean("penglai_menang", true);
				DimensiData.dirty(player);
				DimensiData.give(player, new ItemStack(DimensiItems.AIR_MATA_AIR_PENGLAI));
				Voice.say(immortal, "elder.inheritance", null);
				Msg.chat(player, Component.translatable("kultivator.dimensi.penglai.menang").withColor(0xFFE9A8));
			} else if (player.isAlive()) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.penglai.kalah").withColor(0xC8E0B0));
			}
		});
		if (started) {
			immortal.setFlag(DimensiNpc.FLAG_FIGHT, true);
			immortal.setTarget(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.penglai.mulai").withColor(0xFFE9A8));
		}
	}

	/** Air Mata Air Penglai: +20 comprehension of the player's Dao, once per player. */
	public static boolean drinkPenglai(ServerPlayer player) {
		CompoundTag laut = data(player);
		if (laut.getBooleanOr("penglai_minum", false)) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.penglai.sudah").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Dao dao = Cultivators.get(player).dao();
		if (dao == null) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.penglai.tanpa_dao").withStyle(ChatFormatting.GRAY));
			return false;
		}
		laut.putBoolean("penglai_minum", true);
		DimensiData.dirty(player);
		Comprehension.grant(player, dao, PENGLAI_INSIGHT);
		DimensiNet.fx(player.level(), DimensiNet.FX_BLESS, player.position(), 2, 48.0D);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.3F);
		Voice.heaven(player, "heaven.dao_insight");
		return true;
	}

	// ================================================================== the merchant

	private static void openMerchant(ServerPlayer player, LautNpcEntity merchant) {
		CompoundTag tag = new CompoundTag();
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (Offer offer : MERCHANT) {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", offer.id());
			entry.putInt("count", offer.count());
			entry.putInt("price", offer.price());
			list.add(entry);
		}
		tag.put("offers", list);
		tag.putLong("nb", DimensiData.nbIn(player));
		Voice.say(merchant, "disciple.m2.greet", null);
		DimensiNet.open(player, DimensiNet.SCREEN_MERCHANT, merchant.getId(), tag);
	}

	/** {@code beli:<index>}. */
	public static void buy(ServerPlayer player, int index) {
		if (index < 0 || index >= MERCHANT.length) {
			return;
		}
		Offer offer = MERCHANT[index];
		ItemStack stack = Dimensi.itemById(offer.id(), offer.count());
		if (stack.isEmpty()) {
			return;
		}
		if (!DimensiData.pay(player, offer.price())) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.uang_kurang", offer.price()).withStyle(ChatFormatting.GRAY));
			return;
		}
		DimensiData.give(player, stack);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	// ================================================================== herb beds and the turtle

	/** A herb bed mark was used: one aged herb per bed per player per day. */
	public static void harvest(ServerPlayer player, TandaEntity mark, @Nullable DuniaAnchorEntity anchor) {
		long day = DimensiData.day(player.level().getServer());
		CompoundTag state = anchor != null ? anchor.state() : new CompoundTag();
		String key = "h_" + player.getUUID();
		if (state.getLongOr(key, -1L) == day) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.herba.habis").withStyle(ChatFormatting.GRAY));
			return;
		}
		state.putLong(key, day);
		HerbKind[] kinds = {HerbKind.SPIRIT_GRASS, HerbKind.NINECURL_GINSENG, HerbKind.EIGHT_ANGLED_ICE_GRASS, HerbKind.PURPLE_FLAME_FLOWER,
				HerbKind.YELLOW_ESSENCE_MUSHROOM, HerbKind.ILLUSORY_SPIRIT_HERB};
		HerbKind kind = kinds[player.getRandom().nextInt(kinds.length)];
		Item herb = HartaItems.herb(kind);
		if (herb == null) {
			return;
		}
		ItemStack stack = new ItemStack(herb);
		stack.set(HartaComponents.HERB_AGE, 150 + player.getRandom().nextInt(250));
		DimensiData.give(player, stack);
		DimensiNet.fx(player.level(), DimensiNet.FX_HARVEST, mark.position(), kind.ordinal(), 32.0D);
		player.level().playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.dimensi.herba.petik", stack.getHoverName()).withColor(0xC8E0B0));
	}

	/** A player stands on Penyu Ao's back (every 2 s). */
	static void onTurtle(ServerPlayer player) {
		CompoundTag laut = data(player);
		if (!laut.getBooleanOr("penyu", false)) {
			laut.putBoolean("penyu", true);
			DimensiData.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.penyu.naik").withColor(0x9FE0C0));
		}
	}

	// ================================================================== spawning

	static @Nullable LautNpcEntity spawn(ServerLevel level, Vec3 at, float yaw, int role, int realm, @Nullable UUID anchor) {
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, at)) {
			return null;
		}
		LautNpcEntity npc = DimensiEntities.RAJA_NAGA.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		npc.setup(role, role == LautNpcEntity.RAJA_NAGA ? 7 : level.getRandom().nextInt(9), realm);
		npc.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		npc.setHome(BlockPos.containing(at), role == LautNpcEntity.PERTAPA ? 8 : 3);
		npc.setAnchor(anchor);
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		return npc;
	}

	private SeaPeople() {
	}
}
