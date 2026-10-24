package dev.kultivator.sosok;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The test bodies of {@code /kultivasi uji penampilan} (DESIGN 40.6.4): galleries of looks laid out in front of the
 * player, clip parades, the gait walk, the face show and the spring run. Every body is a {@link KultivatorNpc} (type
 * {@code noSave}) or, where a disciple field is needed (rare physique, function and DESIGN 39 activities), an unowned
 * {@link DiscipleEntity} of role RIVAL (never saved); all carry {@link #TAG}, stand still ({@code setNoAi}) unless the
 * test needs walking, and vanish by themselves after {@link #UMUR} ticks (600 s) or with {@code hapus}.
 * <p>
 * Budget (weak laptops): at most {@link #MAKS} test bodies at once; the driver runs on the server tick over that short
 * list only, the walking and running tests steer every 10 ticks, the parades step per clip.
 */
public final class GaleriUji {

	/** Tag of every Sosok test body. */
	public static final String TAG = "kultivator.sosok_uji";
	/** Test bodies alive at once at most. */
	public static final int MAKS = 64;
	/** A test body's life (ticks, 600 s). */
	public static final int UMUR = 600 * 20;
	/** Grid spacing of the galleries (blocks). */
	private static final double JARAK = 1.6D;
	/** Pause between two clips of a parade (ticks). */
	private static final int JEDA_KLIP = 20;
	/** Length given to a looping clip in a parade (ticks). */
	private static final int LAMA_LOOP = 80;

	/** What a test body does on the server tick. */
	enum Mode {
		DIAM, PARADE, JALAN, PEGAS, WAJAH
	}

	/** One tracked test body and its driver state. */
	private static final class Uji {
		final LivingEntity body;
		final ServerPlayer pemain;
		final long habis;
		Mode mode = Mode.DIAM;
		/** PARADE: the clips in turn; index of the next one, the game time it is due. */
		int[] daftar = new int[0];
		int indeks;
		int langkah = 1;
		long berikut;
		/** JALAN: the two marks and which one it walks to; PEGAS: angle offset; WAJAH: step. */
		@Nullable Vec3 a;
		@Nullable Vec3 b;
		boolean keB;
		double sudut;
		double laju = 0.8D;
		int tahap;

		Uji(LivingEntity body, ServerPlayer pemain, long habis) {
			this.body = body;
			this.pemain = pemain;
			this.habis = habis;
		}
	}

	private static final List<Uji> UJI = new ArrayList<>();

	/** Called once from {@link Sosok#init()}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GaleriUji::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> UJI.clear());
	}

	// ================================================================== bodies

	/** Room for {@code n} more test bodies. */
	static boolean muat(int n) {
		bersihkan();
		return UJI.size() + n <= MAKS;
	}

	/** How many test bodies live now. */
	static int jumlah() {
		bersihkan();
		return UJI.size();
	}

	/**
	 * A test NPC at {@code at} facing the player: look key {@code rupa} (a role id gives that role, anything else a
	 * townsman wearing the key), sex and age forced when given (-1 = the key's policy), plate {@code papan}. Null when
	 * the cap is full or it could not be added.
	 */
	static @Nullable KultivatorNpc npc(ServerPlayer pemain, Vec3 at, String rupa, int kelamin, int usia, int look,
			@Nullable Dao dao, int realm, Component papan, boolean ai) {
		if (!muat(1)) {
			return null;
		}
		ServerLevel level = pemain.level();
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, at);
		if (spot == null) {
			spot = at;
		}
		float yaw = hadap(spot, pemain.position());
		npc.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		NpcRole role = NpcRole.byId(rupa);
		boolean milikPeran = role != null;
		if (role == null) {
			role = NpcRole.WARGA;
			SosokApi.setRupa(npc, rupa);
		}
		int r = realm >= 0 ? realm : realmUntuk(role, milikPeran);
		npc.setup(role, look, r, Math.floorMod(look >>> 5, 4), dao, Math.floorMod(look >>> 3, 6), 0, papan);
		Sosok sosok = SosokApi.sosok(npc);
		if (kelamin >= 0 && sosok.kelamin() != kelamin || usia >= 0 && sosok.usia() != usia) {
			SosokApi.setSosok(npc, sosok.withKelamin(kelamin >= 0 ? kelamin : sosok.kelamin()).withUsia(usia >= 0 ? usia : sosok.usia()));
		}
		npc.setFlag(KultivatorNpc.FLAG_GHOST, rupa.equals("sisa_jiwa") || rupa.equals("d.arwah") || rupa.equals(SosokGen.MURID_BAYANGAN));
		npc.addTag(TAG);
		npc.memory().putBoolean("busy", true);
		npc.setLonely(96.0D, UMUR);
		npc.setNoAi(!ai);
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		UJI.add(new Uji(npc, pemain, level.getGameTime() + UMUR));
		return npc;
	}

	/**
	 * A test disciple body (role RIVAL, never saved) for the fields only a disciple has: physique, function activities
	 * 13-27 and DESIGN 39 activities 40-56. Null when the cap is full or it could not be added.
	 */
	static @Nullable DiscipleEntity murid(ServerPlayer pemain, Vec3 at, Sosok sosok, int physique, int aktivitas, Component papan) {
		if (!muat(1)) {
			return null;
		}
		ServerLevel level = pemain.level();
		RandomSource random = level.getRandom();
		DiscipleData data = DiscipleData.fresh(random);
		if (physique > 0) {
			Talents.setPhysique(data, physique);
		}
		DiscipleEntity body = Disciples.spawnUnowned(level, at, data, papan, 0, sosok.lenganRamping(), sosok, 0,
				DiscipleEntity.Role.RIVAL, BlockPos.containing(at));
		if (body == null) {
			return null;
		}
		float yaw = hadap(body.position(), pemain.position());
		body.setYRot(yaw);
		body.setYHeadRot(yaw);
		body.setYBodyRot(yaw);
		body.setNoAi(true);
		body.addTag(TAG);
		body.setCustomNameVisible(true);
		if (physique > 0) {
			body.setSyncedTalent(DiscipleEntity.packTalent(physique, 0, 0));
		}
		if (aktivitas > 0) {
			body.setActivity(aktivitas, body.blockPosition());
		}
		UJI.add(new Uji(body, pemain, level.getGameTime() + UMUR));
		return body;
	}

	/** Removes every test body; returns how many. */
	static int hapus(MinecraftServer server) {
		int n = 0;
		for (Uji uji : UJI) {
			if (!uji.body.isRemoved()) {
				uji.body.discard();
				n++;
			}
		}
		UJI.clear();
		for (ServerLevel level : server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity != null && entity.entityTags().contains(TAG) && !entity.isRemoved()) {
					entity.discard();
					n++;
				}
			}
		}
		return n;
	}

	/** The tracked state of a body this class made, or null. */
	private static @Nullable Uji uji(LivingEntity body) {
		for (Uji uji : UJI) {
			if (uji.body == body) {
				return uji;
			}
		}
		return null;
	}

	// ================================================================== drivers

	/** Makes {@code body} play {@code klip} one after the other, starting at {@code mulai}, stepping {@code langkah}. */
	static void parade(LivingEntity body, int[] klip, int mulai, int langkah) {
		Uji uji = uji(body);
		if (uji == null || klip.length == 0) {
			return;
		}
		uji.mode = Mode.PARADE;
		uji.daftar = klip;
		uji.indeks = Math.floorMod(mulai, klip.length);
		uji.langkah = Math.max(1, langkah);
		uji.berikut = body.level().getGameTime() + 10L;
	}

	/** Walks {@code body} to and fro between {@code a} and {@code b} at {@code laju} (navigation speed factor). */
	static void jalan(LivingEntity body, Vec3 a, Vec3 b, double laju) {
		Uji uji = uji(body);
		if (uji != null) {
			uji.mode = Mode.JALAN;
			uji.a = a;
			uji.b = b;
			uji.keB = true;
			uji.laju = laju;
		}
	}

	/** Runs {@code body} round the player at radius 5, starting at angle {@code sudut}, jumping now and then. */
	static void pegas(LivingEntity body, double sudut) {
		Uji uji = uji(body);
		if (uji != null) {
			uji.mode = Mode.PEGAS;
			uji.sudut = sudut;
			uji.berikut = body.level().getGameTime() + 20L;
		}
	}

	/** The face show: every mood, the drained face, closed eyes, then a spoken greeting; again until it vanishes. */
	static void wajah(LivingEntity body) {
		Uji uji = uji(body);
		if (uji != null) {
			uji.mode = Mode.WAJAH;
			uji.tahap = 0;
			uji.berikut = body.level().getGameTime() + 20L;
		}
	}

	private static void tick(MinecraftServer server) {
		if (UJI.isEmpty()) {
			return;
		}
		Iterator<Uji> it = UJI.iterator();
		while (it.hasNext()) {
			Uji uji = it.next();
			LivingEntity body = uji.body;
			if (body.isRemoved() || !body.isAlive()) {
				it.remove();
				continue;
			}
			long now = body.level().getGameTime();
			if (now >= uji.habis) {
				body.discard();
				it.remove();
				continue;
			}
			switch (uji.mode) {
				case PARADE -> tickParade(uji, now);
				case JALAN -> tickJalan(uji, now);
				case PEGAS -> tickPegas(uji, now);
				case WAJAH -> tickWajah(uji, now);
				default -> {
				}
			}
		}
	}

	private static void tickParade(Uji uji, long now) {
		if (now < uji.berikut) {
			return;
		}
		int klip = uji.daftar[uji.indeks];
		uji.indeks = (uji.indeks + uji.langkah) % uji.daftar.length;
		int lama = GerakId.durasiSekali(klip, 0);
		if (lama <= 0) {
			lama = LAMA_LOOP;
		}
		if (uji.body instanceof DiscipleEntity murid && GerakId.fungsi(klip)) {
			murid.setActivity(GerakId.aktivitasFungsi(klip), murid.blockPosition());
		} else {
			SosokApi.gerak(uji.body, klip, 0, lama);
		}
		papan(uji.body, Component.literal(klip + " " + GerakId.namaAtauId(klip)));
		uji.berikut = now + lama + JEDA_KLIP;
	}

	private static void tickJalan(Uji uji, long now) {
		if (now % 10L != 0L || !(uji.body instanceof Mob mob) || uji.a == null || uji.b == null) {
			return;
		}
		Vec3 tujuan = uji.keB ? uji.b : uji.a;
		if (mob.position().distanceToSqr(tujuan) < 1.0D) {
			uji.keB = !uji.keB;
			tujuan = uji.keB ? uji.b : uji.a;
			mob.getNavigation().stop();
		}
		if (mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(tujuan.x, tujuan.y, tujuan.z, uji.laju);
		}
	}

	private static void tickPegas(Uji uji, long now) {
		if (now % 10L != 0L || !(uji.body instanceof Mob mob)) {
			return;
		}
		Vec3 pusat = uji.pemain.isRemoved() ? mob.position() : uji.pemain.position();
		uji.sudut += 0.35D;
		Vec3 titik = pusat.add(Math.cos(uji.sudut) * 5.0D, 0.0D, Math.sin(uji.sudut) * 5.0D);
		mob.getNavigation().moveTo(titik.x, titik.y, titik.z, 1.5D);
		if (now >= uji.berikut && mob.onGround()) {
			mob.getJumpControl().jump();
			uji.berikut = now + 60L + mob.getRandom().nextInt(40);
		}
	}

	private static void tickWajah(Uji uji, long now) {
		if (now < uji.berikut) {
			return;
		}
		LivingEntity body = uji.body;
		if (!uji.pemain.isRemoved()) {
			SosokApi.tatap(body, uji.pemain);
		}
		int tahap = uji.tahap;
		uji.tahap = (tahap + 1) % (Gaya.SUASANA_MAKS + 4);
		uji.berikut = now + 40L;
		if (tahap <= Gaya.SUASANA_MAKS) {
			SosokApi.setLuka(body, Gaya.LUKA_SEHAT);
			SosokApi.setSuasana(body, tahap, 0);
			papan(body, Component.translatable("kultivator.sosok.cmd.wajah.suasana", Component.translatable(Gaya.kunciSuasana(tahap))));
		} else if (tahap == Gaya.SUASANA_MAKS + 1) {
			SosokApi.setSuasana(body, Gaya.SUASANA_NETRAL, 0);
			SosokApi.setLuka(body, Gaya.LUKA_HABIS_QI);
			papan(body, Component.translatable("kultivator.sosok.cmd.wajah.luka", Component.translatable(Gaya.kunciLuka(Gaya.LUKA_HABIS_QI))));
		} else if (tahap == Gaya.SUASANA_MAKS + 2) {
			SosokApi.setLuka(body, Gaya.LUKA_SEHAT);
			SosokApi.gerak(body, GerakId.NAPAS_MEDITASI, 0, 40);
			papan(body, Component.translatable("kultivator.sosok.cmd.wajah.mata"));
		} else {
			SosokApi.setSuasana(body, Gaya.SUASANA_SENANG, 60);
			Component salam = Component.translatable("kultivator.sosok.cmd.wajah.salam");
			if (body instanceof KultivatorNpc npc) {
				npc.say(salam, 60);
			}
			SosokApi.bicara(body, 60);
			papan(body, Component.translatable("kultivator.sosok.cmd.wajah.bicara"));
			uji.berikut = now + 70L;
		}
	}

	// ================================================================== layout helpers

	/** The spot of column {@code kolom} (of {@code kolomJumlah}) and row {@code baris} in front of the player. */
	static Vec3 grid(ServerPlayer pemain, int kolom, int kolomJumlah, int baris, double jarak, double maju) {
		float yaw = pemain.getYRot() * Mth.DEG_TO_RAD;
		Vec3 depan = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
		Vec3 kanan = new Vec3(-depan.z, 0.0D, depan.x);
		double x = (kolom - (kolomJumlah - 1) / 2.0D) * jarak;
		double z = maju + baris * jarak;
		return pemain.position().add(kanan.scale(x)).add(depan.scale(z));
	}

	/** {@link #grid} with the standard spacing, two blocks ahead. */
	static Vec3 grid(ServerPlayer pemain, int kolom, int kolomJumlah, int baris) {
		return grid(pemain, kolom, kolomJumlah, baris, JARAK, 2.0D);
	}

	/** The yaw that looks from {@code dari} to {@code ke}. */
	static float hadap(Vec3 dari, Vec3 ke) {
		double dx = ke.x - dari.x;
		double dz = ke.z - dari.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
	}

	/** Sets a test body's plate (NPC plate or disciple custom name). */
	static void papan(LivingEntity body, Component text) {
		if (body instanceof KultivatorNpc npc) {
			npc.setPlate(text);
		} else {
			body.setCustomName(text);
		}
	}

	/** "juru_lelang · P": the plate of a gallery body. */
	static MutableComponent label(String rupa, int kelamin) {
		return Component.literal(rupa + " · ").append(Component.translatable(Sosok.kunciKelamin(kelamin)));
	}

	/** Realm of a test body: Immortal City roles ascended, non-combatants mortal, fighters Foundation. */
	private static int realmUntuk(NpcRole role, boolean milikPeran) {
		if (!milikPeran) {
			return Realm.FOUNDATION.ordinal();
		}
		if (role.immortal()) {
			return Realm.IMMORTAL_ASCENSION.ordinal();
		}
		return role.tier <= 0 ? Realm.MORTAL.ordinal() : role.tier >= 3 ? Realm.GOLDEN_CORE.ordinal() : Realm.FOUNDATION.ordinal();
	}

	private static void bersihkan() {
		UJI.removeIf(uji -> uji.body.isRemoved() || !uji.body.isAlive());
	}

	private GaleriUji() {
	}
}
