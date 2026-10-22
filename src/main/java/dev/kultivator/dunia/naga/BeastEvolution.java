package dev.kultivator.dunia.naga;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.api.BeastEvolutionService;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Evolusi Binatang Roh (DESIGN 34.5.12, contract C34.B1): the {@link BeastEvolutionService} of module B, the offerings of
 * the Kesengsaraan Binatang Roh with their fallbacks, Luka Kesengsaraan, and what an evolution tells the world
 * ({@code DuniaEvents.BEAST_EVOLVED}, the advancements, the new bloodline art). Tiers 0-3 and their XP rule stay exactly
 * as in phase 3; 4-6 need the tribulation:
 * <ul>
 * <li>tier 4: 1 King beast core + 3 Batu Meteor Roh (module L; fallback 3 High spirit stones);</li>
 * <li>tier 5: the wolf an Inti Iblis Kuno (module R), the fox 3 Bulu Ekor Roh, the Jiao a Mutiara Naga (module D's Dragon
 * Palace); fallback 1 Supreme stone;</li>
 * <li>tier 6: a Persik Abadi (module K's Kota Abadi; fallback 2 Supreme stones) + 3 Esensi Makam.</li>
 * </ul>
 * Offerings of a module that is off or missing always have their fallback (config {@code fallbackItems}, default on).
 */
public final class BeastEvolution implements BeastEvolutionService {

	/** Luka Kesengsaraan on a wolf or fox: the game time until which no new tribulation may start (a Jiao keeps it in its record). */
	public static final AttachmentType<Long> LUKA = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG)
			.buildAndRegister(Kultivator.id("beast_luka"));

	public static final int LINE_WOLF = 0;
	public static final int LINE_FOX = 1;
	public static final int LINE_JIAO = 2;

	/** One part of an offering: {@code count} of {@code id}, else {@code fallbackCount} of {@code fallback} (null = none). */
	record Need(String id, int count, @Nullable String fallback, int fallbackCount) {
	}

	static void init() {
		// loads LUKA
	}

	// ================================================================== the service (contract C34.B1)

	@Override
	public int tier(LivingEntity beast) {
		if (beast instanceof NagaLiarEntity dragon) {
			return dragon.isTamed() ? dragon.tier() : 0;
		}
		BeastData data = SpiritBeasts.data(beast);
		return data == null ? 0 : data.tier();
	}

	@Override
	public String line(LivingEntity beast) {
		int line = lineOf(beast);
		return line < 0 ? "" : BeastData.lineOf(line);
	}

	@Override
	public boolean canEvolve(ServerPlayer owner, LivingEntity beast) {
		return problem(owner, beast, false) == null;
	}

	@Override
	public boolean startTribulation(ServerPlayer owner, LivingEntity beast, boolean test) {
		return BeastTribulation.start(owner, beast, test, null);
	}

	@Override
	public void grantItem(ServerPlayer player, String what) {
		if ("darah_jiao".equals(what) || "darah".equals(what)) {
			NagaItems.give(player, DarahJiaoItem.of(NagaKind.AIR));
			return;
		}
		Item item = switch (what == null ? "" : what) {
			case "sisik_jiao", "sisik" -> NagaItems.SISIK_JIAO;
			case "mutiara_jiao", "mutiara" -> NagaItems.MUTIARA_JIAO;
			case "selendang", "selendang_pengikat_naga" -> NagaItems.SELENDANG;
			default -> null;
		};
		if (item != null) {
			NagaItems.give(player, new ItemStack(item));
		}
	}

	// ================================================================== readers

	/** 0 wolf, 1 fox, 2 tamed Jiao, -1 anything else. */
	static int lineOf(LivingEntity beast) {
		if (beast instanceof NagaLiarEntity dragon) {
			return dragon.isTamed() ? LINE_JIAO : -1;
		}
		BeastData data = SpiritBeasts.data(beast);
		return data == null ? -1 : data.kind();
	}

	/** The beast's owner check. */
	static boolean ownedBy(LivingEntity beast, ServerPlayer player) {
		if (beast instanceof NagaLiarEntity dragon) {
			return dragon.isTamed() && player.getUUID().equals(dragon.ownerId());
		}
		BeastData data = SpiritBeasts.data(beast);
		return data != null && data.ownedBy(player);
	}

	/** The beast's spirit data (a Jiao's from its owner's record). */
	static @Nullable BeastData dataOf(LivingEntity beast, @Nullable ServerPlayer owner) {
		if (beast instanceof NagaLiarEntity dragon) {
			if (owner == null || !dragon.isTamed()) {
				return null;
			}
			TamedJiao.Record record = TamedJiao.get(owner);
			return record == null ? null : record.data(owner.getUUID());
		}
		return SpiritBeasts.data(beast);
	}

	/** Game time until which Luka Kesengsaraan lasts (0 when none). */
	static long lukaUntil(LivingEntity beast, @Nullable ServerPlayer owner) {
		if (beast instanceof NagaLiarEntity) {
			TamedJiao.Record record = owner == null ? null : TamedJiao.get(owner);
			return record == null ? 0L : record.lukaUntil();
		}
		Long until = beast.getAttached(LUKA);
		return until == null ? 0L : until;
	}

	static void setLuka(LivingEntity beast, @Nullable ServerPlayer owner, long until) {
		if (beast instanceof NagaLiarEntity) {
			TamedJiao.Record record = owner == null ? null : TamedJiao.get(owner);
			if (record != null) {
				TamedJiao.put(owner, record.withLuka(until));
			}
		} else {
			beast.setAttached(LUKA, until);
		}
	}

	/**
	 * Why {@code owner} cannot start the tribulation of {@code beast} now (null = it can). {@code test} skips XP, realm,
	 * luka, the open sky and the offering.
	 */
	static @Nullable Component problem(ServerPlayer owner, LivingEntity beast, boolean test) {
		NagaConfig config = Naga.config();
		if (!config.evolution) {
			return Component.translatable("kultivator.naga.evolusi.off");
		}
		int line = lineOf(beast);
		if (line < 0 || !ownedBy(beast, owner)) {
			return Component.translatable("kultivator.naga.evolusi.not_yours");
		}
		BeastData data = dataOf(beast, owner);
		if (data == null) {
			return Component.translatable("kultivator.naga.evolusi.not_yours");
		}
		if (data.tier() < BeastData.MAX_TIER) {
			return Component.translatable("kultivator.naga.evolusi.too_young", BeastData.MAX_TIER);
		}
		if (data.tier() >= BeastData.TOP_TIER) {
			return Component.translatable("kultivator.naga.evolusi.top");
		}
		if (BeastTribulation.running(beast)) {
			return Component.translatable("kultivator.naga.evolusi.running");
		}
		if (test) {
			return null;
		}
		int next = data.tier() + 1;
		if (data.xp() < BeastData.TIER_XP[next]) {
			return Component.translatable("kultivator.naga.evolusi.xp", Msg.number(data.xp()), Msg.number(BeastData.TIER_XP[next]));
		}
		Realm needed = BeastData.TIER_REALM[next];
		if (!Cultivators.get(owner).realm().atLeast(needed)) {
			return Component.translatable("kultivator.beasts.evolve.blocked", beast.getDisplayName(), next, needed.displayName());
		}
		long now = owner.level().getGameTime();
		long luka = lukaUntil(beast, owner);
		if (luka > now) {
			return Component.translatable("kultivator.naga.evolusi.luka", Msg.number((luka - now) / 1200.0D));
		}
		if (!(beast.level() instanceof ServerLevel level) || !level.canSeeSky(beast.blockPosition().above())) {
			return Component.translatable("kultivator.naga.evolusi.sky");
		}
		if (resolve(owner, offering(line, next), false) == null) {
			return Component.translatable("kultivator.naga.evolusi.offering", describe(offering(line, next)));
		}
		return null;
	}

	// ================================================================== offerings

	static List<Need> offering(int line, int tier) {
		List<Need> needs = new ArrayList<>();
		switch (tier) {
			case 4 -> {
				needs.add(new Need("kultivator:beast_core_king", 1, null, 0));
				needs.add(new Need("kultivator:batu_meteor_roh", 3, "kultivator:spirit_stone_high", 3));
			}
			case 5 -> {
				switch (line) {
					case LINE_FOX -> needs.add(new Need("kultivator:spirit_tail_feather", 3, null, 0));
					case LINE_JIAO -> needs.add(new Need("kultivator:mutiara_naga", 1, "kultivator:spirit_stone_supreme", 1));
					default -> needs.add(new Need("kultivator:inti_iblis_kuno", 1, "kultivator:spirit_stone_supreme", 1));
				}
			}
			default -> {
				needs.add(new Need("kultivator:persik_abadi", 1, "kultivator:spirit_stone_supreme", 2));
				needs.add(new Need("kultivator:tomb_essence", 3, null, 0));
			}
		}
		return needs;
	}

	/** True when {@code stack} is one of the items an offering may use (the player sneak-uses the beast holding it). */
	static boolean isOfferingItem(ItemStack stack, int line, int tier) {
		if (stack.isEmpty()) {
			return false;
		}
		for (Need need : offering(line, tier)) {
			Item primary = NagaItems.byId(need.id());
			Item fallback = need.fallback() == null ? null : NagaItems.byId(need.fallback());
			if (primary != null && stack.is(primary) || fallback != null && stack.is(fallback)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The stacks the offering takes from {@code player} (each need from its primary item, else its fallback), or null
	 * when something is missing. With {@code take} the stacks are removed from the inventory; the returned copies are
	 * what was paid (half of it comes back after a failure).
	 */
	static @Nullable List<ItemStack> resolve(ServerPlayer player, List<Need> needs, boolean take) {
		List<ItemStack> plan = new ArrayList<>();
		boolean fallbacks = Naga.config().fallbackItems;
		for (Need need : needs) {
			Item primary = NagaItems.byId(need.id());
			if (primary != null && count(player, primary) >= need.count()) {
				plan.add(new ItemStack(primary, need.count()));
				continue;
			}
			Item fallback = need.fallback() == null || !fallbacks ? null : NagaItems.byId(need.fallback());
			if (fallback != null && count(player, fallback) >= need.fallbackCount()) {
				plan.add(new ItemStack(fallback, need.fallbackCount()));
				continue;
			}
			return null;
		}
		if (take && !player.isCreative()) {
			for (ItemStack paid : plan) {
				remove(player, paid.getItem(), paid.getCount());
			}
		}
		return plan;
	}

	private static int count(ServerPlayer player, Item item) {
		int total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void remove(ServerPlayer player, Item item, int amount) {
		var inventory = player.getInventory();
		int left = amount;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inventory.setChanged();
	}

	/** "1× Inti Binatang Raja + 3× Batu Meteor Roh (or 3× High Spirit Stone)". */
	static Component describe(List<Need> needs) {
		MutableComponent text = Component.empty();
		boolean first = true;
		for (Need need : needs) {
			if (!first) {
				text.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
			}
			first = false;
			text.append(Component.literal(need.count() + "× ").append(name(need.id())).withStyle(ChatFormatting.WHITE));
			if (need.fallback() != null && Naga.config().fallbackItems) {
				text.append(Component.translatable("kultivator.naga.evolusi.or", need.fallbackCount() + "×", name(need.fallback()))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		return text;
	}

	private static Component name(String id) {
		Item item = NagaItems.byId(id);
		if (item != null) {
			return new ItemStack(item).getHoverName();
		}
		return Component.translatable("item." + id.replace(':', '.'));
	}

	// ================================================================== what an evolution tells

	/** The phase-3 evolution hook (a wolf or fox reached a new tier, any way). */
	static void onEvolved(Mob beast, BeastData before, BeastData after, @Nullable ServerPlayer owner, boolean celebrate) {
		if (owner == null || after.tier() <= before.tier()) {
			return;
		}
		if (celebrate) {
			DuniaEvents.fireBeastEvolved(owner, beast, after.tier(), after.line());
		}
		grantTierAdvancements(owner, after.tier());
		if (!celebrate) {
			return;
		}
		for (int tier = Math.max(4, before.tier() + 1); tier <= after.tier(); tier++) {
			String art = switch (tier) {
				case 4 -> after.isFox() ? "fox_fire" : "wolf_howl";
				case 6 -> after.isFox() ? "fox_passive" : "wolf_passive";
				default -> after.isFox() ? "fox_5" : "wolf_5";
			};
			Msg.chat(owner, Component.translatable("kultivator.naga.art." + art).withColor(SpiritBeasts.tierColor(after.kind(), tier)));
		}
	}

	/** The phase-3 ready hook: the beast waits for its tribulation. */
	static void onReady(Mob beast, BeastData data, ServerPlayer owner) {
		tellReady(owner, beast.getDisplayName(), data.kind(), data.tier() + 1);
	}

	/** Tells the owner how to start the tribulation to {@code tier}. */
	static void tellReady(ServerPlayer owner, Component name, int line, int tier) {
		if (!Naga.config().evolution) {
			return;
		}
		Msg.chat(owner, Component.translatable("kultivator.naga.evolusi.ready", name, tier).withStyle(ChatFormatting.LIGHT_PURPLE));
		Msg.chat(owner, Component.translatable("kultivator.naga.evolusi.ready.how", describe(offering(line, tier))).withStyle(ChatFormatting.GRAY));
	}

	static void grantTierAdvancements(ServerPlayer owner, int tier) {
		if (tier >= 4) {
			Advancements.grant(owner, "dunia/beast_tier_4");
		}
		if (tier >= BeastData.TOP_TIER) {
			Advancements.grant(owner, "dunia/beast_tier_6");
		}
	}
}
