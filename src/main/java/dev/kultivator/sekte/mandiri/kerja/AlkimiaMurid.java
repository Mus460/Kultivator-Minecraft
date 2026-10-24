package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.AlchemyRecipes;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MERAMU (activity 41, DESIGN 39.4.2, 39.6.8): the disciple brews pills without the master and without a cauldron block.
 * <p>
 * <b>Where and with what.</b> At the kitchen post or the alchemy room of the hall (the point cache's DAPUR); a small
 * cauldron and a fan are the client's props. The ingredients come from its own bag first, then from the Lumbung for the
 * items the policy's allowance names ({@code jatahLumbung}); they are taken when the block starts and kept in the block's
 * record, and an interrupted brew that cooked less than {@link #MASAK} ticks gives them all back.
 * <p>
 * <b>Which pill.</b> Every recipe of {@code AlchemyRecipes} without a required beast core whose ingredients the stock
 * holds, within the disciple's skill ({@code xp <= 30 + 15 x level}); what it needs scores x3 (a healing pill while hurt,
 * a Heart Calming Pill while tempted, a Foundation Pill at Qi Refining, a Golden Core Pill at Foundation, a Tribulation
 * Warding Pill from Golden Core), the harder pills score higher (alchemists x1.5).
 * <p>
 * <b>The roll</b> (the old cauldron's rules): success {@code AlchemyFurnace.successChance(level, no soul fire, Fire Dao)},
 * level = {@code min(10, brewing hours / 40)}; quality {@code random + 0.03 x level} against 0.35 / 0.75 / 0.95 (the
 * private {@code rollQuality} thresholds of {@code AlchemyFurnace}, copied); Bakat Pil +25 % one grade up
 * ({@code Duties.PILL_GIFT_CHANCE}); a pill without quality gives one more at Perfect. A failure loses the ingredients like
 * the cauldron does. The pills carry {@code kultivator:buatan_murid} and go into the bag (else the Lumbung).
 * <p>
 * Visible while it brews: coloured steam in the recipe's colour and a curl of smoke every few seconds, the brewing bubble
 * and the fire's crackle; at the end the cauldron's own success or failure burst ({@code FxType.ALCHEMY}).
 */
final class AlkimiaMurid implements Pelaksana {

	/** Cooking time at the spot a brew needs to finish (ticks). */
	static final int MASAK = 600;
	/** {@code AlchemyFurnace.QUALITY_PER_LEVEL} (private there, copied). */
	static final double MUTU_PER_LEVEL = 0.03D;
	/** {@code AlchemyFurnace.rollQuality} thresholds (private there, copied). */
	static final double AMBANG_MID = 0.35D;
	static final double AMBANG_HIGH = 0.75D;
	static final double AMBANG_PERFECT = 0.95D;

	@Override
	public boolean bisa(DiscipleEntity murid, Konteks konteks) {
		DiscipleData data = murid.data();
		return konteks.tempat() != null && data.realm().atLeast(Realm.QI_REFINING) && murid.getHealth() > murid.getMaxHealth() * 0.4F
				&& resep(murid, Bahan.stok(murid, AlchemyRecipes::isIngredient)) != null;
	}

	@Override
	public void mulai(DiscipleEntity murid, Konteks konteks) {
		AlchemyRecipes.Recipe recipe = resep(murid, Bahan.stok(murid, AlchemyRecipes::isIngredient));
		if (recipe == null) {
			return;
		}
		List<Bahan.Ambil> taken = Bahan.ambilSemua(murid, recipe.ingredients());
		if (taken.isEmpty()) {
			return;
		}
		CompoundTag racik = new CompoundTag();
		racik.putString("resep", recipe.id());
		racik.putInt("masak", 0);
		Bahan.simpanRekam(murid, racik, taken);
		DataKerja.tulis(murid.data()).put(DataKerja.RACIK, racik);
		murid.setActivity(Kegiatan.MERAMU.act(), konteks.tempat());
	}

	@Override
	public boolean jalan(DiscipleEntity murid, Konteks konteks) {
		CompoundTag racik = DataKerja.baca(murid.data()).getCompoundOrEmpty(DataKerja.RACIK);
		if (murid.activity() != Kegiatan.MERAMU.act() || racik.isEmpty() || !(murid.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos spot = konteks.tempat();
		if (spot == null || !murid.getNavigation().isDone() || murid.distanceToSqr(Vec3.atBottomCenterOf(spot)) > 2.5D * 2.5D) {
			return true;
		}
		AlchemyRecipes.Recipe recipe = AlchemyRecipes.byId(racik.getStringOr("resep", ""));
		int masak = racik.getIntOr("masak", 0) + 20;
		DataKerja.tulis(murid.data()).getCompoundOrEmpty(DataKerja.RACIK).putInt("masak", masak);
		Vec3 pot = kuali(murid);
		murid.getLookControl().setLookAt(pot.x, pot.y, pot.z);
		int beat = masak / 20;
		int color = recipe == null ? AlchemyRecipes.MURKY_COLOR : recipe.color();
		RandomSource random = murid.getRandom();
		level.sendParticles(new DustParticleOptions(color, 1.1F), pot.x, pot.y + 0.25D, pot.z, 2, 0.12D, 0.08D, 0.12D, 0.01D);
		if (beat % 3 == 0) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pot.x, pot.y + 0.3D, pot.z, 1, 0.05D, 0.0D, 0.05D, 0.01D);
		}
		if (beat % 4 == 1) {
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.35F, 1.1F + random.nextFloat() * 0.2F);
		} else if (beat % 4 == 3) {
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
		}
		return true;
	}

	@Override
	public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
		if (murid.activity() == Kegiatan.MERAMU.act()) {
			murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		DiscipleData data = murid.data();
		CompoundTag own = DataKerja.tulis(data);
		CompoundTag racik = own.getCompoundOrEmpty(DataKerja.RACIK);
		own.remove(DataKerja.RACIK);
		if (racik.isEmpty()) {
			return;
		}
		List<Bahan.Ambil> bahan = Bahan.bacaRekam(murid, racik);
		AlchemyRecipes.Recipe recipe = AlchemyRecipes.byId(racik.getStringOr("resep", ""));
		int masak = racik.getIntOr("masak", 0);
		if (recipe == null || masak < MASAK) {
			Bahan.kembalikan(murid, bahan);
			if (recipe != null && masak > 0) {
				KabarKerja.catat(murid, "meramu", "meramu.terganggu", EntriHarian.Suasana.KESAL, false, recipe.outputItem().getDescriptionId());
			}
			return;
		}
		long jam = Math.max(1L, Math.round((murid.level().getGameTime() - konteks.mulai()) / (double) DataKerja.JAM_TICK));
		MandiriApi.tambahStatistik(data, MandiriIds.S_JAM_MERAMU, jam);
		Hasil hasil = racik(murid, recipe, murid.getRandom());
		tampil(murid, recipe, hasil);
		cerita(murid, recipe, hasil, DataKerja.hari(murid.level().getServer()), DataKerja.jam(murid.level().getServer()));
	}

	// ================================================================== the brew

	/** What one brew gave: the pills (empty on a failure), their quality, whether they went into the bag. */
	record Hasil(ItemStack pil, @Nullable PillQuality mutu, boolean keTas) {
		boolean berhasil() {
			return !pil.isEmpty();
		}
	}

	/** The old cauldron's roll for this disciple; the pills are made here (lazily) and stored. */
	static Hasil racik(DiscipleEntity murid, AlchemyRecipes.Recipe recipe, RandomSource random) {
		DiscipleData data = murid.data();
		int level = DataKerja.levelAlkimia(data);
		double chance = AlchemyFurnace.successChance(level, false, data.dao() == Dao.FIRE);
		if (random.nextDouble() >= chance) {
			return new Hasil(ItemStack.EMPTY, null, false);
		}
		double roll = random.nextDouble() + level * MUTU_PER_LEVEL;
		PillQuality quality = roll < AMBANG_MID ? PillQuality.LOW : roll < AMBANG_HIGH ? PillQuality.MID
				: roll < AMBANG_PERFECT ? PillQuality.HIGH : PillQuality.PERFECT;
		if ((Talents.talents(data) & Duties.TALENT_PILL_GIFT) != 0 && quality != PillQuality.PERFECT
				&& random.nextDouble() < Duties.PILL_GIFT_CHANCE) {
			quality = quality.up();
		}
		boolean carries = recipe.carriesQuality();
		int count = recipe.count() + (!carries && quality == PillQuality.PERFECT ? 1 : 0);
		ItemStack pills = new ItemStack(recipe.outputItem(), count);
		if (carries) {
			AlchemyComponents.withQuality(pills, quality);
		}
		KerjaKomponen.tandaiBuatan(pills, murid);
		ItemStack shown = pills.copy();
		boolean keTas = Bahan.simpan(murid, pills);
		MandiriApi.maksStatistik(data, MandiriIds.S_KARYA, grade(recipe, quality));
		return new Hasil(shown, quality, keTas);
	}

	/** The craft grade 1-6 of a brew for the KARYA goal: quality + the recipe's tier. */
	static int grade(AlchemyRecipes.Recipe recipe, PillQuality quality) {
		int tier = recipe.xp() >= 45.0D ? 2 : recipe.xp() >= 25.0D ? 1 : 0;
		return Math.min(6, 1 + quality.ordinal() + tier);
	}

	/** The cauldron's own burst at the pot, the sounds of the old success or failure. */
	private static void tampil(DiscipleEntity murid, AlchemyRecipes.Recipe recipe, Hasil hasil) {
		if (!(murid.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 pot = kuali(murid);
		if (hasil.berhasil()) {
			PillQuality q = hasil.mutu() == null ? PillQuality.MID : hasil.mutu();
			Fx.send(level, pot, FxType.ALCHEMY, 2, q.ordinal(), (float) recipe.color());
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8F, 1.2F);
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.7F, 1.3F);
			if (q == PillQuality.PERFECT) {
				level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.5F);
				KabarKerja.suara(murid, "cheer", 2400);
			}
		} else {
			Fx.send(level, pot, FxType.ALCHEMY, 3, 0, 1.0F);
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8F, 0.7F);
			level.playSound(null, pot.x, pot.y, pot.z, SoundEvents.SMOKER_SMOKE, SoundSource.NEUTRAL, 0.9F, 0.6F);
		}
	}

	/** Diary, news, memory and notice of one brew (dated {@code hari}). */
	private static void cerita(DiscipleEntity murid, AlchemyRecipes.Recipe recipe, Hasil hasil, long hari, int jam) {
		java.util.UUID owner = murid.ownerId();
		if (owner == null) {
			return;
		}
		String item = recipe.outputItem().getDescriptionId();
		if (!hasil.berhasil()) {
			KabarKerja.catatPada(owner, murid.soulId(), hari, jam, "meramu", "meramu.gagal", EntriHarian.Suasana.KESAL, false, item);
			return;
		}
		PillQuality q = hasil.mutu() == null ? PillQuality.MID : hasil.mutu();
		String mutu = "kultivator.alchemy.quality." + q.id();
		String n = String.valueOf(hasil.pil().getCount());
		boolean sempurna = q == PillQuality.PERFECT;
		KabarKerja.catatPada(owner, murid.soulId(), hari, jam, "meramu", sempurna ? "meramu.sempurna" : "meramu.berhasil",
				sempurna ? EntriHarian.Suasana.BANGGA : EntriHarian.Suasana.SENANG, sempurna, item, mutu, n,
				hasil.keTas() ? DataKerja.P + "tempat.tas" : DataKerja.P + "tempat.lumbung");
		if (sempurna || recipe.xp() >= 45.0D) {
			KabarKerja.kabar(murid, sempurna ? "pil_sempurna" : "pil", sempurna, murid.getName().getString(), n, item, mutu);
			KabarKerja.beritahu(murid, Kebijakan.Pemberitahuan.TEKNIK, KabarKerja.baris("pesan.pil", 0xFFC23C, KabarKerja.nama(murid),
					hasil.pil().getHoverName(), q.displayName()), sempurna);
		}
		if (sempurna) {
			Ingatan.remember(murid.level().getServer(), murid.data(), "mandiri.karya", null, 4, DataKerja.P + "ingat.pil_sempurna",
					hasil.pil().getHoverName().getString());
		}
	}

	// ================================================================== the recipe

	/** The recipe to brew with this stock, or null. */
	static @Nullable AlchemyRecipes.Recipe resep(DiscipleEntity murid, Bahan.Stok stok) {
		DiscipleData data = murid.data();
		int level = DataKerja.levelAlkimia(data);
		double batas = 30.0D + 15.0D * level;
		boolean alkemis = MandiriApi.jalan(data) == JalanHidup.ALKEMIS;
		AlchemyRecipes.Recipe best = null;
		double bestScore = 0.0D;
		for (AlchemyRecipes.Recipe recipe : AlchemyRecipes.all()) {
			if (recipe.core() != null || recipe.xp() > batas || !stok.cukup(recipe.ingredients())) {
				continue;
			}
			double score = recipe.xp() * (butuh(murid, recipe) ? 3.0D : 1.0D) * (alkemis ? 1.5D : 1.0D);
			if (score > bestScore) {
				bestScore = score;
				best = recipe;
			}
		}
		return best;
	}

	/** True when the pill answers what the disciple needs now. */
	private static boolean butuh(DiscipleEntity murid, AlchemyRecipes.Recipe recipe) {
		DiscipleData data = murid.data();
		var item = recipe.outputItem();
		if (murid.getHealth() < murid.getMaxHealth() * 0.7F && item == AlchemyItems.HEALING_PILL) {
			return true;
		}
		if (MandiriApi.iblis(data) >= 60 && item == AlchemyItems.HEART_CALMING_PILL) {
			return true;
		}
		Realm realm = data.realm();
		return realm == Realm.QI_REFINING && item == AlchemyItems.FOUNDATION_PILL
				|| realm == Realm.FOUNDATION && item == AlchemyItems.GOLDEN_CORE_PILL
				|| realm.atLeast(Realm.GOLDEN_CORE) && item == AlchemyItems.TRIBULATION_WARDING_PILL
				|| item == AlchemyItems.QI_GATHERING_PILL && !realm.atLeast(Realm.GOLDEN_CORE);
	}

	/** Where the small cauldron stands: a little in front of the seated disciple. */
	static Vec3 kuali(DiscipleEntity murid) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, murid.getYRot());
		return murid.position().add(forward.x * 0.7D, 0.2D, forward.z * 0.7D);
	}

	/**
	 * The catch-up event of 39.4.7 step 4 ("made 1-3 pills from allowed Lumbung herbs"): one brew from the bag and the
	 * allowance, dated {@code hari}, without effects. Only for alchemists and disciples that have brewed before.
	 */
	static boolean kejar(DiscipleEntity murid, long hari, RandomSource random) {
		DiscipleData data = murid.data();
		boolean suka = MandiriApi.jalan(data) == JalanHidup.ALKEMIS || MandiriApi.statistik(data, MandiriIds.S_JAM_MERAMU) > 0L;
		if (!suka || !data.realm().atLeast(Realm.QI_REFINING) || murid.ownerId() == null) {
			return false;
		}
		AlchemyRecipes.Recipe recipe = resep(murid, Bahan.stok(murid, AlchemyRecipes::isIngredient));
		if (recipe == null || Bahan.ambilSemua(murid, recipe.ingredients()).isEmpty()) {
			return false;
		}
		MandiriApi.tambahStatistik(data, MandiriIds.S_JAM_MERAMU, 2L);
		Hasil hasil = racik(murid, recipe, random);
		cerita(murid, recipe, hasil, hari, 15);
		return true;
	}
}
