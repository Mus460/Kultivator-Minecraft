package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.data.RootGrade;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.item.RootItems;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pills the disciple takes by itself (DESIGN 39.5.6): {@code DiscipleCultivation.feedSelf} on one pill out of its own bag,
 * or out of the Lumbung within the hall's allowance ({@code Kebijakan.jatahLumbung}: by default Healing, Qi Gathering and
 * Qi Recovery Pills, one of each a day; breakthrough pills and the Marrow Cleansing Pill only from the bag or with the
 * master's per-kind permission in that list). The rules, checked every 30 s for a loaded disciple that is not fighting:
 * <ul>
 *   <li>a healer when the wound drive is above 40 (health below 60 %) or the disciple is badly wounded;</li>
 *   <li>Toxin Purging (or a Heart Calming Pill) during a Qi Deviation;</li>
 *   <li>a Heart Calming Pill when the temptation is above 50 (temptation -10, 39.3.3);</li>
 *   <li>the Marrow Cleansing Pill while the root is Low or worse, first of all when the Red colour caps it at Golden Core
 *   Peak (H7);</li>
 *   <li>a Bone Tempering Pill whenever it has one (until the old cap);</li>
 *   <li>a Qi Gathering Pill during seclusion.</li>
 * </ul>
 * The breakthrough pills are swallowed by {@link TerobosanSendiri} in its preparation. The Bond never grows (that is the
 * tie to the master); a pill the gates refuse goes back where it came from. A bottle pill (honey, dragon's breath) leaves
 * its glass bottle in the bag.
 */
public final class PilSendiri {

	public static final int LUKA_MINUM = 40;
	public static final int IBLIS_MINUM = 50;
	public static final int IBLIS_TURUN = 10;
	private static final String LUMBUNG = "lumbung";

	static void init() {
		// A Heart Calming Pill from the master calms the temptation too (39.3.3).
		DiscipleHooks.addOnFed((entity, master, stack) -> {
			if (isKind(stack, PillKind.HEART_CALMING)) {
				DataTumbuh.ubahIblis(entity.level().getServer(), entity, -IBLIS_TURUN);
			}
		});
	}

	/** Swallows one pill of {@code kind} from the bag; true when taken. */
	static boolean telan(DiscipleEntity entity, PillKind kind) {
		return telan(entity, stack -> isKind(stack, kind), "pil." + kind.id());
	}

	/** Swallows one Marrow Cleansing Pill from the bag. */
	static boolean telanSumsum(DiscipleEntity entity) {
		return telan(entity, stack -> stack.is(RootItems.MARROW_CLEANSING_PILL), "pil.sumsum");
	}

	/** True when the bag holds a pill of {@code kind}. */
	static boolean punya(DiscipleEntity entity, PillKind kind) {
		for (ItemStack stack : MandiriApi.tas(entity.data(), entity.level().registryAccess())) {
			if (isKind(stack, kind)) {
				return true;
			}
		}
		return false;
	}

	static boolean isKind(ItemStack stack, PillKind kind) {
		return stack.getItem() instanceof PillItem pill && pill.kind() == kind;
	}

	private static boolean telan(DiscipleEntity entity, Predicate<ItemStack> filter, String jenis) {
		DiscipleData data = entity.data();
		HolderLookup.Provider registries = entity.level().registryAccess();
		ItemStack pill = MandiriApi.ambilTas(data, registries, filter, 1);
		if (pill.isEmpty()) {
			return false;
		}
		return makan(entity, pill, jenis, false);
	}

	/** Feeds {@code pill} (one item); on refusal it goes back to the bag (or the Lumbung when it came from there). */
	private static boolean makan(DiscipleEntity entity, ItemStack pill, String jenis, boolean dariLumbung) {
		DiscipleData data = entity.data();
		HolderLookup.Provider registries = entity.level().registryAccess();
		boolean bottle = pill.is(Items.HONEY_BOTTLE) || pill.is(Items.DRAGON_BREATH);
		boolean calming = isKind(pill, PillKind.HEART_CALMING);
		Component name = pill.getHoverName().copy();
		String id = BuiltInRegistries.ITEM.getKey(pill.getItem()).toString();
		if (!DiscipleCultivation.feedSelf(entity, pill)) {
			UUID owner = entity.ownerId();
			if (dariLumbung && owner != null) {
				Storehouse.insert(owner, pill);
			} else {
				MandiriApi.masukTas(data, registries, pill);
			}
			return false;
		}
		if (bottle) {
			MandiriApi.masukTas(data, registries, new ItemStack(Items.GLASS_BOTTLE));
		}
		if (calming) {
			DataTumbuh.ubahIblis(entity.level().getServer(), entity, -IBLIS_TURUN);
		}
		KabarTumbuh.catat(entity, "pil", dariLumbung ? "pil.lumbung" : "pil.tas", EntriHarian.Suasana.TENANG, false, "i:" + id,
				jenis);
		if (dariLumbung && entity.ownerId() != null) {
			Halls.log(entity.ownerId(), Component.translatable(KabarTumbuh.P + "pil.log_lumbung", entity.getName(), name));
		}
		return true;
	}

	/**
	 * Takes one pill of {@code kind} out of the Lumbung, within the policy's allowance and once a day per kind, and
	 * swallows it. True when taken.
	 */
	static boolean dariLumbung(DiscipleEntity entity, PillKind kind) {
		UUID owner = entity.ownerId();
		if (owner == null) {
			return false;
		}
		Kebijakan policy = MandiriApi.kebijakan(entity);
		String id = "kultivator:" + kind.id();
		if (!policy.jatahLumbung().contains(id)) {
			return false;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		CompoundTag days = DataTumbuh.sub(entity.data(), LUMBUNG);
		if (days.getLongOr(kind.id(), -1L) == today) {
			return false;
		}
		List<ItemStack> taken = Storehouse.take(owner, stack -> isKind(stack, kind), 1);
		if (taken.isEmpty()) {
			return false;
		}
		days.putLong(kind.id(), today);
		return makan(entity, taken.get(0), "pil." + kind.id(), true);
	}

	/** The rules of 39.5.6, every 30 s for a loaded free disciple (not in a fight, not breaking through). */
	static void aturan(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (data.breakingThrough() || entity.getTarget() != null || TerobosanSendiri.sedang(entity.soulId())) {
			return;
		}
		long now = entity.level().getGameTime();
		float max = Math.max(1.0F, entity.getMaxHealth());
		int wound = (int) Math.round((1.0D - entity.getHealth() / max) * 100.0D);
		boolean berat = MandiriApi.luka(data, now) == JenisLuka.BERAT;
		if (wound > LUKA_MINUM || berat) {
			if (telan(entity, PillKind.HEALING) || dariLumbung(entity, PillKind.HEALING)) {
				return;
			}
		}
		if (data.deviating()) {
			if (telan(entity, PillKind.TOXIN_PURGING) || telan(entity, PillKind.HEART_CALMING)) {
				return;
			}
		}
		if (MandiriApi.iblis(data) > IBLIS_MINUM && telan(entity, PillKind.HEART_CALMING)) {
			return;
		}
		boolean redCap = String.valueOf(DiscipleCultivation.breakthroughBlockerSelf(entity, 1.0D)).endsWith("why.red_cap");
		if ((redCap || data.rootGrade().ordinal() <= RootGrade.LOW.ordinal()) && telanSumsum(entity)) {
			return;
		}
		if (telan(entity, PillKind.BONE_TEMPERING)) {
			return;
		}
		if (entity.activity() == MandiriKegiatan.ACT_TUTUP_DIRI) {
			if (!telan(entity, PillKind.QI_GATHERING)) {
				dariLumbung(entity, PillKind.QI_GATHERING);
			}
		}
	}

	private PilSendiri() {
	}
}
