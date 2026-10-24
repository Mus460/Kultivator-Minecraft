package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code kultivator:herba_tebing} (DESIGN 38.T.9). Herba tebing berkilau di ceruk dan tepi jalan papan (12 titik
 * per kota, muncul ulang tiap hari per pemain): tanaman silang dan kuncup pancar yang bergoyang angin; menghilang
 * bagi pemain yang sudah memetik.
 * <p>
 * Behaviour (38TK-G, 38.T.6.4): bound to anchor {@code tema_gunung/herba} (no 1-12). A click picks it once per player per
 * world day: a mountain spirit herb aged 60-300 years (the M3 cap of 38.0), 5 % the Rumput Awan Seribu Tahun (age 300),
 * one Pemetik Tebing point. The body is then hidden for that player ({@link #broadcastToPlayer} plus a remove packet)
 * until the next day. Pickers' rank opens the herbs: rank 1 the seven of the cliff district, rank 2 also the summit pair,
 * rank 3 all twelve. A Tali Panjat in the inventory is armed on picking (one fall without damage in a Kota Gunung).
 * <p>
 * {@link #nilai()}: goyang angin 0..1. Never saved. The renderer (38TV-M) reads {@link #varian()}, {@link #keadaan()},
 * {@link #mulai()} and {@link #nilai()} only.
 */
public class HerbaTebingEntity extends TemaMekanismeEntity {

	/** Herba gunung. */
	public static final int VARIAN_BIASA = 0;
	/** Rumput Awan Seribu Tahun. */
	public static final int VARIAN_RUMPUT_AWAN = 1;

	/** Bergoyang. */
	public static final int KEADAAN_TUMBUH = 0;
	/** Skala ke 0. */
	public static final int KEADAAN_DIPETIK = 1;

	public static final int UMUR_MIN = 60;
	public static final int UMUR_MAKS = 300;
	public static final double PELUANG_RUMPUT_AWAN = 0.05D;

	private int no;
	private long situsId;

	public HerbaTebingEntity(EntityType<? extends HerbaTebingEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	public void siapkan(DuniaAnchorEntity anchor) {
		this.no = Math.max(1, Math.min(12, GunungAnchors.angka(anchor, "no", 1)));
		TemaKota.Kota kota = GunungAnchors.kota(anchor);
		this.situsId = kota == null ? 0L : kota.situsId();
		this.setVarian(no == 7 || no == 12 ? VARIAN_RUMPUT_AWAN : VARIAN_BIASA);   // the two hardest spots shimmer gold
		this.setNilai((float) ((no * 0.37D) % 1.0D));
	}

	public int no() {
		return no;
	}

	/** The rank needed for herb {@code no}. */
	public static int pangkatPerlu(int no) {
		return no <= 7 ? 1 : no <= 9 ? 2 : 3;
	}

	/** True when {@code player} already picked herb {@code no} of town {@code situsId} today. */
	public static boolean sudah(ServerPlayer player, long situsId, int no) {
		CompoundTag p = GunungData.pemain(player);
		long hari = TemaKalender.hari((ServerLevel) player.level());
		if (p.getLongOr("hd", -1L) != hari) {
			return false;
		}
		return (p.getCompoundOrEmpty("hb").getIntOr(Long.toString(situsId), 0) & (1 << (no - 1))) != 0;
	}

	private static void tandai(ServerPlayer player, long situsId, int no) {
		CompoundTag p = GunungData.pemain(player);
		long hari = TemaKalender.hari((ServerLevel) player.level());
		if (p.getLongOr("hd", -1L) != hari) {
			p.putLong("hd", hari);
			p.put("hb", new CompoundTag());
		}
		CompoundTag hb = p.getCompoundOrEmpty("hb");
		String key = Long.toString(situsId);
		hb.putInt(key, hb.getIntOr(key, 0) | (1 << (no - 1)));
		p.put("hb", hb);
		GunungData.ubah(player.level().getServer());
	}

	@Override
	public boolean broadcastToPlayer(ServerPlayer player) {
		return no <= 0 || !sudah(player, situsId, no);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp) || !(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (sudah(sp, situsId, no)) {
			sp.connection.send(new ClientboundRemoveEntitiesPacket(this.getId()));
			return InteractionResult.CONSUME;
		}
		int pangkat = GunungData.pangkatPemetik(GunungData.poinPemetik(sp));
		if (pangkat < pangkatPerlu(no)) {
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.gunung.herba.pangkat", pangkatPerlu(no)).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		petik(sp, level);
		return InteractionResult.SUCCESS_SERVER;
	}

	private void petik(ServerPlayer player, ServerLevel level) {
		tandai(player, situsId, no);
		ItemStack hasil;
		boolean langka = player.getRandom().nextDouble() < PELUANG_RUMPUT_AWAN;
		if (langka) {
			hasil = new ItemStack(TemaItems.RUMPUT_AWAN_SERIBU_TAHUN);
		} else {
			Element unsur = player.getRandom().nextBoolean() ? Element.WOOD : Element.WIND;
			hasil = SpiritHerbs.randomFor(unsur, player.getRandom(), UMUR_MIN, UMUR_MAKS);
		}
		Barang.beri(player, hasil);
		GunungData.tambahPoinPemetik(player, langka ? 5 : 1);
		armTaliPanjat(player);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.5D, getZ(), 6, 0.3D, 0.3D, 0.3D, 0.0D);
		Msg.actionBar(player, Component.translatable(langka ? "kultivator.kota_tema.gunung.herba.langka" : "kultivator.kota_tema.gunung.herba.petik",
				hasil.getHoverName(), SpiritHerbs.age(hasil)).withStyle(langka ? ChatFormatting.GOLD : ChatFormatting.GREEN));
		CeritaApi.peristiwa(player, "tanda", "gunung_petik");
		if (langka) {
			CeritaApi.peristiwa(player, "tanda", "gunung_rumput_awan");
		}
		player.connection.send(new ClientboundRemoveEntitiesPacket(this.getId()));
	}

	/** One Tali Panjat in the inventory is armed (consumed) on picking: it saves the next fall in a Kota Gunung. */
	private static void armTaliPanjat(ServerPlayer player) {
		CompoundTag p = GunungData.pemain(player);
		if (p.getBooleanOr("tp", false)) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(TemaItems.TALI_PANJAT)) {
				s.shrink(1);
				p.putBoolean("tp", true);
				GunungData.ubah(player.level().getServer());
				Msg.actionBar(player, Component.translatable("kultivator.kota_tema.gunung.herba.tali_panjat").withStyle(ChatFormatting.AQUA));
				return;
			}
		}
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
