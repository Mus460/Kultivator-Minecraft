package dev.kultivator.client.goals;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.goals.Prep;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Persiapan forecast (DESIGN 19.3.10) as lines for the Status page fold-out and the breakthrough talisman's tooltip.
 * It never copies a formula: strikes and shares come from {@code Tribulation.strikeCount/strikePct}, the strike factor and
 * the quality bonus from the server's {@code sessionExtra.prep}, the Foundation chance from {@code Breakthrough.chance}.
 * <p>
 * Sumber: game design (UX).
 */
public final class PrepView {

	private static final String P = "kultivator.goals.prep.";
	private static final Identifier WARD_TALISMAN = Kultivator.id("tribulation_ward_talisman");
	private static final double FULL_HEALTH = 0.9D;
	private static final double TOXIC = 80.0D;

	/** One line: text, ink colour on the parchment, heading or not. */
	public record Row(Component text, int color, boolean heading) {
	}

	/** The breakthrough the player is heading to (the next one after the current realm or layer), or null at the summit. */
	public static @Nullable Rank target(CultivationData data) {
		Rank rank = data.rank();
		Rank peak = new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
		return peak.isMax() ? null : peak.next();
	}

	/** Every line of the fold-out. */
	public static List<Row> rows(CultivationData data) {
		List<Row> rows = new ArrayList<>();
		Rank target = target(data);
		if (target == null) {
			rows.add(new Row(Component.translatable(P + "summit"), Palette.INK_SOFT, false));
			return rows;
		}
		Rank rank = data.rank();
		boolean ready = data.barFull() && rank.nextIsBreakthrough();
		rows.add(new Row(Component.translatable(P + "title", target.displayName()), Palette.SEAL_DARK, true));
		if (!ready) {
			rows.add(new Row(Component.translatable(P + "not_ready"), Palette.INK_FAINT, false));
		}
		CompoundTag prep = data.sessionExtra().getCompoundOrEmpty(Prep.KEY);
		if (rank.realm().isMortal()) {
			rows.add(new Row(Component.translatable(P + "awakening"), Palette.INK, false));
			if (!data.rootTested()) {
				rows.add(new Row(Component.translatable(P + "root_first"), Palette.DANGER, false));
			}
			return rows;
		}
		if (target.realm() == Realm.FOUNDATION) {
			chanceRows(rows, data, prep);
		} else {
			tribulationRows(rows, data, target, prep);
		}
		checklist(rows, data, target, prep);
		return rows;
	}

	/** The place bonus the Foundation breakthrough would read now: the server's value, else the running meditation's. */
	private static double siteBonus(CultivationData data, CompoundTag prep) {
		if (prep.contains("site")) {
			return prep.getDoubleOr("site", 0.0D);
		}
		return data.meditating() ? (data.siteMultiplier() > 1.01D ? Prep.SITE_TIP : 0.0D) + Prep.FORMATION_TIP * data.formationTier() : 0.0D;
	}

	/** The share of max health one normal strike takes: strike share × the strike factor × the server's damage scale. */
	private static double strikeShare(CultivationData data, Rank strength, CompoundTag prep) {
		double factor = prep.contains("f") ? prep.getDoubleOr("f", 1.0D) : data.nextTribulationFactor();
		return Tribulation.strikePct(strength) * factor * Math.max(0.0D, prep.getDoubleOr("scale", 1.0D));
	}

	private static void chanceRows(List<Row> rows, CultivationData data, CompoundTag prep) {
		double site = siteBonus(data, prep);
		double total = Breakthrough.chance(data, site);
		double[] parts = Breakthrough.chanceParts(data);
		double root = prep.contains("root") ? prep.getDoubleOr("root", 0.0D) : parts[1];
		double pill = prep.contains("pill") ? prep.getDoubleOr("pill", 0.0D) : parts[2];
		double stored = prep.contains("stored") ? prep.getDoubleOr("stored", 0.0D) : parts[3];
		rows.add(new Row(Component.translatable(P + "chance", percent(total)), Palette.INK, false));
		rows.add(new Row(Component.translatable(P + "chance_parts", percent(parts[0]), signed(root), signed(pill), signed(stored), signed(site)),
				Palette.INK_SOFT, false));
		rows.add(new Row(Component.translatable(P + "chance_tip", percent(Prep.SITE_TIP), percent(Prep.FORMATION_TIP)), Palette.INK_FAINT, false));
	}

	private static void tribulationRows(List<Row> rows, CultivationData data, Rank target, CompoundTag prep) {
		Rank strength = Prep.strength(target);
		int strikes = Tribulation.strikeCount(strength);
		double factor = prep.contains("f") ? prep.getDoubleOr("f", 1.0D) : data.nextTribulationFactor();
		double share = strikeShare(data, strength, prep);
		double total = share * (strikes + 1);
		double max = maxHealth();
		rows.add(new Row(Component.translatable(P + "strikes", strikes, percent(share), percent(share * 2.0D), percent(total)), Palette.INK, false));
		rows.add(new Row(Component.translatable(P + "per_strike", hearts(share * max), hearts(share * 2.0D * max), decimal(factor)), Palette.INK_SOFT,
				false));
		double heal = total * max - (max - 1.0D);
		if (heal <= 0.0D) {
			rows.add(new Row(Component.translatable(P + "no_heal"), Palette.JADE_DARK, false));
		} else {
			rows.add(new Row(Component.translatable(P + "heal", hearts(heal), decimal(heal / max)), Palette.WARN, false));
		}
		if (prep.contains("q")) {
			double q = prep.getDoubleOr("q", 0.0D);
			double trials = prep.getDoubleOr("trials", 0.0D);
			double stones = prep.getDoubleOr("stones", 0.0D);
			double guards = prep.getDoubleOr("guards_q", 0.0D);
			double other = q - trials - stones - guards;
			MutableComponent line = Component.translatable(P + "quality", signed2(q), signed2(trials), signed2(stones), signed2(guards));
			if (Math.abs(other) > 0.005D) {
				line.append(Component.translatable(P + "quality_other", signed2(other)));
			}
			rows.add(new Row(line, q > 0.0D ? Palette.GOLD_DARK : Palette.INK, false));
		} else {
			rows.add(new Row(Component.translatable(P + "quality_wait"), Palette.INK_FAINT, false));
		}
		CompoundTag cfg = GoalsClient.cfgTag();
		Realm realm = target.realm();
		if (cfg.getBooleanOr("hd", true) && target.layer() == 0 && (realm == Realm.NASCENT_SOUL || realm == Realm.DAO_UNION || realm == Realm.TRUE_IMMORTAL)
				&& !data.trialPassed(realm)) {
			rows.add(new Row(Component.translatable(P + "heart_demon"), Palette.SEAL_DARK, false));
		}
		if (cfg.getBooleanOr("dp", true)) {
			int waves = (strikes + 1) / 3;
			int mobs = 1 + strength.realm().ordinal() / 2;
			rows.add(new Row(Component.translatable(strength.realm().atLeast(Realm.NASCENT_SOUL) ? P + "protectors_elite" : P + "protectors", waves, mobs),
					Palette.INK_SOFT, false));
		}
		if (cfg.getBooleanOr("tele", true) && strength.realm().atLeast(Realm.DEITY_TRANSFORMATION)) {
			rows.add(new Row(Component.translatable(P + "rings"), Palette.INK_SOFT, false));
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null && minecraft.level.dimension().equals(Level.NETHER)) {
			rows.add(new Row(Component.translatable(P + "nether"), Palette.WARN, false));
		} else if (minecraft.level != null && minecraft.level.dimension().equals(Level.END)) {
			rows.add(new Row(Component.translatable(P + "end"), Palette.WARN, false));
		}
	}

	private static void checklist(List<Row> rows, CultivationData data, Rank target, CompoundTag prep) {
		rows.add(new Row(Component.translatable(P + "check"), Palette.SEAL_DARK, true));
		LocalPlayer player = Minecraft.getInstance().player;
		boolean tribulation = target.realm().atLeast(Realm.GOLDEN_CORE);
		if (player != null && tribulation) {
			check(rows, player.getHealth() >= player.getMaxHealth() * FULL_HEALTH, Component.translatable(P + "c_health",
					Math.round(player.getHealth() / Math.max(1.0F, player.getMaxHealth()) * 100.0F)));
			int healing = count(player, Items.GOLDEN_APPLE) + count(player, Items.ENCHANTED_GOLDEN_APPLE) + count(player, AlchemyItems.HEALING_PILL);
			check(rows, healing > 0, Component.translatable(P + "c_healing", healing));
			int wards = countId(player, WARD_TALISMAN);
			check(rows, data.nextTribulationFactor() < 1.0D || wards > 0, Component.translatable(P + "c_warding", wards));
			check(rows, prep.getIntOr("allies", 0) > 0, Component.translatable(P + "c_allies", prep.getIntOr("allies", 0)));
		}
		check(rows, data.toxicity() < TOXIC, Component.translatable(P + "c_toxicity", Math.round(data.toxicity())));
		check(rows, !data.deviating(), Component.translatable(P + "c_deviation"));
		if (data.hardcore()) {
			rows.add(new Row(Component.translatable(P + "c_hardcore"), Palette.DANGER, false));
		}
	}

	private static void check(List<Row> rows, boolean ok, Component text) {
		MutableComponent line = Component.literal(ok ? "✓ " : "✗ ").withColor(ok ? 0x2E8963 : 0xB3281D);
		rows.add(new Row(line.append(text), ok ? Palette.INK_SOFT : Palette.INK, false));
	}

	/** A short summary for the breakthrough talisman's tooltip. */
	public static List<Component> tooltip(CultivationData data) {
		List<Component> lines = new ArrayList<>();
		Rank target = target(data);
		Rank rank = data.rank();
		if (target == null || rank.realm().isMortal()) {
			return lines;
		}
		CompoundTag prep = data.sessionExtra().getCompoundOrEmpty(Prep.KEY);
		if (target.realm() == Realm.FOUNDATION) {
			lines.add(Component.translatable(P + "chance", percent(Breakthrough.chance(data, siteBonus(data, prep)))).withStyle(ChatFormatting.YELLOW));
		} else {
			Rank strength = Prep.strength(target);
			int strikes = Tribulation.strikeCount(strength);
			double share = strikeShare(data, strength, prep);
			lines.add(Component.translatable(P + "strikes", strikes, percent(share), percent(share * 2.0D), percent(share * (strikes + 1)))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			if (prep.contains("q")) {
				lines.add(Component.translatable(P + "quality_short", signed2(prep.getDoubleOr("q", 0.0D))).withStyle(ChatFormatting.GOLD));
			}
			double max = maxHealth();
			double heal = share * (strikes + 1) * max - (max - 1.0D);
			if (heal > 0.0D) {
				lines.add(Component.translatable(P + "heal", hearts(heal), decimal(heal / max)).withStyle(ChatFormatting.RED));
			}
		}
		lines.add(Component.translatable(P + "more").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static double maxHealth() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? 20.0D : Math.max(1.0D, player.getMaxHealth());
	}

	private static int count(LocalPlayer player, Item item) {
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

	private static int countId(LocalPlayer player, Identifier id) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	static String percent(double share) {
		return Math.round(share * 100.0D) + "%";
	}

	private static String signed(double share) {
		long value = Math.round(share * 100.0D);
		return (value >= 0 ? "+" : "") + value + "%";
	}

	private static String signed2(double value) {
		return String.format(Locale.ROOT, "%+.2f", value);
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** Health points as hearts with one decimal. */
	private static String hearts(double health) {
		return String.format(Locale.ROOT, "%.1f", health / 2.0D);
	}

	private PrepView() {
	}
}
