package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A street brawl (DESIGN 42.7.4 "perkelahian"): two residents come to blows: sworn enemies ({@code MUSUH}) first, else the
 * night drinker and a man near him, else two men outside the tavern. They meet halfway and trade tavern punches in step
 * (clips 387/388 {@code tinju_kedai_a/b}, one synced start) while up to eight residents crowd round and cheer
 * ({@code HidupApi.kerumunan}). After {@value #LAMA} ticks a guard comes and breaks it up. Nobody dies: the loser kneels.
 * <p>
 * Players choose with the mouse, no screen (an action-bar hint says how):
 * <ul>
 * <li><b>right-click</b> a brawler = Lerai (calm them): sure two realms above the stronger brawler, else
 * 45 % + 10 % per realm. Both stop and bow; the one who started it thinks a little less of the player, the other more;
 * the town is calmer for a day (+5).</li>
 * <li><b>shift + right-click</b> = Taruhan on that brawler: stake 2 x (band + 1) NB, a win pays 1.8 x (EV 0.9); a calmed
 * brawl refunds.</li>
 * <li><b>attack</b> a brawler = Ikut (join in): Nama Baik -3, the struck one remembers being hit and goes down, the fight
 * ends.</li>
 * </ul>
 * The town gossips {@code BERKELAHI} about the pair either way; both stay angry for a while ({@code Suasana.marah}).
 */
final class Perkelahian extends PeristiwaJalan.Kejadian {

	static final int LAMA = 800;
	static final int PENJAGA_MAKS = 600;
	static final double BAYAR = 1.8D;

	private record Taruhan(boolean padaA, long taruh) {
	}

	private final Warga a;
	private final Warga b;
	private final BlockPos pusat;
	private final BlockPos posA;
	private final BlockPos posB;
	private final Map<UUID, Taruhan> taruhan = new HashMap<>();
	private final Map<UUID, Long> leraiT = new HashMap<>();
	private long t0 = -1L;
	private long tanpaTubuh;
	private @Nullable KultivatorNpc penjaga;
	private long penjagaT;
	private boolean selesai;

	private Perkelahian(TempatHuni huni, long now, Warga a, Warga b, BlockPos pusat) {
		super(PeristiwaJalan.PERKELAHIAN, huni, now);
		this.a = a;
		this.b = b;
		this.pusat = pusat;
		this.posA = pusat.offset(-1, 0, 0);
		this.posB = pusat.offset(1, 0, 0);
	}

	// ================================================================== start

	static @Nullable Perkelahian mulai(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
		List<KultivatorNpc> bodies = PeristiwaJalan.tubuhDekat(level, huni.situsId, player.position(), 32.0D);
		Warga a = null;
		Warga b = null;
		// 1. sworn enemies both out in the street
		for (KultivatorNpc npc : bodies) {
			Warga w = WargaApi.dari(npc);
			Warga foe = w == null || !layak(w) ? null : PeristiwaJalan.musuhDari(w);
			if (foe != null && layak(foe) && PeristiwaJalan.tubuh(huni, foe) != null) {
				a = w;
				b = foe;
				break;
			}
		}
		// 2. the drunk and the nearest man
		if (a == null) {
			for (KultivatorNpc npc : bodies) {
				Warga w = WargaApi.dari(npc);
				if (w != null && layak(w) && w.slot != Warga.TANPA_SLOT && huni.keadaanDari(w.slot) == PendudukData.MABUK) {
					Warga other = priaDekat(huni, bodies, npc, w);
					if (other != null) {
						a = w;
						b = other;
						break;
					}
				}
			}
		}
		// 3. two men near the tavern (or anywhere close together)
		if (a == null) {
			List<Titik> kedai = PeristiwaJalan.titik(huni, "kerja:teko", "meja_makan");
			for (KultivatorNpc npc : bodies) {
				Warga w = WargaApi.dari(npc);
				// the hot-tempered always qualify, the drunk usually, the placid half of the time (Kebiasaan.faktorKelahi)
				if (w == null || !layak(w) || !w.pria() || level.getRandom().nextDouble() * 2.0D >= dev.kultivator.dunia.kota42.warga.Kebiasaan.faktorKelahi(w)) {
					continue;
				}
				boolean dekatKedai = kedai.isEmpty();
				for (Titik t : kedai) {
					if (t.pos().distSqr(npc.blockPosition()) < 14.0D * 14.0D) {
						dekatKedai = true;
						break;
					}
				}
				Warga other = dekatKedai ? priaDekat(huni, bodies, npc, w) : null;
				if (other != null) {
					a = w;
					b = other;
					break;
				}
			}
		}
		if (a == null || b == null) {
			return null;
		}
		KultivatorNpc ta = PeristiwaJalan.tubuh(huni, a);
		KultivatorNpc tb = PeristiwaJalan.tubuh(huni, b);
		if (ta == null || tb == null) {
			return null;
		}
		BlockPos mid = PeristiwaJalan.berdiri(level, BlockPos.containing(ta.position().lerp(tb.position(), 0.5D)));
		if (mid == null) {
			return null;
		}
		Perkelahian k = new Perkelahian(huni, now, a, b, mid);
		long sampai = now + LAMA + PENJAGA_MAKS + 100L;
		PeristiwaJalan.tugas(a, huni.situsId, PeristiwaJalan.di("berkelahi", k.posA, KotaGerakId.TINJU_KEDAI_A, 0), sampai);
		PeristiwaJalan.tugas(b, huni.situsId, PeristiwaJalan.di("berkelahi", k.posB, KotaGerakId.TINJU_KEDAI_B, 0), sampai);
		HidupApi.kerumunan(level, mid, (LAMA + PENJAGA_MAKS) / 20);
		MinecraftServer server = level.getServer();
		Suasana.marah(server, a, now);
		Suasana.marah(server, b, now);
		HidupApi.klip(ta, GerakId.MARAH_TUNJUK, 0, 40);
		PeristiwaJalan.seru(ta, null, "kelahi.tantang." + level.getRandom().nextInt(4));
		PeristiwaJalan.kabar(level, Vec3.atCenterOf(mid), 40.0D,
				Component.translatable(PeristiwaJalan.L + "kelahi.mulai", WargaApi.nama(a), WargaApi.nama(b)).withStyle(ChatFormatting.YELLOW), false);
		PeristiwaJalan.kabar(level, Vec3.atCenterOf(mid), 24.0D, Component.translatable(PeristiwaJalan.L + "kelahi.petunjuk"), true);
		return k;
	}

	/** An adult (16-70), living here, not already busy with an event or ceremony. */
	private static boolean layak(Warga w) {
		int umur = WargaApi.umur(w);
		return w.status == Warga.Status.HIDUP && umur >= 16 && umur <= 70 && !PeristiwaJalan.bertugas(w);
	}

	private static @Nullable Warga priaDekat(TempatHuni huni, List<KultivatorNpc> bodies, KultivatorNpc dari, Warga w) {
		for (KultivatorNpc o : bodies) {
			if (o == dari || o.distanceToSqr(dari) > 12.0D * 12.0D) {
				continue;
			}
			Warga ow = WargaApi.dari(o);
			if (ow != null && ow != w && layak(ow) && ow.pria() && PeristiwaJalan.tubuh(huni, ow) != null) {
				return ow;
			}
		}
		return null;
	}

	// ================================================================== tick

	@Override
	boolean tik(ServerLevel level, long now) {
		if (selesai) {
			return true;
		}
		KultivatorNpc ta = PeristiwaJalan.tubuh(huni, a);
		KultivatorNpc tb = PeristiwaJalan.tubuh(huni, b);
		if (ta == null || tb == null) {
			tanpaTubuh++;
			if (tanpaTubuh > 400L) {
				bubar(level, "data", null);
				return true;
			}
			return false;
		}
		tanpaTubuh = 0L;
		if ((now - mulai) % 20L == 0L) {
			boolean tibaA = ta.blockPosition().distSqr(posA) < 2.5D * 2.5D;
			boolean tibaB = tb.blockPosition().distSqr(posB) < 2.5D * 2.5D;
			if (tibaA && tibaB) {
				if (t0 < 0L) {
					t0 = now;
					HidupApi.tatap(ta, tb);
					HidupApi.tatap(tb, ta);
				}
				SosokApi.gerakSinkron(ta, KotaGerakId.TINJU_KEDAI_A, t0);
				SosokApi.gerakSinkron(tb, KotaGerakId.TINJU_KEDAI_B, t0);
				RandomSource r = level.getRandom();
				if (r.nextInt(2) == 0) {
					level.playSound(null, pusat, r.nextBoolean() ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_STRONG,
							SoundSource.NEUTRAL, 0.8F, 0.9F + r.nextFloat() * 0.2F);
					level.sendParticles(ParticleTypes.CRIT, pusat.getX() + 0.5D, pusat.getY() + 1.4D, pusat.getZ() + 0.5D, 3, 0.3D, 0.2D, 0.3D, 0.1D);
				}
				if (r.nextInt(5) == 0) {
					PeristiwaJalan.seru(r.nextBoolean() ? ta : tb, null, "kelahi.seru." + r.nextInt(6));
				}
			}
		}
		if (penjaga == null && now - mulai >= LAMA) {
			panggilPenjaga(level, now);
		}
		if (penjaga != null) {
			if (penjaga.isRemoved() || penjaga.distanceToSqr(Vec3.atCenterOf(pusat)) < 3.0D * 3.0D || now - penjagaT >= PENJAGA_MAKS) {
				if (!penjaga.isRemoved()) {
					PeristiwaJalan.seru(penjaga, "kota42.lerai", "kelahi.penjaga");
					HidupApi.klip(penjaga, KotaGerakId.LERAI, 0, 80);
				}
				bubar(level, "penjaga", null);
				return true;
			}
			if ((now - penjagaT) % 20L == 0L) {
				penjaga.setHome(pusat);
				penjaga.getNavigation().moveTo(pusat.getX() + 0.5D, pusat.getY(), pusat.getZ() + 0.5D, 1.2D);
			}
		}
		return false;
	}

	private void panggilPenjaga(ServerLevel level, long now) {
		BlockPos from = null;
		for (Titik t : huni.titik("jalan")) {
			double d = t.pos().distSqr(pusat);
			if (d > 14.0D * 14.0D && d < 28.0D * 28.0D) {
				from = t.pos();
				break;
			}
		}
		BlockPos spot = PeristiwaJalan.berdiri(level, from == null ? pusat.offset(16, 0, 0) : from);
		penjaga = spot == null ? null : HidupApi.tubuh(level, situs, NpcRole.PENJAGA, spot, 0.0F);
		penjagaT = now;
		if (penjaga == null) {
			// no room for another body: a resident calls the guard by name and the pair stops on its own
			penjagaT = now - PENJAGA_MAKS;
			return;
		}
		penjaga.memory().putBoolean("hold", true);
		penjaga.addTag(PeristiwaJalan.TAG_WAKIL);
		PeristiwaJalan.kabar(level, Vec3.atCenterOf(pusat), 40.0D, Component.translatable(PeristiwaJalan.L + "kelahi.penjaga_datang"), true);
	}

	// ================================================================== player choices

	@Override
	boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
		boolean onA = e == PeristiwaJalan.tubuh(huni, a);
		boolean onB = e == PeristiwaJalan.tubuh(huni, b);
		if (selesai || !onA && !onB) {
			return false;
		}
		if (shift) {
			bertaruh(player, onA);
		} else {
			lerai(level, player, (KultivatorNpc) e);
		}
		return true;
	}

	private void bertaruh(ServerPlayer player, boolean padaA) {
		Taruhan ada = taruhan.get(player.getUUID());
		Warga pilihan = padaA ? a : b;
		if (ada != null) {
			Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "kelahi.sudah_taruh", NilaiBatu.text(ada.taruh()),
					WargaApi.nama(ada.padaA() ? a : b)));
			return;
		}
		long stake = 2L * (Barang.pita(player) + 1L);
		if (!NilaiBatu.pay(player, stake)) {
			Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "kelahi.kurang", NilaiBatu.text(stake)));
			return;
		}
		taruhan.put(player.getUUID(), new Taruhan(padaA, stake));
		Msg.chat(player, Component.translatable(PeristiwaJalan.L + "kelahi.taruh", NilaiBatu.text(stake), WargaApi.nama(pilihan)));
	}

	private void lerai(ServerLevel level, ServerPlayer player, KultivatorNpc body) {
		long now = level.getGameTime();
		Long last = leraiT.get(player.getUUID());
		if (last != null && now - last < 60L) {
			return;
		}
		leraiT.put(player.getUUID(), now);
		KultivatorNpc ta = PeristiwaJalan.tubuh(huni, a);
		KultivatorNpc tb = PeristiwaJalan.tubuh(huni, b);
		int ranah = Cultivators.get(player).realm().ordinal();
		int kuat = Math.max(ta == null ? 0 : ta.realm(), tb == null ? 0 : tb.realm());
		boolean pasti = ranah >= kuat + 2;
		if (pasti || level.getRandom().nextDouble() < 0.45D + 0.10D * ranah) {
			MinecraftServer server = level.getServer();
			// the one who started it (a) resents the meddling a little; the other is grateful
			WargaApi.ubahKesan(a, player, -2, "lerai", null);
			WargaApi.ubahKesan(b, player, 8, "lerai", null);
			PeristiwaJalan.catatSuasanaTempat(server, situs, 5);
			Msg.chat(player, Component.translatable(PeristiwaJalan.L + (pasti ? "kelahi.lerai_wibawa" : "kelahi.lerai_berhasil"))
					.withStyle(ChatFormatting.GOLD));
			bubar(level, "lerai", null);
		} else {
			HidupApi.tatap(body, player);
			PeristiwaJalan.seru(body, null, "kelahi.tolak." + level.getRandom().nextInt(3));
			Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "kelahi.lerai_gagal"));
		}
	}

	@Override
	boolean pukul(ServerLevel level, ServerPlayer player, Entity e) {
		boolean onA = e == PeristiwaJalan.tubuh(huni, a);
		boolean onB = e == PeristiwaJalan.tubuh(huni, b);
		if (selesai || !onA && !onB) {
			return false;
		}
		PeristiwaJalan.namaBaik(player, situs, -3);
		WargaApi.peristiwaPemain(player, situs, "pukul", onA ? a : b);
		level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
		Msg.chat(player, Component.translatable(PeristiwaJalan.L + "kelahi.ikut", WargaApi.nama(onA ? a : b)).withStyle(ChatFormatting.RED));
		bubar(level, "ikut", !onA);
		return true;
	}

	// ================================================================== end

	/** Ends the brawl: {@code menangA} null = by hash; bets settle (refunded when calmed), gossip, the loser kneels. */
	private void bubar(ServerLevel level, String sebab, @Nullable Boolean menangA) {
		if (selesai) {
			return;
		}
		selesai = true;
		MinecraftServer server = level.getServer();
		boolean winA = menangA != null ? menangA : (PeristiwaJalan.acak(a.id, b.id, mulai) & 1L) == 0L;
		KultivatorNpc ta = PeristiwaJalan.tubuh(huni, a);
		KultivatorNpc tb = PeristiwaJalan.tubuh(huni, b);
		boolean damai = "lerai".equals(sebab);
		if (damai) {
			for (KultivatorNpc t : new KultivatorNpc[]{ta, tb}) {
				if (t != null) {
					SosokApi.henti(t);
					HidupApi.klip(t, GerakId.HORMAT, 0, 40);
				}
			}
			PeristiwaJalan.seru(ta, null, "kelahi.damai");
		} else {
			KultivatorNpc loser = winA ? tb : ta;
			KultivatorNpc winner = winA ? ta : tb;
			if (loser != null) {
				SosokApi.henti(loser);
				// the guard has arrived: the loser groans with a slap on the forehead, otherwise he kneels
				HidupApi.klip(loser, "penjaga".equals(sebab) ? KotaGerakId.MENEPUK_JIDAT : GerakId.BERLUTUT, 0, "penjaga".equals(sebab) ? 40 : 120);
			}
			if (winner != null) {
				SosokApi.henti(winner);
				HidupApi.klip(winner, GerakId.SIKAP_BERKACAK, 0, 80);
			}
		}
		for (Map.Entry<UUID, Taruhan> e : taruhan.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				continue;
			}
			Taruhan t = e.getValue();
			if (damai) {
				NilaiBatu.give(p, t.taruh());
				Msg.chat(p, Component.translatable(PeristiwaJalan.L + "kelahi.taruh_kembali", NilaiBatu.text(t.taruh())));
			} else if (t.padaA() == winA) {
				long win = Math.round(t.taruh() * BAYAR);
				NilaiBatu.give(p, win);
				Msg.chat(p, Component.translatable(PeristiwaJalan.L + "kelahi.taruh_menang", NilaiBatu.text(win)).withStyle(ChatFormatting.GOLD));
			} else {
				Msg.chat(p, Component.translatable(PeristiwaJalan.L + "kelahi.taruh_kalah", NilaiBatu.text(t.taruh())).withStyle(ChatFormatting.GRAY));
			}
		}
		taruhan.clear();
		GosipWarga.lahir(level, situs, "BERKELAHI", Fakta.Rujuk.warga(winA ? a.id : b.id), Fakta.Rujuk.warga(winA ? b.id : a.id));
		if (!"data".equals(sebab)) {
			PeristiwaJalan.kabar(level, Vec3.atCenterOf(pusat), 40.0D,
					Component.translatable(PeristiwaJalan.L + "kelahi.akhir." + sebab, WargaApi.nama(winA ? a : b), WargaApi.nama(winA ? b : a)), false);
		}
		HidupApi.bubarkan(level, situs);
	}

	@Override
	void akhiri(ServerLevel level) {
		if (!selesai) {
			bubar(level, "data", null);
		}
		PeristiwaJalan.lepas(a);
		PeristiwaJalan.lepas(b);
		PeristiwaJalan.hilangkan(penjaga);
	}
}
