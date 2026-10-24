package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Temple of Heaven of the Scholars' District (DESIGN 38.B.4.4). The paid incense and the Temple Blessing are 38EK's
 * {@code Jasa} (the priest's {@code jasa} screen); the temple itself gives:
 * <ul>
 * <li>a meditation site "Kuil Langit" x1.2 in its courtyard (within 18 blocks of the great incense burner), through
 * {@link SpiritSites#addSiteProvider} like every other extra site;</li>
 * <li>the morning bell at 1000: the priest's line {@code kotabesar.temple_bell} and every townsfolk body within 48 blocks
 * of the temple turning to it and bowing;</li>
 * <li>the incense smoke over the burner on clients ({@link KotaBesarPayload} {@code dupa}, refreshed every 10 s for players
 * within 96 blocks; particles only, under {@code EffectQuality}).</li>
 * </ul>
 */
final class Kuil {

	static final double SITUS_JARAK = 18.0D;
	static final double SITUS_KALI = 1.2D;
	static final double BUNGKUK_JARAK = 48.0D;

	static void init() {
		SpiritSites.addSiteProvider(Kuil::situs);
		TempatEvents.ACARA.register((level, situsId, acara, mulai) -> {
			if ("lonceng_pagi".equals(acara) && mulai) {
				loncengPagi(level, situsId);
			}
		});
	}

	private static @Nullable SpiritSites.ExtraSite situs(ServerPlayer player) {
		if (!KotaBesarConfig.hidup()) {
			return null;
		}
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			if (kota.kuil != null && kota.dimensi == player.level().dimension()
					&& kota.kuil.closerToCenterThan(player.position(), SITUS_JARAK)) {
				return new SpiritSites.ExtraSite("kuil_langit", SITUS_KALI, null, Component.translatable("kultivator.kota_besar.kuil.situs"));
			}
		}
		return null;
	}

	private static void loncengPagi(ServerLevel level, long town) {
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		if (kota == null || kota.kuil == null) {
			return;
		}
		BlockPos temple = kota.kuil;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(temple).inflate(BUNGKUK_JARAK, 16.0D, BUNGKUK_JARAK),
				n -> n.isAlive() && n.getTarget() == null && (n.role().townsfolk() || n.role() == NpcRole.RAHIB))) {
			double dx = temple.getX() + 0.5D - npc.getX();
			double dz = temple.getZ() + 0.5D - npc.getZ();
			npc.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
			npc.setYHeadRot(npc.getYRot());
			HidupApi.klip(npc, npc.role() == NpcRole.RAHIB ? GerakId.SUJUD_DUPA : GerakId.HORMAT, 0, npc.role() == NpcRole.RAHIB ? 90 : 40);
			if (npc.role() == NpcRole.RAHIB) {
				Voice.say(npc, "kotabesar.temple_bell", null);
			}
		}
	}

	/** Every 200 ticks: the smoke over every loaded burner for the players near it. */
	static void tick(MinecraftServer server) {
		for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			if (level == null || kota.kuil == null) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			tag.putDouble("x", kota.kuil.getX() + 0.5D);
			tag.putDouble("y", kota.kuil.getY() + 2.2D);
			tag.putDouble("z", kota.kuil.getZ() + 0.5D);
			tag.putBoolean("aktif", true);
			for (ServerPlayer p : level.players()) {
				if (kota.kuil.closerToCenterThan(p.position(), 96.0D)) {
					KotaBesarPayload.kirim(p, "dupa", tag);
				}
			}
		}
	}

	private Kuil() {
	}
}
