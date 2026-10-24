package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.alchemy.PillKind;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.KontribusiMurid;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.api.PasarMurid;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Terobosan sendiri, the breakthrough a disciple makes on its own (DESIGN 39.5.2; executor TEROBOSAN of C39.B3). It never
 * replaces the master's way (the master within 16 blocks still starts it from the screen); it removes blocker H1.
 * <ol>
 *   <li><b>Bar full at a bottleneck</b>: {@code tunggu.terobosan = day}; the master is asked "<name> is ready to break
 *   into <realm>. Guard them, or let them break through alone?" with {@code [Saya akan menjaga]} (the disciple waits up to
 *   two days for the master) and {@code [Biarkan]} (it goes on now).</li>
 *   <li><b>Waiting for the master</b>: Seimbang {@code tungguTerobosanHari} (2), Penuh 0, Patuh forever; meanwhile it
 *   meditates (Stored Qi grows as before).</li>
 *   <li><b>Preparation</b> ({@link Persiapan}), each step visible: it walks to an open spot under the sky and sits until
 *   health and qi are full (required for a tribulation); it swallows the helpful pills from its bag, buying a missing
 *   Foundation, Golden Core or Tribulation Warding Pill with its own PK at the Paviliun Yue Lu when it can (or, when 39C's
 *   market runs, postponing up to three days to buy one in town); it invites up to two friends ({@link HuFaSahabat}).</li>
 *   <li><b>Heart Trial</b> at the great gates ({@link UjianHati}).</li>
 *   <li><b>Start</b>: {@code DiscipleCultivation.startBreakthroughSelf}; the old omen, strikes, Dao Protector, success,
 *   failure, foundations, heir hex and {@code BreakthroughListener}s are unchanged.</li>
 * </ol>
 * Never off-screen: a breakthrough due while the body was unloaded waits for the load. At most one self-made
 * breakthrough per hall at a time and one start per two minutes (the sky never fills with storm clouds). The prepared
 * state is session data: a reload starts the preparation again.
 */
public final class TerobosanSendiri {

	public static final int TUNGGU_GURU_JAGA_HARI = 2;
	public static final int ANTRE_TICKS = 2400;
	public static final int JALAN_MAKS = 1200;
	public static final int PULIH_MAKS = 4000;
	public static final int PENJAGA_MAKS_TUNGGU = 1200;
	public static final int TUNDA_BELANJA_MAKS_HARI = 3;

	enum Fase { JALAN, PULIH, PIL, HUFA, HATI, MULAI, BERJALAN }

	/** One running self-breakthrough (session only). */
	static final class Proses {
		final UUID owner;
		final UUID soul;
		final Persiapan rencana;
		Fase fase = Fase.JALAN;
		long sejak;
		int pilKe;
		int hatiDetik;
		boolean pilTenang;
		final long dibuat;

		Proses(UUID owner, UUID soul, Persiapan rencana, long now) {
			this.owner = owner;
			this.soul = soul;
			this.rencana = rencana;
			this.sejak = now;
			this.dibuat = now;
		}
	}

	private static final Map<UUID, Proses> PROSES = new HashMap<>();
	/** Game time of the hall's last self start. */
	private static final Map<UUID, Long> TERAKHIR = new HashMap<>();
	/** The master's answer by soul: true "I will guard", false "let them"; applied on the disciple's next second. */
	private static final Map<UUID, Boolean> JAWABAN = new HashMap<>();

	static void init() {
		MandiriApi.register(Kegiatan.TEROBOSAN, new Pelaksana() {
			@Override
			public boolean bisa(DiscipleEntity murid, Konteks konteks) {
				return siap(murid);
			}

			@Override
			public void mulai(DiscipleEntity murid, Konteks konteks) {
				mulaiPersiapan(murid, konteks.tempat(), false);
			}

			@Override
			public boolean jalan(DiscipleEntity murid, Konteks konteks) {
				return PROSES.containsKey(murid.soulId()) || murid.data().breakingThrough();
			}

			@Override
			public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
				Proses proses = PROSES.get(murid.soulId());
				if (batal && proses != null && proses.fase != Fase.BERJALAN) {
					batalkan(murid, null);
				}
			}

			@Override
			public double K(DiscipleEntity murid, Konteks konteks) {
				return 0.0D;
			}
		});
		DiscipleHooks.addQualityBonus((entity, target) -> {
			Proses proses = PROSES.get(entity.soulId());
			return proses != null && proses.rencana.tergesa() ? Persiapan.MUTU_TERGESA : 0.0D;
		});
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onStart(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
				Proses proses = PROSES.get(entity.soulId());
				if (proses == null && target.realm().atLeast(Realm.NASCENT_SOUL)) {
					// The master started this step himself (Golden Core and on): no longer "grown without the master".
					DataTumbuh.sub(entity.data(), "abadi").putBoolean("guru", true);
				}
			}

			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				Proses proses = PROSES.get(entity.soulId());
				if (proses != null && proses.fase == Fase.BERJALAN) {
					selesai(entity, success);
				}
			}

			@Override
			public void onCancel(DiscipleEntity entity, DiscipleBreakthrough.Kind kind) {
				Proses proses = PROSES.remove(entity.soulId());
				if (proses != null) {
					lepasTubuh(entity);
				}
			}
		});
		DiscipleHooks.addOnTaught((entity, master, technique) -> {
			if (entity.data().realm().atLeast(Realm.GOLDEN_CORE)) {
				DataTumbuh.sub(entity.data(), "abadi").putBoolean("guru", true);
			}
		});
	}

	static void clear() {
		PROSES.clear();
		TERAKHIR.clear();
		JAWABAN.clear();
	}

	/** True while the soul prepares or runs a self-made breakthrough. */
	public static boolean sedang(UUID soul) {
		return PROSES.containsKey(soul);
	}

	/** The phase id for the info line ({@code jalan}, {@code pulih} ... {@code berjalan}) or "". */
	static String fase(UUID soul) {
		Proses proses = PROSES.get(soul);
		return proses == null ? "" : proses.fase.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** The master's click: true "I will guard" (wait up to two days), false "let them break through alone". */
	static void jawab(UUID soul, boolean jaga) {
		JAWABAN.put(soul, jaga);
	}

	// ================================================================== readiness

	/** Bar full at a bottleneck, no deviation, a real disciple at home (pure check). */
	static boolean dibottleneck(DiscipleData data) {
		Rank rank = data.rank();
		return !rank.isMax() && rank.isBottleneck() && data.barFull();
	}

	/**
	 * Ready to begin its own breakthrough now (39.5.2 steps 1-2 passed): the module and policy (not Patuh) allow it, the
	 * waiting for the master is over, it is not wounded, its last Heart Trial is two days past, the hall has no other one
	 * running and two minutes passed since the last start, and the old gates (minus the master) hold apart from health,
	 * which the preparation restores.
	 */
	public static boolean siap(DiscipleEntity entity) {
		if (!MandiriApi.aktif() || entity.role() != DiscipleEntity.Role.DISCIPLE || entity.mode().follows()) {
			return false;
		}
		UUID owner = entity.ownerId();
		DiscipleData data = entity.data();
		if (owner == null || !dibottleneck(data) || data.breakingThrough() || data.deviating() || PROSES.containsKey(entity.soulId())) {
			return false;
		}
		Kebijakan policy = MandiriApi.kebijakan(entity);
		if (policy.mode() == ModeMandiri.PATUH) {
			return false;
		}
		long now = entity.level().getGameTime();
		JenisLuka luka = MandiriApi.luka(data, now);
		if (luka == JenisLuka.BERAT || luka == JenisLuka.TERTAWAN || UjianHati.masihTerluka(entity)) {
			return false;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		long since = DataTumbuh.tungguHari(data, DataTumbuh.T_TEROBOSAN);
		if (since < 0L || today < since + hariTunggu(entity, policy)) {
			return false;
		}
		long tunda = DataTumbuh.baca(data).getCompoundOrEmpty("tembus").getLongOr("tunda", -1L);
		if (tunda >= 0L && today < tunda) {
			return false;
		}
		if (aulaSibuk(owner, now)) {
			return false;
		}
		String why = DiscipleCultivation.breakthroughBlockerSelf(entity, 1.0D);
		return why == null || why.endsWith("why.bt_injured") || why.endsWith("why.bt_doomed");
	}

	/** Days the disciple waits for its master: the policy's, two when the master promised to guard, none when told "let them". */
	private static int hariTunggu(DiscipleEntity entity, Kebijakan policy) {
		CompoundTag tembus = DataTumbuh.baca(entity.data()).getCompoundOrEmpty("tembus");
		String jawaban = tembus.getStringOr("jawaban", "");
		if ("biar".equals(jawaban)) {
			return 0;
		}
		if ("jaga".equals(jawaban)) {
			return Math.max(policy.tungguTerobosanHari(), TUNGGU_GURU_JAGA_HARI);
		}
		return policy.tungguTerobosanHari();
	}

	/** Another self-made breakthrough of the hall runs, or the last began less than two minutes ago. */
	private static boolean aulaSibuk(UUID owner, long now) {
		for (Proses proses : PROSES.values()) {
			if (proses.owner.equals(owner) && proses.fase.ordinal() >= Fase.HATI.ordinal()) {
				return true;
			}
		}
		Long last = TERAKHIR.get(owner);
		return last != null && now - last < ANTRE_TICKS;
	}

	// ================================================================== step 1: the bar is full

	/** Once a minute per loaded disciple: notes the full bar (and tells the master once), forgets it when no longer full. */
	static void catatBarPenuh(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		UUID owner = entity.ownerId();
		if (owner == null) {
			return;
		}
		Boolean answer = JAWABAN.remove(entity.soulId());
		if (answer != null) {
			DataTumbuh.sub(data, "tembus").putString("jawaban", answer ? "jaga" : "biar");
		}
		if (!dibottleneck(data)) {
			if (DataTumbuh.tungguHari(data, DataTumbuh.T_TEROBOSAN) >= 0L) {
				DataTumbuh.setTunggu(data, DataTumbuh.T_TEROBOSAN, -1L);
				CompoundTag tembus = DataTumbuh.sub(data, "tembus");
				tembus.remove("jawaban");
				tembus.remove("tunda");
				tembus.remove("tundaSejak");
			}
			return;
		}
		if (DataTumbuh.tungguHari(data, DataTumbuh.T_TEROBOSAN) >= 0L || !MandiriApi.aktif()
				|| MandiriApi.kebijakan(entity).mode() == ModeMandiri.PATUH) {
			return;
		}
		MinecraftServer server = entity.level().getServer();
		DataTumbuh.setTunggu(data, DataTumbuh.T_TEROBOSAN, DataTumbuh.hari(server));
		Rank next = data.rank().next();
		String soul = entity.soulId().toString();
		MutableComponent line = KabarTumbuh.baris("tembus.siap", 0xD9B8FF, KabarTumbuh.nama(entity),
				next.displayName().withColor(next.realm().color()));
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.jaga",
				"/kultivasi perguruan mandiri tembus jaga " + soul, KabarTumbuh.P + "tombol.jaga.hover", 0xFFD54A));
		line.append(Component.literal(" ")).append(KabarTumbuh.tombol(KabarTumbuh.P + "tombol.biarkan",
				"/kultivasi perguruan mandiri tembus biarkan " + soul, KabarTumbuh.P + "tombol.biarkan.hover", 0x8FE3C8));
		KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, line);
	}

	// ================================================================== step 3: preparation

	/**
	 * Begins the preparation at {@code saran} (or an open spot nearby); {@code diTempat}: break through where it sits
	 * (seclusion, recovery already done). False when no spot was found.
	 */
	static boolean mulaiPersiapan(DiscipleEntity entity, @Nullable BlockPos saran, boolean diTempat) {
		UUID owner = entity.ownerId();
		if (owner == null || !(entity.level() instanceof ServerLevel level) || PROSES.containsKey(entity.soulId())) {
			return false;
		}
		BlockPos spot = diTempat ? entity.blockPosition() : TempatTumbuh.terbuka(level, entity, saran);
		if (spot == null) {
			spot = entity.blockPosition();
		}
		Persiapan plan = Persiapan.rencana(entity, spot, entity.getRandom());
		Proses proses = new Proses(owner, entity.soulId(), plan, level.getGameTime());
		PROSES.put(entity.soulId(), proses);
		if (diTempat) {
			proses.fase = Fase.PIL;
		} else {
			entity.setActivity(MandiriKegiatan.ACT_MEDITASI_MANDIRI, spot);
		}
		if (!plan.tergesa()) {
			HuFaSahabat.undang(entity, spot);
		}
		KabarTumbuh.catat(entity, "tembus", plan.tergesa() ? "tembus.tergesa" : "tembus.siap", EntriHarian.Suasana.TENANG, false,
				"r:" + entity.data().rank().next().realm().id());
		return true;
	}

	/** Seclusion found the bar full: break through in place when ready. */
	static void diPengasingan(DiscipleEntity entity) {
		if (siap(entity)) {
			mulaiPersiapan(entity, entity.blockPosition(), true);
		}
	}

	/** Once a second while a preparation runs. */
	static void tick20(DiscipleEntity entity) {
		Proses proses = PROSES.get(entity.soulId());
		if (proses == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		DiscipleData data = entity.data();
		if (proses.fase != Fase.BERJALAN && (entity.getTarget() != null || entity.mode().follows() || !dibottleneck(data))) {
			batalkan(entity, entity.getTarget() != null ? "tembus.batal_tempur" : null);
			return;
		}
		switch (proses.fase) {
			case JALAN -> {
				BlockPos spot = proses.rencana.tempat();
				if (entity.activity() != MandiriKegiatan.ACT_MEDITASI_MANDIRI) {
					entity.setActivity(MandiriKegiatan.ACT_MEDITASI_MANDIRI, spot);
				}
				boolean there = entity.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) <= 2.25D;
				if (there || now - proses.sejak > JALAN_MAKS) {
					if (!there) {
						entity.setActivity(MandiriKegiatan.ACT_MEDITASI_MANDIRI, entity.blockPosition());
					}
					ganti(proses, Fase.PULIH, now);
				}
			}
			case PULIH -> {
				boolean full = entity.getHealth() >= entity.getMaxHealth() - 0.5F && data.qi() >= DiscipleCultivation.maxQi(data) - 1.0D;
				if (full) {
					ganti(proses, proses.rencana.tergesa() ? Fase.HATI : Fase.PIL, now);
				} else if (now - proses.sejak > PULIH_MAKS) {
					batalkan(entity, "tembus.batal_lemah");
				}
			}
			case PIL -> {
				if ((now - proses.sejak) % 40L < 20L) {
					return;
				}
				if (proses.pilKe >= proses.rencana.pil().size()) {
					ganti(proses, Fase.HUFA, now);
					return;
				}
				PillKind kind = proses.rencana.pil().get(proses.pilKe++);
				if (!PilSendiri.punya(entity, kind)) {
					beliDenganPk(entity, kind);
				}
				if (PilSendiri.telan(entity, kind)) {
					proses.pilTenang |= kind == PillKind.HEART_CALMING;
				} else if (Persiapan.pilWajib(kind) && tundaUntukBelanja(entity)) {
					batalkan(entity, "tembus.tunda_belanja");
				}
			}
			case HUFA -> {
				int invited = HuFaSahabat.diundang(entity.soulId());
				int standing = HuFaSahabat.berdiri(entity).size();
				if (invited == 0 || standing >= invited || now - proses.sejak > PENJAGA_MAKS_TUNGGU) {
					ganti(proses, Fase.HATI, now);
				}
			}
			case HATI -> {
				if (!proses.rencana.ujianHati()) {
					ganti(proses, Fase.MULAI, now);
					return;
				}
				if (proses.hatiDetik < UjianHati.DURASI / 20) {
					UjianHati.kabut(entity, proses.hatiDetik++);
					return;
				}
				if (UjianHati.uji(entity, HuFaSahabat.berdiri(entity).size(), proses.pilTenang)) {
					ganti(proses, Fase.MULAI, now);
				} else {
					PROSES.remove(entity.soulId());
					HuFaSahabat.lepasSemua(entity.soulId());
					lepasTubuh(entity);
				}
			}
			case MULAI -> mulai(entity, proses, now);
			case BERJALAN -> {
				if (!data.breakingThrough()) {
					PROSES.remove(entity.soulId());
					HuFaSahabat.lepasSemua(entity.soulId());
					lepasTubuh(entity);
				}
			}
		}
	}

	private static void ganti(Proses proses, Fase fase, long now) {
		proses.fase = fase;
		proses.sejak = now;
	}

	/** A missing Foundation, Golden Core or Tribulation Warding Pill bought with the disciple's own PK (Paviliun Yue Lu). */
	private static void beliDenganPk(DiscipleEntity entity, PillKind kind) {
		KontribusiMurid.Barang barang = switch (kind) {
			case FOUNDATION -> KontribusiMurid.Barang.FOUNDATION;
			case GOLDEN_CORE -> KontribusiMurid.Barang.GOLDEN_CORE;
			case TRIBULATION_WARDING -> KontribusiMurid.Barang.WARDING;
			default -> null;
		};
		if (barang == null || KontribusiMurid.blocker(entity, barang, null) != null) {
			return;
		}
		int price = KontribusiMurid.harga(entity.ownerId(), barang);
		if (!KontribusiMurid.beli(entity, barang, null).isEmpty()) {
			KabarTumbuh.catat(entity, "beli", "beli.paviliun", EntriHarian.Suasana.TENANG, false, "i:kultivator:" + kind.id(), "n:" + price);
		}
	}

	/**
	 * The breakthrough pill is missing and could be bought in town (39C's market runs): postpone to the next day for a
	 * shopping trip, at most three days in all; a hurried disciple never postpones.
	 */
	private static boolean tundaUntukBelanja(DiscipleEntity entity) {
		Proses proses = PROSES.get(entity.soulId());
		if (proses == null || proses.rencana.tergesa() || MandiriApi.pasar() == PasarMurid.NONE) {
			return false;
		}
		MinecraftServer server = entity.level().getServer();
		long today = DataTumbuh.hari(server);
		CompoundTag tembus = DataTumbuh.sub(entity.data(), "tembus");
		long first = tembus.getLongOr("tundaSejak", -1L);
		if (first < 0L) {
			tembus.putLong("tundaSejak", today);
			first = today;
		}
		if (today - first >= TUNDA_BELANJA_MAKS_HARI) {
			return false;
		}
		tembus.putLong("tunda", today + 1L);
		return true;
	}

	// ================================================================== step 5: the start

	private static void mulai(DiscipleEntity entity, Proses proses, long now) {
		if (aulaSibukLain(entity, proses.owner, now)) {
			return;
		}
		double janji = HuFaSahabat.faktorJanji(entity);
		String why = DiscipleCultivation.breakthroughBlockerSelf(entity, janji);
		if (why != null || !DiscipleCultivation.startBreakthroughSelf(entity, janji)) {
			Rank next = entity.data().rank().next();
			KabarTumbuh.catat(entity, "tembus", "tembus.urung", EntriHarian.Suasana.KESAL, false, "r:" + next.realm().id());
			KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, KabarTumbuh.baris("tembus.urung", 0xFF9A7A,
					KabarTumbuh.nama(entity), why == null ? Component.empty() : Component.translatable(why)));
			DataTumbuh.sub(entity.data(), "tembus").putLong("tunda", DataTumbuh.hari(entity.level().getServer()) + 1L);
			batalkan(entity, null);
			return;
		}
		ganti(proses, Fase.BERJALAN, now);
		TERAKHIR.put(proses.owner, now);
		SekteSuara.line(entity, "tembus_sendiri", "cultivate", 0);
		if (entity.level() instanceof ServerLevel level) {
			Fx.ring(level, entity.position().add(0.0D, 0.1D, 0.0D), entity.data().rank().next().realm().color(), 2.0F);
		}
		Rank next = entity.data().rank().next();
		KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, KabarTumbuh.baris("tembus.mulai", 0xFFE27A, KabarTumbuh.nama(entity),
				next.displayName().withColor(next.realm().color()), HuFaSahabat.berdiri(entity).size()));
	}

	/** Another disciple of the hall is in its Heart Trial or tribulation (one at a time). */
	private static boolean aulaSibukLain(DiscipleEntity entity, UUID owner, long now) {
		for (Proses other : PROSES.values()) {
			if (other.owner.equals(owner) && !other.soul.equals(entity.soulId()) && other.fase == Fase.BERJALAN) {
				return true;
			}
		}
		Long last = TERAKHIR.get(owner);
		return last != null && now - last < ANTRE_TICKS;
	}

	/** The breakthrough ended: the record, the bus, the master, the diary, the advancements. */
	private static void selesai(DiscipleEntity entity, boolean success) {
		Proses proses = PROSES.remove(entity.soulId());
		HuFaSahabat.lepasSemua(entity.soulId());
		lepasTubuh(entity);
		DiscipleData data = entity.data();
		MinecraftServer server = entity.level().getServer();
		Rank rank = data.rank();
		UUID owner = entity.ownerId();
		CompoundTag tembus = DataTumbuh.sub(data, "tembus");
		if (success) {
			DataTumbuh.tambahStatistik(data, DataTumbuh.S_TEROBOSAN, 1);
			DataTumbuh.setTunggu(data, DataTumbuh.T_TEROBOSAN, -1L);
			tembus.remove("jawaban");
			tembus.remove("tunda");
			tembus.remove("tundaSejak");
			tembus.putInt("gagalBeruntun", 0);
			Ingatan.remember(server, data, "mandiri.tembus_sendiri", null, 6, KabarTumbuh.P + "ingat.tembus_sendiri",
					rank.displayName().getString());
			KabarTumbuh.catat(entity, "tembus", "tembus.berhasil", EntriHarian.Suasana.BANGGA, true, "r:" + rank.realm().id(),
					"n:" + HuFaSahabat.berdiri(entity).size());
			KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, KabarTumbuh.baris("tembus.berhasil", 0xFFD54A,
					KabarTumbuh.nama(entity), rank.displayName().withColor(rank.realm().color())).withStyle(ChatFormatting.BOLD));
			KabarTumbuh.advancement(entity, "mandiri_tembus");
			if (rank.realm() == Realm.TRUE_IMMORTAL && !DataTumbuh.baca(data).getCompoundOrEmpty("abadi").getBooleanOr("guru", false)) {
				KabarTumbuh.advancement(entity, "mandiri_abadi");
				KabarTumbuh.kirim(entity, null, KabarTumbuh.baris("tembus.abadi", 0xFFE9A8, KabarTumbuh.nama(entity)).withStyle(ChatFormatting.BOLD));
			}
		} else {
			int streak = tembus.getIntOr("gagalBeruntun", 0) + 1;
			tembus.putInt("gagalBeruntun", streak);
			if (streak >= 2) {
				// 39.3.5: two failures in a row push towards seclusion and stir the temptation a little.
				DataTumbuh.ubahIblis(server, entity, 3);
			}
			KabarTumbuh.catat(entity, "tembus", "tembus.gagal", EntriHarian.Suasana.SEDIH, true, "r:" + rank.next().realm().id());
			KabarTumbuh.kirim(entity, Kebijakan.Pemberitahuan.TEROBOSAN, KabarTumbuh.baris("tembus.gagal", 0xFF8A7A,
					KabarTumbuh.nama(entity)));
		}
		MandiriBus.terobosanSendiri(server, owner, entity.soulId(), rank, success);
	}

	/** Two failures in a row (39.3.5: seclusion first). */
	public static boolean gagalBeruntun(DiscipleData data) {
		return DataTumbuh.baca(data).getCompoundOrEmpty("tembus").getIntOr("gagalBeruntun", 0) >= 2;
	}

	/** Ends the preparation without a breakthrough ({@code alasan}: a diary key, or null). */
	static void batalkan(DiscipleEntity entity, @Nullable String alasan) {
		Proses proses = PROSES.remove(entity.soulId());
		if (proses == null) {
			return;
		}
		HuFaSahabat.lepasSemua(entity.soulId());
		lepasTubuh(entity);
		if (alasan != null) {
			KabarTumbuh.catat(entity, "tembus", alasan, EntriHarian.Suasana.KESAL, false, "r:" + entity.data().rank().next().realm().id());
		}
	}

	/** The body leaves the breakthrough seat (only the pose this module set). */
	private static void lepasTubuh(DiscipleEntity entity) {
		if (entity.activity() == MandiriKegiatan.ACT_MEDITASI_MANDIRI && MandiriApi.kegiatan(entity) != Kegiatan.MEDITASI) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** {@code uji ... tembus <murid>}: the whole flow now, the waiting skipped (the preparation shortened to its steps). */
	static boolean paksa(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!dibottleneck(data) || data.breakingThrough() || PROSES.containsKey(entity.soulId())) {
			return false;
		}
		MinecraftServer server = entity.level().getServer();
		DataTumbuh.setTunggu(data, DataTumbuh.T_TEROBOSAN, DataTumbuh.hari(server) - 30L);
		DataTumbuh.sub(data, "tembus").putString("jawaban", "biar");
		DataTumbuh.sub(data, "tembus").remove("tunda");
		TERAKHIR.remove(entity.ownerId());
		return mulaiPersiapan(entity, null, false);
	}

	/** Every disciple of a hall: drops the sessions of the ones unloaded (called when a body unloads is not needed). */
	static void bersihkan(MinecraftServer server) {
		PROSES.values().removeIf(proses -> {
			for (DiscipleEntity e : Disciples.loaded(proses.owner)) {
				if (e.soulId().equals(proses.soul)) {
					return false;
				}
			}
			HuFaSahabat.lepasSemua(proses.soul);
			return true;
		});
	}

	private TerobosanSendiri() {
	}
}
