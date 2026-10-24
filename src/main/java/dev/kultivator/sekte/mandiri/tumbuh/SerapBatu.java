package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.fx.Fx;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Serap Batu, absorbing spirit stones (DESIGN 39.5.6; executor SERAP_BATU 54 of C39.B3): one block is K 3.0 instead of
 * meditation's 1.5 (the seated meditation XP x2 through {@link LajuMandiri}), costs {@code 4 x (realm + 1)} NB from the
 * purse (or one spirit stone from the bag worth at least that, the change in NB back to the purse, NB = the towns'
 * {@code NilaiBatu.VALUE}), at most two blocks a day. It adds about 15 % to a day's XP: a sink for money, not a shortcut.
 * Seen: the disciple sits with both hands open, stones of its realm's grade (Low for Qi Refining-Foundation, Mid for
 * Golden Core-Nascent Soul, High for Deity Transformation-Immortal Ascension, Supreme for True Immortal) circle and break
 * into light that sinks into its chest.
 */
public final class SerapBatu {

	public static final int NB_PER_RANAH = 4;
	public static final int MAKS_HARI = 2;
	/** Light of the four stone grades. */
	private static final int[] WARNA = {0x9FD3FF, 0x6FE0A8, 0xC08CFF, 0xFFD866};
	private static final String KEY = "serap";

	static void init() {
		MandiriApi.register(Kegiatan.SERAP_BATU, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				return boleh(murid);
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				mulaiDi(murid, konteks.tempat());
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				return murid.activity() == MandiriKegiatan.ACT_SERAP_BATU;
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				akhiri(murid, batal);
			}

			@Override
			public double K(DiscipleEntity murid, Konteks konteks) {
				return 3.0D;
			}
		});
	}

	/** NB of one block for {@code data}'s realm. */
	public static long biaya(DiscipleData data) {
		return NB_PER_RANAH * (data.realm().ordinal() + 1L);
	}

	/** The stone grade shown for the realm (0 Low ... 3 Supreme). */
	static int tingkat(Realm realm) {
		return switch (realm) {
			case MORTAL, QI_REFINING, FOUNDATION -> 0;
			case GOLDEN_CORE, NASCENT_SOUL -> 1;
			case DEITY_TRANSFORMATION, DAO_UNION, IMMORTAL_ASCENSION -> 2;
			case TRUE_IMMORTAL -> 3;
		};
	}

	/** Can pay, under two blocks today, a cultivator. */
	static boolean boleh(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (data.realm().isMortal() || data.breakingThrough() || hariIni(entity) >= MAKS_HARI) {
			return false;
		}
		long cost = biaya(data);
		return MandiriApi.dompet(data) >= cost || batuTas(entity, cost) >= 0;
	}

	private static int hariIni(DiscipleEntity entity) {
		CompoundTag record = DataTumbuh.baca(entity.data()).getCompoundOrEmpty(KEY);
		return record.getLongOr("hari", -1L) == DataTumbuh.hari(entity.level().getServer()) ? record.getIntOr("n", 0) : 0;
	}

	/** The grade of the cheapest stone in the bag worth at least {@code cost}, or -1. */
	private static int batuTas(DiscipleEntity entity, long cost) {
		int best = -1;
		for (ItemStack stack : MandiriApi.tas(entity.data(), entity.level().registryAccess())) {
			int grade = SpiritStones.grade(stack);
			if (grade >= 0 && NilaiBatu.VALUE[grade] >= cost && (best < 0 || grade < best)) {
				best = grade;
			}
		}
		return best;
	}

	/** Pays and sits down to absorb. */
	static boolean mulaiDi(DiscipleEntity entity, BlockPos saran) {
		if (!(entity.level() instanceof ServerLevel level) || !boleh(entity)) {
			return false;
		}
		DiscipleData data = entity.data();
		long cost = biaya(data);
		if (MandiriApi.dompet(data) >= cost) {
			MandiriApi.ubahDompet(data, -cost, "serap_batu");
		} else {
			int grade = batuTas(entity, cost);
			ItemStack stone = MandiriApi.ambilTas(data, level.registryAccess(), stack -> SpiritStones.grade(stack) == grade, 1);
			if (stone.isEmpty()) {
				return false;
			}
			long change = NilaiBatu.VALUE[grade] - cost;
			if (change > 0L) {
				MandiriApi.ubahDompet(data, change, "kembalian_batu");
			}
		}
		CompoundTag record = DataTumbuh.sub(data, KEY);
		long today = DataTumbuh.hari(level.getServer());
		if (record.getLongOr("hari", -1L) != today) {
			record.putLong("hari", today);
			record.putInt("n", 0);
		}
		record.putInt("n", record.getIntOr("n", 0) + 1);
		DataTumbuh.tambahStatistik(data, DataTumbuh.S_BATU, 1);
		BlockPos seat = TempatTumbuh.tenang(level, entity, saran);
		entity.setActivity(MandiriKegiatan.ACT_SERAP_BATU, seat == null ? entity.blockPosition() : seat);
		SekteSuara.line(entity, "serap_batu", "cultivate", 400);
		int color = WARNA[tingkat(data.realm())];
		Vec3 at = entity.position().add(0.0D, 1.0D, 0.0D);
		Fx.ring(level, at, color, 1.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 0.8F);
		KabarTumbuh.catat(entity, "serap", "serap.mulai", EntriHarian.Suasana.TENANG, false, "n:" + cost);
		return true;
	}

	/** Ends the block (the pose cleared). */
	static void akhiri(DiscipleEntity entity, boolean batal) {
		if (entity.activity() == MandiriKegiatan.ACT_SERAP_BATU) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			if (!batal && entity.level() instanceof ServerLevel level) {
				int color = WARNA[tingkat(entity.data().realm())];
				Fx.burst(level, entity.position().add(0.0D, 1.1D, 0.0D), color, 18, 0.9F);
				level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.6F, 1.3F);
			}
		}
	}

	/** Once a second while absorbing: the stones circle and break into light (every 4 s, a few quads). */
	static void tick20(DiscipleEntity entity) {
		if (entity.tickCount % 80 >= 20 || !entity.isMeditatingNow() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int color = WARNA[tingkat(entity.data().realm())];
		double angle = (entity.tickCount / 80) * 1.3D;
		Vec3 orbit = entity.position().add(Math.cos(angle) * 0.9D, 1.3D, Math.sin(angle) * 0.9D);
		Vec3 chest = entity.position().add(0.0D, 1.1D, 0.0D);
		Fx.beam(level, orbit, chest, color, 0.05F);
		Fx.burst(level, orbit, color, 4, 0.2F);
	}

	private SerapBatu() {
	}
}
