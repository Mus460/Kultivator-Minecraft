package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Effects of a feud on a disciple whose body is not loaded (away on a journey, the hall unloaded): a memory for its
 * scroll ({@code Ingatan}) or a feud activity to show when it is back (the limp home, the vow). They wait in
 * {@link SeteruData.Aula#antre}, at most {@link SeteruData#ANTRE_MAKS} per soul, and are applied when the body is next
 * seen; the sweep runs every 100 ticks and only for halls that have something waiting (no world scan). Effects that do
 * not need the body (honour, mistreatment, grudges as goals) go straight through their own buses instead.
 */
final class Antrean {

	private static final String JENIS = "j";
	private static final String INGAT = "ingat";
	private static final String KEGIATAN = "kegiatan";
	private static final String DOMPET = "dompet";

	/** A memory now when the body is loaded, else when it is next seen. */
	static void ingat(MinecraftServer server, UUID owner, UUID soul, String kind, @Nullable UUID other, int weight, String textKey,
			String arg) {
		// a memory keeps one literal argument: resolve names (keys, nested sect names) now
		String text = Teks.polos(arg);
		DiscipleEntity body = loaded(owner, soul);
		if (body != null) {
			Ingatan.remember(server, body.data(), kind, other, weight, textKey, text);
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putString(JENIS, INGAT);
		t.putString("kind", kind);
		if (other != null) {
			t.store("other", UUIDUtil.CODEC, other);
		}
		t.putInt("w", weight);
		t.putString("text", textKey);
		t.putString("arg", text);
		t.putLong("hari", Ingatan.today(server));
		tambah(server, owner, soul, t);
	}

	/** A feud activity (TERLUKA, SUMPAH) now when the body is loaded and free, else when it is next seen. */
	static void kegiatan(MinecraftServer server, UUID owner, UUID soul, Kegiatan kegiatan, int lamaTick, long sampaiHari) {
		DiscipleEntity body = loaded(owner, soul);
		if (body != null && PelaksanaSeteru.mulai(body, kegiatan, lamaTick, null, null)) {
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putString(JENIS, KEGIATAN);
		t.putString("id", kegiatan.id());
		t.putInt("lama", lamaTick);
		t.putLong("sampai", sampaiHari);
		tambah(server, owner, soul, t);
	}

	/** NB into the disciple's purse (compensation, 39.F.4) through the one purse writer, now or when it is next seen. */
	static void dompet(MinecraftServer server, UUID owner, UUID soul, long nb, String sebab) {
		DiscipleEntity body = loaded(owner, soul);
		if (body != null) {
			MandiriApi.ubahDompet(body.data(), nb, sebab);
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putString(JENIS, DOMPET);
		t.putLong("nb", nb);
		t.putString("sebab", sebab == null ? "seteru" : sebab);
		tambah(server, owner, soul, t);
	}

	private static void tambah(MinecraftServer server, UUID owner, UUID soul, CompoundTag effect) {
		SeteruData data = SeteruData.get(server);
		List<CompoundTag> list = data.aula(owner).antre.computeIfAbsent(soul, key -> new ArrayList<>());
		list.add(effect);
		while (list.size() > SeteruData.ANTRE_MAKS) {
			list.remove(0);
		}
		data.setDirty();
	}

	/** Every 100 ticks: applies what waits for bodies that are loaded now. */
	static void sapu(MinecraftServer server) {
		SeteruData data = SeteruData.get(server);
		long today = Ingatan.today(server);
		boolean changed = false;
		for (Map.Entry<UUID, SeteruData.Aula> hall : data.aula.entrySet()) {
			if (hall.getValue().antre.isEmpty()) {
				continue;
			}
			List<DiscipleEntity> bodies = Disciples.loaded(hall.getKey());
			if (bodies.isEmpty()) {
				continue;
			}
			Iterator<Map.Entry<UUID, List<CompoundTag>>> it = hall.getValue().antre.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, List<CompoundTag>> e = it.next();
				DiscipleEntity body = null;
				for (DiscipleEntity b : bodies) {
					if (b.soulId().equals(e.getKey())) {
						body = b;
						break;
					}
				}
				if (body == null || body.level().isClientSide()) {
					continue;
				}
				List<CompoundTag> left = new ArrayList<>();
				for (CompoundTag t : e.getValue()) {
					if (!terapkan(server, body, t, today)) {
						left.add(t);
					}
				}
				if (left.size() == e.getValue().size()) {
					continue; // nothing applied: no save every 100 ticks for an effect still waiting
				}
				changed = true;
				if (left.isEmpty()) {
					it.remove();
				} else {
					e.setValue(left);
				}
			}
		}
		if (changed) {
			data.setDirty();
		}
	}

	/** Applies one effect; false keeps it waiting (an activity that cannot start yet). */
	private static boolean terapkan(MinecraftServer server, DiscipleEntity body, CompoundTag t, long today) {
		switch (t.getStringOr(JENIS, "")) {
			case INGAT -> {
				Ingatan.remember(body.data(), new Kenangan(t.getStringOr("kind", ""), t.getLongOr("hari", today),
						t.read("other", UUIDUtil.CODEC).orElse(null), t.getIntOr("w", 0), t.getStringOr("text", ""), t.getStringOr("arg", "")));
				return true;
			}
			case DOMPET -> {
				MandiriApi.ubahDompet(body.data(), t.getLongOr("nb", 0L), t.getStringOr("sebab", "seteru"));
				return true;
			}
			case KEGIATAN -> {
				if (today > t.getLongOr("sampai", today)) {
					return true;
				}
				Kegiatan kegiatan = Kegiatan.byId(t.getStringOr("id", ""));
				if (kegiatan == null) {
					return true;
				}
				return PelaksanaSeteru.mulai(body, kegiatan, t.getIntOr("lama", 600), null, null);
			}
			default -> {
				return true;
			}
		}
	}

	static @Nullable DiscipleEntity loaded(UUID owner, UUID soul) {
		for (DiscipleEntity body : Disciples.loaded(owner)) {
			if (body.soulId().equals(soul)) {
				return body;
			}
		}
		return null;
	}

	private Antrean() {
	}
}
