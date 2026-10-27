package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.masuk.BonekaUjianEntity;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PEDANG "Seratus Tebasan" (DESIGN 41.5.4; the sword sects' drill of a hundred cuts): six straw dummies on their posts
 * ({@link BonekaUjianEntity}, a ring of 4 blocks around the test spot, all facing the middle) light up one after another;
 * strike the lit one with a sword or spear ({@link WeaponClass#of}: PEDANG, PEDANG_BERAT, TOMBAK) before it goes dark.
 * A hundred lights in all; the beat quickens from 2 s to 1.4 s, so a light may overlap the next late in the drill.
 * {@code S2 = kena / 100}. A wrong weapon or a dark dummy breaks the combo (shown on the HUD, not scored). An outer
 * disciple of the sect drills beside the ring (clip {@code tebasan_ujian}, wooden sword) as the example.
 * <p>
 * The duel option (41.5.4): a candidate of Foundation or higher may instead right-click the outer disciple before the
 * first hit lands to ask for a friendly Duel Pedang. The dummies then stand dark. A win is {@code S2} 1.0; a loss that
 * still took 40 % of the disciple's health is 0.6; a lesser loss or a draw 0.3. The limit is 195 s so that the last of the
 * hundred lights can still be answered at the slowest beat.
 */
public final class SeratusTebasan extends UjianDasar {

	public static final int BONEKA = 6;
	public static final int NYALA_TOTAL = 100;
	public static final double JARI = 4.0D;
	private static final int JEDA_AWAL = 40;
	private static final int JEDA_AKHIR = 28;
	/** 100 lights at 40..28 ticks come to about 173 s; the limit leaves room for the last one. */
	private static final int BATAS = 195 * 20;
	private static final float LAWAN_HILANG = 0.40F;

	private final List<BonekaUjianEntity> boneka = new ArrayList<>();
	private int nyala;
	private int kena;
	private int kombo;
	private int komboTerbaik;
	private int berikut = 60;
	private int terakhir = -1;
	private int salahPesan;
	private @Nullable KultivatorNpc guru;
	/** 0 the dummies, 1 the duel runs, 2 the duel ended (the score is set). */
	private int duel;
	private int duelPesan;

	public SeratusTebasan(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.PEDANG, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < BONEKA) {
			return t("anggaran_kurang", BONEKA);
		}
		int warna = sekte.isEmpty() ? 0xC9D6E8 : SekteApi.diplomasi().color(sekte);
		for (int i = 0; i < BONEKA; i++) {
			double a = Math.PI * 2.0D * i / BONEKA;
			Vec3 at = dariPusat(Math.cos(a) * JARI, Math.sin(a) * JARI);
			BonekaUjianEntity b = GabungEntities.BONEKA_UJIAN.create(level, EntitySpawnReason.EVENT);
			if (b == null) {
				return t("tidak_bisa");
			}
			b.snapTo(at.x, at.y, at.z, yaw(at, pusat), 0.0F);
			b.setWarna(warna);
			b.setPemilik((dummy, player) -> dipukul(dummy, player));
			if (!muncul(b)) {
				return t("anggaran_kurang", BONEKA);
			}
			boneka.add(b);
		}
		Vec3 sisi = dariPusat(JARI + 2.5D, 0.0D);
		guru = tubuh(NpcRole.MURID_SEKTE_LUAR, sisi, yaw(sisi, pusat), realmLawan(), t("pengajar"), null);
		if (guru != null) {
			guru.memory().putBoolean("hold", true);
			HidupApi.klip(guru, GabungGerakId.TEBASAN_UJIAN, GabungGerakId.PROP_PEDANG_KAYU, 0);
		}
		chat(t(ChatFormatting.GOLD, "seratus_tebasan.mulai", NYALA_TOTAL));
		if (guru != null && bolehDuel()) {
			chat(t(ChatFormatting.AQUA, "seratus_tebasan.tawaran_duel"));
		}
		return null;
	}

	@Override
	public int batasTick() {
		return BATAS;
	}

	/** Foundation or higher, before the first hit. */
	private boolean bolehDuel() {
		return duel == 0 && kena == 0 && Cultivators.get(pemain).realm().ordinal() >= Realm.FOUNDATION.ordinal();
	}

	@Override
	public InteractionResult pakaiEntitas(Entity entity, InteractionHand hand) {
		if (guru == null || entity != guru || hand != InteractionHand.MAIN_HAND || duel != 0) {
			return InteractionResult.PASS;
		}
		if (!bolehDuel()) {
			if (duelPesan <= 0) {
				bar(t(ChatFormatting.YELLOW, Cultivators.get(pemain).realm().ordinal() < Realm.FOUNDATION.ordinal()
						? "seratus_tebasan.duel_ranah" : "seratus_tebasan.duel_terlambat"));
				duelPesan = 60;
			}
			return InteractionResult.SUCCESS;
		}
		guru.memory().putBoolean("hold", false);
		Component sebab = DuelChallenges.startDirect(pemain, pemain, guru, DuelIds.PAIR_NPC, DuelRuleSet.defaults());
		if (sebab != null) {
			guru.memory().putBoolean("hold", true);
			chat(t(ChatFormatting.RED, "seratus_tebasan.duel_gagal", sebab));
			return InteractionResult.SUCCESS;
		}
		duel = 1;
		for (BonekaUjianEntity b : boneka) {
			b.padamkan();
		}
		chat(t(ChatFormatting.GOLD, "seratus_tebasan.duel_mulai"));
		return InteractionResult.SUCCESS;
	}

	/** The duel of the option ended (told by {@code UjianCorak}'s duel listener; cheap, the end is taken in the tick). */
	public void duelSelesai(@Nullable LivingEntity pemenang, @Nullable LivingEntity kalah) {
		if (duel != 1 || selesai()) {
			return;
		}
		duel = 2;
		boolean menang = pemenang != null && pemenang.getUUID().equals(uuid());
		LivingEntity lawan = guru;
		boolean separuh = lawan != null && lawan.getHealth() <= lawan.getMaxHealth() * (1.0F - LAWAN_HILANG);
		double nilai = menang ? 1.0D : separuh ? 0.6D : 0.3D;
		skor = nilai;
		akhiri(nilai, menang ? "tuntas" : "duel_kalah", false);
	}

	@Override
	public void tik() {
		if (duelPesan > 0) {
			duelPesan--;
		}
		if (duel != 0) {
			if (guru == null || guru.isRemoved()) {
				akhiri(skor, "rusak", true);
			}
			return;
		}
		for (BonekaUjianEntity b : boneka) {
			if (b.isRemoved()) {
				akhiri(skor, "rusak", true);
				return;
			}
		}
		if (salahPesan > 0) {
			salahPesan--;
		}
		if (nyala < NYALA_TOTAL && --berikut <= 0) {
			nyalakanBerikut();
			berikut = JEDA_AWAL - (JEDA_AWAL - JEDA_AKHIR) * nyala / NYALA_TOTAL;
		}
		if (nyala >= NYALA_TOTAL && boneka.stream().noneMatch(BonekaUjianEntity::aktif)) {
			akhiri(kena / (double) NYALA_TOTAL, "tuntas", false);
		}
	}

	private void nyalakanBerikut() {
		Random r = acak();
		int pilih = -1;
		for (int coba = 0; coba < 12 && pilih < 0; coba++) {
			int i = r.nextInt(BONEKA);
			if (i != terakhir && !boneka.get(i).aktif()) {
				pilih = i;
			}
		}
		if (pilih < 0) {
			return;
		}
		BonekaUjianEntity b = boneka.get(pilih);
		b.nyalakan();
		terakhir = pilih;
		nyala++;
		bunyi(b.position(), SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.2F + 0.4F * nyala / NYALA_TOTAL);
	}

	private void dipukul(BonekaUjianEntity b, ServerPlayer player) {
		if (selesai() || !player.getUUID().equals(uuid())) {
			return;
		}
		WeaponClass kelasSenjata = WeaponClass.of(player.getMainHandItem());
		boolean senjata = kelasSenjata == WeaponClass.PEDANG || kelasSenjata == WeaponClass.PEDANG_BERAT || kelasSenjata == WeaponClass.TOMBAK;
		b.tandaiKena();
		if (!b.aktif()) {
			kombo = 0;
			bunyi(b.position(), SoundEvents.ARMOR_STAND_HIT, 0.5F, 0.7F);
			return;
		}
		if (!senjata) {
			kombo = 0;
			bunyi(b.position(), SoundEvents.ARMOR_STAND_HIT, 0.6F, 0.6F);
			if (salahPesan <= 0) {
				bar(t(ChatFormatting.RED, "seratus_tebasan.senjata"));
				salahPesan = 60;
			}
			return;
		}
		b.padamkan();
		kena++;
		kombo++;
		komboTerbaik = Math.max(komboTerbaik, kombo);
		skor = kena / (double) NYALA_TOTAL;
		bunyi(b.position(), SoundEvents.ARMOR_STAND_HIT, 0.9F, 1.1F + Math.min(0.6F, kombo * 0.03F));
		if (kombo > 0 && kombo % 10 == 0) {
			bar(t(ChatFormatting.AQUA, "seratus_tebasan.kombo", kombo));
		}
	}

	@Override
	public void waktuHabis() {
		akhiri(duel != 0 ? skor : kena / (double) NYALA_TOTAL, "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		int lit = 0;
		for (int i = 0; i < boneka.size(); i++) {
			if (boneka.get(i).aktif()) {
				lit = i + 1;
			}
		}
		d[1] = n(kena);
		d[2] = n(nyala);
		d[3] = n(NYALA_TOTAL);
		d[4] = n(lit);
		d[5] = n(kombo);
		return 5;
	}

	/** Best combo of the run (for 41B's verdict lines). */
	public int komboTerbaik() {
		return komboTerbaik;
	}
}
