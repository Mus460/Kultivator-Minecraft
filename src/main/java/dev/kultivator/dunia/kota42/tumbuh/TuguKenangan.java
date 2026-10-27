package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Memorials of finished rebuild projects (DESIGN 42.9.5 "Tugu Kenangan"): a client-drawn inscription on the square (no
 * block) with the world day, the blocks rebuilt and the five biggest donors. {@link BangunKembali} writes one record per
 * finished project (at most 8 per place, oldest first out); clients and screens read them here.
 * <p>Until a client draws the inscription, the memorial reads itself: a player who walks up to the square of a place that
 * has one gets the newest record in chat once per visit ({@link #tick}).
 */
public final class TuguKenangan {

	/** One donor line of a memorial. */
	public record Penyumbang(String nama, long nb) {
	}

	/** One memorial: world day the project ended, blocks rebuilt, up to five donors (biggest first). */
	public record Tugu(long hari, int blok, List<Penyumbang> penyumbang) {
	}

	/** The memorials of place {@code situs}, oldest first. */
	public static List<Tugu> daftar(MinecraftServer server, long situs) {
		List<Tugu> out = new ArrayList<>();
		for (Tag t : BangunKembali.data(server, situs).getListOrEmpty("tugu")) {
			if (t instanceof CompoundTag c) {
				List<Penyumbang> donors = new ArrayList<>();
				for (Tag d : c.getListOrEmpty("dn")) {
					if (d instanceof CompoundTag dc) {
						donors.add(new Penyumbang(dc.getStringOr("n", "?"), dc.getLongOr("nb", 0L)));
					}
				}
				out.add(new Tugu(c.getLongOr("hari", 0L), c.getIntOr("blok", 0), List.copyOf(donors)));
			}
		}
		return out;
	}

	/** Player + place -> memorials already read this session. */
	private static final Map<String, Integer> DILIHAT = new HashMap<>();
	private static final double JARAK_BACA = 14.0D;

	/** Every ~4 seconds: players near the square of a place with memorials read the newest once. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if ((server.getTickCount() / 20) % 4 != 0 || level.players().isEmpty()) {
			return;
		}
		CompoundTag all = KotaTumbuhData.get(server).bagian(KotaTumbuhData.BANGUN);
		for (String key : all.keySet()) {
			if (!key.startsWith("kota_") || all.getCompoundOrEmpty(key).getListOrEmpty("tugu").isEmpty()) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException exception) {
				continue;
			}
			BlockPos at = BangunKembali.alun(level, situs);
			if (at == null) {
				continue;
			}
			List<Tugu> list = daftar(server, situs);
			for (ServerPlayer p : level.players()) {
				String k = p.getUUID() + ":" + situs;
				if (p.isSpectator() || p.blockPosition().distSqr(at) > JARAK_BACA * JARAK_BACA || DILIHAT.getOrDefault(k, 0) >= list.size()) {
					continue;
				}
				DILIHAT.put(k, list.size());
				Tugu tugu = list.get(list.size() - 1);
				StringBuilder names = new StringBuilder();
				for (Penyumbang d : tugu.penyumbang()) {
					names.append(names.isEmpty() ? "" : ", ").append(d.nama());
				}
				Msg.chat(p, Component.translatable("kultivator.kota42.tumbuh.bangun.tugu", tugu.hari(), tugu.blok(),
						names.isEmpty() ? "-" : names.toString()).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	static void lupakan(UUID player) {
		DILIHAT.keySet().removeIf(k -> k.startsWith(player + ":"));
	}

	static void lupakan() {
		DILIHAT.clear();
	}

	private TuguKenangan() {
	}
}
