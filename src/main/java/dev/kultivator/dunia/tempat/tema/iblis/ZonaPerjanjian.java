package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The Market Pact (DESIGN 38.T.7.8 butir 1): inside a Zona Perjanjian ({@code pasar_gelap}, {@code kedai_racun}) no blood
 * is spilled. A blow struck there by a player on anyone who is not a monster is stopped and makes the player a Pact
 * breaker: every enforcer hunts them for one world day ({@code langgar_sampai}) and the Pact Zone is barred to them for
 * three days ({@code zp_larang_sampai}; inside it while barred the enforcers turn on them). A blow struck there on a
 * player by anyone (an NPC, an ambusher, a beast) is stopped too: the only safe ground of the city. Outside the zone the
 * city is not safe ground. PvE only: two players can never be made to fight here.
 */
public final class ZonaPerjanjian {

	public static final long LANGGAR_TICK = 24000L;
	public static final long LARANG_TICK = 3L * 24000L;

	private ZonaPerjanjian() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ZonaPerjanjian::bolehLuka);
	}

	private static boolean bolehLuka(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim.level() instanceof ServerLevel level) || !TemaConfig.aktif(Tema.IBLIS)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == victim) {
			return true;
		}
		if (!IblisKota.zp(level, victim.blockPosition()) && !IblisKota.zp(level, attacker.blockPosition())) {
			return true;
		}
		if (attacker instanceof ServerPlayer player) {
			if (victim instanceof Enemy && !(victim instanceof ServerPlayer)) {
				return true;
			}
			langgar(player);
			return false;
		}
		// nobody hurts a player inside the zone
		return !(victim instanceof ServerPlayer);
	}

	/** Marks a Pact breaker in the town the player stands in. */
	public static void langgar(ServerPlayer player) {
		TemaKota.Kota kota = IblisKota.di(player);
		if (kota == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		boolean baru = !IblisData.sampai(server, kota.situsId(), player.getUUID(), "langgar_sampai");
		IblisData.setSampai(server, kota.situsId(), player.getUUID(), "langgar_sampai", LANGGAR_TICK);
		IblisData.setSampai(server, kota.situsId(), player.getUUID(), "zp_larang_sampai", LARANG_TICK);
		if (baru) {
			Msg.title(player, Component.translatable("kultivator.kota_tema.iblis.zp.langgar").withStyle(ChatFormatting.DARK_RED),
					Component.translatable("kultivator.kota_tema.iblis.zp.langgar.sub").withStyle(ChatFormatting.RED), 5, 70, 20);
		} else {
			Msg.actionBar(player, Component.translatable("kultivator.kota_tema.iblis.zp.darah").withStyle(ChatFormatting.RED));
		}
	}

	/** True while the player is barred from the Pact Zone and stands in it. */
	public static boolean terlarangDiSini(ServerPlayer player, long situsId) {
		return IblisData.sampai(player.level().getServer(), situsId, player.getUUID(), "zp_larang_sampai") && IblisKota.zp(player.level(), player.blockPosition());
	}
}
