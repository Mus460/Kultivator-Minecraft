package dev.kultivator.dunia.reruntuhan.penjara;

import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RItems;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.talisman.Talismans;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Aula Rantai deep under the shrine (anchor {@code penjara/segel}, DESIGN 34.5.6): the Jantung Segel (the seal core,
 * a {@link TekaPartEntity#CORE} for Perkuat Segel), the five seal pillars on the ring ({@link TekaPartEntity#PILLAR},
 * re-lightable during the fight) and the demon king's soft arena (22 blocks). When the seal is broken and someone
 * stands in the hall the break plays (beat 41 on the clients: chains snap, the floor cracks, a red pillar, the sky
 * darkens within 128 blocks) and the king rises from the pit with 3-6 lesser demons from the cells.
 * <p>
 * Re-seal win: every pillar re-lit (a 3 s channel each, broken by a hit; each lit pillar weakens the king's blows by
 * 15 %) seals the king again: integrity 100, the bigger Pahala and a T3 treasure born at the shrine. Kill win: Inti Iblis
 * Kuno, King cores and a demonic jade slip for everyone who took part, and the prison is calm for {@code rebreakDays}.
 * Nobody near for ten seconds: the king sinks back and keeps its health in the seal.
 */
public final class CoreRun extends AnchorRun {

	public static final int CHANNEL = 60;
	public static final double ARENA = 22.0D;
	private @Nullable TekaPartEntity core;
	private final TekaPartEntity[] pillars = new TekaPartEntity[5];
	private final boolean[] lit = new boolean[5];
	private @Nullable RajaIblisEntity king;
	private final List<IblisKecilEntity> lessers = new ArrayList<>();
	private final Map<UUID, Float> damage = new HashMap<>();
	private final Map<UUID, Integer> present = new HashMap<>();
	private final Map<UUID, int[]> channels = new HashMap<>();
	private final ServerBossEvent bar;
	private long riseAt = -1L;
	private int empty;

	public CoreRun(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()), Component.empty(), BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_20);
	}

	public Prisons.Seal seal() {
		return Prisons.seal(level, anchor);
	}

	public long prisonId() {
		return Prisons.idOf(anchor);
	}

	@Override
	public double radius() {
		return 48.0D;
	}

	// ================================================================== parts

	@Override
	protected void spawn() {
		TekaPartEntity made = make(REntities.SEGEL_JIMAT, centre(), 0.0F);
		if (made != null) {
			made.setRole(TekaPartEntity.CORE);
			made.setCenter(centre());
			made.setListener((player, part, attack) -> {
				if (player.isShiftKeyDown()) {
					tradePahala(player);
				} else {
					reinforce(player);
				}
			});
			if (add(made)) {
				core = made;
			}
		}
		int n = Frame.triples(data(), "pillars");
		for (int i = 0; i < Math.min(5, n); i++) {
			int[] off = Frame.triple(data(), "pillars", i);
			TekaPartEntity pillar = make(REntities.SEGEL_JIMAT, frame.bottom(off[0], off[1], off[2]), 0.0F);
			if (pillar == null) {
				continue;
			}
			pillar.setRole(TekaPartEntity.PILLAR);
			pillar.setCenter(centre());
			pillar.setArg(i);
			pillar.setState(TekaPartEntity.LOCKED);
			final int index = i;
			pillar.setListener((player, part, attack) -> beginChannel(player, index));
			if (add(pillar)) {
				pillars[i] = pillar;
			}
		}
	}

	/** Perkuat Segel at the core: 4 blank talismans and 1 High stone, +15 integrity, once per player per in-game day. */
	private void reinforce(ServerPlayer player) {
		Prisons.Seal seal = seal();
		if (seal.broken()) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.core.broken").withStyle(ChatFormatting.RED));
			return;
		}
		if (seal.integrity() >= 100) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.core.whole").withStyle(ChatFormatting.GREEN));
			return;
		}
		long day = level.getGameTime() / 24000L;
		if (Prisons.reinforcedToday(seal, player, day)) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.core.today").withStyle(ChatFormatting.GRAY));
			return;
		}
		Item high = HartaItems.stoneItem(SpiritStones.HIGH);
		// A Sobekan Segel stands for two blank talismans (the torn paper still holds the seal's power).
		int blanks = player.getInventory().countItem(Talismans.BLANK);
		int shards = player.getInventory().countItem(RItems.SOBEKAN_SEGEL);
		if (!player.isCreative() && (blanks + shards * Prisons.SHARD_AS_BLANKS < 4 || player.getInventory().countItem(high) < 1)) {
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.core.need", 4, SpiritStones.name(SpiritStones.HIGH))
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isCreative()) {
			int fromBlanks = Math.min(4, blanks);
			take(player, Talismans.BLANK, fromBlanks);
			int rest = 4 - fromBlanks;
			if (rest > 0) {
				take(player, RItems.SOBEKAN_SEGEL, (rest + Prisons.SHARD_AS_BLANKS - 1) / Prisons.SHARD_AS_BLANKS);
			}
			take(player, high, 1);
		}
		Prisons.markReinforced(level, seal, player, day);
		Prisons.change(level, seal, seal.integrity + Prisons.REINFORCE);
		renown(player, 10, "kultivator.reruntuhan.penjara.renown.reinforce");
		RRewards.give(player, new ItemStack(Talismans.BLANK, 2));
		addPahala(player, 1);
		RFx.at(level, centre().add(0.0D, 2.0D, 0.0D), RFx.SEAL_SEALED, 0, seal.integrity(), 1.2F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.8F);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.core.done", seal.integrity()).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.pahala.hint", Reruntuhan.data(player).getIntOr("pahala", 0),
				Prisons.PAHALA_PER_JADE).withStyle(ChatFormatting.GRAY));
		if (Prisons.eclipse(level)) {
			Advancements.grant(player, "dunia/eclipse_seal");
		}
	}

	private static void take(ServerPlayer player, Item item, int count) {
		int need = count;
		for (int i = 0; i < player.getInventory().getContainerSize() && need > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int t = Math.min(need, stack.getCount());
				stack.shrink(t);
				need -= t;
			}
		}
	}

	/**
	 * Sneak + use on the Jantung Segel: {@link Prisons#PAHALA_PER_JADE} Pahala buy one universal Giok Warisan (an
	 * inheritance trial; balance change approved by the user, integration audit S22).
	 */
	private void tradePahala(ServerPlayer player) {
		CompoundTag own = Reruntuhan.data(player);
		int pahala = own.getIntOr("pahala", 0);
		if (pahala < Prisons.PAHALA_PER_JADE) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.pahala.kurang", pahala, Prisons.PAHALA_PER_JADE)
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		ItemStack jade = RRewards.jade(dev.kultivator.technique.source.InheritanceTrials.UNIVERSAL);
		if (jade.isEmpty()) {
			return;
		}
		own.putInt("pahala", pahala - Prisons.PAHALA_PER_JADE);
		dev.kultivator.data.Cultivators.dirty(player);
		RRewards.give(player, jade);
		RFx.at(level, centre().add(0.0D, 2.0D, 0.0D), RFx.SEAL_SEALED, 0, seal().integrity(), 0.8F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.8F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.pahala.tukar", Prisons.PAHALA_PER_JADE, pahala - Prisons.PAHALA_PER_JADE)
				.withStyle(ChatFormatting.GOLD));
	}

	/** Pahala: a small merit tally in {@code extra.reruntuhan.pahala} (shown on the Dunia page) and the sect's renown. */
	private static void addPahala(ServerPlayer player, int points) {
		CompoundTag own = Reruntuhan.data(player);
		own.putInt("pahala", own.getIntOr("pahala", 0) + points);
		dev.kultivator.data.Cultivators.dirty(player);
	}

	private static void renown(ServerPlayer player, int amount, String reason) {
		Sect sect = Sects.sectOf(player);
		UUID owner = sect != null ? sect.master() : player.getUUID();
		Halls.addRenown(owner, amount, reason);
	}

	private void beginChannel(ServerPlayer player, int index) {
		if (king == null || index < 0 || index > 4 || lit[index] || channels.containsKey(player.getUUID())) {
			return;
		}
		channels.put(player.getUUID(), new int[]{index, CHANNEL});
		Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.pilar.channel").withStyle(ChatFormatting.AQUA));
	}

	// ================================================================== the fight

	/** The seal just broke (Prisons): play the break now if someone is in the hall. */
	void onBreak() {
		riseAt = -1L;
	}

	@Override
	protected void tick(long now) {
		Prisons.Seal seal = seal();
		List<ServerPlayer> inHall = playersWithin(ARENA + 2.0D);
		for (int i = 0; i < 5; i++) {
			TekaPartEntity pillar = pillars[i];
			if (pillar != null) {
				pillar.setState(king == null ? TekaPartEntity.LOCKED : lit[i] ? TekaPartEntity.LIT : TekaPartEntity.IDLE);
			}
		}
		if (core != null) {
			core.setArg(seal.integrity());
			core.setState(seal.broken() ? TekaPartEntity.WRONG : TekaPartEntity.IDLE);
		}
		if (now % 40L == 0L) {
			RFx.at(level, centre(), RFx.SEAL_LEAK, seal.broken() ? 2 : seal.integrity() < 30 ? 1 : 0, seal.integrity(), 1.6F);
		}
		RajaIblisEntity current = king;
		if (current != null && current.isRemoved()) {
			king = null;
			current = null;
		}
		if (current == null) {
			bar.removeAllPlayers();
			if (seal.broken() && !inHall.isEmpty() && Reruntuhan.config().prisons && !anyoneMayWake(inHall)) {
				// Only weaker cultivators in the hall: the king stirs below but does not rise for them (integration audit T7).
				riseAt = -1L;
				if (now % 100L == 0L) {
					RFx.at(level, centre().add(0.0D, -1.0D, 0.0D), RFx.SEAL_LEAK, 2, 0, 1.6F);
					for (ServerPlayer player : inHall) {
						Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.raja.lemah", Prisons.RISE_REALM.displayName())
								.withStyle(ChatFormatting.DARK_RED));
					}
				}
				return;
			}
			if (seal.broken() && !inHall.isEmpty() && Reruntuhan.config().prisons) {
				if (riseAt < 0L) {
					riseAt = now + (seal.pendingRise() ? 50L : 10L);
					if (seal.pendingRise()) {
						playBreak(seal);
					}
				}
				if (now >= riseAt) {
					rise(seal, inHall);
				}
			}
			return;
		}
		fight(seal, current, inHall, now);
	}

	private static boolean anyoneMayWake(List<ServerPlayer> inHall) {
		for (ServerPlayer player : inHall) {
			if (!player.isSpectator() && Prisons.mayWake(player)) {
				return true;
			}
		}
		return false;
	}

	private void playBreak(Prisons.Seal seal) {
		RFx.big(level, centre(), RFx.SEAL_BREAK, seal.king(), 0, (float) ARENA);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.6F);
		Voice.heavenNear(level, centre(), "demon.laugh", 96.0D);
		Component name = Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[seal.king()]);
		for (ServerPlayer player : playersWithin(96.0D)) {
			Msg.title(player, Component.translatable("kultivator.reruntuhan.penjara.break.title").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.reruntuhan.penjara.break.sub", name).withStyle(ChatFormatting.RED), 5, 50, 20);
		}
	}

	private void rise(Prisons.Seal seal, List<ServerPlayer> inHall) {
		RajaIblisEntity made = make(REntities.RAJA_IBLIS, centre(), 180.0F);
		if (made == null) {
			return;
		}
		made.setKind(seal.king());
		made.setTier(3);
		int party = Math.max(1, inHall.size());
		double health = 2000.0D * (1.0D + 0.5D * (party - 1));
		made.setBase(Attributes.MAX_HEALTH, health);
		made.setHealth((float) Math.max(1.0D, health * Mth.clamp(seal.kingHealth(), 0.05F, 1.0F)));
		made.setIntakeCap(0.03D);
		made.setFlag(RCreature.FLAG_RISING, true);
		made.setSkill(9);
		made.setBrain(new KingBrain(centre(), ARENA, (creature, source) -> slain(), this::credit, this::enrage));
		if (!add(made)) {
			return;
		}
		king = made;
		seal.pendingRise = false;
		Prisons.get(level.getServer()).setDirty();
		for (int i = 0; i < 5; i++) {
			lit[i] = false;
		}
		updateFactor();
		damage.clear();
		present.clear();
		channels.clear();
		int count = 3 + Math.min(3, party);
		callLessers(count);
		RFx.big(level, centre(), RFx.KING_SKILL, seal.king(), 0, 8.0F);
	}

	private void callLessers(int count) {
		int cells = Frame.triples(data(), "cells");
		for (int i = 0; i < count; i++) {
			Vec3 at;
			if (cells > 0) {
				int[] off = Frame.triple(data(), "cells", i % cells);
				at = frame.bottom(off[0], off[1], off[2]).add(frame.dir(-Math.signum(off[0]) * 3.0D, -Math.signum(off[2]) * 3.0D));
			} else {
				double a = i * Math.PI * 2.0D / count;
				at = centre().add(Math.cos(a) * 12.0D, 1.0D, Math.sin(a) * 12.0D);
			}
			IblisKecilEntity demon = make(REntities.IBLIS_KECIL, at, 0.0F);
			if (demon == null) {
				continue;
			}
			demon.setKind(i % 3);
			demon.setTier(3);
			demon.setBase(Attributes.MAX_HEALTH, 160.0D);
			demon.setHealth(demon.getMaxHealth());
			demon.setBrain(new LesserBrain(centre(), ARENA, 0.08D));
			if (add(demon)) {
				lessers.add(demon);
				RFx.at(level, at.add(0.0D, 1.0D, 0.0D), RFx.SOUL_APPEAR, 0xC02020, 1, 1.0F);
			}
		}
	}

	private void enrage() {
		callLessers(2);
	}

	private void credit(DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		UUID who = null;
		if (attacker instanceof ServerPlayer player) {
			who = player.getUUID();
		} else if (attacker instanceof DiscipleEntity disciple) {
			who = disciple.ownerId();
		}
		if (who != null) {
			damage.merge(who, amount, Float::sum);
		}
	}

	private void fight(Prisons.Seal seal, RajaIblisEntity current, List<ServerPlayer> inHall, long now) {
		for (ServerPlayer player : inHall) {
			present.merge(player.getUUID(), 1, Integer::sum);
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.isRemoved() || player.distanceToSqr(centre()) > (ARENA + 12.0D) * (ARENA + 12.0D)) {
				bar.removePlayer(player);
			}
		}
		int litCount = 0;
		for (boolean b : lit) {
			if (b) {
				litCount++;
			}
		}
		bar.setName(Component.translatable("kultivator.reruntuhan.penjara.bar", Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[seal.king()]),
				litCount, 5));
		bar.setProgress(Mth.clamp(current.getHealth() / Math.max(1.0F, current.getMaxHealth()), 0.0F, 1.0F));
		seal.kingHealth = Mth.clamp(current.getHealth() / Math.max(1.0F, current.getMaxHealth()), 0.0F, 1.0F);
		tickChannels(seal);
		if (king == null) {
			return;
		}
		if (playersWithin(96.0D).isEmpty()) {
			if (++empty > 200) {
				sink(false);
			}
		} else {
			empty = 0;
		}
	}

	private void tickChannels(Prisons.Seal seal) {
		for (Map.Entry<UUID, int[]> entry : List.copyOf(channels.entrySet())) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			int[] c = entry.getValue();
			TekaPartEntity pillar = pillars[c[0]];
			if (player == null || pillar == null || player.level() != level || player.hurtTime > 0 || player.distanceToSqr(pillar) > 3.5D * 3.5D
					|| lit[c[0]]) {
				channels.remove(entry.getKey());
				if (player != null) {
					Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.penjara.pilar.broken").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (c[1] % 10 == 0) {
				RFx.line(level, player.position().add(0.0D, 1.2D, 0.0D), pillar.position().add(0.0D, 2.0D, 0.0D), RFx.LINE, 0xFFD27A, 10, 0.3F);
			}
			if (--c[1] <= 0) {
				channels.remove(entry.getKey());
				lit[c[0]] = true;
				pillar.setState(TekaPartEntity.LIT);
				updateFactor();
				RFx.at(level, pillar.position(), RFx.PILLAR_LIT, c[0], 0, 1.0F);
				level.playSound(null, pillar.getX(), pillar.getY(), pillar.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.0F);
				Msg.chat(player, Component.translatable("kultivator.reruntuhan.penjara.pilar.lit", litNow(), 5).withStyle(ChatFormatting.GOLD));
				if (litNow() >= 5) {
					resealed(seal);
					return;
				}
			}
		}
	}

	private int litNow() {
		int n = 0;
		for (boolean b : lit) {
			if (b) {
				n++;
			}
		}
		return n;
	}

	private void updateFactor() {
		RajaIblisEntity current = king;
		if (current != null) {
			current.setDamageFactor(Math.max(0.25D, 1.0D - 0.15D * litNow()));
		}
	}

	/** Every pillar lit: the king is sealed again. */
	private void resealed(Prisons.Seal seal) {
		Vec3 shrine = Vec3.atBottomCenterOf(seal.shrine());
		List<ServerPlayer> heroes = participants();
		sink(true);
		Prisons.resealed(level, seal);
		RFx.big(level, centre(), RFx.SEAL_SEALED, seal.king(), 100, (float) ARENA);
		Component sealVoice = Component.translatable("kultivator.reruntuhan.penjara.seal_voice");
		for (ServerPlayer player : playersWithin(96.0D)) {
			Voice.inner(player, "penjara.resealed", sealVoice);
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : heroes) {
			int nb = RRewards.roll(random, 300, 600, 1.0D);
			RRewards.stones(player, nb);
			RRewards.give(player, new ItemStack(Talismans.BLANK, 4));
			addPahala(player, 5);
			renown(player, 40, "kultivator.reruntuhan.penjara.renown.reseal");
			Msg.title(player, Component.translatable("kultivator.reruntuhan.penjara.sealed.title").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.penjara.sealed.sub", nb).withStyle(ChatFormatting.YELLOW), 10, 60, 20);
			Advancements.grant(player, "dunia/prison_sealed");
			// audit M4 S4 (approved): a Dunia milestone, once per player per prison (not again on a later reseal)
			CompoundTag own = dev.kultivator.dunia.reruntuhan.Reruntuhan.data(player);
			String sealKey = "ms_seal_" + seal.id;
			if (!own.getBooleanOr(sealKey, false)) {
				own.putBoolean(sealKey, true);
				dev.kultivator.data.Cultivators.dirty(player);
				dev.kultivator.cultivation.XpSources.milestone(player, dev.kultivator.cultivation.XpSources.MILESTONE_PRISON,
						Component.translatable("kultivator.kekuatan.milestone.prison"));
			}
		}
		ShrineRun.birthAt(level, shrine);
	}

	/** The king fell. */
	private void slain() {
		Prisons.Seal seal = seal();
		List<ServerPlayer> heroes = participants();
		king = null;
		for (IblisKecilEntity demon : lessers) {
			if (!demon.isRemoved()) {
				demon.setBrain(null);
				demon.discard();
			}
		}
		lessers.clear();
		bar.removeAllPlayers();
		Prisons.slain(level, seal);
		RFx.big(level, centre(), RFx.GUARDIAN_DOWN, 0xC02020, 9, 6.0F);
		RandomSource random = level.getRandom();
		for (ServerPlayer player : heroes) {
			List<ItemStack> items = new ArrayList<>();
			items.add(new ItemStack(RItems.INTI_IBLIS_KUNO));
			items.add(RRewards.core(random, 4));
			items.add(RRewards.core(random, 4));
			ItemStack slip = RRewards.slip(random, TechniqueGrade.TIAN, TechniqueGrade.ABADI, Dao.SHADOW);
			if (!slip.isEmpty()) {
				items.add(slip);
			}
			RRewards.giveAll(player, items, "kultivator.reruntuhan.penjara.spoils");
			Msg.title(player, Component.translatable("kultivator.reruntuhan.penjara.slain.title").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.penjara.slain.sub").withStyle(ChatFormatting.GRAY), 10, 60, 20);
			Advancements.grant(player, "dunia/prison_slain");
		}
	}

	/** Everyone who dealt at least 3 % of the damage or stood in the hall for 30 s. */
	private List<ServerPlayer> participants() {
		float total = 0.0F;
		for (float d : damage.values()) {
			total += d;
		}
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			float mine = damage.getOrDefault(id, 0.0F);
			if ((total > 0.0F && mine / total >= 0.03F) || present.getOrDefault(id, 0) >= 600) {
				list.add(player);
			}
		}
		return list;
	}

	/** The king goes back into the pit (re-sealed, or nobody near: its health stays in the seal). */
	private void sink(boolean sealed) {
		RajaIblisEntity current = king;
		king = null;
		if (current != null && !current.isRemoved()) {
			RFx.at(level, current.position(), RFx.GUARDIAN_RISE, 9, sealed ? 1 : 0, 3.0F);
			current.setBrain(null);
			current.discard();
		}
		for (IblisKecilEntity demon : lessers) {
			if (!demon.isRemoved()) {
				demon.setBrain(null);
				demon.discard();
			}
		}
		lessers.clear();
		bar.removeAllPlayers();
		channels.clear();
		riseAt = -1L;
		empty = 0;
		Prisons.get(level.getServer()).setDirty();
	}

	/** A lethal blow of a demon: out of the arena to the landing of the stair. */
	void expel(ServerPlayer player) {
		int[] off = Frame.triple(data(), "landing", 0);
		Vec3 at = off == null ? centre().add(0.0D, 1.0D, -16.0D) : frame.bottom(off[0], off[1], off[2]);
		player.teleportTo(at.x, at.y, at.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.needsSync = true;
		channels.remove(player.getUUID());
	}

	@Override
	protected void despawn() {
		if (king != null) {
			sink(false);
		}
		bar.removeAllPlayers();
		core = null;
		for (int i = 0; i < 5; i++) {
			pillars[i] = null;
		}
		super.despawn();
	}

	// ================================================================== tests

	/** {@code uji penjara segel_ulang}: lights every pillar (a re-seal now). */
	public void testReseal() {
		Prisons.Seal seal = seal();
		if (king == null) {
			Prisons.resealed(level, seal);
			return;
		}
		for (int i = 0; i < 5; i++) {
			lit[i] = true;
		}
		resealed(seal);
	}

	public @Nullable RajaIblisEntity king() {
		return king;
	}

	/** Tells one player the seal's state (the stele line of {@code uji penjara}). */
	public void describe(ServerPlayer player) {
		Prisons.Seal seal = seal();
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "seal");
		tag.putInt("integrity", seal.integrity());
		tag.putBoolean("broken", seal.broken());
		RNet.send(player, tag);
	}
}
