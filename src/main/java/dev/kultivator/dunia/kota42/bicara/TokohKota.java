package dev.kultivator.dunia.kota42.bicara;

import com.google.gson.JsonObject;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Named town characters (DESIGN 42.5 / 42.10): the {@code tempat/tokoh/*.json} registry cast onto residents of the first
 * capital and the first theme city, their tagged bodies, names, titles.
 * <p>
 * stub F42-2-kompil: 42B must finish TokohKota (step 8 of m2-42B); until then nobody is cast and names are the plain
 * resident names.
 */
public final class TokohKota {

	static void init() {
	}

	/** Datapack reload: tokoh id -> json. stub F42-2-kompil: ignored. */
	static void muat(Map<String, JsonObject> tokoh) {
	}

	/** Daily step: casts characters in place {@code s}. stub F42-2-kompil: does nothing. */
	static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
	}

	static void tick(MinecraftServer server) {
	}

	static void lupakanSemua() {
	}

	/** Tags a named character's body so its 38 data dialogs apply. stub F42-2-kompil: does nothing. */
	static void tandaiTubuh(KultivatorNpc npc, Warga w) {
	}

	/** The display name of {@code w} (a character's own name once cast). stub F42-2-kompil: the resident name. */
	static Component nama(Warga w) {
		return WargaApi.nama(w);
	}

	/** A named character's title, or null. stub F42-2-kompil: always null. */
	static @Nullable Component gelar(Warga w) {
		return null;
	}

	/** {@code uji kota_hidup tokoh} lines. stub F42-2-kompil: one line saying nothing is cast. */
	static List<Component> laporan(MinecraftServer server) {
		return List.of(Component.literal("TokohKota: stub F42-2-kompil, no characters cast yet"));
	}

	private TokohKota() {
	}
}
