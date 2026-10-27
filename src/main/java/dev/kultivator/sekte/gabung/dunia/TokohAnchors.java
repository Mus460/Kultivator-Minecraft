package dev.kultivator.sekte.gabung.dunia;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.sosok.SosokGen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bodies of the named elders and master (DESIGN 41.6.12, contract C41.W1, owner 41W). A character scheduled at its titik
 * (the {@code "tokoh"} table of {@code data/kultivator/tempat/jadwal/kompleks_sekte_tokoh.json}: office -&gt; hour range
 * -&gt; titik, clip and props) gets a body there through {@code HidupApi.tubuhStaf} (a sect's own workplace people: never
 * refused by the NPC caps, at most eight per sect) while a player is within {@link #JARAK} blocks. The body is tagged with
 * the tokoh id ({@code memory "tokoh"}, so the 38 story engine fires {@code bicara tokoh:<id>}/{@code duel tokoh:<id>}
 * and service providers of 41B/41C know who they face through {@link #tokohDari}), named "Tetua Hukum Qin Zhenyuan",
 * dressed with {@code SosokApi.setRupa(npc, "tetua_sekte|sekte=npc:<s>")} (master: {@code "ketua_sekte|sekte=npc:<s>"}),
 * set to its realm, Dao and sex, and holds the scheduled clip. A body whose hour moved it elsewhere, or whose character is
 * wounded or retired, leaves once no player stands within {@link #PERGI_JARAK} blocks of it, so nobody sees it vanish.
 * <p>
 * Without a compound the gate titik of 41W are used ({@link TitikGabung#penggantiGerbang}): only the admissions and the
 * pavilion elder then sit at {@code sektenpc/loket}; the master's body at the gate stays 36 D's own (no double master).
 * Members walking past an elder are greeted once per {@link #SAPA_JEDA} ticks by office and relation.
 */
public final class TokohAnchors {

	/** Distance within which a character gets a body. */
	public static final double JARAK = 64.0D;
	/** A body that should leave waits until no player is this close. */
	public static final double PERGI_JARAK = 24.0D;
	/** Greeting range and the pause per player and character (ticks: 10 minutes). */
	public static final double SAPA_JARAK = 6.0D;
	public static final long SAPA_JEDA = 12_000L;
	/** The schedule file read for the {@code "tokoh"} table. */
	public static final Identifier BERKAS = Kultivator.id("tempat/jadwal/kompleks_sekte_tokoh.json");

	/** Compound titik the characters stand at (41.6.12). */
	public static final String TITIK_TETUA = "tetua";
	public static final String TITIK_JASA = TitikGabung.JASA;
	public static final String TITIK_HUKUM = TitikGabung.HUKUM;
	public static final String TITIK_PUNCAK = "puncak";

	/** Rupa keys of 40.7.4 for elders and the master (41V adds them to {@code sosok/resep.json}). */
	public static final String RUPA_TETUA = "tetua_sekte";
	public static final String RUPA_KETUA = "ketua_sekte";

	/** One scheduled hour of a character: the titik and the pose held there. */
	public record Jam(String titik, int klip, int kanan, int kiri) {
	}

	/** office -&gt; 24 slots (null = away: no body). Read lazily, dropped on a data-pack reload. */
	private static volatile @Nullable Map<String, Jam[]> jadwal;
	/** tokoh id -&gt; its body. */
	private static final Map<String, KultivatorNpc> TUBUH = new HashMap<>();
	/** tokoh id -&gt; the titik its body was placed at (to see when the schedule moved it). */
	private static final Map<String, String> TITIK_TUBUH = new HashMap<>();
	/** player|tokoh -&gt; game time of the last greeting. */
	private static final Map<String, Long> SAPA = new HashMap<>();

	// ================================================================== contract C41.W1

	/**
	 * The body of character {@code tokohId} of {@code sekte} in {@code level}: the live one, or a new one at its scheduled
	 * titik when a player is near and its titik is loaded. Null when the character is wounded, retired, away by schedule,
	 * nobody is near, or its titik is not loaded.
	 */
	public static @Nullable KultivatorNpc tubuh(ServerLevel level, String sekte, String tokohId) {
		KultivatorNpc live = TUBUH.get(tokohId);
		if (live != null && live.isAlive() && !live.isRemoved()) {
			return live;
		}
		MinecraftServer server = level.getServer();
		TokohSekte.Tokoh t = TokohSekte.tokoh(server, tokohId);
		if (t == null || !sekte.equals(t.sekte()) || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		Tempat tempat = tempat(server, t);
		if (tempat == null || !level.isLoaded(tempat.pos) || !adaPemain(level, tempat.pos, JARAK)) {
			return null;
		}
		return buat(level, t, tempat);
	}

	/** The tokoh id of a body made here ({@code s3#t2}), or null for every other NPC. */
	public static @Nullable String tokohDari(@Nullable KultivatorNpc npc) {
		if (npc == null) {
			return null;
		}
		String id = npc.memory().getStringOr("tokoh", "");
		return TokohSekte.idSah(id) ? id : null;
	}

	/** The office id ({@code agung} .. {@code penerimaan}, {@code ketua}) of a body made here, or null. */
	public static @Nullable String kantorDari(@Nullable KultivatorNpc npc) {
		String id = tokohDari(npc);
		if (id == null) {
			return null;
		}
		String kantor = npc.memory().getStringOr("gabung_kantor", "");
		return kantor.isEmpty() ? null : kantor;
	}

	// ================================================================== schedule

	/** The scheduled hour of office {@code kantor} at clock hour {@code jam} (0-23), or null (away). */
	public static @Nullable Jam jam(MinecraftServer server, String kantor, int jam) {
		Jam[] slots = jadwal(server).get(kantor);
		return slots == null ? null : slots[Math.floorMod(jam, 24)];
	}

	/** The clock hour (0-23) of the overworld now: tick 0 is 06:00, as in the 38NL schedules. */
	public static int jamSekarang(MinecraftServer server) {
		return (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
	}

	/** Drops the read schedule (data-pack reload). */
	static void lupakanJadwal() {
		jadwal = null;
	}

	private static Map<String, Jam[]> jadwal(MinecraftServer server) {
		Map<String, Jam[]> now = jadwal;
		if (now != null) {
			return now;
		}
		Map<String, Jam[]> read = baca(server);
		jadwal = read;
		return read;
	}

	private static Map<String, Jam[]> baca(MinecraftServer server) {
		Map<String, Jam[]> out = new HashMap<>();
		Optional<Resource> resource = server.getResourceManager().getResource(BERKAS);
		if (resource.isPresent()) {
			try (Reader reader = resource.get().openAsReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				JsonObject table = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("tokoh") : null;
				if (table != null) {
					for (Map.Entry<String, JsonElement> office : table.entrySet()) {
						if (office.getValue().isJsonObject()) {
							out.put(office.getKey(), slots(office.getKey(), office.getValue().getAsJsonObject()));
						}
					}
				}
			} catch (Exception e) {
				Kultivator.LOGGER.warn("Elder schedule {} could not be read; built-in hours used", BERKAS, e);
			}
		}
		if (out.isEmpty()) {
			bawaan(out);
		}
		return Map.copyOf(out);
	}

	private static Jam[] slots(String office, JsonObject hours) {
		Jam[] slots = new Jam[24];
		for (Map.Entry<String, JsonElement> e : hours.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject o = e.getValue().getAsJsonObject();
			String titik = o.has("titik") ? o.get("titik").getAsString() : TITIK_TETUA;
			Jam jam = new Jam(titik, klip(teks(o, "klip")), prop(teks(o, "kanan")), prop(teks(o, "kiri")));
			int[] range = rentang(e.getKey());
			if (range == null) {
				Kultivator.LOGGER.warn("Elder schedule: bad hour {} of {}", e.getKey(), office);
				continue;
			}
			for (int h = range[0]; ; h = (h + 1) % 24) {
				slots[h] = jam;
				if (h == range[1]) {
					break;
				}
			}
		}
		return slots;
	}

	/** Hours of the built-in fallback (the file is missing): every office at its own titik 06:00-20:00. */
	private static void bawaan(Map<String, Jam[]> out) {
		String[][] rows = {{"agung", TITIK_TETUA}, {"hukum", TITIK_HUKUM}, {"luar", TitikGabung.PAPAN_MISI}, {"ajar", TITIK_JASA},
				{"obat", TITIK_TETUA}, {"pusaka", TitikGabung.JASA_MEJA}, {TokohSekte.PENERIMAAN, TitikGabung.GERBANG_KOMPLEKS},
				{TokohSekte.KETUA, TITIK_PUNCAK}};
		for (String[] row : rows) {
			Jam[] slots = new Jam[24];
			for (int h = 6; h <= 20; h++) {
				slots[h] = new Jam(row[1], GerakId.id("sikap_tangan_belakang"), 0, 0);
			}
			out.put(row[0], slots);
		}
	}

	private static String teks(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	/** A clip by name: the shared catalogue, then 41's own clips 300-319. */
	static int klip(String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		int id = GerakId.id(nama);
		if (id > 0) {
			return id;
		}
		for (int i = 0; i < GabungGerakId.KLIP_NAMA.length; i++) {
			if (GabungGerakId.KLIP_NAMA[i].equals(nama)) {
				return GabungGerakId.KLIP_FIRST + i;
			}
		}
		Kultivator.LOGGER.warn("Elder schedule: unknown clip {}", nama);
		return 0;
	}

	/** A prop by name: the shared catalogue, then 41's own props 150-156. */
	static int prop(String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		int id = PropId.id(nama);
		if (id > 0) {
			return id;
		}
		for (int i = 0; i < GabungGerakId.PROP_NAMA.length; i++) {
			if (GabungGerakId.PROP_NAMA[i].equals(nama)) {
				return GabungGerakId.PROP_FIRST + i;
			}
		}
		Kultivator.LOGGER.warn("Elder schedule: unknown prop {}", nama);
		return 0;
	}

	private static int @Nullable [] rentang(String key) {
		try {
			String[] parts = key.trim().split("-");
			int a = Integer.parseInt(parts[0].trim());
			int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
			return a < 0 || a > 23 || b < 0 || b > 23 ? null : new int[]{a, b};
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ================================================================== the body driver (every 40 ticks, DuniaGabung)

	/** Where a character stands now: the titik name and the block (with its seat's offset). */
	record Tempat(String titik, BlockPos pos, Jam jam) {
	}

	/**
	 * Where {@code t} stands this hour, or null (away, wounded, retired, no place). Without a compound only the admissions
	 * and the pavilion office are seated at the gate desk.
	 */
	static @Nullable Tempat tempat(MinecraftServer server, TokohSekte.Tokoh t) {
		if (t.status() != TokohSekte.Status.AKTIF || t.terluka(Kota.day(server))) {
			return null;
		}
		Jam jam = jam(server, t.kantor(), jamSekarang(server));
		if (jam == null) {
			return null;
		}
		BlockPos base = TitikGabung.kompleks(t.sekte(), jam.titik());
		if (base == null) {
			boolean gerbangSaja = KompleksApi.spot(t.sekte(), TitikGabung.AULA) == null;
			if (!gerbangSaja || !(TokohSekte.PENERIMAAN.equals(t.kantor()) || "ajar".equals(t.kantor()) || t.penerimaan())) {
				return null;
			}
			base = TokohSekte.titikGerbang(server, t.sekte(), TitikGabung.GERBANG_LOKET);
			if (base == null) {
				return null;
			}
		}
		return new Tempat(jam.titik(), geser(base, t), jam);
	}

	/** Seats sharing one titik stand in a small ring (radius 2) so two elders never stack. */
	private static BlockPos geser(BlockPos base, TokohSekte.Tokoh t) {
		if (t.ketua()) {
			return base;
		}
		int n = t.id().charAt(t.id().length() - 1) - '0';
		double a = n * (Math.PI * 2.0D / 7.0D);
		return base.offset((int) Math.round(Math.cos(a) * 2.0D), 0, (int) Math.round(Math.sin(a) * 2.0D));
	}

	/** One pass: bodies for characters with a player near, poses, leaving bodies, greetings. */
	static void rawat(MinecraftServer server) {
		ServerLevel level = server.overworld();
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() == level && !p.isSpectator()) {
				players.add(p);
			}
		}
		// bodies that should leave: dead, wounded, retired, or the hour moved them, once nobody is close
		TUBUH.entrySet().removeIf(entry -> {
			KultivatorNpc npc = entry.getValue();
			if (npc == null || !npc.isAlive() || npc.isRemoved()) {
				TITIK_TUBUH.remove(entry.getKey());
				return true;
			}
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, entry.getKey());
			Tempat now = t == null ? null : tempat(server, t);
			boolean pindah = now == null || !now.titik().equals(TITIK_TUBUH.get(entry.getKey()));
			if (pindah && !dekat(players, npc.blockPosition(), PERGI_JARAK)) {
				npc.discard();
				TITIK_TUBUH.remove(entry.getKey());
				return true;
			}
			if (!pindah) {
				SosokApi.gerak(npc, now.jam().klip(), 0, 0, now.jam().kanan(), now.jam().kiri(), 0);
			}
			return false;
		});
		if (players.isEmpty()) {
			return;
		}
		for (String sekte : SekteApi.diplomasi().sects()) {
			List<TokohSekte.Tokoh> all = new ArrayList<>(TokohSekte.tetua(server, sekte));
			TokohSekte.Tokoh ketua = TokohSekte.ketua(server, sekte);
			if (ketua != null) {
				all.add(ketua);
			}
			for (TokohSekte.Tokoh t : all) {
				KultivatorNpc live = TUBUH.get(t.id());
				if (live != null && live.isAlive() && !live.isRemoved()) {
					sapa(server, players, live, t);
					continue;
				}
				Tempat tempat = tempat(server, t);
				if (tempat == null || !dekat(players, tempat.pos, JARAK) || !level.isLoaded(tempat.pos)) {
					continue;
				}
				buat(level, t, tempat);
			}
		}
	}

	private static @Nullable KultivatorNpc buat(ServerLevel level, TokohSekte.Tokoh t, Tempat tempat) {
		MinecraftServer server = level.getServer();
		TempatInfo info = TempatApi.tempatDi(level, tempat.pos);
		long situs = info == null ? 0L : info.situsId();
		int look = lookUntuk(server, t);
		BlockPos face = KompleksApi.spot(t.sekte(), TitikGabung.AULA);
		float yaw = face == null ? 0.0F : (float) (Math.toDegrees(Math.atan2(-(face.getX() - tempat.pos.getX()), face.getZ() - tempat.pos.getZ())));
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, NpcRole.TETUA_SEKTE, tempat.pos, yaw, look);
		if (npc == null) {
			return null;
		}
		String rupa = t.ketua() ? RupaKunci.ketuaSekteNpc(t.sekte()).format()
				: new RupaKunci(RUPA_TETUA, "npc:" + t.sekte(), t.dao().ordinal(), RupaKunci.PANGKAT_MAKS).format();
		SosokApi.setRupa(npc, rupa);
		Component plate = Component.translatable("kultivator.kota.papan_nama", Component.literal(t.nama()),
				Component.translatable("kultivator.gabung.tokoh.papan." + t.kantor(), sektePendek(t.sekte())));
		npc.setup(NpcRole.TETUA_SEKTE, 4, look, t.ranah().ordinal(), 1 + Math.floorMod(look >>> 3, 3), t.dao(), Math.floorMod(look >>> 9, 6),
				0, plate);
		SosokApi.setUsia(npc, dev.kultivator.sosok.Sosok.TUA);
		npc.memory().putString("tokoh", t.id());
		npc.memory().putString("gabung_kantor", t.kantor());
		npc.memory().putBoolean("hold", true);
		SosokApi.gerak(npc, tempat.jam.klip(), 0, 0, tempat.jam.kanan(), tempat.jam.kiri(), 0);
		TUBUH.put(t.id(), npc);
		TITIK_TUBUH.put(t.id(), tempat.titik);
		return npc;
	}

	/** A look seed whose body has the character's sex (the Sosok follows from role and look, 40.2.2). */
	private static int lookUntuk(MinecraftServer server, TokohSekte.Tokoh t) {
		int look = TokohSekte.look(server, t.id());
		int want = t.perempuan() ? dev.kultivator.sosok.Sosok.PEREMPUAN : dev.kultivator.sosok.Sosok.LAKI;
		for (int i = 0; i < 64; i++) {
			if (SosokGen.kelamin(NpcRole.TETUA_SEKTE, look + i * 0x61C88647) == want) {
				return look + i * 0x61C88647;
			}
		}
		return look;
	}

	private static Component sektePendek(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name != null ? name : Component.literal(sekte);
	}

	/** Greets a member or visitor walking past (office x relation lines, once per {@link #SAPA_JEDA}). */
	private static void sapa(MinecraftServer server, List<ServerPlayer> players, KultivatorNpc npc, TokohSekte.Tokoh t) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : players) {
			if (p.distanceToSqr(npc) > SAPA_JARAK * SAPA_JARAK || p.isCreative()) {
				continue;
			}
			String key = p.getUUID() + "|" + t.id();
			Long last = SAPA.get(key);
			if (last != null && now - last < SAPA_JEDA) {
				continue;
			}
			SAPA.put(key, now);
			String rel = hubungan(server, p, t);
			HidupApi.tatap(npc, p);
			if ("murid".equals(rel) || "anggota".equals(rel)) {
				npc.bow(30);
			}
			npc.say(Component.translatable("kultivator.gabung.tokoh.sapa." + t.kantor() + "." + rel, p.getName(), sektePendek(t.sekte())), 100);
			p.sendSystemMessage(Component.translatable("kultivator.gabung.tokoh.sapa.chat", t.namaGelar().withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.gabung.tokoh.sapa." + t.kantor() + "." + rel, p.getName(), sektePendek(t.sekte())))
					.withStyle(ChatFormatting.GRAY));
			kenang(server, p, t, rel);
			break;
		}
	}

	/** Anecdotes an elder may tell per office (lang {@code tokoh.cerita.<office>.0-3}); the later ones need a warmer attitude. */
	static final int KENANGAN = 4;

	/**
	 * After the greeting an elder sometimes tells a memory of their own (D-41W): a different one each day (a pure function of
	 * player, character and world day), the personal ones only once the attitude has grown ({@code 0, 15, 40, 70}; a Shifu
	 * counts 15 higher, a guest only ever hears the first). On the other days the elder's own temper speaks instead.
	 * A deserter or an enemy of the sect gets nothing.
	 */
	private static void kenang(MinecraftServer server, ServerPlayer p, TokohSekte.Tokoh t, String rel) {
		if ("musuh".equals(rel)) {
			return;
		}
		long h = TokohSekte.campur(p.getUUID().getLeastSignificantBits() ^ t.id().hashCode(), Kota.day(server));
		long dadu = Math.floorMod(h, 3L);
		if (dadu == 2L) {
			return;
		}
		Component isi;
		if (dadu == 1L && !t.sifat().isEmpty()) {
			isi = Component.translatable("kultivator.gabung.tokoh.ucap." + t.sifat().get((int) Math.floorMod(h >>> 8, (long) t.sifat().size())).id());
		} else {
			int sikap = TokohSekte.sikap(server, t.id(), p.getUUID()) + ("murid".equals(rel) ? 15 : 0);
			int terbuka = "tamu".equals(rel) ? 1 : 1 + (sikap >= 15 ? 1 : 0) + (sikap >= 40 ? 1 : 0) + (sikap >= 70 ? 1 : 0);
			int n = (int) Math.floorMod(h >>> 16, (long) Math.min(terbuka, KENANGAN));
			isi = Component.translatable("kultivator.gabung.tokoh.cerita." + t.kantor() + "." + n);
		}
		p.sendSystemMessage(Component.translatable("kultivator.gabung.tokoh.cerita.chat", t.namaGelar().withStyle(ChatFormatting.GOLD), isi)
				.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
	}

	/**
	 * The player's relation to the character for greeting lines: {@code murid} (the character is their Shifu),
	 * {@code anggota} (member of the sect), {@code musuh} (standing below -30 or a deserter), else {@code tamu}.
	 */
	public static String hubungan(MinecraftServer server, ServerPlayer player, TokohSekte.Tokoh t) {
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		if (k != null && k.anggotaDi(t.sekte())) {
			return t.id().equals(k.shifu) ? "murid" : "anggota";
		}
		if (k != null && k.riwayatSekte.containsKey(t.sekte())
				&& k.riwayatSekte.get(t.sekte()).status() == dev.kultivator.sekte.gabung.api.StatusKeluar.PEMBELOT) {
			return "musuh";
		}
		return SekteApi.diplomasi().standing(player.getUUID(), t.sekte()) < -30 ? "musuh" : "tamu";
	}

	/** True when member {@code k}'s rank makes NPC disciples bow (41.6.14: Inti and above). */
	public static boolean dihormati(Keanggotaan k) {
		return k != null && k.status.anggota() && k.pangkat.minimal(Pangkat.INTI);
	}

	private static boolean adaPemain(ServerLevel level, BlockPos pos, double jarak) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().distSqr(pos) <= jarak * jarak) {
				return true;
			}
		}
		return false;
	}

	private static boolean dekat(List<ServerPlayer> players, BlockPos pos, double jarak) {
		for (ServerPlayer p : players) {
			if (p.blockPosition().distSqr(pos) <= jarak * jarak) {
				return true;
			}
		}
		return false;
	}

	/** Every live body (tests, the budget line). */
	static Map<String, KultivatorNpc> hidup() {
		return Map.copyOf(TUBUH);
	}

	/** Server stop: forget bodies (they are {@code noSave}) and greetings. */
	static void bersih() {
		TUBUH.clear();
		TITIK_TUBUH.clear();
		SAPA.clear();
		jadwal = null;
	}

	/** Drops one player's greetings (logout). */
	static void lupakan(UUID player) {
		String prefix = player + "|";
		SAPA.keySet().removeIf(k -> k.startsWith(prefix));
	}

	private TokohAnchors() {
	}
}
