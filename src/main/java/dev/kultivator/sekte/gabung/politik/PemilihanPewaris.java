package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.data.Cultivators;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The heir election of DESIGN 41.8.5 step 1 ("Pemilihan Pewaris", the disciple path; Xiao Yan's promised fight and the
 * Chosen Disciple selection of <i>A Will Eternal</i>). Held when a player candidate has registered and the last election is
 * at least {@link #JEDA_HARI} days ago (the seat is then "challenged"), or at once when the heir's seat is vacant.
 * <ol>
 *   <li><b>Registration</b> ({@code /kultivasi gabung pewaris daftar}): Core players with the heir's realm and 20 days of
 *   membership. The election day is announced {@link #TUNDA_HARI} days ahead to every member.</li>
 *   <li><b>Ujian Pewaris</b>: a bracket of all candidates (players + the 39F roster's Eldest Senior and the strongest Core
 *   disciple, so there are always rivals). NPC bouts are decided by rank and luck on the spot; a player fights his bout in
 *   person ({@code /kultivasi gabung pewaris tanding}: a Sword Duel under sect rules through {@code Kursi}), two players by
 *   invitation. A player bout not fought within {@link #JENDELA_HARI} days is lost.</li>
 *   <li><b>Penilaian tetua</b>: every elder votes for the finalist he likes better (41W's attitude toward a player; a
 *   steady regard for an NPC disciple); player elders vote themselves within one day
 *   ({@code /kultivasi gabung suara pewaris <1|2>}). The final's winner is heir when he also has at least half the votes,
 *   else the finalist with more votes.</li>
 * </ol>
 * State: the sect's {@code politik} compound, key {@code pemilihan}. Server thread only.
 */
public final class PemilihanPewaris {

	/** Days between two elections of a held seat. */
	public static final int JEDA_HARI = 30;
	/** Days from the announcement to the election. */
	public static final int TUNDA_HARI = 3;
	/** Days a player has to fight his bouts once the election began. */
	public static final int JENDELA_HARI = 3;
	/** Days player elders have to vote. */
	public static final int SUARA_HARI = 1;
	/** Membership days a candidate needs. */
	public static final int HARI_ANGGOTA = 20;

	public static final int TAHAP_TIDAK = 0;
	public static final int TAHAP_DAFTAR = 1;
	public static final int TAHAP_LAGA = 2;
	public static final int TAHAP_SUARA = 3;

	private static final String KEY = "kultivator.gabung.politik.pewaris.";

	// ================================================================== state

	private static CompoundTag state(MinecraftServer server, String sekte) {
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		CompoundTag t = b.getCompoundOrEmpty("pemilihan");
		if (!b.contains("pemilihan")) {
			b.put("pemilihan", t);
		}
		return t;
	}

	/** The stage of the sect's election ({@code TAHAP_*}). */
	public static int tahap(MinecraftServer server, String sekte) {
		return state(server, sekte).getIntOr("tahap", TAHAP_TIDAK);
	}

	/** The candidates still in the bracket (UUID strings or roster ids). */
	public static List<String> hidup(MinecraftServer server, String sekte) {
		return strings(state(server, sekte).getListOrEmpty("hidup"));
	}

	/** The two finalists while the elders vote (empty otherwise). */
	public static List<String> finalis(MinecraftServer server, String sekte) {
		return strings(state(server, sekte).getListOrEmpty("finalis"));
	}

	/** Votes so far: voter id -> finalist id (secret until the result: the screen shows them only when the stage ends). */
	public static Map<String, String> suara(MinecraftServer server, String sekte) {
		Map<String, String> out = new LinkedHashMap<>();
		CompoundTag s = state(server, sekte).getCompoundOrEmpty("suara");
		for (String key : s.keySet()) {
			out.put(key, s.getStringOr(key, ""));
		}
		return out;
	}

	// ================================================================== registration

	/** Why {@code player} may not stand for heir now, or null. */
	public static @Nullable Component alasan(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !Gabung.aktif()) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		if (k.pangkat != Pangkat.INTI) {
			return Component.translatable(KEY + "pangkat", Pangkat.INTI.nama());
		}
		var perlu = Pangkat.PEWARIS.syaratRanah(GabungApi.kelasSekte(sekte), GabungApi.tier(sekte));
		if (Cultivators.get(player).realm().ordinal() < perlu.ordinal()) {
			return Component.translatable(KEY + "ranah", perlu.displayName());
		}
		long hari = GabungApi.hari(server);
		if (k.umurAnggota(hari) < HARI_ANGGOTA) {
			return Component.translatable(KEY + "hari", HARI_ANGGOTA - k.umurAnggota(hari));
		}
		int t = tahap(server, sekte);
		if (t == TAHAP_LAGA || t == TAHAP_SUARA) {
			return Component.translatable(KEY + "berlangsung");
		}
		return null;
	}

	/** Registers {@code player} as a candidate; null when done (the election is then announced), else why not. */
	public static @Nullable Component daftar(ServerPlayer player) {
		Component why = alasan(player);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		CompoundTag t = state(server, sekte);
		long hari = GabungApi.hari(server);
		List<String> daftar = strings(t.getListOrEmpty("daftar"));
		String id = player.getUUID().toString();
		if (!daftar.contains(id)) {
			daftar.add(id);
		}
		t.put("daftar", list(daftar));
		boolean kosong = Kursi.pemegang(server, sekte, Pangkat.PEWARIS).isEmpty();
		long lalu = t.getLongOr("lalu", -1000L);
		if (t.getIntOr("tahap", TAHAP_TIDAK) == TAHAP_TIDAK && (kosong || hari >= lalu + JEDA_HARI)) {
			t.putInt("tahap", TAHAP_DAFTAR);
			t.putLong("hari", hari + (kosong ? 1 : TUNDA_HARI));
			Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "umum", DiplomasiGabung.nama(server, sekte),
					t.getLongOr("hari", hari) - hari), ChatFormatting.GOLD);
		}
		GabungData.get(server).setDirty();
		if (t.getIntOr("tahap", TAHAP_TIDAK) == TAHAP_TIDAK) {
			player.sendSystemMessage(Component.translatable(KEY + "tunggu", lalu + JEDA_HARI - hari).withStyle(ChatFormatting.GRAY));
		} else {
			player.sendSystemMessage(Component.translatable(KEY + "terdaftar", Math.max(0L, t.getLongOr("hari", hari) - hari))
					.withStyle(ChatFormatting.GREEN));
		}
		return null;
	}

	// ================================================================== the bracket

	/** Starts the election now ({@code uji gabung pewaris mulai}, or the daily pass on the election day). */
	public static void mulai(MinecraftServer server, String sekte) {
		CompoundTag t = state(server, sekte);
		long hari = GabungApi.hari(server);
		List<String> calon = new ArrayList<>();
		for (String id : strings(t.getListOrEmpty("daftar"))) {
			UUID uuid = Kursi.uuid(id);
			Keanggotaan k = uuid == null ? null : GabungData.get(server).peek(uuid);
			if (k != null && k.anggotaDi(sekte) && (k.pangkat == Pangkat.INTI || k.pangkat == Pangkat.PEWARIS)) {
				calon.add(id);
			}
		}
		for (String holder : Kursi.pemegang(server, sekte, Pangkat.PEWARIS)) {
			if (!calon.contains(holder)) {
				calon.add(holder);
			}
		}
		List<Roster.Tokoh> roster = new ArrayList<>(Roster.daftar(server, sekte));
		roster.sort((a, b) -> Integer.compare(b.rank().step(), a.rank().step()));
		int npc = (int) calon.stream().filter(c -> Kursi.uuid(c) == null).count();
		for (Roster.Tokoh r : roster) {
			if (npc >= 2) {
				break;
			}
			if (r.aktif() && ("kakak".equals(r.peran()) || "inti".equals(r.peran())) && !calon.contains(r.id())) {
				calon.add(r.id());
				npc++;
			}
		}
		if (calon.size() < 2) {
			t.putInt("tahap", TAHAP_TIDAK);
			t.putLong("lalu", hari);
			GabungData.get(server).setDirty();
			return;
		}
		// a shuffled bracket (one roll per election)
		RandomSource random = server.overworld().getRandom();
		java.util.Collections.shuffle(calon, new java.util.Random(random.nextLong()));
		t.put("hidup", list(calon));
		t.put("menang", new ListTag());
		t.putInt("babak", 1);
		t.putInt("tahap", TAHAP_LAGA);
		t.putLong("akhir", hari + JENDELA_HARI);
		t.put("suara", new CompoundTag());
		t.remove("finalis");
		GabungData.get(server).setDirty();
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "mulai", calon.size()), ChatFormatting.GOLD);
		majukan(server, sekte);
	}

	/**
	 * Settles every bout of the round that needs no player (NPC against NPC by rank and luck; a player's bye), then starts
	 * the next round, then the vote when one candidate is left.
	 */
	static void majukan(MinecraftServer server, String sekte) {
		CompoundTag t = state(server, sekte);
		if (t.getIntOr("tahap", TAHAP_TIDAK) != TAHAP_LAGA) {
			return;
		}
		RandomSource random = server.overworld().getRandom();
		for (int guard = 0; guard < 8; guard++) {
			List<String> hidup = strings(t.getListOrEmpty("hidup"));
			List<String> menang = strings(t.getListOrEmpty("menang"));
			boolean tunggu = false;
			int done = menang.size();
			for (int i = done * 2; i < hidup.size(); i += 2) {
				if (i + 1 >= hidup.size()) {
					menang.add(hidup.get(i));
					continue;
				}
				String a = hidup.get(i);
				String b = hidup.get(i + 1);
				if (Kursi.uuid(a) != null || Kursi.uuid(b) != null) {
					tunggu = true;
					break;
				}
				menang.add(simulasi(server, sekte, a, b, random) ? a : b);
			}
			t.put("menang", list(menang));
			if (tunggu) {
				GabungData.get(server).setDirty();
				return;
			}
			if (menang.size() <= 1) {
				break;
			}
			t.put("hidup", list(menang));
			t.put("menang", new ListTag());
			t.putInt("babak", t.getIntOr("babak", 1) + 1);
		}
		List<String> hidup = strings(t.getListOrEmpty("hidup"));
		List<String> menang = strings(t.getListOrEmpty("menang"));
		if (hidup.size() == 2 && menang.size() == 1) {
			t.put("finalis", list(hidup));
			t.put("juara", StringTag.valueOf(menang.get(0)));
			mulaiSuara(server, sekte);
		}
		GabungData.get(server).setDirty();
	}

	private static boolean simulasi(MinecraftServer server, String sekte, String a, String b, RandomSource random) {
		int sa = Kursi.langkahTokoh(server, sekte, a);
		int sb = Kursi.langkahTokoh(server, sekte, b);
		double p = Math.max(0.15D, Math.min(0.85D, 0.5D + 0.08D * (sa - sb)));
		return random.nextDouble() < p;
	}

	/** The player's bout of the current round now; null when it started, else why not. */
	public static @Nullable Component tanding(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		if (sekte == null || tahap(server, sekte) != TAHAP_LAGA) {
			return Component.translatable(KEY + "tak_ada_laga");
		}
		CompoundTag t = state(server, sekte);
		List<String> hidup = strings(t.getListOrEmpty("hidup"));
		List<String> menang = strings(t.getListOrEmpty("menang"));
		String id = player.getUUID().toString();
		int i = menang.size() * 2;
		if (i + 1 >= hidup.size() || !(hidup.get(i).equals(id) || hidup.get(i + 1).equals(id))) {
			return Component.translatable(KEY + "bukan_giliran");
		}
		String lawan = hidup.get(i).equals(id) ? hidup.get(i + 1) : hidup.get(i);
		String busy = Sibuk.why(player);
		if (busy != null) {
			return Sibuk.text(busy);
		}
		Kursi.Hasil hasil = (p, win, alasan, other) -> hasilLaga(server, sekte, id, lawan, win);
		UUID lawanUuid = Kursi.uuid(lawan);
		if (lawanUuid != null) {
			ServerPlayer lp = server.getPlayerList().getPlayer(lawanUuid);
			if (lp == null) {
				return Component.translatable(KEY + "lawan_luring", Kursi.namaPemegang(server, lawan));
			}
			return Kursi.tandingPemain(player, lp, hasil);
		}
		return Kursi.tandingTokoh(player, sekte, lawan, Kursi.namaPemegang(server, lawan), Kursi.langkahTokoh(server, sekte, lawan),
				Kursi.TANDING_TICKS, hasil);
	}

	private static void hasilLaga(MinecraftServer server, String sekte, String pemain, String lawan, boolean menang) {
		CompoundTag t = state(server, sekte);
		if (t.getIntOr("tahap", TAHAP_TIDAK) != TAHAP_LAGA) {
			return;
		}
		List<String> hidup = strings(t.getListOrEmpty("hidup"));
		List<String> menangList = strings(t.getListOrEmpty("menang"));
		int i = menangList.size() * 2;
		if (i + 1 >= hidup.size()) {
			return;
		}
		String a = hidup.get(i);
		String b = hidup.get(i + 1);
		if (!(a.equals(pemain) && b.equals(lawan) || a.equals(lawan) && b.equals(pemain))) {
			return;
		}
		String winner = menang ? pemain : lawan;
		menangList.add(winner);
		t.put("menang", list(menangList));
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "hasil", Kursi.namaPemegang(server, winner),
				Kursi.namaPemegang(server, winner.equals(a) ? b : a)), ChatFormatting.YELLOW);
		GabungData.get(server).setDirty();
		majukan(server, sekte);
	}

	// ================================================================== the elders' vote

	private static void mulaiSuara(MinecraftServer server, String sekte) {
		CompoundTag t = state(server, sekte);
		List<String> fin = strings(t.getListOrEmpty("finalis"));
		t.putInt("tahap", TAHAP_SUARA);
		t.putLong("suaraSampai", GabungApi.hari(server) + SUARA_HARI);
		CompoundTag suara = new CompoundTag();
		for (TokohSekte.Tokoh e : TokohSekte.tetua(server, sekte)) {
			suara.putString(e.id(), pilihanTokoh(server, e.id(), fin));
		}
		t.put("suara", suara);
		GabungData.get(server).setDirty();
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "final", Kursi.namaPemegang(server, fin.get(0)),
				Kursi.namaPemegang(server, fin.get(1))), ChatFormatting.GOLD);
	}

	/** An elder's choice: the finalist he likes more (players by 41W attitude; NPC disciples count a steady 25). */
	private static String pilihanTokoh(MinecraftServer server, String elder, List<String> fin) {
		int best = Integer.MIN_VALUE;
		String pick = fin.get(0);
		for (String c : fin) {
			UUID uuid = Kursi.uuid(c);
			int s = uuid != null ? TokohSekte.sikap(server, elder, uuid) : 25 + Math.floorMod((elder + c).hashCode(), 11) - 5;
			if (s > best) {
				best = s;
				pick = c;
			}
		}
		return pick;
	}

	/** A player elder votes for finalist {@code nomor} (1 or 2); null when counted, else why not. */
	public static @Nullable Component pilih(ServerPlayer player, int nomor) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || tahap(server, sekte) != TAHAP_SUARA) {
			return Component.translatable(KEY + "tak_ada_suara");
		}
		if (!k.pangkat.bersuara()) {
			return Component.translatable(KEY + "tak_bersuara");
		}
		List<String> fin = finalis(server, sekte);
		if (nomor < 1 || nomor > fin.size()) {
			return Component.translatable(KEY + "nomor");
		}
		if (fin.contains(player.getUUID().toString())) {
			return Component.translatable(KEY + "diri_sendiri");
		}
		CompoundTag t = state(server, sekte);
		CompoundTag suara = t.getCompoundOrEmpty("suara");
		suara.putString(player.getUUID().toString(), fin.get(nomor - 1));
		t.put("suara", suara);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "dipilih", Kursi.namaPemegang(server, fin.get(nomor - 1))).withStyle(ChatFormatting.GREEN));
		return null;
	}

	/** Counts the vote and names the heir. */
	static void putus(MinecraftServer server, String sekte) {
		CompoundTag t = state(server, sekte);
		List<String> fin = strings(t.getListOrEmpty("finalis"));
		String juara = t.getStringOr("juara", fin.isEmpty() ? "" : fin.get(0));
		if (fin.size() < 2) {
			t.putInt("tahap", TAHAP_TIDAK);
			return;
		}
		Map<String, Integer> hitung = new LinkedHashMap<>();
		Map<String, String> suara = suara(server, sekte);
		suara.values().forEach(c -> hitung.merge(c, 1, Integer::sum));
		int total = suara.size();
		int juaraSuara = hitung.getOrDefault(juara, 0);
		String pewaris;
		if (total == 0 || juaraSuara * 2 >= total) {
			pewaris = juara;
		} else {
			String other = fin.get(0).equals(juara) ? fin.get(1) : fin.get(0);
			pewaris = hitung.getOrDefault(other, 0) > juaraSuara ? other : juara;
		}
		tetapkan(server, sekte, pewaris);
		t.putInt("tahap", TAHAP_TIDAK);
		t.putLong("lalu", GabungApi.hari(server));
		t.put("daftar", new ListTag());
		GabungData.get(server).setDirty();
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "pewaris", Kursi.namaPemegang(server, pewaris),
				juaraSuara, total), ChatFormatting.GOLD);
	}

	/** Seats {@code pewaris} (a player or a roster id) as heir; a player heir before him returns to Inti. */
	static void tetapkan(MinecraftServer server, String sekte, String pewaris) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		UUID lama = Kursi.uuid(d.pewaris);
		if (lama != null && !d.pewaris.equals(pewaris)) {
			ServerPlayer p = server.getPlayerList().getPlayer(lama);
			if (p != null) {
				GabungApi.setPangkat(p, Pangkat.INTI, "gabung.pewaris_kalah");
			} else {
				Keanggotaan k = data.peek(lama);
				if (k != null && k.anggotaDi(sekte) && k.pangkat == Pangkat.PEWARIS) {
					k.pangkat = Pangkat.INTI;
				}
			}
		}
		d.pewaris = pewaris;
		UUID baru = Kursi.uuid(pewaris);
		if (baru != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(baru);
			if (p != null) {
				GabungApi.setPangkat(p, Pangkat.PEWARIS, "gabung.pewaris");
			} else {
				Keanggotaan k = data.peek(baru);
				if (k != null && k.anggotaDi(sekte)) {
					k.pangkat = Pangkat.PEWARIS;
					k.hariPangkat = GabungApi.hari(server);
				}
			}
			d.pewaris = pewaris;
		}
		data.setDirty();
	}

	// ================================================================== daily (from Politik)

	static void harian(MinecraftServer server, String sekte, long hari) {
		CompoundTag t = state(server, sekte);
		switch (t.getIntOr("tahap", TAHAP_TIDAK)) {
			case TAHAP_DAFTAR -> {
				if (hari >= t.getLongOr("hari", hari)) {
					mulai(server, sekte);
				}
			}
			case TAHAP_LAGA -> {
				if (hari > t.getLongOr("akhir", hari)) {
					gugurkan(server, sekte);
				} else {
					majukan(server, sekte);
				}
			}
			case TAHAP_SUARA -> {
				if (hari >= t.getLongOr("suaraSampai", hari)) {
					putus(server, sekte);
				}
			}
			default -> {
			}
		}
	}

	/** The window is over: every player bout not fought is lost by the player (two players: the first seed advances). */
	private static void gugurkan(MinecraftServer server, String sekte) {
		CompoundTag t = state(server, sekte);
		for (int guard = 0; guard < 8 && t.getIntOr("tahap", TAHAP_TIDAK) == TAHAP_LAGA; guard++) {
			List<String> hidup = strings(t.getListOrEmpty("hidup"));
			List<String> menang = strings(t.getListOrEmpty("menang"));
			int i = menang.size() * 2;
			if (i + 1 >= hidup.size()) {
				majukan(server, sekte);
				continue;
			}
			String a = hidup.get(i);
			String b = hidup.get(i + 1);
			String winner = Kursi.uuid(a) == null ? a : Kursi.uuid(b) == null ? b : a;
			menang.add(winner);
			t.put("menang", list(menang));
			majukan(server, sekte);
		}
		GabungData.get(server).setDirty();
	}

	// ================================================================== helpers

	private static List<String> strings(ListTag list) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			String s = list.getStringOr(i, "");
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	private static ListTag list(List<String> values) {
		ListTag out = new ListTag();
		for (String v : values) {
			out.add(StringTag.valueOf(v));
		}
		return out;
	}

	private PemilihanPewaris() {
	}
}
