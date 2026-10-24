package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaSitus;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;

/**
 * Kota Gunung (DESIGN 38.T.6): the init of this theme package, called by {@code KotaTema.init()} on both physical sides.
 * Registers the anchor module {@code tema_gunung} ({@link GunungAnchors}), the cable car ({@link KeretaGantung}), the
 * pilgrimage ({@link Ziarah}, Restu Puncak as the condition of the Aula Emas meditation site), the calendar
 * ({@link GunungAcara}: Musim Ziarah, Fajar Cahaya Budha, Longsor, Lift Macet), the porter service ({@link JasaKuli}),
 * the screens and dialog services ({@link GunungLayar}), the story hooks ({@link GunungCerita}) and the test word
 * {@code uji tema lift} ({@link GunungCommands}); the Tali Panjat fall guard. Shared state through
 * {@code KotaTemaData.bagian("gunung")} ({@link GunungData}); never edits 38TK inti files.
 * <p>
 * Rules: no block ever changes at runtime (the town is worldgen only; every moving part is a noSave entity), never the
 * weather, time or tick rate; every switch of {@code TemaConfig.gunung()} only gates behaviour.
 */
public final class Gunung {

	private static boolean initialised;

	/** From {@code KotaTema.init()} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GunungAnchors.init();
		KeretaGantung.init();
		Ziarah.init();
		GunungAcara.init();
		JasaKuli.init();
		GunungLayar.init();
		GunungCerita.init();
		GunungCommands.init();
		TemaSitus.daftarSyarat(Tema.GUNUNG, Ziarah::restuAktif);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && player.level() instanceof ServerLevel level) {
				CompoundTag p = GunungData.pemain(player);
				if (p.getBooleanOr("tp", false) && GunungTata.kotaDi(level, player.blockPosition()) != null) {
					p.putBoolean("tp", false);
					GunungData.ubah(level.getServer());
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.herba.tali_selamat").withStyle(ChatFormatting.AQUA));
					return false;
				}
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GunungAnchors.reset();
			KeraGunungEntity.reset();
			GunungLayar.reset();
		});
	}

	private Gunung() {
	}
}
