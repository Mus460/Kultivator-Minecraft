package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TUBUH "Pikul Gunung" (DESIGN 41.5.4; the body sects' mountain-carrying, as the Monkey King was pinned under Five Finger
 * Mountain): carry the {@code batu_pikul} from the foot of the test stair ({@code kompleks/ujian_bawah}) to its top
 * ({@code ujian_atas}); without those titik, to the highest ground within 28 blocks. In the main hand the stone weighs
 * Slowness II and Mining Fatigue; stuffed into the pack it is worse (Slowness III), so it is carried, not hidden. Dropping
 * it costs 10 s (the stone is picked back up); losing it (a chest, lava) fails the test. Arrive holding it.
 * <p>
 * {@code S2 = 1 - (time + penalties) / 240}; limit 240 s.
 */
public final class PikulGunung extends UjianDasar {

	public static final int BATAS = 240 * 20;
	private static final int HUKUMAN_TICK = 200;

	private Vec3 bawah;
	private Vec3 atas;
	private int jatuh;
	private int hukuman;
	private int pesanJeda;

	public PikulGunung(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.TUBUH, kelas, pusat, benih);
		this.bawah = pusat;
		this.atas = pusat;
	}

	@Override
	public int batasTick() {
		return BATAS;
	}

	@Override
	public Vec3 pusatWilayah() {
		return bawah.add(atas).scale(0.5D);
	}

	@Override
	public double jariWilayah() {
		return bawah.distanceTo(atas) / 2.0D + 24.0D;
	}

	@Override
	public @Nullable Component siapkan() {
		// the stone is handed over where the candidate stands (41B brings them to the foot of the stair)
		bawah = pemain.position();
		Vec3 a = titik(TitikGabung.UJIAN_ATAS);
		atas = a != null && a.distanceTo(bawah) >= 12.0D ? a : puncakTerdekat();
		ItemStack batu = itemUjian(new ItemStack(GabungItems.BATU_PIKUL), "batu", null, t(ChatFormatting.GRAY, "pikul_gunung.lore"));
		ItemStack lama = pemain.getMainHandItem().copy();
		pemain.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, batu);
		if (!lama.isEmpty()) {
			beri(lama);
		}
		chat(t(ChatFormatting.GOLD, "pikul_gunung.mulai", (int) atas.x, (int) atas.y, (int) atas.z, (int) Math.round(atas.distanceTo(bawah))));
		bunyi(pemain.position(), SoundEvents.ANVIL_LAND, 0.6F, 0.5F);
		return null;
	}

	/** The highest ground around the foot (16 bearings x 12..28 blocks); 30 blocks ahead when the land is flat. */
	private Vec3 puncakTerdekat() {
		Vec3 best = null;
		int bestY = (int) Math.floor(bawah.y) + 3;
		for (int r = 12; r <= 28; r += 4) {
			for (int i = 0; i < 16; i++) {
				double ang = Math.PI * 2.0D * i / 16.0D;
				int x = (int) Math.floor(bawah.x + Math.cos(ang) * r);
				int z = (int) Math.floor(bawah.z + Math.sin(ang) * r);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (y > bestY && level.getFluidState(new net.minecraft.core.BlockPos(x, y - 1, z)).isEmpty()) {
					bestY = y;
					best = new Vec3(x + 0.5D, y, z + 0.5D);
				}
			}
		}
		if (best != null) {
			return best;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, pemain.getYRot());
		return tanah(bawah.x + look.x * 30.0D, bawah.y, bawah.z + look.z * 30.0D);
	}

	@Override
	public void tik() {
		if (pesanJeda > 0) {
			pesanJeda--;
		}
		boolean diTangan = "batu".equals(jenisItem(pemain.getMainHandItem()));
		if (!diTangan && hitungItem("batu") == 0) {
			if (!pungut()) {
				akhiri(0.0D, "batu_jatuh", true);
				return;
			}
		}
		if (umur % 20 == 1) {
			pemain.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, diTangan ? 1 : 2, false, false, true));
			pemain.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 1, false, false, true));
		}
		if (!diTangan && pesanJeda <= 0) {
			bar(t(ChatFormatting.YELLOW, "pikul_gunung.pegang"));
			pesanJeda = 80;
		}
		double total = Math.max(1.0D, bawah.distanceTo(atas));
		skor = Math.max(0.0D, 1.0D - (umur + hukuman) / (double) BATAS) * Math.min(1.0D, Math.max(0.0D, 1.0D - pemain.position().distanceTo(atas) / total));
		Vec3 p = pemain.position();
		double dx = p.x - atas.x;
		double dz = p.z - atas.z;
		if (diTangan && dx * dx + dz * dz <= 2.5D * 2.5D && Math.abs(p.y - atas.y) <= 2.5D) {
			bunyi(p, SoundEvents.ANVIL_LAND, 1.0F, 0.8F);
			akhiri(1.0D - (umur + hukuman) / (double) BATAS, "tuntas", false);
		}
	}

	/** The stone was dropped: pick it back up from the ground near the player (+10 s). False when it is gone. */
	private boolean pungut() {
		List<ItemEntity> near = level.getEntitiesOfClass(ItemEntity.class, new AABB(pemain.blockPosition()).inflate(10.0D),
				e -> "batu".equals(jenisItem(e.getItem())));
		if (near.isEmpty()) {
			return false;
		}
		ItemEntity e = near.getFirst();
		ItemStack s = e.getItem().copy();
		e.discard();
		beri(s);
		jatuh++;
		hukuman += HUKUMAN_TICK;
		bar(t(ChatFormatting.RED, "pikul_gunung.jatuh"));
		return true;
	}

	@Override
	public void waktuHabis() {
		akhiri(0.0D, "waktu", false);
	}

	@Override
	public void bersihkan() {
		pemain.removeEffect(MobEffects.SLOWNESS);
		pemain.removeEffect(MobEffects.MINING_FATIGUE);
		super.bersihkan();
	}

	@Override
	public int isiData(byte[] d) {
		double total = Math.max(1.0D, bawah.distanceTo(atas));
		d[1] = b(1.0D - Math.min(1.0D, pemain.position().distanceTo(atas) / total));
		d[2] = n("batu".equals(jenisItem(pemain.getMainHandItem())) ? 1 : 0);
		d[3] = n(jatuh);
		return 3;
	}

	/** The top the candidate must reach (41V-P draws a beacon of mist there). */
	public Vec3 atas() {
		return atas;
	}
}
