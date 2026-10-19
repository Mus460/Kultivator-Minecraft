package dev.kultivator.cultivation;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.Stars;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sparring;
import dev.kultivator.talisman.Inscribe;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.talisman.Talismans;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * P7-kultivasi (DESIGN 19.3.11): Anugerah Kenaikan, the Immortal Ascension boons ("Urat Abadi terbuka"), and the
 * player's Bintang Abadi; also the entry of Perkokoh Fondasi ({@link Reforge}), the Cave Abode ({@link CaveAbode}) and
 * the vein compass ({@link VeinCompassItem}), with their commands and test commands. A boon is active when realm =
 * Immortal Ascension with layer ≥ n − 1, or realm = True Immortal. The attribute boons are applied by {@code Stats.apply},
 * L4 and L6 by {@code Qi}, L8 by {@code Meditation}; L7 is a {@code DeathGuard} PERK guard and L9 a strike factor.
 * <p>
 * Sumber (the system): ISSTH Book 6 ch. 963–983, at Immortal Ascension the Door of Immortality opens and Immortal
 * meridians open with it. Stars: BTTH Dou grades in 1–9 stars; <i>terinspirasi dari</i> the Ancient God stars of RI ch.
 * ~1157–1159. Every effect and number is game design.
 */
public final class ImmortalPerks {

	/** The nine boons, one per Immortal Ascension layer (lang {@code kultivator.kultivasi.boon.<id>}). */
	public enum Boon {
		BREATH(1, "breath"),
		STEP(2, "step"),
		JADE(3, "jade"),
		SEA(4, "sea"),
		REACH(5, "reach"),
		MERIDIANS(6, "meridians"),
		SOUL(7, "soul"),
		MIND(8, "mind"),
		DOOR(9, "door");

		private final int layer;
		private final String id;

		Boon(int layer, String id) {
			this.layer = layer;
			this.id = id;
		}

		public int layer() {
			return layer;
		}

		public String id() {
			return id;
		}

		public Component displayName() {
			return Component.translatable("kultivator.kultivasi.boon." + id);
		}

		public Component description() {
			return Component.translatable("kultivator.kultivasi.boon." + id + ".desc");
		}

		public Component source() {
			return Component.translatable("kultivator.kultivasi.boon." + id + ".source");
		}

		public static Boon ofLayer(int layer) {
			return values()[Math.max(1, Math.min(9, layer)) - 1];
		}
	}

	/** L7 Jiwa Tak Hancur: once per this many game ticks (one game day). */
	public static final long UNBROKEN_COOLDOWN = 24000L;
	/** L7: Absorption worth this share of max health, for 5 s, and Resistance III for 3 s. */
	public static final double UNBROKEN_ABSORPTION_SHARE = 0.5D;
	public static final int UNBROKEN_ABSORPTION_TICKS = 100;
	public static final int UNBROKEN_RESISTANCE_TICKS = 60;
	/** L9 Gerbang Keabadian: the True Immortal tribulation's strikes. */
	public static final double DOOR_STRIKE_FACTOR = 0.85D;
	/** Stars at which Gerbang Dewa I and II open for the player's own learning. */
	public static final int GATE_I_STARS = 9;
	public static final int GATE_II_STARS = 18;
	/** {@code star:all} never condenses more than this many at once. */
	public static final int MAX_STARS_AT_ONCE = 1000;
	private static final String PERK_COUNT_KEY = "perk_count";
	private static final String UNBROKEN_KEY = "unbroken_at";
	private static final String STARS_KEY = "stars";
	private static final int GOLD = 0xFFD54A;

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).filter(realm -> !realm.isMortal()).map(Realm::id), builder);
	private static final SuggestionProvider<CommandSourceStack> QUALITIES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(new String[]{"retak", "normal", "sempurna", "surgawi"}, builder);
	private static final SuggestionProvider<CommandSourceStack> TALISMAN_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Talismans.testIds(), builder);

	/** Registers the module's events; also initialises {@link Reforge}, {@link CaveAbode} and the compass (DESIGN 19.2.3). */
	public static void init() {
		VeinCompassItem.init();
		Reforge.init();
		CaveAbode.init();
		DeathGuard.register(DeathGuard.Stage.PERK, ImmortalPerks::unbrokenSoul);
		Tribulation.addStrikeFactorProvider((player, target, index, last) ->
				target.realm() == Realm.TRUE_IMMORTAL && boonActive(Cultivators.get(player), Boon.DOOR.layer()) ? DOOR_STRIKE_FACTOR : 1.0D);
	}

	/** Every server tick for each player: the ritual of {@link Reforge}, and every 20 ticks the boon announcements. */
	public static void tick(ServerPlayer player, CultivationData data) {
		Reforge.tick(player, data);
		if (player.tickCount % 20 != 0) {
			return;
		}
		int count = activeCount(data);
		CompoundTag section = CaveAbode.section(data);
		int known = section.getIntOr(PERK_COUNT_KEY, 0);
		if (count == known) {
			return;
		}
		section.putInt(PERK_COUNT_KEY, count);
		Cultivators.dirty(player);
		Stats.apply(player);
		Qi.clamp(player);
		if (count > known) {
			for (int layer = known + 1; layer <= count; layer++) {
				announce(player, Boon.ofLayer(layer), layer == count);
			}
		}
	}

	// ================================================================== boons

	/** True when the boon of Immortal Ascension layer {@code layer} (1-9) is active for this player. Pure (client too). */
	public static boolean boonActive(CultivationData data, int layer) {
		Rank rank = data.rank();
		if (rank.realm() == Realm.TRUE_IMMORTAL) {
			return true;
		}
		return rank.realm() == Realm.IMMORTAL_ASCENSION && rank.layer() >= layer - 1;
	}

	/** Boons active now (0-9). */
	public static int activeCount(CultivationData data) {
		int count = 0;
		for (Boon boon : Boon.values()) {
			if (boonActive(data, boon.layer())) {
				count++;
			}
		}
		return count;
	}

	/** "Urat Abadi terbuka: Napas Abadi — …": the chat line, and with {@code title} the title and the effect. */
	private static void announce(ServerPlayer player, Boon boon, boolean title) {
		Msg.chat(player, Component.translatable("kultivator.kultivasi.boon.opened", boon.displayName().copy().withStyle(ChatFormatting.BOLD)
				.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(boon.source()))), boon.description()).withColor(GOLD));
		Msg.chat(player, boon.source().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (!title) {
			return;
		}
		Msg.title(player, Component.translatable("kultivator.kultivasi.boon.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				boon.displayName().copy().withStyle(ChatFormatting.YELLOW), 10, 60, 20);
		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.TALISMAN, TalismanFxIds.BOON, boon.layer(), player.getBbHeight());
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/**
	 * L7 Jiwa Tak Hancur (DESIGN 19.3.11): once per game day a killing blow leaves the player at 1 HP with Absorption and
	 * Resistance III, and takes all current qi. Never against {@code #kultivator:unblockable}, {@code /kill} or the void
	 * ({@code bypasses_invulnerability}), tribulation lightning, a duel or sparring. The totem and the technique
	 * cheat-deaths act first (earlier stages).
	 */
	private static boolean unbrokenSoul(ServerPlayer player, DamageSource source, float amount) {
		CultivationData data = Cultivators.get(player);
		if (!boonActive(data, Boon.SOUL.layer())) {
			return false;
		}
		if (source.is(DamageHooks.UNBLOCKABLE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(Tribulation.DAMAGE)
				|| DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		CompoundTag section = CaveAbode.section(data);
		if (section.contains(UNBROKEN_KEY) && now - section.getLongOr(UNBROKEN_KEY, 0L) < UNBROKEN_COOLDOWN) {
			return false;
		}
		section.putLong(UNBROKEN_KEY, now);
		player.setHealth(1.0F);
		player.clearFire();
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		int levels = (int) Math.round(player.getMaxHealth() * UNBROKEN_ABSORPTION_SHARE / 4.0D);
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, UNBROKEN_ABSORPTION_TICKS, Math.max(0, levels - 1)));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, UNBROKEN_RESISTANCE_TICKS, 2));
		data.setQi(0.0D);
		Cultivators.dirty(player);
		Vec3 pos = player.position();
		Fx.send(level, pos, FxType.TALISMAN, TalismanFxIds.SOUL, 0, player.getBbHeight());
		level.playSound(null, pos.x, pos.y + 1.0D, pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, pos.x, pos.y + 1.0D, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 1.3F);
		Msg.title(player, Component.translatable("kultivator.kultivasi.unbroken.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.kultivasi.unbroken.subtitle").withStyle(ChatFormatting.YELLOW), 5, 50, 20);
		Msg.chat(player, Component.translatable("kultivator.kultivasi.unbroken.chat").withColor(GOLD));
		Advancements.grant(player, "unbroken_soul");
		return true;
	}

	/** Ticks until Jiwa Tak Hancur can act again (0 when ready). */
	public static long unbrokenCooldown(ServerPlayer player) {
		CompoundTag section = Cultivators.get(player).extra().getCompoundOrEmpty(TechniquePower.PLAYER_EXTRA);
		if (!section.contains(UNBROKEN_KEY)) {
			return 0L;
		}
		return Math.max(0L, section.getLongOr(UNBROKEN_KEY, 0L) + UNBROKEN_COOLDOWN - player.level().getGameTime());
	}

	// ================================================================== stars

	/** The player's stars ({@code extra.kultivasi.stars}, read by 6c through {@code TechniquePower.playerStars}). */
	public static int stars(CultivationData data) {
		return TechniquePower.playerStars(data);
	}

	/** Qi Abadi (Stored Qi) the next star costs: {@code Stars.cost(stars + 1)}. */
	public static double nextStarCost(CultivationData data) {
		return Stars.cost(stars(data) + 1);
	}

	/** Action {@code star} / {@code star:all} and {@code bintang padatkan [semua]}: condenses stars from Qi Abadi. */
	public static int condense(ServerPlayer player, boolean all) {
		CultivationData data = Cultivators.get(player);
		if (!data.rank().isMax()) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.star.need_peak",
					new Rank(Realm.TRUE_IMMORTAL, 0, Rank.PEAK).displayName().withColor(Realm.TRUE_IMMORTAL.color())).withColor(0xFF9A7A));
			return 0;
		}
		int before = stars(data);
		int stars = before;
		double stored = data.storedQi();
		double spent = 0.0D;
		Component gated = null;
		while (stars - before < (all ? MAX_STARS_AT_ONCE : 1)) {
			// Phase 9 (DESIGN 26.14 item 7): a star gate (the Seven Calamities) stops before the gated star.
			gated = dev.kultivator.ascension.Stars.gateBlock(player, stars + 1);
			if (gated != null) {
				break;
			}
			// Phase 8 (DESIGN 25.8.4): the cost modifiers (Esensi discount) are asked per star; their listener consumes.
			double cost = Stars.cost(stars + 1) * dev.kultivator.ascension.Stars.costFactor(player);
			if (stored + 1.0E-6D < cost) {
				break;
			}
			stored -= cost;
			spent += cost;
			stars++;
			dev.kultivator.ascension.Stars.condensed(player, stars);
		}
		if (stars == before && gated != null) {
			Msg.chat(player, gated);
			return 0;
		}
		if (stars == before) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.star.cost", Msg.number(data.storedQi()),
					Msg.number(Stars.cost(before + 1) * dev.kultivator.ascension.Stars.costFactor(player))).withColor(0xFF9A7A));
			return 0;
		}
		data.setStoredQi(Math.max(0.0D, stored));
		writeStars(player, data, stars);
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		Fx.send(level, pos, FxType.TALISMAN, TalismanFxIds.STAR, stars, player.getBbHeight());
		level.playSound(null, pos.x, pos.y + 1.0D, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, pos.x, pos.y + 1.0D, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		int gained = stars - before;
		Msg.title(player, Component.translatable("kultivator.kultivasi.star.title", stars).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.kultivasi.star.subtitle", gained).withStyle(ChatFormatting.YELLOW), 10, 50, 20);
		Msg.chat(player, Component.translatable("kultivator.kultivasi.star.done", gained, stars, Msg.number(spent),
				Msg.number(Stars.cost(stars + 1))).withColor(GOLD));
		announceGates(player, before, stars);
		if (gated != null) {
			Msg.chat(player, gated);
		}
		return gained;
	}

	/** Sets the player's stars ({@code uji bintang}). */
	public static void setStars(ServerPlayer player, int stars) {
		CultivationData data = Cultivators.get(player);
		int before = stars(data);
		writeStars(player, data, Math.max(0, stars));
		announceGates(player, before, stars);
	}

	private static void writeStars(ServerPlayer player, CultivationData data, int stars) {
		CaveAbode.section(data).putInt(STARS_KEY, Math.max(0, stars));
		Stats.apply(player);
		Qi.clamp(player);
		Techniques.refreshUnlocks(player);
		if (stars >= 1) {
			Advancements.grant(player, "immortal_star");
		}
		if (stars >= GATE_I_STARS) {
			Advancements.grant(player, "nine_stars");
		}
		if (stars >= GATE_II_STARS) {
			Advancements.grant(player, "eighteen_stars");
		}
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	private static void announceGates(ServerPlayer player, int before, int after) {
		if (before < GATE_I_STARS && after >= GATE_I_STARS) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.star.gate", 1, GATE_I_STARS).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (before < GATE_II_STARS && after >= GATE_II_STARS) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.star.gate", 2, GATE_II_STARS).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	// ================================================================== actions and commands

	/** Action {@code KULTIVASI}: text = "star", "star:all", "reforge", "abode:set" or "abode:clear" (DESIGN 19.2.1). */
	public static void handleAction(ServerPlayer player, String text) {
		switch (text == null ? "" : text) {
			case "star" -> condense(player, false);
			case "star:all" -> condense(player, true);
			case "reforge" -> Reforge.start(player);
			case "abode:set" -> CaveAbode.set(player);
			case "abode:clear" -> CaveAbode.clear(player);
			default -> {
			}
		}
	}

	/** {@code gua ...}, {@code fondasi perkokoh}, {@code bintang padatkan [semua]} and {@code debug perks show} (DESIGN 19.4). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("gua")
				.then(Commands.literal("tetapkan").executes(ctx -> CaveAbode.set(self(ctx)) ? 1 : 0))
				.then(Commands.literal("lihat").executes(ctx -> {
					CaveAbode.show(self(ctx));
					return 1;
				}))
				.then(Commands.literal("hapus").executes(ctx -> CaveAbode.clear(self(ctx)) ? 1 : 0)));
		root.then(Commands.literal("fondasi")
				.then(Commands.literal("perkokoh").executes(ctx -> Reforge.start(self(ctx)) ? 1 : 0)));
		root.then(Commands.literal("bintang")
				.then(Commands.literal("padatkan")
						.executes(ctx -> condense(self(ctx), false))
						.then(Commands.literal("semua").executes(ctx -> condense(self(ctx), true)))));
		debug.then(Commands.literal("perks")
				.then(Commands.literal("show").executes(ctx -> showPerks(ctx.getSource(), self(ctx)))));
	}

	/** {@code uji urat|tertutup|gua|jimat|anugerah|jiwa|fondasi|bintang ...} (DESIGN 19.4). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("urat")
				.then(Commands.literal("cari").executes(ctx -> findVeins(ctx.getSource(), self(ctx))))
				.then(Commands.literal("tingkat")
						.then(Commands.literal("agung").executes(ctx -> Meditation.setVeinGrade(ctx.getSource(), self(ctx), SpiritVeins.GREAT)))
						.then(Commands.literal("kaya").executes(ctx -> Meditation.setVeinGrade(ctx.getSource(), self(ctx), SpiritVeins.RICH)))
						.then(Commands.literal("biasa").executes(ctx -> Meditation.setVeinGrade(ctx.getSource(), self(ctx), SpiritVeins.NORMAL)))
						.then(Commands.literal("tipis").executes(ctx -> Meditation.setVeinGrade(ctx.getSource(), self(ctx), SpiritVeins.THIN)))
						.then(Commands.literal("reset").executes(ctx -> Meditation.setVeinGrade(ctx.getSource(), self(ctx), null))))
				.then(Commands.argument("persen", DoubleArgumentType.doubleArg(0.0D, 200.0D))
						.executes(ctx -> Meditation.setDensity(ctx.getSource(), self(ctx), DoubleArgumentType.getDouble(ctx, "persen")))));
		uji.then(Commands.literal("tertutup")
				.then(Commands.literal("30m").executes(ctx -> seclusion(ctx, Meditation.SECLUSION_DEEP)))
				.then(Commands.argument("tumpukan", IntegerArgumentType.integer(0, Meditation.SECLUSION_MAX))
						.executes(ctx -> seclusion(ctx, IntegerArgumentType.getInteger(ctx, "tumpukan") * Meditation.SECLUSION_PERIOD))));
		uji.then(Commands.literal("gua").executes(ctx -> {
			CaveAbode.setUnchecked(self(ctx));
			return 1;
		}));
		uji.then(Commands.literal("jimat")
				.then(Commands.literal("tingkat")
						.then(Commands.argument("tingkat", IntegerArgumentType.integer(0, Inscribe.MAX_LEVEL))
								.executes(ctx -> talismanLevel(ctx, IntegerArgumentType.getInteger(ctx, "tingkat")))))
				.then(Commands.argument("jenis", StringArgumentType.word()).suggests(TALISMAN_IDS)
						.executes(ctx -> giveTalismans(ctx, 5))
						.then(Commands.argument("kekuatan", IntegerArgumentType.integer(1, 9))
								.executes(ctx -> giveTalismans(ctx, IntegerArgumentType.getInteger(ctx, "kekuatan"))))));
		uji.then(Commands.literal("anugerah").executes(ctx -> listBoons(ctx.getSource(), self(ctx), true)));
		uji.then(Commands.literal("jiwa").executes(ctx -> {
			ServerPlayer player = self(ctx);
			CaveAbode.section(Cultivators.get(player)).remove(UNBROKEN_KEY);
			Cultivators.dirty(player);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.soul_reset")), false);
			return 1;
		}));
		uji.then(Commands.literal("fondasi")
				.then(Commands.argument("ranah", StringArgumentType.word()).suggests(REALMS)
						.then(Commands.argument("kualitas", StringArgumentType.word()).suggests(QUALITIES)
								.executes(ImmortalPerks::setQualityCommand))));
		uji.then(Commands.literal("bintang")
				.then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
						.executes(ctx -> {
							int n = IntegerArgumentType.getInteger(ctx, "n");
							setStars(self(ctx), n);
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.stars", n)), false);
							return 1;
						})));
	}

	private static int seclusion(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		if (!Meditation.setUnbroken(player, seconds)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kultivasi.test.need_meditation"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.seclusion", seconds / 60,
				Meditation.seclusionStacks(player))), false);
		return 1;
	}

	private static int talismanLevel(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
		Inscribe.setLevel(self(ctx), level);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.talismans.test.level", level,
				Math.round(Inscribe.chance(level) * 100.0D))), false);
		return 1;
	}

	private static int giveTalismans(CommandContext<CommandSourceStack> ctx, int power) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "jenis");
		int given = Talismans.testGive(self(ctx), id, power);
		if (given == 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.talismans.test.unknown", id, String.join(", ", Talismans.testIds())));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.talismans.test.given", given, power)), false);
		return given;
	}

	private static int setQualityCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Realm realm = Realm.byId(StringArgumentType.getString(ctx, "ranah"));
		BreakthroughQuality quality = qualityById(StringArgumentType.getString(ctx, "kualitas"));
		if (realm == null || realm.isMortal() || quality == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kultivasi.test.quality_unknown"));
			return 0;
		}
		CultivationData data = Cultivators.get(player);
		data.setQuality(realm, quality);
		Stats.apply(player);
		Qi.clamp(player);
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.quality",
				realm.displayName(), quality.displayName().copy().withColor(quality.color()))), false);
		return 1;
	}

	private static @Nullable BreakthroughQuality qualityById(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "retak", "cracked" -> BreakthroughQuality.CRACKED;
			case "normal" -> BreakthroughQuality.NORMAL;
			case "sempurna", "perfect" -> BreakthroughQuality.PERFECT;
			case "surgawi", "heavenly" -> BreakthroughQuality.HEAVENLY;
			default -> null;
		};
	}

	/** {@code uji anugerah} and {@code debug perks show}: the active boons; with {@code replay} the newest title again. */
	private static int listBoons(CommandSourceStack source, ServerPlayer player, boolean replay) {
		CultivationData data = Cultivators.get(player);
		int count = activeCount(data);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.boons", count, Boon.values().length)), false);
		for (Boon boon : Boon.values()) {
			boolean on = boonActive(data, boon.layer());
			MutableComponent line = Component.literal((on ? "✔ L" : "✘ L") + boon.layer() + " ").withColor(on ? 0x7CFFB2 : 0x808080)
					.append(boon.displayName().copy().withColor(on ? GOLD : 0x9A9A9A))
					.append(Component.literal(" — ").withColor(0x808080))
					.append(boon.description().copy().withColor(on ? 0xE0E0E0 : 0x808080))
					.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(boon.source())));
			source.sendSuccess(() -> line, false);
			source.sendSuccess(() -> Component.literal("   ").append(boon.source().copy()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
		}
		if (replay && count > 0) {
			announce(player, Boon.ofLayer(count), true);
		}
		return count;
	}

	private static int showPerks(CommandSourceStack source, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		listBoons(source, player, false);
		long soul = unbrokenCooldown(player);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.debug.perks", stars(data),
				Msg.number(nextStarCost(data)), Msg.number(data.storedQi()), soul / 20L, Reforge.count(data),
				Inscribe.level(data), Inscribe.made(data))), false);
		return 1;
	}

	private static int findVeins(CommandSourceStack source, ServerPlayer player) {
		SpiritVeins.SearchResult result = SpiritVeins.search(player.level(), player.blockPosition(), VeinCompassItem.SEARCH_RADIUS);
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.test.veins", result.evaluated())), false);
		for (SpiritVeins.Found found : new SpiritVeins.Found[]{result.great(), result.rich()}) {
			if (found == null) {
				continue;
			}
			var center = SpiritVeins.regionCenter(found.regionX(), found.regionZ(), player.getBlockY());
			int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(center)));
			source.sendSuccess(() -> Component.translatable("kultivator.kultivasi.test.vein_line",
					SpiritVeins.gradeName(found.grade()).copy().withColor(SpiritVeins.gradeColor(found.grade())), center.getX(), center.getZ(),
					distance), false);
		}
		if (result.great() == null && result.rich() == null) {
			source.sendSuccess(() -> Component.translatable("kultivator.kultivasi.compass.none",
					VeinCompassItem.SEARCH_RADIUS * SpiritVeins.REGION_BLOCKS), false);
		}
		return 1;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private ImmortalPerks() {
	}
}
