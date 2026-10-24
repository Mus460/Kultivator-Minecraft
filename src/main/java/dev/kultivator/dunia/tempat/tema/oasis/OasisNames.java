package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Display names of the Kota Oasis towns (DESIGN 38.T.4): the name of the born town from 38TK inti's
 * {@code KotaTemaNames} (keys {@code kultivator.kota_tema.oasis.nama.a.<i>|.b.<j>}), else the generic name
 * {@code kultivator.kota_tema.oasis.nama_umum} ("Kota Oasis").
 */
final class OasisNames {

	static Component nama(@Nullable MinecraftServer server, long situs) {
		if (server != null && situs != 0L) {
			TemaKota.Kota kota = TemaKota.dari(server, situs);
			if (kota != null) {
				return kota.nama(server.overworld().getSeed());
			}
		}
		return Component.translatable("kultivator.kota_tema.oasis.nama_umum");
	}

	private OasisNames() {
	}
}
