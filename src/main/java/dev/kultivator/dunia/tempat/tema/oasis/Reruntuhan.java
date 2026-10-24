package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaAnchors;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Reruntuhan Gurun satellite (DESIGN 38.T.4.6, 38.T.4.7 stage 4; the jigsaw ruin and its NBT are 38TK inti's
 * {@code gen_tempat_tema_satelit.py}). The oasis serves the ruin's anchor kinds through {@code TemaAnchors.daftar}:
 * <pre>
 * kota_tema/sarang_kalajengking   the third hall: Raja Kalajengking's nest (boss once per player per ruin; the four
 *                                 water-drinking rune pillars appear for a player in chain stage "formasi")
 * kota_tema/pintu_pasir           a sand door, data ruang 1-3 (1 opens by hand; 2 and 3 need a Lempeng Peta)
 * kota_tema/temuan_reruntuhan     a find in the sand, data jenis koin|mural|peta (once per player)
 * kota_tema/mural_gua             a painted wall, data kisah 0-11 (read like the cliff murals, {@link Mural})
 * </pre>
 * A ruin is keyed by its structure start ({@link DuniaAnchorEntity#origin()}). Its tier follows the distance ring of
 * {@code TemaAnchors.tingkatReruntuhan} (1 inside 3000, 2 inside 6000, 3 beyond), the boss's band 1-4 the 38.C.9 ring.
 * Nothing here changes a block; every body is {@code noSave} and goes when the anchor unloads.
 */
public final class Reruntuhan implements AnchorHandler {

	public static final String SARANG = "sarang_kalajengking";
	public static final String PINTU = "pintu_pasir";
	public static final String TEMUAN = "temuan_reruntuhan";
	public static final String MURAL_GUA = "mural_gua";
	public static final double JARAK_BOS = 20.0D;
	public static final ResourceKey<LootTable> HADIAH = ResourceKey.create(Registries.LOOT_TABLE, Kultivator.id("tema/oasis/raja_kalajengking"));
	/** The chain whose stage "formasi" brings the pillars. */
	public static final String QUEST = "tema_oasis_mata_air";
	public static final String TAHAP_FORMASI = "formasi";

	static final Reruntuhan HANDLER = new Reruntuhan();

	private final Map<UUID, Entity> buatan = new HashMap<>();
	private final Map<UUID, List<PilarRuneEntity>> pilar = new HashMap<>();
	/** Loaded mural walls (for {@link #muralDi}). */
	private final List<DuniaAnchorEntity> mural = new ArrayList<>();

	static void init() {
		TemaAnchors.daftar(SARANG, HANDLER);
		TemaAnchors.daftar(PINTU, HANDLER);
		TemaAnchors.daftar(TEMUAN, HANDLER);
		TemaAnchors.daftar(MURAL_GUA, HANDLER);
	}

	static void reset() {
		HANDLER.buatan.clear();
		HANDLER.pilar.clear();
		HANDLER.mural.clear();
	}

	@Override
	public int radius() {
		return 32;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (MURAL_GUA.equals(anchor.what()) && !mural.contains(anchor)) {
			mural.add(anchor);
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!OasisKota.aktif() || !TemaConfig.oasis().reruntuhan) {
			return;
		}
		switch (anchor.what()) {
			case SARANG -> sarang(level, anchor, within);
			case PINTU -> pintu(level, anchor);
			case TEMUAN -> temuan(level, anchor, within);
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		mural.remove(anchor);
		Entity made = buatan.remove(anchor.getUUID());
		if (made != null) {
			TemaMekanisme.lepas(made);
		}
		List<PilarRuneEntity> list = pilar.remove(anchor.getUUID());
		if (list != null) {
			for (PilarRuneEntity p : list) {
				TemaMekanisme.lepas(p);
			}
		}
	}

	private @Nullable Entity hidup(DuniaAnchorEntity anchor) {
		Entity e = buatan.get(anchor.getUUID());
		return e == null || e.isRemoved() ? null : e;
	}

	/** The key of the ruin an anchor belongs to. */
	static long kunci(DuniaAnchorEntity anchor) {
		BlockPos o = anchor.origin();
		return (o != null ? o : anchor.blockPosition()).asLong();
	}

	/** Per-player record of one ruin ({@code bagian("oasis")["p_<uuid>"]["r_<key>"]}). */
	private static CompoundTag catatan(MinecraftServer server, UUID player, long ruin) {
		CompoundTag me = OasisKota.pemain(server, player);
		String key = "r_" + ruin;
		CompoundTag tag = me.getCompoundOrEmpty(key);
		if (!me.contains(key)) {
			me.put(key, tag);
		}
		return tag;
	}

	/** The boss band 1-4 by the 38.C.9 ring. */
	static int tingkatBos(BlockPos pos) {
		return OasisKota.cincin(pos);
	}

	// ================================================================== the nest

	private void sarang(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		MinecraftServer server = level.getServer();
		long ruin = kunci(anchor);
		ServerPlayer pemburu = null;
		ServerPlayer formasi = null;
		for (ServerPlayer p : within) {
			if (p.isSpectator() || p.isCreative()) {
				continue;
			}
			boolean dekat = p.distanceToSqr(anchor) <= JARAK_BOS * JARAK_BOS;
			if (dekat && !catatan(server, p.getUUID(), ruin).getBooleanOr("bos", false)) {
				pemburu = pemburu == null ? p : pemburu;
			}
			if (QuestTracker.aktif(p, QUEST) && TAHAP_FORMASI.equals(QuestTracker.simpul(p, QUEST))) {
				formasi = p;
			}
		}
		if (pemburu != null && hidup(anchor) == null) {
			Vec3 at = anchor.position();
			KalajengkingRajaEntity raja = TemaMekanisme.muncul(level, TemaEntities.KALAJENGKING_RAJA, at, anchor.getYRot(), null, anchor.blockPosition());
			if (raja != null) {
				raja.setup(tingkatBos(anchor.blockPosition()), ruin, formasi != null);
				buatan.put(anchor.getUUID(), raja);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), at.x, at.y + 0.5D, at.z, 60,
						1.6D, 0.4D, 1.6D, 0.15D);
				level.playSound(null, anchor.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.6F, 0.5F);
				Voice.say(raja, "tema.oasis.scorpion", null);
				for (ServerPlayer p : within) {
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.raja.muncul").withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
		List<PilarRuneEntity> list = pilar.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		list.removeIf(Entity::isRemoved);
		if (formasi != null && list.isEmpty()) {
			CompoundTag mine = catatan(server, formasi.getUUID(), ruin);
			for (int i = 0; i < 4; i++) {
				double a = Math.toRadians(anchor.getYRot()) + Math.PI / 4.0D + i * Math.PI / 2.0D;
				Vec3 at = anchor.position().add(Math.cos(a) * 8.0D, 0.0D, Math.sin(a) * 8.0D);
				PilarRuneEntity p = TemaMekanisme.muncul(level, TemaEntities.PILAR_RUNE, at, 0.0F, null, anchor.blockPosition());
				if (p == null) {
					break;
				}
				p.pasang(ruin, i, mine.getIntOr("pilar_" + i, PilarRuneEntity.KEADAAN_AKTIF));
				list.add(p);
			}
		}
	}

	/** Boss death: rewards once per player per ruin to everyone within 32 blocks. */
	static void bosMati(ServerLevel level, KalajengkingRajaEntity raja) {
		MinecraftServer server = level.getServer();
		long ruin = raja.ruin();
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, raja.getBoundingBox().inflate(32.0D))) {
			CompoundTag rec = catatan(server, p.getUUID(), ruin);
			if (rec.getBooleanOr("bos", false)) {
				continue;
			}
			rec.putBoolean("bos", true);
			OasisKota.pemain(server, p.getUUID()).putBoolean("bos_kalajengking", true);
			OasisKota.ubah(server);
			List<ItemStack> items = new ArrayList<>();
			items.add(new ItemStack(TemaItems.EKOR_RACUN_KALAJENGKING, 1 + raja.tingkat() / 2));
			items.add(new ItemStack(DagangItems.GIOK_BARAT, 1));
			try {
				LootTable table = server.reloadableRegistries().getLootTable(HADIAH);
				LootParams params = new LootParams.Builder(level)
						.withParameter(LootContextParams.ORIGIN, raja.position())
						.withOptionalParameter(LootContextParams.THIS_ENTITY, p)
						.create(LootContextParamSets.CHEST);
				items.addAll(table.getRandomItems(params));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Scorpion king loot failed", exception);
			}
			RRewards.giveAll(p, items, "kultivator.kota_tema.oasis.raja.hadiah");
			QuestTracker.peristiwa(p, "oasis_bos", "kalajengking");
			dev.kultivator.util.Advancements.grant(p, "tempat/tema_oasis_kalajengking");
		}
		CeritaApi.kabar(level, null, "tema_oasis_raja_tumbang");
	}

	// ================================================================== pillars

	/** A pillar changed (broken or sealed): stores it for the quest player and, all four done, ends the formation. */
	static void pilarBerubah(ServerPlayer player, PilarRuneEntity p) {
		MinecraftServer server = player.level().getServer();
		CompoundTag rec = catatan(server, player.getUUID(), p.ruin());
		rec.putInt("pilar_" + p.nomor(), p.keadaan());
		OasisKota.ubah(server);
		int pecah = 0;
		int segel = 0;
		for (int i = 0; i < 4; i++) {
			int k = rec.getIntOr("pilar_" + i, PilarRuneEntity.KEADAAN_AKTIF);
			if (k == PilarRuneEntity.KEADAAN_PECAH) {
				pecah++;
			} else if (k == PilarRuneEntity.KEADAAN_TERSEGEL) {
				segel++;
			}
		}
		if (pecah + segel < 4) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.pilar.sisa", 4 - pecah - segel).withStyle(ChatFormatting.AQUA));
			return;
		}
		boolean disegel = segel >= pecah;
		CeritaApi.setBendera(player, disegel ? "oasis.formasi_segel" : "oasis.formasi_pecah", true);
		QuestTracker.peristiwa(player, "oasis_formasi", disegel ? "segel" : "pecah");
		Msg.chat(player, Component.translatable(disegel ? "kultivator.kota_tema.oasis.pilar.tersegel" : "kultivator.kota_tema.oasis.pilar.pecah")
				.withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, disegel ? 1.4F : 0.7F);
	}

	// ================================================================== doors and finds

	private void pintu(ServerLevel level, DuniaAnchorEntity anchor) {
		if (hidup(anchor) != null) {
			return;
		}
		PintuPasirEntity e = TemaMekanisme.muncul(level, TemaEntities.PINTU_PASIR, anchor.position(), anchor.getYRot(), null, anchor.blockPosition());
		if (e != null) {
			e.setRuang(Math.max(1, Math.min(3, OasisAnchors.angka(anchor, "ruang", 1))));
			buatan.put(anchor.getUUID(), e);
		}
	}

	private void temuan(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (hidup(anchor) != null) {
			return;
		}
		MinecraftServer server = level.getServer();
		String id = "r:" + kunci(anchor) + ":" + anchor.blockPosition().asLong();
		boolean adaYangBelum = false;
		for (ServerPlayer p : within) {
			if (!OasisKota.pemain(server, p.getUUID()).getBooleanOr("t_" + id, false)) {
				adaYangBelum = true;
			}
		}
		if (!adaYangBelum) {
			return;
		}
		TemuanPasirEntity e = TemaMekanisme.muncul(level, TemaEntities.TEMUAN_PASIR, anchor.position().add(0.0D, -0.15D, 0.0D), anchor.getYRot(), null,
				anchor.blockPosition());
		if (e != null) {
			String jenis = anchor.data().getStringOr("jenis", "koin");
			e.setVarian(switch (jenis) {
				case "peta" -> TemuanPasirEntity.VARIAN_PETA;
				case "mural" -> TemuanPasirEntity.VARIAN_MURAL;
				default -> TemuanPasirEntity.VARIAN_KOIN;
			});
			e.setDataId(id);
			buatan.put(anchor.getUUID(), e);
		}
	}

	/** A player picks up a ruin find (once per player). */
	static void ambil(ServerPlayer player, TemuanPasirEntity e) {
		MinecraftServer server = player.level().getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		String key = "t_" + e.dataId();
		if (me.getBooleanOr(key, false) || e.keadaan() != TemuanPasirEntity.KEADAAN_TERTANAM) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.temuan.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		me.putBoolean(key, true);
		OasisKota.ubah(server);
		ItemStack hadiah = switch (e.varian()) {
			case TemuanPasirEntity.VARIAN_PETA -> new ItemStack(TemaItems.LEMPENG_PETA);
			case TemuanPasirEntity.VARIAN_MURAL -> new ItemStack(TemaItems.PECAHAN_MURAL, 2);
			default -> new ItemStack(TemaItems.KOIN_KUNO, 4 + player.getRandom().nextInt(5));
		};
		Component nama = hadiah.getHoverName();
		OasisKota.beri(player, hadiah);
		e.setKeadaan(TemuanPasirEntity.KEADAAN_DIAMBIL);
		e.hilangSetelah(20);
		player.level().playSound(null, e.blockPosition(), SoundEvents.SUSPICIOUS_SAND_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.temuan.dapat", nama).withStyle(ChatFormatting.GOLD));
		QuestTracker.peristiwa(player, "oasis_temuan", Integer.toString(e.varian()));
		// guild mission "reruntuhan" (TemaMisi, 38.T.11): a relic of the buried city brought out of the ruin
		if (EkonomiApi.misiAktif(player, "reruntuhan")) {
			EkonomiApi.misiSelesai(player, "reruntuhan");
		}
	}

	// ================================================================== murals of the ruin

	/** The cliff-mural number (1-12) of a ruin wall within 4 blocks of {@code pos}, or 0. */
	static int muralDi(ServerLevel level, BlockPos pos) {
		for (DuniaAnchorEntity a : HANDLER.mural) {
			if (a.isRemoved() || a.level() != level) {
				continue;
			}
			if (a.blockPosition().distSqr(pos) <= 16.0D) {
				return Math.max(0, Math.min(11, OasisAnchors.angka(a, "kisah", 0))) + 1;
			}
		}
		return 0;
	}

	private Reruntuhan() {
	}
}
