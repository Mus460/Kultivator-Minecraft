package dev.kultivator.dunia.tempat.cerita;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A quest chain {@code data/kultivator/tempat/quest/<id>.json} (DESIGN 38.S.1, 38.S.4): ordered stages with branches; a
 * failed deadline takes the failure branch, never a dead end.
 *
 * <p>JSON: {@code {pemberi: "tokoh:<id>"|"peran:<role>", tempat?: lang key suffix, ulang?: bool, alur?: bool, tahap: [
 * {id, tujuan?[], akibat_masuk?[], akibat?[], cabang?[{syarat[], ke}], ke?, batas_hari?, gagal_ke?, arah?}]}}.
 * <ul>
 * <li>{@code tujuan}: goal lines {@code "<jenis> <args...> [xN]"} (all must be met; {@code xN} = N times). A goal kind
 * that is not a {@link QuestTracker} kind but a {@link Syarat} id is met while the condition holds
 * ({@code punya}, {@code bendera}, {@code alur}, ...). A stage without goals waits for {@code quest_maju}.</li>
 * <li>{@code ke}: next stage id, {@code selesai} (finished, stage 999) or {@code gagal} (failed, -1); default the next
 * stage in the list, {@code selesai} after the last.</li>
 * <li>{@code gagal_ke}: where a passed {@code batas_hari} leads (default {@code gagal}).</li>
 * <li>{@code arah}: site kind prefix the journal needle points at (nearest known site of that kind).</li>
 * </ul>
 * Lang: {@code kultivator.cerita.quest.<id>.nama}, {@code .ringkas}, {@code .<stage>} (stage line), {@code .hasil.<stage>}
 * (ending text when the quest finished after that stage).
 */
public record Quest(String id, String pemberi, String tempat, boolean ulang, boolean alur, List<Tahap> tahap) {

	/** One goal of a stage. */
	public record Tujuan(String jenis, List<String> args, int jumlah) {

		static Tujuan parse(String line) {
			List<String> words = new ArrayList<>(List.of(line.trim().split("\\s+")));
			int count = 1;
			if (words.size() > 1) {
				String last = words.get(words.size() - 1).toLowerCase(Locale.ROOT);
				if (last.length() > 1 && last.charAt(0) == 'x' && last.substring(1).chars().allMatch(Character::isDigit)) {
					count = Math.max(1, Math.min(999, Integer.parseInt(last.substring(1))));
					words.remove(words.size() - 1);
				}
			}
			String kind = words.isEmpty() ? "" : words.remove(0);
			words.removeIf("ke"::equals);
			return new Tujuan(kind, List.copyOf(words), count);
		}

		/** The goal as a line without the count. */
		public String baris() {
			return args.isEmpty() ? jenis : jenis + " " + String.join(" ", args);
		}
	}

	/** A branch: the first one whose conditions all hold decides the next stage. */
	public record Cabang(List<String> syarat, String ke) {
	}

	/** One stage. */
	public record Tahap(String id, List<Tujuan> tujuan, List<String> akibatMasuk, List<String> akibat, List<Cabang> cabang,
			String ke, int batasHari, String gagalKe, String arah) {
	}

	public static final String SELESAI = "selesai";
	public static final String GAGAL = "gagal";

	public @Nullable Tahap tahap(String stageId) {
		for (Tahap t : tahap) {
			if (t.id.equals(stageId)) {
				return t;
			}
		}
		return null;
	}

	/** 1-based index of a stage (0 when unknown). */
	public int indeks(String stageId) {
		for (int i = 0; i < tahap.size(); i++) {
			if (tahap.get(i).id.equals(stageId)) {
				return i + 1;
			}
		}
		return 0;
	}

	public String kunciNama() {
		return "kultivator.cerita.quest." + id + ".nama";
	}

	public String kunciRingkas() {
		return "kultivator.cerita.quest." + id + ".ringkas";
	}

	public String kunciTahap(String stageId) {
		return "kultivator.cerita.quest." + id + "." + stageId;
	}

	public String kunciHasil(String stageId) {
		return "kultivator.cerita.quest." + id + ".hasil." + stageId;
	}

	/** Lang key of the place the quest belongs to ({@code kultivator.cerita.tempat.<tempat>}). */
	public String kunciTempat() {
		return "kultivator.cerita.tempat." + (tempat.isEmpty() ? "dunia" : tempat);
	}

	static @Nullable Quest parse(String id, JsonObject json) {
		JsonElement stages = json.get("tahap");
		if (stages == null || !stages.isJsonArray()) {
			return null;
		}
		List<JsonObject> raw = new ArrayList<>();
		for (JsonElement e : stages.getAsJsonArray()) {
			if (e.isJsonObject()) {
				raw.add(e.getAsJsonObject());
			}
		}
		List<Tahap> list = new ArrayList<>();
		for (int i = 0; i < raw.size(); i++) {
			JsonObject s = raw.get(i);
			String stageId = DialogNode.str(s, "id", "t" + (i + 1));
			List<Tujuan> goals = new ArrayList<>();
			for (String line : DialogNode.list(s, "tujuan")) {
				Tujuan goal = Tujuan.parse(line);
				if (!goal.jenis().isEmpty()) {
					goals.add(goal);
				}
			}
			List<Cabang> branches = new ArrayList<>();
			JsonElement cab = s.get("cabang");
			if (cab != null && cab.isJsonArray()) {
				for (JsonElement b : cab.getAsJsonArray()) {
					if (b.isJsonObject()) {
						JsonObject o = b.getAsJsonObject();
						branches.add(new Cabang(DialogNode.list(o, "syarat"), DialogNode.str(o, "ke", SELESAI)));
					}
				}
			}
			String next = i + 1 < raw.size() ? DialogNode.str(raw.get(i + 1), "id", "t" + (i + 2)) : SELESAI;
			list.add(new Tahap(stageId, List.copyOf(goals), DialogNode.list(s, "akibat_masuk"), DialogNode.list(s, "akibat"),
					List.copyOf(branches), DialogNode.str(s, "ke", next), s.has("batas_hari") ? Math.max(0, s.get("batas_hari").getAsInt()) : 0,
					DialogNode.str(s, "gagal_ke", GAGAL), DialogNode.str(s, "arah", "")));
		}
		if (list.isEmpty()) {
			return null;
		}
		return new Quest(id, DialogNode.str(json, "pemberi", ""), DialogNode.str(json, "tempat", ""),
				json.has("ulang") && json.get("ulang").getAsBoolean(), json.has("alur") && json.get("alur").getAsBoolean(), List.copyOf(list));
	}
}
