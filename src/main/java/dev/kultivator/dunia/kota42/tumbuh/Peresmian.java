package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.acara.BarongsaiEntity;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.perbaikan.JenisTempat;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two ceremonies of the growing town (DESIGN 42.9.4 "Upacara peresmian", 42.9.5 "upacara syukur"), both staged only
 * while a player is near (otherwise the event passes as data: the gossip and the mood already came from 42F), both without
 * a single block or lightning bolt: bodies, the lion costume of 42D, props, sounds and particles.
 * <ul>
 *   <li><b>Opening</b> ({@code KotaBus.KAVLING}, stage 5 done): the lion dance ({@link BarongsaiEntity} with two dancers inside
 *       and two drummers behind) comes up the street to the new house, the place's officer waits at the door with the
 *       big scissors and a red ribbon, makes a speech, and cuts it when the lion arrives; the firecrackers are the client's
 *       ({@code Petasan}, on the final {@code kota_kavling} stage). A player who paid for the building (the name on the
 *       sign) is handed the scissors instead and cuts it himself (Nama Baik +2).</li>
 *   <li><b>Thanksgiving</b> ({@link BangunKembali}'s project ended): the officer and two craftsmen of the place kneel on the
 *       square with three incense sticks ({@code sujud_dupa}), smoke rises, a gong, the thanks are said aloud.</li>
 * </ul>
 * Bodies are {@code HidupApi.tubuhStaf}, held and sent away in a puff when the ceremony ends. At most {@link #MAKS} scenes run
 * at once. State is memory only (a scene never survives a restart).
 */
public final class Peresmian {

	private static final int MAKS = 3;
	private static final double JARAK = 72.0D;
	private static final long RESMI_MAKS = 2400L;
	private static final long SYUKUR_LAMA = 1800L;
	private static final String L = "kultivator.kota42.tumbuh.";

	private static final class Sesi {
		final long situs;
		final boolean resmi;
		final long mulai;
		final Vec3 depan;
		final Direction arah;
		@Nullable BarongsaiEntity singa;
		@Nullable KultivatorNpc kepala;
		@Nullable KultivatorNpc ekor;
		@Nullable KultivatorNpc pejabat;
		final List<KultivatorNpc> pemusik = new ArrayList<>();
		final List<KultivatorNpc> hadirin = new ArrayList<>();
		long potong = -1L;
		long pidato = -1L;
		@Nullable UUID donor;
		long sepi;

		Sesi(long situs, boolean resmi, long mulai, Vec3 depan, Direction arah) {
			this.situs = situs;
			this.resmi = resmi;
			this.mulai = mulai;
			this.depan = depan;
			this.arah = arah;
		}
	}

	private static final List<Sesi> SESI = new ArrayList<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KotaBus.KAVLING.register((server, situs, kavling, tahap) -> {
			if (tahap == Kavling.SELESAI - 1) {
				resmi(server, situs, kavling);
			}
		});
	}

	static void lupakan() {
		for (Sesi s : SESI) {
			bubar(s);
		}
		SESI.clear();
	}

	/** True while a ceremony runs in {@code situs}. */
	static boolean berjalan(long situs) {
		for (Sesi s : SESI) {
			if (s.situs == situs) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== start

	private static void resmi(MinecraftServer server, long situs, int id) {
		if (!KotaConfig.tumbuh().aktif || SESI.size() >= MAKS) {
			return;
		}
		Kavling.Rinci r = Kavling.rinci(server, situs, id);
		ServerLevel level = server.overworld();
		if (r == null) {
			return;
		}
		Direction arah = r.info().hadap().getAxis().isHorizontal() ? r.info().hadap() : Direction.SOUTH;
		BlockPos c = r.kotak().getCenter();
		int jauh = (arah.getAxis() == Direction.Axis.X ? r.kotak().getXSpan() : r.kotak().getZSpan()) / 2 + 2;
		BlockPos depan = TempatApi.groundSpot(level, c.getX() + arah.getStepX() * jauh, c.getZ() + arah.getStepZ() * jauh, r.kotak().minY());
		if (depan == null) {
			depan = new BlockPos(c.getX() + arah.getStepX() * jauh, r.kotak().minY(), c.getZ() + arah.getStepZ() * jauh);
		}
		if (level.getNearestPlayer(depan.getX() + 0.5D, depan.getY(), depan.getZ() + 0.5D, JARAK, false) == null || berjalan(situs)) {
			return;
		}
		Sesi s = new Sesi(situs, true, level.getGameTime(), Vec3.atBottomCenterOf(depan), arah);
		UUID terbesar = null;
		long nb = 0L;
		for (Map.Entry<UUID, Long> e : r.dana().entrySet()) {
			if (e.getValue() > nb) {
				nb = e.getValue();
				terbesar = e.getKey();
			}
		}
		s.donor = r.papanNama().isEmpty() ? null : terbesar;
		SESI.add(s);
		BlockPos base = depan;
		// the lion comes from the street end, twelve blocks out, to the door
		Vec3 jauhTitik = s.depan.add(arah.getStepX() * 14.0D, 0.0D, arah.getStepZ() * 14.0D);
		Vec3 dekat = s.depan.add(arah.getStepX() * 3.0D, 0.0D, arah.getStepZ() * 3.0D);
		if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 5 <= DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
			BarongsaiEntity singa = KotaAcaraEntities.BARONGSAI.create(level, EntitySpawnReason.EVENT);
			if (singa != null) {
				singa.ikat(situs, base.asLong());
				singa.pindah(jauhTitik, yaw(dekat.subtract(jauhTitik)));
				singa.setVarian((int) Math.floorMod(situs, (long) BarongsaiEntity.VARIAN_JUMLAH));
				if (level.addFreshEntity(singa)) {
					DuniaBudget.track(KotaAcaraEntities.ANGGARAN, singa);
					singa.arak(List.of(jauhTitik, dekat), false);
					s.singa = singa;
				}
			}
		}
		BlockPos start = BlockPos.containing(jauhTitik);
		s.kepala = badan(level, situs, NpcRole.PENARI, start, 1);
		s.ekor = badan(level, situs, NpcRole.PENARI, start, 2);
		for (int i = 0; i < 2; i++) {
			KultivatorNpc m = badan(level, situs, NpcRole.PEMUSIK, start.offset(arah.getStepX() * 3, 0, arah.getStepZ() * 3), 10 + i);
			if (m != null) {
				s.pemusik.add(m);
			}
		}
		Vec3 sisi = new Vec3(-arah.getStepZ(), 0.0D, arah.getStepX());
		Vec3 tempatPejabat = s.depan.add(sisi.scale(1.6D)).add(arah.getStepX() * 1.2D, 0.0D, arah.getStepZ() * 1.2D);
		s.pejabat = badan(level, situs, peranPejabat(server, situs), BlockPos.containing(tempatPejabat), 20);
		if (s.pejabat != null) {
			s.pejabat.setYRot(yaw(s.depan.subtract(tempatPejabat)));
			s.pejabat.setYHeadRot(s.pejabat.getYRot());
		}
		level.playSound(null, base, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 2.0F, 1.0F);
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, r.rencana());
		Component nama = plan == null ? Component.translatable(L + "resmi.bangunan") : PekerjaanTumbuh.namaRencana(plan);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().distSqr(base) <= JARAK * JARAK) {
				Msg.actionBar(p, Component.translatable(L + "resmi.mulai", nama).withStyle(ChatFormatting.RED));
			}
		}
	}

	/** {@code BangunKembali} finished a project: thanks on the square. */
	static void syukur(MinecraftServer server, ServerLevel level, long situs) {
		if (!KotaConfig.tumbuh().aktif || SESI.size() >= MAKS || berjalan(situs)) {
			return;
		}
		BlockPos alun = BangunKembali.alun(level, situs);
		if (alun == null || level.getNearestPlayer(alun.getX() + 0.5D, alun.getY(), alun.getZ() + 0.5D, JARAK, false) == null) {
			return;
		}
		Sesi s = new Sesi(situs, false, level.getGameTime(), Vec3.atBottomCenterOf(alun), Direction.SOUTH);
		SESI.add(s);
		NpcRole[] peran = {peranPejabat(server, situs), peranTukang(server, situs), peranTukang(server, situs)};
		for (int i = 0; i < peran.length; i++) {
			BlockPos at = BlockPos.containing(s.depan.add((i - 1) * 1.6D, 0.0D, 0.0D));
			BlockPos tanah = TempatApi.groundSpot(level, at.getX(), at.getZ(), at.getY());
			KultivatorNpc npc = badan(level, situs, peran[i], tanah == null ? at : tanah, 30 + i);
			if (npc != null) {
				npc.setYRot(0.0F);
				npc.setYHeadRot(0.0F);
				s.hadirin.add(npc);
			}
		}
		level.playSound(null, alun, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.4F, 0.6F);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().distSqr(alun) <= JARAK * JARAK) {
				Msg.actionBar(p, Component.translatable(L + "syukur.mulai").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static NpcRole peranPejabat(MinecraftServer server, long situs) {
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		return site == null ? NpcRole.KEPALA_DESA : JenisTempat.of(site.kind()).mandor();
	}

	private static NpcRole peranTukang(MinecraftServer server, long situs) {
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		return TukangPendatang.peranDari(site == null ? null : site.kind());
	}

	private static @Nullable KultivatorNpc badan(ServerLevel level, long situs, NpcRole peran, BlockPos at, int urut) {
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, peran, at, 0.0F, (int) (situs * 31L + urut * 7919L + peran.ordinal()));
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
		}
		return npc;
	}

	// ================================================================== the scenes

	/** Every other tick (from {@link KotaTumbuh}). */
	static void tick(MinecraftServer server) {
		if (SESI.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Iterator<Sesi> it = SESI.iterator(); it.hasNext(); ) {
			Sesi s = it.next();
			try {
				boolean selesai = s.resmi ? tickResmi(level, s, now) : tickSyukur(level, s, now);
				if (selesai || level.getNearestPlayer(s.depan.x, s.depan.y, s.depan.z, JARAK + 24.0D, false) == null) {
					bubar(s);
					it.remove();
				}
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: ceremony at {} failed; it is dropped", s.situs, exception);
				bubar(s);
				it.remove();
			}
		}
	}

	private static boolean tickResmi(ServerLevel level, Sesi s, long now) {
		long t = now - s.mulai;
		BarongsaiEntity singa = s.singa;
		if (singa != null && !singa.isRemoved()) {
			singa.bawaPenari(s.kepala, s.ekor);
			Vec3 maju = new Vec3(-Math.sin(Math.toRadians(singa.getYRot())), 0.0D, Math.cos(Math.toRadians(singa.getYRot())));
			Vec3 sisi = new Vec3(maju.z, 0.0D, -maju.x);
			for (int i = 0; i < s.pemusik.size(); i++) {
				KultivatorNpc m = s.pemusik.get(i);
				if (m.isRemoved()) {
					continue;
				}
				Vec3 at = singa.position().add(maju.scale(-3.2D)).add(sisi.scale(i == 0 ? 0.9D : -0.9D));
				if (m.distanceToSqr(at) > 0.04D) {
					m.teleportTo(at.x, at.y, at.z);
				}
				m.setYRot(singa.getYRot());
				m.setYHeadRot(singa.getYRot());
				int klip = i == 0 ? KotaGerakId.TABUH_GENDERANG_KECIL : GerakId.GENDERANG;
				if (SosokApi.gerakSekarang(m) != klip) {
					SosokApi.gerakSinkron(m, klip, singa.mulai());
				}
			}
		}
		KultivatorNpc p = s.pejabat;
		boolean singaTiba = singa == null || singa.isRemoved() || !singa.berarak();
		if (p != null && !p.isRemoved()) {
			if (s.pidato < 0L && t > 140L) {
				s.pidato = now;
				p.say(Component.translatable(L + "resmi.pidato"), 200);
				SosokApi.gerak(p, GerakId.BICARA_TANGAN, 0, 120);
			}
			if (s.potong < 0L && singaTiba && (s.pidato >= 0L && now - s.pidato > 220L || t > RESMI_MAKS / 2L)) {
				s.potong = now;
				mulaiPotong(level, s, p);
			}
			if (s.potong >= 0L && now - s.potong == 60L) {
				level.playSound(null, BlockPos.containing(s.depan), SoundEvents.SHEEP_SHEAR, SoundSource.AMBIENT, 1.5F, 0.9F);
				level.playSound(null, BlockPos.containing(s.depan), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 2.0F, 1.0F);
				level.playSound(null, BlockPos.containing(s.depan), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.AMBIENT, 1.5F, 1.2F);
				p.say(Component.translatable(L + "resmi.potong"), 120);
				if (singa != null && !singa.isRemoved()) {
					singa.setKeadaan(BarongsaiEntity.KEADAAN_MELOMPAT);
				}
			}
		}
		// a short while after the cut, the troupe goes away
		return s.potong >= 0L && now - s.potong > 400L || t > RESMI_MAKS;
	}

	/** The officer takes the scissors, or hands them to the paying player standing near. */
	private static void mulaiPotong(ServerLevel level, Sesi s, KultivatorNpc p) {
		SosokApi.gerak(p, KotaGerakId.POTONG_PITA, 0, 120, KotaGerakId.PROP_GUNTING_BESAR, KotaGerakId.PROP_PITA_MERAH, PropId.TIDAK_ADA);
		ServerPlayer donor = s.donor == null ? null : level.getServer().getPlayerList().getPlayer(s.donor);
		if (donor != null && donor.level() == level && donor.position().distanceToSqr(s.depan) <= 14.0D * 14.0D) {
			Msg.actionBar(donor, Component.translatable(L + "resmi.pita_pemain").withStyle(ChatFormatting.GOLD));
			Msg.chat(donor, Component.translatable(L + "resmi.pita_ok", 2).withStyle(ChatFormatting.GOLD));
			PekerjaanTumbuh.namaBaik(donor, s.situs, 2);
		}
	}

	private static boolean tickSyukur(ServerLevel level, Sesi s, long now) {
		long t = now - s.mulai;
		for (int i = 0; i < s.hadirin.size(); i++) {
			KultivatorNpc npc = s.hadirin.get(i);
			if (npc.isRemoved()) {
				continue;
			}
			if (t % 100L == 0L) {
				SosokApi.gerak(npc, GerakId.SUJUD_DUPA, 0, 0, PropId.DUPA, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
			}
			if (t % 20L == 0L) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, npc.getX(), npc.getY() + 1.5D, npc.getZ(), 1, 0.05D, 0.02D, 0.05D, 0.01D);
			}
			if (t == 120L && i == 0) {
				npc.say(Component.translatable(L + "syukur.kata"), 160);
			}
		}
		if (t == 400L || t == 1000L) {
			level.playSound(null, BlockPos.containing(s.depan), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.2F, 0.5F);
		}
		return t > SYUKUR_LAMA;
	}

	private static void bubar(Sesi s) {
		if (s.singa != null && !s.singa.isRemoved()) {
			s.singa.discard();
		}
		List<KultivatorNpc> semua = new ArrayList<>(s.pemusik);
		semua.addAll(s.hadirin);
		semua.add(s.kepala);
		semua.add(s.ekor);
		semua.add(s.pejabat);
		for (KultivatorNpc npc : semua) {
			if (npc != null && !npc.isRemoved()) {
				npc.vanish();
			}
		}
	}

	private static float yaw(Vec3 d) {
		return (float) (Mth.atan2(-d.x, d.z) * (180.0D / Math.PI));
	}

	private Peresmian() {
	}
}
