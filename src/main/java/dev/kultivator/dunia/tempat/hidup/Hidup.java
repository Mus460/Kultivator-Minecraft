package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.TempatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;

/**
 * Init of 38NL's server package {@code dunia/tempat/hidup} (DESIGN 38.N), called once from {@code Tempat.init()} on both
 * physical sides. Registers, always (38.0 rule 17): the config section {@code tempatHidup}, the schedule data loader
 * ({@link JadwalData}), the far-crowd payload ({@link PendudukSnapshot}), the body caller ({@link Pemanggil}), the
 * {@code uji hidup} commands ({@link HidupCommands}) and the event hooks:
 * <ul>
 *   <li>{@code TempatEvents.ACARA}: an event of a place overrides its schedule while it runs (festival, auction,
 *       tournament, beast wave; 38KS and 38TK fire it);</li>
 *   <li>{@code TempatEvents.KEJAHATAN}: witnesses point, shout and run to a guard ({@link TubuhPenduduk#kejahatan});</li>
 *   <li>a lightning bolt of the vanilla weather (read when it loads, never made): children jump and hide, adults flinch
 *       ({@link TubuhPenduduk#petir});</li>
 *   <li>a resident struck by a player marks danger there for 60 s; badly hurt, it stays indoors for a day; killed, a
 *       newcomer takes its place after three days ({@link PendudukData}).</li>
 * </ul>
 * The switches of {@link HidupConfig} gate the behaviour only.
 */
public final class Hidup {
	private static boolean ready;

	private Hidup() {
	}

	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		HidupConfig.get();
		JadwalData.register();
		PendudukSnapshot.register();
		Pemanggil.init();
		HidupCommands.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HidupApi.lupakan());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Pemanggil.lupakanPemain(handler.getPlayer().getUUID()));
		TempatEvents.ACARA.register((level, situsId, acara, mulai) -> {
			long now = level.getGameTime();
			if (mulai) {
				HidupApi.timpaJadwal(situsId, acara, now + 24000L);
			} else if (acara.equals(HidupApi.timpaan(situsId, now))) {
				HidupApi.timpaJadwal(situsId, "", 0L);
			}
			TempatHuni huni = Pemanggil.huni(situsId);
			if (huni != null) {
				for (KultivatorNpc body : huni.tubuh.values()) {
					TubuhPenduduk.segarkan(body);
				}
			}
		});
		TempatEvents.KEJAHATAN.register((player, town, jenis) -> TubuhPenduduk.kejahatan(player, town));
		// a lightning bolt of the vanilla weather is only read (the mod never makes one): the town flinches
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt && !Pemanggil.semua().isEmpty() && HidupConfig.get().reaksiCuaca) {
				TubuhPenduduk.petir(level, entity.blockPosition());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Hidup::luka);
		ServerLivingEntityEvents.AFTER_DEATH.register(Hidup::mati);
	}

	private static void luka(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof KultivatorNpc npc) || !(npc.level() instanceof ServerLevel level) || !TubuhPenduduk.adalah(npc) || damageTaken <= 0.0F) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer) {
			HidupApi.bahaya(level, npc.blockPosition(), 60);
		}
		if (npc.isAlive() && npc.getHealth() < npc.getMaxHealth() * 0.3F) {
			PendudukData.get(level.getServer()).luka(npc.situsPenduduk(), npc.pendudukId(), level.getGameTime() + 24000L);
		}
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof KultivatorNpc npc) || !(npc.level() instanceof ServerLevel level) || !TubuhPenduduk.adalah(npc)) {
			return;
		}
		long situs = npc.situsPenduduk();
		int id = npc.pendudukId();
		TempatHuni huni = Pemanggil.huni(situs);
		Penduduk dead = huni != null ? huni.orang(id) : null;
		int rumahTangga = dead != null && !dead.tamu() ? dead.kelompok() : 0;
		PendudukData.get(level.getServer()).mati(situs, id, rumahTangga, level.getGameTime());
		if (huni != null) {
			huni.tubuh.remove(id);
			huni.lepas(id);
		}
		HidupApi.bahaya(level, npc.blockPosition(), 90);
	}
}
