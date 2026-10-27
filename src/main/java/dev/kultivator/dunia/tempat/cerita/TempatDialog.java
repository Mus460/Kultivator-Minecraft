package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.bicara.BicaraWarga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The talk with a 38 NPC (DESIGN 38.S.1, screen {@code dialog} of 38.U.3.1, sheet keys {@link LembarKunci.Dialog}). A
 * right-click reaches {@link #interact} from the one hook line in {@code Kota.interact}; the best data dialog for this
 * character or role opens (highest priority whose conditions hold), else the 38 roles get a greeting sheet with a line
 * of {@link Kalimat}, a rumour choice and their services. Choices run their effects ({@link Akibat}), walk to the next
 * node and the screen refreshes itself ({@link TempatLayar} rebuilds through {@link #bangun}).
 *
 * <p>Services: modules owning a service of an NPC (shops, bank, guild board, inn...) add a {@link PenyediaJasa}
 * ({@link #daftarJasa}); the sheet lists them in the "Jasa" tab and op {@code jasa:LAYAR} opens that screen for the
 * same NPC ({@link TempatLayar#bukaDari}). Quests the NPC gave appear in the "Quest" tab.
 *
 * <p>Talking is a quest event: {@code bicara tokoh:<id>} and {@code bicara peran:<role>} fire before the dialog is
 * chosen, and goals {@code antar <item> <tokoh:id>} take the item when its receiver is spoken to.
 */
public final class TempatDialog {

	/** One service shown in the "Jasa" tab. */
	public record JasaEntri(String layar, Component nama, int ikon, @Nullable Component catatan, boolean terkunci, @Nullable Component alasan) {
	}

	/** Lists the services an NPC offers a player (empty when none). */
	@FunctionalInterface
	public interface PenyediaJasa {
		List<JasaEntri> untuk(ServerPlayer player, KultivatorNpc npc);
	}

	/** Pseudo dialog id of the greeting sheet. */
	public static final String SAPA = "sapa";
	private static final String SAPA_MULAI = "mulai";
	private static final String SAPA_KABAR = "kabar";

	/**
	 * The talk of one player: NPC, dialog ({@link #SAPA} for the greeting sheet), node, place, and the resolved text of
	 * nodes whose line is random (greeting, rumour) so refreshes keep it.
	 */
	private record Sesi(int npcId, String dialog, String simpul, long situs, @Nullable Component teksTetap) {
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	private static final List<PenyediaJasa> JASA = new CopyOnWriteArrayList<>();
	private static int kedalaman;

	static void init() {
		TempatLayar.daftar(LembarKunci.Dialog.LAYAR, TempatDialog::aksi, TempatDialog::bangun);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESI.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESI.clear());
	}

	/** Adds a service provider (38EK shops and bank, 38KS civil services, 38DS sect market, 38TK theme services). */
	public static void daftarJasa(PenyediaJasa penyedia) {
		if (penyedia != null) {
			JASA.add(penyedia);
		}
	}

	// ================================================================== context for conditions and effects

	/** The NPC of the player's open talk, or null. */
	public static @Nullable KultivatorNpc npc(ServerPlayer player) {
		Sesi sesi = SESI.get(player.getUUID());
		if (sesi == null) {
			return null;
		}
		Entity e = player.level().getEntity(sesi.npcId);
		return e instanceof KultivatorNpc npc && npc.isAlive() ? npc : null;
	}

	/** "The place" of story conditions: the talk's town, else the 38 place the player stands in, else 0. */
	public static long situs(ServerPlayer player) {
		Sesi sesi = SESI.get(player.getUUID());
		if (sesi != null && sesi.situs != 0L) {
			return sesi.situs;
		}
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
			if (info != null) {
				return info.situsId();
			}
		}
		return 0L;
	}

	/** The capital whose arc story conditions read: the talk's place when it has an arc, else the nearest arc. */
	public static long situsAlur(ServerPlayer player) {
		long here = situs(player);
		if (here != 0L && AlurTakhta.ada(player.level().getServer(), here)) {
			return here;
		}
		return AlurTakhta.terdekat(player);
	}

	// ================================================================== opening

	/** From {@code Kota.interact}: true when a dialog or the greeting sheet opened (the old behaviour is skipped). */
	public static boolean interact(ServerPlayer player, KultivatorNpc npc) {
		if (!CeritaConfig.jalan() || !npc.isAlive()) {
			return false;
		}
		NpcRole role = npc.role();
		boolean warga42 = BicaraWarga.boleh(player, npc); // DESIGN 42.5.1: every civilian talks (also tags a named character)
		String tokoh = Tokoh.dari(npc);
		boolean role38 = warga42 || role.ordinal() >= NpcRole.PANGERAN_KOTA.ordinal();
		if (!role38 && tokoh.isEmpty()) {
			return false;
		}
		if (role.townsfolk() && npc.town() != 0L
				&& KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID()) < Kota.HOSTILE_REPUTATION) {
			return false; // the town's own refusal runs
		}
		Tokoh.lihat(npc);
		HidupApi.tatap(npc, player);
		SESI.put(player.getUUID(), new Sesi(npc.getId(), "", "", npc.town(), null));
		bicaraPeristiwa(player, npc, tokoh, role);
		SumberLayanan.sapa(player, npc, tokoh);
		Dialog dialog = pilih(player, npc, tokoh, role.id());
		if (dialog != null) {
			mulai(player, npc, dialog);
		} else {
			sapa(player, npc, warga42);
		}
		return true;
	}

	/** C38.ST1 {@code dialog}: opens dialog {@code dialogId} of {@code npc} for {@code player}; false when unknown. */
	public static boolean buka(ServerPlayer player, KultivatorNpc npc, String dialogId) {
		Dialog dialog = CeritaReload.dialog(dialogId);
		if (dialog == null || dialog.awal() == null || !CeritaConfig.jalan()) {
			return false;
		}
		SESI.put(player.getUUID(), new Sesi(npc.getId(), "", "", npc.town(), null));
		mulai(player, npc, dialog);
		return true;
	}

	private static void bicaraPeristiwa(ServerPlayer player, KultivatorNpc npc, String tokoh, NpcRole role) {
		if (!tokoh.isEmpty()) {
			antar(player, "tokoh:" + tokoh);
			QuestTracker.peristiwa(player, "bicara", "tokoh:" + tokoh);
		}
		antar(player, "peran:" + role.id());
		QuestTracker.peristiwa(player, "bicara", "peran:" + role.id());
	}

	/** Goals {@code antar <item> <receiver>} of running quests: takes the item and fires the event. */
	private static void antar(ServerPlayer player, String receiver) {
		CeritaData data = CeritaData.get(player.level().getServer());
		for (String questId : data.questIds(player.getUUID())) {
			Quest quest = CeritaReload.quest(questId);
			if (quest == null || !QuestTracker.aktif(player, questId)) {
				continue;
			}
			Quest.Tahap stage = quest.tahap(QuestTracker.simpul(player, questId));
			if (stage == null) {
				continue;
			}
			for (Quest.Tujuan goal : stage.tujuan()) {
				if ("antar".equals(goal.jenis()) && goal.args().size() >= 2 && goal.args().get(1).equals(receiver)) {
					String item = goal.args().get(0);
					if (BarangCerita.ambil(player, item, goal.jumlah())) {
						for (int i = 0; i < goal.jumlah(); i++) {
							QuestTracker.peristiwa(player, "antar", item + "@" + receiver);
						}
					}
				}
			}
		}
	}

	private static @Nullable Dialog pilih(ServerPlayer player, KultivatorNpc npc, String tokoh, String peran) {
		CeritaData data = CeritaData.get(player.level().getServer());
		Dialog best = null;
		for (Dialog d : CeritaReload.dialogs()) {
			if (!d.pembicara(tokoh, peran) || d.awal() == null) {
				continue;
			}
			if (d.sekali() && data.flag(player.getUUID(), d.benderaSekali())) {
				continue;
			}
			if (!Syarat.semua(player, d.syarat())) {
				continue;
			}
			if (best == null || d.prioritas() > best.prioritas()) {
				best = d;
			}
		}
		return best;
	}

	private static void mulai(ServerPlayer player, KultivatorNpc npc, Dialog dialog) {
		DialogNode start = dialog.awal();
		if (start == null) {
			return;
		}
		String before = TempatLayar.layarTerbuka(player);
		masuk(player, npc, dialog, start, true);
		String after = TempatLayar.layarTerbuka(player);
		boolean otherScreen = after != null && !after.equals(before) && !after.equals(LembarKunci.Dialog.LAYAR);
		if (!otherScreen && SESI.containsKey(player.getUUID()) && dialog.id().equals(SESI.get(player.getUUID()).dialog)) {
			CompoundTag sheet = lembar(player, npc);
			if (sheet != null) {
				TempatLayar.buka(player, LembarKunci.Dialog.LAYAR, npc.getId(), sheet);
			}
		}
	}

	private static void sapa(ServerPlayer player, KultivatorNpc npc, boolean warga42) {
		// a 42 resident opens its own talk (BicaraWarga.lembar speaks the engine's first line); the 38 greeting stays for the rest
		Component line = warga42 ? Component.empty() : Kalimat.sapa(player, npc);
		SESI.put(player.getUUID(), new Sesi(npc.getId(), SAPA, SAPA_MULAI, npc.town(), line));
		if (!warga42) {
			Voice.say(npc, npc.voiceKey(npc.getRandom().nextBoolean() ? "greet" : "greet_alt"), null);
			npc.say(line, 80);
			gerak(npc, Kalimat.gestur(npc), 0);
		}
		CompoundTag sheet = lembar(player, npc);
		if (sheet != null) {
			TempatLayar.buka(player, LembarKunci.Dialog.LAYAR, npc.getId(), sheet);
		}
	}

	/** Enters {@code node}: session, gesture, voice, speech bubble, then the node's effects. */
	private static void masuk(ServerPlayer player, KultivatorNpc npc, Dialog dialog, DialogNode node, boolean first) {
		Component fixed = null;
		if (node.kabar()) {
			fixed = KabarTempat.pilih(player, npc.town(), npc.role());
			if (fixed == null) {
				fixed = Component.translatable("kultivator.cerita.kabar.sepi");
			}
		}
		SESI.put(player.getUUID(), new Sesi(npc.getId(), dialog.id(), node.id(), npc.town(), fixed));
		gerak(npc, GerakId.id(node.gestur()), PropId.id(node.prop()));
		if (!node.suara().isEmpty() && VoiceEvents.exists(node.suara())) {
			Voice.say(npc, node.suara(), null);
		} else if (first) {
			Voice.say(npc, npc.voiceKey("greet"), null);
		}
		npc.say(teksNode(player, npc.town(), node, fixed), 100);
		if (!node.akibat().isEmpty() && kedalaman < 6) {
			kedalaman++;
			try {
				Akibat.semua(player, node.akibat());
			} finally {
				kedalaman--;
			}
		}
	}

	private static void gerak(KultivatorNpc npc, int klip, int prop) {
		if (klip > 0) {
			HidupApi.klip(npc, klip, prop, Math.max(40, GerakId.durasiSekali(klip, 0)));
		}
	}

	private static Component teksNode(ServerPlayer player, long situs, DialogNode node, @Nullable Component fixed) {
		if (fixed != null) {
			return fixed;
		}
		return Component.translatable(node.teks(), Isian.resolve(player, situs, node.isian()));
	}

	// ================================================================== sheet

	/** The {@link TempatLayar.Pembangun} of the dialog screen. */
	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		Sesi sesi = SESI.get(player.getUUID());
		Entity entity = player.level().getEntity(npcId);
		if (!(entity instanceof KultivatorNpc npc) || !npc.isAlive()) {
			return null;
		}
		if (sesi == null || sesi.npcId != npcId || sesi.dialog.isEmpty()) {
			return null; // talks start with a right-click (interact), never from a bare "lihat"
		}
		return lembar(player, npc);
	}

	private static @Nullable CompoundTag lembar(ServerPlayer player, KultivatorNpc npc) {
		Sesi sesi = SESI.get(player.getUUID());
		if (sesi == null || sesi.npcId != npc.getId()) {
			return null;
		}
		var server = player.level().getServer();
		CompoundTag tag = new CompoundTag();
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_KERTAS);
		String tokoh = Tokoh.dari(npc);
		CompoundTag card = new CompoundTag();
		LembarKunci.teks(card, LembarKunci.NAMA, tokoh.isEmpty() ? KotaNames.person(npc.look(), npc.role()) : Tokoh.nama(server, tokoh, npc.town()));
		LembarKunci.teks(card, LembarKunci.GELAR, tokoh.isEmpty() ? Component.translatable(npc.role().titleKey()) : Tokoh.gelar(tokoh));
		card.putString(LembarKunci.PERAN, npc.role().name());
		card.putInt(LembarKunci.POTRET, npc.getId());
		if (!tokoh.isEmpty()) {
			card.putString(LembarKunci.TOKOH_ID, tokoh);
		}
		if (npc.town() != 0L && PerbaikanApi.takut(npc.town()) >= 60) {
			card.putBoolean(LembarKunci.TAKUT, true);
		}
		tag.put(LembarKunci.NPC, card);
		if (npc.town() != 0L) {
			tag.putInt(LembarKunci.NAMA_BAIK, KotaData.get(server).reputation(npc.town(), player.getUUID()));
		}
		ListTag choices = new ListTag();
		if (SAPA.equals(sesi.dialog)) {
			tag.putString(LembarKunci.Dialog.DIALOG, SAPA);
			tag.putString(LembarKunci.Dialog.SIMPUL, sesi.simpul);
			LembarKunci.teks(tag, LembarKunci.TEKS, sesi.teksTetap == null ? Component.empty() : sesi.teksTetap);
			if (SAPA_MULAI.equals(sesi.simpul)) {
				choices.add(pilihan(1, Component.translatable("kultivator.cerita.sapa.tanya_kabar"), null));
			}
			choices.add(pilihan(SAPA_MULAI.equals(sesi.simpul) ? 2 : 1, Component.translatable("kultivator.cerita.sapa.pamit"), null));
		} else {
			Dialog dialog = CeritaReload.dialog(sesi.dialog);
			DialogNode node = dialog == null ? null : dialog.node(sesi.simpul);
			if (node == null) {
				return null;
			}
			tag.putString(LembarKunci.Dialog.DIALOG, dialog.id());
			tag.putString(LembarKunci.Dialog.SIMPUL, node.id());
			int clip = GerakId.id(node.gestur());
			if (clip > 0) {
				tag.putInt(LembarKunci.Dialog.GESTUR, clip);
			}
			LembarKunci.teks(tag, LembarKunci.TEKS, teksNode(player, npc.town(), node, sesi.teksTetap));
			for (DialogNode.Pilihan c : node.pilihan()) {
				String fail = Syarat.gagal(player, c.syarat());
				if (fail != null && !c.tampil()) {
					continue;
				}
				Component reason = fail == null ? null : Component.translatable(c.alasan().isEmpty() ? "kultivator.cerita.pilihan.terkunci" : c.alasan());
				choices.add(pilihan(c.no(), Component.translatable(c.teks(), Isian.resolve(player, npc.town(), List.of())), reason));
			}
			if (node.pilihan().isEmpty()) {
				tag.putBoolean(LembarKunci.Dialog.AKHIR, true);
			}
		}
		tag.put(LembarKunci.Dialog.PILIHAN, choices);
		ListTag services = new ListTag();
		for (PenyediaJasa provider : JASA) {
			try {
				for (JasaEntri e : provider.untuk(player, npc)) {
					if (services.size() >= LembarKunci.DAFTAR_MAKS) {
						break;
					}
					CompoundTag s = new CompoundTag();
					s.putString(LembarKunci.ID, e.layar());
					LembarKunci.teks(s, LembarKunci.NAMA, e.nama());
					s.putInt(LembarKunci.IKON, e.ikon());
					if (e.catatan() != null) {
						LembarKunci.teks(s, LembarKunci.CATATAN, e.catatan());
					}
					if (e.terkunci()) {
						s.putBoolean(LembarKunci.TERKUNCI, true);
						if (e.alasan() != null) {
							LembarKunci.teks(s, LembarKunci.ALASAN, e.alasan());
						}
					}
					services.add(s);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("A dialog service provider failed", exception);
			}
		}
		if (!services.isEmpty()) {
			tag.put(LembarKunci.Dialog.JASA, services);
		}
		ListTag quests = questNpc(player, npc, tokoh);
		if (!quests.isEmpty()) {
			tag.put(LembarKunci.Dialog.QUEST, quests);
		}
		CompoundTag talk42 = SAPA.equals(sesi.dialog) ? BicaraWarga.lembar(player, npc) : null;
		if (talk42 != null) {
			tag.merge(talk42); // the 42 tablets over the greeting sheet (keeps the 38 services and quests)
		}
		return tag;
	}

	private static CompoundTag pilihan(int no, Component text, @Nullable Component reason) {
		CompoundTag c = new CompoundTag();
		c.putInt(LembarKunci.NO, no);
		LembarKunci.teks(c, LembarKunci.TEKS, text);
		if (reason != null) {
			c.putBoolean(LembarKunci.TERKUNCI, true);
			LembarKunci.teks(c, LembarKunci.ALASAN, reason);
		}
		return c;
	}

	private static ListTag questNpc(ServerPlayer player, KultivatorNpc npc, String tokoh) {
		ListTag list = new ListTag();
		CeritaData data = CeritaData.get(player.level().getServer());
		long day = Kota.day(player.level().getServer());
		for (String questId : data.questIds(player.getUUID())) {
			Quest quest = CeritaReload.quest(questId);
			if (quest == null || !QuestTracker.aktif(player, questId)) {
				continue;
			}
			boolean mine = !tokoh.isEmpty() && quest.pemberi().equals("tokoh:" + tokoh) || quest.pemberi().equals("peran:" + npc.role().id());
			if (!mine) {
				continue;
			}
			CompoundTag q = new CompoundTag();
			q.putString(LembarKunci.ID, questId);
			LembarKunci.teks(q, LembarKunci.NAMA, Component.translatable(quest.kunciNama()));
			Component line = QuestTracker.baris(player, questId);
			if (line != null) {
				LembarKunci.teks(q, LembarKunci.TEKS, line);
			}
			if (data.quest(player.getUUID(), questId).getLongOr("hari", -1L) == day) {
				q.putBoolean(LembarKunci.BARU, true);
			}
			list.add(q);
			if (list.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
		}
		return list;
	}

	// ================================================================== actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		Sesi sesi = SESI.get(player.getUUID());
		Entity entity = player.level().getEntity(npcId);
		if (sesi == null || sesi.npcId != npcId || !(entity instanceof KultivatorNpc npc)) {
			return;
		}
		switch (verb) {
			case LembarKunci.Dialog.OP_PILIH -> pilihNo(player, npc, sesi, LembarKunci.argInt(parts, 1, -1));
			case LembarKunci.Dialog.OP_JASA -> {
				String layar = LembarKunci.arg(parts, 1);
				if (!TempatLayar.bukaDari(player, layar, npcId)) {
					Msg.actionBar(player, Component.translatable("kultivator.cerita.jasa.tutup").withStyle(ChatFormatting.RED));
				}
			}
			case LembarKunci.Dialog.OP_QUEST -> {
				String quest = LembarKunci.arg(parts, 1);
				if (QuestTracker.aktif(player, quest)) {
					CeritaData.get(player.level().getServer()).setTracked(player.getUUID(), quest);
					JurnalCerita.petunjuk(player, quest);
				}
			}
			default -> BicaraWarga.aksi(player, npc, op); // the ops of the 42 tablets; the sheet refreshes after every action
		}
	}

	private static void pilihNo(ServerPlayer player, KultivatorNpc npc, Sesi sesi, int no) {
		if (SAPA.equals(sesi.dialog)) {
			boolean askRumour = SAPA_MULAI.equals(sesi.simpul) && no == 1;
			if (askRumour) {
				Component rumour = KabarTempat.pilih(player, npc.town(), npc.role());
				Component line = rumour != null ? rumour : Component.translatable("kultivator.cerita.kabar.sepi");
				SESI.put(player.getUUID(), new Sesi(sesi.npcId, SAPA, SAPA_KABAR, sesi.situs, line));
				gerak(npc, GerakId.BISIK, 0);
				npc.say(line, 100);
				Voice.say(npc, npc.voiceKey("greet_alt"), null);
			} else {
				akhiri(player);
			}
			return;
		}
		Dialog dialog = CeritaReload.dialog(sesi.dialog);
		DialogNode node = dialog == null ? null : dialog.node(sesi.simpul);
		if (node == null) {
			akhiri(player);
			return;
		}
		DialogNode.Pilihan choice = null;
		for (DialogNode.Pilihan c : node.pilihan()) {
			if (c.no() == no) {
				choice = c;
				break;
			}
		}
		if (choice == null) {
			return;
		}
		if (!Syarat.semua(player, choice.syarat())) {
			Msg.actionBar(player, Component.translatable(choice.alasan().isEmpty() ? "kultivator.cerita.pilihan.terkunci" : choice.alasan())
					.withStyle(ChatFormatting.RED));
			return;
		}
		kedalaman++;
		try {
			Akibat.semua(player, choice.akibat());
		} finally {
			kedalaman--;
		}
		Sesi after = SESI.get(player.getUUID());
		if (after == null || after.npcId != sesi.npcId || !after.dialog.equals(sesi.dialog) || !after.simpul.equals(sesi.simpul)
				|| !LembarKunci.Dialog.LAYAR.equals(TempatLayar.layarTerbuka(player))) {
			return; // an effect closed the talk, opened another screen or another dialog
		}
		if (choice.ke() == null) {
			if (dialog.sekali()) {
				CeritaData.get(player.level().getServer()).setFlag(player.getUUID(), dialog.benderaSekali(), true);
			}
			akhiri(player);
			return;
		}
		DialogNode next = dialog.node(choice.ke());
		if (next == null) {
			Kultivator.LOGGER.warn("Dialog {} node {} leads to unknown node {}", dialog.id(), node.id(), choice.ke());
			akhiri(player);
			return;
		}
		masuk(player, npc, dialog, next, false);
	}

	/** Ends the player's talk and closes the screen (effect {@code tutup}). */
	public static void akhiri(ServerPlayer player) {
		SESI.remove(player.getUUID());
		if (LembarKunci.Dialog.LAYAR.equals(TempatLayar.layarTerbuka(player))) {
			TempatLayar.tutup(player);
		}
	}

	/** Lists a player's talk for tests. */
	static @Nullable String sesiTeks(ServerPlayer player) {
		Sesi s = SESI.get(player.getUUID());
		return s == null ? null : s.dialog + "/" + s.simpul + " npc " + s.npcId;
	}

	static List<String> dialogIds() {
		List<String> out = new ArrayList<>();
		for (Dialog d : CeritaReload.dialogs()) {
			out.add(d.id());
		}
		return out;
	}

	private TempatDialog() {
	}
}
