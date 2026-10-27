package dev.kultivator.sekte.gabung.hidup;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.GeneratorNama;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.sosok.SosokGen;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The disciple tournament of DESIGN 41.6.9 (contract C41.C1, owner 41C-K): every 15 world days (day
 * {@code (index * 5) % 15}, period {@code gabungSekte.turnamenHari}) at 12000 on the lei tai ({@code kompleks/arena}), a
 * bracket of {@link #BRACKET}: member players of rank Luar-Inti within 64 blocks plus the 39F roster by realm. Rounds are
 * Duel Pedang under sect rules (90 s, stop at 10 %); NPC against NPC with no player near is drawn as data
 * ({@code 0.5 + 0.12 x (P a - P b)}). Spectators: up to 8 compound disciples ({@code HidupApi.kerumunan}). Absence is never
 * punished: a player who walked away forfeits that bout and still gets the entry reward.
 * <p>
 * Bracket: seeds 1-8 by strength (1 v 8, 4 v 5, 2 v 7, 3 v 6); matches 0-3 quarter-finals, 4-5 semi-finals, 6 the final.
 * With a player near, each NPC of a bout gets a body on the lei tai ({@code HidupApi.tubuhStaf}, the sect's uniform and the
 * roster name), so NPC against NPC is a real duel the player watches; a player's bout is always a real duel
 * ({@code DuelChallenges.startDirect}, the 10 % line decides, nobody dies). The bracket is kept in
 * {@code DataSekteGabung.bracket} + {@code bagian("turnamen")}; a tournament cut by a server stop is finished by data on the
 * next pass.
 * <p>
 * The roster comes from {@link #setSumberRoster} (39F's {@code Roster} through 41A's public readers); without one the
 * compound's own disciples stand in, named from the sect name tables ({@link #roster}).
 */
public final class TurnamenMurid {

	public static final int BRACKET = 8;
	public static final int PERIODE_HARI = 15;
	public static final int MULAI_TICK = 12000;
	/** The tournament may still start until this time of day (dusk). */
	public static final int AKHIR_TICK = 18000;
	/** Seconds per round and the HP fraction a duel stops at. */
	public static final int DETIK_BABAK = 90;
	public static final float BERHENTI = 0.10F;
	/** PK rewards (41.6.2): champion, final, semifinal, entered (times the class factor {@code kf}). */
	public static final int PK_JUARA = 300;
	public static final int PK_FINAL = 150;
	public static final int PK_SEMIFINAL = 80;
	public static final int PK_IKUT = 20;
	/** Days the title "Juara Turnamen Murid" is shown on the card. */
	public static final int GELAR_HARI = 15;
	/** Players and bodies count within this range of the lei tai. */
	public static final double JARAK = 64.0D;
	/** Pause between two bouts and the longest a bout may run before data decides (ticks). */
	public static final int JEDA_TICK = 100;
	public static final int BATAS_TICK = (DETIK_BABAK + 60) * 20;
	/** Health share the sect healer restores a player fighter to after each bout. */
	public static final float RAWAT = 0.70F;
	/** Roster roles (39F {@code TokohSekte.PERAN_*}). */
	public static final int PERAN_LUAR = 0;
	public static final int PERAN_DALAM = 1;
	public static final int PERAN_INTI = 2;
	public static final int PERAN_KAKAK = 3;
	public static final int PERAN_TETUA = 4;
	/** Placements kept per member ({@link #terbaik}): champion, finalist, semi-finalist, entered. */
	public static final int JUARA = 1;
	public static final int FINALIS = 2;
	public static final int SEMIFINALIS = 3;
	public static final int PESERTA = 4;

	/**
	 * One named disciple of a sect's roster: id ({@code s3#4}), the display name, sex, rank step ({@code Rank.step}, realm
	 * units = step / 4), role ({@link #PERAN_LUAR} .. {@link #PERAN_TETUA}) and traits.
	 */
	public record Peserta(String id, Component nama, boolean perempuan, int langkah, int peran, List<Sifat> sifat) {
		public Peserta {
			sifat = sifat == null ? List.of() : List.copyOf(sifat);
		}

		public boolean punya(Sifat s) {
			return sifat.contains(s);
		}
	}

	/** Where the named disciples of a sect come from (41A wires 39F's {@code Roster} here). */
	public interface SumberRoster {
		/** The active, unwounded disciples of {@code sekte} (any order; empty when none). */
		List<Peserta> peserta(MinecraftServer server, String sekte);

		/** {@code pemain} beat roster disciple {@code id} in the tournament (the 39F grudge of a proud or vengeful one). */
		default void kalahDari(MinecraftServer server, String id, UUID pemain) {
		}
	}

	/** One bracket slot: a player (uuid) or a roster disciple; strength in realm units. */
	private record Slot(boolean pemain, String id, Component nama, boolean perempuan, int langkah, int peran, List<Sifat> sifat) {

		double kekuatan() {
			return langkah / 4.0D;
		}

		@Nullable UUID uuid() {
			if (!pemain) {
				return null;
			}
			try {
				return UUID.fromString(id);
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
	}

	/** A running tournament of one sect. */
	private static final class Laga {
		final String sekte;
		final BlockPos arena;
		final long situs;
		final Slot[] slot = new Slot[BRACKET];
		final int[] menang = {-1, -1, -1, -1, -1, -1, -1};
		int pertandingan;
		boolean bertarung;
		long sampai;
		@Nullable KultivatorNpc tubuhA;
		@Nullable KultivatorNpc tubuhB;
		@Nullable LivingEntity sisiA;
		@Nullable LivingEntity sisiB;
		int hasil = -1;

		Laga(String sekte, BlockPos arena, long situs) {
			this.sekte = sekte;
			this.arena = arena;
			this.situs = situs;
		}
	}

	private static final Map<String, Laga> LAGA = new HashMap<>();
	private static volatile @Nullable SumberRoster sumber;
	private static boolean initialised;
	private static long tickCount;
	private static final String BAGIAN = "turnamen";

	private TurnamenMurid() {
	}

	// ================================================================== pure rules

	/** True when world day {@code hari} is a tournament day of the sect with plan index {@code indeks}. */
	public static boolean hariTurnamen(int indeks, long hari) {
		long periode = Math.max(5, dev.kultivator.sekte.gabung.GabungConfig.get().turnamenHari);
		return Math.floorMod(hari, periode) == Math.floorMod(indeks * 5L, periode);
	}

	/** Data draw of an NPC-only round: the chance that side A wins from the two strengths (realm steps). */
	public static double peluangData(double kekuatanA, double kekuatanB) {
		return Math.max(0.05D, Math.min(0.95D, 0.5D + 0.12D * (kekuatanA - kekuatanB)));
	}

	/** A 64-bit mix of {@code seed} and {@code x} (splitmix64), for every seeded pick of 41C-K. */
	public static long campur(long seed, long x) {
		long z = seed + 0x9E3779B97F4A7C15L * (x + 1L);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// ================================================================== roster

	/** Wires the named disciples of 39F (41A; one source, the last call wins). */
	public static void setSumberRoster(@Nullable SumberRoster s) {
		sumber = s;
	}

	/**
	 * The named disciples of {@code sekte}: the registered roster, else the compound's own disciples as stand-ins
	 * ({@code 6 + tier}, at most 10; one Eldest Senior, core disciples from tier 3, then inner and outer ones), named from
	 * the sect name tables and seeded by world and sect so they are the same people every time.
	 */
	public static List<Peserta> roster(MinecraftServer server, String sekte) {
		SumberRoster s = sumber;
		if (s != null) {
			try {
				List<Peserta> list = s.peserta(server, sekte);
				if (list != null && !list.isEmpty()) {
					return list;
				}
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Gabung roster source failed for {}", sekte, exception);
			}
		}
		return cadangan(server, sekte);
	}

	private static List<Peserta> cadangan(MinecraftServer server, String sekte) {
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		if (e == null) {
			return List.of();
		}
		int tier = Math.max(1, GabungApi.tier(sekte));
		int n = Math.max(6, Math.min(10, 6 + tier));
		long seed = campur(server.overworld().getSeed(), e.lambangSeed());
		int master = 4 + 2 * tier;
		List<Peserta> out = new ArrayList<>(n);
		java.util.Set<Integer> used = new java.util.HashSet<>();
		for (int i = 0; i < n; i++) {
			long h = campur(seed, i);
			int peran = i == 0 ? PERAN_KAKAK : i <= 2 && tier >= 3 ? PERAN_INTI : i <= n / 2 ? PERAN_DALAM : PERAN_LUAR;
			int marga = (int) Math.floorMod(h, (long) GeneratorNama.MARGA.length);
			int nama = (int) Math.floorMod(h >>> 12, (long) GeneratorNama.NAMA.length);
			for (int guard = 0; guard < GeneratorNama.NAMA.length && !used.add(marga * 64 + nama); guard++) {
				nama = (nama + 1) % GeneratorNama.NAMA.length;
			}
			int turun = peran == PERAN_KAKAK ? 2 : peran == PERAN_INTI ? 3 : peran == PERAN_DALAM ? 5 : 7;
			int langkah = Math.max(1, master - turun - (int) Math.floorMod(h >>> 24, 3L));
			List<Sifat> sifat = List.of(Sifat.values()[(int) Math.floorMod(h >>> 32, (long) Sifat.values().length)]);
			out.add(new Peserta(sekte + "#c" + i, Component.literal(GeneratorNama.MARGA[marga] + " " + GeneratorNama.NAMA[nama]),
					((h >>> 40) & 1L) == 1L, langkah, peran, sifat));
		}
		return out;
	}

	// ================================================================== reads for other modules

	/** The best placement {@code k} ever reached ({@link #JUARA} .. {@link #PESERTA}, 0 never entered). 41A: "3 besar". */
	public static int terbaik(Keanggotaan k) {
		return k.bagian(BAGIAN).getIntOr("terbaik", 0);
	}

	/** "Juara Turnamen Murid Sekte X" while the title lasts (15 days, 41.6.9), else null. */
	public static @Nullable Component gelar(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag t = k.bagian(BAGIAN);
		String sekte = k.sekteAktif();
		if (sekte == null || !sekte.equals(t.getStringOr("juaraSekte", "")) || t.getLongOr("juaraSampai", -1L) < GabungApi.hari(player.level().getServer())) {
			return null;
		}
		return Component.translatable("kultivator.gabung.turnamen.gelar", Kediaman.nama(sekte)).withColor(0xFFD24A);
	}

	/** The last champion of {@code sekte}: a player UUID string or a roster id, or null (41C {@code KuotaAlam}). */
	public static @Nullable String juaraTerakhir(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		String id = d == null ? "" : d.bagian(BAGIAN).getStringOr("juara", "");
		return id.isEmpty() ? null : id;
	}

	/**
	 * The member's tournament card for the sect board ({@code gabung_papan} key {@code turnamen}; Sekte › Kegiatan › Arena
	 * of an NPC-sect member, DESIGN 43.5): {@code hari} L the next tournament day (today until it ends; absent without a
	 * compound), {@code berjalan} B, {@code juara} T the last champion and {@code juara_hari} L (absent none),
	 * {@code terbaik} I the member's best placement ({@link #JUARA} .. {@link #PESERTA}, 0 never entered).
	 */
	public static CompoundTag lembar(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		CompoundTag out = new CompoundTag();
		long hari = GabungApi.hari(server);
		boolean jalan = berjalan(sekte);
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		if (e != null) {
			// today counts while it has not ended (not past its hours, not already fought)
			boolean usaiHariIni = !jalan && (d != null && d.turnamenHari == hari || dev.kultivator.dunia.kota.Kota.timeOfDay(server) >= AKHIR_TICK);
			long periode = Math.max(5, dev.kultivator.sekte.gabung.GabungConfig.get().turnamenHari);
			for (long i = usaiHariIni ? 1L : 0L; i <= periode; i++) {
				if (hariTurnamen(e.index(), hari + i)) {
					out.putLong("hari", hari + i);
					break;
				}
			}
		}
		out.putBoolean("berjalan", jalan);
		if (d != null) {
			CompoundTag bagian = d.bagian(BAGIAN);
			Tag nama = bagian.get("juaraNama");
			if (nama != null) {
				out.store("juara", ComponentSerialization.CODEC, bacaKomponen(server, nama));
				out.putLong("juara_hari", bagian.getLongOr("juaraHari", -1L));
			}
		}
		out.putInt("terbaik", terbaik(GabungApi.anggota(player)));
		return out;
	}

	/** True while the tournament of {@code sekte} runs. */
	public static boolean berjalan(String sekte) {
		return LAGA.containsKey(sekte);
	}

	// ================================================================== wiring

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuelEndListeners.add(TurnamenMurid::duelAkhir);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			for (Laga laga : LAGA.values()) {
				hapusTubuh(laga);
			}
			LAGA.clear();
		});
	}

	/** From {@link SitusGabung}'s tick, every 20 ticks: start checks (every 10 s), running bouts. */
	static void tick(MinecraftServer server) {
		tickCount++;
		if (tickCount % 10L == 0L) {
			periksaJadwal(server);
		}
		for (Laga laga : List.copyOf(LAGA.values())) {
			try {
				jalan(server, laga);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Disciple tournament of {} failed; finishing by data", laga.sekte, exception);
				hapusTubuh(laga);
				selesaikanData(server, laga);
			}
		}
	}

	private static void periksaJadwal(MinecraftServer server) {
		if (!GabungApi.aktif()) {
			return;
		}
		long hari = GabungApi.hari(server);
		long jam = dev.kultivator.dunia.kota.Kota.timeOfDay(server);
		GabungData data = GabungData.get(server);
		for (String sekte : SekteApi.diplomasi().sects()) {
			if (LAGA.containsKey(sekte)) {
				continue;
			}
			GabungData.DataSekteGabung d = data.peekSekte(sekte);
			if (d != null && d.turnamenHari == hari && !d.bagian(BAGIAN).getBooleanOr("selesai", true)) {
				// cut by a server stop: finish the rest by data
				Laga laga = pulihkan(server, sekte, d);
				if (laga != null) {
					selesaikanData(server, laga);
				}
				continue;
			}
			GerbangPlan.Entry e = GabungApi.entry(server, sekte);
			if (e == null || jam < MULAI_TICK || jam >= AKHIR_TICK || !hariTurnamen(e.index(), hari)) {
				continue;
			}
			if (d != null && d.turnamenHari == hari) {
				continue;
			}
			if (!mulai(server, sekte)) {
				data.sekte(sekte).turnamenHari = hari;
				data.setDirty();
			}
		}
	}

	// ================================================================== start

	/** Starts the tournament of {@code sekte} now (its day, or a test); false when it cannot run (no compound, running). */
	public static boolean mulai(MinecraftServer server, String sekte) {
		if (sekte == null || LAGA.containsKey(sekte) || GabungApi.tier(sekte) <= 0) {
			return false;
		}
		BlockPos arena = GabungApi.titik(sekte, TitikGabung.ARENA);
		if (arena == null) {
			return false;
		}
		ServerLevel level = server.overworld();
		if (arena.getY() == 0 && level.isLoaded(arena)) {
			// the planned gate column (no compound, gate not yet seen): stand on the ground there
			arena = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arena);
		}
		TempatInfo info = TempatApi.tempatDi(level, arena);
		Laga laga = new Laga(sekte, arena.immutable(), info == null ? 0L : info.situsId());
		List<Slot> entrants = new ArrayList<>();
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			Keanggotaan k = data.peek(uuid);
			if (p == null || k == null || !dekat(p, laga.arena)) {
				continue;
			}
			Pangkat pk = k.pangkat;
			if (pk != Pangkat.LUAR && pk != Pangkat.DALAM && pk != Pangkat.INTI) {
				continue;
			}
			entrants.add(new Slot(true, uuid.toString(), p.getDisplayName(), false, Cultivators.get(p).rank().step(), PERAN_DALAM, List.of()));
		}
		entrants.sort(Comparator.comparingDouble(Slot::kekuatan).reversed());
		while (entrants.size() > BRACKET) {
			entrants.removeLast();
		}
		List<Peserta> roster = new ArrayList<>(roster(server, sekte));
		roster.removeIf(p -> p.peran() > PERAN_KAKAK);
		roster.sort(Comparator.comparingInt(Peserta::langkah).reversed());
		for (Peserta p : roster) {
			if (entrants.size() >= BRACKET) {
				break;
			}
			entrants.add(new Slot(false, p.id(), p.nama(), p.perempuan(), p.langkah(), p.peran(), p.sifat()));
		}
		if (entrants.size() < 2) {
			return false;
		}
		entrants.sort(Comparator.comparingDouble(Slot::kekuatan).reversed());
		// seeds 1 v 8, 4 v 5, 2 v 7, 3 v 6 (byes go to the top seeds)
		int[] posisi = {0, 7, 3, 4, 1, 6, 2, 5};
		for (int s = 0; s < BRACKET; s++) {
			laga.slot[posisi[s]] = s < entrants.size() ? entrants.get(s) : null;
		}
		long hari = GabungApi.hari(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		d.turnamenHari = hari;
		CompoundTag bagian = d.bagian(BAGIAN);
		bagian.putBoolean("selesai", false);
		bagian.putLong("hari", hari);
		simpan(server, laga);
		LAGA.put(sekte, laga);
		laga.sampai = level.getGameTime() + JEDA_TICK;
		if (adaPemainDekat(level, laga.arena)) {
			HidupApi.kerumunan(level, laga.arena, 600);
			level.playSound(null, laga.arena, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.4F, 0.9F);
			siar(level, laga, Component.translatable("kultivator.gabung.turnamen.mulai", Kediaman.nama(sekte)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			for (int m = 0; m < 4; m++) {
				Slot a = laga.slot[m * 2];
				Slot b = laga.slot[m * 2 + 1];
				siar(level, laga, Component.translatable("kultivator.gabung.turnamen.bagan", m + 1, namaSlot(a), namaSlot(b)).withColor(0xE8D9A8));
			}
		}
		for (Slot s : laga.slot) {
			ServerPlayer p = s == null || !s.pemain() ? null : server.getPlayerList().getPlayer(s.uuid());
			if (p != null) {
				Msg.title(p, Component.translatable("kultivator.gabung.turnamen.judul"), Component.translatable("kultivator.gabung.turnamen.ikut_sub"), 10, 50, 10);
			}
		}
		return true;
	}

	// ================================================================== the running tournament

	private static void jalan(MinecraftServer server, Laga laga) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (laga.bertarung) {
			boolean habis = now >= laga.sampai || laga.hasil >= 0 || sisiHilang(laga);
			if (!habis) {
				return;
			}
			int pemenang = laga.hasil;
			if (pemenang < 0) {
				pemenang = putusanSisa(level, laga);
			}
			hapusTubuh(laga);
			laga.bertarung = false;
			catatHasil(server, level, laga, pemenang, false);
			return;
		}
		if (now < laga.sampai) {
			return;
		}
		if (laga.pertandingan >= laga.menang.length) {
			selesai(server, laga);
			return;
		}
		int m = laga.pertandingan;
		int[] pair = pasangan(laga, m);
		int ia = pair[0];
		int ib = pair[1];
		Slot a = ia < 0 ? null : laga.slot[ia];
		Slot b = ib < 0 ? null : laga.slot[ib];
		if (a == null || b == null) {
			catatHasil(server, level, laga, a != null ? ia : b != null ? ib : -1, true);
			return;
		}
		boolean ramai = adaPemainDekat(level, laga.arena);
		ServerPlayer pa = pemainDi(server, a, laga.arena);
		ServerPlayer pb = pemainDi(server, b, laga.arena);
		if (a.pemain() && pa == null || b.pemain() && pb == null) {
			// a player who walked away forfeits this bout (no punishment)
			int w = a.pemain() && pa == null ? (b.pemain() && pb == null ? acak(level, a, b, ia, ib) : ib) : ia;
			siar(level, laga, Component.translatable("kultivator.gabung.turnamen.mundur", namaSlot(w == ia ? b : a)).withStyle(ChatFormatting.GRAY));
			catatHasil(server, level, laga, w, false);
			return;
		}
		if (!ramai && pa == null && pb == null) {
			catatHasil(server, level, laga, acak(level, a, b, ia, ib), false);
			return;
		}
		Component why = tarung(level, laga, a, b, pa, pb);
		if (why != null) {
			hapusTubuh(laga);
			siar(level, laga, Component.translatable("kultivator.gabung.turnamen.adu_data", why).withStyle(ChatFormatting.GRAY));
			catatHasil(server, level, laga, acak(level, a, b, ia, ib), false);
			return;
		}
		laga.bertarung = true;
		laga.hasil = -1;
		laga.sampai = now + BATAS_TICK;
		String babak = m < 4 ? "perempat" : m < 6 ? "semi" : "final";
		siar(level, laga, Component.translatable("kultivator.gabung.turnamen.laga." + babak, namaSlot(a), namaSlot(b)).withColor(0xFFE08A));
		level.playSound(null, laga.arena, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, m == 6 ? 0.7F : 1.0F);
	}

	/** Slot indices fighting in match {@code m} (-1 for a missing side). */
	private static int[] pasangan(Laga laga, int m) {
		if (m < 4) {
			return new int[]{m * 2, m * 2 + 1};
		}
		if (m < 6) {
			int base = (m - 4) * 2;
			return new int[]{laga.menang[base], laga.menang[base + 1]};
		}
		return new int[]{laga.menang[4], laga.menang[5]};
	}

	/** Puts both sides on the lei tai and starts the duel; null when it runs, else the reason. */
	private static @Nullable Component tarung(ServerLevel level, Laga laga, Slot a, Slot b, @Nullable ServerPlayer pa, @Nullable ServerPlayer pb) {
		Vec3 centre = Vec3.atBottomCenterOf(laga.arena);
		LivingEntity ea = pa;
		LivingEntity eb = pb;
		if (ea == null) {
			laga.tubuhA = tubuh(level, laga, a, centre.add(-4.0D, 0.0D, 0.0D), -90.0F);
			ea = laga.tubuhA;
		}
		if (eb == null) {
			laga.tubuhB = tubuh(level, laga, b, centre.add(4.0D, 0.0D, 0.0D), 90.0F);
			eb = laga.tubuhB;
		}
		if (ea == null || eb == null) {
			return Component.translatable("kultivator.gabung.turnamen.tanpa_tubuh");
		}
		for (ServerPlayer p : new ServerPlayer[]{pa, pb}) {
			if (p != null) {
				Component self = DuelChallenges.selfProblem(p);
				if (self != null) {
					return self;
				}
			}
		}
		int gap = 0;
		if (pa != null && eb instanceof KultivatorNpc npc) {
			gap = Cultivators.get(pa).realm().ordinal() - npc.realm();
		} else if (pb != null && ea instanceof KultivatorNpc npc) {
			gap = Cultivators.get(pb).realm().ordinal() - npc.realm();
		} else if (pa != null && pb != null) {
			gap = Cultivators.get(pa).realm().ordinal() - Cultivators.get(pb).realm().ordinal();
		}
		int pair = pa != null && pb != null ? DuelIds.PAIR_PVP : DuelIds.PAIR_NPC;
		ServerPlayer challenger = pa != null ? pa : pb;
		// the player stands as side a of the session, as in every place bout (TandingTempat)
		boolean tukar = pa == null && pb != null;
		Component why = tukar ? DuelChallenges.startDirect(challenger, eb, ea, pair, aturan(gap))
				: DuelChallenges.startDirect(challenger, ea, eb, pair, aturan(gap));
		if (why != null) {
			return why;
		}
		laga.sisiA = ea;
		laga.sisiB = eb;
		return null;
	}

	/** Sect rules of 41.6.9: the 10 % line, no stake, short intro, the crowd watches, 90 s. */
	private static DuelRuleSet aturan(int gap) {
		DuelRuleSet r = DuelRuleSet.suggested(gap).withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN).withoutStake().withIntro(false).withCrowd(true);
		return new DuelRuleSet(r.realm(), r.jurus(), r.win(), r.radius(), DETIK_BABAK * 20, r.introFilm(), r.stake(), r.stakeGrade(),
				r.stakeCount(), r.slipSpec(), r.crowd());
	}

	/** A body for roster disciple {@code s} on the lei tai: uniform, name plate, realm from its rank step, a wooden-style blade. */
	private static @Nullable KultivatorNpc tubuh(ServerLevel level, Laga laga, Slot s, Vec3 at, float yaw) {
		NpcRole role = s.peran() >= PERAN_DALAM ? NpcRole.MURID_SEKTE_DALAM : NpcRole.MURID_SEKTE_LUAR;
		String pangkat = switch (s.peran()) {
			case PERAN_LUAR -> "luar";
			case PERAN_DALAM -> "dalam";
			default -> "inti";
		};
		String rupa = "murid." + pangkat;
		int look = (int) campur(level.getSeed(), s.id().hashCode());
		for (int i = 0; i < 16 && SosokGen.untukRupa(rupa, role, look).perempuan() != s.perempuan(); i++) {
			look = look * 31 + 7;
		}
		KultivatorNpc npc = HidupApi.tubuhStaf(level, laga.situs, role, BlockPos.containing(at), yaw, look);
		if (npc == null) {
			return null;
		}
		Rank rank = Rank.ofStep(Mth.clamp(s.langkah(), 0, Rank.MAX_STEP));
		Corak corak = GabungApi.corak(laga.sekte);
		Dao dao = corak.daoUtama();
		SosokApi.setRupa(npc, rupa + "|sekte=npc:" + laga.sekte);
		npc.setup(role, look, rank.realm().ordinal(), Mth.clamp(rank.stage(), 0, 3), dao, look >>> 3, 0, s.nama());
		npc.setPlate(s.nama());
		npc.memory().putBoolean("hold", false);
		npc.memory().putString("gabung_turnamen", laga.sekte);
		if (npc.getMainHandItem().isEmpty()) {
			npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
		DuelistRegistry.override(npc, profil(npc, s, corak));
		return npc;
	}

	/** The bout profile of a roster body: style from its traits, rank from realm and stage, surrender at 15 %. */
	private static DuelistProfile profil(KultivatorNpc npc, Slot s, Corak corak) {
		DuelStyle gaya = DuelStyle.BERTAHAN;
		if (s.peran() == PERAN_KAKAK) {
			gaya = DuelStyle.SANTO_PEDANG;
		} else if (s.sifat().contains(Sifat.LICIK)) {
			gaya = DuelStyle.PENIPU;
		} else if (s.sifat().contains(Sifat.SOMBONG) || s.sifat().contains(Sifat.AMBISIUS) || s.sifat().contains(Sifat.BERANI)) {
			gaya = DuelStyle.AGRESIF;
		}
		Realm realm = npc.realmValue();
		int stage = Mth.clamp(npc.stage(), 0, Math.max(0, realm.stages() - 1));
		int step = new Rank(realm, 0, stage).step();
		String kiai = npc.female() ? "grunt.female.attack" : "grunt.male.attack";
		return new DuelistProfile(gaya, step, WeaponClass.of(npc), corak.warna(), kiai, 1000 + 25 * npc.realm(), Math.max(0.15F, gaya.surrender),
				0, 0, null, false);
	}

	private static boolean sisiHilang(Laga laga) {
		for (LivingEntity e : new LivingEntity[]{laga.sisiA, laga.sisiB}) {
			if (e == null || e.isRemoved() || !e.isAlive()) {
				return true;
			}
		}
		return false;
	}

	/** A bout that did not end by itself: the side with more health share wins, data decides a tie. */
	private static int putusanSisa(ServerLevel level, Laga laga) {
		int[] pair = pasangan(laga, laga.pertandingan);
		LivingEntity a = laga.sisiA;
		LivingEntity b = laga.sisiB;
		boolean aOk = a != null && a.isAlive() && !a.isRemoved();
		boolean bOk = b != null && b.isAlive() && !b.isRemoved();
		if (aOk && !bOk) {
			return pair[0];
		}
		if (bOk && !aOk) {
			return pair[1];
		}
		if (aOk) {
			float fa = a.getHealth() / Math.max(1.0F, a.getMaxHealth());
			float fb = b.getHealth() / Math.max(1.0F, b.getMaxHealth());
			if (Math.abs(fa - fb) > 0.02F) {
				return fa > fb ? pair[0] : pair[1];
			}
		}
		return acak(level, laga.slot[pair[0]], laga.slot[pair[1]], pair[0], pair[1]);
	}

	/** The data draw between two slots (seeded by the game time so a replayed test differs). */
	private static int acak(ServerLevel level, Slot a, Slot b, int ia, int ib) {
		double p = peluangData(a.kekuatan(), b.kekuatan());
		return level.getRandom().nextDouble() < p ? ia : ib;
	}

	/** The duel end of a running bout (both sides are known entities of the current match). */
	private static void duelAkhir(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Laga laga : LAGA.values()) {
			if (!laga.bertarung || laga.sisiA == null || laga.sisiB == null) {
				continue;
			}
			boolean ours = session.a() == laga.sisiA && session.b() == laga.sisiB || session.a() == laga.sisiB && session.b() == laga.sisiA;
			if (!ours) {
				continue;
			}
			int[] pair = pasangan(laga, laga.pertandingan);
			if (winner == laga.sisiA) {
				laga.hasil = pair[0];
			} else if (winner == laga.sisiB) {
				laga.hasil = pair[1];
			} else if (loser == laga.sisiA) {
				laga.hasil = pair[1];
			} else if (loser == laga.sisiB) {
				laga.hasil = pair[0];
			} else if (laga.sisiA.level() instanceof ServerLevel level) {
				// a draw or a cancel: data decides
				laga.hasil = acak(level, laga.slot[pair[0]], laga.slot[pair[1]], pair[0], pair[1]);
			}
			return;
		}
	}

	/** Records the winner of the current match, narrates it and schedules the next one. */
	private static void catatHasil(MinecraftServer server, ServerLevel level, Laga laga, int pemenang, boolean bye) {
		int m = laga.pertandingan;
		int[] pair = pasangan(laga, m);
		laga.menang[m] = pemenang;
		laga.pertandingan++;
		laga.sampai = level.getGameTime() + (bye ? 1 : JEDA_TICK);
		simpan(server, laga);
		if (bye || pemenang < 0) {
			return;
		}
		Slot w = laga.slot[pemenang];
		int kalahIdx = pemenang == pair[0] ? pair[1] : pair[0];
		Slot l = kalahIdx < 0 ? null : laga.slot[kalahIdx];
		if (l == null) {
			return;
		}
		siar(level, laga, Component.translatable("kultivator.gabung.turnamen.menang", namaSlot(w), namaSlot(l)).withColor(0xBFE6A8));
		for (Slot s : new Slot[]{w, l}) {
			ServerPlayer p = s.pemain() && s.uuid() != null ? server.getPlayerList().getPlayer(s.uuid()) : null;
			if (p != null && p.isAlive() && p.getHealth() < p.getMaxHealth() * RAWAT) {
				// the sect's healer tends the fighters between bouts (nobody climbs the lei tai at 10 %)
				p.setHealth(p.getMaxHealth() * RAWAT);
				Msg.actionBar(p, Component.translatable("kultivator.gabung.turnamen.rawat").withColor(0x9FE0C0));
			}
		}
		if (w.pemain() && !l.pemain()) {
			SumberRoster s = sumber;
			UUID uuid = w.uuid();
			if (s != null && uuid != null && (l.sifat().contains(Sifat.SOMBONG) || l.sifat().contains(Sifat.PENDENDAM))) {
				try {
					s.kalahDari(server, l.id(), uuid);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.warn("Gabung roster grudge failed", exception);
				}
			}
		}
	}

	/** Ends the tournament: placements, PK, the title, the champion's moment and the news to the sect's members. */
	private static void selesai(MinecraftServer server, Laga laga) {
		ServerLevel level = server.overworld();
		LAGA.remove(laga.sekte);
		hapusTubuh(laga);
		int juara = laga.menang[6];
		if (juara < 0) {
			juara = laga.menang[4] >= 0 ? laga.menang[4] : laga.menang[5];
		}
		int[] tempat = new int[BRACKET];
		for (int i = 0; i < BRACKET; i++) {
			tempat[i] = laga.slot[i] == null ? 0 : PESERTA;
		}
		for (int m = 4; m <= 5; m++) {
			int[] pair = pasangan(laga, m);
			for (int idx : pair) {
				if (idx >= 0 && laga.slot[idx] != null) {
					tempat[idx] = SEMIFINALIS;
				}
			}
		}
		for (int idx : pasangan(laga, 6)) {
			if (idx >= 0 && laga.slot[idx] != null) {
				tempat[idx] = FINALIS;
			}
		}
		if (juara >= 0) {
			tempat[juara] = JUARA;
		}
		long hari = GabungApi.hari(server);
		double kf = GabungApi.kelasSekte(laga.sekte).kf();
		GabungData data = GabungData.get(server);
		for (int i = 0; i < BRACKET; i++) {
			Slot s = laga.slot[i];
			if (s == null || !s.pemain() || s.uuid() == null) {
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(s.uuid());
			Keanggotaan k = data.peek(s.uuid());
			if (k == null) {
				continue;
			}
			CompoundTag t = k.bagian(BAGIAN);
			int best = t.getIntOr("terbaik", 0);
			if (best == 0 || tempat[i] < best) {
				t.putInt("terbaik", tempat[i]);
			}
			t.putInt("ikut", t.getIntOr("ikut", 0) + 1);
			if (tempat[i] == JUARA) {
				t.putLong("juaraSampai", hari + GELAR_HARI - 1L);
				t.putString("juaraSekte", laga.sekte);
				t.putInt("juaraKali", t.getIntOr("juaraKali", 0) + 1);
			}
			data.setDirty();
			if (p == null) {
				continue;
			}
			int base = switch (tempat[i]) {
				case JUARA -> PK_JUARA;
				case FINALIS -> PK_FINAL;
				case SEMIFINALIS -> PK_SEMIFINAL;
				default -> PK_IKUT;
			};
			GabungApi.tambahPk(p, (int) Math.round(base * kf), "gabung.turnamen");
			if (tempat[i] <= SEMIFINALIS && k.pangkat == Pangkat.DALAM) {
				dev.kultivator.sekte.gabung.Promosi.lulus(p, Pangkat.INTI); // 41.7: top 3 of the Disciple Tournament opens Inti
			}
			Msg.chat(p, Component.translatable("kultivator.gabung.turnamen.hadiah." + tempat[i], (int) Math.round(base * kf)).withColor(0xFFD24A));
			if (tempat[i] == JUARA) {
				Advancements.grant(p, "sekte/gabung_turnamen");
				GabungPayloads.kirimKlip(p, GabungGerakId.SALAM_SEKTE, 0);
				level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2D, p.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.02D);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
				Msg.title(p, Component.translatable("kultivator.gabung.turnamen.juara_judul"), Component.translatable("kultivator.gabung.turnamen.gelar",
						Kediaman.nama(laga.sekte)), 10, 70, 20);
			}
		}
		GabungData.DataSekteGabung d = data.sekte(laga.sekte);
		CompoundTag bagian = d.bagian(BAGIAN);
		bagian.putBoolean("selesai", true);
		Slot champion = juara >= 0 ? laga.slot[juara] : null;
		if (champion != null) {
			bagian.putString("juara", champion.id());
			Tag namaTag = komponen(server, champion.nama());
			if (namaTag != null) {
				bagian.put("juaraNama", namaTag);
			}
			bagian.putLong("juaraHari", hari);
			dev.kultivator.sekte.gabung.dunia.KabarGabung.turnamen(server, laga.sekte, namaSlot(champion).getString());
		}
		simpan(server, laga);
		data.setDirty();
		Component kabar = champion == null ? null
				: Component.translatable("kultivator.gabung.turnamen.juara", namaSlot(champion), Kediaman.nama(laga.sekte)).withStyle(ChatFormatting.GOLD);
		if (kabar != null) {
			siar(level, laga, kabar);
			for (UUID uuid : data.anggotaSekte(laga.sekte)) {
				ServerPlayer p = server.getPlayerList().getPlayer(uuid);
				if (p != null && !dekat(p, laga.arena)) {
					Msg.chat(p, kabar);
				}
			}
			KultivatorNpc speaker = null;
			for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new net.minecraft.world.phys.AABB(laga.arena).inflate(24.0D))) {
				if (npc.role() == NpcRole.TETUA_SEKTE || speaker == null) {
					speaker = npc;
				}
			}
			if (speaker != null) {
				Voice.say(speaker, "gabung.turnamen_juara", null);
			}
		}
		if (laga.situs != 0L) {
			HidupApi.bubarkan(level, laga.situs);
		}
		level.playSound(null, laga.arena, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.4F, 1.1F);
	}

	/** Finishes every open match by data (no player near, a cut tournament, a failure). */
	private static void selesaikanData(MinecraftServer server, Laga laga) {
		ServerLevel level = server.overworld();
		LAGA.putIfAbsent(laga.sekte, laga);
		laga.bertarung = false;
		while (laga.pertandingan < laga.menang.length) {
			int[] pair = pasangan(laga, laga.pertandingan);
			Slot a = pair[0] < 0 ? null : laga.slot[pair[0]];
			Slot b = pair[1] < 0 ? null : laga.slot[pair[1]];
			int w = a == null ? (b == null ? -1 : pair[1]) : b == null ? pair[0] : acak(level, a, b, pair[0], pair[1]);
			catatHasil(server, level, laga, w, a == null || b == null);
		}
		selesai(server, laga);
	}

	// ================================================================== saving and narration

	private static void simpan(MinecraftServer server, Laga laga) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(laga.sekte);
		d.bracket.clear();
		for (Slot s : laga.slot) {
			CompoundTag t = new CompoundTag();
			if (s != null) {
				t.putBoolean("pemain", s.pemain());
				t.putString("id", s.id());
				Tag nama = komponen(server, s.nama());
				if (nama != null) {
					t.put("nama", nama);
				}
				t.putBoolean("perempuan", s.perempuan());
				t.putInt("langkah", s.langkah());
				t.putInt("peran", s.peran());
				ListTag sifat = new ListTag();
				for (Sifat f : s.sifat()) {
					sifat.add(StringTag.valueOf(f.name()));
				}
				t.put("sifat", sifat);
			}
			d.bracket.add(t);
		}
		CompoundTag bagian = d.bagian(BAGIAN);
		bagian.putIntArray("menang", laga.menang.clone());
		bagian.putInt("pertandingan", laga.pertandingan);
		data.setDirty();
	}

	private static @Nullable Laga pulihkan(MinecraftServer server, String sekte, GabungData.DataSekteGabung d) {
		BlockPos arena = GabungApi.titik(sekte, TitikGabung.ARENA);
		Laga laga = new Laga(sekte, arena == null ? BlockPos.ZERO : arena.immutable(), 0L);
		for (int i = 0; i < BRACKET && i < d.bracket.size(); i++) {
			CompoundTag t = d.bracket.get(i);
			if (!t.contains("id")) {
				continue;
			}
			List<Sifat> sifat = new ArrayList<>();
			ListTag list = t.getListOrEmpty("sifat");
			for (int j = 0; j < list.size(); j++) {
				try {
					sifat.add(Sifat.valueOf(list.getStringOr(j, "")));
				} catch (IllegalArgumentException ignored) {
					// an unknown trait is dropped
				}
			}
			Component nama = t.contains("nama") ? bacaKomponen(server, t.get("nama")) : Component.literal(t.getStringOr("id", "?"));
			laga.slot[i] = new Slot(t.getBooleanOr("pemain", false), t.getStringOr("id", ""), nama, t.getBooleanOr("perempuan", false),
					t.getIntOr("langkah", 4), t.getIntOr("peran", PERAN_DALAM), sifat);
		}
		CompoundTag bagian = d.bagian(BAGIAN);
		int[] menang = bagian.getIntArray("menang").orElse(new int[0]);
		for (int i = 0; i < laga.menang.length && i < menang.length; i++) {
			laga.menang[i] = menang[i];
		}
		laga.pertandingan = Mth.clamp(bagian.getIntOr("pertandingan", 0), 0, laga.menang.length);
		return laga;
	}

	private static @Nullable Tag komponen(MinecraftServer server, Component c) {
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ComponentSerialization.CODEC.encodeStart(ops, c).result().orElse(null);
	}

	private static Component bacaKomponen(MinecraftServer server, @Nullable Tag tag) {
		if (tag == null) {
			return Component.literal("?");
		}
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.literal("?"));
	}

	private static Component namaSlot(@Nullable Slot s) {
		return s == null ? Component.translatable("kultivator.gabung.turnamen.kosong").withStyle(ChatFormatting.DARK_GRAY) : s.nama();
	}

	/** Tells every player within {@link #JARAK} blocks of the lei tai. */
	private static void siar(ServerLevel level, Laga laga, Component line) {
		for (ServerPlayer p : level.players()) {
			if (dekat(p, laga.arena)) {
				Msg.chat(p, line);
			}
		}
	}

	private static boolean dekat(ServerPlayer p, BlockPos arena) {
		return p.level() == p.level().getServer().overworld()
				&& p.position().distanceToSqr(arena.getX() + 0.5D, arena.getY(), arena.getZ() + 0.5D) <= JARAK * JARAK;
	}

	private static boolean adaPemainDekat(ServerLevel level, BlockPos arena) {
		for (ServerPlayer p : level.players()) {
			if (dekat(p, arena) && !p.isSpectator()) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable ServerPlayer pemainDi(MinecraftServer server, Slot s, BlockPos arena) {
		if (!s.pemain() || s.uuid() == null) {
			return null;
		}
		ServerPlayer p = server.getPlayerList().getPlayer(s.uuid());
		return p != null && dekat(p, arena) && p.isAlive() ? p : null;
	}

	private static void hapusTubuh(Laga laga) {
		for (KultivatorNpc npc : new KultivatorNpc[]{laga.tubuhA, laga.tubuhB}) {
			if (npc != null && !npc.isRemoved()) {
				// a session still holding the body sees its side vanish and ends itself
				DuelistRegistry.override(npc, null);
				npc.discard();
			}
		}
		laga.tubuhA = null;
		laga.tubuhB = null;
		laga.sisiA = null;
		laga.sisiB = null;
	}

	// ================================================================== test commands

	/** {@code uji gabung turnamen [mulai|babak lewati]}. */
	static LiteralArgumentBuilder<CommandSourceStack> ujiNode() {
		return Commands.literal("turnamen")
				.executes(TurnamenMurid::ujiStatus)
				.then(Commands.literal("mulai").executes(TurnamenMurid::ujiMulai))
				.then(Commands.literal("babak").then(Commands.literal("lewati").executes(TurnamenMurid::ujiLewati)));
	}

	private static @Nullable String sekteUji(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer p)) {
			return null;
		}
		return GabungApi.anggota(p).sekteAktif();
	}

	private static int ujiStatus(CommandContext<CommandSourceStack> ctx) {
		String sekte = sekteUji(ctx);
		MinecraftServer server = ctx.getSource().getServer();
		if (sekte == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		long hari = GabungApi.hari(server);
		long next = hari;
		while (e != null && !hariTurnamen(e.index(), next) && next < hari + 61) {
			next++;
		}
		long nextDay = next;
		String juara = juaraTerakhir(server, sekte);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.turnamen.uji.status", Kediaman.nama(sekte),
				Component.translatable(berjalan(sekte) ? "kultivator.gabung.kediaman.ya" : "kultivator.gabung.kediaman.tidak"), nextDay,
				juara == null ? "-" : juara), false);
		Laga laga = LAGA.get(sekte);
		if (laga != null) {
			for (int i = 0; i < BRACKET; i++) {
				int idx = i;
				ctx.getSource().sendSuccess(() -> Component.literal(" " + (idx + 1) + ". ").append(namaSlot(laga.slot[idx])), false);
			}
		}
		return 1;
	}

	private static int ujiMulai(CommandContext<CommandSourceStack> ctx) {
		String sekte = sekteUji(ctx);
		if (sekte == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.uji.bukan_anggota"));
			return 0;
		}
		boolean ok = mulai(ctx.getSource().getServer(), sekte);
		ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.gabung.turnamen.uji.mulai" : "kultivator.gabung.turnamen.uji.gagal",
				Kediaman.nama(sekte)), true);
		return ok ? 1 : 0;
	}

	private static int ujiLewati(CommandContext<CommandSourceStack> ctx) {
		String sekte = sekteUji(ctx);
		Laga laga = sekte == null ? null : LAGA.get(sekte);
		if (laga == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.turnamen.uji.tidak_jalan"));
			return 0;
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		if (laga.bertarung) {
			int[] pair = pasangan(laga, laga.pertandingan);
			laga.hasil = acak(level, laga.slot[pair[0]], laga.slot[pair[1]], pair[0], pair[1]);
		} else {
			laga.sampai = level.getGameTime();
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.turnamen.uji.lewati"), true);
		return 1;
	}
}
