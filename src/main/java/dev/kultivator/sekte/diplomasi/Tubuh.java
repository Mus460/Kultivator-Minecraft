package dev.kultivator.sekte.diplomasi;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The temporary bodies of module D (DESIGN 36.2.9): envoys, gate members, allied guards and sect masters are disciple
 * bodies of role RIVAL ({@code Disciples.spawnUnowned}: never saved, never listed, untouchable except by a duel partner),
 * like the visitors of {@code sect.event.Rivals}. Each carries its sect's banner (a vanilla banner item whose base, border
 * and charge follow the sect's {@link Lambang}; the stack is made when the body spawns, never in a static initializer).
 */
final class Tubuh {

	/** Banner charges by emblem symbol (righteous sects); demonic sects always carry the skull. */
	private static final ResourceKey<?>[] CHARGES = {BannerPatterns.CIRCLE_MIDDLE, BannerPatterns.RHOMBUS_MIDDLE, BannerPatterns.STRAIGHT_CROSS,
			BannerPatterns.FLOWER, BannerPatterns.TRIANGLE_TOP, BannerPatterns.STRIPE_CENTER, BannerPatterns.CROSS, BannerPatterns.GLOBE};

	/** A body of {@code sect} at {@code pos} with rank step {@code step}; {@code banner} puts the sect banner in its hand. */
	static @Nullable DiscipleEntity spawn(ServerLevel level, Vec3 pos, SekteNpc sect, int step, int slot, Component name, @Nullable BlockPos anchor,
			boolean banner) {
		RandomSource random = level.getRandom();
		DiscipleData data = body(random, step);
		long h = sect.lambang * 31L + slot * 17L + sect.index;
		int skin = (int) Math.floorMod(h, DiscipleEntity.SKIN_COUNT);
		boolean slim = Math.floorMod(h >>> 3, 3) == 0;
		DiscipleEntity body = Disciples.spawnUnowned(level, pos, data, name, skin, slim, DiscipleEntity.Role.RIVAL, anchor);
		if (body == null) {
			return null;
		}
		body.setHealth(body.getMaxHealth());
		if (banner) {
			ItemStack flag = banner(level, sect.emblem(), sect.iblis());
			if (!flag.isEmpty()) {
				body.setItemSlot(EquipmentSlot.MAINHAND, flag);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.6D, pos.z, 8, 0.3D, 0.4D, 0.3D, 0.01D);
		return body;
	}

	/** A cultivator's data at {@code step}: a rolled root, normal qualities up to its realm, full qi. */
	static DiscipleData body(RandomSource random, int step) {
		DiscipleData data = DiscipleData.fresh(random);
		data.setRank(Rank.ofStep(Math.max(0, Math.min(Rank.MAX_STEP, step))));
		for (Realm realm : Realm.values()) {
			if (!realm.isMortal() && realm.ordinal() <= data.realm().ordinal()) {
				data.setQuality(realm, BreakthroughQuality.NORMAL);
			}
		}
		data.setComprehension(10 + random.nextInt(40));
		data.setQi(DiscipleCultivation.maxQi(data));
		return data;
	}

	/**
	 * The body speaks one of the existing disciple lines ({@code greet}, {@code thanks}, {@code farewell}, ...): an own
	 * disciple through {@code VoiceDisciples} (its cooldowns), a sect body in its own voice.
	 */
	static void say(@Nullable DiscipleEntity body, String line) {
		if (body == null || body.isRemoved()) {
			return;
		}
		if (body.role() == DiscipleEntity.Role.DISCIPLE) {
			VoiceDisciples.line(body, line, 200);
		} else if (Voice.free(body)) {
			Voice.say(body, "disciple." + VoiceDisciples.voiceOf(body) + "." + line, null);
		}
	}

	/** Leaves in a puff of cloud (never a death, never loot). */
	static void vanish(@Nullable DiscipleEntity body) {
		if (body == null || body.isRemoved()) {
			return;
		}
		if (body.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 0.8D, body.getZ(), 12, 0.35D, 0.5D, 0.35D, 0.02D);
		}
		body.discard();
	}

	/** The top of the ground at {@code pos}'s column (motion blocking, no leaves). */
	static BlockPos ground(ServerLevel level, BlockPos pos) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
	}

	/** The sect's banner: base = field colour, border = border colour, charge = symbol (skull for the demonic path). */
	@SuppressWarnings("unchecked")
	static ItemStack banner(ServerLevel level, Lambang emblem, boolean iblis) {
		DyeColor field = nearest(emblem.fieldRgb());
		DyeColor charge = nearest(emblem.chargeRgb());
		DyeColor border = nearest(emblem.borderRgb());
		if (charge == field) {
			charge = field == DyeColor.WHITE ? DyeColor.BLACK : DyeColor.WHITE;
		}
		ItemStack stack = new ItemStack(Items.BANNER.pick(field));
		try {
			var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			ResourceKey<BannerPattern> chargeKey = iblis ? BannerPatterns.SKULL
					: (ResourceKey<BannerPattern>) CHARGES[Math.floorMod(emblem.symbol(), CHARGES.length)];
			Holder<BannerPattern> chargeHolder = patterns.getOrThrow(chargeKey);
			BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
			if (emblem.shape() >= 4) {
				layers.add(patterns.getOrThrow(BannerPatterns.GRADIENT), border);
			}
			layers.add(chargeHolder, charge);
			layers.add(patterns.getOrThrow(emblem.shape() % 2 == 0 ? BannerPatterns.BORDER : BannerPatterns.CURLY_BORDER), border);
			stack.set(DataComponents.BANNER_PATTERNS, layers.build());
		} catch (RuntimeException exception) {
			// A pattern missing from the registry: the plain coloured banner still shows the sect.
		}
		return stack;
	}

	/** The dye whose banner colour is closest to {@code rgb}. */
	static DyeColor nearest(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		DyeColor best = DyeColor.WHITE;
		long bestD = Long.MAX_VALUE;
		for (DyeColor dye : DyeColor.values()) {
			int c = dye.getTextureDiffuseColor();
			long dr = (c >> 16 & 0xFF) - r;
			long dg = (c >> 8 & 0xFF) - g;
			long db = (c & 0xFF) - b;
			long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (d < bestD) {
				bestD = d;
				best = dye;
			}
		}
		return best;
	}

	/** Turns the body and its head to {@code yaw} at once: a body put at a post faces its work, not a random way. */
	static void hadap(DiscipleEntity body, float yaw) {
		body.setYRot(yaw);
		body.yRotO = yaw;
		body.setYHeadRot(yaw);
		body.yHeadRotO = yaw;
		body.setYBodyRot(yaw);
		body.yBodyRotO = yaw;
	}

	/** The yaw (degrees) that looks from {@code from} toward {@code to} along the ground. */
	static float menuju(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(from.x - to.x, to.z - from.z));
	}

	/**
	 * A gate member's job by its slot (the clips and props of {@code KerjaPeran}'s MURID_SEKTE_LUAR/DALAM and PELAYAN_SEKTE): the
	 * flag-bearer stands guard holding its banner (a real held item keeps the clip's spear away), the others drill with the
	 * sword, breathe in meditation, sweep or carry water on the yoke. The clip loops until replaced.
	 */
	static void kerja(DiscipleEntity body, int slot, boolean panji) {
		if (panji) {
			SosokApi.gerak(body, GerakId.BERDIRI_JAGA, 0, 0);
			return;
		}
		SosokApi.gerak(body, switch (Math.floorMod(slot, 6)) {
			case 2 -> GerakId.NAPAS_MEDITASI;
			case 4 -> GerakId.SAPU;
			case 5 -> GerakId.JALAN_PIKUL;
			default -> GerakId.LATIHAN_PEDANG_1;
		}, 0, 0);
	}

	private Tubuh() {
	}
}
