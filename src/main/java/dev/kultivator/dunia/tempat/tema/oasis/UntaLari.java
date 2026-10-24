package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Side quest {@code tema_oasis_unta_hilang} (DESIGN 38.T.4.7): Si Belang, the lead camel of Bulat Khan, bolted in the
 * last sandstorm. Effect {@code oasis_unta_kabur} (Ajun reads the tracks) fixes where it stands: 140-220 blocks from
 * the centre of the player's oasis, in a direction that depends only on the player and the town, so asking Ajun again
 * gives the same spot. The player hears the compass point and the distance.
 * <ul>
 * <li>Data first, body when near: the camel ({@link UntaKafilahEntity}, {@code noSave}, hired to the player so he can
 * ride it) appears only when the player comes within 96 blocks of the spot and its chunk is loaded. Until the player
 * has stood next to it, the hire's "left behind" timer is held, so it never wanders home before it is found.</li>
 * <li>Riding or walking it to within 48 blocks of the centre fires quest event {@code oasis_unta pulang}; the camel then
 * walks off to the caravanserai and goes ({@link UntaKafilahEntity#pulang}).</li>
 * <li>A body that is gone (logout, unloaded, killed) comes back at the spot the next time the player is near; memory only,
 * a restart forgets the spot until Ajun is asked again (same spot).</li>
 * </ul>
 * Nothing here changes a block.
 */
public final class UntaLari {

	public static final int JARAK_MIN = 140;
	public static final int JARAK_MAKS = 220;
	/** Horizontal distance at which the body appears. */
	private static final double MUNCUL = 96.0D;
	/** The player stood this close: the camel is found. */
	private static final double TEMU = 12.0D;
	/** Home: this close to the town centre. */
	private static final double PULANG = 48.0D;
	private static final long SEWA_TICK = 48_000L;

	private static final class Lari {
		final long situs;
		final BlockPos tuju;
		@Nullable UntaKafilahEntity unta;
		boolean ditemukan;

		Lari(long situs, BlockPos tuju) {
			this.situs = situs;
			this.tuju = tuju;
		}
	}

	private static final Map<UUID, Lari> LARI = new HashMap<>();

	/** Effect {@code oasis_unta_kabur}: Ajun reads the tracks; tells the player where Si Belang went. */
	static void kabur(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		OasisKota.Hidup kota = OasisKota.terdekat(level, player.position(), OasisKota.JANGKAU_KOTA);
		if (kota == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.kafilah.bukan_kota").withStyle(ChatFormatting.RED));
			return;
		}
		Lari lari = LARI.get(player.getUUID());
		if (lari == null || lari.situs != kota.situs) {
			long seed = player.getUUID().getLeastSignificantBits() ^ kota.situs * 0x9E3779B97F4A7C15L;
			double a = Math.floorMod(seed, 3600L) / 3600.0D * Math.PI * 2.0D;
			int d = JARAK_MIN + (int) Math.floorMod(seed >>> 16, (long) (JARAK_MAKS - JARAK_MIN + 1));
			BlockPos tuju = new BlockPos(kota.pusat.getX() + Mth.floor(Math.cos(a) * d), kota.pusat.getY(), kota.pusat.getZ() + Mth.floor(Math.sin(a) * d));
			Lari baru = new Lari(kota.situs, tuju);
			if (lari != null && lari.unta != null && !lari.unta.isRemoved()) {
				lari.unta.discard();
			}
			LARI.put(player.getUUID(), baru);
			lari = baru;
		}
		double dx = lari.tuju.getX() + 0.5D - player.getX();
		double dz = lari.tuju.getZ() + 0.5D - player.getZ();
		int jarak = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.unta.jejak", Component.translatable(arah(dx, dz)), jarak)
				.withStyle(ChatFormatting.GOLD));
	}

	/** Lang key of the compass point toward (dx, dz) (north = -z): {@code arah.0} east, counter-clockwise in steps of 45. */
	static String arah(double dx, double dz) {
		double deg = Math.toDegrees(Math.atan2(-dz, dx));
		int i = Math.floorMod((int) Math.round(deg / 45.0D), 8);
		return "kultivator.kota_tema.oasis.arah." + i;
	}

	/** True while the player has a runaway camel to find. */
	public static boolean dicari(ServerPlayer player) {
		return LARI.containsKey(player.getUUID());
	}

	/** Every 20 ticks from {@link Oasis}. */
	static void tick(MinecraftServer server) {
		if (LARI.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Lari>> it = LARI.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Lari> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			Lari lari = e.getValue();
			if (!QuestTracker.aktif(player, OasisCerita.QUEST_UNTA)) {
				if (lari.unta != null && !lari.unta.isRemoved()) {
					lari.unta.pulang();
				}
				it.remove();
				continue;
			}
			if (lari.unta != null && lari.unta.isRemoved()) {
				lari.unta = null;
			}
			if (lari.unta == null) {
				muncul(level, player, lari);
				continue;
			}
			UntaKafilahEntity unta = lari.unta;
			if (!lari.ditemukan) {
				if (unta.distanceToSqr(player) <= TEMU * TEMU) {
					lari.ditemukan = true;
					unta.lutut(true);
					unta.playSound(SoundEvents.CAMEL_AMBIENT, 1.0F, 0.9F);
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.unta.temu").withStyle(ChatFormatting.GOLD));
				} else {
					// hold the "left behind" timer until the player has found it
					unta.sewa(player, level.getGameTime() + SEWA_TICK);
					unta.setVarian(UntaKafilahEntity.VARIAN_BEBAN);
				}
			}
			OasisKota.Hidup kota = OasisKota.terdekat(level, unta.position(), PULANG + 16.0D);
			if (lari.ditemukan && kota != null && kota.situs == lari.situs && kota.jarak(unta.position()) <= PULANG) {
				if (player.getVehicle() == unta) {
					player.stopRiding();
				}
				unta.pulang();
				it.remove();
				Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.unta.pulang").withStyle(ChatFormatting.GOLD));
				QuestTracker.peristiwa(player, "oasis_unta", "pulang");
				dev.kultivator.util.Advancements.grant(player, "tempat/tema_oasis_si_belang");
			}
		}
	}

	private static void muncul(ServerLevel level, ServerPlayer player, Lari lari) {
		double dx = lari.tuju.getX() + 0.5D - player.getX();
		double dz = lari.tuju.getZ() + 0.5D - player.getZ();
		if (dx * dx + dz * dz > MUNCUL * MUNCUL || !level.hasChunk(lari.tuju.getX() >> 4, lari.tuju.getZ() >> 4)) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lari.tuju.getX(), lari.tuju.getZ());
		UntaKafilahEntity u = TemaEntities.UNTA_KAFILAH.create(level, EntitySpawnReason.EVENT);
		if (u == null) {
			return;
		}
		u.snapTo(lari.tuju.getX() + 0.5D, y, lari.tuju.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
		u.ikat(lari.situs);
		u.sewa(player, level.getGameTime() + SEWA_TICK);
		u.setVarian(UntaKafilahEntity.VARIAN_BEBAN);
		if (!level.addFreshEntity(u)) {
			return;
		}
		lari.unta = u;
		lari.ditemukan = false;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), u.getX(), u.getY() + 0.6D, u.getZ(), 24,
				0.8D, 0.4D, 0.8D, 0.05D);
		level.playSound(null, u.blockPosition(), SoundEvents.CAMEL_AMBIENT, SoundSource.NEUTRAL, 1.4F, 0.8F);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.unta.terlihat").withStyle(ChatFormatting.YELLOW));
	}

	/** Logout: the body goes, the spot is kept (the body comes back when the player is near again). */
	static void lupakan(UUID id) {
		Lari lari = LARI.get(id);
		if (lari != null && lari.unta != null && !lari.unta.isRemoved()) {
			lari.unta.discard();
			lari.unta = null;
		}
	}

	static void reset() {
		LARI.clear();
	}

	private UntaLari() {
	}
}
