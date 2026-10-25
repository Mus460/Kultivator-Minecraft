package dev.kultivator.sekte.gabung.politik;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Aturan;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Panggilan Perang (DESIGN 41.6.10, module 41D-P): the War Call of an NPC sect at war, the war's score and its end.
 * <p>
 * The war itself belongs to 41D ({@code DiplomasiGabung.perangMulai/perangSkor/perangAkhir}, the record
 * {@code GabungData.sekte.<s>.perang} with {@code lawan, skor, mulai, sampai}, the daily data skirmish, the end after
 * {@link #DURASI_HARI} days or at +-{@link #SKOR_MENANG}). This class listens to {@link GabungBus#PERANG_MULAI} and keeps the
 * members' side of each war in {@code DataSekteGabung.bagian("perang").aktif} ({@code lawan, mulai, ikut, panggil, lalai,
 * tawanan, skorKemarin, misi}). Every player member gets the call: a gold chat card with {@code [Lihat]}, a title and the
 * temple bell, through the one busy door ({@code Sibuk.why}, retried every {@code Sibuk.RETRY_TICKS}); offline members get
 * it at their next login. On {@link GabungBus#PERANG_AKHIR} every member who took part in at least
 * {@link #HADIAH_MISI_MIN} war missions of a won war gets a technique jade of grade class - 1 in the sect's Dao (else
 * {@link #HADIAH_PK} PK), and the side record closes. Mission scores go through {@link #skor} into 41D's record.
 * <p>
 * Inner, Core and higher ranks ({@code Pangkat.DALAM}+, not the guest path) must finish at least one war mission within
 * {@link #WAJIB_HARI} days of receiving the call; neglect records rule A4 ({@code GabungApi.langgar}, +10). Outer and lower
 * members may join freely. A day on which the score fell (41D's data skirmish, a lost mission) may cost a captive disciple
 * (half the time, at most {@link #TAWANAN_MAKS}), freed by the mission Jemput Tawanan ({@link MisiPerang}).
 * <p>
 * {@link #init()} is the one entry of 41D-P: it also wires {@link MisiPerang}, {@link SerbuanKompleks} and
 * {@link PemburuSekte}. Server thread only; nothing here changes a block, the weather or the time, and nobody dies.
 */
public final class PanggilanPerang {

	/** World days a war lasts at most (41.6.10). */
	public static final int DURASI_HARI = 10;
	/** The score that ends a war at once. */
	public static final int SKOR_MENANG = 100;
	/** Days an obliged member has to finish one war mission after the call. */
	public static final int WAJIB_HARI = 3;
	/** War missions a member must have finished to share the victory reward. */
	public static final int HADIAH_MISI_MIN = 2;
	/** The reward in PK when no fitting technique jade exists (or the member was away). */
	public static final int HADIAH_PK = 300;
	/** Captive disciples the enemy may hold. */
	public static final int TAWANAN_MAKS = 2;
	/** Entries kept in the war log ({@code perang.misi}). */
	public static final int LOG_MAKS = 20;

	/** The free compound of 41D-P in {@code Keanggotaan.bagian} and {@code DataSekteGabung.bagian}. */
	static final String BAGIAN = "perang";

	static final String LAWAN = "lawan";
	static final String MULAI = "mulai";
	static final String IKUT = "ikut";
	static final String PANGGIL = "panggil";
	static final String LALAI = "lalai";
	static final String TAWANAN = "tawanan";
	static final String SKOR_KEMARIN = "skorKemarin";
	/** The latest score this side saw (41D's record is closed before the end is heard). */
	static final String SKOR_AKHIR = "skorAkhir";
	static final String LOG = "misi";
	/** The side record in {@code DataSekteGabung.bagian(BAGIAN)}. */
	static final String AKTIF = "aktif";
	/** Player bagian keys. */
	static final String DIBERI = "diberi";
	static final String HADIAH = "hadiahPk";

	/** player -> game tick of the next try to hand over a pending War Call. */
	private static final Map<UUID, Long> ANTRE = new ConcurrentHashMap<>();
	private static long hariTerakhir = Long.MIN_VALUE;
	private static boolean initialised;

	/** The entry of 41D-P, called once from {@code Politik.init()} while 41 runs. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GabungBus.PERANG_MULAI.register(PanggilanPerang::mulai);
		GabungBus.PERANG_AKHIR.register(PanggilanPerang::selesai);
		GabungBus.DITERIMA.register((player, sekte, pangkat) -> {
			MinecraftServer server = player.level().getServer();
			if (server != null && aktif(server, sekte)) {
				antre(player, 40);
			}
		});
		Keanggotaan.addPenyentuh(PanggilanPerang::sentuh);
		ServerTickEvents.END_SERVER_TICK.register(PanggilanPerang::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ANTRE.clear();
			hariTerakhir = Long.MIN_VALUE;
		});
		GabungCommands.daftar("perang", PanggilanPerang::nodePemain);
		GabungApi.daftarUji("perang", PanggilanPerang::nodeUji);
		dev.kultivator.sekte.gabung.GabungUji.chips("gabung perang misi <jenis>", "gabung perang tawanan <n>");
		MisiPerang.init();
		SerbuanKompleks.init();
		PemburuSekte.init();
	}

	// ================================================================== reads (41A Pamit, 41U screens, 41D)

	/** True while {@code sekte} fights a war that 41 tracks. */
	public static boolean aktif(MinecraftServer server, @Nullable String sekte) {
		return perang(server, sekte) != null;
	}

	/** The opponent of {@code sekte}'s running war, or null. */
	public static @Nullable String lawan(MinecraftServer server, @Nullable String sekte) {
		DiplomasiGabung.Perang p = catatan(server, sekte);
		return p == null ? null : p.lawan();
	}

	/** The running war's score from {@code sekte}'s side (0 without war). */
	public static int nilaiSkor(MinecraftServer server, @Nullable String sekte) {
		DiplomasiGabung.Perang p = catatan(server, sekte);
		return p == null ? 0 : p.skor();
	}

	/** World days left of {@code sekte}'s war (0 without war). */
	public static long sisaHari(MinecraftServer server, @Nullable String sekte) {
		DiplomasiGabung.Perang p = catatan(server, sekte);
		return p == null ? 0L : Math.max(0L, p.sampai() - GabungApi.hari(server));
	}

	/** 41D's war record of {@code sekte}, or null. */
	static DiplomasiGabung.@Nullable Perang catatan(@Nullable MinecraftServer server, @Nullable String sekte) {
		return server == null || sekte == null || sekte.isEmpty() ? null : DiplomasiGabung.perang(server, sekte);
	}

	/** Captive disciples the enemy holds (Jemput Tawanan). */
	public static int tawanan(MinecraftServer server, @Nullable String sekte) {
		CompoundTag p = perang(server, sekte);
		return p == null ? 0 : p.getIntOr(TAWANAN, 0);
	}

	/**
	 * True while the War Call binds {@code player}: a member of rank Dalam or higher (not the guest path) whose sect is at war
	 * and who has not finished a war mission of it yet (41.7.1: Pamit is refused then).
	 */
	public static boolean wajib(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = server == null ? null : GabungData.get(server).peek(player.getUUID());
		if (k == null || k.sekteAktif() == null || !terikat(k.pangkat)) {
			return false;
		}
		CompoundTag p = perang(server, k.sekte);
		return p != null && p.getCompoundOrEmpty(IKUT).getIntOr(player.getStringUUID(), 0) <= 0;
	}

	/** War missions {@code player} finished in {@code sekte}'s running war. */
	public static int misiDiikuti(MinecraftServer server, String sekte, UUID player) {
		CompoundTag p = perang(server, sekte);
		return p == null ? 0 : p.getCompoundOrEmpty(IKUT).getIntOr(player.toString(), 0);
	}

	/** True for ranks the call obliges (Dalam and higher; guest elders are free). */
	static boolean terikat(Pangkat pangkat) {
		return pangkat != Pangkat.TAMU && pangkat.ordinal() >= Pangkat.DALAM.ordinal();
	}

	/**
	 * The members' side record of {@code sekte}'s running war (live tag), opened on first use for the war 41D records; null
	 * without war.
	 */
	static @Nullable CompoundTag perang(@Nullable MinecraftServer server, @Nullable String sekte) {
		DiplomasiGabung.Perang war = catatan(server, sekte);
		if (war == null) {
			return null;
		}
		GabungData data = GabungData.get(server);
		CompoundTag bagian = data.sekte(sekte).bagian(BAGIAN);
		CompoundTag p = bagian.getCompoundOrEmpty(AKTIF);
		if (!war.lawan().equals(p.getStringOr(LAWAN, "")) || p.getLongOr(MULAI, Long.MIN_VALUE) != war.mulai()) {
			p = baru(server, sekte, war);
			bagian.put(AKTIF, p);
			data.setDirty();
		}
		return p;
	}

	private static CompoundTag baru(MinecraftServer server, String sekte, DiplomasiGabung.Perang war) {
		CompoundTag p = new CompoundTag();
		p.putString(LAWAN, war.lawan());
		p.putLong(MULAI, war.mulai());
		p.putInt(SKOR_KEMARIN, war.skor());
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ war.mulai() * 31L ^ sekte.hashCode() * 17L);
		p.putInt(TAWANAN, random.nextInt(2));
		p.put(IKUT, new CompoundTag());
		p.put(PANGGIL, new CompoundTag());
		p.put(LALAI, new CompoundTag());
		p.put(LOG, new ListTag());
		return p;
	}

	/** The sect's shown name ("Sekte Pedang Awan Ungu"), or its id. */
	static Component nama(@Nullable String sekte) {
		Component name = sekte == null || sekte.isEmpty() ? null : SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte == null ? "?" : sekte) : name;
	}

	// ================================================================== opening a war

	/**
	 * Declares a war of {@code sekte} against {@code lawan} through 41D ({@code DiplomasiGabung.perangMulai}, which fires
	 * {@link GabungBus#PERANG_MULAI}). Null when done, else why not.
	 */
	public static @Nullable Component nyatakan(MinecraftServer server, String sekte, String lawan) {
		if (sekte == null || lawan == null || sekte.equals(lawan) || GabungApi.tier(sekte) <= 0 || GabungApi.tier(lawan) <= 0) {
			return Component.translatable("kultivator.gabung.perang.alasan.sekte");
		}
		if (aktif(server, sekte) || aktif(server, lawan)) {
			return Component.translatable("kultivator.gabung.perang.alasan.sudah");
		}
		return DiplomasiGabung.perangMulai(server, sekte, lawan) ? null : Component.translatable("kultivator.gabung.perang.alasan.mati");
	}

	/** {@link GabungBus#PERANG_MULAI} (41D fires it once per side): opens the side record and sends the calls. */
	private static void mulai(MinecraftServer server, String sekte, String lawan) {
		if (!Gabung.aktif() || !GabungConfig.get().perangPanggil || sekte == null || lawan == null || sekte.equals(lawan)) {
			return;
		}
		CompoundTag p = perang(server, sekte);
		if (p == null) {
			return;
		}
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan k = data.peek(uuid);
			if (k != null) {
				k.catat(hari, "kultivator.gabung.riwayat.perang_mulai", sekte, lawan);
			}
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				antre(player, 20);
			}
		}
		data.setDirty();
		Kultivator.LOGGER.info("Gabung: war call {} vs {} (day {})", sekte, lawan, hari);
	}

	/** Queues the call for {@code player} in {@code delay} ticks (the Sibuk door is asked when it is due). */
	static void antre(ServerPlayer player, int delay) {
		ANTRE.put(player.getUUID(), player.level().getGameTime() + Math.max(1, delay));
	}

	/** Hands the War Call to {@code player} now (chat card, title, bell, voice). */
	private static void sampaikan(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		String sekte = k == null ? null : k.sekteAktif();
		CompoundTag p = perang(server, sekte);
		if (p == null) {
			return;
		}
		String tanda = sekte + ":" + p.getLongOr(MULAI, 0L);
		CompoundTag mine = k.bagian(BAGIAN);
		if (tanda.equals(mine.getStringOr(DIBERI, ""))) {
			return;
		}
		mine.putString(DIBERI, tanda);
		long hari = GabungApi.hari(server);
		CompoundTag panggil = p.getCompoundOrEmpty(PANGGIL);
		if (!panggil.contains(player.getStringUUID())) {
			panggil.putLong(player.getStringUUID(), hari);
			p.put(PANGGIL, panggil);
		}
		data.setDirty();
		String lawan = p.getStringOr(LAWAN, "");
		boolean wajib = terikat(k.pangkat);
		Msg.title(player, Component.translatable("kultivator.gabung.perang.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.gabung.perang.subjudul", nama(sekte), nama(lawan)).withStyle(ChatFormatting.YELLOW),
				10, 70, 20);
		player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.kartu.atas").withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.kartu.isi", nama(sekte), nama(lawan), DURASI_HARI)
				.withStyle(ChatFormatting.YELLOW));
		player.sendSystemMessage(Component.translatable(wajib ? "kultivator.gabung.perang.kartu.wajib" : "kultivator.gabung.perang.kartu.bebas",
				WAJIB_HARI).withStyle(wajib ? ChatFormatting.RED : ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("  ").append(tombol("kultivator.gabung.perang.tombol.lihat", "/kultivasi gabung perang",
				"kultivator.gabung.perang.tombol.lihat.tip", ChatFormatting.GOLD)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9F, 0.7F);
		Voice.later(14, () -> player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK,
				SoundSource.PLAYERS, 0.8F, 0.7F));
		Voice.later(28, () -> player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE,
				SoundSource.PLAYERS, 0.7F, 0.8F));
		Voice.inner(player, "gabung.panggilan_perang", nama(sekte));
	}

	/** A clickable chat button {@code [Label]} that runs {@code command}. */
	static MutableComponent tombol(String key, String command, String tipKey, ChatFormatting color) {
		return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(tipKey))));
	}

	// ================================================================== score and end

	/**
	 * Moves {@code sekte}'s war score by {@code delta} for {@code sebab} (a mission): 41D's record ({@code perangSkor},
	 * mirrored for the enemy, +-{@link #SKOR_MENANG} ends the war) and this side's log.
	 */
	public static void skor(MinecraftServer server, String sekte, int delta, String sebab) {
		CompoundTag p = perang(server, sekte);
		if (p == null || delta == 0) {
			return;
		}
		ListTag log = p.getListOrEmpty(LOG);
		CompoundTag line = new CompoundTag();
		line.putLong("hari", GabungApi.hari(server));
		line.putString("sebab", sebab == null ? "" : sebab);
		line.putInt("skor", delta);
		log.add(line);
		while (log.size() > LOG_MAKS) {
			log.remove(0);
		}
		p.put(LOG, log);
		DiplomasiGabung.Perang war = catatan(server, sekte);
		if (war != null) {
			p.putInt(SKOR_AKHIR, Math.max(-SKOR_MENANG, Math.min(SKOR_MENANG, war.skor() + delta)));
		}
		GabungData.get(server).setDirty();
		DiplomasiGabung.perangSkor(server, sekte, delta, "gabung." + (sebab == null ? "misi" : sebab));
	}

	/** {@link GabungBus#PERANG_AKHIR} (41D, once per side, after its record closed): rewards, messages, history (idempotent). */
	private static void selesai(MinecraftServer server, String sekte, String lawan, boolean menang) {
		MisiPerang.batalSekte(server, sekte);
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.peekSekte(sekte);
		if (d == null) {
			return;
		}
		CompoundTag bagian = d.bagian(BAGIAN);
		CompoundTag p = bagian.getCompoundOrEmpty(AKTIF);
		if (!p.getStringOr(LAWAN, "").equals(lawan)) {
			return;
		}
		int s = p.getIntOr(SKOR_AKHIR, p.getIntOr(SKOR_KEMARIN, 0));
		boolean seri = !menang && s == 0;
		long hari = GabungApi.hari(server);
		CompoundTag ikut = p.getCompoundOrEmpty(IKUT);
		List<UUID> berhak = new ArrayList<>();
		for (String key : ikut.keySet()) {
			if (ikut.getIntOr(key, 0) >= HADIAH_MISI_MIN) {
				try {
					berhak.add(UUID.fromString(key));
				} catch (IllegalArgumentException ignored) {
					// a damaged key is skipped
				}
			}
		}
		String hasil = menang ? "menang" : seri ? "seri" : "kalah";
		for (UUID uuid : data.anggotaSekte(sekte)) {
			Keanggotaan k = data.peek(uuid);
			if (k == null) {
				continue;
			}
			k.catat(hari, "kultivator.gabung.riwayat.perang_" + hasil, lawan, Integer.toString(s));
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.akhir." + hasil, nama(sekte), nama(lawan), s)
						.withStyle(menang ? ChatFormatting.GOLD : seri ? ChatFormatting.GRAY : ChatFormatting.DARK_RED));
				if (menang) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
							0.7F, 0.9F);
				}
			}
		}
		if (menang) {
			for (UUID uuid : berhak) {
				hadiah(server, data, uuid, sekte);
			}
		}
		bagian.remove(AKTIF);
		bagian.putString("lawanAkhir", lawan);
		bagian.putInt("skorAkhir", s);
		bagian.putLong("hariAkhir", hari);
		bagian.putString("hasilAkhir", hasil);
		data.setDirty();
	}

	/** The victory share of one member: a technique jade (online, fitting spec found) or 300 PK (now or at login). */
	private static void hadiah(MinecraftServer server, GabungData data, UUID uuid, String sekte) {
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player != null) {
			String spec = pilihGiok(player, sekte);
			ItemStack slip = spec == null ? ItemStack.EMPTY : JadeSlips.stack(spec);
			if (!slip.isEmpty()) {
				Component name = slip.getHoverName();
				beri(player, slip);
				player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.hadiah.giok", name).withStyle(ChatFormatting.GOLD));
			} else {
				GabungApi.tambahPk(player, HADIAH_PK, "gabung.perang.hadiah");
				player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.hadiah.pk", HADIAH_PK).withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		Keanggotaan k = data.peek(uuid);
		if (k != null && k.anggotaDi(sekte)) {
			CompoundTag mine = k.bagian(BAGIAN);
			mine.putInt(HADIAH, mine.getIntOr(HADIAH, 0) + HADIAH_PK);
			data.setDirty();
		}
	}

	/**
	 * A technique the player does not know, of grade class - 1 (class 1 and 2: Huang), in one of the sect style's Daos, that
	 * a jade slip may hold; picked by the player and the day so a re-roll gives the same answer. Null when none fits.
	 */
	static @Nullable String pilihGiok(ServerPlayer player, String sekte) {
		int kelas = GabungApi.kelas(sekte);
		TechniqueGrade[] grades = TechniqueGrade.values();
		TechniqueGrade grade = grades[Math.max(0, Math.min(TechniqueGrade.ABADI.ordinal(), kelas - 2))];
		Corak corak = GabungApi.corak(sekte);
		CultivationData cultivation = Cultivators.get(player);
		List<String> pool = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.ofGrade(grade)) {
			if (spec.slipAllowed() && spec.dao() != null && corak.punyaDao(spec.dao()) && !cultivation.learnedIds().contains(spec.id())) {
				pool.add(spec.id());
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		long h = player.getUUID().getLeastSignificantBits() ^ GabungApi.hari(player.level().getServer()) * 7919L;
		return pool.get((int) Math.floorMod(h, (long) pool.size()));
	}

	/** Into the inventory; what does not fit drops at the player's feet for them alone. */
	static void beri(ServerPlayer player, ItemStack stack) {
		ItemStack copy = stack.copy();
		if (player.getInventory().add(copy) && copy.isEmpty()) {
			return;
		}
		if (!copy.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), copy);
			item.setTarget(player.getUUID());
			item.setNoPickUpDelay();
			player.level().addFreshEntity(item);
		}
	}

	/** Counts a finished war mission of {@code player} (win or loss) toward the reward and the duty. */
	static void ikutMisi(MinecraftServer server, String sekte, UUID player) {
		CompoundTag p = perang(server, sekte);
		if (p == null) {
			return;
		}
		CompoundTag ikut = p.getCompoundOrEmpty(IKUT);
		ikut.putInt(player.toString(), ikut.getIntOr(player.toString(), 0) + 1);
		p.put(IKUT, ikut);
		GabungData.get(server).setDirty();
	}

	/** One captive fewer (freed by Jemput Tawanan). */
	static void bebaskanTawanan(MinecraftServer server, String sekte, int n) {
		CompoundTag p = perang(server, sekte);
		if (p != null && n > 0) {
			p.putInt(TAWANAN, Math.max(0, p.getIntOr(TAWANAN, 0) - n));
			GabungData.get(server).setDirty();
		}
	}

	// ================================================================== the daily pass and lazy upkeep

	private static void tick(MinecraftServer server) {
		if (!Gabung.aktif()) {
			return;
		}
		int t = server.getTickCount();
		if (t % 20 == 3 && !ANTRE.isEmpty()) {
			for (Map.Entry<UUID, Long> e : List.copyOf(ANTRE.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player == null) {
					ANTRE.remove(e.getKey());
					continue;
				}
				long now = player.level().getGameTime();
				if (now < e.getValue()) {
					continue;
				}
				if (Sibuk.why(player) != null) {
					ANTRE.put(e.getKey(), now + Sibuk.RETRY_TICKS);
					continue;
				}
				ANTRE.remove(e.getKey());
				sampaikan(player);
			}
		}
		if (t % 100 == 41) {
			long hari = GabungApi.hari(server);
			if (hari != hariTerakhir) {
				boolean first = hariTerakhir == Long.MIN_VALUE;
				hariTerakhir = hari;
				if (!first) {
					harian(server, hari);
				}
			}
		}
	}

	/** Once per world day: captives after a falling score, the last known score, the duty of the members online. */
	private static void harian(MinecraftServer server, long hari) {
		GabungData data = GabungData.get(server);
		for (String sekte : SekteApi.diplomasi().sects()) {
			DiplomasiGabung.Perang war = catatan(server, sekte);
			CompoundTag p = war == null ? null : perang(server, sekte);
			if (p == null) {
				continue;
			}
			if (war.skor() < p.getIntOr(SKOR_KEMARIN, 0)) {
				RandomSource random = RandomSource.create(server.overworld().getSeed() ^ hari * 6151L ^ sekte.hashCode());
				if (random.nextBoolean()) {
					p.putInt(TAWANAN, Math.min(TAWANAN_MAKS, p.getIntOr(TAWANAN, 0) + 1));
				}
			}
			p.putInt(SKOR_KEMARIN, war.skor());
			p.putInt(SKOR_AKHIR, war.skor());
			data.setDirty();
		}
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			Keanggotaan k = data.peek(player.getUUID());
			if (k != null) {
				periksaWajib(player, k, hari);
			}
		}
	}

	/** Lazy upkeep (login, compound, screens): pending reward, stale mission, the call not yet received, the duty. */
	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		MinecraftServer server = player.level().getServer();
		CompoundTag mine = k.bagian(BAGIAN);
		int pending = mine.getIntOr(HADIAH, 0);
		if (pending > 0 && k.sekteAktif() != null) {
			mine.remove(HADIAH);
			GabungApi.tambahPk(player, pending, "gabung.perang.hadiah");
			player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.hadiah.pk", pending).withStyle(ChatFormatting.GOLD));
		}
		MisiPerang.bersihkanBasi(player, k);
		CompoundTag p = perang(server, k.sekteAktif());
		if (p != null) {
			String tanda = k.sekte + ":" + p.getLongOr(MULAI, 0L);
			if (!tanda.equals(mine.getStringOr(DIBERI, ""))) {
				antre(player, 60);
			}
		}
		periksaWajib(player, k, hari);
	}

	/** Rule A4 once per war for an obliged member who let {@link #WAJIB_HARI} days pass without a war mission. */
	private static void periksaWajib(ServerPlayer player, Keanggotaan k, long hari) {
		MinecraftServer server = player.level().getServer();
		String sekte = k.sekteAktif();
		CompoundTag p = perang(server, sekte);
		if (p == null || !terikat(k.pangkat)) {
			return;
		}
		String id = player.getStringUUID();
		CompoundTag panggil = p.getCompoundOrEmpty(PANGGIL);
		if (!panggil.contains(id) || hari - panggil.getLongOr(id, hari) < WAJIB_HARI) {
			return;
		}
		if (p.getCompoundOrEmpty(IKUT).getIntOr(id, 0) > 0) {
			return;
		}
		CompoundTag lalai = p.getCompoundOrEmpty(LALAI);
		if (lalai.getBooleanOr(id, false)) {
			return;
		}
		lalai.putBoolean(id, true);
		p.put(LALAI, lalai);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable("kultivator.gabung.perang.lalai", nama(sekte), Aturan.A4.poin())
				.withStyle(ChatFormatting.RED));
		GabungApi.langgar(player, Aturan.A4, player.blockPosition(), false);
	}

	// ================================================================== sheets (41U-2: LembarPapan, LembarPolitik)

	/**
	 * The {@code perang} compound of {@code gabung_papan} for {@code player}: {@code lawan} T, {@code lawan_id} S, {@code skor}
	 * I, {@code sisa_hari} I, {@code wajib} B. Null without war (the key is then absent).
	 */
	public static @Nullable CompoundTag lembarPapan(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		CompoundTag p = perang(server, sekte);
		if (p == null) {
			return null;
		}
		CompoundTag t = new CompoundTag();
		String lawan = p.getStringOr(LAWAN, "");
		LembarKunci.teks(t, GabungLembar.Papan.LAWAN, nama(lawan));
		t.putString(GabungLembar.Papan.LAWAN_ID, lawan);
		t.putInt(LembarKunci.SKOR, nilaiSkor(server, sekte));
		t.putInt(LembarKunci.SISA_HARI, (int) sisaHari(server, sekte));
		t.putBoolean(GabungLembar.Papan.WAJIB, wajib(player));
		return t;
	}

	/**
	 * The {@code perang} compound of {@code gabung_politik}: {@code lawan} T, {@code lawan_id} S, {@code skor} I,
	 * {@code sisa_hari} I, {@code misi} D [{@code id} S, {@code nama} T, {@code selesai} B], {@code garis} IA [x1, z1, x2, z2]
	 * (gate to gate, absent when a gate is unknown), {@code ikut} B. Null without war.
	 */
	public static @Nullable CompoundTag lembarPolitik(ServerPlayer player) {
		CompoundTag t = lembarPapan(player);
		if (t == null) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String lawan = t.getStringOr(GabungLembar.Papan.LAWAN_ID, "");
		t.remove(GabungLembar.Papan.WAJIB);
		t.put(GabungLembar.Politik.MISI, MisiPerang.lembarPolitik(player));
		BlockPos a = gerbang(sekte);
		BlockPos b = gerbang(lawan);
		if (a != null && b != null) {
			t.put(GabungLembar.Politik.GARIS, new IntArrayTag(new int[]{a.getX(), a.getZ(), b.getX(), b.getZ()}));
		}
		t.putBoolean(GabungLembar.Politik.IKUT, Politik.ikutDuel(server, player.getUUID()));
		return t;
	}

	/** The sect's mountain gate (generated, else planned), or null. */
	static @Nullable BlockPos gerbang(@Nullable String sekte) {
		if (sekte == null || sekte.isEmpty()) {
			return null;
		}
		DiplomasiService d = SekteApi.diplomasi();
		BlockPos gate = d.gate(sekte);
		return gate != null ? gate : d.plannedGate(sekte);
	}

	// ================================================================== commands

	/** {@code /kultivasi gabung perang [terima <misi>|batal]}: the same actions as the board's war tab. */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nodePemain() {
		return Commands.literal("perang")
				.executes(PanggilanPerang::status)
				.then(Commands.literal("terima")
						.then(Commands.argument("misi", StringArgumentType.word()).suggests((ctx, b) ->
										net.minecraft.commands.SharedSuggestionProvider.suggest(MisiPerang.Jenis.ids(), b))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Component why = MisiPerang.terima(player, StringArgumentType.getString(ctx, "misi"));
									if (why != null) {
										ctx.getSource().sendFailure(why);
										return 0;
									}
									return 1;
								})))
				.then(Commands.literal("batal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Component why = MisiPerang.batal(player);
					if (why != null) {
						ctx.getSource().sendFailure(why);
						return 0;
					}
					return 1;
				}));
	}

	/** The war status in chat with one clickable line per mission. */
	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		if (!Gabung.aktif()) {
			source.sendFailure(Component.translatable("kultivator.gabung.cmd.mati"));
			return 0;
		}
		String sekte = GabungApi.sekte(player.getUUID());
		CompoundTag p = perang(server, sekte);
		if (p == null) {
			source.sendFailure(Component.translatable(sekte == null ? "kultivator.gabung.perang.status.bukan" : "kultivator.gabung.perang.status.damai"));
			return 0;
		}
		String lawan = p.getStringOr(LAWAN, "");
		int s = nilaiSkor(server, sekte);
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.perang.status.judul", nama(sekte), nama(lawan))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.perang.status.skor", s, sisaHari(server, sekte), tawanan(server, sekte),
				misiDiikuti(server, sekte, player.getUUID())).withStyle(s >= 0 ? ChatFormatting.YELLOW : ChatFormatting.RED), false);
		if (wajib(player)) {
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.perang.status.wajib", WAJIB_HARI).withStyle(ChatFormatting.RED), false);
		}
		MisiPerang.cetak(player);
		return 1;
	}

	/**
	 * {@code uji gabung perang <lawan> | skor <n> | akhir | misi <jenis> | tawanan <n>}: a war of the tester's sect now
	 * (through 41D's {@code DiplomasiGabung.perangMulai}), a score change, the end, a war mission started next to the tester
	 * (no walk), captives.
	 */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nodeUji() {
		return Commands.literal("perang")
				.then(Commands.literal("skor")
						.then(Commands.argument("n", IntegerArgumentType.integer(-SKOR_MENANG, SKOR_MENANG)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							MinecraftServer server = ctx.getSource().getServer();
							String sekte = GabungApi.sekte(player.getUUID());
							if (!aktif(server, sekte)) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.perang.status.damai"));
								return 0;
							}
							int n = IntegerArgumentType.getInteger(ctx, "n");
							skor(server, sekte, n, "uji");
							ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.perang.uji.skor", n, nilaiSkor(server, sekte)), false);
							return 1;
						})))
				.then(Commands.literal("akhir").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					MinecraftServer server = ctx.getSource().getServer();
					String sekte = GabungApi.sekte(player.getUUID());
					if (!aktif(server, sekte)) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.perang.status.damai"));
						return 0;
					}
					DiplomasiGabung.perangAkhir(server, sekte, null);
					return 1;
				}))
				.then(Commands.argument("lawan", StringArgumentType.word()).suggests(dev.kultivator.sekte.gabung.GabungUji.SEKTE).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					String sekte = GabungApi.sekte(player.getUUID());
					if (sekte == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.perang.status.bukan"));
						return 0;
					}
					String lawan = StringArgumentType.getString(ctx, "lawan");
					Component why = nyatakan(ctx.getSource().getServer(), sekte, lawan);
					if (why != null) {
						ctx.getSource().sendFailure(why);
						return 0;
					}
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.perang.uji.mulai", nama(sekte), nama(lawan)), true);
					return 1;
				}))
				.then(Commands.literal("tawanan")
						.then(Commands.argument("n", IntegerArgumentType.integer(0, TAWANAN_MAKS)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							CompoundTag p = perang(ctx.getSource().getServer(), GabungApi.sekte(player.getUUID()));
							if (p == null) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.gabung.perang.status.damai"));
								return 0;
							}
							int n = IntegerArgumentType.getInteger(ctx, "n");
							p.putInt(TAWANAN, n);
							GabungData.get(ctx.getSource().getServer()).setDirty();
							ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.gabung.perang.uji.tawanan", n), false);
							return 1;
						})))
				.then(Commands.literal("misi")
						.then(Commands.argument("jenis", StringArgumentType.word()).suggests((ctx, b) ->
										net.minecraft.commands.SharedSuggestionProvider.suggest(MisiPerang.Jenis.ids(), b))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									Component why = MisiPerang.ujiMulai(player, StringArgumentType.getString(ctx, "jenis"));
									if (why != null) {
										ctx.getSource().sendFailure(why);
										return 0;
									}
									return 1;
								})));
	}

	private PanggilanPerang() {
	}
}
