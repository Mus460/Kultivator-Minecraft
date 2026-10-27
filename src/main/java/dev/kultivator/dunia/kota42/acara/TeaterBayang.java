package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The shadow theatre of the night district (DESIGN 42.9.6, 42.7.6): a {@link LayarBayangEntity} on the {@code panggung}
 * titik of the theatre lot ({@code acara=wayang}), a puppeteer (the dalang, clip {@code DALANG}, wand prop) behind it, a
 * ticket of 2 NB (click the screen; free for ever after the premiere), two shows a night, 19:00 and 21:00.
 * <p>
 * <b>A show</b> is 1800 ticks: the lamp is lit and the four acts of the script run (the client plays them: 4 x 300 ticks, see
 * {@code LayarBayangRenderer.BABAK_NASKAH}) while the dalang speaks a line for each act, then the cloth holds on the last
 * pose and he speaks the two epilogue lines and the thanks. One of the four scripts a night ({@code kera_sakti},
 * {@code ular_putih}, {@code hua_mulan}, {@code hakim_bao}, by day, show and place), the gong-drum-erhu air of {@link Lagu}
 * under it. The residents of the place sit around ({@code HidupApi.kerumunan}). A player with a ticket within 16 blocks of the
 * screen for 80 % of the show gets <i>Terhibur</i>, one lore page and the advancement {@code kota42_wayang}. A show starts
 * only in the first 600 ticks of its slot (a screen raised later waits for the next one).
 * <p>
 * <b>"Malam Wayang"</b> ({@code Akibat} {@code wayang <verb>}): {@code curi} the puppet (no shows, the lamp at half, the dalang
 * sits mourning), {@code kembali} puts it back, {@code beri_wayang <naskah>} hands the puppet item to the player,
 * {@code perdana} runs the premiere now at the theatre of the player's place: the player strikes the gong with the rhythm HUD
 * ({@code kota_irama}, kind {@code J_WAYANG}) in three rounds of five beats; two clean rounds are a success (free tickets for
 * ever, flag {@code wayang_tiket_gratis}, advancement, the quest event {@code wayang_perdana}).
 */
public final class TeaterBayang {

	/** Show start hours and length in ticks (one tick = 1/1000 of an hour). */
	static final int[] JAM = {19, 21};
	static final int LAMA = 1800;
	/** A show may start this late after its hour. */
	static final int TELAT_MAKS = 600;
	public static final long HARGA_TIKET = 2L;
	/** The flag of a player who beat the gong at the premiere. */
	public static final String BENDERA_GRATIS = "wayang_tiket_gratis";
	static final String L = "kultivator.kota42.acara.wayang.";
	private static final String[] ID = LayarBayangEntity.NASKAH_ID;

	/** A theatre of a place: where its screen stands and which way it faces (the audience side). */
	private record Tempat(Vec3 pos, float yaw) {
	}

	private static final class Teater {
		final long situs;
		final Tempat tempat;
		@Nullable LayarBayangEntity layar;
		@Nullable KultivatorNpc dalang;
		/** Game tick the running show began, or -1. */
		long mulai = -1L;
		long kunci = -1L;
		int naskah;
		int baris = -1;
		@Nullable UUID perdana;
		boolean paksa;

		Teater(long situs, Tempat tempat) {
			this.situs = situs;
			this.tempat = tempat;
		}
	}

	private static final Map<Long, Teater> TEATER = new HashMap<>();
	/** Theatres forced by tests: place -> position. */
	private static final Map<Long, Tempat> PAKSA = new HashMap<>();
	/** Viewers of the show of a place: player -> {show start tick, samples, near}. */
	private static final Map<UUID, long[]> TONTON = new HashMap<>();
	/** Premiere rhythm state: player -> {pattern, struck, misses, round, rounds, tpb, start low, rounds cleaned}. */
	private static final Map<UUID, int[]> IRAMA = new HashMap<>();
	/** Places known to have a theatre (by plan) or not. */
	private static final Map<Long, Boolean> ADA = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(TeaterBayang::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (entity instanceof LayarBayangEntity layar) {
				tiket(sp, layar);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		KalenderKota.daftarSumber(TeaterBayang::kalender);
		Akibat.daftar("wayang", (player, args) -> efek(player, args));
	}

	private static void lupakan() {
		for (Teater t : TEATER.values()) {
			bubar(t);
		}
		TEATER.clear();
		PAKSA.clear();
		TONTON.clear();
		IRAMA.clear();
		ADA.clear();
	}

	// ================================================================== places and clock

	private static boolean wayang(Titik t) {
		return "wayang".equals(t.data().get("acara"));
	}

	/** True when the plan of {@code situs} has a theatre titik (cached). */
	private static boolean adaTeater(MinecraftServer server, long situs) {
		if (PAKSA.containsKey(situs)) {
			return true;
		}
		return ADA.computeIfAbsent(situs, s -> {
			for (Titik t : TempatApi.titik(server.overworld(), s, "panggung")) {
				if (wayang(t)) {
					return true;
				}
			}
			return false;
		});
	}

	private static @Nullable Tempat cariTempat(TempatHuni huni) {
		Tempat paksa = PAKSA.get(huni.situsId);
		if (paksa != null) {
			return paksa;
		}
		for (Titik t : huni.titik("panggung")) {
			if (wayang(t)) {
				return new Tempat(Vec3.atBottomCenterOf(t.pos()), t.hadap() == null ? 0.0F : t.hadap().toYRot());
			}
		}
		return null;
	}

	/** The index of the slot (show hour) running at clock hour {@code jam} and ticks since it began, or -1. */
	private static int slot(double jam, long[] t) {
		for (int i = 0; i < JAM.length; i++) {
			long dt = (long) ((jam - JAM[i]) * 1000.0D);
			if (dt >= 0L && dt < LAMA) {
				t[0] = dt;
				return i;
			}
		}
		return -1;
	}

	private static boolean dicuri(MinecraftServer server, long situs) {
		return KotaAcaraData.get(server).situs(KotaAcaraData.MALAM, situs).getBooleanOr("wayang_dicuri", false);
	}

	/** True while a shadow play runs in place {@code situs} (by the clock, or a forced or premiere show). */
	static boolean sedangPentas(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.acara().distrikMalam) {
			return false;
		}
		Teater te = TEATER.get(situs);
		if (te != null && te.mulai >= 0L) {
			return true;
		}
		if (!adaTeater(server, situs) || dicuri(server, situs)) {
			return false;
		}
		return slot(KalenderKota.jamDesimal(server), new long[1]) >= 0;
	}

	/** Which script the show of day {@code hari}, slot {@code slot} at {@code situs} plays. */
	public static int naskahHari(long hari, int slot, long situs) {
		return (int) Math.floorMod(hari * 2L + slot + (situs >>> 3), (long) LayarBayangEntity.NASKAH_JUMLAH);
	}

	// ================================================================== calendar

	private static void kalender(MinecraftServer server, long situs, long dari, long sampai, List<KalenderKota.Entri> out) {
		if (!KotaConfig.acara().distrikMalam || !adaTeater(server, situs)) {
			return;
		}
		for (long d = dari; d <= sampai; d++) {
			for (int i = 0; i < JAM.length; i++) {
				Component nama = Component.translatable(L + "kalender", Component.translatable(L + "judul." + ID[naskahHari(d, i, situs)]));
				out.add(new KalenderKota.Entri("wy:" + d + ":" + i, KotaLembar.Buku.AC_WAYANG, nama, d, JAM[i], 0, 2, null));
			}
		}
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		long tc = server.getTickCount();
		if (tc % 5L != 3L) {
			return;
		}
		KotaConfig.Acara c = KotaConfig.acara();
		ServerLevel level = server.overworld();
		if (!c.aktif || !c.distrikMalam && PAKSA.isEmpty()) {
			if (!TEATER.isEmpty()) {
				lupakan();
			}
			return;
		}
		long now = level.getGameTime();
		double jam = KalenderKota.jamDesimal(server);
		long hari = KalenderKota.hari(server);
		boolean malam = KalenderKota.antara(jam, 17.5D, 23.5D);
		Set<Long> hidup = new HashSet<>();
		for (TempatHuni huni : KalenderKota.aktif(level)) {
			Tempat tempat = cariTempat(huni);
			if (tempat == null) {
				continue;
			}
			boolean paksa = PAKSA.containsKey(huni.situsId);
			Teater te = TEATER.get(huni.situsId);
			BlockPos at = BlockPos.containing(tempat.pos());
			boolean dekat = (malam || paksa || te != null && te.paksa) && KalenderKota.adaPemain(level, at, 64.0D);
			if (!dekat) {
				if (te != null) {
					selesai(level, te, false);
					bubar(te);
					TEATER.remove(huni.situsId);
				}
				continue;
			}
			if (te == null) {
				te = new Teater(huni.situsId, tempat);
				TEATER.put(huni.situsId, te);
			}
			if (!siapkan(level, huni, te)) {
				continue;
			}
			hidup.add(huni.situsId);
			jalankan(level, huni, te, now, jam, hari);
		}
		Iterator<Map.Entry<Long, Teater>> it = TEATER.entrySet().iterator();
		while (it.hasNext()) {
			Teater te = it.next().getValue();
			if (!hidup.contains(te.situs) && !PAKSA.containsKey(te.situs)) {
				bubar(te);
				it.remove();
			}
		}
	}

	private static boolean siapkan(ServerLevel level, TempatHuni huni, Teater te) {
		if (te.layar == null || te.layar.isRemoved()) {
			if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 3 > DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
				return false;
			}
			LayarBayangEntity l = KotaAcaraEntities.LAYAR_BAYANG.create(level, EntitySpawnReason.EVENT);
			if (l == null) {
				return false;
			}
			l.ikat(huni.situsId, BlockPos.containing(te.tempat.pos()).asLong());
			l.pindah(te.tempat.pos(), te.tempat.yaw());
			if (!level.addFreshEntity(l)) {
				return false;
			}
			DuniaBudget.track(KotaAcaraEntities.ANGGARAN, l);
			te.layar = l;
			te.mulai = -1L;
			te.kunci = -1L;
		}
		if (te.dalang == null || te.dalang.isRemoved()) {
			Vec3 belakang = belakang(te, 1.2D);
			te.dalang = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.PENDONGENG, BlockPos.containing(belakang), te.tempat.yaw() + 180.0F,
					(int) (huni.situsId * 29L + 4242L));
			if (te.dalang != null) {
				te.dalang.memory().putBoolean("hold", true);
				te.dalang.teleportTo(belakang.x, belakang.y, belakang.z);
				te.dalang.setYRot(te.tempat.yaw() + 180.0F);
				te.dalang.setYHeadRot(te.tempat.yaw() + 180.0F);
			}
		}
		return true;
	}

	private static Vec3 belakang(Teater te, double jarak) {
		double r = Math.toRadians(te.tempat.yaw());
		return te.tempat.pos().add(Math.sin(r) * jarak, 0.0D, -Math.cos(r) * jarak);
	}

	private static Vec3 depan(Teater te, double jarak) {
		double r = Math.toRadians(te.tempat.yaw());
		return te.tempat.pos().add(-Math.sin(r) * jarak, 0.0D, Math.cos(r) * jarak);
	}

	private static void bubar(Teater te) {
		if (te.layar != null && !te.layar.isRemoved()) {
			te.layar.discard();
		}
		te.layar = null;
		if (te.dalang != null && !te.dalang.isRemoved()) {
			te.dalang.vanish();
		}
		te.dalang = null;
	}

	// ================================================================== a show

	private static void jalankan(ServerLevel level, TempatHuni huni, Teater te, long now, double jam, long hari) {
		MinecraftServer server = level.getServer();
		LayarBayangEntity layar = te.layar;
		KultivatorNpc dalang = te.dalang;
		if (layar == null) {
			return;
		}
		boolean dicuri = dicuri(server, huni.situsId);
		if (dicuri) {
			if (te.mulai >= 0L) {
				selesai(level, te, false);
			}
			if (layar.keadaan() != LayarBayangEntity.KEADAAN_JEDA) {
				layar.jeda();
			}
			gerakDalang(dalang, GerakId.MENANGIS, 0);
			if (now % 600L == 0L && dalang != null) {
				dalang.say(Component.translatable(L + "dicuri"), 120);
			}
			return;
		}
		long[] t = new long[1];
		int slot = slot(jam, t);
		long kunci = slot < 0 ? -1L : hari * 4L + slot;
		if (te.mulai < 0L && !te.paksa && slot >= 0 && kunci != te.kunci) {
			te.kunci = kunci;
			if (t[0] <= TELAT_MAKS) {
				mulaiPentas(level, huni, te, naskahHari(hari, slot, huni.situsId), now, null);
			}
		}
		if (te.mulai < 0L) {
			if (layar.keadaan() != LayarBayangEntity.KEADAAN_GELAP) {
				layar.padam();
			}
			gerakDalang(dalang, GerakId.DUDUK_LANTAI, 0);
			return;
		}
		long te0 = now - te.mulai;
		if (te0 >= LAMA) {
			selesai(level, te, true);
			return;
		}
		BlockPos at = BlockPos.containing(te.tempat.pos());
		// the dalang: puppets on sticks while the acts run, hands folded in the epilogue
		gerakDalang(dalang, te0 < 1200L ? KotaGerakId.DALANG : GerakId.SIKAP_TANGAN_DEPAN, te0 < 1200L ? KotaGerakId.PROP_WAYANG_TONGKAT : 0);
		int baris = te0 < 60L ? 0 : te0 < 1200L ? 1 + (int) ((te0 - 60L) / 300L) : te0 < 1500L ? 5 : te0 < 1700L ? 6 : 7;
		if (baris != te.baris) {
			te.baris = baris;
			bariskan(level, te, baris);
		}
		if (te0 >= 1200L && layar.keadaan() == LayarBayangEntity.KEADAAN_PENTAS) {
			layar.jeda();
		}
		Lagu.mainkan(level, at, Lagu.WAYANG, te0 - 5L, te0, 40.0D);
		if (te.perdana != null) {
			ronde(level, te, te0);
		}
		if (server.getTickCount() % 20L == 3L) {
			catat(level, te);
		}
	}

	private static void gerakDalang(@Nullable KultivatorNpc dalang, int klip, int prop) {
		if (dalang != null && !dalang.isRemoved() && SosokApi.gerakSekarang(dalang) != klip) {
			SosokApi.gerak(dalang, klip, 0, 0, prop, 0, 0);
		}
	}

	/** Starts a show now ({@code perdana} = the premiere of this player's gong). */
	private static void mulaiPentas(ServerLevel level, TempatHuni huni, Teater te, int naskah, long now, @Nullable ServerPlayer perdana) {
		LayarBayangEntity layar = te.layar;
		if (layar == null) {
			return;
		}
		te.mulai = now;
		te.naskah = naskah;
		te.baris = -1;
		te.perdana = perdana == null ? null : perdana.getUUID();
		layar.pentas(naskah);
		BlockPos at = BlockPos.containing(te.tempat.pos());
		HidupApi.kerumunan(level, BlockPos.containing(depan(te, 4.0D)), LAMA / 20 + 10);
		level.playSound(null, at, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 3.0F, 0.5F);
		for (ServerPlayer p : KalenderKota.penonton(level, at)) {
			Msg.actionBar(p, Component.translatable(L + "mulai", Component.translatable(L + "judul." + ID[naskah])).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void bariskan(ServerLevel level, Teater te, int baris) {
		KultivatorNpc dalang = te.dalang;
		if (dalang == null || dalang.isRemoved()) {
			return;
		}
		String id = ID[te.naskah];
		String kunci = switch (baris) {
			case 0 -> "buka";
			case 5 -> "epi1";
			case 6 -> "epi2";
			case 7 -> "tutup";
			default -> Integer.toString(baris);
		};
		dalang.say(Component.translatable(L + id + "." + kunci), 280);
		BlockPos at = BlockPos.containing(te.tempat.pos());
		// the bubble only reaches 12 blocks: the ticket holders further back get the narration in the chat
		Vec3 pos = te.tempat.pos();
		for (ServerPlayer p : KalenderKota.penonton(level, at)) {
			if (punyaTiket(p) && p.distanceToSqr(pos.x, pos.y, pos.z) > 12.0D * 12.0D) {
				Msg.chat(p, Component.translatable(L + "narasi", Component.translatable(L + id + "." + kunci)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
		level.playSound(null, at, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 2.5F, baris == 4 ? 0.45F : 0.6F);
		if (baris >= 1 && baris <= 4) {
			level.playSound(null, at, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.AMBIENT, 1.5F, 0.9F);
		}
		if (baris == 0 && VoiceEvents.exists("kota42.wayang_buka")) {
			Voice.say(dalang, "kota42.wayang_buka", "");
		}
	}

	/** A show ended (or the screen went away): the lamp goes out; with {@code selesai} the viewers are rewarded. */
	private static void selesai(ServerLevel level, Teater te, boolean selesai) {
		if (te.mulai < 0L) {
			return;
		}
		long mulai = te.mulai;
		te.mulai = -1L;
		te.paksa = false;
		te.baris = -1;
		if (te.layar != null && !te.layar.isRemoved()) {
			te.layar.padam();
		}
		if (te.dalang != null && !te.dalang.isRemoved()) {
			HidupApi.klip(te.dalang, GerakId.HORMAT, 0, 40);
		}
		UUID perdana = te.perdana;
		te.perdana = null;
		Iterator<Map.Entry<UUID, long[]>> it = TONTON.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, long[]> e = it.next();
			if (e.getValue()[0] != mulai) {
				continue;
			}
			it.remove();
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (!selesai || p == null || p.isRemoved() || e.getValue()[1] <= 0L) {
				continue;
			}
			boolean penuh = e.getValue()[2] >= e.getValue()[1] * 8L / 10L;
			if (penuh && e.getValue()[1] >= LAMA / 20L * 8L / 10L) {
				KalenderKota.Berkah.terhibur(p);
				Rombongan.halamanLore(p);
				Advancements.grant(p, "tempat/kota42_wayang");
				Msg.chat(p, Component.translatable(L + "selesai", Component.translatable(L + "judul." + ID[te.naskah])).withStyle(ChatFormatting.GOLD));
				CeritaApi.peristiwa(p, "wayang_tonton", Integer.toString(te.naskah));
			}
		}
		if (perdana != null) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(perdana);
			if (p != null) {
				hasilPerdana(p, te, selesai);
			}
		}
	}

	private static void catat(ServerLevel level, Teater te) {
		Vec3 pos = te.tempat.pos();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(pos.x, pos.y, pos.z) > 40.0D * 40.0D) {
				continue;
			}
			if (!punyaTiket(p)) {
				continue;
			}
			long[] st = TONTON.computeIfAbsent(p.getUUID(), k -> new long[] {te.mulai, 0L, 0L});
			if (st[0] != te.mulai) {
				st[0] = te.mulai;
				st[1] = 0L;
				st[2] = 0L;
			}
			st[1]++;
			if (p.distanceToSqr(pos.x, pos.y, pos.z) <= 16.0D * 16.0D) {
				st[2]++;
			}
		}
	}

	// ================================================================== tickets

	/** True with today's ticket or the free pass of the premiere. */
	private static boolean punyaTiket(ServerPlayer p) {
		return KalenderKota.Berkah.hariIni(p, "tiket_wayang") || CeritaApi.bendera(p, BENDERA_GRATIS);
	}

	private static void tiket(ServerPlayer p, LayarBayangEntity layar) {
		if (CeritaApi.bendera(p, BENDERA_GRATIS)) {
			KalenderKota.Berkah.sekaliSehari(p, "tiket_wayang");
			Msg.actionBar(p, Component.translatable(L + "tiket.gratis").withStyle(ChatFormatting.GOLD));
			return;
		}
		if (KalenderKota.Berkah.hariIni(p, "tiket_wayang")) {
			Msg.actionBar(p, Component.translatable(L + "tiket.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!EkonomiApi.bayar(p, HARGA_TIKET, "tiket_wayang")) {
			Msg.actionBar(p, Component.translatable(L + "tiket.kurang", HARGA_TIKET).withStyle(ChatFormatting.RED));
			return;
		}
		KalenderKota.Berkah.sekaliSehari(p, "tiket_wayang");
		Msg.actionBar(p, Component.translatable(L + "tiket.ok", HARGA_TIKET).withStyle(ChatFormatting.GOLD));
		Teater te = TEATER.get(layar.situsId());
		if (te != null && te.dalang != null && !te.dalang.isRemoved()) {
			HidupApi.tatap(te.dalang, p);
			te.dalang.say(Component.translatable(L + "tiket.sapa"), 80);
		}
	}

	// ================================================================== the premiere (rhythm HUD)

	/** Starts the premiere for {@code player} at the theatre of his place now; the reason it cannot start, or null. */
	public static @Nullable String perdana(ServerPlayer player) {
		long situs = TempatDialog.situs(player);
		Teater te = TEATER.get(situs);
		if (te == null || te.layar == null || te.layar.isRemoved()) {
			return L + "perdana.tak_ada";
		}
		if (te.mulai >= 0L) {
			return L + "perdana.sibuk";
		}
		if (dicuri(player.level().getServer(), situs)) {
			return L + "perdana.dicuri";
		}
		TempatHuni huni = dev.kultivator.dunia.tempat.hidup.Pemanggil.huni(situs);
		if (huni == null) {
			return L + "perdana.tak_ada";
		}
		te.paksa = true;
		mulaiPentas((ServerLevel) player.level(), huni, te, naskahHari(KalenderKota.hari(player.level().getServer()), 1, situs), player.level().getGameTime(), player);
		IRAMA.remove(player.getUUID());
		Msg.chat(player, Component.translatable(L + "perdana.mulai").withStyle(ChatFormatting.YELLOW));
		return null;
	}

	/** The rounds start at 300, 700 and 1100 ticks of the show: five beats of 20 ticks, a new pattern each. */
	private static final int[] RONDE = {300, 700, 1100};
	private static final int[] POLA = {0b00101, 0b01011, 0b10110};

	private static void ronde(ServerLevel level, Teater te, long te0) {
		ServerPlayer p = te.perdana == null ? null : level.getServer().getPlayerList().getPlayer(te.perdana);
		if (p == null) {
			te.perdana = null;
			return;
		}
		for (int i = 0; i < RONDE.length; i++) {
			if (te0 >= RONDE[i] && te0 < RONDE[i] + 5L) {
				int[] st = IRAMA.get(p.getUUID());
				if (st != null && st[3] == i + 1) {
					return;
				}
				int bersih = st == null ? 0 : st[7] + ((st[1] & st[0]) == st[0] && st[2] <= 2 ? 1 : 0);
				int start = (int) (level.getGameTime() & 0x7fffffffL);
				IRAMA.put(p.getUUID(), new int[] {POLA[i], 0, 0, i + 1, RONDE.length, 20, start, bersih});
				KotaPayloads.kirim(p, new KotaPayloads.Irama(KotaLembar.HudIrama.data(KotaLembar.HudIrama.J_WAYANG, 0, POLA[i], 20, i + 1, RONDE.length,
						0, 0, start & 0xFF)));
				return;
			}
		}
		if (te0 >= 1300L && te0 < 1305L) {
			// the last round is closed by the end of the show
			KotaPayloads.kirim(p, new KotaPayloads.Irama(KotaLembar.HudIrama.data(KotaLembar.HudIrama.J_KOSONG)));
		}
	}

	/** The C2S beat of the premiere gong (routed by {@code Ronda.irama}): {@code d[1]} the beat 0-4, {@code d[2]} the offset + 128. */
	static void irama(ServerPlayer player, byte[] d) {
		int[] st = IRAMA.get(player.getUUID());
		if (st == null) {
			return;
		}
		int beat = KotaLembar.HudIrama.baca(d, 1);
		int offset = KotaLembar.HudIrama.baca(d, 2) - 128;
		if (beat < 0 || beat >= KotaLembar.HudIrama.KETUKAN) {
			return;
		}
		if ((st[0] >> beat & 1) != 0 && Math.abs(offset) <= 5) {
			st[1] |= 1 << beat;
		} else {
			st[2]++;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 2.0F, 0.5F);
		KotaPayloads.kirim(player, new KotaPayloads.Irama(KotaLembar.HudIrama.data(KotaLembar.HudIrama.J_WAYANG, beat, st[0], st[5], st[3], st[4], st[1],
				st[2], st[6] & 0xFF)));
	}

	private static void hasilPerdana(ServerPlayer p, Teater te, boolean selesai) {
		int[] st = IRAMA.remove(p.getUUID());
		KotaPayloads.kirim(p, new KotaPayloads.Irama(KotaLembar.HudIrama.data(KotaLembar.HudIrama.J_KOSONG)));
		if (!selesai || st == null) {
			Msg.chat(p, Component.translatable(L + "perdana.gagal").withStyle(ChatFormatting.RED));
			return;
		}
		int bersih = st[7] + ((st[1] & st[0]) == st[0] && st[2] <= 2 ? 1 : 0);
		if (bersih >= 2) {
			CeritaApi.setBendera(p, BENDERA_GRATIS, true);
			Msg.chat(p, Component.translatable(L + "perdana.sukses", bersih).withStyle(ChatFormatting.GOLD));
			Advancements.grant(p, "tempat/kota42_wayang");
			CeritaApi.peristiwa(p, "wayang_perdana", Long.toString(te.situs));
		} else {
			Msg.chat(p, Component.translatable(L + "perdana.kurang", bersih).withStyle(ChatFormatting.RED));
		}
	}

	// ================================================================== story effects

	/** {@code wayang curi | kembali | perdana | beri_wayang <naskah>}. */
	private static void efek(ServerPlayer player, List<String> args) {
		if (args.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		long situs = TempatDialog.situs(player);
		switch (args.getFirst().toLowerCase(Locale.ROOT)) {
			case "curi" -> aturCuri(server, situs, true);
			case "kembali" -> aturCuri(server, situs, false);
			case "perdana" -> {
				String sebab = perdana(player);
				if (sebab != null) {
					Msg.chat(player, Component.translatable(sebab).withStyle(ChatFormatting.GRAY));
				}
			}
			case "beri_wayang" -> {
				int n = 0;
				if (args.size() > 1) {
					for (int i = 0; i < ID.length; i++) {
						if (ID[i].equals(args.get(1))) {
							n = i;
						}
					}
				}
				PeristiwaJalan.beri(player, new ItemStack(KotaAcaraItems.WAYANG.get(n)));
			}
			default -> {
			}
		}
	}

	private static void aturCuri(MinecraftServer server, long situs, boolean curi) {
		if (situs == 0L) {
			return;
		}
		CompoundTag t = KotaAcaraData.get(server).situs(KotaAcaraData.MALAM, situs);
		if (curi) {
			t.putBoolean("wayang_dicuri", true);
		} else {
			t.remove("wayang_dicuri");
		}
		KotaAcaraData.get(server).ubah();
	}

	// ================================================================== test commands (uji kota_hidup acara wayang ...)

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("wayang").executes(TeaterBayang::ujiRingkas)
				.then(Commands.literal("naskah").then(Commands.argument("naskah", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of(ID), b)).executes(TeaterBayang::ujiNaskah)))
				.then(Commands.literal("curi").executes(ctx -> ujiCuri(ctx, true)))
				.then(Commands.literal("kembali").executes(ctx -> ujiCuri(ctx, false)))
				.then(Commands.literal("perdana").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					String sebab = perdana(p);
					if (sebab != null) {
						ctx.getSource().sendFailure(Component.translatable(sebab));
						return 0;
					}
					return 1;
				}))
				.then(Commands.literal("padam").executes(ctx -> {
					PAKSA.clear();
					for (Teater te : TEATER.values()) {
						selesai(ctx.getSource().getServer().overworld(), te, false);
					}
					lupakan();
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.wayang_padam"), false);
					return 1;
				}));
	}

	private static int ujiRingkas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		boolean ada = adaTeater(server, info.situsId());
		Teater te = TEATER.get(info.situsId());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.wayang", ada ? "ya" : "tidak",
				te == null ? "-" : te.mulai < 0L ? "gelap" : ID[te.naskah], sedangPentas(info.situsId()) ? "ya" : "tidak",
				dicuri(server, info.situsId()) ? "ya" : "tidak"), false);
		return ada ? 1 : 0;
	}

	/** Raises a screen three blocks in front of the player (a test theatre) and plays {@code naskah} at once. */
	private static int ujiNaskah(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		String nama = StringArgumentType.getString(ctx, "naskah");
		int n = -1;
		for (int i = 0; i < ID.length; i++) {
			if (ID[i].equals(nama)) {
				n = i;
			}
		}
		if (n < 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.wayang_tak_dikenal", nama));
			return 0;
		}
		float yaw = Mth.wrapDegrees(p.getYRot() + 180.0F);
		Vec3 look = p.getLookAngle();
		Vec3 pos = p.position().add(look.x * 4.0D, 0.0D, look.z * 4.0D);
		if (!PAKSA.containsKey(info.situsId()) && cariTeaterTitikAda(info.situsId())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.wayang_ada"));
		}
		PAKSA.put(info.situsId(), new Tempat(pos, yaw));
		Teater lama = TEATER.remove(info.situsId());
		if (lama != null) {
			bubar(lama);
		}
		ServerLevel level = ctx.getSource().getServer().overworld();
		TempatHuni huni = dev.kultivator.dunia.tempat.hidup.Pemanggil.huni(info.situsId());
		if (huni == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.tidak_aktif"));
			return 0;
		}
		Teater te = new Teater(info.situsId(), new Tempat(pos, yaw));
		te.paksa = true;
		TEATER.put(info.situsId(), te);
		if (!siapkan(level, huni, te)) {
			return 0;
		}
		mulaiPentas(level, huni, te, n, level.getGameTime(), null);
		final int pilih = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.wayang_mulai", Component.translatable(L + "judul." + ID[pilih])), false);
		return 1;
	}

	private static boolean cariTeaterTitikAda(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return false;
		}
		for (Titik t : TempatApi.titik(server.overworld(), situs, "panggung")) {
			if (wayang(t)) {
				return true;
			}
		}
		return false;
	}

	private static int ujiCuri(CommandContext<CommandSourceStack> ctx, boolean curi) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		aturCuri(ctx.getSource().getServer(), info.situsId(), curi);
		ctx.getSource().sendSuccess(() -> Component.translatable(curi ? "kultivator.kota42.acara.uji.wayang_curi" : "kultivator.kota42.acara.uji.wayang_kembali"), false);
		return 1;
	}

	private TeaterBayang() {
	}
}
