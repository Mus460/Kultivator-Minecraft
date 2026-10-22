package dev.kultivator.sekte.diplomasi;

import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pacts (DESIGN 36.2.9): a non-aggression pact from standing 20 (no raids, no poaching, no ambushes, the standing never
 * drifts below 20) and an alliance from standing 60 (allied guards in a live raid, their defence in a raid settled as
 * data, a weekly gift of PK, joint tournaments). A sect of the demonic path never allies. The player proposes and the
 * sect weighs it by standing and honour; a refusal closes the door for three days. Breaking a pact costs standing.
 */
final class Pakta {

	/** Days a refused proposal waits. */
	static final int JEDA_TOLAK = 3;

	/** Why the hall cannot propose {@code kind} ({@link Relasi#PAKTA_DAMAI} or {@link Relasi#PAKTA_SEKUTU}); null: it can. */
	static @Nullable Component cannot(MinecraftServer server, SekteNpc sect, Relasi r, int kind) {
		long today = Diplomasi.today(server);
		if (r.perang) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.perang");
		}
		if (r.pakta >= kind) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.sudah");
		}
		if (kind == Relasi.PAKTA_SEKUTU && sect.iblis()) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.iblis");
		}
		int min = kind == Relasi.PAKTA_SEKUTU ? Standing.PAKTA_SEKUTU_MIN : Standing.PAKTA_DAMAI_MIN;
		if (r.standing < min) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.standing", min);
		}
		if (today < r.paktaTolakDay + JEDA_TOLAK) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.jeda", r.paktaTolakDay + JEDA_TOLAK - today);
		}
		return null;
	}

	/** The chance the sect accepts the hall's proposal (0.1-0.95). */
	static double chance(SekteNpc sect, Relasi r, int kind) {
		int min = kind == Relasi.PAKTA_SEKUTU ? Standing.PAKTA_SEKUTU_MIN : Standing.PAKTA_DAMAI_MIN;
		double c = (kind == Relasi.PAKTA_SEKUTU ? 0.5D : 0.55D) + (r.standing - min) / 60.0D + (sect.kehormatan - 50) / 200.0D;
		if (sect.iblis()) {
			c -= 0.15D;
		}
		return Mth.clamp(c, 0.1D, 0.95D);
	}

	/** The player proposes; the sect accepts or refuses at once. */
	static void propose(ServerPlayer player, SekteNpc sect, int kind) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		Component why = cannot(server, sect, r, kind);
		if (why != null) {
			player.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
			return;
		}
		if (server.overworld().getRandom().nextDouble() < chance(sect, r, kind)) {
			if (kind == Relasi.PAKTA_SEKUTU) {
				sekutu(server, owner, sect);
			} else {
				damai(server, owner, sect);
			}
		} else {
			long today = Diplomasi.today(server);
			r.paktaTolakDay = today;
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.pakta_tolak"));
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.pakta_tolak", sect.name(), JEDA_TOLAK), ChatFormatting.GRAY);
			data.setDirty();
		}
	}

	/** A non-aggression pact is concluded. */
	static void damai(MinecraftServer server, UUID owner, SekteNpc sect) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (r.perang || r.pakta >= Relasi.PAKTA_DAMAI) {
			return;
		}
		long today = Diplomasi.today(server);
		r.pakta = Relasi.PAKTA_DAMAI;
		r.standing = Math.max(r.standing, Standing.PAKTA_DAMAI_MIN);
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.pakta_damai"));
		Component line = Component.translatable(GeneratorNama.KEY + "log.pakta_damai", sect.name());
		Diplomasi.log(server, owner, line);
		Diplomasi.tell(server, owner, line, ChatFormatting.GREEN);
		Diplomasi.fire(server, owner, sect, AksiSekte.DAMAI);
		data.setDirty();
	}

	/** An alliance is concluded (scene 181, advancement {@code diplomasi_sekutu}). */
	static void sekutu(MinecraftServer server, UUID owner, SekteNpc sect) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (r.perang || r.pakta == Relasi.PAKTA_SEKUTU || sect.iblis()) {
			return;
		}
		long today = Diplomasi.today(server);
		r.pakta = Relasi.PAKTA_SEKUTU;
		r.standing = Math.max(r.standing, Standing.PAKTA_SEKUTU_MIN);
		r.sekutuHadiahDay = today;
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.sekutu"));
		Component line = Component.translatable(GeneratorNama.KEY + "log.sekutu", sect.name());
		Diplomasi.log(server, owner, line.copy().withStyle(ChatFormatting.GOLD));
		Diplomasi.tell(server, owner, line, ChatFormatting.GOLD);
		Diplomasi.fire(server, owner, sect, AksiSekte.SEKUTU);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Advancements.grant(player, SekteIds.ADVANCEMENT_FOLDER + "diplomasi_sekutu");
		}
		Perang.scene(server, owner, Perang.BEAT_SEKUTU, sect, null);
		data.setDirty();
	}

	/** The hall breaks its pact: −30 for an alliance, −15 for a non-aggression pact. */
	static void putus(ServerPlayer player, SekteNpc sect) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (r.pakta == Relasi.PAKTA_TIDAK) {
			return;
		}
		boolean alliance = r.pakta == Relasi.PAKTA_SEKUTU;
		r.pakta = Relasi.PAKTA_TIDAK;
		r.bawahan = false;
		if (sect.tuan != null && sect.tuan.equals(owner)) {
			sect.tuan = null;
		}
		Standing.adjust(server, owner, sect, alliance ? -30 : -15, "pakta_putus");
		Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.putus", sect.name()).withStyle(ChatFormatting.RED));
		Diplomasi.fire(server, owner, sect, AksiSekte.PUTUS);
		data.setDirty();
	}

	private Pakta() {
	}
}
