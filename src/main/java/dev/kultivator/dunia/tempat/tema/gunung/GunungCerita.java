package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * The story hooks of the Kota Gunung: chain {@code tema_gunung_tali} "Tali yang Terakhir" (DESIGN 38.T.6.5, six chapters,
 * data {@code data/kultivator/tempat/quest/tema_gunung_tali.json} written by {@code tools/gen_tempat_tema_gunung.py}) and
 * the four side quests. The quest data advances on story marks ({@code tanda <id>} goals of 38ST's QuestTracker) that the
 * mechanisms fire; this class makes the scenes that need bodies:
 * <ol>
 * <li>{@code macet}: the first climb of the cable car stops mid-line ({@link KeretaGantung}); the chain starts there.</li>
 * <li>{@code jaga}: a night watch at the Tiang Tengah: after 30 s on the post three saboteurs with spirit saws come;
 * defeating all three catches one (flag {@code gunung_tangkap}); after 90 s the survivors flee to the cliff (flag
 * {@code gunung_kabur}).</li>
 * <li>{@code jerat}: Gou Tie's six spirit snares in the niches of the plank path ({@link JeratEntity}), one click each.</li>
 * <li>{@code kera_putih}: the Kera Putih Tua comes out of the mist at the foot of the Katrol Tebing and waits at its
 * nest on the crag; ten fruits given politely (flag {@code gunung_sopan}) or the fibre taken by force (flag
 * {@code gunung_paksa}, the guardian turns hostile) give the Tali Sutra Kera.</li>
 * <li>{@code bela}: Gou Tie and four hunters storm the lower station while the new rope is fitted; the guardian
 * helps if the player was polite; Gou Tie yields at low health and his dialog decides (effect {@code gunung_gou}).</li>
 * <li>{@code berkat}: the blessing (effect {@code gunung_upacara}): the Lonceng Awan rings, the Buddha's light is forced,
 * the cable car becomes free for life, one Restu Puncak without the climb, the title Penjaga Tali Awan.</li>
 * </ol>
 * Effects registered here (used by the dialog and quest data): {@code gunung_herba N}, {@code gunung_kera_putih},
 * {@code gunung_gou tobat|usir|suap}, {@code gunung_upacara}, {@code gunung_nama_baik N}, {@code gunung_gelar} (title +
 * advancement {@code tempat/tema_penjaga_tali}), {@code gunung_adv <id>} (one of {@link #ADV}), {@code gunung_hadiah
 * <id>} (loot table of {@link #HADIAH}). Bodies
 * of the scenes are noSave and vanish when their anchor unloads.
 */
public final class GunungCerita {

	public static final String QUEST_TALI = "tema_gunung_tali";
	public static final String QUEST_IBU = "tema_ziarah_ibu";
	public static final String QUEST_KULI = "tema_kuli_sehari";
	public static final String QUEST_KERA = "tema_kera_nakal";
	public static final String QUEST_RUMPUT = "tema_rumput_awan";

	public static final int JAGA_TUNGGU = 30;
	public static final int JAGA_KABUR = 90;
	public static final int BUAH_SOPAN = 10;

	/** Theme advancements an effect {@code gunung_adv <id>} may grant ({@code tempat/tema_gunung_<id>}). */
	public static final List<String> ADV = List.of("ibu_peziarah", "kuli_sehari", "kera_nakal", "rumput_awan", "tobat_gou");

	/** Loot tables {@code kultivator:tema/gunung/<id>} an effect {@code gunung_hadiah <id>} may roll. */
	public static final List<String> HADIAH = List.of("sarang_kera", "persembahan_ziarah", "bekal_kuli", "rampasan_pemburu");

	/** One scene of one player (watch or station defence). */
	private static final class Adegan {
		final UUID pemain;
		final String jenis;
		final List<KultivatorNpc> musuh = new ArrayList<>();
		@Nullable KultivatorNpc gouTie;
		@Nullable KeraGunungEntity kera;
		int detik;
		boolean muncul;

		Adegan(UUID pemain, String jenis) {
			this.pemain = pemain;
			this.jenis = jenis;
		}
	}

	private static final Map<UUID, Adegan> ADEGAN = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Akibat.daftar("gunung_herba", (p, a) -> {
			int n = a.isEmpty() ? 1 : Math.max(1, Math.min(6, parse(a.get(0), 1)));
			for (int i = 0; i < n; i++) {
				Barang.beri(p, SpiritHerbs.randomFor(p.getRandom().nextBoolean() ? Element.WOOD : Element.WIND, p.getRandom(),
						HerbaTebingEntity.UMUR_MIN, HerbaTebingEntity.UMUR_MAKS));
			}
		});
		Akibat.daftar("gunung_kera_putih", (p, a) -> keraMuncul(p));
		Akibat.daftar("gunung_gou", (p, a) -> keputusanGou(p, a.isEmpty() ? "usir" : a.get(0)));
		Akibat.daftar("gunung_upacara", (p, a) -> upacara(p));
		Akibat.daftar("gunung_nama_baik", (p, a) -> namaBaik(p, a.isEmpty() ? 0 : parse(a.get(0), 0)));
		Akibat.daftar("gunung_adv", (p, a) -> {
			if (!a.isEmpty() && ADV.contains(a.get(0))) {
				dev.kultivator.util.Advancements.grant(p, "tempat/tema_gunung_" + a.get(0));
			}
		});
		Akibat.daftar("gunung_hadiah", (p, a) -> {
			if (!a.isEmpty() && HADIAH.contains(a.get(0))) {
				hadiah(p, a.get(0));
			}
		});
		Akibat.daftar("gunung_gelar", (p, a) -> {
			CeritaApi.setBendera(p, "gunung_penjaga_tali_awan", true);
			CeritaApi.setBendera(p, "gelar.penjaga_tali_awan", true);
			dev.kultivator.util.Advancements.grant(p, "tempat/tema_penjaga_tali");
			Msg.title(p, Component.translatable("kultivator.kota_tema.gunung.gelar.penjaga_tali_awan").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.kota_tema.gunung.gelar.penjaga_tali_awan.sub"), 10, 80, 30);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(GunungCerita::matiMusuh);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GunungCerita::bolehLuka);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ADEGAN.clear());
	}

	/** Rolls loot table {@code kultivator:tema/gunung/<id>} for {@code p} (gift context: origin and player). */
	static void hadiah(ServerPlayer p, String id) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Kultivator.id("tema/gunung/" + id));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, p.position())
				.withOptionalParameter(LootContextParams.THIS_ENTITY, p)
				.create(LootContextParamSets.CHEST);
		for (ItemStack stack : new ArrayList<>(table.getRandomItems(params))) {
			Barang.beri(p, stack);
		}
	}

	private static int parse(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String tahap(ServerPlayer p, String quest) {
		return QuestTracker.aktif(p, quest) ? QuestTracker.simpul(p, quest) : "";
	}

	/** Nama Baik at the 38KS town the player stands in. */
	static void namaBaik(ServerPlayer p, int n) {
		if (n == 0 || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		long town = Towns.within(level, p.blockPosition());
		if (town == 0L) {
			town = Towns.near(level, p.blockPosition(), 320);
		}
		if (town != 0L) {
			KotaData.get(level.getServer()).addReputation(town, p.getUUID(), n);
			Msg.actionBar(p, Component.translatable(n > 0 ? "kultivator.cerita.akibat.nama_baik_naik" : "kultivator.cerita.akibat.nama_baik_turun",
					Math.abs(n)).withStyle(n > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		}
	}

	// ================================================================================================ 1 Lift Macet

	/** The cable car stopped on the first climb of {@code p}: the chain starts. */
	static void liftMacet(ServerPlayer p, long situsId) {
		if (QuestTracker.tahap(p, QUEST_TALI) <= 0) {
			long town = Towns.within((ServerLevel) p.level(), p.blockPosition());
			CeritaApi.mulaiQuest(p, QUEST_TALI, town != 0L ? town : situsId);
		}
		CeritaApi.peristiwa(p, "tanda", "gunung_lift_macet");
	}

	/** A rider left the stuck cabin: Slow Falling so the rope ride is safe. */
	static void keluarSaatMacet(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, true));
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.lift.tali").withStyle(ChatFormatting.AQUA));
	}

	// ================================================================================================ 2 the night watch

	static void dekatTiang(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		if (kota == null) {
			return;
		}
		Vec3 pos = GunungTata.dunia(kota, GunungTata.JAGA_TIANG);
		long jam = TemaKalender.jam(level);
		boolean malam = jam >= 13000L && jam < 23000L;
		for (ServerPlayer p : within) {
			if (!"jaga".equals(tahap(p, QUEST_TALI))) {
				continue;
			}
			Adegan a = ADEGAN.get(p.getUUID());
			if (a == null) {
				if (!malam || p.position().distanceToSqr(pos) > 6.0D * 6.0D) {
					if (level.getServer().getTickCount() % 400 == 0 && p.position().distanceToSqr(pos) < 24.0D * 24.0D) {
						Msg.actionBar(p, Component.translatable(malam ? "kultivator.kota_tema.gunung.jaga.ke_pos" : "kultivator.kota_tema.gunung.jaga.malam")
								.withStyle(ChatFormatting.GRAY));
					}
					continue;
				}
				a = new Adegan(p.getUUID(), "jaga");
				ADEGAN.put(p.getUUID(), a);
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.jaga.mulai").withStyle(ChatFormatting.YELLOW));
			}
			if (!"jaga".equals(a.jenis)) {
				continue;
			}
			a.detik++;
			if (!a.muncul && a.detik >= JAGA_TUNGGU) {
				a.muncul = true;
				for (int i = 0; i < 3; i++) {
					double ang = Math.toRadians(p.getYRot() + 140.0D + i * 40.0D);
					BlockPos at = BlockPos.containing(pos.x + Math.cos(ang) * 10.0D, pos.y, pos.z + Math.sin(ang) * 10.0D);
					KultivatorNpc m = HidupApi.tubuh(level, GunungAnchors.kotaId(level, anchor), NpcRole.PENGEMBARA, at, p.getYRot() + 180.0F);
					if (m != null) {
						musuh(m, p, "jaga");
						m.setPlate(Component.translatable("kultivator.kota_tema.gunung.musuh.penggergaji"));
						a.musuh.add(m);
					}
				}
				Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.jaga.datang").withStyle(ChatFormatting.RED));
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
			}
			if (a.muncul) {
				a.musuh.removeIf(Entity::isRemoved);
				if (a.musuh.isEmpty()) {
					selesaiJaga(p, a, true);
				} else if (a.detik >= JAGA_TUNGGU + JAGA_KABUR) {
					for (KultivatorNpc m : a.musuh) {
						m.vanish();
					}
					selesaiJaga(p, a, false);
				}
			}
		}
	}

	private static void musuh(KultivatorNpc m, ServerPlayer p, String grup) {
		m.memory().putString("alur", "musuh");
		m.memory().putString("gunung_musuh", p.getUUID() + "|" + grup);
		m.memory().putBoolean("hold", false);
		m.markHostile(p);
		m.setTarget(p);
	}

	private static void selesaiJaga(ServerPlayer p, Adegan a, boolean tangkap) {
		ADEGAN.remove(p.getUUID());
		CeritaApi.setBendera(p, tangkap ? "gunung_tangkap" : "gunung_kabur", true);
		Msg.chat(p, Component.translatable(tangkap ? "kultivator.kota_tema.gunung.jaga.tangkap" : "kultivator.kota_tema.gunung.jaga.kabur")
				.withStyle(ChatFormatting.GOLD));
		CeritaApi.peristiwa(p, "tanda", "gunung_jaga_selesai");
	}

	private static void matiMusuh(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity instanceof KultivatorNpc npc)) {
			return;
		}
		String tag = npc.memory().getStringOr("gunung_musuh", "");
		if (tag.isEmpty()) {
			return;
		}
		// the lists of the scenes drop removed bodies on their next pass; nothing else to do here
		npc.memory().remove("gunung_musuh");
	}

	// ================================================================================================ 3 the snares

	static void dekatCeruk(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		int no = GunungAnchors.angka(anchor, "no", 0);
		if (no <= 0) {
			return;
		}
		boolean perlu = false;
		for (ServerPlayer p : within) {
			if ("jerat".equals(tahap(p, QUEST_TALI)) && (GunungData.pemain(p).getIntOr("jr", 0) & (1 << (no - 1))) == 0
					&& p.distanceToSqr(anchor) < 40.0D * 40.0D) {
				perlu = true;
				break;
			}
		}
		List<Entity> list = GunungAnchors.HANDLER.daftar(anchor);
		JeratEntity ada = null;
		for (Entity e : list) {
			if (e instanceof JeratEntity j && !j.isRemoved()) {
				ada = j;
			}
		}
		if (perlu && ada == null) {
			TemaKota.Kota kota = GunungAnchors.kota(anchor);
			JeratEntity j = TemaMekanisme.muncul(level, TemaEntities.JERAT, anchor.position(), anchor.getYRot(), kota, anchor.blockPosition());
			if (j != null) {
				j.setKeadaan(JeratEntity.KEADAAN_TERBUKA);
				j.setNilai((float) no);
				list.add(j);
			}
		} else if (!perlu && ada != null && ada.keadaan() != JeratEntity.KEADAAN_DILEPAS) {
			TemaMekanisme.lepas(ada);
			list.remove(ada);
		}
	}

	/** A player clicked a snare. */
	static void lepasJerat(ServerPlayer p, JeratEntity jerat) {
		if (!"jerat".equals(tahap(p, QUEST_TALI))) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.jerat.bukan").withStyle(ChatFormatting.GRAY));
			return;
		}
		int no = Math.round(jerat.nilai());
		CompoundTag d = GunungData.pemain(p);
		int bits = d.getIntOr("jr", 0);
		if (no >= 1 && no <= 6 && (bits & (1 << (no - 1))) == 0) {
			d.putInt("jr", bits | (1 << (no - 1)));
			GunungData.ubah(p.level().getServer());
			CeritaApi.peristiwa(p, "tanda", "gunung_jerat");
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.jerat.lepas", Integer.bitCount(bits | (1 << (no - 1))), 6)
					.withStyle(ChatFormatting.GOLD));
		}
		jerat.setKeadaan(JeratEntity.KEADAAN_DILEPAS);
		p.level().playSound(null, jerat.getX(), jerat.getY(), jerat.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	// ================================================================================================ 4 the white guardian

	/** Stage 4 begins: mist at the town and the guardian at the foot of the Katrol Tebing, pointing up. */
	static void keraMuncul(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		TemaKota.Kota kota = GunungTata.terdekat(level, p.blockPosition(), 200);
		if (kota == null) {
			return;
		}
		CompoundTag fx = new CompoundTag();
		fx.putString("apa", "kabut");
		fx.putInt("x", p.getBlockX());
		fx.putInt("y", p.getBlockY());
		fx.putInt("z", p.getBlockZ());
		fx.putLong("tick", level.getGameTime());
		fx.putLong("panjang", 200L);
		fx.putBoolean("nyala", true);
		TemaNet.kirim(p, TemaNet.CAHAYA, kota.situsId(), fx);
		Vec3 kaki = GunungTata.dunia(kota, "tebing_papan", 64.5D, 5.0D, 80.5D);
		KeraGunungEntity kera = TemaMekanisme.muncul(level, TemaEntities.KERA_GUNUNG, kaki, 90.0F, kota, null);
		if (kera != null) {
			kera.jadikanPutihTua();
			kera.setKeadaan(KeraGunungEntity.KEADAAN_TUNJUK);
			kera.ikat(kota.situsId(), BlockPos.containing(kaki));
			Adegan a = new Adegan(p.getUUID(), "kera");
			a.kera = kera;
			ADEGAN.put(p.getUUID(), a);
		}
		Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.kera_putih.muncul").withStyle(ChatFormatting.AQUA));
	}

	/** The nest on the crag: the guardian waits there while stage 4 runs for a player near. */
	static void dekatSarang(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		boolean perlu = false;
		for (ServerPlayer p : within) {
			if ("kera_putih".equals(tahap(p, QUEST_TALI))) {
				perlu = true;
			}
		}
		List<Entity> list = GunungAnchors.HANDLER.daftar(anchor);
		KeraGunungEntity ada = null;
		for (Entity e : list) {
			if (e instanceof KeraGunungEntity k && k.isAlive()) {
				ada = k;
			}
		}
		if (perlu && ada == null) {
			TemaKota.Kota kota = GunungAnchors.kota(anchor);
			KeraGunungEntity k = TemaMekanisme.muncul(level, TemaEntities.KERA_GUNUNG, anchor.position(), anchor.getYRot(), kota, anchor.blockPosition());
			if (k != null) {
				k.jadikanPutihTua();
				k.ikat(kota == null ? 0L : kota.situsId(), anchor.blockPosition());
				k.setKeadaan(KeraGunungEntity.KEADAAN_DUDUK);
				k.addTag("gunung_sarang");
				list.add(k);
			}
		}
	}

	/** The pulley put a player on its upper landing. */
	static void katrolTiba(ServerPlayer p, KatrolEntity katrol) {
		if ("tebing".equals(katrol.nama()) && "kera_putih".equals(tahap(p, QUEST_TALI))) {
			Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.kera_putih.sarang").withStyle(ChatFormatting.AQUA));
			CeritaApi.peristiwa(p, "tanda", "gunung_sarang");
			Adegan a = ADEGAN.remove(p.getUUID());
			if (a != null && a.kera != null && !a.kera.isRemoved()) {
				TemaMekanisme.lepas(a.kera);
			}
		}
	}

	/** A player clicks the guardian at its nest: ten fruits are a polite request. */
	static boolean keraPutihDiklik(ServerPlayer p, KeraGunungEntity kera) {
		if (!"kera_putih".equals(tahap(p, QUEST_TALI))) {
			kera.setKeadaan(KeraGunungEntity.KEADAAN_MEMBUNGKUK);
			return false;
		}
		int buah = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (buah(s)) {
				buah += s.getCount();
			}
		}
		if (buah < BUAH_SOPAN) {
			kera.setKeadaan(KeraGunungEntity.KEADAAN_TUNJUK);
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.gunung.kera_putih.minta", BUAH_SOPAN).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		int sisa = BUAH_SOPAN;
		for (int i = 0; i < p.getInventory().getContainerSize() && sisa > 0; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (buah(s)) {
				int take = Math.min(sisa, s.getCount());
				s.shrink(take);
				sisa -= take;
			}
		}
		kera.setKeadaan(KeraGunungEntity.KEADAAN_MEMBUNGKUK);
		Barang.beri(p, new ItemStack(TemaItems.TALI_SUTRA_KERA));
		hadiah(p, "sarang_kera");
		CeritaApi.setBendera(p, "gunung_sopan", true);
		CeritaApi.peristiwa(p, "tanda", "gunung_tali_sutra");
		Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.kera_putih.sopan").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** The guardian was attacked: under half health it lets go of the fibre and stays hostile (the forced path). */
	static void keraPutihDiserang(ServerPlayer p, KeraGunungEntity kera) {
		if (!"kera_putih".equals(tahap(p, QUEST_TALI)) || CeritaApi.bendera(p, "gunung_paksa")) {
			return;
		}
		if (kera.getHealth() < kera.getMaxHealth() * 0.5F) {
			Barang.beri(p, new ItemStack(TemaItems.TALI_SUTRA_KERA));
			CeritaApi.setBendera(p, "gunung_paksa", true);
			CeritaApi.peristiwa(p, "tanda", "gunung_tali_sutra");
			Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.kera_putih.paksa").withStyle(ChatFormatting.RED));
		}
	}

	private static boolean buah(ItemStack s) {
		return s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE) || s.is(Items.MELON_SLICE);
	}

	// ================================================================================================ 5 the station defence

	/** A station anchor with players near (stage 5 at the lower station). */
	static void dekatStasiun(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!"bawah".equals(anchor.data().getStringOr("stasiun", ""))) {
			return;
		}
		for (ServerPlayer p : within) {
			if (!"bela".equals(tahap(p, QUEST_TALI)) || p.distanceToSqr(anchor) > 20.0D * 20.0D) {
				continue;
			}
			Adegan a = ADEGAN.get(p.getUUID());
			if (a == null) {
				a = new Adegan(p.getUUID(), "bela");
				ADEGAN.put(p.getUUID(), a);
				serbuan(level, anchor, p, a);
			}
			if (!"bela".equals(a.jenis)) {
				continue;
			}
			a.musuh.removeIf(Entity::isRemoved);
			a.detik++;
			if (a.kera != null && !a.kera.isRemoved() && !a.musuh.isEmpty()) {
				a.kera.setTarget(a.musuh.getFirst());
			}
			if (a.musuh.isEmpty() && (a.gouTie == null || a.gouTie.isRemoved() || a.gouTie.memory().getBooleanOr("gunung_menyerah", false))) {
				if (!CeritaApi.bendera(p, "gunung_gou_menyerah")) {
					CeritaApi.setBendera(p, "gunung_gou_menyerah", true);
					Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.bela.menyerah").withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	private static void serbuan(ServerLevel level, DuniaAnchorEntity anchor, ServerPlayer p, Adegan a) {
		long town = GunungAnchors.kotaId(level, anchor);
		Vec3 c = anchor.position();
		for (int i = 0; i < 4; i++) {
			double ang = Math.toRadians(i * 90.0D + 45.0D);
			BlockPos at = BlockPos.containing(c.x + Math.cos(ang) * 12.0D, c.y - 8.0D, c.z + Math.sin(ang) * 12.0D);
			KultivatorNpc m = HidupApi.tubuh(level, town, NpcRole.PENGEMBARA, at, 0.0F);
			if (m != null) {
				musuh(m, p, "bela");
				m.setPlate(Component.translatable("kultivator.kota_tema.gunung.musuh.pemburu"));
				a.musuh.add(m);
			}
		}
		BlockPos gp = BlockPos.containing(c.x, c.y - 8.0D, c.z + 14.0D);
		KultivatorNpc gou = GunungTokoh.panggil(level, town, GunungTokoh.GOU_TIE, gp, 180.0F);
		if (gou != null) {
			musuh(gou, p, "bela");
			gou.memory().putString("gunung_gou_lawan", p.getUUID().toString());
			a.gouTie = gou;
		}
		if (CeritaApi.bendera(p, "gunung_sopan")) {
			TemaKota.Kota kota = GunungAnchors.kota(anchor);
			KeraGunungEntity kera = TemaMekanisme.muncul(level, TemaEntities.KERA_GUNUNG, c.add(3.0D, -8.0D, 3.0D), 0.0F, kota, null);
			if (kera != null) {
				kera.jadikanPutihTua();
				kera.marah(null);
				a.kera = kera;
				Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.bela.kera_bantu").withStyle(ChatFormatting.AQUA));
			}
		}
		Msg.title(p, Component.translatable("kultivator.kota_tema.gunung.bela.judul").withStyle(ChatFormatting.RED),
				Component.translatable("kultivator.kota_tema.gunung.bela.sub"), 10, 60, 20);
	}

	/** Gou Tie never dies in the defence: at low health he yields (his dialog then decides his fate). */
	private static boolean bolehLuka(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (entity instanceof KultivatorNpc npc && GunungTokoh.adalah(npc, GunungTokoh.GOU_TIE)
				&& npc.getHealth() - amount < 4.0F && !npc.memory().getBooleanOr("gunung_menyerah", false)) {
			npc.memory().putBoolean("gunung_menyerah", true);
			npc.memory().putString("alur", "");
			String lawan = npc.memory().getStringOr("gunung_gou_lawan", "");
			if (!lawan.isEmpty()) {
				try {
					npc.forgive(UUID.fromString(lawan));
				} catch (IllegalArgumentException ignored) {
					// bad id in memory: nothing to forgive
				}
			}
			npc.setTarget(null);
			npc.memory().putBoolean("hold", true);
			npc.say(Component.translatable("kultivator.kota_tema.gunung.bela.gou_menyerah"), 100);
			HidupApi.klip(npc, GerakId.SUJUD_DUPA, 0, 100);
			return false;
		}
		if (entity instanceof KeraGunungEntity kera && kera.varian() == KeraGunungEntity.VARIAN_PUTIH_TUA
				&& source.getEntity() instanceof ServerPlayer p && kera.entityTags().contains("gunung_sarang")) {
			keraPutihDiserang(p, kera);
		}
		return true;
	}

	/** Effect {@code gunung_gou}: the fate of Gou Tie (a) tobat, (b) usir, (c) suap. */
	static void keputusanGou(ServerPlayer p, String pilihan) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		TemaKota.Kota kota = GunungTata.terdekat(level, p.blockPosition(), 200);
		Adegan a = ADEGAN.remove(p.getUUID());
		if (kota != null) {
			CompoundTag k = GunungData.kota(level.getServer(), kota.situsId());
			switch (pilihan) {
				case "tobat" -> {
					k.putInt("gt", 1);
					hadiah(p, "rampasan_pemburu");
				}
				case "suap" -> {
					k.putInt("gt", 0);
					k.putBoolean("mo", true);
					k.putBoolean("sp", true);
					Barang.beri(p, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 6));
				}
				default -> {
					k.putInt("gt", 2);
					hadiah(p, "rampasan_pemburu");
				}
			}
			GunungData.ubah(level.getServer());
		}
		CeritaApi.setBendera(p, "gunung_gou_" + pilihan, true);
		if (a != null) {
			if (a.gouTie != null && !a.gouTie.isRemoved()) {
				a.gouTie.vanish();
			}
			if (a.kera != null && !a.kera.isRemoved()) {
				TemaMekanisme.lepas(a.kera);
			}
		}
		CeritaApi.peristiwa(p, "tanda", "gunung_bela_selesai");
	}

	// ================================================================================================ 6 the blessing

	/** Effect {@code gunung_upacara}: bell, forced Buddha's light, free cable car for life, one Restu Puncak. */
	static void upacara(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		TemaKota.Kota kota = GunungTata.terdekat(level, p.blockPosition(), 240);
		if (kota != null) {
			GunungData.kota(level.getServer(), kota.situsId()).putBoolean("tb", true);
			GunungAcara.upacaraLonceng(level, kota);
			TemaKalender.paksa(level, kota.situsId(), GunungData.TEMA, GunungAcara.CAHAYA_BUDHA, true);
		}
		CompoundTag d = GunungData.pemain(p);
		d.putBoolean("lg", true);
		GunungData.ubah(level.getServer());
		Ziarah.restuHadiah(p);
		Msg.chat(p, Component.translatable("kultivator.kota_tema.gunung.upacara").withStyle(ChatFormatting.GOLD));
	}

	// ================================================================================================ side quest giver

	/**
	 * The Kuil Kaki desk: Pei Wenzhi lives at the temple (he offers {@code tema_ziarah_ibu}, waits while it runs and stays
	 * on afterwards, a pilgrim who keeps trying the steps).
	 */
	static void dekatZiarah(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within, GunungAnchors handler) {
		if (!within.isEmpty()) {
			handler.tamu(level, anchor, GunungTokoh.PEI_WENZHI, anchor.blockPosition().offset(3, 0, 2));
		}
	}

	/** An anchor unloads: the scenes of the players there end quietly. */
	static void jangkarLepas(ServerLevel level, DuniaAnchorEntity anchor) {
		if (!GunungAnchors.TIANG.equals(anchor.what()) && !GunungAnchors.LIFT.equals(anchor.what())) {
			return;
		}
		for (Adegan a : List.copyOf(ADEGAN.values())) {
			boolean jauh = true;
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(a.pemain);
			if (p != null && p.distanceToSqr(anchor) < 96.0D * 96.0D) {
				jauh = false;
			}
			if (jauh && !"kera".equals(a.jenis)) {
				for (KultivatorNpc m : a.musuh) {
					if (!m.isRemoved()) {
						m.vanish();
					}
				}
				if (a.gouTie != null && !a.gouTie.isRemoved()) {
					a.gouTie.vanish();
				}
				ADEGAN.remove(a.pemain);
			}
		}
	}

	private GunungCerita() {
	}
}
