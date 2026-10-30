package dev.kultivator.sekte.gabung;

import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.dunia.tempat.kompleks.RencanaKompleks;
import dev.kultivator.dunia.tempat.tema.iblis.GulunganTerlarangItem;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.hidup.MuridTitipan;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The watchers of the sect rules (DESIGN 41.6.11, detection column): they turn what a member does into
 * {@link GabungApi#langgar} calls; {@link BalaiHukum} then judges. Nothing ticks per member: the damage, death, chest and
 * item events are vanilla/Fabric callbacks, and one sweep every {@link #SAPU_TICK} ticks over the online members keeps the
 * presence record (A9) and starts the lazy upkeep when a member walks into the own compound (41.12.3).
 * <ul>
 * <li><b>A1</b> harming a fellow member (player or sect body) inside the sect's grounds. Righteous and neutral sects: only on
 * the lei tai (the compound {@code arena}) with an accepted spar ({@code /sect spar}) is it allowed. Demonic sects (K10):
 * an accepted spar anywhere is the "challenge first"; striking an elder there is A1 too (the strong are honoured, but only
 * after a challenge).</li>
 * <li><b>A2</b> killing a fellow player member outside an accepted spar.</li>
 * <li><b>A3</b> opening a {@code peti_milik} chest of the own compound (demonic: only when witnessed).</li>
 * <li><b>A7</b> striking a named elder or the master of the own sect (righteous/neutral).</li>
 * <li><b>A8</b> using a Forbidden Scroll (38.T.7.7) inside a righteous sect's grounds.</li>
 * <li><b>A6</b> teaching the sect's Teknik Warisan to an own disciple: a member breaks A6; a former member without the
 * master's blessing loses standing ({@link Keluar#ajarWarisan}).</li>
 * <li><b>A9</b> presence: {@code bagian("aturan").hadir} is the last world day the member stood in the own grounds;
 * {@link BalaiHukum} demotes after 30 days ("Lalai").</li>
 * </ul>
 * A4 (War Call) and A5 (plotting with the sect's enemies) are reported by 41D through {@link #komplotan} and
 * {@code GabungApi.langgar(player, Aturan.A4, ...)}. Repeated hits are counted once per {@link #JEDA_TICK} per victim.
 */
public final class Pemeriksa {

	/** Ticks between two sweeps over the online members (presence, entering the compound). */
	public static final int SAPU_TICK = 100;
	/** One counted offence per attacker and victim per this many ticks (a flurry of blows is one offence). */
	public static final int JEDA_TICK = 600;
	/** Blocks from the compound arena that count as the lei tai. */
	public static final double LEI_TAI = 14.0D;
	/** Blocks from a sect's gate that count as its grounds when no compound was born. */
	public static final double WILAYAH_GERBANG = 48.0D;
	/** Witnesses named in the record (players and named characters who saw it). */
	public static final int SAKSI_MAKS = 3;

	/** attacker -> victim -> game tick of the last counted offence. */
	private static final Map<UUID, Map<UUID, Long>> JEDA = new HashMap<>();
	/** Members inside their own grounds at the last sweep. */
	private static final Map<UUID, String> DI_WILAYAH = new HashMap<>();
	private static boolean registered;

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Pemeriksa::luka);
		ServerLivingEntityEvents.AFTER_DEATH.register(Pemeriksa::mati);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl && !sp.isSpectator()) {
				bukaPeti(sl, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				ItemStack stack = sp.getItemInHand(hand);
				if (stack.getItem() instanceof GulunganTerlarangItem && !sp.getCooldowns().isOnCooldown(stack)) {
					seniIblis(sl, sp);
				}
			}
			return InteractionResult.PASS;
		});
		DiscipleHooks.addOnTaught(Pemeriksa::diajarkan);
		ServerTickEvents.END_SERVER_TICK.register(Pemeriksa::sapu);
		ServerPlayerEvents.LEAVE.register(player -> {
			JEDA.remove(player.getUUID());
			DI_WILAYAH.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			JEDA.clear();
			DI_WILAYAH.clear();
		});
	}

	// ================================================================== grounds

	/**
	 * The NPC sect whose grounds hold {@code pos}: the born compound there, else a gate within {@link #WILAYAH_GERBANG}
	 * blocks (overworld only); null outside every sect.
	 */
	public static @Nullable String wilayah(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		RencanaKompleks.Kompleks k = KompleksApi.kompleksDi(level, pos);
		if (k != null) {
			return k.sekte();
		}
		for (String sekte : SekteApi.diplomasi().sects()) {
			BlockPos gate = SekteApi.diplomasi().gate(sekte);
			if (gate != null && horizontal(gate, pos) <= WILAYAH_GERBANG * WILAYAH_GERBANG) {
				return sekte;
			}
		}
		return null;
	}

	/** True when {@code pos} is on the lei tai of {@code sekte} (its compound arena). */
	public static boolean leiTai(String sekte, BlockPos pos) {
		BlockPos arena = KompleksApi.spot(sekte, "arena");
		return arena != null && horizontal(arena, pos) <= LEI_TAI * LEI_TAI && Math.abs(arena.getY() - pos.getY()) <= 8;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// ================================================================== A1, A7 (damage) and A2 (death)

	private static void luka(LivingEntity victim, DamageSource source, float baseDamage, float taken, boolean blocked) {
		if (taken <= 0.0F || !Gabung.aktif() || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim
				|| attacker.isCreative() || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		String sekte = GabungApi.sekte(attacker.getUUID());
		if (sekte == null || !sekte.equals(wilayah(level, victim.blockPosition()))) {
			return;
		}
		boolean iblis = SekteApi.diplomasi().aliran(sekte) == 2;
		Aturan aturan = null;
		if (victim instanceof ServerPlayer target) {
			if (GabungApi.anggota(target.getUUID(), sekte) && !tantanganSah(attacker, target, sekte, iblis)) {
				aturan = Aturan.A1;
			}
		} else if (victim instanceof KultivatorNpc npc) {
			String tokoh = Tokoh.dari(npc);
			boolean tetua = TokohSekte.idSah(tokoh) && sekte.equals(TokohSekte.sekteDari(tokoh));
			if (tetua) {
				// K10: a demonic sect honours the strong, but only after a challenge, so an unprovoked blow is A1 there
				aturan = iblis ? Aturan.A1 : Aturan.A7;
			} else if (tubuhSekte(npc, sekte)) {
				aturan = Aturan.A1;
			}
		} else if (victim instanceof DiscipleEntity murid && !murid.isOwnedBy(attacker) && sekte.equals(MuridTitipan.sekteDari(murid))) {
			// 41.8.7: a ward of the sect is not struck in its grounds, no more than a member
			aturan = Aturan.A1;
		}
		if (aturan == null || !jeda(attacker, victim.getUUID(), level.getGameTime())) {
			return;
		}
		catatSaksi(attacker, level, victim.blockPosition());
		GabungApi.langgar(attacker, aturan, victim.blockPosition(), false);
	}

	private static void mati(LivingEntity victim, DamageSource source) {
		if (!Gabung.aktif() || !(victim instanceof ServerPlayer target) || !(source.getEntity() instanceof ServerPlayer killer)
				|| killer == target || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		String sekte = GabungApi.sekte(killer.getUUID());
		if (sekte == null || !GabungApi.anggota(target.getUUID(), sekte) || Sparring.areSparring(killer, target)) {
			return;
		}
		catatSaksi(killer, level, target.blockPosition());
		GabungApi.langgar(killer, Aturan.A2, target.blockPosition(), false);
	}

	/**
	 * K10: is this fight between two members of {@code sekte} allowed? An accepted spar ({@code /sect spar}) is the formal
	 * challenge: anywhere for a demonic sect, only on the lei tai for the others.
	 */
	public static boolean tantanganSah(ServerPlayer a, ServerPlayer b, String sekte, boolean iblis) {
		if (!Sparring.areSparring(a, b) && !Sparring.inDuel(a)) {
			return false;
		}
		return iblis || leiTai(sekte, a.blockPosition()) && leiTai(sekte, b.blockPosition());
	}

	/** A generic body of sect {@code sekte}'s compound (disciples, elders, servants, champion). */
	private static boolean tubuhSekte(KultivatorNpc npc, String sekte) {
		NpcRole role = npc.role();
		boolean sektenya = role == NpcRole.MURID_SEKTE_LUAR || role == NpcRole.MURID_SEKTE_DALAM || role == NpcRole.TETUA_SEKTE
				|| role == NpcRole.PELAYAN_SEKTE || role == NpcRole.JUARA_SEKTE;
		if (!sektenya) {
			return false;
		}
		long situs = npc.situsPenduduk();
		String dari = situs == 0L ? null : KompleksApi.sekteDi(situs);
		return dari == null || sekte.equals(dari);
	}

	private static boolean jeda(ServerPlayer attacker, UUID victim, long now) {
		Map<UUID, Long> map = JEDA.computeIfAbsent(attacker.getUUID(), key -> new HashMap<>());
		Long last = map.get(victim);
		if (last != null && now - last < JEDA_TICK) {
			return false;
		}
		map.put(victim, now);
		if (map.size() > 32) {
			map.entrySet().removeIf(e -> now - e.getValue() >= JEDA_TICK);
		}
		return true;
	}

	// ================================================================== A3 (chests) and A8 (forbidden scroll)

	private static void bukaPeti(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!Gabung.aktif() || player.isCreative() || !(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity)) {
			return;
		}
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null) {
			return;
		}
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info == null || !sekte.equals(KompleksApi.sekteDi(info.situsId()))) {
			return;
		}
		for (Titik t : TempatApi.titik(level, info.situsId(), "peti_milik")) {
			if (t.pos().distManhattan(pos) <= 1) {
				if (jeda(player, new UUID(pos.asLong(), 3L), level.getGameTime())) {
					catatSaksi(player, level, pos);
					// demonic sects count it only when witnessed (GabungApi.langgar asks HidupApi.saksi for A3 there)
					GabungApi.langgar(player, Aturan.A3, pos, false);
				}
				return;
			}
		}
	}

	private static void seniIblis(ServerLevel level, ServerPlayer player) {
		if (!Gabung.aktif()) {
			return;
		}
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null || SekteApi.diplomasi().aliran(sekte) != 0 || !sekte.equals(wilayah(level, player.blockPosition()))) {
			return;
		}
		if (jeda(player, new UUID(8L, 8L), level.getGameTime())) {
			catatSaksi(player, level, player.blockPosition());
			GabungApi.langgar(player, Aturan.A8, player.blockPosition(), false);
		}
	}

	// ================================================================== A6 (teaching the Warisan)

	private static void diajarkan(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		if (Gabung.aktif() && technique != null) {
			Keluar.ajarWarisan(master, technique.id());
		}
	}

	// ================================================================== A5 (reported by 41D)

	/**
	 * Reported by 41D when the player's own hall makes a pact with, or sends a gift to, {@code sekteLain} while it is at war
	 * with the player's sect (A5, 20 points).
	 */
	public static void komplotan(ServerPlayer player, String sekteLain) {
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte != null && !sekte.equals(sekteLain)) {
			GabungApi.langgar(player, Aturan.A5, player.blockPosition(), false);
		}
	}

	// ================================================================== witnesses

	/**
	 * Remembers who saw the offence for the trial screen ({@code bagian("hukum").saksi}): other players within 16 blocks
	 * and named characters or sect bodies with a line of sight, at most {@link #SAKSI_MAKS} names.
	 */
	static void catatSaksi(ServerPlayer pelaku, ServerLevel level, BlockPos pos) {
		List<String> names = new ArrayList<>();
		AABB box = new AABB(pos).inflate(16.0D);
		for (Player other : level.getEntitiesOfClass(Player.class, box, p -> p != pelaku && p.isAlive() && !p.isSpectator())) {
			if (names.size() < SAKSI_MAKS) {
				names.add(other.getName().getString());
			}
		}
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, box, n -> n.isAlive() && n.hasLineOfSight(pelaku))) {
			if (names.size() >= SAKSI_MAKS) {
				break;
			}
			String tokoh = Tokoh.dari(npc);
			if (!tokoh.isEmpty() || npc.role().townsfolk() || npc.role() == NpcRole.MURID_SEKTE_LUAR || npc.role() == NpcRole.MURID_SEKTE_DALAM) {
				names.add(npc.getName().getString());
			}
		}
		MinecraftServer server = level.getServer();
		Keanggotaan k = GabungData.get(server).peek(pelaku.getUUID());
		if (k == null) {
			return;
		}
		CompoundTag hukum = k.bagian("hukum");
		hukum.putString("saksi", String.join(", ", names));
		GabungData.get(server).setDirty();
	}

	// ================================================================== sweep: presence (A9) and entering the grounds

	private static void sapu(MinecraftServer server) {
		if (server.getTickCount() % SAPU_TICK != 0 || !Gabung.aktif()) {
			return;
		}
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Keanggotaan k = data.peek(player.getUUID());
			String sekte = k == null ? null : k.sekteAktif();
			if (sekte == null) {
				DI_WILAYAH.remove(player.getUUID());
				continue;
			}
			boolean di = sekte.equals(wilayah(player.level(), player.blockPosition()));
			if (!di) {
				DI_WILAYAH.remove(player.getUUID());
				continue;
			}
			CompoundTag aturan = k.bagian("aturan");
			if (aturan.getLongOr("hadir", -1L) != hari) {
				aturan.putLong("hadir", hari);
				data.setDirty();
			}
			if (DI_WILAYAH.put(player.getUUID(), sekte) == null) {
				// 41.12.3: entering the own grounds runs the lazy upkeep, then the Law Hall's summons (if any)
				if (k.sentuh(player, hari)) {
					data.setDirty();
				}
				BalaiHukum.tibaDiWilayah(player, k);
			}
		}
	}

	/** True while the member stands in the own grounds (last sweep). */
	public static boolean diWilayahSendiri(ServerPlayer player) {
		return DI_WILAYAH.containsKey(player.getUUID());
	}

	/** World days since the member last stood in the own grounds (0 when never recorded: the joining day counts). */
	public static long hariAbsen(Keanggotaan k, long hari) {
		long hadir = k.bagian("aturan").getLongOr("hadir", -1L);
		long dasar = hadir >= 0L ? hadir : k.hariMasuk;
		return dasar < 0L ? 0L : Math.max(0L, hari - dasar);
	}

	static Component nama(String sekte) {
		Component name = sekte == null ? null : SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte == null ? "?" : sekte) : name;
	}

	private Pemeriksa() {
	}
}
