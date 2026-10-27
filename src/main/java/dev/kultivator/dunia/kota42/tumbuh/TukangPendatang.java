package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.sekte.KotaSekte;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kotabesar.GelombangBinatang;
import dev.kultivator.dunia.tempat.perbaikan.JenisTempat;
import dev.kultivator.fx.Fx;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Migrant builders (DESIGN 42.9.5: "tukang pendatang dari kota tetangga (+1 regu, tubuh berpakaian kota asal) bila proyek
 * &gt; 500 blok"). While a rebuild project above {@link BangunKembali#PENDATANG} blocks runs and a player is near by day, two
 * craftsmen of the nearest other town walk in from the side that town lies on (their dress is the craftsman of that town's
 * kind: the shipwright of a harbour, the quarryman of the mountain town, the plain builder elsewhere; the Devil City's
 * residents stay home), carry planks to the destroyed houses and hammer or plaster there until the sun sets or the
 * project ends. The speed is the x1.33 of {@link BangunKembali#pengali}; these bodies are the part of it people see. Bodies
 * of {@code HidupApi.tubuh} (inside the NPC cap, never saved, {@code hold}); nothing here touches a block.
 */
final class TukangPendatang {

	static final int MAKS = 2;
	private static final double JARAK = 64.0D;
	private static final double JANGKAU = 2.6D;
	/** Place kinds that send builders (cities and large villages; no sect compounds, post inns or the immortal city). */
	private static final List<String> ASAL = List.of("kota/", "kota_tema/", "kampung/desa", "kampung/klan");

	private static final class Regu {
		final List<KultivatorNpc> tamu = new ArrayList<>();
		long berikut;
		int urut;
		boolean sapa;
	}

	private static final Map<Long, Regu> REGU = new HashMap<>();
	/** Entity id -> game time its work spot changes. */
	private static final Map<Integer, Long> GANTI = new HashMap<>();

	/** Every second, for each running project: brings the migrants in and keeps them at work. */
	static void tick(MinecraftServer server, ServerLevel level, long situs) {
		if (!BangunKembali.pendatang(server, situs)) {
			buang(level, situs);
			return;
		}
		List<BoundingBox> lokasi = Pembangun.lokasi(server, situs);
		if (lokasi.isEmpty()) {
			buang(level, situs);
			return;
		}
		BlockPos c = lokasi.get(0).getCenter();
		Player near = level.getNearestPlayer(c.getX(), c.getY(), c.getZ(), JARAK, false);
		if (near == null || level.isDarkOutside()) {
			buang(level, situs);
			return;
		}
		Regu r = REGU.computeIfAbsent(situs, k -> new Regu());
		r.tamu.removeIf(npc -> npc.isRemoved() || !npc.isAlive());
		long now = level.getGameTime();
		if (r.tamu.size() < MAKS && now >= r.berikut) {
			r.berikut = now + 160L;
			datang(server, level, situs, r, c);
		}
		for (int i = 0; i < r.tamu.size(); i++) {
			kerja(level, situs, r, r.tamu.get(i), i, now, lokasi.get(0));
		}
	}

	/** Sends home the migrants of places whose project is over (once a second). */
	static void bersihkan(MinecraftServer server) {
		for (Iterator<Map.Entry<Long, Regu>> it = REGU.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Regu> e = it.next();
			if (!BangunKembali.pendatang(server, e.getKey())) {
				for (KultivatorNpc npc : e.getValue().tamu) {
					pergi(npc);
				}
				it.remove();
			}
		}
	}

	static void lupakan() {
		for (Regu r : REGU.values()) {
			r.tamu.forEach(npc -> {
				if (!npc.isRemoved()) {
					npc.discard();
				}
			});
		}
		REGU.clear();
		GANTI.clear();
	}

	/** Migrant bodies standing at {@code situs} now (tests and the project sheet). */
	static int jumlah(long situs) {
		Regu r = REGU.get(situs);
		return r == null ? 0 : (int) r.tamu.stream().filter(n -> !n.isRemoved()).count();
	}

	private static void buang(ServerLevel level, long situs) {
		Regu r = REGU.remove(situs);
		if (r != null) {
			r.tamu.forEach(TukangPendatang::pergi);
		}
	}

	private static void pergi(KultivatorNpc npc) {
		GANTI.remove(npc.getId());
		if (!npc.isRemoved()) {
			if (npc.level() instanceof ServerLevel level) {
				Fx.burst(level, npc.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 6, 0.4F);
			}
			npc.discard();
		}
	}

	// ================================================================== arrival

	/** The nearest other town that can spare builders, or null. */
	private static DuniaSites.@Nullable Site asal(MinecraftServer server, ServerLevel level, long situs, BlockPos dari) {
		for (DuniaSites.Site s : DuniaSites.near(level, dari, 2600, "")) {
			if (s.id() == situs || s.kind().equals("kota/kota_abadi")) {
				continue;
			}
			for (String prefix : ASAL) {
				if (s.kind().startsWith(prefix)) {
					return s;
				}
			}
		}
		return null;
	}

	/** The craftsman of a place kind in that town's dress (its own repair crews' role), never the Devil City's. */
	static NpcRole peranDari(@Nullable String kind) {
		NpcRole role = kind == null ? NpcRole.TUKANG : JenisTempat.of(kind).tukang();
		return role == NpcRole.WARGA_IBLIS || role.tier > 1 ? NpcRole.TUKANG : role;
	}

	private static void datang(MinecraftServer server, ServerLevel level, long situs, Regu r, BlockPos pusat) {
		DuniaSites.Site asal = asal(server, level, situs, pusat);
		NpcRole role = peranDari(asal == null ? null : asal.kind());
		BlockPos spot = pintuMasuk(level, situs, pusat, asal == null ? null : asal.pos());
		if (spot == null) {
			return;
		}
		int look = (int) (situs * 31L + r.urut * 977L + level.getServer().getTickCount() / 24000L) * 0x9E3779B9;
		KultivatorNpc npc = HidupApi.tubuh(level, situs, role, spot, 0.0F, look);
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("hold", true);
		npc.memory().putBoolean("perbaikan", true);
		r.tamu.add(npc);
		if (!r.sapa && asal != null) {
			r.sapa = true;
			BangunKembali.siar(level, situs, Component.translatable("kultivator.kota42.tumbuh.bangun.tiba", KotaSekte.namaTempat(server, asal.id()))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Where migrants walk in: the edge of the walled place on the side of their town (else a side by the situs). */
	private static @Nullable BlockPos pintuMasuk(ServerLevel level, long situs, BlockPos pusat, @Nullable BlockPos dariMana) {
		BoundingBox box = GelombangBinatang.kotakTempat(level, situs);
		if (box == null) {
			return null;
		}
		BlockPos c = box.getCenter();
		double dx = dariMana == null ? (Math.floorMod(situs, 2L) * 2.0D - 1.0D) : dariMana.getX() - c.getX();
		double dz = dariMana == null ? 0.0D : dariMana.getZ() - c.getZ();
		double len = Math.max(1.0D, Math.hypot(dx, dz));
		double ux = dx / len;
		double uz = dz / len;
		// the scale at which the ray from the centre leaves the box (plus two blocks)
		double s = Math.min(Math.abs(ux) < 1.0E-6D ? Double.MAX_VALUE : (box.getXSpan() / 2.0D + 2.0D) / Math.abs(ux),
				Math.abs(uz) < 1.0E-6D ? Double.MAX_VALUE : (box.getZSpan() / 2.0D + 2.0D) / Math.abs(uz));
		int x = (int) Math.round(c.getX() + ux * s);
		int z = (int) Math.round(c.getZ() + uz * s);
		BlockPos spot = TempatApi.groundSpot(level, x, z, pusat.getY());
		return spot != null ? spot : pusat;
	}

	// ================================================================== at work

	private static void kerja(ServerLevel level, long situs, Regu r, KultivatorNpc npc, int i, long now, BoundingBox box) {
		Long ganti = GANTI.get(npc.getId());
		BlockPos home = npc.home();
		if (home == null || ganti == null || now >= ganti) {
			BlockPos spot = Pembangun.titikKerja(level, situs, r.urut++ * 2 + i);
			if (spot != null) {
				npc.setHome(spot);
				home = spot;
				GANTI.put(npc.getId(), now + 600L + level.getRandom().nextInt(400));
			}
		}
		boolean sana = home != null && npc.position().distanceTo(Vec3.atBottomCenterOf(home)) <= JANGKAU;
		if (!sana) {
			HidupApi.klip(npc, GerakId.ANGKAT_PAPAN, PropId.PAPAN_KAYU, 0);
			return;
		}
		BlockPos c = box.getCenter();
		npc.getLookControl().setLookAt(c.getX() + 0.5D, c.getY() + 1.0D, c.getZ() + 0.5D);
		boolean palu = (i + now / 1200L) % 2L == 0L;
		HidupApi.klip(npc, palu ? GerakId.PERBAIKI_PALU : GerakId.PERBAIKI_PLESTER, palu ? PropId.PALU : PropId.SENDOK_PLESTER, 0);
	}

	private TukangPendatang() {
	}
}
