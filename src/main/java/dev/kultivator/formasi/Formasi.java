package dev.kultivator.formasi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.catalog.CatalogA;
import dev.kultivator.formasi.catalog.LegacyMirrors;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreState;
import dev.kultivator.formasi.core.CoreStore;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.OwnerStore;
import dev.kultivator.formasi.core.Verifier;
import dev.kultivator.formasi.fuel.FuelTable;
import dev.kultivator.formasi.fuel.Kas;
import dev.kultivator.formasi.fuel.Upkeep;
import dev.kultivator.formasi.integrate.AlchemyBridge;
import dev.kultivator.formasi.integrate.BreakthroughBridge;
import dev.kultivator.formasi.integrate.DiscipleBridge;
import dev.kultivator.formasi.integrate.HallBridge;
import dev.kultivator.formasi.integrate.MeditationBridge;
import dev.kultivator.formasi.integrate.ProjectileTracker;
import dev.kultivator.formasi.integrate.QiBridge;
import dev.kultivator.formasi.integrate.TribulationBridge;
import dev.kultivator.formasi.integrate.ZoneBridge;
import dev.kultivator.formasi.keeper.KeeperOrder;
import dev.kultivator.formasi.keeper.Keepers;
import dev.kultivator.formasi.match.ChunkReader;
import dev.kultivator.formasi.match.Detect;
import dev.kultivator.formasi.match.MatchResult;
import dev.kultivator.formasi.match.PatternMatcher;
import dev.kultivator.formasi.own.Limits;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.own.Slots;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Pattern;
import dev.kultivator.formasi.spec.Slot;
import dev.kultivator.formasi.stack.Power;
import dev.kultivator.formasi.stack.PowerBonus;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Phase 7c "Formasi Sekte" (DESIGN 24): the module entry of P7c-inti. {@link #init()} registers the eye, builds the
 * formation registry on both physical sides (legacy mirrors, catalogue A, {@code FormasiB.init}, freeze), the server
 * runtime ({@code CoreTicker}), the sneak-use of the eye (Pengenalan Darah, deposits, toggles), the block-event re-reads
 * and every integration hook. Module B plugs in through the {@code add*} registries below. Nothing here changes a block.
 */
public final class Formasi {

	/** Looked-at eyes are found within this many blocks. */
	public static final double LOOK_RANGE = 8.0D;
	public static final int DEFAULT_HOLOGRAM_MINUTES = 10;
	public static final String EXTRA = "formasi";

	private static final List<SyncPart> SYNC_PARTS = new ArrayList<>();
	private static final List<InfoPart> INFO_PARTS = new ArrayList<>();
	private static final Map<String, BiConsumer<ServerPlayer, String>> ACTION_HANDLERS = new LinkedHashMap<>();
	private static final Map<Family, UseHandler> USE_HANDLERS = new EnumMap<>(Family.class);
	private static final List<PowerBonus> POWER_BONUSES = new ArrayList<>();
	private static final Map<UUID, Long> DETECT_COOLDOWN = new HashMap<>();
	private static final String[] DIRECTIONS = {"utara", "timur", "selatan", "barat"};
	private static boolean initialised;

	private static final SuggestionProvider<CommandSourceStack> SPECS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(FormationRegistry.all().stream().map(FormationSpec::id), builder);
	private static final SuggestionProvider<CommandSourceStack> ORDERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.Arrays.stream(KeeperOrder.values()).map(KeeperOrder::id), builder);
	private static final SuggestionProvider<CommandSourceStack> ARAH = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.Arrays.stream(DIRECTIONS), builder);

	/** Called once from {@code Kultivator.onInitialize}, before {@code ModItems.init()}. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		FormasiItems.init();
		LegacyMirrors.register(FormationRegistry.specSink());
		CatalogA.register(FormationRegistry.specSink(), FormationRegistry.effectSink());
		FormasiB.init(FormationRegistry.specSink(), FormationRegistry.effectSink());
		FormationRegistry.freeze();

		CoreTicker.init();
		UseBlockCallback.EVENT.register(Formasi::onUseBlock);
		PlayerBlockBreakEvents.AFTER.register(Formasi::onBreak);
		MeditationBridge.init();
		DiscipleBridge.init();
		TribulationBridge.init();
		BreakthroughBridge.init();
		AlchemyBridge.init();
		QiBridge.init();
		ZoneBridge.init();
		HallBridge.init();
		ProjectileTracker.init();
	}

	// ================================================================== block events

	private static void onBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		try {
			if (state.is(FormasiItems.FORMATION_EYE)) {
				CoreTicker.eyeGone(serverLevel, pos);
			} else if (!CoreTicker.store(serverLevel).isEmpty()) {
				Verifier.reread(serverLevel, pos);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Formasi block break hook failed at {}", pos, exception);
		}
	}

	/**
	 * Sneak-use of a Mata Formasi with fuel or an empty main hand (24.2.5); a plain use passes through to vanilla. Any use
	 * while holding a block also queues a re-read of the slot it may fill (the placement event of 24.2.3).
	 */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (level instanceof ServerLevel serverLevel && player.getItemInHand(hand).getItem() instanceof BlockItem
				&& !CoreTicker.store(serverLevel).isEmpty()) {
			Verifier.queue(serverLevel, pos.relative(hit.getDirection()));
			Verifier.queue(serverLevel, pos);
		}
		if (player.isSpectator() || !player.isSecondaryUseActive() || !level.getBlockState(pos).is(FormasiItems.FORMATION_EYE)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		boolean fuel = FuelTable.grade(held) != FuelTable.NONE;
		boolean empty = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty();
		if (!fuel && !empty) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		try {
			return useEye(serverPlayer, serverLevel, pos.immutable(), held, fuel);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Formation eye interaction failed at {}", pos, exception);
			return InteractionResult.SUCCESS;
		}
	}

	private static InteractionResult useEye(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held, boolean fuel) {
		CoreStore store = CoreTicker.store(level);
		CoreRecord record = store.atEye(pos);
		if (record == null) {
			return useUnregistered(player, level, store, pos, held, fuel);
		}
		FormationSpec spec = FormationRegistry.byId(record.spec);
		boolean owner = record.owner.equals(player.getUUID());
		boolean mate = Ownership.mates(level.getServer(), record.owner, player.getUUID());
		if (!owner && !mate) {
			boolean concealed = Stacking.fieldAt(level, Vec3.atCenterOf(pos), player).concealGrade() > 0
					|| !dev.kultivator.formasi.core.Cores.covering(level, Vec3.atCenterOf(pos), Family.PENYEMBUNYI).isEmpty();
			Msg.actionBar(player, concealed ? Component.translatable("kultivator.formasi.silent_eye")
					: Component.translatable("kultivator.formasi.belongs", record.ownerName));
			return InteractionResult.SUCCESS;
		}
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.reason.unknown_spec", record.spec).withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		if (fuel) {
			int result = feed(player, level, record, spec, held);
			if (result == 0) {
				int daya = Kas.depositInventory(player, record.owner, false);
				Msg.chat(player, Component.translatable(daya > 0 ? "kultivator.formasi.core_full_kas" : "kultivator.formasi.kas_full", spec.name(), daya)
						.withStyle(ChatFormatting.YELLOW));
			} else if (result > 0 && mate) {
				OwnerStore.get(level.getServer()).log(record.owner, level.getGameTime() / 24000L, "kultivator.formasi.log.donation",
						player.getPlainTextName(), Integer.toString(result));
			}
			return InteractionResult.SUCCESS;
		}
		UseHandler handler = USE_HANDLERS.get(spec.family());
		if (handler != null) {
			return handler.use(player, CoreTicker.view(record));
		}
		if (owner) {
			record.on = !record.on;
			store.setDirty();
			CoreTicker.refreshState(level, record);
			sound(level, Vec3.atCenterOf(pos), record.on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.2F);
			Msg.chat(player, Component.translatable(record.on ? "kultivator.formasi.toggled_on" : "kultivator.formasi.toggled_off", spec.name(),
					record.state.label()).withStyle(record.on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		} else {
			Msg.chat(player, statusLine(record, spec));
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useUnregistered(ServerPlayer player, ServerLevel level, CoreStore store, BlockPos pos, ItemStack held,
			boolean fuel) {
		UUID territory = Ownership.territoryOwnerAt(level, Vec3.atCenterOf(pos));
		boolean personal = territory == null;
		boolean foreign = territory != null && !territory.equals(player.getUUID());
		if (foreign) {
			boolean mate = Ownership.mates(level.getServer(), territory, player.getUUID());
			Halls.HallView hall = Halls.hallOf(territory);
			Msg.chat(player, (mate ? Component.translatable("kultivator.formasi.only_master")
					: Component.translatable("kultivator.formasi.foreign_territory", hall == null ? "?" : hall.name())).withStyle(ChatFormatting.YELLOW));
			if (fuel) {
				return InteractionResult.SUCCESS;
			}
		}
		long now = level.getGameTime();
		Long last = DETECT_COOLDOWN.get(player.getUUID());
		if (last != null && now - last < Detect.COOLDOWN_TICKS && now >= last) {
			return InteractionResult.SUCCESS;
		}
		DETECT_COOLDOWN.put(player.getUUID(), now);
		int hallLevel = personal ? Limits.PERSONAL_HALL_LEVEL : CoreTicker.hallLevel(foreign ? territory : player.getUUID());
		Detect.Outcome outcome = Detect.detect(level, pos, store.target(pos), hallLevel, personal);
		if (!fuel || foreign || outcome.match() == null) {
			report(player, level, pos, outcome);
			return InteractionResult.SUCCESS;
		}
		MatchResult match = outcome.match();
		FormationSpec spec = match.spec();
		Component refusal = registrationRefusal(player, level, spec, personal, hallLevel);
		if (refusal != null) {
			Msg.chat(player, refusal.copy().withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		CoreRecord record = store.create(spec.id(), pos, match.rot(), player.getUUID(), player.getPlainTextName(),
				personal ? Ownership.KIND_PERSONAL : Ownership.KIND_SECT);
		record.on = true;
		record.createdDay = now / 24000L;
		OwnerStore owners = OwnerStore.get(level.getServer());
		record.draw = !personal && owners.entry(player.getUUID()).drawDefault;
		Verifier.apply(record, match);
		store.setTarget(pos, null);
		feed(player, level, record, spec, held);
		CoreTicker.refreshState(level, record);
		Vec3 centre = Vec3.atCenterOf(pos);
		Fx.send(level, centre, FxType.FORMASI, FormasiFxIds.AWAKEN, spec.netId(), (float) record.radius);
		sound(level, centre, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		sound(level, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F);
		Msg.chat(player, Component.translatable("kultivator.formasi.registered", spec.name(), spec.grade().label(), record.state.label())
				.withStyle(ChatFormatting.GREEN));
		if (record.reason != null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.state_reason", record.state.label(), record.reason).withStyle(ChatFormatting.GRAY));
		}
		if (outcome.hint() != null) {
			hint(player, outcome.hint());
		}
		owners.log(player.getUUID(), now / 24000L, "kultivator.formasi.log.registered", spec.id());
		Advancements.grant(player, "formation_eye");
		noteGrade(player, spec.grade());
		return InteractionResult.SUCCESS;
	}

	/** Why {@code player} may not register {@code spec} here, or null. */
	private static @Nullable Component registrationRefusal(ServerPlayer player, ServerLevel level, FormationSpec spec, boolean personal, int hallLevel) {
		if (!KultivatorConfig.get().sectFormations) {
			return Component.translatable("kultivator.formasi.reason.config");
		}
		CoreStore store = CoreTicker.store(level);
		if (store.realCount() >= KultivatorConfig.get().formationMaxCores) {
			return Component.translatable("kultivator.formasi.refuse.dimension", KultivatorConfig.get().formationMaxCores);
		}
		int personalCount = 0;
		int registered = 0;
		int group = 0;
		String key = Limits.group(spec);
		for (CoreView view : dev.kultivator.formasi.core.Cores.of(player.getUUID())) {
			FormationSpec other = FormationRegistry.byId(view.spec());
			if (view.virtual() || other == null) {
				continue;
			}
			if (view.kind() == Ownership.KIND_PERSONAL) {
				personalCount++;
				continue;
			}
			if (!Limits.transport(other)) {
				registered++;
			}
			if (Limits.group(other).equals(key)) {
				group++;
			}
		}
		if (personal) {
			if (!spec.has(Flag.PERSONAL_OK) || spec.grade().ordinal() > Grade.MISTERIUS.ordinal()) {
				return Component.translatable("kultivator.formasi.refuse.personal_spec", spec.name());
			}
			return personalCount >= Limits.PERSONAL_MAX ? Component.translatable("kultivator.formasi.reason.limit_personal", Limits.PERSONAL_MAX) : null;
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			return Component.translatable("kultivator.formasi.refuse.no_hall");
		}
		if (!Limits.transport(spec) && registered >= Limits.registered(hallLevel)) {
			return Component.translatable("kultivator.formasi.reason.limit_total", Limits.registered(hallLevel));
		}
		if (group >= Limits.groupMax(spec, hallLevel)) {
			return Component.translatable("kultivator.formasi.reason.limit_group", Limits.groupMax(spec, hallLevel));
		}
		return null;
	}

	/**
	 * Feeds one item of {@code held} into the core (24.2.7): used when at least half of its value fits, the overflow goes to
	 * the Kas. Returns the Daya added (> 0), 0 when the core is full, -1 when refused (amethyst on a stone-only core).
	 */
	private static int feed(ServerPlayer player, ServerLevel level, CoreRecord record, FormationSpec spec, ItemStack held) {
		int grade = FuelTable.grade(held);
		int daya = FuelTable.daya(held);
		if (grade == FuelTable.NONE || daya <= 0) {
			return -1;
		}
		if (grade == FuelTable.AMETHYST && spec.has(Flag.STONE_ONLY)) {
			Msg.chat(player, Component.translatable("kultivator.formasi.stone_only", spec.name()).withStyle(ChatFormatting.YELLOW));
			return -1;
		}
		long value = daya * 100L;
		long room = Upkeep.capCenti(spec) - record.daya;
		if (record.virtual || room < value / 2L) {
			return 0;
		}
		long accepted = Math.min(value, room);
		record.daya += accepted;
		if (value > accepted) {
			Kas.add(record.owner, value - accepted, grade);
		}
		if (grade >= 0) {
			record.best = Math.max(record.best, grade);
		}
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		CoreTicker.markDirty(level);
		CoreTicker.refreshState(level, record);
		Vec3 centre = Vec3.atCenterOf(record.eye);
		sound(level, centre, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.9F + 0.1F * Math.max(0, grade));
		Fx.send(level, centre, FxType.FORMASI, FormasiFxIds.PULSE, spec.family().ordinal(), (float) record.radius);
		Msg.actionBar(player, Component.translatable("kultivator.formasi.fed", spec.name(), daya, record.daya / 100L, Upkeep.capCenti(spec) / 100L));
		return daya;
	}

	/** The detection report (24.2.3): the hint, the grouped missing blocks with markers, or "no pattern". */
	private static void report(ServerPlayer player, ServerLevel level, BlockPos eye, Detect.Outcome outcome) {
		if (outcome.match() != null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.detect.complete", outcome.match().spec().name()).withStyle(ChatFormatting.AQUA));
			return;
		}
		if (outcome.overBudget()) {
			Msg.chat(player, Component.translatable("kultivator.formasi.detect.over_budget").withStyle(ChatFormatting.YELLOW));
		}
		if (outcome.hint() != null) {
			hint(player, outcome.hint());
			return;
		}
		MatchResult partial = outcome.partial();
		if (partial == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.detect.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		String command = "/kultivasi formasi pola " + partial.spec().id() + " " + DIRECTIONS[partial.rot()];
		MutableComponent show = Component.translatable("kultivator.formasi.detect.show").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)));
		Msg.chat(player, Component.translatable("kultivator.formasi.detect.incomplete", partial.spec().name(), partial.bad(),
				PatternMatcher.missingSummary(partial, 3)).withStyle(ChatFormatting.YELLOW).append(" ").append(show));
		List<Byte> kinds = new ArrayList<>();
		List<BlockPos> bad = PatternMatcher.badPositions(eye, partial, CoreTicker.MAX_MARKS, kinds);
		for (int i = 0; i < bad.size(); i++) {
			Vec3 at = Vec3.atCenterOf(bad.get(i));
			Fx.sendTo(player, at, at, FxType.FORMASI, FormasiFxIds.SLOT_MARK, kinds.get(i) == ChunkReader.WRONG ? 0 : 1, 30.0F);
		}
	}

	private static void hint(ServerPlayer player, MatchResult hint) {
		Msg.chat(player, Component.translatable("kultivator.formasi.detect.hint", hint.spec().grade().label(), hint.spec().name(),
				hint.spec().minHallLevel()).withStyle(ChatFormatting.GOLD));
	}

	private static Component statusLine(CoreRecord record, FormationSpec spec) {
		return Component.translatable("kultivator.formasi.status", spec.name(), record.state.label(), record.daya / 100L,
				Upkeep.capCenti(spec) / 100L).withStyle(ChatFormatting.AQUA);
	}

	/** Remembers registered grades; one formation of each of the 5 grades grants {@code formation_grandmaster}. */
	private static void noteGrade(ServerPlayer player, Grade grade) {
		CultivationData data = Cultivators.get(player);
		CompoundTag section = data.extra().getCompound(EXTRA).orElse(null);
		if (section == null) {
			section = new CompoundTag();
			data.extra().put(EXTRA, section);
		}
		int bits = section.getIntOr("grades_seen", 0) | (1 << grade.ordinal());
		section.putInt("grades_seen", bits);
		Cultivators.dirty(player);
		if (bits == (1 << Grade.values().length) - 1) {
			Advancements.grant(player, "formation_grandmaster");
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
	}

	/** Sets the target spec of an unregistered eye ({@code formasi tuju}, the Kitab button); true when set. */
	public static boolean setTarget(ServerPlayer player, ServerLevel level, BlockPos pos, String specId) {
		FormationSpec spec = FormationRegistry.byId(specId);
		if (!level.getBlockState(pos).is(FormasiItems.FORMATION_EYE) || spec == null || spec.has(Flag.LEGACY_MIRROR)) {
			Msg.chat(player, Component.translatable("kultivator.formasi.target.bad").withStyle(ChatFormatting.RED));
			return false;
		}
		CoreStore store = CoreTicker.store(level);
		if (store.atEye(pos) != null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.target.registered").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		UUID territory = Ownership.territoryOwnerAt(level, Vec3.atCenterOf(pos));
		if (territory != null && !territory.equals(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.formasi.only_master").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		store.setTarget(pos, spec.id());
		Msg.chat(player, Component.translatable("kultivator.formasi.target.set", spec.name()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ================================================================== commands

	/** {@code /kultivasi formasi ...} (module A builds the node; module B adds {@code simpul} and {@code pindah}). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("formasi").executes(Formasi::summary);
		node.then(Commands.literal("info").executes(Formasi::info));
		node.then(Commands.literal("katalog").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			Halls.openScreen(player, player.getUUID(), Halls.PAGE_FORMASI);
			return 1;
		}));
		node.then(Commands.literal("pola")
				.then(Commands.literal("mati").executes(ctx -> hideHologram(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS)
						.executes(ctx -> pattern(ctx, -1, DEFAULT_HOLOGRAM_MINUTES))
						.then(Commands.argument("arah", StringArgumentType.word()).suggests(ARAH)
								.executes(ctx -> pattern(ctx, direction(StringArgumentType.getString(ctx, "arah")), DEFAULT_HOLOGRAM_MINUTES))
								.then(Commands.argument("menit", IntegerArgumentType.integer(1, 60))
										.executes(ctx -> pattern(ctx, direction(StringArgumentType.getString(ctx, "arah")),
												IntegerArgumentType.getInteger(ctx, "menit")))))));
		node.then(Commands.literal("tuju").then(Commands.argument("id", StringArgumentType.word()).suggests(SPECS).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			BlockPos eye = lookedAtEye(player);
			if (eye == null) {
				Msg.chat(player, Component.translatable("kultivator.formasi.look_at_eye").withStyle(ChatFormatting.YELLOW));
				return 0;
			}
			return setTarget(player, (ServerLevel) player.level(), eye, StringArgumentType.getString(ctx, "id")) ? 1 : 0;
		})));
		node.then(Commands.literal("nyala").executes(ctx -> toggle(ctx, true)));
		node.then(Commands.literal("mati").executes(ctx -> toggle(ctx, false)));
		node.then(Commands.literal("setor").executes(ctx -> deposit(ctx, false)).then(Commands.literal("semua").executes(ctx -> deposit(ctx, true))));
		node.then(Commands.literal("isi").executes(ctx -> fill(ctx, Upkeep.DRAW_HOURS))
				.then(Commands.literal("semua").executes(Formasi::fillAll))
				.then(Commands.argument("jam", IntegerArgumentType.integer(1, Grade.CAP_HOURS))
						.executes(ctx -> fill(ctx, IntegerArgumentType.getInteger(ctx, "jam")))));
		node.then(Commands.literal("penjaga")
				.then(Commands.literal("lepas").executes(Formasi::releaseKeepers))
				.then(Commands.argument("nama", StringArgumentType.string())
						.executes(ctx -> assignKeeper(ctx, KeeperOrder.JAGA))
						.then(Commands.argument("order", StringArgumentType.word()).suggests(ORDERS)
								.executes(ctx -> {
									KeeperOrder order = KeeperOrder.byId(StringArgumentType.getString(ctx, "order"));
									return assignKeeper(ctx, order == null ? KeeperOrder.JAGA : order);
								}))));
		FormasiB.addCommands(node);
		root.then(node);
		FormasiTests.addDebug(debug);
	}

	/** {@code uji formasi ...} (op; no test command places or breaks a block). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("formasi");
		FormasiTests.add(node);
		FormasiB.addTestCommands(node);
		uji.then(node);
	}

	private static int direction(String text) {
		for (int i = 0; i < DIRECTIONS.length; i++) {
			if (DIRECTIONS[i].equalsIgnoreCase(text) || text.equalsIgnoreCase(new String[]{"north", "east", "south", "west"}[i])) {
				return i;
			}
		}
		return -1;
	}

	/** The Mata Formasi the player looks at within {@link #LOOK_RANGE}, or null. */
	public static @Nullable BlockPos lookedAtEye(ServerPlayer player) {
		BlockHitResult hit = Targeting.blockHit(player, LOOK_RANGE);
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return player.level().getBlockState(hit.getBlockPos()).is(FormasiItems.FORMATION_EYE) ? hit.getBlockPos().immutable() : null;
	}

	/** The registered (or virtual) core the player looks at, the nearest virtual core within 6 blocks, or null. */
	public static @Nullable CoreRecord lookedAtCore(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		BlockPos eye = lookedAtEye(player);
		CoreStore store = CoreTicker.store(level);
		if (eye != null) {
			return store.atEye(eye);
		}
		CoreRecord best = null;
		double bestDistance = 36.0D;
		for (CoreRecord record : store.all()) {
			double distance = Vec3.atCenterOf(record.eye).distanceToSqr(player.position());
			if (distance < bestDistance) {
				best = record;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static int summary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		int hallLevel = CoreTicker.hallLevel(owner);
		long a = Kas.amethyst(owner) / 100L;
		long s = Kas.stone(owner) / 100L;
		Msg.chat(player, Component.translatable("kultivator.formasi.summary.kas", a + s, a, s).withStyle(ChatFormatting.GOLD));
		int active = 0;
		int registered = 0;
		List<CoreView> views = dev.kultivator.formasi.core.Cores.of(owner);
		for (CoreView view : views) {
			FormationSpec spec = FormationRegistry.byId(view.spec());
			if (!view.virtual() && view.kind() == Ownership.KIND_SECT) {
				if (view.state().running()) {
					active++;
				}
				if (spec != null && !Limits.transport(spec)) {
					registered++;
				}
			}
		}
		Msg.chat(player, Component.translatable("kultivator.formasi.summary.slots", active, Slots.active(hallLevel), registered,
				Limits.registered(hallLevel), hallLevel).withStyle(ChatFormatting.GRAY));
		if (views.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.formasi.summary.none").withStyle(ChatFormatting.GRAY));
		}
		for (CoreView view : views) {
			CoreRecord record = CoreTicker.record(view.dim(), view.id());
			FormationSpec spec = FormationRegistry.byId(view.spec());
			if (record == null) {
				continue;
			}
			double hours = spec == null ? 0.0D : Upkeep.hoursLeft(record.daya, record.rate);
			Component name = spec == null ? Component.literal(view.spec()) : spec.name();
			MutableComponent line = Component.translatable("kultivator.formasi.summary.core", name, view.state().label(), record.daya / 100L,
					Double.isInfinite(hours) ? "∞" : Msg.number(hours), view.eye().getX(), view.eye().getY(), view.eye().getZ());
			if (view.virtual()) {
				line.append(Component.translatable("kultivator.formasi.uji_tag"));
			}
			if (view.reason() != null && !view.state().running()) {
				line.append(Component.literal(" — ")).append(view.reason());
			}
			Msg.chat(player, line.withStyle(view.state().running() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		CoreRecord record = lookedAtCore(player);
		if (record == null) {
			BlockPos eye = lookedAtEye(player);
			if (eye == null) {
				Msg.chat(player, Component.translatable("kultivator.formasi.look_at_eye").withStyle(ChatFormatting.YELLOW));
				return 0;
			}
			UUID territory = Ownership.territoryOwnerAt(level, Vec3.atCenterOf(eye));
			int hallLevel = territory == null ? Limits.PERSONAL_HALL_LEVEL : CoreTicker.hallLevel(territory);
			report(player, level, eye, Detect.detect(level, eye, CoreTicker.store(level).target(eye), hallLevel, territory == null));
			return 1;
		}
		describe(player, level, record);
		return 1;
	}

	/** The full card of a core for {@code formasi info} and {@code uji formasi status}. */
	public static void describe(ServerPlayer player, ServerLevel level, CoreRecord record) {
		FormationSpec spec = FormationRegistry.byId(record.spec);
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.reason.unknown_spec", record.spec).withStyle(ChatFormatting.RED));
			return;
		}
		Pattern pattern = spec.pattern();
		int total = pattern.size();
		int ok = 0;
		for (byte state : record.slotState) {
			if (state == ChunkReader.OK) {
				ok++;
			}
		}
		Msg.chat(player, Component.translatable("kultivator.formasi.info.head", spec.name(), spec.grade().label(), spec.family().label(),
				DIRECTIONS[record.rot], record.ownerName).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.formasi.info.match", record.virtual ? total : ok, total, record.missing, record.unknown)
				.withStyle(ChatFormatting.GRAY));
		if (record.missing > 0) {
			MatchResult result = new MatchResult(spec, record.rot, total, ok, record.missing, 0, record.unknown, record.slotState.clone());
			Msg.chat(player, Component.translatable("kultivator.formasi.info.missing", PatternMatcher.missingSummary(result, 3)).withStyle(ChatFormatting.RED));
			if (record.owner.equals(player.getUUID()) || Ownership.mates(level.getServer(), record.owner, player.getUUID())) {
				CoreTicker.markBad(player, record, spec);
			}
		}
		MutableComponent state = Component.translatable("kultivator.formasi.info.state", record.state.label());
		if (record.reason != null) {
			state.append(Component.literal(" — ")).append(record.reason);
		}
		if (record.idle) {
			state.append(Component.translatable("kultivator.formasi.info.idle"));
		}
		Msg.chat(player, state.withStyle(record.state.running() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		Msg.chat(player, Component.translatable("kultivator.formasi.info.power", fmt(record.power), fmt(Power.q(record.best)), fmt(Power.k(record)),
				fmt(Power.f(record)), fmt(spec.family().support() ? 0.0D : Power.a(record, CoreTicker.view(record))), fmt(record.radius))
				.withStyle(ChatFormatting.AQUA));
		double hours = Upkeep.hoursLeft(record.daya, record.rate);
		Msg.chat(player, Component.translatable("kultivator.formasi.info.upkeep", record.daya / 100L, Upkeep.capCenti(spec) / 100L,
				Double.isInfinite(hours) ? "∞" : fmt(hours), fmt(spec.upkeep()), fmt(record.vein), fmt(record.keeperUpkeep),
				record.idle ? fmt(Upkeep.STANDBY) : "1", record.flaggedBy >= 0 ? fmt(Upkeep.FLAG_TARGET) : "1").withStyle(ChatFormatting.GRAY));
		Msg.chat(player, Component.translatable("kultivator.formasi.info.keepers", record.keepers.size(), spec.grade().maxKeepers(), record.keepersAtPost)
				.withStyle(ChatFormatting.GRAY));
		Msg.chat(player, spec.source().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** Two decimals with a dot (chat numbers). */
	public static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static int pattern(CommandContext<CommandSourceStack> ctx, int rot, int minutes) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		FormationSpec spec = FormationRegistry.byId(StringArgumentType.getString(ctx, "id"));
		if (spec == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.unknown", StringArgumentType.getString(ctx, "id")).withStyle(ChatFormatting.RED));
			return 0;
		}
		showHologram(player, spec, rot, minutes);
		return 1;
	}

	/**
	 * Sends the hologram of {@code spec} to the player (24.7.3): anchored on the looked-at eye (the rotation with the most
	 * OK slots unless {@code rot} is given, and the eye's target becomes the spec), on top of the looked-at block, or on
	 * the ground 4 blocks ahead. FIXED specs always face true north.
	 */
	public static void showHologram(ServerPlayer player, FormationSpec spec, int rot, int minutes) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos eye = lookedAtEye(player);
		BlockPos anchor;
		int turn = rot;
		if (eye != null) {
			anchor = eye;
			if (turn < 0) {
				turn = bestRotation(level, eye, spec);
			}
			if (CoreTicker.store(level).atEye(eye) == null && !spec.has(Flag.LEGACY_MIRROR)) {
				UUID territory = Ownership.territoryOwnerAt(level, Vec3.atCenterOf(eye));
				if (territory == null || territory.equals(player.getUUID())) {
					CoreTicker.store(level).setTarget(eye, spec.id());
				}
			}
		} else {
			BlockHitResult hit = Targeting.blockHit(player, LOOK_RANGE);
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				anchor = hit.getBlockPos().above();
			} else {
				Vec3 look = player.getLookAngle();
				Vec3 ahead = player.position().add(look.x * 4.0D, 0.0D, look.z * 4.0D);
				anchor = BlockPos.containing(ahead);
				for (int i = 0; i < 6 && level.getBlockState(anchor.below()).isAir(); i++) {
					anchor = anchor.below();
				}
			}
		}
		if (spec.has(Flag.FIXED_ORIENTATION) || turn < 0) {
			turn = 0;
		}
		Vec3 at = Vec3.atCenterOf(anchor);
		Fx.sendTo(player, at, at, FxType.FORMASI, FormasiFxIds.PATTERN_SHOW, spec.netId() | (turn << 12), Math.max(1, minutes));
		Msg.actionBar(player, Component.translatable("kultivator.formasi.hologram.shown", spec.name(), DIRECTIONS[turn], minutes));
	}

	private static int hideHologram(ServerPlayer player) {
		Vec3 at = player.position();
		Fx.sendTo(player, at, at, FxType.FORMASI, FormasiFxIds.PATTERN_SHOW, 0, 0.0F);
		Msg.actionBar(player, Component.translatable("kultivator.formasi.hologram.hidden"));
		return 1;
	}

	/** The rotation of {@code spec} with the most OK slots at {@code eye}. */
	public static int bestRotation(ServerLevel level, BlockPos eye, FormationSpec spec) {
		if (spec.has(Flag.FIXED_ORIENTATION)) {
			return 0;
		}
		ChunkReader reader = new ChunkReader(level);
		int best = 0;
		int bestOk = -1;
		for (int rot = 0; rot < spec.pattern().distinctRotations(); rot++) {
			int ok = PatternMatcher.full(reader, eye, spec, rot).ok();
			if (ok > bestOk) {
				bestOk = ok;
				best = rot;
			}
		}
		return best;
	}

	private static @Nullable CoreRecord ownCore(ServerPlayer player) {
		CoreRecord record = lookedAtCore(player);
		if (record == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.look_at_eye").withStyle(ChatFormatting.YELLOW));
			return null;
		}
		if (!record.owner.equals(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.formasi.not_owner").withStyle(ChatFormatting.RED));
			return null;
		}
		return record;
	}

	private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CoreRecord record = ownCore(player);
		FormationSpec spec = record == null ? null : FormationRegistry.byId(record.spec);
		if (record == null || spec == null) {
			return 0;
		}
		record.on = on;
		ServerLevel level = (ServerLevel) player.level();
		CoreTicker.markDirty(level);
		CoreTicker.refreshState(level, record);
		Msg.chat(player, Component.translatable(on ? "kultivator.formasi.toggled_on" : "kultivator.formasi.toggled_off", spec.name(),
				record.state.label()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		return 1;
	}

	private static int deposit(CommandContext<CommandSourceStack> ctx, boolean all) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = Halls.viewedOwner(player);
		if (!owner.equals(player.getUUID()) && !Ownership.mates(player.level().getServer(), owner, player.getUUID())) {
			owner = player.getUUID();
		}
		int daya = Kas.depositInventory(player, owner, all);
		Msg.chat(player, Component.translatable(daya > 0 ? "kultivator.formasi.deposited" : "kultivator.formasi.deposit_none", daya)
				.withStyle(daya > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return daya > 0 ? 1 : 0;
	}

	private static int fill(CommandContext<CommandSourceStack> ctx, double hours) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CoreRecord record = ownCore(player);
		FormationSpec spec = record == null ? null : FormationRegistry.byId(record.spec);
		if (record == null || spec == null || record.virtual) {
			return 0;
		}
		long got = CoreTicker.draw(record, spec, hours);
		ServerLevel level = (ServerLevel) player.level();
		CoreTicker.markDirty(level);
		CoreTicker.refreshState(level, record);
		Msg.chat(player, Component.translatable(got > 0L ? "kultivator.formasi.filled" : "kultivator.formasi.fill_none", spec.name(), got / 100L)
				.withStyle(got > 0L ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return got > 0L ? 1 : 0;
	}

	private static int fillAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		long moved = 0L;
		for (CoreStore store : CoreTicker.stores()) {
			for (CoreRecord record : store.all()) {
				FormationSpec spec = FormationRegistry.byId(record.spec);
				if (spec != null && !record.virtual && record.owner.equals(player.getUUID())) {
					long got = CoreTicker.draw(record, spec, Upkeep.DRAW_HOURS);
					if (got > 0L) {
						moved += got;
						store.setDirty();
					}
				}
			}
		}
		Msg.chat(player, Component.translatable("kultivator.formasi.filled_all", moved / 100L).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int assignKeeper(CommandContext<CommandSourceStack> ctx, KeeperOrder order) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CoreRecord record = ownCore(player);
		if (record == null) {
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "nama");
		DiscipleEntity found = null;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.getName().getString().equalsIgnoreCase(name)) {
				found = disciple;
				break;
			}
		}
		if (found == null) {
			for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
				if (disciple.getName().getString().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
					found = disciple;
					break;
				}
			}
		}
		if (found == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.keeper.no_disciple", name).withStyle(ChatFormatting.RED));
			return 0;
		}
		Component why = Keepers.assign(player, (ServerLevel) player.level(), record, found, order, false);
		Msg.chat(player, why != null ? why.copy().withStyle(ChatFormatting.YELLOW)
				: Component.translatable("kultivator.formasi.keeper.assigned", found.getName()).withStyle(ChatFormatting.GREEN));
		return why == null ? 1 : 0;
	}

	private static int releaseKeepers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CoreRecord record = ownCore(player);
		if (record == null) {
			return 0;
		}
		int released = 0;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (record.hasKeeper(disciple.soulId())) {
				Keepers.release(disciple);
				released++;
			}
		}
		released += record.keepers.size();
		record.keepers.clear();
		record.pretend.clear();
		CoreTicker.markDirty((ServerLevel) player.level());
		Msg.chat(player, Component.translatable("kultivator.formasi.keeper.released", released).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	// ================================================================== actions and registries

	/** {@code Actions.FORMASI}: routes {@code op[:args]} to the handler registered for its first word. */
	public static void handleAction(ServerPlayer player, String text) {
		if (text == null || text.isEmpty() || text.length() > 256) {
			return;
		}
		int colon = text.indexOf(':');
		String op = colon < 0 ? text : text.substring(0, colon);
		BiConsumer<ServerPlayer, String> handler = ACTION_HANDLERS.get(op);
		if (handler == null) {
			return;
		}
		try {
			handler.accept(player, text);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Formasi action '{}' failed", op, exception);
		}
	}

	/** The "Fase 7c" test kit: eyes, every heart block, amethyst, end rods, crying obsidian (items only). */
	public static List<ItemStack> kitItems() {
		List<ItemStack> items = new ArrayList<>();
		items.add(new ItemStack(FormasiItems.FORMATION_EYE_ITEM, 8));
		Set<Item> hearts = new LinkedHashSet<>();
		for (FormationSpec spec : FormationRegistry.all()) {
			if (spec.has(Flag.LEGACY_MIRROR)) {
				continue;
			}
			for (Slot slot : spec.pattern().slots(0)) {
				if (slot.key() == Pattern.HEART) {
					Item item = slot.rule().canonical(0).getBlock().asItem();
					if (item != Items.AIR) {
						hearts.add(item);
					}
				}
			}
		}
		for (Item item : hearts) {
			items.add(new ItemStack(item));
		}
		items.add(new ItemStack(Items.AMETHYST_BLOCK, 64));
		items.add(new ItemStack(Items.AMETHYST_SHARD, 32));
		items.add(new ItemStack(Items.END_ROD, 16));
		items.add(new ItemStack(Items.CRYING_OBSIDIAN, 16));
		return items;
	}

	/** Module fields of {@code FormasiSync}. */
	public static void addSyncPart(SyncPart part) {
		SYNC_PARTS.add(part);
	}

	/** Module fields of the {@code formasi} part of {@code HallInfo}. */
	public static void addInfoPart(InfoPart part) {
		INFO_PARTS.add(part);
	}

	/** Handles {@code Actions.FORMASI} texts starting with {@code opPrefix} (B: "travel", "node"). */
	public static void addActionHandler(String opPrefix, BiConsumer<ServerPlayer, String> handler) {
		ACTION_HANDLERS.put(opPrefix, handler);
	}

	/** Replaces the empty-hand sneak-use of eyes of {@code family} (B: the node map on transport eyes). */
	public static void addUseHandler(Family family, UseHandler handler) {
		USE_HANDLERS.put(family, handler);
	}

	/** An amplifier term of power P (B: A1 and A2). */
	public static void addPowerBonus(PowerBonus bonus) {
		POWER_BONUSES.add(bonus);
	}

	/** The registered sync parts (read by {@code SyncSender}). */
	public static List<SyncPart> syncParts() {
		return Collections.unmodifiableList(SYNC_PARTS);
	}

	/** The registered info parts (read by {@code HallBridge}). */
	public static List<InfoPart> infoParts() {
		return Collections.unmodifiableList(INFO_PARTS);
	}

	/** The use handler of {@code family}, or null. */
	public static UseHandler useHandler(Family family) {
		return USE_HANDLERS.get(family);
	}

	/** The registered power bonuses (read by {@code Power}). */
	public static List<PowerBonus> powerBonuses() {
		return Collections.unmodifiableList(POWER_BONUSES);
	}

	/** True for a running state (helper for module B's views). */
	public static boolean running(CoreState state) {
		return state != null && state.running();
	}

	private Formasi() {
	}
}
