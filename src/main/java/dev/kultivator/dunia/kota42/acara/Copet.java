package dev.kultivator.dunia.kota42.acara;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.PasarHantu;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The pickpocket (DESIGN 42.7.4 "copet", K2: spirit stones only). Two forms:
 * <ul>
 * <li><b>on a player</b> ({@link #padaPemain}): a {@code PENCOPET} body slips through the crowd toward a player standing or
 * strolling among 4+ residents, bumps into him ({@code tabrak}, "permisi") and the purse is lighter: 10 % of his stones
 * (at least 1 NB, at most 27 x (band + 1)). A player of Foundation or above feels the hand first half the time: for
 * {@value #RABA_TICK} ticks a click or a hit catches him red-handed before anything is taken.</li>
 * <li><b>on a resident</b> ({@link #padaWarga}): a market-goer shouts "Copet!" and points while the thief runs.</li>
 * </ul>
 * The chase lasts {@value #KEJAR_TICK} ticks (Sibuk {@code kota.kejar}, the {@code kota_kejar} HUD at 4 Hz for every chaser
 * within 32 blocks): the thief runs from street node to street node away from the nearest chaser, a little faster than a
 * walking player. One hit and he kneels: the stones go back, Nama Baik +2, the residents who saw it think better of the
 * player (+3), a robbed resident +20 (saved), gossip {@code COPET_TERTANGKAP}, advancement {@code kota42_copet}; the
 * guards take him away. If he gets away the stones turn up at the Ghost Market that night ({@code PasarHantu.tambahCurian},
 * redeemable) and a robbed resident's mood drops.
 */
final class Copet extends PeristiwaJalan.Kejadian {

	private enum Fase { MENDEKAT, RABA, KABUR, TERTANGKAP }

	static final int KEJAR_TICK = 600;
	static final int RABA_TICK = 30;
	static final int DEKAT_MAKS = 500;
	static final int DIBAWA_TICK = 100;
	/** Navigation speed while fleeing (about 1.25 x a walking player for a civilian body). */
	static final double LARI = 1.35D;
	static final double JARAK_LEPAS = 48.0D;

	private final @Nullable UUID korbanPemain;
	private final @Nullable Warga korbanWarga;
	private final KultivatorNpc pencopet;
	private final Set<UUID> pengejar = new HashSet<>();
	private final List<BlockPos> dilalui = new ArrayList<>();
	private Fase fase;
	private long faseT;
	private long curian;
	private boolean terasa;
	private @Nullable BlockPos tujuan;

	private Copet(TempatHuni huni, long now, @Nullable UUID korbanPemain, @Nullable Warga korbanWarga, KultivatorNpc pencopet, Fase fase) {
		super(korbanPemain != null ? PeristiwaJalan.COPET : PeristiwaJalan.COPET_WARGA, huni, now);
		this.korbanPemain = korbanPemain;
		this.korbanWarga = korbanWarga;
		this.pencopet = pencopet;
		this.fase = fase;
		this.faseT = now;
	}

	// ================================================================== start

	static @Nullable Copet padaPemain(ServerLevel level, TempatHuni huni, ServerPlayer player, long now) {
		if (Sibuk.why(player) != null || NilaiBatu.total(player) <= 0L) {
			return null;
		}
		Vec3 back = player.position().subtract(player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(9.0D));
		BlockPos spot = PeristiwaJalan.berdiri(level, BlockPos.containing(back));
		if (spot == null) {
			spot = PeristiwaJalan.berdiri(level, player.blockPosition().offset(6, 0, -6));
		}
		if (spot == null) {
			return null;
		}
		int look = (int) PeristiwaJalan.acak(huni.situsId, now, 0xC0BE7L);
		KultivatorNpc thief = HidupApi.tubuh(level, huni.situsId, NpcRole.PENCOPET, spot, player.getYRot(), look);
		if (thief == null) {
			return null;
		}
		thief.memory().putBoolean("hold", true);
		thief.addTag(PeristiwaJalan.TAG_WAKIL);
		Copet k = new Copet(huni, now, player.getUUID(), null, thief, Fase.MENDEKAT);
		k.terasa = Cultivators.get(player).realm().ordinal() >= Realm.FOUNDATION.ordinal() && level.getRandom().nextBoolean();
		k.pengejar.add(player.getUUID());
		return k;
	}

	static @Nullable Copet padaWarga(ServerLevel level, TempatHuni huni, ServerPlayer saksi, long now) {
		// a market-goer near a stall, within sight of the player
		KultivatorNpc victimBody = null;
		Warga victim = null;
		List<Titik> stalls = PeristiwaJalan.titik(huni, "lapak", "kerja:kios");
		for (KultivatorNpc npc : PeristiwaJalan.tubuhDekat(level, huni.situsId, saksi.position(), 32.0D)) {
			Warga w = WargaApi.dari(npc);
			if (w == null || WargaApi.umur(w) < 16 || PeristiwaJalan.bertugas(w) || npc.distanceToSqr(saksi) < 16.0D) {
				continue;
			}
			boolean market = stalls.isEmpty();
			for (Titik t : stalls) {
				if (t.pos().distSqr(npc.blockPosition()) < 10.0D * 10.0D) {
					market = true;
					break;
				}
			}
			if (market) {
				victimBody = npc;
				victim = w;
				break;
			}
		}
		if (victimBody == null) {
			return null;
		}
		BlockPos spot = PeristiwaJalan.berdiri(level, victimBody.blockPosition().offset(2, 0, 1));
		if (spot == null) {
			return null;
		}
		int look = (int) PeristiwaJalan.acak(huni.situsId, now, 0xC0BE8L);
		KultivatorNpc thief = HidupApi.tubuh(level, huni.situsId, NpcRole.PENCOPET, spot, victimBody.getYRot(), look);
		if (thief == null) {
			return null;
		}
		thief.memory().putBoolean("hold", true);
		thief.addTag(PeristiwaJalan.TAG_WAKIL);
		Copet k = new Copet(huni, now, null, victim, thief, Fase.KABUR);
		HidupApi.tatap(victimBody, thief);
		HidupApi.klip(victimBody, GerakId.TUNJUK, 0, 80);
		PeristiwaJalan.seru(victimBody, "kota42.copet_teriak", "copet.teriak");
		HidupApi.klip(thief, KotaGerakId.TABRAK, 0, 16);
		PeristiwaJalan.kabar(level, thief.position(), 40.0D,
				Component.translatable(PeristiwaJalan.L + "copet.warga_mulai", WargaApi.nama(victim)).withStyle(ChatFormatting.YELLOW), false);
		for (ServerPlayer p : PeristiwaJalan.pemainDekat(level, thief.position(), 32.0D)) {
			k.pengejar.add(p.getUUID());
			Msg.actionBar(p, Component.translatable(PeristiwaJalan.L + "copet.petunjuk_kejar"));
		}
		k.mulaiKabur(level, now);
		return k;
	}

	// ================================================================== tick

	@Override
	boolean tik(ServerLevel level, long now) {
		if (pencopet.isRemoved()) {
			if (fase == Fase.KABUR) {
				lolos(level);
			}
			return true;
		}
		MinecraftServer server = level.getServer();
		switch (fase) {
			case MENDEKAT -> {
				ServerPlayer target = korbanPemain == null ? null : server.getPlayerList().getPlayer(korbanPemain);
				if (target == null || target.level() != level || target.distanceToSqr(pencopet) > 40.0D * 40.0D || now - faseT > DEKAT_MAKS
						|| Sibuk.why(target) != null) {
					return true;
				}
				if ((now - faseT) % 10L == 0L) {
					pencopet.setHome(target.blockPosition());
					pencopet.getNavigation().moveTo(target, 0.85D);
				}
				if (pencopet.distanceToSqr(target) < 1.8D * 1.8D) {
					if (terasa) {
						fase = Fase.RABA;
						faseT = now;
						pencopet.getNavigation().stop();
						Msg.actionBar(target, Component.translatable(PeristiwaJalan.L + "copet.terasa").withStyle(ChatFormatting.RED));
						level.playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
					} else {
						curi(level, target, now);
					}
				}
			}
			case RABA -> {
				if (now - faseT >= RABA_TICK) {
					ServerPlayer target = korbanPemain == null ? null : server.getPlayerList().getPlayer(korbanPemain);
					if (target == null) {
						return true;
					}
					curi(level, target, now);
				}
			}
			case KABUR -> {
				if ((now - faseT) % 5L == 0L) {
					int sisa = (int) Math.max(0L, KEJAR_TICK - (now - faseT));
					for (UUID id : pengejar) {
						ServerPlayer p = server.getPlayerList().getPlayer(id);
						if (p != null && p.distanceToSqr(pencopet) < 32.0D * 32.0D) {
							KotaPayloads.kirim(p, new KotaPayloads.Kejar(pencopet.getId(), sisa));
						}
					}
				}
				ServerPlayer near = pengejarTerdekat(server);
				if (now - faseT >= KEJAR_TICK || near == null || near.distanceToSqr(pencopet) > JARAK_LEPAS * JARAK_LEPAS) {
					lolos(level);
					return true;
				}
				if ((now - faseT) % 10L == 0L) {
					lari(level, near);
				}
			}
			case TERTANGKAP -> {
				if (now - faseT >= DIBAWA_TICK) {
					return true;
				}
			}
		}
		return false;
	}

	private @Nullable ServerPlayer pengejarTerdekat(MinecraftServer server) {
		ServerPlayer best = null;
		double d = Double.MAX_VALUE;
		for (UUID id : pengejar) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.level() == pencopet.level()) {
				double dd = p.distanceToSqr(pencopet);
				if (dd < d) {
					d = dd;
					best = p;
				}
			}
		}
		// anyone who joins the chase counts
		for (ServerPlayer p : PeristiwaJalan.pemainDekat((ServerLevel) pencopet.level(), pencopet.position(), 16.0D)) {
			if (pengejar.add(p.getUUID())) {
				KotaHidup.tandaiSibuk(p, KotaHidup.SIBUK_KEJAR, KEJAR_TICK);
			}
			double dd = p.distanceToSqr(pencopet);
			if (dd < d) {
				d = dd;
				best = p;
			}
		}
		return best;
	}

	/** Runs to the next street node away from the chaser (6-22 blocks on, never back to one already passed). */
	private void lari(ServerLevel level, ServerPlayer chaser) {
		if (tujuan == null || pencopet.blockPosition().distSqr(tujuan) < 2.5D * 2.5D || pencopet.getNavigation().isDone()) {
			BlockPos best = null;
			double score = -Double.MAX_VALUE;
			Vec3 at = pencopet.position();
			for (Titik t : huni.titik("jalan")) {
				double d = Math.sqrt(t.pos().distToCenterSqr(at));
				if (d < 6.0D || d > 22.0D || dilalui.contains(t.pos())) {
					continue;
				}
				double away = Math.sqrt(t.pos().distToCenterSqr(chaser.position()));
				double s = away - d * 0.3D;
				if (s > score) {
					score = s;
					best = t.pos();
				}
			}
			if (best == null) {
				Vec3 dir = at.subtract(chaser.position()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(12.0D);
				best = BlockPos.containing(at.add(dir));
			}
			tujuan = best;
			dilalui.add(best);
		}
		pencopet.setHome(tujuan);
		pencopet.getNavigation().moveTo(tujuan.getX() + 0.5D, tujuan.getY(), tujuan.getZ() + 0.5D, LARI);
	}

	// ================================================================== theft, catch, escape

	private void curi(ServerLevel level, ServerPlayer target, long now) {
		long total = NilaiBatu.total(target);
		long amount = Mth.clamp(total / 10L, 1L, 27L * (Barang.pita(target) + 1L));
		if (total <= 0L || !NilaiBatu.pay(target, amount)) {
			fase = Fase.TERTANGKAP;
			faseT = now - DIBAWA_TICK;
			return;
		}
		curian = amount;
		HidupApi.klip(pencopet, KotaGerakId.TABRAK, 0, 16);
		PeristiwaJalan.seru(pencopet, "kota42.copet_tabrak", "copet.permisi");
		level.playSound(null, target.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.6F);
		Msg.title(target, Component.empty(), Component.translatable(PeristiwaJalan.L + "copet.ringan").withStyle(ChatFormatting.RED), 4, 40, 10);
		Msg.chat(target, Component.translatable(PeristiwaJalan.L + "copet.dicuri", NilaiBatu.text(amount)).withStyle(ChatFormatting.RED));
		Msg.actionBar(target, Component.translatable(PeristiwaJalan.L + "copet.petunjuk_kejar"));
		mulaiKabur(level, now);
	}

	private void mulaiKabur(ServerLevel level, long now) {
		fase = Fase.KABUR;
		faseT = now;
		SosokApi.gerak(pencopet, KotaGerakId.LARI_COPET, 0, KEJAR_TICK);
		for (UUID id : pengejar) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				KotaHidup.tandaiSibuk(p, KotaHidup.SIBUK_KEJAR, KEJAR_TICK);
			}
		}
	}

	@Override
	boolean pukul(ServerLevel level, ServerPlayer player, Entity e) {
		if (e != pencopet) {
			return false;
		}
		if (fase == Fase.RABA || fase == Fase.MENDEKAT && pencopet.distanceToSqr(player) < 3.0D * 3.0D && player.getUUID().equals(korbanPemain)) {
			tangkap(level, player, true);
		} else if (fase == Fase.KABUR) {
			tangkap(level, player, false);
		}
		return true;
	}

	@Override
	boolean klik(ServerLevel level, ServerPlayer player, Entity e, boolean shift) {
		if (e != pencopet) {
			return false;
		}
		if (fase == Fase.RABA && player.getUUID().equals(korbanPemain)) {
			tangkap(level, player, true);
		} else if (fase == Fase.KABUR) {
			Msg.actionBar(player, Component.translatable(PeristiwaJalan.L + "copet.petunjuk_pukul"));
		}
		return true;
	}

	private void tangkap(ServerLevel level, ServerPlayer catcher, boolean tanganMerah) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		fase = Fase.TERTANGKAP;
		faseT = now;
		pencopet.getNavigation().stop();
		HidupApi.klip(pencopet, GerakId.BERLUTUT, 0, DIBAWA_TICK + 20);
		PeristiwaJalan.seru(pencopet, "kota42.copet_tertangkap", "copet.ampun");
		level.playSound(null, pencopet.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (curian > 0L && korbanPemain != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(korbanPemain);
			if (owner != null) {
				NilaiBatu.give(owner, curian);
				Msg.chat(owner, Component.translatable(PeristiwaJalan.L + "copet.kembali", NilaiBatu.text(curian)).withStyle(ChatFormatting.GOLD));
			}
		}
		PeristiwaJalan.namaBaik(catcher, situs, 2);
		WargaApi.peristiwaPemain(catcher, situs, "gosip_baik");
		if (korbanWarga != null) {
			WargaApi.ubahKesan(korbanWarga, catcher, 20, "copet", Kenangan.DISELAMATKAN);
			KultivatorNpc body = PeristiwaJalan.tubuh(huni, korbanWarga);
			if (body != null) {
				HidupApi.klip(body, GerakId.HORMAT_DALAM, 0, 60);
				PeristiwaJalan.seru(body, "kota42.terima_kasih", "copet.terima_kasih");
			}
		}
		GosipWarga.lahir(level, situs, "COPET_TERTANGKAP", (int) curian, Fakta.Kebenaran.BENAR, List.of(), null, Fakta.Rujuk.tempat(situs),
				Fakta.Rujuk.pemain(PeristiwaJalan.indeks(catcher)));
		Advancements.grant(catcher, "tempat/kota42_copet");
		dev.kultivator.dunia.kota42.bicara.TujuanKota.kejarCopet(catcher, situs);
		Msg.chat(catcher, Component.translatable(PeristiwaJalan.L + (tanganMerah ? "copet.tangan_merah" : "copet.tertangkap"))
				.withStyle(ChatFormatting.GOLD));
		lepasPengejar(server);
	}

	private void lolos(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Vec3 at = pencopet.position();
		if (curian > 0L && korbanPemain != null) {
			if (PasarHantu.aktif()) {
				long left = curian;
				for (int grade = 3; grade >= 0 && left > 0L; grade--) {
					long n = left / NilaiBatu.VALUE[grade];
					left -= n * NilaiBatu.VALUE[grade];
					while (n > 0L) {
						int count = (int) Math.min(64L, n);
						n -= count;
						PasarHantu.tambahCurian(server, korbanPemain, SpiritStones.stack(grade, count), situs);
					}
				}
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(korbanPemain);
			if (owner != null) {
				Msg.chat(owner, Component.translatable(PeristiwaJalan.L + (PasarHantu.aktif() ? "copet.lolos_pasar_hantu" : "copet.lolos"))
						.withStyle(ChatFormatting.GRAY));
			}
		} else if (korbanWarga != null) {
			Suasana.catat(server, korbanWarga, Suasana.Peristiwa.DIRAMPOK);
			GosipWarga.lahir(level, situs, "BAHAYA", 0, Fakta.Kebenaran.BENAR, List.of(), null, Fakta.Rujuk.tempat(situs),
					Fakta.Rujuk.kata("copet"));
			PeristiwaJalan.kabar(level, at, 40.0D, Component.translatable(PeristiwaJalan.L + "copet.warga_lolos", WargaApi.nama(korbanWarga))
					.withStyle(ChatFormatting.GRAY), false);
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y + 0.8D, at.z, 8, 0.3D, 0.5D, 0.3D, 0.02D);
		lepasPengejar(server);
		PeristiwaJalan.hilangkan(pencopet);
	}

	private void lepasPengejar(MinecraftServer server) {
		for (UUID id : pengejar) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				KotaHidup.lepasSibuk(p, KotaHidup.SIBUK_KEJAR);
				KotaPayloads.kirim(p, new KotaPayloads.Kejar(-1, 0));
			}
		}
	}

	@Override
	void akhiri(ServerLevel level) {
		lepasPengejar(level.getServer());
		PeristiwaJalan.hilangkan(pencopet);
	}
}
