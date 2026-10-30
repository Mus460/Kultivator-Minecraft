package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.politik.Politik;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a ward ("murid titipan", DESIGN 41.8.7) gets for the 50 x kf PK its master paid. The disciple stays the player's
 * (Disciples, Bond and SectRanks are untouched); these are the sect's side of the bargain:
 * <ul>
 *   <li><b>Pool</b>: a ward meditating within {@link #DEKAT_KOLAM} blocks of a meditation seat of its master's own compound
 *       (the spirit pools and meditation caves, {@code KolamRoh}) gains x{@value #FAKTOR_KOLAM} meditation XP, through the
 *       disciple XP hook. No booking is needed and the master's own session is not touched.</li>
 *   <li><b>Not attacked</b>: inside that compound a fellow member who hits a ward breaks A1 like a blow at a member
 *       ({@code Pemeriksa}), and what other creatures deal to a ward there is halved ({@value #FAKTOR_LUKA}), as the
 *       compound's guards come to its aid. The disciple never dies by this (disciples die only by command).</li>
 *   <li><b>Second home</b>: the master's sect counts as met for the hall (the Mandiri journey kind SEKTE_TAMU lists only met
 *       sects), now and every world day. A ward whose disciple was released or died is dropped from the list then too, so
 *       the seat of the {@code 1 + rank/2} limit frees again.</li>
 *   <li><b>Disciple Tournament</b>: the wards of members at the lei tai enter the bracket as named disciples of the master
 *       (a sect body fights for them, {@link #peserta}); the master gets half the PK of the placement ({@link #hadiah}).</li>
 *   <li>Lectures (Ceramah) are 41C's {@code PaviliunJasa}.</li>
 * </ul>
 * Cheap: every hook first asks the one map of the master's record and only then looks at positions.
 */
public final class MuridTitipan {

	/** Meditation XP factor of a ward at a pool seat of its master's compound (41.8.7). */
	public static final double FAKTOR_KOLAM = 1.15D;
	/** Blocks from a meditation seat that count as sitting at the pool. */
	public static final double DEKAT_KOLAM = 3.5D;
	/** Share of the damage a ward in its compound still takes from creatures. */
	public static final double FAKTOR_LUKA = 0.5D;
	/** Prefix of a ward's bracket id: {@code titipan:<master uuid>:<soul uuid>}. */
	public static final String AWALAN = "titipan:";

	private static boolean registered;

	private MuridTitipan() {
	}

	/** From {@code SitusGabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		DiscipleHooks.addXpFactor((entity, data, source) -> source == XpSource.MEDITATION && entity != null && diKolam(entity) ? FAKTOR_KOLAM : 1.0D);
		DiscipleHooks.addHurtFactor((entity, source) -> source.getEntity() != null && !(source.getEntity() instanceof Player) && dalamKompleks(entity)
				? FAKTOR_LUKA : 1.0D);
		Keanggotaan.addPenyentuh(MuridTitipan::sentuh);
	}

	// ================================================================== who is a ward

	/** The sect {@code murid} is a ward of (its master's sect; the master is a member of it), or null. */
	public static @Nullable String sekteDari(DiscipleEntity murid) {
		if (murid.role() != DiscipleEntity.Role.DISCIPLE || !(murid.level() instanceof ServerLevel level)) {
			return null;
		}
		UUID master = murid.ownerId();
		return master == null ? null : Politik.titipan(level.getServer(), master, murid.soulId());
	}

	/** True when {@code murid} is a ward standing inside the compound of its own sect. */
	public static boolean dalamKompleks(DiscipleEntity murid) {
		String sekte = sekteDari(murid);
		if (sekte == null) {
			return false;
		}
		var kompleks = KompleksApi.kompleksDi((ServerLevel) murid.level(), murid.blockPosition());
		return kompleks != null && sekte.equals(kompleks.sekte());
	}

	private static boolean diKolam(DiscipleEntity murid) {
		return dalamKompleks(murid) && KolamRoh.dekatKolam((ServerLevel) murid.level(), murid.blockPosition(), DEKAT_KOLAM);
	}

	// ================================================================== registration and daily upkeep

	/** A disciple was just registered as a ward: its sect is a second home from now on. */
	public static void didaftar(ServerPlayer master, String sekte) {
		SekteApi.diplomasi().meet(master.getUUID(), sekte);
	}

	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		String sekte = k.sekteAktif();
		if (sekte == null || k.muridTitipan.isEmpty()) {
			return;
		}
		Set<UUID> ada = new HashSet<>();
		// "list" is Disciples' own key of the master's list; a released or fallen disciple is no longer a ward
		for (Tag tag : Cultivators.get(player).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list")) {
			if (tag instanceof CompoundTag e && !e.getBooleanOr("dead", false)) {
				try {
					ada.add(UUID.fromString(e.getStringOr("id", "")));
				} catch (IllegalArgumentException ignored) {
					// a broken entry is not a disciple
				}
			}
		}
		k.muridTitipan.removeIf(soul -> !ada.contains(soul));
		if (!k.muridTitipan.isEmpty()) {
			SekteApi.diplomasi().meet(player.getUUID(), sekte);
		}
	}

	// ================================================================== the Disciple Tournament

	/** The wards of {@code master} that stand in the world now (loaded), as bracket entrants of the tournament. */
	static List<TurnamenMurid.Peserta> peserta(ServerPlayer master, Keanggotaan k) {
		List<TurnamenMurid.Peserta> out = new ArrayList<>();
		if (k.muridTitipan.isEmpty()) {
			return out;
		}
		for (DiscipleEntity d : Disciples.loaded(master.getUUID())) {
			if (d.role() != DiscipleEntity.Role.DISCIPLE || !k.muridTitipan.contains(d.soulId())) {
				continue;
			}
			Rank rank = d.data().rank();
			int peran = rank.realm().ordinal() >= Realm.FOUNDATION.ordinal() ? TurnamenMurid.PERAN_DALAM : TurnamenMurid.PERAN_LUAR;
			out.add(new TurnamenMurid.Peserta(AWALAN + master.getUUID() + ":" + d.soulId(),
					Component.translatable("kultivator.gabung.turnamen.titipan_nama", d.getName(), master.getName()), d.slimSkin(), rank.step(), peran,
					List.of()));
		}
		return out;
	}

	/** True when a bracket id is a ward's. */
	static boolean titipan(String id) {
		return id.startsWith(AWALAN);
	}

	/** The master's UUID string of a ward's bracket id (so the champion's quota goes to the master). */
	static String tuan(String id) {
		int mulai = AWALAN.length();
		int akhir = id.indexOf(':', mulai);
		return akhir < 0 ? id.substring(mulai) : id.substring(mulai, akhir);
	}

	/** Pays the master (when online) {@code pk} PK for its ward's placement {@code tempat} (1 champion .. 4 entered). */
	static void hadiah(MinecraftServer server, String id, Component nama, int tempat, int pk) {
		try {
			ServerPlayer master = server.getPlayerList().getPlayer(UUID.fromString(tuan(id)));
			if (master == null) {
				return;
			}
			GabungApi.tambahPk(master, pk, "gabung.turnamen");
			Msg.chat(master, Component.translatable("kultivator.gabung.turnamen.titipan_hadiah." + tempat, nama, pk).withColor(0xFFD24A));
		} catch (IllegalArgumentException ignored) {
			// a broken id (a hand-edited save) pays nobody
		}
	}
}
