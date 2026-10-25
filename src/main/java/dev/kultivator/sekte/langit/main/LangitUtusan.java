package dev.kultivator.sekte.langit.main;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.purba.HeavenlyWrath;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utusan Istana Langit, the Heaven Court envoy (DESIGN 37.7.4; "titah" is never used for it, audit 4.9: the task is a
 * "Perintah Istana Langit"). On an A7+ island at tier 9 with the Gate of the Nine Heavens, at dusk, a beam of gold falls on
 * the gate and Xian Lingyue, envoy of the Heaven Court ({@code KultivatorNpc} role {@code UTUSAN}, never hostile), reads the
 * Court's word to the hall's master with three chat seals:
 * <ul>
 * <li><b>[Terima Perintah]</b>: within {@link #ORDER_DAYS} days slay {@link #ORDER_KILLS} rogue cultivators, or lay
 * {@link #ORDER_NB} NB of spirit stones (eight High stones, through {@code NilaiBatu.pay}) before the Court, whose
 * offering value ({@code ORDER_NB / Harga.NB_PER_PK} PK, the same rate as the hall's offerings) goes into the hall pool.
 * Reward: {@link #RENOWN} renown, the title "Diakui Langit" for {@link #ACKNOWLEDGED_DAYS} days (hub card and screen), one
 * High pill, the advancement {@code sekte_langit/utusan_langit}.</li>
 * <li><b>[Tolak dengan hormat]</b>: nothing happens; three refusals in a row make the master's next tribulation strike
 * × 1.10 ({@link LangitSaksi}).</li>
 * <li><b>[Tantang Langit]</b>: only shown when Heaven has noticed the master ({@code HeavenlyWrath.canChallenge}); it
 * calls {@code HeavenlyWrath.challenge}, which only moves the next descent to the coming dusk: the dusk roll and every
 * refusal ({@code Sibuk}, Peaceful, place, AFK) still decide (audit 4.3). Winning that descent within
 * {@link #ORDER_DAYS} days adds {@link #CHALLENGE_RENOWN} renown.</li>
 * </ul>
 * Sumber: <i>Journey to the West</i> ch. 4, Heaven's envoy summons Sun Wukong to the Court (the register of names).
 */
public final class LangitUtusan {

	public static final int ORDER_DAYS = 3;
	public static final int ORDER_KILLS = 12;
	/** Eight High spirit stones (81 NB each, unified rates of audit M3). */
	public static final long ORDER_NB = 8L * 81L;
	public static final int RENOWN = 500;
	public static final int CHALLENGE_RENOWN = 800;
	public static final int ACKNOWLEDGED_DAYS = 30;
	public static final int STAY_TICKS = 20 * 60 * 3;
	public static final String TAG = "kultivator.langit_utusan";
	public static final int BEAT_UTUSAN = dev.kultivator.epic.EpicIds.EXTRA_SEKTE_FIRST + 97;

	/** owner → the envoy waiting at the gate. */
	private static final Map<UUID, KultivatorNpc> ENVOYS = new HashMap<>();
	private static final Map<UUID, Long> LEAVE_AT = new HashMap<>();

	/** True when the hall may receive the envoy (A7+, tier 9, the Gate of the Nine Heavens built). */
	static boolean eligible(SkyHallView view) {
		return view.template() >= 17 && view.tier() >= 9 && LangitApi.get().anchor(view.owner(), "gerbang_langit") != null;
	}

	static boolean orderOpen(CompoundTag hall) {
		return hall.getCompoundOrEmpty("perintah").getIntOr("status", 0) == 1;
	}

	static void clear() {
		for (KultivatorNpc envoy : ENVOYS.values()) {
			envoy.discard();
		}
		ENVOYS.clear();
		LEAVE_AT.clear();
	}

	/** True for an envoy entity this module spawned and still tracks. */
	static boolean tracked(Entity entity) {
		return ENVOYS.containsValue(entity);
	}

	// ================================================================== arrival

	/** The envoy descends (null when he came, else why not); {@code test} skips the tier gate. */
	static @Nullable Component arrive(MinecraftServer server, UUID owner, boolean test) {
		SkyHallView view = LangitApi.get().skyHall(owner);
		if (view == null || view.tethered()) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		if (!test && !eligible(view)) {
			return Component.translatable("kultivator.sekte_langit.utusan.belum_layak");
		}
		if (ENVOYS.containsKey(owner)) {
			return Component.translatable("kultivator.sekte_langit.utusan.sudah");
		}
		ServerLevel level = server.getLevel(view.dim());
		if (level == null) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		BlockPos gate = LangitApi.get().anchor(owner, "gerbang_langit");
		BlockPos spot = gate != null ? gate : LangitPeristiwa.landingSpot(view);
		KultivatorNpc envoy = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (envoy == null) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_tempat");
		}
		Vec3 at = Targeting.safeSpot(level, envoy, Vec3.atBottomCenterOf(spot));
		if (at == null) {
			at = Vec3.atBottomCenterOf(spot);
		}
		Vec3 toThrone = Vec3.atCenterOf(view.origin()).subtract(at);
		float yaw = (float) (Math.atan2(-toThrone.x, toThrone.z) * 180.0D / Math.PI);
		envoy.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		envoy.setup(NpcRole.UTUSAN, 4, 0x5A17, Realm.TRUE_IMMORTAL.ordinal(), 2, null, 3, 0,
				Component.translatable("kultivator.sekte_langit.utusan.nama"));
		envoy.setHome(spot);
		envoy.setLonely(96.0D, STAY_TICKS);
		envoy.setYHeadRot(yaw);
		envoy.addTag(TAG);
		// tracked before it joins the world: the ENTITY_LOAD guard of LangitMain removes untracked envoys
		ENVOYS.put(owner, envoy);
		if (!level.addFreshEntity(envoy)) {
			ENVOYS.remove(owner);
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_tempat");
		}
		LEAVE_AT.put(owner, level.getGameTime() + STAY_TICKS);
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.hall(owner);
		hall.putLong("utusan_menunggu", LangitPeristiwa.today(server));
		data.setDirty();
		beam(level, at);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.0F, 1.3F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.0F, 0.8F);
		LangitSuaraServer.say(envoy, "sekte_langit.utusan_salam", "elder.welcome");
		Component line = Component.translatable("kultivator.sekte_langit.utusan.datang", Halls.hallOf(owner) == null ? "" : Halls.hallOf(owner).name());
		LangitBajak.tellIsland(server, view, line.copy().withStyle(ChatFormatting.GOLD));
		data.chronicle(owner, LangitPeristiwa.today(server), line);
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			offer(master);
			LangitPeristiwa.scene(level, BEAT_UTUSAN, envoy, master, at, 0);
		}
		return null;
	}

	/** The gold beam on the gate (vanilla particles, a few dozen; no block, no lightning). */
	private static void beam(ServerLevel level, Vec3 at) {
		for (int i = 0; i < 40; i++) {
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + i * 0.6D, at.z, 1, 0.08D, 0.1D, 0.08D, 0.0D);
		}
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3D, at.z, 24, 1.2D, 0.2D, 1.2D, 0.02D);
	}

	/** The envoy's words and the three seals, to the master. */
	static void offer(ServerPlayer master) {
		Msg.chat(master, Component.translatable("kultivator.sekte_langit.utusan.sabda").withStyle(ChatFormatting.YELLOW));
		MutableComponent seals = Component.literal("  ");
		seals.append(seal("kultivator.sekte_langit.utusan.terima", "kultivator.sekte_langit.utusan.terima.tip", "/kultivasi langit utusan terima",
				ChatFormatting.GREEN));
		seals.append(Component.literal("  "));
		seals.append(seal("kultivator.sekte_langit.utusan.tolak", "kultivator.sekte_langit.utusan.tolak.tip", "/kultivasi langit utusan tolak",
				ChatFormatting.GRAY));
		if (HeavenlyWrath.canChallenge(master)) {
			seals.append(Component.literal("  "));
			seals.append(seal("kultivator.sekte_langit.utusan.tantang", "kultivator.sekte_langit.utusan.tantang.tip", "/kultivasi langit utusan tantang",
					ChatFormatting.RED));
		}
		Msg.chat(master, seals);
	}

	private static Component seal(String key, String tip, String command, ChatFormatting color) {
		return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.translatable(tip))));
	}

	// ================================================================== answers

	/** {@code /kultivasi langit utusan <answer>} from the master: terima, tolak, tantang or setor. */
	static int answer(ServerPlayer player, String answer) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.hall(owner);
		long today = LangitPeristiwa.today(server);
		if ("setor".equals(answer)) {
			return deposit(player, hall);
		}
		long asked = hall.getLongOr("utusan_menunggu", Long.MIN_VALUE);
		// contains() first: today - Long.MIN_VALUE overflows negative and would pass with no envoy.
		if (!hall.contains("utusan_menunggu") || today - asked > 1L) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.utusan.tak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		hall.remove("utusan_menunggu");
		switch (answer) {
			case "terima" -> {
				CompoundTag order = new CompoundTag();
				order.putInt("status", 1);
				order.putLong("until", today + ORDER_DAYS);
				order.putInt("kills", 0);
				hall.put("perintah", order);
				hall.putInt("tolak", 0);
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.utusan.diterima", ORDER_KILLS, ORDER_DAYS, NilaiBatu.text(ORDER_NB))
						.withStyle(ChatFormatting.GOLD));
				data.chronicle(owner, today, Component.translatable("kultivator.sekte_langit.utusan.log_terima"));
			}
			case "tantang" -> {
				Component refused = HeavenlyWrath.challenge(player);
				if (refused != null) {
					hall.putLong("utusan_menunggu", asked);
					Msg.chat(player, refused.copy().withStyle(ChatFormatting.GRAY));
					data.setDirty();
					return 0;
				}
				hall.putLong("tantang_hari", today);
				hall.putInt("tolak", 0);
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.utusan.ditantang").withStyle(ChatFormatting.RED));
				data.chronicle(owner, today, Component.translatable("kultivator.sekte_langit.utusan.log_tantang"));
				LangitSuaraServer.heaven(player, "sekte_langit.utusan_tantang", "heaven.tribulation_begin");
			}
			default -> {
				int refusals = hall.getIntOr("tolak", 0) + 1;
				hall.putInt("tolak", refusals);
				Msg.chat(player, Component.translatable(refusals >= LangitSaksi.REFUSALS ? "kultivator.sekte_langit.utusan.ditolak_murka"
						: "kultivator.sekte_langit.utusan.ditolak", refusals).withStyle(ChatFormatting.GRAY));
				data.chronicle(owner, today, Component.translatable("kultivator.sekte_langit.utusan.log_tolak"));
			}
		}
		data.setDirty();
		depart(owner);
		return 1;
	}

	private static int deposit(ServerPlayer player, CompoundTag hall) {
		if (!orderOpen(hall)) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.utusan.tanpa_perintah").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!NilaiBatu.pay(player, ORDER_NB)) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte_langit.utusan.kurang_batu", NilaiBatu.text(ORDER_NB)).withStyle(ChatFormatting.RED));
			return 0;
		}
		Halls.addPool(player.getUUID(), (int) (ORDER_NB / Harga.NB_PER_PK));
		complete(player.level().getServer(), player.getUUID(), "setor");
		return 1;
	}

	/** A rogue cultivator died: the master's open order counts it (from {@code AFTER_DEATH}). */
	static void onDeath(LivingEntity victim, DamageSource source) {
		if (!RogueCultivators.isRogue(victim) || !(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		MinecraftServer server = killer.level().getServer();
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.peekHall(killer.getUUID());
		if (hall == null || !orderOpen(hall)) {
			return;
		}
		CompoundTag order = hall.getCompoundOrEmpty("perintah");
		int kills = order.getIntOr("kills", 0) + 1;
		order.putInt("kills", kills);
		hall.put("perintah", order);
		data.setDirty();
		if (kills >= ORDER_KILLS) {
			complete(server, killer.getUUID(), "basmi");
		} else {
			Msg.actionBar(killer, Component.translatable("kultivator.sekte_langit.utusan.hitung", kills, ORDER_KILLS).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void complete(MinecraftServer server, UUID owner, String how) {
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.hall(owner);
		hall.remove("perintah");
		long today = LangitPeristiwa.today(server);
		hall.putLong("diakui_sampai", today + ACKNOWLEDGED_DAYS);
		data.setDirty();
		Halls.addRenown(owner, RENOWN, "kultivator.sekte_langit.utusan.renown");
		Component line = Component.translatable("kultivator.sekte_langit.utusan.selesai." + how, RENOWN, ACKNOWLEDGED_DAYS);
		SekteLog.log(server, owner, "langit", line);
		data.chronicle(owner, today, line);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.GOLD));
			PillKind[] pills = {PillKind.TRIBULATION_WARDING, PillKind.SOUL_NURTURING, PillKind.DAO_INSIGHT};
			ItemStack pill = AlchemyComponents.withQuality(new ItemStack(AlchemyItems.get(pills[player.getRandom().nextInt(pills.length)])),
					PillQuality.HIGH);
			LangitBajak.give(player, pill);
			Advancements.grant(player, LangitIds.ADV + "utusan_langit");
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F,
					1.0F);
		}
		LangitSyncServer.dirty(owner);
	}

	/** A won Heavenly Wrath descent (HeavenlyWrath's win listener): the challenge of Tantang Langit pays. */
	static void onWrathWon(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.peekHall(player.getUUID());
		if (hall == null || !hall.contains("tantang_hari")) {
			return;
		}
		long today = LangitPeristiwa.today(server);
		long day = hall.getLongOr("tantang_hari", Long.MIN_VALUE);
		hall.remove("tantang_hari");
		data.setDirty();
		if (today - day > ORDER_DAYS) {
			return;
		}
		Halls.addRenown(player.getUUID(), CHALLENGE_RENOWN, "kultivator.sekte_langit.utusan.renown_tantang");
		Component line = Component.translatable("kultivator.sekte_langit.utusan.tantang_menang", CHALLENGE_RENOWN);
		SekteLog.log(server, player.getUUID(), "langit", line);
		data.chronicle(player.getUUID(), today, line);
		Msg.chat(player, line.copy().withStyle(ChatFormatting.GOLD));
	}

	/** True while the hall carries the title "Diakui Langit". */
	public static boolean acknowledged(MinecraftServer server, UUID owner) {
		CompoundTag hall = LangitData.get(server).peekHall(owner);
		return hall != null && LangitPeristiwa.today(server) <= hall.getLongOr("diakui_sampai", Long.MIN_VALUE);
	}

	// ================================================================== the envoy's stay

	/** Once a second: an order past its days lapses; an envoy leaves after his stay or when answered. */
	static void second(MinecraftServer server) {
		long today = LangitPeristiwa.today(server);
		LangitData data = LangitData.get(server);
		for (UUID owner : data.owners()) {
			CompoundTag hall = data.peekHall(owner);
			if (hall != null && orderOpen(hall) && today > hall.getCompoundOrEmpty("perintah").getLongOr("until", Long.MAX_VALUE)) {
				hall.remove("perintah");
				data.setDirty();
				Component line = Component.translatable("kultivator.sekte_langit.utusan.lewat");
				data.chronicle(owner, today, line);
				ServerPlayer player = server.getPlayerList().getPlayer(owner);
				if (player != null) {
					Msg.chat(player, line.copy().withStyle(ChatFormatting.GRAY));
				}
			}
		}
		for (Map.Entry<UUID, KultivatorNpc> entry : List.copyOf(ENVOYS.entrySet())) {
			KultivatorNpc envoy = entry.getValue();
			long leave = LEAVE_AT.getOrDefault(entry.getKey(), 0L);
			if (envoy.isRemoved() || envoy.level().getGameTime() >= leave) {
				depart(entry.getKey());
			}
		}
	}

	static void depart(UUID owner) {
		KultivatorNpc envoy = ENVOYS.remove(owner);
		LEAVE_AT.remove(owner);
		if (envoy == null || envoy.isRemoved()) {
			return;
		}
		if (envoy.level() instanceof ServerLevel level) {
			beam(level, envoy.position());
		}
		envoy.discard();
	}

	/** The open order of the hall for the screen: {status, until, kills} (empty when none). */
	static CompoundTag order(MinecraftServer server, UUID owner) {
		CompoundTag hall = LangitData.get(server).peekHall(owner);
		return hall == null ? new CompoundTag() : hall.getCompoundOrEmpty("perintah").copy();
	}

	static boolean enabled() {
		return LangitConfig.get().aktif && LangitConfig.get().utusanLangit;
	}

	private LangitUtusan() {
	}
}
