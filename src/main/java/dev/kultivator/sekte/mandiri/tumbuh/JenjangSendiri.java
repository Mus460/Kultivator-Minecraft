package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.Stars;
import dev.kultivator.disciple.path.Surpass;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Naik jenjang sendiri, the disciple's own rank request (DESIGN 39.5.5) and its ambitions beyond (39.5.8).
 * <p>
 * <b>The request.</b> A disciple that meets every old requirement ({@code SectRanks.requirementsSelf}: realm, its own PK
 * at {@code PK_COST}, the retest, the Latihan Khusus, the tournament or Void floor of a Murid Inti) asks to rise. Who
 * approves follows the policy ({@code Kebijakan.jenjang}): at once under Penuh or {@code OTOMATIS}; under the default
 * {@code TETUA_AGUNG} the hall's Grand Elder when one is here (the diary names who said yes), else the master; always the
 * master under {@code TANYA}. The master's answer is a chat click: {@code [Setujui]} runs the old promotion with the
 * master (Luar to Murid Dalam starts the Latihan Khusus), {@code [Nanti]} lets it rest three days. A request held only by
 * a line that needs the master (the trial, the seat counts of Tetua and Tetua Agung while the master is away) goes to the
 * master too. Approved: {@code SectRanks.promoteSelf} (the old robe flash and bell, its own PK, the hall registry).
 * <p>
 * <b>Ambitions</b> (39.5.8): an Ambisius or Sombong disciple whose realm stands level with its master's asks once a week
 * for a "spar with the master" (the master starts it the old way; {@code Surpass} itself is untouched); a disciple at the
 * peak that reaches a star calamity gate asks the master to open it (the calamity is always the master's), also when the
 * master was away at that minute. Star condensing itself already runs on its own ("Otomatis" is the old default).
 */
public final class JenjangSendiri {

	private static final String KEY = "jenjang";
	public static final int ULANG_HARI = 3;
	public static final int TANDING_HARI = 7;

	// ================================================================== the request

	/** Once a minute per loaded disciple. */
	static void periksa(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		DiscipleData data = entity.data();
		if (owner == null || !MandiriApi.aktif() || data.breakingThrough()) {
			return;
		}
		Kebijakan policy = MandiriApi.kebijakan(entity);
		if (policy.mode() == ModeMandiri.PATUH) {
			return;
		}
		ambisi(entity);
		int target = SectRanks.nextRank(data);
		if (target < 0) {
			DataTumbuh.setTunggu(data, DataTumbuh.T_JENJANG, -1L);
			return;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		CompoundTag record = DataTumbuh.baca(data).getCompoundOrEmpty(KEY);
		if (record.getIntOr("target", -1) != target) {
			// A promotion happened (or a demotion): the old request is gone.
			CompoundTag live = DataTumbuh.sub(data, KEY);
			live.putInt("target", target);
			live.remove("ditanya");
			live.remove("nanti");
			DataTumbuh.setTunggu(data, DataTumbuh.T_JENJANG, -1L);
			record = live;
		}
		if (today < record.getLongOr("nanti", -100L) + ULANG_HARI) {
			return;
		}
		boolean ready = SectRanks.promoteBlockerSelf(entity) == null;
		boolean masterOnly = !ready && SectRanks.onlyMasterLinesSelf(entity);
		if (!ready && !masterOnly) {
			return;
		}
		if (DataTumbuh.tungguHari(data, DataTumbuh.T_JENJANG) < 0L) {
			DataTumbuh.setTunggu(data, DataTumbuh.T_JENJANG, today);
			KabarTumbuh.catat(entity, "jenjang", "jenjang.mohon", EntriHarian.Suasana.TENANG, false, SectRanks.rankName(target).getString());
		}
		if (ready) {
			DiscipleEntity approver = penyetuju(entity, policy);
			if (approver != null || policy.mode() == ModeMandiri.PENUH || policy.jenjang() == Kebijakan.Jenjang.OTOMATIS) {
				naik(entity, target, approver);
				return;
			}
		}
		if (today >= record.getLongOr("ditanya", -100L) + ULANG_HARI) {
			DataTumbuh.sub(data, KEY).putLong("ditanya", today);
			tanya(entity, target, masterOnly);
		}
	}

	/** The Grand Elder of the hall (loaded, not the disciple itself) that may approve under {@code TETUA_AGUNG}, or null. */
	private static @Nullable DiscipleEntity penyetuju(DiscipleEntity entity, Kebijakan policy) {
		UUID owner = entity.ownerId();
		if (owner == null || policy.jenjang() != Kebijakan.Jenjang.TETUA_AGUNG) {
			return null;
		}
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other != entity && other.role() == DiscipleEntity.Role.DISCIPLE && SectRanks.rank(other.data()) == SectRanks.TETUA_AGUNG
					&& other.isAlive()) {
				return other;
			}
		}
		return null;
	}

	/** Promoted by its own request: the old promotion, the diary, a memory, the master's line, the Grand Elder's word. */
	private static void naik(DiscipleEntity entity, int target, @Nullable DiscipleEntity approver) {
		if (!SectRanks.promoteSelf(entity)) {
			return;
		}
		DiscipleData data = entity.data();
		DataTumbuh.setTunggu(data, DataTumbuh.T_JENJANG, -1L);
		CompoundTag record = DataTumbuh.sub(data, KEY);
		record.remove("ditanya");
		record.remove("nanti");
		MinecraftServer server = entity.level().getServer();
		String rankName = SectRanks.rankName(target).getString();
		Ingatan.remember(server, data, "mandiri.jenjang_sendiri", approver == null ? null : approver.soulId(), 5,
				KabarTumbuh.P + "ingat.jenjang", rankName);
		if (approver != null) {
			KabarTumbuh.catat(entity, "jenjang", "jenjang.disetujui_tetua", EntriHarian.Suasana.BANGGA, true, approver.getName().getString(),
					rankName);
			SekteSuara.body(approver, "jenjang_setuju", "greet");
		} else {
			KabarTumbuh.catat(entity, "jenjang", "jenjang.naik", EntriHarian.Suasana.BANGGA, true, rankName);
		}
		SekteSuara.line(entity, "jenjang_naik", "thanks", 0);
		Component who = approver == null ? Component.translatable(KabarTumbuh.P + "jenjang.oleh_aturan")
				: Component.translatable(KabarTumbuh.P + "jenjang.oleh_tetua", KabarTumbuh.nama(approver));
		KabarTumbuh.kirim(entity, null, KabarTumbuh.baris("jenjang.naik", SectRanks.TRIM[target], KabarTumbuh.nama(entity),
				SectRanks.rankName(target), who).withStyle(ChatFormatting.BOLD));
	}

	/** The master is asked: the realm and PK lines held, the buttons; the unmet master lines listed. */
	private static void tanya(DiscipleEntity entity, int target, boolean masterOnly) {
		String soul = entity.soulId().toString();
		MutableComponent line = KabarTumbuh.baris(masterOnly ? "jenjang.tanya_guru" : "jenjang.tanya", 0xE6D2A0, KabarTumbuh.nama(entity),
				SectRanks.rankName(target));
		if (masterOnly) {
			List<Component> unmet = SectRanks.unmetSelf(entity);
			if (!unmet.isEmpty()) {
				line.append(Component.literal(" (")).append(KabarTumbuh.abu(unmet.get(0))).append(Component.literal(")"));
			}
		}
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.jenjang_setujui",
				"/kultivasi perguruan mandiri jenjang setujui " + soul, KabarTumbuh.P + "tombol.jenjang_setujui.hover", 0xFFD54A));
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.jenjang_nanti",
				"/kultivasi perguruan mandiri jenjang nanti " + soul, KabarTumbuh.P + "tombol.jenjang_nanti.hover", 0xA0A0A0));
		KabarTumbuh.kirim(entity, null, line);
	}

	/** The master's {@code [Setujui]}: the old promotion with the master (the trial for Luar to Dalam). */
	static boolean setujui(DiscipleEntity entity, ServerPlayer master) {
		boolean done = SectRanks.promoteByMaster(master, entity);
		if (done) {
			DataTumbuh.setTunggu(entity.data(), DataTumbuh.T_JENJANG, -1L);
			DataTumbuh.sub(entity.data(), KEY).remove("ditanya");
		}
		return done;
	}

	/** The master's {@code [Nanti]}: no new request for three days. */
	static void nanti(DiscipleEntity entity) {
		DataTumbuh.sub(entity.data(), KEY).putLong("nanti", DataTumbuh.hari(entity.level().getServer()));
		DataTumbuh.setTunggu(entity.data(), DataTumbuh.T_JENJANG, -1L);
		KabarTumbuh.catat(entity, "jenjang", "jenjang.ditunda", EntriHarian.Suasana.KESAL, false);
	}

	/** {@code uji ... tumbuh jenjang <murid>}: the waiting rules skipped, the request decided now. */
	static void paksa(DiscipleEntity entity) {
		CompoundTag record = DataTumbuh.sub(entity.data(), KEY);
		record.remove("nanti");
		record.remove("ditanya");
		periksa(entity);
	}

	// ================================================================== ambitions (39.5.8)

	private static void ambisi(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		CompoundTag record = DataTumbuh.baca(data).getCompoundOrEmpty(KEY);
		ServerPlayer master = entity.owner();
		boolean proud = SekteApi.hati().has(data, Sifat.AMBISIUS) || SekteApi.hati().has(data, Sifat.SOMBONG);
		if (proud && master != null && !Surpass.surpassed(data) && today >= record.getLongOr("tanding", -100L) + TANDING_HARI
				&& data.realm().ordinal() >= Cultivators.get(master).realm().ordinal()) {
			DataTumbuh.sub(data, KEY).putLong("tanding", today);
			KabarTumbuh.kirim(entity, null, KabarTumbuh.baris("ambisi.tanding", 0xFFB08A, KabarTumbuh.nama(entity)));
			KabarTumbuh.catat(entity, "jenjang", "ambisi.tanding", EntriHarian.Suasana.SENANG, false);
			SekteSuara.line(entity, "tanding_guru", "greet", 600);
		}
		int calamity = data.rank().isMax() ? Stars.pendingCalamity(data) : 0;
		if (calamity > 0 && record.getIntOr("bencana", 0) != calamity) {
			DataTumbuh.sub(data, KEY).putInt("bencana", calamity);
			KabarTumbuh.kirim(entity, null, KabarTumbuh.baris("ambisi.bencana", 0xD9B8FF, KabarTumbuh.nama(entity), calamity));
			KabarTumbuh.catat(entity, "jenjang", "ambisi.bencana", EntriHarian.Suasana.TENANG, true, "n:" + calamity);
		}
	}

	private JenjangSendiri() {
	}
}
