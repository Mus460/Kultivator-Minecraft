package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The Sumber lines of the capital's services and events (DESIGN 38.B.3 "Baris Sumber per bangunan layanan", keys
 * {@code kultivator.kota_besar.<id>.inspired} in {@code tempat_lore}). A service's line is shown once per player in chat
 * (dark grey, italic, like the cave and road Sumber lines) the first time the player talks to that service's NPC inside a
 * capital; an event's line once per player when the event starts in the capital the player stands in. Remembered as story
 * flags {@code sumber.<id>} ({@link CeritaData}). Text only: no block, no world change.
 */
final class SumberLayanan {

	/** Role -> service id (KETUA_GILDA is resolved by the character, {@link #layanan}). */
	private static final Map<NpcRole, String> PERAN = Map.ofEntries(
			Map.entry(NpcRole.PANGERAN_KOTA, "istana"),
			Map.entry(NpcRole.MENTERI, "istana"),
			Map.entry(NpcRole.PENGAWAL_ISTANA, "istana"),
			Map.entry(NpcRole.KAPTEN_PENJAGA, "gerbang"),
			Map.entry(NpcRole.PENJAGA_GERBANG, "gerbang"),
			Map.entry(NpcRole.HAKIM, "yamen"),
			Map.entry(NpcRole.BANKIR, "rumah_uang"),
			Map.entry(NpcRole.BANDAR_BATU, "judi_batu"),
			Map.entry(NpcRole.PEMOTONG_BATU, "judi_batu"),
			Map.entry(NpcRole.PEDAGANG_BINATANG, "pasar_binatang"),
			Map.entry(NpcRole.PEDAGANG_SENJATA, "toko_senjata"),
			Map.entry(NpcRole.EMPU, "gilda_tempa"),
			Map.entry(NpcRole.ALKEMIS, "gilda_alkemis"),
			Map.entry(NpcRole.PETUGAS_GILDA, "gilda_kultivator"),
			Map.entry(NpcRole.PENGUJI, "gilda_kultivator"),
			Map.entry(NpcRole.PENJAGA_FORMASI, "stasiun_teleport"),
			Map.entry(NpcRole.NAKHODA, "dermaga_langit"),
			Map.entry(NpcRole.PEMILIK_PENGINAPAN, "penginapan"),
			Map.entry(NpcRole.KOKI, "rumah_makan"),
			Map.entry(NpcRole.PUSTAKAWAN, "perpustakaan"),
			Map.entry(NpcRole.PENCERAMAH_FANA, "perpustakaan"),
			Map.entry(NpcRole.RAHIB, "kuil_langit"),
			Map.entry(NpcRole.TABIB, "balai_tabib"),
			Map.entry(NpcRole.PENJAGA_TEKA, "festival"),
			Map.entry(NpcRole.JUARA_SEKTE, "arena"));

	/** Capital event (TempatEvents.ACARA id) -> Sumber id. */
	private static final Map<String, String> ACARA = Map.of(
			"gelombang_binatang", "gelombang_binatang",
			"pertemuan_pil", "pertemuan_pil",
			"lelang_agung", "lelang_agung",
			"turnamen_agung", "turnamen_agung",
			"hari_gerbang", "hari_gerbang",
			"pertemuan_sekte", "pertemuan_sekte",
			"pasar_malam", "pasar_malam",
			"festival_lentera", "festival");

	private static final String JENIS = "kota/kota_besar";

	/** From {@code TempatDialog.interact}: the service's Sumber line, once, when {@code npc} serves in a capital. */
	static void sapa(ServerPlayer player, KultivatorNpc npc, String tokoh) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		String id = layanan(npc.role(), tokoh);
		if (id == null) {
			return;
		}
		TempatInfo info = TempatApi.tempatDi(level, npc.blockPosition());
		if (info == null || !info.jenis().startsWith(JENIS)) {
			return;
		}
		tunjukkan(player, id);
	}

	/** From the ACARA listener: the event's Sumber line, once per player, to every player standing in that capital. */
	static void acara(ServerLevel level, long situs, String acara) {
		String id = ACARA.get(acara);
		if (id == null || !CeritaConfig.jalan()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
			if (info != null && info.situsId() == situs && info.jenis().startsWith(JENIS)) {
				tunjukkan(player, id);
			}
		}
	}

	static @Nullable String layanan(NpcRole role, String tokoh) {
		if (role == NpcRole.KETUA_GILDA) {
			return "dan_chen".equals(tokoh) ? "gilda_alkemis" : "gilda_kultivator";
		}
		return PERAN.get(role);
	}

	private static void tunjukkan(ServerPlayer player, String id) {
		CeritaData data = CeritaData.get(player.level().getServer());
		String flag = "sumber." + id;
		if (data.flag(player.getUUID(), flag)) {
			return;
		}
		data.setFlag(player.getUUID(), flag, true);
		Msg.chat(player, baris(id).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** The line of {@code id}; 38KS's two older keys carry no "Sumber:" word, so they are wrapped. */
	private static MutableComponent baris(String id) {
		return switch (id) {
			case "istana" -> Component.translatable("kultivator.cerita.sumber.awal", Component.translatable("kultivator.kota_besar.istana.inspired"));
			case "festival" -> Component.translatable("kultivator.kota_besar.festival.source");
			default -> Component.translatable("kultivator.kota_besar." + id + ".inspired");
		};
	}

	private SumberLayanan() {
	}
}
