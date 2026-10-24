package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Sibuk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live scenes of a feud (DESIGN 39.F.5), shown only when a player is near (96 blocks of the place, or the
 * disciple's body is loaded at the hall); otherwise everything stays data. A scene casts our disciple (its real body at
 * the hall, 39C's town body, or a stand-in) and the member of the other sect (a roster body), turns them to face each
 * other and plays the feud clips 250-273 with the voice lines of 39.F.7 in turn, captioned from {@link Naskah}:
 * <ul>
 * <li>the quarrel (T1): finger pointed, chest out, three alternating lines, angry puffs, the townsfolk within 16 blocks
 * turn their heads (C39.K2);</li>
 * <li>an elder parting them, an apology bow, compensation handed over in a pouch, the kneeling vow with a short mist,
 * the bow of peace.</li>
 * </ul>
 * Near the master every scene asks {@code Sibuk.why} first and is skipped while the master is busy. Disciples in a scene
 * answer "seteru" to {@code SibukMurid}. Timed steps run from a small queue on the server tick (no world scans).
 */
final class Adegan {

	static final double JARAK_LIHAT = 96.0D;

	/** The cast of one scene. */
	record Pemeran(ServerLevel level, BlockPos pos, @Nullable DiscipleEntity kita, boolean kitaAsli, @Nullable DiscipleEntity mereka) {
	}

	private record Tugas(long saat, Runnable aksi) {
	}

	private static final List<Tugas> ANTRE = new ArrayList<>();
	/** Disciples in a scene until a game time (SibukMurid "seteru"). */
	private static final Map<UUID, Long> SIBUK = new HashMap<>();

	static boolean sibuk(DiscipleEntity e) {
		Long until = SIBUK.get(e.getUUID());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() > until) {
			SIBUK.remove(e.getUUID());
			return false;
		}
		return true;
	}

	static void tandaiSibuk(@Nullable DiscipleEntity e, int ticks) {
		if (e != null) {
			SIBUK.put(e.getUUID(), e.level().getGameTime() + ticks);
		}
	}

	/** Runs {@code aksi} after {@code delay} ticks on the server thread. */
	static void nanti(MinecraftServer server, int delay, Runnable aksi) {
		ANTRE.add(new Tugas(server.getTickCount() + Math.max(1, delay), aksi));
	}

	static void tick(MinecraftServer server) {
		if (ANTRE.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		List<Tugas> due = new ArrayList<>();
		ANTRE.removeIf(t -> {
			if (t.saat <= now) {
				due.add(t);
				return true;
			}
			return false;
		});
		for (Tugas t : due) {
			try {
				t.aksi.run();
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.warn("Feud scene step failed", exception);
			}
		}
	}

	static void bersihkan() {
		ANTRE.clear();
		SIBUK.clear();
	}

	// ================================================================== casting

	/** The level of the hall (journeys stay in the hall's dimension), else the overworld. */
	static ServerLevel level(MinecraftServer server, UUID owner) {
		var hall = Halls.hallOf(owner);
		ServerLevel level = hall == null ? null : server.getLevel(hall.dim());
		return level != null ? level : server.overworld();
	}

	/** True when a player stands within {@link #JARAK_LIHAT} of {@code pos}. */
	static boolean terlihat(ServerLevel level, BlockPos pos) {
		return level.getNearestPlayer(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, JARAK_LIHAT, false) != null;
	}

	/** False while the master is near {@code pos} and busy (tribulation, a boss, a duel of its own...). */
	static boolean gurubebas(MinecraftServer server, UUID owner, ServerLevel level, BlockPos pos) {
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null || master.level() != level || master.blockPosition().distSqr(pos) > 64.0D * 64.0D) {
			return true;
		}
		return Sibuk.why(master) == null;
	}

	/**
	 * Casts the scene of feud {@code f}: where (our real body at the hall, else the feud's place), whether anyone sees it,
	 * our disciple's body (real, 39C's town body, or a stand-in when {@code pengganti}) and their member's body. Null when
	 * nobody would see it or the master is busy nearby.
	 */
	static @Nullable Pemeran pemeran(MinecraftServer server, Perseteruan f, boolean pengganti, int lama) {
		UUID soul = f.jiwaUtama();
		DiscipleEntity real = soul == null ? null : Antrean.loaded(f.owner, soul);
		ServerLevel level;
		BlockPos pos;
		if (real != null && real.level() instanceof ServerLevel l) {
			level = l;
			pos = real.blockPosition();
		} else {
			if (f.pos == 0L) {
				// a feud born from a bus event far from any body has no place to be shown
				return null;
			}
			level = level(server, f.owner);
			pos = f.posisi();
		}
		if (!terlihat(level, pos) || !gurubebas(server, f.owner, level, pos)) {
			return null;
		}
		DiscipleEntity kita = real;
		if (kita == null && soul != null) {
			MuridInfo murid = MuridInfo.dari(f.owner, soul);
			kita = murid.jelmaan(level, Vec3.atCenterOf(pos));
			if (kita == null && pengganti) {
				kita = TubuhSeteru.pengganti(level, pos.offset(2, 0, 0), murid, f.owner, f.id, lama);
			}
		}
		TokohSekte tokoh = SeteruData.get(server).tokoh(f.tokohUtama());
		DiscipleEntity mereka = null;
		if (tokoh != null) {
			BlockPos at = kita != null ? kita.blockPosition().offset(3, 0, 1) : pos.offset(3, 0, 1);
			mereka = TubuhSeteru.munculkan(level, at, tokoh, TubuhSeteru.Peran.TEMU, f.owner, f.id, lama);
		}
		if (kita == null && mereka == null) {
			return null;
		}
		return new Pemeran(level, pos, kita, real != null, mereka);
	}

	// ================================================================== voices

	/** {@code body} says feud line {@code line} (39.F.7) or its fallback. */
	static void ucap(@Nullable DiscipleEntity body, String line) {
		if (body == null || body.isRemoved()) {
			return;
		}
		String fallback = cadangan(line);
		if (body.role() == DiscipleEntity.Role.DISCIPLE) {
			SekteSuara.line(body, line, fallback, 200);
		} else {
			SekteSuara.body(body, line, fallback);
		}
	}

	/** The existing line used until 39E's clips exist (39.F.7). */
	static String cadangan(String line) {
		return switch (line) {
			case SeteruIds.V_TANTANG, SeteruIds.V_TERIMA -> "leitai_salam";
			case SeteruIds.V_MENANG, SeteruIds.V_SAINGAN -> "victory";
			case SeteruIds.V_KALAH -> "injured";
			case SeteruIds.V_MAAF, SeteruIds.V_DAMAI -> "farewell";
			case SeteruIds.V_KEKASIH -> "beloved";
			case SeteruIds.V_BALAS, SeteruIds.V_KEROYOK, SeteruIds.V_LINDUNGI -> "attack_alt";
			default -> "grudge";
		};
	}

	/** Two bodies face each other. */
	static void hadap(@Nullable DiscipleEntity a, @Nullable DiscipleEntity b) {
		if (a != null && b != null) {
			SosokApi.tatap(a, b);
			SosokApi.tatap(b, a);
			a.getLookControl().setLookAt(b, 30.0F, 30.0F);
			b.getLookControl().setLookAt(a, 30.0F, 30.0F);
		}
	}

	/** Plays feud clip {@code klip} on a body for {@code ticks} (the disciple's own activity when it is ours and free). */
	static void klip(@Nullable DiscipleEntity body, int klip, int ticks, int propKanan, int propKiri, int propPunggung) {
		if (body != null && !body.isRemoved()) {
			SosokApi.gerak(body, klip, 0, ticks, propKanan, propKiri, propPunggung);
		}
	}

	/** Angry puffs over a head ("gelembung marah"). */
	static void marah(@Nullable DiscipleEntity body) {
		if (body != null && body.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, body.getX(), body.getY() + body.getBbHeight() + 0.35D, body.getZ(), 2, 0.25D,
					0.1D, 0.25D, 0.0D);
			SosokApi.setSuasana(body, Gaya.SUASANA_MARAH, 200);
		}
	}

	/** The townsfolk within 16 blocks turn their heads toward the speaker (C39.K2), at most six. */
	static void wargaMenoleh(ServerLevel level, @Nullable DiscipleEntity speaker) {
		if (speaker == null) {
			return;
		}
		List<KultivatorNpc> npcs = level.getEntitiesOfClass(KultivatorNpc.class, speaker.getBoundingBox().inflate(16.0D), KultivatorNpc::isAlive);
		int n = 0;
		for (KultivatorNpc npc : npcs) {
			if (n++ >= 6) {
				break;
			}
			HidupApi.tatap(npc, speaker);
		}
	}

	// ================================================================== the scenes

	/** T0: a sideways glare, a toss of the head and a muttered line; ours glares back a moment later. */
	static void gelembung(MinecraftServer server, Perseteruan f, String jenis) {
		Pemeran p = pemeran(server, f, false, 300);
		if (p == null) {
			return;
		}
		hadap(p.kita, p.mereka);
		marah(p.mereka);
		klip(p.mereka, SeteruIds.KLIP_MELIRIK, 50, 0, 0, 0);
		Naskah.ucap(p.mereka, SeteruIds.V_EJEK, Naskah.baris(f, "tegang"));
		DiscipleEntity ours = p.kita;
		boolean asli = p.kitaAsli;
		nanti(server, 30, () -> {
			if (!asli) {
				klip(ours, SeteruIds.KLIP_MELIRIK, 50, 0, 0, 0);
			}
			marah(ours);
		});
	}

	/**
	 * T1: the quarrel. A short prologue shows what it is about (a scroll snatched, a villager or merchant squeezed for
	 * coins, a laugh over a spilled cup, a cold glare), then the scripted exchange of the cause: their opener, our answer
	 * (humble or hot), their sting and challenge. Our real disciple takes the CEKCOK activity (57).
	 */
	static void cekcok(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, true, 900);
		if (p == null) {
			return;
		}
		hadap(p.kita, p.mereka);
		if (p.kitaAsli && p.kita != null) {
			PelaksanaSeteru.mulai(p.kita, Kegiatan.CEKCOK, 460, p.kita.blockPosition(), null);
		}
		tandaiSibuk(p.kita, 460);
		DiscipleEntity ours = p.kita;
		DiscipleEntity theirs = p.mereka;
		int mulai = prolog(server, p, f);
		boolean hina = SeteruIds.SEBAB_HINAAN.equals(f.sebab);
		boolean ejekGuru = SekteApi.diplomasi().aliran(f.sekte) == 2 || f.korban();
		UUID soul = f.jiwaUtama();
		boolean rendah = soul != null && MuridInfo.dari(f.owner, soul).punya(Sifat.RENDAH_HATI);
		nanti(server, mulai, () -> {
			hadap(ours, theirs);
			klip(theirs, hina ? SeteruIds.KLIP_TERTAWA : SeteruIds.KLIP_CEKCOK, 60, 0, hina ? SeteruIds.PROP_CAWAN_PECAH : 0, 0);
			Naskah.ucap(theirs, ejekGuru ? SeteruIds.V_EJEK_GURU : SeteruIds.V_EJEK, Naskah.sebab(f, 'a'));
			marah(theirs);
			wargaMenoleh(p.level, theirs);
		});
		nanti(server, mulai + 60, () -> {
			hadap(ours, theirs);
			if (!p.kitaAsli) {
				klip(ours, rendah ? SeteruIds.KLIP_MAAF : SeteruIds.KLIP_CEKCOK_BALAS, rendah ? 70 : 120, 0, 0, 0);
			}
			if (rendah) {
				Naskah.ucap(ours, SeteruIds.V_MAAF, Naskah.rendah(f));
			} else {
				Naskah.ucap(ours, SeteruIds.V_BALAS, Naskah.sebab(f, 'b'));
				marah(ours);
			}
			wargaMenoleh(p.level, ours);
		});
		nanti(server, mulai + 125, () -> {
			klip(theirs, SeteruIds.KLIP_CEKCOK, 100, 0, 0, 0);
			Naskah.ucap(theirs, SeteruIds.V_TANTANG, Naskah.sebab(f, 'c'));
			marah(theirs);
		});
	}

	/**
	 * What the quarrel is about, acted out before the words (D-39F). Returns the delay before the exchange starts.
	 * <ul>
	 * <li>a stolen scroll: the thief snatches it and hides it behind the back, the other shouts "thief";</li>
	 * <li>a bully or a squeezed village: their member looms over the nearest townsperson with an open palm, the
	 * townsperson turns to them, ours steps in between with an arm thrown out;</li>
	 * <li>an insult at the tea house: a mocking laugh over a broken cup;</li>
	 * <li>anything else: both glare and toss their heads.</li>
	 * </ul>
	 */
	private static int prolog(MinecraftServer server, Pemeran p, Perseteruan f) {
		DiscipleEntity ours = p.kita;
		DiscipleEntity theirs = p.mereka;
		String sebab = f.sebab == null ? "" : f.sebab;
		switch (sebab) {
			case SeteruIds.SEBAB_GULUNGAN -> {
				boolean kitaPencuri = SeteruIds.SALAH_KITA.equals(f.salah);
				DiscipleEntity thief = kitaPencuri ? ours : theirs;
				DiscipleEntity victim = kitaPencuri ? theirs : ours;
				if (thief == ours && p.kitaAsli) {
					// our real disciple's activity owns its clip; the line still tells what happened
					marah(victim);
				} else {
					klip(thief, SeteruIds.KLIP_CURI, 40, 0, 0, 0);
				}
				nanti(server, 28, () -> {
					hadap(victim, thief);
					Naskah.ucap(victim, SeteruIds.V_CURI, Naskah.baris(f, "curi"));
					marah(victim);
					wargaMenoleh(p.level, victim);
				});
				return 70;
			}
			case SeteruIds.SEBAB_PERUNDUNG, SeteruIds.SEBAB_DESA -> {
				KultivatorNpc korban = theirs == null ? null : wargaTerdekat(p.level, theirs, 12.0D);
				if (theirs != null) {
					if (korban != null) {
						SosokApi.tatap(theirs, korban);
						theirs.getLookControl().setLookAt(korban, 30.0F, 30.0F);
						HidupApi.tatap(korban, theirs);
					}
					klip(theirs, SeteruIds.KLIP_PALAK, 60, 0, 0, 0);
					Naskah.ucap(theirs, SeteruIds.V_EJEK, Naskah.baris(f, "palak"));
				}
				nanti(server, 50, () -> {
					hadap(ours, theirs);
					if (!p.kitaAsli) {
						klip(ours, SeteruIds.KLIP_LINDUNGI, 70, 0, 0, 0);
					}
					Naskah.ucap(ours, SeteruIds.V_LINDUNGI, Naskah.baris(f, "lindungi"));
					if (korban != null && ours != null) {
						HidupApi.tatap(korban, ours);
					}
				});
				return 100;
			}
			case SeteruIds.SEBAB_HINAAN -> {
				klip(theirs, SeteruIds.KLIP_TERTAWA, 40, 0, SeteruIds.PROP_CAWAN_PECAH, 0);
				if (theirs != null) {
					p.level.sendParticles(ParticleTypes.SPLASH, theirs.getX(), theirs.getY() + 0.9D, theirs.getZ(), 6, 0.3D, 0.1D, 0.3D, 0.05D);
				}
				return 40;
			}
			default -> {
				klip(theirs, SeteruIds.KLIP_MELIRIK, 50, 0, 0, 0);
				if (!p.kitaAsli) {
					klip(ours, SeteruIds.KLIP_MELIRIK, 50, 0, 0, 0);
				}
				return 30;
			}
		}
	}

	/** The nearest living townsperson within {@code r} of {@code e}, or null. */
	private static @Nullable KultivatorNpc wargaTerdekat(ServerLevel level, DiscipleEntity e, double r) {
		KultivatorNpc best = null;
		double bestD = Double.MAX_VALUE;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, e.getBoundingBox().inflate(r), KultivatorNpc::isAlive)) {
			double d = npc.distanceToSqr(e);
			if (d < bestD) {
				bestD = d;
				best = npc;
			}
		}
		return best;
	}

	/** An elder of the sect parts them (a pact, or T3 under a pact): "Cukup! ..." */
	static void lerai(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 600);
		ServerPlayer master = server.getPlayerList().getPlayer(f.owner);
		if (p == null) {
			if (master != null) {
				SekteSuara.inner(master, SeteruIds.V_TETUA_LERAI, "elder.welcome");
			}
			return;
		}
		TokohSekte elder = Roster.tetua(server, f.sekte);
		DiscipleEntity body = elder == null ? null : TubuhSeteru.munculkan(p.level, p.pos.offset(-3, 0, 2), elder, TubuhSeteru.Peran.SAKSI,
				f.owner, f.id, 400);
		if (body != null) {
			klip(body, SeteruIds.KLIP_TETUA_LERAI, 80, 0, 0, 0);
			Naskah.tetua(body, SeteruIds.V_TETUA_LERAI, Naskah.baris(f, "lerai"));
			hadap(body, p.mereka);
		} else if (master != null) {
			SekteSuara.inner(master, SeteruIds.V_TETUA_LERAI, "elder.welcome");
		}
		DiscipleEntity ours = p.kita;
		DiscipleEntity theirs = p.mereka;
		// a start at the elder's shout, then both bow their heads
		klip(ours, SeteruIds.KLIP_KETAHUAN, 30, 0, 0, 0);
		klip(theirs, SeteruIds.KLIP_KETAHUAN, 30, 0, 0, 0);
		nanti(server, 30, () -> {
			klip(ours, SeteruIds.KLIP_MAAF, 60, 0, 0, 0);
			klip(theirs, SeteruIds.KLIP_MAAF, 60, 0, 0, 0);
		});
	}

	/** The guilty side bows and apologises in its own words; the other answers with the bow of peace. */
	static void maaf(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 400);
		if (p == null) {
			return;
		}
		hadap(p.kita, p.mereka);
		DiscipleEntity guilty = SeteruIds.SALAH_KITA.equals(f.salah) ? p.kita : p.mereka;
		DiscipleEntity other = guilty == p.kita ? p.mereka : p.kita;
		klip(guilty, SeteruIds.KLIP_MAAF, 70, 0, 0, 0);
		Naskah.ucap(guilty, SeteruIds.V_MAAF, Naskah.baris(f, "maaf"));
		nanti(server, 50, () -> klip(other, SeteruIds.KLIP_DAMAI, 50, 0, 0, 0));
	}

	/** Their elder hands over a pouch: "Sekte kami akan membayar ganti rugi." */
	static void gantiRugi(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 400);
		if (p == null) {
			return;
		}
		TokohSekte elder = Roster.tetua(server, f.sekte);
		DiscipleEntity body = elder == null ? p.mereka : TubuhSeteru.munculkan(p.level, p.pos.offset(-2, 0, 2), elder, TubuhSeteru.Peran.SAKSI,
				f.owner, f.id, 300);
		hadap(body, p.kita);
		klip(body, SeteruIds.KLIP_GANTI_RUGI, 80, SeteruIds.PROP_KANTONG_GANTI_RUGI, 0, 0);
		Naskah.tetua(body, SeteruIds.V_TETUA_GANTI_RUGI, Naskah.baris(f, "ganti_rugi"));
		// their member, shamed, bows behind the elder
		if (body != p.mereka) {
			nanti(server, 40, () -> klip(p.mereka, SeteruIds.KLIP_MAAF, 70, 0, 0, 0));
		}
	}

	/** T5: the loser kneels, fist on the ground, swears revenge; a short thin mist; the winner turns away. */
	static void sumpah(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 400);
		if (p == null) {
			return;
		}
		// the side that lost the last fight swears; without one, theirs
		boolean kitaKalah = !f.riwayat.isEmpty() && f.riwayat.get(f.riwayat.size() - 1).getStringOr("kunci", "").endsWith(".kalah");
		DiscipleEntity loser = kitaKalah ? p.kita : p.mereka;
		DiscipleEntity winner = kitaKalah ? p.mereka : p.kita;
		boolean winnerAsli = !kitaKalah && p.kitaAsli;
		if (loser == null) {
			return;
		}
		if (kitaKalah && p.kitaAsli) {
			PelaksanaSeteru.mulai(loser, Kegiatan.SUMPAH, 200, loser.blockPosition(), null);
		} else {
			klip(loser, SeteruIds.KLIP_SUMPAH, 120, 0, 0, 0);
		}
		tandaiSibuk(loser, 200);
		nanti(server, 38, () -> {
			Naskah.ucap(loser, SeteruIds.V_SUMPAH, Naskah.baris(f, "sumpah"));
			marah(loser);
		});
		p.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, loser.getX(), loser.getY() + 0.2D, loser.getZ(), 6, 0.6D, 0.05D, 0.6D, 0.004D);
		nanti(server, 90, () -> {
			hadap(winner, loser);
			if (!winnerAsli) {
				klip(winner, SeteruIds.KLIP_MELIRIK, 50, 0, 0, 0);
			}
		});
	}

	/** DAMAI: both bow; each says its own line of peace. */
	static void damai(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 300);
		if (p == null) {
			return;
		}
		hadap(p.kita, p.mereka);
		klip(p.kita, SeteruIds.KLIP_DAMAI, 60, 0, 0, 0);
		klip(p.mereka, SeteruIds.KLIP_DAMAI, 60, 0, 0, 0);
		Naskah.ucap(p.mereka, SeteruIds.V_DAMAI, Naskah.baris2(f, "damai", 'a'));
		nanti(server, 50, () -> Naskah.ucap(p.kita, SeteruIds.V_DAMAI, Naskah.baris2(f, "damai", 'b')));
		if (p.mereka != null) {
			p.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.mereka.getX(), p.mereka.getY() + 1.9D, p.mereka.getZ(), 4, 0.4D, 0.2D, 0.4D,
					0.0D);
		}
	}

	/** SAINGAN SEUMUR HIDUP: blades pointed at each other, a fist to the heart, "next season". */
	static void saingan(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 300);
		if (p == null) {
			return;
		}
		hadap(p.kita, p.mereka);
		klip(p.mereka, SeteruIds.KLIP_SAINGAN, 66, 0, 0, 0);
		Naskah.ucap(p.mereka, SeteruIds.V_SAINGAN, Naskah.baris2(f, "saingan", 'a'));
		nanti(server, 36, () -> {
			klip(p.kita, SeteruIds.KLIP_SAINGAN, 66, 0, 0, 0);
			Naskah.ucap(p.kita, SeteruIds.V_SAINGAN, Naskah.baris2(f, "saingan", 'b'));
		});
	}

	/** DENDAM: their member turns away with a trembling fist and one burning look back. */
	static void dendam(MinecraftServer server, Perseteruan f) {
		Pemeran p = pemeran(server, f, false, 300);
		if (p == null) {
			return;
		}
		hadap(p.mereka, p.kita);
		klip(p.mereka, SeteruIds.KLIP_DENDAM, 60, 0, 0, 0);
		marah(p.mereka);
		nanti(server, 30, () -> Naskah.ucap(p.mereka, SeteruIds.V_KALAH, Naskah.baris(f, "dendam")));
	}

	/**
	 * The forbidden lovers' secret meeting (39.F.3): both hold the red string to the heart, glance about, speak softly,
	 * hearts float up. Found out: an elder of their sect appears, both start apart and hide their hands, the elder's
	 * sharp line follows. {@code pos} is the meeting place (the night market of the journey stage).
	 */
	static void rahasia(MinecraftServer server, Perseteruan f, BlockPos pos, boolean ketahuan) {
		if (f.pos == 0L) {
			f.pos = pos.asLong();
		}
		Pemeran p = pemeran(server, f, true, 600);
		if (p == null) {
			return;
		}
		DiscipleEntity ours = p.kita;
		DiscipleEntity theirs = p.mereka;
		hadap(ours, theirs);
		tandaiSibuk(ours, 400);
		if (!p.kitaAsli) {
			klip(ours, SeteruIds.KLIP_RAHASIA, 200, 0, SeteruIds.PROP_TALI_MERAH, 0);
		}
		klip(theirs, SeteruIds.KLIP_RAHASIA, 200, 0, SeteruIds.PROP_TALI_MERAH, 0);
		hati(p.level, theirs);
		Naskah.ucap(theirs, SeteruIds.V_KEKASIH, Naskah.baris2(f, "kekasih", 'a'));
		nanti(server, 60, () -> {
			hati(p.level, ours);
			Naskah.ucap(ours, SeteruIds.V_KEKASIH, Naskah.baris2(f, "kekasih", 'b'));
		});
		if (!ketahuan) {
			return;
		}
		nanti(server, 120, () -> {
			TokohSekte elder = Roster.tetua(server, f.sekte);
			BlockPos at = theirs != null ? theirs.blockPosition().offset(-4, 0, 3) : p.pos.offset(-4, 0, 3);
			DiscipleEntity saksi = elder == null || elder.id.equals(f.tokohUtama()) ? null
					: TubuhSeteru.munculkan(p.level, at, elder, TubuhSeteru.Peran.SAKSI, f.owner, f.id, 300);
			if (!p.kitaAsli) {
				klip(ours, SeteruIds.KLIP_KETAHUAN, 56, 0, 0, 0);
			}
			klip(theirs, SeteruIds.KLIP_KETAHUAN, 56, 0, 0, 0);
			if (saksi != null) {
				hadap(saksi, theirs);
				klip(saksi, SeteruIds.KLIP_CEKCOK, 80, 0, 0, 0);
				marah(saksi);
				Naskah.ucap(saksi, SeteruIds.V_EJEK_GURU, Naskah.baris(f, "ketahuan"));
				wargaMenoleh(p.level, saksi);
			}
		});
	}

	/** Hearts over a head (lovers), a handful. */
	private static void hati(ServerLevel level, @Nullable DiscipleEntity body) {
		if (body != null) {
			level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + body.getBbHeight() + 0.3D, body.getZ(), 2, 0.3D, 0.15D, 0.3D,
					0.0D);
		}
	}

	/** Bodies within {@code r} of {@code pos} that belong to this module (for tests). */
	static int tubuhDekat(ServerLevel level, BlockPos pos, double r) {
		int n = 0;
		for (TubuhSeteru.Tubuh t : TubuhSeteru.semua()) {
			if (t.body.level() == level && t.body.getBoundingBox().intersects(new AABB(pos).inflate(r))) {
				n++;
			}
		}
		return n;
	}

	private Adegan() {
	}
}
