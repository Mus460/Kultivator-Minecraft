package dev.kultivator.sekte.gabung.masuk;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.masuk.corak.KolamDarah;
import dev.kultivator.sekte.gabung.masuk.corak.LabirinBatu;
import dev.kultivator.sekte.gabung.masuk.corak.LimaPilar;
import dev.kultivator.sekte.gabung.masuk.corak.LorongPerunggu;
import dev.kultivator.sekte.gabung.masuk.corak.PikulGunung;
import dev.kultivator.sekte.gabung.masuk.corak.SatuTungku;
import dev.kultivator.sekte.gabung.masuk.corak.SeratusTebasan;
import dev.kultivator.sekte.gabung.masuk.corak.TaklukkanBinatang;
import dev.kultivator.sekte.gabung.masuk.corak.TigaCawan;
import dev.kultivator.sekte.gabung.masuk.corak.UjianDasar;
import dev.kultivator.sekte.gabung.masuk.corak.UjianSenyap;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 2 of the entrance test, the Ujian Corak of DESIGN 41.5.4 (module 41B-C): one real mini-game per sect style, one
 * class per style in {@code masuk/corak/*}. 41B's {@code HariUjian} calls {@link #mulai} between stage 1 (Tangga Ujian)
 * and stage 3 (Ujian Hati) and gets the stage score {@code S2} back through {@link Selesai}.
 * <p>
 * Common rules (41.5.4): Sibuk {@code gabung.ujian} for the whole test, at most one test per sect at a time (another
 * player waits up to 60 s), flight is refused (the player is set down with a line), a teleport or leaving the field for
 * 3 s or dying (the {@link DeathGuard} keeps the player alive) fails the stage with S2 0 and no other loss. Every field
 * is entities, bodies, mobs and test items only: never a block (41.0 item 1). Entities count under
 * {@link GabungEntities#ANGGARAN} (41.12.3; the walls of the maze count as one maze, see {@link GabungEntities}).
 * <p>
 * HUD: {@link GabungPayloads#kirimUjian} with {@code tahap} 2, {@code tekanan} 0, {@code waktu} ticks left,
 * {@code skor} the live S2 and {@code data}: byte 0 = {@link Corak#ordinal()}, bytes 1.. per style (unsigned; "f" = a
 * 0-1 value as 0-255), shared with 41U-1's HUD and 41V-P's {@code UjianFx}:
 * <pre>
 * PEDANG   1 kena (hits) 2 nyala (lights so far) 3 total lights (100) 4 lit dummy index+1 (0 none) 5 combo
 * BUDDHA   1 wave 1-3 2 waves passed 3 monks standing 4 hp f of the player 5 vow broken 0/1
 * BINATANG 1 phase (0 subdue, 1 feed, 2 done) 2 beast hp f 3 own beast helped 0/1 4 subdue line f (0.2)
 * FORMASI  1 eyes done 0-3 2 wrong steps 3 trigram of the next eye (0-7) 4..6 the three trigrams of the riddle
 * UNSUR    1 pillar 0-4 2 needle f 3 band centre f 4 band width f 5 accuracy of this pillar f 6 holding 0/1
 *          7..11 accuracy of pillars 0-4 f 12 seconds held of 15 on this pillar
 * ALKIMIA  1 phase (0 brewing, 1 a furnace is refining, 2 done) 2 attempts 3 best quality (0 none, 1-4 Low-Perfect)
 *          4 furnace found 0/1
 * TUBUH    1 progress f bottom->top 2 stone in hand 0/1 3 drops
 * DARAH    1 wave 1-3 2 foes alive 3 hp f 4 sacrifice ready 0/1 5 strength seconds left 6 in the pool 0/1
 * RACUN    1 cup 0-2 2 phase (0 choose a cup, 1 choose the antidote, 2 done) 3 correct so far 4 seconds to choose
 *          5..7 result per cup (0 waiting, 1 right, 2 wrong, 3 endured)
 * BAYANGAN 1 times seen 0-3 2 jade taken 0/1 3 alert f (the fullest guard) 4 dark place 0/1 5 nearest guard blocks
 * </pre>
 * Test command (op 2): {@code uji gabung corak <corak> [sekte]} starts one test at the player (or the sect's titik),
 * {@code uji gabung corak batal} cancels it.
 */
public final class UjianCorak {

	/** Sibuk state of the whole entrance test (41.0 item 6). */
	public static final String SIBUK = "gabung.ujian";
	/** 41.5.4: another player waits at most this long for the field of a sect. */
	public static final int TUNGGU_DETIK = 60;

	/** The result of a corak test. */
	public record Hasil(double skor, boolean gagal, String alasan, Corak corak) {
	}

	/** Told once when the test ends (also when cancelled). */
	@FunctionalInterface
	public interface Selesai {
		void selesai(ServerPlayer player, Hasil hasil);
	}

	private static final class Sesi {
		final UjianDasar ujian;
		final @Nullable Selesai balas;
		final boolean dariPerintah;
		Vec3 terakhir;
		int luar;
		int terbangPesan;

		Sesi(UjianDasar ujian, @Nullable Selesai balas, boolean dariPerintah, Vec3 posisi) {
			this.ujian = ujian;
			this.balas = balas;
			this.dariPerintah = dariPerintah;
			this.terakhir = posisi;
		}
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	/** Sect id -> the player whose test holds its field. */
	private static final Map<String, UUID> LAPANGAN = new HashMap<>();
	private static boolean initialised;

	private UjianCorak() {
	}

	// ================================================================== public API

	/**
	 * Starts the corak test of {@code sekte} (style {@code corak}, null = the sect's own) for {@code player}; {@code balas}
	 * hears the end. Returns null when it started, else the reason (already in a test, the field is busy, busy elsewhere,
	 * the budget is full, the field could not be built).
	 */
	public static @Nullable Component mulai(ServerPlayer player, String sekte, @Nullable Corak corak, @Nullable Selesai balas) {
		return mulai(player, sekte, corak, balas, false);
	}

	/** True while {@code player} runs a corak test. */
	public static boolean berjalan(UUID player) {
		return SESI.containsKey(player);
	}

	/** The live S2 of the player's test, or -1. */
	public static double skor(UUID player) {
		Sesi s = SESI.get(player);
		return s == null ? -1.0D : s.ujian.skor();
	}

	/** Cancels the player's test (S2 0, reason {@code batal}); the callback still hears it. */
	public static void batal(ServerPlayer player) {
		Sesi s = SESI.get(player.getUUID());
		if (s != null) {
			s.ujian.gagalkan("batal");
			tutup(player, s, "batal");
		}
	}

	/** Seconds until the field of {@code sekte} is free (0 = free now). */
	public static int tungguLapangan(MinecraftServer server, String sekte) {
		UUID holder = LAPANGAN.get(sekte);
		Sesi s = holder == null ? null : SESI.get(holder);
		if (s == null) {
			return 0;
		}
		return Mth.clamp((s.ujian.batasTick() - s.ujian.umur()) / 20, 1, TUNGGU_DETIK);
	}

	// ================================================================== init

	/**
	 * Hooks and the test command; called from {@code GabungEntities.register()} (both sides, always: the hooks idle until a
	 * test runs, and {@link #mulai} refuses while 41 is off). Safe to call twice.
	 */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(UjianCorak::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<UUID, Sesi> e : List.copyOf(SESI.entrySet())) {
				e.getValue().ujian.bersihkan();
			}
			SESI.clear();
			LAPANGAN.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			Sesi s = SESI.get(p.getUUID());
			if (s != null) {
				s.ujian.gagalkan("keluar");
				tutup(p, s, "keluar");
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> UjianDasar.ambilItemUjian(handler.getPlayer()));
		Sibuk.register(SIBUK, p -> SESI.containsKey(p.getUUID()));
		DeathGuard.register(DeathGuard.Stage.DUEL, (player, source, amount) -> {
			Sesi s = SESI.get(player.getUUID());
			if (s == null || s.ujian.selesai()) {
				return false;
			}
			player.setHealth(Math.max(2.0F, player.getMaxHealth() * 0.3F));
			s.ujian.tumbang();
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (SESI.isEmpty()) {
				return true;
			}
			for (Sesi s : List.copyOf(SESI.values())) {
				if (!s.ujian.selesai() && s.ujian.level() == victim.level() && !s.ujian.bolehLuka(victim, source, amount)) {
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (SESI.isEmpty()) {
				return;
			}
			for (Sesi s : List.copyOf(SESI.values())) {
				if (!s.ujian.selesai() && s.ujian.level() == victim.level()) {
					s.ujian.mati(victim, source);
				}
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			Sesi s = sesi(player);
			InteractionResult r = s == null ? InteractionResult.PASS : s.ujian.pakaiItem(stack, hand);
			// A test item has no vanilla use on either side (the trial feed is not eaten, the herbs are not chewed).
			return r == InteractionResult.PASS && UjianDasar.itemUjian(stack) ? InteractionResult.SUCCESS : r;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			Sesi s = sesi(player);
			return s == null ? InteractionResult.PASS : s.ujian.pakaiEntitas(entity, hand);
		});
		AlchemyFurnace.addFinishListener((level, pos, owner, result, quality, success, alchemyLevel) -> {
			Sesi s = owner == null ? null : SESI.get(owner);
			if (s != null && !s.ujian.selesai()) {
				s.ujian.pilSelesai(level, pos, result, quality, success);
			}
		});
		// the friendly duel of Seratus Tebasan (41.5.4): the listener only passes the result on, the runner ends the test
		DuelEndListeners.add((session, winner, loser) -> {
			ServerPlayer p = winner instanceof ServerPlayer w ? w : loser instanceof ServerPlayer l ? l : null;
			Sesi s = p == null ? null : sesi(p);
			if (s != null && s.ujian instanceof SeratusTebasan tebasan) {
				tebasan.duelSelesai(winner, loser);
			}
		});
		GabungApi.daftarUji("corak", UjianCorak::perintah);
		for (Corak c : Corak.values()) {
			dev.kultivator.sekte.gabung.GabungUji.chips("gabung corak " + c.id());
		}
		dev.kultivator.sekte.gabung.GabungUji.chips("gabung corak batal");
	}

	private static @Nullable Sesi sesi(Player player) {
		if (!(player instanceof ServerPlayer sp) || SESI.isEmpty()) {
			return null;
		}
		Sesi s = SESI.get(sp.getUUID());
		return s == null || s.ujian.selesai() ? null : s;
	}

	// ================================================================== start

	private static @Nullable Component mulai(ServerPlayer player, String sekte, @Nullable Corak corak, @Nullable Selesai balas, boolean perintah) {
		String id = sekte == null ? "" : sekte;
		if (!GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.belum_tersedia");
		}
		if (SESI.containsKey(player.getUUID())) {
			return Component.translatable("kultivator.gabung.corak.sedang");
		}
		MinecraftServer server = player.level().getServer();
		if (!id.isEmpty() && LAPANGAN.containsKey(id)) {
			ServerPlayer holder = server.getPlayerList().getPlayer(LAPANGAN.get(id));
			return Component.translatable("kultivator.gabung.corak.lapangan_dipakai",
					holder == null ? Component.literal("?") : holder.getDisplayName(), tungguLapangan(server, id));
		}
		String busy = Sibuk.whyExcept(player, SIBUK);
		if (busy != null) {
			return Sibuk.text(busy);
		}
		if (!dev.kultivator.dunia.DuniaBudget.mayAdd(player.level(), GabungEntities.ANGGARAN)) {
			return Component.translatable("kultivator.gabung.corak.anggaran");
		}
		Corak c = corak != null ? corak : id.isEmpty() ? Corak.PEDANG : GabungApi.corak(id);
		int kelas = id.isEmpty() ? 1 : GabungApi.kelas(id);
		long hari = GabungApi.hari(server);
		long benih = player.getUUID().getMostSignificantBits() ^ (long) id.hashCode() * 0x9E3779B97F4A7C15L ^ hari * 1_000_003L;
		Vec3 pusat = pusat(player, id, c);
		UjianDasar ujian = buat(c, player, id, kelas, pusat, benih);
		Component tolak;
		try {
			tolak = ujian.siapkan();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: corak test {} failed to start", c.id(), exception);
			tolak = Component.translatable("kultivator.gabung.corak.tidak_bisa");
		}
		if (tolak != null) {
			ujian.bersihkan();
			return tolak;
		}
		SESI.put(player.getUUID(), new Sesi(ujian, balas, perintah, player.position()));
		if (!id.isEmpty()) {
			LAPANGAN.put(id, player.getUUID());
		}
		GabungApi.sibuk(player, SIBUK, ujian.batasTick() + 200);
		Msg.title(player, Component.translatable("kultivator.gabung.corak." + c.ujian() + ".nama").withColor(c.warna()),
				Component.translatable("kultivator.gabung.corak.tahap2"), 5, 50, 10);
		Msg.chat(player, Component.translatable("kultivator.gabung.corak." + c.ujian() + ".aturan").withStyle(ChatFormatting.GRAY));
		kirimHud(player, ujian);
		return null;
	}

	/** The field's centre: the style's titik when it is near and sane, else the player's feet. */
	private static Vec3 pusat(ServerPlayer player, String sekte, Corak corak) {
		if (!sekte.isEmpty()) {
			String nama = switch (corak) {
				case BUDDHA -> TitikGabung.LORONG;
				case BINATANG -> TitikGabung.KANDANG;
				case UNSUR -> TitikGabung.PILAR + "0";
				case TUBUH -> TitikGabung.UJIAN_BAWAH;
				case DARAH -> TitikGabung.UJIAN_DARAH;
				case BAYANGAN -> player.level().isBrightOutside() ? TitikGabung.GUA_RENUNGAN : TitikGabung.TETUA;
				default -> TitikGabung.UJIAN_CORAK;
			};
			net.minecraft.core.BlockPos pos = GabungApi.titik(sekte, nama);
			if (pos != null) {
				Vec3 v = Vec3.atBottomCenterOf(pos);
				if (v.distanceTo(player.position()) <= UjianDasar.TITIK_JAUH && Math.abs(v.y - player.getY()) <= 24.0D) {
					return v;
				}
			}
		}
		return player.position();
	}

	private static UjianDasar buat(Corak c, ServerPlayer p, String sekte, int kelas, Vec3 pusat, long benih) {
		return switch (c) {
			case PEDANG -> new SeratusTebasan(p, sekte, kelas, pusat, benih);
			case BUDDHA -> new LorongPerunggu(p, sekte, kelas, pusat, benih);
			case BINATANG -> new TaklukkanBinatang(p, sekte, kelas, pusat, benih);
			case FORMASI -> new LabirinBatu(p, sekte, kelas, pusat, benih);
			case UNSUR -> new LimaPilar(p, sekte, kelas, pusat, benih);
			case ALKIMIA -> new SatuTungku(p, sekte, kelas, pusat, benih);
			case TUBUH -> new PikulGunung(p, sekte, kelas, pusat, benih);
			case DARAH -> new KolamDarah(p, sekte, kelas, pusat, benih);
			case RACUN -> new TigaCawan(p, sekte, kelas, pusat, benih);
			case BAYANGAN -> new UjianSenyap(p, sekte, kelas, pusat, benih);
		};
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (SESI.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Sesi> e : List.copyOf(SESI.entrySet())) {
			Sesi s = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				s.ujian.gagalkan("keluar");
				tutup(null, s, "keluar");
				SESI.remove(e.getKey());
				continue;
			}
			s.ujian.setPemain(p);
			try {
				langkah(p, s);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: corak test {} failed", s.ujian.corak().id(), exception);
				s.ujian.gagalkan("galat");
				tutup(p, s, "galat");
			}
		}
	}

	private static void langkah(ServerPlayer p, Sesi s) {
		UjianDasar u = s.ujian;
		if (u.selesai()) {
			tutup(p, s, u.alasan());
			return;
		}
		if (p.level() != u.level() || !p.isAlive()) {
			u.gagalkan("wilayah");
			tutup(p, s, "wilayah");
			return;
		}
		// teleport (an ender pearl, a command, a technique): more than 10 blocks in one tick
		if (p.position().distanceToSqr(s.terakhir) > 100.0D) {
			u.gagalkan("teleport");
			tutup(p, s, "teleport");
			return;
		}
		s.terakhir = p.position();
		if (p.getAbilities().flying && !p.isCreative() && !p.isSpectator()) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
			if (s.terbangPesan <= 0) {
				Msg.actionBar(p, Component.translatable("kultivator.gabung.corak.terbang").withStyle(ChatFormatting.RED));
				s.terbangPesan = 60;
			}
		}
		if (s.terbangPesan > 0) {
			s.terbangPesan--;
		}
		double r = u.jariWilayah();
		if (p.position().distanceToSqr(u.pusatWilayah()) > r * r) {
			if (s.luar == 0) {
				Msg.actionBar(p, Component.translatable("kultivator.gabung.corak.peringatan_wilayah").withStyle(ChatFormatting.RED));
			}
			if (++s.luar > UjianDasar.KELUAR_TICK) {
				u.gagalkan("wilayah");
				tutup(p, s, "wilayah");
				return;
			}
		} else {
			s.luar = 0;
		}
		u.majukan();
		u.tik();
		peringatanWaktu(p, u);
		if (!u.selesai() && u.umur() >= u.batasTick()) {
			u.waktuHabis();
		}
		if (u.selesai()) {
			tutup(p, s, u.alasan());
			return;
		}
		if (u.umur() % u.jedaHud() == 0) {
			kirimHud(p, u);
		}
	}

	/** 60, 30 and 10 seconds left: a line on the action bar and a tick of the bell (only when the limit is longer). */
	private static void peringatanWaktu(ServerPlayer p, UjianDasar u) {
		if (u.selesai()) {
			return;
		}
		int sisa = u.batasTick() - u.umur();
		for (int detik : new int[] {60, 30, 10}) {
			if (sisa == detik * 20 && u.batasTick() > (detik + 20) * 20) {
				Msg.actionBar(p, Component.translatable("kultivator.gabung.corak.sisa_waktu", detik)
						.withStyle(detik <= 10 ? ChatFormatting.RED : ChatFormatting.YELLOW));
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5F,
						detik <= 10 ? 1.6F : 1.2F);
			}
		}
	}

	private static void kirimHud(ServerPlayer p, UjianDasar u) {
		byte[] data = new byte[GabungPayloads.UJIAN_DATA_MAKS];
		data[0] = (byte) u.corak().ordinal();
		int used = 1 + Math.max(0, u.isiData(data));
		GabungPayloads.kirimUjian(p, 2, 0.0F, Math.max(0, u.batasTick() - u.umur()), (float) u.skor(),
				java.util.Arrays.copyOf(data, Math.min(used, data.length)));
	}

	// ================================================================== end

	private static void tutup(@Nullable ServerPlayer p, Sesi s, String sebab) {
		UjianDasar u = s.ujian;
		SESI.values().remove(s);
		LAPANGAN.values().remove(u.pemain().getUUID());
		try {
			u.bersihkan();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: corak test {} failed to clean up", u.corak().id(), exception);
		}
		String alasan = u.alasan().isEmpty() ? sebab : u.alasan();
		Hasil hasil = new Hasil(u.skorAkhir(), u.gagal(), alasan, u.corak());
		if (p != null) {
			GabungPayloads.kirimUjian(p, 0, 0.0F, 0, (float) hasil.skor(), new byte[0]);
			int persen = (int) Math.round(hasil.skor() * 100.0D);
			Component judul = Component.translatable("kultivator.gabung.corak.akhir." + alasan.toLowerCase(Locale.ROOT));
			Msg.chat(p, Component.translatable("kultivator.gabung.corak.hasil", judul, persen)
					.withStyle(hasil.gagal() ? ChatFormatting.RED : persen >= 70 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), hasil.gagal() || persen < 35 ? SoundEvents.ANVIL_LAND : SoundEvents.PLAYER_LEVELUP,
					SoundSource.PLAYERS, hasil.gagal() ? 0.4F : 0.6F, hasil.gagal() ? 0.7F : persen >= 70 ? 1.2F : 0.9F);
			if (s.dariPerintah) {
				GabungApi.lepasSibuk(p, SIBUK);
			}
		}
		if (s.balas != null && p != null) {
			try {
				s.balas.selesai(p, hasil);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: corak test callback failed", exception);
			}
		}
	}

	// ================================================================== test command

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> perintah() {
		var root = Commands.literal("corak");
		root.then(Commands.literal("batal").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			batal(p);
			return 1;
		}));
		for (Corak c : Corak.values()) {
			root.then(Commands.literal(c.id())
					.executes(ctx -> mulaiUji(ctx, c, ""))
					.then(Commands.argument("sekte", StringArgumentType.word())
							.executes(ctx -> mulaiUji(ctx, c, StringArgumentType.getString(ctx, "sekte")))));
		}
		return root;
	}

	private static int mulaiUji(CommandContext<CommandSourceStack> ctx, Corak c, String sekte) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Component tolak = mulai(p, sekte, c, (player, hasil) -> ctx.getSource().sendSuccess(() -> Component.translatable(
				"kultivator.gabung.corak.uji_hasil", c.id(), String.format(Locale.ROOT, "%.2f", hasil.skor()), hasil.alasan()), false), true);
		if (tolak != null) {
			ctx.getSource().sendFailure(tolak);
			return 0;
		}
		return 1;
	}

	/** Active tests (for {@code uji gabung anggaran}). */
	public static List<String> ringkasan() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<UUID, Sesi> e : SESI.entrySet()) {
			out.add(e.getValue().ujian.corak().id() + " " + e.getKey() + " t=" + e.getValue().ujian.umur());
		}
		return out;
	}
}
