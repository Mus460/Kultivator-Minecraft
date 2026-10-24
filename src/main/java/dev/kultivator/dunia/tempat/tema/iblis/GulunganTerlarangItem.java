package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gulungan Terlarang (DESIGN 38.T.7.7, 38.T.10.2): single-use forbidden scrolls sold in the Menara Kitab Terlarang
 * (Si Buta Mo Wen; floor n needs Gentar >= 10 x (n - 1)). Every purchase adds Infami +2; every use outside the Kota Iblis
 * witnessed by townsfolk (who report it to the righteous sects) adds Infami +1. The four kinds and their numbers are
 * fixed here so the shop, the tooltip and the effect agree.
 * <ul>
 * <li><b>Bakar Darah</b>: burns 30 % of the current health (never below 1) for Strength II 30 s; cooldown 5 minutes.</li>
 * <li><b>Langkah Hantu</b>: Invisibility 12 s and Speed II 12 s for 20 % of the maximum Qi.</li>
 * <li><b>Rantai Jiwa</b>: pulls the one creature the player looks at within 12 blocks to 2 blocks in front of him;
 * realm foes, trial mobs and bosses are immune (the chain snaps, the scroll is kept).</li>
 * <li><b>Topeng Iblis Hati</b>: +15 % attack damage for 60 s (a transient attribute modifier), then heart-demon
 * pressure +5 ({@code CultivationData.setHeartDemonPressure}).</li>
 * </ul>
 * No block changes, no fire, no lightning; the effects are vanilla effects, attributes and a teleport of a mob.
 */
public class GulunganTerlarangItem extends Item {

	/** The four scrolls (38.T.7.7). */
	public enum Jenis {
		/** Bakar Darah: sacrifice 30 % HP for Strength II 30 s; cooldown 5 minutes. 40 NB, band >= 2. */
		BAKAR_DARAH("bakar_darah", 40, 2, 6000),
		/** Langkah Hantu: Invisibility 12 s plus speed, costs 20 % Qi. 60 NB, band >= 3. */
		LANGKAH_HANTU("langkah_hantu", 60, 3, 2400),
		/** Rantai Jiwa: pull one foe within 12 blocks to the front (markRealmFoe foes immune). 90 NB, band >= 4. */
		RANTAI_JIWA("rantai_jiwa", 90, 4, 1200),
		/** Topeng Iblis Hati: +15 % damage for 60 s, then heart-demon pressure +5. 120 NB, band >= 4. */
		TOPENG_IBLIS_HATI("topeng_iblis_hati", 120, 4, 6000);

		private final String id;
		private final int harga;
		private final int pita;
		private final int jeda;

		Jenis(String id, int harga, int pita, int jeda) {
			this.id = id;
			this.harga = harga;
			this.pita = pita;
			this.jeda = jeda;
		}

		/** The item path suffix ({@code tema/gulungan_<id>}). */
		public String id() {
			return id;
		}

		/** Price in Nilai Batu (38.T.10.2). */
		public int harga() {
			return harga;
		}

		/** Smallest realm band allowed to buy it. */
		public int pita() {
			return pita;
		}

		/** Item cooldown in ticks after use. */
		public int jeda() {
			return jeda;
		}
	}

	/** Infami added per purchase. */
	public static final int INFAMI_BELI = 2;
	/** Infami added per use seen by townsfolk outside the Kota Iblis. */
	public static final int INFAMI_DISAKSIKAN = 1;
	public static final float BAKAR_HP = 0.30F;
	public static final int BAKAR_TICK = 30 * 20;
	public static final int HANTU_TICK = 12 * 20;
	public static final double HANTU_QI = 0.20D;
	public static final double RANTAI_JARAK = 12.0D;
	public static final int TOPENG_TICK = 60 * 20;
	public static final double TOPENG_DAMAGE = 0.15D;
	public static final double TOPENG_IBLIS_HATI_TEKANAN = 5.0D;
	private static final Identifier TOPENG_ID = Kultivator.id("topeng_iblis_hati");

	/** Players wearing the Heart-Demon Mask -> game time it ends. */
	private static final Map<UUID, Long> TOPENG = new HashMap<>();

	private final Jenis jenis;

	public GulunganTerlarangItem(Properties properties, Jenis jenis) {
		super(properties);
		this.jenis = jenis;
	}

	public Jenis jenis() {
		return jenis;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (p.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		boolean done = switch (jenis) {
			case BAKAR_DARAH -> bakarDarah(p, server);
			case LANGKAH_HANTU -> langkahHantu(p, server);
			case RANTAI_JIWA -> rantaiJiwa(p, server);
			case TOPENG_IBLIS_HATI -> topeng(p, server);
		};
		if (!done) {
			return InteractionResult.FAIL;
		}
		p.getCooldowns().addCooldown(stack, jenis.jeda());
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		if (IblisKota.di(p) == null && HidupApi.saksi(server, p.blockPosition(), p)) {
			IblisMeter.tambahInfami(p, INFAMI_DISAKSIKAN, "gulungan_saksi");
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.disaksikan").withStyle(ChatFormatting.DARK_RED));
		}
		CeritaApi.peristiwa(p, IblisCerita.KITAB, "pakai_" + jenis.id());
		return InteractionResult.SUCCESS_SERVER;
	}

	private static boolean bakarDarah(ServerPlayer p, ServerLevel level) {
		float burn = p.getHealth() * BAKAR_HP;
		if (p.getHealth() - burn < 1.0F) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.bakar_darah.lemah").withStyle(ChatFormatting.RED));
			return false;
		}
		p.setHealth(p.getHealth() - burn);
		p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BAKAR_TICK, 1));
		level.sendParticles(new DustParticleOptions(0x8A0B12, 1.4F), p.getX(), p.getY() + 1.0D, p.getZ(), 30, 0.4D, 0.7D, 0.4D, 0.02D);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_HURT_ON_FIRE, SoundSource.PLAYERS, 0.8F, 0.6F);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.bakar_darah.pakai").withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	private static boolean langkahHantu(ServerPlayer p, ServerLevel level) {
		CultivationData data = Cultivators.get(p);
		double cost = Qi.max(data) * HANTU_QI;
		if (!Qi.trySpend(p, cost)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.langkah_hantu.qi").withStyle(ChatFormatting.RED));
			return false;
		}
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, HANTU_TICK, 0, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, HANTU_TICK, 1, false, false, true));
		level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.8D, p.getZ(), 24, 0.35D, 0.6D, 0.35D, 0.02D);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.7F, 1.3F);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.langkah_hantu.pakai").withStyle(ChatFormatting.DARK_GRAY));
		return true;
	}

	private static boolean rantaiJiwa(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestScore = 0.0D;
		AABB box = p.getBoundingBox().expandTowards(look.scale(RANTAI_JARAK)).inflate(2.0D);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > RANTAI_JARAK || dist < 2.5D) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot < 0.94D || !p.hasLineOfSight(e)) {
				continue;
			}
			double score = dot - dist * 0.01D;
			if (best == null || score > bestScore) {
				best = e;
				bestScore = score;
			}
		}
		if (best == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.rantai_jiwa.kosong").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 from = best.position();
		if (TechniquePower.isTrialMob(best) || TechniquePower.isBoss(best) || best instanceof ServerPlayer) {
			chain(level, eye, best.getBoundingBox().getCenter());
			level.playSound(null, from.x, from.y, from.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.rantai_jiwa.kebal").withStyle(ChatFormatting.RED));
			return false;
		}
		Vec3 front = p.position().add(look.x * 2.0D, 0.0D, look.z * 2.0D);
		chain(level, eye, best.getBoundingBox().getCenter());
		best.teleportTo(level, front.x, p.getY(), front.z, java.util.Set.of(), best.getYRot(), best.getXRot(), true);
		best.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.rantai_jiwa.pakai", best.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	private static void chain(ServerLevel level, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		int n = Math.max(4, (int) (d.length() * 3.0D));
		for (int i = 0; i <= n; i++) {
			Vec3 at = a.add(d.scale(i / (double) n));
			level.sendParticles(new DustParticleOptions(0x5E1A8C, 0.9F), at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static boolean topeng(ServerPlayer p, ServerLevel level) {
		AttributeInstance attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack == null) {
			return false;
		}
		attack.removeModifier(TOPENG_ID);
		attack.addTransientModifier(new AttributeModifier(TOPENG_ID, TOPENG_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		TOPENG.put(p.getUUID(), level.getGameTime() + TOPENG_TICK);
		level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.6D, p.getZ(), 16, 0.3D, 0.3D, 0.3D, 0.02D);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.7F);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.topeng_iblis_hati.pakai").withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** Every 20 ticks: the Heart-Demon Mask ends (modifier off, heart-demon pressure +5). */
	static void tick(MinecraftServer server) {
		if (TOPENG.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		TOPENG.entrySet().removeIf(e -> {
			if (e.getValue() > now) {
				return false;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null) {
				lepasTopeng(p, true);
			}
			return true;
		});
	}

	/** Takes the mask's modifier off (logout, death, the end of its 60 s). */
	static void lepasTopeng(ServerPlayer p, boolean tekanan) {
		AttributeInstance attack = p.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.removeModifier(TOPENG_ID);
		}
		if (tekanan) {
			CultivationData data = Cultivators.get(p);
			data.setHeartDemonPressure(data.heartDemonPressure() + TOPENG_IBLIS_HATI_TEKANAN);
			Cultivators.dirty(p);
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.gulungan.topeng_iblis_hati.habis").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	static void clear() {
		TOPENG.clear();
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
		builder.accept(Component.translatable("kultivator.kota_tema.iblis.gulungan." + jenis.id() + ".efek").withStyle(ChatFormatting.DARK_RED));
		builder.accept(Component.translatable("kultivator.kota_tema.iblis.gulungan.peringatan").withStyle(ChatFormatting.DARK_GRAY));
	}
}
