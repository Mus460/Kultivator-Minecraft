package dev.kultivator.dunia.tempat.kampung;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.desa.Tribute;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.dunia.tempat.kompleks.TandingTempat;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The clan of a Desa Klan (DESIGN 38.C.2.3). Source ({@code kultivator.kampung.klan.inspired}): the Xiao family of Wu Tan
 * City (Battle Through the Heavens) and the arrogant young master of a noble clan, a stock trope of xianxia novels.
 * <ul>
 *   <li><b>Regard</b> ({@code kampung.klan.<site>.sikap}, -100..100 per visitor, {@link KampungData}): beating the young
 *       master +25, losing to him -5, yielding -10, beating the patriarch +20, the patriarch's tea +5 (once a day).</li>
 *   <li><b>The young master</b> ({@code kampung/tuan_muda}, the training ground): a visitor below {@code kampung.ranahTantangan}
 *       who walks onto the ground is challenged in front of the clan (a card with Accept / Yield; once per ten minutes
 *       until answered). The bout is a friendly Sword Duel ({@link TandingTempat}) with the duel's intro film. The
 *       result sets 38ST's story flags {@code klan_hormat} / {@code klan_meremehkan} (the dialogs of the patriarch and the
 *       young master and the quest "Tuan Muda yang Sombong" follow them).</li>
 *   <li><b>The patriarch</b> ({@code kampung/patriark}): invites a visitor at least as strong as the clan's patriarch
 *       ({@link #ranahPatriark}: Foundation to Nascent Soul by the site hash) to tea; a hall master whose regard is below
 *       {@code kampung.sikapIkrarKlan} may challenge him for the clan's pledge.</li>
 *   <li><b>Pledge</b>: the vassal plaque of the Desa system (25.6) at the clan's bell is refused ({@link Tribute#addPledgeVeto})
 *       until the patriarch was beaten or the regard reached {@code kampung.sikapIkrarKlan}.</li>
 *   <li><b>Tribute</b> ({@link Tribute#addBonusProvider}): a pledged clan pays cultivation goods besides the crops: one aged
 *       spirit herb (60-300 years by loyalty), a second from loyalty 70, and a middle spirit stone from loyalty 50.</li>
 * </ul>
 */
final class Klan {

	/** A challenge is not repeated sooner than this (ticks). */
	static final long TANTANG_JEDA = 20L * 60L * 10L;
	/** The patriarch's card is not repeated sooner than this (ticks). */
	static final long PATRIARK_JEDA = 20L * 60L * 5L;

	/** An open challenge of the young master: site, the body, until. */
	private record Tantangan(long situs, UUID npc, long sampai) {
	}

	private static final Map<UUID, Tantangan> TANTANGAN = new HashMap<>();
	private static final Map<UUID, Long> DITANTANG = new HashMap<>();
	private static final Map<UUID, Long> KARTU_PATRIARK = new HashMap<>();

	static void init() {
		Tribute.addPledgeVeto(Klan::vetoIkrar);
		Tribute.addBonusProvider(Klan::upeti);
		// story effect for 38ST's clan dialogs: "tanding_klan" starts the bout with the NPC being talked to
		Akibat.daftar("tanding_klan", (player, args) -> {
			KultivatorNpc npc = TempatDialog.npc(player);
			if (npc != null) {
				TempatDialog.akhiri(player);
				tandingDialog(player, npc);
			}
		});
	}

	/** The bout a clan dialog asked for: the young master's challenge or the patriarch's duel for the pledge. */
	static int tandingDialog(ServerPlayer player, KultivatorNpc npc) {
		ServerLevel level = player.level();
		DuniaAnchorEntity anchor = DuniaAnchors.nearest(level, npc.position(), npc.role() == NpcRole.PATRIARK_KLAN ? "kampung/patriark"
				: "kampung/tuan_muda", 64.0D);
		if (anchor == null) {
			return 0;
		}
		long site = KampungAnchors.situs(anchor);
		Component why;
		if (npc.role() == NpcRole.PATRIARK_KLAN) {
			why = TandingTempat.mulai(player, npc, DuelStyle.SANTO_PEDANG, 0xE8C860, Items.IRON_SWORD, true,
					(p, body, menang, kalah) -> hasilPatriark(p, body, site, menang, kalah));
		} else if (npc.role() == NpcRole.TUAN_MUDA) {
			why = TandingTempat.mulai(player, npc, DuelStyle.AGRESIF, 0x7FB8E8, Items.IRON_SWORD, true,
					(p, body, menang, kalah) -> hasilTuanMuda(p, body, site, menang, kalah));
		} else {
			return 0;
		}
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	static void reset() {
		TANTANGAN.clear();
		DITANTANG.clear();
		KARTU_PATRIARK.clear();
	}

	/** The realm of the clan's patriarch: Foundation, Golden Core or Nascent Soul by the site hash (38.C.2.3). */
	static Realm ranahPatriark(long situs) {
		return Realm.values()[Realm.FOUNDATION.ordinal() + Math.floorMod(KampungNames.mix(situs ^ 0x50415452L), 3)];
	}

	// ------------------------------------------------------------------ the young master

	static void tuanMuda(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within) {
		KampungConfig config = KampungConfig.get();
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.cari(site);
		if (d == null || !d.klan()) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.isCreative() || player.distanceToSqr(anchor) > 14.0D * 14.0D) {
				continue;
			}
			UUID id = player.getUUID();
			if (Cultivators.get(player).realm().ordinal() >= config.ranahTantangan || data.tuanMudaKalah(d, id) || data.mengalah(d, id)) {
				continue;
			}
			Long last = DITANTANG.get(id);
			if (last != null && now - last < TANTANG_JEDA) {
				continue;
			}
			KultivatorNpc npc = tubuh(level, anchor, site, NpcRole.TUAN_MUDA);
			if (npc == null) {
				continue;
			}
			DITANTANG.put(id, now);
			TANTANGAN.put(id, new Tantangan(site, npc.getUUID(), now + 20L * 60L));
			HidupApi.tatap(npc, player);
			npc.arm(true);
			npc.say(Component.translatable("kultivator.kampung.tuan_muda.ejek." + Math.floorMod(now / 20L, 8L)), 100);
			Component clan = KampungNames.nama(d.ukuran, d.situs);
			Kartu.kirim(player, Component.translatable("kultivator.kampung.tuan_muda.judul", clan),
					List.of(Kartu.ucapan(npc.getDisplayName(), Component.translatable("kultivator.kampung.tuan_muda.tantang")),
							Component.translatable("kultivator.kampung.tuan_muda.aturan")),
					List.of(Kartu.chip(Component.translatable("kultivator.kampung.tuan_muda.terima"), "/kultivasi kampung tantang terima",
									Component.translatable("kultivator.kampung.tuan_muda.terima.hover"), ChatFormatting.GREEN),
							Kartu.chip(Component.translatable("kultivator.kampung.tuan_muda.mengalah"), "/kultivasi kampung tantang mengalah",
									Component.translatable("kultivator.kampung.tuan_muda.mengalah.hover"), ChatFormatting.GRAY)));
			level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
		}
	}

	/** {@code /kultivasi kampung tantang terima|mengalah}. */
	static int jawab(ServerPlayer player, boolean terima) {
		Tantangan t = TANTANGAN.remove(player.getUUID());
		ServerLevel level = player.level();
		if (t == null || level.getGameTime() > t.sampai()) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.tuan_muda.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.cari(t.situs());
		if (!(level.getEntity(t.npc()) instanceof KultivatorNpc npc) || d == null || !npc.isAlive()) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.tuan_muda.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!terima) {
			data.setMengalah(d, player.getUUID());
			data.ubahSikap(d, player.getUUID(), -10);
			CeritaApi.setBendera(player, "klan_meremehkan", true);
			npc.say(Component.translatable("kultivator.kampung.tuan_muda.tawa"), 100);
			Msg.chat(player, Component.translatable("kultivator.kampung.tuan_muda.mengalah.chat", npc.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return 1;
		}
		long site = t.situs();
		Component why = TandingTempat.mulai(player, npc, DuelStyle.AGRESIF, 0x7FB8E8, Items.IRON_SWORD, true,
				(p, body, menang, kalah) -> hasilTuanMuda(p, body, site, menang, kalah));
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	private static void hasilTuanMuda(ServerPlayer player, KultivatorNpc npc, long site, boolean menang, boolean kalah) {
		KampungData data = KampungData.get(player.level().getServer());
		KampungData.Desa d = data.desa(site);
		if (menang) {
			data.setTuanMudaKalah(d, player.getUUID());
			int v = data.ubahSikap(d, player.getUUID(), 25);
			CeritaApi.setBendera(player, "klan_hormat", true);
			CeritaApi.setBendera(player, "klan_meremehkan", false);
			npc.say(Component.translatable("kultivator.kampung.tuan_muda.kalah"), 120);
			npc.bow(60);
			Msg.chat(player, Component.translatable("kultivator.kampung.tuan_muda.menang.chat", npc.getDisplayName(), v).withStyle(ChatFormatting.GOLD));
		} else if (kalah) {
			data.ubahSikap(d, player.getUUID(), -5);
			npc.say(Component.translatable("kultivator.kampung.tuan_muda.menang"), 120);
			Msg.chat(player, Component.translatable("kultivator.kampung.tuan_muda.kalah.chat", npc.getDisplayName()).withStyle(ChatFormatting.YELLOW));
		}
	}

	// ------------------------------------------------------------------ the patriarch

	static void patriark(ServerLevel level, DuniaAnchorEntity anchor, long site, List<ServerPlayer> within) {
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.cari(site);
		if (d == null || !d.klan()) {
			return;
		}
		long now = level.getGameTime();
		long day = Kota.day(level.getServer());
		KampungConfig config = KampungConfig.get();
		Realm patriarch = ranahPatriark(site);
		for (ServerPlayer player : within) {
			if (player.isSpectator() || player.distanceToSqr(anchor) > 10.0D * 10.0D) {
				continue;
			}
			Long last = KARTU_PATRIARK.get(player.getUUID());
			if (last != null && now - last < PATRIARK_JEDA) {
				continue;
			}
			KARTU_PATRIARK.put(player.getUUID(), now);
			UUID id = player.getUUID();
			boolean strong = Cultivators.get(player).realm().ordinal() >= patriarch.ordinal();
			List<Component> lines = new ArrayList<>();
			Component clan = KampungNames.nama(d.ukuran, d.situs);
			Component who = Component.translatable("kultivator.kampung.patriark.nama", KampungNames.marga(site));
			if (strong && data.ambilUndangan(d, id, day)) {
				int v = data.ubahSikap(d, id, 5);
				lines.add(Kartu.ucapan(who, Component.translatable("kultivator.kampung.patriark.undang." + d.gaya)));
				lines.add(Component.translatable("kultivator.kampung.patriark.teh", v));
				level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.6F, 1.2F);
			} else {
				lines.add(Kartu.ucapan(who, Component.translatable(data.tuanMudaKalah(d, id) ? "kultivator.kampung.patriark.hormat"
						: data.mengalah(d, id) ? "kultivator.kampung.patriark.remeh" : "kultivator.kampung.patriark.sapa." + level.getRandom().nextInt(3))));
			}
			int regard = data.sikap(d, id);
			lines.add(Component.translatable("kultivator.kampung.klan.sikap", regard, config.sikapIkrarKlan));
			List<Component> chips = new ArrayList<>();
			boolean open = data.patriarkKalah(d, id) || regard >= config.sikapIkrarKlan;
			if (open) {
				lines.add(Component.translatable("kultivator.kampung.klan.ikrar_terbuka").withStyle(ChatFormatting.GREEN));
			} else if (Halls.hallOf(id) != null) {
				chips.add(Kartu.chip(Component.translatable("kultivator.kampung.patriark.tantang"), "/kultivasi kampung patriark",
						Component.translatable("kultivator.kampung.patriark.tantang.hover", Component.translatable("kultivator.realm." + patriarch.id())),
						ChatFormatting.GOLD));
			}
			Kartu.kirim(player, Component.translatable("kultivator.kampung.patriark.judul", clan), lines, chips);
		}
	}

	/** {@code /kultivasi kampung patriark}: challenge the patriarch for the clan's pledge. */
	static int tantangPatriark(ServerPlayer player) {
		ServerLevel level = player.level();
		DuniaAnchorEntity anchor = DuniaAnchors.nearest(level, player.position(), "kampung/patriark", 14.0D);
		if (anchor == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.patriark.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		long site = KampungAnchors.situs(anchor);
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.cari(site);
		if (d == null || !d.klan() || Halls.hallOf(player.getUUID()) == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.patriark.jauh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (data.patriarkKalah(d, player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.kampung.klan.ikrar_terbuka").withStyle(ChatFormatting.GREEN));
			return 0;
		}
		KultivatorNpc npc = tubuh(level, anchor, site, NpcRole.PATRIARK_KLAN);
		if (npc == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kampung.patriark.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Component why = TandingTempat.mulai(player, npc, DuelStyle.SANTO_PEDANG, 0xE8C860, Items.IRON_SWORD, true,
				(p, body, menang, kalah) -> hasilPatriark(p, body, site, menang, kalah));
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		npc.say(Component.translatable("kultivator.kampung.patriark.terima"), 100);
		return 1;
	}

	private static void hasilPatriark(ServerPlayer player, KultivatorNpc npc, long site, boolean menang, boolean kalah) {
		KampungData data = KampungData.get(player.level().getServer());
		KampungData.Desa d = data.desa(site);
		if (menang) {
			data.setPatriarkKalah(d, player.getUUID());
			data.ubahSikap(d, player.getUUID(), 20);
			npc.bow(60);
			npc.say(Component.translatable("kultivator.kampung.patriark.kalah"), 140);
			Msg.chat(player, Component.translatable("kultivator.kampung.patriark.menang.chat", KampungNames.nama(d.ukuran, d.situs))
					.withStyle(ChatFormatting.GOLD));
		} else if (kalah) {
			npc.say(Component.translatable("kultivator.kampung.patriark.menang"), 120);
			Msg.chat(player, Component.translatable("kultivator.kampung.patriark.kalah.chat").withStyle(ChatFormatting.YELLOW));
		}
	}

	// ------------------------------------------------------------------ bodies

	/** The clan body of {@code role} near {@code anchor} (a resident of 38NL), else one made here within the village cap. */
	private static @Nullable KultivatorNpc tubuh(ServerLevel level, DuniaAnchorEntity anchor, long site, NpcRole role) {
		KultivatorNpc best = null;
		double bestD = 32.0D * 32.0D;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, anchor.getBoundingBox().inflate(32.0D),
				n -> n.isAlive() && n.role() == role)) {
			double dist = npc.distanceToSqr(anchor);
			if (dist < bestD && !TandingTempat.sibuk(npc)) {
				bestD = dist;
				best = npc;
			}
		}
		if (best != null) {
			return best;
		}
		if (Kampung.tubuhKu(site) >= KampungConfig.get().tubuhMaks) {
			return null;
		}
		KultivatorNpc npc = HidupApi.tubuh(level, site, role, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			Component given = Component.translatable("kultivator.kampung.nama_diri." + Math.floorMod(KampungNames.mix(site ^ role.ordinal()), 12));
			npc.setPlate(Component.translatable(role == NpcRole.PATRIARK_KLAN ? "kultivator.kampung.patriark.plat" : "kultivator.kampung.tuan_muda.plat",
					KampungNames.marga(site), given));
			Kampung.catatTubuh(site, npc);
		}
		return npc;
	}

	// ------------------------------------------------------------------ pledge and tribute

	/** The clan refuses a plaque until its patriarch fell or its regard reached the threshold. */
	private static @Nullable Component vetoIkrar(ServerPlayer player, ServerLevel level, BlockPos bell) {
		if (!KampungConfig.hidup()) {
			return null;
		}
		KampungData data = KampungData.get(level.getServer());
		KampungData.Desa d = data.dekat(bell, 32.0D);
		if (d == null || !d.klan()) {
			return null;
		}
		int need = KampungConfig.get().sikapIkrarKlan;
		if (data.patriarkKalah(d, player.getUUID()) || data.sikap(d, player.getUUID()) >= need) {
			return null;
		}
		return Component.translatable("kultivator.kampung.klan.ikrar_tolak", KampungNames.nama(d.ukuran, d.situs), need,
				data.sikap(d, player.getUUID()));
	}

	/** A pledged clan's cultivation goods (aged herbs, a middle spirit stone), scaled by the cycle's factor. */
	private static void upeti(MinecraftServer server, UUID owner, BlockPos bell, int loyalty, double factor, RandomSource random, List<ItemStack> out) {
		if (!KampungConfig.hidup()) {
			return;
		}
		KampungData.Desa d = KampungData.get(server).dekat(bell, 32.0D);
		if (d == null || !d.klan()) {
			return;
		}
		HerbKind[] kinds = HerbKind.values();
		int herbs = (loyalty >= 70 ? 2 : 1);
		herbs = (int) Math.max(factor < 1.0D ? 0 : 1, Math.round(herbs * factor));
		int age = 60 + Math.min(240, loyalty * 2 + random.nextInt(41));
		for (int i = 0; i < herbs; i++) {
			out.add(SpiritHerbs.create(kinds[random.nextInt(kinds.length)], age));
		}
		if (loyalty >= 50 && (factor >= 1.0D || random.nextBoolean())) {
			out.add(SpiritStones.stack(SpiritStones.MID, factor > 1.0D ? 2 : 1));
		}
	}

	private Klan() {
	}
}
