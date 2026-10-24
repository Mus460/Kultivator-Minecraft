package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaAnchors;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pemburu Lurus (DESIGN 38.T.7.9): the righteous hunters. Three jobs:
 * <ul>
 * <li><b>The camp</b> ({@code kota_tema/pos_pemburu}, inti's satellite): anchors {@code kota_tema/pemburu} bring a
 * PEMBURU_LURUS body each; the one with {@code ketua} is Han Qingshuang. Their "Jasa" tab opens the bounty board of
 * {@link BuronanIblis} (they also buy Kontrak Kepala there).</li>
 * <li><b>Interception</b>: a player with Infami &gt;= 40, outside safe ground, at most once per 5 world days, through the
 * shared daily enemy pressure ({@code Sibuk.enemyRefusal}/{@code enemyTaken}, entry {@code tema.pemburu}) and never in
 * real danger ({@code Sibuk.why}): a squad of 2-4 PEMBURU_LURUS (the player's realm +0..1, realm foes) led by Han
 * Qingshuang appears 18 blocks away and walks up. Han talks first (dialog {@code tema_iblis_cegat}): <b>Menyerah dan
 * menebus</b> ({@code 50 x (band + 1)} NB "denda tobat", Infami -20, they leave) / <b>Lawan</b> (they fight; winning:
 * Infami +5, standing -3 with their sect) / <b>Lari</b> (they give chase; 64 blocks away and they let him go).</li>
 * <li><b>Contract foes</b> of {@link SarangKontrak} kind {@code pemburu} and the siege's squads ({@link PenyerbuanLurus}).</li>
 * </ul>
 * Han Qingshuang never dies in this story: at 10 % health she withdraws wounded (she yields, the squad leaves).
 */
public final class PemburuLurus {

	public static final int INFAMI_MIN = 40;
	public static final int JEDA_HARI = 5;
	public static final int DENDA = 50;
	public static final double JARAK_MUNCUL = 18.0D;
	public static final double JARAK_LARI = 64.0D;
	public static final long CEGAT_TICK = 6L * 60L * 20L;
	/** Chance per check (every 30 s) that an eligible player is intercepted. */
	public static final double PELUANG = 0.08D;

	/** One interception squad. */
	static final class Regu {
		final UUID player;
		final List<KultivatorNpc> anggota = new ArrayList<>();
		final long mulai;
		final String sekte;
		@Nullable KultivatorNpc ketua;
		boolean bicara;
		boolean lawan;
		boolean lari;

		Regu(UUID player, long mulai, String sekte) {
			this.player = player;
			this.mulai = mulai;
			this.sekte = sekte;
		}
	}

	private static final Map<UUID, Regu> REGU = new HashMap<>();
	/** Camp anchor -> its body. */
	private static final Map<UUID, KultivatorNpc> KEMAH = new HashMap<>();

	private PemburuLurus() {
	}

	static void init() {
		Sibuk.registerEnemy("tema.pemburu", p -> REGU.containsKey(p.getUUID()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PemburuLurus::bolehLuka);
		ServerLivingEntityEvents.AFTER_DEATH.register(PemburuLurus::mati);
	}

	static void clear() {
		REGU.clear();
		KEMAH.clear();
	}

	// ================================================================== the camp

	/** Anchor {@code kota_tema/pemburu} of the hunters' camp: a hunter (Han Qingshuang for {@code ketua}). */
	static void nearbyPos(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		KultivatorNpc npc = KEMAH.get(anchor.getUUID());
		if (npc != null && !npc.isRemoved() && npc.isAlive()) {
			if (!within.isEmpty() && npc.getTarget() == null && (level.getGameTime() + npc.getId()) % 160L == 0L) {
				HidupApi.tatap(npc, within.getFirst());
			}
			return;
		}
		if (IblisAnchors.menunggu(anchor)) {
			return;
		}
		boolean ketua = "true".equals(anchor.data().getStringOr("ketua", "")) || anchor.data().getBooleanOr("ketua", false);
		long situs = anchor.site();
		if (ketua && !hanSedangBerburu(level.getServer())) {
			npc = TokohIblis.panggil(level, situs, TokohIblis.HAN_QINGSHUANG, anchor.blockPosition(), anchor.getYRot());
		} else {
			npc = HidupApi.tubuh(level, situs, NpcRole.PEMBURU_LURUS, anchor.blockPosition(), anchor.getYRot(), (int) (anchor.blockPosition().asLong() * 0x2545F491L));
		}
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("iblis_pemburu", true);
		npc.memory().putBoolean("hold", true);
		npc.setDiamDiTitik(true);
		HidupApi.klip(npc, ketua ? GerakId.SIKAP_GAGANG : GerakId.BERDIRI_JAGA, 0, 0);
		KEMAH.put(anchor.getUUID(), IblisAnchors.simpan(anchor, npc));
	}

	private static boolean hanSedangBerburu(MinecraftServer server) {
		for (Regu r : REGU.values()) {
			if (r.ketua != null && !r.ketua.isRemoved() && TokohIblis.adalah(r.ketua, TokohIblis.HAN_QINGSHUANG)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== interception

	/** Every 600 ticks per online player: may a squad come for him now? */
	static void periksa(ServerPlayer p) {
		if (!TemaConfig.aktif(Tema.IBLIS) || REGU.containsKey(p.getUUID()) || p.isCreative() || p.isSpectator()) {
			return;
		}
		ServerLevel level = p.level();
		if (level.dimension() != Level.OVERWORLD || IblisMeter.infami(p) < INFAMI_MIN || p.getRandom().nextDouble() >= PELUANG) {
			return;
		}
		if (alasanTidak(p) != null) {
			return;
		}
		cegat(p, false);
	}

	/** Why no squad may come now (null = it may): last time, safe ground, the shared pressure, real danger. */
	static @Nullable String alasanTidak(ServerPlayer p) {
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		CompoundTag mine = IblisData.pemain(level.getServer(), p.getUUID());
		if (hari - mine.getLongOr("pemburu_hari", -100L) < JEDA_HARI) {
			return "jeda";
		}
		BlockPos pos = p.blockPosition();
		if (TempatZones.safe(level, pos) || DuniaApi.safeGround(level, pos) || IblisKota.di(p) != null) {
			return "aman";
		}
		String busy = Sibuk.why(p);
		if (busy != null) {
			return busy;
		}
		return Sibuk.enemyRefusal(p);
	}

	/** Brings a squad for {@code p} ({@code uji}: the test command, no day or pressure check). */
	static boolean cegat(ServerPlayer p, boolean uji) {
		ServerLevel level = p.level();
		BlockPos spot = titikMuncul(level, p);
		if (spot == null) {
			return false;
		}
		String sekte = sekteLurus(p.blockPosition());
		Regu regu = new Regu(p.getUUID(), level.getGameTime(), sekte == null ? "" : sekte);
		int n = 2 + p.getRandom().nextInt(3);
		int realm = Mth.clamp(Cultivators.get(p).realm().ordinal() + p.getRandom().nextInt(2), 0, Realm.values().length - 1);
		int tier = Mth.clamp(1 + IblisKota.band(p) / 2, 1, 5);
		KultivatorNpc han = hanSedangBerburu(level.getServer()) ? null : TokohIblis.panggil(level, 0L, TokohIblis.HAN_QINGSHUANG, spot, p.getYRot() + 180.0F);
		if (han != null) {
			siapkan(han, tier + 1, realm, true);
			regu.ketua = han;
			regu.anggota.add(han);
		}
		for (int i = regu.anggota.size(); i < n; i++) {
			BlockPos at = spot.offset((i % 2 == 0 ? 2 : -2), 0, i);
			KultivatorNpc h = hunter(level, at, p.getYRot() + 180.0F, (int) (level.getGameTime() * 31L + i * 977L));
			if (h == null) {
				continue;
			}
			siapkan(h, tier, Math.max(0, realm - (i == 0 ? 0 : 1)), false);
			if (regu.ketua == null) {
				regu.ketua = h;
			}
			regu.anggota.add(h);
		}
		if (regu.anggota.isEmpty()) {
			return false;
		}
		for (KultivatorNpc h : regu.anggota) {
			h.memory().putString("iblis_regu", p.getUUID().toString());
			h.setCompanion(null, 0L);
			h.getNavigation().moveTo(p, 1.0D);
		}
		REGU.put(p.getUUID(), regu);
		if (!uji) {
			IblisData.pemain(level.getServer(), p.getUUID()).putLong("pemburu_hari", IblisKota.hari(level));
			IblisData.ubah(level.getServer());
			Sibuk.enemyTaken(p, "tema.pemburu");
		}
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.datang").withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.iblis.pemburu.datang.sub", SekteApi.diplomasi().name(regu.sekte) == null
						? Component.translatable("kultivator.kota_tema.iblis.pemburu.gilda") : SekteApi.diplomasi().name(regu.sekte))
						.withStyle(ChatFormatting.YELLOW), 5, 60, 15);
		return true;
	}

	private static void siapkan(KultivatorNpc h, int tier, int realm, boolean han) {
		Component plate = han ? h.plate() : Component.translatable("kultivator.kota.papan_nama", KotaNames.person(h.look(), false),
				Component.translatable("kultivator.kota_tema.iblis.pemburu.gelar"));
		h.setup(NpcRole.PEMBURU_LURUS, tier, h.look(), realm, Math.floorMod(h.look(), 4), h.dao(), h.voice(), h.robe(), plate);
		if (han) {
			TokohIblis.tandai(h, TokohIblis.HAN_QINGSHUANG);
		}
		h.memory().putBoolean("hold", false);
		h.memory().putBoolean("iblis_pemburu", true);
		h.setDiamDiTitik(false);
		h.arm(true);
		TechniquePower.markRealmFoe(h);
	}

	private static @Nullable KultivatorNpc hunter(ServerLevel level, BlockPos at, float yaw, int look) {
		return HidupApi.tubuh(level, 0L, NpcRole.PEMBURU_LURUS, at, yaw, look);
	}

	private static @Nullable BlockPos titikMuncul(ServerLevel level, ServerPlayer p) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = Math.toRadians(p.getYRot() + 180.0F + (attempt - 2) * 35.0F);
			int x = Mth.floor(p.getX() - Math.sin(angle) * JARAK_MUNCUL);
			int z = Mth.floor(p.getZ() + Math.cos(angle) * JARAK_MUNCUL);
			BlockPos ground = TempatApi.groundSpot(level, x, z, p.getBlockY());
			if (ground != null && level.isPositionEntityTicking(ground) && Math.abs(ground.getY() - p.getBlockY()) < 12) {
				return ground;
			}
		}
		return null;
	}

	/** The righteous sect nearest to {@code pos} (null: the Cultivator Guild issues it). */
	static @Nullable String sekteLurus(BlockPos pos) {
		return SekteApi.diplomasi().nearest(pos, 0);
	}

	/** Every 20 ticks: squads walk up, Han opens the talk, chase and give-up rules, a squad that lost its player leaves. */
	static void tick(MinecraftServer server) {
		if (REGU.isEmpty()) {
			return;
		}
		for (Regu r : new ArrayList<>(REGU.values())) {
			r.anggota.removeIf(h -> h.isRemoved() || !h.isAlive());
			ServerPlayer p = server.getPlayerList().getPlayer(r.player);
			if (p == null || r.anggota.isEmpty()) {
				selesai(r, p, r.anggota.isEmpty() && r.lawan ? "menang" : "pergi");
				continue;
			}
			KultivatorNpc lead = r.ketua != null && !r.ketua.isRemoved() && r.ketua.isAlive() ? r.ketua : r.anggota.getFirst();
			double d = lead.distanceTo(p);
			long now = p.level().getGameTime();
			if (lead.level() != p.level() || now - r.mulai > CEGAT_TICK || (r.lari || r.lawan) && d > JARAK_LARI) {
				if (r.lari && d > JARAK_LARI) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.lolos").withStyle(ChatFormatting.GRAY));
				}
				selesai(r, p, "pergi");
				continue;
			}
			if (!r.bicara) {
				if (d < 6.0D) {
					r.bicara = true;
					for (KultivatorNpc h : r.anggota) {
						h.getNavigation().stop();
						HidupApi.tatap(h, p);
					}
					Voice.say(lead, "tema.iblis.hunter", null);
					HidupApi.klip(lead, GerakId.TUNJUK, 0, 40);
					CeritaApi.dialog(p, lead, "tema_iblis_cegat");
				} else if (now % 40L == 0L) {
					for (KultivatorNpc h : r.anggota) {
						h.getNavigation().moveTo(p, 1.1D);
					}
				}
			} else if (!r.lawan && !r.lari && now - r.mulai > 60L * 20L) {
				// silence is an answer: they draw
				lawan(p);
			}
		}
	}

	/** Dialog effect {@code iblis_pemburu menyerah|lawan|lari}. */
	static void jawab(ServerPlayer p, String how) {
		Regu r = REGU.get(p.getUUID());
		if (r == null) {
			return;
		}
		switch (how) {
			case "menyerah" -> {
				long fine = (long) DENDA * (IblisKota.band(p) + 1L);
				if (!NilaiBatu.pay(p, fine)) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(fine)).withStyle(ChatFormatting.RED));
					lawan(p);
					return;
				}
				IblisMeter.tambahInfami(p, -20, "tobat");
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.tobat", NilaiBatu.text(fine)).withStyle(ChatFormatting.GREEN));
				CeritaApi.peristiwa(p, IblisCerita.PEMBURU, "menyerah");
				selesai(r, p, "tobat");
			}
			case "lari" -> {
				r.lari = true;
				for (KultivatorNpc h : r.anggota) {
					h.markHostile(p);
				}
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.kejar").withStyle(ChatFormatting.RED));
			}
			default -> lawan(p);
		}
	}

	private static void lawan(ServerPlayer p) {
		Regu r = REGU.get(p.getUUID());
		if (r == null) {
			return;
		}
		r.lawan = true;
		for (KultivatorNpc h : r.anggota) {
			h.markHostile(p);
			h.setTarget(p);
		}
	}

	/** Han Qingshuang never dies here: at 10 % health she withdraws and the squad yields with her. */
	private static boolean bolehLuka(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof KultivatorNpc npc) || !TokohIblis.adalah(npc, TokohIblis.HAN_QINGSHUANG)) {
			return true;
		}
		if (npc.getHealth() - amount > npc.getMaxHealth() * 0.1F) {
			return true;
		}
		npc.setHealth(npc.getMaxHealth() * 0.1F);
		if (source.getEntity() instanceof ServerPlayer p) {
			Regu r = REGU.get(p.getUUID());
			if (r != null) {
				HidupApi.klip(npc, GerakId.PINCANG, 0, 80);
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.han_mundur").withStyle(ChatFormatting.GOLD));
				selesai(r, p, "menang");
				return false;
			}
			PenyerbuanLurus.hanKalah(p, npc);
		}
		return false;
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof KultivatorNpc npc) || !npc.memory().contains("iblis_regu")) {
			return;
		}
		Regu r = REGU.get(parse(npc.memory().getStringOr("iblis_regu", "")));
		if (r == null) {
			return;
		}
		r.anggota.remove(npc);
		r.anggota.removeIf(h -> h.isRemoved() || !h.isAlive());
		if (r.anggota.isEmpty() && entity.level().getServer() != null) {
			selesai(r, entity.level().getServer().getPlayerList().getPlayer(r.player), "menang");
		}
	}

	private static @Nullable UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void selesai(Regu r, @Nullable ServerPlayer p, String how) {
		REGU.remove(r.player);
		if (p != null && "menang".equals(how) && r.lawan) {
			IblisMeter.tambahInfami(p, 5, "lawan_pemburu");
			if (!r.sekte.isEmpty()) {
				SekteApi.diplomasi().adjust(p.getUUID(), r.sekte, -3, "38tk.pemburu");
			}
			CeritaApi.peristiwa(p, IblisCerita.PEMBURU, "menang");
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.kalah").withStyle(ChatFormatting.DARK_RED));
		}
		for (KultivatorNpc h : r.anggota) {
			if (!h.isRemoved()) {
				if (p != null) {
					h.forgive(p.getUUID());
				}
				h.vanish();
			}
		}
	}

	/** True while a squad hunts the player. */
	public static boolean diburu(ServerPlayer p) {
		return REGU.containsKey(p.getUUID());
	}

	// ================================================================== bodies for other systems

	/** A squad that is the foe of a Sarang Kontrak contract (no talk; they fight on sight). */
	static List<LivingEntity> reguKontrak(ServerLevel level, Vec3 at, ServerPlayer p, int tier, int n, String tag) {
		List<LivingEntity> out = new ArrayList<>();
		int realm = Mth.clamp(Cultivators.get(p).realm().ordinal(), 0, Realm.values().length - 1);
		for (int i = 0; i < n; i++) {
			KultivatorNpc h = hunter(level, BlockPos.containing(at.add(i * 1.5D - n * 0.75D, 0.0D, (i % 2) * 2.0D)), 0.0F,
					(int) (at.x * 73856093.0D) ^ (int) (at.z * 19349663.0D) ^ i * 83492791);
			if (h == null) {
				continue;
			}
			siapkan(h, tier, Math.max(0, realm - (i == 0 ? 0 : 1)), false);
			h.addTag(tag);
			h.markHostile(p);
			out.add(h);
		}
		return out;
	}

	/** One siege fighter (no talk, a realm foe; side decided by the siege). */
	static @Nullable KultivatorNpc prajurit(ServerLevel level, BlockPos at, float yaw, int look, int tier, int realm) {
		KultivatorNpc h = hunter(level, at, yaw, look);
		if (h != null) {
			siapkan(h, tier, realm, false);
			h.memory().putBoolean("iblis_serbu", true);
		}
		return h;
	}

	/** Lines for {@code uji tema pemburu}: why a squad could or could not come now. */
	static Component pratinjau(ServerPlayer p) {
		String why = alasanTidak(p);
		return Component.translatable("kultivator.kota_tema.iblis.cmd.pemburu", IblisMeter.infami(p), INFAMI_MIN,
				why == null ? Component.translatable("kultivator.kota_tema.iblis.cmd.boleh") : Component.literal(why));
	}

	static @Nullable TemaKota.Kota kotaKemah(ServerLevel level, BlockPos camp) {
		return TemaAnchors.kotaIblisDari(level, camp);
	}
}
