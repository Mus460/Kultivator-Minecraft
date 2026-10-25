package dev.kultivator.sekte.gabung.masuk.corak;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * BINATANG "Taklukkan Binatang" (DESIGN 41.5.4; the beast-taming sects): a spirit beast of the land waits in the pen
 * ({@code kompleks/kandang}): a grey wolf of the region, or a polar bear where the snow lies (white-rabbit biomes). Its
 * body has the spirit-beast tier of the sect's class ({@link SpiritBeasts#applyStats}). Bring it down to 20 % health
 * <b>without killing it</b> (a blow that would kill it does kill it, and the test is failed: the hand must soften at the
 * end), then feed it the food it loves by hand (wolf: meat, bear: fish; one portion is handed out). It never leaves the
 * pen: a beast more than 10 blocks out is set back in.
 * <p>
 * {@code S2} 1.0 when tamed; 0.5 when the subduing blow came from the player's own spirit beast of tier &ge; class (the
 * design's alternative: "the beast bows to a stronger beast, not to you"). A kill = 0.
 */
public final class TaklukkanBinatang extends UjianDasar {

	private static final float TUNDUK = 0.20F;
	private static final double KANDANG = 10.0D;

	private @Nullable Mob binatang;
	private boolean beruang;
	private boolean tunduk;
	private boolean bantuan;
	private int pesanJeda;

	public TaklukkanBinatang(ServerPlayer pemain, String sekte, int kelas, Vec3 pusat, long benih) {
		super(pemain, sekte, Corak.BINATANG, kelas, pusat, benih);
	}

	@Override
	public @Nullable Component siapkan() {
		if (sisaAnggaran() < 1) {
			return t("anggaran_kurang", 1);
		}
		BlockPos at = BlockPos.containing(pusat);
		beruang = level.getBiome(at).is(BiomeTags.SPAWNS_WHITE_RABBITS);
		EntityType<? extends Mob> jenis = beruang ? EntityTypes.POLAR_BEAR : EntityTypes.WOLF;
		Mob mob = jenis.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return t("tidak_bisa");
		}
		Vec3 spot = dariPusat(0.0D, 0.0D);
		mob.snapTo(spot.x, spot.y, spot.z, yaw(spot, pemain.position()), 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setPersistenceRequired();
		int tier = Math.max(1, Math.min(BeastData.TOP_TIER, kelas));
		SpiritBeasts.applyStats(mob, tier, true);
		mob.setCustomName(t(ChatFormatting.GOLD, beruang ? "taklukkan_binatang.nama_beruang" : "taklukkan_binatang.nama_serigala", tier));
		mob.addTag("kultivator.gabung_ujian");
		TechniquePower.markRealmFoe(mob);
		if (!muncul(mob)) {
			return t("anggaran_kurang", 1);
		}
		binatang = mob;
		mob.setTarget(pemain);
		ItemStack pakan = beruang ? new ItemStack(Items.SALMON) : new ItemStack(Items.BEEF);
		beri(itemUjian(pakan, "pakan", t(ChatFormatting.YELLOW, "taklukkan_binatang.pakan"), t(ChatFormatting.GRAY, "taklukkan_binatang.pakan_lore")));
		chat(t(ChatFormatting.GOLD, beruang ? "taklukkan_binatang.mulai_beruang" : "taklukkan_binatang.mulai_serigala"));
		return null;
	}

	@Override
	public void tik() {
		Mob b = binatang;
		if (b == null || b.isRemoved()) {
			if (!selesai()) {
				akhiri(0.0D, "rusak", true);
			}
			return;
		}
		if (pesanJeda > 0) {
			pesanJeda--;
		}
		if (b.position().distanceToSqr(pusat) > KANDANG * KANDANG) {
			Vec3 back = dariPusat(0.0D, 0.0D);
			b.teleportTo(back.x, back.y, back.z);
		}
		if (!tunduk) {
			if (b.getTarget() != pemain) {
				b.setTarget(pemain);
			}
			skor = 0.0D;
		}
	}

	@Override
	public boolean bolehLuka(LivingEntity korban, DamageSource sumber, float jumlah) {
		if (korban != binatang) {
			return true;
		}
		Mob b = binatang;
		if (tunduk) {
			if (pesanJeda <= 0 && olehPemain(sumber)) {
				bar(t(ChatFormatting.RED, "taklukkan_binatang.jangan"));
				pesanJeda = 40;
			}
			return false;
		}
		float sisa = b.getHealth() - jumlah;
		if (sisa <= 0.0F) {
			return true; // too heavy a hand: it dies, mati() fails the test
		}
		if (sisa <= b.getMaxHealth() * TUNDUK) {
			b.setHealth(sisa);
			tunduk = true;
			Entity pelaku = sumber.getEntity();
			bantuan = pelaku instanceof Mob own && own != b && bantuanSah(own);
			b.setTarget(null);
			b.setNoAi(true);
			if (b instanceof Wolf wolf) {
				wolf.setInSittingPose(true);
			}
			bunyi(b.position(), beruang ? SoundEvents.POLAR_BEAR_WARNING : SoundEvents.WOLF_WHINE_BABY.value(), 1.0F, 0.6F);
			chat(t(ChatFormatting.GREEN, bantuan ? "taklukkan_binatang.tunduk_bantuan" : "taklukkan_binatang.tunduk"));
			skor = bantuan ? 0.5D : 1.0D;
			return false;
		}
		return true;
	}

	/** The subduing blow came from the player's own spirit beast of tier &ge; the class. */
	private boolean bantuanSah(Mob own) {
		ServerPlayer owner = SpiritBeasts.ownerOf(own);
		BeastData data = SpiritBeasts.data(own);
		return owner != null && owner.getUUID().equals(uuid()) && data != null && data.tier() >= kelas;
	}

	@Override
	public void mati(LivingEntity korban, DamageSource sumber) {
		if (korban == binatang) {
			chat(t(ChatFormatting.DARK_RED, "taklukkan_binatang.terbunuh"));
			akhiri(0.0D, "bunuh_binatang", true);
		}
	}

	@Override
	public InteractionResult pakaiEntitas(Entity entity, InteractionHand hand) {
		if (entity != binatang || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack s = pemain.getMainHandItem();
		boolean suka = beruang ? s.is(Items.SALMON) || s.is(Items.COD) || s.is(Items.COOKED_SALMON) || s.is(Items.COOKED_COD) : s.is(ItemTags.WOLF_FOOD);
		if (!suka) {
			bar(t(ChatFormatting.YELLOW, "taklukkan_binatang.bukan_pakan"));
			return InteractionResult.FAIL;
		}
		if (!tunduk) {
			bar(t(ChatFormatting.RED, "taklukkan_binatang.belum_tunduk"));
			return InteractionResult.FAIL;
		}
		if (!pemain.isCreative()) {
			s.shrink(1);
		}
		bunyi(entity.position(), SoundEvents.PLAYER_BURP, 0.8F, 0.8F);
		chat(t(ChatFormatting.GREEN, "taklukkan_binatang.jinak"));
		akhiri(bantuan ? 0.5D : 1.0D, "tuntas", false);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void waktuHabis() {
		// subdued but not fed: half the bow is there
		akhiri(tunduk ? (bantuan ? 0.25D : 0.5D) : 0.0D, "waktu", false);
	}

	@Override
	public int isiData(byte[] d) {
		Mob b = binatang;
		d[1] = n(selesai() ? 2 : tunduk ? 1 : 0);
		d[2] = b(b == null ? 0.0D : b.getHealth() / Math.max(1.0F, b.getMaxHealth()));
		d[3] = n(bantuan ? 1 : 0);
		d[4] = b(TUNDUK);
		return 4;
	}
}
