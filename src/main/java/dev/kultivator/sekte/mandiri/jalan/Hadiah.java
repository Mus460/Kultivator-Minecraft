package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Gifts brought home (DESIGN 39.6.7 step 3): the bond grows +2 for a friend, +4 for a close friend, +6 for a sweetheart
 * or Dao partner ({@code HubunganService.adjust}, reason {@code mandiri.hadiah}), both remember it; a friend standing near
 * gets the gift in hand (a bow, the gift floats over with a glint, into its bag), one who is away finds it in the Lumbung.
 * A gift for the master goes into his inventory when he is within 16 blocks (else the Lumbung) and honours the disciple
 * (+2).
 */
final class Hadiah {

	/** Gives one gift {@code {stack, ke}}; {@code diTangan}: the receiver stands beside the giver (the scene walked there). */
	static void beri(DiscipleEntity murid, CompoundTag gift, boolean diTangan) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return;
		}
		UUID owner = murid.ownerId();
		ItemStack stack = Kepulangan.stackDari(level.registryAccess(), gift.getCompoundOrEmpty("stack"));
		if (stack.isEmpty()) {
			return;
		}
		String ke = gift.getStringOr("ke", DaftarBelanja.KE_GURU);
		if (DaftarBelanja.KE_GURU.equals(ke)) {
			guru(murid, level, owner, stack);
			return;
		}
		UUID friend = Rencana.uuid(ke);
		if (friend == null) {
			LumbungOverflow.store(owner, stack);
			return;
		}
		Ikatan bond = SekteApi.hubungan().kind(owner, murid.soulId(), friend);
		int delta = bond == Ikatan.KEKASIH || bond == Ikatan.PASANGAN_DAO ? 6 : bond == Ikatan.SAHABAT || bond == Ikatan.SAUDARA_ANGKAT ? 4 : 2;
		SekteApi.hubungan().adjust(owner, murid.soulId(), friend, delta, "mandiri.hadiah");
		Ingatan.remember(level.getServer(), murid.data(), "mandiri.hadiah_beri", friend, 2, JalanIds.k("ingatan.hadiah_beri"), "");
		DiscipleEntity other = Perencana.cari(owner, friend);
		if (other != null && other.isAlive()) {
			Ingatan.remember(level.getServer(), other.data(), "mandiri.hadiah_terima", murid.soulId(), 3, JalanIds.k("ingatan.hadiah_terima"), "");
			if (diTangan || other.distanceToSqr(murid) < 12.0D * 12.0D) {
				efek(level, murid, other);
				SosokApi.gerak(murid, GerakId.HORMAT, 0, 30);
				SosokApi.gerak(other, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 30);
				SekteSuara.line(murid, "hadiah", "greet", 200);
				ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
				if (master != null && master.level() == level && master.distanceToSqr(murid) <= 24.0D * 24.0D) {
					Msg.chat(master, teman(murid, other, stack).withColor(JalanIds.WARNA_ABU));
				}
			}
			if (!MandiriApi.masukTas(other.data(), level.registryAccess(), stack)) {
				LumbungOverflow.store(owner, stack);
			}
		} else {
			LumbungOverflow.store(owner, stack);
		}
	}

	private static void guru(DiscipleEntity murid, ServerLevel level, UUID owner, ItemStack stack) {
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
		if (master != null && master.level() == level && master.distanceToSqr(murid) <= 16.0D * 16.0D) {
			ItemStack rest = stack.copy();
			master.getInventory().add(rest);
			if (!rest.isEmpty()) {
				LumbungOverflow.store(owner, rest);
			}
			efek(level, murid, master);
			SosokApi.gerak(murid, GerakId.HORMAT_DALAM, 0, 40);
			SekteSuara.line(murid, "pulang_belanja", "thanks", 0);
			SekteBus.honored(level.getServer(), owner, murid.soulId(), "mandiri_hadiah_guru", 2);
			Msg.chat(master, JalanIds.t("hadiah.guru", murid.getName().copy().withColor(JalanIds.WARNA_NAMA), stack.getHoverName())
					.withColor(JalanIds.WARNA_EMAS));
		} else {
			LumbungOverflow.store(owner, stack);
			if (master != null) {
				Msg.chat(master, JalanIds.t("hadiah.guru_lumbung", murid.getName().copy().withColor(JalanIds.WARNA_NAMA), stack.getHoverName())
						.withColor(JalanIds.WARNA_ABU));
			}
		}
	}

	/** The gift floats from the giver's hands to the receiver with a soft chime and a glint. */
	private static void efek(ServerLevel level, DiscipleEntity giver, @Nullable net.minecraft.world.entity.Entity receiver) {
		Vec3 from = giver.position().add(0.0D, 1.2D, 0.0D);
		Vec3 to = receiver == null ? from : receiver.position().add(0.0D, 1.2D, 0.0D);
		Fx.beam(level, from, to, 0xFFE9A8, 0.15F);
		Fx.burst(level, to, 0xFFD27A, 10, 0.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.6F, 1.4F);
	}

	private Hadiah() {
	}

	/** "<giver> memberikan <item> kepada <friend>." for a master watching nearby. */
	private static net.minecraft.network.chat.MutableComponent teman(DiscipleEntity giver, DiscipleEntity friend, ItemStack stack) {
		return JalanIds.t("hadiah.teman", giver.getName().copy().withColor(JalanIds.WARNA_NAMA), friend.getName().copy().withColor(JalanIds.WARNA_NAMA),
				stack.getHoverName());
	}
}
