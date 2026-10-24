package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.TempatNames;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Milestones (DESIGN 38.C.6.3, {@code pos/tonggak}): a carved stela every 500 blocks of road. Touching one (standing
 * within {@link #JARAK} blocks) discovers the milestone site, both end places of its road (their published sites within
 * {@link #JARAK_UJUNG} blocks) and the road itself: the player's known roads ({@code CultivationData.extra()} tag
 * {@code jalan}, list {@code sisi} of edge id + position pairs, at most {@link JalanNet#MAKS}) are sent to the client
 * and drawn on the world map. The stone reads "<- place 1.2 li / 600 b ->" (chat line; the carved board over the stone,
 * with arrows turned to the viewer and the stela's couplet, is {@link PapanTonggak}). Ten milestones grant {@code tempat/peziarah_jalan}; three grant {@code tempat/tonggak_tiga} (guide step {@code c19.jalan}).
 */
public final class Tonggak {

	public static final double JARAK = 4.0D;
	public static final int JARAK_UJUNG = 200;
	/** 1 li = 500 blocks (rancangan game; the Tang li was about 530 m). */
	public static final double LI = 500.0D;
	public static final String TAG = "jalan";

	/** A player stands at the milestone of {@code anchor}. */
	static void sentuh(ServerLevel level, DuniaAnchorEntity anchor, ServerPlayer player) {
		long site = anchor.siteOf("pos/tonggak");
		if (!DuniaSites.discover(player, site)) {
			return;
		}
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		BlockPos at = anchor.blockPosition();
		RencanaJalan.Sisi e = RencanaJalan.terdekat(state, at.getX(), at.getZ(), 64);
		level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
		if (e == null) {
			Msg.chat(player, Component.translatable("kultivator.jalan.tonggak.sunyi").withStyle(ChatFormatting.GRAY));
		} else {
			double along = posisi(e, at.getX(), at.getZ());
			double total = e.panjang();
			Msg.chat(player, Component.translatable("kultivator.jalan.tonggak.baca",
					namaUjung(level, e.dari()), li(along), Math.round(along),
					namaUjung(level, e.ke()), li(total - along), Math.round(total - along)).withStyle(ChatFormatting.GOLD));
			Msg.chat(player, Component.translatable("kultivator.jalan.tonggak.nama", TempatNames.jalan(e.id(), e.kelas()))
					.withStyle(ChatFormatting.GRAY));
			RencanaJalan.Pos next = null;
			int ownX = Math.floorDiv((e.xs()[0] + e.xs()[e.xs().length - 1]) / 2, RencanaJalan.SEL);
			int ownZ = Math.floorDiv((e.zs()[0] + e.zs()[e.zs().length - 1]) / 2, RencanaJalan.SEL);
			for (RencanaJalan.Pos p : RencanaJalan.posSel(state, ownX, ownZ)) {
				if (p.sisiId() == e.id() && (p.jenis().startsWith("singgah") || p.jenis().equals("dagang")) && p.jarak() > along
						&& (next == null || p.jarak() < next.jarak())) {
					next = p;
				}
			}
			if (next != null) {
				long postSite = DuniaSites.idOf(level.dimension(), new BlockPos(next.x(), 0, next.z()), "pos/" + next.jenis());
				Msg.chat(player, Component.translatable("kultivator.jalan.tonggak.singgah", TempatNames.pos(next.jenis(), postSite),
						li(next.jarak() - along)).withStyle(ChatFormatting.GRAY));
			}
			tambahSisi(player, e, at);
			for (SimpulJalan end : List.of(e.dari(), e.ke())) {
				for (DuniaSites.Site s : DuniaSites.near(level, new BlockPos(end.x(), at.getY(), end.z()), JARAK_UJUNG, "")) {
					if (!s.kind().startsWith("pos/")) {
						DuniaSites.discover(player, s.id());
						break;
					}
				}
			}
			kirim(player);
		}
		int known = 0;
		for (DuniaSites.Site s : DuniaSites.known(player)) {
			if ("pos/tonggak".equals(s.kind())) {
				known++;
			}
		}
		if (known >= 3) {
			Advancements.grant(player, "tempat/tonggak_tiga");
		}
		if (known >= 10) {
			Advancements.grant(player, "tempat/peziarah_jalan");
		}
	}

	/** How many milestones {@code player} has touched (guide step {@code c19.jalan}, needs 3). */
	public static int dikenal(ServerPlayer player) {
		int known = 0;
		for (DuniaSites.Site s : DuniaSites.known(player)) {
			if ("pos/tonggak".equals(s.kind())) {
				known++;
			}
		}
		return known;
	}

	/** Sends the player's known roads to his client (join, new milestone). */
	public static void kirim(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ChunkGeneratorStructureState state = RencanaJalan.of(level.getServer().overworld());
		long[] known = Cultivators.get(player).extra().getCompoundOrEmpty(TAG).getLongArray("sisi").orElse(new long[0]);
		List<JalanNet.Garis> out = new ArrayList<>();
		for (int i = 0; i + 1 < known.length && out.size() < JalanNet.MAKS; i += 2) {
			int x = (int) (known[i + 1] >> 32);
			int z = (int) known[i + 1];
			RencanaJalan.Sisi e = RencanaJalan.sisi(state, known[i], x, z);
			if (e != null) {
				out.add(new JalanNet.Garis(e.kelas(), e.xs(), e.zs()));
			}
		}
		JalanNet.send(player, out, denah(player));
	}

	/**
	 * The district boxes of every capital the player (or a sect mate) knows: a known {@code kota/kota_besar} site whose
	 * position lies in a planned capital (DESIGN 38.D.12, close-zoom plan on the map).
	 */
	static List<JalanNet.Denah> denah(ServerPlayer player) {
		List<JalanNet.Denah> out = new ArrayList<>();
		List<BlockPos> capitals = new ArrayList<>();
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (site.kind().equals("kota/kota_besar") && site.dim() == Level.OVERWORLD) {
				capitals.add(site.pos());
			}
		}
		if (capitals.isEmpty()) {
			return out;
		}
		long seed = player.level().getServer().overworld().getSeed();
		for (Kandidat k : RencanaKotaBesar.of(seed).kandidat()) {
			BoundingBox kota = RencanaKotaBesar.kotakKota(k);
			boolean known = false;
			for (BlockPos p : capitals) {
				if (p.getX() >= kota.minX() && p.getX() <= kota.maxX() && p.getZ() >= kota.minZ() && p.getZ() <= kota.maxZ()) {
					known = true;
					break;
				}
			}
			if (!known) {
				continue;
			}
			for (Distrik d : RencanaKotaBesar.distrikOf(k)) {
				if (out.size() >= JalanNet.MAKS_DENAH) {
					return out;
				}
				int h = RencanaKotaBesar.SETENGAH_DISTRIK;
				out.add(new JalanNet.Denah(d.id(), d.pusatX() - h, d.pusatZ() - h, d.pusatX() + h - 1, d.pusatZ() + h - 1));
			}
		}
		return out;
	}

	private static void tambahSisi(ServerPlayer player, RencanaJalan.Sisi e, BlockPos at) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = data.extra().getCompoundOrEmpty(TAG).copy();
		long[] old = tag.getLongArray("sisi").orElse(new long[0]);
		for (int i = 0; i + 1 < old.length; i += 2) {
			if (old[i] == e.id()) {
				return;
			}
		}
		int keep = Math.min(old.length, (JalanNet.MAKS - 1) * 2);
		long[] next = new long[keep + 2];
		System.arraycopy(old, old.length - keep, next, 0, keep);
		next[keep] = e.id();
		next[keep + 1] = ((long) at.getX() << 32) | (at.getZ() & 0xFFFFFFFFL);
		tag.putLongArray("sisi", next);
		data.extra().put(TAG, tag);
		Cultivators.dirty(player);
	}

	/** Distance along the polyline to the point nearest {@code (x, z)}. */
	static double posisi(RencanaJalan.Sisi e, int x, int z) {
		double walked = 0.0D;
		double bestD = Double.MAX_VALUE;
		double bestS = 0.0D;
		int[] xs = e.xs();
		int[] zs = e.zs();
		for (int i = 1; i < xs.length; i++) {
			double len = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
			double d = RencanaJalan.segDist2(x, z, xs[i - 1], zs[i - 1], xs[i], zs[i]);
			if (d < bestD) {
				bestD = d;
				double vx = xs[i] - xs[i - 1];
				double vz = zs[i] - zs[i - 1];
				double t = len < 1.0E-6D ? 0.0D : Math.clamp(((x - xs[i - 1]) * vx + (z - zs[i - 1]) * vz) / (len * len), 0.0D, 1.0D);
				bestS = walked + t * len;
			}
			walked += len;
		}
		return bestS;
	}

	private static String li(double blocks) {
		return String.format(java.util.Locale.ROOT, "%.1f", blocks / LI);
	}

	/** The name of a road end: the NPC sect's name at a gate, else the kind of place. */
	static Component namaUjung(ServerLevel level, SimpulJalan end) {
		if ("sekte".equals(end.jenis())) {
			GerbangPlan.Entry entry = GerbangPlan.of(level.getSeed()).entry((int) end.kunci());
			if (entry != null) {
				return entry.name();
			}
		}
		return Component.translatable("kultivator.jalan.ujung." + end.jenis());
	}

	private Tonggak() {
	}
}
