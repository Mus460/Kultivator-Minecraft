package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The repair crews of DESIGN 38.R.3: real NPC bodies ({@code HidupApi.tubuh}, inside the NPC cap) that walk to the
 * cluster, carry planks on the shoulder, hammer, plaster, dig and replant, while a foreman stands at the edge and points.
 * Bodies come only while a player is within 48 blocks of the cluster (the work goes on as data without them) and leave
 * when the job is done. Craftsmen per job: the place's crews (capital 3 x 3, town 3, village 2, compound 3, post 1),
 * three more when the bill was paid, at most 12. Every body's work spot is the free block beside its operation; a body
 * within reach of its spot counts as "working" for the job's rate.
 * <p>Clips (38.N.5.3, ids of {@code GerakId}): {@code perbaiki_palu} with a hammer, {@code perbaiki_plester} with a
 * trowel on brick and stone, {@code angkat_papan} carrying a plank to wood and roof work, {@code ganti_tanah} with a
 * spade, {@code tanam_ulang} with a seedling, {@code angkat_air} with a bucket against fire; the foreman {@code tunjuk}.
 */
public final class Tukang {

	/** Bodies come when a player is this close to the cluster. */
	public static final double JARAK_HADIR = 48.0D;
	public static final int MAKS_TUKANG = 12;
	/** A body within this distance of its work spot is working. */
	private static final double JANGKAU = 2.6D;

	private static final class Regu {
		final List<KultivatorNpc> tukang = new ArrayList<>();
		final Map<Integer, Long> tugas = new HashMap<>();
		@Nullable KultivatorNpc mandor;
		long suara;
		int muncul;
	}

	private static final Map<Long, Regu> REGU = new HashMap<>();
	/** Entity id -> cluster id of every crew body (foremen too), for the donation sheet. */
	private static final Map<Integer, Long> TUBUH = new HashMap<>();

	/** Bodies of {@code g} at their work spot now. */
	static int bekerja(Gugus g) {
		Regu r = REGU.get(g.id);
		if (r == null) {
			return 0;
		}
		int n = 0;
		for (KultivatorNpc npc : r.tukang) {
			Long target = r.tugas.get(npc.getId());
			if (npc.isAlive() && target != null && npc.home() != null && npc.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(npc.home())) <= JANGKAU) {
				n++;
			}
		}
		return n;
	}

	/** The cluster a crew body (or foreman) works for, or null. */
	static @Nullable Gugus gugusDari(MinecraftServer server, Entity entity) {
		Long id = TUBUH.get(entity.getId());
		return id == null ? null : PerbaikanData.get(server).gugus.get(id);
	}

	/** Every 10 ticks: bodies come, get work, animate. */
	static void tick(MinecraftServer server) {
		PerbaikanData data = PerbaikanData.get(server);
		for (Iterator<Map.Entry<Long, Regu>> it = REGU.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Regu> e = it.next();
			if (!data.gugus.containsKey(e.getKey())) {
				vanish(e.getValue());
				it.remove();
			}
		}
		for (Gugus g : data.gugus.values()) {
			if (!g.dilaporkan || g.tuntas()) {
				continue;
			}
			TempatRawat t = data.tempat.get(g.kunci);
			ServerLevel level = t == null ? null : server.getLevel(t.dimensi);
			if (level == null) {
				continue;
			}
			ServerPlayer near = dekat(level, g);
			Regu r = REGU.get(g.id);
			if (near == null) {
				if (r != null) {
					r.tukang.removeIf(npc -> !npc.isAlive());
				}
				continue;
			}
			if (r == null) {
				r = new Regu();
				REGU.put(g.id, r);
			}
			jalankan(level, t, g, r, near);
		}
	}

	private static @Nullable ServerPlayer dekat(ServerLevel level, Gugus g) {
		BlockPos c = g.kotak.getCenter();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().distSqr(c) <= JARAK_HADIR * JARAK_HADIR) {
				return p;
			}
		}
		return null;
	}

	private static void jalankan(ServerLevel level, TempatRawat t, Gugus g, Regu r, ServerPlayer near) {
		JenisTempat.Profil profil = t.profil();
		r.tukang.removeIf(npc -> {
			if (!npc.isAlive()) {
				TUBUH.remove(npc.getId());
				r.tugas.remove(npc.getId());
				return true;
			}
			return false;
		});
		if (r.mandor != null && !r.mandor.isAlive()) {
			TUBUH.remove(r.mandor.getId());
			r.mandor = null;
		}
		int want = Math.min(MAKS_TUKANG, profil.tukangDasar() + (g.sewa ? 3 : 0));
		want = Math.min(want, Math.max(1, g.sisa()));
		// one new body per call, so a crew walks in one by one instead of popping up at once
		if (r.tukang.size() < want) {
			BlockPos spot = pijakSekitar(level, g.kotak, r.muncul++);
			if (spot != null) {
				KultivatorNpc npc = HidupApi.tubuh(level, t.situsId, profil.tukang(), spot, level.getRandom().nextFloat() * 360.0F);
				if (npc != null) {
					npc.memory().putBoolean("hold", true);
					npc.memory().putBoolean("perbaikan", true);
					r.tukang.add(npc);
					TUBUH.put(npc.getId(), g.id);
				}
			}
		} else if (r.mandor == null) {
			BlockPos spot = pijakSekitar(level, g.kotak, r.muncul++ + 3);
			if (spot != null) {
				KultivatorNpc boss = HidupApi.tubuh(level, t.situsId, profil.mandor(), spot, 0.0F);
				if (boss != null) {
					boss.memory().putBoolean("hold", true);
					boss.memory().putBoolean("perbaikan", true);
					boss.setHome(spot);
					r.mandor = boss;
					TUBUH.put(boss.getId(), g.id);
				}
			}
		}
		List<Gugus.Op> open = new ArrayList<>();
		for (Gugus.Op op : g.ops) {
			if (op.terbuka()) {
				open.add(op);
			}
		}
		open.sort((a, b) -> Integer.compare(a.urut, b.urut));
		long tick = level.getServer().getTickCount();
		for (KultivatorNpc npc : r.tukang) {
			Long current = r.tugas.get(npc.getId());
			Gugus.Op op = current == null ? null : cari(open, current);
			if (op == null) {
				op = pilih(open, r, npc);
				if (op == null) {
					HidupApi.klip(npc, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0);
					continue;
				}
				r.tugas.put(npc.getId(), op.pos);
				BlockPos stand = berdiri(level, BlockPos.of(op.pos));
				npc.setHome(stand == null ? BlockPos.of(op.pos) : stand);
			}
			animasi(level, npc, op, tick);
		}
		if (r.mandor != null) {
			KultivatorNpc boss = r.mandor;
			BlockPos c = g.kotak.getCenter();
			boss.getLookControl().setLookAt(c.getX() + 0.5D, c.getY() + 0.5D, c.getZ() + 0.5D);
			if (tick % 160L < 10L) {
				HidupApi.klip(boss, GerakId.TUNJUK, 0, 60);
			}
			if (boss.distanceToSqr(near) < 8.0D * 8.0D && tick % 200L < 10L) {
				HidupApi.tatap(boss, near);
			}
		}
		if (tick >= r.suara && !r.tukang.isEmpty()) {
			r.suara = tick + 900L + level.getRandom().nextInt(600);
			KultivatorNpc speaker = r.tukang.get(level.getRandom().nextInt(r.tukang.size()));
			Voice.say(speaker, "tempat.repair_work", null);
		}
	}

	private static @Nullable Gugus.Op cari(List<Gugus.Op> open, long pos) {
		for (Gugus.Op op : open) {
			if (op.pos == pos) {
				return op;
			}
		}
		return null;
	}

	/** The first open operation (work order) nobody else holds, preferring one near the body. */
	private static @Nullable Gugus.Op pilih(List<Gugus.Op> open, Regu r, KultivatorNpc npc) {
		Gugus.Op best = null;
		double bestScore = Double.MAX_VALUE;
		int looked = 0;
		for (Gugus.Op op : open) {
			if (r.tugas.containsValue(op.pos)) {
				continue;
			}
			BlockPos p = BlockPos.of(op.pos);
			double score = looked * 6.0D + Math.sqrt(npc.blockPosition().distSqr(p));
			if (score < bestScore) {
				bestScore = score;
				best = op;
			}
			if (++looked >= 8) {
				break;
			}
		}
		return best;
	}

	private static void animasi(ServerLevel level, KultivatorNpc npc, Gugus.Op op, long tick) {
		BlockPos target = BlockPos.of(op.pos);
		BlockPos home = npc.home();
		boolean there = home != null && npc.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(home)) <= JANGKAU;
		String group = op.target.isAir() ? "buang" : NilaiBlok.kelompok(op.target);
		if (!there) {
			if (NilaiBlok.KAYU.equals(group)) {
				HidupApi.klip(npc, GerakId.ANGKAT_PAPAN, PropId.PAPAN_KAYU, 0);
			} else {
				HidupApi.klip(npc, GerakId.JALAN_BAWA, alat(op, group), 0);
			}
			return;
		}
		npc.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
		int clip;
		if (op.ditemukan.getBlock() instanceof BaseFireBlock) {
			clip = GerakId.ANGKAT_AIR;
		} else if (Banding.kategori(op.target, op.ditemukan) == Banding.K_TANAMAN) {
			clip = GerakId.TANAM_ULANG;
		} else if (NilaiBlok.TANAH.equals(group)) {
			clip = GerakId.GANTI_TANAH;
		} else if (NilaiBlok.BATA.equals(group) || NilaiBlok.BATU.equals(group)) {
			clip = GerakId.PERBAIKI_PLESTER;
		} else {
			clip = GerakId.PERBAIKI_PALU;
		}
		HidupApi.klip(npc, clip, alat(op, group), 0);
		if ((tick + npc.getId()) % 20L < 10L) {
			BlockState shown = op.target.isAir() ? op.ditemukan : op.target;
			if (!shown.isAir() && !(shown.getBlock() instanceof BaseFireBlock)) {
				SoundType sound = shown.getSoundType();
				level.playSound(null, target, sound.getHitSound(), SoundSource.NEUTRAL, 0.45F, sound.getPitch() * 0.85F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
						3, 0.25D, 0.25D, 0.25D, 0.05D);
			}
		}
	}

	private static int alat(Gugus.Op op, String group) {
		if (op.ditemukan.getBlock() instanceof BaseFireBlock) {
			return PropId.EMBER;
		}
		if (Banding.kategori(op.target, op.ditemukan) == Banding.K_TANAMAN) {
			return PropId.BIBIT;
		}
		return switch (group) {
			case NilaiBlok.TANAH -> PropId.SEKOP;
			case NilaiBlok.BATA, NilaiBlok.BATU -> PropId.SENDOK_PLESTER;
			default -> PropId.PALU;
		};
	}

	/** The free standing block beside {@code target} (or below it on the ground), else null. */
	static @Nullable BlockPos berdiri(ServerLevel level, BlockPos target) {
		for (int dy : new int[]{0, -1, 1, -2}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = target.relative(d).above(dy);
				if (bisaBerdiri(level, p)) {
					return p;
				}
			}
		}
		for (int r = 2; r <= 3; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = pijak(level, target.getX() + d.getStepX() * r, target.getZ() + d.getStepZ() * r, target.getY());
				if (p != null) {
					return p;
				}
			}
		}
		return null;
	}

	/** A standing spot on the ring around {@code box} (2 blocks out), the {@code n}-th try. */
	private static @Nullable BlockPos pijakSekitar(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box, int n) {
		int w = box.getXSpan() + 4;
		int d = box.getZSpan() + 4;
		int perimeter = Math.max(4, 2 * (w + d));
		for (int i = 0; i < 12; i++) {
			int k = Math.floorMod(n * 7 + i * 5, perimeter);
			int x;
			int z;
			if (k < w) {
				x = box.minX() - 2 + k;
				z = box.minZ() - 2;
			} else if (k < w + d) {
				x = box.maxX() + 2;
				z = box.minZ() - 2 + (k - w);
			} else if (k < 2 * w + d) {
				x = box.maxX() + 2 - (k - w - d);
				z = box.maxZ() + 2;
			} else {
				x = box.minX() - 2;
				z = box.maxZ() + 2 - (k - 2 * w - d);
			}
			BlockPos p = pijak(level, x, z, box.minY());
			if (p != null) {
				return p;
			}
		}
		return null;
	}

	/** The first standable block in column {@code (x, z)} from {@code nearY + 3} down to {@code nearY - 6} (never a roof search). */
	static @Nullable BlockPos pijak(ServerLevel level, int x, int z, int nearY) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		for (int y = nearY + 3; y >= nearY - 6; y--) {
			BlockPos p = new BlockPos(x, y, z);
			if (bisaBerdiri(level, p)) {
				return p;
			}
		}
		return null;
	}

	private static boolean bisaBerdiri(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockState below = level.getBlockState(feet.below());
		return below.isFaceSturdy(level, feet.below(), Direction.UP) && below.getFluidState().isEmpty()
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet).getFluidState().isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	/** The foreman (or a craftsman) asks nearby players to move a filled container out of the way (rule 3). */
	static void mintaPindah(ServerLevel level, TempatRawat t, Gugus g) {
		Regu r = REGU.get(g.id);
		KultivatorNpc speaker = r == null ? null : r.mandor != null ? r.mandor : r.tukang.isEmpty() ? null : r.tukang.get(0);
		BlockPos c = g.kotak.getCenter();
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().distSqr(c) <= 32.0D * 32.0D) {
				Component who = speaker == null ? Component.translatable(t.profil().mandor().titleKey()) : speaker.getName();
				Msg.chat(p, Component.translatable("kultivator.tempat_perbaikan.halangan", who).withColor(0xE8C78A));
			}
		}
		if (speaker != null) {
			HidupApi.klip(speaker, GerakId.TUNJUK, 0, 60);
		}
	}

	/** The job is over: the crew looks at its work a moment, then leaves (done) or leaves at once. */
	static void bubar(ServerLevel level, Gugus g, boolean selesai) {
		Regu r = REGU.remove(g.id);
		if (r == null) {
			return;
		}
		if (selesai) {
			for (KultivatorNpc npc : r.tukang) {
				if (npc.isAlive()) {
					HidupApi.klip(npc, GerakId.TERTAWA, 0, 40);
				}
			}
		}
		vanish(r);
	}

	/** Forgets a cluster's crew bookkeeping (the bodies leave on their own when nobody is near). */
	static void lupakan(Gugus g) {
		Regu r = REGU.remove(g.id);
		if (r != null) {
			vanish(r);
		}
	}

	private static void vanish(Regu r) {
		for (KultivatorNpc npc : r.tukang) {
			TUBUH.remove(npc.getId());
			if (npc.isAlive()) {
				npc.vanish();
			}
		}
		if (r.mandor != null) {
			TUBUH.remove(r.mandor.getId());
			if (r.mandor.isAlive()) {
				r.mandor.vanish();
			}
		}
		r.tukang.clear();
		r.mandor = null;
	}

	/** {jobs with bodies, bodies} for {@code uji tempat gugus}. */
	static int[] statistik() {
		int bodies = 0;
		for (Regu r : REGU.values()) {
			bodies += r.tukang.size() + (r.mandor == null ? 0 : 1);
		}
		return new int[]{REGU.size(), bodies};
	}

	static void lupa() {
		for (Regu r : REGU.values()) {
			r.tukang.clear();
		}
		REGU.clear();
		TUBUH.clear();
	}

	private Tukang() {
	}
}
