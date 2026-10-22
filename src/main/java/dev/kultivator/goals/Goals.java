package dev.kultivator.goals;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Stars;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.util.KillCredit;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * P7-jalan (DESIGN 19.3.9, 19.3.10): the Jalan Kultivasi tablet with Langkah Pertama ({@link Guide}), Ujian Fondasi
 * ({@link RealmTrials}) and Kewajiban Harian ({@link DailyChores}), their counters ({@link GoalCounters}) and the Persiapan
 * data ({@link Prep}). Pure player data in {@code extra.goals}; checks every 20/100 ticks plus one kill and one
 * block-break listener. {@link #init()} runs before {@code ModItems.init()} and never reads {@code TechniqueRegistry}.
 */
public final class Goals {

	/** {@code sessionExtra.hud_demo}: the game time until which {@code uji hud} shows sample HUD values. */
	public static final String HUD_DEMO = "hud_demo";
	public static final int HUD_DEMO_TICKS = 200;

	/**
	 * Phase-8 lifetime counters of the glue (DESIGN 25.8.3) in {@code extra.goals.row4}: rogue cultivators slain (any credit
	 * kind), village requests finished, secret realm clears, demonic elders (tier 3) slain during a live sect raid.
	 */
	public static final String WORLD = "row4";
	public static final String W_ROGUES = "rk";
	public static final String W_REQUESTS = "vq";
	public static final String W_CLEARS = "rc";
	public static final String W_ELDERS = "tt";
	/**
	 * Phase-9 lifetime counters of the glue (DESIGN 26.9, P9-batin) in the same {@code extra.goals.row4}: Alam Abadi natives
	 * slain (any {@code kultivator.abadi_foe} that is not a boss, killed in Alam Abadi) and inner-world plots harvested.
	 */
	public static final String W_NATIVES = "an";
	public static final String W_PLOTS = "bh";
	/** Star condensing sink (DESIGN 25.8.4): each Esensi Energi Makam carried lowers the star's cost by 2%, at most 10. */
	public static final double ESSENCE_DISCOUNT = 0.02D;
	public static final int ESSENCE_MAX = 10;
	/**
	 * Audit T15: before Ascension the discount never uses the Esensi the Ascension offering needs (the same 9 as
	 * {@code abadi.Ascend.OFFER_ESSENCE}, package-private there); only what is carried above it counts.
	 */
	public static final int ESSENCE_RESERVE = 9;
	/** {@code extra.goals.star_discount_off}: the player switched the star discount (Esensi and Batu Asal) off. */
	public static final String STAR_DISCOUNT_OFF = "star_discount_off";

	public static void init() {
		Advancements.addListener(Guide::onAdvancement);
		RealmTrials.init();
		ServerPlayerEvents.JOIN.register(player -> {
			CultivationData data = Cultivators.get(player);
			Guide.backfill(player, data);
			RealmTrials.update(player, data);
			DailyChores.update(player, data);
		});
		ServerPlayerEvents.LEAVE.register(GoalCounters::forget);
		ServerLivingEntityEvents.AFTER_DEATH.register(GoalCounters::onDeath);
		PlayerBlockBreakEvents.AFTER.register(GoalCounters::onBlockBroken);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GoalCounters.clear());
		// Phase 8 glue (DESIGN 25.8): the world counters and the star condensing sink.
		ServerLivingEntityEvents.AFTER_DEATH.register(Goals::onWorldKill);
		Alam.addClearListener((player, id, layer, first) -> addWorldCounter(player, W_CLEARS, 1));
		Stars.addCostModifier(player -> 1.0D - ESSENCE_DISCOUNT * discountEssence(player));
		Stars.addCondenseListener((player, stars) -> takeEssence(player, discountEssence(player)));
	}

	/** A rogue cultivator died: the credited player's rogue counter, and the elder counter during a live sect raid. */
	private static void onWorldKill(LivingEntity victim, DamageSource source) {
		if (victim.level().isClientSide()) {
			return;
		}
		// Phase 9 (DESIGN 26.9): an Alam Abadi native (an abadi_foe that is not a boss, slain in Alam Abadi).
		if (victim.entityTags().contains(dev.kultivator.technique.spec.TechniquePower.TAG_ABADI_FOE) && !victim.entityTags().contains("kultivator.boss")
				&& dev.kultivator.abadi.Abadi.isImmortalLevel(victim.level())) {
			KillCredit.Credit nativeCredit = KillCredit.resolve(victim, source);
			if (nativeCredit != null) {
				addWorldCounter(nativeCredit.owner(), W_NATIVES, 1);
			}
		}
		if (!RogueCultivators.isRogue(victim)) {
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null) {
			return;
		}
		ServerPlayer player = credit.owner();
		addWorldCounter(player, W_ROGUES, 1);
		if (RogueCultivators.tier(victim) == 3 && SectRaids.active(player.getUUID())) {
			addWorldCounter(player, W_ELDERS, 1);
		}
	}

	/** Adds to a phase-8 world counter (written silently; the trials and chores read it on their next pass). */
	public static void addWorldCounter(ServerPlayer player, String key, int amount) {
		CompoundTag world = GoalCounters.section(GoalCounters.goals(Cultivators.get(player)), WORLD);
		world.putInt(key, Math.max(0, world.getIntOr(key, 0)) + amount);
	}

	/** A phase-8 world counter (pure). */
	public static int worldCounter(CultivationData data, String key) {
		return GoalCounters.view(data).getCompoundOrEmpty(WORLD).getIntOr(key, 0);
	}

	private static Item essenceItem() {
		return BuiltInRegistries.ITEM.getValue(Kultivator.id("tomb_essence"));
	}

	/** Esensi Energi Makam in the player's inventory (0 while module A's item is not registered). */
	static int essence(ServerPlayer player) {
		Item item = essenceItem();
		if (item == Items.AIR) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** True unless the player switched the star discount off ({@code /kultivasi diskonbintang mati}). Batu Asal follow it too. */
	public static boolean starDiscountOn(ServerPlayer player) {
		return !GoalCounters.view(Cultivators.get(player)).getBooleanOr(STAR_DISCOUNT_OFF, false);
	}

	/** The Esensi the next star's discount uses: 0 when switched off, else what is carried above the reserve (before Ascension), at most 10. */
	static int discountEssence(ServerPlayer player) {
		if (!starDiscountOn(player)) {
			return 0;
		}
		int have = essence(player);
		if (!dev.kultivator.abadi.Abadi.ascended(player)) {
			have -= ESSENCE_RESERVE;
		}
		return Math.max(0, Math.min(ESSENCE_MAX, have));
	}

	/** {@code /kultivasi diskonbintang}: the switch and a preview of what the next star would use (audit T15). */
	private static int starDiscount(ServerPlayer player, @org.jetbrains.annotations.Nullable Boolean on) {
		CultivationData data = Cultivators.get(player);
		if (on != null) {
			if (on) {
				GoalCounters.goals(data).remove(STAR_DISCOUNT_OFF);
			} else {
				GoalCounters.goals(data).putBoolean(STAR_DISCOUNT_OFF, true);
			}
			Cultivators.dirty(player);
		}
		boolean enabled = starDiscountOn(player);
		int essence = discountEssence(player);
		int stones = enabled ? Math.min(dev.kultivator.abadi.OriginStones.STAR_STONES_MAX, dev.kultivator.dunia.kota.NilaiBatu.origin(player)) : 0;
		boolean ascended = dev.kultivator.abadi.Abadi.ascended(player);
		Msg.chat(player, Component.translatable(enabled ? "kultivator.ekonomi.bintang.hidup" : "kultivator.ekonomi.bintang.mati")
				.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
		Msg.chat(player, Component.translatable("kultivator.ekonomi.bintang.pratinjau", essence, Math.round(ESSENCE_DISCOUNT * 100.0D * essence),
				stones, Math.round(dev.kultivator.abadi.OriginStones.STAR_DISCOUNT * 100.0D * stones)).withStyle(net.minecraft.ChatFormatting.GRAY));
		if (!ascended) {
			Msg.chat(player, Component.translatable("kultivator.ekonomi.bintang.sisih", ESSENCE_RESERVE).withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		return 1;
	}

	private static void takeEssence(ServerPlayer player, int amount) {
		Item item = essenceItem();
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0 && item != Items.AIR; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		if (amount > 0) {
			Msg.chat(player, Component.translatable("kultivator.jalan8.essence_used", amount - left,
					Math.round(ESSENCE_DISCOUNT * 100.0D * (amount - left))).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** Every server tick for each player: counters every 20 ticks, the guide, trials, chores and Persiapan every 100. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 20 == 7) {
			GoalCounters.second(player, data);
		}
		if (player.tickCount % 100 == 13) {
			GoalCounters.every100(player, data);
			Guide.check(player, data);
			boolean changed = RealmTrials.update(player, data);
			changed |= DailyChores.update(player, data);
			changed |= Prep.update(player, data);
			if (changed) {
				Cultivators.dirty(player);
			}
		}
	}

	/** Action {@code JALAN}: text = "hide", "show", "claim:N" (N = chapter 1-15) or "seen:codex" (DESIGN 19.2.1). */
	public static void handleAction(ServerPlayer player, String text) {
		CultivationData data = Cultivators.get(player);
		CompoundTag goals = GoalCounters.goals(data);
		if ("hide".equals(text) || "show".equals(text)) {
			goals.putBoolean(Guide.HIDDEN, "hide".equals(text));
			Cultivators.dirty(player);
		} else if ("seen:codex".equals(text)) {
			if (!goals.getBooleanOr(Guide.SEEN, false)) {
				goals.putBoolean(Guide.SEEN, true);
				Cultivators.dirty(player);
				Guide.check(player, data);
			}
		} else if (text != null && text.startsWith("claim:")) {
			try {
				Guide.claim(player, Integer.parseInt(text.substring("claim:".length())));
			} catch (NumberFormatException ignored) {
				// A malformed claim from a modified client: nothing to do.
			}
		}
	}

	/** {@code /kultivasi jalan} (opens the Jalan tab, DESIGN 19.2.2) and {@code harian} (today's chores in chat). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("jalan").executes(ctx -> {
			Net.send(ctx.getSource().getPlayerOrException(), new Payloads.OpenScreen(Payloads.OpenScreen.INFO, Payloads.OpenScreen.TAB_JALAN));
			return 1;
		}));
		root.then(Commands.literal("harian").executes(ctx -> DailyChores.print(ctx.getSource().getPlayerOrException())));
		// Audit T15: the star discount switch (Esensi Energi Makam and Batu Asal) with a preview of the next star.
		root.then(Commands.literal("diskonbintang").executes(ctx -> starDiscount(ctx.getSource().getPlayerOrException(), null))
				.then(Commands.literal("hidup").executes(ctx -> starDiscount(ctx.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("mati").executes(ctx -> starDiscount(ctx.getSource().getPlayerOrException(), false))));
	}

	/** {@code uji jalan reset|bab|ujian}, {@code uji harian ulang|selesai|beruntun} and {@code uji hud} (DESIGN 19.4). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("jalan")
				.then(Commands.literal("reset").executes(Goals::reset))
				.then(Commands.literal("bab")
						// Phase 9 (DESIGN 26.14 item 18): 1-15; the glue (P9-batin) completes chapters 11-15 in guide3.
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 15))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Guide.completeUpTo(player, IntegerArgumentType.getInteger(ctx, "n"));
									return ok(ctx, Component.translatable("kultivator.goals.test.chapters", IntegerArgumentType.getInteger(ctx, "n")));
								})))
				.then(Commands.literal("ujian")
						.executes(ctx -> RealmTrials.print(ctx.getSource().getPlayerOrException()))
						.then(Commands.literal("selesai").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!RealmTrials.completeAll(player)) {
								ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.goals.trial.none")));
								return 0;
							}
							return ok(ctx, Component.translatable("kultivator.goals.test.trials"));
						}))));
		uji.then(Commands.literal("harian")
				.then(Commands.literal("ulang").executes(ctx -> {
					DailyChores.reroll(ctx.getSource().getPlayerOrException());
					return ok(ctx, Component.translatable("kultivator.goals.test.daily_reroll"));
				}))
				.then(Commands.literal("selesai").executes(ctx -> {
					DailyChores.completeAll(ctx.getSource().getPlayerOrException());
					return ok(ctx, Component.translatable("kultivator.goals.test.daily_done"));
				}))
				.then(Commands.literal("beruntun")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, 1000))
								.executes(ctx -> {
									int n = IntegerArgumentType.getInteger(ctx, "n");
									DailyChores.setStreak(ctx.getSource().getPlayerOrException(), n);
									return ok(ctx, Component.translatable("kultivator.goals.test.streak", n));
								}))));
		uji.then(Commands.literal("hud").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			CultivationData data = Cultivators.get(player);
			data.sessionExtra().putLong(HUD_DEMO, player.level().getGameTime() + HUD_DEMO_TICKS);
			Cultivators.syncNow(player);
			return ok(ctx, Component.translatable("kultivator.goals.test.hud"));
		}).then(Commands.literal("dunia").executes(ctx -> {
			dev.kultivator.command.HudDunia.show(ctx.getSource().getPlayerOrException());
			return ok(ctx, Component.translatable("kultivator.jalan8.test.hud_dunia"));
		})));
	}

	/** {@code uji jalan reset}: forgets the guide, the trials and the chores (the lifetime counters stay). */
	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CultivationData data = Cultivators.get(player);
		CompoundTag goals = GoalCounters.goals(data);
		for (String key : new String[]{Guide.KEY, Guide.KEY2, Guide.KEY3, Guide.CLAIMED, Guide.HIDDEN, Guide.SEEN, Guide.ABADI_TITLE, RealmTrials.KEY,
				DailyChores.KEY}) {
			goals.remove(key);
		}
		Cultivators.syncNow(player);
		return ok(ctx, Component.translatable("kultivator.goals.test.reset"));
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, Component message) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(message), false);
		return 1;
	}

	private Goals() {
	}
}
