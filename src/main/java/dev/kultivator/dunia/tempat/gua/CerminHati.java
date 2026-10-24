package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gua Cermin Hati (DESIGN 38.C.4, anchor {@code gua/cermin_kolam} at the meditation point by the still pool): a small
 * cave with a pool of still water over black stone and a mirror of polished blackstone. A cultivator who sits in
 * meditation at the pool is offered "Hadapi Iblis Hati" (a click in chat, {@code /kultivasi cermin}): a heart demon
 * trial of their own choosing ({@code HeartDemons.startVoluntaryTrial}), once per world day, from the suggested realm
 * (Nascent Soul, 38.0 rule 13), never while something else holds the player ({@code Sibuk}). At the mirror the losses
 * weigh half ({@code TempatApi} factor {@code iblis_hati} 0.5); a win clears all heart demon pressure as any won trial
 * does (the designed "−20 more" is part of that). The player is {@code Sibuk} {@code tempat.cermin} meanwhile and the
 * followers wait outside (38.0 rule 18).
 * <p>
 * Sumber ({@code kultivator.gua.cermin_hati.inspired}): inspired by the Heart Mirror and the inner-demon trials of
 * <i>I Shall Seal the Heavens</i>; the design is the game's own.
 */
public final class CerminHati extends GuaRun {

	public static final double DUDUK = 2.5D;
	public static final Realm RANAH = Realm.NASCENT_SOUL;

	/** Players facing their heart demon at a mirror now (memory; a trial ends with the logout anyway). */
	private static final Set<UUID> AKTIF = new HashSet<>();
	private final Map<UUID, Long> tawar = new HashMap<>();

	public CerminHati(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 9.0D;
	}

	/** True while {@code player} faces the mirror's trial. */
	public static boolean aktif(ServerPlayer player) {
		return AKTIF.contains(player.getUUID());
	}

	/** {@code uji tempat_gua ujian cermin}: marks a test trial as the mirror's (no day limit, no realm gate). */
	static void paksa(ServerPlayer player) {
		AKTIF.add(player.getUUID());
	}

	static void lupakan() {
		AKTIF.clear();
	}

	@Override
	protected void spawn() {
	}

	@Override
	protected void nearby(List<ServerPlayer> within) {
		long now = now();
		for (ServerPlayer player : within) {
			if (AKTIF.contains(player.getUUID()) || player.position().distanceToSqr(centre()) > DUDUK * DUDUK
					|| !Cultivators.get(player).meditating()) {
				continue;
			}
			Long last = tawar.get(player.getUUID());
			if (last != null && now - last < 600L) {
				continue;
			}
			tawar.put(player.getUUID(), now);
			Component button = Component.translatable("kultivator.gua.cermin.tombol").withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE)
					.withBold(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi cermin"))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable("kultivator.gua.cermin.tombol.hover"))));
			Msg.chat(player, Gua.acak("kultivator.gua.cermin.tawar", 4).withStyle(ChatFormatting.GRAY).append(" ").append(button));
		}
	}

	/** {@code /kultivasi cermin}: starts the trial at the mirror the player sits by; false with a reason in chat. */
	public static boolean mulai(ServerPlayer player) {
		CerminHati run = GuaRuns.at(player, CerminHati.class);
		if (run == null || player.position().distanceToSqr(run.centre()) > (DUDUK + 1.0D) * (DUDUK + 1.0D)) {
			Msg.chat(player, Component.translatable("kultivator.gua.cermin.jauh").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (!GuaConfig.on()) {
			return false;
		}
		if (!Cultivators.get(player).realm().atLeast(RANAH)) {
			Msg.chat(player, Component.translatable("kultivator.gua.cermin.ranah", RANAH.displayName()).withStyle(ChatFormatting.RED));
			return false;
		}
		GuaData data = GuaData.get(player.level().getServer());
		long today = GuaAnchors.today(player.level());
		if (data.playerLong(player.getUUID(), "cermin") == today) {
			Msg.chat(player, Component.translatable("kultivator.gua.cermin.sudah").withStyle(ChatFormatting.GRAY));
			return false;
		}
		String busy = Sibuk.whyExcept(player, Gua.SIBUK_CERMIN);
		if (busy != null) {
			Msg.chat(player, Sibuk.text(busy).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!HeartDemons.startVoluntaryTrial(player)) {
			Msg.chat(player, Component.translatable("kultivator.gua.cermin.gagal").withStyle(ChatFormatting.GRAY));
			return false;
		}
		data.setPlayerLong(player.getUUID(), "cermin", today);
		AKTIF.add(player.getUUID());
		GuaTempat.parkir(player, run.centre());
		Vec3 c = run.centre();
		ServerLevel level = run.level;
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.AMBIENT, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.3D, c.z, 40, 1.5D, 0.2D, 1.5D, 0.05D);
		CeritaApi.peristiwa(player, "ujian", "cermin_hati");
		return true;
	}

	@Override
	protected void tick(long now) {
		if (now % 10L != 0L || AKTIF.isEmpty()) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (AKTIF.contains(player.getUUID()) && !HeartDemons.inTrial(player)) {
				AKTIF.remove(player.getUUID());
				Msg.chat(player, Component.translatable("kultivator.gua.cermin.usai").withStyle(ChatFormatting.DARK_AQUA));
			}
		}
	}

	/** Every 20 ticks from {@link Gua}: settles trials whose player left the mirror's run (logout, death, far away). */
	static void sapu(net.minecraft.server.MinecraftServer server) {
		if (AKTIF.isEmpty()) {
			return;
		}
		AKTIF.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			return p == null || !HeartDemons.inTrial(p);
		});
	}

	@Override
	protected void despawn() {
		tawar.clear();
		super.despawn();
	}
}
