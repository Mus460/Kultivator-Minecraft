package dev.kultivator.disciple.path;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A disciple's path beyond the realms (DESIGN 20.3.18-20.3.21; module D, P6-jalan-abadi): Bintang Abadi stars and
 * calamities ({@link Stars}, {@link Calamity}, {@link SoulLamps}, {@link DivineClones}), bond and the Soul Seal
 * ({@link Bond}, {@link SoulSeal}), Hu Fa and the Dao foundations ({@link HuFa}, {@link FoundationTypes}), the heir and
 * the Nine Hexes ({@link Heir}), the Master's Soul Ring ({@link MasterRing}), surpassing the master ({@link Surpass}) and
 * the destroying arts ({@link DestroyerArts}). Everything lives in {@code DiscipleData.extra().path} and in the player's
 * {@code CultivationData.extra().path}; this class wires the hooks, events, actions and commands and holds the public
 * API of DESIGN 20.4.5.
 */
public final class DisciplePaths {

	/** Sub-compound of {@code DiscipleData.extra()} owned by module D. */
	public static final String EXTRA = "path";
	/** Sub-compound of the player's {@code CultivationData.extra()} owned by module D. */
	public static final String PLAYER_EXTRA = "path";

	/** Darah Dewa Kuno's talent bit (second row of the talent table of DESIGN 20.3.5; rolled by module A). */
	public static final int TALENT_ANCIENT_GOD_BLOOD = 1 << 1;
	/** Pembawa Simbol Melahap's physique id (fifth row of the physique table of DESIGN 20.3.5; rolled by module A). */
	public static final int PHYSIQUE_DEVOURING = 5;

	// Synced power bits of module D (DiscipleEntity.POWER_MASK_D) for the renderer and PathFx.
	public static final int BIT_SEALED = 1 << 16;
	public static final int BIT_HEIR = 1 << 17;
	/** The red seal is released (Mode Kehancuran lock 3): a red seal on the nametag, red sparks in the aura. */
	public static final int BIT_RELEASED = 1 << 18;
	public static final int BIT_RING = 1 << 19;
	public static final int BIT_DESCEND = 1 << 20;
	/** Penginjak Langit: the white-gold aura. */
	public static final int BIT_TRAMPLING = 1 << 21;
	public static final int BIT_SURPASSED = 1 << 22;
	/** A breakthrough or calamity guarded by Hu Fa right now. */
	public static final int BIT_GUARDED = 1 << 23;
	/** Foundation type (0 none, 1 Fana .. 4 Sempurna) in bits 24-26. */
	public static final int FOUNDATION_SHIFT = 24;
	public static final int BIT_NINE_HEXES = 1 << 27;
	/** Finished Trials (0-3) in bits 28-29. */
	public static final int TRIALS_SHIFT = 28;
	/** A calamity or lamp trial runs. */
	public static final int BIT_CALAMITY = 1 << 30;

	static final String PREFIX = "kultivator.dpath.";
	static final int GOLD = 0xFFD54A;
	static final int JADE = 0x8FE3C8;
	static final int REFUSE = 0xFF9A7A;
	static final int MUTED = 0xA8B5B0;
	static final int STAR = 0xE8F0FF;

	public static void init() {
		// Items first (registered when their classes load).
		SoulSeal.init();
		MasterRing.init();
		Stars.register();
		SoulLamps.register();
		HuFa.register();
		FoundationTypes.register();
		Bond.register();
		SoulSeal.register();
		Heir.register();
		MasterRing.register();
		Surpass.register();
		DivineClones.register();
		DestroyerArts.register();
		DiscipleHooks.addTick20(DisciplePaths::tick20);
		DiscipleHooks.addInfo(PathInfo::compose);
		Progression.addMultiplierProvider(Surpass::masterPride);

		// The jade and the ring are handed over before the disciple core turns a sneak-use into "open the screen".
		Identifier early = Kultivator.id("disciple_path_items");
		UseEntityCallback.EVENT.register(early, DisciplePaths::onUseEntity);
		UseEntityCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DisciplePaths::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(DisciplePaths::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(DisciplePaths::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
	}

	// ================================================================== actions

	/** {@code Actions.DISCIPLE_PATH}: {@code arg} = the disciple's entity id, {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String text = action.text() == null ? "" : action.text();
		int colon = text.indexOf(':');
		String op = colon >= 0 ? text.substring(0, colon) : text;
		String param = colon >= 0 ? text.substring(colon + 1) : "";
		DiscipleEntity entity = resolve(player, action.arg());
		if (entity == null) {
			return;
		}
		switch (op) {
			case "condense" -> Stars.order(entity, player);
			case "auto" -> Stars.setAuto(entity, player, "on".equals(param));
			case "calamity" -> Calamity.start(entity, player, false, false);
			case "lamp" -> SoulLamps.start(entity, player);
			case "recall" -> SoulSeal.recall(player, param);
			case "tahan" -> HuFa.requestTahan(player, entity);
			case "heir" -> Heir.request(player, entity, false);
			case "heir_confirm" -> Heir.request(player, entity, true);
			case "gen_hex" -> Heir.setGenerationHex(player, entity, param);
			case "hex" -> Heir.createHex(player, entity, param);
			case "descend" -> MasterRing.order(player, entity);
			case "honor" -> Surpass.setHonor(player, entity, "on".equals(param));
			case "duel" -> Surpass.startDuel(player, entity);
			case "art" -> DestroyerArts.choose(player, entity, param);
			case "cast" -> DestroyerArts.order(player, entity);
			default -> {
				return;
			}
		}
		if (entity.isAlive() && !entity.isRemoved()) {
			Disciples.sendInfo(player, entity);
		}
	}

	/** The player's own living disciple with this entity id within reach, or null (with a short reason). */
	static @Nullable DiscipleEntity resolve(ServerPlayer player, int entityId) {
		Entity entity = player.level().getEntity(entityId);
		if (!(entity instanceof DiscipleEntity disciple) || !disciple.isAlive() || disciple.isReleasing()
				|| disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			Msg.actionBar(player, Component.translatable(PREFIX + "gone").withStyle(ChatFormatting.GRAY));
			return null;
		}
		if (!disciple.isOwnedBy(player)) {
			Msg.chat(player, Component.translatable(PREFIX + "not_yours", disciple.getDisplayName()).withColor(REFUSE));
			return null;
		}
		if (player.distanceToSqr(disciple) > Disciples.ACTION_RANGE * Disciples.ACTION_RANGE) {
			Msg.chat(player, Component.translatable(PREFIX + "too_far", disciple.getDisplayName(), (int) Disciples.ACTION_RANGE)
					.withColor(REFUSE));
			return null;
		}
		return disciple;
	}

	// ================================================================== commands

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("murid").then(Commands.literal("tahan")
				.executes(ctx -> HuFa.tahanCommand(ctx.getSource().getPlayerOrException()))));

		LiteralArgumentBuilder<CommandSourceStack> murid = Commands.literal("murid");
		murid.then(Commands.literal("bintang").then(Commands.literal("set")
				.then(Commands.argument("n", IntegerArgumentType.integer(0, 100000)).executes(DisciplePaths::debugStars))));
		murid.then(Commands.literal("bencana").then(Commands.literal("mulai").executes(DisciplePaths::debugCalamity)));
		murid.then(Commands.literal("ikatan")
				.then(Commands.argument("n", IntegerArgumentType.integer(0, Bond.MAX)).executes(DisciplePaths::debugBond)));
		debug.then(murid);
	}

	private static int debugStars(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity disciple = debugTarget(player);
		if (disciple == null) {
			ctx.getSource().sendFailure(Component.translatable(PREFIX + "debug.no_target"));
			return 0;
		}
		int n = IntegerArgumentType.getInteger(ctx, "n");
		setStars(disciple, n);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(PREFIX + "debug.stars", disciple.getDisplayName(), n)), false);
		return 1;
	}

	private static int debugCalamity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity disciple = debugTarget(player);
		if (disciple == null) {
			ctx.getSource().sendFailure(Component.translatable(PREFIX + "debug.no_target"));
			return 0;
		}
		return Calamity.start(disciple, player, true, false) ? 1 : 0;
	}

	private static int debugBond(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity disciple = debugTarget(player);
		if (disciple == null) {
			ctx.getSource().sendFailure(Component.translatable(PREFIX + "debug.no_target"));
			return 0;
		}
		int n = IntegerArgumentType.getInteger(ctx, "n");
		section(disciple.data()).putInt("bond", Math.max(0, Math.min(Bond.MAX, n)));
		syncPower(disciple);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(PREFIX + "debug.bond", disciple.getDisplayName(), n)), false);
		return 1;
	}

	/** The disciple an operator looks at within 16 blocks, else the nearest one within 16 blocks (role DISCIPLE). */
	static @Nullable DiscipleEntity debugTarget(ServerPlayer player) {
		double range = Disciples.ACTION_RANGE;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult block = Targeting.blockHit(player, range);
		double maxSq = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(eye);
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(range)), sweep,
				entity -> entity instanceof DiscipleEntity disciple && disciple.isAlive() && disciple.role() == DiscipleEntity.Role.DISCIPLE, maxSq);
		if (hit != null && hit.getEntity() instanceof DiscipleEntity disciple) {
			return disciple;
		}
		DiscipleEntity best = null;
		double bestSq = range * range;
		for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, player.getBoundingBox().inflate(range),
				each -> each.isAlive() && each.role() == DiscipleEntity.Role.DISCIPLE)) {
			double distanceSq = disciple.distanceToSqr(player);
			if (distanceSq <= bestSq) {
				bestSq = distanceSq;
				best = disciple;
			}
		}
		return best;
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** Bintang Abadi stars (0 before True Immortal Peak). */
	public static int stars(DiscipleData data) {
		return Math.max(0, tag(data).getIntOr("stars", 0));
	}

	/** Rank step plus stars: how strong the disciple is on one scale (DESIGN 20.3.20). */
	public static int power(DiscipleData data) {
		return data.rank().step() + stars(data);
	}

	/** One full bar: the XP of the current rank, or the next star's cost at True Immortal Peak (DESIGN 20.3.19). */
	public static double barSize(DiscipleData data) {
		return data.rank().isMax() ? Stars.cost(stars(data) + 1) : data.xpRequired();
	}

	/**
	 * Gives {@code fraction} of a full bar as EXACT XP, without the entity (expeditions, events); at True Immortal Peak it
	 * becomes Qi Bintang (Stored Qi) for the next star. The caller calls {@code refreshSynced} when the body is loaded.
	 * Returns the XP given.
	 */
	public static double grantBarShare(DiscipleData data, double fraction) {
		double amount = barSize(data) * Math.max(0.0D, fraction);
		if (amount > 0.0D && Double.isFinite(amount)) {
			data.addTotalXp(amount);
			DiscipleCultivation.distribute(data, amount);
			return amount;
		}
		return 0.0D;
	}

	/** Bond with the master, 0-100. */
	public static int bond(DiscipleData data) {
		return Math.max(0, Math.min(Bond.MAX, tag(data).getIntOr("bond", 0)));
	}

	/** Adds bond (clamped 0-100); {@code reasonKey} is a translation key (tournament bouts, the war champion...). */
	public static void addBond(DiscipleData data, int amount, String reasonKey) {
		Bond.add(data, amount);
	}

	/** True for the master's heir (Pewaris). */
	public static boolean isHeir(DiscipleData data) {
		return tag(data).contains("heir");
	}

	/** Calamities passed (0-7). */
	public static int calamitiesPassed(DiscipleData data) {
		return Math.max(0, Math.min(Calamity.COUNT, tag(data).getIntOr("calamity", 0)));
	}

	/**
	 * Sets the Bintang Abadi star tier (test and debug commands): lifts the disciple to True Immortal Peak if needed and
	 * marks every calamity gated at or below {@code n} as passed, so the star count is consistent.
	 */
	public static void setStars(DiscipleEntity entity, int n) {
		DiscipleData data = entity.data();
		if (!data.rank().isMax()) {
			for (Realm each : Realm.values()) {
				if (!each.isMortal() && data.quality(each) == null) {
					data.setQuality(each, BreakthroughQuality.NORMAL);
				}
			}
			DiscipleCultivation.setRank(entity, Rank.ofStep(Rank.MAX_STEP));
		}
		int stars = Math.max(0, n);
		CompoundTag path = section(data);
		path.putInt("stars", stars);
		int passed = 0;
		for (int k = 1; k <= Calamity.COUNT; k++) {
			if (Stars.gateStar(data, k) <= stars) {
				passed = k;
			}
		}
		path.putInt("calamity", passed);
		path.putInt("trials", Calamity.trialsFor(passed));
		Stars.afterStarChange(entity, 0, stars, false);
		entity.refreshSynced();
		entity.setHealth(entity.getMaxHealth());
		syncPower(entity);
	}

	// ================================================================== events

	private static void tick20(DiscipleEntity entity) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE || !(entity.level() instanceof ServerLevel)) {
			return;
		}
		ServerPlayer master = entity.owner();
		Calamity.resolveStale(entity);
		Stars.tick20(entity, master);
		Bond.tick20(entity, master);
		SoulSeal.tick20(entity, master);
		Heir.tick20(entity, master);
		MasterRing.tick20(entity, master);
		Surpass.tick20(entity, master);
		DivineClones.tick20(entity, master);
		syncPower(entity);
	}

	/** Copies the star count and module D's visual flags into the entity's synced data. */
	public static void syncPower(DiscipleEntity entity) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE && entity.role() != DiscipleEntity.Role.CLONE) {
			return;
		}
		DiscipleData data = entity.data();
		CompoundTag path = tag(data);
		int bits = 0;
		if (path.getBooleanOr("sealed", false)) {
			bits |= BIT_SEALED;
		}
		if (path.contains("heir")) {
			bits |= BIT_HEIR;
			if (Heir.hexes(data).size() >= Heir.MAX_HEXES) {
				bits |= BIT_NINE_HEXES;
			}
		}
		if (path.getBooleanOr("released", false)) {
			bits |= BIT_RELEASED;
		}
		if (MasterRing.bonded(data)) {
			bits |= BIT_RING;
		}
		if (MasterRing.descending(entity)) {
			bits |= BIT_DESCEND;
		}
		if (calamitiesPassed(data) >= Calamity.COUNT) {
			bits |= BIT_TRAMPLING;
		}
		if (path.getBooleanOr("surpassed", false)) {
			bits |= BIT_SURPASSED;
		}
		if (HuFa.guarded(entity)) {
			bits |= BIT_GUARDED;
		}
		bits |= (FoundationTypes.type(data) & 0x7) << FOUNDATION_SHIFT;
		bits |= (Math.max(0, Math.min(3, path.getIntOr("trials", 0))) & 0x3) << TRIALS_SHIFT;
		if (Calamity.running(entity)) {
			bits |= BIT_CALAMITY;
		}
		entity.setSyncedPower(DiscipleEntity.POWER_MASK_D, bits);
		entity.setSyncedStars(stars(data));
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof DiscipleEntity disciple)
				|| disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean seal = stack.is(SoulSeal.ITEM);
		boolean ring = stack.is(MasterRing.ITEM);
		if (!seal && !ring || !disciple.isOwnedBy(player)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (disciple.isAlive() && !disciple.isReleasing()) {
			if (seal) {
				SoulSeal.use(serverPlayer, disciple, stack);
			} else {
				MasterRing.give(serverPlayer, disciple, stack);
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || !(victim.level() instanceof ServerLevel)) {
			return;
		}
		if (victim instanceof ServerPlayer master) {
			SoulSeal.onMasterHurt(master);
			Surpass.onMasterHurt(master);
		}
		if (source.getEntity() instanceof DiscipleEntity disciple && disciple != victim && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
			Heir.onHit(disciple, victim);
		}
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel)) {
			return;
		}
		HuFa.onDeath(victim, source);
		DivineClones.onDeath(victim);
	}

	private static void tick(MinecraftServer server) {
		PathTasks.tick(server);
		Calamity.tick(server);
		HuFa.tick(server);
		SoulSeal.tick(server);
		Heir.tick(server);
		MasterRing.tick(server);
		DivineClones.tick(server);
		Surpass.tick(server);
	}

	private static void clearAll() {
		PathTasks.clear();
		Calamity.clear();
		HuFa.clear();
		SoulSeal.clear();
		Heir.clear();
		MasterRing.clear();
		DivineClones.clear();
		Surpass.clear();
		DestroyerArts.clear();
	}

	// ================================================================== helpers

	/** {@code extra.path} for reading (a detached empty tag when missing). */
	static CompoundTag tag(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA);
	}

	/** {@code extra.path} for writing (created and kept when missing). */
	static CompoundTag section(DiscipleData data) {
		return data.extraSection(EXTRA);
	}

	/** A child compound of {@code parent}, created and kept when missing. */
	static CompoundTag child(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	/** The player's {@code extra.path} for writing (call {@code Cultivators.dirty} after a change). */
	static CompoundTag playerSection(ServerPlayer player) {
		return child(Cultivators.get(player).extra(), PLAYER_EXTRA);
	}

	/** The player's {@code extra.path} for reading. */
	static CompoundTag playerTag(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(PLAYER_EXTRA);
	}

	/** The master's own Bintang Abadi stars ({@code extra.kultivasi.stars}, 0 until phase 7). */
	static int masterStars(ServerPlayer master) {
		CultivationData data = Cultivators.get(master);
		return Math.max(0, data.extra().getCompoundOrEmpty("kultivasi").getIntOr("stars", 0));
	}

	/** In-game day index: overworld day time / 24000 (DESIGN 20.1). */
	static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / 24000L;
	}

	/** Overworld game time (cooldowns and timers that run on while the body is unloaded). */
	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Seconds left until {@code readyAt} (game time), 0 when ready. */
	static int secondsLeft(MinecraftServer server, long readyAt) {
		long left = readyAt - now(server);
		return left <= 0L ? 0 : (int) ((left + 19L) / 20L);
	}

	static Component name(DiscipleEntity entity) {
		return entity.getName().copy().withColor(0xF2E6C8);
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
	}

	/** True when {@code master} is online in the disciple's level within {@code range} blocks. */
	static boolean near(@Nullable ServerPlayer master, DiscipleEntity entity, double range) {
		return master != null && master.level() == entity.level() && master.distanceToSqr(entity) <= range * range;
	}

	private DisciplePaths() {
	}
}
