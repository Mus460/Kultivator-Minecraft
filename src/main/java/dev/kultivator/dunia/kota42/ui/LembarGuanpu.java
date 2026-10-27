package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.acara.Guanpu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Guanpu.*;

/**
 * {@code guanpu} (DESIGN 42.11.10, keys {@link KotaLembar.Guanpu}): the registration of the dice table screen. The rules,
 * the stakes, the daily limit, the prizes and the sheet itself belong to 42D ({@link Guanpu#bangun} and
 * {@link Guanpu#aksi}: Besar-Kecil at 0.85 : 1 with 105 of 216 winning, the named triple at 150 : 1, the prize throws at
 * 1/8 of the value won on a sum of 15 or more); this class only puts them into the shared registry of
 * {@link LembarKotaDasar} (so the town header of {@code kepala} and the pending result line are added like on every other
 * 42 sheet) and the client draws the sheet as the dice tab of the betting screen ({@code LayarTaruhan}).
 */
public final class LembarGuanpu {

	static void init() {
		LembarKotaDasar.daftar(LAYAR, LembarGuanpu::bangun);
		for (String verb : new String[] {OP_TARUH, OP_LEMPAR, OP_LEMPAR_GUANPU}) {
			LembarKotaDasar.bawaan(LAYAR, verb, (p, npc, situs, op) -> Guanpu.aksi(p, npc, op));
		}
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		CompoundTag tag = Guanpu.bangun(player, npcId);
		if (tag != null && !tag.contains(dev.kultivator.dunia.tempat.ui.LembarKunci.SITUS)) {
			LembarKotaDasar.kepala(tag, player, situs);
		}
		return tag;
	}

	private LembarGuanpu() {
	}
}
