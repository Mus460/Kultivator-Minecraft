package dev.kultivator.duel.session;

import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stakes of formal duels (DESIGN 35.2.4): the checks when a challenge is made and accepted, taking the stakes into the
 * {@link DuelEscrow} when INTRO begins, and handing them on at the end. Spirit stones: both sides the same grade and count
 * (an NPC from its profile). Jade slips: one slip each, the winner takes the other's. Disciples never stake items, a
 * disciple always "stakes" nothing or Honour. An NPC's own stake is virtual until it loses (then it is made); what an
 * NPC wins it carries ({@link DuelistRegistry#carry}) and drops when it is later beaten for real.
 */
public final class DuelStakes {

	/** A reason the stake of this challenge is not possible, or null. Called when a challenge is made. */
	public static @Nullable Component validate(ServerPlayer challenger, LivingEntity target, int pair, DuelRuleSet rules) {
		DuelRuleSet.StakeKind kind = rules.stake();
		if (kind == DuelRuleSet.StakeKind.TANPA) {
			return null;
		}
		DuelConfig config = DuelConfig.get();
		if (kind == DuelRuleSet.StakeKind.KEHORMATAN) {
			return config.honour ? null : Component.translatable("kultivator.duel.reason.stake_off");
		}
		if (!config.allowStakes || pair != DuelIds.PAIR_PVP && pair != DuelIds.PAIR_NPC) {
			return Component.translatable("kultivator.duel.reason.stake_pair");
		}
		if (kind == DuelRuleSet.StakeKind.BATU_ROH) {
			if (rules.stakeCount() > config.maxStoneCount) {
				return Component.translatable("kultivator.duel.reason.stake_count", config.maxStoneCount);
			}
			if (count(challenger, HartaItems.stoneItem(rules.stakeGrade())) < rules.stakeCount()) {
				return Component.translatable("kultivator.duel.reason.stake_missing");
			}
			if (!(target instanceof ServerPlayer)) {
				DuelistProfile profile = DuelistRegistry.profile(target);
				if (profile == null || !profile.stakesStones() || rules.stakeGrade() > profile.stoneGrade() || rules.stakeCount() > profile.stoneCount()) {
					return Component.translatable("kultivator.duel.reason.stake_npc");
				}
			}
			return null;
		}
		// GULUNGAN
		if (!config.allowSlipStakes) {
			return Component.translatable("kultivator.duel.reason.stake_off");
		}
		String spec = rules.slipSpec();
		if (spec == null || JadeSlips.stack(spec).isEmpty() || findSlip(challenger, spec) < 0) {
			return Component.translatable("kultivator.duel.reason.stake_slip");
		}
		if (!(target instanceof ServerPlayer)) {
			DuelistProfile profile = DuelistRegistry.profile(target);
			if (profile == null || profile.slipSpec() == null || JadeSlips.stack(profile.slipSpec()).isEmpty()) {
				return Component.translatable("kultivator.duel.reason.stake_npc");
			}
		}
		return null;
	}

	/** The slip a player puts up when accepting a slip duel: the one in hand, else the first in the inventory; null none. */
	public static @Nullable String ownSlip(ServerPlayer player) {
		String held = JadeSlips.specOf(player.getMainHandItem());
		if (held != null && !JadeSlips.stack(held).isEmpty()) {
			return held;
		}
		held = JadeSlips.specOf(player.getOffhandItem());
		if (held != null && !JadeSlips.stack(held).isEmpty()) {
			return held;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			String spec = JadeSlips.specOf(inventory.getItem(i));
			if (spec != null && !JadeSlips.stack(spec).isEmpty()) {
				return spec;
			}
		}
		return null;
	}

	/** True when {@code player} can put up their side of {@code rules} ({@code slip} their own slip for GULUNGAN). */
	public static boolean has(ServerPlayer player, DuelRuleSet rules, @Nullable String slip) {
		return switch (rules.stake()) {
			case BATU_ROH -> count(player, HartaItems.stoneItem(rules.stakeGrade())) >= rules.stakeCount();
			case GULUNGAN -> slip != null && findSlip(player, slip) >= 0;
			default -> true;
		};
	}

	/** The technique name of a slip spec ("" when none). */
	public static Component slipName(@Nullable String spec) {
		if (spec == null) {
			return Component.empty();
		}
		TechniqueSpec technique = TechniqueRegistry.byId(spec);
		return technique == null ? Component.literal(spec) : technique.displayName();
	}

	/** At INTRO: both players' stakes go into the escrow. A missing stake returns what was taken; the reason or null. */
	static @Nullable Component collect(DuelSession session) {
		MinecraftServer server = session.level.getServer();
		DuelEscrow escrow = DuelEscrow.get(server);
		DuelRuleSet rules = session.rules;
		LivingEntity[] sides = {session.a, session.b};
		String[] slips = {session.slipA, session.slipB};
		for (int i = 0; i < 2; i++) {
			if (sides[i] instanceof ServerPlayer player && !has(player, rules, slips[i])) {
				return Component.translatable("kultivator.duel.reason.stake_lost", player.getDisplayName());
			}
		}
		for (int i = 0; i < 2; i++) {
			if (!(sides[i] instanceof ServerPlayer player)) {
				continue;
			}
			List<ItemStack> taken = take(player, rules, slips[i]);
			for (ItemStack stack : taken) {
				escrow.deposit(session.key, player.getUUID(), stack);
			}
		}
		session.stakesTaken = true;
		return null;
	}

	/** The winner takes it all; the stake line for the result card. */
	static Component award(DuelSession session, LivingEntity winner, LivingEntity loser) {
		MinecraftServer server = session.level.getServer();
		DuelEscrow escrow = DuelEscrow.get(server);
		DuelRuleSet rules = session.rules;
		if (!rules.stake().items()) {
			return line(rules, session);
		}
		List<DuelEscrow.Deposit> deposits = escrow.take(session.key);
		List<ItemStack> prize = new ArrayList<>();
		for (DuelEscrow.Deposit deposit : deposits) {
			prize.add(deposit.stack());
		}
		if (!(loser instanceof ServerPlayer)) {
			// The NPC's virtual stake is made now.
			DuelistProfile profile = DuelistRegistry.profile(loser);
			if (rules.stake() == DuelRuleSet.StakeKind.BATU_ROH) {
				prize.add(new ItemStack(HartaItems.stoneItem(rules.stakeGrade()), rules.stakeCount()));
			} else if (profile != null && profile.slipSpec() != null) {
				ItemStack slip = JadeSlips.stack(profile.slipSpec());
				if (!slip.isEmpty()) {
					prize.add(slip);
				}
			}
		}
		if (winner instanceof ServerPlayer player) {
			for (ItemStack stack : prize) {
				escrow.give(server, player.getUUID(), stack);
			}
		} else {
			for (ItemStack stack : prize) {
				// The NPC pockets the player's stake (its own virtual one never existed).
				DuelistRegistry.carry(winner, stack);
			}
		}
		return Component.translatable("kultivator.duel.msg.stake_won", winner.getDisplayName(), line(rules, session));
	}

	/** Draw or cancel: everything goes back. */
	static Component refund(DuelSession session) {
		MinecraftServer server = session.level.getServer();
		DuelEscrow.get(server).returnAll(server, session.key);
		if (!session.rules.stake().items() || !session.stakesTaken) {
			return Component.empty();
		}
		return Component.translatable("kultivator.duel.msg.stake_returned");
	}

	/** "10 batu roh tengah" / "slip A ⇄ slip B" / "Kehormatan". */
	static Component line(DuelRuleSet rules, @Nullable DuelSession session) {
		return switch (rules.stake()) {
			case BATU_ROH -> Component.translatable("kultivator.duel.stake.stones", rules.stakeCount(),
					new ItemStack(HartaItems.stoneItem(rules.stakeGrade())).getHoverName());
			case GULUNGAN -> {
				String mine = session != null ? session.slipA : rules.slipSpec();
				MutableComponent text = session != null
						? Component.translatable("kultivator.duel.stake.slips", slipName(mine), slipName(session.slipB))
						: Component.translatable("kultivator.duel.stake.slip_offer", slipName(mine));
				yield text;
			}
			case KEHORMATAN -> Component.translatable("kultivator.duel.stake.honour");
			default -> Component.empty();
		};
	}

	// ================================================================ inventory helpers

	private static int count(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int findSlip(ServerPlayer player, String spec) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (spec.equals(JadeSlips.specOf(inventory.getItem(i)))) {
				return i;
			}
		}
		return -1;
	}

	/** Takes the player's side out of the inventory (the check ran just before). */
	private static List<ItemStack> take(ServerPlayer player, DuelRuleSet rules, @Nullable String slip) {
		List<ItemStack> taken = new ArrayList<>();
		Inventory inventory = player.getInventory();
		if (rules.stake() == DuelRuleSet.StakeKind.BATU_ROH) {
			Item stone = HartaItems.stoneItem(rules.stakeGrade());
			int left = rules.stakeCount();
			for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack.is(stone)) {
					int n = Math.min(left, stack.getCount());
					taken.add(stack.split(n));
					left -= n;
				}
			}
		} else if (rules.stake() == DuelRuleSet.StakeKind.GULUNGAN && slip != null) {
			int slot = findSlip(player, slip);
			if (slot >= 0) {
				taken.add(inventory.getItem(slot).split(1));
			}
		}
		inventory.setChanged();
		return taken;
	}

	private DuelStakes() {
	}
}
