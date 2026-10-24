package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.technique.source.InheritanceTrials;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the caves and sites give to the systems that already exist (DESIGN 38.0 rule 4, 38.A #20-28), always bounded:
 * the place factors of {@code TempatApi} ({@code kesengsaraan} 0.90 on Puncak Petir, {@code iblis_hati} 0.5 at the Heart
 * Mirror, {@code warisan} 1.10 in a tomb whose Heart trial was passed with a Giok Warisan in hand, {@code bintang} 2.0 on
 * a star stage, {@code binatang_xp} 1.25 in a beast den), the extra meditation sites of {@code SpiritSites} (Gua Pertapa
 * ×1.3, Gua Urat Roh ×1.6, Puncak Petir ×1.25, Mata Air Roh ×1.3, all within the old {@code CAP}), the waiting
 * followers of closed trials (38.0 rule 18), the Giok Warisan's pointer to the nearest known tomb and the known
 * treasure-birth places. Every check reads only the live runs of {@link GuaRuns} (a player is "at" a place only while its
 * run is alive), so nothing scans the world's anchors.
 */
public final class GuaTempat {

	/** Chance that a treasure birth picks a known spirit-vein cave or star stage in range (38.A #23). */
	public static final double LAHIR_PELUANG = 0.35D;

	/** Owner -> disciples told to wait at a closed trial's entrance. */
	private static final Map<UUID, Set<UUID>> PARKIR = new HashMap<>();
	/** Owner -> the last tomb the Giok Warisan pointed at. */
	private static final Map<UUID, Long> GIOK = new HashMap<>();

	static void init() {
		TempatApi.daftarFaktor("kesengsaraan", (p, k) -> on() && GuaRuns.at(p, PuncakPetir.class) != null ? 0.90D : 1.0D);
		TempatApi.daftarFaktor("iblis_hati", (p, k) -> on() && CerminHati.aktif(p) ? 0.5D : 1.0D);
		TempatApi.daftarFaktor("warisan", (p, k) -> {
			if (!on()) {
				return 1.0D;
			}
			MakamRun run = GuaRuns.at(p, MakamRun.class);
			return run != null && (run.bits(p) & GuaData.GIOK) != 0 ? 1.10D : 1.0D;
		});
		TempatApi.daftarFaktor("bintang", (p, k) -> on() && GuaRuns.at(p, PanggungBintang.class) != null ? 2.0D : 1.0D);
		TempatApi.daftarFaktor("binatang_xp", (p, k) -> on() && GuaRuns.at(p, SarangBinatang.class) != null ? 1.25D : 1.0D);
		SpiritSites.addSiteProvider(GuaTempat::situsMeditasi);
		Disciples.addFollowVeto((player, origin, destination) -> GuaApi.dalamUji(player) ? "kultivator.gua.veto" : null);
	}

	private static boolean on() {
		return GuaConfig.on();
	}

	// ================================================================== meditation sites

	private static SpiritSites.@Nullable ExtraSite situsMeditasi(ServerPlayer player) {
		if (!on()) {
			return null;
		}
		GuaUratRoh urat = GuaRuns.at(player, GuaUratRoh.class);
		if (urat != null) {
			return new SpiritSites.ExtraSite("gua/urat_roh", 1.6D, null, Component.translatable("kultivator.gua.situs.urat_roh"));
		}
		PertapaRun pertapa = GuaRuns.at(player, PertapaRun.class);
		if (pertapa != null) {
			return new SpiritSites.ExtraSite("gua/pertapa", 1.3D, pertapa.dao(), Component.translatable("kultivator.gua.situs.pertapa"));
		}
		MataAirRoh air = GuaRuns.at(player, MataAirRoh.class);
		if (air != null) {
			return new SpiritSites.ExtraSite("situs/mata_air_roh", 1.3D, Dao.LIFE, Component.translatable("kultivator.gua.situs.mata_air_roh"));
		}
		PuncakPetir puncak = GuaRuns.at(player, PuncakPetir.class);
		if (puncak != null) {
			return new SpiritSites.ExtraSite("situs/puncak_petir", 1.25D, Dao.LIGHTNING, Component.translatable("kultivator.gua.situs.puncak_petir"));
		}
		return null;
	}

	/** {@code SpiritBeasts} XP factor: a spirit beast fighting in a beast den gains ×1.25 (bounded there too). */
	static double xpBinatang(Mob beast) {
		if (!on() || !(beast.level() instanceof ServerLevel level)) {
			return 1.0D;
		}
		for (SarangBinatang den : GuaRuns.all(SarangBinatang.class)) {
			if (den.level == level && den.centre().distanceToSqr(beast.position()) <= SarangBinatang.WAKE * SarangBinatang.WAKE) {
				return 1.25D;
			}
		}
		return 1.0D;
	}

	// ================================================================== waiting followers (38.0 rule 18)

	/** A closed trial begins at {@code at}: the player's following disciples wait at the entrance. */
	static void parkir(ServerPlayer player, Vec3 at) {
		Set<UUID> parked = PARKIR.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
		int n = 0;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() != player.level() || disciple.distanceToSqr(player) > 32.0D * 32.0D || !disciple.mode().follows()) {
				continue;
			}
			disciple.setMode(DiscipleEntity.Mode.STAY);
			parked.add(disciple.getUUID());
			n++;
		}
		if (n > 0) {
			Msg.chat(player, Component.translatable("kultivator.gua.murid_menunggu", n).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Every 40 ticks: followers come back once their master left the closed trial. */
	static void tickParkir(MinecraftServer server) {
		if (PARKIR.isEmpty()) {
			return;
		}
		for (UUID owner : List.copyOf(PARKIR.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null && (GuaApi.dalamUji(player) || GuaRuns.at(player, MakamRun.class) != null
					|| GuaRuns.at(player, CerminHati.class) != null)) {
				continue;
			}
			Set<UUID> parked = PARKIR.remove(owner);
			if (parked == null) {
				continue;
			}
			for (DiscipleEntity disciple : Disciples.loaded(owner)) {
				if (parked.contains(disciple.getUUID()) && disciple.mode() == DiscipleEntity.Mode.STAY) {
					disciple.setMode(DiscipleEntity.Mode.FOLLOW);
				}
			}
		}
	}

	// ================================================================== the Giok Warisan's pointer

	/** Every 40 ticks: a held Giok Warisan points to the nearest known tomb (lodestone tracker) and tells the way. */
	static void tickGiok(MinecraftServer server, long tick) {
		if (!on()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack held = player.getMainHandItem().is(InheritanceTrials.GIOK_WARISAN) ? player.getMainHandItem()
					: player.getOffhandItem().is(InheritanceTrials.GIOK_WARISAN) ? player.getOffhandItem() : ItemStack.EMPTY;
			if (held.isEmpty()) {
				continue;
			}
			ServerLevel level = player.level();
			DuniaSites.Site site = GuaApi.makamTerdekatSitus(player);
			if (site == null) {
				if (tick % 200L == 0L) {
					Msg.actionBar(player, Component.translatable("kultivator.gua.giok.tiada").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			Long last = GIOK.get(player.getUUID());
			LodestoneTracker tracker = held.get(DataComponents.LODESTONE_TRACKER);
			if (last == null || last != site.id() || tracker == null) {
				held.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(site.dim(), site.pos())), false));
				GIOK.put(player.getUUID(), site.id());
			}
			if (tick % 200L == 0L) {
				int d = (int) Math.round(site.distance(level.dimension(), player.blockPosition()));
				Msg.actionBar(player, Component.translatable("kultivator.gua.giok.arah", d,
						Component.translatable("kultivator.gua.arah." + arah(player.position(), Vec3.atCenterOf(site.pos())))).withStyle(ChatFormatting.AQUA));
			}
		}
	}

	/** One of eight compass words (0 north, 1 north-east, ... 7 north-west) from {@code from} to {@code to}. */
	static int arah(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		return Math.floorMod((int) Math.round(angle / 45.0D), 8);
	}

	// ================================================================== treasure births (38.A #23)

	/**
	 * At {@link #LAHIR_PELUANG}: a known spirit-vein cave treasure point or star stage of {@code level} between
	 * {@code min} and {@code max} blocks of {@code focus}, else null (the old random search runs).
	 */
	static @Nullable BlockPos tempatLahir(ServerLevel level, ServerPlayer focus, double min, double max) {
		if (!on() || level.getRandom().nextDouble() >= LAHIR_PELUANG) {
			return null;
		}
		List<BlockPos> pilihan = new ArrayList<>();
		for (GuaData.Lahir l : GuaData.get(level.getServer()).lahir()) {
			if (!l.dim().equals(level.dimension()) || !DuniaSites.knows(focus, l.situs())) {
				continue;
			}
			double dx = l.pos().getX() - focus.getX();
			double dz = l.pos().getZ() - focus.getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d >= min && d <= max) {
				pilihan.add(l.pos());
			}
		}
		return pilihan.isEmpty() ? null : pilihan.get(level.getRandom().nextInt(pilihan.size()));
	}

	static void reset() {
		PARKIR.clear();
		GIOK.clear();
	}

	private GuaTempat() {
	}
}
