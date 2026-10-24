package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.dunia.kota.KultivatorNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The friendly bouts of the places of 38DS (DESIGN 38.C.2.3 and 38.C.3.3; "Duel Pedang aturan ramah, pola KotaDuel"): a
 * sect compound's disciple on its training ground, a clan's young master who challenges a weak visitor, a patriarch who
 * must be beaten before the clan pledges. The town roles of {@code KotaDuel} have their own profiles; these bodies get a
 * profile only for the bout ({@link DuelistRegistry#override}) and the bout starts at once through
 * {@link DuelChallenges#startDirect} (the player already accepted on the card), so the town code is not involved.
 * <p>
 * Rules: Realm Suppression from two realms apart ({@link DuelRuleSet#suggested}), the 10 % line decides, no stake. The
 * bout ends through {@link DuelEndListeners}; the caller's {@link Hasil} gets the result once. Nobody dies (the duel
 * session stops both sides at 10 %).
 */
public final class TandingTempat {

	/** What the place does with the result: {@code menang} the player won, {@code kalah} he lost (both false: draw or cancel). */
	@FunctionalInterface
	public interface Hasil {
		void selesai(ServerPlayer player, KultivatorNpc npc, boolean menang, boolean kalah);
	}

	private record Laga(UUID player, Hasil hasil) {
	}

	/** NPC body UUID -> the bout it fights. */
	private static final Map<UUID, Laga> LAGA = new HashMap<>();
	private static boolean siap;

	static void init() {
		if (siap) {
			return;
		}
		siap = true;
		DuelEndListeners.add(TandingTempat::akhir);
	}

	/** Forgets every bout (server stop). */
	static void reset() {
		LAGA.clear();
	}

	/** True while {@code npc} fights one of these bouts. */
	public static boolean sibuk(KultivatorNpc npc) {
		return LAGA.containsKey(npc.getUUID()) || DuelSessions.byEntity(npc) != null;
	}

	/**
	 * Starts a friendly bout of {@code player} against {@code npc} with the fighting style {@code gaya} and qi colour
	 * {@code warna}; {@code senjata} goes into an empty main hand. {@code intro} plays the duel's intro film (a young
	 * master's or patriarch's challenge), else the bout begins at once. Null when it started, else the reason.
	 */
	public static @Nullable Component mulai(ServerPlayer player, KultivatorNpc npc, DuelStyle gaya, int warna, Item senjata, boolean intro,
			Hasil hasil) {
		if (sibuk(npc) || !npc.isAlive()) {
			return Component.translatable("kultivator.kompleks.tanding.sibuk", npc.getDisplayName());
		}
		Component self = DuelChallenges.selfProblem(player);
		if (self != null) {
			return self;
		}
		if (npc.getMainHandItem().isEmpty()) {
			npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(senjata));
		}
		DuelistRegistry.override(npc, profil(npc, gaya, warna));
		npc.setHealth(npc.getMaxHealth());
		npc.setTarget(null);
		npc.memory().putBoolean("hold", false);
		int gap = Cultivators.get(player).realm().ordinal() - npc.realm();
		DuelRuleSet rules = DuelRuleSet.suggested(gap).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN).withoutStake().withIntro(intro);
		Component why = DuelChallenges.startDirect(player, player, npc, DuelIds.PAIR_NPC, rules);
		if (why != null) {
			DuelistRegistry.override(npc, null);
			return why;
		}
		LAGA.put(npc.getUUID(), new Laga(player.getUUID(), hasil));
		return null;
	}

	/** The bout profile of a place body: rank from its realm and stage, honour by realm, surrender at 15 %, no stones. */
	static DuelistProfile profil(KultivatorNpc npc, DuelStyle gaya, int warna) {
		Realm realm = npc.realmValue();
		int stage = Mth.clamp(npc.stage(), 0, Math.max(0, realm.stages() - 1));
		int step = new Rank(realm, 0, stage).step();
		WeaponClass weapon = WeaponClass.of(npc);
		String kiai = npc.female() ? "grunt.female.attack" : "grunt.male.attack";
		int honour = 1000 + 25 * npc.realm();
		return new DuelistProfile(gaya, step, weapon, warna, kiai, honour, Math.max(0.15F, gaya.surrender), 0, 0, null, false);
	}

	private static void akhir(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (LivingEntity side : new LivingEntity[]{session.a(), session.b()}) {
			if (!(side instanceof KultivatorNpc npc)) {
				continue;
			}
			Laga laga = LAGA.remove(npc.getUUID());
			if (laga == null) {
				continue;
			}
			DuelistRegistry.override(npc, null);
			if (!(npc.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(laga.player());
			if (player == null) {
				continue;
			}
			boolean menang = winner == player;
			boolean kalah = loser == player;
			try {
				laga.hasil().selesai(player, npc, menang, kalah);
			} catch (RuntimeException e) {
				dev.kultivator.Kultivator.LOGGER.warn("Place bout result failed for {}", npc, e);
			}
		}
	}

	/** The plain iron sword a bout body takes when its hand is empty. */
	public static Item pedang() {
		return Items.IRON_SWORD;
	}

	private TandingTempat() {
	}
}
