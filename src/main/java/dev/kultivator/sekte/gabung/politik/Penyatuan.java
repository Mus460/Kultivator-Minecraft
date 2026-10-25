package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.diplomasi.GeneratorNama;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Absorb and merge of DESIGN 41.8.6 (the alliance Xiao Yan built of the sects he led). Only a player master of an NPC sect
 * who also owns a hall; both directions use the one existing mechanism of module D, the vassal {@code SekteNpc.tuan}.
 * <ul>
 *   <li><b>Serap</b> ({@link #serap}): the NPC sect enters the player's hall: {@code tuan = owner}, {@code lebur = true}.
 *   The sect stays in the one T12 list (diplomacy, map and rivals agree); its shown name becomes
 *   "&lt;hall&gt; · Gunung &lt;word B&gt;" ({@link #namaTampil}). Every day the hall pool gains {@code min(2 x tier, 30)} PK
 *   from the mountain, and every {@link #CALON_HARI} days a candidate comes from the sect's people (source
 *   {@code gabung_serap}). The sect no longer raids anyone on its own (D halves the growth of a vassal sect already).</li>
 *   <li><b>Lebur</b> ({@link #lebur}): the hall enters the NPC sect as a permanent branch without tribute (41.8.2), the
 *   player stays master; the hall's disciples may live at the compound.</li>
 * </ul>
 * Undone only by abdication ({@code Suksesi.turunTakhta} calls {@link #pisah}): {@code tuan = null}, the standing stays at
 * least SEKUTU 60. Advancement {@code sekte/gabung_serap}. State: the sect's {@code DataSekteGabung.lebur} and its
 * {@code politik} compound ({@code serapPemilik}, {@code serapCalonHari}).
 */
public final class Penyatuan {

	/** Days between two candidates from an absorbed sect. */
	public static final int CALON_HARI = 5;
	/** The daily PK of an absorbed sect: {@code min(2 x tier, 30)}. */
	public static final int PK_MAKS = 30;

	private static final String KEY = "kultivator.gabung.politik.serap.";

	/** Why {@code player} may not absorb or merge now, or null (master of the sect, own hall, {@code bolehLebur}). */
	public static @Nullable Component syarat(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !Gabung.aktif()) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		if (!GabungConfig.get().bolehLebur) {
			return Component.translatable(KEY + "mati");
		}
		GabungData.DataSekteGabung d = GabungData.get(server).sekte(sekte);
		if (k.pangkat != Pangkat.KETUA || !player.getUUID().equals(d.ketuaPemain)) {
			return Component.translatable(KEY + "bukan_ketua");
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			return Component.translatable(KEY + "tanpa_aula");
		}
		if (d.lebur || Cabang.induk(server, player.getUUID()) != null && isLebur(server, sekte, player.getUUID())) {
			return Component.translatable(KEY + "sudah");
		}
		return null;
	}

	/** Absorbs the player's NPC sect into his hall; null when done, else why not. */
	public static @Nullable Component serap(ServerPlayer player) {
		Component why = syarat(player);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String induk = Cabang.induk(server, player.getUUID());
		if (induk != null) {
			// a branch hall that absorbs its own mother: the branch tie is replaced by the absorption
			Cabang.lepas(server, player.getUUID(), induk, 0);
		}
		DiplomasiGabung.setLebur(server, sekte, player.getUUID());
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		b.putString("serapPemilik", player.getUUID().toString());
		b.putLong("serapCalonHari", GabungApi.hari(server));
		GabungData.get(server).setDirty();
		Advancements.grant(player, "sekte/gabung_serap");
		Halls.log(player.getUUID(), Component.translatable(KEY + "log", DiplomasiGabung.nama(server, sekte)).withStyle(ChatFormatting.GOLD));
		Politik.kabar(server, "gabung.serap", DiplomasiGabung.nama(server, sekte), player.getName());
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "kabar", player.getName(), namaTampil(server, sekte)), ChatFormatting.GOLD);
		return null;
	}

	/** Merges the player's hall into his NPC sect as a permanent branch without tribute; null when done. */
	public static @Nullable Component lebur(ServerPlayer player) {
		Component why = syarat(player);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		String sekte = GabungApi.sekte(player.getUUID());
		String induk = Cabang.induk(server, player.getUUID());
		if (induk != null && !induk.equals(sekte)) {
			Cabang.lepas(server, player.getUUID(), induk, 0);
		}
		Cabang.jadikan(server, player.getUUID(), sekte, true);
		player.sendSystemMessage(Component.translatable(KEY + "lebur", DiplomasiGabung.nama(server, sekte)).withStyle(ChatFormatting.GOLD));
		return null;
	}

	private static boolean isLebur(MinecraftServer server, String sekte, UUID owner) {
		CompoundTag info = DiplomasiGabung.bagian(server, sekte).getCompoundOrEmpty("cabangInfo").getCompoundOrEmpty(owner.toString());
		return info.getBooleanOr("lebur", false);
	}

	/** Releases the absorption and the permanent branch of {@code owner} (abdication). */
	public static void pisah(MinecraftServer server, String sekte, UUID owner) {
		if (DiplomasiGabung.lebur(server, sekte)) {
			DiplomasiGabung.setLebur(server, sekte, null);
			DiplomasiGabung.bagian(server, sekte).remove("serapPemilik");
			Halls.log(owner, Component.translatable(KEY + "log_pisah", DiplomasiGabung.nama(server, sekte)));
		}
		if (isLebur(server, sekte, owner)) {
			Cabang.lepas(server, owner, sekte, 0);
		}
		GabungData.get(server).setDirty();
	}

	/** The absorbing hall's owner, or null. */
	public static @Nullable UUID pemilik(MinecraftServer server, String sekte) {
		return server == null || !DiplomasiGabung.lebur(server, sekte) ? null
				: Kursi.uuid(DiplomasiGabung.bagian(server, sekte).getStringOr("serapPemilik", ""));
	}

	/** The shown name: "&lt;hall&gt; · Gunung &lt;word B&gt;" while absorbed, else the sect's own name. */
	public static Component namaTampil(MinecraftServer server, String sekte) {
		UUID owner = pemilik(server, sekte);
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		if (hall == null || e == null) {
			return DiplomasiGabung.nama(server, sekte);
		}
		Component aula = hall.name().isEmpty() ? Kursi.namaPemegang(server, owner.toString()) : Component.literal(hall.name());
		return Component.translatable(KEY + "nama", aula, Component.translatable(GeneratorNama.KEY + "nama.b." + e.kataB()));
	}

	/** Daily: the mountain's PK into the hall pool and the candidate every 5 days. */
	static void harian(MinecraftServer server, String sekte, long hari) {
		UUID owner = pemilik(server, sekte);
		if (owner == null || Halls.hallOf(owner) == null) {
			return;
		}
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		if (b.getLongOr("serapHari", -1L) < hari) {
			b.putLong("serapHari", hari);
			int pk = Math.min(2 * GabungApi.tier(sekte), PK_MAKS);
			Halls.addPool(owner, pk);
			EkonomiApi.kas(owner, LembarKunci.BukuKas.SUMBER_UPETI, true, pk, KEY + "buku_kas");
			// shortcut: renown from disciples posted at the compound (20.3.1) is not counted; add when 39's posts expose it.
		}
		if (hari >= b.getLongOr("serapCalonHari", hari) + CALON_HARI) {
			b.putLong("serapCalonHari", hari);
			var calon = DiplomasiGabung.calon(server, sekte, "gabung_serap");
			if (calon != null) {
				Halls.addCandidates(owner, List.of(calon));
			}
		}
		GabungData.get(server).setDirty();
	}

	private Penyatuan() {
	}
}
