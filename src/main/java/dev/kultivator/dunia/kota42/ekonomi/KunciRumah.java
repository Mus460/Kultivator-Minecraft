package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code kultivator:kunci_rumah} (DESIGN 42.6.1): the key of one house ({@code KotaEkonomiItems.RUMAH}). Used by its
 * owner while he still holds the house, it starts a 3 s channel (standing still, not busy: {@code Sibuk.why}); at the end
 * he stands at the house's door. Cooldown {@code kotaEkonomi.kunciJedaMenit} minutes, free, only in the overworld (never
 * from another dimension or a secret realm). Moving more than half a block or taking damage breaks the channel.
 */
public final class KunciRumah extends Item {

	/** Channel length in ticks. */
	public static final int KANAL_TICK = 60;
	/** Busy state while channelling ({@code kultivator.sibuk.kota.kunci}). */
	public static final String SIBUK = "kota.kunci";

	private record Kanal(long situs, int id, Vec3 dari, float darah, long selesai) {
	}

	private static final Map<UUID, Kanal> KANAL = new ConcurrentHashMap<>();
	private static boolean registered;

	public KunciRumah(Item.Properties properties) {
		super(properties);
	}

	/** From {@code KotaEkonomi.init}: the busy state. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		Sibuk.register(SIBUK, player -> KANAL.containsKey(player.getUUID()));
	}

	static void lupakan() {
		KANAL.clear();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		mulai(sp, player.getItemInHand(hand));
		return InteractionResult.CONSUME;
	}

	/** Starts the channel of key {@code stack} for its owner (checks everything first). */
	static void mulai(ServerPlayer player, ItemStack stack) {
		MinecraftServer server = player.level().getServer();
		KotaEkonomiItems.Rumah tanda = stack.get(KotaEkonomiItems.RUMAH);
		if (!KotaConfig.ekonomi().aktif || !KotaConfig.ekonomi().rumah) {
			tolak(player, "mati");
			return;
		}
		if (tanda == null || !player.getUUID().equals(tanda.pemilik())) {
			tolak(player, "bukan_milik");
			return;
		}
		RumahKota.Info info = RumahKota.info(server, tanda.situs(), tanda.rumah());
		if (info == null || !player.getUUID().equals(info.pemilik()) || RumahKota.ST_SEGEL.equals(info.status())) {
			tolak(player, "bukan_milik");
			return;
		}
		if (player.level().dimension() != Level.OVERWORLD) {
			tolak(player, "dimensi");
			return;
		}
		if (KANAL.containsKey(player.getUUID())) {
			return;
		}
		String sibuk = Sibuk.why(player);
		if (sibuk != null) {
			Msg.actionBar(player, Sibuk.text(sibuk).withStyle(ChatFormatting.RED));
			return;
		}
		long now = server.overworld().getGameTime();
		long jeda = KotaConfig.ekonomi().kunciJedaMenit * 1200L;
		long last = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH).getCompoundOrEmpty("jeda").getLongOr(player.getUUID().toString(), -jeda);
		if (now - last < jeda) {
			long menit = (jeda - (now - last) + 1199L) / 1200L;
			tolak(player, "jeda", menit);
			return;
		}
		KANAL.put(player.getUUID(), new Kanal(tanda.situs(), tanda.rumah(), player.position(), player.getHealth(), now + KANAL_TICK));
		Msg.actionBar(player, Component.translatable("kultivator.kota42.kunci.mulai", RumahKota.nama(server, info)).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	/** Every tick from {@code KotaEkonomi}: channels run, break or arrive. */
	static void tick(MinecraftServer server) {
		if (KANAL.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Map.Entry<UUID, Kanal> e : KANAL.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Kanal k = e.getValue();
			if (player == null || player.isDeadOrDying() || player.level().dimension() != Level.OVERWORLD) {
				KANAL.remove(e.getKey());
				continue;
			}
			if (player.position().distanceToSqr(k.dari()) > 0.25D || player.getHealth() < k.darah()) {
				KANAL.remove(e.getKey());
				tolak(player, "putus");
				continue;
			}
			ServerLevel level = player.level();
			if ((now & 3L) == 0L) {
				level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.4D, 0.6D, 0.4D, 0.2D);
			}
			if (now < k.selesai()) {
				continue;
			}
			KANAL.remove(e.getKey());
			RumahKota.Info info = RumahKota.info(server, k.situs(), k.id());
			if (info == null || !player.getUUID().equals(info.pemilik())) {
				tolak(player, "bukan_milik");
				continue;
			}
			BlockPos door = info.pintu();
			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.4D);
			player.teleportTo(door.getX() + 0.5D, door.getY(), door.getZ() + 0.5D);
			player.setYRot(info.hadap().getOpposite().toYRot());
			level.playSound(null, door, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.0F);
			net.minecraft.nbt.CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.RUMAH);
			net.minecraft.nbt.CompoundTag jeda = part.getCompoundOrEmpty("jeda");
			jeda.putLong(player.getUUID().toString(), now);
			part.put("jeda", jeda);
			KotaEkonomiData.get(server).ubah();
			Msg.actionBar(player, Component.translatable("kultivator.kota42.kunci.tiba", RumahKota.nama(server, info)).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void tolak(ServerPlayer player, String sebab, Object... isian) {
		Msg.actionBar(player, Component.translatable("kultivator.kota42.kunci.gagal." + sebab, isian).withStyle(ChatFormatting.RED));
	}
}
