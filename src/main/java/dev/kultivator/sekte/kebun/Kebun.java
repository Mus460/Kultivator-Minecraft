package dev.kultivator.sekte.kebun;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.KebunService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.kebun.TabelHasil.Benih;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module W, Kebun Obat &amp; Tambang Roh (DESIGN 36.2.10, 36.4 W): the entry ({@link #init}, one line in
 * {@code SekteContent}) and the {@link KebunService} (contract C36.W1).
 * <p>
 * Everything is data in the hall's section {@code sekte_kebun} and computed lazily like {@code sect.event.Duties}: every
 * {@code hitungDetik} seconds for each online owner whose hall chunk is loaded (at most one world day banked), when the
 * board opens and on the test commands. Gardeners (activity {@code ACT_TEND}) and miners ({@code ACT_MINE}) hold their
 * spots at the anchors of W's two template components; the yield goes to the Lumbung ({@code LumbungOverflow}). No block
 * changes here: the beds and the mine mouth are built by the sect engine from deposited materials, the herbs and the ore
 * are drawn by the client.
 * <p>
 * Sumber: the Hundred Medicine Garden (<i>A Record of a Mortal's Journey</i> ch. 150-151); Nüwa's five-coloured stones
 * (<i>Huainanzi</i> ch. 6) and the jade mountains of the <i>Shanhaijing</i>.
 */
public final class Kebun implements KebunService {

	/** The away reason of a miner hurt in a cave-in (back after a world day). */
	public static final String REASON_HURT = "sekte_kebun_luka";
	/** W's beats (EXTRA 190-199, DESIGN 36.3.7): a rare bloom, the mountain core. */
	public static final int BEAT_BLOOM = 190;
	public static final int BEAT_CORE = 191;
	/** The board's two pages. */
	public static final int PAGE_GARDEN = 0;
	public static final int PAGE_MINE = 1;
	/** Workers must stand within this many blocks of the stele to be given a job. */
	public static final double WORK_RANGE = 64.0D;
	/** World payloads reach players this close to the stele. */
	public static final double SYNC_RANGE = 96.0D;

	static final Kebun SERVICE = new Kebun();
	private static @Nullable MinecraftServer server;
	private static long ticks;
	private static boolean initialised;

	/** One hall at work: the owner, its stele, level and live section, the time, the owner when online. */
	record Aula(UUID owner, Halls.HallView hall, ServerLevel level, CompoundTag sec, long now, @Nullable ServerPlayer player,
				RandomSource random, MinecraftServer server) {
	}

	/** From {@code SekteContent.init} (both sides). */
	/** Registry content (K1): the Mountain Spirit Core, the plot/mine components and the payload types, always. */
	public static void registerContent() {
		KebunItems.init();
		KebunKomponen.register();
		KebunPayloads.register();
	}

	public static void init() {
		registerContent();
		if (initialised || !SekteContent.on(SekteIds.W)) {
			return;
		}
		initialised = true;
		KebunConfig.load();
		KebunCommands.init();
		SekteApi.setKebun(SERVICE);
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
		ServerTickEvents.END_SERVER_TICK.register(Kebun::tick);
		UseBlockCallback.EVENT.register(Kebun::useBlock);
		Halls.addReturnHandler(REASON_HURT, (level, owner, stored, info) -> {
			BlockPos mouth = EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_MOUTH);
			return mouth == null ? null : Vec3.atBottomCenterOf(mouth);
		});
		DiscipleHooks.addInfo((entity, master, tag) -> info(entity, tag));
	}

	// ================================================================== KebunService (C36.W1)

	@Override
	public int plots(UUID owner) {
		return plotCount(owner);
	}

	@Override
	public int mineLevel(UUID owner) {
		return TambangRoh.level(owner);
	}

	@Override
	public boolean working(UUID owner, UUID soul) {
		if (owner == null || soul == null) {
			return false;
		}
		CompoundTag sec = section(owner);
		return slotOf(sec.getCompoundOrEmpty("rawat"), soul) > 0 || slotOf(TambangRoh.miners(sec), soul) > 0;
	}

	// ================================================================== data

	/** The owner's live section {@code sekte_kebun} (a detached empty tag without a hall). */
	static CompoundTag section(UUID owner) {
		return Halls.section(owner, SekteIds.hallSection(SekteIds.W));
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** The working context of the owner's hall, or null when it has no active hall in a loaded level. */
	static @Nullable Aula aula(UUID owner) {
		MinecraftServer s = server;
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (s == null || hall == null || !hall.active() || hall.dim() == null) {
			return null;
		}
		ServerLevel level = s.getLevel(hall.dim());
		if (level == null) {
			return null;
		}
		return new Aula(owner, hall, level, section(owner), s.overworld().getGameTime(), s.getPlayerList().getPlayer(owner),
				level.getRandom(), s);
	}

	/** Beds that count: the built {@code kebun_petak_<n>} anchors (or the test override), at most {@code petakMaks}. */
	static int plotCount(UUID owner) {
		if (owner == null) {
			return 0;
		}
		int forced = section(owner).getIntOr("uji_petak", 0);
		int n = 0;
		if (forced > 0) {
			n = forced;
		} else {
			for (int i = 1; i <= 12; i++) {
				if (EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_PLOT + i) == null) {
					break;
				}
				n = i;
			}
		}
		return Math.max(0, Math.min(KebunConfig.get().petakMaks, n));
	}

	/** Gardener slots: one per two beds. */
	static int tenderSlots(int plots) {
		return (plots + 1) / 2;
	}

	/** The slot (1-based) {@code soul} holds in a slot compound ({@code "1" → soul}), 0 when none. */
	static int slotOf(CompoundTag slots, UUID soul) {
		String id = soul.toString();
		for (String key : slots.keySet()) {
			CompoundTag entry = slots.getCompoundOrEmpty(key);
			if (id.equals(entry.getStringOr("soul", ""))) {
				try {
					return Integer.parseInt(key);
				} catch (NumberFormatException exception) {
					return 0;
				}
			}
		}
		return 0;
	}

	static @Nullable UUID uuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** The owner's loaded disciple with this soul, or null. */
	static @Nullable DiscipleEntity bySoul(UUID owner, @Nullable UUID soul) {
		if (soul == null) {
			return null;
		}
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(soul)) {
				return disciple;
			}
		}
		return null;
	}

	/** The master's list entry of {@code soul} (read only), or null; only with the master online. */
	static @Nullable CompoundTag listEntry(@Nullable ServerPlayer master, UUID soul) {
		if (master == null) {
			return null;
		}
		String id = soul.toString();
		for (Tag tag : Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list")) {
			if (tag instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				return entry;
			}
		}
		return null;
	}

	/** False only when the master is online and the soul is not a living disciple of the list any more. */
	static boolean stillListed(@Nullable ServerPlayer master, UUID soul) {
		if (master == null) {
			return true;
		}
		CompoundTag entry = listEntry(master, soul);
		return entry != null && !entry.getBooleanOr("dead", false);
	}

	/** A worker's name: the loaded body, else the master's list, else "?". */
	static Component nameOf(UUID owner, @Nullable ServerPlayer master, UUID soul) {
		DiscipleEntity disciple = bySoul(owner, soul);
		if (disciple != null) {
			return disciple.getDisplayName();
		}
		CompoundTag entry = listEntry(master, soul);
		if (entry != null) {
			return entry.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		}
		return Component.literal("?");
	}

	/** True when the disciple has a Wood or Water root (the gardener's 1.1, DESIGN 36.2.10). */
	static boolean greenRoot(DiscipleData data) {
		int roots = data.rootElements();
		return (roots & Element.WOOD.bit()) != 0 || (roots & Element.WATER.bit()) != 0;
	}

	/** True when the disciple has an Earth or Metal root (the miner's 1.1). */
	static boolean stoneRoot(DiscipleData data) {
		int roots = data.rootElements();
		return (roots & Element.EARTH.bit()) != 0 || (roots & Element.METAL.bit()) != 0;
	}

	/** The disciple's own {@code extra.kerja} (live, created when missing). */
	static CompoundTag kerja(DiscipleData data) {
		return data.extraSection(SekteIds.EXTRA_W);
	}

	/** A line of the board's own short log (the last eight) and, when {@code hall} is set, of the Aula's Catatan too. */
	static void note(UUID owner, Component line, boolean hall) {
		CompoundTag sec = section(owner);
		ListTag log = sec.getList("log").orElse(null);
		if (log == null) {
			log = new ListTag();
			sec.put("log", log);
		}
		CompoundTag entry = new CompoundTag();
		entry.putLong("day", Ingatan.today(server));
		entry.store("text", ComponentSerialization.CODEC, line);
		log.add(entry);
		while (log.size() > 8) {
			log.remove(0);
		}
		Halls.setDirty();
		if (hall) {
			SekteLog.log(server, owner, SekteIds.W, line);
		}
	}

	// ================================================================== the rhythm

	private static void tick(MinecraftServer s) {
		server = s;
		ticks++;
		if (ticks % 20L != 0L) {
			return;
		}
		KebunConfig cfg = KebunConfig.get();
		long compute = cfg.hitungDetik * 20L;
		long sync = cfg.sinkronDetik * 20L;
		for (ServerPlayer player : List.copyOf(s.getPlayerList().getPlayers())) {
			UUID owner = player.getUUID();
			long phase = Math.floorMod(owner.hashCode(), 20) * 20L;
			if ((ticks + phase) % compute == 0L) {
				try {
					compute(owner);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kebun: computing the hall of {} failed", owner, exception);
				}
			}
			if ((ticks + phase) % sync == 0L) {
				try {
					KebunPayloads.syncWorld(owner);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kebun: syncing the hall of {} failed", owner, exception);
				}
			}
		}
	}

	/** Works out the owner's garden and mine since the last time (at most a world day), then the passing days. */
	static void compute(UUID owner) {
		Aula aula = aula(owner);
		if (aula == null || !aula.level().isLoaded(aula.hall().pos())) {
			return;
		}
		long last = aula.sec().getLongOr("last", aula.now());
		long elapsed = Math.max(0L, Math.min(24000L, aula.now() - last));
		aula.sec().putLong("last", aula.now());
		advance(aula, elapsed);
		long today = Ingatan.today(aula.server());
		long seen = aula.sec().getLongOr("day", -1L);
		if (seen < 0L) {
			aula.sec().putLong("day", today);
		} else if (today > seen) {
			for (long day = Math.max(seen + 1L, today - 2L); day <= today; day++) {
				GiliranTambang.endOfDay(aula, day);
			}
			aula.sec().putLong("day", today);
		}
		Halls.setDirty();
	}

	/** Grows the beds and works the mine by {@code elapsed} ticks (the tests call it with whole days). */
	static void advance(Aula aula, long elapsed) {
		KebunConfig cfg = KebunConfig.get();
		if (cfg.kebunAktif) {
			Pertumbuhan.grow(aula, elapsed);
		}
		if (cfg.tambangAktif) {
			GiliranTambang.work(aula, elapsed);
		}
	}

	// ================================================================== jobs

	/** Why a disciple cannot take a job now, or null. */
	static @Nullable Component refuse(Aula aula, @Nullable DiscipleEntity disciple) {
		if (disciple == null || !disciple.isAlive() || disciple.isRemoved() || disciple.role() != DiscipleEntity.Role.DISCIPLE
				|| disciple.isReleasing()) {
			return Component.translatable("kultivator.sekte.kebun.job.gone");
		}
		if (disciple.data().breakingThrough() || DuelRules.inDuel(disciple)) {
			return Component.translatable("kultivator.sekte.kebun.job.busy", disciple.getDisplayName());
		}
		if (!disciple.level().dimension().equals(aula.hall().dim())
				|| disciple.distanceToSqr(Vec3.atBottomCenterOf(aula.hall().pos())) > WORK_RANGE * WORK_RANGE) {
			return Component.translatable("kultivator.sekte.kebun.job.far", disciple.getDisplayName());
		}
		if (SekteApi.hati().defiant(disciple.data())) {
			return Component.translatable("kultivator.sekte.kebun.job.defiant", disciple.getDisplayName());
		}
		long restUntil = kerja(disciple.data()).getLongOr("rest_until", 0L);
		if (restUntil > aula.now()) {
			return Component.translatable("kultivator.sekte.kebun.job.resting", disciple.getDisplayName());
		}
		return null;
	}

	/** Takes the soul off any W job it holds (garden or mine), quietly. */
	static void releaseEverywhere(Aula aula, UUID soul) {
		CompoundTag tenders = aula.sec().getCompoundOrEmpty("rawat");
		int t = slotOf(tenders, soul);
		if (t > 0) {
			Pertumbuhan.release(aula, t, false);
		}
		int m = slotOf(TambangRoh.miners(aula.sec()), soul);
		if (m > 0) {
			GiliranTambang.release(aula, m, false, false);
		}
	}

	/** Where gardener slot {@code slot} stands: its anchor, or a ring cell near the stele (test beds). */
	static BlockPos tenderSpot(Aula aula, int slot) {
		BlockPos anchor = EngineApi.get().anchor(aula.owner(), KebunKomponen.ANCHOR_TENDER + slot);
		return anchor != null ? anchor : ringSpot(aula, 7, slot * 40);
	}

	/** Where miner slot {@code slot} (1-6) stands: the adit for 1, the hidden cavity for the rest. */
	static BlockPos minerSpot(Aula aula, int slot) {
		int spot = switch (slot) {
			case 1 -> 1;
			case 2, 5 -> 2;
			case 3, 6 -> 3;
			default -> 4;
		};
		BlockPos anchor = EngineApi.get().anchor(aula.owner(), KebunKomponen.ANCHOR_INSIDE + spot);
		return anchor != null ? anchor : ringSpot(aula, 9, 200 + slot * 30);
	}

	/** A cell on a ring around the stele (only for test beds and a test mine without the buildings). */
	private static BlockPos ringSpot(Aula aula, int radius, int degrees) {
		double angle = Math.toRadians(degrees);
		BlockPos stele = aula.hall().pos();
		return stele.offset((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
	}

	// ================================================================== the board in the world

	/**
	 * Right-click on a bed's soil or the mine mouth of one's own sect: with a seed in hand on a bare bed it is sown at
	 * once; a ripe bed is harvested; otherwise the board opens on that page. Anything else passes (the barrel cart opens
	 * as a barrel).
	 */
	private static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND
				|| player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (level.getBlockState(pos).is(Blocks.BARREL)) {
			return InteractionResult.PASS;
		}
		UUID owner = serverPlayer.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || !level.dimension().equals(hall.dim()) || pos.distSqr(hall.pos()) > 160 * 160) {
			return InteractionResult.PASS;
		}
		int plots = plotCount(owner);
		for (int i = 1; i <= plots; i++) {
			BlockPos bed = EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_PLOT + i);
			if (bed != null && (pos.equals(bed.below()) || pos.equals(bed))) {
				clickBed(serverPlayer, i - 1);
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		BlockPos mouth = EngineApi.get().anchor(owner, KebunKomponen.ANCHOR_MOUTH);
		if (mouth != null && Math.abs(pos.getX() - mouth.getX()) <= 3 && Math.abs(pos.getZ() - mouth.getZ()) <= 4
				&& pos.getY() >= mouth.getY() - 1 && pos.getY() <= mouth.getY() + 4) {
			compute(owner);
			KebunPayloads.open(serverPlayer, PAGE_MINE);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	private static void clickBed(ServerPlayer player, int index) {
		UUID owner = player.getUUID();
		compute(owner);
		Aula aula = aula(owner);
		if (aula == null) {
			return;
		}
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		ItemStack held = player.getMainHandItem();
		Benih seed = Benih.of(held);
		if (bed.empty() && seed != null) {
			Pertumbuhan.plantFromHand(aula, index, player, held, seed);
			return;
		}
		if (bed.ripe()) {
			Pertumbuhan.harvest(aula, index, null, player);
			return;
		}
		KebunPayloads.open(player, PAGE_GARDEN);
	}

	// ================================================================== the disciple scroll

	/** {@code DiscipleHooks.addInfo}: the job, streak and rest of a W worker ({@code sekte_kebun} compound). */
	private static void info(DiscipleEntity entity, CompoundTag tag) {
		CompoundTag kerja = entity.data().extra().getCompoundOrEmpty(SekteIds.EXTRA_W);
		String job = kerja.getStringOr("job", "");
		if (job.isEmpty() && kerja.getIntOr("streak", 0) <= 0) {
			return;
		}
		CompoundTag out = new CompoundTag();
		out.putString("job", job);
		out.putInt("streak", kerja.getIntOr("streak", 0));
		out.putInt("rest", KebunConfig.get().hariLibur);
		out.putInt("total", kerja.getIntOr("total", 0));
		tag.put("sekte_kebun", out);
	}

	/** Chat to the owner when online. */
	static void tell(@Nullable ServerPlayer player, Component message, ChatFormatting color) {
		if (player != null) {
			Msg.chat(player, message.copy().withStyle(color));
		}
	}

	/** The owner's loaded disciples within the work range of the stele (the board's candidates). */
	static List<DiscipleEntity> nearby(Aula aula) {
		List<DiscipleEntity> list = new ArrayList<>();
		Vec3 stele = Vec3.atBottomCenterOf(aula.hall().pos());
		for (DiscipleEntity disciple : Disciples.loaded(aula.owner())) {
			if (disciple.isAlive() && disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.level().dimension().equals(aula.hall().dim())
					&& disciple.distanceToSqr(stele) <= WORK_RANGE * WORK_RANGE) {
				list.add(disciple);
			}
		}
		return list;
	}

	private Kebun() {
	}
}
