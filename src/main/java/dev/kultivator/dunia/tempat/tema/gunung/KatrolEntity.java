package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@code kultivator:katrol} (DESIGN 38.T.9). Keranjang katrol anyaman (2 orang atau 1 muatan): naik-turun vertikal
 * 30 blok dalam 12 s, empat tali ke roda, drum engkol diputar PENJAGA_LIFT (klip 96 {@code putar_katrol}); gratis.
 * <p>
 * Behaviour (38TK-G): bound to its anchor ({@code tema_gunung/katrol}: {@code naik} blocks up, {@code keluar} exit side),
 * the basket waits at the bottom; a click boards it (two riders), it rises at 2.5 blocks per second (30 blocks in 12 s),
 * puts the riders on the upper landing (two blocks out on the exit side), waits 5 s and comes down; a click at the top
 * takes riders down. Riders are held in {@code tema.lift}, never fall (they are moved with the basket), and a pilgrim's
 * Seribu Anak Tangga is cancelled. The pulley wheel of the same anchor turns while it moves.
 * <p>
 * {@link #nilai()}: tinggi 0..1. Never saved; re-made from its anchor. The renderer (38TV-M) reads {@link #varian()},
 * {@link #keadaan()}, {@link #mulai()} and {@link #nilai()} only.
 */
public class KatrolEntity extends TemaMekanismeEntity {

	/** Anyaman bambu. */
	public static final int VARIAN_BIASA = 0;

	/** Di stasiun bawah. */
	public static final int KEADAAN_BAWAH = 0;
	/** Naik. */
	public static final int KEADAAN_NAIK = 1;
	/** Di stasiun atas. */
	public static final int KEADAAN_ATAS = 2;
	/** Turun. */
	public static final int KEADAAN_TURUN = 3;

	/** Blocks per tick (2.5 blocks per second). */
	public static final double LAJU = 0.125D;
	public static final int TUNGGU = 100;

	private Vec3 bawah = Vec3.ZERO;
	private int naik = 12;
	private Vec3 keluar = new Vec3(0.0D, 0.0D, -1.0D);
	private String nama = "";
	private boolean siap;
	private double tinggi;
	private int tunggu;
	private final Set<UUID> penumpang = new LinkedHashSet<>();
	private final Set<UUID> bawa = new LinkedHashSet<>();

	public KatrolEntity(EntityType<? extends KatrolEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected int kursi() {
		return 2;
	}

	@Override
	protected boolean bisaDipilih() {
		return true;
	}

	/** Binds the basket to its anchor (bottom = anchor position). */
	public void siapkan(DuniaAnchorEntity anchor) {
		this.bawah = new Vec3(anchor.getBlockX() + 0.5D, anchor.getY(), anchor.getBlockZ() + 0.5D);
		this.naik = Math.max(3, Math.min(40, GunungAnchors.angka(anchor, "naik", 12)));
		this.keluar = keluarArah(anchor);
		this.nama = anchor.data().getStringOr("nama", "");
		this.siap = true;
		this.tinggi = 0.0D;
		this.pindah(bawah, anchor.getYRot());
		this.setKeadaan(KEADAAN_BAWAH);
		this.setNilai(0.0F);
	}

	/** The exit direction of a pulley anchor (data {@code keluar}: north/south/east/west, before the town rotation, turned). */
	public static Vec3 keluarArah(DuniaAnchorEntity anchor) {
		Vec3 base = switch (anchor.data().getStringOr("keluar", "north")) {
			case "south" -> new Vec3(0.0D, 0.0D, 1.0D);
			case "east" -> new Vec3(1.0D, 0.0D, 0.0D);
			case "west" -> new Vec3(-1.0D, 0.0D, 0.0D);
			default -> new Vec3(0.0D, 0.0D, -1.0D);
		};
		// the template turned the anchor's yaw by the town rotation; turn the exit the same way (yaw 0 in the template)
		return base.yRot((float) Math.toRadians(-anchor.getYRot()));
	}

	public String nama() {
		return nama;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!siap || keadaan() == KEADAAN_NAIK || keadaan() == KEADAAN_TURUN) {
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.gunung.katrol.berjalan").withStyle(ChatFormatting.GRAY));
			return InteractionResult.CONSUME;
		}
		if (penumpang.contains(sp.getUUID())) {
			return InteractionResult.CONSUME;
		}
		if (penumpang.size() >= kursi()) {
			Msg.actionBar(sp, Component.translatable("kultivator.kota_tema.gunung.katrol.penuh").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.CONSUME;
		}
		if (!sp.startRiding(this)) {
			bawa.add(sp.getUUID());
		}
		penumpang.add(sp.getUUID());
		tunggu = Math.min(tunggu, 30);
		TemaSibuk.tahan(sp, TemaSibuk.LIFT, (int) (naik / LAJU) + 200);
		Ziarah.batal(sp, Ziarah.BATAL_KATROL);
		Msg.actionBar(sp, Component.translatable(keadaan() == KEADAAN_BAWAH ? "kultivator.kota_tema.gunung.katrol.naik"
				: "kultivator.kota_tema.gunung.katrol.turun").withStyle(ChatFormatting.GOLD));
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level) || !siap) {
			return;
		}
		bersihkan(level);
		switch (keadaan()) {
			case KEADAAN_BAWAH, KEADAAN_ATAS -> {
				tunggu++;
				boolean diAtas = keadaan() == KEADAAN_ATAS;
				if (!penumpang.isEmpty() && tunggu >= 40) {
					mulai(level, diAtas ? KEADAAN_TURUN : KEADAAN_NAIK);
				} else if (diAtas && penumpang.isEmpty() && tunggu >= TUNGGU) {
					mulai(level, KEADAAN_TURUN);
				}
			}
			case KEADAAN_NAIK -> {
				tinggi = Math.min(naik, tinggi + LAJU);
				gerak();
				if (tinggi >= naik) {
					tiba(level, true);
				}
			}
			case KEADAAN_TURUN -> {
				tinggi = Math.max(0.0D, tinggi - LAJU);
				gerak();
				if (tinggi <= 0.0D) {
					tiba(level, false);
				}
			}
			default -> {
			}
		}
		bawaPenumpang();
	}

	private void mulai(ServerLevel level, int arah) {
		tunggu = 0;
		setKeadaan(arah);
		putarRoda(arah == KEADAAN_NAIK ? 1 : -1);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
	}

	private void gerak() {
		this.pindah(bawah.add(0.0D, tinggi, 0.0D), getYRot());
		this.setNilai((float) (tinggi / naik));
	}

	private void tiba(ServerLevel level, boolean atas) {
		setKeadaan(atas ? KEADAAN_ATAS : KEADAAN_BAWAH);
		tunggu = 0;
		putarRoda(0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
		Vec3 out = atas ? bawah.add(0.0D, naik, 0.0D).add(keluar.scale(2.0D)) : bawah.subtract(keluar.scale(1.5D));
		for (UUID id : Set.copyOf(penumpang)) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			if (p.getVehicle() == this) {
				p.stopRiding();
			}
			p.teleportTo(out.x, out.y, out.z);
			p.resetFallDistance();
			TemaSibuk.lepas(p, TemaSibuk.LIFT);
			dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.lapor(p, dev.kultivator.dunia.tempat.tema.bersama.TemaCerita.NAIK_LIFT, "katrol:" + GunungData.TEMA);
			CeritaApi.peristiwa(p, "tanda", "gunung_katrol_" + (nama.isEmpty() ? "x" : nama) + (atas ? "_atas" : "_bawah"));
			if (atas) {
				GunungCerita.katrolTiba(p, this);
			}
		}
		penumpang.clear();
		bawa.clear();
	}

	private void bersihkan(ServerLevel level) {
		penumpang.removeIf(id -> {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null || p.level() != level) {
				bawa.remove(id);
				return true;
			}
			boolean di = p.getVehicle() == this || bawa.contains(id);
			if (!di) {
				boolean berjalan = keadaan() == KEADAAN_NAIK || keadaan() == KEADAAN_TURUN;
				if (berjalan && p.position().distanceToSqr(position()) < 16.0D) {
					if (!p.startRiding(this)) {
						bawa.add(id);
					}
					return false;
				}
				TemaSibuk.lepas(p, TemaSibuk.LIFT);
				return true;
			}
			return false;
		});
	}

	private void bawaPenumpang() {
		if (bawa.isEmpty() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		int i = 0;
		for (UUID id : bawa) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			Vec3 seat = position().add(i++ == 0 ? -0.35D : 0.35D, 0.2D, 0.0D);
			if (p.position().distanceToSqr(seat) > 0.02D) {
				p.connection.teleport(new PositionMoveRotation(seat, Vec3.ZERO, 0.0F, 0.0F), EnumSet.of(Relative.Y_ROT, Relative.X_ROT));
			}
			p.resetFallDistance();
		}
	}

	private void putarRoda(int arah) {
		for (DuniaAnchorEntity a : GunungAnchors.dimuat(GunungAnchors.RODA)) {
			if (!nama.isEmpty() && nama.equals(a.data().getStringOr("katrol", "")) && a.distanceToSqr(this) < 48.0D * 48.0D
					&& GunungAnchors.tubuh(a) instanceof RodaKatrolEntity roda) {
				roda.setKeadaan(arah == 0 ? RodaKatrolEntity.KEADAAN_DIAM : RodaKatrolEntity.KEADAAN_PUTAR);
				roda.setNilai(arah);
			}
		}
	}

	/** The riders now in the basket. */
	public int jumlahPenumpang() {
		return penumpang.size();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, this.getPassengers().indexOf(passenger));
		return new Vec3(index == 0 ? -0.35D : 0.35D, 0.2D, 0.0D);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		if (keadaan() == KEADAAN_ATAS) {
			return bawah.add(0.0D, naik, 0.0D).add(keluar.scale(2.0D));
		}
		return keadaan() == KEADAAN_BAWAH ? bawah.subtract(keluar.scale(1.5D)) : position();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	/** The pulley this entity belongs to, for the story (null when unbound). */
	public @Nullable Vec3 atas() {
		return siap ? bawah.add(0.0D, naik, 0.0D) : null;
	}
}
