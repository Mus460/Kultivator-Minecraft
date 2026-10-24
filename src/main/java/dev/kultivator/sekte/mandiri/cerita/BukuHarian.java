package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.CitaCita;
import dev.kultivator.sekte.mandiri.Irama;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.Tokoh;
import dev.kultivator.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Buku Harian, the diary writer of DESIGN 39.8.1 (contract C39.E1, installed with {@code MandiriApi.setPencatat}) and the
 * builder of the diary page ({@link #halaman}, payload {@code mandiri_harian}).
 * <p>
 * <b>Writing.</b> The server keeps keys and arguments only (the ring of 40 in {@code MandiriData.harian.<soul>}, ordinary
 * entries dropped first). The writer adds one context argument {@code ctx:<cuaca>:<bulan>} (rain, snow, storm or clear;
 * full or new moon) when the entry still has room, read from the disciple's level at writing time, so the client can open
 * the entry with the weather of that real day ("Hujan tipis membasahi..."). Nothing else is invented.
 * <p>
 * <b>Reading.</b> The page carries everything the client composer ({@code HarianTeks}) needs: the entries, the
 * disciple's traits (the reflection), its priority-1 goal (the closing line), two days of hour strips, the purse and bag,
 * the shopping list, the characters it knows, its three strongest ties, the travel scrolls of its last journeys and the
 * effective policy. A disciple whose body is not loaded shows the last mirror ({@code PapanMandiri.ringkas}).
 */
public final class BukuHarian {

	/** The context argument prefix (stripped by the composer). */
	public static final String CTX = "ctx:";
	/** The last travel scrolls kept per disciple (hall record {@code cerita.gulungan.<soul>}). */
	static final int GULUNGAN_MAKS = 3;

	/** The installed writer (C39.E1). */
	static void catat(UUID owner, UUID soul, EntriHarian entri) {
		MinecraftServer server = Cerita.server();
		if (server == null) {
			return;
		}
		EntriHarian isi = entri;
		if (entri.arg().size() < EntriHarian.ARG_MAKS && !punyaCtx(entri.arg())) {
			String ctx = konteks(server, owner, soul);
			if (ctx != null) {
				List<String> arg = new ArrayList<>(entri.arg());
				arg.add(ctx);
				isi = new EntriHarian(entri.hari(), entri.jam(), entri.jenis(), entri.kunci(), arg, entri.suasana(), entri.penting());
			}
		}
		MandiriApi.simpanHarian(server, owner, soul, isi);
	}

	private static boolean punyaCtx(List<String> arg) {
		for (String a : arg) {
			if (a != null && a.startsWith(CTX)) {
				return true;
			}
		}
		return false;
	}

	/** {@code ctx:<cuaca>:<bulan>} of the disciple's level now (its body, else the overworld), or null. */
	static @Nullable String konteks(MinecraftServer server, UUID owner, UUID soul) {
		DiscipleEntity body = Perencana.cari(owner, soul);
		ServerLevel level = body != null && body.level() instanceof ServerLevel l ? l : server.overworld();
		BlockPos pos = body != null ? body.blockPosition() : level.getRespawnData().pos();
		String cuaca;
		if (level.isThundering()) {
			cuaca = "badai";
		} else if (level.isRaining()) {
			boolean salju;
			try {
				salju = level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
			} catch (RuntimeException exception) {
				salju = false;
			}
			cuaca = salju ? "salju" : "hujan";
		} else {
			cuaca = "cerah";
		}
		String bulan = Irama.purnama(level, pos) ? "purnama" : Irama.bulanBaru(level, pos) ? "baru" : "biasa";
		return CTX + cuaca + ":" + bulan;
	}

	// ================================================================== the page

	/** Builds the diary page of {@code soul} for {@code viewer} (the master or an op), or null when unknown. */
	static @Nullable MandiriPayloads.Harian halaman(ServerPlayer viewer, UUID owner, UUID soul) {
		MinecraftServer server = viewer.level().getServer();
		DiscipleEntity body = Perencana.cari(owner, soul);
		CompoundTag mirror = PapanMandiri.ringkas(server, owner, soul);
		if (body == null && mirror.isEmpty() && MandiriApi.harian(server, owner, soul).isEmpty()) {
			return null;
		}
		CompoundTag out = new CompoundTag();
		out.putString("soul", soul.toString());
		out.putBoolean("own", owner.equals(viewer.getUUID()));
		out.putBoolean("muat", body != null);
		if (body != null) {
			out.putInt("eid", body.getId());
		}
		ServerLevel level = body != null && body.level() instanceof ServerLevel l ? l : server.overworld();
		out.putLong("hari", Irama.hari(level));
		out.putInt("jam", Irama.jam(level));
		Component name = body != null ? body.getName() : mirror.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		out.store("nama", ComponentSerialization.CODEC, name);

		ListTag entries = new ListTag();
		for (EntriHarian e : MandiriApi.harian(server, owner, soul)) {
			entries.add(e.toTag());
		}
		out.put("e", entries);
		out.put("g", server == null ? new ListTag() : gulungan(server, owner, soul).copy());

		List<ItemStack> tas = List.of();
		if (body != null) {
			DiscipleData data = body.data();
			CompoundTag m = MandiriApi.bagian(data);
			out.putString("jl", m.getStringOr(MandiriIds.K_JALAN, ""));
			out.put("al", m.getListOrEmpty(MandiriIds.K_JALAN_ALASAN).copy());
			out.putInt("ib", MandiriApi.iblis(data));
			ListTag sifat = new ListTag();
			for (Sifat s : SekteApi.hati().traits(data)) {
				sifat.add(StringTag.valueOf(s.id()));
			}
			out.put("sf", sifat);
			out.put("cita", citaDetail(server, owner, body));
			out.putString("ct", citaUtama(data));
			out.put("rw", new IntArrayTag(m.getIntArray(MandiriIds.K_RIWAYAT).orElse(new int[24])));
			out.put("km", new IntArrayTag(m.getIntArray(MandiriIds.K_KEMARIN).orElse(new int[24])));
			out.putLong("dp", MandiriApi.dompet(data));
			out.putLong("dm", MandiriApi.dompetMaks(data));
			Kebijakan k = MandiriApi.kebijakan(body);
			out.putInt("us", k.uangSakuPersen());
			out.putLong("sk", (long) MandiriConfig.get().uangSakuJenjang(SectRanks.rank(data)) * k.uangSakuPersen() / 100L);
			out.put("bl", m.getListOrEmpty(MandiriIds.K_BELANJA).copy());
			out.put("ik", ikatan(owner, body));
			out.put("kb", k.toTag());
			out.put("tm", m.getCompoundOrEmpty(MandiriIds.K_KEBIJAKAN).copy());
			out.putInt("rank", data.rank().step());
			tas = new ArrayList<>(MandiriApi.tas(data, server.registryAccess()));
			tas.removeIf(ItemStack::isEmpty);
			PapanMandiri.perbaruiRingkas(server, body);
		} else {
			out.putString("jl", mirror.getStringOr("j", ""));
			out.put("sf", mirror.getListOrEmpty("s").copy());
			out.putString("ct", mirror.getStringOr("c", ""));
			out.putLong("dp", mirror.getLongOr("d", 0L));
			out.putInt("ib", mirror.getIntOr("i", 0));
			out.putInt("rank", mirror.getIntOr("r", 0));
		}
		out.put("tk", tokoh(server, owner, soul));
		if (!entries.isEmpty() && owner.equals(viewer.getUUID())) {
			Advancements.grant(viewer, dev.kultivator.sekte.SekteIds.ADVANCEMENT_FOLDER + MandiriIds.ADV_HARIAN);
		}
		return new MandiriPayloads.Harian(out, tas);
	}

	/** The priority-1 goal kind of {@code data}, or "". */
	static String citaUtama(DiscipleData data) {
		for (CitaCita.Cita c : CitaCita.aktif(data)) {
			if (c.prio() == 1) {
				return c.jenis().id();
			}
		}
		return "";
	}

	/** Every active goal with a readable target, its progress, priority and origin. */
	private static ListTag citaDetail(MinecraftServer server, UUID owner, DiscipleEntity body) {
		ListTag list = new ListTag();
		DiscipleData data = body.data();
		long hari = Irama.hari(body.level());
		for (CitaCita.Cita c : CitaCita.aktif(data)) {
			CompoundTag t = new CompoundTag();
			t.putString("j", c.jenis().id());
			t.putInt("p", c.prog());
			t.putInt("r", c.prio());
			t.putString("a", c.asal());
			t.putLong("h", Math.max(0L, hari - c.mulai()));
			t.store("t", ComponentSerialization.CODEC, target(server, owner, c.jenis(), c.target()));
			list.add(t);
		}
		return list;
	}

	/** The goal's target in words: a rank, a percentage of comprehension, a count, a name. */
	static Component target(MinecraftServer server, UUID owner, JenisCita jenis, String target) {
		String k = "kultivator.sekte.mandiri.cerita.target.";
		try {
			return switch (jenis) {
				case RANAH -> Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, Integer.parseInt(target)))).displayName();
				case DAO -> Component.translatable(k + "dao", target);
				case TEKNIK -> Component.translatable(k + "teknik", target);
				case PUSAKA -> Component.translatable(k + "pusaka", target);
				case JUARA -> Component.translatable(k + "juara", target);
				case DENDAM -> Component.translatable(k + "dendam", namaJiwa(server, owner, target));
				case JODOH -> Component.translatable(k + "jodoh");
				case GURU -> Component.translatable(k + "guru");
				case JENJANG -> SectRanks.rankName(Integer.parseInt(target));
				case KEKAYAAN -> Component.translatable(k + "kekayaan", target);
				case BINATANG -> Component.translatable(k + "binatang", target);
				case RAHASIA -> Component.translatable(k + "rahasia", target);
				case MENOLONG -> Component.translatable(k + "menolong", target);
				case KARYA -> Component.translatable(k + "karya", target);
			};
		} catch (NumberFormatException exception) {
			return Component.literal(target);
		}
	}

	/** A soul's name: its loaded body, the board mirror, or "?". */
	static Component namaJiwa(MinecraftServer server, UUID owner, String soul) {
		try {
			UUID id = UUID.fromString(soul);
			DiscipleEntity d = Perencana.cari(owner, id);
			if (d != null) {
				return d.getName();
			}
			return PapanMandiri.ringkas(server, owner, id).read("n", ComponentSerialization.CODEC)
					.orElse(Component.translatable("kultivator.sekte.mandiri.cerita.tak_dikenal"));
		} catch (IllegalArgumentException exception) {
			return Component.literal(soul);
		}
	}

	/** The three strongest ties (friends, sworn kin, lovers, Dao companions, rivals, grudges). */
	private static ListTag ikatan(UUID owner, DiscipleEntity body) {
		record Tie(UUID soul, Ikatan kind, int affinity) {
		}
		List<Tie> ties = new ArrayList<>();
		Ikatan[] kinds = {Ikatan.PASANGAN_DAO, Ikatan.KEKASIH, Ikatan.SAUDARA_ANGKAT, Ikatan.SAHABAT, Ikatan.SAINGAN, Ikatan.DENDAM};
		for (Ikatan kind : kinds) {
			for (UUID other : SekteApi.hubungan().with(owner, body.soulId(), kind)) {
				ties.add(new Tie(other, kind, SekteApi.hubungan().affinity(owner, body.soulId(), other)));
			}
		}
		ties.sort(Comparator.comparingInt((Tie t) -> -Math.abs(t.affinity())));
		ListTag list = new ListTag();
		MinecraftServer server = Cerita.server();
		for (Tie t : ties) {
			if (list.size() >= 3) {
				break;
			}
			CompoundTag tag = new CompoundTag();
			tag.putString("k", t.kind().id());
			tag.putInt("a", t.affinity());
			tag.store("n", ComponentSerialization.CODEC, server == null ? Component.literal("?") : namaJiwa(server, owner, t.soul().toString()));
			list.add(tag);
		}
		return list;
	}

	/** The characters this soul has met, warmest first (at most 5). */
	private static ListTag tokoh(MinecraftServer server, UUID owner, UUID soul) {
		List<Tokoh> known = new ArrayList<>();
		for (Tokoh t : DaftarTokoh.semua(server, owner)) {
			if (t.sikap().containsKey(soul)) {
				known.add(t);
			}
		}
		known.sort(Comparator.comparingInt((Tokoh t) -> -t.sikapKe(soul)));
		ListTag list = new ListTag();
		for (Tokoh t : known) {
			if (list.size() >= 5) {
				break;
			}
			CompoundTag tag = new CompoundTag();
			tag.putString("n", t.nama());
			tag.putString("p", t.peran().name());
			tag.putInt("s", t.sikapKe(soul));
			tag.putInt("j", t.jumpa());
			tag.putString("w", t.watak());
			list.add(tag);
		}
		return list;
	}

	// ================================================================== travel scrolls (Gulungan Perjalanan, 39.8.2)

	/** The kept travel scrolls of {@code soul}, newest last. */
	static ListTag gulungan(MinecraftServer server, UUID owner, UUID soul) {
		return Cerita.bagian(server, owner, "gulungan").getListOrEmpty(soul.toString());
	}

	/** Keeps the story lines of a finished journey (from {@code MandiriBus.JALAN_TIBA}). */
	static void simpanGulungan(MinecraftServer server, UUID owner, UUID soul, String jenis, String tujuan, long hari, List<String> tahap,
			boolean sukses) {
		if (tahap == null || tahap.isEmpty()) {
			return;
		}
		CompoundTag all = Cerita.bagian(server, owner, "gulungan");
		ListTag list = all.getListOrEmpty(soul.toString()).copy();
		CompoundTag scroll = new CompoundTag();
		scroll.putString("j", jenis);
		scroll.putString("t", tujuan);
		scroll.putLong("h", hari);
		scroll.putBoolean("ok", sukses);
		ListTag lines = new ListTag();
		for (String line : tahap) {
			if (lines.size() < 12 && line != null && !line.isEmpty()) {
				lines.add(StringTag.valueOf(line.length() > 240 ? line.substring(0, 240) : line));
			}
		}
		scroll.put("s", lines);
		list.add(scroll);
		while (list.size() > GULUNGAN_MAKS) {
			list.remove(0);
		}
		all.put(soul.toString(), list);
		MandiriApi.tandai(server);
	}

	/** Sends the page of {@code soul} to {@code viewer} (nothing when unknown). */
	static void kirim(ServerPlayer viewer, UUID owner, UUID soul) {
		try {
			MandiriPayloads.Harian page = halaman(viewer, owner, soul);
			if (page != null) {
				MandiriPayloads.kirim(viewer, page);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri diary page failed", exception);
		}
	}

	private BukuHarian() {
	}
}
