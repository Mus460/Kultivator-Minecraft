package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.masuk.DindingIlusiEntity;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * FORMASI "Labirin Batu" (DESIGN 41.5.4; Zhuge Liang's Stone Sentinel Maze, 八阵图, that held Lu Xun): a 9 x 9 maze of
 * invisible walls ({@link DindingIlusiEntity}, 1 x 3 colliders; the client draws mist and trigrams) rises on the
 * courtyard with its mouth towards the candidate. Three formation eyes lie inside; the riddle names three of the eight
 * trigrams by their image ("Langit, then Air, then Gunung"); each eye is flanked by a wall bearing its trigram, five more
 * walls bear the other trigrams as decoys. Step on the eyes in the riddle's order, then walk out. A wrong eye flashes red
 * and costs 10 s.
 * <p>
 * Layout: a perfect maze (randomised depth-first carve over the 4 x 4 cells of the 7 x 7 inside, seeded per candidate
 * and day) plus three extra openings (loops, so a wrong turn is not a dead loss), mouth at one side and exit at the
 * other: 30 border + 15 inner walls + 3 eyes = 48 entities (41.12.3). The walls count as one maze for the budget.
 * {@code S2 = 1 - (time + penalties) / 180} when out with all three eyes; 0 when the time runs out.
 */
public final class LabirinBatu extends UjianDasar {

	public static final int UKURAN = 9;
	public static final int MAKS_ENTITAS = 48;
	private static final int HUKUMAN_TICK = 200;
	private static final int UMPAN = 5;

	private final boolean[][] terbuka = new boolean[UKURAN][UKURAN];
	private final List<DindingIlusiEntity> dinding = new ArrayList<>();
	private final DindingIlusiEntity[] mata = new DindingIlusiEntity[3];
	private final int[][] mataSel = new int[3][2];
	private final int[] teka = new int[3];
	private BlockPos tengah = BlockPos.ZERO;
	private int putar;
	private int maju;
	private int salah;
	private int hukuman;
	private int salahTampil = -1;
	private int salahSampai;
	private boolean pernahMasuk;

	public LabirinBatu(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.FORMASI, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < 1) {
			return t("anggaran_kurang", 1);
		}
		Vec3 m = pusat;
		if (m.distanceToSqr(pemain.position()) < 7.5D * 7.5D) {
			Vec3 look = Vec3.directionFromRotation(0.0F, pemain.getYRot());
			m = pemain.position().add(look.x * 8.0D, 0.0D, look.z * 8.0D);
		}
		tengah = BlockPos.containing(m);
		// the mouth (local -z side) faces the candidate
		Vec3 ke = pemain.position().subtract(Vec3.atCenterOf(tengah));
		double best = -Double.MAX_VALUE;
		int[][] mulut = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int r = 0; r < 4; r++) {
			double dot = mulut[r][0] * ke.x + mulut[r][1] * ke.z;
			if (dot > best) {
				best = dot;
				putar = r;
			}
		}
		Random rnd = new Random(benih);
		bangun(rnd);
		for (int i = 0; i < 3; i++) {
			teka[i] = -1;
		}
		List<Integer> semua = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
		Collections.shuffle(semua, rnd);
		for (int i = 0; i < 3; i++) {
			teka[i] = semua.get(i);
		}
		pilihMata(rnd);
		// walls
		List<int[]> dindingSel = new ArrayList<>();
		for (int gx = 0; gx < UKURAN; gx++) {
			for (int gz = 0; gz < UKURAN; gz++) {
				if (!terbuka[gx][gz]) {
					dindingSel.add(new int[] {gx, gz});
				}
			}
		}
		int[] trigramDinding = new int[dindingSel.size()];
		java.util.Arrays.fill(trigramDinding, -1);
		boolean[] ungkap = new boolean[dindingSel.size()];
		for (int k = 0; k < 3; k++) {
			int idx = tetanggaDinding(dindingSel, mataSel[k][0], mataSel[k][1], ungkap, rnd);
			if (idx >= 0) {
				trigramDinding[idx] = teka[k];
				ungkap[idx] = true;
			}
		}
		for (int u = 0; u < UMPAN; u++) {
			int idx = rnd.nextInt(dindingSel.size());
			int[] c = dindingSel.get(idx);
			boolean tepi = c[0] == 0 || c[1] == 0 || c[0] == UKURAN - 1 || c[1] == UKURAN - 1;
			if (!ungkap[idx] && !tepi) {
				trigramDinding[idx] = semua.get(3 + u % 5);
				ungkap[idx] = true;
			}
		}
		for (int i = 0; i < dindingSel.size(); i++) {
			int[] c = dindingSel.get(i);
			DindingIlusiEntity d = sel(c[0], c[1]);
			if (d == null) {
				return t("tidak_bisa");
			}
			d.setup(trigramDinding[i], false, 0);
			d.setFlag(DindingIlusiEntity.FLAG_TERUNGKAP, ungkap[i]);
			d.setPemilik(true);
			if (!muncul(d)) {
				return t("tidak_bisa");
			}
			dinding.add(d);
		}
		for (int k = 0; k < 3; k++) {
			DindingIlusiEntity e = sel(mataSel[k][0], mataSel[k][1]);
			if (e == null) {
				return t("tidak_bisa");
			}
			e.setup(urutanTrigram(k), true, k + 1);
			e.setPemilik(true);
			if (!muncul(e)) {
				return t("tidak_bisa");
			}
			mata[k] = e;
		}
		bunyi(Vec3.atCenterOf(tengah), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F);
		chat(t(ChatFormatting.GOLD, "labirin_batu.mulai", trigram(teka[0]), trigram(teka[1]), trigram(teka[2])));
		return null;
	}

	/** The trigram shown on eye {@code k}: its riddle trigram (the client draws it only once stepped). */
	private int urutanTrigram(int k) {
		return teka[k];
	}

	private static Component trigram(int n) {
		return t("trigram." + n);
	}

	/** Carves the maze: cells at odd grid coordinates of 1..7, the border closed except mouth (1,0) and exit (7,8). */
	private void bangun(Random rnd) {
		boolean[][] dikunjungi = new boolean[4][4];
		List<int[]> tumpuk = new ArrayList<>();
		tumpuk.add(new int[] {0, 0});
		dikunjungi[0][0] = true;
		terbuka[1][1] = true;
		int[][] arah = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!tumpuk.isEmpty()) {
			int[] c = tumpuk.get(tumpuk.size() - 1);
			List<int[]> calon = new ArrayList<>();
			for (int[] a : arah) {
				int nx = c[0] + a[0];
				int nz = c[1] + a[1];
				if (nx >= 0 && nz >= 0 && nx < 4 && nz < 4 && !dikunjungi[nx][nz]) {
					calon.add(new int[] {nx, nz});
				}
			}
			if (calon.isEmpty()) {
				tumpuk.remove(tumpuk.size() - 1);
				continue;
			}
			int[] n = calon.get(rnd.nextInt(calon.size()));
			dikunjungi[n[0]][n[1]] = true;
			terbuka[1 + 2 * n[0]][1 + 2 * n[1]] = true;
			terbuka[1 + c[0] + n[0]][1 + c[1] + n[1]] = true;
			tumpuk.add(n);
		}
		terbuka[1][0] = true;
		terbuka[UKURAN - 2][UKURAN - 1] = true;
		// three loops: open closed walls between two cells
		List<int[]> tutup = new ArrayList<>();
		for (int gx = 1; gx < UKURAN - 1; gx++) {
			for (int gz = 1; gz < UKURAN - 1; gz++) {
				if (!terbuka[gx][gz] && (gx % 2 == 1) != (gz % 2 == 1)) {
					tutup.add(new int[] {gx, gz});
				}
			}
		}
		Collections.shuffle(tutup, rnd);
		for (int i = 0; i < Math.min(3, tutup.size()); i++) {
			terbuka[tutup.get(i)[0]][tutup.get(i)[1]] = true;
		}
	}

	/** Three eye cells, pairwise at least 4 apart (Manhattan), never the first or last cell. */
	private void pilihMata(Random rnd) {
		List<int[]> sel = new ArrayList<>();
		for (int cx = 0; cx < 4; cx++) {
			for (int cz = 0; cz < 4; cz++) {
				if (!(cx == 0 && cz == 0) && !(cx == 3 && cz == 3)) {
					sel.add(new int[] {1 + 2 * cx, 1 + 2 * cz});
				}
			}
		}
		for (int coba = 0; coba < 200; coba++) {
			Collections.shuffle(sel, rnd);
			int[] a = sel.get(0);
			int[] b = sel.get(1);
			int[] c = sel.get(2);
			if (jarak(a, b) >= 4 && jarak(a, c) >= 4 && jarak(b, c) >= 4) {
				break;
			}
		}
		for (int k = 0; k < 3; k++) {
			mataSel[k][0] = sel.get(k)[0];
			mataSel[k][1] = sel.get(k)[1];
		}
	}

	private static int jarak(int[] a, int[] b) {
		return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
	}

	/** A wall next to cell (gx, gz) not yet carrying a trigram (index in {@code list}), or -1. */
	private static int tetanggaDinding(List<int[]> list, int gx, int gz, boolean[] dipakai, Random rnd) {
		List<Integer> calon = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			int[] c = list.get(i);
			if (!dipakai[i] && Math.abs(c[0] - gx) + Math.abs(c[1] - gz) == 1) {
				calon.add(i);
			}
		}
		return calon.isEmpty() ? -1 : calon.get(rnd.nextInt(calon.size()));
	}

	/** World block of grid cell (gx, gz). */
	private BlockPos blok(int gx, int gz) {
		int lx = gx - 4;
		int lz = gz - 4;
		int wx;
		int wz;
		switch (putar) {
			case 1 -> {
				wx = -lz;
				wz = lx;
			}
			case 2 -> {
				wx = -lx;
				wz = -lz;
			}
			case 3 -> {
				wx = lz;
				wz = -lx;
			}
			default -> {
				wx = lx;
				wz = lz;
			}
		}
		return tengah.offset(wx, 0, wz);
	}

	/** Grid cell of a world block, or null outside the 9 x 9. */
	private int @Nullable [] grid(BlockPos p) {
		int dx = p.getX() - tengah.getX();
		int dz = p.getZ() - tengah.getZ();
		int lx;
		int lz;
		switch (putar) {
			case 1 -> {
				lx = dz;
				lz = -dx;
			}
			case 2 -> {
				lx = -dx;
				lz = -dz;
			}
			case 3 -> {
				lx = -dz;
				lz = dx;
			}
			default -> {
				lx = dx;
				lz = dz;
			}
		}
		int gx = lx + 4;
		int gz = lz + 4;
		return gx < 0 || gz < 0 || gx >= UKURAN || gz >= UKURAN ? null : new int[] {gx, gz};
	}

	private @Nullable DindingIlusiEntity sel(int gx, int gz) {
		BlockPos b = blok(gx, gz);
		Vec3 at = tanah(b.getX() + 0.5D, tengah.getY(), b.getZ() + 0.5D);
		DindingIlusiEntity d = GabungEntities.DINDING_ILUSI.create(level, EntitySpawnReason.EVENT);
		if (d == null) {
			return null;
		}
		d.snapTo(at.x, at.y, at.z, putar * 90.0F, 0.0F);
		return d;
	}

	@Override
	public void tik() {
		if (salahTampil >= 0 && umur >= salahSampai) {
			mata[salahTampil].setFlag(DindingIlusiEntity.FLAG_SALAH, false);
			salahTampil = -1;
		}
		int[] g = grid(pemain.blockPosition());
		if (g != null) {
			pernahMasuk = true;
		}
		if (g != null && pemain.onGround()) {
			for (int k = 0; k < 3; k++) {
				if (g[0] == mataSel[k][0] && g[1] == mataSel[k][1] && !mata[k].has(DindingIlusiEntity.FLAG_TERINJAK)) {
					injak(k);
				}
			}
		}
		if (maju >= 3 && g == null && pernahMasuk) {
			akhiri(1.0D - (umur + hukuman) / (double) batasTick(), "tuntas", false);
			return;
		}
		skor = maju >= 3 ? Math.max(0.0D, 1.0D - (umur + hukuman) / (double) batasTick()) : 0.0D;
	}

	private void injak(int k) {
		if (k == maju) {
			mata[k].setFlag(DindingIlusiEntity.FLAG_TERINJAK, true);
			maju++;
			bunyi(mata[k].position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.8F + 0.25F * maju);
			bar(t(ChatFormatting.AQUA, maju >= 3 ? "labirin_batu.terbuka" : "labirin_batu.mata", maju));
		} else if (salahTampil != k) {
			salah++;
			hukuman += HUKUMAN_TICK;
			if (salahTampil >= 0) {
				mata[salahTampil].setFlag(DindingIlusiEntity.FLAG_SALAH, false);
			}
			mata[k].setFlag(DindingIlusiEntity.FLAG_SALAH, true);
			salahTampil = k;
			salahSampai = umur + 20;
			bunyi(mata[k].position(), SoundEvents.VILLAGER_NO, 0.8F, 0.7F);
			bar(t(ChatFormatting.RED, "labirin_batu.salah"));
		}
	}

	@Override
	public void waktuHabis() {
		akhiri(0.0D, "waktu", false);
	}

	@Override
	public void bersihkan() {
		for (DindingIlusiEntity d : dinding) {
			if (!d.isRemoved()) {
				d.pudar();
				d.setPemilik(false);
			}
		}
		for (DindingIlusiEntity e : mata) {
			if (e != null && !e.isRemoved()) {
				e.pudar();
				e.setPemilik(false);
			}
		}
		// the faded cells leave by themselves after PUDAR_TICKS; everything else as usual
		dinding.clear();
		super.bersihkanKecuali(DindingIlusiEntity.class);
	}

	@Override
	public int isiData(byte[] d) {
		d[1] = n(maju);
		d[2] = n(salah);
		d[3] = n(maju < 3 ? teka[maju] : 255);
		d[4] = n(teka[0]);
		d[5] = n(teka[1]);
		d[6] = n(teka[2]);
		return 6;
	}
}
