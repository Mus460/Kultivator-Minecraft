package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The night district (DESIGN 42.9.6, 42.7.6), open 18:00-02:00 in every town that has the lots of 42W (dice tables, the shadow
 * theatre, the fan-dancer stage): a place is a district when its plan has a {@code meja_dadu} titik or a {@code panggung} with
 * {@code acara} {@code wayang} / {@code penari_kipas}. While a player is within 64 blocks of it the district is set up:
 * <ul>
 * <li>a {@link MejaDaduEntity} on every {@code meja_dadu} titik with its keeper (a stone banker, dice cup in hand) who throws
 * with the cup whenever somebody plays ({@link Guanpu});</li>
 * <li>the fan dancer ({@code penari_kipas} stage: {@code TARI_YANGGE}, fan prop) and, for the theatre, the screen of
 * {@link TeaterBayang};</li>
 * <li>the loan shark ({@code obrol} titik with {@code peran=RENTENIR}) counting coins and calling for his money;</li>
 * <li>two drinkers playing <i>huaquan</i> (finger guessing) on an {@code obrol} corner: the two bodies shout numbers in step
 * and the loser drinks; <b>shift-click</b> a drinker to bet 2 NB on the next round (45 % to win, a win pays 2 : 1: expected
 * return 0.90);</li>
 * <li>up to three night-market tents ({@link TendaPasarEntity}) with their hawkers around the centre of the district; shift-click
 * a hawker of a snack tent for a tanghulu (3 NB);</li>
 * <li>the pipa and erhu air of {@link Lagu} over the whole district until 01:00.</li>
 * </ul>
 * Pickpockets are 1.5 times and brawls twice as likely here at night ({@link #faktorCopet}, {@link #faktorPerkelahian}; 42D-P's
 * scheduler asks them). The district is data only without a player near.
 */
public final class DistrikMalam {

	/** Opening hours (wrap past midnight). */
	public static final double BUKA = 18.0D;
	public static final double TUTUP = 2.0D;
	/** The music stops at 01:00. */
	static final double MUSIK_SAMPAI = 1.0D;
	public static final long TARUHAN_HUAQUAN = 2L;
	public static final long HARGA_TANGHULU = 3L;
	static final String L = "kultivator.kota42.acara.malam.";
	/** Share of huaquan bets won (the return of a win is 2:1, so the expected return is 0.90). */
	static final double PELUANG_HUAQUAN = 0.45D;

	/** One district at work. */
	private static final class Malam {
		final long situs;
		final Map<String, KultivatorNpc> badan = new HashMap<>();
		final Map<String, MejaDaduEntity> meja = new HashMap<>();
		Vec3 pusat = Vec3.ZERO;
		/** Huaquan round number and the game tick it began. */
		long babakHuaquan = Long.MIN_VALUE;
		boolean menangHuaquan;

		Malam(long situs) {
			this.situs = situs;
		}
	}

	private static final Map<Long, Malam> MALAM = new HashMap<>();
	private static final TendaPasarEntity.Pengelola TENDA = new TendaPasarEntity.Pengelola();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Guanpu.init();
		ServerTickEvents.END_SERVER_TICK.register(DistrikMalam::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !player.isShiftKeyDown() || !(player instanceof ServerPlayer sp)
					|| !(entity instanceof KultivatorNpc npc)) {
				return InteractionResult.PASS;
			}
			return klik(sp, npc) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	private static void lupakan() {
		for (Malam m : MALAM.values()) {
			sapu(m);
		}
		MALAM.clear();
		TENDA.hapusSemua();
		ADA.clear();
	}

	// ================================================================== the clock and the factors

	/** True from 18:00 to 02:00 with the switches on. */
	public static boolean buka(MinecraftServer server) {
		KotaConfig.Acara c = KotaConfig.acara();
		return c.aktif && c.distrikMalam && KalenderKota.antara(KalenderKota.jamDesimal(server), BUKA, TUTUP);
	}

	/** Pickpockets in an open district: x1.5 (42D-P's chance factor), else 1. */
	public static double faktorCopet(MinecraftServer server, long situs) {
		return buka(server) && ada(server, situs) ? 1.5D : 1.0D;
	}

	/** Brawls in an open district: x2, else 1. */
	public static double faktorPerkelahian(MinecraftServer server, long situs) {
		return buka(server) && ada(server, situs) ? 2.0D : 1.0D;
	}

	/** Per site: {@link Long#MAX_VALUE} once lots were found, else the server tick of the next look (unloaded pieces or growth can add lots). */
	private static final Map<Long, Long> ADA = new HashMap<>();

	/** True when the plan of {@code situs} holds night-district lots. */
	public static boolean ada(MinecraftServer server, long situs) {
		long now = server.getTickCount();
		Long cek = ADA.get(situs);
		if (cek != null && now < cek) {
			return cek == Long.MAX_VALUE;
		}
		boolean ada = false;
		for (Titik t : dev.kultivator.dunia.tempat.TempatApi.titik(server.overworld(), situs, "")) {
			if (distrik(t)) {
				ada = true;
				break;
			}
		}
		ADA.put(situs, ada ? Long.MAX_VALUE : now + 1200L);
		return ada;
	}

	private static boolean distrik(Titik t) {
		String acara = t.data().get("acara");
		return KotaTitik.MEJA_DADU.equals(t.jenis()) || KotaTitik.PANGGUNG.equals(t.jenis()) && ("wayang".equals(acara) || "penari_kipas".equals(acara));
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		long tc = server.getTickCount();
		boolean lambat = tc % 20L == 11L;
		boolean cepat = tc % 5L == 4L;
		if (!lambat && !cepat) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!buka(server)) {
			if (!MALAM.isEmpty()) {
				lupakan();
			}
			return;
		}
		long now = level.getGameTime();
		double jam = KalenderKota.jamDesimal(server);
		if (lambat) {
			for (TempatHuni huni : KalenderKota.aktif(level)) {
				if (!KalenderKota.kota(huni)) {
					continue;
				}
				List<Titik> semua = new ArrayList<>();
				for (String type : new String[] {KotaTitik.MEJA_DADU, KotaTitik.PANGGUNG, "obrol"}) {
					for (Titik t : huni.titik(type)) {
						if (type.equals("obrol") || distrik(t)) {
							semua.add(t);
						}
					}
				}
				boolean ada = false;
				for (Titik t : semua) {
					if (distrik(t)) {
						ada = true;
						break;
					}
				}
				Malam m = MALAM.get(huni.situsId);
				Vec3 pusat = pusat(semua);
				if (!ada || !KalenderKota.adaPemain(level, BlockPos.containing(pusat), 64.0D)) {
					if (m != null) {
						sapu(m);
						MALAM.remove(huni.situsId);
					}
					continue;
				}
				if (m == null) {
					m = new Malam(huni.situsId);
					MALAM.put(huni.situsId, m);
				}
				m.pusat = pusat;
				siapkan(level, huni, m, semua, jam);
			}
			TENDA.rapikan();
			Iterator<Map.Entry<Long, Malam>> it = MALAM.entrySet().iterator();
			while (it.hasNext()) {
				Malam m = it.next().getValue();
				if (Pemanggil.huni(m.situs) == null) {
					sapu(m);
					it.remove();
				}
			}
		}
		if (cepat) {
			for (Malam m : MALAM.values()) {
				animasi(level, m, now, jam);
			}
		}
	}

	/** The mean position of the district's points. */
	private static Vec3 pusat(List<Titik> semua) {
		double x = 0.0D;
		double z = 0.0D;
		double y = 0.0D;
		int n = 0;
		for (Titik t : semua) {
			if (distrik(t)) {
				x += t.pos().getX() + 0.5D;
				y += t.pos().getY();
				z += t.pos().getZ() + 0.5D;
				n++;
			}
		}
		return n == 0 ? Vec3.ZERO : new Vec3(x / n, y / n, z / n);
	}

	private static void sapu(Malam m) {
		for (KultivatorNpc npc : m.badan.values()) {
			if (!npc.isRemoved()) {
				npc.vanish();
			}
		}
		m.badan.clear();
		for (MejaDaduEntity e : m.meja.values()) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
		m.meja.clear();
	}

	// ================================================================== set and bodies

	private static void siapkan(ServerLevel level, TempatHuni huni, Malam m, List<Titik> semua, double jam) {
		int no = 0;
		int tarian = 0;
		int obrol = 0;
		for (Titik t : semua) {
			String acara = t.data().get("acara");
			Vec3 at = Vec3.atBottomCenterOf(t.pos());
			float yaw = t.hadap() == null ? 0.0F : t.hadap().toYRot();
			switch (t.jenis()) {
				case KotaTitik.MEJA_DADU -> {
					String kunci = "meja" + no++;
					meja(level, huni, m, kunci, t, at, yaw);
				}
				case KotaTitik.PANGGUNG -> {
					if ("penari_kipas".equals(acara) && KalenderKota.antara(jam, 20.0D, 1.0D)) {
						orang(level, huni, m, "penari" + tarian++, NpcRole.PENARI, at, yaw, huni.situsId * 13L + tarian, true, GerakId.MENARI_PANEN);
					}
				}
				default -> {
					if ("RENTENIR".equals(t.data().get("peran"))) {
						orang(level, huni, m, "rentenir", NpcRole.MAKELAR_GELAP, at, yaw, huni.situsId * 17L + 5L, false, 0);
					} else if (obrol < 2 && t.data().get("peran") == null) {
						orang(level, huni, m, "huaquan" + obrol, NpcRole.WARGA, at, yaw, huni.situsId * 19L + 100L + obrol, obrol == 0, 0);
						obrol++;
					}
				}
			}
		}
		pasar(level, huni, m);
	}

	private static void meja(ServerLevel level, TempatHuni huni, Malam m, String kunci, Titik t, Vec3 at, float yaw) {
		MejaDaduEntity e = m.meja.get(kunci);
		if (e == null || e.isRemoved()) {
			if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 2 > DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
				return;
			}
			e = KotaAcaraEntities.MEJA_DADU.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				return;
			}
			e.ikat(huni.situsId, t.pos().asLong());
			e.pindah(at, yaw);
			String v = t.data().get("varian");
			e.setVarian("kedai".equals(v) ? MejaDaduEntity.VARIAN_KEDAI : "gelap".equals(v) ? MejaDaduEntity.VARIAN_GELAP : MejaDaduEntity.VARIAN_GUANPU);
			if (!level.addFreshEntity(e)) {
				return;
			}
			DuniaBudget.track(KotaAcaraEntities.ANGGARAN, e);
			m.meja.put(kunci, e);
		}
		// the banker behind the cup
		Direction hadap = t.hadap() == null ? Direction.NORTH : t.hadap();
		Vec3 sisi = at.add(hadap.getStepX() * -1.1D, 0.0D, hadap.getStepZ() * -1.1D);
		orang(level, huni, m, kunci + "_bandar", NpcRole.BANDAR_BATU, sisi, yaw + 180.0F, huni.situsId * 23L + kunci.hashCode(), true, 0);
	}

	/** Keeps the body {@code kunci} of the district (spawns it once), standing on {@code at} facing {@code yaw}. */
	private static @Nullable KultivatorNpc orang(ServerLevel level, TempatHuni huni, Malam m, String kunci, NpcRole peran, Vec3 at, float yaw,
			long seed, boolean tetap, int klip) {
		KultivatorNpc npc = m.badan.get(kunci);
		if (npc != null && !npc.isRemoved()) {
			return npc;
		}
		npc = HidupApi.tubuhStaf(level, huni.situsId, peran, BlockPos.containing(at), yaw, (int) (seed ^ seed >>> 32));
		if (npc == null) {
			return null;
		}
		npc.memory().putBoolean("hold", true);
		npc.teleportTo(at.x, at.y, at.z);
		npc.setYRot(yaw);
		npc.setYHeadRot(yaw);
		m.badan.put(kunci, npc);
		return npc;
	}

	/** The night market: three tents in a ring of 8 blocks around the district's centre, a hawker at each. */
	private static void pasar(ServerLevel level, TempatHuni huni, Malam m) {
		int[] varian = {TendaPasarEntity.VARIAN_JAJANAN, TendaPasarEntity.VARIAN_MAINAN, TendaPasarEntity.VARIAN_PERMAINAN};
		for (int i = 0; i < 3; i++) {
			double a = Math.PI * 2.0D * i / 3.0D + (huni.situsId & 7L) * 0.3D;
			BlockPos at = MusimKota.tanah(level, BlockPos.containing(m.pusat.add(Math.cos(a) * 8.0D, 0.0D, Math.sin(a) * 8.0D)));
			float yaw = (float) Math.toDegrees(Math.atan2(m.pusat.z - at.getZ(), m.pusat.x - at.getX())) - 90.0F;
			TENDA.pastikan(level, huni.situsId, huni.situsId + ":malam:" + i, at, yaw, varian[i], 0xB0302E);
			Vec3 pj = Vec3.atBottomCenterOf(at).add(Math.cos(a) * -1.5D, 0.0D, Math.sin(a) * -1.5D);
			orang(level, huni, m, "pedagang" + i, NpcRole.PEDAGANG_BAZAR, Vec3.atBottomCenterOf(BlockPos.containing(pj)), yaw + 180.0F,
					huni.situsId * 29L + i, true, 0);
		}
	}

	// ================================================================== animation (every 5 ticks)

	private static void animasi(ServerLevel level, Malam m, long now, double jam) {
		BlockPos pusat = BlockPos.containing(m.pusat);
		if (KalenderKota.antara(jam, BUKA, MUSIK_SAMPAI)) {
			Lagu.mainkan(level, pusat, Lagu.MALAM, now - 5L, now, 28.0D);
		}
		// the keepers: a cup in hand, throwing when the cup shakes
		for (Map.Entry<String, MejaDaduEntity> e : m.meja.entrySet()) {
			KultivatorNpc bandar = m.badan.get(e.getKey() + "_bandar");
			if (bandar == null || bandar.isRemoved()) {
				continue;
			}
			boolean kocok = e.getValue().keadaan() == MejaDaduEntity.KEADAAN_KOCOK;
			int klip = kocok ? KotaGerakId.LEMPAR_DADU : GerakId.SIKAP_BERKACAK;
			if (SosokApi.gerakSekarang(bandar) != klip) {
				SosokApi.gerak(bandar, klip, 0, 0, KotaGerakId.PROP_DADU_CAWAN, 0, 0);
			}
		}
		for (Map.Entry<String, KultivatorNpc> e : m.badan.entrySet()) {
			KultivatorNpc npc = e.getValue();
			if (npc == null || npc.isRemoved()) {
				continue;
			}
			String k = e.getKey();
			if (k.startsWith("penari")) {
				if (SosokApi.gerakSekarang(npc) != KotaGerakId.TARI_YANGGE) {
					SosokApi.gerak(npc, KotaGerakId.TARI_YANGGE, 0, 0, KotaGerakId.PROP_KIPAS_TARI, KotaGerakId.PROP_KIPAS_TARI, 0);
				}
			} else if (k.equals("rentenir")) {
				if (SosokApi.gerakSekarang(npc) != KotaGerakId.HITUNG_KOIN) {
					SosokApi.gerak(npc, KotaGerakId.HITUNG_KOIN, 0, 0, PropId.TIDAK_ADA, 0, 0);
				}
				if (now / 600L != (now - 5L) / 600L) {
					npc.say(Component.translatable(L + "rentenir." + (int) Math.floorMod(now / 600L, 4L)), 100);
				}
			} else if (k.startsWith("pedagang")) {
				if (SosokApi.gerakSekarang(npc) != GerakId.SERU_DAGANG) {
					SosokApi.gerak(npc, GerakId.SERU_DAGANG, 0, 0, PropId.TIDAK_ADA, 0, 0);
				}
				if (now / 400L != (now - 5L) / 400L) {
					npc.say(Component.translatable(L + "pedagang." + (int) Math.floorMod(now / 400L + k.hashCode(), 4L)), 80);
				}
			}
		}
		huaquan(level, m, now);
	}

	/** Two drinkers play finger guessing: 100-tick rounds, the numbers shouted at 20 and 40, the loser drinks. */
	private static void huaquan(ServerLevel level, Malam m, long now) {
		KultivatorNpc a = m.badan.get("huaquan0");
		KultivatorNpc b = m.badan.get("huaquan1");
		if (a == null || b == null || a.isRemoved() || b.isRemoved()) {
			return;
		}
		long babak = now / 100L;
		long fase = now % 100L;
		a.getLookControl().setLookAt(b, 30.0F, 30.0F);
		b.getLookControl().setLookAt(a, 30.0F, 30.0F);
		if (babak != m.babakHuaquan) {
			m.babakHuaquan = babak;
			m.menangHuaquan = level.getRandom().nextDouble() < 0.5D;
			SosokApi.gerakSinkron(a, KotaGerakId.HUAQUAN_A, now);
			SosokApi.gerakSinkron(b, KotaGerakId.HUAQUAN_B, now);
		}
		if (fase < 5L || fase >= 20L && fase < 25L || fase >= 40L && fase < 45L) {
			int n1 = (int) Math.floorMod(babak * 7L + fase, 11L);
			int n2 = (int) Math.floorMod(babak * 5L + fase + 3L, 11L);
			if (fase >= 20L && fase < 25L) {
				a.say(Component.translatable(L + "huaquan.seru", n1), 30);
			} else if (fase >= 40L && fase < 45L) {
				b.say(Component.translatable(L + "huaquan.seru", n2), 30);
			}
		}
		if (fase >= 60L && fase < 65L) {
			KultivatorNpc kalah = m.menangHuaquan ? b : a;
			SosokApi.gerak(kalah, KotaGerakId.MINUM_ARAK, 0, 40, KotaGerakId.PROP_CAWAN_ARAK, 0, 0);
			SosokApi.gerak(m.menangHuaquan ? a : b, GerakId.TERTAWA, 0, 40);
		}
	}

	// ================================================================== player verbs

	private static boolean klik(ServerPlayer player, KultivatorNpc npc) {
		for (Malam m : MALAM.values()) {
			for (Map.Entry<String, KultivatorNpc> e : m.badan.entrySet()) {
				if (e.getValue() != npc) {
					continue;
				}
				String k = e.getKey();
				if (k.startsWith("huaquan")) {
					bertaruh(player, m, npc);
					return true;
				}
				if (k.equals("pedagang0")) {
					jajan(player, npc);
					return true;
				}
				return false;
			}
		}
		return false;
	}

	/** Betting on the huaquan round of the drinkers: 2 NB, 45 % to win 2 : 1. */
	private static void bertaruh(ServerPlayer player, Malam m, KultivatorNpc npc) {
		if (!EkonomiApi.bayar(player, TARUHAN_HUAQUAN, "huaquan")) {
			Msg.actionBar(player, Component.translatable(L + "huaquan.kurang", TARUHAN_HUAQUAN).withStyle(ChatFormatting.RED));
			return;
		}
		HidupApi.tatap(npc, player);
		if (player.getRandom().nextDouble() < PELUANG_HUAQUAN) {
			EkonomiApi.beri(player, TARUHAN_HUAQUAN * 2L, "huaquan_menang");
			npc.say(Component.translatable(L + "huaquan.kalah"), 60);
			Msg.actionBar(player, Component.translatable(L + "huaquan.menang", TARUHAN_HUAQUAN).withStyle(ChatFormatting.GOLD));
		} else {
			npc.say(Component.translatable(L + "huaquan.menangnya"), 60);
			Msg.actionBar(player, Component.translatable(L + "huaquan.kalah_taruh", TARUHAN_HUAQUAN).withStyle(ChatFormatting.GRAY));
		}
	}

	private static void jajan(ServerPlayer player, KultivatorNpc npc) {
		if (!EkonomiApi.bayar(player, HARGA_TANGHULU, "tanghulu_malam")) {
			Msg.actionBar(player, Component.translatable(L + "jajan.kurang", HARGA_TANGHULU).withStyle(ChatFormatting.RED));
			return;
		}
		PeristiwaJalan.beri(player, new ItemStack(KotaAcaraItems.TANGHULU));
		HidupApi.klip(npc, KotaGerakId.BERI_KOIN, 0, 30);
		npc.say(Component.translatable(L + "jajan.terima"), 60);
		Msg.actionBar(player, Component.translatable(L + "jajan.ok", HARGA_TANGHULU).withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== test commands (uji kota_hidup acara malam ...)

	static LiteralArgumentBuilder<CommandSourceStack> ujiMalam() {
		return Commands.literal("malam").executes(ctx -> {
			TempatInfo info = KalenderKota.tempatUji(ctx);
			if (info == null) {
				return 0;
			}
			MinecraftServer server = ctx.getSource().getServer();
			Malam m = MALAM.get(info.situsId());
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.malam", ada(server, info.situsId()) ? "ya" : "tidak",
					buka(server) ? "buka" : "tutup", m == null ? 0 : m.meja.size(), m == null ? 0 : m.badan.size(),
					String.format(java.util.Locale.ROOT, "%.1f", faktorCopet(server, info.situsId())),
					String.format(java.util.Locale.ROOT, "%.1f", faktorPerkelahian(server, info.situsId()))), false);
			return 1;
		});
	}

	private DistrikMalam() {
	}
}
