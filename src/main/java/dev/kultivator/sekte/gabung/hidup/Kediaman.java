package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.cultivation.CaveAbode;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cave dwellings of DESIGN 41.6.6 (contract C41.C1, owner 41C-K). A member rents one dwelling of the sect by rank: its
 * titik is the dwelling, it is the target of the Jimat Pulang ({@code CaveAbode.setUnchecked}), a 9-slot deposit chest
 * (data) and a meditation factor through the {@code SpiritSites} provider {@code gabung_kediaman} ({@link SitusGabung}).
 * Unpaid rent sends the player back to the dormitory ({@code CaveAbode.clear} when the seat was 41's); nothing else is
 * fined. NPC elders and seniors occupy part of the rest (data only, seeded per world, sect and titik).
 * <p>
 * Data: {@code Keanggotaan.kediaman} = {@code "<jenis>:<titik>"} ({@code "bilik"} for the dormitory, "" none),
 * {@code Keanggotaan.sewaSampai} = the world day the rent is paid to (inclusive); the free compound
 * {@code bagian("kediaman")} keeps the Jimat Pulang seat 41 wrote ({@code seat}, {@code dim}), the pending seat flag
 * ({@code tunda}: the seat is written when the player first stands in the dwelling) and the deposit chest
 * ({@code peti}, nine item tags). The chest belongs to the player, not to the cave: it moves along on a change of cave and
 * its items come back into the inventory (or at the feet) when the player is left without a cave.
 * <p>
 * Everything is lazy (41.12.3): the daily upkeep runs through {@link Keanggotaan#addPenyentuh} (login, compound, screen),
 * the seat watcher looks at online members only every {@link #SEAT_JEDA} ticks. No block ever changes.
 */
public final class Kediaman {

	/**
	 * The five kinds (41.6.6). {@code titik} is the anchor name or prefix ({@code kediaman_<n>} etc., titik of 41W),
	 * {@code pangkatMin} the lowest {@code api/Pangkat} height ({@link Pangkat#tingkat()}) allowed (Tetua Tamu: Puncak at rent
	 * x1.5), {@code kelasMin} the lowest sect class (41.4.5 "Kediaman terbaik": Gua Urat from Menengah, Puncak from Besar,
	 * Paviliun Awan from Agung), {@code sewa} PK per 7 days (times {@code kf}), {@code faktor} the meditation factor,
	 * {@code kursi} dwellings per compound (0 = no limit).
	 */
	public enum Jenis {
		BILIK("bilik", "kasur", 0, 1, 0, 1.05D, 0),
		GUA_KECIL("gua_kecil", "kediaman_", 3, 1, 60, 1.20D, 6),
		GUA_URAT("gua_urat", "kediaman_dalam_", 4, 2, 200, 1.40D, 4),
		PUNCAK("puncak", "kediaman_puncak_", 5, 3, 500, 1.70D, 2),
		PAVILIUN_AWAN("paviliun_awan", "sektelangit/murid", 4, 4, 800, 1.80D, 0);

		private final String id;
		private final String titik;
		private final int pangkatMin;
		private final int kelasMin;
		private final int sewa;
		private final double faktor;
		private final int kursi;

		Jenis(String id, String titik, int pangkatMin, int kelasMin, int sewa, double faktor, int kursi) {
			this.id = id;
			this.titik = titik;
			this.pangkatMin = pangkatMin;
			this.kelasMin = kelasMin;
			this.sewa = sewa;
			this.faktor = faktor;
			this.kursi = kursi;
		}

		public String id() {
			return id;
		}

		public String titik() {
			return titik;
		}

		public int pangkatMin() {
			return pangkatMin;
		}

		public int kelasMin() {
			return kelasMin;
		}

		/** Rent in PK per {@link #SEWA_HARI} days before the class factor {@code kf}. */
		public int sewa() {
			return sewa;
		}

		public double faktor() {
			return faktor;
		}

		public int kursi() {
			return kursi;
		}

		/** True for the caves with a deposit chest and a Jimat Pulang seat (everything above the dormitory). */
		public boolean gua() {
			return this != BILIK;
		}

		public String key() {
			return "kultivator.gabung.kediaman." + id;
		}

		public MutableComponent nama() {
			return Component.translatable(key());
		}

		/** The titik names of this kind in a compound ({@code kediaman_0..5} ...); the dormitory and the palace have one. */
		public List<String> semuaTitik() {
			List<String> out = new ArrayList<>(Math.max(1, kursi));
			if (kursi <= 0) {
				out.add(titik);
				return out;
			}
			for (int i = 0; i < kursi; i++) {
				out.add(titik + i);
			}
			return out;
		}

		public static @Nullable Jenis byId(@Nullable String id) {
			for (Jenis jenis : values()) {
				if (jenis.id.equals(id)) {
					return jenis;
				}
			}
			return null;
		}

		/** The kind a titik name belongs to (longest prefix first: {@code kediaman_dalam_2} is a vein cave). */
		public static @Nullable Jenis dariTitik(@Nullable String titik) {
			if (titik == null) {
				return null;
			}
			if (titik.equals(PAVILIUN_AWAN.titik)) {
				return PAVILIUN_AWAN;
			}
			if (titik.equals(BILIK.titik)) {
				return BILIK;
			}
			if (titik.startsWith(GUA_URAT.titik)) {
				return GUA_URAT;
			}
			if (titik.startsWith(PUNCAK.titik)) {
				return PUNCAK;
			}
			if (titik.startsWith(GUA_KECIL.titik)) {
				return GUA_KECIL;
			}
			return null;
		}
	}

	/** A rented dwelling: sect, kind, the titik name it stands at ({@code kediaman_3}) and the world day the rent is paid to. */
	public record Hunian(String sekte, Jenis jenis, String titik, long sewaSampai) {

		/** 1-based number shown to players ("Gua Kecil #3" is {@code kediaman_2}); 1 for the dormitory and the palace. */
		public int nomor() {
			return Kediaman.nomor(titik);
		}

		/** "Gua Kecil #3". */
		public MutableComponent nama() {
			return Kediaman.nama(jenis, titik);
		}
	}

	/**
	 * One row of the dwelling list (screen tab Pilih, 41.10.7): the titik, its kind and number, the PK rent for 7 days,
	 * {@code keadaan} in the {@code LembarKunci.K_*} vocabulary ({@code ok} free and allowed, {@code habis} taken,
	 * {@code terkunci} rank/class lock or missing building, {@code kurang_uang} too little PK), whether it is the player's
	 * own, who lives there and why it is locked.
	 */
	public record Pilihan(String titik, Jenis jenis, int nomor, int harga, String keadaan, boolean milikmu, @Nullable Component pemakai,
			@Nullable Component alasan) {
	}

	/** Rent period (days). */
	public static final int SEWA_HARI = 7;
	/** Rent may be paid at most this many days ahead (two periods). */
	public static final int SEWA_MAKS_HARI = 14;
	/** Deposit chest slots of a cave. */
	public static final int PETI_SLOT = 9;
	/** Spirit pool session: 30 real minutes for 20 PK, factor x1.30 (Inti+ one free session a day). */
	public static final int KOLAM_HARGA = 20;
	public static final double KOLAM_FAKTOR = 1.30D;
	/** The dwelling's factor and the seat act within this many blocks of its titik. */
	public static final double DEKAT = 5.0D;
	/** The Jimat Pulang seat is written once the player stands this close to the titik. */
	public static final double SEAT_JARAK = 3.0D;
	/** Paviliun Awan: horizontal reach around the palace column and the lowest height counted as "on the island". */
	public static final double AWAN_JARAK = 40.0D;
	public static final int AWAN_Y = LangitNpcPlan.Y_MIN - 16;
	/** The seat watcher runs this often (ticks). */
	public static final int SEAT_JEDA = 40;
	/** Tetua Tamu pay Puncak rent x1.5 (41.6.6 with 41.8.3). */
	public static final double TAMU_PUNCAK = 1.5D;

	/** Free compound of {@code Keanggotaan.bagian} used by 41C-K. */
	static final String BAGIAN = "kediaman";
	private static final String SEAT = "seat";
	private static final String SEAT_DIM = "dim";
	private static final String TUNDA = "tunda";
	private static final String TUNDA_TANYA = "tunda_tanya";
	private static final String PETI = "peti";
	private static final int WARNA = 0x8FE3C8;

	/** Between two spoken greetings in the same cave (ticks, 5 minutes); the action-bar line shows at every entry. */
	public static final int SAMBUT_JEDA = 6000;

	private static boolean initialised;
	/** Members now inside their own cave, and the game time of the last spoken greeting (runtime only). */
	private static final Set<UUID> DI_GUA = new HashSet<>();
	private static final Map<UUID, Long> SAMBUT = new HashMap<>();

	private Kediaman() {
	}

	static void reset() {
		DI_GUA.clear();
		SAMBUT.clear();
	}

	/**
	 * Once a second: a member who steps into the cave he rents is welcomed (D-41C). The action bar names the dwelling, its
	 * meditation factor and the day the rent runs to; the first entry in 5 minutes adds one line in the cave's own voice
	 * ({@code sambut.<kind>}), a chime and a drift of motes, and a hint to set the Jimat Pulang when it is not set yet.
	 */
	static void sambut(MinecraftServer server) {
		GabungData data = GabungData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Keanggotaan k = data.peek(player.getUUID());
			Hunian h = k == null || k.sekteAktif() == null ? null : hunian(k);
			boolean di = h != null && h.jenis() != Jenis.BILIK && player.isAlive() && diDalam(player, h);
			if (!di) {
				DI_GUA.remove(player.getUUID());
				continue;
			}
			if (!DI_GUA.add(player.getUUID())) {
				continue;
			}
			ServerLevel level = player.level();
			Msg.actionBar(player, Component.translatable("kultivator.gabung.kediaman.sambut", h.nama(), faktorTeks(h.jenis()), h.sewaSampai()).withColor(WARNA));
			long now = level.getGameTime();
			Long last = SAMBUT.get(player.getUUID());
			if (last != null && now - last < SAMBUT_JEDA) {
				continue;
			}
			SAMBUT.put(player.getUUID(), now);
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.sambut." + h.jenis().id()).withStyle(ChatFormatting.ITALIC).withColor(WARNA));
			if (!jimat(player)) {
				Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.sambut_jimat").withStyle(ChatFormatting.GRAY));
			}
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.9F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2D, player.getZ(), 24, 0.7D, 0.6D, 0.7D, 0.4D);
		}
	}

	// ================================================================== wiring

	/** Runtime wiring (from {@link SitusGabung#init()}): daily upkeep, the sect bus and the seat watcher. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Keanggotaan.addPenyentuh(Kediaman::sentuh);
		GabungBus.DITERIMA.register((player, sekte, pangkat) -> {
			Keanggotaan k = GabungApi.anggota(player);
			if (k.kediaman.isEmpty()) {
				k.kediaman = Jenis.BILIK.id();
				k.sewaSampai = 0L;
				GabungData.get(player.level().getServer()).setDirty();
			}
		});
		GabungBus.KELUAR.register((player, sekte, status) -> {
			// GabungApi.keluar already cleared k.kediaman (Keanggotaan.lepas); seat and chest live in our compound
			Keanggotaan k = GabungApi.anggota(player);
			lepasSeat(player, k);
			kembalikanPeti(player, k);
			k.bagian(BAGIAN).remove(TUNDA);
			GabungData.get(player.level().getServer()).setDirty();
		});
		GabungBus.PANGKAT_NAIK.register((player, sekte, lama, baru) -> {
			if (baru.tingkat() < lama.tingkat()) {
				periksaPangkat(player, GabungApi.anggota(player), true);
			}
		});
	}

	// ================================================================== reads (contract C41.C1)

	/** The dwelling {@code player} rents now, or null (no sect, no dwelling, dormitory only). */
	public static @Nullable Hunian milik(ServerPlayer player) {
		Hunian h = hunian(player);
		return h == null || h.jenis() == Jenis.BILIK ? null : h;
	}

	/** Like {@link #milik} but the dormitory counts too (every member has at least a dormitory cell). */
	public static @Nullable Hunian hunian(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		return k == null ? null : hunian(k);
	}

	/** The dwelling of record {@code k}, or null when not a member. */
	public static @Nullable Hunian hunian(Keanggotaan k) {
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return null;
		}
		if (k.kediaman.isEmpty() || k.kediaman.equals(Jenis.BILIK.id())) {
			return new Hunian(sekte, Jenis.BILIK, Jenis.BILIK.titik(), Long.MAX_VALUE);
		}
		int colon = k.kediaman.indexOf(':');
		Jenis jenis = colon > 0 ? Jenis.byId(k.kediaman.substring(0, colon)) : null;
		if (jenis == null) {
			return new Hunian(sekte, Jenis.BILIK, Jenis.BILIK.titik(), Long.MAX_VALUE);
		}
		return new Hunian(sekte, jenis, k.kediaman.substring(colon + 1), k.sewaSampai);
	}

	/** The rent of {@code jenis} for {@code player} in {@code sekte}: base x {@code kf} x {@code skalaBiaya} (Tamu Puncak x1.5). */
	public static int harga(ServerPlayer player, String sekte, Jenis jenis) {
		int base = GabungApi.biaya(sekte, jenis.sewa());
		if (jenis == Jenis.PUNCAK && GabungApi.anggota(player).pangkat == Pangkat.TAMU) {
			base = (int) Math.round(base * TAMU_PUNCAK);
		}
		return Math.max(0, base);
	}

	/** The PK to extend the current dwelling by one period (0 for the dormitory or a non-member). */
	public static int hargaPerpanjang(ServerPlayer player) {
		Hunian h = milik(player);
		return h == null ? 0 : harga(player, h.sekte(), h.jenis());
	}

	/** "Gua Kecil #3" for titik {@code kediaman_2}. */
	public static MutableComponent nama(Jenis jenis, String titik) {
		if (jenis.kursi() <= 0) {
			return jenis.nama();
		}
		return Component.translatable("kultivator.gabung.kediaman.nomor", jenis.nama(), nomor(titik));
	}

	/** 1-based number of a titik ({@code kediaman_dalam_3} -&gt; 4), 1 when the name carries none. */
	public static int nomor(String titik) {
		int i = titik.length();
		while (i > 0 && Character.isDigit(titik.charAt(i - 1))) {
			i--;
		}
		if (i == titik.length()) {
			return 1;
		}
		try {
			return Integer.parseInt(titik.substring(i)) + 1;
		} catch (NumberFormatException exception) {
			return 1;
		}
	}

	/**
	 * Where titik {@code titik} of {@code jenis} of {@code sekte} stands, or null while unknown: the caves only at their own
	 * compound anchor (never a stand-in: six caves must not collapse onto one spot), the dormitory at the sect's outer
	 * court ({@code kompleks/aula}, else the gate's tower room {@code sektenpc/bilik_0}), the cloud pavilion at the sky
	 * palace's column on the island height.
	 */
	public static @Nullable BlockPos posisi(MinecraftServer server, String sekte, Jenis jenis, String titik) {
		return switch (jenis) {
			case BILIK -> {
				BlockPos aula = KompleksApi.spot(sekte, TitikGabung.AULA);
				yield aula != null ? aula : GabungApi.titik(sekte, TitikGabung.GERBANG_BILIK + "0");
			}
			case PAVILIUN_AWAN -> {
				LangitNpcPlan.Istana istana = istana(server, sekte);
				yield istana == null ? null : istana.column().atY(LangitNpcPlan.Y_MIN);
			}
			default -> KompleksApi.spot(sekte, titik);
		};
	}

	/** The sky palace of {@code sekte}, or null (no palace, palaces off). */
	public static LangitNpcPlan.@Nullable Istana istana(MinecraftServer server, String sekte) {
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(server.overworld().getSeed())) {
			if (sekte.equals(LangitNpcPlan.sectId(istana))) {
				return istana;
			}
		}
		return null;
	}

	/**
	 * True when {@code player} stands in dwelling {@code h} right now (overworld; within {@link #DEKAT} blocks of the cave,
	 * on the palace island for the cloud pavilion; the dormitory counts anywhere in the sect's own compound).
	 */
	public static boolean diDalam(ServerPlayer player, Hunian h) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (h.jenis() == Jenis.PAVILIUN_AWAN) {
			LangitNpcPlan.Istana istana = istana(server, h.sekte());
			if (istana == null || player.getY() < AWAN_Y) {
				return false;
			}
			double dx = istana.column().getX() + 0.5D - player.getX();
			double dz = istana.column().getZ() + 0.5D - player.getZ();
			return dx * dx + dz * dz <= AWAN_JARAK * AWAN_JARAK;
		}
		if (h.jenis() == Jenis.BILIK) {
			var k = KompleksApi.kompleksDi(level, player.blockPosition());
			return k != null && h.sekte().equals(k.sekte());
		}
		BlockPos at = posisi(server, h.sekte(), h.jenis(), h.titik());
		return at != null && player.position().distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D) <= DEKAT * DEKAT;
	}

	// ================================================================== the list (tab Pilih)

	/** Every dwelling of the player's sect with its state for the player (empty for a non-member). */
	public static List<Pilihan> daftar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return List.of();
		}
		Map<String, Component> pemakai = penghuni(server, sekte, player.getUUID());
		Hunian kini = hunian(k);
		List<Pilihan> out = new ArrayList<>();
		for (Jenis jenis : Jenis.values()) {
			if (jenis == Jenis.BILIK) {
				out.add(new Pilihan(jenis.titik(), jenis, 1, 0, dev.kultivator.dunia.tempat.ui.LembarKunci.K_OK,
						kini != null && kini.jenis() == Jenis.BILIK, null, null));
				continue;
			}
			int harga = harga(player, sekte, jenis);
			Component kunci = kunci(server, player, k, sekte, jenis);
			for (String titik : jenis.semuaTitik()) {
				boolean milik = kini != null && kini.jenis() == jenis && kini.titik().equals(titik);
				Component siapa = milik ? null : pemakai.get(titik);
				String keadaan;
				Component alasan = null;
				if (milik) {
					keadaan = dev.kultivator.dunia.tempat.ui.LembarKunci.K_OK;
				} else if (kunci != null) {
					keadaan = dev.kultivator.dunia.tempat.ui.LembarKunci.K_TERKUNCI;
					alasan = kunci;
				} else if (siapa != null) {
					keadaan = dev.kultivator.dunia.tempat.ui.LembarKunci.K_HABIS;
				} else if (k.pk < harga) {
					keadaan = dev.kultivator.dunia.tempat.ui.LembarKunci.K_KURANG_UANG;
					alasan = Component.translatable("kultivator.gabung.kediaman.kurang_pk", harga, k.pk);
				} else {
					keadaan = dev.kultivator.dunia.tempat.ui.LembarKunci.K_OK;
				}
				out.add(new Pilihan(titik, jenis, nomor(titik), harga, keadaan, milik, siapa, alasan));
			}
		}
		return out;
	}

	/** Why {@code jenis} is closed to the player in {@code sekte}, or null when it may be rented. */
	private static @Nullable Component kunci(MinecraftServer server, ServerPlayer player, Keanggotaan k, String sekte, Jenis jenis) {
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		if (kelas.nomor() < jenis.kelasMin()) {
			return Component.translatable("kultivator.gabung.kediaman.kunci.kelas", jenis.nama(), KelasSekte.byNomor(jenis.kelasMin()).nama());
		}
		if (k.pangkat.tingkat() < jenis.pangkatMin()) {
			return Component.translatable("kultivator.gabung.kediaman.kunci.pangkat", Pangkat.byOrdinal(jenis.pangkatMin()).nama());
		}
		switch (jenis) {
			case PAVILIUN_AWAN -> {
				if (istana(server, sekte) == null) {
					return Component.translatable("kultivator.gabung.kediaman.kunci.tanpa_istana");
				}
			}
			case GUA_KECIL -> {
				if (KompleksApi.spot(sekte, TitikGabung.AULA) == null) {
					return Component.translatable("kultivator.gabung.kediaman.kunci.tanpa_kompleks");
				}
			}
			case GUA_URAT -> {
				if (KompleksApi.spot(sekte, TitikGabung.DALAM) == null) {
					return Component.translatable("kultivator.gabung.kediaman.kunci.tanpa_distrik", Component.translatable("kultivator.gabung.kediaman.distrik.dalam"));
				}
			}
			case PUNCAK -> {
				if (KompleksApi.spot(sekte, TitikGabung.PUNCAK) == null) {
					return Component.translatable("kultivator.gabung.kediaman.kunci.tanpa_distrik", Component.translatable("kultivator.gabung.kediaman.distrik.puncak"));
				}
			}
			default -> {
			}
		}
		return null;
	}

	/**
	 * Who lives where in {@code sekte} (titik -&gt; name): member players first, then the NPC elders and seniors that hold the
	 * rest as data (41.6.6 "NPC tokoh menempati sisa"). The NPC share is seeded by world, sect and titik so it stays stable;
	 * the first titik of every kind is never an NPC's, so a free cave always exists for players. {@code kecuali} is left out
	 * of the player list (the viewer's own dwelling is marked separately).
	 */
	public static Map<String, Component> penghuni(MinecraftServer server, String sekte, @Nullable UUID kecuali) {
		Map<String, Component> out = new LinkedHashMap<>();
		GabungData data = GabungData.get(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			if (uuid.equals(kecuali)) {
				continue;
			}
			Keanggotaan other = data.peek(uuid);
			Hunian h = other == null ? null : hunian(other);
			if (h == null || h.jenis() == Jenis.BILIK || h.jenis() == Jenis.PAVILIUN_AWAN) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(uuid);
			out.put(h.titik(), online != null ? online.getDisplayName() : Component.translatable("kultivator.gabung.kediaman.anggota_lain"));
		}
		long seed = server.overworld().getSeed();
		List<TurnamenMurid.Peserta> roster = TurnamenMurid.roster(server, sekte);
		List<TokohSekte.Tokoh> tetua = TokohSekte.tetua(server, sekte);
		for (Jenis jenis : new Jenis[]{Jenis.GUA_KECIL, Jenis.GUA_URAT, Jenis.PUNCAK}) {
			int share = switch (jenis) {
				case GUA_KECIL -> 35;
				case GUA_URAT -> 40;
				default -> 50;
			};
			List<String> titikList = jenis.semuaTitik();
			for (int i = 1; i < titikList.size(); i++) {
				String titik = titikList.get(i);
				if (out.containsKey(titik)) {
					continue;
				}
				long h = TurnamenMurid.campur(seed, sekte.hashCode() * 31L + titik.hashCode());
				if (Math.floorMod(h, 100L) >= share) {
					continue;
				}
				Component nama = namaNpc(jenis, h, roster, tetua);
				out.put(titik, nama);
			}
		}
		return out;
	}

	private static Component namaNpc(Jenis jenis, long h, List<TurnamenMurid.Peserta> roster, List<TokohSekte.Tokoh> tetua) {
		if (jenis == Jenis.PUNCAK && !tetua.isEmpty()) {
			TokohSekte.Tokoh t = tetua.get((int) Math.floorMod(h >>> 8, (long) tetua.size()));
			return Component.literal(t.nama());
		}
		int minPeran = jenis == Jenis.GUA_KECIL ? TurnamenMurid.PERAN_DALAM : TurnamenMurid.PERAN_INTI;
		List<TurnamenMurid.Peserta> pool = new ArrayList<>();
		for (TurnamenMurid.Peserta p : roster) {
			if (p.peran() >= minPeran) {
				pool.add(p);
			}
		}
		if (pool.isEmpty()) {
			return Component.translatable(jenis == Jenis.PUNCAK ? "kultivator.gabung.kediaman.npc_tetua" : "kultivator.gabung.kediaman.npc_murid");
		}
		return pool.get((int) Math.floorMod(h >>> 8, (long) pool.size())).nama();
	}

	// ================================================================== actions (screen ops of 41.10.7, commands)

	/**
	 * Rents dwelling {@code titik} for one period ({@code sewa:TITIK}). Moving between two titik of the same kind keeps the
	 * paid days and costs nothing ({@code pindah:TITIK}); a new kind costs a full period and the old dwelling is given up
	 * without refund. Null when done, else the reason.
	 */
	public static @Nullable Component sewa(ServerPlayer player, String titik) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.kediaman.bukan_anggota");
		}
		Jenis jenis = Jenis.dariTitik(titik);
		if (jenis == null || !jenis.semuaTitik().contains(titik)) {
			return Component.translatable("kultivator.gabung.kediaman.tidak_ada", titik);
		}
		Hunian kini = hunian(k);
		if (jenis == Jenis.BILIK) {
			if (kini != null && kini.jenis() == Jenis.BILIK) {
				return Component.translatable("kultivator.gabung.kediaman.sudah");
			}
			return lepas(player);
		}
		if (kini != null && kini.jenis() == jenis && kini.titik().equals(titik)) {
			return Component.translatable("kultivator.gabung.kediaman.sudah");
		}
		Component kunci = kunci(server, player, k, sekte, jenis);
		if (kunci != null) {
			return kunci;
		}
		if (jenis.kursi() > 0 && penghuni(server, sekte, player.getUUID()).containsKey(titik)) {
			return Component.translatable("kultivator.gabung.kediaman.dipakai", nama(jenis, titik));
		}
		long hari = GabungApi.hari(server);
		boolean pindah = kini != null && kini.jenis() == jenis && kini.sewaSampai() >= hari;
		if (!pindah) {
			int harga = harga(player, sekte, jenis);
			if (!GabungApi.bayarPk(player, harga, "gabung.sewa_kediaman")) {
				return Component.translatable("kultivator.gabung.kediaman.kurang_pk", harga, k.pk);
			}
			k.sewaSampai = hari + SEWA_HARI - 1L;
		}
		lepasSeat(player, k);
		k.kediaman = jenis.id() + ":" + titik;
		CompoundTag bagian = k.bagian(BAGIAN);
		bagian.putBoolean(TUNDA, true);
		bagian.remove(TUNDA_TANYA);
		k.catat(hari, "kultivator.gabung.riwayat.kediaman", jenis.id(), Integer.toString(nomor(titik)));
		data.setDirty();
		Advancements.grant(player, "sekte/gabung_kediaman");
		Msg.chat(player, Component.translatable(pindah ? "kultivator.gabung.kediaman.pindah_ok" : "kultivator.gabung.kediaman.sewa_ok",
				nama(jenis, titik), k.sewaSampai, faktorTeks(jenis)).withColor(WARNA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		cobaSeat(player, k);
		return null;
	}

	/** Same as {@link #sewa}; the screen's {@code pindah:TITIK} (same kind: free, keeps the paid days). */
	public static @Nullable Component pindah(ServerPlayer player, String titik) {
		return sewa(player, titik);
	}

	/** Pays one more period of the current dwelling ({@code perpanjang}); at most {@link #SEWA_MAKS_HARI} days ahead. */
	public static @Nullable Component perpanjang(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Hunian h = milik(player);
		if (h == null) {
			return Component.translatable("kultivator.gabung.kediaman.tanpa_gua");
		}
		Keanggotaan k = GabungApi.anggota(player);
		long hari = GabungApi.hari(server);
		long dasar = Math.max(hari - 1L, k.sewaSampai);
		if (dasar + SEWA_HARI > hari + SEWA_MAKS_HARI) {
			return Component.translatable("kultivator.gabung.kediaman.sudah_lunas", k.sewaSampai);
		}
		int harga = harga(player, h.sekte(), h.jenis());
		if (!GabungApi.bayarPk(player, harga, "gabung.sewa_kediaman")) {
			return Component.translatable("kultivator.gabung.kediaman.kurang_pk", harga, k.pk);
		}
		k.sewaSampai = dasar + SEWA_HARI;
		GabungData.get(server).setDirty();
		Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.perpanjang_ok", h.nama(), k.sewaSampai).withColor(WARNA));
		return null;
	}

	/** Gives the dwelling up and returns to the dormitory ({@code lepas}); the chest's items come back to the player. */
	public static @Nullable Component lepas(ServerPlayer player) {
		Hunian h = milik(player);
		if (h == null) {
			return Component.translatable("kultivator.gabung.kediaman.tanpa_gua");
		}
		Keanggotaan k = GabungApi.anggota(player);
		keBilik(player, k, "kultivator.gabung.kediaman.lepas_ok", h);
		return null;
	}

	/**
	 * Makes the dwelling's seat the Jimat Pulang target although the player already had an abode of his own (the click of
	 * the hint, {@code /kultivasi gabung kediaman jimat}). Needs the player to stand in the dwelling.
	 */
	public static @Nullable Component jadikanJimat(ServerPlayer player) {
		Hunian h = milik(player);
		if (h == null) {
			return Component.translatable("kultivator.gabung.kediaman.tanpa_gua");
		}
		if (!diDalam(player, h)) {
			return Component.translatable("kultivator.gabung.kediaman.harus_di_dalam", h.nama());
		}
		tulisSeat(player, GabungApi.anggota(player));
		return null;
	}

	/** True when the Jimat Pulang points at the seat 41 wrote for the current dwelling. */
	public static boolean jimat(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		GlobalPos seat = seatKu(k);
		return seat != null && seat.equals(CaveAbode.of(player));
	}

	/** The factor shown for {@code jenis} ("x1.20"). */
	public static Component faktorTeks(Jenis jenis) {
		return Component.literal("×" + String.format(java.util.Locale.ROOT, "%.2f", jenis.faktor()));
	}

	// ================================================================== deposit chest

	/** The nine chest slots (copies; empty stacks for free slots). */
	public static List<ItemStack> peti(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		return bacaPeti(player, k);
	}

	/** Puts the main-hand stack into the chest ({@code peti_taruh}); merges with equal stacks first. */
	public static @Nullable Component petiTaruh(ServerPlayer player) {
		Component why = bolehPeti(player);
		if (why != null) {
			return why;
		}
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (hand.isEmpty()) {
			return Component.translatable("kultivator.gabung.kediaman.peti.tangan_kosong");
		}
		if (hand.has(dev.kultivator.sekte.gabung.masuk.GabungItems.ANGGOTA)) {
			return Component.translatable("kultivator.gabung.kediaman.peti.lencana");
		}
		Keanggotaan k = GabungApi.anggota(player);
		List<ItemStack> isi = bacaPeti(player, k);
		for (ItemStack slot : isi) {
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, hand) && slot.getCount() < slot.getMaxStackSize()) {
				int move = Math.min(hand.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(move);
				hand.shrink(move);
				if (hand.isEmpty()) {
					break;
				}
			}
		}
		if (!hand.isEmpty()) {
			for (int i = 0; i < isi.size(); i++) {
				if (isi.get(i).isEmpty()) {
					isi.set(i, hand.copyAndClear());
					break;
				}
			}
		}
		if (!hand.isEmpty()) {
			tulisPeti(player, k, isi);
			return Component.translatable("kultivator.gabung.kediaman.peti.penuh");
		}
		tulisPeti(player, k, isi);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BARREL_CLOSE, SoundSource.PLAYERS, 0.6F, 1.1F);
		return null;
	}

	/** Takes slot {@code slot} back into the inventory ({@code peti_ambil:SLOT}); what does not fit drops at the feet. */
	public static @Nullable Component petiAmbil(ServerPlayer player, int slot) {
		Component why = bolehPeti(player);
		if (why != null) {
			return why;
		}
		Keanggotaan k = GabungApi.anggota(player);
		List<ItemStack> isi = bacaPeti(player, k);
		if (slot < 0 || slot >= isi.size() || isi.get(slot).isEmpty()) {
			return Component.translatable("kultivator.gabung.kediaman.peti.slot_kosong");
		}
		ItemStack take = isi.get(slot);
		isi.set(slot, ItemStack.EMPTY);
		tulisPeti(player, k, isi);
		beri(player, take);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.6F, 1.0F);
		return null;
	}

	/** Why the chest cannot be used now (no cave, or away from both the cave and the sect's compound), or null. */
	private static @Nullable Component bolehPeti(ServerPlayer player) {
		Hunian h = milik(player);
		if (h == null) {
			return Component.translatable("kultivator.gabung.kediaman.tanpa_gua");
		}
		if (diDalam(player, h)) {
			return null;
		}
		var kompleks = KompleksApi.kompleksDi(player.level(), player.blockPosition());
		if (kompleks != null && h.sekte().equals(kompleks.sekte())) {
			return null;
		}
		return Component.translatable("kultivator.gabung.kediaman.peti.jauh", h.nama());
	}

	private static List<ItemStack> bacaPeti(ServerPlayer player, Keanggotaan k) {
		List<ItemStack> out = new ArrayList<>(PETI_SLOT);
		ListTag list = k.bagian(BAGIAN).getListOrEmpty(PETI);
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		for (int i = 0; i < PETI_SLOT; i++) {
			ItemStack stack = ItemStack.EMPTY;
			if (i < list.size()) {
				Tag tag = list.get(i);
				stack = ItemStack.OPTIONAL_CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
			}
			out.add(stack);
		}
		return out;
	}

	private static void tulisPeti(ServerPlayer player, Keanggotaan k, List<ItemStack> isi) {
		RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ListTag list = new ListTag();
		for (int i = 0; i < PETI_SLOT; i++) {
			ItemStack stack = i < isi.size() ? isi.get(i) : ItemStack.EMPTY;
			list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElseGet(CompoundTag::new));
		}
		k.bagian(BAGIAN).put(PETI, list);
		GabungData.get(player.level().getServer()).setDirty();
	}

	/** Returns every chest item to the player (inventory, then the feet) and empties the chest. */
	private static void kembalikanPeti(ServerPlayer player, Keanggotaan k) {
		if (!k.bagian(BAGIAN).contains(PETI)) {
			return;
		}
		List<ItemStack> isi = bacaPeti(player, k);
		k.bagian(BAGIAN).remove(PETI);
		int n = 0;
		for (ItemStack stack : isi) {
			if (!stack.isEmpty()) {
				beri(player, stack);
				n++;
			}
		}
		if (n > 0) {
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.peti.kembali", n).withColor(WARNA));
		}
	}

	private static void beri(ServerPlayer player, ItemStack stack) {
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy) || !copy.isEmpty()) {
			if (!copy.isEmpty()) {
				player.spawnAtLocation(player.level(), copy);
			}
		}
	}

	// ================================================================== upkeep and the Jimat Pulang seat

	/** The daily upkeep (a {@link Keanggotaan.Penyentuh}): dormitory for the homeless, unpaid rent, a rank now too low. */
	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (k.sekteAktif() == null) {
			return;
		}
		if (k.kediaman.isEmpty()) {
			k.kediaman = Jenis.BILIK.id();
			k.sewaSampai = 0L;
			return;
		}
		Hunian h = hunian(k);
		if (h == null || h.jenis() == Jenis.BILIK) {
			return;
		}
		if (hari > k.sewaSampai) {
			keBilik(player, k, "kultivator.gabung.kediaman.jatuh_tempo", h);
			return;
		}
		if (k.sewaSampai - hari <= 1L) {
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.ingat_sewa", h.nama(), k.sewaSampai,
					hargaPerpanjang(player)).withStyle(ChatFormatting.GOLD));
		}
		periksaPangkat(player, k, false);
	}

	/** Moves the player out when his rank or the sect's class no longer allows the dwelling (demotion, a class lost). */
	private static void periksaPangkat(ServerPlayer player, Keanggotaan k, boolean segera) {
		Hunian h = hunian(k);
		if (h == null || h.jenis() == Jenis.BILIK) {
			return;
		}
		boolean pangkatKurang = k.pangkat.tingkat() < h.jenis().pangkatMin();
		boolean kelasKurang = GabungApi.kelas(h.sekte()) < h.jenis().kelasMin();
		if (pangkatKurang || kelasKurang) {
			keBilik(player, k, "kultivator.gabung.kediaman.turun", h);
		}
	}

	/** Back to the dormitory: clears 41's seat, returns the chest's items, tells why ({@code kunci} with the dwelling's name). */
	private static void keBilik(ServerPlayer player, Keanggotaan k, String kunci, Hunian lama) {
		lepasSeat(player, k);
		kembalikanPeti(player, k);
		k.kediaman = Jenis.BILIK.id();
		k.sewaSampai = 0L;
		CompoundTag bagian = k.bagian(BAGIAN);
		bagian.remove(TUNDA);
		bagian.remove(TUNDA_TANYA);
		k.catat(GabungApi.hari(player.level().getServer()), "kultivator.gabung.riwayat.kediaman_lepas", lama.jenis().id());
		GabungData.get(player.level().getServer()).setDirty();
		Msg.chat(player, Component.translatable(kunci, lama.nama()).withColor(0xC9B37A));
	}

	/** The seat 41 wrote for the player, or null. */
	private static @Nullable GlobalPos seatKu(Keanggotaan k) {
		CompoundTag bagian = k.bagian(BAGIAN);
		if (!bagian.contains(SEAT)) {
			return null;
		}
		net.minecraft.resources.Identifier dim = net.minecraft.resources.Identifier.tryParse(bagian.getStringOr(SEAT_DIM, "minecraft:overworld"));
		if (dim == null) {
			return null;
		}
		return GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim),
				BlockPos.of(bagian.getLongOr(SEAT, 0L)));
	}

	/** Clears the Jimat Pulang seat when it is still the one 41 wrote (an abode the player made himself is kept). */
	private static void lepasSeat(ServerPlayer player, Keanggotaan k) {
		GlobalPos ours = seatKu(k);
		CompoundTag bagian = k.bagian(BAGIAN);
		bagian.remove(SEAT);
		bagian.remove(SEAT_DIM);
		if (ours != null && ours.equals(CaveAbode.of(player))) {
			CaveAbode.clear(player);
		}
	}

	/** Writes the seat under the player's feet and remembers it as 41's. */
	private static void tulisSeat(ServerPlayer player, Keanggotaan k) {
		CaveAbode.setUnchecked(player);
		GlobalPos now = CaveAbode.of(player);
		CompoundTag bagian = k.bagian(BAGIAN);
		if (now != null) {
			bagian.putLong(SEAT, now.pos().asLong());
			bagian.putString(SEAT_DIM, now.dimension().identifier().toString());
		}
		bagian.remove(TUNDA);
		bagian.remove(TUNDA_TANYA);
		GabungData.get(player.level().getServer()).setDirty();
	}

	/**
	 * The pending seat: when the player stands in his new cave, the seat is written there; a player with an abode of his
	 * own is asked once (clickable line) instead, so 41 never overwrites a home he built himself.
	 */
	static void cobaSeat(ServerPlayer player, Keanggotaan k) {
		CompoundTag bagian = k.bagian(BAGIAN);
		if (!bagian.getBooleanOr(TUNDA, false)) {
			return;
		}
		Hunian h = milik(player);
		if (h == null) {
			bagian.remove(TUNDA);
			return;
		}
		if (!diDalam(player, h)) {
			return;
		}
		if (h.jenis() != Jenis.PAVILIUN_AWAN) {
			BlockPos at = posisi(player.level().getServer(), h.sekte(), h.jenis(), h.titik());
			if (at == null || player.position().distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D) > SEAT_JARAK * SEAT_JARAK) {
				return;
			}
		}
		GlobalPos own = CaveAbode.of(player);
		if (own == null || own.equals(seatKu(k))) {
			tulisSeat(player, k);
			return;
		}
		if (!bagian.getBooleanOr(TUNDA_TANYA, false)) {
			bagian.putBoolean(TUNDA_TANYA, true);
			GabungData.get(player.level().getServer()).setDirty();
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.jimat_tanya", h.nama())
					.append(Component.literal(" "))
					.append(Component.translatable("kultivator.gabung.kediaman.jimat_klik").withStyle(style -> style.withColor(ChatFormatting.AQUA)
							.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung kediaman jimat"))))
					.withColor(WARNA));
		}
	}

	/** From {@link SitusGabung}'s tick: online members with a pending seat are checked every {@link #SEAT_JEDA} ticks. */
	static void tick(MinecraftServer server) {
		GabungData data = GabungData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Keanggotaan k = data.peek(player.getUUID());
			if (k != null && k.sekteAktif() != null && k.bagian(BAGIAN).getBooleanOr(TUNDA, false)) {
				cobaSeat(player, k);
			}
		}
	}

	// ================================================================== chat summary (command without the 41U screen)

	/** The dwelling summary in chat: current dwelling, rent, factor, Jimat, chest fill, and the free caves of each kind. */
	static void ringkas(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.bukan_anggota").withStyle(ChatFormatting.RED));
			return;
		}
		Hunian h = hunian(k);
		Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.ringkas.judul", nama(sekte)).withStyle(ChatFormatting.GOLD));
		if (h == null || h.jenis() == Jenis.BILIK) {
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.ringkas.bilik", faktorTeks(Jenis.BILIK)).withColor(WARNA));
		} else {
			int filled = 0;
			for (ItemStack stack : peti(player)) {
				if (!stack.isEmpty()) {
					filled++;
				}
			}
			Msg.chat(player, Component.translatable("kultivator.gabung.kediaman.ringkas.gua", h.nama(), h.sewaSampai(), faktorTeks(h.jenis()),
					Component.translatable(jimat(player) ? "kultivator.gabung.kediaman.ya" : "kultivator.gabung.kediaman.tidak"), filled, PETI_SLOT)
					.withColor(WARNA));
		}
		for (Pilihan p : daftar(player)) {
			if (p.jenis() == Jenis.BILIK || p.milikmu()) {
				continue;
			}
			MutableComponent line = Component.translatable("kultivator.gabung.kediaman.ringkas.baris", nama(p.jenis(), p.titik()), p.harga());
			if (p.pemakai() != null) {
				line.append(Component.translatable("kultivator.gabung.kediaman.ringkas.dipakai", p.pemakai()).withStyle(ChatFormatting.GRAY));
				Msg.chat(player, line.withStyle(ChatFormatting.DARK_GRAY));
			} else if (p.alasan() != null) {
				line.append(Component.literal(" · ")).append(p.alasan());
				Msg.chat(player, line.withStyle(ChatFormatting.DARK_GRAY));
			} else {
				Msg.chat(player, line.append(Component.literal(" ")).append(Component.translatable("kultivator.gabung.kediaman.ringkas.sewa_klik")
						.withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
								.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung kediaman sewa " + p.titik())))).withColor(0xD8E6D0));
			}
		}
	}

	static Component nama(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte) : name;
	}

	/** Test: the rent falls due now ({@code uji gabung sewa lewat}); the next upkeep moves the player out at once. */
	static boolean sewaLewat(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		Hunian h = hunian(k);
		if (h == null || h.jenis() == Jenis.BILIK) {
			return false;
		}
		k.sewaSampai = GabungApi.hari(player.level().getServer()) - 1L;
		k.hariSentuh = -1L;
		if (k.sentuh(player, GabungApi.hari(player.level().getServer()))) {
			GabungData.get(player.level().getServer()).setDirty();
		}
		return true;
	}
}
