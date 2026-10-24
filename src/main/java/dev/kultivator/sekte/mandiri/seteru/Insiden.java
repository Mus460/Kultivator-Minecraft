package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.api.TurnamenHasil;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.PertemuanProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The triggers of feuds (DESIGN 39.F.2). Checked only when a journey stage of 39C falls due (through {@link Pertemuan})
 * or when a bus event happens; there is no tick per character. A sect is present at a place with
 * {@code hadir = 0.25} when its gate is within 3000 blocks (x2 within 1500, x2 when hostile or at war, x0.5 when allied),
 * at most one meeting with another sect per journey. The meeting turns into an incident by the table of 39.F.2 (each
 * chance times {@code frekuensi}):
 * <pre>
 * tea-house insult 30 %   treasure quarrel 50 %   same guild mission 12 % then 60 %   lost tournament 40/15 %
 * love rival 20 %         stolen scroll 3/2 %      market bully 60 % we step in   village squeezed 10 % then 70 %
 * D events (ambushed mission, poaching, raid): heat +10 on a running feud, or a new one 30 %
 * </pre>
 * with the trait and stance multipliers of the table; who was wrong decides who may demand compensation.
 */
final class Insiden {

	/** One rolled incident: the trigger, who was wrong, the starting heat and the member involved. */
	record Hasil(String sebab, String salah, int panas, TokohSekte tokoh) {
	}

	// ================================================================== presence

	/** The sect present at {@code pos} for this stage (nearest first), or null. */
	static @Nullable String hadir(MinecraftServer server, UUID owner, BlockPos pos, String tahap, RandomSource rnd) {
		DiplomasiService d = SekteApi.diplomasi();
		SeteruConfig config = SeteruConfig.get();
		List<String> sects = new ArrayList<>(d.sects());
		if (sects.isEmpty()) {
			return null;
		}
		sects.sort(Comparator.comparingDouble(s -> jarak(d, s, pos)));
		if ("gerbang_sekte".equals(tahap)) {
			// a guest at their gate (SEKTE_TAMU): that sect is there for sure
			return jarak(d, sects.get(0), pos) <= 400.0D ? sects.get(0) : null;
		}
		for (String sekte : sects) {
			double dist = jarak(d, sekte, pos);
			if (dist > 3000.0D) {
				break;
			}
			double chance = config.peluang.hadir * (dist <= 1500.0D ? 2.0D : 1.0D);
			Sikap sikap = d.stance(owner, sekte);
			if (sikap == Sikap.BERMUSUHAN || sikap == Sikap.PERANG) {
				chance *= 2.0D;
			} else if (sikap == Sikap.SEKUTU) {
				chance *= 0.5D;
			}
			if (rnd.nextDouble() < config.kali(chance)) {
				return sekte;
			}
		}
		return null;
	}

	static double jarak(DiplomasiService d, String sekte, BlockPos pos) {
		BlockPos gate = d.gate(sekte);
		if (gate == null) {
			gate = d.plannedGate(sekte);
		}
		if (gate == null) {
			return Double.MAX_VALUE;
		}
		double dx = gate.getX() - pos.getX();
		double dz = gate.getZ() - pos.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ================================================================== the 39.F.2 table

	/** Rolls the incident of a meeting of {@code murid} with sect {@code sekte} at stage {@code tahap}, or null. */
	static @Nullable Hasil roll(MinecraftServer server, PertemuanProvider.Pertemuan p, MuridInfo murid, String sekte, RandomSource rnd) {
		SeteruConfig config = SeteruConfig.get();
		SeteruConfig.Peluang peluang = config.peluang;
		DiplomasiService d = SekteApi.diplomasi();
		long today = p.hari();
		Sikap sikap = d.stance(p.owner(), sekte);
		boolean iblis = d.aliran(sekte) == 2;
		TokohSekte tokoh = Roster.pilih(server, sekte, murid.soul(), today, rnd, true);
		if (tokoh == null) {
			return null;
		}
		// the stolen scroll can happen on any stage when a scroll is in the bag (theirs 3 %, ours 2 %)
		if (adaGulungan(server, murid)) {
			if (tokoh.punya(Sifat.LICIK) && rnd.nextDouble() < config.kali(peluang.curiMereka)) {
				return new Hasil(SeteruIds.SEBAB_GULUNGAN, SeteruIds.SALAH_MEREKA, 35, tokoh);
			}
			if ((murid.punya(Sifat.LICIK) || murid.iblis() >= 60) && rnd.nextDouble() < config.kali(peluang.curiKita)) {
				return new Hasil(SeteruIds.SEBAB_GULUNGAN, SeteruIds.SALAH_KITA, 35, tokoh);
			}
		}
		switch (p.tahap()) {
			case "kedai_teh", "gerbang_sekte", "arena" -> {
				double chance = peluang.hinaan;
				if (murid.punya(Sifat.SOMBONG) || tokoh.punya(Sifat.SOMBONG)) {
					chance *= 1.6D;
				}
				if (sikap == Sikap.DINGIN) {
					chance *= 1.5D;
				} else if (sikap == Sikap.BERMUSUHAN || sikap == Sikap.PERANG) {
					chance *= 2.0D;
				}
				if (iblis) {
					chance *= 1.5D;
				}
				if (murid.punya(Sifat.RENDAH_HATI)) {
					chance *= 0.5D;
				}
				if (rnd.nextDouble() < config.kali(chance)) {
					return new Hasil(SeteruIds.SEBAB_HINAAN, rnd.nextDouble() < 0.70D ? SeteruIds.SALAH_MEREKA : SeteruIds.SALAH_KITA, 15, tokoh);
				}
			}
			case "berburu", "reruntuhan", "alam" -> {
				ServerLevel level = Adegan.level(server, p.owner());
				boolean birth = !DuniaApi.treasures().live(level).isEmpty();
				double chance = peluang.harta * (birth ? 1.0D : 0.4D);
				if (murid.punya(Sifat.AMBISIUS) || tokoh.punya(Sifat.AMBISIUS)) {
					chance *= 1.5D;
				}
				if (d.tier(sekte) >= 5) {
					chance *= 1.3D;
				}
				if (rnd.nextDouble() < config.kali(chance)) {
					return new Hasil(SeteruIds.SEBAB_HARTA, SeteruIds.SALAH_UNDIAN, 25, tokoh);
				}
			}
			case "gilda" -> {
				if (rnd.nextDouble() < config.kali(peluang.gildaSama)) {
					double chance = peluang.gilda * (tokoh.punya(Sifat.LICIK) ? 1.5D : 1.0D);
					if (rnd.nextDouble() < Math.min(1.0D, chance)) {
						return new Hasil(SeteruIds.SEBAB_GILDA, tokoh.punya(Sifat.LICIK) ? SeteruIds.SALAH_MEREKA : SeteruIds.SALAH_UNDIAN, 20, tokoh);
					}
				}
			}
			case "pasar", "pasar_malam" -> {
				if (murid.cita(JenisCita.JODOH) && rnd.nextDouble() < config.kali(peluang.asmara)) {
					return new Hasil(SeteruIds.SEBAB_ASMARA, SeteruIds.SALAH_TIDAK, 20, tokoh);
				}
				if (tokoh.punya(Sifat.SOMBONG) || iblis) {
					double step = murid.punya(Sifat.PENYAYANG) || murid.punya(Sifat.BERANI) ? 0.90D
							: murid.punya(Sifat.PENAKUT) ? 0.20D : peluang.perundung;
					if (rnd.nextDouble() < config.kali(0.5D) && rnd.nextDouble() < step) {
						return new Hasil(SeteruIds.SEBAB_PERUNDUNG, SeteruIds.SALAH_MEREKA, 15, tokoh);
					}
				}
			}
			case "desa" -> {
				if (d.aliran(sekte) >= 1 && rnd.nextDouble() < config.kali(peluang.desaPeras)) {
					double chance = peluang.desa * (murid.punya(Sifat.PENYAYANG) ? 1.3D : 1.0D);
					if (rnd.nextDouble() < Math.min(1.0D, chance)) {
						return new Hasil(SeteruIds.SEBAB_DESA, SeteruIds.SALAH_MEREKA, 25, tokoh);
					}
				}
			}
			default -> {
				// an open road: an insult is rarer there
				if (rnd.nextDouble() < config.kali(peluang.hinaan * 0.35D * (iblis ? 1.5D : 1.0D))) {
					return new Hasil(SeteruIds.SEBAB_HINAAN, SeteruIds.SALAH_MEREKA, 15, tokoh);
				}
			}
		}
		return null;
	}

	/** True when the disciple carries a technique scroll or manual in its bag. */
	private static boolean adaGulungan(MinecraftServer server, MuridInfo murid) {
		if (murid.data() == null) {
			return false;
		}
		for (ItemStack stack : MandiriApi.tas(murid.data(), server.registryAccess())) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (id.contains("scroll") || id.contains("gulungan") || id.contains("kitab") || id.contains("manual")) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== bus events (no journey)

	static void init() {
		SekteBus.RIVAL_SECT_ACTION.register(Insiden::aksiSekte);
		SekteBus.TOURNAMENT_ENDED.register(Insiden::turnamen);
	}

	/** D events: an ambushed mission, poaching, a raid (39.F.2 last row). */
	private static void aksiSekte(MinecraftServer server, @Nullable UUID owner, String sekte, AksiSekte action, int magnitude) {
		if (owner == null || !Seteru.aktif() || action != AksiSekte.SERGAP_MISI && action != AksiSekte.MEREKRUT && action != AksiSekte.SERANGAN) {
			return;
		}
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		Perseteruan running = aula.dengan(sekte);
		if (running != null) {
			Tangga.panas(server, running, 10, "riwayat.peristiwa." + action.name().toLowerCase(java.util.Locale.ROOT));
			return;
		}
		long today = Ingatan.today(server);
		RandomSource rnd = RandomSource.create(owner.getMostSignificantBits() ^ today * 977L ^ sekte.hashCode());
		SeteruConfig config = SeteruConfig.get();
		if (rnd.nextDouble() >= config.kali(config.peluang.peristiwaBaru)) {
			return;
		}
		List<DiscipleEntity> bodies = Disciples.loaded(owner);
		if (bodies.isEmpty()) {
			return;
		}
		DiscipleEntity pick = bodies.get(rnd.nextInt(bodies.size()));
		if (aula.milik(pick.soulId()) != null) {
			return;
		}
		TokohSekte tokoh = Roster.pilih(server, sekte, pick.soulId(), today, rnd, true);
		if (tokoh == null) {
			return;
		}
		MuridInfo murid = MuridInfo.dari(owner, pick.soulId());
		Tangga.mulai(server, owner, murid, tokoh, SeteruIds.SEBAB_PERISTIWA, SeteruIds.SALAH_MEREKA, 10, 0L, pick.blockPosition());
	}

	/** A lost inter-sect tournament (39.F.2): the proud and vengeful losers 40 %, others 15 %; a rout x1.5. */
	private static void turnamen(MinecraftServer server, UUID owner, TurnamenHasil result) {
		if (owner == null || !Seteru.aktif() || result == null || result.guestSect() == null || !"antar_sekte".equals(result.kind())) {
			return;
		}
		String sekte = result.guestSect();
		long today = Ingatan.today(server);
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		// a feud that went official at T7: its people came with the delegation ("Dia datang")
		for (Perseteruan f : aula.seteru) {
			if (f.resmi && f.sekte.equals(sekte)) {
				for (UUID soul : f.jiwa) {
					TokohSekte t = data.tokoh(f.tokohUtama());
					Tangga.harian(server, owner, soul, "seteru.delegasi", "harian.delegasi", EntriHarian.Suasana.KESAL, true,
							t == null ? "" : t.argNama(), Teks.argSekte(sekte));
				}
			}
		}
		if (!result.guestWon()) {
			return;
		}
		SeteruConfig config = SeteruConfig.get();
		RandomSource rnd = RandomSource.create(owner.getLeastSignificantBits() ^ today * 4447L ^ result.season());
		int n = 0;
		for (UUID soul : result.placings()) {
			if (n++ >= 3) {
				break;
			}
			MuridInfo murid = MuridInfo.dari(owner, soul);
			double chance = murid.punya(Sifat.SOMBONG) || murid.punya(Sifat.PENDENDAM) ? config.peluang.turnamen : config.peluang.turnamenLain;
			TokohSekte tokoh = Roster.pilih(server, sekte, soul, today, rnd, true);
			if (tokoh == null) {
				continue;
			}
			if (tokoh.kekuatan(today, SekteApi.diplomasi().tier(sekte)) - murid.kekuatan() >= 1.0D) {
				chance *= 1.5D;
			}
			if (rnd.nextDouble() < config.kali(chance)) {
				BlockPos at = murid.tubuh() != null ? murid.tubuh().blockPosition() : BlockPos.ZERO;
				Perseteruan f = Tangga.mulai(server, owner, murid, tokoh, SeteruIds.SEBAB_TURNAMEN, SeteruIds.SALAH_TIDAK, 20, 0L, at);
				if (f != null) {
					// the grudge is one-sided: ours lost and wants revenge
					MandiriApi.dendam(server, owner, soul, "seteru:" + tokoh.id);
				}
			}
		}
	}

	private Insiden() {
	}
}
