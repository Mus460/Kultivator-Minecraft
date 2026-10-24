package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Advancements;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jam Berburu (DESIGN 38.T.7.8 butir 2): the hunting hour of the Kota Iblis, 13000-23000 outside the Pact Zone (calendar
 * event {@code jam_berburu}, every night). Once a night per player (twice during the Pesta Darah), 1-3 demonic
 * cultivators (rogue cultivators of variant DEMONIC, role QUEST, rogue tier from the band, realm the player's +-1, realm
 * foes by the rogue type) bar the way: "Upeti jalan, atau darahmu?" (voice {@code tema.iblis.hunt_hour}). The talk is
 * shown as a title with two answers in chat (the rogue is no townsfolk to hold a dialog): <b>Bayar</b>
 * {@code 5 x (band + 1)} NB ({@code /kultivasi tema_iblis bayar}, or the click in chat) and they melt into the dark, or
 * <b>Lawan</b>: they fight; a won hunt gives Gentar +10 and the usual rogue loot.
 * <ul>
 * <li>Gentar &gt;= 30: half as often; Gentar &gt;= 60: never; the Topeng Tanpa Wajah worn: half as often (with Gentar &gt;= 30:
 * a quarter). A night chance of 0.6 is rolled once per night when the player first walks the streets after dusk.</li>
 * <li>Through the shared daily enemy pressure ({@code Sibuk.enemyRefusal}/{@code enemyTaken}, entry {@code tema.berburu});
 * postponed while the player is in real danger ({@code Sibuk.why}) or in the arena.</li>
 * </ul>
 */
public final class JamBerburu {

	public static final String ACARA = "jam_berburu";
	public static final int DARI = 13000;
	public static final int SAMPAI = 23000;
	public static final double PELUANG_MALAM = 0.6D;
	public static final int UPETI = 5;
	public static final int GENTAR_MENANG = 10;
	public static final long TUNGGU_JAWAB = 20L * 20L;

	/** One hunt on one player. */
	static final class Buru {
		final UUID player;
		final long situs;
		final long mulai;
		final long biaya;
		final List<Mob> pemburu = new ArrayList<>();
		boolean lawan;

		Buru(UUID player, long situs, long mulai, long biaya) {
			this.player = player;
			this.situs = situs;
			this.mulai = mulai;
			this.biaya = biaya;
		}
	}

	private static final Map<UUID, Buru> BURU = new HashMap<>();

	private JamBerburu() {
	}

	static void init() {
		TemaKalender.daftar(IblisData.TEMA, ACARA, DARI, SAMPAI, TemaKalender.selalu(), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				if (dipaksa) {
					paksaSemua(level, situsId);
				}
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				if (tickKe % 100 == 0) {
					periksaKota(level, situsId);
				}
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				for (Buru b : new ArrayList<>(BURU.values())) {
					if (b.situs == situsId && !b.lawan) {
						bubar(level.getServer(), b, false);
					}
				}
			}
		});
		Sibuk.registerEnemy("tema.berburu", p -> BURU.containsKey(p.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register(JamBerburu::mati);
	}

	static void clear() {
		BURU.clear();
	}

	/** How many hunts the player may meet tonight (Pesta Darah: two). */
	static int jatahMalam(long situs) {
		return PestaDarah.berjalan(situs) ? 2 : 1;
	}

	/** The night's chance for this player in this town (0 when spared). */
	static double peluang(ServerPlayer p, long situs) {
		int gentar = IblisMeter.gentar(p, situs);
		if (gentar >= IblisMeter.GENTAR_AUDIENSI) {
			return 0.0D;
		}
		if (IblisData.PENGUASA_YE.equals(IblisData.penguasa(p.level().getServer(), situs)) && IblisData.gelar(p, IblisData.GELAR_FAJAR)) {
			return 0.0D;
		}
		double chance = PELUANG_MALAM;
		if (gentar >= IblisMeter.GENTAR_SEDIKIT) {
			chance *= 0.5D;
		}
		if (p.getItemBySlot(EquipmentSlot.HEAD).is(TemaItems.TOPENG_TANPA_WAJAH)) {
			chance *= 0.5D;
		}
		return chance;
	}

	private static void periksaKota(ServerLevel level, long situsId) {
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situsId);
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.berisi(p.getBlockX(), p.getBlockZ())) {
				periksa(p, kota, false);
			}
		}
	}

	private static void paksaSemua(ServerLevel level, long situsId) {
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situsId);
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.berisi(p.getBlockX(), p.getBlockZ())) {
				periksa(p, kota, true);
			}
		}
	}

	/** One check for a player walking the streets at night: the night's roll, the rules, then the ambush. */
	static void periksa(ServerPlayer p, TemaKota.Kota kota, boolean paksa) {
		if (BURU.containsKey(p.getUUID()) || p.isCreative() || p.isSpectator() || IblisKota.zp(p.level(), p.blockPosition())) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		long hari = IblisKota.hari(p.level());
		CompoundTag pk = IblisData.pemainKota(server, kota.situsId(), p.getUUID());
		if (!paksa) {
			if (IblisData.hariIni(server, kota.situsId(), p.getUUID(), "berburu", hari) >= jatahMalam(kota.situsId())) {
				return;
			}
			if (pk.getLongOr("berburu_lempar", -1L) != hari * 4L + IblisData.hariIni(server, kota.situsId(), p.getUUID(), "berburu", hari)) {
				pk.putLong("berburu_lempar", hari * 4L + IblisData.hariIni(server, kota.situsId(), p.getUUID(), "berburu", hari));
				pk.putBoolean("berburu_kena", p.getRandom().nextDouble() < peluang(p, kota.situsId()));
				IblisData.ubah(server);
			}
			if (!pk.getBooleanOr("berburu_kena", false)) {
				return;
			}
			if (Sibuk.why(p) != null || Sibuk.enemyRefusal(p) != null || ArenaDarah.sedang(p)) {
				return;
			}
		}
		if (mulai(p, kota)) {
			pk.putBoolean("berburu_kena", false);
			IblisData.hitungHariIni(server, kota.situsId(), p.getUUID(), "berburu", hari);
			if (!paksa) {
				Sibuk.enemyTaken(p, "tema.berburu");
			}
		}
	}

	private static boolean mulai(ServerPlayer p, TemaKota.Kota kota) {
		ServerLevel level = p.level();
		int band = IblisKota.band(p);
		int n = 1 + Math.floorMod((int) (IblisKota.hari(level) + p.getId()), 3);
		int tier = Mth.clamp(1 + band / 2, 1, 4);
		int realm = Mth.clamp(Cultivators.get(p).realm().ordinal() + p.getRandom().nextInt(3) - 1, 0, Realm.values().length - 1);
		Buru b = new Buru(p.getUUID(), kota.situsId(), level.getGameTime(), (long) UPETI * (band + 1L));
		for (int i = 0; i < n; i++) {
			BlockPos spot = titik(level, p, i);
			if (spot == null) {
				continue;
			}
			Mob rogue = RogueCultivators.spawn(level, Vec3.atBottomCenterOf(spot), i == 0 ? tier : Math.max(1, tier - 1), realm,
					RogueCultivators.VARIANT_DEMONIC, RogueCultivators.ROLE_QUEST, spot);
			if (rogue != null) {
				rogue.addTag("kultivator.iblis_berburu");
				rogue.setTarget(null);
				rogue.getNavigation().moveTo(p, 0.9D);
				b.pemburu.add(rogue);
			}
		}
		if (b.pemburu.isEmpty()) {
			return false;
		}
		BURU.put(p.getUUID(), b);
		Voice.say(b.pemburu.getFirst(), "tema.iblis.hunt_hour", null);
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.berburu.judul").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.kota_tema.iblis.berburu.sub", NilaiBatu.text(b.biaya)).withStyle(ChatFormatting.RED), 5, 70, 15);
		p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.berburu.pilihan",
				Component.translatable("kultivator.kota_tema.iblis.berburu.bayar", NilaiBatu.text(b.biaya)).withStyle(style -> style.withColor(ChatFormatting.GOLD)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/kultivasi tema_iblis bayar"))),
				Component.translatable("kultivator.kota_tema.iblis.berburu.lawan").withStyle(style -> style.withColor(ChatFormatting.RED)
						.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/kultivasi tema_iblis lawan")))));
		return true;
	}

	private static @Nullable BlockPos titik(ServerLevel level, ServerPlayer p, int i) {
		double angle = Math.toRadians(p.getYRot() + (i - 1) * 40.0D);
		int x = Mth.floor(p.getX() - Math.sin(angle) * 9.0D);
		int z = Mth.floor(p.getZ() + Math.cos(angle) * 9.0D);
		BlockPos g = TempatApi.groundSpot(level, x, z, p.getBlockY());
		return g != null && Math.abs(g.getY() - p.getBlockY()) <= 6 && !IblisKota.zp(level, g) ? g : null;
	}

	/** The player's answer: {@code bayar} (pay the road toll) or {@code lawan} (fight). */
	static void jawab(ServerPlayer p, boolean bayar) {
		Buru b = BURU.get(p.getUUID());
		if (b == null || b.lawan) {
			return;
		}
		if (bayar) {
			if (!NilaiBatu.pay(p, b.biaya)) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(b.biaya)).withStyle(ChatFormatting.RED));
				lawan(p, b);
				return;
			}
			IblisData.tambahKas(p.level().getServer(), b.situs, b.biaya / 2L);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.berburu.dibayar", NilaiBatu.text(b.biaya)).withStyle(ChatFormatting.GRAY));
			bubar(p.level().getServer(), b, false);
			CeritaApi.peristiwa(p, IblisCerita.BERBURU, "bayar");
			return;
		}
		lawan(p, b);
	}

	private static void lawan(ServerPlayer p, Buru b) {
		b.lawan = true;
		for (Mob m : b.pemburu) {
			m.setTarget(p);
		}
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.berburu.darah").withStyle(ChatFormatting.DARK_RED));
	}

	/** Every 20 ticks: unanswered tolls turn to blood after 20 s; a hunt whose player left or whose foes fell ends. */
	static void tick(MinecraftServer server) {
		if (BURU.isEmpty()) {
			return;
		}
		for (Buru b : new ArrayList<>(BURU.values())) {
			b.pemburu.removeIf(m -> m.isRemoved() || !m.isAlive());
			ServerPlayer p = server.getPlayerList().getPlayer(b.player);
			if (p == null) {
				bubar(server, b, false);
				continue;
			}
			if (b.pemburu.isEmpty()) {
				BURU.remove(b.player);
				if (b.lawan) {
					menang(p, b);
				}
				continue;
			}
			long now = p.level().getGameTime();
			Mob first = b.pemburu.getFirst();
			if (!b.lawan) {
				for (Mob m : b.pemburu) {
					m.setTarget(null);
					if (m.distanceToSqr(p) > 9.0D) {
						m.getNavigation().moveTo(p, 0.9D);
					}
				}
				if (now - b.mulai > TUNGGU_JAWAB || IblisKota.zp(p.level(), p.blockPosition())) {
					if (IblisKota.zp(p.level(), p.blockPosition())) {
						bubar(server, b, false);
						Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.berburu.zp").withStyle(ChatFormatting.GRAY));
					} else {
						lawan(p, b);
					}
				}
			} else if (first.distanceToSqr(p) > 64.0D * 64.0D) {
				bubar(server, b, false);
			}
		}
	}

	private static void menang(ServerPlayer p, Buru b) {
		IblisMeter.tambahGentar(p, b.situs, GENTAR_MENANG, "berburu");
		Advancements.grant(p, "tempat/tema_iblis_berburu");
		CeritaApi.peristiwa(p, IblisCerita.BERBURU, "menang");
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.berburu.menang").withStyle(ChatFormatting.DARK_RED));
	}

	private static void bubar(MinecraftServer server, Buru b, boolean silent) {
		BURU.remove(b.player);
		for (Mob m : b.pemburu) {
			if (!m.isRemoved()) {
				m.discard();
			}
		}
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (!entity.entityTags().contains("kultivator.iblis_berburu")) {
			return;
		}
		for (Buru b : BURU.values()) {
			if (b.pemburu.remove(entity) && !b.lawan && source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(b.player)) {
				// striking first is an answer too
				b.lawan = true;
			}
		}
	}

	/** True while a hunt bars the player's way. */
	public static boolean sedang(ServerPlayer p) {
		return BURU.containsKey(p.getUUID());
	}

	/** {@code uji tema iblis berburu}: an ambush on the caller now (no roll, no pressure). */
	static boolean uji(ServerPlayer p) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return false;
		}
		periksa(p, kota, true);
		return BURU.containsKey(p.getUUID());
	}
}
