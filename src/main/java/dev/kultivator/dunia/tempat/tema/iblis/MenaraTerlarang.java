package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Menara Kitab Terlarang (DESIGN 38.T.7.7): the nine-floor tower of the {@code menara_terlarang} district and its keeper
 * Si Buta Mo Wen ({@code tema_kitab_terlarang} screen, anchor {@code kota_tema/kitab_terlarang} on floor 1).
 * <ul>
 * <li>Floor n opens with Gentar &gt;= 10 x (n - 1) in this town. Each floor shelves its own wares: the four Gulungan
 * Terlarang (floors 1, 2, 4, 6) and the forbidden jade slips of blood, soul and shadow techniques (floors 3, 5, 7, 8;
 * {@code JadeSlips.stack}, price 3 x the slip's pavilion value, band &gt;= 3, the custom-data stain
 * {@value #NODA_KEY}: Infami +8 when it is learned). A player climbing above the floors open to him feels the books
 * push back (Slowness II and a warning while he stays there); no block or door ever changes.</li>
 * <li>Every scroll bought adds Infami +2 ({@link GulunganTerlarangItem#INFAMI_BELI}).</li>
 * <li>Floor 9 is sealed by {@link SegelPintuEntity} over the last stair (anchor {@code kota_tema/segel_menara}). It
 * opens during the Pesta Darah, or for a player of the Darah route (title "Tamu Darah"); there lies the one Kitab Darah
 * Senja of the town (stock 1, Infami +20).</li>
 * </ul>
 */
public final class MenaraTerlarang {

	public static final String DISTRIK = "menara_terlarang";
	/** Tower interior box on the district canvas (tools/gen_tempat_tema_iblis.py MENARA_X0/Z0, 15 x 15). */
	static final int X0 = 16;
	static final int Z0 = 42;
	static final int X1 = 28;
	static final int Z1 = 54;
	/** Template y of floor 1 and the storey height (MENARA_F1 = LY + 2, MENARA_SH = 5). */
	static final int F1 = IblisKota.LY + 2;
	static final int SH = 5;
	public static final int LANTAI = 9;
	public static final int INFAMI_GIOK = 8;
	public static final int INFAMI_KITAB = 20;
	public static final long HARGA_KITAB = 1500L;
	/** Custom-data key of a forbidden slip (its learning costs Infami). */
	public static final String NODA_KEY = "kultivator_noda_terlarang";
	public static final String KITAB_KEY = "kultivator_kitab_darah_senja";
	/** The technique the Kitab Darah Senja holds, with fallbacks when a spec may not be put on a slip. */
	static final String[] KITAB_SPEC = {"blood_death_world", "bloodmoon_immortal_slaying_sword", "blood_clones"};

	/** One ware of a floor. */
	private record Rak(String id, int lantai, @Nullable GulunganTerlarangItem.Jenis gulungan, @Nullable String spec) {
	}

	private static final List<Rak> RAK = List.of(
			new Rak("bakar_darah", 1, GulunganTerlarangItem.Jenis.BAKAR_DARAH, null),
			new Rak("langkah_hantu", 2, GulunganTerlarangItem.Jenis.LANGKAH_HANTU, null),
			new Rak("ghost_shadow_track", 3, null, "ghost_shadow_track"),
			new Rak("bloodshadow_evasion", 3, null, "bloodshadow_evasion"),
			new Rak("rantai_jiwa", 4, GulunganTerlarangItem.Jenis.RANTAI_JIWA, null),
			new Rak("wraith_butterflies", 5, null, "wraith_butterflies"),
			new Rak("blood_drop_stance", 5, null, "blood_drop_stance"),
			new Rak("topeng_iblis_hati", 6, GulunganTerlarangItem.Jenis.TOPENG_IBLIS_HATI, null),
			new Rak("purple_demon_eye", 7, null, "purple_demon_eye"),
			new Rak("soul_falling_formation", 7, null, "soul_falling_formation"),
			new Rak("finger_of_death", 8, null, "finger_of_death"),
			new Rak("stygian_tiger_seal", 8, null, "stygian_tiger_seal"),
			new Rak("blood_clones", 8, null, "blood_clones"));

	private static final Map<Long, SegelPintuEntity> SEGEL = new HashMap<>();
	/** Town id -> last game time a permitted player stood by the seal. */
	private static final Map<Long, Long> SEGEL_PERLU = new HashMap<>();
	/** Player -> forbidden slips carried before a use (Infami when one is gone next tick = learned). */
	private static final Map<UUID, Integer> NODA_SEBELUM = new HashMap<>();
	/** Player -> last warning time on a floor above his Gentar. */
	private static final Map<UUID, Long> TOLAK = new HashMap<>();

	private MenaraTerlarang() {
	}

	static void init() {
		TemaLayar.daftar(TemaLayar.KitabTerlarang.LAYAR, MenaraTerlarang::bangun, MenaraTerlarang::aksi);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer p) {
				ItemStack stack = player.getItemInHand(hand);
				if (noda(stack)) {
					NODA_SEBELUM.put(p.getUUID(), hitungNoda(p));
				}
			}
			return InteractionResult.PASS;
		});
	}

	static void clear() {
		SEGEL.clear();
		SEGEL_PERLU.clear();
		NODA_SEBELUM.clear();
		TOLAK.clear();
	}

	// ================================================================== floors

	/** The Gentar floor {@code n} needs (10 x (n - 1)); floor 9 is the seal. */
	public static int gentarMin(int lantai) {
		return 10 * (lantai - 1);
	}

	/** True when floor {@code n} is open to the player in this town (floor 9: seal rules; Mo Wen's three books count +20). */
	public static boolean terbuka(ServerPlayer p, TemaKota.Kota kota, int lantai) {
		if (lantai >= LANTAI) {
			return segelUntuk(p, kota);
		}
		int bonus = CeritaApi.bendera(p, "iblis_akses_lantai") ? 20 : 0;
		return IblisMeter.gentar(p, kota.situsId()) + bonus >= gentarMin(lantai);
	}

	/** True when the seal of floor 9 gives way for this player (Pesta Darah, or the Darah route's title). */
	public static boolean segelUntuk(ServerPlayer p, TemaKota.Kota kota) {
		return PestaDarah.berjalan(kota.situsId()) || IblisData.gelar(p, IblisData.GELAR_TAMU)
				|| IblisData.kota(p.level().getServer(), kota.situsId()).getBooleanOr("segel_terbuka", false) && "darah".equals(IblisData.rute(p));
	}

	/** The tower floor (1-9) the player stands on, 0 when outside the tower. */
	public static int lantaiDi(ServerPlayer p, TemaKota.Kota kota) {
		Vec3 c = IblisKota.kanvas(kota, DISTRIK, p.position());
		if (c == null || c.x < X0 || c.x >= X1 + 1 || c.z < Z0 || c.z >= Z1 + 1 || c.y < F1) {
			return 0;
		}
		int n = (int) Math.floor((c.y - F1) / SH) + 1;
		return n >= 1 && n <= LANTAI ? n : 0;
	}

	/** Every 40 ticks for a player in a Kota Iblis: the books push back on a floor above his Gentar; stained slips learned. */
	static void pindai(ServerPlayer p, TemaKota.Kota kota) {
		int n = lantaiDi(p, kota);
		if (n >= 2 && n < LANTAI && !terbuka(p, kota, n) && !p.isCreative()) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false, true));
			long now = p.level().getGameTime();
			if (now - TOLAK.getOrDefault(p.getUUID(), 0L) > 200L) {
				TOLAK.put(p.getUUID(), now);
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.menara.tolak", n, gentarMin(n)).withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	/** Every tick: a stained slip that left the inventory after a use was learned (Infami +8 once per slip). */
	static void tickNoda(MinecraftServer server) {
		if (NODA_SEBELUM.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Integer> e : new ArrayList<>(NODA_SEBELUM.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			NODA_SEBELUM.remove(e.getKey());
			if (p == null) {
				continue;
			}
			int now = hitungNoda(p);
			if (now < e.getValue()) {
				IblisMeter.tambahInfami(p, INFAMI_GIOK * (e.getValue() - now), "giok_terlarang");
				CeritaApi.peristiwa(p, IblisCerita.KITAB, "giok");
			}
		}
	}

	private static int hitungNoda(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (noda(s)) {
				n += s.getCount();
			}
		}
		return n;
	}

	public static boolean noda(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(NODA_KEY, false);
	}

	// ================================================================== the seal of floor 9

	/** Anchor {@code kota_tema/kitab_terlarang}: Mo Wen reads at his desk (his body is the {@code iblis_tokoh} anchor's). */
	static void nearbyKitab(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(anchor) < 5.0D * 5.0D && (level.getGameTime() + p.getId()) % 600L == 0L) {
				for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, anchor.getBoundingBox().inflate(8.0D),
						n -> TokohIblis.adalah(n, TokohIblis.MO_WEN))) {
					Voice.say(n, "tema.iblis.forbidden", null);
					break;
				}
			}
		}
	}

	/** Anchor {@code kota_tema/segel_menara}: the seal body; it cracks open for a permitted player within 6 blocks. */
	static void nearbySegel(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		SegelPintuEntity seal = SEGEL.get(kota.situsId());
		if (seal == null || seal.isRemoved()) {
			Vec3 at = anchor.position();
			seal = TemaMekanisme.muncul(level, TemaEntities.SEGEL_PINTU, at, anchor.getYRot(), kota, anchor.blockPosition());
			if (seal == null) {
				return;
			}
			seal.setVarian(SegelPintuEntity.VARIAN_BIASA);
			seal.setKeadaan(SegelPintuEntity.KEADAAN_UTUH);
			SEGEL.put(kota.situsId(), IblisAnchors.simpan(anchor, seal));
		}
		long now = level.getGameTime();
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(seal) < 6.0D * 6.0D && segelUntuk(p, kota)) {
				SEGEL_PERLU.put(kota.situsId(), now);
				if (seal.keadaan() == SegelPintuEntity.KEADAAN_UTUH) {
					seal.setKeadaan(SegelPintuEntity.KEADAAN_RETAK);
					level.playSound(null, seal.getX(), seal.getY(), seal.getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.segel.retak").withStyle(ChatFormatting.DARK_RED));
				}
			} else if (p.distanceToSqr(seal) < 3.0D * 3.0D && seal.keadaan() == SegelPintuEntity.KEADAAN_UTUH && now % 40L == 0L) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.segel.tolak").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	/** Every 5 ticks: the seal's crack (20 ticks) and its closing again 5 s after nobody permitted stands by it. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		SEGEL.values().removeIf(s -> s.isRemoved());
		for (Map.Entry<Long, SegelPintuEntity> e : SEGEL.entrySet()) {
			SegelPintuEntity seal = e.getValue();
			long since = IblisKota.sejak(seal, now);
			seal.setNilai((float) (0.5D + 0.5D * Math.sin(now * 0.12D)));
			switch (seal.keadaan()) {
				case SegelPintuEntity.KEADAAN_RETAK -> {
					if (since >= 20L) {
						seal.setKeadaan(SegelPintuEntity.KEADAAN_TERBUKA);
						if (seal.level() instanceof ServerLevel level) {
							level.sendParticles(ParticleTypes.CRIMSON_SPORE, seal.getX(), seal.getY() + 1.0D, seal.getZ(), 24, 0.8D, 0.8D, 0.8D, 0.01D);
						}
					}
				}
				case SegelPintuEntity.KEADAAN_TERBUKA -> {
					if (now - SEGEL_PERLU.getOrDefault(e.getKey(), 0L) > 100L) {
						seal.setKeadaan(SegelPintuEntity.KEADAAN_UTUH);
						if (seal.level() instanceof ServerLevel level) {
							level.playSound(null, seal.getX(), seal.getY(), seal.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F, 0.6F);
						}
					}
				}
				default -> {
				}
			}
		}
	}

	// ================================================================== wares

	private static ItemStack barang(Rak r) {
		if (r.gulungan() != null) {
			return new ItemStack(gulunganItem(r.gulungan()));
		}
		ItemStack slip = r.spec() == null ? ItemStack.EMPTY : JadeSlips.stack(r.spec());
		return slip.isEmpty() ? slip : tandaiNoda(slip);
	}

	static net.minecraft.world.item.Item gulunganItem(GulunganTerlarangItem.Jenis jenis) {
		return switch (jenis) {
			case BAKAR_DARAH -> TemaItems.GULUNGAN_BAKAR_DARAH;
			case LANGKAH_HANTU -> TemaItems.GULUNGAN_LANGKAH_HANTU;
			case RANTAI_JIWA -> TemaItems.GULUNGAN_RANTAI_JIWA;
			case TOPENG_IBLIS_HATI -> TemaItems.GULUNGAN_TOPENG_IBLIS_HATI;
		};
	}

	/** Marks a forbidden slip (Infami +8 when learned) and gives it the stain's lore line. */
	public static ItemStack tandaiNoda(ItemStack stack) {
		CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.putBoolean(NODA_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.iblis.menara.noda_lore").withStyle(ChatFormatting.DARK_RED))));
		return stack;
	}

	/** The price of a ware (scrolls: their fixed NB; slips: 3 x the pavilion value of the slip's grade). */
	static long harga(Rak r) {
		if (r.gulungan() != null) {
			return r.gulungan().harga();
		}
		TechniqueSpec spec = r.spec() == null ? null : TechniqueRegistry.byId(r.spec());
		return spec == null ? 0L : 3L * Math.max(100, spec.grade().pavilionPrice());
	}

	static int pitaMin(Rak r) {
		return r.gulungan() != null ? r.gulungan().pita() : 3;
	}

	/** The Kitab Darah Senja: a jade slip of the ancient lord's technique, named, stained, with its lore. */
	public static ItemStack kitabDarahSenja() {
		ItemStack slip = ItemStack.EMPTY;
		for (String id : KITAB_SPEC) {
			slip = JadeSlips.stack(id);
			if (!slip.isEmpty()) {
				break;
			}
		}
		if (slip.isEmpty()) {
			return slip;
		}
		tandaiNoda(slip);
		CompoundTag data = slip.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.putBoolean(KITAB_KEY, true);
		slip.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		slip.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.iblis.kitab").withStyle(ChatFormatting.DARK_RED));
		slip.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.iblis.kitab.lore.1").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.kota_tema.iblis.kitab.lore.2").withStyle(ChatFormatting.DARK_RED))));
		return slip;
	}

	/** Gives the town's one Kitab Darah Senja to the player (Darah route reward or a floor-9 purchase); false when gone. */
	static boolean beriKitab(ServerPlayer p, long situsId) {
		MinecraftServer server = p.level().getServer();
		CompoundTag town = IblisData.kota(server, situsId);
		if (town.getIntOr("kitab_stok", 1) <= 0) {
			return false;
		}
		ItemStack kitab = kitabDarahSenja();
		if (kitab.isEmpty()) {
			return false;
		}
		town.putInt("kitab_stok", 0);
		IblisData.ubah(server);
		Barang.beri(p, kitab);
		IblisMeter.tambahInfami(p, INFAMI_KITAB, "kitab_darah");
		Advancements.grant(p, "tempat/tema_iblis_kitab_darah");
		CeritaApi.peristiwa(p, IblisCerita.KITAB, "darah_senja");
		CeritaApi.kabar(p.level(), situsId, "tema_iblis_kitab", p.getName().getString());
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.kitab").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.kota_tema.iblis.kitab.dapat").withStyle(ChatFormatting.RED), 10, 70, 20);
		return true;
	}

	// ================================================================== the screen

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return null;
		}
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.kitab", LembarKunci.GAYA_KERTAS, npc);
		if (Gerbang.diburu(p, kota.situsId())) {
			IblisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.iblis.menara.diburu"), null);
			return tag;
		}
		var registries = p.level().registryAccess();
		MinecraftServer server = p.level().getServer();
		int band = IblisKota.band(p);
		ListTag floors = new ListTag();
		for (int n = 1; n <= LANTAI; n++) {
			CompoundTag f = new CompoundTag();
			f.putInt("no", n);
			LembarKunci.teks(f, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.iblis.menara.lantai." + n));
			f.putBoolean(TemaLayar.KitabTerlarang.TERBUKA, terbuka(p, kota, n));
			f.putInt(TemaLayar.KitabTerlarang.GENTAR_MIN, n >= LANTAI ? -1 : gentarMin(n));
			f.putBoolean(TemaLayar.KitabTerlarang.SEGEL, n >= LANTAI);
			floors.add(f);
		}
		tag.put(TemaLayar.KitabTerlarang.LANTAI, floors);
		ListTag scrolls = new ListTag();
		ListTag slips = new ListTag();
		for (Rak r : RAK) {
			ItemStack stack = barang(r);
			if (stack.isEmpty()) {
				continue;
			}
			boolean open = terbuka(p, kota, r.lantai());
			int min = pitaMin(r);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, r.id());
			LembarKunci.teks(row, LembarKunci.NAMA, stack.getHoverName());
			LembarKunci.barang(row, stack, registries);
			row.putLong(LembarKunci.HARGA, harga(r));
			row.putInt("lantai", r.lantai());
			String keadaan = !open || band < min ? LembarKunci.K_TERKUNCI : NilaiBatu.total(p) < harga(r) ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
			row.putString(LembarKunci.KEADAAN, keadaan);
			if (!open) {
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.iblis.menara.butuh_gentar", gentarMin(r.lantai())));
			} else if (band < min) {
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.iblis.pasar.pita", min));
			}
			if (r.gulungan() != null) {
				row.putInt(LembarKunci.PITA, min);
				row.putInt(TemaLayar.KitabTerlarang.INFAMI, GulunganTerlarangItem.INFAMI_BELI);
				LembarKunci.teks(row, TemaLayar.KitabTerlarang.EFEK, Component.translatable("kultivator.kota_tema.iblis.gulungan." + r.gulungan().id() + ".efek"));
				scrolls.add(row);
			} else {
				row.putInt(TemaLayar.KitabTerlarang.INFAMI, INFAMI_GIOK);
				slips.add(row);
			}
		}
		// floor 9: the one Kitab Darah Senja
		CompoundTag town = IblisData.kota(server, kota.situsId());
		ItemStack kitab = kitabDarahSenja();
		if (!kitab.isEmpty()) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "kitab_darah_senja");
			LembarKunci.teks(row, LembarKunci.NAMA, kitab.getHoverName());
			LembarKunci.barang(row, kitab, registries);
			row.putLong(LembarKunci.HARGA, HARGA_KITAB);
			row.putInt("lantai", LANTAI);
			row.putInt(TemaLayar.KitabTerlarang.INFAMI, INFAMI_KITAB);
			boolean stok = town.getIntOr("kitab_stok", 1) > 0;
			boolean segel = segelUntuk(p, kota);
			row.putString(LembarKunci.KEADAAN, !stok ? LembarKunci.K_HABIS : !segel ? LembarKunci.K_TERKUNCI : LembarKunci.K_OK);
			if (!segel && stok) {
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.iblis.menara.segel_alasan"));
			}
			slips.add(row);
		}
		tag.put(TemaLayar.KitabTerlarang.GULUNGAN, scrolls);
		tag.put(TemaLayar.KitabTerlarang.GIOK, slips);
		return tag;
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		if (TemaLayar.KitabTerlarang.OP_LANTAI.equals(parts[0])) {
			int n = (int) Math.max(1L, Math.min(LANTAI, IblisLayar.angka(parts, 1, 1L)));
			Component line = Component.translatable("kultivator.kota_tema.iblis.menara.lantai." + n + ".isi");
			if (terbuka(p, kota, n)) {
				IblisLayar.pesan(p, line, true);
			} else {
				IblisLayar.peringatan(p, Component.translatable("kultivator.kota_tema.iblis.menara.butuh_gentar", n >= LANTAI ? 0 : gentarMin(n)));
			}
			return;
		}
		if (!TemaLayar.KitabTerlarang.OP_BELI.equals(parts[0]) || parts.length < 2) {
			return;
		}
		if (Gerbang.diburu(p, kota.situsId())) {
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.menara.diburu"), false);
			return;
		}
		String id = parts[1];
		if ("kitab_darah_senja".equals(id)) {
			if (!segelUntuk(p, kota)) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.menara.segel_alasan"), false);
				return;
			}
			if (IblisData.kota(p.level().getServer(), kota.situsId()).getIntOr("kitab_stok", 1) <= 0) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.menara.kitab_habis"), false);
				return;
			}
			if (!NilaiBatu.pay(p, HARGA_KITAB)) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(HARGA_KITAB)), false);
				return;
			}
			IblisData.tambahKas(p.level().getServer(), kota.situsId(), HARGA_KITAB / 2L);
			beriKitab(p, kota.situsId());
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kitab.dapat"), true);
			return;
		}
		for (Rak r : RAK) {
			if (!r.id().equals(id)) {
				continue;
			}
			if (!terbuka(p, kota, r.lantai())) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.menara.butuh_gentar", gentarMin(r.lantai())), false);
				return;
			}
			int min = pitaMin(r);
			if (IblisKota.band(p) < min) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.pita", min), false);
				return;
			}
			ItemStack stack = barang(r);
			long price = harga(r);
			if (stack.isEmpty() || price <= 0L) {
				return;
			}
			if (!NilaiBatu.pay(p, price)) {
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(price)), false);
				return;
			}
			IblisData.tambahKas(p.level().getServer(), kota.situsId(), price / 4L);
			Barang.beri(p, Barang.tandaiAsal(stack, IblisKota.hari(p.level())));
			if (r.gulungan() != null) {
				IblisMeter.tambahInfami(p, GulunganTerlarangItem.INFAMI_BELI, "gulungan");
			}
			CeritaApi.peristiwa(p, IblisCerita.KITAB, r.gulungan() != null ? "gulungan" : "beli_giok");
			IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.beli", stack.getHoverName(), NilaiBatu.text(price)), true);
			return;
		}
	}

	/** Lines for {@code uji tema iblis menara}: floors open to the caller and the seal's state. */
	static List<Component> status(ServerPlayer p) {
		List<Component> out = new ArrayList<>();
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return out;
		}
		for (int n = 1; n <= LANTAI; n++) {
			out.add(Component.translatable("kultivator.kota_tema.iblis.cmd.lantai", n, terbuka(p, kota, n) ? "+" : "-", n >= LANTAI ? 0 : gentarMin(n)));
		}
		SegelPintuEntity seal = SEGEL.get(kota.situsId());
		out.add(Component.translatable("kultivator.kota_tema.iblis.cmd.segel", seal == null ? -1 : seal.keadaan(),
				IblisData.kota(p.level().getServer(), kota.situsId()).getIntOr("kitab_stok", 1)));
		return out;
	}

	/** Supplier for tests: the forbidden slip of a floor ware id ("" when unknown). */
	static Supplier<ItemStack> giokUji(String id) {
		for (Rak r : RAK) {
			if (r.id().equals(id)) {
				return () -> barang(r);
			}
		}
		return () -> ItemStack.EMPTY;
	}

	static long hari(ServerLevel level) {
		return TemaKalender.hari(level);
	}
}
