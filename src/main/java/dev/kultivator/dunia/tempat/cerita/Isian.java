package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.sekte.api.SekteApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Format arguments of story lines ({@code isian} of a dialog node or rumour, DESIGN 38.S.1 / 38.S.4). Each name resolves to
 * a component for one player and place:
 * <ul>
 * <li>{@code pemain}: the player's name; {@code kota}: the name of the place of the talk (town name);</li>
 * <li>{@code sekte_terdekat}, {@code sekte_lurus}, {@code sekte_iblis}: the nearest NPC sect (any / righteous / demonic,
 * from the single sect list of {@code DiplomasiService}); falls back to a generic "a sect in the mountains";</li>
 * <li>{@code tokoh:<id>}: a recurring character's name at this place; {@code penguasa}: the capital's ruler (38.S.3);</li>
 * <li>{@code nb_pita:<k>}: {@code k × (band + 1)} Nilai Batu as money text (band = realm band of the player);</li>
 * <li>{@code nb:<n>}: n Nilai Batu as money text; {@code sisa_bab}: days left in the current capital chapter;</li>
 * <li>{@code sekte:<id>}: that NPC sect's name (rumours store it so the name is translated by the listener's client);</li>
 * <li>anything else: the literal word (kept for templates filled by other modules).</li>
 * </ul>
 */
public final class Isian {

	public static Object[] resolve(ServerPlayer player, long situs, List<String> names) {
		Object[] out = new Object[names.size()];
		for (int i = 0; i < names.size(); i++) {
			out[i] = one(player, situs, names.get(i));
		}
		return out;
	}

	public static int pita(ServerPlayer player) {
		return Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, Harga.BANDS - 1);
	}

	static Component one(ServerPlayer player, long situs, String name) {
		if (name == null || name.isEmpty()) {
			return Component.empty();
		}
		switch (name) {
			case "pemain" -> {
				return player.getName().copy();
			}
			case "kota" -> {
				return situs == 0L ? Component.translatable("kultivator.cerita.isian.kota_ini") : KotaNames.town(situs);
			}
			case "sekte_terdekat" -> {
				return sekte(player, -1);
			}
			case "sekte_lurus" -> {
				return sekte(player, 0);
			}
			case "sekte_iblis" -> {
				return sekte(player, 2);
			}
			case "penguasa" -> {
				String ruler = AlurTakhta.penguasa(player.level().getServer(), situs);
				return Tokoh.nama(player.level().getServer(), "gu".equals(ruler) ? "gu_lianshan" : "ruyi".equals(ruler) ? "yan_ruyi" : "yan_wushuang", situs);
			}
			case "sisa_bab" -> {
				return Component.literal(Integer.toString(AlurTakhta.sisaHari(player.level().getServer(), situs)));
			}
			default -> {
			}
		}
		if (name.startsWith("tokoh:")) {
			return Tokoh.nama(player.level().getServer(), name.substring(6), situs);
		}
		if (name.startsWith("nb_pita:")) {
			return NilaiBatu.text(nbPita(player, name.substring(8)));
		}
		if (name.startsWith("sekte:")) {
			Component sect = SekteApi.diplomasi().name(name.substring(6));
			return sect != null ? sect : Component.translatable("kultivator.cerita.isian.sekte_gunung");
		}
		if (name.startsWith("nb:")) {
			return NilaiBatu.text(parse(name.substring(3), 0L));
		}
		return Component.literal(name);
	}

	/** {@code k × (band + 1)} for a factor written as text. */
	public static long nbPita(ServerPlayer player, String factor) {
		return parse(factor, 0L) * (pita(player) + 1L);
	}

	static Component sekte(ServerPlayer player, int aliran) {
		var service = SekteApi.diplomasi();
		String id = service.nearest(player.blockPosition(), aliran);
		Component name = id == null ? null : service.name(id);
		return name != null ? name : Component.translatable("kultivator.cerita.isian.sekte_gunung");
	}

	static long parse(String s, long fallback) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private Isian() {
	}
}
