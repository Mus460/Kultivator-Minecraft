package dev.kultivator.sect.event;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tugas Sekte and the herb refining (DESIGN 20.3.9, 20.3.11): duty posts 5 blocks N/E/S/W of the stele, a disciple per
 * post looping a cosmetic pose (activity 1-4, rendered by module C). No block or crop is touched: the output is computed
 * lazily from elapsed game time (at most 24,000 ticks banked per post) every 60 s while the hall chunk is loaded and
 * when the hall opens, and goes to the {@link Storehouse}. The disciple's duty is kept in {@code extra.activity.duty}.
 * <p>
 * Sumber: Renegade Immortal (Wang Lin's ten vats of water a day); A Record of a Mortal's Journey ch. 150-151 (the
 * Hundred Medicine Garden); A Will Eternal ch. 2 and 20-26 (the Ovens, the Spirit Tail Chickens, the chicken thief task
 * force).
 */
public final class Duties {

	/** Sub-compound of {@code DiscipleData.extra()} owned by module B. */
	public static final String EXTRA = "activity";

	public static final String WATER = "water";
	public static final String GARDEN = "garden";
	public static final String KITCHEN = "kitchen";
	public static final String FARM = "farm";
	public static final String[] DUTIES = {WATER, GARDEN, KITCHEN, FARM};

	/** Posts stand this many blocks from the stele. */
	public static final int POST_DISTANCE = 5;
	/** Output is computed this often while the hall chunk is loaded (ticks). */
	public static final int COMPUTE_INTERVAL = 1200;
	/** At most this much elapsed time is banked per post (ticks). */
	public static final long MAX_BANKED = 24000L;
	/** Pikul Air: XP per minute, Tubuh Terlatih +1 max health per 60 minutes (max 10), 10 PK per 50 minutes. */
	public static final double WATER_XP_PER_MINUTE = 0.8D;
	public static final double WATER_HP_MINUTES = 60.0D;
	public static final int WATER_HP_MAX = 10;
	public static final double WATER_PK_MINUTES = 50.0D;
	public static final int WATER_PK = 10;
	/** Minutes per item of the garden, the Ovens and the chicken farm. */
	public static final double GARDEN_MINUTES = 4.0D;
	public static final double KITCHEN_MINUTES = 6.0D;
	public static final double FARM_MINUTES = 10.0D;
	/** The farm needs this many chickens within 16 blocks of the stele. */
	public static final int FARM_CHICKENS = 4;
	public static final double FARM_RADIUS = 16.0D;
	/** Murid Tercatat work 1.2 times as much (DESIGN 20.3.6). */
	public static final double REGISTERED_OUTPUT = 1.2D;
	/** A spirit meal fed to a disciple: XP x1.2 for this long (ticks). */
	public static final double MEAL_XP_FACTOR = 1.2D;
	public static final long MEAL_TICKS = 2400L;
	/** Herbs per Foundation pill, and the qualities' weights Low / Mid / High / Perfect. */
	public static final int HERBS_PER_PILL = 3;
	public static final int[] REFINE_WEIGHTS = {30, 45, 20, 5};
	/** Daily chance that a Takut Mati disciple on duty eats up to 3 feathers. */
	public static final double THIEF_CHANCE = 0.05D;
	public static final int THIEF_FEATHERS = 3;
	/**
	 * Talent bits of {@code Talents.talents} (module A) in the order of the DESIGN 20.3.5 table: Hati Gigih 1, Darah Dewa
	 * Kuno 2, Bakat Pil 4, Jari Penempa Roh 8, Takut Mati 16, Indra Pedang 32.
	 */
	public static final int TALENT_PILL_GIFT = 1 << 2;
	public static final int TALENT_FEAR_OF_DEATH = 1 << 4;
	/** Bakat Pil: 25% chance of one quality step more when herbs are refined (DESIGN 20.3.5). */
	public static final double PILL_GIFT_CHANCE = 0.25D;

	private static final Identifier TRAINED_BODY_ID = Kultivator.id("duty_trained_body");
	private static final String POSTS = "posts";
	private static final Item[] GARDEN_GOODS = {Items.GLOW_BERRIES, Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE, Items.SWEET_BERRIES};

	/** Called once from {@link SectEvents#init()}. */
	static void init() {
		// Tubuh Terlatih: the permanent max health of the water duty.
		DiscipleHooks.addStats((entity, sink) -> {
			if (entity.role() == DiscipleEntity.Role.DISCIPLE) {
				int hp = trainedHealth(entity.data());
				if (hp > 0) {
					sink.add(Attributes.MAX_HEALTH, TRAINED_BODY_ID, hp, AttributeModifier.Operation.ADD_VALUE);
				}
			}
		});
		// Hidangan Roh fed to a disciple.
		DiscipleHooks.addFeedHandler(Duties::feedMeal);
		DiscipleHooks.addXpFactor((entity, data, source) -> mealUntil(data) > EventUtil.gameTime() ? MEAL_XP_FACTOR : 1.0D);
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** The disciple's duty id ("water", "garden", "kitchen", "farm"), or null for none. */
	public static @Nullable String dutyOf(DiscipleData data) {
		String duty = data.extra().getCompoundOrEmpty(EXTRA).getStringOr("duty", "");
		return duty.isEmpty() ? null : duty;
	}

	/** Posts of a hall of {@code level}: {@code 1 + floor(level / 3)}. */
	public static int posts(int level) {
		return 1 + Math.max(0, level) / 3;
	}

	/** The activity id of a duty (1 water, 2 garden, 3 kitchen, 4 farm), 0 for none. */
	public static int activityOf(@Nullable String duty) {
		if (duty == null) {
			return DiscipleEntity.ACTIVITY_NONE;
		}
		return switch (duty) {
			case WATER -> DiscipleEntity.ACTIVITY_WATER;
			case GARDEN -> DiscipleEntity.ACTIVITY_GARDEN;
			case KITCHEN -> DiscipleEntity.ACTIVITY_KITCHEN;
			case FARM -> DiscipleEntity.ACTIVITY_FARM;
			default -> DiscipleEntity.ACTIVITY_NONE;
		};
	}

	static boolean valid(String duty) {
		return activityOf(duty) != DiscipleEntity.ACTIVITY_NONE;
	}

	static int trainedHealth(DiscipleData data) {
		return Math.max(0, Math.min(WATER_HP_MAX, data.extra().getCompoundOrEmpty(EXTRA).getIntOr("water_hp", 0)));
	}

	static long mealUntil(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getLongOr("meal_until", 0L);
	}

	// ================================================================== assigning

	/** Puts {@code disciple} on a post with {@code duty} (or changes its duty). False with a chat reason. */
	static boolean assign(ServerPlayer player, DiscipleEntity disciple, String duty) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = hall == null ? null : EventUtil.hallLevel(hall);
		if (hall == null || !hall.active() || level == null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!valid(duty) || !EventUtil.free(disciple) || !EventUtil.nearHall(disciple, hall, 64.0D)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.not_here", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		CompoundTag posts = EventUtil.sub(EventUtil.section(owner), POSTS);
		String soul = disciple.soulId().toString();
		String key = postOf(posts, soul);
		if (key == null) {
			int max = posts(Halls.level(owner));
			for (int i = 0; i < Math.min(4, max); i++) {
				if (!posts.contains(String.valueOf(i))) {
					key = String.valueOf(i);
					break;
				}
			}
			if (key == null) {
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.full", max).withStyle(ChatFormatting.YELLOW));
				return false;
			}
		}
		CompoundTag post = EventUtil.sub(posts, key);
		BlockPos spot = EventUtil.pos(post, "spot");
		if (spot == null) {
			spot = postSpot(level, disciple, hall.pos(), Integer.parseInt(key));
			if (spot == null) {
				posts.remove(key);
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.blocked").withStyle(ChatFormatting.YELLOW));
				return false;
			}
			post.putLong("spot", spot.asLong());
		}
		boolean fresh = !post.contains("soul");
		post.putString("soul", soul);
		post.putString("duty", duty);
		if (fresh) {
			post.putLong("last", EventUtil.gameTime());
		}
		EventUtil.extra(disciple.data()).putString("duty", duty);
		disciple.setActivity(activityOf(duty), spot);
		EventUtil.dirty();
		EventUtil.fx(level, Vec3.atBottomCenterOf(spot), SectEventFxIds.DUTY_PUFF, activityOf(duty), 0.0F);
		level.playSound(null, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 0.6F, 1.1F);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.assigned", disciple.getDisplayName(),
				Component.translatable("kultivator.sectevent.duty." + duty)).withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Takes the disciple off its post (also when it is not loaded: by soul). */
	static void release(UUID owner, UUID soul, @Nullable ServerPlayer tell) {
		CompoundTag posts = EventUtil.sub(EventUtil.section(owner), POSTS);
		String key = postOf(posts, soul.toString());
		if (key != null) {
			posts.remove(key);
			EventUtil.dirty();
		}
		DiscipleEntity disciple = EventUtil.bySoul(owner, soul);
		if (disciple != null) {
			EventUtil.extra(disciple.data()).remove("duty");
			if (disciple.activity() >= DiscipleEntity.ACTIVITY_WATER && disciple.activity() <= DiscipleEntity.ACTIVITY_FARM) {
				disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			if (tell != null) {
				EventUtil.chat(tell, Component.translatable("kultivator.sectevent.duty.released", disciple.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** True when the master's disciple list holds this soul as living (read only). */
	private static boolean listedAlive(ServerPlayer master, UUID soul) {
		String id = soul.toString();
		CompoundTag root = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA);
		for (Tag tag : root.getListOrEmpty("list")) {
			if (tag instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				return !entry.getBooleanOr("dead", false);
			}
		}
		return false;
	}

	private static @Nullable String postOf(CompoundTag posts, String soul) {
		for (String key : posts.keySet()) {
			if (soul.equals(posts.getCompoundOrEmpty(key).getStringOr("soul", ""))) {
				return key;
			}
		}
		return null;
	}

	/** A safe standing spot near post {@code index} (0 N, 1 E, 2 S, 3 W of the stele), read-only; null when none. */
	private static @Nullable BlockPos postSpot(ServerLevel level, DiscipleEntity disciple, BlockPos stele, int index) {
		int dx = index == 1 ? POST_DISTANCE : index == 3 ? -POST_DISTANCE : 0;
		int dz = index == 0 ? -POST_DISTANCE : index == 2 ? POST_DISTANCE : 0;
		Vec3 target = new Vec3(stele.getX() + dx + 0.5D, stele.getY(), stele.getZ() + dz + 0.5D);
		if (!level.isLoaded(BlockPos.containing(target))) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, disciple, target);
		return spot == null ? null : BlockPos.containing(spot);
	}

	// ================================================================== output

	/**
	 * Works out every post's output since it was last computed (at most 24,000 ticks): XP, Tubuh Terlatih, PK and Poin
	 * Tempa of the water duty, garden goods, spirit meals and feathers into the storehouse. A post whose disciple is not
	 * loaded keeps banking; a disciple called away from its post (activity cleared) leaves the post.
	 */
	static void compute(UUID owner, Halls.HallView hall) {
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level == null || !hall.active()) {
			return;
		}
		CompoundTag posts = EventUtil.sub(EventUtil.section(owner), POSTS);
		if (posts.isEmpty()) {
			return;
		}
		long now = EventUtil.gameTime();
		ServerPlayer player = EventUtil.player(owner);
		List<Component> lost = new ArrayList<>();
		RandomSource random = level.getRandom();
		for (String key : List.copyOf(posts.keySet())) {
			CompoundTag post = posts.getCompoundOrEmpty(key);
			UUID soul = EventUtil.uuid(post.getStringOr("soul", ""));
			String duty = post.getStringOr("duty", "");
			if (soul == null || !valid(duty)) {
				posts.remove(key);
				continue;
			}
			long last = post.getLongOr("last", now);
			long elapsed = Math.min(MAX_BANKED, Math.max(0L, now - last));
			DiscipleEntity disciple = EventUtil.bySoul(owner, soul);
			if (disciple == null) {
				if (player != null && !listedAlive(player, soul)) {
					// Fallen, forgotten or released for good: the post is free again.
					posts.remove(key);
					continue;
				}
				// Not loaded (another dimension, an errand): the post keeps banking, at most a day.
				if (now - last > MAX_BANKED) {
					post.putLong("last", now - MAX_BANKED);
				}
				continue;
			}
			if (disciple.isReleasing()) {
				posts.remove(key);
				EventUtil.extra(disciple.data()).remove("duty");
				continue;
			}
			if (disciple.activity() != activityOf(duty)) {
				// Called away ("murid panggil") or given another task: the post is free again.
				posts.remove(key);
				EventUtil.extra(disciple.data()).remove("duty");
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.left", disciple.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			post.putLong("last", now);
			if (elapsed <= 0L) {
				continue;
			}
			DiscipleData data = disciple.data();
			double minutes = elapsed / 1200.0D * (SectRanks.rank(data) == 0 ? REGISTERED_OUTPUT : 1.0D);
			switch (duty) {
				case WATER -> water(disciple, data, minutes);
				case GARDEN -> {
					double factor = (hasElement(data, Element.WOOD) || data.dao() == Dao.LIFE ? 1.5D : 1.0D)
							* (Halls.level(owner) >= 5 ? 1.25D : 1.0D);
					int count = progress(post, minutes / GARDEN_MINUTES * factor);
					for (int i = 0; i < count; i++) {
						store(owner, new ItemStack(GARDEN_GOODS[random.nextInt(GARDEN_GOODS.length)]), lost);
					}
				}
				case KITCHEN -> {
					double factor = hasElement(data, Element.FIRE) || data.dao() == Dao.FIRE ? 1.5D : 1.0D;
					int count = progress(post, minutes / KITCHEN_MINUTES * factor);
					if (count > 0) {
						store(owner, new ItemStack(SectEventItems.SPIRIT_MEAL, count), lost);
					}
				}
				case FARM -> {
					if (chickens(level, hall.pos()) >= FARM_CHICKENS) {
						int count = progress(post, minutes / FARM_MINUTES);
						if (count > 0) {
							store(owner, new ItemStack(SectEventItems.SPIRIT_TAIL_FEATHER, count), lost);
						}
					}
				}
				default -> {
				}
			}
			BlockPos spot = disciple.activitySpot();
			if (spot != null) {
				EventUtil.fx(level, Vec3.atBottomCenterOf(spot), SectEventFxIds.DUTY_PUFF, activityOf(duty), 0.0F);
			}
			thief(owner, disciple, data, player, random);
		}
		EventUtil.dirty();
		if (!lost.isEmpty() && player != null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.store.full", lost.get(0)).withStyle(ChatFormatting.RED));
		}
	}

	private static void water(DiscipleEntity disciple, DiscipleData data, double minutes) {
		DiscipleCultivation.addXp(disciple, WATER_XP_PER_MINUTE * minutes, XpSource.SECT);
		CompoundTag extra = EventUtil.extra(data);
		double total = extra.getDoubleOr("water_min", 0.0D) + minutes;
		extra.putDouble("water_min", total);
		int hp = Math.min(WATER_HP_MAX, (int) Math.floor(total / WATER_HP_MINUTES));
		boolean refresh = false;
		if (hp > extra.getIntOr("water_hp", 0)) {
			extra.putInt("water_hp", hp);
			refresh = true;
		}
		double pkMinutes = extra.getDoubleOr("water_pk", 0.0D) + minutes;
		while (pkMinutes >= WATER_PK_MINUTES) {
			pkMinutes -= WATER_PK_MINUTES;
			SectRanks.addPk(data, WATER_PK, "kultivator.sectevent.pk.water");
		}
		extra.putDouble("water_pk", pkMinutes);
		double temper = extra.getDoubleOr("temper", 0.0D) + minutes;
		int points = (int) Math.floor(temper);
		if (points > 0) {
			DisciplePowers.addTempering(data, points);
			temper -= points;
		}
		extra.putDouble("temper", temper);
		if (refresh) {
			disciple.refreshSynced();
		}
	}

	/** Adds {@code amount} to the post's fractional progress and returns the whole items finished. */
	private static int progress(CompoundTag post, double amount) {
		double total = post.getDoubleOr("prog", 0.0D) + Math.max(0.0D, amount);
		int whole = (int) Math.min(512, Math.floor(total));
		post.putDouble("prog", total - whole);
		return whole;
	}

	private static void store(UUID owner, ItemStack stack, List<Component> lost) {
		ItemStack rest = Storehouse.insert(owner, stack);
		if (!rest.isEmpty()) {
			lost.add(rest.getHoverName());
		}
	}

	static boolean hasElement(DiscipleData data, Element element) {
		return (data.rootElements() & element.bit()) != 0;
	}

	/** Chickens within 16 blocks of the stele (read-only count). */
	static int chickens(ServerLevel level, BlockPos stele) {
		AABB box = new AABB(stele).inflate(FARM_RADIUS);
		return level.getEntitiesOfClass(Chicken.class, box, chicken -> chicken.isAlive()
				&& chicken.distanceToSqr(Vec3.atCenterOf(stele)) <= FARM_RADIUS * FARM_RADIUS).size();
	}

	/** The chicken thief task force: a Takut Mati disciple on duty may eat feathers once a day (5%). */
	private static void thief(UUID owner, DiscipleEntity disciple, DiscipleData data, @Nullable ServerPlayer player, RandomSource random) {
		if ((Talents.talents(data) & TALENT_FEAR_OF_DEATH) == 0) {
			return;
		}
		CompoundTag extra = EventUtil.extra(data);
		long day = EventUtil.day();
		if (extra.getLongOr("thief_day", -1L) == day) {
			return;
		}
		extra.putLong("thief_day", day);
		if (random.nextDouble() >= THIEF_CHANCE) {
			return;
		}
		int eaten = Storehouse.remove(owner, stack -> stack.is(SectEventItems.SPIRIT_TAIL_FEATHER), 1 + random.nextInt(THIEF_FEATHERS));
		if (eaten > 0 && player != null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.duty.thief", eaten).withStyle(ChatFormatting.GOLD));
			Advancements.grant(player, "chicken_task_force");
		}
	}

	// ================================================================== spirit meal and herbs

	private static boolean feedMeal(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		if (!stack.is(SectEventItems.SPIRIT_MEAL) || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return false;
		}
		stack.consume(1, master);
		EventUtil.extra(entity.data()).putLong("meal_until", EventUtil.gameTime() + MEAL_TICKS);
		if (entity.level() instanceof ServerLevel level) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.1F);
			EventUtil.fx(level, entity.position().add(0.0D, 1.2D, 0.0D), SectEventFxIds.DUTY_PUFF, DiscipleEntity.ACTIVITY_KITCHEN, 0.0F);
		}
		EventUtil.chat(master, Component.translatable("kultivator.sectevent.meal.fed", entity.getDisplayName(), (int) (MEAL_TICKS / 1200L))
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Herba Fondasi in the storehouse and the master's inventory. */
	static int herbs(UUID owner, @Nullable ServerPlayer player) {
		int count = Storehouse.count(owner, SectEventItems.FOUNDATION_HERB);
		if (player != null) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(SectEventItems.FOUNDATION_HERB)) {
					count += stack.getCount();
				}
			}
		}
		return count;
	}

	/**
	 * Murnikan Herba: every 3 herbs (storehouse first, then the master's inventory) become one Foundation Establishment
	 * Pill, Low 30 / Mid 45 / High 20 / Perfect 5; leftovers stay. A Bakat Pil disciple in the territory gives each pill a
	 * 25% chance of one quality step more.
	 */
	static void refine(ServerPlayer player) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int sets = herbs(owner, player) / HERBS_PER_PILL;
		if (sets <= 0) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.refine.none", HERBS_PER_PILL).withStyle(ChatFormatting.YELLOW));
			return;
		}
		boolean gift = false;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if ((Talents.talents(disciple.data()) & TALENT_PILL_GIFT) != 0 && Halls.inTerritory(owner, disciple.level(), disciple.position())) {
				gift = true;
				break;
			}
		}
		int need = sets * HERBS_PER_PILL;
		int taken = Storehouse.remove(owner, stack -> stack.is(SectEventItems.FOUNDATION_HERB), need);
		for (int i = 0; i < player.getInventory().getContainerSize() && taken < need; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(SectEventItems.FOUNDATION_HERB)) {
				int take = Math.min(need - taken, stack.getCount());
				stack.shrink(take);
				taken += take;
			}
		}
		player.getInventory().setChanged();
		RandomSource random = player.getRandom();
		List<Component> made = new ArrayList<>();
		int best = 0;
		for (int i = 0; i < taken / HERBS_PER_PILL; i++) {
			int quality = rollQuality(random);
			if (gift && quality < 3 && random.nextDouble() < PILL_GIFT_CHANCE) {
				quality++;
			}
			best = Math.max(best, quality);
			PillQuality pill = PillQuality.byOrdinal(quality);
			EventUtil.give(player, AlchemyComponents.withQuality(new ItemStack(AlchemyItems.FOUNDATION_PILL), pill));
			made.add(pill.displayName());
		}
		ServerLevel level = player.level();
		EventUtil.fx(level, player.position().add(0.0D, 1.0D, 0.0D), SectEventFxIds.REFINE, PillQuality.byOrdinal(best).color(), 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.2F);
		Component list = made.isEmpty() ? Component.empty() : made.get(0).copy();
		for (int i = 1; i < made.size(); i++) {
			list = Component.empty().append(list).append(Component.literal(", ")).append(made.get(i));
		}
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.refine.done", made.size(), list).withStyle(ChatFormatting.GREEN));
	}

	private static int rollQuality(RandomSource random) {
		int total = 0;
		for (int weight : REFINE_WEIGHTS) {
			total += weight;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < REFINE_WEIGHTS.length; i++) {
			roll -= REFINE_WEIGHTS[i];
			if (roll < 0) {
				return i;
			}
		}
		return 1;
	}

	private Duties() {
	}
}
