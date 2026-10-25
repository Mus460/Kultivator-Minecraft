package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.peta.PetaDomains;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which sects a player knows (DESIGN 41.5.1). Full knowledge is {@code Keanggotaan.dikenal} (class, style, requirements,
 * place); "heard of" is a second bitset kept here ({@code bagian("masuk").didengar}: name, class, rough direction, test
 * day). The directory screen (41U-1) shows a known sect in full, a heard one with its rumour line and the rest as "?".
 * <ul>
 * <li><b>Visit</b>: {@code DiplomasiService.meet} (gate, compound, cloud palace within 96 blocks) is mirrored into the
 * directory every 10 s ({@link #sinkron}).</li>
 * <li><b>Rumour</b>: the tea master of a Kedai Teh ({@code PEMILIK_TEH}) or an innkeeper, talked to once a day, whispers
 * which sect opens its gates next ("Sekte X membuka penerimaan pada hari ke-n, di arah timur laut").</li>
 * <li><b>Map</b>: standing inside a sect's map domain ({@code sekte:s<i>}, {@code PetaDomains}) means its name is heard.</li>
 * <li><b>Recruiter</b> and <b>envoy</b>: {@link Perekrut} and {@link TokenNaik#utusan} make the sect known.</li>
 * <li><b>Ranking</b>: 41D's ranking board calls {@link #dengar}.</li>
 * </ul>
 */
public final class Direktori {

	/** Directory mirror period. */
	public static final int SINKRON_TICK = 200;
	private static final String DIDENGAR = "didengar", KABAR_HARI = "kabar_hari";

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SINKRON_TICK == 13 && Gabung.aktif()) {
				sinkron(server);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof KultivatorNpc npc && Gabung.aktif()
					&& (npc.role() == NpcRole.PEMILIK_TEH || npc.role() == NpcRole.PEMILIK_PENGINAPAN)) {
				bisikKabar(sp, npc);
			}
			return InteractionResult.PASS;
		});
	}

	// ================================================================== reads

	/** True when {@code sekte} is at least heard of (known sects count). */
	public static boolean didengar(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		if (e == null || k == null) {
			return false;
		}
		return k.kenal(e.index()) || (k.bagian(Putusan.B).getLongOr(DIDENGAR, 0L) & (1L << e.index())) != 0L;
	}

	/** Marks {@code sekte} as heard of; true when it was new (a known sect stays known). */
	public static boolean dengar(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		if (e == null || e.index() < 0 || e.index() >= 64) {
			return false;
		}
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		if (k.kenal(e.index())) {
			return false;
		}
		CompoundTag bag = k.bagian(Putusan.B);
		long bits = bag.getLongOr(DIDENGAR, 0L);
		if ((bits & (1L << e.index())) != 0L) {
			return false;
		}
		bag.putLong(DIDENGAR, bits | 1L << e.index());
		data.setDirty();
		return true;
	}

	/** The 8-sector direction 0 = north, clockwise, from {@code from} to {@code to}. */
	public static int arah(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		return (int) Math.floorMod(Math.round(deg / 45.0D), 8L);
	}

	/** The direction's name ({@code kultivator.gabung.masuk.arah.<0-7>}). */
	public static Component namaArah(int arah) {
		return Masuk.t("arah." + Math.floorMod(arah, 8));
	}

	// ================================================================== visits and map domains

	private static void sinkron(MinecraftServer server) {
		DiplomasiService d = SekteApi.diplomasi();
		ServerLevel overworld = server.overworld();
		List<PetaDomains.Domain> domains = null;
		GerbangPlan.Plan plan = GerbangPlan.of(overworld.getSeed());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Keanggotaan k = GabungData.get(server).peek(p.getUUID());
			for (GerbangPlan.Entry e : plan.entries()) {
				if ((k == null || !k.kenal(e.index())) && d.met(p.getUUID(), e.id())) {
					if (GabungApi.kenal(p, e.id())) {
						Msg.chat(p, Masuk.t("direktori.kenal", Masuk.nama(e.id())).withStyle(ChatFormatting.AQUA));
					}
				}
			}
			if (p.level() == overworld) {
				if (domains == null) {
					domains = PetaDomains.of(overworld, 64);
				}
				PetaDomains.Domain here = PetaDomains.at(domains, p.blockPosition());
				if (here != null && here.id().startsWith("sekte:")) {
					String sekte = here.id().substring("sekte:".length());
					if (dengar(p, sekte)) {
						Msg.actionBar(p, Masuk.t("direktori.wilayah", here.name()).withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
	}

	// ================================================================== the tea house rumour

	/** Once a day per player: the tea master tells of the next sect test he heard of, and the sect is heard. */
	private static void bisikKabar(ServerPlayer player, KultivatorNpc npc) {
		MinecraftServer server = player.level().getServer();
		long hari = Kota.day(server);
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		CompoundTag bag = k.bagian(Putusan.B);
		if (bag.getLongOr(KABAR_HARI, -1L) == hari) {
			return;
		}
		String sekte = pilihKabar(server, player, k);
		if (sekte == null) {
			return;
		}
		bag.putLong(KABAR_HARI, hari);
		data.setDirty();
		dengar(player, sekte);
		DiplomasiService d = SekteApi.diplomasi();
		BlockPos gate = d.gate(sekte) != null ? d.gate(sekte) : d.plannedGate(sekte);
		Component arah = gate == null ? Masuk.t("arah.tak_tentu") : namaArah(arah(player.blockPosition(), gate));
		long next = HariUjian.berikutUntuk(server, sekte);
		Component line = Masuk.t("direktori.bisik", Masuk.nama(sekte), GabungApi.kelasSekte(sekte).nama(), next, arah);
		npc.say(line, 120);
		Msg.chat(player, Masuk.t("direktori.bisik_chat", npc.plate(), line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** The rumour's sect: the one with the nearest test day among those the player does not know yet (else any). */
	private static @Nullable String pilihKabar(MinecraftServer server, ServerPlayer player, Keanggotaan k) {
		List<String> unknown = new ArrayList<>();
		List<String> all = new ArrayList<>();
		for (GerbangPlan.Entry e : GerbangPlan.of(server.overworld().getSeed()).entries()) {
			if (SekteApi.diplomasi().tier(e.id()) <= 0) {
				continue;
			}
			all.add(e.id());
			if (!k.kenal(e.index())) {
				unknown.add(e.id());
			}
		}
		List<String> pool = unknown.isEmpty() ? all : unknown;
		String best = null;
		long bestDay = Long.MAX_VALUE;
		for (String s : pool) {
			long day = HariUjian.berikutUntuk(server, s);
			if (day >= 0L && day < bestDay) {
				best = s;
				bestDay = day;
			}
		}
		return best;
	}

	private Direktori() {
	}
}
