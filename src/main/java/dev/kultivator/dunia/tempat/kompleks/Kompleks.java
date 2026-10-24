package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Init of 38DS NPC sect compounds (DESIGN 38.C.3): the compound plan behind every NPC sect gate ({@link RencanaKompleks},
 * registered with the 38W plan registry), the anchors' handler ({@link KompleksAnchors}: the site, named spots, the
 * stance of visitors, the training ground, the sect market, sect uniforms on the residents), the named-spot provider of
 * {@code TempatApi.spot}, the friendly bouts ({@link TandingTempat}) and the commands ({@link KompleksCommands}).
 * <p>
 * Registry entries (the plan, structure and placement types of 38W) exist whatever the switches say (38.0 rule 17):
 * {@code kompleksSekte.worldgen} only decides whether the built-in pack {@code kultivator:dunia_kompleks} is registered.
 * {@code diplomasi.strukturAktif} is read at run time by the plan ({@code GerbangPlan.enabled()}): with the gates off the
 * plan has no compound, so the pack's sets place nothing (the sect config loads after this init). Compounds come only from
 * world generation in new chunks; runtime code here never changes a block (38.0 rule 1).
 */
public final class Kompleks {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KompleksConfig config = KompleksConfig.get();
		RencanaKompleks.atur(config.worldgen, config.selisihMaks);
		RencanaRegistry.register(RencanaKompleks.ID, RencanaKompleks.INSTANCE);
		DuniaPacks.worldgen("kompleks", config.worldgen);
		DuniaAnchors.register("kompleks", new KompleksAnchors());
		TempatApi.daftarSpot(KompleksApi::spot);
		TandingTempat.init();
		KompleksCommands.init();
		// story effect for 38ST's sect dialogs: "tanding_sekte" starts a friendly bout with the disciple being talked to
		dev.kultivator.dunia.tempat.cerita.Akibat.daftar("tanding_sekte", (player, args) -> {
			dev.kultivator.dunia.kota.KultivatorNpc npc = dev.kultivator.dunia.tempat.cerita.TempatDialog.npc(player);
			if (npc != null) {
				dev.kultivator.dunia.tempat.cerita.TempatDialog.akhiri(player);
				KompleksCommands.tandingDengan(player, npc);
			}
		});
		DuniaConfig.onReload(() -> {
			KompleksConfig c = KompleksConfig.get();
			RencanaKompleks.atur(c.worldgen, c.selisihMaks);
		});
		ServerLifecycleEvents.SERVER_STARTING.register(KompleksApi::server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KompleksApi.server(null);
			KompleksSikap.reset();
			PasarSekte.reset();
			TandingTempat.reset();
			KompleksAnchors.reset();
			RencanaKompleks.lupakan();
		});
	}

	/** True when compound life runs ({@code kompleksSekte.aktif} and {@code tempat.aktif}). */
	static boolean hidup() {
		return KompleksConfig.get().aktif && dev.kultivator.dunia.tempat.TempatConfig.get().aktif;
	}

	/** The born compound that holds {@code anchor} (overworld), or null. */
	static RencanaKompleks.@Nullable Kompleks dari(ServerLevel level, DuniaAnchorEntity anchor) {
		BlockPos at = anchor.blockPosition();
		return KompleksApi.kompleksDi(level, at);
	}

	/** The place site id of compound {@code k}: the published {@code sekte/kompleks} site, else the plan's synthetic id. */
	static long situs(ServerLevel level, RencanaKompleks.Kompleks k) {
		if (level.dimension() == Level.OVERWORLD) {
			for (DuniaSites.Site s : DuniaSites.near(level, new BlockPos(k.kandidat().pusatX(), 64, k.kandidat().pusatZ()), 300,
					RencanaKompleks.JENIS)) {
				return s.id();
			}
		}
		TempatInfo info = TempatApi.tempatDi(level, new BlockPos(k.kandidat().pusatX(), 64, k.kandidat().pusatZ()));
		return info != null ? info.situsId() : TempatApi.situsRencana(k.kandidat(), RencanaKompleks.JENIS);
	}

	/** True when {@code pos} lies in compound {@code k}'s box. */
	static boolean dalam(RencanaKompleks.Kompleks k, BlockPos pos) {
		return k.kotak().isInside(new BlockPos(pos.getX(), 0, pos.getZ()));
	}

	private Kompleks() {
	}
}
