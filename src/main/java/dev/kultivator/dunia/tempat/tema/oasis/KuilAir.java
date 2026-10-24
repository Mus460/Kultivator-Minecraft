package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Kuil Air and its water (DESIGN 38.T.4.5, 38.T.4.7): Restu Mata Air, Air Suci, the meditation site "Mata Gurun",
 * the Batu Ukur Air stela and the shaduf wells with their rationing queue.
 * <ul>
 * <li><b>Restu Mata Air</b> (3 NB, at most once per world day, from Pendeta Shui Lian or any water priestess): Regeneration
 * I for 60 s and 30 minutes (36 000 ticks of game time) of immunity to "Tertiup Pasir" ({@link BadaiPasir}).</li>
 * <li><b>Air Suci</b> ({@code kultivator:tema/air_suci}, 2 NB): cooling-pill ingredient and the seal of the chain's stage 4
 * branch and stage 5 persuasion.</li>
 * <li><b>Mata Gurun</b>: meditating within the holy pool's radius ({@code r} of the {@code tema_oasis/kuil_air} anchor) adds
 * the extra site x1.25 (Dao of Life), inside {@code SpiritSites.CAP}. One cheap provider (a few anchors).</li>
 * <li><b>Batu Ukur Air</b>: {@code nilai} 0 while the lake is two marks down (the chain's premise), 1 once a player
 * finished the chain in that town; {@link #naik} plays the 4 s rise of the ceremony.</li>
 * <li><b>Wells</b>: the shaduf beam works in the morning and evening water hours; while the lake is low, in the morning a
 * queue of four townsfolk with jugs forms at a well marked {@code antre} (the living scene of chain stage 1).</li>
 * </ul>
 */
public final class KuilAir {

	public static final long HARGA_RESTU = 3L;
	public static final long HARGA_AIR_SUCI = 2L;
	/** Immunity to Tertiup Pasir after a blessing (30 minutes of game time). */
	public static final long RESTU_TICK = 36_000L;
	private static final int REGEN_TICK = 60 * 20;
	public static final double PENGALI_MATA_GURUN = 1.25D;
	private static final int ANTRE = 4;

	private static final Map<UUID, List<KultivatorNpc>> ANTREAN = new HashMap<>();

	static void init() {
		SpiritSites.addSiteProvider(KuilAir::situs);
	}

	static void reset() {
		ANTREAN.clear();
	}

	// ================================================================== blessing and water

	/** True while the player's Restu Mata Air protects from the storm. */
	public static boolean restuAktif(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return OasisKota.pemain(server, player.getUUID()).getLongOr("restu_sampai", 0L) > player.level().getGameTime();
	}

	/** Buys the blessing (dialog effect {@code oasis_restu}); false when already blessed today or too poor. */
	public static boolean restu(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		long hari = OasisKota.hari(level);
		if (me.getLongOr("restu_hari", Long.MIN_VALUE) == hari) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.restu.sudah").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!EkonomiApi.bayar(player, HARGA_RESTU, "tema.oasis.restu")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(HARGA_RESTU)).withStyle(ChatFormatting.RED));
			return false;
		}
		me.putLong("restu_hari", hari);
		me.putLong("restu_sampai", level.getGameTime() + RESTU_TICK);
		OasisKota.ubah(server);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICK, 0));
		level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.8F, 1.3F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.oasis.restu.dapat").withStyle(ChatFormatting.AQUA));
		QuestTracker.peristiwa(player, "oasis_restu", "dapat");
		return true;
	}

	/** Buys {@code n} flasks of Air Suci (dialog effect {@code oasis_air_suci N}). */
	public static boolean beliAirSuci(ServerPlayer player, int n) {
		int count = Math.max(1, Math.min(8, n));
		long harga = HARGA_AIR_SUCI * count;
		if (!EkonomiApi.bayar(player, harga, "tema.oasis.air_suci")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(harga)).withStyle(ChatFormatting.RED));
			return false;
		}
		ItemStack stack = new ItemStack(TemaItems.AIR_SUCI, count);
		OasisKota.beri(player, stack);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.air_suci.beli", count, NilaiBatu.text(harga))
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	// ================================================================== Mata Gurun

	private static SpiritSites.@Nullable ExtraSite situs(ServerPlayer player) {
		if (!OasisKota.aktif() || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		for (OasisKota.Hidup kota : OasisKota.semua()) {
			if (!kota.dimensi.equals(level.dimension())) {
				continue;
			}
			for (DuniaAnchorEntity a : kota.jangkar(OasisAnchors.KUIL_AIR)) {
				int r = Math.max(3, OasisAnchors.angka(a, "r", 6)) + 2;
				if (a.position().distanceToSqr(player.position()) <= (double) r * r) {
					return new SpiritSites.ExtraSite("mata_gurun", PENGALI_MATA_GURUN, Dao.LIFE,
							Component.translatable("kultivator.kota_tema.oasis.situs.mata_gurun"));
				}
			}
		}
		return null;
	}

	// ================================================================== Batu Ukur Air

	/** True once the lake of {@code situs} was restored (chain stage 6 in that town). */
	public static boolean pulih(MinecraftServer server, long situs) {
		return OasisKota.kota(server, situs).getBooleanOr("air_pulih", false);
	}

	static void batuDibuat(ServerLevel level, DuniaAnchorEntity anchor, BatuUkurAirEntity batu) {
		batu.setNilai(pulih(level.getServer(), batu.situsId() != 0L ? batu.situsId() : OasisAnchors.situsOf(level, anchor)) ? 1.0F : 0.0F);
	}

	/** The ceremony of chain stage 6: the lake mark rises four marks in 4 s and stays restored. */
	public static void naik(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		OasisKota.Hidup kota = OasisKota.cari(server, situs);
		long key = kota != null ? kota.situs : situs;
		OasisKota.kota(server, key).putBoolean("air_pulih", true);
		OasisKota.ubah(server);
		if (kota == null) {
			return;
		}
		for (DuniaAnchorEntity a : kota.jangkar(OasisAnchors.BATU_UKUR)) {
			if (OasisAnchors.HANDLER.buatan(a) instanceof BatuUkurAirEntity batu) {
				batu.naik();
			}
		}
	}

	// ================================================================== wells

	static void timbaDekat(ServerLevel level, DuniaAnchorEntity anchor, @Nullable TimbaSumurEntity timba, List<ServerPlayer> within) {
		int jam = OasisKota.jamHari(level);
		boolean jamAir = jam >= 5 && jam <= 8 || jam >= 16 && jam <= 19;
		long situs = OasisAnchors.situsOf(level, anchor);
		boolean rendah = situs != 0L && !pulih(level.getServer(), situs);
		boolean antre = rendah && OasisAnchors.angka(anchor, "antre", 0) == 1 && jam >= 6 && jam <= 10 && !BadaiPasir.aktif(situs);
		if (timba != null) {
			timba.setKeadaan(jamAir || antre ? TimbaSumurEntity.KEADAAN_TIMBA : TimbaSumurEntity.KEADAAN_DIAM);
		}
		List<KultivatorNpc> queue = ANTREAN.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		queue.removeIf(n -> n.isRemoved() || !n.isAlive());
		if (!antre) {
			for (KultivatorNpc n : queue) {
				n.vanish();
			}
			queue.clear();
			return;
		}
		if (queue.size() >= ANTRE) {
			return;
		}
		// the line runs away from the well along the anchor's facing (+z of the template = its yaw)
		float yaw = anchor.getYRot();
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		for (int i = queue.size(); i < ANTRE; i++) {
			double d = 2.0D + i * 1.4D;
			BlockPos at = BlockPos.containing(anchor.getX() + fx * d, anchor.getY(), anchor.getZ() + fz * d);
			NpcRole role = i == 0 ? NpcRole.WARGA : i == 3 ? NpcRole.ANAK : (i % 2 == 0 ? NpcRole.TETUA_DESA : NpcRole.WARGA);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, role, at, yaw + 180.0F);
			if (npc == null) {
				break;
			}
			npc.memory().putBoolean("hold", true);
			npc.setLonely(40.0D, 400);
			if (i == 0) {
				SosokApi.gerak(npc, GerakId.TIMBA_SUMUR, 0, 0, PropId.KENDI, 0, 0);
			} else {
				SosokApi.gerak(npc, GerakId.SIKAP_TANGAN_DEPAN, 0, 0, PropId.KENDI, 0, 0);
			}
			queue.add(npc);
		}
		if (!queue.isEmpty() && level.getRandom().nextInt(6) == 0) {
			KultivatorNpc speaker = queue.get(level.getRandom().nextInt(queue.size()));
			speaker.say(Component.translatable("kultivator.kota_tema.oasis.antre." + level.getRandom().nextInt(4)), 70);
		}
	}

	/** The nearest live water priestess body within {@code r} of {@code at}, or null. */
	static @Nullable KultivatorNpc pendeta(ServerLevel level, Vec3 at, double r) {
		KultivatorNpc best = null;
		double bestD = r * r;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new net.minecraft.world.phys.AABB(BlockPos.containing(at)).inflate(r),
				n -> n.role() == NpcRole.PENDETA_AIR)) {
			double d = npc.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = npc;
			}
		}
		return best;
	}

	private KuilAir() {
	}
}
