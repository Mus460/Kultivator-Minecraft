package dev.kultivator.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.network.Net;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Module K: Kota dan NPC (DESIGN 34.5.1 Kota Kultivator, 34.5.2 Kultivator Pengembara, 34.5.3 Saingan, 34.5.4 Kota Abadi).
 * The one init of the module (wired by one line in {@code DuniaContent}): config section {@code kota}, the world
 * generation pack {@code kultivator:dunia_kota}, entities, items, payloads, the {@code kota/*} anchor handler, the rival
 * service, the event listeners, the test commands and one server tick. It dispatches NPC right-clicks and screen actions
 * to the services (shops, auction, teahouse, black market, bounties, wanderers, rivals, the Immortal City).
 * <p>
 * Nothing in this module places, breaks or changes a block: towns come only from world generation in new chunks, the
 * black-market door is an entity, every effect is a client visual (DESIGN 34.0 rule W1/W2).
 */
public final class Kota {

	/** Below this Nama Baik a town's guards are hostile and its shops refuse (34.5.1). */
	public static final int HOSTILE_REPUTATION = -30;
	/** From this Nama Baik on, a 10 % discount. */
	public static final int DISCOUNT_REPUTATION = 50;
	public static final int HURT_PENALTY = 10;
	public static final int KILL_PENALTY = 30;

	private static KotaConfig config = new KotaConfig();
	private static @Nullable MinecraftServer server;

	public static void init() {
		config = DuniaConfig.section("kota", KotaConfig.class, KotaConfig::new, KotaConfig::clamp);
		KotaItems.init();
		KotaEntities.init();
		KotaNet.init();
		DuniaPacks.worldgen("kota", config.worldgen);
		DuniaAnchors.register("kota", KotaAnchors.HANDLER);
		Saingan.init();
		KedaiTeh.init();
		PapanMisi.init();
		Pengembara.init();
		KotaAbadi.init();
		KabarDunia.init();
		NpcLife.init();
		KotaCommands.init();
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		// Audit T14: close every running auction while the players are still online and before the world is saved, so
		// every consigned piece is paid (offline owners get it as "owed"); the saved entries cover a crash as well.
		ServerLifecycleEvents.SERVER_STOPPING.register(BalaiLelang::forceClose);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			KotaAnchors.HANDLER.clear();
			BalaiLelang.clear();
			PasarGelap.clear();
			Pengembara.clear();
			Saingan.clear();
			KabarDunia.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Kota::serverTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Kota::afterDeath);
		ServerPlayerEvents.LEAVE.register(player -> {
			Pengembara.onLeave(player);
			BalaiLelang.onLeave(player);
		});
		// Teh Embun Pagi: +teaBonus to XP while meditating (the one public XP hook; it multiplies every non-exact source,
		// so it is applied only during meditation, where the gain is meditation XP).
		Progression.addMultiplierProvider(player -> KedaiTeh.active(player) == KedaiTeh.EMBUN && Meditation.isMeditating(player)
				? 1.0D + config.teaBonus : 1.0D);
	}

	public static KotaConfig config() {
		return config;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** True while the module runs (the spine's master switch and {@code kota.enabled}). */
	public static boolean enabled() {
		return DuniaSpine.enabled() && config.enabled;
	}

	/** The in-game day the daily caps count in (overworld clock). */
	public static long day(MinecraftServer current) {
		return Math.floorDiv(current.overworld().getOverworldClockTime(), 24000L);
	}

	/** The overworld time of day 0-23999. */
	public static long timeOfDay(MinecraftServer current) {
		return Math.floorMod(current.overworld().getOverworldClockTime(), 24000L);
	}

	private static void serverTick(MinecraftServer current) {
		if (!DuniaSpine.enabled()) {
			return;
		}
		long tick = current.getTickCount();
		BalaiLelang.tick(current, tick);
		PasarGelap.tick(current, tick);
		KedaiTeh.tick(current, tick);
		Pengembara.tick(current, tick);
		Saingan.tick(current, tick);
		KotaAbadi.tick(current, tick);
	}

	// ================================================================== NPC interaction

	/** A right-click on an NPC (server). True when something answered. */
	static boolean interact(ServerPlayer player, KultivatorNpc npc) {
		if (!enabled() || !npc.isAlive()) {
			return false;
		}
		if (npc.isHostileTo(player.getUUID()) && npc.role() != NpcRole.PENJAGA) {
			return false;
		}
		NpcRole role = npc.role();
		npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
		switch (role) {
			case WARGA -> {
				Voice.say(npc, npc.voiceKey(npc.getRandom().nextBoolean() ? "greet" : "greet_alt"), null);
				String said = NpcLife.townLine(npc, player);
				Msg.chat(player, Component.empty().append(npc.plate()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
						.append(Component.translatable(said).withStyle(ChatFormatting.WHITE)));
				NpcLife.speak(npc, said, 80);
				NpcLife.greeted(npc, player);
				return true;
			}
			case SAINGAN -> {
				Voice.say(npc, "rogue.ambush", null);
				String said = "kultivator.kota.saingan.kata." + npc.getRandom().nextInt(6);
				Msg.chat(player, Component.empty().append(npc.plate()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
						.append(Component.translatable(said).withStyle(ChatFormatting.WHITE)));
				NpcLife.speak(npc, said, 80);
				return true;
			}
			case ABADI -> {
				// a native immortal looks down on those still short of True Immortal now and then
				boolean scorn = dev.kultivator.data.Cultivators.get(player).realm().ordinal() < dev.kultivator.data.Realm.TRUE_IMMORTAL.ordinal()
						&& npc.getRandom().nextInt(3) == 0;
				if (scorn) {
					KotaVoice.say(npc, KotaVoice.IMMORTAL_SCORN, npc.voiceKey("greet_alt"), null);
				} else {
					Voice.say(npc, npc.voiceKey("greet_alt"), null);
				}
				String said = scorn ? "kultivator.kota.abadi.cela." + npc.getRandom().nextInt(4) : "kultivator.kota.abadi.kata." + npc.getRandom().nextInt(8);
				Msg.chat(player, Component.empty().append(npc.plate()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
						.append(Component.translatable(said).withStyle(ChatFormatting.WHITE)));
				NpcLife.speak(npc, said, 80);
				return true;
			}
			case JURU_LELANG_ABADI -> {
				Voice.say(npc, npc.voiceKey("greet"), null);
				// the existing Lelang Abadi tab of the Keabadian screen (34.5.4: no auction logic here)
				Net.send(player, new dev.kultivator.network.Payloads.OpenScreen(dev.kultivator.network.Payloads.OpenScreen.ABADI,
						dev.kultivator.network.Payloads.OpenScreen.ABADI_LELANG));
				return true;
			}
			case PENGEMBARA -> {
				return Pengembara.talk(player, npc);
			}
			default -> {
				if (role == NpcRole.PENJAGA && PasarGelap.raided(player, npc.town())) {
					PasarGelap.offerBribe(player, npc);
					return true;
				}
				if (role.townsfolk() && !role.immortal() && KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID()) < HOSTILE_REPUTATION) {
					if (role == NpcRole.PENJAGA) {
						KotaVoice.say(npc, KotaVoice.GUARD_HALT, npc.voiceKey("careful"), null);
					} else {
						Voice.say(npc, npc.voiceKey("careful"), null);
					}
					Msg.actionBar(player, Component.translatable("kultivator.kota.nama_baik.ditolak").withStyle(ChatFormatting.RED));
					return true;
				}
				if (role == NpcRole.PEMILIK_TEH) {
					KotaVoice.say(npc, KotaVoice.TEA_WELCOME, greetLine(npc), null);
					KabarDunia.tell(player, npc);
				} else if (role == NpcRole.PENJAGA_KEBUN) {
					KotaVoice.say(npc, KotaVoice.PEACH_KEEPER, greetLine(npc), null);
				} else if (role == NpcRole.PENCERAMAH) {
					KotaVoice.say(npc, KotaVoice.LECTURE, greetLine(npc), null);
				} else {
					Voice.say(npc, greetLine(npc), null);
				}
				NpcLife.greeted(npc, player);
				open(player, npc, role.tab, true);
				return true;
			}
		}
	}

	private static String greetLine(KultivatorNpc npc) {
		return npc.role() == NpcRole.PENCERAMAH || npc.role() == NpcRole.UTUSAN ? "elder.welcome" : npc.voiceKey("greet");
	}

	/** Opens (or refreshes) the NPC's screen on {@code tab} for the player. */
	static void open(ServerPlayer player, KultivatorNpc npc, int tab, boolean open) {
		if (tab == KotaNet.TAB_PERTEMUAN) {
			KotaNet.screen(player, open, KotaNet.SCREEN_PERTEMUAN, tab, npc.getId(), Pengembara.view(player, npc));
			return;
		}
		boolean abadi = npc.role().immortal();
		CompoundTag data = abadi ? KotaAbadi.view(player, npc) : KotaView.town(player, npc);
		KotaNet.screen(player, open, abadi ? KotaNet.SCREEN_ABADI : KotaNet.SCREEN_KOTA, tab, npc.getId(), data);
	}

	/** Refreshes the open screen of every player within {@code range} of the NPC (live auction, raid). */
	static void refreshAround(KultivatorNpc npc, double range) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(npc) <= range * range) {
				open(player, npc, -1, false);
			}
		}
	}

	// ================================================================== screen actions

	static void onAction(ServerPlayer player, KotaNet.Action action) {
		if (!enabled() || action.op() == null || action.op().isEmpty()) {
			return;
		}
		Entity entity = player.level().getEntity(action.npcId());
		if (!(entity instanceof KultivatorNpc npc) || !npc.isAlive() || npc.distanceToSqr(player) > KotaNet.REACH * KotaNet.REACH) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.aksi.jauh").withStyle(ChatFormatting.RED));
			return;
		}
		String[] parts = action.op().split(":");
		String verb = parts[0];
		try {
			boolean refresh = switch (verb) {
				case "lihat" -> true;
				case "beli", "jual" -> Toko.action(player, npc, verb, parts);
				case "tawar", "titip" -> BalaiLelang.action(player, npc, verb, parts);
				case "teh", "beliteh", "kabar", "token" -> KedaiTeh.action(player, npc, verb, parts);
				case "gbeli", "gjual", "suap" -> PasarGelap.action(player, npc, verb, parts);
				case "ambil", "serah", "batal" -> PapanMisi.action(player, npc, verb, parts);
				case "abeli", "persik", "titah" -> KotaAbadi.action(player, npc, verb, parts);
				case "tukar", "diskusi", "tanding", "duel", "tantang", "ajak", "pamit" -> Pengembara.action(player, npc, verb, parts);
				default -> false;
			};
			if (refresh && !npc.isRemoved()) {
				open(player, npc, -1, false);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kota action {} failed", action.op(), exception);
		}
	}

	/** An int argument of an action ({@code fallback} when missing or broken). */
	static int arg(String[] parts, int index, int fallback) {
		if (index >= parts.length) {
			return fallback;
		}
		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	// ================================================================== harm and death

	/** An NPC was hurt (server). Townsfolk remember it: Nama Baik −10 (once per 10 s) and the guards wake. */
	static void onNpcHurt(KultivatorNpc npc, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(npc.level() instanceof ServerLevel level)) {
			return;
		}
		if (dev.kultivator.disciple.DuelRules.dueling(npc, player)) {
			return;
		}
		NpcRole role = npc.role();
		if (role == NpcRole.PENGEMBARA) {
			Pengembara.attacked(player, npc);
			return;
		}
		if (role == NpcRole.SAINGAN) {
			npc.markHostile(player);
			Saingan.attacked(player, npc);
			return;
		}
		npc.markHostile(player);
		long now = level.getGameTime();
		if (now - npc.memory().getLongOr("pen", -1000L) >= 200L && npc.town() != 0L) {
			npc.memory().putLong("pen", now);
			int value = KotaData.get(level.getServer()).addReputation(npc.town(), player.getUUID(), -HURT_PENALTY);
			Msg.actionBar(player, Component.translatable("kultivator.kota.nama_baik.turun", HURT_PENALTY, value).withStyle(ChatFormatting.RED));
		}
		alertGuards(level, npc, player);
	}

	/** Every guard of the town within 32 blocks turns on {@code player}. */
	static void alertGuards(ServerLevel level, KultivatorNpc victim, ServerPlayer player) {
		for (KultivatorNpc other : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(victim.blockPosition()).inflate(32.0D),
				each -> each.role() == NpcRole.PENJAGA && each.town() == victim.town())) {
			if (!other.isHostileTo(player.getUUID())) {
				other.markHostile(player);
				Voice.say(other, other.voiceKey("attack"), null);
			}
		}
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof KultivatorNpc npc) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		KotaAnchors.HANDLER.died(npc);
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		ServerPlayer killer = credit == null ? null : credit.owner();
		switch (npc.role()) {
			case PENGEMBARA -> Pengembara.slain(level, npc, killer);
			case SAINGAN -> Saingan.slain(level, npc, killer);
			default -> {
				if (killer != null && npc.town() != 0L && !(killer instanceof net.fabricmc.fabric.api.entity.FakePlayer)) {
					int value = KotaData.get(level.getServer()).addReputation(npc.town(), killer.getUUID(), -KILL_PENALTY);
					Msg.chat(killer, Component.translatable("kultivator.kota.nama_baik.bunuh", KILL_PENALTY, value).withStyle(ChatFormatting.DARK_RED));
					PasarGelap.addShadowReputation(killer, npc.role() == NpcRole.PENJAGA ? -5 : 0);
				}
			}
		}
	}

	/** Every 20 ticks per NPC (from {@link NpcAi#check}): the role's own pass. */
	static void tickNpc(ServerLevel level, KultivatorNpc npc, long now) {
		switch (npc.role()) {
			case PENGEMBARA -> Pengembara.tickNpc(level, npc, now);
			case SAINGAN -> Saingan.tickNpc(level, npc, now);
			case PENJAGA -> {
				if (npc.hostiles().isEmpty() || now % 200L != 0L) {
					return;
				}
				// a grudge fades when the player made peace (reputation back above the line, raid over)
				for (java.util.UUID id : List.copyOf(npc.hostiles())) {
					int rep = KotaData.get(level.getServer()).reputation(npc.town(), id);
					ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
					if (rep >= HOSTILE_REPUTATION && (player == null || !PasarGelap.raided(player, npc.town()))
							&& (npc.getLastHurtByMob() == null || !npc.getLastHurtByMob().getUUID().equals(id))) {
						npc.forgive(id);
					}
				}
			}
			default -> {
				if (npc.memory().getBooleanOr("escort", false)) {
					PapanMisi.tickEscort(level, npc);
				}
			}
		}
	}

	/** Drops a stack at the player's feet that only they may pick up for a while (the inventory was full). */
	static void drop(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(),
				player.getY() + 0.5D, player.getZ(), stack.copy());
		item.setTarget(player.getUUID());
		item.setNoPickUpDelay();
		item.addTag(dev.kultivator.util.NoGrief.TAG);
		player.level().addFreshEntity(item);
		stack.setCount(0);
	}

	/** True while the player is in Alam Abadi (the Immortal City's only dimension). */
	static boolean inAlamAbadi(ServerPlayer player) {
		return Abadi.isImmortalLevel(player.level());
	}

	private Kota() {
	}
}
