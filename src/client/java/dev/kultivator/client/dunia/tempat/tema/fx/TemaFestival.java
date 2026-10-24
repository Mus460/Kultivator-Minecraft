package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/**
 * The festivals of the themed towns as this client knows them ({@code TemaNet.FESTIVAL}: {@code acara}, {@code mulai},
 * {@code tick}, {@code akhir}), routing each to its effect and announcing a start with the HUD banner ({@link TemaHud}):
 * <ul>
 * <li>{@code lentera_air} -> {@link LenteraAirFx} (lanterns on the canals until {@code akhir});</li>
 * <li>{@code lentera_lepas} ({@code x, y, z}, {@code harapan} only to its owner) -> one lantern and the wish caption;</li>
 * <li>{@code mural} / {@code mural_bergerak} -> {@link MuralBillboard} (no banner for the plain mural list);</li>
 * <li>{@code pesta_darah} -> {@link RiakDarahFx#pesta} (the blood pool beats faster);</li>
 * <li>every other id ({@code dewi_laut}, {@code tari_putar}, {@code perahu_naga}, {@code ziarah}, {@code kafilah_agung},
 * {@code lelang_gelap}, {@code cahaya_budha}, ...) -> the banner, and {@link #aktif(String)} for other effects.</li>
 * </ul>
 * A festival without {@code akhir} lasts 6000 ticks. Nothing here changes the world.
 */
final class TemaFestival {

	static final long PANJANG_BAKU = 6000L;

	private record Acara(long mulai, long akhir) {
	}

	/** site -> acara -> window. */
	private static final Map<Long, Map<String, Acara>> AKTIF = new HashMap<>();

	private TemaFestival() {
	}

	static void terima(long situs, CompoundTag d) {
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
		String acara = d.getStringOr("acara", "");
		if (acara.isEmpty()) {
			return;
		}
		boolean mulai = d.getBooleanOr("mulai", true);
		switch (acara) {
			case "mural" -> {
				MuralBillboard.terima(d);
				return;
			}
			case "mural_bergerak" -> {
				MuralBillboard.bergerak(d, now);
				if (mulai && !sudah(situs, acara)) {
					TemaHud.spanduk(acara);
				}
				catat(situs, acara, mulai, now, now + 1500L);
				return;
			}
			case "lentera_lepas" -> {
				String harapan = d.getStringOr("harapan", "");
				LenteraAirFx.lepas(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0), d.getIntOr("z", 0) + 0.5D, !harapan.isEmpty());
				if (!harapan.isEmpty()) {
					TemaHud.harapan(harapan.length() > 32 ? harapan.substring(0, 32) : harapan);
				}
				return;
			}
			default -> {
			}
		}
		long tick = d.getLongOr("tick", now);
		long akhir = d.getLongOr("akhir", tick + PANJANG_BAKU);
		boolean baru = mulai && !sudah(situs, acara);
		catat(situs, acara, mulai, tick, akhir);
		if ("lentera_air".equals(acara)) {
			if (mulai) {
				LenteraAirFx.mulai(situs, akhir);
			} else {
				LenteraAirFx.akhir(situs);
			}
		} else if ("pesta_darah".equals(acara)) {
			RiakDarahFx.pesta(mulai);
		}
		if (baru && now < akhir) {
			TemaHud.spanduk(acara);
		}
	}

	private static boolean sudah(long situs, String acara) {
		Map<String, Acara> m = AKTIF.get(situs);
		return m != null && m.containsKey(acara);
	}

	private static void catat(long situs, String acara, boolean mulai, long dari, long akhir) {
		if (mulai) {
			AKTIF.computeIfAbsent(situs, k -> new HashMap<>()).put(acara, new Acara(dari, akhir));
		} else {
			Map<String, Acara> m = AKTIF.get(situs);
			if (m != null) {
				m.remove(acara);
				if (m.isEmpty()) {
					AKTIF.remove(situs);
				}
			}
		}
	}

	/** True while a festival {@code acara} runs in any known town. */
	static boolean aktif(String acara) {
		for (Map<String, Acara> m : AKTIF.values()) {
			if (m.containsKey(acara)) {
				return true;
			}
		}
		return false;
	}

	/** How many festivals this client knows to be running (the {@code /temafx info} report). */
	static int jumlah() {
		int n = 0;
		for (Map<String, Acara> m : AKTIF.values()) {
			n += m.size();
		}
		return n;
	}

	static void tick(Minecraft minecraft, long now) {
		if (AKTIF.isEmpty()) {
			return;
		}
		AKTIF.values().forEach(m -> m.values().removeIf(a -> a.akhir() < now));
		AKTIF.values().removeIf(Map::isEmpty);
		if (!aktif("pesta_darah")) {
			RiakDarahFx.pesta(false);
		}
	}

	static void bersihkan() {
		AKTIF.clear();
	}
}
