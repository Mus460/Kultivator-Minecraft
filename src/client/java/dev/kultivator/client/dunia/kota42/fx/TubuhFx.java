package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.screen.SettingsRows;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.acara.BarongsaiEntity;
import dev.kultivator.dunia.kota42.acara.KalenderKota;
import dev.kultivator.dunia.kota42.acara.MusimKota;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the bodies and the ceremonies of the town sound and shed, read from what the client already knows (DESIGN 42.12.6): the
 * clip a townsperson plays ({@code SosokApi.gerakSekarang}, synced) and the running ceremonies of {@code kota_upacara}. Every
 * five ticks the bodies within 48 blocks are looked at:
 * <ul>
 * <li>{@code main_erhu}: the busker's tune ({@code jembatan}, {@code bulan_sabit} or {@code perahu_pulang}, picked by the body and
 * the day), at the body, repeating after a pause for as long as the clip lasts;</li>
 * <li>{@code pukul_gong}: one strike of the night watchman's gong when the clip starts; {@code pukul_kentongan}: the {@code ronda}
 * knocks; {@code tiup_suona}: the suona phrase of the wedding tune from a strolling musician;</li>
 * <li>{@code tabur_uang_kertas} and the yangge dance: joss paper from the steward's hands and red scraps from the dancers.</li>
 * </ul>
 * Every second the ceremonies and places are looked at: a wedding procession plays {@code arak_pengantin} (suona, small drum,
 * gong, wood block) at the middle of its participants, a funeral {@code ratap_pagi}; a lion dance ({@code BarongsaiEntity}) the
 * {@code barongsai} drums; and in a place whose night district is open (or on a festival evening) a pipa, erhu or dizi tune comes
 * from a lantern 12-42 blocks away that the client picks the same way for ten minutes. All music is {@link MelodiJalan}; it is
 * silent at "Musik jalanan 0 %". Client thread only.
 */
final class TubuhFx {

	private static final String[] ERHU = {"jembatan", "bulan_sabit", "perahu_pulang"};
	private static final String[] MALAM = {"gembira_pasar", "pesta_lentera", "malam_teh"};

	private static final Map<Integer, Integer> KLIP = new HashMap<>();
	private static int detak;

	static void bersihkan() {
		KLIP.clear();
	}

	static void tik() {
		var level = Bingkai.level;
		var pemain = Bingkai.pemain;
		if (level == null || pemain == null) {
			return;
		}
		detak++;
		if (detak % 5 == 0) {
			tubuh(level, pemain.position());
		}
		if (detak % 20 == 10 && SuaraKota42.musik() > 0.0F && SettingsRows.kota42Melodi() > 0) {
			upacara(level);
			singa(level, pemain.position());
			malam(level, pemain.position());
		}
	}

	// ================================================================== bodies

	private static void tubuh(net.minecraft.client.multiplayer.ClientLevel level, Vec3 p) {
		List<KultivatorNpc> daftar = level.getEntitiesOfClass(KultivatorNpc.class, new AABB(p, p).inflate(48.0D, 24.0D, 48.0D), KultivatorNpc::isAlive);
		Set<Integer> ada = new HashSet<>();
		RandomSource rnd = level.getRandom();
		for (KultivatorNpc npc : daftar) {
			int id = npc.getId();
			ada.add(id);
			int klip = SosokApi.gerakSekarang(npc);
			Integer lama = KLIP.put(id, klip);
			boolean baru = lama == null || lama != klip;
			switch (klip) {
				case KotaGerakId.MAIN_ERHU -> {
					if (SuaraKota42.musik() > 0.0F && !MelodiJalan.main("erhu:" + id)) {
						long hari = Bingkai.hari;
						MelodiJalan.mulai("erhu:" + id, ERHU[(int) Math.floorMod(id + hari, (long) ERHU.length)], bagian(level, id, KotaGerakId.MAIN_ERHU, 1.25D), 1.0F, true, 140, 340);
					}
				}
				case KotaGerakId.PUKUL_GONG -> {
					if (baru) {
						SuaraKota42.suara(level, npc.getX(), npc.getY() + 1.2D, npc.getZ(), SuaraKota42.acara("kota42.gong.1"), 1.1F, 0.97F + rnd.nextFloat() * 0.06F);
					}
				}
				case KotaGerakId.PUKUL_KENTONGAN -> {
					if (baru && SuaraKota42.musik() > 0.0F) {
						MelodiJalan.mulai("ronda:" + id, "ronda", bagian(level, id, KotaGerakId.PUKUL_KENTONGAN, 1.1D), 1.0F, false, 0, 0);
					}
				}
				case KotaGerakId.TIUP_SUONA -> {
					if (SuaraKota42.musik() > 0.0F && !MelodiJalan.main("suona:" + id) && tidakAdaUpacara(npc.position())) {
						MelodiJalan.mulai("suona:" + id, "arak_pengantin", bagian(level, id, KotaGerakId.TIUP_SUONA, 1.3D), 0.9F, true, 80, 200);
					}
				}
				case KotaGerakId.TABUR_UANG_KERTAS -> {
					if (Bingkai.tier > 0) {
						float yaw = npc.yBodyRot * Mth.DEG_TO_RAD;
						float fx = -Mth.sin(yaw);
						float fz = Mth.cos(yaw);
						int n = 1 + rnd.nextInt(2);
						for (int i = 0; i < n; i++) {
							PartikelKota.uang(rnd, (float) (npc.getX() + fx * 0.45D), (float) (npc.getY() + 1.35D), (float) (npc.getZ() + fz * 0.45D), fx * 0.05F + (rnd.nextFloat() - 0.5F) * 0.05F,
									0.07F + rnd.nextFloat() * 0.05F, fz * 0.05F + (rnd.nextFloat() - 0.5F) * 0.05F);
						}
					}
				}
				case KotaGerakId.TARI_YANGGE -> {
					if (Bingkai.tier > 0 && rnd.nextInt(3) == 0) {
						PartikelKota.kertasMerah(rnd, (float) npc.getX(), (float) (npc.getY() + 1.6D), (float) npc.getZ(), (rnd.nextFloat() - 0.5F) * 0.06F, 0.04F,
								(rnd.nextFloat() - 0.5F) * 0.06F);
					}
				}
				default -> {
				}
			}
		}
		KLIP.keySet().retainAll(ada);
	}

	/** The point of the body {@code id} while it still plays {@code klip}: the emitter ends when the clip or the body is gone. */
	private static java.util.function.Supplier<@Nullable Vec3> bagian(net.minecraft.client.multiplayer.ClientLevel level, int id, int klip, double tinggi) {
		return () -> {
			Entity e = level.getEntity(id);
			if (!(e instanceof KultivatorNpc npc) || !npc.isAlive() || SosokApi.gerakSekarang(npc) != klip) {
				return null;
			}
			return npc.position().add(0.0D, tinggi, 0.0D);
		};
	}

	/** No wedding or funeral procession plays within 14 blocks of {@code at}. */
	private static boolean tidakAdaUpacara(Vec3 at) {
		for (KotaPayloads.Upacara u : KotaHidupClient.upacara()) {
			Vec3 c = pusatUpacara(u);
			if (c != null && c.distanceToSqr(at) < 14.0D * 14.0D) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== ceremonies, lion dance, night district

	/** The middle of the participants of a ceremony that exist on this client, else its first path node, else null. */
	static @Nullable Vec3 pusatUpacara(KotaPayloads.Upacara u) {
		var level = Bingkai.level;
		if (level == null) {
			return null;
		}
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		int n = 0;
		for (int id : u.peserta()) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				x += e.getX();
				y += e.getY();
				z += e.getZ();
				n++;
			}
		}
		if (n > 0) {
			return new Vec3(x / n, y / n + 1.4D, z / n);
		}
		if (u.simpul().length > 0) {
			BlockPos b = BlockPos.of(u.simpul()[0]);
			return new Vec3(b.getX() + 0.5D, b.getY() + 1.5D, b.getZ() + 0.5D);
		}
		return null;
	}

	private static void upacara(net.minecraft.client.multiplayer.ClientLevel level) {
		for (KotaPayloads.Upacara u : KotaHidupClient.upacara()) {
			String lagu = u.jenis() == JenisUpacara.NIKAH.ordinal() ? "arak_pengantin" : u.jenis() == JenisUpacara.MAKAM.ordinal() ? "ratap_pagi" : null;
			if (lagu == null || u.fase() < 0) {
				continue;
			}
			int id = u.id();
			String kunci = "upacara:" + id;
			if (!MelodiJalan.main(kunci)) {
				MelodiJalan.mulai(kunci, lagu, () -> {
					for (KotaPayloads.Upacara k : KotaHidupClient.upacara()) {
						if (k.id() == id && k.fase() >= 0) {
							return pusatUpacara(k);
						}
					}
					return null;
				}, 1.15F, true, 60, 140);
			}
		}
	}

	private static void singa(net.minecraft.client.multiplayer.ClientLevel level, Vec3 p) {
		for (BarongsaiEntity b : level.getEntitiesOfClass(BarongsaiEntity.class, new AABB(p, p).inflate(48.0D, 24.0D, 48.0D), BarongsaiEntity::isAlive)) {
			int id = b.getId();
			String kunci = "barongsai:" + id;
			if (!MelodiJalan.main(kunci)) {
				MelodiJalan.mulai(kunci, "barongsai", () -> {
					Entity e = level.getEntity(id);
					return e == null || !e.isAlive() ? null : e.position().add(0.0D, 1.0D, 0.0D);
				}, 1.2F, true, 20, 80);
			}
		}
	}

	private static void malam(net.minecraft.client.multiplayer.ClientLevel level, Vec3 p) {
		if (!Bingkai.antara(18.0D, 2.0D)) {
			return;
		}
		for (KotaPayloads.Suasana s : KotaHidupClient.semuaSuasana()) {
			boolean pesta = (s.acara() & KalenderKota.BIT_DISTRIK_MALAM) != 0
					|| s.hariRaya() != MusimKota.HR_TIDAK && s.hariRaya() != MusimKota.HR_QINGMING && Bingkai.antara(18.0D, 23.0D);
			if (!pesta || (s.acara() & KalenderKota.BIT_MAKAM) != 0) {
				continue;
			}
			String kunci = "malam:" + s.situs();
			if (MelodiJalan.main(kunci)) {
				continue;
			}
			long jendela = Bingkai.waktu / 12000L;
			TitikPeta.Lentera pilih = null;
			int hitung = 0;
			for (TitikPeta.Lentera l : TitikPeta.lentera) {
				if (l.situs() != s.situs()) {
					continue;
				}
				double d2 = (l.x() - p.x) * (l.x() - p.x) + (l.z() - p.z) * (l.z() - p.z);
				if (d2 < 12.0D * 12.0D || d2 > 42.0D * 42.0D) {
					continue;
				}
				hitung++;
				if (Math.floorMod(TitikPeta.mix(l.hash(), (int) jendela, 7), hitung) == 0) {
					pilih = l;
				}
			}
			if (pilih == null) {
				continue;
			}
			final Vec3 at = new Vec3(pilih.x(), pilih.y() + 0.6D, pilih.z());
			String lagu = MALAM[(int) Math.floorMod(jendela + s.situs() + pilih.hash(), (long) MALAM.length)];
			MelodiJalan.mulai(kunci, lagu, () -> at, 1.0F, true, 100, 320);
		}
	}

	private TubuhFx() {
	}
}
