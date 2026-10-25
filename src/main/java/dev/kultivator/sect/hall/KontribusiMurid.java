package dev.kultivator.sect.hall;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.item.RootItems;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.technique.source.JadeSlips;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bridge of Murid Mandiri (DESIGN 39.5.5, 39.5.6; module 39B) into the Paviliun Yue Lu of {@link Contribution}: a
 * disciple buys a ware with its <b>own</b> Poin Kontribusi ({@code SectRanks.pk}), never the hall pool. Every old rule
 * holds: the ware's hall level, {@code Contribution.price(ware, level, owner)} (the hall's price factor and the alliance
 * discount), the jade slip's grade price and hall level ({@code slipPrice}), the hall's two slips a day; on top of that a
 * disciple buys at most one slip a day. The goods go into the disciple's bag ({@code MandiriApi.masukTas}); nothing is
 * bought when the bag has no room (the PK stay). Server side only; no block is touched.
 * <p>
 * Sumber: <i>A Record of a Mortal's Journey</i> ch. 153 (Yue Lu Hall supplies manuals, recipes and materials for
 * contribution), already cited in {@link Contribution}.
 */
public final class KontribusiMurid {

	/** {@code extra.sect.slip_day}: the world day of the disciple's last slip. */
	private static final String SLIP_DAY = "slip_murid_day";

	/** What a disciple may buy for itself (the target-less pill wares and the jade slip). */
	public enum Barang {
		HEALING(Contribution.Ware.HEALING),
		QI_GATHERING(Contribution.Ware.QI_GATHERING),
		FOUNDATION(Contribution.Ware.FOUNDATION),
		GOLDEN_CORE(Contribution.Ware.GOLDEN_CORE),
		WARDING(Contribution.Ware.WARDING),
		/** A Marrow Cleansing Pill of Perfect quality (hall level 6). */
		MARROW(Contribution.Ware.MARROW),
		/** Slip Giok Teknik: the spec id is the parameter. */
		TECHNIQUE(Contribution.Ware.TECHNIQUE);

		final Contribution.Ware ware;

		Barang(Contribution.Ware ware) {
			this.ware = ware;
		}

		/** The ware's lang key {@code kultivator.hall.ware.<id>} (the hall fragment). */
		public String kunciNama() {
			return "kultivator.hall.ware." + ware.id;
		}
	}

	/** The PK price for the disciple's hall, or -1 when the hall does not sell it yet (level) or there is no hall. */
	public static int harga(UUID owner, Barang barang) {
		if (owner == null || barang == null || Halls.hallOf(owner) == null) {
			return -1;
		}
		int level = Halls.level(owner);
		return level < barang.ware.level ? -1 : Contribution.price(barang.ware, level, owner);
	}

	/** The PK price of a slip of {@code spec}, or -1 when the pavilion does not sell it at this hall level. */
	public static int hargaSlip(UUID owner, TechniqueSpec spec) {
		if (owner == null || spec == null || Halls.hallOf(owner) == null || !Contribution.sellable(spec)) {
			return -1;
		}
		int level = Halls.level(owner);
		if (level < Contribution.Ware.TECHNIQUE.level || level < spec.grade().pavilionHallLevel()) {
			return -1;
		}
		return Contribution.discounted(Contribution.slipPrice(spec, level), owner);
	}

	/**
	 * Why the disciple cannot buy it now (a lang key of {@code sekte_mandiri_tumbuh}), or null: a hall, the level, its own
	 * PK, the slip limits, a free bag slot, and for a slip a technique it could actually learn by itself.
	 */
	public static @Nullable String blocker(DiscipleEntity entity, Barang barang, @Nullable String specId) {
		UUID owner = entity.ownerId();
		if (owner == null || Halls.hallOf(owner) == null) {
			return "kultivator.sekte.mandiri.tumbuh.beli.tanpa_aula";
		}
		int price;
		if (barang == Barang.TECHNIQUE) {
			TechniqueSpec spec = specId == null ? null : TechniqueRegistry.byId(specId);
			if (spec == null) {
				return "kultivator.sekte.mandiri.tumbuh.beli.tanpa_slip";
			}
			price = hargaSlip(owner, spec);
			if (price < 0) {
				return "kultivator.sekte.mandiri.tumbuh.beli.terkunci";
			}
			if (DiscipleCultivation.teachBlockerSelf(entity, spec) != null || SectRanks.slotsFull(entity.data())) {
				return "kultivator.sekte.mandiri.tumbuh.beli.tak_cocok";
			}
			CompoundTag core = Halls.coreOf(owner);
			if (core != null && Contribution.slipsToday(core) >= Contribution.SLIPS_PER_DAY) {
				return "kultivator.sekte.mandiri.tumbuh.beli.slip_aula";
			}
			if (entity.data().extra().getCompoundOrEmpty(SectRanks.EXTRA).getLongOr(SLIP_DAY, -1L) == Halls.dayIndex()) {
				return "kultivator.sekte.mandiri.tumbuh.beli.slip_hari";
			}
		} else {
			price = harga(owner, barang);
			if (price < 0) {
				return "kultivator.sekte.mandiri.tumbuh.beli.terkunci";
			}
		}
		if (SectRanks.pk(entity.data()) < price) {
			return "kultivator.sekte.mandiri.tumbuh.beli.pk";
		}
		if (MandiriApi.tas(entity.data(), entity.level().registryAccess()).size() >= dev.kultivator.sekte.mandiri.api.MandiriIds.TAS_SLOT) {
			return "kultivator.sekte.mandiri.tumbuh.beli.tas_penuh";
		}
		return null;
	}

	/**
	 * Buys one ware with the disciple's own PK into its bag. Returns a copy of what was bought, or {@link ItemStack#EMPTY}
	 * when refused (nothing changed). The hall's log keeps a line; the caller writes the diary.
	 */
	public static ItemStack beli(DiscipleEntity entity, Barang barang, @Nullable String specId) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE || blocker(entity, barang, specId) != null) {
			return ItemStack.EMPTY;
		}
		UUID owner = entity.ownerId();
		DiscipleData data = entity.data();
		ItemStack stack;
		int price;
		TechniqueSpec spec = null;
		if (barang == Barang.TECHNIQUE) {
			spec = TechniqueRegistry.byId(specId);
			stack = JadeSlips.stack(spec.id());
			price = hargaSlip(owner, spec);
		} else {
			stack = barangStack(barang);
			price = harga(owner, barang);
		}
		if (stack.isEmpty() || price < 0 || !MandiriApi.masukTas(data, entity.level().registryAccess(), stack)) {
			return ItemStack.EMPTY;
		}
		SectRanks.addPk(data, -price, "kultivator.sekte.mandiri.tumbuh.pk.beli");
		if (spec != null) {
			SectRanks.ensure(data);
			data.extraSection(SectRanks.EXTRA).putLong(SLIP_DAY, Halls.dayIndex());
			// A disciple's slip counts toward the hall's daily limit too (as in Contribution.buy).
			CompoundTag core = Halls.coreOf(owner);
			if (core != null) {
				long day = Halls.dayIndex();
				if (core.getLongOr("slip_day", -1L) != day) {
					core.putLong("slip_day", day);
					core.putInt("slips_today", 0);
				}
				core.putInt("slips_today", core.getIntOr("slips_today", 0) + 1);
				Halls.setDirty();
			}
		}
		Component what = spec != null ? spec.displayName() : stack.getHoverName();
		Halls.log(owner, Component.translatable("kultivator.sekte.mandiri.tumbuh.beli.log", entity.getName(), what, price));
		if (entity.level() instanceof ServerLevel level) {
			level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.4F);
		}
		return stack.copy();
	}

	/**
	 * Slips the disciple could buy and learn by itself now, cheapest first (its own Dao's arts first among equals), at most
	 * {@code max}. Reads the registry once; for the planner's shopping wish and the test command.
	 */
	public static List<TechniqueSpec> slipTerjangkau(DiscipleEntity entity, int max) {
		UUID owner = entity.ownerId();
		List<TechniqueSpec> list = new ArrayList<>();
		if (owner == null || Halls.hallOf(owner) == null || SectRanks.slotsFull(entity.data())) {
			return list;
		}
		int pk = SectRanks.pk(entity.data());
		for (TechniqueSpec spec : Contribution.slips()) {
			int price = hargaSlip(owner, spec);
			if (price >= 0 && price <= pk && DiscipleCultivation.teachBlockerSelf(entity, spec) == null) {
				list.add(spec);
			}
		}
		DiscipleData data = entity.data();
		list.sort((a, b) -> {
			int byPrice = Integer.compare(hargaSlip(owner, a), hargaSlip(owner, b));
			if (byPrice != 0) {
				return byPrice;
			}
			boolean ownA = a.dao() != null && a.dao() == data.dao();
			boolean ownB = b.dao() != null && b.dao() == data.dao();
			return Boolean.compare(ownB, ownA);
		});
		return list.size() > max ? new ArrayList<>(list.subList(0, Math.max(0, max))) : list;
	}

	/** The item of a pill ware, created on demand (lazy ItemStacks). */
	private static ItemStack barangStack(Barang barang) {
		return switch (barang) {
			case HEALING -> AlchemyComponents.withQuality(new ItemStack(AlchemyItems.HEALING_PILL), PillQuality.MID);
			case QI_GATHERING -> AlchemyComponents.withQuality(new ItemStack(AlchemyItems.QI_GATHERING_PILL), PillQuality.MID);
			case FOUNDATION -> AlchemyComponents.withQuality(new ItemStack(AlchemyItems.FOUNDATION_PILL), PillQuality.MID);
			case GOLDEN_CORE -> AlchemyComponents.withQuality(new ItemStack(AlchemyItems.GOLDEN_CORE_PILL), PillQuality.MID);
			case WARDING -> AlchemyComponents.withQuality(new ItemStack(AlchemyItems.TRIBULATION_WARDING_PILL), PillQuality.MID);
			case MARROW -> AlchemyComponents.withQuality(new ItemStack(RootItems.MARROW_CLEANSING_PILL), PillQuality.PERFECT);
			case TECHNIQUE -> ItemStack.EMPTY;
		};
	}

	private KontribusiMurid() {
	}
}
