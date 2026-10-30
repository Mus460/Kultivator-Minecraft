package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kolam Roh / Gua Meditasi of DESIGN 41.6.6 (owner 41C-K): the existing {@code meditasi} titik of the compound's
 * {@code kolam_roh} (x8) and {@code gua_meditasi} (x6) lots become bookable seats. A session lasts 30 real minutes
 * ({@link #SESI_TICK}) and costs {@value Kediaman#KOLAM_HARGA} PK (Inti and above: one free session a day); while it runs
 * and the member sits within {@link #DEKAT} blocks of the booked seat, {@link SitusGabung} offers the factor x1.30
 * ({@code gabung_kolam}). One member per seat, so the number of users is the number of titik.
 * <p>
 * Data: {@code Keanggotaan.bagian("kediaman")} keeps {@code kolam} (packed seat), {@code kolamSampai} (game time the session
 * ends) and {@code kolamGratis} (the world day the free session was used). Seats are read from the loaded pieces of the
 * compound the player stands in ({@code TempatApi.titik}), cached per place for {@link #CACHE_TICK} ticks. Nothing changes
 * in the world: the pool, the cushions and the steam are the lot's own blocks.
 */
public final class KolamRoh {

	/** Titik type of a meditation seat (tools/tempat_lib.py). */
	public static final String JENIS_TITIK = "meditasi";
	/** A session: 30 real minutes. */
	public static final int SESI_TICK = 30 * 60 * 20;
	/** The factor acts within this many blocks of the booked seat. */
	public static final double DEKAT = 2.5D;
	/** The seat list of a place is read again after this many ticks. */
	public static final int CACHE_TICK = 600;

	/**
	 * One meditation seat of the compound (screen tab Kolam, 41.10.7): id ({@code x,y,z}), position, 1-based number, taken,
	 * who sits there (a fellow member's name), the player's own booking.
	 */
	public record Kolam(String id, BlockPos pos, int nomor, boolean terisi, @Nullable Component pemakai, boolean milikmu) {
	}

	private record Cache(long time, List<BlockPos> seats) {
	}

	private static final Map<Long, Cache> CACHE = new HashMap<>();
	private static final String KOLAM = "kolam";
	private static final String SAMPAI = "kolamSampai";
	private static final String GRATIS = "kolamGratis";
	private static final int WARNA = 0x9FE0E8;

	private KolamRoh() {
	}

	/** Forgets the seat caches (server stop). */
	static void reset() {
		CACHE.clear();
	}

	// ================================================================== reads

	/** The meditation seats of the compound of the player's own sect he stands in (empty elsewhere or for a non-member). */
	public static List<Kolam> daftar(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		ServerLevel level = player.level();
		if (sekte == null || level.dimension() != Level.OVERWORLD) {
			return List.of();
		}
		var kompleks = KompleksApi.kompleksDi(level, player.blockPosition());
		if (kompleks == null || !sekte.equals(kompleks.sekte())) {
			return List.of();
		}
		TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
		if (info == null) {
			return List.of();
		}
		List<BlockPos> seats = seats(level, info.situsId());
		Map<Long, UUID> taken = terisi(level.getServer(), sekte, level.getGameTime());
		BlockPos own = sesi(player);
		List<Kolam> out = new ArrayList<>(seats.size());
		for (int i = 0; i < seats.size(); i++) {
			BlockPos pos = seats.get(i);
			UUID who = taken.get(pos.asLong());
			boolean mine = own != null && own.equals(pos);
			Component name = null;
			if (who != null && !mine) {
				ServerPlayer other = level.getServer().getPlayerList().getPlayer(who);
				name = other != null ? other.getDisplayName() : Component.translatable("kultivator.gabung.kediaman.anggota_lain");
			}
			out.add(new Kolam(id(pos), pos, i + 1, who != null, name, mine));
		}
		return out;
	}

	/** True when the player may book a free session today (Inti and above, not used yet today). */
	public static boolean gratis(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (k.sekteAktif() == null || !k.pangkat.minimal(Pangkat.INTI)) {
			return false;
		}
		return k.bagian(Kediaman.BAGIAN).getLongOr(GRATIS, -1L) != GabungApi.hari(player.level().getServer());
	}

	/** The seat of the player's running session, or null. */
	public static @Nullable BlockPos sesi(ServerPlayer player) {
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k == null || k.sekteAktif() == null) {
			return null;
		}
		CompoundTag t = k.bagian(Kediaman.BAGIAN);
		if (t.getLongOr(SAMPAI, 0L) <= player.level().getGameTime() || !t.contains(KOLAM)) {
			return null;
		}
		return BlockPos.of(t.getLongOr(KOLAM, 0L));
	}

	/** Ticks left of the running session (0 for none). */
	public static long sisa(ServerPlayer player) {
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k == null) {
			return 0L;
		}
		return Math.max(0L, k.bagian(Kediaman.BAGIAN).getLongOr(SAMPAI, 0L) - player.level().getGameTime());
	}

	/** True when {@code pos} lies within {@code radius} blocks of a meditation seat of the compound it stands in (a ward at the pool, 41.8.7). */
	public static boolean dekatKolam(ServerLevel level, BlockPos pos, double radius) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info == null) {
			return false;
		}
		for (BlockPos seat : seats(level, info.situsId())) {
			if (seat.distSqr(pos) <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	/** True while the session runs and the player sits at its seat (the {@code gabung_kolam} factor of {@link SitusGabung}). */
	public static boolean aktifDi(ServerPlayer player) {
		BlockPos seat = sesi(player);
		return seat != null && player.level().dimension() == Level.OVERWORLD
				&& player.position().distanceToSqr(seat.getX() + 0.5D, seat.getY(), seat.getZ() + 0.5D) <= DEKAT * DEKAT;
	}

	/** Once a second: motes of Qi gather round a member sitting out a booked session (D-41C), so the pool looks used. */
	static void suasana(net.minecraft.server.MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!aktifDi(p)) {
				continue;
			}
			ServerLevel level = p.level();
			level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 0.4D, p.getZ(), 10, 0.5D, 0.2D, 0.5D, 0.3D);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.2D, p.getZ(), 1, 0.4D, 0.0D, 0.4D, 0.02D);
		}
	}

	// ================================================================== booking

	/** Books seat {@code id} ({@code sesi:TITIK}) for one session. Null when booked, else the reason. */
	public static @Nullable Component pesan(ServerPlayer player, String id) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.kediaman.bukan_anggota");
		}
		Kolam target = null;
		List<Kolam> list = daftar(player);
		if (list.isEmpty()) {
			return Component.translatable("kultivator.gabung.kolam.di_kompleks");
		}
		for (Kolam kolam : list) {
			if (kolam.id().equals(id)) {
				target = kolam;
			}
		}
		if (target == null) {
			return Component.translatable("kultivator.gabung.kolam.tidak_ada");
		}
		if (target.milikmu()) {
			return Component.translatable("kultivator.gabung.kolam.sudah", target.nomor(), sisa(player) / 1200L);
		}
		if (target.terisi()) {
			return Component.translatable("kultivator.gabung.kolam.terisi", target.nomor(), target.pemakai());
		}
		MinecraftServer server = player.level().getServer();
		long hari = GabungApi.hari(server);
		CompoundTag t = k.bagian(Kediaman.BAGIAN);
		boolean free = gratis(player);
		if (free) {
			t.putLong(GRATIS, hari);
		} else if (!GabungApi.bayarPk(player, Kediaman.KOLAM_HARGA, "gabung.kolam")) {
			return Component.translatable("kultivator.gabung.kediaman.kurang_pk", Kediaman.KOLAM_HARGA, k.pk);
		}
		t.putLong(KOLAM, target.pos().asLong());
		t.putLong(SAMPAI, player.level().getGameTime() + SESI_TICK);
		GabungData.get(server).setDirty();
		Msg.chat(player, Component.translatable(free ? "kultivator.gabung.kolam.pesan_gratis" : "kultivator.gabung.kolam.pesan_ok", target.nomor(),
				Kediaman.KOLAM_HARGA, Component.literal("×1.30")).withColor(WARNA));
		ServerLevel level = player.level();
		BlockPos p = target.pos();
		level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5D, p.getY() + 0.6D, p.getZ() + 0.5D, 12, 0.3D, 0.4D, 0.3D, 0.01D);
		level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.4F);
		return null;
	}

	/** The nearest free seat to the player (the command and the screen's quick button), or null. */
	public static @Nullable Kolam terdekatBebas(ServerPlayer player) {
		Kolam best = null;
		double bestD = Double.MAX_VALUE;
		for (Kolam kolam : daftar(player)) {
			if (kolam.terisi() || kolam.milikmu()) {
				continue;
			}
			double d = player.position().distanceToSqr(kolam.pos().getX() + 0.5D, kolam.pos().getY(), kolam.pos().getZ() + 0.5D);
			if (d < bestD) {
				bestD = d;
				best = kolam;
			}
		}
		return best;
	}

	/** The seat list in chat, with a click to book each free one (the command without the 41U screen). */
	static void ringkas(ServerPlayer player) {
		List<Kolam> list = daftar(player);
		if (list.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.gabung.kolam.di_kompleks").withStyle(ChatFormatting.GRAY));
			return;
		}
		Msg.chat(player, Component.translatable("kultivator.gabung.kolam.judul", list.size(),
				Component.translatable(gratis(player) ? "kultivator.gabung.kolam.gratis_ada" : "kultivator.gabung.kolam.gratis_tidak")).withColor(WARNA));
		for (Kolam kolam : list) {
			MutableComponent line = Component.translatable("kultivator.gabung.kolam.baris", kolam.nomor());
			if (kolam.milikmu()) {
				line.append(Component.translatable("kultivator.gabung.kolam.milikmu", sisa(player) / 1200L).withStyle(ChatFormatting.GREEN));
			} else if (kolam.terisi()) {
				line.append(Component.translatable("kultivator.gabung.kolam.dipakai", kolam.pemakai()).withStyle(ChatFormatting.DARK_GRAY));
			} else {
				line.append(Component.translatable("kultivator.gabung.kolam.pesan_klik").withStyle(style -> style.withColor(ChatFormatting.AQUA)
						.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung kediaman kolam " + kolam.id()))));
			}
			Msg.chat(player, line);
		}
	}

	// ================================================================== internals

	/** The id of a seat as the screen ops carry it ({@code x,y,z}). */
	public static String id(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static List<BlockPos> seats(ServerLevel level, long situsId) {
		long now = level.getGameTime();
		Cache c = CACHE.get(situsId);
		if (c != null && now - c.time() < CACHE_TICK && now >= c.time()) {
			return c.seats();
		}
		List<BlockPos> out = new ArrayList<>();
		for (Titik t : TempatApi.titik(level, situsId, JENIS_TITIK)) {
			if (!out.contains(t.pos())) {
				out.add(t.pos());
			}
		}
		out.sort((a, b) -> a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : a.getZ() != b.getZ()
				? Integer.compare(a.getZ(), b.getZ()) : Integer.compare(a.getY(), b.getY()));
		List<BlockPos> list = List.copyOf(out);
		CACHE.put(situsId, new Cache(now, list));
		return list;
	}

	/** Seats with a running session of a member of {@code sekte} (packed pos -&gt; member). */
	private static Map<Long, UUID> terisi(MinecraftServer server, String sekte, long now) {
		Map<Long, UUID> out = new HashMap<>();
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan other = data.peek(uuid);
			if (other == null) {
				continue;
			}
			CompoundTag t = other.bagian(Kediaman.BAGIAN);
			if (t.contains(KOLAM) && t.getLongOr(SAMPAI, 0L) > now) {
				out.put(t.getLongOr(KOLAM, 0L), uuid);
			}
		}
		return out;
	}
}
