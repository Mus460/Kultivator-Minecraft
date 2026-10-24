package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.dunia.kota.NpcPoses;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.sosok.SosokJalan;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/**
 * The Gerak controller (DESIGN 40.5.1, 40.5.4): once per frame per Sosok body, in the renderer's extract step after
 * {@code PoseRenderData.extract}, it evaluates the layers bottom to top and writes the result into the render state.
 * <pre>
 *  prio 1-2  duel 35 / cast 27 (data POSE)      -> layers 4-8 fade out in 4 ticks, no gaze or breath, angry face
 *  prio 3    meditation seat                    -> seated clips (DUDUK/BERSILA) lay arms and head on top
 *  prio 4    server once clip (DATA_GERAK)      in 3 / out 4
 *  prio 5    disciple activity                  in 6 / out 6, owns the body: 6-8 are suppressed; a mapped Gerak clip
 *            (DESIGN 39 activities 40-60) is sampled here, the old duties 1-4 and function poses 13-27 stay DutyPoses'
 *  prio 6    server loop clip (schedules 38, activities 39; a town NPC's old flags via NpcPoses)   in 6 / out 6
 *  prio 7    client reactions and idle gestures in 3 / out 4, not while 4-6 hold
 *  prio 8    idle stance by role/rank/path/trait (still, or JALAN_OK stances while walking)  in 10 / out 10
 *  prio 9    walk profile (changes vanilla's walk)
 *  +         additive: breath, gaze, glances, face, springs, props
 * </pre>
 * The 18 PoseLibrary channels become a {@code CastPoses.Snapshot} stored in {@code POSE} when no cast or duel pose is
 * there (so PoseApplier puts it on every PlayerModel of the body, as {@code NpcPoses} did); Gerak's own channels, the
 * additive offsets, face, springs and props go into {@link GerakData}. At most three clips are sampled per body (once,
 * loop, stance; reactions replace nothing), every channel is smoothed with {@code a = 1 - 0.72^dt}, nothing is
 * allocated on the hot path. Level of detail ({@code SosokDetail.lod}, 40R) and the per-frame budgets of 40.6.1 limit
 * the work: KURANG samples every second frame without face, gaze or springs, SILUET freezes the stance, JAUH leaves
 * vanilla alone. Not evaluated again in Iris' shadow pass (the extracted state is reused).
 */
public final class GerakPengendali {

	private static final Int2ObjectOpenHashMap<GerakKeadaan> KEADAAN = new Int2ObjectOpenHashMap<>();
	private static final SikapTabel.Hasil HASIL = new SikapTabel.Hasil();
	private static final float D = Mth.DEG_TO_RAD;

	public static final int LOD_PENUH = 0, LOD_KURANG = 1, LOD_SILUET = 2, LOD_JAUH = 3;

	// ------------------------------------------------------------------ per-frame budgets (40.6.1)

	private static long frameKunci = Long.MIN_VALUE;
	private static final float[] JARAK = new float[256];
	private static int jarakN;
	private static float batasPenuhSq = Float.MAX_VALUE;
	private static float batasPegasSq = Float.MAX_VALUE;

	/** Evaluates a disciple or a town NPC (their look key comes from the entity). */
	public static @Nullable CastPoses.Snapshot evaluasi(LivingEntity e, DiscipleRenderState st, float age) {
		return evaluasi(e, st, age, null);
	}

	/**
	 * Evaluates one body this frame. {@code kunciRupa} is the look key for bodies that are not disciples or town NPCs
	 * (Dimension D people {@code d.*}, rogue cultivators {@code liar.*}, the envoy {@code utusan_langit}); null takes it
	 * from the entity. Returns the idle snapshot it stored in {@code POSE} (null when a cast or duel pose is there or
	 * nothing drives the body).
	 */
	public static @Nullable CastPoses.Snapshot evaluasi(LivingEntity e, DiscipleRenderState st, float age, @Nullable String kunciRupa) {
		GerakKatalog.pastikan();
		int id = e.getId();
		GerakKeadaan k = KEADAAN.get(id);
		if (k == null) {
			k = new GerakKeadaan(id);
			KEADAAN.put(id, k);
		}
		if (!Float.isNaN(k.ageLalu) && age + 1.0F < k.ageLalu) {
			k.reset();
		}
		float dt = Float.isNaN(k.ageLalu) ? 1.0F : Mth.clamp(age - k.ageLalu, 0.0F, 20.0F);
		k.ageLalu = age;
		long now = e.level().getGameTime();
		k.dilihat = now;
		float pt = Mth.clamp(age - e.tickCount, 0.0F, 1.0F);
		FabricRenderState data = (FabricRenderState) st;
		GerakData d = k.data;
		d.kosongkan();

		// level of detail and the budgets
		frame(now, pt);
		float jarakSq = (float) st.distanceToCameraSq;
		catatJarak(jarakSq);
		int lod = lod(st);
		if (lod == LOD_PENUH && jarakSq > batasPenuhSq) {
			lod = LOD_KURANG;
		}
		d.lod = lod;
		if (lod == LOD_JAUH) {
			data.setData(GerakData.KUNCI, d);
			return null;
		}
		// movement (run blend, turn, start/stop, landing) for the walk profile and the shared polish
		Langkah.perbarui(k.langkah, st, age, e.onGround(), false);
		if (lod <= LOD_KURANG) {
			d.langkah = k.langkah;
		}

		// who the body is
		SosokApi.Pemegang p = SosokApi.pemegang(e);
		int sp = p != null ? p.sosokData() : 0;
		if (sp != k.sosokPacked || k.sosok == null) {
			k.sosokPacked = sp;
			k.sosok = SosokApi.sosok(e);
			k.benih = k.sosok.benih() ^ id * 0x45D9F3B;
		}
		int gp = p != null ? p.gayaData() : 0;
		if (gp != k.gayaPacked) {
			k.gayaPacked = gp;
			k.gaya = Gaya.unpack(gp);
		}
		Sosok sosok = k.sosok;
		Gaya gaya = k.gaya;
		boolean murid = e instanceof DiscipleEntity;
		boolean perempuan = sosok.perempuan();
		String kunci = kunciRupa != null ? kunciRupa : kunci(e, st, k, p);
		int sifatBit = sifatBit(gaya);
		int ciri = ciri(e, st, sosok, gaya, kunci);
		d.ciri = ciri;
		d.sifatBit = sifatBit;
		boolean kerjaAnak = sosok.anak();
		d.skalaAmp = kerjaAnak ? 1.15F : sosok.tua() ? 0.8F : 1.0F;
		d.skalaWaktu = kerjaAnak ? 1.25F : sosok.tua() ? 0.77F : 1.0F;
		int pangkat = murid ? st.sectRank : -1;
		boolean pemimpin = e instanceof KultivatorNpc npc && npc.hasFlag(KultivatorNpc.FLAG_LEADER);
		SikapTabel.pilih(kunci, sosok, gaya, pangkat, pemimpin, murid, k.benih, HASIL);

		// cast and duel own the body (priority 1-2)
		CastPoses.Snapshot pose = PoseRenderData.pose(st);
		boolean castDuel = pose != null && pose != k.snap && pose != k.snapDua;
		boolean bertarung = murid ? (st.flags & DiscipleLook.FIGHTING) != 0
				: e instanceof KultivatorNpc npc2 && npc2.hasFlag(KultivatorNpc.FLAG_HOSTILE);
		k.bebas.nyala(!castDuel, age);
		float bebas = k.bebas.bobot(age);
		float meditasi = MeditationPose.strength(st);
		boolean walking = st.walkAnimationSpeed > 0.15F;

		// layer 5: the disciple's activity pose (old duties and function poses drawn by DutyPoses, Gerak clips here)
		int act = murid ? st.activity : 0;
		boolean terbang = act == LangitIds.ACT_TERBANG_PEDANG || act == MandiriKegiatan.ACT_TERBANG_JAUH;
		boolean aktPunya = act != 0 && (act >= DiscipleEntity.ACTIVITY_WATER && act <= DiscipleEntity.ACTIVITY_FARM
				|| SektePoses.handles(act) || act >= LangitIds.ACT_TERBANG_PEDANG && act <= LangitIds.ACT_MEMANDANG
				|| GerakKatalog.klipAktivitas(act) != 0);
		int aktId = act >= 28 ? GerakKatalog.klipAktivitas(act) : 0;
		int aktVarian = GerakKatalog.varianAktivitas(act);
		Klip aktDasar = aktId != 0 ? GerakKatalog.klip(aktId) : null;
		Klip aktK = aktDasar != null && aktDasar.sumber() != Klip.Sumber.FUNGSI ? aktDasar.pilih(aktVarian, ciri, sifatBit) : null;
		// a Gerak activity clip may play while walking (JALAN_OK: carrying home) or on the seat (DUDUK, BERSILA)
		boolean aktJalan = terbang || st.walkAnimationSpeed <= 0.1F || aktK != null && aktK.punya(Klip.JALAN_OK);
		boolean aktDuduk = st.seated <= 0.5F || aktK != null && aktK.punya(Klip.DUDUK | Klip.BERSILA);
		boolean aktOn = aktPunya && !castDuel && !bertarung && aktJalan && aktDuduk;
		k.aktivitas.nyala(aktOn, age);
		float wAkt = k.aktivitas.bobot(age);
		k.aktKlip.minta(aktId, aktVarian, aktOn ? aktK : null, age, k.aktKlip.k == aktK ? k.aktKlip.mulai : age);
		float w5 = k.aktKlip.bobot(age) * bebas;
		// the legacy duty and function poses are drawn by DutyPoses on the model; a Gerak clip is sampled here
		d.aktivitas = wAkt > 0.5F && aktK == null;

		// layers 4 and 6: the server clip
		int g = p != null ? p.gerakData() : 0;
		boolean dariBendera = false;
		if (g == 0) {
			// no server clip: a town NPC's flag clip, or the clip a renderer asked for (SosokRender.mintaKlip: Dimension D
			// people, sea folk, ghosts); both keep their own start time so a loop does not restart every frame
			int b = 0;
			if (e instanceof KultivatorNpc npcB) {
				b = NpcPoses.klip(npcB, walking);
			} else {
				dev.kultivator.client.sosok.SosokRender.Keadaan sk = dev.kultivator.client.sosok.SosokRender.keadaan(st);
				if (sk != null && sk.klipKlien != 0) {
					b = GerakId.pack(sk.klipKlien, sk.varianKlien);
				}
			}
			if (b != k.bendera) {
				k.bendera = b;
				k.benderaMulai = age;
			}
			g = b;
			dariBendera = b != 0;
		} else {
			k.bendera = 0;
		}
		int klipId = GerakId.klip(g);
		int varian = GerakId.varian(g);
		Klip dasar = klipId != 0 ? GerakKatalog.klip(klipId) : null;
		if (dasar != null && dasar.sumber() == Klip.Sumber.FUNGSI) {
			dasar = null;
		}
		boolean pincang = gaya.luka() == Gaya.LUKA_PINCANG || murid && e.getHealth() < e.getMaxHealth() * 0.35F && e.getHealth() > 0.0F;
		if (dasar == null && pincang) {
			dasar = GerakKatalog.klip(GerakId.PINCANG);
			klipId = GerakId.PINCANG;
			varian = 0;
		} else if (dasar == null && gaya.luka() == Gaya.LUKA_LENGAN) {
			dasar = GerakKatalog.klip(GerakId.LENGAN_DIGENDONG);
			klipId = GerakId.LENGAN_DIGENDONG;
			varian = 0;
		}
		Klip kl = dasar != null ? dasar.pilih(varian, ciri, sifatBit) : null;
		int t0 = p != null ? p.gerakT0() : 0;
		float sejak = dariBendera ? age - k.benderaMulai : ((int) (now & 0x7FFFFFFFL) - t0 & 0x7FFFFFFF) + pt;
		boolean sekaliMode = kl != null && !kl.loop();
		if (sekaliMode && kl.mode() == Klip.Mode.SEKALI && sejak > kl.durasi() * d.skalaWaktu + 4.0F) {
			kl = null;
		}
		k.sekali.minta(klipId, varian, sekaliMode ? kl : null, age, age - sejak);
		k.loop.minta(klipId, varian, !sekaliMode ? kl : null, age, age);
		float w4 = k.sekali.bobot(age) * bebas;
		float w6 = k.loop.bobot(age) * bebas * (1.0F - wAkt);

		// layer 7: reactions and idle gestures (not while 4-6 hold the body)
		GerakReaksi.cek(e, k, age, now, gaya);
		boolean sibuk = w4 > 0.3F || w5 > 0.3F || w6 > 0.3F || wAkt > 0.3F || castDuel;
		Klip reaksi = null;
		int reaksiId = -1;
		if (!sibuk && k.reaksiKlip != 0 && age < k.reaksiSampai) {
			reaksi = GerakKatalog.klip(k.reaksiKlip);
			reaksiId = k.reaksiKlip;
		} else if (k.reaksiKlip != 0 && age >= k.reaksiSampai) {
			k.reaksiKlip = 0;
		}
		boolean sikapAktif = !walking && meditasi <= 0.5F && !sibuk;
		if (sikapAktif) {
			if (k.diamSejak < 0.0F) {
				k.diamSejak = age;
			}
		} else {
			k.diamSejak = -1.0F;
		}
		if (reaksi == null && lod == LOD_PENUH && !sibuk) {
			if (k.isengKlip != null && age - k.reaksi.mulai < k.isengKlip.durasi() && k.reaksi.k == k.isengKlip) {
				reaksi = k.isengKlip;
			} else {
				k.isengKlip = null;
				Klip baru = Iseng.pilih(k, age, sikapAktif && k.diamSejak >= 0.0F && age - k.diamSejak > 200.0F, gaya, sosok.anak(),
						sosok.tua(), perempuan);
				if (baru != null) {
					k.isengKlip = baru;
					reaksi = baru;
				}
			}
		}
		if (reaksi != null && reaksi == k.isengKlip) {
			reaksiId = -1;
		}
		float mulaiReaksi = reaksi != null && (reaksi != k.reaksi.k) ? age : k.reaksi.mulai;
		k.reaksi.minta(reaksiId, 0, reaksi, age, mulaiReaksi);
		float w7 = k.reaksi.bobot(age) * bebas * (1.0F - Math.max(w4, Math.max(w5, w6))) * (1.0F - wAkt);

		// layer 8: the idle stance
		Klip sikapDasar = GerakKatalog.klip(HASIL.sikap);
		Klip sikapK = sikapDasar != null ? sikapDasar.pilih(HASIL.varian, ciri, sifatBit) : null;
		boolean sikapBoleh = sikapK != null && meditasi <= 0.5F && (!walking || sikapK.punya(Klip.JALAN_OK)) && !st.isPassenger;
		k.sikap.minta(HASIL.sikap, HASIL.varian, sikapBoleh ? sikapK : null, age, k.sikap.k == sikapK ? k.sikap.mulai : age);
		float w8 = k.sikap.bobot(age) * bebas * (1.0F - wAkt);

		// combine 9 -> 4
		float[] tgt = k.target;
		float[] bob = k.bobot;
		boolean pakaiCache = lod == LOD_KURANG && (k.frameGenap++ & 1) == 1;
		ProfilJalan profil = profil(kl, varian, HASIL.profil, pincang);
		boolean ketuk = false;
		if (pakaiCache) {
			System.arraycopy(k.cacheTarget, 0, tgt, 0, Klip.KANAL);
			System.arraycopy(k.cacheBobot, 0, bob, 0, Klip.KANAL);
			profilJalanSaja(profil, st, k, d, perempuan, age, meditasi);
		} else {
			Arrays.fill(tgt, Float.NaN);
			Arrays.fill(bob, 0.0F);
			if (meditasi <= 0.5F && !st.isPassenger && lod != LOD_SILUET) {
				ketuk = profil.terapkan(st, k, d, tgt, bob, perempuan, age);
			}
			if (w8 > 0.0F) {
				float tS = lod == LOD_SILUET ? 0.0F : age - k.sikap.mulai + k.benih % 97;
				lapis(k, k.sikap, tS, w8, tgt, bob, d, false);
				if (HASIL.leanTambah != 0.0F) {
					tambahTarget(tgt, bob, Klip.LEAN, HASIL.leanTambah, w8);
				}
			}
			if (lod != LOD_SILUET) {
				if (w7 > 0.0F && k.reaksi.k != null) {
					lapis(k, k.reaksi, (age - k.reaksi.mulai) * d.skalaWaktu, w7, tgt, bob, d, true);
				}
				if (w6 > 0.0F && k.loop.k != null) {
					lapis(k, k.loop, waktuServer(k.loop.k, e, age, sejak) * kerja(k.loop.k, d), w6, tgt, bob, d, false);
				}
				if (w5 > 0.0F && k.aktKlip.k != null) {
					lapis(k, k.aktKlip, waktuAktivitas(k, e, age) * kerja(k.aktKlip.k, d), w5, tgt, bob, d, false);
				}
				if (w4 > 0.0F && k.sekali.k != null) {
					lapis(k, k.sekali, sejak * kerja(k.sekali.k, d), w4, tgt, bob, d, false);
				}
				bidik(e, st, k, p, tgt, bob, w4, w6);
			}
			if (lod == LOD_KURANG) {
				System.arraycopy(tgt, 0, k.cacheTarget, 0, Klip.KANAL);
				System.arraycopy(bob, 0, k.cacheBobot, 0, Klip.KANAL);
			}
		}

		// smoothing (NpcPoses.Blend: a = 1 - 0.72^dt)
		float a = 1.0F - (float) Math.pow(0.72D, dt);
		float[] val = k.val;
		float[] w = k.w;
		for (int c = 0; c < Klip.KANAL; c++) {
			if (bob[c] > 0.0F && !Float.isNaN(tgt[c])) {
				val[c] = Float.isNaN(val[c]) || w[c] < 0.02F || k.blendAwal ? tgt[c] : Mth.lerp(a, val[c], tgt[c]);
			}
			w[c] = k.blendAwal ? bob[c] : Mth.lerp(a, w[c], bob[c]);
			if (w[c] < 0.01F) {
				w[c] = 0.0F;
			}
		}
		k.blendAwal = false;

		// the snapshot of the 18 PoseLibrary channels
		float kuat = 0.0F;
		for (int c = 0; c < Klip.KANAL_POSE; c++) {
			boolean ada = w[c] > 0.0F && !Float.isNaN(val[c]);
			k.snapVal[c] = ada ? val[c] : 0.0F;
			k.snapW[c] = ada ? w[c] : 0.0F;
			kuat = Math.max(kuat, k.snapW[c]);
		}
		d.hz = ekstra(val, w, Klip.HZ);
		d.bz = ekstra(val, w, Klip.BZ);
		d.bob = ekstra(val, w, Klip.BOB);
		d.bahu = ekstra(val, w, Klip.BAHU) + HASIL.bahu * w8;
		d.napas = w[Klip.NAPAS] > 0.0F && !Float.isNaN(val[Klip.NAPAS]) ? Mth.lerp(w[Klip.NAPAS], 1.0F, val[Klip.NAPAS]) : 1.0F;
		Klip atas = klipAtas(k, w4, w5, w6, w7, w8);
		putar(k, d, atas, val, w, dt);
		d.lenganDipose = castDuel || k.snapW[Klip.RAX] > 0.5F || k.snapW[Klip.LAX] > 0.5F;

		// seated clips: the meditation seat carries the legs, the clip lays arms and head on top
		float bersila = bersila(k, w4, w6, wAkt, act);
		if (bersila > 0.0F) {
			float lama = data.getDataOrDefault(MeditationPose.STRENGTH, 0.0F);
			float kuatDuduk = Math.max(lama, bersila);
			data.setData(MeditationPose.STRENGTH, kuatDuduk);
			st.walkAnimationSpeed = Mth.lerp(kuatDuduk, st.walkAnimationSpeed, 0.0F);
			st.seated = Math.max(st.seated, kuatDuduk);
			meditasi = kuatDuduk;
		}
		if (meditasi > 0.5F) {
			for (int c = 0; c < Klip.KANAL_POSE; c++) {
				boolean atasBadan = c == Klip.HX || c == Klip.HY || c == Klip.LEAN || c >= Klip.RAX && c <= Klip.LAZ;
				d.duduk[c] = atasBadan ? k.snapVal[c] : 0.0F;
				d.dudukW[c] = atasBadan ? k.snapW[c] : 0.0F;
			}
		}

		// lying down
		float tidur = 0.0F;
		if (k.sekali.k != null && k.sekali.k.punya(Klip.BERBARING)) {
			tidur = Math.max(tidur, w4);
		}
		if (k.loop.k != null && k.loop.k.punya(Klip.BERBARING)) {
			tidur = Math.max(tidur, w6);
		}
		if (k.aktKlip.k != null && k.aktKlip.k.punya(Klip.BERBARING)) {
			tidur = Math.max(tidur, w5);
		}
		d.tidurBobot = tidur;
		d.tidur = tidur > 0.5F;
		if (atas != null) {
			d.klip = atas == k.sekali.k ? k.sekali.klip : atas == k.aktKlip.k ? k.aktKlip.klip : atas == k.loop.k ? k.loop.klip
					: atas == k.sikap.k ? k.sikap.klip : 0;
			d.varian = atas == k.sekali.k ? k.sekali.varian : atas == k.aktKlip.k ? k.aktKlip.varian : atas == k.loop.k ? k.loop.varian : 0;
		}
		if ("abadi".equals(kunci) || "d.dewa_penglai".equals(kunci)) {
			d.melayang = 0.02F;
		}

		// breath, gaze (additive)
		boolean tanpaNapas = atas != null && atas.punya(Klip.TANPA_NAPAS);
		if (!castDuel && !tanpaNapas) {
			napas(k, d, st, sosok, gaya, atas, age, meditasi > 0.5F);
		}
		if (atas == k.loop.k && k.loop.klip == GerakId.TIANG_BERDIRI && age - k.loop.berubah > 600.0F) {
			float gem = 0.4F * D * Mth.sin(age * 2.9F + k.benih);
			d.tambah[Klip.RAX] += gem;
			d.tambah[Klip.LAX] -= gem;
		}
		if (lod == LOD_PENUH) {
			UUID guru = murid && st.role == DiscipleEntity.Role.DISCIPLE.ordinal() ? ((DiscipleEntity) e).ownerId() : null;
			int lookAt = p != null ? p.tatapData() : -1;
			boolean tatapBoleh = !castDuel && d.tidurBobot < 0.5F && meditasi < 0.5F;
			Tatap.hitung(e, st, k, d, age, dt, lookAt, guru, sifatBit, gaya.jalan() == SosokJalan.IBLIS, tatapBoleh);
			d.tambah[Klip.HY] += k.tatapYaw * D;
			d.tambah[Klip.HX] += k.tatapPitch * D;
			d.tambah[Klip.TWIST] += k.tatapTwist * D;
		}

		// events (sounds, particles, face, props, hooks) of the sampled clips
		if (lod == LOD_PENUH && !pakaiCache) {
			if (w4 > 0.5F && k.sekali.k != null) {
				peristiwa(e, st, k, k.sekali, sejak * kerja(k.sekali.k, d), jarakSq);
			} else if (w5 > 0.5F && k.aktKlip.k != null) {
				peristiwa(e, st, k, k.aktKlip, waktuAktivitas(k, e, age) * kerja(k.aktKlip.k, d), jarakSq);
			} else if (w6 > 0.5F && k.loop.k != null) {
				peristiwa(e, st, k, k.loop, waktuServer(k.loop.k, e, age, sejak) * kerja(k.loop.k, d), jarakSq);
			} else if (w7 > 0.5F && k.reaksi.k != null) {
				peristiwa(e, st, k, k.reaksi, (age - k.reaksi.mulai) * d.skalaWaktu, jarakSq);
			}
			if (ketuk && jarakSq < 256.0F) {
				GerakKlien.suara(e, "block.bamboo.step", 0.15F, 1.4F);
			}
		}

		// face (40K WajahModel)
		if (lod == LOD_PENUH) {
			wajah(e, st, k, d, atas, age, castDuel || bertarung, meditasi > 0.5F);
		}

		// springs (40K SosokBusanaModel)
		if (lod <= LOD_KURANG) {
			boolean pegas = lod == LOD_PENUH && jarakSq <= batasPegasSq;
			pegas(e, st, k, d, dt, pegas, meditasi > 0.5F, atas, profil == ProfilJalan.ARWAH || d.melayang > 0.0F);
		}

		// props (SosokPropLayer)
		props(st, k, d, p, atas, w8, castDuel || bertarung, lod, meditasi > 0.5F);

		data.setData(GerakData.KUNCI, d);
		if (kuat <= 0.0F || castDuel || PoseRenderData.pose(st) != null) {
			return null;
		}
		CastPoses.Snapshot snap = atas != null && atas.punya(Klip.DUA_TANGAN) ? k.snapDua : k.snap;
		data.setData(PoseRenderData.POSE, snap);
		return snap;
	}

	// ================================================================== helpers: identity

	/** The look key of a disciple or town NPC (cached; no strings built per frame). */
	private static @Nullable String kunci(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, @Nullable SosokApi.Pemegang p) {
		if (e instanceof DiscipleEntity) {
			int code = st.role * 16 + st.sectRank;
			if (code != k.pangkatCache || k.kunci == null) {
				k.pangkatCache = code;
				if (st.role == DiscipleEntity.Role.CANDIDATE.ordinal()) {
					k.kunci = "calon";
				} else if (st.role == DiscipleEntity.Role.RIVAL.ordinal()) {
					k.kunci = "saingan_murid";
				} else {
					k.kunci = "murid." + Mth.clamp(st.sectRank, 0, 5);
				}
			}
			return k.kunci;
		}
		String mentah = p != null ? p.rupaData() : "";
		if (mentah != null && !mentah.isEmpty()) {
			if (!mentah.equals(k.rupaMentah)) {
				k.rupaMentah = mentah;
				k.kunci = SosokApi.rupa(e).kunci();
				k.peranOrdinal = -1;
			}
			return k.kunci;
		}
		if (e instanceof KultivatorNpc npc) {
			int ord = npc.role().ordinal();
			if (ord != k.peranOrdinal || k.rupaMentah != null) {
				k.peranOrdinal = ord;
				k.rupaMentah = null;
				k.kunci = npc.role().id();
			}
			return k.kunci;
		}
		return null;
	}

	private static int sifatBit(Gaya gaya) {
		int bit = 0;
		Sifat a = gaya.sifatPertama();
		Sifat b = gaya.sifatKedua();
		if (a != null) {
			bit |= 1 << a.ordinal();
		}
		if (b != null) {
			bit |= 1 << b.ordinal();
		}
		return bit;
	}

	/** Trait condition bits of the variants (40.5.6): sex, age, martial look, elder rank. */
	private static int ciri(LivingEntity e, DiscipleRenderState st, Sosok sosok, Gaya gaya, @Nullable String kunci) {
		int c = sosok.perempuan() ? Klip.SY_P : Klip.SY_L;
		if (sosok.anak()) {
			c |= Klip.SY_ANAK;
		}
		if (sosok.tua()) {
			c |= Klip.SY_TUA;
		}
		boolean beladiri = beladiri(e, st, gaya, kunci);
		c |= beladiri ? Klip.SY_BELA_DIRI : Klip.SY_SIPIL;
		boolean tetua = e instanceof DiscipleEntity ? st.sectRank >= 4
				: kunci != null && (kunci.startsWith("tetua") || kunci.equals("ketua_sekte") || kunci.equals("patriark_klan"));
		if (tetua) {
			c |= Klip.SY_TETUA;
		}
		return c;
	}

	/** Martial bodies bow with the cupped fist (baoquan); civilian women curtsy (wanfu). */
	private static boolean beladiri(LivingEntity e, DiscipleRenderState st, Gaya gaya, @Nullable String kunci) {
		int jalan = gaya.jalan();
		if (jalan == SosokJalan.PEDANG || jalan == SosokJalan.PENJAGA || jalan == SosokJalan.PEMBURU
				|| gaya.busanaKhusus() == Gaya.BUSANA_ZIRAH) {
			return true;
		}
		if (e instanceof DiscipleEntity) {
			return st.role == DiscipleEntity.Role.RIVAL.ordinal() || st.dao == dev.kultivator.data.Dao.SWORD.ordinal();
		}
		if (e instanceof KultivatorNpc npc) {
			dev.kultivator.dunia.kota.NpcRole.Kelompok kel = npc.role().kelompok;
			if (kel == dev.kultivator.dunia.kota.NpcRole.Kelompok.PENJAGA || kel == dev.kultivator.dunia.kota.NpcRole.Kelompok.KULTIVATOR) {
				return true;
			}
		}
		return kunci != null && (kunci.startsWith("liar") || kunci.contains("penjaga") || kunci.contains("pengawal")
				|| kunci.contains("gladiator") || kunci.equals("pengembara") || kunci.equals("saingan") || kunci.equals("juara_sekte"));
	}

	// ================================================================== helpers: layers

	/** Samples a layer (with the cross-fade from the clip it replaced) and lays it over the lower layers. */
	private static void lapis(GerakKeadaan k, GerakKeadaan.Jalur jalur, float t, float bobot, float[] tgt, float[] bob, GerakData d,
			boolean reaksi) {
		Klip klip = jalur.k;
		if (klip == null) {
			return;
		}
		float amp = klip.punya(Klip.KERJA) ? d.skalaAmp : 1.0F;
		float[] s = k.sampel;
		Sampler.sampel(klip, t, s, amp, k.benih);
		float silang = jalur.silang(k.ageLalu);
		if (silang < 1.0F && jalur.kLama != null) {
			float[] lama = k.sampelLama;
			Sampler.sampel(jalur.kLama, k.ageLalu - jalur.mulaiLama, lama, 1.0F, k.benih);
			for (int c = 0; c < Klip.KANAL; c++) {
				if (Float.isNaN(s[c])) {
					s[c] = lama[c];
				} else if (!Float.isNaN(lama[c])) {
					s[c] = Mth.lerp(silang, lama[c], s[c]);
				}
			}
		}
		for (int c = 0; c < Klip.KANAL; c++) {
			float v = s[c];
			if (Float.isNaN(v)) {
				continue;
			}
			tgt[c] = Float.isNaN(tgt[c]) ? v : Mth.lerp(bobot, tgt[c], v);
			bob[c] = Math.max(bob[c], bobot);
		}
	}

	private static void tambahTarget(float[] tgt, float[] bob, int c, float v, float w) {
		tgt[c] = Float.isNaN(tgt[c]) ? v : tgt[c] + v * w;
		bob[c] = Math.max(bob[c], w);
	}

	/** Clip time of a server clip: in step with the server for SINKRON clips, else per-body phase-shifted (id x 7). */
	private static float waktuServer(Klip klip, LivingEntity e, float age, float sejak) {
		if (klip.punya(Klip.SINKRON)) {
			return sejak;
		}
		return age + Math.floorMod(e.getId() * 7, Math.max(1, klip.durasi()));
	}

	/** Clip time of the activity clip: from its start for once clips, per-body phase-shifted for loops. */
	private static float waktuAktivitas(GerakKeadaan k, LivingEntity e, float age) {
		Klip klip = k.aktKlip.k;
		if (klip == null || !klip.loop()) {
			return age - k.aktKlip.mulai;
		}
		return waktuServer(klip, e, age, age - k.aktKlip.mulai);
	}

	/** Children work quicker, elders slower (40.5.6), only on work clips. */
	private static float kerja(Klip klip, GerakData d) {
		return klip.punya(Klip.KERJA) ? d.skalaWaktu : 1.0F;
	}

	/** The walk profile, with the clip's own walk (hurry, old walk, child, guard, carrying on the back, limp). */
	private static ProfilJalan profil(@Nullable Klip kl, int varian, ProfilJalan bawaan, boolean pincang) {
		if (pincang) {
			return ProfilJalan.PINCANG;
		}
		if (kl == null) {
			return bawaan;
		}
		return switch (kl.id()) {
			case GerakId.JALAN_TERGESA -> ProfilJalan.TERGESA;
			case GerakId.LARI_TAKUT -> ProfilJalan.TAKUT;
			case GerakId.JALAN_TUA -> ProfilJalan.TUA_TONGKAT;
			case GerakId.JALAN_ANAK, GerakId.MAIN_KEJAR -> ProfilJalan.ANAK_LOMPAT;
			case GerakId.BARIS_JAGA -> ProfilJalan.MILITER;
			case GerakId.JALAN_PIKUL, GerakId.DAKI_PIKUL, GerakId.PIKUL_TANDU -> ProfilJalan.KULI;
			case GerakId.JALAN_BAWA -> varian == GerakId.V_JALAN_BAWA_PUNGGUNG ? ProfilJalan.KULI : bawaan;
			case GerakId.PINCANG -> ProfilJalan.PINCANG;
			case GerakId.TARI_PUTAR -> ProfilJalan.PENARI;
			default -> bawaan;
		};
	}

	/** On cached (KURANG) frames only the walk's state edit is redone (targets come from the cache). */
	private static void profilJalanSaja(ProfilJalan profil, DiscipleRenderState st, GerakKeadaan k, GerakData d, boolean perempuan,
			float age, float meditasi) {
		if (meditasi > 0.5F || st.isPassenger) {
			return;
		}
		float[] buang = k.sampelLama;
		float[] buangW = k.sampel;
		Arrays.fill(buang, Float.NaN);
		Arrays.fill(buangW, 0.0F);
		profil.terapkan(st, k, d, buang, buangW, perempuan, age);
	}

	/** The arm aims at the look target for pointing clips (ARAH_SASARAN). */
	private static void bidik(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, @Nullable SosokApi.Pemegang p, float[] tgt, float[] bob,
			float w4, float w6) {
		Klip klip = w4 > 0.0F && k.sekali.k != null ? k.sekali.k : w6 > 0.0F ? k.loop.k : null;
		if (klip == null || !klip.punya(Klip.ARAH_SASARAN) || p == null) {
			return;
		}
		Entity sasaran = p.tatapData() >= 0 ? e.level().getEntity(p.tatapData()) : null;
		if (sasaran == null) {
			return;
		}
		double dx = sasaran.getX() - e.getX();
		double dz = sasaran.getZ() - e.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float rel = Mth.clamp(Mth.wrapDegrees(yaw - st.bodyRot), -60.0F, 60.0F);
		k.bidikYaw = Mth.lerp(0.3F, k.bidikYaw, rel * D);
		float w = Math.max(w4, w6);
		tgt[Klip.RAY] = (Float.isNaN(tgt[Klip.RAY]) ? 0.0F : tgt[Klip.RAY]) + k.bidikYaw * w;
		bob[Klip.RAY] = Math.max(bob[Klip.RAY], w);
	}

	/** The clip that drives the body most this frame. */
	private static @Nullable Klip klipAtas(GerakKeadaan k, float w4, float w5, float w6, float w7, float w8) {
		if (w4 > 0.5F && k.sekali.k != null) {
			return k.sekali.k;
		}
		if (w5 > 0.5F && k.aktKlip.k != null) {
			return k.aktKlip.k;
		}
		if (w6 > 0.5F && k.loop.k != null) {
			return k.loop.k;
		}
		if (w7 > 0.5F && k.reaksi.k != null) {
			return k.reaksi.k;
		}
		if (w8 > 0.3F && k.sikap.k != null) {
			return k.sikap.k;
		}
		return null;
	}

	/** The dance's spin: accumulated while a PUTAR clip plays, eased back to a whole turn after. */
	private static void putar(GerakKeadaan k, GerakData d, @Nullable Klip atas, float[] val, float[] w, float dt) {
		if (atas != null && atas.punya(Klip.PUTAR) && w[Klip.SPIN] > 0.0F && !Float.isNaN(val[Klip.SPIN])) {
			k.spin = (k.spin + val[Klip.SPIN] * w[Klip.SPIN] * dt) % 360.0F;
		} else if (k.spin != 0.0F) {
			float sisa = Mth.wrapDegrees(k.spin);
			sisa *= (float) Math.pow(0.8D, dt);
			k.spin = Math.abs(sisa) < 0.5F ? 0.0F : sisa;
		}
		d.spin = k.spin;
	}

	private static float ekstra(float[] val, float[] w, int c) {
		return w[c] > 0.0F && !Float.isNaN(val[c]) ? val[c] * w[c] : 0.0F;
	}

	/** Weight of a seated cross-legged clip (BERSILA) among the server clip and the activity's clip. */
	private static float bersila(GerakKeadaan k, float w4, float w6, float wAkt, int act) {
		float b = 0.0F;
		if (k.sekali.k != null && k.sekali.k.punya(Klip.BERSILA)) {
			b = Math.max(b, w4);
		}
		if (k.loop.k != null && k.loop.k.punya(Klip.BERSILA)) {
			b = Math.max(b, w6);
		}
		if (wAkt > 0.0F && act >= 28 && k.aktKlip.k != null && k.aktKlip.k.punya(Klip.BERSILA)) {
			b = Math.max(b, wAkt);
		}
		return b;
	}

	// ================================================================== helpers: breath, face, springs, props, events

	/** The breath (40.5.9): bob, chest and shoulders; deeper after running, slower for elders and in meditation. */
	private static void napas(GerakKeadaan k, GerakData d, DiscipleRenderState st, Sosok sosok, Gaya gaya, @Nullable Klip atas, float age,
			boolean duduk) {
		boolean lari = st.walkAnimationSpeed > 0.9F;
		if (lari) {
			if (k.lariSejak < 0.0F) {
				k.lariSejak = age;
			}
		} else {
			if (k.lariSejak >= 0.0F && age - k.lariSejak > 60.0F) {
				k.terengahSejak = age;
			}
			k.lariSejak = -1.0F;
		}
		float periode = 80.0F;
		if (sosok.anak()) {
			periode = 60.0F;
		} else if (sosok.tua()) {
			periode = 100.0F;
		}
		if (duduk || atas != null && atas.punya(Klip.MATA_TUTUP) || gaya.punya(Sifat.TENANG)) {
			periode = 120.0F;
		}
		float terengah = 1.0F + 1.5F * Mth.clamp(1.0F - (age - k.terengahSejak) / 100.0F, 0.0F, 1.0F);
		float amp = d.napas * terengah;
		float s = Mth.sin(age * Mth.TWO_PI / periode + (k.benih & 63));
		d.bob += 0.2F * s * amp;
		d.tambah[Klip.HX] += 1.0F * D * s * amp;
		d.tambah[Klip.RAZ] += 1.2F * D * s * amp;
		d.tambah[Klip.LAZ] -= 1.2F * D * s * amp;
	}

	private static void wajah(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, GerakData d, @Nullable Klip atas, float age,
			boolean bertarung, boolean duduk) {
		int km = -1;
		int kmu = -1;
		boolean tutup = d.tidur;
		boolean bicara = false;
		if (atas != null) {
			km = atas.wajahMata();
			kmu = atas.wajahMulut();
			tutup |= atas.punya(Klip.MATA_TUTUP);
			if (atas.punya(Klip.MATA_SETENGAH_WAKTU) && (age % 160.0F) < 80.0F) {
				tutup = true;
			}
			bicara = atas.punya(Klip.BICARA);
		}
		if (k.wajahMataPeristiwa >= 0) {
			km = k.wajahMataPeristiwa;
		}
		if (k.wajahMulutPeristiwa >= 0) {
			kmu = k.wajahMulutPeristiwa;
		}
		if (duduk && km < 0) {
			tutup = true;
		}
		if (atas != null && atas.punya(Klip.LIRIK_BAWAH)) {
			d.lirikY = -1.0F;
		} else if (atas != null && atas.punya(Klip.LIRIK_ATAS)) {
			d.lirikY = 1.0F;
		}
		String garis = "";
		if (e instanceof KultivatorNpc npc) {
			bicara |= npc.hasFlag(KultivatorNpc.FLAG_TALK);
			Component line = npc.line();
			if (line != k.garisKomponen) {
				k.garisKomponen = line;
				k.garis = line.getString();
			}
			garis = k.garis;
			bicara |= !garis.isEmpty();
		}
		if (k.terengahSejak > age - 100.0F && kmu < 0 && !bicara) {
			kmu = Wajah.MULUT_A;
		}
		boolean terluka = e.getHealth() > 0.0F && e.getHealth() < e.getMaxHealth() * 0.35F;
		Wajah.hitung(k, d, age, km, kmu, tutup, bicara, garis, bertarung, st.realm, terluka, SosokDetail.wajah());
	}

	private static void pegas(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, GerakData d, float dt, boolean aktif, boolean duduk,
			@Nullable Klip atas, boolean melayang) {
		Pegas.Gerak g = k.gerak;
		double x = e.getX();
		double y = e.getY();
		double z = e.getZ();
		float bodyRot = st.bodyRot;
		if (k.posAwal || dt <= 0.0F) {
			k.xLalu = x;
			k.yLalu = y;
			k.zLalu = z;
			k.bodyRotLalu = bodyRot;
			k.kepalaYawLalu = st.yRot;
			k.kepalaPitchLalu = st.xRot;
			k.posAwal = false;
		}
		float inv = dt > 0.0F ? 1.0F / dt : 0.0F;
		float vx = (float) (x - k.xLalu) * inv;
		float vy = (float) (y - k.yLalu) * inv;
		float vz = (float) (z - k.zLalu) * inv;
		float yaw = bodyRot * D;
		float fx = -Mth.sin(yaw);
		float fz = Mth.cos(yaw);
		g.vF = Mth.clamp(vx * fx + vz * fz, -0.6F, 0.6F);
		g.vS = Mth.clamp(vx * -Mth.cos(yaw) + vz * -Mth.sin(yaw), -0.6F, 0.6F);
		g.vY = Mth.clamp(vy, -0.8F, 0.8F);
		g.omegaBadan = Mth.clamp(Mth.wrapDegrees(bodyRot - k.bodyRotLalu) * inv, -30.0F, 30.0F);
		g.omegaKepalaYaw = Mth.wrapDegrees(st.yRot - k.kepalaYawLalu) * D * inv;
		g.omegaKepalaPitch = (st.xRot - k.kepalaPitchLalu) * D * inv;
		float[] val = k.val;
		float[] w = k.w;
		g.lean = ekstra(val, w, Klip.LEAN);
		g.kepalaX = st.xRot * D + ekstra(val, w, Klip.HX);
		float phi = st.walkAnimationPos * 0.6662F;
		float speed = st.walkAnimationSpeed / Math.max(0.0001F, st.speedValue);
		float kananX = w[Klip.RAX] > 0.5F ? val[Klip.RAX] : Mth.cos(phi + Mth.PI) * speed * d.ayunLengan;
		float kiriX = w[Klip.LAX] > 0.5F ? val[Klip.LAX] : Mth.cos(phi) * speed * d.ayunLengan;
		g.omegaLenganKanan = (kananX - k.lenganKananLalu) * inv;
		g.omegaLenganKiri = (kiriX - k.lenganKiriLalu) * inv;
		g.lenganKananX = kananX;
		g.lenganKiriX = kiriX;
		g.lenganKananZ = w[Klip.RAZ] > 0.5F ? val[Klip.RAZ] : 0.0F;
		g.lenganKiriZ = w[Klip.LAZ] > 0.5F ? val[Klip.LAZ] : 0.0F;
		g.bz = d.bz;
		g.fasaLangkah = phi;
		g.kakiKananX = w[Klip.RLX] > 0.5F ? val[Klip.RLX] : Mth.cos(phi) * 1.4F * speed;
		g.kakiKiriX = w[Klip.LLX] > 0.5F ? val[Klip.LLX] : Mth.cos(phi + Mth.PI) * 1.4F * speed;
		g.kakiKananZ = w[Klip.RLZ] > 0.5F ? val[Klip.RLZ] : 0.0F;
		g.kakiKiriZ = w[Klip.LLZ] > 0.5F ? val[Klip.LLZ] : 0.0F;
		g.bersila = duduk;
		g.dudukKursi = !duduk && atas != null && atas.punya(Klip.DUDUK);
		g.lari = st.walkAnimationSpeed > 0.9F;
		g.melayang = melayang;
		g.age = k.ageLalu;
		Pegas.langkah(k, d, g, dt, aktif);
		k.xLalu = x;
		k.yLalu = y;
		k.zLalu = z;
		k.bodyRotLalu = bodyRot;
		k.kepalaYawLalu = st.yRot;
		k.kepalaPitchLalu = st.xRot;
		k.lenganKananLalu = kananX;
		k.lenganKiriLalu = kiriX;
	}

	/** Props: the server's DATA_PROP over the clip's defaults, the stance's idle prop, the life path's worn props. */
	private static void props(DiscipleRenderState st, GerakKeadaan k, GerakData d, @Nullable SosokApi.Pemegang p, @Nullable Klip atas, float w8,
			boolean sembunyi, int lod, boolean duduk) {
		d.propSembunyi = sembunyi || lod >= LOD_SILUET;
		if (d.aktivitas) {
			// DutyPoses' activity: the legacy layer draws the duty prop (DiscipleHeldItemLayer)
			d.propLama = true;
		}
		Klip server = k.sekali.k != null && k.sekali.on ? k.sekali.k : k.loop.k != null && k.loop.on ? k.loop.k : null;
		if (atas != null && atas == k.aktKlip.k && k.aktKlip.on) {
			// a Gerak activity clip (DESIGN 39): its own props, or the legacy layer's for PROP_LAMA clips
			d.propKanan = atas.propKanan();
			d.propKiri = atas.propKiri();
			d.propPunggung = atas.propPunggung();
			d.pasang = atas.pasang().length > 0 ? atas.pasang() : null;
			d.propLama |= atas.punya(Klip.PROP_LAMA);
			terapkanPropPeristiwa(k, d);
		} else if (server != null && (atas == server)) {
			int data = p != null ? p.propData() : 0;
			int kanan = PropId.kanan(data);
			int kiri = PropId.kiri(data);
			int punggung = PropId.punggung(data);
			d.propKanan = kanan != 0 ? kanan : server.propKanan();
			d.propKiri = kiri != 0 ? kiri : server.propKiri();
			d.propPunggung = punggung != 0 ? punggung : server.propPunggung();
			d.pasang = server.pasang().length > 0 ? server.pasang() : null;
			d.propLama |= server.punya(Klip.PROP_LAMA);
			terapkanPropPeristiwa(k, d);
		} else if (atas != null && atas == k.reaksi.k) {
			d.propKanan = atas.propKanan();
			d.propKiri = atas.propKiri();
			d.pasang = atas.pasang().length > 0 ? atas.pasang() : null;
		} else if (w8 > 0.5F && !d.aktivitas) {
			d.propKanan = HASIL.prop;
			if (atas != null) {
				if (atas.propKanan() != 0) {
					d.propKanan = atas.propKanan();
				}
				d.propKiri = atas.propKiri();
			}
		}
		if (lod == LOD_KURANG && d.pasang != null) {
			// only big props far away
			d.pasang = null;
		}
		if (duduk && server == null && atas != k.aktKlip.k) {
			d.propKanan = 0;
			d.propKiri = 0;
		}
		d.pasangTetap = lod == LOD_PENUH ? HASIL.pasang : null;
	}

	/** Prop changes from the playing clip's PROP events override its defaults. */
	private static void terapkanPropPeristiwa(GerakKeadaan k, GerakData d) {
		int[] ev = k.propPeristiwa;
		if (ev[Klip.PROP_SLOT_KANAN] >= 0) {
			d.propKanan = ev[Klip.PROP_SLOT_KANAN];
		}
		if (ev[Klip.PROP_SLOT_KIRI] >= 0) {
			d.propKiri = ev[Klip.PROP_SLOT_KIRI];
		}
		if (ev[Klip.PROP_SLOT_PUNGGUNG] >= 0) {
			d.propPunggung = ev[Klip.PROP_SLOT_PUNGGUNG];
		}
		if (ev[Klip.PROP_SLOT_PASANG] == 0) {
			d.pasang = null;
		}
	}

	/** Fires the events between the last processed clip tick and now (once per tick, none skipped within a cycle). */
	private static void peristiwa(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, GerakKeadaan.Jalur jalur, float t, float jarakSq) {
		Klip klip = jalur.k;
		if (klip == null || klip.peristiwa().length == 0) {
			if (klip != null && jalur.tickPeristiwa < 0) {
				jalur.tickPeristiwa = 0;
				k.wajahMataPeristiwa = -1;
				k.wajahMulutPeristiwa = -1;
				Arrays.fill(k.propPeristiwa, -1);
			}
			return;
		}
		int dur = Math.max(1, klip.durasi());
		int tick = (int) Math.floor(t);
		int siklus = klip.loop() ? Math.floorDiv(tick, dur) : 0;
		int dalam = klip.loop() ? Math.floorMod(tick, dur) : Math.min(tick, dur);
		if (siklus == jalur.siklusPeristiwa && dalam == jalur.tickPeristiwa) {
			return;
		}
		boolean awal = jalur.tickPeristiwa < 0;
		int dari = awal ? dalam - 1 : jalur.tickPeristiwa;
		boolean bungkus = !awal && siklus != jalur.siklusPeristiwa;
		if (awal || bungkus && klip.loop()) {
			k.wajahMataPeristiwa = -1;
			k.wajahMulutPeristiwa = -1;
			Arrays.fill(k.propPeristiwa, -1);
		}
		for (Klip.Peristiwa ev : klip.peristiwa()) {
			int et = ev.t();
			boolean kena = bungkus ? et > dari || et <= dalam : et > dari && et <= dalam;
			if (awal && et == 0 && dalam == 0) {
				kena = true;
			}
			if (kena) {
				picu(e, st, k, ev, t, jarakSq);
			}
		}
		jalur.siklusPeristiwa = siklus;
		jalur.tickPeristiwa = dalam;
	}

	private static void picu(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, Klip.Peristiwa ev, float t, float jarakSq) {
		switch (ev.jenis()) {
			case SUARA -> {
				if (jarakSq < 256.0F) {
					float pitch = ev.b() < 0.0F ? nadaAcak(k, t) : ev.b();
					GerakKlien.suara(e, ev.id(), ev.a(), pitch);
				}
			}
			case PARTIKEL -> GerakKlien.partikel(e, st, ev.id(), (int) ev.a(), (int) ev.b());
			case WAJAH -> {
				if (ev.a() >= 0.0F) {
					k.wajahMataPeristiwa = (int) ev.a();
				}
				if (ev.b() >= 0.0F) {
					k.wajahMulutPeristiwa = (int) ev.b();
				}
			}
			case PROP -> {
				int slot = (int) ev.a();
				if (slot >= 0 && slot < k.propPeristiwa.length) {
					k.propPeristiwa[slot] = (int) ev.b();
				}
			}
			case KAIT -> {
				KaitGerak kait = GerakKatalog.kait(ev.id());
				if (kait != null) {
					kait.picu(e, st, t);
				}
			}
		}
	}

	/** The guqin's notes: a pentatonic pitch from the body's seed and the time. */
	private static float nadaAcak(GerakKeadaan k, float t) {
		int i = Math.floorMod((int) (t / 6.0F) * 31 + k.benih, NADA.length);
		return NADA[i];
	}

	/** A pentatonic scale as note-block pitches. */
	private static final float[] NADA = {0.749F, 0.841F, 0.944F, 1.122F, 1.26F, 1.498F};

	// ================================================================== LOD, budgets, cleanup

	private static int lod(DiscipleRenderState st) {
		return switch (SosokDetail.lod(st)) {
			case PENUH -> LOD_PENUH;
			case KURANG -> LOD_KURANG;
			case SILUET -> LOD_SILUET;
			default -> LOD_JAUH;
		};
	}

	/** On the first body of a new frame: last frame's distances decide the full-detail and spring cut-offs. */
	private static void frame(long gameTime, float pt) {
		long kunci = gameTime * 4096L + (long) (pt * 4095.0F);
		if (kunci == frameKunci) {
			return;
		}
		frameKunci = kunci;
		int penuh;
		int pegas;
		switch (SosokDetail.tingkat().name()) {
			case "TINGGI" -> {
				penuh = 24;
				pegas = 12;
			}
			case "RENDAH" -> {
				penuh = 10;
				pegas = 4;
			}
			default -> {
				penuh = 16;
				pegas = 8;
			}
		}
		int n = jarakN;
		if (n > 1) {
			Arrays.sort(JARAK, 0, n);
		}
		batasPenuhSq = n > penuh ? JARAK[penuh - 1] : Float.MAX_VALUE;
		batasPegasSq = n > pegas ? JARAK[pegas - 1] : Float.MAX_VALUE;
		jarakN = 0;
	}

	private static void catatJarak(float jarakSq) {
		if (jarakN < JARAK.length) {
			JARAK[jarakN++] = jarakSq;
		}
	}

	/** Forgets bodies not evaluated for 200 ticks (GerakKlien calls it every 200 ticks). */
	static void bersihkan(ClientLevel level) {
		long now = level.getGameTime();
		KEADAAN.values().removeIf(k -> now - k.dilihat > 200L || level.getEntity(k.id) == null);
	}

	/** Forgets everything (world change). */
	public static void kosongkan() {
		KEADAAN.clear();
		jarakN = 0;
		frameKunci = Long.MIN_VALUE;
	}

	/** Number of bodies with state (for {@code /sosok info}). */
	public static int jumlahKeadaan() {
		return KEADAAN.size();
	}

	private GerakPengendali() {
	}
}
