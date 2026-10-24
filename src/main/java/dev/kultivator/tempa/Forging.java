package dev.kultivator.tempa;

import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.formasi.stack.FormationField;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Forging jobs and the Keahlian Tempa skill (DESIGN 25.5.3-25.5.10, 25.7.2, 25.15, owned by P8-tempa). A plan is computed
 * from the forge's 9 slots, the heat, the skill, the realm, the assistant and the 7c formation field; the client's preview
 * card shows its lines. A job channels while the menu stays open and the player within 4 blocks (a hit above 20% of max
 * health pauses it; 10 s paused cancels it with nothing consumed); only completion consumes materials. Every number is
 * game design except the cited fires and the RMJ/AWE concepts named in the lang Sumber lines.
 */
public final class Forging {

	/** Skill gates per grade Kuning … Dewa. */
	public static final int[] GATES = {0, 2, 4, 6, 8, 10};
	public static final double[] QI = {20.0D, 60.0D, 150.0D, 400.0D, 1000.0D, 0.0D};
	public static final double DEWA_QI_SHARE = 0.8D;
	public static final double INNATE_QI_FACTOR = 2.0D;
	public static final int[] CHANNEL_SECONDS = {10, 15, 20, 30, 40, 60};
	/** The core grade each forging grade needs (Dewa: two Raja cores). */
	public static final int[] CORE_MIN = {1, 1, 2, 3, 4, 4};
	/** Realm materials per grade (Bumi 2, Langit 1, Abadi 2, Dewa 3). */
	public static final int[] REALM_COUNT = {0, 0, 2, 1, 2, 3};
	public static final double BASE_CHANCE = 0.60D;
	public static final double SKILL_CHANCE = 0.035D;
	public static final double CHANCE_CAP = 0.95D;
	public static final double STONE_LOW = 0.05D;
	public static final double STONE_MID = 0.10D;
	public static final int MAX_STONES = 2;
	public static final double FIRE_DAO = 0.05D;
	public static final double CORE_SUB = 0.1D;
	public static final double HERB_YEARS = 3333.0D;
	public static final double HERB_CAP = 0.3D;
	public static final double SUB_LOW = 0.5D;
	public static final double SUB_MID = 0.85D;
	public static final double ASSIST = 0.05D;
	public static final double ASSIST_TALENT = 0.10D;
	public static final double ASSIST_FIRE = 0.03D;
	public static final int XP_SUCCESS = 5;
	public static final int XP_FAIL = 2;
	public static final int XP_GATE_SLACK = 2;
	public static final double REACH = 4.0D;
	public static final double PAUSE_HIT = 0.2D;
	public static final int PAUSE_HIT_TICKS = 60;
	public static final int PAUSE_CANCEL = 200;
	public static final double REFINE_BASE = 0.95D;
	public static final double REFINE_STEP = 0.08D;
	public static final double REFINE_SKILL = 0.02D;
	public static final double REFINE_MIN = 0.15D;
	public static final int KUALI_SKILL = 6;
	public static final double KUALI_QI = 400.0D;
	public static final int KUALI_SECONDS = 60;
	public static final int COLLAPSE_STONES = 5;

	public static final int KIND_NONE = 0;
	public static final int KIND_NEW = 1;
	public static final int KIND_KUALI = 2;
	public static final int KIND_REFINE = 3;
	public static final int KIND_SPIRIT = 4;
	public static final int KIND_RING = 5;

	private static final String[] LIMITS = {"heat", "material", "skill", "realm"};

	/** One step of what a job consumes: a match and a count; {@code keepOnFail} survives a failed forging. */
	record Take(Predicate<ItemStack> match, int count, boolean keepOnFail) {
	}

	/** A computed plan for the current tab. */
	static final class Plan {
		int kind = KIND_NONE;
		boolean ok;
		@Nullable CompoundTag problem;
		final ListTag lines = new ListTag();
		CompoundTag title = text("kultivator.tempa.preview.none");
		final List<Take> takes = new ArrayList<>();
		double chance = -1.0D;
		double qi;
		int ticks;
		int heatNeed;
		int grade = -1;
		String form = ArtifactItem.PEDANG;
		@Nullable Element element;
		double subBonus;
		int ringTier = -1;
		int ringBase = -1;
		int ringExtra;
		int refineTarget;
		int artifactSlot = -1;
		boolean natal;

		void fail(String key, String... args) {
			ok = false;
			problem = text(key, args);
		}
	}

	/** A running channel (server only). */
	static final class Job {
		final int kind;
		final int ticks;
		final double qi;
		final int heatNeed;
		int done;
		int paused;
		long pauseUntil;

		Job(int kind, int ticks, double qi, int heatNeed) {
			this.kind = kind;
			this.ticks = Math.max(1, ticks);
			this.qi = qi;
			this.heatNeed = heatNeed;
		}
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && player.containerMenu instanceof ForgeMenu menu && menu.job != null
					&& damageTaken > player.getMaxHealth() * PAUSE_HIT) {
				menu.job.pauseUntil = player.level().getGameTime() + PAUSE_HIT_TICKS;
			}
		});
		DiscipleHooks.addXpFactor((entity, data, source) ->
				entity != null && source == XpSource.MEDITATION && entity.activity() == DiscipleEntity.ACTIVITY_FORGE_ASSIST ? 0.5D : 1.0D);
		DiscipleHooks.addTick20(Forging::checkAssistant);
	}

	// ================================================================== skill

	/** The player's forging skill level 0-10 ({@code min(10, floor(sqrt(xp / 10)))}). */
	public static int skillLevel(ServerPlayer player) {
		return level(xp(player));
	}

	public static int level(double xp) {
		return (int) Math.min(10, Math.floor(Math.sqrt(Math.max(0.0D, xp) / 10.0D)));
	}

	/** XP of the next level (for the skill bar). */
	public static double xpFor(int level) {
		return 10.0D * level * level;
	}

	static double xp(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(NatalTreasureSelf.TEMPA).getDoubleOr("xp", 0.0D);
	}

	static void setXp(ServerPlayer player, double xp) {
		NatalTreasureSelf.tempa(Cultivators.get(player)).putDouble("xp", Math.max(0.0D, xp));
		Cultivators.dirty(player);
	}

	/** +5 × (grade + 1) per success, +2 per failure, 0 when the grade's gate is more than 2 levels below the forger. */
	static void addXp(ServerPlayer player, int grade, boolean success) {
		int level = skillLevel(player);
		if (level - GATES[Math.max(0, Math.min(5, grade))] > XP_GATE_SLACK) {
			return;
		}
		int before = level;
		setXp(player, xp(player) + (success ? XP_SUCCESS * (grade + 1) : XP_FAIL));
		int after = skillLevel(player);
		if (after > before) {
			Msg.chat(player, Component.translatable("kultivator.tempa.skill.up", title(after)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** "Ahli Tempa Tingkat n" or "Grandmaster Tempa". */
	public static Component title(int level) {
		return level >= 10 ? Component.translatable("kultivator.tempa.skill.grandmaster") : Component.translatable("kultivator.tempa.skill.level", level);
	}

	private static void count(ServerPlayer player, String key) {
		CompoundTag tempa = NatalTreasureSelf.tempa(Cultivators.get(player));
		tempa.putInt(key, tempa.getIntOr(key, 0) + 1);
		Cultivators.dirty(player);
	}

	// ================================================================== gates

	static boolean realmAllows(CultivationData data, int grade) {
		Rank rank = data.rank();
		Realm realm = rank.realm();
		return switch (grade) {
			case 0 -> realm.atLeast(Realm.QI_REFINING);
			case 1 -> realm.atLeast(Realm.FOUNDATION);
			case 2 -> realm.atLeast(Realm.GOLDEN_CORE);
			case 3 -> realm.atLeast(Realm.NASCENT_SOUL);
			case 4 -> realm.atLeast(Realm.DEITY_TRANSFORMATION);
			default -> realm.atLeast(Realm.TRUE_IMMORTAL) || realm == Realm.IMMORTAL_ASCENSION && rank.layer() >= 6;
		};
	}

	private static int realmGate(CultivationData data) {
		int best = -1;
		for (int g = 0; g <= ArtifactItem.MAX_GRADE; g++) {
			if (realmAllows(data, g)) {
				best = g;
			}
		}
		return best;
	}

	private static int skillGate(int skill) {
		int best = 0;
		for (int g = 0; g <= ArtifactItem.MAX_GRADE; g++) {
			if (GATES[g] <= skill) {
				best = g;
			}
		}
		return best;
	}

	// ================================================================== helpers

	static CompoundTag text(String key, String... args) {
		CompoundTag tag = new CompoundTag();
		tag.putString("k", key);
		ListTag list = new ListTag();
		for (String arg : args) {
			list.add(StringTag.valueOf(arg));
		}
		tag.put("a", list);
		return tag;
	}

	private static void line(Plan plan, int color, String key, String... args) {
		CompoundTag tag = text(key, args);
		tag.putInt("c", color);
		plan.lines.add(tag);
	}

	private static final String[] STONE_IDS = {"low", "mid", "high", "supreme"};

	private static String stoneKey(int grade) {
		return "#item.kultivator.spirit_stone_" + STONE_IDS[Math.max(0, Math.min(3, grade))];
	}

	/** The component of a {k, a} text tag (args starting with '#' are translation keys). */
	public static Component component(CompoundTag tag) {
		ListTag list = tag.getListOrEmpty("a");
		Object[] args = new Object[list.size()];
		for (int i = 0; i < list.size(); i++) {
			String arg = list.get(i).asString().orElse("");
			args[i] = arg.startsWith("#") ? Component.translatable(arg.substring(1)) : Component.literal(arg);
		}
		return Component.translatable(tag.getStringOr("k", ""), args);
	}

	static String pct(double fraction) {
		return String.format(Locale.ROOT, "%.0f%%", fraction * 100.0D);
	}

	static String signed(double fraction) {
		return String.format(Locale.ROOT, "%+.0f%%", fraction * 100.0D);
	}

	static String num(double value) {
		return String.format(Locale.ROOT, "%.0f", value);
	}

	private static Predicate<ItemStack> item(Item item) {
		return stack -> stack.is(item);
	}

	private static Predicate<ItemStack> stone(int grade) {
		return stack -> SpiritStones.isStone(stack) && SpiritStones.grade(stack) == grade;
	}

	private static Predicate<ItemStack> core(int grade, @Nullable Element element) {
		return stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) == grade && BeastCores.element(stack) == element;
	}

	private static boolean hasSeedItem(SimpleContainer input) {
		Item seed = TempaItems.byId(TempaItems.FLAME_SEED_ID);
		return seed != Items.AIR && Inv.count(input, item(seed)) > 0;
	}

	private static int stoneCount(SimpleContainer input, int grade) {
		return Inv.count(input, stone(grade));
	}

	/** The best core in the slots matching {@code filter}: {grade, element ordinal or -1}, or null. */
	private static int[] bestCore(SimpleContainer input, Predicate<ItemStack> filter, boolean lowest) {
		int[] best = null;
		for (int i = 0; i < input.getContainerSize(); i++) {
			ItemStack stack = input.getItem(i);
			if (!BeastCores.isCore(stack) || !filter.test(stack)) {
				continue;
			}
			int grade = BeastCores.grade(stack);
			Element element = BeastCores.element(stack);
			if (best == null || (lowest ? grade < best[0] : grade > best[0])) {
				best = new int[] {grade, element == null ? -1 : element.ordinal()};
			}
		}
		return best;
	}

	private static @Nullable Element elementOf(int ordinal) {
		return ordinal < 0 || ordinal >= Element.values().length ? null : Element.values()[ordinal];
	}

	/** The disciple assistant's bonus at this forge (DESIGN 25.5.8): +0.05, +0.10 with Jari Penempa Roh, +0.03 Fire root or Dao. */
	static double assistBonus(ServerLevel level, BlockPos forge, UUID owner) {
		double best = 0.0D;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box(forge, 3.0D),
				entity -> entity.activity() == DiscipleEntity.ACTIVITY_FORGE_ASSIST && owner.equals(entity.ownerId()))) {
			DiscipleData data = disciple.data();
			double bonus = (Talents.talents(data) & Talents.JARI_PENEMPA_ROH) != 0 ? ASSIST_TALENT : ASSIST;
			if ((data.rootElements() & Element.FIRE.bit()) != 0 || data.dao() == Dao.FIRE) {
				bonus += ASSIST_FIRE;
			}
			best = Math.max(best, bonus);
		}
		return best;
	}

	private static AABB box(BlockPos pos, double radius) {
		return new AABB(pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius, pos.getX() + 1 + radius, pos.getY() + 1 + radius,
				pos.getZ() + 1 + radius);
	}

	/** The chance parts every forging shares: skill, Fire Dao, assistant, formation field; lines added to the plan. */
	private static double commonParts(Plan plan, ServerPlayer player, ForgeMenu menu, double skillStep, int skill) {
		CultivationData data = Cultivators.get(player);
		double sum = skillStep * skill;
		line(plan, 0x465950, "kultivator.tempa.part.skill", signed(skillStep * skill), String.valueOf(skill));
		if (data.dao() == Dao.FIRE) {
			sum += FIRE_DAO;
			line(plan, 0x465950, "kultivator.tempa.part.fire", signed(FIRE_DAO));
		}
		double assist = assistBonus(player.level(), menu.pos, player.getUUID());
		if (assist > 0.0D) {
			sum += assist;
			line(plan, 0x465950, "kultivator.tempa.part.assist", signed(assist));
		}
		FormationField field = Stacking.fieldAtBlock(player.level(), menu.pos, player.getUUID());
		if (field.refineChance() > 0.0D) {
			sum += field.refineChance();
			line(plan, 0x465950, "kultivator.tempa.part.field", signed(field.refineChance()));
		}
		return sum;
	}

	private static double timeFactor(ServerPlayer player, ForgeMenu menu) {
		FormationField field = Stacking.fieldAtBlock(player.level(), menu.pos, player.getUUID());
		return Math.max(0.25D, Math.min(2.0D, field.refineTime()));
	}

	// ================================================================== plans

	static Plan plan(ServerPlayer player, ForgeMenu menu) {
		int heat = menu.heat();
		return switch (menu.tab()) {
			case ForgeMenu.TAB_REFINE -> planRefine(player, menu, heat);
			case ForgeMenu.TAB_SPIRIT -> planSpirit(player, menu);
			case ForgeMenu.TAB_RING -> planRing(player, menu, heat);
			default -> kuali(menu.input) >= 0 ? planKuali(player, menu, heat) : planNew(player, menu, heat);
		};
	}

	/** Tempa Baru (DESIGN 25.5.4). */
	private static Plan planNew(ServerPlayer player, ForgeMenu menu, int heat) {
		Plan plan = new Plan();
		plan.kind = KIND_NEW;
		plan.title = text("kultivator.tempa.preview.new");
		SimpleContainer in = menu.input;
		CultivationData data = Cultivators.get(player);
		// The embryo sets the form.
		Take embryo;
		Take embryoExtra = null;
		if (Inv.count(in, stack -> stack.is(ItemTags.SWORDS)) > 0) {
			plan.form = ArtifactItem.PEDANG;
			embryo = new Take(stack -> stack.is(ItemTags.SWORDS), 1, true);
		} else if (Inv.count(in, item(Items.BAMBOO_BLOCK)) > 0 && Inv.count(in, item(Items.STICK)) >= 4) {
			plan.form = ArtifactItem.PAYUNG;
			embryo = new Take(item(Items.BAMBOO_BLOCK), 1, true);
			embryoExtra = new Take(item(Items.STICK), 4, true);
		} else if (Inv.count(in, item(Items.CAULDRON)) > 0) {
			plan.form = ArtifactItem.WAJAN;
			embryo = new Take(item(Items.CAULDRON), 1, true);
		} else if (Inv.count(in, item(Items.BELL)) > 0) {
			plan.form = ArtifactItem.LONCENG;
			embryo = new Take(item(Items.BELL), 1, true);
		} else {
			plan.fail("kultivator.tempa.problem.embryo");
			line(plan, 0x7A8C83, "kultivator.tempa.hint.embryo");
			return plan;
		}
		line(plan, 0x1B2521, "kultivator.tempa.preview.form", "#kultivator.tempa.form." + plan.form);
		int[] bestCore = bestCore(in, stack -> true, false);
		int rajaCount = Inv.count(in, stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) >= BeastCores.KING);
		int highestRow = -1;
		for (int g = 0; g <= ArtifactItem.MAX_GRADE; g++) {
			if (rowOk(in, g, bestCore, rajaCount)) {
				highestRow = g;
			}
		}
		int skill = skillLevel(player);
		int realmGate = realmGate(data);
		int skillGate = skillGate(skill);
		int cap = Math.min(heat, Math.min(skillGate, realmGate));
		int result = -1;
		for (int g = 0; g <= Math.max(-1, cap); g++) {
			if (rowOk(in, g, bestCore, rajaCount)) {
				result = g;
			}
		}
		if (realmGate < 0) {
			plan.fail("kultivator.tempa.problem.realm", "#kultivator.realm.qi_refining");
			return plan;
		}
		if (result < 0) {
			plan.fail("kultivator.tempa.problem.metal");
			line(plan, 0x7A8C83, "kultivator.tempa.hint.metal");
			return plan;
		}
		String limit;
		if (highestRow > result) {
			limit = cap == heat ? LIMITS[0] : cap == skillGate ? LIMITS[2] : LIMITS[3];
		} else {
			limit = LIMITS[1];
		}
		final int grade = result;
		plan.grade = grade;
		plan.heatNeed = grade;
		line(plan, ArtifactItem.GRADE_COLORS[grade], "kultivator.tempa.preview.grade", "#kultivator.tempa.grade." + grade, "#kultivator.tempa.limit." + limit);
		// Core and element.
		int coreGrade;
		Element element;
		if (grade == 5) {
			int[] raja = bestCore(in, stack -> BeastCores.grade(stack) >= BeastCores.KING, false);
			coreGrade = raja == null ? BeastCores.KING : raja[0];
			element = raja == null ? null : elementOf(raja[1]);
			plan.takes.add(new Take(stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) >= BeastCores.KING, 2, false));
		} else {
			int[] chosen = bestCore(in, stack -> BeastCores.grade(stack) >= CORE_MIN[grade], false);
			coreGrade = chosen == null ? CORE_MIN[grade] : chosen[0];
			element = chosen == null ? null : elementOf(chosen[1]);
			plan.takes.add(new Take(core(coreGrade, element), 1, false));
		}
		plan.element = element;
		if (element != null) {
			line(plan, element.color(), "kultivator.tempa.preview.element", "#kultivator.element." + element.id());
		}
		addMetal(plan, grade);
		if (REALM_COUNT[grade] > 0) {
			plan.takes.add(new Take(stack -> stack.is(TempaItems.realmTag(grade)), REALM_COUNT[grade], true));
		}
		plan.takes.add(embryo);
		if (embryoExtra != null) {
			plan.takes.add(embryoExtra);
		}
		Heat.Reading reading = Heat.read(player.level(), menu.pos, player, hasSeedItem(in));
		if (grade == 5 && reading.usesSeed()) {
			Item seed = TempaItems.byId(TempaItems.FLAME_SEED_ID);
			plan.takes.add(new Take(item(seed), 1, false));
		}
		// Catalysts: up to two spirit stones, the best first.
		double chance = BASE_CHANCE;
		line(plan, 0x465950, "kultivator.tempa.part.base", pct(BASE_CHANCE));
		int left = MAX_STONES;
		double stones = 0.0D;
		for (int g = SpiritStones.SUPREME; g >= SpiritStones.LOW && left > 0; g--) {
			int have = Math.min(left, stoneCount(in, g));
			if (have > 0) {
				plan.takes.add(new Take(stone(g), have, false));
				stones += have * (g == SpiritStones.LOW ? STONE_LOW : STONE_MID);
				left -= have;
			}
		}
		if (stones > 0.0D) {
			chance += stones;
			line(plan, 0x465950, "kultivator.tempa.part.stones", signed(stones));
		}
		chance += commonParts(plan, player, menu, SKILL_CHANCE, skill);
		plan.chance = Math.min(CHANCE_CAP, chance);
		// Sub-quality odds: core grade above the minimum and the herb's age.
		double sub = CORE_SUB * Math.max(0, coreGrade - CORE_MIN[grade]);
		ItemStack herb = bestHerb(in);
		if (!herb.isEmpty()) {
			int age = SpiritHerbs.age(herb);
			HerbKind kind = SpiritHerbs.kind(herb);
			double years = age * (kind == HerbKind.GOLD_LIGHTNING_BAMBOO && ArtifactItem.PEDANG.equals(plan.form) ? 2.0D : 1.0D);
			sub += Math.min(HERB_CAP, years / HERB_YEARS);
			HerbKind herbKind = kind;
			plan.takes.add(new Take(stack -> SpiritHerbs.isHerb(stack) && SpiritHerbs.kind(stack) == herbKind && SpiritHerbs.age(stack) == age, 1, false));
		}
		plan.subBonus = sub;
		double low = clamp01(SUB_LOW - sub);
		double mid = clamp01(SUB_MID - sub) - low;
		line(plan, 0x184B36, "kultivator.tempa.preview.odds", pct(low), pct(mid), pct(Math.max(0.0D, 1.0D - low - mid)));
		double qi = grade == 5 ? Qi.max(data) * DEWA_QI_SHARE : QI[grade];
		if (heat == Heat.INNATE) {
			qi *= INNATE_QI_FACTOR;
		}
		plan.qi = qi;
		plan.ticks = (int) Math.round(CHANNEL_SECONDS[grade] * 20 * timeFactor(player, menu));
		line(plan, 0x47B2FF, "kultivator.tempa.preview.cost", num(qi), String.valueOf(plan.ticks / 20));
		line(plan, 0x2E8963, "kultivator.tempa.preview.chance", pct(plan.chance));
		plan.ok = true;
		return plan;
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private static ItemStack bestHerb(SimpleContainer in) {
		ItemStack best = ItemStack.EMPTY;
		for (int i = 0; i < in.getContainerSize(); i++) {
			ItemStack stack = in.getItem(i);
			if (SpiritHerbs.isHerb(stack) && (best.isEmpty() || SpiritHerbs.age(stack) > SpiritHerbs.age(best))) {
				best = stack;
			}
		}
		return best;
	}

	/** The metal of each grade row. */
	private static boolean metalOk(SimpleContainer in, int grade) {
		return switch (grade) {
			case 0 -> Inv.count(in, item(Items.IRON_INGOT)) >= 4;
			case 1 -> Inv.count(in, item(Items.GOLD_INGOT)) >= 4 && Inv.count(in, item(Items.DIAMOND)) >= 1;
			case 2 -> Inv.count(in, item(Items.DIAMOND)) >= 2 && Inv.count(in, item(Items.AMETHYST_BLOCK)) >= 2;
			case 3 -> Inv.count(in, item(Items.NETHERITE_INGOT)) >= 1 && Inv.count(in, item(Items.DIAMOND)) >= 2;
			case 4 -> Inv.count(in, item(Items.NETHERITE_INGOT)) >= 2 && Inv.count(in, item(Items.ECHO_SHARD)) >= 1;
			default -> Inv.count(in, item(Items.NETHERITE_INGOT)) >= 4 && Inv.count(in, item(Items.NETHER_STAR)) >= 1;
		};
	}

	private static void addMetal(Plan plan, int grade) {
		switch (grade) {
			case 0 -> plan.takes.add(new Take(item(Items.IRON_INGOT), 4, false));
			case 1 -> {
				plan.takes.add(new Take(item(Items.GOLD_INGOT), 4, false));
				plan.takes.add(new Take(item(Items.DIAMOND), 1, false));
			}
			case 2 -> {
				plan.takes.add(new Take(item(Items.DIAMOND), 2, false));
				plan.takes.add(new Take(item(Items.AMETHYST_BLOCK), 2, false));
			}
			case 3 -> {
				plan.takes.add(new Take(item(Items.NETHERITE_INGOT), 1, false));
				plan.takes.add(new Take(item(Items.DIAMOND), 2, false));
			}
			case 4 -> {
				plan.takes.add(new Take(item(Items.NETHERITE_INGOT), 2, false));
				plan.takes.add(new Take(item(Items.ECHO_SHARD), 1, false));
			}
			default -> {
				plan.takes.add(new Take(item(Items.NETHERITE_INGOT), 4, false));
				plan.takes.add(new Take(item(Items.NETHER_STAR), 1, false));
			}
		}
	}

	private static boolean rowOk(SimpleContainer in, int grade, int[] bestCore, int rajaCount) {
		if (!metalOk(in, grade) || bestCore == null) {
			return false;
		}
		boolean coreOk = grade == 5 ? rajaCount >= 2 : bestCore[0] >= CORE_MIN[grade];
		if (!coreOk) {
			return false;
		}
		return REALM_COUNT[grade] == 0 || Inv.count(in, stack -> stack.is(TempaItems.realmTag(grade))) >= REALM_COUNT[grade];
	}

	/** The slot of an unopened Kuali Hampa Langit, or -1. */
	static int kuali(SimpleContainer in) {
		Item cauldron = TempaItems.byId(TempaItems.CAULDRON_ID);
		if (cauldron == Items.AIR) {
			return -1;
		}
		return Inv.firstIndex(in, stack -> stack.is(cauldron) && !opened(stack));
	}

	static boolean opened(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("kultivator_opened", false);
	}

	/** Buka Tutup (DESIGN 25.5.9): Nascent Soul+, skill 6+, Langit heat, 400 qi, a 60 s channel; no failure. */
	private static Plan planKuali(ServerPlayer player, ForgeMenu menu, int heat) {
		Plan plan = new Plan();
		plan.kind = KIND_KUALI;
		plan.title = text("kultivator.tempa.preview.kuali");
		plan.form = ArtifactItem.FORMS.get(Math.max(0, Math.min(3, menu.kualiForm)));
		line(plan, 0x1B2521, "kultivator.tempa.preview.form", "#kultivator.tempa.form." + plan.form);
		line(plan, 0x465950, "kultivator.tempa.hint.kuali");
		CultivationData data = Cultivators.get(player);
		if (!data.realm().atLeast(Realm.NASCENT_SOUL)) {
			plan.fail("kultivator.tempa.problem.realm", "#kultivator.realm.nascent_soul");
			return plan;
		}
		if (skillLevel(player) < KUALI_SKILL) {
			plan.fail("kultivator.tempa.problem.skill", String.valueOf(KUALI_SKILL));
			return plan;
		}
		if (heat < Heat.LUNG) {
			plan.fail("kultivator.tempa.problem.heat", "#kultivator.tempa.heat." + Heat.LUNG);
			return plan;
		}
		plan.grade = 3;
		plan.heatNeed = Heat.LUNG;
		plan.qi = KUALI_QI;
		plan.ticks = (int) Math.round(KUALI_SECONDS * 20 * timeFactor(player, menu));
		plan.chance = 1.0D;
		line(plan, ArtifactItem.GRADE_COLORS[3], "kultivator.tempa.preview.grade", "#kultivator.tempa.grade.3", "#kultivator.tempa.limit.kuali");
		line(plan, 0x47B2FF, "kultivator.tempa.preview.cost", num(plan.qi), String.valueOf(plan.ticks / 20));
		plan.ok = true;
		return plan;
	}

	/** Tempa Ulang +0…+12 (DESIGN 25.5.8). */
	private static Plan planRefine(ServerPlayer player, ForgeMenu menu, int heat) {
		Plan plan = new Plan();
		plan.kind = KIND_REFINE;
		plan.title = text("kultivator.tempa.preview.refine");
		plan.natal = menu.natalTarget();
		SimpleContainer in = menu.input;
		CompoundTag art;
		if (plan.natal) {
			art = NatalTreasureSelf.record(player);
			if (art == null) {
				plan.fail("kultivator.tempa.natal.none");
				return plan;
			}
			line(plan, 0x8A671B, "kultivator.tempa.preview.natal_target");
		} else {
			plan.artifactSlot = Inv.firstIndex(in, ArtifactItem::is);
			if (plan.artifactSlot < 0) {
				plan.fail("kultivator.tempa.problem.artifact");
				line(plan, 0x7A8C83, "kultivator.tempa.hint.refine");
				return plan;
			}
			art = ArtifactItem.data(in.getItem(plan.artifactSlot));
			if (!NatalTreasureSelf.usable(player, art)) {
				plan.fail("kultivator.tempa.foreign");
				return plan;
			}
		}
		int refine = ArtifactItem.refine(art);
		int grade = ArtifactItem.grade(art);
		plan.grade = grade;
		if (refine >= ArtifactItem.MAX_REFINE) {
			plan.fail("kultivator.tempa.problem.max_refine");
			return plan;
		}
		int n = refine + 1;
		plan.refineTarget = n;
		line(plan, 0x1B2521, "kultivator.tempa.preview.refine_to", String.valueOf(refine), String.valueOf(n));
		CultivationData data = Cultivators.get(player);
		if (n >= 10) {
			int stars = 9 * (n - 9);
			if (!data.realm().atLeast(Realm.TRUE_IMMORTAL) || ImmortalPerks.stars(data) < stars) {
				plan.fail("kultivator.tempa.problem.stars", String.valueOf(stars));
				return plan;
			}
		}
		if (heat < grade) {
			plan.fail("kultivator.tempa.problem.heat", "#kultivator.tempa.heat." + grade);
			return plan;
		}
		int stoneGrade;
		int stones;
		int coreNeed;
		Predicate<ItemStack> extra;
		String extraKey;
		if (n <= 3) {
			stoneGrade = SpiritStones.LOW;
			stones = 4 * n;
			coreNeed = BeastCores.LOW;
			extra = item(Items.IRON_INGOT);
			extraKey = "#item.minecraft.iron_ingot";
		} else if (n <= 6) {
			stoneGrade = SpiritStones.MID;
			stones = n - 2;
			coreNeed = BeastCores.MID;
			extra = item(Items.GOLD_INGOT);
			extraKey = "#item.minecraft.gold_ingot";
		} else if (n <= 9) {
			stoneGrade = SpiritStones.HIGH;
			stones = n - 6;
			coreNeed = BeastCores.HIGH;
			extra = stack -> stack.is(TempaItems.REALM_ABADI);
			extraKey = "#kultivator.tempa.material.abadi";
		} else {
			stoneGrade = SpiritStones.SUPREME;
			stones = 1;
			coreNeed = BeastCores.KING;
			extra = stack -> stack.is(TempaItems.REALM_DEWA);
			extraKey = "#kultivator.tempa.material.dewa";
		}
		Element element = ArtifactItem.element(art);
		line(plan, 0x465950, "kultivator.tempa.preview.refine_cost", String.valueOf(stones), stoneKey(stoneGrade),
				"#kultivator.harta.core_grade." + coreNeed, element == null ? "-" : "#kultivator.element." + element.id(), extraKey);
		if (stoneCount(in, stoneGrade) < stones) {
			plan.fail("kultivator.tempa.problem.stones", String.valueOf(stones), stoneKey(stoneGrade));
			return plan;
		}
		int[] chosen = bestCore(in, stack -> BeastCores.grade(stack) >= coreNeed && (element == null || BeastCores.element(stack) == element), true);
		if (chosen == null) {
			plan.fail("kultivator.tempa.problem.core", "#kultivator.harta.core_grade." + coreNeed);
			return plan;
		}
		if (Inv.count(in, extra) < 1) {
			plan.fail("kultivator.tempa.problem.extra", extraKey);
			return plan;
		}
		plan.takes.add(new Take(stone(stoneGrade), stones, false));
		plan.takes.add(new Take(core(chosen[0], elementOf(chosen[1])), 1, false));
		plan.takes.add(new Take(extra, 1, false));
		double chance = REFINE_BASE - REFINE_STEP * (n - 1);
		line(plan, 0x465950, "kultivator.tempa.part.base", pct(chance));
		chance += commonParts(plan, player, menu, REFINE_SKILL, skillLevel(player));
		plan.chance = Math.max(REFINE_MIN, Math.min(CHANCE_CAP, chance));
		plan.heatNeed = grade;
		plan.ticks = (int) Math.round((100 + 20 * n) * timeFactor(player, menu));
		if (n >= 4) {
			line(plan, 0xD5322A, "kultivator.tempa.preview.refine_risk");
		}
		line(plan, 0x2E8963, "kultivator.tempa.preview.chance", pct(plan.chance));
		plan.ok = true;
		return plan;
	}

	/** The Roh tab: the natal spirit's state and its four actions (instant). */
	private static Plan planSpirit(ServerPlayer player, ForgeMenu menu) {
		Plan plan = new Plan();
		plan.kind = KIND_SPIRIT;
		plan.title = text("kultivator.tempa.preview.spirit");
		CompoundTag art = NatalTreasureSelf.record(player);
		if (art == null) {
			plan.fail("kultivator.tempa.natal.none");
			line(plan, 0x7A8C83, "kultivator.tempa.hint.spirit");
			return plan;
		}
		int stage = ArtifactItem.spiritStage(art);
		line(plan, 0x8A671B, "kultivator.tempa.preview.spirit_state", "#kultivator.tempa.spirit." + stage,
				String.valueOf((int) ArtifactItem.spiritYears(art)), ArtifactItem.spiritKind(art) == 1 ? "#kultivator.tempa.kind.artificial"
						: "#kultivator.tempa.kind.natural");
		line(plan, 0x465950, NatalTreasureSelf.tempered(player) ? "kultivator.tempa.preview.tempered" : "kultivator.tempa.preview.untempered");
		line(plan, 0x465950, "kultivator.tempa.hint.spirit_actions");
		plan.ok = false;
		return plan;
	}

	/** The Cincin tab: crafting ring tier 1 or upgrading a ring one tier (DESIGN 25.7.2). Instant, always succeeds. */
	private static Plan planRing(ServerPlayer player, ForgeMenu menu, int heat) {
		Plan plan = new Plan();
		plan.kind = KIND_RING;
		plan.title = text("kultivator.tempa.preview.ring");
		SimpleContainer in = menu.input;
		int baseSlot = -1;
		int baseTier = -1;
		for (int i = 0; i < in.getContainerSize(); i++) {
			int tier = StorageRingItem.tierOf(in.getItem(i));
			if (tier > baseTier) {
				baseTier = tier;
				baseSlot = i;
			}
		}
		if (baseTier >= 4) {
			plan.fail("kultivator.tempa.problem.ring_max");
			return plan;
		}
		int target = baseTier >= 1 ? baseTier + 1 : 1;
		plan.ringTier = target;
		plan.ringBase = baseTier >= 0 ? baseSlot : -1;
		line(plan, 0x1B2521, "kultivator.tempa.preview.ring_to", "#item.kultivator." + BuiltinRingNames.NAMES[target]);
		line(plan, 0x465950, "kultivator.tempa.recipe.ring." + target);
		if (baseTier >= 0) {
			UUID owner = StorageRingItem.bondOwner(in.getItem(baseSlot));
			if (owner != null && !owner.equals(player.getUUID())) {
				plan.fail("kultivator.tempa.problem.ring_foreign");
				line(plan, 0x7A8C83, "kultivator.tempa.hint.collapse", String.valueOf(COLLAPSE_STONES));
				return plan;
			}
		}
		int heatNeed = target - 1;
		if (heat < heatNeed) {
			plan.fail("kultivator.tempa.problem.heat", "#kultivator.tempa.heat." + heatNeed);
			return plan;
		}
		int coreNeed = Math.min(BeastCores.KING, target);
		int[] chosen = bestCore(in, stack -> BeastCores.grade(stack) >= coreNeed, true);
		boolean ok = chosen != null;
		switch (target) {
			case 1 -> ok &= Inv.count(in, item(Items.GOLD_INGOT)) >= 2 && stoneCount(in, SpiritStones.LOW) >= 4;
			case 2 -> ok &= baseTier == 1 && Inv.count(in, item(Items.GOLD_BLOCK)) >= 1 && stoneCount(in, SpiritStones.MID) >= 2
					&& Inv.count(in, item(Items.ENDER_PEARL)) >= 1;
			case 3 -> ok &= baseTier == 2 && Inv.count(in, item(Items.DIAMOND)) >= 2 && stoneCount(in, SpiritStones.HIGH) >= 2
					&& Inv.count(in, item(Items.SHULKER_SHELL)) >= 1;
			default -> ok &= baseTier == 3 && Inv.count(in, item(Items.NETHERITE_INGOT)) >= 1 && stoneCount(in, SpiritStones.SUPREME) >= 1
					&& Inv.count(in, stack -> stack.is(TempaItems.SPIRIT_SEED)) >= 1;
		}
		if (!ok) {
			plan.fail("kultivator.tempa.problem.ring_materials");
			return plan;
		}
		Element element = elementOf(chosen[1]);
		plan.takes.add(new Take(core(chosen[0], element), 1, false));
		switch (target) {
			case 1 -> {
				plan.takes.add(new Take(item(Items.GOLD_INGOT), 2, false));
				plan.takes.add(new Take(stone(SpiritStones.LOW), 4, false));
			}
			case 2 -> {
				plan.takes.add(new Take(item(Items.GOLD_BLOCK), 1, false));
				plan.takes.add(new Take(stone(SpiritStones.MID), 2, false));
				plan.takes.add(new Take(item(Items.ENDER_PEARL), 1, false));
			}
			case 3 -> {
				plan.takes.add(new Take(item(Items.DIAMOND), 2, false));
				plan.takes.add(new Take(stone(SpiritStones.HIGH), 2, false));
				plan.takes.add(new Take(item(Items.SHULKER_SHELL), 1, false));
			}
			default -> {
				plan.takes.add(new Take(item(Items.NETHERITE_INGOT), 1, false));
				plan.takes.add(new Take(stone(SpiritStones.SUPREME), 1, false));
				plan.takes.add(new Take(stack -> stack.is(TempaItems.SPIRIT_SEED), 1, false));
			}
		}
		int previous = baseTier >= 1 ? StorageRingItem.extraSlots(in.getItem(baseSlot)) : 0;
		plan.ringExtra = target <= 3 ? Math.max(previous, element == Element.SPACE ? StorageRingItem.SPACE_BONUS : 0) : 0;
		if (element == Element.SPACE && target <= 3) {
			line(plan, Element.SPACE.color(), "kultivator.tempa.preview.ring_space", String.valueOf(StorageRingItem.SPACE_BONUS));
		}
		int slots = Math.min(StorageRingItem.MAX_SLOTS, StorageRingItem.SLOTS[target] + plan.ringExtra);
		line(plan, 0x2E8963, "kultivator.tempa.preview.ring_slots", String.valueOf(slots));
		plan.grade = target - 1;
		plan.chance = 1.0D;
		plan.ok = true;
		return plan;
	}

	// ================================================================== buttons

	static void button(ServerPlayer player, ForgeMenu menu, int id) {
		if (id >= 0 && id <= ForgeMenu.TAB_RING) {
			if (menu.job == null) {
				menu.setTab(id);
				menu.previewDirty = true;
			}
			return;
		}
		switch (id) {
			case ForgeMenu.BTN_START -> start(player, menu);
			case ForgeMenu.BTN_CANCEL -> {
				if (menu.job != null) {
					stop(player, menu, true);
					Msg.actionBar(player, Component.translatable("kultivator.tempa.forge.cancelled").withStyle(ChatFormatting.YELLOW));
				}
			}
			case ForgeMenu.BTN_ASSIST -> assign(player, menu);
			case ForgeMenu.BTN_RELEASE -> release(player, menu);
			case ForgeMenu.BTN_NATAL -> {
				if (menu.job == null) {
					menu.setFlag(ForgeMenu.FLAG_NATAL, !menu.natalTarget());
					menu.previewDirty = true;
				}
			}
			case ForgeMenu.BTN_FEED -> spiritAction(player, menu, () -> WeaponSpirit.feed(player, Inv.first(menu.input, BeastCores::isCore)));
			case ForgeMenu.BTN_SEED -> spiritAction(player, menu, () -> WeaponSpirit.seed(player, Inv.first(menu.input, stack -> stack.is(TempaItems.SPIRIT_SEED))));
			case ForgeMenu.BTN_SOUL -> spiritAction(player, menu, () -> WeaponSpirit.beastSoul(player,
					Inv.first(menu.input, stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) >= BeastCores.KING),
					Inv.first(menu.input, stack -> stack.is(TempaItems.SPIRIT_SEED))));
			case ForgeMenu.BTN_PURIFY -> spiritAction(player, menu, () -> WeaponSpirit.purify(player));
			case ForgeMenu.BTN_COLLAPSE -> collapse(player, menu);
			default -> {
				if (id >= ForgeMenu.BTN_KUALI_FORM && id < ForgeMenu.BTN_KUALI_FORM + 4 && menu.job == null) {
					menu.kualiForm = id - ForgeMenu.BTN_KUALI_FORM;
					menu.previewDirty = true;
				}
			}
		}
	}

	private static void spiritAction(ServerPlayer player, ForgeMenu menu, java.util.function.Supplier<@Nullable Component> action) {
		if (menu.job != null) {
			return;
		}
		Component problem = action.get();
		if (problem != null) {
			Msg.chat(player, problem.copy().withStyle(ChatFormatting.RED));
		}
		menu.input.setChanged();
		menu.previewDirty = true;
	}

	private static void start(ServerPlayer player, ForgeMenu menu) {
		if (menu.job != null) {
			return;
		}
		if (dev.kultivator.alam.Alam.blocked(player)) {
			Msg.chat(player, dev.kultivator.alam.Alam.refusal());
			return;
		}
		Plan plan = plan(player, menu);
		if (!plan.ok) {
			if (plan.problem != null) {
				Msg.actionBar(player, component(plan.problem).copy().withStyle(ChatFormatting.RED));
			}
			return;
		}
		if (plan.kind == KIND_RING) {
			finishRing(player, menu, plan);
			return;
		}
		if (!Qi.trySpend(player, plan.qi)) {
			Msg.actionBar(player, Component.translatable("kultivator.tempa.problem.qi", num(plan.qi)).withStyle(ChatFormatting.RED));
			return;
		}
		Cultivators.dirty(player);
		menu.job = new Job(plan.kind, plan.ticks, plan.qi, plan.heatNeed);
		menu.setFlag(ForgeMenu.FLAG_RUNNING, true);
		menu.setFlag(ForgeMenu.FLAG_PAUSED, false);
		menu.setProgress(0.0F);
		menu.previewDirty = true;
		player.level().playSound(null, menu.pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7F, 0.8F);
	}

	/** Ends the job without a result; {@code refund} returns its qi (closing the menu, cancelling, a long pause). */
	static void stop(ServerPlayer player, ForgeMenu menu, boolean refund) {
		Job job = menu.job;
		menu.job = null;
		if (job != null && refund) {
			Qi.restore(player, job.qi);
			Cultivators.dirty(player);
		}
		menu.setFlag(ForgeMenu.FLAG_RUNNING, false);
		menu.setFlag(ForgeMenu.FLAG_PAUSED, false);
		menu.setProgress(0.0F);
		menu.previewDirty = true;
	}

	/** {@code uji tempa selesai}: finishes the current channel now. */
	static boolean finishNow(ServerPlayer player) {
		if (player.containerMenu instanceof ForgeMenu menu && menu.job != null) {
			menu.job.done = menu.job.ticks;
			return true;
		}
		return false;
	}

	// ================================================================== ticking

	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.containerMenu instanceof ForgeMenu menu) {
				tickMenu(player, menu);
			}
		}
	}

	private static void tickMenu(ServerPlayer player, ForgeMenu menu) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean second = (now + player.getId()) % 20L == 0L;
		if (second || menu.previewDirty) {
			int heat = Heat.read(level, menu.pos, player, hasSeedItem(menu.input)).heat();
			if (heat != menu.heat()) {
				menu.setHeat(heat);
				menu.previewDirty = true;
			}
		}
		Job job = menu.job;
		if (job != null) {
			boolean far = player.distanceToSqr(Vec3.atCenterOf(menu.pos)) > REACH * REACH;
			boolean paused = now < job.pauseUntil || far || menu.heat() < job.heatNeed;
			menu.setFlag(ForgeMenu.FLAG_PAUSED, paused);
			if (paused) {
				job.paused++;
				if (job.paused >= PAUSE_CANCEL) {
					stop(player, menu, true);
					Msg.chat(player, Component.translatable("kultivator.tempa.forge.interrupted").withStyle(ChatFormatting.RED));
					return;
				}
			} else {
				job.paused = 0;
				job.done++;
			}
			menu.setProgress(job.done / (float) job.ticks);
			if (second && !paused) {
				Fx.send(level, Vec3.atCenterOf(menu.pos).add(0.0D, 0.6D, 0.0D), FxType.TEMPA, TempaFxIds.FORGE_WORK, menu.heat(), 0.0F);
			}
			if (job.done >= job.ticks) {
				finish(player, menu, job);
			}
		}
		if (menu.previewDirty) {
			menu.previewDirty = false;
			Tempa.sync(player);
		}
	}

	// ================================================================== finishing

	private static void finish(ServerPlayer player, ForgeMenu menu, Job job) {
		menu.job = null;
		menu.setFlag(ForgeMenu.FLAG_RUNNING, false);
		menu.setFlag(ForgeMenu.FLAG_PAUSED, false);
		menu.setProgress(0.0F);
		menu.previewDirty = true;
		Plan plan = plan(player, menu);
		if (!plan.ok || plan.kind != job.kind) {
			Qi.restore(player, job.qi);
			Msg.chat(player, Component.translatable("kultivator.tempa.forge.changed").withStyle(ChatFormatting.RED));
			return;
		}
		switch (job.kind) {
			case KIND_NEW -> finishNew(player, menu, plan, job);
			case KIND_KUALI -> finishKuali(player, menu, plan);
			case KIND_REFINE -> finishRefine(player, menu, plan);
			default -> {
			}
		}
	}

	private static void consume(ForgeMenu menu, Plan plan, boolean success) {
		for (Take take : plan.takes) {
			if (success || !take.keepOnFail()) {
				Inv.take(menu.input, take.match(), take.count());
			}
		}
	}

	private static void finishNew(ServerPlayer player, ForgeMenu menu, Plan plan, Job job) {
		ServerLevel level = player.level();
		boolean success = level.getRandom().nextDouble() < plan.chance;
		consume(menu, plan, success);
		Vec3 at = Vec3.atCenterOf(menu.pos).add(0.0D, 0.7D, 0.0D);
		Fx.send(level, at, FxType.TEMPA, TempaFxIds.FORGE_DONE, success ? 1 : 0, menu.heat());
		addXp(player, plan.grade, success);
		if (!success) {
			Qi.restore(player, job.qi * 0.5D);
			level.playSound(null, menu.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.7F);
			Msg.chat(player, Component.translatable("kultivator.tempa.forge.failed").withStyle(ChatFormatting.RED));
			return;
		}
		double roll = level.getRandom().nextDouble() + plan.subBonus;
		int sub = roll < SUB_LOW ? 0 : roll < SUB_MID ? 1 : 2;
		ItemStack artifact = ArtifactItem.create(plan.form, plan.grade, sub, 0, plan.element, player.getName().getString());
		Component name = artifact.getHoverName().copy();
		Inv.give(player, artifact);
		level.playSound(null, menu.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.tempa.forge.success", name).withStyle(ChatFormatting.GOLD));
		count(player, "made");
		Advancements.grant(player, "first_forge");
		if (plan.grade >= 3) {
			Advancements.grant(player, "artifact_langit");
		}
		if (plan.grade >= 5) {
			Advancements.grant(player, "artifact_dewa");
		}
	}

	private static void finishKuali(ServerPlayer player, ForgeMenu menu, Plan plan) {
		int slot = kuali(menu.input);
		if (slot < 0) {
			Qi.restore(player, plan.qi);
			return;
		}
		ItemStack cauldron = menu.input.getItem(slot);
		CompoundTag custom = cauldron.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		custom.putBoolean("kultivator_opened", true);
		cauldron.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
		menu.input.setChanged();
		ItemStack embryo = ArtifactItem.create(plan.form, 3, 2, 0, null, player.getName().getString());
		Component name = embryo.getHoverName().copy();
		Inv.give(player, embryo);
		ServerLevel level = player.level();
		Fx.send(level, Vec3.atCenterOf(menu.pos).add(0.0D, 0.8D, 0.0D), FxType.TEMPA, TempaFxIds.KUALI_OPEN, 0, 1.0F);
		level.playSound(null, menu.pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
		Msg.chat(player, Component.translatable("kultivator.tempa.kuali.opened", name).withStyle(ChatFormatting.GOLD));
		addXp(player, 3, true);
		count(player, "made");
		Advancements.grant(player, "cauldron_opened");
		Advancements.grant(player, "first_forge");
		Advancements.grant(player, "artifact_langit");
	}

	private static void finishRefine(ServerPlayer player, ForgeMenu menu, Plan plan) {
		ServerLevel level = player.level();
		boolean success = level.getRandom().nextDouble() < plan.chance;
		CompoundTag art;
		ItemStack stack = ItemStack.EMPTY;
		if (plan.natal) {
			CompoundTag live = NatalTreasureSelf.record(player);
			if (live == null) {
				return;
			}
			art = live.copy();
		} else {
			stack = menu.input.getItem(plan.artifactSlot);
			if (!ArtifactItem.is(stack)) {
				return;
			}
			art = ArtifactItem.data(stack);
		}
		consume(menu, plan, success);
		int refine = ArtifactItem.refine(art);
		int after = success ? refine + 1 : plan.refineTarget >= 4 ? Math.max(0, refine - 1) : refine;
		art.putInt("refine", after);
		if (plan.natal) {
			NatalTreasureSelf.update(player, art);
		} else {
			ArtifactItem.write(stack, art);
			menu.input.setChanged();
		}
		addXp(player, plan.grade, success);
		count(player, "refined");
		Fx.send(level, Vec3.atCenterOf(menu.pos).add(0.0D, 0.7D, 0.0D), FxType.TEMPA, TempaFxIds.REFINE_DONE, success ? 1 : 0, ArtifactItem.tint(art));
		if (success) {
			level.playSound(null, menu.pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
			Msg.chat(player, Component.translatable("kultivator.tempa.refine.success", after).withStyle(ChatFormatting.GOLD));
			if (after >= 9) {
				Advancements.grant(player, "refine_9");
			}
		} else {
			level.playSound(null, menu.pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.7F);
			Msg.chat(player, Component.translatable(after < refine ? "kultivator.tempa.refine.dropped" : "kultivator.tempa.refine.failed", after)
					.withStyle(ChatFormatting.RED));
		}
		Tempa.sync(player);
	}

	private static void finishRing(ServerPlayer player, ForgeMenu menu, Plan plan) {
		ItemStack result;
		if (plan.ringBase >= 0) {
			ItemStack base = menu.input.getItem(plan.ringBase);
			result = new ItemStack(TempaItems.ringItem(plan.ringTier));
			CompoundTag record = StorageRingItem.record(base);
			record.putInt("tier", plan.ringTier);
			record.putInt("extra_slots", plan.ringExtra);
			if (record.getStringOr("ring_id", "").isEmpty()) {
				record.putString("ring_id", UUID.randomUUID().toString());
			}
			result.set(TempaItems.RING, record);
			if (base.has(DataComponents.CONTAINER)) {
				result.set(DataComponents.CONTAINER, base.get(DataComponents.CONTAINER));
			}
			consume(menu, plan, true);
			menu.input.setItem(plan.ringBase, ItemStack.EMPTY);
		} else {
			consume(menu, plan, true);
			result = StorageRingItem.create(plan.ringTier, null);
			CompoundTag record = StorageRingItem.record(result);
			record.putInt("extra_slots", plan.ringExtra);
			result.set(TempaItems.RING, record);
		}
		menu.input.setChanged();
		Component name = result.getHoverName().copy();
		Inv.give(player, result);
		addXp(player, plan.grade, true);
		ServerLevel level = player.level();
		level.playSound(null, menu.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.5F);
		Fx.send(level, Vec3.atCenterOf(menu.pos).add(0.0D, 0.7D, 0.0D), FxType.TEMPA, TempaFxIds.FORGE_DONE, 1, menu.heat());
		Msg.chat(player, Component.translatable("kultivator.tempa.ring.crafted", name).withStyle(ChatFormatting.GOLD));
		if (plan.ringTier >= 4) {
			Advancements.grant(player, "ring_supreme");
		}
		menu.previewDirty = true;
	}

	/** Runtuhkan Ruang (DESIGN 25.7.3): 5 Mid stones erase a foreign imprint and destroy the ring's contents. */
	private static void collapse(ServerPlayer player, ForgeMenu menu) {
		if (menu.job != null) {
			return;
		}
		SimpleContainer in = menu.input;
		int slot = Inv.firstIndex(in, stack -> {
			UUID owner = StorageRingItem.bondOwner(stack);
			return owner != null && !owner.equals(player.getUUID());
		});
		if (slot < 0) {
			Msg.actionBar(player, Component.translatable("kultivator.tempa.problem.no_foreign_ring").withStyle(ChatFormatting.RED));
			return;
		}
		if (stoneCount(in, SpiritStones.MID) < COLLAPSE_STONES) {
			Msg.actionBar(player, Component.translatable("kultivator.tempa.problem.stones", COLLAPSE_STONES, SpiritStones.name(SpiritStones.MID))
					.withStyle(ChatFormatting.RED));
			return;
		}
		Inv.take(in, stone(SpiritStones.MID), COLLAPSE_STONES);
		ItemStack ring = in.getItem(slot);
		StorageRingItem.setOwner(ring, null);
		ring.remove(DataComponents.CONTAINER);
		in.setChanged();
		player.level().playSound(null, menu.pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		Msg.chat(player, Component.translatable("kultivator.tempa.ring.collapsed").withStyle(ChatFormatting.YELLOW));
	}

	// ================================================================== assistant

	/** Tugaskan: the nearest free disciple of the player within 8 blocks becomes the forge assistant (activity 12). */
	private static void assign(ServerPlayer player, ForgeMenu menu) {
		ServerLevel level = player.level();
		DiscipleEntity pick = null;
		double best = Double.MAX_VALUE;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box(menu.pos, 8.0D), entity -> entity.isAlive()
				&& player.getUUID().equals(entity.ownerId()) && entity.role() == DiscipleEntity.Role.DISCIPLE && !entity.departed() && !entity.isReleasing()
				&& (entity.syncedFlags() & DiscipleEntity.FLAG_BREAKING_THROUGH) == 0
				&& (entity.activity() == DiscipleEntity.ACTIVITY_NONE || entity.activity() == DiscipleEntity.ACTIVITY_FORGE_ASSIST))) {
			double distance = disciple.distanceToSqr(Vec3.atCenterOf(menu.pos));
			if (distance < best) {
				best = distance;
				pick = disciple;
			}
		}
		if (pick == null) {
			Msg.actionBar(player, Component.translatable("kultivator.tempa.assist.none").withStyle(ChatFormatting.RED));
			return;
		}
		BlockPos post = post(level, menu.pos);
		pick.setActivity(DiscipleEntity.ACTIVITY_FORGE_ASSIST, post);
		CompoundTag section = pick.data().extraSection(NatalTreasureSelf.DUNIA);
		section.putString("activity", "forge");
		section.putLong("ref", menu.pos.asLong());
		section.putString("dim", level.dimension().identifier().toString());
		Msg.chat(player, Component.translatable("kultivator.tempa.assist.done", pick.getName()).withStyle(ChatFormatting.GREEN));
		menu.previewDirty = true;
	}

	private static void release(ServerPlayer player, ForgeMenu menu) {
		int freed = 0;
		for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, box(menu.pos, 16.0D),
				entity -> entity.activity() == DiscipleEntity.ACTIVITY_FORGE_ASSIST && player.getUUID().equals(entity.ownerId()))) {
			releaseAssistant(disciple);
			freed++;
		}
		Msg.actionBar(player, Component.translatable("kultivator.tempa.assist.released", freed).withStyle(ChatFormatting.YELLOW));
		menu.previewDirty = true;
	}

	private static void releaseAssistant(DiscipleEntity disciple) {
		disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		CompoundTag section = disciple.data().extra().getCompound(NatalTreasureSelf.DUNIA).orElse(null);
		if (section != null && "forge".equals(section.getStringOr("activity", ""))) {
			section.remove("activity");
			section.remove("ref");
			section.remove("dim");
		}
	}

	/** Once a second per disciple: an assistant whose forge is gone (or who took another activity) is freed. */
	private static void checkAssistant(DiscipleEntity disciple) {
		CompoundTag section = disciple.data().extra().getCompound(NatalTreasureSelf.DUNIA).orElse(null);
		boolean recorded = section != null && "forge".equals(section.getStringOr("activity", ""));
		if (disciple.activity() != DiscipleEntity.ACTIVITY_FORGE_ASSIST) {
			if (recorded) {
				section.remove("activity");
				section.remove("ref");
				section.remove("dim");
			}
			return;
		}
		if (!recorded || !(disciple.level() instanceof ServerLevel level)) {
			disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			return;
		}
		BlockPos forge = BlockPos.of(section.getLongOr("ref", 0L));
		if (!level.dimension().identifier().toString().equals(section.getStringOr("dim", "")) || level.isLoaded(forge)
				&& !level.getBlockState(forge).is(TempaItems.EARTHFIRE_FORGE)) {
			releaseAssistant(disciple);
		}
	}

	/** A standing spot within 2 blocks of the forge (air with a solid floor), or the forge's side. */
	private static BlockPos post(ServerLevel level, BlockPos forge) {
		int[][] offsets = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		for (int[] offset : offsets) {
			for (int dy = 0; dy >= -1; dy--) {
				BlockPos spot = forge.offset(offset[0], dy, offset[1]);
				if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && !level.getBlockState(spot.below()).isAir()) {
					return spot;
				}
			}
		}
		return forge.offset(1, 0, 0);
	}

	// ================================================================== preview

	/** The preview card for the open forge menu (part of the tempa sync). */
	static CompoundTag preview(ServerPlayer player, ForgeMenu menu) {
		Plan plan = plan(player, menu);
		CompoundTag tag = new CompoundTag();
		tag.putInt("tab", menu.tab());
		tag.putInt("kind", plan.kind);
		tag.putBoolean("ok", plan.ok);
		tag.put("title", plan.title);
		tag.put("lines", plan.lines);
		if (plan.problem != null) {
			tag.put("problem", plan.problem);
		}
		tag.putFloat("chance", (float) plan.chance);
		tag.putInt("form", menu.kualiForm);
		tag.putBoolean("natal_bound", NatalTreasureSelf.bound(player));
		tag.putDouble("assist", assistBonus(player.level(), menu.pos, player.getUUID()));
		tag.putInt("skill", skillLevel(player));
		return tag;
	}

	// ================================================================== 38EK: shop stock and forge orders (DESIGN 38.M.2.1)

	/** Heat level of a guild forge (Gilda Pandai Pusaka): forge orders are made as at heat 3. */
	public static final int GUILD_HEAT = 3;
	/** Highest grade a guild order may ask for. */
	public static final int ORDER_MAX_GRADE = 4;

	/**
	 * One material line of the forge recipe for a new artifact ({@link #planNew}'s rows, as a guild order lists them):
	 * {@code test} matches the stacks, {@code count} is needed, {@code nameKey} names the line, {@code icon} makes a
	 * display stack on demand (never in a static initializer).
	 */
	public record OrderMaterial(Predicate<ItemStack> test, int count, String nameKey, java.util.function.Supplier<ItemStack> icon) {
	}

	/**
	 * The materials a forge order of {@code form} at {@code grade} hands over (38EK {@code PesananTempa}): the embryo of
	 * the form, the beast core of the grade (two king cores for grade 5), the metal row and the realm materials. The same
	 * rows as the forge itself (no cheaper path through the guild).
	 */
	public static List<OrderMaterial> orderMaterials(String form, int grade) {
		int g = Math.max(0, Math.min(ArtifactItem.MAX_GRADE, grade));
		List<OrderMaterial> list = new ArrayList<>();
		switch (ArtifactItem.FORMS.contains(form) ? form : ArtifactItem.PEDANG) {
			case ArtifactItem.PAYUNG -> {
				list.add(new OrderMaterial(item(Items.BAMBOO_BLOCK), 1, "block.minecraft.bamboo_block", () -> new ItemStack(Items.BAMBOO_BLOCK)));
				list.add(new OrderMaterial(item(Items.STICK), 4, "item.minecraft.stick", () -> new ItemStack(Items.STICK)));
			}
			case ArtifactItem.WAJAN -> list.add(new OrderMaterial(item(Items.CAULDRON), 1, "block.minecraft.cauldron", () -> new ItemStack(Items.CAULDRON)));
			case ArtifactItem.LONCENG -> list.add(new OrderMaterial(item(Items.BELL), 1, "block.minecraft.bell", () -> new ItemStack(Items.BELL)));
			default -> list.add(new OrderMaterial(stack -> stack.is(ItemTags.SWORDS) && !ArtifactItem.is(stack), 1, "kultivator.tempa.order.embryo_sword",
					() -> new ItemStack(Items.IRON_SWORD)));
		}
		if (g == 5) {
			list.add(new OrderMaterial(stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) >= BeastCores.KING, 2, "kultivator.tempa.order.core_king",
					() -> BeastCores.create(null, BeastCores.KING)));
		} else {
			int min = CORE_MIN[g];
			list.add(new OrderMaterial(stack -> BeastCores.isCore(stack) && BeastCores.grade(stack) >= min, 1, "kultivator.tempa.order.core." + min,
					() -> BeastCores.create(null, min)));
		}
		switch (g) {
			case 0 -> list.add(new OrderMaterial(item(Items.IRON_INGOT), 4, "item.minecraft.iron_ingot", () -> new ItemStack(Items.IRON_INGOT)));
			case 1 -> {
				list.add(new OrderMaterial(item(Items.GOLD_INGOT), 4, "item.minecraft.gold_ingot", () -> new ItemStack(Items.GOLD_INGOT)));
				list.add(new OrderMaterial(item(Items.DIAMOND), 1, "item.minecraft.diamond", () -> new ItemStack(Items.DIAMOND)));
			}
			case 2 -> {
				list.add(new OrderMaterial(item(Items.DIAMOND), 2, "item.minecraft.diamond", () -> new ItemStack(Items.DIAMOND)));
				list.add(new OrderMaterial(item(Items.AMETHYST_BLOCK), 2, "block.minecraft.amethyst_block", () -> new ItemStack(Items.AMETHYST_BLOCK)));
			}
			case 3 -> {
				list.add(new OrderMaterial(item(Items.NETHERITE_INGOT), 1, "item.minecraft.netherite_ingot", () -> new ItemStack(Items.NETHERITE_INGOT)));
				list.add(new OrderMaterial(item(Items.DIAMOND), 2, "item.minecraft.diamond", () -> new ItemStack(Items.DIAMOND)));
			}
			case 4 -> {
				list.add(new OrderMaterial(item(Items.NETHERITE_INGOT), 2, "item.minecraft.netherite_ingot", () -> new ItemStack(Items.NETHERITE_INGOT)));
				list.add(new OrderMaterial(item(Items.ECHO_SHARD), 1, "item.minecraft.echo_shard", () -> new ItemStack(Items.ECHO_SHARD)));
			}
			default -> {
				list.add(new OrderMaterial(item(Items.NETHERITE_INGOT), 4, "item.minecraft.netherite_ingot", () -> new ItemStack(Items.NETHERITE_INGOT)));
				list.add(new OrderMaterial(item(Items.NETHER_STAR), 1, "item.minecraft.nether_star", () -> new ItemStack(Items.NETHER_STAR)));
			}
		}
		if (REALM_COUNT[g] > 0) {
			var tag = TempaItems.realmTag(g);
			list.add(new OrderMaterial(stack -> stack.is(tag), REALM_COUNT[g], "kultivator.tempa.order.realm." + g,
					() -> new ItemStack(Items.AMETHYST_SHARD)));
		}
		return list;
	}

	/** A sub-quality roll 0-2 with the forge's odds ({@link #SUB_LOW}, {@link #SUB_MID}) shifted by {@code bonus}. */
	private static int rollSub(net.minecraft.util.RandomSource random, double bonus) {
		double roll = random.nextDouble() + bonus;
		return roll < SUB_LOW ? 0 : roll < SUB_MID ? 1 : 2;
	}

	/**
	 * A forged artifact for a guild shop rack (38EK): {@code form} at {@code grade} (0-5) with a random sub-quality and,
	 * half of the time, a random element; the maker line names the guild ({@code maker}, may be null).
	 */
	public static ItemStack buatStok(ServerLevel level, String form, int grade, net.minecraft.util.RandomSource random) {
		return buatStok(level, form, grade, random, null);
	}

	/** {@link #buatStok(ServerLevel, String, int, net.minecraft.util.RandomSource)} with a maker name. */
	public static ItemStack buatStok(ServerLevel level, String form, int grade, net.minecraft.util.RandomSource random, @Nullable String maker) {
		Element element = random.nextBoolean() ? null : Element.values()[random.nextInt(Element.values().length)];
		return ArtifactItem.create(form, grade, rollSub(random, 0.0D), 0, element, maker);
	}

	/**
	 * The finished artifact of a guild forge order (38EK {@code PesananTempa}): made as at {@link #GUILD_HEAT} by the
	 * master smith; the sub-quality odds get the core bonus of a core one grade above the minimum ({@link #CORE_SUB}).
	 */
	public static ItemStack buatPesanan(ServerLevel level, String form, int grade, @Nullable Element element, String maker,
			net.minecraft.util.RandomSource random) {
		int g = Math.max(0, Math.min(ORDER_MAX_GRADE, grade));
		return ArtifactItem.create(form, g, rollSub(random, CORE_SUB), 0, element, maker);
	}

	/** Item ids of the ring tiers (the preview names them). */
	static final class BuiltinRingNames {
		static final String[] NAMES = {"storage_pouch", "storage_ring_low", "storage_ring_mid", "storage_ring_high", "storage_ring_supreme"};

		private BuiltinRingNames() {
		}
	}

	private Forging() {
	}
}
