package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The anchors of the NPC sect compounds (module {@code kompleks}, DESIGN 38.C.3; templates of
 * {@code tools/gen_tempat_kompleks.py}): {@code kompleks/situs} (front court: publishes the site {@code sekte/kompleks}
 * once, 38.C.3.3), {@code kompleks/gerbang} (where the court meets the mountain gate: welcome, warning, war),
 * {@code kompleks/aula} (main hall counter), {@code kompleks/arena} (training ground: friendly bouts),
 * {@code kompleks/pasar} (sect market of a neutral sect), {@code kompleks/dalam} (inner court), {@code kompleks/puncak}
 * (the master's seat in the summit hall), {@code kompleks/tetua} (an elder's courtyard) and {@code kompleks/dermaga_awan}
 * (the cloud pier of a sect that owns a sky palace, read by 37 through {@code TempatApi.spot}).
 * <p>
 * On load every anchor remembers its position as the sect's named spot ({@link KompleksData}), so {@code TempatApi.spot}
 * answers while the compound is unloaded. While players are near, the handler runs the visitor's stance
 * ({@link KompleksSikap}), the training ground and the market, and every 5 seconds dresses the compound's resident
 * bodies (38NL) in the sect's uniform: {@code murid.<rank>|sekte=npc:<sect>} through {@link SosokApi#setRupa}.
 * Anchors outside a born compound (a template preview) stay idle.
 */
final class KompleksAnchors implements AnchorHandler {

	/** Spot names an anchor of this kind stores (the anchor's {@code what}). */
	static final Set<String> TITIK = Set.of("gerbang", "aula", "arena", "pasar", "dalam", "puncak", "tetua", "dermaga_awan");
	/** Uniforms are checked this often per anchor (ticks). */
	static final long RUPA_JEDA = 100L;

	/** Anchor UUID -> game time of the last uniform pass. */
	private static final Map<UUID, Long> RUPA = new HashMap<>();
	/** Sect id -> the bodies this module made for it (sparring partners), capped by {@code kompleksSekte.tubuhMaks}. */
	private static final Map<String, List<KultivatorNpc>> TUBUH = new HashMap<>();
	/** Anchor UUID -> the disciple minding the sect's wares at the market lot or the hall counter. */
	private static final Map<UUID, KultivatorNpc> PENJUAL = new HashMap<>();

	static void reset() {
		RUPA.clear();
		TUBUH.clear();
		PENJUAL.clear();
	}

	/** The disciple who keeps the sale point (staff: outside the NPC caps, held at the anchor). */
	private static void penjual(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k) {
		KultivatorNpc npc = PENJUAL.get(anchor.getUUID());
		if (npc != null && npc.isAlive() && !npc.isRemoved()) {
			return;
		}
		npc = HidupApi.tubuhStaf(level, Kompleks.situs(level, k), NpcRole.MURID_SEKTE_DALAM,
				anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			PENJUAL.put(anchor.getUUID(), npc);
		}
	}

	/** Living bodies this module made for sect {@code sekte} (the dead and the gone are dropped). */
	static int tubuhKu(String sekte) {
		List<KultivatorNpc> list = TUBUH.get(sekte);
		if (list == null) {
			return 0;
		}
		list.removeIf(npc -> !npc.isAlive() || npc.isRemoved());
		return list.size();
	}

	static void catatTubuh(String sekte, KultivatorNpc npc) {
		TUBUH.computeIfAbsent(sekte, s -> new java.util.ArrayList<>()).add(npc);
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		RencanaKompleks.Kompleks k = Kompleks.dari(level, anchor);
		if (k == null) {
			return;
		}
		String what = anchor.what();
		BlockPos pos = anchor.blockPosition();
		KompleksData data = KompleksData.get(level.getServer());
		if (what.equals("situs")) {
			BlockPos at = anchor.origin() != null ? anchor.origin() : pos;
			int tier = Math.max(1, SekteApi.diplomasi().tier(k.sekte()));
			DuniaSites.publish(level, RencanaKompleks.JENIS, at, tier);
			data.spot(k.sekte(), "situs", pos);
			return;
		}
		if (TITIK.contains(what)) {
			data.spot(k.sekte(), what, pos);
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!Kompleks.hidup() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		RencanaKompleks.Kompleks k = Kompleks.dari(level, anchor);
		if (k == null) {
			return;
		}
		switch (anchor.what()) {
			case "gerbang" -> KompleksSikap.gerbang(level, anchor, k, within);
			case "dalam", "puncak", "tetua" -> KompleksSikap.dalam(level, anchor, k, within);
			case "arena" -> arena(level, anchor, k, within);
			case "pasar" -> {
				penjual(level, anchor, k);
				PasarSekte.dekat(level, anchor, k, within);
			}
			case "aula" -> {
				if (!anyMarketLot(k)) {
					penjual(level, anchor, k);
					PasarSekte.dekat(level, anchor, k, within);
				}
			}
			default -> {
			}
		}
		if (anchor.what().equals("situs") || anchor.what().equals("dalam") || anchor.what().equals("puncak")) {
			seragam(level, anchor, k);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		RUPA.remove(anchor.getUUID());
		KultivatorNpc npc = PENJUAL.remove(anchor.getUUID());
		if (npc != null && !npc.isRemoved()) {
			npc.discard();
		}
	}

	@Override
	public int radius() {
		return 48;
	}

	/** Only neutral sects have the market lot ({@code pasar_sekte}, 38.C.3.2); the others sell at the hall counter. */
	private static boolean anyMarketLot(RencanaKompleks.Kompleks k) {
		return k.aliran().equals("netral");
	}

	// ------------------------------------------------------------------ training ground

	/** A friend of the sect within 12 blocks of the training ground is offered a friendly bout once per visit. */
	private static void arena(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k, List<ServerPlayer> within) {
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.isCreative() || player.distanceToSqr(anchor) > 12.0D * 12.0D) {
				continue;
			}
			if (!KompleksSikap.RAMAH.contains(KompleksSikap.sikap(player, k.sekte())) || !KompleksSikap.tawarTanding(player, k.sekte(), now)) {
				continue;
			}
			KultivatorNpc partner = KompleksSikap.terdekat(level, anchor, 24.0D);
			Component who = partner != null ? partner.getDisplayName() : Component.translatable("kultivator.kompleks.tanding.murid");
			Component sect = KompleksSikap.namaSekte(k.sekte());
			Kartu.kirim(player, Component.translatable("kultivator.kompleks.tanding.judul", sect),
					List.of(Kartu.ucapan(who, Component.translatable("kultivator.kompleks.tanding.ajak." + k.aliran())),
							Component.translatable("kultivator.kompleks.tanding.aturan")),
					List.of(Kartu.chip(Component.translatable("kultivator.kompleks.tanding.terima"), "/kultivasi kompleks tanding",
							Component.translatable("kultivator.kompleks.tanding.terima.hover"), ChatFormatting.GREEN)));
			if (partner != null) {
				HidupApi.tatap(partner, player);
				partner.bow(30);
			}
		}
	}

	// ------------------------------------------------------------------ uniforms

	/** Dresses the compound's undressed resident bodies near {@code anchor} in the sect's uniform (every 5 s). */
	private static void seragam(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k) {
		long now = level.getGameTime();
		Long last = RUPA.get(anchor.getUUID());
		if (last != null && now - last < RUPA_JEDA) {
			return;
		}
		RUPA.put(anchor.getUUID(), now);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, anchor.getBoundingBox().inflate(64.0D, 24.0D, 64.0D),
				n -> n.isAlive() && n.rupaData().isEmpty() && Kompleks.dalam(k, n.blockPosition()))) {
			String rupa = rupa(npc, k.sekte());
			if (rupa != null) {
				SosokApi.setRupa(npc, rupa);
			}
		}
	}

	/** The look key of a compound resident: disciples by rank, staff in their own look with the sect's uniform colours. */
	static String rupa(KultivatorNpc npc, String sekte) {
		NpcRole r = npc.role();
		int rank = switch (r) {
			case PELAYAN_SEKTE -> 0;
			case MURID_SEKTE_LUAR -> 1;
			case MURID_SEKTE_DALAM -> Math.floorMod(npc.look(), 4) == 0 ? 3 : 2;
			case TETUA_SEKTE -> Math.floorMod(npc.look(), 5) == 0 ? 5 : 4;
			default -> -1;
		};
		if (rank >= 0) {
			return RupaKunci.muridSekteNpc(rank, sekte).format();
		}
		return new RupaKunci(r.id(), "npc:" + sekte, RupaKunci.TIDAK_ADA, RupaKunci.TIDAK_ADA).format();
	}
}
