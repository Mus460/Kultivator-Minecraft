package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatConfig;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ferry crossings of wide rivers and lakes (DESIGN 38.C.6.2, {@code pos/penyeberangan}): where a road meets more than
 * 28 blocks of water, a dock stands on each bank. The dock anchor keeps one raft ({@link RakitEntity}) moored while a
 * player is near; using the raft pays the ferryman {@link #ONGKOS} NB ({@code NilaiBatu.pay}), the raft is poled out
 * for a 4-second scene and the passenger steps onto the opposite dock, found along the same road edge with the world
 * generator's noise ({@link #seberang}), so it is right even before that bank's chunks are loaded. Murid followers
 * follow by their own rules (no veto: the raft is open).
 */
public final class Penyeberangan {

	/** The fare (Nilai Batu, 38.C.6.3). */
	public static final long ONGKOS = 1L;

	private static final Map<Long, RakitEntity> RAKIT = new ConcurrentHashMap<>();

	/** Keeps a raft moored at the dock of {@code anchor} (called while players are near). */
	static void jaga(ServerLevel level, DuniaAnchorEntity anchor) {
		RakitEntity raft = RAKIT.get(anchor.site());
		if (raft != null && !raft.isRemoved()) {
			return;
		}
		BlockPos water = airDekat(level, anchor.blockPosition());
		if (water == null) {
			return;
		}
		RakitEntity made = JalanEntities.RAKIT.create(level, EntitySpawnReason.EVENT);
		if (made == null) {
			return;
		}
		BlockPos at = new BlockPos(water.getX(), level.getSeaLevel(), water.getZ());
		made.setPos(at.getX() + 0.5D, at.getY() - 0.05D, at.getZ() + 0.5D);
		made.tambat(at);
		level.addFreshEntity(made);
		RAKIT.put(anchor.site(), made);
	}

	/** The dock unloads: its raft goes too (bodies are never saved). */
	static void lepas(DuniaAnchorEntity anchor) {
		RakitEntity raft = RAKIT.remove(anchor.site());
		if (raft != null && !raft.isRemoved() && !raft.sibuk()) {
			raft.discard();
		}
	}

	/** Server stop. */
	static void reset() {
		RAKIT.clear();
	}

	/** A player uses the raft: pay, sit, start the crossing. False when nothing happened. */
	static boolean naik(ServerPlayer player, RakitEntity raft) {
		if (!TempatConfig.get().aktif || raft.sibuk() || !(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		BlockPos dock = raft.tambatan() != null ? raft.tambatan() : raft.blockPosition();
		Vec3[] route = seberang(level, dock);
		if (route == null) {
			Msg.actionBar(player, Component.translatable("kultivator.jalan.seberang.tidak_ada").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!player.isCreative() && !NilaiBatu.pay(player, ONGKOS)) {
			Msg.actionBar(player, Component.translatable("kultivator.jalan.seberang.ongkos", ONGKOS).withStyle(ChatFormatting.RED));
			return true;
		}
		if (!player.startRiding(raft, true, true)) {
			return false;
		}
		raft.mulai(route[0], route[1]);
		level.playSound(null, dock, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.6F, 1.3F);
		Msg.actionBar(player, Component.translatable("kultivator.jalan.seberang.berangkat").withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** The crossing ends: the passenger steps onto the far bank. */
	static void tiba(ServerPlayer player, ServerLevel level, Vec3 to) {
		int x = (int) Math.floor(to.x);
		int z = (int) Math.floor(to.z);
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.teleportTo(level, x + 0.5D, y, z + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
		Msg.actionBar(player, Component.translatable("kultivator.jalan.seberang.tiba").withStyle(ChatFormatting.AQUA));
	}

	/**
	 * The crossing of the dock at {@code dock}: {direction over the water, landing point on the far bank}, or null when
	 * no road crosses water here. Walks the nearest road edge from the dock's sample toward the water until land, then
	 * three samples further (onto the far dock's ramp).
	 */
	public static Vec3 @Nullable [] seberang(ServerLevel level, BlockPos dock) {
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		RencanaJalan.Sisi e = RencanaJalan.terdekat(state, dock.getX(), dock.getZ(), 40);
		if (e == null) {
			return null;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		double len = e.panjang();
		int n = (int) (len / 3.0D);
		int near = 0;
		double best = Double.MAX_VALUE;
		for (int k = 0; k <= n; k++) {
			int[] p = e.titikPada(k * 3.0D);
			double d = Math.hypot(p[0] - dock.getX(), p[1] - dock.getZ());
			if (d < best) {
				best = d;
				near = k;
			}
		}
		for (int dir = 1; dir >= -1; dir -= 2) {
			int k = near + dir;
			int wet = 0;
			while (k >= 0 && k <= n && wet < 1200) {
				int[] p = e.titikPada(k * 3.0D);
				boolean water = sumber.surface(p[0], p[1]) > sumber.floor(p[0], p[1]);
				if (!water) {
					break;
				}
				wet++;
				k += dir;
			}
			if (wet >= 3 && k >= 0 && k <= n) {
				int land = Math.max(0, Math.min(n, k + dir * 3));
				int[] from = e.titikPada(near * 3.0D);
				int[] to = e.titikPada(land * 3.0D);
				Vec3 arah = new Vec3(to[0] - from[0], 0.0D, to[1] - from[1]);
				return new Vec3[]{arah, new Vec3(to[0] + 0.5D, 0.0D, to[1] + 0.5D)};
			}
		}
		return null;
	}

	/** A water column next to the dock's far end (where the raft floats), searched in a 6-block square. */
	private static @Nullable BlockPos airDekat(ServerLevel level, BlockPos anchor) {
		int sea = level.getSeaLevel() - 1;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				BlockPos p = new BlockPos(anchor.getX() + dx, sea, anchor.getZ() + dz);
				if (!level.getFluidState(p).isEmpty() && level.getBlockState(p.above()).isAir()) {
					double d = dx * dx + dz * dz;
					if (d >= 4 && d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		return best;
	}

	private Penyeberangan() {
	}
}
