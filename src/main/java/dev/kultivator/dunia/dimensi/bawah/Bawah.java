package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.TandaEntity;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Dunia Bawah's part of module D (DESIGN 34.5.13): the soul journey ({@link SoulJourney}), the anchors
 * ({@link BawahAnchors}), the people ({@link BawahPeople}), the Ten Courts ({@link Courts}) and the ghosts' errands
 * ({@link GhostQuests}). Called by {@code Dimensi}.
 */
public final class Bawah {

	public static void init() {
		SoulJourney.init();
	}

	public static void clear() {
		SoulJourney.clear();
		BawahAnchors.clear();
	}

	public static void tick(MinecraftServer server, long ticks) {
		SoulJourney.tick(server, ticks);
		if (ticks % 20L == 3L) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (DimensiLevels.isBawah(player.level())) {
					Courts.tick(player);
				}
			}
		}
		if (ticks % 40L == 11L) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				GhostQuests.deliveryTick(player);
			}
		}
	}

	/** Dizang's shrine (the bodhisattva who vowed to empty the hells): once a day it mends Luka Jiwa and steadies the soul. */
	public static void dizangBlessing(ServerPlayer player, TandaEntity mark) {
		CompoundTag bawah = SoulJourney.data(player);
		long day = DimensiData.day(player.level().getServer());
		if (bawah.getLongOr("dizang_day", -1L) == day) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.dizang.sudah").withColor(0xE8D9A8));
			return;
		}
		bawah.putLong("dizang_day", day);
		bawah.remove("luka");
		DimensiData.dirty(player);
		player.removeEffect(MobEffects.WEAKNESS);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 0));
		DimensiNet.fx(player.level(), DimensiNet.FX_BLESS, mark.position(), 0, 32.0D);
		player.level().playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		Msg.chat(player, Component.translatable("kultivator.dimensi.dizang.berkah").withColor(0xE8D9A8));
		SoulJourney.sendState(player);
	}

	private Bawah() {
	}
}
