package dev.kultivator.dunia.naga;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The test commands of module B (DESIGN 34.5.11 / 34.5.12 "Uji"), all under {@code /kultivasi uji} (op):
 * <ul>
 * <li>{@code naga <air|awan|petir> [lemah|gila]}: a wild Jiao in front of you (lemah: already weakened, low and close;
 * gila: Gila Darah, as under the blood eclipse);</li>
 * <li>{@code naga amuk}: the nearest wild Jiao enters Amukan Naga; {@code naga jurus <napas|ekor|raung|lilit|gigit|khusus>}:
 * it uses that skill on you now;</li>
 * <li>{@code naga jaga}: your beasts and Jiao guard your spot for 60 s (Penjaga Raga without a soul journey);
 * {@code naga darah [kind]}: a Darah Jiao; {@code naga ucap}: your Jiao's remarks now; {@code naga langit
 * <meteor|gerhana|bulan>}: your Jiao and beasts react to that sky event;</li>
 * <li>{@code naga tunduk menang|kalah}: ends your contest of wills with that result, or starts one on the nearest Jiao;</li>
 * <li>{@code naga hapus}: removes the wild Jiao within 256 blocks and your tamed one;</li>
 * <li>{@code naga tunggang}: gives you a tamed Jiao if you have none, summons it and seats you on it;</li>
 * <li>{@code naga info}: the wild Jiao, your record, the multipliers;</li>
 * <li>{@code evolusi <4-6>}: the beast you look at (or your Jiao) jumps to that tier with the full evolution;</li>
 * <li>{@code evolusi tribulasi [lulus|gagal]}: a test tribulation on the beast you look at (no offering, no checks);</li>
 * <li>{@code evolusi garis <wolf|fox|jiao>}: a tier 3 beast of that line, with the XP for tier 4;</li>
 * <li>{@code evolusi info}: the beast you look at, its next tier, the offering, the running tribulations.</li>
 * </ul>
 */
final class NagaCommands {

	private static final double LOOK = 16.0D;

	static void init() {
		DuniaCommands.test("naga", NagaCommands::nagaNode);
		DuniaCommands.test("evolusi", NagaCommands::evolusiNode);
	}

	// ================================================================== naga

	private static LiteralArgumentBuilder<CommandSourceStack> nagaNode() {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("naga").executes(ctx -> info(ctx));
		for (NagaKind kind : NagaKind.values()) {
			node.then(Commands.literal(kind.id())
					.executes(ctx -> spawn(ctx, kind, false, false))
					.then(Commands.literal("lemah").executes(ctx -> spawn(ctx, kind, true, false)))
					.then(Commands.literal("gila").executes(ctx -> spawn(ctx, kind, false, true))));
		}
		node.then(Commands.literal("amuk").executes(NagaCommands::enrage));
		LiteralArgumentBuilder<CommandSourceStack> skills = Commands.literal("jurus");
		String[] names = {"napas", "ekor", "raung", "lilit", "gigit", "khusus"};
		int[] ids = {NagaLiarEntity.SKILL_BREATH, NagaLiarEntity.SKILL_TAIL, NagaLiarEntity.SKILL_ROAR, NagaLiarEntity.SKILL_COIL,
				NagaLiarEntity.SKILL_BITE, NagaLiarEntity.SKILL_SPECIAL};
		for (int i = 0; i < names.length; i++) {
			int id = ids[i];
			skills.then(Commands.literal(names[i]).executes(ctx -> skill(ctx, id)));
		}
		node.then(skills);
		node.then(Commands.literal("jaga").executes(NagaCommands::guard));
		LiteralArgumentBuilder<CommandSourceStack> blood = Commands.literal("darah");
		for (NagaKind kind : NagaKind.values()) {
			blood.then(Commands.literal(kind.id()).executes(ctx -> blood(ctx, kind)));
		}
		node.then(blood.executes(ctx -> blood(ctx, NagaKind.AIR)));
		node.then(Commands.literal("ucap").executes(NagaCommands::speak));
		node.then(Commands.literal("langit")
				.then(Commands.literal("meteor").executes(ctx -> sky(ctx, dev.kultivator.dunia.api.SkyKind.METEOR_SHOWER)))
				.then(Commands.literal("gerhana").executes(ctx -> sky(ctx, dev.kultivator.dunia.api.SkyKind.BLOOD_ECLIPSE)))
				.then(Commands.literal("bulan").executes(ctx -> sky(ctx, dev.kultivator.dunia.api.SkyKind.TWIN_MOONS))));
		node.then(Commands.literal("tunduk")
				.then(Commands.literal("menang").executes(ctx -> subdue(ctx, true)))
				.then(Commands.literal("kalah").executes(ctx -> subdue(ctx, false))));
		node.then(Commands.literal("hapus").executes(NagaCommands::remove));
		node.then(Commands.literal("tunggang").executes(NagaCommands::ride));
		node.then(Commands.literal("info").executes(NagaCommands::info));
		return node;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, NagaKind kind, boolean lemah, boolean frenzy) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
		Vec3 pos = lemah ? player.position().add(forward.scale(10.0D)).add(0.0D, 5.0D, 0.0D)
				: player.position().add(forward.scale(36.0D)).add(0.0D, 22.0D, 0.0D);
		pos = new Vec3(pos.x, Math.min(pos.y, level.getMaxY() - 8.0D), pos.z);
		NagaLiarEntity dragon = WildDragons.spawn(level, player, kind, pos, true, lemah, frenzy || WildDragons.sky(level,
				dev.kultivator.dunia.api.SkyKind.BLOOD_ECLIPSE));
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.spawn_failed"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(lemah ? "kultivator.naga.cmd.spawned_lemah" : "kultivator.naga.cmd.spawned",
				kind.displayName(), Msg.number(dragon.getMaxHealth()))), false);
		return 1;
	}

	private static int subdue(CommandContext<CommandSourceStack> ctx, boolean win) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (WillContest.forceEnd(player, win)) {
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.contest_forced")), false);
			return 1;
		}
		NagaLiarEntity dragon = nearestWild(player, 64.0D);
		if (dragon == null) {
			Vec3 pos = player.position().add(player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize().scale(10.0D)).add(0.0D, 5.0D, 0.0D);
			dragon = WildDragons.spawn(player.level(), player, NagaKind.AIR, pos, true, true, false);
		}
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.spawn_failed"));
			return 0;
		}
		if (TamedJiao.get(player) != null && win) {
			TamedJiao.remove(player);
		}
		if (!WillContest.begin(player, dragon, null, win)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.contest_failed"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.contest_started",
				win ? Component.translatable("kultivator.naga.cmd.win") : Component.translatable("kultivator.naga.cmd.lose"))), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int wild = 0;
		for (NagaLiarEntity dragon : WildDragons.all()) {
			if (dragon.level() == player.level() && dragon.distanceToSqr(player) < 256.0D * 256.0D) {
				dragon.vanish(player.level(), false);
				wild++;
			}
		}
		boolean tamed = TamedJiao.get(player) != null;
		WillContest.clearFor(player);
		TamedJiao.remove(player);
		int count = wild;
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.removed", count,
				tamed ? Component.translatable("gui.yes") : Component.translatable("gui.no"))), false);
		return wild + (tamed ? 1 : 0);
	}

	private static int ride(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (TamedJiao.get(player) == null) {
			TamedJiao.put(player, TamedJiao.Record.fresh(WildDragons.kindFor(player.level(), player)));
		}
		NagaLiarEntity dragon = TamedJiao.summon(player, true);
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.spawn_failed"));
			return 0;
		}
		dragon.setStamina(1.0F);
		dragon.setFlag(NagaLiarEntity.FLAG_TIRED, false);
		player.startRiding(dragon, true, true);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.riding", dragon.getDisplayName())), false);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaConfig config = Naga.config();
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.head").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.config", config.wild, config.minRealm().displayName(),
				Msg.number(config.chance * 100.0D), Msg.number(WildDragons.skyFactor(player.level())), config.worldCap, config.subdue,
				config.mount, config.evolution).withStyle(ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.config2", config.enrage, config.specials, config.guardBody,
				config.dialogue, Msg.number(config.darahChance * 100.0D)).withStyle(ChatFormatting.GRAY), false);
		List<NagaLiarEntity> wild = WildDragons.all();
		if (wild.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.none").withStyle(ChatFormatting.GRAY), false);
		}
		for (NagaLiarEntity dragon : wild) {
			double distance = dragon.level() == player.level() ? Math.sqrt(dragon.distanceToSqr(player)) : -1.0D;
			source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.wild", dragon.kind().displayName(), dragon.mode.name(),
					Msg.number(dragon.getHealth()), Msg.number(dragon.getMaxHealth()), dragon.blockPosition().toShortString(),
					distance < 0.0D ? "—" : Msg.number(distance)).withColor(dragon.kind().glow), false);
		}
		TamedJiao.Record record = TamedJiao.get(player);
		if (record != null) {
			NagaLiarEntity active = TamedJiao.active(player);
			source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.tamed",
					Component.translatable(BeastData.titleKey(BeastData.JIAO, record.tier())), record.kind().displayName(), record.tier(),
					Msg.number(record.xp()), active != null ? Component.translatable("kultivator.naga.cmd.info.out")
							: Component.translatable("kultivator.naga.cmd.info.pearl"), Msg.number(record.stamina() * 100.0D))
					.withColor(record.kind().glow), false);
		}
		for (String line : WillContest.describe()) {
			source.sendSuccess(() -> Component.literal(" ⚔ " + line).withStyle(ChatFormatting.DARK_AQUA), false);
		}
		for (String line : BodyGuards.describe()) {
			source.sendSuccess(() -> Component.literal(" ⛨ " + line).withStyle(ChatFormatting.GOLD), false);
		}
		source.sendSuccess(() -> Component.translatable("kultivator.naga.cmd.info.feats", Integer.bitCount(NagaFeats.kinds(player)),
				Msg.number(NagaFeats.ridden(player)), Msg.number(NagaFeats.RIDE_GOAL)).withStyle(ChatFormatting.GRAY), false);
		return wild.size();
	}

	/** {@code naga amuk}: the nearest wild Jiao enters Amukan Naga against you now. */
	private static int enrage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaLiarEntity dragon = nearestWild(player, 128.0D);
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.no_wild"));
			return 0;
		}
		dragon.forceEnrage(player.level(), player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.enraged", dragon.kind().displayName())), false);
		return 1;
	}

	/** {@code naga jurus <skill>}: the nearest wild Jiao uses that skill on you now. */
	private static int skill(CommandContext<CommandSourceStack> ctx, int id) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaLiarEntity dragon = nearestWild(player, 128.0D);
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.no_wild"));
			return 0;
		}
		dragon.forceSkill(player.level(), player, id);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.skill", dragon.kind().displayName(),
				Component.translatable("kultivator.naga.skill." + id))), false);
		return 1;
	}

	/** {@code naga jaga}: your beasts and Jiao guard the spot you stand on for 60 s, as they guard a soul's body. */
	private static int guard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int guards = BodyGuards.startTest(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.guard", guards)), false);
		return guards;
	}

	/** {@code naga darah [kind]}: a vial of Darah Jiao. */
	private static int blood(CommandContext<CommandSourceStack> ctx, NagaKind kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaItems.give(player, DarahJiaoItem.of(kind));
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.blood", kind.displayName())), false);
		return 1;
	}

	/** {@code naga ucap}: your summoned Jiao speaks its summon greeting and one rest remark now (no rate limit). */
	/**
	 * {@code naga langit <meteor|gerhana|bulan>}: your Jiao and tier 4+ beasts react as if that sky event had just begun
	 * (remarks, the eclipse howl); the sky itself does not change (module L's own {@code uji} starts real events).
	 */
	private static int sky(CommandContext<CommandSourceStack> ctx, dev.kultivator.dunia.api.SkyKind kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaLines.skyEvent(player.level(), kind, true, true);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.sky")), false);
		return 1;
	}

	private static int speak(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NagaLiarEntity dragon = TamedJiao.active(player);
		if (dragon == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.naga.cmd.no_tamed"));
			return 0;
		}
		NagaLines.tamed(player, dragon, NagaLines.greetEvent(player.level(), player), NagaLines.GREET, 1, true);
		NagaLines.tamed(player, dragon, "rest", NagaLines.REST, 1, true);
		NagaLines.tamed(player, dragon, "victory", NagaLines.VICTORY, 1, true);
		return 1;
	}

	private static @Nullable NagaLiarEntity nearestWild(ServerPlayer player, double range) {
		NagaLiarEntity best = null;
		double bestSq = range * range;
		for (NagaLiarEntity dragon : WildDragons.all()) {
			if (dragon.level() != player.level()) {
				continue;
			}
			double d = dragon.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = dragon;
			}
		}
		return best;
	}

	// ================================================================== evolusi

	private static LiteralArgumentBuilder<CommandSourceStack> evolusiNode() {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("evolusi").executes(NagaCommands::evolusiInfo);
		for (int tier = 4; tier <= BeastData.TOP_TIER; tier++) {
			int target = tier;
			node.then(Commands.literal(Integer.toString(tier)).executes(ctx -> setTier(ctx, target)));
		}
		node.then(Commands.literal("tribulasi")
				.executes(ctx -> tribulation(ctx, null))
				.then(Commands.literal("lulus").executes(ctx -> tribulation(ctx, true)))
				.then(Commands.literal("gagal").executes(ctx -> tribulation(ctx, false))));
		node.then(Commands.literal("garis")
				.then(Commands.literal("wolf").executes(ctx -> line(ctx, BeastData.WOLF)))
				.then(Commands.literal("fox").executes(ctx -> line(ctx, BeastData.FOX)))
				.then(Commands.literal("jiao").executes(ctx -> line(ctx, BeastData.JIAO))));
		node.then(Commands.literal("info").executes(NagaCommands::evolusiInfo));
		return node;
	}

	private static int setTier(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		LivingEntity beast = lookedAtBeast(player);
		if (beast == null) {
			NagaLiarEntity own = TamedJiao.active(player);
			if (own == null && TamedJiao.get(player) == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.no_target"));
				return 0;
			}
			TamedJiao.evolve(player, own, tier, true);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.tier", Component.translatable(
					BeastData.titleKey(BeastData.JIAO, tier)), tier)), false);
			return 1;
		}
		if (beast instanceof NagaLiarEntity dragon) {
			TamedJiao.evolve(player, dragon, tier, true);
		} else if (beast instanceof Mob mob) {
			SpiritBeasts.evolveTo(mob, tier, true);
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.tier", beast.getDisplayName(), tier)), false);
		return 1;
	}

	private static int tribulation(CommandContext<CommandSourceStack> ctx, @Nullable Boolean result) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		LivingEntity beast = lookedAtBeast(player);
		if (beast == null) {
			beast = TamedJiao.active(player);
		}
		if (beast == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.no_target"));
			return 0;
		}
		boolean started = BeastTribulation.start(player, beast, true, result);
		return started ? 1 : 0;
	}

	private static int line(CommandContext<CommandSourceStack> ctx, int kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		if (kind == BeastData.JIAO) {
			TamedJiao.Record record = TamedJiao.Record.fresh(NagaKind.AIR).withXp(BeastData.TIER_XP[4]);
			TamedJiao.remove(player);
			TamedJiao.put(player, record);
			NagaLiarEntity dragon = TamedJiao.summon(player, true);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.line", Component.translatable(
					BeastData.titleKey(BeastData.JIAO, 3)))), false);
			return dragon == null ? 0 : 1;
		}
		EntityType<? extends Mob> type = kind == BeastData.FOX ? EntityTypes.FOX : EntityTypes.WOLF;
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return 0;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 at = player.position().add(-Mth.sin(yaw) * 2.0D, 0.0D, Mth.cos(yaw) * 2.0D);
		Vec3 spot = Targeting.safeSpot(level, mob, at);
		Vec3 pos = spot == null ? player.position() : spot;
		mob.snapTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0F, 0.0F);
		if (mob instanceof Wolf wolf) {
			wolf.tame(player);
		}
		if (!level.addFreshEntity(mob)) {
			return 0;
		}
		if (!SpiritBeasts.awakenFor(player, mob)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.not_beast"));
			return 0;
		}
		SpiritBeasts.evolveTo(mob, BeastData.MAX_TIER, false);
		SpiritBeasts.addXp(mob, BeastData.TIER_XP[4] - BeastData.TIER_XP[BeastData.MAX_TIER]);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.naga.cmd.line", mob.getDisplayName())), false);
		return 1;
	}

	private static int evolusiInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		LivingEntity beast = lookedAtBeast(player);
		if (beast == null) {
			beast = TamedJiao.active(player);
		}
		if (beast != null) {
			Naga.status(player, beast);
		} else {
			ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.no_target"));
		}
		for (String line : BeastTribulation.describe()) {
			ctx.getSource().sendSuccess(() -> Component.literal(" ☁ " + line).withStyle(ChatFormatting.DARK_PURPLE), false);
		}
		return beast == null ? 0 : 1;
	}

	/** The awakened wolf / fox or tamed Jiao the player looks at within 16 blocks (any owner). */
	static @Nullable LivingEntity lookedAtBeast(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult block = Targeting.blockHit(player, LOOK);
		double maxSq = block.getType() == HitResult.Type.MISS ? LOOK * LOOK : block.getLocation().distanceToSqr(eye);
		Vec3 end = eye.add(look.scale(LOOK));
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(LOOK)).inflate(1.5D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> entity.isAlive() && (entity instanceof NagaLiarEntity dragon && dragon.isTamed()
						|| entity instanceof Wolf && entity.hasAttached(BeastData.TYPE) || entity instanceof Fox && entity.hasAttached(BeastData.TYPE)),
				maxSq);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	private NagaCommands() {
	}
}
