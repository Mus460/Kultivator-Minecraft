package dev.kultivator.client.dunia.tempat.kotabesar;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.tempat.model.HiasanDunia;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.camera.SkipKey;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarPayload;
import dev.kultivator.fx.QiParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client state of the capital's pictures ({@link KotaBesarPayload}): it turns them into 38MD's world decorations
 * ({@link HiasanDunia} groups, which draw lanterns, sky lanterns, fireworks, banners and incense smoke as layered
 * camera-relative quads under the 7b frame budget) and into its own small effects: the Formation Pillar of a teleport
 * (rising {@code kultivator:qi} motes, a jade vignette for the traveller, the arrival flash through the shared luminance
 * gate), the cloud-boat wipe of the Sky Dock (skippable with the 7b skip key), and the defence bar data read by
 * {@link KotaBesarHud}. Every count scales with {@link EffectQuality}; nothing is stored and nothing touches the world.
 *
 * <p>Groups: {@code kb:<town>:festival} (street lanterns on the {@code jalan_lentera} hooks, sky lantern launches on the
 * Great Square), {@code kb:<town>:kembang} (the firework window 15000-16000, opened by each {@code kembang} picture for 20 s),
 * {@code kb:<town>:pasar} (night market stall lanterns), {@code kb:<town>:panji} (tournament banners around the lei tai),
 * {@code kb:dupa:<x>:<z>} (incense over the Temple of Heaven's burner, kept 15 s after the last refresh).
 */
public final class KotaBesarFx {

	private static final int[] WARNA_LENTERA = {0xFFFFFF, 0xFFE0A0, 0xFFC0B0};
	private static final int[] TIANG_MOTE = {2, 5, 10};

	/** A running teleport pillar (world position, end time, kind, own). */
	private record Pilar(double x, double y, double z, long sampai, boolean awan, boolean milikku) {
	}

	private static final List<Pilar> PILAR = new ArrayList<>();
	private static final Map<String, Long> SAMPAI = new HashMap<>();
	private static boolean awanBerjalan;

	// defence bar (KotaBesarHud)
	static boolean pertahanan;
	static int gelombang;
	static int total;
	static int sisa;
	static boolean roboh;
	static float gerbang = 1.0F;
	static double gerbangX;
	static double gerbangZ;
	static long pertahananT;

	// teleport channel ring (KotaBesarHud)
	static long kanalMulai;
	static long kanalSampai;
	static boolean kanalAwan;

	private KotaBesarFx() {
	}

	static void bersihkan() {
		PILAR.clear();
		for (String key : List.copyOf(SAMPAI.keySet())) {
			HiasanDunia.hapus(key);
		}
		SAMPAI.clear();
		pertahanan = false;
		kanalSampai = 0L;
		awanBerjalan = false;
	}

	private static List<Vec3> titik(int[] flat) {
		List<Vec3> out = new ArrayList<>();
		for (int i = 0; i + 2 < flat.length; i += 3) {
			out.add(new Vec3(flat[i] + 0.5D, flat[i + 1], flat[i + 2] + 0.5D));
		}
		return out;
	}

	/** One picture from the server (render thread, via the receiver). */
	static void terima(KotaBesarPayload payload) {
		CompoundTag d = payload.data();
		switch (payload.jenis()) {
			case "festival" -> festival(d);
			case "kembang" -> kembang(d);
			case "pasar_malam" -> pasarMalam(d);
			case "panji" -> panji(d);
			case "dupa" -> dupa(d);
			case "teleport" -> teleport(d);
			case "pertahanan" -> pertahananData(d);
			default -> {
			}
		}
	}

	private static void festival(CompoundTag d) {
		String key = "kb:" + d.getLongOr("town", 0L) + ":festival";
		if (!d.getBooleanOr("aktif", false)) {
			HiasanDunia.festival(key, false);
			HiasanDunia.hapus(key);
			SAMPAI.remove(key);
			return;
		}
		List<HiasanDunia.Hias> isi = new ArrayList<>();
		int i = 0;
		for (Vec3 hook : titik(d.getIntArray("lentera").orElse(new int[0]))) {
			isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_JALAN, hook.x, hook.y - 0.6D, hook.z, 0.0F, WARNA_LENTERA[i++ % WARNA_LENTERA.length], 0.0F));
		}
		double cx = d.getDoubleOr("x", 0.0D);
		double cz = d.getDoubleOr("z", 0.0D);
		double y = d.getIntOr("y", 64);
		// the Great Square and the three plazas around it send the sky lanterns up
		isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_LANGIT, cx, y + 1.0D, cz, 0.0F, 0xFFFFFF, 0.0F));
		isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_LANGIT, cx + 22.0D, y + 1.0D, cz - 18.0D, 0.0F, 0xFFE8C0, 0.0F));
		isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_LANGIT, cx - 24.0D, y + 1.0D, cz + 16.0D, 0.0F, 0xFFD0A8, 0.0F));
		if (!EffectQuality.minimal()) {
			isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_LANGIT, cx + 6.0D, y + 1.0D, cz + 140.0D, 0.0F, 0xFFFFFF, 0.0F));
		}
		HiasanDunia.pasang(key, isi);
		HiasanDunia.festival(key, true);
		SAMPAI.put(key, Long.MAX_VALUE);
	}

	private static void kembang(CompoundTag d) {
		String key = "kb:" + d.getLongOr("town", 0L) + ":kembang";
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (!SAMPAI.containsKey(key)) {
			double x = d.getDoubleOr("x", 0.0D);
			double z = d.getDoubleOr("z", 0.0D);
			double ground = d.getDoubleOr("y", 64.0D) - 36.0D;
			HiasanDunia.pasang(key, List.of(new HiasanDunia.Hias(HiasanDunia.Jenis.KEMBANG_API, x, ground, z, 0.0F, 0xFFFFFF, 0.0F)));
		}
		HiasanDunia.festival(key, true);
		SAMPAI.put(key, level.getGameTime() + 420L);
	}

	private static void pasarMalam(CompoundTag d) {
		String key = "kb:" + d.getLongOr("town", 0L) + ":pasar";
		if (!d.getBooleanOr("aktif", false)) {
			HiasanDunia.hapus(key);
			SAMPAI.remove(key);
			return;
		}
		List<HiasanDunia.Hias> isi = new ArrayList<>();
		int i = 0;
		for (Vec3 stall : titik(d.getIntArray("lapak").orElse(new int[0]))) {
			isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.LENTERA_JALAN, stall.x, stall.y + 2.6D, stall.z, 0.0F, WARNA_LENTERA[i++ % WARNA_LENTERA.length], 0.0F));
		}
		HiasanDunia.pasang(key, isi);
		SAMPAI.put(key, Long.MAX_VALUE);
	}

	private static void panji(CompoundTag d) {
		String key = "kb:" + d.getLongOr("town", 0L) + ":panji";
		if (!d.getBooleanOr("aktif", false)) {
			HiasanDunia.hapus(key);
			SAMPAI.remove(key);
			return;
		}
		int[] colours = d.getIntArray("warna").orElse(new int[0]);
		double cx = d.getDoubleOr("x", 0.0D);
		double cz = d.getDoubleOr("z", 0.0D);
		double y = d.getIntOr("y", 64) - 2.0D;
		List<HiasanDunia.Hias> isi = new ArrayList<>();
		int n = Math.max(1, colours.length);
		for (int i = 0; i < n; i++) {
			float ang = Mth.TWO_PI * i / n + Mth.PI / n;
			double px = cx + Mth.cos(ang) * 15.5D;
			double pz = cz + Mth.sin(ang) * 15.5D;
			float yaw = (float) Math.toDegrees(Math.atan2(cx - px, pz - cz));
			int colour = colours.length == 0 ? 0xC83030 : colours[i];
			isi.add(new HiasanDunia.Hias(HiasanDunia.Jenis.PANJI, px, y, pz, yaw, colour, 6.0F));
		}
		HiasanDunia.pasang(key, isi);
		SAMPAI.put(key, Long.MAX_VALUE);
	}

	private static void dupa(CompoundTag d) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		double x = d.getDoubleOr("x", 0.0D);
		double y = d.getDoubleOr("y", 64.0D);
		double z = d.getDoubleOr("z", 0.0D);
		String key = "kb:dupa:" + Mth.floor(x) + ":" + Mth.floor(z);
		if (!SAMPAI.containsKey(key)) {
			HiasanDunia.pasang(key, List.of(new HiasanDunia.Hias(HiasanDunia.Jenis.ASAP_DUPA, x, y, z, 0.0F, 0xFFFFFF, 0.0F),
					new HiasanDunia.Hias(HiasanDunia.Jenis.ASAP_DUPA, x + 0.6D, y, z - 0.4D, 0.0F, 0xF0E8E0, 0.0F),
					new HiasanDunia.Hias(HiasanDunia.Jenis.ASAP_DUPA, x - 0.5D, y, z + 0.5D, 0.0F, 0xE8E8F0, 0.0F)));
		}
		SAMPAI.put(key, level.getGameTime() + 300L);
	}

	private static void teleport(CompoundTag d) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		boolean mine = minecraft.player != null && minecraft.player.getId() == d.getIntOr("pemain", -1);
		boolean awan = "awan".equals(d.getStringOr("jenis", "cakram"));
		double x = d.getDoubleOr("x", 0.0D);
		double y = d.getDoubleOr("y", 0.0D);
		double z = d.getDoubleOr("z", 0.0D);
		if (d.getBooleanOr("tiba", false)) {
			burst(level, x, y, z, awan);
			if (mine) {
				EpicScreen.flash(awan ? 0xE8F4FF : 0xBFFFEA, EffectQuality.minimal() ? 0.18F : 0.28F, 6, 0);
				if (awanBerjalan) {
					CloudWipe.release(16);
					awanBerjalan = false;
				}
				kanalSampai = 0L;
			}
			return;
		}
		long ticks = Math.max(20L, d.getIntOr("detik", 3) * 20L);
		PILAR.add(new Pilar(x, y, z, level.getGameTime() + ticks, awan, mine));
		if (mine) {
			kanalMulai = level.getGameTime();
			kanalSampai = level.getGameTime() + ticks;
			kanalAwan = awan;
			EpicScreen.vignette(awan ? 0xD8ECFF : 0x5FE8C0, 0.18F, (int) ticks);
			if (awan) {
				// the cloud boat: clouds close in, hold while the boat rises above them, open on the arrival flash
				awanBerjalan = CloudWipe.start(24, (int) Math.max(10L, ticks - 34L), 20, 0xFFFFFF);
			}
		}
	}

	private static void pertahananData(CompoundTag d) {
		pertahanan = d.getBooleanOr("aktif", false);
		gelombang = d.getIntOr("gelombang", 0);
		total = d.getIntOr("total", 3);
		sisa = d.getIntOr("sisa", 0);
		roboh = d.getBooleanOr("roboh", false);
		gerbang = d.getFloatOr("gerbang", 1.0F);
		gerbangX = d.getDoubleOr("x", 0.0D);
		gerbangZ = d.getDoubleOr("z", 0.0D);
		ClientLevel level = Minecraft.getInstance().level;
		pertahananT = level == null ? 0L : level.getGameTime();
	}

	// ================================================================== tick

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<String, Long>> it = SAMPAI.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Long> e = it.next();
			if (now >= e.getValue()) {
				if (e.getKey().endsWith(":kembang")) {
					HiasanDunia.festival(e.getKey(), false);
				}
				HiasanDunia.hapus(e.getKey());
				it.remove();
			}
		}
		if (pertahanan && now - pertahananT > 100L) {
			pertahanan = false;
		}
		if (awanBerjalan && SkipKey.SKIP.isDown()) {
			CloudWipe.release(8);
			awanBerjalan = false;
		}
		if (kanalSampai != 0L && now > kanalSampai + 40L) {
			kanalSampai = 0L;
		}
		if (PILAR.isEmpty()) {
			return;
		}
		int tier = Math.max(0, EpicBudget.tierOrMinimal());
		RandomSource r = level.getRandom();
		for (Iterator<Pilar> it = PILAR.iterator(); it.hasNext(); ) {
			Pilar p = it.next();
			if (now >= p.sampai()) {
				it.remove();
				continue;
			}
			if (minecraft.player != null && minecraft.player.distanceToSqr(p.x(), p.y(), p.z()) > 64.0D * 64.0D) {
				continue;
			}
			int motes = TIANG_MOTE[Math.min(2, tier)];
			float spin = (now % 40L) / 40.0F * Mth.TWO_PI;
			for (int i = 0; i < motes; i++) {
				float ang = spin + Mth.TWO_PI * i / motes;
				double radius = 1.1D + r.nextDouble() * 0.4D;
				double px = p.x() + Mth.cos(ang) * radius;
				double pz = p.z() + Mth.sin(ang) * radius;
				double py = p.y() + r.nextDouble() * 0.4D;
				int core = p.awan() ? 0xFFFFFF : 0xE8FFF6;
				int body = p.awan() ? 0xBFDFFF : 0x4FE0B0;
				int rim = p.awan() ? 0x7FA8E0 : 0x1E8C6E;
				level.addParticle(new QiParticleOptions(core, body, rim, 0.18F, 0.05F, 26 + r.nextInt(10), (float) (-Mth.sin(ang) * 0.03D),
						0.16F + r.nextFloat() * 0.06F, (float) (Mth.cos(ang) * 0.03D),
						(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.FADE_IN)), true, false, px, py, pz, 0.0D, 0.0D, 0.0D);
			}
			if (tier >= 1 && now % 4L == 0L) {
				// the trigram ring on the disc
				for (int i = 0; i < 8; i++) {
					float ang = Mth.TWO_PI * i / 8.0F - spin * 0.5F;
					level.addParticle(new QiParticleOptions(0xFFF6D0, 0xFFD060, 0xB07A20, 0.14F, 0.0F, 12, 0.0F, 0.0F, 0.0F,
							(byte) (QiParticleOptions.GLOW | QiParticleOptions.NO_GRAVITY | QiParticleOptions.GROUND_HUG)), true, false,
							p.x() + Mth.cos(ang) * 3.0D, p.y() + 0.05D, p.z() + Mth.sin(ang) * 3.0D, 0.0D, 0.0D, 0.0D);
				}
			}
		}
	}

	private static void burst(ClientLevel level, double x, double y, double z, boolean awan) {
		int n = EffectQuality.scale(24);
		RandomSource r = level.getRandom();
		for (int i = 0; i < n; i++) {
			double vx = (r.nextDouble() - 0.5D) * 0.4D;
			double vy = r.nextDouble() * 0.25D;
			double vz = (r.nextDouble() - 0.5D) * 0.4D;
			level.addParticle(new QiParticleOptions(0xFFFFFF, awan ? 0xCFE6FF : 0x7FF0C8, awan ? 0x8AB0E8 : 0x2A9C7C, 0.22F, 0.02F, 20 + r.nextInt(12),
					(float) vx, (float) vy, (float) vz, (byte) (QiParticleOptions.GLOW | QiParticleOptions.DRAG | QiParticleOptions.NO_GRAVITY)), true, false,
					x, y + 0.8D, z, 0.0D, 0.0D, 0.0D);
		}
	}
}
