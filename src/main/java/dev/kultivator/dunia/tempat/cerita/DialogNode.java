package dev.kultivator.dunia.tempat.cerita;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of a data dialog (DESIGN 38.S.1): the line the speaker says, the gesture the body plays while saying it, an
 * optional Japanese voice line, effects run when the node is entered and the player's choices.
 *
 * <p>JSON: {@code {id, teks?, isian?[], gestur?, prop?, suara?, akibat?[], pilihan?[{teks?, syarat?[], akibat?[], ke?,
 * tampil?, alasan?}]}}. Text keys default to {@code kultivator.cerita.<dialog>.<node>} and
 * {@code kultivator.cerita.<dialog>.<node>.<n>} for the n-th choice (1-based). {@code teks: "kabar"} makes the node say
 * a live rumour of the place instead ({@link KabarTempat}). {@code isian} names the format arguments of the line
 * ({@link Isian}). A choice without {@code ke} ends the dialog.
 *
 * @param gestur clip name or id ({@code dev.kultivator.sosok.GerakId}), "" none
 * @param prop   prop name or id ({@code dev.kultivator.sosok.PropId}) held while the clip plays, "" none
 * @param suara  voice event key ({@code VoiceEvents}), "" none
 */
public record DialogNode(String id, String teks, List<String> isian, String gestur, String prop, String suara, List<String> akibat,
		List<Pilihan> pilihan) {

	/**
	 * One answer of the player.
	 *
	 * @param no     1-9 (the digit key on the screen)
	 * @param tampil shown locked with {@link #alasan} when a condition fails (else hidden)
	 * @param alasan lang key of the locked reason ("" = the generic one)
	 */
	public record Pilihan(int no, String teks, List<String> syarat, List<String> akibat, @Nullable String ke, boolean tampil, String alasan) {
	}

	/** True when {@link #teks} asks for a live rumour. */
	public boolean kabar() {
		return "kabar".equals(teks);
	}

	static DialogNode parse(String dialogId, JsonObject json) {
		String id = str(json, "id", "mulai");
		String teks = str(json, "teks", "kultivator.cerita." + dialogId + "." + id);
		List<Pilihan> choices = new ArrayList<>();
		JsonArray array = json.has("pilihan") && json.get("pilihan").isJsonArray() ? json.getAsJsonArray("pilihan") : new JsonArray();
		for (int i = 0; i < array.size() && choices.size() < 9; i++) {
			if (!array.get(i).isJsonObject()) {
				continue;
			}
			JsonObject c = array.get(i).getAsJsonObject();
			int no = choices.size() + 1;
			String ke = c.has("ke") && !c.get("ke").isJsonNull() ? c.get("ke").getAsString() : null;
			choices.add(new Pilihan(no, str(c, "teks", "kultivator.cerita." + dialogId + "." + id + "." + no), list(c, "syarat"),
					list(c, "akibat"), ke == null || ke.isEmpty() ? null : ke, c.has("tampil") && c.get("tampil").getAsBoolean(),
					str(c, "alasan", "")));
		}
		return new DialogNode(id, teks, list(json, "isian"), str(json, "gestur", ""), str(json, "prop", ""), str(json, "suara", ""),
				list(json, "akibat"), List.copyOf(choices));
	}

	static String str(JsonObject json, String key, String fallback) {
		JsonElement e = json.get(key);
		return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? fallback : e.getAsString();
	}

	static List<String> list(JsonObject json, String key) {
		JsonElement e = json.get(key);
		if (e == null || e.isJsonNull()) {
			return List.of();
		}
		if (e.isJsonPrimitive()) {
			return List.of(e.getAsString());
		}
		List<String> out = new ArrayList<>();
		if (e.isJsonArray()) {
			for (JsonElement item : e.getAsJsonArray()) {
				if (item.isJsonPrimitive()) {
					out.add(item.getAsString());
				}
			}
		}
		return List.copyOf(out);
	}
}
