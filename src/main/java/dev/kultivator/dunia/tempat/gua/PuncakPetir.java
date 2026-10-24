package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.abadi.Abadi;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.PapanHidup;
import dev.kultivator.dunia.tempat.ui.PapanHidupEntity;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puncak Petir (DESIGN 38.C.5, 38.A #25, anchor {@code gua/petir_altar} on the round altar of a peak terrace): nine
 * waxed copper pillars under little roofs, a spectators' balustrade, and the Batu Catatan (a stele with a live board
 * of names, {@code PapanHidup}). A tribulation weathered on the terrace lands only 0.90 of each strike ({@code TempatApi}
 * factor {@code kesengsaraan}, bounded ≥ 0.85, through {@code Tribulation.addStrikeFactorProvider}; the old numbers
 * are untouched); 2-4 wandering cultivators come to the balustrade to watch while a tribulation runs there; who passes is
 * written on the stone (name, realm, day; the newest nine, {@link #CATATAN}). An Immortal Ascension (38.A #26) begun on
 * the terrace is followed the same way without touching {@code Ascend}: {@link #nearby} sees {@code Abadi.inAscension}
 * for a player on the terrace (watchers come), and when {@code Abadi.ascended} turns true while they still stand there
 * (the 3 s pass before the crossing) the name is carved as an ascension ({@code |naik} row, its own line on the board).
 * A meditation site ×1.25. Lightning is never made and the weather never changed: the strikes are Tribulation's own
 * entity effects.
 */
public final class PuncakPetir extends GuaRun {

	public static final int CATATAN = 9;

	private @Nullable PapanHidupEntity papan;
	private final List<KultivatorNpc> penonton = new ArrayList<>();
	private long pulangPada;
	/** Players whose ascension ritual runs on this terrace, and whether they had ascended before it began. */
	private final Map<UUID, Boolean> naik = new HashMap<>();

	public PuncakPetir(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return Math.max(8, Math.min(16, data().getIntOr("r", 11))) + 2.0D;
	}

	@Override
	protected void spawn() {
		int bx = data().getIntOr("bx", 0);
		int bz = data().getIntOr("bz", -9);
		// the board stands on the terrace floor (3 below the raised altar) two steps in front of the stele
		PapanHidupEntity board = PapanHidup.pasang(level, block(bx, -3, bz + 2), frame.yaw(0.0F), PapanHidupEntity.JENIS_KABAR);
		if (board != null) {
			own(board);
			papan = board;
			isi();
		}
	}

	/** Fills the Batu Catatan board from the anchor's state (newest first). */
	private void isi() {
		PapanHidupEntity board = papan;
		if (board == null) {
			return;
		}
		List<String> rows = GuaData.strings(state(), "catatan");
		List<Component> judul = new ArrayList<>();
		List<Component> baris = new ArrayList<>();
		judul.add(Component.translatable("kultivator.gua.petir.batu"));
		baris.add(Component.translatable(rows.isEmpty() ? "kultivator.gua.petir.batu_kosong" : "kultivator.gua.petir.batu_isi", rows.size()));
		for (int i = rows.size() - 1; i >= 0 && judul.size() < PapanHidupEntity.MAKS_LEMBAR; i--) {
			String[] p = rows.get(i).split("\\|", 4);
			if (p.length < 3) {
				continue;
			}
			Realm realm = Realm.byId(p[1]);
			Component nama = realm == null ? Component.literal(p[1]) : realm.displayName();
			boolean kenaikan = p.length > 3 && "naik".equals(p[3]);
			judul.add(Component.literal(p[0]).withStyle(kenaikan ? ChatFormatting.GOLD : ChatFormatting.WHITE));
			baris.add(Component.translatable(kenaikan ? "kultivator.gua.petir.baris_naik" : "kultivator.gua.petir.baris", nama, p[2]));
		}
		board.setLembar(judul, baris);
	}

	/** A tribulation begins on the terrace: wanderers come to the balustrade to watch. */
	void omen(ServerPlayer player) {
		if (!penonton.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		int n = 2 + random.nextInt(3);
		List<int[]> spots = new ArrayList<>();
		for (int dz = -6; dz <= 6; dz += 2) {
			spots.add(new int[]{-9, dz});
			spots.add(new int[]{9, dz});
		}
		for (int i = 0; i < n && !spots.isEmpty(); i++) {
			int[] s = spots.remove(random.nextInt(spots.size()));
			Vec3 at = spot(s[0], -2, s[1]);
			Vec3 dir = centre().subtract(at);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			Realm realm = Realm.byOrdinal(Realm.FOUNDATION.ordinal() + random.nextInt(3));
			KultivatorNpc w = GuaTubuh.buat(this, NpcRole.PENGEMBARA, net.minecraft.core.BlockPos.containing(at), yaw, 2, realm, null,
					GerakId.TATAP_LANGIT, 0);
			if (w != null) {
				HidupApi.tatap(w, player);
				penonton.add(w);
			}
		}
		if (!penonton.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.gua.petir.penonton", penonton.size()).withStyle(ChatFormatting.GRAY));
		}
		pulangPada = 0L;
	}

	/** A strike fell: the watchers flinch and cheer. */
	void sambar() {
		for (KultivatorNpc w : penonton) {
			if (!w.isRemoved() && level.getRandom().nextInt(3) == 0) {
				HidupApi.klip(w, GerakId.SORAK_ARENA, 0, 40);
			}
		}
	}

	/** The tribulation is over: on success the name is carved; the watchers clap and leave a little later. */
	void akhir(ServerPlayer player, boolean lulus, boolean uji) {
		for (KultivatorNpc w : penonton) {
			if (!w.isRemoved()) {
				HidupApi.klip(w, lulus ? GerakId.TEPUK_TANGAN : GerakId.MEMANDANG, 0, 80);
			}
		}
		pulangPada = now() + 300L;
		if (!lulus || uji) {
			return;
		}
		Rank rank = Cultivators.get(player).rank();
		GuaData.addString(state(), "catatan", player.getGameProfile().name() + "|" + rank.realm().id() + "|" + today(), CATATAN);
		isi();
		Msg.chat(player, Component.translatable("kultivator.gua.petir.terukir").withStyle(ChatFormatting.GOLD));
		GuaPayload.stempel(player, "kultivator.gua.petir.terukir.kartu", "雷", 0xB57CFF);
	}

	/**
	 * Every 20 ticks: follows an Immortal Ascension on the terrace (38.A #26). The ritual's start brings the watchers;
	 * the pass (ascended while still standing here) carves the name as an ascension; a failed ritual sends them off.
	 */
	@Override
	protected void nearby(List<ServerPlayer> within) {
		if (!GuaConfig.on()) {
			return;
		}
		for (ServerPlayer p : within) {
			UUID id = p.getUUID();
			Boolean sebelum = naik.get(id);
			if (sebelum == null) {
				if (Abadi.inAscension(p) && inside(p)) {
					naik.put(id, Abadi.ascended(p));
					omen(p);
					Msg.chat(p, Component.translatable("kultivator.gua.petir.naik_mulai").withStyle(ChatFormatting.LIGHT_PURPLE));
				}
				continue;
			}
			if (!sebelum && Abadi.ascended(p)) {
				naik.remove(id);
				naikSelesai(p, true);
			} else if (!Abadi.inAscension(p)) {
				naik.remove(id);
				naikSelesai(p, false);
			}
		}
	}

	/** The ascension on the terrace is over: carved on a pass, the watchers react either way. */
	private void naikSelesai(ServerPlayer player, boolean lulus) {
		for (KultivatorNpc w : penonton) {
			if (!w.isRemoved()) {
				HidupApi.klip(w, lulus ? GerakId.TEPUK_TANGAN : GerakId.MEMANDANG, 0, 80);
			}
		}
		pulangPada = now() + 300L;
		if (!lulus) {
			return;
		}
		Rank rank = Cultivators.get(player).rank();
		GuaData.addString(state(), "catatan", player.getGameProfile().name() + "|" + rank.realm().id() + "|" + today() + "|naik", CATATAN);
		isi();
		Msg.chat(player, Component.translatable("kultivator.gua.petir.naik_terukir").withStyle(ChatFormatting.GOLD));
		GuaPayload.stempel(player, "kultivator.gua.petir.naik_terukir.kartu", "昇", 0xFFE9A8);
	}

	@Override
	protected void tick(long now) {
		penonton.removeIf(w -> w.isRemoved());
		if (pulangPada > 0L && now >= pulangPada) {
			for (KultivatorNpc w : penonton) {
				w.discard();
			}
			penonton.clear();
			pulangPada = 0L;
		}
	}

	@Override
	protected void despawn() {
		papan = null;
		penonton.clear();
		naik.clear();
		super.despawn();
	}

	/** The listener that feeds the peak runs (registered once from {@link Gua}). */
	static Tribulation.Listener listener() {
		return new Tribulation.Listener() {
			@Override
			public void onOmen(ServerPlayer p, Rank t) {
				PuncakPetir run = GuaRuns.at(p, PuncakPetir.class);
				if (run != null && GuaConfig.on()) {
					run.omen(p);
				}
			}

			@Override
			public void onStrike(ServerPlayer p, Rank t, int index, int total) {
				PuncakPetir run = GuaRuns.at(p, PuncakPetir.class);
				if (run != null) {
					run.sambar();
				}
			}

			@Override
			public void onEnd(ServerPlayer p, Rank t, boolean success, boolean test) {
				PuncakPetir run = GuaRuns.at(p, PuncakPetir.class);
				if (run != null && GuaConfig.on()) {
					run.akhir(p, success, test);
				}
			}
		};
	}
}
