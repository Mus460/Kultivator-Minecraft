package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.masuk.CawanUjianEntity;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * RACUN "Tiga Cawan" (DESIGN 41.5.4; the Five Poison sects, 五毒教): three bronze cups ({@link CawanUjianEntity}) steam
 * on the test spot, each holding one of five poisons. Drink one (right-click): Poison II for 10 s and a scent rises (a line
 * in chat). Within 20 s pick the antidote from the five herbs of the test pouch: each herb's note says which scent it
 * answers. The right herb ends the poison and pales the cup; a wrong herb is wasted (and may be the one a later cup
 * needs). Letting the poison run its course and standing it adds 0.1.
 * <p>
 * Poisons (scent → herb): green viper (fishy wet scales → dried kelp), scorpion (rusty iron → honeycomb), centipede (musty
 * cave earth → beetroot), golden toad (sickly-sweet rotten fruit → glistering melon), black widow (bitter incense smoke →
 * fermented spider eye). Three distinct poisons per test, seeded per candidate and day.
 * <p>
 * {@code S2 = right / 3 + 0.1 per cup endured} (max 1).
 */
public final class TigaCawan extends UjianDasar {

	public static final int RACUN = 5;
	private static final Item[] PENAWAR = {Items.DRIED_KELP, Items.HONEYCOMB, Items.BEETROOT, Items.GLISTERING_MELON_SLICE, Items.FERMENTED_SPIDER_EYE};
	private static final int[] WARNA = {0x4E8A3A, 0x9A6A2E, 0x6A4A6A, 0xC8B040, 0x2A2A30};
	private static final int PILIH_TICK = 20 * 20;
	private static final int RACUN_TICK = 10 * 20;

	private final CawanUjianEntity[] cawan = new CawanUjianEntity[3];
	private final int[] jenis = new int[3];
	/** Per cup: 0 waiting, 1 right, 2 wrong, 3 endured. */
	private final int[] hasil = new int[3];
	private int diminum = -1;
	private int sampai;
	private int benar;
	private int tahan;
	private int selesaiCawan;

	public TigaCawan(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.RACUN, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < 3) {
			return t("anggaran_kurang", 3);
		}
		List<Integer> semua = new ArrayList<>(List.of(0, 1, 2, 3, 4));
		Collections.shuffle(semua, new Random(benih));
		Vec3 depan = pusat.distanceToSqr(pemain.position()) < 4.0D
				? pemain.position().add(Vec3.directionFromRotation(0.0F, pemain.getYRot()).scale(2.0D))
				: pusat;
		Vec3 ke = pemain.position().subtract(depan);
		Vec3 sisi = new Vec3(-ke.z, 0.0D, ke.x);
		sisi = sisi.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : sisi.normalize();
		for (int i = 0; i < 3; i++) {
			jenis[i] = semua.get(i);
			Vec3 at = tanah(depan.x + sisi.x * (i - 1) * 0.9D, depan.y, depan.z + sisi.z * (i - 1) * 0.9D);
			CawanUjianEntity c = GabungEntities.CAWAN_UJIAN.create(level, EntitySpawnReason.EVENT);
			if (c == null) {
				return t("tidak_bisa");
			}
			c.snapTo(at.x, at.y, at.z, yaw(at, pemain.position()), 0.0F);
			c.setNomor(i);
			c.setWarnaIsi(WARNA[jenis[i]]);
			c.setFase(CawanUjianEntity.FASE_PENUH);
			c.setPemilik(this::minum);
			if (!muncul(c)) {
				return t("anggaran_kurang", 3);
			}
			cawan[i] = c;
		}
		for (int k = 0; k < RACUN; k++) {
			beri(itemUjian(new ItemStack(PENAWAR[k]), "penawar_" + k, t(ChatFormatting.GREEN, "tiga_cawan.herba." + k),
					t(ChatFormatting.GRAY, "tiga_cawan.herba_lore", t("tiga_cawan.aroma." + k))));
		}
		chat(t(ChatFormatting.GOLD, "tiga_cawan.mulai"));
		return null;
	}

	private void minum(CawanUjianEntity c, ServerPlayer player) {
		if (selesai() || !player.getUUID().equals(uuid())) {
			return;
		}
		if (diminum >= 0) {
			bar(t(ChatFormatting.YELLOW, "tiga_cawan.satu_satu"));
			return;
		}
		int i = c.nomor();
		if (i < 0 || i > 2 || cawan[i] != c || !c.penuh()) {
			return;
		}
		diminum = i;
		sampai = umur + PILIH_TICK;
		c.setFase(CawanUjianEntity.FASE_DIMINUM);
		pemain.addEffect(new MobEffectInstance(MobEffects.POISON, RACUN_TICK, 1));
		bunyi(c.position(), SoundEvents.WITCH_DRINK, 1.0F, 0.9F);
		chat(t(ChatFormatting.DARK_GREEN, "tiga_cawan.aroma_naik", t("tiga_cawan.aroma." + jenis[i])));
	}

	@Override
	public InteractionResult pakaiItem(ItemStack stack, InteractionHand hand) {
		String k = jenisItem(stack);
		if (!k.startsWith("penawar_")) {
			return InteractionResult.PASS;
		}
		if (diminum < 0) {
			bar(t(ChatFormatting.YELLOW, "tiga_cawan.minum_dulu"));
			return InteractionResult.FAIL;
		}
		int herba;
		try {
			herba = Integer.parseInt(k.substring("penawar_".length()));
		} catch (NumberFormatException exception) {
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		int i = diminum;
		if (herba == jenis[i]) {
			pemain.removeEffect(MobEffects.POISON);
			cawan[i].setFase(CawanUjianEntity.FASE_TAWAR);
			hasil[i] = 1;
			benar++;
			bunyi(pemain.position(), SoundEvents.PLAYER_BURP, 0.8F, 1.3F);
			bar(t(ChatFormatting.GREEN, "tiga_cawan.benar"));
		} else {
			hasil[i] = 2;
			bunyi(pemain.position(), SoundEvents.VILLAGER_NO, 0.8F, 0.8F);
			bar(t(ChatFormatting.RED, "tiga_cawan.salah", t("tiga_cawan.herba." + jenis[i])));
		}
		tutupCawan();
		return InteractionResult.SUCCESS;
	}

	private void tutupCawan() {
		diminum = -1;
		selesaiCawan++;
		skor = Math.min(1.0D, benar / 3.0D + 0.1D * tahan);
		if (selesaiCawan >= 3) {
			akhiri(skor, "tuntas", false);
		}
	}

	@Override
	public void tik() {
		for (CawanUjianEntity c : cawan) {
			if (c == null || c.isRemoved()) {
				akhiri(skor, "rusak", true);
				return;
			}
		}
		if (diminum >= 0 && umur >= sampai) {
			// no antidote chosen: endured if the poison has run its course and the candidate stands
			hasil[diminum] = 3;
			tahan++;
			bar(t(ChatFormatting.YELLOW, "tiga_cawan.bertahan"));
			tutupCawan();
		}
	}

	@Override
	public void waktuHabis() {
		akhiri(Math.min(1.0D, benar / 3.0D + 0.1D * tahan), "waktu", false);
	}

	@Override
	public void bersihkan() {
		for (CawanUjianEntity c : cawan) {
			if (c != null) {
				c.setPemilik(null);
			}
		}
		super.bersihkan();
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(Math.max(0, diminum));
		d[2] = n(selesai() ? 2 : diminum >= 0 ? 1 : 0);
		d[3] = n(benar);
		d[4] = n(diminum >= 0 ? Math.max(0, (sampai - umur + 19) / 20) : 0);
		for (int i = 0; i < 3; i++) {
			d[5 + i] = n(hasil[i]);
		}
		return 7;
	}
}
