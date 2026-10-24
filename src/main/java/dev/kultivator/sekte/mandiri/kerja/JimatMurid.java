package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.talisman.Inscribe;
import dev.kultivator.talisman.TalismanData;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JIMAT (activity 42, DESIGN 39.4.2, 39.6.8): the disciple writes talismans at a low desk (the pavilion, the hall's tables
 * or the courtyard; desk, paper and brush are the client's props).
 * <p>
 * <b>Materials</b> (the player's own rules of {@code Inscribe}, from the bag first, then the Lumbung items of the policy's
 * allowance): a Blank Talisman, or three paper, a Spirit Grass and red dye that make three blanks by the blank's own
 * recipe, plus the kind's catalyst (copper ingot Thunder, blaze powder Fire Cloud, gold ingot Shield, a chain Binding,
 * an ender pearl Return, a lightning rod Tribulation Ward, sugar Thousand Mile, a fermented spider eye Limpidity), and the
 * kind's qi from the disciple's own qi. Which kind: what the life path carries first (Ahli Formasi the Ward and the
 * Binding, Petualang the Thousand Mile and the Return, Penjaga the Shield...).
 * <p>
 * <b>Writing.</b> One talisman takes {@link #TULIS} ticks of brushwork at the desk (the scratch of the brush, a page now
 * and then, motes of qi sinking into the paper); up to {@link #MAKS_PER_BLOK} in one block while materials last. The
 * chance is {@code Inscribe.chance(level)} with the disciple's own skill (+4 xp a success, +1 a failure, the player's
 * curve), power {@code min(9, realm)}; a failure leaves ash (the old effect). A success is
 * {@code Talismans.create(kind, power, its name)} with {@code kultivator:buatan_murid}, into the bag (else the Lumbung).
 */
final class JimatMurid implements Pelaksana {

	/** Ticks of brushwork per talisman. */
	static final int TULIS = 300;
	static final int MAKS_PER_BLOK = 3;

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		DiscipleData data = murid.data();
		return konteks.tempat() != null && data.realm().atLeast(Realm.QI_REFINING) && rencana(murid) != null;
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		murid.setActivity(Kegiatan.JIMAT.act(), konteks.tempat());
		siapkan(murid);
	}

	/** Takes the materials of the next talisman into the block record; false when there are none (or no qi). */
	private static boolean siapkan(DiscipleEntity murid) {
		TalismanKind kind = rencana(murid);
		if (kind == null) {
			return false;
		}
		buatKertas(murid);
		List<Bahan.Ambil> kertas = Bahan.ambil(murid, Talismans.BLANK, 1);
		if (kertas.isEmpty()) {
			return false;
		}
		Item katalis = katalis(murid, kind);
		List<Bahan.Ambil> taken = new ArrayList<>(kertas);
		List<Bahan.Ambil> kat = katalis == null ? List.of() : Bahan.ambil(murid, katalis, 1);
		if (kat.isEmpty()) {
			Bahan.kembalikan(murid, taken);
			return false;
		}
		taken.addAll(kat);
		DiscipleData data = murid.data();
		CompoundTag rec = DataKerja.anak(DataKerja.tulis(data), DataKerja.JIMAT_BAHAN);
		rec.putString("jenis", kind.testId());
		rec.putInt("tulis", 0);
		Bahan.simpanRekam(murid, rec, taken);
		return true;
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		if (murid.activity() != Kegiatan.JIMAT.act() || !(murid.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos spot = konteks.tempat();
		if (spot == null || !murid.getNavigation().isDone() || murid.distanceToSqr(Vec3.atBottomCenterOf(spot)) > 2.5D * 2.5D) {
			return true;
		}
		DiscipleData data = murid.data();
		CompoundTag own = DataKerja.tulis(data);
		CompoundTag rec = own.getCompoundOrEmpty(DataKerja.JIMAT_BAHAN);
		if (rec.isEmpty()) {
			// Materials ran out after a talisman or more: the disciple tidies the desk until the block ends.
			return own.getIntOr("jimatBlok", 0) > 0;
		}
		TalismanKind kind = TalismanKind.byId(rec.getStringOr("jenis", ""));
		int tulis = rec.getIntOr("tulis", 0) + 20;
		DataKerja.anak(own, DataKerja.JIMAT_BAHAN).putInt("tulis", tulis);
		Vec3 desk = meja(murid);
		murid.getLookControl().setLookAt(desk.x, desk.y, desk.z);
		RandomSource random = murid.getRandom();
		int beat = tulis / 20;
		level.sendParticles(ParticleTypes.ENCHANT, desk.x, desk.y + 0.6D, desk.z, 2, 0.2D, 0.2D, 0.2D, 0.4D);
		if (beat % 3 == 0) {
			level.playSound(null, desk.x, desk.y, desk.z, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.NEUTRAL, 0.35F,
					1.2F + random.nextFloat() * 0.2F);
		} else if (beat % 7 == 0) {
			level.playSound(null, desk.x, desk.y, desk.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
		if (tulis >= TULIS && kind != null) {
			own.remove(DataKerja.JIMAT_BAHAN);
			tulis(murid, level, kind);
			int dibuat = own.getIntOr("jimatBlok", 0) + 1;
			own.putInt("jimatBlok", dibuat);
			if (dibuat < MAKS_PER_BLOK) {
				siapkan(murid);
			}
		}
		return true;
	}

	/** The end of one talisman: the roll, the talisman or the ash, the skill, the diary. */
	private static void tulis(DiscipleEntity murid, ServerLevel level, TalismanKind kind) {
		DiscipleData data = murid.data();
		int levelSebelum = DataKerja.levelJimat(data);
		boolean success = murid.getRandom().nextDouble() < Inscribe.chance(levelSebelum);
		Vec3 desk = meja(murid).add(0.0D, 0.5D, 0.0D);
		data.setQi(Math.max(0.0D, data.qi() - kind.inscribeQi()));
		if (success) {
			int power = Math.min(TalismanData.MAX_POWER, data.realm().ordinal());
			ItemStack talisman = Talismans.create(kind, power, murid.getName().getString());
			KerjaKomponen.tandaiBuatan(talisman, murid);
			boolean keTas = Bahan.simpan(murid, talisman);
			DataKerja.tambahJimatXp(data, Inscribe.XP_SUCCESS, true);
			MandiriApi.maksStatistik(data, MandiriIds.S_KARYA, Math.min(6, 1 + power / 2));
			Fx.send(level, desk, FxType.TALISMAN, TalismanFxIds.INSCRIBED, kind.ordinal(), 0.0F);
			level.playSound(null, desk.x, desk.y, desk.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.7F, 1.3F);
			level.playSound(null, desk.x, desk.y, desk.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.5F);
			KabarKerja.catat(murid, "jimat", "jimat.berhasil", EntriHarian.Suasana.SENANG, false, "item.kultivator." + kind.itemId(),
					String.valueOf(power), keTas ? DataKerja.P + "tempat.tas" : DataKerja.P + "tempat.lumbung");
		} else {
			DataKerja.tambahJimatXp(data, Inscribe.XP_FAILURE, false);
			Fx.send(level, desk, FxType.TALISMAN, TalismanFxIds.ASH, kind.ordinal(), 0.0F);
			level.playSound(null, desk.x, desk.y, desk.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.1F);
			KabarKerja.catat(murid, "jimat", "jimat.gagal", EntriHarian.Suasana.KESAL, false, "item.kultivator." + kind.itemId());
		}
		int levelSesudah = DataKerja.levelJimat(data);
		if (levelSesudah > levelSebelum) {
			KabarKerja.catat(murid, "jimat", "jimat.naik", EntriHarian.Suasana.BANGGA, levelSesudah >= 5, String.valueOf(levelSesudah),
					String.valueOf(Math.round(Inscribe.chance(levelSesudah) * 100.0D)));
			KabarKerja.kabar(murid, "jimat_naik", levelSesudah >= 5, murid.getName().getString(), String.valueOf(levelSesudah));
		}
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		if (murid.activity() == Kegiatan.JIMAT.act()) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		CompoundTag own = DataKerja.tulis(murid.data());
		CompoundTag rec = own.getCompoundOrEmpty(DataKerja.JIMAT_BAHAN);
		own.remove(DataKerja.JIMAT_BAHAN);
		own.remove("jimatBlok");
		if (!rec.isEmpty()) {
			// The talisman on the desk was not finished: blank and catalyst go back.
			Bahan.kembalikan(murid, Bahan.bacaRekam(murid, rec));
		}
	}

	// ================================================================== the plan

	/** The kind the disciple would write now (materials and qi in hand), or null. */
	static @Nullable TalismanKind rencana(DiscipleEntity murid) {
		DiscipleData data = murid.data();
		Bahan.Stok stok = Bahan.stok(murid, JimatMurid::bahanJimat);
		int kertas = stok.ada(Talismans.BLANK);
		boolean bisaKertas = stok.ada(Items.PAPER) >= 3 && stok.ada(HartaItems.SPIRIT_GRASS) >= 1 && stok.ada(Items.DYE.red()) >= 1;
		if (kertas <= 0 && !bisaKertas) {
			return null;
		}
		for (TalismanKind kind : urutan(MandiriApi.jalan(data))) {
			if (data.qi() + 1.0E-6D < kind.inscribeQi()) {
				continue;
			}
			for (Map.Entry<Item, Integer> entry : semua(stok).entrySet()) {
				if (entry.getValue() > 0 && kind.isCatalyst(new ItemStack(entry.getKey()))) {
					return kind;
				}
			}
		}
		return null;
	}

	/** The catalyst item of {@code kind} the stock holds, or null. */
	private static @Nullable Item katalis(DiscipleEntity murid, TalismanKind kind) {
		Bahan.Stok stok = Bahan.stok(murid, JimatMurid::bahanJimat);
		for (Map.Entry<Item, Integer> entry : semua(stok).entrySet()) {
			if (entry.getValue() > 0 && kind.isCatalyst(new ItemStack(entry.getKey()))) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static Map<Item, Integer> semua(Bahan.Stok stok) {
		Map<Item, Integer> all = new LinkedHashMap<>(stok.tas());
		stok.lumbung().forEach((item, n) -> all.merge(item, n, Integer::sum));
		return all;
	}

	/** Paper sets into blanks by the blank's own recipe (3 paper, 1 Spirit Grass, 1 red dye -> 3 blanks), only when needed. */
	private static void buatKertas(DiscipleEntity murid) {
		Bahan.Stok stok = Bahan.stok(murid, JimatMurid::bahanJimat);
		if (stok.ada(Talismans.BLANK) > 0) {
			return;
		}
		Map<Item, Integer> need = new LinkedHashMap<>();
		need.put(Items.PAPER, 3);
		need.put(HartaItems.SPIRIT_GRASS, 1);
		need.put(Items.DYE.red(), 1);
		if (!stok.cukup(need) || Bahan.ambilSemua(murid, need).isEmpty()) {
			return;
		}
		Bahan.simpan(murid, new ItemStack(Talismans.BLANK, 3));
	}

	/** Items of the talisman craft (for the Lumbung allowance reads). */
	private static boolean bahanJimat(Item item) {
		if (item == Talismans.BLANK || item == Items.PAPER || item == Items.DYE.red() || item == HartaItems.SPIRIT_GRASS) {
			return true;
		}
		ItemStack probe = new ItemStack(item);
		return TalismanKind.byCatalyst(probe) != null;
	}

	/** The kinds in the order the life path prefers them (every kind follows). */
	static List<TalismanKind> urutan(@Nullable JalanHidup jalan) {
		List<TalismanKind> out = new ArrayList<>();
		if (jalan != null) {
			switch (jalan) {
				case FORMASI -> out.addAll(List.of(TalismanKind.TRIBULATION_WARD, TalismanKind.BINDING, TalismanKind.LIMPIDITY));
				case PETUALANG -> out.addAll(List.of(TalismanKind.THOUSAND_MILE, TalismanKind.RETURN, TalismanKind.SHIELD));
				case PENJAGA -> out.addAll(List.of(TalismanKind.SHIELD, TalismanKind.BINDING, TalismanKind.THUNDER));
				case PEDANG, PEMBURU -> out.addAll(List.of(TalismanKind.THUNDER, TalismanKind.FIRE_CLOUD, TalismanKind.SHIELD));
				case PERTAPA, CENDEKIA -> out.addAll(List.of(TalismanKind.LIMPIDITY, TalismanKind.TRIBULATION_WARD));
				default -> out.addAll(List.of(TalismanKind.SHIELD, TalismanKind.THOUSAND_MILE, TalismanKind.RETURN));
			}
		}
		for (TalismanKind kind : TalismanKind.values()) {
			if (!out.contains(kind)) {
				out.add(kind);
			}
		}
		return out;
	}

	/** The low desk in front of the seated disciple. */
	static Vec3 meja(DiscipleEntity murid) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, murid.getYRot());
		return murid.position().add(forward.x * 0.6D, 0.3D, forward.z * 0.6D);
	}
}
