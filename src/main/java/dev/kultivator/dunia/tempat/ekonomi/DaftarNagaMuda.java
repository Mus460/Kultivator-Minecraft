package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Daftar Naga Muda (DESIGN 38.M.3): a board of 50 young cultivators of the realm, renewed every world day. Rivals are
 * named from the town name tables and carry the names of the real NPC sects ({@code DiplomasiService}, the one list of
 * T12); Feng Ao, the proud young examiner of the guild (38.S.2), is always on it. A player's score is his guild points,
 * +5 per formal Sword Duel won against a stranger (at most 3 a day) and the tournament results other modules add through
 * {@link #tambahSkor}. Top 10 earns a title line, rank 1 "Naga Muda Pertama" (advancement {@code tempat/naga_muda_pertama}).
 */
public final class DaftarNagaMuda {

	public static final int UKURAN = 50;
	public static final int SKOR_DUEL = 5;
	public static final int DUEL_HARIAN = 3;
	private static final String SKOR = "naga_skor";

	/** One row of the board. */
	public record Entri(Component nama, @Nullable Component sekte, int skor, @Nullable UUID pemain) {
	}

	static void init() {
		DuelEndListeners.add(DaftarNagaMuda::onDuel);
	}

	private static void onDuel(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		if (!(winner instanceof ServerPlayer player) || loser == null || loser instanceof ServerPlayer) {
			return;
		}
		if (loser instanceof dev.kultivator.disciple.DiscipleEntity disciple && player.getUUID().equals(disciple.ownerId())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		if (data.harian(player.getUUID(), hari, "naga_duel") >= DUEL_HARIAN) {
			return;
		}
		data.tambahHarian(player.getUUID(), hari, "naga_duel", 1);
		tambahSkor(player, SKOR_DUEL);
	}

	/** Adds to the player's Young Dragon score (guild points add themselves through {@link Lencana#tambah}). */
	public static void tambahSkor(ServerPlayer player, int n) {
		if (n == 0) {
			return;
		}
		EkonomiData data = EkonomiData.get(player.level().getServer());
		CompoundTag tag = data.pemain(player.getUUID());
		tag.putInt(SKOR, Math.max(0, tag.getIntOr(SKOR, 0) + n));
		tag.putString("nama", player.getName().getString());
		data.touch();
	}

	/** Today's board, best first (players included by their saved name). */
	public static List<Entri> papan(MinecraftServer server) {
		long hari = Barang.hari(server);
		List<Entri> list = new ArrayList<>();
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ 0x4E4147414DL);
		DiplomasiService diplomasi = SekteApi.diplomasi();
		List<String> sects = diplomasi.sects();
		// Feng Ao: climbs a little every day
		list.add(new Entri(Component.translatable("kultivator.tempat_ekonomi.naga.feng_ao"), Component.translatable("kultivator.tempat_ekonomi.naga.gilda"),
				(int) Math.min(900L, 420L + hari * 6L), null));
		for (int i = 0; i < UKURAN - 1; i++) {
			int look = random.nextInt();
			int base = 40 + (int) ((UKURAN - i) * 11.0D) + random.nextInt(40);
			int growth = 1 + random.nextInt(4);
			Component sekte = sects.isEmpty() ? null : diplomasi.name(sects.get(Math.floorMod(look, sects.size())));
			list.add(new Entri(KotaNames.person(look), sekte, (int) Math.min(1200L, base + hari * growth), null));
		}
		EkonomiData data = EkonomiData.get(server);
		for (UUID id : data.pemainIds()) {
			CompoundTag tag = data.pemain(id);
			int skor = tag.getIntOr(SKOR, 0);
			if (skor > 0) {
				list.add(new Entri(Component.literal(tag.getStringOr("nama", "?")), null, skor, id));
			}
		}
		list.sort(Comparator.comparingInt(Entri::skor).reversed());
		return list.size() > UKURAN ? new ArrayList<>(list.subList(0, UKURAN)) : list;
	}

	/** The player's rank 1-50 (0 = not on the board). */
	public static int peringkat(ServerPlayer player) {
		List<Entri> board = papan(player.level().getServer());
		for (int i = 0; i < board.size(); i++) {
			if (player.getUUID().equals(board.get(i).pemain())) {
				if (i == 0) {
					Advancements.grant(player, "tempat/naga_muda_pertama");
				}
				return i + 1;
			}
		}
		return 0;
	}

	/** The {@code naga} rows of the guild sheet. */
	static ListTag lembar(ServerPlayer player) {
		ListTag rows = new ListTag();
		List<Entri> board = papan(player.level().getServer());
		for (int i = 0; i < board.size() && rows.size() < UKURAN; i++) {
			Entri entri = board.get(i);
			CompoundTag row = new CompoundTag();
			row.putInt(LembarKunci.GildaMisi.PERINGKAT, i + 1);
			LembarKunci.teks(row, LembarKunci.NAMA, entri.nama());
			if (entri.sekte() != null) {
				LembarKunci.teks(row, LembarKunci.SEKTE, entri.sekte());
			}
			row.putInt(LembarKunci.SKOR, entri.skor());
			if (player.getUUID().equals(entri.pemain())) {
				row.putBoolean(LembarKunci.ANDA, true);
			}
			rows.add(row);
		}
		return rows;
	}

	private DaftarNagaMuda() {
	}
}
