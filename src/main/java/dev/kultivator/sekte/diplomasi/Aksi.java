package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.TurnamenService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The master's actions toward an NPC sect (DESIGN 36.2.9, the Diplomasi screen and {@code /kultivasi perguruan
 * diplomasi}): send an envoy, send a gift (60/150/300 PK for +5/+10/+15, once a day; the demonic path is greedy and counts
 * half), propose a non-aggression pact or an alliance ({@link Pakta}), break a pact, demand tribute (hall level at least
 * the sect's tier + 2), challenge to an inter-sect tournament (module T), declare war (standing below −30) and offer
 * peace. {@link #why} tells the screen why an action is closed.
 */
final class Aksi {

	public static final int UTUSAN = 0;
	public static final int HADIAH_KECIL = 1;
	public static final int HADIAH_SEDANG = 2;
	public static final int HADIAH_BESAR = 3;
	public static final int PAKTA = 4;
	public static final int SEKUTU = 5;
	public static final int PUTUS = 6;
	public static final int TUNTUT = 7;
	public static final int TANTANG = 8;
	public static final int PERANG = 9;
	public static final int DAMAI = 10;
	public static final int COUNT = 11;
	public static final String[] IDS = DiplomasiPayloads.ACTIONS;

	static final int[] HADIAH_PK = DiplomasiPayloads.GIFT_PK;
	static final int[] HADIAH_STANDING = {5, 10, 15};
	static final int TUNTUT_JEDA = 7;
	static final int TANTANG_JEDA = 5;
	static final int DAMAI_JEDA = 2;

	static int byId(String id) {
		for (int i = 0; i < IDS.length; i++) {
			if (IDS[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** Why the hall of {@code owner} cannot do {@code action} toward {@code sect} now, or null. */
	static @Nullable Component why(MinecraftServer server, UUID owner, SekteNpc sect, Relasi r, int action) {
		if (Halls.hallOf(owner) == null) {
			return key("aula");
		}
		long today = Diplomasi.today(server);
		return switch (action) {
			case UTUSAN -> Utusan.canSend(server, owner, sect, r, null);
			case HADIAH_KECIL, HADIAH_SEDANG, HADIAH_BESAR -> {
				int cost = HADIAH_PK[action - HADIAH_KECIL];
				if (r.perang) {
					yield key("perang");
				}
				if (r.hadiahDay >= today) {
					yield key("hadiah_hari");
				}
				yield Simulasi.poolOf(server, owner) < cost ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.pk", cost) : null;
			}
			case PAKTA -> Pakta.cannot(server, sect, r, Relasi.PAKTA_DAMAI);
			case SEKUTU -> Pakta.cannot(server, sect, r, Relasi.PAKTA_SEKUTU);
			case PUTUS -> r.pakta == Relasi.PAKTA_TIDAK ? key("tanpa_pakta") : null;
			case TUNTUT -> {
				if (r.perang) {
					yield key("perang");
				}
				if (r.pakta == Relasi.PAKTA_SEKUTU || r.bawahan) {
					yield key("sekutu");
				}
				if (Halls.level(owner) < sect.tier + 2) {
					yield Component.translatable(GeneratorNama.KEY + "alasan_tidak.tingkat", sect.tier + 2);
				}
				yield today < r.tuntutDay + TUNTUT_JEDA ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.jeda", r.tuntutDay + TUNTUT_JEDA - today) : null;
			}
			case TANTANG -> {
				if (r.perang) {
					yield key("perang");
				}
				if (SekteApi.turnamen() == TurnamenService.NONE) {
					yield key("turnamen");
				}
				yield today < r.tantangDay + TANTANG_JEDA ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.jeda", r.tantangDay + TANTANG_JEDA - today) : null;
			}
			case PERANG -> {
				if (!Diplomasi.config().perangAktif) {
					yield key("perang_mati");
				}
				if (r.perang) {
					yield key("sudah");
				}
				if (r.pakta != Relasi.PAKTA_TIDAK) {
					yield key("pakta_dulu");
				}
				yield r.standing >= Standing.PERANG_MAX ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.standing_bawah", Standing.PERANG_MAX) : null;
			}
			case DAMAI -> {
				if (!r.perang) {
					yield key("bukan_perang");
				}
				yield today < r.paktaTolakDay + DAMAI_JEDA ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.jeda", r.paktaTolakDay + DAMAI_JEDA - today) : null;
			}
			default -> key("aula");
		};
	}

	private static Component key(String what) {
		return Component.translatable(GeneratorNama.KEY + "alasan_tidak." + what);
	}

	/** Does {@code action}; the result goes to chat and the screen is refreshed by the caller. */
	static void run(ServerPlayer player, SekteNpc sect, int action) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		Component why = why(server, owner, sect, r, action);
		if (why != null) {
			player.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
			return;
		}
		long today = Diplomasi.today(server);
		switch (action) {
			case UTUSAN -> Utusan.send(player, sect);
			case HADIAH_KECIL, HADIAH_SEDANG, HADIAH_BESAR -> {
				int i = action - HADIAH_KECIL;
				if (!Halls.spendPool(owner, HADIAH_PK[i])) {
					return;
				}
				r.hadiahDay = today;
				int gain = sect.iblis() ? HADIAH_STANDING[i] / 2 : HADIAH_STANDING[i];
				sect.sumber += HADIAH_PK[i] / 10;
				Diplomasi.meet(server, owner, sect);
				Standing.adjust(server, owner, sect, gain, "hadiah_kita");
				Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.hadiah_kirim", HADIAH_PK[i], sect.name(), gain), ChatFormatting.GREEN);
			}
			case PAKTA -> Pakta.propose(player, sect, Relasi.PAKTA_DAMAI);
			case SEKUTU -> Pakta.propose(player, sect, Relasi.PAKTA_SEKUTU);
			case PUTUS -> Pakta.putus(player, sect);
			case TUNTUT -> tuntut(server, owner, sect, r, today);
			case TANTANG -> {
				if (SekteApi.turnamen().hostGuests(player, sect.id)) {
					r.tantangDay = today;
					r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.tantang_kita"));
					Diplomasi.meet(server, owner, sect);
					Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.tantang_terima", sect.name()), ChatFormatting.GOLD);
				} else {
					Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.tantang_gagal"), ChatFormatting.GRAY);
				}
			}
			case PERANG -> Perang.nyatakan(server, owner, sect, true);
			case DAMAI -> {
				int ours = Diplomasi.kekuatanAula(server, owner);
				long days = today - r.perangDay;
				if (sect.strength() < ours || r.perangMenang >= 2 || days >= 12) {
					Perang.damai(server, owner, sect);
				} else {
					r.paktaTolakDay = today;
					r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.damai_tolak"));
					Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.damai_tolak", sect.name()), ChatFormatting.RED);
				}
			}
			default -> {
			}
		}
		data.setDirty();
	}

	/** The hall demands tribute: a weak or dishonourable sect pays ({@code tier × 30} PK, −10), a proud one refuses (−15). */
	private static void tuntut(MinecraftServer server, UUID owner, SekteNpc sect, Relasi r, long today) {
		r.tuntutDay = today;
		int ours = Diplomasi.kekuatanAula(server, owner);
		boolean pays = sect.strength() < ours * 0.8D || sect.kehormatan < 40 && server.overworld().getRandom().nextBoolean();
		if (pays) {
			int pk = sect.tier * 30;
			Halls.addPool(owner, pk);
			sect.sumber -= pk / 5;
			Standing.adjust(server, owner, sect, -10, "upeti_tuntut");
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.upeti_terima", sect.name(), pk), ChatFormatting.GOLD);
			Diplomasi.fire(server, owner, sect, AksiSekte.HADIAH);
		} else {
			Standing.adjust(server, owner, sect, -15, "upeti_tolak_mereka");
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.upeti_tolak", sect.name()), ChatFormatting.RED);
			Relasi now = DiplomasiData.get(server).relasi(owner, sect);
			if (Diplomasi.config().perangAktif && sect.agresi > 60 && now.standing < Standing.PERANG_MAX && now.pakta == Relasi.PAKTA_TIDAK) {
				Perang.nyatakan(server, owner, sect, false);
			}
		}
	}

	private Aksi() {
	}
}
