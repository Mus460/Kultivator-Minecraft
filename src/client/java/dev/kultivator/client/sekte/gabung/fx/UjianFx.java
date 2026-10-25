package dev.kultivator.client.sekte.gabung.fx;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.masuk.BonekaUjianEntity;
import dev.kultivator.sekte.gabung.masuk.CawanUjianEntity;
import dev.kultivator.sekte.gabung.masuk.DindingIlusiEntity;
import dev.kultivator.sekte.gabung.masuk.GabungEntities;
import dev.kultivator.sekte.gabung.masuk.PilarQiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stage 2 effects of the ten style tests (DESIGN 41.5.4 Ujian Corak, 41V): what the candidate's test HUD says, made
 * visible in the world around the candidate and the test's own entities (whose models, glow and mist are 41V-G's
 * renderers; this class adds the candidate-side light and the moments). Read from the stage 2 HUD of
 * {@code UjianCorak.kirimHud}: {@code tahap} 2, {@code data[0]} the {@link Corak} ordinal, bytes 1.. per style as listed
 * there ("f" = a 0-1 value as 0-255). A byte that rises between two HUD packets is a moment (a hit, a wave passed, a wrong
 * step, being seen) and starts a short burst at the right place.
 * <ul>
 * <li>PEDANG Seratus Tebasan: a column of sword light over the lit dummy and chevrons on the ground leading to it; each
 * hit a crossing slash and a spray of sparks on the dummy; one flying-sword streak circling the candidate per 5 combo
 * (max 6, Shushan), a gold ring every 10 combo.</li>
 * <li>BUDDHA Lorong Perunggu: a turning gold lotus ring under the candidate (red and flickering below 30 % HP), a bell ring
 * of light per wave passed; a broken vow cracks red round the feet for the rest of the test.</li>
 * <li>BINATANG Taklukkan Binatang: over the caged beast a ring of its health with a gold mark at the subdue line, turning
 * green when low enough to feed; while feeding green leaves spiral round it on a band of light from the candidate;
 * a rising burst of leaves when tamed.</li>
 * <li>FORMASI Labirin Batu: the trigram of the next eye floating above the candidate, the three trigrams of the riddle
 * circling at chest height (done ones dim jade, the next one gold); a gold column on each eye stepped in order, a red
 * crack at the feet on a wrong step.</li>
 * <li>UNSUR Lima Pilar Qi: a wavering stream of element light from the held pillar's crystal to the candidate's chest; an
 * arc of 25 beads before the candidate showing the band (jade) and the needle (white, a spark spray while inside the band
 * and holding); five orbs overhead for the five pillars' accuracy.</li>
 * <li>ALKIMIA Satu Tungku: sparks and smoke rising before the candidate while a furnace refines; a pill of light in the
 * grade's colour each time the best grade rises (Perfect: a pillar of light), a black puff for a failed attempt.</li>
 * <li>TUBUH Pikul Gunung: a ghost mountain pressing over the candidate while the stone is held (turning gold near the
 * top), sweat drops, a dust ring at each drop, a gold burst on reaching the top.</li>
 * <li>DARAH Kolam Darah: red mist round the feet and drops of blood drawn upward while in the pool; a red flame aura while
 * the offering's strength lasts; a breathing blood seal overhead when the offering is ready; a red ring per wave.</li>
 * <li>RACUN Tiga Cawan: a green-violet miasma and a shrinking countdown ring while the antidote is chosen; above each cup
 * its verdict (jade right, red crack wrong, gold endured) with a burst when it is given.</li>
 * <li>BAYANGAN Ujian Senyap: shadow wisps at the feet in a dark place; an eye over the candidate opening with the
 * fullest guard's alert (yellow to red), a flash and a red ring each time the candidate is seen; jade light circling
 * once the jade is taken.</li>
 * </ul>
 * Every test opens with a ring in the sect style's colour. Counts follow {@code EffectQuality} (Minimal: markers and bursts
 * only), all quads from the 7b frame budget, nothing past 64 blocks. Switch {@code gabung.tanggaIlusi}. Client only.
 */
public final class UjianFx {

	static final double JANGKAU = 64.0D;
	private static final int ENTITAS_JARI = 24;
	private static final int[] BANYAK = {0, 1, 2};
	private static final int JADE = 0x5FE3C0;
	private static final int GOLD = 0xFFD27A;
	private static final int RED = 0xFF4A3A;
	private static final int BLOOD = 0xC0141E;
	private static final int WHITE = 0xFFFFFF;
	private static final int LEAF = 0x7EE070;
	private static final int POISON = 0x9A5FD0;
	private static final int SHADOW = 0x2A2238;
	/** Pill grade colours 1-4 (Low, Mid, High, Perfect). */
	private static final int[] MUTU_WARNA = {0xFFFFFF, 0xCFF5D0, 0x7FC8FF, 0xC88CFF, 0xFFD27A};

	// ================================================================== state

	private static boolean aktif;
	private static float tampak;
	private static int corak = -1;
	private static final byte[] KINI = new byte[GabungPayloads.UJIAN_DATA_MAKS];
	private static final byte[] LALU = new byte[GabungPayloads.UJIAN_DATA_MAKS];
	private static boolean adaLalu;

	private static final List<BonekaUjianEntity> BONEKA = new ArrayList<>();
	private static final List<PilarQiEntity> PILAR = new ArrayList<>();
	private static final List<DindingIlusiEntity> MATA = new ArrayList<>();
	private static final List<CawanUjianEntity> CAWAN = new ArrayList<>();
	private static @Nullable Mob binatang;

	// bursts: a small ring buffer of short-lived moments
	private static final int LEDAK_MAKS = 64;
	private static final int K_CINCIN = 0, K_PERCIK = 1, K_TEBAS = 2, K_RETAK = 3, K_ASAP = 4, K_BOLA = 5, K_TIANG = 6, K_DAUN = 7,
			K_MATA = 8;
	private static final double[] LX = new double[LEDAK_MAKS];
	private static final double[] LY = new double[LEDAK_MAKS];
	private static final double[] LZ = new double[LEDAK_MAKS];
	private static final long[] LT = new long[LEDAK_MAKS];
	private static final int[] LL = new int[LEDAK_MAKS];
	private static final int[] LK = new int[LEDAK_MAKS];
	private static final int[] LC = new int[LEDAK_MAKS];
	private static final float[] LS = new float[LEDAK_MAKS];
	private static int ledak;

	static boolean any() {
		return aktif || tampak > 0.0F || hidup() > 0;
	}

	static void clear() {
		aktif = false;
		tampak = 0.0F;
		corak = -1;
		adaLalu = false;
		ledak = 0;
		for (int i = 0; i < LEDAK_MAKS; i++) {
			LL[i] = 0;
		}
		lepasEntitas();
	}

	private static void lepasEntitas() {
		BONEKA.clear();
		PILAR.clear();
		MATA.clear();
		CAWAN.clear();
		binatang = null;
	}

	/** Unsigned byte {@code i} of this HUD (0 past the end). */
	static int b(int i) {
		return i < 0 || i >= KINI.length ? 0 : KINI[i] & 0xFF;
	}

	private static int lalu(int i) {
		return i < 0 || i >= LALU.length ? 0 : LALU[i] & 0xFF;
	}

	/** A 0-255 byte as 0-1. */
	static float f(int i) {
		return b(i) / 255.0F;
	}

	/** True when byte {@code i} rose since the last HUD packet. */
	private static boolean naik(int i) {
		return adaLalu && b(i) > lalu(i);
	}

	// ================================================================== tick

	static void tick(ClientLevel level, Player player, long ticks) {
		GabungPayloads.Ujian u = GabungClient.ujian();
		byte[] data = u == null ? null : u.data();
		int jenis = data != null && data.length >= 1 ? data[0] & 0xFF : 255;
		boolean on = u != null && u.tahap() == 2 && jenis < Corak.values().length && GabungFxClient.saklar(GabungFxClient.TANGGA_ILUSI);
		if (!on) {
			if (aktif) {
				aktif = false;
				lepasEntitas();
			}
			tampak = Math.max(0.0F, tampak - 0.04F);
			if (tampak <= 0.0F) {
				corak = -1;
				adaLalu = false;
			}
			return;
		}
		if (!aktif || jenis != corak) {
			aktif = true;
			corak = jenis;
			adaLalu = false;
			Vec3 p = player.position();
			tambah(K_CINCIN, p.x, p.y + 0.05D, p.z, 30, Corak.values()[jenis].warna(), 4.0F);
		}
		tampak = Math.min(1.0F, tampak + 0.05F);
		System.arraycopy(KINI, 0, LALU, 0, KINI.length);
		java.util.Arrays.fill(KINI, (byte) 0);
		System.arraycopy(data, 0, KINI, 0, Math.min(data.length, KINI.length));
		if (ticks % 10L == 0L || !adaLalu) {
			cari(level, player);
		}
		saring();
		momen(player);
		adaLalu = true;
	}

	/** Finds the test's entities near the candidate (every half second). */
	private static void cari(ClientLevel level, Player player) {
		lepasEntitas();
		AABB box = player.getBoundingBox().inflate(ENTITAS_JARI);
		Corak c = Corak.values()[corak];
		for (Entity e : level.getEntitiesOfClass(Entity.class, box, GabungEntities::milik41)) {
			if (e instanceof BonekaUjianEntity boneka) {
				BONEKA.add(boneka);
			} else if (e instanceof PilarQiEntity pilar) {
				PILAR.add(pilar);
			} else if (e instanceof DindingIlusiEntity dinding && dinding.mata()) {
				MATA.add(dinding);
			} else if (e instanceof CawanUjianEntity cawan) {
				CAWAN.add(cawan);
			}
		}
		if (c == Corak.BINATANG) {
			double best = Double.MAX_VALUE;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0D), m -> m.isAlive() && !GabungEntities.milik41(m))) {
				double d = mob.distanceToSqr(player);
				if (d < best) {
					best = d;
					binatang = mob;
				}
			}
		}
	}

	private static void saring() {
		BONEKA.removeIf(Entity::isRemoved);
		PILAR.removeIf(Entity::isRemoved);
		MATA.removeIf(Entity::isRemoved);
		CAWAN.removeIf(Entity::isRemoved);
		if (binatang != null && (binatang.isRemoved() || !binatang.isAlive())) {
			binatang = null;
		}
	}

	/** Bursts for the bytes that rose. */
	private static void momen(Player player) {
		if (!adaLalu) {
			return;
		}
		Vec3 p = player.position();
		double cy = p.y + 1.1D;
		switch (Corak.values()[corak]) {
			case PEDANG -> {
				if (naik(1)) {
					BonekaUjianEntity hit = nyalaAtauDekat(p);
					Vec3 at = hit != null ? hit.position().add(0.0D, 1.3D, 0.0D) : player.getEyePosition().add(player.getViewVector(1.0F).scale(1.5D));
					tambah(K_TEBAS, at.x, at.y, at.z, 8, WHITE, 1.1F);
					tambah(K_PERCIK, at.x, at.y, at.z, 12, GOLD, 1.0F);
				}
				if (naik(5) && b(5) > 0 && b(5) % 10 == 0) {
					tambah(K_CINCIN, p.x, p.y + 0.1D, p.z, 20, GOLD, 3.0F);
				}
			}
			case BUDDHA -> {
				if (naik(2)) {
					tambah(K_CINCIN, p.x, p.y + 0.1D, p.z, 30, GOLD, 6.0F);
					tambah(K_CINCIN, p.x, cy, p.z, 24, 0xFFF0C0, 3.5F);
					tambah(K_BOLA, p.x, p.y + 2.6D, p.z, 30, GOLD, 1.2F);
				}
				if (naik(5)) {
					tambah(K_RETAK, p.x, p.y + 0.05D, p.z, 40, RED, 2.4F);
				}
			}
			case BINATANG -> {
				if (naik(1) && b(1) == 2 && binatang != null) {
					Vec3 at = binatang.position();
					tambah(K_DAUN, at.x, at.y + 0.5D, at.z, 40, LEAF, 1.5F);
					tambah(K_CINCIN, at.x, at.y + 0.1D, at.z, 30, LEAF, 3.0F);
				}
			}
			case FORMASI -> {
				if (naik(1)) {
					DindingIlusiEntity eye = mata(b(1));
					Vec3 at = eye != null ? eye.position() : p;
					tambah(K_TIANG, at.x, at.y, at.z, 40, GOLD, 0.5F);
					tambah(K_CINCIN, at.x, at.y + 0.05D, at.z, 24, GOLD, 2.0F);
				}
				if (naik(2)) {
					tambah(K_RETAK, p.x, p.y + 0.05D, p.z, 30, RED, 1.8F);
					tambah(K_CINCIN, p.x, p.y + 0.05D, p.z, 16, RED, 2.2F);
				}
			}
			case UNSUR -> {
				if (naik(1)) {
					// a pillar finished: its accuracy decides the colour of the burst
					PilarQiEntity done = pilarDekat(p);
					int warna = done != null ? done.warna() & 0xFFFFFF : JADE;
					Vec3 at = done != null ? done.position().add(0.0D, 3.2D, 0.0D) : new Vec3(p.x, cy, p.z);
					tambah(K_BOLA, at.x, at.y, at.z, 24, warna, 1.0F);
					tambah(K_PERCIK, at.x, at.y, at.z, 16, warna, 1.2F);
				}
			}
			case ALKIMIA -> {
				Vec3 front = depan(player, 1.6D);
				if (naik(3)) {
					int grade = Math.min(4, b(3));
					tambah(K_BOLA, front.x, front.y + 1.0D, front.z, 50, MUTU_WARNA[grade], 0.5F + grade * 0.15F);
					tambah(K_CINCIN, front.x, front.y + 1.0D, front.z, 24, MUTU_WARNA[grade], 2.0F);
					tambah(K_PERCIK, front.x, front.y + 1.0D, front.z, 16, MUTU_WARNA[grade], 1.0F);
					if (grade >= 4) {
						tambah(K_TIANG, front.x, front.y, front.z, 60, GOLD, 0.8F);
					}
				} else if (naik(2)) {
					tambah(K_ASAP, front.x, front.y + 0.8D, front.z, 30, 0x1A1614, 1.2F);
				}
			}
			case TUBUH -> {
				if (naik(3)) {
					tambah(K_ASAP, p.x, p.y + 0.1D, p.z, 24, 0x9A8466, 1.4F);
					tambah(K_CINCIN, p.x, p.y + 0.05D, p.z, 18, 0xB89C70, 2.5F);
				}
				if (b(1) >= 250 && lalu(1) < 250) {
					tambah(K_CINCIN, p.x, p.y + 0.1D, p.z, 30, GOLD, 5.0F);
					tambah(K_TIANG, p.x, p.y, p.z, 50, GOLD, 0.6F);
				}
			}
			case DARAH -> {
				if (naik(1)) {
					tambah(K_CINCIN, p.x, p.y + 0.1D, p.z, 30, BLOOD, 5.0F);
				}
				if (naik(5) && b(5) >= 10) {
					// the offering made: blood spray and a flare
					tambah(K_PERCIK, p.x, cy, p.z, 20, BLOOD, 1.4F);
					tambah(K_BOLA, p.x, cy, p.z, 20, RED, 1.0F);
				}
			}
			case RACUN -> {
				for (int i = 0; i < 3; i++) {
					if (naik(5 + i)) {
						CawanUjianEntity cup = cawan(i);
						Vec3 at = cup != null ? cup.position().add(0.0D, 0.6D, 0.0D) : new Vec3(p.x, cy, p.z);
						int r = b(5 + i);
						int warna = r == 1 ? JADE : r == 2 ? RED : GOLD;
						tambah(r == 2 ? K_RETAK : K_BOLA, at.x, at.y, at.z, 30, warna, 0.6F);
						tambah(K_PERCIK, at.x, at.y, at.z, 16, warna, 0.8F);
					}
				}
			}
			case BAYANGAN -> {
				if (naik(1)) {
					tambah(K_MATA, p.x, p.y + 2.8D, p.z, 30, RED, 1.0F);
					tambah(K_CINCIN, p.x, p.y + 0.1D, p.z, 20, RED, 3.5F);
				}
				if (naik(2)) {
					tambah(K_BOLA, p.x, cy, p.z, 30, JADE, 0.9F);
					tambah(K_PERCIK, p.x, cy, p.z, 14, JADE, 1.0F);
				}
			}
			default -> {
			}
		}
	}

	private static void tambah(int kind, double x, double y, double z, int life, int rgb, float size) {
		int i = ledak;
		ledak = (ledak + 1) % LEDAK_MAKS;
		LX[i] = x;
		LY[i] = y;
		LZ[i] = z;
		LT[i] = GabungFxClient.ticks();
		LL[i] = life;
		LK[i] = kind;
		LC[i] = rgb & 0xFFFFFF;
		LS[i] = size;
	}

	private static int hidup() {
		long now = GabungFxClient.ticks();
		int n = 0;
		for (int i = 0; i < LEDAK_MAKS; i++) {
			if (LL[i] > 0 && now - LT[i] < LL[i]) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== lookups

	private static @Nullable BonekaUjianEntity nyalaAtauDekat(Vec3 p) {
		BonekaUjianEntity best = null;
		double d = Double.MAX_VALUE;
		for (BonekaUjianEntity e : BONEKA) {
			if (e.aktif()) {
				return e;
			}
			double k = e.distanceToSqr(p);
			if (k < d) {
				d = k;
				best = e;
			}
		}
		return best;
	}

	private static @Nullable BonekaUjianEntity nyala() {
		for (BonekaUjianEntity e : BONEKA) {
			if (e.aktif()) {
				return e;
			}
		}
		return null;
	}

	private static @Nullable PilarQiEntity pilarDekat(Vec3 p) {
		PilarQiEntity best = null;
		double d = Double.MAX_VALUE;
		for (PilarQiEntity e : PILAR) {
			double k = e.distanceToSqr(p) - (e.fase() == PilarQiEntity.FASE_AKTIF ? 1000.0D : 0.0D);
			if (k < d) {
				d = k;
				best = e;
			}
		}
		return best;
	}

	private static @Nullable DindingIlusiEntity mata(int urutan) {
		for (DindingIlusiEntity e : MATA) {
			if (e.urutan() == urutan) {
				return e;
			}
		}
		return null;
	}

	private static @Nullable CawanUjianEntity cawan(int nomor) {
		for (CawanUjianEntity e : CAWAN) {
			if (e.nomor() == nomor) {
				return e;
			}
		}
		return null;
	}

	/** A point on the ground {@code jauh} blocks before the candidate. */
	private static Vec3 depan(Player player, double jauh) {
		Vec3 look = player.getViewVector(1.0F);
		double len = Math.max(1.0E-3D, Math.sqrt(look.x * look.x + look.z * look.z));
		return player.position().add(look.x / len * jauh, 0.0D, look.z / len * jauh);
	}

	// ================================================================== drawing

	static void draw(ClientLevel level) {
		if (!any()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int mutu = GabungFxClient.mutu();
		int banyak = BANYAK[mutu];
		int bursts = hidup();
		int want = bursts * 18 + (corak >= 0 ? 40 + banyak * 50 : 0);
		if (!EpicBudget.takeQuads(want)) {
			if (!EpicBudget.takeQuads(want / 3)) {
				return;
			}
			banyak = 0;
		}
		float vis = GabungFxClient.smooth(tampak);
		if (corak >= 0 && vis > 0.0F) {
			Vec3 p = player.getPosition(GabungFxClient.partial);
			if (GabungFxClient.jarak(p.x, p.y, p.z) < JANGKAU) {
				gaya(player, p, vis, banyak);
			}
		}
		drawLedak(banyak);
	}

	private static void gaya(LocalPlayer player, Vec3 p, float vis, int banyak) {
		double now = GabungFxClient.now;
		float part = GabungFxClient.partial;
		double cy = p.y + 1.1D;
		switch (Corak.values()[corak]) {
			case PEDANG -> pedang(player, p, vis, banyak, now, part);
			case BUDDHA -> {
				float hp = f(4);
				boolean low = hp < 0.3F;
				float flick = low ? 0.6F + 0.4F * (float) Math.sin(now * 0.8D) : 1.0F;
				int ring = low ? GabungFxClient.mix(GOLD, RED, 0.7F) : GOLD;
				GabungFxClient.datar(GabungFxClient.SPRITE, p.x, p.y + 0.06D, p.z, 1.6F, GabungFxClient.argb(0.55F * vis * flick, ring),
						GabungFxClient.SEL_CINCIN, now * 0.015D);
				// one small ring per wave reached, stacked under the feet
				for (int w = 1; w < Math.min(3, b(1)); w++) {
					GabungFxClient.datar(GabungFxClient.SPRITE, p.x, p.y + 0.06D + w * 0.02D, p.z, 1.6F + w * 0.45F,
							GabungFxClient.argb(0.3F * vis, ring), GabungFxClient.SEL_CINCIN, -now * 0.01D * w);
				}
				if (banyak > 0) {
					for (int i = 0; i < 4 * banyak; i++) {
						double a = now * 0.03D + i * Math.PI * 2.0D / (4 * banyak);
						double r = 0.9D;
						GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * r, cy + Math.sin(now * 0.05D + i) * 0.2D, p.z + Math.sin(a) * r,
								0.12F, GabungFxClient.argb(0.7F * vis, 0xFFE9A8), GabungFxClient.SEL_GLOW, 0.0F);
					}
				}
				if (b(5) == 1) {
					GabungFxClient.datar(GabungFxClient.SPRITE, p.x, p.y + 0.05D, p.z, 1.3F, GabungFxClient.argb(0.6F * vis, RED), GabungFxClient.SEL_RETAK,
							0.6D);
				}
			}
			case BINATANG -> binatang(player, p, vis, banyak, now, part);
			case FORMASI -> {
				double bob = Math.sin(now * 0.08D) * 0.1D;
				int next = b(3);
				if (next < 8) {
					GabungFxClient.trigram(p.x, p.y + 2.55D + bob, p.z, next, 0.22F, GabungFxClient.argb(0.85F * vis, GOLD));
				}
				int done = b(1);
				for (int i = 0; i < 3; i++) {
					double a = now * 0.02D + i * Math.PI * 2.0D / 3.0D;
					double x = p.x + Math.cos(a) * 1.6D;
					double z = p.z + Math.sin(a) * 1.6D;
					int warna = i < done ? JADE : i == done ? GOLD : 0xC8C8D8;
					float alpha = i < done ? 0.35F : i == done ? 0.9F : 0.5F;
					GabungFxClient.trigram(x, cy + Math.sin(now * 0.06D + i) * 0.08D, z, b(4 + i), 0.16F, GabungFxClient.argb(alpha * vis, warna));
				}
				if (banyak > 0) {
					DindingIlusiEntity eye = mata(done + 1);
					if (eye != null && b(2) > 0) {
						// after a wrong step the next eye glimmers faintly through the mist (a hint, not a path)
						Vec3 e = eye.getPosition(part);
						GabungFxClient.sprite(GabungFxClient.LUNAK, e.x, e.y + 0.4D, e.z, 0.8F,
								GabungFxClient.argb((0.12F + 0.08F * (float) Math.sin(now * 0.1D)) * vis, GOLD), GabungFxClient.SEL_GLOW, 0.0F);
					}
				}
			}
			case UNSUR -> unsur(player, p, vis, banyak, now, part);
			case ALKIMIA -> {
				if (b(1) == 1) {
					Vec3 front = depan(player, 1.6D);
					int n = 3 + banyak * 5;
					for (int i = 0; i < n; i++) {
						double t = (now * 0.03D + i * 0.37D) % 1.0D;
						double a = i * 2.399D;
						double x = front.x + Math.cos(a) * 0.35D * (1.0D - t);
						double z = front.z + Math.sin(a) * 0.35D * (1.0D - t);
						double y = front.y + 0.6D + t * 1.6D;
						GabungFxClient.sprite(GabungFxClient.SPRITE, x, y, z, 0.16F + (float) t * 0.1F,
								GabungFxClient.argb((1.0F - (float) t) * 0.85F * vis, GabungFxClient.mix(0xFFB040, 0xFF5020, (float) t)), GabungFxClient.SEL_API,
								(float) (now * 0.1D + i));
						if (banyak > 0 && i % 2 == 0) {
							GabungFxClient.sprite(GabungFxClient.LUNAK, x, y + 0.8D, z, 0.35F + (float) t * 0.4F,
									GabungFxClient.argb((1.0F - (float) t) * 0.25F * vis, 0x6A625C), GabungFxClient.SEL_ASAP, (float) (now * 0.01D + i));
						}
					}
				}
				if (b(3) > 0) {
					// the best pill so far, a small orb of its grade over the shoulder
					int grade = Math.min(4, b(3));
					GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + GabungFxClient.RIGHT.x * 0.6D, p.y + 2.1D + Math.sin(now * 0.07D) * 0.06D,
							p.z + GabungFxClient.RIGHT.z * 0.6D, 0.14F, GabungFxClient.argb(0.9F * vis, MUTU_WARNA[grade]), GabungFxClient.SEL_GLOW, 0.0F);
				}
			}
			case TUBUH -> {
				if (b(2) == 1) {
					float up = f(1);
					int warna = GabungFxClient.mix(0x8C7A62, GOLD, GabungFxClient.smooth((up - 0.6F) / 0.4F));
					double sink = Math.sin(now * 0.05D) * 0.06D;
					GabungFxClient.tegak(GabungFxClient.LUNAK, p.x, p.y + 3.0D + sink, p.z, 1.2F, 0.9F, GabungFxClient.argb(0.32F * vis, warna),
							GabungFxClient.SEL_GUNUNG);
					GabungFxClient.beam(GabungFxClient.CAHAYA, p.x, p.y + 2.2D + sink, p.z, p.x, p.y + 1.9D, p.z, 0.5F, GabungFxClient.argb(0.0F, warna),
							GabungFxClient.argb(0.3F * vis, warna));
					int n = 1 + banyak * 2;
					for (int i = 0; i < n; i++) {
						double t = (now * 0.04D + i * 0.41D) % 1.0D;
						double a = i * 2.1D + 0.7D;
						GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * 0.3D, p.y + 1.8D - t * 1.2D, p.z + Math.sin(a) * 0.3D, 0.07F,
								GabungFxClient.argb((1.0F - (float) t) * 0.8F * vis, 0xBFE8FF), GabungFxClient.SEL_TETES, 0.0F);
					}
					if (banyak > 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.002D) {
						for (int i = 0; i < 2 * banyak; i++) {
							double a = now * 0.2D + i * 1.7D;
							GabungFxClient.sprite(GabungFxClient.LUNAK, p.x + Math.cos(a) * 0.5D, p.y + 0.15D, p.z + Math.sin(a) * 0.5D, 0.3F,
									GabungFxClient.argb(0.22F * vis, 0x9A8466), GabungFxClient.SEL_ASAP, (float) a);
						}
					}
				}
			}
			case DARAH -> darah(p, vis, banyak, now);
			case RACUN -> racun(p, vis, banyak, now, part);
			case BAYANGAN -> {
				if (b(4) == 1) {
					int n = 3 + banyak * 4;
					for (int i = 0; i < n; i++) {
						double a = i * 2.399D + now * 0.01D;
						double r = 0.5D + (i % 3) * 0.35D;
						GabungFxClient.sprite(GabungFxClient.LUNAK, p.x + Math.cos(a) * r, p.y + 0.25D + (i % 4) * 0.25D, p.z + Math.sin(a) * r, 0.45F,
								GabungFxClient.argb(0.35F * vis, SHADOW), GabungFxClient.SEL_ASAP, (float) (now * 0.005D + i));
					}
				}
				float alert = f(3);
				if (alert > 0.15F) {
					float open = GabungFxClient.smooth((alert - 0.15F) / 0.85F);
					int warna = GabungFxClient.mix(0xFFE060, RED, open);
					float pulse = alert > 0.7F ? 0.75F + 0.25F * (float) Math.sin(now * 0.6D) : 1.0F;
					GabungFxClient.sprite(GabungFxClient.SPRITE, p.x, p.y + 2.5D, p.z, 0.2F + 0.25F * open, GabungFxClient.argb((0.35F + 0.55F * open) * vis * pulse,
							warna), GabungFxClient.SEL_MATA, 0.0F);
				}
				if (b(2) == 1) {
					for (int i = 0; i < 2 + banyak; i++) {
						double a = now * 0.06D + i * Math.PI * 2.0D / (2 + banyak);
						GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * 0.7D, p.y + 1.0D + Math.sin(a * 2.0D) * 0.2D, p.z + Math.sin(a) * 0.7D,
								0.1F, GabungFxClient.argb(0.8F * vis, JADE), GabungFxClient.SEL_GLOW, 0.0F);
					}
				}
			}
			default -> {
			}
		}
	}

	private static void pedang(LocalPlayer player, Vec3 p, float vis, int banyak, double now, float part) {
		BonekaUjianEntity lit = nyala();
		if (lit != null) {
			Vec3 d = lit.getPosition(part);
			float pulse = 0.75F + 0.25F * (float) Math.sin(now * 0.5D);
			GabungFxClient.beam(GabungFxClient.CAHAYA, d.x, d.y, d.z, d.x, d.y + 4.5D, d.z, 0.35F, GabungFxClient.argb(0.6F * vis * pulse, 0xE8F2FF),
					GabungFxClient.argb(0.0F, 0xE8F2FF));
			// chevrons on the ground from the candidate toward the lit dummy
			double dx = d.x - p.x;
			double dz = d.z - p.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 2.0D) {
				double hx = dx / len;
				double hz = dz / len;
				int n = Math.min(6, (int) (len / 1.2D));
				for (int i = 1; i <= n; i++) {
					double s = i * 1.0D + (now * 0.05D % 1.0D);
					if (s > len - 1.0D) {
						break;
					}
					double x = p.x + hx * s;
					double z = p.z + hz * s;
					double ang = Math.atan2(hz, hx);
					float a = 0.5F * vis * (1.0F - (float) (s / len));
					GabungFxClient.beam(GabungFxClient.CAHAYA, x - Math.cos(ang + 2.4D) * 0.35D, p.y + 0.08D, z - Math.sin(ang + 2.4D) * 0.35D, x, p.y + 0.08D, z,
							0.1F, GabungFxClient.argb(a, GOLD), GabungFxClient.argb(a, GOLD));
					GabungFxClient.beam(GabungFxClient.CAHAYA, x - Math.cos(ang - 2.4D) * 0.35D, p.y + 0.08D, z - Math.sin(ang - 2.4D) * 0.35D, x, p.y + 0.08D, z,
							0.1F, GabungFxClient.argb(a, GOLD), GabungFxClient.argb(a, GOLD));
				}
			}
		}
		// flying-sword streaks circling the candidate, one per 5 combo
		int swords = Math.min(6, b(5) / 5);
		for (int i = 0; i < swords; i++) {
			double a = now * 0.12D + i * Math.PI * 2.0D / Math.max(1, swords);
			double r = 1.2D;
			double y = p.y + 1.2D + Math.sin(now * 0.07D + i) * 0.25D;
			double x0 = p.x + Math.cos(a) * r;
			double z0 = p.z + Math.sin(a) * r;
			double x1 = p.x + Math.cos(a - 0.45D) * r;
			double z1 = p.z + Math.sin(a - 0.45D) * r;
			GabungFxClient.beam(GabungFxClient.CAHAYA, x0, y, z0, x1, y, z1, 0.09F, GabungFxClient.argb(0.95F * vis, 0xF4FAFF),
					GabungFxClient.argb(0.0F, 0x9FC8FF));
			if (banyak > 0) {
				GabungFxClient.sprite(GabungFxClient.SPRITE, x0, y, z0, 0.1F, GabungFxClient.argb(0.8F * vis, WHITE), GabungFxClient.SEL_GLOW, 0.0F);
			}
		}
	}

	private static void binatang(LocalPlayer player, Vec3 p, float vis, int banyak, double now, float part) {
		Mob beast = binatang;
		if (beast == null || GabungFxClient.jarak(beast.getX(), beast.getY(), beast.getZ()) > JANGKAU) {
			return;
		}
		Vec3 m = beast.getPosition(part);
		double top = m.y + beast.getBbHeight() + 0.5D;
		int phase = b(1);
		float hp = f(2);
		float line = Math.max(0.05F, f(4));
		if (phase == 0) {
			boolean ready = hp <= line;
			int warna = ready ? LEAF : GabungFxClient.mix(RED, GOLD, GabungFxClient.smooth(1.0F - (hp - line) / Math.max(0.05F, 1.0F - line)));
			float blink = ready ? 0.7F + 0.3F * (float) Math.sin(now * 0.5D) : 1.0F;
			// the health ring shrinks with the beast's health; a still gold ring marks the subdue line
			GabungFxClient.datar(GabungFxClient.SPRITE, m.x, top, m.z, 0.3F + 0.7F * hp, GabungFxClient.argb(0.75F * vis * blink, warna), GabungFxClient.SEL_CINCIN,
					now * 0.03D);
			GabungFxClient.datar(GabungFxClient.SPRITE, m.x, top + 0.02D, m.z, 0.3F + 0.7F * line, GabungFxClient.argb(0.6F * vis, GOLD), GabungFxClient.SEL_CINCIN,
					-now * 0.01D);
		} else if (phase == 1) {
			Vec3 chest = new Vec3(p.x, p.y + 1.1D, p.z);
			Vec3 mid = new Vec3(m.x, m.y + beast.getBbHeight() * 0.6D, m.z);
			GabungFxClient.beam(GabungFxClient.CAHAYA, chest.x, chest.y, chest.z, mid.x, mid.y, mid.z, 0.12F, GabungFxClient.argb(0.5F * vis, LEAF),
					GabungFxClient.argb(0.7F * vis, 0xD8FFB0));
			int n = 4 + banyak * 6;
			for (int i = 0; i < n; i++) {
				double t = (now * 0.02D + (double) i / n) % 1.0D;
				double a = t * Math.PI * 6.0D + i;
				double r = 0.6D + beast.getBbWidth() * 0.6D;
				GabungFxClient.sprite(GabungFxClient.SPRITE, m.x + Math.cos(a) * r, m.y + t * (beast.getBbHeight() + 0.8D), m.z + Math.sin(a) * r, 0.13F,
						GabungFxClient.argb((1.0F - (float) t) * 0.9F * vis, LEAF), GabungFxClient.SEL_DAUN, (float) (a * 0.5D));
			}
		}
		if (b(3) == 1) {
			GabungFxClient.sprite(GabungFxClient.LUNAK, p.x, p.y + 1.0D, p.z, 1.1F, GabungFxClient.argb(0.18F * vis, GOLD), GabungFxClient.SEL_GLOW, 0.0F);
		}
	}

	private static void unsur(LocalPlayer player, Vec3 p, float vis, int banyak, double now, float part) {
		PilarQiEntity held = null;
		for (PilarQiEntity e : PILAR) {
			if (e.fase() == PilarQiEntity.FASE_AKTIF) {
				held = e;
				break;
			}
		}
		int warna = held != null ? held.warna() & 0xFFFFFF : 0x9FD8FF;
		boolean holding = b(6) == 1;
		float needle = f(2);
		float centre = f(3);
		float half = Math.max(0.02F, f(4));
		boolean inside = Math.abs(needle - centre) <= half;
		if (held != null) {
			Vec3 c = held.getPosition(part).add(0.0D, 3.2D, 0.0D);
			double tx = p.x;
			double ty = p.y + 1.1D;
			double tz = p.z;
			int seg = 6 + banyak * 3;
			double px = c.x;
			double py = c.y;
			double pz = c.z;
			float strength = holding ? (inside ? 0.85F : 0.45F) : 0.2F;
			for (int i = 1; i <= seg; i++) {
				double t = (double) i / seg;
				double wob = Math.sin(now * 0.3D + t * 9.0D) * 0.18D * Math.sin(Math.PI * t) * (inside ? 0.4D : 1.0D);
				double x = c.x + (tx - c.x) * t + GabungFxClient.RIGHT.x * wob;
				double y = c.y + (ty - c.y) * t + Math.sin(Math.PI * t) * 0.5D;
				double z = c.z + (tz - c.z) * t + GabungFxClient.RIGHT.z * wob;
				GabungFxClient.beam(GabungFxClient.CAHAYA, px, py, pz, x, y, z, inside ? 0.16F : 0.1F, GabungFxClient.argb(strength * vis, warna),
						GabungFxClient.argb(strength * vis, GabungFxClient.mix(warna, WHITE, 0.3F)));
				px = x;
				py = y;
				pz = z;
			}
		}
		// the gauge: an arc of 25 beads before the candidate, -60..+60 degrees about the look direction
		Vec3 look = player.getViewVector(part);
		double len = Math.max(1.0E-3D, Math.sqrt(look.x * look.x + look.z * look.z));
		double base = Math.atan2(look.z / len, look.x / len);
		double gy = p.y + 0.35D;
		int beads = banyak > 0 ? 25 : 13;
		for (int i = 0; i < beads; i++) {
			float t = (float) i / (beads - 1);
			double a = base + (t - 0.5D) * Math.toRadians(120.0D);
			double x = p.x + Math.cos(a) * 1.4D;
			double z = p.z + Math.sin(a) * 1.4D;
			boolean band = Math.abs(t - centre) <= half;
			GabungFxClient.sprite(GabungFxClient.SPRITE, x, gy, z, band ? 0.075F : 0.05F, GabungFxClient.argb((band ? 0.85F : 0.35F) * vis, band ? JADE : 0x8090A8),
					GabungFxClient.SEL_GLOW, 0.0F);
		}
		double na = base + (needle - 0.5D) * Math.toRadians(120.0D);
		double nx = p.x + Math.cos(na) * 1.4D;
		double nz = p.z + Math.sin(na) * 1.4D;
		GabungFxClient.beam(GabungFxClient.CAHAYA, nx, gy - 0.15D, nz, nx, gy + 0.2D, nz, 0.07F, GabungFxClient.argb(0.95F * vis, inside ? WHITE : RED),
				GabungFxClient.argb(0.95F * vis, inside ? WHITE : RED));
		if (inside && holding && banyak > 0) {
			for (int i = 0; i < 3 * banyak; i++) {
				double t = (now * 0.08D + i * 0.29D) % 1.0D;
				double a = na + (i - 1.5D) * 0.15D;
				GabungFxClient.sprite(GabungFxClient.SPRITE, nx + Math.cos(a) * t * 0.4D, gy + t * 0.6D, nz + Math.sin(a) * t * 0.4D, 0.05F,
						GabungFxClient.argb((1.0F - (float) t) * vis, warna), GabungFxClient.SEL_PERCIK, 0.0F);
			}
		}
		// five orbs overhead: each pillar's accuracy
		int at = Math.min(4, b(1));
		for (int i = 0; i < 5; i++) {
			double a = now * 0.015D + i * Math.PI * 2.0D / 5.0D;
			float acc = f(7 + i);
			float alpha = i == at ? 0.9F : 0.25F + 0.6F * acc;
			int c = GabungFxClient.mix(0x404858, PILAR_WARNA[i], i == at ? 1.0F : 0.3F + 0.7F * acc);
			GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * 0.55D, p.y + 2.35D, p.z + Math.sin(a) * 0.55D, 0.07F + 0.05F * acc,
					GabungFxClient.argb(alpha * vis, c), GabungFxClient.SEL_GLOW, 0.0F);
		}
	}

	/** Wu Xing light colours in PilarQiEntity order (Logam, Kayu, Air, Api, Tanah), for the orbs of pillars not in view. */
	private static final int[] PILAR_WARNA = {0xE8ECF4, 0x7EE070, 0x5FA8FF, 0xFF6A30, 0xD8A850};

	private static void darah(Vec3 p, float vis, int banyak, double now) {
		if (b(6) == 1) {
			int n = 3 + banyak * 4;
			for (int i = 0; i < n; i++) {
				double a = i * 2.399D + now * 0.006D;
				double r = 0.8D + (i % 4) * 0.45D;
				GabungFxClient.sprite(GabungFxClient.LUNAK, p.x + Math.cos(a) * r, p.y + 0.2D + (i % 3) * 0.15D, p.z + Math.sin(a) * r, 0.55F,
						GabungFxClient.argb(0.3F * vis, 0x8A1018), GabungFxClient.SEL_KABUT, (float) (now * 0.004D + i));
			}
			int drops = 2 + banyak * 3;
			for (int i = 0; i < drops; i++) {
				double t = (now * 0.025D + i * 0.31D) % 1.0D;
				double a = i * 1.9D;
				GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * 0.35D, p.y + 0.4D + t * 2.2D, p.z + Math.sin(a) * 0.35D, 0.07F,
						GabungFxClient.argb((1.0F - (float) t) * 0.85F * vis, BLOOD), GabungFxClient.SEL_TETES, (float) Math.PI);
			}
			// the drain: a faint pulse every 40 ticks
			double pulse = (now % 40.0D) / 40.0D;
			GabungFxClient.datar(GabungFxClient.SPRITE, p.x, p.y + 0.05D, p.z, 0.5F + (float) pulse * 1.6F, GabungFxClient.argb((1.0F - (float) pulse) * 0.4F * vis,
					BLOOD), GabungFxClient.SEL_CINCIN, 0.0D);
		}
		if (b(5) > 0) {
			int n = 3 + banyak * 4;
			for (int i = 0; i < n; i++) {
				double t = (now * 0.05D + i * 0.23D) % 1.0D;
				double a = i * 2.399D;
				GabungFxClient.sprite(GabungFxClient.SPRITE, p.x + Math.cos(a) * 0.45D, p.y + 0.2D + t * 1.9D, p.z + Math.sin(a) * 0.45D, 0.18F * (1.0F - (float) t * 0.5F),
						GabungFxClient.argb((1.0F - (float) t) * 0.75F * vis, GabungFxClient.mix(RED, BLOOD, (float) t)), GabungFxClient.SEL_API, (float) (now * 0.1D + i));
			}
		}
		if (b(4) == 1) {
			float breath = 0.6F + 0.4F * (float) Math.sin(now * 0.12D);
			GabungFxClient.sprite(GabungFxClient.SPRITE, p.x, p.y + 2.5D, p.z, 0.28F, GabungFxClient.argb(0.75F * vis * breath, BLOOD), GabungFxClient.SEL_SEGEL, 0.0F);
		}
	}

	private static void racun(Vec3 p, float vis, int banyak, double now, float part) {
		if (b(2) == 1) {
			int n = 3 + banyak * 4;
			for (int i = 0; i < n; i++) {
				double a = i * 2.399D + now * 0.02D;
				double r = 0.5D + (i % 3) * 0.3D;
				int warna = i % 2 == 0 ? POISON : 0x6ACF5A;
				GabungFxClient.sprite(GabungFxClient.LUNAK, p.x + Math.cos(a) * r, p.y + 0.8D + (i % 4) * 0.3D, p.z + Math.sin(a) * r, 0.35F,
						GabungFxClient.argb(0.28F * vis, warna), GabungFxClient.SEL_ASAP, (float) (now * 0.01D + i));
			}
			float left = Math.min(1.0F, b(4) / 20.0F);
			int ring = GabungFxClient.mix(RED, JADE, left);
			GabungFxClient.datar(GabungFxClient.SPRITE, p.x, p.y + 0.06D, p.z, 0.3F + 1.3F * left, GabungFxClient.argb(0.6F * vis, ring), GabungFxClient.SEL_CINCIN,
					now * 0.02D);
		}
		for (int i = 0; i < 3; i++) {
			int r = b(5 + i);
			if (r == 0) {
				continue;
			}
			CawanUjianEntity cup = cawan(i);
			if (cup == null) {
				continue;
			}
			Vec3 c = cup.getPosition(part);
			double y = c.y + 0.9D + Math.sin(now * 0.08D + i) * 0.05D;
			if (r == 2) {
				GabungFxClient.sprite(GabungFxClient.SPRITE, c.x, y, c.z, 0.16F, GabungFxClient.argb(0.8F * vis, RED), GabungFxClient.SEL_RETAK, 0.0F);
			} else {
				GabungFxClient.sprite(GabungFxClient.SPRITE, c.x, y, c.z, 0.13F, GabungFxClient.argb(0.85F * vis, r == 1 ? JADE : GOLD), GabungFxClient.SEL_GLOW,
						0.0F);
			}
		}
	}

	// ================================================================== bursts

	private static void drawLedak(int banyak) {
		long tk = GabungFxClient.ticks();
		double now = GabungFxClient.now;
		for (int i = 0; i < LEDAK_MAKS; i++) {
			if (LL[i] <= 0) {
				continue;
			}
			double age = now - LT[i];
			if (age < 0.0D || tk - LT[i] >= LL[i]) {
				LL[i] = 0;
				continue;
			}
			if (GabungFxClient.jarak(LX[i], LY[i], LZ[i]) > JANGKAU) {
				continue;
			}
			float t = (float) Math.min(1.0D, age / LL[i]);
			float fade = 1.0F - t;
			int c = LC[i];
			float s = LS[i];
			double x = LX[i];
			double y = LY[i];
			double z = LZ[i];
			switch (LK[i]) {
				case K_CINCIN -> GabungFxClient.datar(GabungFxClient.SPRITE, x, y, z, 0.3F + s * GabungFxClient.smooth(t), GabungFxClient.argb(0.8F * fade, c),
						GabungFxClient.SEL_CINCIN, t * 0.8D);
				case K_PERCIK -> {
					int n = 6 + banyak * 5;
					for (int k = 0; k < n; k++) {
						double a = k * 2.399D + i;
						double el = (k % 5 - 2) * 0.35D;
						double r = s * t * 1.4D;
						GabungFxClient.sprite(GabungFxClient.SPRITE, x + Math.cos(a) * Math.cos(el) * r, y + Math.sin(el) * r - t * t * 0.5D, z + Math.sin(a) * Math.cos(el) * r,
								0.06F + 0.04F * fade, GabungFxClient.argb(fade, c), GabungFxClient.SEL_PERCIK, (float) a);
					}
				}
				case K_TEBAS -> {
					float roll = (i % 2 == 0 ? 0.6F : -0.6F);
					GabungFxClient.sprite(GabungFxClient.SPRITE, x, y, z, s * (0.6F + 0.6F * t), GabungFxClient.argb(0.95F * fade, c), GabungFxClient.SEL_TEBASAN, roll);
					GabungFxClient.sprite(GabungFxClient.SPRITE, x, y, z, s * (0.5F + 0.5F * t), GabungFxClient.argb(0.7F * fade, GOLD), GabungFxClient.SEL_TEBASAN,
							roll + (float) Math.PI * 0.5F);
				}
				case K_RETAK -> GabungFxClient.datar(GabungFxClient.SPRITE, x, y, z, s * (0.7F + 0.3F * GabungFxClient.smooth(t * 4.0F)),
						GabungFxClient.argb(0.85F * fade, c), GabungFxClient.SEL_RETAK, i * 0.7D);
				case K_ASAP -> {
					int n = 4 + banyak * 3;
					for (int k = 0; k < n; k++) {
						double a = k * 2.399D + i;
						double r = s * (0.3D + t);
						GabungFxClient.sprite(GabungFxClient.LUNAK, x + Math.cos(a) * r, y + t * 0.8D + (k % 3) * 0.15D, z + Math.sin(a) * r, 0.3F + 0.5F * t,
								GabungFxClient.argb(0.5F * fade, c), GabungFxClient.SEL_ASAP, (float) (a + t));
					}
				}
				case K_BOLA -> {
					float grow = GabungFxClient.smooth(t * 3.0F);
					GabungFxClient.sprite(GabungFxClient.LUNAK, x, y + t * 0.4D, z, s * (0.8F + grow), GabungFxClient.argb(0.45F * fade, c), GabungFxClient.SEL_GLOW, 0.0F);
					GabungFxClient.sprite(GabungFxClient.SPRITE, x, y + t * 0.4D, z, s * 0.35F, GabungFxClient.argb(fade, GabungFxClient.mix(c, WHITE, 0.4F)),
							GabungFxClient.SEL_GLOW, 0.0F);
				}
				case K_TIANG -> {
					float h = 2.0F + 6.0F * GabungFxClient.smooth(t * 2.0F);
					GabungFxClient.beam(GabungFxClient.CAHAYA, x, y, z, x, y + h, z, s, GabungFxClient.argb(0.8F * fade, c), GabungFxClient.argb(0.0F, c));
					if (t < 0.1F && EpicBudget.takePillar()) {
						GabungFxClient.pilar(x, y, z, GabungFxClient.argb(0.8F, c));
					}
				}
				case K_DAUN -> {
					int n = 6 + banyak * 6;
					for (int k = 0; k < n; k++) {
						double a = k * 2.399D + t * 6.0D;
						double r = s * (0.4D + t * 0.8D);
						GabungFxClient.sprite(GabungFxClient.SPRITE, x + Math.cos(a) * r, y + t * 2.5D + (k % 4) * 0.2D, z + Math.sin(a) * r, 0.12F,
								GabungFxClient.argb(fade, c), GabungFxClient.SEL_DAUN, (float) a);
					}
				}
				case K_MATA -> {
					float open = t < 0.2F ? t / 0.2F : 1.0F;
					GabungFxClient.sprite(GabungFxClient.SPRITE, x, y, z, s * 0.6F * open, GabungFxClient.argb(fade, c), GabungFxClient.SEL_MATA, 0.0F);
					GabungFxClient.sprite(GabungFxClient.LUNAK, x, y, z, s * 1.4F, GabungFxClient.argb(0.35F * fade, c), GabungFxClient.SEL_GLOW, 0.0F);
				}
				default -> {
				}
			}
		}
	}

	private UjianFx() {
	}
}
