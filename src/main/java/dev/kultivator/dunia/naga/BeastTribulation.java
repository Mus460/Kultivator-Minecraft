package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kesengsaraan Binatang Roh, the beast tribulation (DESIGN 34.5.12): the way to tiers 4, 5 and 6. The owner sneak-uses
 * the beast holding its offering under the open sky. A 10 s omen (the cloud vortex gathers over the beast, which is held
 * still), then 3 / 5 / 7 telegraphed strikes (a ring closes on the beast for 30 ticks before each), each 12 / 16 / 20 % of
 * the beast's max health as the tribulation damage type (it ignores armour). The owner standing within 6 blocks is the Hu
 * Fa and takes 30 % of each strike instead of the beast, never lethally (the rule-3 clamp: at most health − 1). The beast
 * never dies: a strike that would kill it fails the tribulation instead (1 HP, Luka Kesengsaraan for one in-game day,
 * half the offering back). Passed: a light pillar on the beast, its size grows over 2 s, the evolution, beat 43 of the
 * world scenes and the card "Evolusi! ...". Every bolt is the particle bolt of the player tribulation
 * ({@code FxType.TRIBULATION_STRIKE}), never a LightningBolt; no block is touched.
 */
public final class BeastTribulation {

	public static final int OMEN_TICKS = 200;
	public static final int TELEGRAPH = 30;
	public static final int STRIKE_EVERY = 40;
	public static final int SUCCESS_TICKS = 70;
	public static final int FAILED_TICKS = 40;
	public static final double HU_FA_RANGE = 6.0D;
	public static final int GROW_TICKS = 40;
	/** The host floats this high over the beast (the cloud). */
	private static final double SKY = 14.0D;

	private static final class Session {
		final LivingEntity beast;
		final UUID owner;
		final int line;
		final int from;
		final int to;
		final int strikes;
		final double share;
		final boolean test;
		final @Nullable Boolean forced;
		final List<ItemStack> paid;
		final Vec3 anchor;
		KesengsaraanEntity host;
		int phase = KesengsaraanEntity.PHASE_OMEN;
		int ticks;
		int done;
		/** Strikes the owner stood through as Hu Fa (all of them: the advancement Hu Fa Setia). */
		int guarded;
		double scaleFrom;
		double scaleTo;

		Session(LivingEntity beast, UUID owner, int line, int from, int to, int strikes, double share, boolean test, @Nullable Boolean forced,
				List<ItemStack> paid) {
			this.beast = beast;
			this.owner = owner;
			this.line = line;
			this.from = from;
			this.to = to;
			this.strikes = strikes;
			this.share = share;
			this.test = test;
			this.forced = forced;
			this.paid = paid;
			this.anchor = beast.position();
		}
	}

	/** Beast entity id → its tribulation. */
	private static final Map<Integer, Session> SESSIONS = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BeastTribulation::tick);
		// the beast never dies of anything while its tribulation runs: a lethal hit fails it at 1 HP instead
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			Session session = SESSIONS.get(entity.getId());
			if (session == null || session.beast != entity || session.phase >= KesengsaraanEntity.PHASE_SUCCESS) {
				return true;
			}
			entity.setHealth(1.0F);
			fail(session, true);
			return false;
		});
		// the owner leaves (or the server stops): half the offering comes back while the player is still here
		ServerPlayerEvents.LEAVE.register(player -> abandon(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> abandon(null));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	private static void abandon(@Nullable UUID owner) {
		for (Session session : new ArrayList<>(SESSIONS.values())) {
			if (session.phase >= KesengsaraanEntity.PHASE_SUCCESS || owner != null && !owner.equals(session.owner)) {
				continue;
			}
			ServerPlayer player = session.beast.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(session.owner) : null;
			if (player != null) {
				refundHalf(player, session);
			}
			end(session);
		}
	}

	static boolean running(LivingEntity beast) {
		Session session = SESSIONS.get(beast.getId());
		return session != null && session.beast == beast;
	}

	static boolean hosts(KesengsaraanEntity host) {
		for (Session session : SESSIONS.values()) {
			if (session.host == host) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Starts the tribulation of {@code beast} for {@code owner}. {@code test} skips XP, realm, Luka, the sky and the
	 * offering; {@code forced} (tests) fixes the result (true passes, false fails on the last strike). False with a
	 * message when it cannot start.
	 */
	static boolean start(ServerPlayer owner, LivingEntity beast, boolean test, @Nullable Boolean forced) {
		Component problem = BeastEvolution.problem(owner, beast, test);
		if (problem != null) {
			Msg.chat(owner, Component.translatable("kultivator.naga.evolusi.cannot").append(" ").append(problem).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		BeastData data = BeastEvolution.dataOf(beast, owner);
		if (data == null || !(beast.level() instanceof ServerLevel level)) {
			return false;
		}
		int line = BeastEvolution.lineOf(beast);
		int to = data.tier() + 1;
		List<ItemStack> paid = test ? List.of() : BeastEvolution.resolve(owner, BeastEvolution.offering(line, to), true);
		if (paid == null) {
			return false;
		}
		NagaConfig config = Naga.config();
		Session session = new Session(beast, owner.getUUID(), line, data.tier(), to, config.strikes(to), config.share(to), test, forced,
				new ArrayList<>(paid));
		KesengsaraanEntity host = NagaEntities.KESENGSARAAN.create(level, EntitySpawnReason.TRIGGERED);
		if (host == null) {
			return false;
		}
		Vec3 sky = beast.position().add(0.0D, SKY, 0.0D);
		host.snapTo(sky.x, sky.y, sky.z, 0.0F, 0.0F);
		host.setUp(beast.getId(), session.strikes, SpiritBeasts.tierColor(line, to));
		if (!level.addFreshEntity(host)) {
			return false;
		}
		session.host = host;
		SESSIONS.put(beast.getId(), session);
		if (beast instanceof NagaLiarEntity dragon) {
			dragon.setFlag(NagaLiarEntity.FLAG_TRIBULATION, true);
		}
		level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.2F, 0.6F);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 0.5F);
		NagaVoice.heaven(level, beast.position(), NagaVoice.TRIBULATION, "heaven.witness", 48.0D);
		int length = OMEN_TICKS + TELEGRAPH + (session.strikes - 1) * STRIKE_EVERY + 40;
		NagaPayloads.moment(level, NagaPayloads.MOMENT_TRIB_START, beast.getId(), beast.position(), beast instanceof NagaLiarEntity dragon
				? dragon.kind().ordinal() : 0, to, line, length, owner, 64.0D);
		Msg.title(owner, Component.translatable("kultivator.naga.trib.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
				Component.translatable("kultivator.naga.trib.subtitle", beast.getDisplayName(), to, session.strikes).withStyle(ChatFormatting.LIGHT_PURPLE),
				10, 60, 20);
		Msg.chat(owner, Component.translatable("kultivator.naga.trib.start", beast.getDisplayName(), to, session.strikes,
				Math.round(session.share * 100.0D), Math.round(config.huFaShare * 100.0D)).withStyle(ChatFormatting.LIGHT_PURPLE));
		Msg.chat(owner, Component.translatable("kultivator.naga.evolusi.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Session> iterator = new ArrayList<>(SESSIONS.values()).iterator();
		while (iterator.hasNext()) {
			Session session = iterator.next();
			try {
				step(server, session);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Beast tribulation failed", exception);
				end(session);
			}
		}
	}

	private static void step(MinecraftServer server, Session session) {
		LivingEntity beast = session.beast;
		ServerPlayer owner = server.getPlayerList().getPlayer(session.owner);
		if (beast.isRemoved() || !(beast.level() instanceof ServerLevel level) || session.host == null || session.host.isRemoved()) {
			// gone (unloaded, dismissed, another dimension): half the offering comes back
			if (owner != null) {
				refundHalf(owner, session);
				Msg.chat(owner, Component.translatable("kultivator.naga.trib.abort").withStyle(ChatFormatting.GRAY));
			}
			end(session);
			return;
		}
		session.ticks++;
		KesengsaraanEntity host = session.host;
		host.setPhaseTicks(session.ticks);
		Vec3 sky = beast.position().add(0.0D, SKY, 0.0D);
		host.snapTo(sky.x, sky.y, sky.z, 0.0F, 0.0F);
		if (session.phase < KesengsaraanEntity.PHASE_SUCCESS) {
			hold(beast, session);
		}
		boolean guard = owner != null && owner.level() == level && owner.isAlive() && !owner.isSpectator()
				&& owner.distanceToSqr(beast) <= HU_FA_RANGE * HU_FA_RANGE;
		host.setGuard(guard ? owner.getId() : -1);
		switch (session.phase) {
			case KesengsaraanEntity.PHASE_OMEN -> omen(level, session, owner);
			case KesengsaraanEntity.PHASE_STRIKES -> strikes(level, session, owner, guard);
			case KesengsaraanEntity.PHASE_SUCCESS -> success(level, session, owner);
			default -> {
				if (session.ticks >= FAILED_TICKS) {
					end(session);
				}
			}
		}
	}

	/** The beast stays under its cloud: no walking, no teleporting after its owner. */
	private static void hold(LivingEntity beast, Session session) {
		if (beast instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		if (!(beast instanceof NagaLiarEntity)) {
			beast.setDeltaMovement(0.0D, Math.min(0.0D, beast.getDeltaMovement().y), 0.0D);
			if (beast.position().distanceToSqr(session.anchor) > 4.0D) {
				beast.snapTo(session.anchor.x, session.anchor.y, session.anchor.z, beast.getYRot(), beast.getXRot());
			}
		}
	}

	private static void omen(ServerLevel level, Session session, @Nullable ServerPlayer owner) {
		LivingEntity beast = session.beast;
		int left = OMEN_TICKS - session.ticks;
		if (session.ticks % 20 == 0) {
			// the shared tribulation cloud (TribulationFx) gathers: b is the remaining time on its 240-tick scale
			Fx.send(level, beast.position(), session.host.position(), FxType.TRIBULATION_OMEN, 0, Math.max(0, left * 240 / OMEN_TICKS), 0.0F);
			if (owner != null) {
				Msg.actionBar(owner, Component.translatable("kultivator.naga.trib.omen", (left + 19) / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		if (session.ticks % 60 == 0) {
			level.playSound(null, beast.getX(), beast.getY() + SKY, beast.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6F,
					0.5F + session.ticks / 400.0F);
		}
		if (left <= 0) {
			session.phase = KesengsaraanEntity.PHASE_STRIKES;
			session.ticks = 0;
			session.host.setPhase(KesengsaraanEntity.PHASE_STRIKES);
		}
	}

	private static void strikes(ServerLevel level, Session session, @Nullable ServerPlayer owner, boolean guard) {
		int sinceFirst = session.ticks - TELEGRAPH;
		int next = sinceFirst < 0 ? -sinceFirst : (STRIKE_EVERY - sinceFirst % STRIKE_EVERY) % STRIKE_EVERY;
		session.host.setNext(session.done >= session.strikes ? -1 : next);
		if (session.ticks % 20 == 0) {
			Fx.send(level, session.beast.position(), session.host.position(), FxType.TRIBULATION_OMEN, 0, 0, 0.0F);
		}
		if (session.done >= session.strikes) {
			if (session.ticks >= TELEGRAPH + (session.strikes - 1) * STRIKE_EVERY + 20) {
				beginSuccess(level, session, owner);
			}
			return;
		}
		if (sinceFirst >= 0 && sinceFirst % STRIKE_EVERY == 0) {
			session.done++;
			session.host.setDone(session.done);
			strike(level, session, owner, guard);
		}
	}

	/** One strike: the bolt, the damage on the beast, the Hu Fa's share. */
	private static void strike(ServerLevel level, Session session, @Nullable ServerPlayer owner, boolean guard) {
		LivingEntity beast = session.beast;
		boolean last = session.done >= session.strikes;
		Vec3 feet = beast.position();
		Fx.send(level, feet, session.host.position(), FxType.TRIBULATION_STRIKE, 0, session.done, last ? 2.0F : 1.0F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.6F, 0.8F + 0.05F * session.done);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.2F, 1.0F);
		float amount = (float) (beast.getMaxHealth() * session.share);
		float ownerPart = guard ? (float) (amount * Naga.config().huFaShare) : 0.0F;
		float beastPart = amount - ownerPart;
		if (Boolean.TRUE.equals(session.forced)) {
			beastPart = Math.min(beastPart, beast.getHealth() - 1.0F);
		} else if (Boolean.FALSE.equals(session.forced) && last) {
			beastPart = beast.getHealth() + 1.0F;
		}
		if (beastPart >= beast.getHealth()) {
			beast.setHealth(1.0F);
			fail(session, false);
			return;
		}
		if (beastPart > 0.0F) {
			beast.hurtServer(level, Tribulation.damageSource(level), beastPart);
			if (beast.isAlive() && beast.getHealth() < 1.0F) {
				beast.setHealth(1.0F);
			}
		}
		if (guard && owner != null && ownerPart > 0.0F) {
			session.guarded++;
			guardShare(level, owner, beast, ownerPart);
		}
		if (owner != null) {
			Msg.actionBar(owner, Component.translatable(last ? "kultivator.naga.trib.strike_last" : "kultivator.naga.trib.strike", session.done,
					session.strikes, Msg.number(beast.getHealth()), Msg.number(beast.getMaxHealth())).withStyle(last ? ChatFormatting.GOLD
					: ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** The Hu Fa takes its share: never lethal, nothing for creative or invulnerable players. */
	private static void guardShare(ServerLevel level, ServerPlayer owner, LivingEntity beast, float share) {
		Fx.beam(level, owner.position().add(0.0D, owner.getBbHeight() * 0.6D, 0.0D), beast.position().add(0.0D, beast.getBbHeight() * 0.6D, 0.0D),
				0xFFE08A, 0.1F);
		if (owner.isCreative() || owner.getAbilities().invulnerable) {
			return;
		}
		float pool = owner.getHealth() + owner.getAbsorptionAmount();
		float dealt = Math.min(share, pool - 1.0F);
		if (dealt > 0.0F) {
			owner.hurtServer(level, Tribulation.damageSource(level), dealt);
		}
		if (owner.isAlive() && owner.getHealth() < 1.0F) {
			owner.setHealth(1.0F);
		}
		Msg.chat(owner, Component.translatable("kultivator.naga.trib.hufa", Msg.number(dealt)).withStyle(ChatFormatting.GOLD));
	}

	private static void beginSuccess(ServerLevel level, Session session, @Nullable ServerPlayer owner) {
		session.phase = KesengsaraanEntity.PHASE_SUCCESS;
		session.ticks = 0;
		session.host.setPhase(KesengsaraanEntity.PHASE_SUCCESS);
		session.host.setNext(-1);
		LivingEntity beast = session.beast;
		if (beast instanceof NagaLiarEntity dragon) {
			dragon.setFlag(NagaLiarEntity.FLAG_TRIBULATION, false);
		}
		Fx.send(level, beast.position(), session.host.position(), FxType.TRIBULATION_END, 0, 1, 0.0F);
		AttributeInstance scale = beast.getAttribute(Attributes.SCALE);
		AttributeModifier before = scale == null ? null : scale.getModifier(SpiritBeasts.SCALE_ID);
		session.scaleFrom = before == null ? 0.0D : before.amount();
		if (owner == null) {
			end(session);
			return;
		}
		if (beast instanceof NagaLiarEntity dragon) {
			TamedJiao.evolve(owner, dragon, session.to, true);
		} else if (beast instanceof Mob mob) {
			SpiritBeasts.evolveTo(mob, session.to, true);
		}
		beast.setHealth(beast.getMaxHealth());
		AttributeModifier after = scale == null ? null : scale.getModifier(SpiritBeasts.SCALE_ID);
		session.scaleTo = after == null ? 0.0D : after.amount();
		setScale(beast, session.scaleFrom);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.5F, 0.7F);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.1F);
		NagaVoice.heaven(level, beast.position(), NagaVoice.EVOLVED, "heaven.breakthrough", 48.0D);
		if (!session.test && session.guarded >= session.strikes) {
			dev.kultivator.util.Advancements.grant(owner, "dunia/hufa");
		}
		NagaPayloads.moment(level, NagaPayloads.MOMENT_EVOLVED, beast.getId(), beast.position(), beast instanceof NagaLiarEntity dragon
				? dragon.kind().ordinal() : 0, session.to, session.line, beast.getId(), owner, 96.0D);
	}

	private static void success(ServerLevel level, Session session, @Nullable ServerPlayer owner) {
		if (session.ticks <= GROW_TICKS) {
			// the body grows over 2 s instead of popping
			float t = session.ticks / (float) GROW_TICKS;
			float eased = t * t * (3.0F - 2.0F * t);
			setScale(session.beast, Mth.lerp(eased, session.scaleFrom, session.scaleTo));
		}
		if (session.ticks % 5 == 0 && session.ticks < 40) {
			Fx.pillar(level, session.beast.position(), SpiritBeasts.tierColor(session.line, session.to), 18.0F);
		}
		if (session.ticks >= SUCCESS_TICKS) {
			setScale(session.beast, session.scaleTo);
			end(session);
		}
	}

	private static void setScale(LivingEntity beast, double amount) {
		if (beast instanceof NagaLiarEntity) {
			return;
		}
		AttributeInstance scale = beast.getAttribute(Attributes.SCALE);
		if (scale == null) {
			return;
		}
		if (Math.abs(amount) < 1.0E-4D) {
			scale.removeModifier(SpiritBeasts.SCALE_ID);
		} else {
			scale.addOrReplacePermanentModifier(new AttributeModifier(SpiritBeasts.SCALE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** A strike would have killed it (or it would have died anyway): 1 HP, Luka Kesengsaraan, half the offering back. */
	private static void fail(Session session, boolean fromDeath) {
		if (session.phase >= KesengsaraanEntity.PHASE_SUCCESS) {
			return;
		}
		LivingEntity beast = session.beast;
		session.phase = KesengsaraanEntity.PHASE_FAILED;
		session.ticks = 0;
		if (session.host != null) {
			session.host.setPhase(KesengsaraanEntity.PHASE_FAILED);
			session.host.setNext(-1);
		}
		if (beast instanceof NagaLiarEntity dragon) {
			dragon.setFlag(NagaLiarEntity.FLAG_TRIBULATION, false);
		}
		if (!(beast.level() instanceof ServerLevel level)) {
			end(session);
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(session.owner);
		if (!session.test) {
			long until = level.getGameTime() + Naga.config().failCooldownDays * 24000L;
			BeastEvolution.setLuka(beast, owner, until);
		}
		Fx.send(level, beast.position(), beast.position().add(0.0D, SKY, 0.0D), FxType.TRIBULATION_END, 0, 0, 0.0F);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.NEUTRAL, 0.6F, 1.6F);
		NagaVoice.heaven(level, beast.position(), NagaVoice.TRIB_FAILED, "heaven.tribulation_fail", 48.0D);
		if (owner != null) {
			refundHalf(owner, session);
			Msg.title(owner, Component.translatable("kultivator.naga.trib.failed.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.translatable("kultivator.naga.trib.failed.subtitle", beast.getDisplayName()).withStyle(ChatFormatting.GRAY), 10, 50, 20);
			Msg.chat(owner, Component.translatable("kultivator.naga.trib.failed", beast.getDisplayName(), Naga.config().failCooldownDays)
					.withStyle(ChatFormatting.RED));
			NagaPayloads.moment(level, NagaPayloads.MOMENT_FAILED, beast.getId(), beast.position(), 0, session.to, session.line, beast.getId(),
					owner, 64.0D);
		}
	}

	private static void refundHalf(ServerPlayer owner, Session session) {
		for (ItemStack paid : session.paid) {
			int back = paid.getCount() / 2;
			if (back > 0) {
				NagaItems.give(owner, new ItemStack(paid.getItem(), back));
			}
		}
		session.paid.clear();
	}

	private static void end(Session session) {
		SESSIONS.remove(session.beast.getId());
		if (session.beast instanceof NagaLiarEntity dragon) {
			dragon.setFlag(NagaLiarEntity.FLAG_TRIBULATION, false);
		}
		if (session.host != null && !session.host.isRemoved()) {
			session.host.discard();
		}
	}

	/** {@code uji evolusi info}: the running tribulations. */
	static List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Session session : SESSIONS.values()) {
			lines.add(session.beast.getName().getString() + " → " + session.to + " phase " + session.phase + " strike " + session.done + "/"
					+ session.strikes);
		}
		return lines;
	}

	private BeastTribulation() {
	}
}
