package dev.kultivator.dunia.tempat.cerita;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A data dialog {@code data/kultivator/tempat/dialog/<id>.json} (DESIGN 38.S.1).
 *
 * <p>JSON: {@code {pembicara: "tokoh:<id>" | "peran:<role id>" | [..], prioritas?, syarat?[], sekali?, mulai?,
 * simpul: [DialogNode...]}}. The dialog is offered to a player who talks to a matching NPC when every condition of
 * {@code syarat} holds; among the offered dialogs the highest {@code prioritas} wins (ties: the one named first in file
 * order). {@code sekali} dialogs set flag {@code dialog.<id>} when they end and are not offered again.
 *
 * @param pembicara who may say it: {@code tokoh:<id>} (a recurring character, 38.S.2) or {@code peran:<role id>}
 */
public record Dialog(String id, List<String> pembicara, int prioritas, List<String> syarat, boolean sekali, String mulai,
		Map<String, DialogNode> simpul) {

	public @Nullable DialogNode node(String nodeId) {
		return nodeId == null ? null : simpul.get(nodeId);
	}

	public @Nullable DialogNode awal() {
		return simpul.get(mulai);
	}

	/** Flag set when a {@link #sekali} dialog ended. */
	public String benderaSekali() {
		return "dialog." + id;
	}

	/** True when this dialog may be said by tokoh {@code tokoh} ("" none) of role id {@code peran}. */
	public boolean pembicara(String tokoh, String peran) {
		for (String who : pembicara) {
			if (!tokoh.isEmpty() && who.equals("tokoh:" + tokoh) || who.equals("peran:" + peran)) {
				return true;
			}
		}
		return false;
	}

	static @Nullable Dialog parse(String id, JsonObject json) {
		Map<String, DialogNode> nodes = new LinkedHashMap<>();
		JsonElement list = json.get("simpul");
		if (list == null || !list.isJsonArray()) {
			return null;
		}
		for (JsonElement e : (JsonArray) list) {
			if (e.isJsonObject()) {
				DialogNode node = DialogNode.parse(id, e.getAsJsonObject());
				nodes.put(node.id(), node);
			}
		}
		if (nodes.isEmpty()) {
			return null;
		}
		String start = DialogNode.str(json, "mulai", nodes.keySet().iterator().next());
		int priority = json.has("prioritas") ? json.get("prioritas").getAsInt() : 0;
		boolean once = json.has("sekali") && json.get("sekali").getAsBoolean();
		return new Dialog(id, DialogNode.list(json, "pembicara"), priority, DialogNode.list(json, "syarat"), once, start, Map.copyOf(nodes));
	}
}
