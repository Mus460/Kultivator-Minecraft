package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.data.Cultivators;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.TandingTempat;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The built-in story effects of DESIGN 38.S.1, registered into {@link Akibat} by {@code Cerita.init}. "The NPC" is the
 * one being talked to ({@link TempatDialog#npc}); "the place" is {@link TempatDialog#situs}. No effect changes a block.
 * <pre>
 * bendera ID [off]          set / clear a story flag          nama_baik N        Nama Baik at the place (+/-)
 * beri ITEM [N]             give (cerita:x story item or id)  ambil ITEM [N]     take
 * bayar N / bayar_pita K    pay N / K x (band+1) NB           beri_nb N / beri_nb_pita K   receive NB
 * donasi N                  pay N NB as a gift (quest goal donasi)
 * quest_mulai ID            start a quest here                quest_maju ID      finish the current stage
 * quest_gagal ID            fail a running quest              peristiwa J ID     fire a quest event
 * buka_layanan LAYAR        open a service screen for the NPC mulai_duel         challenge the NPC (Duel Pedang)
 * klip GERAK [PROP] [T]     the NPC plays a clip              suara KEY          the NPC says a voice line
 * adegan ID                 story scene (7b rules)            kabar TEMPLAT [..] spread a rumour at the place
 * alur BAB HASIL            report a capital chapter outcome  sikap_sekte S N    standing with sect S (+/-)
 * panggil TOKOH [musuh]     call a character's body nearby    kawal_mulai MENIT  the NPC follows the player
 * panggil_peran PERAN [musuh] [N]  call N bodies of a role nearby (a band, a witness)
 * jadi_tokoh TOKOH          the NPC becomes that character    npc_pergi          the NPC leaves
 * gelar ID                  a title (flag gelar.ID)           pesan KEY          a chat line
 * efek ID DETIK [AMP]       a potion effect (meals, blessings) tutup             end the talk
 * </pre>
 */
final class AkibatDasar {

	/** Escorts: player -> (escorted body, its reference for goal {@code kawal}). */
	record Kawal(UUID npc, String ref, long sampai) {
	}

	static final Map<UUID, Kawal> KAWAL = new HashMap<>();

	static void daftar() {
		Akibat.daftar("bendera", (p, a) -> {
			if (!a.isEmpty()) {
				CeritaData.get(p.level().getServer()).setFlag(p.getUUID(), a.get(0), a.size() < 2 || !"off".equals(a.get(1)));
			}
		});
		Akibat.daftar("nama_baik", (p, a) -> {
			long town = TempatDialog.situs(p);
			int n = (int) SyaratDasar.num(a, 0, 0);
			if (town != 0L && n != 0) {
				KotaData.get(p.level().getServer()).addReputation(town, p.getUUID(), n);
				Msg.actionBar(p, Component.translatable(n > 0 ? "kultivator.cerita.akibat.nama_baik_naik" : "kultivator.cerita.akibat.nama_baik_turun",
						Math.abs(n)).withStyle(n > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		});
		Akibat.daftar("beri", (p, a) -> {
			if (!a.isEmpty() && BarangCerita.beri(p, a.get(0), (int) SyaratDasar.num(a, 1, 1))) {
				Msg.actionBar(p, Component.translatable("kultivator.cerita.akibat.terima").withStyle(ChatFormatting.GOLD));
			}
		});
		Akibat.daftar("ambil", (p, a) -> {
			if (!a.isEmpty()) {
				BarangCerita.ambil(p, a.get(0), (int) SyaratDasar.num(a, 1, 1));
			}
		});
		Akibat.daftar("bayar", (p, a) -> bayar(p, SyaratDasar.num(a, 0, 0)));
		Akibat.daftar("bayar_pita", (p, a) -> bayar(p, SyaratDasar.num(a, 0, 0) * (Isian.pita(p) + 1L)));
		Akibat.daftar("beri_nb", (p, a) -> terima(p, SyaratDasar.num(a, 0, 0)));
		Akibat.daftar("beri_nb_pita", (p, a) -> terima(p, SyaratDasar.num(a, 0, 0) * (Isian.pita(p) + 1L)));
		Akibat.daftar("donasi", (p, a) -> {
			long n = SyaratDasar.num(a, 0, 0);
			if (n > 0 && bayar(p, n)) {
				QuestTracker.peristiwa(p, "donasi", Long.toString(n));
			}
		});
		Akibat.daftar("quest_mulai", (p, a) -> {
			if (!a.isEmpty()) {
				QuestTracker.mulai(p, a.get(0), TempatDialog.situs(p));
			}
		});
		Akibat.daftar("quest_maju", (p, a) -> {
			if (!a.isEmpty()) {
				QuestTracker.maju(p, a.get(0));
			}
		});
		Akibat.daftar("quest_gagal", (p, a) -> {
			if (!a.isEmpty() && QuestTracker.aktif(p, a.get(0))) {
				QuestTracker.lompat(p, a.get(0), Quest.GAGAL);
			}
		});
		Akibat.daftar("peristiwa", (p, a) -> {
			if (a.size() >= 2) {
				QuestTracker.peristiwa(p, a.get(0), a.get(1));
			}
		});
		Akibat.daftar("buka_layanan", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (!a.isEmpty() && !TempatLayar.bukaDari(p, a.get(0), npc == null ? -1 : npc.getId())) {
				Msg.actionBar(p, Component.translatable("kultivator.cerita.jasa.tutup").withStyle(ChatFormatting.RED));
			}
		});
		Akibat.daftar("mulai_duel", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (npc == null) {
				return;
			}
			TempatDialog.akhiri(p);
			Component refusal;
			if (DuelistRegistry.challengeable(npc)) {
				int gap = Cultivators.get(p).realm().ordinal() - npc.realm();
				refusal = DuelChallenges.challenge(p, npc, null, DuelRuleSet.suggested(gap));
			} else {
				// 38 roles have no town duel profile (KotaDuel: wanderers, rivals, guards, immortals only): the story bout
				// borrows a profile for the fight (TandingTempat), and the quest goal "duel" still counts on the duel's end
				DuelStyle style = gayaDuel(npc.role(), a.isEmpty() ? "" : a.get(0));
				boolean intro = npc.role() == NpcRole.TETUA_IBLIS || a.contains("intro");
				refusal = TandingTempat.mulai(p, npc, style, warnaDuel(style, npc.role()), Items.IRON_SWORD, intro, (pl, body, menang, kalah) -> {
					if (menang) {
						body.say(Component.translatable("kultivator.cerita.duel.kalah." + logatDuel(body.role()) + "." + body.getRandom().nextInt(3)), 100);
					} else if (kalah) {
						body.say(Component.translatable("kultivator.cerita.duel.menang." + logatDuel(body.role()) + "." + body.getRandom().nextInt(3)), 100);
					}
				});
			}
			if (refusal != null) {
				Msg.chat(p, refusal.copy().withStyle(ChatFormatting.RED));
			}
		});
		Akibat.daftar("klip", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			int clip = a.isEmpty() ? 0 : GerakId.id(a.get(0));
			if (npc != null && clip > 0) {
				int prop = a.size() > 1 ? PropId.id(a.get(1)) : 0;
				HidupApi.klip(npc, clip, prop, (int) SyaratDasar.num(a, 2, Math.max(40, GerakId.durasiSekali(clip, 0))));
			}
		});
		Akibat.daftar("suara", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (!a.isEmpty() && VoiceEvents.exists(a.get(0))) {
				if (npc != null) {
					Voice.say(npc, a.get(0), null);
				} else {
					Voice.say(p, a.get(0), null);
				}
			}
		});
		Akibat.daftar("adegan", (p, a) -> {
			if (!a.isEmpty()) {
				KultivatorNpc npc = TempatDialog.npc(p);
				AdeganPayload.kirim(p, a.get(0), npc, p.position(), (int) SyaratDasar.num(a, 1, 0));
			}
		});
		Akibat.daftar("kabar", (p, a) -> {
			if (!a.isEmpty() && p.level() instanceof ServerLevel level) {
				// the word "pemain" is the spreading player's name, fixed now (at telling time it would be the listener's)
				Object[] args = new Object[a.size() - 1];
				for (int i = 1; i < a.size(); i++) {
					args[i - 1] = "pemain".equals(a.get(i)) ? p.getName().getString() : a.get(i);
				}
				KabarTempat.kabar(level, TempatDialog.situs(p), a.get(0), args);
			}
		});
		Akibat.daftar("alur", (p, a) -> {
			if (a.size() >= 2) {
				AlurTakhta.lapor(p, (int) SyaratDasar.num(a, 0, 0), a.get(1));
			}
		});
		Akibat.daftar("sikap_sekte", (p, a) -> {
			String sect = SyaratDasar.sekte(p, a.isEmpty() ? "terdekat" : a.get(0));
			int n = (int) SyaratDasar.num(a, 1, 0);
			if (sect != null && n != 0) {
				SekteApi.diplomasi().adjust(p.getUUID(), sect, n, "cerita.dialog");
			}
		});
		Akibat.daftar("panggil", (p, a) -> {
			if (a.isEmpty() || !(p.level() instanceof ServerLevel level)) {
				return;
			}
			Tokoh.Data data = Tokoh.byId(a.get(0));
			if (data == null) {
				return;
			}
			double angle = p.getYRot() * Math.PI / 180.0D;
			int x = (int) Math.floor(p.getX() - Math.sin(angle) * 5.0D);
			int z = (int) Math.floor(p.getZ() + Math.cos(angle) * 5.0D);
			BlockPos ground = TempatApi.groundSpot(level, x, z, p.blockPosition().getY());
			KultivatorNpc body = HidupApi.tubuh(level, TempatDialog.situs(p), musuh(a) ? dev.kultivator.dunia.kota.NpcRole.TETUA_IBLIS : data.peran(),
					ground == null ? p.blockPosition() : ground, p.getYRot() + 180.0F);
			if (body == null) {
				return;
			}
			Tokoh.tandai(body, data.id());
			if (musuh(a)) {
				body.memory().putString("alur", "musuh");
				body.markHostile(p);
				body.setTarget(p);
			}
		});
		Akibat.daftar("panggil_peran", (p, a) -> {
			if (a.isEmpty() || !(p.level() instanceof ServerLevel level)) {
				return;
			}
			dev.kultivator.dunia.kota.NpcRole role = null;
			for (dev.kultivator.dunia.kota.NpcRole r : dev.kultivator.dunia.kota.NpcRole.values()) {
				if (r.id().equals(a.get(0))) {
					role = r;
				}
			}
			if (role == null) {
				return;
			}
			int count = (int) Math.max(1, Math.min(4, SyaratDasar.num(a, 2, 1)));
			for (int i = 0; i < count; i++) {
				double angle = p.getYRot() * Math.PI / 180.0D + (i - (count - 1) / 2.0D) * 0.6D;
				int x = (int) Math.floor(p.getX() - Math.sin(angle) * 7.0D);
				int z = (int) Math.floor(p.getZ() + Math.cos(angle) * 7.0D);
				BlockPos ground = TempatApi.groundSpot(level, x, z, p.blockPosition().getY());
				KultivatorNpc body = HidupApi.tubuh(level, TempatDialog.situs(p), role, ground == null ? p.blockPosition() : ground, p.getYRot() + 180.0F);
				if (body != null && musuh(a)) {
					body.memory().putString("alur", "musuh");
					body.markHostile(p);
					body.setTarget(p);
				}
			}
		});
		Akibat.daftar("kawal_mulai", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (npc == null) {
				return;
			}
			long until = p.level().getGameTime() + SyaratDasar.num(a, 0, 20) * 1200L;
			npc.setCompanion(p.getUUID(), until);
			npc.memory().putBoolean("hold", false);
			String tokoh = Tokoh.dari(npc);
			KAWAL.put(p.getUUID(), new Kawal(npc.getUUID(), tokoh.isEmpty() ? "peran:" + npc.role().id() : "tokoh:" + tokoh, until));
		});
		Akibat.daftar("jadi_tokoh", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			if (npc != null && !a.isEmpty()) {
				Tokoh.tandai(npc, a.get(0));
			}
		});
		Akibat.daftar("npc_pergi", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			TempatDialog.akhiri(p);
			if (npc != null) {
				npc.vanish();
			}
		});
		Akibat.daftar("gelar", (p, a) -> {
			if (!a.isEmpty()) {
				CeritaData.get(p.level().getServer()).setFlag(p.getUUID(), "gelar." + a.get(0), true);
				Msg.chat(p, Component.translatable("kultivator.cerita.akibat.gelar", Component.translatable("kultivator.cerita.gelar." + a.get(0))
						.withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.YELLOW));
			}
		});
		Akibat.daftar("pesan", (p, a) -> {
			if (!a.isEmpty()) {
				Msg.chat(p, Component.translatable(a.get(0), Isian.resolve(p, TempatDialog.situs(p), a.subList(1, a.size())))
						.withStyle(ChatFormatting.GRAY));
			}
		});
		Akibat.daftar("efek", (p, a) -> {
			if (a.isEmpty()) {
				return;
			}
			Identifier id = Identifier.tryParse(a.get(0));
			if (id == null) {
				return;
			}
			BuiltInRegistries.MOB_EFFECT.get(id).ifPresent(effect -> p.addEffect(new MobEffectInstance(effect,
					(int) SyaratDasar.num(a, 1, 60) * 20, (int) SyaratDasar.num(a, 2, 0), false, true, true)));
		});
		Akibat.daftar("tutup", (p, a) -> TempatDialog.akhiri(p));
	}

	private static boolean musuh(List<String> a) {
		return a.size() > 1 && "musuh".equals(a.get(1));
	}

	static boolean bayar(ServerPlayer p, long n) {
		if (n <= 0L) {
			return true;
		}
		if (!NilaiBatu.pay(p, n)) {
			Msg.actionBar(p, Component.translatable("kultivator.cerita.akibat.kurang_uang", NilaiBatu.text(n)).withStyle(ChatFormatting.RED));
			return false;
		}
		Msg.actionBar(p, Component.translatable("kultivator.cerita.akibat.bayar", NilaiBatu.text(n)).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	private static void terima(ServerPlayer p, long n) {
		if (n > 0L) {
			NilaiBatu.give(p, n);
			Msg.actionBar(p, Component.translatable("kultivator.cerita.akibat.terima_nb", NilaiBatu.text(n)).withStyle(ChatFormatting.GOLD));
		}
	}

	private AkibatDasar() {
	}

	/** Fighting style of a story bout: the dialog's argument when given, else by role (38.S: the demon elder tricks). */
	static DuelStyle gayaDuel(NpcRole role, String arg) {
		for (DuelStyle style : DuelStyle.values()) {
			if (style.name().equalsIgnoreCase(arg)) {
				return style;
			}
		}
		return switch (role) {
			case TETUA_IBLIS, MAKELAR_GELAP, PEMBUNUH_BAYARAN, PENEGAK_IBLIS -> DuelStyle.PENIPU;
			case PENGUJI, TUAN_MUDA, MURID_KLAN, MURID_SEKTE_DALAM, MURID_SEKTE_LUAR, GLADIATOR_IBLIS, JAGAL_ARENA, KAPTEN_PENJAGA -> DuelStyle.AGRESIF;
			case EMPU, PANDAI_PUSAKA, PANDAI_DESA, PENGAWAL_ISTANA, PENJAGA_GERBANG, KULI, TUKANG -> DuelStyle.BERTAHAN;
			default -> DuelStyle.SANTO_PEDANG;
		};
	}

	/** Qi colour of a story bout: blood red for the demonic path, else the style's own colour. */
	static int warnaDuel(DuelStyle style, NpcRole role) {
		return switch (role) {
			case TETUA_IBLIS, PENEGAK_IBLIS, PEMBUNUH_BAYARAN, GLADIATOR_IBLIS, JAGAL_ARENA -> 0x8A1020;
			default -> style.colorRgb;
		};
	}

	/** Which closing lines a body says after a story bout ({@code kultivator.cerita.duel.<menang|kalah>.<logat>}). */
	static String logatDuel(NpcRole role) {
		return switch (role) {
			case TETUA_IBLIS, PENEGAK_IBLIS, PEMBUNUH_BAYARAN, GLADIATOR_IBLIS, JAGAL_ARENA, MAKELAR_GELAP -> "iblis";
			case PENGUJI, TUAN_MUDA, MURID_KLAN, MURID_SEKTE_DALAM, MURID_SEKTE_LUAR -> "muda";
			default -> "tua";
		};
	}
}
