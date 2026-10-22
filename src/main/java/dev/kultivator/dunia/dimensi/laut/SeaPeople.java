package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.DimensiNpc;
import dev.kultivator.dunia.dimensi.DimensiSky;
import dev.kultivator.dunia.dimensi.DimensiVoice;
import dev.kultivator.dunia.dimensi.TandaEntity;
import dev.kultivator.dunia.dimensi.bawah.GhostQuests;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

import java.util.List;
import java.util.UUID;

/**
 * The people of Laut Tanpa Batas (DESIGN 34.5.14) and their errands.
 * <ul>
 * <li><b>Raja Naga</b> (Journey to the West ch. 3; audience from {@code palaceMinRealm}): trades the Mutiara Naga, opens his
 * armoury of sea-forged artifacts (water element, forged by the palace: grade 3 at once, grade 4 for an honoured guest)
 * and gives two quests, {@code extra.laut.q}: 1 = the rebel yaksha chief (Fengshen ch. 12's Li Gen, who served the
 * Dragon King) is out, 2 = slain, report; 3 = the serpent that stole a pearl is out, 4 = slain, report; 5 = all done.</li>
 * <li><b>Penjaga Gerbang</b>: after the first quest, the Mutiara Penghindar Air (once).</li>
 * <li><b>Pertapa</b> of a Pulau Pertapa: once per player, a lesson (comprehension) and aged spirit grass.</li>
 * <li><b>Dewa Penglai</b> (Dao Union+, {@code penglaiMinRealm}): a duel of insight through {@link DuelRules} (ends at
 * 10 % health); the winner receives the Air Mata Air Penglai once, which, drunk, grants a deep insight (+20
 * comprehension of one's Dao) once per player.</li>
 * <li><b>Pedagang Pulau</b>: sells sea goods for spirit stones (fixed prices, NB).</li>
 * <li><b>Nelayan</b>: the old fisherman's news of the sea: the nearest island of a chosen kind, the Dragon Palace or
 * Penglai (the spine's locate, one search per player per 30 s, the site then known on the map), or where Penyu Ao swims.</li>
 * <li>Herb beds (Pulau Herba): one harvest per bed per player per day, an aged spirit herb; two during the twin moons'
 * spirit tide.</li>
 * </ul>
 * The four kinds of spirit island ({@code extra.laut.pulau} bits) a player has stood on give {@code dunia/islands_all}.
 */
public final class SeaPeople {

	/** The merchant's goods: item id, count, price in NB. */
	private record Offer(String id, int count, int price) {
	}

	/** An armoury piece: form, grade, sub-grade, price in NB, the quest step needed. */
	private record Arm(String form, int grade, int sub, int price, int needQ) {
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
	/**
	 * The Dragon King's armoury (water-element artifacts, maker Ao Guang): priced a tenth above the towns' base NB of the
	 * grade ({@code kota/Harga.ARTIFACT}: 700 for grade 3, 2 400 for grade 4) for the sea element and the better
	 * sub-grade; grade 4 only for the honoured guest (quests done).
	 */
	private static final Arm[] ARMORY = {
			new Arm(ArtifactItem.PEDANG, 3, 1, 770, 0),
			new Arm(ArtifactItem.PAYUNG, 3, 1, 770, 0),
			new Arm(ArtifactItem.LONCENG, 3, 1, 770, 0),
			new Arm(ArtifactItem.PEDANG, 4, 1, 2640, 5),
			new Arm(ArtifactItem.WAJAN, 4, 1, 2640, 5),
	};
	public static final String ARMORY_MAKER = "Ao Guang";
	public static final int PEARL_PRICE = 80;
	public static final double PENGLAI_INSIGHT = 20.0D;
	public static final double HERMIT_INSIGHT = 3.0D;
	/** The island kinds (bit order of {@code extra.laut.pulau}). */
	public static final List<String> ISLAND_KINDS = List.of("herba", "pertapa", "pasar", "karang");
	/** What the fisherman knows: the four islands, the palace, Penglai, the turtle. */
	public static final List<String> FISHER_NEWS = List.of("herba", "pertapa", "pasar", "karang", "istana", "penglai", "penyu");

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
			case LautNpcEntity.NELAYAN -> openFisher(player, npc);
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
			DimensiVoice.say(king, DimensiVoice.DRAGON_KING, "boss.taunt", "kultivator.dimensi.voice.dragon_king");
			Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.tolak", min.displayName()).withColor(0x7FE0F0));
			return;
		}
		DimensiVoice.say(king, DimensiVoice.DRAGON_KING, "boss.awaken", "kultivator.dimensi.voice.dragon_king");
		Advancements.grant(player, "dunia/dragon_king");
		int q = data(player).getIntOr("q", 0);
		CompoundTag tag = new CompoundTag();
		tag.putInt("q", q);
		tag.putInt("price", PEARL_PRICE);
		tag.putLong("nb", DimensiData.nbIn(player));
		ListTag armory = new ListTag();
		for (Arm arm : ARMORY) {
			CompoundTag entry = new CompoundTag();
			entry.putString("form", arm.form());
			entry.putInt("grade", arm.grade());
			entry.putInt("price", arm.price());
			entry.putBoolean("open", q >= arm.needQ());
			armory.add(entry);
		}
		tag.put("armory", armory);
		DimensiNet.open(player, DimensiNet.SCREEN_KING, king.getId(), tag);
	}

	/** {@code raja:<beli|q1|q2|lapor|senjata:i>}. */
	public static void kingAction(ServerPlayer player, @Nullable LautNpcEntity king, String what) {
		CompoundTag laut = data(player);
		int q = laut.getIntOr("q", 0);
		ServerLevel level = player.level();
		if (what.startsWith("senjata:")) {
			int index;
			try {
				index = Integer.parseInt(what.substring("senjata:".length()));
			} catch (NumberFormatException exception) {
				return;
			}
			buyArm(player, king, index, q);
			return;
		}
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
					Advancements.grant(player, "dunia/pearl_returned");
				} else {
					return;
				}
				DimensiData.dirty(player);
				if (king != null) {
					king.talk(60);
					DimensiVoice.sayReused(king, "elder.inheritance", "kultivator.dimensi.voice.dragon_king_thanks");
				}
			}
			default -> {
			}
		}
	}

	/** One piece of the armoury (NB in real stones; the grade-4 pieces only for the honoured guest). */
	private static void buyArm(ServerPlayer player, @Nullable LautNpcEntity king, int index, int q) {
		if (index < 0 || index >= ARMORY.length) {
			return;
		}
		Arm arm = ARMORY[index];
		Realm min = DimensiConfig.realm(DimensiConfig.get().palaceMinRealm, Realm.DEITY_TRANSFORMATION);
		if (!Cultivators.get(player).realm().atLeast(min)) {
			// the armoury is part of the audience: never sold to one the King would not receive
			return;
		}
		if (q < arm.needQ()) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.senjata_tutup").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!DimensiData.pay(player, arm.price())) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.uang_kurang", arm.price()).withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack artifact = ArtifactItem.create(arm.form(), arm.grade(), arm.sub(), 0, Element.WATER, ARMORY_MAKER);
		DimensiData.give(player, artifact);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		DimensiNet.fx(player.level(), DimensiNet.FX_BLESS, player.position(), 1, 32.0D);
		Msg.chat(player, Component.translatable("kultivator.dimensi.raja_naga.senjata_beli", artifact.getHoverName()).withColor(0x7FE0F0));
		if (king != null) {
			king.talk(40);
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
			DimensiVoice.say(guard, DimensiVoice.GATE_GUARD, "disciple.m1.thanks", "kultivator.dimensi.penjaga_laut.beri");
			Msg.chat(player, Component.translatable("kultivator.dimensi.penjaga_laut.beri").withColor(0x7FE0F0));
			return;
		}
		DimensiVoice.say(guard, DimensiVoice.GATE_GUARD, "disciple.m1.greet", "kultivator.dimensi.penjaga_laut.salam");
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
				immortal.bow(40);
				Msg.chat(player, Component.translatable("kultivator.dimensi.penglai.menang").withColor(0xFFE9A8));
			} else if (player.isAlive()) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.penglai.kalah").withColor(0xC8E0B0));
			}
		});
		if (started) {
			immortal.setFlag(DimensiNpc.FLAG_FIGHT, true);
			immortal.setFlag(DimensiNpc.FLAG_SIT, false);
			immortal.setTarget(player);
			DimensiVoice.say(immortal, DimensiVoice.PENGLAI_TRIAL, "elder.advice", "kultivator.dimensi.voice.penglai_trial");
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
		Advancements.grant(player, "dunia/penglai_water");
		return true;
	}

	// ================================================================== the merchant

	private static void openMerchant(ServerPlayer player, LautNpcEntity merchant) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
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

	// ================================================================== the fisherman

	private static void openFisher(ServerPlayer player, LautNpcEntity fisher) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("wait", DuniaSites.locateCooldown(player));
		tag.putInt("seen", data(player).getIntOr("pulau", 0));
		tag.putInt("sky", DimensiSky.code(player.level().getServer()));
		tag.putBoolean("turtle", DimensiConfig.get().turtle);
		DimensiVoice.sayReused(fisher, "disciple.m3.greet", "kultivator.dimensi.voice.nelayan");
		DimensiNet.open(player, DimensiNet.SCREEN_FISHER, fisher.getId(), tag);
	}

	/** {@code nelayan:<herba|pertapa|pasar|karang|istana|penglai|penyu>}: the news of one place. */
	public static void fisherAction(ServerPlayer player, LautNpcEntity fisher, String what) {
		if (!FISHER_NEWS.contains(what) || !DimensiLevels.isLaut(player.level())) {
			return;
		}
		ServerLevel level = player.level();
		fisher.talk(50);
		if ("penyu".equals(what)) {
			PenyuAoEntity turtle = null;
			double best = Double.MAX_VALUE;
			for (PenyuAoEntity candidate : level.getEntities(DimensiEntities.PENYU_AO, entity -> entity.isAlive())) {
				double d = candidate.distanceToSqr(player);
				if (d < best) {
					best = d;
					turtle = candidate;
				}
			}
			if (turtle == null) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.nelayan.penyu_tiada").withColor(0x9FE8F0));
				return;
			}
			say(player, "kultivator.dimensi.nelayan.penyu", turtle.getX() - player.getX(), turtle.getZ() - player.getZ());
			return;
		}
		long cooldown = DuniaSites.locateCooldown(player);
		if (cooldown > 0L) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.nelayan.tunggu", cooldown / 20L + 1L).withColor(0x9FE8F0));
			return;
		}
		String kind;
		int tier;
		switch (what) {
			case "istana" -> {
				kind = "laut/istana_naga";
				tier = 5;
			}
			case "penglai" -> {
				kind = "laut/penglai";
				tier = 6;
			}
			default -> {
				kind = "laut/pulau";
				tier = 3;
			}
		}
		DuniaSites.Site site = DuniaSites.locateFor(player, SeaGate.newsTag(what), 64, kind, tier);
		if (site == null) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.nelayan.tiada", Component.translatable("kultivator.dimensi.nelayan.tempat." + what))
					.withColor(0x9FE8F0));
			return;
		}
		DuniaSites.discover(player, site.id());
		say(player, "kultivator.dimensi.nelayan.kabar." + what, site.pos().getX() + 0.5D - player.getX(), site.pos().getZ() + 0.5D - player.getZ());
	}

	private static void say(ServerPlayer player, String key, double dx, double dz) {
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Msg.chat(player, Component.translatable(key, Component.translatable(GhostQuests.compass(dx, dz)), distance).withColor(0x9FE8F0));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.1F);
	}

	// ================================================================== herb beds, islands and the turtle

	/** A herb bed mark was used: one aged herb per bed per player per day (two during the twin moons' spirit tide). */
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
		boolean tide = DimensiSky.twinMoons(player.level().getServer());
		int picks = tide ? 2 : 1;
		ItemStack shown = ItemStack.EMPTY;
		for (int i = 0; i < picks; i++) {
			HerbKind kind = kinds[player.getRandom().nextInt(kinds.length)];
			Item herb = HartaItems.herb(kind);
			if (herb == null) {
				continue;
			}
			ItemStack stack = new ItemStack(herb);
			stack.set(HartaComponents.HERB_AGE, 150 + player.getRandom().nextInt(250));
			if (shown.isEmpty()) {
				shown = stack.copy();
			}
			DimensiData.give(player, stack);
			DimensiNet.fx(player.level(), DimensiNet.FX_HARVEST, mark.position(), kind.ordinal(), 32.0D);
		}
		if (shown.isEmpty()) {
			return;
		}
		player.level().playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
		Msg.actionBar(player, Component.translatable(tide ? "kultivator.dimensi.herba.petik_pasang" : "kultivator.dimensi.herba.petik",
				shown.getHoverName()).withColor(0xC8E0B0));
	}

	/** A player stands on a spirit island of {@code jenis} (its site anchor's pass): the four kinds give an advancement. */
	static void islandSeen(ServerPlayer player, String jenis) {
		int bit = ISLAND_KINDS.indexOf(jenis);
		if (bit < 0) {
			return;
		}
		CompoundTag laut = data(player);
		int seen = laut.getIntOr("pulau", 0);
		if ((seen & (1 << bit)) != 0) {
			return;
		}
		seen |= 1 << bit;
		laut.putInt("pulau", seen);
		DimensiData.dirty(player);
		Msg.actionBar(player, Component.translatable("kultivator.dimensi.pulau.tiba", Component.translatable("kultivator.dimensi.nelayan.tempat." + jenis),
				Integer.bitCount(seen), ISLAND_KINDS.size()).withColor(0x9FE8F0));
		if (Integer.bitCount(seen) >= ISLAND_KINDS.size()) {
			Advancements.grant(player, "dunia/islands_all");
		}
	}

	/** A player stands on Penyu Ao's back (every 2 s). */
	static void onTurtle(ServerPlayer player) {
		CompoundTag laut = data(player);
		if (!laut.getBooleanOr("penyu", false)) {
			laut.putBoolean("penyu", true);
			DimensiData.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.dimensi.penyu.naik").withColor(0x9FE0C0));
		}
		Advancements.grant(player, "dunia/turtle_rider");
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
