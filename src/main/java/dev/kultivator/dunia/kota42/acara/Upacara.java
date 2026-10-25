package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.warga.Warga;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Resident ceremonies (DESIGN 42.7.7, contract C42.D1): weddings, funerals, red eggs, the one-month feast and zhuazhou,
 * scheduled by 42A's life cycle for a following day at the fixed hours of {@link JenisUpacara}, played with real resident
 * bodies, the {@code usungan_peti} / {@code meja_jamuan} entities and the red {@code TanduEntity}, sent to clients as
 * {@code kota_upacara}. Never changes a block, never kills.
 * <p>
 * Skeleton of 42A0: refuses until 42D lands.
 */
public final class Upacara {

	/**
	 * Schedules a ceremony of {@code jenis} for {@code warga} (wedding: groom, bride; funeral: the dead, then family;
	 * births: baby, mother, father). Returns the ceremony id, or -1 when refused.
	 */
	public static int jadwalkan(JenisUpacara jenis, Warga... warga) {
		return -1;
	}

	// stub F42-2-kompil: 42D-P must finish Upacara (KotaBus NIKAH/WAFAT/LAHIR scheduling, processions, click/attack choices)
	static void init() {
	}

	// stub F42-2-kompil: 42D-P must finish the right-click / shift-right-click choices on ceremony NPCs and entities
	static boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
		return false;
	}

	// stub F42-2-kompil: 42D-P must finish the attack choices on ceremony NPCs and entities
	static boolean pukul(ServerLevel level, ServerPlayer player, Entity e) {
		return false;
	}

	private Upacara() {
	}
}
