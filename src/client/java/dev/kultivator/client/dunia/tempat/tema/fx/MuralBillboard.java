package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The painted murals of Tebing Seribu Lukisan in the Kota Oasis (DESIGN 38.T.4.5, 38.T.4.7, 38.T.9.4), drawn as lit
 * decals 4 × 2 blocks a hair in front of their niche walls from the mural atlas ({@code mural.png}, Dunhuang palette):
 * mural {@code i} (1..12) shows cell {@code 2 (i − 1)}; niche 12 shows the players' commissioned murals in turn (cells
 * 24-27 by feat icon), five seconds each; a buried mural of Reruntuhan Gurun shows cells 28-31.
 * <p>
 * "Mural Bergerak": while a player is at stage {@code mural} of {@code tema_oasis_mata_air}, mural 7 "Pendeta yang
 * Diusir" swaps its two frames every 20 ticks for him alone, with a faint warm aura on the rock.
 * <p>
 * From {@code TemaNet.FESTIVAL acara=mural} ({@code gua} count, {@code pemain [{nama, ikon}]}, and the niches
 * {@code titik [{no, x, y, z, yaw}]} with yaw in degrees the direction the painted face looks, optional {@code sel} to
 * pick a cell) and {@code acara=mural_bergerak} ({@code gua} = the mural number, optional {@code x, y, z, yaw}); a
 * moving mural lasts 1500 ticks after its last packet (the server resends every 1200).
 */
final class MuralBillboard {

	/** Feat icon of the server ({@code Mural.prestasi}: 0 realm, 1 Dao, 2 scorpion king, 3 water bearer, 4 caravan guard, 5 dancer) -> cell. */
	private static final int[] IKON_SEL = {26, 27, 25, 24, 25, 27};
	static final float LEBAR = 2.0F;
	static final float TINGGI = 1.0F;
	static final double JANGKAU = 48.0D;

	private record Ceruk(int no, double x, double y, double z, float yaw, int sel) {
	}

	private record Pesanan(String nama, int ikon) {
	}

	private static final Map<Integer, Ceruk> CERUK = new HashMap<>();
	private static final List<Pesanan> PEMAIN = new ArrayList<>();
	private static final Map<Integer, Long> BERGERAK = new HashMap<>();
	private static final float[] UV = new float[4];
	private static @Nullable String captionNama;

	private MuralBillboard() {
	}

	/** {@code acara=mural}. */
	static void terima(CompoundTag d) {
		ListTag titik = d.getListOrEmpty("titik");
		for (int i = 0; i < titik.size() && i < 48; i++) {
			CompoundTag c = titik.getCompoundOrEmpty(i);
			int no = c.getIntOr("no", 0);
			if (no <= 0) {
				continue;
			}
			CERUK.put(no, new Ceruk(no, c.getIntOr("x", 0) + 0.5D, c.getIntOr("y", 0) + 0.5D, c.getIntOr("z", 0) + 0.5D,
					c.getFloatOr("yaw", 0.0F), c.getIntOr("sel", -1)));
		}
		ListTag list = d.getListOrEmpty("pemain");
		PEMAIN.clear();
		for (int i = 0; i < list.size() && PEMAIN.size() < 24; i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			PEMAIN.add(new Pesanan(e.getStringOr("nama", "?"), e.getIntOr("ikon", 0)));
		}
	}

	/** {@code acara=mural_bergerak}. */
	static void bergerak(CompoundTag d, long now) {
		int no = d.getIntOr("gua", 7);
		if (d.contains("x") && d.contains("z")) {
			CERUK.put(no, new Ceruk(no, d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0) + 0.5D, d.getIntOr("z", 0) + 0.5D, d.getFloatOr("yaw", 0.0F), -1));
		}
		if (d.getBooleanOr("mulai", true)) {
			BERGERAK.put(no, now + 1500L);
		} else {
			BERGERAK.remove(no);
		}
	}

	/** Preview: the twelve murals in a row in front of (x, y, z), facing back along yaw (degrees), mural 7 moving. */
	static void uji(double x, double y, double z, float yawDeg, long now) {
		float a = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(a);
		double fz = Mth.cos(a);
		double rx = -fz;
		double rz = fx;
		for (int i = 1; i <= 12; i++) {
			double off = (i - 6.5D) * 4.6D;
			CERUK.put(i, new Ceruk(i, x + fx * 7.0D + rx * off, y + 1.6D, z + fz * 7.0D + rz * off, yawDeg + 180.0F, -1));
		}
		BERGERAK.put(7, now + 600L);
		if (PEMAIN.isEmpty()) {
			PEMAIN.add(new Pesanan("Murid Uji", 0));
			PEMAIN.add(new Pesanan("Penari Uji", 5));
		}
	}

	static boolean aktif() {
		return !CERUK.isEmpty();
	}

	static void bersihkan() {
		CERUK.clear();
		PEMAIN.clear();
		BERGERAK.clear();
		captionNama = null;
	}

	/** The commissioner's name of the niche-12 mural the player stands before (6 blocks), or null. */
	static @Nullable String caption() {
		return captionNama;
	}

	static void tick(long now) {
		BERGERAK.values().removeIf(until -> until < now);
	}

	static void gambar() {
		captionNama = null;
		if (CERUK.isEmpty()) {
			return;
		}
		float t = Bingkai.t;
		float[] glow = FxTekstur.sel(FxTekstur.GLOW);
		for (Ceruk c : CERUK.values()) {
			double d2 = Bingkai.jarak2(c.x(), c.y(), c.z());
			if (d2 > JANGKAU * JANGKAU) {
				continue;
			}
			if (!TemaFx.muat(2)) {
				return;
			}
			boolean moving = BERGERAK.containsKey(c.no());
			int sel;
			if (c.sel() >= 0) {
				sel = c.sel() + (moving ? (int) (t / 20.0F) % 2 : 0);
			} else if (c.no() == 12 && !PEMAIN.isEmpty()) {
				Pesanan p = PEMAIN.get((int) (Bingkai.game / 100L % PEMAIN.size()));
				sel = IKON_SEL[Mth.clamp(p.ikon(), 0, IKON_SEL.length - 1)];
				if (d2 < 36.0D) {
					captionNama = p.nama();
				}
			} else {
				int story = Mth.clamp(c.no() - 1, 0, 11);
				sel = 2 * story + (moving ? (int) (t / 20.0F) % 2 : 0);
			}
			FxTekstur.mural(sel, UV);
			float yaw = c.yaw() * Mth.DEG_TO_RAD;
			// the painted face looks along (-sin yaw, cos yaw): sit 0.03 out from the wall plane
			float nx = -Mth.sin(yaw);
			float nz = Mth.cos(yaw);
			float x = (float) (c.x() - Bingkai.camX) + nx * 0.53F;
			float y = (float) (c.y() - Bingkai.camY);
			float z = (float) (c.z() - Bingkai.camZ) + nz * 0.53F;
			int light = Bingkai.cahaya(c.x() + nx, c.y(), c.z() + nz);
			// dinding(): yaw 0 spans +x and faces +z, so turn by the mural's yaw
			TemaFx.mural.dinding(x, y, z, LEBAR, TINGGI, yaw, UV[0], UV[1], UV[2], UV[3], 0xFFFFFFFF, light);
			if (moving) {
				float pulse = 0.6F + 0.4F * Mth.sin(t * 0.157F);
				TemaFx.fxAdd.dinding(x + nx * 0.02F, y, z + nz * 0.02F, LEBAR * 1.15F, TINGGI * 1.3F, yaw, glow[0], glow[1], glow[2], glow[3],
						Bingkai.argb(0.22F * pulse, 0xFFC070), LightCoordsUtil.FULL_BRIGHT);
			}
		}
	}
}
