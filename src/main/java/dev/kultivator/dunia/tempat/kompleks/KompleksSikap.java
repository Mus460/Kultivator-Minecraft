package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How an NPC sect compound treats a visitor (DESIGN 38.C.3.3), by the stance of the visitor toward the sect
 * ({@link DiplomasiService#stance}; the standing is kept per player UUID, the same key a hall master uses):
 * <ul>
 *   <li><b>SEKUTU / RAMAH</b>: welcomed at the gate, the inner courts and the summit are open, a friendly bout on the
 *       training ground ({@link #RAMAH}), the sect market at a discount;</li>
 *   <li><b>NETRAL / DINGIN</b>: the front court only; lingering in the inner courts costs {@code kompleksSekte.masukDalam}
 *       standing every 30 seconds after one warning;</li>
 *   <li><b>BERMUSUHAN</b>: the gate guards warn; walking on into the court costs {@code kompleksSekte.masukBermusuhan}
 *       standing once per visit;</li>
 *   <li><b>PERANG</b>: the guards and disciples turn on the visitor ({@code KultivatorNpc.markHostile} and
 *       {@link TechniquePower#markRealmFoe}); the master waits at the summit hall when module D sends him there through
 *       {@code TempatApi.spot(sekte, "puncak")}.</li>
 * </ul>
 * A visit ends when the visitor has been away from every anchor of the compound for five minutes. Memory only (a restart
 * starts new visits); the standing changes themselves are D's saved data.
 */
final class KompleksSikap {

	/** Stances that count as friends. */
	static final Set<Sikap> RAMAH = Set.of(Sikap.SEKUTU, Sikap.RAMAH);
	/** A visit is over after this long away (ticks). */
	static final long KUNJUNGAN_HABIS = 20L * 60L * 5L;
	/** Lingering in the inner courts is charged this often (ticks). */
	static final long DALAM_JEDA = 20L * 30L;

	/** One visit of one player to one compound. */
	private static final class Kunjungan {
		final String sekte;
		long terakhir;
		boolean disambut;
		boolean diperingatkan;
		boolean didenda;
		boolean dalamDiperingatkan;
		long dalamTagih;
		boolean musuhDitandai;
		boolean tandingDitawar;
		boolean pasarDitawar;

		Kunjungan(String sekte, long now) {
			this.sekte = sekte;
			this.terakhir = now;
		}
	}

	private static final Map<UUID, Kunjungan> KUNJUNGAN = new HashMap<>();

	static void reset() {
		KUNJUNGAN.clear();
	}

	/** The visit of {@code player} to the compound of {@code sekte} at game time {@code now} (a new one after a pause). */
	static Kunjungan kunjungan(ServerPlayer player, String sekte, long now) {
		Kunjungan k = KUNJUNGAN.get(player.getUUID());
		if (k == null || !k.sekte.equals(sekte) || now - k.terakhir > KUNJUNGAN_HABIS) {
			k = new Kunjungan(sekte, now);
			KUNJUNGAN.put(player.getUUID(), k);
		}
		k.terakhir = now;
		return k;
	}

	static Sikap sikap(ServerPlayer player, String sekte) {
		return SekteApi.diplomasi().stance(player.getUUID(), sekte);
	}

	/** The key of a greeting told in the compound's own voice: {@code <key>.<aliran>.<0|1>}. */
	static String salam(String key, RencanaKompleks.Kompleks k, ServerLevel level) {
		return "kultivator.kompleks." + key + "." + k.aliran() + "." + level.getRandom().nextInt(2);
	}

	static Component namaSekte(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name != null ? name : Component.literal(sekte);
	}

	// ------------------------------------------------------------------ the gate

	/** Every second while players are near the gate anchor {@code kompleks/gerbang} of compound {@code k}. */
	static void gerbang(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k, List<ServerPlayer> within) {
		long now = level.getGameTime();
		BoundingBox court = RencanaKompleks.kotakDistrik(k, RencanaKompleks.petak(k.entry().band()).getFirst(), 0, 0);
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.distanceToSqr(anchor) > 28.0D * 28.0D && !court.isInside(player.blockPosition())) {
				continue;
			}
			Kunjungan v = kunjungan(player, k.sekte(), now);
			Sikap s = sikap(player, k.sekte());
			Component sect = namaSekte(k.sekte());
			switch (s) {
				case SEKUTU, RAMAH -> {
					if (!v.disambut) {
						v.disambut = true;
						SekteApi.diplomasi().meet(player.getUUID(), k.sekte());
						Msg.title(player, sect, Component.translatable("kultivator.kompleks.sambut.sekutu"), 10, 50, 15);
						Msg.chat(player, Component.translatable(salam("sambut.sekutu.chat", k, level), sect).withStyle(ChatFormatting.GREEN));
						tunduk(level, anchor, player);
					}
				}
				case NETRAL, DINGIN -> {
					if (!v.disambut) {
						v.disambut = true;
						SekteApi.diplomasi().meet(player.getUUID(), k.sekte());
						Msg.title(player, sect, Component.translatable("kultivator.kompleks.sambut.netral"), 10, 50, 15);
						Msg.chat(player, Component.translatable(salam("sambut.netral.chat", k, level), sect).withStyle(ChatFormatting.GRAY));
					}
				}
				case BERMUSUHAN -> {
					if (!v.diperingatkan) {
						v.diperingatkan = true;
						Msg.title(player, sect, Component.translatable("kultivator.kompleks.peringatan").withStyle(ChatFormatting.RED), 10, 60, 15);
						Msg.chat(player, Component.translatable(salam("peringatan.chat", k, level), sect).withStyle(ChatFormatting.RED));
						level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0F, 0.8F);
						hunus(level, anchor, player);
					} else if (!v.didenda && court.isInside(player.blockPosition()) && player.distanceToSqr(anchor) > 10.0D * 10.0D) {
						v.didenda = true;
						int n = KompleksConfig.get().masukBermusuhan;
						if (n > 0) {
							SekteApi.diplomasi().adjust(player.getUUID(), k.sekte(), -n, "kompleks_masuk");
						}
						Msg.chat(player, Component.translatable("kultivator.kompleks.masuk_musuh", sect, n).withStyle(ChatFormatting.RED));
					}
				}
				case PERANG -> perang(level, anchor, k, player, v);
			}
		}
	}

	// ------------------------------------------------------------------ inner courts and summit

	/** Every second near the {@code kompleks/dalam} or {@code kompleks/puncak} anchor: only friends may stay. */
	static void dalam(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k, List<ServerPlayer> within) {
		long now = level.getGameTime();
		KompleksConfig config = KompleksConfig.get();
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.isCreative() || player.distanceToSqr(anchor) > 40.0D * 40.0D) {
				continue;
			}
			Kunjungan v = kunjungan(player, k.sekte(), now);
			Sikap s = sikap(player, k.sekte());
			if (s == Sikap.PERANG) {
				perang(level, anchor, k, player, v);
				continue;
			}
			if (RAMAH.contains(s)) {
				continue;
			}
			Component sect = namaSekte(k.sekte());
			if (!v.dalamDiperingatkan) {
				v.dalamDiperingatkan = true;
				v.dalamTagih = now + DALAM_JEDA;
				Msg.chat(player, Component.translatable(salam("dalam.peringatan", k, level), sect).withStyle(ChatFormatting.YELLOW));
				hunus(level, anchor, player);
			} else if (now >= v.dalamTagih) {
				v.dalamTagih = now + DALAM_JEDA;
				int n = s == Sikap.BERMUSUHAN ? config.masukDalam * 2 : config.masukDalam;
				if (n > 0) {
					SekteApi.diplomasi().adjust(player.getUUID(), k.sekte(), -n, "kompleks_dalam");
					Msg.actionBar(player, Component.translatable("kultivator.kompleks.dalam.denda", sect, n).withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	// ------------------------------------------------------------------ war

	/** A visitor at war: once per visit every sect body of the compound turns on him. */
	private static void perang(ServerLevel level, DuniaAnchorEntity anchor, RencanaKompleks.Kompleks k, ServerPlayer player, Kunjungan v) {
		if (v.musuhDitandai || player.isCreative()) {
			return;
		}
		v.musuhDitandai = true;
		Component sect = namaSekte(k.sekte());
		Msg.title(player, sect, Component.translatable("kultivator.kompleks.perang").withStyle(ChatFormatting.DARK_RED), 5, 60, 15);
		Msg.chat(player, Component.translatable("kultivator.kompleks.perang.chat", sect).withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
		HidupApi.bahaya(level, anchor.blockPosition(), 60);
		AABB box = AABB.of(k.kotak()).deflate(0.0D, 0.0D, 0.0D);
		AABB near = player.getBoundingBox().inflate(64.0D, 24.0D, 64.0D).intersect(box);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, near, KompleksSikap::anggota)) {
			npc.markHostile(player);
			TechniquePower.markRealmFoe(npc);
		}
	}

	/** True for the bodies that belong to a compound (sect roles and the compound's guards). */
	static boolean anggota(KultivatorNpc npc) {
		NpcRole r = npc.role();
		return npc.isAlive() && (r == NpcRole.MURID_SEKTE_LUAR || r == NpcRole.MURID_SEKTE_DALAM || r == NpcRole.TETUA_SEKTE
				|| r == NpcRole.PENJAGA);
	}

	// ------------------------------------------------------------------ small gestures

	/** The nearest sect body bows to a welcomed friend. */
	private static void tunduk(ServerLevel level, DuniaAnchorEntity anchor, ServerPlayer player) {
		KultivatorNpc npc = terdekat(level, anchor, 20.0D);
		if (npc != null) {
			HidupApi.tatap(npc, player);
			npc.bow(40);
		}
	}

	/** The nearest sect body turns to the visitor and lays a hand on its sword. */
	private static void hunus(ServerLevel level, DuniaAnchorEntity anchor, ServerPlayer player) {
		KultivatorNpc npc = terdekat(level, anchor, 20.0D);
		if (npc != null) {
			HidupApi.tatap(npc, player);
			npc.arm(true);
			npc.gaze(60);
		}
	}

	static @Nullable KultivatorNpc terdekat(ServerLevel level, DuniaAnchorEntity anchor, double r) {
		KultivatorNpc best = null;
		double bestD = r * r;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, anchor.getBoundingBox().inflate(r), KompleksSikap::anggota)) {
			double d = npc.distanceToSqr(anchor);
			if (d < bestD && !TandingTempat.sibuk(npc)) {
				bestD = d;
				best = npc;
			}
		}
		return best;
	}

	/** True once per visit: offer the sparring card. */
	static boolean tawarTanding(ServerPlayer player, String sekte, long now) {
		Kunjungan v = kunjungan(player, sekte, now);
		if (v.tandingDitawar) {
			return false;
		}
		v.tandingDitawar = true;
		return true;
	}

	/** True once per visit: offer the market card. */
	static boolean tawarPasar(ServerPlayer player, String sekte, long now) {
		Kunjungan v = kunjungan(player, sekte, now);
		if (v.pasarDitawar) {
			return false;
		}
		v.pasarDitawar = true;
		return true;
	}

	private KompleksSikap() {
	}
}
