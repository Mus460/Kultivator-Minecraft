package dev.kultivator.dunia.tempat.tema.air;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.tema.TemaCommands;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Test words of the Kota Air under {@code /kultivasi uji tema perahu} (DESIGN 38.T.13.2): {@code info} (the town the
 * player stands in: id, rotation, live bodies, running events), {@code pos} (teleport to the Perahu Pos), {@code tur}
 * and {@code sewa} (start the tour / a hired boat, paid by the test), {@code tikus}, {@code naga}, {@code lentera}
 * (force the event now), {@code arwah} (raise the lost souls), {@code anak} (the child of the side quest falls in now).
 * None of them touches a block, the weather or the time.
 */
final class AirCommands {

	static void init() {
		TemaCommands.daftar(AirKota.TEMA, AirCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("perahu")
				.executes(AirCommands::info)
				.then(Commands.literal("info").executes(AirCommands::info))
				.then(Commands.literal("pos").executes(AirCommands::pos))
				.then(Commands.literal("tur").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					NilaiBatu.give(p, Perahu.HARGA_TUR);
					return Perahu.mulaiTur(p, null) ? 1 : 0;
				}))
				.then(Commands.literal("sewa").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					NilaiBatu.give(p, Perahu.HARGA_SEWA);
					return Perahu.sewa(p) ? 1 : 0;
				}))
				.then(Commands.literal("tikus").executes(ctx -> paksa(ctx, TikusAir.ACARA)))
				.then(Commands.literal("naga").executes(ctx -> paksa(ctx, LombaPerahuNaga.ACARA)))
				.then(Commands.literal("lentera").executes(ctx -> paksa(ctx, LenteraAir.ACARA)))
				.then(Commands.literal("arwah").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					AirKota.Hidup kota = AirKota.di(p);
					if (kota == null) {
						return luar(ctx);
					}
					for (int i = 0; i < 20; i++) {
						LenteraAir.arwah(p.level().getServer().overworld(), kota);
					}
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota_tema.air.cmd.arwah", LenteraAir.jumlahArwah(kota.situs)), false);
					return 1;
				}))
				.then(Commands.literal("anak").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					AirCerita.paksaAnak(p);
					return 1;
				}));
	}

	private static int luar(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Component.translatable("kultivator.kota_tema.air.cmd.luar"));
		return 0;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		AirKota.Hidup kota = AirKota.di(p);
		if (kota == null) {
			return luar(ctx);
		}
		String distrik = kota.distrik(p.blockPosition());
		double[] l = kota.lokal(p.position());
		StringBuilder acara = new StringBuilder();
		for (String id : new String[] {LenteraAir.ACARA, LombaPerahuNaga.ACARA, TikusAir.ACARA}) {
			if (TemaKalender.berjalan(kota.situs, AirKota.TEMA, id)) {
				acara.append(id).append(' ');
			}
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota_tema.air.cmd.info", Long.toString(kota.situs), kota.kota.putar(),
				distrik == null ? "-" : distrik, Math.round(l[0]), Math.round(l[1]), TemaMekanisme.hidup(kota.situs).size(),
				acara.isEmpty() ? "-" : acara.toString().trim()).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int pos(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		AirKota.Hidup kota = AirKota.di(p);
		if (kota == null) {
			return luar(ctx);
		}
		ServerLevel level = p.level().getServer().overworld();
		double[] at = Perahu.posPada(level.getGameTime() + (kota.situs & 0xFFFL));
		Vec3 w = JalurKanal.dunia(kota, JalurKanal.LINTAS_POS, at[0], 0.0D);
		p.teleportTo(w.x + 2.0D, kota.lantai() + 0.1D, w.z + 2.0D);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota_tema.air.cmd.pos", Math.round(at[0]),
				Math.round(JalurKanal.LINTAS_POS.panjang())), false);
		return 1;
	}

	private static int paksa(CommandContext<CommandSourceStack> ctx, String acara) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		AirKota.Hidup kota = AirKota.di(p);
		if (kota == null) {
			return luar(ctx);
		}
		boolean ok = TemaKalender.paksa(p.level().getServer().overworld(), kota.situs, AirKota.TEMA, acara, true);
		ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.kota_tema.air.cmd.acara" : "kultivator.kota_tema.air.cmd.acara_gagal", acara), false);
		return ok ? 1 : 0;
	}

	private AirCommands() {
	}
}
