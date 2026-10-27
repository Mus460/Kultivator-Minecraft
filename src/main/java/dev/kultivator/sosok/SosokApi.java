package dev.kultivator.sosok;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.langit.api.LangitConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * The server API of the shared Sosok look and animation system (DESIGN 40.7.3 C40.I1). DESIGN 38 (town life, places,
 * themed cities), DESIGN 39 (autonomous disciples) and 40's own commands call only this class; nobody sets skins,
 * {@code DATA_SKIN} or models of their own.
 * <p>
 * <b>Bodies.</b> A body takes part by implementing {@link Pemegang} ({@code DiscipleEntity} and {@code KultivatorNpc},
 * added by 40I): its synced ints {@code DATA_SOSOK}, {@code DATA_GAYA}, {@code DATA_GERAK}, {@code DATA_GERAK_T0},
 * {@code DATA_PROP}, {@code DATA_LOOK_AT} (and {@code DATA_RUPA} on NPCs), one {@link Waktu} for the timers, and a call
 * to {@link #tik(LivingEntity)} from its server tick. For any other entity the getters give defaults
 * ({@link Sosok#dariBenihEntitas}, {@link Gaya#NETRAL}) and the setters do nothing. Setters never act on the client side
 * (synced data is the server's).
 * <p>
 * <b>Clips.</b> {@link #gerak} sends a {@link GerakId} clip. A clip with a duration (the caller's {@code tick}, or
 * {@link GerakId#durasiSekali} for a once clip) is an interruption: what ran before keeps its phase underneath and
 * comes back when it ends, and an untimed clip that arrives meanwhile (a schedule re-asserting its loop) becomes that
 * underlying clip instead of cutting the interruption short. Re-sending the running untimed clip does nothing (its
 * phase is not restarted). Ids 150-164 set disciple activity 13-27 instead (the existing function poses).
 */
public final class SosokApi {

	/**
	 * A body whose look and animation Sosok drives. Accessors map one-to-one to the body's synced data; the body owns
	 * the storage (entity data accessors), saving (disciples save {@code sosok} and {@code gaya} only) and the
	 * {@code Attributes.SCALE} it gets through {@link SosokApi#setSosok}.
	 */
	public interface Pemegang {

		/** {@code DATA_SOSOK}: {@link Sosok#pack()}, 0 = none yet. */
		int sosokData();

		void setSosokData(int packed);

		/** {@code DATA_GAYA}: {@link Gaya#pack()}. */
		int gayaData();

		void setGayaData(int packed);

		/** {@code DATA_GERAK}: {@link GerakId#pack}, 0 = no server clip. */
		int gerakData();

		/** {@code DATA_GERAK_T0}: game time {@code & 0x7FFFFFFF} when the clip started. */
		int gerakT0();

		void setGerakData(int gerak, int t0);

		/** {@code DATA_PROP}: {@link PropId#pack}, 0 = the clip's own props. */
		int propData();

		void setPropData(int packed);

		/** {@code DATA_LOOK_AT}: entity id looked at, -1 = none. */
		int tatapData();

		void setTatapData(int entityId);

		/** {@code DATA_RUPA}: {@link RupaKunci} text form, empty = the body's own role decides. */
		default String rupaData() {
			return "";
		}

		default void setRupaData(String rupa) {
		}

		/** The body's timers (one instance per body, created with it). */
		Waktu waktuSosok();

		/**
		 * Lets a body show talking its own way (an NPC raises its old {@code FLAG_TALK}); return false to get clip
		 * {@link GerakId#BICARA} for {@code tick} ticks instead.
		 */
		default boolean bicaraSendiri(int tick) {
			return false;
		}
	}

	/** Per-body timers of the timed parts of this API (not saved: clips, moods and activities are session state). */
	public static final class Waktu {
		private long gerakHabis;
		private int gerakBawah;
		private int t0Bawah;
		private int propBawah;
		private long suasanaHabis;
		private int aktivitasKita;
		private long aktivitasHabis;

		/** Clears every timer (the body was reset or reloaded). */
		public void reset() {
			gerakHabis = 0L;
			gerakBawah = 0;
			t0Bawah = 0;
			propBawah = 0;
			suasanaHabis = 0L;
			aktivitasKita = 0;
			aktivitasHabis = 0L;
		}
	}

	/** {@link Pemegang#tatapData()} of "looking at nobody". */
	public static final int TATAP_TIDAK_ADA = -1;

	// ================================================================== identity

	/** The body's Sosok (the entity-seed stand-in when it has none). */
	public static Sosok sosok(LivingEntity e) {
		Pemegang p = pemegang(e);
		return Sosok.dari(p != null ? p.sosokData() : Sosok.KOSONG, e.getUUID());
	}

	/** Sets the body's Sosok and its {@code Attributes.SCALE} base value (40.2.3). */
	public static void setSosok(LivingEntity e, Sosok sosok) {
		Pemegang p = pemegang(e);
		if (p == null || sosok == null || klien(e)) {
			return;
		}
		p.setSosokData(sosok.pack());
		terapkanSkala(e, sosok);
	}

	/**
	 * Changes only the visible age group (39 may model real age with it; disciples age by themselves through 40I's daily
	 * tick, and rupa freezes from Foundation up).
	 */
	public static void setUsia(LivingEntity e, int usia) {
		setSosok(e, sosok(e).withUsia(usia));
	}

	/** True for a female body. */
	public static boolean perempuan(LivingEntity e) {
		return sosok(e).perempuan();
	}

	/** The visible age group ({@link Sosok#ANAK} .. {@link Sosok#TUA}). */
	public static int usia(LivingEntity e) {
		return sosok(e).usia();
	}

	/**
	 * Writes the Sosok's {@code Attributes.SCALE} base value so hitbox, eye height and name plate follow the body size.
	 * Bodies call it when they load their saved Sosok; {@link #setSosok} calls it itself.
	 */
	public static void terapkanSkala(LivingEntity e, Sosok sosok) {
		if (klien(e)) {
			return;
		}
		AttributeInstance scale = e.getAttribute(Attributes.SCALE);
		float target = SosokConfig.skala(sosok);
		if (scale != null && Math.abs(scale.getBaseValue() - target) > 1.0E-4) {
			scale.setBaseValue(target);
		}
	}

	// ================================================================== style

	/** The body's changeable style ({@link Gaya#NETRAL} when it has none). */
	public static Gaya gaya(LivingEntity e) {
		Pemegang p = pemegang(e);
		return p != null ? Gaya.unpack(p.gayaData()) : Gaya.NETRAL;
	}

	/** Sets the whole style at once (40I uses it for the shown traits). */
	public static void setGaya(LivingEntity e, Gaya gaya) {
		Pemegang p = pemegang(e);
		if (p == null || gaya == null || klien(e)) {
			return;
		}
		p.setGayaData(gaya.pack());
	}

	/**
	 * Sets a disciple's life path look by its DESIGN 39 id or alias ({@link SosokJalan}); null or unknown clears it. 39
	 * calls it whenever the path is chosen, changed, overridden by {@code iblis} or redeemed.
	 */
	public static void setJalan(DiscipleEntity murid, @Nullable String jalanId) {
		setGaya(murid, gaya(murid).withJalan(SosokJalan.bit(jalanId)));
	}

	/** Sets the mood ({@code Gaya.SUASANA_*}); after {@code tick} ticks it returns to neutral (0 = stays). */
	public static void setSuasana(LivingEntity e, int suasana, int tick) {
		Pemegang p = pemegang(e);
		if (p == null || klien(e)) {
			return;
		}
		setGaya(e, gaya(e).withSuasana(suasana));
		p.waktuSosok().suasanaHabis = tick > 0 && suasana != Gaya.SUASANA_NETRAL ? waktu(e) + tick : 0L;
	}

	/** Sets the wound ({@code Gaya.LUKA_*}). */
	public static void setLuka(LivingEntity e, int luka) {
		setGaya(e, gaya(e).withLuka(luka));
	}

	/** Sets the special outfit ({@code Gaya.BUSANA_*}, 0 = automatic). */
	public static void setBusanaKhusus(LivingEntity e, int busana) {
		setGaya(e, gaya(e).withBusanaKhusus(busana));
	}

	/** Sets an NPC's look key ({@link RupaKunci} text form); empty returns the NPC to its role's look. */
	public static void setRupa(KultivatorNpc npc, String kunciRupa) {
		Pemegang p = pemegang(npc);
		if (p == null || klien(npc)) {
			return;
		}
		RupaKunci kunci = RupaKunci.parse(kunciRupa);
		for (java.util.function.UnaryOperator<RupaKunci> penyaring : PENYARING_RUPA) {
			kunci = penyaring.apply(kunci);
		}
		p.setRupaData(kunci.format());
	}

	private static final java.util.List<java.util.function.UnaryOperator<RupaKunci>> PENYARING_RUPA = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Registers a rewriter every {@link #setRupa} key passes through (DESIGN 41.11.5: 41 turns {@code tetua_sekte|sekte=npc:s3}
	 * into the sect's style row {@code tetua_sekte.pedang|sekte=npc:s3}). It must return the key itself when it does not apply.
	 */
	public static void tambahPenyaringRupa(java.util.function.UnaryOperator<RupaKunci> penyaring) {
		PENYARING_RUPA.add(penyaring);
	}

	/** The body's look key ({@link RupaKunci#KOSONG} when none). */
	public static RupaKunci rupa(LivingEntity e) {
		Pemegang p = pemegang(e);
		return p != null ? RupaKunci.parse(p.rupaData()) : RupaKunci.KOSONG;
	}

	// ================================================================== clips

	/** Plays a clip with the clip's own props; see {@link #gerak(LivingEntity, int, int, int, int, int, int)}. */
	public static void gerak(LivingEntity e, int klip, int varian, int tick) {
		gerak(e, klip, varian, tick, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
	}

	/**
	 * Plays clip {@code klip} (a {@link GerakId}, or a 200-399 id registered by 39/38) with variant {@code varian} and the
	 * given props (0 = the clip's default for that slot). {@code tick > 0} plays it for that long, then what ran before
	 * returns; {@code tick <= 0} plays a once clip for {@link GerakId#durasiSekali} and anything else until replaced or
	 * {@link #henti}. Ids 150-164 set disciple activity 13-27 instead (only for a master's disciple that is not busy with
	 * another module's activity, and only while the module owning that activity runs). Clip 0 stops.
	 */
	public static void gerak(LivingEntity e, int klip, int varian, int tick, int propKanan, int propKiri,
			int propPunggung) {
		if (klien(e)) {
			return;
		}
		if (GerakId.fungsi(klip)) {
			fungsi(e, klip, tick);
			return;
		}
		Pemegang p = pemegang(e);
		if (p == null) {
			return;
		}
		int data = GerakId.pack(klip, varian);
		if (data == 0) {
			henti(e);
			return;
		}
		int prop = PropId.pack(propKanan, propKiri, propPunggung);
		Waktu w = p.waktuSosok();
		long now = waktu(e);
		int durasi = tick > 0 ? tick : GerakId.durasiSekali(klip, varian);
		if (durasi > 0) {
			if (w.gerakHabis == 0L) {
				w.gerakBawah = p.gerakData();
				w.t0Bawah = p.gerakT0();
				w.propBawah = p.propData();
			}
			w.gerakHabis = now + durasi;
			p.setGerakData(data, t0(now));
			p.setPropData(prop);
			return;
		}
		if (w.gerakHabis != 0L) {
			if (w.gerakBawah != data || w.propBawah != prop) {
				w.gerakBawah = data;
				w.t0Bawah = t0(now);
				w.propBawah = prop;
			}
			return;
		}
		if (p.gerakData() == data && p.propData() == prop) {
			return;
		}
		p.setGerakData(data, t0(now));
		p.setPropData(prop);
	}

	/**
	 * Plays a looping clip in step with others from the given start time (paired sparring 66/67, sedan bearers, oars):
	 * every body given the same {@code t0} shows the same phase. Untimed; replaces any interruption.
	 */
	public static void gerakSinkron(LivingEntity e, int klip, long t0) {
		Pemegang p = pemegang(e);
		if (p == null || klien(e) || GerakId.fungsi(klip)) {
			return;
		}
		int data = GerakId.pack(klip, 0);
		Waktu w = p.waktuSosok();
		w.gerakHabis = 0L;
		w.gerakBawah = 0;
		w.t0Bawah = 0;
		w.propBawah = 0;
		p.setGerakData(data, data == 0 ? 0 : (int) (t0 & 0x7FFFFFFFL));
		p.setPropData(PropId.TIDAK_ADA);
	}

	/** Stops every server clip (and an activity this API set), clearing what was underneath. */
	public static void henti(LivingEntity e) {
		Pemegang p = pemegang(e);
		if (klien(e)) {
			return;
		}
		if (p != null) {
			Waktu w = p.waktuSosok();
			int aktivitas = w.aktivitasKita;
			long suasana = w.suasanaHabis;
			w.reset();
			w.suasanaHabis = suasana;
			p.setGerakData(0, 0);
			p.setPropData(PropId.TIDAK_ADA);
			if (aktivitas != 0 && e instanceof DiscipleEntity murid && murid.activity() == aktivitas) {
				murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
	}

	/** Turns the head toward {@code target} (null = back to the walking direction and the client's own choices). */
	public static void tatap(LivingEntity e, @Nullable Entity target) {
		Pemegang p = pemegang(e);
		if (p == null || klien(e)) {
			return;
		}
		int id = target == null || target == e ? TATAP_TIDAK_ADA : target.getId();
		if (p.tatapData() != id) {
			p.setTatapData(id);
		}
	}

	/**
	 * Moves the mouth for {@code tick} ticks (a spoken line; {@code VoiceDisciples.say} calls it with the line's
	 * length). The body may do it its own way ({@link Pemegang#bicaraSendiri}); otherwise clip {@link GerakId#BICARA}
	 * plays as an interruption (head layer only, so the arms keep working).
	 */
	public static void bicara(LivingEntity e, int tick) {
		Pemegang p = pemegang(e);
		if (p == null || klien(e) || tick <= 0) {
			return;
		}
		if (p.bicaraSendiri(tick)) {
			return;
		}
		Waktu w = p.waktuSosok();
		int sekarang = GerakId.klip(p.gerakData());
		if (w.gerakHabis != 0L && sekarang != GerakId.BICARA) {
			return;
		}
		gerak(e, GerakId.BICARA, 0, tick, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
	}

	/** The clip id the body plays now (0 none; 150-164 are never sent, they are activities). */
	public static int gerakSekarang(LivingEntity e) {
		Pemegang p = pemegang(e);
		return p != null ? GerakId.klip(p.gerakData()) : GerakId.TIDAK_ADA;
	}

	/** The id of the entity the body looks at, or {@link #TATAP_TIDAK_ADA}. */
	public static int tatapSekarang(LivingEntity e) {
		Pemegang p = pemegang(e);
		return p != null ? p.tatapData() : TATAP_TIDAK_ADA;
	}

	// ================================================================== ticking

	/**
	 * Ends the timers that ran out: a timed clip gives way to what ran under it (same phase), a timed mood returns to
	 * neutral, a timed function activity ends. Bodies call it once per server tick; it reads three longs when nothing is
	 * due.
	 */
	public static void tik(LivingEntity e) {
		Pemegang p = pemegang(e);
		if (p == null || klien(e)) {
			return;
		}
		Waktu w = p.waktuSosok();
		if (w.gerakHabis == 0L && w.suasanaHabis == 0L && w.aktivitasHabis == 0L) {
			return;
		}
		long now = waktu(e);
		if (w.gerakHabis != 0L && now >= w.gerakHabis) {
			p.setGerakData(w.gerakBawah, w.gerakBawah == 0 ? 0 : w.t0Bawah);
			p.setPropData(w.propBawah);
			w.gerakHabis = 0L;
			w.gerakBawah = 0;
			w.t0Bawah = 0;
			w.propBawah = 0;
		}
		if (w.suasanaHabis != 0L && now >= w.suasanaHabis) {
			w.suasanaHabis = 0L;
			p.setGayaData(Gaya.unpack(p.gayaData()).withSuasana(Gaya.SUASANA_NETRAL).pack());
		}
		if (w.aktivitasHabis != 0L && now >= w.aktivitasHabis) {
			int aktivitas = w.aktivitasKita;
			w.aktivitasHabis = 0L;
			w.aktivitasKita = 0;
			if (aktivitas != 0 && e instanceof DiscipleEntity murid && murid.activity() == aktivitas) {
				murid.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
	}

	// ================================================================== internals

	/** The body's Sosok accessors, or null when it does not take part. */
	public static @Nullable Pemegang pemegang(@Nullable Entity e) {
		return e instanceof Pemegang p ? p : null;
	}

	/**
	 * Function pose ids 150-164 -> disciple activity 13-27 (40.5.12). Only a master's disciple that is free (or still in
	 * an activity this API gave it), and only while the module that owns the activity runs (36 modules through
	 * {@link SekteIds#activityRuns}, Sekte Langit through {@link LangitConfig#on()}).
	 */
	private static void fungsi(LivingEntity e, int klip, int tick) {
		if (!(e instanceof DiscipleEntity murid) || murid.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		int aktivitas = GerakId.aktivitasFungsi(klip);
		boolean jalan = aktivitas <= SekteIds.ACT_LAST ? SekteIds.activityRuns(aktivitas) : LangitConfig.on();
		if (aktivitas < 0 || !jalan) {
			return;
		}
		Pemegang p = pemegang(e);
		Waktu w = p != null ? p.waktuSosok() : null;
		int sekarang = murid.activity();
		boolean milikKita = w != null && w.aktivitasKita != 0 && sekarang == w.aktivitasKita;
		if (sekarang != DiscipleEntity.ACTIVITY_NONE && !milikKita) {
			return;
		}
		if (sekarang != aktivitas) {
			murid.setActivity(aktivitas, null);
		}
		if (w != null) {
			w.aktivitasKita = aktivitas;
			w.aktivitasHabis = tick > 0 ? waktu(e) + tick : 0L;
		}
	}

	private static boolean klien(LivingEntity e) {
		return e.level().isClientSide();
	}

	private static long waktu(LivingEntity e) {
		return e.level().getGameTime();
	}

	private static int t0(long now) {
		return (int) (now & 0x7FFFFFFFL);
	}

	private SosokApi() {
	}
}
