package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.dunia.tempat.tema.bersama.TemaIrama;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Malam Tari Putar (DESIGN 38.T.4.5): the whirling dance on the round stage of the Danau Bulan Sabit.
 * <ul>
 * <li>Festival (calendar event {@code tari_putar}): full-moon nights, 18000-21000, cancelled by a sandstorm. Four
 * {@code PENARI_GURUN} turn in one ring ({@code tari_putar} clip started on one shared tick so they whirl as one), the
 * stage plays a note-block rhythm (drum on the beat, flute and chime melody), brass lanterns light the shore and an
 * impostor audience sits around (both drawn by the client from {@code TemaNet.FESTIVAL acara=tari_putar}).</li>
 * <li>Every evening 19:00-21:00 two dancers perform without the festival.</li>
 * <li>The player may join once per festival (effect {@code oasis_tari} of a dancer's dialog): the shared rhythm game
 * {@code tema_irama}, pattern {@code tari_putar}, 30 s, 40 beats; a score of 70 or more gives the Selendang Penari, Nama
 * Baik +1 and the mural feat "penari" (quest event {@code oasis_tari lulus}). The result arrives through
 * {@link #hasil} (dispatched by 38TK inti's {@code tema_irama} handler for this pattern).</li>
 * </ul>
 */
public final class TariPutar {

	public static final String ACARA = "tari_putar";
	public static final int DARI = 18000;
	public static final int SAMPAI = 21000;
	public static final int DURASI = 600;
	public static final int KETUKAN = 40;
	public static final int LULUS = 70;
	private static final int PENARI_FESTIVAL = 4;
	private static final int PENARI_MALAM = 2;
	/** Melody of the stage (note-block pitch steps 0-24, one per second). */
	private static final int[] LAGU = {6, 8, 10, 13, 10, 8, 6, 3, 6, 8, 10, 15, 13, 10, 8, 6};

	private record Sesi(long situs, long mulai, int[] arah) {
	}

	/** town -> game tick the festival began. */
	private static final Map<Long, Long> FESTIVAL = new HashMap<>();
	private static final Map<UUID, List<KultivatorNpc>> PENARI = new HashMap<>();
	private static final Map<UUID, Long> T0 = new HashMap<>();
	private static final Map<UUID, Sesi> MAIN = new HashMap<>();

	static void init() {
		TemaKalender.daftar(OasisKota.TEMA, ACARA, DARI, SAMPAI, TemaKalender.bulan(MoonPhase.FULL_MOON), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				TariPutar.mulai(level, situsId, dipaksa);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				TariPutar.batal(level, situsId);
			}
		});
	}

	static void reset() {
		FESTIVAL.clear();
		PENARI.clear();
		T0.clear();
		MAIN.clear();
	}

	static void lupakan(UUID id) {
		MAIN.remove(id);
	}

	// ================================================================== festival

	/** Starts the festival in town {@code situs}; false when a storm is on or the switches say no. */
	public static boolean mulai(ServerLevel level, long situs, boolean dipaksa) {
		OasisKota.Hidup kota = OasisKota.cari(level.getServer(), situs);
		long key = kota != null ? kota.situs : situs;
		if (BadaiPasir.aktif(key) || !dipaksa && !TemaConfig.inti().acara) {
			return false;
		}
		FESTIVAL.put(key, level.getGameTime());
		if (kota != null) {
			CompoundTag tag = new CompoundTag();
			tag.putString("acara", ACARA);
			tag.putBoolean("mulai", true);
			tag.putLong("tick", level.getGameTime());
			tag.putLong("akhir", level.getGameTime() + (SAMPAI - DARI));
			TemaNet.kirimSekitar(level, Vec3.atCenterOf(kota.pusat), 160.0D, TemaNet.FESTIVAL, key, tag);
			for (ServerPlayer p : BadaiPasir.pemainDekat(level, kota.pusat, 200)) {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.tari.mulai").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		return true;
	}

	/** Ends (or cancels) the festival of {@code situs}: dancers go back, the client stops drawing. */
	public static void batal(ServerLevel level, long situs) {
		OasisKota.Hidup kota = OasisKota.cari(level.getServer(), situs);
		long key = kota != null ? kota.situs : situs;
		if (FESTIVAL.remove(key) == null) {
			return;
		}
		if (kota != null) {
			CompoundTag tag = new CompoundTag();
			tag.putString("acara", ACARA);
			tag.putBoolean("mulai", false);
			tag.putLong("tick", level.getGameTime());
			TemaNet.kirimSekitar(level, Vec3.atCenterOf(kota.pusat), 160.0D, TemaNet.FESTIVAL, key, tag);
			for (DuniaAnchorEntity a : kota.jangkar(OasisAnchors.PANGGUNG)) {
				bubar(a);
			}
		}
	}

	/** True while the festival runs in {@code situs}. */
	public static boolean festival(long situs) {
		return FESTIVAL.containsKey(situs);
	}

	/** Anchor {@code tema_oasis/panggung} (data {@code r}: ring radius). */
	static void panggungDekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		long situs = OasisAnchors.situsOf(level, anchor);
		boolean fest = FESTIVAL.containsKey(situs) && !BadaiPasir.aktif(situs);
		int jam = OasisKota.jamHari(level);
		boolean malam = jam >= 19 && jam < 21 && !BadaiPasir.aktif(situs);
		int want = fest ? PENARI_FESTIVAL : malam ? PENARI_MALAM : 0;
		List<KultivatorNpc> list = PENARI.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		list.removeIf(n -> n.isRemoved() || !n.isAlive());
		if (want == 0) {
			bubar(anchor);
			return;
		}
		if (list.size() > want) {
			while (list.size() > want) {
				list.removeLast().vanish();
			}
		}
		long t0 = T0.computeIfAbsent(anchor.getUUID(), k -> level.getGameTime());
		int r = Math.max(2, OasisAnchors.angka(anchor, "r", 3));
		for (int i = list.size(); i < want; i++) {
			double a = Math.PI * 2.0D * i / want;
			BlockPos at = BlockPos.containing(anchor.getX() + Math.cos(a) * r, anchor.getY(), anchor.getZ() + Math.sin(a) * r);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, NpcRole.PENARI_GURUN, at, (float) Math.toDegrees(a) + 90.0F);
			if (npc == null) {
				break;
			}
			npc.memory().putBoolean("hold", true);
			npc.memory().putBoolean("penari_panggung", true);
			SosokApi.gerak(npc, GerakId.TARI_PUTAR, 0, 0, PropId.KAIN, 0, 0);
			SosokApi.gerakSinkron(npc, GerakId.TARI_PUTAR, t0);
			list.add(npc);
		}
		// the stage rhythm: drum on the beat, a flute line and a chime every fourth beat
		int beat = (int) ((level.getGameTime() - t0) / 20L);
		BlockPos at = anchor.blockPosition();
		float pitch = (float) Math.pow(2.0D, (LAGU[Math.floorMod(beat, LAGU.length)] - 12) / 12.0D);
		play(level, at, SoundEvents.NOTE_BLOCK_BASEDRUM, 0.9F, 0.8F);
		play(level, at, SoundEvents.NOTE_BLOCK_FLUTE, fest ? 1.0F : 0.6F, pitch);
		if (beat % 4 == 0) {
			play(level, at, SoundEvents.NOTE_BLOCK_CHIME, 0.7F, pitch * 2.0F > 2.0F ? 2.0F : pitch * 2.0F);
		}
		if (beat % 2 == 0) {
			play(level, at, SoundEvents.NOTE_BLOCK_HAT, 0.5F, 1.2F);
		}
		if (fest && level.getRandom().nextInt(20) == 0 && !list.isEmpty()) {
			Voice.say(list.getFirst(), "tema.oasis.dance", null);
		}
	}

	private static void play(ServerLevel level, BlockPos at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at, sound.value(), SoundSource.RECORDS, volume, pitch);
	}

	/** The dancers of a stage go home. */
	static void bubar(DuniaAnchorEntity anchor) {
		List<KultivatorNpc> list = PENARI.remove(anchor.getUUID());
		T0.remove(anchor.getUUID());
		if (list != null) {
			for (KultivatorNpc n : list) {
				if (!n.isRemoved()) {
					SosokApi.henti(n);
					n.vanish();
				}
			}
		}
	}

	// ================================================================== the player's dance

	/** Effect {@code oasis_tari}: opens the rhythm game for {@code player} at the dancer {@code npc}. */
	public static boolean mainkan(ServerPlayer player, @Nullable KultivatorNpc npc) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		OasisKota.Hidup kota = OasisKota.di(player);
		if (kota == null || !FESTIVAL.containsKey(kota.situs)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.tari.bukan_malam").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		MinecraftServer server = level.getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		long festId = FESTIVAL.get(kota.situs);
		if (me.getLongOr("tari_festival", Long.MIN_VALUE) == festId) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.tari.sudah").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		String sibuk = dev.kultivator.util.Sibuk.why(player);
		if (sibuk != null) {
			Msg.actionBar(player, dev.kultivator.util.Sibuk.text(sibuk).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		me.putLong("tari_festival", festId);
		OasisKota.ubah(server);
		// the shared rhythm game of 38TK inti (track, client screen, result check, the tema.tari hold); its callback ends here
		int npcId = npc == null ? -1 : npc.getId();
		if (!TemaIrama.mulai(player, npcId, npc == null ? player.blockPosition() : null, TemaLayar.Irama.POLA_TARI_PUTAR, 1, 1,
				(p, skor, lulus, babak) -> hasil(p, skor))) {
			return false;
		}
		MAIN.put(player.getUUID(), new Sesi(kota.situs, level.getGameTime(), new int[0]));
		if (npc != null) {
			SosokApi.gerak(npc, GerakId.TARI_PUTAR, 0, DURASI);
		}
		return true;
	}

	/**
	 * The rhythm result of pattern {@code tari_putar} (0-100), from 38TK inti's {@code tema_irama} action handler. Checked
	 * against the session: it must come after at least 80 % of the dance and before its end plus 10 s.
	 */
	public static void hasil(ServerPlayer player, int skor) {
		Sesi sesi = MAIN.remove(player.getUUID());
		TemaSibuk.lepas(player, TemaSibuk.TARI);
		if (sesi == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		// timing and range were checked by TemaIrama (the open session, not before the track ends, not after its late window)
		int s = Math.max(0, Math.min(100, skor));
		MinecraftServer server = level.getServer();
		if (s < LULUS) {
			Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.tari.gagal", s).withStyle(ChatFormatting.GRAY));
			QuestTracker.peristiwa(player, "oasis_tari", "gagal");
			return;
		}
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		me.putBoolean("penari", true);
		OasisKota.ubah(server);
		ItemStack selendang = new ItemStack(TemaItems.SELENDANG_PENARI);
		OasisKota.beri(player, selendang);
		OasisKota.Hidup kota = OasisKota.cari(server, sesi.situs());
		if (kota != null && kota.town != 0L) {
			// Nama Baik lives on the 38KS town id, not on the site id
			KotaData.get(server).addReputation(kota.town, player.getUUID(), 1);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.3F);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.tari.lulus", s).withStyle(ChatFormatting.LIGHT_PURPLE));
		QuestTracker.peristiwa(player, "oasis_tari", "lulus");
		dev.kultivator.util.Advancements.grant(player, "tempat/tema_oasis_penari");
	}

	private TariPutar() {
	}
}
