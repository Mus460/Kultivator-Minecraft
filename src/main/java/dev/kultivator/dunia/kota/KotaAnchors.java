package dev.kultivator.dunia.kota;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The anchor handler of module K (DESIGN 34.4.1, 34.5.1, 34.5.4): every {@code kota/*} anchor of the towns, the Immortal
 * City and D's Pulau Pasar. People anchors ({@link #ROLES}) bring their NPC when a player comes within 128 blocks: shop
 * keepers, the tea master, brokers and guards always (staff, outside every cap), the strolling crowd at most
 * {@code kota.npcPerTown} per town inside the spine's NPC budget; a slain one returns after 5 minutes. The NPC leaves 30 s
 * after the last player is gone beyond 160 blocks (crowd 64), or with its chunk. The hidden door anchor brings the door entity;
 * light anchors send their points to nearby clients (lantern rows, the stage light, the pond, cloud skirts, the bell).
 * The town stele greets a player the first time they reach the town (banner, advancement {@code dunia/kota_found}).
 */
final class KotaAnchors implements AnchorHandler {

	static final KotaAnchors HANDLER = new KotaAnchors();
	static final int RESPAWN_TICKS = 6000;
	static final int FX_RESEND = 600;
	/** People anchors bring their NPC when a player is within this ({@link #radius}); the rest keeps {@link #DEKAT}. */
	static final int JARAK_ORANG = 128;
	/** The range of the stele, the door, the seats, the lights and the auction (the radius before people reached 128). */
	static final double DEKAT = 48.0D;

	/** Anchor "what" → role. */
	static final Map<String, NpcRole> ROLES = new HashMap<>();

	static {
		ROLES.put("lelang", NpcRole.JURU_LELANG);
		ROLES.put("teh", NpcRole.PEMILIK_TEH);
		ROLES.put("gelap", NpcRole.MAKELAR_GELAP);
		ROLES.put("pil", NpcRole.PEDAGANG_PIL);
		ROLES.put("pusaka", NpcRole.PANDAI_PUSAKA);
		ROLES.put("penjaga", NpcRole.PENJAGA);
		ROLES.put("papan", NpcRole.PENJAGA);
		ROLES.put("warga", NpcRole.WARGA);
		ROLES.put("abadi_warga", NpcRole.ABADI);
		ROLES.put("abadi_penceramah", NpcRole.PENCERAMAH);
		ROLES.put("abadi_pasar", NpcRole.PEDAGANG_ABADI);
		ROLES.put("abadi_lelang", NpcRole.JURU_LELANG_ABADI);
		ROLES.put("abadi_utusan", NpcRole.UTUSAN);
		ROLES.put("abadi_persik", NpcRole.PENJAGA_KEBUN);
	}

	private final Map<UUID, KultivatorNpc> npcs = new HashMap<>();
	private final Map<UUID, KotaPintuEntity> doors = new HashMap<>();
	private final Map<String, Long> fxSent = new HashMap<>();
	private final Set<String> greeted = new HashSet<>();

	@Override
	public int radius() {
		return JARAK_ORANG;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		long town = Towns.townOf(anchor);
		Towns.remember(level, anchor, town);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> semua) {
		KotaConfig config = Kota.config();
		boolean abadi = Towns.isAbadi(anchor);
		if (!config.enabled || abadi && !config.immortalCity) {
			return;
		}
		long town = Towns.townOf(anchor);
		String what = anchor.what();
		long now = level.getGameTime();
		NpcRole role = ROLES.get(what);
		if (role != null) {
			person(level, anchor, town, role, semua, now);
		}
		List<ServerPlayer> within = dev.kultivator.dunia.DuniaAnchors.dalam(semua, anchor, DEKAT);
		if (within.isEmpty()) {
			return;
		}
		switch (what) {
			case "situs", "abadi_gerbang" -> greet(level, anchor, town, abadi, within);
			case "gerbang_gelap" -> door(level, anchor, town);
			case "kursi_pengembara" -> Pengembara.seat(level, anchor, town, within.getFirst());
			case "lentera", "kolam", "panggung", "abadi_awan", "abadi_lonceng", "abadi_kebun" -> lights(level, anchor, town, within, now);
			default -> {
			}
		}
		if ("lelang".equals(what)) {
			BalaiLelang.nearby(level, anchor, town, within);
		}
		if ("abadi_lonceng".equals(what)) {
			KotaAbadi.bell(level, anchor, now);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		KultivatorNpc npc = npcs.remove(anchor.getUUID());
		if (npc != null && !npc.isRemoved()) {
			npc.discard();
		}
		KotaPintuEntity door = doors.remove(anchor.getUUID());
		if (door != null && !door.isRemoved()) {
			door.discard();
		}
	}

	/** The NPC an anchor brought, when it is still there. */
	@Nullable KultivatorNpc npcOf(DuniaAnchorEntity anchor) {
		KultivatorNpc npc = npcs.get(anchor.getUUID());
		return npc != null && !npc.isRemoved() && npc.isAlive() ? npc : null;
	}

	@Nullable KotaPintuEntity doorOf(DuniaAnchorEntity anchor) {
		KotaPintuEntity door = doors.get(anchor.getUUID());
		return door != null && !door.isRemoved() ? door : null;
	}

	/** Every live anchor NPC of a town. */
	int countIn(long town) {
		int n = 0;
		for (KultivatorNpc npc : npcs.values()) {
			if (!npc.isRemoved() && npc.isAlive() && npc.town() == town) {
				n++;
			}
		}
		return n;
	}

	void clear() {
		npcs.clear();
		doors.clear();
		fxSent.clear();
		greeted.clear();
	}

	// ================================================================== people

	private void person(ServerLevel level, DuniaAnchorEntity anchor, long town, NpcRole role, List<ServerPlayer> within, long now) {
		KultivatorNpc existing = npcOf(anchor);
		if (existing != null) {
			return;
		}
		if (anchor.state().getLongOr("dead_until", 0L) > now) {
			return;
		}
		Vec3 at = anchor.position();
		// shop keepers, the tea master, brokers and guards always come; only the strolling crowd is capped
		boolean crowd = crowd(role);
		if (crowd ? countIn(town) >= Kota.config().npcPerTown || !DuniaBudget.mayAdd(level, DuniaBudget.NPC, at) : !DuniaSpine.enabled()) {
			return;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, at);
		if (spot == null) {
			spot = at;
		}
		int look = (int) (anchor.site() ^ (anchor.site() >>> 32));
		int realm = realmFor(role, within, look);
		Dao dao = role == NpcRole.WARGA ? null : Dao.values()[Math.floorMod(look >>> 3, Dao.COUNT)];
		int voice = voiceFor(role, look);
		npc.snapTo(spot.x, spot.y, spot.z, anchor.getYRot(), 0.0F);
		npc.setup(role, look, realm, Math.floorMod(look >>> 7, 4), dao, voice, 0, KotaNames.plate(look, role));
		npc.setHome(anchor.blockPosition());
		npc.setTown(town);
		npc.setAnchorSite(anchor.site());
		npc.setLonely(crowd ? 64.0D : dev.kultivator.dunia.tempat.hidup.HidupApi.STAF_JARAK, 600);
		npc.setYHeadRot(anchor.getYRot());
		if (role == NpcRole.PENJAGA) {
			npc.setFightsMonsters(true);
			if ("papan".equals(anchor.what())) {
				npc.memory().putBoolean("hold", true);
				npc.memory().putBoolean("board", true);
			}
		}
		BlockPos centre = Towns.centre(level.getServer(), town);
		if (centre != null) {
			npc.memory().putLong("square", centre.asLong());
		}
		if (!level.addFreshEntity(npc)) {
			return;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		npcs.put(anchor.getUUID(), npc);
		// a town that holds a grudge against a nearby player sends its guards at them
		if (role == NpcRole.PENJAGA) {
			for (ServerPlayer player : dev.kultivator.dunia.DuniaAnchors.dalam(within, anchor, DEKAT)) {
				if (KotaData.get(level.getServer()).reputation(town, player.getUUID()) < Kota.HOSTILE_REPUTATION || PasarGelap.raided(player, town)) {
					npc.markHostile(player);
				}
			}
		}
	}

	/** The strolling townsfolk of a town (capped by {@code kota.npcPerTown} and the NPC budget); every other role is staff. */
	static boolean crowd(NpcRole role) {
		return role == NpcRole.WARGA || role == NpcRole.ABADI;
	}

	/**
	 * {@code uji hidup audit}: why a people anchor has no NPC now (suffix of {@code kultivator.hidup.uji.audit.alasan.*}), or
	 * null when it stands.
	 */
	@Nullable String alasanKosong(ServerLevel level, DuniaAnchorEntity anchor) {
		if (npcOf(anchor) != null) {
			return null;
		}
		NpcRole role = ROLES.get(anchor.what());
		if (role == null) {
			return "peran";
		}
		if (!DuniaSpine.enabled()) {
			return "dunia_mati";
		}
		KotaConfig config = Kota.config();
		if (!config.enabled || Towns.isAbadi(anchor) && !config.immortalCity) {
			return "saklar";
		}
		if (anchor.state().getLongOr("dead_until", 0L) > level.getGameTime()) {
			return "mati";
		}
		if (dev.kultivator.dunia.DuniaAnchors.playersNear(level, anchor.position(), JARAK_ORANG).isEmpty()) {
			return "jauh";
		}
		if (crowd(role) && countIn(Towns.townOf(anchor)) >= config.npcPerTown) {
			return "topi_kota";
		}
		if (crowd(role) && !DuniaBudget.mayAdd(level, DuniaBudget.NPC, anchor.position())) {
			return DuniaBudget.overloaded(level.getServer()) ? "mspt" : "topi_npc";
		}
		return "menunggu";
	}

	/** Remembers that the anchor's NPC died (it comes back after 5 minutes). */
	void died(KultivatorNpc npc) {
		for (Map.Entry<UUID, KultivatorNpc> entry : npcs.entrySet()) {
			if (entry.getValue() == npc && npc.level() instanceof ServerLevel level) {
				for (DuniaAnchorEntity anchor : dev.kultivator.dunia.DuniaAnchors.loaded(level, "kota/")) {
					if (anchor.getUUID().equals(entry.getKey())) {
						anchor.state().putLong("dead_until", level.getGameTime() + RESPAWN_TICKS);
					}
				}
			}
		}
	}

	/** Realm of an anchor NPC: townsfolk Mortal / Qi Refining, merchants Foundation / Golden Core, guards follow visitors. */
	private static int realmFor(NpcRole role, List<ServerPlayer> within, int look) {
		int strongest = 0;
		for (ServerPlayer player : within) {
			CultivationData data = Cultivators.get(player);
			strongest = Math.max(strongest, data.realm().ordinal());
		}
		int pick = Math.floorMod(look, 2);
		return switch (role) {
			case WARGA -> pick;
			case PEDAGANG_PIL, PANDAI_PUSAKA, JURU_LELANG -> Realm.FOUNDATION.ordinal() + pick;
			case MAKELAR_GELAP -> Realm.GOLDEN_CORE.ordinal();
			case PEMILIK_TEH -> Realm.NASCENT_SOUL.ordinal();
			case PENJAGA -> Math.max(Realm.FOUNDATION.ordinal(), Math.min(Realm.GOLDEN_CORE.ordinal(), strongest));
			case ABADI, PEDAGANG_ABADI, JURU_LELANG_ABADI, PENJAGA_KEBUN -> Realm.IMMORTAL_ASCENSION.ordinal() + pick;
			case PENCERAMAH, UTUSAN -> Realm.TRUE_IMMORTAL.ordinal();
			default -> Realm.QI_REFINING.ordinal();
		};
	}

	/** The voice of DESIGN 34.8 (auctioneer m2, tea master f1, broker m3, guard m1), else by look. */
	private static int voiceFor(NpcRole role, int look) {
		return switch (role) {
			case JURU_LELANG -> 1;
			case PEMILIK_TEH -> 3;
			case MAKELAR_GELAP -> 2;
			case PENJAGA -> 0;
			case ABADI -> 5;
			default -> Math.floorMod(look >>> 11, KultivatorNpc.VOICES.length);
		};
	}

	// ================================================================== the door

	private void door(ServerLevel level, DuniaAnchorEntity anchor, long town) {
		if (!Kota.config().blackMarket || doorOf(anchor) != null) {
			return;
		}
		KotaPintuEntity door = KotaEntities.PINTU.create(level, EntitySpawnReason.EVENT);
		if (door == null) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(anchor.blockPosition());
		door.snapTo(at.x, at.y, at.z, anchor.getYRot(), 0.0F);
		door.bind(anchor.site(), town);
		if (level.addFreshEntity(door)) {
			doors.put(anchor.getUUID(), door);
		}
	}

	// ================================================================== the stele

	private void greet(ServerLevel level, DuniaAnchorEntity anchor, long town, boolean abadi, List<ServerPlayer> within) {
		for (ServerPlayer player : within) {
			String key = player.getUUID() + ":" + town;
			if (!greeted.add(key)) {
				continue;
			}
			Component name = abadi ? KotaNames.immortalCity(town) : KotaNames.town(town);
			KotaNet.fx(player, KotaNet.FX_BANNER, anchor.getX(), anchor.getY(), anchor.getZ(), abadi ? 1 : 0, (int) town, 60, new int[]{(int) (town >>> 32)});
			// the Sumber line of the feature rides on the arrival line as its hover (DESIGN 34.0 C5)
			Component source = abadi ? Component.translatable("kultivator.kota.kota_abadi.source")
					: Component.translatable("kultivator.kota.kota.inspired").append("\n\n").append(Component.translatable("kultivator.kota.teh.inspired"));
			player.sendSystemMessage(Component.translatable(abadi ? "kultivator.kota.tiba_abadi" : "kultivator.kota.tiba", name)
					.withStyle(style -> style.withColor(abadi ? 0xFFF0C0 : 0xE8C060)
							.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(source))));
			Advancements.grant(player, abadi ? "dunia/immortal_city" : "dunia/kota_found");
		}
	}

	// ================================================================== lights (client visuals only)

	private void lights(ServerLevel level, DuniaAnchorEntity anchor, long town, List<ServerPlayer> within, long now) {
		int kind = switch (anchor.what()) {
			case "panggung" -> KotaNet.FX_STAGE;
			case "kolam" -> KotaNet.FX_POND;
			case "abadi_awan" -> KotaNet.FX_CLOUDS;
			case "abadi_kebun" -> KotaNet.FX_PETALS;
			case "abadi_lonceng" -> -1;
			default -> KotaNet.FX_LANTERNS;
		};
		if (kind < 0) {
			return;
		}
		int[] points = points(anchor);
		int stage = kind == KotaNet.FX_STAGE && BalaiLelang.sessionRunning(town) ? 1 : 0;
		for (ServerPlayer player : within) {
			String key = player.getUUID() + ":" + anchor.getUUID() + ":" + stage;
			Long last = fxSent.get(key);
			if (last != null && now - last < FX_RESEND && now >= last) {
				continue;
			}
			fxSent.put(key, now);
			KotaNet.fx(player, kind, anchor.getX(), anchor.getY(), anchor.getZ(), stage, (int) anchor.getUUID().getLeastSignificantBits(),
					FX_RESEND + 200, points);
		}
		if (fxSent.size() > 4096) {
			fxSent.clear();
		}
	}

	/** The anchor's point list (data {@code pts}: relative x, y, z triples), or its own spot. */
	private static int[] points(DuniaAnchorEntity anchor) {
		ListTag list = anchor.data().getListOrEmpty("pts");
		if (list.isEmpty()) {
			return new int[]{0, 0, 0};
		}
		int n = Math.min(KotaNet.Fx.MAX_POINTS, list.size() - list.size() % 3);
		int[] points = new int[n];
		int i = 0;
		for (Tag tag : list) {
			if (i >= n) {
				break;
			}
			points[i++] = tag instanceof net.minecraft.nbt.IntTag value ? value.intValue() : 0;
		}
		return points;
	}
}
