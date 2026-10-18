package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Misi Pembangunan (DESIGN 21.14, items 1 and 2), the user's "missions to gather resources". They are optional helpers:
 * the sect builds completely without them.
 * <ul>
 * <li><b>Misi Tingkat</b>: every bill line of the tier being built is a standing mission ("Kumpulkan 1570 Batu — Formasi
 * Agung"). It completes by itself when the Gudang covers the line and pays pool PK once per (template, tier, line):
 * {@code min(30, ceil(units x rate))}, exact items 5 each (lodestone 20). Once the tier is covered the next tier shows as
 * "Persiapan".</li>
 * <li><b>Tawaran harian</b> (rolled at sunrise): {@code 3 + floor((S-1)/3)} offers weighted by the shortfall: Setoran (60%,
 * deposits of a category after accepting, by the owner or sect mates; pool PK {@code ceil(amount x 2 x rate)} at most 60,
 * plus Berkah Pengrajin: 20% bonus units) and Ekspedisi Berkah (40%, gathering trips in that category yield x1.5 today).</li>
 * </ul>
 * Build missions pay at most 150 pool PK a day (apart from the Kantor Misi's 300); a tier line over the cap waits for the
 * next day. No renown is ever awarded here (DESIGN 20.3.1: renown comes only from disciples).
 * <p>
 * Source: delivery missions paid in merit, <i>A Will Eternal</i> ch. 9 and 27 (the Missions Office pays merit points for
 * delivered spirit plants) and <i>Martial Peak</i> (the Contribution Hall). Numbers are game design.
 */
public final class BuildMissions {

	public static final int LINE_PK_CAP = 30;
	public static final int EXACT_PK = 5;
	public static final int LODESTONE_PK = 20;
	public static final int OFFER_PK_CAP = 60;
	public static final int DAILY_PK_CAP = 150;
	public static final double DELIVER_SHARE = 0.6D;
	public static final double BONUS_SHARE = 0.2D;
	public static final double BLESS_YIELD = 1.5D;
	public static final int MIN_AMOUNT = 16;
	public static final int MAX_AMOUNT = 1024;
	/** PK rate per unit by category (DESIGN 21.14). */
	public static final double[] RATE = {0.02D, 0.02D, 0.03D, 0.05D, 0.05D, 0.05D, 0.05D, 0.1D, 0.25D};

	public static final String KIND_DELIVER = "deliver";
	public static final String KIND_BLESS = "bless";

	/** A standing tier mission line. */
	public record Line(String key, @Nullable Bahan cat, @Nullable Item item, long amount, long have, boolean covered, boolean paid, int pk) {
	}

	// ================================================================== tier lines

	/** The lines of {@code tier}; the next tier's ("Persiapan") count only what the tier before it leaves in the Gudang. */
	public static List<Line> lines(UUID owner, int tier) {
		EngineApi engine = EngineApi.get();
		int building = buildingTier(owner);
		Bill before = tier == building + 1 && building > 0 ? engine.remainingBill(owner, building) : Bill.EMPTY;
		return lines(owner, tier, before);
	}

	private static List<Line> lines(UUID owner, int tier, Bill before) {
		List<Line> lines = new ArrayList<>();
		EngineApi engine = EngineApi.get();
		int template = engine.template(owner);
		if (template <= 0 || tier < 1 || tier > SectTemplates.TIERS) {
			return lines;
		}
		SiteView site = engine.site(owner);
		Glaze glaze = site == null ? Glaze.TINTA : site.glaze();
		Bill full = engine.tierBill(template, tier, glaze);
		Bill remaining = engine.remainingBill(owner, tier);
		Bill have = Ledger.INSTANCE.balance(owner).minus(before);
		if (full.isEmpty()) {
			full = remaining;
		}
		for (Bahan cat : Bahan.values()) {
			long amount = full.centi(cat);
			if (amount <= 0L) {
				continue;
			}
			long rest = Math.min(amount, remaining.centi(cat));
			long got = Math.min(amount, amount - rest + have.centi(cat));
			String key = "line:" + template + ":" + tier + ":" + cat.id();
			int pk = (int) Math.min(LINE_PK_CAP, Math.ceil(Fmt.ceilUnits(amount) * RATE[cat.ordinal()]));
			lines.add(new Line(key, cat, null, amount, got, have.centi(cat) >= rest, FlowData.awarded(owner, key), Math.max(1, pk)));
		}
		for (Map.Entry<Item, Integer> entry : full.exact().entrySet()) {
			Item item = entry.getKey();
			int amount = entry.getValue();
			int rest = Math.min(amount, remaining.exact(item));
			int got = Math.min(amount, amount - rest + have.exact(item));
			String key = "line:" + template + ":" + tier + ":" + Ledger.id(item);
			int each = item == Items.LODESTONE ? LODESTONE_PK : EXACT_PK;
			int pk = Math.min(LINE_PK_CAP, each * amount);
			lines.add(new Line(key, null, item, amount, got, have.exact(item) >= rest, FlowData.awarded(owner, key), pk));
		}
		return lines;
	}

	/** True when every line of {@code tier} is covered by the Gudang. */
	public static boolean covered(UUID owner, int tier) {
		for (Line line : lines(owner, tier)) {
			if (!line.covered()) {
				return false;
			}
		}
		return true;
	}

	/** The building tier (completed + 1), or 0 when the template is complete or missing. */
	public static int buildingTier(UUID owner) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0) {
			return 0;
		}
		int tier = engine.tier(owner) + 1;
		return tier > SectTemplates.TIERS ? 0 : tier;
	}

	/** Once a second per online owner: pays covered lines, rolls the day's offers. */
	static void tick(ServerPlayer owner) {
		UUID id = owner.getUUID();
		if (EngineApi.get().template(id) <= 0 || isTest(id)) {
			return;
		}
		ensureOffers(id, owner.getRandom());
		int tier = buildingTier(id);
		if (tier <= 0) {
			return;
		}
		for (int each = tier; each <= Math.min(SectTemplates.TIERS, tier + 1); each++) {
			for (Line line : lines(id, each)) {
				if (line.covered() && !line.paid()) {
					if (!pay(id, line.pk())) {
						return;
					}
					FlowData.award(id, line.key());
					Component what = line.cat() != null ? Fmt.line(line.cat(), line.amount()) : Fmt.line(line.item(), (int) line.amount());
					Msg.chat(owner, Component.translatable("kultivator.sectbuild.mission.line_done", what, SectTemplates.tierName(each), line.pk())
							.withStyle(ChatFormatting.AQUA));
				}
			}
			if (!covered(id, each)) {
				break;
			}
		}
	}

	private static boolean isTest(UUID owner) {
		SiteView site = EngineApi.get().site(owner);
		return site != null && site.test();
	}

	// ================================================================== the daily cap

	private static CompoundTag missions(UUID owner) {
		return FlowData.sub(FlowData.of(owner), "missions");
	}

	/** PK paid by build missions today. */
	public static int pkToday(UUID owner) {
		CompoundTag missions = missions(owner);
		return missions.getLongOr("pk_day", Long.MIN_VALUE) == FlowData.day() ? missions.getIntOr("pk_today", 0) : 0;
	}

	/** Pays {@code pk} into the hall pool within the daily cap; false (nothing paid) when it would pass the cap. */
	private static boolean pay(UUID owner, int pk) {
		int today = pkToday(owner);
		if (today + pk > DAILY_PK_CAP) {
			return false;
		}
		CompoundTag missions = missions(owner);
		missions.putLong("pk_day", FlowData.day());
		missions.putInt("pk_today", today + pk);
		Halls.addPool(owner, pk);
		FlowData.dirty();
		return true;
	}

	// ================================================================== daily offers

	/** Rolls today's offers when the day changed (missed days never stack). */
	public static void ensureOffers(UUID owner, RandomSource random) {
		CompoundTag missions = missions(owner);
		if (missions.getLongOr("day", Long.MIN_VALUE) == FlowData.day() && missions.contains("offers")) {
			return;
		}
		roll(owner, random);
	}

	/** Rolls a fresh set of offers ({@code uji sekte misi} too). */
	public static void roll(UUID owner, RandomSource random) {
		CompoundTag missions = missions(owner);
		missions.putLong("day", FlowData.day());
		ListTag offers = new ListTag();
		int template = EngineApi.get().template(owner);
		int tier = buildingTier(owner);
		if (template > 0 && tier > 0) {
			long[] shortfall = shortfall(owner, tier);
			long total = 0L;
			for (long each : shortfall) {
				total += each;
			}
			if (total <= 0L && tier < SectTemplates.TIERS) {
				shortfall = shortfall(owner, tier + 1);
				for (long each : shortfall) {
					total += each;
				}
			}
			int count = 3 + (template - 1) / 3;
			for (int i = 0; i < count && total > 0L; i++) {
				long pick = (long) (random.nextDouble() * total);
				Bahan cat = Bahan.BATU;
				for (Bahan each : Bahan.values()) {
					pick -= shortfall[each.ordinal()];
					if (pick < 0L) {
						cat = each;
						break;
					}
				}
				CompoundTag offer = new CompoundTag();
				offer.putString("cat", cat.id());
				if (random.nextDouble() < DELIVER_SHARE) {
					double units = Fmt.ceilUnits(shortfall[cat.ordinal()]) * (0.20D + random.nextDouble() * 0.15D);
					long amount = Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, Math.round(units / 16.0D) * 16L));
					offer.putString("kind", KIND_DELIVER);
					offer.putLong("amount", amount);
					offer.putInt("pk", (int) Math.min(OFFER_PK_CAP, Math.ceil(amount * 2.0D * RATE[cat.ordinal()])));
					offer.putLong("bonus", Math.round(amount * BONUS_SHARE));
				} else {
					offer.putString("kind", KIND_BLESS);
				}
				offer.putBoolean("accepted", false);
				offer.putLong("progress", 0L);
				offer.putBoolean("done", false);
				offers.add(offer);
			}
		}
		missions.put("offers", offers);
		FlowData.dirty();
	}

	/** What the Gudang still lacks for {@code tier}, per category (centi). */
	private static long[] shortfall(UUID owner, int tier) {
		Bill need = EngineApi.get().remainingBill(owner, tier).minus(Ledger.INSTANCE.balance(owner));
		return need.centi();
	}

	public static ListTag offers(UUID owner) {
		return missions(owner).getListOrEmpty("offers");
	}

	/** Accepts delivery offer {@code index}: deposits of its category count from now on. */
	public static void accept(ServerPlayer player, UUID owner, int index) {
		ListTag offers = offers(owner);
		if (index < 0 || index >= offers.size() || !(offers.get(index) instanceof CompoundTag offer)) {
			return;
		}
		if (!KIND_DELIVER.equals(offer.getStringOr("kind", "")) || offer.getBooleanOr("accepted", false)) {
			return;
		}
		offer.putBoolean("accepted", true);
		offer.putLong("progress", 0L);
		CompoundTag out = missions(owner).getCompoundOrEmpty("surplus_out");
		if (out.getLongOr("day", Long.MIN_VALUE) == FlowData.day()) {
			offer.putLong("owed", out.getLongOr(offer.getStringOr("cat", ""), 0L));
		}
		FlowData.dirty();
		Bahan cat = Bahan.byId(offer.getStringOr("cat", ""));
		if (cat != null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.mission.accepted", offer.getLongOr("amount", 0L), cat.displayName())
					.withStyle(ChatFormatting.AQUA));
		}
	}

	/**
	 * Surplus Batu, Tanah or Kayu taken out as items: each accepted, unfinished Setoran offer of that category owes it back
	 * first, so cycling the surplus out and in again never completes an offer.
	 */
	static void onWithdraw(UUID owner, Bahan cat, long centi) {
		// Today's surplus taken out: an offer accepted later today owes it back too.
		CompoundTag out = FlowData.sub(missions(owner), "surplus_out");
		if (out.getLongOr("day", Long.MIN_VALUE) != FlowData.day()) {
			for (String key : new java.util.ArrayList<>(out.keySet())) {
				out.remove(key);
			}
			out.putLong("day", FlowData.day());
		}
		out.putLong(cat.id(), out.getLongOr(cat.id(), 0L) + centi);
		FlowData.dirty();
		for (Tag tag : offers(owner)) {
			if (tag instanceof CompoundTag offer && KIND_DELIVER.equals(offer.getStringOr("kind", "")) && offer.getBooleanOr("accepted", false)
					&& !offer.getBooleanOr("done", false) && cat.id().equals(offer.getStringOr("cat", ""))) {
				offer.putLong("owed", offer.getLongOr("owed", 0L) + centi);
				FlowData.dirty();
			}
		}
	}

	/** A deposit of {@code centi} of {@code cat} into {@code owner}'s Gudang (by the owner or a sect mate). */
	static void onDeposit(UUID owner, Bahan cat, long centi) {
		for (Tag tag : offers(owner)) {
			if (!(tag instanceof CompoundTag offer) || !KIND_DELIVER.equals(offer.getStringOr("kind", "")) || !offer.getBooleanOr("accepted", false)
					|| offer.getBooleanOr("done", false) || !cat.id().equals(offer.getStringOr("cat", ""))) {
				continue;
			}
			long owed = offer.getLongOr("owed", 0L);
			long repaid = Math.min(owed, centi);
			offer.putLong("owed", owed - repaid);
			long counted = centi - repaid;
			if (counted <= 0L) {
				FlowData.dirty();
				continue;
			}
			long amount = offer.getLongOr("amount", 0L) * Bahan.CENTI;
			long progress = Math.min(amount, offer.getLongOr("progress", 0L) + counted);
			offer.putLong("progress", progress);
			if (progress >= amount) {
				offer.putBoolean("done", true);
				int pk = Math.max(0, Math.min(offer.getIntOr("pk", 0), DAILY_PK_CAP - pkToday(owner)));
				if (pk > 0) {
					pay(owner, pk);
				}
				long bonus = offer.getLongOr("bonus", 0L) * Bahan.CENTI;
				Ledger.INSTANCE.add(owner, cat, bonus);
				ServerPlayer master = Ceremonies.online(owner);
				if (master != null) {
					Msg.chat(master, Component.translatable("kultivator.sectbuild.mission.offer_done", offer.getLongOr("amount", 0L), cat.displayName(), pk,
							Fmt.units(bonus)).withStyle(ChatFormatting.GOLD));
				}
			}
			FlowData.dirty();
		}
	}

	/** The gathering yield factor of a category today (x1.5 under an Ekspedisi Berkah offer). */
	public static double blessing(UUID owner, Bahan cat) {
		CompoundTag missions = missions(owner);
		if (missions.getLongOr("day", Long.MIN_VALUE) != FlowData.day()) {
			return 1.0D;
		}
		for (Tag tag : offers(owner)) {
			if (tag instanceof CompoundTag offer && KIND_BLESS.equals(offer.getStringOr("kind", "")) && cat.id().equals(offer.getStringOr("cat", ""))) {
				return BLESS_YIELD;
			}
		}
		return 1.0D;
	}

	// ================================================================== for the Misi panel

	/** The Misi panel's part: lines of the building tier (and "Persiapan"), the offers, today's PK and the cap. */
	static CompoundTag info(UUID owner) {
		CompoundTag tag = new CompoundTag();
		int tier = buildingTier(owner);
		tag.putInt("tier", tier);
		tag.putBoolean("complete", EngineApi.get().template(owner) > 0 && tier == 0);
		ListTag lines = new ListTag();
		if (tier > 0) {
			boolean covered = writeLines(owner, tier, false, lines);
			if (covered && tier < SectTemplates.TIERS) {
				writeLines(owner, tier + 1, true, lines);
			}
		}
		tag.put("lines", lines);
		tag.put("offers", offers(owner).copy());
		tag.putInt("pk_today", pkToday(owner));
		tag.putInt("pk_cap", DAILY_PK_CAP);
		return tag;
	}

	private static boolean writeLines(UUID owner, int tier, boolean prep, ListTag out) {
		boolean all = true;
		for (Line line : lines(owner, tier)) {
			CompoundTag entry = new CompoundTag();
			if (line.cat() != null) {
				entry.putString("cat", line.cat().id());
			} else if (line.item() != null) {
				entry.putString("item", Ledger.id(line.item()));
			}
			entry.putInt("tier", tier);
			entry.putLong("amount", line.amount());
			entry.putLong("have", line.have());
			entry.putBoolean("covered", line.covered());
			entry.putBoolean("paid", line.paid());
			entry.putInt("pk", line.pk());
			entry.putBoolean("prep", prep);
			out.add(entry);
			all &= line.covered();
		}
		return all;
	}

	private BuildMissions() {
	}
}
