package dev.kultivator.dunia.tempat.tema.gunung;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.tempat.tema.TemaCommands;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /kultivasi uji tema lift} (DESIGN 38.T.13.2, word {@code lift} of the gunung theme): the cable car and the other
 * mountain mechanisms of the nearest Kota Gunung (within 320 blocks of the caller).
 * <pre>
 * uji tema lift                status of the line (phase, cabin position, riders, stop), the station distances
 * uji tema lift jalan          sends the cabin to the other station now
 * uji tema lift macet          the next climb (or the running one) stops mid-line (Lift Macet)
 * uji tema lift longsor        a rockfall now (needs a player near the lanes)
 * uji tema lift cahaya         forces the Fajar Cahaya Budha
 * uji tema lift lonceng        the noon ceremony of the Lonceng Awan
 * uji tema lift herba          clears today's picked cliff herbs of the caller
 * uji tema lift ziarah         the caller's pilgrimage state (landings lit, Restu days)
 * </pre>
 */
final class GunungCommands {

	static void init() {
		TemaCommands.daftar(GunungData.TEMA, GunungCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("lift")
				.executes(ctx -> status(ctx.getSource()))
				.then(Commands.literal("jalan").executes(ctx -> lakukan(ctx.getSource(), "jalan")))
				.then(Commands.literal("macet").executes(ctx -> lakukan(ctx.getSource(), "macet")))
				.then(Commands.literal("longsor").executes(ctx -> lakukan(ctx.getSource(), "longsor")))
				.then(Commands.literal("cahaya").executes(ctx -> lakukan(ctx.getSource(), "cahaya")))
				.then(Commands.literal("lonceng").executes(ctx -> lakukan(ctx.getSource(), "lonceng")))
				.then(Commands.literal("herba").executes(ctx -> lakukan(ctx.getSource(), "herba")))
				.then(Commands.literal("ziarah").executes(ctx -> lakukan(ctx.getSource(), "ziarah")));
	}

	private static TemaKota.Kota kota(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		return GunungTata.terdekat(level, net.minecraft.core.BlockPos.containing(source.getPosition()), 320);
	}

	private static int status(CommandSourceStack source) {
		TemaKota.Kota kota = kota(source);
		if (kota == null) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.gunung.cmd.tidak_ada"));
			return 0;
		}
		KeretaGantung.Info info = KeretaGantung.info(kota.situsId());
		Vec3 here = source.getPosition();
		Vec3 bawah = GunungTata.dunia(kota, GunungTata.KABIN_BAWAH);
		Vec3 atas = GunungTata.dunia(kota, GunungTata.KABIN_ATAS);
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.gunung.cmd.status", kota.nama(source.getLevel().getSeed()),
				Component.translatable("kultivator.kota_tema.gunung.cmd.fase." + info.fase()), String.format(java.util.Locale.ROOT, "%.2f", info.kabin()),
				info.penumpang(), info.kursi(), info.macet() ? "MACET" : "-", info.ada() ? "ya" : "tidak").withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.gunung.cmd.stasiun", (int) here.distanceTo(bawah), (int) here.distanceTo(atas),
				(int) bawah.x, (int) bawah.y, (int) bawah.z, (int) atas.x, (int) atas.y, (int) atas.z), false);
		return 1;
	}

	private static int lakukan(CommandSourceStack source, String apa) {
		TemaKota.Kota kota = kota(source);
		if (kota == null) {
			source.sendFailure(Component.translatable("kultivator.kota_tema.gunung.cmd.tidak_ada"));
			return 0;
		}
		ServerLevel level = source.getLevel();
		ServerPlayer player = source.getPlayer();
		boolean ok;
		switch (apa) {
			case "jalan" -> ok = KeretaGantung.jalankan(kota.situsId());
			case "macet" -> ok = KeretaGantung.macetkan(kota.situsId());
			case "longsor" -> ok = GunungAcara.runtuh(level, kota) > 0;
			case "cahaya" -> ok = TemaKalender.paksa(level, kota.situsId(), GunungData.TEMA, GunungAcara.CAHAYA_BUDHA, true);
			case "lonceng" -> {
				GunungAcara.upacaraLonceng(level, kota);
				ok = true;
			}
			case "herba" -> {
				if (player == null) {
					return 0;
				}
				CompoundTag p = GunungData.pemain(player);
				p.remove("hb");
				p.remove("hd");
				GunungData.ubah(level.getServer());
				ok = true;
			}
			case "ziarah" -> {
				if (player == null) {
					return 0;
				}
				int bits = Ziarah.bordesMenyala(player);
				source.sendSuccess(() -> Component.translatable("kultivator.kota_tema.gunung.cmd.ziarah", Ziarah.status(player), Integer.bitCount(bits),
						Ziarah.sisaHariRestu(player), Ziarah.jedaHari(player), GunungData.poinPemetik(player)), false);
				return 1;
			}
			default -> ok = false;
		}
		boolean hasil = ok;
		source.sendSuccess(() -> Component.translatable(hasil ? "kultivator.kota_tema.gunung.cmd.ok" : "kultivator.kota_tema.gunung.cmd.gagal", apa)
				.withStyle(hasil ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		return hasil ? 1 : 0;
	}

	private GunungCommands() {
	}
}
