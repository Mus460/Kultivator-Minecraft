package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boat hire at the Tambatan Nelayan (DESIGN 38.T.3.4 "Memancing berbayar", 38.T.10.2): a fisherman at the
 * {@code pelabuhan/perahu_sewa} stand rents a {@link PerahuNelayanEntity} for 2 NB per world hour (1000 ticks; 1 or 3
 * hours), or the race boat of the Hari Dewi Laut regatta (5 NB, while its entry is open, {@link HariDewiLaut}).
 * <ul>
 * <li>The boat appears on the water beside the stand and the player sits at the oars; W/S row, A/D turn (client-side
 * steering, like a vanilla boat). It never sinks; in a thunderstorm the bay pushes it a little.</li>
 * <li>Leash: past {@value #LEPAS_PERINGATAN} blocks from the stand the fisherman calls, past {@value #LEPAS} the boat is
 * towed back (the player is set ashore at the stand). A boat left alone for {@value #DITINGGAL} ticks, or whose hour is
 * over, rows itself home (removed).</li>
 * <li>Fishing from a hired boat: vanilla fishing, plus a 10 % chance of an Ikan Roh with every catch (the catch is seen
 * by the experience orb it gives).</li>
 * </ul>
 * Riding a hired boat holds the player in {@code tema.perahu} ({@link TemaSibuk}: no scheduled danger starts and the
 * disciples wait on the pier).
 */
public final class PerahuSewa {

	public static final long HARGA_JAM = 2L;
	public static final long HARGA_LOMBA = 5L;
	public static final int JAM = 1000;
	public static final double LEPAS_PERINGATAN = 180.0D;
	public static final double LEPAS = 200.0D;
	public static final int DITINGGAL = 600;
	public static final double PELUANG_IKAN_ROH = 0.10D;

	/** One rental. */
	static final class Sewa {
		final UUID pemain;
		final long situs;
		final Vec3 rumah;
		final long sampai;
		final boolean lomba;
		PerahuNelayanEntity perahu;
		long turunSejak = -1L;
		boolean diperingati;

		Sewa(UUID pemain, long situs, Vec3 rumah, long sampai, boolean lomba, PerahuNelayanEntity perahu) {
			this.pemain = pemain;
			this.situs = situs;
			this.rumah = rumah;
			this.sampai = sampai;
			this.lomba = lomba;
			this.perahu = perahu;
		}
	}

	private static final Map<UUID, Sewa> SEWA = new HashMap<>();
	/** The stand anchor of each live town (situs -> anchor). */
	private static final Map<Long, DuniaAnchorEntity> STAND = new HashMap<>();
	private static boolean lastOk;
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(PerahuSewa::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEWA.clear();
			STAND.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ExperienceOrb orb && !SEWA.isEmpty()) {
				tangkapan(level, orb);
			}
		});
	}

	// ================================================================== the stand

	/** From the {@code pelabuhan/perahu_sewa} anchor: remembers where the boats are. */
	static void standDekat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, List<ServerPlayer> within) {
		STAND.put(kota.situs, anchor);
	}

	/** The rows of the hire counter (sheet {@code tema_kapal}, counter {@code perahu}). */
	static List<TokoPelabuhan.Jual> baris(ServerPlayer player, long situs) {
		List<TokoPelabuhan.Jual> out = new ArrayList<>();
		Component sudah = SEWA.containsKey(player.getUUID()) ? Component.translatable("kultivator.kota_tema.pelabuhan.perahu.sudah") : null;
		out.add(new TokoPelabuhan.Jual("sewa_1", TokoPelabuhan::ikonPerahu, HARGA_JAM, -1, -1, sudah));
		out.add(new TokoPelabuhan.Jual("sewa_3", TokoPelabuhan::ikonPerahu, HARGA_JAM * 3L, -1, -1, sudah));
		Component lomba = sudah;
		if (lomba == null && !HariDewiLaut.pendaftaran(situs)) {
			lomba = Component.translatable("kultivator.kota_tema.pelabuhan.perahu.lomba_tutup");
		}
		out.add(new TokoPelabuhan.Jual("lomba", () -> new ItemStack(TemaItems.JIMAT_OMBAK), HARGA_LOMBA, -1, -1, lomba));
		return out;
	}

	/** True when the last {@link #sewa} succeeded. */
	static boolean berhasil(Component ignored) {
		return lastOk;
	}

	/** Hires a boat ({@code sewa_1}, {@code sewa_3}, {@code lomba}); the result line. */
	static Component sewa(ServerPlayer player, long situs, String id, @Nullable KultivatorNpc npc) {
		lastOk = false;
		if (SEWA.containsKey(player.getUUID())) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.perahu.sudah");
		}
		boolean lomba = "lomba".equals(id);
		if (lomba && !HariDewiLaut.pendaftaran(situs)) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.perahu.lomba_tutup");
		}
		int hours = "sewa_3".equals(id) ? 3 : 1;
		long price = lomba ? HARGA_LOMBA : HARGA_JAM * hours;
		DuniaAnchorEntity stand = STAND.get(situs);
		if (!(player.level() instanceof ServerLevel level) || stand == null || stand.isRemoved()) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.perahu.tidak_ada");
		}
		Vec3 water = air(level, stand.blockPosition());
		if (water == null) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.perahu.tidak_ada");
		}
		if (!EkonomiApi.bayar(player, price, "pelabuhan.perahu")) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang");
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		PerahuNelayanEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_NELAYAN, water, stand.getYRot(), kota == null ? null : kota.kota,
				stand.blockPosition());
		if (boat == null) {
			EkonomiApi.beri(player, price, "pelabuhan.perahu.kembali");
			return Component.translatable("kultivator.kota_tema.pelabuhan.perahu.penuh");
		}
		boat.setVarian(lomba ? PerahuNelayanEntity.VARIAN_LOMBA : PerahuNelayanEntity.VARIAN_BIASA);
		long until = lomba ? level.getGameTime() + 6000L : level.getGameTime() + (long) JAM * hours;
		Sewa s = new Sewa(player.getUUID(), situs, Vec3.atCenterOf(stand.blockPosition()), until, lomba, boat);
		SEWA.put(player.getUUID(), s);
		EkonomiApi.kasKota(situs, price);
		player.startRiding(boat, true, true);
		TemaSibuk.tahan(player, TemaSibuk.PERAHU, (int) (until - level.getGameTime()) + 40);
		level.playSound(null, boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (lomba) {
			HariDewiLaut.daftarPeserta(level, situs, player, boat);
		}
		lastOk = true;
		return Component.translatable(lomba ? "kultivator.kota_tema.pelabuhan.perahu.lomba_ok" : "kultivator.kota_tema.pelabuhan.perahu.ok", hours);
	}

	/** Water at sea surface next to the stand (a free 3 x 7 patch), or null. */
	static @Nullable Vec3 air(ServerLevel level, BlockPos stand) {
		for (int r = 1; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos at = stand.offset(dx, 0, dz);
					Double surface = JadwalKapal.permukaanAir(level, at);
					if (surface != null && level.getBlockState(BlockPos.containing(at.getX(), surface + 0.5D, at.getZ())).isAir()) {
						return new Vec3(at.getX() + 0.5D, surface - 0.12D, at.getZ() + 0.5D);
					}
				}
			}
		}
		return null;
	}

	/** A right-click on a hired boat: only its renter boards it. */
	static boolean naik(ServerPlayer player, PerahuNelayanEntity boat) {
		Sewa s = SEWA.get(player.getUUID());
		if (s == null || s.perahu != boat) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.perahu.bukan").withStyle(ChatFormatting.GRAY));
			return false;
		}
		s.turunSejak = -1L;
		return player.startRiding(boat, true, true);
	}

	/** The renter got off (dismount or teleport). */
	static void turun(ServerPlayer player, PerahuNelayanEntity boat) {
		Sewa s = SEWA.get(player.getUUID());
		if (s != null && s.perahu == boat) {
			s.turunSejak = player.level().getGameTime();
		}
	}

	/** The rental of a player, or null. */
	static @Nullable PerahuNelayanEntity perahu(ServerPlayer player) {
		Sewa s = SEWA.get(player.getUUID());
		return s == null ? null : s.perahu;
	}

	/** Ends a rental (time over, towed back, the race over, left alone, tests). */
	static void akhiri(MinecraftServer server, UUID id, String why) {
		Sewa s = SEWA.remove(id);
		if (s == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) {
			if (player.getVehicle() == s.perahu) {
				player.stopRiding();
				player.teleportTo(s.rumah.x, s.rumah.y + 1.0D, s.rumah.z);
			}
			TemaSibuk.lepas(player, TemaSibuk.PERAHU);
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.perahu." + why).withStyle(ChatFormatting.GRAY));
		}
		TemaMekanisme.lepas(s.perahu);
	}

	private static void tick(MinecraftServer server) {
		if (SEWA.isEmpty() || server.getTickCount() % 20 != 3) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<UUID> end = new ArrayList<>();
		List<String> why = new ArrayList<>();
		for (Iterator<Sewa> it = SEWA.values().iterator(); it.hasNext(); ) {
			Sewa s = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(s.pemain);
			if (player == null || s.perahu == null || s.perahu.isRemoved()) {
				if (s.perahu != null) {
					TemaMekanisme.lepas(s.perahu);
				}
				it.remove();
				continue;
			}
			if (now >= s.sampai) {
				end.add(s.pemain);
				why.add("habis");
				continue;
			}
			double d = Math.sqrt(s.perahu.position().distanceToSqr(s.rumah.x, s.perahu.getY(), s.rumah.z));
			if (d > LEPAS) {
				end.add(s.pemain);
				why.add("ditarik");
				continue;
			}
			if (d > LEPAS_PERINGATAN && !s.diperingati) {
				s.diperingati = true;
				Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.perahu.jauh").withStyle(ChatFormatting.YELLOW));
			} else if (d < LEPAS_PERINGATAN - 20.0D) {
				s.diperingati = false;
			}
			if (player.getVehicle() != s.perahu) {
				if (s.turunSejak < 0L) {
					s.turunSejak = now;
				}
				if (now - s.turunSejak > DITINGGAL) {
					end.add(s.pemain);
					why.add("ditinggal");
				}
			} else {
				s.turunSejak = -1L;
				TemaSibuk.tahan(player, TemaSibuk.PERAHU, 60);
			}
		}
		for (int i = 0; i < end.size(); i++) {
			akhiri(server, end.get(i), why.get(i));
		}
	}

	/** A fishing catch from a hired boat: the orb appears at the fisher; 10 % of an Ikan Roh too. */
	private static void tangkapan(ServerLevel level, ExperienceOrb orb) {
		AABB box = orb.getBoundingBox().inflate(1.5D);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
			if (player.fishing == null || !(player.getVehicle() instanceof PerahuNelayanEntity) || !SEWA.containsKey(player.getUUID())) {
				continue;
			}
			if (level.getRandom().nextDouble() < PELUANG_IKAN_ROH) {
				PelabuhanKota.beri(player, new ItemStack(TemaItems.IKAN_ROH));
				Msg.actionBar(player, Component.translatable("kultivator.kota_tema.pelabuhan.perahu.ikan_roh").withStyle(ChatFormatting.AQUA));
				level.playSound(null, player.blockPosition(), SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.8F, 1.4F);
				CeritaApi.peristiwa(player, "tangkap", "kultivator:tema/ikan_roh");
			}
		}
	}

	private PerahuSewa() {
	}
}
